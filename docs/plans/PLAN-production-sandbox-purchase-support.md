# 운영 주소의 SANDBOX 구매 지원 — 영향 검토

> 2026-10-18 후속 사용자 결정: 전용 계정 제한·별도 이용권·계정 모드를 **두지 않는다**. 동일 운영 구매 계정과 timeline에서 검증된 두 환경 구매를 처리하며 Store 환경 표시는 거래 식별용으로 보존한다. 아래는 최초 검토 이력이고, 계정 선택/허용 목록 결정 대기는 해소됐다. 현재 구현·이관 기준은 [운영 SANDBOX 구매 안내](../runbooks/billing-production-sandbox-purchases.md) 및 ADR-004 최신 승인을 따른다.

- 날짜: 2026-10-18
- 브랜치: codex/billing-test-deployment
- 상태: 구현 전 영향 보고. 제품 코드·서버·DB·RevenueCat 설정 변경 없음.

## 1. 5줄 결론

1. 사용자는 운영 주소에서 실제 과금 없는 테스트 구매 지원을 요청했다.
2. 현재 구매 반영·조회·시험 예약은 서버당 하나의 payment environment를 사용하므로 웹훅 허용값만 늘려서는 완료되지 않는다.
3. Store가 검증한 SANDBOX 거래를 PRODUCTION으로 다시 표시하거나 기존 운영 환경값을 바꾸지 않는다.
4. 테스트·심사 전용 계정에 한정하는 방향이며, 대상 계정과 계정별 환경 선택·고정 방식은 아직 확정하지 않았다.
5. 기존 실제 구매는 유지한다. 구현·스키마 이관·운영 활성화 완료가 아니다.

## 2. 사용자가 반드시 읽어야 하는 내용

운영 서버 주소와 Store 거래 환경은 다른 개념이다. 같은 주소로 양쪽 거래를 받을 수 있지만 현재 구현에는 그 경계가 없다. 웹훅만 허용하면 이후 구매 반영에서 거절되거나 운영 환경만 읽는 이용권·예약 로직에서 사용할 수 없다.

기존 계약의 SANDBOX→staging / PRODUCTION→production 매핑을 제한적으로 확장하는 작업이다. 사용자 API 경로·인증·멱등성을 유지하는 것을 목표로 하되, purchase-account가 구매 전에 반환하는 환경별 연결 ID와 서버의 계정 선택을 먼저 정해야 한다. 클라이언트 environment 주장만으로 권리를 지급하지 않는다.

## 3. 사용자가 결정해야 하는 사항

- 운영에서 SANDBOX 이용을 허용할 테스트·심사 전용 계정. 로그인 토큰·비밀번호를 받지 않는다. 실제 설정 식별자는 Identity UUID이며 Firebase UID와 구별한다.
- 전용 계정 준비 전에도 비활성 기본값으로 개발할 수 있지만, 일반 사용자 전체를 임의로 허용하지 않는다.
- 동일 계정의 실제 구매와 테스트 구매를 함께 사용하는 기능은 이번 최소 범위로 가정하지 않는다. 해당 필요가 있으면 조회·예약 우선순위를 추가 결정해야 한다.

## 4. 주요 위험과 미확인 사항

- 계정 허용 목록 변경으로 기존 예약·환불·탈퇴 처리가 다른 환경으로 향해서는 안 된다. 이미 저장된 source의 환경은 계속 유지해야 한다.
- 운영 workload 환경과 Store 환경의 현재 일대일 검증은 명시적인 제한 예외가 필요하며 JWT 신뢰 환경을 SANDBOX로 바꾸는 방식은 사용하지 않는다.
- 구매·원장뿐 아니라 환불 fan-out, 탈퇴, 재조회, 삭제 보존까지 환경별 처리 누락을 검토해야 한다.
- 별도 schema/control/product 준비 및 기존 자료 이관 필요성은 설계 후 확인한다. 기존 환경 필드 일괄 변경·기존 자료 이동은 하지 않는다.
- 심사 계정 정책과 Store별 실제 테스트 구매 경로는 아직 검증하지 않았다. RevenueCat 기본 TEST 이벤트는 구매 검증을 대체하지 않는다.

## 5. 다음 구현 순서

1. 전용 계정 범위와 구매 전 계정 환경 선택 규칙 확정.
2. 위 제한 예외를 ADR에 반영하고 schema·기존 자료 호환 방식 명시.
3. 기본 OFF로 구매 계정→검증된 거래 반영→조회→예약을 연결. 환불·탈퇴·재조회도 같은 저장된 환경 기준 사용.
4. 비허용 사용자·교차 환경 거래·중복·환불 격리·허용 해제 후 기존 작업 회귀 및 전체 clean test.
5. 별도 승인 후 운영 설정·웹훅 라우팅과 실제 Store SANDBOX E2E 적용.

## 6. 정적 근거

- `RevenueCatWebhookDecoder`: 환경/app 검사 후 TEST 분기. 단순 TEST 성공으로 실제 결제 지원을 주장할 수 없다.
- `PurchaseCore`: PaymentProperties 환경 고정, observation/receipt 환경 일치 및 환경별 account/ref/lifecycle 조회.
- `PaidEntitlementReader`: 서버 환경으로 control·account·purchase 조회.
- `MemberOperationRecovery`: production→PRODUCTION, test/staging→SANDBOX 생성자 매핑과 MEMBER 증거 확인.
- `PaidReservationService`: PaymentProperties 환경으로 구매·계정 조회 및 source 검사.
- `PaymentProviderWorkerConfiguration`: 단일 환경의 catalog·decoder·REST reader 구성.

이번 검증은 소스 정적 확인과 diff 검사에 한정한다. 실행 테스트·제품 구현·운영 변경은 하지 않았다.
