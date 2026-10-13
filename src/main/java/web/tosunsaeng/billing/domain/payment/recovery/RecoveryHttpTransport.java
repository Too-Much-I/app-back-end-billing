package web.tosunsaeng.billing.domain.payment.recovery;

import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow.Subscription;

/** One POST dispatch, no application retries, redirects, cookies, proxy or authentication fallback. */
final class RecoveryHttpTransport implements HttpIdentityRecoveryClient.Transport, AutoCloseable {
    private final HttpClient client;
    private final Duration timeout;
    RecoveryHttpTransport() { this(Duration.ofSeconds(5)); }
    // Short deadline injection only for local tests. Production uses the fixed constructor above.
    RecoveryHttpTransport(Duration timeout) {
        this.timeout = timeout;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1)
                .proxy(new ProxySelector() {
                    public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                    public void connectFailed(URI uri, SocketAddress address, java.io.IOException failure) { }
                }).build();
    }
    @Override public HttpIdentityRecoveryClient.Response exchange(HttpIdentityRecoveryClient.Request signed, byte[] body) {
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var request = HttpRequest.newBuilder(signed.getUri()).timeout(timeout).POST(HttpRequest.BodyPublishers.ofByteArray(body));
            // JDK synthesizes Host from this exact URI; restricted header overrides are never enabled.
            if (!signed.firstMatchingHeader("Host").orElse("").equals(signed.getUri().getRawAuthority())) {
                throw RecoveryRemoteException.contract();
            }
            signed.headers().forEach((name, values) -> {
                if (!name.equalsIgnoreCase("Host") && !name.equalsIgnoreCase("Content-Length"))
                    values.forEach(value -> request.header(name, value));
            });
            pending = client.sendAsync(request.build(), info -> new BoundedBody());
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS); // Includes body stalls, not only headers.
            return new HttpIdentityRecoveryClient.Response(response.statusCode(), response.headers().map(), response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw RecoveryRemoteException.transientFailure(null);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RecoveryRemoteException classified) throw classified;
            throw RecoveryRemoteException.transientFailure(null);
        } catch (TimeoutException failure) {
            throw RecoveryRemoteException.transientFailure(null);
        } catch (RecoveryRemoteException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw RecoveryRemoteException.contract();
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }
    @Override public void close() { client.shutdownNow(); }

    static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > RecoveryPayloadDecoder.MAX_BYTES - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(RecoveryRemoteException.contract()); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable failure) { result.completeExceptionally(RecoveryRemoteException.transientFailure(null)); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
