# PLAN-009: 결제 상품·구매 계정·public 인증 기반

- 작성일: 2026-10-06 / 상태: 승인 대기 / Jira 미생성 / 코드 미구현
- 선행: [PLAN-008 Phase 0](PLAN-008-fixed-term-payment-roadmap.md), [ADR-004 §5.1~5.3](../adr/ADR-004-fixed-term-premium-payment-contract.md).
- 2026-10-07 [009 세부 기술 계약 초안](../contracts/PLAN-009-payment-foundation-technical-contract.md) 작성: API/error·route별 인증/flag·account 원자 생성·catalog·Mongo 분산 limiter·subset manifest/테스트 구체화. 신규 index/고정 window·privacy manifest 등은 검토 대상으로, 작성 자체는 구현/배포 승인이 아니다.

## 1. 5줄 결론

2026-10-07 최신 승인: [공동 기술 계약 개정안](../contracts/IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md)의 보존·token 상한·기술 설계/초기값 사용자 채택 완료. 아래 과거 ‘미승인 제안’ 설명은 해당 범위에 한해 이 기록으로 갱신한다. Identity 합의/fixture·이관 실측·구현 착수 및 배포/판매/purge는 별도다.

2026-10-07 [Identity–Billing 공동 기술 계약 초안](../contracts/IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md) 작성: 구매 발급 fence, 독립 capture 보존, 고정 snapshot/sequence feed, ACK/retry/rollback 상세 제안. 신규 개인정보 보존/TTL 상한·이관과 상대 route 합의 전 확정 구현 계약으로 사용하지 않는다.

2026-10-07 사용자 승인 운영 주기: 삭제 대상 매시간 점검·미확인 건 일일 재확인·최초 미해결 판정 후 7일 이내 담당자 검토. 조건부 삭제의 거래 확인 gate와 실제 삭제 OFF는 유지한다. 나머지 기술 초기값·예외 보존은 별도 검토다.

2026-10-07 후속: [탈퇴·대사·삭제 기술 설계](../contracts/PLAN-009-withdrawal-reconciliation-and-purge-contract.md)를 작성했다. Identity 독립 delivery/누락 복구, lifecycle fence, 조건부 purge와 010~013 의존성을 제안한다. provider coverage·token drain·예외 보존·cursor 활동 갱신의 ADR 정합성은 구현 전 gate이며 자동 삭제는 기본 OFF다.

1. 상품 조회와 본인 구매용 opaque reference 발급까지만 구현한다.
2. 구매 계정 생성 자체로 무료/유료 사용권을 지급하지 않는다.
3. 기존 사용자 JWT/connector를 확장하되 무료 reader flag와 결제 flag를 분리한다.
4. public 응답은 2026-10-06 승인된 기존 PublicResponse를 재사용하며 ADR §5.3과 일치시킨다.
5. catalog/계정 저장과 동시성·인증 회귀 완료 뒤에도 결제 판매는 OFF다.

## 2. 사용자가 반드시 읽어야 하는 내용

포함: `GET /api/v1/payments/products`(billing:read), `POST /api/v1/payments/purchase-account`(billing:purchase+MEMBER), 5상품 catalog·환경별 account/reference·분산 요청 제한. 구매 sync/webhook·기간권 생성·환불·신규 관리자 API는 제외.

| 상품 | 기간초 | 국내 기준 가격 |
| --- | ---: | ---: |
| PREMIUM_1D | 86400 | 9000 |
| PREMIUM_3D | 259200 | 19000 |
| PREMIUM_7D | 604800 | 29000 |
| PREMIUM_14D | 1209600 | 49000 |
| PREMIUM_28D | 2419200 | 69000 |

상품 ID/RC resource ID/Offering Package를 혼동하지 않는다. 실제 매핑은 환경 입력이고 9/16 Apple 등록 기록은 재검증 입력이지 Google 매핑 증거가 아니다. 표시·동의 금액은 Store SDK의 현지화 가격, 기록 금액은 검증된 provider 값. 서버 catalog의 가격을 client 주장 거래 금액 검증에 사용하지 않는다.

## 3. 사용자가 결정해야 하는 사항

2026-10-07 후속 선택: 고정1분 limiter/subset 적용/조건부 미구매 정리/JWT 만료 수용·reference 필드 암호화 생략 및 미구매 탈퇴 계정15일 후 조건부 삭제 승인. 정확 조건은 세부 계약을 따른다. 새 정책 선택보다 탈퇴 전달/대사·삭제 manifest·Identity purchase 발급·index 기술 검토가 남았으며 구현/배포 승인은 별도다.

2026-10-06 승인: 두 API 및 이후 sync/paid reader에 `PublicResponse<T>` 사용. ADR direct DTO 초안을 `result` 내부 DTO로 변경했다. 기존 무료/internal 응답은 변경하지 않는다. PLAN-009의 나머지 세부 기술 및 구현 승인은 별도이며 추가 제품 정책 선택은 없다.

## 4. 주요 위험과 미확인 사항

Identity billing:read/audience 코드와 purchase scope 발급은 별개다. `account_type=MEMBER`만으로 purchase를 허용하지 않으며 scope+검증된 claim 둘 다 필요하다. ACTIVE 발급/refresh·탈퇴/정지 후 Access Token 잔존 기간의 정책을 Identity와 확인한다. Billing에서 JWT만으로 현재 계정 DB 상태를 실시간 확인한다고 주장하지 않는다.

public JWT 검증은 기존 RS256/kid/JWKS/issuer/aud/exp/iat/jti/canonical sub·skew≤60초를 재사용한다. public connector의 `/internal/**` 차단을 유지하고 provider ingress를 사용자 scope로 열지 않는다. 009 단계에 receipt/webhook parser를 추가하지 않는다.

## 5. 현재 작업과 직접 관련된 구현

### API 제안 및 오류

Products는 body/query 없음. result는 ADR `catalogVersion, products[{offerCode,durationSeconds,autoRenew:false,storeProducts[{store,productId,packageId}]}]`. 판매 mapping 미설정/비활성은 목록에서 제외, DB 장애는 503이지 빈 목록 아님.

Purchase-account는 body 없음. verified sub/environment의 account를 get-or-create 후 동일 lowercase UUID v4 reference 반환. 동시 요청 duplicate-key는 기존 active reference를 읽어 수렴한다. 기존 inactive alias는 신규 구매에 반환하지 않는다. rotation 작업/다른 계정 이전 API는 이번 범위 밖이다.

```json
{"isSuccess":true,"code":"SUCCESS","message":"요청에 성공했습니다.","result":{"purchaseAccountRefId":"ae3af4be-ffeb-4cec-99e8-0df45f6c27ad"}}
```

성공200, 실패 envelope는 `isSuccess:false,code,message,result:null`; 메시지는 고정 안전 문구. 400 INVALID_REQUEST,401 UNAUTHENTICATED,403 FORBIDDEN,404 NOT_FOUND(flag OFF),405 METHOD_NOT_ALLOWED,429 RATE_LIMITED,503 BILLING_TEMPORARILY_UNAVAILABLE. 사용자 응답 no-store. 기존 무료 메시지 변경 없이 결제 오류 매핑을 추가한다. products 60회/분, account 10회/분 합산 subject limiter; 구현 저장소/원자 카운터 index는 Phase 0 manifest에 포함하고 local 메모리만으로 운영 enforcement하지 않는다.

### Transaction·저장

`payment_products`, `payment_accounts`, `purchase_account_refs`와 ADR A.2의 store/RC product unique, environment-user unique, paidSubject unique, active reference partial unique를 추가한다. 계정과 active reference는 같은 Transaction. 식별자는 서버 생성하며 get-or-create는 지급·Claim·Grant·Reservation·무료 ledger를 쓰지 않는다. catalogVersion/duration immutable snapshot 사용 준비.

기존 `BillingMongoIndexInitializer`를 additive manifest로 확장한다. v5 전체 완료와 이 slice subset을 구분하고 신규 collection만 준비된 상태로 v5 전체를 충족했다고 표시하지 않는다. 실제 migration 명령·dry run·reader compatibility는 013에서 승인한다.

### 구현 순서·flag 제안

1. envelope/security 계약 동결 → catalog/account 도메인·repository·unique 테스트.
2. public JWT 설정을 무료 reader 활성화와 분리 가능한 공통 기반으로 최소 추출. 기존 flag 의미 유지.
3. controller·converter·error mapping·합산 rate limit·ingress allowlist.
4. `billing.payment.catalog-enabled`, `purchase-account-enabled` 기본 false; 비활성 exact route404/no-store. flag 이름은 신규 제안이며 config validation 테스트 포함.
5. 문서·OpenAPI 예제·Identity 인계·회귀 확인.

## 6. 부록 — 테스트·완료 기준

- duration/가격/환경·Store/RC product 매핑, 비활성 mapping·장애 구분.
- 같은 user 20개 동시 account 요청→한 account/activeRef, 다른 사용자/환경 분리, Transaction rollback.
- Guest read 허용·purchase 거절, MEMBER scope 없음 거절, 잘못된 aud/issuer/kid/signature/expired/sub, body userId 거절.
- 계정 발급 반복 전후 TrialClaim/Grant/ledger 불변; actual Controller 및 connector 우회 negative test.
- 기존 PLAN-007 응답/flag·SigV4 route 회귀, secret/reference 비로깅.
- `./gradlew clean test`와 replica-set Testcontainers 성공, 미실행 외부 gate 명시. 009는 실결제 가능 선언이 아니다.
