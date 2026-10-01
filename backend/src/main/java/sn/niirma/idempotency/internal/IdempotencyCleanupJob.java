package sn.niirma.idempotency.internal;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Supprime les clés d'idempotence de plus de 24 heures (§32.4).
 */
@Component
class IdempotencyCleanupJob {

    static final Duration RETENTION = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

    private final IdempotencyStore store;

    IdempotencyCleanupJob(IdempotencyStore store) {
        this.store = store;
    }

    @Scheduled(cron = "${pms.idempotency.cleanup-cron:0 15 * * * *}")
    void purge() {
        int deleted = store.purgeOlderThan(RETENTION);
        if (deleted > 0) {
            log.info("{} clés d'idempotence expirées supprimées", deleted);
        }
    }
}
