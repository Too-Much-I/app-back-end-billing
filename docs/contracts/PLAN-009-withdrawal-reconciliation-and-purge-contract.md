# PLAN-009: 탈퇴 수신·구매 계정 상태·거래 확인·삭제 설계

- 작성일: 2026-10-07 / 상태: 기술 설계 제안, 매시간 점검·일일 재확인·7일 이내 담당 검토 승인; 구현·외부 계약·나머지 운영값 승인 전 / Jira 없음
- 기준: [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md), [009 기반 계약](PLAN-009-payment-foundation-technical-contract.md), [PLAN-009](../plans/PLAN-009-payment-catalog-and-account.md). Identity는 읽기만 했으며 이 문서는 상대 서버의 구현 완료를 의미하지 않는다.

## 1. 5줄 결론

Identity 검토 요청은 [서버 인계서](IDENTITY-PAYMENT-ACCOUNT-LIFECYCLE-HANDOFF.md)에 별도로 정리했다. 외부 wire/feed 합의와 구현 완료는 회신 후 확인한다.

Identity 회신 이후 [공동 기술 계약 권장 초안](IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md)을 작성했다. 신규 source120일/snapshot7일/token 최대30분 및 commit-consistent 복구는 미승인 제안이며 기존15일 정책의 자동 변경이 아니다.

1. Identity 기존 `UserWithdrawn` 4필드를 유지하고 Billing 전용 durable delivery와 SigV4 수신 경로를 추가하는 안이다(§5.1, §6.1).
2. 탈퇴 인지 즉시 구매 계정 신규 사용을 차단하지만 탈퇴를 환불로 기록하거나 무료 Claim을 지우지 않는다(§5.2).
3. `withdrawnAt + 15일`은 삭제 자격 시각이며 로컬·provider 거래 확인 완료 없이 삭제하지 않는다([승인된 보존 정책](PLAN-009-payment-foundation-technical-contract.md#미구매-탈퇴-계정-정리--15일-조건부-정책)).
4. 거래 수신·계정 발급·삭제가 같은 계정 fence에 쓰도록 하고, 삭제 후 늦은 거래는 새 사용자에게 자동 연결하지 않는다(§5.3~5.5).
5. provider 거래 부재의 증명 범위·최소 차단기록 보존·Identity 누락 복구 계약은 실제 검증/승인 gate이며 미충족 시 purge는 OFF다(§3~4).

## 2. 사용자가 반드시 읽어야 하는 내용

탈퇴 → 신규 구매 계정 사용 차단 → 15일 대기 → 거래/미해결 확인 → 안전한 미구매 계정만 삭제 순서다. 구매가 있었다면 금융 기록의 기존 5년 보존 정책을 적용한다. “계정을 지운다”는 구매 연결정보 정리이지 시험 이력·무료권·Identity 계정을 Billing이 일괄 삭제한다는 뜻이 아니다.

스토어 결제창이 이미 열려 있으면 Billing이 이를 닫거나 실제 결제를 강제로 막을 수 없다. 탈퇴 후에도 검증된 결제 사실은 수신해야 한다. 해당 금전 사실은 보존하고 문의/대사 대상으로 삼으며 동일 전화번호 재가입자에게 지급하지 않는다. 자동 환불·자동 paid owner 이전도 하지 않는다.

15일 조건부 정책과 매시간 삭제 대상 점검·미확인 건 매일 재확인·계속 미해결이면 7일 이내 담당자 검토는 2026-10-07 사용자 승인됐다. 나머지 worker 초기값과 예외 보존은 제안이며, 15일 경과만으로 개인정보를 무조건 삭제하거나 불명 건을 무기한 보관한다는 승인이 아니다.

## 3. 사용자가 결정해야 하는 사항

| 항목 | 권장안 | 승인/검증 구분 |
| --- | --- | --- |
| 정상 삭제 실행 | 매시간 대상 점검 | 사용자 승인; 탈퇴 15일 정각 삭제 SLA 아님. 조건 충족 후 24시간 초과 지연 경보는 별도 제안 |
| 미해결 재검토 | 자동 일 1회, 최초 미해결 판정 후 7일 이내 담당자 검토 | 사용자 승인; 이 주기가 개인정보 보존 근거를 대신하지 않음. 첫 검토 뒤 최대 7일 간격의 반복 담당 검토는 별도 제안 |
| 재생성 차단 기록 | 사용자 연결은 유효 JWT/진행 요청 drain과 복구 증명이 끝날 때까지만; 완료 시 account와 함께 제거 | 고정 무기한 tombstone 금지. 실제 최대 token/요청 수명과 replay 계약 검증 필요 |
| 증명 불가능한 미해결 건 | 최소 case로 분리하고 목적·필드·보존 종료일·담당자 명시 | 종료일을 정하지 못한 예외의 자동 purge/판매 활성화 금지; 별도 보존 승인 필요 |

exact route/index는 아래 개발 제안으로 제시한다. 사용자에게 UUID/필드명을 다시 선택하도록 요구하는 것은 아니지만 Identity 합의·ADR 동기화·Jira 및 구현 승인은 필요하다.

## 4. 주요 위험과 미확인 사항

- 확인된 구현: Identity에는 탈퇴 transaction/outbox와 LC용 단일 발행 설정이 있다. Billing 독립 delivery, 누락 복구 feed는 확인되지 않았다(§6.1).
- RC `404`/빈 목록은 “RC에 아직 없는 결제도 없음”의 증명이 아니다. 현재 Google 상품 미등록으로 실거래·지연 fixture 미검증이다. Google Orders 보완 승인은 알려진 거래의 부분 환불 확인용이며 전체 사용자 미구매 검색 권한으로 확장하지 않는다.
- JWT 최대 수명은 로컬 기본값과 실제 배포가 다를 수 있다. 기존 토큰 만료까지 수용하는 승인과 일치시키되 Billing이 이미 아는 탈퇴 사실은 신규 account 발급 거절에 사용한다. 전체 서비스 토큰 즉시 폐기 기능을 추가하는 것은 아니다.
- ADR §5.10은 account 호출 시 activity 갱신/lookup cursor 생성을 요구하지만 009 초안은 반복 호출 무변경·cursor 제외로 읽힐 수 있다. 아래 §5.6은 충돌 해소 **제안**이며 기존 ADR을 임의 변경하지 않는다. 이 부분 구현은 합의 전 중단한다.
- HMAC 사용자 키·event digest도 재연결 가능한 개인정보일 수 있다. 해시라는 이유로 무기한 보존하지 않는다. provider 측 고객 데이터 삭제는 Billing DB purge와 별개이며 금융 증거 영향/RC 정책을 확인한 별도 절차가 필요하다.

## 5. 현재 작업과 직접 관련된 설계

### 5.1 탈퇴 수신과 누락 복구

**제안 route:** `POST /internal/v1/payments/accounts/withdrawal/events`. Identity → Billing은 Lattice `AWS_IAM`/SigV4, Identity role에 이 POST만 추가 허용한다. public ALB는 전달하지 않는다. LC 기존 `/internal/v1/events/withdrawn` workload JWT는 유지한다.

기존 wire 그대로 `eventId`, `schemaVersion=1`, `userId`, `withdrawnAt`만 받는다. 새 eventType/producer/consumerScopeId를 v1에 넣지 않는다. 환경/producer는 검증된 principal과 endpoint로 고정한다. UUID lowercase canonical, Instant 유효성·미래시각 최대 60초 허용을 검증하며 오래된 탈퇴는 거절하지 않는다. publisher는 현재 4 KiB 제한, Billing inbound 상한은 기존 16 KiB다.

duplicate field·unknown field·trailing token·coercion을 거절한다. 기존 property 순서/UTC Instant 정규화 canonical SHA-256을 비교한다. 같은 eventId+digest는 204, 다른 digest는 409 `EVENT_ID_CONFLICT`; malformed 400 `INVALID_REQUEST`, schema 불일치 422 `UNSUPPORTED_CONTRACT`, DB 일시 실패 503 `BILLING_TEMPORARILY_UNAVAILABLE`. 모두 internal 기존 오류 형식이며 public wrapper를 쓰지 않는다. 인증 실패는 업무 처리 전에 거절한다.

한 Transaction에서 inbox 멱등 확인 → lifecycle fence 생성/갱신 → account가 있으면 WITHDRAWN 및 ref 신규용도 비활성화 → cleanup job upsert → commit 후 204. account가 없어도 lifecycle fence를 남기며 빈 구매 계정은 만들지 않는다. 동일 사용자/동일 withdrawnAt의 다른 eventId는 의미상 NOOP; 다른 withdrawnAt은 409 `WITHDRAWAL_STATE_CONFLICT`로 운영 확인하며 15일 기산점을 덮어쓰지 않는다. 탈퇴는 terminal이고 다른 userId의 재가입과 구분한다.

Identity 후속: 같은 탈퇴 transaction의 durable 원천에서 `(eventId,destination)`별 Billing/LC delivery를 독립 관리한다. 한쪽 성공으로 다른 쪽을 완료하지 않는다. timeout/429/5xx는 backoff 재시도, 401/403은 BLOCKED_AUTH 경보, malformed/충돌은 dead-letter+담당 검토; 재전송은 원 eventId/payload 유지. backoff 초기 5초~1시간 full jitter, 24시간 미전달 경보를 제안하며 이벤트를 시간 초과만으로 버리지 않는다.

누락 복구는 단순 재시도와 다르다. 최초 cutover snapshot과 이후 durable withdrawal feed의 opaque cursor/완료 watermark를 Billing이 주기적으로 대조하는 계약이 필요하다. 페이지 실패 시 checkpoint를 전진하지 않고 처리 commit 뒤만 전진한다. 원 event 또는 검증된 동일 탈퇴 사실을 같은 수신 서비스로 적용한다. Identity 원천 보존보다 뒤처진 gap은 자동 완료 처리하지 않고 bounded 재동기화한다. feed exact route/DTO/원천 보존은 Identity 인계 후 동결할 항목이다. 공개 구매 요청마다 Identity를 동기 조회하지 않는다.

### 5.2 상태는 서로 분리한다

| 축 | 제안 값 | 의미 |
| --- | --- | --- |
| local lifecycle | ACTIVE / WITHDRAWN | ACTIVE는 Billing이 탈퇴를 아직 받지 않았다는 local 상태; 실시간 Identity ACTIVE 보장 아님 |
| cleanup | WAITING / VERIFYING / BLOCKED / FINANCIAL_RETAINED / READY | 15일 대기, 대사 중, 증거 부족, 실제 구매 보존, 삭제 가능 |
| 삭제 결과 | PURGED | 활성 계정 행을 계속 보관하는 상태가 아니라 삭제 완료 결과 |
| provider 구매 상태 | 기존 Purchase 상태 | 탈퇴만으로 REFUNDED/REVOKED 전이 금지 |

WITHDRAWN의 account POST는 유효 JWT여도 403 `PAYMENT_ACCOUNT_WITHDRAWN`, lifecycle 조회 실패는 503이다. 최초 발급과 탈퇴 수신은 동일 unique lifecycle 행의 version 쓰기로 직렬화한다. 발급이 먼저면 이후 탈퇴가 차단하고, 탈퇴가 먼저면 발급하지 않는다. public GET 무료 reader 및 기존 owner-rebind 계약은 수정하지 않는다.

이미 가진 ref로 발생한 늦은 거래는 inactive라는 이유만으로 버리지 않는다. 검증된 금융 사실/기존 소유 증거는 기록하되 탈퇴 계정 재활성화·새 계정 지급·자동 환불은 금지한다. 탈퇴 전 시작인지 불명한 신규 거래는 별도 CASE로 격리한다. paid admission에서 WITHDRAWN을 거절하는 계약은 PLAN-010/011에 이어 적용하며 탈퇴를 환불 timeline reflow로 치환하지 않는다.

### 5.3 거래 확인 절차

1. worker가 lease를 얻고 account/lifecycle version, 전체 ref 집합과 변경 version, inbox 처리 watermark를 snapshot한다.
2. 로컬 Purchase/ledger/entitlement 존재를 확인한다. 존재하면 FINANCIAL_RETAINED로 분류하고 15일 미구매 삭제에서 제외한다. 금전 보존5년이 모든 불필요 필드 보존을 허용하는 것은 아니다.
3. pending sync/미처리 inbox/대사 job/분쟁·환불 case와 처리 중 쓰기를 확인한다. 하나라도 미해결이면 BLOCKED다.
4. 인증된 RC 조회를 모든 관련 ref·환경·스토어에 대해 끝까지 pagination한다. 오류/페이지 누락/조회 상한/응답 모호함은 UNKNOWN이다. 데이터가 없다는 관측과 미구매 증명은 별도 값이다.
5. `PURCHASE_FOUND`, `NO_PURCHASE_CONFIRMED`, `UNKNOWN`으로 결과를 저장한다. NO_PURCHASE_CONFIRMED는 local barrier와 검증된 provider coverage가 모두 만족된 때만 허용한다. “15일 지났고 RC가 비었다”로 만들지 않는다.
6. 최종 CAS 전 version/새 금융 입력 유무를 재검사하고 바뀌면 결과를 폐기해 다시 대사한다. external API는 Mongo Transaction 밖에서만 호출한다.

확인 증거는 accountVersion, refSetVersion, checkedAt, local watermark, provider adapter/coverage version, 결과·reason code만 최소 저장한다. provider raw 응답은 저장하지 않는다. 최종 purge 시 확인 결과는 최대 1시간 이내 것을 사용하는 초기값을 제안한다. 이는 외부 거래 지연 상한을 보장하지 않는다.

**현재 미해결 gate:** RC 미관측 Store 거래까지 없음을 입증하는 coverage 계약은 아직 없다. fixture/문서 검증으로 이 조건을 충족하지 못하면 실제 구매가 가능한 ref의 자동 purge를 활성화하지 않는다. 결제 미개방 환경에서 발급된 ref도 모든 판매/외부 구매 경로가 닫혀 있었다는 증거 없이 미구매로 단정하지 않는다. 사용자에게 확률적 삭제 위험 수용을 묵시적으로 요구하지 않는다.

### 5.4 삭제 Transaction과 경합

모든 account 생성/withdrawal·provider 수신 연결·sync·구매 반영·purge는 같은 lifecycle fence version을 **쓰기**로 갱신한다. snapshot 읽기만으로 write-skew를 막았다고 하지 않는다. provider inbox 수신이 worker보다 먼저 계정 fence를 갱신하고 pendingEvidence를 표시해야, worker 실행 전 삭제되는 틈을 막는다. 계정 불명 입력은 미매핑 증거로 격리하고 재매핑 전 지급하지 않는다.

purge 후보는 withdrawnAt+15일 경과, READY, 최신 부재 증명, pendingEvidence=0, 진행 command/lease 없음, 아래 token/drain 조건 충족을 모두 요구한다. 한 Transaction에서 exact version CAS → 해당 account의 ref/lookup cursor/불필요 case·account 사용자 연결 삭제 → 보존 대상 없는 lifecycle 제거를 수행한다. 데이터가 bounded 한도를 넘으면 이번 작업을 BLOCKED로 돌려 별도 재개 가능한 정리 manifest를 승인받는다. 무제한 multi-document 삭제 Transaction은 만들지 않는다.

구매 수신이 먼저 commit하면 purge CAS는 실패한다. purge가 먼저면 이후 거래는 missing ref로 격리되며 account를 upsert하지 않는다. unknown commit은 재조회로 결과를 확인한다. 잔존 ref만으로 account를 복구하거나 stable ID를 새로 발급하지 않는다.

재생성 방지: Identity는 탈퇴 userId를 재사용하지 않고 탈퇴 후 token 신규/refresh 발급을 차단해야 한다. Billing은 최대 access-token lifetime+skew 경과와 인증 후 진행 요청의 최대 수명 drain을 확인한다. 삭제 직전 과거 토큰으로 시작한 요청도 최종 생성 경계에서 만료/처리 deadline을 다시 확인한다. 운영 lifetime을 검증하지 못하면 purge 금지다. 영구 user hash 보존으로 해결하지 않는다.

늦은 탈퇴 replay가 삭제된 연결을 다시 남길 수 있으므로 replay도 동일 cleanup 대상으로 넣고 신규 구매 권한을 만들지 않는다. inbox 보존 종료 뒤 semantic replay 처리는 Identity 원천 watermark/동일 탈퇴 사실 검증과 함께 검증한다. 이 재처리 기간을 무기한 개인정보 보존 허용으로 보지 않는다.

### 5.5 삭제 범위·운영·복구

| 자료 | 처리 |
| --- | --- |
| 확인된 미구매 account/ref/사용자 연결·cursor | 조건 충족 후 명시적 삭제, business TTL 금지 |
| 금융 Purchase/ledger/entitlement/환불 | 기존 최종 거래·권리 종료·reversal 중 최후 시점 기준5년 정책 유지 |
| unresolved 최소 case | reason/nextReviewAt/담당/목적·승인된 종료일, 일반 계정 전체 무기한 보관 금지 |
| withdrawal inbox | 제안120일 멱등 보관; eventId/digest/결과/시각만, userId/ref/payload 제외. 개인정보 검토 후 동결 |
| 무료 Claim·Grant·Reservation·owner fence | 본 설계 삭제 대상 아님; 기존 정책 유지 |
| 삭제 실행 감사 | 건수·성공 여부·저카디널리티 실패 사유, userId/ref/candidate 없음 |
| 백업 | 기존 최대35일 rolling; 복원 후 withdrawal 재대사와 만료 purge/차단 검증 전에 트래픽 금지 |

승인된 운영 주기는 삭제 대상 매시간 점검, 미확인 건 자동 일 1회 재확인, 최초 미해결 판정 후 7일 이내 담당자 검토다. 재시도나 reason 변경으로 최초 미해결 시각과 검토 기한을 계속 늦추지 않는다. batch100·lease30초·version fencing·갱신 및 READY24시간 지연 경보는 기술 초기값 제안이다. 일시 장애는 ADR의 5초~1시간 backoff를 재사용한다. 오래된 lease의 삭제 commit을 허용하지 않는다. 대사 오류/오래된 BLOCKED/READY 지연을 구분해 경보한다. 판매 flag를 꺼도 이미 받은 탈퇴·금융 대사와 보존 작업을 중단하지 않는다. RC 고객 삭제는 이 worker에서 자동 실행하지 않는다.

### 5.6 009~013 반영 순서와 초안 충돌 해소안

- 009: lifecycle fence/수신·계정 사용 차단·cleanup 후보와 index 기반, fake 대사 포트까지. 실제 삭제 OFF. Identity Billing delivery 별도 인계. 신규 account 생성 시 조회 cursor도 원자 생성하는 쪽으로 subset 확대를 제안한다.
- 반복 account POST는 stable ref/account version을 불필요하게 변경하지 않되 **별도 cursor의 activity를 갱신**해 ADR §5.10을 만족시키는 안이다. 앞선 “반복 무변경”은 금융 계정/권리 무변경으로 좁혀야 하므로 ADR·009 초안 검토 후 확정한다. 조회 priority 갱신도 purge와 경합하므로 lifecycle fence 통과가 필요하다.
- 010: 실제 RC 대사/금융 입력 fence, 늦은 거래 격리·누락 복구. 011: WITHDRAWN paid admission 거절. 012: 환불 금전/권리와 탈퇴 상태 분리. 013: 운영값·보존 manifest·백업 복원/예외 검증 후 purge 활성화 승인.
- Identity: ACTIVE MEMBER purchase scope를 defaults/명시 scope/login/refresh 전체에서 검증. LC 기존 탈퇴 인증/경로는 그대로. Billing 수신 ready → Identity fan-out ON → 누락 복구 검증 → account 기능 단계 활성화. 실제 구매/삭제는 각각 별도 gate다.

## 6. 부록 — schema·테스트·조사 근거

### 6.1 확인한 소스

- [Identity UserWithdrawnWireEvent](../../../identity/src/main/java/web/tosunsaeng/identity/domain/user/withdrawalevent/application/UserWithdrawnWireEvent.java): exact4필드/property order.
- [Identity mapper](../../../identity/src/main/java/web/tosunsaeng/identity/domain/user/withdrawalevent/application/UserWithdrawnEventMapper.java): publisher4KiB.
- [Identity publisher 설정](../../../identity/src/main/java/web/tosunsaeng/identity/domain/user/withdrawalevent/infrastructure/UserWithdrawnPublisherProperties.java): 단일 endpoint와 `/internal/v1/events/withdrawn`.
- [Identity adapter](../../../identity/src/main/java/web/tosunsaeng/identity/domain/user/withdrawalevent/infrastructure/JdkUserWithdrawnDeliveryAdapter.java): USER_WITHDRAWN workload JWT.
- [Identity 탈퇴 transaction](../../../identity/src/main/java/web/tosunsaeng/identity/domain/user/application/UserWithdrawalTransactionService.java): tombstone/phone revoke/refresh 차단/outbox 연결.
- [provider 조사](PAYMENT_OPERATIONS_PROVIDER_RESEARCH-2026-10-07.md), [기반 선택지 조사](PLAN-009-foundation-options-review-2026-10-07.md): 운영 확인과 로컬 소스/문서상 사실을 구분. 이 작업에서 실제 Store/RC 호출 없음.

위 타 저장소 링크는 동일 부모 디렉터리 checkout 기준이며 Billing 배포 파일 의존성이 아니다.

### 6.2 추가 schema 제안

| collection | 최소 field | index 제안 |
| --- | --- | --- |
| payment_account_lifecycles | environment,userId,state,withdrawnAt,version,pendingEvidence | ux_payment_lifecycle_user(environment,userId), unique |
| payment_withdrawal_inbox | eventId,digest,disposition,receivedAt,purgeAt | ux_payment_withdrawal_event(eventId), unique; ttl_payment_withdrawal_inbox(purgeAt), expireAfterSeconds=0 |
| payment_account_cleanup_jobs | account/lifecycle 연결,eligibleAt,state,nextAttemptAt,lease/version,proof,nextReviewAt | ux_payment_cleanup_subject(environment,userId), unique; ix_payment_cleanup_due(state,nextAttemptAt,_id) |
| payment_reconcile_cursors | ADR의 account/kind cursor와 별도 lastActivityAt | ADR 기존 unique(accountId,kind)/due index 재사용 제안 |

환경은 데이터베이스 격리가 기본이며 user unique에는 방어적으로 포함한다. inbox120일과 lifecycle 보존은 서로 다르다. job/user 연결도 purge manifest에 포함한다. 정확 index 이름/key order/options와 account 참조 무결성은 versioned additive subset으로 검증하고 schema v5 전체 완료로 표시하지 않는다. finance retention·미매핑 case schema는 010/012와 공동 동결한다.

### 6.3 필수 검증 사례와 완료 기준

1. malformed/unknown/duplicate JSON·canonical digest·동일 eventId 충돌·다른 탈퇴시각 충돌·같은 사실 다른 eventId.
2. SigV4 Identity만 성공, unsigned/wrong environment/role/route/direct ALB 차단; LC 기존 JWT 경로 불변.
3. account 없는 탈퇴 선행·발급 선행·동시 처리, 재가입 다른 userId는 독립; 무료 Claim/owner 정책 불변.
4. 15일 전/정각/늦은 수신, 기준은 withdrawnAt 유지. RC404/빈 페이지/누락/오류를 부재 증명으로 취급하지 않음.
5. 구매/환불/pending inbox 존재 시 미구매 purge 불가, 모든 ref 페이지와 환경 확인, version 변경 시 proof 폐기.
6. inbox commit/purge 및 worker/purge 양쪽 순서, stale lease/unknown commit/중복 worker, 미매핑 늦은 거래 durable 격리.
7. 과거 JWT 만료·진행 요청 drain·재생성 차단, inbox TTL 후 replay, 원천 feed gap/snapshot 복구.
8. 백업 복원 후 삭제/차단 재적용 전 트래픽 차단, 금융5년/무료3년/backup35일 분리, 식별자 비로깅.
9. reference/account 안정성 유지와 cursor activity 갱신 충돌 해소; RC에 없는 Store 거래 fixture 포함.

구현 시 replica-set Testcontainers로 CAS/unique/Transaction 경합과 fake provider fixture를 검증하고 `./gradlew clean test`를 실행한다. 이번은 문서 설계만으로 정적 diff/링크 검증만 수행한다. Identity feed/토큰 lifetime·provider coverage·예외 보존을 확인하지 못하면 자동 삭제 완료/production ready로 보고하지 않는다.
