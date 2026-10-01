package sn.niirma.iam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import sn.niirma.support.IntegrationTest;
import sn.niirma.support.TestAuth;
import sn.niirma.support.TestAuth.Session;

/**
 * US-IAM-03 et US-IAM-04 : chaque utilisateur ne voit que ses hôtels et n'agit que selon ses permissions.
 */
@IntegrationTest
class AuthorizationIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestAuth auth;

    @Test
    void aUserOnlySeesTheHotelsHeIsAssignedTo() {
        Session reception = auth.demo("reception");
        Session accountant = auth.demo("comptable");

        assertThat(mvc.get().uri("/api/v1/hotels").header(HttpHeaders.AUTHORIZATION, reception.bearer()))
                .hasStatusOk().bodyJson().extractingPath("$.data[*].code").asArray().containsExactly("DKR01");
        assertThat(mvc.get().uri("/api/v1/hotels").header(HttpHeaders.AUTHORIZATION, accountant.bearer()))
                .hasStatusOk().bodyJson().extractingPath("$.data[*].code").asArray().containsExactly("DKR01", "SLY01");
    }

    @Test
    void anUnassignedHotelLooksLikeItDoesNotExist() {
        Session reception = auth.demo("reception");

        assertThat(mvc.get().uri("/api/v1/hotels/{id}", TestAuth.SALY_HOTEL_ID)
                .header(HttpHeaders.AUTHORIZATION, reception.bearer()))
                .hasStatus(404).bodyJson().extractingPath("$.error.code").isEqualTo("HOTEL_NOT_FOUND");
        assertThat(mvc.get().uri("/api/v1/hotels/{id}", TestAuth.DAKAR_HOTEL_ID)
                .header(HttpHeaders.AUTHORIZATION, reception.bearer())).hasStatusOk();
    }

    @Test
    void aMissingPermissionIsRefused() {
        Session housekeeping = auth.demo("menage");

        assertThat(mvc.get().uri("/api/v1/users").header(HttpHeaders.AUTHORIZATION, housekeeping.bearer()))
                .hasStatus(403).bodyJson().extractingPath("$.error.code").isEqualTo("PERMISSION_DENIED");
        assertThat(mvc.get().uri("/api/v1/audit-logs").header(HttpHeaders.AUTHORIZATION, housekeeping.bearer()))
                .hasStatus(403);
    }

    @Test
    void theProfileListsPermissionsPerHotel() {
        Session reception = auth.demo("reception");

        assertThat(mvc.get().uri("/api/v1/me").header(HttpHeaders.AUTHORIZATION, reception.bearer()))
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.data.hotels[0].hotelId").isEqualTo(TestAuth.DAKAR_HOTEL_ID.toString());
                    assertThat(json).extractingPath("$.data.hotels[0].roles[0].code").isEqualTo("RECEPTIONIST");
                    assertThat(json).extractingPath("$.data.hotels[0].permissions").asArray()
                            .contains("CHECKIN_CREATE", "PAYMENT_CREATE")
                            .doesNotContain("PAYMENT_REFUND", "USER_VIEW");
                });
    }
}
