# Billing 검토 회신 — 단일 진행 관리 exact 개정안

> 최신 방향(2026-10-12): 후속 Identity 통합 인계에서 사용자 미구현 전제에 따라 예정 v1 URL 유지와 replayFloor+별도 range 저장 채택을 전달했다. 아래 v2 URL/10index는 당시 후보이며 현재 방향은 [204 운영 회신](TMI-204-OPERATIONS-PROPOSAL.md)을 따른다. exact 세부 동결·실배포 검증은 여전히 별도다.

- 작성일: 2026-10-12 / develop / TMI-203·204, 영향199·202·205·206
- 대상: Identity의 「Billing 인계 — TMI-203·204 탈퇴 누락 복구 단순화」 및 「Identity 회신 — 단일 진행 관리 exact 개정 미결안」
- 상태: 기술 검토 결과·동결 권장안. 공동 exact 수락 완료나 코드/배포 승인이 아니다. 상대 구현·운영 상태는 독립 검증하지 않았다.

## 1. 5줄 결론

1. Billing local checkpoint/epoch/consumer coverage 단독 소유는 개정204 계획과 일치하며 큰 구조 변경 없이 진행할 수 있다.
2. 명시 streamId 생성 body, 환경/consumer/key 유일성+stream 요청 바인딩, 페이지 범위 availability와 최초 소실1구간은 타당하다.
3. versioned 경로를 권장하고 기존 push/event 계약과 새 복구 revision을 분리한다. 과거199 fixture를 새 규격처럼 덮어쓰지 않는다.
4.503 details variant와 sequence 검증/오류 우선순위,operation metadata 수명 등 구현 경계만 명확히 하면 된다.
5. 이번에는 검토 회신과 기록만 작성했다. 계획/공동 본문/fixture/index·Identity·Jira·코드·외부 설정은 변경하지 않았다.

## 2. 반드시 읽어야 하는 내용

### 2.1 항목별 판단

| 제안 | Billing 검토 | 조건/유지 사항 |
| --- | --- | --- |
| local authority·원격3개 API 제거 | 채택 권장,204 계획과 일치 | local CAS/restore 격리 및 source stream 보호 유지 |
| versioned read4기능 | 채택 권장 | snapshot 생성은 run을 만드는 POST이므로 전체를 무상태 read-only라고 표현하지 않음 |
| snapshot 생성 body streamId | 채택 권장 | body 없는 구 규격과 새 규격을 명시 구분 |
| unique(environment,consumer,requestOperationId) | 채택 권장 | stream은 unique 구성요소가 아니라 canonical 요청 바인딩. 같은 key/new stream은 conflict |
| 마지막 feed cursor non-null | 채택 권장 | H2 binding/TTL 내 중복 조회용;원격 ACK 증거 아님 |
| success availability는 이번 page 범위만 | 채택 권장 | observed head나 H2 전체의 재생 가능 선언 아님 |
|410 최초 missing/range1개 | 채택 권장 | 전체 gap 목록 아님.205가 나머지 범위 안전을 추론하지 않음 |
|409 확정 hole·503 일시 불명 분리 | 채택 권장,503 shape 보완 | 없는 missingSequence를 만들어서는 안 됨 |
| baseline H와 실제(H,C] 분리 | 필수 유지 | C 이하 원event 모두 처리했다고 간주 금지 |
| Identity index 예상10개 | 방향 타당,실제 동결 전 | 이름/key order/options·배포 존재 확인 필요;실제 drop 금지 |

이 판단은 단순화 방향에 대한 기술 검토다. exact URI·오류 variant·index 이름 등을 이미 양측 동결했다고 표시하지 않는다.

### 2.2 Billing 저장·Transaction 책임 회신

- [204 §5~6](../plans/TMI-204-withdrawal-recovery.md)의 control/job, snapshot run, staging rows, page receipts, 최소 operation 기록의5개 후보를 사용한다. ACK jobs/remote confirmation/별도 scans는 없다.
- control epoch/version CAS,기존 inbox/event 적용, lifecycle/account/ref/cleanup 후보 변경,로컬 C/page receipt를 같은 ClientSession/Transaction에서 commit한다. HTTP는 Transaction 밖이다.
- DUPLICATE/NOOP도 fence를 통과한다. push로 먼저 처리한 event도 feed 순번이 연속일 때만 C를 전진시킨다.
- snapshot은 가짜 eventId 없이 사실을 적용하고,전체 검증 완료 뒤 state baseline H를 저장한다. 원event 적용 구간과 unresolved gap을 별도 유지한다.
- 백업/복원 단위는 control만이 아니라 위 적용 데이터·inbox·증거 전체다. 선택적/불명 복원에서는 C를 신뢰하지 않는다. 구worker DB 접근 제거→새 local epoch→검증된 C replay 또는 bootstrap을 따른다.
- 최소 operation 문서는 설치/실패·종료 근거와 승인된120일 보존용이다. current job의 별도 진행 authority나 개인정보 원문 저장소가 아니다.

## 3. 공동 동결을 위한 보완 권장안

### 3.1 revision과 index

새 복구 prefix는 `/internal/v2/billing/withdrawals`와 기존4개 suffix를 권장 후보로 제안한다. **이 문서로 경로를 확정하지 않는다.** 실제 route/build/IAM·client 현황을 확인한 뒤 수락한다. 기존 Identity→Billing push 경로와 event schemaVersion=1은 그대로다. recovery URI 버전 변경을 모든 이벤트 v2 변경으로 확대하지 않는다.

새 snapshot operation index는 기존 이름에 다른 key를 덮어씌우지 않도록 revision 구분 이름을 사용한다. 정확 이름은 Identity manifest 제안으로 동결한다. key는 environment,consumer,requestOperationId 순서를 권장한다. 동시 요청 duplicate-key도 기존 binding 조회/내용 비교로 수렴해야 한다.

204 현재의 stream 포함 scope 표현은 새 exact 수락 시 환경/consumer/key 유일성 + stream payload binding으로 명확히 교체한다. stream을 검증한다는 의미는 유지하지만 unique key에 넣지는 않는다.

### 3.2 snapshot 멱등·보존 경계

- 인증/strict decode 후 기존 key binding 확인 → body mismatch409 OPERATION_CONFLICT → inactive stream409 STREAM_REPLACED → 기존 current-stream run 반환 또는 신규 생성 순서를 권장한다.
- 같은 key/new stream과 old key/old body의 오류 우선순위를 fixture로 구분한다. status/page도 현재 source stream 검증을 유지한다.
- run 개인정보/manifest 만료7일 뒤에도 key/body binding과 최소 종료 상태는 operation120일까지 유지한다. 같은 key로 새 run을 재생성하지 않는다. 그때 반환할 종료 DTO/만료 오류는 새 exact에 명시한다.
-120일 이후 key를 영구 기억하지 않는다. Billing은 새 논리 작업에 새 key를 사용한다. 재시도는 동일 key/body,새 run은 새 key다.

### 3.3 availability 순번과 경계

- 성공 시 checkedFromExclusive=request.afterSequence,checkedThroughSequence=scannedThrough=nextAfterSequence이며 검사 범위의 items는 정확히 연속이어야 한다.
- checkedFromExclusive <= checkedThroughSequence <= H2 <= observedThroughSequence, replayFloor <= observedThroughSequence를 검증한다. H2/stream은 같은 cursor에서 바뀌지 않는다.
- 비어 있지 않은 재생 성공 범위는 필요한 첫 순번이 floor/논리 expiry/hole에 포함되지 않아야 한다. 성공 응답의 메타데이터와 items가 모순되면 C를 전진시키지 않는다.
- after==H2의 빈 범위와 after<H2의 빈 page를 구분한다. after>H2,head 뒤 요청,MAX_VALUE 연산의 정확 오류를 fixture에 명시한다.
-410의 missingSequence는 요청에 필요한 최초 누락이며 D1,unavailableRange 안에 포함돼야 한다. range 끝은 관측 head 이하이며 H2 밖까지 이어질 수 있는지 명시한다. 전체 원천의 확정 interval을 반환한다면 Billing은 필요한 scan 범위와 교차해 판단한다.
- 페이지 도중 hole 발견 시 해당 요청 전체를 오류로 할지,앞의 연속 prefix를 성공 반환하고 다음 요청에서 오류를 낼지 한 규칙으로 동결한다. **권장: 이번에 반환하려는 page 범위에 hole이 있으면 오류, checkpoint 무변경.** 어떤 경우에도 hole을 건너뛴 items는 금지한다.
- 물리 row가 남아 있으나 논리 expiry가 지난 경우도 replay 불가다. cleanup 전에 row의 expiresAt으로 확정할 수 있는 최소 range를410 증거로 인정할지 명시해,정상 만료를 계속503으로 분류하지 않는다.

### 3.4 오류 DTO를 더 단순하게

권장 exact variant는 다음과 같다. 기존 code/message/retryable/correlationId 네 필드는 유지한다.

| code | details 권장안 | Billing 처리 |
| --- | --- | --- |
|410 REPLAY_UNAVAILABLE | 제안된6키 모두 필수,unavailableRange non-null | 알려진 필요구간 GAP 기록,동일 구간 무한 retry 금지 |
|409 COVERAGE_GAP | 같은6키,unavailableRange만 필수 null | 확정 hole 기록,임의 expiry 범위 추정 금지 |
|503 SOURCE_AVAILABILITY_UNCERTAIN | **details 생략** | 일시 불명 재시도/장기화 경보,C 무변경 |
|일반 저장소503 | details 생략 | 기존 일시 장애 처리 |

503에서 신뢰 가능한 부분만 선택적으로 같은 shape에 넣는 variant는 우선 제외하는 편이 단순하다. 필요하면 별도 명세로 추가한다.401/403 인프라 응답을 이 JSON으로 가정하지 않는다. 각 code의 retryable/message 정책도 fixture로 고정한다.

16KiB 안에 metadata+최소1개 item도 들어가지 않는 경우의 명시 오류 코드를 동결한다. 서버 구성/응답 생성 문제를 caller 요청 크기 초과로 오인시키지 않는다. 빈 성공이나 JSON 절삭으로 대체하지 않는다.

## 4. 주요 위험과 운영 gate

- success availability는 관측 시점의 page 증거이며 미래 replay·snapshot 이전 원event 완전성을 보장하지 않는다.
- source cleanup의 전체 interval 저장 상한/정리 방식은 wire1구간 제한만으로 해결되지 않는다. source 보존·누락 판정을 훼손하지 않는 저장 설계가 필요하다.
- Billing 외부 watchdog·Identity source expiry 경보,local restore 권한·source endpoint 격리,실제 DB 일관 복원은206 검증 대상이다.
- token/push 보호와 실제 손실·복원 제한을 유지한다.205는 local baseline/feed 구간/gap과 거래 증거를 함께 판단하며 정상 poll 성공만으로 삭제하지 않는다.
- 정확 retry/circuit 값·API/manifest는 동결 전 후보다. 이번 검토는 실제 Mongo/IAM/복원·Billing/Identity 배포 검증을 대신하지 않는다.

## 5. 다음 작업과 검증 기준

1. Identity가 §3의 권장 보완안에 수락/대안을 회신하고 새 exact revision·route/index 이름·DTO/error 표를 작성한다.
2. Billing은 새 exact 수락 뒤 공동 본문/204/205/결정서 연결을 동기화한다. 기존199 fixture/hash/과거 검증 결과는 보존한다.
3. 신규 fixture: create stream/key 충돌·동시성·7일/120일·stream 교체,availability 순번/빈범위/논리expiry/cleanup 경합/410·409·503 variant·16KiB,baseline과feed 범위 분리.
4. 구현 시 local apply+C atomicity·unknown commit·stale epoch/restore·auth/token/push 회귀를 수행한다. 실제206 gate와 구현 착수·판매·삭제 활성화는 별도다.

이번에 확인한 것은 첨부 두 문서와 Billing204/기존exact의 정합성이다. 상대가 보고한 로컬203 코드 부재는 참고 정보이며 실배포 미존재 증거로 승격하지 않았다.

## 6. recovery-v2-candidate-1 후속 검토 — 2026-10-12

Identity의 「Identity 회신·새 exact 후보 — 단일 진행 관리 복구 API」 전문을 추가 검토했다. 앞선 주요 보완사항이 반영됐으며 **아래 후보를 기술적으로 채택하는 것을 권장한다.** 새 공동 동결/사용자 구현 승인/실제 검증 완료로 표시하지 않는다.

| 후보 | 검토 결과 |
| --- | --- |
| /internal/v2/billing/withdrawals와4개 POST | 구push/schema1과 분리되는 방향으로 타당 |
| stream body·환경/consumer/key unique·충돌 우선순위 | 요청 바인딩과 stream 교체가 명확함 |
| 먼저20개 순번 범위를 정하고 hole이면 전체 오류 | 이전 요청한 page 전체 실패 규칙 반영,원자적 checkpoint 처리와 일치 |
|410 full interval 또는 논리expiry singleton | 알려진 근거 범위만 반환하고 필요한 범위와 교차하는 규칙 타당 |
|503 details 생략 | 모호한 부분DTO 제거 반영 |
|7일 EXPIRED/120일 후404·동일run 재생성 금지 | 임시 개인정보 정리와 멱등 최소metadata 분리 반영 |
|500 RESPONSE_LIMIT_EXCEEDED/retryable=false | 요청413과 서버 생성 오류 구분 타당;Billing 일반5xx retry보다 명시적 non-retryable code 분류를 우선해야 함 |
| 새10개 index 후보 | 기존13개와 정적 대조상 I2의7개 및 snapshot2개 유지,operation key/이름 변경,remote3개 제외와 일치.실제DB 검증 아님 |

큰 구조 재설계나 추가 제품 선택을 요청할 필요는 없다. 다음은 새 exact/schema/fixture를 작성하면서 남은 구현 규격을 닫는 작업이다.

- **source unavailable interval 저장:** 상한·분할/정리·overflow 시 cleanup과 floor/interval의 원자성 및 실패 처리를 확정한다. wire1구간 제한과 별개의 기존 미결사항이며 source cleanup 구현 전에 필요하다. 추가 index가 필요하면10개 후보도 함께 갱신한다.
- **오류표 완결:**429/Retry-After·code별 고정 message를 채운다. snapshot/page의 FAILED 상태 요청 결과도 명시해 BUILDING의 SNAPSHOT_NOT_READY와 섞어 영구 polling하지 않게 한다. 새설계 확대가 아니라 DTO 상태표의 빈칸을 채우는 작업이다.
- **Billing retry 정합:** 신뢰 가능한 RESPONSE_LIMIT_EXCEEDED는5xx 일괄 재시도 대상에서 제외하고 경보한다. 통신 실패/일반 일시503 retry와 구분해 fixture에 포함한다.
- **호환/운영:** actual route/client/index/IAM 존재 조사·전환·local 복원/watchdog는 별도 gate다. 로컬src부재만으로 미배포 판정하지 않는다.

직전 회신 §3.3의20개 page에서 크기에 따라 prefix를 줄일 여지를 두지 않고,이번 후보의고정E·초과시500으로 명확히 정리하는 방향을 권장한다. after==H2에서만 빈성공,MAX overflow 회피,logicalexpiry410 근거 모두 포함됐다.

다음 권장 순서는 **후보 기술 채택 → 남은 저장/오류 명세를 포함한 새 exact·manifest·fixture 작성/공동 검증 → 활성 문서와204계획 동기화 → 별도 구현 착수**다. 설계 검토 회신만 반복하며 구현 규격 작성을 미룰 필요는 없다. 이번 작업은 본 검토와 기록만 수정했고계약본문/계획/fixture/코드/Jira는 변경하지 않았다.

## 7. Identity 내부 검증 코어 구현 보고 검토 — 2026-10-12

대상: 「TMI-203 내부 검증 코어 구현과 공동 확정 필요 항목」. 첨부 기준으로 범위 정규화·feed 검증·streaming digest 내부 코어가 구현됐으며 HTTP/Mongo adapter·builder/cursor·인증 controller는 미구현이다. 따라서 **204가 실제 API에 연결할 단계는 아니다.** 전체1492개 중1486통과/6skip·신규17개통과는 Identity 보고이며 Billing에서 소스/테스트를 독립 실행한 결과가 아니다.

### 기술 검토 결론

다음은 Billing 계획과 충돌하지 않으며 새 exact/manifest에 반영하는 것을 권장한다. 별도 제품 정책 선택은 필요하지 않다.

- withdrawal_unavailable_ranges의streamId/fromSequence/toSequence 최소 필드,from unique·to조회 index2개: 개인정보 없이 source 증거를 분리하는 방향과 일치. 후보12개는 기능index 수이며 조회/정리 보조index까지 확정됐다는 뜻은 아니다.
- availabilityVersion int64≥0의명시0이관,missing/null/overflow fail-closed,cleanup의source CAS·삭제/floor/range원자commit: 기존복원/삭제안전성과 일치한다. 실제DDL·이관완료는아니다.
- 최대100구간 정규화·새singleton포함시기존99개: bounded 처리에 적합하다. 선택밖증거를남기고정상가용구간을건너뛰지않는불변식 유지.
- FAILED page의409 SNAPSHOT_FAILED/retryable=false/details생략: 채택권장. Billing은같은page를반복하지않고status의failureCode를확인한뒤이미승인된일시원인/새run budget범위에서만재생성을판단한다.
-429는status/유효Retry-After로처리하고gatewayJSON을가정하지않는안: 채택권장,204운영계획과일치.새앱rate limiter의임의횟수를추가하지않는다.

### Mongo adapter 구현 시 반드시 닫을 두 경계

1. **100개 제한은 정규화 작업량 제한이지 feed 누락 판정의 정확성 제한이 아니다.** 임시 겹침·선택밖range를 포함해요청page에서필요한최초hole을놓치지않는쿼리/정렬/일관읽기 규격을정한다.100개만읽고나머지가없다고성공시키거나항상같은100개만선택해잔여범위정리가멈추지않도록한다.제안index2개만으로충분한지는실제query plan/부하로확인한다.
2. **unique(streamId,fromSequence)와 선택밖range 충돌을 수렴시킨다.** 병합결과가선택밖기존문서와같은from을만드는경우,rollback후선택범위를조정하거나기존동일키증거를병합해야한다.같은batch를무한retry하지않는다.선택문서_id/원값조건·stream CAS·원천삭제를같은Transaction으로검증하고최소101구간/겹침/동일from경합/partial batch/MAX를포함한다.

이는새로운복구체계를추가하자는요청이아니라이미제안된bounded정규화와DB유일성의구현경계를명확히하는항목이다. 실제같은T/cleanup snapshot·clock증거·restore·IAM 검증은코어테스트로대체하지않는다.

### 다음 작업

새exact 문서에위FAILED/429와source저장/index규격을모으고고정message·schema/null/생략·operation index최종명을작성한뒤새fixture를양측실행한다. 기존199fixture/hash와보고된단위테스트이력은보존한다. 문서/fixture공동확인후production controller/adapter를진행하며이번검토만으로API/운영활성화를승인하지않는다. 이번변경은검토기록뿐이며계획/계약본문/코드/Jira/Identity에는변경하지않았다.
