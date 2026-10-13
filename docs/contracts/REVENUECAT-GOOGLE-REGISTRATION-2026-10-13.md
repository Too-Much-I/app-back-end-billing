# RevenueCat Google 상품 등록·콘솔 확인

- 날짜: 2026-10-13
- 승인: 사용자 「레비뉴캣 들어가서 아까 검증못한거 확인해주고 구글은 상품은 전에 만들었잖아 그거 등록까지 해줘」 및 로그인 완료 알림
- 결과: 토선생 project `70a245ef`의 Google 상품 5종을 Consumable로 import하고 기존 `premium` Offering의 동명 custom package에 연결·저장했다. 저장 후 상세 화면에서 iOS/Play 두 상품씩 확인했다.

## 확인한 설정

- iOS `app3987acee69`, Android `appc95ad9b387` 모두 Bundle/Application ID `com.toteacher.app`이다.
- 각 Public SDK key(appl_/goog_)를 콘솔에서 확인했다. 키 원문은 이 문서/저장소에 복제하지 않는다. 서버 Secret API key를 열거나 변경하지 않았다.
- Google credentials: Valid credentials 표시. developer notifications는 Connected to Google이나 No notifications received. 실제 RTDN 수신 성공은 미검증이다.
- Track new purchases from server-to-server notifications는 OFF를 유지했다.
- Webhooks 화면에 등록 항목 없이 Create new webhook만 있다. 테스트/운영 Billing webhook은 미등록이다. 이번에 URL/secret을 생성·등록하지 않았다.
- 계정 이메일 미확인 배너가 남아 있다. SDK Compatibility에는 No SDK version data yet가 표시된다.

## 매핑

Offering `premium` / resource `ofrng51a9b79a34`. 아래 ID는 Store 상품 및 custom Package identifier이며 prod...는 RevenueCat 내부 resource ID다.

| Package / Store product ID | Google RC resource | import 당시 Google 상태 |
| --- | --- | --- |
| premium1d | prod61b23b6254 | Inactive (상품 목록 Not published) |
| premium3d | prod5b51dba40d | Draft |
| premium7d | prodd55bc1da76 | Draft |
| premium14d | prod83987f38b4 | Draft |
| premium28d | prod903d4e96c7 | Draft |

5종 모두 Consumable로 import했다. Entitlement Attach는 수행하지 않았으며 Billing 기간권 판정 계약을 유지한다. 기존 Apple 매핑, default Offering, Test Store 및 판매 상태는 변경하지 않았다. Play 연결은 저장된 상세에서 SDK v6+로 표시되며 모바일 기술 스택별 실제 호환 SDK/상품 조회를 검증해야 한다.

## 남은 검증

Google 판매/테스트 구매 준비·SDK 실제 상품 조회/구매, Apple 나머지 상품 Missing Metadata(1일 Ready to Submit), Billing sync/기간권/웹훅 구현·배포·sandbox E2E가 남는다. RevenueCat 등록 완료가 실제 결제·서버 지급 완료를 뜻하지 않는다. 앱은 premium Offering을 명시적으로 선택하고 Billing purchaseAccountRefId로 식별한 뒤 구매한다.

이번 작업은 콘솔 상품 import/Offering 저장과 문서 기록이다. Store 판매 활성화·배포·DB·Jira·타저장소·commit/push 변경 없음. Gradle은 실행하지 않았다.
