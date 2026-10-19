# PLAN-010: 구매 검증·기간권 원장·동기화·복구

> 2026-10-13 3차 조사·계획: [RevenueCat 검증·worker·누락 복구](PLAN-010-phase-3-revenuecat-worker-reconciliation.md). 공식 HMAC/REST/SDK 식별자 문서 확인, hash-only sync의 계정목록 대조·source/금액 보완·명시 worker schema 이관을 정리했다. TMI-211은 PR15로 병합됐으며 이번은 후속 계획 작성이다. 실제 provider/계정설정·실거래·Jira/코드/활성화 변경은 없다.

> 2026-10-13 사용자 승인 정책 변경: [환불 전 confirm Session 완료 허용](../contracts/PAYMENT-CONFIRMED-SESSION-REFUND-POLICY-2026-10-13.md)을 우선 적용한다. 환불 전 confirm된 exact Session은 기존 제출 기한 안에 완료 허용, 환불 선행 confirm 및 신규/재응시는 차단한다. paid 전용 채점 승인 API는 제외하고 durable revoke 전달·구매별 최소 이용 증거는 유지한다. 기존 reserve 응답 출처 추가 방향이며 exact wire/LC 공동 동결은 별도다. 아래 OPEN 제출·채점 차단/GRADING-only 완료·전용 승인 API·v2 필수의 종전 표현은 이 변경 범위에서 역사적 기록으로 대체된다. 코드·LC 저장소·배포는 이번에 변경하지 않았다.


> 2026-10-13 2차 상세 계획: [public sync·통합 entitlement 조회](PLAN-010-phase-2-public-sync-and-entitlement-query.md). TMI-210 이후 API/reader·command·snapshot·기본OFF 범위와 provider/worker 미연결 gate를 정리했다. 작성/검토 단계이며 신규 Jira·코드/배포 변경은 없다.

> 2026-10-13 단순화 승인: 접수 row 중심 pending 관리, 신규 sync의 durable 접수→공통 background 처리, 1초 configurable poll, cleanup 전용 정책 revision 통합, 고정 lookup 키/keyVersion을 채택한다. 키 교체 자동화는 만들지 않는다. 세부 기준은 purchase-r1의 승인 보충 및 §5.6/5.10을 따른다. 구현·schema 이관·실부하 검증·배포는 미완료이며 아래 전체 구현 승인 대기와 구분한다. 앱 Retry-After:5는 그대로이며 worker 1초와 다르다.

- 작성일: 2026-10-06 / 상태: TMI-210 내부 코어 로컬 구현·검증 완료, 나머지 단계 미구현 / Jira: [TMI-210](https://to-teacher.atlassian.net/browse/TMI-210) (TMI-137 하위 작업 유형, PLAN-010 첫 slice만)

2026-10-13: 사용자 구현 요청으로 TMI-210의 저장 규격·접수/pending·구매/기간권/원장·중복/탈퇴/삭제 경합 코어를 구현했다. [구현 범위·검증](../runbooks/TMI-210-purchase-core.md): 전체 clean test bootJar 1,168통과/실패·오류·skip0. 코어는 명시 생성형으로 HTTP/Spring bean에 연결하지 않았다. public API·실provider·1초scheduler/부하검증·205 정책revision 이관·실금융 reader·배포는 후속이며 PLAN-010 전체 완료가 아니다. Jira 상태 변경은 수행하지 않았다.
- 선행: [009](PLAN-009-payment-catalog-and-account.md), [ADR-004 §5.4~5.7/5.10/A](../adr/ADR-004-fixed-term-premium-payment-contract.md).

2026-10-13 상세화: [purchase-r1 기술 규격 초안](../contracts/PLAN-010-purchase-technical-spec-r1.md)에 구매/기간권·중복·탈퇴/삭제 경합·sync·통합 조회 DTO 후보·입력 effect/index·flag/구현 순서를 작성했다. 제품 정책은 유지하며 신규 기술값은 검토안이다. 실제 provider 증거·미해결 보존·crypto/worker exact 검증과 구현 승인은 별도다. 이번은 문서 작성이며 코드/manifest/Jira 변경이 아니다.

## 1. 5줄 결론

1. client callback이 아닌 검증된 RC 거래만 Purchase/기간권을 생성한다.
2. sync/webhook/reconciliation은 동일 정규화 command와 Store transaction business key로 수렴한다.
3. 구매 시각+기존 timeline으로 기간을 계산하고 수신 지연만큼 새 기간을 주지 않는다.
4. 환불을 아직 안전하게 반영할 수 없는 중간 배포에서는 판매·유료 입장을 켜지 않는다.
5. fake adapter 테스트와 실제 provider 서명/식별자 검증을 명확히 분리한다.

## 2. 사용자가 반드시 읽어야 하는 내용

포함: `POST /api/v1/payments/sync`, 기존 `GET /api/v1/entitlements`의 무료·유료 통합 확장, provider 전용 `POST /api/v1/payments/revenuecat/events`, Purchase/SubscriptionEntitlement/timeline/payment ledger/inbox/commands/cursor, RC REST read adapter. RC SDK completion은 앱 책임이며 Billing은 finish/consume을 중복 수행하지 않는다. paid Reservation은 011, 환불 원장은 012.

## 3. 사용자가 결정해야 하는 사항

2026-10-13 사용자 승인: 유료 전용 `GET /api/v1/payments/entitlement`는 만들지 않고, 기존 `GET /api/v1/entitlements`에 무료·유료·시험 이용 가능성 정보를 통합한다. 이번은 계획/계약 개정이며 현재 무료 reader 코드·OpenAPI·배포 응답 변경은 아니다. [결정서](../codex/CONTRACT_DECISIONS.md)의 통합 조회 기준을 따른다.

009 PublicResponse envelope는 2026-10-06 승인됐다. 나머지 제품 정책은 재선택하지 않는다. 실제 RC HMAC 지원/SDK 식별자/부분 금액 필드가 ADR와 다르면 근거를 보고하고 계약 변경 승인을 받는다. 없는 provider 필드를 fake로 만들어 검증 완료 처리하지 않는다. 본 slice 상세 기술/구현 승인은 별도다.

## 4. 주요 위험과 미확인 사항

Authorization+HMAC·5분 window는 승인된 목표지만 ADR의 exact header/서명 문자열은 Phase 0 실증 전 그대로 공식 사실로 취급하지 않는다. unsupported일 때 Authorization-only로 임의 강등하지 않는다. 서명 검증 adapter는 fixture 검증 뒤 연결한다. provider event의 시간은 delivery signature 시간과 다르다.

RC v2 customer/original_customer와 SDK account reference 변환/Store transaction hint는 G1/G2 gate. aliases/TRANSFER/family share/anonymous/inactive 신규 ref는 지급 근거가 아니다. 404/empty page/네트워크 실패는 환불 또는 거래 부재 확정이 아니다. 실제 결제가 RC에 기록되기 전 앱 종료되는 G5도 판매 gate다.

## 5. 현재 작업과 직접 관련된 구현

### API

- sync: billing:purchase+MEMBER, 필수 lowercase UUIDv4 Idempotency-Key, body `{store,transactionId}`만. identifier는 1~255 opaque hint, Google purchaseToken을 받는 route가 아님. 동일 key/의미→같은 command, 다른 의미409. userId/가격/기간/receipt/source 지정 거절.
- durable 검증 대기202: result `{operationId,status:"PENDING",purchaseId:null,purchaseStatus:null}`, Retry-After:5. 저장 실패503. 성공200 result `{operationId,status:"APPLIED",purchaseId,purchaseStatus}`. APPLIED는 지금 사용 가능이라는 뜻이 아님. 재요청은 current 상태를 읽고 지급 재수행 안 함.
- 통합 reader: 기존 `GET /api/v1/entitlements`를 확장한다. billing:read·JWT sub·Guest/MEMBER 본인 조회·현재 envelope와 무료 필드 의미를 유지한다. 무료 수량/재응시/진행 상태와 paidActive/이용 종료·연속 종료·다음 시작/hasPendingSync 및 시험 이용 가능성 요약을 한 응답에서 제공한다. 유료 기간과 무료 수량은 합산하지 않으며 paid-first/free-preserve를 따른다. 정확한 추가 DTO·nullable/enum은 아래 호환 규격으로 후속 동결한다. 조회 무변경/no-store, 저장소 장애503, 자격 불명은 확인 불가로 구분한다. 실제 시작 허용은 reserve에서 최종 판정한다.
- public strict16KiB, provider256KiB 예외. HTTP 오류/기본 rate limit은 ADR §5.3 및 009 envelope. 최종 `purchaseStatus` partial-money 표현은 012 모델과 함께 동결하고 active/revoked 판단을 금액 상태에 종속시키지 않음.

### 통합 조회 호환·판정 규격

- 기존 `asOf,consistency,status,benefits`와 benefit별 `availableQuantity,newAttempt,retake,hasInProgress,attemptGroups`의 이름/타입/무료 의미를 유지한다. 기존 status를 유료 포함 종합 가능 여부로 재해석하지 않는다. 신규 paid 영역과 종합 이용 가능성 영역을 추가하는 방식을 우선 검토하고 exact 필드/null/생략·enum은 구현 전 동결한다.
- 신규 요약은 시험 이용 가능/불가/확인 불가, 이용 근거(유료/새 무료/기존 그룹 재응시), 진행 상태를 구분한다. 무료 수량과 유료 기간은 합산하지 않는다. 진행 중 Session 또는 GRADING이 있으면 유효 유료권만 보고 '새 시험 시작 가능'으로 확정하지 않는다.010은 조회 정보를 구성하고011에서 공통 guard/paid resolver와 동일한 판단 규칙으로 검증한다. reserve가 최종 권한 판정이며 별도 시험 시작 승인 token을 발급하지 않는다.
- 재응시를 화면에서 '1회 남음'으로 보여주는 기존 방향은 가능하되, 신규 무료 수량에 재응시를 더해 원래 availableQuantity 의미를 바꾸지 않는다. 내부/DTO의 신규와 재응시 근거를 유지하고 프론트 표시로 조합한다.
- 조회 중 계정/ref/Claim/Grant/기간권 생성·hold/차감·owner 변경이나 provider 동기화를 하지 않는다. 무료·유료를 가능한 동일 조회 기준시각과 일관된 로컬 snapshot으로 읽고 상세 consistency는 구현 규격에서 확정한다. 특정 결제 확인은 sync가 계속 담당한다.
- paid 확장 flag OFF/연동 미준비를 '유료권 없음'으로 꾸미지 않는다. 기존 무료-only 응답 유지와 신규 프론트의 확장 미지원 처리/활성화 순서를 exact 계약으로 고정한다. 유료가 꺼졌다고 기존 무료 route까지 차단하지 않는다. 기존 무료 PENDING은 의미를 유지하며 통합 판단의 미확인을 false/0으로 숨기지 않는다. 저장소 장애는503·Cache-Control:no-store다.
- frontend의 추가필드/unknown enum 허용 여부를 계약 테스트로 확인한 뒤 additive 배포한다. 기존 성공 envelope·무료 응답 의미 회귀, Guest 본인 조회/구매 거절, 유료 유효+무료 보존, 진행/재응시, PENDING/지연·만료·환불·동시 reserve, 확장 OFF/ON을 검증한다. 제거하는 paid 전용 route의 controller/ingress allowlist/독립 reader flag는 만들지 않는다.

### 수신·정규화·Transaction

webhook은 provider 전용 인증 후 duplicate/trailing/coercion 차단, depth32/string16KiB, unknown optional field 무저장 허용. 인증된 unknown type durable ignored 후200. 최소 normalized inbox/digest를 commit한 뒤200; raw payload 저장 금지. 같은 provider event/digest 중복200, 의미 충돌409. environment/app 불일치422. inbox 미확정 commit503 또는 기존 commit 확인.

verified 관측→immutable transaction key/owner/catalog snapshot 확인→account timeline CAS→Purchase+entitlement+ledger 원자 commit. key는 `(environment,store,appId,verified transaction lookup hash)`이며 client hint나 original_transaction_id로 unique를 대체하지 않는다. 동일 transaction의 다른 RC event/API 관측은 하나의 effect로 수렴. 금액은 Decimal128+통화/출처, 불명 null(0 대체 금지).

기간은 구매 시각과 앞선 유효 timeline 끝 중 늦은 시점부터 duration만큼. 뒤늦은 별도 구매 관측의 append sequence는 CAS commit으로 결정하고 이미 확정된 slot을 event timestamp 정렬로 재작성하지 않는 안을 Phase 0 ADR에 동결한다. 동시 append/unknown commit을 unique sequence와 durable effect로 해결한다.

012 이전 refund 관측은 무시하지 않고 미지급 거래의 deny/tombstone·REVIEW_REQUIRED로 격리해 늦은 owned가 새 지급하지 못하게 한다. 이 중간 처리로 기존 판매 환불을 지원한다고 주장하지 않으며 real 판매는 계속 OFF.

### Adapter·worker

RC v2 read endpoint/허용 host/path·최소 권한은 ADR §5.6. HTTP 외부 호출은 Transaction 밖, connect2초/response5초/sync budget5초,redirect 금지,response1MiB,page100. next_page는 host/project/customer/path 검증 후 cursor만 추출. 429 Retry-After,423/5xx/network backoff,401/403 BLOCKED_AUTH 회로. 합산240회/분·동시4 초기 budget, key 증가로 quota 우회 금지.

inbox/cursor/command lease30초+fencing, 승인된 due poll 기본1초(configurable),batch100,5초~1시간 jitter/24시간 뒤 dead-letter. poll과 provider 호출 주기는 다르며 이 worker는 TMI-210에 연결하지 않았다. PENDING5분, ACTIVE/SCHEDULED6시간, terminal 최근90일 일1회. account cursor로 callback 유실 복구, 최근 활동 우선+bounded 전체 순환. PENDING 응답을 영구 immutable snapshot으로 캐시하지 않음.

### 코드·schema·flag

domain/payment의 provider port/fake와 RevenueCat adapter, PurchaseApplicationService/TimelineService/SyncService/reader/worker를 분리한다. 신규 의존성이 필요하면 사전 승인. ADR A.2 구매 unique·sequence·ledger effect·inbox/command/cursor index 검증. Transaction 없는 Mongo는 기동 실패. `sync-enabled`,통합 조회의 paid 정보 확장용 독립 flag(정확 이름 후속 동결),`provider-ingress-enabled`,`provider-worker-enabled`,`reconciliation-enabled` 기본 OFF 제안; 판매 gate와 회수/복구 flag는 분리한다.

## 6. 부록 — 완료 기준/테스트

- 동일 구매 sync+webhook+poll 경쟁, 중복 field/event, same event different digest, unknown commit, 두 구매 동시 append, lost callback/page cursor 복구.
- customer/env/product/identifier 오인·다른 owner·alias·family share 거절, stale owned로 refund tombstone 부활 금지.
- normal/scheduled/expired paid reader·continuous gap·PENDING/DB 장애 분리, 무료 read 부수효과 없음.
- provider 실증 전까지 fake 단위/MVC/replica-set 테스트만. 실제 store 호출 없는 `./gradlew clean test`.
- public HTTP SERVER span 아래 업무 INTERNAL span `payment_sync`, worker 업무 INTERNAL span `payment_event_consume`/`payment_reconcile` 정상·예외 종료; baggage 없음, URL query 참조/원문/token 로그 없음. SpanKind.PUBLIC은 사용하지 않는다.
- 012와 통합 후에만 실제 환불 지원 완료 표시. G1~G5 성공 근거 없이는 flag 활성화 불가.
