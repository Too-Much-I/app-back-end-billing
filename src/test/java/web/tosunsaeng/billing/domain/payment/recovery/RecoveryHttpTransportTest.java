package web.tosunsaeng.billing.domain.payment.recovery;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static web.tosunsaeng.billing.domain.payment.recovery.HttpIdentityRecoveryClientTest.*;

/** Local loopback only. Production endpoint configuration never allows this HTTP test endpoint. */
class RecoveryHttpTransportTest {
    HttpServer server;
    URI base;
    RecoveryHttpTransport transport;
    ExecutorService executor;
    CountDownLatch release = new CountDownLatch(1);
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(executor); server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        transport = new RecoveryHttpTransport();
    }
    @AfterEach void stop() {
        release.countDown(); transport.close(); server.stop(0); executor.shutdownNow();
    }
    HttpIdentityRecoveryClient.Request request() {
        return new HttpIdentityRecoveryClient.Request(base.resolve("/test"), Map.of("Host", List.of(base.getRawAuthority()), "Content-Type", List.of("application/json")));
    }
    @Test void signedWireBodyAndHeadersRemainVerifiable() throws Exception {
        AtomicReference<HttpIdentityRecoveryClient.Request> observed = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/internal/v1/billing/withdrawals/snapshots", exchange -> {
            calls.incrementAndGet(); body.set(exchange.getRequestBody().readAllBytes());
            observed.set(new HttpIdentityRecoveryClient.Request(base.resolve(exchange.getRequestURI()), exchange.getRequestHeaders()));
            byte[] response = ("{\"snapshotId\":\"" + SNAPSHOT + "\",\"status\":\"BUILDING\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Retry-After", "9"); exchange.sendResponseHeaders(202, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        var client = new HttpIdentityRecoveryClient(base, transport, CLOCK, new SimpleMeterRegistry(), HttpIdentityRecoveryClientTest::token);
        assertThat(client.createSnapshot(STREAM, OP).retryAt()).isEqualTo(NOW.plusSeconds(9));
        verifySignature(observed.get(), body.get());
        assertThat(observed.get().firstMatchingHeader("baggage")).isEmpty();
        assertThat(calls).hasValue(1);
    }
    @ParameterizedTest @ValueSource(ints = {16384, 16385})
    void chunkedBodyEnforcesLimitBeforeUnboundedAllocation(int size) {
        server.createContext("/test", exchange -> {
            exchange.getRequestBody().readAllBytes(); exchange.sendResponseHeaders(200, 0);
            try { exchange.getResponseBody().write(new byte[size]); } finally { exchange.close(); }
        });
        if (size == 16384) assertThat(transport.exchange(request(), new byte[0]).body()).hasSize(size);
        else {
            var failure = catchThrowableOfType(() -> transport.exchange(request(), new byte[0]), RecoveryRemoteException.class);
            assertThat(failure.kind()).isEqualTo(RecoveryRemoteException.Kind.CONTRACT);
        }
    }
    @Test void stalledBodyIsCoveredByTotalDeadline() {
        transport.close(); transport = new RecoveryHttpTransport(Duration.ofMillis(250));
        CountDownLatch sent = new CountDownLatch(1);
        server.createContext("/test", exchange -> {
            exchange.getRequestBody().readAllBytes(); exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('x'); exchange.getResponseBody().flush(); sent.countDown();
            try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        long before = System.nanoTime();
        var failure = catchThrowableOfType(() -> transport.exchange(request(), new byte[0]), RecoveryRemoteException.class);
        assertThat(failure.kind()).isEqualTo(RecoveryRemoteException.Kind.TRANSIENT);
        assertThat(sent.getCount()).isZero();
        assertThat(Duration.ofNanos(System.nanoTime() - before)).isLessThan(Duration.ofSeconds(2));
    }
    @Test void redirectIsReturnedWithoutFollowing() {
        AtomicInteger other = new AtomicInteger();
        server.createContext("/test", exchange -> {
            exchange.getRequestBody().readAllBytes(); exchange.getResponseHeaders().add("Location", base + "/other");
            exchange.sendResponseHeaders(307, -1); exchange.close();
        });
        server.createContext("/other", exchange -> { other.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        assertThat(transport.exchange(request(), new byte[0]).status()).isEqualTo(307);
        assertThat(other).hasValue(0);
    }
    @Test void lostResponseDoesNotReplayPostInsideTransport() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/test", exchange -> {
            exchange.getRequestBody().readAllBytes(); calls.incrementAndGet(); exchange.close();
        });
        var failure = catchThrowableOfType(() -> transport.exchange(request(), new byte[]{1}), RecoveryRemoteException.class);
        assertThat(failure.kind()).isEqualTo(RecoveryRemoteException.Kind.TRANSIENT);
        assertThat(calls).hasValue(1);
    }
    @Test void hostMismatchFailsBeforeNetwork() {
        var wrong = new HttpIdentityRecoveryClient.Request(request().getUri(), Map.of("Host", List.of("example.invalid")));
        var failure = catchThrowableOfType(() -> transport.exchange(wrong, new byte[0]), RecoveryRemoteException.class);
        assertThat(failure.kind()).isEqualTo(RecoveryRemoteException.Kind.CONTRACT);
    }
}
