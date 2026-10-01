package sn.niirma.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sn.niirma.support.CapturingMailSender;
import sn.niirma.support.IntegrationTest;
import sn.niirma.support.MutableClock;
import sn.niirma.support.TestAuth;
import sn.niirma.support.TestAuth.CreatedUser;
import sn.niirma.support.TestAuth.Session;

/**
 * US-AUTH-01 à 06 : connexion, blocage, rafraîchissement, déconnexion, mot de passe.
 */
@IntegrationTest
class AuthenticationIntegrationTest {

    private static final String STRONG_PASSWORD = "Teranga#Saloum2026";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestAuth auth;

    @Autowired
    MutableClock clock;

    @Autowired
    CapturingMailSender mails;

    @AfterEach
    void resetClock() {
        clock.reset();
        mails.clear();
    }

    // --- US-AUTH-01 Connexion ----------------------------------------------------------------------------

    @Test
    void loginReturnsAnAccessTokenAndASecureRefreshCookie() {
        MvcTestResult result = auth.attemptLogin("ADMIN", TestAuth.ADMIN_PASSWORD);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.data.tokenType").isEqualTo("Bearer");
        assertThat(result).bodyJson().extractingPath("$.data.expiresIn").isEqualTo(900);
        assertThat(result).bodyJson().extractingPath("$.data.user.username").isEqualTo("admin");
        String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).startsWith(TestAuth.REFRESH_COOKIE + "=")
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");

        Session session = auth.toSession(result);
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .hasStatusOk().bodyJson().extractingPath("$.data.superAdmin").isEqualTo(true);
    }

    @Test
    void unknownUserAndWrongPasswordGetTheSameAnswer() {
        MvcTestResult unknown = auth.attemptLogin("personne", "Nimporte#Quoi2026");
        MvcTestResult wrongPassword = auth.attemptLogin("caisse", "Nimporte#Quoi2026");

        assertThat(unknown).hasStatus(401).bodyJson().extractingPath("$.error.code").isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrongPassword).hasStatus(401).bodyJson().extractingPath("$.error.message")
                .isEqualTo("Nom d'utilisateur ou mot de passe incorrect.");
    }

    @Test
    void fiveFailuresLockTheAccountForFifteenMinutes() {
        Session admin = auth.admin();
        CreatedUser user = auth.createUser(admin, "verrou.test", "RECEPTIONIST", TestAuth.DAKAR_HOTEL_ID, null);

        for (int i = 0; i < 4; i++) {
            assertThat(auth.attemptLogin(user.username(), "Mauvais#Passe2026")).hasStatus(401);
        }
        assertThat(auth.attemptLogin(user.username(), "Mauvais#Passe2026")).hasStatus(423)
                .bodyJson().extractingPath("$.error.code").isEqualTo("ACCOUNT_LOCKED");
        assertThat(auth.attemptLogin(user.username(), user.temporaryPassword())).hasStatus(423);

        clock.advance(Duration.ofMinutes(16));
        assertThat(auth.attemptLogin(user.username(), user.temporaryPassword())).hasStatusOk();
    }

    @Test
    void protectedEndpointsRejectMissingOrInvalidTokens() {
        assertThat(mvc.get().uri("/api/v1/me")).hasStatus(401);
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer pas.un.jeton"))
                .hasStatus(401).bodyJson().extractingPath("$.error.code").isEqualTo("UNAUTHENTICATED");
    }

    // --- US-AUTH-02 Rafraîchissement -----------------------------------------------------------------------

    @Test
    void refreshRotatesTheRefreshToken() {
        Session session = auth.admin();

        MvcTestResult refreshed = mvc.post().uri("/api/v1/auth/refresh").cookie(session.refreshCookie()).exchange();

        assertThat(refreshed).hasStatusOk();
        Session next = auth.toSession(refreshed);
        assertThat(next.refreshToken()).isNotBlank().isNotEqualTo(session.refreshToken());
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, next.bearer())).hasStatusOk();
    }

    @Test
    void reusingAnOldRefreshTokenRevokesTheWholeSession() {
        Session session = auth.admin();
        Session next = auth.toSession(mvc.post().uri("/api/v1/auth/refresh").cookie(session.refreshCookie()).exchange());

        // Deux onglets qui rafraîchissent en même temps : le second est invité à réessayer.
        assertThat(mvc.post().uri("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .hasStatus(409).bodyJson().extractingPath("$.error.code").isEqualTo("REFRESH_IN_PROGRESS");

        // Plus tard, le même jeton revient : vol présumé, la session est fermée.
        clock.advance(Duration.ofMinutes(1));
        assertThat(mvc.post().uri("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .hasStatus(401).bodyJson().extractingPath("$.error.code").isEqualTo("SESSION_REVOKED");
        assertThat(mvc.post().uri("/api/v1/auth/refresh").cookie(next.refreshCookie())).hasStatus(401);
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, next.bearer())).hasStatus(401);
    }

    @Test
    void thirtyMinutesOfInactivityExpireTheSession() {
        Session session = auth.admin();

        clock.advance(Duration.ofMinutes(31));

        assertThat(mvc.post().uri("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .hasStatus(401).bodyJson().extractingPath("$.error.code").isEqualTo("SESSION_EXPIRED");
    }

    @Test
    void refreshWithoutCookieIsRejected() {
        assertThat(mvc.post().uri("/api/v1/auth/refresh")).hasStatus(401)
                .bodyJson().extractingPath("$.error.code").isEqualTo("SESSION_EXPIRED");
    }

    // --- US-AUTH-03 Déconnexion ----------------------------------------------------------------------------

    @Test
    void logoutClosesTheSession() {
        Session session = auth.admin();

        MvcTestResult logout = mvc.post().uri("/api/v1/auth/logout").cookie(session.refreshCookie()).exchange();

        assertThat(logout).hasStatus(204);
        assertThat(logout.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(mvc.post().uri("/api/v1/auth/refresh").cookie(session.refreshCookie())).hasStatus(401);
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, session.bearer())).hasStatus(401);
    }

    // --- US-AUTH-04 Mot de passe temporaire et changement --------------------------------------------------

    @Test
    void aTemporaryPasswordMustBeReplacedBeforeUsingThePms() {
        Session admin = auth.admin();
        CreatedUser user = auth.createUser(admin, "nouvel.agent", "RECEPTIONIST", TestAuth.DAKAR_HOTEL_ID, null);

        MvcTestResult login = auth.attemptLogin(user.username(), user.temporaryPassword());
        assertThat(login).bodyJson().extractingPath("$.data.passwordChangeRequired").isEqualTo(true);
        Session temporary = auth.toSession(login);

        assertThat(mvc.get().uri("/api/v1/hotels").header(HttpHeaders.AUTHORIZATION, temporary.bearer()))
                .hasStatus(403).bodyJson().extractingPath("$.error.code").isEqualTo("PASSWORD_CHANGE_REQUIRED");
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, temporary.bearer())).hasStatusOk();

        MvcTestResult changed = changePassword(temporary, user.temporaryPassword(), STRONG_PASSWORD);
        assertThat(changed).hasStatusOk().bodyJson().extractingPath("$.data.passwordChangeRequired").isEqualTo(false);
        String newToken = "Bearer " + auth.readBody(changed).path("data").path("accessToken").asString();
        assertThat(mvc.get().uri("/api/v1/hotels").header(HttpHeaders.AUTHORIZATION, newToken)).hasStatusOk();
    }

    @Test
    void changingThePasswordChecksTheRulesAndClosesOtherSessions() {
        Session first = auth.createActiveUser(auth.admin(), "change.mdp", "RECEPTIONIST", TestAuth.DAKAR_HOTEL_ID,
                STRONG_PASSWORD);
        Session second = auth.login("change.mdp", STRONG_PASSWORD);

        assertThat(changePassword(first, "Mauvais#Actuel2026", "Autre#Valide2026")).hasStatus(422)
                .bodyJson().extractingPath("$.error.code").isEqualTo("INVALID_PASSWORD");
        assertThat(changePassword(first, STRONG_PASSWORD, "court")).hasStatus(422)
                .bodyJson().extractingPath("$.error.code").isEqualTo("WEAK_PASSWORD");
        assertThat(changePassword(first, STRONG_PASSWORD, "qwertyuiop")).hasStatus(422)
                .bodyJson().extractingPath("$.error.details[0].issue").isEqualTo("COMMON");

        assertThat(changePassword(first, STRONG_PASSWORD, "Baobab#Casamance2026")).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, first.bearer())).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, second.bearer())).hasStatus(401);
    }

    // --- US-AUTH-05 Mot de passe oublié --------------------------------------------------------------------

    @Test
    void forgottenPasswordSendsASingleUseLinkByEmail() {
        auth.createUser(auth.admin(), "oubli.mdp", "RECEPTIONIST", TestAuth.DAKAR_HOTEL_ID, "oubli@demo.niirma.sn");

        assertThat(forgot("oubli.mdp")).hasStatus(202);
        assertThat(mails.sentTo("oubli@demo.niirma.sn")).hasSize(1);
        String token = tokenFrom(mails.sentTo("oubli@demo.niirma.sn").getFirst());

        assertThat(reset(token, "Mangrove#Sine2026")).hasStatus(204);
        assertThat(auth.attemptLogin("oubli.mdp", "Mangrove#Sine2026")).hasStatusOk()
                .bodyJson().extractingPath("$.data.passwordChangeRequired").isEqualTo(false);
        assertThat(reset(token, "Autre#Mangrove2026")).hasStatus(422)
                .bodyJson().extractingPath("$.error.code").isEqualTo("RESET_TOKEN_INVALID");
    }

    @Test
    void forgottenPasswordAnswersTheSameWayForUnknownAccounts() {
        MvcTestResult unknown = forgot("inconnu@nulle-part.sn");
        MvcTestResult withoutEmail = forgot("caisse");

        assertThat(unknown).hasStatus(202);
        assertThat(withoutEmail).hasStatus(202);
        assertThat(unknown.getResponse().getContentAsByteArray().length).isPositive();
        assertThat(auth.readBody(unknown).path("data")).isEqualTo(auth.readBody(withoutEmail).path("data"));
    }

    @Test
    void resetLinksExpireAfterThirtyMinutes() {
        auth.createUser(auth.admin(), "lien.expire", "RECEPTIONIST", TestAuth.DAKAR_HOTEL_ID, "expire@demo.niirma.sn");
        forgot("expire@demo.niirma.sn");
        String token = tokenFrom(mails.sentTo("expire@demo.niirma.sn").getFirst());

        clock.advance(Duration.ofMinutes(31));

        assertThat(reset(token, "Mangrove#Sine2026")).hasStatus(422);
    }

    // --- US-AUTH-06 Sessions -------------------------------------------------------------------------------

    @Test
    void usersSeeAndCloseTheirOwnSessions() {
        Session current = auth.createActiveUser(auth.admin(), "sessions.test", "RECEPTIONIST", TestAuth.DAKAR_HOTEL_ID,
                STRONG_PASSWORD);
        Session other = auth.login("sessions.test", STRONG_PASSWORD);
        String otherSessionId = currentSessionId(other);

        MvcTestResult list = mvc.get().uri("/api/v1/me/sessions").header(HttpHeaders.AUTHORIZATION, current.bearer()).exchange();
        assertThat(list).hasStatusOk().bodyJson().extractingPath("$.data[*].id").asArray()
                .contains(otherSessionId, currentSessionId(current));

        assertThat(mvc.delete().uri("/api/v1/me/sessions/{id}", otherSessionId)
                .header(HttpHeaders.AUTHORIZATION, current.bearer())).hasStatus(204);
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, other.bearer())).hasStatus(401);
        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, current.bearer())).hasStatusOk();
        assertThat(mvc.delete().uri("/api/v1/me/sessions/{id}", currentSessionId(current))
                .header(HttpHeaders.AUTHORIZATION, auth.admin().bearer())).hasStatus(404);
    }

    private String currentSessionId(Session session) {
        MvcTestResult list = mvc.get().uri("/api/v1/me/sessions").header(HttpHeaders.AUTHORIZATION, session.bearer()).exchange();
        for (var entry : auth.readBody(list).path("data")) {
            if (entry.path("current").asBoolean()) {
                return entry.path("id").asString();
            }
        }
        throw new IllegalStateException("Session courante absente");
    }

    private MvcTestResult changePassword(Session session, String current, String next) {
        return mvc.put().uri("/api/v1/auth/password")
                .header(HttpHeaders.AUTHORIZATION, session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(auth.json(Map.of("currentPassword", current, "newPassword", next)))
                .exchange();
    }

    private MvcTestResult forgot(String login) {
        return mvc.post().uri("/api/v1/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(auth.json(Map.of("login", login)))
                .exchange();
    }

    private MvcTestResult reset(String token, String newPassword) {
        return mvc.post().uri("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(auth.json(Map.of("token", token, "newPassword", newPassword)))
                .exchange();
    }

    private static String tokenFrom(SimpleMailMessage message) {
        Matcher matcher = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(message.getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
