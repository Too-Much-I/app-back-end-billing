package web.tosunsaeng.billing.global.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

public class WorkloadJwtCodecTest {
    public static final RSAKey KEY = key("fake-current");
    public static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final String ISSUER = "urn:tosunsaeng:workload:identity:test";
    public static final String AUD = "tosunsaeng-billing:withdrawal-events:test";
    static final ObjectMapper JSON = new ObjectMapper();
    final WorkloadJwtCodec codec = new WorkloadJwtCodec(publicSet(KEY), ISSUER, CLOCK);
    @TestFactory java.util.stream.Stream<DynamicTest> sharedTimeFixture() throws Exception {
        var cases = JSON.readTree(getClass().getResourceAsStream("/contracts/workload-auth-r1/time-cases.json"));
        List<DynamicTest> tests = new ArrayList<>();
        for (var c : cases) tests.add(DynamicTest.dynamicTest(c.get("name").asText(), () -> {
            var payload = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(claims());
            payload.put("iat", NOW.getEpochSecond() + c.get("iatOffset").longValue());
            payload.put("exp", NOW.getEpochSecond() + c.get("expOffset").longValue());
            if (!c.get("nbfOffset").isNull()) payload.put("nbf", NOW.getEpochSecond() + c.get("nbfOffset").longValue());
            check(sign(payload.toString()), c.get("valid").booleanValue());
        }));
        return tests.stream();
    }
    public static RSAKey key(String kid) {
        try { return new RSAKeyGenerator(2048).keyID(kid).algorithm(JWSAlgorithm.RS256).keyUse(KeyUse.SIGNATURE).keyOperations(Set.of(KeyOperation.SIGN)).generate(); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    public static String publicSet(RSAKey... keys) {
        return new JWKSet(Arrays.stream(keys).map(k -> (JWK) new RSAKey.Builder(k.toPublicJWK()).keyOperations(Set.of(KeyOperation.VERIFY)).build()).toList()).toString();
    }
    public static String claims() {
        return "{\"iss\":\"" + ISSUER + "\",\"sub\":\"identity-service\",\"aud\":\"" + AUD
                + "\",\"iat\":" + NOW.getEpochSecond() + ",\"exp\":" + NOW.plusSeconds(60).getEpochSecond()
                + ",\"jti\":\"11111111-1111-4111-8111-111111111111\"}";
    }
    public static String sign(String claims) { return sign(claims, KEY); }
    static String sign(String claims, RSAKey key) {
        try {
            var jwt = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(key.getKeyID()).build(), new Payload(claims));
            jwt.sign(new RSASSASigner(key)); return jwt.serialize();
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
    @Test void authenticatesWithoutGrantingAudienceOrSubjectAuthority() {
        var principal = codec.authenticate(sign(claims())); assertThat(principal.subject()).isEqualTo("identity-service");
        assertThat(codec.authenticate(sign(claims().replace(AUD, "wrong-purpose"))).audience()).isEqualTo("wrong-purpose");
    }
    @ParameterizedTest @CsvSource({"0,false", "1,true", "60,true", "61,false"})
    void ttlBoundaries(int ttl, boolean allowed) throws Exception {
        var claims = JSON.readTree(claims()); ((com.fasterxml.jackson.databind.node.ObjectNode) claims).put("exp", NOW.getEpochSecond() + ttl);
        check(sign(claims.toString()), allowed);
    }
    @ParameterizedTest @CsvSource({"30,true", "31,false"})
    void futureIat(int seconds, boolean allowed) throws Exception {
        var c = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(claims());
        c.put("iat", NOW.getEpochSecond() + seconds).put("exp", NOW.getEpochSecond() + seconds + 60);
        check(sign(c.toString()), allowed);
    }
    @ParameterizedTest @CsvSource({"-1,false", "0,true", "30,true", "31,false", "60,false"})
    void nbfBoundaries(int seconds, boolean allowed) throws Exception {
        var c = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(claims()); c.put("nbf", NOW.getEpochSecond() + seconds);
        check(sign(c.toString()), allowed);
    }
    @Test void expirationUsesExactInstantBoundary() {
        String jwt = sign(claims());
        new WorkloadJwtCodec(publicSet(KEY), ISSUER, Clock.fixed(NOW.plusSeconds(90).minusNanos(1), ZoneOffset.UTC)).authenticate(jwt);
        assertThatThrownBy(() -> new WorkloadJwtCodec(publicSet(KEY), ISSUER, Clock.fixed(NOW.plusSeconds(90), ZoneOffset.UTC)).authenticate(jwt))
                .isInstanceOf(WorkloadJwtCodec.InvalidToken.class);
    }
    @ParameterizedTest @ValueSource(strings={"\"123\"", "1.0", "1e3", "-1", "9223372036854775807", "9223372036854775808", "null"})
    void numericFormAndOverflow(String number) { check(sign(claims().replace("\"iat\":" + NOW.getEpochSecond(), "\"iat\":" + number)), false); }
    @Test void duplicateClaimsTrailingTokensAndWrongIssuerRejected() {
        check(sign(claims().replace("{", "{\"iat\":1,")), false);
        check(sign(claims() + " {}"), false);
        check(sign(claims().replace(ISSUER, "urn:untrusted")), false);
        check(sign(claims().replace("\"" + AUD + "\"", "[\"" + AUD + "\"]")), false);
    }
    @Test void badHeaderForgeryAndUnknownKidAreSanitized() {
        String jwt = sign(claims()); String[] parts = jwt.split("\\.");
        for (String header : List.of("{\"alg\":\"none\",\"typ\":\"JWT\",\"kid\":\"fake-current\"}",
                "{\"alg\":\"RS256\",\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"fake-current\"}",
                "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"fake-current\",\"jku\":\"https://example.invalid\"}")) {
            check(Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[1] + "." + parts[2], false);
        }
        check(parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(claims().replace(AUD, "changed").getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[2], false);
        check(sign(claims(), key("fake-other")), false);
    }
    @Test void keyConfigurationCannotContainPrivateDuplicateOrWrongOperations() {
        for (String invalid : List.of("", "{\"keys\":[]}", "{\"keys\":[" + KEY.toJSONString() + "]}",
                publicSet(KEY, KEY), publicSet(KEY).replace("verify", "sign"), publicSet(KEY).replace("RS256", "RS512"),
                publicSet(KEY).replace("\"kty\":", "\"kty\":\"RSA\",\"kty\":"))) {
            assertThatThrownBy(() -> new WorkloadJwtCodec(invalid, ISSUER, CLOCK)).isInstanceOf(IllegalStateException.class).hasMessage("Invalid workload public key configuration");
        }
    }
    @Test void rotationAndRevocationUseOnlyConfiguredKeys() {
        var next = key("fake-next"); var overlap = new WorkloadJwtCodec(publicSet(KEY, next), ISSUER, CLOCK);
        overlap.authenticate(sign(claims())); overlap.authenticate(sign(claims(), next));
        var replaced = new WorkloadJwtCodec(publicSet(next), ISSUER, CLOCK);
        assertThatThrownBy(() -> replaced.authenticate(sign(claims()))).isInstanceOf(WorkloadJwtCodec.InvalidToken.class);
        replaced.authenticate(sign(claims(), next));
    }
    @Test void issuerCreatesStablePurposeButNewTokenAndJtiPerAttempt() {
        var issuer = new WorkloadJwtCodec.Issuer(KEY.toJSONString(), "urn:tosunsaeng:workload:billing:test", CLOCK);
        String one = issuer.issue("recovery"), two = issuer.issue("recovery"); assertThat(one).isNotEqualTo(two);
        var decoder = new WorkloadJwtCodec(publicSet(KEY), "urn:tosunsaeng:workload:billing:test", CLOCK);
        assertThat(decoder.authenticate(one)).isEqualTo(new WorkloadJwtCodec.Principal("billing-service", "recovery"));
    }
    void check(String jwt, boolean allowed) {
        if (allowed) codec.authenticate(jwt);
        else assertThatThrownBy(() -> codec.authenticate(jwt)).isInstanceOf(WorkloadJwtCodec.InvalidToken.class)
                .hasMessage("Invalid workload token").hasNoCause();
    }
}
