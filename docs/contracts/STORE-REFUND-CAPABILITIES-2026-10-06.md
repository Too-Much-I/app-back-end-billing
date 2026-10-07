# Store 실제 환불·부분 환불 지원 재확인

## 1. 5줄 결론

1. Google Play Console은 인앱 구매의 전액 및 비율/금액 지정 부분 환불을 지원한다.
2. Apple은 고객이 요청하고 Apple이 최종 심사한다. 개발자가 고객 대신 금액을 지정해 직접 환급하는 운영과 다르다.
3. Apple API의 GRANT_PRORATED는 소모품에도 가능한 심사 선호/사용률 제공이며 확정 환급 명령이 아니다.
4. RevenueCat Refund Control은 현재 부분 환불 선호와 거래별 사용률/usage event 전송을 지원하지 않는다.
5. 문의 접수·팀 검토는 유지하되 양 Store에 같은 금액을 직접 환불해줄 수 있다고 약속하면 안 된다. 이번은 공식 문서 조회이지 실제 환불 실행/E2E가 아니다.

## 2. 반드시 읽을 내용

| 구분 | Google consumable one-time | Apple consumable |
| --- | --- | --- |
| 실제 실행 | Play Console 주문 관리에서 주문 선택 후 환불 | 고객이 reportaproblem 또는 앱 StoreKit 환불 요청 UI로 신청, Apple 심사 |
| 부분 환불 | 웹 Console에서 비율 또는 세전 금액 입력 | API로 비례 환불 선호와 사용률을 전달할 수 있으나 Apple 결정 |
| 팀 판단 의미 | 지원되는 주문/결제수단이면 운영자가 금액을 집행 가능 | 상담·판단은 가능하지만 임의 금액의 환급 확약 불가 |
| RevenueCat | 비구독 환불 dashboard/API 지원 명시. 부분 금액 처리·전달은 별도 확인 | 최종 환불 탐지 및 심사 선호 전달. 현재 부분 선호 미지원 |

Google 웹 절차: 주문 관리 → 검증한 주문 선택 → 환불 → 부분 환불 활성화 → 100% 미만 비율 또는 주문 총액 미만 세전 금액 → 사유 → 제출. 세후 금액은 자동 계산한다. 일부 결제수단은 부분 환불 미지원이고 환불 실행은 되돌릴 수 없다. 담당자는 주문 관리 권한이 필요하다. 전액 환불과 부분 환불은 별도 동작이다.

Apple 고객 절차: reportaproblem.apple.com 로그인 → 환불 요청 → 사유 → 구매 선택 → 제출. Apple은 요청 업데이트까지 24~48시간을 안내하고 승인 후 실제 결제수단 환급은 추가 시간이 걸릴 수 있다고 명시한다. 우리 2영업일 1차 응대와 혼동하지 않는다.

## 3. 사용자 결정이 필요한 부분

- 기본 운영 제안: Google은 팀 검토 후 Console에서 지원되는 환불, Apple은 문의 검토 후 Apple 신청 안내. Store 직접 요청도 유지.
- Apple 비례 환불 의견 전달을 구현할지는 별도 승인 필요. 기존 자동 Refund Control OFF를 임의로 켜지 않는다.
- 부분 환불 시 남은 이용권을 종료할지 등 entitlement 의미, 실제 환불 금액/시각/중복 reversal 기록과 정합성 복구는 미완성 계약이다. Store 지원 확인을 구현 완료로 간주하지 않는다.

## 4. 위험·미확인

- Apple GRANT_PRORATED가 원하는 원화 금액을 보장하지 않는다. 기간형 무제한 상품의 consumptionPercentage 계산도 아직 승인된 공식이 없다.
- Apple consumption 응답은 고객 동의가 있어야 하며 CONSUMPTION_REQUEST 수신 후 12시간 이내다. 팀 2영업일 응대를 기다리는 동기 절차로 설계하면 기한을 넘길 수 있다. 프론트 약관 작성과 별도로 적법한 동의/철회·개인정보 고지 확인 필요.
- RC Google Console/Google발 환불 탐지는 최대24시간 걸릴 수 있다. 철회 없는 환불/미승인 구매 자동 환불에 대한 탐지 제한도 문서에 있으므로 consumed one-time 부분 환불의 정확 상태·금액·시각은 실제 fixture로 검증한다. 구독 관련 설명을 소모품에 무조건 동일 적용하지 않는다.
- RC Apple 비구독 환불에는 server notification, consumable 탐지에는 In-App Purchase Key 설정을 확인해야 한다. 실제 계정 설정은 이번에 조회하지 않았다.
- Google은 자체 환불 정책 수립·고지 및 법규 준수 책임을 명시한다. 팀 건별 판단이 법적 기준과 고지를 대체하지 않는다.

## 5. 현재 설계에 미치는 영향

Store의 금전 환불과 Billing 기간권 종료는 별개다. 문의/운영자 판단만으로 final reversal을 만들지 않는 계약을 유지한다. RC Refund Control은 기간형 이용권의 실제 시험 완료를 알지 못한다. 현재 문서의 deliveryStatus는 RC transaction 존재 여부 기준이므로 AI 피드백 제공 증거로 오해하지 않는다.

현재 RC 문서는 Apple뿐 아니라 Google orders.reviewrefund에 대한 선호 응답도 설명한다. 과거 조사보다 기능 설명이 확장됐으나 Billing이 일반 REFUND_REVIEW wire event를 받을 수 있다는 증거는 아니다. 기존 자동 심사 OFF/별도 승인 gate를 유지한다.

## 6. 공식 근거 — 2026-10-06 직접 열람

- [Google 주문 관리·환불](https://support.google.com/googleplay/android-developer/answer/2741495?hl=en): 인앱 부분 환불 절차/세금/결제수단 제한/취소 불가/정책 책임.
- [Apple 고객 환불 요청](https://support.apple.com/ko-kr/118223): 신청 방법·24~48시간 업데이트·환급 추가 소요.
- [Apple refundPreference](https://developer.apple.com/documentation/appstoreserverapi/refundpreference): GRANT_FULL/DECLINE/GRANT_PRORATED, consumable 등 사용률·최종 심사 참고 요소.
- [Apple Send Consumption Information](https://developer.apple.com/documentation/appstoreserverapi/send-consumption-information): API1.19+, v2, 동의,12시간,202는 정보 수신이지 환불 승인 아님.
- [RevenueCat Handling Refunds](https://www.revenuecat.com/docs/subscription-guidance/refunds): Apple 직접 대리 환급 불가·비구독 탐지 조건, Google 비구독 dashboard/API 환불 및 탐지 지연/제한.
- [RevenueCat Refund Control](https://www.revenuecat.com/docs/customers/refund-control): 부분 선호 미지원, 거래별 사용률/usage 미수집·미전송, Apple/Google 최종 판단.
- [RevenueCat 설정](https://www.revenuecat.com/docs/customers/refund-control/configure-refund-policies): 환경 알림·동의·프로젝트 정책. 이번에 설정 변경하지 않음.

법적 분류·법정 반환액 확정이 아닌 provider 기술/운영 지원 조사다. 실제 주문 조회/환불, 고객 정보 전송, 계정 설정, 테스트 결제는 수행하지 않았다.
