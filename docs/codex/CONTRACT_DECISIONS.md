# Billing 계약 결정서

- 최초 작성일: 2026-08-24
- 상태: 결제 구현 deferred, 무료 TrialClaim 계약과 C3-D VPC Lattice workload 인증 승인
- Jira: 없음
- 문서 역할: Billing 구현 전 계약의 단일 기준

이 문서에서 `권장`은 아직 승인되지 않은 후속 제안이다. `확정` 또는 아래 승인 요약에 포함된 항목은 구현 계약이다.

## 0. 일정 변경 — 결제 구현 연기

- 2026-08-24 사용자 결정으로 Apple/Google 결제, credit/pass, coupon, 환불 구현은 후속 릴리스로 연기한다.
- 기존 상품·결제 계약은 삭제하지 않고 동결하며 재개 시 이 문서를 기준으로 이어간다.
- verified-phone당 무료 모의고사 1회는 현재 우선 범위에 남아 있다.
- Billing은 결제 없이 `TrialClaim`, `FREE_EXAM_ONCE`, reserve/confirm/cancel과 reconciliation만 최소 Entitlement로 먼저 구현한다.
- Store, credit, pass, coupon과 환불 코드는 현재 최소 범위에 포함하지 않는다.
- Learning Core 임시 소유와 Identity 소유는 채택하지 않는다.

## 1. 이미 확정된 계약

다음 항목은 기존 논의에서 확정됐으며, 제품 정책이 바뀌지 않는 한 다시 선택하지 않는다.

- Billing은 상품, Apple/Google 결제 검증·원장, credit/pass/free entitlement, `TrialClaim`, `Reservation`, 보상을 소유한다.
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
- confirm/cancel과 provider notification 처리는 멱등이어야 하고 동일 provider event/payment는 unique해야 한다.
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

## 2. 무료 최소 계약 선택지 검토 기록

### C1. 앱이 Billing 사용자 API를 호출하는 경로

무료 최소 릴리스 결정: 사용자 Billing API를 만들지 않고 결제 단계까지 C1을 보류한다 — 확정.

#### A. 앱이 Billing을 직접 호출 — 결제 단계 장기 권장

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

무료 최소 릴리스 결정: Billing 사용자 API를 노출하지 않으므로 사용자 token의 Billing audience 추가를 결제 단계까지 보류한다 — 확정.

#### A. 기존 앱 토큰에 `tosunsaeng-billing` audience를 추가 — 결제 단계 장기 권장

- Identity가 한 Access Token의 `aud` 배열에 기존 Learning Core audience와 Billing audience를 넣는다.
- 각 서비스는 자기 audience만 필수 검증한다.

장점: 앱은 토큰 하나만 관리하고 기존 Learning Core audience도 유지한다.

단점: 한 토큰의 사용 범위가 두 resource server로 넓어지며 Identity 변경이 필요하다.

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
- unlimited → free once → promotional → paid 전체 순서는 결제 단계까지 보류한다.

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

## 3. 결제 구현 전에 확정할 계약

### C9. Store 상품·가격·사용자 연결

#### A. 모든 상품을 재구매 가능한 consumable/one-time product로 매핑 — 권장

- credit pack과 3일 pass는 모두 반복 구매 가능해야 한다.
- 앱은 Apple `appAccountToken`, Google의 obfuscated account identifier에 서버가 발급한 비개인 식별값을 사용한다.
- Store SDK가 현지화 가격을 표시하고 Billing catalog는 store product ID와 지급 entitlement를 결정한다.

장점: pass를 여러 개 별도 보존하는 확정 정책과 맞고 transaction을 canonical 사용자에 안전하게 연결할 수 있다.

단점: restore와 미소비 transaction 처리, store별 consumable semantics를 각각 구현해야 한다.

#### B. 3일 pass를 subscription으로 구성

장점: 자동 갱신 상품으로 확장하기 쉽다.

단점: 현재 1회성 72시간·별도 pass 보존·미사용 환불 계약과 맞지 않고 해지/갱신 정책이 추가된다.

#### C. 클라이언트가 SKU·가격·userId를 최종 결정

장점: 서버 catalog가 작다.

단점: 위변조와 가격·사용자 오귀속 위험 때문에 채택하지 않는다.

실제 Apple/Google product ID, 판매 국가, 가격 tier는 출시 전 별도 승인한다.

### C10. Credit 만료

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

### C11. 환불·부분 사용·chargeback

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
- 보존기간 안에는 benefit-scoped candidate alias, keyVersion, claimedAt, benefitType과 필요한 source event 연결만 최소 저장하고 Claim을 다시 열지 않는다.
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

## 5. 확정 상태와 후속 승인 순서

### 무료 최소 Entitlement — 2026-08-26 승인

1. C1/C2 사용자 Billing API와 audience는 결제 단계까지 보류
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

### 후속 — 결제 파이프라인 계약

1. C9-A 재구매 가능한 store 상품과 account binding
2. C10-A paid 무기한·promotion별 만료를 임시 정책으로 채택
3. C11-A 완전 미사용만 자동 환불

### 후속 — 보상 계약

1. C12-A Billing check-in과 campaign별 coupon 정책

무료 최소 Entitlement의 도메인·Security·Reservation·ledger 구현과 Lattice greenfield 배포 설계를 시작할 수 있다. C13의 보존기간, 물리 purge SLA, backup 수명과 restore 절차는 모두 확정됐다. 단, Billing production 배포는 별도 staging cluster와 분리된 환경을 준비하고 C3-D negative/smoke/E2E gate를 통과한 뒤에만 한다. 결제 C9~C11과 보상 C12는 후속 기능 구현 전에 별도 승인한다.
