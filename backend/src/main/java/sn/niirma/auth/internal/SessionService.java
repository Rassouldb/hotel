package sn.niirma.auth.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import sn.niirma.iam.AuthEventLog;
import sn.niirma.iam.AuthEventType;
import sn.niirma.shared.error.BusinessException;
import sn.niirma.shared.persistence.UuidV7;

/**
 * Sessions de connexion et rotation des jetons de rafraîchissement (US-AUTH-02).
 *
 * <p>Chaque jeton ne sert qu'une fois. Un jeton déjà utilisé qui revient est le signe d'un vol : la
 * session entière est révoquée. Exception : dans les secondes qui suivent son usage (deux onglets
 * qui rafraîchissent en même temps), le second appel est simplement invité à réessayer.
 */
@Service
class SessionService {

    /** Délai pendant lequel un jeton tout juste remplacé n'est pas considéré comme volé. */
    static final Duration REUSE_GRACE = Duration.ofSeconds(30);

    record IssuedSession(UUID sessionId, String refreshToken) {
    }

    record Rotation(UUID userId, UUID sessionId, String refreshToken) {
    }

    record SessionView(UUID id, OffsetDateTime createdAt, OffsetDateTime lastActivityAt, String ip, String userAgent,
                       boolean current) {
    }

    private record StoredToken(UUID tokenId, UUID sessionId, UUID userId, OffsetDateTime usedAt,
                               OffsetDateTime sessionRevokedAt, OffsetDateTime expiresAt,
                               OffsetDateTime lastActivityAt) {
    }

    private final JdbcClient jdbc;
    private final AuthProperties properties;
    private final AuthEventLog authEvents;
    private final Clock clock;

    SessionService(JdbcClient jdbc, AuthProperties properties, AuthEventLog authEvents, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.authEvents = authEvents;
        this.clock = clock;
    }

    @Transactional
    public IssuedSession open(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID sessionId = UuidV7.generate();
        HttpServletRequest request = currentRequest();
        jdbc.sql("""
                        INSERT INTO user_session (id, user_id, created_at, last_activity_at, expires_at, ip, user_agent)
                        VALUES (:id, :userId, :now, :now, :expiresAt, :ip, :userAgent)
                        """)
                .param("id", sessionId)
                .param("userId", userId)
                .param("now", now)
                .param("expiresAt", now.plus(properties.sessionTtl()))
                .param("ip", request != null ? request.getRemoteAddr() : null)
                .param("userAgent", request != null ? truncate(request.getHeader("User-Agent")) : null)
                .update();
        return new IssuedSession(sessionId, newRefreshToken(sessionId, now));
    }

    /** Échange un jeton de rafraîchissement contre un nouveau. */
    @Transactional(noRollbackFor = BusinessException.class)
    public Rotation rotate(String refreshToken) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        StoredToken token = findForUpdate(refreshToken);
        if (token == null) {
            throw sessionExpired();
        }
        if (token.sessionRevokedAt() != null) {
            throw sessionRevoked();
        }
        if (token.usedAt() != null) {
            if (token.usedAt().plus(REUSE_GRACE).isAfter(now)) {
                throw BusinessException.conflict("REFRESH_IN_PROGRESS",
                        "La session est en cours de renouvellement. Réessayez.");
            }
            revoke(token.sessionId(), "REFRESH_TOKEN_REUSED");
            authEvents.record(AuthEventType.REFRESH_TOKEN_REUSED, token.userId(), null, token.sessionId().toString());
            throw sessionRevoked();
        }
        boolean expired = !token.expiresAt().isAfter(now)
                || token.lastActivityAt().plus(properties.inactivityTimeout()).isBefore(now);
        if (expired) {
            revoke(token.sessionId(), "EXPIRED");
            throw sessionExpired();
        }

        jdbc.sql("UPDATE refresh_token SET used_at = :now WHERE id = :id")
                .param("now", now)
                .param("id", token.tokenId())
                .update();
        jdbc.sql("UPDATE user_session SET last_activity_at = :now WHERE id = :id")
                .param("now", now)
                .param("id", token.sessionId())
                .update();
        return new Rotation(token.userId(), token.sessionId(), newRefreshToken(token.sessionId(), now));
    }

    /** Session à laquelle appartient un jeton de rafraîchissement, même déjà utilisé. */
    @Transactional(readOnly = true)
    public Optional<UUID> sessionOf(String refreshToken) {
        return jdbc.sql("SELECT session_id FROM refresh_token WHERE token_hash = :hash")
                .param("hash", SecretTokens.hash(refreshToken))
                .query(UUID.class)
                .optional();
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID sessionId) {
        return jdbc.sql("""
                        SELECT count(*) FROM user_session
                        WHERE id = :id AND revoked_at IS NULL AND expires_at > :now
                        """)
                .param("id", sessionId)
                .param("now", OffsetDateTime.now(clock))
                .query(Long.class)
                .single() > 0;
    }

    @Transactional
    public void revoke(UUID sessionId, String reason) {
        jdbc.sql("UPDATE user_session SET revoked_at = :now, revoke_reason = :reason WHERE id = :id AND revoked_at IS NULL")
                .param("now", OffsetDateTime.now(clock))
                .param("reason", reason)
                .param("id", sessionId)
                .update();
    }

    @Transactional
    public int revokeAllForUser(UUID userId, UUID keptSessionId, String reason) {
        return jdbc.sql("""
                        UPDATE user_session SET revoked_at = :now, revoke_reason = :reason
                        WHERE user_id = :userId AND revoked_at IS NULL AND (CAST(:kept AS uuid) IS NULL OR id <> :kept)
                        """)
                .param("now", OffsetDateTime.now(clock))
                .param("reason", reason)
                .param("userId", userId)
                .param("kept", keptSessionId)
                .update();
    }

    @Transactional(readOnly = true)
    public List<SessionView> listActive(UUID userId, UUID currentSessionId) {
        return jdbc.sql("""
                        SELECT id, created_at, last_activity_at, ip, user_agent FROM user_session
                        WHERE user_id = :userId AND revoked_at IS NULL AND expires_at > :now
                        ORDER BY last_activity_at DESC
                        """)
                .param("userId", userId)
                .param("now", OffsetDateTime.now(clock))
                .query((rs, rowNum) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new SessionView(id, rs.getObject("created_at", OffsetDateTime.class),
                            rs.getObject("last_activity_at", OffsetDateTime.class), rs.getString("ip"),
                            rs.getString("user_agent"), id.equals(currentSessionId));
                })
                .list();
    }

    /** Révoque une session de l'utilisateur ; une session d'un autre utilisateur est introuvable (404). */
    @Transactional
    public void revokeOwn(UUID userId, UUID sessionId) {
        int updated = jdbc.sql("""
                        UPDATE user_session SET revoked_at = :now, revoke_reason = 'REVOKED_BY_USER'
                        WHERE id = :id AND user_id = :userId AND revoked_at IS NULL
                        """)
                .param("now", OffsetDateTime.now(clock))
                .param("id", sessionId)
                .param("userId", userId)
                .update();
        if (updated == 0) {
            throw BusinessException.notFound("SESSION_NOT_FOUND", "Session introuvable.");
        }
    }

    private String newRefreshToken(UUID sessionId, OffsetDateTime now) {
        String token = SecretTokens.generate();
        jdbc.sql("INSERT INTO refresh_token (id, session_id, token_hash, created_at) VALUES (:id, :sessionId, :hash, :now)")
                .param("id", UuidV7.generate())
                .param("sessionId", sessionId)
                .param("hash", SecretTokens.hash(token))
                .param("now", now)
                .update();
        return token;
    }

    private StoredToken findForUpdate(String refreshToken) {
        return jdbc.sql("""
                        SELECT t.id AS token_id, t.used_at, s.id AS session_id, s.user_id, s.revoked_at,
                               s.expires_at, s.last_activity_at
                        FROM refresh_token t JOIN user_session s ON s.id = t.session_id
                        WHERE t.token_hash = :hash
                        FOR UPDATE OF t, s
                        """)
                .param("hash", SecretTokens.hash(refreshToken))
                .query((rs, rowNum) -> new StoredToken(
                        rs.getObject("token_id", UUID.class),
                        rs.getObject("session_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("used_at", OffsetDateTime.class),
                        rs.getObject("revoked_at", OffsetDateTime.class),
                        rs.getObject("expires_at", OffsetDateTime.class),
                        rs.getObject("last_activity_at", OffsetDateTime.class)))
                .optional()
                .orElse(null);
    }

    static BusinessException sessionExpired() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED",
                "Votre session a expiré. Veuillez vous reconnecter.");
    }

    static BusinessException sessionRevoked() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "SESSION_REVOKED",
                "Votre session a été fermée. Veuillez vous reconnecter.");
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 512 ? value : value.substring(0, 512);
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }
}
