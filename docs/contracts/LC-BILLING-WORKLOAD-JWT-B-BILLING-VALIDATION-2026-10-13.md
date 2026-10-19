# LC–Billing workload JWT — Billing B 구현·검증 결과

- 날짜: 2026-10-13 / 관련 LC Jira: TMI-116·TMI-128·TMI-122
- 승인: 사용자 「어 해줘」, Billing 수신 구현·관련 로컬 테스트. LC/Identity 코드·C 도구·배포/활성화 제외.
- 상태: Billing B 로컬 구현·회귀 완료, 기본 OFF. 실제 양측 TLS/ALB·배포키·E2E·운영 활성화 완료는 아니다.
- 기준: [A 명세](LC-BILLING-WORKLOAD-AUTH-R1.md), [B 계획](../plans/PLAN-LC-BILLING-WORKLOAD-JWT.md), fixtures-1 동결 bytes/hash 유지.

## 1. 구현 결과

- WorkloadJwtProperties에 LC 독립 receiver flag/public JWK를 추가했다. 기존 receiver-enabled는 Identity 의미 그대로다. Identity OR LC ON이면 공유 TLS connector를 구성하며 둘 다 OFF면 구성하지 않는다. 켜진 방향의 키만 파싱/검증한다.
- 고정 local issuer→verifier registry를 추가했다. 미검증 issuer는 선택 힌트일 뿐이다. 선택 서비스 key의 서명·issuer·시간 검증 뒤에만 principal을 만들고 same kid 타서비스 key 탐색/원격 refresh/fallback을 제공하지 않는다.
- actual workload chain에서 exact 서비스 sub·목적 audience·POST/path를 인가한다. Reservation 다섯 route와 AttemptGroup event 한 route의 body/성공 DTO/업무 오류는 유지한다. 서비스 sub는 사용자 ID가 아니며 업무 userId는 기존 authenticated LC body 계약을 따른다.
- 여섯 route는 LC OFF·관련 업무 OFF·다른 local port의 살아 있는 ingress에서 body 없는404/no-store로 차단한다. Authorization/query/JWT 형식/신뢰/시간 실패401, 검증된 principal의 목적/route 실패403이다. 둘 다 OFF의 connector 미기동을 HTTP404로 합성하지 않는다.
- SecurityConfig의 여섯 LC route IAM permitAll과 legacy TEST 역할 통과를 제거했다. 구 internal-ingress mode enum/검증은 최소 변경으로 남겼으나 여섯 route를 열어주지 않는다. health/public/user 및 Identity 기존 인증은 유지한다.
- 실제 사용자·무료권/owner·Reservation TTL/terminal·원장·멱등성·retry/status-first·업무 decoder/schema는 변경하지 않았다. 새 운영 의존성은 추가하지 않았다.

## 2. 설정과 운영 영향

| env | 기본값/의미 |
| --- | --- |
| BILLING_WORKLOAD_JWT_RECEIVER_ENABLED | false, 기존 Identity 수신 |
| BILLING_WORKLOAD_LEARNING_CORE_RECEIVER_ENABLED | false, 신규 LC 수신 |
| BILLING_WORKLOAD_LEARNING_CORE_PUBLIC_JWK_SET | 빈 값, LC 배포 public JWK set |
| BILLING_WORKLOAD_JWT_ENVIRONMENT | 기존 빈 값, test/staging/production |
| BILLING_WORKLOAD_JWT_PORT | 기존0, ON이면 격리된 TLS local port 필수 |
| BILLING_WORKLOAD_TLS_KEY_STORE / PASSWORD | 기존 빈 값, ON이면 PKCS12 TLS 구성 필수 |

LC ON이면 LC 공개키·환경·TLS/port와 기존 transaction/schema/scope 조건을 요구한다. Identity ON 조건도 유지한다. LC 서명 private key를 Billing에 넣지 않는다. OFF 방향의 손상 공개키는 파싱하지 않으며 ON 방향의 손상/누락 key는 실제 security context 기동 실패다.

이 버전의 LC OFF는 기존 Lattice/TEST 경로도 차단한다. legacy sender가 활성인 환경에 무조건 배포하면 계약 오류/DEAD_LETTER가 발생할 수 있으므로 실제 origin/키/미완료 saga/outbox inventory와 양측 전환 계획을 먼저 확인해야 한다. 코드의 기본 OFF는 운영 준비 완료 증거가 아니다. 키/버전 롤백으로 SigV4/무인증 fallback을 열지 않는다.

## 3. 실행 증거와 검증 경계

| 실행/검증 | 결과 |
| --- | --- |
| 초기 대상 테스트 | 기존 두 테스트의 LC OFF404/unsigned401 기대를 새 계약으로 수정 후 통과 |
| HTTP27 production guard/auth filter 실행 | 27개 통과, 거절 시 downstream 미호출·빈 body/no-store |
| HTTP27 실제 Spring Security chain | 27개 통과, 매 case의 ON/OFF·key 설정으로 새 context 구성 |
| IAM/TEST 우회·missing ON key actual context | 2개 추가 통과, legacy role/SigV4 header/forwardedport·모든 여섯 path/세 port 차단 |
| ./gradlew clean test bootJar | **1,127개 통과, failures/errors/skipped 0**, 실행 JAR 빌드 성공 |
| 최종 ./gradlew test bootJar | **1,127개 재확인, failures/errors/skipped 0**, 최종 JAR 빌드 성공 |
| git diff --check·frozen SHA256 | 통과, fixtures-1 재생성 없음 |

실제 chain test의 endpoint는 downstream 도달 여부를 관측하는 test-only probe다. 업무 처리를 모형으로 만들어 보안 통과를 주장하지 않는다. 기존 ReservationController 11개·AttemptGroupEventController 5개 회귀도 production JWT chain/서명 토큰으로 전환해 실제 controller와 DTO·업무 오류·decoder/service 경계를 검증했다. 앱 서비스는 mock이고 기존 Mongo/domain 회귀는 전체 test에 포함된다.

HTTP27의 정상 입력은 인증 통과 경계를 검증하며 fixture 자체에 완전한 업무 body가 있는 것은 아니다. NO_HTTP_RESPONSE case는 connector 부재를 확인한 것이며 실제 연결 거절/ALB 응답을 재현한 증거가 아니다. TLS config test도 실제 인증서/ALB handshake가 아니라 connector 구성 검증이다. 추가 key/flag/businessOFF/단일 path segment·same kid 다른 key/OFF 미파싱 테스트를 포함한다.

기존 Identity JWT·사용자 public 인증·무료권/owner·Reservation·AttemptGroup·recovery와 작업 중 별도 payment cleanup 변경을 포함한 전체 worktree 회귀가 통과했다. 새 LC 수신 변경의 테스트 수를 기존 LC859/Mongo146 또는 Billing 과거1004 결과와 합산하지 않는다. 테스트/문서에 실제 key/token/개인정보를 추가하지 않았다.

## 4. 다음 공동 gate

LC B sender 결과와 Billing HTTP27 결과를 양측 수락 기록으로 대조한다. 실제 배포 LC signer public kid/n/e와 Billing LC public JWK 일치·회전/회수 SLA·TLS/DNS/ALB/SG·direct 경계·미완료 inventory와 실환경 E2E는 남아 있다. C 승인 DEAD_LETTER 도구·운영 복구/배포/활성화도 별도다.

Billing 수신 준비 확인 후 LC 발신을 활성화한다. Reservation 상태 불명은 status-first·원 operation/body/Session 증거를 유지하며 AttemptGroup은 원 eventId/payload/digest·실패 이력 보존 후 승인 복구를 따른다. 이번 로컬 구현 완료는 production flag ON 승인이나 배포 실행이 아니다. commit/push/Jira 쓰기/외부 회신 전송/다른 저장소 수정은 하지 않았다.
