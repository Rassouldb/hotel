package sn.niirma.shared.persistence;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Génère des UUID version 7 (RFC 9562) : préfixe horodaté en millisecondes, donc triables
 * chronologiquement et efficaces comme clés primaires indexées.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        return generate(System.currentTimeMillis());
    }

    static UUID generate(long epochMillis) {
        long randA = RANDOM.nextInt(1 << 12);
        long msb = (epochMillis << 16) | (0x7L << 12) | randA;
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
