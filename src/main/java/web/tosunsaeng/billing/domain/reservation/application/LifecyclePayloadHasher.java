package web.tosunsaeng.billing.domain.reservation.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.domain.reservation.dto.request.CancelRequest;
import web.tosunsaeng.billing.domain.reservation.dto.request.ConfirmRequest;

@Component
public class LifecyclePayloadHasher {

    private static final DateTimeFormatter UTC_MILLIS = new DateTimeFormatterBuilder()
            .appendInstant(3)
            .toFormatter();

    public String hashConfirm(
            String reservationId,
            ConfirmRequest request
    ) {
        return sha256("apiVersion=v1\n"
                + "callerService=LEARNING_CORE\n"
                + "commandType=CONFIRM\n"
                + "reservationId=" + reservationId + "\n"
                + "userId=" + request.userId() + "\n"
                + "sessionId=" + request.sessionId() + "\n"
                + "sessionCommittedAt=" + UTC_MILLIS.format(request.sessionCommittedAt()));
    }

    public String hashCancel(
            String reservationId,
            CancelRequest request
    ) {
        return sha256("apiVersion=v1\n"
                + "callerService=LEARNING_CORE\n"
                + "commandType=CANCEL\n"
                + "reservationId=" + reservationId + "\n"
                + "userId=" + request.userId() + "\n"
                + "reason=" + request.reason().name());
    }

    private static String sha256(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                value.append(Character.forDigit((item >>> 4) & 0x0f, 16));
                value.append(Character.forDigit(item & 0x0f, 16));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Lifecycle payload hash could not be created.", exception);
        }
    }
}
