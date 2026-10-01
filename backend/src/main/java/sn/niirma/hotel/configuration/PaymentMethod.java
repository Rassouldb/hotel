package sn.niirma.hotel.configuration;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import sn.niirma.shared.persistence.BaseEntity;

/**
 * Moyen de paiement accepté par l'hôtel (§20.1). Les espèces passent toujours par la caisse.
 */
@Entity
@Table(name = "payment_method")
public class PaymentMethod extends BaseEntity {

    public enum Type { CASH, CARD, TRANSFER, CHEQUE, MOBILE_MONEY, OTHER }

    private UUID hotelId;

    private String code;

    private String name;

    @Enumerated(EnumType.STRING)
    private Type type;

    private boolean requiresCashSession;

    private boolean requiresReference;

    private boolean active;

    private int sortOrder;

    protected PaymentMethod() {
    }

    public PaymentMethod(UUID hotelId, String code, String name, Type type, boolean requiresCashSession,
                         boolean requiresReference, boolean active, int sortOrder) {
        this.hotelId = hotelId;
        this.code = code;
        this.type = type;
        update(name, requiresCashSession, requiresReference, active, sortOrder);
    }

    public void update(String name, boolean requiresCashSession, boolean requiresReference, boolean active, int sortOrder) {
        this.name = name;
        this.requiresCashSession = type == Type.CASH || requiresCashSession;
        this.requiresReference = requiresReference;
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

    public Type getType() {
        return type;
    }

    public boolean isRequiresCashSession() {
        return requiresCashSession;
    }

    public boolean isRequiresReference() {
        return requiresReference;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
