package sn.niirma.rooms.internal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.niirma.audit.AuditEntry;
import sn.niirma.audit.AuditService;
import sn.niirma.hotel.HotelDirectory;
import sn.niirma.rooms.BlockType;
import sn.niirma.rooms.internal.RoomStatusHistory.Dimension;
import sn.niirma.shared.error.BusinessException;
import sn.niirma.shared.security.CurrentUser;

/**
 * Blocages de chambres (US-ROOM-05) : périodes datées [début, fin[ qui retirent une chambre de la
 * vente. Deux blocages d'une même chambre ne se chevauchent jamais (contrainte d'exclusion).
 */
@Service
@Transactional
class RoomBlockService {

    record BlockRequest(@NotNull UUID roomId, @NotNull BlockType type, @NotNull LocalDate startDate,
                        @NotNull LocalDate endDate, @NotBlank @Size(max = 255) String reason) {
    }

    record BlockResponse(UUID id, UUID roomId, String roomNumber, BlockType type, LocalDate startDate, LocalDate endDate,
                         String reason, boolean inProgress, OffsetDateTime releasedAt) {
    }

    private final RoomBlockRepository blocks;
    private final RoomRepository rooms;
    private final RoomStatusHistory history;
    private final HotelDirectory hotelDirectory;
    private final AuditService audit;
    private final Clock clock;

    RoomBlockService(RoomBlockRepository blocks, RoomRepository rooms, RoomStatusHistory history,
                     HotelDirectory hotelDirectory, AuditService audit, Clock clock) {
        this.blocks = blocks;
        this.rooms = rooms;
        this.history = history;
        this.hotelDirectory = hotelDirectory;
        this.audit = audit;
        this.clock = clock;
    }

    /** Blocages qui chevauchent [from, to[ ; par défaut, ceux en cours et à venir sur 90 jours. */
    @Transactional(readOnly = true)
    public List<BlockResponse> list(UUID hotelId, LocalDate from, LocalDate to) {
        LocalDate businessDate = hotelDirectory.businessDate(hotelId);
        LocalDate start = from != null ? from : businessDate;
        LocalDate end = to != null ? to : start.plusDays(90);
        Map<UUID, Room> roomIndex = rooms.findByHotelId(hotelId).stream().collect(Collectors.toMap(Room::getId, Function.identity()));
        return blocks.findOverlapping(hotelId, start, end).stream()
                .map(block -> toResponse(block, roomIndex.get(block.getRoomId()), businessDate))
                .toList();
    }

    public BlockResponse create(UUID hotelId, BlockRequest request) {
        LocalDate businessDate = hotelDirectory.businessDate(hotelId);
        Room room = rooms.findById(request.roomId()).filter(r -> r.getHotelId().equals(hotelId))
                .orElseThrow(() -> BusinessException.unprocessable("ROOM_NOT_FOUND", "Chambre introuvable."));
        if (!request.endDate().isAfter(request.startDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "La date de fin doit être postérieure à la date de début.",
                    List.of(Map.of("field", "endDate", "issue", "BEFORE_START")));
        }
        if (request.startDate().isBefore(businessDate)) {
            throw BusinessException.unprocessable("BLOCK_IN_PAST",
                    "Un blocage ne peut pas commencer avant la date d'exploitation (" + businessDate + ").");
        }
        RoomBlock block = new RoomBlock(hotelId, room.getId(), request.type(), request.startDate(), request.endDate(),
                request.reason().trim());
        try {
            blocks.saveAndFlush(block);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict("ROOM_BLOCK_OVERLAP",
                    "La chambre " + room.getNumber() + " est déjà bloquée sur une partie de cette période.");
        }
        history.record(hotelId, room.getId(), Dimension.TECHNICAL, null, block.getType().name(),
                block.getReason() + " (" + block.getStartDate() + " → " + block.getEndDate() + ")", businessDate);
        audit.record(AuditEntry.of("ROOM_BLOCK_CREATE", "RoomBlock", block.getId()).inHotel(null, hotelId)
                .withChange(null, describe(block, room)).withReason(block.getReason()));
        return toResponse(block, room, businessDate);
    }

    /** Fin anticipée d'un blocage en cours : la chambre redevient disponible dès la date d'exploitation. */
    public BlockResponse release(UUID hotelId, UUID blockId) {
        LocalDate businessDate = hotelDirectory.businessDate(hotelId);
        RoomBlock block = load(hotelId, blockId);
        Room room = rooms.findById(block.getRoomId()).orElseThrow();
        if (!block.getEndDate().isAfter(businessDate)) {
            throw BusinessException.conflict("BLOCK_ENDED", "Ce blocage est déjà terminé.");
        }
        if (block.getStartDate().isAfter(businessDate)) {
            throw BusinessException.conflict("BLOCK_NOT_STARTED", "Ce blocage n'a pas commencé : supprimez-le.");
        }
        Map<String, Object> before = describe(block, room);
        if (!block.getStartDate().isBefore(businessDate)) {
            blocks.delete(block);
        } else {
            block.releaseOn(businessDate, OffsetDateTime.now(clock), CurrentUser.current().map(CurrentUser::userId).orElse(null));
            blocks.saveAndFlush(block);
        }
        history.record(hotelId, room.getId(), Dimension.TECHNICAL, block.getType().name(), "IN_SERVICE", "Blocage levé",
                businessDate);
        audit.record(AuditEntry.of("ROOM_BLOCK_RELEASE", "RoomBlock", block.getId()).inHotel(null, hotelId)
                .withChange(before, Map.of("endDate", businessDate.toString())));
        return toResponse(block, room, businessDate);
    }

    /** Suppression d'un blocage qui n'a pas encore commencé. */
    public void delete(UUID hotelId, UUID blockId) {
        LocalDate businessDate = hotelDirectory.businessDate(hotelId);
        RoomBlock block = load(hotelId, blockId);
        if (!block.getStartDate().isAfter(businessDate)) {
            throw BusinessException.conflict("BLOCK_STARTED", "Ce blocage a commencé : levez-le au lieu de le supprimer.");
        }
        Room room = rooms.findById(block.getRoomId()).orElseThrow();
        blocks.delete(block);
        history.record(hotelId, room.getId(), Dimension.TECHNICAL, block.getType().name(), null,
                "Blocage supprimé (" + block.getStartDate() + " → " + block.getEndDate() + ")", businessDate);
        audit.record(AuditEntry.of("ROOM_BLOCK_DELETE", "RoomBlock", block.getId()).inHotel(null, hotelId)
                .withChange(describe(block, room), null));
    }

    private RoomBlock load(UUID hotelId, UUID blockId) {
        return blocks.findById(blockId).filter(block -> block.getHotelId().equals(hotelId))
                .orElseThrow(() -> BusinessException.notFound("ROOM_BLOCK_NOT_FOUND", "Blocage introuvable."));
    }

    private static Map<String, Object> describe(RoomBlock block, Room room) {
        return Map.of("room", room.getNumber(), "type", block.getType().name(), "startDate", block.getStartDate().toString(),
                "endDate", block.getEndDate().toString(), "reason", block.getReason());
    }

    private static BlockResponse toResponse(RoomBlock block, Room room, LocalDate businessDate) {
        return new BlockResponse(block.getId(), block.getRoomId(), room == null ? null : room.getNumber(), block.getType(),
                block.getStartDate(), block.getEndDate(), block.getReason(), block.covers(businessDate), block.getReleasedAt());
    }
}
