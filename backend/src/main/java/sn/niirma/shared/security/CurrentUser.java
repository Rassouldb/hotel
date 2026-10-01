package sn.niirma.shared.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilisateur authentifié de la requête courante, construit à partir du jeton d'accès.
 *
 * @param passwordChangeRequired vrai tant qu'un mot de passe temporaire n'a pas été remplacé
 */
public record CurrentUser(UUID userId, UUID sessionId, UUID organizationId, String username, boolean superAdmin,
                          boolean passwordChangeRequired) {

    public static Optional<CurrentUser> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof CurrentUser user
                ? Optional.of(user)
                : Optional.empty();
    }

    public static CurrentUser require() {
        return current().orElseThrow(() -> new IllegalStateException("Aucun utilisateur authentifié"));
    }
}
