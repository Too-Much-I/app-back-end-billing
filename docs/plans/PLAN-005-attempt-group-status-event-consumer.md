# PLAN-005: AttemptGroup status event consumer vertical slice

- 상태: 구현 완료·Jira `TMI-117` 완료
- 작성일: 2026-08-31
- 대상 저장소: `app-back-end-billing`
- Jira: `TMI-117` — `[Billing] AttemptGroup status event consumer 구현`
- 선행 작업: Billing `PLAN-003`, `PLAN-004` 구현과 Learning Core `TMI-116` merge 완료
- 관련 계약: `docs/codex/CONTRACT_DECISIONS.md` C8/C8-1, `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`

## 1. 목표

Learning Core가 durable outbox로 전달할 `AttemptGroupStatusChanged` schema v1 event를 Billing이 안전하게 수신해 AttemptGroup과 AttemptSession entitlement projection을 전진시킨다.

```text
INITIAL/REPLACEMENT confirm
→ AttemptGroup OPEN + AttemptSession ACTIVE

Learning Core event
→ GRADING
→ COMPLETED
또는 RETAKE_AVAILABLE
```

이 vertical slice는 다음을 보장한다.

- 필수 submit 접수 뒤 `GRADING`으로 전진한다.
- 피드백·유효 점수·Summary evidence가 모두 true일 때만 `COMPLETED`로 닫는다.
- 승인된 최종 실패만 `RETAKE_AVAILABLE`로 전환한다.
- RETAKE_AVAILABLE은 Claim/Grant/consumption 복원이 아니라 같은 consumption의 REPLACEMENT만 허용한다.
- 중복·역순·이전 Session event가 현재 group을 되돌리거나 잘못 닫지 않는다.
- inbox, group과 session 전이는 한 Mongo Transaction으로 commit된다.
- coarse failureCode의 상세 원인은 W3C trace context와 `traceId + eventId` 로그로 Learning Core까지 추적한다.

## 2. 구현·배포 단위

### 2.1 이번 PLAN에 포함

- `POST /internal/v1/attempt-group-events`
- 16 KiB bounded body와 schema v1 strict decoder
- canonical JSON SHA-256 digest
- 공용 `inbound_event_inbox` 기반 eventId 멱등성
- active Session fencing과 subject/group/session 관계 검증
- `OPEN`, `GRADING`, `RETAKE_AVAILABLE`, `COMPLETED` 전이
- AttemptSession `ACTIVE → COMPLETED | FAILED` terminal 전이
- APPLIED/DUPLICATE/STALE/CONFLICT/PROJECTION_NOT_READY 결과 분류
- 204/400/409/422/503 internal error 계약
- Mongo expected-version CAS, transaction retry와 unknown commit 수렴
- Learning Core workload route의 local/test security 경계
- W3C traceparent 수신, 구조화 로그와 duration/event-age metric
- unit, MVC/security와 replica-set Testcontainers integration/concurrency test
- ADR·index initializer·운영 문서 정합성 갱신

### 2.2 이번 PLAN에서 제외

- Learning Core outbox entity·writer·lease publisher 코드
- Learning Core 결과 판정 로직 변경
- 앱 공개 API와 공개 DTO 변경
- TrialClaim, Grant, allocation 또는 consumption 복원·재지급
- 새로운 paid/subscription/coupon entitlement
- owner rebind, account merge와 탈퇴·재가입 연결
- privileged repair route와 수동 DB 수정 command
- 실제 AWS Lattice service, IAM policy, ECS task role와 Security Group 생성
- 실제 staging Mongo index migration과 AWS E2E 실행
- trace exporter/backend, dashboard와 log retention infrastructure 생성

Billing consumer를 먼저 구현·배포한 뒤 Learning Core outbox/publisher를 별도 PLAN/Jira로 구현한다. producer를 먼저 활성화하지 않는다.

## 3. 현재 baseline

이미 구현된 기반은 다음과 같다.

- Reservation confirm이 만드는 `AttemptGroup OPEN`
- `AttemptGroup.activeSessionId`, `status`, `openGuard`, `version`
- `AttemptSession ACTIVE`, `activeGuard`, `version`
- `OPEN`·`RETAKE_AVAILABLE`의 REPLACEMENT reserve와 `GRADING` 차단
- Identity event용 `inbound_event_inbox`, global eventId unique와 120일 TTL
- replica-set Mongo Transaction executor와 unknown commit 재확인 패턴
- Learning Core `TMI-116`의 ExamSession `attemptGroupId` 저장

아직 없는 것은 다음이다.

- AttemptGroup event endpoint·strict decoder
- group/session terminal CAS repository
- event inbox와 projection을 함께 반영하는 service
- AttemptGroup event error·metric·trace log
- Learning Core outbox/publisher와 실제 Lattice 연동

## 4. 승인된 wire contract

### 4.1 endpoint

```http
POST /internal/v1/attempt-group-events
Content-Type: application/json
traceparent: <W3C trace context>
```

호출 주체는 Learning Core workload만 허용한다. 성공 응답은 body 없는 `204 No Content`다.

### 4.2 common event

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
  "targetStatus": "GRADING"
}
```

### 4.3 target별 필드

| target | evidence | failureCode |
| --- | --- | --- |
| `GRADING` | 금지 | 금지 |
| `COMPLETED` | 필수; 세 boolean 모두 true, `evidenceVersion=1` | 금지 |
| `RETAKE_AVAILABLE` | 금지 | 승인 allowlist 중 하나 필수 |

승인 failureCode:

- `REQUIRED_RESULTS_UNAVAILABLE`
- `SUMMARY_UNAVAILABLE`
- `GRADING_DEADLINE_EXCEEDED`
- `RESULT_INTEGRITY_VIOLATION`

exception message, AI/provider 이름·code·원문과 자유 형식 사유는 받거나 저장하지 않는다.

### 4.4 field validation

- body 16 KiB 이하
- `eventId`: lowercase canonical UUID v4
- `eventType=AttemptGroupStatusChanged`
- `schemaVersion=1`
- `producer=learning-core`
- `occurredAt`: UTC Instant canonical text; 오래된 outbox event는 허용
- `userId`: lowercase canonical UUID
- `attemptGroupId`: lowercase canonical UUID v4
- `sessionId`: UUID로 가정하지 않는 trim 불가 1~128자 opaque token
- duplicate field, trailing token, scalar/date/enum coercion과 unknown field 거절
- target별 optional field 상호배타와 evidence exact validation

미래 시각은 허용 clock skew를 넘으면 400으로 거절한다. 구체적인 skew 기본값은 기존 서비스 시간 정책과 맞춰 configuration으로 두고 contract test에서 경계를 고정한다. 과거 event에는 age 상한을 두지 않고 outbox 보존·재처리를 허용한다.

`traceparent`는 observability header이며 event JSON, digest와 authorization 근거가 아니다. 누락·형식 오류만으로 business event를 거절하지 않고 새 trace를 시작하며 low-cardinality missing/invalid counter를 기록한다. `baggage`는 수신·전달·저장하지 않는다.

## 5. strict decode와 canonical digest

전용 decoder는 bounded raw byte를 payload 저장·logging 전에 검증한다.

canonical JSON 순서는 다음으로 고정한다.

```text
eventId, eventType, schemaVersion, producer, occurredAt,
userId, attemptGroupId, sessionId, targetStatus,
evidence?, failureCode?
```

COMPLETED evidence 순서:

```text
requiredFeedbackQueryable, validScoreQueryable,
summaryQueryable, evidenceVersion
```

- UUID는 lowercase canonical text다.
- timestamp는 UTC millisecond canonical text다.
- absent target field는 canonical JSON에 넣지 않는다.
- raw whitespace/property order 차이는 같은 digest로 수렴한다.
- 의미 있는 field 차이는 다른 digest가 된다.
- digest는 lowercase SHA-256 hex이고 raw payload/evidence source를 저장하지 않는다.

## 6. inbox 설계

기존 collection과 index를 재사용한다.

```text
inbound_event_inbox
```

AttemptGroup inbox document는 공통 최소 field만 저장한다.

```text
_id=eventId,
eventId, producer, eventType, schemaVersion, payloadDigest,
disposition=APPLIED|STALE,
receivedAt, purgeAt=receivedAt+120일
```

- event payload, evidence, failureCode, userId, attemptGroupId와 sessionId를 inbox에 복제하지 않는다.
- `ux_inbox_event_id`와 `ttl_inbox_purge_at`을 그대로 사용한다.
- Identity 전용 `ux_inbox_identity_scope_user_revision` partial index는 Learning Core document에 적용되지 않는다.
- 다른 producer가 같은 eventId를 사용해도 global unique index와 producer/digest 비교로 conflict 처리한다.
- DUPLICATE는 기존 document를 조회한 처리 결과이며 새 document를 만들지 않는다.
- malformed, unsupported, projection-not-ready와 target conflict는 inbox에 저장하지 않는다.

Identity `InboundEventInbox` entity/package를 이번 작업에서 이동하지 않는다. AttemptGroup domain은 같은 collection을 사용하는 최소 `AttemptGroupEventInbox` view/repository를 두어 Identity revision code와 결합하지 않는다. 공용 inbox class 통합은 `_class`/migration 검토가 필요한 별도 refactor다.

새 collection/index가 없고 Identity partial index와 호환되므로 Mongo schema version v3는 유지한다. initializer와 test는 Learning Core inbox document가 기존 index 검증을 통과하는지 추가 확인한다.

## 7. fencing과 상태 전이

### 7.1 active Session fencing

적용 전 다음을 모두 확인한다.

1. AttemptGroup이 event `attemptGroupId`로 존재한다.
2. group `activeSessionId`가 event `sessionId`와 같다.
3. AttemptSession의 group·subject 관계가 group과 같다.
4. AttemptSession state가 `ACTIVE`이고 activeGuard가 true다.
5. active/unexpired BillingSubjectLink가 있으면 event userId와 exact match한다.

old, `ABANDONED_RESTARTED`, `COMPLETED`, `FAILED` Session event는 204 STALE다. 존재하는 active group/session/subject 관계가 서로 다르면 409 `EVENT_TARGET_CONFLICT`다.

retention purge로 active subject link가 없으면 mapping을 복원하지 않는다. exact group/session fencing을 통과한 `COMPLETED`만 anonymous audit close를 허용하고, 새 authorization을 만들 수 있는 GRADING/RETAKE_AVAILABLE은 STALE로 닫는다. 이 경계는 retention 통합 테스트로 고정한다.

### 7.2 transition matrix

| current group | GRADING | COMPLETED | RETAKE_AVAILABLE |
| --- | --- | --- | --- |
| `OPEN` | APPLIED | evidence true면 APPLIED | failureCode 유효하면 APPLIED |
| `GRADING` | semantic no-op/STALE | evidence true면 APPLIED | failureCode 유효하면 APPLIED |
| `RETAKE_AVAILABLE` | STALE | STALE | semantic no-op/STALE |
| `COMPLETED` | STALE | semantic no-op/STALE | STALE |

`OPEN→COMPLETED/RETAKE_AVAILABLE` 직접 전진은 terminal event가 GRADING보다 먼저 전달될 수 있기 때문에 허용한다.

### 7.3 projection update

GRADING:

```text
AttemptGroup.status = GRADING
AttemptGroup.updatedAt = receivedAt
AttemptGroup.version += 1
AttemptSession은 ACTIVE 유지
```

COMPLETED:

```text
AttemptGroup.status = COMPLETED
AttemptGroup.completedAt = occurredAt
AttemptGroup.openGuard unset
AttemptGroup.version += 1

AttemptSession.state = COMPLETED
AttemptSession.activeGuard unset
AttemptSession.terminalAt = occurredAt
AttemptSession.version += 1
```

RETAKE_AVAILABLE:

```text
AttemptGroup.status = RETAKE_AVAILABLE
AttemptGroup.activeSessionId unset
AttemptGroup.openGuard = true
AttemptGroup.version += 1

AttemptSession.state = FAILED
AttemptSession.activeGuard unset
AttemptSession.terminalAt = occurredAt
AttemptSession.version += 1
```

이후 REPLACEMENT confirm만 새 AttemptSession을 ACTIVE로 만들고 group을 OPEN으로 전환한다. Claim, Grant, allocation, ledger consumption과 mockExamId는 변경하지 않는다.

### 7.4 producer terminal 단일성

Learning Core는 같은 Session에 COMPLETED와 RETAKE_AVAILABLE을 모두 생성하지 않아야 한다. consumer는 먼저 commit된 terminal 상태를 유지하고 늦은 모순 event를 STALE 처리하며 anomaly metric을 기록한다.

정확한 제품상 terminal 정답은 producer가 local result state와 outbox terminal event를 같은 Transaction/CAS에 저장하고 Session당 terminal event 하나를 unique하게 강제해 보장한다. 이 전제는 후속 Learning Core PLAN의 필수 완료 조건이다.

## 8. target missing·conflict 분류

| 상황 | 결과 | inbox |
| --- | --- | --- |
| 정상 생성 순서상 group/session이 아직 없음 | 503 `ATTEMPT_PROJECTION_NOT_READY`, `Retry-After: 5` | 저장 안 함 |
| old/abandoned/terminal Session | 204 STALE | STALE 저장 |
| 동일 Session·동일 target semantic no-op | 204 STALE | STALE 저장 |
| group/session이 존재하지만 연결 관계가 다름 | 409 `EVENT_TARGET_CONFLICT` | 저장 안 함 |
| 같은 eventId·같은 digest | 204 DUPLICATE | 기존 inbox 유지 |
| 같은 eventId·다른 digest/producer | 409 `EVENT_ID_CONFLICT` | 기존 inbox 유지 |
| Mongo 일시 장애·unknown commit 미확정 | 503 `BILLING_TEMPORARILY_UNAVAILABLE` | commit 여부에 따름 |

consumer는 missing AttemptGroup/Session/subject link를 새로 생성하거나 임의 연결하지 않는다.

## 9. Transaction과 동시성

한 event 처리 Transaction은 다음 순서를 유지한다.

1. eventId inbox 조회와 digest/producer 분류
2. group·session·subject link 조회
3. active Session fencing과 transition 판정
4. APPLIED 또는 STALE inbox insert
5. APPLIED이면 expected group/session version CAS
6. group과 session의 guard/status/terminal field 변경
7. commit 뒤 outcome metric/log 기록

부분 성공을 만들지 않는다.

- inbox만 저장되고 projection이 바뀌지 않거나 그 반대인 상태를 허용하지 않는다.
- same event concurrent insert의 duplicate key는 winner inbox를 재조회해 DUPLICATE/CONFLICT로 수렴한다.
- group/session CAS loser는 최신 상태를 다시 읽어 STALE, CONFLICT 또는 retry로 분류한다.
- transient transaction label은 bounded local retry 후 같은 eventId로 재확인한다.
- unknown commit 결과는 inbox와 group/session state를 다시 읽어 committed outcome을 확인하고 새 eventId를 만들지 않는다.
- COMPLETED와 RETAKE_AVAILABLE race는 한 expected-version CAS만 승리한다.

Transaction retry 중 raw body, userId, sessionId와 group ID를 log에 남기지 않는다.

## 10. API·오류 계약

| HTTP | code | 처리 |
| --- | --- | --- |
| 204 | body 없음 | APPLIED/DUPLICATE/STALE |
| 400 | `INVALID_REQUEST` | malformed/oversize/field·evidence·failureCode 오류 |
| 409 | `EVENT_ID_CONFLICT` | same eventId different digest/producer |
| 409 | `EVENT_TARGET_CONFLICT` | 존재하는 group/session/subject 관계 충돌 |
| 422 | `UNSUPPORTED_CONTRACT` | eventType/schemaVersion/producer/target enum 미지원 |
| 503 | `ATTEMPT_PROJECTION_NOT_READY` | 같은 eventId로 5초 뒤 재시도 |
| 503 | `BILLING_TEMPORARILY_UNAVAILABLE` | backoff+jitter 재시도 |

internal error envelope만 사용하고 앱용 `BaseResponse`를 사용하지 않는다. 안전한 message, retryable과 비식별 correlationId 외 candidate/user/group/session/provider 정보와 stack trace를 응답하지 않는다.

## 11. security

- TEST mode: `ROLE_LEARNING_CORE_WORKLOAD`만 route 접근 허용
- wrong role, Identity role, unsigned/no principal은 거절
- LATTICE_AWS_IAM mode: Lattice auth policy가 Learning Core task role·POST·path를 검사한다.
- Billing은 Lattice edge를 우회하는 direct task 경로를 신뢰하지 않는다.
- feature flag/config가 완성되지 않으면 route는 deny-all/fail-closed다.
- local/test에서 AWS credential, Lattice 또는 실제 Learning Core를 호출하지 않는다.

`SecurityConfig`에는 `/internal/v1/attempt-group-events`를 기존 Reservation route와 같은 Learning Core workload scope로 추가한다. 실제 IAM/Lattice policy와 direct-bypass 검증은 staging gate다.

## 12. tracing·log·metric

### 12.1 tracing 기반

Billing은 Spring Boot dependency management를 따르는 Micrometer Tracing + OpenTelemetry bridge를 사용한다.

- propagation: W3C `traceparent`
- baggage: disabled
- exporter/backend credential: 이번 저장소에 추가하지 않음
- valid inbound context: Billing HTTP server span 아래 `attempt_group_event_consume` INTERNAL 업무 span으로 연결
- 업무 span 범위: strict event decode부터 멱등성 확인, service 처리와 Mongo 반영까지
- 정상·예외 경로 모두 업무 span 종료; 예외는 span error로 기록
- 업무 span attribute에 event/user/session/group ID, payload, digest, 인증·SigV4 정보를 넣지 않음
- missing/invalid context: 새 trace 시작, counter 기록, event 처리 계속
- test: 실제 embedded HTTP server 요청과 OpenTelemetry SpanProcessor로 server/consume 관계, 이름·kind·종료·privacy 검증

Learning Core의 origin trace 저장과 publish span continue/link는 후속 publisher PLAN에서 같은 W3C 계약으로 구현한다.

### 12.2 구조화 로그

공통 field:

```text
service=billing
operation=attempt_group_event_consume
outcome=applied|duplicate|stale|conflict|projection_not_ready|temporary_failure
traceId=<opaque trace id>
eventId=<opaque event UUID>
durationMs=<non-negative integer>
eventAgeMs=<non-negative integer>
```

- `durationMs`: monotonic clock으로 잰 Billing consume 단계 처리 시간
- `eventAgeMs=max(0, consumeNow-occurredAt)`: outbox 대기·network·retry 포함 전달 지연
- 음수 raw event age: 0으로 정규화하고 clock skew counter 증가
- 플랫폼 timestamp: UTC
- userId, sessionId, attemptGroupId, subjectRefId, candidate와 provider 원문 금지
- payload, Authorization, traceparent 원문 금지

### 12.3 metric

권장 이름:

```text
billing.attempt_group.events
billing.attempt_group.consume.duration
billing.attempt_group.event.age
billing.attempt_group.clock_skew
billing.attempt_group.transaction.retry_exhausted
billing.attempt_group.terminal_conflict
billing.attempt_group.trace_context_missing
```

허용 tag는 `service`, `operation`, `outcome`, `targetStatus` 같은 fixed enum뿐이다. traceId, eventId, user/group/session ID, failureCode 자유값과 duration/age를 label로 사용하지 않는다. duration과 age는 histogram value다.

## 13. package·파일 계획

```text
web.tosunsaeng.billing.domain.attempt
├── api
│   ├── AttemptGroupEventController
│   ├── AttemptGroupEventDecoder
│   └── AttemptGroupEventRequestSizeFilter
├── application
│   ├── AttemptGroupEventService
│   ├── AttemptGroupEventOutcome
│   └── AttemptGroupEventMetrics
├── domain
│   ├── entity
│   │   ├── AttemptGroup                    # transition method/CAS field 보강
│   │   ├── AttemptSession                  # terminal transition 보강
│   │   └── AttemptGroupEventInbox          # same collection 최소 view
│   ├── enums
│   │   ├── AttemptGroupEventTarget
│   │   ├── AttemptGroupFailureCode
│   │   └── AttemptGroupEventDisposition
│   └── model
│       ├── AttemptGroupStatusEvent
│       └── AttemptGroupCompletionEvidence
├── exception
│   └── AttemptGroupEventException
└── repository
    ├── AttemptGroupRepository              # expected version transition
    ├── AttemptSessionRepository            # expected version terminal
    └── AttemptGroupEventInboxRepository

web.tosunsaeng.billing.global
├── config/security/SecurityConfig           # route scope 추가
└── observability
    └── TraceCorrelation                     # tracing adapter/port
```

converter가 DTO→domain 변환 외 실질 검증을 맡지 않도록 strict decoder가 validated immutable domain event와 digest를 생성한다. Identity inbox package 이동과 공통 대규모 refactor는 하지 않는다.

## 14. 테스트 계획

### 14.1 decoder·contract

- target별 valid fixture
- duplicate/unknown field, trailing token, scalar/date/enum coercion
- lowercase UUID v4와 opaque sessionId 경계
- evidence 세 boolean false/missing, evidenceVersion mismatch
- target별 evidence/failureCode 상호배타
- failureCode allowlist 밖 값과 자유 문자열
- whitespace/property order가 같은 digest
- 의미 field 변화가 다른 digest
- 16 KiB exact boundary와 oversize
- 오래된 event 허용, 미래 clock skew 경계
- raw payload·ID가 exception/log에 노출되지 않음

### 14.2 application unit

- OPEN→GRADING
- OPEN/GRADING→COMPLETED direct/normal
- OPEN/GRADING→RETAKE_AVAILABLE direct/normal
- 같은 target different event semantic no-op
- COMPLETED 불가역
- RETAKE 뒤 old Session stale
- REPLACEMENT 새 active Session event만 적용
- active Session/group/subject mismatch conflict
- active link missing retention edge의 anonymous COMPLETED와 GRADING/RETAKE stale
- projection missing 503/Retry-After 5
- same eventId same/different digest

### 14.3 replica-set Mongo integration

- inbox+group+session Transaction commit/rollback
- APPLIED/STALE inbox document 최소 field와 120일 TTL
- Identity partial revision index가 Learning Core inbox를 방해하지 않음
- same event concurrent insert 하나만 적용
- COMPLETED vs RETAKE_AVAILABLE race 한 terminal 승자
- stale old Session과 current Session race
- expected-version CAS loser 재분류
- transient transaction retry
- unknown commit result 재조회 수렴
- duplicate key가 500으로 노출되지 않음
- COMPLETED가 openGuard/activeGuard를 정확히 제거
- RETAKE가 consumption/Grant/ledger를 변경하지 않음

### 14.4 API·security

- Learning Core test role 204
- Identity/wrong/no role 거절
- disabled mode deny-all
- body 없는 204와 internal error envelope exact
- 409/422/503 code·retryable·Retry-After contract
- user/group/session ID가 URL·response에 노출되지 않음

### 14.5 observability

- valid traceparent propagation과 traceId MDC
- missing/invalid traceparent 새 trace·counter
- baggage 무시/미저장
- service/operation/outcome/durationMs/eventAgeMs log field
- monotonic duration non-negative
- negative event age 0 + skew counter
- traceId/eventId가 metric tag에 없음
- 민감 ID와 payload가 log/trace에 없음

### 14.6 전체 회귀

- Trial eligibility event consumer
- BenefitDefinition seed·schema v3
- INITIAL/REPLACEMENT reserve
- confirm/cancel/status/expiry
- Claim 3년 retention 불변식
- 최종 `./gradlew clean test`
- `git diff --check`

## 15. 구현 순서

1. approved contract fixture와 decoder test 추가
2. AttemptGroup event immutable model·strict decoder·canonical digest 구현
3. AttemptGroup 전용 최소 inbox view/repository 구현
4. group/session expected-version CAS repository 보강
5. subject link resolve와 fencing/transition classifier 구현
6. inbox+projection Transaction service와 unknown commit 수렴 구현
7. controller, bounded filter와 internal error mapping 구현
8. TEST/LATTICE security route와 fail-closed configuration 추가
9. Micrometer/OpenTelemetry trace adapter, structured log와 metric 구현
10. unit/MVC/security/replica-set concurrency·failure integration test 완료
11. ADR/CURRENT_STATE/WORKLOG와 운영 gate 갱신
12. 전체 test/diff/privacy scan 후 사용자 검토

외부 HTTP 호출을 Mongo Transaction 안에 넣지 않는다. Billing consumer는 inbound event와 local projection만 처리한다.

## 16. 완료 조건

- [x] schema v1 strict decoder와 canonical digest contract test 통과
- [x] APPLIED/DUPLICATE/STALE 204와 conflict/retry error exact 동작
- [x] active Session fencing과 단방향 transition matrix 구현
- [x] COMPLETED evidence, RETAKE failureCode allowlist 강제
- [x] inbox+group+session 단일 Transaction/CAS
- [x] duplicate/concurrent/transient/unknown commit 수렴
- [x] Claim/Grant/consumption 불변과 REPLACEMENT 회귀
- [x] Learning Core role route 성공과 wrong-role/direct-bypass local negative test
- [x] traceId/eventId/service/duration/eventAge 관측성 및 privacy test
- [x] index initializer가 shared inbox document와 호환
- [x] `./gradlew clean test` 전체 성공
- [x] CURRENT_STATE·WORKLOG 갱신

## 17. production 활성화 gate

PLAN-005 코드 완료만으로 publisher나 production caller를 활성화하지 않는다.

필수 후속 gate:

1. Billing consumer를 staging에 먼저 배포
2. 실제 schema v3 index/replica-set Transaction 검증
3. Learning Core outbox writer·lease publisher 별도 PLAN/Jira 구현
4. Session당 terminal event 하나의 unique/CAS와 outbox retry/dead-letter 검증
5. production/staging Lattice network·task role·route policy·SG 연결
6. traceparent propagation과 service/duration/eventAge log 확인
7. GRADING·COMPLETED·RETAKE_AVAILABLE 역순/중복/failure injection E2E
8. RETAKE_AVAILABLE 후 REPLACEMENT가 추가 차감 없이 같은 group/mockExamId를 유지하는 E2E
9. publisher feature flag를 staging에서 먼저 활성화하고 관찰 후 production 승인

## 18. rollback

- publisher가 아직 비활성화된 consumer-first 배포는 endpoint feature flag를 off해 rollback한다.
- 이미 수신한 APPLIED inbox/group/session projection은 임의 delete·역전하지 않는다.
- 잘못된 code 배포는 publisher를 먼저 중지하고 reader-compatible consumer를 유지한 채 forward fix한다.
- index를 runtime에서 drop/recreate하지 않는다.
- production data repair는 일반 workload route가 아니라 별도 승인된 운영 절차/role로 수행한다.

## 19. 다음 작업

Jira `TMI-117` 기준 Billing local 구현과 전체 회귀 테스트, 사용자 검토 및 Jira 완료 전환이 끝났다. 다음으로 Billing consumer를 먼저 staging에 배포하고, Learning Core 저장소에서 별도 outbox/publisher 계획을 작성한다.

```text
결과 상태 판정
→ 같은 Mongo Transaction에 outbox insert
→ lease publisher
→ SigV4 + traceparent
→ Billing 204/409/422/503 분류
→ retry/dead-letter/replay
```

Learning Core 계획은 기존 TMI-116 public API와 Session saga를 변경하지 않고 AttemptGroup event 생산·전달만 추가해야 한다.
