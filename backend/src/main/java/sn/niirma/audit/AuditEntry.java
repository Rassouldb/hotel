package sn.niirma.audit;

import java.util.UUID;

/**
 * Opération sensible à journaliser (§32.1). Les valeurs avant/après sont sérialisées en JSON ;
 * ne transmettre que les champs modifiés.
 */
public record AuditEntry(
        String action,
        String entityType,
        String entityId,
        UUID organizationId,
        UUID hotelId,
        Object oldValue,
        Object newValue,
        String reason,
        AuditResult result,
        String errorCode) {

    public static AuditEntry of(String action, String entityType, Object entityId) {
        return new AuditEntry(action, entityType, String.valueOf(entityId), null, null, null, null, null,
                AuditResult.SUCCESS, null);
    }

    public AuditEntry inHotel(UUID organizationId, UUID hotelId) {
        return new AuditEntry(action, entityType, entityId, organizationId, hotelId, oldValue, newValue, reason,
                result, errorCode);
    }

    public AuditEntry withChange(Object oldValue, Object newValue) {
        return new AuditEntry(action, entityType, entityId, organizationId, hotelId, oldValue, newValue, reason,
                result, errorCode);
    }

    public AuditEntry withReason(String reason) {
        return new AuditEntry(action, entityType, entityId, organizationId, hotelId, oldValue, newValue, reason,
                result, errorCode);
    }

    public AuditEntry failed(String errorCode) {
        return new AuditEntry(action, entityType, entityId, organizationId, hotelId, oldValue, newValue, reason,
                AuditResult.FAILURE, errorCode);
    }

    public enum AuditResult {
        SUCCESS, FAILURE
    }
}
