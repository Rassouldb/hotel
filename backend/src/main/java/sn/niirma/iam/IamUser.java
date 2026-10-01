package sn.niirma.iam;

import java.util.UUID;

/**
 * Vue d'un compte utilisateur exposée aux autres modules (sans données sensibles).
 */
public record IamUser(UUID id, UUID organizationId, String username, String firstName, String lastName, String email,
                      String language, boolean superAdmin, boolean mustChangePassword, boolean active) {
}
