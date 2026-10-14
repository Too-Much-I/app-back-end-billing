# TMI-212 C1 source worker 구현 안내

2026-10-13 / feat/TMI-212-revenuecat-worker-reconciliation

## 변경 동작

- [PaymentSourceWork](../../src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentSourceWork.java)는inbox행을30초lease로claim한다. 최초복구epoch·owner/leaseVersion/generation을고정하고금융Transaction안에서lease를쓰기검증한다.
- [PaymentSourceWorker](../../src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentSourceWorker.java)는검증관측을core.apply에넘기고같은account/lifecycle/store/app/transactionHash의PENDING sync만bind/complete한다. source당100command, tick최대100source. 신규source와처리중source를번갈아선택한다.
- [PurchaseCore](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCore.java)와[PaymentSyncService](../../src/main/java/web/tosunsaeng/billing/domain/payment/sync/PaymentSyncService.java)는worker용동일Transaction guard와최초ticket경로를추가했다. 기존직접호출은유지하며worker에서새epoch를capture하지않는다.
- 원장성공→sync전중단이나bind→complete전실패뒤에도durable source부터이어가며기간권/원장을재지급하지않는다. 만료lease/복구epoch변경뒤에는금융·sync·작업완료모두차단한다.
- 구매차단은backoff,24시간후미처리source는case로인계. review/sync pending은보존하며자동해소하지않는다. terminalsync실패가예산을넘으면실패코드를남기고작업보류한다.

## 스키마와 운영 경계

[worker-r1](../contracts/TMI-212-worker-r1.md)의purchase-core-r4를따른다. r3에서writer격리/ALL_BLOCKED후`PurchaseCoreSchema.upgradeWorkerUnderMaintenance`실행. validator와nonunique dueindex추가만수행,중간DDL실패는exact상태검사후재개. 초기work는claim시생성,운영시자동DDL없음. r1/r2는이전이관을순서대로먼저수행한다. 구버전rollback은새schema/work를모르므로자동허용하지않는다.

C1은명시생성형tick이며Spring scheduler/실외부HTTP를등록하지않는다. sync ON기동차단과기존flag OFF유지. API목록합치기/프로젝트합산240회·동시4/401·429/외부재시도·sync독립lease/페이지복구는C2다. 정기ACCOUNT_LOOKUP/암호화reference영속화·운영재개/알림·보존은D후속이다. 이계층에서외부호출이없다는사실은RC공유quota구현완료가아니다.

legacy terminal/work없는row의pending sync는C2가회수해야한다. sourceTTL120일이전에terminal미완료작업을운영인계하는보존경계도닫아야한다. case/실패코드를DB에서볼수있다는것은실Slack연동완료가아니다. 실제배포/활성화/운영DB이관없음.

## 검증

최종 `./gradlew clean test bootJar`: **1,338통과/실패0/오류0/skip0**, 직전B대비14추가. diff 검사 통과.

로컬Mongo replica-set에서webhook관측→원장→복수sync,금융commit뒤중단/claim회수,bind뒤실패,두worker,lease30초경계/복구epoch변경/ALL_BLOCKED,source당100개분할,탈퇴/검토pending보존,24시간예산,명시r3→r4이관중단재개·drift거절을검증한다. 전체실행결과는WORKLOG/CURRENT_STATE에기록한다. 실Store/RC/ALB는호출하지않았다.
