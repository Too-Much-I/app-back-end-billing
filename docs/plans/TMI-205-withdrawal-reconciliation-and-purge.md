# TMI-205 — 미구매 탈퇴 계정 거래 확인·15일 조건부 정리

> 2026-10-13 PR1(TMI-209) 구현: 상태/lease/판정/검토 일정 및 fake 알림 기반을 추가했다. 실제 wiring은 UNKNOWN-only, purge/외부alerts true는 기동 거절이다. schema/동작/PR1 한계는 [구현 안내](../runbooks/TMI-209-cleanup-foundation.md)를 따른다. PR2/PR3·실운영은 미완료이며 전체205완료가 아니다.

> PR0 상세 후보: [cleanup-r1 기술 규격](../contracts/TMI-205-cleanup-technical-spec-r1.md). 아래 refSetRevision/financialInputRevision 후보는 공통 lifecycle.version으로 단순화하고, 검토 기한은 cleanup.reviewDueAt으로 구체화했다. CLEANUP/PURGE writer·job/proof null 규칙·상태 전이·schema/index·Slack 전송 규격을 담았다. 기술 검토안이며 구현/운영 활성화 승인이 아니다.

- 작성일: 2026-10-13 / 최종 확인 브랜치: `develop` (기존 TMI-204 작업과 동시 LC 문서 변경 보존, 이번 작업에서 branch 전환 없음)
- 상태: 상세 계획 초안. 제품 정책은 기존 승인 유지, 아래 신규 기술 규격은 구현 전 검토 대상. 코드 구현·Jira 수정·운영 삭제 승인이 아니다.
- Jira: [TMI-205](https://to-teacher.atlassian.net/browse/TMI-205), 조회 당시 `해야 할 일`.
- PR1 하위 작업: [TMI-209](https://to-teacher.atlassian.net/browse/TMI-209) — 상태·lease·거래 판정·검토 일정 기반 구현. 2026-10-13 생성 및 parent205 확인, 해야 할 일·미할당. PR2/PR3 생성·구현·활성화는 별도.
- 기준: [009 삭제 계약](../contracts/PLAN-009-withdrawal-reconciliation-and-purge-contract.md), [PLAN-009](PLAN-009-payment-catalog-and-account.md), [PLAN-010](PLAN-010-purchase-ingestion-and-timeline.md), [TMI-204](TMI-204-withdrawal-recovery.md), [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md).

## 1. 5줄 결론

2026-10-13 PR2 로컬 코어 추가: [구현 안내](../runbooks/TMI-205-pr2-purge-core.md). READY 증거보존 삭제claim/직후실행·tick회수/PURGE fence/100문서 원자삭제와 r2 명시 이관을 구현했다. 실제 purge capability/provider 연결은 없고 운영 ON 기동거절 유지. PR3/206 미완료이므로 전체205 완료가 아니다.

2026-10-13 PR2 방향 승인: 삭제 claim은 READY 증거를 유지한 채 별도 lease를 잡는다. 확인 완료 commit 직후 bounded 삭제를 시도하고, 지연·중단 건은 durable due/tick으로 회수하되 증거 만료·상태 변경 시 먼저 재검증한다. purge OFF에서는 시간당 재확인만 유지한다. [cleanup-r1 §5.3.1](../contracts/TMI-205-cleanup-technical-spec-r1.md)이 상세 기준이며 실제 코드/schema/운영 삭제는 아직 변경하지 않았다.

1. 탈퇴 후 15일이 지난 계정 중 **실제 미구매가 확인된 계정의 구매 연결정보만** 정리한다. 15일은 무조건 삭제 시한이 아니다.
2. 매시간 대상 점검, 미확인 건 매일 재확인, 최초 미해결 후 7일 이내 담당 검토라는 승인 정책을 유지한다.
3. 지금은 상태·lease·증거 판정·삭제 Transaction을 fake 거래 포트와 replica-set 테스트로 구현할 수 있다. 운영의 실제 삭제는 OFF다.
4. 실제 거래 수신·RevenueCat 조회·미관측 거래 확인은 PLAN-010과 연결한다. 빈 조회 결과나 snapshot 성공만으로 미구매를 확정하지 않는다.
5. 삭제/거래 경합·토큰 만료·복원·예외 보존을 TMI-206/PLAN-013에서 검증한 뒤에만 자동 삭제 활성화를 승인한다.

## 2. 사용자가 반드시 읽어야 하는 내용

이 작업은 Identity 계정이나 시험 기록 삭제가 아니다. Billing의 `payment_accounts`, `purchase_account_refs`, 관련 조회 cursor와 cleanup 연결정보를 조건부 정리하는 작업이다. 무료 Claim·Grant·Reservation, 시험 기록과 금융 원장은 이 삭제 대상에서 제외한다.

구매가 발견되면 미구매 삭제 대상에서 빼고 기존 금융 5년 보존 절차로 넘긴다. 탈퇴를 환불로 바꾸거나 새 계정에 구매를 이전하지 않는다. 금융 보존이 불필요한 개인정보까지 5년 보관해도 된다는 뜻은 아니다.

**진행 순서는 205 기반 구현 → PLAN-010 실제 거래 연결 → 206/013 운영 검증이다.** fake 테스트 완료를 실제 미구매 증명이나 205 전체 운영 완료라고 보고하지 않는다. 실제 provider가 증명에 필요한 정보를 제공하지 못하면 자동 삭제는 보류하고, 최소 예외 기록의 목적·보존 종료일·담당자를 정한다. 매일 재시도만 하는 무기한 보관 구조로 끝내지 않는다.

기존 삭제 계약의 SigV4·remote generation/coverage 관련 과거 표현은 최신 [Identity–Billing 인증 계약](../contracts/WORKLOAD-JWT-AUTH-R1.md)과 TMI-204의 로컬 epoch·snapshot/feed 규격으로 해석한다. 205에서 인증 방식이나 외부 wire를 다시 변경하지 않는다. LC 인증 작업은 별도이며 본 계획 범위가 아니다.

## 3. 사용자가 결정해야 하는 사항

### 3.1 다시 결정하지 않는 승인 사항

- 탈퇴 시각 기준 15일, 매시간 점검·미확인 일일 재확인·최초 미해결 7일 이내 검토.
- 재가입자에게 기존 구매/기간권 자동 이전하지 않음, 무료권과 금융 보존 유지.
- RC 404/빈 목록을 거래 부재 증명으로 사용하지 않음, 실제 삭제 기본 OFF.
- 2026-10-13 승인: Slack은 알림 창구로 사용하고 일반 미해결은 일일 요약, 다수 계정에 영향을 주는 장애는 즉시 알림으로 분리한다. 처리 상태·기한은 Billing DB가 관리하며 Slack 확인으로 삭제를 승인하지 않는다(§5.8.1).

### 3.2 구현 전 기술 검토로 닫을 사항

- §5.4 증거 포트 및 §5.6 공통 writer fence를 PLAN-010과 맞춘다. fake만으로 실거래 writer까지 완료 처리하지 않는다.
- §5.7 schema subset·validator·인덱스 및 구 writer 호환성을 확정한다.
- 제안 초기값: 후보 batch 100, lease 30초(장기 조회 중 갱신), 삭제 증거 최대 나이 1시간, 한 삭제 Transaction 대상 최대 100개 document. 모두 성능/안전성 검토 대상이며 새 제품 정책이 아니다.
- 신규 public/internal API는 만들지 않는다. 운영 예외 검토 수단은 승인된 운영 도구로 설계하고 임의 DB 편집이나 강제 `READY` 전환은 제공하지 않는다.

### 3.3 실제 운영 전에 필요한 승인

- 실제 Slack 채널·접근 권한, 담당자·대체 담당자 지정, 미해결 최소 case의 필드·목적·보존 종료일과 종료 시 처리 방식. Slack 알림 구분 승인은 이 값들을 확정하거나 연동을 실행한 것이 아니다.
- 실제 token/진행 요청 수명, provider 미관측 거래 coverage와 늦은 거래 처리 검증.
- 이관 작업 시간·복원 절차, 자동 삭제 ON 승인. provider 지원 여부는 선택으로 대신할 수 없다.

## 4. 주요 위험과 미확인 사항

| 위험 | 처리 원칙 |
| --- | --- |
| Store 결제는 있으나 RC에 아직 없음 | `UNKNOWN`; 15일 경과·RC 빈 응답으로 READY 전환 금지 |
| RC 조회 중 새 거래 입력 | 모든 관련 writer의 lifecycle 쓰기 CAS와 pending 증거로 기존 proof 무효화 |
| 삭제 후 늦은 결제 | PLAN-010의 durable 미매핑 격리로 보존. account upsert·새 사용자 지급 금지 |
| 탈퇴 차단 기록 제거 후 과거 토큰/요청 재실행 | Identity 발급 차단과 실제 최대 수명·drain 확인, 최종 생성 경계 deadline 검사 |
| snapshot에 과거 탈퇴자가 빠짐 | 부재를 ACTIVE/미구매 증거로 사용하지 않음. 해당 계정 근거 부족은 보류 |
| epoch 변경·복원·control 소실 | 이전 proof와 lease 무효화, 재대사 전 삭제 금지 |
| 보류 건 장기 축적 | 최초 미해결 시각/검토 기한 고정, 담당 검토·목적별 최소 보존; 자동 보존 연장 금지 |

Google Orders 보완 승인은 **알려진 거래의 부분 환불 확인**이다. 전체 사용자 미구매 검색 권한으로 확대하지 않는다. 현재 provider coverage의 실증은 이 계획 작성에서 수행하지 않았다.

## 5. 현재 작업과 직접 관련된 설계

### 5.1 현재 구현과 추가 범위

| 항목 | 확인된 현재 상태 | 205 작업 |
| --- | --- | --- |
| 탈퇴 수신·적용 | 공통 `PaymentWithdrawalApplier`가 lifecycle/account WITHDRAWN, ref 비활성, WAITING job 생성 | 기존 사실·기산점 재사용, 중복 수신이 작업을 초기화하지 않도록 회귀 |
| 삭제 후보 | `eligibleAt=withdrawnAt+15일`, BSON 밀리초 올림, account 없는 탈퇴도 후보 생성 | lease·증거·검토 일정·조건부 삭제 상태 추가 |
| 계정 발급 | lifecycle version 쓰기, stable ref, 별도 ACCOUNT_LOOKUP cursor activity, transaction 전후 authorizationDeadline 검사 | 새 cleanup과 동일 fence 경합 검증; 현재 검사만으로 운영 drain 완료라 하지 않음 |
| 복구 | 로컬 control/epoch·snapshot/feed·operator, 기본 OFF | 삭제 전용 writer 권한과 epoch 검증 추가 제안 |
| 금융 조회/삭제 worker | 현재 payment 패키지에는 205 worker와 실제 provider 거래 증거 연결 없음 | 기반 코어와 fake 검증을 먼저 구현, 실제 연결은010 의존 |

### 5.2 상태와 주기

| 상태 | 의미 | 다음 처리 |
| --- | --- | --- |
| WAITING | 15일 전 또는 아직 확인하지 않은 후보 | eligibleAt 이후 매시간 점검 대상 |
| VERIFYING | 유효 lease를 가진 worker가 확인 중 | 거래 관측·증거 검증 후 분기; lease 소실 시 결과 폐기 |
| BLOCKED | 미해결 입력·증거 부족·인증/계약 장애 | 자동 일일 재확인, 최초 미해결 7일 이내 담당 검토 |
| FINANCIAL_RETAINED | 검증된 구매/금융 보존 자료 발견 | 15일 삭제에서 제외, 별도 금융 보존 절차로 인계 |
| READY | 모든 삭제 전제와 최신 증거 충족 | 최종 Transaction에서 다시 확인; proof 만료/변경 시 재검증 |
| PURGED | 삭제 완료 결과 | 사용자 연결 행을 이 상태로 영구 남기지 않음; 비식별 집계만 |

worker crash 후 만료된 VERIFYING lease는 새 version으로 재획득한다. 재시도·reason 변경·중복 탈퇴가 `firstUnresolvedAt`, 검토 deadline, eligibleAt을 뒤로 미루면 안 된다. 담당 검토도 증거 없는 삭제 승인이 아니다.

일시 네트워크 장애의 짧은 재시도는 provider 공통 backoff를 재사용하되 bounded 실행으로 한다. 미해결로 분류된 이후 정기 재검토는 일 1회다. 401/403은 인증 복구 대상이며 빠른 재시도 루프 금지. 미해결 7일은 강제 삭제나 강제 완료 기한이 아니다.

### 5.3 처리 순서

1. due job을 batch 한도 내 조회하고 exact version CAS로 lease를 얻는다. 유효 epoch와 작업 시작 상태를 기록한다.
2. 짧은 DB 읽기로 lifecycle/account version, 모든 active/inactive ref 집합, cursor 활동과 pending/금융 입력 revision을 확보한다.
3. 검증된 금융 기록이 있으면 FINANCIAL_RETAINED, 미처리 입력이 있으면 BLOCKED로 수렴한다.
4. provider 확인이 필요하면 Transaction 밖에서 모든 대상 ref·환경·앱·스토어와 전체 페이지를 조회한다. lease가 끝나거나 응답 일부가 누락되면 삭제 proof를 만들지 않는다.
5. §5.4 규격으로 거래 관측과 부재 증명을 구분하고, 처음 캡처한 version/epoch가 현재도 일치하는지 검사해 결과를 저장한다.
6. purge OFF이면 어떤 상태에서도 삭제하지 않는다. ON이어도 READY의 최신 증거·drain·모든 writer 연결을 최종 재확인한 경우에만 §5.6 Transaction을 실행한다.

account 없는 탈퇴도 lifecycle/job만 정리 가능한 후보이나, 계정 부재 하나만으로 과거 ref 발급·거래 부재를 추정하지 않는다. 누락/복원 이상이 발견되면 보류한다.

### 5.4 거래 확인 포트와 증거 제안

내부 Java port를 분리한다. wire DTO나 provider가 실제 제공한다고 주장하는 필드가 아니다.

- `LocalPaymentEvidenceReader`: Purchase/ledger/entitlement, pending sync/inbox/job/case, ref 집합과 관련 revision을 읽는다. 미구현 collection을 조회해 비었다는 이유로 NONE을 반환하지 않는다. adapter 미설치는 UNKNOWN이다.
- `ProviderPurchaseEvidenceReader`: 인증된 provider 조회의 완료 여부·거래 발견·coverage 범위를 반환한다. fake 구현은 테스트 source에만 둔다.
- `CleanupEligibilityEvaluator`: 탈퇴 사실·15일·local/provider 증거·token drain·epoch를 합쳐 결론을 내리는 순수 판단 로직.

| 결과 | 조건 | 삭제 의미 |
| --- | --- | --- |
| PURCHASE_FOUND | 검증된 거래가 해당 account/ref에 귀속 | 보존, purge 금지 |
| UNKNOWN | 조회 실패/부분 페이지/미설치 adapter/coverage 부족/모호한 연결 | BLOCKED |
| NO_PURCHASE_CONFIRMED | 로컬 미해결 없음 + 전체 ref 확인 + 승인·검증된 provider coverage + 동일 version/epoch | READY의 필요조건, 최종 CAS 검사는 별도 |

최소 proof 후보: `checkedAt`, `validUntil`, `recoveryEpoch`, `lifecycleVersion`, `accountVersion`(계정 없는 경우 명시적 부재), `refSetRevision`, `financialInputRevision`, `cursorActivityAt`, `adapterVersion`, `coverageVersion`, `result`, `reason`.

refSetRevision/financialInputRevision은 **현재 이미 존재하는 필드가 아니다.** PLAN-010의 모든 관련 writer가 같은 lifecycle version을 쓰는 방식으로 대체할 수 있는지 PR0에서 확인하고, 중복 revision을 불필요하게 추가하지 않는다. proof의 null/생략/BSON 타입·결과별 필수필드는 schema manifest에서 고정한다. count/digest만으로 ref 전체 coverage를 추정하지 않는다.

`validUntil ≤ checkedAt+1시간`은 제안한 로컬 신선도 상한이지 외부 결제 지연 상한이 아니다. 404/빈 페이지는 관측일 뿐이며 coverage 계약이 없으면 항상 UNKNOWN이다. 결제 미개방 환경도 외부 구매 경로 전체가 닫혔다는 검증 없이 예외로 처리하지 않는다.

### 5.5 204와의 연결

현재 `PaymentRecoveryFence.Writer`는 ACCOUNT/PUSH/RECOVERY만 존재한다. `PURGE` 추가를 제안한다. 기존 역할을 가장해 호출하거나 recovery flag OFF일 때 fence를 우회하지 않는다.

| control 상태 | 삭제 제안 규칙 |
| --- | --- |
| 없거나 fence 비활성/미지원 schema | 삭제 금지 |
| ALL_BLOCKED | 삭제 금지 |
| PURCHASE_BLOCKED | 이관/복구 중 삭제 금지, 증거 확인만 허용 가능한지 운영 절차에서 검증 |
| OPEN | 삭제의 필요조건일 뿐. 계정별 증거·별도 purge flag 필요 |
| epoch 변경/복원 | 진행 proof/lease 폐기, 새 epoch에서 재검증 |

최종 삭제 Transaction은 local control에 쓰기 CAS를 수행하여 운영 BLOCK/ROTATE와 직렬화한다. 보편적인 과거 이력 `historicalUncertainty` 하나만으로 모든 계정을 영구 보류하지 않는다. 정상 feed lag도 전역 삭제/구매 차단으로 확대하지 않는다. 실제 GAP/복원에서 영향 범위를 특정할 수 없으면 해당 범위를 넓게 보류하고 현재 상태 재동기화 후 계정별로 재판정한다. remote ACK·별도 상세 gap 원장을 재도입하지 않는다.

### 5.6 삭제 Transaction·늦은 입력·재생성

최종 삭제에는 다음 조건을 모두 요구한다.

- canonical withdrawnAt에 기반한 eligibleAt 경과, WITHDRAWN, exact account/lifecycle/ref/proof version.
- 유효 lease·동일 epoch·PURGE writer 허용, proof 신선도/coverage 충족.
- 금융 보존 자료 없음, `pendingEvidence=0`, 진행 중인 다른 sync/command/cursor lease 없음.
- Identity가 탈퇴 userId를 재사용하지 않고 신규/refresh 발급을 막음. 실제 구매 token 상한 30분/skew60초 및 진행 요청 deadline/drain 검증. 단순히 15일 경과로 모든 외부 결제가 종료됐다고 추정하지 않음.

짧은 Transaction에서 control 쓰기 CAS → lifecycle 쓰기 CAS → 최종 조건/문서 수 검사 → 해당 계정의 ref·cursor·불필요 cleanup 연결·account·보존 필요 없는 lifecycle 제거를 원자 실행한다. 외부 HTTP는 넣지 않는다. 제안 한도 100 documents 초과는 BLOCKED로 남기고 별도 분할 manifest 승인 전 임의 분할 삭제하지 않는다.

모든 account/탈퇴/금융 inbox 수신·sync/구매 반영 writer가 같은 lifecycle fence를 **쓰기**로 통과해야 한다. webhook worker가 나중에 처리할 때가 아니라 최소 inbox를 durable 수신하는 시점에 pending 증거와 fence가 함께 반영되어야 한다. 연결 불명 입력은 durable 격리하며 관련 계정 영향이 불명하면 proof의 coverage를 제한한다. 이 연결은 PLAN-010이 제공하며 그 전에는 실제 purge를 활성화하지 않는다.

- 거래 입력 먼저 commit: 삭제 proof 무효화·CAS 실패 후 재대사.
- 삭제 먼저 commit: 늦은 거래를 durable 미매핑 case로 수신, account 재생성/재가입 자동 지급 금지. 입력을 버리거나 이미 환불됐다고 간주하지 않음.
- unknown commit: 동일 Transaction 결과를 재확인하고 일부만 삭제된 것으로 가정해 보상 upsert하지 않음. 원자 삭제 대상 전체 부재와 성공 commit 확인을 구분하며, 확인 불가면 UNKNOWN으로 보고함. 감사 집계 중복을 금융 효과로 취급하지 않음.
- 늦은 탈퇴 replay: 기존 공통 applier가 남긴 WITHDRAWN/job은 다시 조건부 정리 대상으로 취급. 새 구매 권한은 만들지 않음. inbox TTL 후 replay도 테스트.

per-user 영구 삭제 tombstone/hash로 해결하지 않는다. 개별 결과 receipt가 필요해지면 개인정보 연결 가능성과 보존 종료일을 먼저 승인받는다. 현재 제안 감사는 비식별 집계이며 정확히 한 번의 per-user 영구 감사 이력을 약속하지 않는다.

### 5.7 schema와 이관

기존 [foundation index](../../src/main/resources/payment-foundation-indexes.json)와 [withdrawal index](../../src/main/resources/payment-withdrawal-indexes.json)를 유지한다.

| collection | 기존 index | 계획 변경 |
| --- | --- | --- |
| payment_account_cleanup_jobs | ux_payment_cleanup_subject(environment,userId) unique; ix_payment_cleanup_due(state,nextAttemptAt,_id) | version·lease·proof·review 필드의 additive validator/subset |
| payment_account_lifecycles | ux_payment_lifecycle_user(environment,userId) unique | 금융 입력 writer 및 삭제 CAS 연계, 필요한 필드만 추가 검토 |
| payment_accounts / purchase_account_refs | 기존 account/ref unique 및 active partial index | stable ref 불변, 삭제 전 전체 ref 경합 검사 |
| payment_reconcile_cursors | ux_payment_reconcile_cursor(accountId,kind), ix_payment_reconcile_due(nextAttemptAt,_id) | activity/lease 보존·cleanup과 충돌 제어 |

새 인덱스 후보는 `ix_payment_cleanup_review(state,nextReviewAt,_id)` ascending, non-unique, partial/TTL 없음이다. 담당 검토 큐 실행계획을 확인한 후 필요할 때만 exact manifest에 추가한다. account/lifecycle/job의 business TTL은 만들지 않는다.

기존 WAITING job에는 lease/proof/review 필드가 없다. 읽기 호환 → 승인된 subset 설치/검증 → 새 worker OFF 배포 순서로 이관한다. 누락 proof는 UNKNOWN이며 legacy를 자동 READY로 backfill하지 않는다. BSON long의 version은 overflow 시 실패, 날짜는 UTC, lease owner/token은 로그에 남기지 않는다. startup에서 기존 인덱스 drop/recreate 또는 임의 collMod하지 않는다. 롤백은 worker/purge OFF와 drain부터 수행하고 새 상태를 모르는 구 writer를 무조건 재가동하지 않는다.

### 5.8 flag·관측·실행 권한

제안 설정은 `billing.payment.cleanup.reconciliation-enabled`, `purge-enabled`로 분리하며 둘 다 기본 false다. 정확 이름은 PR0에서 기존 설정 충돌 확인 후 동결한다.

- 둘 다 OFF: 기존201/204 동작만, 신규 provider 호출/삭제 없음.
- reconciliation ON, purge OFF: 실증된 read adapter만 사용해 상태·검토 일정 갱신, 물리 삭제 없음. adapter 없으면 활성화를 거절하거나 UNKNOWN으로만 수렴하도록 명시하며 fake fallback 금지.
- purge ON: reconciliation·recovery fence·schema·실제 거래 writer/coverage/drain 준비가 필수. 정적 설정 오류는 기동 실패, 실행 중 epoch/증거 소실은 삭제 중단·경보.
- 판매/public flag를 끄는 것이 이미 받은 금융 사실 처리·보존 작업의 중단을 의미하지 않음. 긴급 purge OFF는 별도 동작.

작업마다 처리시간/상태별 건수·최초 미해결 age·review overdue·lease 회수·CAS 충돌·purge 실패·worker heartbeat를 집계한다. userId/ref/전화번호/토큰/provider 원문·고카디널리티 epoch/job ID는 로그와 metric tag에서 제외한다. 외부 watchdog과 담당 알림 실제 연결은206에서 검증한다. 수동 도구도 사유 기록만으로 거래 부재를 확정하거나 신규 지급/환불하지 않는다.

### 5.8.1 Slack 운영 알림 — 2026-10-13 사용자 승인

| 분류 | 사례 | 전달/대응 |
| --- | --- | --- |
| 일반 검토 | 특정 계정 거래 확인 불가·미처리 거래 대기·검토 기한 도래/초과 | 매일 1회 원인별 건수·경과·기한 요약, 기한 초과 별도 강조. 삭제 보류 자체를 중대한 장애로 취급하지 않음 |
| 시스템 장애 | 지속되는 provider 인증 실패·worker 중단·DB 장애로 다수 계정 확인 실패 | 감지 시 즉시 알림, 영향 범위와 복구 조치 확인 |
| 삭제 안전성 사고 | 보존 대상 오삭제 또는 삭제 방지 장치 미작동 | 즉시 알림과 삭제 기능 중단, 영향 조사·복구 절차 진행 |

기존 운영 Slack 채널 재사용을 우선하되 접근 범위가 넓으면 비공개 채널을 지정한다. Slack은 상태 저장소가 아니며 메시지 읽음/반응/확인 버튼으로 BLOCKED를 해제하거나 READY/삭제로 전환하지 않는다. Billing DB의 처리 상태·최초 미해결 시각·검토 기한을 기준으로 다음 요약을 만든다.

메시지는 비식별 집계·사유·경과일·기한을 기본으로 한다. 개별 참조가 필요하면 사용자 식별자를 포함하지 않는 내부 검토번호만 사용하고, 상세 정보는 별도 접근 통제된 운영 수단에서 확인한다. userId/purchaseAccountRefId/전화번호/결제 원문/credential은 보내지 않는다. 검토번호 또한 불필요하게 장기 보관하거나 metric tag로 사용하지 않는다.

일시 단건 오류와 지속/광범위 장애를 구분하는 임계치·관측 window·동일 장애 중복 알림 억제·복구 알림·Slack 전송 실패 재시도는 PR0/206 기술 검증으로 정한다. Slack 실패가 금융 수신/복구 Transaction을 실패시키거나 삭제 안전 검사를 우회하게 하지 않는다. worker가 죽어도 감지할 외부 watchdog을 포함한다. 삭제 중단의 자동/운영 실행 방식과 담당 권한도 구현·운영 규격에서 닫는다.

7일 검토 기한과 일일 알림은 개인정보 보존 근거나 보존 종료일을 대신하지 않는다. 실제 채널·담당/대체 담당·예외 보존은 여전히 활성화 전 미결 사항이다. 이번 승인은 문서 정책 반영이며 Slack 메시지 발송·webhook 설정·운영 활성화를 실행하지 않는다.

### 5.9 구현 분할과 완료 구분

| 단계 | 산출물 | 완료 기준 |
| --- | --- | --- |
| PR0 기술 규격 | 포트/상태·BSON/null·index subset·writer 목록·삭제 manifest·rollback 규격 |010 연계 누락/운영값/예외 보존을 명시, 구현 승인 |
| PR1 기반 | cleanup 상태/lease·일정·evaluator·fake 테스트·기본OFF 설정 |15일 경계/일일·7일 기한/중복·crash 동작 검증 |
| PR2 삭제 코어 | PURGE fence·bounded Transaction·재생성 방지·감사 |replica-set에서 fake 금융 writer와 모든 commit 순서/unknown commit 검증, 실제 purge OFF |
| PR3 실제 연결(010 의존) | 실제 local/provider evidence adapter·금융 수신 fence·미매핑 격리 |실제 데이터 형태와 pagination/지연 coverage 검증, fake fallback 없음 |
| 운영 gate(206/013) |이관·token drain·복원·부하·권한·경보·예외 보존·활성화 기록 |별도 승인 후 단계적 ON |

PR1/2 완료는 **205 기반 로컬 구현 완료**다. PR3와 Jira 완료 기준의 실제 연결이 남아 있으면 205 전체 완료로 닫지 않는다. 반대로 미확정 provider 실증 때문에 가능한 기반 구현까지 중단할 필요는 없다. 이번 작성은 Jira 상태/설명 변경을 포함하지 않는다.

## 6. 부록 — 테스트·근거·완료 체크리스트

### 6.1 필수 테스트

| 구분 | 검증 사례 |
| --- | --- |
| 시간 |15일 직전/정각/나노초 올림, 늦은 수신, duplicate가 기산점·검토 기한 유지 |
| 상태 |account 없는 탈퇴, WAITING→VERIFYING→각 결과, 만료 lease 재획득, stale lease 쓰기 거절 |
| 거래 |구매/환불/기간권/ledger 존재 시 보존; pending sync/inbox/case는 삭제 금지 |
| provider |404/빈 목록/부분 페이지/429/401/403/timeout/다른 환경/미관측 Store 거래 모두 부재 확정 금지 |
| 증거 |전체 inactive ref 포함, ref/cursor/금융 revision 변경, proof 만료, adapter 미설치, coverage 불명 |
| 경합 |삭제 vs inbox 수신/구매반영/발급/탈퇴/reconcile, 양쪽 commit 순서, duplicate key/unknown commit |
| 복구 |동일 epoch 정상, BLOCK/ROTATE 경합, control 누락, 새 epoch에서 옛 proof·lease 거절 |
| 단순화 |과거 이력 불명만으로 전계정 영구 보류하지 않음; snapshot 성공만으로 READY 금지 |
| 재생성 |과거 token 만료/진행 요청 drain, 삭제 뒤 늦은 거래 격리, TTL 이후 탈퇴 replay, 재가입 별도 user |
| 원자성 |한도 초과 무삭제, 중간 실패 전체 rollback, 부분 부재를 성공으로 은폐하지 않음 |
| 보존 |금융5년/무료3년/inbox120일/backup35일 불변, cleanup 사용자 연결의 영구 잔존 없음 |
| 운영 |기본OFF, fake 운영 주입 거절, schema 불일치/구 writer 호환, 비식별 지표, scheduler 중단 감지 |

단위 테스트는 fake clock/adapter, DB 경합은 replica-set Testcontainers로 검증한다. 구현마다 `./gradlew clean test`, 필요 시 `bootJar`를 수행한다. 실제 Store/RC/AWS/Identity 호출은 로컬 테스트에 넣지 않는다. 복원 fixture 회귀와 실제 backup restore 검증을 구분한다.

### 6.2 정적 근거

- [PaymentWithdrawalApplier](../../src/main/java/web/tosunsaeng/billing/domain/payment/application/PaymentWithdrawalApplier.java): exact 탈퇴 사실, WITHDRAWN/ref 비활성,15일 후보 생성, 삭제 없음.
- [PaymentAccountService](../../src/main/java/web/tosunsaeng/billing/domain/payment/application/PaymentAccountService.java): lifecycle 쓰기, stable ref/cursor와 authorizationDeadline 검사.
- [PaymentRecoveryFence](../../src/main/java/web/tosunsaeng/billing/domain/payment/recovery/PaymentRecoveryFence.java): 현재3 writer/3 barrier 및 epoch 쓰기 CAS; PURGE는 신규 제안.
- [204 운영 도구](../runbooks/TMI-204-operator-and-bounded-verification.md): 격리·epoch·schema 이관 및 실제 운영 검증의 한계.
- [009 삭제 계약 §5.3~5.5](../contracts/PLAN-009-withdrawal-reconciliation-and-purge-contract.md): 거래 부재 증명/삭제 경합/보존 원칙.
- [PLAN-010](PLAN-010-purchase-ingestion-and-timeline.md), [PLAN-013](PLAN-013-payment-rollout-and-operations.md): 실제 provider 연결과 활성화 선행조건.

TMI-205 Jira는 2026-10-13 읽기 조회했다. 원문에 남은 과거 coverage 표현은204 최신 단순화와 대조했으며 이번에는 Jira를 수정하지 않았다. 실환경·provider 기능 지원·예외 보존 적법성은 이 정적 조사로 확인되지 않는다.
