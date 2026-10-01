package sn.niirma.auth.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import sn.niirma.iam.UserSessionsRevocationRequested;

/**
 * Ferme les sessions d'un utilisateur à la demande du module iam, dans la même transaction que
 * l'opération qui l'a déclenchée (désactivation, changement de mot de passe).
 */
@Component
class SessionRevocationListener {

    private final SessionService sessions;

    SessionRevocationListener(SessionService sessions) {
        this.sessions = sessions;
    }

    @EventListener
    void on(UserSessionsRevocationRequested event) {
        sessions.revokeAllForUser(event.userId(), event.keptSessionId(), event.reason());
    }
}
