package web.tosunsaeng.billing.domain.promotion;

public record PromotionEligibilityResponse(String userId, boolean eligible, String checkedAt) {
    @Override public String toString() { return "PromotionEligibilityResponse[REDACTED]"; }
}
