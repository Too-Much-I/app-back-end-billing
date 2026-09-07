package web.tosunsaeng.billing.global.config.security;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtException;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityUserJwtDecoderTest {
    static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");
    static final String USER = "e8b37a41-bae6-47f1-a770-052e6c5786d4";
    final Clock clock = mock(Clock.class);
    final AtomicInteger requests = new AtomicInteger();
    HttpServer server;
    RSAKey key;
    IdentityUserJwtDecoder decoder;
    volatile String keyJson;
    volatile int httpStatus = 200;
    @BeforeEach void setUp() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        keyJson = new JWKSet(key.toPublicJWK()).toString();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            requests.incrementAndGet(); byte[] bytes = keyJson.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(httpStatus, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        var p = new EntitlementQueryProperties(); p.setIssuer("https://identity.test");
        // HTTP fixture is injected directly into the decoder; production configuration only permits HTTPS.
        p.setJwksUri("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks");
        decoder = new IdentityUserJwtDecoder(p, clock);
    }
    @AfterEach void close() { server.stop(0); }
    @Test void acceptsGuestAndMultipleAudiences() throws Exception {
        var token = decoder.decode(sign(claims().claim("account_type", "GUEST").build(), key, "test-key", JOSEObjectType.JWT));
        assertThat(token.getSubject()).isEqualTo(USER);
        assertThat(requests.get()).isOne();
    }
    @ParameterizedTest @ValueSource(strings = {"issuer", "aud", "sub", "exp", "iat", "nbf", "jti", "scope", "missingExp", "missingIat"})
    void rejectsInvalidRequiredClaims(String type) throws Exception {
        var c = claims();
        switch (type) {
            case "issuer" -> c.issuer("https://other.test");
            case "aud" -> c.audience("tosunsaeng-learning-core");
            case "sub" -> c.subject(USER.toUpperCase());
            case "exp" -> c.expirationTime(Date.from(NOW.minusSeconds(61)));
            case "iat" -> c.issueTime(Date.from(NOW.plusSeconds(61)));
            case "nbf" -> c.notBeforeTime(Date.from(NOW.plusSeconds(61)));
            case "jti" -> c.jwtID(null);
            case "scope" -> c.claim("scope", List.of("billing:read"));
            case "missingExp" -> c.expirationTime(null);
            case "missingIat" -> c.issueTime(null);
        }
        String token = sign(c.build(), key, "test-key", JOSEObjectType.JWT);
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void missingKidWrongTypeAndWrongSignatureAreRejected() throws Exception {
        for (String token : List.of(sign(claims().build(), key, null, JOSEObjectType.JWT),
                sign(claims().build(), key, "test-key", new JOSEObjectType("workload")),
                sign(claims().build(), new RSAKeyGenerator(2048).generate(), "test-key", JOSEObjectType.JWT))) {
            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        }
    }
    @Test void unsignedAndHmacAlgorithmsCannotUseTheUserRoute() throws Exception {
        var hmac = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).keyID("test-key").build(), claims().build());
        hmac.sign(new com.nimbusds.jose.crypto.MACSigner(new byte[32]));
        assertThatThrownBy(() -> decoder.decode(hmac.serialize())).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(new PlainJWT(claims().build()).serialize())).isInstanceOf(JwtException.class);
        assertThat(requests.get()).isZero();
    }
    @Test void rotationUnknownKidAndOutageHaveBoundedRefresh() throws Exception {
        String original = sign(claims().build(), key, "test-key", JOSEObjectType.JWT);
        decoder.decode(original);
        var rotated = new RSAKeyGenerator(2048).keyID("rotated").generate();
        keyJson = new JWKSet(List.of(key.toPublicJWK(), rotated.toPublicJWK())).toString();
        when(clock.instant()).thenReturn(NOW.plusSeconds(31));
        decoder.decode(sign(claims().build(), rotated, "rotated", JOSEObjectType.JWT));
        String unknown = sign(claims().build(), key, "unknown", JOSEObjectType.JWT);
        for (int i = 0; i < 10; i++) { assertThatThrownBy(() -> decoder.decode(unknown)).isInstanceOf(JwtException.class); }
        assertThat(requests.get()).isEqualTo(2);
        httpStatus = 503;
        decoder.decode(original); // Still-valid cached key.
        when(clock.instant()).thenReturn(NOW.plusSeconds(400));
        assertThatThrownBy(() -> decoder.decode(original)).isInstanceOf(IdentityUserJwtDecoder.KeysUnavailable.class);
    }
    JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer("https://identity.test").subject(USER)
                .audience(List.of("tosunsaeng-learning-core", "tosunsaeng-billing"))
                .issueTime(Date.from(NOW.minusSeconds(10))).expirationTime(Date.from(NOW.plusSeconds(900)))
                .jwtID("test-jti").claim("scope", "exam:read billing:read");
    }
    static String sign(JWTClaimsSet claims, RSAKey key, String kid, JOSEObjectType type) throws Exception {
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(type).keyID(kid).build(), claims);
        jwt.sign(new RSASSASigner(key)); return jwt.serialize();
    }
}
