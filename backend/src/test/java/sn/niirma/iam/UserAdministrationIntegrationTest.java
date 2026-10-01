package sn.niirma.iam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import sn.niirma.support.IntegrationTest;
import sn.niirma.support.TestAuth;
import sn.niirma.support.TestAuth.CreatedUser;
import sn.niirma.support.TestAuth.Session;

/**
 * US-IAM-01 : gestion des comptes et garde-fous.
 */
@IntegrationTest
class UserAdministrationIntegrationTest {

    private static final String PASSWORD = "Teranga#Saloum2026";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestAuth auth;

    @Autowired
    JdbcClient jdbc;

    @Test
    void creatingAUserReturnsATemporaryPasswordOnceAndIsAudited() throws Exception {
        Session admin = auth.admin();

        CreatedUser created = auth.createUser(admin, "Fatou.Diop", "CASHIER", TestAuth.DAKAR_HOTEL_ID, "fatou@demo.niirma.sn");

        assertThat(created.username()).isEqualTo("Fatou.Diop");
        assertThat(created.temporaryPassword()).hasSize(12);
        MvcTestResult fetched = get("/api/v1/users/" + created.id(), admin);
        assertThat(fetched).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.data.username").isEqualTo("fatou.diop");
            assertThat(json).extractingPath("$.data.mustChangePassword").isEqualTo(true);
            assertThat(json).extractingPath("$.data.assignments[0].roleCode").isEqualTo("CASHIER");
        });
        assertThat(fetched.getResponse().getContentAsString()).doesNotContain("temporaryPassword", "passwordHash");

        Map<String, Object> auditRow = jdbc.sql("""
                        SELECT user_name, new_value::text AS new_value FROM audit_log
                        WHERE action = 'USER_CREATE' AND entity_id = :id
                        """)
                .param("id", created.id().toString())
                .query()
                .singleRow();
        assertThat(auditRow.get("user_name")).isEqualTo("admin");
        assertThat((String) auditRow.get("new_value")).contains("fatou.diop").doesNotContain(created.temporaryPassword());
    }

    @Test
    void usernamesAreUnique() {
        Session admin = auth.admin();
        auth.createUser(admin, "doublon", "CASHIER", TestAuth.DAKAR_HOTEL_ID, null);

        MvcTestResult duplicate = createUser(admin, "DOUBLON", "CASHIER", TestAuth.DAKAR_HOTEL_ID);

        assertThat(duplicate).hasStatus(409).bodyJson().extractingPath("$.error.code").isEqualTo("USERNAME_ALREADY_EXISTS");
    }

    @Test
    void aDirectorManagesOnlyUsersOfHisHotelsWithinHisOwnPermissions() {
        Session director = auth.demo("direction");

        assertThat(createUser(director, "recep.dakar", "RECEPTIONIST", TestAuth.DAKAR_HOTEL_ID)).hasStatus(201);
        assertThat(createUser(director, "recep.saly", "RECEPTIONIST", TestAuth.SALY_HOTEL_ID))
                .hasStatus(403).bodyJson().extractingPath("$.error.code").isEqualTo("PERMISSION_DENIED");

        UUID roleManager = createRole(auth.admin(), "Gestion des rôles " + UUID.randomUUID(), List.of("ROLE_MANAGE", "HOTEL_VIEW"));
        MvcTestResult escalation = createUserWithRole(director, "escalade", roleManager, TestAuth.DAKAR_HOTEL_ID);
        assertThat(escalation).hasStatus(403).bodyJson().extractingPath("$.error.code")
                .isEqualTo("ROLE_EXCEEDS_OWN_PERMISSIONS");

        MvcTestResult list = get("/api/v1/users?size=100", director);
        assertThat(list).hasStatusOk();
        for (JsonNode user : auth.readBody(list).path("data")) {
            assertThat(user.path("superAdmin").asBoolean()).isFalse();
        }
    }

    @Test
    void disablingAUserClosesHisSessionsImmediately() {
        Session admin = auth.admin();
        Session agent = auth.createActiveUser(admin, "agent.desactive", "RECEPTIONIST", TestAuth.DAKAR_HOTEL_ID, PASSWORD);
        String userId = auth.readBody(get("/api/v1/me", agent)).path("data").path("id").asString();

        assertThat(post("/api/v1/users/" + userId + "/disable", admin)).hasStatusOk()
                .bodyJson().extractingPath("$.data.status").isEqualTo("DISABLED");

        assertThat(get("/api/v1/me", agent)).hasStatus(401);
        assertThat(auth.attemptLogin("agent.desactive", PASSWORD)).hasStatus(401);

        assertThat(post("/api/v1/users/" + userId + "/enable", admin)).hasStatusOk();
        assertThat(auth.attemptLogin("agent.desactive", PASSWORD)).hasStatusOk();
    }

    @Test
    void anAdministratorCanResetAForgottenPassword() {
        Session admin = auth.admin();
        Session agent = auth.createActiveUser(admin, "agent.reinit", "RECEPTIONIST", TestAuth.DAKAR_HOTEL_ID, PASSWORD);
        String userId = auth.readBody(get("/api/v1/me", agent)).path("data").path("id").asString();

        MvcTestResult reset = post("/api/v1/users/" + userId + "/reset-password", admin);

        assertThat(reset).hasStatusOk();
        String temporary = auth.readBody(reset).path("data").path("temporaryPassword").asString();
        assertThat(get("/api/v1/me", agent)).hasStatus(401);
        assertThat(auth.attemptLogin("agent.reinit", temporary)).hasStatusOk()
                .bodyJson().extractingPath("$.data.passwordChangeRequired").isEqualTo(true);
    }

    @Test
    void theLastSuperAdministratorCannotBeRemoved() {
        Session admin = auth.admin();
        JsonNode me = auth.readBody(get("/api/v1/me", admin)).path("data");
        String adminId = me.path("id").asString();

        assertThat(post("/api/v1/users/" + adminId + "/disable", admin))
                .hasStatus(409).bodyJson().extractingPath("$.error.code").isEqualTo("CANNOT_DISABLE_SELF");

        JsonNode adminUser = auth.readBody(get("/api/v1/users/" + adminId, admin)).path("data");
        Map<String, Object> demotion = new HashMap<>();
        demotion.put("firstName", adminUser.path("firstName").asString());
        demotion.put("lastName", adminUser.path("lastName").asString());
        demotion.put("language", "fr");
        demotion.put("superAdmin", false);
        demotion.put("assignments", List.of(Map.of("hotelId", TestAuth.DAKAR_HOTEL_ID.toString(),
                "roleId", auth.roleId(admin, "DIRECTOR").toString())));
        demotion.put("version", adminUser.path("version").asLong());
        MvcTestResult result = mvc.put().uri("/api/v1/users/" + adminId)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(auth.json(demotion))
                .exchange();

        assertThat(result).hasStatus(409).bodyJson().extractingPath("$.error.code").isEqualTo("LAST_SUPER_ADMIN");
    }

    private MvcTestResult createUser(Session as, String username, String roleCode, UUID hotelId) {
        return createUserWithRole(as, username, auth.roleId(as, roleCode), hotelId);
    }

    private MvcTestResult createUserWithRole(Session as, String username, UUID roleId, UUID hotelId) {
        return mvc.post().uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, as.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(auth.json(Map.of("username", username, "firstName", "Prénom", "lastName", "Nom",
                        "assignments", List.of(Map.of("hotelId", hotelId.toString(), "roleId", roleId.toString())))))
                .exchange();
    }

    private UUID createRole(Session as, String name, List<String> permissions) {
        MvcTestResult result = mvc.post().uri("/api/v1/roles")
                .header(HttpHeaders.AUTHORIZATION, as.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(auth.json(Map.of("name", name, "permissions", permissions)))
                .exchange();
        assertThat(result).hasStatus(201);
        return UUID.fromString(auth.readBody(result).path("data").path("id").asString());
    }

    private MvcTestResult get(String uri, Session as) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, as.bearer()).exchange();
    }

    private MvcTestResult post(String uri, Session as) {
        return mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, as.bearer()).exchange();
    }
}
