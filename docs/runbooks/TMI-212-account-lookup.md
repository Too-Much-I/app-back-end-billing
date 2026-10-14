# TMI-212 C2b 계정별 결제 확인 worker

> 2026-10-14 이후 runtime/이관은 [D2 운영 안내](TMI-212-reconciliation-and-review.md)가 우선한다. 아래 내용과1,380개 검증은 C2b 당시 기록이다. 정기조회/암호화reference/선택재개/요약은D2에서구현, 실제Slack·운영도구권한/5년physicalpurge·실환경검증은남아있다.

2026-10-13 / feat/TMI-212-revenuecat-worker-reconciliation

## 구현 결과

앱 sync 접수 → 계정 단위 잠금 → 기존 durable source로 먼저 완료 → RC 구매 목록 한 페이지 조회 → 검증 관측 접수/원장 반영 → sync 완료 → 다음 페이지 checkpoint를 연결했다. 같은 계정의 요청은 함께 처리하며 빈 큐에는 외부 요청이 없다. 이미 처리된 legacy source도 회수한다. 원장 중복 지급 없이 페이지 재조회가 가능하다.

주요 파일:

- [PaymentAccountLookupWork](../../src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentAccountLookupWork.java): 기존 cursor의 lease/epoch 쓰기 guard, 암호화 페이지 위치, 계정별 due 조회.
- [PaymentAccountLookupWorker](../../src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentAccountLookupWorker.java): source/RC 조회·접수·sync 완료, Retry-After·5초~1시간 재시도·24시간 검토·100개씩 AUTH/review 인계.
- [PaymentAccountLookupSchema](../../src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentAccountLookupSchema.java): cursor-r1의 명시 validator 이관/검증. 기존 sync schema/index 불변.
- [PaymentProviderWorkerConfiguration](../../src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentProviderWorkerConfiguration.java), Runtime/Properties: 실제 bounded HTTP+공용 project budget, 기본OFF·기동 검증·ApplicationReady 이후 전용 executor.
- [PaymentSyncService](../../src/main/java/web/tosunsaeng/billing/domain/payment/sync/PaymentSyncService.java): 기존 상태 필드로 검토 인계·pending 보존, 중단된 계정의 새 미확인 요청도 같은 검토 상태로 접수.
- [CleanupPurger](../../src/main/java/web/tosunsaeng/billing/domain/payment/cleanup/CleanupPurger.java): 계정 lookup owner가 남아 있으면 삭제 보류. 자동 삭제 활성화 없음.

## 이관·배포 전 점검

[exact](../contracts/TMI-212-account-lookup-r1.md)과 [공유 budget](../contracts/TMI-212-provider-dispatch-r1.md)을 따른다. 기존 foundation/recovery 및 core r4·sync r1이 필요하다. writer를 격리하고 ALL_BLOCKED에서 `PaymentAccountLookupSchema.installUnderMaintenance(db,true)`로 기존 행을 검사한 뒤 strict validator를 적용한다. 새 필드는 optional이라 대량 backfill 없이 첫 claim에서 생성한다. index 두 개는 그대로다. 기존 worker/purger로 자동 rollback하지 않는다.

공유 budget은 별도 DB에 명시 설치하고, 같은 RC project 호출자가 전부 같은 Mongo cluster+DB를 사용해야 한다. 환경별 Mongo cluster가 다르면 지금 배선을 켜지 않는다. worker ON은 api/reference/lookup key·catalog 매핑·schema를 검사하며 실제 HTTP client를 생성한다. 테스트는 ApplicationReady를 보내지 않아 실제 외부 호출 없이 이 배선을 검증한다. 운영키는 저장소에 넣지 않는다.

`BILLING_PAYMENT_PROVIDER_WORKER_ENABLED=false`, `...WORKER_POLL_MILLIS=1000`, `...WORKER_BATCH=100`이 기본이다. ingress와 독립적으로 OFF 유지했다. scheduler의 1초는 한 tick 완료 뒤 DB를 다시 찾는 간격이며 RC 호출 주기가 아니다. HTTP는 인스턴스 내 순차, 여러 인스턴스의 유효 permit는 project 합산4다. phase별 runs/claimed/failed metric을 추가했으며 Slack 연결 완료는 아니다.

## 남은 항목과 주의사항

TMI-212 전체 완료가 아니다. D의 정기 계정 대사, 구매별 암호화 재조회 reference 영속화, terminal/cursor 보존·cleanup, 검토/AUTH 계정·command 승인 재개 도구, 운영 알림이 남았다. schema의 generation은 검증하지만 이번 구현은 자동/수동 계정 재개 기능을 제공하지 않는다. project budget 재개만으로 중단된 command가 풀리지 않는다.

공개 sync ON은 보존·검토·배포 준비 gate 전까지 기동 거절을 유지한다. runtime의 실제 공용 DB topology/Store 거래 매핑/credential·sandbox E2E/부하 검증은 아직 하지 않았다. PENDING command부터 계정별 조회하므로 과거 모든 계정을 매초 스캔하지 않지만, 큰 적체에서는 반환100개가 전체 DB 스캔100개라는 뜻은 아니다. explain/지연/적체 모니터링은 활성화 전에 확인한다. 한 순회1000페이지 제한 초과는 자동 지급/부재 판정 대신 검토다.

조회 실패·404/빈 목록·미확인은 권리를0으로 만들거나 삭제 근거가 되지 않는다. 부분 환불·LC 시험 입장/무료권 복원·판매·205 자동삭제는 구현/활성화하지 않았다. 외부 계약과 기존 pendingEvidence 원자성은 유지한다.

## 검증 결과

최종 `./gradlew clean test bootJar`: **1,380 통과 / 실패0 / 오류0 / skip0**(C2a 대비24개 추가). `git diff --check`·문서 링크 검사 통과. 로컬 Mongo replica-set/fake provider HTTP를 사용했다.

계정 묶기/서로 다른 계정, 실제 decoder/budget 연결, legacy terminal 무HTTP 완료, 암호화 페이지 재개, 접수 후 checkpoint 전 중단, lease·epoch 변경 응답 차단, 같은 계정 두 worker, 빈 목록/404/429,101건 AUTH/review·24시간 인계와 중간 crash, foreign owner·탈퇴 중 응답·잘못된 암호문·페이지 제한·새 구매 backoff 초기화, cursor schema 명시 이관/drift, lookup lease의 삭제 차단, runtime OFF 무의존/ON 설정 검사·실제 배선 무발신을 검증했다.

실패했던 테스트 두 항목은 공유 테스트 MongoClient의 context 종료 범위와 변경된 기동 차단 문구 assertion이었다. 둘 다 수정 후 전체 재실행했다. 운영 DB 이관·실제 RC/Store 요청·공유 topology·활성화는 수행하지 않았다.
