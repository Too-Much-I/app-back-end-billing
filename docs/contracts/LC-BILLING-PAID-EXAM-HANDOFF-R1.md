# LC–Billing 유료 시험 연동 인계 초안 R1

- 작성일: 2026-10-13
- 요청: 「어 준비해줘」
- 상태: Billing 검토용/LC 전달용 초안. 제품 정책은 아래 승인 근거를 따르며 **새 필드·오류·revoke 인증·schema는 공동 확정 전 제안**이다.
- 범위: PLAN011 유료 reserve/이용 증거, PLAN012 환불 전달. 010 구매 수신 구현과 병행 준비. LC 코드/문서 수정·외부 회신 전송·배포·활성화는 수행하지 않는다.
- 근거: [confirm-first 환불 정책](PAYMENT-CONFIRMED-SESSION-REFUND-POLICY-2026-10-13.md), [기존 증거 검토](../plans/PLAN-011-EXISTING-EVIDENCE-REVIEW-2026-10-13.md), PLAN011/012, ADR004. 과거 전용 grading 승인·gradingSessionId 예외·reserve v2 필수 제안을 본 초안으로 다시 협의한다.

## 1. LC에 전달할 요약

1. 기존 reserve API/요청에 source 선택을 받지 않고 Billing이 paid-first/free-preserve로 판정한다. 기존 응답에 `entitlementSource`를 추가하는 안이며 새 v2 route는 만들지 않는다.
2. 별도 유료 채점 시작 승인/결과 조회 API를 만들지 않는다. 환불보다 먼저 confirm된 **exact Session**만 기존 제출 기한 안에서 완료할 수 있다.
3. 환불 후 새로운 INITIAL/replacement는 차단한다. Billing→LC durable revoke는 유지하며 허용 Session 후보 field를 `confirmedSessionId`로 변경한다.
4. 상태 이벤트 `AttemptGroupStatusChanged` schemaVersion1 및 completion evidence/failureCode는 재사용한다. 구매 귀속·장기 증거는 Billing에 저장한다.
5. LC 먼저 호환 reader를 배포하고 Billing 확장을 배포한다. 미지원 LC/환불 consumer/보존·guard 준비가 남으면 paid admission은 OFF다.

## 2. 기존 API 유지와 출처 응답 제안

### 기존 요청 (변경 없음)

- `POST /internal/v1/reservations`: `Idempotency-Key`=lowercase UUIDv4 operationId. body userId/sessionId/mockExamId, 기존 phone continuation 세 field all-or-none.
- `POST /internal/v1/reservations/{reservationId}/confirm`: 같은 기존 command 규칙, body userId/sessionId/sessionCommittedAt.
- cancel·status·phone discovery의 기존 method/path/body 유지. status는 POST body userId/operationId이며 조회로 command/ledger를 생성하지 않는다.
- authenticated LC userId body의 기존 예외를 유지한다. user public API는 JWT sub만 사용한다. sessionId/mockExamId는 기존 opaque 형식이다.
- reserve→LC Session commit→confirm, RESERVED5분, 멱등 command와 immutable payload/digest 유지.

### 출처 field 후보

| 필드 | 타입/값 | 적용 제안 |
| --- | --- | --- |
| entitlementSource | string `TRIAL` 또는 `SUBSCRIPTION` | reserve200·status200에 추가. enum SUBSCRIPTION은 자동 갱신 상품이라는 뜻이 아니라 기존 기간형 모델 명칭이다 |

기존 operationId/reservationId/reservationKind/reservationStatus/attemptGroupId/sessionId/mockExamId/expiresAt 및 continuation field는 그대로다. confirm/cancel 응답 필드는 변경하지 않는 안이다. LC는 reserve source를 local Session에 영속 기록하고 status로 source를 재확인할 수 있다. source는 구매/reflow/refund 이후 재배정하지 않는다. LC에 purchaseId/entitlementId/가격/receipt를 보내지 않는다.

reserve 응답 예시(가짜 ID, SUBSCRIPTION만 신규 field):

```json
{
  "operationId": "11111111-1111-4111-8111-111111111111",
  "reservationId": "22222222-2222-4222-8222-222222222222",
  "reservationKind": "INITIAL",
  "reservationStatus": "RESERVED",
  "attemptGroupId": "33333333-3333-4333-8333-333333333333",
  "sessionId": "example-exam-id",
  "mockExamId": "example-mock-id",
  "expiresAt": "2026-10-13T06:05:00Z",
  "entitlementSource": "SUBSCRIPTION"
}
```

Billing INITIAL은 ACTIVE paid를 먼저 선택하고 무료 Claim/Grant를 보존한다. 기존 TRIAL replacement/phone continuation은 TRIAL source를 유지한다. SUBSCRIPTION replacement는 기존 source/group/mock을 유지하며 구매가 환불/만료됐으면 신규 Session 생성 불가다. 앱/LC가 현재 다른 이용권으로 기존 group을 갈아끼우지 않는다.

### reader-first/legacy

- LC reader는 배포 준비 단계에 field 유무를 둘 다 읽는다. field absent→TRIAL 해석은 **유료 미활성인 기존 무료 응답임이 입증된 환경/기록**에 한정한다. 알 수 없는 enum/잘못된 타입은 CONTRACT_ERROR. 무조건 무료 fallback 금지.
- 신규 Billing paid 응답과 durable command snapshot에는 source를 포함한다. 7일 command 소실 뒤에도 reservation/source snapshot으로 status를 복원한다.
- 이전 command의 exact 응답 재현/legacy source backfill·null 처리 기준은 fixtures로 동결한다. source 없는 paid 문서는 생성/활성화하지 않는다.
- 새 wire를 읽는 모든 saga/reconciliation 경로 확인 후 Billing을 배포한다. 미지원 task가 남거나 drain publisher가 구버전이면 paid ON 금지. 앱 강제 업데이트가 LC 서버 호환을 대신하지 않는다.

## 3. confirm/환불 순서와 완료 예외

| Billing commit 순서 | 확정 동작 |
| --- | --- |
| confirm 성공 → refund | exact confirmed Session만 기존 기한 안에 계속 제출/채점/완료. root/new/replacement는 deny |
| refund 성공 → 최초 confirm | 409 ENTITLEMENT_REVOKED 후보. LC orphan Session 보상. 예약만으로 완료 예외 없음 |
| confirm 성공 → 응답 유실 → refund → 동일 confirm 재호출 | 기존 성공 결과 반환/재확인. 현재 root revoke만 보고 과거 confirm 성공을 실패로 바꾸지 않음 |
| RESERVED 뒤 refund | confirm 불가, 보상/예약 정리. source/Claim 이력 삭제 없음 |
| 기존 Session 실패/기한 종료 뒤 refund | 새 replacement/이미 terminal Session 재개 불가 |
| COMPLETED 뒤 refund | 구매 권리만 종료, 기존 결과 이력 보존 |

순서는 provider 시각·LC sessionCommittedAt·event occurredAt·현재 GRADING 상태로 추정하지 않는다. Billing confirm/refund가 공통 root/account/guard version에 쓰기를 수행하는 Transaction/CAS로 직렬화한다. confirm 증거와 현재 진행 Session을 동일 기준으로 읽어 exact 예외를 고정한다. command TTL에만 의존하지 않는다.

replacement와 환불이 경합할 때 이미 ABANDONED_RESTARTED인 이전 Session은 부활시키지 않는다. 새 replacement가 confirm되지 않았다면 예외를 새 Session으로 승계하지 않는다. source 보호는 사용자/group 전체 무제한 허용이 아니다.

LC는 confirm 결과가 불명확하면 기존 status-first 절차로 재확인한다. CANCELED/EXPIRED/불명 결과를 repair-confirm이나 새 operation으로 우회하지 않는다. confirm 안 된 paid Session은 채점 실행 근거가 없다. confirmed exact Session은 별도 grading 승인 없이 기존 Job 멱등 흐름을 사용한다.

진행 중 protected Session의 사용자 guard를 환불 시 즉시 해제하지 않는다. 기존 기한/terminal을 확인해 exact group/session/version CAS로 해제한 뒤 다른 권리 INITIAL을 허용한다. 유효 무료권/다른 구매의 권리는 보존한다.

## 4. Billing→LC revoke 이벤트 후보

route 후보: `POST /internal/v1/entitlements/access/events` (LC 승인 전 확정/개방하지 않음).

```json
{
  "eventId": "44444444-4444-4444-8444-444444444444",
  "eventType": "AttemptGroupAccessRevoked",
  "schemaVersion": 1,
  "producer": "billing",
  "consumerScopeId": "example-learning-scope",
  "occurredAt": "2026-10-13T06:01:00Z",
  "userId": "55555555-5555-4555-8555-555555555555",
  "attemptGroupId": "33333333-3333-4333-8333-333333333333",
  "accessVersion": 1,
  "reason": "PAYMENT_REFUNDED",
  "confirmedSessionId": "example-exam-id"
}
```

- exact16KiB JSON, UUID/producer/scope/type, positive int64 version, reason PAYMENT_REFUNDED|PAYMENT_REVOKED, UTC occurredAt. duplicate/unknown/trailing/coercion는 거절한다.
- confirmedSessionId는 명시 null 또는 기존1~128 opaque Session ID. root revoke와 직렬화된 refund 적용 기준에서 이미 confirm된 유효 exact Session만 포함한다. 없으면 null. 금전 추가 반환/정정으로 새로운 허용 Session을 만들지 않는다.
- field는 기존 **미구현 초안** gradingSessionId를 대체하는 후보다. 이미 배포된 v1 wire를 확인 없이 바꾸는 승인이 아니다. 실제 LC inventory에서 소비 중인 동일 route/wire가 발견되면 호환 revision을 다시 정한다.
- LC local inbox+deny projection을 Transaction에 commit하고 body없는204. group/version·eventId/digest를 함께 검사한다. duplicate/stale204, 동일ID/동일version 의미충돌409, unknown 계약422, malformed400, transient503 후보.
- revoke-before-session은 deny tombstone과 exact exception을 보존한 뒤204. Session 도착 때 동일 group/owner/source 확인 없이 예외를 적용하지 않는다. 뒤늦은 Session·GRADING·COMPLETED event는 deny를 해제하지 못한다.
- confirmedSessionId가 있어도 기한 연장/terminal 재개/replacement 허용 없음. 현재 기한 판정은 기존 LC 시험 정책을 유지한다. LC의 생성·재응시·제출/Job 경로가 모두 같은 projection을 따른다.
- purchase/entitlement/Store token/금액/raw payload를 LC로 전송하지 않는다. 과거 결과 삭제/owner 이전도 하지 않는다.

### 인증 후보 — 별도 공동 수락 필요

LC→Billing 기존 여섯 API는 동결된 workload JWT(A 명세)를 그대로 사용한다. 새 Billing→LC route는 그 계약에 포함되지 않는다. 기존 revoke 초안의 Lattice/SigV4 전제를 자동 유지/제거한 것으로 보고하지 않는다.

권장 후보는 현재 JWT 전환과 같은 deployed-key RS256 profile이다: issuer `urn:tosunsaeng:workload:billing:{env}`, sub `billing-service`, aud `tosunsaeng-learning-core:entitlement-access-events:{env}`. TTL60/skew30·fresh token/jti·exact Bearer/query 거절·고정 verifier/환경 분리·no fallback을 제안한다. 기존 Billing→Identity recovery와 목적/audience/권한을 분리하고, LC 수신 trust/connector/ON-OFF/키 공유 또는 별도키 선택 및 HTTP fixture를 공동 동결한다. 실제 signer subject/key 규격을 상대 확인 전 확정하지 않는다.

## 5. 오류와 재시도

아래 신규 업무 오류·revoke 응답은 제안이고, 기존 오류 body는 기존 internal envelope(code/message/retryable 및 현행 추가 필드)을 따른다. 공개 BaseResponse로 바꾸지 않는다.

| 경우 | 응답/분류 | 처리 |
| --- | --- | --- |
| 기존 reserve 사용권 부족 | 402 ENTITLEMENT_INSUFFICIENT | 신규 시도 차단, 무료/유료 임의 생성 없음 |
| refund-first confirm / revoked source replacement | 409 ENTITLEMENT_REVOKED (신규 후보) | status-first 및 동일command 성공 증거 확인 후 미확정 Session 보상. 자동repair/new-key 우회 금지 |
| 기존 command 처리중 | 409 COMMAND_PROCESSING | Retry-After 후 같은command 재시도 |
| 같은key 다른내용 | 409 IDEMPOTENCY_KEY_CONFLICT | 자동재시도 금지/격리 |
| transient/transport/unknown commit | 503 또는 transport 실패 | status-first/동일operation·body. HTTP 시도마다 새 JWT |
| 기존 JSON 업무404 | OPERATION_NOT_FOUND | canceled/미소비 증명이 아님. 기존 recovery 정책 |
| LC→Billing 빈 ingress404 | Reservation CONTRACT_ERROR, AttemptGroup DEAD_LETTER | 기존 분류 유지/원본보존·승인복구. 무조건재시도 금지 |
| 신규 revoke401/403 | BLOCKED_AUTH 후보 | 원본보존·경보/인증복구. fallback 금지 |
| revoke400/409/422 | 계약/충돌 격리 후보 | eventId/version/body 유지, 운영 검토 |
| revoke 빈404 | route/receiver 준비오류 격리 후보 | 새 route 전용 fixture로 동결. 기존 LC 발신 분류를 역방향에 자동 적용하지 않음 |

revoke outbox 재전송은 같은 eventId/accessVersion/canonical body/digest, 새 JWT를 사용한다. 응답 유실 duplicate204로 수렴한다. root deny/금전 효과는 LC 전파 실패 때문에 rollback하지 않는다. 전파 지연·미완료 outbox는 경보 및 paid admission 운영 제한으로 관리한다. 별도 DEAD_LETTER 도구가 준비됐다고 가정하지 않는다.

## 6. 이용 증거 — wire 추가 없음

기존 v1 targetStatus/evidence/failureCode를 그대로 발신한다. COMPLETED의 세 queryable boolean=true/evidenceVersion1, RETAKE_AVAILABLE failureCode, occurredAt/group/session을 재사용한다. 이는 당시 결과 제공 가능 증거이지 실제 사용자 열람 증거가 아니다.

Billing은 purchase→immutable source→group/session 연결과 최소 terminal evidence/실패 사유/발생·관측 시각을 적용 Transaction에 저장한다. duplicate/stale가 이중 ledger를 만들지 않도록 source/session/event/effect 유일성과 CAS를 설계한다. inbox120일/digest와 command7일은 유일한 증거가 아니다. 최소 payment 증거5년과 erasable 연결 purge는 PLAN013 exact manifest에서 정하며 모든 LC 데이터/Session 전체를5년 보존하지 않는다.

현재 consumer의 trialClaim/BillingSubjectLink 검사는 paid 전용 owner/source 검증 분기로 보완한다. 무료 epoch·owner fence·phone continuation을 약화하지 않는다. 완료 후 학습 원본 삭제를 미사용으로 바꾸지 않는다. raw 답안/음성/점수/피드백 복제 금지. legacy 상세 증거 부재는 UNKNOWN으로 표시한다.

## 7. 분담·배포 순서

| 담당 | 구현/검증 |
| --- | --- |
| Billing011 | paid-first/source snapshot, reserve/status source 응답·legacy snapshot, 공통guard/confirm 증거, 기존event paid분기·최소증거 저장 |
| Billing012 | root deny/금전/reflow/durable fanout·exact exception, revoke publisher·retry·경보 |
| LC 담당 | source reader/Session영속, confirm불명 status-first, revoke inbox/projection/기한·replacement 차단/exception·Job멱등 |
| 공동 | exact wire·auth/profile/flags, JSON/token/HTTP fixtures+hash·독립검증, rollout/legacyinventory/E2E |

순서: 공동 명세 수락→LC 호환 reader/consumer 준비(기본OFF)→Billing011/012 준비(기본OFF)→공동 fixture/독립 검증→격리 테스트 배포/수신준비→발신 활성화→E2E/coverage/drain/관측→별도 운영 활성화 승인. 구 LC decoder로 무조건 rollback하지 않는다. LC revoke 준비 전 paid ON 금지. 현재010 개발을 이 문서로 완료 처리하지 않는다.

## 8. 공동 수락 테스트 후보

| ID | 확인할 불변식 |
| --- | --- |
| PAID01 | LC reader가 legacy무료 응답 및 TRIAL/SUBSCRIPTION source를 처리, unknown enum/타입 거절 |
| PAID02 | 같은reserve/status에 source 불변, command소실/legacy snapshot 호환, 무료ledger 불변 |
| PAID03 | paid/free 동시 INITIAL 및 owner rebind 경합에서 사용자guard 단일 확보 |
| PAID04 | confirm-first/refund-first 양순서와 commit불명/응답유실 재호출 수렴 |
| PAID05 | OPEN confirmed exact Session은 환불후 기존기한 완료, 다른Session/replacement 차단 |
| PAID06 | PROPOSED/RESERVED Session은 환불후 confirm 불가, orphan 보상·원본보존 |
| PAID07 | replacement예약중 refund, ABANDONED/FAILED/COMPLETED·기한 종료 Session 부활 금지 |
| PAID08 | revoke-before-session·duplicate·stale·sameversion conflict·outbox응답유실 수렴 |
| PAID09 | 전파지연 중 rootdeny/confirm차단, 보호Session guard 조기해제 없음 |
| PAID10 | 늦은GRADING/COMPLETED가 deny를 해제하거나 다른group guard를 풀지 않음 |
| PAID11 | 같은계정두구매/무료/재응시 증거 분리, 중복/역순·command/inbox만료 후 증거조회 |
| PAID12 | refund/reflow/원본삭제뒤 과거source 불변, missing legacy증거 UNKNOWN |
| PAID13 | 새revoke trust/audience·wrongservice/env/unsigned/query/OFF/direct negative 및 업무무호출 |
| PAID14 | 정상만료D1·free owner/epoch/phonecontinuation/AI examId/S3/Redis 회귀 불변 |

## 9. LC 회신에 필요한 항목

- reserve/status의 entitlementSource 필드/enum/legacy 처리와 reader-first 배포 가능 여부
- exact confirmedSessionId 완료 예외/terminal·기한/guard 및 현재Job·status-first 흐름과의 적합성
- 새 revoke route/DTO/오류/인증·환경 trust/준비flags·담당자 선택
- 기존 상태event v1 유지 및 paid Session에도 같은완료증거 발신 가능 여부
- 양측 구현범위/공동fixture 담당/테스트 및 미지원 task drain·롤백 절차

회신 수락 전 본 초안을 동결 계약/테스트 통과/상대 구현 또는 배포 승인으로 보고하지 않는다.
