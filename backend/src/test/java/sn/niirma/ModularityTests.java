package sn.niirma;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Vérifie les frontières entre modules (ADR-15) : pas de cycle, pas d'accès aux packages internes
 * d'un autre module.
 */
class ModularityTests {

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules.of(PmsApplication.class).verify();
    }
}
