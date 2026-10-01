package sn.niirma.auth.internal;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sn.niirma.iam.AuthEventLog;
import sn.niirma.iam.AuthEventType;
import sn.niirma.iam.CredentialService;
import sn.niirma.iam.IamUser;
import sn.niirma.shared.error.BusinessException;
import sn.niirma.shared.persistence.UuidV7;

/**
 * Mot de passe oublié (US-AUTH-05) : lien à usage unique valable 30 minutes, envoyé uniquement si le
 * compte a une adresse email. La réponse est identique que le compte existe ou non.
 */
@Service
class PasswordResetService {

    static final String RESET_PATH = "/reinitialisation-mot-de-passe";

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final JdbcClient jdbc;
    private final CredentialService credentials;
    private final AuthEventLog authEvents;
    private final JavaMailSender mailSender;
    private final AuthProperties properties;
    private final Clock clock;

    PasswordResetService(JdbcClient jdbc, CredentialService credentials, AuthEventLog authEvents,
                         JavaMailSender mailSender, AuthProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.credentials = credentials;
        this.authEvents = authEvents;
        this.mailSender = mailSender;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void request(String login) {
        IamUser user = credentials.findActiveUserWithEmail(login).orElse(null);
        if (user == null) {
            authEvents.record(AuthEventType.PASSWORD_RESET_REQUESTED, null, login, "NO_MATCHING_ACCOUNT");
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbc.sql("UPDATE password_reset_token SET used_at = :now WHERE user_id = :userId AND used_at IS NULL")
                .param("now", now)
                .param("userId", user.id())
                .update();
        String token = SecretTokens.generate();
        jdbc.sql("""
                        INSERT INTO password_reset_token (id, user_id, token_hash, created_at, expires_at)
                        VALUES (:id, :userId, :hash, :now, :expiresAt)
                        """)
                .param("id", UuidV7.generate())
                .param("userId", user.id())
                .param("hash", SecretTokens.hash(token))
                .param("now", now)
                .param("expiresAt", now.plus(properties.resetTokenTtl()))
                .update();
        authEvents.record(AuthEventType.PASSWORD_RESET_REQUESTED, user.id(), user.username(), null);
        send(user, token);
    }

    @Transactional
    public void reset(String token, String newPassword) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID userId = jdbc.sql("""
                        SELECT user_id FROM password_reset_token
                        WHERE token_hash = :hash AND used_at IS NULL AND expires_at > :now
                        FOR UPDATE
                        """)
                .param("hash", SecretTokens.hash(token))
                .param("now", now)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> BusinessException.unprocessable("RESET_TOKEN_INVALID",
                        "Ce lien de réinitialisation est invalide ou a expiré. Faites une nouvelle demande."));
        credentials.resetPassword(userId, newPassword);
        jdbc.sql("UPDATE password_reset_token SET used_at = :now WHERE token_hash = :hash")
                .param("now", now)
                .param("hash", SecretTokens.hash(token))
                .update();
    }

    private void send(IamUser user, String token) {
        String link = properties.frontendUrl() + RESET_PATH + "?token=" + token;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(user.email());
        message.setSubject("Réinitialisation de votre mot de passe");
        message.setText("""
                Bonjour %s,

                Une réinitialisation du mot de passe de votre compte « %s » a été demandée.
                Pour choisir un nouveau mot de passe, ouvrez ce lien (valable %d minutes, utilisable une seule fois) :

                %s

                Si vous n'êtes pas à l'origine de cette demande, ignorez ce message : votre mot de passe reste inchangé.
                """.formatted(user.firstName(), user.username(), properties.resetTokenTtl().toMinutes(), link));
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Échec de l'envoi de l'email de réinitialisation à l'utilisateur {}", user.id(), e);
        }
    }
}
