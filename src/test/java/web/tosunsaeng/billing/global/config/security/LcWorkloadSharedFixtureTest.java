package web.tosunsaeng.billing.global.config.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.assertj.core.api.Assertions.*;

/** Runs shared raw signed artifacts against the existing codec, not a new LC HTTP implementation. */
class LcWorkloadSharedFixtureTest {
    private static final String ROOT = "/contracts/lc-billing-workload-auth-r1/";
    private static final ObjectMapper JSON = new ObjectMapper();
    private byte[] bytes(String file) throws Exception {
        try (var in = getClass().getResourceAsStream(ROOT + file)) {
            if (in == null) throw new IllegalStateException("Missing shared fixture: " + file);
            return in.readAllBytes();
        }
    }
    @TestFactory java.util.stream.Stream<DynamicTest> signedTokenCases() throws Exception {
        JsonNode jwks = JSON.readTree(bytes("public-jwks.json"));
        var tests = new ArrayList<DynamicTest>();
        for (var c : JSON.readTree(bytes("token-cases.json")).get("cases")) {
            tests.add(DynamicTest.dynamicTest(c.get("id").asText(), () -> {
                String service = c.get("verifier").asText();
                String issuer = "urn:tosunsaeng:workload:" + (service.equals("lc") ? "learning-core" : service) + ":test";
                String keySet = c.get("keySet").asText();
                if (keySet.equals("current")) keySet = service;
                var codec = new WorkloadJwtCodec(jwks.get(keySet).toString(), issuer,
                        Clock.fixed(Instant.parse(c.get("now").asText()), ZoneOffset.UTC));
                if (c.get("valid").booleanValue()) {
                    var principal = codec.authenticate(c.get("token").asText());
                    assertThat(principal.subject()).isEqualTo(c.get("subject").asText());
                    assertThat(principal.audience()).isEqualTo(c.get("audience").asText());
                } else {
                    assertThatThrownBy(() -> codec.authenticate(c.get("token").asText()))
                            .isInstanceOf(WorkloadJwtCodec.InvalidToken.class)
                            .hasMessage("Invalid workload token").hasNoCause();
                }
            }));
        }
        return tests.stream();
    }
    @Test void sharedBytesMatchHashManifest() throws Exception {
        String manifest = new String(bytes("SHA256SUMS"), StandardCharsets.UTF_8);
        for (String line : manifest.lines().toList()) {
            String[] parts = line.split("  ", 2);
            assertThat(parts).hasSize(2);
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(parts[1]))))
                    .as(parts[1]).isEqualTo(parts[0]);
        }
    }
}
