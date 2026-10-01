package sn.niirma.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import sn.niirma.idempotency.Idempotent;
import sn.niirma.shared.web.TraceIdFilter;
import sn.niirma.support.IntegrationTest;

@IntegrationTest
class ErrorHandlingIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void everyResponseCarriesATraceIdThatMatchesTheErrorBody() {
        MvcTestResult response = mvc.get().uri("/api/v1/hotels/{id}", "0190f2a0-0000-7000-8000-00000000ffff").exchange();

        String traceId = response.getResponse().getHeader(TraceIdFilter.RESPONSE_HEADER);
        assertThat(traceId).isNotBlank();
        assertThat(response).bodyJson().extractingPath("$.traceId").isEqualTo(traceId);
        assertThat(response).bodyJson().extractingPath("$.success").isEqualTo(false);
        assertThat(response).bodyJson().extractingPath("$.timestamp").isNotNull();
    }

    @Test
    void reusesAValidIncomingRequestId() {
        MvcTestResult response = mvc.get().uri("/api/v1/hotels")
                .header(TraceIdFilter.REQUEST_HEADER, "client-req-12345")
                .exchange();

        assertThat(response).hasHeader(TraceIdFilter.RESPONSE_HEADER, "client-req-12345");
    }

    @Test
    void validationErrorsListTheInvalidFields() {
        MvcTestResult response = mvc.post().uri("/api/v1/test-operations")
                .header(Idempotent.HEADER, "validation-key-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\": \"\", \"amount\": -5}")
                .exchange();

        assertThat(response).hasStatus(400);
        assertThat(response).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
        assertThat(response).bodyJson().extractingPath("$.error.details[*].field")
                .asArray().containsExactlyInAnyOrder("label", "amount");
    }

    @Test
    void malformedJsonIsRejected() {
        MvcTestResult response = mvc.post().uri("/api/v1/test-operations")
                .header(Idempotent.HEADER, "malformed-key-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json")
                .exchange();

        assertThat(response).hasStatus(400);
        assertThat(response).bodyJson().extractingPath("$.error.code").isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void invalidPathParameterIsRejected() {
        MvcTestResult response = mvc.get().uri("/api/v1/hotels/{id}", "pas-un-uuid").exchange();

        assertThat(response).hasStatus(400);
        assertThat(response).bodyJson().extractingPath("$.error.code").isEqualTo("INVALID_PARAMETER");
    }

    @Test
    void unknownRouteReturnsNotFoundInStandardFormat() {
        MvcTestResult response = mvc.get().uri("/api/v1/inexistant").exchange();

        assertThat(response).hasStatus(404);
        assertThat(response).bodyJson().extractingPath("$.error.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void unexpectedErrorsDoNotLeakTechnicalDetails() throws Exception {
        MvcTestResult response = mvc.post().uri("/api/v1/test-operations/crash").exchange();

        assertThat(response).hasStatus(500);
        assertThat(response).bodyJson().extractingPath("$.error.code").isEqualTo("INTERNAL_ERROR");
        assertThat(response.getResponse().getContentAsString()).doesNotContain("détail technique");
    }
}
