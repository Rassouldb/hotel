package sn.niirma.rooms;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import sn.niirma.support.IntegrationTest;
import sn.niirma.support.TestAuth;
import sn.niirma.support.TestAuth.Session;

/**
 * US-ROOM-01 à 05 : types de chambres, chambres, statut ménage, historique, blocages.
 */
@IntegrationTest
class RoomsIntegrationTest {

    private static final String DAKAR = "/api/v1/hotels/" + TestAuth.DAKAR_HOTEL_ID;
    private static final String STANDARD_TYPE = "0190f2a0-0000-7000-8000-000000000301";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestAuth auth;

    @Test
    void theDemoHotelHasItsRoomsReadyForSale() {
        MvcTestResult rooms = get(auth.demo("reception"), DAKAR + "/rooms");

        assertThat(rooms).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.data[?(@.number == '101')].roomTypeCode").asArray().containsExactly("STD");
            assertThat(json).extractingPath("$.data[?(@.number == '101')].occupancyStatus").asArray().containsExactly("VACANT");
            assertThat(json).extractingPath("$.data[?(@.number == '101')].technicalStatus").asArray().containsExactly("IN_SERVICE");
            assertThat(json).extractingPath("$.data[?(@.number == '101')].saleable").asArray().containsExactly(true);
        });
    }

    @Test
    void roomTypeCapacitiesAreConsistentAndAUsedTypeCannotBeDisabled() {
        Session admin = auth.admin();

        assertThat(send(admin, mvc.post().uri(DAKAR + "/room-types"), roomType("FAM", 4, 3, 2, 1, true)))
                .hasStatus(400).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("baseOccupancy");

        MvcTestResult created = send(admin, mvc.post().uri(DAKAR + "/room-types"), roomType("FAM", 2, 4, 2, 2, true));
        assertThat(created).hasStatus(201).bodyJson().extractingPath("$.data.basePrice").isEqualTo(60000);
        JsonNode type = auth.readBody(created).path("data");
        send(admin, mvc.post().uri(DAKAR + "/rooms/batch"), Map.of("from", 801, "to", 801, "roomTypeId", type.path("id").asString(),
                "floor", 8));

        Map<String, Object> disable = roomType("FAM", 2, 4, 2, 2, false);
        disable.put("version", type.path("version").asLong());
        assertThat(send(admin, mvc.put().uri(DAKAR + "/room-types/" + type.path("id").asString()), disable))
                .hasStatus(409).bodyJson().extractingPath("$.error.code").isEqualTo("ROOM_TYPE_IN_USE");
    }

    @Test
    void roomsAreCreatedInSeriesAllOrNothing() {
        Session admin = auth.admin();

        MvcTestResult batch = send(admin, mvc.post().uri(DAKAR + "/rooms/batch"),
                Map.of("from", 501, "to", 505, "roomTypeId", STANDARD_TYPE, "floor", 5));
        assertThat(batch).hasStatus(201).bodyJson().extractingPath("$.data.length()").isEqualTo(5);

        MvcTestResult overlap = send(admin, mvc.post().uri(DAKAR + "/rooms/batch"),
                Map.of("from", 504, "to", 510, "roomTypeId", STANDARD_TYPE, "floor", 5));
        assertThat(overlap).hasStatus(409).bodyJson().extractingPath("$.error.details[*].number").asArray()
                .containsExactly("504", "505");
        assertThat(get(admin, DAKAR + "/rooms")).bodyJson().extractingPath("$.data[?(@.number == '506')]").asArray().isEmpty();

        assertThat(send(admin, mvc.post().uri(DAKAR + "/rooms"), Map.of("number", "501", "roomTypeId", STANDARD_TYPE)))
                .hasStatus(409).bodyJson().extractingPath("$.error.code").isEqualTo("ROOM_NUMBER_ALREADY_EXISTS");
    }

    @Test
    void housekeepingFollowsItsCycleAndIsHistorised() {
        Session admin = auth.admin();
        String roomId = newRoom(admin, 601);
        Session housekeeping = auth.demo("menage");

        assertThat(hk(housekeeping, roomId, "DIRTY", null))
                .hasStatus(400).bodyJson().extractingPath("$.error.code").isEqualTo("REASON_REQUIRED");
        assertThat(hk(housekeeping, roomId, "DIRTY", "Utilisée après nettoyage")).hasStatusOk();
        assertThat(hk(housekeeping, roomId, "INSPECTED", null))
                .hasStatus(409).bodyJson().extractingPath("$.error.code").isEqualTo("INVALID_HOUSEKEEPING_TRANSITION");
        assertThat(hk(housekeeping, roomId, "CLEANING", null)).hasStatusOk();
        assertThat(hk(housekeeping, roomId, "CLEAN", null)).hasStatusOk()
                .bodyJson().extractingPath("$.data.housekeepingStatus").isEqualTo("CLEAN");
        assertThat(hk(housekeeping, roomId, "INSPECTED", null))
                .hasStatus(403).bodyJson().extractingPath("$.error.code").isEqualTo("PERMISSION_DENIED");
        assertThat(hk(admin, roomId, "INSPECTED", null)).hasStatusOk();

        assertThat(get(admin, DAKAR + "/rooms/" + roomId + "/history")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.data.length()").isEqualTo(4);
            assertThat(json).extractingPath("$.data[0].newValue").isEqualTo("INSPECTED");
            assertThat(json).extractingPath("$.data[3].reason").isEqualTo("Utilisée après nettoyage");
            assertThat(json).extractingPath("$.data[3].userName").isEqualTo("menage");
        });
        assertThat(hk(auth.demo("reception"), roomId, "DIRTY", "Test")).hasStatus(403);
    }

    @Test
    void aBlockTakesTheRoomOutOfSaleForItsPeriod() {
        Session admin = auth.admin();
        String roomId = newRoom(admin, 701);
        LocalDate today = businessDate(admin);

        MvcTestResult block = send(admin, mvc.post().uri(DAKAR + "/room-blocks"), Map.of("roomId", roomId, "type", "MAINTENANCE",
                "startDate", today.toString(), "endDate", today.plusDays(3).toString(), "reason", "Climatisation"));
        assertThat(block).hasStatus(201).bodyJson().extractingPath("$.data.inProgress").isEqualTo(true);
        JsonNode blocked = room(admin, "701");
        assertThat(blocked.path("technicalStatus").asString()).isEqualTo("MAINTENANCE");
        assertThat(blocked.path("saleable").asBoolean()).isFalse();

        assertThat(send(admin, mvc.post().uri(DAKAR + "/room-blocks"), Map.of("roomId", roomId, "type", "OUT_OF_ORDER",
                "startDate", today.plusDays(2).toString(), "endDate", today.plusDays(5).toString(), "reason", "Travaux")))
                .hasStatus(409).bodyJson().extractingPath("$.error.code").isEqualTo("ROOM_BLOCK_OVERLAP");
        assertThat(send(admin, mvc.post().uri(DAKAR + "/room-blocks"), Map.of("roomId", roomId, "type", "OUT_OF_ORDER",
                "startDate", today.minusDays(1).toString(), "endDate", today.plusDays(1).toString(), "reason", "Travaux")))
                .hasStatus(422).bodyJson().extractingPath("$.error.code").isEqualTo("BLOCK_IN_PAST");

        String blockId = auth.readBody(block).path("data").path("id").asString();
        assertThat(mvc.post().uri(DAKAR + "/room-blocks/" + blockId + "/release").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .hasStatusOk();
        assertThat(room(admin, "701").path("technicalStatus").asString()).isEqualTo("IN_SERVICE");
    }

    @Test
    void aFutureBlockCanBeDeletedButAStartedOneMustBeReleased() {
        Session admin = auth.admin();
        String roomId = newRoom(admin, 702);
        LocalDate today = businessDate(admin);

        MvcTestResult future = send(admin, mvc.post().uri(DAKAR + "/room-blocks"), Map.of("roomId", roomId, "type", "HOUSE_USE",
                "startDate", today.plusDays(10).toString(), "endDate", today.plusDays(12).toString(), "reason", "Usage interne"));
        MvcTestResult started = send(admin, mvc.post().uri(DAKAR + "/room-blocks"), Map.of("roomId", roomId, "type", "OUT_OF_ORDER",
                "startDate", today.toString(), "endDate", today.plusDays(2).toString(), "reason", "Dégât des eaux"));

        assertThat(mvc.delete().uri(DAKAR + "/room-blocks/" + auth.readBody(future).path("data").path("id").asString())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())).hasStatus(204);
        assertThat(mvc.delete().uri(DAKAR + "/room-blocks/" + auth.readBody(started).path("data").path("id").asString())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .hasStatus(409).bodyJson().extractingPath("$.error.code").isEqualTo("BLOCK_STARTED");
        assertThat(send(auth.demo("reception"), mvc.post().uri(DAKAR + "/room-blocks"), Map.of("roomId", roomId,
                "type", "MAINTENANCE", "startDate", today.plusDays(20).toString(), "endDate", today.plusDays(21).toString(),
                "reason", "Test"))).hasStatus(403);
    }

    @Test
    void roomsOfAnotherHotelAreInvisible() {
        assertThat(get(auth.demo("reception"), "/api/v1/hotels/" + TestAuth.SALY_HOTEL_ID + "/rooms")).hasStatus(404);
    }

    private String newRoom(Session admin, int number) {
        MvcTestResult created = send(admin, mvc.post().uri(DAKAR + "/rooms"),
                Map.of("number", String.valueOf(number), "roomTypeId", STANDARD_TYPE, "floor", number / 100));
        assertThat(created).hasStatus(201);
        return auth.readBody(created).path("data").path("id").asString();
    }

    private JsonNode room(Session as, String number) {
        for (JsonNode room : auth.readBody(get(as, DAKAR + "/rooms")).path("data")) {
            if (number.equals(room.path("number").asString())) {
                return room;
            }
        }
        throw new IllegalStateException("Chambre absente : " + number);
    }

    private LocalDate businessDate(Session as) {
        return LocalDate.parse(auth.readBody(get(as, DAKAR)).path("data").path("businessDate").asString());
    }

    private MvcTestResult hk(Session as, String roomId, String status, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("reason", reason);
        return send(as, mvc.put().uri(DAKAR + "/rooms/" + roomId + "/housekeeping-status"), body);
    }

    private static Map<String, Object> roomType(String code, int base, int max, int adults, int children, boolean active) {
        Map<String, Object> type = new HashMap<>();
        type.put("code", code);
        type.put("name", "Familiale");
        type.put("baseOccupancy", base);
        type.put("maxOccupancy", max);
        type.put("maxAdults", adults);
        type.put("maxChildren", children);
        type.put("basePrice", 60000);
        type.put("active", active);
        type.put("amenityIds", List.of());
        return type;
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
