package sn.niirma.auth.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sn.niirma.auth.internal.SessionService.SessionView;
import sn.niirma.shared.api.ApiResponse;
import sn.niirma.shared.security.AuthenticatedOnly;
import sn.niirma.shared.security.CurrentUser;

/**
 * Sessions actives de l'utilisateur connecté (US-AUTH-06).
 */
@RestController
@RequestMapping("/api/v1/me/sessions")
class SessionController {

    private final SessionService sessions;

    SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    @AuthenticatedOnly
    ApiResponse<List<SessionView>> list() {
        CurrentUser user = CurrentUser.require();
        return ApiResponse.of(sessions.listActive(user.userId(), user.sessionId()));
    }

    @DeleteMapping("/{sessionId}")
    @AuthenticatedOnly
    ResponseEntity<Void> revoke(@PathVariable UUID sessionId) {
        sessions.revokeOwn(CurrentUser.require().userId(), sessionId);
        return ResponseEntity.noContent().build();
    }
}
