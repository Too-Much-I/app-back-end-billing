package web.tosunsaeng.billing.domain.examdeletion;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** MVC body/error boundary only. Authentication is exercised separately with actual JWT filters. */
class DeletionControllerTest {
    DeletionService service;
    MockMvc mvc;
    String body;
    @BeforeEach void setup() throws Exception {
        service = mock(DeletionService.class); mvc = MockMvcBuilders.standaloneSetup(new DeletionController(service)).build();
        try (var in = getClass().getResourceAsStream("/contracts/lc-billing-deletion-exact-r2-draft-2/bytes.json")) {
            body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(in).get("cases").get(0).get("rawUtf8").asText();
        }
    }
    @ParameterizedTest @ValueSource(strings = {"application/json", "application/json; charset=UTF-8", "application/json;charset=\"utf-8\""})
    void empty204OnlyAfterService(String type) throws Exception {
        var response = mvc.perform(post(DeletionController.ROUTE).header("Content-Type", type).content(body)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(204); assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store"); verify(service).process(any());
    }
    @ParameterizedTest @ValueSource(strings = {"text/plain", "application/problem+json", "application/json;charset=latin1",
            "application/json;charset=utf-8;charset=utf-8", "application/json;unknown=x"})
    void rejectsMediaWithoutDb(String type) throws Exception {
        var response = mvc.perform(post(DeletionController.ROUTE).header("Content-Type", type).content(body)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(415); verifyNoInteractions(service);
    }
    @Test void duplicateAndCompressedHeadersNeverReachService() throws Exception {
        for (String encoding : new String[]{"gzip", "identity,identity", "br"}) {
            var response = mvc.perform(post(DeletionController.ROUTE).contentType("application/json").header("Content-Encoding", encoding).content(body)).andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(415);
        }
        var duplicate = mvc.perform(post(DeletionController.ROUTE).header("Content-Type", "application/json", "application/json").content(body)).andReturn().getResponse();
        assertThat(duplicate.getStatus()).isEqualTo(415); verifyNoInteractions(service);
    }
    @Test void bodyLimitAndErrorsAreExactAndPrivacySafe() throws Exception {
        var oversize = mvc.perform(post(DeletionController.ROUTE).contentType("application/json")
                .content(new byte[16_385])).andReturn().getResponse();
        assertThat(oversize.getStatus()).isEqualTo(413); verifyNoInteractions(service);
        doThrow(DeletionService.unavailable()).when(service).process(any());
        var response = mvc.perform(post(DeletionController.ROUTE).contentType("application/json")
                .header("X-Correlation-Id", "untrusted-user-input").content(body.getBytes(StandardCharsets.UTF_8))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(503); assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsByteArray());
        assertThat(node.size()).isEqualTo(4); assertThat(node.get("code").asText()).isEqualTo("BILLING_TEMPORARILY_UNAVAILABLE");
        assertThat(node.get("message").asText()).isEqualTo("Billing is temporarily unavailable.");
        assertThat(node.get("retryable").booleanValue()).isTrue();
        assertThat(node.get("correlationId").asText()).matches("[A-Za-z0-9._-]{1,128}").isNotEqualTo("untrusted-user-input");
    }
}
