package web.tosunsaeng.billing.domain.payment.api;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import web.tosunsaeng.billing.domain.payment.domain.PaymentWithdrawal;
import web.tosunsaeng.billing.domain.payment.exception.PaymentWithdrawalException;
import web.tosunsaeng.billing.global.exception.InternalApiException;

@Component
public class PaymentWithdrawalDecoder {
    public static final int MAX_BYTES = 16 * 1024;
    private static final Set<String> FIELDS = Set.of("eventId", "schemaVersion", "userId", "withdrawnAt");
    private final JsonFactory factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final ObjectMapper mapper = new ObjectMapper(factory);
    private final Clock clock;
    public PaymentWithdrawalDecoder(Clock clock) { this.clock = clock; }

    public PaymentWithdrawal decode(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > MAX_BYTES) throw InternalApiException.invalidRequest();
        try (var parser = factory.createParser(payload)) {
            JsonNode root = mapper.readTree(parser);
            if (root == null || !root.isObject() || parser.nextToken() != null) throw InternalApiException.invalidRequest();
            var names = new HashSet<String>(); root.fieldNames().forEachRemaining(names::add);
            if (!FIELDS.equals(names)) throw InternalApiException.invalidRequest();
            JsonNode schema = root.get("schemaVersion");
            if (!schema.isIntegralNumber() || !schema.canConvertToInt()) throw InternalApiException.invalidRequest();
            if (schema.intValue() != 1) throw PaymentWithdrawalException.unsupported();
            String eventId = uuid(root, "eventId"), userId = uuid(root, "userId");
            String timestamp = text(root, "withdrawnAt");
            if (!timestamp.endsWith("Z")) throw InternalApiException.invalidRequest();
            Instant withdrawnAt = Instant.parse(timestamp);
            if (withdrawnAt.isAfter(clock.instant().plusSeconds(60))) throw InternalApiException.invalidRequest();
            // Ensure all business timestamps can be persisted, without dropping legacy nanoseconds in the digest.
            Date.from(withdrawnAt); Date.from(withdrawnAt.plus(Duration.ofDays(15)));
            var bytes = new ByteArrayOutputStream();
            try (var json = factory.createGenerator(bytes)) {
                json.writeStartObject();
                json.writeStringField("eventId", eventId);
                json.writeNumberField("schemaVersion", 1);
                json.writeStringField("userId", userId);
                json.writeStringField("withdrawnAt", withdrawnAt.toString());
                json.writeEndObject();
            }
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
            return new PaymentWithdrawal(eventId, userId, withdrawnAt, digest);
        } catch (InternalApiException error) { throw error; }
        catch (Exception error) { throw InternalApiException.invalidRequest(); }
    }
    private static String text(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (!value.isTextual() || value.textValue().isEmpty()) throw InternalApiException.invalidRequest();
        return value.textValue();
    }
    private static String uuid(JsonNode root, String name) {
        String value = text(root, name);
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) throw InternalApiException.invalidRequest();
        return value;
    }
}
