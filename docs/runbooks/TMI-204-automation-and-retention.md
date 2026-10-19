# TMI-204 자동 snapshot 조율·보존·관측

> 2026-10-17: [현재 JWT 복구 활성화 순서](cleanup-alerts-and-recovery-readiness-2026-10-17.md)를 추가했다. 기존 OPEN control의 baseline 부재를 임의 checkpoint로 우회하지 않는다. 이번 점검은 실환경 활성화 완료가 아니다.

> 2026-10-13 갱신: Identity–Billing은 JWT 단일화됐고 SigV4 코드/의존성은 제거됐다. 운영 installer·명시 이관·분할 hash/전용 scheduler·HTTP 추적의 로컬 구현은 [새 runbook](TMI-204-operator-and-bounded-verification.md)을 따른다. 아래 과거 미구현/인증 문구보다 우선하며 실제 이관/복원/외부watchdog는 여전히206 gate다.

- 날짜: 2026-10-12 / branch: feat/TMI-204-billing-withdrawal-recovery
- 상태: 로컬 구현·가짜 client 검증. 실제 Identity 연결/운영 활성화 완료 아님.

## 5줄 결론

1. feed의 검증된 원본 소실에서 자동 snapshot을 시작하고 완료 후 H 이후 feed가 재개된다.
2. BUILDING은 동일 snapshot을5초마다 확인하며5분 초과를 표시할 뿐 새 작업을 만들지 않는다.
3. 검증된 build timeout/원본 EXPIRED만 최초+추가2run 범위에서 재생성한다. 불명 실패·hash/내용충돌·반복GAP은 중단한다.
4. 독립 retention worker가 복구 임시자료/만료cursor/120일 operation만 정리하며 계정·결제·탈퇴 사실은 보존한다.
5. 경보 지표·heartbeat와 후속 SigV4 client를 구현했으나 실제 연동·외부 알림 채널/watchdog·운영 이관은 별도 gate다.

## 반드시 읽을 내용

### 실행과 budget

`WithdrawalSnapshotProcessor.tick`은 현재 job과 feed의 SNAPSHOT_REQUIRED를 DB에서 판정한다. 최초 baseline의 stream은 추측하지 않으며 명시적인 설치/start가 선행한다. snapshot worker는1초마다 최대1번의 step을 호출한다. 응답 적용에는 기존 epoch/lease/transaction 검증을 유지한다.

자동 실행 전 dispatch 횟수·실패횟수·nextAttemptAt을 저장한다. crash/응답 유실도 시도로 남고 동일 operation으로 재개한다. network/일시오류는 기존1~60초 backoff·20회후5분±20% probe,유효응답commit 이후연속실패초기화다. 긴Retry-After는앞당기지않는다. 실패횟수와누적attempts는현재run에보존되며불명전송실패로새operation을만들지않는다.

| 원인 | 자동 동작 |
| --- | --- |
| BUILDING | 동일 ID5초poll;5분초과buildOverdue 신호 |
| 인증/계약 오류 | blocked와사유저장,자동호출중단 |
| SNAPSHOT_BUILD_TIMEOUT | 첫재생성60초/두번째300초대기 |
| 유효status의 EXPIRED | SOURCE_EXPIRED로기록하고동일추가run예산적용 |
| page의 SNAPSHOT_EXPIRED/FAILED | 새run생성전같은ID의status확인 |
| local7일/원본expiry 도달 | LOCAL_EXPIRED,원격확정으로추정하지않고중단 |
| 원인불명BUILD_FAILED/HISTORY_LOST·hash/내용충돌 | 자동재생성하지않음 |
| 자동snapshot성공뒤다시GAP | REPEATED_GAP 및feed BLOCKED_CONTRACT,새snapshot무한생성금지 |

추가run은새operationId·동일stream/epoch로생성한다. runNumber는최대3이고운영metadata의최근24시간생성건도최대3으로제한한다. 이조회는환경/consumer 기준이며epoch를바꿔도회피하지못한다. 이전명시start도최근생성건에포함하므로보수적이다. budget소진은blocked로남아24시간후자동해제되지않는다. 기존run의expiry/자료보존을새run때연장하지않는다. old epoch에귀속된기존operation은자동채택하지않는다.

### 보존

`PaymentRecoveryRetentionWorker`는별도flag로1초마다한batch를호출하며client를요구하지않는다. snapshot/feed가OFF여도실행할수있지만fence·schema4는필수다.

- snapshot은기존최대20rows+20receipts/빈run제거를재사용한다. source/local중빠른expiry를유지한다.
- feed receipts는최대20개씩7일만료정리한다.
- 만료feedcursor는새localrunId로무효화하고cursor/target을비운다. 실패횟수·긴nextAttemptAt은유지한다.
- operation은retentionExpiresAt(생성+120일) 이후최대20개삭제한다. 해당현재snapshotJob도이미logicalexpiry가끝난경우함께비운다. baseline/C/historicalUncertainty는유지한다.
- lifecycle/account/ref/cleanup후보/inbox/결제원장은삭제하지않는다.205의조건부계정삭제와다른기능이다.

ALL_BLOCKED에서는정리도거절된다. 격리중overdue경보와승인된정리절차가필요하다. 복원으로생긴orphan자료/임의부분복원의정합성은이번일반정리로보장하지않으며206복원gate에서검증한다.

### 관측

`PaymentRecoveryMonitor`는활성worker가있을때60초간격으로DB진행을샘플링한다. AUTH/CONTRACT/source소실/blocked/build5분/circuit/장기Retry-After/임시자료overdue·monitor실패를저카디널리티reason으로기록한다. feed무성공5분warning/30분critical을구분하며처음부터빈scan의정상poll은success로갱신된다.

같은프로세스안에서는최초1회·지속1시간마다·등급상승즉시·해소1회counter를발행한다. 외부알림발송이아니며재시작/다중replica간중복억제는외부경보의환경/서비스/reason grouping으로구성해야한다. userId/operationId/epoch/cursor/payload는label이나로그에넣지않는다.

`billing.recovery.monitor.heartbeat.seconds`는성공한DB샘플시각이다. 외부watchdog은승인된ON상태에서5분미갱신warning/15분critical을구성해야한다. 애플리케이션자체만으로자신의완전중단을탐지할수없으며,의도적OFF/정비의알림억제도인프라설정대상이다.

## 사용자가 결정해야 하는 사항

추가 상품 정책 선택은 없다. **2026-10-12 후속 사용자 승인으로 AWS SDK v2 인증/서명 모듈과 client를 추가했다.** 별도 shared secret/workload JWT/수제서명으로 대체하지 않았다. 설정과 검증 경계는 [SigV4 client 안내](TMI-204-identity-sigv4-client.md)를 따른다.

## 주요 위험과 미확인 사항

- Identity읽기확인: withdrawalrecovery의Snapshot/FeedService·MongoStore·Cursor코드는있으나main참조검색에서해당service를호출하는HTTPcontroller/Bean연결은확인되지않았다. PREFIX는RecoveryProtocol에있다. 상대전체미구현이나배포상태를단정하지않으며별도인계를확인한다.
- 후속 client 구현에서 3초·5초 timeout/응답상한/서명·Retry-After header 연결을 가짜 서버로 검증했다. 실제 AWS 권한·staging 검증은 미수행이다. HTTP flag는 기본 OFF이며 worker ON인데 client bean이 없으면 startup 실패한다.
- 전체snapshothashpass는bounded메모리/20행읽기지만한step의총실행시간양보는아직없다. 대용량검증과scheduler분리/실행시간조율을활성화전에검토한다.
- 운영migration/epochinstaller·oldworker격리·전체restore/부하검증·경보수신자/채널·독립watchdog설치는후속이다. 이번구현으로판매/자동삭제/전체204완료를승인하지않는다.

## schema·flags·배포 전 확인

control schemaVersion4: snapshotJob에automatic/runNumber/failures/attempts/blocked/failureCode/nextAttemptAt/buildOverdue 추가. exact타입은resource기준이다. reader는1~4,명시snapshot은2~4,feed는3~4,자동snapshot/retention은4만허용한다. 자동startup은기존job의retry증거누락도거절한다. 이전job을추측하여attempts0으로이관하지않는다.

snapshot resource revision4는기존9index에 `ix_payment_recovery_operation_budget(environment,consumer,createdAt ASC)` nonunique/simple/noTTL/noPartial을추가한다. collection수는5개유지한다. exact검증만하며기존validator/index를runtime수정하지않는다.

추가flags는 `BILLING_PAYMENT_RECOVERY_SNAPSHOT_WORKER_ENABLED`, `BILLING_PAYMENT_RECOVERY_RETENTION_ENABLED` 모두false다. 전자는snapshot-enabled+fence+client가필수,후자는fence만필수다. 기존flag도변경하지않았다. writer drain·새validator/index·retry증거명시이관·schemaVersion4설치·신규binary검증을별도승인해수행하고구binary로무조건rollback하지않는다.

## 검증

기존snapshot/feed회귀에자동BUILDING/5분표시·추가2run/대기·24시간후blocked유지·20회재시작/probe·auth/긴Retry-After·source와localexpiry분리·자동GAP복구/반복GAP중단·독립7일/120일정리·cursor정리시budget유지·경보중복/해소·startup누락증거거절을추가했다. 모두가짜client/시계와로컬replica-set Mongo에서검증하며최종수치는WORKLOG/CURRENT_STATE에기록한다.
