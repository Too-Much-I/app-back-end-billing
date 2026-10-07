# ADR-004: RevenueCat 기반 fixed-term premium 결제 계약

- 작성일: 2026-09-07
- 상태: 제품 정책·C9-S8의 9개 선택 승인 / 기술 상세 초안 작성 / 4주 상품·D1-A·D2-A 정책 승인(2026-09-07)
- 구현 상태: 미구현. 이 문서 작성은 PLAN·Jira·코드·배포 승인을 대신하지 않는다.
- 2026-10-07 [공동 기술 계약 개정안](../contracts/IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md) 사용자 승인: 원천 capturedAt+120일, snapshot 최대7일, operation120일·current stream 최소 coverage/폐기 stream120일, 신규 purchase token 최대30분/skew60초 및 발급 fence·snapshot/feed·복구 세대·retry 설계 채택. 이전 동 문서 범위의 미승인 표기는 이 기록으로 갱신한다. 상대 Identity 합의/기술·법적 적합성 검증과 실제 이관·구현 착수/배포/판매/purge 실행·개별 예외 보존 연장은 별도다.
- 2026-10-07 운영 주기 승인: 미구매 탈퇴 계정 삭제 대상 매시간 점검, 미확인 건 매일 재확인, 최초 미해결 판정 후 7일 이내 담당자 검토. 15일 조건부 삭제·거래 확인 선행을 유지하며 개인정보 예외 보존기간/실제 purge 활성화 승인은 아니다. 상세는 탈퇴·purge 설계 §3/5.5를 따른다.
- 2026-10-07 [탈퇴 수신·구매 계정·대사·purge 설계안](../contracts/PLAN-009-withdrawal-reconciliation-and-purge-contract.md) 작성. 승인된15일 조건부 정책의 기술 제안이며 새 route/보존/운영값 승인이 아니다. §5.10 cursor/activity와 009 반복 무변경 표현의 충돌은 별도 cursor 갱신안으로 보고했으며 합의 전 구현하지 않는다.
- 2026-10-06 후속 계획 작성: [PLAN-008 전체 로드맵](../plans/PLAN-008-fixed-term-payment-roadmap.md) 및 PLAN-009~013을 검토 대기 상태로 작성했다. public envelope·LC paid source wire·provider 인증/부분 환불 증거·schema manifest의 차이는 Phase 0 동결 대상이며 PLAN 작성만으로 이 ADR의 기술 초안이 최종 승인된 것은 아니다.
- 후속 우선순위: 2026-09-07 사용자 요청으로 [PLAN-007 무료 사용권 reader](../plans/PLAN-007-public-free-entitlement-query.md)를 결제와 독립적으로 먼저 작성했다. 결제 PLAN은 이후 번호를 사용한다. 무료 reader의 public envelope와 이 초안의 direct DTO 차이는 결제 구현 전 검토하며, 이번 무료 조회로 결제 정책·wire를 소급 변경하지 않는다.
- 기준: [결정 원장](../codex/CONTRACT_DECISIONS.md) C1-A/C2-A/C9-S1~S11, [결제 요약](../contracts/FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md)
- 기존 계약: [ADR-001](ADR-001-free-trial-internal-api-and-mongo-contract.md), [ADR-002](ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md), [ADR-003](ADR-003-retained-trial-owner-rebind-contract.md)

## 1. 5줄 결론

1. Apple·Google 모두 재구매 가능한 일회성 상품 5개이며, RevenueCat은 구매 연동을, Billing은 24·72·168·336·672시간 권리 계산을 맡는다.
2. 앱의 구매 성공만으로 지급하지 않고 검증된 webhook/API 거래를 Purchase·SubscriptionEntitlement·ledger에 원자 반영한다.
3. ACTIVE 유료권을 먼저 사용하고 무료 Claim·Grant는 보존한다. 다른 계정으로 결제 소유권을 자동 이전하지 않는다.
4. 환불은 원장에 취소 기록을 추가하고 신규 사용을 차단하며, Learning Core에는 durable outbox로 exact AttemptGroup 접근 철회를 전달한다.
5. 4주(28일) 상품과 정상 만료(D1-A)·환불 취소(D2-A)는 2026-09-07 승인됐다. 기술 상세 검증·별도 PLAN·Jira·구현은 남아 있으며 실제 Store/RevenueCat 값은 출시 입력이다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 사용자에게 보이는 흐름

```text
토선생 로그인 → 구매용 계정 번호 발급 → RevenueCat SDK 구매
→ Billing에 구매 확인 요청 → 검증 중이면 PENDING
→ 서버의 거래·권리 저장 완료 → 해당 기간 무제한 시험 시작
```

RevenueCat SDK가 Store 거래를 완료 처리한 시점과 Billing 지급 완료 시점은 다를 수 있다. 앱은 PENDING일 때 결제를 다시 유도하지 않고 같은 동기화 요청을 재시도한다. 이미 지급된 권리는 같은 토선생 계정으로 로그인하면 Billing에서 조회한다. 소모성 상품의 Store 복원 버튼은 Billing 원장을 대체하지 않는다.

기간은 첫 시험 시작 시점이 아니라 검증된 구매 시점부터 계산한다. 이미 유료기간이 남아 있으면 뒤에 연결한다. 알림이 늦었다는 이유로 수신 시각부터 새 기간을 주지 않는다. 환불·재가입·앱 재설치로 새 무료권을 만들지 않는다.

### 2.2 승인된 선택과 이번 기술 초안의 구분

| 구분 | 내용 |
| --- | --- |
| 이미 승인 | 일회성 5상품, RevenueCat 표준 completion, MEMBER 전용, 계정 고정, paid-first, 기간 연결, 환불 상태표, 보존 |
| 이미 승인 | public API 4개, sync hint/멱등성, Authorization+HMAC, 환경별 webhook, restore original owner, S2S·자동 refund handling OFF, webhook 직접 반영, durable revoke |
| 이번 기술 초안 | DTO·오류·한도, Mongo schema v5, adapter 필드 매핑, worker·outbox·이관·검증 항목 |
| 2026-09-07 추가 승인 | 30일 대신 4주(28일), D1-A 현재 Session 완료 허용, D2-A 환불 취소 수동 검토 |
| 2026-09-09 추가 승인 | Store 중심 환불 신청/환급 + 토선생 고객지원, 최초 자체 환불 API/UI 없음. 국내 5개 가격은 C9-S10에 기록 |
| 2026-09-09 C9-S11 | 지원 이메일 tosunsaeng093@gmail.com, 정상 만료 뒤 기존 결과/피드백 열람 허용, 최초 별도 할인 없음 |
| 2026-10-06 추가 승인 | REFUND 문의·팀 검토·2영업일 1차 답변·인증된 userId 연결·프론트 약관, Google/Apple별 처리, 부분 환불 뒤 해당 구매 잔여권 종료·다른 권리 보존 |
| 상태 구분 | 9/16 Apple 5종 및 RevenueCat Offering 매핑은 STORE_PRODUCT_REGISTRATION 기록 참조. 결제 application/schema·별도 PLAN·실거래 E2E/판매 준비 완료는 아님. 당시 설정 기록을 현재 운영 검증으로 간주하지 않음 |

## 3. 사용자 결정 사항 — 권장안 승인 완료

### D1. 환불이 아닌 정상 이용기간 만료 중 시험 — A 확정(2026-09-07)

**승인 A:** 유효기간 안에 confirm된 현재 Session은 Learning Core의 기존 시험·제출 기한 안에서 완료를 허용한다. 기간 만료 후 새 INITIAL·새 replacement Session은 이 만료된 이용권으로 만들 수 없다. GRADING은 완료하고 COMPLETED는 보존한다. 무기한 Session이나 별도의 연장시간을 추가하지 않는다.

- 장점: 정상 구매자가 시험 중 시간이 끝났다는 이유로 답안·채점을 잃지 않는다. 이용기간을 새 시험 시작의 경계로 명확히 설명할 수 있다.
- 단점: 실제 시험 완료 시각은 이용기간 종료보다 늦을 수 있다. 재응시와 현재 Session 완료를 구별해야 한다.
- 대안 B: 만료 즉시 OPEN도 차단하고 GRADING만 완료한다. 접근 종료는 단순하지만 정상 응시 중단과 민원 가능성이 커진다.
- 승인된 세부 적용: 만료 전 reserve를 commit한 동일 Session의 confirm은 기존 5분 Reservation 유효기간 안에서는 허용한다. 환불은 이 예외가 없으며 먼저 commit된 refund가 confirm을 거절한다. 다음 유효 paid slot이 있어도 이미 만료된 source group에 자동 재결속하지 않는다. 재시작하려면 새 권리의 새 INITIAL로 처리하고 기존 group 종료·guard 해제를 같은 Transaction에서 검증한다.
- D1-A는 정상 만료에만 적용하며 C9-S3 환불 정책을 완화하지 않는다. 정책 승인은 구현 완료를 뜻하지 않는다.

### D2. Apple이 이미 확정한 환불을 나중에 취소하는 경우 — A 확정(2026-09-07)

RevenueCat에는 Apple용 `REFUND_REVERSED` event가 있다. 단순 중복 구매가 아니라 앞선 환불이 취소되는 별도 상황이다.

**최초 출시 승인 A:** 이벤트를 안전하게 접수하고 `REVIEW_REQUIRED`로 격리·경보한다. 자동 새 지급·기간 복구·Session 재개는 하지 않는다. 담당자가 Store 최종 상태를 확인하고 별도 승인된 복구 절차로 처리한다. 원장 수동 덮어쓰기는 금지한다.

- 장점: 이미 환불로 앞당긴 다른 구매 기간을 되돌리거나 동일 거래를 이중 지급하는 위험을 줄인다.
- 단점: 사용자가 비용을 다시 부담하는 상황일 수 있으므로 신속한 문의 대응과 후속 복구 정책이 필요하다. 영구적으로 권리를 거절하는 정책이 아니다.
- 대안 B: reversal의 reversal을 원장에 추가하고 권리까지 자동 복원한다. 사용자 복구는 빠르지만 복구할 잔여기간·timeline 위치·이미 철회한 Session의 재개 여부를 추가로 정해야 한다.
- 승인된 D2-A에 따라 `refunded → owned` API 관측이나 `REFUND_REVERSED`를 일반 신규 구매로 처리하지 않는다.

### 3.1 지금 다시 정할 필요가 없는 것과 출시 입력

route, UUID, Mongo index, 재시도·보안 설계의 기술 검토는 개발 작업이다. 사용자에게 필드마다 재선택을 요구하지 않는다. 다음 실제 값만 판매/배포 전에 제공한다.

- Store product ID 각 5개, C9-S10의 국내 가격에 맞춘 실제 Store 설정·세금/할인·한국 판매/성인 범위 검증, 상품 심사 결과.
- RevenueCat project/app·Offering/Package 실제 매핑, credential validator 성공, sandbox 계정.
- public hostname/certificate, 환경별 issuer/JWKS·role·Lattice/ALB 정보와 Secret 관리 위치.
- 환불/개인정보 고지와 관할 보존 의무 검토. 본 문서는 법률 적합성 검토를 대신하지 않는다.

## 4. 주요 위험과 미확인 사항

### C9-S10 환불 창구와 구매 고지 — 2026-09-09 승인 반영

2026-10-06 구현 상태 갱신: 아래 Identity 코드 후속 표기는 로컬 cc076442 확인으로 갱신한다. REFUND enum, 인증된 활성 MEMBER/GUEST userId 귀속, 익명401 SUPPORT_REFUND_AUTH_REQUIRED, 임의 body userId400, 저장/멱등 재응답 전 필수 검사가 구현됐다. 문의와 실제 purchase 소유권/환불 연결, 실제 배포·Slack·프론트 E2E는 별도다. 이번 검토는 테스트 소스 확인이며 실행/운영 성공 증거가 아니다.

2026-10-06 추가 승인: 부분 환불 확정 시 해당 구매의 잔여 기간권을 종료한다. 다른 구매·무료권은 유지하고 기존 reflow 및 OPEN/RETAKE_AVAILABLE 차단·GRADING 완료·COMPLETED 보존 방향을 따른다. 금전 원장에는 실제 부분 반환액을 기록하며 전액 환불로 취급하지 않는다. 부분 환불은 기존 범위 제외에서 정책/계획 보완 대상으로 확장됐으나 provider 금액/시각/상태·후속 환불 멱등·원장 exact 설계와 구현은 아직 미완료다. Google 팀 검토 후 Store 실행, Apple 안내 후 Apple 최종 심사라는 운영 차이를 유지한다.

2026-10-06 운영 추가 승인: 환불 범위·금액은 팀 건별 검토, 운영상 기준은 실제 provider 환불 확정 시각, 2영업일 이내 1차 답변, 환불 문의 userId 필수 귀속, 약관은 프론트 담당이다. userId는 검증된 인증/현재 계정에서 서버가 자동 연결하고 클라이언트 지정값은 신뢰하지 않는다. 로그인 불가/탈퇴는 이메일·Store 예외 접수 후 구매자 확인으로 처리한다. 건별 검토는 법정 권리/Store 정책을 대체하지 않으며 확정 시각 선택이 법정 해지 효력·공제 공식의 확정은 아니다. 정확 provider timestamp/fallback, 필요한 부분 반환 실행·원장과 최종 고지는 출시 gate다. 상세 기준은 CONTRACT_DECISIONS C9-S10의 10/6 운영 승인을 따른다.

2026-10-06 추가 승인으로 앱의 Identity 문의 접수를 환불 상담 창구로 사용하고 문의 분류에 `REFUND`를 추가한다. 접수 후 운영자가 구매·이용 증거를 검토한다. 이는 9/9의 자체 문의 UI 제외 범위를 환불 상담 접수에 한해 대체하며, Identity 코드 변경은 별도 후속이다. Store 직접 신청 경로와 지원 이메일은 유지하고 실제 환급은 Store별 절차를 따른다. Billing 자체 환불 집행 endpoint·직접 송금·자동 심사는 승인하지 않았다. 문의 접수/운영 검토만으로 권리를 취소하지 않고 검증된 최종 provider 결과를 반영한다. 검증된 결과 반영·원장·권리/LC 차단 책임은 그대로 유지한다. 장애/미제공/법령상 요청을 Store 거절만으로 종료하지 않는다.

[구매·환불 안내 초안](../contracts/PREMIUM_PURCHASE_REFUND_NOTICE_DRAFT.md)은 게시 전 검토용이다. 실제 지원 채널/담당자와 고지·동의, 법적 분류·잔여기간 반환/공제·해지 효력, 부분 환불의 Store/RevenueCat 실행·금액/상태 수신·원장 수렴은 출시 전 확인한다. 현재 §5.7/§5.9로 부분 환불 전체가 구현/설계됐다고 간주하지 않는다. 별도 승인 없는 부분 반환 자동화나 사용정보 전달은 추가하지 않는다.

이 검토는 아래 G1~G8과 별개인 법률/운영 출시 조건이다. Store 중심 창구 승인이나 기술 테스트 성공만으로 판매 gate를 해제하지 않는다. 근거: [잔여기간 반환 조사](../contracts/REFUND_REMAINDER_RESEARCH-2026-09-08.md).

2026-09-09 C9-S11로 지원 이메일·만료 뒤 기존 결과 열람·출시 별도 할인 없음은 확정했다. 미정으로 적힌 이전 설명은 이 승인으로 대체한다. 실제 담당자/메일 접수, Store 가격·세금, LC/앱 열람 회귀 및 법적/부분 환불 gate는 유지한다. [실제 준비 상태 점검](../contracts/PAYMENT_READINESS_REVIEW-2026-09-09.md)에 RC credential 정상 표시·Google Pub/Sub 오류·실상품/Offering 미연결·webhook 미등록과 로컬 payment 미구현 근거를 분리했다. 설정 수정은 수행하지 않았다.

| Gate | 확인된 사실 / 남은 확인 | 완료 조건 |
| --- | --- | --- |
| G1 SDK 식별자 | SDK transaction 식별자와 API의 Store 구매 식별자는 platform/version별 확인 필요 | iOS transactionId, Android order ID가 webhook/API의 동일 거래로 수렴하는 sandbox fixture |
| G2 계정 연결 | webhook `app_user_id`는 last seen이며 aliases는 소유권 증거가 아님 | original customer·active reference exact binding, 다른 로그인/restore/계정 회전 음성 테스트 |
| G3 환불 | API v2 Purchase는 `owned/refunded`; 모든 CANCELLATION이 금전 환불은 아님 | Apple·Google consumed one-time 환불의 event/API 상태와 지연 확인 |
| G4 사전 환불 심사 | 공식 event 목록에서 일반 `REFUND_REVIEW` event는 확인되지 않음 | 실제 신호 없으면 상태를 만들어내지 않음. 새 integration은 재승인 |
| G5 누락 복구 | RC에 기록되기 전 앱 종료와 webhook 재시도 소진이 가능 | SDK 재시도·계정별 목록 복구. Store에만 남은 거래까지 복구된다고 주장하지 않음 |
| G6 revoke 전파 | 비동기 event는 다른 서버를 같은 순간에 멈추게 하지 못함 | LC 제출 경합·tombstone·outbox replay 검증, 전달 지연 경보 |
| G7 기존 모델 | 현재 AttemptGroup과 subject unique index는 Trial 중심 | paid 독립 owner와 free/paid 공통 동시성 guard reader-first 검증 |
| G8 운영 | Google credential 검증·상품·실제 HMAC/AWS E2E 미완료 | credential 검증, 분리된 staging, negative test와 실제 결제/환불 통과 |

G1~G8은 사용자가 임의 값을 선택해 해결할 사항이 아니라 구현·sandbox·배포 검증이다. 지원되지 않는 API, 없어진 transaction, 성공 예제를 근거로 검증 완료를 추정하지 않는다.

## 5. 현재 작업과 직접 관련된 설계

### 5.1 도메인과 catalog

| offerCode | durationSeconds | Store 유형 |
| --- | ---: | --- |
| PREMIUM_1D | 86400 | 양 Store consumable one-time |
| PREMIUM_3D | 259200 | 동일 |
| PREMIUM_7D | 604800 | 동일 |
| PREMIUM_14D | 1209600 | 동일 |
| PREMIUM_28D | 2419200 | 동일; 표시명 4주 |

- 4주는 정확히 28일·672시간·2,419,200초다. 달력 1개월 또는 30일로 계산하지 않는다. `PREMIUM_30D` 초안은 `PREMIUM_28D`로 대체한다. 실제 Store/RevenueCat ID는 아직 확인되지 않은 출시 입력이며 이미 등록된 30일 상품/거래가 있다면 기존 duration을 소급 변경하지 않고 별도 28일 상품 매핑을 준비한다.
- `BenefitDefinition=PREMIUM_SUBSCRIPTION`, credit/unit 없음. 무료 `FREE_EXAM_ONCE` 정의는 유지한다.
- catalog mapping은 `(environment, store, revenueCatAppId, storeProductId) → offerCode, catalogVersion, durationSeconds`다. API v2의 `product_id`는 `prod...` RC resource ID이며 Store product ID가 아니다. 별도 `revenueCatProductId` 매핑을 둔다.
- Offering·Package는 UI 노출용이다. custom package key 후보는 `premium1d/premium3d/premium7d/premium14d/premium28d`이며 실제 생성값은 config 입력이다. identifier 후보를 이미 생성된 Store ID처럼 문서에 넣지 않는다.
- 가격 표시·결제 동의 금액은 Store SDK의 현지화 가격이다. Billing은 앱이 보낸 가격을 검증 금액으로 쓰지 않는다. provider가 증명하지 않는 금액은 null이며 0이나 추정 환율로 채우지 않는다. 확인된 금액은 Decimal128과 통화·출처로 기록한다.
- 지급 당시 catalogVersion/duration을 Purchase에 snapshot한다. 판매 중단은 기존 거래 검증을 차단하지 않고 재매핑·기간 변경은 기존 Purchase에 소급하지 않는다.

### 5.2 계정 식별

2026-10-07 보존 승인: ACTIVE 회원의 stable 구매 계정/reference는 유지하며, 탈퇴 미구매 계정은 Identity 확정 withdrawnAt+15일 후 거래 대사 완료·실제 구매/처리 중 결제/미해결 건 없음이 확인될 때 명시적 purge로 계정/reference/사용자 연결을 정리한다. 로컬 구매 부재나 TTL만으로 삭제하지 않는다. 실제 구매는 기존 금융 보존, 미해결 건은 최소 증거/사유/재검토 기한으로 분리한다. 지연 탈퇴 event·구매/삭제 경합·재생성 방지를 검증한다. 구체 worker 주기/SLA·탈퇴 전달 계약은 후속 manifest이며15일은 법정기간 또는 전체 지연 보장이 아니다.

2026-10-07 사용자 승인: purchaseAccountRefId는 외부 연동용 opaque 식별자로 별도 필드 암호화 없이 저장한다. Atlas 저장 암호화·TLS·최소 권한·로그 제외는 유지하며 referenceId=_id를 사용한다. 이 값만으로 인증/거래 소유권을 인정하지 않는다. Store 재조회 token·credential·민감 provider ref의 보호 및 암호화 계약은 유지한다. 또한 현재 사용자 JWT는 만료/skew까지 수용하는 방향을 선택했으며 Identity의 신규/갱신 발급에서 ACTIVE MEMBER purchase 권한 검증은 별도 구현한다. Billing 동기 상태 조회는 추가하지 않는다.

1. Identity JWT `sub`로 사용자 확정, `billing:purchase` scope 검증.
2. `(environment,userId)`의 payment account를 get-or-create한다. 계정·reference 생성 자체로 Purchase나 무료권은 만들지 않는다.
3. `purchaseAccountRefId`는 Billing 발급 lowercase UUID v4, 환경별로 다르며 SDK의 custom App User ID로 사용한다.
4. 앱은 로그인 완료 후 reference로 SDK identify를 마치고 구매한다. 로그아웃/계정 전환 중 구매 UI를 비활성화한다. anonymous·가족 공유·TRANSFER/alias를 통한 소유권 이전은 금지한다.
5. 최초 거래 binding은 검증된 원구매자 reference와 Billing active mapping이 일치해야 한다. API `customer_id`와 `original_customer_id`, webhook `app_user_id`와 `original_app_user_id`가 다르거나 alias/transfer 흔적이 있으면 직접 지급하지 않고 재조회/격리한다.
6. 이미 binding된 transaction은 immutable `accountId`를 유지한다. 신규 transaction에 inactive reference를 사용하지 않는다. 과거 transaction·refund 검증에만 inactive alias를 허용한다.

SDK가 Apple `appAccountToken`과 Google obfuscated account identifier에 넣는 실제 값·변환은 SDK version별 G1/G2 검증 대상이다. 특히 Google 값을 raw UUID와 같은 문자열이라고 가정하지 않는다. hash/변환 결과가 account reference와 대응하는지 확인하며 그 값을 클라이언트가 주장하는 것만으로 지급하지 않는다.

### 5.3 Public API v1

2026-10-07 PLAN-009의 두 기반 API 상세는 [기술 계약 초안](../contracts/PLAN-009-payment-foundation-technical-contract.md)에 정리했다. 기존 승인 wire는 유지하고 route별 오류 header·Mongo 고정 window limiter·foundation subset index 추가는 검토 제안으로 분리했다. 해당 초안 링크는 신규 기술 제안 승인이나 실제 API 구현을 의미하지 않는다.

2026-10-06 사용자 승인으로 아래 사용자 public 4개 API는 기존 `PublicResponse<T>`를 사용한다. 응답 Content-Type은 application/json, Cache-Control은 no-store다. 성공은 `{isSuccess:true,code:"SUCCESS",message:"요청에 성공했습니다.",result:<아래 DTO>}`이며 아래 ProductsResponse/SyncResponse/EntitlementResponse 예제는 최상위 wire가 아니라 result 내부다. 202 PENDING도 접수 성공 envelope이며 결제 지급 완료라는 뜻은 아니다. internal의 body 없는204 및 provider webhook의 body 없는200에는 wrapper를 적용하지 않는다.

purchase-account 전체 성공 예시:

```json
{"isSuccess":true,"code":"SUCCESS","message":"요청에 성공했습니다.","result":{"purchaseAccountRefId":"ae3af4be-ffeb-4cec-99e8-0df45f6c27ad"}}
```

| Method·route | scope | request | 성공 |
| --- | --- | --- | --- |
| GET `/api/v1/payments/products` | billing:read | body/query 없음 | 200 ProductsResponse |
| POST `/api/v1/payments/purchase-account` | billing:purchase | body 없음 | 200 PurchaseAccountResponse |
| POST `/api/v1/payments/sync` | billing:purchase | SyncRequest + Idempotency-Key | 200 SyncResponse 또는 202 PENDING |
| GET `/api/v1/payments/entitlement` | billing:read | body/query 없음 | 200 EntitlementResponse |

새 Billing UUID는 lowercase UUID v4, opaque provider identifier는 1~255자 문자열이며 trim/case 변환을 하지 않는다. public request는 16 KiB 이하, duplicate field/trailing token/coercion/unknown field 거절. 사용자·환경·기간·가격·receipt/token·source 선택 필드는 받지 않는다.

`ProductsResponse`:

```json
{
  "catalogVersion": 1,
  "products": [
    {
      "offerCode": "PREMIUM_1D",
      "durationSeconds": 86400,
      "autoRenew": false,
      "storeProducts": [
        {"store": "APP_STORE", "productId": "example.premium1d", "packageId": "premium1d"},
        {"store": "PLAY_STORE", "productId": "example_premium1d", "packageId": "premium1d"}
      ]
    }
  ]
}
```

예제는 1개만 표시했으며 실제는 판매 활성화된 5개 이내다. 매핑 미설정 상품은 노출하지 않는다. `productId` 예제는 실제 등록값이 아니다. 가격은 앱의 Store SDK 결과와 결합한다.

`PurchaseAccountResponse`는 `{"purchaseAccountRefId":"ae3af4be-ffeb-4cec-99e8-0df45f6c27ad"}`이며 동시 요청도 같은 값으로 수렴한다.

`SyncRequest`:

```json
{"store":"APP_STORE","transactionId":"example-store-transaction"}
```

- store는 `APP_STORE|PLAY_STORE`. transactionId는 SDK에서 얻은 Store transaction/order identifier의 lookup hint이며 RC purchase resource ID·Google purchaseToken이 아니다. Android SDK가 order ID를 제공하지 않는 경우 임의 token으로 대체하지 않고 G1에서 DTO/SDK 대안을 먼저 정한다.
- 필수 `Idempotency-Key`는 lowercase UUID v4, body에 operationId 없음. `(environment,userId,operationId)` unique와 검증된 body digest를 사용한다.
- 같은 key·같은 body는 같은 operation/purchase에 수렴한다. 같은 key·다른 body는 409. PENDING을 immutable 202 snapshot으로 영구 캐시하지 않는다.
- 먼저 sync command를 durable 저장하고 로컬 Purchase 또는 RC API로 확인한다. 외부 요청 중 Mongo Transaction을 열어두지 않는다. 처리 완료는 기존 Purchase commit 확인을 포함한다.
- 200: `{"operationId":"e11a3e26-ece0-4dd8-a9ce-cf21a57d8798","status":"APPLIED","purchaseId":"0327e8fb-536b-4c56-b532-e0a20b85269c","purchaseStatus":"VERIFIED"}`.
- 202: 동일 operationId에 `status=PENDING`, purchaseId/purchaseStatus=null, `Retry-After: 5`. durable 검증 대기 상태에서만 반환한다. 명백한 소유권 불일치·미지원 상품을 성공 대기로 위장하지 않는다.
- APPLIED는 동기화 반영 완료이지 현재 이용 가능 보장이 아니다. 재전송 때 이미 환불됐으면 current purchaseStatus를 반환하고 권리는 별도 조회한다. 정상 완료 뒤 재요청으로 기간을 새로 계산하지 않는다.

`EntitlementResponse`:

```json
{
  "asOf": "2026-09-07T04:00:00Z",
  "paidActive": true,
  "activeEntitlementId": "032fdc5a-d6c0-43dd-b68b-14e09a8d420d",
  "activeOfferCode": "PREMIUM_1D",
  "activeEndsAt": "2026-09-08T03:00:00Z",
  "continuousAccessEndsAt": "2026-09-11T03:00:00Z",
  "nextStartsAt": null,
  "hasPendingSync": false
}
```

paidActive는 `startsAt <= now < endsAt`이며 VERIFIED·not revoked·not review 조건을 함께 검사한다. active가 없으면 active 필드와 continuousAccessEndsAt=null, 미래 slot이 있으면 nextStartsAt만 반환한다. continuousAccessEndsAt은 빈틈 없이 연결된 사용 가능 slot의 끝이지 모든 미래 slot 중 최대 끝이 아니다. 무료권 조회/지급은 이 API의 부수효과가 아니다.

사용자 public 공통 오류 envelope는 `{"isSuccess":false,"code":"INVALID_REQUEST","message":"올바르지 않은 요청입니다.","result":null}`. message는 고정된 안전 문구이며 provider 응답/식별자를 넣지 않는다. 기존 direct 오류 초안의 top-level traceId는 PublicResponse에 임의 추가하지 않고 기존 trace 로그 규격을 유지한다. 필요 시 응답 추적 header는 별도 기술 계약으로 정한다. 다음 HTTP/code 표는 유지하되 사용자 JWT 오류와 provider HMAC 오류의 인증/응답 경로를 분리한다.

| HTTP | code | 조건 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 형식·UUID·필드 위반 |
| 401 | UNAUTHENTICATED | JWT/HMAC 검증 실패 |
| 403 | FORBIDDEN | scope 부족 |
| 409 | IDEMPOTENCY_KEY_CONFLICT | sync key payload 충돌 |
| 409 | PURCHASE_BINDING_CONFLICT | 본인 거래로 검증되지 않음; 다른 owner 정보 미노출 |
| 422 | UNSUPPORTED_PRODUCT / UNSUPPORTED_CONTRACT | catalog 또는 provider 계약 미지원 |
| 413 / 415 | PAYLOAD_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE | body 크기/형식 |
| 429 | RATE_LIMITED | 사용자 한도, Retry-After 초 단위 |
| 503 | BILLING_TEMPORARILY_UNAVAILABLE | durable 저장 불가/서버 처리 불가 |

초기 앱 rate limit: subject별 products/entitlement 각 60회/분, purchase-account 10회/분, sync 12회/분. 서버 전체 RC 호출은 별도 quota limiter를 사용한다. 이 수치는 구매 횟수 제한이 아니라 요청 남용 제한이며 분산 인스턴스 합산 enforcement가 필요하다.

### 5.4 RevenueCat webhook ingress

`POST /api/v1/payments/revenuecat/events`는 사용자 JWT가 아닌 전용 provider 인증만 허용한다. 환경마다 다른 hostname·Authorization·HMAC secret을 사용하며 payload environment도 재검증한다. 내부 Identity strict decoder를 재사용하지 않는다.

1. Content-Type JSON, UTF-8, 압축 body 미허용, raw body 상한 256 KiB. 기존 internal 16 KiB 계약의 예외는 이 provider ingress에만 적용한다.
2. Authorization 설정값을 constant-time 비교한다. HMAC 검증 전에 body/parser 오류를 로그에 덤프하지 않는다.
3. `X-RevenueCat-Webhook-Signature: t=<unix seconds>,v1=<hex>`를 공식 규격대로 parse한다. 현재 UTC와 ±300초, `HMAC-SHA256(secret, timestamp + "." + rawBodyBytes)` constant-time 비교. JSON 재직렬화 후 서명을 검증하지 않는다.
4. 서명된 envelope `api_version=1.0`, `event.id/type`를 검사한다. duplicate field/trailing token/scalar coercion은 거절하되 provider의 추가 unknown field는 허용하고 버린다. JSON depth 32, 단일 문자열 16 KiB 상한. 소비하는 identifier/time 필드는 별도 제한을 적용한다.
5. 정규화한 최소 event와 digest를 inbox에 durable commit한 후 body 없는 200. 결제 반영 worker나 외부 API 완료를 기다리지 않는다. commit 불명은 재조회 후 확인되지 않으면 503.
6. 같은 event ID·같은 의미 digest는 200 duplicate. 다른 의미 digest는 409 EVENT_ID_CONFLICT 및 경보. 새로운 event ID의 같은 거래는 Purchase business key에서 수렴한다.
7. 잘못된 인증은 401, 형식은 400, 지원하지 않는 envelope version/다른 환경·app은 422. 인증된 unknown type은 `IGNORED_UNSUPPORTED`를 durable 기록하고 경보 후 200. 필드 없는 TEST는 금전 반영 없이 성공 접수한다.

처리할 구매 event의 필수값은 id/type, environment, app_id, store, transaction_id, product_id, purchased_at_ms, event_timestamp_ms 및 account binding에 필요한 identity다. null/missing을 빈 문자열·0으로 바꾸지 않는다. envelope는 유효하나 의미 판정 정보가 부족하면 `NEEDS_LOOKUP`과 최소 암호화 reference를 저장한다. lookup 불가능하면 REVIEW_REQUIRED로 격리한다.

canonical digest는 raw JSON이 아니라 버전 지정된 정규화 command에 SHA-256을 적용한다. known field의 값·명시적 null, binding 검증 결과를 포함하고 provider unknown field·subscriber_attributes는 포함하지 않는다. property 순서/whitespace는 결과에 영향이 없다. event timestamp와 purchase timestamp를 구분하고 타임스탬프는 UTC milliseconds로 정규화한다. signature timestamp는 delivery metadata이므로 digest에서 제외한다.

단일 RC project의 SANDBOX와 PRODUCTION integration을 별도로 설정한다. TEST/TRANSFER처럼 environment/app이 없을 수 있는 유형은 해당 ingress의 인증 context에만 귀속시키고 금융 처리는 하지 않는다. 실제 구매에 환경/앱이 없는 경우 추정 지급하지 않는다.

### 5.5 Provider event → 정규화 결과

| RevenueCat event/관측 | Billing 처리 |
| --- | --- |
| NON_RENEWING_PURCHASE | full binding·catalog·Store·환경·시간·quantity 검증 후 VERIFIED 후보 |
| CANCELLATION | one-time 환불임이 입증되는 fixture만 direct refund; 그 외 API 조회. 단순 구독 해지를 환불로 간주하지 않음 |
| API Purchase status=refunded | 같은 business key의 REFUNDED와 append-only reversal |
| API status=owned | 미반영 거래는 VERIFIED 후보. 기존 REFUNDED를 되돌리는 근거로 단독 사용 금지 |
| REFUND_REVERSED | D2-A 승인. 신규 지급하지 않고 REVIEW_REQUIRED로 보존·경보하고 승인된 복구 절차로 처리 |
| TRANSFER / SUBSCRIBER_ALIAS | Billing owner 변경 없음, 격리·경보·필요한 재조회 |
| TEMPORARY_ENTITLEMENT_GRANT | 검증되지 않은 임시 RC 권리로 지급하지 않음 |
| INITIAL_PURCHASE / RENEWAL / EXPIRATION | 현재 one-time 상품 계약과 불일치. 구독 권리나 만료를 만들지 않음 |
| TEST / unknown | 금전 효과 없는 durable ignored |

quantity는 초기 단일 구매 1만 지원한다. 누락 시 해당 Store가 단일 수량임을 확인한 adapter fixture에서만 1로 정규화한다. quantity>1을 1로 잘라 지급하지 않고 REVIEW_REQUIRED로 보낸다. `is_family_share=true`, promotional/test_store 등 미승인 Store는 지급하지 않는다. null entitlement_ids는 consumable을 RC Entitlement에 연결하지 않은 정상 경우다.

현재 확인된 RC event 목록에 일반 refund review 신호가 없으므로 `REFUND_REVIEW` 자동 진입 adapter는 비활성 상태다. provider가 실제 지원하는 인증 신호와 종료 증거를 검증하기 전 client 요청·CANCELLATION UNKNOWN·타임아웃을 refund review로 해석하지 않는다.

### 5.6 RevenueCat REST adapter v2

base URL은 `https://api.revenuecat.com/v2`. 서버용 V2 Secret API key와 `Authorization: Bearer ...`를 사용한다. SDK public key·V1 key를 사용하지 않는다. 최소 권한은 `customer_information:purchases:read`이며 이 ADR에 필요 없는 refund/write/transfer API 권한은 부여하지 않는다.

| 용도 | 공식 endpoint |
| --- | --- |
| 사용자 기준 구매 검증/누락 복구 | GET `/projects/{project_id}/customers/{customer_id}/purchases?environment={sandbox|production}&limit=100` |
| SDK hint 조회 | GET `/projects/{project_id}/purchases?store_purchase_identifier={hint}` |
| 알려진 거래 최신 상태 | GET `/projects/{project_id}/purchases/{purchase_id}` |

customer_id는 인증된 사용자의 Billing reference에서만 얻는다. hint 검색은 결과를 찾기 위한 보조 수단이며 owner 검증을 생략하지 않는다. API `id`는 RC purchase ID, `store_purchase_identifier`는 Store 거래/order ID다. `original_transaction_id`를 반복 구매의 unique key로 사용하지 않는다.

API Purchase에서 `customer_id`, `original_customer_id`, `product_id`, `purchased_at`(ms), `quantity`, `status=owned|refunded`, `environment=sandbox|production`, `store=app_store|play_store`, `store_purchase_identifier`, `ownership`을 검증한다. v2 product resource는 사전 검증된 catalog의 app·Store product 매핑으로 해석한다. ownership은 본인 구매만 지원하며 family share는 허용하지 않는다. enum은 webhook 대문자와 API 소문자를 각 adapter에서 명시적으로 매핑한다.

응답은 1 MiB 상한, page 100, cursor 유지. next_page는 승인된 host/project/customer/path인지 검증한 후 필요한 cursor만 사용하며 외부 URL을 그대로 따라가지 않는다. 페이지 누락·상한 도달은 조회 미완료이지 거래 부재가 아니다. 404·empty list·network error는 환불 증거가 아니다.

connection timeout 2초, response 5초, redirect 금지. sync request의 외부 조회 budget은 5초이고 넘으면 durable PENDING에서 worker로 계속한다. 429는 Retry-After 준수, 423/5xx/network는 jitter backoff. 401/403은 BLOCKED_AUTH 경보로 credential 수정 전 반복 호출을 멈춘다. provider body·URL의 customer/transaction query는 access log·HTTP client trace에서 제거한다.

공식 API 기본 Customer Information quota는 조회일 기준 480회/분이다. 초기 Billing 합산 budget은 240회/분, 동시 외부 요청 4개로 두고 양 환경·다른 integration 사용량을 측정한다. quota를 여러 key 생성으로 우회하지 않는다.

### 5.7 Purchase·timeline Transaction

2026-10-07 승인: RC 증거 부족 시 Google 소모성 상품 부분 환불에 한해 인증된 Google Orders 읽기 조회를 보완 신뢰 경로로 허용한다. 신규 구매 지급은 RC 경로를 유지한다. 기존 검증된 Purchase와 package/환경/거래/소유 연결을 검증하고 성공 확정 부분 환불만 금융 사실 및 잔여권 종료에 반영한다. PENDING·404·타임아웃을 환불 완료/미환불로 추정하지 않는다. Google 조회는 자동 환불 실행이나 임의 수동 입력을 허용하지 않는다. RC/Google 관측의 중복 합산 방지와 정정/추가 반환 exact 모델·권한·실거래 fixture는 구현/활성화 전 gate다. Apple 지원으로 확대 해석하지 않는다.

2026-10-06 F1~F4 A안 승인: paid 채점은 전용 멱등 승인 API로 exact Session의 GRADING 전이와 영속 승인 증거를 함께 commit한 뒤 허용한다. 기존 event의 STALE/duplicate204는 승인 증거가 아니며 무료 v1 계약은 유지한다. guard 전이표·관련 writer 짧은 제한 이관은 PLAN-011/013, 환불 기능 선행 자동 활성화 gate+운영 최종 승인은 PLAN-013을 따른다. exact route/DTO/IAM·승인/fan-out schema는 구현 전 동결하고 실제 이관 시간/실행·배포·판매 승인은 별도다.

business key는 `(environment,store,revenueCatAppId,storeTransactionKey)`다. storeTransactionKey는 확인된 원본 식별자의 keyed lookup hash이며 원본은 필요한 경우 암호화한다. SDK hint 자체로 key나 Purchase를 생성하지 않는다. RC purchase ID는 별도 unique lookup으로 연결한다.

```text
검증된 provider 관측 (Transaction 외부)
→ 동일 사용자 payment timeline root CAS
→ Store transaction unique 확인
→ Purchase + 단일 entitlement + 지급 ledger + sequence 갱신
→ inbox/command 완료
→ Mongo majority Transaction commit
```

- sequence는 계정별 첫 VERIFIED commit 순서로 부여한다. 동일 Transaction 재시도는 새 sequence/entitlement를 만들지 않는다. 지연된 과거 구매가 뒤늦게 오면 기존 commit sequence를 재정렬하지 않는다.
- initialStartsAt=`max(purchasedAt,currentTimelineEnd)`, endsAt=startsAt+duration. UTC Instant, 끝 시각 exclusive. current state 표시는 시간에서 계산하며 worker가 EXPIRED를 찍어줄 때까지 접근을 열어두지 않는다.
- 금전 상태는 PENDING/VERIFIED/REFUND_REVIEW/REFUNDED/REVOKED와 분리하고 REVIEW_REQUIRED/BLOCKED_AUTH 같은 운영 처리 상태를 별도 필드로 둔다.
- 구매보다 refund가 먼저 오면 business key별 terminal tombstone Purchase를 만들어 이후 구매 event가 지급하지 못하게 한다. binding 불명 상태는 소유권 확정 전 격리하며 owner를 임의 추정하지 않는다.
- 일반 늦은 VERIFIED 또는 API owned는 REFUNDED/REVOKED를 되돌리지 않는다. provider event 생성 시각만으로 상태를 last-write-wins하지 않는다.
- unknown commit은 같은 business key/command로 결과를 재조회한다. transient retry는 기존 MongoTransactionExecutor 패턴을 재사용하되 provider 호출을 Transaction 재시도에 포함하지 않는다.

최초 검증된 refund의 권리 종료 Transaction은 purchase root 접근 종료, 원 entitlement revoke, 검증된 금전 효과 기록, 남은 timeline reflow, durable fan-out job을 함께 commit한다. 2026-10-06 F2 A안 승인으로 모든 group/outbox 단일 Transaction 초안을 대체한다. 예약/guard/group 정리와 group별 outbox 생성은 재개 가능한 bounded worker로 수행한다. 모든 paid 승인 경로는 root 차단을 검사하고 환불과 공통 CAS 쓰기로 경합하므로 전파 대기 중에도 새 사용을 허용하지 않는다. 선행 GRADING은 exact Session의 영속 승인 증거로 보호한다. cursor/checkpoint·lease/fencing·멱등 outbox·완료 검증은 PLAN-012에 따라 구현 전 동결하며 LC 전파 지연 경보를 유지한다. 불변 ledger는 수정하지 않고 이전·이후 schedule과 원인 operation을 새 entry에 저장한다. 2026-10-06 R1 승인: 정상 환불은 처리 후 종료하며 일반 반복 환불 기능은 만들지 않는다. 다만 외부 Store의 실제 추가 반환/정정은 금융 사실로 별도 반영하고 이미 종료된 권리/reflow를 반복하지 않는다. purchase 접근 terminal을 후속 금전 사실 수신 금지로 사용하지 않는다. 금액이 불명확하면 전액으로 추정하지 않고 대사하며 확정된 접근 종료 사실과 금전 증거 보완 상태를 분리한다. 정확 provider 환불 식별/증분·누적 변환/후속 정정 모델은 PLAN gate다.

reflow 대상은 취소 slot 뒤 아직 시작하지 않은 VERIFIED slot이다. 2026-10-06 R2 승인으로 이전 refundConfirmedAt 기준 및 실제 확정 시각 부재 시 확인 시각으로 대체하던 초안을 대체한다. `providerConfirmedAt`은 검증된 Store 확정 시각(없으면 null), `firstVerifiedAt`은 최초 검증 시각, `appliedAt`은 최초 성공한 권리 종료 Transaction에 저장한 고정 반영 기준 시각이다. 시간 출처를 남기고 실제 Mongo commit timestamp라고 가장하지 않는다. 실패 Transaction은 효과가 없으며 unknown commit은 기존 저장 결과를 확인한다.

후속 미시작 slot은 기존 sequence/duration을 유지하며 `max(appliedAt, 앞선 유효 entitlement endsAt)`부터 앞당기되 기존 시작시각보다 늦추지 않는다. 이미 시작/완료된 다른 slot은 재시작·재지급하지 않는다. 예: Store 10:00 확정, Billing 18:00 반영, B 7일권이 여전히 대기 상태이면 B는 18:00부터 7일이며 지연 8시간을 소급 소진하지 않는다. B가 기존 일정대로 이미 시작됐다면 그 일정을 유지한다. 중복 알림·추가 반환·후속 정확 provider 시각 확보만으로 appliedAt이나 schedule을 다시 움직이지 않는다. 뒤늦은 시각 보완은 감사 근거로만 추가한다. 최초 반영 전 접근/LC 차단 전파 지연은 관측·재조회·경보로 관리하고 소급 과금하지 않는다. 법적 해지 기산점/환불액 계산과 시스템 시각을 동일시하지 않는다.

한 계정의 매우 긴 scheduled timeline 때문에 Transaction 크기·시간 한도를 넘을 경우 부분 commit하지 않는다. staging에서 크기를 검증하고 한도를 넘는 사례는 REVIEW_REQUIRED로 격리한다. 판매 횟수/최대 보유기간 같은 새 제품 제한은 이 문서에서 임의 추가하지 않는다.

### 5.8 기존 Reservation과 paid 연결

현재 코드에는 Trial 중심의 subject/Claim/consumption 연결만 있다. paid 구현은 가짜 TrialClaim을 만들어 기존 경로에 넣지 않는다.

- 별도 payment account의 stable `paidSubjectRefId`를 사용하며 Claim을 필요로 하지 않는다. 현재 `billing_subject_links`는 Trial owner 연결로 유지한다.
- Reservation·AttemptGroup에 `authorizationSource=TRIAL|SUBSCRIPTION`, nullable `subscriptionEntitlementId`, `purchaseId`를 추가한다. legacy missing source는 TRIAL로 읽는다. trialClaimId/consumptionLedgerEventId는 paid에서 null이며 대신 payment usage ledger ID를 연결한다.
- paid reserve는 기존 ReserveResponse의 INITIAL/REPLACEMENT, group/session/mock/operation 계약을 유지한다. 앱이 source를 보내지 않는다. 무료 phone continuation은 ADR-003 exact echo를 유지하고 유료권 이전에 재사용하지 않는다.
- 신규 INITIAL에서만 ACTIVE paid 우선 선택한다. 이미 확정된 무료 group의 명시적 replacement는 기존 consumption을 유지하며 뒤늦은 유료 구매 때문에 source를 바꾸지 않는다.
- INITIAL reserve 시 paid allocation unit을 만들지 않고 source snapshot/usage audit를 저장한다. 5분 expiry·cancel은 audit만 종료하며 무료 available unit을 증가시키지 않는다.
- paid confirm은 source refund/revoke·동일 Reservation·Session을 재검증한다. refund 먼저면 409 `ENTITLEMENT_REVOKED`, 기간 경계는 D1에 따른다. 기존 CANCELED/EXPIRED repair 금지와 confirm 멱등성은 유지한다.
- REPLACEMENT는 같은 group/mock의 새 Session이고 추가 기간/credit를 지급하지 않는다. source가 refunded/revoked면 다른 유료권·무료권이 있어도 그 group을 복원하지 않는다.

free와 paid의 별도 subject unique index만으로는 사용자당 여러 active 시험을 막지 못한다. `exam_owner_guards`를 `(environment,userId)` unique로 두고 reserve/confirm/cancel/expiry/terminal과 Trial owner rebind가 같은 Transaction에서 소유·version을 검사하도록 reader-first 확장한다. guard는 현재 operation/group/source를 참조하는 동시성 장치일 뿐 지급 원장이 아니다. target의 다른 active 시험과 충돌하는 rebind는 기존 pending 계약으로 수렴한다. 단순 사전 조회나 프로세스 mutex로 대신하지 않는다.

### 5.8.1 구매별 최소 이용 증거 — 2026-10-06 설계 보완

사용자가 구매별 증거 연결 보완을 요청했다. 아래는 결제 PLAN에 포함할 설계이며 기존 무료 코드·wire·schema를 이미 변경한 것은 아니다. 환불액 자동 산정이나 학습 원문 보존은 추가하지 않는다.

**조회 연결:** 인증된 문의자 → 소유권 검증된 `purchaseId` → 해당 구매의 `subscriptionEntitlementId` → Reservation/AttemptGroup → AttemptSession. 문의의 userId는 후보 구매 검색 범위이지 특정 거래 선택/소유권의 충분한 증거가 아니다. 문의 ID와 검증된 purchaseId의 운영 연결을 남기며 여러 구매가 있으면 대상을 명시적으로 확인한다. 클라이언트/운영자가 제시한 거래 힌트도 환경·Store·구매 owner를 검증한다. 탈퇴 후 새 계정에는 기존 구매를 이전하지 않고 예외 본인 확인 절차를 따른다. Identity 문의에 purchaseId 필드를 즉시 추가하는 승인이 아니며 exact 인계 DTO는 별도다.

| 최소 증거 | 기록/판정 방법 |
| --- | --- |
| 구매 출처 | §5.8의 authorizationSource·purchaseId·subscriptionEntitlementId를 paid Reservation/Group 및 Session의 불변 연결로 유지. Session에 snapshot 또는 영속 parent 참조를 둘 exact schema는 PLAN에서 고정 |
| 생성 승인 | reservationId·attemptGroupId·sessionId·INITIAL/REPLACEMENT·reserved/confirmed/canceled/expired 시각. 기존 생성 Transaction/ledger에 함께 반영하고 command TTL에 의존하지 않음 |
| 이용 결과 | 기존 OPEN/GRADING/RETAKE_AVAILABLE/COMPLETED 및 Session 상태, 확인된 상태 전이 시각·수신 시각, 기존 정규화 failure code/evidenceVersion. 실제 제공 완료는 승인된 completion evidence가 있는 COMPLETED만 인정 |
| 완료 근거 | 기존 requiredFeedbackQueryable/validScoreQueryable/summaryQueryable 최소 boolean의 완료 당시 증거와 버전. 이후 원본 삭제에도 당시 완료 사실로만 사용하며 현재 열람 가능성을 보장하지 않음 |
| 증거 품질 | 상태 이벤트 누락/역순/확인 중이면 불명으로 표시하고 0회/미이용/정상 제공으로 단정하지 않음. client 주장이나 timeout을 확정 실패 사유로 만들지 않음 |
| 운영 감사 | 문의 참조·선택 purchase·검토자/승인 참조·판정 시각·정규화 사유/근거 버전을 제한된 운영 경로에 기록. API/일반 로그에 식별자나 문의 본문을 복제하지 않음 |

기존 payment usage ledger와 AttemptGroup/Session projection을 우선 재사용한다. 새로운 중복 학습 이력 저장소를 만들지 않는다. 상태 반영과 최소 증거 기록은 같은 로컬 Transaction으로 수렴시키고 이벤트 멱등성·Session fencing을 유지한다. 외부 원본 조회/문의 서비스 호출은 Transaction 안에서 수행하지 않는다.

**운영 화면/조회 판정:** 무료/TRIAL은 유료 구매 이용 건수에서 제외한다. RESERVED/CANCELED/EXPIRED만 있으면 제공 완료로 세지 않는다. 재응시는 같은 group과 별도 Session 시도로 구분하고 시도 수를 완료 시험 수나 금액 공제로 환산하지 않는다. 실패 후 성공은 실패 시도와 성공 결과를 함께 확인한다. 같은 구매의 여러 group을 독립 집계하고 현재 timeline reflow/환불/다른 유료 구매 때문에 과거 source를 다시 배정하지 않는다. 현재 학습 목록에서 삭제됐다는 이유로 미사용으로 되돌리지 않는다. 삭제 여부의 별도 wire는 이번에 추가하지 않으며 원본 부재와 미사용을 동일시하지 않는다.

**개인정보/보존:** purchase·payment ledger에는 기존 승인된 최소 정규화 금전/이용 증거와 5년 보존·erasable 연결 purge 규칙을 적용한다. 이 승인이 모든 AttemptSession이나 학습 개인정보를 5년 보관한다는 뜻은 아니다. PLAN에서 ledger에 남길 최소 summary와 Session/Group 연결의 필요기간·purge 순서·삭제 후 운영 확인 범위를 구분해 확정하며, 7일 command/120일 inbox를 유일한 증거로 삼지 않는다. 답안·음성·점수 상세·피드백·raw event/receipt는 복제하지 않는다. 문의 90일 보존은 원장 보존과 별개이며 상담 본문 전체를 5년 원장으로 이관하지 않는다. 운영자는 필요한 구매와 최소 증거만 권한 검증 후 조회하고 접근을 감사한다. 공개 사용자 API나 신규 관리자 route는 별도 계약 없이 열지 않는다.

**PLAN 필수 검증:** 같은 계정의 두 구매/무료 시험 분리, reservation만 취소, replacement 실패→성공, 완료 후 학습 원본 삭제, command/inbox 만료, 이벤트 중복/역순/유실, refund/reflow 뒤 출처 불변, 다른 userId의 purchase 접근 거절, 탈퇴·재가입의 paid 자동 이전 금지, 최소 연결 purge 후 불명 증거 표시. 실제 provider 거래 검증과 LC 완료 계약은 별도 E2E gate다.

### 5.9 환불과 Learning Core durable access-revocation

| refund commit이 관찰한 상태 | Billing | Learning Core |
| --- | --- | --- |
| RESERVED | cancel·revoke evidence, confirm 거절 | 이미 생성된/뒤늦은 해당 group Session 차단 |
| OPEN | 접근 REVOKED, 새 답안·제출·replacement 승인 금지 | 로컬 deny projection, 채점 신규 시작 금지 |
| RETAKE_AVAILABLE | 접근 REVOKED, replacement 금지 | 새 Session 금지 |
| GRADING | source revoke, 해당 Session의 terminal 수렴만 허용 | 이미 접수된 채점·Summary 완료 |
| COMPLETED | 구매 취소 원장만, history 보존 | 결과 삭제/owner 이전 없음 |

Learning Core 신규 route 초안: `POST /internal/v1/entitlements/access/events`. Billing→Learning Core 전용 Lattice AWS_IAM+SigV4, `vpc-lattice-svcs:Invoke`의 이 method/path만 허용한다. 기존 Identity→Learning Core UserMerged JWT 인증을 교체하지 않는다.

```json
{
  "eventId": "1472d2f7-2551-4c77-8467-2fbd9717755a",
  "eventType": "AttemptGroupAccessRevoked",
  "schemaVersion": 1,
  "producer": "billing",
  "consumerScopeId": "example-learning-scope",
  "occurredAt": "2026-09-07T04:00:00Z",
  "userId": "59bd82fb-b1ad-4e2a-aede-1b6d5d4f747a",
  "attemptGroupId": "d41f1d68-8089-4e77-877e-c857d08f8e96",
  "accessVersion": 1,
  "reason": "PAYMENT_REFUNDED",
  "gradingSessionId": null
}
```

- internal 16 KiB strict v1: exact eventType/producer/scope, UUID, positive int64 accessVersion, UTC occurredAt, unknown/duplicate/trailing/coercion 거절. reason은 PAYMENT_REFUNDED|PAYMENT_REVOKED만 허용한다.
- group 전체 신규 사용은 막는다. gradingSessionId는 refund Transaction 전에 Billing이 GRADING으로 승인한 exact Session만 1~128 opaque 값으로 보내며 그 외 null이다. source payment/Store token/금액/phone/payload는 전송하지 않는다.
- outbox는 `(attemptGroupId,accessVersion)` unique이며 동일 retry의 eventId/wire는 불변이다. LC inbox와 deny projection을 local Transaction에서 commit한 뒤 body 없는 204. 같은 ID·같은 digest duplicate도 204, 다른 digest 409, unsupported 422, transient 503.
- LC Session projection보다 revoke가 먼저 도착해도 group deny tombstone을 저장하고 204한다. 뒤늦은 Session 생성·replacement가 tombstone을 우회하지 못한다. 새 revision이 낮으면 stale 204이며 같은 revision의 다른 의미는 conflict다.
- 후속 event가 상태를 되돌리지 못하도록 group access 상태 `ALLOWED|REVOKED`를 학습 status와 분리한다. 늦은 OPEN/GRADING event로 deny를 해제하지 않는다. 허용된 gradingSessionId만 terminal 반영한다.
- **제출 경합 gate:** LC가 Job을 먼저 저장했어도 Billing에 GRADING이 반영되기 전 refund가 commit되면 선행 GRADING으로 인정하지 않는다. paid 신규 채점 실행은 Billing GRADING 수신 commit 확인 후 진행하도록 LC durable publisher/Job gate를 맞춘다. 이 cross-service gate가 없는 현재 코드에서 동시 제출/환불이 안전하다고 주장하지 않는다. 기존 무료 event DTO를 임의로 변경하지 않는다.
- 비동기 전달 전 LC 로컬에서 발생한 답안 동작을 즉시 되돌린다고 보장하지 않는다. Billing 신규 reserve/confirm 차단은 refund commit 시점, LC 실제 접근 차단은 deny projection commit 시점이다. outbox 지연을 감시하고 장애 시 paid 신규 입장을 제한하는 운영 절차가 필요하다.

이 route/DTO는 상대 서버에 전달할 계약 초안이며 이번 작업에서 Learning Core 코드를 작성하거나 배포하지 않는다. 상대 reader·Job gate·Lattice listener가 준비되기 전 paid 기능을 활성화하지 않는다.

### 5.10 Worker·관측·보존

TMI-199 구체화: [account/activity 정합성](../contracts/TMI-199-PAYMENT-LIFECYCLE-FOUNDATION.md#53-반복-account-호출과-activity-cursor-정합성)에 따라 최초 account/ref/cursor 원자 생성, 반복 호출의 account/ref 안정성 및 별도 lifecycle CAS·cursor lastActivityAt 갱신을 분리한다. 아래 원래 activity 요구를 유지하며 production 코드/worker는 이번 검증에서 추가하지 않았다.

- provider inbox와 revoke outbox: READY/PROCESSING/RETRY/PUBLISHED 또는 APPLIED/IGNORED/REVIEW_REQUIRED/DEAD_LETTER. lease 30초, fencing token으로 늦은 worker commit 방지, scheduler 5초, batch 100.
- transient exponential backoff 기본 5초~1시간 full jitter, 최대 24시간 자동 재시도 후 경보·dead-letter. 401/403은 BLOCKED_AUTH, 409/422는 계약 확인 대상으로 격리한다. outbox 실패가 금융 refund를 롤백하지 않는다. 수정 후 같은 event ID로 운영 승인 replay한다.
- PENDING 확인 5분, ACTIVE/SCHEDULED 6시간, terminal 최근 90일 일 1회; page 100/cursor. pending 15분 초과·revoke outbox oldest 60초 초과는 경보의 초기값이다. 경보는 전달 보장/SLA가 아니다.
- account 발급 시 lookup cursor를 생성해 구매 callback 자체가 유실되어도 RC customer 목록으로 발견할 수 있게 한다. 구매 화면 진입 시 body 없는 purchase-account 호출로 최근 activity를 갱신하고 최근 계정을 우선 조회한다. 장기간 무활동 계정은 bounded 순환 scan과 재로그인 계정 조회로 복구한다. RC에조차 기록되지 않은 Store 거래 복구는 G5 별도 검증 대상이다.
- 구조화 로그: timestamp(UTC), service=billing, environment, operation, outcome, durationMs, traceId, spanId. 내부 eventId는 필요 시 로그에서만 사용한다. userId/reference/transaction/session/group/digest/Authorization/body는 span attribute·metric label에 넣지 않는다.
- 실제 production 경로의 INTERNAL span은 `payment_sync`, `payment_event_consume`, `payment_reconcile`, `entitlement_access_publish`. HTTP span 아래 실행하고 예외에서도 종료한다. provider ingress는 외부 traceparent를 신뢰 경계 밖 입력으로 취급하며 baggage를 전파하지 않는다. 내부 호출은 W3C traceparent 주입 후 SigV4 최종 서명, outbox retry는 원 trace context와 연결한다.
- 5년 보존은 `max(providerFinalAt,entitlementEndsAt,lastReversalAt)`에 UTC calendar 5년을 더한다. Purchase/entitlement/ledger 연결 purge는 daily 명시적 worker; candidate Trial 3년 정책과 별개다. raw provider JSON/JWS/receipt/token은 저장하지 않는다.
- provider inbox 최소 normalized working data와 digest는 120일. terminal에서 working identity/reference를 먼저 제거할 수 있다. 120일 전 미해결 항목은 필요한 최소 증거를 payment review record의 승인 보존 범위로 이관하고 raw payload 없이 inbox를 정리한다. 미해결 work를 TTL로 조용히 유실시키지 않는다.
- sync terminal command 120일, outbox 완료 delivery metadata 120일. 미전달 금융 관련 outbox는 payment 보존 범위 내에서 유지·replay하며 active 업무를 TTL로 삭제하지 않는다. 5년 purge 때 provider lookup hash·계정 연결도 함께 제거한다.
- backup 최대 35일 rolling. 복구본은 격리한 뒤 현재 시각의 Trial/payment expiry purge와 outbox/inbox 정합성 확인을 마치고 트래픽에 연결한다. 삭제 기록은 건수·성공 여부만 남긴다.

## 6. 부록 A — Mongo schema v5 초안

모든 ID는 별도 표기가 없으면 Billing UUID v4, 시간은 BSON Date(UTC ms), version/sequence는 양의 int64다. schema v4를 유지한 reader를 먼저 배포하고 migration 절차로 v5 index를 추가한다. 기존 index를 자동 drop/recreate하지 않는다. application annotations/auto-index에 운영 생성을 맡기지 않는다.

### A.1 Collections와 필수 데이터

| collection | 핵심 field와 역할 |
| --- | --- |
| payment_products | environment, store, appId, storeProductId, rcProductId, offerCode, durationSeconds, catalogVersion, saleEnabled; 기존 매핑 검증 보존 |
| payment_accounts | accountId, environment, userId, paidSubjectRefId, activeRefId, version; 계정 owner와 paid subject |
| purchase_account_refs | referenceId, accountId, environment, active, createdAt, retiredAt; reference 필드 암호화 생략(2026-10-07 승인), 저장 암호화/접근 통제·owner 이전 금지 |
| purchases | purchaseId, accountId(미결 binding 시 null), environment/store/appId, transactionKey, encrypted provider refs, offer/duration snapshot, purchasedAt, financialStatus, processingStatus, confirmedAt/timeSource, version, retentionExpiresAt |
| subscription_entitlements | entitlementId, accountId, purchaseId, sequence, durationSeconds, originalStartsAt/EndsAt, startsAt/endsAt, revokedAt, version; 시점 상태는 derived |
| payment_timelines | accountId, environment, nextSequence, timelineEnd, version; 원장이 아닌 CAS root |
| payment_ledger | ledgerEventId, purchaseId, entitlementId, type, effectKey, occurredAt, immutable before/after schedule·검증된 금액·근거 종류; 원문/토큰 없음 |
| payment_sync_commands | environment, userId, operationId, payloadHash, hintEncrypted, state, purchaseId, attempts, nextAttemptAt, leaseToken/Until, purgeAt |
| payment_provider_inbox | environment, providerEventKey, digestVersion/digest, eventType, minimal normalized command, state, attempts, nextAttemptAt, leaseToken/Until, purgeAt |
| payment_access_outbox | eventId, group/version unique, immutable wire, state, attempts, nextAttemptAt, leaseToken/Until, publishedAt, purgeAt |
| payment_reconcile_cursors | accountId, kind, nextAttemptAt, cursorEncrypted, lease/version, lastSuccessAt |
| exam_owner_guards | environment,userId,operationId,attemptGroupId,source,version; free/paid 단일 진행·재응시 guard |

기존 reservations/attempt_groups/attempt_sessions에는 A.1과 별도로 §5.8 source snapshot과 §5.9 access state를 reader-first 추가한다. nullable paid field가 기존 무료 DTO를 강제로 바꾸게 하지 않는다.

providerEventKey/transactionKey는 별도 lookup HMAC key의 고정형 해시다. 암호화 key와 lookup key를 분리하며 rotation 기간에 기존 lookup을 함께 조회한다. 새 hash만 쓰는 회전으로 unique dedupe가 풀리지 않도록 dual-read/backfill/검증 뒤 old-key 폐기 migration을 수행한다. 조회 해시도 개인정보 연결정보로 취급한다.

### A.2 Exact index 목록

아래 key order는 왼쪽부터 모두 ascending(1), `U`는 unique, 표기 없는 partial/TTL은 없음이다. 각 collection의 기본 `_id` unique는 생략한다.

| collection | index name | keys | option |
| --- | --- | --- | --- |
| payment_products | ux_payment_store_product | environment,store,appId,storeProductId | U |
| payment_products | ux_payment_rc_product | environment,rcProductId | U |
| payment_accounts | ux_payment_account_user | environment,userId | U |
| payment_accounts | ux_payment_paid_subject | paidSubjectRefId | U |
| purchase_account_refs | ux_purchase_active_ref | environment,accountId | U, partial `{active:true}` |
| purchases | ux_purchase_store_transaction | environment,store,appId,transactionKey | U |
| purchases | ux_purchase_rc_id | environment,rcPurchaseKey | U, partial `{rcPurchaseKey:{$type:"string"}}` |
| purchases | ix_purchase_reconcile | processingStatus,nextAttemptAt,_id | — |
| purchases | ix_purchase_retention | retentionExpiresAt,_id | — |
| subscription_entitlements | ux_subscription_purchase | purchaseId | U |
| subscription_entitlements | ux_subscription_sequence | accountId,sequence | U |
| subscription_entitlements | ix_subscription_window | accountId,revokedAt,startsAt,endsAt | — |
| payment_timelines | ux_payment_timeline_account | accountId | U |
| payment_ledger | ux_payment_ledger_effect | effectKey | U |
| payment_ledger | ix_payment_ledger_purchase | purchaseId,occurredAt | — |
| payment_sync_commands | ux_payment_sync_operation | environment,userId,operationId | U |
| payment_sync_commands | ix_payment_sync_due | state,nextAttemptAt,_id | — |
| payment_sync_commands | ttl_payment_sync_purge | purgeAt | expireAfterSeconds=0 |
| payment_provider_inbox | ux_payment_provider_event | environment,providerEventKey | U |
| payment_provider_inbox | ix_payment_provider_due | state,nextAttemptAt,_id | — |
| payment_provider_inbox | ttl_payment_provider_purge | purgeAt | expireAfterSeconds=0 |
| payment_access_outbox | ux_payment_access_group_version | attemptGroupId,accessVersion | U |
| payment_access_outbox | ix_payment_access_due | state,nextAttemptAt,_id | — |
| payment_access_outbox | ttl_payment_access_purge | purgeAt | expireAfterSeconds=0 |
| payment_reconcile_cursors | ux_payment_reconcile_cursor | accountId,kind | U |
| payment_reconcile_cursors | ix_payment_reconcile_due | nextAttemptAt,_id | — |
| exam_owner_guards | ux_exam_owner_guard_user | environment,userId | U |

referenceId/ledgerEventId/eventId/entitlementId는 각 collection의 `_id`와 같은 값으로 둔다. 미해결 sync/outbox에는 purgeAt을 설정하지 않는다. Purchase/entitlement/ledger/Reservation에는 business TTL을 부여하지 않는다.

ledger effectKey는 PURCHASE_VERIFIED, REFUND, REVOKE, TIMELINE_REFLOW, USAGE_RESERVED/CONFIRMED/CANCELED의 종류와 불변 원인·대상 ID로 결정한다. 같은 refund operation과 target entitlement의 reflow는 1건으로 수렴시킨다. 서로 다른 정당한 reflow를 event type만으로 중복 처리하지 않는다.

## 7. 부록 B — 보안·배포와 검증

### B.1 인증·네트워크 이관

- public JWT chain: RS256, exact issuer/JWKS, aud=tosunsaeng-billing, lowercase UUID sub, iat/exp/jti 필수, 최대 skew 60초, scope별 route. client userId/account type 헤더는 사용하지 않는다.
- provider chain: webhook exact route에서만 Authorization+HMAC, JWT·workload principal로 대체 불가.
- internal chain: 기존 Lattice AWS_IAM. 현재 코드의 Lattice route permitAll은 network boundary를 전제로 하므로 ALB 접근 허용 시 같은 의미로 노출하면 안 된다.
- ALB와 Lattice는 별도 target port/listener와 application ingress guard로 분리한다. public connector에서는 internal handler를 거절하고 internal connector에서는 public/provider handler를 거절한다. 외부가 위조할 수 있는 Host/X-Forwarded header만으로 connector를 판별하지 않는다.
- task SG: public port는 승인 ALB SG만, internal port는 승인된 Lattice 경로만. direct IP 접근, public `/internal/**`, 인코딩·중복 slash·method 우회, 임의 workload header를 negative test한다.
- 기존 ALB exact host/path allowlist 외 fixed reject. wildcard `/api/**`로 internal/user 관련 경로를 포괄 노출하지 않는다. 원문 URL/query/access log에서 사용자·provider 참조를 기록하지 않는다.
- Secret은 승인된 secret manager에서 런타임 주입한다. HMAC key rotation 시 RC 재서명·즉시 변경을 고려해 old/new 검증을 제한된 10분 overlap으로 준비하고 RC 변경을 검증한 뒤 old key를 제거한다. 다른 환경 secret은 fallback으로 허용하지 않는다.

### B.2 필수 테스트

| 범위 | 최소 회귀 |
| --- | --- |
| catalog/account | 다섯 exact duration, RC product vs Store product 구분, concurrent get-or-create, inactive alias·Guest 거절 |
| public | 실제 Controller JWT audience/scope/UUID/strict DTO, pending→applied, key conflict, refunded replay가 active로 보이지 않음 |
| webhook | raw byte HMAC·5분 경계·재서명 retry·Authorization, oversize/coercion/duplicate, unknown additive 허용, unknown type durable ignored |
| ownership | 임의 hint·다른 customer/환경·TRANSFER/anonymous/family share·과거 reference로 신규 지급 불가 |
| provider | API v2 typed fixture, pagination/404/429/401, cancelled subscription ≠ refund, owned로 terminal revive 금지 |
| Mongo | duplicate event/transaction, 동시 sync+webhook+reconcile, unknown commit, refund-before-purchase tombstone, 같은 계정 timeline append/reflow |
| Reservation | paid-first 무료 불변, paid/free 동시 시작 guard, expiry/cancel 무료 복구 없음, reserve-confirm-refund 양 commit 순서 |
| Learning Core 계약 | duplicate/stale/revoke-before-session tombstone, GRADING race, 늦은 status로 access 부활 금지, outbox 손실·401·retry |
| privacy/trace | production 업무 span 정상·예외 종료, baggage 비전파, body/식별자/credential의 log·span·metric 비포함 |
| 보존 | 5년 기산점 갱신과 daily purge, 120일 inbox와 분리, 35일 backup 복구 purge, worker lease/fencing |

단위·MVC는 fake adapter만 사용한다. Mongo Transaction/unique/race는 replica-set Testcontainers, 구현 후 `./gradlew clean test`. 실제 credential/Store/RC/AWS 호출은 local 테스트에 넣지 않고 staging E2E gate로 분리한다. 승인된 D1-A·D2-A 경계 테스트를 포함한다: 만료 전 reserve의 5분 내 confirm, 현재 Session 기존 제출 기한 내 완료, 만료 뒤 INITIAL/replacement 거절, 환불에는 만료 유예 미적용, REFUND_REVERSED 중복·owned 재관측의 무자동지급·REVIEW_REQUIRED 수렴. 4주 duration은 2,419,200초이며 30일/달력 월로 계산하지 않음을 검증한다.

### B.3 구현·배포 순서

1. 승인된 4주·D1-A·D2-A를 기준으로 기술 상세 검토, 이후 별도 결제 PLAN과 명시적 승인 후 Jira 생성. PLAN-007은 무료 public reader가 사용한다.
2. 기존 Trial 계약 유지하는 reader-first source/guard/security 기반, v5 migration dry run.
3. catalog·account·public JWT·read APIs, provider-neutral Purchase/timeline/ledger와 fake adapter.
4. RC webhook inbox/API adapter/sync/reconciliation, 모든 payment flag OFF.
5. paid Reservation, LC access consumer·Job gate·Lattice route와 durable Billing publisher.
6. credential·상품·sandbox 구매/환불·response loss·역순·auth failure·reflow·기한 경계 E2E.
7. consumer부터 활성화, staging에서 RC webhook·앱 sync·paid admission 순차 활성화, 동일 검증 뒤 production 검토.

실패 시 신규 paid admission/sale를 닫되 이미 결제된 거래 sync·webhook·환불·조회 복구를 전부 끄지 않는다. reader rollback은 새 source/access field를 이해하는 버전으로만 한다. 이전 schema-v4-only reader로 즉시 되돌리면 paid 접근을 무료로 오판할 수 있으므로 rollback gate가 필요하다.

## 8. 부록 C — 조사 근거와 사실 구분

### C.1 현재 구현을 확인한 근거

- [AttemptGroup](../../src/main/java/web/tosunsaeng/billing/domain/attempt/domain/entity/AttemptGroup.java): OPEN/GRADING/RETAKE_AVAILABLE/COMPLETED와 trialClaimId/consumption 연결. paid/revoke 모델은 현재 없음.
- [Reservation](../../src/main/java/web/tosunsaeng/billing/domain/reservation/domain/entity/Reservation.java), [ReserveResponse](../../src/main/java/web/tosunsaeng/billing/domain/reservation/dto/response/ReserveResponse.java): INITIAL/REPLACEMENT, PHONE_REJOIN와 기존 response fields.
- [SecurityConfig](../../src/main/java/web/tosunsaeng/billing/global/config/security/SecurityConfig.java): internal Lattice 경계를 전제로 하는 route 허용. 이 문서의 public JWT/HMAC 분리는 새 설계다.
- [BillingMongoIndexInitializer](../../src/main/java/web/tosunsaeng/billing/global/infrastructure/mongodb/BillingMongoIndexInitializer.java): 기존 schema v4와 subject별 unique/partial index. 본 문서의 v5 index는 아직 생성하지 않음.

### C.2 공식 RevenueCat 문서 — 2026-09-07 조회

- [Webhooks](https://www.revenuecat.com/docs/integrations/webhooks): Authorization/HMAC와 delivery retry·빠른 200 ack. HMAC timestamp는 event 발생 시각이 아니라 전송 시각.
- [Event types and fields](https://www.revenuecat.com/docs/integrations/webhooks/event-types-and-fields): NON_RENEWING_PURCHASE, CANCELLATION, REFUND_REVERSED, last-seen app_user_id, aliases, nullable/additive field, TEMPORARY_ENTITLEMENT_GRANT.
- [API v2 개요](https://www.revenuecat.com/docs/api-v2): base URL, V2 secret 권한, pagination, domain quota·Retry-After.
- [Purchase API](https://www.revenuecat.com/docs/api-v2/purchase), [공식 Purchase OpenAPI](https://www.revenuecat.com/docs/redocusaurus/openapi-v2-purchase.yaml): Store 식별자 검색과 purchase 조회, original_customer_id, store_purchase_identifier의 string 타입, status owned/refunded. 문서 예제의 숫자 모양 ID도 JSON number로 수신하지 않는다.
- [Customer resources](https://www.revenuecat.com/docs/api-v2/customer/resources): 사용자별 one-time purchase 목록, environment filter, limit 1~100, cursor.
- [Non-subscriptions](https://www.revenuecat.com/docs/platform-resources/non-subscriptions), [Restore behavior](https://www.revenuecat.com/docs/projects/restore-behavior): 기존 RevenueCat 채택 정책의 참고 문서. SDK version별 실거래 검증은 G1/G2에서 별도로 수행한다.

외부 공식 문서의 지원 사실과 실제 토선생 project 설정/Store validator 성공은 다르다. 이 ADR은 공식 문서 조사와 로컬 코드 확인에 기반한 설계 초안이며, 실결제·배포 성공 보고서가 아니다.
