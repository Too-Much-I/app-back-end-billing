package web.tosunsaeng.billing.domain.payment.sync;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.payment.query.*;
import web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse;
import static web.tosunsaeng.billing.domain.entitlement.dto.response.EntitlementQueryResponse.*;
import static org.assertj.core.api.Assertions.*;

class PaymentSyncContractTest {
    static final String ID = "11111111-1111-4111-8111-111111111111";
    final PaymentSyncDecoder decoder = new PaymentSyncDecoder();
    @ParameterizedTest @ValueSource(strings = {"", "null", "[]", "{}", "{\"store\":\"APP_STORE\",\"transactionId\":1}",
            "{\"store\":\"APP_STORE\",\"transactionId\":null}", "{\"store\":\"APP_STORE\",\"transactionId\":\"\"}",
            "{\"store\":\"APP_STORE\",\"transactionId\":\"a\",\"userId\":\"x\"}",
            "{\"store\":\"APP_STORE\",\"store\":\"APP_STORE\",\"transactionId\":\"a\"}",
            "{\"store\":\"APP_STORE\",\"transactionId\":\"a\"} {}", "{\"store\":\"APPLE\",\"transactionId\":\"a\"}"})
    void rejectsBadBodies(String body) { assertThatThrownBy(() -> decoder.decode(ID, body.getBytes(StandardCharsets.UTF_8)))
            .isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.status()).isEqualTo(400)); }
    @Test void strictKeySizeAndOpaqueText() {
        byte[] body = "{\"transactionId\":\" AbC \",\"store\":\"PLAY_STORE\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(decoder.decode(ID, body).transactionId()).isEqualTo(" AbC ");
        for (String invalid : List.of(ID + "\n", ID + "," + ID, ID.replace("4111", "1111"), "x"))
            assertThatThrownBy(() -> decoder.decode(invalid, body)).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> decoder.decode(ID, new byte[16385])).isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.status()).isEqualTo(413));
        assertThat(decoder.decode(ID, body).toString()).doesNotContain("AbC");
    }
    @Test void goldenSyncAndOptionalSupportHint() throws Exception {
        var json = JsonMapper.builder().findAndAddModules().build();
        assertThat(json.writeValueAsString(PaymentSyncResult.pending(ID, false)))
                .isEqualTo("{\"operationId\":\"" + ID + "\",\"status\":\"PENDING\",\"purchaseId\":null,\"purchaseStatus\":null}");
        assertThat(json.readTree(json.writeValueAsString(PaymentSyncResult.pending(ID, true))).get("processingHint").asText()).isEqualTo("CONTACT_SUPPORT");
    }
    @Test void freeOffShapeIsUnchangedAndCanTakeExamNullIsNotOmitted() throws Exception {
        var json = JsonMapper.builder().findAndAddModules().build();
        var free = new EntitlementQueryResponse(Instant.EPOCH, Consistency.LOCAL_PROJECTION, Status.READY, List.of());
        var old = json.readTree(json.writeValueAsString(free));
        assertThat(old.size()).isEqualTo(4); assertThat(old.has("paid")).isFalse(); assertThat(old.has("examAvailability")).isFalse();
        var on = new EntitlementQueryResponse(Instant.EPOCH, Consistency.LOCAL_PROJECTION, Status.READY, List.of(), PaidQueryResponse.empty(false), ExamAvailability.unavailable());
        var expanded = json.readTree(json.writeValueAsString(on));
        assertThat(expanded.get("examAvailability").has("canTakeExam")).isTrue();
        assertThat(expanded.get("examAvailability").get("canTakeExam").isNull()).isTrue();
        assertThat(expanded.get("paid").get("activeEndsAt").isNull()).isTrue();
    }
    @Test void booleanDerivedFromActionAndContradictionsRejected() {
        assertThat(new ExamAvailability(false, Action.ALLOWED, Action.BLOCKED, Action.ALLOWED, ExamAvailability.Source.FREE_RETAKE, false, List.of()).canTakeExam()).isTrue();
        assertThat(new ExamAvailability(true, Action.BLOCKED, Action.BLOCKED, Action.BLOCKED, null, false, List.of("NO_ENTITLEMENT")).canTakeExam()).isFalse();
        assertThat(ExamAvailability.unavailable().canTakeExam()).isNull();
        assertThatThrownBy(() -> new ExamAvailability(true, Action.ALLOWED, Action.PENDING, Action.BLOCKED, ExamAvailability.Source.PAID, null, List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
