# PLAN-012 환불 테스트 준비 확인

## 1. 결론

- 2026-10-14 콘솔 읽기 확인: RevenueCat premium Offering에 Apple/Google 5종 연결 유지.
- Google 5종 모두 활성 구매 옵션 1개. 이전 10/13 Draft 기록보다 진행됐다.
- Apple 소모품 5종은 초안/제출 준비 중. 판매 승인이나 실제 sandbox 구매 성공으로 해석하지 않는다.
- RevenueCat webhook 미등록, Google 연결은 Valid credentials/Connected지만 No notifications received.
- 실제 환불 상태·금액/Google Orders 보완 조회 검증은 미완료. 이번 확인은 구매·환불 실행이 아니다.

## 2. 반드시 읽을 내용

| 대상 | 이번 직접 확인 | 남은 확인 |
| --- | --- | --- |
| RevenueCat catalog | premium1d/3d/7d/14d/28d 모두 양 Store 연결 | 앱 SDK 상품 조회·구매 |
| Google 상품 | 5종 모두 활성 구매 옵션 1개, 1일권 한국/활성 | 테스트 배포·테스터·실제 구매 |
| Apple 상품 | 5종 소모품, 초안/제출 준비 중 | sandbox 빌드·계정·실제 구매; 출시 심사는 별도 |
| Google RC 연결 | Valid credentials, Connected to Google, No notifications received | RTDN 시험 수신·실제 거래 알림 |
| RC→Billing | Integrations Active 0, Webhooks 등록 목록 없이 Create new webhook | 테스트 수신 URL·인증 규격 호환성 확인 후 등록 |
| SDK | No SDK version data yet | 실제 SDK 호출 여부. 이 문구만으로 테스트 거래 부재를 단정하지 않음 |
| Google Orders | 이번 실 API 호출 없음 | Billing용 인증·권한·주문 매핑·부분 반환 금액 증거 |

신규 구매 S2S 추적 OFF 유지. RevenueCat 계정 이메일 미확인 배너가 남아 있다. 자격증명 정상 표시는 Billing의 Google Orders 조회 권한 보장이 아니다.

## 3. 결정 및 권장 순서

새 제품 정책 결정은 없다. 테스트 환경 수신 URL/인증과 앱 테스트 구매 준비 → Store 테스트 구매 → 알림 및 sync 수렴 확인 → 전액/부분 환불 증거 확보 → PLAN-012 provider별 처리 규격 확정 순서를 권장한다. 부분 환불 테스트 지원 여부는 해당 주문에서 확인하고, 지원되지 않으면 무단 실결제로 대체하지 않는다. PLAN-011 진행은 유지하며 이번 조회로 PLAN-012 구현/배포/판매를 승인하지 않는다.

## 4. 위험·미확인

Store 테스트 계정/배포 트랙, 실제 구매 내역, Apple key/서버 알림 상세, Billing 배포/secret, Google Orders 권한은 이번 범위에서 검증하지 않았다. 원 반환액·통화·확정 시각을 실제 응답으로 대조하기 전 RC 가격 필드를 환불 금액으로 사용하지 않는다. 자동 환불 심사를 활성화하지 않는다.

## 5. 현재 코드

`PurchaseCore`는 기존 구매 reversal을 `REVERSAL_REQUIRES_PLAN_012` review로 보낸다. `RevenueCatPurchaseDecoder`는 `revenue_in_usd`를 원 Store 통화로 사용하지 않는다. 따라서 콘솔 설정만 완료해도 Billing 환불 권리 종료까지 구현 완료되는 것은 아니다. [P0 설계](../plans/PLAN-012-P0-MINIMAL-TECHNICAL-DESIGN-R1.md)는 후속 기준이다.

## 6. 근거·범위

- 직접 읽은 콘솔: RevenueCat project70a245ef Offering premium/Integrations/Webhooks/Apps/Google appc95ad9b387, Play app4974386136746920074 일회성 제품, Apple app6803419955 앱 내 구입.
- 이전 기록: [10/13 등록](REVENUECAT-GOOGLE-REGISTRATION-2026-10-13.md), [Store 환불 지원 조사](STORE-REFUND-CAPABILITIES-2026-10-06.md). 공식 환불 지원 문서는 이번 재열람이 아닌 이전 조사 근거다.
- 브랜치 feat/TMI-217-deletion-owner-retention-contract. 콘솔 읽기·코드 정적 조회 및 문서 기록만. 외부 설정/결제/환불/테스트 알림/DB/배포/Jira/commit/push 변경 없음. 고객 거래 원문·키 미수집. Gradle 미실행.
