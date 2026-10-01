package sn.niirma.audit;

import java.time.Clock;
import java.time.OffsetDateTime;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import sn.niirma.shared.persistence.UuidV7;
import sn.niirma.shared.security.CurrentUser;
import sn.niirma.shared.web.TraceContext;

/**
 * Journal d'audit en ajout seul. L'auteur et l'organisation sont ceux de l'utilisateur authentifié.
 * Un succès est écrit dans la transaction métier (il disparaît si elle est annulée) ; un échec est
 * écrit dans une transaction indépendante pour être conservé.
 */
@Service
public class AuditService {

    private static final int USER_AGENT_MAX_LENGTH = 512;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    AuditService(JdbcClient jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void record(AuditEntry entry) {
        insert(entry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(AuditEntry entry) {
        insert(entry.result() == AuditEntry.AuditResult.FAILURE ? entry : entry.failed(null));
    }

    private void insert(AuditEntry entry) {
        HttpServletRequest request = currentRequest();
        jdbc.sql("""
                        INSERT INTO audit_log (id, occurred_at, organization_id, hotel_id, user_id, user_name, action,
                                               entity_type, entity_id, old_value, new_value, reason, ip,
                                               user_agent, result, error_code, trace_id)
                        VALUES (:id, :occurredAt, :organizationId, :hotelId, :userId, :userName, :action,
                                :entityType, :entityId, CAST(:oldValue AS jsonb), CAST(:newValue AS jsonb), :reason, :ip,
                                :userAgent, :result, :errorCode, :traceId)
                        """)
                .param("id", UuidV7.generate())
                .param("occurredAt", OffsetDateTime.now(clock))
                .param("organizationId", entry.organizationId() != null
                        ? entry.organizationId()
                        : CurrentUser.current().map(CurrentUser::organizationId).orElse(null))
                .param("userId", CurrentUser.current().map(CurrentUser::userId).orElse(null))
                .param("userName", CurrentUser.current().map(CurrentUser::username).orElse(null))
                .param("hotelId", entry.hotelId())
                .param("action", entry.action())
                .param("entityType", entry.entityType())
                .param("entityId", entry.entityId())
                .param("oldValue", toJson(entry.oldValue()))
                .param("newValue", toJson(entry.newValue()))
                .param("reason", entry.reason())
                .param("ip", request != null ? request.getRemoteAddr() : null)
                .param("userAgent", request != null ? truncate(request.getHeader("User-Agent")) : null)
                .param("result", entry.result().name())
                .param("errorCode", entry.errorCode())
                .param("traceId", TraceContext.currentTraceId())
                .update();
    }

    private String toJson(Object value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }

    private static String truncate(String value) {
        return value == null || value.length() <= USER_AGENT_MAX_LENGTH ? value : value.substring(0, USER_AGENT_MAX_LENGTH);
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }
}
