# PLAN-010: 구매 검증·기간권 원장·동기화·복구

- 작성일: 2026-10-06 / 상태: 승인 대기 / Jira 미생성
- 선행: [009](PLAN-009-payment-catalog-and-account.md), [ADR-004 §5.4~5.7/5.10/A](../adr/ADR-004-fixed-term-premium-payment-contract.md).

## 1. 5줄 결론

1. client callback이 아닌 검증된 RC 거래만 Purchase/기간권을 생성한다.
2. sync/webhook/reconciliation은 동일 정규화 command와 Store transaction business key로 수렴한다.
3. 구매 시각+기존 timeline으로 기간을 계산하고 수신 지연만큼 새 기간을 주지 않는다.
4. 환불을 아직 안전하게 반영할 수 없는 중간 배포에서는 판매·유료 입장을 켜지 않는다.
5. fake adapter 테스트와 실제 provider 서명/식별자 검증을 명확히 분리한다.

## 2. 사용자가 반드시 읽어야 하는 내용

포함: `POST /api/v1/payments/sync`, `GET /api/v1/payments/entitlement`, provider 전용 `POST /api/v1/payments/revenuecat/events`, Purchase/SubscriptionEntitlement/timeline/payment ledger/inbox/commands/cursor, RC REST read adapter. RC SDK completion은 앱 책임이며 Billing은 finish/consume을 중복 수행하지 않는다. paid Reservation은 011, 환불 원장은 012.

## 3. 사용자가 결정해야 하는 사항

009 PublicResponse envelope는 2026-10-06 승인됐다. 나머지 제품 정책은 재선택하지 않는다. 실제 RC HMAC 지원/SDK 식별자/부분 금액 필드가 ADR와 다르면 근거를 보고하고 계약 변경 승인을 받는다. 없는 provider 필드를 fake로 만들어 검증 완료 처리하지 않는다. 본 slice 상세 기술/구현 승인은 별도다.

## 4. 주요 위험과 미확인 사항

Authorization+HMAC·5분 window는 승인된 목표지만 ADR의 exact header/서명 문자열은 Phase 0 실증 전 그대로 공식 사실로 취급하지 않는다. unsupported일 때 Authorization-only로 임의 강등하지 않는다. 서명 검증 adapter는 fixture 검증 뒤 연결한다. provider event의 시간은 delivery signature 시간과 다르다.

RC v2 customer/original_customer와 SDK account reference 변환/Store transaction hint는 G1/G2 gate. aliases/TRANSFER/family share/anonymous/inactive 신규 ref는 지급 근거가 아니다. 404/empty page/네트워크 실패는 환불 또는 거래 부재 확정이 아니다. 실제 결제가 RC에 기록되기 전 앱 종료되는 G5도 판매 gate다.

## 5. 현재 작업과 직접 관련된 구현

### API

- sync: billing:purchase+MEMBER, 필수 lowercase UUIDv4 Idempotency-Key, body `{store,transactionId}`만. identifier는 1~255 opaque hint, Google purchaseToken을 받는 route가 아님. 동일 key/의미→같은 command, 다른 의미409. userId/가격/기간/receipt/source 지정 거절.
- durable 검증 대기202: result `{operationId,status:"PENDING",purchaseId:null,purchaseStatus:null}`, Retry-After:5. 저장 실패503. 성공200 result `{operationId,status:"APPLIED",purchaseId,purchaseStatus}`. APPLIED는 지금 사용 가능이라는 뜻이 아님. 재요청은 current 상태를 읽고 지급 재수행 안 함.
- paid reader: billing:read, ADR `asOf,paidActive,activeEntitlementId,activeOfferCode,activeEndsAt,continuousAccessEndsAt,nextStartsAt,hasPendingSync`를 009 envelope로 반환 제안. 무료 reader는 별도 유지, 합산 수량 없음. 조회 무변경/no-store, 장애503. Guest 구매 금지와 본인 조회를 혼동하지 않음.
- public strict16KiB, provider256KiB 예외. HTTP 오류/기본 rate limit은 ADR §5.3 및 009 envelope. 최종 `purchaseStatus` partial-money 표현은 012 모델과 함께 동결하고 active/revoked 판단을 금액 상태에 종속시키지 않음.

### 수신·정규화·Transaction

webhook은 provider 전용 인증 후 duplicate/trailing/coercion 차단, depth32/string16KiB, unknown optional field 무저장 허용. 인증된 unknown type durable ignored 후200. 최소 normalized inbox/digest를 commit한 뒤200; raw payload 저장 금지. 같은 provider event/digest 중복200, 의미 충돌409. environment/app 불일치422. inbox 미확정 commit503 또는 기존 commit 확인.

verified 관측→immutable transaction key/owner/catalog snapshot 확인→account timeline CAS→Purchase+entitlement+ledger 원자 commit. key는 `(environment,store,appId,verified transaction lookup hash)`이며 client hint나 original_transaction_id로 unique를 대체하지 않는다. 동일 transaction의 다른 RC event/API 관측은 하나의 effect로 수렴. 금액은 Decimal128+통화/출처, 불명 null(0 대체 금지).

기간은 구매 시각과 앞선 유효 timeline 끝 중 늦은 시점부터 duration만큼. 뒤늦은 별도 구매 관측의 append sequence는 CAS commit으로 결정하고 이미 확정된 slot을 event timestamp 정렬로 재작성하지 않는 안을 Phase 0 ADR에 동결한다. 동시 append/unknown commit을 unique sequence와 durable effect로 해결한다.

012 이전 refund 관측은 무시하지 않고 미지급 거래의 deny/tombstone·REVIEW_REQUIRED로 격리해 늦은 owned가 새 지급하지 못하게 한다. 이 중간 처리로 기존 판매 환불을 지원한다고 주장하지 않으며 real 판매는 계속 OFF.

### Adapter·worker

RC v2 read endpoint/허용 host/path·최소 권한은 ADR §5.6. HTTP 외부 호출은 Transaction 밖, connect2초/response5초/sync budget5초,redirect 금지,response1MiB,page100. next_page는 host/project/customer/path 검증 후 cursor만 추출. 429 Retry-After,423/5xx/network backoff,401/403 BLOCKED_AUTH 회로. 합산240회/분·동시4 초기 budget, key 증가로 quota 우회 금지.

inbox/cursor/command lease30초+fencing,scheduler5초,batch100,5초~1시간 jitter/24시간 뒤 dead-letter. PENDING5분, ACTIVE/SCHEDULED6시간, terminal 최근90일 일1회. account cursor로 callback 유실 복구, 최근 활동 우선+bounded 전체 순환. PENDING 응답을 영구 immutable snapshot으로 캐시하지 않음.

### 코드·schema·flag

domain/payment의 provider port/fake와 RevenueCat adapter, PurchaseApplicationService/TimelineService/SyncService/reader/worker를 분리한다. 신규 의존성이 필요하면 사전 승인. ADR A.2 구매 unique·sequence·ledger effect·inbox/command/cursor index 검증. Transaction 없는 Mongo는 기동 실패. `sync-enabled`,`paid-reader-enabled`,`provider-ingress-enabled`,`provider-worker-enabled`,`reconciliation-enabled` 기본 OFF 제안; 판매 gate와 회수/복구 flag는 분리한다.

## 6. 부록 — 완료 기준/테스트

- 동일 구매 sync+webhook+poll 경쟁, 중복 field/event, same event different digest, unknown commit, 두 구매 동시 append, lost callback/page cursor 복구.
- customer/env/product/identifier 오인·다른 owner·alias·family share 거절, stale owned로 refund tombstone 부활 금지.
- normal/scheduled/expired paid reader·continuous gap·PENDING/DB 장애 분리, 무료 read 부수효과 없음.
- provider 실증 전까지 fake 단위/MVC/replica-set 테스트만. 실제 store 호출 없는 `./gradlew clean test`.
- public HTTP SERVER span 아래 업무 INTERNAL span `payment_sync`, worker 업무 INTERNAL span `payment_event_consume`/`payment_reconcile` 정상·예외 종료; baggage 없음, URL query 참조/원문/token 로그 없음. SpanKind.PUBLIC은 사용하지 않는다.
- 012와 통합 후에만 실제 환불 지원 완료 표시. G1~G5 성공 근거 없이는 flag 활성화 불가.
