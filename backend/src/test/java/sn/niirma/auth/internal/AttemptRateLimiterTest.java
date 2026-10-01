package sn.niirma.auth.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import sn.niirma.shared.error.BusinessException;
import sn.niirma.support.MutableClock;

class AttemptRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final AttemptRateLimiter limiter = new AttemptRateLimiter(properties(10), clock);

    @Test
    void allowsTenAttemptsPerMinuteAndPerAddress() {
        for (int i = 0; i < 10; i++) {
            limiter.check("login", "10.0.0.1");
        }

        assertThatThrownBy(() -> limiter.check("login", "10.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo("TOO_MANY_REQUESTS"));
        assertThatCode(() -> limiter.check("login", "10.0.0.2")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("forgot", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void theCounterResetsAfterOneMinute() {
        for (int i = 0; i < 11; i++) {
            try {
                limiter.check("login", "10.0.0.3");
            } catch (BusinessException ignored) {
                // la onzième tentative est refusée
            }
        }

        clock.advance(Duration.ofSeconds(61));

        assertThatCode(() -> limiter.check("login", "10.0.0.3")).doesNotThrowAnyException();
    }

    private static AuthProperties properties(int attemptsPerMinute) {
        return new AuthProperties("dGVzdC1vbmx5LWp3dC1zZWNyZXQtcG1zLW5paXJtYS0yMDI2LTMyLWJ5dGVz", Duration.ofMinutes(15),
                Duration.ofDays(7), Duration.ofMinutes(30), Duration.ofMinutes(30), true, attemptsPerMinute,
                "http://localhost", "PMS <test@pms.local>");
    }
}
