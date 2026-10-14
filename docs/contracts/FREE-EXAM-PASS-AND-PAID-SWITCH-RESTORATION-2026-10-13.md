# 무료 모의고사권과 유료 전환 복원 정책

- 확정일: 2026-10-13
- 승인 근거: 공통 무료권 모델 「어 그렇게 하는 게 좋아보인다」, 사용 순서·복원권 무기한·EXAM_PASS/SUBSCRIPTION 선택, 계정 귀속·전환 복구 「1번은 그렇게 하는 게 좋아보여 좋아 2번 그렇게 하자」
- 추가 승인: 무료 제출 경합 제한적 허용·최초 consumption당 복원 1회·현재 무기한/지급순 구현으로 단순화. MEMBER 보상권의 Guest merge 확장 제외.
- 상태: 제품 정책·설계 방향 승인. exact schema/wire·LC 공동 계약·구현·배포는 후속
- 적용: PLAN-011 공통 무료권 사용·유료 전환 복원 설계, PLAN-013 보존·운영 준비

## 1. 공통 무료권과 지급 출처

사용자에게는 무료 모의고사권의 사용 가능한 수량을 합산해 보여준다. Billing은 각 grant의 지급 출처·원장·사용 그룹을 분리해 보존한다. 현재 EntitlementGrant의 benefitCode/sourceType/sourceId 및 unit allocation/ledger 구조를 재사용하는 방향이며, mutable 합산 수량만을 원장으로 두거나 별도 paid credit 모델을 추가하지 않는다. 현재 엔티티가 공통 필드를 갖는 사실이 trial 전용 resolver/owner/reader의 일반화 구현 완료를 뜻하지 않는다.

| 지급 출처 | 정책 | 이번 범위 |
| --- | --- | --- |
| 최초 휴대전화 무료 체험 | 기존 TrialClaim 유일성·3년 보존 및 최초 reserve 지급 유지 | 기존 계약 유지 |
| 유료 전환 복원 | 미완료 최초 무료 체험의 남은 재응시 기회를 새 무료권 1개로 전환 | 정책·설계 승인 |
| 친구 초대 보상 | 향후 유효 초대 1건에 무료권 1개 등 별도 정책으로 지급 | 출처 확장 경계만 준비; 조건·API·지급 구현 미포함 |

친구 초대 등 보상을 TrialClaim으로 가장하지 않는다. 출처별 지급 멱등성은 별도로 관리하고 예약·hold·소비·cancel/expiry의 exact allocation 복구는 공통으로 처리한다. 무료권 사용 순서·복원권 만료·계정 귀속은 아래 승인 규칙을 따른다. reader의 exact 응답과 이전 schema는 구현 전 공동 확정한다. 기존 phone trial owner rebind를 모든 보상권에 자동 적용하지 않는다.

### 1.1 사용 순서·만료 — 승인

ACTIVE paid를 먼저 사용한다. 현재 무료권은 무기한 권리만 구현하고 지급 시각 순서로 사용한다. 지급 시각까지 같을 때의 결정적 tie-breaker는 구현에서 정한다. 만료형 무료권 발급·만료 처리·만료순 선택은 이번 구현 범위에서 제외한다.

미래에 만료 기능이 필요하면 추가한다. 그때의 선택 정책은 만료 가까운 순→지급순이며 무기한 권리는 뒤에 둔다. 기존 무기한 권리에 소급 만료를 적용하지 않는다. 최초 trial의 Claim 보존·dedupe 만료와 권리 만료는 구분한다.

### 1.2 계정 귀속 — 승인

복원권과 미래 초대권은 지급받은 계정에 귀속한다. 탈퇴 후 같은 전화번호로 재가입해도 해당 권리를 자동 복구·이전하지 않는다. 최초 trial의 기존 phone 재가입·owner/epoch 계약은 유지한다.

복원권은 MEMBER의 유료 전환 때 지급하고 미래 초대권도 MEMBER 전용으로 설계한다. 따라서 보상권에 Guest→MEMBER merge 기능을 확장하지 않는다. 종전 보상권 미사용 수량 병합 계획은 제외한다. 최초 trial의 기존 Guest merge/phone owner 계약은 유지한다. MEMBER 간 계정 병합이 실제 제품 기능으로 도입될 때 새 보상권 이전을 별도 검토한다.

무기한 이용 가능 정책이 개인정보 무기한 보존을 뜻하지 않는다. 활성 권리·원 consumption 중복 방지 증거의 최소 보존, 탈퇴 후 연결 제거·purge는 PLAN-013에서 기존 trial 보존 규칙과 충돌 없이 설계한다.

## 2. 유료 전환 시 사용자 동작

무료 시험 시작 → 이탈 → 유료 이용권 구매 → 새 시험 시작에서 ACTIVE paid를 선택 → 유료 INITIAL confirm과 복원 확정 → 기존 무료 그룹 종료 및 무료 모의고사권 1개 사용 가능 상태로 표시한다. 새 유료 시험은 새 AttemptGroup/Session이며 이전 답안·결과를 승계하지 않는다. 별도 포기 버튼은 추가하지 않는다.

구매만으로는 무료 그룹을 종료하거나 복원권을 지급하지 않는다. paid가 없으면 기존 무료 그룹의 replacement를 유지한다. ACTIVE paid가 있는 신규 시작은 기존 무료 replacement보다 paid 전환을 우선한다. Billing에서 복원 확정 전에 완료로 확인한 무료 시험에는 복원권을 지급하지 않는다. LC 제출·완료의 동시 경합은 §4의 제한적 예외를 따른다. paid 기간은 기존 구매/timeline 규칙대로 흐르며 시험 전환 때 재기산하지 않는다.

예: 무료권으로 시험을 시작해 수량이 0인 상태에서 나간 뒤 유료권을 구매했다면, 유료 시험으로 성공적으로 전환한 뒤에는 **유료 이용 중 + 무료 모의고사권 1개**가 된다. 미래 초대 보상 1개가 별도 지급되면 화면에서는 2개로 합산할 수 있지만 각 출처 이력은 유지한다.

## 3. 원장과 중복 방지

원래 TrialClaim·grant consumption을 삭제·취소하거나 claimedAt을 갱신하지 않는다. 기존 무료 그룹의 재응시 가능성을 종료하고 별도 복원 grant와 추적 가능한 보상 ledger를 남긴다. 새로운 TrialClaim을 만들지 않으며 cancel/expiry의 allocation 반환과 구분한다.

복원은 최초 무료 체험 source만 대상으로 하고 **원래 consumption ID당 최대 1회** 지급한다. 원 consumption이 실제 최초 trial임을 Billing 저장 증거로 검증한다. 새 그룹·operation·replacement Session마다 지급하지 않는다. 복원권과 미래 초대권에서 소비한 시험은 복원 대상이 아니다. 복원권 후손을 재귀적으로 추적하는 일반 계보 모델은 만들지 않는다.

Billing은 paid confirm·복원 자격 확인·기존 무료 그룹 재응시 차단·복원 grant/ledger·전환 기록을 하나의 Mongo Transaction으로 확정한다. 원 consumption에 대한 복원 지급 unique 제약과 동일 operation의 멱등 재현을 유지한다. duplicate-key/unknown-commit은 기존 기록으로 수렴시킨다. source 연결·retention·index·ledger enum의 exact schema는 PLAN-011에서 동결한다. trial 연결 purge/retention expiry를 우회하거나 개인정보를 무기한 보존하지 않는다.

## 4. 서비스 간 경합과 복구 — 단순화 승인

`reserve → LC 새 Session commit → Billing confirm → LC 기존 Session 종료/전환 완료` 순서를 유지한다. Billing에서는 유료 확정·복원권 지급·기존 무료 그룹 재응시 차단을 함께 commit하고, LC는 성공 증거를 확인한 뒤 기존 Session을 순차적으로 종료한다. confirm 이전에 이전 무료 Session만 먼저 종료하지 않는다.

양 서비스에서 무료 제출과 유료 전환을 완벽하게 동시에 차단하는 조정은 구현하지 않는다. 전환 직전에 LC가 이미 접수한 무료 제출은 채점·완료를 허용할 수 있다. 완료 이벤트가 늦게 Billing에 도착하면 **기존 무료 시험 결과와 복원권 1개가 함께 남을 수 있으며 이를 허용한다.** 이 경합만을 이유로 이미 지급한 복원권을 회수하거나 유료 confirm을 취소하지 않는다. Billing이 복원 Transaction 전에 이미 완료로 확인한 경우에는 복원하지 않는다.

이 예외는 새 무료 Session/replacement 허용이나 종료 후 신규 제출 허용이 아니다. Billing은 차단된 원 그룹의 신규 사용을 거절하고 LC는 실제 종료 이후 신규 제출을 거절한다. 이미 접수된 Job·제출은 기존 멱등 규칙으로 처리한다. LC 종료 지연 중의 접수/종료 순서와 기존 Job 판정은 LC 로컬 상태 전이로 결정한다. 두 서비스에 걸친 무중첩 보장이나 새 분산 lock은 요구하지 않는다.

유료 confirm 전 실패하면 복원권을 지급하지 않는다. confirm 성공 응답 유실이나 LC 종료 실패는 같은 operation의 authoritative status를 확인하고 영속 재시도로 후속 종료를 완료한다. 전환 기록에는 원 그룹과 새 그룹/Session 연결을 남긴다. CONFIRMED를 cancel로 되돌리거나 실패마다 새 operation으로 지급하지 않는다. LC 후속 종료가 지연됐다는 이유로 추가 복원하지 않는다.

환불이 paid confirm보다 앞서면 전환 실패·복원 없음, confirm이 앞서면 확정한 복원권은 해당 paid 구매 환불로 회수하지 않는다. 환불 및 owner 변경의 기존 안전 조건을 유지한다. 상태/DTO·종료 단계·오류 복구 fixture는 LC와 공동 확정한다. 이 단순화는 최초 trial에서 paid로 전환하는 경우에 적용하며 다른 source 전환/환불 계약에 자동 확대하지 않는다.

### 4.1 LC 마무리 확정

기존 시험 생성 operation을 LC 종료 처리까지 복구 가능한 상태로 유지하고, 별도 종료 복구 시스템은 만들지 않는다. 새 Session 사용 가능·이전 Session 신규 제출 차단·operation 성공을 LC 로컬 Transaction으로 확정한 뒤 앱에 성공을 반환한다. 기존 Job 채점 완료까지 기다리지 않는다. 미완료 작업은 TTL로 삭제하지 않으며 같은 요청·operation으로 복구한다.

종료 전 durable하게 접수한 문항/요청의 작업은 완료 허용하고 종료 후 사용자 새 제출은 차단한다. 원 접수 증거에 연결된 Summary Job은 종료 후 처음 생성돼도 허용하며 시스템 채점/요약 재시도도 기존 멱등·lifecycle 조건에 따라 허용한다. Job 생성 시각만으로 차단하지 않는다. 부분 접수만 있다면 남은 문항을 새로 받지 않으며 기존 완료 조건을 충족할 때만 완료 처리한다. 상세 기준은 [LC 인계 R2 §10](LC-BILLING-PAID-EXAM-HANDOFF-R2.md)을 따른다.

## 5. PLAN-011 및 reader 변경 준비

- ACTIVE paid-first/free-preserve resolver를 공통 무료권과 전환 복원에 맞춰 확장한다. FREE_EXAM_ONCE의 기존 trial wire 의미를 임의로 재정의하지 않는다.
- source 전달은 공통 무료권을 TRIAL로 오표기하지 않도록 수정한다. 사용자 승인으로 LC 전달 분류를 `EXAM_PASS|SUBSCRIPTION`으로 정하고 체험·복원·초대의 세부 지급 출처는 Billing 내부에서 관리한다. 기존 `TRIAL|SUBSCRIPTION` 후보는 대체한다. field 위치·nullable/legacy 처리·DTO/fixture·공동 배포 계약은 LC와 동결해야 한다. 기존 trial snapshot의 이관·LC strict reader 선배포·operation/Session 영속 source 보존은 유지한다.
- 기존 무료 reader의 예상 신규 trial 수량과 실제 발급된 여러 grant 수량을 구분한다. 조회에서 발급·복원하지 않으며 합산 응답·PENDING/거절 사유·grant/source 이관은 별도 exact API 계약으로 확정한다.
- 최초 trial 유일성·owner/epoch, refund 전 confirm된 exact Session 완료 허용 및 신규/replacement 차단 계약은 유지한다. 새 제출 timeout·paid 전용 채점 승인 API는 추가하지 않는다.
- 초대 이벤트 수신·보상 지급·coupon·출석은 이번에 구현하지 않는다. 이후 별도 승인 시 지급 출처와 멱등성만 추가할 수 있게 경계를 준비한다.

## 6. 구현 전 검증 항목

미완료 최초 trial→구매→paid confirm 시 복원 1개, 구매만/confirm 전 실패/Billing에 이미 완료 반영 시 복원 없음, 원 consumption당 재시도·동시 전환·응답 유실에서도 지급 1회를 검증한다. 복원권·초대권 소비는 복원하지 않는다. cancel/expiry는 exact hold만 반환하고 replacement는 추가 소비하지 않는다.

LC에서 이미 접수한 무료 제출이 전환 뒤 완료돼도 유료 확정/복원권은 유지되는 경합을 검증한다. 원 그룹 재응시 차단, LC 종료 뒤 신규 제출 거절, 기존 접수의 중복 처리 방지, confirm 응답 유실/후속 종료 실패의 status-first 복구를 확인한다. 무료권 지급순·무기한 유지·탈퇴 재가입 자동복구없음·기존 trial owner/merge 회귀도 검증한다. 만료형 무료권과 보상권 merge/초대 지급 구현은 제외한다.

이 문서는 R2 §9의 무료 replacement 우선 및 새 무료권 미지급 결정을 이 승인 범위에서 대체한다. 기존 정책 문구는 이 변경 외의 trial cancel/expiry/rebind와 미승인 보상 지급에 계속 적용한다. 제품 코드·타 저장소·DB·RevenueCat·Jira·배포는 이번 문서 작업에서 변경하지 않는다.
