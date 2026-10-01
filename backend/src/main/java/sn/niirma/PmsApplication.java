package sn.niirma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrée du PMS. Chaque sous-package direct de {@code sn.niirma} est un module
 * applicatif Spring Modulith (voir docs/specs/cahier_des_charges_v2.md §40.2).
 */
@SpringBootApplication
@Modulithic(sharedModules = "shared")
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@EnableScheduling
public class PmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PmsApplication.class, args);
    }
}
