package sn.niirma.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Remplace l'horloge et l'envoi d'emails par des versions pilotables dans les tests.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructureConfiguration {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    CapturingMailSender capturingMailSender() {
        return new CapturingMailSender();
    }
}
