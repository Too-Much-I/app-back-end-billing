# 결제 출시 준비 점검 — 운영 확정·실제 RevenueCat·법령·코드

- 확인일: 2026-09-09 / Billing 브랜치: develop / Jira 없음
- 범위: 승인 운영 정책 문서 반영, 로그인된 RevenueCat 화면 읽기, 공식 법령/사업자 문서 확인, Billing와 Identity/LC 로컬 소스 읽기. 외부 설정 저장·문의 발송·결제/환불 실행·배포 없음.
- 기준: [결정서 C9-S10·S11](../codex/CONTRACT_DECISIONS.md), [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md), [구매 안내 초안](PREMIUM_PURCHASE_REFUND_NOTICE_DRAFT.md).

## 1. 5줄 결론

1. 지원 이메일·정상 만료 후 기존 결과 열람·최초 별도 할인 없음은 사용자 승인으로 확정했다. 구현/게시/수신함 테스트는 별개다.
2. 실제 RC 화면에서 Apple 두 종류 credential은 Valid, 알림 설정은 correctly configured이나 수신 이력은 없었다. 자동 환불 응답은 Do not respond로 승인 정책과 일치했다.[R1][R3]
3. Google은 Valid credentials 표시와 동시에 Pub/Sub API 권한 오류가 발생했고 RTDN이 연결되지 않았다. 원인이 API 미활성/프로젝트·role 불일치 중 무엇인지는 GCP 직접 검증 전이다.[R2][S3]
4. RC의 양 Store 상품은 비어 있고 default Offering은 Test Store 월간/연간/평생 3개다. Billing webhook도 없어서 승인된 5기간권 판매·환불 연동 준비가 완료되지 않았다.[R4][R5][R6]
5. Billing 결제 application code/schema v5 및 Identity 구매 scope/LC paid revoke는 확인한 로컬 소스에 없었다. 법적 공제식과 Store 실설정·sandbox·AWS 검증이 남았으므로 판매 승인으로 간주하지 않는다.[L1][L2][L3][S1][S2]

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 이번 확정 사항

- 고객지원: `tosunsaeng093@gmail.com`. 사용자가 공개 고객지원 용도로 지정한 주소이며 테스트 메일·외부 문의를 보내지 않았다.
- 기존 결과: 정상 기간 만료 후 본인 계정의 기존 시험 결과·피드백 열람 허용. 보존/탈퇴·재가입 비이전 정책은 그대로이며 무기한 보관·새 유료 시험·임의 재채점 권한을 추가하지 않는다. D1 현재 Session 완료 및 기존 승인 장애 복구는 유지한다.
- 할인: 최초 별도 프로모션 할인 없음. 1/3/7/14/28일 가격 9,000/19,000/29,000/49,000/69,000원 유지. 기간별 단가 차이를 별도 출시 프로모션으로 취급하지 않는다.

### 2.2 실제 RevenueCat에서 확인한 상태

| 항목 | 읽기 확인 결과 | 의미/다음 작업 |
| --- | --- | --- |
| 계정 인증 | 이메일 미인증 안내 표시 | RC 가입 계정의 확인 메일 처리 필요. 고객지원 이메일과 같은 계정이라고 추정하지 않음 |
| iOS 앱 | In-App Purchase Key, App Store Connect API 모두 Valid credentials 표시 | UI 검증 통과 표시이며 실제 결제 성공 증거 아님 |
| Apple 서버 알림 | configured correctly, No notifications received | 설정 검증과 실제 전달 테스트를 구분. sandbox 수신 검증 필요 |
| S2S-only 신규 구매 tracking | checkbox unchecked | 최초 OFF 계약과 일치 |
| Refund Control | default Do not respond to refund requests, 추가 정책 미표시 | 자동 심사 OFF 계약과 일치. 최종 환불 알림 수신 중단 설정으로 혼동하지 않음 |
| Google credential | Valid credentials 표시 | 결제 검증 credential과 Pub/Sub 접근은 별개 |
| Google Cloud Pub/Sub | 접근 권한이 없다는 정확한 dashboard 오류 | API 활성 여부·동일 프로젝트·실제 service account role을 확인해야 함 |
| Google RTDN | Topic 선택·Connect to Google 화면, Refund Control에도 RTDN 설정 요구 | 연결 미완료. 결제 성공과 별도로 실시간 이벤트 전달을 검증해야 함 |
| Products — All filter | iOS/Play Store 모두 Add products 빈 목록 | RC에 Store product 연결 없음. Store 자체 상품 부재를 증명하는 것은 아님 |
| Products — Test Store | Monthly/Yearly/Lifetime | 테스트용 상품으로 실제 1/3/7/14/28일 consumable과 다름 |
| default Offering | monthly/yearly/lifetime 3개 package | 실제 양 Store 5개 기간권 package 매핑 필요. 임의 삭제/수정하지 않음 |
| Integrations/Webhooks | Active 0, webhook 화면 Create new webhook만 표시 | Billing webhook 미등록. backend 구현 후 환경 분리해 연결해야 함 |

Pub/Sub 공식 문서는 API 활성 여부와 role을 점검하도록 안내한다.[S3] 본문 설정 예시에는 Pub/Sub Editor가 있고 일부 troubleshooting은 Admin을 언급한다. 이를 근거로 무조건 Admin을 부여하거나 키를 재발급하지 않는다. 현재 JSON이 가리키는 프로젝트/계정과 필요한 정확 권한을 확인한 후 최소 권한으로 별도 승인 변경한다. 이번에는 Key 보기·다운로드·재발급, role 변경, Connect/Save/Resend를 실행하지 않았다.

### 2.3 Store 콘솔 직접 확인의 한계

- App Store Connect `/apps` 접근은 `authResult=FAILED` 로그인 경로로 이동했다. 실제 상품/가격/심사·세금·한국 판매 설정을 읽지 못했다.
- Google Play Console은 현재 브라우저 계정의 개발자 가입/2단계 인증 안내 화면으로 이동했다. 사용자의 기존 개발자 계정이 없다고 단정하지 않으며 해당 계정으로의 접근이 필요하다. 계정 생성·전환·보안 설정 변경을 하지 않았다.
- 따라서 'RC에 상품이 없음'은 확인됐지만 'Apple/Google에도 상품이 전혀 없음'은 미확인이다. 실제 상품 생성 요청이나 금융/스토어 거래는 이번 범위가 아니다.

## 3. 사용자가 결정하거나 제공해야 하는 사항

새로운 상품 선택을 계속 추가할 필요는 없다. 다음은 실제 접근과 검토 자료다.

1. RC 가입 이메일 인증 처리. 지정한 고객지원 이메일의 실제 수신·담당 운영도 확인한다.
2. 올바른 Apple/Google 개발자 계정으로 로그인된 콘솔 접근 또는 상품 목록/가격/판매 국가 화면. 비밀번호·키 원문을 대화/문서로 보내지 않는다.
3. GCP의 해당 프로젝트 Pub/Sub API 활성·service account IAM 화면을 읽을 수 있는 상태. 실제 접근 수정은 영향 확인 후 별도 승인한다.
4. 부분 반환 적용/공제·효력 시점 검토를 위한 법률 상담. 이번 확인으로 고객지원 정책은 확정됐지만 법적 결론까지 확정되지 않는다.

### 외부 단건 검토에 전달할 질문 — 발송하지 않은 초안

> 한국 성인에게 개인사업자가 AI 모의고사·채점만 제공하고 강사 수업·정규 교육 과정은 없습니다. 28일(672시간) 무제한 이용권은 자동 갱신 없이 69,000원이며 추가 구매 시 기간이 연결됩니다. 만료 후 본인 계정의 기존 결과는 열람할 수 있으나 파일 다운로드는 제공하지 않습니다. Apple/Google 인앱 구매를 사용하고 일반 환불은 Store로, 서비스/법적 요청은 자체 고객지원으로 받습니다.
>
> (1) 전자상거래법상 가분적 용역/콘텐츠와 계속거래, 교육 관련 별도 반환 기준 중 무엇이 적용됩니까?
> (2) 구매 후 정확히 24시간 제공된 시점의 적법한 청약철회와 일반 중도해지에서 각각 반환액·공제 근거는 무엇입니까?
> (3) 실제 결제금액 기준 시간비례/추가 위약금 없는 정책을 사용할 수 있으며 잔여기간 계산의 효력 시점은 언제입니까?
> (4) 기존 무료시험과 구매 화면의 제공개시 안내로 어떤 시험 사용/고지·동의 요건을 충족해야 합니까?
> (5) Store 재량 환불 거절 또는 부분 환불 실행 제한이 있을 때 사업자가 취해야 할 조치와 법정 기한은 무엇입니까?

이는 완성된 사실관계 검토의 출발점이다. 사업자등록상 정확 업태/종목·신고 여부와 실제 구매/약관 화면을 추가해야 하며 상담 결과가 자동 구현 승인은 아니다.

## 4. 주요 위험과 미확인 사항

### 4.1 법령 재확인 결과

- 전자상거래법 제17조 제2항 제5호는 제공 개시 제한과 가분적 미개시 부분 예외를 구분한다. 제17조 제4항은 서면 의사표시 발송 효력, 제18조는 디지털콘텐츠/용역 철회 시 환급기한·결제 취소 조치와 위약금 제한 등을 규정한다. 내부 처리 지연/Store 심사를 이유로 법정 기산점을 자동 변경하지 않는다.[S1]
- 추가로 시행령 제24조를 직접 확인했다. 일부 소비 비용은 소모성 부품, 다수의 동일한 가분물 중 소비 부분 공급비용의 범위로 정해져 있다. 이 조문에 '28일권 가격 ÷ 28'이나 '별도 1일권 정가 9,000원 공제' 공식은 없다. 서비스 일할 반환의 법적 적용과 이 조문 관계를 전문가 검토 없이 단정하지 않는다.[S2]
- 시행령 제21조의2는 미리보기/한시적 이용/제한 체험 등을 규정한다. 무료시험이 있다는 사실만으로 모든 구매자에 대한 필요한 시험 사용·고지 조치 완료를 자동 인정하지 않는다. 실제 화면에서 사전 접근 가능성과 고지 내용을 확인해야 한다.[S2]
- 가상 참고 계산은 69,000×27/28=66,535.714…원이다. 단순 비례 반환안을 가정한 산술일 뿐 승인 환불액이 아니며 통화 반올림·기산점·사유별 적용이 미정이다. 69,000−9,000=60,000원을 당연한 법정 환불액으로 채택하지 않는다.

공식 조문 확인은 수행했지만 개별 상품 분류/환급액의 법률 적합성 판단은 완료하지 못했다. 기존 콘텐츠이용자 보호지침의 조건부 제25조 권고와 28일/계속거래 쟁점은 [선행 조사](REFUND_REMAINDER_RESEARCH-2026-09-08.md)에 남겨두며 법적 분류를 IT 업종명만으로 확정하지 않는다.

### 4.2 Store/RevenueCat 환불 지원

RC Handling Refunds를 다시 확인했다. Apple consumable 환불 감지에 서버 알림·In-App Purchase Key가 필요하고 개발자가 Apple 환급을 대리 확정할 수 없으며, Google 외부 환불 감지는 최대 24시간 안내와 revoke 없는 환불 탐지 제한을 둔다.[S4] 이미 확인한 Google Console 부분 환불/Apple 비례 선호와 RC 부분 선호·사용률 미지원은 선행 조사에 근거한다. 이번에 실제 구매/부분 환불 fixture 또는 금액 전달을 실행 검증한 것은 아니다.

따라서 정상 전액 환불 경로와 별개로 부분 환불 금액/증거/중복/원장 계약 검증이 필요하다. 법적 요청을 거절하는 근거로 시스템 미구현을 사용하지 않는다. RC 문의는 작성/발송하지 않았고 실제 답변도 없다.

## 5. 현재 작업과 직접 관련된 코드·배포 점검

| 범위 | 확인된 로컬 사실 | 미완료 의미 |
| --- | --- | --- |
| Billing | domain은 무료/예약/owner/reader 중심. payment endpoint·RevenueCat adapter·Purchase/SubscriptionEntitlement 구현 없음 | 결제는 ADR 초안 단계. 신규 기능 테스트 불가 |
| Billing 설정 | schema-version 4, public reader/connector 기본 false, 내부 ingress disabled 기본값 | 로컬 기본값이다. ECS 실제 환경값/production 상태 증거 아님 |
| Identity | JwtAccessTokenIssuer가 Billing audience와 billing:read를 기본/명시 scope에 추가 | 이전 로컬 단일 audience 관찰은 현재 파일과 다름. 실제 배포 완료는 미확인 |
| Identity 구매 | 확인한 src/main에서 billing:purchase 발급 경로 미발견 | ACTIVE MEMBER 구매 scope는 별도 후속. read가 있다고 구매 허용 안 됨 |
| Learning Core | ExamReadService의 history/retries는 현재 사용자·Session owner로 조회. paid 권리 유효기간 의존이 이 코드에는 없음 | 만료 후 본인 결과 열람 방향과 맞지만 future paid integration/E2E는 별도 |
| Learning Core paid revoke | 확인한 src/main에서 AttemptGroupAccessRevoked/해당 신규 route 미발견 | 환불 시 OPEN/RETAKE deny·GRADING gate를 구현한 것으로 간주하지 않음 |

현재 사용자 요청은 정책 문서 반영과 점검이므로 application code를 추가하지 않았다. Identity/LC 파일도 읽기만 했다. AWS 계정/클러스터·ALB/Lattice·Mongo 실제 인덱스·Store sandbox·앱 SDK·성인 제한은 이번 직접 검증 범위 밖으로 남긴다. 기존 무료 테스트 성공 기록을 결제 기능 검증으로 재사용하지 않는다. Gradle은 문서/정적 점검만이므로 실행하지 않았다.

권장 다음 순서: RC 이메일 인증/Google 알림 문제와 Store 상품 등록 상태 확인을 진행하면서 결제 PLAN을 작성한다. 이후 별도 승인한 코드 구현 → 환경별 webhook/상품 Offering 연결 → sandbox 구매·환불·응답 유실/경합 → 법률/운영/배포 gate 통과 순서로 진행한다. API 미구현 상태에서 webhook URL을 임의 등록하지 않는다.

## 6. 부록 — 근거 위치와 검사

### 실제 화면 — 2026-09-09 읽기 확인

- R1: RevenueCat → Apps → 토선생 iOS. Key/Issuer/알림 URL 원문은 기록하지 않고 검증 상태만 요약.
- R2: RevenueCat → Apps → 토선생 (Play Store). credential badge, Pub/Sub 오류, RTDN 연결 화면.
- R3: RevenueCat → Lifecycle → Refund Control. default Do not respond, Google RTDN 요구.
- R4: RevenueCat → Product catalog → Products → All. Store 빈 목록/Test Store 세 상품.
- R5: RevenueCat → Product catalog → Offerings → default. 월간·연간·평생 3 package.
- R6: RevenueCat → Integrations → Webhooks. 등록 목록 없이 Create new webhook.

### 공식 문서

- [S1 전자상거래법 제17~19조](https://www.law.go.kr/LSW/lsLawLinkInfo.do?chrClsCd=010202&lsId=009318&lsJoLnkSeq=1000527255) — 시행 2026-07-21, 2026-09-09 재확인.
- [S2 전자상거래법 시행령](https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq=288143) — 시행 2026-07-21, 대통령령 제36507호. 제21조의2/제24조 본문 확인.
- [S3 RevenueCat Google credential 설정/오류 안내](https://www.revenuecat.com/docs/service-credentials/creating-play-service-credentials) — Pub/Sub API/role 분리 점검. 문서의 설정 명령은 실행하지 않음.
- [S4 RevenueCat Handling Refunds](https://www.revenuecat.com/docs/subscription-guidance/refunds) — 플랫폼별 실행/감지 조건.

### 로컬 코드

- L1: [Billing 설정](../../src/main/resources/application.yml), [BenefitCatalog](../../src/main/java/web/tosunsaeng/billing/domain/benefit/application/BenefitCatalog.java), src/main/src/test의 payment/RevenueCat 구현 검색.
- L2: `/Users/msde76/identity/src/main/java/web/tosunsaeng/identity/global/security/jwt/JwtAccessTokenIssuer.java`.
- L3: `/Users/msde76/app-back-end-learning-core/src/main/java/web/tosunsaeng/domain/exams/application/ExamReadService.java`, 같은 도메인의 `api/ExamRestController.java`, src/main paid revoke 검색.

검사: 문서 diff·Markdown 로컬 링크 검증. 정책 변경은 C9-S11만이며 부분 환불/환불식은 미승인 유지. 실제 Secret/키 ID/Issuer ID/서버 알림 URL/사용자 거래·계정 개인정보는 조사 기록에 복제하지 않았다. 사용자 지정 고객지원 이메일만 공개 안내 목적에 사용한다.
