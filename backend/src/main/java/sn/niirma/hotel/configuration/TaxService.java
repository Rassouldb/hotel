package sn.niirma.hotel.configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.niirma.audit.AuditEntry;
import sn.niirma.audit.AuditService;
import sn.niirma.hotel.ChargeCategory;
import sn.niirma.hotel.HotelDirectory;
import sn.niirma.shared.error.BusinessException;

/**
 * Taxes de l'hôtel (US-TAX-01). Les taux en pourcentage sont exprimés en points (18 = 18 %).
 */
@Service
@Transactional
class TaxService {

    record CreateTaxRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{2,20}") String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull Tax.Type type,
            boolean vat,
            @NotEmpty Set<ChargeCategory> appliesTo,
            @Min(0) @Max(25) Integer exemptChildrenUnderAge,
            boolean exemptResidents,
            @NotNull LocalDate validFrom,
            @DecimalMin("0") BigDecimal rate,
            @DecimalMin("0") BigDecimal amount) {
    }

    record UpdateTaxRequest(
            @NotBlank @Size(max = 100) String name,
            @NotEmpty Set<ChargeCategory> appliesTo,
            @Min(0) @Max(25) Integer exemptChildrenUnderAge,
            boolean exemptResidents,
            boolean active,
            @NotNull Long version) {
    }

    record NewRateRequest(@NotNull LocalDate validFrom, @DecimalMin("0") BigDecimal rate, @DecimalMin("0") BigDecimal amount) {
    }

    record PeriodResponse(LocalDate validFrom, LocalDate validTo, BigDecimal rate, BigDecimal amount) {
    }

    record TaxResponse(UUID id, String code, String name, Tax.Type type, boolean vat, Set<ChargeCategory> appliesTo,
                       Integer exemptChildrenUnderAge, boolean exemptResidents, boolean active, BigDecimal currentRate,
                       BigDecimal currentAmount, List<PeriodResponse> periods, Long version) {
    }

    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100");

    private final TaxRepository taxes;
    private final HotelDirectory hotelDirectory;
    private final AuditService audit;

    TaxService(TaxRepository taxes, HotelDirectory hotelDirectory, AuditService audit) {
        this.taxes = taxes;
        this.hotelDirectory = hotelDirectory;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TaxResponse> list(UUID hotelId) {
        LocalDate businessDate = hotelDirectory.businessDate(hotelId);
        return taxes.findByHotelIdOrderByCodeAsc(hotelId).stream().map(tax -> toResponse(tax, businessDate)).toList();
    }

    public TaxResponse create(UUID hotelId, CreateTaxRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (taxes.existsByHotelIdAndCode(hotelId, code)) {
            throw BusinessException.conflict("TAX_CODE_ALREADY_EXISTS", "Une taxe porte déjà ce code.");
        }
        checkValue(request.type(), request.rate(), request.amount());
        Tax tax = new Tax(hotelId, code, request.name().trim(), request.type(), request.vat());
        tax.update(request.name().trim(), request.appliesTo(), request.exemptChildrenUnderAge(), request.exemptResidents(), true);
        tax.startPeriod(request.validFrom(), valueOrNull(request.type(), true, request.rate(), request.amount()),
                valueOrNull(request.type(), false, request.rate(), request.amount()));
        taxes.saveAndFlush(tax);
        audit.record(AuditEntry.of("TAX_CREATE", "Tax", tax.getId()).inHotel(null, hotelId)
                .withChange(null, describe(tax)));
        return toResponse(tax, hotelDirectory.businessDate(hotelId));
    }

    public TaxResponse update(UUID hotelId, UUID taxId, UpdateTaxRequest request) {
        Tax tax = load(hotelId, taxId);
        if (!Objects.equals(tax.getVersion(), request.version())) {
            throw BusinessException.conflict("VERSION_CONFLICT", "Cette taxe a été modifiée entre-temps. Rechargez puis réessayez.");
        }
        Map<String, Object> before = describe(tax);
        tax.update(request.name().trim(), request.appliesTo(), request.exemptChildrenUnderAge(), request.exemptResidents(),
                request.active());
        taxes.saveAndFlush(tax);
        audit.record(AuditEntry.of("TAX_UPDATE", "Tax", tax.getId()).inHotel(null, hotelId).withChange(before, describe(tax)));
        return toResponse(tax, hotelDirectory.businessDate(hotelId));
    }

    /** Nouveau taux à partir d'une date : jamais dans le passé, ni avant le début de la période en cours. */
    public TaxResponse changeRate(UUID hotelId, UUID taxId, NewRateRequest request) {
        Tax tax = load(hotelId, taxId);
        checkValue(tax.getType(), request.rate(), request.amount());
        LocalDate businessDate = hotelDirectory.businessDate(hotelId);
        LocalDate openSince = tax.openPeriod().map(Tax.RatePeriod::validFrom).orElse(LocalDate.MIN);
        if (request.validFrom().isBefore(businessDate) || !request.validFrom().isAfter(openSince)) {
            throw BusinessException.conflict("TAX_PERIOD_LOCKED",
                    "Un nouveau taux ne peut prendre effet qu'à partir de la date d'exploitation ("
                            + businessDate + ") et après le début du taux en vigueur.");
        }
        Map<String, Object> before = describe(tax);
        tax.startPeriod(request.validFrom(), valueOrNull(tax.getType(), true, request.rate(), request.amount()),
                valueOrNull(tax.getType(), false, request.rate(), request.amount()));
        taxes.saveAndFlush(tax);
        audit.record(AuditEntry.of("TAX_RATE_CHANGE", "Tax", tax.getId()).inHotel(null, hotelId)
                .withChange(before, describe(tax)));
        return toResponse(tax, businessDate);
    }

    private Tax load(UUID hotelId, UUID taxId) {
        return taxes.findById(taxId)
                .filter(tax -> tax.getHotelId().equals(hotelId))
                .orElseThrow(() -> BusinessException.notFound("TAX_NOT_FOUND", "Taxe introuvable."));
    }

    private static void checkValue(Tax.Type type, BigDecimal rate, BigDecimal amount) {
        if (type == Tax.Type.PERCENTAGE) {
            if (rate == null || rate.compareTo(MAX_PERCENTAGE) > 0) {
                throw invalid("rate", "Indiquez un taux entre 0 et 100 %.");
            }
        } else if (amount == null) {
            throw invalid("amount", "Indiquez le montant de la taxe.");
        }
    }

    private static BigDecimal valueOrNull(Tax.Type type, boolean wantRate, BigDecimal rate, BigDecimal amount) {
        boolean percentage = type == Tax.Type.PERCENTAGE;
        return wantRate ? (percentage ? rate : null) : (percentage ? null : amount);
    }

    private static BusinessException invalid(String field, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message,
                List.of(Map.of("field", field, "issue", message)));
    }

    private static Map<String, Object> describe(Tax tax) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", tax.getCode());
        values.put("name", tax.getName());
        values.put("appliesTo", tax.getAppliesTo().stream().map(Enum::name).sorted().toList());
        values.put("exemptChildrenUnderAge", tax.getExemptChildrenUnderAge());
        values.put("exemptResidents", tax.isExemptResidents());
        values.put("active", tax.isActive());
        values.put("periods", tax.getPeriods().stream()
                .map(p -> p.validFrom() + " → " + (p.validTo() == null ? "…" : p.validTo()) + " : "
                        + (p.rate() != null ? p.rate().stripTrailingZeros().toPlainString() + " %" : p.amount().toPlainString()))
                .toList());
        return values;
    }

    static TaxResponse toResponse(Tax tax, LocalDate businessDate) {
        Tax.RatePeriod current = tax.periodAt(businessDate).orElse(null);
        return new TaxResponse(tax.getId(), tax.getCode(), tax.getName(), tax.getType(), tax.isVat(), tax.getAppliesTo(),
                tax.getExemptChildrenUnderAge(), tax.isExemptResidents(), tax.isActive(),
                current == null ? null : scaled(current.rate()), current == null ? null : scaled(current.amount()),
                tax.getPeriods().stream()
                        .map(p -> new PeriodResponse(p.validFrom(), p.validTo(), scaled(p.rate()), scaled(p.amount())))
                        .toList(),
                tax.getVersion());
    }

    /** Valeurs toujours renvoyées avec deux décimales, qu'elles viennent de la base ou de la requête. */
    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
