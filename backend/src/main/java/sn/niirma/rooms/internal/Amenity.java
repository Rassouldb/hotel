package sn.niirma.rooms.internal;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import sn.niirma.shared.persistence.BaseEntity;

/** Équipement proposé dans un type de chambre (climatisation, minibar…). */
@Entity
@Table(name = "amenity")
public class Amenity extends BaseEntity {

    private UUID hotelId;

    private String code;

    private String name;

    private boolean active;

    protected Amenity() {
    }

    public Amenity(UUID hotelId, String code, String name) {
        this.hotelId = hotelId;
        this.code = code;
        this.name = name;
        this.active = true;
    }

    public void update(String name, boolean active) {
        this.name = name;
        this.active = active;
    }

    public UUID getHotelId() {
        return hotelId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
