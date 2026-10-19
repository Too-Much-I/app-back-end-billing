# 결제 운영·RevenueCat 지원 조사와 권장안

- 날짜: 2026-10-07 / 브랜치: develop / Jira 없음.
- 조사 범위: 공식 공개 문서와 Billing 로컬 계획·설정·기존 이관 runbook. 실제 계정 UI·DB·AWS·Store 거래/환불·외부 지원 문의는 실행하지 않았다. 아래 권장안은 신규 승인/구현이 아니다.

## 1. 5줄 결론

1. RevenueCat 공식 webhook 문서는 Authorization와 HMAC·재시도 재서명을 명시한다. 계정 설정/실제 delivery 검증은 아직 별도다. [S1]
2. Google 부분 환불 실행 지원과 RevenueCat의 금액 수신 지원은 다르다. RC 공식 문서에 Google 부분 환불 금액 미수신 제한이 명시돼 있다. [S2/S3]
3. Google Orders API에는 부분 환불 상태·금액·시각 조회가 있어 보완 후보지만 RC-only 증거 계약을 확대하려면 별도 승인이 필요하다. [S4]
4. 이관은 승인된 짧은 writer 제한을 유지하되 DB 건수/처리량·재시도·drain/검증 시간을 측정한 뒤 시간대를 승인한다. 현재 분 단위 예측 근거는 없다. [L1/L2]
5. 담당/대체자·최소 권한·복구 감사와 환불 선행 활성화 gate를 준비하고 정상 근무 시간에 단계적으로 판매한다. provider 증거 공백을 해결하지 않은 affected 판매는 열지 않는다. [L2/L3]

## 2. 사용자가 반드시 읽어야 하는 내용

### 인증: 기존 방향을 유지할 수 있는 공식 근거 확인

[S1]은 선택형 Authorization, `X-RevenueCat-Webhook-Signature`의 `t`/`v1`, HMAC-SHA256의 timestamp+raw body 입력과 매 delivery 재서명을 설명한다. 승인된 5분 허용시간은 event 생성 시간이 아닌 delivery 서명 시간에 적용해야 한다. 원문 JSON 재직렬화 검증 금지. 프로젝트별 HMAC 설정·요금제 사용 가능 여부·정상/위조/재전송 실측은 미확인이다. 키 회전 시 이전 키 즉시 무효화 설명이 있으므로 별도 회전/복구 절차가 필요하다.

권장: Authorization+HMAC+시간 검증 유지, 실제 staging 전달 검증 후 adapter 완료 판정. 장점은 변조/재생 요청 방어와 기존 계약 유지, 단점은 키 관리·서버 시간·회전 운영이다. 계정 UI에 옵션이 없으면 지원 확인 전 해당 연동 gate를 닫고 임의 Authorization-only로 낮추지 않는다.

### 부분 환불: RC-only 자동 반영을 보장할 수 없음

[S2]는 Virtual Currency 맥락에서 Google 부분 반환액을 RC가 받지 못하며 해당 구매를 paid로 취급한다고 명시한다. 우리 상품은 currency가 아니므로 이 문서만으로 모든 RC endpoint의 행동을 단정하지는 않는다. 다만 일반 webhook 가격 필드는 선택형/nullable이며 음수 가능하다는 설명일 뿐 정확 부분 금액·증분 식별 보장이 아니다. [S6] 따라서 현재 자료로는 consumable 부분 환불을 RC-only로 확실히 감지·기록·권리 종료할 수 있다고 승인할 수 없다.

Google Console의 부분 환불 지원은 별개이며 일부 결제수단 제한이 있다. [S3] Apple consumable 환불 탐지를 위한 서버 알림/IAP key 요구, Google Console 환불 탐지 지연과 한계도 실제 상품 테스트 대상이다. [S5] Apple Refund Control의 부분 환불 선호 미지원은 자동 요청 대응 기능의 제한이지 Apple 최종 환불 사실의 전면 미지원이라는 뜻은 아니다. [S7]

권장 후보: 구매/SDK·일반 검증은 RC 유지, Google 부분 환불 확인은 인증된 Orders 조회 adapter로 한정 보완. `partialRefundEvents`의 성공 상태·processTime·세금 포함 total/tax가 문서에 존재한다. [S4] 이것은 아직 구현/권한/증거 충분성 검증 전이며 자동 환불 실행 API를 만들자는 제안도 아니다.

- 장점: 실제 Store 확정 증거로 기존 부분 환불·잔여권 종료 정책을 유지할 가능성, 운영자 숫자 입력 의존 축소.
- 단점: Google credential/최소 권한·API quota·정기 대사·정규화/금융 멱등성 관리 추가. RC와 중복 관측되는 동일 환불 식별 설계 필요.
- 현재 RC-only 신뢰 계약에 대한 명시적 범위 확대 승인이 필요하다. 이번 조사로 ADR/AGENTS를 변경하거나 direct adapter를 구현하지 않는다.
- 먼저 RC 지원 확인과 실제 consumable fixture를 확보한다. RC가 필요한 증거를 제공하면 불필요한 adapter를 추가하지 않는다. 지원이 부족하면 별도 기술안 승인 후 Google 조회를 검증한다.
- 대안: 승인된 Store 증거를 운영자가 검토해 통제된 정정 절차로 반영. 개발량은 줄지만 오입력/중복/처리 지연 위험이 커지고 이것도 별도 신뢰 계약·감사·2인 검토가 필요하다. 단순 문의/스크린샷/DB 직접 수정으로 우회 금지.
- 금액만 없고 환불 확정은 검증됐다면 기존 정책대로 권리를 종료하고 금액을 UNKNOWN 대사로 남긴다. 환불 사실 자체가 검증되지 않으면 문의만으로 종료하지 않는다. 법정 환불 의무를 기술 미지원으로 무시하지 않는다.

## 3. 사용자가 결정해야 하는 사항

후속 사용자 승인(2026-10-07): 위 Google Orders 읽기 보완 경로를 허용했다. 후보/별도 승인 필요 표현은 조사 당시 판단이며 현재는 경로 범위 승인 완료다. actual fixture·권한·금융 멱등 schema 검증은 미완료다. [PLAN-012 보완 계약](../plans/PLAN-012-refund-ledger-and-access-revocation.md)을 따른다. 자동 환불/신규 권리 지급/타 Store 직접 검증은 허용 범위가 아니다.

추가 공식 확인: [orders.get](https://developers.google.com/android-publisher/api-ref/rest/v3/orders/get)은 packageName/orderId로 조회하며 androidpublisher OAuth scope를 요구한다. 이 scope 자체가 읽기 전용이라는 뜻은 아니므로 별도 주체/최소 Play 권한·GET 제한을 검증해야 한다. [RC purchase v2](https://www.revenuecat.com/docs/api-v2/purchase)의 refund endpoint는 Web Billing용으로 설명돼 있어 이를 Google consumable 부분 환불 근거로 사용하지 않는다. 이번 확인은 공개 문서 수준이며 RC 지원 답변/프로젝트 UI/Store 주문 실호출을 수행하지 않았다.

| 항목 | 권장안 | 장점 | 단점/확정 시점 |
| --- | --- | --- | --- |
| 이관 작업 | 실제 건수·대표 부하 리허설 후 저이용 시간의 짧은 writer 제한, 초과 시 중단 기준 | 복잡한 온라인 이관보다 검증 용이 | 상태 반영/시작 대기; 실측 후 허용 시간·작업일 승인 |
| 운영 담당 | 문의/환불 승인자 1명+대체자, 기술 장애/복구 담당 1명+대체자; 겸임 가능 | 소규모 팀에서 책임 명확 | 겸임 시 독립 검토 약화; 실제 이름 지정 필요 |
| 권한 | 개인 계정·MFA·앱 범위 최소 권한; 문의 조회/Store 실행/기술 복구 권한 분리 | 사고 영향·실수 감소 | 권한 관리·추가 확인 부담 |
| 예외 복구 | 원장 변경·권리 복구·신뢰 경로 확대는 승인+dry-run+멱등 runner | 승인되지 않은 혜택/돈 변동 방지 | 긴급 처리 속도 감소; 직접 DB 변경 금지 |
| 판매 시작 | 환불/복구 기능 선행, 직원 sandbox 검증 후 담당자 대응 가능한 시간에 승인된 소규모 공개 | 문제 감지/대응 용이 | 출시 지연·점진 공개 장치 필요; 정확 비율은 후속 |

새 시험 횟수 제한·환불 기준 변경·자동 보상은 승인 없이 추가하지 않는다. 짧은 제한 이관은 이미 방향 승인됐으며 중단 분수/진행 시험 취소를 승인받은 것은 아니다. 새 Google 조회 adapter와 그 증거 신뢰 범위는 별도 결정이다.

## 4. 주요 위험과 미확인 사항

- 운영 DB 대상 수, active/GRADING 분포, 누락 epoch/source, 실제 index와 task 수 미조회. 로컬 설정 기본 OFF는 배포 상태 증거가 아니다.
- PLAN-007의 최대100건 이관은 기존 Session 귀속용이다. payment guard/schema v5 도구가 구현됐거나 같은 처리량이라는 뜻이 아니다.
- 이관 시간은 writer drain + 대상 수/실측 처리량 + 재시도 여유 + 전체 coverage 검증 + 재개 확인으로 계산한다. 현재는 숫자로 약속할 수 없다.
- 기존 5분 Reservation과 이벤트 retry budget을 이관이 침해하는지 확인한다. 제출 유실·타임아웃 가능성이 있으면 작업을 연기/이관안 재검토하며 자동 시간 연장을 새 정책으로 만들지 않는다.
- Orders 문서 필드 존재만으로 모든 consumed one-time·테스트 환불·추가 정정의 안정적 식별을 보장하지 않는다. 동일 금액/동일 시각을 임의 환불 ID로 발명하지 않는다.
- 긴 timeline reflow의 원자적 처리 한계는 group fan-out으로 해결되지 않는다. 실측 실패 시 승인된 의미를 깨는 부분 반영 대신 설계 재검토가 필요하다.
- Apple 최종 부분 반환액의 우리 거래별 수신·정규화 가능성은 이번 자료로 확정 못 함. 두 Store 지원을 한꺼번에 완료 처리하지 않는다.

## 5. 현재 작업과 직접 관련된 후속

1. RC 실제 프로젝트에서 webhook 사용 가능/HMAC 옵션을 읽기 확인하고 승인된 staging receiver 구축 후 test/retry·변조 거절 증거 확보. Secret은 문서/로그에 남기지 않는다.
2. RC 지원에 consumable Google 부분 환불의 이벤트/API 금액·상태·시각·중복 기준을 질의할 내용 작성. 이번에는 전송하지 않았다.
3. 실제 증거가 부족하면 Google Orders 읽기 전용 대사 adapter의 신뢰 계약 확대안을 승인받는다. 환불 실행 자동화와 분리한다.
4. 새 schema/guard 도구 구현 뒤 승인된 read-only inventory와 운영 유사 데이터 dry-run으로 제한 시간을 산정한다. 외부 접근/실제 이관 승인은 별도다.
5. 경보는 결제 pending·환불 전파 지연·인증 실패·대사 오류를 기술 담당/대체자에게 보낸다. 문의의 2영업일 첫 응답을 장애 대응 시간 또는 Store 환급 완료 시간으로 재사용하지 않는다.
6. 이중 지급/다른 사용자 귀속/인증 우회는 출시 차단 및 사고 대응 대상. 일시 지연은 기존 복구를 유지하며 정한 경보/중단 기준에 따른다. 판매 중지로 sync/환불 처리까지 끄지 않는다.

## 6. 부록 — 근거와 검증 범위

- S1 [RevenueCat Webhooks](https://www.revenuecat.com/docs/integrations/webhooks): 인증/서명/재전송·키 회전.
- S2 [RevenueCat Virtual Currency Refunds](https://www.revenuecat.com/docs/offerings/virtual-currency/refunds): Google 부분 금액 수신 제한. currency 맥락 구분.
- S3 [Google 주문 관리·환불](https://support.google.com/googleplay/android-developer/answer/2741495?hl=en): 부분 환불/결제수단·운영 권한.
- S4 [Google Orders resource](https://developers.google.com/android-publisher/api-ref/rest/v3/orders): 부분 환불 상태·금액·시각 및 주문 조회.
- S5 [RevenueCat Handling Refunds](https://www.revenuecat.com/docs/subscription-guidance/refunds): Store별 탐지 요건/지연.
- S6 [RevenueCat Event Types and Fields](https://www.revenuecat.com/docs/integrations/webhooks/event-types-and-fields): 선택형 nullable 가격 및 환불 이벤트 의미.
- S7 [RevenueCat Refund Control](https://www.revenuecat.com/docs/customers/refund-control): 부분 환불 선호의 별도 제한.
- L1 [기존 Session 이관 runbook](../runbooks/PLAN-007-public-reader-rollout.md), [설정](../../src/main/resources/application.yml).
- L2 [PLAN-013](../plans/PLAN-013-payment-rollout-and-operations.md).
- L3 [PLAN-012](../plans/PLAN-012-refund-ledger-and-access-revocation.md), [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md).

공식 문서는 2026-10-07 조회 기준. 본 조사/기록만 추가하며 계약·정책·코드·타 서버·계정·권한·배포를 변경하지 않았다. 문서 diff/링크 검사, 코드 변경 없어 Gradle 미실행. 실거래/비밀정보/원문 payload 미수집.
