# LC→Billing workload JWT 전환 — Billing 검토·회신 초안

- 작성일: 2026-10-13
- 상태: 검토용 권장안. LC 인증 계약 변경·코드 구현·실제 배포/활성화 승인 아님.
- 2026-10-13 후속 검토 의견 반영: 기존 Billing 권장안 채택 방향과 workload TLS 공유 가능 의견을 접수했다. 404 전환·보존·복구 정책을 아래에 보완했다. 사용자 전달 의견을 정본 계약 확정이나 구현 승인으로 확대하지 않는다.
- 2026-10-13 사용자 추가 요청으로 전환 순서·404 보존/복구 절차를 [공동 통합 계약 §16](BILLING_SERVICE_INTEGRATION_CONTRACT.md#16-lc-jwt-전환의-공동-활성화404-보존복구-절차)에 추가했다. 인증 exact 규격·구현/실제 활성화 승인은 별도다.
- 관련: LC 인계서의 TMI-116/128/122 및 AttemptGroup publisher. 이번 검토의 신규 Jira는 생성하지 않았다.

## 1. 5줄 결론

1. HTTPS와 LC 전용 RS256 workload JWT로 인증만 교체하고 업무 계약을 유지하는 방향은 타당하다.
2. 인계서의 6개 POST와 body는 현재 Billing 구현과 일치하지만 LC의 발신 코드·AWS 현황은 이번에 직접 검증하지 않았다.
3. 현재 승인된 JWT 단일화는 Identity 네 이벤트/네 복구 POST만 포함하므로 LC 전환은 별도 범위 승인과 ADR/AGENTS 동기화가 필요하다.
4. LC 초안의 TTL120·원격 JWKS·인증 JSON 대신 기존 Identity–Billing과 같은 TTL60·배포 공개키·body 없는401/403을 우선 권장한다.
5. 기존 IAM 통과를 차단하고 Billing 수신 준비를 확인한 뒤 LC 발신을 켠다. 전환 중404는 기존 분류와 원본 증거를 유지하고 승인된 복구 절차로 처리한다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 현재 구현과 전환 영향

현재 `SecurityConfig`의 LC `LATTICE_AWS_IAM` 분기는 외부 Lattice가 인증·인가한다는 전제로 `permitAll`한다. Billing 자체가 inbound SigV4를 검증하는 코드가 아니다. 이 분기를 일반 HTTPS/ALB로 그대로 옮기면 인증 경계가 사라진다.

기존 `WorkloadAuthenticationFilter`와 전용 security chain은 Identity issuer/public set/sub/네 목적에만 연결돼 있다. 따라서 LC 키를 설정에 넣는 것만으로 사용할 수 없다. LC 전용 issuer/key verifier·principal·route 인가를 추가하고 Identity 토큰의 LC 접근 및 LC 토큰의 Identity/public 접근을 거절해야 한다. 검증 도구를 재사용하더라도 신뢰 키와 권한은 합치지 않는다.

권장 전환은 LC JWT 수신 기능을 기본 OFF로 구현하고, OFF 상태에서도 지정 LC 업무 route가 기존 IAM 통과나 public/internal 다른 포트로 우회하지 못하도록 만드는 것이다. 기존 실제 사용처가 있으면 새 버전 배포 자체가 legacy sender를 차단할 수 있으므로 배포 전 inventory와 양측 전환 계획이 필요하다. TEST principal은 격리 테스트에서만 제공하며 실제 런타임 인증 fallback으로 유지하지 않는다. 이는 아직 승인 전 제안이다.

### 요청/멱등성 대조 결과

| POST path | 현행 Billing body | 멱등성/성공 |
| --- | --- | --- |
| `/internal/v1/reservations` | 일반 userId/sessionId/mockExamId, phone은 continuationReason/continuationId/expectedAttemptGroupId 세 필드 추가 | lowercase UUIDv4 Idempotency-Key,200 DTO |
| `/internal/v1/reservations/{reservationId}/confirm` | userId/sessionId/sessionCommittedAt | 같은 operation key,200 DTO |
| `/internal/v1/reservations/{reservationId}/cancel` | userId/reason | 같은 operation key,200 DTO |
| `/internal/v1/reservations/status` | userId/operationId | 조회만,200 DTO 또는 현행 업무 오류 |
| `/internal/v1/reservations/continuations/phone` | userId | 200 strict DTO 또는 body 없는204 |
| `/internal/v1/attempt-group-events` | 기존 eventId와 canonical event JSON | eventId/digest 멱등성,현행204/오류 |

JWT sub는 서비스이고 body userId는 인증된 LC가 확정한 사용자다. sessionId/mockExamId는 UUID로 강제하지 않는1~128자 opaque token이다. 일반 reserve는 정확히3개, phone은 정확히6개 필드다. operationId를 reserve/confirm/cancel body에 추가하지 않는다. confirm/cancel path의 reservationId는 기존 parser를 유지하고 단일 segment만 허용한다. `/internal/**`나 느슨한 wildcard로 JWT 권한을 확대하지 않는다.

cancel reason의 `SESSION_COMMIT_FAILED`는 인계서의 LC 현재 발신 설명으로 읽는다. Billing은 `CALLER_ABORTED`도 허용하므로 인증 전환 때문에 기존 reason 집합을 축소하지 않는다. 기존 업무 오류/response DTO/null 생략/16KiB strict decode를 유지한다.

### Billing 권장 인증안 — 아직 제안

| 항목 | 권장 회신 |
| --- | --- |
| 발급 | LC 전용 RSA private key로 직접 RS256 서명,2048bit 이상. 사용자/Identity/Billing signing key와 분리 |
| issuer | `urn:tosunsaeng:workload:learning-core:{env}` 제안,env=test/staging/production 별도 trust |
| subject | `learning-core-service` 제안 |
| audience | 단일 문자열, 아래 두 목적별 audience 제안. 범용 `billing-internal` 대신 환경/목적 분리 |
| header/claims | 기존 workload-auth-r1 strict 구조 재사용: alg/typ/kid만,iss/sub/aud/iat/exp/jti 필수,nbf optional 검증. LC 발신은nbf=iat 가능 |
| 시간 | TTL1~60초,skew30초. NumericDate int64 정수/overflow/미래/시간순서·정확 경계는 기존r1과 동일 |
| jti | 매 HTTP 시도 fresh lowercase UUIDv4,업무operation/event와 별개. DB token 저장·단일사용 replay 목록 없음 |
| 권한 | 고정issuer/key의 신뢰 검증 후sub/aud/method/path 서버 allowlist. 임의scope claim은 권한 근거로 사용하지 않음 |
| 공개키 | 최초는 LC가 공개 JWK set을 제공하고 Billing 배포 설정으로 신뢰. 원격fetch/unknown-kid refresh 없음 |
| 수신 | 기존 workload TLS connector 공유 가능 방향. LC/Identity 키·issuer·principal·목적 인가·수신 활성화는 독립, public/기존 internal port 우회 차단 |

| LC audience 후보 | 허용POST |
| --- | --- |
| `tosunsaeng-billing:reservations:{env}` | reserve/confirm/cancel/status/phone discovery 다섯 업무route |
| `tosunsaeng-billing:attempt-group-events:{env}` | AttemptGroup event 한route |

issuer·audience·subject 문자열은 LC 공동 수락 전 확정값으로 구현/설정하지 않는다. URI issuer는 네트워크 주소가 아니며 실제 DNS를 가정하지 않는다. 신뢰환경과 payment SANDBOX/PRODUCTION enum은 서로 다른 개념이다. test/staging DB·stream 분리도 운영 확인 대상이다.

### 오류 회신

| 상황 | 현재r1과 맞춘 권장안 |
| --- | --- |
| 누락/잘못된형식·서명·issuer·kid·시간·algorithm | body 없는401,no-store |
| 검증된 신뢰token의 잘못된sub/aud/method/route | body 없는403,no-store |
| OFF/잘못된수신port | body 없는404,no-store; 정확 precedence fixture 필요 |
| controller 업무실패 | 기존InternalApiError: code/message/retryable/correlationId 그대로 |
| startup public key 손상/누락 | 켜진 수신기의 기동 실패,인증허용 우회 없음 |

LC의 wrong audience/sub를401로 분류하는 초안은 기존r1의 인증/인가 분리와 다르다. LC가401/403 모두AUTH_FAILURE로 분류한다면 기존 분리를 따를 수 있다. `WORKLOAD_UNAUTHENTICATED/WORKLOAD_FORBIDDEN` JSON은 현재 수신 구현에 없다. 이를 필수로 도입하려면 별도 인증 오류 wire 합의가 필요하며 기존 업무 envelope와 자동으로 같다고 가정하지 않는다.

배포형 공개키에는 런타임 JWKS 조회 장애가 없으므로 원격키 장애503 규칙은 최초안에 해당하지 않는다. 원격JWKS를 채택할 경우만 timeout/cache/refresh 제한·장애와unknown kid 구분 및503 envelope를 별도 설계한다. 업무429/5xx/retry 정책을 인증 전환으로 변경하지 않는다.

### 404 전환·보존·복구 정책 — 공동 동결할 보완안

사용자 전달 LC 검토 의견에 따르면 body 없는 수신 OFF·오포트404는 Reservation에서 `CONTRACT_ERROR`, AttemptGroup publisher에서 `DEAD_LETTER`로 분류하여 자동 재시도를 중단한다. 업무 응답 `code=OPERATION_NOT_FOUND`는 별도 업무 오류이며, 어느 경우도 취소·미소비 확정으로 해석하지 않는다. HTTP404 숫자만으로 둘을 합치지 않는다. LC 소스는 이번에 독립 확인하지 않았으며 오류 구분을 공동 fixture로 확인한다. 이 분류를 그대로 유지하며404를429/503으로 바꾸거나 모든404를 무조건 재시도하게 만들지 않는다.

**활성화 순서**

1. 기존 활성 sender/미완료 saga/outbox와 양측 버전·주소·port·key·flags를 확인한다. legacy 사용처가 있으면 신규 유입과 전환 중 요청을 어떻게 제한할지 계획하고, 복구 worker/원본 기록을 일괄 중단·삭제하지 않는다.
2. LC sender는 준비하되 발신 OFF를 유지한다. Billing JWT verifier·정확 route 인가·TLS/네트워크·관련 업무 기능을 먼저 준비하고 기존 IAM permitAll 우회를 제거한다.
3. 정확한 Billing origin/connector에서 정상 LC 토큰이 각 route의 인증/인가를 통과하고, OFF/다른 port/Identity·사용자 토큰/잘못된 목적은 거절되는 공동 검증 증거를 확보한다. 단순 health200이나 유효 토큰의 business400만으로 전체 수신 준비를 판정하지 않는다. 예약/소비를 만들지 않는 기존 조회와 격리 환경의 여섯 route E2E를 조합한다. 새 readiness API는 이번 범위에서 추가하지 않는다.
4. 준비 확인 후 LC 발신을 단계적으로 활성화한다. 404·AUTH_FAILURE·계약 오류·DEAD_LETTER 증가 시 신규 활성화를 중단하고 대상 origin/route/flag/버전을 조사한다. Identity sender나 진행 중 LC 복구 증거를 함께 끄지 않는다.

**404 관측 뒤 보존과 재처리**

| 대상 | 보존 | 복구 원칙 |
| --- | --- | --- |
| Reservation command/saga | 기존 operationId·canonical 업무 요청·Session commit 증거·상태/lease·실패 이력. JWT 원문은 보존하지 않음 | 수신 복구 후 기존 status-first 정책으로 상태를 확인하고 원 operation/key/body로 필요한 후속만 수행. 상태 조회도 실패하면 미확정 상태를 유지하며 새 시험/새 key를 자동 생성하지 않음 |
| AttemptGroup outbox | DEAD_LETTER에서도 기존 eventId·canonical payload/digest·업무 순서/버전·실패 이력을 보존 | 배포/route/flag 원인 해결과 담당 승인 후 기존 event를 bounded requeue. same eventId/body/digest, 매 전송 새JWT/jti/span. 재처리를 위해 eventId나 발생 시각·payload를 다시 쓰지 않음 |

Reservation status가 RESERVED이면 기존 TTL·Session commit 증거에 따라 원 confirm/cancel 정책을 적용한다. CONFIRMED는 중복 소비하지 않고, CANCELED/EXPIRED는 자동 repair-confirm하거나 다시 열지 않는다. OPERATION_NOT_FOUND도 성공/미소비로 추정하지 않고 원 saga/Session 증거와 기존 복구 계약을 따른다. 전환을 이유로 TTL/Claim/owner fence를 늘리거나 권리를 재지급하지 않는다.

재처리 대상·상한·담당 권한·감사 및 종료 기준을 LC runbook에 고정해야 한다. 2026-10-13 LC 후속 검토 보고에서 확인한 코드·스크립트에 승인 재처리 도구를 찾지 못했으므로 준비 완료로 간주하지 않는다. 별도 승인·구현·검증을 전환 선행 조건으로 둔다. 이 보고는 Billing의 LC 전체 코드 독립 검증은 아니다. DB 직접 수정/새 공개 repair API/무인증 또는 SigV4 fallback을 복구 수단으로 제공하지 않는다. 원인이404였다는 이유만으로 이후 stale/conflict/owner fence 등의 업무 거절을 우회하지 않는다.

공동 fixture에 OFF/다른 port의404가 command/event를 자동 삭제하지 않는지, DEAD_LETTER 보존과 승인 후 같은event 재처리, 응답 유실과404 경합의 status-first 수렴, 이미 만료/취소된 Reservation의 불변성을 포함한다. 완료 증거는 원본 이벤트의 최종 수렴 또는 담당자가 기록한 미해결 사유·후속 조치이며 단순 queue 상태 초기화가 아니다.

### 공유 TLS connector의 독립성 조건

기존 connector를 공유해도 LC/Identity 공개키와 issuer·principal·audience·route allowlist를 각각 검증한다. LC OFF/Identity ON과 LC ON/Identity OFF를 모두 지원하고, 켜진 방향의 필수 키만 검증한다. connector 기동은 켜진 수신 방향을 함께 고려하되 어느 한 방향의 OFF가 다른 방향을 우회 허용하거나 중단하지 않아야 한다. 모두 OFF일 때도 해당 route는 기존 internal/public port에서 접근되지 않는다. 기존 Identity 토큰의시간/키/오류 fixture와 네 이벤트 업무 회귀는 그대로 유지한다.

### Fixture/성공 응답 근거

- 현행 업무 정본: [통합 계약](BILLING_SERVICE_INTEGRATION_CONTRACT.md), ADR-001/003과 [trace 인계](LEARNING_CORE_ATTEMPT_GROUP_TRACE_HANDOFF.md).
- ReserveResponse: operationId/reservationId/reservationKind/reservationStatus/attemptGroupId/sessionId/mockExamId/continuationReason/continuationId/expiresAt. null 필드는 생략한다.
- ConfirmResponse: operationId/reservationId/reservationStatus/attemptGroupId/attemptGroupStatus/sessionId/confirmedAt.
- CancelResponse: operationId/reservationId/reservationStatus/canceledAt.
- StatusResponse: operationId/reservationId/reservationKind/reservationStatus/attemptGroupId/attemptGroupStatus/sessionId/mockExamId/continuationReason/continuationId/expiresAt/terminalAt. null 필드는 생략한다.
- PhoneContinuationResponse: continuationReason/continuationId/attemptGroupId/mockExamId 또는204.
- 기존 `ReservationControllerTest`, `AttemptGroupEventControllerTest`가 업무 회귀 근거다. 기존 Identity JWT 시간fixtures는 참고자료이며 LC 독립키·목적/route·body 없는인증오류 fixture를 검증한 증거가 아니다.

다음 공동동결에서 JSON 성공/업무오류/인증HTTP fixtures와hash manifest를 만들어 양측 독립 실행한다. 이번 회신에서는 새 fixture 생성이나 테스트 실행을 하지 않았으며 성공 검증으로 보고하지 않는다.

## 3. 사용자가 결정해야 하는 사항

1. LC 여섯 업무 route까지 Lattice 제외·workload JWT 단일화를 확장할지 승인. 기존 Identity 예외를 LC 승인으로 해석하지 않는다.
2. 접수된 권장안 채택 방향에 따라 TTL60·배포 공개키·목적별 audience·body 없는 401/403 및 TLS 공유안을 공동 exact 규격으로 수락·동결한다. issuer/audience 문자열과 오류 precedence·독립 flag/설정 이름까지 확정해야 한다.
3. 404 보완안의 status-first·원본 보존·담당 승인 후 bounded requeue와 LC 기존 구현/도구의 호환성을 확인하고 운영 책임·재처리 상한을 동결한다.
4. 기존 무료 기능의 실제 활성 sender·미완료 Reservation/outbox·배포 대상과 변경 순서 확인. DNS/ALB/TLS/키 담당·긴급 폐기 SLA는 별도 운영 결정.

## 4. 주요 위험

- JWT는 body/method 서명이 아니다. TLS·단기 TTL·비로깅·목적별 인가를 적용하고 새로운 body binding이나 replay 정책은 이번에 임의로 추가하지 않는다.
- 기존 IAM permitAll을 남기고 JWT만 추가하면 우회가 남는다. 반대로 legacy 사용처 확인 없이 차단 버전을 배포하면 진행 중 saga가 멈출 수 있다. 현재 운영 OFF라고 추정하지 않는다.
- Identity와 LC를 한 key set/principal로 합치거나 issuer만 바꿔 신뢰하면 상호 권한이 침범한다. 테스트에 LC→Identity/Identity→LC/user→LC 실패를 포함한다.
- 단기 토큰 키 회전은 old+new 공개키를 전체 receiver에 먼저 배포→new 발급→old 발급 종료 확인→최소120초(TTL60+미래 iat30+exp skew30) 뒤 old 신뢰 제거다. 긴급 폐기는 이 대기를 강제하지 않으며 담당/SLA가 필요하다.
- 첨부의 AWS 현황은 LC 보고다. 이번에 Billing 실제 AWS 계정/리전/서비스/키를 조회하지 않았으며, 인프라 없음이나 안전한 경로 존재를 확정하지 않는다.

## 5. 후속 구현·검증 순서

1. 사용자 범위 승인/LC 수락 후 ADR-002·업무 통합 계약·AGENTS에 LC 예외와 폐기 경로를 동기화한다. Identity 승인 규격·과거 검증 기록은 유지한다.
2. Billing LC 신뢰/인가·ingress guard·독립 flag·startup 검증을 OFF로 구현. LC 발신 교체는 LC 저장소 담당이 수행한다.
3. 격리 공동 fixture: strict JWT/시간/환경/키/rotation/목적/포트/기능 OFF·wrong method/path·encoded path·forwarded header/Authorization 중복, 인증 실패 DB 무변경을 검증한다.
4. 기존 same-key 응답 유실/status 복구·confirm/cancel·phone owner fence·AttemptGroup 멱등/trace·전체 무료권·Identity JWT 회귀를 실행한다. Billing 구현 후 `./gradlew clean test`, 실패0을 확인한다.
5. 실제 HTTPS/네트워크/키·동시 전환 inventory와404 보존/복구 runbook 검증 후 staging E2E. Billing 수신 준비 확인 뒤 LC 발신을 활성화한다. 날짜/배포 가능 시점은 현재 미확정이며 코드 테스트 완료와 운영 활성화 완료를 분리한다.

이번 작업은 Billing 코드/설정·Identity/LC 저장소·AWS·Jira 변경이나 외부 회신 전송을 수행하지 않았다. 기존 미커밋 변경을 보존한 문서 검토다.
