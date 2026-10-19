# TMI-212 A 구현 안내 — 전체 3차 완료 아님

> 후속 B 구현에서 [webhook 수신](TMI-212-webhook.md)과 purchase-core-r3를 추가했다. 아래 A 완료 당시 r2/미연결 설명은 해당 시점 기록이며, 현재 runtime/schema는 B 안내를 함께 따른다. C/D는 여전히 남아 있다.

- 2026-10-13 / feat/TMI-212-revenuecat-worker-reconciliation
- [계획](../plans/PLAN-010-phase-3-revenuecat-worker-reconciliation.md), [exact 규격](../contracts/TMI-212-revenuecat-adapter-r1.md)

## 구현한 것

- RevenueCatReadClient: 계정 purchases 목록/단건 GET, 서버 고정host/project/environment,검증된ref·next cursor 재구성,HTTP오류·Retry-After 분류.
- RevenueCatPurchaseDecoder: strict JSON·owner/original owner/store/env/catalog/quantity/time/status 검증,API namespace 멱등key와 Store거래HMAC,USD revenue 무사용/null금액.
- RevenueCatHttpTransport: read-only HTTPS,connect2초/전체5초·응답1MiB·redirect/proxy없음·안전한예외. 실제호출없음.
- PurchaseReferenceCipher: 별도AES256-GCM·random nonce·환경/project/app/transaction AAD. 최소RC reference용helper이며 DB저장/키설정미연결.
- PurchaseCore: null금액검증구매지급후known금액보완,MONEY_CONFIRMED unique효과,projection+원장+pending같은Tx,기존known유지/충돌검토·동시/unknowncommit수렴. 이용기간과timeline불변.

## 이관 주의

purchase-core-r2는 payment_ledger type에 MONEY_CONFIRMED를추가한다. 구r1운영DB는자동수정하지않는다. writer격리/ALL_BLOCKED후 `PurchaseCoreSchema.upgradeMoneyEvidenceUnderMaintenance`로구manifest를검증하고validator만변경한다. index/drop/backfill없음. 재실행멱등이며드리프트면중단한다. 구writer는중지/교체하고구r1로자동rollback하지않는다. 실제운영DB이관은이번에수행하지않았다.

## 남은 것

- webhook HMAC receiver/최소durable수신 및 provider security chain.
- sharedquota·lease/epoch·명시worker schema·scheduler·sync source완료연결.
- account reconciliation·metadata collection/암호화reference영속화·case/보존정리·운영알림.
- provider 실거래/SDK매핑·키/프로젝트·실환경부하/인증검증,011/012·205금융coverage.

신규adapter는명시생성형이며Springbean/자동외부호출없음. sync ON차단과flags기본OFF를유지한다. 이A실행결과를TMI212전체완료나실제RC/Store검증으로사용하지않는다. Jira상태변경/배포/commit/push없음.

## 검증

최종 `./gradlew clean test bootJar` 성공: **1,268통과/실패0/오류0/skip0**, 기존1,215대비53추가. `git diff --check` 통과. 중간테스트소스의Java선언/람다컴파일오류를보완후집중및clean전체재실행성공. 실제provider/정상HTTPS transport E2E는별도다.

가짜transport/JSON 응답과실제Mongo replica-set테스트. payload/type/owner/store/env/ID·page변조/중복/상한·HTTP오류/Retry-After·reference변조·금액보완/순서/경합/장애/명시이관을검증한다. 실제provider HTTP/Store호출·정상HTTPS transport E2E는미실행,전체테스트결과는작업기록에기재한다.
