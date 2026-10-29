package web.tosunsaeng.billing.global.config;

import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;
import io.swagger.v3.oas.models.responses.*;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import web.tosunsaeng.billing.global.config.security.PublicIngressFilter;

/** Explicit public contract: never publish inferred internal handlers or servlet/JWT parameters. */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class ReferralOpenApiConfiguration {
    @Bean
    OpenApiCustomizer referralDocumentation() {
        return api -> {
            var issued = new ObjectSchema().addProperty("code", new StringSchema().pattern("^[A-HJ-NP-Z2-9]{10}$").example("ABCDEFGHJK"))
                    .required(List.of("code"));
            var applied = new ObjectSchema().addProperty("applicationId", new StringSchema().format("uuid"))
                    .addProperty("applied", new BooleanSchema().example(true))
                    .addProperty("applicantAwardedQuantity", new IntegerSchema().example(1))
                    .addProperty("referrerAwardedQuantity", new IntegerSchema().example(1))
                    .required(List.of("applicationId", "applied", "applicantAwardedQuantity", "referrerAwardedQuantity"));
            var issue = operation("issueReferralCode", "내 추천 코드 발급·재조회",
                    "Body 없음: {}나 공백도 보내지 않습니다. Idempotency-Key 불필요. 최초 발급 이후 같은 코드를 반환하며 발급만으로 보상은 지급하지 않습니다.", issued);
            var apply = operation("applyReferralCode", "추천 코드 적용·양쪽 무료권 지급",
                    "입력자 계정당 1회, 자기 추천 금지. 추천인 상한 없음. 성공 시 양쪽 무료 응시권 1개씩 지급합니다. "
                    + "200은 최초 지급 또는 기존 성공 재현이며 수량은 현재 잔여권이 아닙니다. "
                    + "같은 key/같은 정규화 code는 재지급 없이 200, 같은 key/다른 code는 409 IDEMPOTENCY_KEY_CONFLICT. "
                    + "다른 key/같은 code는 기존 200, 다른 key/다른 code는 409 REFERRAL_ALREADY_APPLIED. "
                    + "429·503·응답 유실에는 같은 계정/key/body로 재시도합니다. 4xx 실패는 key를 예약하지 않습니다. "
                    + "추천 실패로 회원가입을 취소하지 않습니다.", applied);
            apply.addParametersItem(new HeaderParameter().name("Idempotency-Key").required(true)
                    .description("단일 lowercase UUID v4. 미확인 요청의 key/body를 계정별 보관하며 다른 계정으로 재전송하지 않습니다.")
                    .schema(new StringSchema().pattern("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"))
                    .example("33333333-3333-4333-8333-333333333333"));
            var body = new ObjectSchema().addProperty("code", new StringSchema().maxLength(64).example("ABCDEFGHJK"))
                    .required(List.of("code")).additionalProperties(false);
            apply.requestBody(new RequestBody().required(true).description("UTF-8 JSON 최대 1,024 bytes. 양끝 ASCII space 제거·ASCII 소문자 대문자화 후 10자리. 중복/미지 필드·null·내부공백은 거절합니다.")
                    .content(json(body)));
            api.setInfo(new Info().title("Billing 테스트 — 추천인 API").version("R1")
                    .description("Identity MEMBER Access Token: audience tosunsaeng-billing, scope billing:purchase. "
                    + "입력자·추천인 모두 POST /api/v1/payments/purchase-account 준비가 필요하며 실제 구매나 RevenueCat 로그인은 필요 없습니다. "
                    + "각 API 호출자별 10회/분. 잔여권은 GET /api/v1/entitlements로 조회합니다. "
                    + "Try it out은 테스트 DB에 실제 코드를 발급하거나 보상을 지급합니다. 토큰은 저장하지 않습니다."));
            api.setServers(List.of(new Server().url("/")));
            api.setPaths(new Paths().addPathItem(PublicIngressFilter.REFERRAL_CODE, new PathItem().post(issue))
                    .addPathItem(PublicIngressFilter.REFERRALS, new PathItem().post(apply)));
            api.setComponents(new Components().addSecuritySchemes("bearerAuth",
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
        };
    }

    private static Operation operation(String id, String summary, String description, Schema<?> result) {
        var responses = new ApiResponses().addApiResponse("200", new ApiResponse().description("성공")
                .content(json(envelope(result, true, "SUCCESS"))));
        Map<String, String> errors = Map.ofEntries(
                Map.entry("400", "INVALID_REQUEST: 형식 수정, 자동 반복 금지"),
                Map.entry("401", "UNAUTHENTICATED: 인증 복구 후 같은 계정으로 재시도"),
                Map.entry("403", "FORBIDDEN / PAYMENT_ACCOUNT_WITHDRAWN: 권한 부족·탈퇴, 중단"),
                Map.entry("404", "NOT_FOUND: 기능 OFF·잘못된 경로"),
                Map.entry("405", "METHOD_NOT_ALLOWED: POST 사용"),
                Map.entry("409", "PURCHASE_ACCOUNT_REQUIRED / IDEMPOTENCY_KEY_CONFLICT / REFERRAL_ALREADY_APPLIED / SELF_REFERRAL_NOT_ALLOWED"),
                Map.entry("413", "PAYLOAD_TOO_LARGE: body 크기 수정"),
                Map.entry("415", "UNSUPPORTED_MEDIA_TYPE: application/json UTF-8 사용"),
                Map.entry("422", "REFERRAL_CODE_UNAVAILABLE: 없거나 사용 불가 코드"),
                Map.entry("429", "RATE_LIMITED: Retry-After 이후 재시도"),
                Map.entry("503", "BILLING_TEMPORARILY_UNAVAILABLE / AUTHENTICATION_TEMPORARILY_UNAVAILABLE: Retry-After 이후 재시도"));
        errors.forEach((status, text) -> {
            var response = new ApiResponse().description(text).content(json(envelope(new Schema<>().nullable(true), false, text.split(":| / ")[0])));
            if (status.equals("429") || status.equals("503")) response.addHeaderObject("Retry-After",
                    new io.swagger.v3.oas.models.headers.Header().description("재시도 대기 초").schema(new IntegerSchema()));
            responses.addApiResponse(status, response);
        });
        return new Operation().operationId(id).summary(summary).description(description).tags(List.of("추천인 코드"))
                .security(List.of(new SecurityRequirement().addList("bearerAuth"))).responses(responses);
    }

    private static Schema<?> envelope(Schema<?> result, boolean success, String code) {
        return new ObjectSchema().addProperty("isSuccess", new BooleanSchema().example(success))
                .addProperty("code", new StringSchema().example(code)).addProperty("message", new StringSchema())
                .addProperty("result", result).required(List.of("isSuccess", "code", "message", "result"));
    }
    private static Content json(Schema<?> schema) {
        return new Content().addMediaType("application/json", new MediaType().schema(schema));
    }
}
