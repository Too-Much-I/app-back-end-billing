# TMI-212 B — webhook-r1

2026-10-13 / feat/TMI-212-revenuecat-worker-reconciliation. 사용자 후속 구현 승인. 운영 활성화 승인이 아니다.

## 1. 결론

1. POST `/api/v1/payments/revenuecat/events`만 provider 인증 경계로 분리한다. 사용자 JWT나 workload JWT로 대체하지 않는다.
2. 설정된 public connector의 local port·host·POST·query 없음과 수신 flag를 검사한다. 전달 헤더로 port를 선택하지 않는다. OFF/다른 port/host는404.
3. Authorization 단일 값과 HMAC 단일 값을 검사한다. 원문256KiB 제한, UTF-8 JSON·압축 금지, delivery 시각±300초. 접수 commit 뒤 body 없는200, 지급 없음.
4. 기존 provider inbox를 r3로 명시 확장한다. 새 중복 상태 장부는 만들지 않는다. 정상 관측은RECEIVED, TEST/unknown은IGNORED, 판정 불명은case로인계한다.
5. 기존 worker/sync ON 차단은 유지한다. C/D·실거래·운영 알림/보존 검증 전 판매 및 실제 수신 활성화는 별도다.

## 2. exact 인증/입력

Authorization은 설정 문자열과 byte exact 상수시간 비교. HMAC secret은 dashboard 문자열의 UTF-8 bytes(임의base64decode없음), lookup key와 별개다. header grammar는 `t=(0|[1-9][0-9]{0,18}),v1=[0-9a-f]{64}`이며 전체문자열일치, 중복/공백/개행/unknown항목/overflow거절. HMAC 입력은 t+`.`+rawbytes. ±300초 경계 포함, event_timestamp_ms는 freshness에 사용하지 않는다. 재전송은 원eventId로 멱등 처리한다. 인증실패401, body형식400/상한413, version/environment/app불일치422, event의미충돌409, 저장확인실패503. provider 응답에는 사용자wrapper/원문/예외정보를 넣지 않는다.

strict JSON duplicate/trailing/coercion거절, depth32/string16KiB/number64. envelope api_version=`1.0`,event object,id/type문자열 필수. unknown optional은 무저장 허용. UTF-8외인코딩과BOM거절. 입력 의미digest는 고정 allowlist의 known field만 재귀 key정렬하여SHA256; null과생략구분, whitespace/property순서무관, 서명시각제외. aliases는 입력순서보존(동일event에서변경되면충돌).

정상 NON_RENEWING_PURCHASE는 설정app/store/product매핑, 동일canonicalUUIDv4 current/original reference, aliases가없거나해당reference만포함, quantity integer1, is_family_share=false, nonnegativeint64 구매/이벤트ms가현재이하일때만 PURCHASE관측. quantity/family/owner불명은추정하지않고검토. 통화/금액이둘다유효하면 price_in_purchased_currency를STORE_PAID로사용한다. 금액누락/null이면통화가알려져있어도money=null로접수한다. 금액이있는데통화가없거나금액/통화형식이잘못되면검토한다. price(USD)는사용하지않는다. CANCELLATION/REFUND_REVERSED/TRANSFER/ALIAS/구독/임시권리는직접지급·환불하지않고검토. TEST/새unknown type은ignored. 명시된다른env/app은422.

## 3. r3 저장·이관

기존source/case에 optional `receptionDigest`(SHA256)와`receptionLookupKeyVersion`(string)을추가,normalizedObservation은null허용. 새webhook행은두필드를항상저장하고core/sync생성시기존keyversion과대조한다. source IGNORED는counted=false/purgeAt필수,RECEIVED는object관측필수. 정상관측의기존digest는apply검증에유지하고 receptionDigest로webhook의미충돌을검사한다. null관측은apply불가,review case는reason/account연결만남기며원문/StoreID/alias를복제하지않는다. reference해결가능한review는기존pendingEvidence를증가하고case로이관해보존한다. 미해결owner는globalcase이며205미구매증명으로사용금지. 기존paid reader는globalcase가있는환경의paid상태를UNKNOWN으로표시하므로해결절차를준비한뒤활성화한다.

r2→r3: writer격리/ALL_BLOCKED후source와case validator만collMod,재시도시각collection이exactr2또는r3인지확인한다. index는불변. r1→r2이관을먼저수행한다. 자동DDL/구writerrollback없음. 알려진관측과ignored/review모두동일sourceeventunique로수렴하며case는TTL없음. ignored만120일TTL. 미해결보존/운영검토및암호화재조회reference지속저장은C/D후속,수신ON전점검한다.

## 4. 설정·검증 경계

`billing.payment.provider.ingress-enabled` 기본false. ON은별도authorization/signing-secret/lookup-key(base64)/lookup-key-version/project/host/apps 및기존public connector·Mongo transaction/schema·recovery fence필수다. host는hostname만,port는localconnector검증. app목록은store와StoreproductID목록의서버설정이며실catalog를대체하지않는다. runtime은schemaverify-only,실키는환경주입한다. OFF에서는키/DB접수bean무의존.

단위/HTTP경계/Mongo검증을수행하고실행결과는WORKLOG에기록한다. fake서명/가짜event는실제Storefixture수락이아니다. 자동외부호출/지급worker/Slack발송은이번B범위아니다.

공식규격 재확인: [RevenueCat Webhooks](https://www.revenuecat.com/docs/integrations/webhooks), 2026-10-13. dashboard HMAC 활성화와 실제 delivery 수신은 미검증이다.
