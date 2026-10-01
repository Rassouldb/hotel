package sn.niirma.hotel.configuration;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import sn.niirma.shared.persistence.BaseEntity;

/**
 * Canal par lequel une réservation est arrivée (§10.3), avec sa commission éventuelle.
 */
@Entity
@Table(name = "reservation_channel")
public class ReservationChannel extends BaseEntity {

    private UUID hotelId;

    private String code;

    private String name;

    private BigDecimal commissionRate;

    private boolean active;

    private int sortOrder;

    protected ReservationChannel() {
    }

    public ReservationChannel(UUID hotelId, String code, String name, BigDecimal commissionRate, int sortOrder) {
        this.hotelId = hotelId;
        this.code = code;
        update(name, commissionRate, true, sortOrder);
    }

    public void update(String name, BigDecimal commissionRate, boolean active, int sortOrder) {
        this.name = name;
        this.commissionRate = commissionRate;
        this.active = active;
        this.sortOrder = sortOrder;
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

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
