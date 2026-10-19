# TMI-205 PR2 — 조건부 삭제 코어와 OFF 유지

- 날짜: 2026-10-13 / 브랜치: feat/TMI-209-billing-cleanup-foundation
- 기준: [cleanup 기술 규격](../contracts/TMI-205-cleanup-technical-spec-r1.md), [205 계획](../plans/TMI-205-withdrawal-reconciliation-and-purge.md).

## 1. 5줄 결론

1. READY 증거를 보존하는 삭제 claim과 최종 원자 삭제 코어를 구현했다.
2. 확인 완료 직후 삭제 시도·crash 이후 tick 회수·증거 만료/변경 재검증을 테스트용 구성으로 연결했다.
3. production 구성은 여전히 미준비 증거 포트만 사용하며 purge ON은 기동 거절한다. 운영 삭제는 불가능하다.
4. r1 manifest를 보존하고 r2 validator를 추가했다. 기존 r1에서 명시 maintenance 이관만 허용한다.
5. PR3 실제 금융/RevenueCat/Store/drain 연결과206 운영 검증·활성화는 별도이며 전체205 완료가 아니다.

## 2. 반드시 읽어야 하는 내용

`CleanupPurger`는 package-private 코어이며 Spring bean·HTTP route가 없다. 같은 패키지의 테스트 전용 wiring이 LocalReader와 FinalPolicy를 주입한다. production `CleanupConfiguration`에는 이 wiring이 없고 `purge-enabled=true`는 reconciliation OFF일 때도 실패한다. 테스트의 fake 승인 policy를 실제 부재 증명으로 사용하지 않는다.

최종 policy는 네트워크를 호출하지 않는 bounded 로컬 검사 포트다. exact subject·저장된 adapter/coverage/drain 버전·checkedAt/now를 받아 현재 유효성을 확인하며 불명은 false다. PR3에서 실제 환경의 토큰/구 요청 drain과 provider coverage로 구현해야 한다. 별도 clock attestation이나 승인 DB를 새로 만들지 않았다.

## 3. 동작·수치

- 확인 완료는 READY/proof를 durable commit한 뒤 별도 삭제 claim을 시도한다. 삭제 claim은 proof/checkedAt/validUntil을 그대로 두고30초 lease와 job version CAS만 갱신한다.
- 삭제는 기존 PaymentStore의2초 Transaction budget 안에서 control PURGE(OPEN-only)·job lease·epoch·WITHDRAWN/15일·lifecycle/account/cursor·pending0·local/policy 증거를 검사한다. lifecycle 쓰기 CAS와 모든 삭제를 같은 session에서 수행한다.
- 삭제 목록은 account refs·ACCOUNT_LOOKUP cursor·account·job·불필요 lifecycle뿐이다. 전체100문서 상한이며101개는 DOCUMENT_LIMIT/BLOCKED다. 추가 cursor/진행lease는 무삭제 보류한다. exact row/deletedCount 불일치는 전체 rollback이다.
- 최종 DB 작업 동안 lease/proof가 만료돼도 전체 rollback한다. 삭제 lease는 자동 갱신하지 않는다. bounded Tx가 끝나지 못한 작업은30초 만료 뒤 새 worker가 재검증한다. 확인 단계의 기존 renew API와 구분한다.
- 완료 commit 확인만 PURGED다. 동일 lease 재시도에서 전체 대상 부재는 ALREADY_ABSENT이며 성공 건수로 합치지 않는다. 부분 잔존은 오류, unknown commit budget 소진도 성공으로 보고하지 않는다. 영구 per-user receipt는 추가하지 않았다.
- 기존 worker10초 tick에 READY/만료 삭제lease 회수를 연결했다. tick 최대100건/30초·keyset·동일 tick 같은 job 중복 처리 방지. 매시간 후보 discovery/미해결24시간 재확인/최초7일 검토는 유지한다.
- runtime purge OFF에서는 READY nextAttemptAt=now+1시간, 삭제 lease/호출 없음. 테스트 삭제 capability에서는 READY nextAttemptAt=now, 직후 실행 또는 후속 tick 회수다.
- `billing_cleanup_purge_result{result=...}`는 고정 enum 비식별 집계이며 exactly-once 사용자별 감사가 아니다. PURGED와 ALREADY_ABSENT는 별도다.

## 4. r2 이관과 rollback

[r1](../../src/main/resources/payment-cleanup-schema-r1.json)은 변경하지 않았다. [r2](../../src/main/resources/payment-cleanup-schema-r2.json)는 subset 이름과 lease 관계만 변경한다: VERIFYING 필수, READY 선택, 나머지 금지. proof는 READY 필수/다른 상태 금지를 유지한다. row cleanup.schemaVersion=1과 기존 인덱스/BSON/retention은 그대로다. 독립 subset의 exact validator bytes가 이관 버전을 구분한다.

runtime `CleanupSchema.verify`는 r2만 인정한다. `installUnderMaintenance`는 기존 validator 없음/정확 r1/정확 r2만 허용하고, 격리 확인 인수·모든 control ALL_BLOCKED·기존 row 검사 후 r2를 설치한다. 실제 writer 격리/권한은 운영 확인 대상이며 Boolean 인수가 증명은 아니다. startup DDL/자동 downgrade 없음.

운영 이관 전 worker OFF·writer drain·ALL_BLOCKED·backup/권한/변경 승인 필요. 구 PR1 worker는 READY 삭제lease를 모르므로 drain/lease 해소·validator 호환 확인 없이 되돌리지 않는다. 이번 실행은 disposable 테스트 DB에서만 이관했고 운영 DB에 적용하지 않았다.

## 5. 검증·미확인

최종 `./gradlew clean test bootJar` 성공: **65 suites / 1,066개 통과 / 실패0 / 오류0 / skip0**, 패키징 성공. CleanupCoreTest11개와 CleanupIntegrationTest50개(이번27개 추가)가 포함된다. 기존 별도 LC fixture 등 저장소 전체 테스트 결과이며 실환경 검증이 아니다. 문서 local link35개·git diff --check·r1/r2 index 동일성도 확인했다.

테스트는 fake 증거와 disposable replica-set Mongo를 사용한다. 삭제/새 금융 입력의 양쪽 순서와 실제 동시 경합, 중복 claim, 증거/lease 경계, epoch/BLOCK, 정책/cursor 변경,100/101상한, 중간 delete 실패 rollback, unknown commit, schema 이관, 직후 실행/다음tick 회수를 포함한다. 최종 점검에서 같은 tick 중복처리를 제한하고 PURGE 검사로 일반 PURCHASE_BLOCKED 상태의 비삭제 확인까지 막지 않도록 보완한 뒤 전체 검증을 재실행했다.

주요 코드: [CleanupPurger](../../src/main/java/web/tosunsaeng/billing/domain/payment/cleanup/CleanupPurger.java), [CleanupProcessor](../../src/main/java/web/tosunsaeng/billing/domain/payment/cleanup/CleanupProcessor.java), [CleanupWorker](../../src/main/java/web/tosunsaeng/billing/domain/payment/cleanup/CleanupWorker.java), [CleanupSchema](../../src/main/java/web/tosunsaeng/billing/domain/payment/cleanup/CleanupSchema.java), [PURGE fence](../../src/main/java/web/tosunsaeng/billing/domain/payment/recovery/PaymentRecoveryFence.java), [통합 테스트](../../src/test/java/web/tosunsaeng/billing/domain/payment/cleanup/CleanupIntegrationTest.java). HTTP/API·인증·provider wire·기존 인덱스/보존기간은 변경하지 않았다.

늦은 금융 입력은 **test-only writer**가 같은 control/lifecycle을 쓰고 미매핑 inbox로 보내는지 검증했다. 실금융 수신·purchase/ref 자동 upsert 방지까지 완성했다는 뜻이 아니다. 삭제 후 만료 user request 차단은 기존 authorizationDeadline으로 검증했으며, 과거 토큰/진행 요청이 모두 소진됐다는 실환경 증명은 남는다. 무료·구매 원장 처리/Identity·LC 코드는 변경하지 않았다.

Jira TMI-205를 직접 조회했고 하위 항목은 TMI-209(PR1)만 확인했다. 검색 권한 오류와 달리 직접 읽기는 성공했다. 신규 PR2 이슈 생성/수정·상태 전환은 하지 않았다. 실provider/Slack/배포/commit/push 없음. 기존 LC 문서·PR1·작업기록 변경은 보존했다.
