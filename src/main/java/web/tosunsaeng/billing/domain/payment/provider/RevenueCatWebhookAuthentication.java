package web.tosunsaeng.billing.domain.payment.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;

public final class RevenueCatWebhookAuthentication {
    private static final Pattern SIGNATURE = Pattern.compile("t=(0|[1-9][0-9]{0,18}),v1=([0-9a-f]{64})");
    private final byte[] authorization, secret;
    private final Clock clock;
    public RevenueCatWebhookAuthentication(String authorization, String signingSecret, Clock clock) {
        if (authorization == null || authorization.isBlank() || authorization.length() > 4096
                || authorization.chars().anyMatch(c -> c < 32 || c > 126) || signingSecret == null
                || signingSecret.length() < 32 || signingSecret.length() > 4096) throw new IllegalArgumentException("Invalid webhook credentials");
        this.authorization = authorization.getBytes(StandardCharsets.UTF_8);
        this.secret = signingSecret.getBytes(StandardCharsets.UTF_8); this.clock = Objects.requireNonNull(clock);
    }
    public void authorize(List<String> headers) {
        String value = single(headers);
        if (!MessageDigest.isEqual(authorization, value.getBytes(StandardCharsets.UTF_8))) throw denied();
    }
    public void verify(List<String> headers, byte[] body) {
        String value = single(headers); var matcher = SIGNATURE.matcher(value);
        if (!matcher.matches() || body == null) throw denied();
        try {
            Instant timestamp = Instant.ofEpochSecond(Long.parseLong(matcher.group(1)));
            if (Duration.between(timestamp, clock.instant()).abs().compareTo(Duration.ofSeconds(300)) > 0) throw denied();
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update((matcher.group(1) + ".").getBytes(StandardCharsets.US_ASCII));
            if (!MessageDigest.isEqual(mac.doFinal(body), HexFormat.of().parseHex(matcher.group(2)))) throw denied();
        } catch (Exception invalid) { throw denied(); }
    }
    private static String single(List<String> values) {
        if (values == null || values.size() != 1 || values.getFirst() == null || values.getFirst().length() > 4096) throw denied();
        return values.getFirst();
    }
    private static PaymentException denied() { return new PaymentException(401, "UNAUTHENTICATED"); }
    @Override public String toString() { return "RevenueCatWebhookAuthentication[REDACTED]"; }
}
