package web.tosunsaeng.billing.reservation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import web.tosunsaeng.billing.global.api.InternalApiException;

class IdempotencyKeyParserTest {

    private final IdempotencyKeyParser parser = new IdempotencyKeyParser();

    @Test
    void acceptsOnlyLowercaseUuidV4() {
        String value = "018f6f36-2f42-4bf5-8c17-0be35de4872c";
        assertThat(parser.parse(value)).isEqualTo(value);

        assertInvalid(null);
        assertInvalid(value.toUpperCase());
        assertInvalid("00000000-0000-1000-8000-000000000001");
        assertInvalid("not-a-uuid");
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> parser.parse(value))
                .isInstanceOf(InternalApiException.class)
                .extracting("code").isEqualTo("INVALID_IDEMPOTENCY_KEY");
    }
}
