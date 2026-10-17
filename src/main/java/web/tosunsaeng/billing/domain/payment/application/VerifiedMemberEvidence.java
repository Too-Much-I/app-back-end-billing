package web.tosunsaeng.billing.domain.payment.application;

import java.time.*;
import java.util.*;
import org.bson.Document;
import org.springframework.security.oauth2.jwt.Jwt;
import web.tosunsaeng.billing.domain.payment.exception.PaymentException;

/** Construct only after the Identity decoder and route scope checks have authenticated the JWT. */
public final class VerifiedMemberEvidence {
    private final String userId, environment, issuer;
    private final Instant issuedAt, verifiedAt, authorizationDeadline;
    private VerifiedMemberEvidence(String userId, String environment, String issuer, Instant issuedAt,
            Instant verifiedAt, Instant authorizationDeadline) {
        this.userId = userId; this.environment = environment; this.issuer = issuer;
        this.issuedAt = issuedAt; this.verifiedAt = verifiedAt; this.authorizationDeadline = authorizationDeadline;
    }
    public static VerifiedMemberEvidence fromAuthenticated(Jwt jwt, String trustedIssuer, String environment,
            Duration skew, Instant now) {
        if (skew == null || skew.isNegative() || skew.compareTo(Duration.ofSeconds(60)) > 0 || trustedIssuer == null || trustedIssuer.isBlank() || !Set.of("SANDBOX", "PRODUCTION").contains(environment))
            throw PaymentException.unavailable();
        if (jwt == null || !trustedIssuer.equals(jwt.getClaimAsString("iss"))
                || !"MEMBER".equals(jwt.getClaims().get("account_type"))
                || jwt.getSubject() == null || !jwt.getSubject().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || !(jwt.getClaims().get("scope") instanceof String scope) || !Arrays.asList(scope.split(" ")).contains("billing:purchase")
                || !jwt.getAudience().contains("tosunsaeng-billing")
                || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null
                || !jwt.getIssuedAt().isBefore(jwt.getExpiresAt())
                || jwt.getIssuedAt().isAfter(now.plus(skew)) || !now.isBefore(jwt.getExpiresAt().plus(skew)))
            throw new PaymentException(401, "UNAUTHENTICATED");
        return new VerifiedMemberEvidence(jwt.getSubject(), environment, trustedIssuer, jwt.getIssuedAt(), now,
                PurchaseTokenLifetime.deadline(jwt, skew));
    }
    public String userId() { return userId; }
    public String environment() { return environment; }
    public Instant authorizationDeadline() { return authorizationDeadline; }
    public Document document() {
        return new Document("source", "IDENTITY_USER_JWT").append("evidenceSchemaVersion", 1L)
                .append("userId", userId).append("environment", environment).append("issuer", issuer)
                .append("accountType", "MEMBER").append("tokenIssuedAt", issuedAt.toString()).append("verifiedAt", verifiedAt.toString());
    }
    public static boolean matches(Document evidence, String userId, String environment, String issuer) {
        if (evidence == null) return false;
        try {
            return evidence.keySet().equals(Set.of("source", "evidenceSchemaVersion", "userId", "environment", "issuer", "accountType", "tokenIssuedAt", "verifiedAt"))
                    && "IDENTITY_USER_JWT".equals(evidence.getString("source")) && Long.valueOf(1).equals(evidence.get("evidenceSchemaVersion"))
                    && userId.equals(evidence.getString("userId")) && environment.equals(evidence.getString("environment"))
                    && issuer.equals(evidence.getString("issuer")) && "MEMBER".equals(evidence.getString("accountType"))
                    && Instant.parse(evidence.getString("tokenIssuedAt")) != null && Instant.parse(evidence.getString("verifiedAt")) != null;
        } catch (RuntimeException malformed) { return false; }
    }
    @Override public String toString() { return "VerifiedMemberEvidence[REDACTED]"; }
}
