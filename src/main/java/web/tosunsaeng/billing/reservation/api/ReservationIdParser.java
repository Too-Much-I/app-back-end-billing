package web.tosunsaeng.billing.reservation.api;

import java.util.UUID;

import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.global.api.InternalApiException;

@Component
public class ReservationIdParser {

    public String parse(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            if (uuid.version() != 4 || !uuid.toString().equals(value)) {
                throw InternalApiException.invalidRequest();
            }
            return value;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw InternalApiException.invalidRequest();
        }
    }
}
