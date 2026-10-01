package sn.niirma.shared.security;

import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

/**
 * Renseigne created_by / updated_by avec l'utilisateur authentifié (vide pour les traitements système).
 */
@Configuration(proxyBeanMethods = false)
class AuditorConfiguration {

    @Bean
    AuditorAware<UUID> currentAuditor() {
        return () -> CurrentUser.current().map(CurrentUser::userId);
    }
}
