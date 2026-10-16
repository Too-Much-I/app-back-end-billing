# TMI-217 삭제 연동 exact R2 draft-1

> 후속: [draft-2](TMI-217-DELETION-EXACT-R2-DRAFT-2.md)에 LC E1~E5 의견을 반영했다. 시간/오류/상태/LC 상세보존의 상충 부분은 draft-2가 우선한다. 두 문서 모두 공동 재확인 전 초안이며 구44개 자료는 그대로 보존한다.

- 날짜: 2026-10-16 / revision: `lc-billing-deletion-exact-r2-draft-1`
- 상태: R2 방향 양측 수락 이후 **저장·오류·fixture 공동 검토안**. 동결/구현/검증 통과 아님.
- 우선: [승인 R2](TMI-217-DELETION-SIMPLIFIED-R2.md) → 본문 → 상충하지 않는 draft-3. 구7필드 형식·canonical·workload 인증은 재사용한다.

## 1. 5줄 결론

1. Billing은 최초 삭제사실과 event별 멱등 receipt를 두 최소 collection으로 분리한다. 큐/ACK API/merge 이력은 추가하지 않는다.
2. 같은event 충돌검사가 이미삭제 판정보다 먼저다. 다른event의 이미삭제 exact대상은 receipt만 추가하고204로 수렴한다.
3. LC는 기존 요청별 target에 불변payload/전달상태를 저장하고, 모든 필요한204 확인 전 새시험을 차단한다.
4. 인증/영구오류는 격리, 일시오류는 같은payload로 재시도한다. 원문정리는 독립 진행한다.
5. 미전달30일TTL 제외, 금융/deny/receipt 보존 분리.120일은 아래 **검토 제안**이며 자동purge 승인 아님.

## 2. 전송·검사·오류

### 2.1 요청

`POST /internal/v1/exam-deletions/events`, query/redirect/압축body 금지, UTF-8 최대16,384bytes. eventId는 **요청의 target별** UUIDv4이고 같은 전송에서만 불변이다. 새 삭제요청은 새event 가능하다. Idempotency-Key header는 source로 사용하지 않는다.

canonical 필드 순서: eventId, schemaVersion, userId, deletionOperationId, attemptGroupId, sessionId, deletedAt. 앞선 draft-3의 scalar/escaping 규칙을 재사용한다. UUID는 lowercase canonical, eventId/deletionOperationId/attemptGroupId는v4, schemaVersion은JSON정수1, sessionId는기존opaque1~128자, deletedAt은UTC millisecond다. unknown/duplicate/null/coercion/trailing/손상UTF8/lone surrogate 거절. userId는 **LC가 인가한 actor에 대한 증언**이지 Billing의 과거merge 재검증 요청이 아니다.

JWT issuer/sub는 기존LC, audience는 `tosunsaeng-billing:exam-deletion-events:{env}`. RS256/TTL1~60초/skew30초/전용TLS·키/포트·Bearer exact는 기존A 규칙, Identity/user/다른purpose fallback 없음. OFF/잘못된connector404 → invalid JWT/Bearer/query401 → 유효주체 purpose/subject/method권한403 → body형식/지원schema → receipt충돌/재현 → exact대상판정 → Tx commit 순서다. 같은event의 retained receipt는 원commit 증거이므로 후속Session이 없어도 동일digest204를 재현한다. 새event는 아래 대상증명을 반드시 거친다.

### 2.2 응답

업무오류는 `{code,message,retryable,correlationId}` 네필드/no-store, 503만Retry-After:1. 인증계층은 기존 body없는 응답을 유지한다. 원문/식별자/내부예외를 message에 넣지 않는다.

| HTTP/code | exact message | retryable |
| --- | --- | --- |
|204|empty body|해당없음|
|400 INVALID_REQUEST|The request is invalid.|false|
|413 INVALID_REQUEST|The request is invalid.|false|
|422 UNSUPPORTED_CONTRACT|The contract is not supported.|false|
|409 EVENT_ID_CONFLICT|The event ID conflicts with an existing event.|false|
|409 DELETION_TARGET_UNRESOLVED|The deletion target cannot be resolved.|false|
|503 BILLING_TEMPORARILY_UNAVAILABLE|Billing is temporarily unavailable.|true|

이route에서는 구 DELETION_FACT_CONFLICT/OPERATION_OWNER_MISMATCH를 사용하지 않는다. 다른group/환경연결불명/현재group영향판정불명은 DELETION_TARGET_UNRESOLVED. Billing 과거merge근거 부재는 오류가 아니다. JSON이 아닌Content-Type은415 INVALID_REQUEST/같은message/false를 제안하며 LC와 새로 확인한다. unknown field+미지원schema 조합은400 우선.

LC 성공은 **empty204만**이다.200/202/비어있지않은204/redirect는성공아님·계약격리.401/403은인증격리,404/405/400/409/413/415/422 및 기타영구4xx는계약/운영격리.429/408/5xx·timeout/I/O는 같은전송재시도.503업무오류의retryable와HTTP분류가 모순이면 계약격리한다. HTML5xx/본문해석실패5xx는 성공추정없이 일시재시도한다.

제안 재시도: 최초시도부터24시간 또는 HTTP시도200회 중 먼저 도달하면NEEDS_REVIEW. attempt는발신전lease/versionCAS로증가, crash도소진허용. backoff는 n번째실패후 min(300초,2^(min(n-1,9))초)에[0,1초)jitter를더한다.429/503의단일유효Retry-After(정수초/HTTP-date)는backoff보다늦은시각을사용한다.잘못된/중복header는backoff,24시간을넘는유효대기는자동재시도중단 및명시재개검토(그시각전재개금지).원event/body변경0.이구체시간식은공동수락대상이다.

## 3. Billing 저장 명세 제안

공통: environment는 신뢰 runtime의 test/staging/production, 모든 시각은 BSON date UTCms, UUID는 canonical 문자열, digest는 64자리 lowercase SHA256이다. append-only이며 nullable/unknown 필드는 허용하지 않는다. version은 BSON long=1 고정으로 별도 작업 세대를 만들지 않는다. 업무 변경은 기존 Session/group/link/guard의 Tx/CAS를 사용한다.

### 3.1 `exam_deletion_facts` — 최초 효과

| 필드 | 타입/의미 |
| --- | --- |
|_id|string: environment + ':' + sessionId|
|environment|string enum|
|sessionId, attemptGroupId|string, exact 연결|
|eventId, deletionOperationId, userId|string, 최초 유효요청값|
|schemaVersion|BSON long=1|
|deletedAt|date, 최초LC접근차단시각|
|payloadDigest|string SHA256, 최초7필드canonical|
|appliedAt|date, 최초Billing처리시각(unknowncommit재조회시보존)|
|disposition|string: SESSION_ONLY 또는 CURRENT_GROUP_BLOCKED|
|version|BSON long=1|

unique `ux_exam_deletion_fact_session`: environment ASC, sessionId ASC. nonunique `ix_exam_deletion_fact_group`: environment ASC, attemptGroupId ASC. 둘다partial없음/TTL없음. 기본_id인덱스외위두개를명시비교한다. 최초effect fact에는원문답안/영수증/merge증거를저장하지않는다.

### 3.2 `exam_deletion_receipts` — event 멱등성

필수필드: `_id`(environment+':'+eventId), environment, eventId, payloadDigest, sessionId, attemptGroupId, processedAt(date), detailReviewAt(date), disposition(enum APPLIED/ALREADY_DELETED), version(long=1). userId/body/새원장/전달queue없음. detailReviewAt=processedAt+120일 제안, 재전송시연장0. 이는자동삭제시각이아니다.

unique `ux_exam_deletion_receipt_event`: environment ASC,eventId ASC. nonunique `ix_exam_deletion_receipt_review`: environment ASC,detailReviewAt ASC,_id ASC. partial없음/TTL없음. 새event no-op도receipt1개를같은Tx에저장하므로그event의다른body를이후거절한다. 무한배열/operation간원fact참조없음.

### 3.3 원자 처리

1. 인증/strict검증후 Tx에서env/event receipt조회. 있으면digest같음204/다름409. 동일receipt재현은새group/guard변경0.
2. receipt없으면env/session fact조회. 존재하고group일치하면 ALREADY_DELETED receipt만삽입. group불일치409.현재guard를다시정리하지않음.
3. fact없으면실Session/group/환경/source연결검증. 자료없는대상을삭제됐다고추정하지않음. 과거creationOwner일치나merge증거를사용자인가요건으로추가하지않음.
4. 해당source의현재owner와activeSession이요청actor/Session에맞으면현재group차단규칙적용. 명확한과거Session/다른현재owner면Session-only. 영향근거불명이면409. 경합중owner변경을같은link/account/guard쓰기fence로검출한다. unresolved를SESSION_ONLY로무조건성공시키지않음.
5. 충돌하는pending reserve/생성은503으로원operation수렴을기다린다. 새로운guard를해제하지않음. Session deny·필요현재group/자기guard변경·fact·APPLIED receipt를동일ClientSession Tx에commit.

unique경합/unknowncommit은정확한receipt/fact재조회로수렴하며commit불명에204를추정하지않는다.증거삽입실패시Session deny도rollback. deletion은금융/consumption/복원grant를취소하지않는다.이미삭제no-op과늦은환불정리는서로원사실/현재guard를변경하지않는다.

## 4. LC 저장·실행 제안

기존operation-scoped DeletionTarget을재사용. 신규collection/전역Session unique/원fact참조없음. nullable부분object대신 LINKED에는완전한 `billingDelivery`, 검증된UNLINKED에는object없음, UNRESOLVED는전달/생성성공으로간주하지않는다.

`billingDelivery` 필수: environment, eventId, payload(7필드), payloadDigest, state(PENDING/ACKED/NEEDS_REVIEW), attempts(long>=0), firstAttemptAt(date|null), nextAttemptAt(date|null), ackedAt(date|null), failureCode(고정 enum|null), leaseToken(string|null), leaseUntil(date|null). lease 두 필드는 동시에 null 또는 존재하며 기존 target version CAS를 사용한다. payload/event/digest는 불변이다. ACKED만 ackedAt 필수이고 lease/nextAttemptAt은 null이다. 미시도는 firstAttemptAt=null/attempts=0. failureCode 후보는 AUTHENTICATION_FAILED / CONTRACT_ERROR / EVENT_ID_CONFLICT / TARGET_UNRESOLVED / RETRY_EXHAUSTED / RETRY_AFTER_REVIEW / STORAGE_UNAVAILABLE이다. 자유 텍스트 오류와 원문 응답은 저장하지 않는다.

같은 target 준비/원문 제거 Tx에서 payload를 영속한다. 원 Session의 삭제 접수 권한은 LC가 검증하며 deletedAt은 실제 접근 차단 commit에 대응하는 operation 시각을 재사용한다. LC는 requestedAt가 그 경계인지 확인한다. 자료 영속 후 HTTP는 Tx 밖에서 호출하고, 응답은 lease/version 및 원 digest가 일치할 때만 반영한다.

초기sender는5초poll/한tick최대20건순차,lease30초/HTTP timeout5초/응답16KiB상한/no redirect를제안한다.시간설정만으로stale응답저장허용안함.lease만료결과는버리고같은전송재시도.503/pending reserve를완료로취급하지않음.

원문정리진행과전달진행은독립.기존 `prepare→allowNewLearning→cleanup`의순서를ACK대기로막지않도록수정한다. 생성허용은inventory고정/대상분류확정/기존봉인·challenge안전조건/모든LINKED ACKED/진행중생성없음/lifecycle허용을ownerTx에서다시확인한뒤한번기록한다.일부ACK/원문완료만으로허용0.전체operation완료는cleanup및필요전달모두완료해야하며중간미전달에30일TTL전파0.

전달auth/계약NEEDS_REVIEW가안전하게준비된원문정리까지멈추면안된다. 반대로인가/참조자료자체불명인대상의안전한원문제거는LC가별도로판정한다. sender OFF도미전달을ACKED/UNLINKED로변환하지않는다.

## 5. 보존·기동 및 공동 확인

120일멱등receipt/전달상세검토기준은동결요청값.미전달은기존30일자동TTL제외,기존미연결challenge등정책일괄변경0.새linked목적자료는ACK만으로자동삭제하지않으며최종purger는별도다.최초deny/금융5년/Claim3년/답안원문을120일에일괄묶지않는다.구TTL문서inventory와reader선행·명시migration/rollback가능버전은운영gate다.기존receipt삭제뒤같은eventId영구검출은보장하지않음.

기본OFF. Billing `app.billing.exam-deletion.enabled` 후보와LC sender flag는공동확정대상.기존LC인증/TLS/새schema/runtime fence검증없이는ON금지.명시maintenance DDL,자동startup index생성/삭제없음.이미존재하는draft schema가있다면실제inventory후migration이며없다고추정하지않음.

LC 확인 요청: E1 wire/오류/재시도표(415추가포함), E2 Billing두collection/원자성, E3 target상태/전체204/원문독립, E4 120일검토기준과30일TTL제외·최종purge별도, E5 아래fixture기대값. LC수락전동결/구현완료로기록하지않는다.

## 6. 공통 fixture 초안과 검증 범위

[fixture 초안](../../src/test/resources/contracts/lc-billing-deletion-exact-r2-draft-1/cases.json)은 가짜값의기본요청·입력변형·상태시나리오/기대결과를포함한다. decoder/oracle구현이아니며각서버가독립검증해야한다. 기존A/R2/simplified bytes는수정하지않는다. 보안세부경계는기존A를재사용하고새route purpose/port매핑은추가검증한다.

이번검사는JSON구문/ID중복/필수구조와파일SHA256정적확인뿐. 새HTTP/DB/controller/Tx테스트실행없음. fixture목록작성이나hash일치는업무성공증거가아니다. runtime/scalar·동시성·LC원문정리·실환경검증은후속이다.
