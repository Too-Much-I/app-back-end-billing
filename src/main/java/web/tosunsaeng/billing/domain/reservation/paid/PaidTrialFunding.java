package web.tosunsaeng.billing.domain.reservation.paid;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


import web.tosunsaeng.billing.domain.benefit.application.BenefitCatalog;
import web.tosunsaeng.billing.domain.benefit.domain.entity.BenefitDefinition;
import web.tosunsaeng.billing.domain.reservation.exception.ReservationException;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.BillingSubjectLink;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementGrant;
import web.tosunsaeng.billing.domain.entitlement.domain.entity.EntitlementLedgerEntry;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialCandidateAlias;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialClaim;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.BillingSubjectLinkRepository;
import web.tosunsaeng.billing.domain.entitlement.repository.EntitlementGrantRepository;
import web.tosunsaeng.billing.domain.entitlement.repository.EntitlementLedgerRepository;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.TrialCandidateAliasRepository;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.TrialClaimRepository;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibility;
import web.tosunsaeng.billing.domain.eligibility.trial.domain.entity.TrialEligibilityCandidate;
import web.tosunsaeng.billing.domain.entitlement.trial.application.TrialEntitlementPolicy;
import web.tosunsaeng.billing.domain.eligibility.trial.repository.TrialEligibilityRepository;

/** Same-session trial issuance; reuses the existing trial policy and repositories. */
final class PaidTrialFunding {
    private final TrialClaimRepository claimRepository;
    private final TrialCandidateAliasRepository aliasRepository;
    private final BillingSubjectLinkRepository subjectLinkRepository;
    private final EntitlementGrantRepository grantRepository;
    private final EntitlementLedgerRepository ledgerRepository;
    private final BenefitCatalog benefitCatalog;
    private final org.springframework.data.mongodb.core.MongoOperations mongo;
    PaidTrialFunding(org.springframework.data.mongodb.core.MongoOperations mongo) {
        this.mongo = mongo;
        claimRepository = new TrialClaimRepository(mongo); aliasRepository = new TrialCandidateAliasRepository(mongo);
        subjectLinkRepository = new BillingSubjectLinkRepository(mongo); grantRepository = new EntitlementGrantRepository(mongo);
        ledgerRepository = new EntitlementLedgerRepository(mongo);
        benefitCatalog = new BenefitCatalog(new web.tosunsaeng.billing.domain.benefit.repository.BenefitDefinitionRepository(mongo));
    }
    ClaimContext resolve(String scope, String userId, Instant now, ReserveIds ids) {
        TrialEligibility eligibility = new TrialEligibilityRepository(mongo).findByScopeAndUser(scope, userId)
                .filter(TrialEntitlementPolicy::verified).orElseThrow(ReservationException::entitlementInsufficient);
        return resolveClaim(eligibility, userId, now, ids);
    }
    private ClaimContext resolveClaim(
            TrialEligibility eligibility,
            String userId,
            Instant now,
            ReserveIds ids
    ) {
        List<TrialEligibilityCandidate> candidates = eligibility.getCandidates();
        BenefitDefinition definition = benefitCatalog.findActiveFreeExamOnce()
                .orElseThrow(ReservationException::temporarilyUnavailable);
        String benefitCode = definition.getBenefitCode();
        aliasRepository.deactivateExpiredMatches(benefitCode, candidates, now);
        List<TrialCandidateAlias> matches = aliasRepository.findActiveMatches(
                benefitCode, candidates, now
        );
        Set<String> claimIds = new HashSet<>();
        matches.forEach(alias -> claimIds.add(alias.getTrialClaimId()));
        if (claimIds.size() > 1) {
            throw ReservationException.temporarilyUnavailable();
        }
        if (claimIds.size() == 1) {
            String claimId = claimIds.iterator().next();
            TrialClaim claim = claimRepository.findById(claimId)
                    .filter(value -> value.getState() == TrialClaim.State.ACTIVE)
                    .filter(value -> TrialEntitlementPolicy.retained(value.getRetentionExpiresAt(), now))
                    .orElseThrow(ReservationException::temporarilyUnavailable);
            if (!benefitCode.equals(claim.getBenefitCode())) {
                throw ReservationException.temporarilyUnavailable();
            }
            BillingSubjectLink link = subjectLinkRepository.findByClaim(claimId)
                    .filter(BillingSubjectLink::isActive)
                    .filter(value -> TrialEntitlementPolicy.retained(value.getRetentionExpiresAt(), now))
                    .orElseThrow(ReservationException::temporarilyUnavailable);
            if (!link.getUserId().equals(userId)) {
                throw ReservationException.entitlementInsufficient();
            }
            addMissingAliases(candidates, claim, benefitCode, now);
            EntitlementGrant grant = grantRepository.findByClaimAndBenefitCode(
                            claimId, benefitCode
                    )
                    .orElseThrow(ReservationException::temporarilyUnavailable);
            if (!TrialEntitlementPolicy.matchesDefinition(definition, grant) || !TrialEntitlementPolicy.validUnits(grant))
                throw ReservationException.temporarilyUnavailable();
            return new ClaimContext(claim, link, grant);
        }

        Instant retentionExpiresAt = now.atZone(ZoneOffset.UTC).plusYears(3).toInstant();
        TrialClaim claim = TrialClaim.active(
                ids.trialClaimId(), benefitCode, ids.subjectRefId(),
                eligibility.getLastEventId(), now, retentionExpiresAt
        );
        claimRepository.insert(claim);
        BillingSubjectLink link = subjectLinkRepository.insert(BillingSubjectLink.active(
                ids.subjectRefId(), ids.trialClaimId(), eligibility.getConsumerScopeId(),
                userId, now, retentionExpiresAt
        ));
        for (TrialEligibilityCandidate candidate : candidates) {
            aliasRepository.insert(TrialCandidateAlias.active(
                    uuid(), benefitCode, candidate.keyVersion(), candidate.value(),
                    ids.trialClaimId(), now, retentionExpiresAt
            ));
        }
        EntitlementGrant grant = EntitlementGrant.unitGrant(
                ids.grantId(), benefitCode, "TRIAL_CLAIM", ids.trialClaimId(),
                ids.subjectRefId(), definition.getDefaultGrantUnits(), now
        );
        grantRepository.insert(grant);
        ledgerRepository.insert(EntitlementLedgerEntry.granted(
                ids.grantedLedgerId(), ids.grantId(), ids.subjectRefId(),
                ids.trialClaimId(), now
        ));
        return new ClaimContext(claim, link, grant);
    }

    private void addMissingAliases(
            List<TrialEligibilityCandidate> candidates,
            TrialClaim claim,
            String benefitCode,
            Instant now
    ) {
        Set<CandidateKey> existing = new HashSet<>();
        aliasRepository.findActiveByClaim(claim.getTrialClaimId(), benefitCode, now)
                .forEach(alias -> existing.add(new CandidateKey(
                        alias.getKeyVersion(), alias.getCandidate()
                )));
        for (TrialEligibilityCandidate candidate : candidates) {
            CandidateKey key = new CandidateKey(candidate.keyVersion(), candidate.value());
            if (!existing.contains(key)) {
                aliasRepository.insert(TrialCandidateAlias.active(
                        uuid(), benefitCode, candidate.keyVersion(), candidate.value(),
                        claim.getTrialClaimId(), now, claim.getRetentionExpiresAt()
                ));
            }
        }
    }

    private static String uuid() { return UUID.randomUUID().toString(); }
    record ClaimContext(
            TrialClaim claim,
            BillingSubjectLink link,
            EntitlementGrant grant
    ) {
        String subjectRefId() {
            return link.getSubjectRefId();
        }
    }

    private record CandidateKey(String keyVersion, String candidate) {
    }

    record ReserveIds(String trialClaimId, String subjectRefId, String grantId, String grantedLedgerId) {
        static ReserveIds create() { return new ReserveIds(uuid(), uuid(), uuid(), uuid()); }
    }
}
