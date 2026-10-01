package sn.niirma.auth.internal;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import sn.niirma.shared.error.BusinessException;

/**
 * Limite le nombre de tentatives par adresse IP et par minute sur les points d'entrée sensibles
 * (connexion, mot de passe oublié). En mémoire : suffisant pour une instance ; à porter sur Redis
 * si l'API est déployée sur plusieurs instances.
 */
@Component
class AttemptRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_KEYS = 10_000;

    private record Window(long startMillis, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AuthProperties properties;
    private final Clock clock;

    AttemptRateLimiter(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    void check(String action, String clientIp) {
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startMillis() >= WINDOW_MILLIS);
        }
        Window window = windows.compute(action + "|" + clientIp, (key, current) ->
                current == null || now - current.startMillis() >= WINDOW_MILLIS
                        ? new Window(now, 1)
                        : new Window(current.startMillis(), current.count() + 1));
        if (window.count() > properties.attemptsPerMinute()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                    "Trop de tentatives. Patientez une minute avant de réessayer.");
        }
    }
}
