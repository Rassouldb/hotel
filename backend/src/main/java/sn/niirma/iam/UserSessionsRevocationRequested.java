package sn.niirma.iam;

import java.util.UUID;

/**
 * Publié quand les sessions d'un utilisateur doivent être fermées (désactivation, changement ou
 * réinitialisation du mot de passe). Le module auth révoque les sessions concernées.
 *
 * @param keptSessionId session à conserver (celle qui a changé le mot de passe), ou {@code null}
 */
public record UserSessionsRevocationRequested(UUID userId, UUID keptSessionId, String reason) {
}
