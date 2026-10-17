# TMI-205 cleanup-r1 — 미구매 탈퇴 계정 정리 기술 규격

> 현재 기준: 2026-10-17 사용자 승인 [관측 기반 삭제 R1](TMI-205-OBSERVED-ABSENCE-R1.md). 과거 미관측 Store 거래 부재 증명/무조건 purge 거절 설명은 이 범위에서 대체한다. 실제 활성화는 별도다.

> 2026-10-13 단순화 목표 승인: adapterVersion/coverageVersion/drainPolicyVersion 세 항목은 cleanupPolicyVersion 하나로 통합한다. 일반 앱 배포가 아니라 삭제 증거 판단 규칙이 바뀔 때 증가시킨다. 외부 조회 완전성·진행 중 입력 확인·최종 CAS/lease/epoch·proof 유효기간은 유지한다. 아래 세 필드와 현재 r1/r2 schema/코드는 변경 전 구현 기록이다. 새 validator revision·호환 reader·명시 이관 후 신규 writer를 적용하며 기존 세 버전 증거를 임의로 새 버전에 대응시키지 않고 재확인한다. 실행 중인 lease와 공존하는 증거 변경은 금지하며 구 writer 격리/회수 후 이관한다. 이번은 문서 승인 반영이며 schema 변경·실삭제 활성화가 아니다.

> 2026-10-13 PR2 구현 보충: READY 증거보존 삭제claim/PURGE fence/최종bounded삭제·직후실행/tick회수 코어를 추가했다. 실제 구성은 여전히 UNKNOWN-only/purge ON 거절이다. r1 manifest를 보존하고 r2 exact validator로 명시 이관한다. 상세 구현·검증 범위는 [PR2 안내](../runbooks/TMI-205-pr2-purge-core.md). 아래 PR0/PR1 시점의 미구현 표현은 역사적 범위이며 이 보충과 구분한다.

> 2026-10-13 PR1 구현 보충: TMI-209에서 job/lease/evaluator/CLEANUP fence/schema 검증·명시 installer·fake 알림 interface를 구현했다. 아래 PURGE/실제 provider/운영 alert state·전송은 후속 규격이다. bounded5초step에는 자동10초renew loop가 없고 갱신API만 검증했다. alert 전달은 runtime OFF이며 메모리fake집계는 durable Slack전송 완료가 아니다. 상세 [PR1 안내](../runbooks/TMI-209-cleanup-foundation.md).

- 작성일: 2026-10-13 / 브랜치: develop / 관련 Jira: TMI-205
- 상태: PR0 검토용 기술 규격. 사용자 요청으로 구체화했으며 구현·schema 설치·운영 활성화 완료가 아니다. 새 기술 초기값은 권장 후보다.

> 2026-10-13 PR2 방향 승인: READY의 증거를 보존하는 별도 삭제 lease, 확인 직후 삭제 시도, 지연·변경 시 재검증을 사용자 승인으로 반영했다. 아래 §5.3.1이 해당 동작의 기준이다. PR1 코드/manifest는 아직 변경 전이며 실제 purge ON 승인이나 PR2 구현 완료가 아니다.
- 상위 기준: [205 계획](../plans/TMI-205-withdrawal-reconciliation-and-purge.md), [009 삭제 계약](PLAN-009-withdrawal-reconciliation-and-purge-contract.md), [공동 lifecycle 계약](IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md), [204 계획](../plans/TMI-204-withdrawal-recovery.md).

## 1. 5줄 결론

1. 현재 cleanup job을 확장하고 계정 변경 감지는 기존 lifecycle.version을 공통 기준으로 사용한다. 별도 ref/금융 revision은 추가하지 않는다.
2. 증거가 없는 adapter는 UNKNOWN을 반환한다. 실제 provider coverage가 없으면 READY/실제 삭제에 도달할 수 없다.
3. cleanup lease·job version·복구 epoch를 검사하고, 최종 삭제는 공통 lifecycle/control 쓰기와 한 Transaction으로 직렬화한다.
4. 일반 미해결은 Slack 일일 요약, 다수 영향 장애는 즉시, 삭제 안전성 사고는 즉시 알림 및 purge 중단이다. Slack은 승인 수단이 아니다.
5. PR1/2는 fake 기반 코어 검증, 실제 금융 입력/조회 연결은 PLAN-010, 운영 채널·보존 예외·drain·복원 검증은206/013 gate다.

## 2. 사용자가 반드시 읽어야 하는 내용

새 public/internal endpoint나 Identity wire field는 추가하지 않는다. 미구매 연결정보 정리만 다루며 무료권·구매 지급·환불 기능을 구현하지 않는다. 구매 발견은 FINANCIAL_RETAINED로 분류하고, 금융5년 보존 절차 자체는 별도다.

구체화한 규격도 provider가 제공하지 않는 증거를 만들어낼 수 없다. RC 빈 목록/404,15일 경과, 담당자 확인은 단독으로 미구매 증명이 되지 않는다. 실제 부재 증명 미완료 상태에서 테스트 adapter를 운영에 연결하지 않는다.

205의 공통 fence 변경은 기존 account/탈퇴/204 동작을 유지하면서 추가한다. 현재 코드에는 PURGE/CLEANUP writer가 없으므로 아래 규격은 구현 사실과 구분한다. 외부 인증과 LC 동시 작업을 변경하지 않는다.

## 3. 결정 사항과 남은 값

### 3.1 유지하는 사용자 승인

탈퇴15일, 매시간 대상 점검, 미해결 일1회 재확인, 최초 미해결7일 이내 담당 검토, 기본OFF, Slack 알림 구분 및 비식별 메시지, 결제/무료 보존과 재가입 자동 이전 금지를 유지한다.

### 3.2 이번 규격의 권장 기술값

| 항목 | r1 후보 |
| --- | --- |
| 날짜 | DB는 UTC BSON Date(ms); 기존 withdrawnAtCanonical 보존, eligibleAt은15일 더한 뒤 ms 올림 |
| 후보 조회 | 각 round 최대100 job, (nextAttemptAt,_id) 순서, offset 없이 keyset |
| 실행 | 전용 단일 worker thread, tick 최대30초/100 job 중 먼저 도달, 진행 중 DB 호출은 기존2초 budget 내 종료 |
| lease |30초,10초마다 갱신, 매 처리/commit 시 유효성 확인; 임대 만료는 now>=leaseUntil |
| 외부 확인 | adapter 호출 한 step 최대5초, 페이지/전체 조회 완료 여부 별도. 부분 결과로 proof 생성 금지 |
| proof |checkedAt부터 최대1시간, provider 보장 종료시각이 더 이르면 그 시각. expiry 경계는 now<validUntil |
| 삭제 상한 |한 계정당 삭제할 document 최대100개, 초과면 무삭제·BLOCKED |
| 미해결 재확인 |판정 시각부터24시간 뒤, firstUnresolvedAt은 최초값 고정 |
| Slack 요약 |매일09:00 Asia/Seoul; DB 시간은 UTC |
| 즉시 장애 |인증/계약 설정 오류 최초 감지, worker heartbeat10분 부재, DB/외부 전송 오류5분 연속(실패3회 이상) |
| 알림 중복 |동일 환경/종류/사유 장애 OPEN 1회, 지속 시1시간 간격,복구1회; 삭제 안전성 사고 최초 즉시 |

30초는 네트워크/DB 호출을 강제로 끊어 Transaction 안전성을 훼손하는 hard deadline이 아니다. 다음 작업 dispatch를 멈추는 budget이며 외부 호출5초/DB2초를 별도로 제한한다. 지연과 부하는206에서 측정한다.

### 3.3 운영 전 미결

실제 Slack 채널·담당/대체 담당, 미해결 연결정보의 목적·필드·보존 종료일, provider coverage 근거, 토큰/구 요청 drain 및 cutover 증거, 실제 권한/복원/자동 삭제 ON 승인. 이 값은 임의 기본값으로 대체하지 않는다. 미해결 보존을 자동5년으로 정하지 않는다.

## 4. 주요 위험·미확인

- 기존 payment foundation은 index initializer이며 collection validator 전체를 관리하지 않는다. 새로운 subset 설치는 명시적 maintenance 절차로 한다.
- 현재 Purchase/inbox/case 실제 writer 연결은205 기반 작업에 존재하지 않는다. 같은 fence 사용이 구현·검증되기 전 purge 활성화 불가다.
- 모든 job 처리에서 전역 control을 touch하면 복구와 경합할 수 있다. 정확성 우선으로 r1에서 유지하되 부하 검증 없이 락 단위를 임의 축소하지 않는다.
- 계정 부재·전체 document 부재는 과거 구매 부재나 직전 삭제 성공을 자동 증명하지 않는다. unknown commit의 정확 per-user 감사는 영구 receipt 없이 보장하지 않는다.
- provider pagination이 step budget 안에 끝나지 않으면 UNKNOWN이다. PR3에서 재개 가능한 조회 모델이 필요하면 별도 bounded cursor 계약으로 닫고 단순 재시작 때문에 영원히 끝나지 않는 것을 성공으로 처리하지 않는다.

## 5. 구현 기술 규격

### 5.1 내부 타입·포트

다음은 Java 내부 규격이며 HTTP DTO가 아니다. 객체 toString은 식별자·증거를 출력하지 않는다.

| 이름 | 입력/출력 및 의미 |
| --- | --- |
| CleanupRepository |job lease CAS·결과 저장·최종 삭제. PaymentStore의 동일 ClientSession 사용 |
| LocalPaymentEvidenceReader |session + SubjectSnapshot → FOUND/NONE/PENDING/UNKNOWN. NONE은 구현된 모든 필수 저장소 조회가 끝난 경우만 |
| ProviderPurchaseEvidenceReader |SubjectSnapshot + deadline → FOUND/ABSENT_OBSERVED/UNKNOWN + completion/coverage 메타데이터 |
| CleanupCoveragePolicy |provider 관측과 검증된 adapter/환경별 coverage 설정 → CONFIRMED 또는 UNKNOWN |
| CleanupEligibilityEvaluator |시각/탈퇴/drain/local/provider 증거 → 보존/보류/READY, DB/HTTP 부수효과 없음 |
| CleanupAlertSink |비식별 AlertSummary → ACCEPTED/RETRYABLE/CONFIGURATION_ERROR. 기본 disabled, fake는 test source 전용 |

SubjectSnapshot은 environment,userId,lifecycleId/lifecycleVersion,accountId/accountVersion,전체 ref 목록,ACCOUNT_LOOKUP cursor의 lastActivityAt,epoch를 담는다. snapshot은 작업 메모리에만 두며 provider 원문을 저장하지 않는다. 외부 호출 전에 복사하고 Transaction 밖에서 사용한다.

NONE/ABSENT_OBSERVED는 NO_PURCHASE_CONFIRMED와 다르다. local NONE + pending0 + 전체 ref/환경/앱/스토어 coverage + drain/시각/버전 일치에서만 최종 NO_PURCHASE_CONFIRMED다. 알 수 없는 추가 source가 있으면 UNKNOWN이다. FOUND는 신뢰된 거래만 뜻하며 client hint만 있으면 PENDING이다.

account 없는 후보도 known lifecycle/fact와 발급 이력 확인 범위가 있어야 한다. account null 하나로 provider 확인을 건너뛰어 READY로 만들지 않는다.

### 5.2 job schema — payment_account_cleanup_jobs

기존 root 필드를 유지하며 r1 관리 필드는 선택형 `cleanup` 객체에 모은다. 기존 applier가 생성한 WAITING row에는 cleanup이 없을 수 있다. worker가 최초 lease 획득 Transaction에서 초기화하며 부재를 READY로 해석하지 않는다.

| 필드 | BSON/필수·부재 규칙 |
| --- | --- |
| _id/environment/userId/lifecycleId |기존 필수 문자열 유지. id는 각 기존 canonical 규격 |
| accountId |기존 필수 string 또는 null. null은 계정 없는 탈퇴 |
| eligibleAt/createdAt |기존 필수 Date, 중복/재시도로 덮어쓰지 않음 |
| state |필수 WAITING/VERIFYING/BLOCKED/FINANCIAL_RETAINED/READY. PURGED row는 저장하지 않음 |
| version |필수 BSON long,1..Long.MAX_VALUE, 변경마다+1; MAX이면 변경 거절/경보 |
| nextAttemptAt |WAITING/VERIFYING/BLOCKED/READY에서 Date 필수, FINANCIAL_RETAINED는 unset |
| cleanup.schemaVersion |cleanup 존재 시 필수 BSON long=1 |
| cleanup.epoch |cleanup 존재 시 필수 canonical UUIDv4 string |
| cleanup.lease |VERIFYING은 필수, PR2 READY는 삭제 lease 보유 중 선택형, 나머지 상태에서는 unset(null 금지) |
| cleanup.lease.token/until |필수 UUIDv4 string/Date. hostname·사용자정보 저장 없음 |
| cleanup.reason |마지막 판정이 있을 때만 §5.3 enum string |
| cleanup.lastCheckedAt |마지막 판정 완료 시 Date, API 성공시각을 꾸며 넣지 않음 |
| cleanup.firstUnresolvedAt |최초 BLOCKED 시 Date 고정, 재시도/사유 변경으로 갱신 금지 |
| cleanup.reviewDueAt |firstUnresolvedAt+7일, 함께 존재하며 고정 |
| cleanup.lastReviewedAt |운영 검토 기록이 있을 때 Date. reviewDueAt을 자동 연장하지 않음 |
| cleanup.proof |READY에서만 필수 object, 다른 상태는 unset |

proof 필드는 모두 필수: `checkedAt,validUntil` Date; `epoch` UUIDv4; `lifecycleVersion` long>0; `accountVersion` long>0 또는 accountId null인 경우에만 null; `cursorActivityAt` Date 또는 계정 없는 경우만 null; `adapterVersion,coverageVersion,drainPolicyVersion` 1~64 ASCII `[A-Za-z0-9._-]`; `result` 고정 `NO_PURCHASE_CONFIRMED`.

proof에 ref 집합/사용자·거래 ID·원문은 중복 저장하지 않는다. ref/금융 변경은 lifecycle.version으로 검출하며 cursor 활동은 추가 exact compare한다. 새로운 revision을 별도로 만들지 않는다. checkedAt≤now<validUntil≤checkedAt+1시간, proof epoch==lease epoch==control epoch를 확인한다. coverage/drain policy version이 현재 설정과 다르면 재확인한다.

root는 기존 필드/다른 writer의 승인된 확장과 호환되는 validator, cleanup과 그 하위 객체는 additionalProperties=false로 제한한다. 상태/필드 관계는 application과 DB validator 양쪽에서 검사한다. proof/lease 누락·잘못된 숫자 타입·overflow는 보류/오류이며 기본0으로 보정하지 않는다.

### 5.3 상태 전이·사유

| 시작 | 조건 | 결과 |
| --- | --- | --- |
| WAITING/BLOCKED |due 도래, lease 획득 |VERIFYING, proof unset |
| VERIFYING |lease 만료 |새 token/version으로 VERIFYING 재획득, 이전 결과 거절 |
| VERIFYING |검증된 구매/보존 자료 |FINANCIAL_RETAINED, lease/proof/nextAttemptAt unset |
| VERIFYING |필수 증거 부족/조회 실패/미처리 입력 |BLOCKED, nextAttemptAt=now+24시간, 최초 검토 기한 설정 |
| VERIFYING |모든 부재 증거 충족 |READY, proof 저장, 확인 lease 해제. 삭제 capability 활성 시 nextAttemptAt=now, OFF는 now+1시간 |
| READY |유효 proof·삭제 가능·다른 유효 lease 없음 |READY/proof 유지, 별도 삭제 lease를 CAS 획득 |
| READY |proof 만료/epoch·version 변경 |다시 VERIFYING으로 전체 확인; 이전 proof unset |
| READY |purge OFF |삭제 없음. 시간당 한 번 이하로 재점검하며 due busy loop 금지 |
| READY |purge ON 및 최종 검증 성공 |동일 Tx에서 대상 삭제, 반환값 PURGED |

사유 enum: `PURCHASE_FOUND`, `PENDING_EVIDENCE`, `LOCAL_EVIDENCE_UNAVAILABLE`, `PROVIDER_UNAVAILABLE`, `PROVIDER_AUTH_ERROR`, `PROVIDER_CONTRACT_ERROR`, `PROVIDER_INCOMPLETE`, `COVERAGE_UNPROVEN`, `DRAIN_UNPROVEN`, `VERSION_CHANGED`, `PROOF_EXPIRED`, `RECOVERY_BLOCKED`, `DOCUMENT_LIMIT`, `DATA_INCONSISTENT`, `SCHEMA_MISMATCH`.

SCHEMA_MISMATCH/전역control 소실은 job을 억지로 갱신하지 않고 worker 중단·시스템 경보로 보고한다. lease나 epoch를 잃은 worker 역시 BLOCKED 저장조차 하지 못하며 유효 worker가 재판정한다. 주기적 재조회와 시스템 인증 복구는 분리한다. 반복 실패가 firstUnresolvedAt을 초기화하지 않는다.

### 5.3.1 PR2 승인 — 증거 보존 삭제 lease와 직후 실행

확인 claim과 삭제 claim을 별도 경로로 둔다. 기존 확인 claim은 VERIFYING 전환과 proof 제거를 유지한다. 삭제 claim은 READY와 기존 proof 전체(checkedAt/validUntil/정책 버전 포함)를 유지한 채 job version을 증가시키고30초 token/until lease를 획득한다. lease를 얻거나 갱신한다고 증거 수명을 연장하지 않는다. 별도 상태 enum이나 승인 시스템은 추가하지 않는다.

삭제 claim부터 PURGE fence의 OPEN-only·같은 epoch 규칙을 적용한다. 최종 Transaction에서도 현재 lease token/version/epoch, now<leaseUntil, checkedAt≤now<validUntil, lifecycle/account/cursor/pending·local 증거·현재 coverage/drain policy를 검사한다. 다른 삭제/재확인 worker는 같은 job의 유효 lease를 빼앗지 못한다. 잠금은 결제 writer를 차단하는 장치가 아니므로 공통 lifecycle/control 쓰기 CAS를 생략하지 않는다.

READY 저장 commit 이후 같은 bounded 실행에서 삭제를 시도한다. 다음 매시간 discovery를 기다리지 않는다. 예산 소진/crash 등으로 직접 실행하지 못한 READY는 durable nextAttemptAt을 통해10초 tick의 bounded 후속 처리 대상으로 둔다. 매 tick 최대100건/30초 예산과 keyset 순회를 유지하고, 실패 한 건이 같은 tick에서 반복 선택되는 busy loop를 금지한다. 이는 즉시 완료 SLA가 아니다.

삭제 lease가 만료되면 새 token/version으로 회수할 수 있으나 기존 증거와 최종 조건을 다시 검사한다. proof 만료·policy/version/epoch 변경이면 삭제하지 않고 확인 경로로 넘겨 이전 proof를 폐기한다. 이전 worker는 stale token/version/epoch로 결과를 쓰거나 삭제하지 못한다. 새 epoch에서는 현재 control 허용 범위에서 새 확인을 수행하며 이전 증거를 재사용하지 않는다. 금융 발견은 FINANCIAL_RETAINED, 증거 부족은 기존 BLOCKED/24시간 재확인·최초7일 검토 규칙을 따른다.

purge OFF는 READY/proof를 저장하되 삭제 lease를 획득하지 않고 nextAttemptAt=now+1시간으로 유지한다. ON으로 전환해도 만료 증거를 살려 쓰지 않는다. PR2의 삭제 capability는 test-only wiring으로 검증하며 production의 purge ON 기동 거절은 PR3/운영 gate 전까지 유지한다.

### 5.4 epoch·lease·CAS

`PaymentRecoveryFence.Writer`에 CLEANUP/PURGE 추가를 제안한다. 두 writer 모두 fence-required=false를 허용하지 않으며 기존 ACCOUNT/PUSH/RECOVERY 권한은 그대로다.

| barrier | CLEANUP(lease/판정/일정) | PURGE |
| --- | --- | --- |
| OPEN |허용 |조건부 허용 |
| PURCHASE_BLOCKED |허용; READY/삭제는 금지, 복원 검증 미완료는 보류 |거절 |
| ALL_BLOCKED/없는 control |거절 |거절 |

job을 변경하는 Transaction마다 control acquire 후 job을 exact(_id,version,state,lease token,epoch)로 갱신한다. leaseUntil>now를 검사하며 갱신 시 job version도 증가한다. provider 결과는 갱신된 lease version을 가진 동일 실행만 저장한다. epoch 변경 시 old lease/proof는 무효이며 새 control에서 새 token을 획득해 확인부터 시작한다.

결과 저장 전에는 lifecycle.version/account.version/cursor activity를 다시 읽어 snapshot과 비교한다. 잠재 write-skew는 최종 purge에서 lifecycle 쓰기 CAS로 막는다. 조회 성공 후 READY 저장 자체를 삭제 완료로 간주하지 않는다.

### 5.5 금융 writer 계약과 삭제 Transaction

PR3 필수 writer 목록: account 발급/참조 변경, withdrawal 적용, mapped provider inbox 최소 수신, sync durable 접수, Purchase/ledger/기간권 반영, reconcile 결과·pending 해소. 금융/참조 관련 변경은 동일 lifecycle.version을 해당 자료와 한 Tx에서 증가시킨다. 단순 읽기 체크는 대체가 아니다. 기존 withdrawal NOOP는 상태 변화가 없으므로 version 증가를 강제하지 않는다.

pendingEvidence는 아직 해결되지 않은 durable 입력 수다. 수신 멱등 key가 처음 등록될 때만+1, terminal 해소가 처음 commit될 때만-1, 음수·누락은 오류다. 의미가 다른 job을 무작정 합산하지 않고 PR3의 effect key로 증감 멱등성을 검증한다. 미매핑 거래는 별도 durable 격리하며 전체 영향 불명 상태를 후보의 안전한0으로 숨기지 않는다. 상세 금융 schema는010의 계약을 따른다.

삭제 순서는 다음과 같다.

1. §5.3.1에 따라 READY/proof를 보존하는 별도 삭제 lease 획득, dispatch epoch 고정. 기존 확인 claim으로 증거를 지우지 않는다.
2. PaymentStore.transaction에서 PURGE control 쓰기 CAS, job lease/version/시각 재검사.
3. lifecycle WITHDRAWN/version/pending0·account version·inactive refs·cursor activity/다른 lease 없음·proof·token drain policy를 검사.
4. 같은 session으로 LocalPaymentEvidenceReader 재확인. 미설치/UNKNOWN이면 무삭제.
5. exact lifecycle version 쓰기 CAS. 삭제 대상 목록을 limit101로 읽어 합계100 이내 확인. 모든 참조가 해당 environment/account/lifecycle에 귀속됐는지 확인.
6. account의 전체 refs, ACCOUNT_LOOKUP cursor, account, cleanup job, 보존 필요 없는 lifecycle을 exact key로 삭제하고 예상 deletedCount 검사. 하나라도 다르면 전체 rollback.
7. 동일 Tx 성공 commit 확인 뒤 PURGED 반환. 계정 없는 후보는 검증된 lifecycle/job만 삭제 가능.

scope 밖 cursor/case·금융 자료가 하나라도 있으면 임의로 넓은 deleteMany를 하지 않고 보류한다. 검토 case를 자동 삭제하는 추가 collection은 r1에서 만들지 않는다. 외부 호출/Slack 전송은 Tx 밖이다. 현재 PaymentStore의 snapshot/majority+j 및 동일 session unknown-commit 재시도 규칙을 사용한다.

unknown commit budget 소진은 성공/실패로 단정하지 않는다. 다음 조회에서 동일 job이 있으면 정상 재검증, 전체 대상 부재면 ALREADY_ABSENT(재생성 안 함), 부분 잔존이면 DATA_INCONSISTENT 경보다. ALREADY_ABSENT는 해당 시도의 PURGED 성공 증명이 아니며 정확 삭제 집계에 포함하지 않는다. 무한 per-user 영구 receipt는 추가하지 않는다.

purge 먼저 commit 후 늦은 금융 입력은010의 durable unmapped 처리로 남겨야 한다. account/ref 자동 upsert와 재가입 지급 금지. 모든 입력 writer가 이 규격을 따르기 전 실제 purge flag를 켜지 않는다.

### 5.6 토큰·진행 요청 gate

새 발급30분/skew60초는 승인된 상한이지만 과거 발급값·cutover·서버시계 오차·인증 이후 대기 요청의 실제 최대 수명도 검증한다. `DrainEvidencePolicy`는 검증된 환경 cutover와 상한으로 해당 withdrawnAt에 대해 안전 시각을 계산하며, 알 수 없으면 DRAIN_UNPROVEN이다. 임의 Boolean `verified=true` 하나로 gate를 대체하지 않는다.

현재 PaymentAccountService의 transaction 시작/종료 authorizationDeadline 검사를 유지하고 retry/commit 지연과 lifecycle 제거 경합을 테스트한다. 새 수명 설정으로 과거 JWT가 소급 폐기됐다고 가정하지 않는다. 구 토큰 재생성 방지와 Store의 늦은 결제 부재는 별개 검증이다.

### 5.7 schema·인덱스·이관

새 manifest 이름 후보는 `payment-cleanup-schema-r1.json`. core schema v4나 paid schema 전체 완료 version을 올리지 않고 독립 subset으로 검증한다. 기존 index 이름/key order/unique/partial/TTL을 변경하지 않는다.

PR2에서는 현재 PR1 manifest의 'VERIFYING에서만 lease 허용' 제약을 'VERIFYING 필수/READY 선택/나머지 금지'로 명시 개정한다. proof는 READY에서만 필수라는 조건을 유지한다. 설치된 PR1 validator와 새 validator를 구분하는 exact revision/이관 경로를 구현 전에 고정하고 알려진 이전 규격에서만 maintenance 이관한다. worker OFF·drain·ALL_BLOCKED에서 기존 row 검사 후 변경하며 startup 자동 collMod는 금지한다. 구 PR1 worker는 READY의 삭제 lease 의미를 모르므로 purge 작업 drain/lease 정리·schema 호환 확인 없이 재투입하지 않는다. 이번 문서 반영은 실제 manifest/DB 변경이 아니다.

| collection/index | exact key 순서 | 옵션 |
| --- | --- | --- |
| payment_account_cleanup_jobs / ux_payment_cleanup_subject |environment:1,userId:1 |기존 unique=true,partial/TTL 없음 |
| 동일 / ix_payment_cleanup_due |state:1,nextAttemptAt:1,_id:1 |기존 unique=false,partial/TTL 없음 |
| 동일 / ix_payment_cleanup_review |state:1,cleanup.reviewDueAt:1,_id:1 |신규 unique=false,partial/TTL 없음 |

이전 계획의 nextReviewAt 후보 대신 고정 reviewDueAt과 lastReviewedAt을 사용한다. root에 중복 일정을 만들지 않는다. exact index 비교는 sparse/hidden=false, simple collation,필드 순서까지 포함한다. READY/미해결 및 금융 보존 row에는 business TTL 없음.

배포 순서: legacy 필드 형태 사전 검사 → worker/purge OFF·필요 writer drain → 명시 installer로 기존 validator 확인 및 additive r1 설치 → 기존 applier의 WAITING insert 호환 테스트 → 새 reader/writer OFF 배포. 기존 cleanup 없는 WAITING은 허용하되 다른 legacy 상태 이상을 자동 복구하지 않는다. 알려지지 않은 validator/index drift는 실패, startup drop/recreate/collMod 금지. 초기izer는 runtime ON일 때 검증만 하고 DDL은 운영 승인 절차로 분리한다.

### 5.8 worker·flag와 로컬 완료 범위

| 설정 후보 | 기본값/조건 |
| --- | --- |
| billing.payment.cleanup.reconciliation-enabled |false. ON은 schema/fence/포트 주입 검증, 미설치 evidence는 명시 UNKNOWN |
| billing.payment.cleanup.purge-enabled |false. ON은 reconciliation/fence·실local/provider/drain policy가 구성되어야 하며 fake 금지 |
| billing.payment.cleanup.alerts-enabled |false. ON 전 채널/credential·허용 전송 대상 확인, 로그 노출 금지 |

cleanup scheduler는 전용 single-thread, 후보 discovery는 매시간, 이미 획득한 bounded 작업의 처리/lease 회수는10초 tick으로 한다. BLOCKED는24시간 notBefore를 지키며 새 evidence 알림을 핑계로 무제한 재조회하지 않는다. 일일 요약은 별도 스케줄이며 장기 provider 조회로 전체 서비스 scheduler를 점유하지 않는다.

PR1/2에서는 운영용 실제 삭제 capability를 제공하지 않는 기본 포트 구성을 사용한다. 명시 purge ON 시 가짜 evidence로 실행하지 않고 기동을 거절한다. replica-set 테스트는 test-only wiring으로 삭제 로직을 검증한다. PR3가 검증된 provider coverage를 제공하지 못하면 이후에도 실제 ON 불가다.

### 5.9 Slack 기술 규격

처리 상태는 job DB, 알림은 비식별 집계다. 개별 jobId 검토번호는 초기 Slack payload에 포함하지 않는 최소안을 채택한다. 별도 운영 접근 도구가 준비되기 전 Slack에서 개인정보 상세조회/강제삭제 기능을 만들지 않는다.

payload: `environment`, `kind`(DAILY_SUMMARY/INCIDENT/RECOVERY), `reason`, `asOf`, `count`, `oldestAgeHours`, `overdueCount`, `runbookKey`. user/account/ref·원문·임의 exception message·외부 URL은 금지한다. 일일 count는 DB의 현재값이며 정확 처리 횟수 원장이 아니다.

중복 억제/재시작을 위해 비식별 `payment_cleanup_alert_states`를 제안한다. 필드: `_id`(고정 알림 key),environment,kind,reason,state(OPEN/CLOSED),version(long>0),firstObservedAt,lastObservedAt,lastSentAt?(Date),nextAttemptAt?(Date),lastDailyDate?(YYYY-MM-DD KST),lease?(token/until). 고정 enum 조합만 허용해 계정 수만큼 증가하지 않는다. 개별 payload 원문을 저장하지 않고 전송 때 집계한다. 물음표 필드는 미존재 시 unset,null 금지.

index: `ux_payment_cleanup_alert_key(environment,kind,reason)` unique=true; `ix_payment_cleanup_alert_due(nextAttemptAt,_id)` unique=false. 두 index는partial/TTL 없음,simple collation. 현재 알림 상태만 저장하고 영구 사용자별 감사로 쓰지 않는다. 전체 enum/validator는 PR1 manifest에서 검사한다.

Slack 전송 timeout은3초/connect2초,redirect 금지,목적지 allowlist·secret 환경참조. retry는1분→5분→15분→1시간 상한,429 Retry-After가 더 길면 존중하며 raw 응답을 기록하지 않는다. 성공은 전송 adapter가 검증한 응답일 때만 기록한다. 전송 성공 후 DB 실패는 중복 알림 가능성을 허용한다(at-least-once). pending incident 도중 복구되면 복구 메시지에 발생·복구를 합쳐 표시하고 해결된 장애를 현재 장애처럼 뒤늦게 보내지 않는다.

Slack 자체 인증 실패는 같은 고장난 Slack으로만 알리지 않고 외부 모니터링 상태에 반영한다. heartbeat 중단 감지는 독립 watchdog 책임이다. 운영 채널 설정은 나중에 하고 fake sink로 본 규격을 검증할 수 있다.

삭제 안전성 사고는 severity=CRITICAL, 가능한 경우 control의 PURCHASE_BLOCKED로 전환해 PURGE를 차단하고 즉시 알린다. 이 조치는 신규 구매 계정 발급에도 영향을 주므로 자동 전환은 별도 승인 전 구현하지 않는다. 초기 운영안은 purge flag OFF·worker 격리·writer drain을 운영자가 즉시 수행하고, DB가 살아 있으면 승인된 operator BLOCK 절차를 사용한다. 다중 인스턴스 동시 중단과 기동값 전파 시간을206에서 검증하기 전 판매/삭제 활성화 금지. 별도 자동 kill switch가 필요하면 기존 정책 영향과 함께 계약을 보완한다.

## 6. 부록 — 구현 전 수락/테스트 목록

### 6.1 파일 분담 후보

- `domain/payment/cleanup`: evaluator/evidence ports/repository/worker/schema/alert 집계 및 sink interface.
- 기존 `PaymentRecoveryFence`: CLEANUP/PURGE 권한 추가와 OFF 우회 거절 테스트.
- 기존 `PaymentWithdrawalApplier`, `PaymentAccountService`: writer/legacy 호환 회귀, 불필요 리팩터링 없이 유지.
- test source: fake local/provider/coverage/drain/Slack, 실제 replica-set 경합 테스트.
- resources: cleanup/alert subset manifest; 실제 credential/ID 없음. 신규 SDK 의존성은 추가 승인 전 도입하지 않음.

### 6.2 필수 검증

1.15일 nanosecond 올림, now==leaseUntil/validUntil 경계, Long.MAX_VALUE/잘못된 BSON 숫자 거절.
2. missing cleanup legacy WAITING,accountId null과불명account 구분,duplicate탈퇴가job/기한 미초기화.
3. lease 갱신·다중worker·crash·epoch변경·ALL_BLOCKED·fenceOFF·PURCHASE_BLOCKED 판정/삭제 차이.
4. local NONE/provider 빈응답/부분page와 CONFIRMED 분리,fake 운영 사용/coverage version 변경 거절.
5. 공통lifecycle 변경/ref변경/cursor activity/새inbox 수신의 proof무효화,모든commit순서 및 pending증감 멱등.
6.100/101document경계,삭제도중실패rollback,같은sessionunknowncommit재시도,ALREADY_ABSENT를정확성공으로계상안함.
7.늦은거래durable격리/자동upsert금지,token/drain/구요청 재생성 차단,financial/free불변.
8.KST09:00요약·재시작중복·사유변경중최초7일고정·Slack실패/429/응답유실·개인정보비노출·장애복구요약.
9.schema drift fail-fast/기존index옵션보존/rollback안전성,실envworker중단/알림실패는206 별도.
10.READY proof 보존 삭제claim·중복claim/확인claim 경합·30초 만료 회수·stale worker 거절. lease 갱신으로 proof 수명 연장 금지.
11.READY 직후 실행·commit 후 crash/예산 양보와 tick 재발견·정확 validUntil 경계 재검증·purge OFF 시간당 재확인·READY lease schema 이관/구 worker 차단.

코드 구현 후 `./gradlew clean test`와 필요 패키징 검증. 본 문서 작성은 정적 확인이며 이전931 테스트를205 구현 검증으로 사용하지 않는다.

### 6.3 정적 근거

- [PaymentStore](../../src/main/java/web/tosunsaeng/billing/domain/payment/repository/PaymentStore.java):2초budget/snapshot/majority+j/unknowncommit 동일session.
- [PaymentRecoveryFence](../../src/main/java/web/tosunsaeng/billing/domain/payment/recovery/PaymentRecoveryFence.java):기존3writer/control쓰기가드.
- [WithdrawalApplier](../../src/main/java/web/tosunsaeng/billing/domain/payment/application/PaymentWithdrawalApplier.java):기존job필드/15일/canonical시각.
- [AccountService](../../src/main/java/web/tosunsaeng/billing/domain/payment/application/PaymentAccountService.java):lifecycle버전/cursor활동/authorizationDeadline.
- [FoundationInitializer](../../src/main/java/web/tosunsaeng/billing/domain/payment/config/PaymentFoundationInitializer.java):현재index초기화범위와옵션검증.
- [PLAN-010](../plans/PLAN-010-purchase-ingestion-and-timeline.md):실거래writer/조회/미관측거래의존성.

후속: r1 검토 후 PR1/2 구현 승인,PR3 금융연결·provider/drain검증,운영예외/채널/담당/중단절차 수락. Jira 수정·실제 연동·자동 삭제 실행은 이번 범위가 아니다.
