# PLAN-003: Reservation lifecycle vertical slice

- 상태: 구현 완료·전체 회귀 성공·Jira 완료
- 작성일: 2026-08-28
- 대상 저장소: `app-back-end-billing`
- Jira: `TMI-113` — `[Billing] Reservation lifecycle 구현` (`완료`)
- 선행 작업: `PLAN-002` 구현 완료, `TMI-112` 완료
- 관련 계약: `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `docs/codex/CONTRACT_DECISIONS.md`

## 1. 목표

PLAN-002가 만든 `RESERVED` Reservation을 정상적으로 종료하는 Billing lifecycle을 구현한다.

- Learning Core Session이 durable commit되면 `confirm`으로 권리를 최종 소비한다.
- Session commit이 실패하거나 caller가 중단하면 `cancel`로 hold를 해제한다.
- 아무 후속 호출 없이 5분이 지나면 expiry worker가 hold를 해제한다.
- confirm 응답 유실·timeout에는 `status`로 실제 commit 결과를 조회한다.
- confirm, cancel과 expiry가 경쟁해도 Reservation은 정확히 하나의 terminal 상태로만 전이한다.

```text
RESERVED ──confirm──> CONFIRMED
   │
   ├──cancel────────> CANCELED
   └──expiry worker─> EXPIRED
```

이 작업은 무료권을 잠그는 PLAN-002와 그 잠금을 최종 소비하거나 복원하는 lifecycle을 연결한다. Summary·채점 완료는 confirm 조건이 아니다. confirm 시점은 Learning Core ExamSession의 DB commit 직후다.

## 2. 범위

### 2.1 포함

- `POST /internal/v1/reservations/{reservationId}/confirm`
- `POST /internal/v1/reservations/{reservationId}/cancel`
- `POST /internal/v1/reservations/status`
- lowercase UUID v4 path/header/body 식별자 검증
- confirm/cancel 16 KiB strict JSON decode와 canonical payload hash
- confirm/cancel command 멱등성 기록과 7일 terminal retention
- INITIAL confirm의 allocation·grant consume, `CONSUMED` ledger, AttemptGroup `OPEN`, Session `ACTIVE`
- REPLACEMENT confirm의 무추가차감 Session 교체와 group `OPEN`
- INITIAL cancel/expiry의 allocation release, grant 복원과 `RELEASED` ledger
- REPLACEMENT cancel/expiry의 기존 consumption 보존
- Reservation·allocation·grant·Session 조건부 상태 전이
- reserve command active guard 종료와 terminal snapshot 유지
- read-only operation status 조회
- configurable 5분 expiry scanner와 per-Reservation Mongo Transaction
- confirm/cancel/expiry race, transient retry와 unknown commit 결과 수렴
- Learning Core test principal route 권한과 wrong role/default deny 검증
- privacy-safe metric·경보와 replica-set Testcontainers 검증

### 2.2 제외

- Learning Core Billing client와 `reserve → Session commit → confirm` saga 구현
- confirm 결과 불명에 대한 Learning Core reconciliation worker
- privileged repair-confirm route
- AttemptGroup `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE` event consumer
- TrialClaim 3년 purge worker와 backup 운영 자동화
- Identity publisher 변경
- 실제 VPC Lattice, IAM auth policy, ECS task role과 Security Group 배포
- user merge·owner transfer
- Apple/Google 결제, paid credit, pass, coupon, 출석·추천, 환불

PLAN-003은 Billing 내부 lifecycle만 완성한다. 이 계획의 코드가 완료되어도 Learning Core saga와 실제 Lattice staging E2E가 끝나기 전에는 production caller를 활성화하지 않는다.

## 3. 선행 baseline

PLAN-002 구현으로 다음 document와 보장이 준비돼 있다.

- `RESERVED` Reservation과 5분 `expiresAt`
- INITIAL의 `HELD` allocation, grant `heldUnits=1`과 `RESERVED` ledger
- REPLACEMENT의 무allocation Reservation
- preallocated `attemptGroupId`와 `PROPOSED` attempt Session
- 성공한 RESERVE idempotency command와 response snapshot
- 사용자당 active create command·Reservation·Session unique guard
- grant ledger dedupe·sequence, reservation expiry scan index
- replica-set Transaction executor와 bounded retry 기반
- internal ingress `disabled`, `test`, `lattice-aws-iam` mode

현재 domain enum에는 목표 terminal 상태가 선언돼 있지만 실제 전이 method와 repository CAS는 아직 없다. `IdempotencyCommand.ResponseSnapshot`도 RESERVE 응답 전용이므로 lifecycle command snapshot을 안전하게 표현하도록 확장해야 한다.

## 4. 핵심 불변식

1. Reservation terminal 상태는 `CONFIRMED`, `CANCELED`, `EXPIRED` 중 하나이며 서로 전환하지 않는다.
2. `CONFIRMED`는 일반 cancel이나 expiry로 되돌리지 않는다.
3. CANCELED/EXPIRED는 일반 confirm으로 복구하지 않는다.
4. INITIAL의 HELD allocation은 정확히 한 번 `CONSUMED` 또는 `RELEASED`가 된다.
5. grant 수량은 항상 음수가 아닌 정수이고 `available + held + consumed = total`을 유지한다.
6. `CONSUMED`와 `RELEASED` ledger는 같은 Reservation에 동시에 생길 수 없다.
7. REPLACEMENT에는 새 allocation, grant consume과 entitlement ledger를 만들지 않는다.
8. cancel/expiry는 TrialClaim, aliases, subject link, `claimedAt`과 `retentionExpiresAt`을 변경하지 않는다.
9. status는 command, ledger와 상태 전이를 만들지 않는 read-only 조회다.
10. Session commit 시각은 caller evidence일 뿐 CANCELED/EXPIRED를 되돌리는 권한이 아니다.

## 5. 공통 HTTP·인증 계약

- 모든 endpoint는 동일 환경의 VPC Lattice `AWS_IAM`에서 허용된 Learning Core ECS task role만 호출한다.
- Identity role, unsigned 요청, 다른 환경 role, wrong route와 direct task 우회는 거절한다.
- local/test에서는 실제 AWS credential 없이 `LEARNING_CORE_WORKLOAD` test principal을 사용한다.
- request는 `Content-Type: application/json`, body 최대 16 KiB이며 redirect를 허용하지 않는다.
- internal response에는 앱용 `BaseResponse` wrapper를 적용하지 않는다.
- request/response body, SigV4 Authorization, userId, operationId와 Reservation 식별자를 일반 log에 남기지 않는다.

confirm과 cancel의 `Idempotency-Key`는 Reservation을 만든 RESERVE의 operation ID와 정확히 같아야 한다. status는 header 없이 body의 operation ID로 조회하며 새 idempotency command를 만들지 않는다.

## 6. strict decoder와 식별자 검증

confirm, cancel과 status는 endpoint별 전용 decoder를 사용한다.

- duplicate JSON field 거절
- trailing token 거절
- string/number/boolean coercion 금지
- unknown field 거절
- 빈 body·16 KiB 초과 거절
- 필수 문자열의 leading/trailing whitespace 거절
- `userId`, `operationId`, `reservationId`는 lowercase canonical UUID v4
- `sessionId`는 1~128자 exact opaque token
- timestamp는 RFC 3339 UTC `Z`, millisecond precision
- cancel reason은 v1 enum exact match

`sessionCommittedAt`은 형식과 canonical payload 비교에 사용한다. caller timestamp만으로 이미 EXPIRED/CANCELED인 Reservation을 repair-confirm하지 않으며 Billing `confirmedAt`은 Transaction에서 주입한 `Clock`의 시각이다.

## 7. confirm API

### 7.1 request·response

```http
POST /internal/v1/reservations/{reservationId}/confirm
Idempotency-Key: 018f6f36-2f42-4bf5-8c17-0be35de4872c
Content-Type: application/json
```

```json
{
  "userId": "e8b37a41-bae6-47f1-a770-052e6c5786d4",
  "sessionId": "ex_a1b2c3d4e5_0826_1530",
  "sessionCommittedAt": "2026-08-26T06:30:01.200Z"
}
```

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

### 7.2 canonical payload hash

```text
apiVersion=v1
callerService=LEARNING_CORE
commandType=CONFIRM
reservationId=<canonical UUID>
userId=<canonical UUID>
sessionId=<exact opaque token>
sessionCommittedAt=<canonical UTC Instant>
```

검증된 값을 LF로 연결해 UTF-8 SHA-256 lowercase hex를 계산한다. JSON property order와 whitespace는 hash에 영향을 주지 않지만 검증된 값이 다르면 다른 hash가 된다. raw JSON은 저장하지 않는다.

### 7.3 사전 일치 검증

Billing은 RESERVE command와 Reservation을 함께 읽어 다음을 확인한다.

- path `reservationId`가 RESERVE command snapshot의 Reservation과 일치
- header operation ID가 stored operation과 일치
- body `userId`가 stored RESERVE command user와 일치
- body `sessionId`가 Reservation `proposedSessionId`와 일치
- attempt Session의 operation·group·subject가 Reservation과 일치

- 해당 user·operation의 RESERVE command가 없으면 `404 OPERATION_NOT_FOUND`다.
- RESERVE command는 있지만 path Reservation 또는 session이 stored 값과 다르면 `409 RESERVATION_STATE_CONFLICT`다.
- 오류 body에는 어떤 식별자가 달랐는지 노출하지 않는다.

### 7.4 INITIAL confirm Transaction

하나의 Mongo Transaction에서 다음을 처리한다.

1. `(callerService, userId, operationId, CONFIRM)` command를 claim하거나 기존 command를 분류한다.
2. Reservation을 `status=RESERVED`, `activeGuard=true`, expected version 조건으로 `CONFIRMED` CAS 전이한다.
3. allocation을 `HELD → CONSUMED` CAS 전이한다.
4. 원 grant를 `heldUnits -1`, `consumedUnits +1`로 CAS 전이하고 수량 불변식을 확인한다.
5. `CONSUMED:<reservationId>` dedupe key로 append-only ledger를 추가한다.
6. preallocated ID로 AttemptGroup `OPEN` document를 생성하고 consumption ledger event를 연결한다.
7. proposed Session을 `PROPOSED → ACTIVE`로 전환하고 `confirmedAt`을 기록한다.
8. AttemptGroup `activeSessionId`를 현재 Session으로 설정한다.
9. RESERVE command와 CONFIRM command를 terminal success로 만들고 `active=false`, `terminalAt`, `purgeAt=terminalAt+7일`을 기록한다.
10. confirm response snapshot을 저장하고 commit 뒤 200을 반환한다.

TrialClaim과 eligibility projection은 confirm에서 다시 만들거나 변경하지 않는다.

### 7.5 REPLACEMENT confirm Transaction

- Reservation CAS와 command 멱등성 처리는 INITIAL과 같다.
- existing AttemptGroup ID, subject, fixed `mockExamId`와 상태를 다시 확인한다.
- `OPEN`은 유지하고 `RETAKE_AVAILABLE`이면 `OPEN`으로 전환한다.
- 새 proposed Session을 `ACTIVE`로 만들고 group `activeSessionId`를 교체한다.
- grant, allocation, `CONSUMED` ledger를 생성하거나 변경하지 않는다.
- 기존 consumption ledger event를 그대로 유지한다.

## 8. cancel API

### 8.1 request·response

```http
POST /internal/v1/reservations/{reservationId}/cancel
Idempotency-Key: 018f6f36-2f42-4bf5-8c17-0be35de4872c
Content-Type: application/json
```

```json
{
  "userId": "e8b37a41-bae6-47f1-a770-052e6c5786d4",
  "reason": "SESSION_COMMIT_FAILED"
}
```

v1 reason은 다음 두 값만 허용한다.

- `SESSION_COMMIT_FAILED`
- `CALLER_ABORTED`

```json
{
  "operationId": "018f6f36-2f42-4bf5-8c17-0be35de4872c",
  "reservationId": "36c2356c-29d1-443f-b8f1-298345ee4e89",
  "reservationStatus": "CANCELED",
  "canceledAt": "2026-08-26T06:30:02.000Z"
}
```

### 8.2 canonical payload hash

```text
apiVersion=v1
callerService=LEARNING_CORE
commandType=CANCEL
reservationId=<canonical UUID>
userId=<canonical UUID>
reason=<exact v1 enum>
```

### 8.3 INITIAL cancel Transaction

1. stored Reservation·operation·user 일치와 CANCEL command replay/conflict를 확인한다.
2. Reservation을 `RESERVED → CANCELED` CAS 전이하고 `activeGuard`를 제거한다.
3. allocation을 `HELD → RELEASED` CAS 전이한다.
4. 원 grant를 `heldUnits -1`, `availableUnits +1`로 복원한다.
5. `RELEASED:<reservationId>` dedupe key로 release ledger를 추가한다.
6. proposed Session을 `FAILED` terminal로 전환하고 active guard를 제거한다.
7. RESERVE·CANCEL command를 terminal success로 만들고 7일 retention을 설정한다.
8. cancel response snapshot을 저장하고 commit 뒤 200을 반환한다.

cancel reason은 command hash와 제한된 command metadata에만 반영한다. exception message나 Learning Core 내부 실패 원문은 저장하지 않는다.

### 8.4 REPLACEMENT cancel Transaction

- Reservation과 proposed Session만 terminal 처리한다.
- AttemptGroup과 기존 consumption은 변경하지 않는다.
- grant, allocation과 entitlement ledger를 변경하지 않는다.
- TrialClaim을 삭제하거나 재개방하지 않는다.

## 9. operation status API

```http
POST /internal/v1/reservations/status
Content-Type: application/json
```

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

- RESERVE command의 `(callerService, userId, operationId)`로 조회하고 연결된 Reservation의 live 상태를 반환한다.
- AttemptGroup document가 아직 없는 INITIAL RESERVED/CANCELED/EXPIRED에서는 `attemptGroupStatus`를 생략한다.
- terminal 전이 전에는 `terminalAt`을 생략한다.
- 조회 과정에서 command, Reservation, ledger와 `updatedAt`을 쓰지 않는다.
- 존재하지 않거나 terminal command 7일 retention 뒤 제거된 operation은 `404 OPERATION_NOT_FOUND`다.
- status 404는 앱에 직접 노출하기 위한 것이 아니라 Learning Core reconciliation 판단용이다.

## 10. expiry worker

### 10.1 실행 방식

- query는 `status=RESERVED AND expiresAt<=now`만 대상으로 한다.
- `ix_reservation_status_expiry`를 사용해 `expiresAt` 오름차순의 제한된 batch를 읽는다.
- 한 batch 전체가 아니라 Reservation 하나마다 독립 Transaction을 실행한다.
- 여러 ECS task에서 worker가 동시에 실행돼도 Reservation CAS에 성공한 한 worker만 실제 release한다.
- scheduler는 설정으로 명시적으로 활성화하며 local/test에서는 자동 외부 실행 없이 worker를 직접 검증할 수 있어야 한다.
- 최초 제안 기본값은 scan interval 10초, batch size 100이다. hold duration의 운영 계약은 5분 그대로다.

제안 설정은 다음과 같다.

```yaml
billing:
  reservation:
    hold-duration: 5m
    expiry-enabled: false
    expiry-scan-interval: 10s
    expiry-batch-size: 100
    terminal-command-retention: 7d
```

staging·production에서는 lifecycle 배포와 index preflight 뒤 `expiry-enabled=true`를 명시한다. worker가 꺼진 상태에서는 production reserve caller를 활성화하지 않는다.

### 10.2 INITIAL expiry Transaction

1. due Reservation을 `RESERVED → EXPIRED` CAS 전이한다.
2. HELD allocation을 RELEASED로 바꾼다.
3. 원 grant의 held unit을 available unit으로 복원한다.
4. cancel과 같은 `RELEASED:<reservationId>` ledger dedupe key를 사용한다.
5. proposed Session을 `FAILED` terminal로 전환한다.
6. RESERVE command를 terminal 처리하고 active create command guard를 해제한다.

### 10.3 REPLACEMENT expiry Transaction

- Reservation과 proposed Session만 terminal 처리한다.
- AttemptGroup, 기존 consumption, grant와 entitlement ledger는 변경하지 않는다.

expiry는 Reservation audit document를 삭제하지 않는다. Mongo TTL은 terminal command 보조 기록에만 사용한다.

## 11. race와 멱등성 수렴

### 11.1 confirm·cancel·expiry 경쟁

세 경로 모두 첫 write를 다음 조건의 Reservation CAS로 시작한다.

```text
reservationId = expected
status = RESERVED
activeGuard = true
version = expected
```

- CAS 성공 경로만 allocation·grant·ledger·group·session을 변경하고 Transaction을 commit한다.
- CAS 실패 경로는 live Reservation을 다시 읽어 같은 command replay인지 state conflict인지 분류한다.
- confirm과 expiry가 동시에 도착하면 하나만 `CONFIRMED` 또는 `EXPIRED`로 commit한다.
- cancel과 expiry는 release 결과가 같지만 terminal 상태는 먼저 CAS에 성공한 값 하나만 남긴다.
- `CONSUMED:<reservationId>`, `RELEASED:<reservationId>` unique dedupe가 이중 ledger를 방어한다.

`expiresAt`이 지난 뒤에도 Reservation이 아직 RESERVED라면 confirm과 worker가 동일한 CAS를 경쟁한다. caller의 `sessionCommittedAt`은 이미 EXPIRED된 상태를 되돌리지 못하며 privileged repair는 이 계획에 포함하지 않는다.

### 11.2 confirm/cancel command 멱등성

- key scope는 `(callerService, userId, operationId, commandType)`다.
- same command type·same canonical hash·SUCCEEDED는 저장된 response snapshot을 200으로 반환한다.
- same command type·different hash는 `409 IDEMPOTENCY_KEY_CONFLICT`다.
- CONFIRM과 CANCEL은 command type이 다르므로 cross-command hash conflict로 처리하지 않고 Reservation terminal state로 승자를 결정한다.
- losing command는 상태 전이 없이 `409 RESERVATION_STATE_CONFLICT`로 수렴한다.
- terminal command replay 보장 기간은 우선 7일이며 Reservation·ledger audit은 TTL 삭제하지 않는다.

### 11.3 Transaction retry·unknown commit

- preallocated command·ledger ID, immutable payload hash와 Billing `now`를 한 logical invocation 동안 재사용한다.
- `TransientTransactionError`는 bounded retry한다.
- `UnknownTransactionCommitResult`는 command snapshot과 live Reservation을 재조회해 commit을 확인한다.
- terminal 상태와 같은 command snapshot이 확인되면 기존 200 결과를 반환한다.
- 결과를 확정할 수 없으면 `503 BILLING_TEMPORARILY_UNAVAILABLE`와 `Retry-After`를 반환한다.
- retry 과정에서 새로운 operation, Reservation, ledger나 AttemptGroup을 만들지 않는다.

## 12. document·repository 변경 계획

### 12.1 domain transition

- `Reservation`: conditional confirm/cancel/expire와 terminal fields
- `ReservationAllocation`: `HELD → CONSUMED|RELEASED`
- `EntitlementGrant`: `consumeHeldOne`, `releaseHeldOne` CAS와 quantity invariant
- `EntitlementLedgerEntry`: `consumed`, `released` factory와 고정 dedupe key
- `AttemptGroup`: INITIAL OPEN 생성 시 active Session 연결, REPLACEMENT OPEN 전이
- `AttemptSession`: `PROPOSED → ACTIVE|FAILED` terminal transition
- `IdempotencyCommand`: command type별 factory/snapshot, active guard 종료, terminalAt/purgeAt

상태 전이를 단순 `save`의 last-write-wins로 구현하지 않는다. repository query에 expected current state와 version을 포함하고 modified count 또는 returned document를 확인한다.

### 12.2 repository operation

- Reservation ID 조회, user/operation 연결 검증과 terminal CAS
- allocation by Reservation 조회와 expected status CAS
- grant held consume/release CAS
- AttemptGroup ID 조회·insert·active Session 교체
- proposed Session confirm/fail CAS
- command type별 find/insert/terminal snapshot
- due Reservation ordered batch 조회
- status용 RESERVE command + Reservation + optional group read

## 13. Mongo index와 schema 계획

PLAN-002 schema v2의 다음 index를 그대로 사용한다.

- `ix_reservation_status_expiry`
- `ux_command_scope_operation_type`
- `ux_active_create_command_user`
- `ttl_terminal_command_purge_at`
- `ux_ledger_dedupe`
- `ux_ledger_aggregate_sequence`
- allocation·AttemptGroup·Session unique/partial unique index

PLAN-003은 새 collection이나 index를 요구하지 않는다. 구현 시작 시 실제 query explain과 index option test를 다시 확인하고, 새 index가 정말 필요하면 이 계획과 ADR 영향부터 보고한다. 승인 없이 schema version을 올리거나 기존 index를 drop/recreate하지 않는다.

## 14. 오류 mapping

| 상황 | HTTP/code | DB 결과 |
| --- | --- | --- |
| 최초 confirm/cancel 성공 | 200 | 전체 Transaction commit |
| same command/same payload replay | 200 | 기존 snapshot 반환 |
| status 성공 | 200 | read-only |
| malformed body·path·timestamp·enum | 400 `INVALID_REQUEST` | 없음 |
| header 누락·non-lowercase·non-v4 | 400 `INVALID_IDEMPOTENCY_KEY` | 없음 |
| status user/operation 또는 command 없음 | 404 `OPERATION_NOT_FOUND` | 없음 |
| same command key/different payload | 409 `IDEMPOTENCY_KEY_CONFLICT` | 없음 |
| wrong Reservation/user/operation/session 또는 terminal race 패배 | 409 `RESERVATION_STATE_CONFLICT` | 없음 |
| 같은 command가 아직 처리 중 | 409 `COMMAND_PROCESSING` | 없음 |
| invariant 오염·Mongo transient retry exhausted | 503 `BILLING_TEMPORARILY_UNAVAILABLE` | rollback 또는 commit 재확인 |
| unsigned/wrong role/direct bypass | Lattice 또는 security 401/403 | controller 미도달 |

409 processing과 503에는 제한된 정수 초 `Retry-After`를 제공한다. 오류 body에는 userId, Reservation·grant·Claim·Session 식별자, payload hash와 Mongo exception 원문을 넣지 않는다.

## 15. 보안·관측성

### 15.1 route matrix

| principal | reserve | confirm | cancel | status | eligibility event |
| --- | --- | --- | --- | --- | --- |
| Learning Core workload | allow | allow | allow | allow | deny |
| Identity workload | deny | deny | deny | deny | allow |
| unsigned/wrong role | deny | deny | deny | deny | deny |

Lattice mode의 애플리케이션 `permitAll`은 edge에서 검증된 경로만 도달한다는 ADR-002 전제와 SG direct-bypass 차단을 필요로 한다. 실제 AWS 배포 전에는 test principal로 route boundary를 검증한다.

### 15.2 metric

```text
billing.reservation.lifecycle{
  action=CONFIRM|CANCEL|EXPIRE|STATUS,
  kind=INITIAL|REPLACEMENT|UNKNOWN,
  outcome=SUCCEEDED|REPLAYED|NOT_FOUND|CONFLICT|RACE_LOST|TEMPORARY_FAILURE
}
```

별도 counter/gauge는 다음을 둔다.

- expiry due 처리 건수·실패 건수
- 가장 오래된 due Reservation lag
- CAS race lost
- grant/allocation invariant violation
- Transaction retry exhaustion과 unknown commit unresolved

metric label과 일반 log에 userId, operationId, reservationId, sessionId, subjectRefId와 payload hash를 넣지 않는다. 경보용 로그는 correlation ID, action, kind와 low-cardinality outcome만 포함한다.

## 16. 테스트 계획

### 16.1 decoder·hash·domain unit test

- confirm/cancel/status valid DTO
- duplicate field, trailing token, unknown field와 scalar coercion 거절
- lowercase UUID v4 path/header/body와 opaque session 경계
- timestamp UTC `Z` millisecond 형식과 cancel enum 검증
- JSON order·whitespace가 달라도 같은 hash
- sessionCommittedAt·reason·session/user/Reservation 차이는 다른 hash
- terminal 상태의 재전이 금지와 grant quantity invariant
- consumed/released ledger dedupe key 고정

### 16.2 application service test

- INITIAL confirm consume·ledger·group OPEN·Session ACTIVE
- REPLACEMENT confirm 무추가차감과 RETAKE_AVAILABLE → OPEN
- confirm same payload replay와 different payload conflict
- wrong user/operation/session/Reservation 거절
- CANCELED/EXPIRED confirm 거절, 자동 repair 없음
- INITIAL cancel 원 grant 복원·release ledger·TrialClaim 불변
- REPLACEMENT cancel consumption 불변
- cancel replay와 CONFIRMED cancel 거절
- status가 모든 Reservation 상태를 반환하고 write하지 않음
- operation not found 404
- due INITIAL/REPLACEMENT expiry와 non-due/CONFIRMED no-op
- terminal command active guard 해제와 7일 purgeAt

### 16.3 MVC·security test

- 세 endpoint의 정상 200 DTO와 body 상한
- stable 400/404/409/503 error envelope와 Retry-After
- error/response에 금지 정보가 불필요하게 노출되지 않음
- Learning Core test principal만 lifecycle route 성공
- Identity/wrong principal/default disabled 실패
- Learning Core principal의 eligibility route 실패
- 기존 reserve와 PLAN-001 security 회귀 유지

### 16.4 replica-set Testcontainers integration·concurrency test

- INITIAL confirm 전체 document atomic commit과 중간 실패 rollback
- INITIAL cancel/expiry 전체 release atomic commit과 rollback
- REPLACEMENT confirm/cancel/expiry에서 entitlement ledger·grant 변경 없음
- same confirm/cancel 동시 replay가 terminal transition·ledger 하나
- confirm vs cancel race에서 terminal 상태 하나
- confirm vs expiry race에서 terminal 상태 하나
- cancel vs expiry race에서 release ledger 하나
- 두 worker instance가 같은 due Reservation을 처리해도 release 한 번
- non-due와 CONFIRMED Reservation을 expiry시키지 않음
- grant available/held/consumed 합이 모든 race 뒤 1
- AttemptGroup·active Session unique guard 유지
- transient retry와 unknown commit response 재확인
- status 호출 전후 collection count/document가 동일
- 기존 schema v2 index exact option과 initializer idempotency 유지

### 16.5 전체 회귀

```bash
./gradlew clean test
```

PLAN-001·PLAN-002 테스트를 포함한 전체 테스트가 통과해야 한다. replica-set concurrency test는 skip해 성공으로 처리하지 않는다.

## 17. 구현 단계

### Step 1. wire contract·decoder·hash

- confirm/cancel/status DTO와 response snapshot
- path/header parser, strict decoder와 canonical hash
- timestamp/enum validation과 contract test

### Step 2. domain transition·repository CAS

- Reservation/allocation/grant/group/session/command transition
- consumed/released ledger factory
- expected state/version 조건과 modified result 확인
- existing schema v2 index query 적합성 검증

### Step 3. confirm Transaction

- command claim/replay/conflict
- INITIAL consume·ledger·group/session atomic transition
- REPLACEMENT no-additional-consumption transition
- transient retry와 unknown commit 수렴

### Step 4. cancel·expiry 공통 release

- 명시적 cancel command 처리
- INITIAL allocation/grant release와 ledger
- REPLACEMENT no-entitlement-change 처리
- due scanner, scheduler configuration과 multi-instance CAS

### Step 5. status·controller·security·observability

- read-only operation status
- lifecycle endpoint controller와 route matrix
- stable errors·Retry-After
- privacy-safe metric와 expiry lag 경보 기반

### Step 6. concurrency·회귀 검증

- confirm/cancel/expiry 모든 race 조합
- failure injection, transient/unknown commit
- 전체 Gradle test와 개인정보·계약 리뷰
- CURRENT_STATE, WORKLOG와 구현 결과 갱신

각 Step은 독립적으로 검토하되 불완전한 lifecycle을 production caller에 노출하지 않는다.

## 18. 완료 조건

- [x] confirm/cancel/status wire DTO가 ADR-001과 일치
- [x] strict decoder·16 KiB·lowercase UUID v4 계약 검증
- [x] confirm/cancel same payload replay와 different payload conflict 수렴
- [x] INITIAL confirm이 allocation·grant consume, ledger, group, Session을 원자적으로 처리
- [x] REPLACEMENT confirm이 추가 consumption 없이 Session만 연결
- [x] INITIAL cancel/expiry가 원 grant를 정확히 한 번 복원
- [x] REPLACEMENT cancel/expiry가 기존 consumption을 변경하지 않음
- [x] TrialClaim·claimedAt·retentionExpiresAt이 lifecycle에서 불변
- [x] CONFIRMED cancel/expiry와 CANCELED/EXPIRED 일반 confirm 차단
- [x] confirm/cancel/expiry race에서 terminal 상태와 ledger 하나
- [x] status가 read-only이고 missing operation은 404
- [x] reserve active command guard가 terminal에서 해제되고 command는 7일 보존
- [x] Reservation·ledger audit에 TTL 삭제가 없음
- [x] expiry worker due 조건·batch·multi-instance CAS 검증
- [x] transient retry·unknown commit 결과 수렴
- [x] default deny, wrong role/route deny, Learning Core test principal 성공
- [x] raw phone·candidate·userId·payload·credential log/metric 노출 없음
- [x] AttemptGroup event·repair·타 서비스·결제 범위가 섞이지 않음
- [x] `./gradlew clean test` 전체 성공
- [x] `CURRENT_STATE.md`·`WORKLOG.md` 갱신

### 18.1 구현 결과

- confirm·cancel·status endpoint와 전용 strict decoder, UUID v4 path/header/body 검증, canonical SHA-256 hash를 구현했다.
- Reservation을 첫 CAS write로 사용하고 allocation·grant·ledger·AttemptGroup·Session·command를 같은 Mongo Transaction에서 전이한다.
- expiry scheduler는 기본 비활성이고 `expiry-enabled=true`에서 10초 간격·100건 batch 기본값으로 동작한다. due Reservation마다 독립 Transaction을 사용한다.
- schema v2 collection·index는 변경하지 않았다. `ix_reservation_status_expiry`, command TTL과 기존 unique index를 재사용한다.
- replica-set Testcontainers에서 INITIAL·REPLACEMENT, replay/conflict, 세 terminal race, multi-worker, transient retry와 unknown commit을 실행했다.
- 2026-08-28 `./gradlew clean test`: 총 82개, 실패 0, 오류 0, skip 0.
- 사용자 승인에 따라 Jira `TMI-113`을 `완료`로 전환하고 완료 category를 재확인했다.

## 19. 위험과 대응

| 위험 | 대응 |
| --- | --- |
| confirm과 expiry가 각각 성공해 consume 후 release | Reservation expected-state/version CAS를 첫 write로 사용하고 같은 Transaction 안에서만 후속 변경 |
| cancel과 expiry가 RELEASED ledger를 중복 생성 | `RELEASED:<reservationId>` unique dedupe + allocation HELD CAS |
| REPLACEMENT가 다시 무료권을 차감 | kind별 branch에서 allocation/grant/ledger repository 호출 부재를 통합 테스트 |
| terminal 뒤 reserve command active guard가 남음 | 세 terminal 경로 모두 command `active=false`를 같은 Transaction에서 처리 |
| status 조회가 reconciliation state를 변경 | read-only service 분리와 before/after DB assertion |
| caller 시각으로 EXPIRED를 임의 복구 | sessionCommittedAt을 evidence/hash로만 사용하고 terminal CAS를 권한 기준으로 사용 |
| 여러 ECS task scheduler의 중복 실행 | per-Reservation CAS·Transaction, bounded ordered batch와 race metric |
| worker 비활성 상태에서 hold가 누적 | startup/config 확인과 production activation checklist에서 expiry-enabled 필수 |
| command TTL을 Reservation audit TTL로 오해 | terminal command만 7일 TTL, Reservation·ledger에는 TTL 금지 test |
| lifecycle만 완료하고 production 연동 | Learning Core saga·Lattice·staging E2E까지 production gate 유지 |

## 20. production 활성화 gate

다음 항목이 모두 완료되기 전에는 Learning Core production role에 Reservation route를 허용하지 않는다.

1. PLAN-003 confirm/cancel/status·expiry lifecycle과 모든 race test 완료
2. production/staging expiry worker 명시적 활성화와 lag alert 준비
3. Learning Core의 same operation reserve·Session commit·confirm/cancel/status saga 구현
4. confirm 응답 유실, timeout과 Session commit 실패 E2E 수렴
5. 실제 Lattice/IAM/SG route matrix와 direct bypass negative test
6. replica-set Mongo schema/index preflight와 rollback·backup runbook 확인

## 21. 후속 작업

PLAN-003 구현·회귀 검증과 Jira 완료 전환은 끝났다. 이후 기능 순서는 다음과 같다.

1. AttemptGroup 상태 event consumer와 entitlement projection
2. Learning Core Billing client·시험 생성 saga와 status reconciliation
3. privileged repair 정책·운영 reconciliation 계약
4. Identity/Learning Core SigV4 adapter와 Lattice staging E2E
5. TrialClaim daily purge·24시간 SLA·35일 backup/restore runbook
6. production migration과 caller activation

결제, coupon, 출석·추천과 환불은 무료 시험 lifecycle과 production gate 완료 뒤 별도 계획으로 진행한다.
