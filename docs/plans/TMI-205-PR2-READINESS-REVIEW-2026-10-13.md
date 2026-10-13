# TMI-205 PR2 착수 전 대조

- 날짜: 2026-10-13 / 브랜치: feat/TMI-209-billing-cleanup-foundation
- 범위: 계획·기술 규격·PR1 코드 정적 검토. 구현/정본 규격 변경/운영 삭제 승인이 아니다.

후속 2026-10-13: 사용자가 증거 보존 삭제 lease·확인 직후 시도·지연/변경 재검증을 승인했다. [기술 규격 §5.3.1](../contracts/TMI-205-cleanup-technical-spec-r1.md)에 반영했으며 아래는 당시 검토 근거다. 코드/manifest 이관·실환경 활성화 완료는 아니다.

## 1. 5줄 결론

1. 다음 구현 범위는 PR2 삭제 코어이며 실제 삭제 활성화는 계속 OFF다.
2. PR1의 job/증거/lease/CLEANUP fence는 있으나 PURGE writer와 삭제 코어는 없다.
3. READY 증거와 삭제 lease의 동시 보유가 현재 schema에서 불가능하므로 PR2 전이·schema 이관을 먼저 명시해야 한다.
4. 금융 writer/실제 provider/drain 증거는 PR3 및 운영 gate로 유지하고 PR2에서는 fake 기반 경합 검증만 한다.
5. Jira 검색은 앱 미설치/권한 403으로 실패했다. 기존 PR2 이슈 유무·최신 상태는 확인하지 못했으며 생성/수정하지 않았다.

## 2. 반드시 읽어야 하는 내용

PR2를 구현할 수 있다는 것은 실제 고객 연결정보를 삭제해도 된다는 뜻이 아니다. 현재 runtime evidence는 UNKNOWN-only이고 purge ON은 기동 실패다. 이 보호는 PR2에서도 유지한다. 무료·금융 원장과 Identity/LC 데이터는 삭제 대상이 아니다.

## 3. 결정·보완 권장안

새 상품 정책 선택은 필요하지 않다. 구현 전에 다음 기술 상세를 PR2 규격으로 정리하는 것을 권장한다.

- **READY 삭제 lease:** 현재 `claim()`은 proof를 지우고 VERIFYING으로 바꾼다. schema는 VERIFYING에서만 lease, READY에서만 proof를 허용한다. 별도 삭제 claim 경로에서 READY+proof를 유지하며 lease를 허용하는 additive 개정, exact job version/token/epoch CAS, 만료 lease 회수와 proof 만료 후 재검증 규칙을 명시한다. 기존 validator와의 명시적 maintenance 이관/rollback도 필요하다. 이는 후보이며 이번에 schema를 변경하지 않는다.
- **삭제 일정:** 현재 READY nextAttemptAt은 now+1시간이고 proof 수명은 최대1시간이다. 이를 그대로 쓰면 다음 처리 때 이미 증거가 만료된다. 테스트용 삭제 경로는 READY 직후 bounded 실행, 실제 purge OFF 경로는 현재 시간당 재확인 유지로 구분하는 것이 합리적이다. 분리 worker라면 재확인 작업과 삭제 작업의 claim 경합도 검증한다.
- **PURGE·최종 재확인:** fence OFF/없는 control/ALL_BLOCKED/PURCHASE_BLOCKED는 삭제 거절, OPEN만 조건부 허용. lifecycle 쓰기 CAS와 동일 Tx에서 최종 local 증거·policy version·pending0·cursor lease·문서 귀속·삭제 수를 검사한다. 계정당100개는 ref 수가 아니라 전체 삭제 문서 합계이며 101개면 무삭제다.
- **완료 결과:** 전체 rollback/unknown commit/ALREADY_ABSENT/부분 잔존을 구분하고 부재를 정확 삭제 성공으로 계상하지 않는다. 삭제 후 과거 요청의 재생성과 늦은 금융 입력 격리는 fake 경합으로 검증하되 실제 금융 연결 완료로 보고하지 않는다.

## 4. 위험과 미확인

Jira 확인 실패로 중복 이슈 생성 방지 확인이 남았다. provider 부재 증명·실금융 writer·실token/request drain·운영 담당/보존 예외가 아직 갖춰진 것으로 보지 않는다. 최근 시간 증명 단순화는 Identity 발신 한정이며 구매/삭제 drain 보호를 제거하지 않는다.

상위 계획에는 과거 후보 표현(3 writer, nextReviewAt, 별도 ref/financial revision)이 남아 있다. 최신 PR1 보충과 cleanup-r1을 우선 대조하고 PR2 계획 정리 시 현재 CLEANUP writer·reviewDueAt·공통 lifecycle.version으로 본문을 정합화해야 한다. 이번 검토에서 정본을 임의 개정하지 않았다.

## 5. 권장 작업 순서

PR2 전이/schema·삭제 일정 규격 보완 → Jira 기존 이슈 확인 및 사용자 승인 후 생성/연결 → PURGE/삭제 코어 → replica-set의 양쪽 commit 순서·unknown commit·재생성 테스트 → 전체 테스트. PR3 실제 연결 및206 운영 검증 전 purge ON 거절 유지.

## 6. 근거와 검증

- [205 계획 §5.9](TMI-205-withdrawal-reconciliation-and-purge.md): PR2/PR3 범위 분리.
- [cleanup-r1 §5.3~5.8](../contracts/TMI-205-cleanup-technical-spec-r1.md): 삭제 lease/Transaction/100문서/기본OFF 규격.
- [CleanupProcessor](../../src/main/java/web/tosunsaeng/billing/domain/payment/cleanup/CleanupProcessor.java): claim proof 제거, complete READY 1시간 due.
- [schema](../../src/main/resources/payment-cleanup-schema-r1.json): VERIFYING lease / READY proof 상호 제약.
- [CleanupConfiguration](../../src/main/java/web/tosunsaeng/billing/domain/payment/cleanup/CleanupConfiguration.java): UNKNOWN-only·purge ON 거절.
- [PaymentRecoveryFence](../../src/main/java/web/tosunsaeng/billing/domain/payment/recovery/PaymentRecoveryFence.java): 현재 ACCOUNT/PUSH/RECOVERY/CLEANUP, PURGE 없음.
- [PaymentAccountService](../../src/main/java/web/tosunsaeng/billing/domain/payment/application/PaymentAccountService.java): lifecycle 부재 시 생성과 authorizationDeadline 검사. 따라서 삭제 후 재생성 안전성은 실제 drain 전제가 필요하다.

정적 대조만 수행했다. 코드 변경이 없어 Gradle은 재실행하지 않았고 이전1,039개 통과를 PR2 검증으로 사용하지 않는다. 기존 별도 LC/PR1/문서 변경은 보존했다.
