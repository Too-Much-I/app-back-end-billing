package web.tosunsaeng.billing.domain.payment.provider;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Explicitly constructed read-only adapter. No retry, Spring runtime registration or provider writes. */
public final class RevenueCatReadClient {
    public interface Transport {
        Response get(URI uri, String bearer);
        default Response get(URI uri, String bearer, Duration timeout) { return get(uri, bearer); }
    }
    public record Response(int status, Map<String, List<String>> headers, byte[] body) {
        public Response { headers = Map.copyOf(headers); body = body.clone(); }
        @Override public byte[] body() { return body.clone(); }
        @Override public String toString() { return "RevenueCatResponse[REDACTED]"; }
    }
    public record Page(List<RevenueCatPurchaseDecoder.Item> items, String nextCursor) {
        public Page { items = List.copyOf(items); }
        @Override public String toString() { return "RevenueCatPage[REDACTED]"; }
    }
    private final String project, environment, secret;
    private final Transport transport;
    private final RevenueCatPurchaseDecoder decoder;
    private final Clock clock;
    public RevenueCatReadClient(String project, String environment, String secret, Transport transport, RevenueCatPurchaseDecoder decoder, Clock clock) {
        RevenueCatPurchaseDecoder.text(project);
        if (!Set.of("SANDBOX", "PRODUCTION").contains(environment) || secret == null || secret.isBlank() || secret.length() > 4096
                || secret.chars().anyMatch(c -> c <= 32 || c > 126)) throw RevenueCatFailure.contract();
        this.project = project; this.environment = environment.toLowerCase(Locale.ROOT); this.secret = secret;
        if (!decoder.matches(project, environment)) throw RevenueCatFailure.contract();
        this.transport = Objects.requireNonNull(transport); this.decoder = Objects.requireNonNull(decoder); this.clock = Objects.requireNonNull(clock);
    }
    public Page purchases(String reference, String cursor) {
        return purchases(reference, cursor, Duration.ofSeconds(5));
    }
    public Page purchases(String reference, String cursor, Duration timeout) {
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(5)) > 0)
            throw RevenueCatFailure.contract();
        long started = System.nanoTime();
        RevenueCatPurchaseDecoder.owner(reference); if (cursor != null) RevenueCatPurchaseDecoder.text(cursor);
        String requestedEnvironment = environment;
        if (decoder.includesSandbox() && cursor != null && cursor.startsWith("S:")) {
            requestedEnvironment = "sandbox";
            cursor = cursor.substring(2);
            if (cursor.isEmpty()) cursor = null;
        } else if (decoder.includesSandbox() && cursor != null) {
            if (!cursor.startsWith("P:")) throw RevenueCatFailure.contract();
            cursor = cursor.substring(2); RevenueCatPurchaseDecoder.text(cursor);
        }
        String path = customerPath(reference);
        URI uri = URI.create("https://api.revenuecat.com" + path + "?environment=" + requestedEnvironment + "&limit=100"
                + (cursor == null ? "" : "&starting_after=" + encode(cursor)));
        byte[] bytes = response(uri, timeout);
        var page = decoder.page(bytes, reference);
        if (decoder.needsAliases(bytes, reference, true)) {
            var aliases = aliases(reference, remaining(timeout, started));
            page = decoder.page(bytes, reference, aliases);
        }
        String next = nextCursor(page.nextPage(), path, requestedEnvironment);
        if (next != null && next.equals(cursor)) throw RevenueCatFailure.contract();
        if (decoder.includesSandbox()) {
            next = next == null ? ("production".equals(requestedEnvironment) ? "S:" : null)
                    : ("production".equals(requestedEnvironment) ? "P:" : "S:") + next;
            if (next != null) RevenueCatPurchaseDecoder.text(next);
        }
        return new Page(page.items(), next);
    }
    public RevenueCatPurchaseDecoder.Item purchase(String reference, String purchaseId) {
        RevenueCatPurchaseDecoder.owner(reference); RevenueCatPurchaseDecoder.text(purchaseId);
        long started = System.nanoTime();
        byte[] bytes = response(URI.create("https://api.revenuecat.com/v2/projects/" + encode(project) + "/purchases/" + encode(purchaseId)));
        var item = decoder.purchase(bytes, reference);
        if (decoder.needsAliases(bytes, reference, false))
            item = decoder.purchase(bytes, reference, aliases(reference, remaining(Duration.ofSeconds(5), started)));
        if (!purchaseId.equals(item.reference())) throw RevenueCatFailure.contract(); return item;
    }
    private Set<String> aliases(String reference, Duration timeout) {
        URI uri = URI.create("https://api.revenuecat.com/v2/projects/" + encode(project)
                + "/customers/" + encode(reference) + "/aliases?limit=100");
        return decoder.aliases(response(uri, timeout), reference);
    }
    private static Duration remaining(Duration timeout, long started) {
        Duration remaining = timeout.minusNanos(System.nanoTime() - started);
        if (remaining.isNegative() || remaining.isZero()) throw new RevenueCatFailure(RevenueCatFailure.Kind.RETRY, null);
        return remaining;
    }
    private byte[] response(URI uri) {
        return response(uri, Duration.ofSeconds(5));
    }
    private byte[] response(URI uri, Duration timeout) {
        Response r;
        try { r = transport.get(uri, secret, timeout); }
        catch (RevenueCatFailure e) { throw e; }
        catch (RuntimeException e) { throw new RevenueCatFailure(RevenueCatFailure.Kind.RETRY, null); }
        int s = r.status();
        if (s == 401 || s == 403) throw new RevenueCatFailure(RevenueCatFailure.Kind.AUTH, null);
        if (s == 404) throw new RevenueCatFailure(RevenueCatFailure.Kind.NOT_FOUND, null);
        if (s == 429 || s == 503) throw new RevenueCatFailure(RevenueCatFailure.Kind.RETRY, retryAt(r.headers()));
        if (s == 423 || s >= 500 && s <= 599) throw new RevenueCatFailure(RevenueCatFailure.Kind.RETRY, null);
        if (s != 200) throw RevenueCatFailure.contract();
        List<String> types = header(r.headers(), "Content-Type");
        if (types.size() != 1 || !types.getFirst().split(";", 2)[0].trim().equalsIgnoreCase("application/json")) throw RevenueCatFailure.contract();
        return r.body();
    }
    private Instant retryAt(Map<String, List<String>> headers) {
        List<String> values = header(headers, "Retry-After"); if (values.isEmpty()) return null;
        try {
            if (values.size() != 1) throw new IllegalArgumentException();
            String v = values.getFirst(); Instant now = clock.instant();
            Instant at = v.matches("[0-9]{1,10}") ? now.plusSeconds(Long.parseLong(v)) : ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            if (at.isAfter(now.plus(Duration.ofHours(24)))) throw new IllegalArgumentException();
            return at.isBefore(now) ? now : at;
        } catch (RuntimeException invalid) { throw new RevenueCatFailure(RevenueCatFailure.Kind.REVIEW, null); }
    }
    private static List<String> header(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream().filter(e -> name.equalsIgnoreCase(e.getKey())).flatMap(e -> e.getValue().stream()).toList();
    }
    private String customerPath(String reference) { return "/v2/projects/" + encode(project) + "/customers/" + encode(reference) + "/purchases"; }
    private String nextCursor(String next, String path, String requestedEnvironment) {
        if (next == null) return null;
        try {
            URI u = URI.create(next);
            if (u.getFragment() != null || u.getUserInfo() != null || !path.equals(u.getRawPath())
                    || u.isAbsolute() && (!"https".equals(u.getScheme()) || !"api.revenuecat.com".equals(u.getHost()) || u.getPort() != -1)
                    || !u.isAbsolute() && u.getRawAuthority() != null || u.getRawQuery() == null) throw RevenueCatFailure.contract();
            Map<String, String> params = new HashMap<>();
            for (String pair : u.getRawQuery().split("&", -1)) {
                String[] kv = pair.split("=", 2); if (kv.length != 2) throw RevenueCatFailure.contract();
                String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8), v = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                if (!Set.of("starting_after", "environment", "limit").contains(k) || params.put(k, v) != null) throw RevenueCatFailure.contract();
            }
            if (params.containsKey("environment") && !requestedEnvironment.equals(params.get("environment"))
                    || params.containsKey("limit") && !"100".equals(params.get("limit"))) throw RevenueCatFailure.contract();
            String cursor = params.get("starting_after"); RevenueCatPurchaseDecoder.text(cursor); return cursor;
        } catch (RuntimeException invalid) { throw RevenueCatFailure.contract(); }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace(".", "%2E"); }
    @Override public String toString() { return "RevenueCatReadClient[REDACTED]"; }
}
