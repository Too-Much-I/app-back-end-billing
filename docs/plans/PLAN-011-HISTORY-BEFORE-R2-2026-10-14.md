# PLAN-011 — R2 구현 계획 정리 전 이력

이 문서는 2026-10-14 정리 직전 계획서의 보존본이다. 아래의 승인 대기·과거 기술 제안은 현재 구현 기준이 아니다. 현재 기준은 [PLAN-011](PLAN-011-paid-reservation-and-usage-evidence.md)과 그 문서가 참조하는 R2 계약이다.

---

# PLAN-011: 유료 우선 시험 승인·공통 guard·구매별 이용 증거

> 2026-10-14 공동 기술 검토 기준 통합: [유료 시험 계약 R2](../contracts/LC-BILLING-PAID-EXAM-CONTRACT-R2.md)를 우선 참조한다. 최신 승인 정책·LC 최소wire 제안을 한 곳에 정리했으며 본문의 종전 기술 후보는 변경 이력이다. 새정본의 exact 공동수락/fixture/구현·배포는 미완료이고 phone/삭제/보존 gate는 별도 확인한다.

> 2026-10-14 문제선정 추가승인: [단순화 인계 §1/9](../contracts/LC-BILLING-PAID-EXAM-SIMPLIFICATION-HANDOFF-2026-10-14.md). 전체필수답안 durable접수 후 채점/요약대기 신규시험은 직전mock 제외, 다른적격후보0개 확인시에만 동일mock 허용. 부분접수는동일mock, 완료후는기존선정규칙. 조회장애를후보0으로오인하지않고 같은operation선정고정. 코드/LC/DB변경없음.

> 2026-10-14 [wire R1 draft-2 §9](../contracts/LC-BILLING-PAID-EXAM-WIRE-R1.md)에 LC 검토 반영: replay우선·삭제continuation/누락context·조합표·거절snapshot·기존409/503오류유지·digest상세·예외역순·추가fixture. 신규계약은여전히후보,OTHER_SOURCE_CHANGE 최초활성화제외제안·미확정보존/phone충돌 gate명시.

> 2026-10-13 [공동 기술 계약 R1 초안](../contracts/LC-BILLING-PAID-EXAM-WIRE-R1.md) 작성: 전환 요청/응답·문제 선택·오류/복구·역방향인증/보존·fixture 제작명세. 신규 wire/enum/오류는 LC 공동 수락 전 후보이며 구현/배포 미실행.

> 2026-10-13 LC 마무리 승인: [R2 §10](../contracts/LC-BILLING-PAID-EXAM-HANDOFF-R2.md)에 기존 operation을 LC 종료까지 미완료/복구 가능 상태로 유지하고, 새 Session 활성·이전 신규제출 차단·operation 성공을 함께 저장한 뒤 공개 성공 반환하도록 확정했다. 접수된 Job만 완료 허용, 기존시험 ID목록 방식 우선검토. TRIAL fallback/최초trial→paid 절대무중첩 문구 정리. exact wire/상태/schema·LC 구현·배포는 후속.

> 2026-10-13 legacy 보존 승인: [prod 점검·확정 정책](PLAN-011-SOURCE-PRODUCTION-INVENTORY-2026-10-13.md)에 따라 기존 LC 시험은 기존 규칙/이력을 유지하고 일괄 종료·무료source backfill하지 않는다. 전환 후 새 시험·새 재응시부터 Billing 및 source 필수 적용. legacy는 source 누락이 아닌 전환 전 대상 증거로 구분하고 소비/복원 증거로 임의 사용하지 않는다. Billing prod DB는 미구축으로 기존 데이터 이관이 불필요하다. 실제 전환 경계와 LC 구현은 후속이다.

> 2026-10-13 최신 단순화 승인: [무료권 정책 §1/3/4](../contracts/FREE-EXAM-PASS-AND-PAID-SWITCH-RESTORATION-2026-10-13.md) 우선. Billing은 paid confirm·복원 지급·원 그룹 재응시 차단을 같은 Transaction으로 확정하고 LC는 기존 Session을 순차 종료한다. 이미 접수된 무료 제출의 경합 완료와 복원권 동시 보유는 허용한다. 최초 trial consumption ID당 복원 1회, 복원권/초대권 재복원 없음. 현재 무기한·지급순만 구현하고 만료 기능은 후속. MEMBER 전용 보상권의 Guest merge 확장은 제외하며 기존 trial merge는 유지한다. 종전 무중첩 보장·일반 root 계보·현재 만료순 구현·보상권 merge 표현은 이 범위에서 대체한다.

> 2026-10-13 추가 확정: [무료권 정책 §1/4/5](../contracts/FREE-EXAM-PASS-AND-PAID-SWITCH-RESTORATION-2026-10-13.md)에 만료순→지급순(무기한 후순위), 복원권 현재 무기한, LC `EXAM_PASS/SUBSCRIPTION`·세부출처 Billing 관리, 보상권 계정귀속·탈퇴재가입 자동복구없음·정상merge 미사용권 이전, Billing confirm/차단/복원 원자확정과 LC status-first 후속복구를 반영했다. exact schema/wire·merge 경합·보존 구현은 후속이다.

> 2026-10-13 사용자 승인: [공통 무료 모의고사권·유료 전환 복원](../contracts/FREE-EXAM-PASS-AND-PAID-SWITCH-RESTORATION-2026-10-13.md)을 우선 적용한다. ACTIVE paid로 새 시험을 성공적으로 confirm할 때 미완료 최초 trial 그룹을 종료하고 별도 무료권 1개를 원 lineage당 한 번 복원한다. 구매만으로 지급하지 않으며 원 TrialClaim/consumption은 보존한다. 기존 무료 replacement 우선·새 무료권 미지급 표현은 이 좁은 승인 범위에서 대체한다. 친구 초대는 출처 확장 경계만 준비하며 지급 구현은 후속이다. exact schema/wire·LC 공동 계약·구현/배포는 별도다.

> 2026-10-13 무료재응시 보존: 무료시험 중 구매해도 유효TRIAL group은 기존replacement 유지·유료group으로 자동전환하지 않는다. paid-first는신규INITIAL에만적용, Claim/consumption불변·새무료권지급없음. 다른권리새group전환은기존source환불/만료로재응시불가인경우에한정. [LC 인계 R2 §9](../contracts/LC-BILLING-PAID-EXAM-HANDOFF-R2.md) 참조.

> 2026-10-13 사용자 승인: 별도 포기 버튼 없이 기존 새 시험 시작 흐름을 활용한다. 동일 유효source는 기존group replacement, 환불source replacement는 차단, 다른유효권리 INITIAL은 새group으로 전환하며 원source/이력 보존. 실패시 이전Session만 먼저 종료하지 않도록 durable 전환·CAS·status-first 공동설계. [인계 R2 §8](../contracts/LC-BILLING-PAID-EXAM-HANDOFF-R2.md)을 따른다. 구현/배포는 별도.

> 2026-10-13 LC 검토 5항목 보완 [인계 R2](../contracts/LC-BILLING-PAID-EXAM-HANDOFF-R2.md) 작성. source operation보존/새제출제한없음/오류보상/역방향인증/순서·보존·lifecycle 후보를 따르며 공동동결 전 구현불가.

> 2026-10-13 LC 공동 확정용 [유료 시험 연동 인계 R1](../contracts/LC-BILLING-PAID-EXAM-HANDOFF-R1.md) 작성. 신규 field/오류/revoke 인증은 후보이며 구현 전 양측 수락 필요. 기존 상태event v1 재사용·Billing 내부증거 저장 방향을 따른다.

> 2026-10-13 사용자 승인 정책 변경: [환불 전 confirm Session 완료 허용](../contracts/PAYMENT-CONFIRMED-SESSION-REFUND-POLICY-2026-10-13.md)을 우선 적용한다. 환불 전 confirm된 exact Session은 기존 제출 기한 안에 완료 허용, 환불 선행 confirm 및 신규/재응시는 차단한다. paid 전용 채점 승인 API는 제외하고 durable revoke 전달·구매별 최소 이용 증거는 유지한다. 기존 reserve 응답 출처 추가 방향이며 exact wire/LC 공동 동결은 별도다. 아래 OPEN 제출·채점 차단/GRADING-only 완료·전용 승인 API·v2 필수의 종전 표현은 이 변경 범위에서 역사적 기록으로 대체된다. 코드·LC 저장소·배포는 이번에 변경하지 않았다.


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
