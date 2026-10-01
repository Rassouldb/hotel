package sn.niirma.shared.time;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;

/**
 * Horloge unique de l'application (remplaçable dans les tests). Toujours en UTC : les dates
 * métier locales (date d'exploitation) sont calculées avec le fuseau de l'hôtel.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(OffsetDateTime.now(clock));
    }
}
