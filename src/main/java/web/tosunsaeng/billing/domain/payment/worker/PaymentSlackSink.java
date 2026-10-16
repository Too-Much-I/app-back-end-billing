package web.tosunsaeng.billing.domain.payment.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow.Subscription;

/** Incoming webhook, single configured channel. Never logs the secret URL or transport exceptions. */
public final class PaymentSlackSink implements PaymentProviderAlerts.Sink, AutoCloseable {
    record Response(int status, String body, List<String> retryAfter) {
        @Override public String toString() { return "SlackResponse[REDACTED]"; }
    }
    interface Transport extends AutoCloseable {
        Response post(URI target, byte[] json) throws Exception;
        default void close() {}
    }
    private final URI target;
    private final Clock clock;
    private final Transport transport;
    private Instant retryAt = Instant.MIN;
    private boolean blocked, closed;
    public PaymentSlackSink(String url, Clock clock) {
        this.target = target(url); this.clock = Objects.requireNonNull(clock); this.transport = new JdkTransport();
    }
    PaymentSlackSink(String url, Clock clock, Transport transport) {
        this.target = target(url); this.clock = Objects.requireNonNull(clock); this.transport = Objects.requireNonNull(transport);
    }
    static URI target(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || !"hooks.slack.com".equals(uri.getRawAuthority())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !uri.getRawPath().matches("/services/[A-Za-z0-9]+/[A-Za-z0-9]+/[A-Za-z0-9]+")) throw new IllegalArgumentException();
            return uri;
        } catch (RuntimeException invalid) { throw new IllegalStateException("Invalid Slack webhook configuration"); }
    }
    @Override public synchronized boolean send(PaymentProviderAlerts.Summary summary) {
        if (closed || blocked || clock.instant().isBefore(retryAt)) return false;
        try {
            if (summary == null || summary.kind() == null || summary.asOf() == null
                    || !Set.of("SANDBOX", "PRODUCTION").contains(summary.environment())
                    || !(summary.kind().name().startsWith("REFUND_") ? "TMI-221" : "TMI-212").equals(summary.runbookKey())
                    || summary.pending() < 0 || summary.review() < 0 || summary.cases() < 0 || summary.oldestPendingHours() < 0 || summary.overdueReview() < 0) return false;
            String title = switch (summary.kind()) {
                case DAILY_REVIEW -> "Billing 일반 검토 요약";
                case AUTH_INCIDENT -> "Billing 장애: RevenueCat 인증 차단";
                case AUTH_RECOVERY -> "Billing 복구: RevenueCat 인증 차단 해제";
                case REFUND_DAILY -> "Billing 환불 검토 요약";
                case REFUND_AUTH -> "Billing 장애: LC 환불 전달 인증 차단";
                case REFUND_DELAY -> "Billing 환불 정리·전달 지연";
                case REFUND_RECOVERY -> "Billing 환불 정리·전달 경보 회복";
            };
            String text = "[" + summary.environment() + "] " + title + "\n기준 시각: " + summary.asOf()
                    + (summary.kind().name().startsWith("REFUND_") ? "\n정리 미완료: " : "\n확인 대기: ") + summary.pending()
                    + (summary.kind().name().startsWith("REFUND_") ? " / 시험 보류: " : " / 검토 요청: ") + summary.review()
                    + (summary.kind().name().startsWith("REFUND_") ? " / LC 미전송: " : " / 구매 검토 기록: ") + summary.cases()
                    + "\n가장 오래된 대기: " + summary.oldestPendingHours() + "시간 / 7일 이상 검토: " + summary.overdueReview()
                    + "\n운영 안내: " + summary.runbookKey() + " (각 건수는 중복될 수 있음)";
            byte[] json = new ObjectMapper().writeValueAsBytes(Map.of("text", text, "mrkdwn", false));
            Response response = transport.post(target, json);
            if (Set.of(401, 403, 404, 410).contains(response.status())) blocked = true;
            if (response.status() == 429 || response.status() == 503) retryAt = retryAfter(response.retryAfter());
            return response.status() == 200 && "ok".equals(response.body().strip());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); return false;
        } catch (Exception ignored) { return false; }
    }
    private Instant retryAfter(List<String> values) {
        Instant fallback = clock.instant().plusSeconds(3600);
        if (values == null || values.isEmpty()) return fallback;
        if (values.size() != 1) return Instant.MAX;
        try {
            String value = values.getFirst();
            Instant time = value.matches("[0-9]+") ? clock.instant().plusSeconds(Long.parseLong(value))
                    : ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return time.isAfter(fallback) ? time : fallback;
        } catch (RuntimeException invalid) { return Instant.MAX; } // Operator config/restart, no early retry against an unknown deadline.
    }
    @Override public synchronized void close() {
        if (closed) return; closed = true;
        try { transport.close(); } catch (Exception ignored) { /* No raw errors. */ }
    }
    @Override public String toString() { return "PaymentSlackSink[REDACTED]"; }

    static final class JdkTransport implements Transport {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).proxy(new ProxySelector() {
                    public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                    public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) { }
                }).build();
        public Response post(URI uri, byte[] json) throws Exception {
            target(uri.toString());
            CompletableFuture<HttpResponse<byte[]>> future = null;
            try {
                var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(json)).build();
                future = client.sendAsync(request, info -> new BoundedBody());
                var response = future.get(5, TimeUnit.SECONDS);
                return new Response(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8), response.headers().allValues("Retry-After"));
            } finally { if (future != null && !future.isDone()) future.cancel(true); }
        }
        public void close() { client.shutdownNow(); }
        @Override public String toString() { return "SlackTransport[REDACTED]"; }
    }
    static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Subscription s) { subscription = s; s.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > 2048 - bytes.size()) { subscription.cancel(); result.completeExceptionally(new IllegalStateException("Slack response limit")); return; }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(new IllegalStateException("Slack transport failed")); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
