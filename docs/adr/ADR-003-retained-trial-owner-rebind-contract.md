# ADR-003: Retained trial owner rebind 계약

- 상태: 승인 — C14·PLAN-006·TMI-120의 owner rebind 구현 기술 기준
- 작성일: 2026-09-02
- 대상 릴리스: 결제 제외 `FREE_EXAM_ONCE` owner rebind vertical slice
- Jira: `TMI-120` — `[Billing] Retained trial owner rebind consumer 및 Transaction 구현`
- 선행 기준: `ADR-001`, `ADR-002`, `CONTRACT_DECISIONS.md`, `PLAN-006`

## 1. 5줄 결론

1. owner rebind는 새 무료권을 지급하는 기능이 아니라 stable `subjectRefId`의 current owner만 source에서 target으로 CAS 이전하는 기능이다.
2. phone 재가입 `TrialOwnerRebindApproved`와 Guest merge `UserMerged`는 서로 다른 strict decoder·route를 사용하고 검증 뒤 내부 `OwnerRebindCommand`로만 수렴한다.
3. Billing Mongo schema는 v4로 올리며 `owner_rebind_inbox`, `subject_owner_rebinds`와 `BillingSubjectLink.ownerVersion`을 추가하되 legacy v3 document를 자동 bulk rewrite하지 않는다.
4. active Reservation/PROCESSING은 503 pending으로 미루고, rebind 전에 생성된 exact Session status만 legacy source fencing으로 terminal 수렴까지 한시 허용한다.
5. Identity consumer별 durable fan-out, Learning Core owner migration/source deny와 순서 역전 staging E2E가 끝나기 전에는 Billing production owner-rebind flag를 활성화하지 않는다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 이 작업이 바꾸는 것과 바꾸지 않는 것

변경하는 값은 `billing_subject_links.userId`, `ownerVersion`, `ownerUpdatedAt`뿐이다.

다음 값과 기록은 그대로 유지한다.

- `trialClaimId`, `claimedAt`, `retentionExpiresAt`
- `subjectRefId`
- Grant의 total/available/held/consumed unit
- entitlement ledger와 `consumptionLedgerEventId`
- Reservation, AttemptGroup, AttemptSession과 `mockExamId`
- 기존 `IdempotencyCommand.userId`, payload hash와 response snapshot

따라서 이 기능은 다음 행동을 하지 않는다.

- 새 `TrialClaim`, Grant, allocation 또는 consumption 생성
- cancel·expiry로 Claim 재개방
- 이미 소비된 무료권 복원
- `COMPLETED` AttemptGroup 재개방
- 기존 ledger 또는 command audit rewrite

### 2.2 두 lifecycle은 같은 event가 아니다

| lifecycle | wire event | Billing route | Identity가 보증하는 의미 |
| --- | --- | --- | --- |
| phone 재가입 | `TrialOwnerRebindApproved` v1 | `POST /internal/v1/eligibility/trial/owner/events` | source binding 종료, 같은 retained candidate의 target 검증과 owner 이전 승인 |
| Guest merge | `UserMerged` v1 | `POST /internal/v1/owners/merge/events` | ACTIVE GUEST source가 기존 ACTIVE MEMBER target으로 canonical merge됨 |

두 endpoint는 각자 exact field 집합을 strict decode한다. 하나의 generic JSON DTO로 받은 뒤 `reason`만 보고 의미를 추측하지 않는다. decoder를 통과한 뒤에만 공통 application command로 변환한다.

### 2.3 Billing만 배포해도 기능은 열리지 않는다

Billing owner mapping만 target으로 변경하고 Learning Core가 source 소유 시험을 그대로 유지하면 target의 재응시와 기존 Session event가 서로 어긋난다.

production 활성화에는 다음이 모두 필요하다.

1. Billing 두 consumer route와 feature flag off 배포
2. Learning Core 두 owner event consumer, source actor deny와 ownership migration 배포
3. Identity event core + `BILLING`/`LEARNING_CORE` delivery fan-out과 SigV4 배포
4. production/staging exact IAM policy와 direct bypass 차단
5. staging에서 두 서비스 delivery 순서를 뒤집은 E2E
6. consumer readiness 확인 뒤 producer와 publisher flag 단계적 활성화

### 2.4 legacy source 허용은 사용자 권한 승계가 아니다

owner rebind 전에 Learning Core outbox에 source userId로 이미 생성된 exact status event가 늦게 도착할 수 있다. Billing은 이 event가 다음 조건을 모두 만족할 때만 status projection의 전진을 허용한다.

- 인증된 같은 환경 Learning Core workload 요청
- fence에 저장된 exact `subjectRefId`, `attemptGroupId`, `sessionId`
- 해당 Session이 rebind 전에 만들어짐
- `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE` 중 기존 단방향 전이표가 허용하는 전진
- `legacyFenceExpiresAt` 이전

source user에게 신규 reserve, replacement, 다른 Session, status 조회 또는 사용자 actor 권한을 주지 않는다.

## 3. 사용자 승인 사항

2026-09-02 사용자가 ADR-003 전체와 다음 기술 기본값을 승인했다.

1. Mongo schema v4 collection은 `owner_rebind_inbox`, `subject_owner_rebinds`를 사용한다.
2. Guest merge 한 event의 retained subject 처리 상한은 100건이다.
3. cleanup worker는 최대 1시간 간격으로 실행해 승인된 24시간 SLA를 지킨다.
4. Learning Core phone rejoin route는 `POST /internal/v1/owners/trial/rebind/events`다.
5. TMI-120에는 historical backfill과 privileged mutation HTTP route를 포함하지 않는다.

따라서 Billing TMI-120 구현을 시작하는 데 필요한 계약 결정은 남아 있지 않다. Identity와 Learning Core 구현은 각 저장소의 별도 계획·Jira 승인이 필요하다.

## 4. 확정하는 기술 결정

### 4.1 HTTP wire와 strict decoder

#### 4.1.1 phone 재가입

```http
POST /internal/v1/eligibility/trial/owner/events
Content-Type: application/json
```

exact field 집합:

```json
{
  "eventId": "<lowercase UUID v4>",
  "eventType": "TrialOwnerRebindApproved",
  "schemaVersion": 1,
  "producer": "identity",
  "consumerScopeId": "<expected opaque trial scope>",
  "occurredAt": "<UTC Instant>",
  "sourceUserId": "<lowercase canonical UUID>",
  "targetUserId": "<lowercase canonical UUID>",
  "lifecycleReason": "PHONE_REJOIN",
  "sourceBindingRevision": 2,
  "targetBindingRevision": 1
}
```

- `sourceBindingRevision`, `targetBindingRevision`은 1 이상의 JSON integer다.
- `consumerScopeId`는 Billing 환경 설정의 expected opaque scope와 exact match한다.
- raw phone, candidate, Firebase UID, email, token과 credential은 field로 허용하지 않는다.

#### 4.1.2 Guest merge

```http
POST /internal/v1/owners/merge/events
Content-Type: application/json
```

Identity 기존 `UserMerged` v1 wire를 그대로 사용한다.

```json
{
  "eventId": "<lowercase UUID v4>",
  "schemaVersion": 1,
  "sourceUserId": "<lowercase canonical UUID>",
  "targetUserId": "<lowercase canonical UUID>",
  "occurredAt": "<UTC Instant>"
}
```

endpoint와 인증 principal이 `producer=identity`, `eventKind=USER_MERGED` 의미를 고정한다. 기존 wire에 `eventType`, `producer`, `consumerScopeId` 또는 reason을 추가하지 않는다.

#### 4.1.3 공통 decode 규칙

- body 상한은 16 KiB, 기존 Identity `UserMerged` producer 상한은 4 KiB를 유지한다.
- duplicate field, trailing token, unknown field와 string/number/boolean coercion을 거절한다.
- UUID는 lowercase canonical text여야 하고 `sourceUserId != targetUserId`여야 한다.
- timestamp는 UTC `Instant`로 parse하고 승인된 clock-skew 범위를 넘는 미래 event를 거절한다.
- schema/event/producer/reason/scope 불일치는 422 `UNSUPPORTED_CONTRACT`다.
- malformed body와 잘못된 UUID·revision·timestamp는 400 `INVALID_REQUEST`다.
- raw payload는 저장하거나 로그로 남기지 않는다.

검증 뒤 decoder는 다음 내부 command로 정규화한다.

```text
OwnerRebindCommand(
  eventId, eventKind, schemaVersion, occurredAt,
  sourceUserId, targetUserId,
  consumerScopeId?, sourceBindingRevision?, targetBindingRevision?,
  canonicalDigest
)
```

### 4.2 canonical digest와 inbox 멱등성

digest는 raw bytes가 아니라 strict validation을 통과한 semantic value의 canonical JSON bytes에 SHA-256을 적용한다.

- property order는 위 wire 순서로 고정한다.
- UUID는 lowercase canonical text로, timestamp는 UTC canonical `Instant` text로 정규화한다.
- number는 leading zero 없는 JSON integer로 직렬화한다.
- digest는 lowercase hex로 저장한다.

`owner_rebind_inbox`에는 다음만 저장한다.

```text
_id=eventId
eventId
producer=IDENTITY
eventKind=PHONE_REJOIN | USER_MERGED
schemaVersion
payloadDigest
disposition=APPLIED | NOOP | STALE | CONFLICT
affectedSubjectCount
receivedAt
processedAt
purgeAt=receivedAt+120d
```

inbox에는 raw payload, source/target userId, candidate, `subjectRefId`와 `trialClaimId`를 저장하지 않는다.

| 조건 | 결과 |
| --- | --- |
| 처음 보는 event | prerequisite와 owner state를 평가해 local Transaction 처리 |
| 같은 eventId·같은 digest | 기존 disposition 재현; 성공이면 204, 저장된 conflict면 같은 409 |
| 같은 eventId·다른 digest | 기존 record 유지, 409 `EVENT_ID_CONFLICT` |
| 낮거나 이미 대체된 lifecycle event | STALE commit 후 204 |
| current owner 또는 source→target 관계 모순 | 409 `OWNER_REBIND_CONFLICT` |

`DUPLICATE`는 새 stored disposition이 아니라 기존 commit을 재현한 처리 결과다. permanent owner conflict는 비식별 inbox disposition을 commit한 뒤 409를 반환한다. pending과 일시 Mongo 장애는 inbox 결과로 commit하지 않으며 publisher가 같은 eventId·payload를 재전송해야 한다.

### 4.3 phone lifecycle prerequisite

phone event는 current `trial_eligibility` projection과 retained Claim alias를 다시 검증한다.

1. source projection revision이 event revision보다 낮으면 503 pending이다.
2. target projection revision이 event revision보다 낮으면 503 pending이다.
3. source가 required revision과 같은데 `REVOKED`가 아니거나 target이 required revision과 같은데 `VERIFIED`가 아니면 producer contract conflict다.
4. projection이 required revision보다 높고 source가 다시 VERIFIED됐거나 target이 REVOKED됐다면 해당 승인 event는 superseded된 STALE로 204 commit한다.
5. revision이 같거나 높고 source `REVOKED`, target `VERIFIED`가 유지되면 target candidate가 source current link의 retained TrialClaim alias와 일치하는지 확인한다.
6. candidate가 Claim과 일치하지 않으면 권리를 추측해 옮기지 않고 409 `OWNER_REBIND_CONFLICT`다.

source account inactive 여부는 Identity가 `TrialOwnerRebindApproved`를 발행했다는 lifecycle 승인 의미로 신뢰하되, Billing은 eligibility와 candidate-to-Claim 관계를 독립적으로 재검증한다.

### 4.4 Guest merge prerequisite와 범위

`UserMerged`는 source가 보유한 active·unexpired retained Billing subject link를 모두 target으로 옮긴다.

- 한 event가 처리할 subject link는 최대 100개다.
- 100개를 넘으면 부분 적용하지 않고 409 `OWNER_REBIND_CONFLICT`와 invariant alert를 발생시킨다.
- 하나라도 active Reservation 또는 관련 PROCESSING command가 있으면 전체 event를 503 pending으로 두고 아무 link도 옮기지 않는다.
- multi-link 이전은 한 Mongo Transaction에서 all-or-nothing이다.
- source가 가진 retained link가 없으면 NOOP를 commit하고 새 Claim이나 Grant를 만들지 않는다.

현재 무료 Trial 모델에서는 정상적으로 한 건 이하가 예상된다. 100은 데이터 이상을 무제한 Transaction으로 처리하지 않기 위한 구현 상한이며, 향후 benefit 모델이 이 상한을 정당하게 넘기면 새 ADR에서 조정한다.

### 4.5 current owner 상태 전이

| current link owner | 수신 event | 처리 |
| --- | --- | --- |
| source | source→target | prerequisite 통과 후 APPLIED 후보 |
| target | exact same eventId/digest | DUPLICATE |
| target | 새 eventId의 같은 source→target | STALE 204 |
| third user | source→target | CONFLICT 409 |
| source | target→source reverse | 별도 승인 없으므로 CONFLICT 409 |

`A→B` 뒤 `B→C`는 각 event가 독립적으로 승인되고 수신 시 current owner가 정확히 source면 허용한다. `A→B` 뒤 `A→C`, cycle과 stale predecessor를 자동 chain으로 추측하지 않는다.

### 4.6 pending과 HTTP 응답

owner link를 변경하지 않고 retry하는 조건:

- 관련 `Reservation.status=RESERVED`
- 관련 active RESERVE `IdempotencyCommand.state=PROCESSING`
- lifecycle prerequisite projection revision 미도착
- 같은 aggregate의 다른 owner rebind Transaction과 일시적 충돌

| 상황 | HTTP | code |
| --- | --- | --- |
| APPLIED, NOOP, STALE local commit 또는 기존 성공 commit 확인 | 204 | body 없음 |
| malformed | 400 | `INVALID_REQUEST` |
| unknown contract | 422 | `UNSUPPORTED_CONTRACT` |
| eventId/digest conflict | 409 | `EVENT_ID_CONFLICT` |
| permanent owner/lifecycle conflict | 409 | `OWNER_REBIND_CONFLICT` |
| Reservation/PROCESSING/projection pending | 503 | `OWNER_REBIND_PENDING` |
| Mongo 일시 장애 | 503 | `BILLING_TEMPORARILY_UNAVAILABLE` |

`Retry-After`는 HTTP-date가 아닌 delta-seconds integer다.

- Reservation pending: `ceil(expiresAt-now)`를 1~300초로 clamp
- PROCESSING 또는 projection pending: 5초

425, 202와 409는 temporary pending에 사용하지 않는다. 409는 자동 재시도로 해결되지 않는 영구 conflict다.

### 4.7 Billing Mongo schema v4

#### 4.7.1 `billing_subject_links` 확장

```text
_id=subjectRefId
subjectRefId
trialClaimId
consumerScopeId
userId
active
createdAt
retentionExpiresAt
ownerVersion
ownerUpdatedAt
```

- 신규 link는 `ownerVersion=1`, `ownerUpdatedAt=createdAt`으로 저장한다.
- v3 legacy link의 missing `ownerVersion`은 reader에서 logical version 1로 해석한다.
- first rebind CAS는 `ownerVersion=1 OR ownerVersion missing`을 exact source/current 조건과 함께 사용하고 target + version 2 + updated time으로 원자 수렴시킨다.
- 이후 rebind는 읽은 explicit version과 exact match하고 `$inc: {ownerVersion: 1}`한다.
- `ownerVersion`을 wire에 노출하거나 Identity revision으로 대체하지 않는다.
- startup에서 invalid/non-positive/non-integer ownerVersion 또는 ownerUpdatedAt만 존재하는 모순 document를 발견하면 fail-fast한다.

CAS filter의 의미:

```text
_id=subjectRefId
+ active=true
+ retentionExpiresAt > now
+ userId=sourceUserId
+ expected ownerVersion match
```

CAS update count가 0이면 같은 Transaction에서 current link를 다시 읽어 duplicate/stale/conflict를 판정한다.

#### 4.7.2 `subject_owner_rebinds`

legacy-source fence가 필요한 subject 단위 record다.

```text
_id=<eventId:subjectRefId derived internal id>
eventId
eventKind
subjectRefId
trialClaimId
sourceUserId?          # cleanup 뒤 unset
attemptGroupId?        # pre-rebind active group이 있을 때만
sessionId?             # pre-rebind active session이 있을 때만
ownerVersionFrom
ownerVersionTo
appliedAt
legacyFenceExpiresAt
cleanupDueAt
cleanupState=WAITING_TERMINAL | DUE | CLEANED
sourceUnlinkedAt?
purgeAt
```

`targetUserId`는 저장하지 않는다. current target은 `BillingSubjectLink`에서 확인하며 duplicate 멱등성은 inbox가 담당한다.

- active pre-rebind Session이 없으면 subject rebind record 자체를 만들지 않고 inbox의 처리 건수만 남긴다.
- active Session이 있으면 exact group/session과 source를 저장하고 `WAITING_TERMINAL`로 둔다.
- `legacyFenceExpiresAt = min(appliedAt+120일, TrialClaim.retentionExpiresAt)`다.
- 관련 Session terminal이 먼저 오면 terminal time부터 legacy authorization을 논리 종료하고 `cleanupDueAt`을 그 시각으로 바꾼다.
- `purgeAt`은 logical cleanup upper bound 뒤 24시간으로 두는 TTL safety net이다. 명시적 worker가 먼저 source link를 제거하고 record를 삭제한다.

#### 4.7.3 collection과 index

| collection | index name | key | option |
| --- | --- | --- | --- |
| `owner_rebind_inbox` | `ux_owner_rebind_inbox_event_id` | `{eventId: 1}` | unique |
| `owner_rebind_inbox` | `ttl_owner_rebind_inbox_purge_at` | `{purgeAt: 1}` | TTL 0 |
| `subject_owner_rebinds` | `ux_owner_rebind_event_subject` | `{eventId: 1, subjectRefId: 1}` | unique |
| `subject_owner_rebinds` | `ix_owner_rebind_subject_fence` | `{subjectRefId: 1, attemptGroupId: 1, sessionId: 1, legacyFenceExpiresAt: 1}` | non-unique |
| `subject_owner_rebinds` | `ix_owner_rebind_cleanup_due` | `{cleanupState: 1, cleanupDueAt: 1}` | non-unique |
| `subject_owner_rebinds` | `ttl_owner_rebind_purge_at` | `{purgeAt: 1}` | TTL 0 safety net |

기존 `billing_subject_links`의 `ux_subject_link_claim`, `ix_subject_link_user_active`, `ix_subject_link_expiry`는 유지한다. owner CAS는 `_id` 조회를 사용하므로 별도 ownerVersion index를 추가하지 않는다.

initializer는 schema version, collection, index key order, name, unique/TTL option과 partial filter를 exact 비교한다. 같은 이름의 불일치 index나 legacy data를 실행 중 drop/recreate 또는 bulk update하지 않고 startup fail-fast한다.

#### 4.7.4 v3→v4 배포 순서

1. v4 reader가 missing ownerVersion을 logical 1로 읽는 code와 preflight를 배포한다.
2. v4 collection/index migration을 통제된 운영 단계에서 생성한다.
3. 신규 link writer가 version 1을 기록하게 한다.
4. owner consumer와 worker를 feature flag off 상태로 배포한다.
5. staging fixture에서 missing-version CAS가 version 2로 수렴하는지 확인한다.
6. consumer readiness 뒤에만 publisher를 켠다.

historical owner event backfill과 기존 link의 bulk version rewrite는 TMI-120에 포함하지 않는다.

### 4.8 Transaction과 whole-unit retry

한 event 처리 unit은 다음 순서를 따른다.

```text
strict decode + canonical digest (Transaction 밖, raw payload 비저장)
→ 새 Mongo Transaction 시작
→ inbox eventId/digest 확인
→ lifecycle prerequisite 확인
→ source active/unexpired link와 current owner 확인
→ 관련 Reservation/PROCESSING 확인
→ exact owner/version CAS
→ pre-rebind active group/session fence 저장
→ inbox disposition 저장
→ commit 확인
→ 204 반환
```

Transaction 안에서 새 Claim/Grant/ledger/Reservation을 만들거나 AttemptGroup 상태를 바꾸지 않는다.

`DuplicateKeyException`, `TransientTransactionError`, `UnknownTransactionCommitResult`는 abort된 Transaction object를 재사용하지 않는다. bounded outer retry가 새 Transaction으로 eventId/inbox와 current owner부터 whole unit을 다시 실행한다. 최종 응답 전에 local commit 또는 같은 digest의 기존 commit을 확인한다.

### 4.9 legacy-source fence와 AttemptGroup consumer 연결

AttemptGroup event의 일반 authorization은 current `BillingSubjectLink.userId` exact match다. mismatch일 때만 active fence를 조회한다.

legacy source event 허용 조건:

```text
authenticated caller = LEARNING_CORE
+ event.userId = fence.sourceUserId
+ event.attemptGroupId/sessionId = fence exact tuple
+ loaded group/session의 subjectRefId = fence.subjectRefId
+ Session.proposedAt 또는 created evidence < rebind.appliedAt
+ now < legacyFenceExpiresAt
+ event가 기존 상태 전이표의 전진
```

다음은 fence가 있어도 거절한다.

- reserve, status, confirm/cancel authorization을 source로 수행
- REPLACEMENT 생성
- 다른 group/session event
- 상태 rollback 또는 `COMPLETED` 재개방
- hard cap 이후 source event
- source userId를 target alias처럼 사용

target userId의 current-owner event는 기존 일반 경로로 처리한다. source와 target에서 같은 semantic status가 중복 도착하면 기존 AttemptGroup event inbox, Session fencing과 상태 전이표로 APPLIED/DUPLICATE/STALE/CONFLICT를 판정한다.

### 4.10 cleanup worker

legacy authorization은 DB 물리 cleanup과 무관하게 `legacyFenceExpiresAt` 또는 Session terminal 시각에 즉시 논리 종료한다.

worker는 최대 1시간 간격으로 due record를 스캔한다.

1. related Session terminal 여부 또는 hard cap 도달을 확인한다.
2. `sourceUserId`를 unset하고 group/session user 연결을 제거한다.
3. `sourceUnlinkedAt`, `cleanupState=CLEANED`를 기록한 뒤 더 이상 필요한 참조가 없으면 subject record를 삭제한다.
4. terminal 또는 hard cap 뒤 24시간을 넘긴 record는 운영 경보를 발생시킨다.

TTL은 worker 실패 시 safety net일 뿐 authorization이나 24시간 SLA를 보장하는 수단으로 사용하지 않는다. cleanup log에는 처리 건수, 성공/실패, 지연 bucket만 기록하고 userId, subject, Claim, group, session을 기록하지 않는다.

`owner_rebind_inbox`의 eventId/digest/outcome은 120일 멱등성 기록으로 유지된다. 이것은 TrialClaim 3년 보존이나 legacy source user 연결 보존과 별개다.

### 4.11 Identity durable fan-out migration

Identity는 immutable event core 하나와 consumer별 delivery를 분리한다.

```text
owner event core
├─ delivery(eventId, BILLING)
└─ delivery(eventId, LEARNING_CORE)
```

- consumer allowlist는 `BILLING`, `LEARNING_CORE`다.
- event core와 delivery 두 건은 Identity lifecycle Mongo Transaction에서 원자 저장한다.
- `(eventId, consumer)`는 unique다.
- delivery별 status, attemptCount, nextAttemptAt, lease owner/expiry, published/dead-letter time, cleanup time과 feature flag를 독립 관리한다.
- global `PUBLISHED`, consumer별 full event payload 복제와 동기 순차 POST를 사용하지 않는다.

기존 `user_merged_outbox`는 transition 동안 immutable `UserMerged` event core로 읽는다. 기존 embedded delivery status는 Learning Core legacy publisher가 reader-first로 호환하되, 신규 event부터 별도 `BILLING`/`LEARNING_CORE` delivery 두 건을 생성한다. Billing으로 historical merge event를 자동 backfill하지 않는다.

cutover preflight에서 transition 규칙으로 안전하게 분류되지 않는 legacy row 또는 동일 event의 모순 delivery를 발견하면 publisher를 켜지 않고 별도 migration 승인을 받는다. phone rejoin은 별도 event core/delivery schema를 사용하되, 두 lifecycle을 성급한 generic domain 하나로 합치지 않는다.

### 4.12 Learning Core owner consumer 계약

Identity는 consumer별로 다음 route에 전달한다.

| lifecycle | Learning Core route |
| --- | --- |
| Guest merge | `POST /internal/v1/owners/merge/events` |
| phone 재가입 | `POST /internal/v1/owners/trial/rebind/events` |

Learning Core는 lifecycle별 strict decoder 뒤 한 local Transaction에서 event inbox, source actor deny marker와 시험/Session current ownership migration을 처리한다. 기존 Session·AttemptGroup 정체성과 결과는 새로 만들지 않는다.

이미 source userId로 저장된 status outbox는 Billing의 bounded fence 안에서 수렴시킬 수 있다. Learning Core ownership migration 뒤 필요하면 target userId와 새 eventId로 current terminal status를 재발행하며, old source eventId를 payload만 바꿔 재사용하지 않는다.

두 route의 실제 구현·저장 schema와 Learning Core Jira는 Learning Core 저장소가 소유한다. 이 ADR은 cross-service wire와 activation gate만 고정한다.

### 4.13 Lattice IAM

exact action은 `vpc-lattice-svcs:Invoke`다.

- caller identity policy는 환경별 exact Billing 또는 Learning Core Lattice service ARN만 허용한다.
- Billing service auth policy는 같은 환경 exact Identity application task role Principal, POST와 두 승인 route만 허용한다.
- Learning Core service auth policy도 same principal과 위 owner route만 허용한다.
- `Action:*`, `Resource:*`, wildcard/account-root principal, production↔staging 교차 ARN을 금지한다.
- 불필요한 `vpc-lattice-svcs:InvokeWithServiceNetworkContext`를 추가하지 않는다.
- task execution role과 GitHub OIDC deploy role에는 application invoke 권한을 주지 않는다.
- actual ARN, service ID와 DNS는 환경 inventory/Secret에서 주입하고 코드·문서 fixture에 하드코딩하지 않는다.

Billing route의 구체 auth-policy 예시는 `ADR-002`를 따른다.

### 4.14 관측성과 개인정보

Billing HTTP server span 아래 `owner_rebind_consume` INTERNAL span을 둔다. 범위는 strict decode 이후 command 처리부터 Mongo commit 확인까지이며 정상·예외 모두 종료한다.

구조화 completion log는 다음 저카디널리티/운영 field만 사용한다.

```text
service=billing
traceId
eventId
eventKind
outcome
errorCode?
durationMs
```

metric 예:

- `billing.owner.rebind.events{eventKind,outcome}`
- `billing.owner.rebind.pending{reason}`
- `billing.owner.rebind.cleanup{outcome}`
- cleanup overdue count/oldest-age gauge

span attribute, metric tag와 log에 다음을 넣지 않는다.

- source/target userId
- candidate, phone 또는 scope 원문
- subjectRefId, trialClaimId, attemptGroupId, sessionId
- payload, digest
- Authorization, SigV4 header, credential

W3C `traceparent` inbound 연결은 유지하고 baggage는 전파하지 않는다. traceId, eventId, service와 duration으로 서비스 간 처리 경로를 추적한다.

## 5. 주요 위험과 미확인 사항

### 5.1 Billing schema v4 consumer 구현 상태

TMI-120 브랜치에서 `BillingMongoIndexInitializer.SCHEMA_VERSION=4`, owner rebind collection/index preflight와 `BillingSubjectLink.ownerVersion/ownerUpdatedAt` reader-first CAS를 구현했다. legacy missing version은 logical 1로 읽고 첫 owner 이전에서 explicit version 2로 수렴한다. 이 코드 구현만으로 운영 Mongo index migration이나 consumer 활성화가 실행되는 것은 아니며, 승인된 별도 production index 단계와 feature flag off 선배포가 필요하다.

### 5.2 Identity 기존 publisher transport와 storage migration

현재 Identity `UserMergedOutbox`는 event core와 단일 delivery 상태가 같은 document에 결합돼 있고 publisher는 단일 endpoint 구조다. reader-first delivery 분리는 Identity 별도 코드/Jira가 필요하며 Billing 구현에서 Identity collection을 직접 수정하지 않는다.

### 5.3 Learning Core owner consumer는 별도 구현이다

Billing fence는 늦은 status event 유실을 제한적으로 막을 뿐 Learning Core 시험 ownership을 target으로 바꾸지 않는다. Learning Core consumer 없이 production flag를 켜면 target 재응시와 source deny가 완성되지 않는다.

### 5.4 hard cap 이후 자동 복구 route가 없다

TMI-120에는 privileged HTTP repair route를 만들지 않는다. hard cap 이후 late source event는 alert와 운영 review로 보낸다.

운영 절차:

1. Identity event core와 두 delivery 상태 확인
2. Learning Core owner inbox/outbox와 current ownership 확인
3. Learning Core migration이 완료됐다면 target owner로 새 eventId terminal status 재발행
4. old source event replay 또는 Billing direct DB update로 우회하지 않음
5. 위 방식으로 수렴할 수 없으면 별도 repair ADR, 운영 role과 audit 계약 승인 후 mutation 수행

### 5.5 historical backfill은 제외다

기능 활성화 전에 발생한 Guest merge를 Billing에 자동 replay하지 않는다. 필요한 실제 대상과 개인정보·중복 권리 영향을 확인한 별도 migration 승인이 없으면 preflight에서 중단한다.

## 6. 현재 작업과 직접 관련된 구현 순서

1. schema v4 reader/preflight와 exact index initializer
2. owner rebind domain entity, inbox/fence repository와 CAS
3. lifecycle별 strict decoder와 canonical digest
4. phone/Guest prerequisite validator와 common owner transfer service
5. Reservation/PROCESSING pending과 HTTP error mapping
6. AttemptGroup legacy-source fence 연동
7. cleanup worker, metric, trace와 privacy-safe log
8. unit·replica-set Testcontainers·security/transport contract test
9. ADR-001·통합 계약·운영 문서 갱신
10. Identity/Learning Core 별도 consumer 작업과 staging E2E

TMI-120은 1~9의 Billing 범위만 구현한다. Identity, Learning Core와 실제 AWS resource 변경은 이 저장소의 구현 범위가 아니다.

## 7. 상세 부록

### A. 필수 테스트 매트릭스

#### Decoder/HTTP

- 두 lifecycle 정상 payload
- duplicate/unknown/trailing field, trailing token, scalar coercion
- wrong event/schema/producer/reason/scope
- UUID casing, source==target, invalid revision/time, oversize
- same eventId same/different digest
- 204/400/409/422/503와 exact Retry-After

#### Domain/Transaction

- source current owner→target APPLIED
- exact duplicate와 new-event semantic STALE
- no retained source NOOP
- third owner, reverse, cycle와 conflicting target
- A→B→C chain과 A→C stale predecessor 차단
- phone lower revision pending, exact mismatch conflict, superseded stale
- target candidate-to-Claim mismatch conflict
- Guest multi-link all-or-nothing과 100건 상한
- active Reservation/PROCESSING pending 후 terminal retry
- owner rebind와 reserve/confirm/expiry concurrency
- duplicate-key, transient Transaction과 unknown commit result 수렴

#### Legacy fence/cleanup

- exact pre-rebind Session source status 전진
- 다른 group/session, rollback, reserve/replacement와 expired fence 거절
- source/target 동일 semantic status 중복 수렴
- terminal 즉시 logical fence 종료
- terminal/hard cap 뒤 source unset과 24시간 overdue alert
- TTL worker 장애 safety net, identifier 비로깅

#### Schema/security

- missing ownerVersion logical 1→CAS version 2
- malformed legacy version/index option fail-fast
- unsigned, wrong role, wrong route, cross-environment와 direct bypass 거절
- traceId 연결, inner span name/duration, baggage 미전파와 민감정보 미포함

### B. staging E2E 순서 역전 시나리오

1. Billing이 먼저 owner rebind를 적용하고 source status event가 나중에 도착
2. Learning Core가 먼저 ownership을 이전하고 Billing owner event가 나중에 도착
3. 두 consumer 중 하나만 503 pending 후 retry
4. Billing 204 응답 유실 뒤 same event replay
5. rebind와 5분 Reservation expiry 동시 실행
6. source terminal event와 target 재발행 event 순서 역전
7. chain A→B→C delivery 순서 역전과 stale predecessor
8. hard cap 경계의 source event 거절과 운영 alert

각 시나리오는 새 Claim·Grant·consumption이 생기지 않고 current owner가 하나이며 ledger 합계와 AttemptGroup 상태가 단방향으로 수렴하는지 확인한다.

### C. 배포와 rollback

배포:

```text
Billing reader/schema/index, flag off
→ Learning Core consumer, flag off
→ Identity delivery writer/publisher, flag off
→ staging IAM negative + order-reversal E2E
→ Billing/LC consumer flag
→ Identity producer/publisher canary
→ pending/dead-letter/cleanup age 관찰 후 확대
```

rollback:

- producer/publisher를 먼저 꺼서 신규 event 생성을 중단한다.
- consumer는 이미 저장된 duplicate/retry를 수렴할 수 있도록 유지한다.
- 이미 적용된 owner mapping을 DB 수동 update로 source에 되돌리지 않는다.
- 보상 이전이 필요하면 reverse event를 임의 생성하지 않고 별도 승인된 lifecycle/repair 절차를 사용한다.

### D. 기준 문서 우선순위

1. 제품 정책·보존 결정: `docs/codex/CONTRACT_DECISIONS.md`
2. owner rebind wire·Mongo·상태·cleanup: 이 ADR
3. Lattice·SigV4·환경 격리: `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`
4. 기존 무료 Trial API·Mongo 불변식: `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`
5. 구현 순서와 범위: `docs/plans/PLAN-006-retained-trial-owner-rebind.md`

상위 통합 안내서와 이 ADR이 충돌하면 owner rebind 세부사항은 이 ADR을 따른다. 승인된 wire나 보존 정책을 바꾸려면 producer·consumer와 migration 영향을 함께 검토하는 후속 ADR이 필요하다.
