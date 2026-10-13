package web.tosunsaeng.billing.domain.payment.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import static org.assertj.core.api.Assertions.*;

class HttpIdentityRecoveryClientTest {
    static final String STREAM = "11111111-1111-4111-8111-111111111111";
    static final String SNAPSHOT = "22222222-2222-4222-8222-222222222222";
    static final String OP = "33333333-3333-4333-8333-333333333333";
    static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    static final URI ENDPOINT = URI.create("https://identity.example.invalid");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    final ObjectMapper json = new ObjectMapper();
    final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    HttpIdentityRecoveryClient.Request request;
    byte[] body;
    int calls;
    HttpIdentityRecoveryClient.Response response = reply(202, "{\"snapshotId\":\"" + SNAPSHOT + "\",\"status\":\"BUILDING\"}", Map.of());
    HttpIdentityRecoveryClient client = client(() -> token());
    HttpIdentityRecoveryClient client(java.util.function.Supplier<String> bearer) {
        return new HttpIdentityRecoveryClient(ENDPOINT, (request, body) -> {
            this.request = request; this.body = body; calls++; return response;
        }, CLOCK, metrics, bearer);
    }
    static HttpIdentityRecoveryClient.Response reply(int status, String body, Map<String, List<String>> headers) {
        return new HttpIdentityRecoveryClient.Response(status, headers, body.getBytes(StandardCharsets.UTF_8));
    }
    @Test void exactCreateAndIndependentSignatureOracle() throws Exception {
        assertThat(client.createSnapshot(STREAM, OP).status()).isEqualTo("BUILDING");
        assertThat(json.readTree(body)).isEqualTo(json.readTree("{\"streamId\":\"" + STREAM + "\"}"));
        assertThat(request.getUri().getPath()).isEqualTo("/internal/v1/billing/withdrawals/snapshots");
        assertThat(request.getUri().getQuery()).isNull();
        assertThat(request.firstMatchingHeader("Idempotency-Key")).contains(OP);
        assertThat(request.firstMatchingHeader("x-amz-security-token")).isEmpty();
        assertThat(request.firstMatchingHeader("baggage")).isEmpty();
        assertThat(request.firstMatchingHeader("x-amz-content-sha256")).isEmpty();
        verifySignature(request, body);
        assertThat(calls).isEqualTo(1);
    }
    @Test void resolvesCredentialsForEachDispatch() {
        AtomicInteger resolved = new AtomicInteger();
        client = client(() -> { resolved.incrementAndGet(); return token(); });
        client.createSnapshot(STREAM, OP); client.createSnapshot(STREAM, OP);
        assertThat(resolved).hasValue(2);
    }
    @Test void credentialFailureNeverSendsOrRetainsCause() {
        client = client(() -> { throw new IllegalStateException("fake-sensitive-value"); });
        var failure = catchThrowableOfType(() -> client.createSnapshot(STREAM, OP), RecoveryRemoteException.class);
        assertThat(failure.kind()).isEqualTo(RecoveryRemoteException.Kind.AUTH);
        assertThat(failure.getCause()).isNull(); assertThat(failure).hasMessage("Recovery remote request failed");
        assertThat(calls).isZero();
    }
    @Test void exactStatusAndNullablePageCursor() throws Exception {
        response = reply(200, new String(response.body(), StandardCharsets.UTF_8), Map.of());
        client.snapshotStatus(SNAPSHOT, STREAM);
        assertThat(json.readTree(body).fieldNames()).toIterable().containsExactly("snapshotId");
        assertThat(request.firstMatchingHeader("Idempotency-Key")).isEmpty();
        response = reply(200, "{\"items\":[],\"nextCursor\":null,\"done\":true}", Map.of());
        client.snapshotPage(SNAPSHOT, null);
        assertThat(request.getUri().getPath()).endsWith("/snapshots/page");
        assertThat(json.readTree(body).get("cursor").isNull()).isTrue();
        client.snapshotPage(SNAPSHOT, "opaque-1");
        assertThat(json.readTree(body).get("cursor").asText()).isEqualTo("opaque-1");
    }
    @Test void exactFeedStringSequenceAndLocalOnlyTarget() throws Exception {
        response = reply(200, emptyFeed(Long.MAX_VALUE), Map.of());
        client.feedPage(STREAM, Long.MAX_VALUE, null, null);
        assertThat(json.readTree(body).get("afterSequence").isTextual()).isTrue();
        assertThat(json.readTree(body).get("afterSequence").asText()).isEqualTo(Long.toString(Long.MAX_VALUE));
        assertThat(json.readTree(body).get("scanCursor").isNull()).isTrue();
        client.feedPage(STREAM, Long.MAX_VALUE, "opaque", Long.MAX_VALUE);
        assertThat(json.readTree(body).fieldNames()).toIterable().containsExactly("streamId", "afterSequence", "scanCursor");
        assertThat(request.getUri().getPath()).endsWith("/feed/page");
    }
    static String emptyFeed(long after) {
        return "{\"items\":[],\"scanCursor\":\"opaque\",\"targetThroughSequence\":\"" + after
                + "\",\"nextAfterSequence\":\"" + after + "\",\"scannedThrough\":\"" + after
                + "\",\"done\":true,\"availability\":{\"streamId\":\"" + STREAM
                + "\",\"replayFloor\":\"0\",\"observedThroughSequence\":\"" + after
                + "\",\"checkedFromExclusive\":\"" + after + "\",\"checkedThroughSequence\":\"" + after
                + "\",\"observedAt\":\"2026-10-12T00:00:00Z\"}}";
    }
    @ParameterizedTest @ValueSource(ints = {401, 403})
    void infrastructureAuthDoesNotNeedJson(int status) {
        response = reply(status, "denied", Map.of());
        assertKind(RecoveryRemoteException.Kind.AUTH);
    }
    @ParameterizedTest @ValueSource(ints = {408, 425, 429, 500, 502, 503, 504})
    void infrastructureRetryPreservesLongWait(int status) {
        response = reply(status, "unavailable", Map.of("retry-after", List.of("90000")));
        var failure = assertKind(RecoveryRemoteException.Kind.TRANSIENT);
        assertThat(failure.retryAt()).isEqualTo(NOW.plusSeconds(90000));
        assertThat(metrics.get("billing.recovery.http.retry_after").tag("outcome", "OVER_ONE_DAY").counter().count()).isEqualTo(1);
    }
    @Test void typedNonretryable500WinsOverInfrastructureRetry() {
        response = reply(500, "{\"code\":\"RESPONSE_LIMIT_EXCEEDED\",\"message\":\"Recovery response exceeds the byte limit.\",\"retryable\":false,\"correlationId\":\"" + OP + "\"}", Map.of());
        var failure = assertKind(RecoveryRemoteException.Kind.APPLICATION);
        assertThat(failure.error().code()).isEqualTo(RecoveryPayloadDecoder.ErrorCode.RESPONSE_LIMIT_EXCEEDED);
    }
    @Test void buildingLongWaitAndInvalidHeaderSignals() {
        String building = new String(response.body(), StandardCharsets.UTF_8);
        response = reply(202, building, Map.of("Retry-After", List.of("90000")));
        assertThat(client.createSnapshot(STREAM, OP).retryAt()).isEqualTo(NOW.plusSeconds(90000));
        response = reply(202, building, Map.of("Retry-After", List.of("5", "90")));
        assertThat(client.createSnapshot(STREAM, OP).retryAt()).isNull();
        assertThat(metrics.get("billing.recovery.http.retry_after").tag("outcome", "INVALID").counter().count()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(ints = {201, 204, 301, 302, 307, 308, 409, 410})
    void unexpectedStatusOrMalformedSourceErrorFailsClosed(int status) {
        response = reply(status, "{}", Map.of("Location", List.of("https://example.invalid")));
        assertKind(RecoveryRemoteException.Kind.CONTRACT);
        assertThat(calls).isEqualTo(1);
    }
    @Test void boundSourceLossAndMismatchedStream() {
        String error = "{\"code\":\"COVERAGE_GAP\",\"message\":\"Required withdrawal source is missing.\",\"retryable\":false,\"correlationId\":\"" + OP
                + "\",\"details\":{\"streamId\":\"" + STREAM + "\",\"replayFloor\":\"0\",\"observedThroughSequence\":\"1\",\"missingSequence\":\"1\",\"unavailableRange\":null,\"observedAt\":\"2026-10-12T00:00:00Z\"}}";
        response = reply(409, error, Map.of());
        var failure = catchThrowableOfType(() -> client.feedPage(STREAM, 0, null, null), RecoveryRemoteException.class);
        assertThat(failure.error().code()).isEqualTo(RecoveryPayloadDecoder.ErrorCode.COVERAGE_GAP);
        response = reply(409, error.replace(STREAM, SNAPSHOT), Map.of());
        failure = catchThrowableOfType(() -> client.feedPage(STREAM, 0, null, null), RecoveryRemoteException.class);
        assertThat(failure.kind()).isEqualTo(RecoveryRemoteException.Kind.CONTRACT);
    }
    @Test void invalidInputNeverDispatched() {
        assertThatThrownBy(() -> client.createSnapshot(STREAM + "\n", OP)).isInstanceOf(RecoveryRemoteException.class);
        assertThatThrownBy(() -> client.feedPage(STREAM, -1, null, null)).isInstanceOf(RecoveryRemoteException.class);
        assertThatThrownBy(() -> client.snapshotPage(SNAPSHOT, "a\nb")).isInstanceOf(RecoveryRemoteException.class);
        assertThatThrownBy(() -> client.feedPage(STREAM, 0, null, 3L)).isInstanceOf(RecoveryRemoteException.class);
        assertThat(calls).isZero();
    }
    @Test void invalidUtf8WrongSnapshotAndEncodedResponseRejected() {
        response = new HttpIdentityRecoveryClient.Response(200, Map.of(), new byte[]{(byte) 0xc3, 0x28});
        assertThatThrownBy(() -> client.snapshotStatus(SNAPSHOT, STREAM)).isInstanceOf(RecoveryContractException.class);
        response = reply(200, "{\"snapshotId\":\"" + OP + "\",\"status\":\"BUILDING\"}", Map.of());
        assertThatThrownBy(() -> client.snapshotStatus(SNAPSHOT, STREAM)).isInstanceOf(RecoveryContractException.class);
        response = reply(200, "{}", Map.of("Content-Encoding", List.of("gzip")));
        assertKind(RecoveryRemoteException.Kind.CONTRACT);
    }
    RecoveryRemoteException assertKind(RecoveryRemoteException.Kind kind) {
        var failure = catchThrowableOfType(() -> client.createSnapshot(STREAM, OP), RecoveryRemoteException.class);
        assertThat(failure.kind()).isEqualTo(kind); return failure;
    }
    @Test void disabledConfigurationCreatesNoAdapterAndEnabledValidatesBeforeCredentials() {
        new ApplicationContextRunner().withUserConfiguration(RecoveryHttpConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(IdentityWithdrawalRecoveryClient.class);
        });
        new ApplicationContextRunner().withUserConfiguration(RecoveryHttpConfiguration.class)
                .withBean(PaymentProperties.class, PaymentProperties::new).withBean(Clock.class, () -> CLOCK)
                .withBean(io.micrometer.core.instrument.MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("billing.payment.recovery.http-enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void validEndpointIsEnvironmentPinned() {
        PaymentProperties p = properties(); assertThat(RecoveryHttpConfiguration.jwtEndpoint(p, workload())).isEqualTo(ENDPOINT);
        p.getRecovery().setIdentityEnvironment("PRODUCTION");
        assertThatThrownBy(() -> RecoveryHttpConfiguration.jwtEndpoint(p, workload())).isInstanceOf(IllegalStateException.class);
        PaymentProperties wrong = properties(); wrong.getRecovery().setIdentityAllowedHost("other.invalid");
        assertThatThrownBy(() -> RecoveryHttpConfiguration.jwtEndpoint(wrong, workload())).isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"http://svc.test.vpc-lattice-svcs.ap-northeast-2.on.aws", "https://svc.test.vpc-lattice-svcs.ap-northeast-2.on.aws/", "https://svc.test.vpc-lattice-svcs.ap-northeast-2.on.aws?x=1", "https://svc.test.vpc-lattice-svcs.ap-northeast-2.on.aws:443", "https://svc.test.vpc-lattice-svcs.ap-northeast-2.on.aws#x", "https://user@svc.test.vpc-lattice-svcs.ap-northeast-2.on.aws"})
    void unsafeEndpointRejected(String endpoint) {
        PaymentProperties p = properties(); p.getRecovery().setIdentityBaseUrl(endpoint);
        p.getRecovery().setIdentityAllowedHost(URI.create(endpoint).getHost());
        assertThatThrownBy(() -> RecoveryHttpConfiguration.jwtEndpoint(p, workload())).isInstanceOf(IllegalStateException.class);
    }
    static web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties workload() { var p = new web.tosunsaeng.billing.global.config.security.WorkloadJwtProperties(); p.setEnvironment("test"); return p; }
    static PaymentProperties properties() {
        PaymentProperties p = new PaymentProperties(); p.setEnvironment("SANDBOX"); p.getRecovery().setFenceRequired(true);
        p.getRecovery().setIdentityBaseUrl(ENDPOINT.toString()); p.getRecovery().setIdentityAllowedHost(ENDPOINT.getHost());
        p.getRecovery().setIdentityEnvironment("test"); return p;
    }
    static String token() { return new web.tosunsaeng.billing.global.config.security.WorkloadJwtCodec.Issuer(web.tosunsaeng.billing.global.config.security.WorkloadJwtCodecTest.KEY.toJSONString(), "urn:tosunsaeng:workload:billing:test", CLOCK).issue("tosunsaeng-identity:withdrawal-recovery:test"); }
    static void verifySignature(HttpIdentityRecoveryClient.Request request, byte[] body) {
        var verifier = new web.tosunsaeng.billing.global.config.security.WorkloadJwtCodec(web.tosunsaeng.billing.global.config.security.WorkloadJwtCodecTest.publicSet(web.tosunsaeng.billing.global.config.security.WorkloadJwtCodecTest.KEY), "urn:tosunsaeng:workload:billing:test", CLOCK);
        assertThat(verifier.authenticate(request.firstMatchingHeader("Authorization").orElseThrow().substring(7)).audience()).isEqualTo("tosunsaeng-identity:withdrawal-recovery:test");
    }
}
