# Billing 회신 — 단일 진행 관리 방향과204 운영값 제안

- 작성일: 2026-10-12 / develop / TMI-203·204, 연계205·206
- 근거: Identity 「Billing 전달 — TMI-203·204 단일 진행 관리 최종 방향 정리」
- 상태: 인계 방향 확인 및 **운영 초기값 권장안**. 수치 공동 동결·구현·배포·판매·삭제 활성화 승인은 아니다. 외부 전송/실환경 검증 없음.
- 후속(2026-10-12): 사용자가 본 초기값의204계획/Jira반영을 승인했다. §2~3의값은Billing계획기준으로채택됐으며,아래제안시점표현은이력이다. Identity공동exact동결·실제설정/검증·구현착수/판매/삭제승인은별도다.현재기준은 [TMI-204 계획 §7](../plans/TMI-204-withdrawal-recovery.md#7-실패flag관측)을따른다.

## 1. 5줄 결론

1. 첨부의 최신 방향은 예정 v1 주소 최초 구현과 replayFloor+별도 중간 range 저장이다. 직전 v2 URL/예상10index 후보보다 우선한다.
2. Billing local authority·적용/C 원자 commit·epoch/복원 보호와 snapshot T/H·H2를 유지한다.
3. 정상 조회는5초,빈 scan 완료 후30초,일시 오류20회 뒤5분 probe를 권장한다.202 publisher 정책과 별개다.
4. 오류는 장애/job 단위로 경보하며 사용자별 승인을 요구하지 않는다. 실제GAP/인증/계약오류/복원은 일반 재시도와 분리한다.
5. Identity range schema/index와 이 운영 제안·남은 DTO/error를 함께 동결한 뒤 새 문서 revision/fixture를 작성한다.

## 2. 반드시 읽어야 하는 내용

### 2.1 최신 방향과 확인 수준

- prefix는 `/internal/v1/billing/withdrawals`,snapshot 생성/status/page·feed/page의4기능이다. 생성 body는streamId,operation key는환경/consumer별unique+stream binding이다.
- 사용자 전제로 전달된 미구현 상태를 존중해v2 URL을 추가하지 않는다. 실제 기존 consumer가 발견되면 그 영향 범위만 재검토한다. 운영 미배포를 독립 조회했다고 보고하지 않는다.
- 문서/fixture revision은 URL v1과 별개로 새로 관리한다. 이전199 body 없음/remote generation fixture의 수락 이력을 소급 변경하지 않는다.
- Identity는 중간 소실 구간을 별도문서로 저장하고 인접/겹침 병합 및 prefix 편입을 수행한다. journal/delivery 삭제와 range/floor 갱신은 같은 Transaction이다. 정상 가용 구간을 건너뛰어 병합하지 않는다.
- 새 range index가 추가될 수 있으므로 Identity 총10개 index는 더 이상 고정 목표가 아니다. `_v2` operation index 이름도 URL버전과 무관한 후보이며 최종명세에서 결정한다.

### 2.2 Billing 운영 초기값 권장표

모두204 pull 기준이다. 계획의 기존1시간 backoff/probe 초기 후보를 아래 짧은 복구 주기로 **변경 제안**한다. 적용된 설정이 아니다.

| 항목 | 권장값/동작 |
| --- | --- |
| connect/request timeout |3초/5초 |
| 정상 feed |caught-up 뒤5초±20% jitter. 이번 scan이 처음부터빈목록이면30초±20% |
| 따라잡기 중 page |한 job만 실행,commit 후다음page 예약.한 실행최대10page 또는30초 뒤양보하고1초뒤재개. C는매page저장 |
| page/lease |최대20건,lease60초+version CAS.필요시20초마다갱신하되시간만으로권한판정하지않음 |
| 일시오류 backoff |실패n에서상한 min(60초,2^(n-1)초),대기 max(1초,0~상한균등 jitter).횟수큰경우지수계산overflow방지 |
| 일시오류 budget |같은논리job에서20회연속실패,dispatch marker 뒤crash도1회.성공한유효응답/필요한local commit 뒤연속실패초기화;누적장애기록은유지 |
| circuit probe |budget소진뒤같은job에서5분±20%에1회.한probe만실행,새operation/epoch로budget우회금지 |
| 자동복구 |probe의계약검증및local상태확인/적용commit성공후정상진행.프로세스재시작만으로circuit을닫지않음 |
| snapshot BUILDING |Retry-After:5 기준5초poll,일시전송실패budget과분리.5분source build budget 초과지속은경보,유효상태확인없이새run중복생성금지 |
| snapshot 자동 새 run |확정FAILED/EXPIRED 및일시원인에만최초run+추가2개/rolling24시간.추가run대기1분,5분.매run새key,같은epoch/job,전체실패기록/각run기한보존 |

feed에서진행없는유효완료는정상이다. BUILDING 성공poll은HTTP실패를해소해도snapshot run재생성횟수/전체build기한을초기화하지않는다. 실패run의payload/hash불일치·계약문제·원인불명 BUILD_FAILED는자동재생성하지않는다.동일상태GAP을새snapshot으로반복우회하지않으며run budget소진은느린HTTP probe로새run을무한발급하는권한이아니다.

### 2.3 오류와 Retry-After

- network/408/425/429/일시5xx는위backoff대상. 신뢰가능한500 RESPONSE_LIMIT_EXCEEDED/retryable=false는일괄5xx보다우선해중단·경보한다.
-401/403·strict/계약불일치·operation충돌은BLOCKED로두고설정/구현조치후job재개한다.확정409GAP/410원천소실은범위기록·영향평가,동일요청probe를계속하지않는다.
- CURSOR_EXPIRED는durable C에서새scan,source stream교체는운영확인/새baseline이다.원격값으로C를승격하지않는다.
- Retry-After 정수초/HTTP-date는현재backoff/probe보다앞당기지않는다.유효값은clamp로줄이지않고지킨다.24시간초과는같은job PAUSED_UNTIL+경보,시각전에호출하지않으며원천만료위험을별도평가한다.
- 잘못된값/복수모호값은원문비로깅으로기록하고기본backoff·계약경보를사용한다.시각해석은기존clock gate를전제로한다.큰값overflow는무효값처리하고즉시반복하지않는다.
-429의최종code/message/header는Identityexact에서동결한다.429라고사용자별심사나구매권리변경을하지않는다.

## 3. 경보·담당·권한 제안

### 3.1 경보 초기값

| 신호 | 권장기준/책임 |
| --- | --- |
| worker heartbeat |60초마다,독립watchdog가마지막수신5분초과경고·15분초과긴급.의도적OFF/승인된정비는구분 |
| feed 무성공 |feed활성상태에서마지막유효응답5분초과경고·30분초과긴급 |
| backlog 미진행 |관측 H2>C인데committed C가5분간안움직이면경고·30분긴급.처음부터빈feed는경보대상아님 |
| circuit/budget소진 |전환시경보,같은원인재발중복억제 |
| auth/계약오류·확정GAP·stream교체·증거소실 |즉시담당알림,실제영향에따라barrier/삭제보류;단순lag와분리 |
| source 만료위험 |Identity가원천capturedAt/expiry로7일전경고·24시간전긴급.이미적용된원천의정상cleanup을consumer장애로단정하지않음 |
| snapshot/staging 정리 |expiry뒤첫정리주기에도잔존하면경보,worker OFF를무기한보존근거로사용하지않음 |

관측head순번차이를시간으로변환하지않는다. feed event의withdrawnAt을capturedAt으로대체해남은120일을추정하지않는다. 현재availability DTO에는사건별capturedAt이없으므로consumer별정확expiry경보는Identity원천지표와Billing진행을운영에서함께확인한다.새wire필드를이문서에서추가하지않는다.

동일환경/서비스/job종류/원인별최초1회→계속되면1시간마다재알림,등급상승은즉시,해소는1회. userId/runId/epoch를metric label이나외부경보중복키로쓰지않는다.주담당은Billing백엔드운영자,Identityauth/source문제는Identity담당과공동대응,외부watchdog는인프라담당을권장한다. **실제성명·대체자·수신채널은미지정**이며활성화전확인한다.

### 3.2 local epoch/restore 권한

- worker/task role은4개source기능호출·기존job처리만한다.새epoch설치/운영barrier해제권한은분리된운영주체에둔다.실제DB권한/도구로분리가강제되는지검증한다.
- 최초설치/명시RESYNC/restore는운영도구로수행.평상시재시도·circuit자동회복에는운영승인을요구하지않는다.
- restore는영향writer/recovery/purge정지→drain→구프로세스DB접근제거/복원DB격리→새local epoch CAS 설치→증거확인순서다.
- 일관backup의lifecycle/account/ref/inbox/cleanup후보/control/관련진행증거가함께복원됐고같은stream/source범위가충분하면C부터replay.일부만복원됐거나증거불명이면C신뢰중단/새snapshot·대사.
- 상태적용·발급/탈퇴보호·필요범위정합성확인후운영자가barrier해제한다.새H가과거GAP을지우거나판매재개가205삭제자동허가가되지않는다.

## 4. 주요 위험과 미확인 사항

- 제안된poll/lease/timeout/page budget은운영동등DB부하로측정해야한다.기존PaymentStore2초Transaction budget을임의늘리지않는다.
- source별도range저장설계의batch/상한/정리·MAX·동시병합·rollback은Identity명세및테스트책임이다.본회신이이를검증완료로승격하지않는다.
- snapshot build timeout/history문제가반복되면새run으로숨기지않고budget소진후담당조치한다.
- 실제운영담당/채널/권한·watchdog·IAM·복원runbook검증없이는활성화완료라고하지않는다.

## 5. 다음 작업·완료 기준

1. Identity는range schema/index·normalization batch/보존과오류/429/message·FAILED page응답을마무리한다.
2. 양측은v1주소의**새문서revision**과본운영값을동결하고기존199fixture를보존한채새fixture/manifest를작성한다.
3. Billing은204계획의1시간probe 등구후보를최종수락값으로갱신하고공동본문/205/206을동기화한다.이번회신만으로값이적용됐다고하지않는다.
4. 구현시가짜시계로empty poll·20회budget·Retry-After·5분probe·중복경보·snapshot run상한·old epoch/restore를검증한다.실제206gate·구현착수/판매/삭제는별도승인이다.

이번작업은첨부최신방향검토와회신작성이다.실제서버호출·운영변경·Jira·타채팅전송은하지않았다.
