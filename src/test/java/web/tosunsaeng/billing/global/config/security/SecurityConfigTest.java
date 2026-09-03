package web.tosunsaeng.billing.global.config.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import web.tosunsaeng.billing.domain.eligibility.trial.api.TrialEligibilityEventDecoder;
import web.tosunsaeng.billing.domain.eligibility.trial.application.TrialEligibilityEventService;
import web.tosunsaeng.billing.domain.attempt.api.AttemptGroupEventDecoder;
import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventMetrics;
import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventService;
import web.tosunsaeng.billing.domain.attempt.application.AttemptGroupEventTracing;
import web.tosunsaeng.billing.domain.reservation.api.support.IdempotencyKeyParser;
import web.tosunsaeng.billing.domain.reservation.api.support.ReserveRequestDecoder;
import web.tosunsaeng.billing.domain.reservation.api.support.LifecycleRequestDecoder;
import web.tosunsaeng.billing.domain.reservation.api.support.ReservationIdParser;
import web.tosunsaeng.billing.domain.reservation.application.LifecyclePayloadHasher;
import web.tosunsaeng.billing.domain.reservation.application.ReservationLifecycleService;
import web.tosunsaeng.billing.domain.reservation.application.ReservePayloadHasher;
import web.tosunsaeng.billing.domain.reservation.application.ReserveService;
import web.tosunsaeng.billing.domain.reservation.converter.ReservationConverter;
import web.tosunsaeng.billing.domain.ownerrebind.api.PhoneOwnerRebindEventDecoder;
import web.tosunsaeng.billing.domain.ownerrebind.api.UserMergedEventDecoder;
import web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindMetrics;
import web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindService;
import web.tosunsaeng.billing.domain.ownerrebind.application.OwnerRebindTracing;
import web.tosunsaeng.billing.global.observability.TraceCorrelation;

@WebMvcTest
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrialEligibilityEventDecoder decoder;

    @MockitoBean
    private TrialEligibilityEventService service;

    @MockitoBean
    private AttemptGroupEventDecoder attemptGroupEventDecoder;

    @MockitoBean
    private AttemptGroupEventService attemptGroupEventService;
    @MockitoBean
    private AttemptGroupEventTracing attemptGroupEventTracing;

    @MockitoBean
    private AttemptGroupEventMetrics attemptGroupEventMetrics;

    @MockitoBean
    private TraceCorrelation traceCorrelation;

    @MockitoBean
    private ReserveRequestDecoder reserveRequestDecoder;

    @MockitoBean
    private IdempotencyKeyParser idempotencyKeyParser;

    @MockitoBean
    private ReservePayloadHasher reservePayloadHasher;

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

    @MockitoBean
    private ReservationConverter reservationConverter;

    @MockitoBean
    private PhoneOwnerRebindEventDecoder phoneOwnerRebindEventDecoder;

    @MockitoBean
    private UserMergedEventDecoder userMergedEventDecoder;

    @MockitoBean
    private OwnerRebindService ownerRebindService;

    @MockitoBean
    private OwnerRebindTracing ownerRebindTracing;

    @MockitoBean
    private OwnerRebindMetrics ownerRebindMetrics;

    @Test
    void unconfiguredEndpointIsDenied() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalEligibilityEndpointIsDeniedByDefault() throws Exception {
        mockMvc.perform(post("/internal/v1/eligibility/trial/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalReservationEndpointIsDeniedByDefault() throws Exception {
        mockMvc.perform(post("/internal/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalAttemptGroupEndpointIsDeniedByDefault() throws Exception {
        mockMvc.perform(post("/internal/v1/attempt-group-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void internalOwnerRebindEndpointsAreDeniedByDefault() throws Exception {
        mockMvc.perform(post("/internal/v1/eligibility/trial/owner/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/v1/owners/merge/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
