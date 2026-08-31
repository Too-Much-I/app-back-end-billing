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
import web.tosunsaeng.billing.domain.reservation.api.support.IdempotencyKeyParser;
import web.tosunsaeng.billing.domain.reservation.api.support.ReserveRequestDecoder;
import web.tosunsaeng.billing.domain.reservation.api.support.LifecycleRequestDecoder;
import web.tosunsaeng.billing.domain.reservation.api.support.ReservationIdParser;
import web.tosunsaeng.billing.domain.reservation.application.LifecyclePayloadHasher;
import web.tosunsaeng.billing.domain.reservation.application.ReservationLifecycleService;
import web.tosunsaeng.billing.domain.reservation.application.ReservePayloadHasher;
import web.tosunsaeng.billing.domain.reservation.application.ReserveService;
import web.tosunsaeng.billing.domain.reservation.converter.ReservationConverter;

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

    @MockitoBean
    private ReservationConverter reservationConverter;

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
