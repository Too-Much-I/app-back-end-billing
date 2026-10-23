package web.tosunsaeng.billing.domain.payment.provider;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.*;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.payment.purchase.*;

/** Only authenticated raw UTF-8 bytes enter this decoder. */
public final class RevenueCatWebhookDecoder {
    public static final int MAX_BYTES = 256 * 1024;
    private static final List<String> FIELDS = List.of("id", "type", "environment", "app_id", "store", "transaction_id",
            "product_id", "purchased_at_ms", "event_timestamp_ms", "app_user_id", "original_app_user_id", "aliases",
            "quantity", "is_family_share", "currency", "price_in_purchased_currency", "cancel_reason", "transferred_from", "transferred_to");
    private static final Set<String> REVIEW_TYPES = Set.of("CANCELLATION", "REFUND_REVERSED", "TRANSFER", "SUBSCRIBER_ALIAS",
            "TEMPORARY_ENTITLEMENT_GRANT", "INITIAL_PURCHASE", "RENEWAL", "EXPIRATION", "PRODUCT_CHANGE", "BILLING_ISSUE", "UNCANCELLATION");
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(16384).maxNumberLength(64).build()).build())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    public record App(PurchaseObservation.Store store, Set<String> products) {
        public App { Objects.requireNonNull(store); products = Set.copyOf(products); products.forEach(RevenueCatPurchaseDecoder::text); }
    }
    private final String environment, project;
    private final Map<String, App> apps;
    private final PurchaseLookupKey lookup;
    private final Clock clock;
    private final StoreEnvironmentPolicy environments;
    public RevenueCatWebhookDecoder(String environment, String project, Map<String, App> apps, PurchaseLookupKey lookup, Clock clock) {
        this(environment, project, apps, lookup, clock, false);
    }
    public RevenueCatWebhookDecoder(String environment, String project, Map<String, App> apps, PurchaseLookupKey lookup, Clock clock,
                                    boolean sandboxPurchasesEnabled) {
        if (!Set.of("SANDBOX", "PRODUCTION").contains(environment) || apps.isEmpty()) throw new IllegalArgumentException("Invalid webhook mapping");
        RevenueCatPurchaseDecoder.text(project); apps.keySet().forEach(RevenueCatPurchaseDecoder::text);
        this.environment = environment; this.project = project; this.apps = Map.copyOf(apps); this.lookup = lookup; this.clock = clock;
        environments = new StoreEnvironmentPolicy(environment, sandboxPurchasesEnabled);
    }
    public WebhookReceipt decode(byte[] bytes) {
        JsonNode root = json(bytes);
        if (!"1.0".equals(text(root, "api_version", true))) throw unsupported();
        JsonNode event = root.get("event"); if (event == null || !event.isObject()) throw malformed();
        String id = text(event, "id", true), type = text(event, "type", true);
        String env = text(event, "environment", false), appId = text(event, "app_id", false);
        if (env != null && !environments.accepts(env) || appId != null && !apps.containsKey(appId)) throw unsupported();
        String receiptEnvironment = env == null ? environment : env;
        String key = lookup.event(receiptEnvironment, project, id), digest = digest(event);
        if ("TEST".equals(type) || !"NON_RENEWING_PURCHASE".equals(type) && !REVIEW_TYPES.contains(type))
            return new WebhookReceipt(receiptEnvironment, key, digest, lookup.version(), null, null, WebhookReceipt.Disposition.IGNORED, "IGNORED_UNSUPPORTED");
        String current = text(event, "app_user_id", false), original = text(event, "original_app_user_id", false);
        boolean anonymousOriginal = canonicalOwner(current) && RevenueCatPurchaseDecoder.anonymous(original);
        String owner = canonicalOwner(current) && (Objects.equals(current, original) || anonymousOriginal) ? current : null;
        if (!"NON_RENEWING_PURCHASE".equals(type)) {
            // Cancellation is only a lookup hint; final API evidence must still revoke access.
            ReviewPurchaseCandidate candidate = "CANCELLATION".equals(type)
                    ? cancellationCandidate(event, env, appId, owner) : null;
            return new WebhookReceipt(receiptEnvironment, key, digest, lookup.version(), null, owner,
                    WebhookReceipt.Disposition.REVIEW, "EVENT_REQUIRES_LOOKUP", candidate);
        }
        String store = text(event, "store", false), product = text(event, "product_id", false), tx = text(event, "transaction_id", false);
        Long purchased = number(event, "purchased_at_ms"), eventTime = number(event, "event_timestamp_ms"), quantity = number(event, "quantity");
        JsonNode family = event.get("is_family_share");
        if (family != null && !family.isNull() && !family.isBoolean()) throw malformed();
        JsonNode aliases = event.get("aliases"); boolean aliasSafe = true;
        if (aliases != null && !aliases.isNull()) {
            if (!aliases.isArray()) throw malformed();
            for (JsonNode alias : aliases) { if (!alias.isTextual()) throw malformed(); if (!Objects.equals(owner, alias.textValue())) aliasSafe = false; }
        }
        App app = appId == null ? null : apps.get(appId);
        String reason = owner == null || !aliasSafe || anonymousOriginal ? "OWNER_UNRESOLVED"
                : app == null || env == null || store == null || product == null || tx == null || purchased == null || eventTime == null ? "PURCHASE_FACTS_INCOMPLETE"
                : !app.store().name().equals(store) || !app.products().contains(product) ? "PRODUCT_UNMAPPED"
                : quantity == null ? "QUANTITY_REQUIRES_LOOKUP"
                : quantity != 1L ? "QUANTITY_UNSUPPORTED"
                : family == null || !family.isBoolean() || family.booleanValue() ? "OWNERSHIP_UNSUPPORTED"
                : purchased < 0 || eventTime < 0 || Instant.ofEpochMilli(purchased).isAfter(clock.instant())
                    || Instant.ofEpochMilli(eventTime).isAfter(clock.instant()) ? "PURCHASE_TIME_INVALID" : null;
        if (reason != null) {
            // Only incomplete purchase evidence is matchable; explicit unsupported quantity/family sharing,
            // owner mismatch, reversal/transfer events and missing facts remain manual review.
            ReviewPurchaseCandidate candidate = null;
            if (Set.of("QUANTITY_REQUIRES_LOOKUP", "OWNER_UNRESOLVED").contains(reason)
                    && owner != null && safeReviewAliases(aliases, owner) && app != null && env != null && app.store().name().equals(store)
                    && product != null && app.products().contains(product) && tx != null && purchased != null && purchased >= 0
                    && !Instant.ofEpochMilli(purchased).isAfter(clock.instant()) && (quantity == null || quantity == 1L)
                    && family != null && family.isBoolean() && !family.booleanValue())
                candidate = new ReviewPurchaseCandidate(env, app.store(), appId,
                        lookup.transaction(env, store, appId, tx), lookup.version(), owner, product, Instant.ofEpochMilli(purchased));
            return new WebhookReceipt(receiptEnvironment, key, digest, lookup.version(), null, owner,
                    WebhookReceipt.Disposition.REVIEW, reason, candidate);
        }
        PurchaseObservation.Money money = null;
        JsonNode amount = event.get("price_in_purchased_currency"), currency = event.get("currency");
        boolean hasAmount = amount != null && !amount.isNull(), hasCurrency = currency != null && !currency.isNull();
        if (hasCurrency && (!currency.isTextual() || !currency.textValue().matches("[A-Z]{3}")))
            return receipt(receiptEnvironment, key, digest, null, owner, WebhookReceipt.Disposition.REVIEW, "MONEY_UNRESOLVED");
        if (hasAmount) {
            if (!hasCurrency || !amount.isNumber())
                return receipt(receiptEnvironment, key, digest, null, owner, WebhookReceipt.Disposition.REVIEW, "MONEY_UNRESOLVED");
            try { money = new PurchaseObservation.Money(amount.decimalValue(), currency.textValue()); }
            catch (IllegalArgumentException invalid) { return receipt(receiptEnvironment, key, digest, null, owner, WebhookReceipt.Disposition.REVIEW, "MONEY_UNRESOLVED"); }
        }
        var observation = new PurchaseObservation(receiptEnvironment, app.store(), appId, lookup.transaction(receiptEnvironment, store, appId, tx),
                lookup.version(), key, PurchaseObservation.Kind.PURCHASE, owner, product, Instant.ofEpochMilli(purchased), money);
        return receipt(receiptEnvironment, key, digest, observation, owner, WebhookReceipt.Disposition.PURCHASE, "VERIFIED_CANDIDATE");
    }
    private ReviewPurchaseCandidate cancellationCandidate(JsonNode event, String env, String appId, String owner) {
        String store = text(event, "store", false), product = text(event, "product_id", false), tx = text(event, "transaction_id", false);
        Long purchased = number(event, "purchased_at_ms"), eventTime = number(event, "event_timestamp_ms"), quantity = number(event, "quantity");
        JsonNode aliases = event.get("aliases"), family = event.get("is_family_share");
        App app = apps.get(appId == null ? "" : appId);
        if (owner == null || env == null || app == null || !app.store().name().equals(store)
                || product == null || !app.products().contains(product) || tx == null || purchased == null || purchased < 0
                || eventTime == null || eventTime < 0 || eventTime > clock.millis() || purchased > clock.millis()
                || quantity != null && quantity != 1L
                || family == null || !family.isBoolean() || family.booleanValue()) return null;
        if (aliases != null && !aliases.isNull()) {
            if (!aliases.isArray()) return null;
            for (JsonNode alias : aliases) if (!alias.isTextual()) return null;
            if (!safeReviewAliases(aliases, owner)) return null;
        }
        return new ReviewPurchaseCandidate(env, app.store(), appId, lookup.transaction(env, store, appId, tx),
                lookup.version(), owner, product, Instant.ofEpochMilli(purchased));
    }
    private static boolean safeReviewAliases(JsonNode aliases, String owner) {
        if (aliases == null || aliases.isNull()) return true;
        for (JsonNode alias : aliases)
            if (!owner.equals(alias.textValue()) && !RevenueCatPurchaseDecoder.anonymous(alias.textValue())) return false;
        return true;
    }
    private WebhookReceipt receipt(String storeEnvironment, String key, String digest, PurchaseObservation o, String owner, WebhookReceipt.Disposition d, String reason) {
        return new WebhookReceipt(storeEnvironment, key, digest, lookup.version(), o, owner, d, reason);
    }
    private JsonNode json(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw malformed();
        if (bytes.length > MAX_BYTES) throw new PaymentException(413, "PAYLOAD_TOO_LARGE");
        try {
            String utf8 = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (utf8.startsWith("\uFEFF")) throw malformed();
            try (JsonParser parser = mapper.createParser(utf8)) {
                JsonNode root = mapper.readTree(parser); if (root == null || !root.isObject() || parser.nextToken() != null) throw malformed(); return root;
            }
        } catch (Exception e) { throw malformed(); }
    }
    private String digest(JsonNode event) {
        ObjectNode selected = mapper.createObjectNode(); for (String field : FIELDS) if (event.has(field)) selected.set(field, event.get(field));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonical(selected)))); }
        catch (Exception e) { throw malformed(); }
    }
    private JsonNode canonical(JsonNode n) {
        if (n.isObject()) { ObjectNode out = mapper.createObjectNode(); TreeSet<String> keys = new TreeSet<>(); n.fieldNames().forEachRemaining(keys::add); for (String key : keys) out.set(key, canonical(n.get(key))); return out; }
        if (n.isArray()) { var out = mapper.createArrayNode(); n.forEach(v -> out.add(canonical(v))); return out; }
        return n;
    }
    private static String text(JsonNode n, String field, boolean required) {
        JsonNode v = n.get(field); if (v == null || v.isNull()) { if (required) throw malformed(); return null; }
        if (!v.isTextual() || v.textValue().isBlank() || v.textValue().length() > 255 || v.textValue().codePoints().anyMatch(Character::isISOControl)) throw malformed();
        return v.textValue();
    }
    private static Long number(JsonNode n, String field) {
        JsonNode v = n.get(field); if (v == null || v.isNull()) return null;
        if (!v.isIntegralNumber() || !v.canConvertToLong()) throw malformed(); return v.longValue();
    }
    private static boolean canonicalOwner(String s) {
        if (s == null) return false;
        try { RevenueCatPurchaseDecoder.owner(s); return true; } catch (RuntimeException e) { return false; }
    }
    private static PaymentException malformed() { return new PaymentException(400, "INVALID_REQUEST"); }
    private static PaymentException unsupported() { return new PaymentException(422, "UNSUPPORTED_CONTRACT"); }
}
