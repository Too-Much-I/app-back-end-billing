package web.tosunsaeng.billing.domain.reservation.paid;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RefundAccessPublisherTest {
    static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
    static RefundAccessPublisher.Reply reply(int code) { return new RefundAccessPublisher.Reply(code, Map.of(), new byte[0]); }
    @Test void exact204OnlyAndKnownConflictsAreSeparated() {
        assertThat(RefundAccessPublisher.classify(reply(204), NOW, NOW.plusSeconds(86400), 1).state()).isEqualTo("DELIVERED");
        assertThat(RefundAccessPublisher.classify(new RefundAccessPublisher.Reply(204, Map.of(), new byte[]{1}), NOW, NOW.plusSeconds(86400), 1).state()).isEqualTo("REVIEW_REQUIRED");
        for (String code : List.of("EVENT_ID_CONFLICT", "GROUP_REVOKE_CONFLICT")) {
            var response = new RefundAccessPublisher.Reply(409, Map.of(), ("{\"code\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8));
            assertThat(RefundAccessPublisher.classify(response, NOW, NOW.plusSeconds(86400), 1).failure()).isEqualTo(code);
        }
    }
    @ParameterizedTest @ValueSource(ints = {200, 201, 202, 301, 307, 400, 404, 409, 422})
    void unexpectedStatusRequiresReview(int code) {
        assertThat(RefundAccessPublisher.classify(reply(code), NOW, NOW.plusSeconds(86400), 1).state()).isEqualTo("REVIEW_REQUIRED");
    }
    @ParameterizedTest @ValueSource(ints = {401, 403})
    void authBlocksImmediately(int code) {
        assertThat(RefundAccessPublisher.classify(reply(code), NOW, NOW.plusSeconds(86400), 1).state()).isEqualTo("BLOCKED_AUTH");
    }
    @ParameterizedTest @ValueSource(ints = {408, 429, 500, 503, 599})
    void transientRetriesWithBoundedBackoff(int code) {
        var result = RefundAccessPublisher.classify(reply(code), NOW, NOW.plusSeconds(86400), 1);
        assertThat(result.state()).isEqualTo("READY"); assertThat(result.next()).isBetween(NOW.plusSeconds(5), NOW.plusSeconds(9));
    }
    @Test void retryAfterMalformedDuplicateOverflowFallBackAndLongValidWaitIsNotIgnored() {
        for (var headers : List.of(Map.of("Retry-After", List.of("bad")), Map.of("Retry-After", List.of("1", "2")),
                Map.of("Retry-After", List.of("999999999999999999999999")))) {
            var result = RefundAccessPublisher.classify(new RefundAccessPublisher.Reply(503, headers, new byte[0]), NOW, NOW.plusSeconds(86400), 1);
            assertThat(result.state()).isEqualTo("READY"); assertThat(result.next()).isBetween(NOW.plusSeconds(5), NOW.plusSeconds(9));
        }
        var longWait = RefundAccessPublisher.classify(new RefundAccessPublisher.Reply(429, Map.of("Retry-After", List.of("86401")), new byte[0]), NOW, NOW.plusSeconds(86400), 1);
        assertThat(longWait.state()).isEqualTo("REVIEW_REQUIRED"); assertThat(longWait.next()).isEqualTo(NOW.plusSeconds(86401));
        var future = DateTimeFormatter.RFC_1123_DATE_TIME.format(NOW.plusSeconds(100).atZone(ZoneOffset.UTC));
        assertThat(RefundAccessPublisher.retryAfter(Map.of("retry-after", List.of(future)), NOW)).isEqualTo(NOW.plusSeconds(100));
        var past = DateTimeFormatter.RFC_1123_DATE_TIME.format(NOW.minusSeconds(100).atZone(ZoneOffset.UTC));
        assertThat(RefundAccessPublisher.retryAfter(Map.of("Retry-After", List.of(past)), NOW)).isEqualTo(NOW);
    }
    @Test void realHttpTransportDoesNotFollowRedirectAndBoundsResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger followed = new AtomicInteger();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/target"); exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/target", exchange -> { followed.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close(); });
        server.createContext("/large", exchange -> {
            exchange.sendResponseHeaders(200, 16385); exchange.getResponseBody().write(new byte[16385]); exchange.close();
        });
        server.start();
        try (var transport = new RefundAccessHttpTransport()) {
            URI origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            assertThat(transport.post(origin.resolve("/redirect"), "fake-token", new byte[]{1}).status()).isEqualTo(302);
            assertThat(followed).hasValue(0);
            assertThatThrownBy(() -> transport.post(origin.resolve("/large"), "fake-token", new byte[]{1})).isInstanceOf(RefundAccessPublisher.ContractFailure.class);
        } finally { server.stop(0); }
    }
}
