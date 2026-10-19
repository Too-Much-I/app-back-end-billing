package web.tosunsaeng.billing.domain.payment.provider;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import web.tosunsaeng.billing.domain.payment.purchase.*;

/** Decode only after authenticated HTTPS retrieval. Not a client-input or webhook decoder. */
public final class RevenueCatPurchaseDecoder {
    public static final int MAX_BYTES = 1024 * 1024;
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(16384).maxNumberLength(64).build()).build());
    private final String project, environment;
    private final PurchaseLookupKey lookup;
    private final Map<String, Product> products;
    private final Clock clock;
    private final StoreEnvironmentPolicy environments;
    public record Product(String appId, PurchaseObservation.Store store, String storeProductId) {
        public Product { text(appId); text(storeProductId); Objects.requireNonNull(store); }
    }
    public record Item(String reference, PurchaseObservation observation, String reviewReason) {
        @Override public String toString() { return "RevenueCatItem[REDACTED]"; }
    }
    public record Page(List<Item> items, String nextPage) {
        public Page { items = List.copyOf(items); }
        @Override public String toString() { return "RevenueCatPage[REDACTED]"; }
    }
    public RevenueCatPurchaseDecoder(String project, String environment, PurchaseLookupKey lookup, Map<String, Product> products, Clock clock) {
        this(project, environment, lookup, products, clock, false);
    }
    public RevenueCatPurchaseDecoder(String project, String environment, PurchaseLookupKey lookup, Map<String, Product> products, Clock clock,
                                     boolean sandboxPurchasesEnabled) {
        text(project); if (!Set.of("SANDBOX", "PRODUCTION").contains(environment)) throw RevenueCatFailure.contract();
        this.project = project; this.environment = environment; this.lookup = Objects.requireNonNull(lookup);
        this.products = Map.copyOf(products); this.products.keySet().forEach(RevenueCatPurchaseDecoder::text); this.clock = Objects.requireNonNull(clock);
        environments = new StoreEnvironmentPolicy(environment, sandboxPurchasesEnabled);
    }
    public Page page(byte[] bytes, String ownerRef) {
        owner(ownerRef); JsonNode root = json(bytes);
        if (!"list".equals(string(root, "object")) || !root.path("items").isArray() || root.path("items").size() > 100 || !root.has("next_page"))
            throw RevenueCatFailure.contract();
        String next = root.get("next_page").isNull() ? null : string(root, "next_page", 4096);
        List<Item> items = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (JsonNode row : root.get("items")) {
            Item item = item(row, ownerRef); if (!ids.add(item.reference())) throw RevenueCatFailure.contract(); items.add(item);
        }
        return new Page(items, next);
    }
    boolean matches(String project, String environment) { return this.project.equals(project) && this.environment.equals(environment); }
    boolean includesSandbox() { return environments.sandboxPurchasesEnabled(); }
    public Item purchase(byte[] bytes, String ownerRef) { owner(ownerRef); return item(json(bytes), ownerRef); }
    private Item item(JsonNode row, String ownerRef) {
        if (!row.isObject() || !"purchase".equals(string(row, "object"))) throw RevenueCatFailure.contract();
        String id = string(row, "id"), customer = string(row, "customer_id", 1500), original = string(row, "original_customer_id", 1500);
        String productId = string(row, "product_id"), storeId = string(row, "store_purchase_identifier"), env = string(row, "environment");
        String store = string(row, "store"), ownership = string(row, "ownership"), status = string(row, "status");
        long quantity = integer(row, "quantity"), millis = integer(row, "purchased_at");
        if (millis < 0) throw RevenueCatFailure.contract();
        Instant purchased = Instant.ofEpochMilli(millis);
        String storeEnvironment = switch (env) { case "production" -> "PRODUCTION"; case "sandbox" -> "SANDBOX"; default -> ""; };
        if (!environments.accepts(storeEnvironment)) return review(id, "ENVIRONMENT_MISMATCH");
        if (!ownerRef.equals(customer) || !ownerRef.equals(original)) return review(id, "OWNER_UNRESOLVED");
        if (!"purchased".equals(ownership)) return review(id, "OWNERSHIP_UNSUPPORTED");
        if (quantity != 1) return review(id, "QUANTITY_UNSUPPORTED");
        if (purchased.isAfter(clock.instant())) return review(id, "PURCHASE_TIME_INVALID");
        Product p = products.get(productId);
        if (p == null) return review(id, "PRODUCT_UNMAPPED");
        if (!p.store().name().toLowerCase(Locale.ROOT).equals(store)) return review(id, "STORE_MISMATCH");
        PurchaseObservation.Kind kind = switch (status) {
            case "owned" -> PurchaseObservation.Kind.PURCHASE;
            case "refunded" -> PurchaseObservation.Kind.REFUND;
            default -> null;
        };
        if (kind == null) return review(id, "STATUS_UNSUPPORTED");
        // API revenue_in_usd is not original store paid currency; leave money unknown.
        String tx = lookup.transaction(storeEnvironment, p.store().name(), p.appId(), storeId);
        String facts = fingerprint(List.of("api-purchase-r1", storeEnvironment, p.store().name(), p.appId(), tx, customer, original,
                productId, p.storeProductId(), Long.toString(millis), "1", ownership, status));
        return new Item(id, new PurchaseObservation(storeEnvironment, p.store(), p.appId(), tx, lookup.version(),
                lookup.apiObservation(storeEnvironment, project, id, facts), kind, ownerRef, p.storeProductId(), purchased, null), null);
    }
    private static Item review(String id, String reason) { return new Item(id, null, reason); }
    private JsonNode json(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw RevenueCatFailure.contract();
        try (JsonParser parser = mapper.createParser(bytes)) {
            JsonNode node = mapper.readTree(parser);
            if (node == null || !node.isObject() || parser.nextToken() != null) throw RevenueCatFailure.contract();
            return node;
        } catch (Exception failure) { throw RevenueCatFailure.contract(); }
    }
    private static String string(JsonNode node, String field) { return string(node, field, 255); }
    private static String string(JsonNode node, String field, int limit) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual()) throw RevenueCatFailure.contract();
        String s = v.textValue();
        if (s.isBlank() || s.length() > limit || s.codePoints().anyMatch(Character::isISOControl)) throw RevenueCatFailure.contract(); return s;
    }
    private static long integer(JsonNode node, String field) {
        JsonNode v = node.get(field); if (v == null || !v.isIntegralNumber() || !v.canConvertToLong()) throw RevenueCatFailure.contract(); return v.longValue();
    }
    static void text(String s) {
        if (s == null || s.isBlank() || s.length() > 255 || s.codePoints().anyMatch(Character::isISOControl)) throw RevenueCatFailure.contract();
    }
    static void owner(String s) {
        try { UUID u = UUID.fromString(s); if (!u.toString().equals(s) || u.version() != 4 || u.variant() != 2) throw RevenueCatFailure.contract(); }
        catch (RuntimeException invalid) { throw RevenueCatFailure.contract(); }
    }
    private static String fingerprint(List<String> values) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String v : values) { byte[] b = v.getBytes(StandardCharsets.UTF_8); md.update(java.nio.ByteBuffer.allocate(4).putInt(b.length).array()); md.update(b); }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) { throw RevenueCatFailure.contract(); }
    }
}
