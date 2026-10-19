# TMI-212 C2b — 계정 조회·sync 연결 규격

> 2026-10-14 D2 개정: 아래 C2b 원규격 중 sync-r1/cursor-r1·재개/정기조회 미구현 표현은 [재개](TMI-212-review-resume-r1.md)·[정기조회](TMI-212-reconciliation-r1.md)로 대체한다. runtime은 sync-r2/cursor-r2와 [구매 reference](TMI-212-purchase-reference-r1.md) 명시 이관 후 사용한다. 나머지 lease/검증/외부 wire는 유지하며 [운영 안내](../runbooks/TMI-212-reconciliation-and-review.md)를 따른다.

2026-10-13. C2a의 공유 project budget/guarded receive를 사용한다. 자동 활성화·실환경 승인이 아니다.

## 결론과 유지 계약

- 기존 `payment_reconcile_cursors` ACCOUNT_LOOKUP 한 행이 계정의 모든 PENDING sync를 묶는다. sync-command-r1에는 새 필드를 추가하지 않는다. command의 기존 PENDING/APPLIED/REVIEW_REQUIRED/BLOCKED_AUTH와 errorCode를 사용한다.
- HTTP는 Transaction 밖, 원 관측 접수·반영·command 연결·완료·페이지 전진은 최초 epoch와 동일 cursor lease의 쓰기 guard로 보호한다. 이미 APPLIED인 legacy source도 HTTP 전에 회수한다.
- 페이지의 정상 관측은 전부 durable 접수 후 전진한다. PURCHASE_BLOCKED source는 접수함/C1 worker에 남긴다. 검증 불명 item은 남은 PENDING command를 검토로 인계하고 account scan을 멈춘다. 미구매·환불·pending 차감 근거로 쓰지 않는다.
- 원문 페이지 cursor는 저장하지 않는다. 기존 AES-GCM helper를 사용하고 AAD를 환경/project/`account-page`/계정 HMAC으로 고정한다. 한 순회 최대1000페이지로 제한하고 한 번의 claim에서 한 페이지만 처리한다. 제한 초과/잘못된 페이지는 검토 보류한다.
- 기본 OFF runtime 배선을 구현한다. ApplicationReady 후 전용 단일 executor에서 source tick→account tick을 수행하며 1초 fixed delay, batch100이 기본이다. 공개 sync ON은 D의 보존/운영 검토·rollout readiness 전까지 계속 거절한다.

## 저장 규격·이관

cursor-r1: 기존 `_id,accountId,kind,version,nextAttemptAt,lastActivityAt` 필수, kind=ACCOUNT_LOOKUP, version long>=1, nextAttemptAt date|null. optional `lookup` object: owner string|null, until date|null, leaseVersion/generation/attempts long>=1, fenceVersion/pages long>=0, reference UUID string, cursor object|null, failure string|null. cursor envelope는 revision long=1/keyId/nonce/ciphertext string. work owner/leaseVersion/generation/unexpired until을 매 금융 Transaction에서 쓰기 검증한다. reference는 account.activeRefId와 동일해야 하며 같은 account/environment의 refs 연결을 확인한다. WITHDRAWN은 참조를 확인하되 active=true를 요구하지 않고 기존 core가 금융 기록/검토만 남기게 한다.

기존 index 두 개 `ux_payment_reconcile_cursor(accountId,kind)` unique, `ix_payment_reconcile_due(nextAttemptAt,_id)` nonunique를 보존하고 새 index/TTL은 없다. 모든 writer 격리/ALL_BLOCKED에서 기존 validator 없음 또는 exact r1만 수락한다. 전체 기존 문서의 목표 validator 적합성 검사 후 collMod strict/error. 기존 work 없는 행은 첫 claim에 초기화한다. 구 writer 자동 rollback은 금지한다. foundation index 검증과 별도로 cursor validator를 확인한다.

기존 sync-command-r1 필드를 재사용하므로 종전 계획의 sync worker 필드 추가/r2 backfill 요구는 이 구현 범위에서 대체한다. 금융 상태 중복 저장을 줄이기 위한 계정별 잠금이며 지급/취소/보존 계약 변경이 아니다.

## 시간·재시도·복구

lease30초, 최초 epoch 고정, batch1~100, 한 account는 tick당 한 번만 처리한다. 5초~1시간 exponential+jitter와 RevenueCatFailure.retryAt 중 늦은 시각을 nextAttemptAt으로 사용한다. 404/빈 목록은 동일 재시도이며 권리/금전 상태를 바꾸지 않는다. command.createdAt+24시간에 도달한 미해결 command는 검토로 전환하되 pending을 보존한다. 먼저 이미 있는 source를 완료한 뒤 남은 command에 예산을 적용한다.

정상 처리로 해당 계정 PENDING이 모두 없어지면 같은 cursor Transaction에서 페이지를 비우고 failure=IDLE/attempts=1/nextAttemptAt=now로 둔다. 다음 구매의 첫 claim은 attempts=1부터 시작하므로 과거 장애 backoff가 새 구매에 누적되지 않는다. IDLE은 외부 호출 예약이나 금융 상태가 아니며 PENDING이 생겨야 due 조회에 포함된다.

401/403은 command BLOCKED_AUTH와 cursor nextAttemptAt=null/failure=BLOCKED_AUTH. 실제 RC 호출은 C2a project block도 적용한다. 계약/불명 오류는 REVIEW_REQUIRED로 인계한다. 검토 인계 marker를 먼저 저장하고 100개씩 command를 옮기며, 나머지가 있으면 1초 뒤 계속한다. 중간 crash/일반 오류로 marker를 지우거나 HTTP 조회로 되돌리지 않는다. 중단된 cursor는 자동 재개하지 않는다. generation은1로 생성·검증한다. 계정/command의 승인 재개 도구(원 version CAS/살아있는 lease 없음/최초 epoch guard·generation 증가)는 **D 후속이며 아직 구현하지 않았다**. C2a project credential 재개만 이미 구현돼 있으며 그것만으로 계정/command가 재개되지는 않는다.

새 sync는 기존 cursor를 공유한다. PENDING commands에서 계정별 group→cursor lookup→due 순 정렬/limit하므로 빈 큐에서 모든 과거 계정 cursor를 순회하지 않는다. DB timeout2초와 반환 batch100은 실제 조회 스캔량 상한을 뜻하지 않으며 운영 데이터 규모의 explain/부하 검증은 필요하다. 검토/AUTH marker가 있는 계정의 새 미확인 sync는 같은 검토 상태로 접수하고 pending을 보존하며 자동 재개하지 않는다. 이미 검증·반영된 구매는 즉시 APPLIED가 우선이다. 종료된 페이지 순회는 cursor를 비워 다음 순회를 첫 페이지부터 시작하며 도중 도착한 새 sync도 다음 순회에서 회수한다. 페이지 접수 뒤 checkpoint 전 장애는 동일 관측 재접수/원장 멱등성으로 복구한다.

## runtime 설정

`billing.payment.provider.worker.enabled=false`, poll-millis=1000(1000~60000), batch=100(1~100). ON일 때 api-key, reference-key(AES256 base64), reference-key-id, budget-database 및 기존 provider.project/lookup-key/version/apps가 필요하다. 환경변수는 application.yml의 `BILLING_PAYMENT_PROVIDER_*` placeholder를 사용한다. OFF에서는 DB/schema/키 의존성을 만들지 않는다. ON은 기존 foundation/recovery/core/sync/cursor 및 shared budget schema를 verify-only로 확인하고 catalog의 RC product→앱/store/상품 매핑을 로드한다. 새 schema를 자동 생성하지 않는다.

budget-database는 Billing DB와 다른 명시 이름이며 동일 MongoClient/cluster를 사용한다. 같은 RC project의 모든 환경/인스턴스가 같은 cluster+DB를 사용해야 한다. 서로 다른 cluster인 경우 이 배선으로는 합산을 보장할 수 없으므로 활성화하지 않고 별도 공유 연결 구성을 먼저 정한다. 실제 topology는 확인하지 않았다. API transport는 실제 bounded HTTPS+공용 budget이며 fake/ready=true 주입 경로를 runtime에 두지 않는다. 실행은 ApplicationReady 이후, 종료 때 executor와 HTTP client를 닫는다. 식별자 없는 phase별 runs/claimed/failed metric만 추가한다. Slack 경보/운영 재개는 D 후속이다.

## 삭제와 운영 경계

205 purge는 cursor.lookup.owner가 있으면 (만료 여부와 무관하게) 삭제를 보류한다. pendingEvidence와 cursor version CAS도 유지한다. 지금 구현은 PENDING sync가 있는 계정만 조회하므로 일반 ACCOUNT_LOOKUP 정기 대사/retention cleanup은 D 후속이다. 자동 삭제를 활성화하지 않는다. shared budget DB topology·운영 재개/알림·실Store/RC 매핑 검증은 배포 전 필요하다.
