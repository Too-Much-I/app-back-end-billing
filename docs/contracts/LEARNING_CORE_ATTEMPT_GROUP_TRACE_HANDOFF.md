# Learning Core AttemptGroup trace 연동 전달 사항

## 1. 목적

Learning Core의 AttemptGroup outbox publisher와 Billing consumer를 하나의 분산 trace로 연결한다. 두 서비스는 같은 event를 `eventId`로 찾고 같은 호출 흐름을 `traceId`로 연결한다.

`traceparent` 문자열 전체를 두 서비스에서 그대로 재사용하는 것이 목표가 아니다. 같은 trace 안에서 단계마다 새 span을 만들기 때문에 `traceId`는 같고 `spanId`는 달라지는 것이 정상이다.

## 2. 확정 전파 규격

- 전파 표준: W3C Trace Context
- HTTP header: `traceparent`와 필요한 경우 `tracestate`
- `baggage`: 저장·전파하지 않음
- event JSON, canonical digest, idempotency key와 domain aggregate에 trace context를 포함하지 않음
- Billing endpoint: `POST /internal/v1/attempt-group-events`

Learning Core는 terminal 결과와 outbox event를 같은 Mongo Transaction/CAS로 만들 때 현재 유효한 trace context를 outbox 전송 metadata로 보존한다. raw inbound HTTP header 전체나 baggage를 복사하지 않는다.

publisher 처리 순서는 다음과 같다.

```text
원래 요청/worker span
→ terminal 결과 + outbox와 trace context metadata 저장
→ publisher가 저장된 context 추출
→ attempt_group_outbox_publish span 생성
→ 해당 publish span의 W3C context를 HTTP header에 inject
→ SigV4 요청으로 Billing 호출
→ Billing attempt_group_event_consume span이 같은 trace를 이어받음
```

저장된 `traceparent`를 HTTP 요청에 그대로 복사하지 않는다. publisher span을 새로 만든 뒤 그 span의 context를 inject해야 publish 단계가 trace에 별도 span으로 나타난다.

context가 없거나 유효하지 않으면 publisher가 새 trace를 시작하고 고정 counter를 증가시킨 뒤 event 전송은 계속한다. trace 문제 때문에 업무 event를 유실하거나 DEAD_LETTER로 보내지 않는다.

재시도는 같은 `eventId`와 canonical payload를 유지한다. 각 publish attempt는 별도 span으로 남기되 원래 저장된 context를 parent 또는 link로 사용한다.

## 3. 구조화 로그 규격

Learning Core publisher의 공통 필드는 다음과 같다.

```text
service=learning-core
operation=attempt_group_outbox_publish
outcome=<고정 lowercase enum>
traceId=<현재 publish span의 32자리 lowercase trace id>
eventId=<AttemptGroupStatusChanged event UUID>
durationMs=<해당 publish attempt 처리 시간의 non-negative integer>
```

Billing consumer는 같은 키를 사용한다.

```text
service=billing
operation=attempt_group_event_consume
outcome=applied|duplicate|stale|conflict|projection_not_ready|temporary_failure
traceId=<같은 distributed trace id>
eventId=<같은 event UUID>
durationMs=<Billing consume 처리 시간>
eventAgeMs=<event occurredAt부터 Billing 수신까지의 시간>
```

- `durationMs`는 wall clock 차이가 아니라 monotonic clock으로 측정한다. Java에서는 `System.nanoTime()` 차이를 millisecond로 변환한다.
- 플랫폼 로그 timestamp는 UTC를 사용한다.
- outcome은 자유 문자열이나 exception message가 아니라 코드에 정의한 고정 lowercase enum만 사용한다.
- Learning Core publisher outcome 권장 allowlist는 `delivered`, `retry_scheduled`, `dead_letter`, `auth_failure`, `lease_lost`, `temporary_failure`다. 실제 구현 PLAN에서 최종 고정한다.
- network/408/425/429/5xx는 `retry_scheduled`, 400/409/422는 `dead_letter`, 401/403은 `auth_failure`로 분류한다.

## 4. 로그·trace 금지 항목

다음 값은 일반 log message, MDC, span attribute에 넣지 않는다.

- `userId`, `sessionId`, `attemptGroupId`, `subjectRefId`
- candidate와 전화번호 관련 값
- event payload와 canonical digest
- Authorization, SigV4 관련 header와 credential
- raw `traceparent`, raw `tracestate`, baggage
- exception stack에 포함될 수 있는 provider/AI 응답 원문

`traceId`와 `eventId`는 구조화 로그에는 허용하지만 metric label/tag에는 사용하지 않는다.

## 5. metric 규칙

- 허용 tag: `service`, `operation`, 고정 `outcome`, 고정 target/status 등 저카디널리티 enum
- 금지 tag: `traceId`, `eventId`, 사용자/group/session ID, 자유 문자열 오류, `durationMs`, age 값
- duration과 age는 label이 아니라 timer/histogram value로 기록한다.
- missing/invalid trace context, retry exhausted, auth failure와 dead-letter는 고정 counter로 관측한다.

Learning Core metric 이름은 Learning Core namespace를 사용해도 되지만 `service`, `operation`, `outcome` 의미와 단위는 Billing과 동일해야 한다.

## 6. 필수 테스트

1. 유효한 원본 W3C context를 outbox에 보존하고 publisher HTTP 요청이 같은 `traceId`를 Billing까지 전달한다.
2. origin, publisher, Billing consumer의 `spanId`는 서로 다르고 `traceId`는 같다.
3. publisher가 새 span을 만들고 그 context를 inject하며 저장된 `traceparent`를 그대로 replay하지 않는다.
4. missing/invalid context는 새 trace와 counter로 수렴하고 event 전송을 막지 않는다.
5. baggage가 outbox와 HTTP 요청에 저장·전파되지 않는다.
6. 재시도에서도 같은 eventId·payload를 유지하고 publish attempt별 span이 생성된다.
7. 구조화 로그에 공통 필드가 있고 금지 식별자·payload·credential이 없다.
8. metric tag에 traceId/eventId/사용자 식별자가 없고 duration이 non-negative histogram/timer value다.

## 7. Billing 현재 구현과 운영 경계

Billing은 Micrometer Tracing과 OpenTelemetry bridge를 사용하고 W3C-only `ContextPropagators`를 명시했다. inbound context가 정상일 때 HTTP server span 아래 `attempt_group_event_consume` INTERNAL 업무 span을 만들고 strict decode부터 멱등성 확인, service·Mongo 처리까지 감싼다. Spring Security 관측 span이 server와 consume 사이에 추가될 수 있지만 같은 trace의 descendant 관계는 유지된다.

실제 embedded HTTP 통합 테스트는 Learning Core에서 전달된 traceId 유지, HTTP/consume의 서로 다른 spanId, 정확한 span 이름과 INTERNAL kind, baggage 미전파, 정상·예외 종료와 민감 attribute 부재를 검증한다.

현재 확정 범위는 context propagation과 구조화 로그·metric 계약이다. 실제 trace exporter/backend, dashboard와 alert 인프라는 별도 운영 작업이다.
