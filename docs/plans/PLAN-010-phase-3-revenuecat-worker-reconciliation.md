# PLAN-010 3차 — RevenueCat 검증 연결·공통 worker·누락 복구

- 2026-10-14 공개 sync 연결 구현: [실행 안내](../runbooks/TMI-212-public-sync-wiring.md). worker와같은 service bean 사용, 무조건ON거절을실제구성/의존성/schema검사로대체. 아래 과거 sync ON무조건차단 설명은 이 범위에서 대체한다. 기본OFF·실Store/환불/입장/판매gate 유지, 5년정리ON 요구없음.

- 2026-10-14 사용자 승인: [금융 보존 종료 정리](TMI-212-financial-link-erasure-plan.md)는 장기 후속 작업으로 분리한다. nullable 연결·partial unique 이관은 지금 구현하지 않으며, 5년 정리 미구현만으로 현재 결제 출시/TMI-212를 차단하지 않는다. 보존기간은 그대로이며 최초 만료일/legacy 확인과 만료 전 후속 일정 지정은 필요하다. 실환경·결제/환불·인증/복원 등 다른 gate는 유지한다.

- 2026-10-14 D3 부분 구현: [참조 보존 정리 r1](../contracts/TMI-212-reference-retention-r1.md). 현재 구매/기간권/원장·미해결 보호 후 암호화 reference 삭제, 만료 lease owner/until 해제. 매시간 bounded/default dry-run. 기존 코드는 보존하고 삭제OFF 유지. 금융 사용자 연결 제거 미구현은 위 승인으로 장기 후속에 분리했으며, 이것만으로 D 미완료로 판정하지 않는다. 전체 완료/실배포 승인은 다른 잔여 gate 확인이 필요하다.

- 작성일: 2026-10-13 / develop, TMI-211 병합 HEAD ea30a62 확인.
- Jira: [TMI-212](https://to-teacher.atlassian.net/browse/TMI-212), TMI-137 하위 작업. 2026-10-13 사용자 요청으로 생성, 기본OFF 구현과 실환경 활성화 분리.
- 구현 진행: A/B에 이어 C1의durable source worker와거래별sync완료연결·동일Tx lease/최초epoch검사·r4명시이관을구현했다. [adapter-r1](../contracts/TMI-212-revenuecat-adapter-r1.md), [webhook-r1](../contracts/TMI-212-webhook-r1.md), [worker-r1](../contracts/TMI-212-worker-r1.md) 참조. C2의실API조회/계정단위합치기·공유quota·sync독립retry/runtime과D의대사/reference영속화/운영알림/보존은미완료다.
- C2 선행 진행: [provider-dispatch-r1](../contracts/TMI-212-provider-dispatch-r1.md)의 guarded receive·공용 Mongo 기반 project rolling 240회/60초·동시 permit4·AUTH 영속 차단을 명시 생성형으로 추가했다. 계정 scan/페이지 checkpoint/sync 재시도/runtime 배선은 아직 남았다. 공유 budget은 같은 DB를 주입할 때만 환경·서버 전체 합산이므로 실배포 topology 확인이 필요하다.
- C2b 진행: [계정 조회 규격](../contracts/TMI-212-account-lookup-r1.md)의 계정별 조회 묶기·암호화 페이지 checkpoint·legacy terminal sync 회수·Retry-After/24h 검토·AUTH 인계와 기본OFF 실제 runtime 배선을 구현했다. sync-command-r1은 유지하고 기존 ACCOUNT_LOOKUP에 optional lookup/명시 validator 이관을 적용한다. 계정/command 운영 재개 도구·정기 대사·보존/알림은 D 후속이다.
- 상태: A/B/C1/C2의 처리 경로 로컬 구현, 기본 OFF. TMI-212 전체 완료가 아니며 실제 계정 설정·배포는 미수행이다. 공개 sync ON은 D/rollout gate 전까지 계속 거절한다.
- D1 진행(2026-10-14): [보존 규격](../contracts/TMI-212-retention-r1.md)의 시간당 bounded RECEIVED 119일 case 인계·terminal sync 120일 대상 점검/독립 OFF 삭제·개수 metric을 구현했다. 기존 schema/index와 외부 계약을 유지한다. D의 정기 provider 조회/reference 보존·terminal inbox TTL와 미완료 sync의 연결 보호·운영 재개/외부 알림은 미완료이며 sync ON 차단을 유지한다.
- D2 진행(2026-10-14): [운영 안내](../runbooks/TMI-212-reconciliation-and-review.md)의 정기계정조회·구매별암호화reference·source TTL뒤원장완료·선택적lookup재개·식별자없는운영요약을연결했다. sync-r2/cursor-r2·reference전용collection 명시이관 필요, 정기조회/알림기본OFF. 후속으로 [Slack Incoming Webhook adapter](../runbooks/TMI-212-slack-alerts.md) 및 [운영재개 CLI](../runbooks/TMI-212-payment-review-operator.md)를 구현했다. CLI는조회/dry-run/apply/result·DB인증principal·원자receipt를제공하며receipt-r1명시설치필요. 실제secret주입·개인별운영권한/감사보존·5년physicalpurge/cursor정리·실환경검증은후속이며전체D/판매완료아님.
- 선행: [TMI-210 코어](../runbooks/TMI-210-purchase-core.md), [TMI-211 API](../runbooks/TMI-211-public-sync-entitlement-query.md), [상위 PLAN](PLAN-010-purchase-ingestion-and-timeline.md), [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md).

## 1. 5줄 결론

1. 공식 문서에서 webhook HMAC 지원을 확인했다. 기존 Authorization+HMAC 목표를 낮출 이유는 발견하지 않았다. 실제 프로젝트에서 켜져 있다는 뜻은 아니다.
2. 앱 sync·webhook·정기 조회를 검증된 PurchaseObservation과 기존 구매 코어로 연결한다. 앱 callback만으로 지급하지 않는다.
3. sync는 해시만 저장하므로 구매 계정별 RC 목록 조회→거래 식별자 해시 대조가 초기 권장 경로다. 해시를 원래 거래 ID로 복원하거나 검색 API에 보내지 않는다.
4. 1초 poll은 DB에서 실행할 작업을 찾는 주기다. 빈 큐에서는 RC를 호출하지 않으며 외부 호출은 lease·backoff·공유 budget으로 제한한다.
5. 실거래 매핑·원구매자·환불/입장 준비가 끝나기 전 판매는 OFF다. PLAN-011/012·205 자동 삭제는 이번에 구현하지 않는다.

## 2. 사용자가 반드시 읽어야 하는 내용

이번 단계는 이미 만든 접수함과 구매 원장 사이를 연결한다.

`앱 결제 → sync 접수 → worker의 RC 확인 → 기존 코어에 관측 저장/반영 → sync APPLIED`

webhook은 접수 성공 후 빠르게 200을 반환하고 지급 처리는 worker로 넘긴다. 정기 계정 조회는 앱 콜백·webhook 누락을 보완한다. RC에 아직 없는 Store 거래까지 복구된다고 보장하지 않는다.

### 공식 확인과 미검증 구분

| 항목 | 이번 확인 | 남은 검증 |
| --- | --- | --- |
| webhook 인증 | 공식 문서의 Authorization 설정·HMAC 제공 [S1] | 실제 프로젝트 플랜/토글·수신 서명·재전송 |
| 일회성 거래 조회 | v2 purchase 검색/단건 및 계정별 구매 목록 제공 [S2,S3] | 계정 권한·실응답·양 Store ID 일치 |
| SDK 식별자 | iOS transactionIdentifier, Android orderId API 존재 [S5,S6] | 앱 실제 SDK/wrapper 버전·Android null·반복 구매 |
| 계정 귀속 | API 원구매자/현재 고객과 webhook 원본/현재 ID를 구분해야 함 | alias/restore/재가입 시 G2 fixture |
| 금액 | API의 USD revenue 예시는 실제 결제 통화 금액의 대체물이 아님 [S2] | webhook/API 금액의 의미·null 보완 계약 |

현재 purchase core는 정규화 자료만 받는다. adapter가 Store/앱/환경/상품/수량/소유권을 확인한 뒤에만 생성해야 한다. unknown event·원구매자 불명·부분 환불을 PURCHASE로 강제 변환하지 않는다.

## 3. 사용자가 결정해야 하는 사항

가격·기간·환불 정책의 새 선택은 없다. 다음 **기술 권장안의 승인** 뒤 단계별 구현을 권장한다.

- 초기 sync는 **계정별 목록 조회+해시 비교**로 구현한다. 원문 hint를 새로 보관하지 않는 대신 거래가 많은 계정은 페이지 조회가 늘 수 있다. 반복 sync를 계정 단위로 합쳐 호출한다.
- 검증할 수 없는 건은 자동 지급/환불/미구매 판정을 하지 않고 검토 대기로 보존한다. 기존 운영 알림 채널 연결은 활성화 전 확정한다.
- 명시 schema 이관과 source 관측 ID/금액 보완 규칙은 §5의 첫 구현 단위에서 exact로 고정한다. 임의 필드 추가나 운영 데이터 자동 이관은 하지 않는다.

현재 공식 조사 결과 인증 방향 변경 승인은 필요하지 않다. 실제 dashboard/fixture에서 HMAC 지원이 다르면 구현·활성화를 중단하고 근거와 대안을 별도로 요청한다. 이 계획 자체는 권한 축소 없는 인증 fallback을 허용하지 않는다.

## 4. 주요 위험과 미확인 사항

- **SDK null ID:** Android orderId는 nullable로 문서화돼 있다. purchaseToken·RC resource ID로 대체하거나 빈 sync를 보내지 않는다. 거래 ID가 없는 구매 후 앱은 통합 조회·서버 누락 복구를 사용하도록 프론트와 합의한다. 현재 sync DTO를 몰래 바꾸지 않는다.
- **숫자 예시:** RC 문서의 store_purchase_identifier 예시에는 숫자도 나타난다. 예시를 근거로 자동 숫자→문자 coercion을 추가하지 않는다. 공식 schema·양 Store 실응답 타입을 비교하고 모호하면 격리한다.
- **원구매자:** last-seen app_user_id나 고객 목록에 포함됐다는 사실만으로 소유권을 확정하지 않는다. 원구매 reference/current Billing 연결·환경·catalog가 모두 일치해야 한다. 탈퇴 후에는 금융 사실만 남기고 신규 이용권을 주지 않는다.
- **이미 반영된 거래의 금액 보완:** 현재 코어의 불변 자료 비교 때문에 API null금액→webhook 실제 금액이 충돌할 수 있다. 같은 거래의 금액 불명→검증 금액 추가를 지급 없이 보완하는 규격/테스트가 필요하다. 기존 확인 금액 충돌은 검토 대상이다.
- **과거 owned 재관측:** API 재조회에 고정 webhook eventId가 있는 것처럼 만들면 안 된다. 상태 전환과 재관측 중복을 구분하고 기존 refund/revoke 뒤 owned로 재지급하지 않는다.
- **조회 완전성:** next_page 종료는 이번 API 목록 순회를 마쳤다는 뜻이지 Store 거래 전체 부재 증거가 아니다. 205의 자동 삭제용 미구매 검증으로 그대로 재사용하지 않는다.
- **보존:** raw hint/receipt/webhook 전문은 저장하지 않는다. 미해결 pending을 TTL 삭제하지 않는다. terminal command 정리와 case 이관·개인정보 보존은 실접수 전에 닫는다.

## 5. 직접 관련된 설계와 구현 순서

### 5.1 A — provider adapter와 exact 규격 (OFF)

신규 운영 SDK 의존성 없이 기존 Java HTTP 수단을 우선한다. 외부 응답을 다루는 adapter와 테스트 fake를 분리한다.

허용 base는 `https://api.revenuecat.com/v2`, 서버용 최소 권한 `customer_information:purchases:read`. endpoint는 ADR §5.6의 계정 목록/단건/거래 검색만 허용한다. 초기 sync worker는 계정 목록을 사용한다. 환불 실행·restore·transfer·고객 삭제 API는 호출하지 않는다.

고정 project·Billing 계정에서 얻은 customer reference만 URL 구성에 사용한다. env와 product→app/store/productId 매핑은 서버 catalog 기준이다. `current/original customer`, ownership, quantity=1, purchased_at, 상태·사업 key를 검증한다. 목록은 page100·응답1MiB·redirect 금지, connect2초/response5초. next_page의 host/path/project/customer/environment를 검사하고 cursor만 추출해 서버 URL을 재구성한다. 외부 호출은 Mongo Transaction 밖이다.

첫 구현에서 닫을 최소 규격:

| 대상 | 권장 기준 |
| --- | --- |
| webhook dedupe | 환경/project/eventId의 기존 HMAC key. 같은 key의 다른 검증 의미는 conflict |
| API 관측 dedupe | API 전용 namespace + 환경/project/RC purchase ID + 검증된 상태/불변 사실 fingerprint. 동일 관측은 재사용, owned/refunded는 다른 입력. webhook과 key namespace 분리 |
| 거래 유일성 | 기존 environment/store/appId/Store transaction HMAC 불변. RC purchase resource ID와 다름 |
| 정규화 금액 | USD revenue를 STORE_PAID로 저장 금지. 불명 null 유지, 검증 금액 보완은 별도 append-only 증거이며 기간 재지급 금지 |
| RC 재조회 reference | 재조회에 필요한 최소 RC purchase ID 등만 암호화 저장하는 adapter 메타데이터 제안. 실제 key/nonce/envelope·보존·index를 명시 migration으로 고정; credential/원문 저장 금지 |
| 지원 외 입력 | immutable event digest+금전 효과 없는 disposition 또는 최소 review case. 기존 Kind 3종을 억지로 확장해 지급하지 않음 |

API fingerprint의 금액 보완/상태 전환 canonicalization, schema validator·인덱스 이름/필드순서·unique/partial/TTL 및 migration manifest를 코드 전 동결한다. 추적용 request마다 랜덤 event key를 만들지 않는다. secret signing key와 transaction lookup key는 별개다.

### 5.2 B — webhook ingress (OFF)

경로는 기존 계획의 `POST /api/v1/payments/revenuecat/events`. 사용자 JWT route와 분리된 provider chain/host-path allowlist, user/internal port 우회 거절을 검증한다. 해당 route만256KiB 제한을 적용하며 사용자 sync16KiB는 유지한다.

공식 서명 header는 `X-RevenueCat-Webhook-Signature`, HMAC-SHA256 입력은 timestamp 문자열·점·raw body bytes다 [S1]. Authorization와 서명을 모두 검사하고 상수시간 비교한다. delivery timestamp 기준 ±300초만 허용한다. `event_timestamp_ms`를 freshness로 쓰지 않는다. 원문 JSON은 메모리 검증 후 폐기한다. header 중복·t/v1 중복·overflow·이상형식 거절, 정확 허용 grammar는 fixture로 고정한다.

인증→strict JSON→최소 durable 접수 commit→200 순서. unknown optional 필드는 저장하지 않고 허용한다. TEST/unknown type은 durable ignored, 금융 오류/원구매자 불명은 review로 분리한다. unknown type을 unknown Store의 구매 승인으로 혼동하지 않는다. 인증 실패401, malformed400, 계약 불일치422, 의미 충돌409, DB 미확정503을 기존 provider 계약 기준으로 고정한다. 성공은200이지 임의2xx가 아니다.

NON_RENEWING_PURCHASE는 full binding이 증명될 때만 지급 후보, 단순 CANCELLATION은 곧바로 환불이 아니다 [S4]. 환불·TRANSFER·REFUND_REVERSED는 ADR의 재조회/격리 기준을 유지한다. 012 이전에는 코어의 안전한 handoff만 사용하고 실제 환불 지원 완료라고 하지 않는다.

### 5.3 C — 공통 worker·sync 연결 (OFF)

별도 범용 effect 장부를 만들지 않는다. sync command, provider inbox와 account cursor의 상태를 각각 소유하고 pendingEvidence는 기존 Transaction 규칙으로 관리한다.

1. due row를 소량 조회하고 lease30초·leaseVersion·epoch·rowVersion을 CAS로 확보한다.
2. Transaction 밖에서 RC 조회/검증을 수행한다. 외부 요청 전에 shared budget을 확보한다.
3. 응답 적용 시 lease/epoch를 재검사한다. 만료된 worker는 상태/pending/cursor를 수정하지 못한다.
4. 검증 source 접수·코어 apply·sync source binding/complete를 수행한다. 재시작 시 기존 durable source를 찾아 이어가며 거래 재지급 없음.
5. snapshot/list 페이지 cursor는 해당 페이지의 모든 관측이 durable하게 처리된 뒤만 전진한다. 환불/충돌 case 인계도 완료 조건을 명시한다.

poll1초 configurable, batch최대100은 조회 상한이지 동시 HTTP100개가 아니다. 초기 shared240회/분·외부 동시4, page별 호출에도 budget 적용. 같은 RC project의 양 환경 합산을 실제 보장하는 배포 구성을 정한다(각 서버240씩 적용 금지). 빈 큐는 외부 호출/무의미 DB update 없음. 계정별 sync는 하나의 scan에 합쳐 모든 대기 hint를 대조한다.

429는 Retry-After를 존중,423/5xx/network는5초~1시간 jitter backoff. 응답 헤더 이상/과도한 대기는 운영 보류한다.401/403은 BLOCKED_AUTH 경보로 반복 호출 중지.24시간 retry 예산 소진은 review/dead-letter 인계이며 pending을 자동 차감하거나 사용자 권리를0으로 만들지 않는다. 명시 재개는 시도 세대를 바꾸고 과거 worker를 fence한다.

2026-10-13 C2b exact: 계정별 lease/leaseVersion/generation/암호화 cursor는 기존 ACCOUNT_LOOKUP의 optional lookup에 저장하고 sync-command-r1에는 새 field를 추가하지 않는다. retry 예산은 command.createdAt+24시간이다. [cursor-r1](../contracts/TMI-212-account-lookup-r1.md)의 **writer 정지/ALL_BLOCKED→기존 행 적합성 검사→명시 validator 이관→verify→새 worker 배포**를 따른다. 기존 두 index는 유지, work 없는 행은 첫 claim에 초기화한다. 구 worker/purger 자동 rollback 금지. 종전 command worker field/r2 backfill 제안은 이 방식으로 대체한다.

### 5.4 D — reconciliation·보존·운영 검증

ACCOUNT_LOOKUP 활동 cursor로 최근 계정을 우선 확인하되 전체 대상 bounded 순환을 병행한다. 계획값은 대기5분, 활성/예정6시간, 최근90일 terminal 일1회이며 shared budget 아래 공정하게 분배한다. 목록은 변경 가능한 provider 자료이지 고정 snapshot으로 가정하지 않는다. 순회 재시작·중복·새 거래 삽입을 거래 unique와 반복 순회로 수렴시킨다.

404/빈 목록/페이지 상한/타임아웃은 미구매·환불 확정이 아니다. 미해결 입력의 120일 경계 전에 case로 원자 인계하거나 조회 가능한 최소 증거와 pending을 보존하는 규격을 마련한다. terminal sync120일 명시 정리도 포함하되 실제 삭제는 검증 후 별도 flag/gate로 둔다. payment 금융5년·inbox120일·backup35일의 목적을 혼용하지 않는다.

알림은 auth 차단·적체/미처리 age·반복 실패·schema/fence 오류·충돌을 식별자 없는 metric으로 분리한다. 일반 검토 건과 서비스 장애를 구분하고 기존 Slack 운영 연동을 재사용한다. owner·채널·재개 권한은 운영 ON 전 필요하다. URL query/customer ID·Authorization·raw body는 log/trace에서 제거한다.

### 5.5 flag와 배포 순서

기존 sync/통합조회 OFF를 유지하고 provider-ingress/provider-worker/reconciliation을 독립 OFF로 추가하는 안이다. 새 플래그의 계층·환경변수 이름은 구현 시 exact로 기록한다.

`A 규격/adapter → B 수신 → C worker → D 누락복구/보존 → sandbox 실증 → 011/012 준비 → 별도 활성화 승인`

2026-10-14 구현: sync ON은 실제 worker runtime이 검증한 service bean에 의존하고 worker flag/recovery fence·기존 public JWT/ingress·실제 key/catalog/schema/shared budget 구성이 부족하면 기동 실패한다. 임의 ready=true를 쓰지 않는다. 실Store/운영 준비는 [배포 확인](../runbooks/TMI-212-public-sync-wiring.md)으로 별도 검증한다. 장애 시 ingress 수신과 worker 실행은 별도로 멈출 수 있지만 sync ON/worker OFF 재기동은 거절한다. 수신만ON이면 적체 용량·보존을 감시한다. 환불 processor/revoke 준비보다 판매/paid admission을 먼저 켜지 않는다.

## 6. 부록 — 테스트와 조사 근거

### 완료 기준

- HMAC raw whitespace/body 변조·wrong auth·header 중복/시간 경계·재서명 retry·unknown type durable ignored·commit 유실200/503 수렴.
- 양 Store/환경별 SDK→webhook→API→사업key·original owner·반복 구매·alias/restore/탈퇴·quantity·null/누락/unknown enum을 실제 fixture와 가짜 회귀로 구분.
- sync hash-only 대조·다페이지/중간 장애·empty/404·query injection/redirect·response cap·Retry-After·shared quota·idle poll 부하.
- source/sync/cursor pending 원자성, lease 만료·stale epoch·동시worker·crash after apply/before completion, schema 구버전/rollback.
- webhook/API 금액 순서가 달라도 중복 지급 없음, 환불 선행 후 owned 재수신도 재지급 없음, reviewer 재개에도 중복 금융 효과 없음.
- 무료/read/JWT/Identity/LC 전체 회귀. `./gradlew clean test bootJar`, 실제 replica-set Testcontainers. 실제 RC/Store는 자동 단위 테스트에서 호출하지 않음.
- sandbox 실구매는 별도 승인된 환경에서 검증. dashboard test webhook만으로 구매/소유/부분환불 검증 완료로 표시하지 않음.

### 공식 출처 (2026-10-13 열람)

- [S1 Webhooks](https://www.revenuecat.com/docs/integrations/webhooks): HMAC·Authorization·재전송 규격. 재시도 때 delivery 서명이 갱신되며 event ID는 유지된다. 실제 프로젝트 설정 미조회.
- [S2 v2 Purchase](https://www.revenuecat.com/docs/api-v2/purchase): one-time 검색/단건, Store ID와 RC ID, 읽기 권한·기본 Customer Information480회/분. 실제 키 권한/응답 미조회.
- [S3 Customer resources](https://www.revenuecat.com/docs/api-v2/customer/resources): 고객별 purchases·environment·pagination. SDK App User ID와 실제 v2 고객 매핑은 실증 필요.
- [S4 Event types and fields](https://www.revenuecat.com/docs/integrations/webhooks/event-types-and-fields): one-time event·Store transaction 필드. 환불 금액·부분환불 지원 완료를 뜻하지 않음.
- [S5 iOS StoreTransaction source](https://github.com/RevenueCat/purchases-ios/blob/main/Sources/Purchasing/StoreKitAbstractions/StoreTransaction.swift): transactionIdentifier. main source 참조는 앱 버전 고정을 대신하지 않음.
- [S6 Android9.18.0 StoreTransaction](https://sdk.revenuecat.com/android/9.18.0/purchases/com.revenuecat.purchases.models/-store-transaction/index.html): nullable orderId와 별도 purchaseToken. 이 버전을 앱이 사용한다는 뜻은 아님.

### 코드 근거

- [PaymentSyncService](../../src/main/java/web/tosunsaeng/billing/domain/payment/sync/PaymentSyncService.java): HMAC hint 저장·source binding/complete·미처리 집계.
- [PaymentSyncSchema](../../src/main/java/web/tosunsaeng/billing/domain/payment/sync/PaymentSyncSchema.java): strict r1, worker lease/due 필드 미포함.
- [PurchaseObservation](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseObservation.java): 검증 adapter 전용 입력·STORE_PAID 금액·3종 kind.
- [PurchaseCore](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCore.java): 접수/지급/격리·중복 거래 처리.
- [PublicSecurityConfig](../../src/main/java/web/tosunsaeng/billing/global/config/security/PublicSecurityConfig.java): worker 미연결 sync ON 차단.

최초 계획 작성은 공식 문서와 로컬 정적 조사만 수행했다. 이후 사용자 승인으로 TMI-212를 생성하고 선행 A adapter·금액 보완을 구현했으며 `./gradlew clean test bootJar` 1,268개가 통과했다. 현재 구현 범위와 미완료 B/C/D는 문서 상단 및 [구현 안내](../runbooks/TMI-212-revenuecat-adapter.md)를 따른다. 실제 provider 호출·dashboard 변경·secret 조회·앱 SDK 버전 확인·배포는 수행하지 않았다.
