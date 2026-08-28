package web.tosunsaeng.billing.reservation.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import web.tosunsaeng.billing.config.SecurityConfig;
import web.tosunsaeng.billing.global.api.InternalApiException;
import web.tosunsaeng.billing.reservation.application.ReserveCommand;
import web.tosunsaeng.billing.reservation.application.ReservePayloadHasher;
import web.tosunsaeng.billing.reservation.application.ReserveResult;
import web.tosunsaeng.billing.reservation.application.ReserveService;
import web.tosunsaeng.billing.reservation.domain.IdempotencyCommand;
import web.tosunsaeng.billing.reservation.domain.Reservation;

@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "billing.internal-ingress.mode=test",
        "billing.trial-eligibility.expected-consumer-scope-id=opaque-scope-v1"
})
class ReservationControllerTest {

    private static final String PATH = "/internal/v1/reservations";
    private static final String KEY = "018f6f36-2f42-4bf5-8c17-0be35de4872c";
    private static final String JSON = """
            {"userId":"e8b37a41-bae6-47f1-a770-052e6c5786d4",
             "sessionId":"session-1","mockExamId":"mock-1"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReserveRequestDecoder decoder;

    @MockitoBean
    private IdempotencyKeyParser keyParser;

    @MockitoBean
    private ReservePayloadHasher payloadHasher;

    @MockitoBean
    private ReserveService reserveService;

    @Test
    void learningCorePrincipalReceivesDirect200Dto() throws Exception {
        ReserveRequest request = request();
        when(keyParser.parse(KEY)).thenReturn(KEY);
        when(decoder.decode(any())).thenReturn(request);
        when(payloadHasher.hash(request)).thenReturn("digest");
        when(reserveService.reserve(any())).thenReturn(new ReserveResult(snapshot(), false));

        mockMvc.perform(post(PATH)
                        .with(user("learning-core").roles("LEARNING_CORE_WORKLOAD"))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(KEY))
                .andExpect(jsonPath("$.reservationKind").value("INITIAL"))
                .andExpect(jsonPath("$.reservationStatus").value("RESERVED"))
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void identityPrincipalIsDeniedBeforeController() throws Exception {
        mockMvc.perform(post(PATH)
                        .with(user("identity").roles("IDENTITY_WORKLOAD"))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON))
                .andExpect(status().isForbidden());

        verifyNoInteractions(decoder, keyParser, payloadHasher, reserveService);
    }

    @Test
    void invalidIdempotencyKeyUsesStable400Envelope() throws Exception {
        when(keyParser.parse(null)).thenThrow(InternalApiException.invalidIdempotencyKey());

        mockMvc.perform(post(PATH)
                        .with(user("learning-core").roles("LEARNING_CORE_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.correlationId").isString());
    }

    @Test
    void processingIncludesRetryAfterWithoutSensitiveValues() throws Exception {
        when(keyParser.parse(KEY)).thenReturn(KEY);
        when(decoder.decode(any())).thenReturn(request());
        when(payloadHasher.hash(any())).thenReturn("digest");
        when(reserveService.reserve(any())).thenThrow(InternalApiException.commandProcessing());

        mockMvc.perform(post(PATH)
                        .with(user("learning-core").roles("LEARNING_CORE_WORKLOAD"))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON))
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(jsonPath("$.code").value("COMMAND_PROCESSING"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("e8b37a41")
                )));
    }

    @Test
    void oversizedBodyIsRejectedBeforeDecoder() throws Exception {
        when(keyParser.parse(KEY)).thenReturn(KEY);
        byte[] body = new byte[ReserveRequestDecoder.MAX_PAYLOAD_BYTES + 1];

        mockMvc.perform(post(PATH)
                        .with(user("learning-core").roles("LEARNING_CORE_WORKLOAD"))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(decoder, payloadHasher, reserveService);
    }

    private static ReserveRequest request() {
        return new ReserveRequest(
                "e8b37a41-bae6-47f1-a770-052e6c5786d4", "session-1", "mock-1"
        );
    }

    private static IdempotencyCommand.ResponseSnapshot snapshot() {
        return new IdempotencyCommand.ResponseSnapshot(
                KEY,
                "36c2356c-29d1-443f-b8f1-298345ee4e89",
                Reservation.Kind.INITIAL,
                Reservation.Status.RESERVED,
                "be07ae1d-f877-4ae4-82df-c5f442e9bb8e",
                "session-1",
                "mock-1",
                Instant.parse("2026-08-28T00:05:00Z")
        );
    }
}
