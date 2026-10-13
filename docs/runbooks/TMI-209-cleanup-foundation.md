# TMI-209 — cleanup PR1 구현·검증 및 OFF 배포 안내

> 후속 PR2는 [삭제 코어 안내](TMI-205-pr2-purge-core.md)를 따른다. 아래 r1과 테스트 수치는 PR1 당시 기록이다. 현재 runtime schema 검증은 r2를 요구하며 실제 purge ON 거절은 유지한다.

- 날짜: 2026-10-13 / 최종 작업 브랜치: feat/TMI-209-billing-cleanup-foundation
- 기준: [TMI-205 계획](../plans/TMI-205-withdrawal-reconciliation-and-purge.md), [cleanup-r1](../contracts/TMI-205-cleanup-technical-spec-r1.md), [Jira TMI-209](https://to-teacher.atlassian.net/browse/TMI-209).

## 1. 5줄 결론

1. PR1은 job 상태/lease/판정/일정만 구현한다. 계정/ref/금융/무료 데이터를 물리 삭제하는 코드가 없다.
2. 실제 Spring 구성은 local/provider/coverage를 명시적 미준비로 연결한다. production은 UNKNOWN→BLOCKED이며 fake가 READY를 만들 수 없다.
3. READY/FINANCIAL_RETAINED는 테스트 주입으로 검증한다. 실제 provider와 금융 writer 연결은 PR3이며 본 구현의 완료 근거가 아니다.
4. cleanup schema는 별도 maintenance 설치, application startup은 검증만 한다. recovery control4와 fence가 필수다.
5. purge-enabled/alerts-enabled는 true이면 OFF 배포라도 기동 실패다. 실제 삭제·Slack 채널/HTTP 전송 구현을 뜻하지 않는다.

## 2. 사용자가 반드시 읽어야 하는 내용

현재 production에서 reconciliation만 ON하면15일 지난 후보를 미확인으로 보류하고 다음 확인과7일 검토 기한을 기록한다. provider가 없는데 빈 DB/빈 응답으로 미구매라고 하지 않는다. 검토 담당과 예외 보존이 정해지기 전 ON을 권장하지 않는다.

정리 후보는 매시간 최대100개씩 keyset 순회하고, 실패 후보가 뒤의 후보를 계속 가로막지 않도록 다음 round에서 cursor를 이어간다. 전체 due 순회가 끝나면 cursor를 초기화한다. 만료 VERIFYING lease는 별도10초 tick에서 최대10개씩 회수한다. 이 cursor는 메모리 scan 위치일 뿐이며 재시작 시 durable job/state/version으로 중복을 제어한다. 큰 적체가 매시간 모두 처리된다는 SLA는 아니다.

작업 executor는 자체 단일 thread로 Boot의 공유 scheduler나204 snapshot executor를 바꾸지 않는다. 모든 ApplicationRunner 검증이 끝난 ApplicationReadyEvent 뒤에만 시작한다. 작업 예외는 원문 없이 집계하며 다음 tick을 죽이지 않는다.

## 3. 확정한 PR1 기술 범위와 후속 결정

- cleanup-r1 후보 중 PR1 범위의 job 상태/BSON/null·공통lifecycle 비교·CLEANUP fence·30초 lease·100후보·24시간재확인·7일기한·기본OFF를 구현했다. PURGE writer/100문서 삭제 상한은 PR2로 남긴다.
- lease 갱신 API는 현재 version/token/epoch가 유효할 때만30초 연장한다. PR1의 단일 외부 확인 step은5초 예산이므로 자동10초 갱신 루프를 돌리지 않는다. 향후 여러 step 조회를 붙일 때10초 갱신 orchestration을 연결해야 한다. 정지한 provider를 lease 무한 갱신으로 숨기지 않는다.
- provider 포트는 deadline 준수 계약을 받으며5초 후 반환한 결과는 폐기한다. PR1에는 실제 HTTP가 없고 thread 강제 중단도 없다. 실제 adapter의 transport timeout/페이지 재개는 PR3 검증 대상이다.
- Slack은 Summary/Sink interface·집계·KST09시 일일 요약/장애 신호·중복 억제의 fake 테스트까지만 구현했다. durable alert collection/index·실전송·외부 watchdog·장애 임계치 운영 분류는 후속이다. PR1 signal은 일반 BLOCKED가 아니라 기술 실행 예외를 나타내며 실제 심각도 판정으로 간주하지 않는다.
- 채널/담당·미해결 보존 종료일·provider coverage/drain·자동 삭제 ON 승인과 실제 DB 이관 승인은 미결이다.

## 4. 안전성·제약

- CLAIM/RENEW/COMPLETE마다 CLEANUP control 쓰기 CAS와 exact job version/token/epoch 검사. ALL_BLOCKED/control 소실/fenceOFF/구 schema는 거절한다. PURCHASE_BLOCKED에서는 보류 처리 가능, READY 판정 불가.
- lease 만료(now>=until) 또는 epoch 변경 후에는 BLOCKED 결과조차 옛 worker가 덮어쓰지 못한다. crash 후 lease 만료와 새 worker로 재시도한다.
- subject snapshot은 lifecycle/account version·전체 inactive ref·cursor activity·pending count를 포함한다. 결과 저장 시 다시 읽어 변경이 있으면 VERSION_CHANGED다. 신규 금융 writer의 실제 lifecycle CAS 연계는010/PR3에 남아 있으며 이 구현만으로 삭제 경합 완료라고 하지 않는다.
- 동일 탈퇴가 재전달돼도 최초 eligibleAt·작업 상태·최초 미해결/7일기한을 초기화하지 않는다. firstUnresolvedAt+7일 관계와 proof1시간상한/epoch/null 관계는 DB validator도 검사한다.
- runtime 알림 sink는 전송하지 않는다. fake sink 실패는 job에 영향을 주지 않고 지표만 올린다. PR1 일일/incident 중복 억제는 메모리이며 재시작 중복 가능,운영 전송의 durable 재시도 구현이 아니다.
- financial retained는 다음 due를 제거하지만 불필요 사용자정보까지5년 보관하라는 뜻이 아니다. 금융 보존 인계·예외 보존 처리 도구는 별도다.

## 5. schema·기동 절차

manifest는 [payment-cleanup-schema-r1.json](../../src/main/resources/payment-cleanup-schema-r1.json). 기존 WAITING insert가 cleanup 객체 없이 계속 유효하며 userId UUID 버전/variant를 기존 withdrawal 계약보다 좁히지 않는다. 신규 lease/epoch/job ID는 해당 UUIDv4 규칙이다.

기존 `ux_payment_cleanup_subject`, `ix_payment_cleanup_due`를 보존하고 `ix_payment_cleanup_review(state,cleanup.reviewDueAt,_id)`만 추가한다. TTL/partial/hidden/sparse를 추가하지 않는다. root의 기존 확장 허용,cleanup 하위는 unknown field 거절이다.

설치 절차(이번 실행하지 않음):

1. replica-set·기존 foundation/withdrawal/control4 준비 및 backup/변경 승인 확인.
2. 모든 writer를 격리·drain하고 control을 ALL_BLOCKED로 설정한다. Boolean 인수는 실제 격리 증명이 아니다.
3. 유지보수 프로세스에서 `CleanupSchema.installUnderMaintenance(db, true)`를 호출한다. 서버 startup/request에서 호출하지 않는다. 전용 운영 CLI 포장은 이번 범위가 아니다.
4. helper는 기존 collection/index와 기존 row를 검사하고, validator 없음/정확r1만 허용해 알려지지 않은 drift를 거절한다. 기존 row의 r1 불일치는 자동 backfill하지 않는다.
5. 별도 승인된 운영 절차로 barrier를 재개하고 모든 cleanup flag false로 배포한다. schema/index 변경은 startup에서 자동 설치하지 않는다.

환경 설정은 `BILLING_PAYMENT_CLEANUP_RECONCILIATION_ENABLED`, `BILLING_PAYMENT_CLEANUP_PURGE_ENABLED`, `BILLING_PAYMENT_CLEANUP_ALERTS_ENABLED`, 모두 false 기본이다. 후자 두 설정은 이 PR1에서 true 금지다. reconciliation ON은 SANDBOX/PRODUCTION exact 환경, fence-required,control4 및 exact cleanup schema 검증을 요구한다.

rollback은 cleanup worker OFF·drain부터 한다. schema/state를 모르는 구 cleanup writer 재투입 금지. 기존201 탈퇴 applier와204 작업은 보존하며 proof 없는 legacy를 READY로 만드는 migration은 없다.

## 6. 검증과 다음 작업

대상 테스트는 `CleanupCoreTest`, `CleanupIntegrationTest` 신규34개다. fake clock/ports/sink와 disposable Mongo7 replica-set으로legacy·기산시각·lease 갱신/만료·다중worker·epoch/버전경합·schema오류·unknowncommit·rollback·기본OFF·deadline·비식별요약을 검증했다. 최종 `./gradlew clean test bootJar`: 전체1,038개 통과/실패0/오류0/skip0, 패키징 성공. 이는 실제 provider/Slack/배포 검증이 아니다.

처음 unknowncommit 테스트에서 shutdown 계열 오류91이2초예산을 넘겨 실패해,동일session commit재시도 자체를 검증하는 기존복구테스트의errorCode1+UnknownTransactionCommitResult로 수정했다. 예산초과를성공으로바꾼production수정은없다.

다음은 PR2의 실제 삭제 코어/공통PURGE fence와 삭제 경합 검증이다. PR3 실제 거래 writer/provider·drain coverage,206 실환경이관/복원/알림/권한·삭제활성화는 별도다. Jira 상태/담당자 변경이나 배포·commit/push는 수행하지 않는다.
