package sn.niirma.iam;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.niirma.audit.AuditEntry;
import sn.niirma.audit.AuditService;
import sn.niirma.iam.internal.PasswordPolicy;
import sn.niirma.iam.internal.User;
import sn.niirma.iam.internal.UserRepository;
import sn.niirma.shared.error.BusinessException;

/**
 * Vérification des identifiants et gestion du mot de passe de l'utilisateur, utilisées par le module
 * auth. Les échecs sont comptés pour bloquer le compte après 5 tentatives (§7.2).
 */
@Service
public class CredentialService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Nom d'utilisateur ou mot de passe incorrect.";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthEventLog authEvents;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    /** Haché de référence : vérifié quand le compte n'existe pas, pour un temps de réponse identique. */
    private final String dummyHash;

    CredentialService(UserRepository users, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                      AuthEventLog authEvents, AuditService audit, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.authEvents = authEvents;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("mot-de-passe-de-reference-" + UUID.randomUUID());
    }

    /**
     * Vérifie le couple nom d'utilisateur / mot de passe. Le compteur d'échecs est enregistré même
     * quand la connexion est refusée.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public IamUser authenticate(String username, String password) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<User> found = users.findByUsername(normalized);

        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            authEvents.record(AuthEventType.LOGIN_FAILURE, null, normalized, "UNKNOWN_USER");
            throw invalidCredentials();
        }

        User user = found.get();
        if (user.isLocked(now)) {
            authEvents.record(AuthEventType.LOGIN_LOCKED, user.getId(), normalized, null);
            throw locked(user);
        }

        boolean passwordMatches = passwordEncoder.matches(password, user.getPasswordHash());
        if (!user.isActive()) {
            authEvents.record(AuthEventType.LOGIN_FAILURE, user.getId(), normalized, "DISABLED");
            throw invalidCredentials();
        }
        if (!passwordMatches) {
            boolean justLocked = user.registerFailedLogin(now);
            authEvents.record(AuthEventType.LOGIN_FAILURE, user.getId(), normalized, "BAD_PASSWORD");
            if (justLocked) {
                authEvents.record(AuthEventType.ACCOUNT_LOCKED, user.getId(), normalized, null);
                throw locked(user);
            }
            throw invalidCredentials();
        }

        user.registerSuccessfulLogin(now);
        authEvents.record(AuthEventType.LOGIN_SUCCESS, user.getId(), normalized, null);
        return toView(user);
    }

    /** Compte actif, pour prolonger une session ; vide s'il a été supprimé ou désactivé. */
    @Transactional(readOnly = true)
    public Optional<IamUser> findActiveUser(UUID userId) {
        return users.findById(userId).filter(User::isActive).map(CredentialService::toView);
    }

    /** Compte actif ayant une adresse email, recherché par nom d'utilisateur ou par email. */
    @Transactional(readOnly = true)
    public Optional<IamUser> findActiveUserWithEmail(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        String trimmed = login.trim();
        Optional<User> user = trimmed.contains("@")
                ? users.findByEmailIgnoreCase(trimmed)
                : users.findByUsername(trimmed.toLowerCase(Locale.ROOT));
        return user.filter(User::isActive).filter(u -> u.getEmail() != null).map(CredentialService::toView);
    }

    /**
     * Changement de mot de passe par l'utilisateur lui-même. Ses autres sessions sont fermées.
     */
    @Transactional
    public IamUser changePassword(UUID userId, UUID currentSessionId, String currentPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow(() -> BusinessException.notFound("USER_NOT_FOUND",
                "Utilisateur introuvable."));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_PASSWORD",
                    "Le mot de passe actuel est incorrect.",
                    List.of(Map.of("field", "currentPassword", "issue", "INVALID")));
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "PASSWORD_REUSED",
                    "Le nouveau mot de passe doit être différent de l'actuel.",
                    List.of(Map.of("field", "newPassword", "issue", "REUSED")));
        }
        passwordPolicy.validate(newPassword, user.getUsername());
        user.changePassword(passwordEncoder.encode(newPassword), OffsetDateTime.now(clock));
        audit.record(AuditEntry.of("PASSWORD_CHANGE", "User", user.getId()));
        authEvents.record(AuthEventType.PASSWORD_CHANGED, user.getId(), user.getUsername(), null);
        events.publishEvent(new UserSessionsRevocationRequested(user.getId(), currentSessionId, "PASSWORD_CHANGED"));
        return toView(user);
    }

    /**
     * Nouveau mot de passe choisi depuis un lien de réinitialisation : toutes les sessions sont fermées.
     */
    @Transactional
    public void resetPassword(UUID userId, String newPassword) {
        User user = users.findById(userId).filter(User::isActive)
                .orElseThrow(() -> BusinessException.unprocessable("RESET_TOKEN_INVALID",
                        "Ce lien de réinitialisation n'est plus valide."));
        passwordPolicy.validate(newPassword, user.getUsername());
        user.changePassword(passwordEncoder.encode(newPassword), OffsetDateTime.now(clock));
        user.enable();
        audit.record(AuditEntry.of("PASSWORD_RESET", "User", user.getId()).inHotel(user.getOrganizationId(), null));
        authEvents.record(AuthEventType.PASSWORD_RESET, user.getId(), user.getUsername(), null);
        events.publishEvent(new UserSessionsRevocationRequested(user.getId(), null, "PASSWORD_RESET"));
    }

    private static BusinessException invalidCredentials() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID_CREDENTIALS_MESSAGE);
    }

    private static BusinessException locked(User user) {
        return new BusinessException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                "Compte bloqué après plusieurs échecs de connexion. Réessayez plus tard.",
                List.of(Map.of("lockedUntil", user.getLockedUntil().toString())));
    }

    static IamUser toView(User user) {
        return new IamUser(user.getId(), user.getOrganizationId(), user.getUsername(), user.getFirstName(),
                user.getLastName(), user.getEmail(), user.getLanguage(), user.isSuperAdmin(),
                user.isMustChangePassword(), user.isActive());
    }
}
