package web.tosunsaeng.billing.domain.attempt.domain.model;

import java.time.Instant;

import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupEventTarget;
import web.tosunsaeng.billing.domain.attempt.domain.enums.AttemptGroupFailureCode;

public record AttemptGroupStatusEvent(
        String eventId,
        String eventType,
        int schemaVersion,
        String producer,
        Instant occurredAt,
        String userId,
        String attemptGroupId,
        String sessionId,
        AttemptGroupEventTarget targetStatus,
        AttemptGroupCompletionEvidence evidence,
        AttemptGroupFailureCode failureCode,
        String payloadDigest
) {
}
