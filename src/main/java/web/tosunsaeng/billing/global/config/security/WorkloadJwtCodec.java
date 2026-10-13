package web.tosunsaeng.billing.global.config.security;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;

/** Local deployed keys only. Authentication does not grant route/audience/subject authority. */
public final class WorkloadJwtCodec {
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private final Map<String, RSAKey> keys;
    private final String issuer;
    private final Clock clock;
    public record Principal(String subject, String audience) { }
    public static final class InvalidToken extends RuntimeException {
        public InvalidToken() { super("Invalid workload token"); }
    }
    public WorkloadJwtCodec(String publicJwkSet, String issuer, Clock clock) {
        this.issuer = issuer; this.clock = clock;
        try {
            var root = parse(publicJwkSet.getBytes(StandardCharsets.UTF_8), 65536);
            check(root.size() == 1 && root.path("keys").isArray() && !root.path("keys").isEmpty() && root.path("keys").size() <= 20);
            Map<String, RSAKey> result = new HashMap<>();
            for (var node : root.path("keys")) {
                check(Set.of("kty", "n", "e", "kid", "alg", "use", "key_ops").containsAll(fields(node)));
                RSAKey key = RSAKey.parse(node.toString()); validateKey(key, false);
                check(result.put(key.getKeyID(), key) == null);
            }
            keys = Map.copyOf(result);
        } catch (Exception invalid) { throw new IllegalStateException("Invalid workload public key configuration"); }
    }
    public Principal authenticate(String token) {
        try {
            check(token != null && token.length() <= 16384);
            String[] parts = token.split("\\.", -1); check(parts.length == 3);
            for (String part : parts) check(part.matches("[A-Za-z0-9_-]+"));
            JsonNode header = parse(Base64.getUrlDecoder().decode(parts[0]), 16384);
            check(fields(header).equals(Set.of("alg", "typ", "kid")));
            check(text(header, "alg").equals("RS256") && text(header, "typ").equals("JWT"));
            String kid = text(header, "kid"); RSAKey key = keys.get(kid); check(key != null);
            JWSObject jws = JWSObject.parse(token); check(jws.verify(new RSASSAVerifier(key.toRSAPublicKey())));
            JsonNode claims = parse(Base64.getUrlDecoder().decode(parts[1]), 16384);
            check(issuer.equals(text(claims, "iss")));
            String subject = text(claims, "sub"), audience = text(claims, "aud"), jti = text(claims, "jti");
            UUID id = UUID.fromString(jti); check(id.toString().equals(jti) && id.version() == 4 && id.variant() == 2);
            long iat = number(claims, "iat"), exp = number(claims, "exp");
            long ttl = Math.subtractExact(exp, iat); check(ttl >= 1 && ttl <= 60);
            Instant now = clock.instant();
            check(!Instant.ofEpochSecond(iat).isAfter(now.plusSeconds(30)) && now.isBefore(Instant.ofEpochSecond(exp).plusSeconds(30)));
            if (claims.has("nbf")) {
                long nbf = number(claims, "nbf");
                check(iat <= nbf && nbf < exp && !Instant.ofEpochSecond(nbf).isAfter(now.plusSeconds(30)));
            }
            return new Principal(subject, audience);
        } catch (Exception invalid) { throw new InvalidToken(); }
    }
    public static final class Issuer {
        private final RSAKey key;
        private final String issuer;
        private final Clock clock;
        public Issuer(String privateJwk, String issuer, Clock clock) {
            this.issuer = issuer; this.clock = clock;
            try {
                JsonNode node = parse(privateJwk.getBytes(StandardCharsets.UTF_8), 65536);
                check(Set.of("kty", "n", "e", "d", "p", "q", "dp", "dq", "qi", "kid", "alg", "use", "key_ops").containsAll(fields(node)));
                key = RSAKey.parse(node.toString()); validateKey(key, true);
                // A mismatched public/private pair must fail startup, not produce unusable tokens.
                JWSObject probe = new JWSObject(new JWSHeader(JWSAlgorithm.RS256), new Payload("key-check"));
                probe.sign(new RSASSASigner(key)); check(probe.verify(new RSASSAVerifier(key.toRSAPublicKey())));
            } catch (Exception invalid) { throw new IllegalStateException("Invalid workload signing key configuration"); }
        }
        public String issue(String audience) {
            try {
                long now = clock.instant().getEpochSecond(); check(now >= 0);
                var claims = JSON.createObjectNode().put("iss", issuer).put("sub", "billing-service").put("aud", audience)
                        .put("iat", now).put("exp", Math.addExact(now, 60)).put("jti", UUID.randomUUID().toString());
                var token = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(key.getKeyID()).build(), new Payload(claims.toString()));
                token.sign(new RSASSASigner(key)); return token.serialize();
            } catch (Exception invalid) { throw new InvalidToken(); }
        }
    }
    private static void validateKey(RSAKey key, boolean signing) {
        check(key.size() >= 2048 && key.isPrivate() == signing && key.getKeyID() != null && !key.getKeyID().isBlank()
                && key.getKeyID().length() <= 256 && JWSAlgorithm.RS256.equals(key.getAlgorithm())
                && KeyUse.SIGNATURE.equals(key.getKeyUse())
                && Set.of(signing ? KeyOperation.SIGN : KeyOperation.VERIFY).equals(key.getKeyOperations()));
        check(key.getModulus().decodeToBigInteger().testBit(0)
                && key.getPublicExponent().decodeToBigInteger().compareTo(java.math.BigInteger.valueOf(3)) >= 0
                && key.getPublicExponent().decodeToBigInteger().testBit(0));
    }
    private static JsonNode parse(byte[] bytes, int max) throws Exception {
        check(bytes.length > 0 && bytes.length <= max);
        String input = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        check(!input.startsWith("\uFEFF"));
        try (var parser = JSON.createParser(input)) {
            JsonNode node = JSON.readTree(parser); check(node != null && node.isObject() && parser.nextToken() == null); return node;
        }
    }
    private static Set<String> fields(JsonNode node) { Set<String> names = new HashSet<>(); node.fieldNames().forEachRemaining(names::add); return names; }
    private static String text(JsonNode node, String name) { var value = node.get(name); check(value != null && value.isTextual() && !value.textValue().isBlank()); return value.textValue(); }
    private static long number(JsonNode node, String name) { var value = node.get(name); check(value != null && value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0); return value.longValue(); }
    private static void check(boolean valid) { if (!valid) throw new InvalidToken(); }
}
