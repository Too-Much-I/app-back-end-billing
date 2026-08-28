package web.tosunsaeng.billing.config;

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

import web.tosunsaeng.billing.trialeligibility.api.TrialEligibilityEventDecoder;
import web.tosunsaeng.billing.trialeligibility.application.TrialEligibilityEventService;
import web.tosunsaeng.billing.reservation.api.IdempotencyKeyParser;
import web.tosunsaeng.billing.reservation.api.ReserveRequestDecoder;
import web.tosunsaeng.billing.reservation.api.LifecycleRequestDecoder;
import web.tosunsaeng.billing.reservation.api.ReservationIdParser;
import web.tosunsaeng.billing.reservation.application.LifecyclePayloadHasher;
import web.tosunsaeng.billing.reservation.application.ReservationLifecycleService;
import web.tosunsaeng.billing.reservation.application.ReservePayloadHasher;
import web.tosunsaeng.billing.reservation.application.ReserveService;

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
}
