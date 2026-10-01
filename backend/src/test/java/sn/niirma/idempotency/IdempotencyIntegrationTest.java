package sn.niirma.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sn.niirma.support.IntegrationTest;
import sn.niirma.support.TestOperationsController;

@IntegrationTest
class IdempotencyIntegrationTest {

    private static final String BODY = "{\"label\": \"Acompte\", \"amount\": 50000}";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void resetCounter() {
        TestOperationsController.EXECUTIONS.set(0);
    }

    @Test
    void aRepeatedRequestIsExecutedOnceAndTheFirstResponseIsReplayed() throws Exception {
        String key = newKey();

        MvcTestResult first = post("/api/v1/test-operations", key, BODY);
        MvcTestResult second = post("/api/v1/test-operations", key, BODY);

        assertThat(first).hasStatus(201);
        assertThat(second).hasStatus(201);
        assertThat(second).hasHeader(Idempotent.REPLAYED_HEADER, "true");
        assertThat(second).bodyJson().isLenientlyEqualTo(first.getResponse().getContentAsString());
        assertThat(TestOperationsController.EXECUTIONS.get()).isEqualTo(1);
    }

    @Test
    void reusingAKeyWithADifferentPayloadIsRejected() {
        String key = newKey();
        post("/api/v1/test-operations", key, BODY);

        MvcTestResult reused = post("/api/v1/test-operations", key, "{\"label\": \"Autre\", \"amount\": 1}");

        assertThat(reused).hasStatus(422);
        assertThat(reused).bodyJson().extractingPath("$.error.code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(TestOperationsController.EXECUTIONS.get()).isEqualTo(1);
    }

    @Test
    void theKeyIsMandatory() {
        MvcTestResult response = mvc.post().uri("/api/v1/test-operations")
                .contentType(MediaType.APPLICATION_JSON).content(BODY).exchange();

        assertThat(response).hasStatus(400);
        assertThat(response).bodyJson().extractingPath("$.error.code").isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(TestOperationsController.EXECUTIONS.get()).isZero();
    }

    @Test
    void aFailedOperationReleasesTheKeySoTheClientCanRetry() {
        String key = newKey();

        MvcTestResult first = post("/api/v1/test-operations/failing", key, BODY);
        MvcTestResult retry = post("/api/v1/test-operations/failing", key, BODY);

        assertThat(first).hasStatus(409);
        assertThat(retry).hasStatus(409);
        assertThat(TestOperationsController.EXECUTIONS.get()).isEqualTo(2);
        assertThat(storedKeys(key)).isZero();
    }

    @Test
    void concurrentDuplicatesExecuteTheOperationOnlyOnce() throws Exception {
        String key = newKey();
        int clients = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < clients; i++) {
            calls.add(() -> {
                start.await();
                return post("/api/v1/test-operations", key, BODY).getResponse().getStatus();
            });
        }

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(clients)) {
            List<Future<Integer>> futures = calls.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
        }

        assertThat(TestOperationsController.EXECUTIONS.get()).isEqualTo(1);
        assertThat(statuses).allMatch(status -> status == 201 || status == 409);
        assertThat(statuses).contains(201);
    }

    private MvcTestResult post(String uri, String key, String body) {
        return mvc.post().uri(uri)
                .header(Idempotent.HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private long storedKeys(String key) {
        return jdbc.sql("SELECT count(*) FROM idempotency_key WHERE idem_key = :key")
                .param("key", key)
                .query(Long.class)
                .single();
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}
