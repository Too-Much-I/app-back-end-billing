package web.tosunsaeng.billing.domain.payment.recovery;

import java.net.URI;
import java.time.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import web.tosunsaeng.billing.global.config.security.WorkloadJwtCodec;

/** Fresh short-lived token per dispatch; operation/body semantics remain recovery-r1. */
public final class JwtIdentityRecoveryClient extends HttpIdentityRecoveryClient {
    JwtIdentityRecoveryClient(URI endpoint, Transport transport, Clock clock, MeterRegistry metrics,
                              WorkloadJwtCodec.Issuer issuer, String audience) {
        super(endpoint, transport, clock, metrics, () -> issuer.issue(audience));
    }
}
