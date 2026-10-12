# TMI-204: Billing 단일 진행 관리 기반 탈퇴 누락 복구 구현 계획

- 작성·개정일: 2026-10-12 / Jira: TMI-204, 상위 TMI-137
- 상태: **snapshot 중심 재동기화 승인·scalar-end-1 Billing 기술 수락. PR1 제어기반과 PR2 snapshot 영속 처리 코어를 구현했다. 자동 worker·feed·실환경 검증은 후속이며 전체204 완료 아님.**
- 2026-10-12 추가 승인: 사용자 요청으로 v1 최초 구현·floor+별도 range 방향과 [204 운영 회신](../contracts/TMI-204-OPERATIONS-PROPOSAL.md)의 초기값을 본 계획 및 Jira TMI-204에 반영한다. 수치의 Billing 계획 채택이며 실제 설정 적용·공동 exact 검증 완료는 아니다.
- 개정 근거: Identity 「TMI-203·204 단순화 공동 검토 회신 — 진행 상태의 Billing 단일 소유」와 [단순화 인계서](../contracts/TMI-203-204-SIMPLIFICATION-HANDOFF.md).
- 이번 갱신 범위: Billing 계획·작업기록과 Jira TMI-204 본문. Identity203 계획/코드·Jira는 변경하지 않는다. 상대 회신을 기록하며 운영 DB 완전성/배포 완료를 독립 검증한 것은 아니다.
- 기준: [PLAN-009](PLAN-009-payment-catalog-and-account.md), [공동 계약](../contracts/IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md), [기존 exact 규격](../contracts/TMI-199-EXACT-WIRE-AND-INDEX-SPEC.md), [삭제 설계](../contracts/PLAN-009-withdrawal-reconciliation-and-purge-contract.md), [201 구현 안내](../runbooks/TMI-201-payment-foundation.md).
- 버전 주의: 이 개정은 구 계획의 원격 ACK/generation 설계를 대체하는 **계획 기준**이다. 기존 공동 exact 규격을 이미 변경·검증했다는 뜻이 아니다. 신규 revision과 전환 방식 동결 전 구 wire와 새 의미를 혼합 구현하지 않는다.

## 1. 5줄 결론

1. Identity는 탈퇴 원천·snapshot/feed·재생 가능 범위를 제공하고, Billing만 적용 진행점·로컬 복구 epoch·consumer coverage를 관리한다.
2. 원격 복구 완료 ACK, Identity 발급 consumer generation, 양쪽 checkpoint 대사를 제거한다. 기존202 push의204 응답은 유지한다.
3. 고정 T/H snapshot·digest, 고정 H2 cursor, Billing 로컬 CAS·복원 격리는 유지해 누락과 오래된 작업의 덮어쓰기를 막는다.
4. 정상 운영은 push+주기 feed이고 snapshot은 최초 초기화·기준점 상실 등에 사용한다. 일시 지연을 전체 판매 차단이나 건별 수동 심사에 자동 연결하지 않는다.
5. 실제 거래 확인·계정 물리 삭제·결제 지급/환불은 별도다. 모든 신규 기능은 기본 OFF, 공동 exact·실제 Mongo/IAM/복원 검증 후 활성화한다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.0 최신 승인 — snapshot 중심 재동기화 (2026-10-12)

사용자는 다음 단순화 흐름을 승인했다. 본 항목은 과거 상세 GAP 누적/상한 초과 UNKNOWN 설계 및 과거 이력 완전성을 현재 복구 완료 조건으로 요구하는 표현을 대체한다.

1. 평소에는 push로 탈퇴를 반영하고 feed를 연속 순번으로 따라간다. push 자체의 도착 순서는 보장하지 않으며 기존 멱등 처리를 유지한다.
2. 필요한 원본이 없어 이어받을 수 없으면 같은 요청을 무한 반복하지 않고 현재 탈퇴 목록의 고정 T/H snapshot으로 bounded 재동기화한다. 일시503/통신실패는 기존 retry를 따르며 즉시 원본소실로 간주하지 않는다.
3. 검증된 snapshot에 있는 계정을 WITHDRAWN으로 수렴시킨다. 목록에 없다는 이유로 기존 WITHDRAWN을 ACTIVE로 되돌리지 않는다.
4. count/order/digest/expiry·stream binding 검증 완료 뒤 H를 새 상태 기준점으로 설치하고 H 이후 feed를 이어간다. 이전 job/cursor의 늦은 결과는 local CAS/fencing으로 거절한다. 미검증 snapshot으로 C를 건너뛰지 않는다.
5. 과거 원event 이력을 모두 확인할 수 없었다는 최소 상태/사유만 남긴다. 상세 누락 interval 목록·무한 작업 이력을 control에 누적하지 않는다. 새 기준점 이후 현재 동기화 상태와 과거 이력 불명을 별개로 관리한다.

상세 GAP 개수 상한·초과 시 목록 UNKNOWN 설계는 도입하지 않는다. 최소 기록에 사용자/event 원문을 포함하지 않고 기존 보존기한을 연장하지 않는다. 기존 snapshot 실패/만료 budget·알림·정리 정책은 유지하며 실패 때마다 무제한 새 snapshot을 만들지 않는다.

이 승인은 Billing 복구 방식 변경이다. Identity의 source replayFloor/range 증거와 wire를 없애는 변경은 아니다. snapshot 원천 범위 밖의 과거 탈퇴까지 모두 복원했다고 주장하지 않는다.

205 삭제는 해당 계정의 검증된 탈퇴 사실·거래/환불·보존기간·동시성 보호 등 별도 증거로 판정한다. 과거 전체event 불명만으로 모든 계정의 삭제를 영구 보류하지 않되 해당 계정의 필수 증거가 부족하면 삭제하지 않는다. snapshot 성공만으로 미구매/자동 삭제를 승인하지 않는다.

### 2.1 정상 흐름

#### Identity snapshot 포함 범위 회신 (2026-10-12)

- 고정 시점 T에 Identity DB에 남아 있는 모든 WITHDRAWN User 기록이 대상이다.
- 탈퇴 event 원본이 보존기간 경과로 없어졌어도 User tombstone이 남아 있으면 snapshot에 포함된다.
- tombstone까지 물리 삭제됐거나 DB 복원으로 소실됐다면 snapshot으로 복구할 수 없다. journal 보존기간과 tombstone 보존을 동일시하지 않는다.
- Identity는 현재 탈퇴 코드가 User를 tombstone으로 남기며 확인한 코드에서 자동 물리 삭제 경로를 찾지 못했다고 회신했다. 상대 코드 조사 보고이며 무기한 보존·운영 데이터 완전성 보장은 아니다.
- count/digest는 제공된 snapshot의 무결성만 검증한다. 사라진 과거 기록이 포함됐음을 증명하지 않는다.

따라서 event 소실/tombstone 잔존은 snapshot 재동기화 대상으로, tombstone 소실은 snapshot만으로 해결할 수 없는 한계로 구분한다. 목록 부재로 계정을 ACTIVE로 되돌리거나 미구매/삭제 가능을 판단하지 않는다. 과거 이력 불명 최소 표시와 현재 동기화 상태를 분리한다.

1. Identity202가 탈퇴·journal·순번·delivery를 원자 저장하고 실시간 push를 재시도한다.
2. Billing201은 탈퇴를 반영한 Transaction commit 뒤204를 반환한다.
3. Billing204는 로컬 진행 위치 C 이후 feed를 주기 조회한다.
4. 원 event를 기존 inbox/applier로 적용하고 C와 작업 진행을 **같은 Transaction**에서 저장한다.
5. 외부 ACK 없이 다음 조회를 이어간다. 이미 push로 처리한 event도 중복 안전하게 통과하되 연속 순번만 전진한다.
6. H2까지 도달하면 이번 scan의 따라잡기 상태를 기록하고 다음 poll을 예약한다.

checkpoint가 없거나 신뢰할 수 없으면 고정 시점 snapshot을 검증·적용해 baseline H를 설치하고 H 이후 feed를 이어간다. snapshot을 매 poll마다 만들지 않는다. 탈퇴 계정을 살리거나 재가입자에게 유료권을 이전하지 않으며 무료 owner 정책도 불변이다.

### 2.2 책임과 줄어드는 부분

| 작업 | 책임 |
| --- | --- |
| TMI-199 | 기존 event/canonical/numeric fixture 재사용; 바뀌는 API/index는 새 revision fixture로 분리 |
| TMI-200 | ACTIVE MEMBER 구매 권한·발급/탈퇴 경합·기존 token/drain 보호 |
| TMI-201 | Billing 실시간 수신/계정 차단; 공통 applier와 local fence 연결 |
| TMI-202 | durable capture·push·원천 cleanup; consumer 증거 port는 새 책임에 맞춰 개정 필요 |
| TMI-203 | Identity snapshot/feed와 source availability; consumer 완료 상태는 소유하지 않음 |
| TMI-204 | Billing local checkpoint/epoch/coverage·복구 worker·복원 보호 |
| TMI-205 | Billing 로컬 복구 증거와 거래 확인을 사용한 조건부 purge |
| TMI-206/PLAN-013 | 실제 연동·복원·판매/삭제 활성화 gate |

Identity의 recoveries/checkpoints/checkpoints-status 3개 API와 consumer control/operation/checkpoint 저장소를 새 계약에서 제거한다. Billing의 ACK worker/outbox·remote confirmation·별도 scan collection도 제거한다. source stream/run 보호는 그대로다. IAM 인증이나 기존 push 성공 응답을 없애지 않는다.

### 2.3 최초 결제 출시의 기존 대상 없음 — 2026-10-12 승인

사용자는 기존 구매 계정·purchaseAccountRefId 발급·실거래가 없다고 확인했다. 운영 DB/Store 독립 조사 결과는 아니다.

- 과거 전체 legacy COMPLETE를 최초 출시 필수 조건으로 요구하지 않는다. 과거 PARTIAL/UNKNOWN을 사실대로 두고 과거 증명만을 위한 무기한 재시도를 하지 않는다.
- 시작 전에 capture, ACTIVE MEMBER 발급 fence·기존 token/요청 drain,201 수신,203/204 초기 baseline·feed·로컬 epoch 연동을 준비한다.
- 결제 시작 기준점은 환경/stream/commit 순번·관측시각·capture 이관 근거로 기록한다. 벽시계 시각만으로 commit 경계를 대체하지 않는다.
- snapshot에서 기존 탈퇴자를 빼거나 빈 목록을 가정하지 않는다. 기준점 이후 실제 누락·복원 증거 소실을 이 예외로 통과시키지 않는다.
- 기존 발급/거래가 발견되면 영향 범위를 재평가한다. 판매 활성화와205 삭제 승인은 별도다.

### 2.4 상태와 이벤트 이력은 다르다

로컬 모델은 아래 의미를 분리한다. 필드명은 wire가 아닌 설계 용어다.

- verifiedStateBaseline: 검증된 snapshot의 stream/T/H 및 최소 완료 증거. **H 이전 원 event를 모두 수신했다는 증거가 아니다.**
- verifiedFeedRange: 해당 baseline 이후 실제 원 event를 연속 적용한 시작/끝 순번.
- C: 다음 feed를 읽을 위치. baseline 설치 직후 H일 수 있으므로 C 이하 전체를 원 event 처리 완료로 간주하지 않는다.
- historical uncertainty/legacyCoverage: 과거 원사건 이력 불명의 최소 표시. 상세 gap 배열을 누적하지 않으며 새 snapshot 성공이나 C 상승을 과거 전체event 확인으로 해석하지 않는다.

유효한 로컬 증거가 남아 있는 이미 적용 구간의 정상 source 만료는 그 증거를 무효화하지 않는다. 반대로 필요한 미적용 구간의 만료는 GAP다. snapshot 상태 수렴과 원사건 이력 복원을 혼동해205 삭제를 허가하지 않는다.

## 3. 사용자가 결정해야 하는 사항

2026-10-12 추가 확인: 사용자는 Billing 내부 5개 collection 유지와 필드/상태/index 명세 방향, 단순 지연은 정상 이용을 유지하고 복원·control 소실 시 관련 쓰기를 막되 승인 복구를 허용하는 방향을 승인했다. 이어 §2.0의 snapshot 중심 재동기화·과거 이력 불명 최소 기록을 승인하여 상세 GAP 배열/상한 설계를 대체했다. 정확 local schema/전이표 작성은 남아 있으며 구현 중단 상태를 유지한다.

상품·가격·탈퇴 후 유료권 미이전·15일 조건부 정리·원천120일·snapshot7일·operation120일·현 stream 최소 증거/폐기 stream 종료 후120일·token30분/skew60초는 재결정하지 않는다. 방향 선택은 이번 요청으로 계획에 반영했다.

구현 전 양 서버가 동결할 기술 사항:

1. 주소는 사용자 미구현 전제에 따라 예정 `/internal/v1/billing/withdrawals`를 유지한다. 새 문서/fixture revision은 별도로 동결한다. 실제 기존 consumer가 발견되면 그 영향 범위만 재검토하며 운영 미배포를 독립 검증했다고 주장하지 않는다.
2. availability의 정확 필드·null·오류·상한·관측 일관성과410 응답에서도 필요한 재생 범위를 얻는 방법.
3. 아래 로컬 모델/index·최소 operation 보존과205 증거 소비 규격. retry/circuit 초기값은 §7의 사용자 채택값을 사용하고 실제 부하·공동 호환성을 검증한다.
4. AWS SDK v2 signer의 정확 module/version·credential provider·HTTP client. 명시 승인 없이 새 운영 의존성을 추가하지 않는다.

담당자·알림·Identity trusted 주소·현재 streamId·task/운영 역할은 운영 입력이며 실제 값/Secret은 문서에 넣지 않는다. 신규 public repair API는 만들지 않는 안이다.

## 4. 주요 위험과 미확인 사항

| 위험 | 대응 |
| --- | --- |
| Identity203 지원/배포 미확인 | fake 개발과 실제 연동 완료를 구분; 새 exact 동결 전 production adapter 구현 금지 |
| Billing이 정지해 consumer lag 경보도 멈춤 | Billing 외부 독립 heartbeat/watchdog와 Identity 원천 만료 경보 |
| snapshot live scan 대체 | 이번에는 하지 않음. 고정 T 실현성/history-loss spike 유지 |
| 선택적 DB 복원으로 checkpoint만 앞섬 | lifecycle/account/inbox/control의 일관 복원 단위 확인, 불명하면 C 신뢰 중단 |
| 복원된 epoch와 옛 worker | lease만 믿지 않음. 구프로세스 DB 접근 제거·격리·새 local epoch |
| control 쓰기 경합 | 기존 PaymentStore2초 budget·page20·account/push 부하 검증; 임의 timeout 확대/partition 금지 |
| source gap 정보 크기 증가 | 범위·상한을 동결, silent truncation 금지. 모든 과거 interval을 매 응답/단일 문서에 무한 누적하지 않음 |
| 누락 계정 특정 불가 | 삭제 보류 범위가 넓을 수 있음. 모든 계정의 개별 안전성을 추측하지 않음 |
| 증거 보존을 위한 무기한 개인정보 저장 | 기존7일/120일 정리 유지; 미해결만으로 연장하지 않음 |

## 5. 구현 설계

### 5.1 코드 경계와 공통 적용

[PaymentWithdrawalService](../../src/main/java/web/tosunsaeng/billing/domain/payment/application/PaymentWithdrawalService.java)의 적용을 같은 ClientSession을 받는 PaymentWithdrawalApplier로 추출한다. [PaymentAccountService](../../src/main/java/web/tosunsaeng/billing/domain/payment/application/PaymentAccountService.java)의 발급/탈퇴 lifecycle CAS는 유지하고 공통 recovery fence를 연결한다. 기존 domain/payment 구조와 public DTO를 유지한다.

- push: local fence → eventId/digest/inbox → applier → commit →204.
- snapshot: fence/lease → fact 적용 → 최소 staging/page receipt/진행 → 같은 commit. 가짜 eventId나 push inbox 생성 금지.
- feed: fence/lease → 원 event strict decode·기존 inbox/applier → 연속 checkpoint/작업 진행 → 같은 commit.
- account: fence → 기존 lifecycle/account/ref/cursor 처리. barrier/control 부재를 ACTIVE로 취급하지 않는다.
- DUPLICATE/NOOP도 control CAS 쓰기 경계를 통과한다. epoch 읽기만 하고 나중에 쓰지 않는다.
- 외부 HTTP와 credential network lookup은 Transaction 밖에서 수행한다. 늦은 결과는 요청 시 캡처한 epoch/job/lease version으로 검증하고 옛 결과에 새 epoch를 붙이지 않는다.
- snapshot 일부 적용 뒤 최종 검증이 실패해도 WITHDRAWN을 ACTIVE로 되돌리지 않는다. baseline 완료만 금지한다.

클래스 후보: PaymentRecoveryFence, WithdrawalSnapshotWorker, WithdrawalFeedWorker, WithdrawalRecoveryInstaller, WithdrawalRecoveryRetentionWorker, PaymentRecoveryRepository/IndexInitializer, IdentityWithdrawalRecoveryClient/SigV4 adapter, RecoveryProperties/관측. **WithdrawalAckWorker와 CheckpointStatus remote 모델은 만들지 않는다.**

### 5.2 로컬 control·epoch·barrier

Billing이 epoch UUID·version·barrier·작업 lease·baseline/연속 적용 범위·C·coverage를 소유한다. source streamId는 Identity의 원천 세대이며 local epoch와 다르다.

| 상황 | 처리 |
| --- | --- |
|204 도입 전 | 기존201 유지, 복구 OFF; recovery-ready 아님 |
|fence-required인데 control 없음/설치 중 | 영향 account/push 쓰기503, worker 정지; 임의 control 생성 금지 |
|local epoch 설치 후 초기화 | 검증된 snapshot 적용 허용; 최초 구매 활성화는 준비 gate 충족 후 |
|정상 poll/단순 lag | 기존201 인증·탈퇴 차단 유지, local C부터 자동 처리; 매 구매마다 CAUGHT_UP 강제 안 함 |
|GAP/계약 오류 | 불명 범위 보존·자동 정상화 금지, 영향 차단 범위 평가 |
|restore/source 교체 | 영향 구매 쓰기·purge barrier, old worker 격리 후 local epoch 설치/복구 |

worker 재시작·timeout·cursor 만료는 같은 epoch에서 재개한다. 새 epoch는 초기화/복원/명시적 재동기화의 운영 도구에서만 설치한다. barrier는 설치/복원을 위한 것이며 모든 RECOVERING 상태의 페이지 쓰기를 금지하는 플래그가 아니다. 구 writer와 fence-required writer 혼합 배포를 금지한다.

### 5.3 Identity client와 새 exact 동결 경계

최초 구현 prefix는 `/internal/v1/billing/withdrawals`이며 POST snapshot 생성/status/page, feed/page의4개다. 별도 v2 URL은 추가하지 않는다. 기존199과 다른 body/header/응답은 새 문서 revision으로 구분하고 과거 fixture를 덮어쓰지 않는다.

| suffix | 기능 |
| --- | --- |
| /snapshots | body {streamId}, durable Idempotency-Key로 생성/재조회, 신규 BUILDING202·기존 종료/READY200 의미 유지 |
| /snapshots/status | READY에만 완전한 manifest |
| /snapshots/page | immutable snapshot 페이지·nullable cursor·done |
| /feed/page | stream/afterSequence/nullable scanCursor로 H2 고정 feed와 source availability |

새 계약에는 /recoveries, /checkpoints, /checkpoints/status 호출이나 X-Consumer-Recovery-Generation을 넣지 않는다. Identity snapshot operation은 unique(environment,consumer,requestOperationId)이며 streamId는 canonical 요청 binding으로 따로 비교한다. 같은 key/new stream은 OPERATION_CONFLICT, 동일 key/body의 inactive stream은 STREAM_REPLACED로 구분하는 새 exact 후보를 따른다. local epoch는 인증 수단이나 Identity authority가 아니다. snapshot 생성은 DB run을 만드는 POST다.

Lattice AWS_IAM/SigV4, role/환경/route allowlist, unsigned/JWT fallback 금지를 유지한다. 요청 bytes/header/traceparent를 확정한 뒤 최종 서명하고 baggage는 전파하지 않는다. trusted base URL만 사용하며 redirect NEVER, cursor를 URL로 해석하지 않는다.

strict decoder는 duplicate/unknown/trailing/coercion, 필수-null/생략, 잘못된 UUID casing을 거절한다. D0/D1 canonical decimal→BSON int64, T의 U32 pair→BSON Timestamp, UUID userId는v4로 제한하지 않는다.16KiB·page최대20을 유지한다. manifest binding은 digest 외에도 별도 확인한다.

### 5.4 snapshot 적용

1. local job/epoch와 멱등 key를 먼저 저장한다. 같은 run 응답 유실은 같은 key로 재시도한다.
2. READY의 stream/snapshot/T/H/count/digest/expiry/source gap metadata를 검증·불변 저장한다. 원격 generation은 없다.
3. 페이지의 userId ASCII 순서·중복/역순·cursor 진전·done/null·count/overflow를 검증한다. wire에 임의 pageIndex를 추가하지 않는다.
4. 최대20개 적용·staging·receipt·다음 cursor를 같은 Transaction으로 commit한다. commit 불명은 durable receipt/진행을 다시 읽는다.
5. EOF 뒤 bounded staging keyset으로 canonical digest를 재계산한다. userId→withdrawnAt JSON, UTF-8 길이 unsigned32 big-endian prefix, SHA-256 규칙을 재사용한다. empty snapshot page1개/zero-byte hash, 고정밀 시각 golden도 유지한다. MessageDigest 내부 직렬화 금지.
6. 마지막 bounded Transaction에서 epoch/run/version·expiry·count/order/hash 확인과 SNAPSHOT_APPLIED marker·state baseline H·feed 시작점을 함께 저장한다. 원격 BASELINE ACK는 없다.
7. 초기 bootstrap·승인된 source 재동기화에서 baseline을 설치한다. stale job/epoch와 진행점 역행을 거절하고 과거 이력 불명 최소 기록을 유지한다. 필요한 원본소실 뒤 snapshot 재동기화는 §2.0을 따른다.

snapshot 실패/expiry의 일시 원인이면 같은 논리 job에서 **새 key의 새 run**을 bounded budget 내 자동 예약한다. 같은 key가 새 run을 만들게 하지 않으며 기존 실패를 지우거나 매번 epoch/운영 승인을 요구하지 않는다. privacy 사본 만료는 연장하지 않는다.

source expiry와 local 시작+7일 중 빠른 시점 안에 최소 userId/정확 withdrawnAt staging을 정리한다. raw HTTP/JSON 전문 저장 금지. live scan fallback 금지. digest 실패 상태의 partial 적용을 전체 완료로 표시하지 않는다.

### 5.5 feed·availability·commit 불명

- 검증된 baseline 또는 일관 복원된 연속 진행 C에서 scan을 연다. H2와 cursor binding은 scan 동안 불변이다.
- 새 exact 후보의 검사 끝 E=C+min(20,H2-C)를 사용한다. (C,E]에 hole/논리 expiry가 있으면 전체 오류이며 C는 그대로다. C==H2에서만 빈 완료를 허용하고 이때 C+1을 계산하지 않는다. D1/overflow·H2 상한·done·nextAfterSequence/scannedThrough와 availability 검사 범위를 확인한다.
- page 전체 inbox/applier/진행을 같은 Transaction으로 commit한다. conflict면 checkpoint 포함 전체 rollback. push 선행 NOOP도 연속성을 확인한 뒤 진행한다.
- unknown commit은 DB의 epoch·C·receipt로 수렴한다. 외부 ACK/outbox/원격 checkpoint 재조회는 없다.
- cursor1시간 만료 시 마지막 committed C부터 새 scan을 연다. 이전 H2 완료를 주장하지 않는다. H2 이후 신규 탈퇴는 다음 poll 대상이다.
- feed로 적용했더라도 Identity delivery를 임의 DELIVERED로 바꾸지 않는다.202 push 성공 응답으로만 그 상태가 전진한다.

source availability exact 동결 요구:

- stream, 관측 목표/범위, replayFloor, 필요한 unavailable interval, 관측시각/버전을 journal과 일관되게 읽는다. 관측시각만으로 순번 완전성을 추론하지 않는다.
- 조회 범위에 필요한 gap을 정확히 전달하고 prefix floor와 중간 hole을 구분한다. 관련 범위 밖의 과거 interval 전체 반환은 요구하지 않는다.
-16KiB/interval 상한과 초과 시 오류·복구 정보 취득 경로를 명세한다. gap을 조용히 자르거나 빈 성공으로 바꾸지 않는다.
-410에서도 현재 stream/재생 가능 범위 등 최소 진단을 얻을 응답 또는 별도 읽기 경로를 합의한다. 아직 승인되지 않은 API를 임의 추가하지 않는다.
- C와 원사건 적용 범위/상태 baseline을 함께 비교한다. 이미 검증된 범위의 정상 만료와 필요한 미적용 구간 GAP을 구분한다.
- Identity cleanup은 원천 삭제와 floor/interval을 원자 기록하며 Billing ACK를 기다리지 않고 기존120일을 지킨다. consumer 적용 판정은 Billing으로 이동한다. 기존202 Evidence port/monitor/gate의 변경이 선행 조건이다.
- source 저장 방향은 replayFloor+별도 중간 range 문서다. 인접/겹침만 병합하고 연속 prefix만 floor로 편입한다. 정상 가용 구간을 건너뛰지 않는다. batch/경합/상한/retired stream 정리 및 추가 index는 Identity203에서 동결하며 Billing이 source 컬렉션을 직접 읽거나 수정하지 않는다.
- wire는 성공 page 범위 availability,410 최초 missing/range1개,409 확정 hole의 range=null,503 details 생략 방향이다. 전체 interval 목록을 요구하지 않으며 고정필드/오류명·message/429는 새 exact에서 동결한다.

### 5.6 복원과 운영 도구

운영 도구는 dry-run/상태 확인을 기본으로 하고 exact CLI/권한은 runbook에서 동결한다. 일반 worker에 epoch 설치 권한을 주지 않는다.

1. 영향 구매 writer·recovery·purge를 barrier로 막고 진행 요청/Transaction을 drain한다.
2. 구 Billing 프로세스의 DB 접근을 제거하고 복원 DB를 격리한다. source 복원이면 옛 Identity endpoint/DB도 격리한다.
3. 별도 운영 권한으로 새 local epoch를 CAS 설치한다. 동시 설치의 한 요청만 성공하고 실패 중 worker는 OFF다.
4. lifecycle/account/ref/inbox/cleanup 후보/control 및 관련 진행 증거가 같은 일관 복원 단위이고 같은 source stream/원천 가용 범위가 충분하면 검증된 C부터 replay한다. 옛 in-flight job/cursor는 새 epoch로 복사하지 않는다.
5. 선택적 복원·증거 불명·stream 교체면 새 snapshot 기반 복구로 전환한다. 과거 GAP/소실을 새 기준점으로 지우지 않는다.
6. 적용·발급/탈퇴 보호를 확인한 범위만 barrier 해제한다. remote ACK나 원격 세대 설치는 없다.

서로 다른 stream의 순번 비교 금지. 정상 failover와 backup restore를 구분한다. 복원된 epoch 값만으로 현재 안전을 증명하지 않는다.

## 6. 영속 모델·보존·index 후보

구7개 제안에서 ACK jobs/scans를 제거하고 operation 진행은 control/job에 통합한다. **기본4개 + 최소 operation/폐기 stream 보존용1개, 총5개 후보**로 한다. 감사 보존을 control의 무한 배열로 합쳐 수량만 줄이지 않는다. 정확 validator/index는 공동 동결·PR1 검증 대상이며 기존 Billing16개 index에 포함되지 않는다.

| collection | 책임 | index 후보: key 순서 |
| --- | --- | --- |
| payment_recovery_controls | 환경/consumer별 현재 epoch/version/barrier, baseline/feed range/C/coverage, 현재 job·lease/H2/cursor·retry | ux_payment_recovery_control: environment,consumer unique |
| payment_recovery_snapshots | run/멱등 key·manifest·단계·검증 marker·expiry | ux_payment_recovery_snapshot: environment,consumer,epoch,snapshotId unique; ux_payment_recovery_snapshot_operation: environment,consumer,operationId unique; ix_payment_recovery_snapshot_due: state,nextAttemptAt,_id |
| payment_recovery_snapshot_rows | 최소 fact와 run 귀속 | ux_payment_recovery_snapshot_user: environment,consumer,epoch,snapshotId,userId unique; ix_payment_recovery_row_expiry: expiresAt,_id |
| payment_recovery_pages | bounded receipt·request binding/checksum·commit 범위;원문/사용자 배열 없음 | ux_payment_recovery_page: environment,consumer,epoch,kind,runId,pageIndex unique; ix_payment_recovery_page_expiry: expiresAt,_id |
| payment_recovery_operations | 최소 운영 설치/종료 결과·실패 기록·폐기 stream 증거, worker 진행의 별도 authority 아님 | ux_payment_recovery_operation: environment,consumer,operationId unique; ix_payment_recovery_operation_retention: retentionExpiresAt,_id |

- 후보 index는 방향1, sparse=false, simple collation, partial/TTL 없음. snapshotId 발급 전 local run에는 이 unique key에 nullable snapshotId를 넣지 않는다. 생성 요청은 control/job에 저장하고 remote ID 수신 후 run을 삽입하는 경계를 validator와 함께 동결한다.
- 모든 sequence/version/count/pageIndex는 BSON int64, 증가 전 overflow 검사. checkpoint 없음과0을 구분한다. core v4/기존201 index는 임의 변경하지 않는다.
- snapshot staging/cursor/digest는7일 이내 또는 더 이른 source expiry, scanCursor는1시간 이후 사용 금지/단기 정리. 장기 marker에 userId/raw cursor/digest를 복제하지 않는다.
- operation 최소 metadata120일, 현 stream 최소 baseline/coverage 유지, 폐기 stream 종료 후120일. 종료/실패 operation 보존과 현재 job을 분리하되 개인정보 원문을 보존하지 않는다.
- 상세 unresolved interval 누적/개수 상한은 §2.0 승인으로 대체한다. 현재 진행과 과거 이력 불명 최소 표시를 분리하고 작업 이력은 기존 최소 operation 보존으로 제한한다. 과거 원event 전체 수신이라는 false COMPLETE를 만들지 않는다.
- 명시적 bounded cleanup과 lease/version CAS로 정리한다. source/worker OFF도 개인정보 expiry를 무기한 연장하지 않는다.204 임시자료 정리와205 구매 계정 삭제는 별개다.
- initializer는 key 순서/unique/partial/TTL/collation을 exact 비교하고 drift 시 fail-fast. 실행 중 drop/recreate하지 않는다.

## 7. 실패·flag·관측

### 7.1 채택한204 초기 운영값

2026-10-12 사용자 승인 반영. 기존202 publisher20회/관리 재개와205 매시간·매일·7일 정책은 변경하지 않는다. 수치는 환경 설정의 계획 기준이며 아직 실행하지 않았다.

| 항목 | 초기값/처리 |
| --- | --- |
| connect/request timeout |3초/5초 |
| feed 정상/빈 scan |caught-up 뒤5초±20%;처음부터 빈 scan 완료 뒤30초±20% |
| catch-up 실행 한도 |한 job만 실행,매page commit 후계속.한 실행 최대10page 또는30초에서양보하고1초뒤재개 |
| page/lease |최대20건,lease60초+version CAS,필요시20초마다갱신.시간만으로쓰기권한판정금지 |
| 일시 오류backoff |실패n의상한 min(60초,2^(n-1)초),대기 max(1초,0~상한균등jitter).지수overflow방지 |
| 실패budget |같은논리job20회연속실패.영속dispatch marker뒤crash도1회.유효응답·필요local commit성공뒤연속횟수초기화,누적장애기록유지 |
| circuit/probe |소진뒤같은job5분±20%마다1회.새operation/epoch로budget우회금지 |
| probe복구 |응답계약과local상태확인/적용commit성공뒤자동복귀.재시작만으로circuit해제금지 |
| snapshot BUILDING |Retry-After:5 기준5초poll.일시전송실패와분리,5분build budget초과지속경보.유효상태확인없이새run중복생성금지 |
| 새 snapshot run |확정FAILED/EXPIRED·일시원인에한해최초+추가2개/rolling24시간.추가대기1분,5분.각run새key·같은epoch/job·각run원래expiry유지 |

일반일시 network/408/425/429/5xx에만위자동재시도를적용한다. 신뢰가능한500 RESPONSE_LIMIT_EXCEEDED/retryable=false는일괄5xx보다우선해중단/경보한다.401/403·strict/계약오류·operation충돌은담당조치후재개,확정GAP/원천소실은동일요청probe를계속하지않는다. CURSOR_EXPIRED는C에서새scan,STREAM_REPLACED는운영source확인/새baseline이다. local stale epoch/lease는CAS로거절하며원격checkpoint/generation오류는구현하지않는다.

Retry-After 정수초/HTTP-date는backoff/probe보다앞당기지않고유효값을상한으로잘라일찍호출하지않는다.24시간초과는같은job의notBefore까지보류하고경보,원천만료위험별도평가.무효/모호한다중값은원문비로깅·기본backoff·계약경보,overflow도무효처리한다.시각해석은clock gate전제다.

BUILDING 성공poll은통신실패를해소하지만전체build기한/새run횟수를초기화하지않는다. hash/계약오류·원인불명 BUILD_FAILED·반복GAP은자동run재생성대상이아니다.새run budget소진은담당조치이며느린probe로새run을무한발급하지않는다.정상retry/자동probe회복은개별운영승인을요구하지않는다.

### 7.2 기능 스위치와 적용 범위

후보: billing.payment.recovery.fence-required, snapshot-enabled, feed-enabled, retention-enabled 모두 기본false. **ack-enabled는 제거**한다. 기존 public/withdrawal flag는 독립이다.

| 상황 | 서비스/복구 |205 삭제 |
| --- | --- | --- |
| 과거 legacy 불명만 존재 | 최초 출시 한정 예외 유지 | 후보의 거래·탈퇴 근거 별도 |
| 정상 network 지연·상태 유실 없음 | 자동 retry, 단순 lag로 전체 구매/무료 차단 안 함 | 증거 부족 후보 보류 |
| 인증/계약 오류 | 빠른 반복 중단, 실제 차단 기능 손상 범위 평가 | 영향 범위 보류 |
| 실제 GAP/원천 소실 | 가능한 snapshot 상태 복구·공백 기록, 영향 구매 제한 평가 | 특정 불가면 넓게 보류 가능 |
| restore/control 소실 | 영향 writer barrier·격리·새 epoch·근거 기반 재개 | 복원/거래 대사 전 보류 |

worker ON은 index/transaction/fence/trusted 설정과 exact 호환성이 선행한다. fence 도입 시201 writer drain/설치가 필요하다. FEED 시작 조건은 로컬 baseline/검증된 C이며 remote 확인은 없다. token 발급 중단만으로 기존 token 무효화를 가정하지 않는다. 무료/Identity 전체 health를 recovery lag에 자동 결합하지 않는다.

### 7.3 관측

service/environment/operation(snapshot,feed,apply,install,cleanup)/outcome/durationMs/traceId/spanId 유지. userId/ref/payload/cursor/digest/SigV4 credential 비로깅, stream/epoch/runId는 metric tag 금지.

마지막 성공 poll·checkpoint lag·heartbeat·원천 만료 위험·UNKNOWN/GAP·BLOCKED_AUTH·circuit·stale epoch·lease 재획득·임시자료 overdue 관측. remote ACK lag는 제거한다. Identity는 source 가용성/expiry를, Billing은 consumer lag를 담당한다. Billing 중단도 감지할 외부 watchdog을 실제 배포 gate에 포함한다.205의 매시간/매일/7일 검토 기준을 모든 feed 오류의 수동 승인으로 확대하지 않는다.

| 신호 | 채택 초기 기준 |
| --- | --- |
| heartbeat |60초마다,외부watchdog가5분미수신경고/15분긴급.의도적OFF/승인정비구분 |
| feed 무성공 |활성상태마지막유효응답5분경고/30분긴급 |
| backlog 미진행 |H2>C인데C가5분간미진행경고/30분긴급.빈feed제외 |
| circuit·auth·계약·확정GAP·stream교체 |전환/발견시즉시담당알림.실제손상범위평가와barrier/삭제보류분리 |
| source 만료 |Identity가capturedAt/expiry기반7일전경고/24시간전긴급.정상source정리를consumer장애로단정금지 |
| 임시자료 overdue |expiry후첫정리주기에도잔존하면경보 |

동일환경/서비스/job종류/원인별최초1회,지속시1시간마다,등급상승즉시,해소1회로중복억제한다. userId/runId/epoch를metric label/외부중복키로쓰지않는다. 주담당Billing백엔드운영자,Identityauth/source공동담당,외부watchdog인프라담당으로분담하되실제성명·대체자·수신채널은활성화전지정한다.

현wire에capturedAt이없으므로withdrawnAt/sequence를원천expiry시각으로대체추정하지않는다.정확consumer별만료위험은Identity원천지표와Billing진행을운영에서대조한다.일반worker와epoch설치/barrier해제운영권한은실제도구·DB접근제어로분리검증한다.

## 8. 구현 순서

2026-10-12 추가 구현: [snapshot 영속 처리 안내](../runbooks/TMI-204-snapshot-processing.md). 나머지 4collection/9index와 control version2 진행필드, durable operation·lease·페이지 원자 적용·전체 hash 검증·baseline 설치·만료 batch 정리 코어를 추가했다. 자동 scheduler/실HTTP/feed/실패 후 새 run budget/120일 정리는 미구현이며 PR2 전체 운영 완료는 아니다. 기존 중단/미구현 표기는 이 항목의 명시 범위에 한해 갱신한다.

2026-10-12 재개 구현: [PR1 제어기반 규격/검증 안내](../runbooks/TMI-204-recovery-foundation.md). 기존 push applier 추출·snapshot fact 경계, 성공/오류 strict decoder, fakeable client port, ACCOUNT/PUSH/RECOVERY epoch/version CAS와 기본OFF flag, control header v1/index/validator 검증을 추가했다. 5collection 중 control header만 이번에 명세/구현하며 snapshot/rows/pages/operations·control 진행필드는 후속 명시 migration으로 추가한다. 자동복구worker·실HTTP·운영installer/retention·활성화는 없다. 기존 문서의 중단 표기는 이 사용자 재개 요청으로 해제하되 미구현 범위를 완료로 보지 않는다.

2026-10-12 Phase0 진행: [Billing 독립 검증](../contracts/TMI-204-RECOVERY-R1-INDEPENDENT-VALIDATION.md)에 최초 결과와 scalar-end-1 재검증을 기록했다. 끝 개행 차이 해결, 정상8/실패264 및 전체724개 통과·새hash 일치에 따라 해당 공유 보완본을 Billing 기술 수락한다. local schema/index·공동 계약 본문 정리 등 Phase0 전체 완료나 PR1 운영 코드 착수/실배포 완료와 구분한다.

| 단계 | 산출물/완료 기준 |
| --- | --- |
| Phase0 공동 개정 | 신규 revision/availability/error/index·구배포 호환 동결, T/H 실현성 확인,202 cleanup port·205/206 책임 이동 수락 |
| PR1 기반 | fake client/strict decoder·신규 schema·공통 applier/control CAS, 기존201 회귀·기본OFF |
| PR2 snapshot | bounded 적용/staging hash/로컬 baseline, expiry/재시작/실패 후 새 run 검증 |
| PR3 feed | inbox와 C 원자 commit·H2·availability/GAP·자동 retry/circuit; 원격 ACK 없음 |
| PR4 운영 | local epoch installer·복원/격리 runbook·retention·readiness/관측·승인된 SigV4 adapter |

코드 구현/신규 의존성/실제 이관·판매·삭제 활성화는 이번 계획 수정에 포함하지 않는다. Identity203 저장소나 Jira를 자동 수정하지 않는다.

## 9. 테스트와 완료 기준

- 기존4필드 UserWithdrawn·canonical digest/numeric39개/Long.MAX_VALUE·overflow·D0/D1/U32 회귀. 새 revision fixture는 기존199 파일을 조용히 덮어쓰지 않는다.
- snapshot golden: empty/page1, UTF-8 prefix, 고정밀 시각, 페이지 분할과 무관한 digest, metadata mismatch 별도 거절.
- strict unknown/duplicate/trailing/coercion·nullable·16KiB/page20·서명 bytes/traceparent 순서·baggage 미전파·wrong role/환경/direct bypass/redirect.
- replica-set: epoch 설치와 account/push/page/NOOP/marker의 CAS 경합; page 일부 실패 rollback; commit 불명·crash 후 C/receipt 수렴.
- push 선행/후행/중복 feed·eventId payload conflict에서 checkpoint 진행 금지. snapshot에는 가짜 inbox 없음. 무료/owner/paid 미이전 불변.
- snapshot partial/count/order/digest/expiry/history 실패에 baseline 금지; cleanup과 검증 경합; 자동 새 run의 key/실패 보존·기간 비연장.
- H>0 baseline은 H 이전 event 수신 증거 아님. event 소실/tombstone 잔존 시 snapshot 적용→H 이후 feed 재개를 검증하고 과거 이력 불명 최소 표시를 유지한다.
- tombstone 소실 시 snapshot 부재를 ACTIVE/과거 완전성/삭제 안전성으로 오인하지 않는다. count/digest가 정상이어도 과거 소실을 복원했다고 표시하지 않는다.
- 새 snapshot 검증 실패 시 새 baseline 금지, 성공 후 이전 job/cursor 결과 거절. 상세 gap 배열을 누적하지 않으며 일시503과 원본소실의 복구 경로를 구분한다.
- H2 이후 commit은 다음 scan, C<H2 빈 page/불연속/overflow 거절, cursor expiry·availability410/상한 초과·관측 경합.
- source 만료 prefix/중간 hole, Billing 중단 뒤 재개, feed로 적용 후 push204 수렴(Identity delivery 임의 변경 없음).
- Billing 일관/선택적 복원·Identity stream 교체·old worker·옛 endpoint 격리·동시 epoch 설치/실패. fault 주입과 실제 응답 유실 실험 결과를 구분한다.
- 가짜시계로5초/빈30초·catch-up10page/30초·20회실패후5분probe·lease60/갱신20·추가snapshot2run/24시간을검증한다. 긴Retry-After의조기호출금지·500nonretryable·BUILDING실패분리·중복경보/해소·heartbeat/watchdog경계도포함한다. 정상지연건별승인/일괄차단없음,실제restore/GAP우회없음.
- index/validator drift·unique 경합·7일/120일 cleanup·기존201 public/internal 회귀. 모든 구현 후 ./gradlew clean test.

로컬 완료는 코드/manifest/runbook·fake 계약·replica-set 검증과 작업 기록까지다. 실제203 지원·고정T/history window·운영 동등 DB·IAM/부하·복원/이관 시간·watchdog는206에서 별도 검증한다. 판매/삭제 활성화는 별도 승인이다.

## 10. 공동 계약 변경·인계 체크리스트

- 이 계획은 Billing 단일 authority를 채택하지만 공동 exact 재동결을 완료했다고 표시하지 않는다.
- Identity203: 원격3개 API/consumer generation·저장소 삭제, snapshot 멱등 scope·cursor binding·availability 개정. 구 API에 가짜204를 반환하지 않는다.
- Identity202: capture/push는 유지, cleanup Evidence/monitor·PurchaseActivationEvidence에서 remote consumer 의존 제거. boolean true로 gate를 대체하지 않는다.
- Billing204: local epoch·C·baseline/feed range,5개 후보 schema·retry·source 관측 규격 동결.
- Billing205: remote ACK 아닌 해당 계정의 탈퇴 사실·거래/환불·보존/동시성 증거를 확인한다. 과거 전체event 불명만으로 전계정 영구 보류하지 않으며 계정별 필수 증거 부족은 보류한다.
- IAM/206:4개 복구 기능 allowlist·운영 epoch 설치 권한·구프로세스 격리·현재 stream 운영 전달. discovery API 임의 추가 없음.
- 기존199 identity index13개 중 remote3개 제거·snapshot operation index 변경 및별도source range 추가index는 **새 manifest**에서 정의. 이전10개예상총수/이름의v2표기는확정기준아니다. 배포 여부 조사 없이 기존 index drop 금지.
- Identity 인계: Billing 원본소실→snapshot 상태수렴→H 이후 feed와 과거 불명 최소표시를 공유한다. recovery-r1/scalar-end-1·4route·T/H·count/digest/expiry·source floor/range·보존계약 변경 요청은 없다. snapshot 포함 범위는 §2.1 회신으로 확인했으며 실제 DB/API 검증은 별도다.
- 다음: 최소 local schema/재동기화 전이·구 공동본문 정합화 → 중단 PR1 재개 범위 확인 → 실제203 연동 및206 환경 gate. 본 갱신은 코드재개/배포승인이 아니다.
