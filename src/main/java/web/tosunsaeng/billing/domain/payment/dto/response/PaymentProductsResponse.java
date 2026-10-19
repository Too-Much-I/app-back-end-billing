package web.tosunsaeng.billing.domain.payment.dto.response;

import java.util.List;

public record PaymentProductsResponse(long catalogVersion, List<Product> products) {
    public record Product(String offerCode, long durationSeconds, boolean autoRenew, List<StoreProduct> storeProducts) {}
    public record StoreProduct(String store, String productId, String packageId) {}
}
