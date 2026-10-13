package web.tosunsaeng.billing.global.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static web.tosunsaeng.billing.global.config.security.WorkloadJwtCodecTest.*;

class IdentitySharedJwtFixtureTest {
    @TestFactory List<DynamicTest> originalNumericTokensThroughRealVerifier() throws Exception {
        String raw;
        try (var input = getClass().getResourceAsStream("/contracts/workload-jwt-r1/time-cases.json")) {
            raw = new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
        }
        var cases = Pattern.compile("\\{[^{}]*}").matcher(raw);
        List<DynamicTest> tests = new ArrayList<>();
        while (cases.find()) {
            String original = cases.group();
            var control = new ObjectMapper().readTree(original);
            tests.add(DynamicTest.dynamicTest(control.get("name").asText(), () -> {
                // Preserve each raw NumericDate token; parse only the expectation and fixed clock.
                StringBuilder payload = new StringBuilder("{\"iss\":\"").append(ISSUER)
                        .append("\",\"sub\":\"identity-service\",\"aud\":\"").append(AUD)
                        .append("\",\"jti\":\"11111111-1111-4111-8111-111111111111\"");
                for (String field : List.of("iat", "exp", "nbf")) {
                    var token = Pattern.compile("\"" + field + "\"\\s*:\\s*([^,}]+)").matcher(original);
                    if (token.find()) payload.append(",\"").append(field).append("\":").append(token.group(1));
                }
                payload.append('}');
                var verifier = new WorkloadJwtCodec(publicSet(KEY), ISSUER,
                        Clock.fixed(Instant.ofEpochSecond(control.get("now").longValue()), ZoneOffset.UTC));
                String jwt = sign(payload.toString());
                if (control.get("accepted").booleanValue()) verifier.authenticate(jwt);
                else assertThatThrownBy(() -> verifier.authenticate(jwt)).isInstanceOf(WorkloadJwtCodec.InvalidToken.class);
            }));
        }
        assertThat(tests).hasSize(16);
        return tests;
    }
}
