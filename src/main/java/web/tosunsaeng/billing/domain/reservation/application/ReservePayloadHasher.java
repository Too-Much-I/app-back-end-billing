package web.tosunsaeng.billing.domain.reservation.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Component;

import web.tosunsaeng.billing.domain.reservation.dto.request.ReserveRequest;

@Component
public class ReservePayloadHasher {

    public String hash(ReserveRequest request) {
        String canonical = "apiVersion=v1\n"
                + "callerService=LEARNING_CORE\n"
                + "commandType=RESERVE\n"
                + "userId=" + request.userId() + "\n"
                + "sessionId=" + request.sessionId() + "\n"
                + "mockExamId=" + request.mockExamId();
        if (request.continuationReason() != null) {
            canonical += "\ncontinuationReason=" + request.continuationReason()
                    + "\ncontinuationId=" + request.continuationId()
                    + "\nexpectedAttemptGroupId=" + request.expectedAttemptGroupId();
        }
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
            throw new IllegalStateException("Reserve payload hash could not be created.", exception);
        }
    }

}
