package sn.niirma.iam;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import sn.niirma.shared.persistence.UuidV7;
import sn.niirma.shared.web.TraceContext;

/**
 * Journal des connexions et événements d'authentification (§7.2). Chaque événement est écrit dans
 * sa propre transaction : un échec de connexion reste tracé même si la requête aboutit à une erreur.
 */
@Service
public class AuthEventLog {

    private static final int MAX_USER_AGENT = 512;
    private static final int MAX_USERNAME = 100;

    private final JdbcClient jdbc;
    private final Clock clock;

    AuthEventLog(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuthEventType event, UUID userId, String username, String detail) {
        HttpServletRequest request = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a
                ? a.getRequest()
                : null;
        jdbc.sql("""
                        INSERT INTO auth_event (id, occurred_at, user_id, username, event, ip, user_agent, detail, trace_id)
                        VALUES (:id, :at, :userId, :username, :event, :ip, :userAgent, :detail, :traceId)
                        """)
                .param("id", UuidV7.generate())
                .param("at", OffsetDateTime.now(clock))
                .param("userId", userId)
                .param("username", truncate(username, MAX_USERNAME))
                .param("event", event.name())
                .param("ip", request != null ? request.getRemoteAddr() : null)
                .param("userAgent", request != null ? truncate(request.getHeader("User-Agent"), MAX_USER_AGENT) : null)
                .param("detail", detail)
                .param("traceId", TraceContext.currentTraceId())
                .update();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
