package sn.niirma.hotel.configuration;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.niirma.audit.AuditEntry;
import sn.niirma.audit.AuditService;
import sn.niirma.shared.error.BusinessException;

/**
 * Listes de référence de l'hôtel : moyens de paiement (US-PAY-01), canaux de réservation et motifs
 * (US-REF-01). Rien n'est supprimé : un élément devenu inutile est désactivé.
 */
@Service
@Transactional
class ReferenceDataService {

    static final String CODE_PATTERN = "[A-Za-z0-9_]{2,30}";

    record PaymentMethodRequest(
            @Pattern(regexp = CODE_PATTERN) String code,
            @NotBlank @Size(max = 100) String name,
            PaymentMethod.Type type,
            boolean requiresCashSession,
            boolean requiresReference,
            boolean active,
            int sortOrder) {
    }

    record PaymentMethodResponse(UUID id, String code, String name, PaymentMethod.Type type, boolean requiresCashSession,
                                 boolean requiresReference, boolean active, int sortOrder) {
    }

    record ChannelRequest(
            @Pattern(regexp = CODE_PATTERN) String code,
            @NotBlank @Size(max = 100) String name,
            @DecimalMin("0") @DecimalMax("100") BigDecimal commissionRate,
            boolean active,
            int sortOrder) {
    }

    record ChannelResponse(UUID id, String code, String name, BigDecimal commissionRate, boolean active, int sortOrder) {
    }

    record ReasonRequest(
            ReasonCode.Domain domain,
            @Pattern(regexp = CODE_PATTERN) String code,
            @NotBlank @Size(max = 150) String label,
            boolean active,
            int sortOrder) {
    }

    record ReasonResponse(UUID id, ReasonCode.Domain domain, String code, String label, boolean active, int sortOrder) {
    }

    private final PaymentMethodRepository paymentMethods;
    private final ReservationChannelRepository channels;
    private final ReasonCodeRepository reasons;
    private final AuditService audit;

    ReferenceDataService(PaymentMethodRepository paymentMethods, ReservationChannelRepository channels,
                         ReasonCodeRepository reasons, AuditService audit) {
        this.paymentMethods = paymentMethods;
        this.channels = channels;
        this.reasons = reasons;
        this.audit = audit;
    }

    // --- Moyens de paiement ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> paymentMethods(UUID hotelId) {
        return paymentMethods.findByHotelIdOrderBySortOrderAscNameAsc(hotelId).stream().map(ReferenceDataService::toResponse).toList();
    }

    public PaymentMethodResponse createPaymentMethod(UUID hotelId, PaymentMethodRequest request) {
        String code = requireCode(request.code());
        if (request.type() == null) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "Indiquez le type du moyen de paiement.");
        }
        if (paymentMethods.existsByHotelIdAndCode(hotelId, code)) {
            throw BusinessException.conflict("PAYMENT_METHOD_CODE_ALREADY_EXISTS", "Un moyen de paiement porte déjà ce code.");
        }
        checkCash(request.type(), request.requiresCashSession());
        PaymentMethod method = paymentMethods.saveAndFlush(new PaymentMethod(hotelId, code, request.name().trim(), request.type(),
                request.requiresCashSession(), request.requiresReference(), request.active(), request.sortOrder()));
        audit.record(AuditEntry.of("PAYMENT_METHOD_CREATE", "PaymentMethod", method.getId()).inHotel(null, hotelId)
                .withChange(null, describe(method)));
        return toResponse(method);
    }

    public PaymentMethodResponse updatePaymentMethod(UUID hotelId, UUID id, PaymentMethodRequest request) {
        PaymentMethod method = paymentMethods.findById(id).filter(m -> m.getHotelId().equals(hotelId))
                .orElseThrow(() -> BusinessException.notFound("PAYMENT_METHOD_NOT_FOUND", "Moyen de paiement introuvable."));
        checkCash(method.getType(), request.requiresCashSession());
        Map<String, Object> before = describe(method);
        method.update(request.name().trim(), request.requiresCashSession(), request.requiresReference(), request.active(),
                request.sortOrder());
        paymentMethods.saveAndFlush(method);
        audit.record(AuditEntry.of("PAYMENT_METHOD_UPDATE", "PaymentMethod", method.getId()).inHotel(null, hotelId)
                .withChange(before, describe(method)));
        return toResponse(method);
    }

    // --- Canaux de réservation ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ChannelResponse> channels(UUID hotelId) {
        return channels.findByHotelIdOrderBySortOrderAscNameAsc(hotelId).stream().map(ReferenceDataService::toResponse).toList();
    }

    public ChannelResponse createChannel(UUID hotelId, ChannelRequest request) {
        String code = requireCode(request.code());
        if (channels.existsByHotelIdAndCode(hotelId, code)) {
            throw BusinessException.conflict("CHANNEL_CODE_ALREADY_EXISTS", "Un canal porte déjà ce code.");
        }
        ReservationChannel channel = new ReservationChannel(hotelId, code, request.name().trim(), request.commissionRate(),
                request.sortOrder());
        channel.update(request.name().trim(), request.commissionRate(), request.active(), request.sortOrder());
        channels.saveAndFlush(channel);
        audit.record(AuditEntry.of("CHANNEL_CREATE", "ReservationChannel", channel.getId()).inHotel(null, hotelId)
                .withChange(null, Map.of("code", code, "name", channel.getName())));
        return toResponse(channel);
    }

    public ChannelResponse updateChannel(UUID hotelId, UUID id, ChannelRequest request) {
        ReservationChannel channel = channels.findById(id).filter(c -> c.getHotelId().equals(hotelId))
                .orElseThrow(() -> BusinessException.notFound("CHANNEL_NOT_FOUND", "Canal introuvable."));
        Map<String, Object> before = describe(channel);
        channel.update(request.name().trim(), request.commissionRate(), request.active(), request.sortOrder());
        channels.saveAndFlush(channel);
        audit.record(AuditEntry.of("CHANNEL_UPDATE", "ReservationChannel", channel.getId()).inHotel(null, hotelId)
                .withChange(before, describe(channel)));
        return toResponse(channel);
    }

    // --- Motifs --------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ReasonResponse> reasons(UUID hotelId, ReasonCode.Domain domain) {
        return reasons.findByHotelIdOrderByDomainAscSortOrderAscLabelAsc(hotelId).stream()
                .filter(reason -> domain == null || reason.getDomain() == domain)
                .map(ReferenceDataService::toResponse)
                .toList();
    }

    public ReasonResponse createReason(UUID hotelId, ReasonRequest request) {
        String code = requireCode(request.code());
        if (request.domain() == null) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "Indiquez le domaine du motif.");
        }
        if (reasons.existsByHotelIdAndDomainAndCode(hotelId, request.domain(), code)) {
            throw BusinessException.conflict("REASON_CODE_ALREADY_EXISTS", "Un motif porte déjà ce code dans ce domaine.");
        }
        ReasonCode reason = new ReasonCode(hotelId, request.domain(), code, request.label().trim(), request.sortOrder());
        reason.update(request.label().trim(), request.active(), request.sortOrder());
        reasons.saveAndFlush(reason);
        audit.record(AuditEntry.of("REASON_CREATE", "ReasonCode", reason.getId()).inHotel(null, hotelId)
                .withChange(null, Map.of("domain", reason.getDomain().name(), "code", code, "label", reason.getLabel())));
        return toResponse(reason);
    }

    public ReasonResponse updateReason(UUID hotelId, UUID id, ReasonRequest request) {
        ReasonCode reason = reasons.findById(id).filter(r -> r.getHotelId().equals(hotelId))
                .orElseThrow(() -> BusinessException.notFound("REASON_NOT_FOUND", "Motif introuvable."));
        Map<String, Object> before = Map.of("label", reason.getLabel(), "active", reason.isActive());
        reason.update(request.label().trim(), request.active(), request.sortOrder());
        reasons.saveAndFlush(reason);
        audit.record(AuditEntry.of("REASON_UPDATE", "ReasonCode", reason.getId()).inHotel(null, hotelId)
                .withChange(before, Map.of("label", reason.getLabel(), "active", reason.isActive())));
        return toResponse(reason);
    }

    // --- Outils --------------------------------------------------------------------------------------------

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "Le code est obligatoire.");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static void checkCash(PaymentMethod.Type type, boolean requiresCashSession) {
        if (type == PaymentMethod.Type.CASH && !requiresCashSession) {
            throw BusinessException.unprocessable("CASH_REQUIRES_CASH_SESSION",
                    "Les paiements en espèces passent toujours par une session de caisse.");
        }
    }

    private static Map<String, Object> describe(PaymentMethod method) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", method.getName());
        values.put("requiresCashSession", method.isRequiresCashSession());
        values.put("requiresReference", method.isRequiresReference());
        values.put("active", method.isActive());
        return values;
    }

    private static Map<String, Object> describe(ReservationChannel channel) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", channel.getName());
        values.put("commissionRate", channel.getCommissionRate());
        values.put("active", channel.isActive());
        return values;
    }

    static PaymentMethodResponse toResponse(PaymentMethod m) {
        return new PaymentMethodResponse(m.getId(), m.getCode(), m.getName(), m.getType(), m.isRequiresCashSession(),
                m.isRequiresReference(), m.isActive(), m.getSortOrder());
    }

    static ChannelResponse toResponse(ReservationChannel c) {
        return new ChannelResponse(c.getId(), c.getCode(), c.getName(), c.getCommissionRate(), c.isActive(), c.getSortOrder());
    }

    static ReasonResponse toResponse(ReasonCode r) {
        return new ReasonResponse(r.getId(), r.getDomain(), r.getCode(), r.getLabel(), r.isActive(), r.getSortOrder());
    }
}
