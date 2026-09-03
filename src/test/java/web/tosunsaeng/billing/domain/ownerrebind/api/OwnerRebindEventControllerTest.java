package web.tosunsaeng.billing.domain.ownerrebind.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
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

import web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindMetrics;
import web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindOutcome;
import web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindService;
import web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindTracing;
import web.tosunsaeng.billing.domain.ownerrebind.domain.enums.OwnerRebindEventKind;
import web.tosunsaeng.billing.domain.ownerrebind.domain.model.OwnerRebindCommand;
import web.tosunsaeng.billing.domain.ownerrebind.exception.OwnerRebindException;
import web.tosunsaeng.billing.global.config.security.SecurityConfig;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.global.observability.TraceCorrelation;

@WebMvcTest(OwnerRebindEventController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "billing.internal-ingress.mode=test",
        "billing.trial-eligibility.expected-consumer-scope-id=opaque-scope-v1",
        "billing.owner-rebind.enabled=true"
})
class OwnerRebindEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PhoneOwnerRebindEventDecoder phoneDecoder;
    @MockitoBean
    private UserMergedEventDecoder mergeDecoder;
    @MockitoBean
    private OwnerRebindService service;
    @MockitoBean
    private OwnerRebindTracing tracing;
    @MockitoBean
    private OwnerRebindMetrics metrics;
    @MockitoBean
    private TraceCorrelation traceCorrelation;

    @BeforeEach
    void setUp() {
        when(traceCorrelation.classify(nullable(String.class)))
                .thenReturn(TraceCorrelation.TraceparentStatus.MISSING);
        when(tracing.inConsumeSpan(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
    }

    @Test
    void identityRoleCanDeliverBothLifecycleEvents() throws Exception {
        when(phoneDecoder.decode(any())).thenReturn(command(OwnerRebindEventKind.PHONE_REJOIN));
        when(mergeDecoder.decode(any())).thenReturn(command(OwnerRebindEventKind.USER_MERGED));
        when(service.process(any())).thenReturn(OwnerRebindOutcome.APPLIED);

        mockMvc.perform(post(OwnerRebindEventController.PHONE_PATH)
                        .with(user("identity").roles("IDENTITY_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(post(OwnerRebindEventController.MERGE_PATH)
                        .with(user("identity").roles("IDENTITY_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void learningCoreAndUnsignedAreDeniedBeforeDecode() throws Exception {
        mockMvc.perform(post(OwnerRebindEventController.PHONE_PATH)
                        .with(user("learning").roles("LEARNING_CORE_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(OwnerRebindEventController.MERGE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(phoneDecoder, mergeDecoder, service);
    }

    @Test
    void pendingUses503AndDeltaSecondsRetryAfter() throws Exception {
        when(phoneDecoder.decode(any())).thenReturn(command(OwnerRebindEventKind.PHONE_REJOIN));
        when(service.process(any())).thenThrow(OwnerRebindException.pending(300));

        mockMvc.perform(post(OwnerRebindEventController.PHONE_PATH)
                        .with(user("identity").roles("IDENTITY_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "300"))
                .andExpect(jsonPath("$.code").value("OWNER_REBIND_PENDING"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void mapsMalformedUnsupportedAndConflictToApprovedStatuses() throws Exception {
        when(phoneDecoder.decode(any()))
                .thenThrow(InternalApiException.invalidRequest())
                .thenThrow(OwnerRebindException.unsupportedContract())
                .thenReturn(command(OwnerRebindEventKind.PHONE_REJOIN));
        when(service.process(any())).thenThrow(OwnerRebindException.ownerConflict());

        mockMvc.perform(post(OwnerRebindEventController.PHONE_PATH)
                        .with(user("identity").roles("IDENTITY_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(post(OwnerRebindEventController.PHONE_PATH)
                        .with(user("identity").roles("IDENTITY_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CONTRACT"));
        mockMvc.perform(post(OwnerRebindEventController.PHONE_PATH)
                        .with(user("identity").roles("IDENTITY_WORKLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWNER_REBIND_CONFLICT"));
    }

    private static OwnerRebindCommand command(OwnerRebindEventKind kind) {
        return new OwnerRebindCommand(
                "00000000-0000-4000-8000-000000000001",
                kind,
                1,
                Instant.parse("2026-09-02T04:59:00Z"),
                "00000000-0000-4000-8000-000000000002",
                "00000000-0000-4000-8000-000000000003",
                kind == OwnerRebindEventKind.PHONE_REJOIN ? "opaque-scope-v1" : null,
                kind == OwnerRebindEventKind.PHONE_REJOIN ? 2L : null,
                kind == OwnerRebindEventKind.PHONE_REJOIN ? 1L : null,
                "digest"
        );
    }
}
