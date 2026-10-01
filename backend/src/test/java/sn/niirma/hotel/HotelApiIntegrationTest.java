package sn.niirma.hotel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import sn.niirma.support.IntegrationTest;
import sn.niirma.support.TestAuth;

@IntegrationTest
class HotelApiIntegrationTest {

    private static final String DEMO_HOTEL_ID = "0190f2a0-0000-7000-8000-000000000101";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestAuth auth;

    @Test
    void superAdminListsAllHotelsOfTheOrganization() {
        var response = mvc.get().uri("/api/v1/hotels").header(HttpHeaders.AUTHORIZATION, auth.admin().bearer()).exchange();

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.success").isEqualTo(true);
        assertThat(response).bodyJson().extractingPath("$.data[0].code").isEqualTo("DKR01");
        assertThat(response).bodyJson().extractingPath("$.data[0].currencyCode").isEqualTo("XOF");
        assertThat(response).bodyJson().extractingPath("$.meta.page").isEqualTo(1);
        assertThat(response).bodyJson().extractingPath("$.meta.total").isEqualTo(2);
    }

    @Test
    void returnsOneHotelWithItsBusinessDate() {
        var response = mvc.get().uri("/api/v1/hotels/{id}", DEMO_HOTEL_ID)
                .header(HttpHeaders.AUTHORIZATION, auth.admin().bearer()).exchange();

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.data.timezone").isEqualTo("Africa/Dakar");
        assertThat(response).bodyJson().extractingPath("$.data.businessDate").asString().matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void unknownHotelReturnsHotelNotFound() {
        var response = mvc.get().uri("/api/v1/hotels/{id}", "0190f2a0-0000-7000-8000-00000000ffff")
                .header(HttpHeaders.AUTHORIZATION, auth.admin().bearer()).exchange();

        assertThat(response).hasStatus(404);
        assertThat(response).bodyJson().extractingPath("$.error.code").isEqualTo("HOTEL_NOT_FOUND");
    }
}
