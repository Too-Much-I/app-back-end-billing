# Billing → LC 통합 전환 준비 회신

> 2026-10-18 후속 사용자 활성화 승인: [운영 적용 기록](../runbooks/billing-prod-business-activation-2026-10-18.md)을 우선한다. Identity prod16 COMPLETED를 독립 확인했고 Billing prod8에 유료 시험·삭제 수신 의존 설정12개 ON 배포를 요청했다. 삭제 ALB125 연결 완료. 실제204/LC ACKED는 배포 후 검증하며 아래 사전 E2E 요구는 이 범위에서 대체한다. LC의 봉인/저장소 조건이나 전달 검증 결과를 임의로 완료 처리하지 않는다.

> 최신 독립 조회: [Billing 업무 준비 실측](../runbooks/billing-prod-business-readiness-2026-10-18.md). prod7 완료/양포트 healthy, PURCHASE_BLOCKED/version2, 기존 Billing 권리·시험 inventory0 확인. Identity scope/발신과 LC 준비는 남음. 이전 미확인 표기는 해당 결과로 갱신한다.

> 후속 상태 갱신: 아래 prod:6/ALL_BLOCKED는 직전 Billing 실측이다. 이후 Identity 연결 작업의 문서에는 prod:7 배포 요청·ALB123 탈퇴 경로 추가·PURCHASE_BLOCKED/version2 전환이 기록됐다. 배포 완료는 미확인이다. 실행 시 §7을 우선하고 과거 상태를 덮어쓰지 않는다.

## 1. 5줄 결론

1. 직전 실측 Billing prod:6/8443/TLS/무인증 차단은 완료됐지만 유료 시험·phone·상태·삭제 업무는 아직 활성화하지 않았다. 이번 AWS 재조회는 없다.
2. 아래 표는 코드에서 확인한 조건부 활성화 묶음이다. 실제 환경에 적용하거나 기동 검증한 설정이 아니다.
3. 유료 예약·MEMBER 복구·상태 이벤트·삭제는 ACCOUNT fence를 쓰므로 `PURCHASE_BLOCKED`로도 처리할 수 없다. 검증된 전환 시점에 `OPEN`이 필요하다.
4. 삭제는 paid admission을 포함한 의존성을 요구한다. ALB 삭제POST 추가, LC의 동일event/body 재시도·204 저장 검증도 필요하다.
5. LC 삭제6개ON/집계OFF 후보는 상대 보고로 접수했다. 운영 저장소 권한·writer 배출·봉인·검증계정 등 선행조건이 남아 지금 중단하지 않는다.

## 2. 반드시 읽을 내용

이 회신은 Billing 준비 조건을 고정하는 문서다. 서버 중단·DB 쓰기/봉인·기능ON·ALB 변경·배포 승인이나 정상서명 공동검증 완료가 아니다. LC/Identity 파일과 실행환경은 수정하지 않았다.

`ALL_BLOCKED`는 모든 Billing 무료 writer/모든 LC 경로를 막는 전역 maintenance 스위치가 아니다. PaymentRecoveryFence를 사용하는 쓰기와 별개인 기존 무료 Reservation 경로가 있으므로, 전환 중 유입 제한과 구 writer 배출을 독립적으로 확인한다. 차단값 변경만으로 봉인 조건을 충족했다고 판단하지 않는다.

paid recovery의 기동 의존성 때문에 purchase-account와 MEMBER evidence가 함께 필요하다. 이는 단순 서버용 포트 작업보다 넓은 변경이다. `OPEN` 이후 이미 공개된 purchase-account 경로는 정상 billing:purchase 사용자 요청을 받을 수 있으므로, 검증 중 공개 구매계정/시험 생성 유입 제한을 포함한 실행 승인과 Identity 준비 확인이 필요하다. sync/provider/판매 기능까지 자동ON하지 않는다.

## 3. 조건부 Billing 설정표 — 미적용

기준 이미지: 소스57d6639, ECR digest `sha256:e5666d537391eebc7cffe91896a756f93b93d8acef2d480030e1b56e9e7597f9`. 실제 적용 직전 서비스 revision/이미지/전체 환경변수·Secret 참조를 재조회하고, 동시변경을 보존한다. 아래 ON은 목표값이며 현재값이 아니다.

| 환경변수 | 조건부 목표 | 필수 근거/주의 |
| --- | --- | --- |
| BILLING_WORKLOAD_LEARNING_CORE_RECEIVER_ENABLED | true 유지 | production LC 공개키·별도 TLS8443·JWT 목적 검증 |
| BILLING_MONGODB_INITIALIZE_INDEXES / BILLING_MONGODB_REQUIRE_TRANSACTIONS | true 유지 | 기존schema exact 검사; 누락/드리프트는 중단하고 별도 maintenance 판단 |
| BILLING_PAYMENT_RECOVERY_FENCE_REQUIRED | true 유지 | environment PRODUCTION/control 단일성·epoch/version 읽기 검증 |
| BILLING_PAID_EXAM_FOUNDATION_ENABLED | true | PaidExamSchema 검증, session-bound Tx |
| BILLING_PAYMENT_PURCHASE_ACCOUNT_ENABLED | true | 사용자JWT·limiter·catalog 설정/기본schema, Identity billing:purchase 준비 |
| BILLING_PAYMENT_MEMBER_EVIDENCE_ENABLED | true | 위 purchase-account와 신뢰 issuer/JWKS·fence 필수 |
| BILLING_PAID_EXAM_RECOVERY_ENABLED | true | foundation·MEMBER writer·purchase-account 필수 |
| BILLING_ENTITLEMENT_QUERY_ENABLED | true | public connector·JWT·scope·무료 attribution coverage |
| BILLING_LEGACY_ATTRIBUTION_VERIFIED | 검증 후에만 true | Billing 무료 소유/기존 자료 coverage 증거. LC 목록 봉인과 동일한 증거가 아님 |
| BILLING_PAYMENT_ENTITLEMENT_QUERY_ENABLED | true | 무료reader·fence·PurchaseCore/Sync/Recovery schema 검사 |
| BILLING_ATTEMPT_GROUP_EVENTS_ENABLED | true | source-aware paid 이벤트 처리 및 기존 event 계약 |
| BILLING_PAID_ADMISSION_ENABLED | true | 위 foundation/recovery·LC receiver·통합reader·상태event·PurchaseCoreSchema |
| BILLING_OWNER_REBIND_ENABLED | true | phone continuation 업무 허용. 현재 Identity projection/owner 자료도 별도 확인 |
| BILLING_RESERVATION_EXPIRY_ENABLED | true | source-aware 만료 경로로 RESERVED만 정리; 유지할5분 hold/주기·batch 재대조 |
| BILLING_EXAM_DELETION_ENABLED | true | PaidExamTransactions + PaidReservationService + LC receiver + DeletionSchema |

- Spring profile `prod`, workload/source `production`, payment `PRODUCTION`, scope `tosunsaeng-billing-prod` 유지. issuer는 실제 Identity 발급값을 대조하며 hostname 이름만 보고 바꾸지 않는다.
- Identity receiver, owner cleanup, 구매sync/provider worker·재조회, 자동삭제/purge, snapshot/feed 등 별도 기능은 이 묶음에 추가하지 않는다. phone 이벤트 공급이 OFF/미완료라면 phone 업무E2E를 통과했다고 주장할 수 없고, 별도 Identity 연동 범위를 확정해야 한다.
- 환불 `BILLING_PAYMENT_REFUND_PROCESSING_ENABLED`/`BILLING_PAYMENT_REFUND_PUBLISHER_ENABLED`는 초기 단계OFF 유지. 환불 검증까지 이번 전환에 포함하려면 별도 조건부 묶음으로 provider 증거·fanout/schema·Slack/운영 대응을 점검한다. publisher는 LC8444의 실제 수신 준비·정상JWT 검증 이후에만 ON. origin은 `https://api-prod.to-teacher.com`이며 경로를 설정값에 중복하지 않는다.
- 이것은 즉시 ECS 등록 가능한 전체task JSON이 아니다. coverage/Identity 준비/실DB 재검증을 통과하지 않은 값을 true로 채우지 않는다.

## 4. DB barrier 및 삭제 경로 실행안

1. 중단 전: 운영 control을 읽어 단일성·PRODUCTION/consumer billing·schemaVersion·epoch·version·barrier 확인. 직전 성공검증은 ALL_BLOCKED이며 현재 재조회/변경 없음.
2. 설정·schema 검증 동안 ALL_BLOCKED 유지. 기존 시험 생성 및 공개 purchase-account 유입 제한은 별도 적용·검증한다.
3. LC 봉인/배출·양측 exact 배포·index/owner/삭제저장소 검증과 서명검사 준비 후, 별도 승인으로 `ALL_BLOCKED → OPEN`. environment/consumer/_id/schemaVersion/epoch/version/기존barrier exact CAS, matched1 확인과 version 증가/readback을 사용한다. epoch·schemaVersion은 임의 변경하지 않는다. 사전값 없는 실행명령은 제공하지 않는다.
4. 정상 업무 검증은 OPEN 이후 수행한다. ACCOUNT writer인 paid reservation, MemberOperationRecovery, PaidAttemptEventService, DeletionService는 PURCHASE_BLOCKED에서도 실패한다. OPEN을 기능ON 또는 삭제권한의 대체로 사용하지 않는다.
5. 실패 시 유입 제한을 유지하고 영향 writer를 멈춘 뒤 호환 가능한 차단/복구를 결정한다. 신규 데이터 발생 뒤 구 이미지로 자동 rollback하거나 ledger/receipt를 지우지 않는다.

삭제 네트워크 추가안: 기존 workload host + POST + exact `/internal/v1/exam-deletions/events` + source `13.124.57.130/32` → 기존 HTTPS8443 TG. 현재120~122는 기존6경로,129는 host 나머지404다. 적용 직전 빈 priority를 선택해129 앞에 추가하며 기존 규칙/앱8083/LC host는 유지한다. 삭제 목적 audience는 `tosunsaeng-billing:exam-deletion-events:production`; 다른 목적/환경/주체 토큰으로 대신하지 않는다. 키 교체·권한 확대·규칙 적용은 이번에 하지 않았다.

## 5. 정상서명 검증 순서·완료 증거

| 단계 | 대상 | 필요한 증거 |
| --- | --- | --- |
|A 인증 경계|실제 LC 발신 경로→Billing status 등|LC 운영 issuer/sub/목적aud/kid·scope 대조, 정상서명은 인증단계를 통과하고 계약대로 응답. 토큰/키 로그 금지. 401/404만으로 완료 불가|
|B 예약·상태|승인된 회원/무료·유료 source|reserve→LC commit→confirm, status 원본복원, same-key 동일Session, 응답유실·cancel/expiry·terminal 이벤트. 실제 body/source·원장/receipt와 양측 결과 대조|
|C phone|승인된 binding/owner 자료|continuation 적용/없음, exact echo 및 owner 경합. 근거 없이 phone candidate/이전 소유 증거를 발명하지 않음|
|D 삭제|삭제 가능한 지정Session|최초event/body 영속화→서명POST→Billing commit 후 empty204→LC204저장. 재시도 동일body/event, 중복/충돌·응답유실, ACK 전 신규생성차단/후해제, 원문정리·미전달 TTL 보호|
|E 구앱·신앱|지정 운영 검증 계정|v1 무료 생성·기존 제출/채점 유지, v2 최종자격검사·same-key. 권리 없는 v2의 무료 우회 금지|
|F 환불(포함 승인 시)|검증된 환불 fixture/지정 구매|LC8444 준비 후 발신. 신규사용차단·보호Session/기접수 작업·중복/늦은이벤트, 양측 확정결과. 실제 Store 환불 실행은 별도 승인|

운영 검증계정·삭제가능 Session·구매/환불 자료와 영향범위는 아직 지정되지 않았다. 사용자 자료를 임의삭제하거나 실제 Store 환불을 만들어 테스트하지 않는다. 비운영 리허설은 runtime 구현 근거가 되지만 실제 운영 key/TLS/storage 권한 검증을 대신하지 않는다.

LC `billingDeliveryVerified`는 위 D의 실행 근거 또는 합의된 사전검증 절차로만 충족한다. 전체 LC 기동이 그 값에 의존해 순환한다면, 먼저 전용 사전검증 절차/승인된 fixture와 증거 저장 방법을 양측이 확정한다. 기동을 위해 값을 선행 true로 기록하지 않는다. `storageVerified`도 Billing 수신준비로 대체할 수 없다.

## 6. LC에 전달할 요지 및 미해결

> Billing은 prod:6 LC JWT/TLS 수신까지만 완료했습니다. 유료·상태·삭제 업무용 조건부 flag 묶음과 삭제POST allowlist 추가안을 코드 기준으로 정리했습니다. 이들 paid/삭제 쓰기는 ACCOUNT fence 때문에 OPEN이 필요하며 PURCHASE_BLOCKED로 검증할 수 없습니다. OPEN 전 공개 구매계정/신규 시험 유입 제한과 Identity 구매권한·Billing attribution coverage를 확인해야 합니다. 삭제 전달·204 저장 실검증 전 billingDeliveryVerified를 완료로 보지 않습니다. LC storage/writer 근거·봉인/배포설정·검증 계정/영향범위를 맞춘 뒤 하나의 승인된 중단 구간에서 진행하겠습니다. 환불 publisher는 LC8444 준비 전OFF입니다.

이 문서는 인계용 파일이며 다른 채팅·Jira·외부로 전송하지 않았다. 다음 실행 전 확정할 것은 (1) 양측 선행조건 증거 (2) 생성/구매계정 제한 방식·시간·최대중단/복구 기준 (3) 검증계정/자료 및 삭제·환불 영향범위 (4) barrier OPEN/업무ON·삭제route 변경 승인이다.

### 근거·검증 범위

- [직전 운영 연결 결과](../runbooks/billing-prod-lc-receiver-8443-2026-10-18.md), [운영 전환 묶음](../runbooks/billing-production-cutover-package-2026-10-18.md).
- `PaidExamConfiguration`, `MemberEvidenceConfiguration`, `PublicSecurityConfig`, `DeletionConfiguration`, `WorkloadIngressFilter`, `PaymentRecoveryFence`, `PaidReservationService`, `MemberOperationRecovery`, `PaidAttemptEventService`, `DeletionService`, `RefundRuntimeConfiguration`, `PaymentRefundConfiguration` 및 application.yml을 정적으로 대조했다.
- LC `LC_PRODUCTION_COMBINED_CUTOVER_READINESS_2026-10-18.md` 최신판을 읽었다. 삭제6개ON/집계OFF·테스트8개는 LC 보고이며 이번 Billing 실행 결과가 아니다.
- 이번은 문서 작업만: 제품코드/DB/AWS/LC·Identity/Git commit·push 변경 없음. Gradle 미실행, 환경변수 매핑·링크/공백 정적검사. 기존1838개 통과를 신규 업무ON 검증으로 재사용하지 않는다.

## 7. 후속 선행조건 대조 및 삭제 사전검증안

### 최신 상대 기록 — 이번 AWS 독립 조회 아님

- Identity `identity-prod-capture-applied-2026-10-18.md`: prod15 capture/control/fence ON, 운영 control523명 준비. 구매 scope·publisher OFF. 이전의 control/fence 미준비 설명은 대체하지만 구매 권한 준비 완료는 아니다.
- Identity `identity-billing-prod-withdrawal-connection-plan-2026-10-18.md` §9: Billing prod7 등록/배포 요청, Identity receiver/withdrawal 두 값 ON, ALB123 탈퇴 POST 추가, DB PURCHASE_BLOCKED/version2로 CAS 전환 및 epoch 보존을 보고한다. ECS 완료/실제 JWT probe/Identity publisher는 미완료다.
- 따라서 다음 실제 작업의 첫 단계는 prod7 rollout/target health와 live control 재조회다. prod6 task를 재등록하거나 ALL_BLOCKED/version1을 전제로 CAS하지 않는다. ALB123을 삭제 route에 재사용하지 않는다. 기존 Identity 연결 변경을 보존한다.
- ACCOUNT writer의 OPEN 요구는 동일하다. 다음 전환은 최신 확인값에서 OPEN으로 계획해야 하며, Identity 작업의 PURCHASE_BLOCKED 승인만으로 OPEN을 승인받은 것으로 보지 않는다.

### Billing 무료 attribution 근거 확보

기존 [reader rollout §5](../runbooks/PLAN-007-public-reader-rollout.md)의 전체 inventory/dry-run을 재사용한다. 활성 Session의 subject/group/Reservation 및 epoch 연결을 확인하고 누락/blocked 건수를 집계한다. schema41개 검증이나 LC 목록 봉인은 이 데이터 검증을 대체하지 않는다. 현재 운영 coverage 보고서는 확보하지 못했다. 데이터가 없으면 정확한 환경의 전체 inventory가 0임을 확인한 결과를 기록하며, 단순 신규 DB 추정으로 verified=true를 넣지 않는다. 이관이 필요하면 별도 승인 전 읽기에서 멈춘다.

### 삭제 전달 gate 순환을 해소할 한정 사전검증 제안 — LC 검토/구현 필요

1. 모의 DB/서버 리허설과 실제 운영 경로 검증을 분리한다. 운영의 잘못된 body에 대한 정상JWT400 검사는 인증 경계 증거일 뿐 commit204/ACK 저장 증거가 아니다.
2. LC 전체 scheduler/gate를 켜지 않는 일회성 검증 도구로 기존 sender/client·Tx·lease·ACK 저장 로직을 재사용하는 방안을 권장한다. rollout의 billingDeliveryVerified를 선행 true로 쓰는 우회는 하지 않는다. 새 상시 서비스나 승인 시스템은 만들지 않는다.
3. **현재 BillingDeletionSender.tick()은 전체 PENDING 중 한 건을 고른다.** 운영에서 그대로 한 번 호출하는 것도 지정 외 삭제를 보낼 수 있다. 도구가 exact 승인 target/event만 선택하도록 LC 측 범위 제한과 회귀검증을 먼저 마련해야 한다. 현재 도구가 구현됐다고 보지 않는다.
4. 대상은 정상 경로로 생성된 양측 exact Session 및 사용자가 삭제를 허용한 자료여야 한다. 검증용 사용자/구매/소유 증거를 DB에 직접 발명하지 않는다. Billing은 존재하지 않는 Session/group을 DELETION_TARGET_UNRESOLVED로 거절하므로 임의 ID로204를 확인할 수 없다.
5. 실행 전 양측 schema/owner/storage·미전달TTL/전달index 확인, 대상·event/body digest 고정, 다른 worker와 공개 유입 제한, Billing OPEN·삭제 업무/route 활성화 승인까지 필요하다. 운영 실제 쓰기 단계이며 현재 요청에서 실행하지 않는다.
6. 성공 증거는 Billing fact/receipt commit, empty204, LC 동일event/digest의 영속 ACK 저장이다. 토큰/본문 대신 제한된 검증 기록을 보관한다. 응답 유실 재시도는 최초event/body 유지, 중복 원장 없음·ACK 수렴을 확인한다. 검증 후 fact/receipt를 지우거나 Session을 부활시키지 않는다.
7. 이 증거와 LC storage/writer 근거가 모두 확인된 뒤에만 해당 rollout 값을 기록한다. 대상이 없거나 한정 실행 도구가 없으면 중단 전에 준비하며 운영 gate를 완화하지 않는다.

LC에 요청할 최소 회신: exact target 한정 사전검증 도구의 구현 가능 여부와 승인된 검증자료 준비 여부, storage/writer 근거. Identity에는 prod7 연결 후속 상태 및 billing:purchase 발급 준비 결과를 확인한다. 다른 채팅에 메시지를 전송하지 않았다.
