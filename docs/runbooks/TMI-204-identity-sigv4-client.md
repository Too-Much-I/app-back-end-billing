 > 2026-10-13 폐기 안내: 이 문서는 과거 SigV4 구현/검증 기록이다. 현재 코드의 SigV4 wrapper·SDK·인증 선택 설정은 제거 대상이며 JWT 단일화 규격은 [현행 인증 계약](../contracts/WORKLOAD-JWT-AUTH-R1.md)을 따른다. 이 문서의 구 설정을 주입하면 기동 오류이며 활성화 절차로 사용하지 않는다.

# TMI-204 Identity recovery SigV4 client

> 2026-10-12 후속 승인/구현: Identity–Billing 신규 목표는 [workload-auth-r1](../contracts/WORKLOAD-JWT-AUTH-R1.md)의 ALB/JWT다. 이 문서는 명시적 legacy sigv4 모드/과거 검증 기록이며 JWT 실패 시 fallback으로 사용하지 않는다. 공통HTTP코어는 HttpIdentityRecoveryClient로 분리했다. HTTP 기본OFF 유지.

- 날짜: 2026-10-12 / 브랜치: feat/TMI-204-billing-withdrawal-recovery
- 상태: Billing 로컬 구현·가짜 credential/서버 검증. 실제 Identity/AWS 연결·배포·활성화 완료 아님.

## 5줄 결론

1. 승인된 AWS SDK v2 `auth`·`http-auth-aws`로 4개 recovery-r1 POST를 서명한다.
2. HTTPS·서울 region·환경별 exact Lattice host를 검사하고, 요청 본문을 한 번 직렬화해 SHA-256 서명 후 그대로 보낸다.
3. 연결 3초·HTTP 전체 5초·응답 16KiB 상한·redirect 차단을 적용했다.
4. 인증/계약/일시 오류를 구분하고 `Retry-After`는 기존 DB 재시도 일정에 연결한다.
5. 기본 OFF이며 실제 IAM·Identity API·운영 이관·복원·staging 검증 전 켜지 않는다.

## 반드시 읽어야 하는 내용

`SigV4IdentityRecoveryClient`는 기존 worker port의 구현이다. API, 공유 fixture, source error 의미, 무료권/결제 정책을 바꾸지 않는다. Identity 코드·설정은 수정하지 않았다.

SDK는 Identity의 로컬 기준과 맞춘 BOM **2.29.52**를 사용한다. 최신 버전 또는 취약점 검증 완료라는 의미는 아니다. 추가 운영 모듈은 `auth`, `http-auth-aws`이며 STS/S3 서비스 client나 다른 provider SDK는 추가하지 않았다. `DefaultCredentialsProvider`가 요청마다 credential을 해석한다. ECS task role의 회전 credential을 사용하고, 이 기능의 static access key 설정은 제공하지 않는다. ECS에는 개발용 credential 환경변수/profile을 주입하지 않는다.

| API suffix | wire body | 추가 요청 header |
| --- | --- | --- |
| `/snapshots` | `streamId` | durable operation의 `Idempotency-Key` |
| `/snapshots/status` | `snapshotId` | 없음 |
| `/snapshots/page` | `snapshotId`, `cursor`(null 포함) | 없음 |
| `/feed/page` | `streamId`, 문자열 `afterSequence`, `scanCursor`(null 포함) | 없음 |

prefix는 `/internal/v1/billing/withdrawals`다. `expectedTarget`·local epoch·status의 expected stream은 검증용 로컬 값이며 wire에 추가하지 않는다. cursor는 body 값일 뿐 URL로 사용하지 않는다.

### 서명과 전송

- service=`vpc-lattice-svcs`, region=`ap-northeast-2` 고정. body UTF-8 bytes의 SHA-256을 사용하며 UNSIGNED-PAYLOAD로 대체하지 않는다.
- Content-Type/Accept/Idempotency-Key/payload hash를 먼저 고정한 뒤 SDK signer가 Host·날짜·session token·Authorization을 생성한다.
- 서명 후 URI/body/header를 다른 middleware로 수정하지 않는다. JDK의 제한 header 설정을 완화하지 않으며 Host는 동일 URI의 authority와 일치해야 한다.
- inbound 임의 header나 baggage를 복사하지 않는다. 이 단계는 새 traceparent 주입/HTTP span 자동계측을 추가하지 않았으며 전체 PLAN의 outbound trace 연계는 후속 관측 gate다.
- JDK HttpClient HTTP/1.1, redirect NEVER, proxy/cookie/authenticator 미사용. 애플리케이션/SDK 재시도 loop는 없다. 연결 이전의 JDK 내부 연결 동작과 별개로, 서버가 POST를 읽은 뒤 응답이 유실된 테스트에서는 전송이 1회임을 검증했다. 재시도는 worker의 영속 정책과 같은 operation key를 따른다.
- 3초 connect timeout, 전송 시작부터 body 완료까지 5초 deadline. credential provider 탐색 시간은 HTTP deadline 밖이며 ECS credential endpoint의 가용성/지연은 staging 관측 대상이다.
- body subscriber가 chunk마다 누적 16KiB를 검사한다. 전체를 무제한으로 읽은 뒤 검사하지 않는다. timeout 시 pending 전송을 취소한다. TLS 검증을 끄는 코드가 없다.

### 오류와 대기

| 응답 | 처리 |
| --- | --- |
| 401/403 | AUTH, 원문 없는 blocked 경로 |
| 408/425/429, transport 오류/timeout | TRANSIENT, 영속 재시도 |
| generic 5xx | TRANSIENT |
| strict 검증된 500 RESPONSE_LIMIT_EXCEEDED | APPLICATION nonretryable; generic 5xx보다 우선 |
| 409/410 source 오류 | decoder의 stream/after/target binding 검증 뒤에만 source loss로 분류 |
| redirect/뜻밖의 성공 코드/잘못된 body/초과 body | CONTRACT |

성공은 200, 생성 BUILDING에 한해 202를 허용한다. UTF-8·strict scalar/field·binding 검증은 기존 decoder가 수행한다. Content-Encoding 응답은 자동 해제하지 않고 거절한다. parser/credential/transport 원문이나 exception cause를 상위 오류에 담지 않는다.

`Retry-After`는 초·HTTP date를 해석하고 큰 유효값을 줄이지 않는다. 잘못된/중복 header는 기존 backoff로 돌아가며 `billing.recovery.http.retry_after{outcome=INVALID}` 신호를 기록한다. 24시간 초과는 `OVER_ONE_DAY` 신호다. BUILDING 성공 응답의 긴 대기도 DB `nextAttemptAt`에 저장하며 프로세스 재시작 후 유지한다. HTTP가 ON이면 수동 `step`도 이 대기와 dispatch budget을 우회하지 않는다.

## 사용자가 결정해야 하는 사항

새 상품 정책은 없다. SDK 추가 승인은 이번 작업에 반영했다. 실제 환경별 host/환경 marker·IAM task role·운영 이관/활성화 시점은 배포 전에 확정하고 승인해야 한다. 정확한 실제 주소/credential은 문서에 기록하지 않는다.

## 주요 위험과 미확인 사항

- 실제 Identity controller 연결/배포 readiness·4개 route IAM·wrong environment/unsigned/direct bypass를 검증하지 않았다.
- endpoint allowlist는 배포 설정을 pin하는 방어다. 운영자가 잘못된 환경 주소와 marker를 함께 넣는 상황까지 애플리케이션만으로 증명할 수 없으므로 인프라 환경 대조가 필요하다.
- SDK dependency 보안 점검, task role credential 회전, 실제 TLS/DNS/Lattice timeout·대용량 동작은 별도 staging gate다.
- Mongo 이관/epoch installer·복원·외부 watchdog/알림 채널·전체 snapshot hash 실행시간 조율·outbound trace 계측은 아직 후속이다. TMI-204 전체 완료가 아니다.

## 설정·배포 조건

| 환경변수 | 기본값/조건 |
| --- | --- |
| `BILLING_PAYMENT_RECOVERY_HTTP_ENABLED` | false |
| `BILLING_PAYMENT_RECOVERY_IDENTITY_BASE_URL` | 빈 값; HTTPS Lattice generated DNS origin, 끝 slash/path/port/query/userinfo/fragment 없음 |
| `BILLING_PAYMENT_RECOVERY_IDENTITY_ALLOWED_HOST` | 빈 값; base URL host와 exact 일치 |
| `BILLING_PAYMENT_RECOVERY_IDENTITY_ENVIRONMENT` | 빈 값; payment environment SANDBOX/PRODUCTION과 일치 |

HTTP ON은 fence-required 및 control schema4/현행 validator·index 검증이 필수다. 기존 snapshot job의 retry metadata가 없으면 시작을 거절하며 암묵적으로 초기화하지 않는다. OFF일 때는 HTTP client/credential provider bean 자체를 만들지 않는다. HTTP ON만으로 worker가 켜지지는 않는다. worker flag는 독립이며, worker ON/HTTP OFF일 때 다른 승인된 client bean이 없다면 기존대로 startup 실패한다.

## 검증 근거

- `SigV4IdentityRecoveryClientTest`: exact body/header·문자열 Long.MAX_VALUE·nullable cursor·독립 HMAC oracle·credential 갱신 호출·원문/원인 비노출·오류 분류·source binding·UTF-8·환경 설정·기본 OFF.
- `RecoveryHttpTransportTest`: loopback fake HTTP 서버에서 실제 wire 서명 검산·body 보존·16KiB 경계·chunked 초과·본문 stall timeout·redirect 미추종·응답 유실 POST 1회·Host mismatch 차단. 실제 TLS/IAM 인증 시험은 아니다.
- `WithdrawalSnapshotIntegrationTest`: BUILDING 긴 대기의 Mongo 저장/프로세서 재생성 뒤 유지·수동 HTTP step의 대기 준수·구 schema 활성화 거절.
- 전체 실행 수치는 WORKLOG/CURRENT_STATE에 기록한다. 실제 Identity/AWS/Store/RevenueCat 호출·Jira 쓰기·commit/push·배포는 하지 않았다.
