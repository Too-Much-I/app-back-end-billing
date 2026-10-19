# Identity–Billing SigV4 제거: Billing 범위 확인 및 이행 회신

- 날짜: 2026-10-13
- 브랜치: feat/TMI-204-billing-withdrawal-recovery
- 상태: 첨부 인계 요청에 대한 로컬 정적 검토·이행안. 제거 구현/실배포/활성화 또는 신규 공동 fixture 수락 완료가 아니다.

## 1. 결론과 현재 근거

Identity–Billing만 JWT 단일화하는 방향에 동의한다. LC/user 인증과 다른 AWS 서비스 인증은 변경하지 않는다. LC 수신 API는 이미 구현돼 있으나 Billing의 Lattice 모드는 외부 인증을 신뢰하는 설정이지 자체 SigV4 검증기가 아니다.

| 질문 | Billing 확인 결과 |
| --- | --- |
| 현재 실행 중인 SigV4가 있는가 | 운영 배포/활성 설정은 미확인. 로컬 RecoveryHttpConfiguration에 JWT/SigV4 선택이 있으며 recovery.authentication 기본값은 sigv4다. 기본 OFF만으로 운영 미사용을 단정하지 않는다. |
| IAM 의존 수신을 제거할 수 있는가 | Identity 네 이벤트만 제거 가능하다. SecurityConfig의 Lattice permitAll에서 제외하고, WorkloadIngressFilter가 receiver OFF 또는 다른 포트에서도 이 경로를 차단해야 한다. 현재 guard는 receiver ON일 때만 다른 포트를 차단하므로 signer 삭제만으로는 부족하다. LC용 LATTICE_AWS_IAM 분기는 유지한다. |
| SDK의 다른 사용처 | 현재 src/main/java 검색에서 AWS SDK 사용은 recovery HTTP 코어·설정·transport·SigV4 wrapper에 한정된다. 중립 request 타입으로 SdkHttpRequest까지 교체한 뒤 build.gradle의 명시 AWS BOM/auth/http-auth-aws 의존성을 제거할 수 있다. ECS role/Secrets Manager 운영 권한 제거 요청은 아니다. |
| 공동 fixture 검증 | Identity workload-jwt-r1의 README/time-cases를 읽고 비교했다. Billing workload-auth-r1은 offset 기반 13개, Identity는 절대 시각 및 숫자 token/null 경계 포함 16개다. 동일 파일이 아니며 이번에 동기화/독립 실행하지 않았다. |

## 2. 제거 범위와 기능 플래그

1. HttpIdentityRecoveryClient/RecoveryHttpTransport의 인증 중립 요청·응답·예외·Retry-After 처리를 유지하며 AWS 요청 타입/credential/signer 분기를 제거한다. SigV4IdentityRecoveryClient와 Lattice 전용 recovery origin 검증도 제거한다.
2. recovery.authentication 및 BILLING_PAYMENT_RECOVERY_AUTHENTICATION 선택 설정을 제거한다. 구 설정이 주입된 상태를 조용히 무시하지 않도록 명시 오류 검증과 배포 설정 제거를 함께 준비한다.
3. recovery.http-enabled는 Billing의 **발신** 기능 스위치다. OFF면 signing key와 HTTP client 없이 기동, ON이면 JWT key·trust environment·HTTPS origin·기존 recovery 필수조건을 검증한다. snapshot/feed worker 스위치는 별도로 유지한다.
4. workload-jwt.receiver-enabled는 Billing의 **수신** 기능 스위치다. OFF면 Identity 네 이벤트를 모든 포트에서 차단한다. ON이면 전용 TLS connector/public JWK/목적별 권한을 검증한다. withdrawal/owner 개별 기능 스위치는 유지하며 OFF 경로를 열지 않는다.
5. SecurityConfig.validateWithdrawal 등 기존 ingress 모드 의존 검사도 수신 JWT 기준으로 정합화한다. OFF 상태의 service bean 생성과 외부 route 접근 허용을 혼동하지 않는다. TEST principal 우회도 Identity 실제 HTTP 경로에 남기지 않고, controller 테스트는 전용 JWT chain을 사용한다.

인계서의 복구 HTTP 수신은 Identity 기준이다. Billing에서 같은 명칭을 사용하더라도 방향이 반대이므로 위와 같이 구분한다. 수신 OFF가 발신 OFF를 의미하지 않으며, durable capture/미처리 기록 삭제를 의미하지도 않는다.

## 3. 유지 계약과 검증 계획

- 네 이벤트/네 복구 POST의 body·operation/event ID·strict decoding·응답·멱등성·재시도·16KiB·redirect 금지·snapshot/feed·checkpoint/epoch·보존/삭제 조건 유지.
- OFF/ON 조합별 키·origin·TLS 설정 검사, ON 누락/손상 기동 실패, 실행 중 인증 실패 시 fallback 부재 검증.
- Identity 네 이벤트는 unsigned/구 SigV4/잘못된 subject/audience/다른 포트/forwarded port 위조/receiver OFF에서 거절. 올바른 JWT 및 각 기능 ON에서만 처리.
- LC route의 기존 허용/거절 회귀 유지. Identity 경로 변경을 LC 인증 완료나 LC JWT 전환으로 보고하지 않는다.
- 기존 HTTP timeout·body 상한·응답 유실·Retry-After 테스트를 보존하고 HMAC 서명 검산은 JWT 검증으로 대체한다.
- Identity 16개 fixture를 원문 숫자 token을 보존해 실제 Billing verifier로 실행한다. Billing 기존 13개 및 추가 strict claim/권한/키 교체 회귀도 유지한다. 이전 896개 성공은 삭제 후 검증 결과가 아니다.
- 구현 후 ./gradlew clean test 및 문서/설정 사용처 검사. body fixture/hash는 변경하지 않는다.

## 4. 배포 전환과 미확인 조건

실제 환경별 활성 publisher/receiver·버전·origin·port·target group·SG·health check·TLS 설정을 확인해야 한다. test/staging/production trust 분리, payment SANDBOX/PRODUCTION 대응과 DB namespace 격리는 기존 계약을 유지하되 실제 배포값은 아직 확인하지 않았다. health check는 별도 최소 경로만 사용하고 이벤트 인증을 우회시키지 않는다.

양방향 공개키/trust 및 네트워크를 선배포하고, 활성 구 sender가 있다면 새 claim/발신 중지→진행 요청 정리→수신 JWT 단일화→JWT sender 재개 순서로 전환한다. pending delivery와 source capture는 보존하고 LC 전달을 함께 끄지 않는다. 미사용임이 확인되면 최초 JWT 적용으로 진행한다. 인증 오류 시 명시적으로 발신을 중지하며 자동 SigV4/무인증 전환을 하지 않는다.

기존 issuer/audience·RS256·TTL60/skew30·키 선배포/교체 규격을 유지한다. 실제 origin/키/ALB→task TLS 및 긴급 폐기 검증은 별도 gate다. 재시작/응답 유실/인증 중단·재개까지 staging에서 확인한 뒤 승인된 기능만 활성화한다. 이 회신은 판매·자동 삭제·실배포 승인이 아니다.

## 5. 이번 작업 범위

회신·CURRENT_STATE·WORKLOG만 작성했다. 코드/설정/의존성/공유 fixture/Identity/Jira/인프라 변경, 외부 발송, commit/push 없음. Gradle은 재실행하지 않았으며 문서 diff 검사만 수행했다.

## 6. 후속 승인에 따른 구현 결과 — 2026-10-13

위 §1~5는 최초 회신 시점 기록이다. 이후 사용자 구현 승인으로 Billing의 제거 작업과 로컬 검증을 완료했다.

- SigV4 wrapper/credential/signer 및 AWS SDK 명시 의존성을 제거했다. 공통 HTTP 요청 모델도 AWS 타입 대신 중립 immutable 타입을 사용한다. body/decoder/응답/예외/재시도는 유지했다.
- 구 authentication 설정은 값과 ON/OFF에 관계없이 존재 자체가 기동 오류다. 배포 설정에서 삭제해야 한다. 발신 HTTP와 수신 receiver 스위치는 별도로 유지한다.
- Identity 네 이벤트의 Lattice permitAll 및 TEST 역할 통과를 제거했다. receiver OFF는 모든 포트에서404, ON은 전용 포트의 JWT만 허용한다. LC 인증은 그대로다. Identity의 기존 OFF 차단에 결함이 있었다는 주장이 아니라 Billing 수신의 보완이다.
- Identity 공유16개 원문 token을 실제 검증기로 독립 실행했고 모두 통과했다. 양측 파일 SHA256은 0d38a6fa289bb244c0aff0c91124de5f920fcd9c9c308536d3d754ece446f695로 같다. 기존 Billing13개 유지.
- 최종 전체 clean test: **917성공/실패0/오류0/skip0**. 초기 Mongo index초기화 단일 실패는 대상·최종전체 재실행에서 미재현이며 원인을 확정하지 않았다. 문서/diff 검사 통과.
- 기본OFF이며 실제 SigV4 사용 여부·양측 JWT 배포 준비/ALB/TLS/키 교체는 별도 확인 필요다. 실제 전환·판매·자동삭제 승인이나 TMI-204 전체 완료가 아니다. Jira쓰기/Identity 수정/commit/push/배포는 하지 않았다.
