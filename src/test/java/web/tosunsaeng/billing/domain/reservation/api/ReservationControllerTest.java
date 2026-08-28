package web.tosunsaeng.billing.domain.reservation.api;

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

import web.tosunsaeng.billing.global.config.security.SecurityConfig;
import web.tosunsaeng.billing.domain.reservation.exception.ReservationException;
import web.tosunsaeng.billing.domain.reservation.application.ReserveCommand;
import web.tosunsaeng.billing.domain.reservation.application.ReservePayloadHasher;
import web.tosunsaeng.billing.domain.reservation.application.ReserveResult;
import web.tosunsaeng.billing.domain.reservation.application.ReserveService;
import web.tosunsaeng.billing.domain.reservation.application.LifecyclePayloadHasher;
import web.tosunsaeng.billing.domain.reservation.application.ReservationLifecycleService;
import web.tosunsaeng.billing.domain.reservation.application.LifecycleResult;
import web.tosunsaeng.billing.domain.reservation.application.ReservationStatusResult;
import web.tosunsaeng.billing.domain.reservation.api.support.IdempotencyKeyParser;
import web.tosunsaeng.billing.domain.reservation.api.support.LifecycleRequestDecoder;
import web.tosunsaeng.billing.domain.reservation.api.support.ReservationIdParser;
import web.tosunsaeng.billing.domain.reservation.api.support.ReserveRequestDecoder;
import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.reservation.domain.entity.IdempotencyCommand;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest;
import web.tosunsaeng.billing.domain.reservation.dto.request.ConfirmRequest;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReservationStatusRequest;
import web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest;
import web.tosunsaeng.billing.domain.reservation.converter.ReservationConverter;

@WebMvcTest(ReservationController.class)
@Import({SecurityConfig.class, ReservationConverter.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "billing.internal-ingress.mode=test",
        "billing.trial-eligibility.expected-consumer-scope-id=opaque-scope-v1"
})
class ReservationControllerTest {

    private static final String PATH = "/internal/v1/reservations";
    private static final String KEY = "018f6f36-2f42-4bf5-8c17-0be35de4872c";
    private static final String RESERVATION_ID = "36c2356c-29d1-443f-b8f1-298345ee4e89";
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

    @MockitoBean
    private LifecycleRequestDecoder lifecycleRequestDecoder;

    @MockitoBean
    private ReservationIdParser reservationIdParser;

    @MockitoBean
    private LifecyclePayloadHasher lifecyclePayloadHasher;

    @MockitoBean
    private ReservationLifecycleService reservationLifecycleService;

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
        when(keyParser.parse(null)).thenThrow(ReservationException.invalidIdempotencyKey());

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
        when(reserveService.reserve(any())).thenThrow(ReservationException.commandProcessing());

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

    @Test
    void confirmReturnsDirectLifecycleDto() throws Exception {
        ConfirmRequest body = new ConfirmRequest(
                "e8b37a41-bae6-47f1-a770-052e6c5786d4", "session-1",
                Instant.parse("2026-08-26T06:30:01.200Z")
        );
        when(reservationIdParser.parse(RESERVATION_ID)).thenReturn(RESERVATION_ID);
        when(keyParser.parse(KEY)).thenReturn(KEY);
        when(lifecycleRequestDecoder.decodeConfirm(any())).thenReturn(body);
        when(lifecyclePayloadHasher.hashConfirm(RESERVATION_ID, body)).thenReturn("hash");
        when(reservationLifecycleService.confirm(any())).thenReturn(new LifecycleResult(
                lifecycleSnapshot(Reservation.Status.CONFIRMED, AttemptGroup.Status.OPEN), false
        ));

        mockMvc.perform(post(PATH + "/" + RESERVATION_ID + "/confirm")
                        .with(user("learning-core").roles("LEARNING_CORE_WORKLOAD"))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.attemptGroupStatus").value("OPEN"))
                .andExpect(jsonPath("$.confirmedAt").value("2026-08-28T00:00:00Z"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void cancelAndStatusAreLearningCoreOnly() throws Exception {
        CancelRequest cancel = new CancelRequest(
                "e8b37a41-bae6-47f1-a770-052e6c5786d4",
                CancelRequest.Reason.SESSION_COMMIT_FAILED
        );
        when(reservationIdParser.parse(RESERVATION_ID)).thenReturn(RESERVATION_ID);
        when(keyParser.parse(KEY)).thenReturn(KEY);
        when(lifecycleRequestDecoder.decodeCancel(any())).thenReturn(cancel);
        when(lifecyclePayloadHasher.hashCancel(RESERVATION_ID, cancel)).thenReturn("hash");
        when(reservationLifecycleService.cancel(any())).thenReturn(new LifecycleResult(
                lifecycleSnapshot(Reservation.Status.CANCELED, null), false
        ));

        mockMvc.perform(post(PATH + "/" + RESERVATION_ID + "/cancel")
                        .with(user("learning-core").roles("LEARNING_CORE_WORKLOAD"))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("CANCELED"))
                .andExpect(jsonPath("$.attemptGroupId").doesNotExist());

        mockMvc.perform(post(PATH + "/status")
                        .with(user("identity").roles("IDENTITY_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusReturnsLiveReadModelWithOptionalFields() throws Exception {
        ReservationStatusRequest body = new ReservationStatusRequest(
                "e8b37a41-bae6-47f1-a770-052e6c5786d4", KEY
        );
        when(lifecycleRequestDecoder.decodeStatus(any())).thenReturn(body);
        when(reservationLifecycleService.status(body.userId(), body.operationId()))
                .thenReturn(new ReservationStatusResult(
                        KEY, RESERVATION_ID, Reservation.Kind.INITIAL,
                        Reservation.Status.RESERVED,
                        "be07ae1d-f877-4ae4-82df-c5f442e9bb8e", null,
                        "session-1", "mock-1", Instant.parse("2026-08-28T00:05:00Z"), null
                ));

        mockMvc.perform(post(PATH + "/status")
                        .with(user("learning-core").roles("LEARNING_CORE_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("RESERVED"))
                .andExpect(jsonPath("$.attemptGroupStatus").doesNotExist())
                .andExpect(jsonPath("$.terminalAt").doesNotExist());
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

    private static IdempotencyCommand.LifecycleResponseSnapshot lifecycleSnapshot(
            Reservation.Status status,
            AttemptGroup.Status groupStatus
    ) {
        return new IdempotencyCommand.LifecycleResponseSnapshot(
                KEY, RESERVATION_ID, status,
                groupStatus == null ? null : "be07ae1d-f877-4ae4-82df-c5f442e9bb8e",
                groupStatus, "session-1", Instant.parse("2026-08-28T00:00:00Z")
        );
    }
}
