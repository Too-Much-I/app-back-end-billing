# PLAN-006: Retained trial owner rebind vertical slice

- 상태: 계획·D1~D5·ADR-003 승인, Jira `TMI-120` Billing 구현 완료·전체 검증 중
- 작성일: 2026-09-01
- 대상 저장소: `app-back-end-billing`
- Jira: `TMI-120` — `[Billing] Retained trial owner rebind consumer 및 Transaction 구현`
- 선행 작업: Billing `PLAN-005`/`TMI-117`, Learning Core `TMI-118`과 후속 Summary Transaction 보완 merge 완료
- 관련 계약: `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`, `docs/adr/ADR-003-retained-trial-owner-rebind-contract.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `docs/codex/CONTRACT_DECISIONS.md`

## 1. 5줄 결론

1. 다음 제품 vertical slice는 탈퇴·재가입 또는 계정 merge 뒤 기존 무료시험 권리를 새 canonical `userId`에 안전하게 연결하는 Billing owner rebind다.
2. 새 `TrialClaim`, Grant, allocation 또는 consumption을 만들지 않고 stable `subjectRefId`의 현재 owner mapping만 source→target으로 변경한다.
3. Identity의 현재 `UserMerged` v1은 Guest→기존 Member merge 전용이므로 phone 재가입에 재사용하지 않고 별도 승인 event를 사용한다.
4. 진행 중 `RESERVED`/PROCESSING command는 이전하지 않고 최대 5분 lifecycle 종료까지 retry하며, 이미 시작된 AttemptGroup은 이전 Session event를 잃지 않도록 fencing한다.
5. `UserMerged`만 Billing·Learning Core 양쪽 consumer가 처리한다. phone 재가입은 Billing-only이며 continuation discovery로 기존 group/mock을 받은 뒤 명시적 `PHONE_REJOIN REPLACEMENT`로 target의 새 Session을 만들고 과거 Session·결과는 이전하지 않는다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 현재 제품 공백

기존 무료권을 받은 사용자가 탈퇴한 뒤 같은 phone을 검증해 새 계정으로 가입하면 Identity는 새 UUID `userId`를 발급한다.

```text
기존 Claim candidate와 새 VERIFIED candidate 일치
→ 기존 TrialClaim 발견
→ BillingSubjectLink.userId는 탈퇴 전 source userId
→ 새 target userId와 owner mismatch
→ ENTITLEMENT_INSUFFICIENT
```

이 동작은 두 번째 무료권 지급은 막지만 다음 권리까지 사용할 수 없게 한다.

- 아직 사용하지 않은 기존 무료 1회권
- 최종 실패로 `RETAKE_AVAILABLE`인 같은 consumption의 재응시
- merge 전에 이미 생성된 non-terminal AttemptGroup

### 2.2 이번 작업의 목표 상태

```text
Identity가 source→target lifecycle 관계를 승인
→ Billing event strict decode·멱등성 확인
→ source의 active retained subject link 확인
→ 진행 중 Reservation fencing
→ BillingSubjectLink owner를 target으로 CAS 변경
→ Claim·Grant·ledger·Reservation·AttemptGroup의 subjectRefId는 유지
→ target이 미사용권 또는 same-consumption 재응시 사용
→ 재응시는 기존 Session 이전 없이 target의 새 examId로 처음부터 시작
```

변하지 않는 값:

- `trialClaimId`, `claimedAt`, `retentionExpiresAt`
- `subjectRefId`
- Grant total/available/held/consumed unit
- 기존 ledger와 `consumptionLedgerEventId`
- `attemptGroupId`, `mockExamId`와 기존 source Session 기록

따라서 owner rebind는 무료권 재지급이나 consumption 복원이 아니다.

### 2.3 두 lifecycle을 구분한다

Identity의 현재 `UserMerged` schema v1은 다음 의미다.

```text
ACTIVE GUEST source
→ 이미 존재하는 ACTIVE MEMBER target으로 canonical merge
```

현재 field는 `eventId`, `schemaVersion`, `sourceUserId`, `targetUserId`, `occurredAt`이다. 이 event는 탈퇴 후 같은 phone으로 새 계정이 만들어졌다는 뜻이 아니다.

phone 재가입은 다음 의미다.

```text
탈퇴 또는 binding 종료된 source
→ 같은 retained phone candidate를 검증한 새 target
```

두 경우는 승인 조건과 downstream privacy 위험이 다르므로 wire event를 같은 의미로 추측하지 않는다. Billing application에서는 최종적으로 같은 `OwnerRebindCommand`로 정규화할 수 있지만 decoder, event type과 정책 validator는 분리한다.

## 3. 사용자가 결정해야 하는 사항

### D1. phone 재가입 owner rebind trigger

#### A. Identity의 별도 source→target 승인 event — 확정

Identity가 source account가 더 이상 active actor가 아니고 target이 새 canonical owner임을 확인한 뒤 `TrialOwnerRebindApproved` lifecycle event를 `POST /internal/v1/eligibility/trial/owner/events`로 발행한다.

장점:

- 단순한 candidate 일치와 Identity가 승인한 계정 승계를 구분한다.
- 전화번호 재할당 사용자를 과거 계정 소유자로 추측할 위험이 가장 작다.
- Billing이 phone당 무료권과 AttemptGroup 상태를 authoritative하게 판정할 수 있다.
- eventId 기반 재처리·감사·서비스 간 E2E가 명확하다.

단점:

- Identity producer/outbox와 소비자별 delivery 상태가 추가된다.
- target replacement 생성 시 Learning Core가 Billing phone continuation route에서 authoritative group/mock/context를 먼저 받고 exact echo한 경우만 받아들이는 계약 검증이 필요하다.
- consumer-first 배포와 producer activation 순서를 지켜야 한다.

#### B. Billing이 VERIFIED/REVOKED projection과 candidate로 lazy 추론

새 target의 최초 reserve에서 candidate alias가 기존 Claim과 일치하고 source eligibility가 REVOKED면 Billing이 owner를 자동 이전한다.

장점:

- 새 Identity wire event 없이 Billing 내부에서 시작할 수 있다.
- 실제 권리 사용 시점까지 불필요한 owner write를 미룬다.

단점:

- 같은 phone을 검증했다는 사실만으로 동일 인물 또는 승인된 계정 승계를 증명하지 못한다.
- Billing과 Learning Core가 서로 다른 시점과 근거로 owner를 판단할 수 있다.
- 번호 재할당, event 순서 역전과 이전 계정 시험 연결의 privacy 위험이 크다.
- retake에 필요한 Learning Core ownership migration trigger가 없다.

#### C. 기존 `UserMerged` v1을 phone 재가입에도 재사용

장점:

- event DTO 종류가 늘지 않는다.

단점:

- Guest merge라는 기존 의미를 깨뜨린다.
- event만 보고 account merge와 phone rejoin을 구분할 수 없다.
- 기존 consumer가 동일 schema를 서로 다르게 해석하게 된다.

확정값은 A다. C는 허용하지 않는다.

### D2. 구현 범위

#### A. 공통 owner-rebind domain + lifecycle별 decoder 분리 — 확정

- `UserMerged` adapter: Guest→Member merge 규칙
- phone rejoin adapter: 별도 승인 event 규칙
- 내부 `OwnerRebindCommand`: 검증된 source, target, reason과 event metadata
- 하나의 Mongo owner-transfer service와 상태 머신

장점:

- Transaction과 불변식을 재사용하면서 wire 의미는 섞지 않는다.
- 미래의 운영자 승인 이전도 별도 adapter로 확장할 수 있다.

단점:

- 초기 package와 test 수가 늘어난다.
- lifecycle별 승인 조건을 명확히 유지해야 한다.

#### B. phone 재가입만 먼저 구현

장점:

- 현재 제품 공백에 가장 작은 범위로 집중한다.

단점:

- 이미 존재하는 UserMerged source→target 계약을 Billing이 계속 처리하지 못한다.
- 이후 공통화할 때 migration과 중복 code가 생길 수 있다.

확정값은 A다. 첫 배포에서 adapter별 feature flag를 분리해 하나씩 활성화한다.

### D3. 진행 중 Reservation 처리

#### A. `RESERVED`/PROCESSING 종료까지 retry — 확정

active Reservation 또는 active RESERVE command가 있으면 owner link와 command `userId`를 실행 중에 고쳐 쓰지 않는다. `503 OWNER_REBIND_PENDING`과 bounded `Retry-After`를 반환하고 confirm/cancel/5분 expiry로 terminal이 된 뒤 event를 다시 처리한다.

장점:

- 진행 중 saga의 idempotency key와 response snapshot 의미가 바뀌지 않는다.
- confirm/cancel/expiry race를 owner transfer Transaction에 섞지 않는다.
- 최대 대기시간이 기존 Reservation 5분으로 제한된다.

단점:

- merge event 처리 완료가 최대 5분가량 늦을 수 있다.
- Identity publisher가 retryable 응답과 bounded backoff를 지원해야 한다.

#### B. active command와 Reservation까지 즉시 target으로 rewrite

장점:

- owner rebind 자체는 즉시 끝난다.

단점:

- 기존 payload hash·command unique key·Learning Core Session owner와 불일치한다.
- 응답 유실 retry가 source와 target 중 어디로 수렴하는지 불명확하다.
- immutable audit 의미가 손상된다.

확정값은 A다. B는 허용하지 않는다.

### D4. 이미 시작된 AttemptGroup event 처리

#### A. stable subject + bounded legacy-source fencing — 확정

owner rebind 뒤 새 reserve와 replacement authorization은 target만 허용한다. 다만 rebind 전에 생성된 exact active group/session의 Learning Core status event는 authenticated workload, group/session/subject fencing을 모두 통과하면 source userId도 terminal 수렴 목적으로만 한시 허용한다.

장점:

- 서비스별 owner event 순서가 뒤바뀌어도 이미 발생한 GRADING/terminal event를 잃지 않는다.
- source user를 새 API actor로 인정하지 않고 기존 exact Session 종료에만 사용한다.

단점:

- 이전 source와 적용 가능한 Session을 연결하는 최소 rebind record가 필요하다.
- chain rebind와 record purge 조건 테스트가 필요하다.

#### B. rebind 즉시 target event만 허용

장점:

- authorization 규칙이 단순하다.

단점:

- Learning Core에 이미 저장된 source userId outbox가 409로 영구 실패할 수 있다.
- Billing projection이 GRADING에 멈출 수 있다.

확정값은 A다.

### D5. source owner 정보 보존

#### A. 필요한 기간만 별도 rebind record에 보존 후 삭제 — 확정

source userId는 기존 pre-rebind active Session의 terminal 수렴과 duplicate 처리에 필요한 동안만 저장한다. 관련 Session이 terminal이고 inbox retry window가 끝나면 source/target 연결을 제거하고 eventId, digest, 처리 결과와 비식별 건수만 남긴다. 어떤 경우에도 Claim의 3년 만료를 넘기지 않는다.

장점:

- late event fencing과 개인정보 최소화를 함께 충족한다.
- `BillingSubjectLink`는 current owner만 유지한다.

단점:

- cleanup worker와 overdue 경보가 필요하다.

#### B. Claim 보존 3년 동안 전체 source→target history 유지

장점:

- 운영 조사와 매우 늦은 replay가 쉽다.

단점:

- 처리 목적이 끝난 과거 userId 연결을 불필요하게 오래 보존한다.

확정값은 A다. 관련 Session terminal 뒤 24시간 안에 source 연결을 cleanup하고, terminal 미수렴 hard upper bound는 `min(rebindAppliedAt + 120일, Claim retentionExpiresAt)`이다.

## 4. 주요 위험과 미확인 사항

### 4.1 Identity의 현재 UserMerged delivery는 Learning Core 전용이다

현재 Identity UserMerged outbox는 단일 publisher endpoint와 단일 delivery 상태를 갖는다. 같은 event를 Billing에도 보내려면 단순히 endpoint를 Billing로 바꾸면 안 된다.

확정 방식은 immutable event core 하나와 `(eventId, consumer)` unique delivery record다. `UserMerged` consumer allowlist는 `BILLING`, `LEARNING_CORE`, `TrialOwnerRebindApproved`는 `BILLING`이며 Identity lifecycle Transaction에서 core와 lifecycle별 필수 delivery를 원자 저장한다.

각 delivery는 status, attempt, nextAttemptAt, lease, published/dead-letter 상태와 publisher feature flag를 독립 관리한다. Learning Core 성공과 Billing 실패를 하나의 `PUBLISHED` 값으로 덮지 않고 동기 순차 POST나 consumer별 full payload 복제를 사용하지 않는다.

### 4.2 Learning Core에는 현재 UserMerged consumer가 없다

2026-09-01 코드 검색 기준 Learning Core에는 `UserMerged`, `sourceUserId`, `targetUserId` consumer 구현이 없다. 실제 계정 통합인 `UserMerged`에는 source deny와 기존 시험 ownership migration이 필요하다.

phone 재가입은 이 consumer를 사용하지 않는다. source의 기존 시험·Session은 그대로 두고, target reserve에 대해 Billing이 반환한 기존 attemptGroupId의 `REPLACEMENT`를 Learning Core가 받아 target 명의 새 Session을 생성해야 한다. source로 이미 생성된 AttemptGroup status outbox는 Billing legacy fence로만 수렴한다.

따라서 Billing PLAN-006 구현 완료는 production owner rebind 활성화 완료를 뜻하지 않는다. Learning Core `UserMerged` migration/source deny와 phone replacement Session E2E가 별도 선행 gate다.

### 4.3 phone 재할당과 과거 시험 정보

phone당 무료 1회 dedupe는 번호 재할당에도 3년 동안 새 Claim을 막는다. 그러나 새 번호 소유자에게 과거 사용자의 시험 연결까지 이전하는 것은 별도 privacy 결정이다.

전용 Identity 승인 event가 없다면 Billing은 정상 재가입과 번호 재할당을 구분할 수 없다. 이 때문에 candidate 일치만으로 AttemptGroup을 자동 이전하는 D1-B는 권장하지 않는다.

### 4.4 다단계 owner chain

`A→B` 처리 뒤 `B→C`가 올 수 있다. 다음을 fail-closed해야 한다.

- `source == target`
- 현재 owner가 source가 아닌 임의 transfer
- `A→B`, `A→C`처럼 같은 source의 서로 다른 target conflict
- 이미 target인 exact duplicate
- cycle 또는 stale predecessor event

owner sequence/revision이 wire에 없다면 Billing current-link CAS와 event inbox를 최종 순서 경계로 사용한다. source가 current owner가 아닌 신규 event는 자동 chain으로 추측하지 않는다.

### 4.5 Workload 인증 불일치

현재 Billing internal workload 목표 계약은 VPC Lattice `AWS_IAM`과 SigV4다. Identity의 기존 UserMerged publisher 문서는 Bearer workload credential을 전제로 한다. Billing route를 추가할 때는 Identity task role의 exact route 권한과 SigV4 client를 reader/consumer-first 순서로 맞춰야 하며 shared secret이나 새 workload JWT를 추가하지 않는다.

## 5. 구현 범위

### 5.1 이번 PLAN에 포함

- lifecycle별 strict decoder와 내부 `OwnerRebindCommand`
- eventId/digest 멱등성, same-ID different-payload conflict
- source→target/current-owner 상태 전이표
- active Claim과 unexpired subject link 검증
- phone rejoin의 source inactive·target verified·candidate-to-Claim 일치 검증
- phone AttemptGroup 없음/OPEN/RETAKE_AVAILABLE owner 이전, GRADING pending, COMPLETED NOOP 판정
- active Reservation/command pending 판정
- `BillingSubjectLink.userId` expected-owner CAS와 owner version
- late pre-rebind AttemptGroup event용 bounded legacy-source fencing
- owner rebind inbox/record와 개인정보 cleanup
- stable subject 기반 Claim·Grant·ledger·AttemptGroup 불변식 검증
- Mongo Transaction retry, duplicate-key와 unknown commit result 수렴
- Identity workload route local/test security
- low-cardinality metric, W3C trace와 구조화 로그
- schema/index initializer fail-fast와 replica-set Testcontainers 테스트
- ADR-001, 통합 계약, CONTRACT_DECISIONS와 운영 문서 갱신

### 5.2 이번 PLAN에서 제외

- Identity merge/rejoin producer·outbox·fan-out 코드
- Learning Core `UserMerged` consumer와 실제 계정 통합 범위의 시험 데이터 migration
- phone source Session·답안·결과 migration 또는 phone owner event route
- 앱 공개 API와 사용자 직접 Billing 호출
- 새로운 Claim, Grant, allocation 또는 balance 생성
- 기존 ledger event rewrite 또는 consumption 복원
- `COMPLETED` AttemptGroup 재개방
- paid, subscription, coupon, refund와 Store 기능
- MEMBER→MEMBER 임의 transfer와 고객센터 수동 repair route
- 실제 Lattice/IAM/SG/ECS 생성과 production flag 활성화
- TrialClaim 3년 purge worker 자체 구현

## 6. 승인된 wire 계약

다음 exact event·route·pending response는 2026-09-02 승인됐고 phone 상태별 처리와 destination은 2026-09-03 보정됐다. 구체적인 delivery, IAM, Mongo schema v4와 legacy cleanup 실행 계약은 `ADR-003-retained-trial-owner-rebind-contract.md`를 따른다.

### 6.1 Guest→Member merge

Identity의 기존 UserMerged v1 JSON field와 의미를 변경하지 않는다.

```json
{
  "eventId": "018f6f36-2f42-4bf5-8c17-0be35de4872c",
  "schemaVersion": 1,
  "sourceUserId": "018f6f36-2f42-4bf5-8c17-0be35de4872d",
  "targetUserId": "018f6f36-2f42-4bf5-8c17-0be35de4872e",
  "occurredAt": "2026-09-01T06:00:00Z"
}
```

Billing route는 `POST /internal/v1/owners/merge/events`다. endpoint context가 event type과 producer를 고정하며 기존 v1 payload에 field를 임의 추가하지 않는다. Billing 전용 durable delivery stream의 구현 방식은 ADR-003에서 확정한다.

### 6.2 phone 재가입

```json
{
  "eventId": "018f6f36-2f42-4bf5-8c17-0be35de4872c",
  "eventType": "TrialOwnerRebindApproved",
  "schemaVersion": 1,
  "producer": "identity",
  "consumerScopeId": "<expected opaque trial scope>",
  "occurredAt": "2026-09-01T06:00:00Z",
  "sourceUserId": "018f6f36-2f42-4bf5-8c17-0be35de4872d",
  "targetUserId": "018f6f36-2f42-4bf5-8c17-0be35de4872e",
  "lifecycleReason": "PHONE_REJOIN",
  "sourceBindingRevision": 2,
  "targetBindingRevision": 1
}
```

- route: `POST /internal/v1/eligibility/trial/owner/events`
- `eventType=TrialOwnerRebindApproved`
- `schemaVersion=1`
- `producer=identity`
- `lifecycleReason=PHONE_REJOIN`
- `consumerScopeId`는 Billing expected opaque scope와 exact match한다.
- `sourceBindingRevision`, `targetBindingRevision`은 1 이상의 integer이며 Billing projection prerequisite fencing에 사용한다.
- UUID는 lowercase canonical UUID
- raw phone, candidate, Firebase UID, email과 credential은 포함하지 않는다.
- candidate 일치는 Billing current VERIFIED projection과 retained alias로 재검증한다.
- source account가 inactive라는 Identity 승인 의미와 source eligibility REVOKED 조건을 모두 요구한다.

### 6.3 strict decoder

- body 16 KiB 이하; 기존 UserMerged producer 상한은 4 KiB 유지
- duplicate field, trailing token, unknown field와 scalar coercion 거절
- exact schema/event/producer/route contract 검증
- source와 target UUID canonical validation, `source != target`
- occurredAt UTC Instant parse와 future clock-skew validation
- raw payload 비저장·비로깅
- canonical validated JSON 또는 승인된 semantic byte 형식의 SHA-256 digest

### 6.4 응답 계약

| 상황 | HTTP | code/의미 |
| --- | --- | --- |
| 신규 APPLIED 또는 권리 없는 source NOOP commit | 204 | body 없음 |
| 동일 eventId + 동일 digest | 204 | duplicate |
| malformed | 400 | `INVALID_REQUEST` |
| 알 수 없는 schema/producer/reason | 422 | `UNSUPPORTED_CONTRACT` |
| same eventId different digest 또는 source target conflict | 409 | `EVENT_ID_CONFLICT`/`OWNER_REBIND_CONFLICT` |
| active Reservation/command 또는 prerequisite 미수렴 | 503 | retryable `OWNER_REBIND_PENDING` + `Retry-After` |
| Mongo 일시 장애 | 503 | `BILLING_TEMPORARILY_UNAVAILABLE` |

- `Retry-After`는 HTTP-date가 아닌 delta-seconds integer만 사용한다.
- active Reservation은 `ceil(expiresAt-now)`를 1~300초로 clamp한다.
- PROCESSING 또는 projection prerequisite pending은 `Retry-After: 5`다.
- 425, 202와 409를 temporary pending에 사용하지 않는다. 409는 permanent event/owner conflict에만 사용한다.
- `OWNER_REBIND_PENDING`과 Mongo/서비스 장애는 stable error code와 low-cardinality metric으로 구분한다.

### 6.5 phone continuation discovery와 reserve 확장

Learning Core 전용 read-only route는 `POST /internal/v1/reservations/continuations/phone`이다. exact request는 target `userId` 하나뿐이다.

- current `BillingSubjectLink.ownerTransitionReason=PHONE_REJOIN`
- `ownerTransitionId`가 존재
- 같은 subject의 group이 `OPEN` 또는 `RETAKE_AVAILABLE`
- group의 Claim/subject가 link와 일치

위 조건이면 200으로 `continuationReason=PHONE_REJOIN`, `continuationId=ownerTransitionId`, existing `attemptGroupId`, existing `mockExamId`를 반환한다. 대상 없음은 204, GRADING은 retryable 409, 중복·projection 불일치는 503이다.

phone reserve는 기존 request에 `continuationReason`, `continuationId`, `expectedAttemptGroupId`를 모두 추가한다. strict decoder는 기본 3-field schema 또는 phone 6-field schema만 허용한다. Reserve Transaction은 owner transition ID/reason과 expected group/mock을 다시 검사하고, 성공 snapshot·Reservation·status에 optional reason/id를 저장한다. 일반 request/response에는 optional field가 없으며 일반 unexpected REPLACEMENT는 Learning Core에서 fail-closed한다.

## 7. Domain 상태와 불변식

### 7.1 정상 전이

```text
current link owner = source
+ 승인된 source→target event
+ Claim active/unexpired
+ active Reservation 없음
+ lifecycle별 prerequisite 충족
→ owner = target
```

### 7.2 결과 분류

- `APPLIED`: 하나 이상의 current subject link가 source→target으로 이전됨
- `DUPLICATE`: exact event가 이미 같은 결과로 commit됨
- `NOOP`: source가 보유한 retained 무료 Claim이 없거나 phone AttemptGroup이 `COMPLETED`라 변경하지 않음
- `PENDING`: active Reservation/command 또는 선행 eligibility가 아직 수렴하지 않음
- `CONFLICT`: current owner, event relation 또는 digest가 모순됨
- `STALE`: 승인된 predecessor가 이미 적용돼 event가 더 이상 current owner에 해당하지 않음

`NOOP`는 새 Claim을 만들지 않는다. 이후 target이 VERIFIED 상태로 최초 reserve하면 일반 lazy Claim 발급 규칙을 따른다.

### 7.3 current owner와 exact replay

| current link | event source→target | 결과 |
| --- | --- | --- |
| source | source→target | 신규 적용 후보 |
| target | source→target, same event/digest | DUPLICATE |
| target | source→target, new event | STALE 또는 duplicate semantic 판정 |
| third user | source→target | CONFLICT |
| source | target→source | 별도 승인 없는 reverse이므로 CONFLICT |

### 7.4 stable subject 원칙

다음 aggregate는 `subjectRefId`를 사용하므로 owner rebind 때 rewrite하지 않는다.

- `TrialClaim`
- `EntitlementGrant`
- `EntitlementLedgerEntry`
- `Reservation`, `ReservationAllocation`
- `AttemptGroup`, `AttemptSession`

`BillingSubjectLink`의 current `userId`와 owner version만 변경한다. 기존 `IdempotencyCommand.userId`와 response snapshot은 audit/idempotency 의미를 보존하기 위해 rewrite하지 않는다.

phone 재가입 상태표:

| AttemptGroup | 처리 |
| --- | --- |
| 없음 | 미사용 owner 이전 |
| `OPEN`, `RETAKE_AVAILABLE` | same-consumption replacement 권리를 target으로 이전 |
| `GRADING` | 503 pending 후 재판정 |
| `COMPLETED` | owner/fence 변경 없는 NOOP 204 |

replacement는 source Session을 target으로 rewrite하지 않는다. target의 새 key·새 examId로 같은 group·mockExamId 아래 처음부터 시작한다.

## 8. Mongo 설계 초안

### 8.1 `billing_subject_links` 확장

```text
subjectRefId, trialClaimId, consumerScopeId, userId,
active, createdAt, retentionExpiresAt,
ownerVersion, ownerUpdatedAt,
ownerTransitionReason?, ownerTransitionId?
```

- legacy document의 `ownerVersion` reader-first 호환과 migration은 ADR-003의 schema v4 계약을 따른다.
- update 조건은 `subjectRefId + active=true + userId=source + ownerVersion=expected`다.
- update 결과 0이면 Transaction 안에서 current link를 다시 읽어 duplicate/stale/conflict를 판정한다.

### 8.2 owner rebind inbox/record

collection 이름과 exact field/index는 ADR-003에서 `owner_rebind_inbox`, `subject_owner_rebinds`로 확정한다. 필요한 최소 의미:

```text
eventId, eventKind, schemaVersion, payloadDigest,
subjectRefId?, trialClaimId?, sourceUserId?, targetUserId?,
disposition, occurredAt, receivedAt, appliedAt?,
legacyFenceUntil?, purgeAt
```

- raw JSON, phone candidate, email과 credential은 저장하지 않는다.
- eventId unique와 source/target conflict 검증에 필요한 최소 field만 저장한다.
- sourceUserId는 pre-rebind Session terminal fencing이 끝나면 unset한다.
- digest/event outcome의 보존기간과 user 연결 보존기간을 분리할 수 있어야 한다.
- Claim retention 만료 뒤 source/target/user 연결이 남지 않아야 한다.
- exact Session terminal 뒤 daily cleanup이 24시간 안에 sourceUserId를 unset한다.
- terminal 미수렴 hard upper bound는 `min(rebindAppliedAt + 120일, Claim retentionExpiresAt)`이다.
- 120일 hard cap 뒤 late source event는 자동 authorization이 아니라 privileged reconciliation 대상이다.

### 8.3 index 초안

| collection | key | option | 목적 |
| --- | --- | --- | --- |
| owner rebind inbox | `{eventId: 1}` | unique | eventId 멱등성 |
| owner rebind inbox | `{sourceUserId: 1, eventKind: 1}` | partial, 정책 확정 후 unique 검토 | 같은 source의 모순된 target 탐지 |
| owner rebind inbox | `{subjectRefId: 1, disposition: 1}` | non-unique | legacy Session fencing |
| owner rebind inbox | `{purgeAt: 1}` | TTL 0 | 승인된 개인정보 cleanup |
| `billing_subject_links` | 기존 `{trialClaimId: 1}` | unique 유지 | Claim당 current link 하나 |
| `billing_subject_links` | 기존 `{userId: 1, active: 1}` | non-unique 유지 | user의 active link 조회 |

기존 index를 runtime에서 drop/recreate하지 않는다. schema v4 preflight, option comparison과 production migration을 분리한다.

## 9. Mongo Transaction 경계

한 owner rebind event 처리 Transaction은 다음 순서를 유지한다.

1. eventId/digest inbox claim 또는 기존 결과 확인
2. source current active/unexpired subject link 조회
3. event kind별 prerequisite 확인
4. active Reservation와 PROCESSING/active command 확인
5. phone AttemptGroup 상태 판정과 COMPLETED NOOP/GRADING pending 분기
6. current owner와 ownerVersion fencing
7. `BillingSubjectLink` source→target CAS update
8. pre-rebind active group/session이 있으면 bounded legacy-source fence 기록
9. rebind record disposition과 processed timestamp 저장
10. commit 확인 뒤에만 204 반환

다음은 같은 Transaction에서 만들거나 변경하지 않는다.

- 새 Claim/Grant/ledger allocation
- 기존 Grant 수량
- Reservation status
- AttemptGroup/AttemptSession status
- terminal idempotency command

`DuplicateKeyException`, `TransientTransactionError`와 `UnknownTransactionCommitResult`는 abort된 Transaction을 계속 사용하지 않는다. 바깥 bounded retry가 eventId와 current owner 상태를 다시 읽고 새 Transaction으로 전체 unit을 재실행한다.

## 10. Reservation과 AttemptGroup fencing

### 10.1 active Reservation

다음 중 하나면 owner link를 변경하지 않고 PENDING을 반환한다.

- source subject의 `Reservation.status=RESERVED`
- source user의 active RESERVE command
- lifecycle command가 PROCESSING이며 commit 결과가 아직 수렴하지 않음

confirm/cancel 또는 5분 expiry 뒤 publisher retry가 owner rebind를 다시 실행한다.

### 10.2 confirmed/non-terminal group

AttemptGroup과 Session은 stable subjectRef를 유지한다. rebind 적용 시 다음을 기록한다.

```text
subjectRefId
sourceUserId
targetUserId
preRebind activeSessionId/attemptGroupId 또는 그 검증 가능한 fence
appliedAt
```

이 기록은 다음 용도로만 사용한다.

- rebind 전에 Learning Core outbox에 저장된 exact Session status event 수렴
- source event가 신규 reserve authorization으로 사용되지 않도록 분리
- target event가 도착하면 current owner exact match 처리

source event 허용 조건:

1. authenticated Learning Core workload
2. exact subject/group/session relation
3. 해당 Session이 rebind 전에 생성됨
4. event가 GRADING 또는 terminal projection 전진 목적
5. 새 Reservation/REPLACEMENT authorization이 아님

관련 Session이 terminal이면 legacy source 연결을 cleanup 대상으로 전환한다.

## 11. 보안·개인정보·관측성

- Identity task role만 owner rebind route를 호출할 수 있다.
- Learning Core task role은 해당 event route를 호출할 수 없다.
- unsigned, wrong role, wrong route와 direct task bypass를 거절한다.
- sourceUserId, targetUserId, subjectRefId, Claim/group/session ID를 metric tag로 사용하지 않는다.
- raw payload, candidate, digest, Authorization/SigV4 header와 credential을 log/trace에 넣지 않는다.
- 구조화 로그는 `service`, `operation`, `outcome`, `traceId`, `eventId`, `durationMs`만 허용한다.
- eventId는 구조화 로그에만 사용하고 span attribute에는 넣지 않는다.
- W3C traceparent inbound는 연결하고 baggage는 전파·저장하지 않는다.
- low-cardinality metric outcome은 APPLIED/DUPLICATE/NOOP/PENDING/STALE/CONFLICT/TEMPORARY_FAILURE로 제한한다.
- cleanup 로그에는 처리 건수와 성공 여부만 남기고 user/subject ID를 기록하지 않는다.

## 12. 제안 package와 파일 초안

기존 domain/global 구조를 유지한다.

```text
domain/ownerrebind/
├─ api/
│  ├─ OwnerRebindEventController
│  └─ lifecycle별 strict decoder
├─ application/
│  ├─ OwnerRebindService
│  ├─ OwnerRebindPolicyValidator
│  ├─ OwnerRebindTracing
│  └─ OwnerRebindMetrics
├─ domain/
│  ├─ OwnerRebindCommand
│  ├─ OwnerRebindRecord
│  ├─ OwnerRebindReason
│  └─ OwnerRebindOutcome
├─ repository/
│  └─ OwnerRebindRepository
└─ exception/
   └─ OwnerRebindException
```

실제 이름은 프로젝트의 converter/dto 분리 스타일에 맞추되 eligibility나 reservation package 안에 owner transfer 전체를 섞지 않는다.

## 13. 테스트 계획

### 13.1 strict contract test

- exact UserMerged v1 fixture reader compatibility
- phone rejoin 승인 event fixture
- duplicate field, unknown field, trailing token와 scalar coercion 거절
- noncanonical UUID, source=target, future time와 oversize 거절
- property order/whitespace 차이는 같은 digest
- field 값 차이는 다른 digest
- raw payload·userId·credential logging 없음

### 13.2 application/state test

- source link 없음 → NOOP, 새 Claim/Grant 없음
- source current owner → target APPLIED
- exact duplicate → 204 DUPLICATE
- same eventId different digest → conflict
- current owner third user → conflict
- source→target 뒤 stale/reverse/chain event fencing
- phone rejoin target VERIFIED + retained candidate match 성공
- target not verified, source not revoked 또는 candidate mismatch fail-closed
- Claim expired/anonymized, subject link inactive이면 이전 금지
- Grant/ledger unit와 Claim retention timestamp 불변

### 13.3 Reservation/AttemptGroup test

- active RESERVED/command이면 no write + retryable PENDING
- cancel/expiry 뒤 retry가 한 번만 APPLIED
- confirm/rebind race가 stable subject 하나로 수렴
- phone 이력 없음·OPEN·RETAKE_AVAILABLE이면 owner 이전
- phone GRADING이면 no write + retryable PENDING
- phone COMPLETED이면 owner·Claim·Grant·consumption·fence 불변 NOOP, replay 204
- pre-rebind exact source Session GRADING/terminal event 허용
- source의 새 reserve/다른 Session event 거절
- target REPLACEMENT가 같은 consumption/mockExamId를 사용
- target REPLACEMENT는 source Session 기록을 이전하지 않고 새 examId로 시작
- phone continuation 200/204와 exact owner epoch/group/mock 검증
- 잘못되거나 일부만 온 context fail-closed, 일반 replacement에는 phone reason 없음
- COMPLETED group을 re-open하거나 새 free unit을 만들지 않음

### 13.4 replica-set Testcontainers

- inbox + link + rebind record atomic commit/rollback
- concurrent exact duplicate event 하나만 적용
- same source different target race conflict
- owner rebind와 reserve/confirm/expiry concurrency
- duplicate key abort 뒤 새 Transaction whole-unit retry
- unknown commit result 후 current owner/inbox 수렴
- index key/order/partial/TTL option과 schema v4 fail-fast
- chain transfer와 legacy fence cleanup race

### 13.5 security/trace test

- Identity role success
- unsigned/wrong role/Learning Core role/wrong route/direct bypass 실패
- inbound traceId 유지, HTTP span과 업무 span 분리
- baggage 미전파
- 정상·exception span 종료와 금지 attribute 부재

## 14. 구현 순서

1. 승인된 D1~D5를 반영한 owner rebind ADR-003 작성·검토 승인 — 완료
2. Identity UserMerged fixture와 phone rejoin event contract를 consumer-first로 고정 — 완료
3. schema v4 data preflight·index migration 계획 작성 — 완료
4. domain command/outcome/state validator 구현 — 완료
5. strict decoder, canonical digest와 internal error mapping 구현 — 완료
6. owner rebind inbox/record와 repository CAS 구현 — 완료
7. Mongo Transaction service, retry·unknown commit 수렴 구현 — 완료
8. Reserve/AttemptGroup legacy-source fencing 연결 — 완료
9. cleanup worker와 privacy/metric/trace 구현 — 완료
10. unit/MVC/security/replica-set integration test 완료 — 테스트 코드 구현, local Docker 실행 검증 중
11. ADR-001·통합 계약·CONTRACT_DECISIONS·runbook 갱신 — TMI-120 구현 상태 반영 완료
12. Billing consumer flag off 배포
13. Identity event별 durable delivery와 Learning Core `UserMerged` consumer/phone replacement 처리 구현
14. staging에서 순서 역전·응답 유실·auth failure·phone replacement E2E
15. consumer readiness 확인 뒤 Identity producer, Billing, Learning Core 관련 flag 순차 canary

## 15. 완료 조건

- 승인된 source→target event가 새 무료권 없이 current owner mapping 하나로 수렴한다.
- `claimedAt + 3년`, Claim ID, subjectRefId, Grant 수량과 consumption ledger가 변하지 않는다.
- 미사용권과 OPEN/RETAKE_AVAILABLE same-consumption replacement만 target이 이어받는다.
- COMPLETED phone history는 owner/fence를 바꾸지 않고 과거 시험·답안·피드백을 target에 연결하지 않는다.
- phone replacement는 기존 Session을 이전하지 않고 target의 새 examId로 처음부터 시작한다.
- active Reservation/command를 rewrite하지 않고 lifecycle 종료 뒤 재시도한다.
- 중복, 응답 유실, concurrent event와 unknown commit에서 이중 transfer가 없다.
- rebind 전 exact source Session의 terminal event가 유실되지 않고 source 신규 authorization은 차단된다.
- phone 재가입과 Guest merge wire 의미가 분리된다.
- 개인정보 cleanup과 TrialClaim retention upper bound가 지켜진다.
- local 전체 test와 replica-set integration test가 성공한다.
- Billing·Identity·Learning Core cross-service staging gate 전 production flag는 기본 false다.

## 16. 배포·롤백

### 배포

1. schema/index preflight와 backup 확인
2. Billing reader/consumer와 flag off 배포
3. Learning Core `UserMerged` consumer와 phone continuation reader·명시적 replacement 처리 flag off 배포
4. Identity event별 outbox/delivery writer 배포, publisher off
5. staging positive/negative, event ordering과 phone replacement E2E
6. Identity publisher idle/canary
7. Billing owner rebind canary
8. Learning Core `UserMerged` migration과 phone replacement canary
9. metric·dead-letter·pending age 확인 뒤 확대

### 롤백

- producer publisher를 먼저 끄고 신규 owner event 생성을 중단한다.
- consumer는 duplicate replay를 위해 유지한다.
- 이미 적용된 owner mapping을 DB 수동 update로 source에 되돌리지 않는다.
- 잘못된 event는 일반 workload route가 아니라 별도 승인된 repair ADR·운영 role로 처리한다.
- schema/index는 기존 version compatibility를 확인하지 않고 runtime drop하지 않는다.

## 부록 A. 구현 사실·계획·추론 구분

### 확인된 구현 사실

- Billing owner 확인의 current source는 `BillingSubjectLink.userId`다.
- Claim, Grant, Reservation, AttemptGroup과 AttemptSession은 stable `subjectRefId`를 사용한다.
- `IdempotencyCommand`는 userId를 직접 저장하고 lifecycle에서 request userId와 비교한다.
- current owner mismatch reserve는 `ENTITLEMENT_INSUFFICIENT`로 차단한다.
- Identity UserMerged v1은 Guest source→Member target과 5개 field 계약이다.
- Learning Core에는 2026-09-01 기준 UserMerged consumer 구현이 없다.
- Billing에는 lifecycle별 owner event endpoint·strict decoder, schema v4 owner CAS/inbox/fence, AttemptGroup legacy source fencing과 cleanup worker가 구현돼 있다.

### 아직 외부 후속 계획인 내용

- Identity의 phone 재가입 전용 event producer
- Learning Core `UserMerged` owner migration과 source deny marker
- Identity의 event별 durable delivery (`UserMerged`: Billing/LC, phone: Billing-only)
- 실제 Lattice/IAM/SG와 cross-service staging E2E 뒤 production flag 활성화

### PLAN의 분석·권장

- candidate lazy 추론보다 Identity의 별도 source→target 승인 event가 안전하다.
- active Reservation을 rewrite하지 않고 5분 lifecycle 종료를 기다리는 편이 idempotency를 보존한다.
- stable subject aggregate는 rewrite하지 않고 current owner mapping만 CAS 이전해야 한다.
- pre-rebind exact Session의 source status event는 terminal 수렴에 한해 한시 허용해야 event 순서 역전을 견딜 수 있다.

## 부록 B. Jira 초안 범위

사용자 승인에 따라 Jira `TMI-120`을 생성했다.

- 제목: `[Billing] Retained trial owner rebind consumer 및 Transaction 구현`
- 포함: 승인 event consumer, strict decode/digest, owner CAS, active Reservation pending, legacy Session fencing, schema/index, replica-set/security/trace test
- 제외: Identity producer/delivery, Learning Core `UserMerged` ownership migration, AWS resource 생성, production flag 활성화, 결제·구독·coupon
- 상태: `해야 할 일`, resolution 없음
- 선행 조건: 승인 wire를 consumer별 delivery·IAM·legacy cleanup 값으로 구체화한 ADR-003 승인 — 완료
