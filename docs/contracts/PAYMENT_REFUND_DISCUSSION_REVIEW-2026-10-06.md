# 결제·환불 논의 검토 — 2026-10-06

- 브랜치: develop / 신규 Jira 없음.
- 범위: 현재 미커밋 문서와 10/6 승인 기록, ADR-004 기술 초안, Billing 소스 파일 목록 대조. 정책 수정·구현 승인이 아닌 리뷰다.
- 실제 Store/RevenueCat 설정·최신 공식 지원·법률 적합성·타 서비스 배포를 이번에 재검증하지 않았다. Store 지원 설명은 기존 조사 기록 기준이다.

## 1. 5줄 결론

1. 상담 접수→팀 검토→Store 처리→검증된 결과 반영을 분리한 운영 방향은 일관된다. [C9-S10](../codex/CONTRACT_DECISIONS.md#c9-s10-store-중심-환불-창구와-국내-판매가격--확정2026-09-09)
2. 부분 환불 후 해당 구매만 종료한다는 정책은 승인됐지만, 기존 전액 환불 모델에 그대로 넣을 수 없다. [ADR-004 §4·§5.5·§5.7](../adr/ADR-004-fixed-term-premium-payment-contract.md)
3. 확정 시각 미제공·수신 지연·후속 환불의 exact 처리와 구매별 이용 증거가 결제 PLAN의 핵심 보완 대상이다. [ADR-004 §5.7·부록 A](../adr/ADR-004-fixed-term-premium-payment-contract.md)
4. AGENTS의 부분 환불 범위 제외와 구매 안내 초안은 최신 승인에 맞춰 정리할 필요가 있다. [AGENTS](../../AGENTS.md), [안내 초안](PREMIUM_PURCHASE_REFUND_NOTICE_DRAFT.md)
5. 정책 방향 검토와 구현·판매 준비 완료는 다르며, 결제 PLAN 승인·provider fixture·LC 경합·법적 고지 검증이 남아 있다. [ADR-004 부록 B](../adr/ADR-004-fixed-term-premium-payment-contract.md)

## 2. 사용자가 반드시 읽어야 하는 내용

### R1 — P1: 부분 환불 증거와 후속 금전 처리가 전액 환불 모델에 아직 반영되지 않았다

**확인된 문서 사실:** C9-S10과 ADR §4는 실제 부분 반환액 보존과 해당 구매의 잔여권 종료를 승인 기록한다. 반면 §5.5는 API `status=refunded`를 REFUNDED reversal로 매핑하고, §5.6의 필드 목록은 부분 금액·환불 식별자를 정의하지 않는다. §5.7은 purchase terminal 전이, 부록 A는 일반 REFUND effectKey를 설명한다. 문서가 스스로 후속 설계 필요를 인정하므로 구현 결함이 아니라 구현 전 차단되는 설계 공백이다.

**분석:** 같은 구매에 부분 환불→추가 부분 환불 또는 전액 환불이 이어지면 권리는 첫 확정 때 한 번 종료하되 금전 기록은 이후에도 추가돼야 한다. 구매가 terminal이라는 이유로 후속 반환을 버리거나, 누적 반환액을 신규 반환액으로 다시 더하면 실제 금액과 원장이 어긋난다. webhook event ID 중복 방지만으로 서로 다른 관측 경로의 같은 환불을 식별할 수 있는지도 불명이다.

**필요한 보완:** provider별 환불 증거의 출처·식별자·통화·금액·세금 기준, 증분/누적 의미, 알려지지 않은 금액 처리, 금융 상태와 entitlement 접근 상태의 분리, 환불별 dedupe/effectKey와 후속 환불 규칙을 정의한다. API `owned/refunded`만으로 부분 금액을 판정할 수 있다고 가정하지 않는다. Google Console 실행 결과를 RC가 충분히 제공하지 못하면 승인된 증거·대사 경로가 필요하다. 운영자 입력만으로 기존 provider-confirmed 계약을 우회하지 않는다.

근거: [C9-S10](../codex/CONTRACT_DECISIONS.md), [ADR-004 §4·§5.5~5.7·부록 A](../adr/ADR-004-fixed-term-premium-payment-contract.md), [Store 지원 조사 §3~4](STORE-REFUND-CAPABILITIES-2026-10-06.md).

### R2 — P1: 환불 확정 시각과 fallback·지연 reflow의 의미를 고정해야 한다

**확인된 문서 사실:** 10/6 승인 기록은 실제 provider 환불 확정 시각을 기준으로 삼고 정확한 field/fallback은 검증 대상으로 남긴다. 기존 ADR §5.7은 effectiveAt이 없는 API 관측을 최초 검증 시각으로 고정한다. 이 fallback은 기술 초안이며 최신 운영 선택으로 자동 확정됐다고 볼 수 없다.

**분석:** 실제 환불은 오전에 확정됐지만 저녁에 수신한 경우, 뒤의 예정 이용권을 오전으로 당기는지 저녁부터 시작하는지에 따라 잔여시간이 달라진다. 이미 시작한 다른 slot은 재지급하지 않는 규칙도 함께 만족해야 한다. 뒤늦게 정확한 provider 시각을 얻었을 때 기존 schedule을 다시 조정할지도 불명이다. 법적 해지/반환 기산점, provider 확정, Billing 반영, LC 차단 시각은 같은 시각으로 취급할 수 없다.

**필요한 보완:** providerConfirmedAt·observedAt·appliedAt과 timeSource의 의미, 확정 시각 부재 처리, 후속 시각 정정 여부를 정의한다. 지연 중 사용된 다른 구매 slot과 미시작 slot을 포함한 reflow 예제로 결과를 고정한다. 수신 시각을 법적 반환액 계산 시각으로 자동 사용하지 않는다. 현재 ADR fallback을 구현 시 임의 채택하지 않는다.

근거: [C9-S10 운영 승인](../codex/CONTRACT_DECISIONS.md), [ADR-004 §4·§5.7](../adr/ADR-004-fixed-term-premium-payment-contract.md).

### R3 — P2: 최신 승인과 현재 작업 지침·안내 초안이 어긋난다

**확인된 문서 사실:** AGENTS의 제품 범위와 리뷰 항목 12는 부분 환불을 여전히 범위 밖으로 열거한다. CURRENT_STATE는 최신 C9-S10을 우선한다고 기록한다. 구매 안내 초안은 이메일 중심이며 앱 REFUND 문의 경로와 부분 환불 뒤 잔여 이용권 종료를 사용자 문구에 명시하지 않는다. C9-S3의 전체 transaction refund 설명도 최신 변경과 함께 읽어야 한다.

**영향:** 후속 작업자가 부분 환불 설계를 범위 밖으로 잘못 판단하거나, 프론트가 오래된 초안으로 부분 환불 뒤 이용권을 유지한다고 기대하게 만들 수 있다. 최신 승인으로 대체된 문구는 현재 규칙과 역사 기록을 구분해야 한다.

**필요한 보완:** 승인 이력은 보존하고 AGENTS의 현재 적용 범위·리뷰 기준과 안내 초안의 현행 설명을 동기화한다. 안내에는 해당 구매 잔여권 종료, 다른 구매·무료권 보존, Apple 최종 심사와 Google 운영 가능 범위를 반영한다. 이번 리뷰에서 계약·AGENTS·안내 본문을 수정하지 않았다.

근거: [AGENTS](../../AGENTS.md), [C9-S3·C9-S10](../codex/CONTRACT_DECISIONS.md), [안내 초안 §2.3~2.4](PREMIUM_PURCHASE_REFUND_NOTICE_DRAFT.md).

### R4 — P2: userId 귀속만으로 환불 대상 구매와 이용량을 확인할 수 없다

**확인된 문서 사실:** 문의의 userId는 인증된 계정에서 자동 귀속하며 로그인 불가/탈퇴는 예외 접수한다. ADR §5.8은 paid purchase/source snapshot을 계획하지만 현재 Billing에는 유료 Purchase/payment ledger 구현이 없다. 기존 AttemptGroup·Session은 무료 lifecycle projection이다.

**분석:** 한 사용자의 여러 구매, 무료 시험, 실패·복구 시험, 삭제된 학습 기록을 구분하지 않고 현재 시험 목록 개수로 공제하면 잘못 판단한다. userId는 문의자의 인증 문맥이지 특정 Store 거래 소유권 증명 자체가 아니다. 특히 탈퇴 후 새 계정 문의는 paid owner 이전과 별개로 확인해야 한다.

**필요한 보완:** 문의와 확인된 purchase의 운영 연결, 구매 소유자 검증, 해당 purchase로 승인된 group/session과 최소 이용·장애 증거, 열람 권한·접근 감사·보존근거를 정의한다. 기존 projection 재사용 가능성을 먼저 검토하고 답안·피드백 원문이나 중복 이용 이력 저장소를 임의로 추가하지 않는다. 원장 5년 보존을 모든 학습 개인정보 5년 보존의 근거로 확대하지 않는다.

근거: [ADR-004 §5.8·부록 A](../adr/ADR-004-fixed-term-premium-payment-contract.md), [SNS/문의 검토 §4~6](REFUND-SNS-REVIEW-2026-10-06.md), [AttemptGroup](../../src/main/java/web/tosunsaeng/billing/domain/attempt/domain/entity/AttemptGroup.java).

## 3. 사용자가 결정해야 하는 사항

- 재선택 불필요: 상품·가격, Google/Apple 운영 차이, REFUND 문의, 2영업일 1차 응답, 부분 환불 뒤 해당 구매 종료, 다른 권리 보존, GRADING 완료·COMPLETED 보존.
- 남은 운영 항목: 실제 담당자와 환불 집행 권한, 사유/판단/Store 결과 감사 절차, 구매자 확인의 예외 처리, D2 환불 취소 복구 절차.
- 법적 검토 대상: 사유별 반환 의무·공제·반올림·효력 시점과 최종 고지/동의. 팀 건별 판단이나 프론트 담당 지정만으로 확정되지 않는다.
- 개발 검증 대상: R1·R2의 증거 매핑·멱등성·reflow·fallback, R4의 최소 증거 계약. 사용자에게 provider field를 임의 선택하도록 요구할 항목이 아니다.

## 4. 주요 위험과 미확인 사항

- 기존 Store 조사상 Google 부분 환불 실행과 RC 부분 금액/상태 수신은 별개다. Apple GRANT_PRORATED는 원하는 금액의 환급 명령이 아니다. 실제 fixture 검증이 필요하다.
- Apple consumption 응답의 12시간 기한과 팀의 2영업일 1차 응답은 별개다. 자동 Refund Control OFF를 유지하며 이번 상담 승인을 개인정보 전송/자동 심사 승인으로 해석하지 않는다.
- LC 접근 차단은 비동기 전파다. Billing refund commit과 LC deny commit 사이의 차이 및 GRADING 승인 경합은 ADR §5.9의 Job gate를 검증해야 한다.
- ADR의 Store 상품 미생성/매핑 미완료 설명 일부는 9/16 Apple 5종 및 RC Offering 기록보다 오래됐다. 그 기록도 현재 판매·Google 설정·실거래 E2E 성공을 증명하지 않는다.
- 공제 공식·provider 지원·설정·배포를 이번에 새로 확인하지 않았으며, 이미 승인된 계약을 리뷰 추론으로 변경하지 않는다.

## 5. 현재 작업과 직접 관련된 설명

정책 방향은 유지하고 R1~R4를 결제 PLAN의 완료 조건으로 포함하는 것이 다음 작업이다. 정책 문서 현행화→partial refund 금융 모델/증거/시각 예제→provider-neutral 원장과 fake adapter→Store fixture 및 LC 경합 검증 순서로 구체화한다. 구현은 기존 ADR·PLAN 승인 절차를 따른다. Identity 문의·scope와 LC revoke 변경은 별도 서버 후속이며 Billing에서 함께 수정하지 않는다.

이번 변경은 본 리뷰와 CURRENT_STATE/WORKLOG 기록뿐이다. 기존 미커밋 파일을 보존했다. 코드·API·schema·외부 서비스·Jira·Git 이력·배포 변경 없음. 분석/문서 작업이므로 Gradle 테스트를 실행하지 않았고 문서 diff와 링크를 확인했다.

## 6. 부록 — PLAN에 포함할 검증 사례

| 사례 | 확인해야 할 결과 |
| --- | --- |
| 같은 부분 환불 webhook/API 재관측 | 실제 환불액 한 번 기록, 권리 종료·reflow 한 번 |
| 부분 환불 후 추가 부분/전액 환불 | 추가 금액만 기록, 이미 종료된 권리 재종료·후속 기간 재당김 없음 |
| 누적 환불액 제공 | 증분과 구분하며 이중 합산 없음 |
| 금액·통화·확정 시각 미제공 | 추정 금액/전액 반환으로 채우지 않고 승인된 증거 보완 절차 사용 |
| 환불 확정 후 지연 수신 | 승인된 기준 시각과 slot별 사용 상태에 맞춘 일정, 기존 사용시간 재지급 없음 |
| fallback 이후 정확 시각 수신 | 정정/비정정 계약대로 멱등 처리하고 감사 기록 보존 |
| scheduled 또는 이미 EXPIRED 구매 부분 환불 | 실제 금전 기록 유지, 다른 구매/무료권·기존 결과 훼손 없음 |
| refund-before-purchase | 늦은 구매로 권리 부활 없음, 실제 금전 증거 보존 |
| reserve/confirm/GRADING와 환불 경합 | Transaction 순서·LC durable gate·상태별 허용 예외 유지 |
| 학습 기록 삭제·탈퇴 계정 문의 | 현재 화면 개수 대신 승인된 최소 증거 사용, paid 자동 이전 없음 |

실제 provider가 해당 금액·시각·식별자를 제공하는지는 테스트의 입력 가정이 아니라 선행 검증 대상이다. 기존 전액 환불·무료/owner 회귀와 보안·보존 테스트도 유지한다.
