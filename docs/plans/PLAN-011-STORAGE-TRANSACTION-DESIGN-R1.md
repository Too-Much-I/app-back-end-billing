# PLAN-011 저장 구조·Transaction 설계 R1

- 날짜: 2026-10-14 / revision: plan-011-storage-transaction-r1
- 상태: Billing 구현 설계 제안. 사용자 요청으로 작성했으며 schema 설치·제품 구현·최종 공동 수락은 수행하지 않았다.
- 기준: [PLAN-011](PLAN-011-paid-reservation-and-usage-evidence.md), [R2 draft-2](../contracts/LC-BILLING-PAID-EXAM-CONTRACT-R2.md), [공동 구현 합의안](../contracts/LC-BILLING-PAID-EXAM-R2-IMPLEMENTATION-AGREEMENT-R1.md).
- 관련: PLAN-011/012/013, TMI-116/TMI-128/TMI-122, TMI-125. 아래 신규 저장 field/collection 명칭은 내부 설계 제안이며 wire 추가가 아니다.

## 1. 5줄 결론

1. 기존 reservations/attempt_groups/attempt_sessions/grant/ledger를 확장하고 별도 시험 시스템을 만들지 않는다.
2. 사용자 guard와 TTL 독립 command receipt 두 저장소를 제안한다. 복원 중복 방지는 기존 grant source의 unique 제약을 재사용한다.
3. confirm·최초 trial 복원·원 그룹 replacement 차단·새 그룹 귀속·영속 결과를 하나의 Mongo Transaction에서 확정한다.
4. 현재 무료/결제의 Transaction 실행 방식이 다르므로 단일 ClientSession을 공유하는 adapter가 선행 조건이다.
5. LC 마무리는 LC 로컬 Transaction이다. 분산 Transaction/새 마무리 API 없이 기존 operation과 status로 복구하며 S15/V07 세부는 공동 확인한다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 현재 코드에서 확인한 차이

| 현재 구현 | 011에서 필요한 변경 |
| --- | --- |
| Reservation에는 subjectRefId·kind·상태·version·owner epoch가 있지만 source/transition은 없음 | source와 요청/응답의 immutable 전환 근거 추가 |
| AttemptGroup은 trialClaimId/consumption 연결과 openGuard 중심 | 유료/복원권을 가짜 TrialClaim으로 표현하지 않는 source별 필수값 검증 |
| IdempotencyCommand는 purgeAt와 응답 snapshot을 보유하고 status가 reserve command에 의존 | TTL 이후 성공/실패/status 복구용 영속 receipt 추가 |
| ReserveService는 기존 VERIFIED trial 경로를 먼저 통과하고 replacement reserve 때 active Session을 abandon | paid-first 선택 분리. 신규 전환에서 기존 시험을 reserve만으로 종료하지 않음. 기존 replacement도 영향 분석/회귀 검증 |
| Reservation은 TransactionTemplate, PaymentStore는 직접 생성한 ClientSession | 같은 DB라는 사실만으로 원자적이지 않음. 동일 session을 모든 참여 repository에 전달 |
| payment_ledger type validator는 구매 관측·확인·금액 사실 중심 | 시험 이용 증거를 무심코 기존 validator 밖 type으로 쓰지 않도록 schema 확장 명시 |

위는 읽기 확인이며 코드가 이미 R2를 지원한다는 뜻이 아니다. 실제 변경 전에 기존 schema manifest/인덱스와 테스트를 기준으로 아래 제안을 확정한다.

### 2.2 원자성 경계

Billing confirm 성공과 LC 시험 공개 성공은 다른 시점이다. Billing은 자신의 DB 안에서 권리 확정과 복원을 완료한다. LC는 성공 증거를 확인한 뒤 이전 신규 제출 차단·새 Session 활성·operation 성공을 함께 저장한다. Billing에서 LC HTTP를 호출한 상태로 DB Transaction을 유지하지 않는다.

정상 만료 전에 reserve한 권리는 기존 5분 안 confirm을 허용하는 D1을 유지한다. refund 선행은 별도 revoke 거절이다. 확정된 Session 완료에 새 제출 마감은 만들지 않는다.

## 3. 결정·확인할 사항

- 이 문서의 저장 구조와 단일 session adapter를 Billing 구현 기준으로 검토·수락한다. 실제 validator/index 코드와 설치는 구현 단계다.
- 공동 확인은 wire를 재설계하는 대신 원 대상/접수 증거, LC 마무리, replay/보상과 S15/V07로 제한한다.
- S15 phone+paid·삭제 continuation 정상 처리 및 V07 receipt/tombstone/purge exact는 미확정이다. 그 구조에 의존하는 writer와 운영 삭제는 확정 전 활성화하지 않는다.
- 기존 A/paid fixture bytes는 그대로 둔다. 추가 행동 기대값이 필요하면 별도 revision/추가 테스트로 검증한다.

## 4. 주요 위험과 미확인 사항

1. 단일 Transaction 안에서 raw Mongo 쓰기만 session에 참여하고 Spring repository 쓰기는 별도 commit되는 실수를 막아야 한다.
2. 기존 subject별 unique guard는 새로운 paid/free 사용자 guard로 자동 대체되지 않는다. 각 guard의 해제 대상·시점을 함께 검증한다.
3. LC 마무리 여부는 기존 wire로 Billing이 알 수 없다. Billing receipt에 LC 완료를 추정 저장하거나 신규 ack API를 발명하지 않는다.
4. 최종 실패 receipt를 저장한 뒤 예외를 던져 Transaction을 rollback하면 재현 계약이 깨진다. 결과를 commit한 후 HTTP 오류로 변환한다.
5. source별 owner 검증이 달라진다. MEMBER 보상권/paid를 phone subject로 위장하거나 기존 trial owner 이전 규칙을 확장하지 않는다.
6. 보존 상세가 미정인 receipt에 임의 TTL을 설치하지 않는다. 이는 무기한 개인정보 보존 승인이 아니며 활성화 전 V07에서 기한·삭제 후 응답·재유입 처리까지 정한다.

## 5. 저장 구조와 Transaction 설계

### 5.1 기존 저장소 확장

| 저장소 | 제안 변경/의미 | 검증 조건 |
| --- | --- | --- |
| reservations | environment, owner snapshot, entitlementSource, sourceRef, restartContext, transition, confirmRejection 추가. 기존 operation/kind/status/terminalAt/version 유지 | reserve 성공 source 필수. restartContext는 immutable. transition은 context 있는 INITIAL에만 필수 |
| attempt_groups | immutable entitlementSource/sourceRef, replacementBlocked 및 차단 원인·시각, 원 전환 operation 근거 | 학습 status와 재응시 차단 분리. 늦은 완료로 차단 해제 금지 |
| attempt_sessions | group/source 연결·owner snapshot을 보존, 기존 proposed/confirmed/terminal/version 유지 | LC Session과 혼동하지 않음. 이전 완료와 신규 귀속은 별도 |
| entitlement_grants | MEMBER 귀속 보상권 source 지원 및 지급 순서 필드 | trial source/Claim 유일성 유지. 복원 sourceId는 원 consumption ID. 총량=available+held+consumed 불변 |
| reservation_allocations | EXAM_PASS INITIAL에 기존 hold/consume/release 유지 | SUBSCRIPTION에는 가짜 unit allocation/consumption 생성 금지 |
| entitlement_ledger | 복원 grant 지급·무료권 hold/consume/release를 최소 증거로 기록 | 원 TrialClaim/consumption 불변. grant 단위 sequence·중복 제약 유지 |
| payment_ledger | 유료 reserve/confirm/cancel/expire 및 상태 이용 증거 type/필수 detail을 명시 확장 | effectKey unique, purchase/entitlement exact 연결, 개인정보/답안 원문 없음 |
| idempotency_commands | 기존 단기 실행/응답 캐시 유지, 새 응답 field 대응 | 유일한 복구 근거로 사용 금지. 영속 receipt 검증 후만 기존 terminal TTL 허용 |

sourceRef는 내부 tagged object 제안이다. SUBSCRIPTION은 accountId/purchaseId/entitlementId와 선택 당시 기간·선택시각을, EXAM_PASS는 grantId와 최초 소비 이후 consumptionLedgerEventId를 가진다. trialClaimId는 최초 trial일 때만 존재한다. INITIAL reserve의 소비 ID는 아직 없으므로 confirm에서 한 번 결속하고 이후 바꾸지 않는다. 구매 timeline reflow 뒤에도 source 구매를 다른 구매로 치환하지 않는다.

owner snapshot은 caller가 주장한 userId만이 아니라 검증한 owner 종류·owner reference·epoch/version을 보존한다. 최초 trial은 기존 subject link/epoch, paid와 MEMBER 복원권은 계정 귀속/활성 lifecycle 근거를 사용한다. 새 복원권에 가짜 phone binding을 만들지 않는다. 기존 subjectRefId 필수 validator·repository 조건의 변경은 source별 조건부 validator로 명시하며 기존 trial reader를 보존한다.

무료권은 현재 무기한이다. 지급 순서는 owner별 grantOrder를 guard의 단조 증가 카운터에서 배정하는 안으로 고정하고 기존 권의 순서는 생성시각/ID로 안정화해 이관 검증한다. 신규 발급 동률을 ID 무작위 순서로 결정하지 않는다. trial 미발급/보상권 동시 보유의 선택 상세와 기존 번호 재검증 정책은 실제 resolver 표에서 검증하며 조회로 발급하지 않는다.

### 5.2 신규 사용자 guard — exam_owner_guards (제안)

| field | 의미 |
| --- | --- |
| environment/userId | 환경·사용자별 unique. userId는 인증된 내부 caller가 전달한 owner 검증 후 사용 |
| currentGroupId/currentSessionId | 마지막 확정 귀속, 없으면 null |
| pendingOperationId/pendingReservationId/pendingGroupId | 예약 중 신규 요청의 자리. 아직 current를 종료하지 않음 |
| version/nextGrantOrder | CAS와 무료권 지급 순서 |
| ownerFence | 활성 계정/기존 owner 귀속과 연계한 검증 근거 |

행 자체는 삭제/재생성으로 version을 초기화하지 않고 비어 있는 자리도 CAS로 유지하는 안이다. 개인정보 폐기는 V07/owner lifecycle과 함께 결정한다. TTL 없음. reserve는 pending을 확보하고 confirm은 exact pending을 current로 바꾸며 cancel/expiry는 exact pending만 비운다. REPLACEMENT 취소는 기존 current를 유지한다. 원 그룹 완료는 currentGroupId가 원 그룹이고 pending이 없을 때만 현재 자리를 해제한다. pending이 있으면 그 예약을 보존하고 confirm/cancel 경로에서 원 그룹 terminal 상태를 다시 확인한다.

Billing pending은 예약 terminal에서 끝나지만 LC 미완료 operation guard는 LC 마무리까지 남는다. 새 key로 이 간격을 우회할 수 없도록 LC의 모든 생성 경로가 그 guard를 확인해야 한다. 기존 subject openGuard/activeGuard는 선택 대상 제한을 유지하되 새 권리 전환 commit에서 원 것만 해제한다. 완료 허용을 위해 원 group를 COMPLETED로 위조하지 않는다.

### 5.3 신규 영속 receipt — reservation_operation_receipts (제안)

unique key는 environment/callerService/userId/operationId/commandType이다. commandType은 기존 RESERVE/CONFIRM/CANCEL을 구분한다. schemaVersion, canonical payloadHash, 검증한 owner 근거, reservationId(nullable), 결과 종류, 최초 응답 snapshot, 오류 code, createdAt/terminalAt를 보존한다. 운영 중 raw request 전문 대신 필요한 식별자와 digest를 저장한다.

- RESERVE 성공은 최초 reserve 응답을 replay한다. status는 이 receipt의 reservationId로 현재 Reservation terminal 상태/source/transition을 읽는다. status 자체가 receipt/원장을 생성하지 않는다.
- command cache 삭제 후에도 같은 canonical 요청은 영속 결과를 반환하고, 다른 요청은 IDEMPOTENCY_KEY_CONFLICT다. 소유권 상실은 replay로 우회하지 않는다.
- reservation 생성 전 terminal context 실패도 이 key에 FAILED_TERMINAL 결과로 저장한다. 예약이 없으면 예약 ID를 발명하지 않는다. status의 해당 실패 표현이 기존 R2/fixture에 충분히 고정돼 있는지는 공동 확인 항목 J3으로 남긴다.
- malformed/인증 실패/단순 일시 장애를 영구 terminal 실패로 고정하지 않는다. 동일 key 경합은 unique 제약으로 최초 commit에 수렴한다.
- Reservation을 먼저 purge하면 status가 깨지므로 receipt/Reservation 최소 결과의 보존·삭제를 함께 설계한다. 보존 종료 뒤 성공 재현을 무한 보장하지 않는다.

### 5.4 인덱스·validator 제안

| 대상 | 제약 |
| --- | --- |
| exam_owner_guards | unique(environment,userId), pending 조회용 partial index 필요 여부는 worker 조회 계획으로 결정 |
| reservation_operation_receipts | unique(environment,callerService,userId,operationId,commandType), reservationId 조회 index; TTL 없음 |
| 복원 grant | sourceType=TRIAL_PAID_RESTORATION인 문서에 unique(environment,sourceType,sourceId). sourceId=원 consumption ID |
| grant FIFO | owner별 grantOrder unique 및 state/available 조건 조회 index. trial 기존 source 유일성 유지 |
| reservations/group/session | 기존 ID·subject active partial unique 유지 검토, source별 필수 reference와 enum 조건 검증. null paid subject가 기존 unique에 걸리지 않도록 partial 조건을 명시 이관 |
| payment_ledger | 기존 effectKey unique 유지, effectKey를 reservation/command 또는 상태 event 의미 단위로 결정 |

인덱스 설치는 앱 기동 시 임의 drop/recreate하지 않는다. 기존 index manifest와 diff, 중복 inventory, reader-first validator 이관, bounded schema 설치/검증을 별도 실행한다. schema 버전 숫자는 현재 manifest를 기준으로 구현에서 배정한다.

### 5.5 단일 session 구현 경계

011의 결합 Transaction은 PaymentStore의 명시 ClientSession을 상위 경계로 삼는 안을 제안한다. session을 받는 reservation/grant/ledger/guard/receipt adapter를 제공하고 기존 MongoTemplate 접근은 동일 session에 바인딩해 사용한다. transaction 내부에서 기존 MongoTransactionExecutor.execute 또는 PaymentStore.transaction을 다시 열지 않는다. 기존 무료 경로의 wrapper는 유지할 수 있으나 같은 업무 단위의 중첩 session은 금지한다.

같은 ClientSession으로 payment lifecycle/recovery fence와 source 검증·Reservation·grant·receipt 모두 참여해야 한다. refund/reflow writer도 동일 purchase/account 경합 자원을 쓴다. 이 adapter가 불가능하면 코드 작성 전에 단일 session을 보장하는 대안을 재검토하며 단순 서비스 연속 호출로 대체하지 않는다.

실제 replica-set 테스트에서 각 쓰기 뒤 강제 실패를 주입해 모든 컬렉션이 rollback되는지 확인한다. ID/effectKey는 retry 바깥에서 고정한다. transient retry는 전체 Transaction을 재실행하되 unknown commit은 동일 commit 재확인 후 receipt를 읽고, 결과 불명은 503/기존 복구로 남긴다. external HTTP/provider 조회는 Transaction 밖에서 수행한다.

### 5.6 Transaction별 읽기/쓰기

| Transaction | 검증/경합 대상 | 원자 쓰기 | 하지 않는 일 |
| --- | --- | --- | --- |
| T1 reserve | owner fence, receipt/replay, current context, paid timeline 또는 무료 grant, 사용자 guard | pending 확보, 필요 trial 최초 지급/무료 hold, Reservation/PROPOSED Session, source/transition·reserve receipt/원장 | 원 Session 선종료·복원 지급·paid 차감 |
| T2 confirm | exact pending/Reservation version, owner, source/revoke, 원 group의 복원 자격 | CONFIRMED·무료 hold consume 또는 paid 이용 증거·새/기존 group 귀속·원 replacement 차단·자격 복원 grant/ledger·current 이전·성공 receipt | LC DB 쓰기·실제 채점·새 API ack |
| T3 irreversible 거절 | RESERVED와 revoke/owner 근거, 같은 source 경합 | CANCELED/confirmRejection/terminalAt·hold의 안전 정리·pending 정리·거절 receipt | revoked 권리 재활성·성공 confirm 취소 |
| T4 cancel/expiry | exact 예약 version/pending, terminal 최초 결과 | 무료 원 allocation release 또는 paid audit, 예약 terminal·receipt, exact pending 정리 | current 신규 group 무조건 해제·복원 지급 |
| T5 상태 event | 기존 v1 inbox/digest/owner fence·exact group/session | inbox·학습 상태·최소 이용 증거, 허용된 exact guard 해제 | 다른 group 해제·replacementBlocked 초기화 |
| T6 refund (012) | 동일 purchase/account 경합 쓰기, 이미 저장된 confirm 사실 | 권리 종료·금전 원장·reflow·group별 최초 revoke outbox와 보호 Session | 새 revoke 내용 재발행·다른 구매 종료 |

T1/T2와 T6는 선택한 purchase의 version을 실제 갱신하고 account timeline/lifecycle 경계를 공유한다. 조건 검사만 하고 쓰기를 생략하면 snapshot write-skew를 막지 못한다. 기간 중 paid가 전혀 없는 무료 선택도 account timeline의 공통 쓰기 경계로 신규 구매 적용과 순서화한다. 계정이 없는 Guest 무료 경로는 기존 trial owner fence를 유지한다. 각 writer의 session/공통 자원 참여를 테스트로 입증한다.

T2/T5는 원 group version으로 복원 자격 판단 순서를 고정한다. 늦은 완료가 복원 지급 뒤 도착하면 기존 지급 유지, 완료가 먼저 commit되면 그 상태에서 자격을 판정한다. 복원 grant source unique가 원 consumption별 중복을 막으며 별도 복원 root 계보는 만들지 않는다.

T3의 terminalAt을 유일 거절 시각으로 쓴다. 거절 DTO는 commit 후 반환한다. 이미 CONFIRMED이면 T3 대신 최초 성공을 replay하며 환불 후에도 바꾸지 않는다. CANCELED 일반 취소와 revoke 거절은 status에서 구분한다.

### 5.7 LC local Transaction과 복구

LC는 마지막 필수 답안 접수/operation 준비를 같은 version/fence로 순서화한다. 준비 시 사유·원 대상·문제를 고정하고, reserve 뒤 Session을 확인 대기로 저장한다. Billing 성공 확인 뒤 exact 이전 신규 제출 차단·새 Session 활성·operation SUCCEEDED를 한 로컬 Transaction으로 저장한다. 원 대상이 이미 완료/삭제됐으면 lifecycle 정책을 따르며 재생성하지 않는다.

Billing 성공/LC 실패는 같은 operation worker가 status-first로 복구한다. 새 key·새 무료권·repair-confirm으로 우회하지 않는다. 원 Session의 이미 접수된 작업과 Summary는 원 접수 증거로 계속 처리하며 새 Session guard를 건드리지 않는다. LC 저장 필드/접수 receipt는 LC 담당 설계이며 여기서 LC repository 이름을 확정하지 않는다.

## 6. 부록 — 검증·이관·근거

### 6.1 필수 실행 테스트

- 원자성: T2의 각 쓰기 사이 실패 주입, grant/ledger/group/receipt 어느 것도 부분 commit되지 않음.
- 순서: confirm/refund, confirm/원 완료, cancel/confirm, expiry/confirm 양 commit 순서.
- 중복: 두 confirm이 복원 1회로 수렴, duplicate key/unknown commit 후 같은 응답, TTL 뒤 성공·거절 replay.
- guard: 원 시험 늦은 완료와 새 pending/current 경합, reserve 실패/취소 시 이전 권리 보존, 두 paid/free reserve 경합.
- source: 모든 source/kind/transition 조합, 정상 기간 만료 D1, 기존 trial owner rebind/Guest merge 회귀, paid/referral 재복원 금지.
- session 경계: raw payment와 Spring adapter 쓰기 모두 같은 Mongo session에서 rollback됨.
- LC: S01~S14/S16 실제 상태 테스트, S15/V07은 미확정 정상 기대값 추가 금지. revoke V01~V06 및 실제 chain/TLS는 012와 공동 검증.

### 6.2 이관 및 실행 순서

P0 수락·공동 잔여 확인 → session adapter/schema 설계 테스트 → reader 선행 → 기존 writer drain 및 source/guard coverage 점검 → 승인된 schema 설치 → 기본 OFF writer → 양측 E2E → 별도 활성화 승인 순서다. 현재 production Billing DB가 없다는 사용자 확인과 LC inventory는 [기존 조사](PLAN-011-SOURCE-PRODUCTION-INVENTORY-2026-10-13.md)를 따른다. 실제 전환 직전 다시 확인하며 이번에 DB를 조회하지 않았다.

기존 LC 시험은 exact legacy 목록으로 보존한다. 출처/소비를 가짜로 채우지 않는다. 구 source writer로 rollback하면 증거를 잃을 수 있으므로 rollback은 신규 입장 OFF·drain·증거 보존을 우선하며 구버전 무조건 복귀를 허용하지 않는다.

### 6.3 읽기 근거

- [Reservation](../../src/main/java/web/tosunsaeng/billing/domain/reservation/domain/entity/Reservation.java), [IdempotencyCommand](../../src/main/java/web/tosunsaeng/billing/domain/reservation/domain/entity/IdempotencyCommand.java)
- [ReserveService](../../src/main/java/web/tosunsaeng/billing/domain/reservation/application/ReserveService.java), [LifecycleService](../../src/main/java/web/tosunsaeng/billing/domain/reservation/application/ReservationLifecycleService.java)
- [AttemptGroup](../../src/main/java/web/tosunsaeng/billing/domain/attempt/domain/entity/AttemptGroup.java), [AttemptSession](../../src/main/java/web/tosunsaeng/billing/domain/attempt/domain/entity/AttemptSession.java)
- [EntitlementGrant](../../src/main/java/web/tosunsaeng/billing/domain/entitlement/domain/entity/EntitlementGrant.java), [기존 index manifest](../../src/main/java/web/tosunsaeng/billing/global/infrastructure/mongodb/BillingMongoIndexInitializer.java)
- [PaymentStore](../../src/main/java/web/tosunsaeng/billing/domain/payment/repository/PaymentStore.java), [Spring executor](../../src/main/java/web/tosunsaeng/billing/global/infrastructure/mongodb/MongoTransactionExecutor.java)
- [PurchaseCore](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCore.java), [결제 schema](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCoreSchema.java)

이번 작업은 읽기 조사·설계 문서뿐이다. 실제 validator/index/Transaction을 구현하거나 Gradle/실환경 테스트를 실행하지 않았다.
