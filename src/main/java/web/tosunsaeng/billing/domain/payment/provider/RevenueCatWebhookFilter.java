package web.tosunsaeng.billing.domain.payment.provider;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;
import web.tosunsaeng.billing.domain.payment.purchase.WebhookReceipt;

/** Terminal provider handler. Never forwards authenticated body or credentials to a user controller. */
public final class RevenueCatWebhookFilter extends OncePerRequestFilter {
    public static final String PATH = "/api/v1/payments/revenuecat/events";
    private final RevenueCatWebhookAuthentication authentication;
    private final RevenueCatWebhookDecoder decoder;
    private final Consumer<WebhookReceipt> receiver;
    public RevenueCatWebhookFilter(RevenueCatWebhookAuthentication authentication, RevenueCatWebhookDecoder decoder, Consumer<WebhookReceipt> receiver) {
        this.authentication = authentication; this.decoder = decoder; this.receiver = receiver;
    }
    @Override protected void doFilterInternal(HttpServletRequest q, HttpServletResponse r, FilterChain chain) throws IOException {
        r.setHeader("Cache-Control", "no-store");
        try {
            authentication.authorize(Collections.list(q.getHeaders("Authorization")));
            List<String> contentTypes = Collections.list(q.getHeaders("Content-Type"));
            if (contentTypes.size() != 1 || q.getHeader("Content-Encoding") != null) throw new PaymentException(400, "INVALID_REQUEST");
            try {
                MediaType media = MediaType.parseMediaType(contentTypes.getFirst());
                if (!"application".equalsIgnoreCase(media.getType()) || !"json".equalsIgnoreCase(media.getSubtype())
                        || media.getCharset() != null && !StandardCharsets.UTF_8.equals(media.getCharset())
                        || media.getParameters().keySet().stream().anyMatch(k -> !"charset".equalsIgnoreCase(k))) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw new PaymentException(400, "INVALID_REQUEST"); }
            if (q.getContentLengthLong() > RevenueCatWebhookDecoder.MAX_BYTES) throw new PaymentException(413, "PAYLOAD_TOO_LARGE");
            byte[] body = q.getInputStream().readNBytes(RevenueCatWebhookDecoder.MAX_BYTES + 1);
            if (body.length > RevenueCatWebhookDecoder.MAX_BYTES) throw new PaymentException(413, "PAYLOAD_TOO_LARGE");
            authentication.verify(Collections.list(q.getHeaders("X-RevenueCat-Webhook-Signature")), body);
            receiver.accept(decoder.decode(body));
            r.setStatus(200);
        } catch (PaymentException e) { r.setStatus(e.status()); }
        catch (IOException e) { r.setStatus(400); }
        catch (RuntimeException e) { r.setStatus(503); }
    }
}
