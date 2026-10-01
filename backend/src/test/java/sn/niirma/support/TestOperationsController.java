package sn.niirma.support;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sn.niirma.idempotency.Idempotent;
import sn.niirma.shared.api.ApiResponse;
import sn.niirma.shared.error.BusinessException;
import sn.niirma.shared.security.AuthenticatedOnly;

/**
 * Contrôleur présent uniquement dans les tests, pour éprouver les mécanismes transverses
 * (validation, idempotence, erreurs) indépendamment des modules métier.
 */
@RestController
@RequestMapping("/api/v1/test-operations")
public class TestOperationsController {

    public record OperationRequest(@NotBlank String label, @Positive long amount) {
    }

    public static final AtomicInteger EXECUTIONS = new AtomicInteger();

    @PostMapping
    @AuthenticatedOnly
    @Idempotent
    ResponseEntity<ApiResponse<Map<String, Object>>> create(@Valid @RequestBody OperationRequest request) {
        int execution = EXECUTIONS.incrementAndGet();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(Map.of("label", request.label(), "execution", execution)));
    }

    @PostMapping("/failing")
    @AuthenticatedOnly
    @Idempotent
    ResponseEntity<ApiResponse<Void>> failing(@RequestBody OperationRequest request) {
        EXECUTIONS.incrementAndGet();
        throw BusinessException.conflict("TEST_CONFLICT", "Conflit simulé.");
    }

    @PostMapping("/crash")
    @AuthenticatedOnly
    ResponseEntity<Void> crash() {
        throw new IllegalStateException("détail technique qui ne doit pas fuiter");
    }
}
