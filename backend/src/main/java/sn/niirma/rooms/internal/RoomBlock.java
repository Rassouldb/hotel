package sn.niirma.rooms.internal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import sn.niirma.rooms.BlockType;
import sn.niirma.shared.persistence.BaseEntity;

/** Retrait d'une chambre de la vente sur la période [startDate, endDate[ (§9.4). */
@Entity
@Table(name = "room_block")
public class RoomBlock extends BaseEntity {

    private UUID hotelId;

    private UUID roomId;

    @Enumerated(EnumType.STRING)
    private BlockType type;

    private LocalDate startDate;

    private LocalDate endDate;

    private String reason;

    private OffsetDateTime releasedAt;

    private UUID releasedBy;

    protected RoomBlock() {
    }

    public RoomBlock(UUID hotelId, UUID roomId, BlockType type, LocalDate startDate, LocalDate endDate, String reason) {
        this.hotelId = hotelId;
        this.roomId = roomId;
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && date.isBefore(endDate);
    }

    /** Fin anticipée : la chambre redevient disponible à partir de {@code date}. */
    public void releaseOn(LocalDate date, OffsetDateTime at, UUID by) {
        this.endDate = date;
        this.releasedAt = at;
        this.releasedBy = by;
    }

    public UUID getHotelId() {
        return hotelId;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public BlockType getType() {
        return type;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getReleasedAt() {
        return releasedAt;
    }
}
