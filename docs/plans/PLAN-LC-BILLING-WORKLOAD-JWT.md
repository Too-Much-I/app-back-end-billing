# LC–Billing workload JWT — B 구현 계획

- 작성일: 2026-10-13
- 상태: 사용자 「어 해줘」로 승인된 Billing B 로컬 수신 구현·회귀 완료. [검증 결과](../contracts/LC-BILLING-WORKLOAD-JWT-B-BILLING-VALIDATION-2026-10-13.md): clean test/bootJar 1,127개 통과. 기본 OFF·C/실환경/배포·활성화는 별도다. 아래 PR 단위는 승인 당시 계획과 담당 경계다.
- 2026-10-13 후속 확인: LC commit `3d50195`의 두 JWT sender·startup/index 구현과 B 검증 회신을 읽기 확인했다. LC 단위859·Mongo146 실패/오류/skip0 XML 및 fixtures-1 동일 bytes 확인. LC 상세 표/계획·ADR/AGENTS 동기화와 LC B 구현은 진행됐으며 이후 Billing verifier/chain·HTTP27 로컬 구현·검증도 완료했다. 실제 양측 TLS 연결·배포 gate는 남아 있다. 상대 결과를 Billing 테스트로 대체하지 않는다.
- 관련 LC Jira: TMI-116 / TMI-128 / TMI-122. 이번 Jira 변경 없음.
- 기준: [A 명세](../contracts/LC-BILLING-WORKLOAD-AUTH-R1.md), [수락 기록](../contracts/LC-BILLING-WORKLOAD-A-ACCEPTANCE-R1.md), [startup 조합표](../contracts/LC-BILLING-WORKLOAD-SENDER-MAPPING-R1.md), [ADR-002 §20](../adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md#20-lcbilling-workload-jwt-전환--2026-10-13-문서-동기화).

## 1. 결과와 변경 경계

LC→Billing 여섯 POST의 인증을 전용 RS256 workload JWT로 전환한다. Billing은 기존 Identity workload TLS connector/코덱을 재사용하되 신뢰·principal·목적인가·수신 flag를 독립 구성한다. LC는 두 Billing client의 SigV4 signer를 JWT issuer로 교체한다. 실패 시 SigV4/무인증 fallback과 기존 IAM permitAll 우회를 제거한다.

대상: reserve, confirm, cancel, status, phone continuation, AttemptGroup event의 A 명세 exact method/path. Identity 기존 네 이벤트·Billing→Identity 네 recovery POST·사용자 JWT는 회귀 대상이며 인증 규격을 변경하지 않는다. DTO/204/업무 오류·Idempotency-Key·eventId/digest·무료권/원장/owner/TTL/Session/terminal·trace/retry 계약을 유지한다. 새 Mongo schema·결제 기능·공개 repair API는 추가하지 않는다.

Billing 저장소만 수정하는 것이 기본 범위다. LC 작업은 담당 분담으로 기술하며 LC 코드/AGENTS/설정·Jira를 이번 작업에서 수정하지 않는다. 기존 별도 TMI-205/209 작업은 보존한다.

## 2. 선행 상태

- 완료: A 규칙·fixtures-1 hash·신규 LC sender 세 명칭의 최종 수락, 양측 token72/hash1 독립 검증. 기존 Billing1004·LC713은 당시 전체 테스트 결과다.
- 완료: 사용자 승인 startup 조합표의 Billing 문서 반영. W OFF/P ON은 LC source의 MongoTemplate lease/CAS 경로를 확인해 drain 허용 기준으로 정했다.
- LC 완료 보고/확인: 상세 조합표 수락·계획/ADR/AGENTS 동기화 및 JWT sender·index gate 구현. LC context/Mongo drain 증거는 상대 검증 회신을 따르며 전체 실환경 기동 성공과 구분한다. Billing 실제 verifier/chain·HTTP27 로컬 구현·검증 완료, 남음: 양측 결과 수락·실환경 연결 gate.
- 이번 완료: Billing ADR/AGENTS의 인증 목표 동기화. 이후 사용자 승인으로 Billing B 수신 코드와 로컬 테스트를 완료했다. 실제 배포/활성화는 별도다.

## 3. PR 단위와 담당

### PR0 — 계약·구현 준비

A exact/hash를 유지하고 본 계획·ADR/AGENTS를 동기화한다. LC 상세 startup 표·sender 준비 gate/기존 기능 flags·S3 AWS provider 보존을 확인한다. 실제 활성 legacy sender와 미완료 saga/outbox가 있으면 OFF 기본값만 보고 배포 안전을 추정하지 않는다.

### PR1 — Billing verifier·인가·connector

- WorkloadJwtProperties에 LC 전용 `learning-core-receiver-enabled=false`, `learning-core-public-jwk-set` 빈 값 추가. 기존 `receiver-enabled`는 Identity 의미 그대로 유지한다. env 후보: BILLING_WORKLOAD_LEARNING_CORE_RECEIVER_ENABLED / BILLING_WORKLOAD_LEARNING_CORE_PUBLIC_JWK_SET. 실제 Secret 값은 추가하지 않는다.
- WorkloadSecurityConfiguration은 Identity OR LC 수신 ON에서만 TLS connector를 기동한다. 둘 다 OFF면 미기동. 공통 port/TLS/env·transaction schema 조건을 유지하고 ON 서비스의 키만 파싱/필수 검증한다. OFF 서비스 설정 오류로 ON 서비스를 중단하지 않는다.
- fixed local issuer→verifier registry를 구성한다. 미검증 iss는 선택 힌트뿐이며 선택 verifier의 서명·issuer·시간 검증 후 principal을 만든다. 같은 kid의 다른 서비스 키를 탐색하거나 fallback하지 않는다.
- WorkloadAuthenticationFilter/chain은 Identity·LC principal을 분리하고 exact sub/aud/method/path로 인가한다. reservationId는 단일 path segment. encoded/trailing/중복 slash·forwarded port로 우회하지 않는다. public/user JWT나 임의 userId header는 workload 권한을 얻지 못한다.
- WorkloadIngressFilter는 LC 여섯 route를 모든 살아 있는 ingress에서 보호한다. LC OFF/관련 업무 OFF/다른 local port는 body 없는404/no-store를 token/query/body보다 먼저 반환한다. connector 미기동을404로 합성하지 않는다.
- SecurityConfig의 여섯 LC route용 LATTICE_AWS_IAM permitAll과 production 경로의 legacy TEST role 통과를 제거한다. 새 JWT port 외 public/기존 internal/direct 경로는 통과하지 못한다. local 테스트는 명시적인 harness/controller 검증으로 수행하고 배포 환경의 인증 우회로 남기지 않는다.
- InternalIngressProperties의 구 mode를 삭제/재해석할지는 여섯 route 외 남은 사용처를 확인해 최소 변경한다. health/public/user/미승인 다른 route를 함께 개방·삭제하지 않는다. enum이 남아도 여섯 route에는 IAM 통과를 허용하지 않는다.

### PR2 — LC 두 client의 JWT 발신(LC 담당)

- A 수락한 sender-enabled/environment/private-jwk 설정과 startup 조합표를 구현한다. 초기 전부 OFF/빈 키. sender flag는 인증 방식 선택이 아니다.
- Reservation client와 AttemptGroup event client는 HTTP 시도 직전 새 JWT/jti를 발급한다. 원 operation/event/body/digest·traceparent·timeout PT2S/PT5S·redirect 금지·기존 retry/circuit/error 분류를 유지한다.
- 두 Billing client의 SigV4 signer/region 인증 의존성을 제거한다. S3 등 다른 AWS 사용처의 provider/SDK는 보존한다. JWT 준비 OFF+S/P ON, H/R ON+S OFF, 잘못된 키는 기동 거절한다.
- W OFF/P ON은 기존 outbox 처리만 허용한다. writer Transaction을 필수로 만들지 않으며 publisher 단독 ON에서 outbox 필수 index 검증을 포함한다. 승인 없이 DEAD_LETTER를 requeue하지 않는다.
- 로컬 signer self-check와 Billing 배포 공개키의 kid/n/e 일치 검증을 구분한다. 원격 fetch 없이 실제 배포 manifest·서명 smoke로 활성화 전 일치를 확인한다.

### PR3 — 실제 chain/sender·기존 업무 회귀

- 기존 fixtures-1 bytes와 SHA256SUMS를 유지한다. shared token72/hash1, HTTP27을 실제 Billing chain과 LC sender로 검증한다. 새 startup fixture는 별도 파일/revision으로 추가한다.
- Billing 수신 네 flag 조합, enabled 방향 key 손상/누락/중복kid/private field/RSA 크기·OFF 미파싱·TLS 기동을 확인한다. port 미기동은 connector 부재/전송 오류로 검증한다.
- LC startup 64 flag 조합의 기대는 조건 정상 시22허용/42거절이다. 키 부정·기존 DB/업무 조건은 추가 검증한다. W OFF/P ON bean/drain/index/lease 경쟁·새event 미생성을 확인한다.
- 교차 ON/OFF401/403/404와 변조 서명·same kid 다른 키·query/Bearer·method/path·IAM/direct/public 우회 실패·인증 오류 DB/service 미호출을 검증한다.
- fresh JWT/jti·원 업무 멱등성·응답 유실 status-first·CONFIRMED/CANCELED/EXPIRED 불변·Identity/user·무료/owner 회귀를 검증한다. 로그/trace/report에 실제 token/key/body를 노출하지 않는다.
- 양측 `./gradlew clean test`와 필요한 replica-set Mongo integration을 각 담당이 실행한다. 실TLS/ALB 증거와 mock 테스트를 구분한다. 통과해도 기본 OFF를 유지한다.

## 4. 완료 기준·운영 영향

B 완료는 해당 저장소 구현·실제 chain/sender/negative/회귀 테스트와 상대 연결 증거로 판단한다. 기존 A 테스트를 B 완료로 재사용하지 않는다. OFF 차단 버전은 legacy sender를 중단시킬 수 있으므로 실제 사용처·키·origin/port·미완료 inventory 확인 없이 배포하지 않는다. rollback은 키·버전·진행 중 증거를 보존하는 승인 절차이며 SigV4/무인증 자동 fallback을 다시 여는 방식이 아니다.

C의 승인 DEAD_LETTER 도구·audit/CAS/lease/fence·복구 E2E, 실제 TLS/DNS/ALB/SG·키 담당/회수 SLA·배포·활성화는 별도다. Billing 준비 후 LC 발신 활성화 순서를 유지한다. 문서 동기화 이후 승인된 Billing B 로컬 구현은 완료했으며 실제 배포/활성화는 수행하지 않았다.
