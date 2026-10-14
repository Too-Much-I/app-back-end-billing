# PLAN-011 남은 기술안 — LC·Identity 회신 반영 R2

> 2026-10-14 최신 사용자 승인: [단순화 결정](PLAN-011-SIMPLIFICATION-DECISION-2026-10-14.md). 재가입 유료 우선·원 소비당 target 복원1회 유지, 생성 중 삭제 미접수·앱 MEMBER 보강·120일 멱등 보장/종료 상세 기록 정리 채택. 이후 과거 key 신규 처리 위험 수용, 금융/소비/미완료 증거 보존은 유지. 아래 대기열 삭제/영구 key 보장 전제는 이 범위에서 대체하며 구현·공동 exact는 후속이다.

- 날짜: 2026-10-14 / revision: plan-011-remaining-technical-response-r2-draft-1
- 상태: 양측 정적 검토 접수·기술 보완 제안. 공동 수락/fixture 제작/구현/배포 승인 아님.
- 관련: PLAN-011/012/013, TMI-116/TMI-128/TMI-122/TMI-125, LC 삭제 TMI-193/TMI-136.
- 기준: [기술안 R1](PLAN-011-REMAINING-TECHNICAL-PROPOSAL-R1.md), [승인 정책](PLAN-011-J4-S15-V07-DECISIONS-2026-10-14.md). 아래 보완이 R1의 해당 기술 후보보다 우선한다.

## 1. 5줄 결론

1. M1 기존 purchase-account JWT 증거 경로는 양측 방향 이견 없음으로 접수한다. 운영 issuer/flags와 실제 저장 구현은 미검증이다.
2. 삭제는 접근 차단·payload 보존·원문 정리·Billing ACK·신규 생성 허용·작업 완료를 분리하고 진행 생성의 복구를 자기 deny로 막지 않는다.
3. phone INITIAL의 echo/원 대상/새 group와 복원 수혜자를 명시하되 소유 이전 승인 확대 여부는 공동 확인한다.
4. 오래된 UUID의 신규 재입력은 LC가 새 issuedAt을 붙여도 탐지되지 않는다.120일만으로 tombstone 폐기를 승인하지 않는다.
5. M1/D/P 기술안은 아래 제안대로 검토하고, V07의 최초 key 증거와 폐기 후 계약은 별도 결정이 필요하다.

## 2. 사용자가 반드시 읽어야 하는 내용

Identity는 검증된 sub/issuer/audience/시간/scope/account_type에 근거한 MEMBER 증거 저장 방향이 로컬 발급 구현과 맞는다고 회신했다. billing:purchase는 발급 fence 승인 때 합성되며 신규 claim/매 시험 조회를 추가할 필요는 없다는 범위다. 환경별 실제 활성화·issuer/JWKS는 확인되지 않았다. AUTH_PURCHASE_SOURCE_STREAM_ID를 JWT에서 얻거나 추정 저장하지 않는다.

MEMBER 증거는 최초 계정 유형 근거이지 현재 ACTIVE 인증 토큰이 아니다. 탈퇴 전 발급된 유효 token/전파 지연을 기존 deny·push/feed·recovery fence와 함께 처리한다. 미래 유형 정책이 바뀌면 evidenceSchemaVersion과 기존 증거 사용 조건을 재검토한다.

## 3. 기술 보완안

### 3.1 M1 보강·오류·재개

- 신규 paid 및 기존 paid/MEMBER_GRANT에 memberEvidence가 없으면 내부503 `MEMBER_EVIDENCE_PENDING`, retryable=true, Retry-After:1을 제안한다. 기존4-field 오류 형식을 유지하고 ENTITLEMENT_INSUFFICIENT/GUEST로 변환하지 않는다.
- LC 공개 응답은 기존503 BILLING_TEMPORARILY_UNAVAILABLE·Retry-After:1을 유지하는 안이다. 원인별 앱 안내에 별도 필드가 필요하면 공개 계약 변경으로 분리한다. 앱은 로그인 후 구매 기능 준비 시 기존 purchase-account를 호출해 선보강하고, 단순503마다 무제한 호출하지 않는다.
- worker는 해당 pending에서 같은 operation/guard/evidence를 보존한다. 현재 status-only worker의 reserve 재발행 금지는 유지한다. 예약 생성 전 pending이면 앱의 같은 key HTTP 재시도로만 reserve가 재개되며, 예약이 이미 있으면 기존 단계별 confirm/status 복구를 따른다. worker가 새 key/새 reserve를 만들지 않는다.
- 기존 account 성공 조회도 동일 Transaction에서 증거를 저장한다. stable purchaseAccountRefId 유지, WITHDRAWN deny·복구 coverage fence 우선, 계정/구매 존재만으로 자동 backfill 금지.
- Identity 검증은 전달 보고에 근거한다. 실제 배포 issuer/JWKS·purchase flags/control/source readiness 확인 전 운영 완료로 보고하지 않는다.

### 3.2 D1 checkpoint·교착 방지

| checkpoint 후보 | 의미/진행 조건 |
| --- | --- |
| REQUEST_RECORDED | 명시 삭제 요청·대상 범위 보존, 새 생성 진입 차단. 기존 생성의 금융 결과 복구는 허용 |
| CREATION_COORDINATED | 기존 생성이 있으면 같은 operation으로 결과 수렴. 삭제 때문에 생성 복구를 막거나 삭제를 무조건 기다리게 하지 않음 |
| ACCESS_FENCED | 원 exact 대상 접근/신규 제출 차단 commit. 이 시각을 immutable deletedAt로 고정 |
| DELIVERY_RETAINED | Session별 eventId/최소 payload·owner 증거 영속. 다중 inventory는 대상별 한 번 생성 |
| CONTENT_PURGED | 원문/S3/Mongo 정리 진행. Billing ACK와 독립적으로 수행 가능 |
| BILLING_ACKED | 원 대상 Billing 차단 commit204를 확인 |
| NEW_CREATION_ALLOWED | 필요한 대상 inventory/쓰기차단/전달증거 완료·Billing ACK 및 기존 생성 수렴 확인 후 새 학습 허용 |
| COMPLETED | 원문 삭제와 Billing 전달이 모두 끝남. 원문 정리 완료/전달 대기를 별도로 관측 |

이는 기존 stage 위 논리 checkpoint 제안이며 새 enum 전부를 추가하라는 요구가 아니다. 기존 DeletionAccess의 일반 writeBlocked가 생성 finalize까지 막을 수 있으므로, 삭제보다 먼저 확보한 **exact 생성 operation**만 금융 상태 조회/복구·필요 terminal 수렴을 허용하는 좁은 내부 경로가 필요하다. 사용자 제출이나 다른 생성 작업에는 적용하지 않는다. 삭제 대상이면 금융 성공을 기록해도 공개 학습 접근/삭제 자료 재생성을 허용하지 않는다. 현재 코드가 이 경로를 이미 지원한다고 보고하지 않는다.

삭제 요청 재시도는 동일 logical 삭제의 기존 operation/eventId/deletedAt를 재사용한다. 같은 Session에 새 operation을 매번 만들지 않는다. 정리 완료 후 다시 삭제 요청이 오면 retained 결과로 no-op 수렴한다. 원문 정리는 전송 장애로 무한 정지시키지 않고 최소 전송 증거만 보존한다.

### 3.3 D1 동일사실·오류·귀속

R1 body7필드를 유지하는 후보에서 canonical 순서는 eventId/schemaVersion/userId/deletionOperationId/attemptGroupId/sessionId/deletedAt다. 동일 eventId digest는 모든 field를 포함한다. 다른 eventId의 동일사실은 eventId만 제외한 **schemaVersion/userId/deletionOperationId/group/session/deletedAt 전체** 일치다. 별도 semantic hash를 추가할 필요 없이 정규화 field를 비교한다. 타임스탬프/ID를 재시도 때 다시 만들지 않는다.

검사 순서: 서비스 인증/route → 크기 → strict JSON·형식 → 지원 schema → caller/과거 owner 귀속 → eventId conflict → exact 대상 사실 conflict → commit/기존 commit 확인204. 인증되지 않은 요청에 dedupe 결과를 공개하지 않는다.

| 상황 | 제안 응답 |
| --- | --- |
| 최대16KiB 초과 | 413 INVALID_REQUEST, retryable=false |
| malformed/duplicate/trailing/unknown/필수·타입 오류 | 400 INVALID_REQUEST, false |
| 형식은 맞지만 미지원 schema | 422 UNSUPPORTED_CONTRACT, false |
| 같은 eventId 다른내용 | 409 EVENT_ID_CONFLICT, false |
| 같은 group/session에 다른 삭제 사실 | 409 DELETION_FACT_CONFLICT, false |
| owner 근거 불충분/비허용 | 409 OPERATION_OWNER_MISMATCH, false; owner-blocked, auth circuit 변경 없음 |
| group/session 미존재 | 409 DELETION_TARGET_UNRESOLVED, false; needs-review·최소 payload 보존, 성공/삭제무관으로 추정 금지 |
| DB 불가용 | 503 BILLING_TEMPORARILY_UNAVAILABLE, true·Retry-After:1 |
| 최초/동일 사실 중복 | commit 확인 뒤 body없는204 |

신규 오류는 제안이며 LC category/재처리 규칙과 fixture로 수락해야 한다. 미존재 대상은 정상 타이밍 지연 여부를 status/기존 생성 복구로 확인한 뒤 같은 사실로 승인 재개할 수 있으나 임의 자동 무한 retry는 하지 않는다.

과거 owner 판정은 Billing의 AttemptSession.sessionOwnerEpoch와 생성 당시 owner snapshot/기존 source event fence를 먼저 사용한다. 현재 userId만 비교하지 않는다. 기존 자료로 owner/epoch를 입증할 수 없는 row는 성공 적용하지 않는다. 추가 body ownerEpoch가 필요한지는 이 coverage 조사 후 결정하며 근거 없는 값을 채우지 않는다. 검증된 과거 source owner의 삭제도 그 exact Session 사실만 반영하고 현재 target Session/다른 group 권리를 종료하지 않는다.

삭제 route의 issuer/sub/audience/port/OFF/query/auth 인가 exact와 old/new allowlist는 별도 A 보완 계약이 필요하다. Reservation audience 재사용은 아직 확정되지 않았다.

### 3.4 D3 삭제 후 replay

명시 삭제 fact가 있으면 RESTART_INCOMPLETE의 일반 REPLACEMENT보다 우선하여 새 key는 INITIAL로 판정한다. source/transition은 기존 일반 restartContext INITIAL 규칙대로 새group·exact 원대상을 대조한다. 과거 operation의 금융 성공은 그대로지만 LC는 성공 replay 전에 삭제 deny를 확인한다.

**공개 응답 제안:** 삭제된 Session의 과거 성공 key는404와 기존 LC 시험 없음 응답으로 수렴시키고 Session을 재생성하지 않는다. 정확한 기존 code/envelope를 LC가 확인해야 하며 임의 새 public code는 추가하지 않는다. 삭제 진행 중엔 기존 deletion blocked/pending 계약을 따른다. 새로운 시험은 앱의 의도적 새 key 요청으로만 생성한다. 공개404가 Billing의 취소/미소비 증명은 아니다.

### 3.5 P1 phone snapshot·경합·복원

| 요청/결과 | 응답·검증 제안 |
| --- | --- |
| phone echo + 유효 target paid | INITIAL/SUBSCRIPTION, continuationReason=PHONE_REJOIN/id는 요청과 동일, 새 attemptGroupId는 expectedAttemptGroupId와 다름, transition 없음, mock는 discovery mock |
| phone echo + paid 없음 + 유효 원 무료 | REPLACEMENT/EXAM_PASS, reason/id 동일, attemptGroupId=expectedAttemptGroupId, transition 없음, mock 동일 |
| 같은 operation replay/status | 최초 source/kind/group/mock/reason/id snapshot 그대로, 재선택 안 함 |
| discovery GRADING pending/오류 | paid를 이유로 우회하지 않음. 기존 pending/오류 유지 |
| discovery204 | phone echo 만들지 않음. 일반 INITIAL 권리 선택; 구 source 결과/권리 승계 금지 |
| discovery 뒤 paid 생성/정상 만료 | 신규 reserve Transaction 시점에 선택. 존재 불명/차단 source를 무료로 추정하지 않음 |
| reserve 뒤 paid 만료/revoke | 기존 D1/환불 confirm 규칙 적용. 같은 operation을 free로 다시 선택하지 않음 |

LC는 phone 원 group/id·선정 mock와 실제 반환 새 group/source/kind를 별도 저장한다. paid INITIAL이 구 expected group을 응답에 덮어쓴 것으로 오해하지 않는다. source Session 종료는 target LC의 일반 transition으로 지시하지 않는다.

복원 예제(공동 확인 후보): 원 trial consumption C, source 사용자 A→기존 승인된 phone rebind로 target B가 현재 trial owner가 됨, B의 paid 구매 P로 INITIAL confirm. C가 미완료 최초 trial이고 삭제/복원완료 등 부적격이 아니며 기존 rebind fence가 유효할 때만 **B에게 C당 한 번 새 복원 grant** 지급을 제안한다. A의 기존 grant/paid를 이전하는 것이 아니다. C unique 제약으로 A/B 어느 경로에서든 이미 지급됐으면 새 지급 없음. 원 지급을 B로 재귀속하지 않는다. B→C 재가입 반복도 원 consumption 기준으로 중복 차단한다.

이 수혜자 규칙은 기존 rebind만으로 자동 승인됐다고 볼 수 없으므로 양측 공동 수락 및 필요 시 사용자 정책 확인을 남긴다. 승인 전 phone paid 복원 정상 fixture를 만들지 않는다. 기존 GRADING pending·COMPLETED NOOP/Claim 보존은 유지한다.

## 4. V07 — 폐기 안전성 보정

LC 지적을 수용한다. operation/Session/최소 tombstone을 모두 지운 뒤 앱이 과거 UUID를 보내면 새 PREPARED 시각이 생긴다. 따라서 worker의 원 생성시각120일 검사와 **LC가 새로 붙인 authenticated issuedAt**은 이 재입력을 막지 못한다. R1의 authenticated issuedAt 대안은 최초 key와 원 발급시각의 결속이 보존/검증되지 않으면 유효하지 않다.

| 선택지 | 장점 | 제약 |
| --- | --- | --- |
| A. 기존 UUID 유지 + 최소 최초-key fence 보존 | 앱 wire 변경 최소 | fence 폐기 시 같은 문제가 재발. 목적/기간·개인정보 제거를 따로 승인해야 하며 무기한 저장을 기본값으로 두지 않음 |
| B. 서버 발급·서명된 만료 있는 시작권/키로 전환 | 최초 발급시각을 검증해 만료 후 재입력 거절 가능 | 앱·LC 공개 계약 변경/발급 단계 필요. 재서명 시 과거 UUID 나이 초기화를 허용하면 효과 없음 |
| C. 멱등 보장 기간 종료 후 과거 key를 새 요청으로 허용 | 구현 단순 | 이중 소비/시험 생성 위험. 현재 같은-key 수렴 정책 변경이므로 권장하지 않음 |

현재는 **A의 폐기 안전성 검증 전 자동 purge OFF**를 유지하고, 실제 운영 전 A의 목적별 보존/제거 계약 또는 B 공개 계약 변경 중 하나를 결정해야 한다. 이 보류는 무기한 개인정보 보존 승인이 아니다. 당장120일에 삭제하는 코드를 작성하지 않는다.

상세 terminal receipt120일은 최초 terminalAt부터, 자동 실행 상한120일은 최초 operation 생성부터이며 정각 `now >= deadline`이면 해당 상한에 도달한 것으로 제안한다. 일반 retry는 기존24시간/200회 격리 정책이 먼저 적용된다.120일까지 계속 retry하거나 격리를 자동 해제하지 않는다. 수동 재처리도 남은 authoritative 증거·현재 owner/deny·폐기 정책 확인 후 같은 사실만 처리하고 운영 승인으로 과거 성공을 발명하지 않는다.

진행/분쟁 예외는 원인·담당·검토 예정일·종료 조건을 남기고 매일 overdue를 관측한다. 종료 시 보존 기산점을 무조건 현재로 재설정하지 않고 원 정책상 기산점과 법적/운영 보존 근거를 적용한다. 실제 개인정보 제거 책임은 Billing 금융/receipt와 LC 학습/operation 소유자별로 지정한다. 담당자가 미지정인 예외를 영구 보존 정책으로 쓰지 않는다.

5분/1시간 운영 목표도 보류 후보다. 채택한다면 전파 지연은 최초 durable 발생/접수 증거부터 ACK까지로 측정하고 blocked/불가용도 포함한다. 양측 clock 차이를 고려해 송신측 미ACK age를 기본 지표로 쓰며 마지막 성공시각만으로 미처리를 숨기지 않는다. 기존 Identity20회·Retry-After·blocked 정책을 바꾸거나 재시도 횟수를 초기화하지 않는다.

## 5. 공동 확인 요청과 다음 순서

- M1: 내부 pending 코드·앱 선보강·same-key HTTP 재개 및 worker 무reserve, Identity 발급 조건·환경 확인.
- D: checkpoint/fence 예외·과거 owner 근거 coverage·오류표·중복 규칙·삭제 replay 공개 응답.
- P: phone echo/INITIAL 예외·discovery 경합·복원 수혜 B 규칙과 기존 rebind 범위.
- V: 오래된 UUID 신규 재입력 테스트를 폐기 gate에 추가하고, 최초-key fence 또는 공개 시작권 계약 결정.

새 fixture 목록에 M1 보강/탈퇴선행, 삭제 ACK 불가용 중 원문삭제, 생성/삭제 교착 방지, old owner 지연삭제, 다른ID동일사실, phone INITIAL/status echo, 과거UUID 모든 증거 삭제 뒤 재입력을 추가한다. 미확정 정상 경로를 통과 기대값으로 만들지 않는다. 기존 A/R2/fixtures-2는 변경하지 않는다.

## 6. 근거·실행 범위

LC 첨부 검토 및 사용자 전달 Identity 회신을 접수했다. Identity 코드/환경을 이번에 자체 확인하지 않았다. LC DeletionAccess/생성 saga는 일부 읽기 확인했고 공개404의 exact code는 확인 필요다. 문서/기록만 갱신, 신규 code/route/fixture·제품/DB/배포·외부 전송·Jira·commit/push 없음. 문서 링크/diff·원본 hash 검사, Gradle 미실행.
