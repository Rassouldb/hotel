package sn.niirma.idempotency.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sn.niirma.shared.persistence.UuidV7;

/**
 * Persistance des clés d'idempotence. Chaque écriture est autonome (hors transaction métier) afin
 * que la réservation de la clé soit visible immédiatement par les requêtes concurrentes.
 */
@Component
class IdempotencyStore {

    enum Status { IN_PROGRESS, COMPLETED }

    record StoredKey(String requestHash, Status status, Integer responseStatus, String responseBody,
                     OffsetDateTime createdAt) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;

    IdempotencyStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Réserve la clé ; renvoie {@code false} si elle existe déjà pour ce périmètre. */
    boolean tryAcquire(String scope, String key, String requestHash) {
        int inserted = jdbc.sql("""
                        INSERT INTO idempotency_key (id, scope, idem_key, request_hash, status, created_at)
                        VALUES (:id, :scope, :key, :hash, 'IN_PROGRESS', :now)
                        ON CONFLICT (scope, idem_key) DO NOTHING
                        """)
                .param("id", UuidV7.generate())
                .param("scope", scope)
                .param("key", key)
                .param("hash", requestHash)
                .param("now", OffsetDateTime.now(clock))
                .update();
        return inserted == 1;
    }

    Optional<StoredKey> find(String scope, String key) {
        return jdbc.sql("""
                        SELECT request_hash, status, response_status, response_body::text AS response_body, created_at
                        FROM idempotency_key WHERE scope = :scope AND idem_key = :key
                        """)
                .param("scope", scope)
                .param("key", key)
                .query((rs, rowNum) -> new StoredKey(
                        rs.getString("request_hash"),
                        Status.valueOf(rs.getString("status")),
                        (Integer) rs.getObject("response_status"),
                        rs.getString("response_body"),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .optional();
    }

    void complete(String scope, String key, int responseStatus, String responseBody) {
        jdbc.sql("""
                        UPDATE idempotency_key
                        SET status = 'COMPLETED', response_status = :status,
                            response_body = CAST(:body AS jsonb), completed_at = :now
                        WHERE scope = :scope AND idem_key = :key
                        """)
                .param("status", responseStatus)
                .param("body", responseBody)
                .param("now", OffsetDateTime.now(clock))
                .param("scope", scope)
                .param("key", key)
                .update();
    }

    void release(String scope, String key) {
        jdbc.sql("DELETE FROM idempotency_key WHERE scope = :scope AND idem_key = :key AND status = 'IN_PROGRESS'")
                .param("scope", scope)
                .param("key", key)
                .update();
    }

    int purgeOlderThan(Duration retention) {
        return jdbc.sql("DELETE FROM idempotency_key WHERE created_at < :limit")
                .param("limit", OffsetDateTime.now(clock).minus(retention))
                .update();
    }
}
