package web.tosunsaeng.billing.domain.payment.purchase;

import java.util.*;
import org.bson.Document;

/** Offline export of exact r8/r9 manifests for a separately approved maintenance process. */
public final class BillingReviewSchemaExport {
    private static Document export(Map<String, PurchaseCoreSchema.Definition> manifest) {
        Document result = new Document();
        manifest.forEach((name, definition) -> result.put(name, new Document("validator", definition.validator())
                .append("indexes", definition.indexes().stream().map(i -> new Document("name", i.name())
                        .append("key", i.keys()).append("unique", i.unique()).append("ttl", i.ttl())).toList())));
        return result;
    }
    public static void main(String[] args) {
        Document manifest = new Document("old", export(MixedStorePurchaseSchema.manifest()))
                .append("target", export(PurchaseReviewResolutionSchema.manifest()));
        String json = manifest.toJson(org.bson.json.JsonWriterSettings.builder()
                .outputMode(org.bson.json.JsonMode.EXTENDED).build());
        if (!manifest.equals(Document.parse(json))) {
            throw new IllegalStateException("Schema export must preserve BSON numeric types");
        }
        System.out.println(json);
    }
}
