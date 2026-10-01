package sn.niirma.hotel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import sn.niirma.support.IntegrationTest;
import sn.niirma.support.TestAuth;
import sn.niirma.support.TestAuth.Session;

/**
 * US-HOTEL-01 à 03, US-TAX-01, US-PAY-01, US-REF-01 : paramétrage de l'hôtel.
 */
@IntegrationTest
class HotelConfigurationIntegrationTest {

    private static final String DAKAR = TestAuth.DAKAR_HOTEL_ID.toString();
    /** Plus petit PNG valide (1 × 1 pixel). */
    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestAuth auth;

    // --- US-HOTEL-01 -------------------------------------------------------------------------------------

    @Test
    void aSuperAdministratorCreatesAHotelWithItsDefaultConfiguration() {
        Session admin = auth.admin();

        MvcTestResult created = send(admin, mvc.post().uri("/api/v1/hotels"),
                Map.of("code", "ths01", "name", "Hôtel Démo Thiès", "currencyCode", "XOF", "timezone", "Africa/Dakar"));

        assertThat(created).hasStatus(201).bodyJson().extractingPath("$.data.code").isEqualTo("THS01");
        String hotelId = auth.readBody(created).path("data").path("id").asString();
        assertThat(get(admin, "/api/v1/hotels/" + hotelId + "/payment-methods"))
                .bodyJson().extractingPath("$.data[*].code").asArray().contains("CASH", "ORANGE_MONEY", "WAVE");
        assertThat(get(admin, "/api/v1/hotels/" + hotelId + "/channels"))
                .bodyJson().extractingPath("$.data[*].code").asArray().contains("WALK_IN", "BOOKING");
        assertThat(get(admin, "/api/v1/hotels/" + hotelId + "/reasons?domain=ROOM_BLOCK"))
                .bodyJson().extractingPath("$.data.length()").isEqualTo(5);
        assertThat(get(admin, "/api/v1/hotels/" + hotelId + "/amenities"))
                .bodyJson().extractingPath("$.data[*].code").asArray().contains("AIR_CONDITIONING", "WIFI");
        assertThat(get(admin, "/api/v1/hotels/" + hotelId + "/taxes")).bodyJson().extractingPath("$.data.length()").isEqualTo(0);
        assertThat(get(admin, "/api/v1/hotels/" + hotelId + "/settings"))
                .bodyJson().extractingPath("$.data.checkInTime").isEqualTo("14:00:00");

        MvcTestResult duplicate = send(admin, mvc.post().uri("/api/v1/hotels"),
                Map.of("code", "THS01", "name", "Doublon", "currencyCode", "XOF"));
        assertThat(duplicate).hasStatus(409).bodyJson().extractingPath("$.error.code").isEqualTo("HOTEL_CODE_ALREADY_EXISTS");
    }

    @Test
    void onlyASuperAdministratorCreatesHotelsAndCurrenciesAreChecked() {
        assertThat(send(auth.demo("direction"), mvc.post().uri("/api/v1/hotels"), Map.of("code", "ZIG01", "name", "Ziguinchor")))
                .hasStatus(403);
        assertThat(send(auth.admin(), mvc.post().uri("/api/v1/hotels"),
                Map.of("code", "ZIG01", "name", "Ziguinchor", "currencyCode", "ZZZ")))
                .hasStatus(400).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("currencyCode");
    }

    // --- US-HOTEL-02 -------------------------------------------------------------------------------------

    @Test
    void theDirectorUpdatesInvoiceInformationAndTheLogo() {
        Session admin = auth.admin();
        String hotelId = createHotel(admin, "INF01");
        Session director = auth.createActiveUser(admin, "dir.inf01", "DIRECTOR", UUID.fromString(hotelId), "Teranga#Saloum2026");
        JsonNode hotel = auth.readBody(get(director, "/api/v1/hotels/" + hotelId)).path("data");

        Map<String, Object> update = new HashMap<>();
        update.put("name", "Hôtel Informations");
        update.put("legalName", "Informations SARL");
        update.put("address", "Rue 10, Dakar");
        update.put("email", "contact@informations.sn");
        update.put("legalIds", Map.of("RCCM", "SN-DKR-2026-B-12345", "NINEA", "123456789"));
        update.put("language", "fr");
        update.put("version", hotel.path("version").asLong());
        assertThat(send(director, mvc.put().uri("/api/v1/hotels/" + hotelId), update))
                .hasStatusOk().bodyJson().extractingPath("$.data.legalIds.NINEA").isEqualTo("123456789");
        assertThat(send(director, mvc.put().uri("/api/v1/hotels/" + hotelId), update))
                .hasStatus(409).bodyJson().extractingPath("$.error.code").isEqualTo("VERSION_CONFLICT");

        MockMultipartFile logo = new MockMultipartFile("file", "logo.png", "image/png", TINY_PNG);
        assertThat(mvc.put().uri("/api/v1/hotels/" + hotelId + "/logo").multipart().file(logo)
                .header(HttpHeaders.AUTHORIZATION, director.bearer())).hasStatus(204);
        assertThat(mvc.get().uri("/api/v1/hotels/" + hotelId + "/logo").header(HttpHeaders.AUTHORIZATION, director.bearer()))
                .hasStatusOk().hasContentType("image/png");
        MockMultipartFile text = new MockMultipartFile("file", "logo.txt", "text/plain", "pas une image".getBytes());
        assertThat(mvc.put().uri("/api/v1/hotels/" + hotelId + "/logo").multipart().file(text)
                .header(HttpHeaders.AUTHORIZATION, director.bearer()))
                .hasStatus(422).bodyJson().extractingPath("$.error.code").isEqualTo("UNSUPPORTED_IMAGE_TYPE");
    }

    // --- US-HOTEL-03 -------------------------------------------------------------------------------------

    @Test
    void operatingSettingsAreValidatedAndRestrictedToTheirManagers() {
        Session admin = auth.admin();
        String hotelId = createHotel(admin, "SET01");
        JsonNode settings = auth.readBody(get(admin, "/api/v1/hotels/" + hotelId + "/settings")).path("data");

        Map<String, Object> changed = auth.readBody(get(admin, "/api/v1/hotels/" + hotelId + "/settings")).path("data")
                .propertyStream().collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll);
        changed.put("checkInTime", "13:00:00");
        changed.put("inspectionRequired", true);
        changed.put("discountLimits", Map.of("RECEPTIONIST", 5, "DIRECTOR", 20));
        assertThat(send(admin, mvc.put().uri("/api/v1/hotels/" + hotelId + "/settings"), changed))
                .hasStatusOk().bodyJson().extractingPath("$.data.checkInTime").isEqualTo("13:00:00");

        changed.put("discountLimits", Map.of("RECEPTIONIST", 150));
        assertThat(send(admin, mvc.put().uri("/api/v1/hotels/" + hotelId + "/settings"), changed)).hasStatus(400);
        assertThat(settings.path("pricesIncludeTax").asBoolean()).isTrue();

        assertThat(send(auth.demo("reception"), mvc.put().uri("/api/v1/hotels/" + DAKAR + "/settings"), changed))
                .hasStatus(403);
    }

    // --- US-TAX-01 ---------------------------------------------------------------------------------------

    @Test
    void demoTaxesAreAvailableWithTheirCurrentValue() {
        MvcTestResult taxes = get(auth.demo("reception"), "/api/v1/hotels/" + DAKAR + "/taxes");

        assertThat(taxes).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.data[?(@.code == 'TVA')].currentRate").asArray().containsExactly(18.0);
            assertThat(json).extractingPath("$.data[?(@.code == 'TS')].currentAmount").asArray().containsExactly(1000.0);
            assertThat(json).extractingPath("$.data[?(@.code == 'TS')].exemptChildrenUnderAge").asArray().containsExactly(12);
        });
    }

    @Test
    void aRateChangeOpensANewPeriodAndNeverRewritesThePast() {
        Session admin = auth.admin();
        String hotelId = createHotel(admin, "TAX01");
        LocalDate businessDate = LocalDate.parse(auth.readBody(get(admin, "/api/v1/hotels/" + hotelId)).path("data")
                .path("businessDate").asString());

        assertThat(send(admin, mvc.post().uri("/api/v1/hotels/" + hotelId + "/taxes"), Map.of("code", "TVA", "name", "TVA",
                "type", "PERCENTAGE", "vat", true, "appliesTo", List.of("ACCOMMODATION"), "validFrom", businessDate.toString())))
                .hasStatus(400).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("rate");

        MvcTestResult created = send(admin, mvc.post().uri("/api/v1/hotels/" + hotelId + "/taxes"), Map.of("code", "TVA",
                "name", "TVA", "type", "PERCENTAGE", "vat", true, "appliesTo", List.of("ACCOMMODATION", "FOOD"),
                "validFrom", businessDate.minusMonths(1).toString(), "rate", 18));
        assertThat(created).hasStatus(201);
        String taxId = auth.readBody(created).path("data").path("id").asString();

        assertThat(send(admin, mvc.post().uri("/api/v1/hotels/" + hotelId + "/taxes/" + taxId + "/rates"),
                Map.of("validFrom", businessDate.minusDays(1).toString(), "rate", 10)))
                .hasStatus(409).bodyJson().extractingPath("$.error.code").isEqualTo("TAX_PERIOD_LOCKED");

        MvcTestResult changed = send(admin, mvc.post().uri("/api/v1/hotels/" + hotelId + "/taxes/" + taxId + "/rates"),
                Map.of("validFrom", businessDate.plusDays(10).toString(), "rate", 20));
        assertThat(changed).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.data.currentRate").isEqualTo(18.0);
            assertThat(json).extractingPath("$.data.periods.length()").isEqualTo(2);
            assertThat(json).extractingPath("$.data.periods[0].validTo").isEqualTo(businessDate.plusDays(10).toString());
            assertThat(json).extractingPath("$.data.periods[1].rate").isEqualTo(20.0);
        });
    }

    // --- US-PAY-01, US-REF-01 ----------------------------------------------------------------------------

    @Test
    void cashAlwaysGoesThroughTheCashRegisterAndMethodsAreDisabledNotDeleted() {
        Session admin = auth.admin();
        String hotelId = createHotel(admin, "PAY01");

        assertThat(send(admin, mvc.post().uri("/api/v1/hotels/" + hotelId + "/payment-methods"), Map.of("code", "CASH_EUR",
                "name", "Espèces en euros", "type", "CASH", "requiresCashSession", false)))
                .hasStatus(422).bodyJson().extractingPath("$.error.code").isEqualTo("CASH_REQUIRES_CASH_SESSION");

        JsonNode wave = null;
        for (JsonNode method : auth.readBody(get(admin, "/api/v1/hotels/" + hotelId + "/payment-methods")).path("data")) {
            if ("WAVE".equals(method.path("code").asString())) {
                wave = method;
            }
        }
        assertThat(wave).isNotNull();
        assertThat(send(admin, mvc.put().uri("/api/v1/hotels/" + hotelId + "/payment-methods/" + wave.path("id").asString()),
                Map.of("name", "Wave", "requiresCashSession", false, "requiresReference", true, "active", false, "sortOrder", 5)))
                .hasStatusOk().bodyJson().extractingPath("$.data.active").isEqualTo(false);
    }

    @Test
    void channelsAndReasonsCanBeAdded() {
        Session admin = auth.admin();
        String hotelId = createHotel(admin, "REF01");

        assertThat(send(admin, mvc.post().uri("/api/v1/hotels/" + hotelId + "/channels"),
                Map.of("code", "AIRBNB", "name", "Airbnb", "commissionRate", 15, "active", true)))
                .hasStatus(201).bodyJson().extractingPath("$.data.code").isEqualTo("AIRBNB");
        assertThat(send(admin, mvc.post().uri("/api/v1/hotels/" + hotelId + "/reasons"),
                Map.of("domain", "CANCELLATION", "code", "WEATHER", "label", "Intempéries", "active", true)))
                .hasStatus(201);
        assertThat(send(admin, mvc.post().uri("/api/v1/hotels/" + hotelId + "/reasons"),
                Map.of("domain", "CANCELLATION", "code", "WEATHER", "label", "Doublon", "active", true)))
                .hasStatus(409).bodyJson().extractingPath("$.error.code").isEqualTo("REASON_CODE_ALREADY_EXISTS");
    }

    @Test
    void anotherHotelsConfigurationIsInvisible() {
        assertThat(get(auth.demo("reception"), "/api/v1/hotels/" + TestAuth.SALY_HOTEL_ID + "/taxes")).hasStatus(404);
    }

    private String createHotel(Session admin, String code) {
        MvcTestResult created = send(admin, mvc.post().uri("/api/v1/hotels"),
                Map.of("code", code, "name", "Hôtel " + code, "currencyCode", "XOF", "timezone", "Africa/Dakar"));
        assertThat(created).hasStatus(201);
        return auth.readBody(created).path("data").path("id").asString();
    }

    private MvcTestResult get(Session as, String uri) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, as.bearer()).exchange();
    }

    private MvcTestResult send(Session as, MockMvcTester.MockMvcRequestBuilder request, Object body) {
        return request.header(HttpHeaders.AUTHORIZATION, as.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(auth.json(body))
                .exchange();
    }
}
