package sn.niirma.hotel;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import sn.niirma.audit.AuditEntry;
import sn.niirma.audit.AuditService;
import sn.niirma.hotel.internal.Hotel;
import sn.niirma.hotel.internal.HotelRepository;
import sn.niirma.shared.error.BusinessException;
import sn.niirma.shared.security.AccessControl;
import sn.niirma.shared.security.CurrentUser;
import sn.niirma.shared.security.HotelScope;

/**
 * Hôtels de l'utilisateur courant (US-HOTEL-01 à 03) : uniquement les hôtels de son organisation
 * auxquels il est affecté (tous pour un super administrateur).
 */
@Service
@Transactional(readOnly = true)
public class HotelService {

    public record CreateHotelRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{2,10}", message = "2 à 10 lettres ou chiffres") String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String legalName,
            @Size(min = 3, max = 3) String currencyCode,
            @Size(max = 50) String timezone,
            @Pattern(regexp = "fr|en") String language,
            LocalDate businessDate) {
    }

    public record UpdateHotelRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String legalName,
            @Size(max = 500) String address,
            @Size(max = 30) String phone,
            @Email @Size(max = 200) String email,
            Map<@Size(max = 30) String, @Size(max = 60) String> legalIds,
            @NotBlank @Pattern(regexp = "fr|en") String language,
            @NotNull Long version) {
    }

    public record Logo(byte[] content, String contentType) {
    }

    static final Set<String> LOGO_TYPES = Set.of("image/png", "image/jpeg");
    static final int MAX_LOGO_BYTES = 500 * 1024;

    private final HotelRepository hotels;
    private final AccessControl accessControl;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final JdbcClient jdbc;
    private final Clock clock;

    HotelService(HotelRepository hotels, AccessControl accessControl, AuditService audit, ApplicationEventPublisher events,
                 ObjectMapper objectMapper, Validator validator, JdbcClient jdbc, Clock clock) {
        this.hotels = hotels;
        this.accessControl = accessControl;
        this.audit = audit;
        this.events = events;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Page<HotelView> list(Pageable pageable) {
        UUID organizationId = CurrentUser.require().organizationId();
        HotelScope scope = accessControl.accessibleHotels();
        if (scope.isEmpty()) {
            return Page.empty(pageable);
        }
        Page<Hotel> page = scope.allHotels()
                ? hotels.findByOrganizationId(organizationId, pageable)
                : hotels.findByOrganizationIdAndIdIn(organizationId, scope.hotelIds(), pageable);
        return page.map(this::toView);
    }

    public HotelView get(UUID hotelId) {
        return toView(load(hotelId));
    }

    /** Création d'un hôtel, réservée au super administrateur (US-HOTEL-01). */
    @Transactional
    public HotelView create(CreateHotelRequest request) {
        if (!accessControl.accessibleHotels().allHotels()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                    "Seul un super administrateur peut créer un hôtel.");
        }
        UUID organizationId = CurrentUser.require().organizationId();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (hotels.existsByOrganizationIdAndCode(organizationId, code)) {
            throw BusinessException.conflict("HOTEL_CODE_ALREADY_EXISTS", "Un hôtel porte déjà ce code.");
        }
        String currency = validCurrency(request.currencyCode());
        ZoneId zone = validZone(request.timezone());
        LocalDate businessDate = request.businessDate() != null ? request.businessDate() : LocalDate.now(clock.withZone(zone));
        String language = request.language() == null ? "fr" : request.language();

        Hotel hotel = new Hotel(organizationId, code, request.name().trim(), currency, zone.getId(), language, businessDate,
                objectMapper.writeValueAsString(HotelSettings.defaults()));
        hotel.updateInformation(request.name().trim(), blankToNull(request.legalName()), null, null, null, null, language);
        hotels.saveAndFlush(hotel);

        audit.record(AuditEntry.of("HOTEL_CREATE", "Hotel", hotel.getId()).inHotel(organizationId, hotel.getId())
                .withChange(null, Map.of("code", code, "name", hotel.getName(), "currency", currency)));
        events.publishEvent(new HotelCreated(hotel.getId(), organizationId));
        return toView(hotel);
    }

    @Transactional
    public HotelView update(UUID hotelId, UpdateHotelRequest request) {
        Hotel hotel = load(hotelId);
        if (!Objects.equals(hotel.getVersion(), request.version())) {
            throw BusinessException.conflict("VERSION_CONFLICT", "L'hôtel a été modifié entre-temps. Rechargez puis réessayez.");
        }
        Map<String, Object> before = snapshot(hotel);
        Map<String, String> legalIds = request.legalIds() == null ? Map.of() : cleanLegalIds(request.legalIds());
        hotel.updateInformation(request.name().trim(), blankToNull(request.legalName()), blankToNull(request.address()),
                blankToNull(request.phone()), blankToNull(request.email()),
                legalIds.isEmpty() ? null : objectMapper.writeValueAsString(legalIds), request.language());
        hotels.saveAndFlush(hotel);
        Map<String, Object> after = snapshot(hotel);
        audit.record(AuditEntry.of("HOTEL_UPDATE", "Hotel", hotel.getId()).inHotel(hotel.getOrganizationId(), hotel.getId())
                .withChange(changed(before, after), changed(after, before)));
        return toView(hotel);
    }

    public HotelSettings settings(UUID hotelId) {
        return readSettings(load(hotelId));
    }

    @Transactional
    public HotelSettings updateSettings(UUID hotelId, HotelSettings settings) {
        Hotel hotel = load(hotelId);
        Set<ConstraintViolation<HotelSettings>> violations = validator.validate(settings);
        if (!violations.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Les paramètres envoyés sont invalides.",
                    violations.stream()
                            .map(v -> Map.<String, Object>of("field", v.getPropertyPath().toString(), "issue", v.getMessage()))
                            .toList());
        }
        if (settings.checkInGuarantee() == HotelSettings.CheckInGuarantee.FIXED_DEPOSIT && settings.guaranteeAmount() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Indiquez le montant de la caution.",
                    List.of(Map.of("field", "guaranteeAmount", "issue", "REQUIRED")));
        }
        HotelSettings before = readSettings(hotel);
        hotel.updateSettings(objectMapper.writeValueAsString(settings));
        audit.record(AuditEntry.of("HOTEL_SETTINGS_UPDATE", "Hotel", hotel.getId())
                .inHotel(hotel.getOrganizationId(), hotel.getId())
                .withChange(changed(asMap(before), asMap(settings)), changed(asMap(settings), asMap(before))));
        return settings;
    }

    public Logo logo(UUID hotelId) {
        load(hotelId);
        return jdbc.sql("SELECT logo, logo_content_type FROM hotel WHERE id = :id AND logo IS NOT NULL")
                .param("id", hotelId)
                .query((rs, rowNum) -> new Logo(rs.getBytes("logo"), rs.getString("logo_content_type")))
                .optional()
                .orElseThrow(() -> BusinessException.notFound("LOGO_NOT_FOUND", "Aucun logo pour cet hôtel."));
    }

    @Transactional
    public void updateLogo(UUID hotelId, byte[] content, String contentType) {
        Hotel hotel = load(hotelId);
        if (contentType == null || !LOGO_TYPES.contains(contentType)) {
            throw BusinessException.unprocessable("UNSUPPORTED_IMAGE_TYPE", "Le logo doit être une image PNG ou JPEG.");
        }
        if (content.length == 0 || content.length > MAX_LOGO_BYTES) {
            throw BusinessException.unprocessable("IMAGE_TOO_LARGE", "Le logo ne doit pas dépasser 500 Ko.");
        }
        jdbc.sql("UPDATE hotel SET logo = :logo, logo_content_type = :type WHERE id = :id")
                .param("logo", content)
                .param("type", contentType)
                .param("id", hotelId)
                .update();
        audit.record(AuditEntry.of("HOTEL_LOGO_UPDATE", "Hotel", hotelId).inHotel(hotel.getOrganizationId(), hotelId)
                .withChange(null, Map.of("contentType", contentType, "size", content.length)));
    }

    @Transactional
    public void deleteLogo(UUID hotelId) {
        Hotel hotel = load(hotelId);
        jdbc.sql("UPDATE hotel SET logo = NULL, logo_content_type = NULL WHERE id = :id").param("id", hotelId).update();
        audit.record(AuditEntry.of("HOTEL_LOGO_DELETE", "Hotel", hotelId).inHotel(hotel.getOrganizationId(), hotelId));
    }

    HotelSettings readSettings(Hotel hotel) {
        String json = hotel.getSettings();
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return HotelSettings.defaults();
        }
        return objectMapper.readValue(json, HotelSettings.class).withDefaults();
    }

    private Hotel load(UUID hotelId) {
        UUID organizationId = CurrentUser.require().organizationId();
        return hotels.findById(hotelId)
                .filter(hotel -> hotel.getOrganizationId().equals(organizationId))
                .filter(hotel -> accessControl.canAccessHotel(hotel.getId()))
                .orElseThrow(() -> BusinessException.notFound("HOTEL_NOT_FOUND", "Hôtel introuvable."));
    }

    HotelView toView(Hotel hotel) {
        return new HotelView(hotel.getId(), hotel.getOrganizationId(), hotel.getCode(), hotel.getName(),
                hotel.getLegalName(), hotel.getAddress(), hotel.getPhone(), hotel.getEmail(), parseLegalIds(hotel.getLegalIds()),
                hotel.getCurrencyCode(), hotel.getTimezone(), hotel.getLanguage(), hotel.getBusinessDate(),
                hotel.getStatus().name(), hotel.hasLogo(), hotel.getVersion());
    }

    private Map<String, String> parseLegalIds(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
        });
    }

    private static Map<String, String> cleanLegalIds(Map<String, String> legalIds) {
        Map<String, String> cleaned = new LinkedHashMap<>();
        legalIds.forEach((label, value) -> {
            if (label != null && !label.isBlank() && value != null && !value.isBlank()) {
                cleaned.put(label.trim(), value.trim());
            }
        });
        return cleaned;
    }

    private Map<String, Object> asMap(HotelSettings settings) {
        return objectMapper.convertValue(settings, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private static Map<String, Object> snapshot(Hotel hotel) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", hotel.getName());
        values.put("legalName", hotel.getLegalName());
        values.put("address", hotel.getAddress());
        values.put("phone", hotel.getPhone());
        values.put("email", hotel.getEmail());
        values.put("legalIds", hotel.getLegalIds());
        values.put("language", hotel.getLanguage());
        return values;
    }

    private static Map<String, Object> changed(Map<String, Object> values, Map<String, Object> other) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (!Objects.equals(value, other.get(key))) {
                result.put(key, value);
            }
        });
        return result;
    }

    private static String validCurrency(String code) {
        try {
            return Currency.getInstance(code == null ? "XOF" : code.trim().toUpperCase(Locale.ROOT)).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw invalidField("currencyCode", "Devise inconnue (code ISO 4217 attendu, ex. XOF).");
        }
    }

    private static ZoneId validZone(String timezone) {
        try {
            return ZoneId.of(timezone == null ? "Africa/Dakar" : timezone.trim());
        } catch (RuntimeException e) {
            throw invalidField("timezone", "Fuseau horaire inconnu (ex. Africa/Dakar).");
        }
    }

    private static BusinessException invalidField(String field, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message,
                List.of(Map.of("field", field, "issue", message)));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
