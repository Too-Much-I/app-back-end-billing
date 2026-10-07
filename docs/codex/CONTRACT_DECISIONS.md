# Billing 계약 결정서

- 최초 작성일: 2026-08-24
- 상태: 결제 제품 정책·C9-S1~S9 승인, C9-S10 Store 중심 환불 창구 승인(2026-09-09). 국내 판매가격 기록 완료; ADR-004 기술 초안 작성, 실제 Store 설정·기술 검증·별도 PLAN은 대기
- Jira: 무료/owner lifecycle 관련 `TMI-120` 구현은 병합 완료; 결제 PLAN·Jira 생성 대기
- 문서 역할: Billing 구현 전 계약의 단일 기준

### 2026-10-07 Identity–Billing 공동 기술 계약 개정안 — 사용자 승인

- [개정 계약](../contracts/IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md)의 정책·기술 설계/초기값 채택. 탈퇴 원천 capturedAt 관측 기준120일, snapshot 사용자 자료 최대7일, snapshot/recovery operation120일·활성 stream 최소 checkpoint 유지/폐기 stream 종료 후120일, 신규 구매 token 최대30분/skew60초 승인.
- 공통 발급/탈퇴 fence·control 이관, independent capture, 고정 snapshot/sequence feed/consumer coverage, contentDigest·consumerRecoveryGeneration·ACK/retry/복원 설계 포함. 기존15일 조건부 삭제와 운영 주기 유지.
- 사용자 승인과 Identity 합의·운영 가능성/보존 적합성 검증은 구별한다. 실제 barrier 시간/실행·코드 착수·Jira 변경·배포/판매/자동삭제·개별 예외 보존 연장은 승인하지 않았다. 기술 검증에서 범위 변경이 필요하면 재보고한다.

### 2026-10-07 탈퇴 구매 계정 정리 운영 주기 — 확정

- 삭제 대상은 매시간 점검한다. 미확인 건은 매일 재확인하고, 계속 미해결이면 최초 미해결 판정 후 7일 이내 담당자가 검토한다. 재시도로 최초 검토 기한을 연장하지 않는다.
- 기존 withdrawnAt+15일 조건부 삭제와 거래 확인 선행은 유지한다. 예외 정보 보존기간·batch/lease·24시간 지연 경보·첫 검토 이후 반복 담당 검토 주기 및 실제 삭제 실행은 이번 승인에 포함하지 않는다.
- 상세: [탈퇴·대사·삭제 설계](../contracts/PLAN-009-withdrawal-reconciliation-and-purge-contract.md). provider coverage/Identity 계약·보존 manifest 검증 전 purge OFF 유지.

이 문서에서 `권장`은 아직 승인되지 않은 후속 제안이다. `확정` 또는 아래 승인 요약에 포함된 항목은 구현 계약이다.

## 0. 과거 일정 기록 — 2026-08-24 결제 구현 연기

- 2026-08-24 사용자 결정으로 Apple/Google 결제, credit/pass, coupon, 환불 구현은 후속 릴리스로 연기한다.
- 기존 상품·결제 계약은 삭제하지 않고 동결하며 재개 시 이 문서를 기준으로 이어간다.
- verified-phone당 무료 모의고사 1회는 현재 우선 범위에 남아 있다.
- Billing은 결제 없이 `TrialClaim`, `FREE_EXAM_ONCE`, reserve/confirm/cancel과 reconciliation만 최소 Entitlement로 먼저 구현한다.
- Store, credit, pass, coupon과 환불 코드는 현재 최소 범위에 포함하지 않는다.
- Learning Core 임시 소유와 Identity 소유는 채택하지 않는다.

### 0A. 2026-09-05 결제 개발 재개 — 최신 결정

- 사용자는 1차 개발 범위에 Apple App Store·Google Play 결제를 포함하기로 했다. 위 2026-08-24 결제 연기는 역사적 일정 기록이며 현재 결제 개발을 차단하지 않는다.
- 유료 상품은 자동 갱신형 월 구독이 아니라 결제 검증 뒤 정해진 기간 동안 모의고사를 무제한 사용하는 fixed-term access 상품이다. 기간은 `1일`, `3일`, `7일`, `14일`, `4주(28일)`이다.
- 활성 유료 기간에는 유료 권리를 먼저 사용하고 `FREE_EXAM_ONCE` Claim·Grant·available unit은 변경하지 않는다. 유료 기간 종료 뒤 무료권이 아직 미사용이면 그대로 사용할 수 있다.
- 기존 `CREDIT_5`, `CREDIT_10`, `CREDIT_100`, `UNLIMITED_3D`, first-purchase credit bonus, check-in 연장과 C10 credit 만료 결정은 현재 결제 구현 계약에서 제외한 역사적 초안이다. 별도 사용자 승인 없이는 구현하지 않는다.
- C9의 Store 상품 매핑·account binding·RevenueCat 검증 데이터·복원은 C9-S1, 앱 API/auth는 C1-A/C2-A, entitlement lifecycle·환불·webhook·reconciliation·보존과 ADR-004 세부 연동 선택은 아래 C9-S2~S8로 확정했다.
- 2026-09-07 사용자는 RevenueCat을 Apple/Google 결제 연동 계층으로 채택했다. 결제 편의성을 얻기 위해 RevenueCat 표준 SDK가 transaction completion을 맡기며, Billing은 RevenueCat Authorization+HMAC webhook과 인증된 API의 Store transaction만 정규화한다. RevenueCat Entitlement는 fixed-term 권리 source로 사용하지 않는다.
- 실제 Store product ID, 판매 국가·가격, public hostname/ALB inventory, RevenueCat Offering/Package·secret/API quota, exact public DTO·Mongo index와 staging 측정 뒤 운영 조정값은 구현 ADR/출시 준비에서 별도로 확정한다.

## 1. 이미 확정된 계약

다음 항목은 기존 논의에서 확정됐다. 단, credit/pass 관련 항목은 최신 0A에 의해 역사적 초안으로 대체됐으며 현재 구현 근거로 사용하지 않는다.

- Billing은 상품, RevenueCat을 통해 검증된 Apple/Google 결제의 원장, credit/pass/free entitlement, `TrialClaim`, `Reservation`, 보상을 소유한다.
- Identity는 계정과 사용자 토큰 발급, Learning Core는 시험 Session·문제·채점·결과를 소유한다.
- 모의고사 1회는 10 credits이며 credit는 음수가 아닌 정수다.
- 상품 초안은 `CREDIT_5`, `CREDIT_10`, `CREDIT_100`, `UNLIMITED_3D`다.
- 첫 credit 상품은 verified-phone 기준 1회만 base와 같은 양의 bonus를 지급한다. unlimited 선구매는 이 자격을 소진하지 않는다.
- `UNLIMITED_3D`는 구매 후 30일 안의 첫 reserve에서 활성화되고 72시간 유효하다. 서로 다른 KST 3일 check-in 시 24시간 한 번 연장하며 재구매 pass는 별도로 보존한다.
- 추천인은 입력자와 추천인에게 각각 10 credits를 지급한다. 입력자의 verified phone과 첫 유료 결제 확정 뒤 phone당 한 번만 지급한다.
- 무료 시험은 canonical userId가 아니라 verified-phone candidate를 기준으로 관리한다. 한 Claim은 `claimedAt`부터 3년 동안 `FREE_EXAM_ONCE` 재수급을 차단하며, 3년 만료 뒤에는 같은 번호도 새 Claim을 받을 수 있다. raw phone은 Billing에 저장하지 않는다.
- 결제 채널은 Apple App Store와 Google Play만 사용하며 웹 결제는 현재 범위가 아니다.
- balance 단일 값이 아니라 출처·만료·환불 연결을 보존하는 ledger/grant가 진실 공급원이다.
- 시험 시작은 `reserve → Learning Core Session commit → confirm` 순서이며 `RESERVED` TTL은 5분이다. 확정된 consumption은 이 TTL로 만료되지 않는다.
- confirm/cancel과 RevenueCat webhook 처리는 멱등이어야 하고 동일 RevenueCat event/Store transaction은 unique해야 한다.
- 앱 종료 뒤 기존 시험을 이어풀지 않고 새 key·새 examId로 처음부터 시작한다. 이전 결과·파일·Job은 승계하지 않는다.
- 하나의 최초 consumption에 `AttemptGroup`을 연결하고 완료 전 restart는 같은 group에서 추가 차감 없이 허용하는 R3 정책을 사용한다. mockExamId는 group 동안 고정한다.

## 1A. 2026-08-26 승인된 무료 모의고사 최소 계약

- 이번 릴리스에는 앱이 직접 호출하는 Billing 사용자 API를 만들지 않는다. 앱은 Learning Core에 시험 생성을 요청하고 Learning Core만 Billing 내부 API를 호출한다. C1의 장기 사용자 API 경로와 C2의 사용자 Billing audience는 결제 단계까지 보류한다.
- Identity의 versioned `PhoneEligibilityBindingVerified`/`Revoked` 이벤트를 at-least-once로 수신한다. Billing은 eventId unique inbox와 bindingRevision high-water를 같은 로컬 Transaction에서 반영하고, binding 미도착·revoke·인증 실패는 무료권 지급 없이 fail-closed한다.
- eligibility 내부 API는 `/internal/v1/eligibility/{kind}/...` namespace로 묶는다. 현재 Trial event route는 `/internal/v1/eligibility/trial/events`이며, 향후 paid·coupon API가 실제로 필요하면 각각 `paid`, `coupon` 하위 route를 사용한다. URL namespace만 공통화하고 종류별 DTO·권한·멱등성·aggregate는 분리한다.
- 이벤트 수신 자체는 무료권 지급이 아니다. 첫 reserve의 Mongo Transaction에서 current verified binding을 확인하고 `(benefitScopedCandidate, FREE_EXAM_ONCE)` unique `TrialClaim`, 무료 grant/ledger, allocation과 `Reservation`을 함께 생성한다.
- Learning Core와 Identity → Billing 내부 호출은 C3-D `VPC Lattice + ECS task role + SigV4 + AWS_IAM`을 사용한다. 기존 두 서비스의 사용자 inbound Load Balancer는 유지하고 아직 미배포인 Billing만 public/internal ALB 없이 Lattice service target으로 배포한다.
- Learning Core task role은 reserve, confirm, cancel, status만, Identity task role은 phone eligibility verified/revoked event 전달만 허용한다. privileged repair-confirm은 별도 운영 role/permission으로 분리한다. Billing direct task 접근과 Lattice를 우회하는 route는 security group과 routing으로 차단한다.
- Billing production 배포 전 production/staging 두 ECS cluster와 각 환경의 Identity·Learning Core·Billing service, Mongo database, Secret, task role, Lattice service network/policy와 security group을 분리해 준비하는 것을 배포 gate로 삼는다. 현재 `tosunsaeng-staging-cluster`는 이름과 domain은 staging이지만 실제 운영 트래픽을 처리한다. 새 production cluster에 Identity·Learning Core를 배포·검증하고 운영 트래픽을 전환한 뒤 기존 cluster를 최종 staging으로 전환한다. staging Lattice 리소스는 유지하되 ECS service는 평소 `desiredCount=0`, E2E 전 필요한 service를 `1+`로 올려 health/smoke/E2E를 통과한 뒤 다시 0으로 내릴 수 있다.
- 현재 무료시험 생성 API의 UUID v4 `Idempotency-Key`는 필수다. 앱은 응답이 확정될 때까지 같은 key를 보존하고 Learning Core는 같은 operation ID를 reserve, confirm, status와 reconciliation에 전달한다. 같은 user·operation·payload 재호출은 기존 결과를 반환하고 다른 payload 재사용은 conflict다. terminal command 기록은 우선 7일 보존한다.
- 오류는 행동별 stable code를 사용한다. 사용권 부족은 402 `ENTITLEMENT_INSUFFICIENT`, 처리 중은 409 `COMMAND_PROCESSING`, key 충돌은 409 `IDEMPOTENCY_KEY_CONFLICT`, rate limit은 429, 일시 장애는 503 `BILLING_TEMPORARILY_UNAVAILABLE`다. 안전한 자동 재시도는 동일 key를 사용하고 processing/429/503은 `Retry-After`를 제공한다.
- 이번 resolver는 클라이언트 선택을 받지 않고 서버가 `FREE_EXAM_ONCE`만 자동 선택한다. unlimited, promotional, paid를 포함한 전체 우선순위는 결제 단계까지 확정·구현하지 않는다.
- 사용자당 OPEN AttemptGroup 1개, active Session 1개, 생성 command 1개만 허용한다. 동일 operation은 같은 Reservation을 반환하고 confirm 결과 불명은 `ENTITLEMENT_CONFIRMING`, status retry와 reconciliation으로 수렴시킨다.
- 무료권은 reserve에서 `RESERVED`로 잠그고 Learning Core가 ExamSession을 durable commit한 뒤 confirm에서 ledger를 `CONFIRMED`/`CONSUMED`로 최종 전환한다. Session commit 전 실패·cancel·5분 expiry는 allocation을 복구하지만 `TrialClaim`은 삭제하거나 다시 열지 않는다. confirm 뒤 일반 cancel은 금지한다.
- 필수 피드백, 유효 점수와 Summary가 사용자에게 조회 가능할 때 AttemptGroup을 `COMPLETED`로 닫는다. 결과 생성이 최종 실패하면 `RETAKE_AVAILABLE`로 전환하고 같은 consumption으로 새 Session을 허용한다. 완료 전 허용된 restart도 같은 consumption과 mockExamId를 사용한다.
- AttemptGroup 상태 연동은 고정 저카디널리티 failureCode, active Session fencing과 단방향 전이표, missing/stale/conflict 원인별 503·204·409 분리, pending event 무TTL 지수 backoff outbox를 사용한다. 서비스 간 추적은 JSON 계약이 아닌 W3C `traceparent`와 구조화 로그의 `traceId + eventId`로 수행한다.
- TrialClaim의 dedupe 연결은 immutable `claimedAt`부터 3년 동안 보존한다. 이 기간에는 계정 merge·탈퇴·binding revoke·Reservation cancel/expiry로 Claim을 삭제하거나 재개방하지 않는다. 3년 만료 시 candidate alias와 사용자·source event 연결을 dedupe 대상에서 제거하고 삭제·비식별화하며, 이후 같은 번호의 새 Claim을 허용한다. raw phone·last4·Identity fingerprint는 저장하지 않는다.
- 번호 재할당도 기존 Claim의 3년 보존기간 안에는 새 Claim을 허용하지 않는다. `retentionExpiresAt` 뒤에는 재할당 증거 없이도 같은 번호의 새 Claim을 허용한다.

## 1B. 2026-08-26 승인된 ADR-002 인프라 입력

- production/staging은 별도 VPC Lattice service network로 분리한다 — 확정.
- AWS Region은 서울 `ap-northeast-2`를 사용한다 — 확정.
- production/staging은 같은 AWS account와 같은 VPC를 사용하되 task role, Lattice auth policy/service network, security group, Secret과 database는 환경별로 분리한다 — 확정.
- Identity, Learning Core, Billing은 각각 별도 application task role을 사용한다. task execution role과 GitHub OIDC deploy role은 다른 역할이며 호출 권한에 사용하지 않는다 — 확정.
- 초기 internal endpoint는 custom domain 없이 환경별 Lattice 기본 DNS를 사용한다 — 확정.
- Java outbound SigV4 client는 AWS SDK v2 signer 의존성을 사용하고 adapter 뒤에 격리하며 local/test는 fake adapter를 사용한다 — 확정.
- 현재 인프라는 AWS Console에서 최초 수동 생성했고, 배포는 GitHub Actions OIDC로 ECR image를 push한 뒤 실행 중인 ECS Service의 Task Definition image를 render한 새 revision을 배포한다 — 현재 방식 확인.
- 새 production cluster·Lattice·IAM·SG 생성에 Console 수동 방식을 계속 쓸지 IaC를 도입할지는 ADR-002 구현·운영 선택으로 남긴다.

## 1C. 2026-08-28 승인된 장기 Entitlement 구조

- `BenefitDefinition`은 공통 혜택 종류·정책 catalog다. stable `benefitCode`, 표시 이름, one-time/subscription 유형, 소비 방식과 policy version을 소유한다. 최초 코드는 `FREE_EXAM_ONCE`이며 `PREMIUM_SUBSCRIPTION`은 후속 구독 계약용 식별자로 예약한다.
- `TrialClaim`은 `(verified-phone candidate, FREE_EXAM_ONCE)`의 3년 중복 수급 방지 전용이며 일반 상품 catalog나 소비 이력으로 사용하지 않는다.
- `EntitlementGrant`는 subject가 실제로 보유한 one-time 혜택 instance/batch다. 무료 MVP에서는 `FREE_EXAM_ONCE` 1 unit이며 allocation과 ledger로 available·held·consumed를 관리한다.
- `SubscriptionEntitlement`는 subject별 fixed-term paid 권리다. 수량 차감 대신 `SCHEDULED/ACTIVE/EXPIRED/REVOKED`와 시작·종료 시각으로 authorization하며 현재 상품은 자동 갱신하지 않는다. 상세 계약은 C9-S2를 따른다.
- `Reservation`은 무료 Grant와 활성 SubscriptionEntitlement 모두에 사용하는 공통 시험 시작 승인·멱등성 경계다. INITIAL/REPLACEMENT attempt 관계와 GRANT/SUBSCRIPTION authorization source는 서로 다른 축으로 모델링한다.
- `AttemptGroup`은 최초 시험 사용 건과 same-consumption replacement Session을 연결하며 entitlement 종류와 무관하게 결과 생성 lifecycle을 추적한다.
- 실제 지급·hold·release·consume 감사 이력은 append-only `EntitlementLedger`가 소유한다. TrialClaim이나 mutable Grant projection을 이력 원장으로 대체하지 않는다.
- 이 구조 승인은 현재 무료 MVP의 lazy TrialClaim/Grant 생성, `reserve → Session commit → confirm`과 Mongo wire/schema를 즉시 변경하지 않는다. 현재 코드에 없는 BenefitDefinition foundation은 별도 vertical slice로, SubscriptionEntitlement와 구독 resolver는 결제 재개 시 별도 계약·계획으로 구현한다.
- 과거 credit/pass 상품 초안은 2026-09-05의 0A·C9-S1에 의해 현재 결제 범위에서 제외됐다. 현재 유료 방향은 credit balance가 아니라 1·3·7·14·28일 fixed-term unlimited entitlement다.
- 2026-08-28에는 `FREE_EXAM_ONCE` BenefitDefinition foundation만 구현했으며, 그 당시의 `PREMIUM_SUBSCRIPTION`·Store 구현 연기는 2026-09-05 결제 개발 재개 결정으로 종료됐다. paid benefit은 `PREMIUM_SUBSCRIPTION`, 기간형 권리는 `SubscriptionEntitlement`로 사용하고 exact field·Mongo schema/index는 결제 ADR에서 확정한다.

## 2. 무료 최소 계약 선택지 검토 기록

### C1. 앱이 Billing 사용자 API를 호출하는 경로

#### C1-R1. 무료 사용권 public reader 선행 — 2026-09-07 승인·구현

- 사용자 요청으로 결제와 독립적인 [PLAN-007](../plans/PLAN-007-public-free-entitlement-query.md)을 작성했다. 이 계획의 exact URL은 `GET /api/v1/entitlements`, scope는 `billing:read`, actor는 검증된 사용자 JWT sub다. Guest/MEMBER 모두 본인 조회를 허용한다.
- 공개 envelope는 `isSuccess/code/message/result`. benefit별 수량·newAttempt·hasInProgress·retake·usageState와 current-owned group 목록을 분리한다. grant 미생성이라도 current VERIFIED/retained Claim 부재가 증명되면 신규 예상 수량 1이며 조회로 지급하지 않는다.
- 미수신 eligibility·미해결 owner 연결은 200 PENDING/null, 저장소·불변식 실패는 503 result=null이다. 같은 전화번호 retained 사용 완료는 0이며 재가입으로 초기화하지 않는다.
- 이 조회 한 개에 한해 기존 무료-only public Billing 없음 전제를 확장한다. 별도 public connector/ALB exact allowlist와 사용자 JWT chain을 준비하고 internal Lattice AWS_IAM·SigV4/DTO는 유지한다. 결제 route 전체를 미리 열지 않는다.
- Identity account_type PR #39 병합과 Billing audience/read 발급은 별개다. Billing OFF 배포 → Identity aud/read 후속 → staging → reader ON → 앱 순서다. paid scope/SDK/collection은 포함하지 않는다.
- paid `/api/v1/payments/entitlement`는 별도 후속 조회다. ADR-004의 direct DTO 초안과 이번 public envelope 차이는 결제 구현 전에 검토하며 이번 계획으로 paid wire를 변경하지 않는다.
- 사용자가 보완 계획의 구현을 승인해 reader·JWT·포트 격리·durable attribution을 구현했다. Jira·실제 migration·production 활성화는 수행하지 않았으며 별도 승인/gate가 필요하다. [OpenAPI](../openapi/free-entitlements.yaml), [배포·이관](../runbooks/PLAN-007-public-reader-rollout.md)을 따른다.
- 2026-09-07 검토 보완: PLAN-007 §8.2~8.5에 command TTL 독립적인 sessionOwnerEpoch 증빙·쓰기 Transaction CAS·legacy 이관 gate를 추가했다. PHONE_REJOIN과 실제 USER_MERGED의 의미를 구분하며 기존 source Session을 새 owner의 epoch로 덮어쓰지 않는다. Guest ELIGIBILITY_UNKNOWN은 실제 처리 job이 있다는 뜻이 아니므로 프론트 안내를 '사용 가능 여부를 확인할 수 없습니다'로 구분한다. Identity 9.2의 aud/read 발급 인계는 그대로 유지한다.

무료 최소 릴리스에서 보류했던 결정을 2026-09-05 결제 재개 시 C1-A로 확정했다. 앱은 상품·구매 동기화·현재 유료 권리 조회를 Billing public API로 직접 호출하고, 시험 생성·Reservation은 계속 Learning Core→Billing internal Lattice 경로를 사용한다.

#### A. 앱이 Billing을 직접 호출 — 확정

- 상품 조회, 결제 확인, 잔액/사용권 조회, check-in, coupon은 앱 → Billing이다.
- 시험 reserve/confirm/cancel만 Learning Core → Billing 내부 API다.

장점:

- 도메인 소유권이 명확하고 Learning Core가 결제 API proxy가 되지 않는다.
- Billing 장애와 배포가 Learning Core 코드에 덜 전파된다.
- 향후 구매 복원·스토어 notification 흐름을 Billing 안에서 끝낼 수 있다.

단점:

- 앱이 Identity, Learning Core, Billing의 base URL과 오류 처리를 알아야 한다.
- Billing도 사용자 JWT 검증, CORS/관측성/rate limit를 독립 운영해야 한다.

#### B. 앱 요청을 모두 Learning Core가 proxy

장점: 앱의 API 주소와 Access Token audience 변경이 적다.

단점: Learning Core가 Billing DTO와 장애에 결합되고 결제 도메인 경계가 흐려진다.

#### C. 별도 API Gateway/BFF를 먼저 구축

장점: 앱에는 단일 진입점, 공통 인증·rate limit·routing을 제공할 수 있다.

단점: 현재 서비스보다 인프라와 운영 범위가 커지고 Billing 구현이 지연된다.

### C2. 사용자 Access Token의 Billing audience

무료 최소 릴리스에서 보류했던 결정을 2026-09-05 결제 재개 시 C2-A로 확정했다.

#### A. 기존 앱 토큰에 `tosunsaeng-billing` audience를 추가 — 확정

- Identity가 한 Access Token의 `aud` 배열에 기존 Learning Core audience와 Billing audience를 넣는다.
- 각 서비스는 자기 audience만 필수 검증한다.

장점: 앱은 토큰 하나만 관리하고 기존 Learning Core audience도 유지한다.

단점: 한 토큰의 사용 범위가 두 resource server로 넓어지며 Identity 변경이 필요하다.

확정 검증 profile:

- Identity의 기존 RS256 Access Token과 환경별 exact issuer/JWKS/key rotation을 재사용한다.
- `aud` 배열은 기존 `tosunsaeng-learning-core`와 신규 `tosunsaeng-billing`을 포함하고 각 resource server는 자기 audience를 필수 검증한다.
- Billing은 `sub`의 lowercase canonical UUID, signature, issuer, audience, `iat`, `exp`, `jti`와 최대 60초 clock skew를 검증한다.
- public Billing route는 최소 `billing:read`, `billing:purchase` scope로 분리한다. 정확한 route-scope matrix는 public API ADR에서 고정한다.
- userId를 request body/path/query/header에서 받거나 신뢰하지 않고 검증된 JWT `sub`만 actor로 사용한다.
- Billing reader를 먼저 배포한 뒤 Identity가 다중 audience·scope token을 발급하고, 기존 Learning Core audience와 scope를 제거하지 않는다.

#### B. Billing 전용 Access Token 또는 token exchange

장점: audience와 권한을 가장 강하게 분리하고 유출 범위를 줄인다.

단점: 앱 토큰 관리와 Identity 발급·교환 흐름이 복잡해진다.

#### C. Billing 사용자 API를 Learning Core proxy로만 제공

장점: 당장 Billing audience를 추가하지 않아도 된다.

단점: C1의 도메인 결합 문제가 생기며 장기 구조로 비권장이다.

승인할 때 audience 문자열, issuer, 필수 claim, 허용 clock skew도 함께 고정한다.

### C3. Learning Core → Billing workload 인증

#### A. 배포 플랫폼이 발급한 5분 이하 workload identity JWT — 미채택

- `aud=tosunsaeng-billing`, logical workload principal `app-back-end-learning-core`, reserve/confirm/cancel/status 최소 권한을 사용한다.

장점: 서명·만료·audience·scope를 표준 방식으로 검증하고 서비스별 최소 권한을 줄 수 있다.

단점: ECS task role은 일반 OIDC JWT·JWKS를 자동 제공하지 않는다. 별도 OIDC issuer가 없다면 이 선택지는 그대로 구현할 수 없다.

#### D. ECS task role + AWS SigV4 + VPC Lattice `AWS_IAM` 검증 — 확정

- Learning Core와 Identity task role의 임시 credential로 요청을 SigV4 서명하고 VPC Lattice가 IAM principal과 route 권한을 검증한다.
- Billing으로의 직접 우회 경로를 security group과 routing으로 차단한다.

장점: ECS가 기본 제공하는 task role과 자동 credential rotation을 사용하며 별도 JWT issuer·JWKS·client secret이 필요 없다.

단점: 새 Lattice service network/service/listener/target과 auth policy, SigV4 HTTP client, local/test adapter와 AWS 비용이 추가된다.

#### E. Identity 발급 workload JWT + Billing의 Identity JWKS 검증 — 미채택 대안

- 기존 사용자 인증과 같은 RS256 issuer/JWKS/Spring Resource Server 메커니즘을 사용하되 workload 전용 token profile로 분리한다.
- workload token은 `token_use=workload`, `aud=tosunsaeng-billing`, `sub=app-back-end-learning-core`, reserve/confirm/cancel/status scope와 5분 이하 TTL을 사용한다.
- 사용자 Access Token은 `aud=tosunsaeng-learning-core`, UUID `sub`이므로 Billing 내부 API에서 거절한다.

장점: Learning Core가 이미 사용하는 Identity RS256/JWKS 검증 구조와 Spring Security 설정을 재사용하고 ECS ingress 종류와 독립적으로 애플리케이션 계층에서 호출자를 검증할 수 있다.

단점: 현재 Identity에는 workload client 등록·인증·발급 endpoint가 없으므로 client-credentials, Secret rotation, token cache와 발급 장애 복구를 구현해야 한다. 사용자 token을 workload token으로 재사용해서는 안 된다.

#### B. mTLS

장점: 네트워크 계층에서 강한 상호 인증을 제공하고 bearer token 탈취 위험이 없다.

단점: 인증서 발급·회전·ALB/ECS 구성과 로컬 개발이 복잡하다.

#### C. 고정 API key 또는 shared HMAC secret

장점: 초기 구현이 빠르다.

단점: secret 배포·회전, replay 방지, 호출 주체와 scope 분리가 어렵다. 운영 최종안으로 비권장이다.

### C4. 시험 생성과 Billing command의 멱등성

#### A. 앱 UUID v4 필수 `Idempotency-Key`, 같은 사용자·operation 범위 unique — 확정

- 신규 앱은 시험 시작 동작마다 key를 만들고 응답이 확정될 때까지 같은 key로 재시도한다.
- Learning Core는 같은 operation ID를 reserve/confirm/reconciliation에 전달한다.
- 완료 재호출은 기존 Session 결과를 재구성하고, 처리 중은 409, 다른 payload 재사용은 conflict다.
- Session의 operation ID는 Session 수명 동안 보존하고 terminal command 상태는 우선 7일 보존한다.

장점: 응답 유실과 중복 터치에도 Session·차감이 하나로 수렴한다.

단점: 앱의 UUID 생성·안전한 로컬 보존과 양 서버의 unique index/reconciliation이 필요하다.

#### B. 서버가 매 요청 새 key 생성

장점: 앱 변경이 없다.

단점: 응답을 못 받은 앱의 새 HTTP 요청을 이전 요청과 연결하지 못한다.

#### C. active Session 존재 여부만으로 중복 판단

장점: 별도 key 계약이 작다.

단점: 의도적 restart와 transport retry를 구분하지 못해 R3에서 E2/E3가 연속 생성될 수 있다.

무료 최소 릴리스에서는 `Idempotency-Key`가 필수다. optional fallback은 제공하지 않는다.

### C5. Billing 공개 오류 계약

#### A. 안정적인 행동별 code와 HTTP status mapping — 확정

- 사용권 부족: 402 `ENTITLEMENT_INSUFFICIENT`
- 정상 처리 중/동일 command 진행 중: 409 + 안정적인 processing code
- key 충돌: 409 `IDEMPOTENCY_KEY_CONFLICT`
- rate limit: 429
- Billing timeout/불가: 503 `BILLING_TEMPORARILY_UNAVAILABLE`
- 409 processing, 429, 503에는 `Retry-After`; 자동 재시도는 같은 key를 사용한다.

장점: 앱이 구매 유도, 재로그인, 같은-key 재시도, 수동 복구를 정확히 구분한다.

단점: 앱·Learning Core·Billing 간 오류 mapping 표와 contract test가 필요하다.

#### B. 모든 도메인 충돌을 409로 통합

장점: HTTP status 종류가 적다.

단점: 부족·처리 중·key 충돌을 code에만 의존하게 된다.

#### C. 모두 500/503으로 통합

장점: 서버 구현이 단순하다.

단점: 사용권 부족도 장애로 보이고 안전한 자동 재시도 여부를 판단할 수 없다.

오류 `result`에는 balance, provider 원문 code, candidate, reservation/payment identifier를 노출하지 않는다.

### C6. 시험 시작 시 사용할 사용권 우선순위

#### A. 서버 자동 선택 — 무료 최소 릴리스 확정

- 현재는 `FREE_EXAM_ONCE`만 선택한다.
- 2026-09-05 결제 계약부터 서버는 `ACTIVE PREMIUM_SUBSCRIPTION → FREE_EXAM_ONCE` 순서로 자동 선택한다. 유료 기간에는 무료 Claim·Grant·unit을 변경하지 않는다.

장점: Request 계약이 작고 클라이언트가 entitlement identifier를 조작할 수 없다.

단점: 결제 entitlement가 추가되면 사용자가 무료권을 아끼는 선택을 허용할지 우선순위를 다시 결정해야 한다.

#### B. 사용자가 pass/free/credit를 선택

장점: 사용자가 혜택 사용 시점을 통제한다.

단점: UI와 Request 필드가 늘고 선택 이후 entitlement 상태 변화와 ID 검증이 필요하다.

#### C. paid credit를 free once보다 먼저 사용

장점: 무료권을 나중에 보존할 수 있다.

단점: 무료 기회가 있는데 유료 재화가 먼저 없어져 불만 가능성이 높다.

promotional credit끼리는 만료 임박순, paid credit끼리는 오래된 grant 순을 권장한다.

### C7. Reservation 동시성과 confirm 불명 복구

#### A. 사용자당 OPEN AttemptGroup 1개·active Session 1개·생성 command 1개 — 확정

- 동일 operation은 같은 Reservation을 반환한다.
- Session commit 후 confirm 결과가 불명이면 Session을 내부 `ENTITLEMENT_CONFIRMING`으로 두고 성공 응답을 노출하지 않는다.
- status 조회·confirm 재시도·reconciliation으로 수렴한다.
- Session이 존재하는데 Reservation이 만료된 예외는 privileged repair-confirm 또는 같은 group replacement로 복구하고 고심각도 경보를 낸다.

장점: 한 사용자의 동시 이중 차감과 여러 active 시험을 강하게 막는다.

단점: Mongo unique guard, 내부 pending 상태와 양방향 reconciliation이 필요하다.

#### B. operation별 Reservation만 unique, 사용자 동시 시작 허용

장점: 여러 시험을 동시에 시작할 수 있다.

단점: 제품 정책과 충돌하고 mobile double tap에서 서로 다른 key면 여러 차감이 가능하다.

#### C. confirm timeout이면 즉시 Session 삭제·cancel

장점: 표면 흐름이 단순하다.

단점: 실제 confirm 성공 후 응답만 유실된 경우 차감만 남을 수 있어 채택하지 않는다.

### C8. AttemptGroup 완료 시점

#### A. 필수 피드백·유효 점수·Summary가 사용자 조회 가능할 때 `COMPLETED` — 확정

- 모든 필수 최초 submit이 durable Job과 함께 접수되면 `GRADING`이다.
- retry/reconciliation 최종 실패면 `RETAKE_AVAILABLE`로 돌아가 같은 consumption으로 새 Session을 연다.

장점: 사용자가 실제 결과를 받기 전에는 새 결제를 요구하지 않는다.

단점: Learning Core 결과 불변식 확인, outbox와 group close reconciliation이 필요하다.

#### B. 모든 필수 submit 접수 시 즉시 `COMPLETED`

장점: 완료 판정이 빠르고 단순하다.

단점: 채점이 영구 실패해 결과가 없어도 새 시험에 다시 차감될 수 있다.

#### C. 사용자가 마지막 화면을 확인할 때 완료

장점: 사용자 경험상 소비 완료 시점이 직관적일 수 있다.

단점: 결과를 확인하지 않은 계정이 영원히 OPEN으로 남고 클라이언트 이벤트를 신뢰해야 한다.

### C8-1. AttemptGroup 상태 event 수렴·outbox 정책

#### A. 고정 failureCode·상태/Session fencing·원인별 오류·durable outbox — 확정

- `RETAKE_AVAILABLE.failureCode`는 `REQUIRED_RESULTS_UNAVAILABLE`, `SUMMARY_UNAVAILABLE`, `GRADING_DEADLINE_EXCEEDED`, `RESULT_INTEGRITY_VIOLATION`만 허용한다. exception message, provider 이름·code·원문과 자유 문자열은 금지한다.
- event revision을 추가하지 않는다. same eventId/digest는 duplicate no-op이고, `activeSessionId`와 AttemptSession `ACTIVE` 상태가 일치한 event만 단방향 상태 전이표로 처리한다.
- terminal evidence가 먼저 도착할 수 있으므로 `OPEN→COMPLETED`와 `OPEN→RETAKE_AVAILABLE` 직접 전진을 허용한다. 늦은 `GRADING`, abandoned/restarted/failed Session event와 COMPLETED 이후 역행은 204 stale no-op다.
- 같은 active Session의 `COMPLETED`와 `RETAKE_AVAILABLE`을 모두 발행하지 않는 것을 Learning Core producer 불변식으로 둔다. 결과 상태와 terminal outbox event를 같은 Mongo Transaction/CAS로 저장하고 Session별 terminal event는 하나만 허용한다.
- 아직 생성 순서상 보이지 않는 group/session projection은 503 `ATTEMPT_PROJECTION_NOT_READY`와 `Retry-After: 5`, abandoned/old Session은 204 stale, 존재하는 group/session/subject 관계 충돌은 409 `EVENT_TARGET_CONFLICT`로 분리한다. consumer가 missing target을 임의 생성하거나 연결하지 않는다.
- Learning Core outbox의 retryable PENDING event는 TTL로 삭제하지 않는다. network/408/425/429/5xx는 `5초→15초→1분→5분→15분` 뒤 최대 15분+jitter로 재시도한다. 400/409/422는 DEAD_LETTER, 401/403은 전송 일시 정지와 긴급 경보다.
- DELIVERED outbox는 30일, DEAD_LETTER는 90일 보존한다. 장기 PENDING과 dead-letter는 운영 경보·수동 replay 대상이며 replay에서도 eventId와 canonical payload를 바꾸지 않는다.
- 운영 추적은 W3C `traceparent`로 서비스 간 전달하고 구조화 로그에 `traceId`, `eventId`, 고정 `service`, `operation`, `outcome`, `durationMs`를 함께 기록한다. Billing consume 시에는 event 생성부터 수신까지의 `eventAgeMs`도 기록한다. 비동기 outbox는 원본 trace context를 안전하게 저장해 publish span을 continue 또는 link하며 `baggage`는 전달·저장하지 않는다.
- `service`는 `learning-core` 또는 `billing` 고정값이고 `durationMs`는 각 publish/consume 처리의 monotonic elapsed time이다. `eventAgeMs`는 UTC `now-occurredAt`의 전달 지연이며 clock skew로 음수가 되면 0으로 정규화하고 skew metric을 별도로 올린다.
- trace context는 event JSON, canonical digest, idempotency key와 domain aggregate에 포함하지 않는다. `traceId`와 `eventId`는 metric label로 사용하지 않고, userId·sessionId·attemptGroupId·candidate·provider 원문도 일반 log/trace attribute에 기록하지 않는다.

장점: failureCode는 안정적으로 유지하면서 상세 장애는 traceId/eventId로 Learning Core 로그와 publish/Billing consume 구간을 연결해 조사할 수 있고, service·durationMs·eventAgeMs로 어느 서비스·단계가 느렸는지 구분할 수 있다. event 순서 역전·중복·재응시 세대를 기존 상태와 식별자로 수렴시키고 장기 Billing 장애에도 event를 유실하지 않는다.

단점: Learning Core와 Billing에 실제 tracing propagation·MDC/log 연동, outbox trace context와 dead-letter 운영 기능이 필요하다. 같은 Session의 모순 terminal event가 producer에서 생성되면 revision 없는 Billing은 어느 것이 제품상 정답인지 판정할 수 없으므로 producer terminal 단일성 보장이 필수다.

## 3. 결제 계약과 구현 전 세부 입력

### C9. Store 상품·가격·사용자 연결

#### C9-S1. Store one-time fixed-term 상품 + RevenueCat + opaque account binding — 확정

상품과 내부 catalog:

- Billing 내부 offer code는 `PREMIUM_1D`, `PREMIUM_3D`, `PREMIUM_7D`, `PREMIUM_14D`, `PREMIUM_28D`다.
- 다섯 offer는 동일한 무제한 premium benefit을 각각 1·3·7·14·28일 동안 부여한다. 앱이 보낸 가격·기간·표시 이름을 권리 지급 근거로 신뢰하지 않는다.
- Apple은 offer별 재구매 가능한 `Consumable In-App Purchase`, Google은 offer별 재구매 가능한 consumable `one-time product`를 사용한다.
- 앱의 Store purchase, 상품 노출과 transaction completion은 RevenueCat 표준 SDK·Offering/Package를 사용한다. RevenueCat은 결제 연동·검증 데이터 공급 계층이며 Billing entitlement를 소유하지 않는다.
- 각 Store의 5개 provider product ID는 환경별 Billing catalog 설정에서 내부 offer code로 exact mapping한다. 실제 product ID, 판매 국가와 가격은 Store Console 생성·출시 승인 때 확정하며 소스 코드에 운영값을 하드코딩하지 않는다.
- Store 상품 자체의 subscription period나 expiry를 권리 기간으로 사용하지 않는다. 검증된 product ID가 매핑된 Billing catalog의 24·72·168·336·672시간을 권위 있는 duration으로 사용한다.

사용자 연결:

- 구매는 Identity가 `billing:purchase`를 발급한 `ACTIVE MEMBER`만 허용한다. Guest는 인증된 상품·현재 권리 조회용 `billing:read`까지만 받을 수 있으며 Guest purchase와 paid `UserMerged` migration은 현재 범위에 없다.
- Billing은 로그인한 canonical user에 연결되는 비개인 `purchaseAccountRefId`를 사용자·환경별 stable 값으로 하나 발급한다. wire 형식은 Apple `appAccountToken`에 사용할 수 있는 lowercase UUID v4로 고정하고 Google obfuscated account identifier에도 같은 opaque 값을 사용한다.
- RevenueCat custom App User ID에도 `purchaseAccountRefId`를 사용한다. 익명 상태 구매를 허용하지 않고, RevenueCat alias/restore 결과만으로 Billing의 purchase owner나 entitlement를 다른 토선생 계정으로 이전하지 않는다.
- Billing만 `purchaseAccountRefId`와 current canonical user의 연결을 소유한다. raw phone, email, device ID와 Store account ID를 연결 식별자로 사용하지 않고 실제 userId를 Store parameter에 직접 전달하지 않는다.
- Store transaction의 account reference가 현재 인증 사용자에게 발급된 값과 exact match한 경우에만 권리를 연결한다. 다른 앱 계정, 같은 device, 같은 phone 또는 같은 Apple/Google 계정이라는 이유만으로 유료 권리를 이전하지 않는다.
- 같은 canonical user의 여러 device와 재로그인은 stable reference를 재사용한다. 보안상 회전하면 새 reference만 신규 purchase에 사용하고 이전 reference는 과거 transaction 검증·환불용 inactive alias로만 보존한다.
- phone 재가입 proof로 유료 구매·이용권을 자동 이전하지 않는다. 향후 Guest purchase를 허용하려면 paid `UserMerged` migration을 별도 계약·Jira로 먼저 승인한다.

검증·멱등성과 복원:

- 앱의 구매 성공 callback은 지급 증거가 아니다. Billing이 RevenueCat HMAC webhook 또는 인증된 RevenueCat API로 store, app/package, environment, product ID, Store transaction ID, purchase state와 account reference를 확인한 뒤에만 entitlement를 만든다.
- provider transaction ID 또는 purchase token과 provider event ID를 unique/idempotency 경계로 사용한다. client retry, server notification과 reconciliation이 같은 구매를 발견해도 purchase·entitlement는 하나로 수렴해야 한다.
- 이미 Billing에 반영된 활성 권리는 같은 토선생 계정 로그인 뒤 Billing current entitlement 조회로 복원한다.
- Store 결제 후 Billing 반영 전에 앱이 종료된 경우 RevenueCat webhook retry와 주기적 API reconciliation이 같은 Store transaction 검증 경계로 수렴한다. Store/RevenueCat 원문 payload를 entitlement ledger나 일반 로그에 복제하지 않는다.
- RevenueCat 표준 SDK가 Apple transaction finish와 Google consumable completion을 담당한다. Billing 반영보다 Store completion이 먼저일 수 있으므로 local `PENDING`, webhook inbox와 reconciliation으로 수렴하며, Billing 반영 전 client callback을 근거로 fail-open하지 않는다. 이 항목은 2026-09-07 이전의 `Billing commit 후 finish/consume` 계약을 supersede한다.

장점:

- 상품 의미가 정해진 기간 이용과 맞고, Store 식별자와 Billing 내부 duration을 서버에서 통제하므로 client 위변조를 차단한다.
- 비개인 account reference로 다른 앱 계정 오귀속을 막고 device 변경·callback 유실에도 Billing 원장과 Store 재검증으로 복원할 수 있다.
- provider별 adapter는 달라도 내부 purchase·entitlement model은 같은 offer code와 duration으로 정규화할 수 있다.

단점:

- 두 Store 모두 one-time product라 exact 기간을 Billing catalog에서 동일하게 계산할 수 있고 Google prepaid의 14일 미지원·달력 1개월 차이를 피한다.
- consumable purchase는 Store subscription expiry/restore에 의존할 수 없으므로 Billing ledger가 권리 복원의 source of truth가 되고, 미완료 transaction·notification·provider 조회 수렴을 provider별로 구현해야 한다.

#### C9-S2. fixed-term entitlement lifecycle과 무료권 우선순위 — 확정

- 공통 paid benefit은 `PREMIUM_SUBSCRIPTION`으로 유지하고 다섯 offer가 서로 다른 duration의 `SubscriptionEntitlement`를 만든다. 이름은 Store 자동 갱신을 뜻하지 않으며 `autoRenew=false` fixed-term 권리다.
- 권리는 provider purchase가 `VERIFIED`로 검증됐을 때만 생성한다. authorization은 Billing local Transaction commit 뒤 시작한다.
- 기준 `startsAt`은 provider가 증명한 purchase 시각이다. 두 Store 모두 검증된 product ID의 Billing catalog duration으로 `endsAt`을 계산한다.
- 1·3·7·14·28일은 각각 24·72·168·336·672시간의 UTC duration이다. KST 자정이나 달력 월말로 재계산하지 않는다.
- active 권리가 없으면 `startsAt=provider start`, active 또는 scheduled paid timeline이 있으면 `startsAt=max(provider start,current paid timeline endsAt)`로 이어 붙이고 `endsAt=startsAt+duration`으로 만든다. 구매별 entitlement와 immutable purchase 연결을 유지해 기존 기간을 덮어쓰지 않는다.
- active 또는 scheduled entitlement가 refund/revoke되면 그 purchase의 slot만 timeline에서 제거한다. 2026-10-06 R2 승인으로 기존 refundConfirmedAt 기준을 대체한다. 최초 성공한 Billing 권리 종료 Transaction에 고정한 `appliedAt`을 기준으로, 아직 시작하지 않은 뒤의 VERIFIED entitlement를 기존 sequence/duration을 유지하며 `max(appliedAt, 앞선 유효 entitlement endsAt)`부터 앞당긴다. 기존 시작시각보다 뒤로 미루거나 이미 시작/완료된 다른 slot을 재시작하지 않는다. 원래 schedule과 모든 조정은 append-only ledger로 남긴다. provider 확정 시각은 별도 사실 기록이며 소급 소진 근거로 쓰지 않는다.
- entitlement 상태는 최소 `SCHEDULED`, `ACTIVE`, `EXPIRED`, `REVOKED`를 사용하고 provider purchase의 `PENDING`, `VERIFIED`, `REFUND_REVIEW`, `REFUNDED`, `REVOKED`와 분리한다. 자동갱신용 grace/account-hold/cancel-scheduled 상태는 현재 범위에 만들지 않는다.
- Reservation resolver는 현재 시각에 ACTIVE paid entitlement를 먼저 선택하고 authorization source를 `SUBSCRIPTION`으로 기록한다. paid 사용은 unit을 차감하지 않되 Reservation·AttemptGroup usage audit를 남긴다.
- paid가 ACTIVE가 아니면 기존 `FREE_EXAM_ONCE` resolver를 사용한다. paid 기간 중 TrialClaim·무료 Grant·available unit·claimedAt을 생성·소비·갱신하지 않으므로 종료 뒤 미사용 무료권을 그대로 사용할 수 있다.
- client가 entitlement, startsAt, endsAt, state 또는 무료권 우선순위를 선택하지 않는다.

#### C9-S3. refund·revoke·chargeback — 확정

- 실제 금전 환불과 chargeback 판단은 Apple/Google의 최종 상태를 source of truth로 사용한다. 앱 callback이나 client가 보낸 refund 주장을 신뢰하지 않는다.
- client의 환불 신청 주장만으로 상태를 변경하지 않는다. provider-authenticated refund review/consumption request처럼 신뢰 가능한 사전 신호가 있으면 `REFUND_REVIEW`로 두고 신규 INITIAL·replacement를 일시 중지하며 provider 최종 결과로 해제 또는 확정한다.
- 검증된 refund/revoke는 원 purchase와 entitlement에 append-only reversal을 연결하고 해당 paid source의 새 INITIAL Reservation을 즉시 차단한다. purchase·ledger·usage history를 삭제하거나 과거 상태로 덮어쓰지 않는다.
- refund/revoke 시점의 AttemptGroup 상태별 처리는 다음과 같다. `RESERVED`는 cancel/expiry·Session compensation으로 종료하고, `OPEN`은 access-revoked terminal로 전환해 추가 답안·제출·채점·replacement를 차단하며, `RETAKE_AVAILABLE`은 replacement를 차단한다. 이미 제출돼 `GRADING`이면 채점·Summary를 terminal까지 수렴시키고 `COMPLETED` history는 삭제하지 않는다.
- refund와 reserve/Session commit/confirm race는 refund Transaction이 관찰한 상태와 CAS로 선형화한다. refund가 먼저 commit되면 이후 confirm은 stable revoked error로 거절하고 Learning Core가 생성된 Session을 access-revoked로 보상한다. confirm 또는 GRADING이 먼저 commit됐더라도 refund 뒤 `OPEN` replacement를 계속 허용하지 않는다.
- Billing 신규 reserve 차단만으로 기존 Learning Core Session을 막을 수 없으므로 Billing은 durable access-revocation event를 발행하고 Learning Core는 exact AttemptGroup/Session projection으로 답안·제출·채점·replacement를 fail-closed한다. event 이름·wire·route는 payment ADR에서 고정한다.
- Store가 환불을 확정하기 전에 이미 완료된 디지털 서비스는 회수할 수 없다. provider가 지원하면 최소 consumption evidence를 전송하고 `REFUNDED_AFTER_USE` 저카디널리티 운영 지표로 반복 악용을 관찰하되 사용자 식별자를 metric label이나 일반 로그에 넣지 않는다.
- refund 뒤 ACTIVE paid 권리와 진행 가능한 paid AttemptGroup이 없으면 다음 신규 시험은 보존된 `FREE_EXAM_ONCE`가 있을 때 기존 무료 resolver를 사용할 수 있다. 기존 refunded AttemptGroup을 무료권으로 자동 재결속하지 않는다.
- fixed-term 상품에는 자동 잔여 시간 비례 환급액 산정이나 Billing 자체 negative balance를 만들지 않는다. 2026-10-06 C9-S10 승인으로 Store가 확정한 전액/부분 환불 결과 반영과 해당 구매 잔여권 종료를 설계 범위에 포함한다. 부분 반환액의 증거/원장 exact 계약은 PLAN에서 보완하며 자동 심사·직접 송금·수동 보상은 승인하지 않았다.

#### C9-S4. RevenueCat client sync + Authorization/HMAC webhook — 확정

- 결제 직후 앱은 인증된 Billing purchase-sync API를 호출해 즉시 활성화를 요청한다. client body의 가격·기간·권리·userId와 구매 성공 callback은 지급 증거가 아니며, Billing은 현재 사용자의 `purchaseAccountRefId`로 RevenueCat API를 조회한다.
- Apple App Store Server Notifications와 Google RTDN은 RevenueCat에 연결하고, Billing은 환경별 RevenueCat Authorization+HMAC webhook을 공통 provider ingress로 수신한다.
- webhook은 `X-RevenueCat-Webhook-Signature`의 raw body HMAC-SHA256과 timestamp replay window를 검증한다. event ID inbox를 durable commit한 뒤 200을 반환하고 실제 purchase 반영은 event ID·Store transaction unique로 멱등하게 처리한다.
- RevenueCat webhook은 at-least-once이며 자동 retry가 종료될 수 있으므로 duplicate, 순서 역전과 client sync/webhook/reconciliation 경합을 하나의 purchase·entitlement로 수렴한다.
- RevenueCat 표준 SDK가 transaction completion을 담당한다. Store 결제 완료 후 Billing 반영이 지연되면 권리를 임의 활성화하지 않고 `PENDING`으로 노출하며 webhook retry와 API reconciliation으로 복구한다.
- raw receipt, signed transaction/JWS, purchase token, Store notification과 RevenueCat webhook 전문은 일반 log·metric·ledger에 저장하지 않는다. 검증과 retry에 필요한 최소 암호화 reference와 정규화 field만 저장한다.

#### C9-S5. 주기적 RevenueCat reconciliation — 확정

- client sync와 RevenueCat Authorization+HMAC webhook을 주 경로로 사용하고 scheduled RevenueCat API reconciliation을 webhook 유실·지연·일시적 RevenueCat/Store 장애의 안전망으로 둔다.
- 최소 대상은 `PENDING`, Store-completed/Billing-pending, ACTIVE, SCHEDULED와 최근 REFUNDED/REVOKED/EXPIRED purchase다. API cursor·quota와 backoff를 적용하고 전체 사용자를 매 주기 무제한 scan하지 않는다.
- RevenueCat이 제공하는 current Store state와 local state가 다르면 기존 Store transaction business key로 같은 검증·Transaction service를 재사용하고 append-only correction/reversal을 만든다. DB document를 수동 덮어쓰거나 client에게 correction 권한을 주지 않는다.
- 일시 오류는 bounded exponential backoff, 반복 실패는 dead-letter와 운영 경보로 격리한다. replay에서도 RevenueCat event/Store transaction key와 account binding을 변경하지 않는다.
- 기본 주기는 `PENDING`·Store-completed/Billing-pending 5분, `ACTIVE`·`SCHEDULED` 6시간, 최근 90일 `REFUNDED`·`REVOKED`·`EXPIRED` 일 1회다. 각 scan은 기본 100건 bounded batch와 RevenueCat API cursor를 사용한다.
- retry backoff, interval, lookback와 batch는 환경 설정값으로 두고 staging quota/failure test에서 RevenueCat API 한도를 넘으면 보수적으로 조정한다. 제품 의미와 상태별 우선순위는 바꾸지 않는다.

#### C9-S6. public Billing API·internal Reservation 경계 — 확정

- 앱이 직접 호출하는 public 범위와 route는 C9-S8의 상품 조회, purchase account reference 조회/발급, 구매 동기화와 current paid entitlement 조회 네 개로 확정했다. exact DTO·error code와 rate limit는 ADR-004에서 고정한다.
- 첫 배포의 public ingress는 같은 환경의 기존 public ALB를 재사용하되 Billing 전용 hostname/path allowlist, listener rule과 별도 target group을 사용한다. listener default는 fixed reject이고 승인된 public/payment-provider route만 Billing target으로 전달한다.
- public API는 사용자 JWT를 사용하고 `/internal/**`에는 접근할 수 없다. RevenueCat webhook은 사용자 JWT route와 분리된 HMAC-authenticated endpoint를 사용한다.
- Learning Core만 기존 Lattice SigV4 internal Reservation·AttemptGroup route를 호출한다. 앱은 Reservation source, free balance, AttemptGroup owner와 internal repair route를 직접 선택하거나 호출하지 않는다.
- Billing public ingress와 internal Lattice ingress는 SecurityFilterChain, principal, route permission과 security group을 분리한다. public 경로가 internal workload principal을 흉내 내거나 direct task address로 우회할 수 없어야 한다.
- 이 결정은 ADR-002의 무료-only "Billing ALB 없음"을 payment public route에 한해 supersede한다. internal route는 계속 ALB에 노출하지 않고 Lattice AWS_IAM만 사용한다. 기존 ALB listener·certificate·SG가 exact 분리를 지원하지 않으면 임의 완화하지 않고 dedicated Billing ALB 대안을 재승인한다.

#### C9-S7. payment 보존·삭제 — 확정

- 정규화된 `Purchase`, `SubscriptionEntitlement`, refund/revoke와 payment ledger는 `max(provider transaction finalAt, entitlement endsAt, last reversalAt)`부터 5년 보존한다.
- provider event inbox의 digest·disposition 멱등성 기록은 120일 보존한다. raw receipt, signed JWS, Store notification과 RevenueCat webhook 전문은 active DB·ledger·일반 로그에 저장하지 않는다.
- RevenueCat/Store 재조회에 필요한 transaction reference는 최소 field만 암호화하여 payment lifecycle·분쟁 대응 동안 보존하되 5년 상한을 넘기지 않는다. 실제 RevenueCat API/HMAC secret, Store credential·private key와 encryption key는 Secret Manager에서 회전한다.
- 5년 만료 후 canonical user 연결과 erasable provider lookup reference를 삭제 또는 비가역 비식별화한다. 삭제 worker는 사용자 식별자를 로그에 남기지 않고 처리 건수·성공 여부만 기록한다.
- 재해복구 backup은 기존 운영 원칙과 같이 최대 35일 rolling 보존하며, 복구본은 현재 retention 기준 purge를 적용한 뒤 사용자 트래픽에 연결한다.
- 이 5년 값은 현재 운영 승인값이며 관할 법령·Store 의무가 더 긴 보존을 요구하면 출시 전 계약을 갱신한다.

#### C9-S8. ADR-004 RevenueCat·public API·revoke 세부 선택 — 확정

- public 사용자 route는 `GET /api/v1/payments/products`, `POST /api/v1/payments/purchase-account`, `POST /api/v1/payments/sync`, `GET /api/v1/payments/entitlement`로 기능별 분리한다. JWT `sub`만 actor로 사용하고 조회는 `billing:read`, account 발급과 sync는 `billing:purchase`를 요구한다.
- purchase account 발급은 body 없는 idempotent get-or-create로 사용자·환경별 stable lowercase UUID v4 하나에 수렴한다. purchase sync는 lowercase UUID v4 `Idempotency-Key`와 SDK의 Store/transaction identifier를 untrusted lookup hint로 받되 userId·가격·기간·receipt·purchase token을 받지 않는다.
- sync에서 RevenueCat 검증과 local entitlement commit이 끝나면 200, 아직 RevenueCat에서 transaction을 확인할 수 없으면 `202 PENDING`과 `Retry-After`를 반환한다. 앱은 같은 key로 재시도하며 PENDING 동안 유료 권리를 추측해 열지 않는다.
- RevenueCat webhook은 별도 Authorization header와 HMAC signing을 모두 검증한다. `X-RevenueCat-Webhook-Signature`의 timestamp·raw body HMAC-SHA256을 constant-time compare하고 5분 replay window를 적용한 뒤 최소 inbox를 durable commit하고 빠르게 200을 반환하며 실제 원장 반영은 worker가 수행한다.
- provider payload는 required envelope/field type·size·environment·app/product/account binding을 엄격히 검증하되 RevenueCat의 forward-compatible optional unknown field는 허용한다. 인증된 unknown event type은 durable `IGNORED_UNSUPPORTED`와 경보로 200 수렴하고 malformed/auth failure는 저장·반영하지 않는다.
- 현재 생성한 단일 RevenueCat project 아래 iOS/Android app을 두고 webhook integration을 `SANDBOX → staging`, `PRODUCTION → production`으로 filter한 두 개로 분리한다. URL, Authorization 값과 HMAC secret은 환경별로 다르게 관리하며 한 환경의 event를 다른 DB에 반영하지 않는다.
- RevenueCat restore behavior는 original App User ID에 유지한다. ACTIVE MEMBER가 Billing 발급 `purchaseAccountRefId`로 식별된 뒤에만 구매하고 anonymous purchase, alias/restore에 의한 계정 간 자동 이전과 phone rejoin paid migration을 허용하지 않는다.
- RevenueCat `Track new purchases from server-to-server notifications`와 자동 `Refund request handling`은 최초 출시에서 OFF다. 전자는 owner binding 없는 신규 purchase 생성을 막고, 후자는 Apple에 consumption data를 자동 제공하기 전에 별도 개인정보·운영 검토를 하기 위함이다.
- 정상 인증 webhook은 직접 정규화·멱등 반영하고 RevenueCat REST API는 client sync, 모호한/불완전 event와 scheduled reconciliation에 사용한다. 모든 webhook마다 동기 API 재조회를 강제하지 않아 RevenueCat API 장애·quota가 전체 webhook 처리를 막지 않게 한다.
- refund/revoke의 Learning Core 차단은 Billing refund Transaction과 같은 local outbox에 exact AttemptGroup 단위 durable event를 기록하고 VPC Lattice SigV4로 비동기 전달한다. Billing은 신규 Reservation을 즉시 차단하고 Learning Core 장애 때문에 provider refund commit을 롤백하지 않는다. exact event name·route·wire와 inbox는 ADR-004에서 작성한다.
- Mongo collection/index/Transaction/CAS, UTC/exclusive time, raw-body 상한, timeout/backoff, error envelope/rate limit, secret rotation, observability와 purge worker는 위 정책을 바꾸지 않는 구현 세부로 ADR-004에서 exact 고정한다.

#### C9-S9. 4주 상품과 ADR-004 예외 정책 — 확정(2026-09-07)

- 기술 초안: [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md). C9-S1~S8 승인 정책은 유지하며 이 초안이 승인 원장을 자동으로 대체하지 않는다.
- 사용자 승인: 기존 30일 상품을 4주(28일)로 대체한다. 내부 offer는 `PREMIUM_28D`, duration은 672시간·2,419,200초이며 표시명은 '4주'다. 실제 Store/RevenueCat 상품 변경은 별도 준비하며 기존 거래 duration에 소급하지 않는다.
- D1-A 정상 기간 만료 중 시험: 유효기간 안에 confirm된 현재 Session은 기존 시험·제출 기한 안에서 완료를 허용한다. 만료 전 reserve가 commit된 동일 Session의 confirm도 기존 5분 Reservation 유효기간 안에서는 허용한다. 만료된 권리로 새 INITIAL/replacement를 만들지 않으며 다음 유효 권리로 기존 group을 자동 재결속하지 않는다. 새 권리의 새 INITIAL 전 기존 group 종료·guard 해제를 같은 Transaction에서 검증한다. 환불의 OPEN 차단 및 refund-first confirm 거절에는 이 유예를 적용하지 않는다.
- D2-A Apple `REFUND_REVERSED`: 최초 출시에서는 durable REVIEW_REQUIRED와 경보로 격리하고 자동 기간 복구·재지급·Session 재개는 하지 않는다. `refunded → owned` 재관측도 일반 신규 구매로 처리하지 않는다. 담당자가 Store 최종 상태를 확인하고 별도 승인된 복구 절차로 처리하며 원장 덮어쓰기는 금지한다. 영구적인 권리 거절 정책이 아니다.
- SDK transaction/order ID 매핑, Store account identifier 변환, consumed 구매 환불 fixture, 누락 거래 복구와 LC 제출/환불 경합은 사용자 선택 대신 기술 gate로 검증한다.

#### C9-S10. Store 중심 환불 창구와 국내 판매가격 — 확정(2026-09-09)

- 2026-10-06 Identity 구현 확인: 로컬 cc076442의 REFUND enum·인증된 활성 계정 userId 자동 귀속·익명401/본문 userId400·저장/멱등 응답 전 검사와 테스트 코드 확인. 아래 Identity 구현 후속이라는 당시 상태는 이 기록으로 갱신. ACTIVE Guest 문의 허용은 구매/환불 거래 소유권을 뜻하지 않음. 배포·프론트·Slack 활성화·대상 구매 연결은 미확인, 구매 scope는 별도다.

- 2026-10-06 개발 진행 승인: PLAN-008의 009→010→011→012→013 단계별 진행과 사용자 public 결제4 API의 기존 PublicResponse 통일 승인. 성공/오류 envelope는 ADR §5.3, 내부/제공자 응답은 유지. 각 세부 PLAN의 구현·Jira·배포 승인을 대신하지 않는다.
- 2026-10-06 PLAN-013 출시 검증·운영 방향 승인: 실제 연동 검증, 안전한 migration, 단계별 활성화, 신규 판매 중단과 기존 결제 복구/환불 처리 분리, 문의·보존·백업·경보 운영 방향을 승인했다. 미확정 기술 상세·운영 담당/권한·실제 배포/판매/환불 실행 승인은 별도이며 판매 gate 통과로 간주하지 않는다.
- 2026-10-06 F1~F4 A안 승인: (1) paid 전용 멱등 채점 승인 API, exact Session GRADING/승인 증거 원자 저장·환불 CAS, 무료 v1 204 유지. (2) 구매 root 차단/금융 효과/reflow/durable job 원자 저장 후 group/outbox bounded 전파; 기존 전 group 단일 Transaction 초안 대체. (3) PLAN-011 exact guard 전이표·최초 짧은 관련 writer 제한 이관, 실측 제한 시간·실행 별도 승인. (4) 환불/전파·LC deny/채점 gate·schema coverage 선행 자동 활성화 검사+운영 최종 판매 승인. exact wire/schema·타 서버·Jira/구현·실제 배포는 별도이며 상세 설계 미완료를 숨기지 않는다.
- 2026-10-07 Google 보완 경로 승인: RC가 부족한 소모성 상품 부분 환불의 검증에 인증된 Google Orders 읽기 조회 허용. 기존 Purchase 매핑·성공 확정 증거 검증 후 금융 원장과 해당 구매 잔여권 종료만 처리. 신규 지급/owner 이전/자동 환불/임의 입력은 제외. actual fixture·권한·중복/정정/누적 schema 검증은 후속이며 코드는 미구현.
- 2026-10-07 009 선택 승인: 고정1분60/10 제한 A·subset 단계 적용 A·회원 중 stable/탈퇴 후 조건부 정리 A·기존 JWT 만료까지 수용 A. 보존30일 후보는 미확정. purchaseAccountRefId 별도 필드 암호화는 생략, Atlas 저장 암호화/TLS/최소권한/로그 제외 및 Store token/credential 보호 유지. Atlas 사용은 사용자 확인, 실제 설정 미점검. 동기 Identity 상태 API는 추가하지 않으며 ACTIVE MEMBER 전용 purchase 발급 검증은 필요. exact manifest·구현/배포 승인 별도.
- 2026-10-07 후속 보존 확정: 앞선30일 미확정 후보 대신 미구매 탈퇴 계정의 Identity 확정 withdrawnAt+15일 후 조건부 삭제 승인. 거래 대사/미해결 점검 선행, 실제 구매는 금융 보존·미해결 최소 증거와 재검토로 분리. 지연 거래/재생성 방지와 물리 purge 주기·실행/배포는 상세 검증 대상. 법정/최대지연 보장 기간으로 주장하지 않는다.

- 2026-10-06 R1/R2 권장안 승인: 정상 운영은 팀 검토→환불 처리→해당 잔여권 종료→문의 종결이다. 일반 반복 환불 UI/자동 추가 환불은 만들지 않는다. 검증된 Store 추가 반환/정정은 금전 원장에만 추적하고 최초 권리 종료/reflow를 반복하지 않는다. 같은 환불의 재관측은 중복 무효, 금액 불명은 전액 추정 없이 대사한다. provider별 식별자·증분/누적 변환 exact 계약은 PLAN에서 고정한다.
- 시각은 `providerConfirmedAt`(미제공이면 null), `firstVerifiedAt`(최초 검증), `appliedAt`(최초 성공한 Billing 권리 종료 Transaction의 고정 기준)으로 분리한다. C9-S2 reflow는 appliedAt 기준이다. 뒤늦은 정확 provider 시각은 감사 근거만 보완하며 이미 반영한 후속 권리를 재시작/축소하지 않는다. 중복/재조회는 기준 시각을 갱신하지 않는다. LC 실제 차단은 비동기 전파임을 유지하고 지연 중 사용을 소급 과금하거나 환불액에서 임의 공제하지 않는다. 법적 해지/반환 기산점과 이 운영 시각은 별개다.

- 2026-10-06 구매별 증거 보완 요청 반영: 인증된 문의자→검증된 구매→Reservation/Group/Session source와 최소 완료·실패 증거를 ADR-004 §5.8.1에 설계한다. userId/현재 시험 목록 개수만으로 환불 거래·사용분을 판정하지 않는다. 신규 중복 이력 저장소나 학습 원문 보존은 추가하지 않으며 exact schema/보존 구분/권한·테스트는 PLAN에서 확정한다.
- 반복 환불은 일반 사용자 기능이나 분할 환불 지급 정책으로 제안한 것이 아니다. 통상 팀 검토 뒤 정해진 환불 건을 처리하고 해당 잔여권을 종료한다. Store의 별도 판단·오처리 정정 등으로 실제 추가 반환이 발생한 경우 금전 사실을 누락하지 않는 예외 대비와 중복 알림 재반영 금지는 구분한다. 예외의 exact 식별/금액 처리는 미확정이며 임의 추가 환불 실행 권한을 부여하지 않는다.

- 2026-10-06 추가 승인: Google은 팀 검토 후 지원되는 Store 전액/부분 환불 실행, Apple은 팀 검토·신청 안내 후 Apple 최종 결과 반영으로 운영한다. 검증된 부분 환불이 확정되면 해당 구매의 남은 기간권도 종료한다. 다른 구매의 권리·미사용 무료권은 취소하지 않으며 기존 timeline reflow/상태별 접근 차단 방향을 유지한다. 부분 환불 금액은 실제 반환액으로 기록해야 하고 권리 종료를 전액 금전 환불로 오기하지 않는다. 부분 환불의 원장/금액·시각 증거/중복·후속 환불 및 reflow exact 계약은 결제 PLAN의 추가 설계 범위이며 구현 완료가 아니다. 문의·팀 승인만으로 종료하지 않고 검증된 provider 최종 결과를 따른다.

- 2026-10-06 운영 추가 승인: 환불 가능 범위·금액은 접수 후 팀이 구매/이용 증거를 보고 건별 검토한다. 이는 법정 권리·Store 정책을 임의 재량으로 배제하거나 공제 공식을 승인한 것이 아니다. 운영상 기준 시각은 실제 provider 환불 확정 시각(팀 승인/웹훅 수신/계좌 입금 시각과 구분), 2영업일 이내 1차 응답으로 정한다. 법정 청약철회/해지 효력이나 금액 계산을 처리 지연만큼 불리하게 미루는 근거로 사용하지 않는다. provider의 정확한 확정 시각 field/fallback은 기술 검증 대상이다.
- 환불 문의에는 userId 귀속을 필수로 한다. 기존 인증 계약에 따라 Identity가 검증된 로그인 JWT sub와 현재 계정으로 자동 연결하며 임의 body userId/이메일을 소유권 증거로 신뢰하지 않는다. 이는 이번 요청의 안전한 구현 해석이며 Identity exact API는 후속이다. 로그인 불가/탈퇴 사용자의 법적 요청은 지원 이메일/Store 경로로 받고 별도 본인·구매자 확인을 하며 userId 부재만으로 거절하지 않는다.
- 약관 작성·화면 고지는 프론트 담당으로 정한다. 최종 내용의 법규/Store 정책·백엔드 동작 일치는 출시 전 확인하며 역할 배정이 약관 완료/법률 검토 완료를 뜻하지 않는다. 접수/판단/금액/사유/Store 결과의 감사 절차, 담당자 배정과 부분 환불의 실제 실행·원장 경로는 후속이다.

- 2026-10-06 추가 승인: 앱의 Identity 문의 접수를 환불 상담 창구로 사용하고 분류 `REFUND`를 추가한다. 운영자가 구매와 이용 내역을 확인한다. 아래 최초 자체 UI 제외는 상담 접수에 한해 대체한다. Store 직접 신청과 지원 이메일은 유지하며 실제 환급은 Store 절차, Billing 반영은 검증된 최종 provider 결과를 따른다. 문의 접수 자체는 환불 확정/이용권 취소가 아니다. Identity 구현은 후속이며 자동 심사·직접 송금·공제 공식·부분 환불 원장 계약은 이번 승인에 포함하지 않는다.

- 2026-09-09 당시 승인(상담 UI 범위는 위 10/6 승인으로 대체): 일반 환불은 구매한 Apple App Store/Google Play의 신청·심사·환급 경로를 기본으로 한다. 앱에는 Store 환불 안내와 토선생 고객지원 연락 수단을 제공한다. 최초 범위에 Billing 자체 환불 신청 API·앱 자체 심사 UI·직접 송금 기능을 추가하지 않는다.
- 토선생은 결제 후 권리 미반영·서비스 장애·미제공·관련 법령상 요청을 고객지원으로 접수하고 확인한다. 지원 이메일/실제 채널·담당자는 출시 전 입력한다. Store 거절을 모든 법적 요청의 자동 종결 사유로 사용하지 않는다.
- Store 환불은 자동 승인이 아니며, Billing은 검증된 최종 거래 상태에 따라 append-only 원장·source 권리·LC 접근 차단을 반영한다. 앱/이메일의 환불 신청 주장만으로 REFUNDED나 환급 완료를 표시하지 않는다. C9-S3·S8·S9의 상태 전이와 자동 refund handling OFF는 유지한다.
- 2026-09-08 사용자 제시 국내 판매가격: PREMIUM_1D 9,000원 / PREMIUM_3D 19,000원 / PREMIUM_7D 29,000원 / PREMIUM_14D 49,000원 / PREMIUM_28D 69,000원. 최초 판매 대상은 한국 성인이다. 실제 Store product ID·가격 설정·세금/할인·연령 제한 검증은 별도이며 앱의 최종 표시·결제 금액은 Store SDK의 검증 가능한 현지화 가격을 사용한다.
- [구매·환불 안내 초안](../contracts/PREMIUM_PURCHASE_REFUND_NOTICE_DRAFT.md)은 게시 전 검토용이다. 고객지원 실제 값, 법적 고지/동의, 부분 반환 적용·공제/효력 시점, Store/RevenueCat 부분 환불 전달·정합성 경로는 출시 gate로 남긴다.
- 이 승인은 잔여기간 일할 환불식·위약금·부분 환불 자동화·법적 분류 확정이 아니다. 부분 기능 미구현을 법정 반환 거절 사유로 사용하지 않는다. 필요 시 별도 승인으로 계약·운영 경로를 확장한 뒤 판매한다. 코드·스토어 설정·배포 승인이 아니다.

#### C9-S11. 고객지원·만료 후 열람·출시 할인 — 확정(2026-09-09)

- 고객지원 이메일은 `tosunsaeng093@gmail.com`이다. 메일 발송·수신함 운영 검증이나 담당자 지정 완료를 뜻하지 않는다.
- 정상 이용기간 만료 뒤 본인 계정의 기존 시험 결과·피드백 열람을 허용한다. 기존 데이터 보존·탈퇴 정책은 유지하고 영구 보관·재가입 이전 기록 복원이나 추가 유료 시험/임의 재채점을 허용하지 않는다. D1의 현재 Session 완료 예외·승인 장애 복구 흐름은 유지한다.
- 최초 출시에는 별도 할인을 하지 않는다. C9-S10의 국내 5개 가격을 유지하고 실제 Store 가격/세금/프로모션 설정은 검증한다.
- [2026-09-09 준비 상태 점검](../contracts/PAYMENT_READINESS_REVIEW-2026-09-09.md): RC 앱 자격증명 정상 표시와 Google Pub/Sub 권한 오류는 별개다. 실제 상품·Offering·Billing webhook과 sandbox 검증은 미완료. 법적 반환 기준·부분 환불 실행/증거·원장 경로는 C9-S10과 같이 미확정 gate다.
- 이번 승인은 문서상 운영 계약 반영이다. 타 서버/앱·Store 설정 변경, 법률 자문 대행·문의 발송, 결제 코드 구현·배포 또는 판매 승인으로 확대하지 않는다.

아래 C9-A/B/C는 2026-08-24 credit/3일 pass 초안의 역사적 비교 기록이다. C9-A의 Store one-time 유형은 2026-09-07 C9-S1에 fixed-term 5개 상품으로 다시 승인됐지만 credit/3일 pass 중심의 나머지 설명은 현재 구현 선택이 아니다.

#### C9-A-legacy. 모든 상품을 재구매 가능한 consumable/one-time product로 매핑 — Store 유형 재채택, 세부는 역사적

- credit pack과 3일 pass는 모두 반복 구매 가능해야 한다.
- 앱은 Apple `appAccountToken`, Google의 obfuscated account identifier에 서버가 발급한 비개인 식별값을 사용한다.
- Store SDK가 현지화 가격을 표시하고 Billing catalog는 store product ID와 지급 entitlement를 결정한다.

장점: pass를 여러 개 별도 보존하는 확정 정책과 맞고 transaction을 canonical 사용자에 안전하게 연결할 수 있다.

단점: restore와 미소비 transaction 처리, store별 consumable semantics를 각각 구현해야 한다.

#### C9-B-legacy. 3일 pass를 subscription으로 구성

장점: 자동 갱신 상품으로 확장하기 쉽다.

단점: 현재 1회성 72시간·별도 pass 보존·미사용 환불 계약과 맞지 않고 해지/갱신 정책이 추가된다.

#### C9-C-legacy. 클라이언트가 SKU·가격·userId를 최종 결정

장점: 서버 catalog가 작다.

단점: 위변조와 가격·사용자 오귀속 위험 때문에 채택하지 않는다.

실제 Apple/Google product ID, 판매 국가, 가격 tier는 출시 전 별도 승인한다.

### C10. Credit 만료 — 역사적 초안, 현재 fixed-term 범위에 미적용

#### A. paid credit는 정책 승인 전 무기한, promotion은 grant별 만료 — 권장

장점: 임의의 짧은 만료로 유료 재화를 잃지 않고 캠페인은 개별 통제할 수 있다.

단점: 장기 미사용 부채와 데이터 보존 부담이 남는다.

#### B. paid와 promotion 모두 고정 기간 만료

장점: 운영과 회계상 장기 잔액을 줄일 수 있다.

단점: 법무·스토어 표시 의무 검토와 사용자 고지가 필요하며 만료 민원이 생긴다.

#### C. 모든 credit 무기한

장점: 규칙이 가장 단순하다.

단점: 이벤트성 promotional credit도 영구 부채로 남고 campaign 종료 통제가 어렵다.

최종 기간은 법무·회계·스토어 정책 검토 후 승인해야 한다.

### C11. 환불·부분 사용·chargeback — credit 기준 역사적 초안; fixed-term 정책은 C9-S3으로 확정

#### A. 완전 미사용 purchase group만 자동 전액 환불 — 권장

- base와 first-purchase bonus가 모두 미사용일 때만 자동 환불한다.
- 일부 사용은 자동 공식이 승인될 때까지 운영 심사한다.
- chargeback 부족분은 음수 credit가 아니라 별도 debt/blocked 상태로 기록하고 새 reserve를 차단한다.

장점: bonus 선사용 후 base 환불 악용과 임의 부분 환불 계산을 막는다.

단점: 일부 사용 환불의 고객지원 수작업이 필요하다.

#### B. 남은 paid credit 비율로 자동 부분 환불

장점: 고객에게 빠른 부분 환불을 제공한다.

단점: bonus·promotion 혼합 사용과 store 부분 환불 금액을 공정하게 배분하기 어렵다.

#### C. 사용 여부와 무관하게 전액 환불하고 negative balance 허용

장점: 환불 처리가 단순하다.

단점: credit 비음수 불변식과 충돌하고 악용·추심 정책이 복잡해진다.

## 4. 보상·개인정보 운영 전에 확정할 계약

### C12. 연속 출석과 coupon

출석 선택지:

- A. Billing daily check-in, KST 날짜당 1회, 7일 cycle 반복, 결석 다음 check-in은 day 1 — 권장
- B. 실제 시험 시작/완료만 출석 인정
- C. Identity 로그인 event를 출석으로 사용

A의 장점은 앱 활동과 보상을 명확히 분리하고 로그인/reissue 중복에 영향받지 않는다는 점이다. 단점은 별도 API와 abuse/rate limit가 필요하다는 점이다. B는 실제 학습을 유도하지만 시험을 살 entitlement가 없는 신규 사용자에게 불리하다. C는 구현이 쉬워 보여도 token reissue·다중 로그인 중복과 Identity 결합이 커진다.

coupon 기본 선택지:

- A. campaign이 stacking·만료·전체/사용자/phone 한도를 모두 명시하고 미지정 stacking은 금지 — 권장
- B. 모든 bonus와 자동 stacking 허용
- C. coupon을 단일 fixed 정책으로만 운영

A는 캠페인별 비용과 악용을 통제하지만 운영 catalog가 복잡하다. B는 사용자 혜택은 크지만 첫 구매·추천 보상 중첩 비용을 예측하기 어렵다. C는 단순하지만 마케팅 확장성이 낮다.

### C13. TrialClaim 보존과 번호 재할당

#### A. `claimedAt`부터 3년 동안 retained candidate와 Claim dedupe 연결 보존 — 확정

- raw phone·last4·Identity fingerprint는 저장하지 않는다.
- `retentionExpiresAt = claimedAt + 3년`이며 로그인, merge, 탈퇴, binding revoke, Reservation cancel/expiry 또는 재응시로 연장하거나 다시 계산하지 않는다.
- 보존기간 안에는 benefit-scoped candidate alias, keyVersion, claimedAt, stable benefitCode와 필요한 source event 연결만 최소 저장하고 Claim을 다시 열지 않는다.
- `retentionExpiresAt`부터 기존 alias는 dedupe matching에서 즉시 제외해 같은 번호의 새 Claim을 허용한다. 물리 purge가 지연돼도 만료된 alias가 재수급을 차단해서는 안 된다.
- purge는 candidate alias·keyVersion과 사용자·source event 연결을 삭제 또는 비가역 비식별화한다. 감사·통계가 필요하면 개인이나 candidate에 다시 연결할 수 없는 benefit type, terminal status와 거친 시각 정보만 남긴다.
- purge job은 매일 실행하며 `retentionExpiresAt`부터 24시간 안에 운영 DB의 candidate alias와 erasable subject 연결을 물리 삭제하고 Claim을 비식별 tombstone으로 전환해야 한다. 24시간 SLA를 넘긴 항목이 있으면 운영 경보를 발생시키고 성공할 때까지 재시도한다.
- MongoDB 전체 재해복구 backup은 생성 시점부터 최대 35일 rolling 보존 후 자동 만료한다. candidate만 별도 backup하거나 만료된 backup을 일반 조회 용도로 보존하지 않는다.
- 과거 backup 복구는 격리 환경에서 수행하며, 현재 시각 기준 만료 purge를 완료한 뒤에만 사용자 트래픽을 연결한다. 삭제 증적에는 실행 시각, 처리 건수, 성공 여부와 저 cardinality 실패 분류만 남기고 candidate, keyVersion, userId, source event와 payload를 기록하지 않는다.

장점: 3년 동안 verified-phone 중복수급을 막으면서 보존 종료 시점과 재수급 동작이 명확하다.

단점: 3년 뒤 같은 번호가 다시 무료권을 받을 수 있으므로 영구·평생 1회 정책은 아니다.

#### B. 무료시험 프로그램 수명 동안 보존

장점: 프로그램 운영 중 같은 번호의 재수급을 가장 강하게 막는다.

단점: pseudonymous personal data의 장기 보존 근거와 삭제 정책 부담이 크다.

#### C. 계정 탈퇴 시 즉시 삭제

장점: 개인정보 보존을 최소화한다.

단점: 탈퇴·재가입으로 무료시험을 반복 받을 수 있어 확정된 phone당 1회 계약을 지키지 못한다.

번호 재할당 정책은 다음과 같이 확정한다.

- 기존 Claim의 `retentionExpiresAt` 전: 번호 소유자가 바뀌어도 새 Claim을 허용하지 않는다 — 확정
- 기존 Claim의 `retentionExpiresAt` 이후: 재할당 증거 없이도 새 Claim을 허용한다 — 확정

### C14. Retained trial owner rebind — 2026-09-02 확정

#### A. lifecycle 분리·stable subject owner CAS·bounded legacy fencing — 확정

- 기존 Identity `UserMerged` v1은 ACTIVE GUEST source→기존 MEMBER target canonical merge 의미를 유지한다.
- 탈퇴 뒤 같은 phone으로 새 UUID가 발급되는 재가입은 `UserMerged` 의미를 재사용하지 않고 Identity의 별도 source→target 승인 event를 사용한다.
- lifecycle별 strict decoder가 각 wire schema·producer·reason을 exact 검증한 뒤 Billing 내부 공통 `OwnerRebindCommand`로 정규화한다.
- 새 TrialClaim, Grant, allocation 또는 consumption을 만들지 않는다. `trialClaimId`, `claimedAt`, `retentionExpiresAt`, `subjectRefId`, Grant unit, ledger와 AttemptGroup 식별자는 유지한다.
- `BillingSubjectLink.userId`만 current source와 expected owner version을 조건으로 target에 CAS 이전한다.
- active `RESERVED` 또는 PROCESSING command가 있으면 document를 rewrite하지 않고 confirm/cancel/5분 expiry 종료까지 retryable PENDING으로 처리한다.
- phone 재가입은 AttemptGroup이 없거나 `OPEN`/`RETAKE_AVAILABLE`일 때만 owner를 이전한다. `GRADING`은 terminal 판정까지 503 pending, `COMPLETED`는 owner와 fence를 변경하지 않는 성공 NOOP다.
- phone target의 재응시는 기존 consumption·attemptGroupId·mockExamId를 유지하지만 source Session을 이전하지 않는다. target의 새 key·새 examId로 replacement Session을 처음부터 만든다.
- Learning Core에 이전 Session이 없는 phone target은 `POST /internal/v1/reservations/continuations/phone`으로 Billing authoritative attemptGroupId/mockExamId와 `PHONE_REJOIN` context를 먼저 조회한다. 적용 가능한 context가 없으면 204다.
- phone reserve는 `continuationReason`, `continuationId`, `expectedAttemptGroupId` 세 field를 모두 echo해야 한다. Billing은 current owner epoch와 exact group/mock을 다시 검증하고 성공 응답·status에 reason/id를 포함한다. 일반 unexpected REPLACEMENT는 Learning Core가 계속 fail-closed한다.
- rebind 전에 생성된 exact AttemptGroup/Session의 authenticated Learning Core status event는 subject/group/session fencing을 통과할 때 GRADING·terminal 수렴 목적으로만 legacy source를 한시 허용한다.
- legacy source는 신규 reserve·replacement·다른 Session 또는 사용자 actor 권한으로 사용할 수 없다.
- source owner 연결은 관련 Session terminal 또는 승인된 retry window 종료 후 삭제하며 어떤 경우에도 Claim 3년 retention을 넘기지 않는다.
- owner rebind consumer는 Identity task role의 VPC Lattice AWS_IAM·SigV4 exact route만 허용하고 production flag는 기본 비활성으로 둔다.
- Identity event별 durable delivery, Learning Core `UserMerged` ownership migration/source deny, phone replacement staging E2E 완료 전 production owner rebind를 활성화하지 않는다.
- phone 재가입 event type은 `TrialOwnerRebindApproved`, route는 `POST /internal/v1/eligibility/trial/owner/events`로 확정한다.
- phone event exact field는 `eventId`, `eventType`, `schemaVersion`, `producer`, `consumerScopeId`, `occurredAt`, `sourceUserId`, `targetUserId`, `lifecycleReason`, `sourceBindingRevision`, `targetBindingRevision`이다. exact value는 `schemaVersion=1`, `producer=identity`, `lifecycleReason=PHONE_REJOIN`이다.
- 두 binding revision은 1 이상의 integer이고 Billing projection prerequisite fencing에 사용한다. raw phone·candidate·Firebase UID·email·credential은 event에 포함하지 않는다.
- Guest merge는 기존 `UserMerged` v1 payload를 변경하지 않고 `POST /internal/v1/owners/merge/events`로 분리한다.
- active Reservation/PROCESSING 또는 prerequisite projection 미수렴은 `503 OWNER_REBIND_PENDING`과 delta-seconds `Retry-After`를 반환한다. Reservation은 `ceil(expiresAt-now)`를 1~300초로 clamp하고 다른 pending은 5초다.
- temporary pending에 425, 202와 409를 사용하지 않는다. 409는 permanent event/owner conflict에만 사용한다.
- Identity lifecycle Transaction은 immutable event core와 `(eventId, consumer)` unique delivery를 원자 저장한다. `UserMerged` consumer는 `BILLING`, `LEARNING_CORE`, `TrialOwnerRebindApproved` consumer는 `BILLING`뿐이다.
- 각 delivery는 lease·attempt·nextAttemptAt·PUBLISHED/DEAD_LETTER와 feature flag를 독립 관리한다. global PUBLISHED 하나, 동기 순차 POST와 consumer별 full payload outbox 복제를 사용하지 않는다. phone event의 Learning Core delivery나 route는 만들지 않는다.
- Identity→Billing owner event는 VPC Lattice SigV4를 사용하고 Identity→Learning Core `UserMerged`는 기존 workload JWT를 유지한다.
- VPC Lattice exact IAM action은 `vpc-lattice-svcs:Invoke`다. caller policy는 환경별 exact service ARN만, service auth policy는 exact Identity task role Principal·POST·승인 route만 허용한다.
- `Action:*`, `Resource:*`, wildcard/account-root Principal, production↔staging 교차 ARN과 불필요한 `InvokeWithServiceNetworkContext`를 허용하지 않는다.
- pre-rebind Session terminal 뒤 daily cleanup이 24시간 안에 legacy sourceUserId를 unset한다. terminal 미수렴 hard upper bound는 `min(rebindAppliedAt+120일, Claim retentionExpiresAt)`이다.
- 120일은 Learning Core dead-letter retention 90일 + 30일 safety buffer이며 Billing inbox retention과 같다. hard cap 뒤 late source event는 자동 authorization이 아니라 privileged reconciliation 대상이다.
- eventId/digest/outcome 비식별 멱등성 기록과 source/target user 연결 cleanup은 분리한다.

구체적인 Billing schema v4/CAS, Identity existing outbox reader-first migration, Learning Core `UserMerged` route·phone replacement와 privileged reconciliation runbook은 2026-09-03 보정된 `docs/adr/ADR-003-retained-trial-owner-rebind-contract.md`를 기술 기준으로 사용한다.

## 5. 확정 상태와 후속 승인 순서

### 무료 최소 Entitlement — 2026-08-26 승인

1. 무료-only 단계에서는 C1/C2 사용자 Billing API와 audience를 보류했으나 2026-09-05 결제 계약에서 C1-A/C2-A로 확정
2. Identity eligibility event inbox·revision high-water·fail-closed
3. 첫 reserve Transaction에서 TrialClaim·무료 grant·Reservation 생성
4. C3-D VPC Lattice + ECS task role + SigV4 + AWS_IAM, 기존 Identity·Learning Core inbound LB 유지
5. C4-A 필수 UUID v4 same-key retry와 terminal command 7일 보존
6. C5-A 행동별 오류 mapping
7. C6-A 서버가 `FREE_EXAM_ONCE` 자동 선택
8. C7-A 사용자당 단일 OPEN group/active Session/command
9. Session durable commit 뒤 confirm 최종 소비
10. C8-A 결과 조회 가능 시 AttemptGroup 완료
11. C13-A `claimedAt + 3년` 보존, 기간 안의 기존 Claim 유지, 만료 후 재수급 허용, daily purge·24시간 삭제 SLA·35일 rolling backup
12. Billing production 배포 전 production/staging 두 cluster와 분리된 데이터·credential·IAM·Lattice/SG 경계를 준비; staging task는 필요할 때 `0 → 1+ → 0`으로 운영. 현재 `tosunsaeng-staging-cluster`의 최종 역할은 재확인 필요
13. C14-A Guest merge와 phone 재가입 lifecycle 분리, stable subject owner CAS, active Reservation retry와 bounded legacy-source fencing

### 후속 — 결제 파이프라인 계약

1. C9-S1 Apple/Google consumable one-time fixed-term 상품, 다섯 내부 offer, opaque account binding과 Billing 검증·복원 — 확정
2. C1-A/C2-A 앱→Billing public API, 다중 audience RS256 Access Token과 최소 scope — 확정
3. C9-S2 provider purchase start, Billing catalog의 24시간 단위 fixed duration, timeline stacking, paid-first/free-preserve resolver — 확정
4. C9-S3 provider-confirmed refund 뒤 RESERVED/OPEN/RETAKE_AVAILABLE 차단, GRADING 완료 수렴, COMPLETED history 보존과 durable Learning Core revoke — 확정
5. C9-S4 RevenueCat 표준 SDK client sync + Authorization/HMAC webhook, Store transaction 검증과 Billing pending 복구 — 확정
6. C9-S5 RevenueCat API 기반 상태별 5분/6시간/일 1회 periodic reconciliation, 100건 bounded batch와 append-only correction — 확정
7. C9-S6 기존 public ALB의 Billing 전용 host/path+target group, RevenueCat webhook·internal Lattice route의 인증 및 ingress 분리 — 확정
8. C9-S7 정규화 payment/ledger 5년, event inbox 120일, backup 35일과 raw payload 비저장 — 확정
9. C9-S8 resource별 public API, sync 202 PENDING, Authorization+HMAC fast inbox ack, 환경별 webhook, original owner restore, S2S/refund 자동 처리 OFF, webhook direct apply와 durable Learning Core revoke — 확정
10. 실제 Store product ID·판매 국가·가격, RevenueCat Offering/Package·secret, exact DTO·Mongo index와 staging quota 기반 운영 조정값 — 출시/ADR 입력 대기

### 후속 — 보상 계약

1. C12-A Billing check-in과 campaign별 coupon 정책

무료 최소 Entitlement의 도메인·Security·Reservation·ledger 구현과 Lattice greenfield 배포 설계를 시작할 수 있다. C13의 보존기간, 물리 purge SLA, backup 수명과 restore 절차는 모두 확정됐다. 단, Billing production 배포는 별도 staging cluster와 분리된 환경을 준비하고 C3-D negative/smoke/E2E gate를 통과한 뒤에만 한다. 결제의 제품·account binding·public auth·lifecycle·환불·RevenueCat webhook·reconciliation·보존·ADR-004 세부 선택은 C1-A/C2-A와 C9-S1~S8로 확정됐으며, 실제 Store/RevenueCat 값·가격과 exact DTO/Mongo 설계는 ADR·구현 계획에서 추가 고정한다. 보상 C12는 후속 기능 구현 전에 별도 승인한다.
