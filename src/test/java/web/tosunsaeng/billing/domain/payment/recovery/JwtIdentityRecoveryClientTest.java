package web.tosunsaeng.billing.domain.payment.recovery;

import java.net.URI;
import java.util.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.billing.global.config.security.*;
import static web.tosunsaeng.billing.global.config.security.WorkloadJwtCodecTest.*;
import static org.assertj.core.api.Assertions.*;

class JwtIdentityRecoveryClientTest {
    @Test void sendingIsIndependentOfReceiverSwitchAndRequiresValidSigningKey() {
        for (boolean receiving : new boolean[]{false, true}) {
            var properties = new web.tosunsaeng.billing.domain.payment.config.PaymentProperties();
            properties.setEnvironment("SANDBOX");
            var r = properties.getRecovery(); r.setFenceRequired(true);
            r.setIdentityEnvironment("test"); r.setIdentityBaseUrl("https://identity.example.invalid");
            r.setIdentityAllowedHost("identity.example.invalid");
            var workload = new WorkloadJwtProperties(); workload.setEnvironment("test"); workload.setReceiverEnabled(receiving);
            var config = new RecoveryHttpConfiguration();
            assertThatThrownBy(() -> config.identityWithdrawalRecoveryClient(properties, CLOCK, new SimpleMeterRegistry(), workload))
                    .isInstanceOf(IllegalStateException.class);
            workload.setBillingPrivateJwk(KEY.toJSONString());
            try (var client = config.identityWithdrawalRecoveryClient(properties, CLOCK, new SimpleMeterRegistry(), workload)) {
                assertThat(client).isInstanceOf(JwtIdentityRecoveryClient.class);
            }
        }
    }
    @Test void explicitJwtModeUsesPinnedOriginAndNeverNeedsAwsCredentials() {
        var properties = new web.tosunsaeng.billing.domain.payment.config.PaymentProperties();
        properties.setEnvironment("SANDBOX"); var recovery = properties.getRecovery();
        recovery.setFenceRequired(true);
        recovery.setIdentityBaseUrl("https://identity.example.invalid"); recovery.setIdentityAllowedHost("identity.example.invalid"); recovery.setIdentityEnvironment("test");
        var workload = new WorkloadJwtProperties(); workload.setEnvironment("test"); workload.setBillingPrivateJwk(KEY.toJSONString());
        try (var client = new RecoveryHttpConfiguration().identityWithdrawalRecoveryClient(properties, CLOCK, new SimpleMeterRegistry(), workload)) {
            assertThat(client).isInstanceOf(JwtIdentityRecoveryClient.class);
        }
        recovery.setIdentityEnvironment("production");
        assertThatThrownBy(() -> new RecoveryHttpConfiguration().identityWithdrawalRecoveryClient(properties, CLOCK, new SimpleMeterRegistry(), workload))
                .isInstanceOf(IllegalStateException.class);
        recovery.setIdentityEnvironment("test"); workload.setBillingPrivateJwk("invalid");
        assertThatThrownBy(() -> new RecoveryHttpConfiguration().identityWithdrawalRecoveryClient(properties, CLOCK, new SimpleMeterRegistry(), workload))
                .isInstanceOf(IllegalStateException.class).hasMessage("Invalid workload signing key configuration");
    }
    @Test void sameBodyAndOperationFreshBearerNoSigV4OrBaggage() {
        List<String> tokens = new ArrayList<>(); List<byte[]> bodies = new ArrayList<>();
        String stream="11111111-1111-4111-8111-111111111111", operation="22222222-2222-4222-8222-222222222222";
        var issuer = new WorkloadJwtCodec.Issuer(KEY.toJSONString(), "urn:tosunsaeng:workload:billing:test", CLOCK);
        var client = new JwtIdentityRecoveryClient(URI.create("https://identity.example.invalid"), (request,body) -> {
            tokens.add(request.firstMatchingHeader("Authorization").orElseThrow().substring(7)); bodies.add(body);
            assertThat(request.firstMatchingHeader("Idempotency-Key")).contains(operation);
            assertThat(request.firstMatchingHeader("x-amz-date")).isEmpty(); assertThat(request.firstMatchingHeader("baggage")).isEmpty();
            return new HttpIdentityRecoveryClient.Response(403,Map.of(),new byte[0]);
        }, CLOCK,new SimpleMeterRegistry(),issuer,"tosunsaeng-identity:withdrawal-recovery:test");
        for(int i=0;i<2;i++) assertThatThrownBy(() -> client.createSnapshot(stream,operation)).isInstanceOf(RecoveryRemoteException.class);
        assertThat(tokens).hasSize(2); assertThat(tokens.get(0)).isNotEqualTo(tokens.get(1)); assertThat(bodies.get(0)).isEqualTo(bodies.get(1));
        var decoder=new WorkloadJwtCodec(publicSet(KEY),"urn:tosunsaeng:workload:billing:test",CLOCK);
        assertThat(decoder.authenticate(tokens.getFirst()).audience()).isEqualTo("tosunsaeng-identity:withdrawal-recovery:test");
    }
}
