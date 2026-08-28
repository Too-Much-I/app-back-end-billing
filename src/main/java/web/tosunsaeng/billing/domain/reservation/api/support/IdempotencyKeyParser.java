package web.tosunsaeng.billing.domain.reservation.api.support;

import java.util.UUID;

import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.domain.reservation.exception.ReservationException;

@Component
public class IdempotencyKeyParser {

    public String parse(String value) {
        if (value == null || value.isEmpty()) {
            throw ReservationException.invalidIdempotencyKey();
        }
        try {
            UUID uuid = UUID.fromString(value);
            if (uuid.version() != 4 || !uuid.toString().equals(value)) {
                throw ReservationException.invalidIdempotencyKey();
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw ReservationException.invalidIdempotencyKey();
        }
    }
}
