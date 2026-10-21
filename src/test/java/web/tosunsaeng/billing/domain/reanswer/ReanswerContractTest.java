package web.tosunsaeng.billing.domain.reanswer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import web.tosunsaeng.billing.domain.entitlement.exception.EntitlementQueryException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReanswerContractTest {
    static final String USER = "11111111-1111-4111-8111-111111111111";
    static final String BODY = "{\"userId\":\"" + USER + "\"}";
    static final Instant NOW = Instant.parse("2026-10-21T00:00:00.123456789Z");
    ReanswerRequestDecoder decoder = new ReanswerRequestDecoder();
    ReanswerService service = mock(ReanswerService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new ReanswerController(service)).build();

    @Test void validRequestAndExactSizeAccepted() {
        assertThat(decoder.decode(BODY.getBytes(StandardCharsets.UTF_8))).isEqualTo(USER);
        assertThat(decoder.decode((BODY + " ".repeat(16384-BODY.length())).getBytes(StandardCharsets.UTF_8))).isEqualTo(USER);
    }
    @ParameterizedTest @ValueSource(strings = {"", "null", "[]", "{}", "{\"userId\":null}", "{\"userId\":3}",
            "{\"userId\":\"A1111111-1111-4111-8111-111111111111\"}", "{\"userId\":\"1-1-1-1-1\"}",
            "{\"userId\":\"11111111-1111-4111-8111-111111111111\",\"extra\":1}",
            "{\"userId\":\"11111111-1111-4111-8111-111111111111\",\"userId\":\"11111111-1111-4111-8111-111111111111\"}"})
    void invalidWire(String body) {
        assertThatThrownBy(() -> decoder.decode(body.getBytes(StandardCharsets.UTF_8))).isInstanceOf(InternalApiException.class);
    }
    @Test void trailingOversizeAndMalformedUtf8Rejected() {
        for (byte[] bytes : new byte[][]{(BODY+" {}").getBytes(StandardCharsets.UTF_8), new byte[16385], {(byte)0xc3, 0x28}, BODY.getBytes(StandardCharsets.UTF_16)})
            assertThatThrownBy(() -> decoder.decode(bytes)).isInstanceOf(InternalApiException.class);
    }
    @Test void paidWindowCapsAtExactNanosecondAndFreeUsesFiveSeconds() {
        Instant end = NOW.plusNanos(1);
        assertThat(ReanswerService.decide(USER,NOW,false,true,end,false).validUntil()).isEqualTo(end.toString());
        assertThat(ReanswerService.decide(USER,NOW,true,true,end,true).validUntil()).isEqualTo(NOW.plusSeconds(5).toString());
        assertThat(ReanswerService.decide(USER,NOW,false,false,null,true).decision()).isEqualTo("PENDING");
        assertThat(ReanswerService.decide(USER,NOW,false,false,null,false).reason()).isEqualTo("ENTITLEMENT_INSUFFICIENT");
        assertThatThrownBy(() -> ReanswerService.decide(USER,NOW,false,true,NOW,false)).isInstanceOf(EntitlementQueryException.class);
    }
    @Test void responseIsUnwrappedAndNullIsExplicit() throws Exception {
        when(service.query(USER)).thenReturn(ReanswerService.decide(USER,NOW,false,false,null,false));
        mvc.perform(post(ReanswerController.ROUTE).contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(content().json("{\"userId\":\""+USER+"\",\"decision\":\"DENIED\",\"reason\":\"ENTITLEMENT_INSUFFICIENT\",\"checkedAt\":\""+NOW+"\",\"validUntil\":null}",true));
    }
    @Test void invalidBodyAndMediaTypeNeverReadDatabase() throws Exception {
        for (String type : new String[]{"text/plain","application/json; charset=UTF-16"}) {
            mvc.perform(post(ReanswerController.ROUTE).contentType(type).content(BODY)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(ReanswerController.ROUTE).contentType("application/json").content(BODY+" {}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("The request is invalid.")).andExpect(jsonPath("$.retryable").value(false));
        mvc.perform(post(ReanswerController.ROUTE).contentType("application/json").content(new byte[16385])).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void storageFailureIsExact503WithFreshCorrelation() throws Exception {
        when(service.query(USER)).thenThrow(new EntitlementQueryException(EntitlementQueryException.Failure.STORAGE));
        var result = mvc.perform(post(ReanswerController.ROUTE).contentType("application/json").content(BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After","1"))
                .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.code").value("BILLING_TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Billing is temporarily unavailable.")).andReturn();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("correlationId").asText();
        assertThat(UUID.fromString(id).version()).isEqualTo(4);
    }
}
