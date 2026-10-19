package web.tosunsaeng.billing.domain.payment.sync;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.stereotype.Component;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.payment.purchase.PurchaseObservation.Store;

@Component
public final class PaymentSyncDecoder {
    public static final int MAX_BYTES = 16384;
    private final JsonFactory factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(MAX_BYTES).build()).build();
    private final ObjectMapper mapper = new ObjectMapper(factory);
    public PaymentSyncRequest decode(String operationId, byte[] body) {
        if (body.length > MAX_BYTES) throw new PaymentException(413, "PAYLOAD_TOO_LARGE");
        try (var parser = factory.createParser(body)) {
            JsonNode root = mapper.readTree(parser);
            if (root == null || !root.isObject() || parser.nextToken() != null || root.size() != 2
                    || !root.has("store") || !root.has("transactionId")
                    || !root.get("store").isTextual() || !root.get("transactionId").isTextual()) throw new IllegalArgumentException();
            return new PaymentSyncRequest(operationId, Store.valueOf(root.get("store").textValue()), root.get("transactionId").textValue());
        } catch (Exception invalid) { throw new PaymentException(400, "INVALID_REQUEST"); }
    }
}
