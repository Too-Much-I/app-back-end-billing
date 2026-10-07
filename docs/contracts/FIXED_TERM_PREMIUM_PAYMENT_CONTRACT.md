# Fixed-term premium 결제 계약 요약

- 작성일: 2026-09-05
- 최종 갱신일: 2026-09-09
- 상태: Apple·Google consumable one-time fixed-term 상품과 RevenueCat 결제 연동·9개 선택 승인. [ADR-004 기술 초안](../adr/ADR-004-fixed-term-premium-payment-contract.md) 작성 완료, 4주(28일) 상품·D1-A 정상 만료·D2-A 환불 취소 정책 승인. 기술 상세 검증·별도 PLAN·구현은 후속
- 기준 문서: `docs/codex/CONTRACT_DECISIONS.md` C1-A, C2-A, C9-S1~S11. 2026-09-09 Store 중심 환불 창구·지원 이메일·만료 후 열람·별도 할인 없음 승인; 2026-10-06 앱 Identity 문의의 `REFUND` 분류와 수동 검토 승인으로 아래 자체 UI 제외를 상담 접수에 한해 대체한다. Store 직접 신청/실제 환급 및 검증된 최종 결과 반영은 유지한다. 문의만으로 권리를 취소하지 않으며 자동 심사·직접 송금·공제 공식은 미승인이다. Identity 구현은 후속; 구매 고지는 별도 게시 전 초안
- 적용 서비스: Billing, Identity, Learning Core, iOS/Android app

## 0. 5줄 결론

1. 유료 상품은 자동 갱신 구독이나 credit가 아니라 1·3·7·14·28일 동안 모의고사를 무제한 사용하는 fixed-term `PREMIUM_SUBSCRIPTION`이다.
2. Apple Consumable In-App Purchase와 Google consumable one-time product를 RevenueCat 표준 SDK·Offering으로 판매하고, RevenueCat이 제공하는 검증된 Store transaction만 Billing 내부 다섯 offer에 매핑한다.
3. RevenueCat custom App User ID와 Store account reference에는 Billing 발급 opaque `purchaseAccountRefId`를 사용하고, Billing public API는 사용자 JWT로 호출한다.
4. ACTIVE paid 권리를 무료권보다 먼저 사용하므로 유료 기간에는 `FREE_EXAM_ONCE` Claim·Grant·unit을 변경하지 않는다.
5. client sync, RevenueCat Authorization+HMAC webhook과 RevenueCat API reconciliation을 같은 Store transaction 멱등 처리로 수렴한다. Store-confirmed refund는 OPEN·RETAKE_AVAILABLE과 replacement를 차단하고, 이미 제출된 GRADING만 완료하며 COMPLETED history는 보존한다.

## 1. 반드시 읽어야 하는 내용

- UI에서 구독 또는 이용권으로 표시할 수 있지만 현재 상품은 자동 갱신하지 않는다.
- 각 offer는 Store에서도 재구매 가능한 consumable/one-time product다. 구매 한 건이 Billing catalog의 고정 duration 하나를 만들며 자동 갱신하지 않는다.
- Billing은 client가 보낸 가격, 기간, entitlement state 또는 userId를 신뢰하지 않는다.
- 앱의 RevenueCat 구매 성공 callback만으로 권리를 지급하지 않는다. Billing이 Authorization+HMAC webhook 또는 인증된 RevenueCat API에서 Store transaction을 확인하고 local Transaction을 commit한 뒤 권리를 지급한다.
- 같은 Apple/Google 계정, device 또는 phone을 사용해도 다른 토선생 계정으로 유료 권리를 자동 이전하지 않는다.
- 구매는 ACTIVE MEMBER만 가능하다. Guest는 상품·권리 조회까지만 가능하며 현재 paid owner migration 대상이 아니다.
- 무료권은 유료 기간에 소비되지 않으며, 유료 기간 종료 뒤 아직 미사용이면 사용할 수 있다.
- 정규화된 결제·권리·원장은 최종 거래·이용기간·reversal 중 가장 늦은 시점부터 5년 보존하고 provider inbox는 120일 보존한다.
- 국내 판매가격과 한국 성인 대상은 아래에 기록했다. 실제 Store 상품 ID·가격 설정·세금/할인·판매 국가/연령 제한은 출시 전에 검증해야 한다.
- RevenueCat은 Store 연동·검증 데이터 공급 계층이고 Billing entitlement의 source of truth가 아니다. consumable을 RevenueCat Entitlement에 연결해 기간 권리를 판정하지 않는다.
- Google prepaid가 14일을 지원하지 않고 1개월이 고정 720시간과 다를 수 있는 문제는 2026-09-07 양 Store one-time product 재승인으로 해소했다. Store 상품에는 기간을 맡기지 않고 Billing catalog가 exact duration을 결정한다.

## 2. 상품 계약

| 내부 offer code | duration | 권리 |
| --- | ---: | --- |
| `PREMIUM_1D` | 24시간 | 기간 중 무제한 시험 시작 |
| `PREMIUM_3D` | 72시간 | 기간 중 무제한 시험 시작 |
| `PREMIUM_7D` | 168시간 | 기간 중 무제한 시험 시작 |
| `PREMIUM_14D` | 336시간 | 기간 중 무제한 시험 시작 |
| `PREMIUM_28D` | 672시간(4주·28일) | 기간 중 무제한 시험 시작 |

국내 판매가격(사용자 제시 2026-09-08, C9-S10 기록): 1일 9,000원 / 3일 19,000원 / 1주 29,000원 / 2주 49,000원 / 4주 69,000원. 최초 출시 별도 할인 없음(C9-S11). 실제 Store 등록 완료를 의미하지 않는다. 앱 가격은 Store SDK와 일치해야 하며 이 문서의 금액을 결제 검증 근거로 하드코딩하지 않는다.

2026-09-16 실제 등록 후속: Apple 소모품 5종과 한국어 정보·위 가격을 저장했고 사용자 요청으로 대한민국만 판매 대상으로 지정했다. 모두 초안/제출 준비 중이다. Google은 결제 권한 포함 빌드 필요 안내로 미생성이다. 실제 product ID와 후속 gate는 [Store 등록 결과](STORE_PRODUCT_REGISTRATION-2026-09-16.md)를 따른다. RevenueCat/Billing 매핑 및 판매 개시는 아직 아니다.

- 2026-09-07 사용자 승인으로 기존 30일 초안을 4주(28일·2,419,200초)로 대체했다. 표시명은 '4주'이며 달력 1개월이 아니다. Store/RevenueCat의 실제 상품 생성·매핑은 별도 작업이고 기존 거래 duration은 소급 변경하지 않는다.
- 공통 `BenefitDefinition`: `PREMIUM_SUBSCRIPTION`
- Apple: offer별 재구매 가능한 Consumable In-App Purchase product ID
- Google: offer별 재구매 가능한 consumable one-time product ID
- provider ID는 환경별 Billing catalog가 내부 offer와 exact mapping한다.
- provider product 자체에는 기간을 표현하지 않고 검증된 product ID가 가리키는 Billing catalog duration을 적용한다.
- RevenueCat Offering/Package는 환경별 Store product를 노출하고 Paywall을 구성한다. 실제 가격·현지화는 Store 값이며 Billing은 verified Store product ID로 지급할 offer와 duration을 결정한다.
- RevenueCat Entitlement는 consumable을 영구 unlock으로 표현하므로 fixed-term 권리 판정에 사용하지 않는다.

## 3. 사용자·구매 연결과 복원

Billing은 Identity가 `billing:purchase`를 발급한 ACTIVE MEMBER에게 사용자·환경별 stable lowercase UUID v4 `purchaseAccountRefId` 하나를 연결한다.

- RevenueCat custom App User ID: `purchaseAccountRefId`
- Apple purchase: 같은 UUID의 `appAccountToken` 연결을 목표로 하며 실제 SDK version별 전달을 ADR-004 G1/G2에서 검증
- Google purchase: RevenueCat SDK의 obfuscated account identifier 연결·변환을 ADR-004 G1/G2에서 검증. raw UUID와 동일한 wire 문자열이라고 가정하지 않음
- Billing: `purchaseAccountRefId → current canonical user` mapping

금지:

- raw phone, email 또는 device ID를 Store account binding으로 사용
- client request의 userId를 actor로 사용
- 동일 phone·device·Store account만으로 다른 앱 계정에 유료 권리 이전
- phone rejoin proof로 purchase/entitlement owner 이전
- Guest purchase와 paid `UserMerged` migration
- 익명 RevenueCat App User ID 상태의 구매
- RevenueCat alias/restore만을 근거로 Billing purchase owner 이전 또는 과거 transaction 재지급

같은 canonical user의 여러 device와 재로그인은 stable reference를 재사용한다. 보안 회전 뒤에는 새 reference만 신규 구매에 사용하고 이전 값은 과거 구매·환불 검증용 inactive alias로만 보존한다.

이미 Billing에 반영된 entitlement는 같은 토선생 계정 로그인 뒤 current entitlement 조회로 복원한다. Store 결제 후 Billing 반영 전에 앱이 종료되면 RevenueCat HMAC webhook과 API reconciliation이 같은 Store transaction key로 재검증한다. phone 재가입은 새 user와 새 reference를 사용하며 유료권을 이전하지 않는다. 향후 Guest purchase를 허용하려면 payment aggregate와 RevenueCat customer alias의 `UserMerged` migration을 별도 승인한다.

RevenueCat SDK가 완료한 consumable purchase는 Store/RevenueCat Entitlement restore를 기간 권리 source로 사용하지 않는다. Billing ledger가 기존 entitlement 복원의 source of truth다. RevenueCat restore behavior는 original App User ID에 유지하며 ACTIVE MEMBER가 Billing 발급 `purchaseAccountRefId`로 식별된 뒤에만 구매한다. anonymous purchase와 RevenueCat alias/restore에 의한 다른 토선생 계정으로의 자동 이전은 금지한다.

## 4. 앱 API와 사용자 JWT

앱은 다음 Billing public API를 직접 호출한다.

- `GET /api/v1/payments/products`: 판매 가능한 상품 조회, `billing:read`
- `POST /api/v1/payments/purchase-account`: body 없는 stable purchase account reference 조회 또는 발급, `billing:purchase`
- `POST /api/v1/payments/sync`: Store purchase 동기화, `billing:purchase`와 lowercase UUID v4 `Idempotency-Key`
- `GET /api/v1/payments/entitlement`: 현재 paid entitlement와 종료 시각 조회, `billing:read`

sync는 SDK의 Store/transaction identifier를 untrusted lookup hint로만 받고 userId·가격·기간·receipt·purchase token을 받지 않는다. local entitlement commit까지 끝나면 200, RevenueCat에서 아직 거래를 확인할 수 없으면 `202 PENDING`과 `Retry-After`를 반환한다. exact DTO, stable error code와 rate limit 초안은 [ADR-004 §5.3](../adr/ADR-004-fixed-term-premium-payment-contract.md)에 작성했다.

Identity Access Token 계약:

- algorithm: RS256
- issuer/JWKS: 환경별 Identity exact 값
- audience: 기존 `tosunsaeng-learning-core`와 신규 `tosunsaeng-billing` 포함
- subject: lowercase canonical user UUID
- 필수 time/identity claim: `iat`, `exp`, `jti`
- Billing verifier clock skew: 최대 60초
- Guest 최소 scope: `billing:read`
- ACTIVE MEMBER 최소 scope: `billing:read`, `billing:purchase`

Billing은 자기 audience, issuer, signature, expiry, subject와 route scope를 모두 검증한다. Billing reader를 먼저 배포하고 Identity가 다중 audience·scope token을 발급하는 reader-first 순서를 사용한다.

## 5. 구매·entitlement lifecycle

### 5.1 정상 구매

```text
로그인·상품 조회
→ purchaseAccountRefId로 RevenueCat 표준 SDK 구매
→ RevenueCat이 Store transaction 완료·정규화
→ 앱 purchase sync 또는 RevenueCat HMAC webhook
→ Billing이 RevenueCat API/webhook의 Store transaction 검증
→ event inbox/store transaction unique 확인
→ Purchase + SubscriptionEntitlement + ledger Transaction commit
→ current entitlement 반환
```

RevenueCat이 정규화한 Store purchase가 `VERIFIED`로 검증됐을 때만 권리를 만든다. SDK 성공 callback, product ID 또는 App User ID만으로는 권리를 만들지 않는다.

- provider purchase 상태: 최소 `PENDING`, `VERIFIED`, `REFUND_REVIEW`, `REFUNDED`, `REVOKED`
- entitlement 상태: 최소 `SCHEDULED`, `ACTIVE`, `EXPIRED`, `REVOKED`
- 자동 갱신용 grace, account hold와 cancel-scheduled는 현재 범위에 없다.

기준 `startsAt`은 provider가 증명한 purchase 시각이다. 두 Store 모두 verified product ID에 매핑된 Billing catalog duration으로 `endsAt`을 계산한다.

### 5.2 재구매와 기간 연결

```text
startsAt = max(providerStart, currentPaidTimelineEndsAt)
endsAt   = startsAt + offerDuration
```

구매별 entitlement와 purchase 연결을 보존하며 기존 entitlement 기간을 덮어쓰지 않는다. 동시 구매도 provider transaction unique와 Mongo Transaction으로 하나의 결정적 timeline에 수렴해야 한다.

중간 entitlement가 refund/revoke되면 해당 purchase slot만 제거하고 뒤의 VERIFIED entitlement를 기존 sequence대로 앞으로 당긴다.

```text
다음 startsAt = max(appliedAt, 앞선 유효 entitlement endsAt)
다음 endsAt   = 다음 startsAt + 해당 offerDuration
```

원래 schedule, revoke와 재배치는 append-only ledger에 모두 남긴다.

### 5.3 시험 Reservation

```text
ACTIVE paid entitlement 있음
→ source=SUBSCRIPTION Reservation
→ unit 차감 없음
→ usage audit와 AttemptGroup 생성
→ FREE_EXAM_ONCE 변경 없음

ACTIVE paid entitlement 없음
→ 기존 FREE_EXAM_ONCE resolver
```

앱은 authorization source나 entitlement ID를 선택하지 않는다. Billing이 현재 상태와 우선순위로 결정한다.

### 5.4 정상 기간 만료 — D1-A 승인

- 만료 전 confirm된 현재 Session은 Learning Core의 기존 시험·제출 기한까지 완료할 수 있다. 별도 무기한 연장은 없다.
- 만료 전 reserve가 commit된 동일 Session은 기존 5분 Reservation 유효기간 안에서 confirm을 허용한다. 환불이 먼저 commit되면 이 예외 없이 거절한다.
- 만료된 권리로 새 INITIAL/replacement를 만들지 않는다. 다음 유효 권리가 있어도 기존 group에 자동 재결속하지 않고, 기존 group 종료·guard 해제를 검증한 뒤 새 권리의 새 INITIAL로 시작한다.

## 6. refund·revoke·chargeback

- RevenueCat이 Store에서 수신·검증한 최종 상태만 금전 환불·revoke 근거로 사용한다.
- client의 환불 신청 주장만으로 상태를 바꾸지 않는다. provider-authenticated refund review/consumption request가 있으면 `REFUND_REVIEW`로 신규 INITIAL·replacement를 일시 중지하고 최종 결과로 해제 또는 확정한다.
- 기존 purchase, ledger와 usage history를 삭제하거나 덮어쓰지 않고 append-only reversal을 연결한다.
- 해당 paid source의 새 INITIAL Reservation은 즉시 차단한다.
- `RESERVED`: cancel/expiry와 생성된 Session compensation으로 종료한다.
- `OPEN`: access-revoked terminal로 전환하고 답안·제출·채점·replacement를 차단한다.
- `RETAKE_AVAILABLE`: replacement를 차단한다.
- `GRADING`: 이미 제출된 채점·Summary를 terminal까지 수렴시킨다.
- `COMPLETED`: 과거 Session·Result·Summary를 삭제하지 않는다.
- Billing은 durable access-revocation event를 발행하고 Learning Core는 exact AttemptGroup/Session projection으로 실제 접근을 fail-closed한다. exact event name·route·wire는 payment ADR에서 고정한다.
- refund와 reserve/confirm이 경합하면 먼저 commit된 상태와 CAS로 수렴한다. refund가 먼저면 이후 confirm을 revoked error로 거절하고 Learning Core가 Session을 access-revoked로 보상한다.
- Store가 환불을 확정하기 전에 이미 완료된 서비스는 회수할 수 없다. provider가 지원하면 consumption evidence를 제공하고 식별자를 metric label에 넣지 않는 `REFUNDED_AFTER_USE` 운영 지표로 반복 악용을 관찰한다.
- 환불 뒤 다른 ACTIVE paid 권리가 없으면 미사용 무료권으로 별도의 새 INITIAL 시험을 시작할 수 있다. refunded AttemptGroup을 무료권으로 자동 재결속하지 않는다.
- 자동 잔여시간 공제/환급액 산정, negative balance와 자동 환불 집행은 범위 밖이다. Store-confirmed 부분 환불의 실제 반환액 기록·해당 구매 잔여권 종료는 10/6 승인된 후속 설계 범위다. exact 증거·원장 경로는 출시 전 검증하며 미구현을 법정 반환 요청 거절 근거로 사용하지 않는다.

### 6.0 환불 신청·고객지원 창구 — C9-S10 승인(2026-09-09)

- 2026-10-06 상태: Identity 로컬 cc076442에서 REFUND 분류/인증된 활성 MEMBER·GUEST userId 귀속 구현·테스트 코드를 확인했다. 익명401 SUPPORT_REFUND_AUTH_REQUIRED, body userId400, 접수는 환불 집행 아님. 아래 Identity 구현 후속 표기는 배포·프론트·구매 연결 통합 확인으로 갱신한다. 이번 실제 테스트 실행/운영 확인은 없으며 기본 접수/Slack flag는 false다.

- 2026-10-06 R1/R2 승인: 정상 환불 건은 처리 후 종료하며 일반 반복 환불 기능은 없다. 검증된 외부 추가 반환/정정은 금전 원장만 보완하고 권리 종료/reflow를 반복하지 않는다. 불명 금액은 추정하지 않고 대사한다. 위 appliedAt은 최초 성공한 Billing 권리 종료 Transaction의 고정 반영 기준이다. providerConfirmedAt은 미제공 시 null로 두고 firstVerifiedAt과 분리한다. 미시작 후속 기간만 앞당기며 기존 시작을 뒤로 미루거나 이미 시작한 다른 권리를 재지급하지 않는다. 늦은 알림으로 다음 이용권 시간을 소급 소진하지 않고 나중에 정확한 Store 시각을 받아도 재배치하지 않는다. LC 차단의 비동기 지연과 법적 효력 시각은 별도다.

- 2026-10-06 추가 승인: 검증된 부분 환불 확정 시에도 해당 구매의 남은 이용권은 종료한다. 다른 구매의 기간권과 무료권은 유지하며 뒤의 정상 구매 timeline reflow와 기존 상태별 차단을 따른다. 금전 원장은 실제 부분 환불액을 보존한다. Google 팀 검토 후 지원되는 Store 환불 실행/Apple 신청 안내 후 최종 결과 반영으로 운영한다. 아래 부분 환불 범위 제외는 이번 정책 승인 및 후속 설계에 한해 대체하며 provider 증거·멱등/원장 구현 완료를 뜻하지 않는다.

- 2026-10-06 최신 운영 승인: Identity 문의 `REFUND`로 접수하고 팀이 범위/금액을 건별 검토한다. 실제 provider 환불 확정 시각을 운영 기준으로 삼고 2영업일 이내 1차 답변한다. userId는 필수 귀속하되 검증된 인증/현재 계정으로 서버가 자동 연결한다. 약관 작성/고지는 프론트 담당이다. 아래 자체 신청 UI 제외는 문의 접수에 한해 대체된다. 법정 반환/해지 효력·공제 공식, provider 정확 시각 매핑과 부분 환불 실행·원장은 별도 검증 대상이며 로그인 불가/탈퇴 요청은 이메일·Store 경로로 본인/구매자 확인한다. 임의 userId만으로 권리를 변경하지 않는다.

- 앱의 Identity 문의 `REFUND`로 상담을 접수한다. Google은 검토 후 지원되는 Store 환불 실행, Apple은 고객의 Store 신청 안내 후 Apple 최종 심사 결과를 반영한다. Store 직접 신청과 지원 이메일도 유지한다. Billing 자체 환불 집행 API·직접 송금·자동 심사 UI를 추가하지 않는다.
- 토선생은 권리 미반영·서비스 장애·미제공·법령상 요청을 `tosunsaeng093@gmail.com`으로 접수한다(C9-S11). 실제 담당자/수신·응대 운영은 출시 전 확인한다. Store 거절만으로 별도 법적 요청을 종료하지 않는다.
- 검증된 환불 결과에 따른 ledger·권리/LC 차단은 계속 Billing 책임이다. Store 신청이나 client callback만으로 완료 처리하지 않으며 부분 환불의 RC 탐지/금액 전달도 검증 전이다.
- 문의자→검증된 구매→해당 구매의 시험 승인/완료·실패 최소 증거 연결은 ADR-004 §5.8.1을 따른다. 기존 projection/ledger를 재사용하고 무료/취소/재시도/삭제된 원본을 구분한다. 통상 한 환불 처리로 종료하되 실제 Store 추가 반환/정정의 원장 처리와 일반 반복 환불 서비스는 구분한다.
- [구매·환불 안내 초안](PREMIUM_PURCHASE_REFUND_NOTICE_DRAFT.md)을 작성했다. 게시용 최종 약관이 아니며 잔여기간 환불식·자동 승인·환급 소요시간·결과 영구 열람을 약속하지 않는다. 자동 refund handling OFF와 D1/D2는 유지한다.

정상 만료 후 기존 결과·피드백은 본인 계정과 기존 보존 정책 범위에서 열람할 수 있다(C9-S11). 탈퇴 뒤 복원·영구 보관·새 시험/임의 재채점 허용이 아니며 D1 현재 Session 완료·승인 장애 복구 계약은 유지한다. 실제 LC/앱 동작은 결제 연동 후 검증한다.

### 6.1 환불 취소 — D2-A 승인

Apple `REFUND_REVERSED` 또는 `refunded → owned` 재관측은 신규 구매로 처리하지 않는다. 최초 출시에서는 durable `REVIEW_REQUIRED`와 경보로 수렴하고 자동 기간 복구·재지급·Session 재개는 하지 않는다. 담당자가 Store 최종 상태를 확인한 뒤 별도 승인된 복구 절차로 처리한다. 원장 덮어쓰기와 영구적인 권리 거절을 의미하지 않는다.

## 7. RevenueCat client sync·webhook·transaction completion

- 앱 purchase sync는 결제 직후 빠른 활성화를 담당한다.
- RevenueCat 표준 SDK가 Apple transaction finish와 Google consumable completion을 담당한다. 기존 `Billing commit → Apple finish/Google backend consume` 순서는 이 결정으로 supersede한다.
- Apple App Store Server Notifications와 Google RTDN은 RevenueCat에 연결한다. 같은 RevenueCat project에 iOS/Android app을 두고 `SANDBOX → staging`, `PRODUCTION → production`으로 filter한 두 webhook integration과 환경별 URL·Authorization·HMAC secret을 사용한다.
- webhook은 고정 Authorization header와 `X-RevenueCat-Webhook-Signature`의 raw body HMAC-SHA256을 모두 검증한다. timestamp replay window는 5분이고 constant-time compare를 사용한다.
- 최소 event inbox를 durable commit한 뒤 빠르게 200을 반환하고 실제 payment 반영은 worker가 수행한다. 같은 event ID 재전송과 같은 Store transaction의 서로 다른 event는 모두 멱등하게 수렴한다.
- required envelope/field type·size·environment·app/product/account binding은 엄격히 검증하되 RevenueCat이 추가하는 optional unknown field는 허용한다. 인증된 unknown event type은 durable ignored disposition과 운영 경보로 200 수렴한다.
- 앱 callback은 즉시 동기화 trigger일 뿐 지급 증거가 아니다. Billing은 인증된 사용자에 연결된 `purchaseAccountRefId`로 RevenueCat API를 조회하거나 검증된 webhook을 처리한다.
- Store 결제 완료 후 Billing 반영이 지연되면 entitlement를 추측해 열지 않고 `PENDING`으로 표시한다. RevenueCat retry와 scheduled API reconciliation으로 복구한다.
- 정상 인증 webhook은 직접 정규화·반영하고 RevenueCat REST API는 client sync, 모호한/불완전 event와 scheduled reconciliation에 사용한다. 모든 webhook마다 동기 API 조회를 요구하지 않는다.
- RevenueCat의 `Track new purchases from server-to-server notifications`와 자동 `Refund request handling`은 최초 출시에서 OFF로 유지한다.
- Apple/Google별 Store 장애와 RevenueCat 장애를 별도 operation/outcome으로 관측하며 어느 경로에서도 client 주장으로 fail-open하지 않는다.

## 8. reconciliation

scheduled reconciliation은 RevenueCat API로 다음 local record를 bounded batch 재검증한다.

- `PENDING`
- Store-completed/Billing-pending
- `ACTIVE`
- `SCHEDULED`
- 최근 `REFUNDED`, `REVOKED`, `EXPIRED`

기본 실행값:

- `PENDING`, Store-completed/Billing-pending: 5분마다
- `ACTIVE`, `SCHEDULED`: 6시간마다
- 최근 90일 `REFUNDED`, `REVOKED`, `EXPIRED`: 하루 한 번
- RevenueCat API cursor와 기본 100건 bounded batch

RevenueCat이 제공하는 current Store state와 local state가 다르면 기존 Store transaction business key로 동일한 검증·Transaction service를 재사용하고 append-only correction/reversal을 만든다. 일시 오류는 bounded exponential backoff, 반복 실패는 dead-letter와 운영 경보로 격리한다. interval, lookback와 batch는 staging API quota 측정 뒤 환경 설정으로 보수적으로 조정할 수 있다.

## 9. 보안·개인정보 경계

- 첫 public ingress는 같은 환경의 기존 public ALB를 재사용하고 Billing 전용 hostname/path allowlist, listener rule과 별도 target group을 사용한다. 승인된 public/provider route 외에는 fixed reject한다.
- public 사용자 API, RevenueCat Authorization+HMAC webhook endpoint와 internal Lattice API는 별도 principal과 SecurityFilterChain을 사용한다.
- 앱은 `/internal/**`, repair route, Reservation source와 다른 사용자의 payment/entitlement를 호출하거나 선택할 수 없다.
- raw receipt, signed JWS, purchase token, Store/RevenueCat credential과 webhook 전문을 일반 log·metric·ledger에 남기지 않는다.
- 저장이 필요한 RevenueCat/store transaction reference는 최소화·암호화하고 응답이나 trace attribute에 노출하지 않는다.
- price와 product display는 Store/RevenueCat Offering 값, entitlement 지급은 Billing catalog와 verified Store transaction이 진실 공급원이다.
- internal route는 ALB listener에 연결하지 않고 기존 VPC Lattice AWS_IAM만 사용한다. 이 payment 결정은 ADR-002의 무료-only "Billing ALB 없음"을 public route에 한해 supersede한다.

### 9.1 보존

- 정규화된 `Purchase`, `SubscriptionEntitlement`, refund/revoke와 payment ledger: `max(provider finalAt, entitlement endsAt, last reversalAt)`부터 5년
- provider event inbox digest·disposition: 120일
- raw receipt, signed JWS, Store notification과 RevenueCat webhook 전문: 저장하지 않음
- 재조회용 RevenueCat/store transaction reference: 최소 field 암호화, 최대 5년
- disaster recovery backup: 최대 35일 rolling
- 5년 만료 뒤 canonical user 연결과 erasable provider lookup reference 삭제 또는 비가역 비식별화

## 10. 구현 전 남은 운영 입력

제품 정책 선택은 완료됐지만 다음 값은 구현 ADR 또는 출시 준비에서 필요하다.

1. Apple Consumable In-App Purchase product ID 5개와 Google consumable one-time product ID 5개
2. 각 상품 가격, 판매 국가와 세금/현지화 설정
3. 양 Store의 consumable/one-time 상품 review 결과
4. 실제 public hostname, ALB listener/certificate/SG inventory와 exact API DTO/error/rate limit
5. Mongo collection/index, transaction retry와 RevenueCat API/HMAC secret 관리
6. RevenueCat project/app, Offering/Package와 custom App User ID의 실제 Console 설정
7. 환경별 webhook URL·Authorization/HMAC secret과 staging API quota 측정 뒤 reconciliation 운영값
8. Store sandbox 계정, Store notification→RevenueCat 연결과 production key rotation runbook
9. 5년 보존보다 긴 관할 법령·Store 의무가 있는지 출시 전 최종 확인

## 11. 구현 권장 순서

1. payment ADR과 schema/API 계약
2. product catalog와 purchase account binding
3. provider-neutral Purchase·SubscriptionEntitlement·ledger
4. public JWT security와 상품/권리 조회 API
5. RevenueCat standard SDK·Offering과 앱 purchase sync
6. RevenueCat HMAC webhook·REST API adapter와 Store transaction 정규화
7. Reservation paid-first resolver
8. refund/revoke, timeline reflow와 Billing→Learning Core access-revocation
9. reconciliation·retention worker
10. RevenueCat+Store sandbox·Mongo replica-set·중복/역순/응답 유실·refund race E2E
11. feature flag canary와 production activation

무료시험·owner rebind production gate와 결제 개발은 병행할 수 있지만, 두 기능 모두 실제 Lattice/IAM/SG·Mongo migration·staging E2E 전에는 production feature flag를 활성화하지 않는다.
