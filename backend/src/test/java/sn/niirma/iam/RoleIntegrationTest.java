package sn.niirma.iam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sn.niirma.support.IntegrationTest;
import sn.niirma.support.TestAuth;
import sn.niirma.support.TestAuth.Session;

/**
 * US-IAM-02 : rôles personnalisés et rôles par défaut.
 */
@IntegrationTest
class RoleIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestAuth auth;

    @Test
    void defaultRolesAndPermissionCatalogueAreAvailable() {
        Session admin = auth.admin();

        assertThat(mvc.get().uri("/api/v1/roles").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .hasStatusOk().bodyJson().extractingPath("$.data[*].code").asArray()
                .contains("DIRECTOR", "RECEPTIONIST", "CASHIER", "ACCOUNTANT", "HOUSEKEEPING_MANAGER",
                        "HOUSEKEEPING_AGENT", "MAINTENANCE_MANAGER", "RESTAURANT_MANAGER", "NIGHT_AUDITOR");
        assertThat(mvc.get().uri("/api/v1/permissions").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .hasStatusOk().bodyJson().extractingPath("$.data.length()").isEqualTo(Permission.values().length);
    }

    @Test
    void permissionChangesApplyToTheNextRequest() {
        Session admin = auth.admin();
        UUID roleId = createRole(admin, "Contrôleur", List.of("HOTEL_VIEW"));
        Session controller = createUserWithRole(admin, "controleur", roleId);

        assertThat(mvc.get().uri("/api/v1/audit-logs").header(HttpHeaders.AUTHORIZATION, controller.bearer()))
                .hasStatus(403);

        MvcTestResult updated = mvc.put().uri("/api/v1/roles/{id}", roleId)
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(auth.json(Map.of("name", "Contrôleur", "permissions", List.of("HOTEL_VIEW", "AUDIT_VIEW"))))
                .exchange();
        assertThat(updated).hasStatusOk().bodyJson().extractingPath("$.data.code").isEqualTo("CONTROLEUR");

        assertThat(mvc.get().uri("/api/v1/audit-logs").header(HttpHeaders.AUTHORIZATION, controller.bearer()))
                .hasStatusOk();
    }

    @Test
    void protectedRolesCannotBeDeleted() {
        Session admin = auth.admin();
        UUID systemRole = auth.roleId(admin, "CASHIER");
        UUID usedRole = createRole(admin, "Rôle utilisé", List.of("HOTEL_VIEW"));
        createUserWithRole(admin, "porteur.role", usedRole);
        UUID unusedRole = createRole(admin, "Rôle temporaire", List.of("HOTEL_VIEW"));

        assertThat(delete(admin, systemRole)).hasStatus(409).bodyJson().extractingPath("$.error.code").isEqualTo("SYSTEM_ROLE");
        assertThat(delete(admin, usedRole)).hasStatus(409).bodyJson().extractingPath("$.error.code").isEqualTo("ROLE_IN_USE");
        assertThat(delete(admin, unusedRole)).hasStatus(204);
    }

    @Test
    void unknownPermissionsAreRejected() {
        MvcTestResult result = mvc.post().uri("/api/v1/roles")
                .header(HttpHeaders.AUTHORIZATION, auth.admin().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(auth.json(Map.of("name", "Rôle invalide", "permissions", List.of("FAIRE_TOUT"))))
                .exchange();

        assertThat(result).hasStatus(422).bodyJson().extractingPath("$.error.code").isEqualTo("UNKNOWN_PERMISSION");
    }

    @Test
    void onlyRoleManagersCanChangeRoles() {
        Session director = auth.demo("direction");

        MvcTestResult result = mvc.post().uri("/api/v1/roles")
                .header(HttpHeaders.AUTHORIZATION, director.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(auth.json(Map.of("name", "Rôle du directeur", "permissions", List.of("HOTEL_VIEW"))))
                .exchange();

        assertThat(result).hasStatus(403);
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

    private Session createUserWithRole(Session admin, String username, UUID roleId) {
        MvcTestResult created = mvc.post().uri("/api/v1/users")
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(auth.json(Map.of("username", username, "firstName", "Prénom", "lastName", "Nom",
                        "assignments", List.of(Map.of("hotelId", TestAuth.DAKAR_HOTEL_ID.toString(),
                                "roleId", roleId.toString())))))
                .exchange();
        assertThat(created).hasStatus(201);
        String temporary = auth.readBody(created).path("data").path("temporaryPassword").asString();
        Session session = auth.login(username, temporary);
        assertThat(mvc.put().uri("/api/v1/auth/password")
                .header(HttpHeaders.AUTHORIZATION, session.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(auth.json(Map.of("currentPassword", temporary, "newPassword", "Teranga#Saloum2026"))))
                .hasStatusOk();
        return auth.login(username, "Teranga#Saloum2026");
    }

    private MvcTestResult delete(Session as, UUID roleId) {
        return mvc.delete().uri("/api/v1/roles/{id}", roleId).header(HttpHeaders.AUTHORIZATION, as.bearer()).exchange();
    }
}
