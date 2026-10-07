# PLAN-009 상품·구매 계정 기반 세부 기술 계약 초안

- 작성일: 2026-10-07 / 상태: 검토용 기술 초안 / Jira 미생성 / 코드 미구현.
- 상위: [PLAN-009](../plans/PLAN-009-payment-catalog-and-account.md), [ADR-004 §5.1~5.3/A](../adr/ADR-004-fixed-term-premium-payment-contract.md).
- 이미 승인된 제품 정책·PublicResponse는 유지한다. 아래 신규 limiter·schema subset·catalog 발행 절차는 제안이며 본 문서 작성이 구현/배포 승인은 아니다. 충돌 시 승인 ADR을 우선하고 조용히 변경하지 않는다.

## 1. 5줄 결론

후속 기술 설계: [탈퇴 수신·계정 상태·거래 확인·삭제](PLAN-009-withdrawal-reconciliation-and-purge-contract.md). 최신 승인 범위는 공동 기술 계약을 따른다. TMI-199에서 반복 account 안정성과 ADR §5.10 activity/cursor 요구를 [별도 lifecycle/cursor 쓰기 규격](TMI-199-PAYMENT-LIFECYCLE-FOUNDATION.md#53-반복-account-호출과-activity-cursor-정합성)으로 구체화하고 test-only Transaction으로 확인했다. production 구현은 TMI-201이며 신규 wire/Identity fixture 합의는 별도다.

1. 상품 GET과 구매 계정 POST만 추가하며 실제 결제·기간권·환불·무료권 지급은 하지 않는다. [PLAN-009 §2](../plans/PLAN-009-payment-catalog-and-account.md)
2. verified JWT sub/environment로 계정을 만들고 원자적 get-or-create로 같은 사용자에게 같은 reference를 반환한다. [ADR §5.2](../adr/ADR-004-fixed-term-premium-payment-contract.md)
3. Guest는 read만, 구매 계정은 MEMBER와 billing:purchase 둘 다 필요하며 현재 계정 ACTIVE 여부를 JWT만으로 실시간 보장하지 않는다.
4. Google 미등록 매핑은 노출하지 않으며 RC/Store 실거래 gate와 Identity 발급 검증 전 판매는 OFF다.
5. 기존 무료 reader·internal SigV4와 분리하고 Mongo 분산 제한·subset index·보안 회귀까지 검증한다. [현재 security](../../src/main/java/web/tosunsaeng/billing/global/config/security/PublicSecurityConfig.java)

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 외부 API

| method/path | 인증/권한 | 요청 | 성공 |
| --- | --- | --- | --- |
| GET /api/v1/payments/products | Identity JWT + billing:read; Guest/MEMBER | body/query 없음 | 200 ProductsResponse |
| POST /api/v1/payments/purchase-account | Identity JWT + billing:purchase + account_type=MEMBER | body/query 없음 | 200 PurchaseAccountResponse |

account_type으로 전화번호 자격을 추론하지 않는다. purchase에는 read scope를 추가 필수로 만들지 않는다. 익명 구매·다른 user 지정·무료/유료 지급을 허용하지 않는다. Idempotency-Key는 이 두 API의 필수 조건이 아니며 account의 환경/user unique가 멱등 기준이다. 보낸 header는 계정 식별이나 신규 회전을 유발하지 않는다.

body는 0 byte만 허용한다. `{}`, `null`, 공백 body도400이다. query parameter는 종류와 무관하게400. POST empty body에는 Content-Type을 강제하지 않는다. 16KiB 초과 요청은 body 미보관 INVALID_REQUEST400; public 응답도 16KiB를 넘으면 잘라 반환하지 않고503. 사용자 ID·환경·reference를 body/path/query/header로 지정할 수 없다.

성공 예시(실제 등록값 아닌 명시적 example):

```json
{
  "isSuccess": true,
  "code": "SUCCESS",
  "message": "요청에 성공했습니다.",
  "result": {
    "catalogVersion": 1,
    "products": [
      {
        "offerCode": "PREMIUM_1D",
        "durationSeconds": 86400,
        "autoRenew": false,
        "storeProducts": [
          {"store": "APP_STORE", "productId": "example.premium1d", "packageId": "premium1d"}
        ]
      }
    ]
  }
}
```

Google 미등록 시 PLAY_STORE 항목은 없다. 해당 플랫폼의 매핑이 없는 앱은 구매 불가로 표시한다. 배열은 기간 오름차순, store는 APP_STORE→PLAY_STORE 고정 순서다. 가격은 Store SDK의 현지 통화 표시와 결합하고 이 DTO에 추정 가격을 추가하지 않는다.

```json
{"isSuccess":true,"code":"SUCCESS","message":"요청에 성공했습니다.","result":{"purchaseAccountRefId":"ae3af4be-ffeb-4cec-99e8-0df45f6c27ad"}}
```

reference는 lowercase UUIDv4이며 userId가 아니다. 앱이 RC custom App User ID로 쓰는 식별자이지 인증 token이 아니다. 다른 사람의 reference를 알더라도 구매/권리 소유자가 되지는 않는다.

### 2.2 오류·헤더 계약

```json
{"isSuccess":false,"code":"FORBIDDEN","message":"접근 권한이 없습니다.","result":null}
```

| HTTP/code | 원인 | 부가 header |
| --- | --- | --- |
| 400 INVALID_REQUEST | body/query·요청 형식 오류 | 없음 |
| 401 UNAUTHENTICATED | token 누락/서명·issuer·aud·expiry·sub 오류 | WWW-Authenticate: Bearer |
| 403 FORBIDDEN | route scope 부족 또는 purchase의 MEMBER 불충족 | scope 부족만 해당 route scope로 Bearer insufficient_scope; account_type 거절은 잘못된 scope 안내 금지 |
| 404 NOT_FOUND | flag OFF·비허용 connector/path | 없음 |
| 405 METHOD_NOT_ALLOWED | ON인 exact route의 wrong method | products=GET, account=POST로 Allow 지정 |
| 429 RATE_LIMITED | 분산 사용자별 제한 초과 | 해당 window 잔여 초를 올림한 Retry-After, 최소1 |
| 503 AUTHENTICATION_TEMPORARILY_UNAVAILABLE | 신뢰 JWKS 일시 장애 | Retry-After: 5 |
| 503 BILLING_TEMPORARILY_UNAVAILABLE | DB/limiter/매핑 정합성·account 불변식 오류 | Retry-After: 5 |

모든 application 생성 성공/오류에 application/json UTF-8, Cache-Control:no-store, X-Trace-Id를 적용한다. 무료 PublicResponse의 기존 메시지는 변경하지 않는다. 결제503 메시지는 `결제 정보를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.`로 route 전용 매핑 제안. ingress→flag/path→method→JWT/scope/account_type→body/query→limiter→업무 순서이며 요청 크기는 ingress에서 먼저 제한 가능하다. ALB 자체 오류와 Tomcat parser 거절까지 같은 envelope를 보장한다고 주장하지 않는다.

## 3. 사용자가 결정해야 하는 사항

2026-10-07 사용자 선택 반영: 고정1분 제한 A, 단계별 subset A, 회원 중 stable 유지/탈퇴 후 조건부 정리 A, 기존 JWT 만료까지 수용 A를 선택했다. 동기 Identity 상태 조회는 이번 범위에 추가하지 않는다. reference 필드 암호화는 별도 생략 승인됐다. 운영 Atlas 사용은 사용자 확인이며 실제 설정 검사는 수행하지 않았다. 미구매 탈퇴 계정은 Identity 확정 withdrawnAt+15일 후 거래 대사·미해결 건 점검을 거쳐 삭제하는 기본 정책으로 승인됐다. Identity ACTIVE MEMBER purchase 발급 검증 및 하위 exact index·보존 manifest와 구현/배포 승인은 별도다.

### 미구매 탈퇴 계정 정리 — 15일 조건부 정책

- 기준은 Identity가 확정한 withdrawnAt이며 Billing 수신 시각으로15일을 다시 시작하지 않는다. 탈퇴 확인 즉시 신규 용도 비활성 처리, 현재 회원의 stable reference는 비활동만으로 삭제하지 않는다.
- 15일 경과만으로 TTL 삭제하지 않는다. 서버가 거래 대사를 완료하고 실제 구매 없음·처리 중 결제 없음·미해결 건 없음이 확인된 account/reference 및 사용자 연결정보를 명시적 purge 작업으로 정리한다. Billing Purchase 부재만으로 미구매를 판정하지 않는다.
- 뒤늦은 탈퇴 event가15일 이후 도착해도 확인 없이 즉시 삭제하지 않는다. event/조회 장애는 정상 미구매가 아니며 대사 실패와 삭제 지연을 관측한다. 구매/삭제 경합은 같은 계정 fence/CAS로 막고 삭제 뒤 과거 JWT·늦은 요청이 계정을 재생성하지 못하도록 한다.
- 실제 거래가 발견되면 해당 금융 증거의 승인 보존 정책으로 분리한다. 미해결 건은 필요한 최소 증거와 사유·재검토 기한을 별도 관리하며 전체 account를 무기한 보존하지 않는다. 삭제 후 늦은 거래는 새 계정/같은 전화번호에 자동 연결·지급하지 않고 검토한다.
- 15일은 운영 기본 기준이지 법정기간/provider 최대 지연 보장이 아니다. 실제 검증에서 부족함이 확인되면 보고 후 정책을 재검토한다. 탈퇴 전달·대사 완전성·purge 실행 주기/지연 경보·미해결 재검토 기한·재생성 방지 기록의 최소 보존은 상세 manifest에서 동결한다. 이번 승인은15일 정확 시점 물리 삭제 SLA나 실제 삭제 실행 승인이 아니다.

새 상품/가격/무료 보존 정책 선택은 없다. 다음 기술안을 검토 승인하면 009 구현 범위를 고정할 수 있다.

- rate limit: Mongo UTC 고정 1분 window, products60/account10, 환경+route+검증된 사용자 기준. 경계 전후 최대 두 window 한도가 연속 가능하며 rolling60초 보장은 아님. sliding window를 원하면 구현 전 변경 승인.
- payment schema 전체 v5와 별개로 `payment-foundation-v1` subset 검증을 추가한다. 아래 추가 index/limiter collection은 ADR A에 정식 동기화 후 구현.
- catalog는 reader에서 만들지 않고 versioned seed/migration으로 OFF 상태에 준비한다. catalogVersion과 승인 목록 변경은 판매 OFF+읽기 writer drain 절차 후 검증/재활성화한다. 무중단 catalog 수정·관리자 API는 제외.
- purchaseAccountRefId는 별도 application field 암호화 없이 저장하고 기존 referenceId=_id 구조를 유지한다. Atlas at-rest·TLS·최소 권한·로그 제외를 유지하며 reference 보유를 인증/소유권 증거로 사용하지 않는다. Store token/credential/민감 provider reference의 별도 보호·암호화와 limiter lookup HMAC은 이 결정으로 제거하지 않는다. 미구매/탈퇴 보존 manifest와 Identity ACTIVE 발급 검증은 선행 동결 대상이다. 기존 JWT는 유효 만료/skew까지 수용하므로 현재 DB ACTIVE를 실시간 보장하지 않는다.

실제 Store ID·RC app/product/package·환경·issuer/JWKS는 운영 입력이며 값이 없으면 임의 생성하지 않는다. Google 등록을 기다리지 않고 fake 테스트 가능하지만 실제 Google 판매는 노출하지 않는다.

## 4. 주요 위험과 미확인 사항

- 현 PublicIngressFilter는 무료 flag OFF면 전체 public 경로를 거절한다. 신규 route registry에서 무료/상품/account flag를 독립 판정해야 한다.
- 현 PublicApiWriter는403 scope를 billing:read,405 Allow를 GET,429 Retry-After를5로 고정한다. 결제용 route context를 추가하되 무료 기본 동작은 그대로 보존한다.
- 현 IdentityUserJwtDecoder의 일반 JWT 검증에 account_type 필수 조건을 전역 추가하지 않는다. MEMBER는 purchase route에서만 검증한다. 누락/비문자열/unknown account_type은 purchase403, read는 기존 계약 유지.
- 현 index initializer SCHEMA_VERSION=4를 단순5로 올리면 아직 없는 paid 전체 schema를 완료로 오인할 수 있다. v4 core 검증과 additive subset을 분리한다.
- get-or-create가 기존 activeRef 소실·다중 reference·account version 불일치를 발견하면 새 UUID로 조용히 복구하지 않고503/운영 경보. rotation/owner migration은 범위 밖이다.
- 이미 생성했으나 구매 없는 account/reference의 장기 보존은 금융5년을 무조건 적용하지 않는다. inactive alias·탈퇴 연결·삭제 절차는 정식 manifest에서 확정해야 하며 production gate다. reference 필드 암호화 생략은 보존/접근 통제 면제가 아니다. 임의 TTL 삭제로 같은 user의 reference가 바뀌면 안 된다.
- Identity ACTIVE 발급과 billing:purchase는 실제 controller 발급/갱신/계정 전환 테스트가 필요하다. 타 서버 코드는 이번에 수정하지 않는다.

## 5. 현재 작업과 직접 관련된 기술 상세

### 5.1 Catalog reader

5종 duration/국내 기준 가격은 PLAN-009 표를 유지한다. 가격은 검증된 거래금액의 대체물이 아니다. 앱 플랫폼 입력을 받지 않고 허용된 Store 매핑들을 반환하며 앱이 자기 Store만 선택한다.

- 환경은 trusted 서버 설정이며 JWT/query/헤더에서 선택하지 않는다. catalogVersion은 positive int64 config, 상품 record와 일치해야 한다.
- mapping key: environment/store/appId/storeProductId. appId는 RC app ID, rcProductId는 RC resource ID로 구분한다. packageId는 Offering 내 package key이며 선택 Offering은 앱/배포 설정에서 동기화한다.
- 새 schema field 제안: packageId, referencePriceKrw, referenceCurrency=KRW. 기존 A.1 field와 합쳐 관리한다. RC/Store 필수 매핑을 모르면 record 생성/판매 활성화하지 않는다.
- saleEnabled=false는 목록에서 제외. 공개 offer는 최소 한 enabled mapping이 있어야 한다. 모든 매핑이 비활성이면200 products:[]; DB 장애/활성 매핑의 필수 필드 누락/버전 충돌은503이다.
- 하나의 snapshot에서 catalog를 읽고 5offer·Store별 최대1 mapping을 검증한다. catalogVersion은 사용 가능 snapshot을 식별하며 current 설정과 불일치한 enabled record를 조용히 제외하지 않는다.
- payment-products는 immutable 거래용 mapping을 유지하며 기존 productId를 다른 기간으로 재사용하지 않는다. 새로운 기간 변경은 별도 product/migration이며 009에서 실행하지 않는다.

### 5.2 Account get-or-create

1. 인증·route 검사 후 environment/sub로 account를 조회하고 업무 Transaction에서 ACTIVE lifecycle fence를 조건부 쓰기로 검증한다. 탈퇴/계정 생성·purge와 경합하며 read-only 상태 검사만으로 대체하지 않는다.
2. 존재하면 activeRef와 accountId/environment·active=true 연결을 검증하고 별도 ACCOUNT_LOOKUP cursor의 lastActivityAt을 monotonic 갱신한 뒤 같은 ref를 반환한다. account 자체의 version/activeRefId/createdAt을 매 호출 갱신하지 않는다. lifecycle version/limiter/cursor는 별도 쓰기다.
3. 없으면 accountId·paidSubjectRefId·referenceId UUIDv4를 생성하고 version=1, createdAt/updatedAt UTC로 account와 active reference 및 ACCOUNT_LOOKUP cursor를 같은 Mongo Transaction에 insert한다. 실제 provider worker는010에서 구현하며009에서는 외부 RC 호출하지 않는다.
4. unique 경합은 실패 Transaction 밖에서 재조회 후 같은 결과 반환. unknown commit은 environment/user와 activeRef를 primary에서 재확인하며 새 reference 지급으로 해결하지 않는다.
5. transient retry는 기존 Transaction executor의 유한 budget 사용. 소진/불명은503이며 client 재시도 시 환경/user unique로 복구. 외부 RC/Identity 호출은 이 Transaction에 넣지 않는다.

payment_accounts._id=accountId, purchase_account_refs._id=referenceId. 참조는 동일 user/environment에 stable하며 TrialClaim/Grant/Reservation/ledger/기간권 document는 쓰지 않는다. 반복 호출의 limiter counter 변경은 보안 목적의 별도 쓰기이며 혜택 지급으로 해석하지 않는다.

### 5.3 Mongo 분산 limiter 제안

추가 collection `payment_rate_limits`: `_id` 서버 UUID, environment, routeKey(PRODUCTS|PURCHASE_ACCOUNT), subjectKey, windowStart, count, purgeAt. subjectKey는 환경별 별도 HMAC key로 environment+canonical sub의 길이 구분 encoding을 해시한 lowercase hex. token/reference/raw userId·IP는 저장하지 않는다. 해시도 연결 가능 정보로 보호한다.

windowStart=UTC minute floor, purgeAt=windowStart+2분. TTL은 청소 전용이며 허용 여부는 window key/count로 판정한다. count<limit 조건의 atomic increment를 사용하고 최초 insert unique 충돌 시 기존 bucket의 조건부 increment로 재시도한다. 초과 요청은 count를 늘리지 않고429. 사전 read/count++/save 및 instance별 메모리 limiter 금지.

limiter DB 장애는503, 우회 허용하지 않는다. limiter write 뒤 업무 실패도 요청 횟수에 포함된다. 서버 간 NTP 오차/분 경계 테스트와 키 교체 중 제한 분리 방지 절차가 필요하다. 키/한도 변경은 window 경계에 협조 전환하거나 dual-read 후 구 bucket까지 합산하는 별도 이관으로 처리하며 임의 rolling 교체 금지. 새로운 운영 의존성은 추가하지 않는다.

### 5.4 Security·flag·ingress

기존 BILLING_USER_JWT_ISSUER/JWKS_URI/CLOCK_SKEW와 PUBLIC_CONNECTOR_ENABLED/PORT 입력 호환성을 보존하면서 공통 public 설정으로 최소 분리한다. 결제 enabled가 무료 LEGACY_ATTRIBUTION_VERIFIED를 대신 true로 만들지 않는다. 무료 reader는 기존 eligibility/legacy gate를 계속 요구한다.

flags 제안: `billing.payment.catalog-enabled` / `BILLING_PAYMENT_CATALOG_ENABLED`, `billing.payment.purchase-account-enabled` / `BILLING_PAYMENT_PURCHASE_ACCOUNT_ENABLED`, 기본false. 추가 서버 입력: `BILLING_PAYMENT_ENVIRONMENT=SANDBOX|PRODUCTION`, `BILLING_PAYMENT_CATALOG_VERSION` 양수, limiter key는 secret store 주입이며 저장소에 값 없음.

어느 결제 flag든 ON이면 isolated public connector·trusted JWT·Mongo replica set/index subset·limiter 설정을 검사한다. catalog ON은 catalog snapshot도 검증. OFF 경로404, 잘못된 port/path는404, internal SigV4 principal이 사용자 JWT를 대신하지 않는다. ALB에는 기존 무료 GET과 결제 두 exact method/path만 허용한다. sync/webhook/paid entitlement는 009에서 열지 않는다. wildcard·forwarded header 기반 bypass 금지, HEAD/OPTIONS도 새로 허용하지 않는다.

## 6. 부록 — schema manifest·검증·인계

### 6.1 Foundation subset index 제안

모든 key는 표 순서 ascending, 기본 _id unique 제외. 별도 명시 없으면 partial/TTL 없음. 기존 운영 index drop/recreate 금지, 불일치 fail-fast.

| collection | index | keys | options |
| --- | --- | --- | --- |
| payment_products | ux_payment_store_product | environment,store,appId,storeProductId | unique; ADR 유지 |
| payment_products | ux_payment_rc_product | environment,rcProductId | unique; ADR 유지 |
| payment_products | ux_payment_sale_offer_store | environment,offerCode,store | unique, partial {saleEnabled:true}; 추가 제안 |
| payment_products | ix_payment_catalog_sale | environment,saleEnabled,offerCode | 추가 제안 |
| payment_accounts | ux_payment_account_user | environment,userId | unique; ADR 유지 |
| payment_accounts | ux_payment_paid_subject | paidSubjectRefId | unique; ADR 유지 |
| purchase_account_refs | ux_purchase_active_ref | environment,accountId | unique, partial {active:true}; ADR 유지 |
| payment_reconcile_cursors | ux_payment_reconcile_cursor | accountId,kind | unique; ADR §5.10/A 유지 |
| payment_reconcile_cursors | ix_payment_reconcile_due | nextAttemptAt,_id | ADR 유지, 실제 worker010 |
| payment_rate_limits | ux_payment_rate_bucket | environment,routeKey,subjectKey,windowStart | unique; 추가 제안 |
| payment_rate_limits | ttl_payment_rate_purge | purgeAt | expireAfterSeconds=0; 추가 제안 |

account/ref/catalog에는 business TTL 없음. migration manifest 성공과 앱 기동 subset 비교를 별도 기록하며 subset 성공으로 v5 전체 완료를 표시하지 않는다.

탈퇴 lifecycle/inbox/cleanup까지 포함한 TMI-201 candidate16개 exact index manifest는 [TMI-199 fixture](../../src/test/resources/contracts/payment-lifecycle/v1/billing-indexes.json)를 따른다. 테스트 DB에서 검증했으며 production initializer는 아직 변경하지 않았다.

### 6.2 테스트 완료 조건

- 무료 flag OFF/결제 ON 및 역방향 조합; exact connector/path/method·위조 Host/forwarded·unsigned internal 회귀.
- JWT aud 배열·scope 문자열·RS256/issuer/kid/signature/exp/iat/jti/sub/skew 기존 계약, JWKS503; Guest read200/purchase403, MEMBER scope없음403, account_type 누락/unknown403.
- 실제 controller의 no-store/envelope·Allow·WWW-Authenticate·Retry-After·body/query/oversize 검사. 무료 메시지/헤더 유지.
- Google 매핑 없음/모두비활성/DB장애 구분; 기간5종/28일/고정 정렬·catalog 충돌 fail-closed.
- 20개 account service 동시 요청은 limiter 우회 unit/integration 설정에서 단일 account/reference 수렴. 실제 endpoint20개 동시 요청은10허용/초과429를 별도 검증한다.
- replica-set multi-instance limiter 상한·window 경계·TTL 지연·키 교체·DB장애; unique/unknown commit/Transaction rollback.
- account/ref 변조·누락은 자동 회전 금지, 다른 user/환경 분리. 반복 발급 전후 무료 Claim/Grant/ledger 및 owner 불변.
- source payload/userId/reference/credential이 로그/metric label/span attribute에 없는지 검증. HTTP SERVER 아래 payment_products/payment_purchase_account INTERNAL span은 제안이며 정상/예외 종료 확인.
- 구현 후 ./gradlew clean test, Testcontainers 실행 결과와 미실행 외부 gate 분리. 이번 문서 작성에서는 실행하지 않음.

### 6.3 인계·실제 판매 gate

Identity: existing read/audience 유지, ACTIVE MEMBER만 purchase scope, 명시 scope/refresh/승격/정지·탈퇴 발급 경로 검증. Billing에서 계정 상태 실시간 조회하는 신규 API는 추가하지 않는다.

앱: Store별 등록/활성 mapping만 표시, Store SDK 가격 결합, 로그인 reference identify 완료 전 구매 차단, 계정 전환 시 UI 차단. 이 slice는 SDK 결제/환불 구현 완료가 아니다.

Google 실제 상품 미등록이므로 실제 거래 매핑/Orders 부분 환불 검증은 후속. 009 구현은 fake ID/응답만 사용하며 실제 ID를 꾸며 넣지 않는다. exact schema/privacy manifest와 Identity 발급 미완료 시 해당 production flag OFF.

### 6.4 현재 코드 근거

- [PublicSecurityConfig](../../src/main/java/web/tosunsaeng/billing/global/config/security/PublicSecurityConfig.java): 무료 설정에 결합된 JWT/connector/권한.
- [PublicIngressFilter](../../src/main/java/web/tosunsaeng/billing/global/config/security/PublicIngressFilter.java): 무료 exact GET과 flag.
- [PublicApiWriter](../../src/main/java/web/tosunsaeng/billing/global/config/security/PublicApiWriter.java): GET/read 고정 헤더.
- [PublicResponse](../../src/main/java/web/tosunsaeng/billing/global/response/PublicResponse.java): 승인 wrapper.
- [IdentityUserJwtDecoder](../../src/main/java/web/tosunsaeng/billing/global/config/security/IdentityUserJwtDecoder.java): 기존 JWT 검증 유지.
- [BillingMongoIndexInitializer](../../src/main/java/web/tosunsaeng/billing/global/infrastructure/mongodb/BillingMongoIndexInitializer.java): schema4와 exact index 검증.
