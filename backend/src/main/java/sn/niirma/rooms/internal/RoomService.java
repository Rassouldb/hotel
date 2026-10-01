package sn.niirma.rooms.internal;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.niirma.audit.AuditEntry;
import sn.niirma.audit.AuditService;
import sn.niirma.hotel.HotelDirectory;
import sn.niirma.rooms.BlockType;
import sn.niirma.rooms.HousekeepingStatus;
import sn.niirma.rooms.OccupancyStatus;
import sn.niirma.rooms.TechnicalStatus;
import sn.niirma.rooms.internal.RoomStatusHistory.Dimension;
import sn.niirma.shared.error.BusinessException;
import sn.niirma.shared.security.AccessControl;

/**
 * Chambres (US-ROOM-02 à 04) : création, création en série, état des trois dimensions de statut,
 * mise à jour du statut ménage et historique.
 */
@Service
@Transactional
class RoomService {

    static final int MAX_BATCH = 200;

    record RoomRequest(
            @Pattern(regexp = "[A-Za-z0-9-]{1,10}") String number,
            @NotNull UUID roomTypeId,
            @Min(-5) @Max(200) Integer floor,
            @Size(max = 50) String building,
            boolean accessible,
            List<@Size(max = 50) String> features,
            @Size(max = 1000) String notes,
            boolean active,
            Long version) {
    }

    record BatchRequest(@Size(max = 5) String prefix, @Min(0) int from, @Min(0) int to, @NotNull UUID roomTypeId,
                        @Min(-5) @Max(200) Integer floor, @Size(max = 50) String building) {
    }

    record HousekeepingRequest(@NotNull HousekeepingStatus status, @Size(max = 255) String reason) {
    }

    record CurrentBlock(UUID id, BlockType type, LocalDate startDate, LocalDate endDate, String reason) {
    }

    record RoomResponse(UUID id, String number, Integer floor, String building, UUID roomTypeId, String roomTypeCode,
                        String roomTypeName, boolean accessible, List<String> features, String notes,
                        OccupancyStatus occupancyStatus, HousekeepingStatus housekeepingStatus,
                        TechnicalStatus technicalStatus, boolean saleable, CurrentBlock currentBlock, boolean active,
                        Long version) {
    }

    /** Transitions manuelles autorisées ; le retour à DIRTY est possible depuis tout statut (motif obligatoire). */
    private static final Map<HousekeepingStatus, Set<HousekeepingStatus>> TRANSITIONS = Map.of(
            HousekeepingStatus.DIRTY, EnumSet.of(HousekeepingStatus.CLEANING, HousekeepingStatus.CLEAN),
            HousekeepingStatus.CLEANING, EnumSet.of(HousekeepingStatus.CLEAN, HousekeepingStatus.DIRTY),
            HousekeepingStatus.CLEAN, EnumSet.of(HousekeepingStatus.INSPECTED, HousekeepingStatus.DIRTY),
            HousekeepingStatus.INSPECTED, EnumSet.of(HousekeepingStatus.DIRTY));

    private final RoomRepository rooms;
    private final RoomTypeRepository roomTypes;
    private final RoomBlockRepository blocks;
    private final RoomStatusHistory history;
    private final HotelDirectory hotelDirectory;
    private final AccessControl accessControl;
    private final AuditService audit;

    RoomService(RoomRepository rooms, RoomTypeRepository roomTypes, RoomBlockRepository blocks, RoomStatusHistory history,
                HotelDirectory hotelDirectory, AccessControl accessControl, AuditService audit) {
        this.rooms = rooms;
        this.roomTypes = roomTypes;
        this.blocks = blocks;
        this.history = history;
        this.hotelDirectory = hotelDirectory;
        this.accessControl = accessControl;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> list(UUID hotelId) {
        LocalDate businessDate = hotelDirectory.businessDate(hotelId);
        Map<UUID, RoomType> types = roomTypes.findByHotelIdOrderBySortOrderAscNameAsc(hotelId).stream()
                .collect(Collectors.toMap(RoomType::getId, Function.identity()));
        Map<UUID, RoomBlock> currentBlocks = blocks.findOverlapping(hotelId, businessDate, businessDate.plusDays(1)).stream()
                .collect(Collectors.toMap(RoomBlock::getRoomId, Function.identity(), (a, b) -> a));
        return rooms.findByHotelId(hotelId).stream()
                .sorted(Comparator.comparing((Room r) -> r.getFloor() == null ? Integer.MAX_VALUE : r.getFloor())
                        .thenComparing(RoomService::numberSortKey))
                .map(room -> toResponse(room, types.get(room.getRoomTypeId()), currentBlocks.get(room.getId())))
                .toList();
    }

    public RoomResponse create(UUID hotelId, RoomRequest request) {
        if (request.number() == null || request.number().isBlank()) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "Le numéro de chambre est obligatoire.");
        }
        String number = request.number().trim().toUpperCase();
        if (rooms.existsByHotelIdAndNumber(hotelId, number)) {
            throw BusinessException.conflict("ROOM_NUMBER_ALREADY_EXISTS", "La chambre " + number + " existe déjà.");
        }
        RoomType type = activeType(hotelId, request.roomTypeId());
        Room room = new Room(hotelId, number);
        room.update(type.getId(), request.floor(), blankToNull(request.building()), request.accessible(),
                cleanFeatures(request.features()), blankToNull(request.notes()), true);
        rooms.saveAndFlush(room);
        audit.record(AuditEntry.of("ROOM_CREATE", "Room", room.getId()).inHotel(null, hotelId)
                .withChange(null, describe(room, type)));
        return toResponse(room, type, null);
    }

    /** Création en série (ex. 101 à 120) : tout ou rien. */
    public List<RoomResponse> createBatch(UUID hotelId, BatchRequest request) {
        if (request.to() < request.from() || request.to() - request.from() + 1 > MAX_BATCH) {
            throw BusinessException.badRequest("VALIDATION_ERROR",
                    "Indiquez une plage croissante de " + MAX_BATCH + " chambres au maximum.");
        }
        RoomType type = activeType(hotelId, request.roomTypeId());
        String prefix = request.prefix() == null ? "" : request.prefix().trim().toUpperCase();
        List<String> numbers = IntStream.rangeClosed(request.from(), request.to()).mapToObj(n -> prefix + n).toList();
        List<String> existing = rooms.findByHotelIdAndNumberIn(hotelId, numbers).stream().map(Room::getNumber).sorted().toList();
        if (!existing.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "ROOM_NUMBER_ALREADY_EXISTS",
                    "Ces chambres existent déjà : " + String.join(", ", existing),
                    existing.stream().map(n -> Map.<String, Object>of("number", n)).toList());
        }
        List<Room> created = numbers.stream().map(number -> {
            Room room = new Room(hotelId, number);
            room.update(type.getId(), request.floor(), blankToNull(request.building()), false, List.of(), null, true);
            return room;
        }).toList();
        rooms.saveAllAndFlush(created);
        audit.record(AuditEntry.of("ROOM_BATCH_CREATE", "Room", numbers.getFirst() + "-" + numbers.getLast())
                .inHotel(null, hotelId)
                .withChange(null, Map.of("numbers", numbers, "roomType", type.getCode())));
        return created.stream().map(room -> toResponse(room, type, null)).toList();
    }

    public RoomResponse update(UUID hotelId, UUID roomId, RoomRequest request) {
        Room room = load(hotelId, roomId);
        if (request.version() == null || !Objects.equals(room.getVersion(), request.version())) {
            throw BusinessException.conflict("VERSION_CONFLICT", "Cette chambre a été modifiée entre-temps. Rechargez puis réessayez.");
        }
        if (room.isActive() && !request.active() && room.getOccupancyStatus() == OccupancyStatus.OCCUPIED) {
            throw BusinessException.conflict("ROOM_OCCUPIED", "Une chambre occupée ne peut pas être désactivée.");
        }
        RoomType type = request.roomTypeId().equals(room.getRoomTypeId())
                ? roomTypes.findById(room.getRoomTypeId()).orElseThrow()
                : activeType(hotelId, request.roomTypeId());
        Map<String, Object> before = describe(room, roomTypes.findById(room.getRoomTypeId()).orElse(null));
        room.update(type.getId(), request.floor(), blankToNull(request.building()), request.accessible(),
                cleanFeatures(request.features()), blankToNull(request.notes()), request.active());
        rooms.saveAndFlush(room);
        audit.record(AuditEntry.of("ROOM_UPDATE", "Room", room.getId()).inHotel(null, hotelId)
                .withChange(before, describe(room, type)));
        return toResponse(room, type, currentBlock(hotelId, room.getId()));
    }

    /** Mise à jour manuelle du statut ménage (US-ROOM-04). */
    public RoomResponse changeHousekeepingStatus(UUID hotelId, UUID roomId, HousekeepingRequest request) {
        Room room = load(hotelId, roomId);
        HousekeepingStatus current = room.getHousekeepingStatus();
        HousekeepingStatus target = request.status();
        if (current != target) {
            if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
                throw BusinessException.conflict("INVALID_HOUSEKEEPING_TRANSITION",
                        "Passage de « " + current + " » à « " + target + " » impossible.");
            }
            if (target == HousekeepingStatus.DIRTY && (request.reason() == null || request.reason().isBlank())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Indiquez pourquoi la chambre redevient sale.",
                        List.of(Map.of("field", "reason", "issue", "REQUIRED")));
            }
            if (target == HousekeepingStatus.INSPECTED && !accessControl.hasPermission("HK_INSPECT", hotelId)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                        "L'inspection des chambres est réservée au responsable housekeeping.");
            }
            room.changeHousekeepingStatus(target);
            rooms.saveAndFlush(room);
            history.record(hotelId, room.getId(), Dimension.HOUSEKEEPING, current.name(), target.name(),
                    blankToNull(request.reason()), hotelDirectory.businessDate(hotelId));
            audit.record(AuditEntry.of("ROOM_HOUSEKEEPING_UPDATE", "Room", room.getId()).inHotel(null, hotelId)
                    .withChange(Map.of("housekeepingStatus", current.name()), Map.of("housekeepingStatus", target.name()))
                    .withReason(blankToNull(request.reason())));
        }
        RoomType type = roomTypes.findById(room.getRoomTypeId()).orElse(null);
        return toResponse(room, type, currentBlock(hotelId, room.getId()));
    }

    @Transactional(readOnly = true)
    public List<RoomStatusHistory.Entry> history(UUID hotelId, UUID roomId) {
        load(hotelId, roomId);
        return history.of(roomId, 100);
    }

    Room load(UUID hotelId, UUID roomId) {
        return rooms.findById(roomId).filter(room -> room.getHotelId().equals(hotelId))
                .orElseThrow(() -> BusinessException.notFound("ROOM_NOT_FOUND", "Chambre introuvable."));
    }

    private RoomType activeType(UUID hotelId, UUID roomTypeId) {
        RoomType type = roomTypes.findById(roomTypeId).filter(t -> t.getHotelId().equals(hotelId))
                .orElseThrow(() -> BusinessException.unprocessable("ROOM_TYPE_NOT_FOUND", "Type de chambre introuvable."));
        if (!type.isActive()) {
            throw BusinessException.unprocessable("ROOM_TYPE_INACTIVE", "Ce type de chambre est désactivé.");
        }
        return type;
    }

    private RoomBlock currentBlock(UUID hotelId, UUID roomId) {
        LocalDate businessDate = hotelDirectory.businessDate(hotelId);
        return blocks.findOverlapping(hotelId, businessDate, businessDate.plusDays(1)).stream()
                .filter(block -> block.getRoomId().equals(roomId))
                .findFirst()
                .orElse(null);
    }

    static RoomResponse toResponse(Room room, RoomType type, RoomBlock block) {
        TechnicalStatus technical = block == null ? TechnicalStatus.IN_SERVICE : switch (block.getType()) {
            case MAINTENANCE -> TechnicalStatus.MAINTENANCE;
            case OUT_OF_ORDER -> TechnicalStatus.OUT_OF_ORDER;
            case HOUSE_USE -> TechnicalStatus.IN_SERVICE;
        };
        boolean saleable = room.isActive() && block == null && type != null && type.isActive();
        CurrentBlock current = block == null ? null
                : new CurrentBlock(block.getId(), block.getType(), block.getStartDate(), block.getEndDate(), block.getReason());
        return new RoomResponse(room.getId(), room.getNumber(), room.getFloor(), room.getBuilding(), room.getRoomTypeId(),
                type == null ? null : type.getCode(), type == null ? null : type.getName(), room.isAccessible(),
                room.getFeatures(), room.getNotes(), room.getOccupancyStatus(), room.getHousekeepingStatus(), technical,
                saleable, current, room.isActive(), room.getVersion());
    }

    private static Map<String, Object> describe(Room room, RoomType type) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("number", room.getNumber());
        values.put("roomType", type == null ? null : type.getCode());
        values.put("floor", room.getFloor());
        values.put("building", room.getBuilding());
        values.put("accessible", room.isAccessible());
        values.put("features", room.getFeatures());
        values.put("active", room.isActive());
        return values;
    }

    /** Tri naturel des numéros : 2 avant 10, puis ordre alphabétique pour les numéros non numériques. */
    private static String numberSortKey(Room room) {
        String number = room.getNumber();
        return number.chars().allMatch(Character::isDigit) ? "%010d".formatted(Long.parseLong(number)) : "~" + number;
    }

    private static List<String> cleanFeatures(List<String> features) {
        return features == null ? List.of()
                : features.stream().filter(Objects::nonNull).map(String::trim).filter(f -> !f.isEmpty())
                .map(f -> f.replace(",", " ")).distinct().toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
