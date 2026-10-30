package web.tosunsaeng.billing.global.config;

import java.net.*;
import java.net.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import web.tosunsaeng.billing.domain.reward.ReferralRewardProperties;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true",
        "billing.payment.environment=SANDBOX", "billing.entitlement-query.public-connector-enabled=true"
})
class ReferralDocumentationTest {
    static final int PORT = freePort();
    @LocalServerPort int internalPort;
    @Autowired ObjectMapper json;
    @Autowired ReferralRewardProperties referrals;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("billing.entitlement-query.public-port", () -> PORT);
    }
    static int freePort() {
        try (var socket = new java.net.ServerSocket(0)) { return socket.getLocalPort(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    HttpResponse<String> get(int port, String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void publicSwaggerHasOnlyExactReferralContractAndLocalAssets() throws Exception {
        var response = get(PORT, "/v3/api-docs");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        var doc = json.readTree(response.body());
        assertThat(doc.path("paths").size()).isEqualTo(2);
        var issue = doc.at("/paths/~1api~1v1~1rewards~1referral-code/post");
        // Use the exact URL pointer independently of source constants to catch accidental route changes.
        var apply = doc.path("paths").path("/api/v1/rewards/referrals").path("post");
        assertThat(issue.has("requestBody")).isFalse();
        assertThat(apply.path("requestBody").path("required").asBoolean()).isTrue();
        assertThat(apply.path("parameters").get(0).path("name").asText()).isEqualTo("Idempotency-Key");
        assertThat(apply.at("/responses/200/content/application~1json/schema/properties/result/properties/referrerAwardedQuantity").isMissingNode()).isFalse();
        assertThat(doc.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(response.body()).doesNotContain("/internal/", "principal", "HttpServletRequest");
        assertThat(get(PORT, "/swagger-ui/index.html").statusCode()).isEqualTo(200);
        assertThat(get(PORT, "/swagger-ui/swagger-ui-bundle.js").statusCode()).isEqualTo(200);
        var config = json.readTree(get(PORT, "/v3/api-docs/swagger-config").body());
        assertThat(config.path("persistAuthorization").asBoolean()).isFalse();
        assertThat(config.path("url").asText()).isEqualTo("/v3/api-docs");
    }
    @Test void documentationDoesNotOpenOtherPortsOrBusinessAuthentication() throws Exception {
        assertThat(get(internalPort, "/v3/api-docs").statusCode()).isEqualTo(404);
        assertThat(get(internalPort, "/swagger-ui/index.html").statusCode()).isEqualTo(404);
        assertThat(get(PORT, "/internal/v1/reservations").statusCode()).isEqualTo(404);
        referrals.setEnabled(true);
        try {
            var result = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + PORT + "/api/v1/rewards/referral-code"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(result.statusCode()).isEqualTo(401);
        } finally { referrals.setEnabled(false); }
    }
}
