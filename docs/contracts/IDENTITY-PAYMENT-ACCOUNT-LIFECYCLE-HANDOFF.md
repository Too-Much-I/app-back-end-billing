# Identity 인계서: 결제 구매 권한·탈퇴 전달·누락 복구

- 작성일: 2026-10-07 / 대상: Identity 서버 담당 / 상태: 검토·기술 계약 합의 요청
- Billing 코드·Identity 코드 구현 승인이나 배포 요청이 아니다. 먼저 현행 코드 확인과 아래 회신 항목을 작성한다.
- 기준: [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md), [009 기반 계약](PLAN-009-payment-foundation-technical-contract.md), [탈퇴·대사·삭제 설계](PLAN-009-withdrawal-reconciliation-and-purge-contract.md).

## 1. 5줄 결론

TMI-199 실행 산출물: [공유 fixture·schema·로컬 Mongo 검증 결과](TMI-199-PAYMENT-LIFECYCLE-FOUNDATION.md). 신규 fixtureVersion1의 Identity 독립 실행/정밀도/DTO·오류 매핑 수락을 회신해야 한다. 기존 개정안 확인을 이번 신규 fixture 교차검증 완료로 간주하지 않는다.

2026-10-07 최신 상태: 공동 기술 계약 개정본의 보존·token 상한·기술 설계는 사용자 승인됐다. Identity는 승인 정책을 기준으로 실현성/상대 route·DTO·보존 적용을 검토하고 차이를 회신한다. 실제 구현/배포/판매/자동삭제 승인이나 Identity 구현 완료를 의미하지 않는다.

후속 구현용 권장안은 [공동 기술 계약 초안](IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md)에 정리했다. 발급 commit fence·capture sequence·고정 snapshot/feed·실패/재개 규격과 신규 보존 승인 항목을 포함하며, 양 서버 합의/구현 완료를 의미하지 않는다.

2026-10-07 개정은 Identity R1~R5를 반영해 baseline ACK/고정 scan/consumer coverage, control 이관·발급 불명 처리와 restore/canonical/retry 규격을 보완했다. 신규 metadata 보존 및 capturedAt 기산도 승인 제안으로 명시했으므로 개정본을 기준으로 회신한다.

1. ACTIVE MEMBER에게만 사용자 JWT의 `billing:purchase`를 발급하고 기존 Billing audience·read·account_type 계약을 유지해 주세요(§5.1).
2. 기존 `UserWithdrawn` 4필드를 변경하지 않고 Billing delivery를 LC와 독립적으로 저장·재시도하는 안을 검토해 주세요(§5.2).
3. Identity→Billing은 Lattice/SigV4, Identity→LC 탈퇴 전달은 기존 workload JWT를 유지합니다(§5.3).
4. 초기 연동 이전 탈퇴와 전달 누락을 복구할 snapshot/feed·원천 보존 범위 및 실제 token 최대 수명을 회신해 주세요(§5.4~5.5).
5. Billing은 탈퇴 후15일 조건부 정리를 수행할 예정이며, 거래 확인/안전성 검증 전 실제 purge는 OFF입니다. Identity가 결제 확인·환불·Billing 삭제를 담당하지 않습니다(§2).

## 2. 사용자가 반드시 읽어야 하는 내용

### 확정 정책과 책임 경계

- 앱은 Identity 사용자 Access Token으로 Billing public API를 호출합니다. 구매 계정은 사용자·환경별 stable `purchaseAccountRefId`를 사용하며 상품별로 새로 만들지 않습니다.
- Guest는 구매할 수 없으며 ACTIVE MEMBER만 구매 권한을 받습니다. 무료/상품 조회의 `billing:read`와 구매의 `billing:purchase`는 별개입니다.
- 동기 Identity 상태 조회를 모든 Billing 요청에 추가하지 않습니다. 기존 토큰은 유효 만료/skew 범위에서 검증하되, Billing이 이미 받은 탈퇴 사실로 구매 계정 신규 사용을 막습니다. 이는 모든 서비스의 access token 즉시 폐기를 의미하지 않습니다.
- 미구매 탈퇴 account/ref는 authoritative `withdrawnAt+15일` 이후 거래·미해결 확인이 끝나야 삭제할 수 있습니다. 매시간 대상 점검, 미확인 건 매일 재확인, 최초 미해결 후7일 이내 담당자 검토는 승인됐습니다.
- 탈퇴는 자동 환불/무료 Claim 삭제/paid owner 이전이 아닙니다. 같은 전화번호·SNS·Store 계정으로 재가입해도 과거 구매를 새 사용자에게 자동 연결하지 않습니다.
- Identity는 계정 상태·token·탈퇴 사실 전달을, Billing은 결제 증거·구매 계정 차단·조건부 삭제를 소유합니다. Learning Core 기존 시험/탈퇴 처리는 이번 인계로 변경하지 않습니다.

### 이번 요청 범위

구매 scope 발급 경로 검토, Billing 탈퇴 독립 delivery, 누락 복구 계약, token/userId 안전성 확인입니다. RevenueCat 구현·Store 환불·무료 owner event 변경·결제 사용권 지급은 Identity 작업 범위가 아닙니다. 아래 route/상태/운영 수치는 제안과 확정을 구분해 주세요.

## 3. 사용자가 결정해야 하는 사항

현재 승인된15일 정책과 세 가지 운영 주기를 다시 선택할 필요는 없습니다. Identity 담당자는 먼저 기술 가능 여부와 차이를 회신해 주세요.

새로운 개인정보 보존기간, 장기 user tombstone, 기존 탈퇴 사용자 복구 불가에 따른 이용 제한, 서비스 중단이 필요하면 사용자 재승인이 필요합니다. route/DTO/IAM/feed 보존 계약은 양 서버 검토 후 동결하며 문서 작성만으로 승인된 외부 계약이 되지 않습니다.

## 4. 주요 위험과 미확인 사항

- 로컬 source에는 `UserWithdrawn`과 LC용 단일 발행 경로가 있습니다. Billing fan-out/복구 feed가 운영 중이라고 가정하지 않습니다(§6.1).
- 설정의 access-token-ttl 기본값은 PT30M이지만 운영 최댓값은 확인하지 않았습니다. 기본값을 근거로 삭제 안전성을 확정하지 않습니다.
- 탈퇴와 token 발급이 경합하면 탈퇴 이후에도 새로운 유효 token이 나올 수 있는지 검사해야 합니다. 로그인에만 ACTIVE 검사를 추가하는 것으로 충분하지 않습니다.
- 정상 delivery 보존이 끝난 뒤에도 복구 가능한 원천이 있는지 확인해야 합니다. 이벤트가 사라진 경우 빈 목록을 “누락 없음”으로 취급하지 않습니다.
- Billing에는 provider 미관측 거래의 부재 증명과 예외 보존 등 별도 gate가 남아 있습니다. Identity 완료만으로 결제 판매나 자동 삭제를 활성화하지 않습니다.

## 5. 현재 작업과 직접 관련된 요청

### 5.1 사용자 JWT 구매 권한

| 대상 | Billing 권한 | 요구 |
| --- | --- | --- |
| Guest | billing:read | billing:purchase 요청/합성 금지 |
| ACTIVE MEMBER | billing:read billing:purchase | 정상 구매 경로에서 발급, 필요한 audience 포함 |
| SUSPENDED / WITHDRAWN / MERGED | 신규 billing:purchase 없음 | 기존 상태별 token 발급 거절 정책을 약화하지 않음 |

`aud`는 기존 Learning Core 등 audience를 보존하면서 `tosunsaeng-billing`을 포함합니다. `account_type`, RS256, issuer/JWKS/kid, expiry/iat/jti, canonical lowercase UUID sub는 기존 승인 계약을 따릅니다. 실제 값/secret을 인계 문서에 적지 않습니다.

default scope뿐 아니라 명시 scope 인자, 로그인·SNS 로그인·refresh·Guest 승격·계정 복구 등 **모든 issuer 진입점**을 확인해 주세요. 호출자 요청 scope만으로 purchase를 발급하지 않고 신뢰할 수 있는 계정 상태를 기준으로 제한합니다. 이 요구를 workload JWT에 적용하거나 사용자 purchase scope를 서비스 token에 추가하지 않습니다.

ACTIVE 확인과 최종 발급 사이 탈퇴 경합을 어떻게 차단하는지 회신해 주세요. 완전 차단이 불가능하면 탈퇴 이후 발급 가능한 시간 상한과 Billing 차단 방식에 미치는 영향을 명시해야 합니다. 이미 발급한 token을 즉시 무효화하는 신규 정책을 임의 추가하지 않습니다.

### 5.2 UserWithdrawn durable fan-out

기존 v1 wire를 유지합니다.

| 필드 | 의미 |
| --- | --- |
| eventId | 기존 탈퇴 event의 식별자; retry 때 새로 발급하지 않음 |
| schemaVersion | 1 |
| userId | 탈퇴한 실제 사용자 UUID, canonical lowercase |
| withdrawnAt | Identity가 확정한 원 탈퇴 Instant; 발행/retry 시각으로 교체 금지 |

eventType/producer/scope를 기존 v1에 추가하지 않습니다. Billing은 endpoint와 검증된 principal로 producer/환경을 식별합니다. 기존 mapper의4KiB 발행 상한도 유지합니다.

탈퇴 transaction과 durable 원천 저장의 원자성을 유지하고 `(eventId,destination)`별 전달 상태를 둡니다. 구현 방식은 기존 outbox를 재사용할 수 있으나 LC 성공이 Billing 성공을 뜻하면 안 됩니다. 같은 원 eventId/payload로 목적지별 독립 retry·lease·dead-letter·feature flag를 지원해야 합니다.

Billing flag OFF 때 미전달 대상을 어떤 원천으로 보관/재구성할지 명시해 주세요. 단순히 OFF 기간의 event를 버리지 않습니다. 기존 LC 전송을 새 Billing 장애에 종속시키지 않습니다. phone eligibility revoke와 UserWithdrawn의 전달 순서는 보장된다고 가정하지 않으며 Billing 결제 lifecycle은 phone revoke만으로 탈퇴를 추론하지 않습니다.

### 5.3 Billing route·인증·응답 제안

**합의 요청 route:** `POST /internal/v1/payments/accounts/withdrawal/events`

- Billing 목적지만 Lattice `AWS_IAM`/SigV4를 사용합니다. IAM action은 `vpc-lattice-svcs:Invoke`이며 환경별 서비스 ARN과 method/path 조건을 최소화하는 실제 policy는 ADR-002/AWS 검증 후 확정합니다. ARN/secret을 코드에 하드코딩하지 않습니다.
- LC `/internal/v1/events/withdrawn`은 기존 USER_WITHDRAWN workload JWT를 유지합니다. 기존 다른 owner event 대상/인증도 바꾸지 않습니다.
- public ALB에는 internal route를 열지 않습니다. redirect는 따라가지 않습니다. 실제 host와 role 배포는 별도 승인 대상입니다.
- Billing은 strict decode, canonical digest, inbox와 lifecycle 원자 commit 이후 body 없는204를 반환하는 안입니다. 전화번호·구매 ref는 payload에 추가하지 않습니다.

| 결과 | Billing 제안 응답 | Identity 전달 처리 |
| --- | --- | --- |
| 적용·같은 event 재전송·같은 탈퇴 사실 | 204 | 해당 destination만 완료 |
| 요청 오류 / 미지원 schema | 400 INVALID_REQUEST / 422 UNSUPPORTED_CONTRACT | 반복 자동 전송 대신 dead-letter·검토 |
| 같은 eventId 다른 payload | 409 EVENT_ID_CONFLICT | 경보·원 event 확인; 새 ID로 우회 금지 |
| 같은 user의 다른 withdrawnAt | 409 WITHDRAWAL_STATE_CONFLICT | 기산점 충돌 검토; 임의 덮어쓰기 금지 |
| 인증/권한 오류 | 401/403 | BLOCKED_AUTH·경보, 설정 복구 후 동일 event 재개 |
| rate limit·일시 장애·timeout | 429/5xx/응답 유실 | Retry-After 존중, 동일 event 재시도 |
| route 미준비 | 404/405 | 성공 처리 금지, 배포/계약 오류로 담당 확인 |

초기 backoff5초~1시간 full jitter 및24시간 미전달 경보는 제안입니다. LC 기존 retry 설정을 일괄 교체하지 않습니다. event를 경보 시점에 폐기하지 않으며 exact connect/read timeout·최대 자동시도/보존/재개 방식은 Identity가 현행값과 변경안을 회신해 주세요.

### 5.4 초기 snapshot·누락 복구 합의 요청

요청마다 동기 상태 조회가 아니라 **백그라운드 복구** 계약입니다. 다음 능력을 제공하는 최소 방식으로 제안해 주세요.

1. cutover 이전 탈퇴 사실의 bounded snapshot과 완료 기준.
2. snapshot 중 발생한 탈퇴를 빠뜨리지 않는 watermark→증분 feed 연결. timestamp 단독 pagination으로 동시/동일시각 event를 누락하지 않음.
3. opaque cursor, 고정 page 상한, 재시도해도 동일 사실로 수렴하는 식별자. Billing은 적용 commit 뒤만 checkpoint 전진.
4. 원 eventId/payload 재발행이 우선. 원 event가 없으면 별도 snapshot DTO/신뢰/멱등 계약부터 합의하고 가짜 원 event를 생성하지 않음.
5. cursor 만료·원천 보존 초과 gap을 명시적으로 반환하고 bounded 재동기화 가능 여부 제공. 조회 장애와 정상 빈 결과 구분.
6. delivery 기록과 실제 authoritative 탈퇴 사실 각각의 보존기간·삭제 조건·운영 복구 범위.

feed route/DTO·페이지 크기·조회 권한은 아직 확정하지 않았습니다. 사용자 식별자를 URL/query/access log에 노출하지 않는 형태를 제안하고 raw event 전문을 기록하지 마세요. 새 장기보존이 필요하면 목적/최소 필드/기간을 제시해 승인받아야 합니다.

### 5.5 계정 삭제 안전성 회신

- 탈퇴한 userId를 이후 계정에 재사용하지 않는가? SNS 재연결/재가입·관리자 복구도 포함해 확인합니다.
- 탈퇴 후 login/refresh/복구에서 purchase token 신규 발급이 차단되는가? 이미 실행 중인 발급과 경합은 어떻게 수렴하는가?
- 환경별 access token 최대 수명과 과거 설정 변경으로 남아 있을 수 있는 최장 token은 얼마인가? Billing 최대60초 skew와 합쳐 안전한 drain 근거를 만들어야 합니다.
- 기존 LC용 탈퇴 delivery 보존 종료 후 Billing 재전달이 가능한가? 불가능하면 어느 시점부터 coverage를 보장할 수 있는가?

Billing의 진행 중 요청 deadline·최종 생성 시 만료 재검증은 Billing 책임입니다. Identity에 Billing account/ref 보관을 요청하지 않습니다. 위 근거가 없으면 영구 user hash를 임의 저장하는 대신 purge를 보류하고 보존/복구 계약을 재검토합니다.

### 5.6 배포 순서

계약 합의 → Billing consumer/차단 로직 비활성 배포 → Identity Billing delivery 비활성 배포 → staging 인증·중복·누락·경합 검증 → Billing consumer 활성 → Identity publisher 단계 활성 및 snapshot/feed coverage 확인 → 구매 scope/account 기능 단계 활성 순서를 제안합니다. LC 기존 전달은 계속 유지합니다.

scope가 token에 존재해도 Store 결제/유료 admission/자동 purge 활성화를 의미하지 않습니다. 활성화 주체·rollback·미전달 backlog 보존을 양 서버 runbook에 적고 실제 판매·삭제는 별도 gate를 통과합니다.

## 6. 부록 — 근거·테스트·회신 양식

### 6.1 로컬 확인 사실 (운영 배포 확인 아님)

- [UserWithdrawnWireEvent](../../../identity/src/main/java/web/tosunsaeng/identity/domain/user/withdrawalevent/application/UserWithdrawnWireEvent.java): 기존4필드와 JsonPropertyOrder.
- [UserWithdrawnEventMapper](../../../identity/src/main/java/web/tosunsaeng/identity/domain/user/withdrawalevent/application/UserWithdrawnEventMapper.java): 발행4KiB.
- [UserWithdrawnPublisherProperties](../../../identity/src/main/java/web/tosunsaeng/identity/domain/user/withdrawalevent/infrastructure/UserWithdrawnPublisherProperties.java), [adapter](../../../identity/src/main/java/web/tosunsaeng/identity/domain/user/withdrawalevent/infrastructure/JdkUserWithdrawnDeliveryAdapter.java): LC 단일 endpoint/workload JWT 경로.
- [UserWithdrawalTransactionService](../../../identity/src/main/java/web/tosunsaeng/identity/domain/user/application/UserWithdrawalTransactionService.java): 탈퇴/refresh/phone revoke/outbox 연결.
- [JwtAccessTokenIssuer](../../../identity/src/main/java/web/tosunsaeng/identity/global/security/jwt/JwtAccessTokenIssuer.java): Billing read/account_type 처리. 신규 purchase 발급의 상태 검증은 호출 경로 전체 검토 필요.
- [application.yml](../../../identity/src/main/resources/application.yml): access-token-ttl 기본 PT30M. 운영 설정/최대값 미확인.

타 저장소 링크는 동일 부모 checkout 기준의 조사 근거이며 런타임 의존성이 아닙니다.

### 6.2 완료 테스트

- Guest purchase 거절; ACTIVE MEMBER 성공; SUSPENDED/WITHDRAWN/MERGED 신규 purchase 미발급; 기존 audience/read/account_type 회귀.
- default/explicit scope·refresh·SNS·승격·복구 및 탈퇴와 token 발급 경합. 최대 token 수명 증빙.
- 탈퇴 transaction rollback 시 event 없음; commit 성공/응답 유실 시 동일 event로 수렴.
- Billing 실패+LC 성공, 역방향, 양쪽 retry·lease 만료·duplicate worker·독립 flag/재개.
- SigV4 정확 role/환경/route 성공과 unsigned/wrong role/direct 우회 실패; LC 기존 JWT 유지.
- 204 duplicate·409 충돌·401/403 차단·429/503 retry·404 계약 오류. 로그에 token/userId/payload 전문 미포함.
- snapshot 도중 신규 탈퇴·동일 timestamp·page retry·cursor 만료·보존 gap·checkpoint 장애 복구.
- 재가입 새 userId와 과거 탈퇴가 분리되며 무료 Claim·paid 권리 자동 이전/지급 없음.

단위/통합 테스트는 fake Billing/credential을 사용하고 실제 사용자·Store를 호출하지 않습니다. 실제 Lattice 연동은 staging gate에서 별도 수행합니다.

### 6.3 Identity 회신 요청

| 항목 | 회신할 내용 |
| --- | --- |
| 현재 구현 | 관련 파일/테스트와 이미 완료·추가 필요 구분 |
| purchase scope | 전체 발급 경로/ACTIVE 확인 및 탈퇴 경합 처리 |
| wire/route | 기존4필드 유지 가능 여부, Billing 제안 route/오류 합의 |
| delivery | 기존 LC 보존, 목적지별 상태/flag/retry/권한/보존안 |
| 누락 복구 | snapshot/feed exact DTO·cursor/완료 기준·원천 보존/gap 처리 |
| 삭제 안전성 | userId 비재사용·실제 최대 token lifetime·탈퇴 후 발급 차단 |
| 운영 영향 | 추가 개인정보 보존·중단/이관·담당/rollback·사용자 승인 필요 항목 |
| 검증 | 추가 테스트/미검증 외부 사항·staging 선행조건 |

회신 검토 후 Billing ADR/009 기술 계약을 동기화하고 Jira·구현 승인을 받습니다. 인계서 작성은 Identity 변경 실행이나 다른 채팅으로의 전송을 뜻하지 않습니다.
