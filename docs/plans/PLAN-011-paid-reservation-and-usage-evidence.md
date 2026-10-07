# PLAN-011: 유료 우선 시험 승인·공통 guard·구매별 이용 증거

- 작성일: 2026-10-06 / 상태: 승인 대기 / Jira 미생성
- 선행: [010](PLAN-010-purchase-ingestion-and-timeline.md), [ADR-004 §5.8/5.8.1/5.9](../adr/ADR-004-fixed-term-premium-payment-contract.md).

## 1. 5줄 결론

1. 신규 INITIAL은 ACTIVE paid 우선, 무료 Claim/Grant는 보존한다.
2. 기존 무료 replacement는 원 consumption을 유지하고 결제로 출처를 바꾸지 않는다.
3. 사용자 공통 guard로 paid/free 동시 시험 생성을 막고 기존 Trial guard도 유지한다.
4. source snapshot과 기존 Session 상태를 연결해 구매별 최소 이용 증거를 남긴다.
5. 상대 LC reader/GRADING gate 준비와 refund 통합 전 유료 입장은 OFF다.

## 2. 사용자가 반드시 읽어야 하는 내용

기존 reserve→LC Session commit→confirm 순서, 5분 RESERVED expiry, cancel/expiry가 CONFIRMED를 되돌리지 않는 계약을 유지한다. paid를 가짜 TrialClaim이나 unit grant로 구현하지 않는다. phone continuation은 무료 재가입 계약 그대로이며 paid owner 이전 수단으로 사용하지 않는다.

### 승인 판정

| 경우 | 처리 |
| --- | --- |
| 새 INITIAL+ACTIVE paid | 그 purchase/entitlement 고정, 무료 미변경 |
| 새 INITIAL+유효 paid 없음 | 기존 무료 정책 판정; 불명 provider 상태를 유효 paid로 추정하지 않음 |
| 명시적 무료 replacement+paid 있음 | 기존 무료 consumption/source 유지 |
| paid replacement | 기존 group/mock/source, 새 Session; source 유효성 검사 |
| 정상 만료 전 reserve | 기존 5분 안 동일 Session confirm 허용(D1) |
| 정상 만료 뒤 기존 confirmed Session | 기존 제출 기한 내 완료, 새 replacement는 금지 |
| refund 먼저 commit | confirm409 ENTITLEMENT_REVOKED, LC orphan Session 보상 |
| expired/refunded group+다른 권리 | 자동 source 변경 금지; 기존 guard 안전 종료 후 별도 INITIAL |

무료 API 수량과 paid 기간권을 한 숫자로 합치지 않는다. 기존 무료 이용권 reader를 유료 구매 부수효과로 변경하지 않는다.

## 3. 사용자가 결정해야 하는 사항

2026-10-06 F1/F3 A안 승인: paid 채점 시작은 전용 멱등 승인 API로 분리한다. 무료 v1 이벤트의 204 의미는 유지하며 reserve v2의 source 표시는 채점 승인 증거가 아니다. 공통 guard는 아래 전이 규칙을 따르고 최초 이관은 관련 writer를 짧게 제한하는 방식으로 준비한다. exact wire/schema·상대 서비스 합의 및 실제 제한 시간/실행 승인은 별도다. 이 승인을 전체 구현·배포 승인으로 확대하지 않는다.

### Paid 채점 승인 계약 방향

- 요청은 인증된 LC와 exact owner/group/Session/source에 결속하며 멱등 command로 처리한다. client가 주장한 source만 신뢰하지 않는다.
- 승인 증거와 GRADING 전이를 하나의 Transaction에 저장하고 환불과 공통 version CAS로 순서를 정한다. 환불 선행은 거절, 승인 선행은 그 exact Session의 완료만 허용한다.
- 응답 유실 재시도는 저장된 승인/거절 결과로 수렴한다. 단순 HTTP 성공·STALE·duplicate 이벤트 또는 현재 group 상태만으로 승인하지 않는다. 승인 근거는 command 7일 TTL에 의존하지 않는 최소 영속 증거로 보존한다.
- LC는 명시 승인 확인 후 동일 Session의 Job을 멱등 실행한다. Billing 장애/불명 결과는 대기·재시도이며 무료 이벤트 처리 경로는 유지한다.
- 구현 전 route/method/DTO/오류/멱등키·결과 재확인·IAM·보존 manifest를 ADR와 LC 인계에서 동결한다. 별도 요청/응답 API의 정확한 이름은 이번 방향 승인으로 발명하지 않는다.

### Guard 전이·최초 이관 방향

| 사건 | guard 처리 |
| --- | --- |
| INITIAL reserve | 해당 사용자 자리를 exact operation/group/source/version으로 원자적 확보 |
| INITIAL cancel/예약 expiry | 현재 guard가 그 미확정 예약의 것인 경우에만 해제 |
| confirm·OPEN·GRADING | 해당 group 소유 유지; 승인된 Session/version만 전진 |
| REPLACEMENT reserve | 기존 group 자리 안에서 새 예약 확보; 다른 group 자리로 전환 금지 |
| REPLACEMENT cancel/예약 expiry | 기존 group의 예약 전 유효 상태로 복귀; consumption 유지, 무조건 자리 해제 금지 |
| RETAKE_AVAILABLE | 유효 기존 group의 재응시 자리 유지 |
| COMPLETED | exact group/version 자리 해제; 과거 이벤트가 새 group 자리 해제 못 함 |
| 정상 기간 만료 | D1 현재 confirmed Session 및 만료 전 reserve의 유효 confirm은 보호; 재응시 불가 group은 안전 종료 후 해제 |
| 환불 | root deny 즉시 적용; 승인 선행 exact GRADING은 유지, 나머지는 안전 종료/CAS 해제 후 다른 권리 INITIAL 허용 |
| owner rebind | source/target guard와 기존 owner 조건 함께 검사, 충돌은 pending; paid ownership 자동 이전 없음 |

guard 해제는 사용자 키만으로 삭제하지 않는다. 종료 전·후 늦은 cancel/terminal/revoke와 새 INITIAL 경합을 검증한다. 최초 이관은 관련 reserve/confirm/cancel/expiry/event/owner writer를 일관되게 제한·drain하고 이벤트를 유실 없이 재시도시키는 절차를 사용한다. 진행 중 시험을 임의 취소하지 않는다. read-only inventory→새 writer 준비→관련 writer 제한/진행 Transaction drain→bounded backfill/CAS→coverage 검증→writer 재개 순서와 실패 시 복구를 runbook에 고정한다. 제한 시간이 길면 온라인 dual-write 이관 대안을 다시 승인받는다.

제품 정책 추가 결정 없음. 내부 source 읽기와 LC commit gate의 기술 wire는 개발 계약 승인 대상이다. 현재 reserve response는 source 정보가 없는 무료 DTO이므로 LC가 paid gate를 무엇으로 판단할지 구현 전에 동결해야 한다. 제안은 capability-gated v2 응답의 source discriminator이며, v1 caller에게 알 수 없는 필드를 무조건 추가하지 않는다. exact route/DTO/version/IAM을 ADR와 LC 인계 문서로 먼저 승인하고 미지원 caller의 paid 선택을 금지한다.

## 4. 주요 위험과 미확인 사항

기존 subject 단위 unique만으로 user의 paid/free 동시 접근을 막을 수 없다. guard는 기존 Trial guard를 제거하는 대신 추가하며 owner rebind가 target user guard까지 검사해야 한다. legacy OPEN/GRADING/RETAKE_AVAILABLE·active Reservation coverage가 부족하면 활성화하지 않는다. source unknown을 무조건 TRIAL로 해석하는 것은 legacy 판정이 입증된 document에 한정한다.

LC 학습 삭제는 별도 기능이다. delete된 시험을 무료 복원/유료 미사용으로 오인하지 않는다. 7일 command·120일 inbox가 유일한 이용 증거가 되어서는 안 된다. source migration 뒤 구버전 writer rollback은 금지한다.

## 5. 현재 작업과 직접 관련된 구현

### 모델·Transaction

- paid account는 stable paidSubjectRefId, Reservation/Group source=TRIAL|SUBSCRIPTION, purchaseId/entitlementId immutable. Session은 동일 source snapshot 또는 삭제 전에 ledger에 보존되는 영속 parent 연결. paid에는 trialClaim/무료 consumption이 없음.
- source 선택→exam_owner_guards(environment,userId) CAS→Reservation/Session/group/usage ledger를 동일 Transaction에 반영. cancel/expiry는 paid audit만 종료하며 무료 unit 복구 없음.
- confirm/reserve/grading 승인과 refund는 공통 entitlement/account/guard version write를 통해 경합하도록 한다. source를 읽기만 하면 snapshot write-skew가 가능하므로 구현 테스트로 양 commit 순서를 입증한다.
- USER_MERGED/PHONE_REJOIN은 기존 무료 범위만 이전하며 target의 paid active guard 충돌은 pending으로 수렴. source의 paid ownership을 함께 이동하지 않는다.
- group access 상태 ALLOWED/REVOKED는 학습 status와 분리하고 늦은 상태 이벤트로 접근을 복구하지 않는다(012).

### 이용 증거

ADR §5.8.1대로 문의자→검증된 purchase→Reservation/Group/Session을 조회한다. reserve/confirm/cancel/expiry ledger와 상태 transition의 최소 증거를 같은 Transaction에 기록한다. 완료 당시 requiredFeedbackQueryable/validScoreQueryable/summaryQueryable/evidenceVersion, terminal 상태·정규화 실패 사유·발생/관측 시각만 남기고 답안·음성·점수 상세·피드백 원문은 저장하지 않는다.

운영 조회는 구매 owner/환경을 검증하고 구매별 group 수·완료 group 수·Session 시도 상태를 분리한다. 무료·취소/만료 예약 제외, 실패→재응시 성공은 두 시도/한 완료로 설명하고 자동 금액 공제하지 않는다. 이벤트 불명/보존 purge 뒤 확인 불가는 UNKNOWN이지0회가 아니다. 원본 삭제 뒤 완료 증거는 당시 제공 사실이며 현재 열람 가능 증명이 아니다.

payment ledger 최소 이용 증거는 승인된 5년 정책, Session/Group 상세 연결은 별도 보존 manifest를 013에서 고정한다. 모든 학습 정보를 5년으로 늘리지 않는다. 운영 query service/감사 port까지만 구현하고 일반 공개 관리자 API·원문 dump를 만들지 않는다. 실제 실행 runner/권한은 013 승인 절차.

### 코드 변경 위치·순서

기존 domain/reservation의 ReserveService/ReservationLifecycleService/expiry, domain/attempt event consumer, ownerrebind service에 필요한 port를 추가하고 결제 source 선택은 domain/payment application으로 분리한다. 먼저 nullable reader→guard/source writer→backfill/dry run→새 reader 검증 순서. 기존 wire strict decoder 및 free Session epoch는 유지한다. `paid-admission-enabled=false`로 배포하고 LC capability/환불 gate가 없으면 기동 또는 활성화 실패.

## 6. 부록 — 필수 검증·완료

- paid-first 전후 Claim/Grant/alias/available/held/consumed 무료 ledger 불변.
- paid/free 동시 reserve, paid reserve와 phone/Guest owner rebind, unknown commit·cancel/expiry race.
- D1 경계: 만료 전 reserve/5분 confirm, 만료 뒤 신규/재응시 거절, 현재 Session 기존 기한 완료.
- 동일 user 여러 구매·무료/유료 source 분리, source immutability after reflow, duplicate completion·역순·실패 후 성공.
- 원본 삭제/command·inbox purge 후 최소 증거 조회, 다른 구매 접근 차단, raw privacy 비포함.
- LC는 paid Job을 Billing GRADING commit 전 실행하지 않음; 무료 Job 흐름은 기존 계약 유지. 이 조건은 실제 상대 테스트로 확인.
- `./gradlew clean test` 및 replica-set Transaction/guard 테스트, 기존 PLAN-001~007 회귀. 012 refund/LC consumer까지 통합 전 활성화 불가.
