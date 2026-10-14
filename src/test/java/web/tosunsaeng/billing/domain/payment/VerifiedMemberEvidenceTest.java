package web.tosunsaeng.billing.domain.payment;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import web.tosunsaeng.billing.domain.payment.application.VerifiedMemberEvidence;
import static org.assertj.core.api.Assertions.*;

class VerifiedMemberEvidenceTest {
    static final String USER = "11111111-1111-4111-8111-111111111111", ISSUER = "https://identity.test";
    static final Instant NOW = Instant.parse("2026-10-14T00:00:00.123456789Z");
    Jwt.Builder token() { return Jwt.withTokenValue("fake-not-stored").header("alg", "RS256").subject(USER).issuer(ISSUER)
            .audience(List.of("tosunsaeng-billing")).claim("account_type", "MEMBER").claim("scope", "billing:purchase")
            .issuedAt(NOW.minusSeconds(30)).expiresAt(NOW.plusSeconds(30)); }
    VerifiedMemberEvidence proof(Jwt token) { return VerifiedMemberEvidence.fromAuthenticated(token, ISSUER, "SANDBOX", Duration.ofSeconds(60), NOW); }
    @Test void evidenceHasOnlyAllowlistedFieldsAndPreservesNanoseconds() {
        var evidence = proof(token().build());
        assertThat(evidence.document()).hasSize(8);
        assertThat(evidence.document().getString("verifiedAt")).isEqualTo(NOW.toString());
        assertThat(evidence.document().toJson()).doesNotContain("fake-not-stored", "signature", "scope");
        assertThat(evidence.toString()).isEqualTo("VerifiedMemberEvidence[REDACTED]");
        assertThat(VerifiedMemberEvidence.matches(evidence.document(), USER, "SANDBOX", ISSUER)).isTrue();
        assertThat(VerifiedMemberEvidence.matches(evidence.document(), USER, "PRODUCTION", ISSUER)).isFalse();
        assertThat(VerifiedMemberEvidence.matches(evidence.document(), UUID.randomUUID().toString(), "SANDBOX", ISSUER)).isFalse();
    }
    @Test void guestWrongIssuerScopeAudienceAndExpiredTokenAreRejected() {
        for (Jwt jwt : List.of(token().claim("account_type", "GUEST").build(), token().issuer("https://other.test").build(),
                token().claim("scope", "billing:read").build(), token().audience(List.of("other")).build(),
                token().issuedAt(NOW.minusSeconds(180)).expiresAt(NOW.minusSeconds(60)).build(), token().issuedAt(NOW.plusSeconds(61)).expiresAt(NOW.plusSeconds(90)).build()))
            assertThatThrownBy(() -> proof(jwt)).hasMessage("UNAUTHENTICATED");
    }
    @Test void malformedHistoricalProofIsNotMembershipAndTimeIsNotLiveActiveProof() {
        var row = proof(token().build()).document();
        row.put("tokenIssuedAt", "not-a-time");
        assertThat(VerifiedMemberEvidence.matches(row, USER, "SANDBOX", ISSUER)).isFalse();
        row = proof(token().build()).document(); row.put("rawToken", "fake");
        assertThat(VerifiedMemberEvidence.matches(row, USER, "SANDBOX", ISSUER)).isFalse();
        // No current-time input in historical evidence matching; lifecycle is checked independently by the transaction.
        row = proof(token().build()).document(); row.put("verifiedAt", "2020-01-01T00:00:00Z");
        assertThat(VerifiedMemberEvidence.matches(row, USER, "SANDBOX", ISSUER)).isTrue();
    }
}
