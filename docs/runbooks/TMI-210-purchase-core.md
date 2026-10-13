# TMI-210 구매 접수·기간권 코어 구현

- 날짜: 2026-10-13
- 브랜치: `feat/TMI-210-purchase-ingestion-timeline-core`
- 상태: 내부 코어·로컬 검증 완료. 실제 provider/API/worker/배포 미연결. PLAN-010 전체 완료가 아니다.

## 1. 구현 범위

`PurchaseCore.receive`가 검증된 adapter의 최소 정규화 관측을 접수하고, `apply`가 별도 Transaction으로 처리한다. 이 둘을 호출하는 HTTP 경로나 Spring bean은 이번에 만들지 않았다. `PurchaseObservation` 생성 자체가 RevenueCat/Store 검증을 수행한다는 뜻이 아니다.

- 접수: environment/app/고정 keyVersion 확인 → 이벤트 digest 중복 확인 → 기존 ref/account/lifecycle 귀속 동결 → inbox와 pendingEvidence 증가를 함께 commit.
- 지급: transaction business key·원구매자·상품/시각/금액 확인 → 현재 ACTIVE/current ref와 OPEN 확인 → Purchase·기간권·timeline·원장·입력 완료/pending 감소를 함께 commit.
- 기간: 1/3/7/14/28일, successful 지급 commit 순서. `start=max(purchasedAt,timelineEnd)`, `[start,end)`이며 지연 수신을 현재 시각으로 보상하지 않는다. 판매 중단된 catalog도 과거 거래 매핑에 사용할 수 있다.
- 중복: 이벤트 unique와 불변 digest, 거래 unique, purchase별 entitlement 및 ledger effect unique. 금액 미상은 null이고 catalog 표시가격을 실제 결제액으로 채우지 않는다.
- 예외: 다른 owner/불변 사실 충돌·불명 상품/시각·탈퇴/inactive·미매핑은 review case. 다른 계정으로 이전하거나 삭제된 account/ref를 다시 만들지 않는다.
- `PURCHASE_BLOCKED`: 금융 사실은 기록하되 지급과 입력 완료를 보류한다. `ALL_BLOCKED`/이전 epoch/미설치 fence는 차단한다. 기존 무료/user/서비스 인증 계약은 변경하지 않는다.

## 2. 저장 규격

정확 BSON 필드·필수/null·index 이름/순서/옵션은 [PurchaseCoreSchema](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCoreSchema.java)의 `payment-purchase-core-r1` programmatic manifest다. 별도 JSON 복제본을 만들지 않아 이중 관리를 피한다. version/sequence/duration/catalogVersion/digestVersion/attempts는 int64, 시각은 BSON Date(ms), 금액은 Decimal128다. input은 raw receipt/token/provider JSON을 저장하지 않는다.

| Collection | 주요 unique/index | TTL |
| --- | --- | --- |
| purchases | ux_purchase_transaction(environment,store,appId,transactionKey); ix_purchase_account(accountId,purchaseState) | 없음 |
| subscription_entitlements | ux_entitlement_purchase(purchaseId); ux_entitlement_sequence(accountId,sequence); ix_entitlement_account_end(accountId,endsAt) | 없음 |
| payment_timelines | _id=accountId; ux_payment_timeline_account(environment,accountId) | 없음 |
| payment_ledger | ux_payment_ledger_effect(effectKey); ix_payment_ledger_purchase(purchaseId,occurredAt) | 없음 |
| payment_provider_inbox | ux_payment_provider_event(environment,providerEventKey); ix_payment_provider_due(state,receivedAt) | ttl_payment_provider_purge(purgeAt), expireAfterSeconds=0 |
| payment_purchase_cases | ux_payment_case_event(environment,providerEventKey); ix_payment_case_account(accountId,state); ix_payment_case_transaction(environment,normalizedObservation.store,normalizedObservation.appId,normalizedObservation.transactionKey) | 없음 |

명시 `installUnderMaintenance`는 writer 격리와 ALL_BLOCKED 전제에서 없는 collection만 설치한다. 기존 validator/index 불일치는 수정하지 않고 거절한다. 코어 생성 시 검증만 하며 startup DDL은 없다. 설치는 실제 운영에서 수행하지 않았다. 초기 설치 도중 실패한 불완전 schema의 복구는 별도 유지보수이며 자동 drop/recreate하지 않는다.

lookup은 길이 구분 UTF-8 tuple의 HMAC-SHA256으로 만든다. 거래/event domain을 분리하고 keyVersion은 business unique key에 넣지 않는다. 다른 version의 기존 자료가 있으면 코어 구성을 거절한다. **같은 version 문자열로 실제 비밀키만 교체하는 실수까지 자동 탐지하지는 않는다.** 키 교체는 writer 중단·이관·검증 후 재개가 필요하며 설정만 변경하면 안 된다. 실제 secret/provider 재조회 암호화 adapter는 이번 범위 밖이다.

## 3. 접수 중심 미처리 관리와 보존

| 전이 | source counted | case counted | pending 변화 |
| --- | --- | --- | --- |
| mapped 최초 접수 | true | 없음 | +1 |
| 정상 처리 완료 | false/APPLIED | 없음 | -1 |
| 검토 인계 | false/QUARANTINED | 기존 true 이전 | 0 |
| unmapped 접수→검토 | false | false | 0, 미매핑 자체는 별도 위험으로 유지 |

인계도 lifecycle.version을 증가시켜 이전 삭제 증거를 무효화한다. case unique는 inbox 만료 후 재전송도 막는다. 별도 범용 evidence effect collection은 만들지 않았다.

source의 retentionDeadline은 receivedAt+120일이다. 완료 또는 case 인계된 source만 purgeAt을 설정하고 RECEIVED에 purgeAt을 넣는 것은 validator가 거절한다. `handoffAged`는 119일 이후 명시 인계를 제공한다. 자동 worker·120일 초과 경보·case 해결/보존 종료 절차가 없으므로 **실수신 활성화는 불가**하며 무기한 보관 정책을 승인한 것이 아니다. 금융 retentionExpiresAt은 지급 종료와 최초 검증 시각 중 늦은 시점+5년으로 시작하며 후속012 reversal이 연장해야 한다. 미지급/검토 사례의 종료일은 null이며 자동 purge하지 않는다.

205의 실제 LocalReader/coverage 연결은 여전히 후속이다. 현재는 실제 삭제 코어에 fake local/provider proof를 넣은 테스트로 양쪽 commit 순서를 검증했다. 이것을 실제 거래 부재 증명 또는 자동 삭제 활성화로 해석하지 않는다.

## 4. 환불 경계

환불/취소가 먼저 확인되면 mapped 거래는 REFUNDED/REVOKED tombstone을 남겨 후속 지급을 막는다. 미매핑 reversal case도 동일 거래의 후속 지급을 막는다. 이미 지급된 구매의 환불은 검토 case로만 보존한다. **실제 반환액 원장·잔여권 종료·timeline reflow·LC revoke는 PLAN-012 후속**이며 구현 전 판매/paid admission을 켜지 않는다.

## 5. 검증 결과와 다음 순서

- `./gradlew clean test bootJar`: 1,168개 통과, 실패/오류/skip 0. 직전 baseline보다 41개 추가.
- 신규 purchase 단위 8개, Mongo 통합 31개, 기존 cleanup 통합에 실구매 입력/삭제 순서 2개 추가.
- 중복·동시 timeline/withdrawal, 금액 null, historical catalog/5기간, owner/env/app/key 불일치, refund-first, source→case counted 이전, source 소실 뒤 중복, barrier/epoch, overflow, Tx rollback·transient/unknown commit, schema drift를 검증했다.
- 첫 장애 주입에서 서버 종료 계열 오류91은 2초 예산 내 unavailable로 끝났다. 이후 기존 저장소와 같은 오류1+UnknownTransactionCommitResult 및 오류112+TransientTransactionError로 각 재시도 분기를 독립 확인했다. 실제 서버 재시작/복구 시간이나 운영 부하를 검증한 결과는 아니다.
- 유지: public API·무료 reader·실provider/환경설정·자동 삭제 OFF. 외부 API/운영 DB/Jira/타서버/commit/push/배포 변경 없음.

다음은 public sync와 통합 reader의 exact DTO/호환 구현, 검증된 provider adapter·공통 worker, 205 실제 금융 reader/coverage 순서다. 1초 poll 부하·실제 Store/RevenueCat·환불/운영 gate는 별도 검증한다.
