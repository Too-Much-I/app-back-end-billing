package web.tosunsaeng.billing.reservation.api;

import java.util.UUID;

import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.global.api.InternalApiException;

@Component
public class IdempotencyKeyParser {

    public String parse(String value) {
        if (value == null || value.isEmpty()) {
            throw InternalApiException.invalidIdempotencyKey();
        }
        try {
            UUID uuid = UUID.fromString(value);
            if (uuid.version() != 4 || !uuid.toString().equals(value)) {
                throw InternalApiException.invalidIdempotencyKey();
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw InternalApiException.invalidIdempotencyKey();
        }
    }
}
