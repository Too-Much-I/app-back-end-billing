package web.tosunsaeng.billing.domain.payment.provider;

import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow.Subscription;

/** Fixed HTTPS destination, bounded body/deadline. No provider SDK, redirect, proxy or retries. */
public final class RevenueCatHttpTransport implements RevenueCatReadClient.Transport, AutoCloseable {
    private final HttpClient client;
    public RevenueCatHttpTransport() {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER)
                .proxy(new ProxySelector() {
                    public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                    public void connectFailed(URI uri, SocketAddress address, java.io.IOException failure) { }
                }).build();
    }
    @Override public RevenueCatReadClient.Response get(URI uri, String bearer) {
        return get(uri, bearer, Duration.ofSeconds(5));
    }
    @Override public RevenueCatReadClient.Response get(URI uri, String bearer, Duration timeout) {
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(5)) > 0)
            throw RevenueCatFailure.contract();
        if (!"https".equals(uri.getScheme()) || !"api.revenuecat.com".equals(uri.getRawAuthority()) || uri.getFragment() != null
                || !uri.getRawPath().startsWith("/v2/projects/")) throw RevenueCatFailure.contract();
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var request = HttpRequest.newBuilder(uri).timeout(timeout).GET()
                    .header("Authorization", "Bearer " + bearer).header("Accept", "application/json").build();
            pending = client.sendAsync(request, info -> new BoundedBody());
            var r = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            return new RevenueCatReadClient.Response(r.statusCode(), r.headers().map(), r.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new RevenueCatFailure(RevenueCatFailure.Kind.RETRY, null);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RevenueCatFailure f) throw f;
            throw new RevenueCatFailure(RevenueCatFailure.Kind.RETRY, null);
        } catch (TimeoutException e) { throw new RevenueCatFailure(RevenueCatFailure.Kind.RETRY, null); }
        catch (RuntimeException e) { throw RevenueCatFailure.contract(); }
        finally { if (pending != null && !pending.isDone()) pending.cancel(true); }
    }
    @Override public void close() { client.shutdownNow(); }
    static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Subscription s) { subscription = s; s.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > RevenueCatPurchaseDecoder.MAX_BYTES - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(RevenueCatFailure.contract()); return;
                }
                byte[] b = new byte[buffer.remaining()]; buffer.get(b); bytes.writeBytes(b);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(new RevenueCatFailure(RevenueCatFailure.Kind.RETRY, null)); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
