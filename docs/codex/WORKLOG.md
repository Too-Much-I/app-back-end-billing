# Billing Service Codex 작업 기록

과거 항목은 수정하거나 삭제하지 않고 새 작업을 파일 끝에 추가한다. Secret, Token, 결제 원문과 개인정보는 기록하지 않는다.

## 2026-08-24 — Billing 저장소 기본 설정

<!-- codex-turn:01a03157-bac8-7001-8ec1-ecb08ccbd692 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: Billing Initializr 프로젝트를 기존 앱 서비스 기준에 맞추고 에이전트 작업 구조와 원격 저장소 연결을 준비한다.
- 변경 파일: `build.gradle`, `settings.gradle`, `application.yml`, `SecurityConfig.java`, 테스트 설정, `.env.example`, `AGENTS.md`, `.codex/hooks/*`, `docs/codex/*`
- 구현 내용: Spring Boot 3.4.2/Java 21 기준 의존성, Mongo 환경변수 설정, health-only 공개 보안 기본값, 문서·작업기록 구조를 추가했다.
- 유지한 계약: 결제 도메인·외부 API·JWT/workload 계약은 새로 구현하거나 임의 확정하지 않았고, Learning Core와 Identity 코드는 복사하지 않았다.
- 결정사항: 애플리케이션 이름 `app-back-end-billing`, 기본 포트 `8082`, 인증 미구현 endpoint fail-closed를 초기값으로 사용한다.
- 테스트: `./gradlew clean test` 성공, `git diff --check` 성공. 보안 기본 차단 테스트를 포함한다.
- 위험 요소: 실제 JWT/workload 인증, Mongo transaction, 스토어 검증, 도메인 멱등성은 아직 구현되지 않았다.
- 다음 작업: API 및 서비스 간 인증 계약 확정 후 도메인 모델과 idempotent reservation부터 구현한다.

## 2026-08-24 — Billing 계약 선택지와 기록 위치 확정

<!-- codex-turn:01a03169-0a24-7150-bc76-e049d9a61cda -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: Billing 구현 전에 확정할 계약을 선택지·장단점·권장안으로 정리하고 Billing 문서를 단일 기록 위치로 정한다.
- 변경 파일: `AGENTS.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: 기존 확정사항을 Billing 문서로 이관하고 API 경계, 사용자/workload 인증, 멱등성, 오류, 사용권 우선순위, Reservation, AttemptGroup, store 상품, 만료·환불, 보상·TrialClaim의 C1~C13 선택지를 기록했다.
- 유지한 계약: 기존에 확정된 상품, 10-credit 비용, phone당 무료 1회, immutable ledger, 5분 RESERVED TTL, reserve→Session commit→confirm, R3 무료 replacement, Apple/Google 전용 결제 채널은 변경하지 않았다.
- 결정사항: 앞으로 Billing 관련 결정과 Codex 작업기록은 Billing 저장소 `docs`에만 추가한다. 새 선택지는 아직 사용자 승인 전이며 권장안으로만 표시했다.
- 테스트: 문서·규칙 변경만 수행해 Gradle 테스트는 실행하지 않는다. `git diff --check`, trailing whitespace, marker 단일 포함을 검증한다.
- 위험 요소: C1~C13이 승인되지 않은 상태에서 구현하면 API·Security·Entity를 재설계할 가능성이 있다. 법무·스토어 정책이 필요한 만료·환불·보존기간은 기술 결정만으로 확정할 수 없다.
- 다음 작업: 1차 권장 패키지 C1-A~C8-A부터 사용자와 순서대로 확정한다.

## 2026-08-24 — 결제 구현 연기와 최소 Free Trial 범위 확정 기록

<!-- codex-turn:01a032ed-fff8-7351-863d-a5737a2aa780 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 결제 기능 연기 결정을 Billing 문서에 반영하고 현재 우선할 무료시험 Entitlement 범위를 분리한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: Store·credit·pass·coupon·환불 구현을 deferred로 표시하고 기존 계약은 삭제하지 않았다. Billing은 TrialClaim·FREE_EXAM_ONCE·reserve/confirm/cancel·reconciliation만 최소 Entitlement로 먼저 구현하는 기존 결정을 현재 범위에 반영했다.
- 유지한 계약: raw phone 비저장, verified-phone candidate당 TrialClaim unique, reserve→Session commit→confirm과 5분 RESERVED TTL 원칙을 유지한다. 결제 코드나 API는 추가하지 않았다.
- 테스트: 문서만 변경해 Gradle 테스트를 실행하지 않았다. 종료 전 `git diff --check`, trailing whitespace와 marker 단일 포함을 검증한다.
- 결정사항: 결제 구현은 후속이며 최소 Entitlement는 현재 우선 범위다. Learning Core·Identity에 임시 TrialClaim을 추가하지 않는다.
- 위험 요소: 최소 consumer 배포 전에 Identity eligibility publisher나 무료시험을 활성화하면 phone당 1회와 실패 복구를 보장할 수 없다.
- 다음 작업: 무료시험 consumer의 API·event·workload 인증·멱등성 계약을 별도 Jira로 확정한다.

## 2026-08-25 — Billing 구현 시작 범위와 우선순위 분석

<!-- codex-turn:01a037c1-29c9-7e50-9a12-d6a84b186961 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: Billing 초기 프로젝트, Identity producer와 Learning Core 시험 생성 흐름을 대조해 현재 구현해야 할 최소 범위와 선후관계를 정리한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: 애플리케이션 코드는 변경하지 않았다. 1차 구현을 Identity `PhoneEligibilityBinding` consumer, verified-phone 무료 1회 `TrialClaim`·entitlement ledger, 멱등 `Reservation` reserve/confirm/cancel/status, 만료·reconciliation, 사용자/workload 인증과 관측성 순으로 정리했다. Apple/Google 결제, paid credit, unlimited pass, coupon과 환불은 기존 결정대로 후속 범위로 유지했다.
- 실행한 테스트와 결과: 코드 변경이 없는 분석 작업이므로 Gradle 테스트는 실행하지 않았다. 문서 diff와 marker 단일 포함을 종료 전에 검증한다.
- 유지한 계약: 시험 1회 10 credits, `RESERVED` TTL 5분, `reserve → Learning Core Session commit → confirm`, raw phone 비저장, client `userId` 비신뢰, immutable ledger와 provider/command 멱등성 원칙을 변경하지 않았다. Identity와 Learning Core 코드를 Billing으로 복사하지 않았다.
- 결정사항: Identity의 phone eligibility producer는 구현돼 있지만 Billing consumer와 staging E2E는 없고, Learning Core는 아직 Billing 없이 즉시 `ExamSession`을 생성한다. 따라서 Store 결제보다 최소 무료 Entitlement vertical slice가 선행해야 한다. C1~C8 권장안은 여전히 사용자 승인 전이므로 외부 API와 보안 계약을 코드로 고정하지 않는다.
- 위험 요소: workload issuer·audience·principal·scope와 Billing 사용자 token audience, exact API schema·오류 mapping, Mongo transaction/unique index, TrialClaim 법적 보존 기간이 미확정이다. 양 서비스 기능을 먼저 활성화하면 eligibility 미도착 우회, 이중 무료 지급, Session만 생성되거나 차감만 남는 불일치가 생길 수 있다. 현재 Billing 프로젝트 파일 전체가 Git 미추적 상태라 기능 구현 전 기준선 구분도 필요하다.
- 다음 작업: 사용자가 현재 프로젝트 골격을 초기 기준선으로 commit한 뒤, C1~C8 중 최소 vertical slice에 필요한 계약을 승인하고 별도 Jira로 분리한다. 이후 phone eligibility consumer와 Mongo transaction/index부터 구현한다.

## 2026-08-25 — 결제 제외 전화번호당 무료 모의고사 1회 작업 정리

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88 -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: Apple/Google 결제와 유료 entitlement를 제외하고 verified-phone당 무료 모의고사 1회를 안전하게 출시하기 위한 구현 작업, 선후관계와 완료 조건을 정리한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 동작: 코드는 변경하지 않았다. 첫 reserve transaction에서 current phone binding 확인, unique `TrialClaim`, 무료 grant/ledger와 Reservation을 함께 생성하고, Learning Core가 `reserve → Session commit → confirm`을 수행하도록 하는 최소 vertical slice를 정의했다. cancel/expiry, same-key retry, confirm 불명, owner 이전과 reconciliation까지 출시 범위에 포함했다.
- 테스트 결과: 분석·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 diff와 whitespace를 종료 전에 검증한다.
- 유지한 계약: raw phone 비저장, verified-phone candidate당 평생 1회, 계정 탈퇴·재가입·merge로 claim 재개방 금지, `RESERVED` 5분, confirm/cancel 멱등성, immutable ledger, client `userId` 비신뢰와 fail-closed 원칙을 유지했다.
- 결정사항: 결제, paid credit, unlimited pass, coupon, 추천·출석과 환불은 후속 범위로 유지한다. Billing 사용자 조회 API가 없으면 C1/C2는 결제 출시까지 미룰 수 있다는 구현 분리안을 제안했으며 아직 승인된 계약으로 변경하지 않았다. C3/C4/C5/C7/C8/C13은 최소 출시에 선행해야 한다.
- 위험 요소: workload JWT 상세, event wire schema·transport, API DTO·오류 code, AttemptGroup 완료 증거, TrialClaim 법적 보존 기간과 번호 재할당 정책은 아직 확정되지 않았다. Mongo TTL로 Reservation 문서를 삭제하면 audit와 확정 consumption을 잃을 수 있으므로 business expiry와 기록 보존을 분리해야 한다.
- 다음 작업: 미확정 최소 계약을 승인하고 Identity consumer, TrialClaim/ledger transaction, Reservation API를 각각 작은 Jira vertical slice로 나눈 뒤 구현한다.

## 2026-08-25 — 무료 Entitlement 계약 선택 안내

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-contract-review -->

- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 결제 제외 무료 모의고사 출시에 필요한 계약만 선별하고 각 선택지의 의미, 장단점과 권장안을 사용자 결정용으로 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 동작: 코드는 변경하지 않았다. C3/C4/C5/C7/C8/C13을 출시 차단 결정으로, C1/C2를 Billing 사용자 API 제공 여부에 따른 보류 가능 결정으로, C6을 무료권 자동 선택으로 분류했다. C9~C12는 이번 결정 범위에서 제외했다.
- 테스트 결과: 분석·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 기록 marker를 종료 전에 검증한다.
- 유지한 계약: Billing 도메인 소유, raw phone 비저장, candidate당 1회, `reserve → Session commit → confirm`, 5분 RESERVED, immutable ledger, 멱등성과 fail-closed 원칙을 변경하지 않았다.
- 결정사항: 이 작업에서는 어떤 선택지도 확정하지 않았다. C1/C2 보류와 C3-A/C4-A/C5-A/C7-A/C8-A/C13-A를 묶은 권장안을 사용자에게 제시한다.
- 위험 요소: C13은 법무가 승인할 보존기간 없이는 A안을 완전히 고정할 수 없다. C3-A는 Identity workload token 발급 역량 확인이 필요하고, C8-A는 Learning Core의 결과 완료 이벤트와 reconciliation 계약이 필요하다.
- 다음 작업: 사용자가 항목별 선택을 답하면 `CONTRACT_DECISIONS.md`에 확정 상태와 세부 상수를 기록하고 구현 backlog를 갱신한다.

## 2026-08-26 — workload JWT와 무료 모의고사 차감 시점 설명

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-workload-consumption -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: C3-A workload JWT의 역할과 현재 Identity 구현 가능성을 확인하고 무료 모의고사 사용권이 reserve, confirm, 완료 중 언제 잠기고 최종 소비되는지 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션과 계약 결정서는 변경하지 않았다.
- 구현 내용: Identity 저장소를 읽기 전용으로 대조해 사용자 Access Token issuer는 있지만 Learning Core용 workload issuer 구현은 없고, phone eligibility event ADR은 배포 플랫폼 발급 5분 이하 service identity JWT를 채택한 상태임을 확인했다. 무료권은 reserve에서 잠그고 durable Session commit 뒤 confirm에서 최종 소비하며 AttemptGroup 완료는 restart 권리 종료 시점으로 구분했다.
- 실행한 테스트와 결과: 분석·문서 작업만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: raw phone 비저장, candidate당 unique TrialClaim, `reserve → Session commit → confirm`, RESERVED 5분, CONFIRMED cancel 금지, 멱등성과 reconciliation 원칙을 변경하지 않았다.
- 결정사항: C3-A의 발급 주체와 C8-A는 아직 사용자 확정 전이다. 기존 `Identity가 발급` 문구를 자동 확정하지 않고 플랫폼 workload identity와 Identity client-credentials 두 선택지로 구분해 설명한다.
- 위험 요소: Identity 자체 발급을 선택하면 별도 client 등록·인증·token endpoint·rotation이 필요하다. 플랫폼 발급을 선택하면 실제 배포 환경의 issuer, JWKS, audience, subject와 로컬·staging credential 공급 방식을 고정해야 한다.
- 다음 작업: 사용자가 workload JWT 발급 주체와 confirm 최종 소비 정책을 선택하면 `CONTRACT_DECISIONS.md`에 확정 계약으로 기록한다.

## 2026-08-26 — 무료 최소 Entitlement 권장 계약 전체 승인 반영

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-free-contract-approved -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 사용자가 승인한 결제 제외 무료 모의고사 권장 계약 전체를 Billing 계약 단일 기준과 현재 상태에 확정 반영한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드는 변경하지 않았다.
- 구현 내용: 무료 릴리스의 C1/C2 보류, Identity eligibility event inbox/high-water, 첫 reserve TrialClaim 생성, 플랫폼 발급 5분 이하 workload JWT, 필수 UUID idempotency key, 행동별 오류, 서버 무료권 자동 선택, 사용자당 단일 OPEN group/session/command, confirm 최종 소비, 결과 조회 가능 시 완료, 법무 승인 기간 Claim 보존과 번호 재할당 시 기존 Claim 유지를 확정했다.
- 실행한 테스트와 결과: 문서·계약 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace, 선택 상태와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: raw phone 비저장, verified-phone candidate당 1회, immutable ledger, `reserve → Session commit → confirm`, RESERVED 5분, CONFIRMED cancel 금지, restart의 동일 consumption·mockExamId, fail-closed와 멱등성 원칙을 유지했다.
- 결정사항: 앱 직접 Billing API와 사용자 Billing audience, 결제 C9~C11, 보상 C12는 후속이다. workload logical audience/principal/TTL과 권한은 확정했고 환경별 issuer·JWKS·실제 platform subject·clock skew는 배포 설정으로 남겼다. C13 정책은 확정했지만 구체적인 법무 보존기간은 production gate다.
- 위험 요소: 플랫폼이 custom scope claim을 제공하지 않으면 검증된 principal과 Billing permission allowlist를 안전하게 매핑해야 한다. exact wire DTO, Mongo partial unique index와 양방향 reconciliation 구현·검증이 남아 있다.
- 다음 작업: 승인 계약을 기준으로 workload 보안과 phone eligibility consumer부터 vertical slice를 구현하고 replica-set Mongo 동시성·멱등성 테스트를 추가한다.

## 2026-08-26 — 승인 후 남은 환경·법무·API·Mongo 작업 설명

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-post-approval-tasks -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 무료 최소 계약 승인 뒤 남은 workload trust 값, scope fallback, TrialClaim 보존기간, API DTO와 Mongo index가 각각 어떤 결정·구현 작업인지 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션과 확정 계약은 변경하지 않았다.
- 구현 내용: workload 항목은 배포 플랫폼 토큰 metadata·principal을 환경별 trust profile과 Billing 권한 allowlist로 고정하는 인프라/보안 작업, TrialClaim 기간은 법무·개인정보 production gate, DTO/index는 Billing에서 즉시 설계 가능한 구현 작업으로 분류했다. candidate key rotation 중에도 phone당 unique를 보장하도록 별도 candidate alias unique index가 필요함을 기록했다.
- 실행한 테스트와 결과: 설명·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: 플랫폼 workload JWT, 최소 권한, candidate당 1회, raw phone 비저장, 필수 idempotency key, audit ledger 보존과 Reservation business expiry를 변경하지 않았다.
- 결정사항: 새 계약을 추가 확정하지 않았다. exact environment trust 값과 법무 보존기간은 외부 입력이 필요하고 API DTO·Mongo index ADR은 그 전에 진행할 수 있다.
- 위험 요소: platform token claim을 확인하지 않고 issuer/subject를 추정하면 production 인증이 전부 실패하거나 잘못된 service를 허용할 수 있다. candidate 배열에 단순 unique multikey index만 두면 key rotation·동시성에서 one-phone-one-claim을 명확히 증명하기 어려우므로 alias collection Transaction 설계가 필요하다.
- 다음 작업: 배포 플랫폼과 production service identity를 확인하고 법무 보존기간 검토를 요청하는 동안 internal API/OpenAPI와 Mongo collection/index ADR을 먼저 작성한다.

## 2026-08-26 — AWS ECS 확인과 workload C3 재검토

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-ecs-workload-review -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 실제 배포 환경이 AWS ECS라는 사용자 정보를 기존 플랫폼 workload JWT 계약에 대조하고 구현 가능한 인증 선택지로 수정한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드는 변경하지 않았다.
- 구현 내용: ECS task role은 임시 AWS credential을 제공하지만 OIDC JWT·issuer·JWKS를 자동 제공하지 않음을 반영해 C3-A만 재검토로 전환했다. VPC Lattice/API Gateway 경로의 task role+SigV4+AWS_IAM을 1차 권장으로 추가하고, 내부 ALB/Service Connect 직접 호출이면 별도 JWT issuer·SigV4 adapter·mTLS가 필요함을 구분했다.
- 실행한 테스트와 결과: 인프라 분석·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 저장소에는 실제 ECS ingress 정의가 없음을 검색했고 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: C3 외 eligibility event, candidate당 TrialClaim, reserve/confirm 소비, 멱등성·오류·동시성·완료·보존 계약은 변경하지 않았다. static API key와 네트워크 위치만 신뢰하는 방식은 채택하지 않았다.
- 결정사항: ECS라는 정보만으로 issuer/JWKS 값을 채우지 않는다. ingress 확인 전 기존 플랫폼 JWT 가정을 구현하지 않으며, Lattice/Gateway가 가능하면 SigV4/IAM을 우선 검토한다.
- 위험 요소: internal ALB나 Service Connect 직접 경로에 IAM task role이 있다는 사실만으로 호출자를 인증할 수 없다. SigV4/IAM edge를 채택하면 direct bypass 차단과 route-level IAM policy가 필수다.
- 다음 작업: ECS 서비스 간 실제 경로가 VPC Lattice, API Gateway, internal ALB, Service Connect 중 무엇인지 확인하고 C3를 최종 확정한다.

## 2026-08-26 — 기존 Identity·Learning Core 인증 구현 대조

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-existing-auth-review -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: Identity와 Learning Core가 현재 실제 사용하는 인증·서버 호출 방식을 확인해 Billing workload 인증을 동일한 패턴으로 맞출 수 있는지 판단한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드는 변경하지 않았다.
- 구현 내용: 실제 공통 구현은 Identity RS256 사용자 JWT 발급과 Learning Core의 issuer·JWKS·audience·UUID sub 로컬 검증임을 확인했다. Identity downstream publisher는 workload credential port만 있고 production provider가 없으며 비활성이고, Learning Core AI dispatch는 Authorization 없이 idempotency key만 사용한다. 사용자 JWT가 아닌 workload 전용 token profile로 Identity RS256/JWKS 패턴을 확장하는 C3-E를 기존 구조 일치 권장안으로 구체화했다.
- 실행한 테스트와 결과: 세 저장소 코드·설정을 읽기 전용 대조하고 문서만 변경해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: 사용자 token과 workload token 분리, Billing audience·service subject·최소 scope·5분 TTL, 사용자 API와 internal API 분리, static API key·네트워크 위치만 신뢰 금지를 유지했다.
- 결정사항: 재사용 가능한 운영 server-to-server 인증은 현재 없다. 기존 인증 메커니즘과 맞추려면 Identity workload client-credentials를 새로 구현해야 하며 C3-E는 사용자 최종 승인 전까지 권장안이다.
- 위험 요소: 사용자 Access Token을 Billing으로 전달하면 workload caller를 증명하지 못하고 audience 경계가 무너진다. client secret을 ECS 환경변수 평문이나 저장소에 두지 말고 Secrets Manager와 rotation 절차를 사용해야 한다.
- 다음 작업: 사용자가 C3-E를 승인하면 exact token claims, client 인증·rotation, Billing validator와 Learning Core cache 계약을 확정하고 Identity→Billing event publisher에도 같은 provider를 적용한다.

## 2026-08-26 — C3 Identity JWT·VPC Lattice·API Gateway 비교

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-c3-three-way-comparison -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: ECS 환경에서 Identity-issued workload JWT, VPC Lattice+SigV4, API Gateway+AWS_IAM 세 구조의 장단점과 권장 우선순위를 비교한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션과 C3 확정 상태는 변경하지 않았다.
- 구현 내용: 내부 ECS 호출 기준으로 Lattice를 서비스 identity·network·IAM 통합과 Identity 장애 비전파 때문에 1순위, 기존 Spring RS256/JWKS 재사용과 인프라 변경 최소화를 위한 Identity workload JWT를 2순위, 외부 공개·중앙 gateway 기능이 필요한 경우 API Gateway를 조건부 선택으로 분류했다.
- 실행한 테스트와 결과: 아키텍처 비교와 문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: Learning Core/Identity workload 분리, 최소 권한, static API key 금지, direct bypass 차단, raw phone 비저장과 무료 entitlement 도메인 계약을 변경하지 않았다.
- 결정사항: C3는 아직 확정하지 않았다. 새 service network 구성이 가능하고 Billing이 내부 전용이면 Lattice를 최종 권장하며 existing ALB/Service Connect 유지가 우선이면 Identity workload JWT를 권장한다.
- 위험 요소: 실제 ECS ingress, account/VPC 구성, 예상 호출량과 Lattice/API Gateway 비용을 확인하지 않고 최종 선택하면 불필요한 인프라 또는 과도한 애플리케이션 보안 구현이 생길 수 있다.
- 다음 작업: 현재 ECS가 internal ALB/Service Connect인지와 Lattice/API Gateway 도입 가능 여부를 확인한 뒤 C3를 하나로 확정한다.

## 2026-08-26 — ECS 경로 확인과 VPC Lattice 전환 안내

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-ecs-path-migration-guide -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 현재 ECS 서비스 통신이 internal ALB, Service Connect, Cloud Map 또는 Lattice인지 확인하는 방법과 Billing 내부 호출을 Lattice로 안전하게 전환하는 절차를 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·인프라·확정 계약은 변경하지 않았다.
- 구현 내용: ECS/EC2/VPC Lattice Console 확인 위치와 read-only AWS CLI query를 정리하고, named port·Lattice service/listener/target·VPC association·task role IAM·SigV4 client를 병렬 배포한 뒤 staging negative test, base URL cutover, direct bypass 차단과 rollback 기간 후 old route 제거 순서를 정의했다.
- 실행한 테스트와 결과: 설명·문서 변경만 수행해 Gradle 테스트와 실제 AWS 조회는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: static API key 금지, task role 최소 권한, unsigned/direct bypass fail-closed, 동일 idempotency key 재시도, 기존 사용자 API 경계와 무료 entitlement 계약을 변경하지 않았다.
- 결정사항: 현재 실제 ingress는 저장소에 ECS service/task definition이 없어 확정하지 않았다. 공유 ALB 또는 사용자 API가 있으면 일괄 제거하지 않고 `/internal/**` 경계를 먼저 분리한다.
- 위험 요소: Lattice 검증 전 기존 route를 제거하면 서비스 중단이 생기고, 새 route 전환 후 direct ALB/Service Connect 접근을 남기면 IAM 인증 우회가 가능하다. AWS 리소스 ARN·role·security group은 실제 계정에서 읽어야 하며 문서나 로그에 credential을 남기지 않는다.
- 다음 작업: AWS Console 또는 CLI에서 Learning Core·Identity·Billing 서비스의 loadBalancers/serviceConnectConfiguration/serviceRegistries/vpcLatticeConfigurations와 실제 Billing base URL을 확인한다.

## 2026-08-26 — ECS Load Balancer·Billing 미배포 현황 반영

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-ecs-topology-confirmed -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: Identity·Learning Core는 Load Balancer 사용, Service Connect 없음, Billing 미배포, Lattice 없음이라는 실제 인프라 현황을 C3 선택에 반영한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·AWS 인프라·확정 계약은 변경하지 않았다.
- 구현 내용: 기존 두 서비스 inbound LB는 유지하고 새 Billing만 ALB 없이 Lattice target으로 배포하며 두 서비스 outbound만 Lattice DNS+SigV4로 추가하는 greenfield 구성을 최종 권장으로 정리했다. task role별 reservation/event 권한과 별도 repair role, direct bypass 차단과 향후 public Billing API 분리를 기록했다.
- 실행한 테스트와 결과: 사용자 제공 인프라 상태를 문서화했으며 Gradle 테스트와 AWS 변경은 수행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: 기존 사용자 API와 ALB 경로, workload 최소 권한, static secret 금지, idempotency·fail-closed와 Billing 내부/사용자 API 분리를 유지했다.
- 결정사항: 현재 조건에서 C3-D Lattice+task role+SigV4가 최종 권장이나 사용자 명시 승인 전까지 C3는 재검토 상태다. Identity-issued workload JWT는 Lattice 도입이 불가능할 때의 대안으로 남긴다.
- 위험 요소: Identity와 Learning Core가 동일 task role을 공유하면 주체별 IAM 분리가 불가능하다. Lattice auth 뒤에도 Billing direct SG path가 남거나 애플리케이션이 위조 가능한 identity header만 신뢰하면 우회가 생길 수 있다.
- 다음 작업: 사용자가 C3-D를 승인하면 exact Lattice service/network/listener, ECS infrastructure role, task role IAM/auth policy, SigV4 signer와 Billing ingress security contract를 확정한다.

## 2026-08-26 — C3-D VPC Lattice workload 인증 최종 승인

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-c3d-approved -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 사용자가 명시 승인한 C3-D VPC Lattice + ECS task role + SigV4 + AWS_IAM을 Billing workload 인증 최종 계약으로 반영한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션과 AWS 인프라는 변경하지 않았다.
- 구현 내용: C3-D를 확정하고 platform JWT·Identity workload JWT를 미채택 대안으로 표시했다. 기존 Identity·Learning Core inbound LB 유지, Billing ALB 없는 Lattice greenfield 배포, role별 reservation/event route 권한, 별도 repair role, direct bypass 차단과 staging negative test를 계약에 고정했다.
- 실행한 테스트와 결과: 계약·문서 변경만 수행해 Gradle 테스트와 AWS 변경은 실행하지 않았다. 문서 whitespace, C3-D 확정 상태와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: 무료 TrialClaim, reserve→Session commit→confirm, 필수 idempotency key, fail-closed, 최소 권한, 기존 사용자 API와 Identity 사용자 JWT 계약을 유지했다.
- 결정사항: Billing 내부 API는 Lattice/SigV4를 사용한다. Identity workload token endpoint, JWKS workload profile, Billing용 API Gateway와 Billing ALB는 이번 범위에서 만들지 않는다.
- 위험 요소: Learning Core·Identity task role 분리, exact ARN/auth policy, Lattice source만 허용하는 SG, SigV4 body/header signing과 local/test adapter 구현이 남아 있다.
- 다음 작업: C3-D 인프라·애플리케이션 계약을 별도 ADR/Jira로 구체화하고 Billing API DTO·Mongo index vertical slice 구현을 시작한다.

## 2026-08-26 — C3-D 승인 후 잔여 확정사항 감사

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-remaining-decisions-audit -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 무료 모의고사 MVP 계약 중 C3-D 승인 뒤에도 사용자 확정이 필요한 사항과 구현·운영 명세로 남은 사항을 구분한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션과 확정 계약은 변경하지 않았다.
- 구현 내용: 법무가 정할 TrialClaim 구체적 보존기간을 유일한 잔여 외부 정책 결정으로 분류했다. API DTO·Mongo index·Lattice 실제 ARN/정책은 구현 ADR, reconciliation 주기·경보·repair와 데이터 정리 job은 운영 명세로 분류했으며 결제·보상 계약은 후속 기능 착수 시점으로 유지했다.
- 실행한 테스트와 결과: 분석·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: C3-D Lattice/SigV4, candidate당 무료 1회, raw phone 비저장, reserve→Session commit→confirm, 5분 RESERVED, 멱등성, 단일 OPEN AttemptGroup과 동일 consumption 재응시를 변경하지 않았다.
- 결정사항: 새 제품 계약은 확정하지 않았다. 무료 MVP 구현은 지금 시작할 수 있으며 TrialClaim 보존기간은 production 활성화 전 승인 gate다.
- 위험 요소: 기술 명세를 계약 없이 즉흥 구현하면 서비스 간 DTO와 index migration이 어긋날 수 있고, 법적 기간 없이 retained candidate 삭제 job을 활성화해서는 안 된다.
- 다음 작업: internal API/OpenAPI·Mongo index ADR과 Lattice/SigV4 ADR을 먼저 작성하고, 병행해서 법무·개인정보 담당자에게 C13 기간·기산점·만료 방식을 승인받는다.

## 2026-08-26 — TrialClaim 보존기간 선택지 구체화

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-trial-retention-options -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 별도 법무팀 없이 제품 책임자가 결정할 TrialClaim 보존기간, 기산점, 만료 처리와 재수급 정책의 선택지를 구체화한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션과 확정 계약은 변경하지 않았다.
- 구현 내용: 유한 기간 뒤 candidate 삭제와 phone당 영구 회 제한은 동시에 달성할 수 없음을 명시하고, claimedAt 기준 3년·5년과 무료시험 프로그램 종료 후 1년 보존안을 비교 대상으로 정리했다. 유한 기간 만료 시 연결 식별자는 삭제하고 비연결 통계만 남기는 방식을 권장 처리로 정리했다.
- 실행한 테스트와 결과: 정책 분석·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: raw phone·last4 비저장, benefit-scoped candidate 사용, 계정 탈퇴·merge·revoke로 Claim 즉시 재개방 금지와 번호 재할당 시 기존 Claim 유지 정책은 변경하지 않았다.
- 결정사항: 아직 보존기간을 확정하지 않았다. 사용자 최종 선택 뒤 C13, 제품의 `평생 1회` 표현, purge/anonymization 계약을 함께 갱신해야 한다.
- 위험 요소: 유한 기간을 선택하면서 영구 1회로 계속 안내하면 실제 시스템 동작과 제품 약속이 어긋난다. 영구 보존안을 선택하면 목적 지속성, 사용자 고지, 접근 통제와 정기 검토 부담이 커진다.
- 다음 작업: 사용자가 3년, 5년 또는 프로그램 종료 + 1년 중 하나를 승인하면 계약 단일 기준에 기산점·만료 처리·재수급 여부까지 확정 반영한다.

## 2026-08-26 — TrialClaim `claimedAt + 3년` 보존 승인 반영

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-trial-retention-three-years-approved -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 사용자가 선택한 TrialClaim 보존기간 B안인 `claimedAt + 3년`을 계약 단일 기준과 현재 상태에 확정 반영한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드는 변경하지 않았다.
- 구현 내용: Claim 최초 생성 시 3년의 immutable retention을 계산하고 기간 안에는 기존 Claim을 유지하며, 만료 시 alias를 즉시 dedupe 대상에서 제외해 같은 번호의 새 Claim을 허용하는 계약을 확정했다. candidate/keyVersion과 user/source event 연결은 삭제·비식별화하고 비연결 최소 집계만 허용하도록 정리했다.
- 실행한 테스트와 결과: 계약·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace, 남은 `평생 1회` 표현과 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: raw phone·last4 비저장, reserve→Session commit→confirm, 5분 RESERVED, cancel/expiry로 Claim 재개방 금지, 번호 재할당 시 보존기간 안의 기존 Claim 유지와 ledger 감사 가능성을 유지했다.
- 결정사항: 제품의 무료권 제한은 영구 1회가 아니라 `verified-phone candidate당 3년 내 1회`다. `claimedAt`은 갱신하지 않고 3년 뒤 재수급을 허용한다. 대화의 B안은 기존 C13-B가 아니라 C13-A의 구체 보존기간 선택으로 기록했다.
- 위험 요소: 물리 purge가 늦어도 만료 alias가 재수급을 막아서는 안 된다. ledger에 직접 candidate/user 연결을 박으면 비식별화가 어려우므로 별도 alias/subject mapping을 제거해 immutable audit를 유지하는 설계가 필요하다.
- 다음 작업: API/Mongo ADR에서 `retentionExpiresAt` index, active alias matching, purge/anonymization transaction과 replica-set 동시성 테스트를 구체화하고 운영 ADR에서 물리 purge SLA와 backup 삭제 주기를 정한다.

## 2026-08-26 — TrialClaim 물리 삭제·백업 주기 선택지 정리

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-trial-purge-backup-options -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: `claimedAt + 3년` 보존 계약을 완결하기 위해 active DB 물리 purge SLA, backup 수명과 restore 처리 선택지를 정리한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션과 확정 계약은 변경하지 않았다.
- 구현 내용: logical expiry 즉시 dedupe 제외를 유지하면서 매일 purge·24시간 이내 삭제·35일 rolling backup을 균형 권장안으로 정리했다. restore 전 만료 purge, SLA 초과 경보와 식별자 없는 삭제 증적을 필수 운영 규칙으로 제안했다.
- 실행한 테스트와 결과: 운영 정책 분석·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: `claimedAt + 3년`, 만료 뒤 재수급 허용, raw phone 비저장, 만료 alias의 즉시 dedupe 제외와 immutable audit의 비식별화를 변경하지 않았다.
- 결정사항: 새 상수는 아직 확정하지 않았다. 7일·35일·90일 backup 선택지 중 35일을 권장하며 active DB 삭제는 24시간 이내를 권장한다.
- 위험 요소: 물리 purge가 논리 만료보다 늦어도 재수급을 차단해서는 안 된다. 만료 전 backup을 그대로 production에 복구하면 삭제된 candidate가 다시 살아날 수 있으므로 restore-before-traffic purge가 필수다.
- 다음 작업: 사용자가 purge 24시간 SLA와 backup 35일 권장안을 승인하면 C13 운영 계약에 확정 반영하고, 이어서 API/Mongo와 Lattice/SigV4 ADR을 작성한다.

## 2026-08-26 — TrialClaim purge 대상 설명

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-trial-purge-scope-explained -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 3년 만료 뒤 물리 삭제한다는 데이터가 무엇이며 어떤 시험·원장 데이터는 영향을 받지 않는지 명확히 한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션과 확정 계약은 변경하지 않았다.
- 구현 내용: 삭제 대상을 candidate dedupe alias, Claim의 user/source event/binding 연결과 삭제 가능한 subject mapping으로 한정했다. TrialClaim은 역추적 불가능한 tombstone, ledger는 식별 mapping이 제거된 audit core만 남길 수 있고 Learning Core 시험 데이터와 유효한 current binding은 이 purge 대상이 아님을 구분했다.
- 실행한 테스트와 결과: 데이터 경계 설명·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: `claimedAt + 3년`, 만료 후 같은 번호 재수급, raw phone 비저장, Billing ledger 감사 가능성과 Identity/Learning Core 도메인 경계를 변경하지 않았다.
- 결정사항: 새 삭제 주기나 backup 기간을 확정하지 않았다. 삭제 계약을 지키도록 immutable audit core와 erasable subject/candidate mapping을 Mongo 설계에서 분리한다.
- 위험 요소: Reservation·ledger에 userId 또는 candidate를 직접 영구 보존하면 alias만 삭제해도 Claim을 재식별할 수 있다. 반대로 current verified binding을 Claim과 함께 삭제하면 신규 Claim 자격 확인과 Identity revision 처리가 깨진다.
- 다음 작업: purge 범위를 이해한 뒤 사용자가 24시간 물리 삭제 SLA와 35일 backup을 승인할지 결정하고, API/Mongo ADR에서 분리 collection과 index를 고정한다.

## 2026-08-26 — 24시간 purge·35일 backup 의미 설명

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-trial-purge-backup-explained -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: TrialClaim 물리 삭제와 MongoDB 재해복구 backup이 서로 어떤 관계인지 날짜 예시로 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션과 확정 계약은 변경하지 않았다.
- 구현 내용: candidate를 별도로 backup하는 것이 아니라 전체 MongoDB 운영 backup에 삭제 전 연결정보가 과거 snapshot으로 남을 수 있음을 명확히 했다. 3년 논리 만료, 24시간 내 active DB purge, 최대 35일 backup 자연 만료와 restore-before-traffic 재-purge를 단계별로 정리했다.
- 실행한 테스트와 결과: 설명·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: `claimedAt + 3년`, 만료 즉시 dedupe 제외, raw phone 비저장, candidate/user 연결만 purge하고 Learning Core 시험 데이터는 건드리지 않는 경계를 유지했다.
- 결정사항: 24시간 purge SLA와 35일 backup은 아직 권장안이며 확정하지 않았다.
- 위험 요소: backup을 평상시 앱이 조회하는 보관 DB로 오해하면 삭제 계약이 흐려진다. backup은 접근 제한된 재해복구 사본이고, 복구 시점에 만료 데이터를 다시 제거하지 않으면 삭제된 연결이 부활할 수 있다.
- 다음 작업: 사용자가 설명을 바탕으로 권장안을 승인하면 C13 운영 계약에 수치와 restore 절차를 확정 반영한다.

## 2026-08-26 — TrialClaim purge·backup 권장안 최종 승인

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-trial-purge-backup-approved -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 사용자가 승인한 daily purge, 24시간 물리 삭제 SLA, 35일 rolling backup과 restore-before-traffic purge를 C13 운영 계약으로 확정한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드는 변경하지 않았다.
- 구현 내용: 3년 논리 만료 즉시 dedupe 제외, 24시간 안의 active DB alias/subject mapping 삭제와 비식별 tombstone 전환, SLA 초과 경보·재시도, MongoDB 전체 backup 최대 35일 자동 만료와 격리 restore 후 선행 purge를 계약에 추가했다. 삭제 증적은 식별자 없는 건수·시각·결과로 제한했다.
- 실행한 테스트와 결과: 계약·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace, 승인 상수와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: raw phone 비저장, `claimedAt + 3년`, 만료 후 같은 번호 재수급, Learning Core 시험 데이터 비삭제, 식별 mapping과 immutable audit core 분리를 유지했다.
- 결정사항: daily purge, 24시간 삭제 SLA, 35일 rolling backup, restore-before-traffic purge와 identifier-free deletion evidence가 확정됐다. C13의 외부 정책 상수는 더 이상 남아 있지 않다.
- 위험 요소: backup provider가 35일 자동 만료를 지원하는지 배포 설계에서 확인해야 한다. restore runbook이 purge 검증을 우회하거나 SLA 경보에 candidate/user 식별자를 넣지 않도록 통제해야 한다.
- 다음 작업: C13 상수를 API/Mongo ADR의 `retentionExpiresAt`, active alias index, purge job과 restore runbook에 구체화하고 Lattice/SigV4 ADR을 작성한다.

## 2026-08-26 — 무료 Trial 내부 API·Mongo ADR-001 작성

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-free-trial-api-mongo-adr -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 승인된 무료 모의고사 계약을 구현 가능한 internal API DTO, 오류·멱등성, Mongo collection/index/Transaction과 purge 명세로 구체화한다.
- 변경 파일: `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드는 변경하지 않았다.
- 구현 내용: Identity phone eligibility event, Learning Core reserve/confirm/cancel/status와 AttemptGroup status event endpoint·DTO를 고정했다. `Idempotency-Key=operationId`, 기존 `examId=sessionId`, opaque mockExamId와 stable error envelope를 정의했다. candidate alias·subject link·TrialClaim·grant/ledger·Reservation/allocation·command·AttemptGroup/session projection collection과 필수 unique/partial unique/TTL index, 6개 Transaction 경계와 concurrency test를 문서화했다.
- 실행한 테스트와 결과: 문서·설계 변경만 수행해 Gradle 테스트는 실행하지 않았다. ADR code fence 균형, 금지 문자열, 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: raw phone 비저장, C3-D Lattice/SigV4, 필수 UUID v4 key, `reserve → Session commit → confirm`, 5분 RESERVED, confirm 취소 금지, R3 동일 consumption/mockExamId, `claimedAt + 3년`, 24시간 purge와 35일 backup을 유지했다.
- 결정사항: internal command는 raw JSON DTO와 `/internal/v1`을 사용하고 public `BaseResponse`를 쓰지 않는다. sessionId는 Learning Core의 비-UUID examId를 허용하고 operationId만 UUID v4다. mutable balance가 아닌 ledger를 truth source로 두며 삭제 가능한 identity mapping과 immutable audit core를 분리한다.
- 위험 요소: Trial retention expiry 뒤 subject link를 제거하면 해당 익명 group은 더 이상 replacement authorization에 사용할 수 없다. Mongo partial unique option은 실제 MongoDB 버전에서 replica-set Testcontainers로 검증해야 하며 production auto-index 생성에 의존해서는 안 된다.
- 다음 작업: Lattice service/network/listener/target, 실제 task role ARN, route policy, ingress SG와 SigV4/local-test adapter를 ADR-002로 고정한 뒤 ADR-001 vertical slice 구현을 시작한다.

## 2026-08-26 — ADR-001 확정 내용 사용자 설명

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-adr001-explained -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: ADR-001에서 확정한 API, 식별자, 상태 전이, Mongo 구조와 아직 하지 않은 작업을 사용자 흐름 중심으로 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. ADR과 애플리케이션 코드는 변경하지 않았다.
- 구현 내용: 앱→Learning Core 경계, Identity/Learning Core 내부 endpoint, operation/session/mockExam 식별자, 최초 reserve-confirm 소비와 restart 재사용, 오류·멱등성, candidate/subject/audit 분리와 partial unique index를 설명 대상으로 정리했다.
- 실행한 테스트와 결과: 설명·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: raw phone 비저장, C3-D, `reserve → Session commit → confirm`, 5분 RESERVED, R3와 `claimedAt + 3년` purge 계약을 변경하지 않았다.
- 결정사항: 새 계약을 추가하지 않았다. ADR-001이 제품 계약이 아닌 정확한 구현 기술 계약이라는 점을 명확히 했다.
- 위험 요소: API 명세 확정과 코드 구현 완료를 혼동하면 안 된다. 실제 Lattice principal/route와 Mongo partial unique 동시성은 후속 ADR·Testcontainers에서 검증해야 한다.
- 다음 작업: 사용자 설명 뒤 ADR-002 Lattice/SigV4 인프라 계약을 작성한다.

## 2026-08-26 — Reservation confirm과 Summary 완료 시점 설명

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-confirm-vs-summary-explained -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: Billing confirm이 ExamSession commit 직후인지 Summary 생성 완료 후인지 구분한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. ADR과 애플리케이션 코드는 변경하지 않았다.
- 구현 내용: confirm은 Session durable commit 직후 5분 hold를 consumption으로 전환하고 AttemptGroup을 OPEN하는 단계이며, Summary 조회 가능 시점은 별도 COMPLETED event라는 기존 계약을 재확인했다.
- 실행한 테스트와 결과: 설명·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: `reserve → Session commit → confirm`, 5분 RESERVED, confirm 뒤 일반 cancel 금지, Summary 조회 가능 시 COMPLETED와 최종 실패 시 RETAKE_AVAILABLE을 변경하지 않았다.
- 결정사항: 새 계약을 추가하지 않았다. confirm을 Summary 완료까지 지연하지 않는다.
- 위험 요소: Summary까지 RESERVED를 유지하면 hold가 시험 도중 만료돼 동일 무료권 재사용과 Session/consumption 불일치가 생긴다.
- 다음 작업: ADR-002 Lattice/SigV4 인프라 계약을 작성한다.

## 2026-08-26 — phone eligibility candidate 의미 설명

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-candidate-explained -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: raw phone 대신 사용하는 eligibility candidate의 생성 주체, 비교 의미와 보안 경계를 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. ADR과 애플리케이션 코드는 변경하지 않았다.
- 구현 내용: candidate를 Identity 전용 secret·consumer scope·normalized phone의 HMAC-SHA-256 가명값으로 정의하고, key rotation의 다중 candidate, Billing alias 비교, Lattice 인증과의 차이를 정리했다.
- 실행한 테스트와 결과: 설명·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: raw phone·last4 비저장, Identity key material 비공유, candidate log 금지, 3년 보존·purge와 C3-D 인증을 변경하지 않았다.
- 결정사항: 새 계약을 추가하지 않았다. candidate는 인증 credential이 아니라 phone당 TrialClaim dedupe용 pseudonymous identifier다.
- 위험 요소: 일반 SHA-256(phone)처럼 secret 없는 hash를 쓰면 번호 사전대입이 가능하고, candidate를 로그에 남기면 가명 식별자의 불필요한 복제가 생긴다.
- 다음 작업: ADR-002 Lattice/SigV4 인프라 계약을 작성한다.

## 2026-08-26 — ADR-002 전 사용자 확정사항 분류

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-adr002-inputs-classified -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: Lattice/ECS/IAM/SG/SigV4 ADR-002 작성 전에 사용자가 선택할 정책과 AWS에서 조회할 사실을 구분한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. ADR과 애플리케이션 코드는 변경하지 않았다.
- 구현 내용: 환경별 service network, IaC source, task role 생성, custom domain과 signer 의존성을 사용자 결정 대상으로, 실제 ARN/VPC/subnet/SG/service 값은 read-only 조회 대상으로 분류했다. 확정된 Billing no-ALB, route별 최소 권한과 bypass 차단은 재선택 대상에서 제외했다.
- 실행한 테스트와 결과: 인프라 결정 분석·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: C3-D, 서비스별 최소 권한, Billing Lattice-only ingress, 별도 repair role, local/test fake와 production fail-closed 원칙을 유지했다.
- 결정사항: 새 인프라 선택은 아직 확정하지 않았다. 환경 분리·IaC·task role·DNS·AWS SDK signer에 권장안을 제시한다.
- 위험 요소: 실제 account/VPC topology를 확인하지 않고 ARN과 auth policy를 작성하면 전체 호출 차단 또는 과도한 권한이 생긴다. shared task role이면 Identity와 Learning Core route 권한을 분리할 수 없다.
- 다음 작업: 사용자가 운영 선택을 승인하고 AWS 배포 사실을 제공하거나 read-only 조회를 허용하면 ADR-002를 작성한다.

## 2026-08-26 — VPC Lattice 환경 분리 비용 확인

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-lattice-env-cost-checked -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 환경별 service network 분리가 VPC Lattice 비용을 직접 증가시키는지 AWS 공식 가격표로 확인한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. ADR과 애플리케이션 코드는 변경하지 않았다.
- 구현 내용: 공식 가격표의 과금 차원을 provisioned service 시간, 데이터 GB, HTTP request/TCP connection으로 확인했다. service network/VPC association 자체와 별개로, staging Billing service를 추가 상시 배포할 때 두 번째 service-hour 비용이 생긴다는 점을 구분했다.
- 실행한 테스트와 결과: 공식 AWS pricing page를 읽기 전용 확인하고 문서만 변경했으므로 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: 환경별 service network 분리 권장, Billing Lattice-only ingress와 staging negative/E2E gate를 변경하지 않았다.
- 결정사항: 새 인프라 선택을 확정하지 않았다. 같은 서비스 수라면 network 공유가 직접 비용을 줄이지 않으므로 격리를 우선하는 권장안을 유지한다.
- 위험 요소: 공식 예시의 us-east-1 단가를 서울 리전에 그대로 적용해서는 안 된다. staging service 상시 운영 여부, 실제 요청량·처리 GB와 서울 단가를 Cost Calculator/배포 region으로 산정해야 한다.
- 다음 작업: 사용자가 환경별 network 분리와 staging 상시/필요시 배포 중 하나를 선택하면 ADR-002 비용 가정에 반영한다.

## 2026-08-26 — staging Lattice 운영 방식 설명

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-staging-lattice-lifecycle-explained -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 비용 절감을 위해 staging Lattice 리소스를 반복 생성·삭제해야 하는지와 더 단순한 운영 방식을 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. ADR과 애플리케이션 코드는 변경하지 않았다.
- 구현 내용: service network/service/listener/target/IAM/SG는 상시 유지하고, 비용 절감이 필요할 때 ECS desired count만 0/1로 조절하는 방식을 현실적 대안으로 정리했다. 개발 중에는 staging task 1개 상시 운영을 기본 권장으로 유지했다.
- 실행한 테스트와 결과: 운영 방식 분석·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: 환경별 격리, staging negative/E2E gate, production과 동일한 Lattice/SigV4 경로를 유지했다.
- 결정사항: 새 운영 방식을 아직 확정하지 않았다. 수동 생성·삭제는 비권장이고 상시 staging 인프라를 권장한다.
- 위험 요소: ECS task만 0으로 낮춰도 Lattice service-hour 비용은 남는다. 서비스 삭제를 수동 반복하면 ARN/DNS 변경과 IAM propagation 때문에 staging이 production을 재현하지 못할 수 있다.
- 다음 작업: 사용자가 staging 인프라 상시 유지 권장안을 승인하면 ADR-002 비용·운영 가정에 반영한다.

## 2026-08-26 — production/staging ECS 운영 형태 설명

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-prod-staging-cluster-explained -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 운영용/테스트용 ECS cluster를 분리하고 테스트 전에 staging task를 올리는 방식이 권장 구조인지 확인한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. ADR과 애플리케이션 코드는 변경하지 않았다.
- 구현 내용: production/staging 두 환경 세트와 staging task 0→1→health→E2E→0 흐름을 정리했다. cluster 분리뿐 아니라 DB, secret, task role, Lattice network와 SG도 분리해야 함을 명시했다.
- 실행한 테스트와 결과: 운영 구조 설명·문서 변경만 수행해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: 환경별 Lattice 격리, staging negative/E2E gate, production Lattice-only ingress와 최소 권한을 유지했다.
- 결정사항: 새 인프라 구성을 아직 최종 승인하지 않았다. 두 cluster/two environment와 on-demand staging task를 권장 형태로 구체화했다.
- 위험 요소: cluster 이름만 분리하고 production DB·role·secret을 공유하면 테스트가 production에 영향을 줄 수 있다. task 0에서도 Lattice와 DB 비용은 남을 수 있다.
- 다음 작업: 사용자가 이 구조를 승인하면 ADR-002에 environment topology와 staging scale runbook을 확정 반영한다.

## 2026-08-26 — production 현행 유지·staging cluster 사전 생성 계약 승인

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-prod-staging-cluster-approved -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 현재 Identity·Learning Core가 운영 중인 단일 ECS cluster의 처리와 Billing production 배포 전 staging 환경 준비 의무를 확정한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드와 AWS resource는 변경하지 않았다.
- 구현 내용: 기존 단일 cluster를 production으로 유지하고, Billing production 배포 전 별도 staging cluster와 staging Identity·Learning Core·Billing service를 준비하는 것을 배포 gate로 기록했다. staging Lattice 인프라는 유지하되 task는 평소 0, E2E 전 1 이상으로 운영한다.
- 실행한 테스트와 결과: 계약·상태 문서만 변경해 Gradle 테스트는 실행하지 않았다. 문서 whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: 기존 Identity·Learning Core inbound Load Balancer 유지, Billing no-ALB/Lattice-only ingress, C3-D SigV4/AWS_IAM, route별 최소 권한과 production gate를 변경하지 않았다.
- 결정사항: 현재 cluster는 production, 새 cluster는 staging으로 간주한다. staging은 DB·Secret·task role·Lattice policy/network·SG를 production과 분리하고, 평소 task 0과 테스트 전 `1+`를 사용한다.
- 위험 요소: cluster만 분리하고 production data·credential·IAM/network boundary를 공유하면 staging이 production에 영향을 줄 수 있다. staging task 0에서도 Lattice·Mongo 등 managed service 비용은 남을 수 있다.
- 다음 작업: AWS region/account/VPC·IaC 방식·현재 task role을 확인해 ADR-002에 실제 environment topology, IAM route policy, SG, SigV4 client와 staging start/stop runbook을 고정한다.

## 2026-08-26 — ADR-002 환경 입력 승인·기존 배포 workflow 확인

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-adr002-inputs-workflows-confirmed -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 사용자가 제공한 ADR-002 환경·기술 선택을 확정하고 Identity·Learning Core의 기존 GitHub Actions 배포 방식과 대조한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드, 다른 서비스 저장소와 AWS resource는 변경하지 않았다.
- 구현 내용: 환경별 Lattice 분리, 서울 region, 같은 VPC, 서비스별 task role, 기본 Lattice DNS와 AWS SDK v2 signer를 확정했다. 두 workflow가 GitHub OIDC→ECR→현 Task Definition image render→ECS Service deploy만 수행하며 최초 인프라 생성 IaC는 아님을 확인했다.
- 실행한 테스트와 결과: 문서·workflow 읽기 전용 분석이므로 Gradle 테스트는 실행하지 않았다. `git diff --check`와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: C3-D, Billing no-ALB/Lattice-only ingress, direct bypass 차단, 환경 격리, static AWS key 비사용과 GitHub Actions·AWS 무변경 원칙을 유지했다.
- 결정사항: Lattice network·task role·DNS·signer 권장안은 승인됐다. 배포 workflow는 application revision 갱신 소스이며 인프라 source of truth는 아직 없거나 외부에 있다.
- 위험 요소: 현재 workflow의 cluster와 domain 이름이 staging이므로 기존 cluster를 production으로 간주한 문서 가정과 충돌한다. 이를 확인하지 않고 클러스터를 추가하면 환경 역할을 거꾸로 구성할 수 있다.
- 다음 작업: AWS account 공유 여부와 현 cluster의 staging/production 역할, Console 대 IaC 선택을 확인한 뒤 read-only AWS 실값으로 task role·VPC/subnet·SG를 검증하고 ADR-002를 작성한다.

## 2026-08-26 — 현행 운영 cluster의 staging 전환·새 production 이관 계약 확정

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-current-to-staging-new-production-approved -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 현재 실제 운영 트래픽을 처리하는 `tosunsaeng-staging-cluster`의 최종 역할과 새 production cluster 이관 순서를 확정한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션, GitHub Actions와 AWS resource는 변경하지 않았다.
- 구현 내용: production/staging이 같은 AWS account·VPC를 사용하는 사실, 최초 인프라는 Console 수동 생성·애플리케이션은 GitHub Actions 배포인 현재 방식을 기록했다. 새 production cluster에 Identity·Learning Core를 구성·검증하고 트래픽을 전환한 뒤 현 cluster를 staging으로 전환하는 순서를 확정했다.
- 실행한 테스트와 결과: 계약·상태 문서만 변경해 Gradle 테스트는 실행하지 않았다. 문서 heading, whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: 환경별 Lattice 분리, Billing Lattice-only ingress, 서비스별 task role, 기본 DNS, AWS SDK v2 signer, staging `desiredCount=0/1+`와 production gate를 유지했다.
- 결정사항: 현 cluster는 전환 전까지 운영용이지만 최종적으로 staging이 된다. 새 production cluster와 환경 경계를 먼저 완성하고 검증·롤백 준비 후 트래픽을 전환한다.
- 위험 요소: Identity·Learning Core의 DB/Secret 연결, ALB target·DNS, session/token 호환성과 rollback window를 확인하지 않고 트래픽을 전환하면 인증·시험 중단이 발생할 수 있다.
- 다음 작업: ADR-002에 환경 topology, Lattice/IAM/SG/SigV4 설계와 새 production 구성→현행 병행 검증→트래픽 전환→기존 cluster staging 전환 runbook을 구체화한다.

## 2026-08-26 — ADR-002 VPC Lattice·ECS SigV4·production/staging 이관 설계 작성

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-adr002-lattice-ecs-migration-written -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 승인된 C3-D VPC Lattice/SigV4, 환경별 role/network 격리와 현 운영 cluster의 staging 전환을 구현·배포 가능한 ADR로 구체화한다.
- 변경 파일: `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. application, GitHub Actions, AWS resource는 변경하지 않았다.
- 구현 내용: production/staging Lattice topology, ECS target/listener/health, role 7종 분리, route별 IAM matrix·policy template, SG direct bypass 차단, Java SigV4·timeout/retry, configuration/Secret, staging 0/1+ E2E, 현행 inventory→새 production 병행 검증→트래픽 전환→기존 cluster staging 전환→Billing 배포 runbook을 고정했다.
- 실행한 테스트와 결과: `aws sts get-caller-identity --region ap-northeast-2`는 local credential이 없어 `NoCredentials`로 종료됐다. AWS mutation은 없었다. 문서 heading/code fence/whitespace/marker와 금지 문자열을 종료 전에 검증하고, 코드 변경이 없어 Gradle test는 실행하지 않는다.
- 유지한 계약: Billing no-ALB/Lattice-only ingress, static credential·raw phone·candidate log 금지, Identity/Learning Core 도메인 경계, `reserve → Session commit → confirm`, same-key retry, 환경별 Lattice·DB·Secret·role 분리와 production gate를 유지했다.
- 결정사항: `AWS_IAM`, exact principal/method/path policy, Lattice managed-prefix-list target SG, SDK v2 `DefaultCredentialsProvider`·`vpc-lattice-svcs`·`ap-northeast-2`, generated DNS, 1초 connect/3초 request timeout과 caller-owned retry를 기술 기준으로 삼았다. 실제 ARN/ID는 infra output으로 주입한다.
- 위험 요소: same VPC에서 환경 오호출, policy ARN/path 오타, 현 production DB/Secret의 staging 잔류, 두 cluster background worker 중복, Identity issuer/JWKS·session 비호환과 DNS cutover 실패가 주요 위험이다.
- 다음 작업: credential이 있는 환경에서 실제 AWS read-only inventory를 수행하고 policy validation 후, ADR-001 Identity event inbox/current binding vertical slice 구현을 시작한다.

## 2026-08-26 — ADR-002 후 다음 구현 순서 확인

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-next-after-adr002-explained -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: ADR-001/002 작성 후 즉시 시작할 애플리케이션 작업과 배포 전 인프라 gate를 구분한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. application·ADR·AWS·GitHub Actions는 변경하지 않았다.
- 구현 내용: Identity eligibility event inbox, payload digest·revision high-water·current binding Transaction을 첫 vertical slice로 지정했다. 이후 TrialClaim/free grant/reserve, confirm/cancel/status/expiry/group, 서비스 client SigV4, staging E2E 순으로 진행한다.
- 실행한 테스트와 결과: 작업 순서 설명·문서 변경만 수행해 Gradle test는 실행하지 않았다. whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: Identity→Billing event의 C3-D 배포 경계, raw phone 비저장, eventId 멱등성, revision high-water, Mongo Transaction·fail-closed와 production 배포 gate를 유지했다.
- 결정사항: 새 production cluster/Lattice를 즉시 생성하지 않고 local application vertical slice를 먼저 구현한다. AWS inventory·환경 이관은 배포 전 필수 gate로 남는다.
- 위험 요소: 인프라가 아직 없다고 인증 경계를 controller에서 제거하거나 production endpoint를 열면 안 된다. local/test와 production Lattice profile을 분리해야 한다.
- 다음 작업: Identity eligibility event inbox/current binding vertical slice 코드·index·Transaction·test를 구현하고 `./gradlew clean test`를 실행한다.

## 2026-08-26 — Identity phone eligibility event consumer 구현 계획서 작성

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-identity-event-consumer-plan-created -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: Identity schema v1 eligibility event를 Billing inbox·revision high-water·current binding Transaction으로 수신하는 첫 vertical slice의 구현 계획을 작성한다.
- 변경 파일: `docs/plans/PLAN-001-identity-phone-eligibility-event-consumer.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. ADR·application·Identity·AWS·GitHub Actions는 변경하지 않았다.
- 구현 내용: 목표/비범위, Identity 실제 producer constraint, package·파일, strict 16 KiB decode·canonical digest, inbox/current binding document·index, Transaction·race convergence, HTTP/security/privacy, unit/MVC/replica-set concurrency test, 6단계 구현 순서·완료 조건·위험을 구체화했다.
- 실행한 테스트와 결과: Identity producer·Billing skeleton을 읽기 전용으로 확인했고 계획·문서만 변경해 Gradle test는 실행하지 않았다. heading/code fence/whitespace/marker와 민감 문자열을 종료 전에 검증한다.
- 유지한 계약: event 수신은 사용권 지급이 아니며 raw phone/key material 비수신, eventId 멱등성, revision high-water, local Mongo Transaction, C3-D/default deny, no TrialClaim/grant/Reservation을 유지했다.
- 결정사항: malformed/unsupported payload 원문은 저장하지 않고 verified/revoked fixture와 strict decoder로 검증한다. inbox 120일, current binding revision tombstone, explicit index initializer·replica-set Testcontainers를 구현 기준으로 삼았다.
- 위험 요소: ADR-001의 same user·scope·revision conflict를 DB race에서 강제할 `consumerScopeId`·compound unique index가 아직 index 표에 없다. 구현 Step 1에서 ADR을 보정하고 코드/index option test와 함께 고정해야 한다.
- 다음 작업: PLAN-001 Step 1부터 순서대로 ADR 보정·fixture·decoder·Mongo Transaction·controller/security·Testcontainers concurrency test를 구현하고 `./gradlew clean test`를 실행한다.

## 2026-08-26 — phone eligibility binding 명칭 단축 선택지 정리

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-phone-eligibility-name-options -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 구현 전 `phone-eligibility-bindings` route·collection·package 명칭을 짧고 명확하게 바꾸는 선택지를 정리한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 계약 ADR·PLAN·application·Identity는 아직 변경하지 않았다.
- 구현 내용: `trial-eligibility`, `eligibility`, `phone-proof` 계열을 비교하고 Billing 무료시험 목적이 드러나는 `trial-eligibility` 계열을 권장안으로 선정했다.
- 실행한 테스트와 결과: 명칭 검토·문서 기록만 수행해 Gradle test는 실행하지 않았다. whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: raw phone 비저장, verified phone candidate, eventId/revision 멱등성과 Identity schema v1 wire 호환성을 유지했다.
- 결정사항: 최종 명칭은 아직 미확정이다. Identity wire event type까지 rename하는 것은 cross-service schema 변경이므로 비권장이다.
- 위험 요소: `eligibility`만 쓰면 향후 유료·쿠폰 eligibility와 모호해지고, `phone-proof`는 인증 credential/전화번호 원문 증명으로 오해될 수 있다.
- 다음 작업: 사용자가 명칭을 선택하면 ADR-001, ADR-002, PLAN-001의 route·collection·package·index 참조를 일괄 치환한 뒤 구현을 시작한다.

## 2026-08-26 — internal route의 hyphen 제거 요구 반영

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-no-hyphen-route-preference -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 짧은 eligibility route에도 hyphen을 사용하지 않으려는 사용자 명칭 선호를 반영한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. ADR·PLAN·application·Identity는 아직 변경하지 않았다.
- 구현 내용: camelCase URL보다 단어를 path segment로 나눈 `/internal/v1/trial/eligibility/events`를 권장했고 collection `trial_eligibility`, package `trialeligibility`와 함께 일관 명칭으로 정리했다.
- 실행한 테스트와 결과: 명칭 검토·문서 기록만 수행해 Gradle test는 실행하지 않았다. whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: `/internal/v1`, Identity event-only route, schema v1 wire event type, raw phone 비저장과 C3-D route 권한 계약을 유지했다.
- 결정사항: URL에 hyphen을 사용하지 않는 선호는 확인했으나 정확한 route 문자열은 사용자 최종 승인 전까지 미확정이다.
- 위험 요소: `/trialEligibility`는 URL에 camelCase 일관성 문제가 있고 `/trial/events`는 eligibility state event임이 모호하다.
- 다음 작업: `/internal/v1/trial/eligibility/events`가 승인되면 ADR-001/002, PLAN-001과 후속 Identity endpoint configuration의 route를 이 값으로 고정한다.

## 2026-08-26 — `trial/eligibility` 단축 명칭 최종 승인·반영

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-trial-eligibility-naming-applied -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: hyphen 없는 짧은 Billing trial eligibility route·collection·package 명칭을 최종 확정하고 계약·계획 문서에 반영한다.
- 변경 파일: `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`, `docs/plans/PLAN-001-identity-phone-eligibility-event-consumer.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. application·Identity·AWS는 변경하지 않았다.
- 구현 내용: route를 `/internal/v1/trial/eligibility/events`, Mongo collection을 `trial_eligibility`, Java package를 `trialeligibility`로 고정했다. Billing class/test 계획은 `TrialEligibility*`, collection index는 `ux_trial_scope_user`·`ix_trial_key_version`로 단축했고 ADR-002 Lattice method/path policy도 같은 route로 바꾸었다.
- 실행한 테스트와 결과: 계약·계획 문서 명칭만 변경해 Gradle test는 실행하지 않았다. old route/collection/package 잔류, policy JSON, code fence, whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: Identity schema v1 wire event type, verified phone candidate·consumer scope, eventId/revision 멱등성, raw phone 비저장, C3-D Identity-only route 권한을 유지했다.
- 결정사항: Billing의 신규 public/internal 자원 명칭에는 `phone-eligibility-bindings`를 사용하지 않는다. 단 Identity producer의 이미 배포된 event type은 호환성을 위해 rename하지 않는다.
- 위험 요소: Identity delivery endpoint 설정이 예전 route를 사용하면 404/403으로 publisher가 dead-letter될 수 있으므로 연동 배포 시 endpoint·Lattice policy·contract test를 한 번에 맞춰야 한다.
- 다음 작업: PLAN-001 Step 1의 ADR inbox index 보정·producer fixture부터 `trialeligibility` 패키지로 구현을 시작한다.

## 2026-08-26 — PLAN-001 제목·파일·fixture·metric 명칭 추가 정합

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-plan-trial-eligibility-naming-aligned -->

- 날짜: 2026-08-26
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 앞서 확정한 `trial eligibility` 명칭을 다음 구현 계획서의 제목·파일명·fixture·metric까지 완전히 반영한다.
- 변경 파일: `docs/plans/PLAN-001-trial-eligibility-event-consumer.md`, `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. application·Identity·AWS는 변경하지 않았다.
- 구현 내용: PLAN-001을 `PLAN-001-trial-eligibility-event-consumer.md`로 rename하고 제목을 Trial eligibility vertical slice로 바꾸었다. test fixture는 `trial-eligibility-verified-v1.json`·`trial-eligibility-revoked-v1.json`, metric은 `billing.trial_eligibility.events`로 맞추고 ADR 섹션·route 설명도 trial eligibility로 통일했다.
- 실행한 테스트와 결과: 계약·계획 문서만 변경해 Gradle test는 실행하지 않았다. old PLAN path·Billing 명칭 잔류, code fence, whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: Identity wire event type `PhoneEligibilityBindingVerified`/`Revoked`, schema v1 payload·candidate constraint, endpoint·collection·package·index 신규 명칭과 C3-D route policy를 유지했다.
- 결정사항: Billing 소유 artifact에는 Trial eligibility 용어를 사용하고 Identity 소유 wire event type만 PhoneEligibilityBinding 이름을 유지한다.
- 위험 요소: 이전 PLAN path를 외부 문서가 참조했다면 링크가 끊길 수 있으나, 현재 저장소 현행 상태 참조는 신규 path로 갱신했고 과거 WORKLOG은 수정하지 않았다.
- 다음 작업: rename된 PLAN-001 Step 1의 ADR inbox index 보정·producer fixture부터 구현을 시작한다.

## 2026-08-27 — Trial·유료·coupon eligibility 도메인 분리 설명

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-trial-paid-coupon-eligibility-boundaries -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: `trial_eligibility` 명칭이 향후 유료·coupon eligibility를 제한하거나 하나의 범용 collection에 혼합하려는 의미인지 명확히 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 계약 ADR·PLAN·application·Identity·AWS는 변경하지 않았다.
- 구현 내용: trial eligibility는 verified-phone fact→TrialClaim/free grant의 자격 근거, 유료는 Store 검증→payment ledger→paid grant/pass, coupon은 campaign 규칙→redemption ledger→promotional grant로 분리하고 reserve에서만 공통 `EntitlementResolver`로 통합하는 목표 구조를 정리했다.
- 실행한 테스트와 결과: 도메인 경계 설명·문서 기록만 수행해 Gradle test는 실행하지 않았다. whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: event 수신 자체는 free grant 지급이 아니고 첫 reserve에서 TrialClaim/grant를 생성한다. payment/coupon/reward는 현재 MVP 비범위이며 ledger/grant가 진실 공급원이다.
- 결정사항: 새 제품 정책을 확정하지 않았다. 범용 eligibility document에 trial/payment/coupon을 혼합하지 않고 소스별 aggregate와 ledger를 유지하며 reserve resolver에서 통합하는 기존 경계를 재확인했다.
- 위험 요소: 하나의 `eligibility` boolean/collection에 모든 혜택을 넣으면 Store 검증, coupon redemption, TrialClaim의 멱등성·보존·환불 규칙이 섞이고 balance를 진실 공급원으로 잘못 사용할 수 있다.
- 다음 작업: 현재는 PLAN-001 trial eligibility event consumer를 구현하고, 결제 전 C9~C11에서 paid grant/pass·resolver priority를, 보상 전 C12에서 coupon campaign/redemption·promotional grant를 구체화한다.

## 2026-08-27 — 향후 paid·coupon eligibility 명칭 규칙 정정

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-paid-coupon-eligibility-naming-clarified -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 사용자가 물은 향후 유료·coupon eligibility의 정확한 명칭 규칙을 다시 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. ADR·PLAN·application·Identity·AWS는 변경하지 않았다.
- 구현 내용: 명칭 규칙을 `{kind}/eligibility` URL, `{kind}_eligibility` collection, `{kind}eligibility` Java package로 정리했다. 현 Trial은 `trial`, 향후 예시는 `paid`, `coupon`을 kind로 사용하며 URL hyphen은 사용하지 않는다.
- 실행한 테스트와 결과: 명칭 설명·문서 정정만 수행해 Gradle test는 실행하지 않았다. whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: 현 API `/internal/v1/trial/eligibility/events`, collection `trial_eligibility`, package `trialeligibility`와 Identity wire event type를 변경하지 않았다.
- 결정사항: 현재 확정된 실제 자원은 Trial뿐이다. paid/coupon은 향후 해당 eligibility 자원이 필요할 때 적용할 명칭 규칙이며 케이스별 endpoint·storage 구현 승인은 아직 하지 않았다.
- 위험 요소: 유료 사용권이 단순 payment entitlement일 뿐인데 `paid_eligibility`를 무조건 만들거나 coupon redemption을 eligibility fact와 혼합하면 불필요한 aggregate가 생길 수 있다.
- 다음 작업: Trial은 현 명칭으로 PLAN-001을 구현하고 paid/coupon의 실제 자원은 C9~C12 구현 전에 이 규칙을 기준으로 확정한다.

## 2026-08-27 — eligibility-first URL namespace 검토

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-eligibility-first-url-review -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: eligibility를 URL 앞에 배치해 Trial·향후 paid·coupon API를 한 namespace에서 볼 수 있는지 검토한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. ADR·PLAN·application·Identity·AWS는 변경하지 않았다.
- 구현 내용: `/internal/v1/eligibility/{kind}/...` namespace 아래에 종류별 sub-route를 두는 안과, 단일 `/eligibility/events` endpoint에서 type으로 분기하는 안을 구분했다.
- 실행한 테스트와 결과: 설계 검토와 작업 기록만 수행해 Gradle test는 실행하지 않았다. whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: Trial·payment·coupon의 원장, 멱등성, 권한과 수명주기는 분리하며 reserve resolver에서만 통합한다. URL namespace 통합이 저장소나 aggregate 통합을 의미하지 않는다.
- 결정사항: eligibility-first namespace를 권장 검토안으로 기록했다. 사용자의 최종 승인 전이므로 현 Trial route와 ADR·PLAN 명칭은 변경하지 않았다.
- 위험 요소: 모든 eligibility 종류를 한 endpoint와 공통 DTO로 합치면 변경 영향과 권한 범위가 커지고 서로 다른 도메인 규칙이 결합될 수 있다.
- 다음 작업: 승인 시 Trial route를 `/internal/v1/eligibility/trial/events`로 변경하고 ADR-001·ADR-002·PLAN-001·계약 상태 문서의 참조를 함께 정렬한다.

## 2026-08-27 — eligibility-first URL namespace 승인·반영

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-eligibility-first-url-approved -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 승인된 eligibility-first URL namespace를 Trial 계약과 구현 계획에 확정 반영한다.
- 변경 파일: `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`, `docs/plans/PLAN-001-trial-eligibility-event-consumer.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. application·Identity·AWS는 변경하지 않았다.
- 구현 내용: Trial route와 Lattice method/path policy를 `/internal/v1/eligibility/trial/events`로 변경했다. 공통 규칙을 `/internal/v1/eligibility/{kind}/...`로 확정하고 향후 paid·coupon은 각각 하위 namespace로 확장하도록 기록했다.
- 실행한 테스트와 결과: 계약·계획 문서만 변경해 Gradle test는 실행하지 않았다. 현행 문서의 구 route 잔류, whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: `trial_eligibility` collection, `trialeligibility` package, `TrialEligibility*` class, Identity wire event type과 payload는 변경하지 않았다. eligibility 종류별 DTO·권한·멱등성·aggregate는 계속 분리한다.
- 결정사항: URL namespace만 eligibility 기준으로 통합하며 모든 종류를 단일 endpoint나 공통 DTO로 합치지 않는다.
- 위험 요소: Identity publisher와 Lattice route policy 구현 시 이전 route를 사용하면 403 또는 404가 발생하므로 양쪽 설정을 같은 배포 단위에서 맞춰야 한다.
- 다음 작업: PLAN-001 Step 1에서 확정 route를 controller contract test와 security allowlist에 적용한다.

## 2026-08-27 — strict decoder·canonical digest 계약 설명

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-strict-decoder-digest-explained -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: PLAN-001 7.2 strict decoder와 7.3 canonical digest가 필요한 이유와 동작을 사용자에게 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 계약 ADR·PLAN·application·Identity·AWS는 변경하지 않았다.
- 구현 내용: strict decoder를 저장 전 계약 방화벽으로, canonical digest를 표현 차이를 제거한 의미 기반 멱등성·conflict 판별값으로 요약했다.
- 실행한 테스트와 결과: 설명·작업 기록만 수행해 Gradle test는 실행하지 않았다. whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: expected opaque scope exact match, unknown contract의 422 무저장, raw payload 미저장, canonical JSON SHA-256과 eventId/digest 멱등성을 유지한다.
- 결정사항: 새 계약을 추가하거나 기존 선택을 변경하지 않았다.
- 위험 요소: decoder 설정 일부가 느슨하면 coercion이나 unknown field가 조용히 수용되고, raw JSON을 직접 hash하면 의미가 같은 재전송을 conflict로 오판할 수 있다.
- 다음 작업: 구현 시 decoder·canonicalizer를 분리하고 PLAN-001 13.1의 순서·whitespace·coercion·duplicate field test로 계약을 고정한다.

## 2026-08-27 — canonical digest 처리 단계 설명

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-canonical-digest-pipeline-explained -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: JSON 수신부터 digest 저장까지 각 단계가 수행하는 일을 쉬운 표현으로 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 계약 ADR·PLAN·application·Identity·AWS는 변경하지 않았다.
- 구현 내용: pipeline을 입력 안전성 검사, 의미값 정규화, 결정적 SHA-256 지문 생성과 재전송 비교 준비로 요약했다.
- 실행한 테스트와 결과: 설명·작업 기록만 수행해 Gradle test는 실행하지 않았다. whitespace와 marker 단일 존재를 검증한다.
- 유지한 계약: raw payload 미저장, canonical JSON SHA-256, same eventId/same digest no-op, same eventId/different digest conflict를 유지한다.
- 결정사항: 새 계약이나 구현 변경은 없다.
- 위험 요소: digest를 암호화나 실제 이벤트 처리 결과로 오해할 수 있으므로 멱등성 비교용 지문임을 명확히 구분해야 한다.
- 다음 작업: 구현 시 각 단계별 실패 응답과 canonicalization 단위 테스트를 PLAN-001대로 작성한다.

## 2026-08-27 — PLAN-001 전체 쉬운 설명

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-plan001-plain-language-explained -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: PLAN-001의 목표·처리 흐름·저장 구조·동시성·오류·보안·테스트·구현 단계를 비기술적인 흐름으로 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 계약 ADR·PLAN·application·Identity·AWS는 변경하지 않았다.
- 구현 내용: PLAN-001을 무료권 지급 전 phone eligibility 동기화 단계로 규정하고, 이벤트 입장 검사부터 inbox/current projection 원자 저장과 네 가지 결과 수렴까지 사용자 관점으로 요약했다.
- 실행한 테스트와 결과: 설명·작업 기록만 수행해 Gradle test는 실행하지 않았다. whitespace와 marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: event 수신 자체는 TrialClaim·grant·ledger·Reservation을 만들지 않으며 raw phone을 받거나 저장하지 않는다. default deny, Mongo Transaction, explicit unique index와 replica-set concurrency test를 유지한다.
- 결정사항: 새 계약을 추가하거나 PLAN-001 범위를 변경하지 않았다.
- 위험 요소: 이 단계를 무료권 지급 완료로 오해하면 후속 Claim·grant·reserve 구현이 누락될 수 있으므로 eligibility evidence 동기화와 entitlement 지급을 명확히 분리한다.
- 다음 작업: 사용자 확인 뒤 PLAN-001 Step 1부터 구현하며 완료 후 TrialClaim·free grant·initial reserve vertical slice로 진행한다.

## 2026-08-27 — Billing AGENTS.md 최신화

<!-- codex-turn:01a041e7-056f-71e1-bf94-e66474f45bdc -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: Billing 저장소의 기존 `AGENTS.md`를 다른 앱 서버와 같은 수준으로 보강하고 현재 확정된 Billing 계약을 작업 규칙에 반영한다.
- 변경 파일: `AGENTS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: Billing 저장소 전용 변경 경계, 무료 최소 Entitlement와 후속 결제 범위, TrialClaim 3년 보존·purge, eligibility event API·멱등성·Transaction, VPC Lattice AWS_IAM·ECS task role·SigV4, Mongo 원장 불변식과 코드 리뷰 우선순위를 추가했다.
- 실행한 테스트와 결과: 문서·에이전트 작업 규칙만 변경해 Gradle 테스트는 실행하지 않았다. `git diff --check`, trailing whitespace와 turn marker 단일 포함을 종료 전에 검증한다.
- 유지한 계약: `/internal/v1/eligibility/trial/events`, Identity wire schema v1, event 수신과 혜택 지급 분리, `reserve → Session commit → confirm`, 5분 Reservation expiry, raw phone 비저장과 current 무료 MVP 범위를 변경하지 않았다.
- 결정사항: 새 제품·API·인프라 계약을 만들지 않고 2026-08-26~27에 이미 승인된 ADR·PLAN·CURRENT_STATE의 내용을 저장소 작업 규칙으로 승격했다. 앱 사용자 Billing API와 Apple/Google 결제는 계속 후속 범위다.
- 위험 요소: 현재 Billing 프로젝트 파일 전체가 Git 미추적 상태이며 실제 도메인 코드와 Lattice 인프라는 아직 구현되지 않았다. AGENTS 규칙 추가 자체가 production readiness를 의미하지 않는다.
- 다음 작업: 사용자가 Billing 초기 골격을 기준선으로 commit한 뒤 PLAN-001 Step 1의 Trial eligibility event consumer를 구현한다.

## 2026-08-27 — AGENTS.md Billing 전용 계약 정교화

<!-- codex-turn:01a041f8-9c74-7fa1-bacd-cbdad5cac50a -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 다른 서버 형식의 일반 규칙이 아니라 실제 Billing ADR-001·ADR-002·PLAN-001을 기준으로 `AGENTS.md`를 Billing 전용 작업 규칙으로 정교화한다.
- 변경 파일: `AGENTS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: 제품 MVP와 현재 PLAN-001 범위를 분리하고 internal API 16 KiB·204/error mapping, 인증된 service userId 예외, Reservation wire 규칙, collection/index·TTL/보존 분리와 Billing 전용 contract/concurrency review 항목을 추가했다.
- 실행한 테스트와 결과: 작업 규칙과 문서만 변경해 Gradle 테스트는 실행하지 않았다. `git diff --check`, trailing whitespace와 turn marker 단일 포함을 종료 전에 검증한다.
- 유지한 계약: Identity schema v1, `/internal/v1/eligibility/trial/events`, raw payload 비저장, event 수신과 TrialClaim 지급 분리, VPC Lattice AWS_IAM·SigV4, `reserve → Session commit → confirm`과 외부 앱 API 비변경을 유지했다.
- 결정사항: PLAN-001에는 inbox·current projection consumer만 포함하고 TrialClaim·grant·Reservation은 후속 vertical slice로 유지한다. 신규 제품 정책이나 API는 추가하지 않았다.
- 위험 요소: `application.yml`의 `auto-index-creation=true`와 standalone test Mongo는 아직 PLAN-001 목표 상태와 다르며 실제 구현에서 versioned index initializer와 replica-set Testcontainers로 전환해야 한다. Billing 프로젝트 파일 전체도 아직 Git 미추적 상태다.
- 다음 작업: 사용자가 초기 기준선을 commit한 뒤 PLAN-001 Phase 0 ADR index 보정과 event consumer 구현을 시작한다.

## 2026-08-27 — Billing 서비스 간 통합 계약서 작성

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: Billing이 Identity·Learning Core와 어떤 계약으로 무엇을 전달하고 재시도·복구하는지 한 문서에서 파악할 수 있는 통합 계약서를 작성한다.
- 변경 파일: `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `AGENTS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`
- 구현 내용: 서비스 책임·통신 topology, Lattice/SigV4 인증, Identity eligibility event, Learning Core Reservation saga와 AttemptGroup event, 멱등성·오류·reconciliation, 개인정보·보존, local/test·배포·변경 절차와 연동 체크리스트를 작성했다.
- 실행한 테스트와 결과: 문서만 변경해 Gradle 테스트는 실행하지 않았다. 문서 경로·계약 용어, `git diff --check`와 trailing whitespace를 종료 전에 검증한다.
- 유지한 계약: Identity schema v1, `/internal/v1/eligibility/trial/events`, Reservation internal API·Idempotency-Key, `reserve → Session commit → confirm`, VPC Lattice AWS_IAM·SigV4, TrialClaim 3년 보존과 기존 Learning Core 공개 API 비변경을 유지했다.
- 결정사항: 새 wire 계약을 만들지 않고 CONTRACT_DECISIONS·ADR-001·ADR-002·PLAN-001의 승인 내용을 서비스 간 흐름 중심으로 통합했다. 세부 충돌 시 ADR이 최종 기준이다.
- 위험 요소: 문서가 설명하는 Billing consumer·Reservation API·Learning Core client와 Lattice 인프라는 아직 구현 전이다. 통합 계약서 작성이 production readiness를 의미하지 않는다.
- 다음 작업: PLAN-001 Trial eligibility consumer를 먼저 구현하고, 후속 TrialClaim·grant·Reservation과 Learning Core saga 단계마다 이 문서와 contract test를 함께 갱신한다.

## 2026-08-27 — Billing 통합 계약 외부 검토 확인

<!-- codex-turn:01a0422f-bec9-7440-ab16-e50f878cefe8 -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: 첨부된 Billing 통합 계약 검토의 네 가지 지적을 Billing·Identity·Learning Core 실제 문서와 코드에 대조해 사실 여부와 우선순위를 판정한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 계약, ADR, PLAN, AGENTS와 애플리케이션 코드는 변경하지 않았다.
- 분석 결과: ADR-001 inbox field/index·disposition 불일치와 필수 Idempotency-Key의 Learning Core 미구현은 정확하다. Identity Bearer publisher와 Billing SigV4 목표 차이도 staging 전 해소가 필요한 구현 공백이다. Identity의 `Retry-After` 미지원은 맞지만 eligibility 409는 구체 계약상 EVENT_ID_CONFLICT 전용이므로 COMMAND_PROCESSING과 body code를 구분해야 한다는 주장은 현재 endpoint에는 적용되지 않는다.
- 실행한 테스트와 결과: 읽기 전용 문서·코드 대조만 수행해 Gradle 테스트는 실행하지 않았다. 작업 기록의 `git diff --check`, trailing whitespace와 turn marker 단일 포함을 종료 전에 검증한다.
- 유지한 계약: Identity schema v1, eligibility event 204/409/422/503, VPC Lattice AWS_IAM·SigV4 목표, 필수 UUID v4 Idempotency-Key와 `reserve → Session commit → confirm`, TrialClaim 3년 보존을 변경하지 않았다.
- 결정사항: 네 지적 중 1·2·4는 유효, 3은 Retry-After 부분만 유효하다고 판정했다. restart 규칙 보강은 타당하고 AttemptGroup route hyphen은 현 no-hyphen 규칙 위반이 아니다.
- 위험 요소: ADR 보정 없이 PLAN 구현을 시작하면 unique index·disposition 구현이 갈릴 수 있다. Identity Bearer adapter와 Learning Core header 공백을 남긴 채 staging을 활성화하면 인증 실패 또는 멱등성 없는 시험 생성으로 이어진다.
- 다음 작업: ADR-001 Phase 0 보정 → PLAN-001 local consumer → Identity SigV4·Retry-After 지원 → 앱·Learning Core 필수 Idempotency-Key와 Reservation saga 순으로 별도 승인·작업한다.

## 2026-08-27 — Billing 통합 계약 불일치 보정

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: `TMI-95` 관련 Identity transport 계약 보정; Jira 변경 없음
- 작업 목표: 확인된 inbox, workload transport, retry와 Idempotency-Key 계약 불일치를 구현 착수 가능한 문서 상태로 정렬한다.
- 변경 파일: Billing `AGENTS.md`, `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, `docs/plans/PLAN-001-trial-eligibility-event-consumer.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, 작업 기록 문서; Identity ADR-002; Learning Core Billing 계약 검토 문서와 작업 기록 문서.
- 구현 내용: inbox scope/index/disposition, endpoint별 409 의미, Retry-After, 필수 공개 Idempotency-Key, restart와 transport retry 구분을 보정하고 Identity SigV4·Learning Core header의 목표 계약을 현재 코드 미구현 상태와 분리해 명시했다.
- 실행한 테스트와 결과: 계약·계획 문서만 변경해 Gradle 테스트는 실행하지 않았다. 세 저장소의 stale 계약 문자열, `git diff --check`, trailing whitespace와 작업 기록을 종료 전에 검증한다.
- 유지한 계약: Identity schema v1, event 수신과 지급 분리, Lattice AWS_IAM·SigV4, `reserve → Session commit → confirm`, 기존 시험 생성 Request Body·성공 DTO, TrialClaim 3년 보존과 외부 AI·S3·Redis 계약을 유지했다.
- 결정사항: eligibility 409는 EVENT_ID_CONFLICT 전용이고 COMMAND_PROCESSING은 Reservation 전용이다. 앱 시험 생성 Idempotency-Key는 optional이 아니라 필수이며 의도적 restart만 새 key·새 examId를 사용한다.
- 위험 요소: Identity 실제 adapter는 아직 Bearer이고 Retry-After를 읽지 않는다. Learning Core controller도 필수 header를 받지 않으며 Reservation client가 없다. 문서 보정만으로 staging 연동이 가능해진 것은 아니다.
- 다음 작업: PLAN-001 Billing consumer 구현 후 Identity SigV4 adapter·Retry-After, 앱·Learning Core header·Reservation saga를 순서대로 별도 구현·검증한다.

## 2026-08-27 — AGENTS·서비스 간 계약 정합성 리뷰

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-agent-integration-contract-review -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: 없음
- 작업 목표: Billing `AGENTS.md`와 서비스 간 통합 계약을 승인된 CONTRACT_DECISIONS·ADR·PLAN 및 실제 Identity·Learning Core 현재 코드와 대조해 모순·누락·연동 위험을 찾는다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 리뷰 대상 계약·ADR·PLAN·AGENTS와 Identity·Learning Core 코드는 변경하지 않았다.
- 구현 내용: Billing 내부 계약의 route·범위·인증·멱등성·Transaction·보존 정합성을 확인하고, ADR-001 inbox schema/index·disposition 불일치, Identity JWT→SigV4 미이관과 retry 응답 해석 부족, 앱→Learning Core operation ID wire 계약 누락, no-resume 불변식 문서 누락을 확인했다.
- 실행한 테스트와 결과: 읽기 전용 문서·코드 리뷰만 수행해 Gradle 테스트는 실행하지 않았다. `git diff --check`와 turn marker 단일 존재를 종료 전에 검증한다.
- 유지한 계약: `/internal/v1/eligibility/trial/events`, Identity schema v1, event 수신과 TrialClaim 지급 분리, VPC Lattice AWS_IAM·SigV4 목표, `reserve → Session commit → confirm`, TrialClaim 3년·daily purge·35일 backup과 PLAN-001 범위를 변경하지 않았다.
- 결정사항: 리뷰만 수행했고 수정안을 승인·적용하지 않았다. 과거 Learning Core 문서는 현 Billing 기준이 아니며 실제 다른 저장소는 읽기 전용으로만 확인했다.
- 위험 요소: ADR이 최종 기술 기준인데 PLAN/AGENTS와 inbox 계약이 다른 상태에서 구현하면 revision race를 DB에서 막지 못하거나 rejected payload 저장 정책이 갈릴 수 있다. Identity 현 adapter로는 Lattice SigV4 호출과 Retry-After/error code 계약을 만족하지 못하며, Learning Core 공개 API는 아직 필수 operation ID를 받지 않는다.
- 다음 작업: 사용자 승인 시 우선 Billing ADR-001 Phase 0 모순을 보정하고 PLAN-001을 구현한다. Identity와 Learning Core 변경은 각각 별도 저장소 작업으로 분리해 staging 연동 전에 수행한다.

## 2026-08-27 — 통합 계약 보정 재검증

<!-- codex-turn:01a037c4-43ac-7252-9317-ae89a1323e88-contract-fixes-reverified -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: Identity transport 관련 기존 `TMI-95`; Jira 변경 없음
- 작업 목표: 사용자가 보정한 Billing AGENTS·ADR·PLAN·통합 계약과 Identity·Learning Core 계약 문서가 이전 리뷰 지적을 해소했는지 재검증한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 리뷰 대상 계약·애플리케이션 코드·다른 저장소는 변경하지 않았다.
- 구현 내용: inbox scope/index/disposition, endpoint별 409 의미, 공개 필수 Idempotency-Key, restart 규칙, Identity SigV4 목표와 Retry-After 계약을 줄 단위로 대조하고 구 route·optional key·whitespace 잔류를 검사했다.
- 실행한 테스트와 결과: 계약 문서만 검토해 Gradle 테스트는 실행하지 않았다. Billing·Identity·Learning Core 관련 문서의 `git diff --check`, trailing whitespace와 stale 핵심 문자열 검사가 통과했다.
- 유지한 계약: Identity schema v1, `/internal/v1/eligibility/trial/events`, event 수신과 지급 분리, VPC Lattice AWS_IAM·SigV4 목표, 필수 공개 Idempotency-Key, `reserve → Session commit → confirm`, TrialClaim 3년 보존을 유지했다.
- 결정사항: 이전에 지적한 문서 계약 불일치는 해소됐고 검토 범위에서 새로운 차단 계약 오류는 확인하지 않았다. 과거 리뷰 기록은 당시 상태를 나타내며 최신 재검증이 이를 대체한다.
- 위험 요소: 문서가 정렬됐을 뿐 Identity SigV4·Retry-After adapter, Learning Core 필수 header·Reservation saga와 Billing PLAN-001 코드는 아직 구현 전이다. 통합 계약의 현재 구현 상태 문구만 읽으면 Identity publisher가 목표 transport까지 완료된 것으로 오해할 여지가 있어 Identity ADR의 미이관 주석을 함께 확인해야 한다.
- 다음 작업: Billing PLAN-001 consumer를 먼저 구현하고, staging 활성화 전 Identity SigV4·Retry-After와 앱·Learning Core 필수 header·Reservation saga를 별도 작업으로 구현·검증한다.

## 2026-08-27 — PLAN-001 Jira 작업 생성

<!-- codex-turn:jira-tmi-110-created -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: `TMI-110` — `[Billing] Trial eligibility event consumer 구현`
- 작업 목표: PLAN-001 Trial eligibility event consumer의 구현 범위, 제외 범위와 검증 가능한 완료 조건을 Jira 작업으로 등록한다.
- 변경 파일: Jira `TMI-110`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드와 계약·ADR·PLAN은 변경하지 않았다.
- 구현 내용: 중복 이슈가 없음을 확인한 뒤 `작업` 유형으로 이슈를 생성했다. endpoint, strict decoder, canonical digest, inbox·projection Transaction, 승인 index, 응답 계약, ingress mode, replica-set Testcontainers와 개인정보 비노출을 포함 범위와 완료 조건으로 기록했다. TrialClaim·grant·Reservation·AttemptGroup과 실제 AWS 배포는 제외 범위로 명시했다.
- 실행한 테스트와 결과: Jira와 작업 기록 문서만 변경해 Gradle 테스트는 실행하지 않았다. 문서 변경에 대해 `git diff --check`와 turn marker 단일 존재를 검증한다.
- 유지한 계약: Identity schema v1, `/internal/v1/eligibility/trial/events`, event 수신과 TrialClaim 지급 분리, 단일 Mongo Transaction, Lattice AWS_IAM·SigV4 목표와 PLAN-001 vertical slice 범위를 변경하지 않았다.
- 결정사항: 이슈는 `해야 할 일`, 담당자 미지정으로 생성했다. 실제 구현은 Jira `TMI-110`의 완료 조건과 PLAN-001을 기준으로 진행한다.
- 위험 요소: Billing은 아직 health-only 골격이고 PLAN-001 구현 전이다. Jira 생성만으로 Identity SigV4 adapter, Learning Core saga 또는 Lattice 인프라가 준비된 것은 아니다.
- 다음 작업: Jira `TMI-110`을 기준으로 PLAN-001 consumer를 구현하고 `./gradlew clean test` 및 replica-set Testcontainers 동시성 테스트를 실행한다.

## 2026-08-27 — TMI-110 Trial eligibility event consumer 구현

<!-- codex-turn:tmi-110-trial-eligibility-consumer-implemented -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: `TMI-110` — 구현 완료, Jira 상태·담당자 변경 없음
- 작업 목표: Identity schema v1 eligibility event를 strict하게 수신해 inbox 멱등성과 current binding revision high-water를 한 Mongo Transaction으로 반영하는 PLAN-001 vertical slice를 구현한다.
- 변경 파일: `build.gradle`, `.env.example`, `src/main/resources/application.yml`, Billing application/config/global Mongo·API, `trialeligibility` api/application/domain/infrastructure 전체, Identity contract fixture와 unit·MVC·security·replica-set integration test, `docs/plans/PLAN-001-trial-eligibility-event-consumer.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 구현 내용: 16 KiB bounded endpoint, duplicate field·trailing token·scalar coercion·unknown field 거절, exact event/schema/producer/scope·UUID/revision/time/candidate 검증, candidate/time 정규화 canonical SHA-256 digest를 구현했다. raw payload는 저장하지 않는다. inbox APPLIED/STALE와 verified/revoked projection을 단일 Transaction으로 반영하고 eventId/revision unique race, transient retry와 unknown commit 재확인을 duplicate/conflict/503으로 수렴시켰다.
- Mongo·보안: 승인된 5개 index의 name·key order·unique·partial·TTL option을 versioned initializer가 비교하고 불일치 시 fail-fast한다. transaction 요구 환경은 replica set capability를 확인하며 auto-index는 비활성화했다. internal ingress는 default disabled, test Identity role, Lattice AWS_IAM deployment guard로 분리했고 사용자 Bearer·shared secret·caller header를 추가하지 않았다.
- 실행한 테스트와 결과: `./gradlew clean test` 성공, 총 33개 테스트 통과. `mongo:7.0.14` Testcontainers replica set에서 index option·initializer 재실행·mismatch fail-fast, transaction rollback, verified/revoked·stale high-water, same event duplicate, same eventId/different payload, same revision/different event와 reverse revision concurrency를 실제 실행했다.
- 유지한 계약: `/internal/v1/eligibility/trial/events`, Identity wire field와 property order, body 없는 204와 400/409/422/503, raw phone/payload 비저장, event 수신과 혜택 지급 분리, 단일 Transaction, Lattice AWS_IAM·SG edge 권한 모델을 유지했다. TrialClaim·candidate alias·subject link·grant·ledger·Reservation·AttemptGroup은 추가하지 않았다.
- 결정사항: Docker Engine 29와 관리 중인 Testcontainers 1.20.4 호환을 위해 test worker API version을 1.44로 고정했다. 실제 Lattice principal/method/path 인증은 애플리케이션의 spoofable header가 아니라 ADR-002의 Lattice auth policy와 SG가 담당하며, 애플리케이션 lattice mode는 격리·transaction 필수 설정이 없으면 startup fail-closed한다.
- 위험 요소: 실제 AWS Lattice/IAM/SG와 Identity SigV4 publisher는 아직 배포·구현되지 않았으므로 production/staging route를 활성화하면 안 된다. Docker API 고정은 Testcontainers 또는 CI Docker 기준 변경 시 재검토가 필요하다. 저장소 전체가 아직 Git 미추적 상태이며 Jira는 여전히 `해야 할 일`이다.
- 다음 작업: 사용자가 코드와 문서를 검토해 기준선을 commit한 뒤, 별도 승인으로 Jira 상태를 갱신한다. 다음 제품 vertical slice는 current verified binding을 사용하는 TrialClaim·candidate alias·subject link·무료 grant/ledger·INITIAL Reservation Transaction이다.
