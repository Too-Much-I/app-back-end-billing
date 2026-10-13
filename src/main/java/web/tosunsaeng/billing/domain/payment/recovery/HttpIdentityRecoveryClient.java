package web.tosunsaeng.billing.domain.payment.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.*;
import java.util.*;
import static web.tosunsaeng.billing.domain.payment.recovery.RecoveryPayloadDecoder.*;

/** Exact recovery-r1 transport. Never logs or retains credentials, raw responses or request identifiers. */
public class HttpIdentityRecoveryClient implements IdentityWithdrawalRecoveryClient, AutoCloseable {
    private static final String PREFIX = "/internal/v1/billing/withdrawals";
    private final URI endpoint;
    private final Transport transport;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final RecoveryPayloadDecoder decoder;
    private final ObjectMapper json = new ObjectMapper();
    private final java.util.function.Supplier<String> bearer;
    interface Transport { Response exchange(Request signed, byte[] body); }
    record Request(URI getUri, Map<String, List<String>> headers) {
        Request {
            var copy = new LinkedHashMap<String, List<String>>();
            headers.forEach((key, values) -> copy.put(key, List.copyOf(values)));
            headers = Collections.unmodifiableMap(copy);
        }
        Optional<String> firstMatchingHeader(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream()).findFirst();
        }
        @Override public String toString() { return "RecoveryHttpRequest[REDACTED]"; }
    }
    record Response(int status, Map<String, List<String>> headers, byte[] body) {
        List<String> header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream()).toList();
        }
        @Override public String toString() { return "RecoveryHttpResponse[REDACTED]"; }
    }
    HttpIdentityRecoveryClient(URI endpoint, Transport transport, Clock clock, MeterRegistry metrics,
                               java.util.function.Supplier<String> bearer) {
        this.endpoint = endpoint; this.transport = transport;
        this.clock = clock; this.metrics = metrics; this.decoder = new RecoveryPayloadDecoder(clock); this.bearer = Objects.requireNonNull(bearer);
    }
    @Override public SnapshotStatus createSnapshot(String streamId, String operationId) {
        uuid(streamId); uuid(operationId);
        Response response = exchange("/snapshots", Map.of("streamId", streamId), operationId, streamId, 0, null, true);
        return status(response, null, streamId);
    }
    @Override public SnapshotStatus snapshotStatus(String snapshotId, String streamId) {
        uuid(snapshotId); uuid(streamId);
        return status(exchange("/snapshots/status", Map.of("snapshotId", snapshotId), null, streamId, 0, null, false), snapshotId, streamId);
    }
    @Override public SnapshotPage snapshotPage(String snapshotId, String cursor) {
        uuid(snapshotId); cursor(cursor);
        var body = new LinkedHashMap<String, Object>(); body.put("snapshotId", snapshotId); body.put("cursor", cursor);
        return decoder.snapshotPage(exchange("/snapshots/page", body, null, null, 0, null, false).body());
    }
    @Override public FeedPage feedPage(String streamId, long afterSequence, String scanCursor, Long expectedTarget) {
        uuid(streamId); cursor(scanCursor);
        if (afterSequence < 0 || (expectedTarget != null && expectedTarget < afterSequence)
                || ((scanCursor == null) != (expectedTarget == null))) throw RecoveryRemoteException.contract();
        var body = new LinkedHashMap<String, Object>();
        body.put("streamId", streamId); body.put("afterSequence", Long.toString(afterSequence)); body.put("scanCursor", scanCursor);
        return decoder.feedPage(exchange("/feed/page", body, null, streamId, afterSequence, expectedTarget, false).body(),
                streamId, afterSequence, expectedTarget);
    }
    private SnapshotStatus status(Response response, String snapshotId, String streamId) {
        SnapshotStatus result = decoder.snapshotStatus(response.body(), snapshotId, streamId);
        if (response.status() == 202 && !result.status().equals("BUILDING")) throw RecoveryRemoteException.contract();
        return new SnapshotStatus(result.snapshotId(), result.status(), result.manifest(), result.failureCode(),
                result.status().equals("BUILDING") ? retryAt(response) : null);
    }
    private Response exchange(String path, Map<String, ?> payload, String operationId, String stream, long after, Long target, boolean create) {
        byte[] body;
        Request signed;
        try {
            body = json.writeValueAsBytes(payload);
            if (body.length > MAX_BYTES) throw RecoveryRemoteException.contract();
            var headers = new LinkedHashMap<String, List<String>>();
            headers.put("Content-Type", List.of("application/json"));
            headers.put("Accept", List.of("application/json"));
            headers.put("Host", List.of(endpoint.getRawAuthority()));
            if (operationId != null) headers.put("Idempotency-Key", List.of(operationId));
            headers.put("Authorization", List.of("Bearer " + Objects.requireNonNull(bearer.get())));
            signed = new Request(endpoint.resolve(PREFIX + path), headers);
        } catch (RecoveryRemoteException failure) { throw failure; }
        catch (Exception failure) { throw RecoveryRemoteException.auth(); }
        Response response = transport.exchange(signed, body);
        if (response.body() == null || response.body().length > MAX_BYTES) throw RecoveryRemoteException.contract();
        int status = response.status();
        if (status == 401 || status == 403) throw RecoveryRemoteException.auth();
        Instant retry = status >= 400 ? retryAt(response) : null;
        if (status == 408 || status == 425 || status == 429) throw RecoveryRemoteException.transientFailure(retry);
        if (!response.header("Content-Encoding").isEmpty()) throw RecoveryRemoteException.contract();
        if (status == 200 || (create && status == 202)) return response;
        if (status >= 300 && status < 400) throw RecoveryRemoteException.contract();
        RemoteError error;
        try { error = decoder.error(response.body(), status, stream, after, target); }
        catch (RecoveryContractException invalid) {
            if (status >= 500 && status <= 599) throw RecoveryRemoteException.transientFailure(retry);
            throw RecoveryRemoteException.contract();
        }
        throw RecoveryRemoteException.application(error, retry);
    }
    private Instant retryAt(Response response) {
        var retry = RecoveryRetryPolicy.retryAfter(response.header("Retry-After"), clock.instant());
        if (retry.invalid()) metrics.counter("billing.recovery.http.retry_after", "outcome", "INVALID").increment();
        if (retry.overOneDay()) metrics.counter("billing.recovery.http.retry_after", "outcome", "OVER_ONE_DAY").increment();
        return retry.notBefore();
    }
    private static void uuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equals(value) || id.version() != 4 || id.variant() != 2) throw new IllegalArgumentException();
        } catch (RuntimeException failure) { throw RecoveryRemoteException.contract(); }
    }
    private static void cursor(String value) {
        if (value != null && !value.matches("[!-~]{1,2048}")) throw RecoveryRemoteException.contract();
    }
    @Override public void close() {
        try { if (transport instanceof AutoCloseable closeable) closeable.close(); }
        catch (Exception ignored) { /* Do not expose transport diagnostics. */ }
    }
}
