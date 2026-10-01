package sn.niirma.hotel.configuration;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import sn.niirma.shared.persistence.BaseEntity;

/**
 * Motif proposé à l'utilisateur pour une opération qui en exige un (annulation, remise, blocage…).
 */
@Entity
@Table(name = "reason_code")
public class ReasonCode extends BaseEntity {

    public enum Domain { CANCELLATION, DISCOUNT, ROOM_CHANGE, ROOM_BLOCK, ADJUSTMENT, HOUSEKEEPING }

    private UUID hotelId;

    @Enumerated(EnumType.STRING)
    private Domain domain;

    private String code;

    private String label;

    private boolean active;

    private int sortOrder;

    protected ReasonCode() {
    }

    public ReasonCode(UUID hotelId, Domain domain, String code, String label, int sortOrder) {
        this.hotelId = hotelId;
        this.domain = domain;
        this.code = code;
        update(label, true, sortOrder);
    }

    public void update(String label, boolean active, int sortOrder) {
        this.label = label;
        this.active = active;
        this.sortOrder = sortOrder;
    }

    public UUID getHotelId() {
        return hotelId;
    }

    public Domain getDomain() {
        return domain;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
