# ADR-001: 무료 모의고사 내부 API와 MongoDB 계약

- 상태: 승인된 제품 계약을 구현하기 위한 기술 기준
- 작성일: 2026-08-26
- 대상 릴리스: 결제 제외 `FREE_EXAM_ONCE` MVP
- Jira: 없음
- 관련 기준: `docs/codex/CONTRACT_DECISIONS.md`

## 1. 결정 요약

이 ADR은 다음을 확정한다.

- Billing 내부 API는 `/internal/v1` 아래에 두며 앱이나 사용자 Access Token에 노출하지 않는다.
- Identity는 기존 `PhoneEligibilityBindingVerified`/`Revoked` schema v1을 push한다.
- Learning Core는 `reserve → ExamSession durable commit → confirm`을 같은 UUID v4 operation ID로 수행한다.
- Learning Core의 기존 `examId`를 wire의 `sessionId`로 사용한다. `sessionId`와 `mockExamId`는 UUID라고 가정하지 않는 opaque token이다.
- `Idempotency-Key` header가 operation ID의 유일한 wire source다. Request Body에 중복 `operationId`를 받지 않는다.
- MongoDB는 candidate alias, 삭제 가능한 subject link와 immutable audit core를 분리한다.
- candidate당 한 번, 사용자당 하나의 non-terminal AttemptGroup, 하나의 active Session과 하나의 active creation command는 unique/partial unique index와 Transaction으로 강제한다.
- `RESERVED` 만료는 business transition이며 Reservation·ledger audit 문서를 TTL로 삭제하지 않는다.
- TrialClaim은 `claimedAt + 3년`에 logical expiry되고 daily purge가 24시간 안에 candidate/user 연결을 제거한다. 재해복구 backup은 최대 35일이다.

이 ADR은 C3-D Lattice/SigV4의 리소스 ARN과 security group을 정하지 않는다. 해당 내용은 `ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`에서 다룬다.

## 2. 서비스와 식별자 경계

| 값 | 생성·소유 | wire 형식 | 용도 |
| --- | --- | --- | --- |
| `userId` | Identity | lowercase canonical UUID | Learning Core가 사용자 JWT `sub`를 검증한 뒤 내부 요청에 전달 |
| `operationId` | 앱 | lowercase UUID v4 | 한 Session 생성 동작과 transport retry 식별 |
| `sessionId` | Learning Core | 1~128자 opaque token | 현재 Learning Core의 `examId`; Session commit 증거 |
| `mockExamId` | Learning Core | 1~128자 opaque token | AttemptGroup 동안 고정되는 문제지 식별자 |
| `reservationId` | Billing | lowercase UUID v4 | Billing hold/confirm/cancel aggregate |
| `attemptGroupId` | Billing | lowercase UUID v4 | 한 consumption과 replacement Session 묶음 |
| `trialClaimId` | Billing | lowercase UUID v4 | 3년 dedupe와 무료 grant source |
| `subjectRefId` | Billing | lowercase UUID v4 | 삭제 가능한 user mapping과 immutable audit core 사이의 간접 참조 |
| `eventId` | event producer | lowercase UUID v4 | at-least-once event 멱등성 |

`userId`는 public client가 Billing에 보내는 값이 아니다. Billing은 Lattice에서 인증된 Learning Core route에서만 Learning Core가 JWT `sub`로 확정한 `userId`를 받는다. Identity event의 `userId`는 인증된 Identity route에서만 받는다. 다른 principal, public path, query parameter 또는 임의 identity header의 `userId`는 신뢰하지 않는다.

모든 시각은 RFC 3339 UTC `Z`와 millisecond precision으로 주고받고 DB에는 BSON Date/`Instant`로 저장한다. JSON duplicate field, 숫자 coercion과 잘못된 UUID casing은 validation 단계에서 거절한다.

## 3. 공통 HTTP 계약

### 3.1 transport

- `Content-Type: application/json`
- request/response body 상한: 16 KiB
- redirect: 금지
- timeout/connection reset은 commit 여부 불명을 뜻하므로 같은 key/event로 재시도
- internal API는 `BaseResponse` wrapper 없이 아래 DTO를 그대로 사용
- request/response 전체 payload, SigV4 Authorization, candidate와 userId를 로그에 남기지 않음
- `/internal/v1`의 breaking change는 `/internal/v2`로 올림
- v1 optional field 추가는 reader-first 배포 뒤에만 허용

### 3.2 command header

`reserve`, `confirm`, `cancel`은 다음 header가 필수다.

```http
Idempotency-Key: 018f6f36-2f42-4bf5-8c17-0be35de4872c
```

- 정확한 lowercase UUID v4만 허용한다.
- 이 값이 `operationId`다.
- confirm/cancel header는 해당 Reservation을 만든 reserve operation ID와 같아야 한다.
- status는 body의 `operationId`로 조회하며 새로운 command를 만들지 않는다.

### 3.3 error envelope

```json
{
  "code": "COMMAND_PROCESSING",
  "message": "The operation is still processing.",
  "retryable": true,
  "correlationId": "018f6f36-2f42-4bf5-8c17-0be35de4872c"
}
```

| HTTP | `code` | 의미 | 동일 key 자동 재시도 |
| --- | --- | --- | --- |
| 400 | `INVALID_REQUEST` | DTO 형식·필수값 오류 | 아니오 |
| 400 | `INVALID_IDEMPOTENCY_KEY` | UUID v4 header 오류 | 아니오 |
| 401/403 | edge/security response | unsigned, wrong role, route 권한 없음 | 아니오; 보안/설정 수정 |
| 402 | `ENTITLEMENT_INSUFFICIENT` | 유효한 무료 Claim/grant를 만들거나 사용할 수 없음 | 아니오 |
| 404 | `OPERATION_NOT_FOUND` | 해당 user/operation 기록 없음 | reconciliation 판단 |
| 409 | `COMMAND_PROCESSING` | 동일 Reservation command 처리가 아직 완료되지 않음 | 예 |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | 같은 command key를 다른 canonical payload에 재사용 | 아니오 |
| 409 | `RESERVATION_STATE_CONFLICT` | confirm/cancel의 허용되지 않은 상태 전이 | 상태 조회 후 판단 |
| 409 | `EVENT_ID_CONFLICT` | 같은 eventId의 canonical payload가 다름 | 아니오; 격리 |
| 409 | `EVENT_TARGET_CONFLICT` | 존재하는 group/session/subject 관계가 event와 충돌 | 아니오; 격리·조사 |
| 422 | `UNSUPPORTED_CONTRACT` | 알 수 없는 schema/event/enum version | 아니오; reader 배포 |
| 429 | `RATE_LIMITED` | rate limit | 예 |
| 503 | `ATTEMPT_PROJECTION_NOT_READY` | 정상 순서상 group/session projection이 아직 보이지 않음 | 예; `Retry-After: 5` |
| 503 | `BILLING_TEMPORARILY_UNAVAILABLE` | DB/Transaction/의존 인프라 일시 장애 | 예 |

409 processing, 429와 503은 정수 초 단위 `Retry-After` header를 준다. 오류에는 balance, candidate, keyVersion, reservation/payment identifier, provider 원문과 stack trace를 넣지 않는다.

## 4. Identity → Billing trial eligibility event API

### 4.1 endpoint

```http
POST /internal/v1/eligibility/trial/events
```

호출 principal은 Identity ECS task role만 허용한다. 기존 Identity ADR의 Bearer workload JWT transport 문구는 C3-D 승인으로 대체되며 wire schema와 retry 의미는 그대로 재사용한다.

### 4.2 verified request

```json
{
  "eventId": "018f6f36-2f42-4bf5-8c17-0be35de4872c",
  "eventType": "PhoneEligibilityBindingVerified",
  "schemaVersion": 1,
  "producer": "identity",
  "occurredAt": "2026-08-14T05:00:00.000Z",
  "consumerScopeId": "opaque-scope-v1",
  "userId": "e8b37a41-bae6-47f1-a770-052e6c5786d4",
  "verifiedAt": "2026-08-14T04:59:58.000Z",
  "bindingRevision": 1,
  "fingerprintCandidates": [
    {
      "keyVersion": "v2",
      "value": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    },
    {
      "keyVersion": "v1",
      "value": "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
    }
  ]
}
```

### 4.3 revoked request

```json
{
  "eventId": "13702e7d-aa52-44dc-848b-59af50a296c5",
  "eventType": "PhoneEligibilityBindingRevoked",
  "schemaVersion": 1,
  "producer": "identity",
  "occurredAt": "2026-08-14T06:00:00.000Z",
  "consumerScopeId": "opaque-scope-v1",
  "userId": "e8b37a41-bae6-47f1-a770-052e6c5786d4",
  "revokedAt": "2026-08-14T06:00:00.000Z",
  "bindingRevision": 2
}
```

verified/revoked의 field constraint, candidate canonical sort, revision high-water와 payload digest는 Identity ADR-002 schema v1을 그대로 따른다.

### 4.4 response

- 처음 적용, duplicate same payload, stale lower revision: `204 No Content`
- same eventId/different digest 또는 same user/scope/revision의 다른 event: `409 EVENT_ID_CONFLICT`
- malformed: `400 INVALID_REQUEST`
- unknown event/schema: `422 UNSUPPORTED_CONTRACT`
- Transaction 일시 장애: `503 BILLING_TEMPORARILY_UNAVAILABLE`

Billing은 local Transaction commit 뒤에만 204를 반환한다.

## 5. Learning Core → Billing Reservation API

### 5.1 reserve

```http
POST /internal/v1/reservations
Idempotency-Key: <operationId>
```

Learning Core는 DB insert 전 `sessionId`와 `mockExamId`를 먼저 확정한다. reserve request의 `sessionId`는 아직 durable하지 않은 proposed ID이며 confirm에서 같은 ID의 commit을 증명한다.

```json
{
  "userId": "e8b37a41-bae6-47f1-a770-052e6c5786d4",
  "sessionId": "ex_a1b2c3d4e5_0826_1530",
  "mockExamId": "mock-exam-01"
}
```

canonical payload hash input은 다음 필드와 순서다.

```text
apiVersion=v1
callerService=LEARNING_CORE
commandType=RESERVE
userId=<canonical UUID>
sessionId=<exact opaque token>
mockExamId=<exact opaque token>
```

동일 `(callerService, userId, operationId, commandType)`와 같은 hash는 기존 결과를 반환한다. hash가 다르면 409 `IDEMPOTENCY_KEY_CONFLICT`다.

성공 response는 최초와 replay 모두 `200 OK`다.

```json
{
  "operationId": "018f6f36-2f42-4bf5-8c17-0be35de4872c",
  "reservationId": "36c2356c-29d1-443f-b8f1-298345ee4e89",
  "reservationKind": "INITIAL",
  "reservationStatus": "RESERVED",
  "attemptGroupId": "be07ae1d-f877-4ae4-82df-c5f442e9bb8e",
  "sessionId": "ex_a1b2c3d4e5_0826_1530",
  "mockExamId": "mock-exam-01",
  "expiresAt": "2026-08-26T06:35:00.000Z"
}
```

`reservationKind`는 Billing이 결정한다.

- 유효한 non-terminal AttemptGroup이 없으면 `INITIAL`; 같은 Transaction에서 TrialClaim/grant가 필요하면 생성하고 1 unit을 hold한다.
- `OPEN` 또는 `RETAKE_AVAILABLE` group이 있으면 `REPLACEMENT`; 기존 consumption을 재사용하며 새 grant allocation을 만들지 않는다.
- `GRADING` group이면 409 `COMMAND_PROCESSING`으로 새 Session을 잠시 차단한다.
- replacement request의 `mockExamId`가 group의 고정 값과 다르면 409 `RESERVATION_STATE_CONFLICT`다.

`attemptGroupId`는 INITIAL reserve에서도 미리 생성하지만 group의 durable `OPEN` 전이는 confirm Transaction에서만 한다.

### 5.2 confirm

```http
POST /internal/v1/reservations/{reservationId}/confirm
Idempotency-Key: <same operationId>
```

```json
{
  "userId": "e8b37a41-bae6-47f1-a770-052e6c5786d4",
  "sessionId": "ex_a1b2c3d4e5_0826_1530",
  "sessionCommittedAt": "2026-08-26T06:30:01.200Z"
}
```

- stored user, operation, session과 모두 일치해야 한다.
- INITIAL은 allocation을 consume하고 ledger event와 AttemptGroup `OPEN`을 만든다.
- REPLACEMENT는 추가 consume 없이 기존 group을 `OPEN`으로 두고 새 active Session projection을 연결한다.
- 같은 payload의 재호출은 기존 confirmed 결과를 반환한다.
- 이미 CANCELED/EXPIRED면 409이고 자동 repair-confirm하지 않는다.
- 이미 CONFIRMED인데 sessionId가 다르면 409다.

```json
{
  "operationId": "018f6f36-2f42-4bf5-8c17-0be35de4872c",
  "reservationId": "36c2356c-29d1-443f-b8f1-298345ee4e89",
  "reservationStatus": "CONFIRMED",
  "attemptGroupId": "be07ae1d-f877-4ae4-82df-c5f442e9bb8e",
  "attemptGroupStatus": "OPEN",
  "sessionId": "ex_a1b2c3d4e5_0826_1530",
  "confirmedAt": "2026-08-26T06:30:01.300Z"
}
```

### 5.3 cancel

```http
POST /internal/v1/reservations/{reservationId}/cancel
Idempotency-Key: <same operationId>
```

```json
{
  "userId": "e8b37a41-bae6-47f1-a770-052e6c5786d4",
  "reason": "SESSION_COMMIT_FAILED"
}
```

v1 reason은 `SESSION_COMMIT_FAILED`, `CALLER_ABORTED`만 허용한다.

- INITIAL cancel은 held allocation을 원 grant에 복원하고 release ledger를 남긴다.
- REPLACEMENT cancel은 consumption을 바꾸지 않는다.
- TrialClaim은 삭제하거나 retention clock을 갱신하지 않는다.
- 같은 cancel 재호출은 기존 CANCELED 결과를 반환한다.
- CONFIRMED는 cancel할 수 없으며 409다.

```json
{
  "operationId": "018f6f36-2f42-4bf5-8c17-0be35de4872c",
  "reservationId": "36c2356c-29d1-443f-b8f1-298345ee4e89",
  "reservationStatus": "CANCELED",
  "canceledAt": "2026-08-26T06:30:02.000Z"
}
```

### 5.4 operation status

```http
POST /internal/v1/reservations/status
```

status는 읽기 요청이지만 userId가 URL/access log에 남는 것을 피하기 위해 body가 있는 POST를 사용한다. command/ledger/idempotency record를 새로 만들지 않는다.

```json
{
  "userId": "e8b37a41-bae6-47f1-a770-052e6c5786d4",
  "operationId": "018f6f36-2f42-4bf5-8c17-0be35de4872c"
}
```

```json
{
  "operationId": "018f6f36-2f42-4bf5-8c17-0be35de4872c",
  "reservationId": "36c2356c-29d1-443f-b8f1-298345ee4e89",
  "reservationKind": "INITIAL",
  "reservationStatus": "CONFIRMED",
  "attemptGroupId": "be07ae1d-f877-4ae4-82df-c5f442e9bb8e",
  "attemptGroupStatus": "OPEN",
  "sessionId": "ex_a1b2c3d4e5_0826_1530",
  "mockExamId": "mock-exam-01",
  "expiresAt": "2026-08-26T06:35:00.000Z",
  "terminalAt": "2026-08-26T06:30:01.300Z"
}
```

존재하지 않는 user/operation 조합은 `404 OPERATION_NOT_FOUND`다. 이 오류는 앱으로 그대로 전달하지 않고 Learning Core reconciliation 판단에만 사용한다.

## 6. AttemptGroup 상태 event

Learning Core가 소유한 Session·채점 결과를 Billing이 직접 조회하거나 저장하지 않는다. Learning Core outbox는 Billing entitlement projection에 필요한 최소 상태만 push한다. 이 route는 C3-D의 Learning Core `status` 권한에 포함한다.

```http
POST /internal/v1/attempt-group-events
```

```json
{
  "eventId": "8d19e341-ec9c-4efd-b4c0-b1f3ad4c4442",
  "eventType": "AttemptGroupStatusChanged",
  "schemaVersion": 1,
  "producer": "learning-core",
  "occurredAt": "2026-08-26T07:00:00.000Z",
  "userId": "e8b37a41-bae6-47f1-a770-052e6c5786d4",
  "attemptGroupId": "be07ae1d-f877-4ae4-82df-c5f442e9bb8e",
  "sessionId": "ex_a1b2c3d4e5_0826_1530",
  "targetStatus": "COMPLETED",
  "evidence": {
    "requiredFeedbackQueryable": true,
    "validScoreQueryable": true,
    "summaryQueryable": true,
    "evidenceVersion": 1
  }
}
```

허용 target은 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE`이다.

- `COMPLETED`는 evidence boolean 세 개가 모두 true여야 한다.
- `RETAKE_AVAILABLE`에는 evidence 대신 `REQUIRED_RESULTS_UNAVAILABLE`, `SUMMARY_UNAVAILABLE`, `GRADING_DEADLINE_EXCEEDED`, `RESULT_INTEGRITY_VIOLATION` 중 하나의 `failureCode`만 허용한다. exception message, AI/provider 이름·code·원문과 자유 문자열은 금지한다.
- Identity의 `bindingRevision`에 해당하는 sequence가 없는 at-least-once event이므로 eventId/digest뿐 아니라 현재 group 상태와 허용 transition을 함께 검사한다.
- same eventId/same digest는 204 no-op, different digest는 409다.
- abandoned/stale Session event는 현재 active session fencing에 실패하면 204 stale no-op로 기록한다.
- event `sessionId`는 group `activeSessionId`, AttemptSession의 group·subject와 `ACTIVE` state를 모두 만족해야 한다. RETAKE_AVAILABLE에서 해당 Session을 FAILED terminal로 닫고 REPLACEMENT confirm이 새 Session을 ACTIVE로 만들기 전까지 이전 Session event는 stale다.
- `OPEN→GRADING/COMPLETED/RETAKE_AVAILABLE`, `GRADING→COMPLETED/RETAKE_AVAILABLE`만 전진으로 허용한다. 동일 target은 no-op이고 COMPLETED 이후와 terminal Session의 역행 event는 stale다.
- Learning Core는 같은 active Session에 COMPLETED와 RETAKE_AVAILABLE을 모두 생성하지 않는다. local terminal 결과와 outbox event는 같은 Mongo Transaction/CAS에 저장하고 Session당 terminal event 하나를 unique하게 강제한다.
- 정상 생성 순서상 group/session projection이 아직 없으면 503 `ATTEMPT_PROJECTION_NOT_READY`와 `Retry-After: 5`, old/abandoned Session이면 204 stale, 존재하는 관계가 다르면 409 `EVENT_TARGET_CONFLICT`다. consumer는 missing target을 생성하거나 임의 연결하지 않는다.

### 6.1 outbox delivery와 trace

- Learning Core retryable PENDING outbox는 TTL로 삭제하지 않는다. network/408/425/429/5xx는 `5초→15초→1분→5분→15분` 뒤 최대 15분+jitter로 재시도한다.
- 400/409/422는 DEAD_LETTER, 401/403은 publish 정지와 긴급 경보다. DELIVERED는 30일, DEAD_LETTER는 90일 보존하고 장기 PENDING을 경보한다.
- retry와 수동 replay는 같은 eventId와 canonical payload를 유지한다. 새 eventId로 우회하거나 consumer state를 직접 수정하지 않는다.
- 서비스 간 추적은 event JSON이 아닌 W3C `traceparent` header를 사용한다. Learning Core outbox는 `baggage` 없이 필요한 trace context만 보존해 publish span을 continue/link하고 Billing은 수신 trace를 잇는다.
- AttemptGroup publish/consume 구조화 로그의 공통 field는 `service`, `operation`, `outcome`, `traceId`, `eventId`, `durationMs`다. `service`는 `learning-core`/`billing` allowlist, `operation`은 `attempt_group_outbox_publish`/`attempt_group_event_consume` 같은 고정값, `durationMs`는 monotonic clock으로 잰 해당 단계 처리 시간의 non-negative integer다.
- Billing consume log에는 `eventAgeMs=max(0, consumeNow-occurredAt)`도 기록해 outbox 대기·network·retry를 포함한 전달 지연을 구분한다. 음수 원시 값은 0으로 정규화하되 clock-skew counter와 low-cardinality outcome으로 관측한다.
- 플랫폼 log timestamp는 UTC로 유지한다. `durationMs`와 `eventAgeMs`는 log field와 histogram 값으로 사용할 수 있지만 metric label로 사용하지 않는다. `service`, `operation`, `outcome`만 승인된 low-cardinality label로 사용할 수 있다.
- traceId/eventId는 metric label, canonical digest, idempotency key나 domain aggregate field가 아니다. userId, sessionId, attemptGroupId, candidate와 provider 원문은 일반 log/trace attribute에 기록하지 않는다.

TrialClaim retention expiry로 subject link가 제거된 group은 더 이상 새 replacement authorization의 근거가 되지 않는다. 기존 Learning Core Session·결과는 삭제하지 않으며 늦은 terminal event는 익명 audit projection만 닫을 수 있다.

## 7. 상태 머신

### 7.1 Reservation

```text
RESERVED ──confirm──> CONFIRMED
   │
   ├──cancel──> CANCELED
   └──expiresAt/reaper──> EXPIRED
```

- terminal: `CONFIRMED`, `CANCELED`, `EXPIRED`
- terminal state를 다른 terminal state로 되돌리지 않는다.
- expiry scan은 `status=RESERVED AND expiresAt<=now`만 처리한다.
- confirm과 expiry race는 Transaction/CAS 한쪽만 성공한다.

### 7.2 AttemptGroup

```text
INITIAL confirm → OPEN → GRADING → COMPLETED
                    ↑         │
                    └─ RETAKE_AVAILABLE
```

- entitlement authorization 관점의 non-terminal 상태는 `OPEN`, `GRADING`, `RETAKE_AVAILABLE`이다.
- replacement reserve는 `OPEN`과 `RETAKE_AVAILABLE`에서만 허용한다.
- `GRADING`은 결과 복구가 끝날 때까지 replacement를 차단한다.
- `COMPLETED`는 다시 열지 않는다.

## 8. MongoDB collection 계약

현재 greenfield Billing schema version은 `v3`다. `v2`의 `benefitType`·`grantType`
field와 index가 존재하는 DB는 application startup에서 자동 변경하지 않고 별도 migration
또는 비운영 DB 재생성 후 v3를 활성화한다.

### 8.1 `inbound_event_inbox`

event payload 자체를 복제하지 않고 멱등성에 필요한 digest와 최소 metadata만 저장한다.

```text
eventId, producer, eventType, schemaVersion, payloadDigest,
consumerScopeId, userId, bindingRevision, disposition, receivedAt, purgeAt
```

- `disposition`: `APPLIED`, `STALE`
- `DUPLICATE`는 기존 inbox를 조회해 반환하는 처리 결과이며 새 문서를 만들지 않는다.
- malformed·unsupported·conflict event는 inbox에 저장하지 않는다.
- candidate, evidence payload와 exception 원문은 저장하지 않는다.
- inbox 기본 보존은 120일이다.

### 8.2 `trial_eligibility`

```text
consumerScopeId, userId, bindingRevision, state,
candidates[{keyVersion, value}], verifiedAt?, revokedAt?, updatedAt
```

- `state`: `VERIFIED`, `REVOKED`
- verified event는 candidates 전체 교체다.
- revoked는 candidates를 제거하지만 revision high-water tombstone을 유지한다.

### 8.3 `benefit_definitions`

```text
_id=benefitCode, displayName, entitlementModel, unitType,
defaultGrantUnits, policyVersion, active, createdAt
```

- 최초 seed는 `FREE_EXAM_ONCE`, `UNIT`, `EXAM_ATTEMPT`, 1 unit, policy v1, active다.
- `_id`의 stable `benefitCode`만 Claim·alias·Grant authorization reference로 사용한다.
- seed가 없으면 insert하고, 기존 document의 승인 policy가 다르면 덮어쓰지 않고 startup을 실패시킨다.
- catalog seed는 user, candidate, TrialClaim 또는 Grant를 생성하지 않는다.

### 8.4 `trial_claims`

```text
trialClaimId, benefitCode, subjectRefId?, sourceEventId?,
claimedAt, retentionExpiresAt, state, anonymizedAt?
```

- `benefitCode`: 현재 `FREE_EXAM_ONCE`
- `state`: `ACTIVE`, `ANONYMIZED`
- `claimedAt`과 `retentionExpiresAt`은 갱신하지 않는다.
- anonymize 시 subject/source 연결을 unset하고 비식별 tombstone만 남긴다.

### 8.5 `trial_candidate_aliases`

한 Claim의 key rotation candidate마다 한 문서를 둔다.

```text
aliasId, benefitCode, keyVersion, candidate,
trialClaimId, active, createdAt, retentionExpiresAt
```

- candidate 배열의 단순 multikey unique index를 사용하지 않는다.
- reserve는 current candidate 중 하나라도 active/unexpired alias와 일치하면 기존 Claim으로 판단한다.
- 한 binding의 candidate가 서로 다른 active Claim에 연결되면 security invariant 위반으로 fail-closed하고 경보한다.
- expired alias가 아직 물리 삭제되지 않았으면 reserve Transaction이 먼저 `active=false`로 fencing한 뒤 새 Claim을 만들 수 있다.

### 8.6 `billing_subject_links`

```text
subjectRefId, trialClaimId, consumerScopeId, userId,
active, createdAt, retentionExpiresAt
```

ledger·Reservation·AttemptGroup은 가능한 한 `userId` 대신 `subjectRefId`를 참조한다. 3년 purge에서 이 mapping을 삭제하면 audit core는 유지하면서 개인 연결을 끊을 수 있다.

### 8.7 `entitlement_grants`

```text
grantId, benefitCode, sourceType, sourceId,
subjectRefId, totalUnits, availableUnits, heldUnits, consumedUnits,
state, createdAt, updatedAt, version
```

- 무료 MVP는 `benefitCode=FREE_EXAM_ONCE`, `totalUnits=1`이다.
- mutable 수량은 projection이며 truth source는 ledger다.
- 모든 수량은 음수가 아닌 정수이고 합은 `totalUnits`와 같아야 한다.

### 8.8 `entitlement_ledger`

```text
ledgerEventId, aggregateType, aggregateId, sequence,
eventType, units, subjectRefId?, trialClaimId?,
reservationId?, allocationId?, dedupeKey,
occurredAt, metadataVersion
```

- append-only이며 update/delete하지 않는다.
- v1 event: `GRANTED`, `RESERVED`, `RELEASED`, `CONSUMED`, `SUBJECT_UNLINKED`
- 민감 payload, candidate, userId와 provider 원문을 넣지 않는다.
- subject link 삭제 뒤에는 subjectRefId가 개인으로 resolve되지 않아야 한다.

### 8.9 `reservations`

```text
reservationId, callerService, subjectRefId, operationId,
payloadHash, reservationKind, status,
attemptGroupId, proposedSessionId, mockExamId,
createdAt, expiresAt, terminalAt?, version, activeGuard
```

- `reservationKind`: `INITIAL`, `REPLACEMENT`
- `status`: `RESERVED`, `CONFIRMED`, `CANCELED`, `EXPIRED`
- `activeGuard=true`는 RESERVED에만 존재하고 terminal transition에서 unset한다.
- audit 문서에 TTL index를 두지 않는다.

### 8.10 `reservation_allocations`

```text
allocationId, reservationId, grantId, units,
status, createdAt, terminalAt?, version
```

- `status`: `HELD`, `CONSUMED`, `RELEASED`
- cancel/expiry는 정확히 원래 grant로 복원한다.
- REPLACEMENT Reservation은 allocation이 없다.

### 8.11 `idempotency_commands`

```text
commandId, callerService, userId, operationId, commandType,
payloadHash, state, reservationId?, responseSnapshot?,
createdAt, terminalAt?, purgeAt?, active
```

- `commandType`: `RESERVE`, `CONFIRM`, `CANCEL`
- `state`: `PROCESSING`, `SUCCEEDED`, `FAILED_TERMINAL`
- terminal command는 `terminalAt + 7일` 뒤 제거할 수 있다.
- PROCESSING에는 `purgeAt`을 두지 않는다.
- response snapshot에는 candidate, balance와 provider 원문을 넣지 않는다.

### 8.12 `attempt_groups`

```text
attemptGroupId, subjectRefId, trialClaimId, consumptionLedgerEventId,
mockExamId, status, activeSessionId?, openGuard,
createdAt, updatedAt, completedAt?, version
```

- `status`: `OPEN`, `GRADING`, `RETAKE_AVAILABLE`, `COMPLETED`
- non-terminal일 때만 `openGuard=true`이고 COMPLETED에서 unset한다.
- Learning Core 학습 데이터를 복제하지 않는 entitlement authorization projection이다.

### 8.13 `attempt_sessions`

```text
sessionId, attemptGroupId, subjectRefId, operationId,
state, activeGuard, proposedAt, confirmedAt?, terminalAt?, version
```

- `state`: `PROPOSED`, `ACTIVE`, `ABANDONED_RESTARTED`, `COMPLETED`, `FAILED`
- 한 group/user에 active Session projection은 하나만 허용한다.
- 질문, 답안, 점수, S3 key와 AI 상태를 저장하지 않는다.

## 9. 필수 MongoDB index

index 이름을 명시적으로 고정하고 production에서 `spring.data.mongodb.auto-index-creation`에 의존하지 않는다. 배포 migration/initializer가 기존 index option을 비교하고 불일치하면 fail-fast한다.

| collection | key | option / partial filter | 이름과 목적 |
| --- | --- | --- | --- |
| `inbound_event_inbox` | `{eventId: 1}` | unique | `ux_inbox_event_id` |
| `inbound_event_inbox` | `{producer: 1, consumerScopeId: 1, userId: 1, bindingRevision: 1}` | unique partial, Identity revision event만 적용 | `ux_inbox_identity_scope_user_revision` |
| `inbound_event_inbox` | `{purgeAt: 1}` | TTL `expireAfterSeconds: 0` | `ttl_inbox_purge_at` |
| `trial_eligibility` | `{consumerScopeId: 1, userId: 1}` | unique | `ux_trial_scope_user` |
| `trial_eligibility` | `{consumerScopeId: 1, "candidates.keyVersion": 1}` | non-unique | `ix_trial_key_version` |
| `benefit_definitions` | `{_id: 1}` | built-in unique | `_id_` |
| `trial_claims` | `{retentionExpiresAt: 1, state: 1}` | non-unique | `ix_claim_retention_state` |
| `trial_candidate_aliases` | `{benefitCode: 1, keyVersion: 1, candidate: 1}` | unique partial `{active: true}` | `ux_active_trial_candidate` |
| `trial_candidate_aliases` | `{active: 1, retentionExpiresAt: 1}` | non-unique | `ix_alias_active_expiry` |
| `trial_candidate_aliases` | `{trialClaimId: 1}` | non-unique | `ix_alias_claim` |
| `billing_subject_links` | `{trialClaimId: 1}` | unique | `ux_subject_link_claim` |
| `billing_subject_links` | `{userId: 1, active: 1}` | non-unique | `ix_subject_link_user_active` |
| `billing_subject_links` | `{active: 1, retentionExpiresAt: 1}` | non-unique | `ix_subject_link_expiry` |
| `entitlement_grants` | `{sourceType: 1, sourceId: 1, benefitCode: 1}` | unique | `ux_grant_source_type` |
| `entitlement_ledger` | `{dedupeKey: 1}` | unique | `ux_ledger_dedupe` |
| `entitlement_ledger` | `{aggregateType: 1, aggregateId: 1, sequence: 1}` | unique | `ux_ledger_aggregate_sequence` |
| `reservations` | `{subjectRefId: 1, operationId: 1}` | unique | `ux_reservation_subject_operation` |
| `reservations` | `{subjectRefId: 1}` | unique partial `{activeGuard: true}` | `ux_active_reservation_subject` |
| `reservations` | `{status: 1, expiresAt: 1}` | non-unique | `ix_reservation_status_expiry` |
| `reservation_allocations` | `{reservationId: 1, grantId: 1}` | unique | `ux_allocation_reservation_grant` |
| `idempotency_commands` | `{callerService: 1, userId: 1, operationId: 1, commandType: 1}` | unique | `ux_command_scope_operation_type` |
| `idempotency_commands` | `{callerService: 1, userId: 1}` | unique partial `{active: true, commandType: "RESERVE"}` | `ux_active_create_command_user` |
| `idempotency_commands` | `{purgeAt: 1}` | TTL `expireAfterSeconds: 0` | `ttl_terminal_command_purge_at` |
| `attempt_groups` | `{subjectRefId: 1}` | unique partial `{openGuard: true}` | `ux_open_group_subject` |
| `attempt_groups` | `{trialClaimId: 1, createdAt: -1}` | non-unique | `ix_group_claim_created` |
| `attempt_sessions` | `{sessionId: 1}` | unique | `ux_attempt_session_id` |
| `attempt_sessions` | `{attemptGroupId: 1, operationId: 1}` | unique | `ux_session_group_operation` |
| `attempt_sessions` | `{subjectRefId: 1}` | unique partial `{activeGuard: true}` | `ux_active_session_subject` |
| `attempt_sessions` | `{attemptGroupId: 1, proposedAt: -1}` | non-unique | `ix_session_group_proposed` |

TTL monitor는 정확한 삭제 시각을 보장하지 않는다. inbox와 terminal command처럼 재처리 window가 끝난 보조 문서에만 TTL을 쓰고, Reservation expiry와 TrialClaim multi-document anonymization은 scheduler+Transaction으로 처리한다.

## 10. Transaction 경계

MongoDB는 replica set과 Transaction을 필수로 사용한다. `TransientTransactionError`와 `UnknownTransactionCommitResult`는 같은 event/operation ID로 재시도한다.

### T1. eligibility event

한 Transaction에서 inbox insert, digest/revision 비교, current binding replace/revoke와 high-water update를 처리한다. commit 뒤에만 204다.

### T2. reserve

한 Transaction에서 다음을 처리한다.

1. idempotency command claim/payload hash 확인
2. current verified binding과 revision 확인
3. expired alias fencing 및 active alias 교집합 확인
4. FREE_EXAM_ONCE BenefitDefinition의 active·policy v1을 확인하고 필요한 경우 TrialClaim,
   subject link, 모든 candidate alias, definition 기반 free grant와 `GRANTED` ledger 생성
5. 기존 non-terminal AttemptGroup 판정
6. INITIAL grant allocation hold 또는 REPLACEMENT authorization
7. Reservation, proposed attempt session과 `RESERVED` ledger 생성
8. command success snapshot 저장

동시 요청에서 alias unique 또는 active guard duplicate가 발생하면 Transaction을 abort하고 기존 command/Claim/group을 다시 읽어 멱등 결과 또는 안정적인 conflict로 수렴시킨다.

### T3. confirm

Reservation CAS 뒤 INITIAL allocation/grant consume, `CONSUMED` ledger, AttemptGroup OPEN과 active Session projection을 한 Transaction에 반영한다. REPLACEMENT는 ledger consume 없이 session pointer만 전환한다.

### T4. cancel/expiry

`RESERVED` CAS에 성공한 한 worker만 allocation을 RELEASED로 바꾸고 원 grant 수량과 ledger를 복원한다. TrialClaim은 변경하지 않는다.

### T5. AttemptGroup event

inbox insert, active session fencing, 허용 group transition과 terminal/open guard 변경을 한 Transaction에 처리한다.

### T6. TrialClaim retention purge

daily worker가 만료 Claim별 Transaction으로 다음을 처리한다.

1. active alias를 false로 fencing하고 alias 문서 삭제
2. subject link 삭제
3. TrialClaim의 subject/source 연결 unset과 `ANONYMIZED` tombstone 전환
4. `SUBJECT_UNLINKED` 비식별 ledger event append

물리 purge 전 reserve가 만료 alias를 발견하면 같은 fencing을 선행해 3년 시점부터 즉시 새 Claim을 만들 수 있어야 한다. purge evidence는 batch 실행 시각·처리 건수·성공 여부만 남긴다.

## 11. 금지 사항

- raw phone, last4, 전화번호 암호문 저장
- candidate 또는 userId를 URL, metric label, 일반 log/trace에 기록
- client가 entitlement type, candidate, TrialClaim/grant/reservation ID를 선택
- userId, candidate 또는 mutable balance만으로 소비 진실을 판단
- Reservation/TrialClaim audit 문서에 TTL delete index 사용
- confirmed consumption 일반 cancel
- app 사용자 JWT를 Billing internal API workload credential로 재사용
- Billing에 Session 문제·답안·점수·피드백·S3/AI payload 저장

## 12. 구현 및 검증 순서

1. DTO validator와 canonical payload digest contract test
2. versioned index initializer와 fail-fast option comparison
3. Identity event inbox/current binding Transaction
4. TrialClaim/alias/subject link/grant/ledger reserve Transaction
5. confirm/cancel/status와 5분 expiry scheduler
6. AttemptGroup event projection
7. 3년 purge와 익명 tombstone
8. replica-set Testcontainers 동시성·unknown commit 결과 테스트
9. Learning Core contract client/outbox와 staging E2E

필수 concurrency test는 다음을 포함한다.

- 동일 candidate의 서로 다른 user/operation 동시 reserve에서 Claim 하나
- key rotation의 old/new candidate 교집합이 Claim 하나로 수렴
- 같은 key/same payload replay와 different payload conflict
- confirm/cancel/expiry race에서 terminal transition 하나
- 사용자당 active reservation/group/session/command 하나
- replacement가 기존 consumption과 mockExamId를 재사용
- expired alias가 purge 전이어도 새 Claim 생성 가능
- purge와 reserve race에서 active alias 하나
- restore snapshot을 가정한 overdue purge 뒤 candidate 연결 없음

## 13. 후속 ADR

- VPC Lattice, ECS role, route auth policy, SG, SigV4 signer·timeout/retry·local/test adapter와 production/staging 이관은 `ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`로 구체화했다.
- 35일 backup provider 설정과 restore runbook의 실제 명령·승인 절차
