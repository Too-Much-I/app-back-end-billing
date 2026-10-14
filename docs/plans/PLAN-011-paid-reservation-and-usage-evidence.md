# PLAN-011: 유료 시험 승인·권리 전환·무료권 복원·구매별 이용 증거

> 2026-10-14 TMI-215 구현: [유료 예약·복원 안내](../runbooks/TMI-215-paid-reservation-restoration.md). paid-first/동일 source replacement/phone target INITIAL·confirm/무료 hold/최초 trial 소비당 복원1회·원 group 재응시 차단·receipt/이용 원장을 단일 ClientSession으로 구현했다. r3 receipt/r5 payment ledger는 명시 maintenance만 가능하며 실DB 설치 없음. 기본 OFF/paid-admission 기동 거절 유지. HTTP/상태 이벤트 MEMBER 귀속·reader/expiry 연결/LC 전체28 검증은216, 삭제보존217·PLAN012/013 활성화 gate 별도. TMI214는 사용자 승인 commit3691f98·PR18 develop mergeebaf8d8 완료,215는 feat/TMI-215-paid-reservation-restoration 로컬 변경이다.

> 2026-10-14 TMI-214 구현: [MEMBER 증거/복구 안내](../runbooks/TMI-214-member-evidence-operation-recovery.md). 계정 증거 writer·MEMBER receipt 대기/재개/J3/status·최초 source snapshot을 구현했다. 단일 ClientSession의 Admission 경계이며 실제 paid resolver/confirm/복원 연결은 TMI-215. 기본 OFF·r2 명시 schema·LC reader 선배포 gate를 유지한다. PR17로 TMI-213 develop 병합 완료. 전체 상태28 완료나 실환경 활성화가 아니다.

> 2026-10-14 TMI-213 구현·로컬 검증 완료: [기반 안내](../runbooks/TMI-213-paid-exam-foundation.md). 단일 Transaction/session·optional source reader·receipt/guard CAS·복원 unique 기반과 명시 schema를 구현했다. 최종 clean test1,497개(신규23 포함), 실패/오류/skip0. 다음은 TMI-214 MEMBER 증거/대기 업무 연결이며 운영 schema 설치·실제 paid admission·전체28시나리오 완료는 아니다. 아래 과거 문서-only 상태는 이번 좁은 기반 구현 범위에서 갱신한다.

### Jira 구현 추적 — 2026-10-14

상위 TMI-137(결제 기능). 사용자 요청으로 작업5개 생성, 모두 해야 할 일·미할당. 구현 순서는213 →214 →215 →216,217은 별도 공동 계약 준비이며 관련 경로 활성화의 선행 조건이다.

- [TMI-213](https://to-teacher.atlassian.net/browse/TMI-213): 공통 Transaction·저장 기반
- [TMI-214](https://to-teacher.atlassian.net/browse/TMI-214): MEMBER 증거·대기·같은 key 복구
- [TMI-215](https://to-teacher.atlassian.net/browse/TMI-215): 유료 우선 reserve·confirm·무료권 복원
- [TMI-216](https://to-teacher.atlassian.net/browse/TMI-216): LC 연동·이용 증거·실제 경합 검증
- [TMI-217](https://to-teacher.atlassian.net/browse/TMI-217): 삭제 전달·과거 owner·자료별 폐기 공동 계약


현재 구현 기준은 [범위 한정 수락 기록](../contracts/LC-BILLING-PAID-EXAM-SIMPLIFIED-R1-ACCEPTANCE.md)과 [단순화 exact draft-3](../contracts/LC-BILLING-PAID-EXAM-SIMPLIFIED-EXACT-R1.md)이다. 기존 R2에 대한 변경 범위는 이 보완 계약이 우선한다. 공동 오프라인 검증은 완료됐으며 제품 구현·실환경 통과와 구분한다. 과거 제안의 삭제 대기열·영구 key 보장·phone 무료 우선은 구현하지 않는다.

- 갱신일: 2026-10-14
- 상태: 최신 합의에 맞춘 구현 계획 정리 완료. 검토된 exact 범위 수락·양측 오프라인 검증 완료. 이번 작업은 계획/ADR 동기화와 구현 범위 확정이며 제품 코드·배포는 포함하지 않는다.
- 관련: PLAN-010/012/013, LC TMI-116/TMI-128/TMI-122, lifecycle TMI-125. 이번 작업에서 Jira 생성·변경 없음.
- 기준: [공동 계약 R2 draft-2](../contracts/LC-BILLING-PAID-EXAM-CONTRACT-R2.md), [무료권·복원 정책](../contracts/FREE-EXAM-PASS-AND-PAID-SWITCH-RESTORATION-2026-10-13.md).
- 종전 계획 전문은 [이력](PLAN-011-HISTORY-BEFORE-R2-2026-10-14.md)에 보존한다. 폐기된 제안을 현재 구현 요구사항으로 사용하지 않는다.

- P0 산출물: [저장 구조·Transaction 설계 R1](PLAN-011-STORAGE-TRANSACTION-DESIGN-R1.md), [LC 공동 구현 합의안 R1](../contracts/LC-BILLING-PAID-EXAM-R2-IMPLEMENTATION-AGREEMENT-R1.md). 저장 설계의 내부 명칭/index는 구현 시 대조한다. 공동 wire의 현재 수락 범위는 위 수락 기록을 따르며 구현 완료가 아니다.

## 1. 5줄 결론

1. PLAN-010이 만든 구매·기간권을 기존 reserve → LC Session 저장 → confirm에 연결한다.
2. Billing은 사용 권리 선택·유료 확정·최초 체험 복원·원 그룹 재응시 차단을 담당하고, LC는 문제 선택·새 시험 활성화·이전 신규 제출 차단을 담당한다.
3. source는 EXAM_PASS/SUBSCRIPTION이며, 같은 요청은 source·전환·성공/거절 증거를 영속 snapshot에서 복구한다.
4. 기존 fixtures-2와 새 simplified fixtures-1은 양측 오프라인 검증이 완료됐다. 실제 HTTP·업무·Transaction·경합 검증은 남아 있다.
5. 구현은 아래 단계로 나누며, S15/V07 미확정 경로와 PLAN-012 환불 통합·PLAN-013 배포 조건을 만족하기 전 유료 입장은 활성화하지 않는다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 제품 동작

| 상황 | 동작 |
| --- | --- |
| 신규 시험 | ACTIVE paid 우선. 없으면 유효 무료권을 판정하며 불명 상태를 유효 권리로 추정하지 않음 |
| 미완료 시험 재시작 | LC가 같은 mock를 선택. 같은 유효 권리면 원 그룹 REPLACEMENT, 권리 전환이면 새 그룹 INITIAL |
| 전체 필수 답안 접수 후 채점/요약 대기 | 직전 mock 제외, 실제 다른 적격 후보가 0개인 경우만 동일 mock. 항상 INITIAL·신규 권리 판정 |
| 완료 후 새 시험 | 기존 문제 선정 규칙, INITIAL·신규 권리 판정 |
| 최초 체험에서 유료로 전환 | 복원 자격을 확인해 paid confirm·무료권 1개 지급·원 그룹 재응시 차단을 같은 Billing Transaction에서 처리 |
| 사용할 권리 부족 | 새 요청 거절. 이전 시험을 먼저 종료하지 않음 |
| 전환 전 접수된 작업 | 원 시험에서 채점·결과 저장·파생 Summary 최초 생성·시스템 재시도 완료 허용. 부분 접수만으로 전체 완료를 만들지 않음 |
| 전환 후 이전 시험 신규 제출 | 차단. 원 시험의 늦은 완료는 새 그룹/미완료 operation guard를 해제하지 않음 |

새 Session은 처음부터 시작하며 답안·점수·Job·요약을 승계하지 않는다. 신규 제출 마감은 추가하지 않는다. 기존 채점 시간 제한을 제출 마감으로 해석하지 않는다. 별도 유료 채점 승인 API를 만들지 않는다.

복원은 구매 시점이 아닌 자격 있는 유료 confirm 시점이다. 최초 trial consumption ID당 한 번만 지급하며 원 TrialClaim/consumption은 보존한다. 복원권·초대권 소비에는 재복원하지 않는다. 늦은 원 시험 완료 때문에 확정된 복원권을 회수하지 않는다. 현재 무료권은 무기한·지급순이며 초대 실제 지급·만료 기능·보상권 Guest merge 확장은 후속이다.

### 2.2 단계와 서비스 경계

| 범위 | 책임 |
| --- | --- |
| PLAN-010 | 구매 검증·Purchase·기간권 timeline·sync. 011은 이 권리를 읽고 시험 승인에 연결 |
| PLAN-011 Billing | 권리 선택, source/transition snapshot, reserve/confirm/cancel/status, 사용자 guard, 복원 원장, 구매별 최소 이용 증거 |
| PLAN-011 LC 연동 | durable 접수 증거·startReason·단일 mock 선택, operation/replay, Session 전환 마무리·신규 제출 차단·기접수 완료 |
| PLAN-012 | 환불/revoke 원장·reflow·권리 종료·불변 revoke outbox/발신 및 LC 수신. 011의 confirm과 공통 경합 경계 검증 |
| PLAN-013 | 실환경 키/port/TLS·schema 설치·reader/writer 전환·legacy 목록·복구·보존/운영·활성화 |

LC 구현은 별도 저장소 담당 작업이다. 이 계획 정리로 LC 코드/설정은 변경하지 않는다. 011에서 revoke 전달 시스템이나 환불 원장을 중복 구현하지 않는다.

## 3. 사용자가 결정해야 하는 사항

이미 승인한 제품 정책·startReason·source·공통 전환 완료 규칙을 다시 선택할 필요는 없다. 다음은 구현 준비/착수 시 처리할 기술 항목이다.

- 정책 재선택 없이 §5.0의 순서로 구현을 준비한다. 수락된 오류·MEMBER 재개·120일·phone 선택 규칙을 새 미확정 사항으로 되돌리지 않는다.
- 단일 ClientSession, validator/index 및 OFF 설정 의존성을 기존 코드와 대조하는 P0가 첫 구현 단위다. 내부 저장 필드 결정으로 외부 wire를 추가하지 않는다.
- 남은 공동 계약은 신규 삭제 route/인증·과거 owner 삭제 근거·자료별 최종 purge다. 해당 계약이 필요한 성공 경로와 삭제 기능은 분리하고 활성화하지 않는다.
- 이번에는 계획·ADR·작업 규칙을 동기화한다. 실제 제품 구현·배포 완료로 기록하지 않는다.

## 4. 주요 위험과 미확인 사항

| 항목 | 구현/활성화 조건 |
| --- | --- |
| LC strict reader | source/transition을 읽고 operation/Session에 저장하는 reader를 writer보다 먼저 준비. 누락 source를 TRIAL로 fallback하지 않음 |
| command TTL | 성공·확정 거절·reservation 생성 전 terminal 실패도 TTL과 별도 증거에서 승인된120일 창 동안 재현. 미완료 operation/guard는 복구 전 TTL 삭제 금지 |
| S15 phone/삭제 | target paid-first INITIAL/원 무료 REPLACEMENT와 복원 규칙은 확정. 신규 삭제 route/과거 owner 삭제 coverage는 미확정으로 활성화 gate 유지. 명시 삭제 시험의 재응시 복구 제외 |
| V07 보존 | 금융 원장 보존과 LC 학습/receipt/group 증거 보존을 구분. 불명확한 purge는 켜지 않으며 무기한 개인정보 보존을 승인하지 않음 |
| 오래된 상태 이벤트 | 원 그룹의 완료/취소/만료가 새 그룹이나 미완료 생성 operation의 자리를 해제하지 않도록 exact group/operation/version으로 처리 |
| 환불 경합 | entitlement/account/guard의 공통 쓰기 경계로 confirm과 refund 순서를 확정. 단순 snapshot 읽기로 경합을 해결했다고 보지 않음 |
| 기존 시험 | 전환 전 서버 exact 시험 ID 목록으로 legacy 구분. source-null 추정·가짜 무료 consumption backfill·일괄 종료 금지 |

S15/V07 전체를 나중으로 넘겨 놓고 모든 사용자 경로를 지원한다고 보고하지 않는다. 영향받는 저장/복구 구조는 해당 상세 확정 후 구현하고, 독립적으로 확정된 경로는 기본 OFF로 개발할 수 있도록 단계를 분리한다. 장기 금융 정리 후속 분리 결정은 현재 요청 복구 증거를 잃어도 된다는 뜻이 아니다.

## 5. 현재 작업과 직접 관련된 구현 계획

### 5.0 착수 순서와 분리 범위

| 순서 | Billing 구현 단위 | 완료 증거 / 의존 경계 |
| --- | --- | --- |
| 1 — P0 | 무료·결제 repository가 참여하는 단일 ClientSession adapter, source/receipt/guard/복원 unique의 명시 schema·index, 기본 OFF 구성 | replica-set에서 다중 쓰기 중 실패 rollback·중첩 Transaction 금지·기존 무료 회귀. 실제 DB 설치는 별도 |
| 2 — P1 | 기존 purchase-account의 검증 MEMBER 증거 저장, WITHDRAWN 선행 거절, WAITING_MEMBER_EVIDENCE receipt·status·same-key 재개, source/결과 snapshot reader | E01~E03/E06/T01~T06. pending commit 후503 반환,7일 cache 소실 후 복구·payload 충돌·120일 정각 검사 |
| 3 — P2/P3 | paid-first reserve·사용자 guard·confirm/복원/원 그룹 차단·최초 결과 replay, 확정 phone 선택/복원 | S 및 P/C 시나리오 실제 실행. C당 unique·동시 confirm·unknown commit·refund 공통 쓰기 경계 검증 |
| 4 — P4/P5 | LC reader/operation 연동, purchase 이용 증거·통합 권리 조회 일치, 실제 HTTP/worker/경합 회귀 | LC 코드 작업은 LC 담당. 새 Session 활성/이전 제출 차단 local commit 후 성공, 마지막 제출/전환 양순서 검증 |
| 별도 계약 의존 | 삭제 event route·JWT allowlist·과거 owner 귀속/지연 삭제 및 자료별 purge | wire·인증·귀속표·추가 fixture 확정 후 구현. phone 정책 확정만으로 삭제 coverage 통과로 보지 않음 |

P1의 MEMBER 대기는 terminal 실패가 아니다. 앱이 기존 purchase-account로 증거를 보강해도 status는 예약을 만들지 않는다. LC HTTP 재개만 같은 key로 reserve를 재시도하고 worker는 reserve를 호출하지 않는다. LC는 대기를 영속하고 일반 PREPARED cleanup과 분리하며 owner/lease/version 및 Session·marker·reservation·cancel 부재를 검증한다. NEEDS_REVIEW 예외는 **동일 MEMBER 대기 때문에 소진됐다는 영속 증거와 현재 안전 조건을 모두 충족한 경우만** 허용한다. retry_exhausted나 보강 성공만으로 격리를 해제하지 않는다.

120일 보장은 LC 최초 durable 접수 firstAcceptedAt부터10,368,000초 미만이다. equality부터 보장 밖이며 terminal 상세 purge 후보는 terminalAt+동일 기간이다. 진행 중·금융·소비·복원1회·권한 차단 근거는 따로 보존한다. 영구 key 시스템이나 Identity 탈퇴 보존기간 변경은 포함하지 않는다. Billing이 LC 최초 접수 시각을 추정하거나 새 wire 필드를 임의 추가하지 않는다.

### 5.1 P0 — 계약·schema·의존성 준비

- 기존 fixtures-2 및 simplified fixtures-1 수락 기록을 기준으로 구현 범위를 구분한다. 계약 원문과 fixture bytes는 수정하지 않는다.
- 기존 ReserveService, ReservationLifecycleService, AttemptGroupEventService, ExamAdmissionReader와 PLAN-010 권리 조회 경계를 확인한다.
- source snapshot, 사용자 guard, 복원 유일성, 성공/실패 receipt의 저장 위치·필수 field·unique index·version/CAS·schema 설치 및 rollback 제한표를 작성한다. 컬렉션 이름/schema 버전은 기존 저장소를 확인해 확정한다.
- 실제 owner/phone/삭제 writer 의존성과 S15/V07 영향을 분리하고 단계별 테스트/완료 조건을 연결한다.
- 완료 조건: 승인된 wire와 저장 구조 사이 대응표, Billing/LC 분담, reader 선행 순서, 미확정 경로 목록이 준비됨. schema 설치나 코드 구현 완료를 뜻하지 않음.

### 5.2 P1 — source·응답 snapshot·복구 reader

- reserve 성공부터 EXAM_PASS/SUBSCRIPTION 필수. PREPARED의 미정 상태와 구분한다. 구매/entitlement 연결은 Billing 내부 immutable source로 저장한다.
- reserve/status가 동일 source와 transition snapshot을 반환한다. 실패/생략 응답으로 저장된 값을 null로 덮어쓰지 않는다.
- 기존 owner/session epoch와 command 멱등성을 보존한다. 인증·owner fence와 canonical 동일성을 확인한 뒤 기존 결과를 재현하고, 가변 current 대상 stale 검사는 신규 operation에 적용한다.
- LC는 source/kind/원 대상/mock를 operation/Session에 보존한다. source reader 준비 전 paid 응답을 활성화하지 않는다.
- 검증: S07/S08/S14 및 wire/status 구조를 실제 codec·repository 경로에서 검증. TTL 후 재현은 승인된 receipt 구조에 의존한다.

### 5.3 P2 — 권리 선택·reserve·guard

- 인증된 LC의 restartContext 세 필드를 검증하며 startReason을 source enum/mock/현재 OPEN 상태로 추정하지 않는다.
- ACTIVE paid-first와 무료권 지급순 선택을 연결한다. 동일 권리 REPLACEMENT는 원 consumption을 재사용하며, 신규 INITIAL과 혼동하지 않는다.
- source 선택·hold·Reservation·사용자 공통 guard를 동일 Transaction으로 처리한다. 기존 Trial guard/owner fence를 제거하지 않는다.
- INITIAL은 새 group, REPLACEMENT는 원 group/mock, 원 Session과 다른 새 Session. context 있는 INITIAL만 exact 원 대상 transition을 필수 반환한다.
- 권리 부족/실패는 이전 시험을 선종료하지 않는다. cancel/5분 expiry는 자신의 예약만 정리하며 CONFIRMED를 되돌리지 않는다.
- 검증: S01~S04/S08/S09, paid/free 동시 reserve, replacement cancel/expiry, owner rebind 경합. phone 확정 분기는 P01~P05로 구현 검증하고 과거 owner 삭제 근거에 의존하는 분기는 보류 사유를 명시한다.

### 5.4 P3 — confirm·복원·전환 확정

- paid confirm, 자격 있는 최초 trial 복원 1회, 원 그룹 재응시 차단 및 원장을 한 Transaction으로 저장한다. 원 consumption별 unique 제약과 duplicate-key 수렴으로 중복 지급을 방지한다.
- guard는 exact 원/신규 group·operation/version으로 이전/유지한다. 늦은 원 시험 완료가 신규 자리를 해제하지 않도록 상태 이벤트 처리를 함께 수정한다.
- PLAN-012와 공통 경합 경계를 사용한다. refund 선행 irreversible 거절은 CANCELED + confirmRejection.code=ENTITLEMENT_REVOKED + terminalAt를 함께 저장하고 revoked hold를 사용 가능 수량으로 복원하지 않는다.
- confirm 성공 선행이면 환불 이후에도 최초 성공을 replay한다. 일반 CANCELED와 환불 거절을 구분하며 rejectedAt은 추가하지 않는다.
- 검증: S09/S10/S12/S13, 중복/동시 confirm, unknown commit, 복원 후 지연 완료, confirm/refund 양 commit 순서.

### 5.5 P4 — LC 마무리·공개 오류 공동 연동

- LC가 접수 완료 증거와 operation 준비를 Transaction/CAS로 순서화하고 사유·원 대상·단일 mock를 고정한다. tracking OFF/marker 부재를 미접수 증거로 쓰지 않는다.
- 흐름은 reserve → 확인 대기 Session 저장 → confirm/status 성공 확인 → LC 로컬 마무리 Transaction이다.
- LC 로컬 Transaction에서 새 Session 활성·exact 이전 Session 신규 제출 차단·operation SUCCEEDED를 함께 저장한 뒤 앱 성공 응답을 보낸다. 기접수 채점 완료를 기다리지는 않는다.
- 기존 operation/worker로 복구하고 별도 마무리 작업 시스템을 추가하지 않는다. 모호한 응답·RESERVED·404만으로 Session 보상/새 key 재지급하지 않는다.
- 기접수 문항의 파생 Summary·시스템 재시도는 허용하고 사용자 신규 제출 유래 Job만 차단한다. 삭제/lifecycle deny를 우회해 Session을 살리지 않는다.
- 기존409 EXAM_CREATION_PROCESSING 및 기존503 BILLING_TEMPORARILY_UNAVAILABLE, Retry-After:1을 유지한다. 신규409 ENTITLEMENT_REVOKED/EXAM_TRANSITION_CONFLICT의 앱 reader를 검증한다.
- 검증: S05~S07/S10~S14/S16, 마지막 제출/준비 양순서, Billing 성공 응답 유실, LC 마무리 실패/재시작, 새 그룹 guard 보호.

### 5.6 P5 — 구매별 증거·통합 완료

- purchase → Reservation/group/Session 연결과 reserve/confirm/cancel/expiry·기존 상태 이벤트의 최소 증거를 보존한다. 기존 상태 이벤트 v1을 유지하며 확인하지 못한 세부 품질 값을 임의로 만들지 않는다.
- 무료와 유료·취소 예약·시도 수와 완료 그룹 수를 구분한다. 삭제/보존 후 불명은 UNKNOWN이며 0회로 단정하지 않는다.
- 답안·음성·피드백/점수 상세는 복제하지 않는다. 운영 조회는 owner/환경을 확인하는 내부 query/감사 경계로 제한하며 공개 관리자 API나 자동 환불 금액 계산을 추가하지 않는다.
- GET /api/v1/entitlements의 무료 수량/유료 기간·입장 가능 상태가 실제 resolver와 맞는지 확인한다. 유료/무료를 한 수량으로 합치거나 조회에서 지급하지 않는다.
- replica-set Transaction/경합 테스트 및 기존 무료/owner 회귀를 실행한다. PLAN-012 실제 revoke와 양측 E2E는 활성화 전 공동 완료 조건이다.

## 6. 부록 — 검증 근거·완료 조건

### 6.1 현재 검증 상태

| 항목 | 근거/상태 |
| --- | --- |
| Billing 오프라인 | [fixtures-2 보고서](../contracts/LC-BILLING-PAID-EXAM-FIXTURES-2-VALIDATION.md): canonical18/clock5/invalid13/token31/HTTP모형12/wire6/status3 및 hash13 통과 |
| 기존 R2 LC 독립 검증 | 사용자 전달 2026-10-14 인수 기록: Java/JCA/Jackson/Instant 89개, 전체 clean test948개(89 포함), 실패/오류/skip0. 이번 문서 작업에서 재실행하지 않음 |
| bytes | LC 보고상 SHA256SUMS 포함14파일 동일. SHA256SUMS 자체 SHA-256: bb4458f3e7b6ed801795fbd652fc41e2d5e6c6c2d752b9ef693d6f43e4893659 |
| 계약 | lc-billing-paid-exam-contract-r2-draft-2, SHA-256: 3210c7eef1fb2f9fdde101c2384c86a5bbde1f9be346da0651c971e357444c52 |
| 미실행 | 실제 HTTP/security chain/port/TLS/DB 무호출・업무/Transaction・상태26개 시나리오. S15/V07 보류 |

fixture revision은 `lc-billing-paid-exam-r2-fixtures-2`다. manifest의 lcIndependentValidation=false는 제작 당시 상태로 보존하고 LC 결과는 위 접수 기록으로 구분한다. 원 계약의 제작 전 상태 문구도 hash 보존을 위해 이번에 고치지 않는다. 오프라인 기대값 수락이 실제 제품 검증이나 최종 공동 동결은 아니다.

현재 simplified 검증은 Billing80벡터, LC81검사(80+hash/범위1) 및 전체1,029개(81 포함)다. LC 수치는 사용자 전달 보고이며 이번 작업에서 재실행하지 않았다.10파일 동일, 계약 hash `79bcfe3d79225c21d712791266bea10d4aed9eed3dc0529ddf86f7b29673a482`, SHA256SUMS hash `35f7d437fb562e1427efe385ffaf1721a60f4780fcaacf495c2a0c7e787c2436`. 새 상태28개와 기존 R2 상태26개는 실제 미실행이며 서로 다른 fixture 집합으로 합산하지 않는다. 원본 제작 당시 미완료 문구와 manifest는 그대로 보존한다.

### 6.2 구현·활성화 완료 기준

- 실제 reserve/confirm/cancel/status·응답 유실·TTL 후 replay·무료권 복원 및 exact guard 테스트 통과.
- 정상 만료 전 reserve의 기존 5분 내 confirm, 만료 후 신규/replacement 차단, 이미 confirm된 exact Session 완료 보호 검증.
- PLAN-012 통합에서 환불 선행 confirm 거절/confirm 선행 보호, 다른 구매·무료권 보존 검증. V01~V06 실제 revoke/인증 검증은 012·LC 분담으로 수행하며 여기서 완료 처리하지 않음.
- 확정 phone 조합을 실제 검증하고, 남은 삭제 route/과거 owner·자료별 보존 설계와 해당 테스트를 확정. 미지원 경로는 영향 범위를 명시하고 활성화에서 제외.
- `./gradlew clean test`와 replica-set 통합 테스트를 실행하고 양측 실제 E2E 결과를 별도 보고. 오프라인 89개와 전체948개를 합산하지 않음.
- LC reader 선배포, 구 worker drain, 신규 writer coverage, legacy 최종 ID 목록, 앱 오류 호환, 실환경 키/port/TLS·복구 경보 및 운영 절차 확인.
- `paid-admission-enabled`는 기본 OFF 유지. 정확 설정 경로·의존 flag 조합은 기존 설정과 대조하고 검증하며, 011 단독 완료로 판매/유료 입장을 켜지 않음.

### 6.3 이번 변경 범위

계획서 재정리와 원문 이력 보존 및 CURRENT_STATE/WORKLOG 갱신만 수행했다. 제품 코드·LC 저장소·R2 계약 bytes·A/paid fixtures·DB·Jira·배포·commit/push는 변경하지 않았다. 기존 dirty 변경을 보존했다. 문서 링크·diff·동결 파일 hash를 검사하며 Java 변경이 없어 Gradle은 재실행하지 않는다.
