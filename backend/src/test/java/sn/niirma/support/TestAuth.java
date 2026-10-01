package sn.niirma.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Connexion et création de comptes dans les tests d'intégration, via l'API réelle.
 * Comptes créés au démarrage (profil test) : {@code admin} (super administrateur) et les comptes de
 * démonstration {@code direction}, {@code reception}, {@code caisse}, {@code menage} sur l'hôtel de Dakar,
 * {@code comptable} sur les deux hôtels.
 */
@Component
public class TestAuth {

    public static final String ADMIN = "admin";
    public static final String ADMIN_PASSWORD = "Admin#Test2026";
    public static final String DEMO_PASSWORD = "Demo#Test2026";
    public static final UUID DAKAR_HOTEL_ID = UUID.fromString("0190f2a0-0000-7000-8000-000000000101");
    public static final UUID SALY_HOTEL_ID = UUID.fromString("0190f2a0-0000-7000-8000-000000000102");
    public static final String REFRESH_COOKIE = "pms_refresh";

    public record Session(String accessToken, String refreshToken, JsonNode body) {

        public String bearer() {
            return "Bearer " + accessToken;
        }

        public Cookie refreshCookie() {
            return new Cookie(REFRESH_COOKIE, refreshToken);
        }
    }

    public record CreatedUser(UUID id, String username, String temporaryPassword) {
    }

    private final MockMvcTester mvc;
    private final ObjectMapper objectMapper;

    TestAuth(MockMvcTester mvc, ObjectMapper objectMapper) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
    }

    public MvcTestResult attemptLogin(String username, String password) {
        return mvc.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", username, "password", password)))
                .exchange();
    }

    public Session login(String username, String password) {
        MvcTestResult result = attemptLogin(username, password);
        assertThat(result).hasStatusOk();
        return toSession(result);
    }

    public Session admin() {
        return login(ADMIN, ADMIN_PASSWORD);
    }

    public Session demo(String username) {
        return login(username, DEMO_PASSWORD);
    }

    public Session toSession(MvcTestResult result) {
        JsonNode body = readBody(result);
        return new Session(body.path("data").path("accessToken").asString(), refreshTokenOf(result), body);
    }

    /** Crée un compte (mot de passe temporaire) avec un rôle par défaut dans un hôtel. */
    public CreatedUser createUser(Session as, String username, String roleCode, UUID hotelId, String email) {
        UUID roleId = roleId(as, roleCode);
        Map<String, Object> request = new HashMap<>();
        request.put("username", username);
        request.put("firstName", "Prénom");
        request.put("lastName", "Nom");
        request.put("email", email);
        request.put("assignments", List.of(Map.of("hotelId", hotelId.toString(), "roleId", roleId.toString())));
        MvcTestResult result = mvc.post().uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, as.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request))
                .exchange();
        assertThat(result).hasStatus(201);
        JsonNode data = readBody(result).path("data");
        return new CreatedUser(UUID.fromString(data.path("user").path("id").asString()), username,
                data.path("temporaryPassword").asString());
    }

    /** Crée un compte puis remplace son mot de passe temporaire ; renvoie une session prête à l'emploi. */
    public Session createActiveUser(Session as, String username, String roleCode, UUID hotelId, String newPassword) {
        CreatedUser user = createUser(as, username, roleCode, hotelId, null);
        Session temporary = login(username, user.temporaryPassword());
        MvcTestResult changed = mvc.put().uri("/api/v1/auth/password")
                .header(HttpHeaders.AUTHORIZATION, temporary.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("currentPassword", user.temporaryPassword(), "newPassword", newPassword)))
                .exchange();
        assertThat(changed).hasStatusOk();
        return login(username, newPassword);
    }

    public UUID roleId(Session as, String roleCode) {
        MvcTestResult roles = mvc.get().uri("/api/v1/roles").header(HttpHeaders.AUTHORIZATION, as.bearer()).exchange();
        assertThat(roles).hasStatusOk();
        for (JsonNode role : readBody(roles).path("data")) {
            if (roleCode.equals(role.path("code").asString())) {
                return UUID.fromString(role.path("id").asString());
            }
        }
        throw new IllegalArgumentException("Rôle inconnu : " + roleCode);
    }

    public JsonNode readBody(MvcTestResult result) {
        try {
            return objectMapper.readTree(result.getResponse().getContentAsString());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private static String refreshTokenOf(MvcTestResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(REFRESH_COOKIE + "="))
                .map(header -> header.substring(REFRESH_COOKIE.length() + 1, header.indexOf(';')))
                .findFirst()
                .orElse(null);
    }
}
