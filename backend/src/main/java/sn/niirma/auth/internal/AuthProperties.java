package sn.niirma.auth.internal;

import java.time.Duration;
import java.util.Base64;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Paramètres d'authentification (§7.2). Le secret JWT vient de l'environnement ({@code PMS_JWT_SECRET},
 * 256 bits minimum encodés en Base64) et n'est jamais commité pour la production.
 */
@ConfigurationProperties("pms.auth")
record AuthProperties(
        String jwtSecret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration sessionTtl,
        @DefaultValue("30m") Duration inactivityTimeout,
        @DefaultValue("30m") Duration resetTokenTtl,
        @DefaultValue("true") boolean cookieSecure,
        @DefaultValue("10") int attemptsPerMinute,
        String frontendUrl,
        @DefaultValue("PMS <no-reply@pms.local>") String mailFrom) {

    static final int MIN_SECRET_BYTES = 32;

    AuthProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("pms.auth.jwt-secret (PMS_JWT_SECRET) est obligatoire");
        }
        if (Base64.getDecoder().decode(jwtSecret).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("pms.auth.jwt-secret doit faire au moins 256 bits (32 octets en Base64)");
        }
    }

    byte[] jwtSecretBytes() {
        return Base64.getDecoder().decode(jwtSecret);
    }
}
