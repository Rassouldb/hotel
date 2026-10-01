package sn.niirma.idempotency.internal;

import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import sn.niirma.idempotency.Idempotent;
import sn.niirma.idempotency.internal.IdempotencyStore.StoredKey;
import sn.niirma.shared.error.BusinessException;

/**
 * Applique {@link Idempotent} : réserve la clé, exécute l'opération une seule fois, mémorise la
 * réponse et la rejoue pour les renvois identiques.
 */
@Aspect
@Component
class IdempotencyAspect {

    /** Au-delà, une clé restée « en cours » est considérée comme abandonnée (processus interrompu). */
    static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9-]{8,100}");

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    IdempotencyAspect(IdempotencyStore store, ObjectMapper objectMapper, Clock clock) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Around("@annotation(sn.niirma.idempotency.Idempotent)")
    Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String key = request.getHeader(Idempotent.HEADER);
        if (key == null || key.isBlank()) {
            throw BusinessException.badRequest("IDEMPOTENCY_KEY_REQUIRED",
                    "L'en-tête " + Idempotent.HEADER + " est obligatoire pour cette opération.");
        }
        if (!VALID_KEY.matcher(key).matches()) {
            throw BusinessException.badRequest("IDEMPOTENCY_KEY_INVALID",
                    "L'en-tête " + Idempotent.HEADER + " doit contenir de 8 à 100 caractères alphanumériques ou tirets.");
        }

        String scope = request.getMethod() + " " + request.getRequestURI();
        String requestHash = hash(scope, requestBody(joinPoint));

        if (!store.tryAcquire(scope, key, requestHash)) {
            StoredKey existing = store.find(scope, key).orElse(null);
            if (existing == null || isStale(existing)) {
                store.release(scope, key);
                if (!store.tryAcquire(scope, key, requestHash)) {
                    throw inProgress();
                }
            } else {
                return replayOrReject(existing, requestHash);
            }
        }

        try {
            Object result = joinPoint.proceed();
            if (!(result instanceof ResponseEntity<?> response)) {
                throw new IllegalStateException("Une méthode @Idempotent doit renvoyer un ResponseEntity : "
                        + joinPoint.getSignature().toShortString());
            }
            store.complete(scope, key, response.getStatusCode().value(), objectMapper.writeValueAsString(response.getBody()));
            return response;
        } catch (Throwable ex) {
            store.release(scope, key);
            throw ex;
        }
    }

    private Object replayOrReject(StoredKey existing, String requestHash) {
        if (!existing.requestHash().equals(requestHash)) {
            throw BusinessException.unprocessable("IDEMPOTENCY_KEY_REUSED",
                    "Cette clé d'idempotence a déjà été utilisée pour une requête différente.");
        }
        if (existing.status() == IdempotencyStore.Status.IN_PROGRESS) {
            throw inProgress();
        }
        return ResponseEntity.status(existing.responseStatus())
                .header(Idempotent.REPLAYED_HEADER, "true")
                .body(existing.responseBody() == null ? null : objectMapper.readTree(existing.responseBody()));
    }

    private boolean isStale(StoredKey existing) {
        return existing.status() == IdempotencyStore.Status.IN_PROGRESS
                && existing.createdAt().isBefore(OffsetDateTime.now(clock).minus(STALE_AFTER));
    }

    private static BusinessException inProgress() {
        return BusinessException.conflict("IDEMPOTENCY_REQUEST_IN_PROGRESS",
                "Une requête identique est déjà en cours de traitement.");
    }

    private Object requestBody(ProceedingJoinPoint joinPoint) {
        Annotation[][] annotations = ((MethodSignature) joinPoint.getSignature()).getMethod().getParameterAnnotations();
        for (int i = 0; i < annotations.length; i++) {
            for (Annotation annotation : annotations[i]) {
                if (annotation instanceof RequestBody) {
                    return joinPoint.getArgs()[i];
                }
            }
        }
        return null;
    }

    private String hash(String scope, Object body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(scope.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(objectMapper.writeValueAsBytes(body));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
