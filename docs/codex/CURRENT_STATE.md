# Billing Service 현재 상태

- 최종 갱신일: 2026-08-28
- 현재 브랜치: `develop`
- Jira: `TMI-113` — `[Billing] Reservation lifecycle 구현` (`해야 할 일`, 담당자 미지정)

## 2026-08-28 TMI-113 PLAN-003 Reservation lifecycle 구현 완료

- `POST /internal/v1/reservations/{reservationId}/confirm`, `cancel`, `POST /internal/v1/reservations/status`를 구현했다. confirm/cancel은 16 KiB strict JSON, lowercase UUID v4 path·header·body, exact enum·UTC millisecond timestamp와 canonical SHA-256 command hash를 사용한다.
- INITIAL confirm은 HELD allocation과 grant를 CONSUMED로 전이하고 `CONSUMED:<reservationId>` ledger, AttemptGroup OPEN과 Session ACTIVE를 하나의 Mongo Transaction으로 반영한다. REPLACEMENT confirm은 기존 consumption을 유지하고 group Session만 교체한다.
- INITIAL cancel·expiry는 allocation을 RELEASED, grant held를 available로 복원하고 `RELEASED:<reservationId>` ledger를 하나만 남긴다. REPLACEMENT cancel·expiry는 entitlement를 변경하지 않는다. TrialClaim·alias·subject retention은 lifecycle에서 건드리지 않는다.
- confirm·cancel·expiry는 `RESERVED + activeGuard + version` Reservation CAS를 첫 write로 사용한다. CONFIRMED 취소와 CANCELED/EXPIRED 일반 confirm은 409이며 repair route는 추가하지 않았다.
- terminal RESERVE·CONFIRM·CANCEL command는 active guard를 해제하고 `terminalAt + 7일` purgeAt을 기록한다. Reservation·ledger audit에는 TTL을 추가하지 않았다. status는 RESERVE operation과 live Reservation·group을 읽기만 하며 missing operation은 404다.
- expiry worker는 기본 disabled, scan 10초·batch 100 기본값이며 due Reservation별 독립 Transaction과 다중 ECS task CAS 수렴을 사용한다. due batch size와 oldest due lag를 식별자 없는 metric으로 기록한다.
- schema v2와 기존 index를 그대로 재사용했다. 새 collection, index, 결제·AttemptGroup 상태 event·owner rebind·repair·타 서비스 코드는 추가하지 않았다.
- `./gradlew clean test`에서 총 82개 테스트가 성공했고 실패·오류·skip은 0개다. replica-set Testcontainers가 INITIAL/REPLACEMENT, concurrent same-command replay, confirm/cancel/expiry 모든 race, multi-worker, transient retry와 unknown commit을 실제 검증했다.
- Jira `TMI-113`은 사용자 승인 없이 상태를 변경하지 않아 `해야 할 일`로 유지한다. production caller는 Learning Core saga, expiry 운영 활성화, Lattice/IAM/SG와 staging E2E 전까지 열지 않는다.

## 2026-08-28 PLAN-003 Jira 작업 생성

- TMI 프로젝트에 `TMI-113` — `[Billing] Reservation lifecycle 구현`을 `작업` 유형으로 생성했다. 상태는 `해야 할 일`, 담당자는 미지정이다.
- Jira 본문에 confirm·cancel·status, 5분 expiry worker, INITIAL consume/release, REPLACEMENT 무추가차감, terminal command 7일 보존과 confirm/cancel/expiry CAS race 완료 조건을 반영했다.
- AttemptGroup 상태 event, 탈퇴·재가입 owner rebind, repair route, Learning Core saga, Identity 변경, 실제 Lattice/AWS 배포, TrialClaim purge와 결제는 제외 범위로 명시했다.
- PLAN-003 상태를 사용자 승인·Jira 생성·구현 전으로 갱신했다. 애플리케이션 구현과 AGENTS 현재 구현 단위 전환은 아직 시작하지 않았다.
- production Reservation caller는 PLAN-003만으로 활성화하지 않고 후속 상태 event·서비스 연동·AWS staging E2E gate를 유지한다.

## 2026-08-28 PLAN-005 이후 남은 출시 작업 구분

- PLAN-005 재가입 owner rebind까지 완료하면 phone당 무료 1회, reserve lifecycle, 결과 실패 재응시와 탈퇴·재가입 승계를 포함한 Billing 핵심 제품 정책은 완성된다.
- 그러나 실제 출시 전에는 Learning Core의 Billing client·시험 생성 saga·status reconciliation과 AttemptGroup outbox, Identity 실제 SigV4 eligibility delivery/owner transfer 계약, VPC Lattice·IAM·SG·ECS 설정이 남는다.
- staging에서 same-key retry, Session commit 실패, confirm 응답 유실, expiry/confirm race, 최종 결과 실패·재가입과 unsigned/wrong-role/direct-bypass E2E를 통과해야 한다.
- production Mongo index/migration preflight, expiry worker enable, metric·alert, backup/rollback runbook과 rollout도 필요하다.
- TrialClaim 3년 만료 daily purge·24시간 SLA와 35일 backup restore purge는 별도 운영 vertical slice로 남는다. 최초 Claim 만료 전에는 반드시 구현·검증돼야 한다.
- 따라서 PLAN-005는 핵심 Billing 기능의 끝이지 전체 production 출시의 끝은 아니다. 이번 설명에서는 PLAN 번호나 계약을 새로 확정하지 않았다.

## 2026-08-28 PLAN-003 이후 재가입 권리 승계 구현 순서

- PLAN-003 Reservation confirm/cancel/status·expiry lifecycle을 먼저 구현하는 순서가 맞다. owner rebind가 소비·복원·terminal 상태를 안전하게 판정하려면 lifecycle 상태 머신과 CAS가 선행돼야 한다.
- 결과 실패 후 재가입 재응시까지 완성하려면 PLAN-003 다음에 AttemptGroup 상태 event consumer를 구현해 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE`을 신뢰 가능한 상태로 만든다.
- 그 다음 별도 vertical slice로 old eligibility REVOKED + new VERIFIED + same candidate에 따른 retained subject owner rebind를 구현한다. 새 Claim·grant는 만들지 않고 기존 미사용 unit 또는 same consumption만 새 userId가 사용한다.
- 권장 순서는 `PLAN-003 lifecycle → AttemptGroup event → 재가입 owner rebind → Learning Core saga/Lattice E2E`다.
- PLAN-003 계획은 아직 초안이고 Jira도 미생성이다. 이번 확인에서는 구현·Jira·계약·PLAN을 변경하지 않았다.

## 2026-08-28 Identity phone uniqueness와 Billing owner transfer 전제 정정

- Identity는 verified phone 하나를 동시에 한 Firebase User와 한 ACTIVE MEMBER에만 연결한다. 다른 활성 owner가 점유하면 가입·Guest 승격은 `PHONE_ALREADY_LINKED`로 실패하므로 정상 흐름에서 같은 번호의 활성 계정 두 개는 존재하지 않는다.
- 탈퇴 lifecycle이 기존 PhoneIdentity/active alias와 Firebase phone 점유를 해제한 뒤에만 같은 번호로 새 UUID 가입이 가능하다.
- 재가입 시 Billing에 남은 old `billing_subject_link`는 활성 Identity 계정이 아니라 Claim 3년 dedupe를 위한 과거 owner mapping이다. owner transfer는 두 활성 계정 사이의 이전이 아니라 이 retained mapping을 새 verified userId에 재연결하는 작업이다.
- 이전 권장안의 “기존 활성 계정이면 자동 이전 금지”는 정상 제품 분기가 아니라 Identity uniqueness drift, revoke/verified event 순서와 부분 완료를 막는 fail-closed 방어 조건으로 정정한다.
- Billing transfer의 실질 조건은 old user eligibility `REVOKED`, new user eligibility `VERIFIED`, 같은 retained candidate와 active Claim 일치다. event 순서가 아직 수렴하지 않았으면 이전하지 않고 processing/reconciliation으로 보낸다.
- 이 설명은 아직 owner-transfer 계약 승인이나 PLAN 변경을 의미하지 않는다.

## 2026-08-28 재가입 사용자의 기존 무료시험 권리 승계 공백 확인

- 현행 owner mismatch 차단은 탈퇴 후 새 UUID로 재가입한 사용자가 같은 verified phone candidate의 기존 Claim을 이어받지 못하게 한다. 중복 무료 지급은 막지만 미사용·재응시 가능 권리까지 영구 차단하는 제품 공백이 있다.
- phone당 1회 정책에 맞는 해결은 새 TrialClaim/grant를 발급하는 것이 아니라 기존 Claim·grant·consumption을 유지한 채 `billing_subject_link`의 owner를 검증된 새 userId로 이전하는 것이다.
- 권장 transfer 조건은 새 사용자의 current VERIFIED candidate가 기존 active Claim alias와 일치하고, 기존 owner가 Identity에서 WITHDRAWN/이전 가능 상태임이 인증된 event로 증명되며, active RESERVED/GRADING race가 없는 경우다. current owner가 활성 상태면 자동 이전하지 않는다.
- 미사용 또는 cancel/expiry로 복원된 grant는 같은 Claim의 available unit을 새 owner가 INITIAL reserve한다. OPEN·RETAKE_AVAILABLE은 같은 AttemptGroup·consumption·mockExamId로 새 Session만 시작한다. GRADING은 최종 수렴 전 차단하고 COMPLETED는 추가 무료 응시를 허용하지 않는다.
- 이전 Session·답안·upload·결과·Summary는 새 계정에 승계하거나 노출하지 않는다. old Session을 fencing하고 새 Session에서 처음부터 시작한다.
- Claim ID, `claimedAt`, `retentionExpiresAt`, grant와 consumption ledger는 변경하지 않아 phone당 1회를 유지한다. owner transfer 자체는 멱등 event·Transaction과 비식별 audit ledger가 필요하다.
- 이 방향은 권장안이며 아직 계약을 변경하거나 PLAN-003에 포함하지 않았다. 승인 시 별도 owner-transfer 계약과 구현 계획을 작성하고 production gate에 포함해야 한다.

## 2026-08-28 탈퇴·재가입 시 미완료 AttemptGroup 재응시 확인

- Identity의 현재 재가입 계약은 탈퇴한 계정을 복구하지 않고 새 canonical UUID `userId`를 발급한다.
- Billing은 TrialClaim과 candidate dedupe를 3년 유지하므로 탈퇴·재가입으로 새 무료권을 지급하지 않는다. 기존 Claim의 active subject link는 old userId를 가리킨다.
- 새 userId가 같은 전화 candidate로 reserve하면 기존 Claim을 찾지만 owner mismatch가 발생해 현재 구현은 `ENTITLEMENT_INSUFFICIENT`로 차단한다. 기존 OPEN·RETAKE_AVAILABLE AttemptGroup도 자동 이전하지 않는다.
- 같은 userId라면 OPEN·RETAKE_AVAILABLE은 REPLACEMENT 재응시가 가능하고 GRADING은 최종 처리 전이므로 `COMMAND_PROCESSING`으로 차단한다. 실제 재가입은 새 UUID이므로 이 예외에 해당하지 않는다.
- 재가입 사용자에게 기존 consumption 재응시를 허용하려면 phone candidate 일치만으로 이전하지 않고 Identity가 인증한 owner-transfer/rejoin event와 Billing의 원자적 subject link·AttemptGroup ownership 이전 계약이 필요하다. 현재 `UserMerged`/owner transfer wire 계약은 미확정·미구현이다.
- 이번 확인으로 계약·코드·PLAN을 변경하지 않았다. 현재 권장 동작은 자동 이전 차단이다.

## 2026-08-28 같은 consumption 재응시 처리 설명

- 최초 INITIAL confirm에서 무료 unit을 한 번 `CONSUMED`하고 그 ledger event를 AttemptGroup의 `consumptionLedgerEventId`로 연결하는 것이 목표 계약이다.
- 결과 생성 중에는 AttemptGroup이 `GRADING`이며 새 reserve를 `COMMAND_PROCESSING`으로 막는다. 최종 실패가 확정되면 후속 AttemptGroup event consumer가 group을 `RETAKE_AVAILABLE`로 전환한다.
- 재응시는 새 operationId·새 sessionId로 reserve하지만 동일 AttemptGroup ID와 고정 `mockExamId`를 사용한다. Billing은 이를 `REPLACEMENT`로 판정한다.
- REPLACEMENT는 새 TrialClaim, grant, allocation, `GRANTED`·`RESERVED`·`CONSUMED` ledger를 만들지 않는다. 기존 consumption을 유지한 채 새 Reservation·PROPOSED Session만 만들고 confirm에서 Session pointer를 교체한다.
- PLAN-002 코드에는 OPEN·RETAKE_AVAILABLE group의 REPLACEMENT 판정과 무allocation·무ledger reserve가 구현돼 있다. PLAN-003 confirm은 아직 미구현이며 결과 실패를 RETAKE_AVAILABLE로 만드는 AttemptGroup event consumer도 PLAN-003 이후 후속 작업이다.
- 따라서 계약과 일부 reserve 기반은 준비됐지만 결과 실패부터 재응시까지의 end-to-end 흐름은 아직 구현 완료 상태가 아니다.

## 2026-08-28 무료권 소비 확정과 Summary 완료 시점 재확인

- `reserve`는 무료 unit을 `HELD`로 잠가 다른 시험 생성 요청이 동시에 사용하지 못하게 하는 임시 상태다.
- 현재 확정 계약에서 무료권의 최종 소비는 Summary 생성 때가 아니라 Learning Core ExamSession이 durable commit된 직후 `confirm`에서 일어난다.
- confirm은 allocation을 `CONSUMED`로 바꾸고 AttemptGroup을 `OPEN`으로 연다. 이는 시험 1회가 시작됐다는 entitlement 확정이며 시험 결과가 완성됐다는 뜻은 아니다.
- 필수 피드백·유효 점수·Summary가 사용자에게 조회 가능해지면 별도 AttemptGroup 상태 event로 `COMPLETED` 처리한다.
- 결과 생성이 최종 실패하면 무료 Claim/grant를 새로 지급하거나 consumption을 취소하지 않고 `RETAKE_AVAILABLE`로 전환해 같은 consumption·mockExamId로 새 Session을 허용한다.
- 이번 설명으로 계약이나 PLAN-003을 변경하지 않았다. 소비 확정을 Summary 완료까지 미루려면 기존 ADR·통합 계약·PLAN과 실패·동시성 정책을 함께 변경하는 별도 결정이 필요하다.

## 2026-08-28 PLAN-003 Reservation lifecycle 계획서 작성

- `docs/plans/PLAN-003-reservation-lifecycle.md` 초안을 작성했으며 신규 Jira는 아직 생성하지 않았다.
- 범위는 confirm, cancel, read-only status와 5분 expiry worker다. 네 경로가 같은 Reservation terminal 상태를 공유하므로 하나의 vertical slice로 묶었다.
- INITIAL confirm은 allocation·grant consume, `CONSUMED` ledger, AttemptGroup `OPEN`, Session `ACTIVE`를 한 Transaction으로 처리한다. REPLACEMENT confirm은 기존 consumption을 유지한다.
- INITIAL cancel/expiry는 allocation을 원 grant로 정확히 복원하고 하나의 `RELEASED` ledger를 남긴다. REPLACEMENT는 기존 consumption을 변경하지 않으며 모든 경로에서 TrialClaim은 유지한다.
- confirm/cancel/expiry는 Reservation expected-state/version CAS를 첫 write로 사용해 경쟁 시 terminal 상태 하나만 commit한다. status는 command·ledger를 만들지 않는 조회로 고정했다.
- terminal command 7일 보존, expiry scan interval 10초·batch 100의 configurable 최초 제안, existing Mongo schema v2 index 재사용과 production caller gate를 계획에 포함했다.
- Learning Core saga, AttemptGroup 상태 event, repair, 실제 Lattice/AWS 배포와 결제는 제외했다. 계획 승인 뒤 별도 사용자 승인으로 Jira를 생성한다.

## 2026-08-28 다음 작업: Reservation lifecycle

- 다음 권장 vertical slice는 `confirm`, `cancel`, `status`와 5분 expiry worker를 함께 구현하는 PLAN-003이다.
- PLAN-002의 `reserve`는 무료 시험 unit을 `RESERVED`로 잠근 단계까지만 구현했다. 다음 작업은 Learning Core Session 저장 성공 시 `CONFIRMED`로 최종 소비하고, 저장 실패 시 `CANCELED`, 호출 없이 5분이 지나면 `EXPIRED`로 잠금을 해제한다.
- `status`는 confirm 응답 유실이나 timeout 때 새 command·ledger를 만들지 않고 기존 Reservation의 실제 결과를 조회하기 위한 read-only API다.
- INITIAL confirm은 allocation과 grant를 `held → consumed`로 전환하고 `CONSUMED` ledger, AttemptGroup `OPEN`, Session `ACTIVE`를 하나의 Transaction으로 반영한다. REPLACEMENT confirm은 기존 consumption을 재사용하고 추가 차감하지 않는다.
- INITIAL cancel/expiry는 원래 grant에 allocation을 정확히 복원하고 `RELEASED` ledger를 남긴다. TrialClaim과 `claimedAt`은 유지한다. REPLACEMENT cancel/expiry는 기존 consumption을 변경하지 않는다.
- confirm·cancel·expiry 경쟁은 Reservation 상태 CAS와 Mongo Transaction으로 한 terminal 상태만 승리하게 해야 한다. `CONFIRMED` 일반 cancel과 CANCELED/EXPIRED 자동 repair-confirm은 금지한다.
- 이 lifecycle이 완성되기 전에는 production Learning Core가 reserve route를 호출하도록 활성화하지 않는다. 이후 작업은 AttemptGroup 상태 event·reconciliation, Learning Core saga, 실제 Lattice staging E2E 순서다.
- 이번 작업은 다음 범위를 설명한 분석이며 PLAN-003, Jira, 애플리케이션 코드는 아직 생성하거나 변경하지 않았다.

## 2026-08-28 TMI-112 완료 처리

- 사용자의 명시적 승인에 따라 Jira `TMI-112`를 `해야 할 일`에서 `완료`로 전환했고 완료 category를 재확인했다.
- Jira 설명·담당자, 애플리케이션 코드, PLAN-002 API·Mongo·보안 계약과 Git branch는 변경하지 않았다.
- 완료 근거는 직전 PLAN-002 구현과 `./gradlew clean test` 총 58개 성공, 실패·skip 0 결과다.
- 실제 production 활성화를 의미하지 않는다. confirm/cancel/status·expiry, Learning Core saga와 Lattice staging gate는 후속 작업으로 유지한다.

## 2026-08-28 TMI-112 PLAN-002 initial reserve 구현 완료

- `POST /internal/v1/reservations`에 16 KiB strict JSON, 필수 lowercase UUID v4 `Idempotency-Key`, canonical SHA-256 payload hash와 직접 200 response DTO를 구현했다.
- 첫 reserve의 단일 Mongo Transaction에서 current VERIFIED eligibility, expired alias fencing, candidate/key rotation dedupe, 필요한 TrialClaim·subject link·전체 aliases·`FREE_EXAM_ONCE` grant와 `GRANTED`, INITIAL allocation hold·`RESERVED`, 5분 Reservation·PROPOSED Session과 command response snapshot을 함께 반영한다.
- 같은 operation/payload는 동일 Reservation을 replay하고 다른 payload는 409다. 다른 owner는 402로 차단하고, 같은 candidate·같은 user 동시 요청은 unique/partial unique index와 retry 후 Claim·active command·Reservation·Session 하나로 수렴한다.
- OPEN·RETAKE_AVAILABLE group은 REPLACEMENT로 기존 consumption·mockExamId를 재사용하며 새 allocation과 entitlement ledger가 없다. GRADING은 processing, mockExamId 불일치는 state conflict다.
- Mongo initializer를 schema v2로 확장해 reserve 관련 10개 collection과 ADR-001의 23개 index를 생성·option 검증한다. runtime drop/recreate와 Reservation·Claim audit TTL 삭제는 추가하지 않았다.
- test security에서 Learning Core role은 reserve route만, Identity role은 eligibility route만 허용하고 default disabled는 계속 deny한다. 실제 Lattice auth policy·SG 배포는 범위 밖이다.
- `./gradlew clean test`에서 총 58개 테스트가 통과했고 실패·skip은 0개다. replica-set Testcontainers가 initial atomicity·rollback·동시성·key rotation·expired alias·REPLACEMENT·retry·unknown commit을 실제 실행했다.
- Jira `TMI-112` 상태는 사용자 승인 없이 변경하지 않아 `해야 할 일`로 유지한다. confirm/cancel/status·expiry lifecycle과 Learning Core saga·실제 staging E2E 전에는 production reserve caller를 활성화하지 않는다.

## 2026-08-28 PLAN-002 Jira 작업 생성

- TMI 프로젝트에 `TMI-112` — `[Billing] Free exam initial reserve 구현`을 `작업` 유형으로 생성했다. 상태는 `해야 할 일`, 담당자는 미지정이다.
- PLAN-002의 reserve endpoint, INITIAL·REPLACEMENT 판정, Claim·무료 grant/ledger·allocation·Reservation 단일 Transaction, 멱등성·index·동시성·보안·개인정보 완료 조건을 Jira 본문에 반영했다.
- confirm/cancel/status·expiry, AttemptGroup event·reconciliation, 타 서비스 adapter, 실제 AWS 배포와 결제는 제외 범위로 명시했다.
- Billing lifecycle과 Learning Core saga·실제 Lattice staging 검증 전에는 production reserve caller를 활성화하지 않는 제한을 유지한다.

## 2026-08-28 reserve response 식별자·상태 의미 설명

- `operationId`는 앱이 만든 한 번의 시험 생성 명령 ID이며 Learning Core와 Billing이 같은 `Idempotency-Key`로 사용한다. transport retry에는 유지하고 의도적인 restart에는 새 값을 사용한다.
- `reservationId`는 Billing이 만든 한 번의 entitlement hold 기록 ID다. 후속 confirm/cancel이 이 Reservation을 대상으로 하며 audit 상태 전이를 보존한다.
- `reservationKind`는 새 소비를 준비하는 `INITIAL`인지 기존 consumption을 재사용하는 `REPLACEMENT`인지 Billing이 판정한 값이다.
- `reservationStatus=RESERVED`는 unit을 잠갔지만 아직 최종 소비하지 않은 상태다. Session durable commit 뒤 confirm되면 `CONFIRMED`, commit 실패·만료 시 `CANCELED` 또는 `EXPIRED`로 전이한다.
- `attemptGroupId`는 최초 응시와 허용된 restart를 하나의 consumption으로 묶는 Billing ID다. INITIAL reserve에서 미리 발급하지만 group `OPEN`은 confirm에서 확정한다.

## 2026-08-28 reserve의 sessionId·mockExamId 역할 설명

- `sessionId`는 Learning Core가 만들 예정인 한 번의 ExamSession 식별자다. reserve 시 proposed 값으로 먼저 고정해 응답 유실 retry가 같은 Session으로 수렴하고, 후속 confirm이 실제 durable commit된 바로 그 Session에 대한 것인지 확인한다.
- `mockExamId`는 AttemptGroup에서 사용할 문제지 식별자다. 최초 reserve에서 group에 고정하고 restart·REPLACEMENT가 같은 문제지를 유지하는지 확인해 다른 시험으로 entitlement를 재사용하는 것을 막는다.
- Billing은 시험 내용이나 Session을 생성하지 않으며 두 값은 1~128자 opaque token으로만 저장·비교한다. `sessionId`는 한 Session마다 바뀌고 `mockExamId`는 같은 AttemptGroup 동안 유지된다.

## 2026-08-28 PLAN-002 initial reserve 계획서 작성

- `docs/plans/PLAN-002-free-exam-initial-reserve.md` 초안을 작성했다. 신규 Jira는 아직 생성하지 않았다.
- 범위는 `POST /internal/v1/reservations`의 전체 reserve 판정으로 고정했다. 첫 INITIAL reserve의 단일 Mongo Transaction에서 command 멱등성, current VERIFIED binding, 만료 alias fencing과 dedupe, 필요한 TrialClaim·subject link·무료 grant/ledger, allocation hold, Reservation과 proposed Session projection을 함께 처리한다.
- 기존 `OPEN`·`RETAKE_AVAILABLE` group은 REPLACEMENT로 기존 consumption을 재사용하고 `GRADING`은 processing conflict, 다른 `mockExamId`는 state conflict로 처리한다.
- confirm/cancel/status, expiry worker, AttemptGroup event·reconciliation, Learning Core·Identity adapter와 실제 Lattice 배포는 제외했다. 해당 lifecycle이 완성되기 전 production caller activation은 금지한다.
- 계획 승인 뒤 별도 Jira를 생성하고 `AGENTS.md`의 현재 구현 단위를 PLAN-002로 전환한 다음 구현하는 순서를 권장한다.

## 2026-08-28 다음 구현 단위 확인

- PLAN-001 다음 권장 작업은 current `trial_eligibility`를 사용하는 `FREE_EXAM_ONCE` INITIAL reserve vertical slice다.
- eligibility event 수신과 TrialClaim 지급을 계속 분리한다. 첫 reserve의 단일 Mongo Transaction 안에서 idempotency command, current VERIFIED binding, candidate alias dedupe, 필요한 TrialClaim·subject link·grant·`GRANTED` ledger, allocation hold·`RESERVED` ledger와 Reservation을 함께 만든다.
- TrialClaim이나 grant만 미리 생성하는 별도 작업은 승인 계약을 위반하므로 진행하지 않는다.
- 다음 구현 전 `PLAN-002`와 별도 Jira로 reserve 범위·index·동시성 완료 조건을 고정하고 `AGENTS.md`의 현재 구현 단위를 갱신해야 한다.
- 그 이후 순서는 confirm/cancel/status·5분 expiry, AttemptGroup event·reconciliation, Learning Core saga와 Identity/Lattice staging 연동이다.

## 2026-08-27 TMI-110 완료 처리

- PLAN-001 구현과 33개 전체 테스트 통과를 근거로 Jira `TMI-110`을 `해야 할 일`에서 `완료`로 전환했다.
- Jira 설명·담당자·애플리케이션 코드·Git 브랜치는 변경하지 않았다.

## 2026-08-27 로컬 main·develop 브랜치 생성

- 현재 feature 브랜치의 커밋 `e0694f9`를 기준으로 로컬 `main`과 `develop` 브랜치를 생성했다.
- 현재 checkout은 `feat/TMI-110-trial-eligibility-event-consumer`에 그대로 유지했다.
- 이후 원격에도 `main`과 `develop`이 생성된 것을 확인했고 GitHub 기본 브랜치를 feature 브랜치에서 `main`으로 변경했다.

## 2026-08-27 PLAN-001 Trial eligibility consumer 구현 완료

- `POST /internal/v1/eligibility/trial/events`에 16 KiB bounded 수신, strict JSON decode, schema v1·expected scope 검증과 canonical SHA-256 digest를 구현했다.
- `inbound_event_inbox`와 `trial_eligibility`를 단일 Mongo Transaction으로 반영하며 APPLIED·DUPLICATE·STALE과 `EVENT_ID_CONFLICT`로 수렴한다. verified는 candidate 전체를 교체하고 revoked는 candidate를 제거한 revision tombstone을 유지한다.
- `ux_inbox_event_id`, `ux_inbox_identity_scope_user_revision`, `ttl_inbox_purge_at`, `ux_trial_scope_user`, `ix_trial_key_version`을 versioned initializer가 생성·비교하고 option 불일치 시 fail-fast한다. `auto-index-creation`은 false다.
- internal ingress는 기본 disabled, test Identity role, 필수 설정을 검증하는 Lattice AWS_IAM mode로 분리했다. 실제 AWS principal 검증은 ADR-002대로 Lattice policy와 SG가 담당하며 Lattice 실배포는 이번 범위가 아니다.
- Identity fixture 기반 decoder, MVC·security, transaction retry·unknown commit 재확인과 `mongo:7.0.14` replica-set index·rollback·동시성 테스트를 추가했다.
- `./gradlew clean test`에서 33개 테스트가 통과했다. TrialClaim, grant·ledger, Reservation·AttemptGroup, Identity SigV4 adapter와 실제 Lattice/IAM/SG는 추가하지 않았다.
- Jira `TMI-110`의 상태·담당자는 별도 수정 승인 없이 변경하지 않았다. 저장소 전체가 아직 Git 미추적 상태이므로 사용자가 기준선과 이번 구현 diff를 검토해 commit해야 한다.

## 2026-08-27 PLAN-001 Jira 작업 생성

- Jira `TMI-110`을 생성해 `docs/plans/PLAN-001-trial-eligibility-event-consumer.md`의 구현 범위와 완료 조건을 작업 단위로 고정했다.
- 포함 범위는 `/internal/v1/eligibility/trial/events`, 16 KiB 제한, strict decode, canonical digest, `inbound_event_inbox`, `trial_eligibility`, 단일 Mongo Transaction, 명시적 index 검증과 replica-set Testcontainers 테스트다.
- TrialClaim, grant·ledger, Reservation·AttemptGroup, Identity SigV4 adapter, Learning Core saga와 실제 Lattice 인프라는 이 이슈에서 제외하고 후속 vertical slice로 유지한다.
- 이슈 상태는 `해야 할 일`, 담당자는 미지정이다. 애플리케이션 코드와 계약 문서는 변경하지 않았고 테스트도 실행하지 않았다.

## 2026-08-27 통합 계약 보정 재검증

- ADR-001 inbox의 `consumerScopeId`, APPLIED·STALE disposition, `ux_inbox_identity_scope_user_revision`이 PLAN-001·AGENTS와 일치하도록 보정된 것을 확인했다.
- eligibility 409는 `EVENT_ID_CONFLICT` 전용, `COMMAND_PROCESSING`은 Reservation 전용으로 ADR·통합 계약이 정렬됐다.
- 공개 `POST /api/v1/exams`의 필수 lowercase UUID v4 `Idempotency-Key`, body 없음·기존 성공 DTO 유지, transport retry와 의도적 restart의 key/Session 구분이 AGENTS·통합 계약·Learning Core 계약 문서에 반영됐다.
- Identity ADR은 목표 transport를 VPC Lattice AWS_IAM·ECS task role·SigV4와 새 eligibility route로 바꾸고, 429·503 `Retry-After` 처리와 현재 Bearer adapter의 미이관 상태를 명시했다.
- 검토한 현행 계약 문서에서 구 eligibility route, optional 시험 생성 key, inbox disposition/index 충돌과 새 whitespace 오류는 확인되지 않았다.
- 문서 계약 보정과 실제 구현 완료는 다르다. Identity 실제 adapter는 아직 Bearer이고 `Retry-After`를 읽지 않으며, Learning Core controller는 필수 header와 Billing saga를 아직 구현하지 않았고 Billing도 PLAN-001 구현 전이다. 이는 문서에 명시된 후속 구현 항목이다.
- 아래의 이전 정합성 리뷰 섹션은 당시 발견 기록이며, 현재 판단은 이 재검증 섹션과 상단 `통합 계약 불일치 보정` 섹션이 대체한다. WORKLOG의 과거 기록은 수정하지 않는다.

## 2026-08-27 AGENTS.md 최신 계약 반영

- 저장소 루트 `AGENTS.md`를 다른 앱 서버와 같은 수준의 작업 규칙으로 보강했다.
- 명시적 요청 없이 Billing 저장소만 변경하고 Identity·Learning Core는 계약 확인용 읽기 대상으로만 사용하도록 저장소 경계를 명확히 했다.
- 현재 최소 Entitlement 구현 범위와 Apple/Google 결제·paid credit·pass·coupon·환불·사용자 Billing API의 후속 범위를 분리했다.
- TrialClaim 계약을 `claimedAt + 3년` 보존, 기간 내 재수급 차단, 만료 후 candidate 연결 purge와 재수급 허용으로 최신화했다.
- `/internal/v1/eligibility/trial/events`, strict decode·canonical digest, inbox·revision·projection Transaction과 event 수신만으로 grant하지 않는 규칙을 추가했다.
- workload 인증은 VPC Lattice `AWS_IAM` + ECS task role + SigV4로 고정하고 Identity·Learning Core·repair role의 route 권한 분리와 local/test adapter 원칙을 반영했다.
- Mongo unique index·Transaction·Reservation expiry, 보안·멱등성·범위 이탈을 중심으로 한 코드 리뷰 우선순위를 추가했다.
- Jira 키는 없고 애플리케이션 코드·ADR·PLAN·외부 API와 배포 설정은 변경하지 않았다.

## 2026-08-27 AGENTS.md Billing 전용 정교화

- `AGENTS.md`에서 무료 MVP 전체 제품 범위와 현재 즉시 구현할 PLAN-001 Trial eligibility consumer 범위를 분리했다.
- PLAN-001에는 `inbound_event_inbox`, `trial_eligibility`, strict decode·canonical digest, APPLIED·DUPLICATE·STALE·CONFLICT와 단일 Mongo Transaction만 포함하고 TrialClaim·grant·Reservation은 후속임을 명시했다.
- internal API는 앱용 `BaseResponse`를 사용하지 않고 16 KiB 제한, body 없는 204와 승인된 stable error envelope를 사용하도록 반영했다.
- public client userId 비신뢰 원칙을 유지하면서 인증된 Identity event와 Learning Core internal route가 canonical UUID userId를 body로 전달하는 서비스 계약 예외를 명시했다.
- Reservation의 `Idempotency-Key=operationId`, opaque sessionId/mockExamId, POST status와 repair-confirm 기본 차단을 후속 구현 규칙으로 추가했다.
- 승인된 collection/index 이름, explicit versioned index initializer, inbox 120일 TTL·TrialClaim 3년·Reservation audit 보존의 분리를 Mongo 규칙과 리뷰 항목에 반영했다.
- Jira 키는 없다. 애플리케이션 코드·ADR·PLAN·외부 API·인프라 설정은 변경하지 않았다.

## 2026-08-27 서비스 간 통합 계약서 추가

- `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`를 추가해 앱·Identity·Learning Core·Billing의 책임과 호출 방향을 한 문서에서 확인할 수 있게 했다.
- Identity→Billing의 Trial eligibility event route, SigV4 principal, schema·멱등성·revision 처리, 응답과 at-least-once retry를 정리했다.
- Learning Core→Billing의 reserve→Session commit→confirm, cancel·status, confirm 응답 유실 reconciliation과 AttemptGroup 상태 event를 정리했다.
- VPC Lattice AWS_IAM·환경 격리, 공통 HTTP·식별자·오류·개인정보 경계, TrialClaim 3년 보존, local/test와 배포·변경 순서 및 E2E 체크리스트를 포함했다.
- 이 문서는 상위 흐름 안내서이며 세부 충돌 시 ADR-001·ADR-002가 최종 기준임을 명시하고 `AGENTS.md` 문서 경로에도 연결했다.
- Jira 키는 없다. 애플리케이션 코드·기존 ADR·PLAN·외부 API·AWS 리소스는 변경하지 않았다.

## 2026-08-27 통합 계약 외부 검토 지적 확인

- 첨부 검토의 4건을 Billing ADR·PLAN·AGENTS, Identity publisher 코드와 Learning Core 시험 생성 코드에 대조했다.
- ADR-001 `inbound_event_inbox`에 `consumerScopeId`와 `(producer, scope, user, revision)` unique index가 없고 disposition이 PLAN의 APPLIED·STALE 저장 모델과 다른 지적은 정확하다. PLAN-001 Phase 0에 이미 보정 작업으로 명시돼 있으며 Step 1 전에 ADR을 고쳐야 한다.
- Billing 목표는 Lattice AWS_IAM·task role·SigV4지만 Identity 실제 adapter는 audience 기반 짧은 workload Bearer credential을 사용한다. 이는 알려진 구현 공백이며 Billing local PLAN-001은 진행 가능하지만 staging publisher 활성화 전 Identity adapter·ADR·contract test를 SigV4와 새 route에 맞춰야 한다. publisher는 기본 disabled라 현재 요청을 잘못 보내는 활성 운영 상태는 아니다.
- Identity delivery가 HTTP status만 반환해 `Retry-After`를 읽지 못하는 지적은 맞다. 다만 eligibility endpoint의 구체 계약은 409를 `EVENT_ID_CONFLICT`로만 사용하고 `COMMAND_PROCESSING`을 반환하지 않으므로 error body를 읽어 두 409를 구분해야 한다는 부분은 현재 범위에 불필요하다. ADR 공통 오류표의 eligibility processing 표현은 구체 endpoint 계약과 일치하도록 정리할 필요가 있다.
- Billing 최신 계약은 앱→Learning Core 시험 생성의 필수 UUID v4 `Idempotency-Key`지만 Learning Core 과거 문서는 optional이고 실제 controller는 header를 받지 않는다. Reservation saga 전에 앱·Learning Core를 함께 전환해야 하는 실제 계약/구현 공백이다.
- 앱 종료 후 새 key·새 examId로 restart하는 규칙을 AGENTS와 통합 계약에 보강하자는 제안은 타당하다. `/internal/v1/attempt-group-events`의 hyphen은 eligibility namespace에만 적용된 no-hyphen 규칙을 위반하지 않는다.
- Jira 키는 없다. 이번 확인에서는 계약·애플리케이션·다른 서버 코드를 수정하지 않았다.

## 2026-08-27 통합 계약 불일치 보정

- ADR-001 `inbound_event_inbox`에 `consumerScopeId`, APPLIED·STALE 저장 disposition과 `ux_inbox_identity_scope_user_revision` unique partial index를 반영해 PLAN-001과 정렬했다.
- ADR 공통 409 `COMMAND_PROCESSING`을 Reservation command 전용으로 명확히 하고 eligibility 409는 `EVENT_ID_CONFLICT` 전용으로 유지했다.
- PLAN-001 Phase 0 문서 보정을 완료 상태로 갱신하고 Step 1을 ADR 계약의 실제 document/index 구현·검증 단계로 바꿨다.
- 통합 계약과 AGENTS에 공개 시험 생성의 필수 lowercase UUID v4 `Idempotency-Key`, Request Body·성공 DTO 유지, transport retry와 앱 종료 restart의 새 key·새 examId 구분을 반영했다.
- Identity ADR-002의 이전 Bearer route를 Billing C3-D Lattice AWS_IAM·task role·SigV4와 `/internal/v1/eligibility/trial/events` 목표로 보정하고 429·503 `Retry-After` 처리 계약을 추가했다. 관련 기존 Jira는 `TMI-95`이며 Jira는 변경하지 않았다.
- Learning Core의 Billing 계약 검토 문서에서 optional header를 최신 필수 header 계약으로 보정했다. 실제 Identity adapter와 Learning Core controller·Reservation saga 코드는 아직 변경하지 않았다.
- 애플리케이션 코드·AWS 리소스·외부 배포는 변경하지 않았다.

## 현재 구성

- Java 21, Spring Boot 3.4.2, Gradle Groovy 기반 초기 프로젝트다.
- MongoDB, Web, Validation, Security Resource Server, Actuator 의존성이 준비되어 있다.
- 애플리케이션 이름은 `app-back-end-billing`, 기본 로컬 포트는 `8082`다.
- MongoDB 연결은 `MONGODB_URI` 환경변수로 재정의한다.
- `/actuator/health`만 공개하고 그 밖의 요청은 인증 계약 구현 전까지 차단한다.
- 원격 저장소 `origin`은 `https://github.com/Too-Much-I/app-back-end-billing.git`이다.

## 확정된 경계

- Billing은 상품, 결제 검증·원장, entitlement, credit, pass, TrialClaim, Reservation을 소유한다.
- Identity는 사용자 계정과 토큰 발급을 소유한다.
- Learning Core는 시험 Session과 학습·채점 상태를 소유한다.
- Reservation 기본 흐름은 `reserve → Learning Core Session commit → confirm`, `RESERVED` TTL은 5분이다.
- raw phone과 실제 결제 credential·receipt·token 원문은 저장소, 로그, 작업 문서에 남기지 않는다.
- Billing 계약의 단일 기준은 `docs/codex/CONTRACT_DECISIONS.md`다. 앞으로 Billing 관련 결정과 작업기록은 Billing 저장소 `docs`에만 기록한다.

## 현재 계약 결정 단계

- 결제·credit·pass·coupon·환불 구현은 후속 릴리스로 연기됐다. 기존 계약은 삭제하지 않고 동결한다.
- verified-phone당 무료 모의고사 1회를 위해 Billing은 최소 Entitlement(`TrialClaim`, `FREE_EXAM_ONCE`, reserve/confirm/cancel, reconciliation)를 먼저 구현한다.
- 기존 확정사항은 Billing의 `CONTRACT_DECISIONS.md`로 이관했다.
- 2026-08-26 사용자가 무료 최소 Entitlement 권장안을 전부 승인했고 후속 인프라 확인 뒤 C3-D VPC Lattice + ECS task role + SigV4 + AWS_IAM을 최종 승인했다. 사용자 Billing API와 사용자 Billing audience(C1/C2)는 결제 단계까지 보류한다.
- Billing production 배포 전 production/staging 두 ECS cluster와 환경별 데이터·credential·IAM·Lattice/SG 격리를 준비하는 것이 확정된 배포 gate다. 현재 운영 트래픽을 처리하는 `tosunsaeng-staging-cluster`는 새 production cluster로 트래픽을 안전하게 전환한 뒤 최종 staging으로 사용한다.
- 결제 adapter 전 C9~C11(store 상품 연결, credit 만료, 환불/chargeback)의 승인이 필요하다.
- 보상 전 C12(출석/coupon)의 승인이 필요하다. C13은 `claimedAt + 3년` 보존, 기간 안의 기존 Claim 유지와 만료 후 같은 번호 재수급 허용으로 확정됐다.

## 아직 구현하지 않은 항목

- Billing 도메인 모델, API, Mongo index와 transaction
- Lattice/SigV4 workload 인증과 reserve/confirm/cancel/status 계약 구현
- Apple/Google server verification adapter와 notification 처리
- reconciliation scheduler와 운영 관측성
- Lattice service network/service/listener/target, ECS task role·auth policy·security group

## 2026-08-26 ADR-002 VPC Lattice·ECS SigV4·환경 이관 기준 작성

- `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`를 승인된 C3-D와 production/staging 이관의 구현 기준으로 추가했다.
- production/staging 별도 Lattice service network, Billing HTTPS 443 listener→HTTP 8082 ECS target, `AWS_IAM`, exact task-role route policy·SG direct bypass 차단을 고정했다.
- Identity role은 eligibility event, Learning Core role은 reservation/status/AttemptGroup event만 호출하고 반대 환경 service와 repair route는 deny한다.
- Java client는 AWS SDK v2 `DefaultCredentialsProvider`, SigV4 service `vpc-lattice-svcs`, region `ap-northeast-2`, exact generated DNS·1초 connect/3초 request timeout·same-key caller retry를 사용한다.
- 현 cluster의 운영 트래픽은 새 production을 먼저 구성·병행 검증하고 전환한다. 전환 후 기존 cluster의 production DB/Secret/role 참조를 제거하고 staging으로 전환한다.
- local shell에 AWS credential이 없어 실제 role/VPC/subnet/SG/Lattice ID는 조회하지 못했다. 임의 ARN을 적지 않고 배포 전 read-only inventory/infra output으로 주입하도록 했다.
- AWS/GitHub Actions·application code는 변경하지 않았다.

## 다음 권장 작업

- 상세 구현 계획은 `docs/plans/PLAN-001-trial-eligibility-event-consumer.md`에 작성했다.
- 즉시 다음 작업은 ADR-001의 Identity `PhoneEligibilityBindingVerified`/`Revoked` event inbox·revision high-water·current binding vertical slice 구현이다.
- v1 DTO/validation, eventId·payload digest 멱등성, Mongo Transaction, unique index, stale/duplicate/conflict response와 fail-closed security test를 함께 구현한다.
- 이 단계에서는 AWS Lattice를 직접 호출하지 않고 inbound workload 경계를 adapter/test principal로 격리해 local test한다.
- 그다음은 TrialClaim·candidate alias·subject link·free grant/ledger와 INITIAL reserve Transaction, 이후 confirm/cancel/status·5분 expiry·AttemptGroup이다.
- Identity·Learning Core SigV4 client와 실제 Lattice/IAM/SG는 Billing API vertical slice가 안정된 후 연동한다.
- credential이 있는 AWS 환경의 role/VPC/subnet/SG read-only inventory와 새 production/staging 이관은 production 배포 준비 gate로 남겨두되, application 개발을 차단하지 않는다.
- 계획 작성 중 same user·scope·revision/different event conflict를 race에서도 강제하기 위해 ADR-001 inbox에 `consumerScopeId`와 `ux_inbox_identity_scope_user_revision` unique partial index를 보강할 필요를 확인했다. 이 문서 보정은 구현 Step 1에서 코드와 함께 수행한다.
- 사용자 승인으로 eligibility 내부 API URL은 `/internal/v1/eligibility/{kind}/...` namespace로 통합한다. 현 Trial route는 `/internal/v1/eligibility/trial/events`이고, 향후 실제 API가 필요할 때 paid는 `/internal/v1/eligibility/paid/...`, coupon은 `/internal/v1/eligibility/coupon/...`를 사용한다. URL namespace만 공통화하며 종류별 DTO·권한·멱등성·aggregate는 분리한다. URL segment에는 hyphen을 사용하지 않는다.
- Trial current projection collection은 `trial_eligibility`, Java package는 `trialeligibility`로 유지한다. index는 `ux_trial_scope_user`, `ix_trial_key_version`, metric은 `billing.trial_eligibility.events`를 사용하고 Billing class와 PLAN 제목·fixture는 `TrialEligibility*` 계열을 사용한다. Identity에 이미 구현된 wire event type `PhoneEligibilityBindingVerified`/`Revoked`는 schema v1 호환성을 위해 유지한다. ADR-001, ADR-002, PLAN-001에 확정 route를 반영했다.
- PLAN-001의 strict decoder는 입력을 저장하기 전에 JSON 구조·타입·계약 상수·event별 field·candidate·시간 순서를 검증하고, configured expected `consumerScopeId`와 exact match하지 않으면 422로 거절한다. canonical digest는 검증된 값을 정렬·정규화한 canonical JSON의 SHA-256이므로 property/candidate 순서와 whitespace만 다른 재전송은 동일 event로 수렴하며 의미가 바뀐 payload는 conflict로 판별한다. raw payload는 저장하지 않는다.
- canonical digest pipeline은 `수신 → strict decode → 값 검증 → 정렬·정규화 → canonical JSON → SHA-256 → digest 저장` 순서다. 이는 이벤트 내용을 암호화하거나 무료권을 차감하는 절차가 아니라, 동일 eventId 재전송의 내용 동일 여부를 비교하기 위한 결정적 지문 생성 절차다.
- PLAN-001은 무료권 지급 구현이 아니라 Identity의 verified/revoked phone eligibility event를 Billing에 안전하게 동기화하는 첫 vertical slice다. Lattice 인증, 16 KiB 제한·strict decode, digest, inbox 멱등성, revision high-water current projection을 거쳐 inbox와 `trial_eligibility`를 한 Mongo Transaction으로 반영한다. 결과는 APPLIED·DUPLICATE·STALE·CONFLICT로 수렴하며 TrialClaim·grant·Reservation은 후속 단계다.

## 다음 작업 전 확인할 사항

- internal API wire DTO, Mongo collection/index/Transaction과 contract test 기준은 `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`로 구체화했다.
- Lattice/ECS/SigV4·환경 이관 기술 기준은 ADR-002로 구체화했다. 남은 작업은 credential이 있는 환경의 read-only AWS inventory와 ADR-001 Identity event consumer vertical slice 구현이다.
- 그다음 store 상품·만료·환불 정책 C9~C11을 확정한다.
- Billing 관련 후속 기록은 Learning Core가 아니라 이 저장소의 `docs/codex`에만 남긴다.
- Mongo transaction 통합 테스트는 replica set Testcontainers로 설계한다.

## 2026-08-25 구현 시작 분석

- 현재 Billing 코드는 health-only 공개 보안 골격뿐이며 도메인 entity, repository, controller와 service는 아직 없다.
- Identity의 `PhoneEligibilityBinding` schema v1 producer와 lease/retry/dead-letter publisher는 구현돼 있다. Billing에는 event inbox, revision high-water, current verified binding consumer와 staging E2E가 없다.
- Learning Core의 `POST /api/v1/exams`는 현재 Billing reserve 없이 기존 진행 Session을 abandon하고 새 `ExamSession`을 즉시 insert한다. Billing 연동 시 이 경계를 `reserve → Session commit → confirm` saga와 동일-operation 복구로 바꿔야 한다.
- 2026-08-25 분석 당시 1차 구현 우선순위는 (1) 최소 계약 승인, (2) workload 보안 경계, (3) phone eligibility consumer의 inbox·high-water·binding Transaction, (4) 무료 1회 TrialClaim·grant ledger, (5) 멱등 Reservation reserve/confirm/cancel/status와 5분 만료, (6) reconciliation·관측성, (7) Learning Core contract client와 staging E2E였다. 최소 계약 승인은 2026-08-26 완료됐다.
- Apple/Google 결제 검증, paid credit, unlimited pass, coupon, 추천·출석 보상과 환불은 최소 무료 Entitlement 출시 뒤의 후속 단계다.
- 2026-08-25 당시 미확정이던 사용자 Billing API·workload 방식·멱등성·오류·AttemptGroup 정책은 2026-08-26 승인됐다. exact wire DTO와 Mongo 설계는 ADR-001로 구체화했고 환경별 Lattice 리소스·role 값은 아직 남아 있다. TrialClaim 보존기간은 같은 날 `claimedAt + 3년`으로 확정됐다.
- 현재 프로젝트 골격과 문서가 모두 Git 미추적 상태다. 기능 구현 전에 사용자가 초기 기준선을 commit해 프로젝트 생성 diff와 첫 기능 diff를 분리해야 한다.
- Jira 키는 없다. 다음 권장 작업은 C1~C8을 한꺼번에 넓게 구현하는 것이 아니라 phone binding 수신부터 무료시험 reservation까지의 vertical slice를 별도 Jira들로 나누는 것이다.

## 2026-08-25 결제 제외 무료 모의고사 1회 구현 백로그

- 이번 출시 범위는 verified-phone candidate당 `FREE_EXAM_ONCE` 1회뿐이다. Apple/Google, 유료 credit, unlimited pass, coupon, 추천·출석, 환불은 구현하지 않는다.
- 첫 reserve의 Mongo transaction 안에서 현재 verified binding을 확인하고, `(benefitScopedCandidate, FREE_EXAM_ONCE)` unique `TrialClaim`, 무료 grant/ledger와 `Reservation`을 함께 만든다. 계정 생성이나 전화번호 인증만으로 사용자 balance를 직접 증가시키지 않는다.
- reserve가 취소·만료되면 allocation은 무료 grant로 정확히 돌아가지만 `TrialClaim` 자체는 삭제하거나 다시 열지 않는다. 계정 merge·탈퇴·재가입에도 claim은 유지하고 현재 verified binding에 맞춰 entitlement owner만 이전한다.
- `Reservation` 기록은 Mongo TTL로 삭제하지 않는다. `expiresAt`을 기준으로 `RESERVED`만 명시적으로 `EXPIRED` 처리하며, `CONFIRMED` consumption과 audit ledger는 보존한다.
- 최소 데이터 경계는 phone eligibility event inbox/high-water/current binding, `TrialClaim`, free entitlement grant/ledger, allocation, `Reservation`, `AttemptGroup`, command idempotency record다. raw phone, last4와 Identity fingerprint는 저장하지 않는다.
- 최소 내부 API는 Learning Core용 reserve/confirm/cancel/status다. 동일 user·operation의 재호출은 같은 결과를 반환하고 payload가 다르면 conflict로 거절한다. 앱에 무료 사용 가능 여부를 미리 보여줄 필요가 없다면 Billing 사용자 조회 API와 Billing audience 추가는 결제 출시까지 미룰 수 있다.
- Learning Core 시험 생성은 같은 operation ID로 `reserve → Session commit → confirm`을 실행한다. Session commit 실패는 cancel하고 confirm 결과가 불명이면 Session을 `ENTITLEMENT_CONFIRMING`으로 유지해 status/retry/reconciliation으로 수렴시킨다.
- 구현 순서는 (1) 승인된 계약의 wire DTO·index 구체화, (2) Lattice/SigV4와 fail-closed 보안, (3) Identity event consumer·replay, (4) TrialClaim·ledger·Mongo unique/transaction, (5) Reservation API와 expiry, (6) Learning Core saga, (7) 양방향 reconciliation·관측성, (8) replica-set Testcontainers와 staging E2E다.
- 출시 조건은 동시 요청에도 한 candidate의 claim이 하나뿐이고, 같은-key retry가 중복 Session/소비를 만들지 않으며, Session commit 실패·confirm 응답 유실·5분 만료·owner 이전이 자동 복구되고, eligibility event가 없거나 인증이 잘못된 요청은 fail-closed 되는 것이다.
- C1/C2는 Billing 사용자 API를 이번에 노출할 때만 출시 차단 계약이다. C6의 전체 entitlement 우선순위는 결제 시 확장하되 이번 resolver는 `FREE_EXAM_ONCE`만 선택한다. 결제 관련 C9~C12는 후속 범위다.

## 2026-08-25 무료 Entitlement 계약 결정 진행

- 사용자와 선택할 이번 범위는 C1~C8과 C13이며, 결제·credit·환불·출석·coupon에 관한 C9~C12는 결정과 구현을 모두 미룬다.
- 즉시 확정이 필요한 출시 차단 항목은 workload 인증(C3), 멱등성(C4), 오류 계약(C5), 동시성과 confirm 불명 복구(C7), AttemptGroup 완료(C8), TrialClaim 보존·번호 재할당(C13)이다.
- Billing 사용자 조회 API를 이번에 제공하지 않으면 앱 호출 경로와 사용자 token audience(C1/C2)는 결제 단계까지 보류할 수 있다. 무료권만 존재하는 이번 resolver는 C6 전체 우선순위를 구현하지 않고 `FREE_EXAM_ONCE`만 서버가 선택할 수 있다.
- 이때 제안한 C1/C2 보류, 필수 앱 key, 행동별 오류, 무료권 자동 선택, C7-A, C8-A, C13-A와 기존 claim 유지 묶음은 2026-08-26 사용자 승인으로 확정됐고 workload 방식은 최종적으로 C3-D Lattice/SigV4로 대체 확정됐다.

## 2026-08-26 workload JWT와 무료권 소비 시점 분석

- Identity는 사용자 RS256 Access Token을 발급하지만 workload issuer는 구현하지 않았다. workload JWT 대신 ECS task role과 Lattice SigV4를 사용하는 C3-D가 후속 승인돼 Identity client-credentials 구현은 현재 범위에서 제외한다.
- 무료권은 전화번호 검증 event 수신이나 시험 완료 때 차감하지 않는다. 첫 `reserve` Transaction에서 current verified binding, unique `TrialClaim`, `FREE_EXAM_ONCE` grant와 allocation을 만들고 사용권을 `RESERVED`로 잠근다.
- Learning Core가 durable Session commit에 성공한 뒤 `confirm`이 Reservation과 ledger를 `CONFIRMED`/`CONSUMED`로 최종 전환한다. Session commit 실패 또는 commit 전 5분 만료는 allocation을 복구하지만 `TrialClaim`은 삭제하지 않는다.
- confirm 뒤에는 일반 cancel로 무료권을 되돌리지 않는다. confirm 결과가 불명이면 Session을 성공으로 노출하거나 즉시 삭제하지 않고 status retry와 reconciliation으로 최종 상태를 확인한다.
- AttemptGroup의 `COMPLETED`는 차감 시점이 아니라 같은 consumption으로 추가 차감 없이 restart할 수 있는 권리를 닫는 시점이다. 결과 생성이 최종 실패하면 확정된 C8-A에 따라 `RETAKE_AVAILABLE`로 전환해 같은 consumption을 재사용한다.

## 2026-08-26 무료 최소 Entitlement 계약 승인

- 사용자가 앞서 설명한 권장안을 전부 승인했다. 계약 단일 기준인 `CONTRACT_DECISIONS.md`에 승인 요약과 상태 전이를 추가하고 선택지의 해당 항목을 확정으로 변경했다.
- 이번 릴리스는 앱 → Learning Core → Billing 내부 호출만 사용하며 Billing 사용자 API와 사용자 token Billing audience는 결제 단계까지 보류한다.
- Identity eligibility event inbox/high-water/fail-closed와 첫 reserve Transaction의 unique TrialClaim·무료 grant·Reservation 생성을 확정했다.
- Learning Core와 Identity의 Billing 내부 호출은 C3-D Lattice/SigV4로 확정했다. task role별 최소 route 권한을 적용하고 privileged repair-confirm은 별도 운영 role이다.
- UUID v4 `Idempotency-Key`는 필수이고 terminal command는 우선 7일 보존한다. 행동별 402/409/429/503 오류와 same-key retry를 확정했다.
- 무료권은 서버가 자동 선택하며 reserve에서 잠그고 Session durable commit 뒤 confirm에서 최종 소비한다. confirm 전 실패는 allocation만 복구하고 TrialClaim은 유지하며 confirm 결과 불명은 status/reconciliation으로 복구한다.
- 사용자당 OPEN AttemptGroup·active Session·생성 command는 각각 하나다. 결과 조회 가능 시 group을 완료하고 결과 생성 최종 실패는 같은 consumption의 `RETAKE_AVAILABLE`로 복구한다.
- TrialClaim dedupe 연결은 `claimedAt + 3년` 보존하고 그 기간의 번호 재할당에도 기존 Claim을 유지한다. 3년 만료 뒤에는 같은 번호의 새 Claim을 허용한다. Lattice/SigV4 staging negative test는 production gate로 남는다.

## 2026-08-26 승인 후 남은 구체화 작업

- workload trust는 Lattice `AWS_IAM` auth policy와 ECS task role ARN으로 관리한다. Learning Core role은 reservation route, Identity role은 eligibility event route만 허용하고 wildcard principal은 금지한다.
- Billing은 Lattice를 거친 ingress만 허용하고 direct task·old ALB 우회 경로를 security group과 routing으로 차단한다. privileged repair-confirm은 별도 운영 role과 route policy로 분리한다.
- TrialClaim의 `retentionExpiresAt`은 immutable `claimedAt + 3년`이다. 만료 alias는 물리 purge 전에도 dedupe matching에서 제외하며 candidate/keyVersion과 사용자·source event 연결을 삭제·비식별화한다. daily purge·24시간 삭제 SLA·35일 rolling backup과 restore-before-traffic purge가 확정됐고 candidate key reference lifecycle은 구현 ADR에 반영한다.
- API DTO 설계는 internal reserve/confirm/cancel/status endpoint, 필수 `Idempotency-Key`, canonical userId·operationId·mockExamId·sessionId 연결, payload hash conflict, stable 오류 envelope와 버전 정책을 OpenAPI/contract test로 고정하는 작업이다.
- Mongo index 설계는 eventId inbox unique, user+scope current binding, candidate alias+benefit unique TrialClaim, user+operation Reservation unique, 사용자당 단일 OPEN group/active command partial unique, allocation·ledger dedupe와 expiry scan index를 정의하는 작업이다. Reservation audit 문서에는 TTL delete index를 두지 않는다.
- 즉시 진행 가능한 작업은 API DTO·collection/index ADR, Lattice/SigV4 client·ingress 보안 설계와 mock contract test다. TrialClaim 기간 숫자는 확정됐고 purge/anonymization 구현 명세만 남았다.

## 2026-08-26 AWS ECS 확인에 따른 C3 재검토

- 사용자가 실제 배포 플랫폼이 AWS ECS라고 확인했다. ECS task role은 컨테이너에 자동 회전되는 AWS 임시 credential을 제공하지만 일반 OIDC workload JWT, issuer와 JWKS를 자동 제공하지 않는다.
- 따라서 앞서 승인한 C3의 `배포 플랫폼 발급 JWT` 전제만 재검토 상태로 변경했다. Identity eligibility event, TrialClaim, Reservation, 멱등성, 오류, 소비·완료와 보존 계약은 그대로 확정 상태다.
- Learning Core → Billing 경로가 VPC Lattice 또는 API Gateway를 거친다면 task role + SigV4 + `AWS_IAM` route policy가 1차 권장이다. 별도 JWT issuer·JWKS·client secret이 필요 없고 IAM role별 최소 권한을 적용할 수 있다.
- 내부 ALB, Cloud Map 또는 ECS Service Connect로 애플리케이션을 직접 호출한다면 이 계층은 task-role SigV4 인증을 자동 검증하지 않는다. 이 경우 별도 OIDC/workload JWT issuer, 애플리케이션 SigV4 검증 adapter 또는 mTLS 중 하나가 추가로 필요하다.
- 후속 확인에서 Identity·Learning Core는 Load Balancer, Service Connect 없음, Billing 미배포, Lattice 없음으로 확인됐고 C3-D greenfield Lattice 구성이 최종 승인됐다.

## 2026-08-26 기존 Identity·Learning Core 인증 방식 대조

- 실제 구현된 공통 인증은 Identity가 사용자 RS256 Access Token을 발급하고 Learning Core가 Identity issuer·JWKS·audience·시간·UUID subject를 Spring OAuth2 Resource Server로 로컬 검증하는 방식이다. 요청마다 Identity introspection을 호출하거나 shared HMAC secret을 사용하지 않는다.
- Identity의 phone eligibility와 user merge publisher는 Bearer workload credential port와 HTTP adapter까지 있으나 `WorkloadIdentityCredentialProvider` production 구현이 없고 publisher flag가 기본 false이므로 현재 재사용 가능한 서버 간 인증 구현은 아니다.
- Learning Core → Python AI 요청은 `Idempotency-Key`만 설정하고 Authorization을 보내지 않는다. 결제·entitlement 차감처럼 권한이 필요한 Billing 내부 API의 선례로 사용하지 않는다.
- 기존 구조와 동일하게 맞추려면 사용자 Access Token을 전달하는 것이 아니라 Identity RS256 issuer/JWKS 메커니즘을 workload 전용 token profile로 확장한다. Billing은 `token_use=workload`, Billing audience, allowlist된 service subject, 5분 TTL과 endpoint scope를 추가 검증한다.
- 이 E안은 비교 대안으로만 남고 후속 C3-D 승인으로 현재 구현 범위에서 제외됐다.

## 2026-08-26 C3 세 방식 비교

- 내부 ECS-to-ECS 호출만 고려한 전체 권장 순서는 VPC Lattice + task role SigV4, Identity-issued workload JWT, API Gateway + AWS_IAM이다.
- VPC Lattice는 task role 임시 credential과 IAM auth policy를 사용해 별도 client secret·JWT issuer 없이 서비스 identity와 network 경로를 함께 관리하고 Identity 장애가 Billing 호출로 전파되지 않는다는 장점이 있다. 서비스 network·route·auth policy·direct bypass 차단과 AWS 종속성이 비용이다.
- Identity workload JWT는 현재 RS256/JWKS/Spring Resource Server 패턴을 재사용하고 direct ALB/Service Connect 경로에도 적용할 수 있으며 cloud portability가 높다. 반면 workload client-credentials·Secret rotation·token endpoint/cache를 세 서비스에 구현하고 Identity 장애와 key 운영을 감수해야 한다.
- API Gateway + AWS_IAM은 task role SigV4, route별 IAM, throttling·WAF·stage·access log가 강점이지만 순수 내부 호출에는 VPC Link/private API, route/stage와 direct bypass 차단이 추가되고 비용·latency·운영 구성이 가장 커질 수 있다.
- Billing이 내부 ECS 서비스에만 쓰이고 새 service network 구성이 가능하면 VPC Lattice를 최종 권장한다. 기존 internal ALB/Service Connect를 유지해야 하거나 AWS 인프라 변경을 피해야 하면 Identity workload JWT를 권장한다. API Gateway는 외부·파트너 공개 또는 조직 표준 gateway가 이미 있을 때 선택한다.

## 2026-08-26 ECS 현재 경로 확인과 Lattice 전환 절차

- 현재 경로는 ECS Console의 서비스 `Load balancing`, `Service Connect`, `Service discovery`, `VPC Lattice configuration`과 AWS CLI `ecs describe-services`의 loadBalancers, serviceConnectConfiguration, serviceRegistries, vpcLatticeConfigurations를 확인해 판별한다.
- EC2 Load Balancer의 scheme이 `internal`이고 ECS 서비스가 해당 target group에 연결돼 있으면 internal ALB 경로다. Service Connect enabled와 namespace/clientAliases가 있으면 Service Connect 경로이며 Cloud Map registry만 있으면 service discovery 직접 경로일 수 있다.
- Lattice 전환은 기존 경로를 즉시 제거하지 않고 task definition named port, Billing Lattice service/listener/target, VPC service network association, Learning Core·Identity task role auth policy와 SigV4 client를 먼저 병렬 배포한다.
- staging에서 허용 role의 서명 요청 성공, unsigned·다른 role 403, 동일 idempotency retry, direct Billing endpoint 우회 차단을 검증한 뒤 `BILLING_BASE_URL`을 Lattice DNS로 전환한다. rollback 기간 뒤 Billing 전용 old ALB/Service Connect route만 제거하고 공유 ALB는 삭제하지 않는다.
- 내부와 사용자 API를 같은 ALB에서 제공 중이면 old ingress를 일괄 차단하지 않는다. `/internal/**` 전용 port/service 또는 routing·security boundary를 먼저 분리해야 한다.

## 2026-08-26 실제 ECS 현황 확인

- 사용자 확인 결과 Identity와 Learning Core는 Load Balancer를 사용하고 Service Connect는 사용하지 않는다. Billing은 아직 ECS에 배포되지 않았고 VPC Lattice도 아직 없다.
- 따라서 기존 Identity·Learning Core inbound Load Balancer를 이전하지 않는다. 두 서비스의 사용자 API 경로는 그대로 두고 outbound Billing 호출만 새 Lattice DNS와 SigV4를 사용한다.
- Billing은 처음부터 public/internal ALB 없이 ECS named port를 VPC Lattice service target에 연결하는 greenfield 구성을 권장한다. Service network를 두 서비스 task가 위치한 VPC에 associate하고 Billing direct task 접근은 security group으로 막는다.
- Learning Core task role에는 reservation reserve/confirm/cancel/status invoke, Identity task role에는 phone eligibility event invoke만 허용한다. repair-confirm은 별도 운영 role로 둔다.
- Billing 애플리케이션은 Lattice auth policy 통과 경로만 내부 API에 허용하도록 배포 profile과 ingress header/identity 검증을 구성하고, unsigned·wrong role·direct path negative test를 production gate로 둔다.
- 향후 앱이 Billing을 직접 호출하는 결제 사용자 API가 생기면 별도 public ALB/API Gateway 경로를 추가하고 `/internal/**` Lattice 경계와 분리한다.
- 현재 조건에서 C3-D VPC Lattice + ECS task role + SigV4가 사용자 승인으로 최종 확정됐다.

## 2026-08-26 C3-D 최종 승인

- 사용자가 `C3-D`를 명시 승인했다. VPC Lattice + ECS task role + SigV4 + AWS_IAM이 Billing workload 인증의 확정 계약이다.
- Identity·Learning Core 기존 사용자 inbound Load Balancer는 유지하고, 미배포 Billing만 ALB 없이 Lattice service target으로 새 배포한다.
- Learning Core와 Identity는 각자 분리된 task role의 임시 credential로 요청을 SigV4 서명한다. 별도 workload JWT issuer·JWKS·client secret과 API Gateway는 이번 내부 API 범위에 만들지 않는다.
- Lattice auth policy는 Learning Core reservation route와 Identity eligibility event route를 분리한다. repair-confirm은 별도 운영 role이며 direct Billing task/old route 우회는 차단한다.
- production gate는 unsigned 요청, wrong role, 권한 없는 route와 direct path가 모두 실패하고 same-key retry와 confirm reconciliation이 staging에서 성공하는 것이다.

## 2026-08-26 C3-D 승인 후 잔여 확정사항 감사

- 무료 모의고사 MVP의 핵심 제품 계약은 구현을 시작할 수 있을 정도로 확정됐다. workload 인증 방식, 무료권 잠금·소비 시점, 멱등성, 오류, 동시성, 재응시와 번호 재할당 정책을 다시 선택할 필요는 없다.
- C13의 TrialClaim 보존기간, 기산점, 만료 후 재수급, 물리 purge SLA와 backup 수명까지 후속 승인으로 해결됐다.
- 구현 전에 ADR로 고정할 기술 명세는 internal API wire DTO·버전·오류 envelope, Mongo collection/index/transaction, Lattice 리소스·실제 task role ARN·route auth policy·security group, SigV4 signer와 local/test 대체 구현이다. 이는 승인된 정책을 바꾸지 않는 한 개발자가 권장안으로 작성해 검토받을 수 있다.
- 운영 전 수치로 고정할 항목은 reconciliation 주기·재시도/경보 기준, repair-confirm 운영 절차, event inbox/tombstone과 audit/command 보존·삭제 job이다. Identity publisher의 기존 retry와 replay 계약은 가능한 한 재사용한다.
- 결제 C9~C11, 출석·coupon C12, 사용자 Billing API·audience C1/C2와 전체 entitlement 우선순위는 현재 무료 MVP의 차단 사항이 아니며 해당 기능 착수 시 확정한다.

## 2026-08-26 TrialClaim 보존기간 선택 준비

- 유한 보존기간이 끝나 candidate/alias를 삭제하면 같은 전화번호의 재등장을 판별할 수 없으므로 이후 무료권 재수급을 허용하게 된다. 반대로 전화번호당 영구 1회를 보장하려면 무료시험 프로그램 운영 중 candidate를 계속 보존해야 한다.
- 선택지는 `claimedAt + 3년`, `claimedAt + 5년`, `무료시험 프로그램 종료 + 1년`으로 구분한다. 개인정보 최소화와 실용적 abuse 방지의 균형안은 3년, 기존의 강한 phone당 1회 정책을 가장 충실히 지키는 안은 프로그램 종료 + 1년이다.
- 유한 기간을 선택하면 기간 종료 후 같은 번호의 새 Claim을 명시적으로 허용하도록 제품 문구를 바꿔야 한다. 만료 시 candidate alias·keyVersion·user/event 연결은 삭제하고, 통계에 필요한 비연결 상태·시각만 비식별화해 남기는 방식을 권장한다.
- 이 절을 작성한 시점에는 사용자 최종 선택 전이었으며, 바로 아래 후속 승인에서 `claimedAt + 3년`으로 확정됐다.

## 2026-08-26 TrialClaim 3년 보존 최종 승인

- 사용자가 보존기간 선택지 B인 `claimedAt + 3년`을 승인했다. 이는 과거 C13의 선택지 문자인 B가 아니라 이번 보존기간 세부 선택지 B를 뜻하며, 계약상 C13-A의 구체적인 기간으로 반영했다.
- `claimedAt`은 Claim 최초 생성 시 고정하고 로그인·merge·탈퇴·revoke·cancel·expiry·restart로 갱신하지 않는다. `retentionExpiresAt`부터 만료 alias는 물리 삭제 여부와 관계없이 dedupe matching에서 제외한다.
- 3년 동안은 번호 재할당, 탈퇴와 재가입에도 기존 Claim이 무료권 재수급을 차단한다. 3년 뒤에는 candidate 연결을 제거하고 같은 번호가 다시 verified되면 새 Claim을 허용한다.
- candidate alias, keyVersion, user와 source event 연결은 삭제·비식별화한다. 개인과 다시 연결할 수 없는 최소 집계만 남길 수 있으며, raw phone·last4·전화번호 암호문은 계속 저장하지 않는다.
- 따라서 제품 계약은 더 이상 `phone당 평생 1회`가 아니라 `verified-phone candidate당 3년 내 1회`다. daily purge·24시간 삭제 SLA와 35일 rolling backup도 후속 승인으로 확정됐다.

## 2026-08-26 TrialClaim 물리 삭제·백업 주기 결정 준비

- 이 값들은 도메인 구현 착수의 선행 조건은 아니지만 C13 보존 계약과 production 운영을 완결하는 상수이므로 지금 확정하는 것이 적절하다.
- 권장안은 logical expiry 즉시 dedupe 제외, 매일 purge 실행과 만료 후 24시간 이내 active DB 연결정보 삭제, 최대 35일 rolling backup 보존이다.
- 복구한 backup은 사용자 트래픽에 연결하기 전에 현재 시각 기준 만료 purge를 먼저 실행해야 하며, backup 속 만료 candidate를 일반 조회·dedupe에 다시 노출해서는 안 된다.
- purge 실패는 재시도하고 24시간 SLA 초과 시 경보한다. 삭제 증적에는 처리 건수·실행 시각·성공 여부만 남기고 candidate, userId와 source event를 기록하지 않는다.
- 7일 backup은 개인정보 최소화에는 유리하지만 장기 장애 복구 여유가 작고, 90일 backup은 복구 여유가 크지만 만료 정보 잔존기간과 접근통제 부담이 커진다. 이 비교 뒤 사용자가 바로 아래 후속 설명을 거쳐 35일 권장안을 최종 승인했다.

## 2026-08-26 TrialClaim purge 대상 명확화

- 3년 만료 시 삭제하는 핵심은 `(benefitType, keyVersion, candidate) → trialClaimId` dedupe alias와 Claim에 붙은 user/source event/phone binding 연결이다. 이 연결을 제거해야 과거 Claim을 같은 번호와 다시 대조할 수 없고 확정된 3년 뒤 재수급이 가능하다.
- `TrialClaim` 자체는 개인·candidate·Reservation·Session으로 역추적할 수 없는 익명 tombstone으로 축약할 수 있다. 남길 수 있는 값은 benefit type, terminal status, 거친 claimed/expired 시각과 식별자 없는 purge 통계뿐이다.
- 시험 Session, 문제, 답안, 점수, 피드백과 Summary는 Learning Core 소유이므로 이 purge 대상이 아니다. Billing의 금액·사용권 감사 event도 candidate/user mapping을 분리 제거한 비식별 core만 보존할 수 있다.
- 아직 유효한 Identity current verified binding은 새 Claim 자격 확인에 필요하므로 TrialClaim 3년 만료만으로 삭제하지 않는다. revoke·phone 교체·탈퇴 event의 별도 lifecycle을 따른다.
- immutable ledger나 Reservation에 userId·candidate를 직접 영구 저장하면 이 삭제 계약을 지킬 수 없다. 구현 ADR은 변경 불가능한 audit core와 삭제 가능한 subject/alias mapping을 분리해야 한다.

## 2026-08-26 24시간 purge·35일 backup 의미 설명

- candidate 삭제를 위해 별도 backup을 만드는 계약이 아니다. MongoDB 전체를 장애·오삭제 복구용으로 주기적으로 snapshot/continuous backup하는 일반 운영 백업에, 삭제 전 candidate 연결정보가 과거 시점 데이터로 잠시 포함될 수 있다는 의미다.
- 예를 들어 2026-09-01에 Claim이 생기면 2029-09-01부터 alias를 dedupe에 사용하지 않고, daily purge가 늦어도 2029-09-02까지 운영 DB의 candidate→Claim 및 Claim→user/event 연결을 제거한다.
- 2029-09-02 이전에 생성된 DB backup에는 삭제 전 연결이 들어 있을 수 있다. 35일 rolling을 선택하면 해당 backup 자체가 생성 후 최대 35일 뒤 만료되며, 애플리케이션은 평소 backup을 조회하지 않고 제한된 운영자만 재해 복구에 사용한다.
- 오래된 backup을 복구하면 삭제됐던 연결이 되살아날 수 있으므로, 격리된 복구 환경에서 현재 시각 기준 purge를 다시 실행한 후에만 사용자 트래픽을 연결한다.
- 이 설명 시점에는 24시간/35일 수치가 권장안이었으며, 후속 사용자 승인으로 `운영 DB 식별 연결 삭제 SLA 24시간 + 전체 DB 재해복구 backup 최대 35일`이 확정됐다.

## 2026-08-26 TrialClaim purge·backup 운영 계약 최종 승인

- logical expiry는 `claimedAt + 3년`에 즉시 적용하고 만료 alias를 dedupe에서 제외한다.
- purge job은 매일 실행하며 만료 후 24시간 안에 운영 MongoDB의 candidate alias와 erasable user/event mapping을 삭제하고 Claim을 비식별 tombstone으로 전환한다.
- 24시간 SLA를 넘긴 항목은 운영 경보를 발생시키고 성공할 때까지 재시도한다. 삭제 증적에는 실행 시각·처리 건수·성공 여부와 저 cardinality 실패 분류만 남기며 candidate, keyVersion, userId와 source event를 남기지 않는다.
- MongoDB 전체 재해복구 backup은 최대 35일 rolling 보존 후 자동 만료한다. backup은 일반 애플리케이션 조회에 사용하지 않고 접근 제한된 복구 용도로만 사용한다.
- 과거 backup을 복구할 때는 격리 환경에서 현재 기준 만료 purge를 먼저 실행하고 검증한 후에만 사용자 트래픽을 연결한다.

## 2026-08-26 무료 Trial 내부 API·Mongo ADR-001 작성

- `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`를 기술 구현 기준으로 추가했다. 앱-facing Billing API는 만들지 않고 Identity event와 Learning Core reservation/group-status 내부 API만 `/internal/v1`에 둔다.
- Learning Core의 기존 `examId`를 opaque `sessionId`, 문제지 ID를 `mockExamId`, 필수 앱 UUID v4 `Idempotency-Key`를 `operationId`로 구분했다. reserve 전에 sessionId/mockExamId를 선할당하고 durable Session commit 뒤 같은 operation으로 confirm한다.
- Identity `PhoneEligibilityBindingVerified`/`Revoked` schema v1 wire는 재사용하되 과거 Bearer JWT transport는 사용하지 않고 승인된 C3-D Lattice/SigV4 route만 사용한다.
- candidate alias, 삭제 가능한 subject link와 immutable ledger core를 분리하고 active alias/reservation/group/session/command partial unique index를 고정했다. expired alias는 purge 전 reserve에서도 fencing할 수 있어 정확히 3년부터 재수급을 허용한다.
- Reservation audit에는 TTL을 두지 않고 5분 business expiry를 scheduler+Transaction으로 처리한다. inbox 120일과 terminal command 7일 같은 보조 기록만 `purgeAt` TTL을 사용할 수 있다.
- Mongo replica-set Transaction 경계를 eligibility event, reserve, confirm, cancel/expiry, group event와 TrialClaim purge로 나눴고 필수 동시성·unknown commit result test 목록을 정의했다.
- TrialClaim 3년 purge로 subject link가 제거된 AttemptGroup은 더 이상 replacement authorization에 사용하지 않는다. Learning Core의 기존 Session·결과는 삭제하지 않으며 익명 audit projection만 남길 수 있다.
- C3-D Lattice/SigV4 인프라 설계는 후속 ADR-002로 완료했다. 다음 코드 작업은 ADR-001의 Identity event consumer부터 vertical slice로 시작한다.

## 2026-08-26 ADR-001 확정 내용 설명

- ADR-001은 기존 제품 결정을 바꾼 문서가 아니라 서비스 간 요청 형식, 식별자 의미, 상태 전이와 Mongo 강제 장치를 구현 가능한 수준으로 고정한 기술 계약이다.
- 앱은 계속 Learning Core만 호출한다. Identity는 verified/revoked phone binding event를, Learning Core는 reserve/confirm/cancel/status와 최소 AttemptGroup 상태 event를 Billing 내부 `/internal/v1`로 보낸다.
- 앱 UUID v4 `Idempotency-Key`가 operationId이고 Learning Core 기존 examId가 sessionId다. mockExamId는 AttemptGroup 동안 고정하며 의도적인 restart는 새 operation/session을 쓰되 같은 consumption/group/mockExamId를 재사용한다.
- 최초 reserve는 current verified binding을 확인하고 필요한 TrialClaim·free grant·hold를 한 Transaction으로 만든다. Session commit 뒤 confirm에서 최종 consume와 group OPEN을 확정하고, commit 실패 cancel/5분 expiry는 hold만 복원하며 Claim은 유지한다.
- candidate alias, erasable subject link와 immutable audit core를 분리했다. partial unique index로 active Claim, Reservation, group, Session과 command를 하나로 강제하고, 3년 만료 시 alias/user 연결만 제거해 새 Claim을 허용한다.
- 실제 AWS Lattice ARN·role·security group inventory와 애플리케이션 구현은 아직 하지 않았다. 논리 설계는 ADR-002로 완료했고 실값 확인과 vertical slice가 남아 있다.

## 2026-08-26 Reservation confirm과 Summary 완료 시점 재확인

- `confirm`은 Summary 생성 완료 때가 아니라 Learning Core가 proposed `sessionId`의 ExamSession을 MongoDB에 durable commit한 직후, 시험 생성 성공 응답을 앱에 보내기 전에 호출한다.
- confirm은 5분 hold를 최종 consumption으로 전환하고 AttemptGroup을 `OPEN`으로 만드는 entitlement 단계다. 사용자가 문제를 풀고 제출·채점·Summary를 받는 과정은 그 뒤에 진행된다.
- 모든 필수 submit 접수 시 `GRADING`, 필수 feedback·유효 score·Summary가 조회 가능할 때 별도 `AttemptGroupStatusChanged(COMPLETED)` event를 보낸다. Summary 실패가 최종 확정되면 `RETAKE_AVAILABLE`이다.
- Summary까지 confirm을 미루면 5분 Reservation이 시험 중 만료되고 같은 무료권이 다른 Session에 재사용될 수 있으며, 장시간 RESERVED 상태가 되어 확정 계약을 위반한다.

## 2026-08-26 phone eligibility candidate 의미 재확인

- candidate는 raw phone을 Billing에 보내지 않고 같은 verified phone인지 비교하기 위해 Identity가 normalized phone, consumerScopeId와 Identity 전용 secret key로 만든 HMAC-SHA-256 Base64URL 값이다.
- 같은 scope와 keyVersion에서 같은 번호는 같은 candidate를 만들지만 scope 또는 keyVersion이 다르면 값이 달라진다. Billing은 key material을 받지 않고 candidate를 역산하거나 새로 만들 수 없다.
- Identity verified event는 key rotation 동안 여러 `{keyVersion, value}` candidate를 함께 보낼 수 있다. Billing은 하나라도 기존 active alias와 일치하면 같은 phone proof로 판정한다.
- candidate는 event 서명이나 인증수단이 아니다. 호출자 인증은 C3-D Lattice/SigV4가 담당하고 candidate는 중복 Claim 비교에만 사용한다.
- raw phone보다 안전하지만 여전히 pseudonymous data이므로 로그·metric에 넣지 않고 TrialClaim `claimedAt + 3년` 만료 purge 대상에 포함한다.

## 2026-08-26 ADR-002 작성 전 사용자 입력 구분

- 실제 AWS account/region, 환경별 VPC·subnet, ECS cluster/service, task execution/task role ARN과 security group ID는 정책 선택이 아니라 AWS read-only 조회로 채울 배포 사실이다.
- 사용자 결정이 필요한 핵심은 (1) dev/staging/prod별 Lattice service network 분리 여부, (2) 기존 IaC 도구와 source of truth, (3) 별도 Identity/Learning Core/Billing task role 신규 생성 허용, (4) 초기 custom domain 사용 여부, (5) AWS SDK v2 SigV4 signer 운영 의존성 허용이다.
- 권장안은 환경별 service network 분리, 기존 IaC 도구 재사용, 서비스별 task role 분리, 초기 Lattice 기본 DNS 사용, AWS SDK v2 signer를 outbound adapter 뒤에 격리하고 local/test에서는 fake adapter를 사용하는 것이다.
- Billing은 ALB 없이 Lattice target, Learning Core reservation/group-status route, Identity eligibility event route, operations repair route와 direct bypass 차단으로 이미 확정돼 다시 선택하지 않는다.
- ADR-002는 실제 ARN을 repository에 고정 문자열로 박지 않고 환경 설정/infra output 참조로 기록한다. credential과 account secret은 문서·로그에 남기지 않는다.

## 2026-08-26 VPC Lattice 환경 분리 비용 확인

- AWS 공식 VPC Lattice pricing page를 2026-08-26 확인했다. 과금 차원은 provisioned Lattice service 실행 시간, 서비스 처리 데이터 GB와 HTTP request/TCP connection이며 service network 자체는 별도 과금 차원으로 제시되지 않는다.
- 공식 페이지는 VPC association과 service network endpoint를 추가 비용 없이 사용할 수 있다고 명시한다. 따라서 service/network association 개수만 늘리는 것이 직접적인 시간당 비용 원인은 아니다.
- staging/prod에 Billing Lattice service를 각각 상시 배포하면 service network를 공유하든 분리하든 provisioned service는 두 개이므로 기본 service-hour 비용은 동일한 방향이다. staging service를 새로 상시 띄우는 비용은 발생하지만 이는 network 분리 비용이 아니라 두 번째 service 실행 비용이다.
- 미국 동부 공식 예시는 HTTP Lattice service 하나가 시간당 0.025 USD, 730시간 기준 월 18.25 USD이며 실제 서울 리전 단가는 별도 확인이 필요하다. 데이터 처리와 요청 초과분은 사용량에 따라 추가된다.
- 비용만 줄이려고 prod/staging service network를 공유하는 것은 권장하지 않는다. 비용 절감이 필요하면 staging Lattice service를 테스트 기간에만 provision하는 선택지가 직접적이지만 상시 E2E·장애 재현 가능성이 낮아진다.
- 확인 출처: `https://aws.amazon.com/vpc/lattice/pricing/`

## 2026-08-26 staging Lattice 운영 방식 보완

- staging service network/service/listener/target group/IAM policy/SG를 테스트마다 생성·삭제하는 방식은 권장하지 않는다. 설정 drift, DNS/ARN 변경, IAM 전파 대기와 E2E 시작 지연이 생긴다.
- 권장 운영은 staging Lattice 인프라를 상시 유지하고 개발·통합 테스트가 잦은 기간에는 Billing ECS task도 상시 1개 운영하는 것이다. 이 구성이 가장 단순하고 production과 유사하다.
- 비용 절감이 필요하면 Lattice 리소스는 유지한 채 staging Billing ECS desired count를 업무 외 시간에 0으로 낮추고 테스트 전 1 이상으로 올린다. 이는 Fargate compute 비용은 줄이지만 provisioned Lattice service-hour 비용은 계속 발생한다.
- 서비스 자체를 제거·재생성하는 방식은 장기간 staging을 사용하지 않을 때만 IaC 자동화로 수행한다. 수동 Console 삭제·재생성은 권장하지 않는다.
- 현재 규모에서는 network 공유로 작은 비용을 아끼는 것보다 환경 격리와 상시 재현성을 우선해 `환경별 network + staging 인프라 상시 유지`를 기본 권장으로 둔다.

## 2026-08-26 production/staging ECS 운영 형태·이관 순서 확정

- Identity와 Learning Core workflow는 모두 `tosunsaeng-staging-cluster`로 배포하고 `identity-staging.to-teacher.com`, `api-staging.to-teacher.com`으로 health check하지만, 현재는 이 cluster가 실제 운영 트래픽을 처리한다.
- 같은 AWS account·VPC에 새 production ECS cluster를 만들고 production용 Identity·Learning Core service, 환경 설정·Secret·task role·SG·Lattice network/policy·database를 분리해 구성한다.
- 새 production Identity·Learning Core의 health·smoke·데이터 연결·롤백을 검증한 뒤 ALB/DNS 또는 현재 진입점의 운영 트래픽을 새 cluster로 전환한다. 전환 안정화 전에 기존 service를 제거하지 않는다.
- 전환·관찰·롤백 window를 통과한 후 기존 `tosunsaeng-staging-cluster`를 staging으로 확정하고 staging용 Identity·Learning Core·Billing과 별도 Lattice network, role/SG/config/secret·Mongo database를 둔다.
- staging Lattice service/listener/target/IAM/SG는 유지하고 평소 staging ECS service `desiredCount=0`, 테스트 전 필요한 Identity·Learning Core·Billing task를 각각 1 이상으로 scale out한다.
- 모든 target health와 phone event/reservation route smoke test가 성공한 뒤 E2E를 실행하고, 종료 후 staging task를 다시 0으로 낮출 수 있다. 자동화는 start→healthy wait→test→stop 순서이며 테스트 실패 시에도 stop 여부와 로그 보존 정책을 명시해야 한다.
- ECS cluster만 둘로 나누고 같은 production DB, secret, task role 또는 Lattice network를 공유하면 충분한 환경 격리가 아니다. 최소한 데이터베이스·credential·IAM principal과 network policy는 분리해야 한다.
- task를 0으로 내려도 provisioned Lattice service와 staging Mongo/backup 등 다른 managed resource 비용은 남을 수 있다.
- 이 턴에서는 AWS resource를 생성·수정·배포하지 않았다. 클러스터 확장은 구현 후 production 배포 전 인프라 작업으로 남아 있다.

## 2026-08-26 ADR-002 입력 승인·현행 workflow 확인

- 환경별 Lattice service network 분리, `ap-northeast-2`, 같은 AWS account·VPC, 서비스별 application task role 생성, Lattice 기본 DNS와 AWS SDK v2 signer를 확정했다.
- Identity와 Learning Core workflow는 GitHub OIDC deploy role로 AWS credential을 받고 ECR에 image를 push한 뒤, 현재 ECS Service의 Task Definition을 download/render/register/deploy한다.
- 이 workflow는 이미 있는 ECS resource의 application revision을 갱신할 뿐 cluster, Lattice, IAM, SG를 생성하지 않는다. 저장소에 Terraform, CDK, CloudFormation은 확인되지 않았다.
- application task role은 GitHub deploy role과 ECS task execution role과 분리해야 한다. 실제 Identity/Learning Core task role ARN은 AWS `describe-task-definition` 읽기 전에는 확정할 수 없다.
- 최초 인프라는 AWS Console에서 수동 생성했고 이후 application 배포만 GitHub Actions로 갱신했다. 새 production 인프라에 수동 방식을 계속 쓸지 IaC를 도입할지는 ADR-002의 남은 운영 선택이다.

## 2026-08-27 AGENTS·서비스 간 계약 정합성 리뷰

- Billing `AGENTS.md`와 통합 계약의 제품 범위, eligibility route, SigV4 목표, strict decode/digest, event 수신과 무료권 지급 분리, Reservation 순서, AttemptGroup 완료 조건, TrialClaim 3년·purge·backup 정책은 승인 내용과 대체로 일치한다.
- 구현 전 차단 이슈로 ADR-001 inbox schema/index에 `consumerScopeId`와 `ux_inbox_identity_scope_user_revision`이 아직 없고, ADR은 persisted disposition에 `DUPLICATE`·`REJECTED`를 열거하지만 PLAN-001은 APPLIED·STALE만 저장한다. ADR이 최종 기술 기준이므로 Phase 0에서 하나로 보정해야 한다.
- 실제 Identity publisher는 구현돼 있지만 현재 adapter는 Bearer workload JWT를 발급받아 전송하고 Identity ADR도 구 route를 적고 있다. 목표 계약의 VPC Lattice ECS task role·SigV4와 `/internal/v1/eligibility/trial/events`로 staging 연동 전에 별도 Identity 변경이 필요하다.
- Identity publisher는 HTTP status만 받고 `Retry-After`와 error code를 읽지 않는다. 429/503의 `Retry-After` 계약을 지킬 수 없고, eligibility route가 409 `COMMAND_PROCESSING`을 반환하면 이를 retryable processing이 아니라 conflict와 같은 dead-letter로 처리한다. PLAN-001처럼 eligibility 409를 event conflict로만 제한할지 producer를 확장할지 계약을 명확히 해야 한다.
- 앱→Learning Core 시험 생성 operation ID의 정확한 wire source가 통합 계약에서 충분히 명시되지 않았다. 제품 결정은 공개 시험 생성 요청의 필수 `Idempotency-Key`지만 현재 Learning Core controller에는 이 header가 없다. Reservation saga 연결 전 Learning Core 후속 변경과 contract test가 필요하다.
- 앱 종료 뒤 기존 시험을 이어풀지 않고 새 key·새 examId를 사용하는 승인 불변식은 CONTRACT_DECISIONS에는 있으나 AGENTS 핵심 불변식과 통합 계약 흐름에는 명시가 약하다. 실제 Learning Core `startNew`는 기존 진행 Session을 abandon하고 새 Session을 만들어 현재 동작은 맞지만 후속 계약 문서 보강이 필요하다.
- 리뷰 요청은 수정 승인이 아니므로 계약·ADR·AGENTS와 다른 서버 코드는 변경하지 않았다. PLAN-001 시작 전 Billing ADR Phase 0 보정, staging 연동 전 Identity transport/retry 변경, Reservation 연동 전 Learning Core public idempotency 계약 보강 순서가 적절하다.
