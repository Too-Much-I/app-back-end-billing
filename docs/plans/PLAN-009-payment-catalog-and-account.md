# PLAN-009: 결제 상품·구매 계정·public 인증 기반

- 작성일: 2026-10-06 / 갱신: 2026-10-12 / Jira: [TMI-201](https://to-teacher.atlassian.net/browse/TMI-201) / 상태: 사용자 요청으로 PR1 구현·로컬 검증 완료, PR2 미구현·전체 이슈 미완료
- 선행: [PLAN-008 Phase 0](PLAN-008-fixed-term-payment-roadmap.md), [ADR-004 §5.1~5.3](../adr/ADR-004-fixed-term-premium-payment-contract.md).
- 세부 기준: [상품·계정 기술 계약](../contracts/PLAN-009-payment-foundation-technical-contract.md), [탈퇴 수신·후속 대사 설계](../contracts/PLAN-009-withdrawal-reconciliation-and-purge-contract.md), [공동 계약](../contracts/IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md), [수락된 exact 규격](../contracts/TMI-199-EXACT-WIRE-AND-INDEX-SPEC.md). [PR1 구현·검증 안내](../runbooks/TMI-201-payment-foundation.md). 구현 완료는 배포·활성화 승인이 아니다.

## 1. 5줄 결론

1. TMI-201은 상품 조회·구매 계정 발급(PR1), 탈퇴 수신·신규 사용 차단(PR2)의 두 구현 단위다.
2. 구매 계정 생성 자체로 무료/유료 사용권을 지급하지 않는다.
3. 기존 사용자 JWT/connector를 확장하되 무료 reader flag와 결제 flag를 분리한다.
4. public 응답은 2026-10-06 승인된 기존 PublicResponse를 재사용하며 ADR §5.3과 일치시킨다.
5. TMI-199 계약/fixture 검증은 완료됐지만 운영 구현·실제 연동·판매·자동삭제 승인은 별도다. 모든 신규 flag는 기본 OFF다.

## 2. 사용자가 반드시 읽어야 하는 내용

포함: `GET /api/v1/payments/products`(billing:read), `POST /api/v1/payments/purchase-account`(billing:purchase+MEMBER), 5상품 catalog·환경별 account/reference·분산 요청 제한. 구매 sync/webhook·기간권 생성·환불·신규 관리자 API는 제외.

### TMI-201 두 PR와 후속 범위

| 단위 | 포함 | 완료 기준/제외 |
| --- | --- | --- |
| PR1 상품·구매 계정 기반 | 두 public API, JWT/권한, stable ref 원자 생성, lifecycle ACTIVE fence, 별도 ACCOUNT_LOOKUP cursor, limiter, additive schema | 같은 사용자/환경 동시 생성은 같은 ref; cursor만 activity 갱신, 무료권 불변. 실제 RC 호출·지급 없음 |
| PR2 탈퇴 수신·차단 | UserWithdrawn strict consumer/inbox, lifecycle CAS, account/ref 비활성, 계정 없는 탈퇴 선행 처리, cleanup 후보 기록, internal 역할 격리 | 중복204/digest 충돌409/장애503, 생성-탈퇴 양방향 경합 검증. 후보를 실제 삭제하지 않음 |

PR1의 lifecycle/cursor 기반은 PR2와 공통 Transaction 경계를 사용한다. PR1만 배포했다고 구매 계정 기능을 활성화하지 않는다. Identity TMI-200 구매 권한 발급 및 TMI-202 durable 탈퇴 전달과 fake fixture로 병행 개발할 수 있지만 실제 연동·활성화에는 상대 구현과 staging 검증이 필요하다.

후속: Identity 복구 API는 TMI-203, Billing 복구 client/generation은 TMI-204, 실제 대사/조건부 purge는 TMI-205와 PLAN-010 거래 증거에 의존한다. 공동 staging은 TMI-206, 판매/삭제 활성화는 PLAN-013의 별도 승인이다. 본 계획에서 결제 검증·기간권 지급·유료 admission·환불·실제 자동삭제를 구현하지 않는다.

| 상품 | 기간초 | 국내 기준 가격 |
| --- | ---: | ---: |
| PREMIUM_1D | 86400 | 9000 |
| PREMIUM_3D | 259200 | 19000 |
| PREMIUM_7D | 604800 | 29000 |
| PREMIUM_14D | 1209600 | 49000 |
| PREMIUM_28D | 2419200 | 69000 |

상품 ID/RC resource ID/Offering Package를 혼동하지 않는다. 실제 매핑은 환경 입력이고 9/16 Apple 등록 기록은 재검증 입력이지 Google 매핑 증거가 아니다. 표시·동의 금액은 Store SDK의 현지화 가격, 기록 금액은 검증된 provider 값. 서버 catalog의 가격을 client 주장 거래 금액 검증에 사용하지 않는다.

## 3. 사용자가 결정해야 하는 사항

고정1분 limiter/subset 적용/JWT 만료 수용·reference 필드 암호화 생략·탈퇴15일 후 조건부 삭제 및 매시간 점검/일일 재확인/7일 내 최초 담당 검토는 승인됐다. TMI-199에서 공동 wire·물리 index·숫자 규격을 양측 수락하고 테스트/병합을 완료했다. 추가 제품 정책 선택은 없으며 실제 구현 착수·운영 이관 시간·환경 입력·배포 승인은 별도다. 대사 coverage와 예외 보존 등 후속 gate를 완료로 간주하지 않는다.

두 API 및 이후 sync/paid reader는 승인된 `PublicResponse<T>`를 사용한다. 기존 무료/internal 응답은 변경하지 않는다. 운영 입력이 없으면 fake 테스트만 수행하고 실제 상품 ID를 임의 생성하지 않는다.

## 4. 주요 위험과 미확인 사항

Identity billing:read/audience 코드와 purchase scope 발급은 별개다. `account_type=MEMBER`만으로 purchase를 허용하지 않으며 scope+검증된 claim 둘 다 필요하다. ACTIVE 발급/refresh·탈퇴/정지 후 Access Token 잔존 기간의 정책을 Identity와 확인한다. Billing에서 JWT만으로 현재 계정 DB 상태를 실시간 확인한다고 주장하지 않는다.

public JWT 검증은 기존 RS256/kid/JWKS/issuer/aud/exp/iat/jti/canonical sub·skew≤60초를 재사용한다. public connector의 `/internal/**` 차단을 유지하고 provider ingress를 사용자 scope로 열지 않는다. 009 단계에 receipt/webhook parser를 추가하지 않는다.

## 5. 현재 작업과 직접 관련된 구현

### API 및 오류

Products는 body/query 없음. result는 ADR `catalogVersion, products[{offerCode,durationSeconds,autoRenew:false,storeProducts[{store,productId,packageId}]}]`. 판매 mapping 미설정/비활성은 목록에서 제외, DB 장애는 503이지 빈 목록 아님.

Purchase-account는 body 없음. verified sub/environment의 account를 get-or-create 후 동일 lowercase UUID v4 reference 반환. 동시 요청 duplicate-key는 기존 active reference를 읽어 수렴한다. 기존 inactive alias는 신규 구매에 반환하지 않는다. rotation 작업/다른 계정 이전 API는 이번 범위 밖이다.

```json
{"isSuccess":true,"code":"SUCCESS","message":"요청에 성공했습니다.","result":{"purchaseAccountRefId":"ae3af4be-ffeb-4cec-99e8-0df45f6c27ad"}}
```

성공200, 실패 envelope는 `isSuccess:false,code,message,result:null`; 메시지는 고정 안전 문구. 400 INVALID_REQUEST,401 UNAUTHENTICATED,403 FORBIDDEN,404 NOT_FOUND(flag OFF),405 METHOD_NOT_ALLOWED,429 RATE_LIMITED,503 BILLING_TEMPORARILY_UNAVAILABLE. 사용자 응답 no-store. 기존 무료 메시지 변경 없이 결제 오류 매핑을 추가한다. products 60회/분, account 10회/분 합산 subject limiter; 구현 저장소/원자 카운터 index는 Phase 0 manifest에 포함하고 local 메모리만으로 운영 enforcement하지 않는다.

### Transaction·저장

`payment_products`, `payment_accounts`, `purchase_account_refs`와 ADR A.2의 store/RC product unique, environment-user unique, paidSubject unique, active reference partial unique를 추가한다. 계정과 active reference는 같은 Transaction. 식별자는 서버 생성하며 get-or-create는 지급·Claim·Grant·Reservation·무료 ledger를 쓰지 않는다. catalogVersion/duration immutable snapshot 사용 준비.

기존 `BillingMongoIndexInitializer`를 additive manifest로 확장한다. v5 전체 완료와 이 slice subset을 구분하고 신규 collection만 준비된 상태로 v5 전체를 충족했다고 표시하지 않는다. 실제 migration 명령·dry run·reader compatibility는 013에서 승인한다.

### 구현 순서·flag

1. 수락된 envelope/security/index 기준으로 catalog/account 도메인·repository·unique 테스트.
2. public JWT 설정을 무료 reader 활성화와 분리 가능한 공통 기반으로 최소 추출. 기존 flag 의미 유지.
3. controller·converter·error mapping·합산 rate limit·ingress allowlist.
4. `billing.payment.catalog-enabled`, `purchase-account-enabled` 기본 false; 비활성 exact route404/no-store. config validation 테스트 포함.
5. 문서·OpenAPI 예제·Identity 인계·회귀 확인.
6. PR2의 탈퇴 strict consumer·inbox·lifecycle 차단/cleanup 후보와 생성 경합 구현. internal feature flag·schema readiness를 검사하고 실제 활성화는 별도다.

## 6. 부록 — 테스트·완료 기준

- duration/가격/환경·Store/RC product 매핑, 비활성 mapping·장애 구분.
- 같은 user 20개 동시 account 요청→한 account/activeRef, 다른 사용자/환경 분리, Transaction rollback.
- Guest read 허용·purchase 거절, MEMBER scope 없음 거절, 잘못된 aud/issuer/kid/signature/expired/sub, body userId 거절.
- 계정 발급 반복 전후 TrialClaim/Grant/ledger 불변; actual Controller 및 connector 우회 negative test.
- 기존 PLAN-007 응답/flag·SigV4 route 회귀, secret/reference 비로깅.
- `./gradlew clean test`와 replica-set Testcontainers 성공, 미실행 외부 gate 명시. 009는 실결제 가능 선언이 아니다.
- PR2: 탈퇴 선행/발급 선행/동시 요청, 동일 event 중복·digest 충돌·다른 탈퇴시각 충돌·Transaction rollback, Identity만 허용/unsigned·wrong role·direct 접근 거절, 무료 Claim/Grant/owner 불변.
- TMI-199의 Billing326건 통과는 test-only 기반 검증이다. TMI-201 production controller/service 구현 검증을 대신하지 않는다. 두 PR의 테스트·문서 완료와 사용자 병합/종료 요청을 따로 확인한다.
