# 운영 구매 검토 2건 읽기 진단 — 2026-10-22

## 확인 결과

MongoDB Atlas의 to-teacher-billing-prod.payment_purchase_cases에서 알림과 같은 조건(environment=PRODUCTION, resolvedObservationKey 미존재)으로 조회하여 2건을 확인했다. 전체12건 중 기존 종결10건과 구분된다.

- 생성시각(KST): 2026-10-20 11:41:30.923 / 11:42:22.632.
- 두 건 모두 state=REVIEW_REQUIRED, reason=OWNER_UNRESOLVED.
- accountId/lifecycleId/normalizedObservation=null, counted=false.
- reviewCandidate/purchaseId/resolvedObservationKey 필드 없음.
- 따라서 단순 누적 이력이 아니며, 계정/검증구매 연결이 없는 미해결 수신 검토다. 실제 결제 미지급2건 또는 사용자2명으로 단정하지 않는다.

## 코드 대조와 한계

RevenueCatWebhookDecoder는 NON_RENEWING_PURCHASE의 app_user_id가 canonical owner가 아니거나 original_app_user_id와 다르거나 aliases가 다른 owner를 포함하면 OWNER_UNRESOLVED로 보류한다. 현재 case만으로 이 세부 원인 및 실제 Store 환경/거래를 복원할 수 없다. 운영 DB 환경명만으로 Store 실결제 여부를 판정하지 않는다.

PaidEntitlementReader는 계정 미귀속 증거가 무관한 계정을 차단하지 않도록 한다. 이번2건은 accountId=null/counted=false이며 특정 계정 pendingEvidence 증가 근거가 아니다. 이 결과가 해당 실제 구매의 별도 REST 확인/이용권 지급 완료를 증명하지 않는다.

다음 대조는 RevenueCat 원본 event와 저장 event HMAC의 exact 연결, app_user_id/original/aliases 및 Store transaction을 검증된 purchases/원장/기간권과 비교하는 것이다. 시각만으로 구매에 귀속시키거나 종결하면 안 된다.

DB 읽기 및 코드 확인만 수행. DB쓰기/종결/재지급/설정/배포/외부 메시지 전송 없음. 사용자·거래ID 및 해시 원문은 이 기록에 저장하지 않았다.

## RevenueCat 원본 대조 완료

2026-10-22 추가 읽기 확인: RevenueCat Overview의 Sandbox data OFF에서 최근28일 Revenue $0, No live transactions to show. 운영 webhook의 해당시각2개 요청은 모두 APP_STORE/SANDBOX/NON_RENEWING_PURCHASE, 상품 premium1d/premium7d다. 두 원본의 decoder FIELDS 선택·정렬 JSON SHA-256을 계산해 DB receptionDigest와 각각 일치(2/2) 확인했다. 단순 시각 추정이 아니다. 원문/사용자/거래ID/해시를 파일에 보관하지 않았다.

두 이벤트는 current app_user_id가 UUID이고 original_app_user_id는 동일한 RCAnonymousID이며 aliases에 두 값이 함께 있다. decoder의 owner=null 및 OWNER_UNRESOLVED 조건과 일치한다. 운영 webhook 설정은 Both Production and Sandbox이므로 테스트 구매가 운영 receiver에 전달된 상황이다. 운영 DB의 environment=PRODUCTION은 실제 Store 결제를 뜻하지 않는다. 이번 두 검토 건은 실제 과금 구매가 아닌 sandbox 테스트 구매임을 확인했다.

설정·case·금융 원장은 변경하지 않았다. 테스트 수신 정책 변경 및 이2건의 감사 종결은 이번 읽기 진단에 포함하지 않는다.

## 앱/Billing 원인 대조

GitHub app-front-end 기본 브랜치의 revenuecat.ts/use-pass-purchase.ts를 읽기 확인했다. configureOnce는 Purchases.configure({apiKey})로 appUserID 없이 초기화하고 logInStoreCustomer는 이후 await Purchases.logIn(purchaseAccountRef)를 호출한다. 구매 함수는 purchaseIdentity.ensure 완료를 기다린 뒤 purchasePackage를 호출하므로 현재 코드상 로그인 대기를 빠뜨린 흐름이 아니다. SDK 최초 익명ID→결제ref 연결과 Billing의 current=original 및 aliases 모두 동일값 요구가 충돌한다. 실제 원본 두 건의 original 익명/current UUID/aliases 양쪽 포함이 이 조건을 충족한다. 당시 배포 앱 바이너리 자체는 독립 확인하지 않았다.

REST RevenueCatPurchaseDecoder도 customer_id/original_customer_id가 모두 ownerRef와 같아야 하므로 같은 소유정보가 API로 전달되면 재조회도 OWNER_UNRESOLVED가 된다. 단순 자동 재시도로 해결된다고 보장할 수 없다. 새 초기화에 appUserID를 지정하는 개선만으로 기존 RevenueCat original ID가 소급 변경되는 것도 보장하지 않는다. 정상 익명 연결과 타계정 구매 이전의 구분 계약을 유지해야 한다.

운영 Sandbox 수신은 billing-production-sandbox-purchases.md의 2026-10-18 사용자 승인 정책과 실제 적용 기록에 따른 의도된 설정이다. 원인 진단상 설정 실수로 취급하거나 임의로 OFF하지 않는다. 제품/앱/정책/DB/배포 변경 없이 진단만 수행했다.
