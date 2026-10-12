package web.tosunsaeng.billing.domain.payment;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import web.tosunsaeng.billing.domain.payment.api.PaymentWithdrawalDecoder;
import web.tosunsaeng.billing.global.exception.InternalApiException;
import static org.assertj.core.api.Assertions.*;

class PaymentWithdrawalDecoderTest {
    static final String EVENT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    static final String USER = "bbbbbbbb-bbbb-1bbb-8bbb-bbbbbbbbbbbb";
    static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    final PaymentWithdrawalDecoder decoder = new PaymentWithdrawalDecoder(Clock.fixed(NOW, ZoneOffset.UTC));
    static String payload(String date) {
        return "{\"eventId\":\"" + EVENT + "\",\"schemaVersion\":1,\"userId\":\"" + USER + "\",\"withdrawnAt\":\"" + date + "\"}";
    }
    @Test void canonicalDigestMatchesWireOrderAndPreservesNanos() throws Exception {
        String source = payload("2026-10-12T00:00:00.123456789Z");
        var event = decoder.decode(source.getBytes(StandardCharsets.UTF_8));
        assertThat(event.digest()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))));
        String reordered = "{ \"withdrawnAt\":\"2026-10-12T00:00:00.123456789Z\", \"userId\":\""+USER+"\",\"schemaVersion\":1,\"eventId\":\""+EVENT+"\" }";
        assertThat(decoder.decode(reordered.getBytes()).digest()).isEqualTo(event.digest());
        assertThat(decoder.decode(payload("2026-10-12T00:00:00.000Z").getBytes()).digest())
                .isEqualTo(decoder.decode(payload("2026-10-12T00:00:00Z").getBytes()).digest());
    }
    @ParameterizedTest @ValueSource(strings = {
            "{}", "null", "[]", "", "1", "true",
            "{\"eventId\":null}", "{\"schemaVersion\":1,\"schemaVersion\":1}"
    }) void rejectsMalformedShapes(String value) { invalid(value); }
    @Test void rejectsDuplicatesUnknownTrailingAndCoercion() {
        String valid = payload(NOW.toString());
        invalid(valid + " {}");
        invalid(valid.replace("{", "{\"extra\":true,"));
        invalid(valid.replace("{", "{\"schemaVersion\":1,"));
        for (String value : new String[]{"\"1\"", "true", "null", "1.0", "1e0", "9223372036854775808"})
            invalid(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":"+value));
        invalid(valid.replace(EVENT, EVENT.toUpperCase()));
        invalid(valid.replace(USER, USER.toUpperCase()));
        invalid(valid.replace("\""+USER+"\"", "123"));
        decoder.decode(valid.replace(EVENT, USER).getBytes()); // Existing wire only requires canonical UUIDs.
        invalid(valid.replace("\"withdrawnAt\":\""+NOW+"\"", "\"withdrawnAt\":null"));
        invalid(" ".repeat(PaymentWithdrawalDecoder.MAX_BYTES+1));
    }
    @Test void acceptsOldAndBoundaryTimeButRejectsFutureAndOffset() {
        decoder.decode(payload("2000-01-01T00:00:00Z").getBytes());
        decoder.decode(payload(NOW.plusSeconds(60).toString()).getBytes());
        invalid(payload(NOW.plusSeconds(60).plusNanos(1).toString()));
        invalid(payload("2026-10-12T00:00:00+00:00"));
        invalid(payload("not-a-time"));
    }
    @Test void unsupportedSchemaIs422() {
        assertThatThrownBy(() -> decoder.decode(payload(NOW.toString()).replace("\"schemaVersion\":1", "\"schemaVersion\":2").getBytes()))
                .isInstanceOfSatisfying(InternalApiException.class, e -> assertThat(e.status().value()).isEqualTo(422));
    }
    void invalid(String value) {
        assertThatThrownBy(() -> decoder.decode(value.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(InternalApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_REQUEST"));
    }
}
