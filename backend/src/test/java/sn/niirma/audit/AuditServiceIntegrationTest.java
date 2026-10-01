package sn.niirma.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import sn.niirma.support.IntegrationTest;

@IntegrationTest
class AuditServiceIntegrationTest {

    @Autowired
    AuditService auditService;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void recordsTheChangeWithOldAndNewValues() {
        String entityId = UUID.randomUUID().toString();

        transactionTemplate.executeWithoutResult(status -> auditService.record(
                AuditEntry.of("RATE_UPDATE", "Rate", entityId)
                        .withChange(Map.of("price", 45000), Map.of("price", 50000))
                        .withReason("Haute saison")));

        Map<String, Object> row = jdbc.sql("""
                        SELECT result, reason, old_value ->> 'price' AS old_price, new_value ->> 'price' AS new_price
                        FROM audit_log WHERE entity_id = :id
                        """)
                .param("id", entityId)
                .query()
                .singleRow();
        assertThat(row).containsEntry("result", "SUCCESS")
                .containsEntry("reason", "Haute saison")
                .containsEntry("old_price", "45000")
                .containsEntry("new_price", "50000");
    }

    @Test
    void aSuccessIsRolledBackWithTheBusinessTransaction() {
        String entityId = UUID.randomUUID().toString();

        transactionTemplate.executeWithoutResult(status -> {
            auditService.record(AuditEntry.of("PAYMENT_CREATE", "Payment", entityId));
            status.setRollbackOnly();
        });

        assertThat(countFor(entityId)).isZero();
    }

    @Test
    void aFailureIsKeptEvenWhenTheBusinessTransactionRollsBack() {
        String entityId = UUID.randomUUID().toString();

        transactionTemplate.executeWithoutResult(status -> {
            auditService.recordFailure(AuditEntry.of("PAYMENT_REFUND", "Payment", entityId).failed("REFUND_EXCEEDS_PAYMENT"));
            status.setRollbackOnly();
        });

        assertThat(countFor(entityId)).isEqualTo(1);
    }

    @Test
    void auditLinesCannotBeModifiedOrDeleted() {
        String entityId = UUID.randomUUID().toString();
        transactionTemplate.executeWithoutResult(status -> auditService.record(AuditEntry.of("ROOM_UPDATE", "Room", entityId)));

        assertThatThrownBy(() -> jdbc.sql("UPDATE audit_log SET action = 'X' WHERE entity_id = :id")
                .param("id", entityId).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ajout seul");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_log WHERE entity_id = :id")
                .param("id", entityId).update())
                .isInstanceOf(DataAccessException.class);
        assertThat(countFor(entityId)).isEqualTo(1);
    }

    private long countFor(String entityId) {
        return jdbc.sql("SELECT count(*) FROM audit_log WHERE entity_id = :id")
                .param("id", entityId)
                .query(Long.class)
                .single();
    }
}
