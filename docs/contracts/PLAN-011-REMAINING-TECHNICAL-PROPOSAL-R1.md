# PLAN-011 남은 기술안 R1 — MEMBER·삭제·phone·보존

> 2026-10-14 최신 사용자 승인: [단순화 결정](PLAN-011-SIMPLIFICATION-DECISION-2026-10-14.md). 재가입 유료 우선·원 소비당 target 복원1회 유지, 생성 중 삭제 미접수·앱 MEMBER 보강·120일 멱등 보장/종료 상세 기록 정리 채택. 이후 과거 key 신규 처리 위험 수용, 금융/소비/미완료 증거 보존은 유지. 아래 대기열 삭제/영구 key 보장 전제는 이 범위에서 대체하며 구현·공동 exact는 후속이다.

> LC·Identity 검토 후속: [회신 반영 R2](PLAN-011-REMAINING-TECHNICAL-REVIEW-RESPONSE-R2.md)를 함께 읽는다. 특히120일/새 issuedAt만으로 과거 UUID 재입력을 막을 수 없으므로 최소 tombstone 자동 폐기는 미승인이다. R1의 후보를 최종 계약으로 사용하지 않는다.

- 날짜: 2026-10-14 / revision: plan-011-remaining-technical-r1-draft-1
- 상태: 사용자 요청으로 작성한 권장안. 아래 신규 field/route·보존기간·phone 조합은 공동 검토 후보이며 구현/배포 승인이 아니다.
- 관련: PLAN-011/012/013, TMI-116/TMI-128/TMI-122, TMI-125.
- 정책 기준: [승인 결정](PLAN-011-J4-S15-V07-DECISIONS-2026-10-14.md). 기존 R2/fixtures-2는 검증 원본으로 보존한다.

## 1. 5줄 결론

1. MEMBER 증거는 기존 purchase-account의 검증된 Identity JWT에서 정규화해 저장하고 worker가 이를 읽도록 제안한다.
2. 삭제는 LC 기존 삭제 operation/worker에 Billing 전달 단계를 연결하고, 명시 삭제 사실과 Billing 원 그룹 차단을 멱등 처리한다.
3. phone 재가입은 새 계정의 유효 paid 우선, 없으면 기존 허용 무료 continuation을 선택한다. 원 계정의 paid/복원권 자동 이전은 없다.
4. 종료된 상세 복구 receipt는120일, 최소 중복 차단 결과는 관계 권리/분쟁 근거의 종료까지 보존하는 표를 제안한다. 진행 중은 자동 폐기하지 않는다.
5. 최초 증거·새 삭제 인증 route·phone INITIAL 예외·폐기 후 재유입 차단은 LC/Identity 공동 확인과 새 fixture가 필요하다.

## 2. 사용자가 반드시 읽어야 하는 내용

정책은 이미 승인된 범위를 유지한다. 여기의 기간 숫자·오류명·이벤트는 새 기술 제안이다. 실시간 탈퇴 일치나 모든 과거 key의 영구 replay를 보장하지 않는다. 삭제의 재응시 포기와 단순 앱 이탈을 구별하고, 명시 삭제 전 이미 확정된 복원권을 회수하지 않는다.

## 3. 권장안과 공동 확인

### 3.1 M1 — 기존 purchase-account에서 MEMBER 증거 확보

현재 Billing은 POST /api/v1/payments/purchase-account에서 Identity 서명·issuer/audience·유효시간·scope를 검증한 JWT와 account_type=MEMBER를 검사한다. 이 성공 경계를 재사용한다. 새로운 매 시험 Identity 동기 조회나 전체 회원상태 이벤트 시스템은 추가하지 않는다.

제안 저장은 account/lifecycle와 같은 Transaction의 `memberEvidence`다. 최소 필드는 source=IDENTITY_USER_JWT, userId/environment, 검증된 issuer, accountType=MEMBER, tokenIssuedAt, verifiedAt, evidenceSchemaVersion=1이다. token 원문/서명/refresh token은 저장하지 않는다. 검증된 JWT에서 서버가 구성한 타입으로 application service에 전달하며 request body가 이 값을 주장하지 못한다. 신규 계정 생성과 기존 계정 성공 조회 모두 증거를 기록하고, WITHDRAWN deny가 선행했으면 거절한다. 증거 갱신이 lifecycle를 ACTIVE로 되살리지 않는다.

MEMBER→GUEST 전환이 없다는 승인에 따라 token 만료 후에도 계정 유형 증거는 유지한다. tokenIssuedAt은 현재 ACTIVE 증명이 아니다. worker는 증거와 현재 로컬 lifecycle deny를 함께 검사한다. 다른 issuer/environment/userId의 증거는 재사용하지 않는다.

초기 복원권은 paid confirm을 통해 생기므로 정상 신규 구매 흐름에서는 purchase-account 증거가 먼저 존재한다. 기존 구매자에게 증거가 없으면 앱이 기존 purchase-account를 새 유효 Identity token으로 한 번 호출해 보강하도록 제안한다. 같은 계정/같은 purchaseAccountRefId를 유지한다. worker는 사용자 토큰을 얻거나 재사용하지 않고 증거 미확인으로 보류한다. 기존 구매/계정 존재만으로 backfill하지 않는다. 복원권 조회/사용마다 구매계정을 새로 생성하도록 강제하지 않는다. 초대 지급은 후속이며 구매 이력이 없는 MEMBER의 최초 증거 경로는 해당 기능 도입 시 별도 확인한다.

**Identity 확인:** 이 endpoint에 발급되는 billing:purchase와 account_type=MEMBER의 권위·환경별 issuer 및 탈퇴 발급 fence가 기존 계약대로인지 확인. 원 token의 새 claim은 요청하지 않는다. 앱은 로그인 후 구매/복원권 증거 부재 시 이 기존 endpoint로 보강할 수 있어야 한다. 미확인 상태의 내부 오류는 retryable pending으로 분류하되 무한 고속 재시도하지 않고 앱 보강/운영 관측으로 연결한다. exact 오류는 기존 paid admission pending과 함께 공동 고정한다.

**탈퇴 지연 운영 제안:** 기존 push/feed 복구를 유지하고 5분 이상 미처리/전파 지연은 경보, 1시간 초과는 운영 조치 대상으로 삼는다. 이 숫자는 신규 권장 운영 목표이며 기존 publisher 주기를 자동 변경하지 않는다. 지연만으로 모든 사용자의 시험을 일괄 차단하지 않고, 알려진 사용자 WITHDRAWN deny는 즉시 적용한다. feed 단절/coverage 불명은 기존 recovery fence 정책을 유지한다. 최종 수치는 기존 운영 SLO와 대조 후 채택한다.

### 3.2 D1 — 사용자 삭제 사실의 최소 전달

현재 LC는 DeletionOperation/worker·소유권 fence와 DeletedExamContinuationStore를 가진다. 새 마무리 작업 시스템 대신 기존 삭제 operation에 Billing 전달 상태를 붙이는 안이다. 답안/S3/Redis 삭제 방식은 이 제안에서 바꾸지 않는다.

**최소 신규 route 후보:** POST /internal/v1/attempt-groups/deletions/events (LC→Billing). 기존 상태 이벤트 v1의 enum/body/204 의미는 유지한다. 이 route는 현재 구현돼 있지 않으며 LC workload 인증 allowlist에 대한 별도 개정·양측 테스트가 필요하다. 기존 A 여섯 POST가 이미 허용한다고 간주하지 않는다. Reservation 목적 audience 재사용을 제안하되 exact route/method 인가 범위 추가는 A 보완 revision으로 명시한다. 기본 OFF·전용 ingress·query 거절·최대16KiB·strict JSON·redirect 금지를 적용한다.

| body field 후보 | 의미 |
| --- | --- |
| eventId | lowercase UUID v4, 최초 생성 후 불변 |
| schemaVersion | 정수1 |
| userId | LC가 인증된 삭제 사용자와 owner fence로 확정 |
| deletionOperationId | LC 삭제 operation의 안정 식별자, 1~128자 opaque |
| attemptGroupId | 원 Billing group UUID |
| sessionId | 명시 삭제된 exact 원 Session, 1~128자 opaque |
| deletedAt | LC가 삭제 접근 차단을 commit한 UTC millisecond 시각 |

producer/환경/목적은 검증된 LC principal/endpoint로 고정한다. body 전문/사용자 식별자를 로그에 쓰지 않는다. canonical 순서는 표 순서, 나머지 escaping 규칙은 R2와 같고 신규 golden bytes를 만든다.

Billing은 inbox eventId/digest와 **동일 group/session의 최초 삭제 사실**을 같은 Transaction으로 저장한다. 같은 eventId/digest는204, 같은 eventId 다른 내용은409 EVENT_ID_CONFLICT. 다른 eventId라도 동일 group/session/deletionOperationId/deletedAt이면 의미 중복204, 같은 group/session에 다른 삭제 사실을 주장하면409 DELETION_FACT_CONFLICT 제안. 여러 Session 삭제는 서로 별개 사실로 기록하고 과거 Session 삭제가 최신 Session을 지우지 않는다.

원 Session의 저장 owner와 현재 owner/fence를 검증한다. 소유권 이전 후 지연 삭제가 도착하면 current userId만으로 target 권리를 취소하지 않고 기존 source owner fence의 허용 범위를 공동 확인한다. 귀속이 불명하면 성공 ACK 없이 owner-blocked/검토로 분류한다. 이 부분은 phone 전환 및 기존 lifecycle와 함께 검증해야 한다.

삭제 적용은 원 Session의 신규 사용/재응시 자격을 차단하고, 삭제된 Session이 현재 재응시 기준인 경우 원 group replacement를 차단한다. 다른 최신 Session이 이미 확정돼 있으면 그 권리는 종료하지 않는다. 원 trial consumption을 새 Claim으로 열거나 무료권을 지급하지 않는다. userId의 모든 guard를 삭제하지 않는다. 다른 구매·무료권·기지급 복원권 보존.

**LC 순서:** 삭제 요청을 받으면 원 대상 inventory/쓰기 차단과 최초 전달 payload를 기존 local Transaction에 고정 → 기존 worker가 같은 payload 전송 → Billing commit204 확인 → 삭제의 Billing 전달 완료 표시. 실제 개인정보 삭제를 Billing 장기 장애 때문에 무한 연기하지 않도록 최소 전달 증거와 원문 자료 삭제를 분리한다. Billing ACK가 없으면 관련 새 시험 생성은 pending으로 유지하고 무료 재응시로 우회하지 않는다. ACK 전인 관련 삭제 operation을 완료로 표시하지 않으며 관측/재처리 경로를 둔다.

legacy로 Billing에 연결되지 않은 시험은 가짜 group를 만들지 않고 Billing 전달 불필요 상태를 로컬 증거로 기록한다. Session 부재만으로 이를 판단하지 않는다.

### 3.3 D2/D3 — 삭제·confirm 순서 및 다음 시험

단순화를 위해 **같은 사용자의 활성 시험 생성 operation과 삭제 preparation은 LC 공통 ownership fence로 순서화**하는 안을 권장한다. 생성이 진행 중이면 삭제는 요청을 보존하고 안전한 경계까지 기다린다. 삭제 preparation이 먼저면 해당 원 시험을 사용하는 새 생성은 Billing 삭제 ACK까지 대기한다. LC HTTP와 worker 모두 같은 fence를 쓰고 old worker drain 후 활성화한다.

| 순서 | 결과 |
| --- | --- |
| confirm·복원 commit이 먼저 | 기존 성공/기지급 복원권 보존. 뒤 삭제는 원 exact Session만 처리하고 새 시험/다른 권리를 건드리지 않음 |
| 삭제 적용이 먼저 | 원 시험을 복원 자격으로 사용하지 않음. 원 consumption 재사용 금지, 새 시험은 신규 권리 INITIAL |
| confirm 응답 유실/commit 불명 | 삭제가 성공 confirm을 되돌리지 않음. 기존 operation status-first로 결과 확정 후 순서화 |
| 오래된 worker로 동시 Billing 쓰기 | 원 group/session version과 공통 guard 쓰기로 commit 순서 수렴. 검증되지 않은 rollout 상태는 활성화 금지 |

삭제 후 새로운 INITIAL에서도 **원 group/session을 가리키는 restartContext는 유지**하는 최소안을 제안한다. startReason은 삭제 전 보존한 실제 접수/완료 증거에 따른 기존 세 enum을 사용하고 삭제 자체를 INCOMPLETE로 위조하지 않는다. Billing은 저장한 삭제 사실을 확인해 REPLACEMENT/복원 대상에서 제외하고 새 권리를 선택한다. 기존 RESTART_INCOMPLETE이면 같은 mock 선택이 가능하지만 새 권리 INITIAL이며 무료 재사용이 아니다. 접수 증거 없는 기존 삭제 row는 자동 추정하지 않고 이관/지원 여부를 별도 판정한다.

이 안은 구 삭제 continuation의 무료권 재사용·claim/transfer 복구를 제거하면서 **전환 대상 식별과 문제 선택에 필요한 최소 삭제 증거**만 유지한다. 원문 답안·결과는 복구하지 않는다. 추가 startReason/DTO는 우선 만들지 않고 이 조합이 충분한지 LC가 확인한다. 새 INITIAL 후 공개 성공·구 guard 보호는 기존 R2를 유지한다.

### 3.4 P1 — 재가입 phone과 paid 동시 선택

권장 우선순위는 **재가입 target 계정의 ACTIVE paid → 유효한 기존 phone 무료 continuation → 그 외 기존 신규 무료 판정**이다. paid 존재가 불명하면 무료로 fallback하지 않는다. source 계정의 구매/복원권을 target으로 이전하지 않는다. 기존 continuation discovery/exact echo·owner epoch 검증은 유지하고 선택을 reserve 시 Transaction으로 확정한다.

기존 phone DTO의 continuationReason/id/expectedAttemptGroupId를 유지하는 최소안을 제안한다. LC는 discovery의 기존 mock를 전달한다. Billing에 유효 paid가 있으면 동일 mock의 새 INITIAL/SUBSCRIPTION, 없으면 허용된 원 group REPLACEMENT/EXAM_PASS다. phone이 일반 restartContext와 함께 오는 요청은 계속 거절한다.

phone INITIAL은 LC target이 원 source Session을 소유하지 않으므로 일반 transition object로 source 시험 종료를 지시하지 않는 **별도 허용 조합**을 제안한다. Billing은 내부적으로 정확한 이전 group/owner 전환을 보존하며 원 source 학습자료를 target으로 이전하지 않는다. 이 조합은 R2 일반 context INITIAL과 다르므로 새 계약/fixture가 필요하다.

재가입 자체로 복원권을 지급하지 않는다. phone 경유의 최초 trial→paid 복원 적용 여부는 삭제 정책과 별개이며, 일반 승인된 최초 trial 자격·exact owner 이전 근거가 성립할 때만 동일 once-per-consumption 규칙을 적용하는 안이다. 새 Claim 지급이나 source의 보상권 자동 이전은 없다. LC/Identity는 이 자격 평가가 기존 rebind 범위를 확대하는지 확인한다. GRADING 재가입 pending·COMPLETED rebind NOOP는 기존 계약 유지, 이 우선순위로 우회하지 않는다.

## 4. V07 — 자료별 보존기간 권장안

아래 숫자는 기술 검토 후보다. 기존 승인된 금융5년/TrialClaim3년/backup35일을 변경하지 않는다. 관계 근거가 사라지기 전에 늦은 신규 처리 차단을 설치한다.

| 자료 | 제안 기간·기산점 | 폐기 전 조건 |
| --- | --- | --- |
| 미완료 operation/필수 복구 receipt/guard | 종료까지 TTL 없음 | 24시간 초과 정체 경보·담당 조사, 오래됐다는 이유만으로 성공/실패 확정 금지 |
| terminal 상세 receipt | 최초 terminalAt부터120일, replay로 연장 안 함 | 실행 가능한 retry/worker가 없고 최소 결과/tombstone 보존 완료 |
| pre-reserve terminal 실패 최소 tombstone | 최초 terminalAt부터120일 | 아래120일 이후 자동 재실행 금지 규칙이 적용됨 |
| 유료 성공·소비·복원 최소 결과/중복 근거 | 연결 금융 원장의 기존5년 만료 기준까지 | 운영 복구·분쟁/진행 요청 없음, 학습 원문 제외 |
| 무료 trial group/소비 최소 근거 | 기존 Claim 보존3년 기준을 기본으로, 진행/승인된 금융 복원 연계는 별도 필요한 최소 근거 유지 | Claim purge와 개인정보 연결 제거를 함께 검증. Claim 자체 보존을 임의 연장하지 않음 |
| 삭제/revoke 전달 inbox | 최초 수신부터120일, duplicate로 연장 안 함 | group/session 최소 차단 사실이 별도 유지됨 |
| group/session 최소 삭제·revoke 사실 | 연결 trial/금융 최소 근거와 동일한 종료 기준 | 미완료 보호 Session/operation 없음. 열린 보호 권한은 단순 TTL로 종료하지 않음 |
| legacy ID manifest | 해당 legacy 시험 존속 중 유지, 최종 종료/삭제 후120일 | 구 writer/백업 재유입 방지 검증. 복원본 purge·manifest 일관성 확인 |
| backup | 기존 최대35일 | 복원 후 삭제/만료 정책 적용 뒤 트래픽 연결 |

**보존 종료 후 처리 제안:**

- 기존 status에서 근거가 없으면 OPERATION_NOT_FOUND를 유지하되 미소비/취소 확정으로 해석하지 않는다. 과거 성공/오류를 추정 재현하지 않는다.
- LC 생성 operation의 자동 remote 실행/재전송은 원 생성시각으로부터120일 이내만 허용하는 안이다. 그 이후 미완료는 증거를 보존한 채 운영 검토로 넘긴다. 이는 시험 제출 마감이나 정상 Session 이용기한이 아니다.
- UUID key만으로 Billing은 요청 나이를 알 수 없다. 따라서 최초 실패 tombstone까지 폐기하려면 LC 모든 재전송 경로/백업에 동일 시간 fence가 적용됨을 검증해야 한다. 이를 보장할 수 없으면 tombstone 자동 폐기는 켜지 않고 별도 authenticated issuedAt 보완 등을 공동 검토한다. 현재 wire가 오래된 재요청을 자체 거절한다고 주장하지 않는다.
- 삭제/revoke sender도120일 초과 unresolved를 정상 자동 재발행하지 않고 증거 보존·운영 검토로 이동한다. 소비/차단 근거가 보존 중이면 승인된 같은 사실 재처리만 허용한다.
- 금융5년 종료 후 receipt 때문에 사용자 연결을 계속 보관하지 않는다. 상세 receipt와 최소 tombstone에 서로 다른 개인정보 제거 규칙을 적용하고 관계를 정리한다.

**추천 이유:**120일은 현재 전달 inbox와 운영 증거 창을 맞추는 초기안이다. 복구 증거 전체를120일에 지우거나 모든 학습정보를5년 보관하지 않는다. 기간 종료 후 안전성을 producer 제한만으로 충분히 보장할 수 있는지는 공동 검증 대상이다.

## 5. 위험·검증·담당

| 담당 | 확인 요청 |
| --- | --- |
| Identity | 기존 purchase-account용 JWT의 MEMBER 권위/발급 fence·동일 사용자 유형 안정성. 새로운 실시간 조회 계약 불필요 여부 |
| Billing | memberEvidence 원자 저장·탈퇴 부활 방지, deletion inbox/group 차단·공통 CAS, paid-first phone 조합·복원 자격, 보존/tombstone schema |
| LC | 기존 삭제 worker 단계/자료삭제 분리·새 인증 route caller·생성/삭제 공통 fence, 최소 context/mock 보존, phone INITIAL 응답 조합,120일 retry age fence |
| 공동 | 새 route/오류·digest·삭제 대상 owner fence 및 fixture, 실제 Transaction/unknown commit/순서 양방향, 폐기/복원 후 재유입 |

J3에서 이미 해소된 strict status/worker terminal 조건은 유지한다. 신규 삭제 전달/phone 조합과 retention은 기존 fixtures-2 통과 범위가 아니다. 새 revision에 다음 사례를 포함한다: MEMBER 중복/탈퇴 선행·증거없음 보강, 삭제/confirm 양순서·같은 사실 중복·다른내용 충돌·old Session 지연삭제, paid phone INITIAL/free REPLACEMENT,120일 직전/정각/후 retry와 tombstone 폐기·backup 복원.

새 route 하나는 Billing이 신뢰할 수 있는 삭제 사실을 받기 위해 제안한다. 기존 상태 v1을 조용히 확장하거나 Learning Core가 Billing DB를 직접 수정하는 방안은 사용하지 않는다. 삭제 실환경 기능이 이미 준비돼 있다고 보고하지 않는다.

## 6. 부록 — 읽기 근거와 작업 경계

- Billing [PaymentController](../../src/main/java/web/tosunsaeng/billing/domain/payment/api/PaymentController.java): MEMBER claim 검사, [public security](../../src/main/java/web/tosunsaeng/billing/global/config/security/PublicSecurityConfig.java): billing:purchase 인가.
- [PaymentAccountService](../../src/main/java/web/tosunsaeng/billing/domain/payment/application/PaymentAccountService.java): 기존 계정/lifecycle Transaction·WITHDRAWN deny·stable ref.
- [PhoneContinuationService](../../src/main/java/web/tosunsaeng/billing/domain/reservation/application/PhoneContinuationService.java): PHONE_REJOIN exact 연결·GRADING pending·OPEN/RETAKE_AVAILABLE 조건.
- LC 읽기: ExamSessionManager와 learningrecorddeletion/DeletedExamContinuationStore. 기존 store의 주석상 production wiring은 rollout gate 전 보류이며 운영 활성화 확인을 의미하지 않는다. retainAndDeleteSession은 삭제 stage/fence와 active 생성 operation 부재를 검사한다.
- 기존 [J4 결정](PLAN-011-J4-MEMBER-EVIDENCE-DECISION-2026-10-14.md), [통합 정책](PLAN-011-J4-S15-V07-DECISIONS-2026-10-14.md), [J3/J4](LC-BILLING-PAID-EXAM-J3-J4-ADDENDUM-R1.md).

이번은 읽기 조사·문서 제안이다. LC/Identity 파일·제품/DB/배포·원 R2/fixtures-2·Jira를 변경하지 않았고 외부 메시지를 전송하지 않았다. 링크/diff·원본 hash 검사만 수행하며 Gradle/실환경 테스트는 실행하지 않았다.
