package web.tosunsaeng.billing.global.config.security;

import java.net.URI;
import java.time.*;
import java.util.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.client.RestTemplate;
import web.tosunsaeng.billing.domain.entitlement.config.EntitlementQueryProperties;

/** Only the configured JWKS is consulted. Token-supplied jku/x5u are never followed. */
public final class IdentityUserJwtDecoder implements JwtDecoder {
    public static final class KeysUnavailable extends JwtException {
        public KeysUnavailable() { super("Trusted verification keys unavailable"); }
    }
    private final EntitlementQueryProperties properties;
    private final Clock clock;
    private final RestTemplate http;
    private Map<String, RSAKey> keys = Map.of();
    private Instant expiresAt = Instant.MIN;
    private Instant nextRefresh = Instant.MIN;
    private boolean upstreamUnavailable;
    public IdentityUserJwtDecoder(EntitlementQueryProperties properties, Clock clock) {
        this.properties = properties; this.clock = clock;
        var factory = new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(java.net.HttpURLConnection connection, String method) throws java.io.IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(1000); factory.setReadTimeout(1000);
        http = new RestTemplate(factory);
    }
    @Override public Jwt decode(String token) throws JwtException {
        try {
            if (token.length() > 16384) { throw new BadJwtException("Invalid token"); }
            var header = SignedJWT.parse(token).getHeader();
            if (!JWSAlgorithm.RS256.equals(header.getAlgorithm()) || !JOSEObjectType.JWT.equals(header.getType())
                    || header.getKeyID() == null || header.getKeyID().isBlank() || header.getKeyID().length() > 256) {
                throw new BadJwtException("Invalid token header");
            }
            RSAKey key = key(header.getKeyID());
            var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
            decoder.setJwtValidator(this::validate);
            return decoder.decode(token);
        } catch (JwtException e) { throw e; }
        catch (Exception e) { throw new BadJwtException("Invalid token"); }
    }
    private synchronized RSAKey key(String kid) {
        Instant now = clock.instant();
        if (now.isBefore(expiresAt) && keys.containsKey(kid)) { return keys.get(kid); }
        if (!now.isBefore(nextRefresh)) {
            nextRefresh = now.plusSeconds(30);
            try {
                byte[] body = http.execute(URI.create(properties.getJwksUri()), HttpMethod.GET, null, response -> {
                    if (!response.getStatusCode().is2xxSuccessful()) { throw new IllegalStateException("JWKS unavailable"); }
                    byte[] bytes = response.getBody().readNBytes(65537);
                    if (bytes.length > 65536) { throw new IllegalStateException("JWKS limit"); }
                    return bytes;
                });
                var set = JWKSet.parse(new String(Objects.requireNonNull(body), java.nio.charset.StandardCharsets.UTF_8));
                if (set.getKeys().size() > 20) { throw new IllegalStateException("JWKS limit"); }
                Map<String, RSAKey> replacement = new HashMap<>();
                for (var jwk : set.getKeys()) {
                    if (jwk instanceof RSAKey rsa && !rsa.isPrivate() && rsa.getKeyID() != null
                            && (rsa.getKeyUse() == null || KeyUse.SIGNATURE.equals(rsa.getKeyUse()))
                            && (rsa.getAlgorithm() == null || JWSAlgorithm.RS256.equals(rsa.getAlgorithm()))
                            && (rsa.getKeyOperations() == null || rsa.getKeyOperations().contains(KeyOperation.VERIFY))
                            && rsa.size() >= 2048) {
                        if (replacement.put(rsa.getKeyID(), rsa) != null) { throw new IllegalStateException("Duplicate verification key"); }
                    }
                }
                keys = Map.copyOf(replacement); expiresAt = now.plusSeconds(300); upstreamUnavailable = false;
            } catch (Exception e) { upstreamUnavailable = true; }
        }
        if (now.isBefore(expiresAt) && keys.containsKey(kid)) { return keys.get(kid); }
        if (upstreamUnavailable) { throw new KeysUnavailable(); }
        throw new BadJwtException("Invalid verification key");
    }
    private OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            Instant now = clock.instant(); Duration skew = properties.getClockSkew();
            Instant exp = jwt.getExpiresAt(), iat = jwt.getIssuedAt(), nbf = jwt.getNotBefore();
            String sub = jwt.getSubject();
            boolean valid = properties.getIssuer().equals(jwt.getClaimAsString("iss"))
                    && jwt.getAudience().contains("tosunsaeng-billing")
                    && exp != null && iat != null && exp.isAfter(iat)
                    && exp.plus(skew).isAfter(now) && !iat.isAfter(now.plus(skew))
                    && (nbf == null || !nbf.isAfter(now.plus(skew)))
                    && jwt.getClaims().get("jti") instanceof String jti && !jti.isBlank()
                    && sub != null && UUID.fromString(sub).toString().equals(sub)
                    && (jwt.getClaims().get("scope") == null || jwt.getClaims().get("scope") instanceof String);
            if (valid) { return OAuth2TokenValidatorResult.success(); }
        } catch (RuntimeException ignored) { /* No claim values or parser exceptions leave this boundary. */ }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid user token", null));
    }
}
