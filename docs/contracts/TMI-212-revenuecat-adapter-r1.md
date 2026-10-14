# TMI-212 A — RevenueCat adapter-r1 구현 규격

- 2026-10-13, feat/TMI-212-revenuecat-worker-reconciliation. 사용자 구현 요청의 선행 A 단계. 외부 API/계정 설정/운영 활성화 없음.
- 상위 [3차 계획](../plans/PLAN-010-phase-3-revenuecat-worker-reconciliation.md). B ingress·C worker·D reconciliation과 배포는 별도 완료 조건이다.

## 1. 5줄 결론

1. 읽기 전용 v2 adapter는 고정 RC host·project와 Billing reference를 사용한다. 페이지 전체를 strict decode한 뒤 검증된 관측/검토 사유를 반환한다.
2. current/original customer, product mapping, store/environment, ownership, quantity/time/transaction을 모두 검증한다. API USD revenue를 실제 결제 금액으로 변환하지 않는다.
3. Store ID는 HMAC 사업key로, API 관측은 별도 namespace와 안정된 사실 fingerprint로 식별한다. 원문/credential은 log/toString에 노출하지 않는다.
4. 검증 구매는 금액 null이어도 지급 가능. 이후 null→확정금액은 MONEY_CONFIRMED 원장과 Purchase projection만 원자 보완한다. 재지급/기간조정 없음.
5. runtime bean/worker/ingress는 아직 연결하지 않는다. sync ON 차단·기본OFF 유지, 전체 TMI212 완료로 보고하지 않는다.

## 2. exact 입력과 결과

목록 GET `/v2/projects/{project}/customers/{ref}/purchases?environment={sandbox|production}&limit=100[&starting_after=cursor]`, 단건 GET `/v2/projects/{project}/purchases/{id}`. ref는 canonical lowercase UUIDv4, project/id/cursor는1~255 opaque이며 URL 인코딩한다. response는1MiB/깊이32/문자열16KiB, duplicate/trailing token/숫자 coercion 거절. next_page는 같은 host·project·customer 목록 path, 승인된 query만 허용하며 cursor 재구성. 페이지당100개 상한·cursor 반복은 호출자가 전체 순회에서 검증한다.

Purchase 필수: object=purchase, id/product_id/store_purchase_identifier/customer_id/original_customer_id 문자열, environment=sandbox|production, store=app_store|play_store, ownership=purchased, quantity integer1, purchased_at nonnegative int64 milliseconds(미래 거절), status=owned|refunded. 숫자 Store ID 예시는 허용 근거로 사용하지 않는다. unknown optional field는 무저장 허용한다. 목록 object=list, items array, next_page null|string 필수. type/필수 누락은 계약 오류, 지원 외 enum/귀속/상품 불명은 검토 대상으로 반환한다. 실제 sandbox 타입/ID 대응은 별도 gate.

서버 주입 catalog 매핑: RC productId→RC appId·Store·Store productId. PURCHASE/REFUND 관측은 검증된 ownerRef와 원래 구매 시각, money=null. refunded는 코어의012 handoff이며 실제 환불 실행 아님. API event key는 별도 HMAC namespace(project/env/RC id/고정 순서 핵심 사실 SHA256), webhook key와 분리. timestamp 조회시각/무관 optional field는 포함하지 않는다.

HTTP: GET only/redirect 없음/connect2초/전체 body5초/허용host 고정/response1MiB. header Retry-After는429/503만 정수초 또는HTTP-date, 중복·잘못된값·24시간초과는 운영검토 분류.401/403 AUTH,404 NOT_FOUND,423/429/5xx 및network RETRY,기타/잘못된200 CONTRACT. 안전한 오류코드만 보존하며 payload/URI/키를 예외 cause에 넣지 않는다. 이 adapter 자체가 retry/scheduler를 실행하지 않는다.

## 3. 구매 금액 보완과 명시 schema 이관

기존 purchase-core-r1에서 ledger type MONEY_CONFIRMED만 추가한r2 manifest. maintenance API는 writer격리+모든control ALL_BLOCKED+기존r1 exact검증 후 payment_ledger validator만 collMod한다. index/drop/데이터 backfill 없음. fresh install은r2, runtime은r2 exact verify-only. 구r1 startup은r2를거절하므로maintenance중구writer drain필수. rollback은구schema로자동되돌리지않는다.

known money와incoming known이 다르면기존 IMMUTABLE_FACT_CONFLICT. known→null은정보손실없이중복수렴. null→known은unique `{purchaseId}:MONEY_CONFIRMED` 원장과Purchase.money/version/lastVerifiedAt·retentionExpiresAt(기존과 now+5년 중늦은값)를같은Tx에저장한다. 최초 money가있는구매는기존 PURCHASE_OBSERVED에기록하므로추가효과없음. entitlement/timeline은변경하지않는다. sourcepending완료도같은Tx,unknowncommit/동시보완은기존unique/CAS로수렴. 역전/미해결환불은보완전에기존차단규칙을따른다.

## 4. 최소 reference 암호화

암호화 helper는AES-256-GCM,nonce12byte(매호출SecureRandom),tag128bit,revision1/keyId를사용한다. AAD는길이prefix된env/project/app/transactionHmac로귀속을묶는다. plaintext는최소RCpurchaseId만. lookupHMAC/서명키와독립key. DB/HTTP에자동저장하지않으며실제metadata collection/보존worker와key주입은B/C연결전명시규격이필요하다. 이helper구현만으로보존/삭제완료가아니다.

## 5. 확인과 후속

가짜response/로컬HTTP·Mongo replica-set로검증한다. 실제RC/Store/키호출없음. provider실fixture·SDK매핑·pagination관측·요청budget/lease/ingress/운영보존은후속. 이문서는A구현규격이며전체TMI212완료/판매승인아님.
