# Store 상품 등록 결과 — 2026-09-16

## 1. 5줄 결론

1. Apple에 승인된 fixed-term 이용권 5종을 소모품으로 생성했다.
2. 한국어 표시명·설명과 대한민국 기준 9,000/19,000/29,000/49,000/69,000원 가격을 저장했다.
3. 사용자 추가 요청에 따라 5종 모두 판매 가능 국가를 대한민국 1개로 지정했다.
4. Apple 상태는 초안/제출 준비 중이며 심사 제출·출시는 하지 않았다.
5. Google은 결제 권한이 포함된 APK 필요 안내로 상품 생성이 막혀 있어 0건이며 Android 빌드 후속이 필요하다.

## 2. 반드시 읽어야 하는 내용

- 상품 레코드 생성은 결제 구현 완료나 판매 개시가 아니다. RevenueCat/Billing 매핑·SDK·sandbox 검증은 별도다.
- Apple의 소모품은 기간 만료를 자체 관리하지 않는다. [승인 결제 계약](FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md)대로 Billing이 duration을 적용한다.
- 판매 국가 지정과 심사 제출은 구분했다. 대한민국 외 자동 환산 가격이 가격표에 있어도 다른 국가를 판매 대상으로 선택하지 않았다.
- Google의 예정 ID는 아직 Store에 등록되지 않았다. 아래 Apple ID를 Google 등록 완료값으로 취급하지 않는다.

## 3. 사용자 결정사항

- 합의된 상품·금액을 그대로 사용했고 2026-09-16 추가 요청으로 최초 판매 지역을 대한민국으로 한정했다.
- Apple product ID는 하이픈 없는 `premium1d`, `premium3d`, `premium7d`, `premium14d`, `premium28d`로 생성했다. Google도 동일 ID로 등록할 예정이나 현재 미생성이다.
- 이번 작업에서 새로운 가격·자동갱신·프로모션·환불 정책을 정하지 않았다.

## 4. 주요 위험과 미확인 사항

- Google UI: “앱에 아직 일회성 제품이 없습니다.” / “일회성 제품을 추가하려면 결제 권한을 APK에 추가해야 합니다.” / “새 APK 업로드”. 현재 생성 버튼 없음. 결제 권한 포함 Android 빌드의 업로드·인식이 선행돼야 하며 앱 저장소/배포는 이번 범위에서 변경하지 않았다.
- Apple은 첫 소모성 앱 내 구입을 새로운 앱 버전과 제출하라는 안내가 있다. 심사 스크린샷과 실제 결제 앱 빌드를 아직 추가하지 않았다.
- Apple 앱 목록에 Developer Program 사용권 계약 업데이트 검토 안내가 있었다. 계약 동의는 수행하지 않았고 계정 소유자가 별도로 확인해야 한다.
- 세금 카테고리는 기본값 ‘상위 앱과 일치’를 변경하지 않았다. 적합성·유료 앱 계약·정산/세금 상태는 이번 작업에서 검증하지 않았다.
- Store sandbox 구매·환불·RevenueCat 수신·Billing 지급 E2E 미실행. 기존 출시 gate 유지.

## 5. 현재 작업과 다음 단계

- Apple: 기존 상품이 없는 목록에서 5종 생성, 한국어 현지화와 가격 저장, 대한민국만 선택 후 메인 저장. 최종 목록에서 초안 5개/소모품/제출 준비 중을 확인했다.
- 각 상품의 ‘현재 가격’을 열어 한국 가격이 승인 금액과 일치하는 것을 확인했다. 지역 선택 시 전체 해제 뒤 대한민국만 체크해 1개 선택을 확인했다.
- Google: 결제 권한을 포함한 새 Android 빌드 업로드 후 같은 상품 목록에서 생성 가능 여부를 확인한다. 생성 가능해지면 일회성 상품 5종·한국 판매·같은 가격을 등록한다.
- 후속 RevenueCat import/Offering과 Billing catalog는 별도 승인 구현·설정 단계다. 이번에는 변경하지 않았다.

## 6. 부록 — 실제 생성한 Apple 상품

| offer code | Store product ID | 한국어 표시명 | duration | 한국 가격 | Apple 상태 |
| --- | --- | --- | ---: | ---: | --- |
| PREMIUM_1D | premium1d | 토선생 프리미엄 1일 이용권 | 24시간 | 9,000원 | 초안/제출 준비 중 |
| PREMIUM_3D | premium3d | 토선생 프리미엄 3일 이용권 | 72시간 | 19,000원 | 초안/제출 준비 중 |
| PREMIUM_7D | premium7d | 토선생 프리미엄 1주 이용권 | 168시간 | 29,000원 | 초안/제출 준비 중 |
| PREMIUM_14D | premium14d | 토선생 프리미엄 2주 이용권 | 336시간 | 49,000원 | 초안/제출 준비 중 |
| PREMIUM_28D | premium28d | 토선생 프리미엄 4주 이용권 | 672시간 | 69,000원 | 초안/제출 준비 중 |

모두 Apple 유형 ‘소모품’, 기준 국가 대한민국(KRW), 판매 대상 대한민국 1개다. 한국어 설명은 각각 `24시간`, `72시간`, `7일(168시간)`, `14일(336시간)`, `28일(672시간)` 뒤에 `동안 모의고사와 AI 피드백 무제한 이용. 자동 갱신 없음.`을 사용했다.

근거는 실제 App Store Connect UI와 [구매 고지 초안의 승인 가격](PREMIUM_PURCHASE_REFUND_NOTICE_DRAFT.md)이다. 원화 가격표는 등록 사실 기록이며 실제 결제 검증에 하드코딩하지 않는다. 심사 승인·앱 출시·Google 등록·RevenueCat 매핑 완료 증거가 아니다.

### 6.1 같은 날 사용자 RevenueCat 설정 후 읽기 검증

이 절은 위 Store 생성 시점 이후의 후속 확인이다. Codex는 import 선택까지 진행하다 사용자의 직접 작업 요청으로 중단했고, 이후 사용자가 설정 완료를 알린 뒤 저장 상태를 읽기만 했다.

- Offering identifier `premium`, 표시명 `토선생 기간형 프리미엄 이용권`, REST resource ID `ofrng51a9b79a34` 확인.
- Apple 앱 `app3987acee69`의 상품 5개가 등록됐으며 각 package identifier와 Store product ID가 아래처럼 일치한다. Google 및 Test Store 상품이 이 Offering에 섞이지 않았다.
- Products의 Apple 5개 모두 Entitlements 열이 `Attach`로 표시돼 연결되지 않은 상태다. 기존 Test Store 상품의 Entitlement 연결과 구분한다.
- 기존 `default` Offering 3개 패키지가 별도로 남아 있다. `premium`을 기본 Offering으로 바꿨는지는 이번 상세 화면에서 판정하지 않았다. 앱 연동에서 `premium`을 명시적으로 선택하면 기본값 의존을 피할 수 있다.
- Apple 상품 5개 상태는 `Missing Metadata`, Google 상품 목록은 비어 있다. 계정 이메일 미확인 배너가 남아 있다. Paywall은 `Add Paywall` 상태다.
- 상품/패키지 연결만 검증했으며 SDK 상품 조회·sandbox 구매·Billing 지급·webhook/환불 검증 또는 심사 준비 완료가 아니다. 설정 저장·기본 Offering 전환·Entitlement 연결·메일 재발송을 실행하지 않았다.

| Package identifier | Apple Store product ID | RevenueCat product resource ID |
| --- | --- | --- |
| premium1d | premium1d | prod2b3350ec70 |
| premium3d | premium3d | prod0c6f7c904a |
| premium7d | premium7d | prodb754238599 |
| premium14d | premium14d | prod2bb863a58c |
| premium28d | premium28d | prodc1485631a1 |

위 `prod...`는 RevenueCat REST 리소스 ID이며 Store product ID와 다르다. 실제 Billing 환경별 catalog 반영은 별도 구현 작업이다.
