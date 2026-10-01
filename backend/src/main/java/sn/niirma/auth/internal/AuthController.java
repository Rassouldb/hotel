package sn.niirma.auth.internal;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sn.niirma.auth.internal.AccessTokenService.AccessToken;
import sn.niirma.auth.internal.SessionService.IssuedSession;
import sn.niirma.auth.internal.SessionService.Rotation;
import sn.niirma.iam.AuthEventLog;
import sn.niirma.iam.AuthEventType;
import sn.niirma.iam.CredentialService;
import sn.niirma.iam.IamUser;
import sn.niirma.shared.api.ApiResponse;
import sn.niirma.shared.security.AuthenticatedOnly;
import sn.niirma.shared.security.CurrentUser;
import sn.niirma.shared.security.PublicEndpoint;

/**
 * Connexion, rafraîchissement, déconnexion et mots de passe (US-AUTH-01 à 05).
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    record LoginRequest(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {
    }

    record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    record ForgotPasswordRequest(@NotBlank @Size(max = 200) String login) {
    }

    record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {
    }

    record AuthenticatedUser(UUID id, String username, String firstName, String lastName, boolean superAdmin) {
    }

    record AuthResponse(String accessToken, String tokenType, long expiresIn, boolean passwordChangeRequired,
                        AuthenticatedUser user) {
    }

    record Message(String message) {
    }

    private final CredentialService credentials;
    private final SessionService sessions;
    private final AccessTokenService accessTokens;
    private final PasswordResetService passwordResets;
    private final RefreshCookie refreshCookie;
    private final AttemptRateLimiter rateLimiter;
    private final AuthEventLog authEvents;

    AuthController(CredentialService credentials, SessionService sessions, AccessTokenService accessTokens,
                   PasswordResetService passwordResets, RefreshCookie refreshCookie, AttemptRateLimiter rateLimiter,
                   AuthEventLog authEvents) {
        this.credentials = credentials;
        this.sessions = sessions;
        this.accessTokens = accessTokens;
        this.passwordResets = passwordResets;
        this.refreshCookie = refreshCookie;
        this.rateLimiter = rateLimiter;
        this.authEvents = authEvents;
    }

    @PostMapping("/login")
    @PublicEndpoint
    ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                    HttpServletRequest httpRequest) {
        rateLimiter.check("login", httpRequest.getRemoteAddr());
        IamUser user = credentials.authenticate(request.username(), request.password());
        IssuedSession session = sessions.open(user.id());
        return withRefreshCookie(session.refreshToken(), authResponse(user, session.sessionId()));
    }

    @PostMapping("/refresh")
    @PublicEndpoint
    ResponseEntity<ApiResponse<AuthResponse>> refresh(HttpServletRequest httpRequest) {
        String token = refreshCookie.read(httpRequest).orElseThrow(SessionService::sessionExpired);
        Rotation rotation = sessions.rotate(token);
        IamUser user = credentials.findActiveUser(rotation.userId()).orElse(null);
        if (user == null) {
            sessions.revoke(rotation.sessionId(), "USER_DISABLED");
            throw SessionService.sessionRevoked();
        }
        return withRefreshCookie(rotation.refreshToken(), authResponse(user, rotation.sessionId()));
    }

    @PostMapping("/logout")
    @PublicEndpoint
    ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        refreshCookie.read(httpRequest).flatMap(sessions::sessionOf).ifPresent(sessionId -> {
            sessions.revoke(sessionId, "LOGOUT");
            authEvents.record(AuthEventType.LOGOUT, null, null, sessionId.toString());
        });
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie.clear()).build();
    }

    @PutMapping("/password")
    @AuthenticatedOnly(allowedDuringPasswordChange = true)
    ApiResponse<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        CurrentUser current = CurrentUser.require();
        IamUser user = credentials.changePassword(current.userId(), current.sessionId(), request.currentPassword(),
                request.newPassword());
        return ApiResponse.of(authResponse(user, current.sessionId()));
    }

    @PostMapping("/password/forgot")
    @PublicEndpoint
    ResponseEntity<ApiResponse<Message>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                        HttpServletRequest httpRequest) {
        rateLimiter.check("forgot", httpRequest.getRemoteAddr());
        passwordResets.request(request.login());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(new Message(
                "Si un compte avec une adresse email correspond, un lien de réinitialisation vient d'être envoyé.")));
    }

    @PostMapping("/password/reset")
    @PublicEndpoint
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                       HttpServletRequest httpRequest) {
        rateLimiter.check("reset", httpRequest.getRemoteAddr());
        passwordResets.reset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private AuthResponse authResponse(IamUser user, UUID sessionId) {
        AccessToken token = accessTokens.issue(user, sessionId);
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), user.mustChangePassword(),
                new AuthenticatedUser(user.id(), user.username(), user.firstName(), user.lastName(), user.superAdmin()));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(String refreshToken, AuthResponse body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(refreshToken))
                .body(ApiResponse.of(body));
    }
}
