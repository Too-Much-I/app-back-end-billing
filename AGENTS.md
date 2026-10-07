# 토선생 Billing Service Codex 작업 규칙

이 규칙은 저장소 전체에 적용한다. 이 저장소는 토선생 앱의 Billing/Entitlement Service이며, 아래 도메인 경계와 계약을 유지한다.

명시적인 요청이 없으면 현재 Billing 저장소의 코드와 문서만 변경한다. Identity와 Learning Core는 계약 확인을 위한 읽기 대상으로만 사용하고, 각 저장소의 코드·설정·배포를 함께 수정하지 않는다.

## 기술 환경

- Java 21
- Spring Boot 3.4.2
- Gradle Groovy
- MongoDB
- Spring Security OAuth2 Resource Server
- 기본 테스트 명령: `./gradlew clean test`

## 프로젝트 역할과 도메인 경계

Billing Service가 소유하는 기능은 다음과 같다.

- 상품과 서버 기준 가격
- RevenueCat을 통한 Apple App Store·Google Play 결제 검증 데이터 수신과 결제·취소·환불 원장
- 유료·프로모션 credit ledger와 unlimited pass
- 검증된 휴대전화 기준 무료 1회 `TrialClaim`
- 시험 생성 전 entitlement `Reservation`, 확정, 취소, 만료
- 출석·추천인·coupon 보상 원장
- entitlement owner 이전과 정합성 복구

Billing Service가 소유하지 않는 기능은 다음과 같다.

- 사용자 계정·로그인·토큰 발급
- 시험 문제·세션·응시·AI 채점·시험 결과
- 음성 파일과 S3 업로드
- Learning Core의 재채점 및 polling 상태

Identity 또는 Learning Core 코드를 이 저장소로 복사하지 않는다. AI, 시험, 음성, S3 로직을 Billing에 추가하지 않는다.

## 현재 제품 범위

verified-phone candidate 기준 무료 모의고사 1회 Entitlement와 owner lifecycle의 핵심 애플리케이션 구현은 완료됐다. 다음 제품 범위는 자동 갱신 없는 fixed-term premium 결제로 승인됐으며, exact ADR·구현 계획과 Jira를 먼저 확정한 뒤 vertical slice로 구현한다.

- Identity `PhoneEligibilityBindingVerified`/`PhoneEligibilityBindingRevoked` event consumer
- event inbox, revision high-water와 `trial_eligibility` current projection
- `TrialClaim`, `FREE_EXAM_ONCE` grant와 ledger
- 시험 시작 전 Reservation reserve, confirm, cancel, status와 5분 expiry
- AttemptGroup consumption 연결과 Learning Core reconciliation
- Mongo transaction, unique index, 멱등성, 관측성과 운영 복구
- Apple Consumable In-App Purchase·Google consumable one-time product와 RevenueCat 표준 SDK·Offering 연동
- `PREMIUM_1D`, `PREMIUM_3D`, `PREMIUM_7D`, `PREMIUM_14D`, `PREMIUM_28D`와 기간형 `SubscriptionEntitlement`
- 앱이 직접 호출하는 Billing public API와 Identity JWT의 `tosunsaeng-billing` audience
- ACTIVE paid-first/free-preserve Reservation resolver
- client sync, RevenueCat Authorization+HMAC webhook·REST API reconciliation과 표준 SDK transaction completion
- refund/revoke의 append-only reversal과 신규 시험 차단
- Store-confirmed 부분 환불의 실제 반환액 기록과 해당 구매 잔여권 종료(2026-10-06 승인). 구매별 최소 이용 증거 연결을 결제 ADR/PLAN에 포함하며 exact schema·provider 증거 검증과 구현은 후속이다.
- ACTIVE MEMBER 전용 구매, 사용자·환경별 stable `purchaseAccountRefId`
- 기존 public ALB의 Billing 전용 host/path allowlist·별도 target group과 internal Lattice 분리
- refund 시 entitlement timeline reflow, OPEN/RETAKE_AVAILABLE 차단·GRADING 완료·COMPLETED 보존
- 정규화 payment/ledger 5년, provider event inbox 120일과 35일 rolling backup

다음 기능은 후속 단계이며 명시적인 구현 요청과 선행 계약 승인 없이 추가하지 않는다.

- paid credit pack, fixed-unit exam pass와 자동 갱신 subscription
- coupon, 출석과 추천인 보상
- 자동 환불 심사·자동 공제액 산정, 수동 보상과 Billing 자체 negative balance. 승인된 Store 부분 환불 결과 수신·원장 반영은 이 범위 제외에 해당하지 않는다.

## 현재 구현 단위

2026-10-06 결제 계획은 `docs/plans/PLAN-008-fixed-term-payment-roadmap.md`와 PLAN-009~013(상품/계정, 구매/기간권, paid 시험/증거, 환불/차단, 배포/운영)에 작성됐으며 검토·구현 승인 대기다. 아래의 결제 PLAN 이후 작성 표현은 이 기록으로 갱신한다. 새 PLAN의 envelope·상대 wire·schema 기술 제안은 ADR 동기화/Phase 0 검증 없이 확정 계약으로 구현하지 않는다. Jira·결제 코드·배포는 아직 수행하지 않았다.

retained trial owner rebind vertical slice는 Jira `TMI-120`, `docs/plans/PLAN-006-retained-trial-owner-rebind.md`와 `docs/adr/ADR-003-retained-trial-owner-rebind-contract.md`에 구현됐다. `docs/plans/PLAN-007-public-free-entitlement-query.md`의 무료 사용권 public reader는 사용자 승인 후 구현·로컬 테스트를 완료했으며 기본 OFF다. 배포·legacy coverage는 `docs/runbooks/PLAN-007-public-reader-rollout.md`를 따른다. 결제 기술 초안은 `docs/adr/ADR-004-fixed-term-premium-payment-contract.md`에 작성됐지만 별도 후속 트랙이다. 제품 정책과 C9-S8의 9개 선택은 승인됐고 2026-09-07 4주(28일) 상품 변경과 정상 기간 만료(D1-A)·환불 취소(D2-A) 권장안도 승인됐으며, 결제 PLAN은 이후 번호로 작성한다. 결제 application code·schema v5는 아직 구현하지 않았다.

무료 reader는 `GET /api/v1/entitlements`, Identity 사용자 JWT `tosunsaeng-billing` audience와 `billing:read`, 검증된 `sub`를 사용한다. Guest/MEMBER 모두 본인 조회만 허용하고 account_type만으로 phone eligibility를 추론하지 않는다. Grant가 없는 신규 VERIFIED 대상은 retained Claim 부재가 확인될 때만 예상 신규 수량을 표시하며 조회에서 발급·hold·소비·owner 변경을 하지 않는다. 연동 불명은 PENDING/null, 저장소 실패는 503으로 분리한다. public 전용 envelope와 ALB connector는 internal Lattice/SigV4와 격리하고 기존 internal DTO를 변경하지 않는다. Identity account_type PR #39 병합은 Billing audience·billing:read 발급 완료가 아니며 별도 후속과 staging 검증 전 reader flag는 OFF다.

PLAN-007 §8 구현: 세션 귀속은 기본 7일 command TTL에 의존하지 않는다. link의 sessionOwnerEpoch/sessionBindingVersion과 Reservation·AttemptSession의 epoch snapshot을 기존 reserve/confirm/owner rebind Transaction에서 기록한다. PHONE_REJOIN 실제 이전만 epoch를 증가시키고 USER_MERGED는 보존한다. GET은 이를 읽기만 하며, legacy 증빙 불명은 별도 bounded migration·활성화 gate로 해결하고 command 소실을 무기한 PENDING으로 숨기지 않는다. 증빙 없는 legacy confirm은 drain/expire 또는 승인 이관 후 처리한다. epoch writer를 증빙 없는 구버전으로 무조건 rollback하지 않는다. projection 없는 Guest의 PENDING은 '사용 가능 여부를 확인할 수 없음'이며 실제 job 처리나 곧 완료를 뜻하지 않는다.

포함 범위:

- phone `TrialOwnerRebindApproved`와 Guest `UserMerged` v1 lifecycle별 strict decoder
- `owner_rebind_inbox`, `subject_owner_rebinds`와 Mongo schema v4 exact index
- `BillingSubjectLink.ownerVersion/ownerUpdatedAt` reader-first source→target CAS
- phone revision/state/candidate-to-Claim prerequisite와 Guest 100 subject all-or-nothing
- active Reservation/PROCESSING과 phone `GRADING`의 `503 OWNER_REBIND_PENDING`
- phone 미사용/`OPEN`/`RETAKE_AVAILABLE` owner 이전과 `COMPLETED` 성공 `NOOP`
- pre-rebind exact AttemptGroup/Session legacy-source fence와 terminal 종료
- 최대 1시간 cleanup worker, 24시간 overdue 경보와 privacy-safe log/metric/trace
- replica-set Testcontainers 기반 legacy version, transaction, duplicate와 동시성 회귀 검증

결제 ADR·PLAN 승인 전에는 RevenueCat adapter, public payment endpoint, Purchase·SubscriptionEntitlement collection/index와 webhook receiver를 구현하지 않는다. 결제 구현은 기존 TrialClaim·무료 Grant·owner rebind를 변경하거나 새 무료권을 지급하지 않으며 coupon·credit·자동 갱신을 포함하지 않는다. phone 재가입은 Learning Core owner event를 만들지 않고 Billing continuation discovery와 exact reserve echo로 승인된 기존 AttemptGroup에 새 replacement Session을 생성한다. 무료/owner 기능과 결제 기능 모두 실제 schema·AWS·RevenueCat·Store sandbox·staging E2E gate 전에는 production flag를 활성화하지 않는다.

## 핵심 불변식

- mutable balance만을 원장으로 사용하지 않고, 지급·결제·사용 기록은 추적 가능한 ledger로 보존한다.
- 결제 및 entitlement 처리에는 idempotency key와 provider event 식별자를 사용해 중복 지급·차감을 막는다.
- `TrialClaim`은 consumer-scoped verified-phone candidate와 benefit type 기준으로 유일하다.
- `claimedAt + 3년` 보존기간 안에는 탈퇴·재가입·merge·revoke·cancel·expiry로 Claim을 삭제하거나 다시 열지 않는다.
- `retentionExpiresAt` 이후 candidate alias와 사용자·source event 연결은 dedupe에서 제외하고 승인된 purge 정책에 따라 삭제한다. 그 뒤 같은 번호가 다시 verified되면 새 Claim을 허용한다.
- TrialClaim 연결정보 purge는 매일 실행하고 logical expiry 후 24시간 안에 active DB에서 제거한다. 재해복구 backup은 최대 35일이며 복구본은 사용자 트래픽 연결 전에 expiry purge를 먼저 적용한다.
- raw phone을 Billing에 전달하거나 저장하지 않는다.
- 현재 paid 상품은 credit를 차감하지 않는 fixed-term unlimited 권리다. 과거의 시험 1회 10 credits 초안은 구현하지 않는다.
- Reservation의 `RESERVED` TTL 초기값은 5분이다. TTL은 확정된 사용을 만료시키지 않는다.
- 시험 생성 흐름은 `reserve → Learning Core Session commit → confirm` 순서를 유지한다.
- 공개 `POST /api/v1/exams`는 필수 lowercase UUID v4 `Idempotency-Key` header를 사용하며 Request Body 없음과 기존 성공 Response DTO를 유지한다.
- 시험 생성 응답 유실의 transport retry는 같은 key·같은 Session으로 수렴한다. 앱 종료 뒤 의도적 restart는 새 key·새 examId를 사용하고 기존 Session을 이어풀지 않는다.
- restart는 기존 결과·upload·Job·summary를 승계하지 않고 같은 AttemptGroup consumption과 `mockExamId`를 유지한다.
- confirm/cancel은 멱등이고 `CONFIRMED`를 cancel로 되돌리지 않는다.
- Reservation allocation은 원래 grant 단위를 보존해 cancel 시 정확히 복구한다.
- 장애 시 reservation과 Learning Core Session을 reconciliation하여 중복 Session이나 이중 차감을 만들지 않는다.
- Apple/Google 구매는 클라이언트 주장만 신뢰하지 않는다. RevenueCat HMAC webhook 또는 Billing이 인증한 RevenueCat REST API 응답의 Store transaction만 정규화하고 entitlement 지급 근거로 사용한다.
- 구매는 Identity가 `billing:purchase`를 발급한 ACTIVE MEMBER만 허용한다. Guest는 최대 `billing:read`만 받으며 Guest purchase·paid UserMerged migration은 현재 범위에 없다.
- `purchaseAccountRefId`는 사용자·환경별 stable lowercase UUID v4다. 회전된 과거 값은 기존 transaction 검증·환불용 inactive alias일 뿐 신규 구매에 사용할 수 없다.
- active/scheduled paid timeline 중간 purchase가 refund/revoke되면 해당 slot만 제거하고 뒤의 VERIFIED entitlement를 기존 sequence·duration대로 즉시 앞으로 재배치하며 원래 schedule과 조정을 ledger에 남긴다.
- 2026-10-06 R2: reflow는 최초 성공한 Billing 권리 종료 Transaction의 고정 appliedAt 기준이다. Store 확정 시각(providerConfirmedAt, 없으면 null)·최초 검증 시각과 분리하고 미시작 후속 기간을 지연 때문에 소급 소진하지 않는다. 이미 시작한 다른 권리는 유지하며 기존 시작을 늦추지 않는다. 중복·외부 추가 반환·후속 정확 시각 보완으로 권리를 재종료하거나 기간을 재배치하지 않는다. R1: 외부 추가 반환/정정은 검증된 금전 사실만 기록하고 불명 금액은 대사하며 일반 반복 환불 기능은 제공하지 않는다.
- provider-confirmed refund 뒤 해당 source의 `RESERVED`, `OPEN`, `RETAKE_AVAILABLE`은 추가 사용·replacement가 불가능하다. 이미 제출된 `GRADING`만 terminal까지 수렴하고 `COMPLETED` history는 삭제하지 않는다.
- refund가 reserve/confirm과 경합하면 Transaction/CAS commit 순서로 수렴한다. refund가 먼저면 confirm을 revoked error로 거절하고 Learning Core Session을 durable access-revocation으로 보상한다.
- refund 신청에 대한 client 주장만 신뢰하지 않는다. RevenueCat이 Store에서 수신·검증한 review 신호는 `REFUND_REVIEW`, RevenueCat API/webhook이 제공하는 최종 Store 상태만 `REFUNDED/REVOKED` 근거다.
- 2026-10-07 사용자 승인 예외: RevenueCat 증거가 부족한 Google 소모성 상품 부분 환불은 Billing이 인증한 Google Orders 읽기 전용 조회 결과를 보완 증거로 허용한다. 기존 검증된 Purchase의 앱/환경/거래·소유 연결과 성공 확정 상태를 검증한 뒤 금액 원장/해당 구매 잔여권 종료에만 사용한다. 신규 구매 지급·owner 이전·자동 환불 실행·임의 운영자 입력 허용이 아니며 RC/Google 중복 관측 수렴과 실제 fixture 검증 전 활성화하지 않는다.
- 환불은 Identity 문의 `REFUND`와 인증된 userId 귀속으로 접수하고 팀이 건별 검토하며 2영업일 내 1차 답변한다. Google은 지원되는 Store 환불 실행, Apple은 고객 신청 안내 후 Apple 최종 결과를 반영한다. 문의만으로 권리를 취소하지 않는다. 실제 반환액과 권리 종료를 분리해 부분 환불에도 해당 구매의 잔여권만 종료하고 다른 구매·무료권은 보존한다. 반복 환불을 일반 상품 기능으로 제공한다는 뜻은 아니며 외부의 추가 반환/정정 사실을 원장에서 누락해서도 안 된다.
- 구매별 이용 증거는 ADR-004 §5.8.1의 내부 source snapshot과 기존 Attempt projection/ledger를 사용한다. userId·현재 시험 목록 개수만으로 환불 거래/사용분을 확정하지 않으며 답안·피드백 원문을 복제하지 않는다. paid schema/내부 계약 확장은 별도 PLAN 승인 후 구현한다.
- RevenueCat은 결제 연동·검증 데이터 공급 계층이며 Billing entitlement의 source of truth가 아니다. consumable을 RevenueCat Entitlement에 연결해 기간 권리를 판정하지 않고, Billing catalog·Purchase·SubscriptionEntitlement·ledger가 24·72·168·336·672시간과 stacking을 결정한다.
- RevenueCat 표준 SDK가 Apple transaction finish와 Google consumable completion을 담당한다. Store 결제가 완료됐지만 Billing 반영이 지연되면 client callback으로 권리를 지급하지 않고 `PENDING`으로 표시하며 HMAC webhook·event ID 멱등성·REST API reconciliation으로 최종 수렴한다.
- RevenueCat custom App User ID에는 실제 userId가 아니라 사용자·환경별 stable `purchaseAccountRefId`를 사용한다. 익명 상태 구매와 RevenueCat alias/restore에 의한 다른 토선생 계정으로의 구매·권리 자동 이전을 허용하지 않는다.
- 결제 public route는 `GET /api/v1/payments/products`, `POST /api/v1/payments/purchase-account`, `POST /api/v1/payments/sync`, `GET /api/v1/payments/entitlement`로 분리한다. sync는 필수 lowercase UUID v4 `Idempotency-Key`와 untrusted Store transaction hint를 사용하며 미확인은 `202 PENDING`과 `Retry-After`로 fail-closed한다.
- RevenueCat webhook은 Authorization header와 raw-body HMAC, 5분 timestamp window를 모두 검증하고 최소 inbox를 durable commit한 뒤 200을 반환해 worker가 처리한다. optional unknown field는 허용하고 인증된 unknown event type은 durable ignored disposition으로 수렴한다.
- 같은 RevenueCat project의 webhook을 SANDBOX→staging, PRODUCTION→production으로 분리하고 URL·Authorization·HMAC secret을 환경별로 다르게 사용한다. `Track new purchases from server-to-server notifications`와 자동 `Refund request handling`은 최초 출시에서 OFF다.
- 정상 인증 webhook은 직접 정규화·반영하고 RevenueCat API는 client sync·모호한 event·reconciliation에 사용한다. refund/revoke Learning Core 차단은 AttemptGroup별 durable outbox와 Lattice SigV4 event로 비동기 전달한다.
- Identity eligibility event를 수신하는 것만으로 TrialClaim, grant 또는 balance를 만들지 않는다. 최초 reserve Transaction에서 현재 binding과 기존 Claim을 확인해 지급과 Reservation을 원자적으로 처리한다.
- owner rebind는 새 Claim·Grant·allocation·consumption을 만들지 않고 stable `subjectRefId`의 current `BillingSubjectLink.userId`만 source→target CAS로 변경한다.
- active Reservation 또는 PROCESSING reserve command가 있으면 owner를 rewrite하지 않고 503 pending으로 재시도시킨다.
- phone 재가입은 AttemptGroup이 없거나 `OPEN`/`RETAKE_AVAILABLE`일 때만 owner를 이전한다. `GRADING`은 503 pending, `COMPLETED`는 owner/fence 변경 없는 성공 NOOP다.
- phone 재가입 뒤 재응시는 Learning Core가 Billing phone continuation route에서 authoritative AttemptGroup·mockExamId와 owner-epoch context를 먼저 조회하고 exact echo한 경우에만 허용한다. 기존 consumption을 유지하고 target 명의의 새 replacement Session을 처음부터 만들며 source의 기존 Session·답안·결과는 이전하지 않는다.
- rebind 이전 exact AttemptGroup/Session의 source status event만 bounded fence 안에서 상태 전진에 허용하고 신규 reserve·replacement·다른 Session 권한으로 사용하지 않는다.

상세 상품·사용권 계약과 미확정 선택지는 이 저장소의 `docs/codex/CONTRACT_DECISIONS.md`를 단일 기준으로 사용하고 fixed-term 결제 요약은 `docs/contracts/FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md`를 따른다. 서비스 간 전체 흐름은 `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, 내부 API와 Mongo 계약은 `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, Lattice·SigV4·환경 이관 계약은 `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`, owner rebind 계약은 `docs/adr/ADR-003-retained-trial-owner-rebind-contract.md`, 현재 구현 순서는 `docs/plans/PLAN-006-retained-trial-owner-rebind.md`를 따른다. payment public ALB 결정은 ADR-002의 무료-only "Billing ALB 없음"을 public route에 한해 supersede하며 internal route는 계속 Lattice-only다. 통합 안내서와 세부 ADR이 충돌하면 최신 승인 ADR을 따르며, 확정된 계약을 임의로 재해석하지 말고 작업을 중단해 보고한다.

과거 Learning Core 문서는 역사적 참고 자료일 뿐이며, 앞으로 Billing 관련 결정과 작업기록은 이 저장소의 `docs`에만 추가한다.

## 내부 API 계약 규칙

- 내부 eligibility namespace는 `/internal/v1/eligibility/{kind}/...` 형식을 사용한다.
- 현재 Trial event endpoint는 `/internal/v1/eligibility/trial/events`다.
- phone owner rebind endpoint는 `/internal/v1/eligibility/trial/owner/events`, Guest merge endpoint는 `/internal/v1/owners/merge/events`다.
- URL namespace만 공통화하며 Trial, paid, coupon의 DTO·권한·멱등성·aggregate와 저장소를 하나로 합치지 않는다.
- Identity가 이미 발행하는 wire event type과 schema v1 필드명을 임의로 변경하지 않는다.
- internal API는 앱용 `BaseResponse` wrapper를 사용하지 않고 승인된 internal DTO 또는 body 없는 204를 그대로 반환한다.
- request/response body 상한은 16 KiB이며 redirect를 허용하지 않는다.
- strict decode 전에 payload를 저장하지 않고 raw JSON 전문을 로그에 남기지 않는다.
- duplicate JSON field, trailing token, scalar coercion, unknown field와 잘못된 UUID casing을 거절한다.
- wire에 해당 field가 있는 Trial/phone contract는 `producer=identity`, `schemaVersion=1`, 승인된 event type과 환경 설정의 expected `consumerScopeId`가 exact match해야 한다. Guest `UserMerged` v1은 기존 5개 field 외 값을 추가하지 않고 endpoint와 principal로 producer 의미를 고정한다.
- canonical digest는 검증·정규화된 canonical JSON의 SHA-256으로 계산하며 멱등성 비교에만 사용한다.
- 같은 `eventId`와 같은 digest는 duplicate no-op, 같은 `eventId`와 다른 digest는 conflict다.
- 다른 `eventId`가 같은 producer·scope·user·revision을 주장하면 conflict다.
- 같은 user·scope의 낮은 revision은 stale 처리하고 최신 projection을 과거 상태로 되돌리지 않는다.
- inbox와 revision high-water, current projection 반영은 하나의 Mongo Transaction으로 처리한다.
- 최초 적용, duplicate와 stale은 local Transaction commit 또는 기존 commit 확인 뒤 body 없는 204를 반환한다.
- malformed는 400 `INVALID_REQUEST`, unknown contract·producer·scope는 422 `UNSUPPORTED_CONTRACT`, event conflict는 409 `EVENT_ID_CONFLICT`, 일시적 Mongo 장애는 503 `BILLING_TEMPORARILY_UNAVAILABLE`로 반환한다.

## Reservation 계약 규칙

PLAN-002 Reservation을 구현할 때 다음 계약을 유지한다.

- `reserve`, `confirm`, `cancel`의 `Idempotency-Key` header가 lowercase UUID v4 `operationId`의 유일한 wire source다. Request Body에 operationId를 중복해서 받지 않는다.
- `sessionId`는 Learning Core의 기존 `examId`, `mockExamId`와 함께 UUID로 가정하지 않는 1~128자 opaque token이다.
- `status`는 userId가 URL/access log에 남지 않도록 `POST /internal/v1/reservations/status` body로 조회하며 새 command나 ledger를 만들지 않는다.
- 같은 caller·user·operation·command와 같은 canonical payload는 기존 결과를 반환하고 다른 payload는 `IDEMPOTENCY_KEY_CONFLICT`다.
- INITIAL reserve에서만 Claim·grant가 필요하면 같은 Transaction에서 생성하고 allocation을 hold한다. REPLACEMENT는 기존 consumption을 재사용하며 추가 차감하지 않는다.
- phone continuation discovery는 `/internal/v1/reservations/continuations/phone`에서 target userId만 받고 적용 대상이 없으면 204, 있으면 `PHONE_REJOIN`, continuationId, 기존 attemptGroupId/mockExamId를 반환한다. reserve는 continuationReason/id/expectedAttemptGroupId 세 field의 all-or-none strict contract와 current owner transition exact match를 사용한다.
- TrialClaim은 cancel·expiry 때 삭제하거나 `claimedAt`을 갱신하지 않는다.
- confirm이 CANCELED 또는 EXPIRED 상태에 도착하면 자동 repair-confirm하지 않는다. privileged repair route는 별도 계약 전까지 열지 않는다.

## 사용자 및 서비스 인증 규칙

- 실제 사용자 ID는 UUID 문자열이며 JWT `sub`에서 가져온다.
- 클라이언트가 Request Body, Path, Query로 보낸 `userId`를 신뢰하지 않는다.
- 예외적으로 인증된 Identity eligibility event와 인증된 Learning Core internal route는 각 서비스가 확정한 lowercase canonical UUID `userId`를 body로 전달한다. 다른 principal, public path, query parameter 또는 임의 identity header의 userId는 신뢰하지 않는다.
- 결제 사용자 API를 추가할 때는 Identity만 사용자 token을 발급한다. Billing은 RS256 signature, exact issuer/JWKS, `tosunsaeng-billing` audience, expiry·iat·jti, lowercase UUID `sub`, 최대 60초 clock skew와 route별 scope를 검증한다.
- Identity는 Guest에 최대 `billing:read`, ACTIVE MEMBER에 `billing:read billing:purchase`를 발급한다. Billing은 client body/path/query/header의 account type이나 userId를 신뢰하지 않는다.
- 현재 내부 workload API는 VPC Lattice `AWS_IAM`, ECS task role과 SigV4를 사용한다. 별도 shared secret, API key 또는 workload JWT를 임의로 추가하지 않는다.
- Identity task role은 Trial eligibility와 승인된 Billing owner rebind event route만, Learning Core task role은 Reservation·status·AttemptGroup route만 호출할 수 있도록 최소 권한을 적용한다. `TrialOwnerRebindApproved`는 Learning Core에 전달하지 않는다.
- repair route는 일반 workload role과 분리된 운영 role만 허용한다.
- unsigned 요청, 다른 환경 role, 권한 없는 route와 direct task 우회는 거절한다.
- local/test에서는 실제 AWS credential이나 Lattice를 호출하지 않고 명시적인 test principal 또는 adapter로 workload 경계를 검증한다.
- workload 요청은 호출 서비스와 idempotency 정보가 검증되기 전까지 처리하지 않는다.
- 인증 계약이 완성되지 않은 endpoint는 fail-closed 상태를 유지한다.

## 보안 및 개인정보 규칙

- 실제 Secret, MongoDB URI, Store/RevenueCat credential, Apple/Google receipt·token·notification 또는 RevenueCat webhook 원문을 저장소에 추가하지 않는다.
- raw phone, token, receipt, payment instrument, 사용자 개인정보를 로그나 작업 문서에 기록하지 않는다.
- 결제 provider 응답은 검증에 필요한 최소 정보만 정규화하여 저장하고 민감 원문을 ledger에 복제하지 않는다.
- 환경변수 참조와 가짜 테스트 값만 저장소에 둔다.
- 테스트에서 실제 Atlas, Apple, Google, RevenueCat, Identity, Learning Core를 호출하지 않는다.
- 실제 AWS role ARN, VPC·subnet·security group·Lattice 식별자를 코드나 테스트에 하드코딩하지 않는다.
- 정규화된 Purchase·SubscriptionEntitlement·refund/revoke·payment ledger는 최종 provider transaction, entitlement 종료와 마지막 reversal 중 가장 늦은 시점부터 5년 보존한다. provider event inbox는 120일, disaster recovery backup은 최대 35일이다.
- raw receipt·signed JWS·Store notification·RevenueCat webhook 전문은 저장하지 않는다. RevenueCat/store 재조회 reference는 최소 field만 암호화하고 최대 5년 뒤 erasable user 연결과 함께 purge한다.

## MongoDB 및 원장 규칙

- 지급·예약·소비·취소·환불은 기존 원장 기록을 덮어쓰는 방식이 아니라 추적 가능한 상태 전이와 ledger entry로 남긴다.
- 유일성은 사전 조회만으로 보장하지 않고 명시적인 unique index와 duplicate-key 수렴으로 보장한다.
- 여러 document의 정합성이 필요한 Claim·grant·Reservation과 inbox·projection 반영은 Mongo Transaction을 사용한다.
- transaction이 필요한 운영 MongoDB는 replica set 구성을 전제로 한다.
- PLAN-001의 inbox collection은 `inbound_event_inbox`, current projection collection은 `trial_eligibility`를 사용한다.
- inbox에는 `ux_inbox_event_id`, `ux_inbox_identity_scope_user_revision`, `ttl_inbox_purge_at`; projection에는 `ux_trial_scope_user`, `ix_trial_key_version`을 승인된 이름과 option으로 생성한다.
- 운영 정합성을 `@Indexed` 또는 `auto-index-creation`에 의존하지 않는다. versioned initializer가 이름·key order·unique·partial filter·TTL option을 비교하고 불일치 시 fail-fast한다.
- 기존 index를 실행 중 임의 drop/recreate하지 않는다. production index 변경은 별도 migration·배포 단계로 승인한다.
- `inbound_event_inbox.purgeAt` 120일 TTL은 수신 멱등성 기록 보존용이다. 이것을 TrialClaim 3년 보존이나 Reservation audit 삭제에 재사용하지 않는다.
- `Reservation` audit document는 Mongo TTL index로 삭제하지 않는다. `expiresAt`을 기준으로 `RESERVED`만 명시적으로 `EXPIRED` 처리한다.
- TTL 또는 expiry는 `CONFIRMED` consumption을 취소하거나 grant를 복구하지 않는다.
- 동시 요청과 응답 유실에서도 같은 operation은 하나의 Claim, grant, Reservation과 consumption으로 수렴해야 한다.
- Mongo schema v4의 owner collection은 `owner_rebind_inbox`, `subject_owner_rebinds`이며 legacy missing `ownerVersion`은 logical 1로 읽고 첫 CAS에서 explicit version 2로 수렴한다.
- owner rebind inbox는 120일 멱등성용이며 raw payload와 source/target/subject/Claim을 저장하지 않는다. legacy fence의 source/group/session 연결은 terminal 또는 hard cap 뒤 cleanup한다.
- payment schema는 provider transaction/purchase token unique, provider event unique, user별 timeline sequence unique와 active/scheduled 조회 index를 명시하고 purchase·entitlement·ledger·timeline reflow를 하나의 Mongo Transaction/CAS로 수렴시킨다.
- payment retention은 Mongo TTL만으로 business state를 삭제하지 않는다. 명시적 purge worker가 5년 만료 뒤 user/provider lookup 연결을 제거하고 처리 건수·성공 여부만 기록한다.

## 테스트 규칙

- 변경한 비즈니스 로직에는 단위 테스트와 필요한 통합 테스트를 추가한다.
- Repository와 provider adapter는 가능한 경우 Mock으로 처리한다.
- Mongo transaction·unique index·동시성 검증은 replica set 기반 Testcontainers 통합 테스트로 수행한다.
- 모든 구현 작업 후 `./gradlew clean test`를 실행한다.
- 외부 API, ledger 불변식, 멱등성 계약이 바뀌지 않았는지 확인한다.
- workload route는 허용 role 성공뿐 아니라 unsigned, wrong role, wrong route와 direct bypass 실패도 테스트한다.
- PLAN-001은 duplicate field·unknown field·coercion·property/candidate 순서·whitespace·oversize와 expected scope mismatch contract test를 포함한다.
- Mongo 통합 테스트는 duplicate event, same revision conflict, stale/gap, unique-index race, transient transaction retry와 unknown commit 결과 수렴을 검증한다.
- PLAN-006은 legacy missing ownerVersion CAS, exact duplicate/concurrent owner event, active Reservation rollback, phone OPEN/RETAKE_AVAILABLE 이전·GRADING pending·COMPLETED NOOP, exact/expired Session fence, terminal cleanup과 식별자 비로깅을 검증한다.
- payment 구현은 RevenueCat event/store transaction duplicate, client retry/webhook/reconciliation 경합, 동시 timeline append, 중간 refund reflow, refund-reserve-confirm 순서 경합, OPEN/RETAKE_AVAILABLE revoke·GRADING completion, Guest purchase 거절과 paid-first/free-preserve를 검증한다.
- RevenueCat adapter 단위 테스트는 fake API response/webhook fixture를 사용하고 실제 RevenueCat·Apple·Google·credential을 호출하지 않는다. RevenueCat+Store sandbox E2E는 배포 gate에서 별도로 수행한다.

## 코드 변경 규칙

- 기존 패키지 구조와 코드 스타일을 우선한다.
- 명시적 요청 없이 새로운 운영 의존성이나 결제 provider SDK를 추가하지 않는다.
- 관련 없는 대규모 리팩터링을 하지 않는다.
- 도메인 계약 또는 외부 API에 영향을 주는 변경은 구현 전에 보고한다.
- Secret이나 개인정보가 포함될 수 있는 payload 전체를 로깅하지 않는다.
- 승인된 ADR이나 외부 wire 계약을 변경하는 구현은 먼저 영향과 migration 방식을 보고한다.

## Git 규칙

Codex는 다음 작업을 직접 수행하지 않는다.

- `git commit`
- `git push` 또는 force push
- `git reset --hard`
- 배포 및 GitHub Actions 추가

커밋과 push는 사용자가 직접 수행한다.

## 작업 기록 규칙

모든 Codex 작업이 끝나기 전에 다음을 수행한다.

1. `docs/codex/WORKLOG.md` 끝에 새 항목을 append한다.
2. `docs/codex/CURRENT_STATE.md`를 최신 상태로 갱신한다.
3. WORKLOG의 과거 기록은 수정하거나 삭제하지 않는다.
4. 코드 변경이 없는 분석 작업도 기록한다.
5. 기록에는 날짜, 브랜치, 목표, 변경 파일, 동작, 테스트 결과, 유지한 계약, 결정사항, 위험 요소, 다음 작업을 포함한다.
6. Secret, Token, Password, 실제 Key, 전체 MongoDB URI, 결제 원문과 개인정보를 기록하지 않는다.
7. Jira 이슈 키가 있으면 WORKLOG와 CURRENT_STATE에 기록한다.

## 계획·조사 문서 작성 규칙

계획, 조사, 분석, 리뷰 문서는 상세 내용을 빠짐없이 확인하되 사용자가 중요한 내용을 먼저 읽을 수 있도록 다음 계층으로 작성한다.

1. 5줄 결론
2. 사용자가 반드시 읽어야 하는 내용
3. 사용자가 결정해야 하는 사항
4. 주요 위험과 미확인 사항
5. 현재 작업과 직접 관련된 설명
6. 상세 조사 근거와 전체 표를 담은 부록

- 각 결론에는 가능한 경우 관련 코드, 설정, 테스트 또는 계약 문서의 파일 근거를 연결한다.
- 확인된 구현 사실, 문서상 계획, Codex의 분석·추론을 명확히 구분한다.
- 상세 조사 결과를 삭제하거나 축약해서 잃지 말고 긴 목록, 비교표와 보조 근거는 부록으로 이동한다.
- 정확한 field, enum, timeout, retry와 보안·과금 조건은 요약만으로 대체하지 않고 관련 계약 원문을 연결한다.

## 작업 완료 보고 규칙

각 구현 작업이 끝나면 다음 내용을 보고한다.

1. 변경한 파일
2. 변경한 동작
3. 유지하거나 변경한 외부 계약
4. 실행한 테스트와 결과
5. 남아 있는 위험과 미확인 사항
6. 배포 전에 확인할 사항
7. 실제 diff에서 예상 밖으로 변경된 파일이나 범위가 있는지 여부
8. 다음 작업 전에 확인할 사항

- 예상 밖의 변경이 없으면 없다고 명시하고, 있으면 사용자 변경과 이번 작업 변경을 구분해 설명한다.
- 테스트를 실행하지 않았다면 그 이유와 대신 수행한 검증을 기록한다.

## Jira 연동 규칙

- Jira 이슈 키가 있는 작업은 구현 전에 해당 이슈를 읽고 완료 조건을 기준으로 사용한다.
- Jira 생성·수정·댓글·상태 전환 전에 사용자 승인을 받는다.
- 명시적 승인 없이 Jira 상태를 변경하거나 댓글을 등록하지 않는다.
- Jira에 Secret, Token, 결제 원문, Store credential, MongoDB URI 또는 개인정보를 기록하지 않는다.
- Git commit과 push는 사용자가 직접 수행한다.

## 코드 리뷰 우선순위

리뷰할 때 다음 문제를 우선 확인한다.

1. Identity wire event type·schema·field가 변경됐는가
2. raw phone, candidate, token, receipt 또는 payload 원문이 로그·응답·문서에 노출되는가
3. event 수신만으로 TrialClaim이나 무료 grant가 지급되는가
4. event inbox·revision·projection 또는 Claim·grant·Reservation의 Transaction 경계가 누락됐는가
5. 사전 조회만 사용하고 unique index·duplicate-key 수렴이 없는가
6. 같은 idempotency key가 중복 지급·차감·Session을 만드는가
7. cancel·expiry가 `CONFIRMED` consumption을 되돌리는가
8. TrialClaim 3년 보존과 만료 후 purge·재수급 계약이 뒤바뀌었는가
9. Identity와 Learning Core workload role의 route 권한이 섞였는가
10. unsigned·wrong role·direct task 접근이 허용되는가
11. 테스트가 실제 Atlas, AWS, RevenueCat, Store, Identity 또는 Learning Core에 의존하는가
12. 승인된 fixed-term 결제·Store 부분 환불 결과 반영을 넘어 paid credit·coupon·자동 갱신·자동 환불 심사/공제 등 범위 밖 기능이나 관련 없는 대규모 리팩터링이 포함됐는가. 부분 환불 금전 기록과 구매 잔여권 종료를 혼동하거나 외부 추가 반환 사실을 누락했는가
13. internal API에 앱용 `BaseResponse`를 적용하거나 userId를 URL·로그에 노출했는가
14. `inbound_event_inbox` 120일 TTL, TrialClaim 3년 보존과 Reservation audit 보존을 같은 정책으로 취급했는가
15. PLAN-002 구현에 confirm·cancel·expiry·reconciliation 또는 결제 기능을 섞어 vertical slice 범위를 넓혔는가
16. Guest가 `billing:purchase`를 받거나 public ALB에서 `/internal/**`가 Billing target으로 전달되는가
17. refund된 OPEN/RETAKE_AVAILABLE에 답안·채점·replacement가 허용되거나 GRADING/COMPLETED history가 삭제되는가
18. raw Store/RevenueCat payload·token·credential이 로그·ledger에 남거나 payment 5년·inbox 120일·backup 35일 보존을 혼용하는가
