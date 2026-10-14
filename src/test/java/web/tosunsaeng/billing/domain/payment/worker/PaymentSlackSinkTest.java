package web.tosunsaeng.billing.domain.payment.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class PaymentSlackSinkTest {
    static final String URL = "https://hooks.slack.com/services/TFAKE/BFAKE/FAKESECRET";
    static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
    static class Time extends Clock {
        Instant now = NOW;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    static class Fake implements PaymentSlackSink.Transport {
        int calls, closes; byte[] payload;
        PaymentSlackSink.Response response = new PaymentSlackSink.Response(200, "ok", List.of());
        public PaymentSlackSink.Response post(URI target, byte[] json) { calls++; payload = json; return response; }
        public void close() { closes++; }
    }
    static PaymentProviderAlerts.Summary summary(PaymentProviderAlerts.Kind kind) {
        return new PaymentProviderAlerts.Summary("SANDBOX", kind, NOW, 1, 2, 3, 4, 5, "TMI-212");
    }
    @Test void rendersOnlyWhitelistedSummaryForAllKinds() throws Exception {
        Fake fake = new Fake();
        try (var sink = new PaymentSlackSink(URL, new Time(), fake)) {
            for (var kind : PaymentProviderAlerts.Kind.values()) {
                assertThat(sink.send(summary(kind))).isTrue();
                var json = new ObjectMapper().readTree(fake.payload);
                assertThat(json.size()).isEqualTo(2);
                assertThat(json.get("mrkdwn").booleanValue()).isFalse();
                assertThat(json.get("text").textValue()).contains("SANDBOX", "TMI-212").doesNotContain("FAKESECRET");
            }
            assertThat(sink.toString()).doesNotContain("FAKESECRET");
        }
        assertThat(fake.closes).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings = {"", "http://hooks.slack.com/services/A/B/C", "https://hooks.slack.com.evil/services/A/B/C", "https://user@hooks.slack.com/services/A/B/C", "https://hooks.slack.com:443/services/A/B/C", "https://hooks.slack.com/services/A/B/C?q=secret", "https://hooks.slack.com/services/A/B/C#secret", "https://hooks.slack.com/services/A/B/%43", " https://hooks.slack.com/services/A/B/C"})
    void rejectsUnsafeUrlsWithoutEchoingInput(String url) {
        assertThatThrownBy(() -> PaymentSlackSink.target(url)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid Slack webhook configuration").hasNoCause();
    }
    @Test void invalidSummaryNeverSends() {
        Fake fake = new Fake(); var sink = new PaymentSlackSink(URL, new Time(), fake);
        assertThat(sink.send(null)).isFalse();
        assertThat(sink.send(new PaymentProviderAlerts.Summary("@everyone", PaymentProviderAlerts.Kind.DAILY_REVIEW, NOW, 0, 0, 0, 0, 0, "TMI-212"))).isFalse();
        assertThat(sink.send(new PaymentProviderAlerts.Summary("SANDBOX", PaymentProviderAlerts.Kind.DAILY_REVIEW, NOW, -1, 0, 0, 0, 0, "TMI-212"))).isFalse();
        assertThat(fake.calls).isZero();
    }
    @ParameterizedTest @ValueSource(ints = {401,403,404,410})
    void permanentConfigurationFailureBlocksFurtherCalls(int status) {
        Fake fake = new Fake(); Time time = new Time();
        fake.response = new PaymentSlackSink.Response(status, "secret", List.of());
        var sink = new PaymentSlackSink(URL, time, fake);
        assertThat(sink.send(summary(PaymentProviderAlerts.Kind.AUTH_INCIDENT))).isFalse();
        time.now = NOW.plusSeconds(86400);
        assertThat(sink.send(summary(PaymentProviderAlerts.Kind.AUTH_INCIDENT))).isFalse();
        assertThat(fake.calls).isEqualTo(1);
        assertThat(fake.response.toString()).doesNotContain("secret");
    }
    @ParameterizedTest @ValueSource(strings = {"7200", "Wed, 14 Oct 2026 02:00:00 GMT"})
    void honorsRetryAfterSecondsAndDate(String header) {
        Fake fake = new Fake(); Time time = new Time();
        fake.response = new PaymentSlackSink.Response(429, "rate limited", List.of(header));
        var sink = new PaymentSlackSink(URL, time, fake);
        assertThat(sink.send(summary(PaymentProviderAlerts.Kind.DAILY_REVIEW))).isFalse();
        time.now = NOW.plusSeconds(7199);
        assertThat(sink.send(summary(PaymentProviderAlerts.Kind.DAILY_REVIEW))).isFalse();
        assertThat(fake.calls).isEqualTo(1);
        time.now = NOW.plusSeconds(7200); fake.response = new PaymentSlackSink.Response(200, "ok\n", List.of());
        assertThat(sink.send(summary(PaymentProviderAlerts.Kind.DAILY_REVIEW))).isTrue();
    }
    @Test void absentOrPastRetryAfterUsesOneHourAndInvalidHeadersHold() {
        for (var headers : List.of(List.<String>of(), List.of("0"), List.of("Tue, 13 Oct 2026 00:00:00 GMT"), List.of("bad"), List.of("1", "2"), List.of("9999999999999999999999"))) {
            Fake fake = new Fake(); Time time = new Time();
            fake.response = new PaymentSlackSink.Response(503, "unavailable", headers);
            var sink = new PaymentSlackSink(URL, time, fake);
            sink.send(summary(PaymentProviderAlerts.Kind.DAILY_REVIEW));
            time.now = NOW.plusSeconds(3599); sink.send(summary(PaymentProviderAlerts.Kind.DAILY_REVIEW));
            assertThat(fake.calls).isEqualTo(1);
            time.now = NOW.plusSeconds(3600); sink.send(summary(PaymentProviderAlerts.Kind.DAILY_REVIEW));
            assertThat(fake.calls).isEqualTo(headers.isEmpty() || headers.getFirst().equals("0") || headers.getFirst().startsWith("Tue") ? 2 : 1);
        }
    }
    @Test void failuresCloseAndInterruptAreContained() {
        var sink = new PaymentSlackSink(URL, new Time(), (uri, body) -> { throw new InterruptedException("secret"); });
        try { assertThat(sink.send(summary(PaymentProviderAlerts.Kind.DAILY_REVIEW))).isFalse(); assertThat(Thread.currentThread().isInterrupted()).isTrue(); }
        finally { Thread.interrupted(); }
        Fake fake = new Fake(); var other = new PaymentSlackSink(URL, new Time(), fake);
        fake.response = new PaymentSlackSink.Response(200, "not ok", List.of());
        assertThat(other.send(summary(PaymentProviderAlerts.Kind.DAILY_REVIEW))).isFalse();
        other.close(); other.close(); assertThat(fake.closes).isEqualTo(1);
        assertThat(other.send(summary(PaymentProviderAlerts.Kind.DAILY_REVIEW))).isFalse();
    }
    @Test void responseBodyHasBoundedMemoryAndSanitizedFailure() {
        for (int size : new int[]{2048,2049}) {
            var body = new PaymentSlackSink.BoundedBody(); boolean[] canceled = {false};
            body.onSubscribe(new Flow.Subscription() { public void request(long n) {} public void cancel() { canceled[0] = true; } });
            body.onNext(List.of(ByteBuffer.wrap(new byte[size]))); body.onComplete();
            if (size == 2048) assertThat(body.getBody().toCompletableFuture().join()).hasSize(2048);
            else { assertThat(canceled[0]).isTrue(); assertThatThrownBy(() -> body.getBody().toCompletableFuture().join()).hasRootCauseMessage("Slack response limit"); }
        }
        var body = new PaymentSlackSink.BoundedBody(); body.onError(new RuntimeException("secret"));
        assertThatThrownBy(() -> body.getBody().toCompletableFuture().join()).hasRootCauseMessage("Slack transport failed");
    }
    @Test void configurationIsOffWithoutDependenciesAndOnRequiresValidSecret() {
        var runner = new ApplicationContextRunner().withUserConfiguration(PaymentSlackConfiguration.class);
        runner.run(context -> { assertThat(context).hasNotFailed(); assertThat(context).doesNotHaveBean(PaymentProviderAlerts.Sink.class); });
        var enabled = runner.withBean(Clock.class, Time::new).withPropertyValues("billing.payment.provider.slack.enabled=true");
        enabled.run(context -> assertThat(context).hasFailed());
        enabled.withPropertyValues("billing.payment.provider.slack.webhook-url=" + URL).run(context -> {
            assertThat(context).hasNotFailed(); assertThat(context).hasSingleBean(PaymentProviderAlerts.Sink.class);
        }); // Creates and closes client only; never sends HTTP.
        assertThat(new PaymentSlackProperties().toString()).isEqualTo("PaymentSlackProperties[REDACTED]");
    }
}
