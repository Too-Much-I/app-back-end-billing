# PLAN-012: 전액·부분 환불 원장·기간 재배치·시험 접근 철회

- 작성일: 2026-10-06 / 상태: 승인 대기 / Jira 미생성
- 선행: [010](PLAN-010-purchase-ingestion-and-timeline.md), [011](PLAN-011-paid-reservation-and-usage-evidence.md), [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md) §5.7/5.9 및 C9-S10 R1/R2.

## 1. 5줄 결론

1. 문의/팀 판단만으로 환불하지 않고 검증된 최종 provider 결과만 반영한다.
2. 전액/부분 모두 해당 구매 잔여권 종료, 다른 구매·무료권은 보존한다.
3. 최초 권리 종료와 후속 외부 금전 정정을 분리해 돈의 실제 반환과 중복 방지를 보장한다.
4. 미시작 후속 기간은 최초 Billing appliedAt 기준으로 앞당기며 지연을 소급 소진하지 않는다.
5. LC exact deny·GRADING gate·durable outbox 및 실제 금액 fixture가 출시 선행 조건이다.

## 2. 사용자가 반드시 읽어야 하는 내용

정상 운영은 문의→팀 검토→Store 환불→권리 종료→문의 종결. 일반 반복 환불 서비스·직접 송금·자동 심사/공제는 없다. Google Console의 부분 환불 기능이 RC 부분 금액 지원을 보장하지 않는다. Apple은 심사 결과 반영이며 Refund Control 자동 활성화/usage 공유는 범위 밖이다.

### 권리 처리

| 상태 | 처리 |
| --- | --- |
| RESERVED | source revoke, confirm 거절·예약 정리·LC Session 보상 |
| OPEN/RETAKE_AVAILABLE | 추가 동작/replacement 차단 |
| GRADING | refund보다 먼저 Billing이 승인한 exact Session만 terminal 허용 |
| COMPLETED | 결과 보존, 재시작 없음 |
| 이미 종료된 source의 추가 환불 | 금전 사실만 기록, 재종료/재배치 없음 |
| REFUND_REVERSED/뒤늦은 owned | D2 REVIEW_REQUIRED, 자동 지급/복구 없음 |

## 3. 사용자가 결정해야 하는 사항

R1/R2와 잔여권 종료는 승인 완료. 다음 금융 모델은 **구현 전 검토할 기술 제안**이다. 실제 provider 증거가 충족하지 못하면 지원되는 대사/정정 경로를 별도 승인받는다. 법적 반환액 공식은 이 계획에서 만들지 않는다.

## 4. 주요 위험과 미확인 사항

동일 refund를 webhook eventId만으로 식별하면 API 관측과 이중 합산될 수 있다. 구매가 REFUNDED라는 이유로 후속 금전 사실을 모두 무시해도 안 된다. 반대로 누적액 감소를 자동 지급/권리 복원으로 처리하면 D2를 위반한다. 실제 refund 식별·금액/통화/세전세후·확정 시각을 source별 검증해야 한다.

RC가 확정 환불은 증명하나 반환액은 제공하지 않는 경우 접근 종료는 수행하고 금액 대사 상태를 분리한다. 금액 미상은0이나 구매 전액으로 대체하지 않는다. 인증된 최종 환불 증거 자체도 없으면 문의만으로 차단하지 않고 REVIEW_REQUIRED. 2026-10-07 사용자 승인으로 RC 증거가 부족한 Google 소모성 부분 환불의 인증된 Orders 읽기 조회 보완만 허용한다. 다른 direct Store adapter/운영자 입력으로 신뢰 계약을 우회하지 않는다.

### Google 부분 환불 조회 보완 — 승인된 범위와 남은 검증

- 조회 후보는 `GET /androidpublisher/v3/applications/{packageName}/orders/{orderId}`다. package는 서버 설정, order는 RC로 검증한 Purchase에 결속한 reference를 사용한다. 앱 입력 orderId만으로 owner를 결정하거나 새 권리를 지급하지 않는다.
- 공식 Orders 모델의 `orderHistory.partialRefundEvents[]`에 `state`, `processTime`, `refundDetails.total/tax`가 있다. `PROCESSED_SUCCESSFULLY`만 확정 근거 후보이며 `PENDING`은 완료가 아니다. total은 세금 포함 금액이다. 실제 소모성 소비 완료 거래에서 매핑/환경/통화/이력 완전성과 정정 의미를 검증한다.
- 공개 모델에 전용 refund ID가 보이지 않는다고 시각+금액을 유일 ID로 발명하지 않는다. RC event ID와 Google 관측을 별도로 더하지 않고 검증된 누적 snapshot/금융 effect 비교안을 exact 계약으로 동결한다. 증거 부족/낮은 누적액/모호한 전체 환불 합산은 REVIEW_REQUIRED이며 기지급 권리를 재개방하지 않는다.
- OAuth scope는 androidpublisher이며 scope 자체는 읽기 전용이 아니다. 실행 코드는 GET allowlist, 별도 서비스 주체와 필요한 Play 최소 권한을 검증한다. 기존 RC 키를 임의 재사용하거나 환불 실행 권한을 자동 부여하지 않는다.
- RC 알림이 없는 부분 환불도 고려해 문의 후 조회와 주기 대사 범위를 설계한다. 실제 조회 빈도/오래된 주문 보존·quota·retry/schema는 구현 전 동결. 원문 response/buyer address/purchaseToken 로그·문서 저장 금지.
- Google 실제 조회·부분 환불 fixture/권한은 미검증이다. 이번 승인은 경로 허용이며 구현·외부 환불/권한 변경·판매 활성화 승인이 아니다.

## 5. 현재 작업과 직접 관련된 구현

### 금융·권리·처리 상태 분리 제안

- `accessState=ALLOWED|REVOKED` 및 최초 `accessRevocationEffectId/appliedAt` 고정.
- 금융 snapshot은 `purchasedAmount`, `currency`, `refundedAmountKnown`, `refundedAmount`, `amountBasis`, `evidenceVersion`를 사용한다. 금액은 Decimal128 decimal, unknown=null. `PARTIALLY_REFUNDED|REFUNDED|REFUND_AMOUNT_UNKNOWN` 같은 상태는 기존 enum과 public projection을 Phase 0에서 동결; 단일 terminal enum으로 모든 의미를 합치지 않음.
- provider observation의 event dedupe와 business refund dedupe를 분리. `(environment,store,appId,transactionKey,refundIdentity)`의 검증된 business key를 사용하며 raw refund ID는 로그에 남기지 않는다. refundIdentity 미제공이면 cumulative snapshot/version 등 충분한 동등 증거가 있을 때만 변환한다. 다른 금액만 보고 임의 새 refund ID를 발명하지 않음.
- INCREMENTAL 증거: 새 unique refund만 금액 추가. CUMULATIVE 증거: 같은 통화/기준에서 authoritative 총액과 마지막 반영액의 차이만 ledger 기록. 두 feed를 혼용할 때 공통 identity/authoritative source 없이 양쪽 합산 금지. 같은 금액 관측은 변화 없음, 늦은 낮은 누적액/음수/초과/통화 불일치는 검토. provider가 정정을 증명하면 승인된 정정 entry로 처리하며 옛 ledger overwrite 금지.
- 추가 반환은 실제 delta만 기록. 이전 unknown 금액이 해소되는 관측은 미기록 차이만 반영하고 access effect를 반복하지 않음. 상세 `payment_refund_observations` collection 도입 여부·business unique·cumulative version CAS·처리 보존을 ADR A에 동결 후 구현한다. 환불 journal/business dedupe는120일 inbox보다 짧게 유실되지 않아야 한다.

### 최초 refund Transaction과 경합

2026-10-06 F2 A안 승인: 모든 group/outbox를 한 Transaction에 넣는 초안을 대체한다. provider I/O 밖에서 증거 검증→account timeline/entitlement version CAS→구매 root 접근 revoke+검증된 monetary entry/금액불명 review+후속 slot reflow+durable fan-out job을 같은 Transaction으로 반영한다. 예약/guard/group 정리와 group별 outbox 생성은 bounded 작업으로 분리한다. 이미 revoke된 구매는 새로운 금전 effect만 반영하고 job/권리 종료/reflow를 중복 생성하지 않는다. unknown commit은 동일 effect 조회, 재시도마다 기간을 다시 계산하지 않는다.

- 모든 paid reserve/confirm/replacement/채점 승인은 group 정리 완료 여부가 아니라 authoritative 구매 root 접근 상태를 검사하고 공통 CAS 쓰기로 환불과 경합해야 한다. 단순 read만으로 우회 허용하지 않는다.
- root 차단과 작업 등록은 원자적이다. worker는 stable group 식별자 기반 진행 지점, lease/fencing, bounded batch, group/version unique outbox 및 완료 검증으로 장애 후 재개한다. checkpoint를 작업 완료보다 먼저 전진시키지 않는다. exact cursor/index/batch/lease schema는 구현 전 동결한다.
- 환불 선행 채점은 거절한다. 승인 선행 exact Session은 영속 승인 증거로 판정하며 worker 실행 당시 상태나 클라이언트 발생 시각으로 추정하지 않는다. 신규 group 삽입도 root CAS와 경합하므로 root 차단 후 새 사용 group이 생길 수 없어야 한다.
- 지연된 정리 작업은 exact group/operation/version에만 적용한다. 새 구매/새 group의 guard를 해제하지 않으며 다른 권리 INITIAL은 기존 guard 안전 종료 후 허용한다.
- root deny는 LC 즉시 동기 차단을 의미하지 않는다. durable 전달·재시도·지연 경보를 유지하고 금융 환불을 전파 실패 때문에 rollback하지 않는다.
- timeline reflow의 Transaction 크기 한계는 별도 검증 대상이다. group 분할만으로 해결됐다고 간주하지 않으며 ADR의 긴 timeline 격리 조건을 유지한다. root 차단과 reflow 경계를 바꿔야 한다면 별도 계약 승인 전 구현하지 않는다.

### R2 시각·reflow

`providerConfirmedAt`은 없으면 null, `firstVerifiedAt`은 최초 검증 고정, `appliedAt`은 최초 성공하는 권리 종료 Transaction에 저장하는 기준 시각이다. commit 불명 재시도는 기존 값 사용. 응답/LC 수신 시각이나 Mongo 실제 commit timestamp라고 주장하지 않는다.

appliedAt 기준 아직 시작하지 않은 뒤 slot만 sequence 순으로 처리한다. 제안 계산: `candidateStart=max(appliedAt, preceding valid slot end)`, 기존 시작보다 늦어지지 않는지 검증해 앞당기고 duration을 유지한다. overlap/순서 불변식 위반은 Transaction rollback/review이지 임의 기간 삭제 아님. 시작/완료된 다른 slot은 변경하지 않는다. 실제 시각 후속 확보/중복/추가 반환은 다시 reflow하지 않는다.

| 예제 | 결과 |
| --- | --- |
| A10:00 환불,18:00 반영,B는 다음 날 시작 예정 | B18:00 시작, 원 duration 유지 |
| B가 기존 일정대로17:00 시작 | B17:00 일정 유지,18:00 재시작 안 함 |
| A는 미래 slot, 앞에 유효 C가 남음 | C 끝보다 먼저 B를 시작하지 않음 |
| source 이미 EXPIRED, 뒤 slot 진행 중 | 금전 기록만 추가, 진행 기간 재지급 없음 |
| provider 시각 미상→나중에 확인 | null을 증거로 보완하되 적용된 B 일정 불변 |

### LC durable revoke

ADR 초안 `POST /internal/v1/entitlements/access/events`, event `AttemptGroupAccessRevoked` v1, producer=billing, exact scope·eventId·userId·group·positive accessVersion·reason·gradingSessionId. 상대 서버 승인 전 route를 확정 배포하지 않는다. SigV4 최소 IAM route, W3C traceparent 주입 후 서명, baggage 금지.

group/version unique outbox, immutable retry event ID/wire. LC inbox+deny local commit 후204; duplicate/stale204, conflict409,unsupported422,transient503. revoke-before-session은 tombstone204, 뒤늦은 Session/status가 해제 못 함. paid Job은 Billing GRADING commit 확인 전 실행 금지. refund 이전 exact GRADING만 완료하고 UI 접근 차단만으로 대체하지 않는다.

worker는 ADR5.10 lease30초/fencing·5초 poll/batch100·5초~1시간 jitter/24시간 dead-letter,401/403 BLOCKED_AUTH,409/422 격리. 실패로 금융 환불 rollback 금지. source revoke와 LC 실제 차단 사이 지연은 경보/운영 차단으로 관리, 소급 과금 없음. `refund-processing-enabled`,`access-publisher-enabled` 기본OFF 제안; 판매 중에는 회수 처리를 신규 판매 flag와 함께 끄지 않음.

## 6. 부록 — 완료 기준

- partial→additional partial/full actual delta, same refund API+webhook, duplicate monetary event·권리/reflow 한 번, unknown amount resolution, 낮은 cumulative/통화 충돌 review.
- refund-before-purchase tombstone, late owned/REFUND_REVERSED 무지급, 추가 반환 뒤 보존 기산점 갱신.
- 모든 R2 표 사례 및 동시 purchase append/refund, confirm/refund 양순서, GRADING/refund 양순서, unknown commit·worker lease 유실.
- LC duplicate/stale/conflict/revoke-before-session, Job 실행 선행 금지, deny 영속화 후204, 타 role/unsigned/direct 우회 차단.
- 실제 Store/RC partial fixture 없이 fake 성공을 G3 통과로 간주하지 않음. `./gradlew clean test` 및 Mongo/상대 계약 테스트 필수. 실제 환불은 승인된 운영자만 수행.
