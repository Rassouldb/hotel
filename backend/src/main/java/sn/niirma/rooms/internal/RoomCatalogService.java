package sn.niirma.rooms.internal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.niirma.audit.AuditEntry;
import sn.niirma.audit.AuditService;
import sn.niirma.shared.error.BusinessException;
import sn.niirma.shared.persistence.UuidV7;

/**
 * Équipements et types de chambres (US-ROOM-01), photos des types comprises.
 */
@Service
@Transactional
class RoomCatalogService {

    static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg");
    static final int MAX_IMAGE_BYTES = 500 * 1024;
    static final int MAX_PHOTOS_PER_TYPE = 8;

    record AmenityRequest(@Pattern(regexp = "[A-Za-z0-9_]{2,30}") String code, @NotBlank @Size(max = 100) String name,
                          boolean active) {
    }

    record AmenityResponse(UUID id, String code, String name, boolean active) {
    }

    record RoomTypeRequest(
            @Pattern(regexp = "[A-Za-z0-9]{2,10}") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 1000) String description,
            @Min(1) @Max(20) int baseOccupancy,
            @Min(1) @Max(20) int maxOccupancy,
            @Min(1) @Max(20) int maxAdults,
            @Min(0) @Max(20) int maxChildren,
            @NotNull @DecimalMin("0") BigDecimal basePrice,
            int sortOrder,
            boolean active,
            Set<UUID> amenityIds,
            Long version) {
    }

    record RoomTypeResponse(UUID id, String code, String name, String description, int baseOccupancy, int maxOccupancy,
                            int maxAdults, int maxChildren, BigDecimal basePrice, int sortOrder, boolean active,
                            Set<UUID> amenityIds, List<UUID> photoIds, long activeRoomCount, Long version) {
    }

    record Photo(byte[] content, String contentType) {
    }

    private final AmenityRepository amenities;
    private final RoomTypeRepository roomTypes;
    private final RoomRepository rooms;
    private final AuditService audit;
    private final JdbcClient jdbc;
    private final Clock clock;

    RoomCatalogService(AmenityRepository amenities, RoomTypeRepository roomTypes, RoomRepository rooms, AuditService audit,
                       JdbcClient jdbc, Clock clock) {
        this.amenities = amenities;
        this.roomTypes = roomTypes;
        this.rooms = rooms;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // --- Équipements ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AmenityResponse> amenities(UUID hotelId) {
        return amenities.findByHotelIdOrderByNameAsc(hotelId).stream()
                .map(a -> new AmenityResponse(a.getId(), a.getCode(), a.getName(), a.isActive()))
                .toList();
    }

    public AmenityResponse createAmenity(UUID hotelId, AmenityRequest request) {
        if (request.code() == null) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "Le code est obligatoire.");
        }
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (amenities.existsByHotelIdAndCode(hotelId, code)) {
            throw BusinessException.conflict("AMENITY_CODE_ALREADY_EXISTS", "Un équipement porte déjà ce code.");
        }
        Amenity amenity = amenities.saveAndFlush(new Amenity(hotelId, code, request.name().trim()));
        audit.record(AuditEntry.of("AMENITY_CREATE", "Amenity", amenity.getId()).inHotel(null, hotelId)
                .withChange(null, Map.of("code", code, "name", amenity.getName())));
        return new AmenityResponse(amenity.getId(), amenity.getCode(), amenity.getName(), amenity.isActive());
    }

    public AmenityResponse updateAmenity(UUID hotelId, UUID amenityId, AmenityRequest request) {
        Amenity amenity = amenities.findById(amenityId).filter(a -> a.getHotelId().equals(hotelId))
                .orElseThrow(() -> BusinessException.notFound("AMENITY_NOT_FOUND", "Équipement introuvable."));
        Map<String, Object> before = Map.of("name", amenity.getName(), "active", amenity.isActive());
        amenity.update(request.name().trim(), request.active());
        amenities.saveAndFlush(amenity);
        audit.record(AuditEntry.of("AMENITY_UPDATE", "Amenity", amenity.getId()).inHotel(null, hotelId)
                .withChange(before, Map.of("name", amenity.getName(), "active", amenity.isActive())));
        return new AmenityResponse(amenity.getId(), amenity.getCode(), amenity.getName(), amenity.isActive());
    }

    // --- Types de chambres ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RoomTypeResponse> roomTypes(UUID hotelId) {
        return roomTypes.findByHotelIdOrderBySortOrderAscNameAsc(hotelId).stream().map(this::toResponse).toList();
    }

    public RoomTypeResponse createRoomType(UUID hotelId, RoomTypeRequest request) {
        if (request.code() == null) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "Le code est obligatoire.");
        }
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (roomTypes.existsByHotelIdAndCode(hotelId, code)) {
            throw BusinessException.conflict("ROOM_TYPE_CODE_ALREADY_EXISTS", "Un type de chambre porte déjà ce code.");
        }
        RoomType type = new RoomType(hotelId, code);
        apply(hotelId, type, request, true);
        roomTypes.saveAndFlush(type);
        audit.record(AuditEntry.of("ROOM_TYPE_CREATE", "RoomType", type.getId()).inHotel(null, hotelId)
                .withChange(null, describe(type)));
        return toResponse(type);
    }

    public RoomTypeResponse updateRoomType(UUID hotelId, UUID roomTypeId, RoomTypeRequest request) {
        RoomType type = loadType(hotelId, roomTypeId);
        if (request.version() == null || !Objects.equals(type.getVersion(), request.version())) {
            throw BusinessException.conflict("VERSION_CONFLICT", "Ce type a été modifié entre-temps. Rechargez puis réessayez.");
        }
        if (type.isActive() && !request.active() && rooms.countByRoomTypeIdAndActiveTrue(type.getId()) > 0) {
            throw BusinessException.conflict("ROOM_TYPE_IN_USE",
                    "Des chambres actives sont de ce type : désactivez-les ou changez leur type d'abord.");
        }
        Map<String, Object> before = describe(type);
        apply(hotelId, type, request, request.active());
        roomTypes.saveAndFlush(type);
        audit.record(AuditEntry.of("ROOM_TYPE_UPDATE", "RoomType", type.getId()).inHotel(null, hotelId)
                .withChange(before, describe(type)));
        return toResponse(type);
    }

    // --- Photos -------------------------------------------------------------------------------------------

    public UUID addPhoto(UUID hotelId, UUID roomTypeId, byte[] content, String contentType) {
        loadType(hotelId, roomTypeId);
        if (contentType == null || !IMAGE_TYPES.contains(contentType)) {
            throw BusinessException.unprocessable("UNSUPPORTED_IMAGE_TYPE", "La photo doit être une image PNG ou JPEG.");
        }
        if (content.length == 0 || content.length > MAX_IMAGE_BYTES) {
            throw BusinessException.unprocessable("IMAGE_TOO_LARGE", "La photo ne doit pas dépasser 500 Ko.");
        }
        if (photoIds(roomTypeId).size() >= MAX_PHOTOS_PER_TYPE) {
            throw BusinessException.unprocessable("TOO_MANY_PHOTOS", "Huit photos au maximum par type de chambre.");
        }
        UUID photoId = UuidV7.generate();
        jdbc.sql("""
                        INSERT INTO room_type_photo (id, room_type_id, content, content_type, sort_order, created_at)
                        VALUES (:id, :typeId, :content, :contentType,
                                (SELECT coalesce(max(sort_order), -1) + 1 FROM room_type_photo WHERE room_type_id = :typeId),
                                :now)
                        """)
                .param("id", photoId)
                .param("typeId", roomTypeId)
                .param("content", content)
                .param("contentType", contentType)
                .param("now", OffsetDateTime.now(clock))
                .update();
        audit.record(AuditEntry.of("ROOM_TYPE_PHOTO_ADD", "RoomType", roomTypeId).inHotel(null, hotelId)
                .withChange(null, Map.of("photoId", photoId.toString(), "size", content.length)));
        return photoId;
    }

    @Transactional(readOnly = true)
    public Photo photo(UUID hotelId, UUID roomTypeId, UUID photoId) {
        loadType(hotelId, roomTypeId);
        return jdbc.sql("SELECT content, content_type FROM room_type_photo WHERE id = :id AND room_type_id = :typeId")
                .param("id", photoId)
                .param("typeId", roomTypeId)
                .query((rs, rowNum) -> new Photo(rs.getBytes("content"), rs.getString("content_type")))
                .optional()
                .orElseThrow(() -> BusinessException.notFound("PHOTO_NOT_FOUND", "Photo introuvable."));
    }

    public void deletePhoto(UUID hotelId, UUID roomTypeId, UUID photoId) {
        loadType(hotelId, roomTypeId);
        int deleted = jdbc.sql("DELETE FROM room_type_photo WHERE id = :id AND room_type_id = :typeId")
                .param("id", photoId)
                .param("typeId", roomTypeId)
                .update();
        if (deleted == 0) {
            throw BusinessException.notFound("PHOTO_NOT_FOUND", "Photo introuvable.");
        }
        audit.record(AuditEntry.of("ROOM_TYPE_PHOTO_DELETE", "RoomType", roomTypeId).inHotel(null, hotelId)
                .withChange(Map.of("photoId", photoId.toString()), null));
    }

    // --- Outils --------------------------------------------------------------------------------------------

    RoomType loadType(UUID hotelId, UUID roomTypeId) {
        return roomTypes.findById(roomTypeId).filter(t -> t.getHotelId().equals(hotelId))
                .orElseThrow(() -> BusinessException.notFound("ROOM_TYPE_NOT_FOUND", "Type de chambre introuvable."));
    }

    private void apply(UUID hotelId, RoomType type, RoomTypeRequest request, boolean active) {
        if (request.baseOccupancy() > request.maxOccupancy()) {
            throw invalid("baseOccupancy", "L'occupation de base ne peut pas dépasser la capacité maximale.");
        }
        if (request.maxOccupancy() > request.maxAdults() + request.maxChildren()) {
            throw invalid("maxOccupancy", "La capacité maximale dépasse le nombre d'adultes et d'enfants autorisés.");
        }
        Set<UUID> amenityIds = request.amenityIds() == null ? Set.of() : request.amenityIds();
        boolean unknownAmenity = amenityIds.stream()
                .anyMatch(id -> amenities.findById(id).filter(a -> a.getHotelId().equals(hotelId)).isEmpty());
        if (unknownAmenity) {
            throw BusinessException.unprocessable("AMENITY_NOT_FOUND", "Un des équipements choisis est introuvable.");
        }
        type.update(request.name().trim(), blankToNull(request.description()), request.baseOccupancy(),
                request.maxOccupancy(), request.maxAdults(), request.maxChildren(), request.basePrice(), request.sortOrder(),
                active, amenityIds);
    }

    private List<UUID> photoIds(UUID roomTypeId) {
        return jdbc.sql("SELECT id FROM room_type_photo WHERE room_type_id = :typeId ORDER BY sort_order, created_at")
                .param("typeId", roomTypeId)
                .query(UUID.class)
                .list();
    }

    private RoomTypeResponse toResponse(RoomType type) {
        return new RoomTypeResponse(type.getId(), type.getCode(), type.getName(), type.getDescription(),
                type.getBaseOccupancy(), type.getMaxOccupancy(), type.getMaxAdults(), type.getMaxChildren(),
                type.getBasePrice(), type.getSortOrder(), type.isActive(), type.getAmenityIds(), photoIds(type.getId()),
                rooms.countByRoomTypeIdAndActiveTrue(type.getId()), type.getVersion());
    }

    private static Map<String, Object> describe(RoomType type) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", type.getCode());
        values.put("name", type.getName());
        values.put("baseOccupancy", type.getBaseOccupancy());
        values.put("maxOccupancy", type.getMaxOccupancy());
        values.put("maxAdults", type.getMaxAdults());
        values.put("maxChildren", type.getMaxChildren());
        values.put("basePrice", type.getBasePrice());
        values.put("active", type.isActive());
        return values;
    }

    private static BusinessException invalid(String field, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message,
                List.of(Map.of("field", field, "issue", message)));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
