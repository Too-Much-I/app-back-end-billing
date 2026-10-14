# TMI-212 B webhook 구현 안내

> 후속 [C1 worker](TMI-212-source-worker.md)에서 purchase-core-r4와검증source→원장→sync완료를추가했다. 현재schema는r4이며아래r3설명은B단계이관기록이다. C2/D·자동scheduler·실환경활성화는여전히남아있다.

2026-10-13 / feat/TMI-212-revenuecat-worker-reconciliation

## 구현 범위

- [인증](../../src/main/java/web/tosunsaeng/billing/domain/payment/provider/RevenueCatWebhookAuthentication.java): Authorization 단일값, raw-body HMAC, 중복 header·시간±300초·overflow 차단.
- [정규화](../../src/main/java/web/tosunsaeng/billing/domain/payment/provider/RevenueCatWebhookDecoder.java): UTF-8 strict JSON, 최소 known field digest, 원구매자·Store·상품·환경·수량 검증, 금액 불명 허용, 미지원/검토 분리.
- [HTTP 수신](../../src/main/java/web/tosunsaeng/billing/domain/payment/provider/RevenueCatWebhookFilter.java)과 [별도 security chain](../../src/main/java/web/tosunsaeng/billing/global/config/security/RevenueCatWebhookConfiguration.java): 정확한 host/port/route, 256KiB, commit 후 body 없는200. 사용자 JWT 경로와 분리, 수신에서 apply/외부조회 없음.
- [PurchaseCore](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCore.java): 기존 inbox에 접수. 같은event 중복/no-op, 의미충돌409. ignored는pending없이120일TTL, review는TTL없는case에pending을보존. 모호한환불을직접종료하지않음.
- [Schema](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCoreSchema.java): r3 명시이관, 기존r1/r2필드보존·nullable관측/IGNORED/수신digest/keyversion추가. sync생성검사도null관측과keyversion구분.

## 이관과 설정

기존 writer와수신을중지하고ALL_BLOCKED확인. r1이면`upgradeMoneyEvidenceUnderMaintenance`로r2→`upgradeWebhookUnderMaintenance`로r3. r2이면두번째만. 새DB는`installUnderMaintenance`로r3. 이관중중단은exactr2/r3조합을검증해재개,다른drift면중단. index/drop/backfill없음,자동DDL없음. 구writer가새행을이해하지못하므로단순rollback금지.

`BILLING_PAYMENT_PROVIDER_INGRESS_ENABLED=false` 유지. ON은별도host/project/Authorization/signing secret·base64 lookup key/keyversion,`billing.payment.provider.apps.<appId>.store/products`매핑과public connector·transaction/recovery/schema조건필수다. appId에점등이있으면Spring map bracket표기를사용한다. 실제키·설정은이번에생성/조회하지않았다. schema는runtime verify-only다.

실제활성화전에도C/D worker·암호화재조회reference/보존·alert/검토절차와실Storefixture를확인한다. 특히quantity/is_family_share가없는실이벤트는현재추정하지않고검토한다. owner미해결globalcase는기존reader의환경별paid UNKNOWN을유발하므로운영해결절차없이수신을열지않는다. ingress만ON으로실판매가가능하다는뜻이아니다.

## 남은 범위

C의공통worker/lease/quota/sync완료연결과D의누락복구·reference영속화·알림·미해결해소/보존. 사용자sync ON기동차단은유지한다. 환불권리종료/paid시험입장·무료권정책은PLAN011/012별도다. 자동외부호출·실DB이관·배포·Jira쓰기·commit/push없음.

## 검증

최종 `./gradlew clean test bootJar`: **1,324통과/실패0/오류0/skip0**, 직전A대비56추가. `git diff --check` 통과.

가짜Authorization/HMAC/JSON, 실제Spring security chain(MVC), 로컬Mongo replica-set으로검증한다. receipt의중복·unknowncommit/동시성·review pending/탈퇴·TTL후case멱등성과이관재개를포함한다. 실행결과는WORKLOG/CURRENT_STATE에기록한다. 실제ALB/TLS/delivery·provider/Store/운영키검증과는구분한다.
