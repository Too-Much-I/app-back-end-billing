package web.tosunsaeng.billing.domain.payment.application;

import java.util.*;
import org.bson.Document;
import org.springframework.stereotype.Service;
import web.tosunsaeng.billing.domain.payment.config.PaymentProperties;
import web.tosunsaeng.billing.domain.payment.domain.PaymentOffer;
import web.tosunsaeng.billing.domain.payment.dto.response.PaymentProductsResponse;
import web.tosunsaeng.billing.domain.payment.dto.response.PaymentProductsResponse.*;
import web.tosunsaeng.billing.domain.payment.repository.PaymentStore;
import static web.tosunsaeng.billing.domain.payment.exception.PaymentException.require;

@Service
public class PaymentCatalogService {
    private final PaymentStore store;
    private final PaymentProperties properties;
    public PaymentCatalogService(PaymentStore store, PaymentProperties properties) { this.store = store; this.properties = properties; }
    public PaymentProductsResponse read() {
        return store.transaction(session -> convert(store.products(session, properties.getEnvironment())));
    }
    public PaymentProductsResponse convert(List<Document> rows) {
        require(rows.size() <= 10);
        Map<PaymentOffer, Map<String, StoreProduct>> grouped = new EnumMap<>(PaymentOffer.class);
        Set<String> providerIds = new HashSet<>(), rcIds = new HashSet<>();
        for (var row : rows) {
            require(properties.getEnvironment().equals(row.getString("environment")) && Boolean.TRUE.equals(row.getBoolean("saleEnabled")));
            PaymentOffer offer = PaymentOffer.valueOf(row.getString("offerCode"));
            require(integer(row, "catalogVersion") == properties.getCatalogVersion()
                    && integer(row, "durationSeconds") == offer.durationSeconds
                    && integer(row, "referencePriceKrw") == offer.referencePriceKrw
                    && "KRW".equals(row.getString("referenceCurrency")));
            String platform = text(row, "store");
            require(Set.of("APP_STORE", "PLAY_STORE").contains(platform));
            String product = text(row, "storeProductId"), app = text(row, "appId");
            require(providerIds.add(platform + "\u0000" + app + "\u0000" + product) && rcIds.add(text(row, "rcProductId")));
            var mappings = grouped.computeIfAbsent(offer, ignored -> new TreeMap<>());
            require(mappings.put(platform, new StoreProduct(platform, product, text(row, "packageId"))) == null);
        }
        List<Product> products = new ArrayList<>();
        grouped.forEach((offer, mappings) -> products.add(new Product(offer.name(), offer.durationSeconds, false, List.copyOf(mappings.values()))));
        return new PaymentProductsResponse(properties.getCatalogVersion(), List.copyOf(products));
    }
    private static long integer(Document row, String key) {
        Object value = row.get(key);
        require(value instanceof Long || value instanceof Integer);
        return ((Number) value).longValue();
    }
    private static String text(Document row, String key) {
        Object value = row.get(key);
        require(value instanceof String s && !s.isBlank() && s.length() <= 256);
        return (String) value;
    }
}
