# 사용 후 환불: Store·한국 법령·RevenueCat 조사

- 확인일: 2026-09-07 / 브랜치: develop / Jira 없음
- 성격: 공식 공개 문서 조사와 분석. 법률 자문·상품 법적 분류 확정·설정 변경·구현 승인이 아니다.

## 1. 5줄 결론

1. Apple의 환불 심사와 Google의 재량 환불은 개발자가 일괄 거절하도록 강제할 수 없다.[S1][S2][S3]
2. Google은 48시간 이내 구매 세부정보에 따른 환불 가능성, 이후 개발자 문의를 안내한다. 48시간 무조건 환불 보장은 아니다.[S2]
3. 한국 전자상거래법 제17조는 7일 청약철회 원칙, 제공 개시된 디지털콘텐츠의 제한 조건, 가분적 미제공 부분의 예외를 둔다.[S5]
4. RevenueCat Refund Control은 Apple·Google에 환불 선호를 보내지만 최종 결정은 Store다. 거래별 사용률·사용 이벤트는 현재 자동 수집/전송하지 않는다.[S3]
5. 현재 Billing의 환불 확정 후 차단 설계는 유지하되, 'Store 거절이면 법적 요청도 종료' 또는 '하루 사용하면 남은 27일도 무조건 환불 불가' 정책으로 확장하면 안 된다.[S2][S5]

## 2. 사용자가 반드시 읽어야 하는 내용

### 하루 사용한 4주권의 예

사용자는 Store에 환불을 신청할 수 있지만 승인 여부를 미리 보장할 수 없다. 승인되면 현재 ADR의 해당 purchase 권리 철회·진행/재응시 차단·선행 GRADING 완료·COMPLETED 보존 설계를 적용한다. 이미 제공한 AI 채점 비용이 자동 회수되는 것은 아니다.

Store가 재량 환불을 거절했더라도 개발자 약관과 관련 법령에 따른 별도 요구가 가능하다. 'Store에만 문의하고 우리는 처리하지 않음'으로 책임 전체를 넘기지 않는다.

한국의 일반 청약철회 기간은 제17조 제1항의 기산점에 따른 7일이다. 제공이 시작된 디지털콘텐츠는 제2항 제5호의 제한 대상이 될 수 있지만, 가분적 용역/디지털콘텐츠의 미제공 부분에는 예외가 있다. 제6항의 명확한 표시 및 디지털콘텐츠 시험 사용 제공 등 조치도 함께 검토해야 한다. 제3항은 표시·광고 또는 계약과 다른 이행에 대한 별도 철회를 인정한다. '사용했음'이라는 사실만으로 모든 환불 사유가 사라지지는 않는다.

토선생 4주 unlimited가 법적으로 가분적인지, 미사용 기간의 금액을 어떻게 계산하는지, 계속거래/콘텐츠 업종 관련 다른 기준이 적용되는지는 이 조문만으로 확정하지 않았다. Store의 consumable 분류는 법적 권리 소진 판정이나 실제 학습 이용률과 동일하지 않다.

## 3. 사용자가 결정해야 하는 사항

이번 조사로 자동 결정하거나 활성화한 정책은 없다. 출시 전에 다음을 검토한다.

- 구매 화면/약관: 28일 고정 기간·시작 시점·자동갱신 없음·환불 접수 경로·법정 권리 보장 문구.
- 개발자 문의: Google의 48시간 이후 문의와 서비스 장애/계약 불이행/미제공 부분 요청의 담당자·처리 절차.
- Refund Control: 기존 출시 OFF 유지. 활성화를 원하면 전송 정보 정확성·소비자 동의·환경/상품별 지원 확인과 별도 승인.
- 법적으로 부분 반환이 필요한 경우의 운영 경로: 현재 '부분 자동 환불 미구현'은 법적 반환 의무를 거절하는 근거가 아니다. 필요하면 승인된 수동 처리·정규화 반영 계약 또는 구현 범위를 먼저 보완한다. 현재 코드를 임의로 확대하지 않는다.

법률 적합성 최종 판단은 별도 확인이 필요하다. 법무팀이 없어도 변호사 단건 검토 또는 관련 기관의 사업자 상담을 통해 상품 분류와 고지 내용을 확인할 수 있다.

## 4. 주요 위험과 미확인 사항

- 실제 Apple/Google 환불 승인 확률·사용량별 거절 기준은 공개 문서에서 확정하지 못했다.
- Apple 지원 안내는 한국어 공식 페이지를 확인했다. 연결된 일반 약관은 United States, English로 표시되어 한국 약관 근거로 사용하지 않았다. 한국 Apple 상세 약관/사업자 계약은 추가 확인 대상이다.
- RevenueCat의 현재 공개 문서는 양 Store Refund Control을 안내하지만 사용자 project에서 실제 제공/활성화됐는지, 해당 one-time 상품의 전체 경로가 동작하는지는 확인하지 않았다.
- RC에 거래가 있으면 Apple deliveryStatus=DELIVERED라고 문서는 설명한다. 현재 설계는 SDK/RC 완료 뒤 Billing PENDING이 가능하므로 실제 Billing 지급·시험 이용 완료와 다를 수 있다.
- RC는 sampleContentProvided=true, Apple customerConsented=true를 전송한다고 설명한다. 기존 무료시험이 모든 구매자의 적합한 시험 사용 제공인지, 필요한 동의가 수집됐는지 검증 없이 활성화하면 안 된다.
- Refund Control 지원은 Billing에 일반 REFUND_REVIEW webhook이 제공된다는 증거가 아니다. 기존 Billing review adapter는 검증 전 비활성 유지.
- 이미 사용한 서비스 비용과 환불 확정 알림 지연의 위험은 완전히 제거되지 않는다. 환불 이용자를 자동으로 악용자로 간주하지 않는다.

## 5. 현재 계약과 직접 관련된 설명

기준: [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md) §3/G3/G4/§5.4/§5.9, [결제 요약](FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md) §6/7.

유지: 최종 provider 증거 기반 환불, paid source 차단, 무료권 보존, ledger append-only, 출시 자동 refund handling OFF, 원문·시험 답안 비전송.

보완 검토: 사전 환불 심사와 최종 환불 처리의 구분, Google 개발자 직접 요청, 한국 미제공 부분/고지/시험 사용 요건, RC 전송 사실과 Billing 지급 사실 불일치. 이번에는 승인 ADR/wire/도메인 상태를 바꾸지 않았다.

고지 초안의 방향(법률 검토 전 게시 금지): '환불은 구매한 스토어의 정책 및 관련 법령에 따라 처리됩니다. 이용 여부와 제공 내역에 따라 환불 가능 여부가 달라질 수 있습니다. 서비스 문제 또는 법령에 따른 환불 문의는 토선생 고객지원으로 접수할 수 있습니다. 환불된 이용권의 사용은 중단됩니다.'

## 6. 부록 — 공식 근거와 세부 비교

| 구분 | 확인된 공식 내용 | 해석하지 말아야 할 내용 |
| --- | --- | --- |
| Apple [S1] | 일부 구매 환불 가능, reportaproblem 접수, 업데이트까지 24~48시간 안내, 국가별 적합성·소비자법 권리 | 24~48시간 내 구매만 환불 가능하거나 그 기간 무조건 승인 |
| Google [S2] | 48시간 이내 세부정보에 따라 가능, 이후 개발자 문의, 개발자 약관·법률상 요청 별도 | 48시간 뒤 모든 환불 불가 또는 48시간 내 자동 보장 |
| Google 일반 [S6] | 정책 악용으로 보이는 경우 일반적으로 환불하지 않음 | 우리 서버가 임의 횟수로 Store 환불을 강제 거절 |
| RC [S3] | Prefer full refund / Prefer no refund / Send consumption data only / Do not respond; 선호는 심사 입력 | 선호 선택이 최종 승인/거절을 보장 |
| RC Apple [S3] | customerConsented=true, 거래 존재 시 DELIVERED, sampleContentProvided=true, refundPreference | 시험 횟수·답안·실제 사용률을 알아서 제출 |
| RC Google [S3] | orders.reviewrefund에 pendingRefundToken, sampleContentProvided=true, APPROVE/DECLINE/NEUTRAL preference | Billing에 사전 심사 이벤트가 항상 전달됨 |
| RC 제한 [S3] | 거래별 consumption percentages/usage events 미수집·미전송, Partial refund preference 미지원 | 법적으로 부분 반환이 필요 없다는 뜻 |
| RC 활성화 [S4] | Apple server notifications·Google RTDN 구성, 필요한 동의 확보, project-level 정책, Save 뒤 적용 | 공식 기능 문서만으로 실제 프로젝트 설정 완료 |
| 한국 법 [S5] | 제17조 제1항 7일, 제2항 제5호 제공 개시 제한/가분적 미제공 부분 예외, 제3항 불일치, 제5항 공급 사실/시기 등 입증, 제6항 고지·시험 사용 등 | 4주권 가분성/공제율/다른 관련 법 적용을 자동 확정 |

### 출처 (2026-09-07 본문 확인)

- [S1 Apple 공식 환불 요청](https://support.apple.com/ko-kr/118223)
- [S2 Google 앱·게임·인앱 환불 정책](https://support.google.com/googleplay/answer/15574908?hl=ko)
- [S3 RevenueCat Refund Control](https://www.revenuecat.com/docs/customers/refund-control)
- [S4 RevenueCat Configure refund policies](https://www.revenuecat.com/docs/customers/refund-control/configure-refund-policies)
- [S5 국가법령정보센터 전자상거래법 제17조](https://www.law.go.kr/LSW/lsLawLinkInfo.do?lsJoLnkSeq=1000527300&lsId=009318&chrClsCd=010202&print=print) — 페이지 표시 시행일 2026-07-21, 법률 제21312호.
- [S6 Google 일반 환불 정책](https://support.google.com/googleplay/answer/2479637?hl=ko)

공개 문서 열람만 수행했다. 계정 정보·위치 등 브라우저 부수 정보는 조사 근거/기록에서 제외했다. 외부 문의·동의·환불 실행·Console 저장·Secret 변경 없음.
