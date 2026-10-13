# TMI-204 feed·영속 재시도 구현

> 후속: [자동snapshot·보존·관측](TMI-204-automation-and-retention.md)에서 schema4와자동조율/정리worker/관측지표를추가했다. 아래미구현설명은해당후속범위와함께읽는다. 실제client/운영활성화는여전히미완료다.

- 날짜: 2026-10-12 / branch: feat/TMI-204-billing-withdrawal-recovery
- 범위: feed executor와 기본 OFF 자동 호출 worker. 전체 TMI-204 완료 아님.

## 5줄 결론

1. 검증된 baseline H 이후 이벤트를 고정 H2·20건 페이지로 받아 C를 전진시킨다.
2. inbox·탈퇴 반영·receipt·C·job 진행은 같은 Mongo transaction에 저장한다.
3. dispatch 전에 시도 횟수/lease/다음 시각을 저장하여 crash·재시작으로 retry budget을 초기화하지 않는다.
4. 503은 재시도, 인증/계약 오류는 차단, 검증된 원본 소실은 SNAPSHOT_REQUIRED로 구분한다.
5. 실제 client·snapshot 재생성 조율·운영 이관/경보가 남아 있으므로 production 활성화는 금지한다.

## 사용자가 반드시 읽어야 하는 내용

구현 파일:

- `WithdrawalFeedProcessor`: local control의 feedJob/C, page 적용, 오류 전이, batch receipt 정리.
- `RecoveryRetryPolicy`: jitter/backoff/probe 및 Retry-After 정수초/HTTP-date 해석.
- `RecoveryRemoteException`: 원문·URL·cause 없는 transport 오류 분류 경계.
- `WithdrawalFeedWorker`: flag ON일 때만 등록, ApplicationReady 이후 1초마다 due 작업 확인. 한 호출에서 최대10page/30초, page commit 후 판단하고1초 양보.

HTTP는 transaction 밖에 있다. 응답 반영은 원래 dispatch의 epoch/lease/run/C를 다시 확인한다. 새 snapshot 시작은 같은 transaction에서 기존 feedJob을 제거하여 in-flight feed를 무효화하며 snapshot COMPLETE 전에는 feed를 시작하지 않는다. snapshot과 함께 설치한 H는 상태 기준점이고 C는 그 이후 실제 원event 적용 진행점이다. 0~C 모든 과거 event의 완전성을 주장하지 않는다.

### 상태와 재시도

| 상태/상황 | 동작 |
| --- | --- |
| READY | 다음 page, 매page commit |
| CAUGHT_UP | 진행이 있었던 scan은5초±20%, 처음부터 빈 scan은30초±20% 후 새 scan |
| RETRY | min(60,2^(n-1))초 상한의 jitter, 최소1초 |
| CIRCUIT | 20회 연속 실패 이후5분±20% probe |
| BLOCKED_AUTH | 자동 재호출 중단 |
| BLOCKED_CONTRACT | strict·digest/내용충돌·nonretryable500 등 자동 재호출 중단 |
| SNAPSHOT_REQUIRED | 필요한 원본 소실, historicalUncertainty=true; C 유지 |
| STREAM_REPLACED | 다른stream 순번을 비교하지 않고 운영 확인 대기 |
| cursor1시간 만료/410 CURSOR_EXPIRED | committed C에서 새scan, 기존 H2 완료 주장하지 않음 |

실패 count는20에서 포화하지만 누적 attempts는 계속 보존한다. 유효 page의 적용 commit 성공 이후에만 연속실패를0으로 만든다. dispatch 뒤 crash는 실패1회로 남고 lease60초까지 다른worker가 재호출하지 않는다. cursor 재개가 circuit을 우회하지 않는다. DB 장애로 실패전이 저장도 실패하면 dispatch marker/lease/notBefore가 남는다.

Retry-After 유효값은 정책보다 늦을 때만 적용하고 길다고 잘라서 일찍 호출하지 않는다. BSON 저장 시 올림하여 nanos 때문에 조기 실행하지 않는다. 다중/invalid/overflow는 기본backoff용 결과를 반환한다. 실제 HTTP adapter가 이 파서 결과를 연결하고 invalid/24시간초과 경보를 발생시키는 작업은 아직 없다.

`RecoveryRemoteException.application`에는 strict decoder로 검증한 오류만 전달해야 한다. 원본소실은 stream·요청 범위·missing/floor/range를 processor에서도 대조한다. 일시503만으로 historicalUncertainty나 구매 barrier를 바꾸지 않는다. 일반 unexpected runtime 오류도 원본소실로 추정하지 않는다.

## 사용자가 결정해야 하는 사항

제품 정책 선택은 없다. 새 schema의 이관·운영 client·flag 활성화는 별도 배포 승인 대상이다. 인증/계약 차단의 운영 재개 endpoint는 임의로 추가하지 않았다.

## 주요 위험과 남은 작업

- **snapshot 자동 조율 미완료:** SNAPSHOT_REQUIRED에서 자동 create하지 않는다. BUILDING5분·실패원인·최초+추가2run/rolling24h·1분/5분 대기·반복GAP 차단을 한 durable coordinator로 연결해야 한다. 현재 snapshot processor는 이전 단계처럼 명시 호출형이다.
- **실제 adapter 없음:** 실Identity 네트워크/SigV4/3초·5초 timeout/16KiB 제한과 Retry-After 헤더 처리는 후속이다. feed ON인데 client Bean이 없으면 startup이 실패한다. fake client를 운영 client로 등록하면 안 된다.
- **운영 관측 미완료:** worker의 bounded tick/pages counter만 있다. tick SUCCESS는 실행함수 반환이며 CAUGHT_UP이나 원본/인증 정상의 증거가 아니다. 상태전이 경보·heartbeat·독립 watchdog·긴Retry-After/overdue 경보는 후속이다.
- **보존 실행 미완료:** feed receipts는7일 만료로 생성하며 `purgeExpiredReceipts`가최대20개를삭제한다. feed OFF에서도 fence하에서호출가능하지만 독립 retention scheduler·만료cursor정리·operation120일 정리는 후속이다. inactive cursor가 영구 남지 않도록 활성화 전에 연결해야 한다.
- 실제 부하/restore/Identity 동반복원/IAM/E2E는 미검증이다. 신규 writer의 공통control CAS 부하도206 gate에서 확인한다.

## schema와 활성화 전 조건

control validator가 int64 schemaVersion1/2/3을 허용하도록 확장됐고 feed writer는3을 요구한다. snapshot은2/3을 읽고 기존account/push fence도1/2/3을 이해한다. feedJob에는 epoch·stream/run·C와별개의scanStart/target/pageIndex·cursor만료·lease·retry/attempts·nextAttemptAt·lastSuccessAt·version이저장된다. field/null/타입/state exact는 `payment-recovery-control-schema.json`을 따른다.

`payment-recovery-snapshot-schema.json` revision3은 pages.kind에 FEED를추가하며 기존9index의이름/키순서/옵션은변경하지않는다. feed receipt는run/pageIndex unique이고사용자목록·rawcursor는저장하지않는다. control한행의fence쓰기로lease·진행·snapshot전환을직렬화하며 feedJob.version도overflow를거절한다.

`BILLING_PAYMENT_RECOVERY_FEED_ENABLED=false` 기본. ON은fence필수·schemaVersion3·두schema의exact검증·인증client가선행한다. 이전version2 validator는새binary의exact검증과다르므로호환성을추정하지말고writer drain/명시validator이관/신규binary검증/version3설치의승인절차를따른다. 운영DDL·flag변경·구binary rollback은이번에수행하지않았다.

## 검증

`WithdrawalFeedIntegrationTest`: 고정H2/멀티page·push중복·내용충돌·receipt실패rollback·목표변경·20회재시작/probe·긴Retry-After·503/source소실·auth/nonretryable500·10page양보·cursor만료·snapshot중in-flight거절·crash/lease·Mongounknowncommit/transient·정리·epoch교체·MAX.

`RecoveryRetryPolicyTest`, `RecoveryConfigurationTest`, `WithdrawalFeedWorkerTest`: 숫자상한/HTTP-date/invalidheader,기본OFF·fence필수·client없음startup실패·ApplicationReady전DB미접근. 전체 결과는 WORKLOG/CURRENT_STATE를 따른다.
