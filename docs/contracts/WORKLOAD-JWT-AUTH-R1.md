# Identity–Billing workload-auth-r1 — Billing OFF 구현 규격

- 날짜: 2026-10-12, JWT 단일화 개정 2026-10-13 / TMI-203·204, 영향 TMI-202·206
- 상태: 사용자 승인 계획의 Billing 로컬 구현. Identity 독립 검증/공동 fixture 수락·배포 완료 아님.

## 1. 5줄 결론

1. Identity 이벤트4POST 수신과 Billing recovery4POST 발신에 전용 RS256 JWT를 연결했다.
2. 사용자/LC 키·audience를 사용하지 않고 환경별 고정 issuer/목적별 audience를 검사한다.
3. Billing 수신은 별도 TLS connector이며 public/기존 내부 port에서 신규 JWT 수신을 우회할 수 없다.
4. HTTP 전송/strict decoder/DB 복구 코어를 재사용하고 SigV4/JWT 실패 fallback은 없다.
5. 기능은 기본 OFF, 키/port/실제 환경은 빈 값이며 공동 검증과 운영 gate 전에는 켜지 않는다.

## 2. 반드시 읽어야 하는 내용

승인 범위는 [계획 §2.5](../plans/TMI-204-withdrawal-recovery.md)다. 이 규격은 ADR-002와 AGENTS에 기록한 Identity–Billing 예외를 구체화한다. 기존 사용자 JWT와 LC 경로의 계약은 바꾸지 않는다. 별도 body 서명 없는 Bearer이므로 SigV4와 같은 request integrity를 주장하지 않는다. jti는 단일 사용을 강제하지 않으며 탈취된 토큰은 유효기간 내 재사용될 수 있다.

### JWT exact

- trust 환경: test/staging/production. test·staging은 Billing SANDBOX, production은 PRODUCTION과 매칭. 같은 SANDBOX의 DB/stream 분리는 별도 배포 gate이며 코드가 인프라 완전성을 추론하지 않는다.
- issuer: `urn:tosunsaeng:workload:identity:{env}` / `urn:tosunsaeng:workload:billing:{env}`. 토큰 값으로 원격 JWKS를 선택하지 않는다.
- header: alg=RS256, typ=JWT, kid의3필드만 수락. kid는 신뢰한 public set에 있어야 한다. critical/jku/x5u 등 추가 header는 거절한다.
- claims: iss/sub/aud/iat/exp/jti 필수, aud는 단일 비어 있지 않은 문자열. jti는 lowercase UUIDv4. optional nbf 검증. 추가 claim은 권한 근거로 사용하지 않는다.
- duplicate header/claim, 잘못된 UTF-8/JSON/trailing token·손상 compact token 거절. 토큰 최대16KiB.
- iat/exp/nbf: 비음수 int64 JSON integer만 수락. 문자열/소수/지수/overflow 거절. TTL은1~60초, iat<=now+30초, now<exp+30초. nbf가 있으면 iat<=nbf<exp 및 nbf<=now+30초. 정수 epoch로 token을 발급하되 검증 시 now의 소수초를 버리지 않는다.
- 발신은 매 HTTP 시도 직전에 새 JWT/jti를 발급한다. same eventId/operationId/body 재시도는 기존 정책 그대로다. token은 DB에 보관하지 않는다.

### 권한

| 방향/sub | audience | POST path |
| --- | --- | --- |
| Identity/identity-service | `tosunsaeng-billing:withdrawal-events:{env}` | `/internal/v1/payments/accounts/withdrawal/events` |
| Identity/identity-service | `tosunsaeng-billing:trial-eligibility-events:{env}` | `/internal/v1/eligibility/trial/events` |
| Identity/identity-service | `tosunsaeng-billing:trial-owner-events:{env}` | `/internal/v1/eligibility/trial/owner/events` |
| Identity/identity-service | `tosunsaeng-billing:user-merge-events:{env}` | `/internal/v1/owners/merge/events` |
| Billing/billing-service | `tosunsaeng-identity:withdrawal-recovery:{env}` | `/internal/v1/billing/withdrawals` 아래 기존4POST |

인증 검증기에서는 신뢰 issuer/key/서명/형식/시간을 확인하고 sub/aud는 반환한다. 수신 filter가 목적/route/sub 인가를 별도로 수행한다. Billing→Identity의 수신 구현은 Identity 담당이며 이 저장소에서 구현/수정하지 않았다.

### 키 설정

원격 key fetch 없음. public JWK set의 keys는1~20개, 입력최대64KiB. 각 key는 kty=RSA, modulus≥2048bit, 유일한 nonblank kid≤256자, alg=RS256, use=sig, key_ops=[verify]가 필수다. 공개 n/e 외 private/임의 field를 허용하지 않는다. 잘못된 key 하나가 있으면 전체 startup 실패다. 공개키 n/e의 기본 수학적 유효성도 검사한다.

Billing signing 설정은 별도 private RSA JWK와 key_ops=[sign]을 사용한다. public/private 쌍의 sign/verify 자체 점검을 기동 때 수행하며 원문/파싱 원인을 오류에 담지 않는다. 실제 key 생성·Secret 등록은 하지 않았다. 설정 클래스에는 secret을 출력하는 toString을 추가하지 않았다.

정상 rotation/120초 및 긴급 폐기는 승인 계획을 따른다. in-memory key set은 배포 때 교체하며 unknown kid에 원격 refresh하지 않는다. key 제거 후의 토큰은401이다. 구 key를 무기한 유지하지 않도록 운영자 교체 완료 확인이 필요하다.

### 수신·오류

workload receiver ON이면 Identity4route는 configured local port에서만 처리한다. X-Forwarded-Port/Host는 신뢰하지 않는다. 다른port의4route는404, 신규port에서 feature OFF의route도404다. 활성port의 미허용 internal route는 인증 성공 후403이고 token이 없으면401이다. `/api` 등 기존 public ingress 제한은 유지한다.

Authorization 단일 header·정확한 Bearer 형식만 수락하며 query string을 가진 workload 요청은401로 거절한다. token/서명/issuer/키/시간/형식 오류401, 검증된 토큰의 잘못된subject/audience/method/route403. 응답은 no-store와 고정 status이며 raw 오류/토큰을 노출하지 않는다. 인증은 controller의 strict body decode보다 먼저 수행한다. 유효 요청의 business204/400/409/503 계약은 그대로다.

### 발신

2026-10-13 승인에 따라 `HttpIdentityRecoveryClient`의 공통 body·decoder·retry는 유지하고 JWT 발신만 남겼다. SigV4 wrapper/credential/signer와 명시 AWS SDK 의존성을 제거했으며 중립 immutable request를 사용한다. `billing.payment.recovery.authentication` 및 `BILLING_PAYMENT_RECOVERY_AUTHENTICATION`은 값이 JWT이거나 비어 있어도 기동 오류다. 구 설정 자체를 제거해야 한다. 기능 OFF에서도 이를 검사하며 자동 fallback은 없다.

JWT origin은 HTTPS/port없음/path없음/query없음/userinfo없음/fragment없음 및 allowed-host exact match를 요구한다. identity-environment는 test/staging/production trust 환경과 일치해야 한다. payment SANDBOX/PRODUCTION과 혼용하지 않는다. body·nullable cursor·문자열sequence·operation key·16KiB·connect3초/HTTP5초·redirect차단·Retry-After·401/403중단은 기존 동작을 유지한다. baggage/임의 inbound header는 복사하지 않는다.

## 3. 사용자가 결정/확인해야 하는 사항

추가 상품정책은 없다. 실제 origin/port/key/DB/source mapping·ALB rule/SG/노출 승인·rotation 담당은 여전히 미확인이다. 이 구현은 TLS target 권장안에 맞춰 TLS connector만 제공하며 평문 connector fallback은 없다. 실제 TLS 운영 승인이 없으면 ON할 수 없다.

## 4. 주요 위험과 미확인 사항

- TLS connector 설정 검증과 mock HTTP 인증 테스트는 실제 ALB/TLS handshake·SG/direct task 격리 검증이 아니다. ALB target TLS를 인증서 검증/mTLS로 간주하지 않는다.
- Identity 네 이벤트는 receiver OFF이면 모든 port에서, ON이면 전용 port 이외에서 차단한다. 기존 IAM/TEST role 통과 경로를 제거했다. 이미 legacy sender가 실행 중이면 수신 전환/발신 전환 계획을 적용해야 하며 무중단 이중 인증을 자동 제공하지 않는다. LC 기존 경로는 변경하지 않았다.
- trust 설정만으로 test/staging DB 공유 충돌이나 잘못 주입된 키/origin을 입증할 수 없다. 운영 inventory와 배포 검사가 필요하다.
- Identity 수신 구현은 상대 보고이며 실연동/공동 배포 승인은 미완료다. 기존 body fixture/hash는 변경하지 않았다. Identity 공유16개 시간fixture를 별도 경로로 동기화하고 원문 NumericDate token을 실제 Billing verifier로 검증한다. Billing 기존13개도 유지한다.
- 2026-10-13 outbound CLIENT span·offline epoch/barrier 도구·명시 validator 이관·분할 hash/scheduler 격리를 로컬 구현했다. [운영 runbook](../runbooks/TMI-204-operator-and-bounded-verification.md)을 따른다. 실제 이관/복원·외부경보·부하·격리 권한/공동 연동 검증은206 gate다. 전체 운영 완료가 아니다.

## 5. 설정과 완료 기준

`billing.workload-jwt` 설정: receiver-enabled=false, port=0, environment/identity-public-jwk-set/billing-private-jwk/tls-key-store/tls-key-store-password는 빈 값. env 이름은 application.yml 참조. 켤 때 port는1024~65535이며 기존 internal/public port와 달라야 한다. transactional schema/expected eligibility scope·환경이 필수다. TLS는 RSA PKCS12 keystore, TLS1.2/1.3이다. 잘못된 실제 keystore는 Tomcat 기동 실패로 처리되며 fallback하지 않는다. 토큰 signing key와 TLS keystore는 역할이 다른 별도 자료다.

receiver OFF는 새 connector를 열거나 public key를 파싱하지 않으며 Identity 네 이벤트를 차단한다. LC internal ingress만 기존 명시 mode를 따른다. recovery HTTP OFF는 발신 private key를 읽지 않는다. 발신·수신 ON/OFF는 독립이며 켜진 방향에 필요한 설정만 요구한다.

검증: strict JWT/time/key/rotation·포트/인가 단위테스트, 실제 PaymentWithdrawalController MockMvc의 JWT→strict decode→204 및 인증실패service미호출, TLS connector설정검사, JWT발신의 fresh token/samebody/operation/noSigV4/fallback없음, 구 SigV4 거절 및 전체무료/복구회귀. 최종 결과는 CURRENT_STATE/WORKLOG에 기록한다.

## 6. 근거

- [계획](../plans/TMI-204-withdrawal-recovery.md), [승인 회신](TMI-203-204-ALB-WORKLOAD-JWT-BILLING-REPLY-2026-10-12.md)
- [JWT codec](../../src/main/java/web/tosunsaeng/billing/global/config/security/WorkloadJwtCodec.java), [수신 설정](../../src/main/java/web/tosunsaeng/billing/global/config/security/WorkloadSecurityConfiguration.java)
- [발신 설정](../../src/main/java/web/tosunsaeng/billing/domain/payment/recovery/RecoveryHttpConfiguration.java), [설정값](../../src/main/resources/application.yml)
- [별도 시간 fixture](../../src/test/resources/contracts/workload-auth-r1/time-cases.json)
