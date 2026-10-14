# TMI-217 — 시험 삭제·과거 owner·자료별 보존 공동 계약안 R1

- 날짜: 2026-10-14 / revision: `lc-billing-exam-deletion-retention-r1-draft-1`
- 상태: **공동 검토용 제안**. 기존 승인 정책을 구체화하며 신규 wire·보존 수치의 공동 수락/제품 구현/활성화 완료가 아니다.
- 관련: TMI-217, PLAN-011/012/013, LC TMI-116/128/122/125/193/136.
- 기준: [최신 단순화 exact](LC-BILLING-PAID-EXAM-SIMPLIFIED-EXACT-R1.md), [범위 한정 수락](LC-BILLING-PAID-EXAM-SIMPLIFIED-R1-ACCEPTANCE.md), [삭제 정책](PLAN-011-J4-S15-V07-DECISIONS-2026-10-14.md). 과거 삭제 queue/무료 continuation 복구/영구 key 보장 제안은 사용하지 않는다.

## 1. 5줄 결론

1. 생성 중 삭제는 기존409로 미접수한다. 접수된 삭제는 LC 원문 정리와 Billing 전달을 분리하고, 기존 삭제 작업에서 재시도한다.
2. 새 POST 하나로 exact Session 삭제 사실을 전달한다. 현재 owner와 과거 Session owner를 구분하고 과거 owner의 삭제로 이전받은 target 권리를 차단하지 않는다.
3. 새 일반 소유권 이력 시스템 대신 생성 시 최소 불변 owner 근거를 보존하는 안이다. 현재 epoch 숫자 또는 짧은 phone fence만으로 과거 귀속을 추정하지 않는다.
4. 상세120일과 금융5년/Claim3년/backup35일을 구분한다. 무료 소비·복원·삭제 deny의 최종 폐기 조건은 표에서 별도 검토 대상으로 표시한다.
5. 아래 exact wire·오류·경합표·검증표를 LC와 검토한 뒤 새 fixture revision을 만든다. 기존 A/R2/simplified bytes는 수정하지 않는다.

## 2. 반드시 읽어야 하는 내용

### 2.1 확정 정책과 이 초안의 경계

- 명시 삭제는 해당 시험의 재응시 포기이며 이미 쓴 무료권을 돌려주지 않는다. 다른 무료권·유료 기간·이미 지급된 복원권은 유지한다.
- 기존 생성 operation이 실행 중이면 삭제 command/전달 queue를 만들지 않는다. LC 동일 사용자 fence를 생성과 삭제가 같은 Transaction에서 쓴다. 삭제 선행이면 신규 생성을 막고, 생성 선행이면 삭제를 미접수한다.
- 접수된 삭제 이후 개인정보 원문/S3 정리는 Billing 장애 때문에 기다리지 않는다. Billing에 보낼 최소 귀속/대상 증거를 원문 제거 전에 영속한다.
- 일반 전환의 기접수 채점·Summary 완료 허용보다 명시 삭제/lifecycle deny가 우선한다. 삭제 자료를 재생성하지 않는다.
- 같은 key 보장120일 뒤 과거 UUID 신규 처리 위험은 이미 수용했다. 영구 key·서명 시작권·새 회원상태 API를 요구하지 않는다.
- 본문에 `제안`으로 표시한 route/audience·owner 저장·오류·폐기 조건은 현재 서버 기능으로 보고하지 않는다.

### 2.2 LC와 Billing 분담

| 단계 | LC | Billing |
| --- | --- | --- |
| 접수 | 현재 actor/탈퇴 deny·생성 guard 확인, 기존 삭제 operation 재사용 | 호출 없음 |
| 원문 삭제 전 | 대상 inventory·불변 event payload·재시도 근거 저장, 접근/신규 제출 차단 | 호출 없음 |
| 삭제 전달 | 아래 POST, 매 시도 새 JWT/jti, payload/eventId는 동일 | 소유 근거·멱등성 확인, Session deny와 필요한 현재 group 차단을 한 Transaction에 commit |
| 원문 정리 | 기존 삭제 worker가 진행. 전달 ACK와 독립 | 답안/음성/요약 저장 안 함 |
| 재시도 | 기존 삭제 operation/target별 전달 상태 활용, 새 복구 시스템 불필요 | 중복204, 충돌은 원 근거 보존 |
| 새 생성 허용 | 필요한 Billing-linked 대상 ACK 및 기존 생성 없음 확인 뒤 삭제 fence 해제 | 차단된 원 group를 재사용하지 않는 INITIAL 판정 |

`CONTENT_PURGED`와 `BILLING_ACKED`는 논리적 완료 조건이다. enum/collection을 전부 새로 만들라는 요구가 아니다. Billing 미연결 legacy 시험은 서버측 exact manifest로 구분하며 가짜 group/consumption을 만들지 않는다. identity 탈퇴는 기존 lifecycle 흐름을 유지하고 이 삭제 route로 대체하지 않는다.

## 3. 공동 확정할 exact 제안

### 3.1 전송과 body

제안 route: `POST /internal/v1/exam-deletions/events`. query는 빈 component도 금지, redirect 금지, UTF-8 JSON raw body 최대16,384bytes(정확히16,384 허용), 압축 body는 지원하지 않는다. Idempotency-Key header를 새 source로 쓰지 않고 body eventId로 dedupe한다. 일반 tracing header는 허용하되 owner 근거로 사용하지 않는다.

| 필드(동시에 canonical 순서) | 형식/의미 |
| --- | --- |
| eventId | lowercase UUID v4, exact Session별 한 번 생성 |
| schemaVersion | JSON 정수1, 문자열/소수/boolean 금지 |
| userId | lowercase canonical UUID, LC가 검증해 삭제를 접수한 actor. 현재 Billing owner로 덮어쓰지 않음 |
| deletionOperationId | lowercase UUID v4, LC의 원 삭제 operation. 같은 작업에 여러 Session 가능 |
| attemptGroupId | lowercase UUID v4 |
| sessionId | 기존 opaque ID 규칙(1~128자); Billing 원 Session과 exact match |
| deletedAt | UTC `YYYY-MM-DDTHH:mm:ss.SSSZ`, LC 접근 차단이 commit된 시각을 한 번 고정 |

필수7필드 외 unknown/duplicate/trailing/null/타입 coercion/손상 UTF-8/lone surrogate는400. schemaVersion2처럼 문법상 유효한 미지원 정수는422. userId는 body를 보낸 클라이언트가 아니라 인증된 LC의 증언이며 Billing에 저장한 귀속과 대조한다. deletedAt을 처리 순서·owner 이전의 단독 증거로 사용하지 않는다. future skew를 근거로 권리를 이전하지 않으며 비정상 시각은 관측·운영 확인 대상이다.

canonical은 R2 scalar escaping 규칙을 재사용한다. 필드 순서 위 표, 공백 없음, UTF-8/BOM 없음, Unicode normalization 없음, quote/backslash escape, U+0000~001F는 소문자 `\u00xx`, 나머지 scalar는 그대로. 전체 canonical bytes SHA-256 lowercase hex를 payloadDigest로 사용한다. 사실 비교는 정규화된7필드 전체로 한다. eventId도 최초 값으로 고정한다. digest를 금융 증명이나 인증 대체로 쓰지 않는다. wire body 순서는 자유다.

### 3.2 인증·포트·기동 제안

| 항목 | 제안 |
| --- | --- |
| issuer / sub | 기존 LC `urn:tosunsaeng:workload:learning-core:{env}` / `learning-core-service` |
| 목적 audience | 신규 `tosunsaeng-billing:exam-deletion-events:{env}` 단일 문자열 |
| 키 | 기존 환경별 LC 전용 공개 keySet/LC signer 재사용. Identity·Billing 역방향 키와 혼합 금지 |
| 시간/문법 | 기존 A RS256·TTL1~60초/skew30초·나노초 now·Bearer exact/JOSE strict 규칙 그대로 |
| connector | 기존 workload TLS connector, localPort 기준; 전달 헤더로 선택 금지 |
| Billing business gate 후보 | `app.billing.exam-deletion.enabled` / `BILLING_EXAM_DELETION_ENABLED`, 기본false |
| ON 조건 | LC receiver/키/TLS 준비, 삭제 schema·귀속 coverage·생성/삭제 공통 fence writer 준비. paid 대상은 paid runtime/recovery 조건도 요구 |
| LC 준비 | 기존 JWT sender에 새 audience 및 삭제 전달 gate 필요. 명칭은 LC 구현계획에서 공동 확인 |

새 audience token은 새 route에만 허용한다. 기존 reservation/attempt audience로 삭제를 호출하거나 삭제 audience로 기존 route를 호출하면 인증이 유효해도403이다. 기존 A 여섯 POST 목적표와 fixtures는 그대로 두고 별도 addendum/새 fixture로 일곱 번째 route만 확장한다.

인가 우선순위 제안: 새 exact route에서 OFF 또는 잘못된 connector는 body없는404/no-store → ON 정상 connector의 invalid token/Bearer/query는401 → 유효 principal의 subject/audience/서비스/method 불일치는403 → 권한 통과 후 body검사/업무처리. URI의 trailing slash/encoded 우회는 새 route alias로 받아들이지 않는다. 사용자/Identity의 기존 route를 새 matcher가 가로채지 않는다. unknown path 및 공유 TLS ON/OFF는 기존 chain 결과를 보존하고 inventory 테스트로 확인한다. TLS 미기동 연결 실패는 HTTP404가 아니다.

### 3.3 응답·오류와 검사 순서

인증/인가 → 크기/JSON/필드 형식 → 지원 schema → exact 귀속 → eventId 충돌 → exact Session 삭제 사실 충돌 → Transaction/기존 commit 확인 순서다. unknown field와 unknown schema가 함께면400이다. 현재 lifecycle이 탈퇴여도 이미 정당하게 접수된 원문 삭제 전달은 불변 귀속이 입증되면 적용 가능하다는 제안이며, ACTIVE로 되돌리거나 시험 접근을 허용하지 않는다.

업무 오류 body는 `{code,message,retryable,correlationId}` 네 필드, correlationId는 불투명 요청 추적값이다. retryable=true인503만 Retry-After:1, 나머지 업무 오류에는 없으며 모두 no-store. 인증 계층 오류는 body없는 기존 형식을 따른다.

| HTTP / code | exact message | retryable / LC 처리 |
| --- | --- | --- |
| 204 | body 없음 | 최초/동일 중복, commit 확인 뒤 ACK |
| 413 INVALID_REQUEST | The request is invalid. | false / 격리·본문 생성 확인 |
| 400 INVALID_REQUEST | The request is invalid. | false / 계약 오류 |
| 422 UNSUPPORTED_CONTRACT | The contract is not supported. | false / 계약 오류 |
| 409 EVENT_ID_CONFLICT | The event ID conflicts with an existing event. | false / 충돌 격리 |
| 409 DELETION_FACT_CONFLICT | The deletion fact conflicts with an existing deletion. | false / 충돌 격리 |
| 409 OPERATION_OWNER_MISMATCH | The operation owner does not match the current owner. | false / owner 확인; 인증 circuit 변경 아님 |
| 409 DELETION_TARGET_UNRESOLVED | The deletion target cannot be resolved. | false / 운영 확인; 대상 없음≠성공 |
| 503 BILLING_TEMPORARILY_UNAVAILABLE | Billing is temporarily unavailable. | true / 같은 사실 재시도 |

신규 오류/INVALID_REQUEST 문구는 이 endpoint의 제안이며 기존 endpoint 문구를 일괄 변경하지 않는다. LC의 생성 중 삭제 공개409/Retry-After:1·`EXAM_CREATION_PROCESSING`, 삭제된 성공 key의 공개404/`EXAM_4004`는 기존 simplified exact를 유지한다. 내부 unresolved를 공개 삭제 성공 또는 미소비로 변환하지 않는다. unknown commit이면 원 event를 보존하고 동일 재전송으로 확인한다.

### 3.4 owner 증거와 적용 범위

현재 AttemptSession에는 subjectRefId/operationId/sessionOwnerEpoch가 있지만 불변 actor userId가 없다. epoch 숫자만으로 과거 user를 알아낼 수 없고 `SubjectOwnerRebind` sourceUserId는 정리되는 짧은 fence다. MEMBER receipt의 account version은 owner epoch가 아니다.

제안: 신규 Session 생성 Transaction에 최소 내부 `creationOwner`를 저장한다. `{kind,userId,reference,epoch}`이며 TRIAL_SUBJECT는 subjectRefId+양의 epoch, MEMBER_ACCOUNT는 accountId+epoch=null. environment는 신뢰 runtime scope로 결속한다. Session/group/원 reserve가 동일 대상을 가리켜야 한다. Trial의 과거 merge로 actor가 바뀐 경우는 원 snapshot 또는 **기존 증거로 검증해 기록한** 별도 이전 사실이 필요하다. 현재 owner를 backfill해 과거 owner로 위장하지 않는다. 전체 회원 이력/계보 시스템은 추가하지 않는다.

| 대상·귀속 | 결과 제안 | 영향 범위 |
| --- | --- | --- |
| 현재 Session owner와 creationOwner 입증 |204| exact Session deny. 같은 귀속의 현재 사용 group면 재응시·최초trial 복원 자격 차단 |
| 같은 owner의 더 오래된 Session, 이미 새 Session 있음 |204| 그 Session만 deny. 최신 Session/group/guard는 유지 |
| phone 이전 source A의 Session, 현재 target B |204(과거 귀속 입증 시)| A Session만 deny; B group/continuation/paid/복원권은 차단하지 않음 |
| target B가 A의 과거 Session 삭제 주장 |409 owner| 현재 owner만으로 과거 학습자료 삭제권을 얻지 않음 |
| MEMBER paid/복원권 |204(원 account/grant 귀속 입증 시)| 해당 Session/현재 group만. phone trial subject로 위장 금지 |
| 탈퇴 후 지연 전달 |204(원 귀속 입증 시)| 삭제만 수행, ACTIVE 복구/다른 계정 권리 변경0 |
| 알 수 없는 group/session 또는 서로 다른 대상 |409 unresolved| 원 payload 보존, 자료 부재로 성공 추정 금지 |
| legacy 귀속 부족/여러 owner 증거 충돌 |409 owner| 자동 차단/추정 backfill 금지, coverage 운영 확인 |

과거 source의 지연 삭제를 target의 재응시 포기로 해석하지 않는 좁은 범위를 공동 검토한다. 삭제가 phone rebind보다 먼저 commit되면 원 재응시 차단이 rebind discovery에도 적용돼야 한다. 반대 순서면 이미 이전된 target 권리는 보존한다. wall-clock deletedAt으로 commit 순서를 뒤집지 않는다. 원 consumption 복원1회는 어떤 순서든 유지한다.

### 3.5 저장·Transaction·경합 제안

별도 범용 inbox+queue를 추가하지 않고 `exam_deletion_facts` 하나를 논리 inbox/원 사실로 사용한다. 최소 field는7-field payload, environment, payloadDigest, disposition(`SESSION_ONLY`/`CURRENT_GROUP_BLOCKED`), verifiedOwner, appliedAt, version. unique(environment,eventId), unique(environment,sessionId), 인덱스(environment,deletionOperationId). 실제 Mongo validator/index와 metadata 제거는 구현 전 설계한다.

귀속 확인에 성공한 같은 eventId·같은7필드는204, 같은 eventId·다른 내용은409 EVENT_ID_CONFLICT, 같은 Session에 다른 eventId/deletionOperationId/deletedAt이면409 DELETION_FACT_CONFLICT를 제안한다. LC는 중복 삭제 API 호출에서도 retained 원 fact를 찾아 같은 eventId로 보낸다. **종전 미수락 D1 후보의 다른 eventId 동일사실204를 이 초안에서는409로 단순화한다.** 별도 alias 목록·두 번째 dedupe 저장소가 불필요해지는 대신, sender가 eventId를 분실하면 운영 확인이 필요하다. 기존 동결 wire를 바꾸는 것이 아니며 LC 수락 전 구현하지 않는다.

한 ClientSession에서 귀속 검증, unique fact 삽입, exact Session deny, 필요한 현재 group 차단, 자기 group/session만 일치하는 current guard 해제를 수행한다. 삭제는 CONFIRMED를 취소하거나 금전/소비/지급 원장을 역분개하지 않는다. 다른 pending operation을 해제하지 않는다. 상태 event writer도 같은 Session deny를 검사해 삭제된 학습상태를 새 완료로 재생성하지 않는다. 이미 저장된 최소 금융 이용 사실은 유지한다.

| 경합 | 먼저 commit한 결과와 후속 처리 |
| --- | --- |
| LC 생성/삭제 | 생성 선행이면 삭제 미접수409; 삭제 선행이면 새 생성 차단 |
| Billing 삭제/유료 confirm | pending reserve가 먼저 있으면 삭제503·원 금융 결과 수렴. 삭제가 reserve보다 먼저 commit되면 §3.6의 INITIAL로 새 권리 판정·삭제원인 복원0. confirm 선행 뒤 삭제는 지급 유지. 같은 group/link/guard 쓰기 fence로 판정 |
| 삭제/상태 완료 | 삭제 선행이면 새 학습 완료 적용 차단. 완료 선행 최소 금융 이력 유지, 학습자료는 삭제 |
| 삭제/phone rebind | 같은 subject/link·group 쓰기 fence. 삭제 선행 재응시 차단 유지, 이전 선행 과거 Session만 삭제 |
| 삭제/unknown commit | receipt 재조회 또는 같은 event 재전송; 새 event 생성/묻지마204 금지 |
| 중복/서로 다른 삭제 fact | unique+Transaction으로 같은 event·같은 사실204 또는409, 부분 차단 없음 |

Billing pending reserve가 이미 있는데 LC가 생성 없음으로 오판한 경우를 정상 삭제로 흡수하지 않는다. 현재 사용자 pending guard를 삭제와 같은 Transaction에서 확인하고 503으로 수렴을 기다린다. 정상 LC 경합 방지와 별도의 서버 방어다. worker가 새 reserve를 발행하지 않는다.

### 3.6 삭제 후 새 시험과 기존 wire의 접점

LC는 원문이 삭제돼도 최소 previous group/session을 새 생성 요청 준비 전까지 보존하고 기존 restartContext로 보낸다. startReason enum을 추가하지 않는다. 명시 삭제 fact가 확인된 현재 원 대상이면 Billing이 RESTART_INCOMPLETE에서도 INITIAL/새group/새Session/기존 transition 형태로 판정한다. 원 권리의 무료 replacement/삭제를 원인으로 한 복원은 금지다. 사용할 새 권리가 없으면 기존 권리 부족으로 거절한다.

현재 `PaidReservationService.previous()`는 deleted/replacementBlocked를 일괄 conflict로 처리하므로 이 정상 INITIAL 분기는 **아직 구현되지 않았다**. 정확한 삭제 fact·현재 귀속과 일반 차단 사유를 분리해야 하며 flag만 보고 모든 차단을 우회하면 안 된다. 현재 guard만 지우고 restartContext를 생략하는 우회는 금지다. 원 대상 최소 context의 최종 폐기 후 새 INITIAL 허용 범위는 아래 폐기 gate와 함께 결정한다.

## 4. 자료별 보존과 남은 운영 결정

D=10,368,000초, UTC millisecond. `now >= deadline`부터 기간 충족. 재시도/증거 refresh/clock rollback으로 최초 시각을 갱신하지 않는다. `기간 충족`과 `실제 폐기 가능`은 다르며 진행/hold/귀속·권리 차단 의존을 함께 검사한다.

| 자료 | 기산점·기간 | 폐기 조건/상태 |
| --- | --- | --- |
| LC/Billing terminal 상세 operation/receipt | LC max(firstAcceptedAt+D,terminalAt+D); Billing terminalAt+D 및 최초receipt+D 하한 | 기존 exact 승인. 미완료 연계·hold 없을 때 상세만 제거 |
| 미완료 operation/guard·미ACK 삭제 fact | terminal 없음 | 자동TTL 없음; 기존 조기 격리 우선,120일 자동복구 상한 뒤 운영 확인. 담당·사유·검토기한·종료증거 필수 |
| 전달 완료 삭제 상세(event/오류/시도 기록) | 제안: LC ackedAt+D, Billing appliedAt+D | 원 deny/귀속·미완료 재생성 방지 근거를 별도 최소 형태로 남길 수 있을 때만 상세 축소 |
| Session/group 삭제 deny·creationOwner·consumption 연결 | fixed TTL 미제안 | 해당 재응시/복원/현재 권한 판정에 필요 없고 미완료/hold 없음이 확인된 뒤 제거. 무기한 개인 연결 승인 아님 |
| 무료 소비·복원1회 unique | 현재 무기한 무료권과 연계 | 삭제된 상세 receipt와 함께 지우지 않음. 원 권리가 더 이상 사용·복원 판정 대상이 아니게 되는 조건과 개인 연결 제거 방식 공동 확정 필요 |
| 구매·기간권·금전/이용 원장 | 기존 최종 provider transaction/권리 종료/마지막 reversal 중 최후 시점+5년 | 기존 금융정책 유지. 학습 원문 보관 근거로 사용 안 함 |
| TrialClaim/alias | claimedAt+3년 | 기존 logical expiry 및24시간 active DB purge; 삭제/복원으로 기산점 연장 안 함. 복원 grant의 목적별 최소 연결은 별도 검토 |
| legacy exact manifest | 대상별 drain/삭제/미완료 해소 확인 | 활성화 cutover coverage 및 구 producer 재유입 검사 후 대상별 제거; 영구 key 저장 요구 아님 |
| 답안·음성·피드백·Summary | 기존 LC 삭제정책 | Billing ACK/금융5년을 이유로 지연·복제 금지 |
| backup | 기존 최대35일 | 복구 후 원 시각 유지, 최신 deny/expiry/purge·미완료 정합성 확인 후 트래픽 |
| Identity 탈퇴 원천 | 기존 Identity 정책 | 이 문서의120일로 변경하지 않음 |

새로운 개인정보 최종 보존기간은 운영/정책 담당 검토 전 자동 purge로 구현하지 않는다. 미완료/분쟁 예외마다 owner·최소항목·사유·reviewDueAt·종료조건을 남기고 완료 시 원 기산점으로 재평가한다. RC 고객 삭제5영업일/30일 후보는 이 계약 대상이 아니며 승인됐다고 간주하지 않는다.

120일 상세 폐기 뒤 과거 key 신규 처리 위험 수용은 유지한다. 하지만 살아 있는 무료권을 다시 복원하거나 삭제 deny를 무시할 수 있게 최소 금융/권한 증거까지 지우는 승인은 아니다. 상세 멱등 증거와 권리 판정 증거의 제거를 분리한다. 백업/producer drain 검사는 살아 있는 권리·삭제 안전성 범위이며 모든 과거 UUID를 영구 추적하라는 요구가 아니다.

## 5. 검증·공동 회신 항목과 다음 작업

[검증 계획](../plans/TMI-217-DELETION-OWNER-RETENTION-VERIFICATION.md)에 wire·인증·owner·경합·보존별 케이스를 정의했다. 아직 fixture bytes/token/hash 또는 실행 PASS는 없다. LC 회신에서 다음을 확인한다.

1. 7-field body/새 목적 audience·OFF/connector·오류 exact와 공개409/404 유지.
2. 최소 creationOwner coverage, 과거 source Session-only 삭제, 현재 group 차단 및 phone 양순서.
3. eventId 불변 의무와 다른ID 동일Session을409로 거절하는 단순화안.
4. ACK 전 신규 생성 차단·원문 정리 독립·삭제 후 restartContext INITIAL의 구현 위치.
5. 상세120일 제안과 무기한 무료권/복원1회 최소 증거의 최종 개인 연결 제거·운영 책임.

공동 회신 반영 → 범위 한정 수락 → 별도 fixture revision 생성/manifest·SHA256SUMS·ZIP → Billing 검사·LC bytes 대조/자체 독립 검증 → 제품 구현·실Mongo 경합/HTTP 검사 → 실환경 gate 순서다. LC 회신을 대신 작성하거나 상대 수락을 선언하지 않는다. 별도 route/owner/purge가 미확정인 영향 경로는 활성화하지 않는다.

## 6. 조사 근거와 실행 범위

- Billing `AttemptSession`: sessionOwnerEpoch는 있으나 불변 userId 없음. `EntitlementSourceSnapshot`은 source 참조이며 owner 증거 전체가 아님.
- `MemberOperationRecovery`/`PaidExamSchema.owner`: MEMBER_ACCOUNT reference/version, account version 증가를 owner 전이로 간주하지 않음.
- `SubjectOwnerRebind`: sourceUserId/exact group/session/legacyFenceExpiresAt 및 purgeAt. 장기 삭제 전달의 유일 근거로 부적합.
- `PaidReservationService.previous/restore/blockPrevious`: 기존 deleted/lifecycleDenied/replacementBlocked 검사 및 복원1회/현재group 경계 확인.
- `PaidAttemptEventService`: 현재 group 단위 deleted 검사. Session-only delete deny 지원은 후속 구현 필요.
- `WorkloadIngressFilter`/`WorkloadJwtVerifierRegistry`: 현재 LC 여섯POST/두 audience, 새로운 route 미등록. 기존 분리 verifier/connector를 확장하는 제안.
- LC HEAD `001b575`의 `DeletionCommandService`/`DeletionWorker` 정적 읽기: 기존 삭제 command/owner guard, target seal·continuation retain 경로 확인. 전체 LC P3 이후나 배포 상태를 검증한 것이 아니다. LC 파일 수정 없음.
- Jira TMI-217 원문은 제품 코드 아닌 공동 계약 준비 과제로 명시한다. 이번 브랜치에 문서만 작성. 실제 DB/키/네트워크/외부 메시지·Jira 변경 없음.
