package sn.niirma.rooms.internal;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import sn.niirma.rooms.HousekeepingStatus;
import sn.niirma.rooms.OccupancyStatus;
import sn.niirma.shared.persistence.BaseEntity;

/**
 * Chambre (§9.2). L'occupation et le ménage sont stockés ; la disponibilité technique est déduite
 * des blocages. La chambre ne porte pas de prix : il vient du type et de la tarification.
 */
@Entity
@Table(name = "room")
public class Room extends BaseEntity {

    private UUID hotelId;

    private UUID roomTypeId;

    private String number;

    private Integer floor;

    private String building;

    private boolean accessible;

    /** Caractéristiques libres séparées par des virgules (vue mer, communicante avec 102…). */
    private String features;

    private String notes;

    @Enumerated(EnumType.STRING)
    private OccupancyStatus occupancyStatus;

    @Enumerated(EnumType.STRING)
    private HousekeepingStatus housekeepingStatus;

    private boolean active;

    protected Room() {
    }

    public Room(UUID hotelId, String number) {
        this.hotelId = hotelId;
        this.number = number;
        this.occupancyStatus = OccupancyStatus.VACANT;
        this.housekeepingStatus = HousekeepingStatus.CLEAN;
        this.active = true;
    }

    public void update(UUID roomTypeId, Integer floor, String building, boolean accessible, List<String> features,
                       String notes, boolean active) {
        this.roomTypeId = roomTypeId;
        this.floor = floor;
        this.building = building;
        this.accessible = accessible;
        this.features = features == null || features.isEmpty() ? null : String.join(",", features);
        this.notes = notes;
        this.active = active;
    }

    public void changeHousekeepingStatus(HousekeepingStatus status) {
        this.housekeepingStatus = status;
    }

    public UUID getHotelId() {
        return hotelId;
    }

    public UUID getRoomTypeId() {
        return roomTypeId;
    }

    public String getNumber() {
        return number;
    }

    public Integer getFloor() {
        return floor;
    }

    public String getBuilding() {
        return building;
    }

    public boolean isAccessible() {
        return accessible;
    }

    public List<String> getFeatures() {
        return features == null ? List.of() : Arrays.stream(features.split(",")).map(String::trim).filter(f -> !f.isEmpty()).toList();
    }

    public String getNotes() {
        return notes;
    }

    public OccupancyStatus getOccupancyStatus() {
        return occupancyStatus;
    }

    public HousekeepingStatus getHousekeepingStatus() {
        return housekeepingStatus;
    }

    public boolean isActive() {
        return active;
    }
}
