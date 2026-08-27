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

@WebMvcTest
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrialEligibilityEventDecoder decoder;

    @MockitoBean
    private TrialEligibilityEventService service;

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
}
