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
- Apple App Store 및 Google Play 결제 검증과 결제·취소·환불 원장
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

현재 우선 범위는 verified-phone candidate 기준 무료 모의고사 1회를 위한 최소 Entitlement다.

- Identity `PhoneEligibilityBindingVerified`/`PhoneEligibilityBindingRevoked` event consumer
- event inbox, revision high-water와 `trial_eligibility` current projection
- `TrialClaim`, `FREE_EXAM_ONCE` grant와 ledger
- 시험 시작 전 Reservation reserve, confirm, cancel, status와 5분 expiry
- AttemptGroup consumption 연결과 Learning Core reconciliation
- Mongo transaction, unique index, 멱등성, 관측성과 운영 복구

다음 기능은 후속 단계이며 명시적인 구현 요청과 선행 계약 승인 없이 추가하지 않는다.

- Apple/Google 결제 adapter와 notification
- paid credit와 unlimited pass
- coupon, 출석과 추천인 보상
- 환불과 chargeback
- 앱이 직접 호출하는 Billing 사용자 API와 Billing 사용자 JWT audience

## 현재 구현 단위

현재 즉시 구현할 단위는 Jira `TMI-113`, `docs/plans/PLAN-003-reservation-lifecycle.md`의 Reservation lifecycle vertical slice다.

포함 범위:

- `POST /internal/v1/reservations/{reservationId}/confirm`
- `POST /internal/v1/reservations/{reservationId}/cancel`
- `POST /internal/v1/reservations/status`
- confirm/cancel strict decode, canonical payload hash와 terminal command 7일 retention
- INITIAL allocation·grant consume 또는 release와 append-only `CONSUMED`·`RELEASED` ledger
- REPLACEMENT의 기존 consumption 유지와 active Session 교체
- 5분 expiry worker, Reservation expected-state/version CAS와 terminal race 수렴
- read-only status, transient retry와 unknown commit 결과 재확인
- internal endpoint default deny와 Identity/Learning Core route 분리
- replica-set Testcontainers 기반 transaction·동시성 검증

이 구현 단위에서는 AttemptGroup `GRADING`·`COMPLETED`·`RETAKE_AVAILABLE` 상태 event, 탈퇴·재가입 owner rebind, reconciliation·repair, 타 서비스 client·SigV4 adapter와 실제 AWS 배포를 추가하지 않는다. PLAN-003 완료만으로 production caller를 활성화하지 않고 후속 상태 event·Learning Core saga·Lattice staging E2E gate를 유지한다.

## 핵심 불변식

- mutable balance만을 원장으로 사용하지 않고, 지급·결제·사용 기록은 추적 가능한 ledger로 보존한다.
- 결제 및 entitlement 처리에는 idempotency key와 provider event 식별자를 사용해 중복 지급·차감을 막는다.
- `TrialClaim`은 consumer-scoped verified-phone candidate와 benefit type 기준으로 유일하다.
- `claimedAt + 3년` 보존기간 안에는 탈퇴·재가입·merge·revoke·cancel·expiry로 Claim을 삭제하거나 다시 열지 않는다.
- `retentionExpiresAt` 이후 candidate alias와 사용자·source event 연결은 dedupe에서 제외하고 승인된 purge 정책에 따라 삭제한다. 그 뒤 같은 번호가 다시 verified되면 새 Claim을 허용한다.
- TrialClaim 연결정보 purge는 매일 실행하고 logical expiry 후 24시간 안에 active DB에서 제거한다. 재해복구 backup은 최대 35일이며 복구본은 사용자 트래픽 연결 전에 expiry purge를 먼저 적용한다.
- raw phone을 Billing에 전달하거나 저장하지 않는다.
- 시험 1회 비용은 10 credits이며 credit는 음수가 아닌 정수다.
- Reservation의 `RESERVED` TTL 초기값은 5분이다. TTL은 확정된 사용을 만료시키지 않는다.
- 시험 생성 흐름은 `reserve → Learning Core Session commit → confirm` 순서를 유지한다.
- 공개 `POST /api/v1/exams`는 필수 lowercase UUID v4 `Idempotency-Key` header를 사용하며 Request Body 없음과 기존 성공 Response DTO를 유지한다.
- 시험 생성 응답 유실의 transport retry는 같은 key·같은 Session으로 수렴한다. 앱 종료 뒤 의도적 restart는 새 key·새 examId를 사용하고 기존 Session을 이어풀지 않는다.
- restart는 기존 결과·upload·Job·summary를 승계하지 않고 같은 AttemptGroup consumption과 `mockExamId`를 유지한다.
- confirm/cancel은 멱등이고 `CONFIRMED`를 cancel로 되돌리지 않는다.
- Reservation allocation은 원래 grant 단위를 보존해 cancel 시 정확히 복구한다.
- 장애 시 reservation과 Learning Core Session을 reconciliation하여 중복 Session이나 이중 차감을 만들지 않는다.
- Apple/Google 구매는 클라이언트 주장만 신뢰하지 않고 서버에서 검증한다.
- Identity eligibility event를 수신하는 것만으로 TrialClaim, grant 또는 balance를 만들지 않는다. 최초 reserve Transaction에서 현재 binding과 기존 Claim을 확인해 지급과 Reservation을 원자적으로 처리한다.

상세 상품·사용권 계약과 미확정 선택지는 이 저장소의 `docs/codex/CONTRACT_DECISIONS.md`를 단일 기준으로 사용한다. 서비스 간 전체 흐름은 `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, 내부 API와 Mongo 계약은 `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, Lattice·SigV4·환경 이관 계약은 `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`, 현재 구현 순서는 `docs/plans/PLAN-003-reservation-lifecycle.md`를 따른다. 통합 안내서와 세부 ADR이 충돌하면 ADR을 따르며, 확정된 계약을 임의로 재해석하지 말고 작업을 중단해 보고한다.

과거 Learning Core 문서는 역사적 참고 자료일 뿐이며, 앞으로 Billing 관련 결정과 작업기록은 이 저장소의 `docs`에만 추가한다.

## 내부 API 계약 규칙

- 내부 eligibility namespace는 `/internal/v1/eligibility/{kind}/...` 형식을 사용한다.
- 현재 Trial event endpoint는 `/internal/v1/eligibility/trial/events`다.
- URL namespace만 공통화하며 Trial, paid, coupon의 DTO·권한·멱등성·aggregate와 저장소를 하나로 합치지 않는다.
- Identity가 이미 발행하는 wire event type과 schema v1 필드명을 임의로 변경하지 않는다.
- internal API는 앱용 `BaseResponse` wrapper를 사용하지 않고 승인된 internal DTO 또는 body 없는 204를 그대로 반환한다.
- request/response body 상한은 16 KiB이며 redirect를 허용하지 않는다.
- strict decode 전에 payload를 저장하지 않고 raw JSON 전문을 로그에 남기지 않는다.
- duplicate JSON field, trailing token, scalar coercion, unknown field와 잘못된 UUID casing을 거절한다.
- `producer=identity`, `schemaVersion=1`, 승인된 event type과 환경 설정의 expected `consumerScopeId`가 exact match해야 한다.
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
- TrialClaim은 cancel·expiry 때 삭제하거나 `claimedAt`을 갱신하지 않는다.
- confirm이 CANCELED 또는 EXPIRED 상태에 도착하면 자동 repair-confirm하지 않는다. privileged repair route는 별도 계약 전까지 열지 않는다.

## 사용자 및 서비스 인증 규칙

- 실제 사용자 ID는 UUID 문자열이며 JWT `sub`에서 가져온다.
- 클라이언트가 Request Body, Path, Query로 보낸 `userId`를 신뢰하지 않는다.
- 예외적으로 인증된 Identity eligibility event와 인증된 Learning Core internal route는 각 서비스가 확정한 lowercase canonical UUID `userId`를 body로 전달한다. 다른 principal, public path, query parameter 또는 임의 identity header의 userId는 신뢰하지 않는다.
- 향후 사용자 API를 추가할 때는 Identity만 사용자 토큰을 발급하며 Billing은 issuer, audience, signature, expiry를 검증한다.
- 현재 내부 workload API는 VPC Lattice `AWS_IAM`, ECS task role과 SigV4를 사용한다. 별도 shared secret, API key 또는 workload JWT를 임의로 추가하지 않는다.
- Identity task role은 Trial eligibility event route만, Learning Core task role은 Reservation·status·AttemptGroup route만 호출할 수 있도록 최소 권한을 적용한다.
- repair route는 일반 workload role과 분리된 운영 role만 허용한다.
- unsigned 요청, 다른 환경 role, 권한 없는 route와 direct task 우회는 거절한다.
- local/test에서는 실제 AWS credential이나 Lattice를 호출하지 않고 명시적인 test principal 또는 adapter로 workload 경계를 검증한다.
- workload 요청은 호출 서비스와 idempotency 정보가 검증되기 전까지 처리하지 않는다.
- 인증 계약이 완성되지 않은 endpoint는 fail-closed 상태를 유지한다.

## 보안 및 개인정보 규칙

- 실제 Secret, MongoDB URI, Store credential, Apple/Google receipt·token·notification 원문을 저장소에 추가하지 않는다.
- raw phone, token, receipt, payment instrument, 사용자 개인정보를 로그나 작업 문서에 기록하지 않는다.
- 결제 provider 응답은 검증에 필요한 최소 정보만 정규화하여 저장하고 민감 원문을 ledger에 복제하지 않는다.
- 환경변수 참조와 가짜 테스트 값만 저장소에 둔다.
- 테스트에서 실제 Atlas, Apple, Google, Identity, Learning Core를 호출하지 않는다.
- 실제 AWS role ARN, VPC·subnet·security group·Lattice 식별자를 코드나 테스트에 하드코딩하지 않는다.

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

## 테스트 규칙

- 변경한 비즈니스 로직에는 단위 테스트와 필요한 통합 테스트를 추가한다.
- Repository와 provider adapter는 가능한 경우 Mock으로 처리한다.
- Mongo transaction·unique index·동시성 검증은 replica set 기반 Testcontainers 통합 테스트로 수행한다.
- 모든 구현 작업 후 `./gradlew clean test`를 실행한다.
- 외부 API, ledger 불변식, 멱등성 계약이 바뀌지 않았는지 확인한다.
- workload route는 허용 role 성공뿐 아니라 unsigned, wrong role, wrong route와 direct bypass 실패도 테스트한다.
- PLAN-001은 duplicate field·unknown field·coercion·property/candidate 순서·whitespace·oversize와 expected scope mismatch contract test를 포함한다.
- Mongo 통합 테스트는 duplicate event, same revision conflict, stale/gap, unique-index race, transient transaction retry와 unknown commit 결과 수렴을 검증한다.

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
11. 테스트가 실제 Atlas, AWS, Store, Identity 또는 Learning Core에 의존하는가
12. 결제·coupon·환불 등 현재 범위 밖 기능이나 관련 없는 대규모 리팩터링이 포함됐는가
13. internal API에 앱용 `BaseResponse`를 적용하거나 userId를 URL·로그에 노출했는가
14. `inbound_event_inbox` 120일 TTL, TrialClaim 3년 보존과 Reservation audit 보존을 같은 정책으로 취급했는가
15. PLAN-002 구현에 confirm·cancel·expiry·reconciliation 또는 결제 기능을 섞어 vertical slice 범위를 넓혔는가
