# Billing Service 현재 상태

- 최종 갱신일: 2026-09-07
- 현재 브랜치: `develop`
- Jira: 무료/owner lifecycle 관련 Billing `TMI-120`, Identity `TMI-123`, Learning Core `TMI-125` 구현은 각 `develop`에 병합됐다. 무료 reader PLAN-007은 승인 후 구현·로컬 검증을 완료했으며 Jira는 미생성이다. 결제 ADR-004는 초안이며 별도 결제 PLAN·Jira·코드는 아직 생성하지 않았다.

## 2026-09-07 현재 우선 작업 — 무료 사용권 public reader PLAN-007

- Git 인계: 현재 develop의 미커밋 변경을 새 codex/feature 브랜치로 옮겨 사용자 직접 commit/push 후 develop 대상 PR을 만드는 명령을 안내했다. 기존 미커밋 결제 설계 문서도 있으므로 docs 전체 staging 시 함께 포함됨을 구분한다. Codex는 Git 변경·PR 생성을 실행하지 않았다.

- 사용자 화면 선호: 신규 무료 응시와 중단 후 허용된 재응시를 모두 '무료 모의고사 1회 남음'으로 표시하는 방향이다. 현재 FREE_EXAM_ONCE의 newAttempt/retake ALLOWED를 화면에서 통합하되 availableQuantity(새 INITIAL 수량)·ledger 의미는 유지하는 방안을 안내했다. GRADING/예약/자격 불명은 별도 상태로 표시하고 wire/앱 코드는 이번 대화에서 수정하지 않았다.

- 조회 설명 확인: 중단 후 RETAKE_AVAILABLE은 신규 수량 0/newAttempt BLOCKED/usage INCOMPLETE/hasInProgress false/retake ALLOWED이며 current-owned group ID를 제공한다. 단순 앱 종료로 OPEN/ACTIVE가 남으면 hasInProgress true여도 replacement는 가능하다. 실시간 접속 의미가 아니며 reserve가 최종 판정한다. 이번 설명 요청에서는 코드 변경·테스트 재실행 없음.

- [PLAN-007](../plans/PLAN-007-public-free-entitlement-query.md) 사용자 승인 후 구현했다. 결제·RevenueCat·상품·paid schema는 제외했고 Jira는 생성하지 않았다. [OpenAPI](../openapi/free-entitlements.yaml), [배포·이관 안내](../runbooks/PLAN-007-public-reader-rollout.md)를 추가했다.
- link/session/reservation의 sessionOwnerEpoch, link sessionBindingVersion CAS를 기존 Transaction에 구현했다. 실제 PHONE_REJOIN만 epoch 증가, Guest merge는 보존하며 replay는 추가 CAS 없이 기존 결과를 반환한다. command TTL은 변경하지 않았다.
- 조회는 explicit primary/SNAPSHOT·driver CSOT 2초 budget·최대 두 번 시도, bounded batch, 순수 free reader registry와 reserve 공통 predicate를 사용한다. GET 업무 write 없음과 command 삭제 뒤 정상 귀속을 실제 Mongo에서 검증했다.
- legacy migration은 명시적인 <=100 Session batch의 dry-run/CAS 방식이다. 미이전 subject·exact 최신 continuation만 자동 증명하며 그 밖은 BLOCKED로 남긴다. 실제 운영 migration/coverage는 수행하지 않았다. 기존 legacy RESERVED는 writer 전환 전 drain/expire 또는 승인 이관이 필요하다.
- API: GET `/api/v1/entitlements`, public isSuccess/code/message/result envelope, JWT sub·Billing audience·billing:read. Guest/MEMBER 모두 본인 read 허용, phone 자격은 별도 검증이다.
- 판정: lazy Grant 부재라도 VERIFIED/retained Claim 부재면 예상 신규 수량 1. 완료 번호는 0. 신규 수량/진행/재응시/사용 완료를 분리하고 조회에서 발급·차감·예약·owner 변경을 하지 않는다.
- 연동 불명은 200 PENDING/null, 저장소·불변식 오류는 503. source 계정의 group/Session/답안/결과를 새 계정에 공개하지 않고 승인 owner 이전 뒤에만 최소 group 재응시 상태를 표시한다.
- Identity 로컬 JwtAccessTokenIssuer는 account_type을 발급하지만 단일 설정 audience를 유지한다. PR #39 병합은 Billing audience/read 배포 완료가 아니다. 별도 발급 후속·새 token·staging E2E가 필요하다.
- 보안: public connector/ALB exact allowlist/JWT를 기존 internal Lattice/SigV4와 격리한다. reader flag 기본 OFF이며 Billing OFF 배포→Identity→staging→reader ON→앱 순서다.
- 검증: `./gradlew clean test` 성공, 236 tests / 실패 0 / skip 0. Docker replica-set snapshot·command listener 무쓰기·confirm 경합·owner CAS·command 삭제·이관, JWT 로컬 JWKS·scope, 실제 public/internal 포트·production Controller INTERNAL span·baggage 미전파 테스트 포함. OpenAPI YAML 파싱과 `git diff --check` 통과.
- 기존 테스트 fixture 두 종류도 보정했다: schema v4 initializer에 schema=3을 넘기던 테스트 3곳, mockExamId를 mock-1로 고정하던 confirmed-group helper. 운영 코드의 기존 wire·index schema는 변경하지 않았다.
- 다음 작업: Identity aud/read 발급 인계 확인, public ALB/SG/Lattice·JWKS·legacy coverage 및 staging E2E. reader/connector 기본 OFF, 배포·Git commit/push·타 서버 수정 없음. payment ADR-004 D1·D2/별도 PLAN은 후속 트랙이다.

## 2026-09-07 ADR-004 기술 초안 작성 기록

- 사용자 요청으로 [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md)를 작성했다. 기존 제품/9개 선택은 유지하고 public DTO·오류·한도, RC webhook/API v2, 계정 binding, payment Transaction, Mongo v5 index, paid/free guard, LC durable revoke와 배포·테스트 gate를 구체화했다.
- 작성 중 발견한 D1 정상 만료 중 현재 Session의 완료 여부, D2 Apple REFUND_REVERSED 후 복구 방식은 미확정 권장안으로 분리했다. 문서 작성 전의 '추가 제품 결정 없음' 예상과 달리 이 두 예외에는 사용자 확인이 필요하다. C9-S9에 기록했다.
- 공식 Purchase API/OpenAPI에서 owned/refunded, original_customer_id, string store_purchase_identifier와 검색/get route를 확인했고 Customer Resources에서 환경별 사용자 구매 목록·pagination을 확인했다. 앱 SDK 식별자 일치와 consumed 환불은 sandbox 검증 전이다.
- 신규 LC route는 `POST /internal/v1/entitlements/access/events`, event는 `AttemptGroupAccessRevoked` v1 초안이다. 실제 상대 reader·paid GRADING 실행 gate·Lattice 리소스를 배포한 것으로 간주하지 않는다.
- Google obfuscated identifier가 raw UUID와 같은 wire 값이라는 단정을 제거하고 SDK별 변환 검증 gate로 명시했다. 원 사용자 고정·alias 자동 이전 금지는 유지한다.
- 검증: 문서만 변경하므로 Gradle 미실행. `git diff --check`, 새 ADR의 로컬 링크 10개와 JSON 예제 4개 문법 검사를 통과했다. 기존 미커밋 문서 변경을 보존했으며 코드·Jira·외부 리소스·Git 이력은 변경하지 않았다.
- 결제 후속 작업: D1·D2 확인과 ADR 상세 검토 뒤 별도 결제 PLAN 작성. PLAN-007은 무료 reader에 배정됐다. 실제 product ID/가격/국가·credential/hostname은 결제 출시 입력으로 준비한다.

## 2026-09-07 RevenueCat 결제 연동 채택

- 사용자는 Apple/Google fixed-term 결제에 RevenueCat을 사용하는 안을 승인했다. RevenueCat은 표준 모바일 SDK·Offering/Package, Store transaction 정규화, HMAC webhook과 API 조회를 제공하는 결제 연동 계층으로 사용한다.
- 연동 편의성을 실제로 얻기 위해 RevenueCat 표준 SDK가 Apple transaction finish와 Google consumable completion을 맡는다. 기존 `Billing local commit → Apple finish/Google backend consume` 순서는 supersede됐다.
- Store 결제 완료 후 Billing 반영이 늦어질 수 있으므로 앱 callback으로 entitlement를 열지 않는다. RevenueCat HMAC webhook 또는 인증된 API 조회로 Store transaction을 확인하고 event ID·Store transaction unique와 Mongo Transaction으로 반영하며, 그 전에는 `PENDING`으로 둔다.
- RevenueCat custom App User ID에는 사용자·환경별 stable lowercase UUID v4 `purchaseAccountRefId`를 사용한다. 익명 구매와 RevenueCat alias/restore만을 근거로 한 토선생 계정 간 유료권 자동 이전은 허용하지 않는다.
- RevenueCat Entitlement는 consumable을 만료 없이 표현하므로 1·3·7·14·30일 권리 source로 사용하지 않는다. Billing catalog·Purchase·SubscriptionEntitlement·ledger가 기간, stacking, 무료권 보존, Reservation과 환불 차단의 authoritative source다.
- Apple/Google Platform Server Notifications는 RevenueCat에 연결하고 Billing은 RevenueCat webhook을 public provider ingress로 받는다. webhook retry 종료와 API 장애를 전제로 scheduled reconciliation과 운영 경보를 유지한다.
- ADR-004에서 exact RevenueCat project/app·Offering/Package, restore/transfer behavior, webhook route/HMAC·timestamp, client sync DTO, API 조회, Mongo index와 장애 UX를 확정한 뒤 PLAN/Jira/구현으로 진행한다.

## 2026-09-07 ADR-004 권장 선택 승인

- 제품 정책은 재결정하지 않는다. 양 Store consumable one-time, `PREMIUM_1D/3D/7D/14D/30D`, 24·72·168·336·720시간, ACTIVE MEMBER, Billing 발급 `purchaseAccountRefId`, paid-first/free-preserve, stacking/reflow, strict refund 상태표와 5년/120일/35일 보존은 이미 승인됐다.
- 사용자는 (1) public API 분리, (2) client sync hint/idempotency, (3) webhook 인증·fast durable ack, (4) RevenueCat sandbox/production routing, (5) anonymous/restore/transfer, (6) S2S-only 신규 구매 추적, (7) 자동 refund data handling, (8) webhook direct apply 대 API 재조회, (9) Learning Core access-revocation delivery의 권장안을 모두 승인했다.
- 확정안은 resource별 public route, lowercase UUID v4 idempotency와 untrusted Store transaction hint, Authorization+HMAC 5분 replay window, durable inbox 후 200/async worker, 같은 RevenueCat project의 environment-filtered webhook 두 개와 secret 분리, original App User ID 유지·익명 구매 금지, S2S-only tracking/refund auto handling 초기 OFF, 인증된 webhook direct normalize+API reconciliation, per-AttemptGroup durable Lattice revoke event다.
- exact Mongo schema/index/state machine, UTC/exclusive time, raw-body limit, retry/backoff, low-cardinality metric와 secret rotation은 승인 정책을 구현하는 기술 계약으로 ADR에서 작성한다. provider event unknown field는 RevenueCat의 forward-compatible 계약에 맞춰 허용하되 required field/type/size/event handling은 fail-closed 또는 durable ignored disposition으로 명시한다.
- 실제 Store product ID·가격·판매 국가, public hostname/certificate/listener, Store review, RevenueCat secret/API quota와 sandbox test account는 출시 입력이며 config placeholder/fake adapter 기반 백엔드 구현을 막지 않는다.
- RevenueCat 공식 문서는 optional Authorization header와 HMAC signing을 모두 지원하며 HMAC header는 `timestamp + raw body`의 SHA-256 서명이고 retry마다 새 timestamp/signature를 만든다고 확인했다. 5분 window는 delivery retry 간격이 아니라 각 HTTP 전송 시각 검증에만 사용한다.
- 이 승인 선택에 기반한 ADR-004 초안을 작성했다. 후속 상태와 추가 예외 정책은 위 `ADR-004 기술 초안 작성` 절을 따른다. ADR·PLAN 승인 전에는 payment application code를 구현하지 않으며 Jira 변경은 별도 사용자 승인을 받는다.

## 2026-09-07 Google Play 일회성 제품 등록 준비

- Google 최신 일회성 제품 객체 모델은 `일회성 제품 → 구매 옵션 → 선택적 혜택` 구조다. 경로는 `Play를 통한 수익 창출 > 제품 > 일회성 제품 > 일회성 제품 만들기`다.
- 토선생은 Billing이 24·72·168·336·720시간을 관리하므로 Google `대여`가 아니라 `구입` 구매 옵션을 사용한다. 각 제품에는 기본 구매 옵션 하나를 두고 수량 구매·선주문·할인 혜택은 최초 출시 범위에서 사용하지 않는다.
- 권장 영구 product ID는 `premium_1d`, `premium_3d`, `premium_7d`, `premium_14d`, `premium_30d`이며 각 제품의 purchase option ID는 공통 `standard`를 권장한다. 실제 생성 전 사용자의 이름·가격 승인이 필요하다.
- 이름은 `토선생 프리미엄 1일/3일/7일/14일/30일 이용권`, 설명은 정확한 시간 동안 프리미엄 모의고사를 이용하는 자동 갱신 없는 상품임을 명시한다. Google의 분류는 `서비스`, 구매 유형은 `구입`, 단일 거래 수량 옵션은 끈다.
- 제품별 한국 가격과 판매 국가를 입력하고 구매 옵션까지 `활성`으로 만들어야 Play Billing/RevenueCat import에 노출된다. 앱 판매 국가가 제품 판매 국가를 최종 제한한다.
- Play Console 생성 후 RevenueCat에 Google app package/service credential을 연결하고 5개 product를 import한다. RevenueCat Offering/Package에 연결하되 consumable을 RevenueCat Entitlement에는 붙이지 않는다.
- 공식 근거: `https://support.google.com/googleplay/android-developer/answer/16430488?hl=ko`, `https://support.google.com/googleplay/android-developer/answer/14590082?hl=ko`, `https://www.revenuecat.com/docs/projects/connect-a-store`.

## 2026-09-07 Google Play 결제 프로필 생성 안내

- 공식 경로는 Play Console `설정 > 결제 프로필 > 결제 프로필 만들기`다. 기존 Google 결제 프로필이 선택 목록에 있으면 새로 만들기 전에 토선생 개발자 계정과 법적 주체가 동일한지 확인한다.
- 국가/지역은 대한민국으로 선택하며 생성 후 변경할 수 없다. 결제 프로필 국가와 판매대금을 받을 거래 은행 계좌의 등록 국가가 같아야 한다.
- 개인/사업자·조직 유형, 법적 이름, 사업자명과 주소는 Play 개발자 계정과 사업자등록증·공식 주소 문서·세금·은행 예금주 정보에 맞춘다. 사서함 주소는 허용되지 않는다.
- 기본 연락처에는 실제로 Google 확인 요청에 응답할 대표자 또는 공식 담당자를 입력한다. 공개 판매자 정보에는 실제 운영 웹사이트, 교육/디지털 서비스에 가까운 카테고리, 모니터링하는 고객지원 이메일과 구매자가 알아볼 수 있는 명세서 표시명을 입력한다.
- 제출 후 세금 정보, 판매대금 은행 계좌 등록·인증과 필요 시 판매자 신원 확인을 이어서 완료한다. 실제 법적 이름·주소·전화·계좌·세금 식별자는 채팅·Jira·Git에 기록하지 않는다.
- 공식 근거: `https://support.google.com/googleplay/android-developer/answer/7161426?hl=ko`.

## 2026-09-07 Google Play 결제 프로필 생성 후 상품 메뉴 확인

- 첨부 화면 기준 결제 프로필은 Play Console에 연결됐고, 매출 0원·거래 없음은 아직 판매가 없다는 의미다. `지급받을 방법 > 결제 수단 추가`는 정산 은행 계좌 등록이며 상품 생성 메뉴 활성화와는 별개일 가능성이 높지만 실제 판매대금 수취 전에는 완료해야 한다.
- 일회성 제품 메뉴가 없거나 생성할 수 없다면 현재 Play에 업로드된 app bundle이 Google Play Billing 기능을 선언했는지 먼저 확인한다. RevenueCat을 사용할 경우 모바일 앱 기술 스택에 맞는 RevenueCat SDK를 추가한 새 AAB를 내부 테스트 트랙에 업로드하고 Play의 artifact 처리가 끝난 뒤 다시 확인한다.
- 네이티브 Android에서는 최종 merged manifest에 `com.android.vending.BILLING` permission이 있어야 한다. Flutter·React Native 등은 해당 RevenueCat 플러그인의 설치·빌드 절차를 따르므로 앱 기술 스택을 확인하기 전 dependency 구문을 고정하지 않는다.
- artifact 처리 뒤 `Play를 통한 수익 창출 > 제품 > 일회성 제품`에서 제품 5개와 각 `구입` 구매 옵션을 생성한다. 그래도 차단되면 해당 메뉴의 정확한 안내/오류 문구를 확인해 계정 검증, 앱 상태 또는 권한 문제를 구분한다.
- 15% 서비스 수수료 프로그램 등록은 선택 정책이며 상품 생성의 필수 선행조건으로 보지 않는다.
- 공식 근거: `https://support.google.com/googleplay/android-developer/answer/1153481?hl=ko`, `https://www.revenuecat.com/docs/getting-started/installation/android`.

### 2026-09-07 정확한 차단 문구 확인

- 사용자가 확인한 문구는 `Google Payments 판매자 계정을 설정해야 이 페이지에 액세스할 수 있습니다`다. 따라서 현재 직접 차단 원인은 앱 artifact의 Billing permission이 아니라 Google Payments 판매자 계정이 아직 Play Console에서 완료·연결 상태로 인식되지 않는 것이다.
- `설정 > 결제 프로필`에서 미완료 경고, 법적 주체·주소·판매자 정보 확인과 Google이 요구하는 인증 상태를 먼저 확인하고, 상품 페이지의 `판매자 계정 설정` 동작이 있으면 현재 Play 개발자 계정과 동일한 법적 주체의 프로필을 선택해 연결한다.
- 첨부 화면의 은행 계좌 추가는 실제 정산을 위해 필요하지만, 표시된 차단 문구만으로 계좌 미등록이 유일한 원인이라고 단정하지 않는다. 설정을 막 끝냈다면 계정 간 반영 지연, 다른 Google 계정/개발자 계정 선택 여부와 브라우저 세션도 확인한다.
- 판매자 계정이 완료·연결된 뒤에도 제품 생성에는 Billing 기능이 포함된 Play artifact가 필요할 수 있으므로 RevenueCat SDK/AAB 단계는 판매자 계정 차단을 해소한 다음 진행한다.

## 2026-09-07 RevenueCat 계정 준비와 백엔드 선행 개발 순서

- Google 정산 계좌 인증을 기다리는 동안 RevenueCat 계정과 토선생 프로젝트를 생성한다. 조직 소유 이메일과 MFA를 사용하고 팀 권한은 최소화한다.
- 한 RevenueCat project 아래 실제 bundle ID의 iOS app과 package name의 Android app을 등록하는 것을 기본 출발점으로 한다. 별도 staging bundle/package가 실제로 존재할 때만 별도 project/app 격리를 확정하며, sandbox transaction 표시는 production entitlement 판정과 구분한다.
- Google 제품이 아직 없어도 계정·project/app 생성은 가능하다. Store credential 연결, 제품 import, Offering/Package와 webhook 활성화는 계좌/상품 준비와 ADR-004 exact 계약 뒤에 수행한다.
- consumable fixed-term 제품은 RevenueCat Entitlement에 연결하지 않는다. App User ID는 raw userId가 아니라 기존 `purchaseAccountRefId`를 쓰며 익명 구매는 허용하지 않는다.
- Billing backend는 RevenueCat 계정 없이도 provider port/fake adapter, catalog, Purchase·기간형 entitlement·ledger, strict webhook contract, Mongo schema/index와 멱등/환불 테스트를 개발할 수 있다. 실제 secret과 Store 호출은 저장소·테스트에 넣지 않고 sandbox/staging gate에서만 사용한다.
- 구현 선행조건은 RevenueCat 계정 자체가 아니라 ADR-004와 PLAN-007의 승인이다. project/app identifier, SDK/API key 구분, webhook 인증, sandbox 판정, API reconciliation과 restore/transfer behavior를 exact contract로 먼저 고정한다.
- 사용자가 RevenueCat 계정과 토선생 project 생성을 완료했다. 공유된 account-scoped URL은 별도 브라우저 세션에서 로그인이 필요해 내부 설정은 확인하지 않았으며 credential을 요청하거나 입력하지 않았다.
- 다음 Console 작업은 project의 `Apps & providers`에서 실제 Android package name으로 Google Play app을 추가하는 것이다. Google service credential·제품 import·Offering/Package·webhook은 아직 필수가 아니며 Store 판매자 계정/상품과 ADR-004 준비 뒤 연결한다. iOS app도 실제 bundle ID가 확정돼 있으면 같은 project에 등록할 수 있다.
- Apple app 설정만으로 양 Store 연동이 완료되지 않는다. 동일 RevenueCat project에 Google Play app을 별도로 추가하고 package name, Google service credential과 RTDN을 구성해야 한다.
- Google 판매자 계정/은행 인증 대기 중에도 RevenueCat Google app 등록과 service account 준비는 가능하다. 일회성 제품 5개 생성·import·Package 연결과 실제 결제 테스트는 판매자 계정 및 Play product 접근이 열린 뒤 수행한다.
- Android package name은 Play production 출시 후 생성되는 값이 아니라 앱 build configuration의 `applicationId`로 이미 정해진 immutable Store 식별자다. AAB를 Play Console에 업로드한 현재 단계에는 이미 존재하므로 공개 게시를 진행하지 않고도 확인해 RevenueCat에 등록할 수 있다.
- authoritative 확인 위치는 모바일 source의 Android app module `defaultConfig.applicationId`다. `namespace`와 다를 수 있으므로 namespace를 대신 입력하지 않는다. Play Console 앱 URL/앱 세부정보에 표시되는 package와 source `applicationId`가 exact match하는지도 확인한다.

### RevenueCat Google service account credential 설정 기준

- RevenueCat 전용 Google Cloud service account를 만들고 기존 Firebase Admin/backend credential을 재사용하지 않는다. 선택한 Cloud project에서 Android Publisher API, Play Developer Reporting API와 Pub/Sub API를 활성화한다.
- service account의 Cloud role은 RevenueCat 공식 절차 기준 `Pub/Sub Editor`, `Monitoring Viewer`를 부여한다. 그 계정에서 JSON key를 1회 생성해 RevenueCat Google app 설정에 직접 업로드한다.
- 두 Cloud role은 Play Console이 아니라 해당 Google Cloud project의 `IAM 및 관리자 > IAM`에서 service account principal을 편집해 부여한다. 이미 계정이 있으면 principal 행의 수정에서 `역할 추가`를 두 번 사용하고, 생성 중이면 `이 서비스 계정에 프로젝트 액세스 권한 부여` 단계에서 같은 역할을 선택한다.
- 사용자가 RevenueCat service account에 `Pub/Sub Editor`, `Monitoring Viewer` 역할 부여를 완료했다. 다음 단계는 `IAM 및 관리자 > 서비스 계정 > 해당 계정 > 키 > 키 추가 > 새 키 만들기 > JSON`으로 user-managed private key를 생성해 RevenueCat에 직접 업로드하는 것이다.
- JSON은 생성 즉시 한 번 다운로드되는 private credential이다. 내용을 열어 복사하거나 파일명/내용을 기록하지 않고 secret manager에 보관하며 Git·문서·Jira·채팅·일반 공유 드라이브에 저장하지 않는다. RevenueCat validator 통과 뒤에도 rotation/revocation 가능한 key inventory를 유지한다.
- 사용자가 JSON을 RevenueCat에 업로드해 `File saved`까지 도달했으나 validator는 `Service account credentials need attention / unable to validate` 상태다. 이는 JSON 저장 실패가 아니라 Google API access 검증 미완료를 뜻한다.
- 신규 service account·Play permission은 공식 안내상 최대 36시간 전파될 수 있으므로 즉시 key를 삭제/재생성하지 않는다. service account enabled, Play 사용자 active, 토선생 app access와 네 account permission, 세 Google API, 두 Cloud role과 AAB 업로드를 확인하고 기다린 뒤 재검증한다.
- 36시간 뒤에도 실패하면 RevenueCat validator의 endpoint/permission별 상세 결과를 기준으로 수정하고 JSON을 다시 업로드해 검증을 트리거한다. 현재 Google Payments 판매자 설정/제품 API 접근이 진행 중인 점도 monetization endpoint 검증에 영향을 주는지 함께 확인한다.
- Google Play Console `사용자 및 권한`에서 service account email을 사용자로 초대하고 App permissions에 토선생 앱을 추가한다. Account permissions에는 `View app information and download bulk reports (read-only)`, `View financial data, orders, and cancellation survey responses`, `Manage orders and subscriptions`, `Manage store presence` 네 권한을 부여한다.
- 현재 Play Console 한국어 UI에서 위 네 권한은 각각 `앱 정보 보기 및 보고서 일괄 다운로드(읽기 전용)`, `재무 데이터, 주문, 취소 설문조사 응답 보기`, `주문 및 구독 관리`, `앱 정보 관리`다. 마지막 권한 설명에 가격 수정·인앱 상품 관리가 포함돼 RevenueCat 문서의 `Manage store presence`에 대응한다.
- service account 초대에서 `관리자(모든 권한)`, 출시/테스트 트랙, 정책, 리뷰, Play Games, 딥 링크와 Android 개발자 인증 권한은 부여하지 않는다. 머신 연동이 지속돼야 하므로 Play access 만료는 기본적으로 설정하지 않고 key rotation/revocation으로 관리한다.
- `Manage store presence`는 RevenueCat에서 Play product를 생성/수정할 때 필요하다고 공식 문서가 명시한다. 첫 출시에서는 RevenueCat의 product mutation을 사용하지 않더라도 공식 validator 통과와 향후 import/관리 계약을 위해 위 네 권한을 exact 기준으로 삼는다.
- JSON은 private key가 포함된 secret이므로 Git·문서·Jira·채팅에 넣지 않고 RevenueCat에 직접 업로드하며 승인된 secret manager에서만 보관한다. 생성/권한 변경 후 Google 전파에는 최대 36시간이 걸릴 수 있고 RevenueCat `Valid credentials` validator로 최종 확인한다.
- 공식 근거: `https://www.revenuecat.com/docs/service-credentials/creating-play-service-credentials`.

## 2026-09-07 RevenueCat iOS app 등록 입력 기준

- `App name`은 RevenueCat 내부 구분명으로 `토선생 iOS`를 권장한다. `App Bundle ID`는 Xcode target의 `PRODUCT_BUNDLE_IDENTIFIER` 및 App Store Connect 앱 정보와 exact match해야 하며 추측해서 새 값을 만들지 않는다.
- Custom URL Scheme은 RevenueCat Paywall preview/deep link용 선택 설정이다. 현재 앱에 등록된 scheme을 확인하기 전 임의 값을 넣지 않고 최초 연동에서는 비워둘 수 있다. 사용할 경우 Xcode URL Types/Info.plist에도 같은 scheme을 등록해야 한다.
- RevenueCat의 In-App Purchase Key는 App Store Connect의 `사용자 및 액세스(Users and Access) > 통합(Integrations) > 인앱 구입(In-App Purchase)`에서 생성한 전용 `.p8` key다. 일반 App Store Connect API key나 APNs key와 혼용하지 않는다.
- `.p8`는 한 번만 다운로드할 수 있는 credential로 직접 RevenueCat에 업로드하고 승인된 secret manager에만 보관한다. Git·문서·Jira·채팅에 파일이나 내용을 넣지 않는다. Key ID와 Issuer ID는 App Store Connect 표시값을 exact 입력한다.
- Apple Small Business Program 시작일은 실제 승인·효력 발생일이 있을 때만 입력하며 신청일을 임의로 넣지 않는다. 현재 미가입/심사 중이면 비워둔다.
- App-specific shared secret은 StoreKit 1 또는 RevenueCat 안내상 지원 대상 iOS 버전 때문에 필요한 경우에만 추가한다. 새 StoreKit 2 중심 연동에서는 legacy field를 기본적으로 비워두되 실제 iOS deployment target과 SDK 구성을 ADR-004/모바일 구현 전에 확인한다.

### App Store Connect API·Apple notification 추가 설정 기준

- `AuthKey_*.p8` App Store Connect API key는 앞의 `SubscriptionKey_*.p8` In-App Purchase key와 다른 credential이다. 전자는 RevenueCat의 product import·가격 metadata 동기화용이고 후자는 StoreKit transaction 검증용이므로 파일과 Key ID를 혼용하지 않는다.
- App Store Connect `Users and Access > Integrations > App Store Connect API`의 team key를 최소 필요 권한으로 생성해 RevenueCat에 직접 업로드한다. `.p8`는 1회 다운로드 credential이므로 secret manager에만 보관하고 Git·문서·Jira·채팅에 넣지 않는다.
- Apple Server Notification v2는 Store refund/revoke를 RevenueCat이 받도록 RevenueCat 제공 URL을 App Store Connect의 production·sandbox notification URL에 설정하는 방향을 유지한다. Billing은 Apple raw notification을 직접 받지 않고 인증된 RevenueCat webhook을 받는다.
- Apple Server Notification Forwarding URL은 현재 구조에서 비워둔다. RevenueCat과 Billing 양쪽에 Apple 원문을 중복 전달하는 별도 ingress는 ADR 승인 전 추가하지 않는다.
- `Track new purchases from server-to-server notifications`는 SDK에서 아직 식별되지 않은 구매를 RevenueCat이 새 구매로 추적할 수 있으므로 익명 구매 금지·`purchaseAccountRefId` exact binding과 충돌할 수 있다. ADR-004에서 식별/멱등 계약을 확정하기 전에는 OFF다.
- Refund request handling/Apple consumption data 전송은 Apple 환불 심사에 사용 데이터가 전달되는 정책·개인정보 기능이므로 자동 활성화하지 않는다. Retention Messaging API와 Subscription Offer key는 현재 consumable one-time 상품 범위 밖이다.
- RevenueCat Public SDK key는 모바일 앱에 포함 가능한 공개 식별자이고 server secret key와 다르다. 화면의 `REST API Identifier` (`app...`)는 RevenueCat app resource 식별자이며 SDK key나 product ID로 사용하지 않는다.
- Apple notification 설정 시 RevenueCat의 `Apple Server Notification URL`을 복사해 App Store Connect의 토선생 앱 `앱 정보 > App Store 서버 알림`에 production·sandbox URL로 등록하고 Version 2를 선택한다. `Apple Server Notification Forwarding URL`은 복사 대상이 아니며 비워둔다. 저장 직후 `No notifications received`는 실제 sandbox/production 이벤트 전까지 정상이다.
- 사용자가 Apple Server Notification v2 URL의 App Store Connect 등록을 완료했다. 이는 Apple→RevenueCat 알림 목적지 설정 완료를 의미하며, 실제 수신 검증·Apple credential·상품 import·RevenueCat Offering 및 Billing webhook/ledger 구현 완료를 의미하지는 않는다.

## 2026-09-07 RevenueCat 도입 적합성 검토

- RevenueCat은 iOS·Android consumable 구매 SDK, Offerings/Packages, Paywall, 구매 이벤트 정규화, webhook과 매출 분석을 제공하므로 앱의 Store별 결제 UI·SDK 유지보수와 성장 실험은 줄일 수 있다.
- 사용자 관점에서 가장 큰 도입 효과는 Apple·Google별 상품 조회·구매·오류·복원 UI를 공통 SDK와 Offering으로 다루고, 가격·상품 노출·Paywall 문구를 앱 심사/배포 없이 원격으로 바꾸며, 양 Store 매출·전환을 한 화면에서 보는 것이다. Store 정책/API 변경 대응도 RevenueCat SDK 업데이트로 상당 부분 흡수할 수 있다.
- 서버 관점에서는 Apple Notification V2와 Google RTDN의 서로 다른 payload를 각각 해석하는 대신 RevenueCat webhook을 공통 ingress로 받을 수 있다. 다만 webhook 인증·멱등성·누락 reconciliation과 토선생 기간형 권리 원장은 계속 필요하므로 `결제 plumbing 감소`이지 Billing 제거가 아니다.
- 현재 토선생 상품은 1·3·7·14·30일을 Billing catalog가 부여하는 consumable one-time product다. RevenueCat 공식 계약상 consumable을 Entitlement에 연결하면 만료 없이 영구 unlock으로 보이며, 실제 지급·소진 상태는 RevenueCat이 관리하지 않는다. 따라서 기간 계산, timeline stacking, 무료권 보존, Reservation, 환불 차단과 ledger는 계속 Billing이 authoritative하게 관리해야 한다.
- 도입 검토 당시 표준 RevenueCat SDK가 purchase completion을 기본 수행하므로 기존 `Billing Transaction commit → Apple finish/Google consume` 순서를 유지하려면 `purchasesAreCompletedBy=.myApp` 방식이 필요하다고 확인했다. 이후 RevenueCat 채택과 함께 표준 completion 및 webhook/API reconciliation 방식이 승인됐다.
- webhook은 빠른 수신·HMAC 검증·event ID 멱등 처리가 필요하고 최대 5회 재시도 후 중단될 수 있다. at-least-once 중복과 지연을 전제로 inbox, provider 조회와 periodic reconciliation은 여전히 필요하다. 대부분 Store의 one-time refund 전체 수신을 위해 Apple/Google Platform Server Notifications 설정도 RevenueCat 측에 계속 연결해야 한다.
- 사용자 식별은 raw userId 대신 기존 사용자·환경별 stable lowercase UUID v4 `purchaseAccountRefId`를 RevenueCat custom App User ID로 사용하는 것이 현재 privacy/owner 계약과 맞다. 익명 ID alias/restore는 다른 토선생 계정으로 구매가 합쳐지거나 이전될 수 있으므로 구매 전 로그인 강제와 `Keep with original App User ID` 검토가 필요하다.
- 검토 당시 기본 권장안은 직접 StoreKit/Google Play Billing 유지였으나 사용자가 출시 편의성을 우선해 RevenueCat 도입을 승인했으므로 위 `RevenueCat 결제 연동 채택` 결정이 이를 supersede한다. RevenueCat은 Billing 대체가 아닌 Store adapter/event source다.
- 2026-09-07 공개 가격은 월 tracked revenue 2,500 USD까지 무료, 이후 tracked revenue의 1%다. 실제 one-time 매출 산정·세금·계약 조건은 가입 전 견적/약관에서 다시 확인한다.
- 공식 근거: `https://www.revenuecat.com/docs/platform-resources/non-subscriptions`, `https://www.revenuecat.com/docs/migrating-to-revenuecat/sdk-or-not/finishing-transactions`, `https://www.revenuecat.com/docs/integrations/webhooks`, `https://www.revenuecat.com/docs/customers/identifying-customers`, `https://www.revenuecat.com/docs/projects/restore-behavior`, `https://www.revenuecat.com/pricing`.

## 2026-09-07 Apple·Google 일회성 fixed-term 상품 최종 변경

- 사용자가 Apple과 Google 모두 재구매 가능한 일회성 상품으로 통일하는 안을 승인했다. Apple은 offer별 Consumable In-App Purchase 5개, Google은 offer별 consumable one-time product 5개를 사용한다.
- Store product ID는 `PREMIUM_1D/3D/7D/14D/30D`와 환경별 Billing catalog에서 exact mapping하며 권리 기간은 Store subscription expiry가 아니라 각각 24·72·168·336·720시간의 catalog duration으로 계산한다.
- 구매는 자동 갱신되지 않는다. active/scheduled 유료 권리가 있을 때 재구매하면 기존 timeline 뒤에 새 duration을 이어 붙이며 무료권은 보존한다.
- 이 시점에는 Billing 검증과 local commit 뒤 Apple finish/Google backend consume을 확정했으나, 2026-09-07 RevenueCat 채택으로 transaction completion은 RevenueCat 표준 SDK가 담당하는 것으로 supersede됐다. Store transaction unique로 권리를 한 번만 만드는 불변식은 유지한다.
- consumable Store restore를 과거 권리의 source로 사용하지 않고 Billing ledger와 current entitlement API를 사용한다. Store에는 아직 완료되지 않은 transaction 복구와 provider refund/revoke 재검증 책임을 둔다.
- Google prepaid의 14일 미지원과 달력 1개월/고정 30일 불일치는 이 변경으로 해소됐다. 실제 product ID 10개, 가격, 국가와 review metadata는 ADR-004 naming 확정 후 Console에서 생성한다.
- 현재 책임 경계는 `Store=상품·가격·실제 결제·거래/환불`, `RevenueCat=SDK·Offering·transaction completion·검증 데이터/webhook`, `Billing=product ID→offer 매핑·정확한 기간·stacking·권리 복원·무료권 우선순위`다. Store completion 뒤 Billing 반영 지연을 webhook/API reconciliation으로 복구하는 책임이 중요하다.

## 2026-09-07 Apple 결제 외부 준비 시작

- Apple 준비는 상품 ID 생성 전에 Developer Program 활성화, App Store Connect 앱/bundle record 존재, Paid Applications Agreement·세금·은행 정보 완료 여부를 먼저 확인한다.
- 사용자가 토선생 앱이 이미 App Store에 배포됐다고 확인했다. 따라서 Developer Program 가입과 App Store Connect 앱/bundle record는 준비된 것으로 본다. 단, 무료 앱 배포 이력만으로 Paid Applications Agreement·세금·은행 정보가 활성화됐다고 보지는 않으며 App Store Connect에서 별도 확인해야 한다.
- 위 조건이 준비된 뒤 App Store Connect에 offer별 Consumable In-App Purchase 5개를 생성한다. 기간은 Apple 상품 설정이 아니라 Billing catalog에서 관리한다.
- 실제 product ID, 가격, 판매 국가와 현지화는 아직 생성·확정하지 않았다. product ID는 생성 후 변경 제약이 있으므로 naming을 ADR-004에서 확정하기 전 임의 생성하지 않는다.
- 첫 사용자 확인사항은 기존 Apple Developer Program과 App Store Connect 앱 등록이 이미 완료되어 있는지 여부다.
- Apple 공식 절차상 IAP 판매에는 `Account Holder`가 `Business > Agreements > Paid Apps > View and Agree to Terms`에서 Paid Apps Agreement를 체결해야 한다. 계약 동의는 되돌릴 수 없으므로 계정의 법적 주체를 확인한 뒤 진행한다.
- 세금 정보는 `Business > Agreements > Tax Forms`에서 등록한다. 모든 개발자는 미국 세금 양식을 완료해야 하며, 미국 외 계정은 질문에 따라 W-8BEN/W-8BEN-E/W-8ECI 등이 제시된다.
- 한국 기반 개발자는 Apple 안내상 유효한 사업자등록번호와 최근 90일 이내 영문 사업자등록증명 또는 비영리 국세청 고유번호와 최근 90일 이내 영문 증명 서류가 추가로 요구된다. 정확한 법인/개인·영리/비영리 양식 선택은 계정 법적 주체와 세무 확인이 필요하다.
- 은행 정보는 Paid Apps Agreement 체결과 필수 세금 양식 제출 뒤 `Business > Agreements > Bank Accounts > Add Bank Account`에서 입력한다. Apple Developer Program의 법적 주체와 계좌 명의 정보가 정확히 일치해야 한다.
- 사용자가 App Store Connect에서 `Agreements`를 찾지 못했다. Apple 공식 경로는 `Business > Agreements`이며, `Business` 또는 그 안의 `Agreements`가 보이지 않으면 현재 로그인 계정이 Account Holder/Admin/Finance 권한인지와 여러 개발자 팀 중 토선생 앱 소유 팀을 선택했는지 먼저 확인한다. 계약 서명 자체는 Account Holder만 가능하다.
- 한국어 App Store Connect의 공식 메뉴명은 `비즈니스 > 계약 > 유료 앱 > 약관 보기 및 동의하기`다. 영문 안내의 `Agreements`가 한국어 화면에서는 `계약`으로 표시된다.
- Paid Apps 진행 중 `이름 확인 문서` 업로드가 표시됐다. 이는 세금 양식과 별도로 Apple 계정에 등록된 개인/법인의 법적 이름을 공식 사업자 또는 법원 문서로 증명하는 단계로 해석한다. 화면에 표시된 이름과 문서의 이름이 exact match해야 하며, 개인사업자는 사업자등록증/사업자등록증명, 법인은 사업자등록증명 또는 법인등기사항증명서처럼 발급기관과 법인명이 명확한 최신 원본을 우선 사용한다.
- `문서 언어: 한국어`이면 한국어 원문을 제출할 수 있다는 의미로 보고 임의 영문 번역본으로 바꾸지 않는다. 다만 이후 한국 세금 정보 단계에서 요구하는 최근 90일 이내 영문 사업자등록증명은 별도 제출물이다.
- 계정에 표시된 법적 이름이 문서와 다르거나 개인 계정인데 조직 문서를 요구하는 경우에는 임의 문서를 제출하지 않고 계정 유형/법인명 정정 또는 Apple Developer Support 확인을 우선한다.
- 이어서 `주소 확인 문서` 업로드가 표시됐다. 입력한 주소와 동일한 현재 사업장/본점 주소가 이름 확인 문서에 함께 적혀 있다면 동일한 사업자등록증·사업자등록증명 또는 법인등기사항증명서를 주소 확인에도 사용할 수 있다. 문서의 주소가 이전 주소라면 파일을 수정하지 않고 Apple 입력 주소를 공식 문서와 맞추거나 사업자/법인 주소 변경을 먼저 완료한다.
- 주소 문서는 상호/법인명, 전체 주소, 발급기관과 문서 전체가 식별 가능해야 하며 임의 마스킹·편집·잘린 캡처를 사용하지 않는다. 개인 주소 문서는 Apple이 화면에서 허용 유형을 명시한 경우에만 해당 공식 문서를 사용한다.
- 사용자가 Apple 디지털 서비스법 규정 준수 정보를 제출했고 `27개의 국가 또는 지역`, 최종 업데이트 `2026-09-07`, 상태 `심사 중`임을 확인했다. 현재는 제출 완료·Apple 검토 대기 상태로 보며 추가 요청 없이 동일 문서를 수정하거나 재제출하지 않는다. Account Holder 이메일과 Business의 규정 준수 상태에서 `조치 필요` 또는 추가 자료 요청이 생기는지만 확인한다.
- Google 앱은 현재 최종 출시 심사 대기 중이라고 사용자가 확인했다. 앱 release 심사 중에도 merchant/payment profile, RevenueCat Google app 연결에 필요한 Cloud project/API·최소 권한 service account, RTDN Pub/Sub, license tester와 internal test 준비를 병행할 수 있다.
- Google one-time product ID도 생성 후 이름 변경 제약이 있으므로 ADR-004 naming/mapping 확정 전 임의 생성하지 않는다. 앱 승인과 merchant verification/product activation이 끝나기 전에는 production 판매 완료로 간주하지 않는다.
- Google prepaid의 14일 미지원과 1개월 의미 차이를 확인한 뒤 사용자가 양 Store 모두 one-time으로 재승인했다. 이전 C9-S1 provider-native subscription 안은 최신 C9-S1 one-time 계약으로 supersede됐다.

## 2026-09-06 기간제 무제한 결제 당시 확정 — Store 유형은 2026-09-07 supersede

- 사용자는 1·3·7·14·30일 fixed-term premium 결제와 나머지 권장안을 모두 승인했다. 자동 갱신, paid credit와 fixed-unit exam pass는 현재 범위가 아니다. 기간제 unlimited Store 상품은 2026-09-07 consumable one-time으로 재승인됐다.
- 내부 offer는 `PREMIUM_1D/3D/7D/14D/30D`, 공통 benefit은 `PREMIUM_SUBSCRIPTION`, 기간형 권리는 `SubscriptionEntitlement`다. 각 기간은 UTC 24·72·168·336·720시간으로 계산한다.
- 당시에는 Apple Non-Renewing Subscription과 Google prepaid subscription/base plan을 승인했으나 2026-09-07 양 Store one-time product로 대체했다. Billing 발급 lowercase UUID v4 `purchaseAccountRefId`와 server verification 계약은 유지한다.
- 앱은 Billing public API를 직접 호출한다. Identity Access Token은 기존 Learning Core audience에 `tosunsaeng-billing`을 추가하며 Billing은 RS256, exact issuer/JWKS/audience, lowercase UUID `sub`, `iat/exp/jti`, 최대 60초 skew와 `billing:read`·`billing:purchase` scope를 검증한다.
- 같은 user가 기간을 재구매하면 `startsAt=max(providerStart,currentPaidTimelineEndsAt)`, `endsAt=startsAt+duration`으로 이어 붙인다. paid entitlement 상태는 `SCHEDULED/ACTIVE/EXPIRED/REVOKED`, purchase 상태는 `PENDING/VERIFIED/REFUNDED/REVOKED`로 분리한다.
- Reservation resolver는 ACTIVE paid를 먼저 사용하고 무료 `TrialClaim`·Grant·unit은 변경하지 않는다. paid가 없을 때만 기존 `FREE_EXAM_ONCE`를 사용하므로 유료 기간 종료 후 미사용 무료권이 남는다.
- provider-confirmed refund/revoke는 append-only reversal로 기록한다. `RESERVED`는 종료, `OPEN`·`RETAKE_AVAILABLE`은 접근과 replacement를 차단하고, 이미 제출된 `GRADING`만 완료하며 `COMPLETED` history는 보존한다. refund 전 confirm 여부만으로 replacement를 계속 허용하지 않는다.
- 결제 반영은 RevenueCat 표준 SDK client sync, Authorization+HMAC webhook과 periodic RevenueCat API reconciliation이 RevenueCat event/Store transaction unique 및 같은 Transaction service로 수렴한다. 이 내용이 이전 direct Store notification·finish/consume 방식을 supersede한다.
- 전체 승인 계약은 `docs/contracts/FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md`와 `docs/codex/CONTRACT_DECISIONS.md` C1-A/C2-A/C9-S1~S8에 기록했다. 다음 작업은 결제 ADR와 구현 계획서 작성이며 애플리케이션 코드는 아직 변경하지 않았다.
- 구현 전 남은 입력은 실제 Store product ID, 가격·판매 국가, Store review, exact public API DTO/error/rate limit, Mongo collection/index와 staging quota 기반 운영 조정값이다.
- 계약의 사용자 관점 요약은 "결제를 Store에서 완료하고 Billing 검증·저장이 끝나면 정해진 시간 동안 시험을 횟수 차감 없이 시작할 수 있으며, 유료 기간에는 무료 1회권이 보존된다"이다. 무제한은 동시 시험 무제한이 아니라 기존 단일 active Session/AttemptGroup 제약 안에서 기간 중 신규 시험 횟수를 unit으로 차감하지 않는다는 의미다.
- 구현 관점의 핵심 객체는 상품 종류 `BenefitDefinition`, 판매 기간 `offer`, Store 결제 한 건 `Purchase`, 실제 시작·종료 권리 `SubscriptionEntitlement`, 변경 감사 `ledger`, 시험 시작 승인 `Reservation`, 해당 시험·재응시 묶음 `AttemptGroup`이다.
- public ingress는 기존 public ALB의 Billing 전용 host/path rule+별도 target group, 구매는 ACTIVE MEMBER만, `purchaseAccountRefId`는 사용자·환경별 stable 값으로 확정했다. Guest는 `billing:read`만 받고 purchase와 paid merge는 현재 범위에서 제외한다.
- stacked entitlement 중간 환불 시 해당 slot을 제거하고 뒤의 verified 기간을 즉시 앞으로 재배치한다. 정규화 결제·원장은 5년, provider event inbox는 120일, backup은 최대 35일 보존한다.
- reconciliation 기본값은 PENDING·Store-completed/Billing-pending 5분, ACTIVE/SCHEDULED 6시간, 최근 90일 terminal 일 1회와 100건 batch다. staging RevenueCat API quota 측정 뒤 환경 설정만 보수적으로 조정할 수 있다.
- Identity는 현재 audience 한 개와 공통 default scope를 모든 사용자 token에 넣고 Guest도 발급한다. 따라서 결제에서는 audience list reader-first 변경뿐 아니라 MEMBER token에만 `billing:purchase`, Guest에는 최대 `billing:read`만 발급하는 account-type별 scope 계약이 필요하다.

## 2026-09-06 결제 출시를 위해 사용자가 외부에서 준비할 항목

- Apple은 Developer Program/App Store Connect 권한, Paid Applications Agreement·세금·은행 정보, app/bundle record, 5개 Consumable In-App Purchase product ID·가격·국가·현지화/review metadata, RevenueCat Apple app 연결 credential, App Store Server Notifications→RevenueCat 연결과 sandbox tester가 필요하다.
- Google은 Play Console 개발자·merchant/payment profile, app/package record, 5개 consumable one-time product ID·가격·국가, RevenueCat Google app 연결용 Cloud project/Play Android Developer API/service account 최소 권한, RTDN Pub/Sub→RevenueCat 연결, license tester와 internal test track이 필요하다.
- AWS에서는 실제 public hostname, ACM certificate, 기존 ALB listener의 Billing host/path allowlist·별도 target group·SG, provider notification public route와 Store credential용 Secrets Manager 항목을 환경별로 준비해야 한다.
- 사용자가 직접 선택하거나 승인할 business 값은 영구적으로 사용할 product ID naming, 5개 가격, 판매 국가·세금/현지화, 약관·개인정보·환불 안내와 Store review 제출 정보다. product ID는 생성 뒤 변경·재사용이 제한될 수 있으므로 ADR naming 확정 전에 임의 생성하지 않는다.
- `purchaseAccountRefId`는 Billing이 자동 발급하고 transaction ID/purchase token은 Store가 결제 때 발급하므로 사용자가 신청하지 않는다. Identity audience/scope, API DTO, Mongo index와 refund event는 코드·ADR 작업이다.
- `.p8`, service-account JSON, issuer/key ID 조합, private key와 notification credential은 채팅·Jira·Git에 올리지 않고 환경별 Secret Manager에 직접 등록한다.

## 2026-09-06 환불 뒤 진행 중 시험 이탈·재시작 검토 기록 — 최신 strict revoke로 대체

- 최초 검토에서는 confirm된 AttemptGroup의 replacement를 refund 뒤에도 허용하는 방안을 고려했으나 start→refund→exit→replacement를 통한 사실상 무료 응시 악용 때문에 채택하지 않았다.
- 최종 확정은 provider-confirmed refund 시 `RESERVED`를 종료하고 `OPEN`과 `RETAKE_AVAILABLE`의 추가 진행·replacement를 차단하며, 이미 제출돼 `GRADING`인 Session만 처리 정합성을 위해 terminal까지 완료하고 `COMPLETED` history는 삭제하지 않는 방식이다.
- refund와 reserve/Session commit/confirm race는 먼저 commit된 상태와 CAS로 판정한다. refund가 먼저면 confirm을 revoked error로 거절하고 Learning Core가 생성된 Session을 access-revoked로 보상한다.
- 이 보정은 Learning Core가 refund-revoked AttemptGroup/Session을 실제로 차단할 수 있는 durable event 또는 동등한 fail-closed projection이 필요하다. Billing의 신규 reserve 차단만으로는 이미 발급된 Session의 답안 제출·채점 요청을 막을 수 없다.
- Store가 환불을 승인하기 전에 이미 완료된 시험 가치는 기술적으로 회수할 수 없다. Apple의 지원 가능한 consumption 정보와 Google의 void/refund 관련 사용 evidence를 최소 정규화 상태로 제공하고, refunded-after-use 지표·반복 악용 운영 검토를 두되 앱 주장만으로 환불·차단하지 않는다.

## 2026-09-05 Store 값과 토선생 사용자 연결 경계

- Store product ID는 어떤 상품을 샀는지 판정하는 데 사용하고 provider transaction ID/purchase token은 같은 구매의 중복 반영을 막는 데 사용한다. 이 값들은 구매 증거이지 토선생 계정의 영구적인 owner 식별자가 아니다.
- Apple/Google 계정은 토선생 계정과 1:1이라는 보장이 없다. 한 Store 계정으로 여러 토선생 계정을 사용하거나, 기기의 Store 계정을 바꾸거나, 같은 기기에서 다른 사용자가 로그인할 수 있으므로 Store 측 계정만으로 entitlement를 정하면 다른 토선생 사용자에게 권리가 붙을 수 있다.
- 앱이 Apple ID 자체를 안정적인 사용자 ID로 받는 구조도 아니며 provider별 식별 방식과 복원 semantics가 다르다. Store 계정값에 owner를 결속하면 Apple·Google 간 동일한 소유권 규칙을 만들기 어렵고 개인정보·계정 공유 영향도 커진다.
- 따라서 구매 시점에 로그인된 토선생 사용자에게 Billing이 발급한 opaque `purchaseAccountRefId`를 Store transaction에 함께 넣고, 검증된 transaction의 reference가 현재 사용자에게 발급된 값과 일치할 때만 entitlement를 연결한다.
- Store 값은 상품·거래 검증에, Billing reference는 앱 사용자 소유권 검증에 각각 사용한다. phone rejoin이나 같은 Store 계정만으로 유료 권리를 이전하지 않고 `UserMerged`처럼 승인된 계정 lifecycle만 별도 migration으로 처리한다.
- 같은 Apple 계정이면 같은 토선생 계정이라는 전제는 토선생 로그인이 Sign in with Apple만 사용하고 그 subject를 영구 1:1 계정키로 강제할 때에만 성립한다. 현재 Identity는 별도 토선생 계정과 phone lifecycle을 소유하므로 App Store의 Media & Purchases 계정과 토선생 로그인 계정을 같은 identity로 간주하지 않는다.
- 가족의 Apple 계정 공유는 보조적인 오귀속 사례일 뿐 핵심 근거가 아니다. 더 근본적인 이유는 Apple이 앱에 Apple ID 자체를 결제 owner ID로 제공하지 않고, 앱 계정과 transaction을 연결하도록 개발자가 제공하는 `appAccountToken`을 지원한다는 점이다. 따라서 `purchaseAccountRefId`는 Store 값을 우회하는 값이 아니라 provider transaction에 토선생 계정 context를 안전하게 넣는 표준 연결값이다.
- 쉬운 역할 구분은 `productId=상품표`, `transactionId/purchaseToken=영수증 번호`, `purchaseAccountRefId=받는 토선생 회원 번호`다. Billing은 세 값을 함께 검증해야 정확한 상품을 정확히 한 번, 정확한 사용자에게 지급할 수 있다.

## 2026-09-05 결제 포함 1차 개발 범위 전환 검토

- Learning Core 후속 보정은 PR #29 merge `88b46c6`으로 `develop`과 `origin/develop`에 반영됐다. phone continuation attemptGroupId lowercase UUID v4, migration orphan/owner mismatch preflight, unknown commit inbox 수렴·503 mapping, replica-set 회귀 범위와 CI task가 코드상 보완됐다.
- 재검증에서 Learning Core `./gradlew clean test`는 성공했고 Node migration test 7개와 `git diff --check`도 성공했다. 로컬 `mongoIntegrationTest`는 Docker provider 부재로 initialization 실패했으므로 GitHub CI의 해당 required job 성공 여부와 staging E2E는 별도 production gate다.
- 무료시험·owner lifecycle은 핵심 애플리케이션 개발 완료로 보고 결제 애플리케이션 개발을 시작할 수 있다. 단, 관련 feature flag 활성화와 production 출시는 schema migration, 실제 Lattice/IAM/SG, Mongo replica-set CI, 중복·역순·응답 유실·인증 실패 staging E2E 뒤에만 진행한다.
- 사용자가 1차 개발 범위에 결제를 포함하려는 의사를 밝혔다. 기존 `CONTRACT_DECISIONS`의 deferred 상태와 C9~C11은 credit pack/3일 pass 초안이므로, 사용자 선호인 단순 premium subscription을 구현하기 전에 이를 subscription 계약으로 명시적으로 대체해야 한다.
- 다음 권장 작업은 코딩이 아니라 구독 결제 계약 확정이다. plan/기간·Store product mapping, 제공 권리, 무료권과 resolver 우선순위, 앱→Billing API와 JWT audience, account binding/restore, renewal·grace·cancel·expiry, refund/revoke·notification 및 reconciliation을 확정한 뒤 별도 ADR·PLAN·Jira로 vertical slice를 나눈다.

## 2026-09-05 기간제 무제한 이용권 당시 선택지 — Store 유형은 2026-09-07 supersede

- 사용자가 유료 상품을 `1일`, `3일`, `7일`, `14일`, `30일` 동안 무제한 사용하는 고정 기간 상품으로 정했고, 유료 이용 중에도 전화번호당 무료 1회권은 소비하지 않고 보존하기로 했다.
- 이 상품은 자동 갱신형 월 구독보다 fixed-term access pass에 가깝다. Apple은 limited duration용 Non-Renewing Subscription, Google은 자동 갱신되지 않고 top-up으로 연장하는 prepaid subscription plan을 공식 제공하므로 provider별 native fixed-term 유형을 우선 검토한다. 정확한 5개 기간의 Console 등록 가능 여부는 실제 상품 생성 전 각 Store에서 확인한다.
- Store mapping 권장안은 Apple non-renewing subscription 5개 product와 Google 하나의 subscription product 아래 5개 prepaid base plan을 Billing의 5개 내부 offer code에 매핑하는 방식이다. 두 Store를 모두 consumable one-time product로 통일하는 대안은 server 구현이 단순하지만 restore와 Store 상품 의미가 약해진다. auto-renewable은 고정 기간·무자동갱신 요구와 맞지 않는다.
- account binding은 Billing이 발급한 환경별 비개인 opaque purchase account ID를 Apple `appAccountToken`과 Google obfuscated account ID에 전달하고 canonical user와 서버에서 연결하는 안을 권장한다. raw userId·phone·email을 Store 식별자로 보내거나 device에 entitlement를 결속하지 않는다.
- 앱 API는 앱→Billing direct public API와 Identity access JWT의 Billing audience를 권장한다. Learning Core proxy는 초기 endpoint가 적지만 결제 보안·장애·DTO 책임을 시험 서비스에 결합한다.
- fixed-term lifecycle은 결제 검증 완료 시 시작하고, active pass 재구매는 `max(now, currentEndsAt)`부터 기간을 이어 붙이는 안을 권장한다. 자동갱신이 없으므로 grace/account-hold/cancel-scheduled 상태는 만들지 않고 pending, active, expired, revoked/refunded를 provider payment 상태와 entitlement 상태로 분리한다.
- 당시에는 confirm된 AttemptGroup의 replacement 보존을 권장했으나 2026-09-06 악용 검토에서 폐기했다. 최신 확정은 위 strict revoke 상태표를 따르며 기존 ledger와 완료 history는 삭제하지 않고 무료 TrialClaim도 복원·소비하지 않는다.
- activation은 client purchase sync와 server notification을 함께 사용하고, notification 누락·지연을 보완할 주기적 Store reconciliation을 둔다. client-only 또는 notification-only는 각각 환불 누락과 활성화 지연 위험이 있다.

## 2026-09-05 C9 Store 상품·account binding 당시 승인 — 2026-09-07 supersede

- 사용자가 4번 권장안을 승인해 `CONTRACT_DECISIONS.md`에 C9-S1 active contract로 반영했다. Apple Non-Renewing Subscription, Google prepaid subscription base plan과 Billing 내부 `PREMIUM_1D/3D/7D/14D/30D` exact mapping을 사용한다.
- 앱의 가격·기간 주장은 신뢰하지 않고 Billing이 Store transaction과 product/base plan을 서버에서 검증한 뒤 catalog duration으로만 entitlement를 만든다. provider transaction/event unique로 client retry·notification·reconciliation 중복 지급을 차단한다.
- Billing 발급 lowercase UUID v4 `purchaseAccountRefId`를 Apple `appAccountToken`과 Google obfuscated account identifier에 사용한다. raw userId·phone·email·device ID를 Store에 결속하지 않고 current canonical user와 Billing 내부에서만 연결한다.
- 이미 반영된 구매는 Billing current entitlement 조회로 복원하고 전달 전 앱 종료는 Store purchase query, server notification과 reconciliation으로 수렴한다. Guest `UserMerged` 외 phone proof나 동일 device/Store account만으로 유료 권리를 다른 앱 계정에 이전하지 않는다.
- 기존 C9-A consumable/credit, C10 credit expiry와 3일 pass 초안은 역사적 기록으로 명시했다. 이 시점에는 C1/C2 public API/auth, entitlement activation·stacking, refund/revoke, notification/ack 책임과 reconciliation이 미확정이었으나 이후 위의 "기간제 무제한 결제 계약 전체 승인"에서 모두 확정됐다.

## 2026-09-05 cross-service 기능 완결성 재점검

- 무료시험 phone continuation과 Guest `UserMerged`의 핵심 정상 흐름은 세 저장소에 구현·병합됐다. 그러나 production-safe 완료로 보기는 이르며 Learning Core 후속 보정과 staging gate가 남아 있다.
- 확정 코드 누락 1: Learning Core phone continuation discovery의 `attemptGroupId`는 계약상 lowercase UUID v4인데 client decoder와 saga 최초 검증이 opaque text만 허용한다. 잘못된 Billing 200 응답을 operation snapshot 생성 전에 strict 거절하도록 보완해야 한다.
- 확정 코드 누락 2: Learning Core `user-merged-prepare.js`는 owner UUID, active Session 중복, index와 기존 MERGED guard를 확인하지만 orphan `ExamResult`/`ExamSummary`와 Result/Summary owner↔Session owner 불일치를 검사하지 않는다. apply 전 데이터 정합성 preflight와 Node 회귀 테스트를 추가해야 한다.
- 확정 CI 누락: `build.gradle`은 `check`에서 `mongoIntegrationTest`를 연결하지만 staging workflow가 `./gradlew clean test`만 실행한다. replica-set Mongo Transaction 검증이 required gate에서 실제 수행되도록 workflow를 보정해야 한다.
- 잠재 기능 누락: `UserOwnedTransactionExecutor`는 `UnknownTransactionCommitResult`를 재실행하지 않는 점은 안전하지만 eventId/inbox를 재조회해 commit 여부로 수렴하지 않고 예외를 그대로 전달한다. 해당 오류가 `TransactionSystemException`으로 포장되면 Controller advice의 `DataAccessException` handler 밖에서 500이 될 가능성이 있어, 재조회 수렴과 503 mapping을 실제 예외 형태로 고정하는 테스트가 필요하다.
- 통합 검증 범위도 계획보다 작다. 현재 UserMerged replica-set test는 4개이며 failure injection rollback, non-terminal operation, concurrent duplicate/write/Callback, unknown commit result 수렴 등의 계획 조건이 빠져 있다.
- 일반 Java 483개와 Node migration 6개는 직전 검토에서 성공했지만 Docker daemon 부재로 `mongoIntegrationTest`는 실행되지 않았다. 위 보정과 Docker 가능한 CI 성공, 실제 Lattice/JWT·순서 역전·응답 유실 staging E2E 전에는 관련 production flag를 OFF로 유지한다.

## 2026-09-05 다음 작업 재확인

- 즉시 다음 구현 대상은 Learning Core `TMI-125`다. Billing phone continuation reader-first 계약을 적용하고 `UserMerged` consumer·workload JWT verifier·source deny와 실제 merge ownership migration을 구현한다.
- phone 재가입은 Learning Core owner event를 받지 않는다. 새 target user에게 과거 Session·답안·결과를 이전하지 않고 Billing continuation discovery가 승인한 `OPEN`/`RETAKE_AVAILABLE` group에 새 examId의 replacement Session만 연결한다.
- Billing의 신규 continuation 응답 optional field를 먼저 허용하고, target에 기존 Session이 없을 때 continuation 조회 후 200이면 six-field reserve exact echo, 204면 일반 INITIAL 흐름을 수행한다. 예상하지 못한 REPLACEMENT나 context 불일치는 fail-closed하고 Reservation을 cancel로 보상한다.
- Learning Core 구현·병합 뒤 Docker가 가능한 환경에서 Billing Testcontainers 4개를 포함한 전체 테스트를 재실행한다.
- 마지막 gate는 Lattice exact route/IAM·SG, schema v4 preflight, 세 서비스 feature flag OFF 배포와 staging 중복·역순·응답 유실·인증 실패 E2E다. 이 검증 전 production flag는 활성화하지 않는다.

## 2026-09-05 Learning Core TMI-122·TMI-125 병합 구현 검토

- Learning Core `develop`과 `origin/develop`은 PR #28 merge `8c8208b`에서 일치한다. phone continuation `TMI-122`는 PR #27, UserMerged `TMI-125`는 PR #28로 병합됐다.
- Billing continuation 조회·6-field reserve·새 target Session 연결과 UserMerged exact route/workload JWT/source deny/owner migration의 큰 방향은 승인 계약과 일치한다. `TrialOwnerRebindApproved` Learning Core consumer와 phone 기반 과거 시험 이전은 추가되지 않았다.
- `./gradlew clean test`는 Java 483개가 failures/errors/skipped 0으로 성공했고 Node migration test 6개와 `git diff --check`도 성공했다. `mongoIntegrationTest`는 Docker daemon 부재로 initialization 단계에서 실행되지 못했다.
- 배포 workflow는 현재 `./gradlew clean test`만 실행하므로 `build.gradle`에서 `check`에 연결한 `mongoIntegrationTest`가 CI gate에서 실제 실행되지 않는다. workflow 또는 별도 required workflow 보정이 필요하다.
- UserMerged Mongo 준비 script는 owner UUID·active 중복·index·기존 MERGED guard만 검사하며 계획서의 orphan Result/Summary와 Session-owner 불일치 검사를 구현하지 않았다. apply 전에 해당 preflight와 테스트를 보완해야 한다.
- Learning Core phone discovery decoder는 `attemptGroupId`를 opaque text로 허용하지만 Billing 계약은 lowercase UUID v4다. malformed 200 response를 operation snapshot에 저장하기 전에 strict 거절하도록 decoder와 회귀 테스트를 보완해야 한다.
- Learning Core 작업 트리에는 검토 시작 뒤 기존 TMI-125 Jira 완료 기록인 CURRENT_STATE/WORKLOG 변경이 남아 있으며 애플리케이션 파일은 clean이다. 이를 수정하거나 되돌리지 않았다.

## 2026-09-03 PHONE_REJOIN continuation discovery 구현

- Learning Core 전용 `POST /internal/v1/reservations/continuations/phone`을 추가했다. target userId의 current owner transition이 PHONE_REJOIN이고 group이 OPEN/RETAKE_AVAILABLE이면 Billing 저장값인 reason/id/attemptGroupId/mockExamId를 200으로 반환하고 대상 없음은 204다.
- owner CAS는 `ownerTransitionReason`, `ownerTransitionId`를 같은 update에 저장한다. 다음 owner CAS가 이 값을 교체하므로 context는 current owner epoch에 결속되며 source userId나 raw phone을 새로 저장하지 않는다.
- reserve strict decoder는 기존 3-field request 또는 phone 6-field request만 허용한다. phone context 세 field는 all-or-none이며 current transition ID, expected group, 기존 mockExamId가 모두 일치해야 `PHONE_REJOIN REPLACEMENT`가 된다.
- phone reserve 응답과 status에는 optional continuation reason/id가 남고 attemptGroupId/mockExamId는 existing group 값을 authoritative하게 사용한다. 일반 reserve 응답과 기존 canonical idempotency digest는 변경하지 않는다.
- 핵심 MVC/security/decoder/policy/service/hash 테스트는 성공했다. `./gradlew clean test` 136개 중 비-Docker 132개는 통과했고 Docker daemon 미가동으로 Testcontainers 4개 initialization failure가 남았다.
- production flag는 계속 off다. Learning Core reader-first 적용, Lattice auth policy에 exact continuation route 추가, Docker replica-set test와 staging E2E 전에는 활성화하지 않는다.

## 2026-09-03 PHONE_REJOIN replacement discovery 계약 공백 확인

- 현재 reserve 요청은 Learning Core가 `mockExamId`를 필수로 먼저 보내며 Billing은 기존 nonterminal AttemptGroup의 `mockExamId`와 exact match한 경우에만 `REPLACEMENT`를 반환한다.
- 재가입한 새 userId의 Learning Core에는 기존 Session과 `mockExamId`가 없으므로 임의의 새 값으로 요청하면 Billing은 응답 전 `STATE_CONFLICT`로 거절한다. 따라서 문서의 phone replacement 흐름은 현재 wire 계약만으로 실행할 수 없다.
- 일반 reserve의 fail-closed 성질은 유지하고, phone rejoin에 한해 Billing이 기존 `attemptGroupId`·`mockExamId`와 명시적인 continuation reason/context를 authoritative하게 제공하는 discovery 계약이 추가로 필요하다.
- Learning Core는 유효한 phone-rejoin context를 받은 경우에만 target 명의 새 examId를 만들고 같은 AttemptGroup에 연결한다. source Session·답안·결과 owner는 변경하지 않는다.
- 구체적인 discovery route, context의 식별·만료·1회성, reserve echo field와 오류 계약은 아직 ADR에서 확정하지 않았으며 현재 구현이나 Jira는 변경하지 않았다.

## 2026-09-03 PHONE_REJOIN 상태별 제한 승계 보정

- `TrialOwnerRebindApproved`는 Billing-only delivery이며 phone proof만으로 완료된 과거 시험·답안·피드백을 Learning Core에서 이전하지 않는다. `UserMerged`만 Billing·Learning Core 양쪽에 전달한다.
- Billing은 active Reservation/PROCESSING 확인 뒤 Claim의 AttemptGroup 전체 상태를 조회한다. 이력 없음·`OPEN`·`RETAKE_AVAILABLE`은 owner CAS `APPLIED`, `GRADING`은 503 pending, `COMPLETED`는 owner/fence 불변 `NOOP` 204다.
- phone target의 재응시는 새 무료권이 아니다. 기존 consumption·attemptGroupId·mockExamId를 `REPLACEMENT`로 재사용하면서 source Session을 이전하지 않고 target의 새 key·새 examId로 처음부터 시작한다.
- `OwnerRebindService`, indexed claim 기반 AttemptGroup 조회, 상태별 unit/MVC 회귀 테스트와 replica-set Mongo integration test를 보정했다. Claim·Grant·ledger·AttemptGroup은 변경하지 않는다.
- ADR-003, PLAN-006, 통합 계약, CONTRACT_DECISIONS와 AGENTS를 같은 delivery·상태·Session 정책으로 갱신했다. Identity/Learning Core 코드와 Jira는 변경하지 않았다.
- 상태별 unit/MVC 테스트는 성공했다. `./gradlew clean test`는 123개 중 비-Docker 119개가 통과했고 Docker daemon 미가동으로 Testcontainers 4개가 initialization failure다. production flag는 계속 off다.

## 2026-09-03 PR 생성 안내가 보이지 않는 원인

- 최신 fetch 기준 HEAD `804d4eb`은 로컬 `develop`에 직접 commit됐고 `origin/develop`보다 1 commit 앞서 있어 아직 원격에 반영되지 않았다.
- PR은 source와 base가 서로 다른 원격 branch여야 한다. 현재 commit을 `develop`에 직접 push하면 `develop`을 base로 한 PR source branch가 없으므로 GitHub의 Compare & pull request 안내가 나타나지 않는다.
- 안전한 정리 순서는 현재 commit에서 새 `fix/TMI-120-phone-rejoin-policy` branch를 만든 뒤 local develop pointer를 `origin/develop`로 복원하고 새 branch를 push하여 base `develop` PR을 만드는 것이다.
- repository의 `main`은 `develop`보다 오래된 상태이므로 PR base를 GitHub 기본값에 맡기지 말고 `develop`으로 명시해야 한다.

## 2026-09-02 TMI-120 Billing owner rebind 구현

- schema v4에 `owner_rebind_inbox`, `subject_owner_rebinds`, exact index와 `BillingSubjectLink.ownerVersion/ownerUpdatedAt` reader-first CAS를 추가했다. legacy missing version은 logical 1로 읽고 첫 이전에서 version 2로 수렴한다.
- phone `TrialOwnerRebindApproved`와 Guest `UserMerged` v1을 route별 strict decode하고 canonical digest로 멱등 처리한다. event만으로 Claim·Grant·allocation·consumption은 만들지 않으며 source current link의 owner만 target으로 이전한다.
- phone projection revision/state/candidate-to-Claim 검증, Guest 최대 100 subject all-or-nothing, active Reservation/PROCESSING의 503 pending, Transaction retry·commit 재확인을 구현했다.
- owner 이전 전 active exact Session에는 120일/Claim retention 상한 fence를 만들고 AttemptGroup status consumer가 이전 source event를 그 Session의 상태 전진에만 허용한다. terminal 시 즉시 fence를 종료한다.
- 최대 1시간 cleanup worker는 due source/group/session 연결을 unset하고 24시간 초과를 저카디널리티 경보로 기록한다. 로그와 span에는 source/target/subject/Claim/Session/payload/digest를 넣지 않는다.
- lifecycle decoder, MVC/security, service, AttemptGroup fence, cleanup과 실제 HTTP W3C trace 테스트가 통과했다. replica-set Mongo 통합 테스트도 작성했으나 현재 local Docker daemon 미가동으로 실행 검증이 남아 있다.
- production owner rebind와 cleanup flag는 기본 false다. Identity event별 delivery, Learning Core `UserMerged` consumer/phone replacement, 실제 Lattice/IAM/SG와 staging E2E 전에는 활성화하지 않는다.

## 2026-09-02 TMI-120 이후 작업 순서

- 즉시 다음 작업은 Docker daemon이 가능한 환경에서 `./gradlew clean test`를 다시 실행해 owner rebind를 포함한 replica-set Testcontainers 4개를 검증하고, 실패가 있으면 수정하는 것이다.
- 전체 테스트와 최종 diff 검토가 통과하면 사용자 승인 후 Jira TMI-120을 완료 처리한다. commit·push·PR은 사용자가 수행한다.
- 다음 cross-service 구현은 Identity event별 durable delivery다. `UserMerged`는 Billing/Learning Core, phone rejoin은 Billing delivery만 독립 저장·retry한다.
- 그다음 Learning Core에 `UserMerged` owner consumer/source deny와 phone target의 기존 group replacement Session 처리를 구현한다.
- 마지막으로 schema v4 migration, Lattice IAM/SG, 두 서비스 flag를 staging에 적용해 Identity/Billing/Learning Core 순서 역전·중복·응답 유실 E2E를 통과한 뒤에만 production canary를 진행한다.

## 2026-09-02 Identity durable owner-event fan-out 인계 내용

- 현재 Identity `UserMergedOutbox`는 immutable event 값과 단일 delivery 상태가 같은 document에 있고 `UserMergedPublisher`도 endpoint 하나만 처리한다. 이를 기존 row reader-first 호환을 유지하면서 event core와 `(eventId, consumer)` delivery로 분리해야 한다.
- 신규 Guest merge부터 같은 lifecycle Mongo Transaction에서 기존 `UserMerged` core와 `BILLING`, `LEARNING_CORE` delivery 두 건을 원자 저장한다. phone 재가입은 별도 `TrialOwnerRebindApproved` core와 `BILLING` delivery 한 건만 만든다.
- delivery는 payload를 복제하지 않고 core를 참조하며 status, attempt, nextAttemptAt, lease, failure, published/dead-letter/cleanup 시각을 consumer별로 독립 관리한다. 한 consumer의 성공·실패가 다른 consumer delivery 상태를 바꾸면 안 된다.
- Billing route는 Guest `/internal/v1/owners/merge/events`, phone `/internal/v1/eligibility/trial/owner/events`다. Learning Core route는 Guest `/internal/v1/owners/merge/events`뿐이며 phone route는 없다.
- Identity→Billing은 서울 리전 Lattice SigV4, Identity→Learning Core `UserMerged`는 기존 workload JWT를 유지한다. consumer별 retry/dead-letter/circuit은 독립 관리한다.
- 기존 merge event historical backfill은 하지 않고, consumer 준비·staging E2E 전 publisher flag는 기본 false로 유지한다.

## 2026-09-02 ADR-003 retained trial owner rebind 계약 초안 작성

- `docs/adr/ADR-003-retained-trial-owner-rebind-contract.md` 검토 초안을 작성했다. 승인된 C14·PLAN-006·TMI-120 정책을 바꾸지 않고 two lifecycle wire, strict decoder, canonical digest, owner 상태 전이와 pending HTTP 계약을 실행 가능한 수준으로 고정했다.
- Billing Mongo schema v4는 `owner_rebind_inbox`, `subject_owner_rebinds`, `BillingSubjectLink.ownerVersion/ownerUpdatedAt`을 사용한다. v3 missing ownerVersion은 logical 1로 reader-first 처리하고 첫 CAS에서 version 2로 수렴하며 자동 bulk rewrite/drop은 금지한다.
- phone prerequisite는 source/target revision lower를 503 pending, exact state 불일치를 conflict, higher revision의 반대 상태를 superseded STALE로 판정한다. Guest merge는 source active retained link 최대 100건을 한 Transaction에서 all-or-nothing 이전한다.
- exact pre-rebind group/session status만 legacy source로 허용하고 terminal 또는 `min(appliedAt+120일, Claim retentionExpiresAt)`에 논리 종료한다. 최대 1시간 간격 worker가 24시간 안에 source link를 unset하며 TTL은 safety net으로만 사용한다.
- Identity 기존 `user_merged_outbox` reader-first core/delivery 전환을 포함했다. 당시 포함했던 Learning Core phone route 전제는 2026-09-03 보정으로 제거됐고 정상 replacement Session 흐름으로 대체됐다.
- PLAN-006, CONTRACT_DECISIONS와 통합 계약에서 ADR-003을 owner rebind 단일 기술 기준으로 연결했다. Jira 내용·상태, 애플리케이션 코드, Identity/Learning Core 코드와 AWS resource는 변경하지 않았다.
- 남은 gate는 ADR-003 검토 승인이다. 승인 뒤 TMI-120 Billing schema/consumer 구현을 시작하며 타 서비스 consumer와 staging 순서 역전 E2E 전 production flag는 off다.

## 2026-09-02 ADR-003 승인과 Jira TMI-120 갱신

- 사용자가 ADR-003 전체와 schema v4 collection 이름, Guest merge 100 subject 상한, 최대 1시간 cleanup worker, historical backfill/privileged mutation route 제외를 승인했다. 당시 Learning Core phone route 승인은 2026-09-03 보정으로 폐기됐다.
- ADR-003 상태를 승인으로, PLAN-006 상태를 ADR 승인·TMI-120 구현 대기로 갱신했다. Billing 구현 시작에 필요한 계약 선택은 더 남아 있지 않다.
- Jira TMI-120의 기존 목표·wire·fan-out·IAM·cleanup·완료 조건은 보존하고 ADR-003 승인 기술 기준을 description에 추가했다. 상태 `해야 할 일`, Medium, 담당자 없음과 Resolution 없음은 유지했다.
- 다음 작업은 TMI-120 Billing schema v4, lifecycle별 strict decoder, owner CAS, legacy fence와 cleanup worker 구현이다. Identity/Learning Core consumer와 실제 AWS/staging E2E는 여전히 별도 작업과 production activation gate다.

## 2026-09-02 Guest merge retained subject 100건 상한 설명

- retained subject는 시험 횟수가 아니라 source user가 current owner로 연결된 active·unexpired `BillingSubjectLink` 한 건을 뜻한다. 현재 `FREE_EXAM_ONCE` 범위에서는 정상적으로 0~1건이 예상된다.
- Guest merge 한 event는 source의 모든 retained subject를 target으로 한 Transaction에서 all-or-nothing 이전한다. 100건은 데이터 이상이나 미래의 과도한 benefit 연결 때문에 Mongo Transaction이 무제한 커지는 것을 막는 방어 상한이다.
- 100건을 초과하면 처음 100건만 부분 이전하지 않는다. 전체 이전을 중단하고 409 conflict와 invariant alert로 운영 검토를 요구한다.
- 이 값은 무료 모의고사를 100번 허용하거나 Claim을 100개 만든다는 뜻이 아니다. 향후 정상 제품 모델이 한 사용자에게 100개를 넘는 retained subject를 요구하면 ADR을 갱신해 상한과 batching 전략을 다시 정한다.

## 2026-09-02 owner rebind strict decoder 의미 설명

- strict decoder는 외부 JSON을 단순 DTO로 변환하는 parser가 아니라 route별 승인 field·type·exact 상수·식별자 형식·상호 관계를 모두 확인한 뒤에만 내부 `OwnerRebindCommand`를 만드는 계약 경계다.
- duplicate/unknown field, trailing token, scalar coercion, 잘못된 UUID casing·revision·timestamp와 source==target을 거절한다. phone route는 eventType/producer/scope/reason을 exact 확인하고 Guest route는 기존 UserMerged v1에 없는 field를 허용하지 않는다.
- malformed 형식은 400, 정상 JSON이지만 지원하지 않는 schema/event/producer/reason/scope는 422로 구분하며 검증 전 raw payload를 저장·로깅하거나 canonical digest를 계산하지 않는다.
- phone과 Guest decoder를 분리해 두 lifecycle의 의미를 섞지 않고, 검증이 끝난 공통 값만 내부 command로 수렴시켜 이후 owner CAS·멱등성 service를 재사용한다.

## 2026-09-02 ADR-003 작성 전 사용자 추가 결정 필요 여부

- 제품·보안·보존 수준의 필수 사용자 결정은 더 없다. event/route/schema, pending response, fan-out, IAM, owner CAS, active Reservation, legacy fencing과 24시간/120일 cleanup이 모두 확정됐다.
- ADR-003의 남은 collection/index 이름, schema v4 ownerVersion backfill, exact CAS query, reader-first delivery migration, Learning Core `UserMerged` route와 phone replacement, cleanup scheduler와 metric은 승인 정책 안의 기술 설계로 Codex 권장안을 작성할 수 있다.
- 보수적 기본값은 TMI-120에서 privileged HTTP repair route를 만들지 않는 것이다. 120일 hard cap 뒤 event는 자동 authorization하지 않고 alert·운영 review·원본 event 증적 확인을 거쳐 별도 future repair ADR로 처리한다.
- Billing은 production 미활성 전제를 유지한다. schema v4 startup preflight에서 legacy data/index가 예상과 다르면 자동 rewrite/drop하지 않고 fail-fast하며 별도 migration 승인을 요구한다.
- Identity merge 성공은 downstream 두 consumer의 즉시 성공을 기다리지 않는 existing transactional outbox semantics를 유지한다. `UserMerged` 두 delivery는 독립 retry/dead-letter되고 phone은 Billing delivery만 둔다.
- 따라서 사용자가 별도 운영 repair endpoint나 historical backfill을 현재 범위에 포함하길 원하지 않는 한 추가 질문 없이 ADR-003 초안을 작성할 수 있다.

## 2026-09-02 TMI-120 구현 전 마지막 ADR gate 확인

- 제품 정책, exact event/route/schema, 503/Retry-After, durable fan-out, Lattice IAM action과 legacy cleanup SLA는 모두 승인돼 선택지는 남지 않았다.
- 그러나 통합 계약은 구체적인 `UserMerged` consumer wire를 별도 ADR 확정 전 임의 구현하지 못하게 하고 현재 저장소에는 ADR-001·ADR-002만 있다. 따라서 바로 코드가 아니라 ADR-003 owner rebind contract를 먼저 작성해야 한다.
- ADR-003은 Billing owner-rebind inbox/record collection·index·schema v4 migration, ownerVersion backfill/CAS, cleanup worker와 privileged reconciliation, Identity existing outbox reader-first delivery migration, Learning Core `UserMerged` route와 phone replacement를 실행 가능한 계약으로 고정한다.
- ADR-003 승인 뒤 TMI-120 Billing reader-first consumer 구현을 시작할 수 있다. Identity fan-out과 Learning Core owner consumer는 별도 저장소/Jira 구현이며 Billing feature flag와 production activation gate는 계속 닫아 둔다.
- 이번 확인은 분석·기록만 수행했고 애플리케이션·Jira·승인 계약을 변경하지 않았다.

## 2026-09-02 owner rebind fan-out·IAM·cleanup 승인과 Jira 갱신

- 사용자가 remaining ADR 권장안을 모두 승인했다. Identity는 immutable event core와 `(eventId, consumer=BILLING|LEARNING_CORE)` unique delivery 두 건을 lifecycle Transaction에서 원자 저장하고 delivery별 lease·retry·dead-letter·published·feature flag를 독립 관리한다.
- Lattice IAM action은 `vpc-lattice-svcs:Invoke`만 사용한다. 환경별 exact service ARN, Identity task role Principal, POST와 승인 route를 함께 제한하고 wildcard·cross-environment·불필요 action을 금지한다.
- legacy sourceUserId는 related Session terminal 뒤 daily cleanup으로 24시간 안에 unset한다. terminal 미수렴 hard upper bound는 `min(rebindAppliedAt+120일, Claim retentionExpiresAt)`이고 이후 late event는 privileged reconciliation 대상이다.
- Jira `TMI-120`에 durable fan-out 구조, exact IAM, cleanup SLA/hard cap과 관련 완료 조건을 추가했다. Identity fan-out·실제 AWS resource 구현은 Billing Jira 제외 범위이면서 production 선행 gate로 유지했다.
- PLAN-006, C14, ADR-002 auth-policy example과 전체 통합 계약을 같은 값으로 갱신했다. Jira 상태 `해야 할 일`, Medium, resolution 없음, 담당자 미지정은 변경하지 않았다.
- 후속 ADR에 남은 것은 승인값 변경이 아니라 Identity existing outbox reader-first migration schema, Learning Core exact owner route와 privileged reconciliation runbook이다.

## 2026-09-02 owner rebind delivery·IAM·legacy cleanup 선택지 설명

- consumer별 durable fan-out 권장안은 immutable event core 하나와 `(eventId, consumer)` unique delivery record를 Billing/Learning Core 각각 두는 방식이다. 두 delivery는 lease·attempt·nextAttemptAt·PUBLISHED/DEAD_LETTER를 독립 관리하며 한 consumer 성공이 다른 consumer 실패를 덮지 않는다.
- Identity merge/rejoin Transaction은 event core와 필수 consumer delivery 두 건을 원자적으로 저장한다. publisher flag와 endpoint도 consumer별로 분리하고 global PUBLISHED 하나 또는 동기 순차 POST는 사용하지 않는다.
- 정확한 Lattice IAM action 권장값은 기존 ADR-002와 같은 `vpc-lattice-svcs:Invoke`다. Identity task-role identity policy는 환경별 exact Billing/Learning Core service ARN만, 각 Lattice service auth policy는 exact Identity task role Principal + POST + 승인 route만 허용한다.
- `Action:*`, `Resource:*`, wildcard principal, production↔staging service ARN 교차 허용과 `InvokeWithServiceNetworkContext` 추가는 필요하지 않다. SG는 direct task bypass를 차단하지만 route 권한의 기준은 IAM/Lattice auth policy다.
- legacy-source user 연결 cleanup 권장안은 관련 pre-rebind Session이 terminal이면 daily cleanup으로 24시간 안에 sourceUserId를 unset하고, terminal이 오지 않아도 `min(rebindAppliedAt+120일, Claim retentionExpiresAt)`을 hard upper bound로 삭제하는 방식이다.
- 120일은 Learning Core dead-letter retention 90일에 30일 안전 여유를 더하고 Billing inbox retention과 맞춘 값이다. hard cap 이후 late source event는 자동 허용하지 않고 privileged reconciliation 대상으로 보낸다. eventId/digest/outcome 비식별 멱등성 기록과 user 연결 cleanup은 분리한다.

## 2026-09-02 owner rebind exact wire 승인과 Jira TMI-120 갱신

- 사용자가 ADR 권장 초안을 승인했다. phone 재가입 event type은 `TrialOwnerRebindApproved`, route는 `POST /internal/v1/eligibility/trial/owner/events`, Guest merge route는 `POST /internal/v1/owners/merge/events`로 확정했다.
- phone schema v1은 producer identity, expected `consumerScopeId`, source/target canonical userId, `PHONE_REJOIN`, occurredAt과 1 이상 source/target binding revision을 exact field로 사용한다. raw phone·candidate·Firebase UID·email·credential은 보내지 않는다.
- active Reservation/PROCESSING 또는 projection prerequisite 미수렴은 `503 OWNER_REBIND_PENDING`과 delta-seconds `Retry-After`를 반환한다. Reservation은 남은 expiry seconds를 1~300으로 clamp하고 다른 pending은 5초다.
- 425/202/409는 temporary pending에 사용하지 않고 409는 permanent conflict에만 사용한다. Mongo/서비스 장애 503은 stable error code와 low-cardinality metric으로 pending과 구분한다.
- Jira `TMI-120` 본문에 확정 wire·field·response와 Retry-After 계산을 추가하고 기존 미확정 425/503 문구를 제거한 뒤 저장 결과를 확인했다. Jira 상태·담당자·priority·댓글은 변경하지 않았다.
- PLAN-006과 C14를 같은 exact 값으로 갱신했다. 후속 ADR에 남은 결정은 Identity consumer별 durable fan-out, exact IAM action과 legacy fence cleanup window다.

## 2026-09-02 owner rebind ADR wire 결정 근거

- ADR은 승인된 C14 owner rebind 정책을 바꾸는 문서가 아니라 Identity와 Billing이 같은 wire와 delivery·IAM·cleanup 값을 구현하도록 고정하는 단계다.
- phone 재가입 event `TrialOwnerRebindApproved`는 단순 `PhoneRejoined`보다 Billing 권리 owner 이전이 Identity에서 승인됐다는 의미가 분명하고 기존 Guest merge `UserMerged`와 구분된다.
- phone route `POST /internal/v1/eligibility/trial/owner/events`는 승인된 `/internal/v1/eligibility/{kind}/...` namespace와 하이픈 없는 경로를 유지하며 기존 `/internal/v1/eligibility/trial/events` strict schema를 섞지 않는다.
- Guest merge `UserMerged`는 별도 `POST /internal/v1/owners/merge/events`를 사용하고 기존 v1 payload 의미를 변경하지 않는다.
- `503 OWNER_REBIND_PENDING` + bounded `Retry-After`는 business prerequisite pending에 맞고 기존 5xx retry와 결합할 수 있다. 425는 TLS early-data 의미와 proxy/client 지원 때문에 채택하지 않았다.
- 409는 permanent conflict로 producer가 재시도하지 않아야 하고, 202는 Billing이 durable async worker로 event를 인수한 경우가 아니므로 pending 응답에 사용하지 않는다.

## 2026-09-02 PLAN-006 승인과 Jira TMI-120 생성

- 사용자가 PLAN-006과 D1~D5 권장안을 모두 승인했다. `CONTRACT_DECISIONS.md` C14-A에 lifecycle 분리, stable subject owner CAS, active Reservation retry, bounded legacy-source fencing과 목적 종료 후 source 연결 cleanup을 확정 계약으로 기록했다.
- phone 재가입은 기존 Guest→Member `UserMerged` v1을 재사용하지 않고 Identity의 별도 source→target 승인 event를 사용한다. lifecycle별 decoder는 분리하고 검증 뒤 내부 공통 `OwnerRebindCommand`로 수렴한다.
- Jira `TMI-120` `[Billing] Retained trial owner rebind consumer 및 Transaction 구현`을 TMI `작업`, 기본 Medium, `해야 할 일`, resolution 없음으로 생성하고 저장된 제목·본문·상태를 재확인했다.
- Jira에는 stable subject 불변식, active Reservation pending, late exact Session fencing, schema v4/index, Mongo retry·concurrency, workload security/trace/privacy와 cross-service production gate를 완료 조건으로 포함했다.
- phone event·route·field와 503/Retry-After는 확정됐다. Identity consumer별 durable fan-out, exact IAM action과 cleanup window를 후속 cross-service ADR로 확정한 뒤 reader-first 구현을 시작한다.
- Jira 댓글·담당자·상태 전환은 수행하지 않았고 애플리케이션 코드는 변경하지 않았다.

## 2026-09-02 PLAN-006 decoder와 legacy-source fencing 설명

- lifecycle별 strict decoder는 기존 Guest→Member `UserMerged`와 향후 phone 재가입 승인 event의 서로 다른 wire schema·의미를 각 입구에서 엄격히 검증한 뒤, Billing 내부 공통 `OwnerRebindCommand`로 정규화하는 구조다. event 의미를 재해석하거나 unknown field·coercion을 허용하는 generic decoder가 아니다.
- `OwnerRebindCommand`는 검증 완료된 eventId, source/target userId, lifecycle reason, occurredAt과 digest 같은 공통 처리 정보만 전달하며 실제 owner CAS·Reservation pending·멱등성 로직은 하나의 service에서 재사용한다.
- bounded legacy-source fencing은 owner rebind 전에 Learning Core outbox에 source userId로 이미 저장된 exact AttemptGroup/Session status event가 Billing owner 전환 뒤 늦게 도착해 유실되는 것을 막는다.
- 이는 source 사용자를 새 owner나 actor로 다시 허용하는 기능이 아니다. 인증된 Learning Core workload의 rebind 이전 exact group/session event만 GRADING·terminal 수렴 목적으로 제한적으로 허용하며 새 reserve·replacement·다른 Session은 차단한다.
- fence는 exact subject/group/session, rebind 시각과 허용 transition으로 제한하고 관련 Session terminal 또는 승인 retry window 종료 후 source 연결을 삭제한다.

## 2026-09-01 PLAN-006 retained trial owner rebind 초안

- `docs/plans/PLAN-006-retained-trial-owner-rebind.md`를 새로 작성했다. 다음 Billing vertical slice는 새 Claim·Grant·consumption을 만들지 않고 stable `subjectRefId`의 current owner mapping만 source→target으로 변경하는 작업이다.
- Identity의 기존 `UserMerged` v1은 Guest→Member merge 전용으로 유지하고 phone 재가입에 재사용하지 않는다. phone 재가입은 Identity가 source→target 관계를 명시적으로 승인하는 `TrialOwnerRebindApproved` schema v1과 분리 route를 사용한다.
- 권장 정책은 lifecycle별 decoder + 공통 owner-rebind domain, active RESERVED/PROCESSING 종료까지 최대 5분 retry, pre-rebind exact Session의 source status event를 terminal 수렴 목적으로만 한시 허용, source 연결은 필요 기간 뒤 cleanup하는 방식이다.
- Claim/Grant/ledger/Reservation/AttemptGroup/Session은 stable subjectRef를 사용하므로 rewrite하지 않는다. `BillingSubjectLink.userId` expected-owner CAS와 owner version, event inbox/rebind record, schema v4와 replica-set concurrency test가 Billing 구현 핵심이다.
- Identity의 current UserMerged publisher는 Learning Core 전용 단일 delivery이고 Learning Core에도 consumer 구현이 없다. Identity durable fan-out·Learning Core ownership migration·cross-service E2E 전에는 Billing PLAN 완료만으로 production owner rebind를 활성화하지 않는다.
- D1~D5, cross-service wire ADR와 privacy retention을 승인한 뒤 Jira 생성·구현으로 진행한다. 이번 turn은 계획 문서와 작업 기록만 변경했고 애플리케이션·계약 기준·Jira를 변경하지 않았다.

## 2026-09-01 Learning Core TMI-118 보완 재검토

- 이전 검토에서 지적한 Summary duplicate-key Transaction blocker는 commit `4781723`, PR #26 merge `4f9e74c`로 Learning Core `develop`과 `origin/develop`에 반영됐다.
- `AttemptGroupSummaryCompletionService`는 Transaction 안에서 `DuplicateKeyException`을 삼키지 않는다. 실패한 Transaction을 종료한 뒤 바깥 bounded loop가 전체 작업을 새 Transaction으로 재시도하고, 기존 Summary의 deterministic identity를 검증한 뒤 Job·Session·outbox 수렴을 계속한다.
- Learning Core 현재 develop에서 `./gradlew clean test`를 새로 실행해 총 444개, skipped/failures/errors 0과 `BUILD SUCCESSFUL`을 확인했다. 기존 release blocker는 해소된 것으로 판정한다.
- 새 테스트는 duplicate insert rollback 후 whole-unit retry, unknown commit result retry, 기존 Summary identity conflict와 coordinator terminal-slot duplicate 전파를 검증한다. 다만 실제 replica-set, multi-instance lease, 실제 SigV4·cross-service trace 검증은 mock 단위 테스트가 아니라 staging/release gate로 남아 있다.
- 다음 제품 vertical slice는 Billing owner rebind가 맞다. 단, Identity의 현재 `UserMerged` v1은 ACTIVE GUEST source→기존 MEMBER target canonical merge 계약이며 탈퇴 후 같은 전화번호 재가입 이벤트가 아니다. Billing ADR에서 guest merge와 phone 재가입의 trigger를 구분하고, 기존 eligibility projection 기반 lazy rebind 또는 전용 lifecycle event 필요성을 먼저 확정해야 한다.

## 2026-09-01 Learning Core TMI-118 검토와 다음 작업 판정

- Learning Core `TMI-118`은 commit `63d0f7d`, PR #25 merge `c00d872`로 `develop`과 `origin/develop`에 반영됐고 Jira status/resolution도 `완료`다. 현재 develop에서 `./gradlew clean test` 439개가 성공했다.
- 그러나 신규 AttemptGroup 전용 테스트는 evidence 2개, publisher 3개, coordinator 1개뿐이며 replica-set Mongo Transaction/rollback·concurrency, lease reclaim/half-open 경쟁, 실제 SigV4·trace·privacy 계약을 직접 검증하지 않는다. Jira 완료 조건 전체가 테스트로 입증된 상태는 아니다.
- `AttemptGroupSummaryCompletionService`가 Mongo Transaction 내부 `insert`의 `DuplicateKeyException`을 잡고 같은 Transaction을 계속 사용한다. MongoDB는 duplicate key로 Transaction을 abort하므로 replay에서 이후 Summary Job·Session·outbox 수렴이 실패할 수 있는 release blocker다.
- 따라서 즉시 다음 작업은 Learning Core TMI-118 안정화 수정과 replica-set integration/transport contract test 보강이다. 이것이 끝난 뒤 다음 제품 vertical slice는 Billing의 탈퇴·재가입 `UserMerged` retained subject owner rebind가 맞다.
- Billing에는 UserMerged consumer와 owner-transfer Transaction이 없고 통합 계약도 구체 wire를 별도 ADR 전까지 금지한다. owner rebind Jira도 아직 없으므로 Identity schema·Billing owner 필드/index·active Reservation/AttemptGroup 충돌 정책을 조사해 계획과 ADR을 먼저 확정해야 한다.
- TMI-118 production 활성화는 별개로 Billing consumer 배포·flag, 양쪽 Mongo replica-set/index, Lattice/IAM/SG, publisher idle→writer canary와 상태·오류 E2E gate가 남아 있다.

## 2026-08-31 Billing AttemptGroup 업무 span 보완

- Learning Core 전달사항을 대조한 결과 기존 production Controller에는 명시적인 `attempt_group_event_consume` 업무 span이 없고 기존 첫 trace test가 test 내부에서 span을 수동 생성한 사실을 확인했다.
- 실제 Controller가 payload 크기 확인 뒤 strict decode부터 service의 멱등성·Mongo 처리까지 `attempt_group_event_consume` span으로 감싸도록 `AttemptGroupEventTracing`을 추가했다. Micrometer에 INTERNAL enum이 없으므로 kind를 지정하지 않는 OpenTelemetry 기본 INTERNAL span으로 생성하며 정상·RuntimeException·Error 모두 종료한다.
- embedded Tomcat 실제 HTTP 요청과 capturing SpanProcessor로 inbound traceId 유지, HTTP SERVER와 consume의 다른 spanId 및 descendant 관계, 정확한 span 이름·INTERNAL kind, decoder/service scope, baggage 미전파와 민감 attribute 부재를 검증했다. Spring Security INTERNAL span이 중간에 존재할 수 있다.
- 외부 event JSON, endpoint와 HTTP status, 기존 구조화 로그·metric은 변경하지 않았다. 선택 제안인 `trace_context_missing` metric rename은 호환성 영향을 고려해 이번에는 수행하지 않았다.
- `./gradlew clean test` 전체 138개 테스트가 성공했다. Jira `TMI-117`은 완료 상태를 유지하고 댓글·상태를 변경하지 않았다.

## 2026-08-31 Learning Core trace 연동 전달사항 정리

- `docs/contracts/LEARNING_CORE_ATTEMPT_GROUP_TRACE_HANDOFF.md`에 Learning Core outbox publisher와 Billing consumer가 공유할 W3C Trace Context, 구조화 로그, metric cardinality와 privacy 계약을 복사 가능한 형태로 정리했다.
- 같은 `traceId` 안에서 origin/publisher/consumer가 서로 다른 `spanId`를 가져야 하며, 저장된 traceparent를 그대로 replay하지 않고 publisher span을 만든 뒤 해당 context를 HTTP header에 inject하도록 명시했다.
- 공통 log field는 service/operation/outcome/traceId/eventId/durationMs이며 Billing만 eventAgeMs를 추가한다. baggage, 사용자·group·session 식별자, payload, digest와 credential은 log/trace에서 제외한다.
- 이는 전달 문서 정리이며 Learning Core 코드·설정과 Billing 애플리케이션 동작은 변경하지 않았다. publisher outcome allowlist는 Learning Core 구현 PLAN에서 최종 고정해야 한다.

## 2026-08-31 Jira TMI-117 완료

- 사용자 승인에 따라 Jira `TMI-117`을 전환 ID `41`로 `해야 할 일`에서 `완료`로 변경했다.
- 재조회 결과 status와 resolution은 모두 `완료`이며 resolution 시각은 2026-08-31 17:30:03 KST다. Jira 댓글·담당자·본문은 변경하지 않았다.
- Billing 코드와 기능 플래그는 추가 변경하지 않았다. 실제 Learning Core outbox/publisher, Lattice/IAM/SG와 staging E2E는 후속 production gate로 유지한다.

## 2026-08-31 TMI-117 변경 파일 역할 검토

- 사용자의 요청에 따라 TMI-117에서 추가·수정된 API, decoder, 상태 처리, inbox, repository, 관측성, 보안, 설정과 테스트 파일의 책임을 실제 코드 기준으로 다시 분류했다.
- 파일 수가 늘어난 이유는 HTTP 수신, strict 계약 검증, Mongo Transaction/CAS 상태 전이, 오류 계약, metric/trace와 계층별 테스트를 분리했기 때문이다. 검토 과정에서 애플리케이션 동작이나 계약은 변경하지 않았다.
- 정상 no-op 결과는 `APPLIED/DUPLICATE/STALE`로 204 처리하고, `CONFLICT/PROJECTION_NOT_READY`는 각각 409/503 예외 계약으로 분리된 현재 구현을 확인했다.

## 2026-08-31 TMI-117 PLAN-005 AttemptGroup event consumer 구현 완료

- `POST /internal/v1/attempt-group-events`와 16 KiB strict decoder, canonical SHA-256 digest, target별 evidence/failureCode 검증을 구현했다. endpoint flag는 기본 false이며 TEST mode에서는 Learning Core workload role만 허용한다.
- 동일 `inbound_event_inbox` collection에 `_class`와 user/group/session/evidence/failureCode 없이 최소 document를 저장한다. global eventId·120일 TTL과 Identity partial revision index를 그대로 사용하며 cross-producer same eventId는 digest가 같아도 conflict로 고정했다.
- active Session/group/subject link fencing과 `OPEN/GRADING → GRADING/COMPLETED/RETAKE_AVAILABLE` 단방향 전이를 구현했다. terminal 전이는 AttemptGroup·AttemptSession·inbox를 하나의 Mongo Transaction과 expected-version CAS로 반영한다.
- RETAKE_AVAILABLE은 AttemptSession만 FAILED로 닫고 Claim·Grant·allocation·ledger consumption을 변경하지 않는다. retention으로 subject link가 없을 때 COMPLETED만 익명 audit close를 허용하고 GRADING/RETAKE는 STALE 처리한다.
- Micrometer Tracing OpenTelemetry bridge와 W3C-only ContextPropagators를 추가하고 baggage를 비활성화했다. 구조화 로그에는 service/operation/outcome/traceId/eventId/durationMs/eventAgeMs만 기록하며 식별자와 payload를 제외한다.
- decoder·service·API/security·metric/log/privacy·W3C HTTP propagation과 replica-set inbox/상태/동시성 테스트를 추가했다. `./gradlew clean test` 전체 137개 테스트가 성공했다.
- 실제 Learning Core outbox/publisher, trace exporter/backend, Lattice/IAM/SG와 staging E2E는 구현하지 않았으며 production caller gate를 유지한다. 이후 사용자 승인으로 Jira는 완료 처리했으며 댓글 추가, commit·push는 수행하지 않았다.

## 2026-08-31 PLAN-005 승인 및 Jira TMI-117 생성

- 사용자가 PLAN-005를 승인해 Jira `TMI-117` — `[Billing] AttemptGroup status event consumer 구현`을 `작업` 유형으로 생성했다. 상태는 `해야 할 일`, 담당자는 미지정이다.
- Jira 본문에 strict decoder/digest, shared inbox, active Session fencing, group/session Transaction·CAS, 204/400/409/422/503, approved failureCode, trace/log/metric과 replica-set 테스트 완료 조건을 반영했다.
- Learning Core outbox/publisher, 실제 Lattice/IAM/SG, staging E2E와 owner rebind는 제외 범위와 후속 gate로 유지했다.
- PLAN-005 상태는 사용자 승인·Jira 생성 완료·구현 요청 대기로 갱신했다. 애플리케이션 구현과 Jira 상태 전환은 아직 수행하지 않았다.

## 2026-08-31 PLAN-005 event 처리 결과 분류 설명

- `APPLIED`, `DUPLICATE`, `STALE`, `CONFLICT`, `PROJECTION_NOT_READY`는 AttemptGroup의 업무 상태가 아니라 Billing consumer가 수신 event 한 건을 어떻게 처리했는지 나타내는 내부 outcome이다.
- APPLIED는 새 유효 event를 Transaction으로 반영한 경우, DUPLICATE는 같은 eventId·digest가 이미 commit된 경우, STALE은 새 event지만 이전 Session·이미 닫힌 상태·동일 상태 no-op이라 현재 projection을 바꾸지 않는 경우다. 세 경우 모두 Learning Core가 재전송을 끝낼 수 있도록 204를 반환한다.
- CONFLICT는 같은 eventId의 내용 변조 또는 존재하는 group/session/subject 관계 충돌이므로 409와 운영 조사 대상이다. PROJECTION_NOT_READY는 정상 순서상 group/session이 아직 보이지 않는 일시 상태이므로 inbox에 저장하지 않고 503과 Retry-After 5초로 같은 event 재전송을 요구한다.

## 2026-08-31 PLAN-005 AttemptGroup status event consumer 계획서 작성

- `docs/plans/PLAN-005-attempt-group-status-event-consumer.md`를 작성했다. 작성 당시에는 구현 승인 대기·Jira 미생성이었고, 현재는 위 기록처럼 승인 및 `TMI-117` 생성이 완료됐다.
- 범위는 Billing `POST /internal/v1/attempt-group-events`, strict decoder/digest, shared inbox eventId 멱등성, active Session fencing, group/session Transaction·CAS, stable 204/400/409/422/503, trace·duration/event-age 관측성과 replica-set concurrency test다.
- 기존 Identity inbox entity를 이동하지 않고 같은 `inbound_event_inbox` collection에 AttemptGroup 전용 최소 view를 추가한다. global unique eventId·120일 TTL을 재사용하고 Identity revision partial index는 유지하므로 schema v3와 기존 index를 변경하지 않는다.
- approved 1A·2A·3A·4A를 완료 조건으로 고정했다. RETAKE_AVAILABLE은 새 Claim/Grant/consumption이 아니며 COMPLETED는 불가역이다.
- Learning Core outbox/publisher, 실제 Lattice/IAM/SG와 staging E2E는 이번 Billing 계획의 제외 범위·후속 gate다. consumer를 먼저 배포한 뒤 publisher 별도 PLAN/Jira를 진행한다.

## 2026-08-31 Learning Core TMI-116 수정·merge 재검증 완료

- Learning Core 수정 commit `c3e3c82`와 merge commit `d95d18b`가 local·`origin/develop`에 반영됐다.
- operation-first 복구로 `SESSION_COMMITTED + ENTITLEMENT_CONFIRMING` same-key 요청이 confirm/status 상태 머신을 다시 실행한다. operation이 purge된 경우에만 durable Session fallback을 사용한다.
- Mongo transient/unknown commit 결과는 shared reservation을 cancel하지 않고 operation·Session 관측 결과와 same-key retry로 수렴한다. 확정적인 local `IllegalStateException`이고 operation·Session 전진이 모두 관측되지 않을 때만 cancel 보상을 수행한다.
- Billing success decoder는 scalar/date/enum coercion과 missing required field를 거절하며 confirm `attemptGroupStatus=OPEN`·필수 terminal timestamp, cancel/status의 조건부 timestamp를 검증한다.
- 최신 Learning Core `develop`에서 `./gradlew clean test`가 성공했고 앞서 보고한 세 finding은 해소됐다. 검토 범위에서 새 merge 차단 결함은 확인되지 않았다.
- production 활성화 전 실제 Mongo replica-set transient/unknown commit failure injection, Lattice/IAM/SG와 INITIAL·REPLACEMENT staging E2E gate는 계속 남아 있다.

## 2026-08-31 다음 개발 작업 — AttemptGroup 상태 연동

- TMI-116이 `attemptGroupId`를 Learning Core ExamSession에 저장했으므로 다음 vertical slice는 시험 결과 lifecycle을 Billing AttemptGroup에 전달하는 상태 event 연동이다.
- 상태는 `OPEN → GRADING → COMPLETED` 또는 최종 결과 생성 실패 시 `RETAKE_AVAILABLE`이다. `COMPLETED`는 필수 feedback·유효 score·Summary가 모두 조회 가능할 때만 허용하고 다시 열지 않는다.
- `RETAKE_AVAILABLE`은 무료권 복원이나 새 Grant 발급이 아니다. 기존 consumption·AttemptGroup·mockExamId를 유지한 REPLACEMENT Session만 추가 차감 없이 허용한다.
- 안전한 개발·배포 순서는 event schema와 전이를 최종 동결한 뒤 Billing consumer를 먼저 구현·배포하고, Learning Core가 동일 local Transaction에서 outbox를 적재한 뒤 SigV4 publisher를 활성화하는 순서다.
- 별도 운영 gate인 Mongo replica-set failure injection, Lattice/IAM/SG, INITIAL·REPLACEMENT staging E2E는 이 개발 작업과 구분하되 production flag 활성화 전에 모두 완료해야 한다.

## 2026-08-31 AttemptGroup 상태 연동 미확정 정책 선택지

- 이미 확정된 것은 `COMPLETED` evidence 조건, sequence 없는 at-least-once event, eventId/digest 멱등성, active Session fencing과 stale Session 204 처리다.
- 추가 선택이 필요한 항목은 failureCode의 세분화, 같은 active Session의 out-of-order event 수렴, target projection 누락 분류, Learning Core outbox의 재시도·보존 정책이다.
- 권장 조합은 `1A 고정 저카디널리티 failureCode`, `2A 상태 전이표+Session fencing`, `3A 503/204/409 원인별 분리`, `4A pending 무TTL+지수 backoff와 delivered/dead-letter 기한 보존`이다.
- 사용자가 `1A·2A·3A·4A`를 모두 승인했다. CONTRACT_DECISIONS, ADR-001과 통합 계약에 failureCode allowlist, 상태/Session fencing, 503·204·409 분류, durable outbox와 trace 정책을 반영했다.
- traceId는 failureCode를 대체하지 않고 상세 조사 경로를 제공한다. 현재 Learning Core의 requestId/Sentry만으로는 Billing까지 연결되지 않으므로 W3C traceparent propagation과 Billing tracing 기반을 구현 계획에 포함한다.
- 사용자 추가 승인으로 AttemptGroup publish/consume 구조화 로그에 서비스 식별 `service`, 고정 `operation`·`outcome`, 단계 처리 시간 `durationMs`를 포함한다. Billing consume에는 생성부터 수신까지 지연 `eventAgeMs`도 기록해 서비스 내부 지연과 outbox/network/retry 지연을 분리한다.

## 2026-08-31 상태 전이표·Session fencing 방식 상세 설명

- Billing은 event의 `occurredAt` 최신 여부로 덮어쓰지 않고 먼저 event `sessionId`가 AttemptGroup의 `activeSessionId`와 같고 해당 AttemptSession이 `ACTIVE`인지 확인한다. 다르면 이전·폐기 Session event이므로 204 stale 처리한다.
- fencing을 통과한 뒤 현재 group status와 targetStatus의 허용 전이표를 적용한다. `OPEN→GRADING/COMPLETED/RETAKE_AVAILABLE`, `GRADING→COMPLETED/RETAKE_AVAILABLE`만 전진이며 같은 상태는 duplicate no-op, COMPLETED 이후는 재개방하지 않는다.
- RETAKE_AVAILABLE 전환은 해당 AttemptSession을 FAILED terminal로 닫는다. 이후 같은 Session의 늦은 event는 stale이고, 새 REPLACEMENT confirm이 새로운 activeSessionId와 OPEN을 만든 뒤 새 Session event만 허용한다.
- group/session/inbox 변경은 expected version CAS를 포함한 하나의 Mongo Transaction으로 처리해 동시에 도착한 terminal event 중 하나만 승리하도록 한다.

## 2026-08-31 revision 없이 안전하다는 의미 보완

- revision 없이 보장하는 것은 producer의 정확한 event 생성 순서 복원이 아니라 duplicate 부작용 방지, 과거 Session 격리, 상태 역행 방지와 동시 write 단일 승자다.
- eventId/digest는 같은 event 재전송을 막고, activeSessionId/AttemptSession state는 재응시 세대를 구분하며, 단방향 상태 전이표는 늦은 GRADING이 COMPLETED를 되돌리지 못하게 하고, Mongo document version CAS는 동시 update 중 하나만 commit하게 한다.
- 같은 active Session에서 서로 모순되는 COMPLETED와 RETAKE_AVAILABLE을 모두 발행한 경우 revision 없는 consumer는 어느 것이 producer 기준 최신·정답인지 알 수 없다. 이는 Learning Core가 둘 중 하나만 durable terminal event로 생성해야 한다는 producer 불변식과 outbox Transaction으로 막아야 한다.
- 따라서 event revision은 정상 상태 머신에서는 불필요하지만 모순 terminal event의 우선순위를 consumer가 판정해야 한다면 추가해야 한다.

## 2026-08-31 Billing merge 및 Learning Core TMI-116 구현 리뷰

- Billing PR #3의 merge commit `39e424d`가 local·`origin/develop`에 반영됐고 BenefitDefinition 구현 commit은 `18f1265`다. 현재 Billing 전체 테스트는 성공했다.
- Learning Core `feat/TMI-116-billing-reservation-exam-saga`는 commit `9241a39`로 구현·push됐고 전체 테스트도 성공했다. 공개 API shape, 기본 off flag, Billing URL/DTO, 서울 리전 `vpc-lattice-svcs` SigV4의 정상 흐름은 Billing 계약과 일치한다.
- merge 전 수정이 필요한 핵심 결함이 있다. durable `ENTITLEMENT_CONFIRMING` Session을 operation보다 먼저 replay하면 `SESSION_COMMITTED`의 confirm/status 재조정을 영구 건너뛴다. Mongo commit의 transient/unknown 실패를 일반 local failure로 분류해 동시 same-key winner가 commit 중인데 shared reservation을 cancel할 수 있다.
- Billing 성공 응답 decoder는 unknown/duplicate/trailing field를 막지만 scalar coercion과 missing required field를 완전히 차단하지 않고, confirm의 `attemptGroupStatus=OPEN`·`confirmedAt` exact 검증도 빠져 있다.
- TMI-116 branch의 단일 commit에는 saga 외에도 비용 추정·10초 챌린지·frontend handoff 등 관련 없는 대형 문서 변경이 함께 포함돼 있으므로 PR 범위를 분리하거나 의도된 포함인지 확인해야 한다.
- production caller gate는 유지한다. 위 코드 결함 수정과 회귀 테스트, replica-set failure injection, 실제 Lattice/IAM/SG 및 INITIAL·REPLACEMENT staging E2E 전에는 flag를 활성화하지 않는다.

## 2026-08-28 TMI-115 PLAN-004 BenefitDefinition foundation 구현 완료

- `benefit_definitions` collection과 FREE_EXAM_ONCE의 UNIT·EXAM_ATTEMPT·1-unit·policy-v1 seed를 구현했다. seed 재실행은 no-op이고 누락 시 생성하며, 기존 policy drift는 자동 수정하지 않고 startup을 실패시킨다.
- TrialClaim·TrialCandidateAlias·EntitlementGrant의 저장 field와 repository query를 `benefitCode`로 통일했다. 최초 INITIAL reserve는 BenefitCatalog를 확인해 definition의 unit 수로 Claim·Grant를 lazy 생성하고, 기존 Claim도 Claim–Grant–Definition code와 수량이 일치해야 사용한다.
- Mongo schema를 v3로 올리고 alias unique index와 grant source unique index key를 `benefitCode`로 변경했다. v2 `benefitType`·`grantType` document 또는 이름이 같은 legacy index가 있으면 자동 변경하지 않고 fail-fast한다.
- definition 누락·inactive·Grant reference 불일치는 같은 reserve Transaction을 rollback하고 `503 BILLING_TEMPORARILY_UNAVAILABLE`로 수렴한다. eligibility event만으로 Claim/Grant를 만들지 않으며 기존 confirm·cancel·expiry·replacement wire 동작은 유지했다.
- ADR-001과 AGENTS의 현재 구현 기준을 PLAN-004/TMI-115 및 schema v3 계약으로 갱신했다. PREMIUM_SUBSCRIPTION·SubscriptionEntitlement·Store·public API·AttemptGroup event·owner rebind·타 서비스/AWS는 추가하지 않았다.
- `./gradlew clean test` 전체 96개 테스트가 성공했고 `git diff --check`도 통과했다. Jira는 별도 상태 변경 승인 전까지 `해야 할 일`로 유지한다.

## 2026-08-28 PLAN-004 Jira 작업 생성

- TMI 프로젝트에 `TMI-115` — `[Billing] BenefitDefinition foundation 구현`을 `작업` 유형으로 생성했다. 상태는 `해야 할 일`, 담당자는 미지정이다.
- Jira 본문에 `benefit_definitions`와 FREE_EXAM_ONCE seed, exact drift fail-fast, Claim·alias·Grant의 `benefitCode` 전환, Mongo v3/index 보정, BenefitCatalog 기반 lazy 발급과 Testcontainers 회귀 완료 조건을 반영했다.
- PREMIUM_SUBSCRIPTION·SubscriptionEntitlement·Store lifecycle·구독 Reservation 분기, eager TrialClaim, public API, AttemptGroup event·owner rebind와 타 서비스/AWS 변경은 제외 범위로 명시했다.
- PLAN-004 상태를 사용자 승인·Jira 생성·구현 전으로 갱신했다. 애플리케이션 코드와 Mongo schema 구현은 아직 시작하지 않았다.
- production caller는 PLAN-004만으로 활성화하지 않고 후속 AttemptGroup/Learning Core saga와 Lattice staging E2E gate를 유지한다.

## 2026-08-28 PLAN-005 초안 철회 — Learning Core 작업으로 범위 정정

- 사용자가 구현 대상은 Billing이 아니라 Learning Core라고 정정했다. 잘못 작성한 Billing `PLAN-005-attempt-group-status-event-consumer.md` 초안은 삭제했고 Billing consumer를 현재 작업으로 진행하지 않는다.
- Billing 애플리케이션·계약·Jira는 변경하지 않았다. 기존 `TMI-115` PLAN-004 상태도 그대로 유지한다.
- Learning Core에서는 AttemptGroup event publisher보다 먼저 필요한 `Idempotency-Key + reserve → Session commit → confirm` 생성 saga 계획을 별도 저장소 문서로 작성한다.
- 아래 PLAN-005 작성 기록은 범위 오해가 있었던 이력이며 활성 계획이 아니다.

## 2026-08-28 PLAN-005 AttemptGroup 상태 event consumer 계획서 작성

- `docs/plans/PLAN-005-attempt-group-status-event-consumer.md` 초안을 작성했다. 상태는 사용자 승인 대기이며 Jira는 아직 생성하지 않았다.
- endpoint는 `POST /internal/v1/attempt-group-events`, schema v1과 16 KiB strict decode, canonical digest, shared inbox 멱등성, active Session fencing, group/session CAS와 단일 Mongo Transaction을 구현 범위로 고정했다.
- `AttemptGroup.subjectRefId`는 실제 `userId`가 아니므로 직접 비교하지 않는다. active·unexpired `BillingSubjectLink`로 owner를 resolve하고 group/session/link의 subject·claim 관계와 event userId를 검증한다. expired/anonymized link는 stale 처리하고 mapping을 복원하지 않는다.
- 유효 terminal evidence는 `GRADING` event가 누락돼도 `OPEN`에서 직접 전진한다. `COMPLETED`와 `RETAKE_AVAILABLE` 확정 후 역행 event, abandoned/old Session event는 `STALE` inbox와 204로 수렴한다.
- group/session missing은 `503 ATTEMPT_PROJECTION_NOT_READY`와 `Retry-After: 5`, group-session-owner 구조 충돌은 `409 EVENT_TARGET_CONFLICT` non-retryable로 분리했다. malformed/conflict/missing은 inbox에 저장하지 않는다.
- RETAKE failureCode 초안 allowlist는 `REQUIRED_RESULTS_UNAVAILABLE`, `SUMMARY_UNAVAILABLE`, `GRADING_DEADLINE_EXCEEDED`, `RESULT_INTEGRITY_VIOLATION`이다. provider 원문과 자유 형식 사유는 받거나 저장하지 않는다.
- PLAN-005는 `TMI-113` 완료를 선행 조건으로 하며 PLAN-004 BenefitDefinition과 독립적이다. 이번 사용자 우선순위에 따라 PLAN-005를 먼저 구현할 수 있지만 PLAN-004/TMI-115 상태 자체는 변경하지 않았다.
- Learning Core publisher/outbox, owner rebind, Reservation saga·reconciliation, 실제 Lattice/AWS와 결제는 제외했다. 이번 턴에는 계획서만 작성했고 애플리케이션·ADR·Jira는 변경하지 않았다.

## 2026-08-28 PLAN-004 BenefitDefinition foundation 계획서 작성

- `docs/plans/PLAN-004-benefit-definition-foundation.md`를 작성하고 사용자 승인을 거쳐 Jira `TMI-115`로 구현 범위를 고정했다.
- 범위는 versioned `benefit_definitions` catalog, stable FREE_EXAM_ONCE code와 exact seed/drift validation, Claim·alias·Grant의 benefitCode reference, schema v3/index 보정과 reserve/lifecycle 회귀 테스트다.
- `benefitCode`를 Mongo `_id`로 사용해 built-in uniqueness를 사용하며 별도 중복 secondary unique index는 만들지 않는다. 기존 `benefitType`·`grantType` field는 미배포 v3에서 `benefitCode`로 통일한다.
- 현재 TrialEligibility → 최초 reserve의 lazy TrialClaim·1-unit Grant → hold → Session commit 뒤 confirm 소비 흐름은 변경하지 않는다. definition 누락·inactive·drift는 부분 지급 없이 retryable 503으로 fail-closed한다.
- PREMIUM_SUBSCRIPTION·SubscriptionEntitlement·Store/renewal·구독 Reservation 분기, TrialClaim eager creation, public 상품 API와 AttemptGroup event는 제외했다.
- 다음 단계는 Jira 완료 조건을 기준으로 PLAN-004를 구현하는 것이다. 완료 후 AttemptGroup event consumer → owner rebind → Learning Core saga/Lattice staging E2E 순서로 돌아간다.

## 2026-08-28 BenefitDefinition·무료 Grant·구독 구조 승인과 즉시 영향

- 사용자가 `BenefitDefinition → TrialClaim/EntitlementGrant 또는 SubscriptionEntitlement → Reservation → AttemptGroup` 구조를 장기 Billing 계약으로 승인해 CONTRACT_DECISIONS 1C에 반영했다.
- 현재 구현에 이미 있는 것은 TrialClaim, 무료 1-unit EntitlementGrant, Reservation, AttemptGroup과 ledger/allocation이다. SubscriptionEntitlement는 후속 구독 기능이라 지금 만들지 않는다.
- 현재 즉시 빠진 요소는 BenefitDefinition이다. FREE_EXAM_ONCE가 Claim·alias·Grant와 repository에 문자열로 하드코딩돼 있으므로 실제 catalog 적용 시 definition collection/seed, stable benefitCode, unique index와 startup validation, Claim/Grant reference와 contract test가 필요하다.
- Reservation의 INITIAL/REPLACEMENT는 attempt 관계이므로 유지한다. 구독 구현 때 별도 authorization source type/reference를 추가하고, 무료는 allocation hold·consume, 구독은 active 기간 확인·usage audit로 분기해야 한다. 지금 무료 DTO/collection을 미리 확장하지 않는다.
- 권장 실행 순서는 작은 BenefitDefinition foundation vertical slice를 별도 PLAN/Jira로 구현한 뒤 AttemptGroup event consumer를 진행하거나, AttemptGroup consumer가 catalog와 독립적이므로 먼저 완료하고 구독 착수 직전에 foundation을 구현하는 두 방식이다. 현재 미배포이고 하드코딩 제거 효과가 있어 foundation을 먼저 하는 안을 권장한다.
- 이 작업에서는 계약·상태 문서만 수정했고 애플리케이션 코드·Mongo schema·Jira는 변경하지 않았다. BenefitDefinition 구현에는 별도 계획과 Jira 승인이 필요하다.

## 2026-08-28 TrialEligibility 역할 확인

- TrialEligibility는 Identity의 verified/revoked phone eligibility event를 Billing이 로컬에 반영한 사용자별 현재 자격 projection이다. raw phone은 저장하지 않고 consumerScopeId, userId, bindingRevision, VERIFIED/REVOKED 상태, verified phone에서 파생된 opaque candidate와 event/digest high-water만 저장한다.
- 이 record는 무료권·Claim·Grant나 소비 이력이 아니다. 최초 reserve가 현재 VERIFIED이고 candidate가 존재하는지 확인한 뒤에만 TrialClaim/Grant 생성 또는 기존 Claim 연결을 진행하도록 하는 fail-closed 판단 근거다.
- revoke event가 오면 state를 REVOKED로 바꾸고 candidate를 제거하되 revision high-water tombstone을 유지해 과거 verified event가 상태를 되돌리지 못하게 한다. revoke만으로 기존 TrialClaim·confirmed consumption을 삭제하거나 복원하지 않는다.

## 2026-08-28 BenefitDefinition·Grant·TrialClaim·Ledger 역할 정리

- BenefitDefinition은 “무슨 응시권인가”를 정의하는 catalog/policy다. immutable benefitCode, 표시 이름, one-time/subscription 유형, 소비 방식과 policy version을 가진다.
- EntitlementGrant는 “누가 어떤 경로로 실제 응시권을 받았는가”를 나타내는 발급 instance/batch다. 무료 MVP에서는 subject가 가진 FREE_EXAM_ONCE 1 unit과 available/held/consumed projection이다.
- TrialClaim은 일반 사용 이력이 아니라 `(verified-phone candidate, FREE_EXAM_ONCE)`가 3년 안에 이미 발급됐는지 증명하는 anti-abuse/dedupe record다. 무료권 발급 source로 Grant와 연결된다.
- 실제 시간순 이력은 append-only EntitlementLedger의 GRANTED·RESERVED·RELEASED·CONSUMED event가 담당한다. ReservationAllocation은 특정 시험 hold가 어느 Grant unit을 사용했는지 연결하고, Reservation·AttemptGroup은 시험 생성·재응시 lifecycle을 담당한다.
- 따라서 요약 관계는 `BenefitDefinition(종류) → EntitlementGrant(보유 권리) ← TrialClaim(무료 발급 근거) → Ledger(변경 이력)`이며 현재 BenefitDefinition만 미구현·하드코딩 상태다.

## 2026-08-28 현재 Benefit/Claim/Grant 구조와 구독제 방향 확인

- 현재 구현에는 TrialClaim과 EntitlementGrant가 있지만 BenefitDefinition/catalog collection은 없다. `FREE_EXAM_ONCE`가 TrialClaim·candidate alias·Grant와 repository 조건에 하드코딩돼 있다.
- verified event는 사전 `TrialEligibility` projection만 만들고, 최초 reserve가 기존 phone candidate Claim을 확인한 뒤 필요하면 TrialClaim·subject link·aliases와 `FREE_EXAM_ONCE` total 1-unit Grant·GRANTED ledger를 같은 Transaction에서 lazy 생성한다.
- TrialClaim은 phone당 무료 1회와 claimedAt+3년 dedupe, EntitlementGrant는 사용자의 실제 one-time unit과 available/held/consumed 상태를 담당한다. BenefitDefinition을 도입한다면 두 record가 stable benefitCode로 참조하는 공통 정책 catalog가 된다.
- 향후 유료 모델을 credit 대신 단순 구독제로 한다면 TrialClaim은 무료 1회 전용으로 유지하고 유료 권리는 기간형 SubscriptionEntitlement로 분리하는 것이 적절하다. 구독은 quantity 차감 대신 ACTIVE 상태와 startsAt/endsAt을 검증하며 시험마다 Reservation·Session idempotency와 usage audit는 계속 필요하다.
- 무료 사용권은 기존 hold/confirm/consume을 사용하고, 활성 구독은 unit을 차감하지 않되 Reservation이 어떤 entitlement source로 승인됐는지 기록하는 resolver가 필요하다. 구독 plan·Store 검증·갱신·해지·만료·grace period는 결제 착수 시 별도 계약으로 확정한다.
- 이번 확인에서는 credit 후속 방향을 구독 선호로 기록했지만 현재 무료 MVP 계약·코드와 동결된 결제 계약을 변경하지 않았다.

## 2026-08-28 사전 정의 혜택과 사용자 Claim/Grant 역할 구분

- 프로모션 공통 정보를 한 번만 사전 정의하고 사용자 보유 기록은 그 정의에 연결하자는 방향은 타당하다. 다만 공유되는 사전 정의와 phone/user별 상태를 가진 TrialClaim은 서로 다른 aggregate다.
- 권장 모델은 `BenefitDefinition` 또는 catalog에 immutable `benefitCode`, 표시 이름, unit type, 소비 정책과 policyVersion을 사전 등록하고, 사용자별 `EntitlementGrant`가 benefitCode·source campaign·quantity·expiry를 참조하는 정규화 구조다. 프로모션 이름·정책을 모든 Claim에 복제하지 않아도 된다.
- 사용자에게 무엇이 몇 개 귀속됐는지는 어떤 형태로든 저장해야 한다. “연결만 한다”의 연결 document가 바로 보유 기록이며, 현재는 EntitlementGrant의 subjectRefId·grantType·sourceType/sourceId와 unit projection이 그 역할을 한다. paid/promotion credits처럼 수량이 있는 권리는 amount나 개별 token 없이는 소유량을 판단할 수 없다.
- TrialClaim은 공유 catalog가 아니라 `(phone candidate, FREE_EXAM_ONCE)` 중복 수급 방지와 3년 retention을 가진 사용자별 anti-abuse record다. 하나의 TrialClaim을 여러 사용자·campaign이 공유하거나 빈 Claim pool을 미리 만들면 subject·claimedAt·retention 의미와 unique candidate 불변식이 깨진다.
- 따라서 사용자의 확장성 직관은 “TrialClaim을 미리 생성”보다 “BenefitDefinition을 미리 생성하고 Claim/Grant가 code로 연결”로 구현하는 것이 적절하다. 이 구조에서는 TrialClaim lazy/eager 생성 시점과 catalog 확장은 독립적으로 결정할 수 있다.
- 무료·일회성 exam pass는 개별 entitlement token 방식도 가능하지만 paid 100 credits까지 token 100개로 만들면 document 수가 커진다. one-off benefit은 unit/token, fungible credit는 grant batch+quantity를 쓰고 ReservationAllocation·ledger를 공통 소비 계층으로 두는 hybrid가 권장된다.
- 현재 코드는 benefit definition collection 없이 `FREE_EXAM_ONCE` 문자열이 Claim·alias·grant/repository에 하드코딩돼 있다. 후속 promotion/payment 단계에서 catalog와 resolver를 도입하면 되며, 이 논의만으로 현행 TrialClaim 생성 시점이나 계약을 변경하지 않았다.

## 2026-08-28 TrialClaim 사전 생성 제안 비교

- 현재는 verified event에서 `trial_eligibility` projection만 만들고, 최초 reserve Transaction에서 TrialClaim·candidate alias·subject link·1-unit grant·GRANTED ledger를 생성한 뒤 즉시 hold한다. 멘토 제안은 Claim 생성 시점을 verified event 처리로 앞당기는 계약 변경에 가깝다.
- TrialClaim을 미리 만들면 최초 reserve의 쓰기와 지연은 줄고, phone candidate dedupe와 사용자 권리 귀속을 더 일찍 확정할 수 있다. 반면 무료 시험을 시작하지 않은 모든 verified 사용자에게 Claim·alias·link가 생기고, grant까지 선발급하면 미사용 grant와 revoke·탈퇴·재가입 정합성 부담도 늘어난다.
- 가장 큰 제품 차이는 `claimedAt + 3년`의 시작점이다. 현재는 최초 reserve가 기산점이다. verified 시 TrialClaim을 ACTIVE로 만들면 인증 시점부터 3년이 시작되어 사용하지 않은 사용자도 보존·재수급 제한 대상이 된다. `claimedAt=null`의 ELIGIBLE Claim을 도입하면 별도 상태·CAS·purge 계약이 필요하며 현재 `trial_eligibility`와 역할이 중복된다.
- TrialClaim만 미리 만들고 grant는 reserve에서 만들면 reserve 단순화 효과가 제한적이다. Claim·alias·link·grant까지 선발급하면 의미는 명확하지만 event consumer가 자격 projection과 entitlement issuance를 함께 책임해 결합도와 장애 영향이 커진다.
- TrialClaim은 `FREE_EXAM_ONCE`의 phone dedupe aggregate이므로 이를 미리 생성하는 것만으로 coupon·출석·추천·유료 promotion 확장이 쉬워지지는 않는다. 프로모션 확장의 공통 지점은 catalog/offer definition과 generic EntitlementGrant·ledger·allocation resolver다.
- 현재 제품 계약과 최소 구현에는 lazy TrialClaim을 유지하는 안을 권장한다. verified 직후 사용자에게 권리를 표시해야 하는 명확한 제품 요구가 생기면 사전 Claim과 grant 발급을 함께 재설계하고 claimedAt 기산점·revoke/rejoin·미사용 권리 정책을 ADR에서 다시 승인해야 한다. 이 비교만으로 계약이나 코드를 변경하지 않았다.

## 2026-08-28 멘토 제안과 현재 무료 모의고사 모델 비교

- 멘토의 “무료 모의고사라는 이름으로 미리 생성”은 전역 catalog/benefit definition 하나를 미리 정의하고, 사용자별 grant는 필요할 때 발급해 기존 reserve·hold·confirm·ledger 흐름을 재사용하는 의미로 해석하는 것이 적절하다. 사용자별 grant나 실제 시험 Session을 모두 미리 만드는 방식은 미사용 document와 revoke·expiry 정합성 비용이 커져 권장하지 않는다.
- 현재 구현도 `grantType=FREE_EXAM_ONCE`, `sourceType=TRIAL_CLAIM`, `sourceId`, unit과 ledger/allocation을 사용하므로 공통 entitlement 모델의 기반은 있다. 다만 type·수량과 resolver가 코드에 고정돼 있고 catalog/campaign definition·policy version·expiry·priority는 아직 없다.
- 현재 최종 소비는 feedback 생성 시점이 아니라 Learning Core Session durable commit 직후 confirm이다. feedback·score·summary 최종 성공은 AttemptGroup COMPLETED, 최종 실패는 RETAKE_AVAILABLE로 구분하고 후자는 같은 consumption의 REPLACEMENT Session을 허용한다.
- catalog definition을 추가해도 실제 Learning Core ExamSession은 시험마다 필요하다. Billing의 AttemptSession은 문제·답안·채점 데이터를 저장하는 Session이 아니라 active/stale fencing과 재시도 정합성을 위한 최소 projection이라 제거하면 안 된다.
- 프로모션 확장성은 display name만 추가해서 얻을 수 없다. immutable `benefitCode`/`offerCode`, campaignId, grant type·unit, source, 유효기간, phone/user 제한, stacking·우선순위, policyVersion과 ledger dedupe 규칙이 필요하다. 이름은 변경 가능한 표시값으로만 사용한다.
- 권장 hybrid는 catalog/offer 정의만 사전 생성하고 사용자 Claim/grant는 최초 reserve 또는 승인된 지급 event에서 lazy issue하며, 무료·promotion·paid 모두 allocation과 reserve/confirm/cancel lifecycle을 재사용하는 방식이다. 이 방향은 후속 결제·프로모션 설계 권장안이며 아직 현재 무료 MVP 계약이나 구현을 변경하지 않았다.

## 2026-08-28 현재 무료 모의고사 소비 로직 재확인

- 현재 무료 모의고사는 paid credit 10개를 차감하지 않고 `FREE_EXAM_ONCE` grant의 1 unit을 `available → held → consumed`로 전이한다. paid credit의 시험당 10-credit 차감은 후속 결제 범위다.
- Identity eligibility event 수신만으로 Claim·grant를 만들지 않는다. 최초 INITIAL reserve Transaction에서 current VERIFIED binding과 candidate alias를 확인하고 기존 Claim이 없을 때만 TrialClaim·subject link·candidate alias, total 1 unit grant와 `GRANTED` ledger를 만든다.
- reserve는 grant를 `available 1→0`, `held 0→1`로 잠그고 HELD allocation, `RESERVED` ledger, 5분 `RESERVED` Reservation과 PROPOSED Session을 같은 Transaction에 만든다. 이는 최종 소비가 아니라 다른 동시 요청의 사용을 막는 hold다.
- Learning Core Session durable commit 뒤 confirm에서 Reservation CAS가 승리하면 allocation을 CONSUMED, grant를 `held 1→0`, `consumed 0→1`로 전환하고 `CONSUMED` ledger, AttemptGroup OPEN과 Session ACTIVE를 같은 Transaction에 반영한다. Summary 완료를 기다리지 않는다.
- confirm 전 cancel 또는 5분 expiry는 allocation을 RELEASED, grant를 `held 1→0`, `available 0→1`로 복원하고 `RELEASED` ledger와 Session FAILED를 기록한다. TrialClaim과 claimedAt은 삭제·갱신하지 않으므로 새 무료권을 만드는 것이 아니라 같은 1 unit을 다시 사용할 수 있게 한다.
- confirm 뒤에는 일반 cancel·expiry로 소비를 복원하지 않는다. 최종 결과 실패 시에도 consumed unit은 유지하고 후속 AttemptGroup event가 RETAKE_AVAILABLE을 만든 뒤 같은 consumption·AttemptGroup·mockExamId의 REPLACEMENT Session만 허용한다. 이 event consumer는 다음 작업으로 아직 미구현이다.
- 모든 지급·hold·소비·복원은 append-only ledger와 Mongo Transaction, unique index, version CAS, operation idempotency로 중복 지급·이중 소비를 방지한다.

## 2026-08-28 다음 작업: AttemptGroup 상태 event consumer

- 다음 권장 vertical slice는 Learning Core가 보내는 `POST /internal/v1/attempt-group-events`를 처리해 Billing의 AttemptGroup·AttemptSession projection을 수렴시키는 작업이다. PLAN 번호와 Jira는 아직 생성하지 않았다.
- `GRADING`은 결과 생성 중이라 replacement reserve를 잠시 차단하고, `COMPLETED`는 필수 feedback·valid score·summary가 모두 조회 가능할 때 group을 terminal로 닫는다. `RETAKE_AVAILABLE`은 최종 실패 때 무료권을 환불하거나 새로 지급하지 않고 기존 consumption·AttemptGroup·mockExamId로 새 Session 재응시를 허용한다.
- 구현 범위는 schema v1 strict decoder·canonical digest, shared `inbound_event_inbox` 멱등성, active Session fencing, AttemptGroup/Session version CAS, inbox와 projection의 단일 Mongo Transaction, Learning Core workload route와 replica-set 동시성·retry 검증이다.
- `COMPLETED`에는 evidence boolean 세 개와 `evidenceVersion=1`을 exact validation하고, `RETAKE_AVAILABLE`에는 승인된 low-cardinality `failureCode`만 허용한다. 질문·답안·점수·feedback·summary·AI/provider 원문은 Billing에 저장하지 않는다.
- 구현 계획에서 추가로 고정할 세부 계약은 sequence가 없는 event의 순서 역전 수렴 정책, 존재하지 않는 group/session·owner mismatch 응답과 재시도 정책, `failureCode` allowlist다. 권장 방향은 terminal evidence가 유효하면 OPEN에서 terminal로도 전진 수렴하고, COMPLETED는 절대 다시 열지 않으며, missing prerequisite는 retryable하게 처리하는 것이다.
- 이 작업에는 Learning Core publisher/outbox, 탈퇴·재가입 owner rebind, repair/reconciliation route, 실제 Lattice/IAM/SG 배포와 결제를 포함하지 않는다. 다음 순서는 AttemptGroup consumer → owner rebind → Learning Core saga/outbox·Lattice staging E2E다.

## 2026-08-28 Billing domain/global 패키지 구조 개편 완료

- Billing Java root `web.tosunsaeng.billing` 아래를 `domain`과 `global` 중심으로 재편했다. `domain`은 `eligibility/trial`, `entitlement`, `entitlement/trial`, `reservation`, `attempt`로 분리했고 테스트 package도 같은 구조로 이동했다.
- Reservation API·application·request/response DTO·domain entity·repository·config를 기능 내부로 정리했다. 기존 Reservation package에 섞여 있던 TrialClaim·candidate alias·subject link, grant·ledger, AttemptGroup·Session은 각각 entitlement/trial, entitlement, attempt로 이동했다.
- 공통 Security·Mongo 설정, internal error handler·response, Mongo initializer·transaction helper는 `global`로 이동했다. Reservation과 Trial eligibility의 오류 생성은 feature exception으로 분리하되 공통 handler와 기존 HTTP status·code·response envelope는 유지했다.
- `ReservationConverter`를 추가해 request→command 및 service result→response 변환을 Controller에서 분리했다. URL·JSON DTO 필드·canonical hash 입력과 순서·service 호출 동작은 변경하지 않았다.
- 최종 `./gradlew clean test`에서 총 82개 테스트가 성공했고 실패·오류·skip은 0개다. 이전 package namespace 참조와 빈 legacy package directory도 제거했으며 `git diff --check`를 통과했다.
- Mongo collection·index·document business field, Transaction·CAS·멱등성·Security 정책은 변경하지 않았다. 다만 Spring Data가 기록하는 `_class`가 Java package 이동에 따라 달라질 수 있다. Billing은 아직 미배포 상태이므로 최초 배포 기준으로 적용하되, 기존 환경에 보존할 데이터가 있다면 배포 전에 old `_class` 존재 여부와 migration 필요성을 확인해야 한다.
- 이번 구조 개편에서는 큰 `ReserveService`와 `ReservationLifecycleService` orchestration을 분해하지 않았다. 다음 구조 개선은 실제 후속 기능 경계가 생길 때 transaction 경계를 보존하면서 별도 계획으로 진행한다.

## 2026-08-28 Billing 패키지 구조 비교와 개편 초안

- Identity는 `web.tosunsaeng.identity.domain/{auth,user}/...`와 `global/...`, Learning Core는 `web.tosunsaeng.domain/{exams,withdrawal}/...`와 `global/...`로 기능 우선 패키지를 사용한다. 기능 내부는 필요에 따라 `api`, `application`, `dto`, `converter`, `domain/entity`, `domain/enums`, `repository`, `exception`, `config`로 나뉜다.
- Billing은 현재 최상단에 `config`, `global`, `reservation`, `trialeligibility`가 함께 있고, `reservation` 안에 Reservation뿐 아니라 TrialClaim·candidate alias·subject link·grant·ledger·AttemptGroup·AttemptSession까지 들어 있다. 구현 vertical slice 경계가 장기 domain 경계처럼 굳어진 상태다.
- 권장 목표는 `web.tosunsaeng.billing` 루트를 유지하면서 바로 아래를 `domain`과 `global`로 구분하는 구조다. domain은 `eligibility/trial`, `entitlement`, `entitlement/trial`, `reservation`, `attempt`로 나누고 각 기능 내부에서 api/application/dto/converter/domain/repository/exception/config를 필요한 만큼만 둔다.
- `ReservationProperties`와 `TrialEligibilityProperties`는 각 domain config로, Security·ingress와 공통 Mongo 설정은 global config/infrastructure로 이동한다. 현재 `global/api`의 공통 error envelope·handler는 global response/exception으로 명확히 이름을 바꾼다.
- 우선 단계는 package·import·test package만 옮기고 HTTP DTO, Mongo collection/index, Spring bean, transaction과 동작을 바꾸지 않는 구조 이동이다. 다음 단계에서 feature exception과 converter를 분리하고, 마지막에 큰 orchestration service의 내부 협력 컴포넌트를 분해한다.
- 이번 작업은 구조 분석과 초안 제시만 수행했다. 애플리케이션·테스트 패키지는 이동하지 않았고 Jira도 생성·변경하지 않았다.

## 2026-08-28 TMI-113 완료 처리

- 사용자의 명시적 승인에 따라 Jira `TMI-113`을 `해야 할 일`에서 `완료`로 전환했고 완료 category를 재확인했다.
- 완료 근거는 PLAN-003 Reservation lifecycle 구현과 최종 `./gradlew clean test` 총 82개 성공, 실패·오류·skip 0 결과다.
- Jira 설명·담당자, 애플리케이션 코드, Git 브랜치와 production 활성화 gate는 변경하지 않았다.
- 다음 기능 순서는 AttemptGroup 상태 event consumer이며 별도 계획·Jira 승인 후 진행한다. Learning Core saga·Lattice staging E2E 전에는 production caller를 활성화하지 않는다.

## 2026-08-28 TMI-113 PLAN-003 Reservation lifecycle 구현 완료

- `POST /internal/v1/reservations/{reservationId}/confirm`, `cancel`, `POST /internal/v1/reservations/status`를 구현했다. confirm/cancel은 16 KiB strict JSON, lowercase UUID v4 path·header·body, exact enum·UTC millisecond timestamp와 canonical SHA-256 command hash를 사용한다.
- INITIAL confirm은 HELD allocation과 grant를 CONSUMED로 전이하고 `CONSUMED:<reservationId>` ledger, AttemptGroup OPEN과 Session ACTIVE를 하나의 Mongo Transaction으로 반영한다. REPLACEMENT confirm은 기존 consumption을 유지하고 group Session만 교체한다.
- INITIAL cancel·expiry는 allocation을 RELEASED, grant held를 available로 복원하고 `RELEASED:<reservationId>` ledger를 하나만 남긴다. REPLACEMENT cancel·expiry는 entitlement를 변경하지 않는다. TrialClaim·alias·subject retention은 lifecycle에서 건드리지 않는다.
- confirm·cancel·expiry는 `RESERVED + activeGuard + version` Reservation CAS를 첫 write로 사용한다. CONFIRMED 취소와 CANCELED/EXPIRED 일반 confirm은 409이며 repair route는 추가하지 않았다.
- terminal RESERVE·CONFIRM·CANCEL command는 active guard를 해제하고 `terminalAt + 7일` purgeAt을 기록한다. Reservation·ledger audit에는 TTL을 추가하지 않았다. status는 RESERVE operation과 live Reservation·group을 읽기만 하며 missing operation은 404다.
- expiry worker는 기본 disabled, scan 10초·batch 100 기본값이며 due Reservation별 독립 Transaction과 다중 ECS task CAS 수렴을 사용한다. due batch size와 oldest due lag를 식별자 없는 metric으로 기록한다.
- schema v2와 기존 index를 그대로 재사용했다. 새 collection, index, 결제·AttemptGroup 상태 event·owner rebind·repair·타 서비스 코드는 추가하지 않았다.
- `./gradlew clean test`에서 총 82개 테스트가 성공했고 실패·오류·skip은 0개다. replica-set Testcontainers가 INITIAL/REPLACEMENT, concurrent same-command replay, confirm/cancel/expiry 모든 race, multi-worker, transient retry와 unknown commit을 실제 검증했다.
- Jira `TMI-113`은 사용자 승인 없이 상태를 변경하지 않아 `해야 할 일`로 유지한다. production caller는 Learning Core saga, expiry 운영 활성화, Lattice/IAM/SG와 staging E2E 전까지 열지 않는다.

## 2026-08-28 PLAN-003 Jira 작업 생성

- TMI 프로젝트에 `TMI-113` — `[Billing] Reservation lifecycle 구현`을 `작업` 유형으로 생성했다. 상태는 `해야 할 일`, 담당자는 미지정이다.
- Jira 본문에 confirm·cancel·status, 5분 expiry worker, INITIAL consume/release, REPLACEMENT 무추가차감, terminal command 7일 보존과 confirm/cancel/expiry CAS race 완료 조건을 반영했다.
- AttemptGroup 상태 event, 탈퇴·재가입 owner rebind, repair route, Learning Core saga, Identity 변경, 실제 Lattice/AWS 배포, TrialClaim purge와 결제는 제외 범위로 명시했다.
- PLAN-003 상태를 사용자 승인·Jira 생성·구현 전으로 갱신했다. 애플리케이션 구현과 AGENTS 현재 구현 단위 전환은 아직 시작하지 않았다.
- production Reservation caller는 PLAN-003만으로 활성화하지 않고 후속 상태 event·서비스 연동·AWS staging E2E gate를 유지한다.

## 2026-08-28 PLAN-005 이후 남은 출시 작업 구분

- PLAN-005 재가입 owner rebind까지 완료하면 phone당 무료 1회, reserve lifecycle, 결과 실패 재응시와 탈퇴·재가입 승계를 포함한 Billing 핵심 제품 정책은 완성된다.
- 그러나 실제 출시 전에는 Learning Core의 Billing client·시험 생성 saga·status reconciliation과 AttemptGroup outbox, Identity 실제 SigV4 eligibility delivery/owner transfer 계약, VPC Lattice·IAM·SG·ECS 설정이 남는다.
- staging에서 same-key retry, Session commit 실패, confirm 응답 유실, expiry/confirm race, 최종 결과 실패·재가입과 unsigned/wrong-role/direct-bypass E2E를 통과해야 한다.
- production Mongo index/migration preflight, expiry worker enable, metric·alert, backup/rollback runbook과 rollout도 필요하다.
- TrialClaim 3년 만료 daily purge·24시간 SLA와 35일 backup restore purge는 별도 운영 vertical slice로 남는다. 최초 Claim 만료 전에는 반드시 구현·검증돼야 한다.
- 따라서 PLAN-005는 핵심 Billing 기능의 끝이지 전체 production 출시의 끝은 아니다. 이번 설명에서는 PLAN 번호나 계약을 새로 확정하지 않았다.

## 2026-08-28 PLAN-003 이후 재가입 권리 승계 구현 순서

- PLAN-003 Reservation confirm/cancel/status·expiry lifecycle을 먼저 구현하는 순서가 맞다. owner rebind가 소비·복원·terminal 상태를 안전하게 판정하려면 lifecycle 상태 머신과 CAS가 선행돼야 한다.
- 결과 실패 후 재가입 재응시까지 완성하려면 PLAN-003 다음에 AttemptGroup 상태 event consumer를 구현해 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE`을 신뢰 가능한 상태로 만든다.
- 그 다음 별도 vertical slice로 old eligibility REVOKED + new VERIFIED + same candidate에 따른 retained subject owner rebind를 구현한다. 새 Claim·grant는 만들지 않고 기존 미사용 unit 또는 same consumption만 새 userId가 사용한다.
- 권장 순서는 `PLAN-003 lifecycle → AttemptGroup event → 재가입 owner rebind → Learning Core saga/Lattice E2E`다.
- PLAN-003 계획은 아직 초안이고 Jira도 미생성이다. 이번 확인에서는 구현·Jira·계약·PLAN을 변경하지 않았다.

## 2026-08-28 Identity phone uniqueness와 Billing owner transfer 전제 정정

- Identity는 verified phone 하나를 동시에 한 Firebase User와 한 ACTIVE MEMBER에만 연결한다. 다른 활성 owner가 점유하면 가입·Guest 승격은 `PHONE_ALREADY_LINKED`로 실패하므로 정상 흐름에서 같은 번호의 활성 계정 두 개는 존재하지 않는다.
- 탈퇴 lifecycle이 기존 PhoneIdentity/active alias와 Firebase phone 점유를 해제한 뒤에만 같은 번호로 새 UUID 가입이 가능하다.
- 재가입 시 Billing에 남은 old `billing_subject_link`는 활성 Identity 계정이 아니라 Claim 3년 dedupe를 위한 과거 owner mapping이다. owner transfer는 두 활성 계정 사이의 이전이 아니라 이 retained mapping을 새 verified userId에 재연결하는 작업이다.
- 이전 권장안의 “기존 활성 계정이면 자동 이전 금지”는 정상 제품 분기가 아니라 Identity uniqueness drift, revoke/verified event 순서와 부분 완료를 막는 fail-closed 방어 조건으로 정정한다.
- Billing transfer의 실질 조건은 old user eligibility `REVOKED`, new user eligibility `VERIFIED`, 같은 retained candidate와 active Claim 일치다. event 순서가 아직 수렴하지 않았으면 이전하지 않고 processing/reconciliation으로 보낸다.
- 이 설명은 아직 owner-transfer 계약 승인이나 PLAN 변경을 의미하지 않는다.

## 2026-08-28 재가입 사용자의 기존 무료시험 권리 승계 공백 확인

- 현행 owner mismatch 차단은 탈퇴 후 새 UUID로 재가입한 사용자가 같은 verified phone candidate의 기존 Claim을 이어받지 못하게 한다. 중복 무료 지급은 막지만 미사용·재응시 가능 권리까지 영구 차단하는 제품 공백이 있다.
- phone당 1회 정책에 맞는 해결은 새 TrialClaim/grant를 발급하는 것이 아니라 기존 Claim·grant·consumption을 유지한 채 `billing_subject_link`의 owner를 검증된 새 userId로 이전하는 것이다.
- 권장 transfer 조건은 새 사용자의 current VERIFIED candidate가 기존 active Claim alias와 일치하고, 기존 owner가 Identity에서 WITHDRAWN/이전 가능 상태임이 인증된 event로 증명되며, active RESERVED/GRADING race가 없는 경우다. current owner가 활성 상태면 자동 이전하지 않는다.
- 미사용 또는 cancel/expiry로 복원된 grant는 같은 Claim의 available unit을 새 owner가 INITIAL reserve한다. OPEN·RETAKE_AVAILABLE은 같은 AttemptGroup·consumption·mockExamId로 새 Session만 시작한다. GRADING은 최종 수렴 전 차단하고 COMPLETED는 추가 무료 응시를 허용하지 않는다.
- 이전 Session·답안·upload·결과·Summary는 새 계정에 승계하거나 노출하지 않는다. old Session을 fencing하고 새 Session에서 처음부터 시작한다.
- Claim ID, `claimedAt`, `retentionExpiresAt`, grant와 consumption ledger는 변경하지 않아 phone당 1회를 유지한다. owner transfer 자체는 멱등 event·Transaction과 비식별 audit ledger가 필요하다.
- 이 방향은 권장안이며 아직 계약을 변경하거나 PLAN-003에 포함하지 않았다. 승인 시 별도 owner-transfer 계약과 구현 계획을 작성하고 production gate에 포함해야 한다.

## 2026-08-28 탈퇴·재가입 시 미완료 AttemptGroup 재응시 확인

- Identity의 현재 재가입 계약은 탈퇴한 계정을 복구하지 않고 새 canonical UUID `userId`를 발급한다.
- Billing은 TrialClaim과 candidate dedupe를 3년 유지하므로 탈퇴·재가입으로 새 무료권을 지급하지 않는다. 기존 Claim의 active subject link는 old userId를 가리킨다.
- 새 userId가 같은 전화 candidate로 reserve하면 기존 Claim을 찾지만 owner mismatch가 발생해 현재 구현은 `ENTITLEMENT_INSUFFICIENT`로 차단한다. 기존 OPEN·RETAKE_AVAILABLE AttemptGroup도 자동 이전하지 않는다.
- 같은 userId라면 OPEN·RETAKE_AVAILABLE은 REPLACEMENT 재응시가 가능하고 GRADING은 최종 처리 전이므로 `COMMAND_PROCESSING`으로 차단한다. 실제 재가입은 새 UUID이므로 이 예외에 해당하지 않는다.
- 재가입 사용자에게 기존 consumption 재응시를 허용하려면 phone candidate 일치만으로 이전하지 않고 Identity가 인증한 owner-transfer/rejoin event와 Billing의 원자적 subject link·AttemptGroup ownership 이전 계약이 필요하다. 현재 `UserMerged`/owner transfer wire 계약은 미확정·미구현이다.
- 이번 확인으로 계약·코드·PLAN을 변경하지 않았다. 현재 권장 동작은 자동 이전 차단이다.

## 2026-08-28 같은 consumption 재응시 처리 설명

- 최초 INITIAL confirm에서 무료 unit을 한 번 `CONSUMED`하고 그 ledger event를 AttemptGroup의 `consumptionLedgerEventId`로 연결하는 것이 목표 계약이다.
- 결과 생성 중에는 AttemptGroup이 `GRADING`이며 새 reserve를 `COMMAND_PROCESSING`으로 막는다. 최종 실패가 확정되면 후속 AttemptGroup event consumer가 group을 `RETAKE_AVAILABLE`로 전환한다.
- 재응시는 새 operationId·새 sessionId로 reserve하지만 동일 AttemptGroup ID와 고정 `mockExamId`를 사용한다. Billing은 이를 `REPLACEMENT`로 판정한다.
- REPLACEMENT는 새 TrialClaim, grant, allocation, `GRANTED`·`RESERVED`·`CONSUMED` ledger를 만들지 않는다. 기존 consumption을 유지한 채 새 Reservation·PROPOSED Session만 만들고 confirm에서 Session pointer를 교체한다.
- PLAN-002 코드에는 OPEN·RETAKE_AVAILABLE group의 REPLACEMENT 판정과 무allocation·무ledger reserve가 구현돼 있다. PLAN-003 confirm은 아직 미구현이며 결과 실패를 RETAKE_AVAILABLE로 만드는 AttemptGroup event consumer도 PLAN-003 이후 후속 작업이다.
- 따라서 계약과 일부 reserve 기반은 준비됐지만 결과 실패부터 재응시까지의 end-to-end 흐름은 아직 구현 완료 상태가 아니다.

## 2026-08-28 무료권 소비 확정과 Summary 완료 시점 재확인

- `reserve`는 무료 unit을 `HELD`로 잠가 다른 시험 생성 요청이 동시에 사용하지 못하게 하는 임시 상태다.
- 현재 확정 계약에서 무료권의 최종 소비는 Summary 생성 때가 아니라 Learning Core ExamSession이 durable commit된 직후 `confirm`에서 일어난다.
- confirm은 allocation을 `CONSUMED`로 바꾸고 AttemptGroup을 `OPEN`으로 연다. 이는 시험 1회가 시작됐다는 entitlement 확정이며 시험 결과가 완성됐다는 뜻은 아니다.
- 필수 피드백·유효 점수·Summary가 사용자에게 조회 가능해지면 별도 AttemptGroup 상태 event로 `COMPLETED` 처리한다.
- 결과 생성이 최종 실패하면 무료 Claim/grant를 새로 지급하거나 consumption을 취소하지 않고 `RETAKE_AVAILABLE`로 전환해 같은 consumption·mockExamId로 새 Session을 허용한다.
- 이번 설명으로 계약이나 PLAN-003을 변경하지 않았다. 소비 확정을 Summary 완료까지 미루려면 기존 ADR·통합 계약·PLAN과 실패·동시성 정책을 함께 변경하는 별도 결정이 필요하다.

## 2026-08-28 PLAN-003 Reservation lifecycle 계획서 작성

- `docs/plans/PLAN-003-reservation-lifecycle.md` 초안을 작성했으며 신규 Jira는 아직 생성하지 않았다.
- 범위는 confirm, cancel, read-only status와 5분 expiry worker다. 네 경로가 같은 Reservation terminal 상태를 공유하므로 하나의 vertical slice로 묶었다.
- INITIAL confirm은 allocation·grant consume, `CONSUMED` ledger, AttemptGroup `OPEN`, Session `ACTIVE`를 한 Transaction으로 처리한다. REPLACEMENT confirm은 기존 consumption을 유지한다.
- INITIAL cancel/expiry는 allocation을 원 grant로 정확히 복원하고 하나의 `RELEASED` ledger를 남긴다. REPLACEMENT는 기존 consumption을 변경하지 않으며 모든 경로에서 TrialClaim은 유지한다.
- confirm/cancel/expiry는 Reservation expected-state/version CAS를 첫 write로 사용해 경쟁 시 terminal 상태 하나만 commit한다. status는 command·ledger를 만들지 않는 조회로 고정했다.
- terminal command 7일 보존, expiry scan interval 10초·batch 100의 configurable 최초 제안, existing Mongo schema v2 index 재사용과 production caller gate를 계획에 포함했다.
- Learning Core saga, AttemptGroup 상태 event, repair, 실제 Lattice/AWS 배포와 결제는 제외했다. 계획 승인 뒤 별도 사용자 승인으로 Jira를 생성한다.

## 2026-08-28 다음 작업: Reservation lifecycle

- 다음 권장 vertical slice는 `confirm`, `cancel`, `status`와 5분 expiry worker를 함께 구현하는 PLAN-003이다.
- PLAN-002의 `reserve`는 무료 시험 unit을 `RESERVED`로 잠근 단계까지만 구현했다. 다음 작업은 Learning Core Session 저장 성공 시 `CONFIRMED`로 최종 소비하고, 저장 실패 시 `CANCELED`, 호출 없이 5분이 지나면 `EXPIRED`로 잠금을 해제한다.
- `status`는 confirm 응답 유실이나 timeout 때 새 command·ledger를 만들지 않고 기존 Reservation의 실제 결과를 조회하기 위한 read-only API다.
- INITIAL confirm은 allocation과 grant를 `held → consumed`로 전환하고 `CONSUMED` ledger, AttemptGroup `OPEN`, Session `ACTIVE`를 하나의 Transaction으로 반영한다. REPLACEMENT confirm은 기존 consumption을 재사용하고 추가 차감하지 않는다.
- INITIAL cancel/expiry는 원래 grant에 allocation을 정확히 복원하고 `RELEASED` ledger를 남긴다. TrialClaim과 `claimedAt`은 유지한다. REPLACEMENT cancel/expiry는 기존 consumption을 변경하지 않는다.
- confirm·cancel·expiry 경쟁은 Reservation 상태 CAS와 Mongo Transaction으로 한 terminal 상태만 승리하게 해야 한다. `CONFIRMED` 일반 cancel과 CANCELED/EXPIRED 자동 repair-confirm은 금지한다.
- 이 lifecycle이 완성되기 전에는 production Learning Core가 reserve route를 호출하도록 활성화하지 않는다. 이후 작업은 AttemptGroup 상태 event·reconciliation, Learning Core saga, 실제 Lattice staging E2E 순서다.
- 이번 작업은 다음 범위를 설명한 분석이며 PLAN-003, Jira, 애플리케이션 코드는 아직 생성하거나 변경하지 않았다.

## 2026-08-28 TMI-112 완료 처리

- 사용자의 명시적 승인에 따라 Jira `TMI-112`를 `해야 할 일`에서 `완료`로 전환했고 완료 category를 재확인했다.
- Jira 설명·담당자, 애플리케이션 코드, PLAN-002 API·Mongo·보안 계약과 Git branch는 변경하지 않았다.
- 완료 근거는 직전 PLAN-002 구현과 `./gradlew clean test` 총 58개 성공, 실패·skip 0 결과다.
- 실제 production 활성화를 의미하지 않는다. confirm/cancel/status·expiry, Learning Core saga와 Lattice staging gate는 후속 작업으로 유지한다.

## 2026-08-28 TMI-112 PLAN-002 initial reserve 구현 완료

- `POST /internal/v1/reservations`에 16 KiB strict JSON, 필수 lowercase UUID v4 `Idempotency-Key`, canonical SHA-256 payload hash와 직접 200 response DTO를 구현했다.
- 첫 reserve의 단일 Mongo Transaction에서 current VERIFIED eligibility, expired alias fencing, candidate/key rotation dedupe, 필요한 TrialClaim·subject link·전체 aliases·`FREE_EXAM_ONCE` grant와 `GRANTED`, INITIAL allocation hold·`RESERVED`, 5분 Reservation·PROPOSED Session과 command response snapshot을 함께 반영한다.
- 같은 operation/payload는 동일 Reservation을 replay하고 다른 payload는 409다. 다른 owner는 402로 차단하고, 같은 candidate·같은 user 동시 요청은 unique/partial unique index와 retry 후 Claim·active command·Reservation·Session 하나로 수렴한다.
- OPEN·RETAKE_AVAILABLE group은 REPLACEMENT로 기존 consumption·mockExamId를 재사용하며 새 allocation과 entitlement ledger가 없다. GRADING은 processing, mockExamId 불일치는 state conflict다.
- Mongo initializer를 schema v2로 확장해 reserve 관련 10개 collection과 ADR-001의 23개 index를 생성·option 검증한다. runtime drop/recreate와 Reservation·Claim audit TTL 삭제는 추가하지 않았다.
- test security에서 Learning Core role은 reserve route만, Identity role은 eligibility route만 허용하고 default disabled는 계속 deny한다. 실제 Lattice auth policy·SG 배포는 범위 밖이다.
- `./gradlew clean test`에서 총 58개 테스트가 통과했고 실패·skip은 0개다. replica-set Testcontainers가 initial atomicity·rollback·동시성·key rotation·expired alias·REPLACEMENT·retry·unknown commit을 실제 실행했다.
- Jira `TMI-112` 상태는 사용자 승인 없이 변경하지 않아 `해야 할 일`로 유지한다. confirm/cancel/status·expiry lifecycle과 Learning Core saga·실제 staging E2E 전에는 production reserve caller를 활성화하지 않는다.

## 2026-08-28 PLAN-002 Jira 작업 생성

- TMI 프로젝트에 `TMI-112` — `[Billing] Free exam initial reserve 구현`을 `작업` 유형으로 생성했다. 상태는 `해야 할 일`, 담당자는 미지정이다.
- PLAN-002의 reserve endpoint, INITIAL·REPLACEMENT 판정, Claim·무료 grant/ledger·allocation·Reservation 단일 Transaction, 멱등성·index·동시성·보안·개인정보 완료 조건을 Jira 본문에 반영했다.
- confirm/cancel/status·expiry, AttemptGroup event·reconciliation, 타 서비스 adapter, 실제 AWS 배포와 결제는 제외 범위로 명시했다.
- Billing lifecycle과 Learning Core saga·실제 Lattice staging 검증 전에는 production reserve caller를 활성화하지 않는 제한을 유지한다.

## 2026-08-28 reserve response 식별자·상태 의미 설명

- `operationId`는 앱이 만든 한 번의 시험 생성 명령 ID이며 Learning Core와 Billing이 같은 `Idempotency-Key`로 사용한다. transport retry에는 유지하고 의도적인 restart에는 새 값을 사용한다.
- `reservationId`는 Billing이 만든 한 번의 entitlement hold 기록 ID다. 후속 confirm/cancel이 이 Reservation을 대상으로 하며 audit 상태 전이를 보존한다.
- `reservationKind`는 새 소비를 준비하는 `INITIAL`인지 기존 consumption을 재사용하는 `REPLACEMENT`인지 Billing이 판정한 값이다.
- `reservationStatus=RESERVED`는 unit을 잠갔지만 아직 최종 소비하지 않은 상태다. Session durable commit 뒤 confirm되면 `CONFIRMED`, commit 실패·만료 시 `CANCELED` 또는 `EXPIRED`로 전이한다.
- `attemptGroupId`는 최초 응시와 허용된 restart를 하나의 consumption으로 묶는 Billing ID다. INITIAL reserve에서 미리 발급하지만 group `OPEN`은 confirm에서 확정한다.

## 2026-08-28 reserve의 sessionId·mockExamId 역할 설명

- `sessionId`는 Learning Core가 만들 예정인 한 번의 ExamSession 식별자다. reserve 시 proposed 값으로 먼저 고정해 응답 유실 retry가 같은 Session으로 수렴하고, 후속 confirm이 실제 durable commit된 바로 그 Session에 대한 것인지 확인한다.
- `mockExamId`는 AttemptGroup에서 사용할 문제지 식별자다. 최초 reserve에서 group에 고정하고 restart·REPLACEMENT가 같은 문제지를 유지하는지 확인해 다른 시험으로 entitlement를 재사용하는 것을 막는다.
- Billing은 시험 내용이나 Session을 생성하지 않으며 두 값은 1~128자 opaque token으로만 저장·비교한다. `sessionId`는 한 Session마다 바뀌고 `mockExamId`는 같은 AttemptGroup 동안 유지된다.

## 2026-08-28 PLAN-002 initial reserve 계획서 작성

- `docs/plans/PLAN-002-free-exam-initial-reserve.md` 초안을 작성했다. 신규 Jira는 아직 생성하지 않았다.
- 범위는 `POST /internal/v1/reservations`의 전체 reserve 판정으로 고정했다. 첫 INITIAL reserve의 단일 Mongo Transaction에서 command 멱등성, current VERIFIED binding, 만료 alias fencing과 dedupe, 필요한 TrialClaim·subject link·무료 grant/ledger, allocation hold, Reservation과 proposed Session projection을 함께 처리한다.
- 기존 `OPEN`·`RETAKE_AVAILABLE` group은 REPLACEMENT로 기존 consumption을 재사용하고 `GRADING`은 processing conflict, 다른 `mockExamId`는 state conflict로 처리한다.
- confirm/cancel/status, expiry worker, AttemptGroup event·reconciliation, Learning Core·Identity adapter와 실제 Lattice 배포는 제외했다. 해당 lifecycle이 완성되기 전 production caller activation은 금지한다.
- 계획 승인 뒤 별도 Jira를 생성하고 `AGENTS.md`의 현재 구현 단위를 PLAN-002로 전환한 다음 구현하는 순서를 권장한다.

## 2026-08-28 다음 구현 단위 확인

- PLAN-001 다음 권장 작업은 current `trial_eligibility`를 사용하는 `FREE_EXAM_ONCE` INITIAL reserve vertical slice다.
- eligibility event 수신과 TrialClaim 지급을 계속 분리한다. 첫 reserve의 단일 Mongo Transaction 안에서 idempotency command, current VERIFIED binding, candidate alias dedupe, 필요한 TrialClaim·subject link·grant·`GRANTED` ledger, allocation hold·`RESERVED` ledger와 Reservation을 함께 만든다.
- TrialClaim이나 grant만 미리 생성하는 별도 작업은 승인 계약을 위반하므로 진행하지 않는다.
- 다음 구현 전 `PLAN-002`와 별도 Jira로 reserve 범위·index·동시성 완료 조건을 고정하고 `AGENTS.md`의 현재 구현 단위를 갱신해야 한다.
- 그 이후 순서는 confirm/cancel/status·5분 expiry, AttemptGroup event·reconciliation, Learning Core saga와 Identity/Lattice staging 연동이다.

## 2026-08-27 TMI-110 완료 처리

- PLAN-001 구현과 33개 전체 테스트 통과를 근거로 Jira `TMI-110`을 `해야 할 일`에서 `완료`로 전환했다.
- Jira 설명·담당자·애플리케이션 코드·Git 브랜치는 변경하지 않았다.

## 2026-08-27 로컬 main·develop 브랜치 생성

- 현재 feature 브랜치의 커밋 `e0694f9`를 기준으로 로컬 `main`과 `develop` 브랜치를 생성했다.
- 현재 checkout은 `feat/TMI-110-trial-eligibility-event-consumer`에 그대로 유지했다.
- 이후 원격에도 `main`과 `develop`이 생성된 것을 확인했고 GitHub 기본 브랜치를 feature 브랜치에서 `main`으로 변경했다.

## 2026-08-27 PLAN-001 Trial eligibility consumer 구현 완료

- `POST /internal/v1/eligibility/trial/events`에 16 KiB bounded 수신, strict JSON decode, schema v1·expected scope 검증과 canonical SHA-256 digest를 구현했다.
- `inbound_event_inbox`와 `trial_eligibility`를 단일 Mongo Transaction으로 반영하며 APPLIED·DUPLICATE·STALE과 `EVENT_ID_CONFLICT`로 수렴한다. verified는 candidate 전체를 교체하고 revoked는 candidate를 제거한 revision tombstone을 유지한다.
- `ux_inbox_event_id`, `ux_inbox_identity_scope_user_revision`, `ttl_inbox_purge_at`, `ux_trial_scope_user`, `ix_trial_key_version`을 versioned initializer가 생성·비교하고 option 불일치 시 fail-fast한다. `auto-index-creation`은 false다.
- internal ingress는 기본 disabled, test Identity role, 필수 설정을 검증하는 Lattice AWS_IAM mode로 분리했다. 실제 AWS principal 검증은 ADR-002대로 Lattice policy와 SG가 담당하며 Lattice 실배포는 이번 범위가 아니다.
- Identity fixture 기반 decoder, MVC·security, transaction retry·unknown commit 재확인과 `mongo:7.0.14` replica-set index·rollback·동시성 테스트를 추가했다.
- `./gradlew clean test`에서 33개 테스트가 통과했다. TrialClaim, grant·ledger, Reservation·AttemptGroup, Identity SigV4 adapter와 실제 Lattice/IAM/SG는 추가하지 않았다.
- Jira `TMI-110`의 상태·담당자는 별도 수정 승인 없이 변경하지 않았다. 저장소 전체가 아직 Git 미추적 상태이므로 사용자가 기준선과 이번 구현 diff를 검토해 commit해야 한다.

## 2026-08-27 PLAN-001 Jira 작업 생성

- Jira `TMI-110`을 생성해 `docs/plans/PLAN-001-trial-eligibility-event-consumer.md`의 구현 범위와 완료 조건을 작업 단위로 고정했다.
- 포함 범위는 `/internal/v1/eligibility/trial/events`, 16 KiB 제한, strict decode, canonical digest, `inbound_event_inbox`, `trial_eligibility`, 단일 Mongo Transaction, 명시적 index 검증과 replica-set Testcontainers 테스트다.
- TrialClaim, grant·ledger, Reservation·AttemptGroup, Identity SigV4 adapter, Learning Core saga와 실제 Lattice 인프라는 이 이슈에서 제외하고 후속 vertical slice로 유지한다.
- 이슈 상태는 `해야 할 일`, 담당자는 미지정이다. 애플리케이션 코드와 계약 문서는 변경하지 않았고 테스트도 실행하지 않았다.

## 2026-08-27 통합 계약 보정 재검증

- ADR-001 inbox의 `consumerScopeId`, APPLIED·STALE disposition, `ux_inbox_identity_scope_user_revision`이 PLAN-001·AGENTS와 일치하도록 보정된 것을 확인했다.
- eligibility 409는 `EVENT_ID_CONFLICT` 전용, `COMMAND_PROCESSING`은 Reservation 전용으로 ADR·통합 계약이 정렬됐다.
- 공개 `POST /api/v1/exams`의 필수 lowercase UUID v4 `Idempotency-Key`, body 없음·기존 성공 DTO 유지, transport retry와 의도적 restart의 key/Session 구분이 AGENTS·통합 계약·Learning Core 계약 문서에 반영됐다.
- Identity ADR은 목표 transport를 VPC Lattice AWS_IAM·ECS task role·SigV4와 새 eligibility route로 바꾸고, 429·503 `Retry-After` 처리와 현재 Bearer adapter의 미이관 상태를 명시했다.
- 검토한 현행 계약 문서에서 구 eligibility route, optional 시험 생성 key, inbox disposition/index 충돌과 새 whitespace 오류는 확인되지 않았다.
- 문서 계약 보정과 실제 구현 완료는 다르다. Identity 실제 adapter는 아직 Bearer이고 `Retry-After`를 읽지 않으며, Learning Core controller는 필수 header와 Billing saga를 아직 구현하지 않았고 Billing도 PLAN-001 구현 전이다. 이는 문서에 명시된 후속 구현 항목이다.
- 아래의 이전 정합성 리뷰 섹션은 당시 발견 기록이며, 현재 판단은 이 재검증 섹션과 상단 `통합 계약 불일치 보정` 섹션이 대체한다. WORKLOG의 과거 기록은 수정하지 않는다.

## 2026-08-27 AGENTS.md 최신 계약 반영

- 저장소 루트 `AGENTS.md`를 다른 앱 서버와 같은 수준의 작업 규칙으로 보강했다.
- 명시적 요청 없이 Billing 저장소만 변경하고 Identity·Learning Core는 계약 확인용 읽기 대상으로만 사용하도록 저장소 경계를 명확히 했다.
- 현재 최소 Entitlement 구현 범위와 Apple/Google 결제·paid credit·pass·coupon·환불·사용자 Billing API의 후속 범위를 분리했다.
- TrialClaim 계약을 `claimedAt + 3년` 보존, 기간 내 재수급 차단, 만료 후 candidate 연결 purge와 재수급 허용으로 최신화했다.
- `/internal/v1/eligibility/trial/events`, strict decode·canonical digest, inbox·revision·projection Transaction과 event 수신만으로 grant하지 않는 규칙을 추가했다.
- workload 인증은 VPC Lattice `AWS_IAM` + ECS task role + SigV4로 고정하고 Identity·Learning Core·repair role의 route 권한 분리와 local/test adapter 원칙을 반영했다.
- Mongo unique index·Transaction·Reservation expiry, 보안·멱등성·범위 이탈을 중심으로 한 코드 리뷰 우선순위를 추가했다.
- Jira 키는 없고 애플리케이션 코드·ADR·PLAN·외부 API와 배포 설정은 변경하지 않았다.

## 2026-08-27 AGENTS.md Billing 전용 정교화

- `AGENTS.md`에서 무료 MVP 전체 제품 범위와 현재 즉시 구현할 PLAN-001 Trial eligibility consumer 범위를 분리했다.
- PLAN-001에는 `inbound_event_inbox`, `trial_eligibility`, strict decode·canonical digest, APPLIED·DUPLICATE·STALE·CONFLICT와 단일 Mongo Transaction만 포함하고 TrialClaim·grant·Reservation은 후속임을 명시했다.
- internal API는 앱용 `BaseResponse`를 사용하지 않고 16 KiB 제한, body 없는 204와 승인된 stable error envelope를 사용하도록 반영했다.
- public client userId 비신뢰 원칙을 유지하면서 인증된 Identity event와 Learning Core internal route가 canonical UUID userId를 body로 전달하는 서비스 계약 예외를 명시했다.
- Reservation의 `Idempotency-Key=operationId`, opaque sessionId/mockExamId, POST status와 repair-confirm 기본 차단을 후속 구현 규칙으로 추가했다.
- 승인된 collection/index 이름, explicit versioned index initializer, inbox 120일 TTL·TrialClaim 3년·Reservation audit 보존의 분리를 Mongo 규칙과 리뷰 항목에 반영했다.
- Jira 키는 없다. 애플리케이션 코드·ADR·PLAN·외부 API·인프라 설정은 변경하지 않았다.

## 2026-08-27 서비스 간 통합 계약서 추가

- `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`를 추가해 앱·Identity·Learning Core·Billing의 책임과 호출 방향을 한 문서에서 확인할 수 있게 했다.
- Identity→Billing의 Trial eligibility event route, SigV4 principal, schema·멱등성·revision 처리, 응답과 at-least-once retry를 정리했다.
- Learning Core→Billing의 reserve→Session commit→confirm, cancel·status, confirm 응답 유실 reconciliation과 AttemptGroup 상태 event를 정리했다.
- VPC Lattice AWS_IAM·환경 격리, 공통 HTTP·식별자·오류·개인정보 경계, TrialClaim 3년 보존, local/test와 배포·변경 순서 및 E2E 체크리스트를 포함했다.
- 이 문서는 상위 흐름 안내서이며 세부 충돌 시 ADR-001·ADR-002가 최종 기준임을 명시하고 `AGENTS.md` 문서 경로에도 연결했다.
- Jira 키는 없다. 애플리케이션 코드·기존 ADR·PLAN·외부 API·AWS 리소스는 변경하지 않았다.

## 2026-08-27 통합 계약 외부 검토 지적 확인

- 첨부 검토의 4건을 Billing ADR·PLAN·AGENTS, Identity publisher 코드와 Learning Core 시험 생성 코드에 대조했다.
- ADR-001 `inbound_event_inbox`에 `consumerScopeId`와 `(producer, scope, user, revision)` unique index가 없고 disposition이 PLAN의 APPLIED·STALE 저장 모델과 다른 지적은 정확하다. PLAN-001 Phase 0에 이미 보정 작업으로 명시돼 있으며 Step 1 전에 ADR을 고쳐야 한다.
- Billing 목표는 Lattice AWS_IAM·task role·SigV4지만 Identity 실제 adapter는 audience 기반 짧은 workload Bearer credential을 사용한다. 이는 알려진 구현 공백이며 Billing local PLAN-001은 진행 가능하지만 staging publisher 활성화 전 Identity adapter·ADR·contract test를 SigV4와 새 route에 맞춰야 한다. publisher는 기본 disabled라 현재 요청을 잘못 보내는 활성 운영 상태는 아니다.
- Identity delivery가 HTTP status만 반환해 `Retry-After`를 읽지 못하는 지적은 맞다. 다만 eligibility endpoint의 구체 계약은 409를 `EVENT_ID_CONFLICT`로만 사용하고 `COMMAND_PROCESSING`을 반환하지 않으므로 error body를 읽어 두 409를 구분해야 한다는 부분은 현재 범위에 불필요하다. ADR 공통 오류표의 eligibility processing 표현은 구체 endpoint 계약과 일치하도록 정리할 필요가 있다.
- Billing 최신 계약은 앱→Learning Core 시험 생성의 필수 UUID v4 `Idempotency-Key`지만 Learning Core 과거 문서는 optional이고 실제 controller는 header를 받지 않는다. Reservation saga 전에 앱·Learning Core를 함께 전환해야 하는 실제 계약/구현 공백이다.
- 앱 종료 후 새 key·새 examId로 restart하는 규칙을 AGENTS와 통합 계약에 보강하자는 제안은 타당하다. `/internal/v1/attempt-group-events`의 hyphen은 eligibility namespace에만 적용된 no-hyphen 규칙을 위반하지 않는다.
- Jira 키는 없다. 이번 확인에서는 계약·애플리케이션·다른 서버 코드를 수정하지 않았다.

## 2026-08-27 통합 계약 불일치 보정

- ADR-001 `inbound_event_inbox`에 `consumerScopeId`, APPLIED·STALE 저장 disposition과 `ux_inbox_identity_scope_user_revision` unique partial index를 반영해 PLAN-001과 정렬했다.
- ADR 공통 409 `COMMAND_PROCESSING`을 Reservation command 전용으로 명확히 하고 eligibility 409는 `EVENT_ID_CONFLICT` 전용으로 유지했다.
- PLAN-001 Phase 0 문서 보정을 완료 상태로 갱신하고 Step 1을 ADR 계약의 실제 document/index 구현·검증 단계로 바꿨다.
- 통합 계약과 AGENTS에 공개 시험 생성의 필수 lowercase UUID v4 `Idempotency-Key`, Request Body·성공 DTO 유지, transport retry와 앱 종료 restart의 새 key·새 examId 구분을 반영했다.
- Identity ADR-002의 이전 Bearer route를 Billing C3-D Lattice AWS_IAM·task role·SigV4와 `/internal/v1/eligibility/trial/events` 목표로 보정하고 429·503 `Retry-After` 처리 계약을 추가했다. 관련 기존 Jira는 `TMI-95`이며 Jira는 변경하지 않았다.
- Learning Core의 Billing 계약 검토 문서에서 optional header를 최신 필수 header 계약으로 보정했다. 실제 Identity adapter와 Learning Core controller·Reservation saga 코드는 아직 변경하지 않았다.
- 애플리케이션 코드·AWS 리소스·외부 배포는 변경하지 않았다.

## 현재 구성

- Java 21, Spring Boot 3.4.2, Gradle Groovy 기반 초기 프로젝트다.
- MongoDB, Web, Validation, Security Resource Server, Actuator 의존성이 준비되어 있다.
- 애플리케이션 이름은 `app-back-end-billing`, 기본 로컬 포트는 `8082`다.
- MongoDB 연결은 `MONGODB_URI` 환경변수로 재정의한다.
- `/actuator/health`만 공개하고 그 밖의 요청은 인증 계약 구현 전까지 차단한다.
- 원격 저장소 `origin`은 `https://github.com/Too-Much-I/app-back-end-billing.git`이다.

## 확정된 경계

- Billing은 상품, 결제 검증·원장, entitlement, credit, pass, TrialClaim, Reservation을 소유한다.
- Identity는 사용자 계정과 토큰 발급을 소유한다.
- Learning Core는 시험 Session과 학습·채점 상태를 소유한다.
- Reservation 기본 흐름은 `reserve → Learning Core Session commit → confirm`, `RESERVED` TTL은 5분이다.
- raw phone과 실제 결제 credential·receipt·token 원문은 저장소, 로그, 작업 문서에 남기지 않는다.
- Billing 계약의 단일 기준은 `docs/codex/CONTRACT_DECISIONS.md`다. 앞으로 Billing 관련 결정과 작업기록은 Billing 저장소 `docs`에만 기록한다.

## 현재 계약 결정 단계

- 결제·credit·pass·coupon·환불 구현은 후속 릴리스로 연기됐다. 기존 계약은 삭제하지 않고 동결한다.
- verified-phone당 무료 모의고사 1회를 위해 Billing은 최소 Entitlement(`TrialClaim`, `FREE_EXAM_ONCE`, reserve/confirm/cancel, reconciliation)를 먼저 구현한다.
- 기존 확정사항은 Billing의 `CONTRACT_DECISIONS.md`로 이관했다.
- 2026-08-26 사용자가 무료 최소 Entitlement 권장안을 전부 승인했고 후속 인프라 확인 뒤 C3-D VPC Lattice + ECS task role + SigV4 + AWS_IAM을 최종 승인했다. 사용자 Billing API와 사용자 Billing audience(C1/C2)는 결제 단계까지 보류한다.
- Billing production 배포 전 production/staging 두 ECS cluster와 환경별 데이터·credential·IAM·Lattice/SG 격리를 준비하는 것이 확정된 배포 gate다. 현재 운영 트래픽을 처리하는 `tosunsaeng-staging-cluster`는 새 production cluster로 트래픽을 안전하게 전환한 뒤 최종 staging으로 사용한다.
- 결제 adapter 전 C9~C11(store 상품 연결, credit 만료, 환불/chargeback)의 승인이 필요하다.
- 보상 전 C12(출석/coupon)의 승인이 필요하다. C13은 `claimedAt + 3년` 보존, 기간 안의 기존 Claim 유지와 만료 후 같은 번호 재수급 허용으로 확정됐다.

## 아직 구현하지 않은 항목

- Billing 도메인 모델, API, Mongo index와 transaction
- Lattice/SigV4 workload 인증과 reserve/confirm/cancel/status 계약 구현
- Apple/Google server verification adapter와 notification 처리
- reconciliation scheduler와 운영 관측성
- Lattice service network/service/listener/target, ECS task role·auth policy·security group

## 2026-08-26 ADR-002 VPC Lattice·ECS SigV4·환경 이관 기준 작성

- `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`를 승인된 C3-D와 production/staging 이관의 구현 기준으로 추가했다.
- production/staging 별도 Lattice service network, Billing HTTPS 443 listener→HTTP 8082 ECS target, `AWS_IAM`, exact task-role route policy·SG direct bypass 차단을 고정했다.
- Identity role은 eligibility event, Learning Core role은 reservation/status/AttemptGroup event만 호출하고 반대 환경 service와 repair route는 deny한다.
- Java client는 AWS SDK v2 `DefaultCredentialsProvider`, SigV4 service `vpc-lattice-svcs`, region `ap-northeast-2`, exact generated DNS·1초 connect/3초 request timeout·same-key caller retry를 사용한다.
- 현 cluster의 운영 트래픽은 새 production을 먼저 구성·병행 검증하고 전환한다. 전환 후 기존 cluster의 production DB/Secret/role 참조를 제거하고 staging으로 전환한다.
- local shell에 AWS credential이 없어 실제 role/VPC/subnet/SG/Lattice ID는 조회하지 못했다. 임의 ARN을 적지 않고 배포 전 read-only inventory/infra output으로 주입하도록 했다.
- AWS/GitHub Actions·application code는 변경하지 않았다.

## 다음 권장 작업

- 상세 구현 계획은 `docs/plans/PLAN-001-trial-eligibility-event-consumer.md`에 작성했다.
- 즉시 다음 작업은 ADR-001의 Identity `PhoneEligibilityBindingVerified`/`Revoked` event inbox·revision high-water·current binding vertical slice 구현이다.
- v1 DTO/validation, eventId·payload digest 멱등성, Mongo Transaction, unique index, stale/duplicate/conflict response와 fail-closed security test를 함께 구현한다.
- 이 단계에서는 AWS Lattice를 직접 호출하지 않고 inbound workload 경계를 adapter/test principal로 격리해 local test한다.
- 그다음은 TrialClaim·candidate alias·subject link·free grant/ledger와 INITIAL reserve Transaction, 이후 confirm/cancel/status·5분 expiry·AttemptGroup이다.
- Identity·Learning Core SigV4 client와 실제 Lattice/IAM/SG는 Billing API vertical slice가 안정된 후 연동한다.
- credential이 있는 AWS 환경의 role/VPC/subnet/SG read-only inventory와 새 production/staging 이관은 production 배포 준비 gate로 남겨두되, application 개발을 차단하지 않는다.
- 계획 작성 중 same user·scope·revision/different event conflict를 race에서도 강제하기 위해 ADR-001 inbox에 `consumerScopeId`와 `ux_inbox_identity_scope_user_revision` unique partial index를 보강할 필요를 확인했다. 이 문서 보정은 구현 Step 1에서 코드와 함께 수행한다.
- 사용자 승인으로 eligibility 내부 API URL은 `/internal/v1/eligibility/{kind}/...` namespace로 통합한다. 현 Trial route는 `/internal/v1/eligibility/trial/events`이고, 향후 실제 API가 필요할 때 paid는 `/internal/v1/eligibility/paid/...`, coupon은 `/internal/v1/eligibility/coupon/...`를 사용한다. URL namespace만 공통화하며 종류별 DTO·권한·멱등성·aggregate는 분리한다. URL segment에는 hyphen을 사용하지 않는다.
- Trial current projection collection은 `trial_eligibility`, Java package는 `trialeligibility`로 유지한다. index는 `ux_trial_scope_user`, `ix_trial_key_version`, metric은 `billing.trial_eligibility.events`를 사용하고 Billing class와 PLAN 제목·fixture는 `TrialEligibility*` 계열을 사용한다. Identity에 이미 구현된 wire event type `PhoneEligibilityBindingVerified`/`Revoked`는 schema v1 호환성을 위해 유지한다. ADR-001, ADR-002, PLAN-001에 확정 route를 반영했다.
- PLAN-001의 strict decoder는 입력을 저장하기 전에 JSON 구조·타입·계약 상수·event별 field·candidate·시간 순서를 검증하고, configured expected `consumerScopeId`와 exact match하지 않으면 422로 거절한다. canonical digest는 검증된 값을 정렬·정규화한 canonical JSON의 SHA-256이므로 property/candidate 순서와 whitespace만 다른 재전송은 동일 event로 수렴하며 의미가 바뀐 payload는 conflict로 판별한다. raw payload는 저장하지 않는다.
- canonical digest pipeline은 `수신 → strict decode → 값 검증 → 정렬·정규화 → canonical JSON → SHA-256 → digest 저장` 순서다. 이는 이벤트 내용을 암호화하거나 무료권을 차감하는 절차가 아니라, 동일 eventId 재전송의 내용 동일 여부를 비교하기 위한 결정적 지문 생성 절차다.
- PLAN-001은 무료권 지급 구현이 아니라 Identity의 verified/revoked phone eligibility event를 Billing에 안전하게 동기화하는 첫 vertical slice다. Lattice 인증, 16 KiB 제한·strict decode, digest, inbox 멱등성, revision high-water current projection을 거쳐 inbox와 `trial_eligibility`를 한 Mongo Transaction으로 반영한다. 결과는 APPLIED·DUPLICATE·STALE·CONFLICT로 수렴하며 TrialClaim·grant·Reservation은 후속 단계다.

## 다음 작업 전 확인할 사항

- internal API wire DTO, Mongo collection/index/Transaction과 contract test 기준은 `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`로 구체화했다.
- Lattice/ECS/SigV4·환경 이관 기술 기준은 ADR-002로 구체화했다. 남은 작업은 credential이 있는 환경의 read-only AWS inventory와 ADR-001 Identity event consumer vertical slice 구현이다.
- 그다음 store 상품·만료·환불 정책 C9~C11을 확정한다.
- Billing 관련 후속 기록은 Learning Core가 아니라 이 저장소의 `docs/codex`에만 남긴다.
- Mongo transaction 통합 테스트는 replica set Testcontainers로 설계한다.

## 2026-08-25 구현 시작 분석

- 현재 Billing 코드는 health-only 공개 보안 골격뿐이며 도메인 entity, repository, controller와 service는 아직 없다.
- Identity의 `PhoneEligibilityBinding` schema v1 producer와 lease/retry/dead-letter publisher는 구현돼 있다. Billing에는 event inbox, revision high-water, current verified binding consumer와 staging E2E가 없다.
- Learning Core의 `POST /api/v1/exams`는 현재 Billing reserve 없이 기존 진행 Session을 abandon하고 새 `ExamSession`을 즉시 insert한다. Billing 연동 시 이 경계를 `reserve → Session commit → confirm` saga와 동일-operation 복구로 바꿔야 한다.
- 2026-08-25 분석 당시 1차 구현 우선순위는 (1) 최소 계약 승인, (2) workload 보안 경계, (3) phone eligibility consumer의 inbox·high-water·binding Transaction, (4) 무료 1회 TrialClaim·grant ledger, (5) 멱등 Reservation reserve/confirm/cancel/status와 5분 만료, (6) reconciliation·관측성, (7) Learning Core contract client와 staging E2E였다. 최소 계약 승인은 2026-08-26 완료됐다.
- Apple/Google 결제 검증, paid credit, unlimited pass, coupon, 추천·출석 보상과 환불은 최소 무료 Entitlement 출시 뒤의 후속 단계다.
- 2026-08-25 당시 미확정이던 사용자 Billing API·workload 방식·멱등성·오류·AttemptGroup 정책은 2026-08-26 승인됐다. exact wire DTO와 Mongo 설계는 ADR-001로 구체화했고 환경별 Lattice 리소스·role 값은 아직 남아 있다. TrialClaim 보존기간은 같은 날 `claimedAt + 3년`으로 확정됐다.
- 현재 프로젝트 골격과 문서가 모두 Git 미추적 상태다. 기능 구현 전에 사용자가 초기 기준선을 commit해 프로젝트 생성 diff와 첫 기능 diff를 분리해야 한다.
- Jira 키는 없다. 다음 권장 작업은 C1~C8을 한꺼번에 넓게 구현하는 것이 아니라 phone binding 수신부터 무료시험 reservation까지의 vertical slice를 별도 Jira들로 나누는 것이다.

## 2026-08-25 결제 제외 무료 모의고사 1회 구현 백로그

- 이번 출시 범위는 verified-phone candidate당 `FREE_EXAM_ONCE` 1회뿐이다. Apple/Google, 유료 credit, unlimited pass, coupon, 추천·출석, 환불은 구현하지 않는다.
- 첫 reserve의 Mongo transaction 안에서 현재 verified binding을 확인하고, `(benefitScopedCandidate, FREE_EXAM_ONCE)` unique `TrialClaim`, 무료 grant/ledger와 `Reservation`을 함께 만든다. 계정 생성이나 전화번호 인증만으로 사용자 balance를 직접 증가시키지 않는다.
- reserve가 취소·만료되면 allocation은 무료 grant로 정확히 돌아가지만 `TrialClaim` 자체는 삭제하거나 다시 열지 않는다. 계정 merge·탈퇴·재가입에도 claim은 유지하고 현재 verified binding에 맞춰 entitlement owner만 이전한다.
- `Reservation` 기록은 Mongo TTL로 삭제하지 않는다. `expiresAt`을 기준으로 `RESERVED`만 명시적으로 `EXPIRED` 처리하며, `CONFIRMED` consumption과 audit ledger는 보존한다.
- 최소 데이터 경계는 phone eligibility event inbox/high-water/current binding, `TrialClaim`, free entitlement grant/ledger, allocation, `Reservation`, `AttemptGroup`, command idempotency record다. raw phone, last4와 Identity fingerprint는 저장하지 않는다.
- 최소 내부 API는 Learning Core용 reserve/confirm/cancel/status다. 동일 user·operation의 재호출은 같은 결과를 반환하고 payload가 다르면 conflict로 거절한다. 앱에 무료 사용 가능 여부를 미리 보여줄 필요가 없다면 Billing 사용자 조회 API와 Billing audience 추가는 결제 출시까지 미룰 수 있다.
- Learning Core 시험 생성은 같은 operation ID로 `reserve → Session commit → confirm`을 실행한다. Session commit 실패는 cancel하고 confirm 결과가 불명이면 Session을 `ENTITLEMENT_CONFIRMING`으로 유지해 status/retry/reconciliation으로 수렴시킨다.
- 구현 순서는 (1) 승인된 계약의 wire DTO·index 구체화, (2) Lattice/SigV4와 fail-closed 보안, (3) Identity event consumer·replay, (4) TrialClaim·ledger·Mongo unique/transaction, (5) Reservation API와 expiry, (6) Learning Core saga, (7) 양방향 reconciliation·관측성, (8) replica-set Testcontainers와 staging E2E다.
- 출시 조건은 동시 요청에도 한 candidate의 claim이 하나뿐이고, 같은-key retry가 중복 Session/소비를 만들지 않으며, Session commit 실패·confirm 응답 유실·5분 만료·owner 이전이 자동 복구되고, eligibility event가 없거나 인증이 잘못된 요청은 fail-closed 되는 것이다.
- C1/C2는 Billing 사용자 API를 이번에 노출할 때만 출시 차단 계약이다. C6의 전체 entitlement 우선순위는 결제 시 확장하되 이번 resolver는 `FREE_EXAM_ONCE`만 선택한다. 결제 관련 C9~C12는 후속 범위다.

## 2026-08-25 무료 Entitlement 계약 결정 진행

- 사용자와 선택할 이번 범위는 C1~C8과 C13이며, 결제·credit·환불·출석·coupon에 관한 C9~C12는 결정과 구현을 모두 미룬다.
- 즉시 확정이 필요한 출시 차단 항목은 workload 인증(C3), 멱등성(C4), 오류 계약(C5), 동시성과 confirm 불명 복구(C7), AttemptGroup 완료(C8), TrialClaim 보존·번호 재할당(C13)이다.
- Billing 사용자 조회 API를 이번에 제공하지 않으면 앱 호출 경로와 사용자 token audience(C1/C2)는 결제 단계까지 보류할 수 있다. 무료권만 존재하는 이번 resolver는 C6 전체 우선순위를 구현하지 않고 `FREE_EXAM_ONCE`만 서버가 선택할 수 있다.
- 이때 제안한 C1/C2 보류, 필수 앱 key, 행동별 오류, 무료권 자동 선택, C7-A, C8-A, C13-A와 기존 claim 유지 묶음은 2026-08-26 사용자 승인으로 확정됐고 workload 방식은 최종적으로 C3-D Lattice/SigV4로 대체 확정됐다.

## 2026-08-26 workload JWT와 무료권 소비 시점 분석

- Identity는 사용자 RS256 Access Token을 발급하지만 workload issuer는 구현하지 않았다. workload JWT 대신 ECS task role과 Lattice SigV4를 사용하는 C3-D가 후속 승인돼 Identity client-credentials 구현은 현재 범위에서 제외한다.
- 무료권은 전화번호 검증 event 수신이나 시험 완료 때 차감하지 않는다. 첫 `reserve` Transaction에서 current verified binding, unique `TrialClaim`, `FREE_EXAM_ONCE` grant와 allocation을 만들고 사용권을 `RESERVED`로 잠근다.
- Learning Core가 durable Session commit에 성공한 뒤 `confirm`이 Reservation과 ledger를 `CONFIRMED`/`CONSUMED`로 최종 전환한다. Session commit 실패 또는 commit 전 5분 만료는 allocation을 복구하지만 `TrialClaim`은 삭제하지 않는다.
- confirm 뒤에는 일반 cancel로 무료권을 되돌리지 않는다. confirm 결과가 불명이면 Session을 성공으로 노출하거나 즉시 삭제하지 않고 status retry와 reconciliation으로 최종 상태를 확인한다.
- AttemptGroup의 `COMPLETED`는 차감 시점이 아니라 같은 consumption으로 추가 차감 없이 restart할 수 있는 권리를 닫는 시점이다. 결과 생성이 최종 실패하면 확정된 C8-A에 따라 `RETAKE_AVAILABLE`로 전환해 같은 consumption을 재사용한다.

## 2026-08-26 무료 최소 Entitlement 계약 승인

- 사용자가 앞서 설명한 권장안을 전부 승인했다. 계약 단일 기준인 `CONTRACT_DECISIONS.md`에 승인 요약과 상태 전이를 추가하고 선택지의 해당 항목을 확정으로 변경했다.
- 이번 릴리스는 앱 → Learning Core → Billing 내부 호출만 사용하며 Billing 사용자 API와 사용자 token Billing audience는 결제 단계까지 보류한다.
- Identity eligibility event inbox/high-water/fail-closed와 첫 reserve Transaction의 unique TrialClaim·무료 grant·Reservation 생성을 확정했다.
- Learning Core와 Identity의 Billing 내부 호출은 C3-D Lattice/SigV4로 확정했다. task role별 최소 route 권한을 적용하고 privileged repair-confirm은 별도 운영 role이다.
- UUID v4 `Idempotency-Key`는 필수이고 terminal command는 우선 7일 보존한다. 행동별 402/409/429/503 오류와 same-key retry를 확정했다.
- 무료권은 서버가 자동 선택하며 reserve에서 잠그고 Session durable commit 뒤 confirm에서 최종 소비한다. confirm 전 실패는 allocation만 복구하고 TrialClaim은 유지하며 confirm 결과 불명은 status/reconciliation으로 복구한다.
- 사용자당 OPEN AttemptGroup·active Session·생성 command는 각각 하나다. 결과 조회 가능 시 group을 완료하고 결과 생성 최종 실패는 같은 consumption의 `RETAKE_AVAILABLE`로 복구한다.
- TrialClaim dedupe 연결은 `claimedAt + 3년` 보존하고 그 기간의 번호 재할당에도 기존 Claim을 유지한다. 3년 만료 뒤에는 같은 번호의 새 Claim을 허용한다. Lattice/SigV4 staging negative test는 production gate로 남는다.

## 2026-08-26 승인 후 남은 구체화 작업

- workload trust는 Lattice `AWS_IAM` auth policy와 ECS task role ARN으로 관리한다. Learning Core role은 reservation route, Identity role은 eligibility event route만 허용하고 wildcard principal은 금지한다.
- Billing은 Lattice를 거친 ingress만 허용하고 direct task·old ALB 우회 경로를 security group과 routing으로 차단한다. privileged repair-confirm은 별도 운영 role과 route policy로 분리한다.
- TrialClaim의 `retentionExpiresAt`은 immutable `claimedAt + 3년`이다. 만료 alias는 물리 purge 전에도 dedupe matching에서 제외하며 candidate/keyVersion과 사용자·source event 연결을 삭제·비식별화한다. daily purge·24시간 삭제 SLA·35일 rolling backup과 restore-before-traffic purge가 확정됐고 candidate key reference lifecycle은 구현 ADR에 반영한다.
- API DTO 설계는 internal reserve/confirm/cancel/status endpoint, 필수 `Idempotency-Key`, canonical userId·operationId·mockExamId·sessionId 연결, payload hash conflict, stable 오류 envelope와 버전 정책을 OpenAPI/contract test로 고정하는 작업이다.
- Mongo index 설계는 eventId inbox unique, user+scope current binding, candidate alias+benefit unique TrialClaim, user+operation Reservation unique, 사용자당 단일 OPEN group/active command partial unique, allocation·ledger dedupe와 expiry scan index를 정의하는 작업이다. Reservation audit 문서에는 TTL delete index를 두지 않는다.
- 즉시 진행 가능한 작업은 API DTO·collection/index ADR, Lattice/SigV4 client·ingress 보안 설계와 mock contract test다. TrialClaim 기간 숫자는 확정됐고 purge/anonymization 구현 명세만 남았다.

## 2026-08-26 AWS ECS 확인에 따른 C3 재검토

- 사용자가 실제 배포 플랫폼이 AWS ECS라고 확인했다. ECS task role은 컨테이너에 자동 회전되는 AWS 임시 credential을 제공하지만 일반 OIDC workload JWT, issuer와 JWKS를 자동 제공하지 않는다.
- 따라서 앞서 승인한 C3의 `배포 플랫폼 발급 JWT` 전제만 재검토 상태로 변경했다. Identity eligibility event, TrialClaim, Reservation, 멱등성, 오류, 소비·완료와 보존 계약은 그대로 확정 상태다.
- Learning Core → Billing 경로가 VPC Lattice 또는 API Gateway를 거친다면 task role + SigV4 + `AWS_IAM` route policy가 1차 권장이다. 별도 JWT issuer·JWKS·client secret이 필요 없고 IAM role별 최소 권한을 적용할 수 있다.
- 내부 ALB, Cloud Map 또는 ECS Service Connect로 애플리케이션을 직접 호출한다면 이 계층은 task-role SigV4 인증을 자동 검증하지 않는다. 이 경우 별도 OIDC/workload JWT issuer, 애플리케이션 SigV4 검증 adapter 또는 mTLS 중 하나가 추가로 필요하다.
- 후속 확인에서 Identity·Learning Core는 Load Balancer, Service Connect 없음, Billing 미배포, Lattice 없음으로 확인됐고 C3-D greenfield Lattice 구성이 최종 승인됐다.

## 2026-08-26 기존 Identity·Learning Core 인증 방식 대조

- 실제 구현된 공통 인증은 Identity가 사용자 RS256 Access Token을 발급하고 Learning Core가 Identity issuer·JWKS·audience·시간·UUID subject를 Spring OAuth2 Resource Server로 로컬 검증하는 방식이다. 요청마다 Identity introspection을 호출하거나 shared HMAC secret을 사용하지 않는다.
- Identity의 phone eligibility와 user merge publisher는 Bearer workload credential port와 HTTP adapter까지 있으나 `WorkloadIdentityCredentialProvider` production 구현이 없고 publisher flag가 기본 false이므로 현재 재사용 가능한 서버 간 인증 구현은 아니다.
- Learning Core → Python AI 요청은 `Idempotency-Key`만 설정하고 Authorization을 보내지 않는다. 결제·entitlement 차감처럼 권한이 필요한 Billing 내부 API의 선례로 사용하지 않는다.
- 기존 구조와 동일하게 맞추려면 사용자 Access Token을 전달하는 것이 아니라 Identity RS256 issuer/JWKS 메커니즘을 workload 전용 token profile로 확장한다. Billing은 `token_use=workload`, Billing audience, allowlist된 service subject, 5분 TTL과 endpoint scope를 추가 검증한다.
- 이 E안은 비교 대안으로만 남고 후속 C3-D 승인으로 현재 구현 범위에서 제외됐다.

## 2026-08-26 C3 세 방식 비교

- 내부 ECS-to-ECS 호출만 고려한 전체 권장 순서는 VPC Lattice + task role SigV4, Identity-issued workload JWT, API Gateway + AWS_IAM이다.
- VPC Lattice는 task role 임시 credential과 IAM auth policy를 사용해 별도 client secret·JWT issuer 없이 서비스 identity와 network 경로를 함께 관리하고 Identity 장애가 Billing 호출로 전파되지 않는다는 장점이 있다. 서비스 network·route·auth policy·direct bypass 차단과 AWS 종속성이 비용이다.
- Identity workload JWT는 현재 RS256/JWKS/Spring Resource Server 패턴을 재사용하고 direct ALB/Service Connect 경로에도 적용할 수 있으며 cloud portability가 높다. 반면 workload client-credentials·Secret rotation·token endpoint/cache를 세 서비스에 구현하고 Identity 장애와 key 운영을 감수해야 한다.
- API Gateway + AWS_IAM은 task role SigV4, route별 IAM, throttling·WAF·stage·access log가 강점이지만 순수 내부 호출에는 VPC Link/private API, route/stage와 direct bypass 차단이 추가되고 비용·latency·운영 구성이 가장 커질 수 있다.
- Billing이 내부 ECS 서비스에만 쓰이고 새 service network 구성이 가능하면 VPC Lattice를 최종 권장한다. 기존 internal ALB/Service Connect를 유지해야 하거나 AWS 인프라 변경을 피해야 하면 Identity workload JWT를 권장한다. API Gateway는 외부·파트너 공개 또는 조직 표준 gateway가 이미 있을 때 선택한다.

## 2026-08-26 ECS 현재 경로 확인과 Lattice 전환 절차

- 현재 경로는 ECS Console의 서비스 `Load balancing`, `Service Connect`, `Service discovery`, `VPC Lattice configuration`과 AWS CLI `ecs describe-services`의 loadBalancers, serviceConnectConfiguration, serviceRegistries, vpcLatticeConfigurations를 확인해 판별한다.
- EC2 Load Balancer의 scheme이 `internal`이고 ECS 서비스가 해당 target group에 연결돼 있으면 internal ALB 경로다. Service Connect enabled와 namespace/clientAliases가 있으면 Service Connect 경로이며 Cloud Map registry만 있으면 service discovery 직접 경로일 수 있다.
- Lattice 전환은 기존 경로를 즉시 제거하지 않고 task definition named port, Billing Lattice service/listener/target, VPC service network association, Learning Core·Identity task role auth policy와 SigV4 client를 먼저 병렬 배포한다.
- staging에서 허용 role의 서명 요청 성공, unsigned·다른 role 403, 동일 idempotency retry, direct Billing endpoint 우회 차단을 검증한 뒤 `BILLING_BASE_URL`을 Lattice DNS로 전환한다. rollback 기간 뒤 Billing 전용 old ALB/Service Connect route만 제거하고 공유 ALB는 삭제하지 않는다.
- 내부와 사용자 API를 같은 ALB에서 제공 중이면 old ingress를 일괄 차단하지 않는다. `/internal/**` 전용 port/service 또는 routing·security boundary를 먼저 분리해야 한다.

## 2026-08-26 실제 ECS 현황 확인

- 사용자 확인 결과 Identity와 Learning Core는 Load Balancer를 사용하고 Service Connect는 사용하지 않는다. Billing은 아직 ECS에 배포되지 않았고 VPC Lattice도 아직 없다.
- 따라서 기존 Identity·Learning Core inbound Load Balancer를 이전하지 않는다. 두 서비스의 사용자 API 경로는 그대로 두고 outbound Billing 호출만 새 Lattice DNS와 SigV4를 사용한다.
- Billing은 처음부터 public/internal ALB 없이 ECS named port를 VPC Lattice service target에 연결하는 greenfield 구성을 권장한다. Service network를 두 서비스 task가 위치한 VPC에 associate하고 Billing direct task 접근은 security group으로 막는다.
- Learning Core task role에는 reservation reserve/confirm/cancel/status invoke, Identity task role에는 phone eligibility event invoke만 허용한다. repair-confirm은 별도 운영 role로 둔다.
- Billing 애플리케이션은 Lattice auth policy 통과 경로만 내부 API에 허용하도록 배포 profile과 ingress header/identity 검증을 구성하고, unsigned·wrong role·direct path negative test를 production gate로 둔다.
- 향후 앱이 Billing을 직접 호출하는 결제 사용자 API가 생기면 별도 public ALB/API Gateway 경로를 추가하고 `/internal/**` Lattice 경계와 분리한다.
- 현재 조건에서 C3-D VPC Lattice + ECS task role + SigV4가 사용자 승인으로 최종 확정됐다.

## 2026-08-26 C3-D 최종 승인

- 사용자가 `C3-D`를 명시 승인했다. VPC Lattice + ECS task role + SigV4 + AWS_IAM이 Billing workload 인증의 확정 계약이다.
- Identity·Learning Core 기존 사용자 inbound Load Balancer는 유지하고, 미배포 Billing만 ALB 없이 Lattice service target으로 새 배포한다.
- Learning Core와 Identity는 각자 분리된 task role의 임시 credential로 요청을 SigV4 서명한다. 별도 workload JWT issuer·JWKS·client secret과 API Gateway는 이번 내부 API 범위에 만들지 않는다.
- Lattice auth policy는 Learning Core reservation route와 Identity eligibility event route를 분리한다. repair-confirm은 별도 운영 role이며 direct Billing task/old route 우회는 차단한다.
- production gate는 unsigned 요청, wrong role, 권한 없는 route와 direct path가 모두 실패하고 same-key retry와 confirm reconciliation이 staging에서 성공하는 것이다.

## 2026-08-26 C3-D 승인 후 잔여 확정사항 감사

- 무료 모의고사 MVP의 핵심 제품 계약은 구현을 시작할 수 있을 정도로 확정됐다. workload 인증 방식, 무료권 잠금·소비 시점, 멱등성, 오류, 동시성, 재응시와 번호 재할당 정책을 다시 선택할 필요는 없다.
- C13의 TrialClaim 보존기간, 기산점, 만료 후 재수급, 물리 purge SLA와 backup 수명까지 후속 승인으로 해결됐다.
- 구현 전에 ADR로 고정할 기술 명세는 internal API wire DTO·버전·오류 envelope, Mongo collection/index/transaction, Lattice 리소스·실제 task role ARN·route auth policy·security group, SigV4 signer와 local/test 대체 구현이다. 이는 승인된 정책을 바꾸지 않는 한 개발자가 권장안으로 작성해 검토받을 수 있다.
- 운영 전 수치로 고정할 항목은 reconciliation 주기·재시도/경보 기준, repair-confirm 운영 절차, event inbox/tombstone과 audit/command 보존·삭제 job이다. Identity publisher의 기존 retry와 replay 계약은 가능한 한 재사용한다.
- 결제 C9~C11, 출석·coupon C12, 사용자 Billing API·audience C1/C2와 전체 entitlement 우선순위는 현재 무료 MVP의 차단 사항이 아니며 해당 기능 착수 시 확정한다.

## 2026-08-26 TrialClaim 보존기간 선택 준비

- 유한 보존기간이 끝나 candidate/alias를 삭제하면 같은 전화번호의 재등장을 판별할 수 없으므로 이후 무료권 재수급을 허용하게 된다. 반대로 전화번호당 영구 1회를 보장하려면 무료시험 프로그램 운영 중 candidate를 계속 보존해야 한다.
- 선택지는 `claimedAt + 3년`, `claimedAt + 5년`, `무료시험 프로그램 종료 + 1년`으로 구분한다. 개인정보 최소화와 실용적 abuse 방지의 균형안은 3년, 기존의 강한 phone당 1회 정책을 가장 충실히 지키는 안은 프로그램 종료 + 1년이다.
- 유한 기간을 선택하면 기간 종료 후 같은 번호의 새 Claim을 명시적으로 허용하도록 제품 문구를 바꿔야 한다. 만료 시 candidate alias·keyVersion·user/event 연결은 삭제하고, 통계에 필요한 비연결 상태·시각만 비식별화해 남기는 방식을 권장한다.
- 이 절을 작성한 시점에는 사용자 최종 선택 전이었으며, 바로 아래 후속 승인에서 `claimedAt + 3년`으로 확정됐다.

## 2026-08-26 TrialClaim 3년 보존 최종 승인

- 사용자가 보존기간 선택지 B인 `claimedAt + 3년`을 승인했다. 이는 과거 C13의 선택지 문자인 B가 아니라 이번 보존기간 세부 선택지 B를 뜻하며, 계약상 C13-A의 구체적인 기간으로 반영했다.
- `claimedAt`은 Claim 최초 생성 시 고정하고 로그인·merge·탈퇴·revoke·cancel·expiry·restart로 갱신하지 않는다. `retentionExpiresAt`부터 만료 alias는 물리 삭제 여부와 관계없이 dedupe matching에서 제외한다.
- 3년 동안은 번호 재할당, 탈퇴와 재가입에도 기존 Claim이 무료권 재수급을 차단한다. 3년 뒤에는 candidate 연결을 제거하고 같은 번호가 다시 verified되면 새 Claim을 허용한다.
- candidate alias, keyVersion, user와 source event 연결은 삭제·비식별화한다. 개인과 다시 연결할 수 없는 최소 집계만 남길 수 있으며, raw phone·last4·전화번호 암호문은 계속 저장하지 않는다.
- 따라서 제품 계약은 더 이상 `phone당 평생 1회`가 아니라 `verified-phone candidate당 3년 내 1회`다. daily purge·24시간 삭제 SLA와 35일 rolling backup도 후속 승인으로 확정됐다.

## 2026-08-26 TrialClaim 물리 삭제·백업 주기 결정 준비

- 이 값들은 도메인 구현 착수의 선행 조건은 아니지만 C13 보존 계약과 production 운영을 완결하는 상수이므로 지금 확정하는 것이 적절하다.
- 권장안은 logical expiry 즉시 dedupe 제외, 매일 purge 실행과 만료 후 24시간 이내 active DB 연결정보 삭제, 최대 35일 rolling backup 보존이다.
- 복구한 backup은 사용자 트래픽에 연결하기 전에 현재 시각 기준 만료 purge를 먼저 실행해야 하며, backup 속 만료 candidate를 일반 조회·dedupe에 다시 노출해서는 안 된다.
- purge 실패는 재시도하고 24시간 SLA 초과 시 경보한다. 삭제 증적에는 처리 건수·실행 시각·성공 여부만 남기고 candidate, userId와 source event를 기록하지 않는다.
- 7일 backup은 개인정보 최소화에는 유리하지만 장기 장애 복구 여유가 작고, 90일 backup은 복구 여유가 크지만 만료 정보 잔존기간과 접근통제 부담이 커진다. 이 비교 뒤 사용자가 바로 아래 후속 설명을 거쳐 35일 권장안을 최종 승인했다.

## 2026-08-26 TrialClaim purge 대상 명확화

- 3년 만료 시 삭제하는 핵심은 `(benefitType, keyVersion, candidate) → trialClaimId` dedupe alias와 Claim에 붙은 user/source event/phone binding 연결이다. 이 연결을 제거해야 과거 Claim을 같은 번호와 다시 대조할 수 없고 확정된 3년 뒤 재수급이 가능하다.
- `TrialClaim` 자체는 개인·candidate·Reservation·Session으로 역추적할 수 없는 익명 tombstone으로 축약할 수 있다. 남길 수 있는 값은 benefit type, terminal status, 거친 claimed/expired 시각과 식별자 없는 purge 통계뿐이다.
- 시험 Session, 문제, 답안, 점수, 피드백과 Summary는 Learning Core 소유이므로 이 purge 대상이 아니다. Billing의 금액·사용권 감사 event도 candidate/user mapping을 분리 제거한 비식별 core만 보존할 수 있다.
- 아직 유효한 Identity current verified binding은 새 Claim 자격 확인에 필요하므로 TrialClaim 3년 만료만으로 삭제하지 않는다. revoke·phone 교체·탈퇴 event의 별도 lifecycle을 따른다.
- immutable ledger나 Reservation에 userId·candidate를 직접 영구 저장하면 이 삭제 계약을 지킬 수 없다. 구현 ADR은 변경 불가능한 audit core와 삭제 가능한 subject/alias mapping을 분리해야 한다.

## 2026-08-26 24시간 purge·35일 backup 의미 설명

- candidate 삭제를 위해 별도 backup을 만드는 계약이 아니다. MongoDB 전체를 장애·오삭제 복구용으로 주기적으로 snapshot/continuous backup하는 일반 운영 백업에, 삭제 전 candidate 연결정보가 과거 시점 데이터로 잠시 포함될 수 있다는 의미다.
- 예를 들어 2026-09-01에 Claim이 생기면 2029-09-01부터 alias를 dedupe에 사용하지 않고, daily purge가 늦어도 2029-09-02까지 운영 DB의 candidate→Claim 및 Claim→user/event 연결을 제거한다.
- 2029-09-02 이전에 생성된 DB backup에는 삭제 전 연결이 들어 있을 수 있다. 35일 rolling을 선택하면 해당 backup 자체가 생성 후 최대 35일 뒤 만료되며, 애플리케이션은 평소 backup을 조회하지 않고 제한된 운영자만 재해 복구에 사용한다.
- 오래된 backup을 복구하면 삭제됐던 연결이 되살아날 수 있으므로, 격리된 복구 환경에서 현재 시각 기준 purge를 다시 실행한 후에만 사용자 트래픽을 연결한다.
- 이 설명 시점에는 24시간/35일 수치가 권장안이었으며, 후속 사용자 승인으로 `운영 DB 식별 연결 삭제 SLA 24시간 + 전체 DB 재해복구 backup 최대 35일`이 확정됐다.

## 2026-08-26 TrialClaim purge·backup 운영 계약 최종 승인

- logical expiry는 `claimedAt + 3년`에 즉시 적용하고 만료 alias를 dedupe에서 제외한다.
- purge job은 매일 실행하며 만료 후 24시간 안에 운영 MongoDB의 candidate alias와 erasable user/event mapping을 삭제하고 Claim을 비식별 tombstone으로 전환한다.
- 24시간 SLA를 넘긴 항목은 운영 경보를 발생시키고 성공할 때까지 재시도한다. 삭제 증적에는 실행 시각·처리 건수·성공 여부와 저 cardinality 실패 분류만 남기며 candidate, keyVersion, userId와 source event를 남기지 않는다.
- MongoDB 전체 재해복구 backup은 최대 35일 rolling 보존 후 자동 만료한다. backup은 일반 애플리케이션 조회에 사용하지 않고 접근 제한된 복구 용도로만 사용한다.
- 과거 backup을 복구할 때는 격리 환경에서 현재 기준 만료 purge를 먼저 실행하고 검증한 후에만 사용자 트래픽을 연결한다.

## 2026-08-26 무료 Trial 내부 API·Mongo ADR-001 작성

- `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`를 기술 구현 기준으로 추가했다. 앱-facing Billing API는 만들지 않고 Identity event와 Learning Core reservation/group-status 내부 API만 `/internal/v1`에 둔다.
- Learning Core의 기존 `examId`를 opaque `sessionId`, 문제지 ID를 `mockExamId`, 필수 앱 UUID v4 `Idempotency-Key`를 `operationId`로 구분했다. reserve 전에 sessionId/mockExamId를 선할당하고 durable Session commit 뒤 같은 operation으로 confirm한다.
- Identity `PhoneEligibilityBindingVerified`/`Revoked` schema v1 wire는 재사용하되 과거 Bearer JWT transport는 사용하지 않고 승인된 C3-D Lattice/SigV4 route만 사용한다.
- candidate alias, 삭제 가능한 subject link와 immutable ledger core를 분리하고 active alias/reservation/group/session/command partial unique index를 고정했다. expired alias는 purge 전 reserve에서도 fencing할 수 있어 정확히 3년부터 재수급을 허용한다.
- Reservation audit에는 TTL을 두지 않고 5분 business expiry를 scheduler+Transaction으로 처리한다. inbox 120일과 terminal command 7일 같은 보조 기록만 `purgeAt` TTL을 사용할 수 있다.
- Mongo replica-set Transaction 경계를 eligibility event, reserve, confirm, cancel/expiry, group event와 TrialClaim purge로 나눴고 필수 동시성·unknown commit result test 목록을 정의했다.
- TrialClaim 3년 purge로 subject link가 제거된 AttemptGroup은 더 이상 replacement authorization에 사용하지 않는다. Learning Core의 기존 Session·결과는 삭제하지 않으며 익명 audit projection만 남길 수 있다.
- C3-D Lattice/SigV4 인프라 설계는 후속 ADR-002로 완료했다. 다음 코드 작업은 ADR-001의 Identity event consumer부터 vertical slice로 시작한다.

## 2026-08-26 ADR-001 확정 내용 설명

- ADR-001은 기존 제품 결정을 바꾼 문서가 아니라 서비스 간 요청 형식, 식별자 의미, 상태 전이와 Mongo 강제 장치를 구현 가능한 수준으로 고정한 기술 계약이다.
- 앱은 계속 Learning Core만 호출한다. Identity는 verified/revoked phone binding event를, Learning Core는 reserve/confirm/cancel/status와 최소 AttemptGroup 상태 event를 Billing 내부 `/internal/v1`로 보낸다.
- 앱 UUID v4 `Idempotency-Key`가 operationId이고 Learning Core 기존 examId가 sessionId다. mockExamId는 AttemptGroup 동안 고정하며 의도적인 restart는 새 operation/session을 쓰되 같은 consumption/group/mockExamId를 재사용한다.
- 최초 reserve는 current verified binding을 확인하고 필요한 TrialClaim·free grant·hold를 한 Transaction으로 만든다. Session commit 뒤 confirm에서 최종 consume와 group OPEN을 확정하고, commit 실패 cancel/5분 expiry는 hold만 복원하며 Claim은 유지한다.
- candidate alias, erasable subject link와 immutable audit core를 분리했다. partial unique index로 active Claim, Reservation, group, Session과 command를 하나로 강제하고, 3년 만료 시 alias/user 연결만 제거해 새 Claim을 허용한다.
- 실제 AWS Lattice ARN·role·security group inventory와 애플리케이션 구현은 아직 하지 않았다. 논리 설계는 ADR-002로 완료했고 실값 확인과 vertical slice가 남아 있다.

## 2026-08-26 Reservation confirm과 Summary 완료 시점 재확인

- `confirm`은 Summary 생성 완료 때가 아니라 Learning Core가 proposed `sessionId`의 ExamSession을 MongoDB에 durable commit한 직후, 시험 생성 성공 응답을 앱에 보내기 전에 호출한다.
- confirm은 5분 hold를 최종 consumption으로 전환하고 AttemptGroup을 `OPEN`으로 만드는 entitlement 단계다. 사용자가 문제를 풀고 제출·채점·Summary를 받는 과정은 그 뒤에 진행된다.
- 모든 필수 submit 접수 시 `GRADING`, 필수 feedback·유효 score·Summary가 조회 가능할 때 별도 `AttemptGroupStatusChanged(COMPLETED)` event를 보낸다. Summary 실패가 최종 확정되면 `RETAKE_AVAILABLE`이다.
- Summary까지 confirm을 미루면 5분 Reservation이 시험 중 만료되고 같은 무료권이 다른 Session에 재사용될 수 있으며, 장시간 RESERVED 상태가 되어 확정 계약을 위반한다.

## 2026-08-26 phone eligibility candidate 의미 재확인

- candidate는 raw phone을 Billing에 보내지 않고 같은 verified phone인지 비교하기 위해 Identity가 normalized phone, consumerScopeId와 Identity 전용 secret key로 만든 HMAC-SHA-256 Base64URL 값이다.
- 같은 scope와 keyVersion에서 같은 번호는 같은 candidate를 만들지만 scope 또는 keyVersion이 다르면 값이 달라진다. Billing은 key material을 받지 않고 candidate를 역산하거나 새로 만들 수 없다.
- Identity verified event는 key rotation 동안 여러 `{keyVersion, value}` candidate를 함께 보낼 수 있다. Billing은 하나라도 기존 active alias와 일치하면 같은 phone proof로 판정한다.
- candidate는 event 서명이나 인증수단이 아니다. 호출자 인증은 C3-D Lattice/SigV4가 담당하고 candidate는 중복 Claim 비교에만 사용한다.
- raw phone보다 안전하지만 여전히 pseudonymous data이므로 로그·metric에 넣지 않고 TrialClaim `claimedAt + 3년` 만료 purge 대상에 포함한다.

## 2026-08-26 ADR-002 작성 전 사용자 입력 구분

- 실제 AWS account/region, 환경별 VPC·subnet, ECS cluster/service, task execution/task role ARN과 security group ID는 정책 선택이 아니라 AWS read-only 조회로 채울 배포 사실이다.
- 사용자 결정이 필요한 핵심은 (1) dev/staging/prod별 Lattice service network 분리 여부, (2) 기존 IaC 도구와 source of truth, (3) 별도 Identity/Learning Core/Billing task role 신규 생성 허용, (4) 초기 custom domain 사용 여부, (5) AWS SDK v2 SigV4 signer 운영 의존성 허용이다.
- 권장안은 환경별 service network 분리, 기존 IaC 도구 재사용, 서비스별 task role 분리, 초기 Lattice 기본 DNS 사용, AWS SDK v2 signer를 outbound adapter 뒤에 격리하고 local/test에서는 fake adapter를 사용하는 것이다.
- Billing은 ALB 없이 Lattice target, Learning Core reservation/group-status route, Identity eligibility event route, operations repair route와 direct bypass 차단으로 이미 확정돼 다시 선택하지 않는다.
- ADR-002는 실제 ARN을 repository에 고정 문자열로 박지 않고 환경 설정/infra output 참조로 기록한다. credential과 account secret은 문서·로그에 남기지 않는다.

## 2026-08-26 VPC Lattice 환경 분리 비용 확인

- AWS 공식 VPC Lattice pricing page를 2026-08-26 확인했다. 과금 차원은 provisioned Lattice service 실행 시간, 서비스 처리 데이터 GB와 HTTP request/TCP connection이며 service network 자체는 별도 과금 차원으로 제시되지 않는다.
- 공식 페이지는 VPC association과 service network endpoint를 추가 비용 없이 사용할 수 있다고 명시한다. 따라서 service/network association 개수만 늘리는 것이 직접적인 시간당 비용 원인은 아니다.
- staging/prod에 Billing Lattice service를 각각 상시 배포하면 service network를 공유하든 분리하든 provisioned service는 두 개이므로 기본 service-hour 비용은 동일한 방향이다. staging service를 새로 상시 띄우는 비용은 발생하지만 이는 network 분리 비용이 아니라 두 번째 service 실행 비용이다.
- 미국 동부 공식 예시는 HTTP Lattice service 하나가 시간당 0.025 USD, 730시간 기준 월 18.25 USD이며 실제 서울 리전 단가는 별도 확인이 필요하다. 데이터 처리와 요청 초과분은 사용량에 따라 추가된다.
- 비용만 줄이려고 prod/staging service network를 공유하는 것은 권장하지 않는다. 비용 절감이 필요하면 staging Lattice service를 테스트 기간에만 provision하는 선택지가 직접적이지만 상시 E2E·장애 재현 가능성이 낮아진다.
- 확인 출처: `https://aws.amazon.com/vpc/lattice/pricing/`

## 2026-08-26 staging Lattice 운영 방식 보완

- staging service network/service/listener/target group/IAM policy/SG를 테스트마다 생성·삭제하는 방식은 권장하지 않는다. 설정 drift, DNS/ARN 변경, IAM 전파 대기와 E2E 시작 지연이 생긴다.
- 권장 운영은 staging Lattice 인프라를 상시 유지하고 개발·통합 테스트가 잦은 기간에는 Billing ECS task도 상시 1개 운영하는 것이다. 이 구성이 가장 단순하고 production과 유사하다.
- 비용 절감이 필요하면 Lattice 리소스는 유지한 채 staging Billing ECS desired count를 업무 외 시간에 0으로 낮추고 테스트 전 1 이상으로 올린다. 이는 Fargate compute 비용은 줄이지만 provisioned Lattice service-hour 비용은 계속 발생한다.
- 서비스 자체를 제거·재생성하는 방식은 장기간 staging을 사용하지 않을 때만 IaC 자동화로 수행한다. 수동 Console 삭제·재생성은 권장하지 않는다.
- 현재 규모에서는 network 공유로 작은 비용을 아끼는 것보다 환경 격리와 상시 재현성을 우선해 `환경별 network + staging 인프라 상시 유지`를 기본 권장으로 둔다.

## 2026-08-26 production/staging ECS 운영 형태·이관 순서 확정

- Identity와 Learning Core workflow는 모두 `tosunsaeng-staging-cluster`로 배포하고 `identity-staging.to-teacher.com`, `api-staging.to-teacher.com`으로 health check하지만, 현재는 이 cluster가 실제 운영 트래픽을 처리한다.
- 같은 AWS account·VPC에 새 production ECS cluster를 만들고 production용 Identity·Learning Core service, 환경 설정·Secret·task role·SG·Lattice network/policy·database를 분리해 구성한다.
- 새 production Identity·Learning Core의 health·smoke·데이터 연결·롤백을 검증한 뒤 ALB/DNS 또는 현재 진입점의 운영 트래픽을 새 cluster로 전환한다. 전환 안정화 전에 기존 service를 제거하지 않는다.
- 전환·관찰·롤백 window를 통과한 후 기존 `tosunsaeng-staging-cluster`를 staging으로 확정하고 staging용 Identity·Learning Core·Billing과 별도 Lattice network, role/SG/config/secret·Mongo database를 둔다.
- staging Lattice service/listener/target/IAM/SG는 유지하고 평소 staging ECS service `desiredCount=0`, 테스트 전 필요한 Identity·Learning Core·Billing task를 각각 1 이상으로 scale out한다.
- 모든 target health와 phone event/reservation route smoke test가 성공한 뒤 E2E를 실행하고, 종료 후 staging task를 다시 0으로 낮출 수 있다. 자동화는 start→healthy wait→test→stop 순서이며 테스트 실패 시에도 stop 여부와 로그 보존 정책을 명시해야 한다.
- ECS cluster만 둘로 나누고 같은 production DB, secret, task role 또는 Lattice network를 공유하면 충분한 환경 격리가 아니다. 최소한 데이터베이스·credential·IAM principal과 network policy는 분리해야 한다.
- task를 0으로 내려도 provisioned Lattice service와 staging Mongo/backup 등 다른 managed resource 비용은 남을 수 있다.
- 이 턴에서는 AWS resource를 생성·수정·배포하지 않았다. 클러스터 확장은 구현 후 production 배포 전 인프라 작업으로 남아 있다.

## 2026-08-26 ADR-002 입력 승인·현행 workflow 확인

- 환경별 Lattice service network 분리, `ap-northeast-2`, 같은 AWS account·VPC, 서비스별 application task role 생성, Lattice 기본 DNS와 AWS SDK v2 signer를 확정했다.
- Identity와 Learning Core workflow는 GitHub OIDC deploy role로 AWS credential을 받고 ECR에 image를 push한 뒤, 현재 ECS Service의 Task Definition을 download/render/register/deploy한다.
- 이 workflow는 이미 있는 ECS resource의 application revision을 갱신할 뿐 cluster, Lattice, IAM, SG를 생성하지 않는다. 저장소에 Terraform, CDK, CloudFormation은 확인되지 않았다.
- application task role은 GitHub deploy role과 ECS task execution role과 분리해야 한다. 실제 Identity/Learning Core task role ARN은 AWS `describe-task-definition` 읽기 전에는 확정할 수 없다.
- 최초 인프라는 AWS Console에서 수동 생성했고 이후 application 배포만 GitHub Actions로 갱신했다. 새 production 인프라에 수동 방식을 계속 쓸지 IaC를 도입할지는 ADR-002의 남은 운영 선택이다.

## 2026-08-27 AGENTS·서비스 간 계약 정합성 리뷰

- Billing `AGENTS.md`와 통합 계약의 제품 범위, eligibility route, SigV4 목표, strict decode/digest, event 수신과 무료권 지급 분리, Reservation 순서, AttemptGroup 완료 조건, TrialClaim 3년·purge·backup 정책은 승인 내용과 대체로 일치한다.
- 구현 전 차단 이슈로 ADR-001 inbox schema/index에 `consumerScopeId`와 `ux_inbox_identity_scope_user_revision`이 아직 없고, ADR은 persisted disposition에 `DUPLICATE`·`REJECTED`를 열거하지만 PLAN-001은 APPLIED·STALE만 저장한다. ADR이 최종 기술 기준이므로 Phase 0에서 하나로 보정해야 한다.
- 실제 Identity publisher는 구현돼 있지만 현재 adapter는 Bearer workload JWT를 발급받아 전송하고 Identity ADR도 구 route를 적고 있다. 목표 계약의 VPC Lattice ECS task role·SigV4와 `/internal/v1/eligibility/trial/events`로 staging 연동 전에 별도 Identity 변경이 필요하다.
- Identity publisher는 HTTP status만 받고 `Retry-After`와 error code를 읽지 않는다. 429/503의 `Retry-After` 계약을 지킬 수 없고, eligibility route가 409 `COMMAND_PROCESSING`을 반환하면 이를 retryable processing이 아니라 conflict와 같은 dead-letter로 처리한다. PLAN-001처럼 eligibility 409를 event conflict로만 제한할지 producer를 확장할지 계약을 명확히 해야 한다.
- 앱→Learning Core 시험 생성 operation ID의 정확한 wire source가 통합 계약에서 충분히 명시되지 않았다. 제품 결정은 공개 시험 생성 요청의 필수 `Idempotency-Key`지만 현재 Learning Core controller에는 이 header가 없다. Reservation saga 연결 전 Learning Core 후속 변경과 contract test가 필요하다.
- 앱 종료 뒤 기존 시험을 이어풀지 않고 새 key·새 examId를 사용하는 승인 불변식은 CONTRACT_DECISIONS에는 있으나 AGENTS 핵심 불변식과 통합 계약 흐름에는 명시가 약하다. 실제 Learning Core `startNew`는 기존 진행 Session을 abandon하고 새 Session을 만들어 현재 동작은 맞지만 후속 계약 문서 보강이 필요하다.
- 리뷰 요청은 수정 승인이 아니므로 계약·ADR·AGENTS와 다른 서버 코드는 변경하지 않았다. PLAN-001 시작 전 Billing ADR Phase 0 보정, staging 연동 전 Identity transport/retry 변경, Reservation 연동 전 Learning Core public idempotency 계약 보강 순서가 적절하다.

## 2026-08-31 앱 서버 통합 구조 조사 반영

- 신규 Jira 없이 Learning Core·Identity·Billing 전체 구조 조사에 Billing `develop@39e424d` snapshot을 반영했다. 관련 현재 구현 문맥은 `TMI-115`와 Learning Core `TMI-116`이며 Jira 상태는 변경하지 않았다. 통합 draw.io와 본 문서는 Learning Core `docs/architecture`에 있다.
- Billing은 TrialEligibility projection, BenefitDefinition, TrialClaim·Grant·Ledger, Reservation·Allocation과 AttemptGroup/AttemptSession 최소 projection을 소유하고 문제·음성·채점 결과는 저장하지 않는 것으로 정리했다.
- reserve·confirm·cancel·status와 expiry는 구현됐고, AttemptGroup `OPEN→GRADING→COMPLETED/RETAKE_AVAILABLE` event consumer는 아직 미구현이다. 앱용 공개 Billing API와 Store 결제·구독은 현재 범위 밖이다.
- 강점은 strict internal decode, durable command idempotency, Transaction·unique/partial index·CAS와 append-only ledger다. 주요 남은 gate는 Identity SigV4 transport, AttemptGroup consumer-first 연동, actual Lattice/Mongo failure-injection staging E2E다.
- 애플리케이션·계약·Jira·외부 인프라는 변경하지 않았고 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.

## 2026-08-31 문서 계층·완료 보고 규칙

- 별도 Jira 없이 `AGENTS.md`에 계획·조사 문서의 6단계 읽기 계층과 구현 완료 보고 필수 항목을 추가했다.
- 결론별 파일 근거와 구현 사실·계획·추론 구분을 요구하며, 상세 목록과 표는 부록으로 보존한다.
- 구현 완료 후 변경·계약·테스트·위험·배포 전 확인·예상 밖 diff·다음 확인을 보고한다.
- Billing 애플리케이션과 외부 계약은 변경하지 않았고 Gradle 테스트를 실행하지 않았다.
