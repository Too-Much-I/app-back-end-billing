# PLAN-008: fixed-term 결제·환불 전체 구현 로드맵

- 작성일: 2026-10-06 / 브랜치: develop / Jira: 미생성
- 상태: 2026-10-06 사용자 승인 — 009~013 단계별 진행 및 결제 public PublicResponse 통일. 각 slice 세부 기술·구현·Jira·배포 승인은 별도다.
- 기준: [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md), [결정 원장 C9](../codex/CONTRACT_DECISIONS.md), [환불 검토](../contracts/PAYMENT_REFUND_DISCUSSION_REVIEW-2026-10-06.md), [Store 지원](../contracts/STORE-REFUND-CAPABILITIES-2026-10-06.md).

## 1. 5줄 결론

1. 무료/owner 기능은 유지하고 결제를 PLAN-009~013의 다섯 vertical slice로 개발한다.
2. 상품·계정→검증된 구매·기간권→유료 시험→환불·차단→배포 검증 순서다.
3. partial refund는 실제 금전 기록과 권리 종료를 분리하고 후속 미시작 권리는 최초 Billing 반영 시점으로 당긴다.
4. Identity·Learning Core·앱 작업은 인계 대상으로만 정의하며 이 저장소에서 함께 구현하지 않는다.
5. fake 기반 개발 완료와 실제 판매 가능은 다르며 Store/RC·LC 경합·운영 gate 전 모든 판매 기능은 OFF다.

## 2. 사용자가 반드시 읽어야 하는 내용

| 순서 | 계획 | 결과 | 선행 |
| --- | --- | --- | --- |
| 0 | 본 문서 Phase 0 | 증거 기반 exact 계약 동결 | 사용자 계획 승인 |
| 1 | [PLAN-009 상품·계정](PLAN-009-payment-catalog-and-account.md) | catalog/구매 연결 ID/public 인증 | Phase 0 관련 계약 |
| 2 | [PLAN-010 구매·기간권](PLAN-010-purchase-ingestion-and-timeline.md) | sync/webhook/원장/기간권 조회·복구 | 009 |
| 3 | [PLAN-011 유료 시험·증거](PLAN-011-paid-reservation-and-usage-evidence.md) | paid-first/공통 guard/구매별 증거 | 010, LC 계약 협의 |
| 4 | [PLAN-012 환불·차단](PLAN-012-refund-ledger-and-access-revocation.md) | 부분 금전 원장/reflow/durable revoke | 010·011, LC consumer |
| 5 | [PLAN-013 연동·운영](PLAN-013-payment-rollout-and-operations.md) | migration/보존/문의/E2E/판매 gate | 009~012 |

009~012 중간 단계만 완료됐다고 결제 판매를 활성화하지 않는다. 013 인프라/타 서비스 준비는 병행 가능하다. 외부 인증 지원 미검증이면 provider-neutral 로직과 fake 테스트까지 진행할 수 있지만 실제 adapter 계약을 가정해 production을 열지 않는다.

### 승인 정책 고정

1/3/7/14/28일 = 86400/259200/604800/1209600/2419200초, 국내 가격 9000/19000/29000/49000/69000원. Apple/Google consumable one-time, 자동 갱신 없음. 구매 시각 시작·기간 stacking, ACTIVE paid-first/free-preserve, ACTIVE MEMBER 구매, 계정 자동 이전 금지. 문의 REFUND·2영업일 1차 답변·팀 검토·프론트 약관·Store별 집행 유지. 정상 만료 D1/환불 취소 D2 및 10/6 R1/R2는 모든 slice 공통 불변식이다.

## 3. 사용자가 결정해야 하는 사항

- 위 분할과 각 계획의 **새 기술 제안**을 검토·승인한 뒤 구현별 Jira 생성을 별도로 요청한다. Jira 자동 생성/상태 전환 없음.
- 재결정 불필요: 가격/기간, 부분 환불 잔여권 종료, R1/R2, 무료권 보존, SNS/phone에 의한 paid 이전 금지.
- 실제 입력: 승인 담당자/운영 역할, 배포 환경의 hostname·issuer/JWKS·Secret 참조 및 Store 매핑. 실제 식별자/Secret을 문서에 새로 하드코딩하지 않는다.
- 결제 public envelope는 2026-10-06 기존 PublicResponse 재사용으로 승인되어 ADR §5.3에 반영했다. 상세 DTO·오류·scope/flag 검토는 각 slice에서 이어간다. internal 및 provider webhook 응답은 통일 대상이 아니다.

## 4. 주요 위험과 미확인 사항

### Phase 0 — 구현 범위별 차단 조건

2026-10-06 F1~F4 A안 승인 산출물: 011 구현 전 전용 paid 채점 승인 wire·영속 exact Session 증거/환불 CAS·guard 전이표를 동결한다. 010/011 source 모델 구현 전 012 root deny+durable fan-out 불변식을 함께 고정한다. 012 구현 전 cursor/checkpoint·lease/fencing·완료 검증/index를 동결한다. 013 이관 전 관련 writer 제한/drain·재시도·coverage·복구 runbook과 예상 제한 시간을 승인받고 활성화 전 환불 선행 flag 표·자동 gate를 검증한다. 방향 승인은 상세 API/스키마·타 서버 구현·실제 배포 승인을 대신하지 않는다.

| 항목 | 산출물/닫는 조건 | 미완료 시 |
| --- | --- | --- |
| public envelope | DTO·오류·scope·flag의 ADR 승인 diff | public 결제 controller 구현 대기 |
| RC 서명 | 실제 제공 기능/헤더/서명 byte·timestamp/retry fixture, 환경별 설정 근거 | 승인된 Authorization+HMAC을 약화하거나 가상 헤더를 구현하지 않음. 대안은 재승인 |
| 거래/owner 식별 | SDK version별 Apple transaction/Android order hint와 RC original owner 매핑 | 미확인 거래 지급 금지 |
| 부분 환불 | 최종 상태·통화·금액 기준·환불 식별·증분/누적/정정·시각 source의 실제 provider 필드 표 | owned/refunded만으로 금액을 추정하지 않음. 부족한 대사 경로 별도 승인 |
| 유료 LC 계약 | paid source 인식·GRADING commit gate·revoke route/IAM/deny 동작 승인 | 유료 입장 활성화 금지 |
| schema manifest | ADR v5+각 slice 추가 field/index/partial/TTL/migration exact 동결 | 자동 index drop/recreate·v5 완료 표시 금지 |

Store 공식 지원과 실제 RC 데이터 전달은 다르다. Phase 0에서 기존 ADR의 오류/불일치를 발견하면 해당 계약 구현을 멈추고 근거·대안을 보고한다. 부분 환불 실행이 가능해도 이를 검증해 원장에 반영할 경로가 없으면 판매 gate를 닫는다. 법률 적합성/반환액 공식을 이 계획이 인증하지 않는다.

## 5. 현재 작업과 직접 관련된 설명

### 구조·공통 구현 규칙

기존 `domain/<도메인>/{api,application,domain,dto,repository,config,exception}`와 global security/infrastructure/response를 따른다. 신규 `domain/payment` 아래 catalog/account/purchase/timeline/refund 책임을 분리하고 `domain/reservation`, `domain/attempt`는 필요한 source/guard 변경만 한다. 전체 리팩터링·운영 의존성 추가는 별도 승인 없이 하지 않는다.

각 단계 PR에는 migration manifest, feature flag 기본 OFF, fake provider 테스트, replica-set Testcontainers 경합 테스트, `./gradlew clean test` 결과, WORKLOG/CURRENT_STATE를 포함한다. 기존 사용자 작업/무료 DTO/Trial retention/owner rebind/Session epoch를 보존한다. 실패 테스트는 숨기거나 임의 skip하지 않는다. Git commit/push는 사용자 수행.

### 공통 완료 기준

- 한 Store transaction은 한 구매/지급, 하나의 business effect는 한 ledger로 수렴.
- 분산 instance에서 unique index/CAS/Transaction으로 보장하고 외부 HTTP를 Transaction 재시도 안에 넣지 않음.
- 사용자·provider·internal 세 인증 경로는 상호 대체 불가. 민감 payload 비저장·비로깅.
- DTO/index/provider schema 신규 제안은 해당 PLAN 승인 및 ADR 동기화 후 구현. 단계 검증용 synthetic 값은 실제 provider 지원 증거로 사용하지 않음.
- 009~012 완료는 로컬 구현 완료, 013의 실제 환경 gate 완료만 판매 검토 가능을 의미.

## 6. 부록 — 구현 사실과 인계 경계

현재 [PublicResponse](../../src/main/java/web/tosunsaeng/billing/global/response/PublicResponse.java), [PublicSecurityConfig](../../src/main/java/web/tosunsaeng/billing/global/config/security/PublicSecurityConfig.java), [schema initializer](../../src/main/java/web/tosunsaeng/billing/global/infrastructure/mongodb/BillingMongoIndexInitializer.java)는 무료 reader/schema v4 기반이다. Purchase/SubscriptionEntitlement는 미구현이다. 이번에는 코드 테스트/실거래·타 서버 배포를 수행하지 않았다.

| 주체 | 별도 작업 |
| --- | --- |
| Identity | ACTIVE MEMBER purchase scope, REFUND 문의·userId 자동 귀속/예외 지원, 로그아웃·탈퇴/정지 토큰 정책 협의 |
| Learning Core | source reader-first, paid GRADING gate, durable revoke consumer/deny tombstone, 삭제 후 최소 증거 일관성 |
| 앱 | RC identify/계정 전환·표준 completion, sync PENDING, 두 entitlement 조회, 환불 문의/Store 안내·약관 |
| 운영 | Store 실제 환불·대사, IAM/ALB/Lattice, Secret, backup/복구, 신고·환불 응대 |

2026-10-06 상태 확인: Identity 로컬 `cc076442`에서 REFUND 분류·인증된 userId 자동 귀속·익명401/본문 userId400과 관련 테스트 구현을 확인했다. 이 인계는 신규 구현이 아니라 배포/프론트·구매 연결 통합 확인 단계다. ACTIVE Guest도 문의 가능하나 구매 권한/소유권을 부여하지 않는다. billing:purchase 발급은 별도 후속이다. 테스트는 이번에 실행하지 않았으며 실제 문의/Slack flag 활성화는 미확인이다.
