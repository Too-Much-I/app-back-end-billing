package web.tosunsaeng.billing.domain.attempt.domain.model;

public record AttemptGroupCompletionEvidence(
        boolean requiredFeedbackQueryable,
        boolean validScoreQueryable,
        boolean summaryQueryable,
        int evidenceVersion
) {
}
