package web.tosunsaeng.billing.domain.reservation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import web.tosunsaeng.billing.domain.attempt.domain.entity.AttemptGroup;
import web.tosunsaeng.billing.domain.attempt.repository.AttemptGroupRepository;
import web.tosunsaeng.billing.domain.eligibility.trial.config.TrialEligibilityProperties;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.BillingSubjectLink;
import web.tosunsaeng.billing.domain.entitlement.trial.domain.entity.TrialClaim;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.BillingSubjectLinkRepository;
import web.tosunsaeng.billing.domain.entitlement.trial.repository.TrialClaimRepository;
import web.tosunsaeng.billing.domain.reservation.domain.entity.Reservation;
import web.tosunsaeng.billing.domain.reservation.exception.ReservationException;

@Service
public class PhoneContinuationService {

    private static final int MAX_LINKS = 101;

    private final BillingSubjectLinkRepository subjectLinkRepository;
    private final AttemptGroupRepository attemptGroupRepository;
    private final TrialClaimRepository trialClaimRepository;
    private final TrialEligibilityProperties eligibilityProperties;
    private final Clock clock;

    public PhoneContinuationService(
            BillingSubjectLinkRepository subjectLinkRepository,
            AttemptGroupRepository attemptGroupRepository,
            TrialClaimRepository trialClaimRepository,
            TrialEligibilityProperties eligibilityProperties,
            Clock clock
    ) {
        this.subjectLinkRepository = subjectLinkRepository;
        this.attemptGroupRepository = attemptGroupRepository;
        this.trialClaimRepository = trialClaimRepository;
        this.eligibilityProperties = eligibilityProperties;
        this.clock = clock;
    }

    public Optional<PhoneContinuationResult> resolve(String userId) {
        Instant now = clock.instant();
        List<BillingSubjectLink> links = subjectLinkRepository.findActiveByOwnerAndScope(
                userId, eligibilityProperties.getExpectedConsumerScopeId(), now, MAX_LINKS
        );
        if (links.size() >= MAX_LINKS) {
            throw ReservationException.temporarilyUnavailable();
        }

        List<PhoneContinuationResult> matches = new ArrayList<>();
        for (BillingSubjectLink link : links) {
            if (!"PHONE_REJOIN".equals(link.getOwnerTransitionReason())
                    || link.getOwnerTransitionId() == null) {
                continue;
            }
            AttemptGroup group = attemptGroupRepository
                    .findNonTerminalBySubject(link.getSubjectRefId())
                    .orElse(null);
            if (group == null) {
                continue;
            }
            if (!link.getTrialClaimId().equals(group.getTrialClaimId())) {
                throw ReservationException.temporarilyUnavailable();
            }
            TrialClaim claim = trialClaimRepository.findById(link.getTrialClaimId())
                    .orElseThrow(ReservationException::temporarilyUnavailable);
            if (claim.getState() != TrialClaim.State.ACTIVE
                    || !claim.getRetentionExpiresAt().isAfter(now)
                    || !link.getSubjectRefId().equals(claim.getSubjectRefId())) {
                throw ReservationException.temporarilyUnavailable();
            }
            if (group.getStatus() == AttemptGroup.Status.GRADING) {
                throw ReservationException.commandProcessing();
            }
            if (group.getStatus() != AttemptGroup.Status.OPEN
                    && group.getStatus() != AttemptGroup.Status.RETAKE_AVAILABLE) {
                throw ReservationException.temporarilyUnavailable();
            }
            matches.add(new PhoneContinuationResult(
                    Reservation.ContinuationReason.PHONE_REJOIN,
                    link.getOwnerTransitionId(), group.getAttemptGroupId(), group.getMockExamId()
            ));
        }
        if (matches.size() > 1) {
            throw ReservationException.temporarilyUnavailable();
        }
        return matches.stream().findFirst();
    }
}
