package web.tosunsaeng.billing.domain.reservation.paid;

import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow.Subscription;

/** No redirects, proxy, cookies, auth fallback, or transport retries. Five-second whole-response deadline. */
public final class RefundAccessHttpTransport implements RefundAccessPublisher.Transport, AutoCloseable {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1)
            .proxy(new ProxySelector() {
                public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                public void connectFailed(URI uri, SocketAddress address, java.io.IOException failure) { }
            }).build();
    @Override public RefundAccessPublisher.Reply post(URI endpoint, String bearer, byte[] body) throws Exception {
        if (body.length > 16384) throw new IllegalArgumentException("Oversize revoke body");
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearer).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var pending = client.sendAsync(request, info -> new LimitedBody());
        try {
            var response = pending.get(5, TimeUnit.SECONDS);
            return new RefundAccessPublisher.Reply(response.statusCode(), response.headers().map(), response.body());
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof RefundAccessPublisher.ContractFailure contract) throw contract;
            throw failed;
        } finally { if (!pending.isDone()) pending.cancel(true); }
    }
    @Override public void close() { client.shutdownNow(); }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > 16384 - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new RefundAccessPublisher.ContractFailure()); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onComplete() { result.complete(bytes.toByteArray()); }
        public void onError(Throwable failure) { result.completeExceptionally(failure); }
    }
}
