# TMI-213 — 유료 시험 Transaction·저장 기반

- 날짜: 2026-10-14 / 브랜치: feat/TMI-213-paid-exam-transaction-foundation
- Jira: [TMI-213](https://to-teacher.atlassian.net/browse/TMI-213), 상위 TMI-137
- 기준: [PLAN-011 §5.0](../plans/PLAN-011-paid-reservation-and-usage-evidence.md), [한정 수락](../contracts/LC-BILLING-PAID-EXAM-SIMPLIFIED-R1-ACCEPTANCE.md)

## 1. 5줄 결론

1. PaymentStore의 동일 ClientSession에 raw driver와 session-bound MongoOperations를 참여시키는 PaidExamTransactions를 추가했다.
2. source snapshot, 비종료 MEMBER receipt, 사용자 guard/CAS 및 복원 grant/원장의 저장 기반을 구현했다.
3. schema paid-exam-foundation-r1은 명시 maintenance 설치와 read-only 검증으로 분리한다. 자동 TTL/운영 설치는 없다.
4. 유료 입장/reserve/confirm 비즈니스 연결은 TMI-214/215이며 새 코드만으로 유료 입장이 활성화되지 않는다.
5. replica-set 실패 주입·중복·경합과 전체 회귀 결과는 아래 검증 절에서 구분한다. 기존 fixture28개 전체 실행 완료를 뜻하지 않는다.

## 2. 반드시 읽어야 하는 내용

### Transaction 사용

PaidExamTransactions.execute의 Context가 제공하는 reservations/allocations/grants/ledger/groups/sessions를 사용한다. 이 저장소들은 **같은 session-bound MongoOperations**로 만들어진다. 결제 repository에 넘기는 세션은 Context.session()이다. callback 밖으로 Context·repository·세션을 보관하지 않는다. 일반 Spring singleton repository를 callback 안에서 주입받아 호출하면 이 경계에 참여한다고 보장할 수 없다.

PaymentStore는 Spring transaction 내부의 새 explicit transaction과 explicit transaction 중첩을 거절한다. MongoTransactionExecutor 및 EntitlementSnapshotExecutor도 explicit 경계 내부 실행을 거절한다. raw driver 오류뿐 아니라 Spring DataAccessException으로 감싸진 Mongo transient/duplicate-key 오류를 기존2초/최대32회 정책에 따라 처리한다. unknown commit은 동일 session의 commit만 재시도하며 작업 callback을 재실행하지 않는다. 일반 업무 예외를 Mongo 장애로 바꾸지 않는다.

Context는 원자성 수단이지 인증·owner 검증기가 아니다. TMI-214/215 caller는 인증·현재 lifecycle/owner·payload/lease/version 검증과 공통 account/purchase 쓰기 fence를 함께 구현해야 한다. 외부 HTTP/provider 호출은 DB Transaction 안에 넣지 않는다.

### 설정·기동 조합

| foundation | admission | 동작 |
| --- | --- | --- |
| OFF(기본) | OFF(기본) | 새 schema 검증/설치 및 paid bean 없음. 기존 무료 동작 유지 |
| ON | OFF | 새 schema/index 정확 검증 후 PaidExamTransactions bean 제공. HTTP/worker resolver 미연결 |
| 무관 | ON | 기동 거절. TMI-214/215/216 및 공동 활성화 gate 전 허용하지 않음 |

- billing.reservation.paid-foundation-enabled / BILLING_PAID_EXAM_FOUNDATION_ENABLED
- billing.reservation.paid-admission-enabled / BILLING_PAID_ADMISSION_ENABLED

Foundation ON은 purchase-account, LC receiver 또는 다른 업무 flag를 자동으로 켜지 않는다. 운영 Secret/키/issuer·포트 변경은 없다. 실제 paid admission 활성화 의존성은 후속 runtime 구현 단계에서 확정한다.

## 3. 결정·후속 사항

새 제품 선택은 없다. 기존 승인 계약을 구현 기반으로 사용한다. 내부 env는 test/staging/production이고 payment의 SANDBOX/PRODUCTION enum과 다르며 임의 동일시하지 않는다. 환경 매핑과 현재 actor/MEMBER 증거의 업무 적용은 TMI-214/215다.

- TMI-214: MEMBER evidence writer, same-key 대기 재개·status/J3 terminal 관측과 실제 HTTP DTO.
- TMI-215: source resolver·hold/confirm/복원 자격·account/purchase fence·phone/refund 경합.
- TMI-216: LC 실제 연동/접수·마무리·공개 오류·상태 시나리오 대응표.
- TMI-217: 삭제 route/과거 owner·자료별 최종 purge. 이번 코드로 미확정 삭제 사실을 추정하지 않는다.

## 4. 위험·이관·rollback

1. 설치는 모든 관련 writer를 격리하고 중복 inventory와 기존 index/options를 확인한 maintenance에서만 PaidExamSchema.installUnderMaintenance(db, true)를 명시 호출한다. 앱 시작/build/test는 운영 DB에 이 호출을 하지 않는다. 이 함수에 넘기는 db는 운영자가 사전 확인해야 하며 자동 탐색하지 않는다.
2. 기존 entitlement_grants collection이 있어야 한다. 기존 grant/trial index와 validator는 교체하거나 drop하지 않는다. 신규 guard/receipt collection은 strict validator, simple collation으로 생성한다. 기존에 같은 이름의 collection이 있으면 validator/options가 exact 일치해야 한다. incompatible collection을 자동 수리하지 않는다.
3. 기존 grant의 sourceType/sourceId/benefitCode unique는 그대로 유지한다. 복원 source 환경별 unique 및 MEMBER FIFO partial unique를 추가한다. 기존 source unique가 더 강한 제약이면 완화하지 않는다. 환경을 섞어 공용 grant namespace를 쓰는 이관은 별도 분석 대상이다.
4. 신규 receipt/guard/grant에 TTL이 발견되면 검증 실패다. terminalAt+120일의 detailEligibleAt은 폐기 가능 시점의 증거이며 자동 삭제 실행 필드가 아니다. 미완료·소비·복원 unique·권한 차단 근거 폐기를 승인하지 않는다.
5. 현재 Reservation/group/Session reader에 optional immutable sourceSnapshot을 추가했다. 기존 기록의 null은 unknown으로 남기고 TRIAL로 추정하지 않는다. 새 source 필수 writer/full 기존 collection validator의 조건부 이관은 TMI-214/215 적용 전에 완료해야 한다. 현재 무료 생성은 그대로다.
6. 실제 paid writer가 새 증거를 쓴 뒤에는 구 writer가 새 필드를 잃거나 증거 없이 권리를 고르는 rollback을 금지한다. 현재 foundation OFF에서는 기존 코드를 유지하며 신규 schema/index도 임의 삭제하지 않는다.
7. 복원 factory는 MEMBER owner·grantOrder·original consumption을 저장하지만 실제 회원/복원 자격을 검증하지 않는다. 기존 trial factory로 위장하지 않으며 호출은 후속 confirm Transaction에만 연결한다.

## 5. 구현 상세

| 저장/모델 | 내용 |
| --- | --- |
| EntitlementSourceSnapshot | EXAM_PASS와 SUBSCRIPTION tagged reference·선택 시각. paid는 가짜 grant/claim/consumption 없음. 무료 소비 결속은 같은 ID에만 멱등 |
| Reservation/AttemptGroup/AttemptSession | sourceSnapshot optional reader·한번 설정 이후 다른 source 교체 금지 |
| reservation_operation_receipts | env/caller/user/operation/command unique, digest·owner·상태·source/응답 snapshot·version·waitingSince·terminalAt/detailEligibleAt. raw request/token 없음 |
| exam_owner_guards | env/user unique, current/pending exact group/session/operation/reservation·version·nextGrantOrder. acquire/release/confirm/complete CAS |
| entitlement_grants | TRIAL_PAID_RESTORATION sourceId=원 consumption, ownerUserId/environment/grantOrder, 무기한1unit. subject/Claim 위장 없음 |
| entitlement_ledger | 복원 GRANTED 이벤트, 환경·원 consumption의 dedupeKey, 원 grant aggregate 참조. 원 TrialClaim/소비 변경 없음 |

receipt storage primitives는 waiting insert/read와 waiting→reserved CAS를 제공한다. HTTP API·terminal 처리 workflow·보상 worker는 아직 연결하지 않는다. owner snapshot의 reference/version은 caller가 검증한 근거이고, epoch는 TRIAL_SUBJECT에만 필수이며 MEMBER_ACCOUNT는 null이다. MEMBER에 phone epoch를 발명하지 않는다. fixture 값으로 runtime 사실을 대신할 수 없다.

시간은 기존 Mongo Date 저장 정밀도(ms)를 따른다. LC 최초 접수의120일 보장 판정은 Instant 비교로 정각을 제외하며, Billing receivedAt을 LC firstAcceptedAt로 위장하거나 wire 필드를 추가하지 않는다.

### 설치 manifest

| Collection | 추가 unique index | 조건 |
| --- | --- | --- |
| exam_owner_guards | ux_exam_owner(environment,userId) | 전체 |
| reservation_operation_receipts | ux_reservation_operation_receipt(environment,callerService,userId,operationId,commandType) | 전체 |
| entitlement_grants | ux_restoration_consumption(environment,sourceType,sourceId) | sourceType=TRIAL_PAID_RESTORATION |
| entitlement_grants | ux_member_grant_order(environment,ownerUserId,grantOrder) | ownerUserId string + grantOrder long |

validator와 exact index manifest는 PaidExamSchema에 함께 있고 verify가 이를 기준으로 비교한다. 설치 재실행은 동일 schema/index만 허용한다. 기존 grant source unique·ledger dedupe index는 기존 schema가 소유하며 제거하지 않는다. installer에는 운영 URI/credential·CLI/HTTP entrypoint를 추가하지 않았다.

## 6. 검증과 변경 경계

- 새 기반 검사: 9개 쓰기 위치별 rollback, raw payment+6개 기존 repository+guard/receipt 같은 commit, 복원 중복/FIFO, 동시 guard 획득·stale 해제 거절, 늦은 완료/새 pending 보호, source reader round-trip, MEMBER 복원 원장, strict schema/TTL, 중첩 Transaction, unknown commit,120일 정각, 기본 OFF/ON 기동 차단.
- 실제 business reserve/confirm/LC chain·전체28시나리오/E2E 통과 주장은 하지 않는다. runtime 제품 연결과 운영 DB 설치는 후속이다.
- 기존 A/R2/simplified fixture bytes와 서비스 wire 불변. LC/Identity 저장소·실DB·외부 서비스·Jira 상태·commit/push·배포 변경 없음. 기존 dirty 문서/fixture 보존.

### 최종 실행 결과

| 실행 | 결과 |
| --- | --- |
| ./gradlew clean test | 1,497개, 실패0/오류0/skip0 |
| 위 전체에 포함된 PaidExamFoundationTest | 3개 통과 |
| 위 전체에 포함된 PaidExamFoundationIntegrationTest | 실제 replica-set20개 통과(9개 실패 위치별 parameter case 포함) |
| verify-paid-exam-simplified-fixtures-r1.py | 기존80벡터/9파일hash 통과, 원본 불변 |
| 문서 상대 링크 / git diff --check | 통과 |

Gradle 캐시·Git branch 쓰기는 sandbox 제한으로 승인된 실행을 사용했다. 테스트는 가짜 데이터와 로컬 Testcontainers mongo:7.0.14만 사용했다. 기존 deprecated/VM 경고는 유지했다. 개발 중 컴파일과 Spring wrapping 경합 실패를 수정했고, commit failpoint는 primary 재탐색까지 유발하는91 대신 기존 검사와 같은 errorCode1+UnknownTransactionCommitResult로 좁혀 검증했다. 최종 전체 실행은 MEMBER epoch null 규칙까지 반영했다. 보고서: build/test-results/test 및 build/reports/tests/test/index.html. 새 clean 시 교체되므로 source/명령/요약을 함께 보존한다.
