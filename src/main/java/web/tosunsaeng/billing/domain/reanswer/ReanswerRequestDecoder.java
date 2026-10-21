package web.tosunsaeng.billing.domain.reanswer;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.nio.*;
import java.nio.charset.*;
import java.io.IOException;
import java.util.UUID;
import web.tosunsaeng.billing.global.exception.InternalApiException;

public final class ReanswerRequestDecoder {
    public static final int MAX_BYTES = 16384;
    private final JsonFactory factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final ObjectMapper json = new ObjectMapper(factory);
    public String decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw InternalApiException.invalidRequest();
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            try (JsonParser parser = factory.createParser(text)) {
                JsonNode root = json.readTree(parser);
                if (root == null || !root.isObject() || root.size() != 1 || !root.has("userId")
                        || !root.get("userId").isTextual() || parser.nextToken() != null) throw InternalApiException.invalidRequest();
                String user = root.get("userId").textValue();
                if (!UUID.fromString(user).toString().equals(user)) throw InternalApiException.invalidRequest();
                return user;
            }
        } catch (IOException | IllegalArgumentException e) { throw InternalApiException.invalidRequest(); }
    }
}
