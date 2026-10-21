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

## 2026-08-27 — 로컬 main·develop 브랜치 생성

<!-- codex-turn:local-main-develop-branches-created -->

- 날짜: 2026-08-27
- 브랜치: `feat/TMI-110-trial-eligibility-event-consumer`
- Jira: `TMI-110`; Jira 변경 없음
- 작업 목표: feature 브랜치만 존재하는 Billing Git 저장소에 `main`과 `develop` 기준 브랜치를 만든다.
- 변경 파일: 로컬 Git refs `refs/heads/main`, `refs/heads/develop`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 구현 내용: 현재 feature 커밋 `e0694f9`를 가리키는 로컬 `main`과 `develop` 브랜치를 생성하고, 현재 checkout은 feature 브랜치로 유지했다.
- 실행한 테스트와 결과: Git branch metadata와 작업 기록만 변경해 Gradle 테스트는 실행하지 않았다. `git branch --verbose --no-abbrev`로 세 로컬 브랜치가 같은 커밋을 가리키는 것을 확인했다.
- 유지한 계약: 애플리케이션 코드, PLAN-001 API·Mongo·보안 계약과 Jira 내용은 변경하지 않았다.
- 결정사항: 별도 기준 커밋이 없으므로 현재 유일한 구현 커밋을 `main`과 `develop`의 시작점으로 사용했다.
- 위험 요소: Codex는 저장소 규칙상 push하지 않았으므로 원격에는 아직 feature 브랜치만 존재한다. 작업 기록 문서 변경은 현재 feature 브랜치 working tree에 미커밋 상태로 남는다.
- 다음 작업: 사용자가 작업 기록 변경을 commit한 뒤 `git push -u origin main`과 `git push -u origin develop`을 직접 실행하고 GitHub 기본 브랜치·보호 규칙을 설정한다.

## 2026-08-27 — GitHub 기본 브랜치를 main으로 변경

<!-- codex-turn:github-default-branch-main -->

- 날짜: 2026-08-27
- 브랜치: `feat/TMI-110-trial-eligibility-event-consumer`
- Jira: `TMI-110`; Jira 변경 없음
- 작업 목표: Billing GitHub 저장소의 기본 브랜치를 feature 브랜치가 아닌 `main`으로 설정한다.
- 변경 파일: GitHub 저장소 기본 브랜치 설정, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 구현 내용: 원격 `main`과 `develop`이 모두 커밋 `e0694f9`를 가리키는 것을 확인한 뒤 GitHub 기본 브랜치를 `feat/TMI-110-trial-eligibility-event-consumer`에서 `main`으로 변경했다.
- 실행한 테스트와 결과: 저장소 설정과 작업 기록만 변경해 Gradle 테스트는 실행하지 않았다. GitHub 조회 결과 `defaultBranchRef.name=main`을 확인했다.
- 유지한 계약: 애플리케이션 코드, PLAN-001 API·Mongo·보안 계약과 Git branch 내용은 변경하지 않았다.
- 결정사항: 장기 기준 브랜치는 `main`, 통합 브랜치는 `develop`, 기능 작업은 feature 브랜치에서 진행하는 구조를 사용한다.
- 위험 요소: branch protection과 pull request base 정책은 아직 확인·설정하지 않았다. 작업 기록 문서 변경은 현재 feature 브랜치 working tree에 미커밋 상태다.
- 다음 작업: 필요하면 별도 승인 후 `main`·`develop` branch protection과 PR 기본 흐름을 설정한다.

## 2026-08-27 — TMI-110 Jira 완료 처리

<!-- codex-turn:tmi-110-jira-completed -->

- 날짜: 2026-08-27
- 브랜치: `develop`
- Jira: `TMI-110` — `완료`
- 작업 목표: 구현·검증이 끝난 PLAN-001 Trial eligibility event consumer Jira 작업을 종료한다.
- 변경 파일: Jira `TMI-110` 상태, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 구현 내용: 사용자의 명시적 승인에 따라 Jira transition `41`을 적용해 `해야 할 일`에서 `완료`로 전환하고 완료 category를 재확인했다.
- 실행한 테스트와 결과: Jira 상태와 작업 기록만 변경해 Gradle 테스트는 다시 실행하지 않았다. 직전 구현 작업의 `./gradlew clean test` 33개 성공 결과를 완료 근거로 사용했다.
- 유지한 계약: Jira 설명, 담당자, 애플리케이션 코드, API·Mongo·보안 계약과 Git branch는 변경하지 않았다.
- 결정사항: 실제 AWS Lattice/IAM/SG와 Identity SigV4 adapter는 이 이슈의 승인된 제외 범위이므로 TMI-110 완료를 막지 않으며 별도 후속 이슈로 관리한다.
- 위험 요소: 작업 기록 문서 변경은 현재 feature 브랜치 working tree에 미커밋 상태다. 실제 staging/production 연동 완료를 의미하지 않는다.
- 다음 작업: 작업 기록을 commit한 뒤 다음 vertical slice인 TrialClaim·candidate alias·subject link·무료 grant/ledger·INITIAL Reservation Transaction을 별도 Jira로 정의한다.

## 2026-08-28 — 다음 구현 작업 분석

<!-- codex-turn:next-work-initial-reserve-analysis -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 완료된 `TMI-110` 참고; 신규 Jira 없음
- 작업 목표: PLAN-001 완료 뒤 계약상 다음 구현 단위와 선후관계를 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·계약은 변경하지 않았다.
- 분석 내용: 다음 권장 단위는 current verified binding을 사용하는 `FREE_EXAM_ONCE` INITIAL reserve vertical slice다. 첫 reserve Transaction에서 idempotency command, candidate alias dedupe, 필요한 TrialClaim·subject link·무료 grant와 `GRANTED` ledger, allocation hold·`RESERVED` ledger, Reservation과 proposed attempt session을 함께 생성해야 한다.
- 실행한 테스트와 결과: 설명과 작업 기록만 변경해 Gradle 테스트는 실행하지 않았다. PLAN-001 후속 작업, ADR-001 T2 reserve Transaction, CONTRACT_DECISIONS와 통합 계약을 대조했다.
- 유지한 계약: eligibility event 수신만으로 TrialClaim/grant를 만들지 않고, `reserve → Learning Core Session commit → confirm`, 3년 Claim 보존, raw phone 비저장과 append-only ledger를 유지한다.
- 결정사항: TrialClaim/grant만 선생성하는 slice는 만들지 않는다. 다음 구현 전에 `PLAN-002`와 Jira로 INITIAL reserve 범위와 완료 조건을 고정하고, 현재 구현 단위를 PLAN-001로 가리키는 `AGENTS.md`를 승인된 PLAN-002로 갱신하는 것이 필요하다.
- 위험 요소: reserve만 production에 활성화하고 confirm/cancel/expiry를 배포하지 않으면 hold가 정상 종료되지 않는다. 구현은 검토 가능한 단계로 나눠도 전체 command lifecycle과 Learning Core 연동 전까지 production caller를 활성화하지 않아야 한다. 현재 작업 기록 문서에는 기존 미커밋 변경도 함께 남아 있다.
- 다음 작업: 사용자 승인 시 `PLAN-002-free-exam-initial-reserve.md`를 작성하고 Jira를 생성한 뒤, Mongo document/index와 T2 reserve Transaction부터 구현한다.

## 2026-08-28 — PLAN-002 free exam initial reserve 계획서 작성

<!-- codex-turn:plan-002-free-exam-initial-reserve-drafted -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-110`을 선행 작업으로 참고
- 작업 목표: PLAN-001 다음 vertical slice인 무료 시험 reserve의 구현 범위, Transaction 경계, 저장 구조, 오류·보안·동시성 완료 조건을 구현 전에 고정한다.
- 변경 파일: `docs/plans/PLAN-002-free-exam-initial-reserve.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·외부 계약·Jira는 변경하지 않았다.
- 계획 내용: `POST /internal/v1/reservations`의 필수 lowercase UUID v4 key와 canonical payload hash, current VERIFIED binding과 expired alias fencing, 필요한 Claim·subject link·alias·grant·`GRANTED` ledger, INITIAL allocation hold·`RESERVED` ledger, Reservation·proposed Session과 response snapshot을 한 Mongo Transaction으로 처리하도록 설계했다. OPEN·RETAKE_AVAILABLE group은 REPLACEMENT로 기존 consumption을 재사용하고 GRADING·mockExamId 불일치·owner mismatch를 fail-closed한다.
- 실행한 테스트와 결과: 문서만 변경해 Gradle 테스트는 실행하지 않았다. ADR-001 T2·collection/index, PLAN-001 후속, CONTRACT_DECISIONS와 서비스 통합 계약을 대조했고 종료 전 `git diff --check`와 marker 단일 포함을 검증한다.
- 유지한 계약: eligibility event 수신과 무료권 지급 분리, verified-phone candidate 기준 3년 Claim dedupe, raw phone 비저장, append-only ledger, 5분 RESERVED hold, `reserve → Session commit → confirm`, REPLACEMENT 추가 차감 금지, VPC Lattice AWS_IAM·SigV4와 default deny를 유지한다.
- 결정사항: PLAN-002는 reserve endpoint 전체의 INITIAL·REPLACEMENT 판정까지만 포함한다. confirm/cancel/status·expiry·AttemptGroup event·reconciliation·실제 AWS/타 서비스 변경·결제는 제외하며 lifecycle 완성 전 production caller activation을 금지한다. 계획은 초안이고 신규 Jira는 사용자 승인 후 별도 동의를 받아 생성한다.
- 위험 요소: owner transfer wire 계약은 아직 없으므로 다른 user에 연결된 기존 Claim은 자동 이전하지 않고 insufficient로 차단한다. reserve 구현만 배포하면 hold를 정상 종료할 수 없으며 신규 index는 운영 data preflight와 별도 migration 검토가 필요하다. 기존 CURRENT_STATE·WORKLOG의 미커밋 변경은 보존했다.
- 다음 작업: 사용자가 PLAN-002를 검토·승인하면 Jira 생성 승인을 받아 작업을 만들고, `AGENTS.md`의 현재 구현 단위를 PLAN-002로 갱신한 뒤 구현을 시작한다.

## 2026-08-28 — reserve request의 sessionId·mockExamId 역할 설명

<!-- codex-turn:reserve-session-mock-exam-id-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음
- 작업 목표: PLAN-002 reserve request가 `sessionId`와 `mockExamId`를 Session commit 전에 받는 이유와 두 값의 수명 차이를 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 계획서·애플리케이션 코드·계약은 변경하지 않았다.
- 분석 내용: `sessionId`는 한 번의 proposed ExamSession을 Reservation·operation·confirm과 연결해 transport retry와 commit 증명을 같은 Session으로 수렴시키는 값이다. `mockExamId`는 최초 AttemptGroup에 문제지를 고정해 REPLACEMENT가 다른 시험으로 바뀌면서 consumption을 재사용하지 못하게 하는 값이다.
- 실행한 테스트와 결과: 설명·작업 기록만 변경해 Gradle 테스트는 실행하지 않았다. 종료 전 문서 whitespace와 `git diff --check`, marker 단일 포함을 검증한다.
- 유지한 계약: Learning Core가 Session·문제지를 소유하고 Billing은 opaque identifier만 저장·비교한다. reserve 전에 두 값을 선할당하고 `reserve → Session durable commit → confirm` 순서, same-key retry와 REPLACEMENT의 동일 `mockExamId`를 유지한다.
- 결정사항: 새 계약을 추가하지 않았다. `sessionId`는 Session마다 달라지고 `mockExamId`는 같은 AttemptGroup 동안 고정된다는 기존 계약을 재확인했다.
- 위험 요소: 두 값을 reserve 후에 임의 변경하면 같은 operation의 payload conflict 또는 replacement state conflict가 발생한다. Billing에 시험 내용이나 Learning Core 도메인 데이터를 복제해서는 안 된다.
- 다음 작업: PLAN-002 검토에서 두 식별자의 필요성이 승인되면 기존 request DTO를 유지하고 Jira 완료 조건에 포함한다.

## 2026-08-28 — reserve response 식별자와 상태 의미 설명

<!-- codex-turn:reserve-response-identifiers-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음
- 작업 목표: reserve 성공 response의 operation, Reservation, kind/status와 AttemptGroup 식별자가 각각 무엇을 나타내며 언제 바뀌는지 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 계획서·코드·외부 계약은 변경하지 않았다.
- 분석 내용: `operationId`는 앱부터 전달되는 한 번의 command 멱등성 ID, `reservationId`는 Billing hold aggregate ID, `reservationKind`는 INITIAL/REPLACEMENT 소비 방식, `reservationStatus`는 hold lifecycle 상태, `attemptGroupId`는 최초 응시와 restart가 공유하는 한 consumption 묶음 ID로 구분했다.
- 실행한 테스트와 결과: 설명과 기록 문서만 변경해 Gradle 테스트는 실행하지 않았다. 종료 전 `git diff --check`, whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: transport retry는 같은 operation·Reservation으로 수렴하고 의도적 restart는 새 operation·Reservation·Session을 쓰되 기존 AttemptGroup consumption과 mockExamId를 재사용한다. RESERVED는 최종 소비가 아니며 Session commit 후 confirm에서 확정한다.
- 결정사항: 새 계약을 추가하지 않았다. INITIAL reserve에서 AttemptGroup ID는 선할당하지만 durable OPEN group 전이는 confirm에서 수행한다는 기존 계약을 재확인했다.
- 위험 요소: operationId와 reservationId를 같은 개념으로 합치면 command replay와 hold lifecycle을 분리해 추적할 수 없고, restart마다 AttemptGroup을 새로 만들면 무료권이 중복 소비될 수 있다.
- 다음 작업: PLAN-002 승인 시 이 response DTO와 lifecycle을 Jira 완료 조건과 contract test에 그대로 포함한다.

## 2026-08-28 — PLAN-002 Jira 작업 생성

<!-- codex-turn:jira-tmi-112-created -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: `TMI-112` — `[Billing] Free exam initial reserve 구현` (`해야 할 일`, 담당자 미지정)
- 작업 목표: 승인된 PLAN-002 free exam initial reserve의 범위·제외 범위와 검증 가능한 완료 조건을 Jira 작업으로 등록한다.
- 변경 파일: Jira `TMI-112`, `docs/plans/PLAN-002-free-exam-initial-reserve.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·외부 계약은 변경하지 않았다.
- 구현 내용: TMI 프로젝트에서 initial reserve·TrialClaim·무료 시험 관련 중복 작업이 없음을 확인하고 `작업` 유형으로 이슈를 생성했다. endpoint, 필수 idempotency key, current eligibility와 alias dedupe, 필요한 Claim·subject link·무료 grant/ledger, INITIAL hold, REPLACEMENT 무추가차감, 단일 Transaction, index·동시성·security와 privacy 완료 조건을 기록했다.
- 실행한 테스트와 결과: Jira와 문서만 변경해 Gradle 테스트는 실행하지 않았다. Jira 생성 응답과 후속 조회로 key·summary·상태·담당자를 확인하고 종료 전 `git diff --check`, whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: event 수신과 지급 분리, candidate 기준 3년 Claim dedupe, raw phone 비저장, append-only ledger, 5분 RESERVED, `reserve → Session commit → confirm`, REPLACEMENT 추가 차감 금지와 VPC Lattice AWS_IAM·SigV4 default deny를 유지한다.
- 결정사항: 이슈는 `TMI-112`, 상태 `해야 할 일`, 담당자 미지정으로 생성했다. confirm/cancel/status·expiry, AttemptGroup event·reconciliation, 타 서비스·AWS 배포와 결제는 제외했고 lifecycle 완성 전 production caller 활성화를 금지했다.
- 위험 요소: PLAN-002는 reserve만 구현하므로 후속 lifecycle 없이 운영에서 활성화하면 hold를 정상 확정·해제할 수 없다. owner transfer 계약이 없어 다른 user에 연결된 Claim은 자동 이전하지 않는다. 기존 작업 기록의 미커밋 변경은 보존했다.
- 다음 작업: 구현 시작 전에 `AGENTS.md`의 현재 구현 단위를 승인된 PLAN-002와 Jira `TMI-112`로 갱신하고, 이슈 완료 조건을 기준으로 document/index와 T2 reserve Transaction부터 구현한다.

## 2026-08-28 — TMI-112 PLAN-002 free exam initial reserve 구현

<!-- codex-turn:tmi-112-free-exam-initial-reserve-implemented -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: `TMI-112` — 구현 완료, Jira 상태 `해야 할 일`·담당자 미지정 유지
- 작업 목표: current verified-phone eligibility를 기준으로 필요한 Claim·무료 grant와 INITIAL entitlement hold 또는 REPLACEMENT authorization을 한 Mongo Transaction으로 처리하는 PLAN-002 reserve vertical slice를 구현한다.
- 변경 파일: `.env.example`, `AGENTS.md`, application config·error/security·Mongo index initializer, `reservation` api/application/domain/infrastructure 전체, reserve unit·MVC·replica-set integration test, PLAN-002와 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. Identity·Learning Core 저장소와 Jira 상태는 변경하지 않았다.
- 구현 내용: 16 KiB strict request decoder, lowercase UUID v4 idempotency key, canonical payload hash와 direct internal response를 추가했다. T2 Transaction은 command claim, VERIFIED binding, expired alias fencing·key rotation dedupe, 필요한 TrialClaim·subject link·aliases·free grant·GRANTED ledger, INITIAL grant hold·allocation·RESERVED ledger, 5분 Reservation·PROPOSED Session과 response snapshot을 원자적으로 저장한다. REPLACEMENT는 기존 OPEN·RETAKE_AVAILABLE group consumption과 mockExamId를 재사용하고 이전 active Session을 fencing하며 추가 entitlement allocation/ledger를 만들지 않는다.
- Mongo·보안: initializer schema를 v2로 올리고 reserve 관련 10개 collection과 ADR-001의 23개 index를 명시적으로 생성·option 비교한다. candidate, grant source, ledger dedupe/sequence, active command·Reservation·group·Session을 unique/partial unique index로 보장한다. test ingress는 Identity eligibility와 Learning Core reserve route를 분리하고 default disabled·Lattice deployment guard를 유지한다.
- 실행한 테스트와 결과: `./gradlew clean test` 성공, 총 58개 테스트 통과, 실패·skip 0. `mongo:7.0.14` replica-set Testcontainers에서 initial multi-document atomicity, rollback, same-key replay/different payload conflict, 같은 candidate의 다른 user와 같은 user의 다른 operation race, key rotation alias 보강, expired alias fencing, REPLACEMENT 무추가차감, GRADING/mock mismatch, transient transaction retry와 unknown commit snapshot 수렴을 실행했다.
- 유지한 계약: eligibility event 수신과 무료권 지급 분리, raw phone 비수신·비저장, candidate 기준 Claim 3년 dedupe, immutable claimedAt/retentionExpiresAt, append-only GRANTED·RESERVED ledger, 5분 RESERVED, `reserve → Session durable commit → confirm`, REPLACEMENT 동일 consumption/mockExamId, VPC Lattice AWS_IAM·SigV4와 fail-closed를 유지했다.
- 결정사항: AGENTS의 현재 구현 단위를 PLAN-002/TMI-112로 전환했다. Mongo schema version은 reserve index 확장을 나타내는 v2로 올렸고 runtime index drop/recreate는 허용하지 않는다. 동시 active command race의 최종 retry 뒤 기존 active command를 재확인해 503이 아닌 `COMMAND_PROCESSING`으로 수렴시켰다.
- 위험 요소: confirm/cancel/status와 expiry worker가 아직 없어 이 코드를 production caller에 활성화하면 hold를 정상 확정·해제할 수 없다. 실제 Lattice/IAM/SG, Learning Core saga, Identity SigV4와 staging E2E도 남아 있다. owner transfer wire 계약은 없으므로 다른 user의 기존 Claim은 자동 이전하지 않는다. schema v2 index는 배포 전 staging preflight와 운영 migration 검토가 필요하다.
- 다음 작업: 사용자가 구현을 검토한 뒤 별도 승인으로 Jira `TMI-112` 상태를 갱신한다. 다음 제품 vertical slice는 confirm/cancel/status·5분 expiry lifecycle이며, 그 전까지 production reserve route를 열지 않는다.

## 2026-08-28 — TMI-112 Jira 완료 처리

<!-- codex-turn:tmi-112-jira-completed -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: `TMI-112` — `완료`
- 작업 목표: 구현과 검증이 끝난 PLAN-002 Free exam initial reserve Jira 작업을 사용자 승인에 따라 종료한다.
- 변경 파일: Jira `TMI-112` 상태, `docs/plans/PLAN-002-free-exam-initial-reserve.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드와 API·Mongo·보안 계약은 변경하지 않았다.
- 구현 내용: Jira의 사용 가능한 transition을 확인한 뒤 transition `41`을 적용해 `해야 할 일`에서 `완료`로 전환하고 done status category를 재조회했다. 설명과 담당자는 변경하지 않았다.
- 실행한 테스트와 결과: Jira 상태와 기록 문서만 변경해 Gradle 테스트는 다시 실행하지 않았다. 직전 PLAN-002 구현의 `./gradlew clean test` 총 58개 성공, 실패·skip 0 결과를 완료 근거로 사용했다. 종료 전 `git diff --check`, whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: event 수신과 지급 분리, candidate 기준 Claim 3년 dedupe, append-only ledger, 5분 RESERVED, REPLACEMENT 무추가차감, `reserve → Session commit → confirm`, Lattice AWS_IAM·SigV4와 production gate를 변경하지 않았다.
- 결정사항: `TMI-112`는 완료 category이며 담당자는 미지정으로 유지한다. Jira 완료는 reserve vertical slice의 코드 완료를 뜻하고 production 서비스 연동 완료를 뜻하지 않는다.
- 위험 요소: confirm/cancel/status·expiry worker, Learning Core saga, 실제 Lattice/IAM/SG와 staging E2E가 아직 없어 reserve route를 production에 활성화하면 안 된다. 코드·문서 변경은 아직 commit/push되지 않았다.
- 다음 작업: 다음 vertical slice로 confirm/cancel/status·5분 expiry lifecycle 계획과 Jira를 확정한다.

## 2026-08-28 — 다음 Reservation lifecycle 작업 설명

<!-- codex-turn:next-reservation-lifecycle-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-112`를 선행 작업으로 참고
- 작업 목표: PLAN-002 initial reserve 다음에 구현할 Reservation lifecycle의 목적, 범위와 후속 작업 경계를 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·PLAN·Jira는 변경하지 않았다.
- 분석 내용: reserve가 만든 임시 hold를 Session durable commit 성공 시 confirm으로 최종 소비하고, commit 실패 시 cancel, 5분 무응답 시 expiry로 해제하며, 응답 유실 시 status로 실제 상태를 확인하는 흐름을 다음 vertical slice로 정리했다. INITIAL confirm은 consume·ledger·AttemptGroup OPEN·Session ACTIVE를, INITIAL cancel/expiry는 원 grant 복원·RELEASED ledger를 하나의 Transaction에서 처리한다. REPLACEMENT는 모든 종료 경로에서 기존 consumption을 유지한다.
- 실행한 테스트와 결과: 설명과 기록 문서만 변경해 Gradle 테스트는 다시 실행하지 않았다. 직전 PLAN-002의 `./gradlew clean test` 총 58개 성공, 실패·skip 0 결과를 기준 상태로 유지하며 종료 전 `git diff --check`, whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: `reserve → Learning Core Session durable commit → confirm`, 5분 RESERVED, append-only ledger, cancel/expiry 시 TrialClaim 유지, REPLACEMENT 추가 차감 금지, CONFIRMED 일반 cancel 금지, CANCELED/EXPIRED 자동 repair-confirm 금지와 production caller activation gate를 유지한다.
- 결정사항: 다음 계획은 confirm·cancel·status·expiry를 하나의 PLAN-003으로 묶는 것을 권장한다. 네 기능이 같은 Reservation 상태 머신과 confirm/cancel/expiry race를 공유하기 때문이다. AttemptGroup 상태 event·reconciliation, Learning Core saga와 실제 AWS 연동은 후속 단위로 분리한다.
- 위험 요소: lifecycle 없이 reserve를 운영 활성화하면 hold가 확정되거나 복구되지 않는다. confirm과 expiry 경쟁을 단순 조회 후 저장으로 구현하면 이중 terminal 전이 또는 잘못된 grant 복원이 발생할 수 있으므로 CAS·Transaction·unique constraint와 동시성 검증이 필요하다.
- 다음 작업: 사용자가 범위를 승인하면 `docs/plans/PLAN-003-reservation-lifecycle.md`를 작성한다. 계획 승인 뒤 별도 사용자 승인으로 Jira를 생성하고, 이후 구현한다.

## 2026-08-28 — PLAN-003 Reservation lifecycle 계획서 작성

<!-- codex-turn:plan-003-reservation-lifecycle-drafted -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-112`를 선행 작업으로 참고
- 작업 목표: PLAN-002가 만든 RESERVED hold를 confirm, cancel, status와 5분 expiry로 안전하게 종료하는 다음 vertical slice의 구현·검증 계획을 고정한다.
- 변경 파일: `docs/plans/PLAN-003-reservation-lifecycle.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·Jira·타 저장소는 변경하지 않았다.
- 계획 내용: confirm/cancel strict decoder와 canonical hash, INITIAL consume·AttemptGroup OPEN·Session ACTIVE, INITIAL cancel/expiry 원 grant 복원·RELEASED ledger, REPLACEMENT 무추가차감, read-only status, terminal command 7일 보존과 configurable expiry worker를 정의했다. Reservation expected-state/version CAS를 첫 write로 두고 confirm/cancel/expiry race에서 terminal 상태와 ledger 하나만 commit하도록 Transaction 경계를 고정했다.
- 실행한 테스트와 결과: 문서만 변경해 Gradle 테스트는 다시 실행하지 않았다. ADR-001 T3/T4·상태 머신·DTO·index, 통합 계약과 실제 PLAN-002 document/repository 구조를 대조했다. 종료 전 `git diff --check`, whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: confirm은 Summary 완료가 아니라 Session durable commit 직후, cancel/expiry는 TrialClaim 불변, CONFIRMED 일반 cancel 금지, CANCELED/EXPIRED 자동 repair-confirm 금지, REPLACEMENT 기존 consumption 재사용, append-only ledger와 VPC Lattice AWS_IAM 경계를 유지한다.
- 결정사항: 네 기능은 하나의 PLAN-003으로 묶고 AttemptGroup 상태 event·reconciliation·Learning Core saga·실제 AWS 배포는 후속으로 분리했다. existing schema v2 index를 재사용하며 새 index가 필요하면 구현 전에 영향과 migration을 다시 보고한다. expiry scheduler 최초 제안은 명시적 enable, 10초 scan, batch 100이다.
- 위험 요소: confirm/cancel/expiry CAS 결과를 확인하지 않으면 이중 terminal 전이와 잘못된 grant 복원이 가능하다. worker가 비활성인 채 reserve caller를 열면 hold가 누적된다. PLAN-003 완료만으로 production 연동을 승인하지 않고 Learning Core saga·Lattice staging E2E gate를 유지한다.
- 다음 작업: 사용자가 PLAN-003을 검토·승인하면 별도 승인으로 Jira를 생성한다. 이후 AGENTS의 현재 구현 단위를 PLAN-003/Jira로 갱신하고 구현을 시작한다.

## 2026-08-28 — 무료권 소비 확정과 Summary 완료 시점 재설명

<!-- codex-turn:consumption-confirm-vs-summary-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-112` 참고
- 작업 목표: reserve의 hold, Session commit 뒤 confirm의 consumption과 Summary 조회 가능 뒤 AttemptGroup 완료가 서로 다른 시점임을 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. PLAN-003·ADR·애플리케이션 코드·Jira는 변경하지 않았다.
- 분석 내용: reserve는 동시 사용을 막는 임시 HELD이고, 현재 계약의 confirm은 Learning Core Session durable commit 직후 무료 unit을 최종 CONSUMED로 전환하며 AttemptGroup을 OPEN으로 연다. 필수 피드백·유효 점수·Summary 조회 가능은 이후 AttemptGroup COMPLETED 조건이다. 결과 생성이 최종 실패하면 consumption을 되돌리지 않고 RETAKE_AVAILABLE로 전환해 같은 consumption과 mockExamId로 replacement Session을 허용한다.
- 실행한 테스트와 결과: 설명과 기록 문서만 변경해 Gradle 테스트는 실행하지 않았다. AGENTS, CONTRACT_DECISIONS, ADR-001, 서비스 통합 계약과 PLAN-003의 시점을 대조했고 종료 전 `git diff --check`, whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: `reserve → Session durable commit → confirm`, confirm 뒤 일반 cancel 금지, Summary 조회 가능 뒤 COMPLETED, 최종 결과 실패 시 같은 consumption의 RETAKE_AVAILABLE을 유지한다.
- 결정사항: 새 결정을 추가하지 않았다. confirm은 시험 결과 완료 확정이 아니라 시험 1회 시작에 대한 entitlement 소비 확정이라는 기존 의미를 재확인했다.
- 위험 요소: consumption을 Summary 완료까지 미루면 장시간 hold, worker expiry와 실제 진행 Session 충돌, 동시 시험 생성과 결과 실패 시 과도한 무료 재수급 문제가 생겨 Reservation TTL·AttemptGroup·reconciliation 계약 전체를 다시 설계해야 한다.
- 다음 작업: 사용자가 기존 시점을 유지하면 PLAN-003 승인·Jira 생성 순서로 진행한다. Summary 완료 시점으로 변경하려면 먼저 계약 변경의 장단점과 migration 범위를 별도로 확정한다.

## 2026-08-28 — 같은 consumption 재응시 처리 설명

<!-- codex-turn:same-consumption-retake-flow-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-112` 참고
- 작업 목표: 결과 생성 최종 실패 시 무료 grant를 복원하지 않고 같은 consumption으로 재응시시키는 AttemptGroup·REPLACEMENT 처리와 현재 구현 상태를 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·PLAN·ADR·Jira는 변경하지 않았다.
- 분석 내용: INITIAL confirm의 단일 CONSUMED ledger를 AttemptGroup에 고정하고, 최종 결과 실패 event에서 group을 RETAKE_AVAILABLE로 만든 뒤 새 operation/session의 reserve를 REPLACEMENT로 판정한다. REPLACEMENT는 같은 attemptGroupId·mockExamId·consumption을 재사용하고 새 Claim·grant·allocation·entitlement ledger 없이 Reservation과 Session만 교체한다.
- 현재 구현 확인: PLAN-002 `ReserveService`는 OPEN·RETAKE_AVAILABLE group을 REPLACEMENT로 판정하고 allocation/RESERVED ledger를 생성하지 않으며 이전 active Session을 ABANDONED_RESTARTED로 fencing한 뒤 새 PROPOSED Session을 만든다. INITIAL/REPLACEMENT confirm은 PLAN-003에 계획만 있고, GRADING·RETAKE_AVAILABLE event consumer는 PLAN-003 후속이라 end-to-end는 아직 미완성이다.
- 실행한 테스트와 결과: 설명과 기록 문서만 변경해 Gradle 테스트는 실행하지 않았다. 실제 ReserveService·AttemptGroup·AttemptSession 코드와 ADR-001·통합 계약·PLAN-002·PLAN-003을 대조했고 종료 전 `git diff --check`, whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: restart는 새 operationId·새 sessionId를 사용하되 동일 AttemptGroup consumption과 mockExamId를 유지하고 결과·upload·Job·Summary를 승계하지 않는다. GRADING 중에는 새 Session을 막고 최종 실패에서만 RETAKE_AVAILABLE을 허용한다.
- 결정사항: 새 계약이나 구현을 추가하지 않았다. 무료권 복원과 same-consumption replacement가 서로 다른 경로임을 재확인했다.
- 위험 요소: 일시적 지연을 최종 실패로 오판해 RETAKE_AVAILABLE로 열면 active Session이 중복될 수 있다. event consumer는 active Session fencing·event 멱등성과 허용 상태 전이를 Transaction으로 보장해야 한다.
- 다음 작업: PLAN-003 승인·Jira·구현 뒤 별도 계획으로 AttemptGroup 상태 event consumer를 작성하고 Learning Core saga와 함께 E2E 검증한다.

## 2026-08-28 — 탈퇴·재가입 시 미완료 AttemptGroup 재응시 확인

<!-- codex-turn:withdraw-rejoin-retake-eligibility-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-112` 참고
- 작업 목표: 탈퇴 후 재가입한 사용자가 결과 실패·미완료 AttemptGroup의 same-consumption 재응시를 할 수 있는지 Identity userId와 Billing owner 계약을 함께 확인한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. Billing·Identity 애플리케이션 코드, PLAN·ADR·Jira는 변경하지 않았다.
- 분석 내용: Identity 현재 계약과 구현 기록에서 재가입은 old account 복구가 아니라 새 UUID 발급임을 확인했다. Billing은 3년 Claim dedupe와 old userId subject link를 유지하며 새 userId가 같은 candidate로 접근하면 owner mismatch로 `ENTITLEMENT_INSUFFICIENT`를 반환한다. 따라서 기존 OPEN·RETAKE_AVAILABLE group은 자동 승계되지 않는다.
- 상태별 의미: 동일 owner라면 OPEN·RETAKE_AVAILABLE은 REPLACEMENT를 허용하고 GRADING은 최종 복구 판단 전이므로 `COMMAND_PROCESSING`으로 막는다. 하지만 실제 탈퇴·재가입은 새 UUID이므로 동일 owner 조건을 만족하지 않는다.
- 실행한 테스트와 결과: 코드 변경이 없는 분석이라 Gradle 테스트는 실행하지 않았다. Billing ReserveService owner mismatch, AGENTS·ADR-001·통합 계약·PLAN-002와 Identity의 withdrawal/rejoin 계약·구현 기록을 읽기 전용으로 대조했고 종료 전 `git diff --check`, whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: 탈퇴·재가입으로 Claim을 삭제·재개방하지 않고 3년 동안 phone당 재수급을 차단한다. 다른 userId로 자동 owner 이전하지 않으며 REPLACEMENT는 현재 Claim owner의 같은 consumption에만 허용한다.
- 결정사항: 새 정책을 확정하지 않았다. 현행 동작은 새 UUID 재가입자의 자동 재응시 차단이다. 허용하려면 authenticated owner-transfer/rejoin event와 원자적 ownership 이전 계약을 별도로 승인해야 한다.
- 위험 요소: phone candidate 일치만으로 기존 AttemptGroup을 새 userId에 넘기면 재할당 전화번호나 계정 탈취 상황에서 이전 사용자의 entitlement·시험 연결이 노출될 수 있다. 반대로 이전 계약이 없으면 정상 재가입 사용자는 남은 retake를 이용하지 못한다.
- 다음 작업: 현행 차단 정책을 유지하면 PLAN-003 범위는 변경하지 않는다. 재가입 승계를 원하면 PLAN-003 구현 전에 owner transfer를 선행 또는 별도 후속으로 둘지 계약을 확정한다.

## 2026-08-28 — 재가입 사용자의 기존 무료시험 권리 승계 공백 분석

<!-- codex-turn:rejoin-existing-trial-right-transfer-proposed -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-112` 참고
- 작업 목표: phone당 1회 정책을 유지하면서 탈퇴·재가입 사용자가 미사용 또는 재응시 가능한 기존 권리를 이용하도록 만드는 정책 방향을 정리한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 계약·PLAN·애플리케이션 코드·Jira는 변경하지 않았다.
- 분석 내용: 현행 owner mismatch는 새 무료권 중복 발급을 막지만 Identity가 재가입에 새 UUID를 발급하므로 기존 Claim의 미사용 unit과 RETAKE_AVAILABLE consumption도 이용하지 못하게 한다. 제품 요구에 맞는 해결은 새 Claim/grant가 아니라 기존 Claim·grant·consumption과 immutable retention clock을 유지하고 subject link owner만 인증된 새 userId로 이전하는 것이다.
- 권장 조건: new user의 current VERIFIED candidate와 기존 alias 일치, old owner의 Identity WITHDRAWN/이전 가능 증거, active RESERVED·GRADING race 부재, current active owner가 아닌 경우를 한 authenticated event와 Mongo Transaction으로 확인한다. 미사용 grant는 같은 Claim으로 INITIAL, OPEN·RETAKE_AVAILABLE은 같은 consumption의 REPLACEMENT를 허용하고 COMPLETED에는 새 무료권을 주지 않는다.
- 개인정보 경계: old Learning Core Session·답안·upload·결과·Summary를 새 계정에 넘기지 않고 old Session을 fencing한 뒤 새 Session에서 시작한다. phone candidate 일치가 entitlement owner transfer 근거가 되더라도 학습 데이터 접근권 이전 근거로 사용하지 않는다.
- 실행한 테스트와 결과: 정책 분석과 기록 문서만 변경해 Gradle 테스트는 실행하지 않았다. 현행 owner mismatch, Identity 새 UUID 재가입, TrialClaim 3년 dedupe, AttemptGroup REPLACEMENT 계약을 대조했고 종료 전 `git diff --check`, whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: raw phone 비저장, phone당 Claim 하나, `claimedAt + 3년` 불변, 중복 grant 금지와 기존 consumption 재사용을 유지한다.
- 결정사항: 권장 owner transfer 방향을 제안했지만 사용자 승인 전 계약으로 확정하지 않았다. PLAN-003 lifecycle에 임의로 섞지 않는다.
- 위험 요소: old owner 상태를 검증하지 않고 candidate 일치만으로 이전하면 활성 계정 탈취와 전화번호 재할당 위험이 있다. 반대로 transfer를 구현하지 않으면 정상 재가입자의 남은 무료시험 권리가 영구 차단된다.
- 다음 작업: 사용자가 권장안을 승인하면 transfer 대상 상태, Identity event, old data 비승계와 PLAN-003 대비 구현 순서를 계약 문서에 확정한 뒤 별도 계획/Jira로 진행한다.

## 2026-08-28 — Identity phone uniqueness와 Billing owner transfer 전제 정정

<!-- codex-turn:identity-phone-uniqueness-transfer-premise-corrected -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-112` 참고
- 작업 목표: mandatory phone verification에서 동일 번호의 활성 계정이 둘 존재할 수 있는지 확인하고 재가입 owner transfer의 정확한 전제를 정리한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. Billing·Identity 코드, 계약·PLAN·Jira는 변경하지 않았다.
- 확인 내용: Identity ADR·가입 계획과 실제 FirebaseSignupService·PhoneIdentityTransactionService는 active retained fingerprint 충돌을 `PHONE_ALREADY_LINKED`로 거절한다. 한 verified phone은 동시에 한 Firebase User와 한 ACTIVE MEMBER만 소유하며, 탈퇴 lifecycle이 PhoneIdentity alias/Firebase 점유를 해제한 뒤에만 새 UUID 가입이 가능하다.
- 정정 내용: 앞서 제안한 “기존 활성 계정이면 이전 금지”는 정상 가입에서 가능한 제품 분기가 아니라 uniqueness drift, withdrawal 부분 완료와 cross-user eligibility event 순서를 방어하는 fail-closed invariant다. Billing의 old subject link는 활성 Identity owner가 아니라 3년 dedupe를 위해 남은 retained mapping이다.
- 권장 transfer 전제: old user eligibility REVOKED, new user eligibility VERIFIED, 같은 retained candidate와 active Claim 일치가 확인된 뒤 retained subject link owner만 원자적으로 변경한다. 두 event가 아직 수렴하지 않았으면 자동 추정하지 않고 processing/reconciliation으로 보낸다.
- 실행한 테스트와 결과: 읽기 전용 분석과 기록 문서만 변경해 Gradle 테스트는 실행하지 않았다. Identity 문서·가입/PhoneIdentity 코드·테스트와 Billing owner mismatch 계약을 대조했고 종료 전 `git diff --check`, whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: phone uniqueness, 새 UUID 재가입, TrialClaim 3년 dedupe, raw phone 비저장과 새 무료 Claim/grant 중복 지급 금지를 유지한다.
- 결정사항: 정책을 새로 확정하지 않았다. owner transfer가 두 활성 계정 사이의 이전이라는 잘못된 인상을 정정했다.
- 위험 요소: Billing이 new VERIFIED만 보고 old REVOKED 수렴 전에 이전하면 event 순서 역전·부분 withdrawal에서 잘못된 owner 변경이 생길 수 있다. 반대로 과거 subject link를 활성 계정 점유로 오해하면 정상 재가입을 영구 차단한다.
- 다음 작업: 권장 owner rebind를 승인할 경우 existing verified/revoked event 조합으로 충분한지 dedicated transfer event가 필요한지 계약 단계에서 확정한다.

## 2026-08-28 — PLAN-003 이후 재가입 권리 승계 구현 순서 확인

<!-- codex-turn:plan-003-before-rejoin-transfer-sequence-confirmed -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-112` 참고
- 작업 목표: Reservation lifecycle과 재가입 무료시험 권리 승계의 구현 의존성과 권장 순서를 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·PLAN·ADR·Jira는 변경하지 않았다.
- 분석 내용: owner rebind가 미사용 grant, HELD Reservation, CONFIRMED consumption과 terminal 상태를 안전하게 구분하려면 PLAN-003 confirm/cancel/status/expiry와 CAS가 먼저 필요하다. 실패 재응시는 AttemptGroup의 GRADING·COMPLETED·RETAKE_AVAILABLE 상태가 신뢰 가능해야 하므로 상태 event consumer를 owner rebind보다 먼저 두는 것이 완전한 E2E 순서다.
- 권장 순서: PLAN-003 lifecycle, AttemptGroup 상태 event consumer, 재가입 retained subject owner rebind, Learning Core saga·Lattice staging E2E와 production migration 순이다.
- 실행한 테스트와 결과: 설명·기록 문서만 변경해 Gradle 테스트는 실행하지 않았다. PLAN-003 포함·제외 범위, ADR-001 상태 머신과 재가입 owner transfer 제안을 대조했고 종료 전 `git diff --check`, whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: 새 Claim/grant 중복 지급 금지, phone당 1회, old data 비승계, same consumption REPLACEMENT와 terminal 상태 불변을 유지한다.
- 결정사항: 이 순서는 권장안으로 정리했으며 새 PLAN 번호·Jira·wire event는 아직 확정하거나 생성하지 않았다.
- 위험 요소: owner rebind를 lifecycle보다 먼저 구현하면 active hold·Session을 새 owner에게 잘못 넘길 수 있고, AttemptGroup event 없이 구현하면 최종 실패와 처리 중 상태를 구분하지 못해 재응시를 잘못 열 수 있다.
- 다음 작업: 사용자가 PLAN-003 계획을 승인하면 별도 승인으로 Jira를 생성하고 PLAN-003을 구현한다. 이후 AttemptGroup event와 owner rebind는 각각 별도 계획·Jira로 진행한다.

## 2026-08-28 — PLAN-005 이후 남은 출시 작업 구분

<!-- codex-turn:post-plan-005-release-work-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-112` 참고
- 작업 목표: 재가입 owner rebind PLAN-005가 무료시험 프로젝트와 production 출시의 마지막 작업인지 범위를 구분한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·PLAN·ADR·Jira는 변경하지 않았다.
- 분석 내용: PLAN-005까지 phone당 무료 1회 Billing 핵심 정책은 완성되지만 실제 사용자 트래픽에는 Learning Core saga/outbox/reconciliation, Identity SigV4 delivery와 owner transfer producer 계약, Lattice/IAM/SG/ECS, staging E2E, production Mongo migration·worker·alert·rollout이 추가로 필요하다. 3년 Claim purge와 backup restore purge도 운영 vertical slice로 남는다.
- 실행한 테스트와 결과: 설명·기록 문서만 변경해 Gradle 테스트는 실행하지 않았다. PLAN-002 production gate, PLAN-003 후속 작업, ADR-002 E2E와 TrialClaim purge 계약을 대조했고 종료 전 `git diff --check`, whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: lifecycle·AttemptGroup·owner rebind 구현만으로 production caller를 열지 않고 cross-service·AWS negative/E2E gate를 통과한다. Claim 만료 daily purge와 backup 복구 전 purge 의무를 유지한다.
- 결정사항: PLAN-005를 Billing 핵심 제품 정책의 마지막으로 설명하되 전체 출시의 마지막으로 보지 않는다. 후속 PLAN 번호와 Jira는 아직 정하지 않았다.
- 위험 요소: 기능 코드 완료를 출시 완료로 오해하면 direct bypass, confirm 불명, disabled expiry worker, index drift와 미구현 purge가 운영 장애·개인정보 정책 위반으로 이어질 수 있다.
- 다음 작업: 우선 PLAN-003 승인·Jira·구현을 진행하고, 각 후속 단계에서 별도 계획과 출시 gate를 순차 확정한다.

## 2026-08-28 — PLAN-003 Jira 작업 생성

<!-- codex-turn:jira-tmi-113-created -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: `TMI-113` — `[Billing] Reservation lifecycle 구현` (`해야 할 일`, 담당자 미지정)
- 작업 목표: 승인된 PLAN-003 Reservation lifecycle의 범위·제외 범위와 검증 가능한 완료 조건을 Jira 작업으로 등록한다.
- 변경 파일: Jira `TMI-113`, `docs/plans/PLAN-003-reservation-lifecycle.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·AGENTS·타 저장소는 변경하지 않았다.
- Jira 내용: confirm/cancel/status endpoint, strict decode·canonical hash, INITIAL consume·release, REPLACEMENT 기존 consumption 유지, read-only status, terminal command 7일 보존, configurable expiry worker, Reservation CAS·Transaction race와 replica-set 동시성·security·privacy 완료 조건을 기록했다.
- 제외 범위: AttemptGroup 상태 event, 재가입 owner rebind, repair, Learning Core saga/reconciliation, Identity publisher, 실제 Lattice/IAM/SG, Claim purge·backup과 결제를 분리하고 production caller activation gate를 명시했다.
- 실행한 테스트와 결과: Jira·문서만 변경해 Gradle 테스트는 실행하지 않았다. Jira 생성 전 TMI 프로젝트에서 Reservation lifecycle 중복 작업이 없음을 확인하고, 생성 후 key·summary·작업 유형·상태·담당자와 본문을 재조회했다. 종료 전 `git diff --check`, whitespace와 marker 단일 포함을 검증한다.
- 유지한 계약: `reserve → Session durable commit → confirm`, Summary와 confirm 분리, terminal 상태 불변, cancel/expiry TrialClaim 불변, REPLACEMENT 무추가차감, append-only ledger와 production gate를 유지한다.
- 결정사항: 이슈 키는 `TMI-113`, 유형 `작업`, 상태 `해야 할 일`, 담당자 미지정이다. PLAN-003은 승인·Jira 생성·구현 전 상태로 갱신했다.
- 위험 요소: lifecycle만 구현하고 production route를 열면 AttemptGroup 상태 수렴·Learning Core confirm 불명·AWS 우회 차단이 완성되지 않는다. 구현 시 CAS modified result를 확인하지 않으면 이중 consume/release가 생길 수 있다.
- 다음 작업: 구현 시작 시 Jira `TMI-113` 완료 조건을 읽고 AGENTS의 현재 구현 단위를 PLAN-003/TMI-113으로 전환한 뒤 confirm/cancel/status·expiry를 구현한다.

## 2026-08-28 — TMI-113 PLAN-003 Reservation lifecycle 구현 완료

<!-- codex-turn:tmi-113-reservation-lifecycle-implemented -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: `TMI-113` — `[Billing] Reservation lifecycle 구현` (`해야 할 일`, 담당자 미지정)
- 작업 목표: PLAN-002의 RESERVED hold를 Session commit 뒤 최종 소비하거나 cancel·5분 expiry로 복원하고, confirm 응답 불명 시 read-only status로 조회할 수 있는 PLAN-003 vertical slice를 구현한다.
- 변경 파일: `AGENTS.md`, `docs/plans/PLAN-003-reservation-lifecycle.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`, Reservation 설정·security·controller·예외 처리, lifecycle DTO·decoder·hash·service·worker·metric, Reservation 관련 domain/repository와 단위·MVC·replica-set 통합 테스트. Identity와 Learning Core 저장소, ADR, Jira 상태와 AWS 리소스는 변경하지 않았다.
- 구현 동작: confirm/cancel/status endpoint에 16 KiB strict decoder, lowercase UUID v4, UTC millisecond timestamp·enum 검증과 command별 canonical SHA-256 hash를 적용했다. status는 user·operation의 RESERVE command와 live Reservation·AttemptGroup을 읽고 아무 document도 쓰지 않는다.
- INITIAL 처리: confirm은 Reservation CAS 승리 뒤 allocation HELD→CONSUMED, grant held→consumed, `CONSUMED:<reservationId>` ledger, AttemptGroup OPEN과 Session ACTIVE를 한 Transaction으로 commit한다. cancel·expiry는 allocation HELD→RELEASED, grant held→available, 단일 `RELEASED:<reservationId>` ledger와 Session FAILED를 같은 Transaction으로 반영한다.
- REPLACEMENT 처리: confirm은 기존 AttemptGroup consumption과 mockExamId를 검증하고 OPEN·RETAKE_AVAILABLE을 OPEN으로 수렴해 새 Session만 ACTIVE로 연결한다. cancel·expiry는 Reservation과 proposed Session만 terminal 처리하고 Claim·grant·allocation·ledger·기존 consumption을 변경하지 않는다.
- race·멱등성: confirm·cancel·expiry 모두 `reservationId + RESERVED + activeGuard + expected version` CAS를 첫 write로 사용한다. command type별 unique key와 canonical hash, stored snapshot으로 same-payload replay·different-payload conflict·unknown commit을 수렴한다. reserve 동시 경합에는 짧은 bounded backoff를 추가해 active command가 보이는 즉시 `COMMAND_PROCESSING`으로 안정화했다.
- expiry·보존: worker는 기본 disabled이고 scan 10초·batch 100 기본값이다. due Reservation별 독립 Transaction과 CAS로 여러 ECS task의 중복 release를 막는다. RESERVE·CONFIRM·CANCEL command는 terminalAt과 7일 purgeAt을 기록하며 Reservation·ledger audit에는 TTL을 추가하지 않았다. due batch와 oldest lag metric은 식별자 없는 low-cardinality 값만 기록한다.
- 테스트 결과: 최종 `./gradlew clean test` 총 82개 성공, 실패 0, 오류 0, skip 0. replica-set Testcontainers에서 INITIAL·REPLACEMENT consume/release, concurrent same-command replay/conflict, wrong link, terminal repair 차단, status 무쓰기, confirm/cancel/expiry 세 race 조합, multi-worker, transient transaction retry와 unknown commit 재확인을 실행했다. 최초 전체 실행은 Gradle wrapper lock의 sandbox 권한으로 시작 전 실패해 승인된 Gradle 명령 범위에서 재실행했고, 동시 confirm 보강 뒤 최종 전체 실행도 성공했다.
- 유지한 계약: confirm은 Summary 완료가 아니라 Learning Core Session durable commit 직후다. CONFIRMED는 cancel/expiry로 되돌리지 않고 CANCELED/EXPIRED를 일반 confirm으로 복구하지 않는다. cancel·expiry로 TrialClaim·claimedAt·3년 retention을 삭제·갱신하지 않으며 REPLACEMENT는 추가 무료권을 차감하지 않는다.
- 결정사항: schema v2 collection·index를 그대로 재사용하고 새 index·migration을 만들지 않았다. expiry worker는 production profile에서 자동 활성화하지 않고 배포 gate에서 `BILLING_RESERVATION_EXPIRY_ENABLED=true`를 명시해야 한다. Jira 상태는 사용자 승인 없이 변경하지 않았다.
- 위험 요소: Billing lifecycle 코드만으로 production 연동은 완료되지 않는다. Learning Core reserve→Session commit→confirm/cancel/status saga와 reconciliation, AttemptGroup 상태 event, 실제 Lattice/IAM/SG direct-bypass 차단, staging E2E와 expiry lag alert가 남아 있다.
- 다음 작업: 사용자가 검토 후 승인하면 Jira `TMI-113` 완료 전환을 별도로 수행한다. 기능 순서는 AttemptGroup 상태 event consumer, 재가입 owner rebind, Learning Core saga·Lattice staging E2E이며 각각 별도 계획·Jira가 필요하다.

## 2026-08-28 — TMI-113 완료 처리

<!-- codex-turn:jira-tmi-113-closed -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: `TMI-113` — `[Billing] Reservation lifecycle 구현` (`완료`, 담당자 미지정)
- 작업 목표: 사용자의 명시적 승인에 따라 PLAN-003 구현 Jira를 완료 상태로 전환하고 실제 완료 category를 확인한다.
- 변경 파일: Jira `TMI-113`, `docs/plans/PLAN-003-reservation-lifecycle.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·테스트·Jira 설명·담당자·Git 브랜치는 변경하지 않았다.
- 수행 내용: 전환 전 이슈가 `해야 할 일` 상태이고 global `완료` transition ID 41이 사용 가능함을 확인한 뒤 전환했다. 전환 응답과 재조회 결과 status `완료`, status category `완료`를 확인했다.
- 완료 근거: PLAN-003 confirm·cancel·status·expiry 구현과 직전 `./gradlew clean test` 총 82개 성공, 실패 0, 오류 0, skip 0 결과를 사용했다.
- 테스트 결과: 이번 작업은 Jira 상태와 문서 기록만 변경해 Gradle 테스트를 다시 실행하지 않았다. 직전 최종 전체 회귀 82개 성공 결과는 유지되며 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: Jira 완료는 production 배포·caller 활성화를 뜻하지 않는다. AttemptGroup 상태 event, Learning Core saga/reconciliation, expiry 운영 활성화, 실제 Lattice/IAM/SG와 staging E2E gate를 계속 유지한다.
- 결정사항: TMI-113은 완료됐고 Jira 댓글·worklog·설명·담당자는 추가로 수정하지 않았다.
- 위험 요소: PLAN-003만 완료한 상태에서 production caller를 열면 Learning Core confirm 불명과 실제 AWS direct-bypass 검증 공백이 남는다.
- 다음 작업: 별도 승인으로 AttemptGroup 상태 event consumer 계획과 Jira를 작성한다. 이후 재가입 owner rebind, Learning Core saga·Lattice staging E2E를 순서대로 진행한다.

## 2026-08-28 — Billing 패키지 구조 비교와 개편 초안

<!-- codex-turn:billing-package-structure-draft -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: Identity·Learning Core의 domain/global 기능 우선 패키지 구조를 실제 코드에서 확인하고 Billing 구조를 같은 방향으로 바꾸는 초안을 작성한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·테스트·계약·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 분석 내용: Identity는 `web.tosunsaeng.identity.domain` 아래 auth/user와 세부 기능, Learning Core는 `web.tosunsaeng.domain` 아래 exams/withdrawal을 두며 기능 내부에서 api/application/dto/converter/domain/repository/exception/config를 선택적으로 사용한다. 공통 security·response·exception·observability는 global에 둔다.
- Billing 현황: 최상단 config와 feature package가 혼재하고 `reservation` 47개 클래스 안에 Reservation, TrialClaim, entitlement ledger와 AttemptGroup 책임이 함께 있다. `trialeligibility`는 별도지만 domain 상위 namespace가 없고 domain 전용 properties도 root config에 있다.
- 권장 초안: `web.tosunsaeng.billing` 루트는 유지하고 `domain/{eligibility/trial,entitlement,entitlement/trial,reservation,attempt}`와 `global/{config,security,exception,response,infrastructure/mongodb}`로 재편한다. 각 domain은 필요한 api/application/dto/converter/domain/entity·enums/repository/exception/config만 만든다.
- 실행한 테스트와 결과: 읽기 전용 구조 분석과 문서 기록만 수행해 Gradle 테스트는 실행하지 않았다. Identity·Learning Core AGENTS와 실제 main package tree, 대표 controller/service/converter/domain exception/global exception 구성을 확인했다.
- 유지한 계약: package 리팩터링 초안은 API URL·Method·DTO·error envelope, Mongo collection/index/document field, transaction·CAS·멱등성, security route와 production gate를 변경하지 않는다. Identity와 Learning Core는 읽기 전용으로 유지했다.
- 결정사항: 단순 package 이동과 책임 재설계를 분리한다. 1차는 package/import/test mirror만 이동하고, 2차는 feature exception·converter 정리, 3차는 ReserveService와 lifecycle orchestration의 협력 컴포넌트 분리로 제안한다.
- 위험 요소: 모든 이동과 서비스 분해를 한 번에 하면 Spring component scan, Mongo document mapping, exception envelope와 transaction 경계 회귀 원인을 분리하기 어렵다. 이름만 domain 구조로 바꾸고 ReserveService 책임을 그대로 두면 가독성 문제 일부는 남는다.
- 다음 작업: 사용자가 목표 tree와 domain 경계를 승인하면 별도 리팩터링 계획서와 Jira를 만들고 package-only migration부터 수행한다.

## 2026-08-28 — Billing domain/global 패키지 구조 개편

<!-- codex-turn:billing-domain-global-package-refactor -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 사용자 승인에 따라 Billing을 Identity·Learning Core와 유사한 기능 우선 `domain`/`global` 구조로 실제 개편하고 외부·저장 계약과 런타임 동작을 보존한다.
- 변경 파일: `src/main/java/web/tosunsaeng/billing/domain/**`, `src/main/java/web/tosunsaeng/billing/global/**`, `src/test/java/web/tosunsaeng/billing/domain/**`, `src/test/java/web/tosunsaeng/billing/global/**`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 기존 root `config`, `reservation`, `trialeligibility`, `global/api`, `global/mongodb` source는 새 package로 이동했다. 작업 전부터 수정돼 있던 `docs/plans/PLAN-003-reservation-lifecycle.md`와 기존 기록 변경은 보존했다.
- 구조 변경: Trial eligibility는 `domain/eligibility/trial`, TrialClaim·candidate alias·subject link는 `domain/entitlement/trial`, grant·ledger는 `domain/entitlement`, AttemptGroup·Session은 `domain/attempt`, Reservation lifecycle은 `domain/reservation`으로 분리했다. 공통 Security·Mongo 설정과 Mongo infrastructure, error handler·response는 `global`로 이동했다.
- 책임 정리: `ReservationConverter`가 request→command와 snapshot/result→response 변환을 담당하도록 Controller의 수동 조립을 이동했다. `ReservationException`과 `TrialEligibilityException`이 feature 오류 code를 생성하고 공통 `InternalApiExceptionHandler`는 base exception을 동일하게 처리한다.
- 테스트 결과: 중간 `./gradlew compileTestJava`를 반복해 package/import와 converter·exception 의존성을 검증했다. 첫 전체 테스트는 Security MVC slice에 `ReservationConverter` mock이 없어 3개가 context 시작 전에 실패했고 test slice dependency를 보완했다. 최종 `./gradlew clean test`는 총 82개 성공, 실패 0, 오류 0, skip 0이며 `git diff --check`도 통과했다.
- 유지한 계약: internal URL·method·DTO JSON·16 KiB strict decode, canonical hash, Mongo collection·index·business field, Transaction·CAS·unique index·멱등성, Claim retention, Reservation/AttemptGroup 상태 전이, security default deny와 workload route 구분을 변경하지 않았다. Identity·Learning Core와 Jira·AWS·배포 설정은 변경하지 않았다.
- 결정사항: `web.tosunsaeng.billing` root는 유지하고 feature 안에 필요한 `api`, `application`, `config`, `converter`, `dto`, `domain`, `exception`, `repository`만 둔다. 공통 error envelope와 handler는 global, feature error factory는 각 domain에 둔다. 이번에는 큰 orchestration service 내부 분해를 범위에서 제외했다.
- 위험 요소: Spring Data MongoDB의 기본 `_class` 값은 Java fully-qualified class name을 포함할 수 있어 package 이동 전에 생성한 document가 있다면 old class resolution 또는 migration 문제가 생길 수 있다. Billing 미배포 전제에서는 최초 schema로 적용 가능하지만, 보존할 기존 환경 데이터가 있다면 배포 전에 `_class` 표본과 migration 필요성을 확인해야 한다.
- 다음 작업: 후속 AttemptGroup 상태 event consumer 계획 전에 새 package 구조를 기준으로 작업한다. Reserve/Lifecycle orchestration 분해가 필요하면 transaction 경계와 race 테스트를 유지하는 별도 리팩터링 계획으로 진행한다.

## 2026-08-28 — 다음 작업 AttemptGroup 상태 event consumer 정리

<!-- codex-turn:next-attempt-group-event-consumer-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: PLAN-003 Reservation lifecycle과 package 구조 개편 다음에 구현할 vertical slice의 목적·범위·완료 조건·미확정 세부사항을 정리한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약 결정서·ADR·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 분석 내용: 다음 작업은 Learning Core `POST /internal/v1/attempt-group-events` consumer다. GRADING은 replacement를 차단하고, COMPLETED는 feedback·valid score·summary evidence가 모두 true일 때 group과 active Session을 terminal 처리하며, RETAKE_AVAILABLE은 새 Claim/grant/refund 없이 기존 consumption·group·mockExamId의 replacement를 다시 허용해야 한다.
- 예상 구현: 16 KiB schema v1 strict decode, canonical SHA-256, shared inbox의 eventId/digest 멱등성, active Session fencing, group/session CAS, inbox·projection 단일 Mongo Transaction, 204 APPLIED/DUPLICATE/STALE 수렴, stable 400/409/422/503 error와 low-cardinality metric을 새 `domain/attempt` 구조에 구현한다.
- 테스트 결과: 이번 작업은 설명과 기록만 변경해 Gradle 테스트는 실행하지 않았다. ADR-001 T5 Transaction·AttemptGroup state machine·Mongo schema, 통합 계약과 현재 AttemptGroup/Session repository·Security route를 읽기 전용으로 대조했고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: confirm은 Session durable commit 직후 소비를 확정하며 Summary 완료와 분리한다. RETAKE_AVAILABLE은 무료권 복원·새 지급이 아니라 same consumption replacement이고 COMPLETED는 다시 열지 않는다. Billing은 Learning Core의 질문·답안·점수·feedback·summary·AI/provider 원문을 저장하지 않는다.
- 결정사항: 다음 작업의 권장 범위만 정리했으며 PLAN 번호, Jira, 새 error code, failureCode 목록과 transition 확장 정책은 확정하지 않았다. 기존 collection/index를 재사용할 수 있으나 eligibility package에 묶인 inbox entity/repository는 cross-domain 공통 위치와 nullable event metadata로 정리해야 한다.
- 위험 요소: event에 sequence가 없어 OPEN→GRADING→terminal 순서를 기계적으로 강제하면 terminal event가 먼저 도착한 경우 영구 재시도가 생길 수 있다. 반대로 stale Session fencing 없이 status를 적용하면 abandon된 Session이 현재 group을 COMPLETED 또는 RETAKE_AVAILABLE로 잘못 바꿀 수 있다. missing group/session을 terminal conflict로 처리하면 confirm/outbox 순서 역전 복구가 불가능할 수 있다.
- 다음 작업: 사용자가 진행을 승인하면 먼저 세부 transition·missing prerequisite·failureCode 정책을 PLAN-004 초안에서 확정하고, 별도 승인 후 Jira를 생성한 다음 구현한다. 이후 owner rebind와 Learning Core saga/outbox·Lattice staging E2E를 진행한다.

## 2026-08-28 — 현재 무료 모의고사 소비 로직 설명

<!-- codex-turn:current-free-exam-consumption-flow-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 현재 구현 코드에서 무료 모의고사 grant가 생성·hold·confirm 소비·cancel/expiry 복원·replacement되는 흐름과 실제 차감 시점을 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 분석 내용: eligibility event는 projection만 갱신하고 Claim/grant를 만들지 않는다. 최초 INITIAL reserve Transaction이 필요 시 TrialClaim과 `FREE_EXAM_ONCE` total 1 unit grant를 생성한 뒤 available을 held로 이동한다. Session durable commit 후 confirm Transaction이 held를 consumed로 전환하는 시점이 실제 소비다.
- cancel/expiry 동작: confirm 전 cancel·5분 expiry는 HELD allocation을 RELEASED로 바꾸고 held unit을 available로 복원하며 `RELEASED` ledger를 append한다. TrialClaim·claimedAt·3년 retention은 유지하므로 새 무료권을 지급하지 않고 기존 단일 grant를 다시 사용할 수 있게 한다.
- confirm 이후 동작: CONFIRMED consumption은 일반 cancel/expiry로 되돌리지 않는다. 결과 최종 실패 시에도 grant는 consumed이며, 후속 AttemptGroup consumer가 RETAKE_AVAILABLE로 바꾼 뒤 REPLACEMENT가 같은 consumption·group·mockExamId를 재사용한다. 이 consumer는 아직 미구현이다.
- 테스트 결과: 이번 작업은 코드 설명과 기록만 변경해 Gradle 테스트를 실행하지 않았다. `ReserveService`, `ReservationLifecycleService`, grant/allocation/ledger entity와 repository의 실제 상태 전이·CAS 조건을 읽기 전용으로 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: `reserve → Session commit → confirm`, 5분 hold, Summary와 confirm 분리, cancel/expiry Claim 불변, confirmed 소비 복원 금지, same-consumption replacement, append-only ledger와 phone candidate당 단일 Claim을 유지한다.
- 결정사항: 현재 free grant는 paid balance의 10 credits를 차감하는 모델이 아니라 `FREE_EXAM_ONCE` 1 unit 모델임을 명확히 했다. 시험당 10-credit paid 차감은 결제 기능 구현 시 별도 ledger allocation 정책으로 추가한다.
- 위험 요소: reserve를 최종 소비로 오해하면 cancel/expiry 복원을 중복 지급으로 볼 수 있고, 반대로 Summary 완료까지 confirm을 늦추면 5분 hold 만료 후 같은 무료권이 중복 사용될 수 있다. AttemptGroup consumer 전에는 결과 최종 실패가 자동으로 RETAKE_AVAILABLE로 수렴하지 않는다.
- 다음 작업: 승인된 순서대로 AttemptGroup 상태 event consumer 계획을 확정한 뒤 구현해 confirmed consumption의 완료·최종 실패·same-consumption 재응시를 end-to-end로 연결한다.

## 2026-08-28 — 멘토의 사전 무료 모의고사 정의 방식 비교

<!-- codex-turn:mentor-predefined-free-exam-model-compared -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: “무료 모의고사라는 이름으로 미리 생성하고 이후 공통 처리” 제안을 현재 lazy TrialClaim/grant·Reservation 구현과 비교해 사용자의 이해와 장단점을 검증한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 분석 내용: 전역 catalog/benefit definition 하나를 미리 만드는 것과 사용자별 grant/Session을 미리 만드는 것을 구분했다. 전자는 프로모션 정책 재사용에 유리하지만 후자는 미사용 데이터와 revoke·expiry 정합성 비용을 늘린다. 현재 구현은 최초 reserve에서 Claim과 1-unit grant를 lazy issue하고 실제 시험마다 최소 AttemptSession projection을 만든다.
- 현재 로직 정정: 최종 consumption은 feedback 생성 때가 아니라 Learning Core Session durable commit 뒤 confirm이다. feedback·valid score·summary 완료는 AttemptGroup COMPLETED이며, 최종 실패는 consumed grant를 복원하지 않고 RETAKE_AVAILABLE과 same-consumption REPLACEMENT로 처리한다.
- 확장성 평가: 현재 `grantType`, `sourceType`, `sourceId`, allocation과 append-only ledger는 공통 entitlement 기반이지만 `FREE_EXAM_ONCE`와 resolver가 하드코딩돼 있다. 새 프로모션을 이름만으로 추가할 수는 없고 stable code, campaign/source, unit, expiry, eligibility limit, stacking/priority, policyVersion과 dedupe가 필요하다.
- 테스트 결과: 설명·기록만 변경해 Gradle 테스트는 실행하지 않았다. `EntitlementGrant`, `AttemptSession`, 계약 결정서의 현재 무료 resolver와 후속 catalog/promotion 계약을 읽기 전용으로 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: eligibility event만으로 지급하지 않고 최초 reserve에서 Claim/grant를 원자적으로 생성한다. `reserve → Session commit → confirm`, confirmed consumption 복원 금지, same-consumption replacement, raw phone 비저장과 append-only ledger를 변경하지 않았다.
- 결정사항: 권장안은 catalog/offer definition만 사전 생성하고 사용자별 Claim/grant는 lazy issue하며 무료·promotion·paid가 공통 allocation/lifecycle을 재사용하는 hybrid다. 이는 설명·권장안이며 현재 계약 변경으로 확정하지 않았다.
- 위험 요소: display name을 식별자로 사용하면 이름 변경·다국어·중복 campaign에서 ledger와 dedupe가 깨진다. Billing AttemptSession을 제거하면 stale Session event와 restart fencing을 보장하기 어렵다. 모든 verified user에게 grant를 미리 발급하면 사용하지 않는 grant와 탈퇴·revoke cleanup 부담이 커진다.
- 다음 작업: 현재 순서대로 AttemptGroup event consumer를 먼저 완성한다. 결제·promotion 착수 시 별도 계약에서 catalog/offer/grant resolver와 consumption 우선순위를 확정한다.

## 2026-08-28 — TrialClaim 사전 생성 제안 비교

<!-- codex-turn:precreated-trial-claim-option-compared -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 멘토의 제안이 phone verification 시 TrialClaim을 미리 생성하는 방식에 가깝다는 사용자 보충을 바탕으로 현재 최초 reserve lazy creation과 정확히 비교한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약 결정서·ADR·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 분석 내용: 현재 verified event는 TrialEligibility만 저장하고 최초 reserve가 Claim·candidate alias·subject link·grant·GRANTED ledger와 hold를 한 Transaction에서 처리한다. 사전 Claim은 reserve 지연·쓰기와 늦은 candidate 경합을 줄이지만 모든 verified 사용자에 미사용 Claim 데이터를 만들고 eligibility consumer에 issuance 책임을 결합한다.
- 기산점 영향: 현 계약은 최초 reserve의 claimedAt부터 3년이다. verified 시 ACTIVE Claim을 만들면 인증 시점으로 기산점이 앞당겨지고, claimedAt 없는 예비 Claim 상태를 추가하면 현재 TrialEligibility와 중복되는 상태 머신·index·CAS·purge 계약이 새로 필요하다.
- 확장성 평가: TrialClaim은 FREE_EXAM_ONCE phone dedupe 전용이므로 사전 생성만으로 일반 campaign·coupon·paid promotion 확장성이 생기지 않는다. 공통 확장은 catalog/offer와 EntitlementGrant·allocation·ledger resolver에서 해야 한다. Claim만 선생성하고 grant를 lazy 생성하는 절충은 양쪽 복잡도를 가지면서 reserve 단순화 효과가 제한적이다.
- 테스트 결과: 설명·기록만 변경해 Gradle 테스트는 실행하지 않았다. TrialClaim·TrialEligibility entity, ADR-001 collection 계약과 승인된 `첫 reserve에서 Claim/grant 생성`·3년 기산점 계약을 읽기 전용으로 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: 이번 비교에서는 eligibility event만으로 지급하지 않고 첫 reserve에서 TrialClaim/grant를 만드는 현행 계약, claimedAt+3년, phone candidate dedupe, raw phone 비저장과 transaction/unique index 원칙을 변경하지 않았다.
- 결정사항: 현재 MVP에는 lazy TrialClaim 유지가 권장된다. 사전 발급이 제품 요구가 되면 TrialClaim과 grant를 함께 발급할지, claimedAt을 verifiedAt으로 볼지, 미사용·revoke·재가입 정책을 먼저 계약으로 재승인해야 한다.
- 위험 요소: Claim만 미리 만들면 grant issuance와 Claim 상태가 분리되어 부분 완료 복구가 늘어난다. verification 시 3년을 시작하면 사용하지 않은 사용자도 만료될 수 있고, claimedAt을 reserve까지 비워두면 dedupe·retention semantics가 불명확해진다.
- 다음 작업: 멘토 의도가 reserve latency 감소인지 verified 즉시 권리 귀속·표시인지 확인한 뒤 변경을 원할 경우 선택지를 포함한 별도 계약안을 작성한다. 변경하지 않으면 기존 순서대로 AttemptGroup event consumer 계획을 진행한다.

## 2026-08-28 — 사전 정의 혜택과 사용자 보유 연결 모델 정리

<!-- codex-turn:benefit-definition-vs-user-claim-grant-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 프로모션이 많아질 때 혜택 정보를 매번 저장하지 않고 사전 생성 record에 연결하면 확장하기 쉽다는 사용자 관점을 TrialClaim·Grant·catalog 책임으로 구분해 검증한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약 결정서·ADR·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 분석 내용: 공통 benefit metadata를 `BenefitDefinition`에 한 번 저장하고 user/phone별 record가 stable benefitCode로 연결하는 정규화 방향은 타당하다. 그러나 TrialClaim은 phone candidate dedupe·claimedAt+3년 retention의 사용자별 record라 shared definition으로 사용할 수 없으며, 사용자 보유량과 source/expiry를 나타내는 연결 document는 여전히 필요하다.
- 역할 구분: catalog는 이름·unit type·소비 정책·policyVersion, TrialClaim은 FREE_EXAM_ONCE anti-abuse, EntitlementGrant는 subject별 지급 source·quantity·expiry, ReservationAllocation과 ledger는 hold/consume/release를 담당한다. “연결만 저장”할 때 그 연결이 곧 grant/ownership record다.
- 확장성 평가: 현재 grantType/sourceType/sourceId와 unit projection은 연결 모델의 기반이지만 benefit definition이 없고 FREE_EXAM_ONCE가 하드코딩돼 있다. 일회성 pass는 entitlement token으로 단순화할 수 있으나 대량 paid credits를 unit별 document로 만들면 비효율적이므로 one-off token과 fungible batch quantity를 병행하는 hybrid가 적절하다.
- 테스트 결과: 설명·기록만 변경해 Gradle 테스트는 실행하지 않았다. 현재 TrialClaim/Grant field와 hard-coded benefit repository 조건, 승인된 paid/promotion 후속 범위를 기존 확인 결과와 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: 현재 무료 MVP의 TrialClaim phone dedupe, 최초 reserve lazy issue, claimedAt+3년, 1-unit grant, allocation·append-only ledger와 paid/promotion 미구현 범위를 변경하지 않았다.
- 결정사항: 사용자의 확장성 목표에는 동의하되, 해결책은 TrialClaim을 catalog로 확장하는 것이 아니라 shared BenefitDefinition과 per-subject Grant/Claim 연결을 분리하는 모델을 권장한다. catalog 도입과 Claim eager/lazy 시점은 독립 결정으로 남겼다.
- 위험 요소: Claim 하나를 공유하거나 이름을 식별자로 사용하면 사용자별 retention·source와 phone unique invariant가 깨진다. 반대로 사용자 보유 연결을 없애면 누가 어떤 campaign 권리를 몇 개·언제까지 보유하는지 판단하거나 환불·만료·중복 지급을 감사할 수 없다.
- 다음 작업: 사용자가 이 모델을 채택하려면 후속 설계에서 BenefitDefinition 식별자·unit type·policy version과 one-off token/quantity grant 경계를 확정한다. 당장 무료 MVP는 AttemptGroup event consumer를 우선한다.

## 2026-08-28 — 현재 Benefit/Claim/Grant 구조와 구독제 방향 확인

<!-- codex-turn:current-benefit-claim-grant-and-subscription-direction -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 현재 코드가 BenefitDefinition·TrialClaim·EntitlementGrant 구조인지 확인하고, credit 대신 단순 구독제로 갈 경우의 적절한 도메인 분리를 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약 결정서·ADR·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 현재 구조: BenefitDefinition/catalog는 없고 FREE_EXAM_ONCE가 Claim·alias·Grant/repository에 하드코딩돼 있다. verified event는 TrialEligibility만 저장하며 최초 reserve가 phone candidate Claim을 확인해 필요 시 TrialClaim·link·aliases와 1-unit Grant·GRANTED ledger를 lazy 생성한다.
- 역할: TrialClaim은 phone별 무료 1회 dedupe와 3년 retention, EntitlementGrant는 one-time unit의 available/held/consumed projection, ledger와 allocation은 지급·hold·소비·복원을 담당한다. 향후 BenefitDefinition은 이 record들이 stable benefitCode로 참조할 공통 정책이다.
- 구독 방향: credit balance 대신 subscription을 채택하면 유료 권리는 quantity Grant가 아니라 subject별 status·startsAt·endsAt을 가진 SubscriptionEntitlement가 적절하다. 활성 기간에는 시험 unit을 차감하지 않지만 Reservation idempotency, 동시 Session 제한과 entitlement source usage audit는 유지해야 한다.
- 테스트 결과: 구조 설명·기록만 변경해 Gradle 테스트를 실행하지 않았다. 현재 FREE_EXAM_ONCE 하드코딩 위치와 TrialClaim/Grant 생성 흐름을 직전 코드 확인 결과에 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: 현재 무료 MVP의 lazy Claim/Grant, reserve hold, Session commit 뒤 confirm 소비, same-consumption replacement와 결제/구독 미구현 범위를 변경하지 않았다.
- 결정사항: 사용자는 장기 유료 모델로 credit보다 단순 구독제를 선호한다고 밝혔다. 이는 방향 기록이며 Store plan·renewal·cancel·expiry·grace와 무료/구독 resolver 우선순위가 아직 승인된 구현 계약은 아니다.
- 위험 요소: 구독을 기존 수량 Grant에 억지로 넣으면 available/held/consumed 의미가 어색해지고, 반대로 구독 중 Reservation을 생략하면 중복 Session·same-key retry·AttemptGroup 연결을 잃는다. BenefitDefinition 없이 새 plan을 계속 하드코딩하면 배포 없이 상품 정책을 변경하기 어렵다.
- 다음 작업: 무료 MVP는 AttemptGroup event consumer를 우선한다. 구독 결제 착수 시 BenefitDefinition/SubscriptionPlan, SubscriptionEntitlement와 무료 TrialClaim/Grant resolver 경계를 별도 계약으로 확정한다.

## 2026-08-28 — BenefitDefinition·Grant·TrialClaim·Ledger 역할 설명

<!-- codex-turn:benefit-grant-trial-claim-ledger-roles-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: BenefitDefinition은 응시권 종류, EntitlementGrant는 보유 응시권, TrialClaim은 이력이라는 사용자 이해를 정확한 도메인 책임으로 보정한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 역할 정리: BenefitDefinition은 혜택 종류·소비 정책 catalog, EntitlementGrant는 subject별 실제 발급 권리와 unit projection, TrialClaim은 verified-phone candidate의 FREE_EXAM_ONCE 3년 중복 발급 방지 근거다. 실제 지급·hold·release·consume 이력은 append-only EntitlementLedger가 담당한다.
- 연결 구조: TrialClaim은 무료 Grant의 source이고 BenefitDefinition은 Grant가 가리킬 종류다. ReservationAllocation이 시험 Reservation과 사용 Grant를 연결하며 Reservation·AttemptGroup은 Session 생성 및 same-consumption 재응시 lifecycle을 담당한다.
- 테스트 결과: 개념 설명·기록만 변경해 Gradle 테스트를 실행하지 않았다. 현재 Claim·Grant·ledger·allocation 책임과 기존 계약을 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: phone당 무료 1회, claimedAt+3년, 1-unit grant, append-only ledger, reserve hold·confirm consume와 same-consumption replacement를 변경하지 않았다.
- 결정사항: 사용자의 구조 이해는 대체로 맞고 TrialClaim을 일반 이력이 아닌 무료 발급 dedupe record로, ledger를 실제 이력으로 구분했다. BenefitDefinition 도입은 아직 후속 설계다.
- 위험 요소: TrialClaim을 소비 이력으로 사용하면 cancel·replacement·다중 ledger event를 표현하지 못하고 Claim 삭제/변경 유혹으로 3년 dedupe가 깨질 수 있다. Grant만 보고 감사하면 mutable projection과 실제 event history가 불일치할 때 복구 근거가 없다.
- 다음 작업: 현재 무료 MVP에서는 기존 책임을 유지하고, 구독 설계 시 BenefitDefinition/SubscriptionPlan과 SubscriptionEntitlement 경계를 확정한다.

## 2026-08-28 — TrialEligibility 역할 설명

<!-- codex-turn:trial-eligibility-role-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: TrialEligibility가 전화번호 인증 여부를 저장하는 record인지 설명하고 Claim·Grant와 경계를 구분한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 설명 내용: TrialEligibility는 Identity verified/revoked event의 user별 current projection이며 consumer scope, binding revision, VERIFIED/REVOKED, opaque candidate와 event high-water를 저장한다. raw phone은 저장하지 않는다.
- 동작: reserve는 current VERIFIED와 candidate 존재를 확인해야 Claim/Grant를 생성·연결한다. revoke는 candidate를 제거하고 revision tombstone을 유지하지만 기존 TrialClaim·Grant·consumption을 삭제하거나 복원하지 않는다.
- 테스트 결과: 개념 설명과 기록만 변경해 Gradle 테스트를 실행하지 않았다. 직전 확인한 TrialEligibility entity와 승인된 event/reserve 계약을 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: event 수신 자체는 지급이 아니며 raw phone 비저장, revision high-water, fail-closed reserve, revoke 시 Claim 불변을 유지한다.
- 결정사항: 새 결정 없이 TrialEligibility를 “현재 전화 인증 기반 무료권 자격 projection”으로 명확히 했다.
- 위험 요소: Eligibility를 entitlement로 오해하면 verified event만으로 무료권을 지급하거나 revoke 때 사용 이력을 삭제할 수 있다. 반대로 revision tombstone을 제거하면 늦은 verified event가 REVOKED 상태를 되돌릴 수 있다.
- 다음 작업: 기존 순서대로 AttemptGroup event consumer 계획을 진행하며 구독 설계에서도 identity eligibility와 paid subscription entitlement를 분리한다.

## 2026-08-28 — 장기 Benefit·무료 Grant·구독 구조 승인 반영

<!-- codex-turn:benefit-free-grant-subscription-architecture-approved -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 사용자가 승인한 BenefitDefinition, TrialClaim, EntitlementGrant, SubscriptionEntitlement, Reservation, AttemptGroup 장기 구조를 단일 계약 기준에 반영하고 즉시 필요한 코드 변경을 판정한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 승인 구조: BenefitDefinition은 공통 종류·정책, TrialClaim은 phone 무료 1회 dedupe, EntitlementGrant는 one-time 보유 권리, SubscriptionEntitlement는 기간형 유료 권리, Reservation은 공통 시험 authorization/idempotency, AttemptGroup은 사용 건·replacement 연결, ledger는 실제 변경 이력으로 확정했다.
- 현재 gap: TrialClaim·Grant·Reservation·AttemptGroup·ledger/allocation은 구현돼 있으나 BenefitDefinition은 없고 FREE_EXAM_ONCE가 문자열로 하드코딩돼 있다. SubscriptionEntitlement는 구독 제품 계약 전 범위 밖이다.
- 즉시 영향: 구조 방향만 적용하는 데 런타임 변경은 필요 없다. 실제 catalog foundation 구현 시 definition collection/seed, stable benefitCode와 unique index/startup validation, Claim/Grant reference 정리와 schema/contract test가 필요하다. 구독 때는 Reservation attempt kind와 별개 authorization source type/reference 및 subscription active-period resolver를 추가해야 한다.
- 테스트 결과: 계약·설명 문서만 변경해 Gradle 테스트를 실행하지 않았다. 현재 FREE_EXAM_ONCE 하드코딩, Reservation.Kind와 allocation 구조, 기존 무료 ADR/결제 deferred 범위를 읽기 전용으로 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: 현재 무료 MVP의 lazy Claim/Grant, claimedAt+3년, reserve hold·confirm consume, same-consumption replacement, internal DTO·Mongo schema와 결제/구독 미구현 gate는 변경하지 않았다.
- 결정사항: 승인 구조를 CONTRACT_DECISIONS 1C에 확정했다. PREMIUM_SUBSCRIPTION 식별자는 후속용으로 예약하지만 Store plan·가격·renewal·cancel·expiry·grace는 미확정이다. 과거 credit/pass 초안은 실제 폐기 승인 전까지 역사적 동결로 남긴다.
- 위험 요소: BenefitDefinition 없이 구독까지 추가하면 hard-coded benefit 분기가 퍼지고, 반대로 지금 빈 SubscriptionEntitlement와 미확정 Store 필드를 만들면 speculative schema가 된다. Reservation.Kind를 entitlement source로 재사용하면 INITIAL/REPLACEMENT와 FREE/SUBSCRIPTION 두 축이 섞인다.
- 다음 작업: 사용자가 foundation 선행을 승인하면 BenefitDefinition vertical slice 계획서를 작성하고 별도 승인으로 Jira를 생성한다. 그렇지 않으면 AttemptGroup event consumer를 먼저 진행하고 구독 착수 전에 foundation을 구현한다.

## 2026-08-28 — BenefitDefinition 선행·구독 후속 순서 확정

<!-- codex-turn:benefit-definition-first-subscription-deferred -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 현재 업데이트에는 BenefitDefinition foundation을 먼저 구현하고 구독은 다음 업데이트로 연기한 뒤 기존 무료 MVP 작업 순서로 복귀한다는 사용자 결정을 반영한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 확정 범위: FREE_EXAM_ONCE BenefitDefinition catalog, stable benefitCode, seed/fail-fast validation, unique index와 Claim·alias·Grant reference/hardcode 정리를 별도 vertical slice로 선행한다. SubscriptionEntitlement, PREMIUM_SUBSCRIPTION 실제 definition, Store·renewal·cancel·expiry·grace와 구독 authorization은 다음 업데이트다.
- 유지 동작: TrialEligibility event는 자격 projection만 저장하고 최초 reserve가 TrialClaim·1-unit Grant를 lazy 생성해 hold한다. Session durable commit 뒤 confirm 소비, cancel/expiry release와 same-consumption replacement를 변경하지 않는다.
- 테스트 결과: 작업 순서·계약 문서만 변경해 Gradle 테스트를 실행하지 않았다. 현재 catalog gap과 Mongo/index 영향, 기존 후속 순서를 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: 현재 internal API DTO·Mongo collection, claimedAt+3년, TrialClaim phone dedupe, one-unit grant·append-only ledger, Reservation lifecycle와 구독/결제 production gate를 유지한다.
- 결정사항: BenefitDefinition foundation을 다음 구현 단위로 선행한 뒤 AttemptGroup event consumer → owner rebind → Learning Core saga/Lattice E2E 순서로 진행한다. PLAN 번호와 Jira는 아직 만들지 않았다.
- 위험 요소: foundation과 구독 entity를 한 번에 만들면 미확정 Store 상태 머신이 schema에 고정된다. 반대로 BenefitDefinition에서 existing benefitType/grantType field migration 방식을 계획 없이 바꾸면 ADR·index와 테스트가 불일치할 수 있다.
- 다음 작업: BenefitDefinition foundation 계획서를 작성해 field/reference·seed·index·schema migration과 테스트 범위를 확정하고, 사용자 승인 후 Jira를 생성한 뒤 구현한다.

## 2026-08-28 — PLAN-004 BenefitDefinition foundation 계획서 작성

<!-- codex-turn:plan-004-benefit-definition-foundation-drafted -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 구독을 제외하고 FREE_EXAM_ONCE BenefitDefinition foundation만 구현할 수 있는 vertical slice 계획을 작성한다.
- 변경 파일: `docs/plans/PLAN-004-benefit-definition-foundation.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·AGENTS·Jira와 타 저장소는 변경하지 않았다.
- 계획 내용: benefit_definitions catalog와 exact seed/drift validation, `_id=benefitCode`, Claim·alias·Grant의 benefitCode reference, 기존 benefitType/grantType 정리, schema v3와 alias/grant index 보정, BenefitCatalog 기반 lazy reserve 발급과 replica-set 회귀 테스트를 포함했다.
- 유지 동작: Identity verified event는 TrialEligibility만 반영하고 최초 reserve가 Claim·1-unit Grant를 생성해 hold한다. confirm/cancel/expiry, ledger, wire DTO, claimedAt+3년과 same-consumption replacement는 변경하지 않는다.
- 제외 범위: PREMIUM_SUBSCRIPTION, SubscriptionEntitlement, Store·renewal·cancel·expiry·grace, 구독 authorization, eager TrialClaim, public 상품 API, AttemptGroup event·owner rebind·타 서비스/AWS 변경을 분리했다.
- 테스트 결과: 계획 문서만 작성해 Gradle 테스트를 실행하지 않았다. 기존 PLAN 형식, current entity/index/schema v2와 승인된 CONTRACT_DECISIONS 1C를 대조했고 종료 전 code fence·`git diff --check`를 검증한다.
- 결정사항: PLAN 번호는 004, 상태는 사용자 승인 대기, Jira는 미생성이다. benefitCode는 `_id`로 유일성을 보장하고 redundant secondary unique index를 만들지 않는다. 기존 storage field는 미배포 schema v3에서 benefitCode로 통일한다.
- 위험 요소: v2 데이터를 보존해야 하는 환경에서 자동 field/index 변경을 하면 데이터 손상 위험이 있으므로 startup migration을 금지하고 별도 migration 또는 비운영 DB 재생성을 요구한다. catalog drift는 자동 update하지 않고 fail-fast한다.
- 다음 작업: 사용자가 PLAN-004를 승인하면 별도 승인으로 Jira를 생성하고, Jira 완료 조건을 읽은 뒤 구현한다. 완료 후 AttemptGroup 상태 event consumer 순서로 복귀한다.

## 2026-08-28 — PLAN-004 Jira 작업 생성

<!-- codex-turn:plan-004-jira-created -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: `TMI-115` — `[Billing] BenefitDefinition foundation 구현` (`해야 할 일`, 담당자 미지정)
- 작업 목표: 사용자가 승인한 PLAN-004 BenefitDefinition foundation 범위와 완료 조건을 Jira 작업으로 고정한다.
- 변경 파일: `docs/plans/PLAN-004-benefit-definition-foundation.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·Mongo schema·ADR·AGENTS와 타 저장소는 변경하지 않았다.
- Jira 내용: benefit_definitions collection, FREE_EXAM_ONCE seed, exact policy drift fail-fast, Claim·alias·Grant의 benefitCode 전환, schema v3/index 보정, BenefitCatalog 기반 최초 reserve lazy 발급과 replica-set Testcontainers 회귀를 포함했다.
- 제외 범위: PREMIUM_SUBSCRIPTION, SubscriptionEntitlement, Store lifecycle, 구독 Reservation 분기, eager TrialClaim, public 상품 API, AttemptGroup event consumer, owner rebind와 Identity·Learning Core·AWS 변경을 명시했다.
- 테스트 결과: Jira와 문서 metadata만 변경해 Gradle 테스트는 실행하지 않았다. 생성 후 Jira의 key·summary·issue type·status·assignee를 다시 조회했고 문서 변경 후 `git diff --check`를 실행한다.
- 유지한 계약: eligibility event는 TrialEligibility만 저장하고 최초 INITIAL reserve가 Claim·1-unit Grant를 lazy 생성·hold하며 Session durable commit 뒤 confirm에서 소비한다. claimedAt+3년, cancel/expiry release, same-consumption replacement와 production caller gate도 유지한다.
- 결정사항: Jira 유형은 `작업`, 상태는 `해야 할 일`, 담당자는 미지정이다. PLAN-004 상태는 사용자 승인·Jira 생성·구현 전으로 갱신했다.
- 위험 요소: v2 보존 데이터가 있는 환경에서 자동 field/index migration을 수행하면 안 되며, definition 누락·inactive·drift 시 부분 지급 없이 fail-closed해야 한다. 구독 기능을 이번 구현에 섞지 않는다.
- 다음 작업: 구현 요청을 받으면 `TMI-115` 완료 조건과 PLAN-004를 읽고 BenefitDefinition foundation을 구현한 뒤 전체 테스트를 수행한다. Jira 상태 변경은 별도 사용자 승인 전까지 하지 않는다.

## 2026-08-28 — PLAN-005 AttemptGroup 상태 event consumer 계획서 작성

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113`과 별도 계획 `TMI-115` 참고
- 작업 목표: Learning Core의 `AttemptGroupStatusChanged` schema v1 event를 Billing inbox와 현재 active Session fencing을 거쳐 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE`로 수렴시키는 다음 vertical slice 계획을 작성한다.
- 변경 파일: `docs/plans/PLAN-005-attempt-group-status-event-consumer.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·통합 계약·AGENTS·Jira와 AWS는 변경하지 않았다.
- 계획 내용: 16 KiB strict decode, canonical digest, shared inbox 일반화, duplicate/conflict, group-session-owner 검증, active Session fencing, group/session version CAS와 단일 Mongo Transaction, feature flag 기본 off, workload security, privacy-safe metric과 replica-set 동시성 테스트를 포함했다.
- 상태 정책: 유효 terminal event는 `GRADING` 누락 시 `OPEN`에서도 직접 전진하며 `COMPLETED`와 `RETAKE_AVAILABLE` 확정 뒤에는 역행하지 않는다. stale Session은 inbox `STALE`과 204, missing group/session은 inbox 없이 retryable `503 ATTEMPT_PROJECTION_NOT_READY`, 구조적 target 충돌은 non-retryable `409 EVENT_TARGET_CONFLICT`다.
- failureCode: `REQUIRED_RESULTS_UNAVAILABLE`, `SUMMARY_UNAVAILABLE`, `GRADING_DEADLINE_EXCEEDED`, `RESULT_INTEGRITY_VIOLATION` 네 저 cardinality 값만 초안 allowlist로 고정했다. provider 원문·exception message·job/문항 식별자는 금지한다.
- 유지한 계약: 기존 Reservation·TrialClaim·grant·ledger, same-consumption replacement, Identity eligibility event, 내부 API와 기존 Mongo index를 변경하지 않는다. AttemptGroup event로 소비를 환불하거나 새 Claim/grant를 만들지 않는다.
- 테스트 결과: 계획 문서만 작성해 Gradle 테스트는 실행하지 않았다. ADR-001·ADR-002·서비스 통합 계약과 현재 AttemptGroup/Session/inbox/security/index 코드를 대조했으며 종료 전 `git diff --check`를 실행한다.
- 결정사항: 기존 PLAN-004와 번호 충돌을 피하기 위해 PLAN-005를 사용한다. PLAN-004 BenefitDefinition은 이 consumer의 기술적 선행 조건이 아니며, 사용자의 우선순위 결정에 따라 PLAN-005를 먼저 구현할 수 있다. 상태는 사용자 승인 대기이고 Jira는 미생성이다.
- 위험 요소: sequence 없는 상충 terminal event는 먼저 commit된 terminal이 승리하므로 producer가 서로 모순된 terminal evidence를 발행하지 않는 contract test가 필요하다. shared inbox 일반화가 Identity revision dedupe를 깨뜨리지 않도록 전체 회귀를 gate로 둔다.
- 다음 작업: 사용자가 PLAN-005를 검토·승인하면 별도 승인으로 Jira를 생성한다. 구현 전 Phase 0에서 ADR·통합 계약에 오류·failureCode·순서 역전 정책을 반영하고 이후 Step 1부터 구현한다.

## 2026-08-28 — TMI-115 PLAN-004 BenefitDefinition foundation 구현 완료

<!-- codex-turn:tmi-115-benefit-definition-foundation-implemented -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: `TMI-115` — `[Billing] BenefitDefinition foundation 구현` (`해야 할 일`, 담당자 미지정)
- 작업 목표: FREE_EXAM_ONCE 하드코딩을 versioned BenefitDefinition catalog 참조로 전환하고 Claim·alias·Grant와 Mongo v3 계약을 일관되게 적용한다.
- 변경 파일: `AGENTS.md`, `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/plans/PLAN-004-benefit-definition-foundation.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`, `src/main/resources/application.yml`, BenefitDefinition domain/application/repository/config 신규 파일, Claim·alias·Grant entity/repository, ReserveService, Mongo properties/index initializer와 관련 단위·Testcontainers 테스트. Identity·Learning Core·AWS 파일은 변경하지 않았다.
- 구현 내용: `benefit_definitions`와 `_id=benefitCode`, FREE_EXAM_ONCE UNIT/EXAM_ATTEMPT/1-unit/policy-v1/active seed, 재실행 no-op, exact drift startup fail-fast를 추가했다. TrialClaim·TrialCandidateAlias·EntitlementGrant를 `benefitCode`로 통일하고 최초 reserve가 BenefitCatalog의 definition으로 Grant unit을 발급하도록 변경했다.
- 정합성: 기존 Claim 재사용 시 Claim·Grant·Definition code와 totalUnits를 검증한다. definition 누락·inactive·reference mismatch는 command·Claim·Grant·Reservation 부분 write 없이 Transaction rollback과 retryable 503으로 처리하고 privacy-safe invariant metric만 기록한다.
- Mongo: schema version을 2에서 3으로 올리고 `ux_active_trial_candidate` key를 `{benefitCode,keyVersion,candidate}`, `ux_grant_source_type` key를 `{sourceType,sourceId,benefitCode}`로 바꿨다. legacy field document와 이름이 같은 v2 index는 자동 rename/drop/recreate하지 않고 preflight fail-fast한다.
- 테스트 결과: BenefitDefinition code/policy와 catalog 단위 테스트, seed idempotency·policy drift·legacy schema/index/document·missing/inactive rollback·same-code reference·existing Grant mismatch·기존 동시성 및 Reservation lifecycle 회귀를 포함해 `./gradlew clean test` 전체 96개가 성공했다. `git diff --check`와 benefit domain 민감정보 검색도 통과했다.
- 유지한 계약: Identity event는 TrialEligibility만 반영하고 최초 INITIAL reserve에서 Claim·1-unit Grant를 lazy 생성한다. reserve → Session durable commit → confirm, claimedAt+3년, cancel/expiry release, confirmed 소비 불복원, same-consumption replacement, append-only ledger와 production caller gate를 유지했다.
- 결정사항: BenefitDefinition은 공통 policy catalog이고 사용자 권리나 candidate를 저장하지 않는다. displayName은 authorization key로 사용하지 않으며, greenfield production은 v3로 준비한다. 보존할 v2 데이터가 발견되면 별도 migration 승인을 받아야 한다.
- 제외 범위: PREMIUM_SUBSCRIPTION, SubscriptionEntitlement, Store lifecycle, 구독 Reservation 분기, eager TrialClaim, public 상품 API, AttemptGroup event consumer, owner rebind, Identity·Learning Core와 AWS/Lattice 변경.
- 위험 요소: 운영에서 `BILLING_MONGODB_INITIALIZE_INDEXES`를 끄거나 v3 catalog seed 없이 caller를 열면 reserve가 fail-closed한다. production 활성화 전 schema v3 initializer, Learning Core saga와 Lattice staging E2E를 검증해야 한다.
- 다음 작업: 사용자가 검토한 뒤 별도 승인으로 Jira TMI-115를 완료 처리한다. 기능 순서는 이미 작성된 PLAN-005 AttemptGroup 상태 event consumer → owner rebind → Learning Core saga/Lattice staging E2E다.

## 2026-08-28 — PLAN-005 초안 철회와 대상 저장소 정정

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 생성·수정 없음. `TMI-113`, `TMI-115` 상태를 변경하지 않았다.
- 정정 내용: 사용자가 수정 대상은 Billing이 아니라 Learning Core라고 명확히 했다. 범위를 잘못 잡아 작성한 Billing `docs/plans/PLAN-005-attempt-group-status-event-consumer.md` 초안을 삭제하고 활성 계획에서 철회했다.
- 변경 범위: 잘못 생성한 미추적 계획 파일 제거와 Billing CURRENT_STATE/WORKLOG의 정정 기록만 수행했다. Billing 애플리케이션·ADR·통합 계약·AGENTS·Jira·AWS는 변경하지 않았다.
- 다음 작업: Learning Core 저장소에서 Billing 연동의 선행 조건인 필수 `Idempotency-Key`, reserve→Session commit→confirm saga와 same-operation replay 계획을 작성한다.

## 2026-08-31 — 웹 제외 앱 서버 통합 구조 조사 참여 기록

- 날짜: 2026-08-31
- 브랜치·snapshot: `develop@39e424d`
- Jira: 이번 분석의 신규 Jira는 없다. 현재 구현 문맥의 `TMI-115`와 Learning Core `TMI-116`을 읽기 전용 근거로 사용했고 Jira mutation은 수행하지 않았다.
- 작업 목표: Learning Core·Identity·Billing 전체 구조 조사에서 Billing 혜택·사용권·Reservation·Attempt lifecycle과 실제 구현/미구현 경계를 정리한다.
- 변경 파일: 통합 산출물은 Learning Core의 `docs/architecture`에 작성했고 이 저장소에서는 `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md` 기록만 갱신했다. 애플리케이션·설정·계약·테스트 코드는 변경하지 않았다.
- 확인 내용: TrialEligibility→BenefitDefinition→TrialClaim/EntitlementGrant→Reservation/Allocation/Ledger→AttemptGroup/AttemptSession 모델과 reserve·confirm·cancel·status·expiry가 구현됐다. 앱용 공개 Billing API는 의도적으로 없다.
- 구조 판단: strict decoder, Transaction·unique index·CAS, command idempotency와 append-only ledger는 강점이다. AttemptGroup 상태 event consumer와 owner lifecycle/reconciliation, 실제 Lattice·Mongo staging gate는 미완성이다.
- 테스트·검증: 코드 변경이 없는 분석이므로 Gradle 테스트를 실행하지 않았다. 중앙 draw.io XML과 문서 whitespace 검증을 수행했다.
- 유지 계약: event 수신과 Claim/Grant lazy 발급 분리, reserve→Learning Session commit→confirm, confirmed 소비 불복원, 내부 API의 BaseResponse 미사용과 데이터 최소화를 유지했다.
- 위험·다음 작업: Billing AttemptGroup consumer를 먼저 배포한 뒤 Learning Core outbox/publisher를 활성화하고, Identity SigV4·route/IAM과 staging failure-injection E2E를 완료해야 한다. Git commit·push와 Secret/Token 기록은 수행하지 않았다.

## 2026-08-31 — Billing merge 및 Learning Core TMI-116 구현 리뷰

<!-- codex-turn:tmi-116-learning-core-implementation-review -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`; Learning Core `feat/TMI-116-billing-reservation-exam-saga`
- Jira: Billing `TMI-115` merge 결과와 Learning Core `TMI-116` 구현을 확인했다. Jira 상태·댓글은 변경하지 않았다.
- 작업 목표: BenefitDefinition merge 반영 여부와 Learning Core의 Billing Reservation 시험 생성 saga가 승인된 API·멱등성·상태 수렴 계약을 지키는지 읽기 전용 검토한다.
- 변경 파일: Billing `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`에 리뷰 결과만 기록했다. Billing·Learning Core 애플리케이션 코드, 계약, Jira, AWS와 배포 설정은 변경하지 않았다.
- 확인 결과: Billing PR #3 merge commit `39e424d`와 구현 commit `18f1265`가 local/origin `develop`에 있다. Learning Core 구현은 commit `9241a39`로 branch와 origin에 있으며 public API shape, default-off flag, reserve→Session commit→confirm 정상 흐름, Billing DTO/path/header 및 `ap-northeast-2`/`vpc-lattice-svcs` SigV4는 계약과 일치한다.
- 리뷰 발견: Learning Core가 durable confirming Session을 먼저 반환해 `SESSION_COMMITTED` confirm/status replay를 건너뛰므로 confirm과 status가 한 번 함께 실패하면 같은 key가 영구 processing에 머문다. transaction transient/unknown failure에서 한 번의 reload만으로 RESERVED를 보면 cancel해 concurrent same-key winner의 shared reservation을 취소할 race가 있다. Billing response decode에는 scalar coercion·missing field 차단과 confirm `attemptGroupStatus`/`confirmedAt` exact validation이 부족하다.
- 범위 위험: Learning Core TMI-116 단일 commit은 saga 외 비용 추정, 10초 챌린지, frontend handoff 등 관련 없는 대형 문서를 함께 포함한다. merge 전에 PR 범위 분리 또는 의도된 포함 확인이 필요하다.
- 테스트 결과: Billing `/Users/msde76/billing`과 Learning Core `/Users/msde76/app-back-end-learning-core`에서 각각 `./gradlew clean test`를 실행했고 둘 다 `BUILD SUCCESSFUL`이었다. Learning Core `git diff --check`도 통과했다.
- 유지한 계약: 정상 순서 `reserve → durable Session commit → confirm`, confirm 불명 시 cancel 금지·status 우선, same-key same-Session 수렴, feature flag 기본 off, 실제 AWS·외부 서비스 미호출과 production gate를 유지했다.
- 결정사항: 테스트 성공만으로 TMI-116을 merge-ready로 판단하지 않는다. 위 상태 수렴·동시성·strict response 검증을 수정하고 해당 회귀 테스트를 추가해야 한다.
- 위험 요소: 현 상태로 flag를 켜면 일시적 Billing/Mongo 장애가 사용자별 active operation을 영구 정체시키거나 이미 commit 중인 shared reservation을 취소할 수 있다.
- 다음 작업: Learning Core에서 세 결함을 수정하고 process-crash replay, Mongo transient/unknown commit 동시성, malformed/missing Billing 2xx contract test를 추가한 뒤 replica-set staging failure injection과 Lattice E2E를 진행한다.

## 2026-08-31 — Learning Core TMI-116 수정·merge 재검증

<!-- codex-turn:tmi-116-fixes-merged-reverified -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`; Learning Core `develop`
- Jira: `TMI-115`, `TMI-116`; Jira 상태·댓글은 변경하지 않았다.
- 작업 목표: 사용자가 merge와 수정 완료를 알린 뒤 Learning Core의 merge 상태와 기존 P1/P2 세 finding 해소 여부를 최신 코드·테스트로 재검증한다.
- 변경 파일: Billing `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`에 검증 결과만 기록했다. Billing·Learning Core 애플리케이션 코드, 외부 계약, Jira, AWS와 배포 설정은 변경하지 않았다.
- merge 확인: Learning Core 수정 commit `c3e3c82`와 PR #24 merge commit `d95d18b`가 local·`origin/develop`에 반영됐다. Billing은 기존 PR #3 merge commit `39e424d` 상태를 유지한다.
- 검증 결과: same-key operation을 Session보다 먼저 조회해 `SESSION_COMMITTED + ENTITLEMENT_CONFIRMING`을 confirm/status로 복구한다. Mongo transient/unknown commit과 동시성 예외에서는 shared reservation cancel을 금지하고 operation·Session 관측으로 수렴한다. strict mapper와 endpoint response constructor/Saga semantic validation이 scalar/date/enum coercion, missing field, confirm OPEN/timestamp와 cancel/status timestamp를 fail-closed한다.
- 테스트 결과: 최신 Learning Core `develop`에서 `./gradlew clean test`를 실행해 `BUILD SUCCESSFUL`을 확인했다. 앞서 확인한 Billing 전체 테스트 성공 결과도 유지되며 이번 재검증에서 Billing 코드는 변경되지 않았다.
- 유지한 계약: `reserve → durable Session commit → confirm`, confirm 불명 시 cancel 금지·status 우선, same-key same-Session 수렴, public API DTO·BaseResponse 불변, feature flag 기본 off와 production gate를 유지했다.
- 결정사항: 기존 세 finding은 해소됐으며 현재 검토 범위에서 새 merge 차단 코드 결함은 확인되지 않았다.
- 위험 요소: Mock/unit 회귀만으로 실제 Mongo replica-set의 transient transaction label·unknown commit 결과와 AWS network/auth 경계를 완전히 증명할 수 없다. 실제 Lattice/IAM/SG 및 INITIAL·REPLACEMENT staging E2E 전에는 production flag를 활성화하지 않는다.
- 다음 작업: replica-set failure injection, index migration, Lattice/IAM/SG 연결과 staging reserve/commit/confirm/status E2E를 완료한다. 이후 AttemptGroup 상태 outbox/publisher와 Billing consumer vertical slice를 진행한다.

## 2026-08-31 — TMI-116 이후 다음 작업 설명

<!-- codex-turn:next-attempt-group-status-integration-explained -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`; Learning Core `develop`
- Jira: 기존 `TMI-115`, `TMI-116` 참고. 신규 Jira 생성·상태·댓글 변경은 수행하지 않았다.
- 작업 목표: TMI-116 merge와 수정 검증 다음에 진행할 개발 작업의 목적, 상태 전이, 저장소별 책임과 안전한 구현 순서를 설명한다.
- 변경 파일: Billing `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`에 분석 결과만 기록했다. 애플리케이션 코드·계약·Jira·AWS와 Learning Core 파일은 변경하지 않았다.
- 확인 결과: Billing ADR과 통합 계약에는 `POST /internal/v1/attempt-group-events`, `AttemptGroupStatusChanged`, `GRADING`·`COMPLETED`·`RETAKE_AVAILABLE` 계약이 있으나 Billing consumer와 Learning Core durable outbox/publisher는 아직 없다. TMI-116이 ExamSession에 `attemptGroupId`를 저장해 이 연동의 선행 조건은 충족했다.
- 동작: 모든 필수 submit이 durable 접수되면 GRADING, 필수 feedback·valid score·Summary가 모두 사용자 조회 가능하면 COMPLETED, 결과 생성의 최종 실패면 제한된 failureCode와 RETAKE_AVAILABLE을 보낸다. RETAKE_AVAILABLE은 새 Claim·Grant·refund가 아니라 같은 consumption의 REPLACEMENT를 허용한다.
- 권장 순서: wire schema·전이·failureCode를 최종 동결하고 Billing strict consumer/inbox/Transaction을 먼저 구현·배포한 뒤 Learning Core local state와 같은 Transaction의 outbox 및 lease/retry SigV4 publisher를 구현·활성화한다. producer-before-consumer 전송 손실을 피하기 위해 consumer-first를 유지한다.
- 테스트 결과: 코드 변경이 없는 설명 작업이므로 Gradle 테스트를 다시 실행하지 않았다. 직전 최신 Learning Core `./gradlew clean test`와 Billing 전체 테스트 성공 결과를 기준으로 현재 구현 부재와 계약을 읽기 전용 대조했다.
- 유지한 계약: confirm은 Session durable commit 직후 소비 확정이고 Summary 완료와 분리한다. COMPLETED는 다시 열지 않으며 RETAKE_AVAILABLE은 무료권 복원·새 차감이 아니다. provider 원문·자유 형식 실패 사유를 Billing에 보내지 않는다.
- 결정사항: 다음 개발 vertical slice는 AttemptGroup 상태 연동이며 Billing consumer를 먼저 만든다. 실제 인프라 검증은 별도 운영 gate이지만 production 활성화 전에 함께 완료한다.
- 위험 요소: Learning Core가 DB 상태 변경과 event 생성을 원자적으로 묶지 않으면 process crash에서 상태 event가 유실된다. stale/abandoned Session fencing이 없으면 과거 Session이 현재 group을 잘못 완료하거나 재응시 가능으로 바꿀 수 있다.
- 다음 작업: 확정된 ADR을 기준으로 Billing consumer 구현 계획서를 작성하고 미확정 failureCode·event revision/ordering·missing target 처리만 명시적으로 확정한 뒤 Jira 승인과 구현으로 진행한다.

## 2026-08-31 — AttemptGroup 상태 연동 정책 선택지 설명

<!-- codex-turn:attempt-group-policy-options-explained -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`; Learning Core `develop`
- Jira: 신규 Jira 생성·수정·상태 변경 없음. 기존 `TMI-115`, `TMI-116` 참고.
- 작업 목표: AttemptGroup consumer/outbox 구현 전에 남은 정책 항목별 선택지와 장단점을 설명하고 권장 조합을 제시한다.
- 변경 파일: Billing `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`에 분석 결과만 기록했다. 애플리케이션 코드, ADR, wire schema, Jira, AWS와 Learning Core 파일은 변경하지 않았다.
- 계약 확인: `COMPLETED` evidence 세 조건, sequence 없는 at-least-once event, eventId/digest 멱등성, active Session fencing과 abandoned/stale 204는 이미 ADR에 확정돼 있다. 새 선택은 이 계약 안의 구체적 수렴·운영 정책으로 한정했다.
- 선택지: failureCode는 고정 저카디널리티 allowlist/세분화 code/free string, ordering은 상태 전이표/occurredAt LWW/새 revision, missing target은 원인별 503·204·409/일괄 503/일괄 409, outbox는 pending 무TTL 지수 backoff/유한 재시도 후 폐기/동기 호출로 구분했다.
- 권장안: `1A·2A·3A·4A`다. 개인정보·metric cardinality를 제한하고 기존 schema를 유지하며, 일시적 순서 역전은 재시도하고 stale과 구조 충돌은 명확히 분리하며, retryable event를 유실하지 않는 조합이다.
- 테스트 결과: 코드와 계약을 변경하지 않은 설명 작업이라 Gradle 테스트를 다시 실행하지 않았다. ADR-001과 통합 계약의 현재 event/status/error 규칙을 읽기 전용으로 대조했다.
- 유지한 계약: provider 원문·자유 문자열 금지, COMPLETED 재개방 금지, RETAKE_AVAILABLE의 같은 consumption replacement, producer-before-consumer 금지와 production gate를 유지했다.
- 결정사항: 선택지는 제안 상태이며 사용자 승인 전에는 확정하지 않는다.
- 위험 요소: occurredAt LWW는 clock skew에 취약하고, 모든 missing target을 409로 처리하면 정상 순서 역전이 영구 유실되며, pending outbox TTL은 장기 Billing 장애에서 event를 삭제할 수 있다.
- 다음 작업: 사용자가 `1A·2A·3A·4A` 또는 대안을 승인하면 CONTRACT_DECISIONS·ADR과 구현 계획서에 반영한다.

## 2026-08-31 — 상태 전이표와 Session fencing 상세 설명

<!-- codex-turn:attempt-group-transition-fencing-explained -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`
- Jira: 신규 Jira 생성·수정 없음. 기존 `TMI-115`, `TMI-116` 참고.
- 작업 목표: AttemptGroup ordering 권장안인 상태 전이표와 Session fencing이 sequence 없이 역순·중복·재응시 event를 어떻게 처리하는지 상세히 설명한다.
- 변경 파일: Billing `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 코드·ADR·wire schema·Jira·AWS와 Learning Core는 변경하지 않았다.
- 설명 내용: event sessionId와 group.activeSessionId, AttemptSession.ACTIVE를 먼저 대조하고 통과한 event만 현재 group status의 허용 전이표에 넣는다. 이전/abandoned/failed Session은 204 stale, 동일 status는 no-op, 허용 전진만 Transaction/CAS로 적용하고 COMPLETED는 재개방하지 않는다.
- 재응시: RETAKE_AVAILABLE에서 기존 Session을 FAILED terminal로 닫고, REPLACEMENT confirm이 새 Session을 ACTIVE·group을 OPEN으로 바꾼 뒤 새 Session ID의 event만 수용한다. 무료 Claim/Grant/consumption은 새로 만들지 않는다.
- 동시성: inbox 기록, AttemptGroup 전이와 AttemptSession terminal 전이를 하나의 Mongo Transaction에서 expected version CAS로 수행해 동시에 도착한 COMPLETED/RETAKE_AVAILABLE 중 하나만 승리하게 한다.
- 테스트 결과: 설명과 기록만 변경해 Gradle 테스트를 실행하지 않았다. 현재 AttemptGroup·AttemptSession entity/repository와 ADR 상태 머신을 읽기 전용 대조했다.
- 유지한 계약: sequence 필드를 추가하지 않고 occurredAt LWW를 사용하지 않는다. active Session fencing, stale 204, COMPLETED 불가역과 same-consumption replacement를 유지한다.
- 결정사항: 상세 설명일 뿐 ordering 권장안은 아직 사용자 최종 승인 전이다.
- 위험 요소: group.activeSessionId만 확인하고 AttemptSession state/subject/group을 함께 확인하지 않으면 폐기 Session event가 통과할 수 있다. CAS 없이 조회 후 저장하면 동시에 도착한 terminal event가 서로 덮어쓸 수 있다.
- 다음 작업: 사용자가 2A를 승인하면 정확한 transition matrix와 APPLIED/DUPLICATE/STALE/CONFLICT 결과를 계획서·ADR에 고정한다.

## 2026-08-31 — revision 없이 가능한 보장 범위 설명

<!-- codex-turn:no-event-revision-safety-boundary-explained -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`
- Jira: 신규 Jira·상태 변경 없음.
- 작업 목표: 상태 전이표와 Session fencing이 왜 revision 없이 중복·역순·재응시 event를 처리할 수 있는지, 그리고 무엇은 보장하지 못하는지 명확히 설명한다.
- 변경 파일: Billing `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 코드·ADR·wire schema·Jira·AWS·Learning Core는 변경하지 않았다.
- 보장 분해: eventId/digest는 동일 event 재전송, activeSessionId와 AttemptSession state는 재응시 세대, 단방향 transition matrix는 상태 역행, Mongo document version CAS는 동시 write race를 각각 차단한다. 서로 다른 문제를 하나의 event revision 없이 기존 식별자·상태·DB version으로 해결한다.
- 한계: 같은 active Session에 모순되는 COMPLETED와 RETAKE_AVAILABLE이 모두 생성되면 consumer는 어느 event가 producer 기준 최신·정답인지 알 수 없다. 첫 terminal commit을 보존할 수 있을 뿐 정확한 순서를 복원할 수 없다.
- 전제: Learning Core는 local 결과 판정과 outbox terminal event 생성을 같은 Transaction/CAS로 묶고 Session당 terminal event 하나만 생성해야 한다. 이 producer 불변식이 없거나 consumer가 모순 event의 최신성을 판단해야 하면 sessionEventRevision이 필요하다.
- 테스트 결과: 설명·기록만 변경해 Gradle 테스트를 실행하지 않았다.
- 유지한 계약: eventId/digest 멱등성, active Session fencing, COMPLETED 불가역, same-consumption replacement와 provider 원문 금지를 유지한다.
- 결정사항: 2A의 안전성은 정확한 전체 순서 복원이 아닌 비역행·세대 격리·단일 적용 보장으로 정의한다. 사용자 승인은 아직 받지 않았다.
- 위험 요소: producer terminal 단일성 없이 revision을 생략하면 먼저 도착한 모순 event가 승리하므로 제품 정답을 보장할 수 없다.
- 다음 작업: 사용자가 이 보장 범위와 producer terminal 단일성 전제를 승인할지, 아니면 sessionEventRevision을 추가할지 선택한다.

## 2026-08-31 — AttemptGroup 1A·2A·3A·4A 승인 및 trace 정책 반영

<!-- codex-turn:attempt-group-all-a-decisions-approved -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`
- Jira: 신규 Jira 생성·수정·상태 변경 없음.
- 작업 목표: 사용자가 승인한 failureCode, ordering, missing target, outbox 권장안 전체와 traceId 운영 추적 보완을 확정 계약에 반영한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·테스트·Jira·AWS·Learning Core는 변경하지 않았다.
- 확정 내용: 네 failureCode allowlist, revision 없는 active Session fencing·단방향 전이와 producer terminal 단일성, projection not ready 503/5초·stale 204·관계 충돌 409, PENDING 무TTL 지수 backoff·DELIVERED 30일·DEAD_LETTER 90일을 확정했다.
- trace 보완: W3C traceparent를 header로 전파하고 비동기 outbox trace를 continue/link해 양쪽 구조화 로그에 traceId와 eventId를 기록한다. trace context는 event JSON/digest/business key와 metric label에 넣지 않고 baggage와 사용자·Session·AttemptGroup·provider 원문을 trace attribute에서 제외한다.
- 현재 기반 확인: Learning Core에는 requestId MDC와 Sentry가 있으나 W3C traceparent 기반 cross-service tracing은 확인되지 않았고 Billing에는 tracing 기반이 없다. 따라서 단순 log field 추가가 아니라 양쪽 propagation/extraction과 logging integration 구현이 필요하다.
- 테스트 결과: 문서 계약만 변경해 Gradle 테스트를 실행하지 않았다. `git diff --check`로 문서 형식을 검증한다.
- 유지한 계약: COMPLETED evidence 세 조건, same-consumption replacement, eventId/digest 멱등성, provider 원문 금지, C3-D SigV4/Lattice와 production gate를 유지했다.
- 결정사항: `1A·2A·3A·4A`는 승인 완료다. traceId는 coarse failureCode의 상세 조사 한계를 보완하지만 failureCode, eventId와 outbox 상태를 대체하지 않는다.
- 위험 요소: traceId를 metric label로 사용하면 cardinality 비용이 폭증하며, header propagation 없이 각 서버가 독립 생성하면 cross-service 추적이 되지 않는다. 로그 보존기간이 dead-letter 조사기간보다 짧으면 trace 상세가 먼저 사라질 수 있다.
- 다음 작업: 확정 계약을 기준으로 Billing consumer 구현 계획서를 작성하고 tracing 도입 범위·로그 보존기간·outbox schema/index를 구체화한다.

## 2026-08-31 — trace 로그 service·duration·event age 계약 추가

<!-- codex-turn:attempt-group-log-service-duration-added -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`
- Jira: 신규 Jira 생성·수정·상태 변경 없음.
- 작업 목표: AttemptGroup event 추적 로그에서 어느 서비스가 처리했고 각 단계와 전체 전달에 얼마나 걸렸는지 확인할 수 있도록 관측성 계약을 보완한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 코드·테스트·Jira·AWS·Learning Core는 변경하지 않았다.
- 확정 내용: 공통 구조화 로그 field를 `service`, `operation`, `outcome`, `traceId`, `eventId`, `durationMs`로 정했다. service는 learning-core/billing, operation·outcome은 고정 low-cardinality allowlist다.
- 시간 의미: durationMs는 monotonic clock으로 측정한 해당 publish/consume 단계 처리 시간이고, Billing consume의 eventAgeMs는 event occurredAt부터 수신까지 outbox 대기·network·retry를 포함한 지연이다. 음수 event age는 0으로 정규화하고 clock-skew counter를 기록한다.
- metric 규칙: durationMs/eventAgeMs는 histogram 값으로 사용할 수 있지만 label로 사용하지 않는다. traceId/eventId도 metric label에서 금지하고 service/operation/outcome만 low-cardinality label로 허용한다.
- 테스트 결과: 문서 계약만 변경해 Gradle 테스트는 실행하지 않았고 `git diff --check`를 수행한다.
- 유지한 계약: trace context는 event JSON/digest/idempotency/domain aggregate에 포함하지 않고 baggage와 사용자·Session·AttemptGroup·candidate·provider 원문을 log/trace attribute에서 제외한다.
- 결정사항: 단일 elapsed field의 모호함을 피하기 위해 서비스 내부 처리 duration과 end-to-end event age를 분리한다.
- 위험 요소: System.currentTimeMillis 차이로 duration을 재면 clock 보정에 흔들릴 수 있으므로 monotonic clock을 사용해야 한다. eventAgeMs는 서비스 clock skew 영향을 받으므로 별도 skew 관측이 필요하다.
- 다음 작업: Billing consumer 계획서에서 로그 event name, operation/outcome allowlist, timer metric 이름과 tracing instrumentation을 구체화한다.

## 2026-08-31 — PLAN-005 AttemptGroup status event consumer 계획서 작성

<!-- codex-turn:plan-005-attempt-group-status-consumer-written -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`
- Jira: 미생성. 기존 `TMI-115`, Learning Core `TMI-116` 참고.
- 작업 목표: 승인된 AttemptGroup 상태·failureCode·missing target·outbox·trace/log 정책을 Billing consumer 구현 가능한 vertical slice 계획으로 구체화한다.
- 변경 파일: `docs/plans/PLAN-005-attempt-group-status-event-consumer.md` 신규, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·테스트·Jira·AWS·Learning Core 파일은 변경하지 않았다.
- 범위: `POST /internal/v1/attempt-group-events`, strict decode/canonical digest, 공용 inbox eventId 멱등성, subject/group/active Session fencing, group/session Transaction·CAS, error/security, tracing·structured log·metric과 replica-set concurrency/failure test를 포함했다.
- inbox 결정: Identity revision entity/package를 이동하지 않고 동일 `inbound_event_inbox` collection을 사용하는 AttemptGroup 최소 view를 둔다. event payload·evidence·failureCode·user/group/session ID를 inbox에 복제하지 않고 global eventId unique와 120일 TTL을 재사용한다. Identity partial index가 Learning Core document를 제외하므로 schema v3를 유지한다.
- 상태 결정: OPEN/GRADING에서 terminal direct 전진, COMPLETED 불가역, RETAKE에서 Session FAILED·activeSessionId 해제, REPLACEMENT confirm만 새 active Session/OPEN을 만든다. producer Session당 terminal event 단일성은 후속 Learning Core 계획의 필수 전제다.
- 관측성: Billing Micrometer Tracing+OpenTelemetry W3C traceparent 수신, service/operation/outcome/traceId/eventId/durationMs/eventAgeMs 구조화 로그와 low-cardinality metric 계획을 포함했다. exporter/backend와 운영 credential은 비범위다.
- 테스트 결과: 문서 계획만 작성해 Gradle 테스트는 실행하지 않았다. 기존 코드·ADR·통합 계약을 읽기 전용 대조하고 종료 전 문서 링크, diff와 민감정보를 검증한다.
- 유지한 계약: provider 원문 금지, event JSON/digest에서 trace 분리, TrialClaim 3년·Claim/Grant/consumption 불변, same-consumption replacement, Learning Core role 최소 권한과 consumer-first 배포를 유지했다.
- 결정사항: PLAN-005는 Billing consumer만 구현하고 Learning Core outbox/publisher는 consumer 선배포 후 별도 PLAN/Jira로 분리한다. 현재는 계획 승인 대기이며 Jira를 생성하지 않았다.
- 위험 요소: 실제 producer terminal 단일성·outbox atomicity는 Billing unit test만으로 보장할 수 없다. cross-service trace는 현재 두 서버에 완성된 W3C 기반이 없어 후속 Learning Core instrumentation과 staging 검증이 필요하다.
- 다음 작업: 사용자가 PLAN-005를 검토·승인하면 Billing Jira를 생성하고 구현한다. 이후 Learning Core outbox/publisher 계획·Jira와 Lattice staging E2E를 진행한다.

## 2026-08-28 — TMI-113 완료 처리

<!-- codex-turn:jira-tmi-113-closed -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: `TMI-113` — `[Billing] Reservation lifecycle 구현` (`완료`, 담당자 미지정)
- 작업 목표: 사용자의 명시적 승인에 따라 PLAN-003 구현 Jira를 완료 상태로 전환하고 실제 완료 category를 확인한다.
- 변경 파일: Jira `TMI-113`, `docs/plans/PLAN-003-reservation-lifecycle.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·테스트·Jira 설명·담당자·Git 브랜치는 변경하지 않았다.
- 수행 내용: 전환 전 이슈가 `해야 할 일` 상태이고 global `완료` transition ID 41이 사용 가능함을 확인한 뒤 전환했다. 전환 응답과 재조회 결과 status `완료`, status category `완료`를 확인했다.
- 완료 근거: PLAN-003 confirm·cancel·status·expiry 구현과 직전 `./gradlew clean test` 총 82개 성공, 실패 0, 오류 0, skip 0 결과를 사용했다.
- 테스트 결과: 이번 작업은 Jira 상태와 문서 기록만 변경해 Gradle 테스트를 다시 실행하지 않았다. 직전 최종 전체 회귀 82개 성공 결과는 유지되며 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: Jira 완료는 production 배포·caller 활성화를 뜻하지 않는다. AttemptGroup 상태 event, Learning Core saga/reconciliation, expiry 운영 활성화, 실제 Lattice/IAM/SG와 staging E2E gate를 계속 유지한다.
- 결정사항: TMI-113은 완료됐고 Jira 댓글·worklog·설명·담당자는 추가로 수정하지 않았다.
- 위험 요소: PLAN-003만 완료한 상태에서 production caller를 열면 Learning Core confirm 불명과 실제 AWS direct-bypass 검증 공백이 남는다.
- 다음 작업: 별도 승인으로 AttemptGroup 상태 event consumer 계획과 Jira를 작성한다. 이후 재가입 owner rebind, Learning Core saga·Lattice staging E2E를 순서대로 진행한다.

## 2026-08-28 — Billing 패키지 구조 비교와 개편 초안

<!-- codex-turn:billing-package-structure-draft -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: Identity·Learning Core의 domain/global 기능 우선 패키지 구조를 실제 코드에서 확인하고 Billing 구조를 같은 방향으로 바꾸는 초안을 작성한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·테스트·계약·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 분석 내용: Identity는 `web.tosunsaeng.identity.domain` 아래 auth/user와 세부 기능, Learning Core는 `web.tosunsaeng.domain` 아래 exams/withdrawal을 두며 기능 내부에서 api/application/dto/converter/domain/repository/exception/config를 선택적으로 사용한다. 공통 security·response·exception·observability는 global에 둔다.
- Billing 현황: 최상단 config와 feature package가 혼재하고 `reservation` 47개 클래스 안에 Reservation, TrialClaim, entitlement ledger와 AttemptGroup 책임이 함께 있다. `trialeligibility`는 별도지만 domain 상위 namespace가 없고 domain 전용 properties도 root config에 있다.
- 권장 초안: `web.tosunsaeng.billing` 루트는 유지하고 `domain/{eligibility/trial,entitlement,entitlement/trial,reservation,attempt}`와 `global/{config,security,exception,response,infrastructure/mongodb}`로 재편한다. 각 domain은 필요한 api/application/dto/converter/domain/entity·enums/repository/exception/config만 만든다.
- 실행한 테스트와 결과: 읽기 전용 구조 분석과 문서 기록만 수행해 Gradle 테스트는 실행하지 않았다. Identity·Learning Core AGENTS와 실제 main package tree, 대표 controller/service/converter/domain exception/global exception 구성을 확인했다.
- 유지한 계약: package 리팩터링 초안은 API URL·Method·DTO·error envelope, Mongo collection/index/document field, transaction·CAS·멱등성, security route와 production gate를 변경하지 않는다. Identity와 Learning Core는 읽기 전용으로 유지했다.
- 결정사항: 단순 package 이동과 책임 재설계를 분리한다. 1차는 package/import/test mirror만 이동하고, 2차는 feature exception·converter 정리, 3차는 ReserveService와 lifecycle orchestration의 협력 컴포넌트 분리로 제안한다.
- 위험 요소: 모든 이동과 서비스 분해를 한 번에 하면 Spring component scan, Mongo document mapping, exception envelope와 transaction 경계 회귀 원인을 분리하기 어렵다. 이름만 domain 구조로 바꾸고 ReserveService 책임을 그대로 두면 가독성 문제 일부는 남는다.
- 다음 작업: 사용자가 목표 tree와 domain 경계를 승인하면 별도 리팩터링 계획서와 Jira를 만들고 package-only migration부터 수행한다.

## 2026-08-28 — Billing domain/global 패키지 구조 개편

<!-- codex-turn:billing-domain-global-package-refactor -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 사용자 승인에 따라 Billing을 Identity·Learning Core와 유사한 기능 우선 `domain`/`global` 구조로 실제 개편하고 외부·저장 계약과 런타임 동작을 보존한다.
- 변경 파일: `src/main/java/web/tosunsaeng/billing/domain/**`, `src/main/java/web/tosunsaeng/billing/global/**`, `src/test/java/web/tosunsaeng/billing/domain/**`, `src/test/java/web/tosunsaeng/billing/global/**`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 기존 root `config`, `reservation`, `trialeligibility`, `global/api`, `global/mongodb` source는 새 package로 이동했다. 작업 전부터 수정돼 있던 `docs/plans/PLAN-003-reservation-lifecycle.md`와 기존 기록 변경은 보존했다.
- 구조 변경: Trial eligibility는 `domain/eligibility/trial`, TrialClaim·candidate alias·subject link는 `domain/entitlement/trial`, grant·ledger는 `domain/entitlement`, AttemptGroup·Session은 `domain/attempt`, Reservation lifecycle은 `domain/reservation`으로 분리했다. 공통 Security·Mongo 설정과 Mongo infrastructure, error handler·response는 `global`로 이동했다.
- 책임 정리: `ReservationConverter`가 request→command와 snapshot/result→response 변환을 담당하도록 Controller의 수동 조립을 이동했다. `ReservationException`과 `TrialEligibilityException`이 feature 오류 code를 생성하고 공통 `InternalApiExceptionHandler`는 base exception을 동일하게 처리한다.
- 테스트 결과: 중간 `./gradlew compileTestJava`를 반복해 package/import와 converter·exception 의존성을 검증했다. 첫 전체 테스트는 Security MVC slice에 `ReservationConverter` mock이 없어 3개가 context 시작 전에 실패했고 test slice dependency를 보완했다. 최종 `./gradlew clean test`는 총 82개 성공, 실패 0, 오류 0, skip 0이며 `git diff --check`도 통과했다.
- 유지한 계약: internal URL·method·DTO JSON·16 KiB strict decode, canonical hash, Mongo collection·index·business field, Transaction·CAS·unique index·멱등성, Claim retention, Reservation/AttemptGroup 상태 전이, security default deny와 workload route 구분을 변경하지 않았다. Identity·Learning Core와 Jira·AWS·배포 설정은 변경하지 않았다.
- 결정사항: `web.tosunsaeng.billing` root는 유지하고 feature 안에 필요한 `api`, `application`, `config`, `converter`, `dto`, `domain`, `exception`, `repository`만 둔다. 공통 error envelope와 handler는 global, feature error factory는 각 domain에 둔다. 이번에는 큰 orchestration service 내부 분해를 범위에서 제외했다.
- 위험 요소: Spring Data MongoDB의 기본 `_class` 값은 Java fully-qualified class name을 포함할 수 있어 package 이동 전에 생성한 document가 있다면 old class resolution 또는 migration 문제가 생길 수 있다. Billing 미배포 전제에서는 최초 schema로 적용 가능하지만, 보존할 기존 환경 데이터가 있다면 배포 전에 `_class` 표본과 migration 필요성을 확인해야 한다.
- 다음 작업: 후속 AttemptGroup 상태 event consumer 계획 전에 새 package 구조를 기준으로 작업한다. Reserve/Lifecycle orchestration 분해가 필요하면 transaction 경계와 race 테스트를 유지하는 별도 리팩터링 계획으로 진행한다.

## 2026-08-28 — 다음 작업 AttemptGroup 상태 event consumer 정리

<!-- codex-turn:next-attempt-group-event-consumer-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: PLAN-003 Reservation lifecycle과 package 구조 개편 다음에 구현할 vertical slice의 목적·범위·완료 조건·미확정 세부사항을 정리한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약 결정서·ADR·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 분석 내용: 다음 작업은 Learning Core `POST /internal/v1/attempt-group-events` consumer다. GRADING은 replacement를 차단하고, COMPLETED는 feedback·valid score·summary evidence가 모두 true일 때 group과 active Session을 terminal 처리하며, RETAKE_AVAILABLE은 새 Claim/grant/refund 없이 기존 consumption·group·mockExamId의 replacement를 다시 허용해야 한다.
- 예상 구현: 16 KiB schema v1 strict decode, canonical SHA-256, shared inbox의 eventId/digest 멱등성, active Session fencing, group/session CAS, inbox·projection 단일 Mongo Transaction, 204 APPLIED/DUPLICATE/STALE 수렴, stable 400/409/422/503 error와 low-cardinality metric을 새 `domain/attempt` 구조에 구현한다.
- 테스트 결과: 이번 작업은 설명과 기록만 변경해 Gradle 테스트는 실행하지 않았다. ADR-001 T5 Transaction·AttemptGroup state machine·Mongo schema, 통합 계약과 현재 AttemptGroup/Session repository·Security route를 읽기 전용으로 대조했고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: confirm은 Session durable commit 직후 소비를 확정하며 Summary 완료와 분리한다. RETAKE_AVAILABLE은 무료권 복원·새 지급이 아니라 same consumption replacement이고 COMPLETED는 다시 열지 않는다. Billing은 Learning Core의 질문·답안·점수·feedback·summary·AI/provider 원문을 저장하지 않는다.
- 결정사항: 다음 작업의 권장 범위만 정리했으며 PLAN 번호, Jira, 새 error code, failureCode 목록과 transition 확장 정책은 확정하지 않았다. 기존 collection/index를 재사용할 수 있으나 eligibility package에 묶인 inbox entity/repository는 cross-domain 공통 위치와 nullable event metadata로 정리해야 한다.
- 위험 요소: event에 sequence가 없어 OPEN→GRADING→terminal 순서를 기계적으로 강제하면 terminal event가 먼저 도착한 경우 영구 재시도가 생길 수 있다. 반대로 stale Session fencing 없이 status를 적용하면 abandon된 Session이 현재 group을 COMPLETED 또는 RETAKE_AVAILABLE로 잘못 바꿀 수 있다. missing group/session을 terminal conflict로 처리하면 confirm/outbox 순서 역전 복구가 불가능할 수 있다.
- 다음 작업: 사용자가 진행을 승인하면 먼저 세부 transition·missing prerequisite·failureCode 정책을 PLAN-004 초안에서 확정하고, 별도 승인 후 Jira를 생성한 다음 구현한다. 이후 owner rebind와 Learning Core saga/outbox·Lattice staging E2E를 진행한다.

## 2026-08-28 — 현재 무료 모의고사 소비 로직 설명

<!-- codex-turn:current-free-exam-consumption-flow-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 현재 구현 코드에서 무료 모의고사 grant가 생성·hold·confirm 소비·cancel/expiry 복원·replacement되는 흐름과 실제 차감 시점을 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 분석 내용: eligibility event는 projection만 갱신하고 Claim/grant를 만들지 않는다. 최초 INITIAL reserve Transaction이 필요 시 TrialClaim과 `FREE_EXAM_ONCE` total 1 unit grant를 생성한 뒤 available을 held로 이동한다. Session durable commit 후 confirm Transaction이 held를 consumed로 전환하는 시점이 실제 소비다.
- cancel/expiry 동작: confirm 전 cancel·5분 expiry는 HELD allocation을 RELEASED로 바꾸고 held unit을 available로 복원하며 `RELEASED` ledger를 append한다. TrialClaim·claimedAt·3년 retention은 유지하므로 새 무료권을 지급하지 않고 기존 단일 grant를 다시 사용할 수 있게 한다.
- confirm 이후 동작: CONFIRMED consumption은 일반 cancel/expiry로 되돌리지 않는다. 결과 최종 실패 시에도 grant는 consumed이며, 후속 AttemptGroup consumer가 RETAKE_AVAILABLE로 바꾼 뒤 REPLACEMENT가 같은 consumption·group·mockExamId를 재사용한다. 이 consumer는 아직 미구현이다.
- 테스트 결과: 이번 작업은 코드 설명과 기록만 변경해 Gradle 테스트를 실행하지 않았다. `ReserveService`, `ReservationLifecycleService`, grant/allocation/ledger entity와 repository의 실제 상태 전이·CAS 조건을 읽기 전용으로 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: `reserve → Session commit → confirm`, 5분 hold, Summary와 confirm 분리, cancel/expiry Claim 불변, confirmed 소비 복원 금지, same-consumption replacement, append-only ledger와 phone candidate당 단일 Claim을 유지한다.
- 결정사항: 현재 free grant는 paid balance의 10 credits를 차감하는 모델이 아니라 `FREE_EXAM_ONCE` 1 unit 모델임을 명확히 했다. 시험당 10-credit paid 차감은 결제 기능 구현 시 별도 ledger allocation 정책으로 추가한다.
- 위험 요소: reserve를 최종 소비로 오해하면 cancel/expiry 복원을 중복 지급으로 볼 수 있고, 반대로 Summary 완료까지 confirm을 늦추면 5분 hold 만료 후 같은 무료권이 중복 사용될 수 있다. AttemptGroup consumer 전에는 결과 최종 실패가 자동으로 RETAKE_AVAILABLE로 수렴하지 않는다.
- 다음 작업: 승인된 순서대로 AttemptGroup 상태 event consumer 계획을 확정한 뒤 구현해 confirmed consumption의 완료·최종 실패·same-consumption 재응시를 end-to-end로 연결한다.

## 2026-08-28 — 멘토의 사전 무료 모의고사 정의 방식 비교

<!-- codex-turn:mentor-predefined-free-exam-model-compared -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: “무료 모의고사라는 이름으로 미리 생성하고 이후 공통 처리” 제안을 현재 lazy TrialClaim/grant·Reservation 구현과 비교해 사용자의 이해와 장단점을 검증한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 분석 내용: 전역 catalog/benefit definition 하나를 미리 만드는 것과 사용자별 grant/Session을 미리 만드는 것을 구분했다. 전자는 프로모션 정책 재사용에 유리하지만 후자는 미사용 데이터와 revoke·expiry 정합성 비용을 늘린다. 현재 구현은 최초 reserve에서 Claim과 1-unit grant를 lazy issue하고 실제 시험마다 최소 AttemptSession projection을 만든다.
- 현재 로직 정정: 최종 consumption은 feedback 생성 때가 아니라 Learning Core Session durable commit 뒤 confirm이다. feedback·valid score·summary 완료는 AttemptGroup COMPLETED이며, 최종 실패는 consumed grant를 복원하지 않고 RETAKE_AVAILABLE과 same-consumption REPLACEMENT로 처리한다.
- 확장성 평가: 현재 `grantType`, `sourceType`, `sourceId`, allocation과 append-only ledger는 공통 entitlement 기반이지만 `FREE_EXAM_ONCE`와 resolver가 하드코딩돼 있다. 새 프로모션을 이름만으로 추가할 수는 없고 stable code, campaign/source, unit, expiry, eligibility limit, stacking/priority, policyVersion과 dedupe가 필요하다.
- 테스트 결과: 설명·기록만 변경해 Gradle 테스트는 실행하지 않았다. `EntitlementGrant`, `AttemptSession`, 계약 결정서의 현재 무료 resolver와 후속 catalog/promotion 계약을 읽기 전용으로 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: eligibility event만으로 지급하지 않고 최초 reserve에서 Claim/grant를 원자적으로 생성한다. `reserve → Session commit → confirm`, confirmed consumption 복원 금지, same-consumption replacement, raw phone 비저장과 append-only ledger를 변경하지 않았다.
- 결정사항: 권장안은 catalog/offer definition만 사전 생성하고 사용자별 Claim/grant는 lazy issue하며 무료·promotion·paid가 공통 allocation/lifecycle을 재사용하는 hybrid다. 이는 설명·권장안이며 현재 계약 변경으로 확정하지 않았다.
- 위험 요소: display name을 식별자로 사용하면 이름 변경·다국어·중복 campaign에서 ledger와 dedupe가 깨진다. Billing AttemptSession을 제거하면 stale Session event와 restart fencing을 보장하기 어렵다. 모든 verified user에게 grant를 미리 발급하면 사용하지 않는 grant와 탈퇴·revoke cleanup 부담이 커진다.
- 다음 작업: 현재 순서대로 AttemptGroup event consumer를 먼저 완성한다. 결제·promotion 착수 시 별도 계약에서 catalog/offer/grant resolver와 consumption 우선순위를 확정한다.

## 2026-08-28 — TrialClaim 사전 생성 제안 비교

<!-- codex-turn:precreated-trial-claim-option-compared -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 멘토의 제안이 phone verification 시 TrialClaim을 미리 생성하는 방식에 가깝다는 사용자 보충을 바탕으로 현재 최초 reserve lazy creation과 정확히 비교한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약 결정서·ADR·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 분석 내용: 현재 verified event는 TrialEligibility만 저장하고 최초 reserve가 Claim·candidate alias·subject link·grant·GRANTED ledger와 hold를 한 Transaction에서 처리한다. 사전 Claim은 reserve 지연·쓰기와 늦은 candidate 경합을 줄이지만 모든 verified 사용자에 미사용 Claim 데이터를 만들고 eligibility consumer에 issuance 책임을 결합한다.
- 기산점 영향: 현 계약은 최초 reserve의 claimedAt부터 3년이다. verified 시 ACTIVE Claim을 만들면 인증 시점으로 기산점이 앞당겨지고, claimedAt 없는 예비 Claim 상태를 추가하면 현재 TrialEligibility와 중복되는 상태 머신·index·CAS·purge 계약이 새로 필요하다.
- 확장성 평가: TrialClaim은 FREE_EXAM_ONCE phone dedupe 전용이므로 사전 생성만으로 일반 campaign·coupon·paid promotion 확장성이 생기지 않는다. 공통 확장은 catalog/offer와 EntitlementGrant·allocation·ledger resolver에서 해야 한다. Claim만 선생성하고 grant를 lazy 생성하는 절충은 양쪽 복잡도를 가지면서 reserve 단순화 효과가 제한적이다.
- 테스트 결과: 설명·기록만 변경해 Gradle 테스트는 실행하지 않았다. TrialClaim·TrialEligibility entity, ADR-001 collection 계약과 승인된 `첫 reserve에서 Claim/grant 생성`·3년 기산점 계약을 읽기 전용으로 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: 이번 비교에서는 eligibility event만으로 지급하지 않고 첫 reserve에서 TrialClaim/grant를 만드는 현행 계약, claimedAt+3년, phone candidate dedupe, raw phone 비저장과 transaction/unique index 원칙을 변경하지 않았다.
- 결정사항: 현재 MVP에는 lazy TrialClaim 유지가 권장된다. 사전 발급이 제품 요구가 되면 TrialClaim과 grant를 함께 발급할지, claimedAt을 verifiedAt으로 볼지, 미사용·revoke·재가입 정책을 먼저 계약으로 재승인해야 한다.
- 위험 요소: Claim만 미리 만들면 grant issuance와 Claim 상태가 분리되어 부분 완료 복구가 늘어난다. verification 시 3년을 시작하면 사용하지 않은 사용자도 만료될 수 있고, claimedAt을 reserve까지 비워두면 dedupe·retention semantics가 불명확해진다.
- 다음 작업: 멘토 의도가 reserve latency 감소인지 verified 즉시 권리 귀속·표시인지 확인한 뒤 변경을 원할 경우 선택지를 포함한 별도 계약안을 작성한다. 변경하지 않으면 기존 순서대로 AttemptGroup event consumer 계획을 진행한다.

## 2026-08-28 — 사전 정의 혜택과 사용자 보유 연결 모델 정리

<!-- codex-turn:benefit-definition-vs-user-claim-grant-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 프로모션이 많아질 때 혜택 정보를 매번 저장하지 않고 사전 생성 record에 연결하면 확장하기 쉽다는 사용자 관점을 TrialClaim·Grant·catalog 책임으로 구분해 검증한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약 결정서·ADR·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 분석 내용: 공통 benefit metadata를 `BenefitDefinition`에 한 번 저장하고 user/phone별 record가 stable benefitCode로 연결하는 정규화 방향은 타당하다. 그러나 TrialClaim은 phone candidate dedupe·claimedAt+3년 retention의 사용자별 record라 shared definition으로 사용할 수 없으며, 사용자 보유량과 source/expiry를 나타내는 연결 document는 여전히 필요하다.
- 역할 구분: catalog는 이름·unit type·소비 정책·policyVersion, TrialClaim은 FREE_EXAM_ONCE anti-abuse, EntitlementGrant는 subject별 지급 source·quantity·expiry, ReservationAllocation과 ledger는 hold/consume/release를 담당한다. “연결만 저장”할 때 그 연결이 곧 grant/ownership record다.
- 확장성 평가: 현재 grantType/sourceType/sourceId와 unit projection은 연결 모델의 기반이지만 benefit definition이 없고 FREE_EXAM_ONCE가 하드코딩돼 있다. 일회성 pass는 entitlement token으로 단순화할 수 있으나 대량 paid credits를 unit별 document로 만들면 비효율적이므로 one-off token과 fungible batch quantity를 병행하는 hybrid가 적절하다.
- 테스트 결과: 설명·기록만 변경해 Gradle 테스트는 실행하지 않았다. 현재 TrialClaim/Grant field와 hard-coded benefit repository 조건, 승인된 paid/promotion 후속 범위를 기존 확인 결과와 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: 현재 무료 MVP의 TrialClaim phone dedupe, 최초 reserve lazy issue, claimedAt+3년, 1-unit grant, allocation·append-only ledger와 paid/promotion 미구현 범위를 변경하지 않았다.
- 결정사항: 사용자의 확장성 목표에는 동의하되, 해결책은 TrialClaim을 catalog로 확장하는 것이 아니라 shared BenefitDefinition과 per-subject Grant/Claim 연결을 분리하는 모델을 권장한다. catalog 도입과 Claim eager/lazy 시점은 독립 결정으로 남겼다.
- 위험 요소: Claim 하나를 공유하거나 이름을 식별자로 사용하면 사용자별 retention·source와 phone unique invariant가 깨진다. 반대로 사용자 보유 연결을 없애면 누가 어떤 campaign 권리를 몇 개·언제까지 보유하는지 판단하거나 환불·만료·중복 지급을 감사할 수 없다.
- 다음 작업: 사용자가 이 모델을 채택하려면 후속 설계에서 BenefitDefinition 식별자·unit type·policy version과 one-off token/quantity grant 경계를 확정한다. 당장 무료 MVP는 AttemptGroup event consumer를 우선한다.

## 2026-08-28 — 현재 Benefit/Claim/Grant 구조와 구독제 방향 확인

<!-- codex-turn:current-benefit-claim-grant-and-subscription-direction -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 현재 코드가 BenefitDefinition·TrialClaim·EntitlementGrant 구조인지 확인하고, credit 대신 단순 구독제로 갈 경우의 적절한 도메인 분리를 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약 결정서·ADR·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 현재 구조: BenefitDefinition/catalog는 없고 FREE_EXAM_ONCE가 Claim·alias·Grant/repository에 하드코딩돼 있다. verified event는 TrialEligibility만 저장하며 최초 reserve가 phone candidate Claim을 확인해 필요 시 TrialClaim·link·aliases와 1-unit Grant·GRANTED ledger를 lazy 생성한다.
- 역할: TrialClaim은 phone별 무료 1회 dedupe와 3년 retention, EntitlementGrant는 one-time unit의 available/held/consumed projection, ledger와 allocation은 지급·hold·소비·복원을 담당한다. 향후 BenefitDefinition은 이 record들이 stable benefitCode로 참조할 공통 정책이다.
- 구독 방향: credit balance 대신 subscription을 채택하면 유료 권리는 quantity Grant가 아니라 subject별 status·startsAt·endsAt을 가진 SubscriptionEntitlement가 적절하다. 활성 기간에는 시험 unit을 차감하지 않지만 Reservation idempotency, 동시 Session 제한과 entitlement source usage audit는 유지해야 한다.
- 테스트 결과: 구조 설명·기록만 변경해 Gradle 테스트를 실행하지 않았다. 현재 FREE_EXAM_ONCE 하드코딩 위치와 TrialClaim/Grant 생성 흐름을 직전 코드 확인 결과에 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: 현재 무료 MVP의 lazy Claim/Grant, reserve hold, Session commit 뒤 confirm 소비, same-consumption replacement와 결제/구독 미구현 범위를 변경하지 않았다.
- 결정사항: 사용자는 장기 유료 모델로 credit보다 단순 구독제를 선호한다고 밝혔다. 이는 방향 기록이며 Store plan·renewal·cancel·expiry·grace와 무료/구독 resolver 우선순위가 아직 승인된 구현 계약은 아니다.
- 위험 요소: 구독을 기존 수량 Grant에 억지로 넣으면 available/held/consumed 의미가 어색해지고, 반대로 구독 중 Reservation을 생략하면 중복 Session·same-key retry·AttemptGroup 연결을 잃는다. BenefitDefinition 없이 새 plan을 계속 하드코딩하면 배포 없이 상품 정책을 변경하기 어렵다.
- 다음 작업: 무료 MVP는 AttemptGroup event consumer를 우선한다. 구독 결제 착수 시 BenefitDefinition/SubscriptionPlan, SubscriptionEntitlement와 무료 TrialClaim/Grant resolver 경계를 별도 계약으로 확정한다.

## 2026-08-28 — BenefitDefinition·Grant·TrialClaim·Ledger 역할 설명

<!-- codex-turn:benefit-grant-trial-claim-ledger-roles-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: BenefitDefinition은 응시권 종류, EntitlementGrant는 보유 응시권, TrialClaim은 이력이라는 사용자 이해를 정확한 도메인 책임으로 보정한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 역할 정리: BenefitDefinition은 혜택 종류·소비 정책 catalog, EntitlementGrant는 subject별 실제 발급 권리와 unit projection, TrialClaim은 verified-phone candidate의 FREE_EXAM_ONCE 3년 중복 발급 방지 근거다. 실제 지급·hold·release·consume 이력은 append-only EntitlementLedger가 담당한다.
- 연결 구조: TrialClaim은 무료 Grant의 source이고 BenefitDefinition은 Grant가 가리킬 종류다. ReservationAllocation이 시험 Reservation과 사용 Grant를 연결하며 Reservation·AttemptGroup은 Session 생성 및 same-consumption 재응시 lifecycle을 담당한다.
- 테스트 결과: 개념 설명·기록만 변경해 Gradle 테스트를 실행하지 않았다. 현재 Claim·Grant·ledger·allocation 책임과 기존 계약을 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: phone당 무료 1회, claimedAt+3년, 1-unit grant, append-only ledger, reserve hold·confirm consume와 same-consumption replacement를 변경하지 않았다.
- 결정사항: 사용자의 구조 이해는 대체로 맞고 TrialClaim을 일반 이력이 아닌 무료 발급 dedupe record로, ledger를 실제 이력으로 구분했다. BenefitDefinition 도입은 아직 후속 설계다.
- 위험 요소: TrialClaim을 소비 이력으로 사용하면 cancel·replacement·다중 ledger event를 표현하지 못하고 Claim 삭제/변경 유혹으로 3년 dedupe가 깨질 수 있다. Grant만 보고 감사하면 mutable projection과 실제 event history가 불일치할 때 복구 근거가 없다.
- 다음 작업: 현재 무료 MVP에서는 기존 책임을 유지하고, 구독 설계 시 BenefitDefinition/SubscriptionPlan과 SubscriptionEntitlement 경계를 확정한다.

## 2026-08-28 — TrialEligibility 역할 설명

<!-- codex-turn:trial-eligibility-role-explained -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: TrialEligibility가 전화번호 인증 여부를 저장하는 record인지 설명하고 Claim·Grant와 경계를 구분한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·계약·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 설명 내용: TrialEligibility는 Identity verified/revoked event의 user별 current projection이며 consumer scope, binding revision, VERIFIED/REVOKED, opaque candidate와 event high-water를 저장한다. raw phone은 저장하지 않는다.
- 동작: reserve는 current VERIFIED와 candidate 존재를 확인해야 Claim/Grant를 생성·연결한다. revoke는 candidate를 제거하고 revision tombstone을 유지하지만 기존 TrialClaim·Grant·consumption을 삭제하거나 복원하지 않는다.
- 테스트 결과: 개념 설명과 기록만 변경해 Gradle 테스트를 실행하지 않았다. 직전 확인한 TrialEligibility entity와 승인된 event/reserve 계약을 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: event 수신 자체는 지급이 아니며 raw phone 비저장, revision high-water, fail-closed reserve, revoke 시 Claim 불변을 유지한다.
- 결정사항: 새 결정 없이 TrialEligibility를 “현재 전화 인증 기반 무료권 자격 projection”으로 명확히 했다.
- 위험 요소: Eligibility를 entitlement로 오해하면 verified event만으로 무료권을 지급하거나 revoke 때 사용 이력을 삭제할 수 있다. 반대로 revision tombstone을 제거하면 늦은 verified event가 REVOKED 상태를 되돌릴 수 있다.
- 다음 작업: 기존 순서대로 AttemptGroup event consumer 계획을 진행하며 구독 설계에서도 identity eligibility와 paid subscription entitlement를 분리한다.

## 2026-08-28 — 장기 Benefit·무료 Grant·구독 구조 승인 반영

<!-- codex-turn:benefit-free-grant-subscription-architecture-approved -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 사용자가 승인한 BenefitDefinition, TrialClaim, EntitlementGrant, SubscriptionEntitlement, Reservation, AttemptGroup 장기 구조를 단일 계약 기준에 반영하고 즉시 필요한 코드 변경을 판정한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 승인 구조: BenefitDefinition은 공통 종류·정책, TrialClaim은 phone 무료 1회 dedupe, EntitlementGrant는 one-time 보유 권리, SubscriptionEntitlement는 기간형 유료 권리, Reservation은 공통 시험 authorization/idempotency, AttemptGroup은 사용 건·replacement 연결, ledger는 실제 변경 이력으로 확정했다.
- 현재 gap: TrialClaim·Grant·Reservation·AttemptGroup·ledger/allocation은 구현돼 있으나 BenefitDefinition은 없고 FREE_EXAM_ONCE가 문자열로 하드코딩돼 있다. SubscriptionEntitlement는 구독 제품 계약 전 범위 밖이다.
- 즉시 영향: 구조 방향만 적용하는 데 런타임 변경은 필요 없다. 실제 catalog foundation 구현 시 definition collection/seed, stable benefitCode와 unique index/startup validation, Claim/Grant reference 정리와 schema/contract test가 필요하다. 구독 때는 Reservation attempt kind와 별개 authorization source type/reference 및 subscription active-period resolver를 추가해야 한다.
- 테스트 결과: 계약·설명 문서만 변경해 Gradle 테스트를 실행하지 않았다. 현재 FREE_EXAM_ONCE 하드코딩, Reservation.Kind와 allocation 구조, 기존 무료 ADR/결제 deferred 범위를 읽기 전용으로 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: 현재 무료 MVP의 lazy Claim/Grant, claimedAt+3년, reserve hold·confirm consume, same-consumption replacement, internal DTO·Mongo schema와 결제/구독 미구현 gate는 변경하지 않았다.
- 결정사항: 승인 구조를 CONTRACT_DECISIONS 1C에 확정했다. PREMIUM_SUBSCRIPTION 식별자는 후속용으로 예약하지만 Store plan·가격·renewal·cancel·expiry·grace는 미확정이다. 과거 credit/pass 초안은 실제 폐기 승인 전까지 역사적 동결로 남긴다.
- 위험 요소: BenefitDefinition 없이 구독까지 추가하면 hard-coded benefit 분기가 퍼지고, 반대로 지금 빈 SubscriptionEntitlement와 미확정 Store 필드를 만들면 speculative schema가 된다. Reservation.Kind를 entitlement source로 재사용하면 INITIAL/REPLACEMENT와 FREE/SUBSCRIPTION 두 축이 섞인다.
- 다음 작업: 사용자가 foundation 선행을 승인하면 BenefitDefinition vertical slice 계획서를 작성하고 별도 승인으로 Jira를 생성한다. 그렇지 않으면 AttemptGroup event consumer를 먼저 진행하고 구독 착수 전에 foundation을 구현한다.

## 2026-08-28 — BenefitDefinition 선행·구독 후속 순서 확정

<!-- codex-turn:benefit-definition-first-subscription-deferred -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 현재 업데이트에는 BenefitDefinition foundation을 먼저 구현하고 구독은 다음 업데이트로 연기한 뒤 기존 무료 MVP 작업 순서로 복귀한다는 사용자 결정을 반영한다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·PLAN·Jira와 타 저장소는 변경하지 않았다.
- 확정 범위: FREE_EXAM_ONCE BenefitDefinition catalog, stable benefitCode, seed/fail-fast validation, unique index와 Claim·alias·Grant reference/hardcode 정리를 별도 vertical slice로 선행한다. SubscriptionEntitlement, PREMIUM_SUBSCRIPTION 실제 definition, Store·renewal·cancel·expiry·grace와 구독 authorization은 다음 업데이트다.
- 유지 동작: TrialEligibility event는 자격 projection만 저장하고 최초 reserve가 TrialClaim·1-unit Grant를 lazy 생성해 hold한다. Session durable commit 뒤 confirm 소비, cancel/expiry release와 same-consumption replacement를 변경하지 않는다.
- 테스트 결과: 작업 순서·계약 문서만 변경해 Gradle 테스트를 실행하지 않았다. 현재 catalog gap과 Mongo/index 영향, 기존 후속 순서를 대조하고 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: 현재 internal API DTO·Mongo collection, claimedAt+3년, TrialClaim phone dedupe, one-unit grant·append-only ledger, Reservation lifecycle와 구독/결제 production gate를 유지한다.
- 결정사항: BenefitDefinition foundation을 다음 구현 단위로 선행한 뒤 AttemptGroup event consumer → owner rebind → Learning Core saga/Lattice E2E 순서로 진행한다. PLAN 번호와 Jira는 아직 만들지 않았다.
- 위험 요소: foundation과 구독 entity를 한 번에 만들면 미확정 Store 상태 머신이 schema에 고정된다. 반대로 BenefitDefinition에서 existing benefitType/grantType field migration 방식을 계획 없이 바꾸면 ADR·index와 테스트가 불일치할 수 있다.
- 다음 작업: BenefitDefinition foundation 계획서를 작성해 field/reference·seed·index·schema migration과 테스트 범위를 확정하고, 사용자 승인 후 Jira를 생성한 뒤 구현한다.

## 2026-08-28 — PLAN-004 BenefitDefinition foundation 계획서 작성

<!-- codex-turn:plan-004-benefit-definition-foundation-drafted -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113` 참고
- 작업 목표: 구독을 제외하고 FREE_EXAM_ONCE BenefitDefinition foundation만 구현할 수 있는 vertical slice 계획을 작성한다.
- 변경 파일: `docs/plans/PLAN-004-benefit-definition-foundation.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·AGENTS·Jira와 타 저장소는 변경하지 않았다.
- 계획 내용: benefit_definitions catalog와 exact seed/drift validation, `_id=benefitCode`, Claim·alias·Grant의 benefitCode reference, 기존 benefitType/grantType 정리, schema v3와 alias/grant index 보정, BenefitCatalog 기반 lazy reserve 발급과 replica-set 회귀 테스트를 포함했다.
- 유지 동작: Identity verified event는 TrialEligibility만 반영하고 최초 reserve가 Claim·1-unit Grant를 생성해 hold한다. confirm/cancel/expiry, ledger, wire DTO, claimedAt+3년과 same-consumption replacement는 변경하지 않는다.
- 제외 범위: PREMIUM_SUBSCRIPTION, SubscriptionEntitlement, Store·renewal·cancel·expiry·grace, 구독 authorization, eager TrialClaim, public 상품 API, AttemptGroup event·owner rebind·타 서비스/AWS 변경을 분리했다.
- 테스트 결과: 계획 문서만 작성해 Gradle 테스트를 실행하지 않았다. 기존 PLAN 형식, current entity/index/schema v2와 승인된 CONTRACT_DECISIONS 1C를 대조했고 종료 전 code fence·`git diff --check`를 검증한다.
- 결정사항: PLAN 번호는 004, 상태는 사용자 승인 대기, Jira는 미생성이다. benefitCode는 `_id`로 유일성을 보장하고 redundant secondary unique index를 만들지 않는다. 기존 storage field는 미배포 schema v3에서 benefitCode로 통일한다.
- 위험 요소: v2 데이터를 보존해야 하는 환경에서 자동 field/index 변경을 하면 데이터 손상 위험이 있으므로 startup migration을 금지하고 별도 migration 또는 비운영 DB 재생성을 요구한다. catalog drift는 자동 update하지 않고 fail-fast한다.
- 다음 작업: 사용자가 PLAN-004를 승인하면 별도 승인으로 Jira를 생성하고, Jira 완료 조건을 읽은 뒤 구현한다. 완료 후 AttemptGroup 상태 event consumer 순서로 복귀한다.

## 2026-08-28 — PLAN-004 Jira 작업 생성

<!-- codex-turn:plan-004-jira-created -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: `TMI-115` — `[Billing] BenefitDefinition foundation 구현` (`해야 할 일`, 담당자 미지정)
- 작업 목표: 사용자가 승인한 PLAN-004 BenefitDefinition foundation 범위와 완료 조건을 Jira 작업으로 고정한다.
- 변경 파일: `docs/plans/PLAN-004-benefit-definition-foundation.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·Mongo schema·ADR·AGENTS와 타 저장소는 변경하지 않았다.
- Jira 내용: benefit_definitions collection, FREE_EXAM_ONCE seed, exact policy drift fail-fast, Claim·alias·Grant의 benefitCode 전환, schema v3/index 보정, BenefitCatalog 기반 최초 reserve lazy 발급과 replica-set Testcontainers 회귀를 포함했다.
- 제외 범위: PREMIUM_SUBSCRIPTION, SubscriptionEntitlement, Store lifecycle, 구독 Reservation 분기, eager TrialClaim, public 상품 API, AttemptGroup event consumer, owner rebind와 Identity·Learning Core·AWS 변경을 명시했다.
- 테스트 결과: Jira와 문서 metadata만 변경해 Gradle 테스트는 실행하지 않았다. 생성 후 Jira의 key·summary·issue type·status·assignee를 다시 조회했고 문서 변경 후 `git diff --check`를 실행한다.
- 유지한 계약: eligibility event는 TrialEligibility만 저장하고 최초 INITIAL reserve가 Claim·1-unit Grant를 lazy 생성·hold하며 Session durable commit 뒤 confirm에서 소비한다. claimedAt+3년, cancel/expiry release, same-consumption replacement와 production caller gate도 유지한다.
- 결정사항: Jira 유형은 `작업`, 상태는 `해야 할 일`, 담당자는 미지정이다. PLAN-004 상태는 사용자 승인·Jira 생성·구현 전으로 갱신했다.
- 위험 요소: v2 보존 데이터가 있는 환경에서 자동 field/index migration을 수행하면 안 되며, definition 누락·inactive·drift 시 부분 지급 없이 fail-closed해야 한다. 구독 기능을 이번 구현에 섞지 않는다.
- 다음 작업: 구현 요청을 받으면 `TMI-115` 완료 조건과 PLAN-004를 읽고 BenefitDefinition foundation을 구현한 뒤 전체 테스트를 수행한다. Jira 상태 변경은 별도 사용자 승인 전까지 하지 않는다.

## 2026-08-28 — PLAN-005 AttemptGroup 상태 event consumer 계획서 작성

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 Jira 없음; 완료된 `TMI-113`과 별도 계획 `TMI-115` 참고
- 작업 목표: Learning Core의 `AttemptGroupStatusChanged` schema v1 event를 Billing inbox와 현재 active Session fencing을 거쳐 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE`로 수렴시키는 다음 vertical slice 계획을 작성한다.
- 변경 파일: `docs/plans/PLAN-005-attempt-group-status-event-consumer.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션 코드·ADR·통합 계약·AGENTS·Jira와 AWS는 변경하지 않았다.
- 계획 내용: 16 KiB strict decode, canonical digest, shared inbox 일반화, duplicate/conflict, group-session-owner 검증, active Session fencing, group/session version CAS와 단일 Mongo Transaction, feature flag 기본 off, workload security, privacy-safe metric과 replica-set 동시성 테스트를 포함했다.
- 상태 정책: 유효 terminal event는 `GRADING` 누락 시 `OPEN`에서도 직접 전진하며 `COMPLETED`와 `RETAKE_AVAILABLE` 확정 뒤에는 역행하지 않는다. stale Session은 inbox `STALE`과 204, missing group/session은 inbox 없이 retryable `503 ATTEMPT_PROJECTION_NOT_READY`, 구조적 target 충돌은 non-retryable `409 EVENT_TARGET_CONFLICT`다.
- failureCode: `REQUIRED_RESULTS_UNAVAILABLE`, `SUMMARY_UNAVAILABLE`, `GRADING_DEADLINE_EXCEEDED`, `RESULT_INTEGRITY_VIOLATION` 네 저 cardinality 값만 초안 allowlist로 고정했다. provider 원문·exception message·job/문항 식별자는 금지한다.
- 유지한 계약: 기존 Reservation·TrialClaim·grant·ledger, same-consumption replacement, Identity eligibility event, 내부 API와 기존 Mongo index를 변경하지 않는다. AttemptGroup event로 소비를 환불하거나 새 Claim/grant를 만들지 않는다.
- 테스트 결과: 계획 문서만 작성해 Gradle 테스트는 실행하지 않았다. ADR-001·ADR-002·서비스 통합 계약과 현재 AttemptGroup/Session/inbox/security/index 코드를 대조했으며 종료 전 `git diff --check`를 실행한다.
- 결정사항: 기존 PLAN-004와 번호 충돌을 피하기 위해 PLAN-005를 사용한다. PLAN-004 BenefitDefinition은 이 consumer의 기술적 선행 조건이 아니며, 사용자의 우선순위 결정에 따라 PLAN-005를 먼저 구현할 수 있다. 상태는 사용자 승인 대기이고 Jira는 미생성이다.
- 위험 요소: sequence 없는 상충 terminal event는 먼저 commit된 terminal이 승리하므로 producer가 서로 모순된 terminal evidence를 발행하지 않는 contract test가 필요하다. shared inbox 일반화가 Identity revision dedupe를 깨뜨리지 않도록 전체 회귀를 gate로 둔다.
- 다음 작업: 사용자가 PLAN-005를 검토·승인하면 별도 승인으로 Jira를 생성한다. 구현 전 Phase 0에서 ADR·통합 계약에 오류·failureCode·순서 역전 정책을 반영하고 이후 Step 1부터 구현한다.

## 2026-08-28 — TMI-115 PLAN-004 BenefitDefinition foundation 구현 완료

<!-- codex-turn:tmi-115-benefit-definition-foundation-implemented -->

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: `TMI-115` — `[Billing] BenefitDefinition foundation 구현` (`해야 할 일`, 담당자 미지정)
- 작업 목표: FREE_EXAM_ONCE 하드코딩을 versioned BenefitDefinition catalog 참조로 전환하고 Claim·alias·Grant와 Mongo v3 계약을 일관되게 적용한다.
- 변경 파일: `AGENTS.md`, `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/plans/PLAN-004-benefit-definition-foundation.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`, `src/main/resources/application.yml`, BenefitDefinition domain/application/repository/config 신규 파일, Claim·alias·Grant entity/repository, ReserveService, Mongo properties/index initializer와 관련 단위·Testcontainers 테스트. Identity·Learning Core·AWS 파일은 변경하지 않았다.
- 구현 내용: `benefit_definitions`와 `_id=benefitCode`, FREE_EXAM_ONCE UNIT/EXAM_ATTEMPT/1-unit/policy-v1/active seed, 재실행 no-op, exact drift startup fail-fast를 추가했다. TrialClaim·TrialCandidateAlias·EntitlementGrant를 `benefitCode`로 통일하고 최초 reserve가 BenefitCatalog의 definition으로 Grant unit을 발급하도록 변경했다.
- 정합성: 기존 Claim 재사용 시 Claim·Grant·Definition code와 totalUnits를 검증한다. definition 누락·inactive·reference mismatch는 command·Claim·Grant·Reservation 부분 write 없이 Transaction rollback과 retryable 503으로 처리하고 privacy-safe invariant metric만 기록한다.
- Mongo: schema version을 2에서 3으로 올리고 `ux_active_trial_candidate` key를 `{benefitCode,keyVersion,candidate}`, `ux_grant_source_type` key를 `{sourceType,sourceId,benefitCode}`로 바꿨다. legacy field document와 이름이 같은 v2 index는 자동 rename/drop/recreate하지 않고 preflight fail-fast한다.
- 테스트 결과: BenefitDefinition code/policy와 catalog 단위 테스트, seed idempotency·policy drift·legacy schema/index/document·missing/inactive rollback·same-code reference·existing Grant mismatch·기존 동시성 및 Reservation lifecycle 회귀를 포함해 `./gradlew clean test` 전체 96개가 성공했다. `git diff --check`와 benefit domain 민감정보 검색도 통과했다.
- 유지한 계약: Identity event는 TrialEligibility만 반영하고 최초 INITIAL reserve에서 Claim·1-unit Grant를 lazy 생성한다. reserve → Session durable commit → confirm, claimedAt+3년, cancel/expiry release, confirmed 소비 불복원, same-consumption replacement, append-only ledger와 production caller gate를 유지했다.
- 결정사항: BenefitDefinition은 공통 policy catalog이고 사용자 권리나 candidate를 저장하지 않는다. displayName은 authorization key로 사용하지 않으며, greenfield production은 v3로 준비한다. 보존할 v2 데이터가 발견되면 별도 migration 승인을 받아야 한다.
- 제외 범위: PREMIUM_SUBSCRIPTION, SubscriptionEntitlement, Store lifecycle, 구독 Reservation 분기, eager TrialClaim, public 상품 API, AttemptGroup event consumer, owner rebind, Identity·Learning Core와 AWS/Lattice 변경.
- 위험 요소: 운영에서 `BILLING_MONGODB_INITIALIZE_INDEXES`를 끄거나 v3 catalog seed 없이 caller를 열면 reserve가 fail-closed한다. production 활성화 전 schema v3 initializer, Learning Core saga와 Lattice staging E2E를 검증해야 한다.
- 다음 작업: 사용자가 검토한 뒤 별도 승인으로 Jira TMI-115를 완료 처리한다. 기능 순서는 이미 작성된 PLAN-005 AttemptGroup 상태 event consumer → owner rebind → Learning Core saga/Lattice staging E2E다.

## 2026-08-28 — PLAN-005 초안 철회와 대상 저장소 정정

- 날짜: 2026-08-28
- 브랜치: `develop`
- Jira: 신규 생성·수정 없음. `TMI-113`, `TMI-115` 상태를 변경하지 않았다.
- 정정 내용: 사용자가 수정 대상은 Billing이 아니라 Learning Core라고 명확히 했다. 범위를 잘못 잡아 작성한 Billing `docs/plans/PLAN-005-attempt-group-status-event-consumer.md` 초안을 삭제하고 활성 계획에서 철회했다.
- 변경 범위: 잘못 생성한 미추적 계획 파일 제거와 Billing CURRENT_STATE/WORKLOG의 정정 기록만 수행했다. Billing 애플리케이션·ADR·통합 계약·AGENTS·Jira·AWS는 변경하지 않았다.
- 다음 작업: Learning Core 저장소에서 Billing 연동의 선행 조건인 필수 `Idempotency-Key`, reserve→Session commit→confirm saga와 same-operation replay 계획을 작성한다.

## 2026-08-31 — PLAN-005 event 처리 outcome 의미 설명

<!-- codex-turn:plan-005-event-outcome-explained -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`
- Jira: 신규 Jira 생성·수정·상태 변경 없음.
- 작업 목표: PLAN-005의 `APPLIED/DUPLICATE/STALE/CONFLICT/PROJECTION_NOT_READY` 분류가 무엇이며 HTTP 응답·재시도와 어떻게 연결되는지 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·PLAN·계약·테스트·Jira·AWS·Learning Core는 변경하지 않았다.
- 설명 내용: APPLIED는 새 전이 반영, DUPLICATE는 동일 eventId·digest의 기처리 재전송, STALE은 이전 Session·terminal 이후 event·동일 상태 no-op, CONFLICT는 eventId 내용 또는 존재하는 target 관계 충돌, PROJECTION_NOT_READY는 정상 생성 순서상 group/session 미가시 상태다.
- 응답 정책: APPLIED/DUPLICATE/STALE은 204로 전송을 종료한다. CONFLICT는 409로 자동 재시도하지 않고 격리·조사하며, PROJECTION_NOT_READY는 inbox 없이 503과 Retry-After 5초로 같은 event를 재시도한다.
- 테스트 결과: 개념 설명과 작업 기록만 변경해 Gradle 테스트를 실행하지 않았다. 종료 전 `git diff --check`를 실행한다.
- 유지한 계약: active Session fencing, eventId/digest 멱등성, 단방향 상태 전이, missing target 비생성, provider·사용자·Session 원문 비노출을 유지한다.
- 결정사항: 새 정책 결정은 없으며 PLAN-005의 승인된 outcome을 업무 상태와 구분해 설명했다.
- 위험 요소: STALE을 실패로 재시도하면 영구 재전송 루프가 생기고, PROJECTION_NOT_READY를 STALE로 저장하면 정상 event가 유실된다. CONFLICT를 503으로 처리하면 잘못된 event가 무한 재시도될 수 있다.
- 다음 작업: PLAN-005 승인 후 별도 사용자 승인으로 Billing 구현 Jira를 생성한다.

## 2026-08-31 — PLAN-005 승인 및 Jira TMI-117 생성

<!-- codex-turn:plan-005-jira-tmi-117-created -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`
- Jira: `TMI-117` — `[Billing] AttemptGroup status event consumer 구현` (`해야 할 일`, 담당자 미지정)
- 작업 목표: 사용자가 승인한 PLAN-005 범위와 완료 조건을 Billing 구현 Jira로 고정한다.
- 변경 파일: `docs/plans/PLAN-005-attempt-group-status-event-consumer.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션·테스트·계약·AWS·Learning Core는 변경하지 않았다.
- Jira 내용: AttemptGroup schema v1 endpoint, strict decode/canonical digest, shared inbox, active Session fencing, 단방향 상태 전이, group/session Transaction·CAS, 승인된 오류/failureCode, trace·duration/event-age 관측성과 replica-set 테스트를 포함했다.
- 완료 조건: APPLIED/DUPLICATE/STALE 204와 400/409/422/503 exact 계약, COMPLETED evidence와 RETAKE allowlist, duplicate/concurrent/transient/unknown commit 수렴, Claim·Grant·consumption 불변, workload security와 개인정보 비노출, 전체 Gradle 테스트 성공을 명시했다.
- 제외 범위: Learning Core outbox/publisher, 실제 Lattice/IAM/SG, staging E2E, owner rebind, paid/subscription/coupon, repair와 trace backend 인프라를 분리했다.
- 검증 결과: Jira 생성 후 key·summary·issue type·status·assignee·description을 다시 조회했다. `TMI-117`, `작업`, `해야 할 일`, 담당자 미지정과 계획서 범위가 일치한다. 문서 변경 후 `git diff --check`를 실행한다.
- 유지한 계약: COMPLETED 불가역, same-consumption replacement, eventId/digest 멱등성, producer terminal 단일성, consumer-first 배포와 production gate를 유지했다.
- 결정사항: PLAN-005는 승인 완료이며 Jira `TMI-117`을 구현 기준으로 사용한다. Jira 상태는 별도 승인 없이 변경하지 않는다.
- 위험 요소: Billing consumer 구현만으로 Learning Core event 전달과 production 연동은 완료되지 않는다. 후속 outbox/publisher와 Lattice staging E2E가 필요하다.
- 다음 작업: 사용자가 구현을 요청하면 Jira `TMI-117`을 먼저 읽고 PLAN-005 완료 조건에 따라 Billing consumer를 구현한다.

## 2026-08-31 — TMI-117 PLAN-005 AttemptGroup event consumer 구현 완료

<!-- codex-turn:tmi-117-attempt-group-event-consumer-implemented -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`
- Jira: `TMI-117` — `[Billing] AttemptGroup status event consumer 구현` (`해야 할 일`, 담당자 미지정; 상태 변경 없음)
- 작업 목표: Learning Core의 schema v1 AttemptGroup status event를 strict decode하고 active Session fencing을 거쳐 Billing AttemptGroup/AttemptSession projection에 멱등·원자적으로 반영한다.
- 변경 파일: `build.gradle`, `src/main/resources/application.yml`, attempt domain의 `api/application/config/domain/exception/repository` 신규·보강 파일, `BillingSubjectLinkRepository`, `TrialEligibilityEventService`, `SecurityConfig`, `InternalApiExceptionHandler`, `global/observability`, AttemptGroup/Session entity·repository, 관련 단위·MVC·security·Testcontainers 테스트, `docs/plans/PLAN-005-attempt-group-status-event-consumer.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. Identity·Learning Core·AWS 파일은 변경하지 않았다.
- wire 구현: `POST /internal/v1/attempt-group-events`, 16 KiB bounded body, duplicate/trailing/unknown/coercion 거절, canonical UUID·opaque session·UTC 최대 millisecond precision, target별 exact field/evidence/failureCode와 configurable 30초 future skew를 구현했다. canonical JSON SHA-256은 UTC millisecond text로 정규화한다.
- inbox 구현: 기존 `inbound_event_inbox`를 raw minimal view로 재사용해 `_class`, payload, evidence, failureCode와 user/group/session ID를 저장하지 않는다. global eventId와 120일 TTL을 사용하고 Identity revision partial index와 schema v3를 유지했다. Identity consumer도 producer+digest를 함께 비교해 cross-producer same eventId를 conflict 처리한다.
- 상태 구현: group/session/subject 관계, AttemptSession ACTIVE와 group activeSessionId를 fencing한다. OPEN/GRADING의 허용 전이만 CAS하고 COMPLETED는 불가역이며 RETAKE_AVAILABLE은 Session을 FAILED로 닫고 activeSessionId를 해제한다. Claim·Grant·allocation·ledger는 변경하지 않는다.
- 장애 수렴: inbox insert와 group/session CAS를 단일 Mongo Transaction으로 처리하고 same-event duplicate key, concurrent terminal CAS loser, transient transaction과 unknown commit 결과를 기존 inbox 재조회 및 동일 event 재처리로 DUPLICATE/STALE/CONFLICT에 수렴시킨다.
- API·보안: APPLIED/DUPLICATE/STALE은 body 없는 204, event/target conflict는 409, unsupported는 422, projection missing은 Retry-After 5의 503, Mongo 장애는 retryable 503을 반환한다. feature flag 기본 false, TEST Learning Core role 성공과 Identity/wrong/unsigned/default-disabled 실패를 검증했다.
- 관측성: Micrometer Tracing OpenTelemetry bridge, 명시적 W3C-only ContextPropagators와 baggage disabled 설정을 추가했다. service/operation/outcome/traceId/eventId/durationMs/eventAgeMs 로그, duration/age histogram과 저카디널리티 tag만 사용하며 사용자·Session·AttemptGroup·payload/digest를 로그·metric tag에서 제외했다.
- 테스트 결과: strict decoder/canonical digest/16 KiB 경계, 상태 전이·retention edge, cross-producer conflict, transient/unknown commit, 구조화 로그 privacy, metric cardinality, W3C HTTP trace continuation, security, replica-set Transaction·동시 terminal race와 기존 전체 회귀를 포함해 `./gradlew clean test` 137개가 성공했다. `git diff --check`와 최종 개인정보/Secret scan을 별도로 수행한다.
- 유지한 계약: eligibility event는 지급이 아니며 최초 reserve lazy Claim/Grant, claimedAt+3년, confirmed consumption 불복원, same-consumption replacement, provider 원문 금지와 consumer-first production gate를 유지했다.
- 결정사항: Mongo schema v3와 기존 index는 변경하지 않는다. endpoint는 기본 off이고 W3C trace propagation은 exporter 없이 동작하며 실제 backend/exporter는 운영 후속 범위다. Jira 상태는 사용자 승인 없이 변경하지 않는다.
- 위험 요소: Billing consumer만 구현돼 실제 event는 아직 오지 않는다. Learning Core terminal 단일성·outbox/publisher, Lattice route/IAM/SG, replica-set failure injection과 staging E2E가 완료되기 전 production flag를 켜면 안 된다.
- 다음 작업: 사용자 검토 후 별도 승인으로 Jira `TMI-117`을 완료 처리한다. 이후 Learning Core outbox/publisher PLAN/Jira를 작성하고 consumer-first staging 연동을 진행한다.

## 2026-08-31 — TMI-117 변경 파일 역할 설명을 위한 코드 검토

<!-- codex-turn:tmi-117-file-responsibility-review -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`
- Jira: `TMI-117` — `[Billing] AttemptGroup status event consumer 구현` (`해야 할 일`; 상태 변경 없음)
- 작업 목표: 이번 구현에서 생성·수정된 파일이 담당하는 기능을 실제 코드와 테스트 기준으로 분류해 사용자에게 설명한다.
- 변경 파일: 작업 기록을 위한 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. 애플리케이션, 테스트, 계약, Identity, Learning Core와 AWS 파일은 변경하지 않았다.
- 검토 내용: HTTP endpoint와 16 KiB filter, strict decoder/canonical digest, event model/enums, Transaction/CAS service와 repository, inbox 최소 저장, stable error, feature flag/security, W3C trace와 metric, 단위·MVC·Testcontainers 테스트의 책임을 확인했다.
- 테스트 결과: 설명만을 위한 read-only 코드 검토이므로 전체 테스트는 재실행하지 않았다. 직전 TMI-117 구현 검증의 `./gradlew clean test` 137개 성공 결과를 유지하며 문서 변경 후 `git diff --check`를 수행한다.
- 유지한 계약: COMPLETED 불가역, RETAKE_AVAILABLE의 entitlement 불복원, Learning Core workload route, payload·사용자 식별자 비노출, endpoint 기본 off와 consumer-first production gate를 변경하지 않았다.
- 결정사항: `APPLIED/DUPLICATE/STALE`는 정상 204 outcome이고 `CONFLICT/PROJECTION_NOT_READY`는 각각 409/503 예외 계약인 계층 분리를 사용자 설명에 명시한다.
- 위험 요소: 파일별 책임을 이해해도 실제 event 발행은 아직 연결되지 않는다. Learning Core outbox/publisher와 staging Lattice E2E는 계속 후속 작업이다.
- 다음 작업: 사용자 검토 후 승인을 받으면 Jira `TMI-117`을 완료 처리하고, 이후 Learning Core publisher 계획을 작성한다.

## 2026-08-31 — Jira TMI-117 완료 전환

<!-- codex-turn:tmi-117-jira-completed -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`
- Jira: `TMI-117` — `[Billing] AttemptGroup status event consumer 구현` (`완료`, resolution `완료`, 담당자 미지정)
- 작업 목표: 사용자의 명시적 승인에 따라 구현·검증이 끝난 TMI-117을 Jira 완료 상태로 닫는다.
- 변경 파일: `docs/plans/PLAN-005-attempt-group-status-event-consumer.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 애플리케이션, 테스트, 계약, Identity, Learning Core와 AWS 파일은 변경하지 않았다.
- Jira 동작: 변경 전 `해야 할 일`과 사용 가능한 완료 전환 ID `41`을 조회한 뒤 `완료`로 전환했다. 별도 댓글, 담당자 변경과 본문 수정은 하지 않았다.
- 검증 결과: 전환 후 Jira를 다시 조회해 status `완료`, status category `done`, resolution `완료`, resolution date `2026-08-31T17:30:03.649+0900`을 확인했다. 문서 변경 후 `git diff --check`를 수행한다.
- 유지한 계약: endpoint 기본 off, COMPLETED 불가역, RETAKE_AVAILABLE entitlement 불복원, consumer-first production gate와 Jira 외부 범위는 변경하지 않았다.
- 결정사항: PLAN-005와 CURRENT_STATE의 현재 Jira 상태를 완료로 갱신하고 WORKLOG의 과거 기록은 그대로 보존한다.
- 위험 요소: Jira 완료는 cross-service production 연동 완료를 뜻하지 않는다. Learning Core outbox/publisher와 Lattice staging E2E는 후속 작업이다.
- 다음 작업: Learning Core outbox/publisher의 계약과 구현 계획을 작성하고 별도 Jira 승인 절차를 진행한다.

## 2026-08-31 — Learning Core AttemptGroup trace 연동 전달사항 정리

<!-- codex-turn:learning-core-attempt-group-trace-handoff -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`
- Jira: 후속 Learning Core outbox/publisher Jira 미생성. 완료된 Billing 기준 이슈는 `TMI-117`이다.
- 작업 목표: Learning Core에 전달할 AttemptGroup outbox publisher와 Billing consumer의 trace·구조화 로그 규격을 명확히 정리한다.
- 변경 파일: `docs/contracts/LEARNING_CORE_ATTEMPT_GROUP_TRACE_HANDOFF.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. Billing 애플리케이션·테스트, Identity, Learning Core와 AWS 파일은 변경하지 않았다.
- 정리 내용: W3C traceparent/tracestate, baggage 금지, outbox trace metadata와 publisher span 생성/inject 순서, retry span, 공통 service/operation/outcome/traceId/eventId/durationMs, Billing eventAgeMs, privacy와 metric cardinality 및 필수 테스트를 정의했다.
- 검증 결과: Billing 실제 `AttemptGroupEventService`, metrics, `TraceCorrelation`, `TracingConfig`와 ADR-001·통합 계약·C8-1을 대조했다. 문서 변경 후 `git diff --check`를 수행한다.
- 유지한 계약: trace context는 event JSON/digest/idempotency/domain key가 아니며 사용자·Session·AttemptGroup·candidate·payload·credential을 log/trace에 기록하지 않는다. missing/invalid trace로 event 처리를 실패시키지 않는다.
- 결정사항: 같은 distributed trace는 동일 traceId와 단계별 서로 다른 spanId로 표현한다. publisher는 저장된 traceparent를 그대로 replay하지 않고 새 publish span context를 inject한다.
- 위험 요소: Learning Core publisher outcome allowlist와 실제 framework adapter는 후속 구현 PLAN에서 확정해야 한다. exporter/backend와 dashboard는 별도 운영 범위다.
- 다음 작업: Learning Core 저장소에서 현행 tracing 의존성과 outbox schema를 확인한 뒤 publisher PLAN/Jira를 작성한다.

## 2026-08-31 — 세 앱 서버 문서 계층·완료 보고 규칙 통일

- 날짜: 2026-08-31
- 브랜치: `develop`
- Jira: 별도 Jira 이슈 키가 없으며 Jira를 조회하거나 변경하지 않았다.
- 작업 목표: Billing을 포함한 세 앱 서버의 계획·조사 문서와 구현 완료 보고 형식을 읽기 쉬운 공통 계층으로 통일한다.
- 변경 파일: `AGENTS.md`, `docs/codex/WORKLOG.md`, `docs/codex/CURRENT_STATE.md`.
- 변경 내용: 5줄 결론부터 상세 부록까지의 6단계 문서 구조와 파일 근거·구현 사실/계획/추론 구분을 추가했다. 구현 완료 보고에는 변경·계약·테스트·위험·배포 전 확인·예상 밖 diff·다음 확인을 포함한다.
- 유지한 계약: Billing internal API, eligibility, Reservation, AttemptGroup, 원장과 workload 인증 계약을 변경하지 않았다.
- 테스트·검증: 규칙·기록 문서만 변경해 Gradle 테스트는 실행하지 않고 `git diff --check`로 검증한다.
- 위험·다음 작업: 새 규칙이 이후 계획과 구현 보고에 실제 적용되는지 확인한다. 애플리케이션 배포 전 확인 사항은 없다.
- 예상 밖 diff: 이번 작업과 무관한 기존 `PLAN-005`와 Learning Core trace handoff 문서 변경이 작업 트리에 있으며 수정하지 않았다.
- Git commit·push를 수행하지 않았고 Secret, Token, 결제 원문이나 개인정보를 기록하지 않았다.

## 2026-08-31 — Billing AttemptGroup production 업무 span 보완

<!-- codex-turn:billing-attempt-group-inner-span -->

- 날짜: 2026-08-31
- 브랜치: Billing `develop`
- Jira: 완료된 `TMI-117` 관련 후속 보완이며 Jira 상태·댓글·담당자는 변경하지 않았다.
- 작업 목표: 실제 Billing HTTP 요청에서 server span 아래 `attempt_group_event_consume` INTERNAL 업무 span을 생성하고 strict decode부터 service·Mongo 처리까지 추적한다.
- 변경 파일: `src/main/java/web/tosunsaeng/billing/domain/attempt/application/AttemptGroupEventTracing.java`, `AttemptGroupEventController.java`, `AttemptGroupEventControllerTest.java`, `AttemptGroupTracePropagationIntegrationTest.java`, `SecurityConfigTest.java`, `docs/plans/PLAN-005-attempt-group-status-event-consumer.md`, `docs/contracts/LEARNING_CORE_ATTEMPT_GROUP_TRACE_HANDOFF.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`. 기존 사용자 변경인 `AGENTS.md`는 수정하지 않았다.
- 구현 내용: 현재 server/security span을 parent로 새 Micrometer span을 만들고 이름을 `attempt_group_event_consume`으로 고정했다. kind 미지정 시 OpenTelemetry INTERNAL이 되며 try-with-resources와 finally로 정상·RuntimeException·Error 경로를 모두 종료하고 예외는 error로 기록한다. span attribute는 추가하지 않았다.
- 테스트 내용: 테스트 내부 수동 consume span만 확인하던 방식을 보강해 embedded Tomcat에 실제 HTTP 요청을 보낸다. inbound traceId, SERVER ancestor, 서로 다른 HTTP/consume spanId, 정확한 이름·INTERNAL kind, decode/service 동일 scope, baggage 미전파, 정상·예외 종료와 금지 attribute 부재를 capturing SpanProcessor로 검증한다.
- 테스트 결과: 집중 Controller/trace 테스트가 성공했고 `./gradlew clean test` 전체 138개가 성공했다. `git diff --check`와 privacy pattern 검사를 추가 수행한다.
- 유지한 계약: `POST /internal/v1/attempt-group-events`, event JSON·digest, 204/400/409/422/503, W3C inbound, baggage disabled, 구조화 로그와 기존 metric 이름·tag를 변경하지 않았다.
- 결정사항: Spring Security가 HTTP SERVER와 업무 span 사이에 INTERNAL 관측 span을 추가할 수 있으므로 직접 부모가 아니라 동일 trace의 descendant 관계를 검증한다. 선택 제안인 `billing.attempt_group.trace_context_missing` rename은 dashboard/alert migration 없는 즉시 변경을 피하기 위해 보류했다.
- 위험 요소: 실제 exporter/backend가 없어 운영 UI에서의 trace 시각화는 후속 인프라가 필요하다. Learning Core outbox metadata·retry sibling span·fallback trace·auth circuit·SigV4 inject/sign 순서는 Learning Core 범위다.
- 다음 작업: Learning Core publisher 구현 후 staging에서 `learning-core publish → Billing HTTP → consume` trace 연결과 금지 attribute 부재를 cross-service E2E로 검증한다.

## 2026-09-01 — Learning Core TMI-118 구현 검토와 Billing 다음 작업 판정

<!-- codex-turn:tmi-118-review-and-billing-next -->

- 날짜: 2026-09-01
- 브랜치: Billing `develop`; Learning Core `develop`은 읽기·테스트 대상으로만 사용했다.
- Jira: Learning Core `TMI-118` `[Learning Core] AttemptGroup durable outbox/publisher 구현`은 status/resolution `완료`; Billing `TMI-117`도 완료다. Jira를 수정하거나 댓글을 추가하지 않았다.
- 작업 목표: TMI-118의 merge·테스트·핵심 구현을 완료 조건과 대조하고 다음 제품 개발이 Billing인지 판정한다.
- 확인 결과: Learning Core commit `63d0f7d`가 PR #25 merge `c00d872`로 local/remote develop에 반영됐다. writer/publisher 기본 off, GRADING/COMPLETED/RETAKE_AVAILABLE, outbox·lease·retry·BLOCKED_AUTH·trace·SigV4와 replacement 연결 코드가 존재한다.
- 실행 테스트: Learning Core 현재 develop에서 `./gradlew clean test`를 실행해 총 439개, failures/errors 0과 `BUILD SUCCESSFUL`을 확인했다. Billing 애플리케이션 테스트는 변경이 없어 재실행하지 않았고 Billing 문서 변경 후 `git diff --check`를 수행한다.
- release blocker: `AttemptGroupSummaryCompletionService`가 Mongo Transaction 내부 Summary insert의 `DuplicateKeyException`을 catch하고 Transaction을 계속 사용한다. Mongo duplicate key는 해당 Transaction을 abort하므로 committed replay에서 이후 Job/Session/outbox 처리까지 안전하게 계속된다는 주석과 다르게 실패할 수 있다.
- 테스트 간극: 신규 AttemptGroup 테스트 6개는 mock 기반이다. replica-set Transaction commit/rollback·unknown commit·terminal race, multi-instance lease reclaim/half-open probe, 실제 SigV4 header mutation 순서, same trace/different span·baggage/privacy를 완료 조건대로 직접 입증하지 않는다.
- 유지한 계약: Billing endpoint/event JSON/status, TrialClaim·Grant·consumption, 공개 Learning API와 AI/S3/Redis 계약은 변경하지 않았다. Identity와 Learning Core 코드는 수정하지 않았다.
- 결정사항: 다음 즉시 작업은 Learning Core TMI-118 blocker 수정과 integration/contract test 보강이다. 그 뒤 다음 제품 vertical slice는 Billing UserMerged retained subject owner rebind로 진행하는 것이 맞다.
- Billing 다음 범위: Identity UserMerged schema, BillingSubjectLink·Claim·Grant·Reservation·AttemptGroup owner 필드와 unique index, active hold/terminal/replacement 충돌 정책, inbox/idempotency와 owner-transfer Transaction을 ADR/PLAN으로 먼저 확정한다. 현재 Billing에는 consumer/transfer 구현과 전용 Jira가 없다.
- 위험·배포 전 확인: TMI-118은 flag off라 코드 merge만으로 production 동작하지 않는다. Billing consumer 배포·flag, Mongo replica-set/index, Lattice/IAM/SG, publisher idle·writer canary와 GRADING/terminal/auth/retry E2E가 필요하다.
- 예상 밖 diff: Learning Core 작업 트리에 기존 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md` 수정이 있었으며 이번 검토에서는 건드리지 않았다. Billing은 이 분석 기록 두 파일만 변경했다.
- 다음 작업: Learning Core blocker 수정 여부를 먼저 결정하고, 완료 후 Billing owner rebind의 미확정 계약 선택지와 구현 계획을 작성한다.

## 2026-09-01 — Learning Core TMI-118 보완 재검토와 Billing 다음 단계 확인

<!-- codex-turn:tmi-118-fix-review-and-owner-rebind-next -->

- 날짜: 2026-09-01
- 브랜치: Billing `develop`; Learning Core `develop`은 읽기·테스트 대상으로만 사용했다.
- Jira: 완료된 Learning Core `TMI-118`의 후속 보완 검토다. Jira 상태·본문·댓글을 조회하거나 변경하지 않았다.
- 작업 목표: 이전에 발견한 aborted Summary Transaction 재사용 문제가 수정됐는지 확인하고 다음 제품 작업이 Billing인지 판정한다.
- 확인 결과: fix commit `4781723`가 PR #26 merge commit `4f9e74c`로 Learning Core local/remote develop에 반영됐다. duplicate insert 예외는 Transaction 밖으로 전파되고 bounded outer loop가 전체 unit을 새 Transaction에서 재시도한다. 재시도에서는 기존 Summary의 id·examId·userId·mockExamId를 검증하며 coordinator의 terminal-slot duplicate도 outer retry까지 전파한다.
- 테스트 결과: Learning Core `./gradlew clean test`를 새로 실행해 총 444개, skipped/failures/errors 0과 `BUILD SUCCESSFUL in 11s`를 확인했다. 신규 단위 테스트는 duplicate-key rollback 후 whole-unit retry, unknown commit result, deterministic identity conflict, terminal-slot duplicate 전파를 검증한다. Billing 애플리케이션 코드는 변경하지 않아 Billing Gradle 테스트는 실행하지 않았다.
- 결정사항: 기존 release blocker는 해소됐다. 실제 replica-set Transaction, multi-instance lease, 실제 SigV4와 Learning Core→Billing trace/privacy는 staging/release gate로 유지하면서 다음 제품 vertical slice인 Billing owner rebind 계약·계획 작업으로 진행할 수 있다.
- 계약 경계: Identity의 현재 `UserMerged` schema v1은 ACTIVE GUEST source를 기존 MEMBER target으로 canonical merge하는 event이며 `eventId`, `schemaVersion`, `sourceUserId`, `targetUserId`, `occurredAt`만 포함한다. 이것은 탈퇴 후 같은 phone으로 새 UUID가 발급되는 재가입 event가 아니므로 Billing에서 두 lifecycle을 같은 의미로 추측해 처리하지 않는다.
- Billing 다음 범위: owner rebind trigger, Identity→Billing 전달/fan-out, source revoked·target verified 조건, `BillingSubjectLink.userId`의 CAS 이전, active Reservation·AttemptGroup event 순서 fencing, inbox/digest/idempotency와 index·Transaction을 ADR과 PLAN에서 먼저 확정한다. Grant·Reservation·AttemptGroup은 `subjectRefId`를 사용하므로 원장·Claim·consumption을 새로 만들지 않고 owner mapping을 이전하는 방향을 유지한다.
- 유지한 계약: phone당 무료 1회, TrialClaim `claimedAt + 3년`, 새 Claim·grant 중복 발급 금지, immutable ledger, active Session/group fencing과 Identity/Learning Core·Billing 도메인 경계를 변경하지 않았다.
- 위험·배포 전 확인: Identity의 현 UserMerged publisher는 Learning Core 전용 endpoint와 단일 delivery 상태를 사용하므로 Billing consumer 추가 시 producer fan-out 계약이 필요할 수 있다. 재가입은 기존 eligibility VERIFIED/REVOKED projection으로 lazy rebind할지 전용 lifecycle event를 추가할지 결정되지 않았다. 실제 cross-service 검증과 Lattice/IAM/SG·feature flag 활성화는 별도 release gate다.
- 예상 밖 diff: Learning Core develop은 테스트 후 clean이며 코드를 수정하지 않았다. Billing은 기존 분석 기록 위에 `CURRENT_STATE.md`, `WORKLOG.md`만 갱신했다.
- 다음 작업: 사용자에게 owner rebind 작업의 목적과 처리 흐름을 설명한 뒤, trigger와 active hold 정책의 선택지를 확정해 ADR·구현 계획서를 작성한다. Jira 생성과 구현은 각각 별도 승인 후 수행한다.

## 2026-09-01 — PLAN-006 retained trial owner rebind 계획서 작성

<!-- codex-turn:plan-006-retained-trial-owner-rebind -->

- 날짜: 2026-09-01
- 브랜치: Billing `develop`
- Jira: 미생성. Jira 생성·수정·댓글·상태 전환을 수행하지 않았다.
- 작업 목표: phone당 무료 1회 정책을 유지하면서 Guest merge와 탈퇴·재가입 뒤 기존 미사용권 또는 same-consumption retake를 새 canonical userId가 이어받도록 Billing owner rebind 구현 계획을 작성한다.
- 변경 파일: `docs/plans/PLAN-006-retained-trial-owner-rebind.md` 신규, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md` 갱신.
- 계획 내용: Identity UserMerged v1과 phone rejoin의 의미를 분리하고 lifecycle별 decoder를 공통 `OwnerRebindCommand`로 수렴시킨다. Claim·Grant·ledger·Reservation·AttemptGroup의 stable subjectRef는 유지하고 `BillingSubjectLink.userId`만 expected-owner/version CAS로 바꾸며, inbox/rebind record와 schema v4 index를 추가하는 방향을 제안했다.
- 권장 선택: phone 재가입 전용 Identity source→target 승인 event, 공통 owner-rebind domain, active RESERVED/PROCESSING 종료까지 최대 5분 retry, pre-rebind exact Session status event의 bounded legacy-source fencing, source 연결의 목적 종료 후 cleanup을 D1~D5 권장안으로 기록했다.
- 구현·테스트 계획: strict decoder/canonical digest, event idempotency/conflict, owner chain fencing, Mongo whole-unit retry, replica-set transaction/concurrency/index test, workload negative security, W3C trace/privacy와 staged rollout/rollback을 포함했다.
- 유지한 계약: TrialClaim `claimedAt + 3년`, phone당 무료 1회, 새 Claim·Grant·consumption 금지, immutable ledger, stable subjectRef, 5분 Reservation, exact Session fencing, SigV4/Lattice workload 경계를 변경하지 않았다.
- 위험·미확인: Identity UserMerged는 Learning Core 전용 단일 delivery이고 Learning Core current code에는 UserMerged consumer가 없다. phone rejoin 전용 wire 이름·route·field, consumer별 fan-out, 425/503 선택과 source 연결 cleanup window는 ADR 승인이 필요하다. 번호 재할당 사용자를 candidate만으로 과거 시험 owner로 추측하지 않는다.
- 테스트 결과: 문서만 변경해 Billing Gradle 테스트는 실행하지 않았다. 종료 전 `git diff --check`, plan marker와 기록 append, 예상 diff 범위를 검증한다.
- 예상 밖 diff: 이번 계획 작업 전부터 Billing `CURRENT_STATE.md`, `WORKLOG.md`에 이전 분석 기록 변경이 있었고 이를 보존했다. 애플리케이션 코드와 Identity/Learning Core 저장소는 수정하지 않았다.
- 다음 작업: 사용자가 PLAN-006 D1~D5 권장안을 검토·승인하면 cross-service owner rebind ADR과 정확한 wire/route/schema/index migration을 확정한다. 이후 별도 승인으로 Jira를 생성하고 Billing 구현을 시작한다.

## 2026-09-02 — PLAN-006 strict decoder와 bounded legacy-source fencing 설명

<!-- codex-turn:plan-006-decoder-fencing-explanation -->

- 날짜: 2026-09-02
- 브랜치: Billing `develop`
- Jira: 미생성. Jira 변경을 수행하지 않았다.
- 작업 목표: PLAN-006의 lifecycle별 strict decoder/OwnerRebindCommand와 late pre-rebind AttemptGroup event용 bounded legacy-source fencing의 역할과 보안 경계를 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 설명 내용: Guest→Member UserMerged와 phone 재가입 event는 각 전용 decoder가 exact schema·producer·reason·UUID·field 집합을 검증한 뒤 공통 command로 정규화한다. 소유자 CAS와 멱등성은 공통 service에서 처리하지만 wire 의미를 하나의 generic payload로 합치지 않는다.
- fencing 의미: rebind 전에 source userId로 이미 생성된 Learning Core outbox event가 owner 변경 후 늦게 도착해 영구 conflict가 되는 것을 막기 위해 exact pre-rebind group/session의 GRADING·terminal 전진만 한시 허용한다. source 신규 reserve·replacement·다른 Session과 사용자 actor 권한은 허용하지 않는다.
- 유지한 계약: stable subjectRef, TrialClaim 3년, 새 권리 미발급, current target owner authorization과 exact Session fencing을 유지한다. source 연결은 terminal 또는 승인된 retry window 종료 후 cleanup한다.
- 테스트 결과: 설명·기록 문서만 변경해 Gradle 테스트는 실행하지 않았다. 종료 전 `git diff --check`를 수행한다.
- 위험·미확인: 구체적인 phone 재가입 wire와 legacy retry window는 아직 ADR 승인 전이다. 구현 시 source event 허용 범위를 status projection 수렴 밖으로 넓히면 안 된다.
- 예상 밖 diff: 애플리케이션 코드를 변경하지 않았고 기존 PLAN-006 및 이전 기록 변경을 보존했다.
- 다음 작업: D1~D5 권장안 승인 후 ADR에서 decoder별 exact contract와 fence key·cleanup window를 수치로 확정한다.

## 2026-09-02 — PLAN-006 승인 반영과 Jira TMI-120 생성

<!-- codex-turn:plan-006-approved-jira-tmi-120 -->

- 날짜: 2026-09-02
- 브랜치: Billing `develop`
- Jira: `TMI-120` `[Billing] Retained trial owner rebind consumer 및 Transaction 구현` 신규 생성.
- 작업 목표: 사용자가 승인한 PLAN-006과 D1~D5 권장안을 확정 계약으로 반영하고 동일 범위의 Billing 구현 Jira를 생성한다.
- 변경 파일: `docs/plans/PLAN-006-retained-trial-owner-rebind.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- Jira 작업: 중복 검색에서 동일 Billing owner rebind 이슈가 없고 Identity 재가입 기반 `TMI-107`만 있음을 확인했다. TMI `작업` 유형으로 `TMI-120`을 생성했으며 기본 priority Medium, 상태 `해야 할 일`, resolution 없음, assignee 없음이다. 생성 후 저장된 summary·description·status를 재조회했다.
- 확정사항: phone 재가입 전용 Identity source→target 승인 event, lifecycle별 decoder + 공통 command/domain, active RESERVED/PROCESSING 종료까지 retry, exact pre-rebind Session의 bounded legacy-source status fencing, 목적 종료 후 source 연결 cleanup을 C14-A로 확정했다.
- Jira 범위: owner CAS와 stable subject 불변식, strict decode/digest/inbox, chain conflict, schema v4/index migration, Mongo whole-unit retry·replica-set concurrency, Identity route security, trace/privacy와 문서 갱신을 포함했다.
- 제외·gate: Identity producer/fan-out과 Learning Core owner consumer, AWS resource와 production activation은 제외했다. 정확한 event wire, 425/503과 cleanup window는 cross-service ADR 뒤 consumer-first로 구현하며 양 서비스와 staging E2E 전에는 flag를 켜지 않는다.
- 테스트 결과: Jira·계약·계획 문서만 변경해 Gradle 테스트는 실행하지 않았다. 종료 전 `git diff --check`, plan status/Jira key/C14와 worklog marker를 검증한다.
- 유지한 계약: phone당 무료 1회, TrialClaim 3년, 새 Claim·Grant·consumption 금지, immutable ledger, stable subjectRef, 5분 Reservation, SigV4/Lattice workload 경계를 유지했다.
- 예상 밖 diff: 애플리케이션 코드를 변경하지 않았고 기존 PLAN-006·작업 기록 변경을 보존했다. Jira 댓글·담당자·상태 전환은 수행하지 않았다.
- 다음 작업: TMI-120 구현 전에 cross-service owner rebind ADR을 작성해 exact event name/route/schema, fan-out, retry status와 legacy fence cleanup window를 확정한다.

## 2026-09-02 — owner rebind ADR의 event·route·pending status 설명

<!-- codex-turn:owner-rebind-adr-wire-options-explanation -->

- 날짜: 2026-09-02
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 변경을 수행하지 않았다.
- 작업 목표: ADR에서 exact event name, route와 425/503 중 무엇을 왜 결정해야 하는지 설명하고 승인 전 권장 초안을 정리한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- event 권장 초안: phone 재가입은 `TrialOwnerRebindApproved`로 두어 Identity가 Billing 권리 owner 이전을 승인했다는 의미를 고정하고 Guest merge `UserMerged` v1과 분리한다. exact payload에는 event/schema/producer/scope/time, source/target, lifecycle reason과 source/target binding revision 필요성을 ADR에서 검토한다.
- route 권장 초안: phone 재가입은 `POST /internal/v1/eligibility/trial/owner/events`, Guest merge는 별도 `POST /internal/v1/owners/merge/events`를 사용한다. 기존 trial eligibility endpoint에 schema를 추가하거나 UserMerged 의미를 재사용하지 않는다.
- pending 응답 권장 초안: no-write pending은 `503 OWNER_REBIND_PENDING`과 bounded `Retry-After`로 통일한다. 425는 TLS early data 의미와 proxy/client 호환성 때문에 비권장하고, 409는 permanent conflict, 202는 durable async acceptance가 아니므로 사용하지 않는다.
- 유지한 계약: C14 D1~D5, strict decoder 분리, active Reservation rewrite 금지, producer retry와 fail-closed를 변경하지 않았다. 이번 설명은 exact wire 승인 자체가 아니다.
- 테스트 결과: 문서 설명만 변경해 Gradle 테스트는 실행하지 않았다. 종료 전 `git diff --check`를 수행한다.
- 위험·미확인: Identity publisher가 `Retry-After`를 bounded parsing하고 503을 재시도하는지 contract test로 고정해야 한다. exact seconds, route IAM action과 event revision field는 ADR에 남아 있다.
- 예상 밖 diff: 애플리케이션과 Jira를 변경하지 않았고 기존 계획·계약 기록을 보존했다.
- 다음 작업: 사용자가 권장값을 승인하면 ADR에 exact event/route/status와 schema/revision/Retry-After 수치를 고정한다.

## 2026-09-02 — owner rebind exact wire 승인 반영과 Jira TMI-120 갱신

<!-- codex-turn:owner-rebind-wire-approved-jira-updated -->

- 날짜: 2026-09-02
- 브랜치: Billing `develop`
- Jira: `TMI-120` description 갱신. 상태·priority·담당자·댓글은 변경하지 않았다.
- 작업 목표: 사용자가 승인한 owner rebind event 이름·route·503 pending 권장안을 exact 계약으로 반영하고 Jira 완료 조건과 일치시킨다.
- 변경 파일: `docs/plans/PLAN-006-retained-trial-owner-rebind.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 확정 wire: phone 재가입은 `TrialOwnerRebindApproved`를 `POST /internal/v1/eligibility/trial/owner/events`로 전달한다. exact schema v1에는 producer/scope/time, source/target, `PHONE_REJOIN`과 1 이상 source/target binding revision이 포함되며 민감 phone/candidate/credential은 제외한다.
- Guest merge: Identity 기존 `UserMerged` v1 payload는 변경하지 않고 Billing route `POST /internal/v1/owners/merge/events`로 분리한다.
- pending 계약: active Reservation/PROCESSING 또는 projection prerequisite는 `503 OWNER_REBIND_PENDING`과 delta-seconds `Retry-After`를 사용한다. Reservation은 남은 expiry를 1~300초로 clamp하고 다른 pending은 5초다. 425/202/409는 temporary pending에 사용하지 않는다.
- Jira 검증: 변경 전 `TMI-120`을 재조회하고 description만 수정했다. 저장된 exact event/route/field/response와 기존 포함·제외·완료 조건이 유지된 것을 tool 응답에서 확인했다.
- 유지한 계약: C14 D1~D5, stable subjectRef, 새 Claim·Grant·consumption 금지, active Reservation rewrite 금지, bounded legacy-source fencing과 production flag off를 유지했다.
- 테스트 결과: Jira·계약·계획 문서만 변경해 Gradle 테스트는 실행하지 않았다. 종료 전 `git diff --check`와 미확정 marker 제거 여부를 검증한다.
- 위험·미확인: Identity consumer별 durable fan-out, exact Lattice IAM action과 legacy fence cleanup window는 후속 ADR에 남아 있다. Identity producer가 503/Retry-After와 revision field를 contract test로 지원해야 한다.
- 예상 밖 diff: 애플리케이션 코드를 변경하지 않았고 기존 승인 문서와 작업 기록 변경을 보존했다.
- 다음 작업: 남은 delivery/IAM/cleanup 값을 ADR로 작성한 뒤 TMI-120 Billing consumer를 reader-first로 구현한다.

## 2026-09-02 — owner rebind durable fan-out·IAM·cleanup 선택지 설명

<!-- codex-turn:owner-rebind-delivery-iam-cleanup-options -->

- 날짜: 2026-09-02
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 변경을 수행하지 않았다.
- 작업 목표: 후속 ADR에 남은 Identity consumer별 durable fan-out, Lattice IAM action과 legacy-source cleanup 기간의 의미·선택지·권장값을 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- fan-out 권장안: immutable event core와 `(eventId, consumer=BILLING|LEARNING_CORE)` unique delivery record 두 건을 Identity lifecycle Transaction에 원자 저장한다. 각 record가 lease·retry·dead-letter·published 상태와 feature flag를 독립 관리해 한 consumer의 성공이 다른 실패를 가리지 않게 한다.
- IAM 권장안: caller task-role identity policy와 Lattice service auth policy 모두 action `vpc-lattice-svcs:Invoke`를 사용한다. 환경별 exact service ARN, exact Identity role Principal, POST와 승인 path를 함께 제한하고 wildcard/cross-environment/불필요 action을 허용하지 않는다.
- cleanup 권장안: exact pre-rebind Session terminal 뒤 daily worker가 24시간 안에 legacy sourceUserId를 unset한다. terminal이 없더라도 `min(appliedAt+120일, Claim retentionExpiresAt)`에 강제 삭제한다. 120일은 Learning Core dead-letter 90일 + 30일 buffer이자 Billing inbox retention과 맞춘다.
- 대안 평가: full outbox를 consumer별 복제하면 단순하지만 payload/state 중복과 drift가 커지고, 동기 순차 POST/global PUBLISHED는 부분 전달 복구가 안 된다. 90일 exact cleanup은 마지막 dead-letter replay 경계가 좁고, Claim 3년 전체 보존은 목적 종료 개인정보를 과다 보존한다.
- 유지한 계약: C14 exact event/route/503, at-least-once, consumer-first, source actor 비허용, TrialClaim 3년 upper bound와 privacy minimum을 변경하지 않았다. 이번 설명은 사용자 승인 전이므로 새 값을 CONTRACT_DECISIONS나 Jira에 확정 반영하지 않았다.
- 테스트 결과: 설명·기록 문서만 변경해 Gradle 테스트는 실행하지 않았다. 종료 전 `git diff --check`를 수행한다.
- 위험·미확인: Identity 저장소의 existing `UserMergedOutbox`를 event core + delivery로 reader-first migration하는 구체 schema와 Learning Core owner endpoint path는 ADR에서 추가로 고정해야 한다. cleanup worker는 terminal 판정과 TTL 단독 삭제가 아니라 명시적 unset·audit count가 필요하다.
- 예상 밖 diff: 애플리케이션·Jira·확정 계약을 변경하지 않았고 기존 계획·기록 변경을 보존했다.
- 다음 작업: 사용자가 세 권장안을 승인하면 C14, PLAN-006와 TMI-120에 exact fan-out/IAM/24시간+120일 cleanup을 반영하고 cross-service ADR을 작성한다.

## 2026-09-02 — owner rebind fan-out·IAM·cleanup 승인 반영과 Jira 갱신

<!-- codex-turn:owner-rebind-delivery-iam-cleanup-approved -->

- 날짜: 2026-09-02
- 브랜치: Billing `develop`
- Jira: `TMI-120` description 갱신. 상태·priority·담당자·댓글은 변경하지 않았다.
- 작업 목표: 승인된 consumer별 durable fan-out, exact Lattice IAM action과 legacy-source cleanup 기간을 확정 계약·Jira·통합 문서에 일관되게 반영한다.
- 변경 파일: `docs/plans/PLAN-006-retained-trial-owner-rebind.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- fan-out 확정: Identity lifecycle Transaction이 immutable event core와 `(eventId, BILLING|LEARNING_CORE)` delivery 두 건을 원자 저장하고 각 delivery가 lease·retry·dead-letter·published·feature flag를 독립 관리한다. global PUBLISHED, 동기 순차 POST와 full payload 복제를 금지한다.
- IAM 확정: action `vpc-lattice-svcs:Invoke`, 환경별 exact service ARN, exact Identity role Principal, POST와 승인 path를 사용한다. wildcard/cross-environment/불필요 `InvokeWithServiceNetworkContext`는 허용하지 않는다. ADR-002 Billing auth-policy 예시에 두 owner route를 추가했다.
- cleanup 확정: related Session terminal 뒤 24시간 SLA로 sourceUserId를 unset하고 terminal 미수렴은 `min(appliedAt+120일, Claim retentionExpiresAt)`에 강제 삭제한다. 120일 뒤 late source event는 privileged reconciliation 대상이며 비식별 멱등성 기록은 user 연결과 분리한다.
- Jira 검증: durable fan-out/IAM/cleanup section과 완료 조건을 추가했고 edit 응답에서 세 항목 포함을 확인했다. Identity fan-out·AWS resource 구현은 Billing code 제외 범위와 production gate로 유지했다.
- 유지한 계약: exact event·route·503, C14 D1~D5, stable subjectRef, 새 Claim·Grant·consumption 금지, privacy minimum과 TrialClaim 3년 upper bound를 유지했다.
- 테스트 결과: Jira·계약·계획 문서만 변경해 Gradle 테스트는 실행하지 않았다. 종료 전 `git diff --check`, auth-policy JSON block과 exact marker를 검증한다.
- 위험·미확인: Identity existing UserMergedOutbox migration schema, Learning Core owner endpoint path와 privileged reconciliation runbook은 후속 ADR에서 구체화해야 한다.
- 예상 밖 diff: 애플리케이션 코드를 변경하지 않았고 기존 승인 문서·기록 변경을 보존했다.
- 다음 작업: cross-service ADR을 작성해 reader-first migration·Learning Core route·reconciliation runbook을 완성한 뒤 TMI-120 Billing 구현을 시작한다.

## 2026-09-02 — TMI-120 구현 시작 가능 여부와 ADR gate 확인

<!-- codex-turn:tmi-120-implementation-readiness-adr-gate -->

- 날짜: 2026-09-02
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 변경을 수행하지 않았다.
- 작업 목표: 승인된 owner rebind 계약만으로 구현을 시작할 수 있는지 저장소의 별도 ADR 요구사항과 대조한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 확인 결과: event/route/schema, 503/Retry-After, fan-out, IAM과 24시간/120일 cleanup 정책은 확정됐다. 다만 통합 계약 10절은 구체적인 UserMerged consumer wire를 별도 ADR 전 임의 추가하지 못하게 하고 현재 ADR은 001·002뿐이므로 ADR-003 작성·승인이 구현 선행 gate다.
- ADR-003 남은 내용: Billing collection/index/schema v4와 legacy backfill, owner CAS/chain state, cleanup worker, privileged reconciliation, Identity existing outbox reader-first delivery migration, Learning Core exact owner endpoint를 승인값 안에서 구체화한다.
- 결정사항: 신규 제품 선택을 다시 묻지 않는다. ADR-003 뒤 Billing reader-first consumer를 구현할 수 있으며 Identity/Learning Core 코드는 별도 작업과 production gate로 유지한다.
- 테스트 결과: 분석·기록 문서만 변경해 Gradle 테스트는 실행하지 않았다. 종료 전 `git diff --check`를 수행한다.
- 유지한 계약: C14, TMI-120 scope, 타 저장소 읽기 전용 원칙, producer/consumer-first와 feature flag off를 유지했다.
- 예상 밖 diff: 애플리케이션·Jira를 변경하지 않았고 기존 문서 변경을 보존했다.
- 다음 작업: 사용자 요청 시 `ADR-003-retained-trial-owner-rebind-contract.md`를 작성하고 검토·승인 뒤 TMI-120을 구현한다.

## 2026-09-02 — ADR-003 작성 전 추가 사용자 결정 필요 여부 확인

<!-- codex-turn:adr-003-user-decisions-readiness -->

- 날짜: 2026-09-02
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 변경을 수행하지 않았다.
- 작업 목표: ADR-003 작성 전에 제품 소유자가 추가로 확정해야 할 정책이 있는지 승인된 C14·PLAN-006·TMI-120 범위와 대조한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 확인 결과: event/route/schema, 503/Retry-After, durable fan-out, exact IAM, stable subject owner CAS, active Reservation pending, bounded legacy fencing과 terminal 24시간/120일 cleanup이 모두 확정돼 필수 사용자 선택은 남지 않았다.
- 기술 기본값: collection/index/schema v4/backfill/CAS, reader-first migration, Learning Core internal route와 scheduler/metric은 기존 규칙에 맞춰 ADR 권장값으로 작성한다. mismatch legacy data/index는 자동 수정하지 않고 startup fail-fast한다.
- repair 기본값: TMI-120에 privileged HTTP repair route를 추가하지 않는다. hard cap 뒤 late event는 자동 처리하지 않고 alert·운영 review 대상으로 남기며 실제 mutation route는 별도 future repair ADR과 운영 role 승인 없이는 만들지 않는다.
- delivery 기본값: Identity lifecycle commit은 downstream 동기 성공을 기다리지 않고 event core + two delivery를 저장한 뒤 각 consumer가 독립 수렴한다. 두 consumer readiness와 staging E2E 전 production flag는 off다.
- 유지한 계약: Billing 범위, 타 저장소 읽기 전용, no automatic migration/drop, privacy minimum과 fail-closed를 유지했다.
- 테스트 결과: 분석·기록 문서만 변경해 Gradle 테스트는 실행하지 않았다. 종료 전 `git diff --check`를 수행한다.
- 예상 밖 diff: 애플리케이션·Jira·확정 계약을 변경하지 않고 기존 문서 변경을 보존했다.
- 다음 작업: 별도 운영 repair endpoint 또는 historical backfill 추가 요구가 없으면 추가 질문 없이 ADR-003 초안을 작성한다.

## 2026-09-02 — ADR-003 retained trial owner rebind 계약 초안 작성

<!-- codex-turn:adr-003-retained-trial-owner-rebind-draft -->

- 날짜: 2026-09-02
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 생성·수정·댓글·상태 전환을 수행하지 않았다.
- 작업 목표: 승인된 owner rebind 제품·wire·fan-out·IAM·cleanup 결정을 Billing과 downstream이 구현할 수 있는 단일 cross-service 기술 계약으로 작성한다.
- 변경 파일: `docs/adr/ADR-003-retained-trial-owner-rebind-contract.md`, `docs/plans/PLAN-006-retained-trial-owner-rebind.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- Billing 계약: phone/Guest lifecycle별 strict decoder와 공통 command, canonical digest/inbox, source→target 상태 전이, phone projection prerequisite, Guest 최대 100 link all-or-nothing, 503 pending과 whole-unit Transaction retry를 구체화했다.
- Mongo 계약: schema v4에 `owner_rebind_inbox`, `subject_owner_rebinds`, `ownerVersion/ownerUpdatedAt`을 추가한다. v3 missing version은 logical 1로 읽고 first CAS에서 2로 수렴하며 legacy document/index를 자동 bulk rewrite 또는 drop/recreate하지 않는다.
- fencing·cleanup: authenticated Learning Core의 exact pre-rebind subject/group/session 전진 event만 source로 한시 허용한다. terminal 또는 120일/Claim 만료 hard cap에 논리 종료하고 최대 1시간 간격 worker가 24시간 안에 source 연결을 unset하며 TTL은 safety net으로만 둔다.
- cross-service 계약: Identity existing UserMerged outbox reader-first core/delivery 전환, consumer별 독립 retry, Learning Core phone route `/internal/v1/owners/trial/rebind/events`, source deny·ownership migration·target terminal 재발행과 hard-cap reconciliation 절차를 정했다.
- 관측성·보안: exact `vpc-lattice-svcs:Invoke`, route/principal/environment 제한, `owner_rebind_consume` span, `service + traceId + eventId + outcome + durationMs` log와 식별자 비로깅을 유지했다.
- 유지한 계약: 새 Claim·Grant·allocation·consumption 금지, stable subjectRef, immutable ledger/command audit, active Reservation rewrite 금지, phone당 무료 1회와 TrialClaim 3년 보존, source actor 신규 권한 금지와 production flag off를 유지했다.
- 테스트 결과: 문서만 변경해 Gradle 애플리케이션 테스트는 실행하지 않았다. `git diff --check`, code-fence 짝수 여부, trailing whitespace와 unresolved placeholder 검사가 모두 통과했다.
- 위험·미확인: 실제 Billing은 아직 schema v3이며 Identity delivery 분리와 Learning Core owner consumer도 미구현이다. ADR 검토 승인, 각 저장소 별도 구현과 staging 순서 역전 E2E 전에는 production 활성화할 수 없다.
- 예상 밖 diff: 애플리케이션·Jira·타 저장소·AWS를 변경하지 않았다. 이전 turn에서 누적된 승인 문서 변경은 보존했다.
- 다음 작업: 사용자가 ADR-003을 검토·승인하면 TMI-120 Billing reader-first schema v4와 owner rebind consumer 구현을 시작한다.

## 2026-09-02 — Guest merge retained subject 100건 상한 설명

<!-- codex-turn:owner-rebind-subject-limit-explanation -->

- 날짜: 2026-09-02
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 변경을 수행하지 않았다.
- 작업 목표: ADR-003의 Guest merge event당 retained subject 100건 상한이 무엇을 세는지 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 설명 내용: subject 한 건은 시험 횟수가 아니라 source user가 current owner로 연결된 active·unexpired `BillingSubjectLink`다. 현재 무료시험 범위에서는 정상 0~1건이며, 100은 미래 benefit 증가나 데이터 이상으로 한 Mongo Transaction이 무제한 커지는 것을 막는 방어 상한이다.
- 초과 처리: 100건을 넘으면 일부만 target으로 옮기지 않고 전체 Transaction을 중단해 409 conflict와 invariant alert로 운영 검토한다. 새 Claim·Grant·시험 기회를 생성하지 않는다.
- 유지한 계약: multi-link all-or-nothing, stable subjectRef, 새 권리 미발급, immutable ledger와 owner mapping만 CAS 이전하는 원칙을 유지했다.
- 테스트 결과: 설명·기록 문서만 변경해 Gradle 테스트는 실행하지 않았다. `git diff --check`를 수행한다.
- 위험·미확인: 100은 현재 제품 수요값이 아니라 기술 방어값이다. 미래에 정상적으로 100개 초과 subject가 필요하면 ADR과 batching/Transaction 정책을 재승인해야 한다.
- 예상 밖 diff: 애플리케이션·Jira·타 저장소를 변경하지 않았고 기존 문서 변경을 보존했다.
- 다음 작업: 사용자가 100건 방어 상한을 승인하거나 원하는 더 작은 상한을 지정한 뒤 ADR-003 전체를 승인한다.

## 2026-09-02 — owner rebind strict decoder 의미 설명

<!-- codex-turn:owner-rebind-strict-decoder-explanation -->

- 날짜: 2026-09-02
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 변경을 수행하지 않았다.
- 작업 목표: ADR-003에서 strict decoder가 담당하는 계약·보안 경계와 일반 JSON parsing의 차이를 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 설명 내용: route별 exact field/type/constant와 UUID·revision·timestamp·source-target 관계를 검증한 뒤에만 내부 `OwnerRebindCommand`를 생성한다. phone rejoin과 Guest merge decoder는 분리하고 검증 뒤 공통 service로 수렴한다.
- 거절 규칙: duplicate/unknown field, trailing token, scalar coercion과 잘못된 casing·값을 거절한다. malformed는 400, 지원하지 않는 정상 계약은 422로 구분한다.
- 개인정보·멱등성: strict validation 전에 raw payload를 저장·로깅하지 않고, 통과한 semantic value만 canonicalize해 digest를 계산한다.
- 유지한 계약: 기존 wire exact 의미, reader-first optional field 배포, source actor fail-closed, raw phone/credential 비저장을 유지했다.
- 테스트 결과: 설명·기록 문서만 변경해 Gradle 테스트는 실행하지 않았다. `git diff --check`를 수행한다.
- 위험·미확인: producer가 새 optional field를 먼저 보내면 old strict consumer는 unknown field로 거절하므로 항상 consumer reader-first 배포가 필요하다.
- 예상 밖 diff: 애플리케이션·Jira·타 저장소를 변경하지 않았고 기존 문서 변경을 보존했다.
- 다음 작업: ADR-003의 남은 기술 기본값을 검토한 뒤 전체 승인 여부를 확정한다.

## 2026-09-02 — ADR-003 승인 반영과 Jira TMI-120 갱신

<!-- codex-turn:adr-003-approved-jira-tmi-120-updated -->

- 날짜: 2026-09-02
- 브랜치: Billing `develop`
- Jira: `TMI-120` description 갱신. 상태·priority·담당자·Resolution·댓글은 변경하지 않았다.
- 작업 목표: 사용자가 승인한 ADR-003 전체와 다섯 기술 기본값을 문서의 확정 기준 및 Jira 구현 완료 조건에 반영한다.
- 변경 파일: `docs/adr/ADR-003-retained-trial-owner-rebind-contract.md`, `docs/plans/PLAN-006-retained-trial-owner-rebind.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 승인값: `owner_rebind_inbox`/`subject_owner_rebinds`, Guest merge 100 subject all-or-nothing 상한, 최대 1시간 cleanup worker, Learning Core `/internal/v1/owners/trial/rebind/events`, historical backfill과 privileged mutation HTTP route 제외다.
- strict decoder: phone/Guest route별 exact JSON 계약을 검증하고 malformed 400·unsupported 422를 구분한 뒤에만 내부 `OwnerRebindCommand`와 canonical digest로 진행하는 기준을 유지했다.
- Jira 검증: 갱신 전 TMI-120의 summary/description/status/priority/assignee/resolution을 재조회했다. 기존 본문을 보존하고 ADR-003 승인 기술 기준을 추가한 뒤 collection, 100건, 1시간, Learning Core route와 제외 범위가 저장된 것을 재확인했다.
- 유지한 계약: 새 Claim·Grant·allocation·consumption 금지, stable subjectRef, active Reservation pending, exact legacy Session fencing, 24시간/120일 cleanup과 production flag off를 유지했다.
- 테스트 결과: 문서·Jira만 변경해 Gradle 테스트는 실행하지 않았다. Jira 저장 결과 검증과 `git diff --check`가 통과했다.
- 위험·미확인: Billing 구현 계약은 확정됐지만 Identity durable fan-out, Learning Core owner consumer, 실제 Lattice/IAM/SG와 staging 순서 역전 E2E는 별도 작업이다.
- 예상 밖 diff: 애플리케이션·타 저장소·AWS를 변경하지 않았고 기존 승인 문서 변경을 보존했다.
- 다음 작업: TMI-120 Billing reader-first schema v4와 owner rebind consumer 구현을 시작한다.

## 2026-09-02 — TMI-120 Billing retained trial owner rebind 구현

<!-- codex-turn:tmi-120-owner-rebind-implementation -->

- 날짜: 2026-09-02
- 브랜치: Billing `feat/TMI-120-trial-owner-rebind-consumer`
- Jira: `TMI-120` 완료 조건을 기준으로 구현했다. 이번 작업에서 Jira 조회·댓글·상태 전환은 수행하지 않았다.
- 작업 목표: ADR-003의 phone 재가입·Guest merge owner event를 strict 수신하고 새 권리 생성 없이 retained `BillingSubjectLink` current owner만 source→target으로 안전하게 이전한다.
- 변경 파일: `AGENTS.md`, `src/main/java/.../domain/ownerrebind/**`, `BillingSubjectLink`, `BillingSubjectLinkRepository`, `ReservationRepository`, `IdempotencyCommandRepository`, `AttemptSession`, `AttemptGroupEventService`, `BillingMongoIndexInitializer`, `BillingMongoProperties`, `SecurityConfig`, `InternalApiExceptionHandler`, `application.yml`, 관련 unit/MVC/trace/Mongo integration test, ADR-003·PLAN-006·통합 계약·CONTRACT_DECISIONS·CURRENT_STATE·WORKLOG.
- wire/API: phone은 `POST /internal/v1/eligibility/trial/owner/events`의 `TrialOwnerRebindApproved` v1, Guest는 `POST /internal/v1/owners/merge/events`의 기존 `UserMerged` 5-field v1을 사용한다. duplicate/unknown/trailing/coercion, UUID/revision/time과 exact schema/event/producer/reason/scope를 lifecycle별 decoder에서 검증하고 canonical JSON SHA-256 digest로 멱등 처리한다.
- Mongo/schema: schema v4에 `owner_rebind_inbox`, `subject_owner_rebinds`와 승인 index를 추가했다. 신규 link는 owner version 1을 기록하고 legacy missing version은 logical 1로 읽어 exact source/current CAS에서 version 2로 수렴한다. invalid ownerVersion/ownerUpdatedAt 조합과 index mismatch는 자동 rewrite/drop 없이 fail-fast한다.
- application 동작: active·unexpired Claim과 subject 관계를 확인하고 phone은 projection revision/state 및 target candidate-to-Claim을 재검증한다. Guest는 최대 100 retained subject를 한 Transaction에서 all-or-nothing 이전한다. 100건 초과는 invariant metric과 409, active Reservation은 남은 시간 1~300초·PROCESSING/prerequisite는 5초 `OWNER_REBIND_PENDING`으로 처리한다.
- 멱등성·원자성: event inbox 확인, prerequisite, Reservation/command guard, pre-rebind fence, owner CAS와 disposition 저장을 하나의 Mongo Transaction으로 처리한다. exact replay는 duplicate, digest mismatch는 event conflict, permanent business conflict는 비식별 inbox로 보존하고 transient/unknown commit은 bounded whole-unit retry와 commit 재확인으로 수렴한다.
- legacy fence: rebind 전 active exact AttemptGroup/Session에만 source·group·session fence를 만들고 `min(appliedAt+120일, Claim.retentionExpiresAt)`을 hard cap으로 둔다. AttemptGroup consumer는 current owner mismatch 때만 exact fence와 `Session.proposedAt < appliedAt`을 확인하고 기존 상태 전진에만 source event를 허용하며 terminal 적용 시 즉시 logical fence를 종료한다.
- cleanup·관측성: 최대 1시간 scheduler가 due source/group/session 연결을 unset하고 CLEANED·sourceUnlinkedAt을 기록한다. 성공/실패·지연 bucket·duration 집계 로그와 저카디널리티 metric, 24시간 초과 경보를 추가했다. HTTP server span 아래 `owner_rebind_consume` INTERNAL span을 만들고 W3C traceId를 연결하되 baggage와 source/target/subject/Claim/group/session/payload/digest/credential은 log·metric tag·span attribute에 넣지 않는다.
- 보안: owner endpoint는 feature flag가 켜져도 test principal 또는 Lattice 외부 검증을 통과한 Identity workload route에만 열리고 Learning Core role·unsigned·미설정 route는 거절한다. production owner consumer와 cleanup flag 기본값은 false다.
- 테스트 추가: decoder canonicalization/strict rejection, 204/400/409/422/503와 Retry-After, Identity/wrong-role/unsigned, service APPLIED/NOOP/CONFLICT/PENDING/duplicate, 100건 상한, phone projection/candidate, active Session fence, exact/expired legacy status, terminal 종료, cleanup failure isolation·overdue privacy log, 실제 HTTP traceId/inner span/baggage 미전파를 검증했다. replica-set integration test는 legacy missing ownerVersion→2, Claim/Grant/ledger 불변, concurrent duplicate와 active Reservation rollback을 검증하도록 작성했다.
- 테스트 결과: 최종 `./gradlew clean test`는 compile 후 118개 test case 중 비-Docker 114개가 통과했다. 기존 3개와 신규 `OwnerRebindMongoIntegrationTest` 등 Testcontainers suite 4개는 local Docker environment를 찾지 못해 test body 실행 전 initialization failure가 났다. 별도 owner-rebind unit/MVC/security/trace 타깃 suite는 `BUILD SUCCESSFUL`이다. `git diff --check`도 통과했다.
- 유지한 계약: 새 Claim·Grant·allocation·consumption 금지, phone당 무료 1회와 TrialClaim 3년 보존, immutable ledger, stable subjectRef, active Reservation rewrite 금지, source 신규 authorization 금지, Identity/Learning Core 코드와 AWS resource 비변경, production flag off를 유지했다.
- 위험·미확인: replica-set transaction/index/concurrency test의 실제 실행 성공은 Docker daemon이 가능한 환경에서 재확인해야 한다. Identity consumer별 durable fan-out, Learning Core owner migration/source deny, 실제 Lattice/IAM/SG와 staging 순서 역전·응답 유실 E2E가 남아 있으므로 production 활성화할 수 없다. historical backfill과 privileged repair route는 의도적으로 제외했다.
- 예상 밖 diff: 없었다. 작업 시작 전 이미 수정돼 있던 ADR-002, CONTRACT_DECISIONS, CURRENT_STATE, WORKLOG와 통합 계약의 사용자 승인 변경은 보존하고 TMI-120 구현 상태만 추가했다. Identity/Learning Core 저장소, Jira, git commit/push는 변경하지 않았다.
- 다음 작업: Docker daemon이 있는 환경에서 `./gradlew clean test`를 다시 실행해 4개 replica-set suite를 통과시킨 뒤 diff를 검토한다. 그 후 사용자 승인으로 Jira TMI-120 상태를 전환하고, 별도 Jira에서 Identity durable fan-out과 Learning Core owner consumer를 구현해 staging E2E를 진행한다.

## 2026-09-02 — TMI-120 이후 작업 순서 설명

<!-- codex-turn:tmi-120-next-work-explanation -->

- 날짜: 2026-09-02
- 브랜치: Billing `feat/TMI-120-trial-owner-rebind-consumer`
- Jira: `TMI-120` 참고. Jira 변경은 수행하지 않았다.
- 작업 목표: Billing owner rebind 구현 뒤 남은 검증과 cross-service 후속 구현 순서를 확정된 production gate 기준으로 설명한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 즉시 작업: Docker 환경에서 `./gradlew clean test`를 재실행해 기존 3개와 신규 owner rebind Mongo replica-set suite를 검증하고 최종 diff를 검토한다. 통과 후 사용자 승인으로 TMI-120을 완료 처리한다.
- 후속 구현: Identity consumer별 durable delivery/fan-out, Learning Core lifecycle별 owner migration/source deny consumer 순서로 별도 계획·Jira를 진행한다.
- release gate: Mongo schema v4 migration과 Lattice IAM/SG를 staging에 반영하고 순서 역전·중복·응답 유실 E2E를 통과한 뒤에만 production flag/canary를 켠다.
- 유지한 계약: Billing feature flag 기본 off, 새 권리 미생성, historical backfill·privileged repair 제외, Identity/Learning Core/AWS 별도 승인 범위를 유지했다.
- 테스트 결과: 설명·기록만 변경해 Gradle 테스트는 재실행하지 않았다. 직전 clean test에서 비-Docker 114개 통과, Docker 미가동으로 Testcontainers 4개 initialization failure 상태다.
- 위험·미확인: replica-set test가 아직 실행되지 않았으므로 TMI-120을 완료로 닫거나 production 활성화할 단계는 아니다.
- 예상 밖 diff: 없으며 기존 구현과 사용자 승인 문서 변경을 보존했다.
- 다음 작업: Docker daemon을 실행한 뒤 Billing 전체 테스트를 재검증한다.

## 2026-09-02 — Identity durable owner-event fan-out 구현 인계 설명

<!-- codex-turn:identity-owner-event-fanout-handoff-explanation -->

- 날짜: 2026-09-02
- 브랜치: Billing `feat/TMI-120-trial-owner-rebind-consumer`
- Jira: Billing `TMI-120`과 후속 Identity 작업 참고. Jira 변경은 수행하지 않았다.
- 작업 목표: Identity 팀이 `UserMerged`와 `TrialOwnerRebindApproved`를 Billing/Learning Core에 독립적으로 durable 전달하는 후속 작업을 계획·구현할 수 있도록 현재 코드와 목표 구조를 설명한다.
- 확인한 현재 구현: Identity `UserMergedOutbox`는 event core와 `PENDING/IN_FLIGHT/PUBLISHED/DEAD_LETTER` 단일 delivery 상태가 결합돼 있고 publisher configuration은 endpoint 하나와 legacy audience를 사용한다. phone eligibility publisher 패턴에는 lease/retry/dead-letter와 auth-failure scope pause가 있지만 owner rebind event core/fan-out은 아직 없다.
- 목표 구조: lifecycle별 immutable event core를 유지하고 별도 delivery에 `(eventId, consumer=BILLING|LEARNING_CORE)` unique를 둔다. lifecycle Transaction에서 core와 두 delivery를 원자 저장하며 delivery에는 payload를 복제하지 않는다. status·attempt·nextAttemptAt·lease·failure·published/dead-letter/cleanup과 feature flag는 consumer별 독립 상태다.
- wire/route: Guest는 기존 5-field `UserMerged` v1을 그대로 두고 Billing/Learning Core 모두 `/internal/v1/owners/merge/events`로 보낸다. phone은 exact `TrialOwnerRebindApproved` v1을 Billing `/internal/v1/eligibility/trial/owner/events`, Learning Core `/internal/v1/owners/trial/rebind/events`로 보낸다.
- lifecycle: Guest merge aggregate commit과 같은 Transaction에서 core+두 delivery를 만든다. phone rejoin은 source 비활성/revoked, target verified와 binding revision을 확정한 Transaction에서 별도 core+두 delivery를 만들고 raw phone/candidate/credential은 wire·delivery/log에 넣지 않는다.
- transport/실패 처리: Identity application task role의 VPC Lattice SigV4(`vpc-lattice-svcs`, `ap-northeast-2`)를 사용하고 W3C traceparent를 inject한 뒤 최종 서명하며 redirect/baggage는 허용하지 않는다. 2xx는 해당 delivery만 published, timeout/connection/408/425/429/5xx 및 Billing 503 pending은 same event retry, 유효한 Retry-After를 존중한다. 400/409/422는 permanent dead-letter, 401/403은 해당 consumer delivery circuit만 중지한다.
- migration/활성화: 기존 `user_merged_outbox`는 transition 동안 core로 reader-first 호환하고 legacy Learning Core publisher 상태를 깨뜨리지 않는다. 신규 event부터 delivery 두 건을 만들며 historical Billing merge backfill은 하지 않는다. consumer readiness와 preflight가 끝나기 전 모든 신규 publisher flag는 false다.
- 필수 테스트: core+두 delivery atomicity, `(eventId, consumer)` unique, consumer별 독립 success/retry/dead-letter, multi-instance lease, response/Retry-After 분류, legacy row preflight, exact wire fixture, SigV4 after trace inject, no redirect/baggage, 민감정보 비로깅과 publisher flag off를 검증해야 한다.
- 유지한 계약: Identity가 Billing/Learning Core DB를 직접 수정하지 않고 merge/signup 요청이 downstream HTTP 완료를 동기 대기하지 않는다. global PUBLISHED, consumer별 payload 복제, 기존 eventId payload 변경, historical backfill과 production 조기 활성화를 금지한다.
- 변경 파일: Billing `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. Identity는 계약 확인을 위해 읽기만 했다.
- 테스트 결과: 설명·기록 작업으로 애플리케이션 테스트는 실행하지 않았다. `git diff --check`를 수행한다.
- 위험·미확인: Identity 실제 collection/index 이름, transition row 분류와 phone rejoin lifecycle service의 정확한 Transaction 위치는 Identity 저장소 계획/ADR에서 코드 근거와 함께 확정해야 한다. Billing Mongo Testcontainers 재검증과 Learning Core consumer 구현 전에는 publisher를 활성화할 수 없다.
- 예상 밖 diff: 없으며 Identity/Learning Core 코드, Jira, AWS와 git 상태를 변경하지 않았다.
- 다음 작업: Identity 저장소에서 reader-first migration과 lifecycle별 core/delivery/publisher 구현 계획서를 작성하고 사용자 승인 후 별도 Jira를 생성한다.
## 2026-09-03 — PHONE_REJOIN 과거 학습 이력 이전 정책 충돌 진단

<!-- codex-turn:phone-rejoin-learning-history-policy-diagnosis -->

- 날짜: 2026-09-03
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 조회·댓글·본문·상태 변경은 수행하지 않았다.
- 작업 목표: Stage 7 보정 요청의 “phone proof는 학습 데이터 소유권 증명이 아니다” 정책과 현재 Billing 구현·ADR/PLAN이 충돌하는지 확인한다.
- 확인한 구현: `OwnerRebindService.processOnce`는 phone/Guest 구분 없이 active Reservation/PROCESSING 확인 후 `createFenceIfRequired`와 owner CAS를 실행한다. fence 조회는 `AttemptGroupRepository.findNonTerminalBySubject`의 `openGuard=true` 조건에 한정된다.
- 진단 결과: Billing은 시험·답안·피드백을 직접 복사하지 않지만 `PHONE_REJOIN`에서도 entitlement owner를 target으로 이전한다. 기존 ADR-003/PLAN-006은 Learning Core phone route와 시험/Session ownership migration까지 전제하므로 전체 설계는 과거 학습 데이터가 새 계정에 연결될 수 있는 방향이며 새 정책과 충돌한다.
- 필요한 보정: `TrialOwnerRebindApproved`는 Billing-only delivery로 제한하고, phone rejoin subject에 terminal/nonterminal 구분 없이 AttemptGroup이 하나라도 존재하면 owner CAS와 fence 없이 inbox `NOOP`, `affectedSubjectCount=0`, HTTP 204로 수렴해야 한다. `USER_MERGED`는 기존 owner 이전과 active Session fence를 유지한다.
- 유지할 계약: phone 이력 NOOP에서도 새 Claim·Grant·allocation·consumption 생성, unit 복원, ledger 수정과 Claim 재개방을 금지한다. strict decoder, event ID/digest 멱등성, active Reservation/PROCESSING 503 pending과 두 inbound route는 유지한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 진단 기록으로 갱신했다. 애플리케이션·ADR·PLAN·통합 계약·Identity/Learning Core·Jira는 변경하지 않았다.
- 테스트 결과: 분석과 기록만 수행해 Gradle 테스트는 실행하지 않았다. 현재 코드와 계약 문서를 `rg`/직접 확인했다.
- 위험·미확인: 현재 상태로 publisher를 활성화하면 Billing owner와 Learning Core owner가 불일치하거나 기존 phone migration 전제에 따라 과거 학습 기록이 새 계정에 노출될 수 있다. 관련 feature flag는 보정과 staging E2E 전까지 off를 유지해야 한다.
- 예상 밖 diff: 작업 시작 시 worktree는 clean이었으며 진단 기록 두 파일 외 변경은 만들지 않았다.
- 다음 작업: 승인된 Stage 7 정책으로 ADR-003, PLAN-006, CONTRACT_DECISIONS, 통합 계약과 AGENTS 기준을 정합화하고 Billing service/repository/회귀 테스트를 보정한다.
## 2026-09-03 — 중단 무료시험의 탈퇴·재가입 동작 확인

<!-- codex-turn:phone-rejoin-interrupted-exam-behavior -->

- 날짜: 2026-09-03
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 변경은 수행하지 않았다.
- 작업 목표: 무료시험 중단 후 같은 AttemptGroup 재응시가 가능한 상태에서 탈퇴·동일 전화번호 재가입 시 새 계정이 시험을 계속할 수 있는지 확인한다.
- 확인한 구현: `ReserveService`는 active Claim owner가 요청 userId와 같아야 하고, 같은 subject의 OPEN/RETAKE_AVAILABLE group이면 `REPLACEMENT`로 기존 attemptGroupId를 재사용하며 추가 allocation을 hold하지 않는다.
- 현재 미보정 동작: `PHONE_REJOIN`이 owner link를 target으로 이전하므로 새 계정은 같은 group을 이어갈 수 있는 방향이다. 이는 새 무료권 지급이 아니라 기존 consumption/AttemptGroup 승계다.
- Stage 7 보정 후 동작: AttemptGroup이 하나라도 있으면 phone rejoin이 owner 이전 없는 `NOOP`이므로 새 계정은 기존 group을 이어갈 수 없고 새 Claim/Grant도 받을 수 없다. 전화번호당 1회와 과거 학습 데이터 격리를 우선하는 명시적 결과다.
- 경계 조건: Session commit/confirm 전에 종료되어 AttemptGroup이 생성되지 않았다면 active Reservation/PROCESSING 해소 후 미사용 owner 이전 대상이 될 수 있다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 분석 기록으로 갱신했다. 애플리케이션·계약·Jira는 변경하지 않았다.
- 테스트 결과: 코드 분석만 수행해 Gradle 테스트는 실행하지 않았다.
- 위험·미확인: 사용자가 “중단된 시험만 재가입 계정에서 계속”을 원한다면 any-history NOOP 정책과 충돌하므로 별도의 제한적 소유권 증명·Learning Core 연동 계약이 필요하다.
- 예상 밖 diff: 없음. 기존 진단 기록 변경을 보존했다.
- 다음 작업: 중단 시험도 차단하는 strict NOOP 정책을 유지할지, exact nonterminal attempt에 한한 별도 승계 정책을 설계할지 제품 결정을 확인한다.
## 2026-09-03 — 중단 무료시험 제한적 승계 방향 검토

<!-- codex-turn:limited-interrupted-attempt-rejoin-policy -->

- 날짜: 2026-09-03
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 변경은 수행하지 않았다.
- 작업 목표: 완료하지 않은 무료시험은 탈퇴·동일 전화번호 재가입 뒤에도 이어볼 수 있어야 한다는 사용자 방향을 현재 any-history NOOP 정책과 비교한다.
- 분석 결과: AttemptGroup 존재만으로 전부 NOOP 처리하면 실제로 완료하지 않은 OPEN/RETAKE_AVAILABLE 사용 건도 영구 차단하므로 제품 의도보다 강하다. 상태별로 미사용, 재개 가능, 처리 중, 완료를 구분하는 편이 적절하다.
- 권장 상태표: 이력 없음은 미사용 owner 이전, OPEN/RETAKE_AVAILABLE은 새 차감 없이 exact AttemptGroup 제한 승계, GRADING은 terminal 판정까지 503 pending, COMPLETED는 owner/fence 변경 없는 성공 NOOP다.
- 불변식: 새 TrialClaim·Grant·allocation·consumption을 만들지 않고 unit을 복원하지 않는다. 동일 attemptGroupId/mockExamId/consumption을 유지하며 완료된 시험·답안·피드백은 phone proof만으로 이전하지 않는다.
- cross-service 영향: Billing owner만 이전하면 Learning Core owner와 달라지므로 resumable exact group만 대상으로 하는 별도 Learning Core migration event 또는 동등한 제한적 권한 계약이 필요하다. 기존 phone event를 Learning Core에 포괄 전달하는 방식은 과거 기록 노출 위험 때문에 부적절하다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 검토 기록으로 갱신했다. 코드·ADR·PLAN·Jira는 변경하지 않았다.
- 테스트 결과: 정책 분석만 수행해 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 위험·미확인: OPEN과 RETAKE_AVAILABLE을 모두 “미완료”로 인정할지, GRADING 결과가 실패해 RETAKE_AVAILABLE이 된 경우 승계를 허용할지 최종 계약 승인이 필요하다.
- 예상 밖 diff: 없음. 앞선 진단 기록을 보존했다.
- 다음 작업: 상태표와 Billing→Learning Core exact-group 전달 구조를 확정한 뒤 ADR-003/PLAN-006 및 TMI-120 완료 조건을 보정한다.
## 2026-09-03 — 재가입 기록 격리와 중단 시험 예외 확인

<!-- codex-turn:rejoin-history-isolation-resume-exception-confirmation -->

- 날짜: 2026-09-03
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 변경은 수행하지 않았다.
- 작업 목표: 탈퇴·재가입 후 과거 기록은 연동하지 않되 중단된 무료시험의 동일 AttemptGroup만 이어보게 한다는 정책 이해를 확인한다.
- 확인한 방향: 완료된 과거 시험·답안·피드백은 새 userId로 이전하지 않는다. OPEN/RETAKE_AVAILABLE인 exact 무료 AttemptGroup만 새 차감 없이 제한 승계한다.
- 기술적 정정: Billing AttemptGroup 자체에는 userId가 없으므로 Billing은 stable subjectRefId의 `BillingSubjectLink.userId`를 CAS 변경한다. Learning Core가 보유한 exact AttemptGroup/Session current owner는 별도 제한 이벤트로 변경해야 한다.
- 상태 경계: GRADING은 판정 완료까지 pending, COMPLETED는 성공 NOOP다. 새 Claim·Grant·allocation·consumption 생성과 unit 복원은 금지한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 정책 설명 기록으로 갱신했다. 코드·ADR·PLAN·Jira는 변경하지 않았다.
- 테스트 결과: 설명·기록 작업으로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 위험·미확인: exact-group Learning Core 전달 event, 양 서비스 전환 순서와 중간 pending/failure 수렴 계약은 ADR 보정에서 확정해야 한다.
- 예상 밖 diff: 없음. 기존 Stage 7 진단 기록을 보존했다.
- 다음 작업: 사용자가 이 상태표를 최종 승인하면 ADR-003/PLAN-006과 구현 계획을 먼저 정합화한다.
## 2026-09-03 — AttemptGroup과 시험 Session 의미 설명·범위 정정

<!-- codex-turn:attempt-group-exam-session-meaning-correction -->

- 날짜: 2026-09-03
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 변경은 수행하지 않았다.
- 작업 목표: 앞선 `AttemptGroup/Session owner` 표현에서 Session이 무엇인지 설명하고 기존 restart 계약에 맞게 승계 범위를 정정한다.
- 확인한 계약: `sessionId`는 Learning Core의 현재 `examId`인 한 번의 시험 실행 식별자다. `AttemptGroup`은 최초 consumption과 replacement Session을 묶으며 mockExamId를 고정한다.
- restart 동작: 앱 종료 뒤 기존 Session을 이어풀지 않고 `ABANDONED_RESTARTED`로 닫은 뒤 새 key·새 examId(Session)로 처음부터 시작한다. 결과·upload·grading Job·summary는 새 Session에 복사하지 않는다.
- 정정: 재가입 시 기본적으로 이전해야 하는 것은 exact nonterminal AttemptGroup을 계속 사용할 권리다. 기존 중단 Session의 owner와 답안/결과를 target으로 옮길 필요는 없고, target 명의의 새 replacement Session을 같은 group에 연결하는 방식이 기존 계약과 일치한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 설명 기록으로 갱신했다. 코드·ADR·PLAN·Jira는 변경하지 않았다.
- 테스트 결과: 계약·코드 분석만 수행해 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 위험·미확인: 사용자가 마지막 문제 위치와 임시 답안까지 그대로 재개하길 원하면 현재 restart 계약을 변경하고 exact Session 데이터 이전 범위를 별도로 승인해야 한다.
- 예상 밖 diff: 없음. 앞선 분석 기록을 보존했다.
- 다음 작업: “같은 group에서 새 시험을 처음부터 시작”과 “기존 Session 진행 위치 그대로 재개” 중 제품 의도를 명확히 한 뒤 Stage 7 계약을 보정한다.

## 2026-09-03 — PHONE_REJOIN 미완료 AttemptGroup 제한 승계 구현

<!-- codex-turn:phone-rejoin-resumable-group-implementation -->

- 날짜: 2026-09-03
- 브랜치: Billing `develop`
- Jira: `TMI-120` 관련 보정. 사용자 요청으로 코드·계약을 수정했으며 Jira 조회·본문·댓글·상태는 변경하지 않았다.
- 작업 목표: 탈퇴·동일 phone 재가입 시 완료된 과거 시험은 연결하지 않고, 미완료 무료시험은 기존 consumption·AttemptGroup을 재사용해 target의 새 Session으로 처음부터 재응시할 수 있게 한다.
- 변경 파일: `OwnerRebindService`, `AttemptGroupRepository`, owner rebind unit/MVC/Mongo integration test, `AGENTS.md`, ADR-003, PLAN-006, `CONTRACT_DECISIONS.md`, 통합 계약, `CURRENT_STATE.md`, `WORKLOG.md`.
- 변경 동작: phone prerequisite와 active Reservation/PROCESSING 확인 뒤 existing Claim의 AttemptGroup을 indexed `trialClaimId`로 조회한다. 이력 없음·OPEN·RETAKE_AVAILABLE은 owner CAS APPLIED, GRADING은 503 `OWNER_REBIND_PENDING`, COMPLETED는 owner/fence 변경 없이 inbox NOOP/affectedSubjectCount=0과 HTTP 204다.
- replacement 의미: 새 Claim·Grant·allocation·consumption이나 unit 복원을 만들지 않는다. target reserve는 기존 attemptGroupId·mockExamId를 재사용하고 source Session을 이전하지 않은 채 새 key·새 examId로 처음부터 시작한다.
- cross-service 계약: `UserMerged`만 Billing/LC delivery를 생성하고 Identity→Learning Core 기존 workload JWT를 유지한다. `TrialOwnerRebindApproved`는 Identity→Billing Lattice SigV4 delivery만 생성하며 Learning Core phone route와 과거 Session/결과 migration 전제를 제거했다.
- 테스트 추가: phone OPEN/RETAKE_AVAILABLE APPLIED, GRADING pending/no-write, COMPLETED NOOP/no-fence, NOOP replay, MVC 204 replay와 Mongo Transaction에서 OPEN group 불변·COMPLETED Claim/Grant/ledger/group 불변을 검증한다.
- 테스트 결과: service/MVC target test는 `BUILD SUCCESSFUL`; `compileTestJava` 성공. 최종 `./gradlew clean test`는 123개 중 비-Docker 119개가 통과했고 Docker daemon 미가동으로 기존 3개와 `OwnerRebindMongoIntegrationTest` 등 Testcontainers 4개가 initialization failure다. `git diff --check`는 통과했다.
- 유지한 외부 계약: 두 Billing inbound route, strict decoder/wire payload, eventId/digest 멱등성, 204/400/409/422/503 mapping, active Reservation/PROCESSING pending, Claim 3년 보존과 immutable ledger를 유지했다.
- 위험·미확인: 새 Mongo integration assertion은 작성·컴파일됐으나 Docker에서 실제 실행되지 않았다. Learning Core가 Billing REPLACEMENT 응답의 기존 group을 target 새 Session에 연결하고 source Session을 이전하지 않는지 별도 저장소 구현·staging E2E가 필요하다.
- 배포 전 확인: Docker replica-set 전체 test, Identity event별 delivery, Learning Core UserMerged consumer/phone replacement, Lattice IAM/SG와 중복·역순·응답 유실·인증 실패 E2E가 모두 통과할 때까지 owner rebind flag를 off로 유지한다.
- 예상 밖 diff: 없음. 작업 시작 전 존재한 이번 대화의 CURRENT_STATE/WORKLOG 진단 기록만 보존했고 Identity/Learning Core 코드, AWS, Jira, git commit/push는 변경하지 않았다.
- 다음 작업: Docker 환경에서 전체 Testcontainers suite를 통과시킨 뒤, Identity와 Learning Core에 수정된 destination/replacement 계약을 전달하고 각 저장소 구현 계획을 보정한다.

## 2026-09-03 — PR 생성 안내 미표시 원인 확인

<!-- codex-turn:pr-prompt-missing-diagnosis -->

- 날짜: 2026-09-03
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 변경은 수행하지 않았다.
- 작업 목표: 사용자가 push했다고 인식했지만 GitHub에 PR 생성 안내가 보이지 않는 원인을 local/remote branch 상태로 확인한다.
- 확인 결과: fetch 후에도 local `develop` HEAD `804d4eb`은 `origin/develop`보다 1 commit 앞서 있다. 원격 feature branch는 생성되지 않았고 해당 commit은 원격 develop에도 없다.
- 원인: commit이 PR source용 feature branch가 아니라 local develop에 직접 생성됐다. 동일 branch 안의 commit은 develop→develop PR을 만들 수 없고, 실제 push도 원격 추적 상태상 성공하지 않았다.
- 권장 복구: 현재 commit에서 새 `fix/TMI-120-phone-rejoin-policy` branch를 만들어 commit을 보존하고, local develop pointer를 `origin/develop`로 돌린 뒤 새 branch를 push한다. GitHub PR base는 오래된 main이 아니라 develop으로 명시한다.
- 변경 파일: 진단 기록을 위한 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 변경했다. 애플리케이션·계약·Jira·원격 branch는 변경하지 않았다.
- 테스트 결과: 코드 변경이 없어 Gradle 테스트는 실행하지 않았다. `git fetch --prune origin`, branch tracking, commit graph와 rev-list를 확인했다.
- 위험·미확인: 사용자가 실행한 원래 push 명령의 오류 출력은 확인하지 못했지만 remote tracking이 갱신된 뒤에도 ahead 1이므로 commit이 origin에 없는 사실은 확정이다.
- 예상 밖 diff: 없음. 앞선 구현 commit 뒤 이번 진단 기록 두 파일만 새로 수정됐다.
- 다음 작업: 사용자가 직접 branch 분리·push한 뒤 base develop/head fix branch로 PR을 생성한다.

## 2026-09-03 — PHONE_REJOIN replacement discovery 계약 공백 확인

<!-- codex-turn:phone-rejoin-replacement-discovery-gap -->

- 날짜: 2026-09-03
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 변경은 수행하지 않았다.
- 작업 목표: 재가입 target에게 Learning Core의 기존 ExamSession이 없을 때 현재 reserve 계약만으로 같은 AttemptGroup의 replacement Session을 만들 수 있는지 확인한다.
- 확인한 구현: `ReserveRequest.mockExamId`는 필수이고 `ReserveService.determineKind`는 기존 OPEN/RETAKE_AVAILABLE group의 `mockExamId`와 요청 값이 exact match해야만 `REPLACEMENT`를 반환한다. 응답에는 attemptGroupId/mockExamId가 있지만 요청자가 기존 값을 이미 알아야 도달할 수 있다.
- 결론: Learning Core가 기존 mockExamId를 모르면 예상 밖 REPLACEMENT를 받은 뒤 거절하는 것이 아니라 Billing 응답 전 `STATE_CONFLICT`가 발생한다. 문서에 적힌 phone replacement E2E에는 discovery/continuation 계약이 빠져 있다.
- 권장 계약: 일반 reserve는 기존 fail-closed 동작을 유지한다. phone rejoin에만 Billing이 authoritative attemptGroupId/mockExamId와 명시적 continuation reason/context를 제공하고, Learning Core가 이를 reserve에 echo한 경우에만 target 명의 새 examId를 같은 group에 연결한다.
- 유지할 경계: source Session·답안·결과 owner는 변경하지 않고 새 Claim·Grant·allocation·consumption도 만들지 않는다. 명시적 context가 없는 일반 unexpected REPLACEMENT는 계속 거절한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 분석 기록으로 갱신했다. 애플리케이션·ADR·PLAN·Jira는 변경하지 않았다.
- 테스트 결과: 코드·계약 분석 작업이므로 Gradle 테스트는 실행하지 않았다. 문서 형식은 `git diff --check`로 확인한다.
- 위험·미확인: 별도 discovery route와 reserve 확장 중 선택, context 만료·재사용·경합 처리, 최초 target Session confirm 뒤 context 종료 조건은 ADR에서 확정해야 한다.
- 예상 밖 diff: 없음.
- 다음 작업: discovery/continuation wire 계약 선택지를 확정한 뒤 ADR-003, PLAN-006, 통합 계약과 Billing/Learning Core 구현 계획을 함께 보정한다.

## 2026-09-03 — PHONE_REJOIN replacement discovery 문제 설명

<!-- codex-turn:phone-rejoin-discovery-gap-explanation -->

- 날짜: 2026-09-03
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 변경은 수행하지 않았다.
- 작업 목표: 재가입 replacement 계약 공백이 발생한 이유를 제품 흐름 중심으로 설명한다.
- 설명 결론: 기존 restart는 같은 Learning Core 사용자 기록에서 기존 mockExamId를 조회할 수 있다는 전제로 설계됐다. 탈퇴·재가입하면 새 userId에는 그 기록이 없지만 Billing은 보안상 기존 mockExamId exact match를 계속 요구하므로, 새 계정이 모르는 값을 먼저 제출해야 하는 순환 의존이 생긴다.
- 유지할 경계: Billing이 아무 요청이나 기존 group에 자동 연결하지 않는 fail-closed 검사는 필요하다. 해결은 검사를 제거하는 것이 아니라 Billing이 승인한 phone-rejoin continuation context로 기존 group 정보를 안전하게 전달하는 것이다.
- 변경 파일: `docs/codex/WORKLOG.md`만 설명 기록으로 갱신했다. 코드·계약·Jira는 변경하지 않았다.
- 테스트 결과: 설명 작업이므로 Gradle 테스트는 실행하지 않았다. 문서 형식만 확인한다.
- 예상 밖 diff: 없음.
- 다음 작업: 사용자가 계약 설계를 진행하면 discovery API와 context 검증 항목의 선택지를 제시한다.

## 2026-09-03 — PHONE_REJOIN 인증·승인 오류 구분 설명

<!-- codex-turn:phone-rejoin-authn-authorization-distinction -->

- 날짜: 2026-09-03
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 변경은 수행하지 않았다.
- 작업 목표: 재가입 replacement 실패가 사용자 인증 오류인지 구분한다.
- 설명 결론: 새 계정의 로그인/JWT 인증은 정상이다. 과거 Learning Core 기록을 연결하지 않아 기존 attemptGroupId/mockExamId를 알 수 없고, Billing의 기존-group exact match 승인 조건을 충족하지 못해 발생하는 authorization/contract state conflict다.
- 유지할 경계: 과거 학습 기록을 새 계정에 전부 이전하지 않는다. Billing이 승인한 제한적 continuation context로 중단 AttemptGroup만 연결해야 한다.
- 변경 파일: `docs/codex/WORKLOG.md`만 설명 기록으로 갱신했다. 코드·계약·Jira는 변경하지 않았다.
- 테스트 결과: 설명 작업이므로 Gradle 테스트는 실행하지 않았다. 문서 형식만 확인한다.
- 예상 밖 diff: 없음.
- 다음 작업: continuation contract 선택지를 확정하고 ADR/PLAN을 보정한다.

## 2026-09-03 — PHONE_REJOIN continuation discovery와 명시적 replacement 구현

<!-- codex-turn:phone-rejoin-continuation-implementation -->

- 날짜: 2026-09-03
- 브랜치: Billing `develop`
- Jira: `TMI-120` 관련 보정. Jira 조회·본문·댓글·상태는 변경하지 않았다.
- 작업 목표: 새 userId의 Learning Core가 과거 Session/mockExamId를 몰라 phone replacement가 reserve 전 STATE_CONFLICT로 막히는 계약 공백을 해소한다.
- 변경 동작: Learning Core 전용 `POST /internal/v1/reservations/continuations/phone`은 target userId만 strict decode한다. current owner transition이 PHONE_REJOIN이고 Claim/link/group이 일치하며 group이 OPEN/RETAKE_AVAILABLE이면 Billing authoritative continuationReason/id, attemptGroupId, mockExamId를 200으로 반환하고 대상 없음은 204다.
- owner epoch: owner CAS와 같은 update에서 `BillingSubjectLink.ownerTransitionReason`, `ownerTransitionId`를 기록한다. transition ID는 검증된 Identity owner eventId이며 다음 CAS에서 교체돼 과거 context가 자동 무효화된다. raw phone이나 source user 연결은 추가하지 않는다.
- reserve 계약: 기존 3-field body 또는 phone context를 포함한 6-field body만 허용한다. continuationReason/id/expectedAttemptGroupId는 all-or-none이며 current link transition, existing group/mock을 Transaction에서 다시 검증한다. 불일치와 INITIAL context는 `RESERVATION_STATE_CONFLICT`다.
- 응답·멱등성: phone reserve와 status에 optional reason/id를 저장·반환하며 group/mock은 request echo 대신 existing AttemptGroup 값을 사용한다. 일반 request/response에는 optional field가 없고 기존 base canonical digest byte 형식을 그대로 유지해 배포 전 command retry가 충돌하지 않는다.
- 무료권 불변식: 새 Claim·Grant·allocation·consumption을 만들거나 unit을 복원하지 않는다. source Session·답안·결과 owner도 변경하지 않고 target의 새 Session만 기존 group에 연결한다.
- 보안: local/test에서 continuation route는 owner-rebind flag가 켜진 경우 Learning Core role만 접근하고 Identity role은 거절한다. Lattice 환경은 외부 service auth policy에 같은 환경 Learning Core task role의 exact POST route를 추가해야 한다.
- 변경 파일: reservation continuation service/policy/DTO/controller/decoder, reserve command/hash/entity/snapshot/status, BillingSubjectLink owner CAS, SecurityConfig, 관련 unit/MVC/Mongo integration tests, ADR-003, PLAN-006, 통합 계약, CONTRACT_DECISIONS, AGENTS, CURRENT_STATE와 WORKLOG.
- 테스트 결과: 신규·핵심 MVC/security/decoder/policy/service/hash 테스트는 `BUILD SUCCESSFUL`. 최종 `./gradlew clean test`는 136개 중 비-Docker 132개 통과, Docker daemon 미가동으로 Testcontainers 기반 4개 integration class가 initialization failure다. 새 Mongo 테스트는 작성·컴파일됐으나 실제 replica-set 실행이 남았다. `git diff --check`를 최종 확인한다.
- 유지·변경한 외부 계약: 기존 reserve 3-field request와 일반 response/idempotency는 유지한다. 새 continuation 조회 route와 phone reserve의 optional 3-field echo, phone response/status optional 2-field가 추가됐다.
- 위험·미확인: Learning Core strict response reader와 신규 discovery 호출, 일반 unexpected REPLACEMENT fail-closed, 실제 Lattice IAM route policy는 이 저장소에서 구현하지 않았다. consumer-first 배포와 staging E2E가 필요하다.
- 예상 밖 diff: 없음. 앞선 같은 대화의 분석 기록 문서 변경을 보존했으며 Identity/Learning Core 코드, AWS, Jira, git commit/push는 변경하지 않았다.
- 다음 작업: Docker 환경에서 전체 integration test를 통과시키고 Learning Core 계약/구현을 reader-first로 보정한 뒤 Lattice staging 순서 역전·응답 유실 E2E를 수행한다.

## 2026-09-03 — phone reserve continuation 재검증 설명

<!-- codex-turn:phone-reserve-continuation-validation-explanation -->

- 날짜: 2026-09-03
- 브랜치: Billing `develop`
- Jira: `TMI-120` 참고. Jira 변경은 수행하지 않았다.
- 작업 목표: reserve의 continuation 세 field all-or-none 및 Billing 재검증 의미를 설명한다.
- 설명 결론: 세 field는 승계 종류, current owner transition 식별자, 기대 group을 하나의 승인 context로 구성한다. discovery와 reserve 사이의 owner/group 상태 변경이나 값 섞임을 막기 위해 일부만 받지 않고 Billing current state와 다시 비교한다.
- 성공 의미: exact PHONE_REJOIN context와 기존 mockExamId가 모두 일치할 때만 같은 consumption의 REPLACEMENT로 분류하며, 새 권리나 과거 Session 데이터 이전을 뜻하지 않는다.
- 변경 파일: `docs/codex/WORKLOG.md`만 설명 기록으로 갱신했다. 코드·계약·Jira는 변경하지 않았다.
- 테스트 결과: 설명 작업이므로 Gradle 테스트는 재실행하지 않았고 문서 형식만 확인한다.
- 예상 밖 diff: 없음.
- 다음 작업: Learning Core reader-first 구현에서 discovery 응답 저장·reserve echo·unexpected replacement fail-closed를 적용한다.

## 2026-09-03 — Learning Core phone continuation 구현 인계 정리

<!-- codex-turn:learning-core-phone-continuation-handoff -->

- 날짜: 2026-09-03
- 브랜치: Billing `develop`
- Jira: `TMI-120` 관련 Billing 계약 인계. Jira 변경은 수행하지 않았다.
- 작업 목표: Billing에 추가된 phone continuation discovery와 명시적 replacement 계약을 Learning Core 구현자가 바로 적용할 수 있게 정리한다.
- 핵심 인계: target userId에 기존 Session이 없을 때만 Billing phone continuation을 먼저 조회한다. 204면 정상 INITIAL 흐름, 200이면 반환 context와 기존 mock/group을 saga에 보존하고 새 target examId로 6-field reserve를 호출한다.
- 응답 검증: phone context를 보냈다면 REPLACEMENT, PHONE_REJOIN reason/id, group/mock exact match만 허용한다. INITIAL 예상 중 context 없는 REPLACEMENT와 phone field 불일치는 fail-closed하고 생성된 Reservation을 cancel로 보상한다.
- 데이터 경계: source Session·답안·upload·grading·summary·결과를 조회·복사·owner rewrite하지 않는다. 새 target Session만 기존 AttemptGroup에 연결하고 기존 consumption을 재사용한다.
- transport/배포: continuation 조회에는 idempotency key가 없고 reserve에는 기존 lowercase UUID v4 key를 유지한다. Billing 응답/status optional field를 먼저 허용하는 reader-first decoder를 배포한 뒤 publisher/caller를 켠다. Lattice policy에 Learning Core role의 exact continuation POST route를 추가한다.
- 변경 파일: `docs/codex/WORKLOG.md`만 인계 기록으로 갱신했다. 애플리케이션·계약·Jira는 변경하지 않았다.
- 테스트 결과: 문서 정리 작업이므로 Gradle 테스트를 재실행하지 않았고 `git diff --check`만 확인한다.
- 예상 밖 diff: 없음.
- 다음 작업: Learning Core 저장소에서 saga/outbox, strict decoder, cancel compensation, duplicate/response-loss/E2E 테스트를 구현한다.

## 2026-09-03 — phone continuation 변경 Git/PR 명령 확인

<!-- codex-turn:phone-continuation-git-pr-commands -->

- 날짜: 2026-09-03
- 브랜치: Billing `develop`
- Jira: `TMI-120` 관련 후속. Jira 변경은 수행하지 않았다.
- 작업 목표: 현재 변경을 안전하게 feature branch로 커밋·push하고 develop PR로 머지하는 명령을 안내한다.
- 확인 결과: local `develop` HEAD `9239a62`는 `origin/develop`과 동일하며 작업 트리에 phone continuation 관련 변경만 존재한다. `main`은 오래된 `e0694f9`이므로 PR base는 `develop`이어야 한다.
- 권장 branch: `fix/TMI-120-phone-continuation`.
- 변경 파일: Git 상태 확인 기록을 위해 `docs/codex/WORKLOG.md`만 추가 갱신했다. 원격·branch·commit·push·PR은 변경하지 않았다.
- 테스트 결과: 코드 변경 없이 Git 상태만 확인했으므로 테스트를 재실행하지 않았다.
- 예상 밖 diff: 없음.
- 다음 작업: 사용자가 안내 명령으로 branch 생성, staged diff 확인, commit, push, PR check와 develop merge를 수행한다.

## 2026-09-05 — Billing·Identity 병합 후 다음 작업 확인

<!-- codex-turn:next-work-after-phone-continuation-merge -->

- 날짜: 2026-09-05
- 브랜치: Billing `develop@7138810`, `origin/develop`과 일치
- Jira: Billing `TMI-120`, Identity `TMI-123`, 후속 Learning Core `TMI-125`. Jira 조회·댓글·상태 변경은 수행하지 않았다.
- 작업 목표: phone continuation과 owner-event fan-out 병합 이후의 다음 구현 순서를 확인한다.
- 확인 결과: Billing phone continuation은 PR #8, Identity durable fan-out/SigV4는 PR #38로 각각 `develop`에 병합됐다. 다음 cross-service 구현은 Learning Core `TMI-125`다.
- 다음 구현: Billing continuation 응답 reader-first 수용, phone continuation discovery와 six-field reserve exact echo/cancel 보상, 일반 unexpected REPLACEMENT fail-closed, `UserMerged` consumer·workload JWT verifier·source deny·실제 merge ownership migration을 구현한다. phone rejoin event consumer와 과거 Session·답안·결과 이전은 만들지 않는다.
- 테스트 결과: 코드 변경 없는 상태·문서 확인 작업이라 Gradle 테스트는 실행하지 않았다. Billing 작업 트리는 확인 시작 시 clean이었고 local `develop`과 `origin/develop`은 일치했다.
- 유지한 계약: phone 재가입은 Billing이 승인한 기존 nonterminal group에 target의 새 Session만 연결하고 새 Claim·Grant·allocation·consumption을 만들지 않는다. Identity→Billing은 SigV4, Identity→Learning Core `UserMerged`는 workload JWT를 유지한다.
- 위험 요소: 현재 workspace에 Learning Core 저장소가 없어 실제 `TMI-125` 구현·병합 상태를 직접 확인하지 못했다. Billing Testcontainers 4개는 Docker daemon 환경에서 전체 통과 확인이 남았다.
- 예상 밖 변경: 없음. 이번 분석 기록을 위한 Billing CURRENT_STATE/WORKLOG 변경 외 애플리케이션·계약·Identity·AWS·Jira·Git 상태는 변경하지 않았다.
- 다음 작업: Learning Core 저장소 접근 후 TMI-125 현재 상태를 확인하고, 미구현 범위를 계획·구현·검증한다. 그 뒤 Lattice/IAM/schema v4/staging E2E gate를 진행한다.

## 2026-09-05 — Learning Core TMI-122·TMI-125 병합 구현 검토

<!-- codex-turn:learning-core-tmi-122-125-post-merge-review -->

- 날짜·브랜치·Jira: 2026-09-05, Billing `develop`; 읽기 대상 Learning Core `develop@8c8208b`; `TMI-122`, `TMI-125`. Jira 변경은 수행하지 않았다.
- 작업 목표: 사용자가 구현 완료한 Learning Core phone continuation과 UserMerged consumer를 Billing·Identity 승인 계약 및 테스트 gate와 대조한다.
- 확인 결과: Learning Core phone continuation은 PR #27 merge `6e3495e`, UserMerged는 PR #28 merge `8c8208b`에 반영됐고 local/remote develop이 일치한다. exact UserMerged route, workload JWT profile, source deny, direct owner migration, phone discovery와 six-field reserve, 새 target Session·기존 group 연결 및 기본 OFF flag의 큰 방향은 계약과 일치한다.
- finding 1: `.github/workflows/deploy-staging.yml`은 `./gradlew clean test`만 실행한다. `build.gradle`의 `check -> mongoIntegrationTest` 연결만으로는 현재 CI에서 replica-set 테스트가 실행되지 않아 C17 required CI gate가 충족되지 않는다.
- finding 2: `scripts/mongodb/user-merged-prepare.js` inventory/blocker는 계획서에 명시된 orphan Result/Summary와 Result/Summary owner 대 Session owner 불일치를 검사하지 않는다. legacy 이상 데이터가 있어도 apply가 진행될 수 있으므로 production migration 전에 preflight와 Node 테스트 보완이 필요하다.
- finding 3: `PhoneContinuationResponse`는 Billing 계약상 lowercase UUID v4인 `attemptGroupId`를 opaque text로만 검사하며 테스트도 `group-existing`을 정상으로 허용한다. discovery 성공 응답 단계에서 strict UUID v4로 거절하도록 수정해야 한다.
- 테스트: Learning Core `./gradlew clean test` Java 483개 failures/errors/skipped 0, Node migration test 6개 성공, `git diff --check` 성공. `./gradlew mongoIntegrationTest`는 Docker client provider initialization failure로 실제 테스트가 실행되지 않았다.
- 유지한 계약: phone 재가입은 과거 source Session·답안·결과를 이전하지 않고 새 target Session만 Billing 승인 group에 연결한다. UserMerged만 학습 데이터 owner migration을 수행하며 기존 AttemptGroup outbox와 terminal creation operation snapshot은 rewrite하지 않는다.
- 변경 파일: Billing의 `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 검토 기록으로 갱신했다. Learning Core·Identity 애플리케이션, Jira, AWS, Mongo 운영 데이터와 Git 이력은 변경하지 않았다.
- 예상 밖 범위: Learning Core PR #28 구현 commit에는 UserMerged 외의 결제 범위·프론트 가이드·아키텍처 문서가 함께 포함됐다. 애플리케이션 외부 계약 변경은 확인되지 않았지만 후속 PR에서는 범위를 분리하는 것이 안전하다. Learning Core worktree의 기존 TMI-125 Jira 완료 문서 변경은 사용자 소유로 보존했다.
- 다음 작업: 위 세 보완을 Learning Core 후속 수정으로 처리하고 Docker CI에서 replica-set 테스트를 통과시킨다. 이후에만 Mongo dry-run/apply, writer drain/backfill, workload/Lattice/IAM과 staging E2E·P99 gate로 진행한다.

## 2026-09-05 — cross-service 기능 개발 완결성 재점검

<!-- codex-turn:cross-service-feature-completeness-audit -->

- 날짜·브랜치·Jira: 2026-09-05, Billing `develop@7138810`; 읽기 대상 Learning Core `develop@8c8208b`; `TMI-120`, `TMI-122`, `TMI-123`, `TMI-125`. Jira 변경은 수행하지 않았다.
- 작업 목표: phone 재가입 continuation과 Guest merge의 병합 뒤 기능 개발이 실제로 부족한 부분이 없는지 정상 흐름, 실패 수렴, migration, CI 관점에서 재점검한다.
- 결론: 핵심 사용자 정상 흐름은 구현됐지만 production-safe 완료는 아니다. Learning Core에 확정 코드 누락 2건, CI gate 누락 1건, Mongo unknown-commit 예외 수렴의 잠재 누락과 계획 대비 통합 테스트 공백이 남았다.
- 확정 코드 누락: phone discovery의 `attemptGroupId`가 lowercase UUID v4가 아니라 opaque text로 검증되고, migration preflight가 orphan Result/Summary 및 Result/Summary owner↔Session owner 불일치를 검사하지 않는다.
- 잠재 코드 누락: `UserOwnedTransactionExecutor`는 `UnknownTransactionCommitResult`를 mutation 재실행 대상으로 삼지 않지만 event inbox/guard를 재조회해 commit 여부로 수렴하지도 않는다. Spring이 이를 `TransactionSystemException`으로 포장하면 현재 Controller advice의 `DataAccessException` handler가 잡지 못해 500이 될 가능성이 있으므로 실제 예외 형태 기반 회귀 테스트와 503 mapping이 필요하다.
- 검증 공백: UserMerged replica-set integration test는 현재 4개로, 계획된 failure injection rollback, non-terminal operation, concurrent duplicate/write/Callback, unknown commit 수렴 등을 모두 고정하지 못한다. staging workflow도 `clean test`만 실행해 별도 `mongoIntegrationTest`를 수행하지 않는다.
- 테스트 결과: 이번에는 코드 변경 없는 재점검이므로 테스트를 재실행하지 않았다. 직전 검토의 Java 483개와 Node 6개 성공 결과를 유지하며, Docker daemon 부재로 Mongo integration test 미실행 상태도 그대로다.
- 유지한 계약: phone 재가입은 과거 학습 데이터를 이전하지 않고 Billing 승인 group에 target의 새 Session만 연결한다. Guest `UserMerged`만 Session·Result·Summary를 이전하며 Claim·Grant·consumption과 기존 outbox snapshot은 변경하지 않는다.
- 변경 파일: Billing `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 분석 기록으로 갱신했다. Learning Core의 기존 `CURRENT_STATE`, `WORKLOG`, `FIRST_UPDATE_PROGRESS_CHECKLIST` 변경과 애플리케이션 코드는 수정하지 않았다.
- 예상 밖 범위: 이번 검토에서 새 애플리케이션 변경은 없었다. Learning Core worktree의 세 문서 변경은 검토 전 존재한 사용자 소유 변경으로 보존했다.
- 다음 작업: Learning Core 후속 보정 PR에서 strict UUID, migration preflight, unknown commit 수렴·503, replica-set 회귀 테스트와 CI 실행을 한 묶음으로 완료한 뒤 staging E2E로 진행한다.

## 2026-09-05 — Learning Core 보완 확인 및 결제 범위 전환 검토

<!-- codex-turn:payment-scope-transition-after-learning-core-fixes -->

- 날짜·브랜치·Jira: 2026-09-05, Billing `develop@7138810`; 읽기 대상 Learning Core `develop@88b46c6`; 관련 `TMI-120`, `TMI-122`, `TMI-123`, `TMI-125`. Jira 변경은 수행하지 않았다.
- 작업 목표: 직전 Learning Core 누락 보완을 확인하고 무료시험 이후 1차 개발 범위에 결제를 포함해 다음 단계로 넘어갈 수 있는지 판정한다.
- 확인 결과: PR #29에서 continuation attemptGroupId UUID strict 검증, migration orphan/owner mismatch 검사, unknown commit wrapper·inbox 수렴·TransactionException 503, UserMerged replica-set 회귀 확대와 staging/verify workflow의 `mongoIntegrationTest` 실행이 반영됐다. local/remote develop은 `88b46c6`에서 일치하고 worktree는 clean이다.
- 테스트 결과: Learning Core `./gradlew clean test --no-daemon` 성공, Node migration test 7개 성공, `git diff --check` 성공. `./gradlew mongoIntegrationTest --no-daemon`은 Docker client provider initialization 단계에서 1개 initialization error로 실패해 실제 test body는 실행되지 않았다.
- 범위 판정: 무료시험·owner lifecycle의 추가 애플리케이션 기능은 결제 개발 착수를 막지 않는다. Mongo CI와 실제 AWS/staging E2E는 production activation gate로 병행 관리하고 feature flag는 계속 OFF로 둔다.
- 결제 방향: 기존 deferred C9~C11은 credit/one-time 상품 초안이라 단순 premium subscription 선호와 맞지 않는다. `PREMIUM_SUBSCRIPTION`, `SubscriptionEntitlement`와 Store subscription lifecycle 중심으로 계약을 교체한 뒤 구현해야 한다.
- 변경 파일: 분석 기록을 위해 Billing `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 갱신했다. Billing·Learning Core·Identity 애플리케이션, AWS, Jira와 Git 이력은 변경하지 않았다.
- 유지한 계약: 무료권은 verified-phone당 1회이며 구독 도입이 새 TrialClaim 지급이나 기존 consumption 복원을 만들면 안 된다. Store 원문·credential·사용자 개인정보를 로그나 원장에 저장하지 않는다.
- 예상 밖 변경: 없음. Learning Core worktree는 clean이었으며 테스트 산출물 외 source diff가 없다.
- 다음 작업: 구독 상품·권리·API/auth·Store lifecycle·환불·resolver 정책의 선택지를 확정하고 기존 deferred/credit 문서를 supersede한 결제 ADR와 구현 계획을 작성한다.

## 2026-09-05 — 기간제 무제한 결제 상품 선택지 설명 준비

<!-- codex-turn:fixed-term-unlimited-payment-options -->

- 날짜·브랜치: 2026-09-05, Billing `develop@7138810`. Jira 변경은 수행하지 않았다.
- 작업 목표: 사용자가 확정 입력한 1·3·7·14·30일 무제한 상품과 무료권 보존을 기준으로 Store mapping 이후의 결제 계약 선택지와 장단점을 설명한다.
- 사용자 확정 입력: 고정 기간 동안 무제한 사용하며 자동 갱신 요구는 제시되지 않았다. 활성 유료 권리를 무료 TrialGrant보다 먼저 사용해 무료 1회권을 보존한다.
- 공식 Store 확인: Apple App Store Connect는 limited duration service용 `Non-Renewing Subscription`을, Google Play Billing은 자동 갱신되지 않고 동일 plan top-up으로 기간을 연장하는 prepaid subscription plan을 제공한다. Google은 1주 미만 prepaid plan도 별도 acknowledgment 시간 규칙과 함께 설명한다. 정확한 각 기간·국가·Console availability는 product 생성 시 재확인한다.
- 권장 Store/account 안: Apple non-renewing subscription과 Google prepaid base plan을 provider adapter에서 정규화하고, Billing 내부 offer 5개로 매핑한다. Store에는 Billing 발급 비개인 opaque account ID만 전달하고 canonical user mapping과 restore 진실 공급원은 Billing ledger로 둔다.
- 권장 API/lifecycle 안: 앱이 Billing public API를 직접 호출하고 Identity의 Billing audience JWT를 사용한다. 검증 완료 시 entitlement를 시작하며 active 재구매는 현재 endsAt 뒤로 이어 붙인다. pending/active/expired/revoked를 지원하고 auto-renew grace/account hold는 제외한다.
- 권장 refund/notification/reconciliation 안: refund/revoke 뒤 새 시험 시작은 차단하되 confirmed AttemptGroup은 끝까지 수렴시킨다. client sync와 server notification을 함께 사용하고 periodic provider recheck를 safety net으로 둔다.
- 변경 파일: Billing `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`만 분석 기록으로 갱신했다. 계약 결정서, 애플리케이션, Jira, Store Console과 AWS는 변경하지 않았다.
- 테스트 결과: 설명·정책 분석 작업이므로 Gradle 테스트를 재실행하지 않았고 `git diff --check`만 최종 확인한다.
- 유지한 계약: 무료권의 phone dedupe/3년 보존과 TrialClaim은 유료 결제로 변경하지 않는다. Store credential·원문 receipt/token·raw phone을 문서·로그에 기록하지 않는다.
- 예상 밖 변경: 없음.
- 다음 작업: 사용자가 4번 이후 선택지를 승인하면 `CONTRACT_DECISIONS`의 deferred credit/pass 초안을 fixed-term unlimited Store 계약으로 supersede하고 ADR·구현 계획을 작성한다.

## 2026-09-05 — Store 상품 연결·복원 구조 설명

<!-- codex-turn:store-product-account-binding-restore-explanation -->

- 날짜·브랜치: 2026-09-05, Billing `develop@7138810`. Jira 변경은 수행하지 않았다.
- 작업 목표: 결제 선택지 4번인 Apple/Google 상품 등록, 내부 offer mapping, 사용자 account binding과 기기 변경 복원을 실제 구매 흐름으로 설명한다.
- 핵심 설명: Store에는 provider별 product/base plan을 등록하고 Billing catalog는 그 식별자를 `PREMIUM_1D/3D/7D/14D/30D`와 duration에 매핑한다. 앱이 보낸 가격·일수는 신뢰하지 않고 서버가 Store transaction을 검증한 뒤 catalog 값으로만 권리를 만든다.
- account binding: Billing이 발급한 비개인 purchase account reference를 Apple `appAccountToken`과 Google obfuscated account identifier에 넣어 canonical user와 연결한다. raw userId·phone·email·device ID를 Store 결속 기준으로 사용하지 않는다.
- restore: 이미 Billing에 반영된 권리는 로그인 후 Billing current entitlement 조회로 복원하고, 결제는 됐지만 앱→Billing 전달 전에 종료된 경우 앱의 Store purchase query와 server notification/reconciliation으로 재검증한다. phone 재가입 증명만으로 유료 권리를 이전하지 않는다.
- 재구매: provider transaction unique로 중복 지급을 막고 active entitlement가 있으면 새 기간을 `max(now,currentEndsAt)` 뒤에 붙인다. 클라이언트가 임의 duration·startsAt·endsAt을 정하지 않는다.
- 변경 파일: 설명 기록을 위한 `docs/codex/WORKLOG.md`만 갱신했다. 계약 결정서·애플리케이션·Store Console·Jira는 변경하지 않았다.
- 테스트 결과: 코드 변경이 없는 설명 작업이라 테스트는 재실행하지 않았다. 문서 diff 형식만 확인한다.
- 예상 밖 변경: 없음.
- 다음 작업: Store native fixed-term mapping과 account binding/restore 안을 사용자 승인하면 fixed-term entitlement schema, product catalog와 purchase sync API 선택으로 넘어간다.

## 2026-09-05 — C9 fixed-term Store/account binding 계약 확정

<!-- codex-turn:approve-c9-fixed-term-store-account-contract -->

- 날짜·브랜치: 2026-09-05, Billing `develop@7138810`. Jira 변경은 수행하지 않았다.
- 작업 목표: 사용자가 승인한 4번 권장안을 결제 계약 단일 기준에 반영한다.
- 결정: Apple Non-Renewing Subscription, Google prepaid subscription base plan을 Billing `PREMIUM_1D/3D/7D/14D/30D`에 exact mapping한다. 실제 provider ID·판매 국가·가격과 5개 기간 Console 지원은 출시 준비에서 확인하며 임의 consumable fallback은 금지한다.
- account binding: Billing 발급 lowercase UUID v4 `purchaseAccountRefId`를 Apple `appAccountToken`과 Google obfuscated account identifier에 사용하고 current canonical user와 내부 매핑한다. phone/email/device/raw userId는 Store 결속 값으로 사용하지 않는다.
- 검증·복원: server-verified product/account/purchase만 entitlement를 만들고 provider transaction/event unique로 중복을 수렴한다. Billing 원장 조회, client Store purchase query, server notification과 reconciliation을 복원 경로로 사용하며 phone rejoin은 유료 권리 이전 증거가 아니다.
- supersede: 2026-08-24 credit/3일 pass C9-A와 C10 credit 만료는 역사적 초안으로 표시했다. public API/auth·lifecycle·환불·notification·reconciliation은 아직 승인하지 않았다.
- 변경 파일: `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 문서 계약 변경이므로 Gradle 테스트는 실행하지 않았고 `git diff --check`와 계약 문구 충돌 검색을 수행한다.
- 유지한 계약: 무료권 phone dedupe/3년 보존, 무료권 보존, Store 원문·credential·개인정보 비로깅, server verification·provider unique 불변식을 유지한다.
- 예상 밖 변경: 없음. 애플리케이션·Identity·Learning Core·Store Console·AWS·Jira·Git 이력은 변경하지 않았다.
- 다음 작업: 5번 앱→Billing public API와 사용자 JWT audience 선택지를 확정한다.

## 2026-09-05 — 기간제 무제한 결제 권장안 전체 승인·계약 정리

<!-- codex-turn:approve-fixed-term-payment-contract -->

- 날짜·브랜치: 2026-09-05, Billing `develop`. 결제 Jira는 아직 생성하지 않았고 Jira·Store Console·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: 사용자가 승인한 1·3·7·14·30일 고정 기간 무제한 상품과 C1-A/C2-A/C9-S1~S6 권장안을 결제 단일 기준으로 정리한다.
- 제품 결정: Apple Non-Renewing Subscription, Google prepaid subscription/base plan을 `PREMIUM_1D/3D/7D/14D/30D`에 매핑한다. 자동 갱신과 credit 차감은 사용하지 않고 각 권리는 UTC 24·72·168·336·720시간의 `SubscriptionEntitlement`를 만든다.
- 사용자·인증 결정: Billing 발급 lowercase UUID v4 `purchaseAccountRefId`로 Store purchase와 canonical user를 연결한다. 앱은 Billing public API를 직접 호출하고 Identity JWT에 `tosunsaeng-billing` audience와 `billing:read`·`billing:purchase` scope를 추가한다.
- lifecycle 결정: verified provider start를 기준으로 기간을 시작하고 active/scheduled timeline이 있으면 뒤에 이어 붙인다. ACTIVE paid를 무료권보다 먼저 사용하며 TrialClaim·무료 Grant·unit은 변경하지 않는다.
- 환불·복구 결정: verified refund/revoke는 append-only reversal과 신규 INITIAL 차단으로 반영하고 confirmed AttemptGroup은 완료시킨다. client sync, Apple Notification V2, Google RTDN·backend acknowledge와 periodic reconciliation은 provider transaction/event unique로 하나의 결과에 수렴한다.
- 변경 파일: `AGENTS.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/contracts/FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 애플리케이션 코드 변경이 없는 계약 문서 작업이므로 Gradle 테스트는 실행하지 않았다. `git diff --check`, 계약 충돌 검색과 변경 범위 확인을 수행한다.
- 유지한 계약: 무료권 phone dedupe·3년 보존, paid-first/free-preserve, server verification, provider unique, append-only 원장, raw Store 원문·credential·개인정보 비로깅, phone proof 유료권 이전 금지를 유지한다.
- 미확정·위험: 실제 Store product/basePlan ID·가격·판매 국가·기간 지원/review, exact public API DTO와 Mongo index, payment 법정 보존기간, reconciliation 주기·lookback·batch·quota는 ADR 또는 출시 준비에서 정해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드, Identity·Learning Core, provider 설정과 배포 파일은 변경하지 않았다.
- 다음 작업: 결제 ADR에서 public API·Mongo·provider adapter·보존·운영 계약을 고정하고 이를 vertical slice 구현 계획과 Jira 완료 조건으로 나눈다.

## 2026-09-05 — Store 식별자와 Billing 사용자 연결 역할 설명

<!-- codex-turn:explain-store-account-binding-boundary -->

- 날짜·브랜치: 2026-09-05, Billing `develop`. Jira·Store Console·Git 이력은 변경하지 않았다.
- 작업 목표: Store 값으로 유료 권리 owner를 직접 연결하지 않고 Billing 발급 `purchaseAccountRefId`를 사용하는 이유를 설명한다.
- 정리: product/basePlan ID는 상품 판정, provider transaction ID/purchase token은 구매 검증과 중복 방지에 사용한다. Store 계정은 토선생 사용자와 1:1이 아니므로 entitlement owner의 진실 공급원으로 사용하지 않는다.
- 연결 계약: 인증된 토선생 사용자에게 발급한 opaque UUID를 Apple `appAccountToken`과 Google obfuscated account identifier로 전달하고, 서버 검증 시 exact match해야 권리를 연결한다. 같은 Store 계정·기기·phone만으로 owner를 이전하지 않는다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 설명 문서만 변경했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 확인한다.
- 유지한 계약: Store server verification, provider transaction unique, 개인정보 최소화와 phone rejoin 유료권 이전 금지를 유지한다.
- 위험·미확인: provider별 exact account-reference validation field와 복원 오류 계약은 결제 ADR에서 고정해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 서비스는 변경하지 않았다.
- 다음 작업: 결제 ADR에서 product evidence, transaction identity와 entitlement owner binding을 서로 다른 필드·index·오류 계약으로 설계한다.

## 2026-09-05 — Apple 결제 계정과 토선생 로그인 계정 구분 보정

<!-- codex-turn:clarify-apple-store-account-vs-app-account -->

- 날짜·브랜치: 2026-09-05, Billing `develop`. Jira·Store·Git 이력은 변경하지 않았다.
- 작업 목표: 같은 Apple 계정이면 같은 토선생 계정으로 로그인되는지와 가족 계정 공유가 account binding의 핵심 문제인지 정확히 구분한다.
- 결론: 토선생이 Sign in with Apple 하나만을 유일한 계정키로 강제한다면 같은 Apple 로그인은 보통 같은 토선생 계정으로 수렴할 수 있다. 그러나 현재 토선생 Identity/phone lifecycle과 App Store Media & Purchases 계정은 별도 인증 context이므로 이를 일반 전제로 사용할 수 없다.
- 핵심 근거: Apple ID 자체를 결제 owner 식별자로 받는 것이 아니라 개발자가 `appAccountToken`으로 앱 계정 context를 transaction에 넣는다. 가족 공유는 가능한 오귀속 사례 중 하나이며 설계의 주된 근거는 provider 계정과 app 계정의 분리다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 설명 기록만 변경했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`만 수행한다.
- 유지한 계약: `purchaseAccountRefId` exact match, provider transaction 검증과 phone rejoin 유료권 이전 금지를 유지한다.
- 위험·미확인: 실제 앱 로그인 제공자 구성과 Sign in with Apple account-linking 정책은 Identity/API ADR에서 확인해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 시스템은 변경하지 않았다.
- 다음 작업: 결제 ADR에서 인증된 Billing user, Store transaction과 `purchaseAccountRefId`의 생성·회전·merge 규칙을 구체화한다.

## 2026-09-05 — 결제 ADR 작성 전 필수 결정사항 조사

<!-- codex-turn:payment-adr-required-decisions-audit -->

- 날짜·브랜치: 2026-09-05, Billing `develop`. Jira·외부 인프라·Git 이력은 변경하지 않았다.
- 작업 목표: 승인된 fixed-term 결제 계약을 exact ADR·구현 계획으로 만들기 전에 사용자가 직접 확정해야 할 제품·비용·보존 정책을 현재 Billing·Identity 구현과 대조한다.
- 확인 사실: Billing은 internal Lattice-only 단일 SecurityFilterChain과 Mongo schema v4 exact initializer를 사용하고 public ingress/JWT verifier/payment aggregate는 아직 없다. Identity Access Token은 현재 audience 단일 문자열과 공통 default scope를 사용하며 Guest와 MEMBER 모두 같은 issuer 경로에서 발급된다.
- 필수 결정: public ingress, Guest 구매 허용, account reference 수명, stacked timeline 환불 재배치, payment 법정·운영 보존기간이다. 실제 Store ID·가격·판매 국가는 ADR 설정 slot만 정의하고 출시 준비 때 입력할 수 있다.
- 권장안: 기존 public ALB에 Billing 전용 host/path allowlist와 별도 target group을 추가하고 internal은 Lattice로 유지한다. 구매는 ACTIVE MEMBER만, `purchaseAccountRefId`는 환경별 사용자당 stable UUID, 환불된 entitlement slot은 제거하고 후속 verified 기간을 즉시 앞으로 당기며 정규화 결제·ledger는 5년 보존한다.
- 운영 기본값 제안: PENDING/unacknowledged는 5분, ACTIVE/SCHEDULED는 6시간, 최근 terminal은 일 1회 bounded batch로 reconciliation하고 실제 quota 측정 뒤 설정값으로 조정한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 조사·문서 기록만 수행했으므로 Gradle 테스트는 실행하지 않았다. 최종 문서 형식은 `git diff --check`로 확인한다.
- 유지한 계약: fixed-term paid-first/free-preserve, Store server verification, internal Lattice SigV4와 public JWT 경계, phone rejoin 유료권 이전 금지를 유지한다.
- 위험·미확인: 현재 ALB의 listener/certificate/host ownership과 실제 public domain, Store quota, 국내 전자상거래 보존 의무의 최종 법률 검토는 운영 입력으로 남는다.
- 예상 밖 변경: 없음. 애플리케이션, Identity 코드와 AWS 리소스는 변경하지 않았다.
- 다음 작업: 사용자가 다섯 권장안을 확정하면 payment ADR과 vertical-slice PLAN을 작성하고 exact API DTO·Mongo schema/index·오류·배포 순서를 고정한다.

## 2026-09-05 — Fixed-term premium 전체 계약 사용자 설명

<!-- codex-turn:explain-fixed-term-payment-contract -->

- 날짜·브랜치: 2026-09-05, Billing `develop`. Jira·Store Console·Git 이력은 변경하지 않았다.
- 작업 목표: 사용자가 첨부한 fixed-term premium 계약을 제품 흐름, 객체 역할, 상태 전이, 환불·복구와 미확정 구현값으로 나눠 이해하기 쉽게 설명한다.
- 핵심 해석: Store 구매를 Billing이 검증·원자 저장한 뒤 `SubscriptionEntitlement` 기간 동안 횟수 차감 없이 시험 시작을 승인하고, ACTIVE paid를 우선 사용해 무료 1회권을 보존한다. 무제한은 동시 시험 제약을 없애는 의미가 아니다.
- 객체 구분: BenefitDefinition/offer는 상품 규칙, Purchase는 결제 증거, SubscriptionEntitlement는 기간 권리, ledger는 변경 감사, Reservation은 시험 시작 승인, AttemptGroup은 한 사용 건과 승인된 재응시 묶음이다.
- 장애·환불 해석: client sync, provider notification과 reconciliation은 같은 provider transaction으로 수렴한다. refund/revoke는 기록을 삭제하지 않고 reversal을 추가해 신규 시험만 차단하며 이미 confirm된 시험은 정합성을 위해 완료한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 설명 기록만 변경했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`만 수행한다.
- 유지한 계약: server verification, provider unique, paid-first/free-preserve, append-only ledger와 기존 AttemptGroup 정합성을 유지한다.
- 위험·미확인: Store ID·가격·판매 국가·기간 지원, exact API/Mongo, 법정 보존기간과 reconciliation 운영값은 아직 ADR·출시 입력으로 남아 있다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 서비스는 변경하지 않았다.
- 다음 작업: 사용자가 계약을 이해·승인한 상태에서 payment ADR과 vertical slice 구현 계획을 작성한다.

## 2026-09-05 — 결제 식별자 세 가지 역할의 쉬운 설명

<!-- codex-turn:explain-payment-identifiers-simply -->

- 날짜·브랜치: 2026-09-05, Billing `develop`. Jira·외부 시스템·Git 이력은 변경하지 않았다.
- 작업 목표: `productId/basePlanId`, `transactionId/purchaseToken`, `purchaseAccountRefId`의 차이를 비기술적인 표현으로 설명한다.
- 정리: 각각 상품표, 영수증 번호, 받는 토선생 회원 번호에 대응하며 세 값이 함께 있어야 상품 종류·중복 결제 반영·권리 소유자를 독립적으로 검증할 수 있다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 설명 기록만 변경했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: Store 서버 검증, provider transaction unique와 opaque account binding을 유지한다.
- 위험·미확인: 없음. exact wire field와 index는 결제 ADR에서 확정한다.
- 예상 밖 변경: 없음. 애플리케이션 코드는 변경하지 않았다.
- 다음 작업: 결제 ADR에서 세 식별자의 source of truth, 저장 형태와 unique index를 고정한다.

## 2026-09-05 — 결제 ADR 필수 결정사항 최종 정리

<!-- codex-turn:payment-adr-required-decisions-final -->

- 날짜·브랜치: 2026-09-05, Billing `develop`. Jira·AWS·Store·Git 이력은 변경하지 않았다.
- 작업 목표: payment ADR과 PLAN 생성 직전에 사용자 승인이 필요한 사항을 최종 정리한다.
- 확인 결과: 제품 기간·Store 유형·paid-first/free-preserve·기본 환불·notification 정책은 승인 완료됐다. 남은 사용자 결정은 public ingress, Guest 구매 여부, account reference 수명, stacked refund timeline, payment 보존기간이며 reconciliation 수치는 운영 기본값으로 승인 가능하다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 코드 변경이 없는 조사 작업이라 Gradle 테스트는 실행하지 않았고 `git diff --check`가 성공했다.
- 유지한 계약: internal Lattice, public JWT, provider 검증·멱등성, 무료권 보존과 개인정보 최소화를 유지한다.
- 위험·미확인: 기존 ALB의 정확한 공유 가능 여부와 도메인/certificate, Store quota와 보존기간의 최종 법률 검토는 배포 입력으로 확인해야 한다.
- 예상 밖 변경: 없음. Billing·Identity 애플리케이션 코드는 변경하지 않았다.
- 다음 작업: 사용자 승인 후 ADR과 vertical-slice PLAN을 작성한다.

## 2026-09-06 — 환불 뒤 진행 중 시험 이탈·replacement 정책 설명

<!-- codex-turn:refund-inflight-attempt-replacement-policy -->

- 날짜·브랜치: 2026-09-06, Billing `develop`. Jira·Store·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: 환불 뒤 이미 진행 중이던 시험에서 사용자가 나갔을 때 replacement와 새 INITIAL 시험의 허용 경계를 설명한다.
- 결정 해석: 환불 전 confirm된 AttemptGroup은 완료와 같은 group/mock의 새 replacement Session까지 허용한다. group 완료 뒤 신규 INITIAL은 revoked paid로 승인하지 않고 미사용 무료권이 있으면 무료로, 없으면 402로 거절한다.
- 추가 발견: refund가 reserve와 Session commit/confirm 사이에 도착하는 race의 선형화 지점은 기존 계약에 충분히 고정되지 않았다. reserve commit 전후를 경계로 하고 기존 Reservation은 5분 내 confirm/cancel/expiry 수렴, 이후 신규 reserve 차단을 권장한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 분석·설명 기록만 변경했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: 기존 AttemptGroup 정합성, replacement 무추가차감, paid refund 뒤 신규 사용 차단과 무료권 보존을 유지한다.
- 위험·미확인: refund/reserve/confirm 동시성의 exact CAS·event ordering과 committed Session 판정은 payment ADR에서 확정해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드는 변경하지 않았다.
- 다음 작업: payment ADR에서 reserve commit을 authorization 선형화 지점으로 확정할지 사용자 승인을 받은 뒤 상태 전이와 회귀 테스트를 설계한다.

## 2026-09-06 — 환불 뒤 replacement 허용의 악용 위험 재검토

<!-- codex-turn:refund-after-start-abuse-policy-review -->

- 날짜·브랜치: 2026-09-06, Billing `develop`. Jira·Store·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: 시험 Session 생성 뒤 환불받고 같은 AttemptGroup replacement로 사실상 무료 응시할 수 있는지 검토한다.
- 확인 결과: 기존 "confirm된 AttemptGroup은 replacement까지 허용" 문구는 실제 악용 경로를 만든다. provider-confirmed refund 뒤 신규 INITIAL만 막는 것으로는 이미 발급된 Learning Core Session과 replacement를 차단할 수 없다.
- 권장 정책: refund 확정 시 OPEN/RETAKE_AVAILABLE 진행과 replacement를 차단하고 GRADING만 terminal 수렴, COMPLETED history는 보존한다. 환불 요청 자체는 신뢰하지 않고 provider 검증 뒤 적용하며 무료권은 자동 소비·복원하지 않는다.
- 필요한 계약: Billing refund 상태를 Learning Core에 durable하게 투영해 답안 제출·채점·replacement를 fail-closed해야 한다. provider 승인 전에 완료된 디지털 서비스는 회수할 수 없으므로 지원 가능한 Store consumption evidence와 반복 refund-after-use 운영 탐지가 필요하다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 분석·정책 검토 기록만 변경했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 확인한다.
- 유지한 계약: Store 최종 상태를 환불 source of truth로 사용하고 append-only reversal, 무료권 보존, 완료 history 비삭제를 유지한다.
- 위험·미확인: Apple/Google별 consumption/void evidence API의 exact contract와 Learning Core revoke event wire, refund-reserve-submit 동시성은 ADR에서 확정해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드는 변경하지 않았다.
- 다음 작업: 사용자가 상태별 strict revoke 권장안을 승인하면 기존 C9-S3·fixed-term 요약을 보정하고 ADR의 refund state table과 cross-service 차단 계약을 작성한다.

## 2026-09-06 — Fixed-term premium 권장안 전체 최종 확정

<!-- codex-turn:finalize-fixed-term-payment-decisions -->

- 날짜·브랜치: 2026-09-06, Billing `develop`. 결제 Jira는 아직 생성하지 않았고 AWS·Store·Git 이력은 변경하지 않았다.
- 작업 목표: 공개 ingress, 구매 자격, account reference, stacked refund timeline, 보존, reconciliation과 refund abuse 보정을 지금까지 논의한 결제 최종 계약으로 통합한다.
- 확정 인프라·인증: 기존 public ALB에 Billing 전용 host/path allowlist와 별도 target group을 사용하고 internal은 Lattice-only로 유지한다. Guest는 `billing:read`, ACTIVE MEMBER만 `billing:purchase`를 받는다.
- 확정 소유·timeline: 사용자·환경별 stable lowercase UUID v4 `purchaseAccountRefId`를 사용한다. 중간 refund/revoke slot은 제거하고 후속 VERIFIED entitlement를 기존 sequence·duration대로 즉시 앞으로 재배치하며 모든 조정을 ledger에 남긴다.
- 확정 refund: provider 최종 검증 뒤 RESERVED를 종료하고 OPEN·RETAKE_AVAILABLE의 진행과 replacement를 차단한다. GRADING만 terminal 수렴시키고 COMPLETED history는 보존한다. Billing→Learning Core durable access-revocation이 실제 답안·제출·채점 차단을 담당한다.
- 확정 보존·운영: 정규화 payment/ledger 5년, event inbox 120일, backup 35일이다. reconciliation은 PENDING/unacknowledged 5분, ACTIVE/SCHEDULED 6시간, 최근 90일 terminal 일 1회와 기본 100건 batch다.
- 변경 파일: `AGENTS.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/contracts/FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 애플리케이션 코드 변경이 없는 계약 문서 정합화 작업이므로 Gradle 테스트는 실행하지 않았다. `git diff --check`와 계약 충돌 검색을 수행한다.
- 유지한 계약: 1·3·7·14·30일 fixed-term, Store server verification, paid-first/free-preserve, append-only 원장, raw Store payload 비저장과 phone rejoin 유료권 이전 금지를 유지한다.
- 위험·미확인: 실제 ALB listener/certificate/SG, Store ID·가격·판매국가·기간 지원, exact revoke event/API DTO/Mongo index와 관할 법령의 5년 초과 의무는 ADR·출시 gate에서 확인해야 한다.
- 예상 밖 변경: 없음. Billing·Identity·Learning Core 애플리케이션과 외부 시스템은 변경하지 않았다.
- 다음 작업: ADR-004에서 exact public API·Mongo schema/index·provider adapter·refund event wire와 배포 migration을 고정하고 PLAN-007에서 vertical slice와 테스트 gate를 나눈다.

## 2026-09-06 — 결제 외부 신청·Console 준비사항 정리

<!-- codex-turn:payment-external-enrollment-checklist -->

- 날짜·브랜치: 2026-09-06, Billing `develop`. Jira·Apple/Google/AWS Console·Git 이력은 변경하지 않았다.
- 작업 목표: Store product/basePlan ID처럼 사용자가 외부 권한으로 신청·생성·승인해야 하는 항목과 코드가 자동 처리하는 값을 구분한다.
- Apple 준비: Developer/App Store Connect 권한, 유료 앱 계약·세금·은행, app/bundle, Non-Renewing Subscription 5개와 가격·국가·metadata, IAP server key, Notification V2 URL과 sandbox tester가 필요하다.
- Google 준비: Play developer·merchant profile, app/package, subscription+prepaid base plan 5개, 가격·국가, Android Publisher API service account 최소 권한, RTDN Pub/Sub와 license tester/internal track이 필요하다.
- AWS·보안 준비: public DNS/ACM/ALB rule·target group·SG, provider callback과 환경별 Secrets Manager 등록이 필요하다. 실제 private key·credential은 문서·채팅·Jira·Git에 기록하지 않는다.
- 구분: `purchaseAccountRefId`는 Billing, transaction ID/purchase token은 Store가 자동 발급한다. JWT audience/scope, API/Mongo/refund event는 개발 범위이고 product ID·가격·판매 국가·계약/review 정보는 사용자 또는 Console 권한자의 외부 작업이다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 외부 준비사항 분석과 문서 기록만 수행했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: fixed-term 5개 상품, 환경 분리, Secret 비저장, ACTIVE MEMBER purchase와 provider server verification을 유지한다.
- 위험·미확인: 현재 Apple/Google 유료 계약·merchant profile·app record·Cloud project·기존 ALB/certificate 준비 여부는 실제 Console inventory 전까지 미확인이다.
- 예상 밖 변경: 없음. 애플리케이션과 외부 리소스는 변경하지 않았다.
- 다음 작업: ADR-004에서 product/basePlan naming slot과 external prerequisite gate를 고정한 뒤 사용자가 sandbox Console 값을 준비한다.

## 2026-09-07 — Apple 결제 외부 준비 착수

<!-- codex-turn:apple-payment-external-setup-start -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Apple Console·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: fixed-term premium 결제의 외부 준비를 Apple부터 진행하기 위한 선행 조건과 순서를 확정한다.
- 확인 결과: 저장소에는 Apple Developer Program, App Store Connect 앱, Paid Applications Agreement·세금·은행 정보의 실제 완료 상태가 기록되어 있지 않다. 따라서 product ID 생성 전에 이 세 상태를 먼저 확인해야 한다.
- 진행 순서: 계정·계약 확인 → Non-Renewing Subscription의 실제 1·3·7·14·30일 지원 여부 확인 → ADR-004 naming/mapping 확정 → product/가격/국가/metadata 생성 → server key·Notification V2·sandbox tester 구성 순서다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 애플리케이션 코드 변경이 없는 준비사항 기록이므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: Apple Non-Renewing Subscription 우선안, Store server verification, 실제 credential 비저장, ACTIVE MEMBER purchase와 다섯 내부 offer를 유지한다.
- 위험·미확인: Apple Console이 다섯 exact 기간을 모두 지원하는지와 계정·계약·app record의 현재 상태가 미확인이다. 미지원 기간이 있으면 product ID 생성 전에 Store mapping을 재검토해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 Apple 리소스는 변경하지 않았다.
- 다음 작업: 사용자에게 기존 Apple Developer Program 및 App Store Connect 앱 등록 여부를 확인받고 Console 선행 조건부터 점검한다.

## 2026-09-07 — 기존 App Store 배포로 Apple 선행 조건 일부 확인

<!-- codex-turn:apple-existing-app-readiness -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Apple Console·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: 기존 App Store 배포가 결제 준비 항목 중 무엇을 충족하는지 구분한다.
- 확인 결과: 이미 App Store에 배포된 앱이면 Developer Program과 App Store Connect app/bundle record는 존재한다고 볼 수 있다. 그러나 무료 앱 배포만으로 Paid Applications Agreement, 세금과 은행 정보의 활성 상태까지 보장되지는 않는다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 설명·상태 기록만 변경했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: product ID 생성 전 Store 기간 지원과 naming을 확인하며 실제 credential은 저장소에 기록하지 않는다.
- 위험·미확인: App Store Connect의 유료 앱 계약·세금·은행 상태와 Non-Renewing Subscription의 exact 기간 지원은 아직 확인되지 않았다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 Apple 외부 리소스는 변경하지 않았다.
- 다음 작업: App Store Connect의 Agreements/Tax/Banking 상태를 확인한 뒤 상품 기간 지원을 점검한다.

## 2026-09-07 — Apple 유료 계약·세금·은행 공식 절차 확인

<!-- codex-turn:apple-paid-agreement-tax-banking-guide -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Apple Console·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: 사용자가 아직 수행하지 않은 Apple Paid Apps Agreement, 세금 정보와 은행 정보 등록 방법을 Apple 공식 안내 기준으로 정리한다.
- 확인 결과: Paid Apps Agreement는 Account Holder만 `Business > Agreements`에서 동의할 수 있고 동의 후 취소할 수 없다. 세금·은행 정보는 Account Holder/Admin/Finance가 입력할 수 있으나 은행 변경은 Account Holder 승인이 필요할 수 있다.
- 한국 세금 입력: 모든 개발자에게 미국 세금 양식이 필요하고 미국 외 계정에는 W-8 계열 질문 흐름이 제시된다. 한국 기반 개발자는 사업자등록번호+최근 90일 이내 영문 사업자등록증명 또는 비영리 고유번호+최근 90일 이내 영문 증명이 추가 요구된다.
- 은행 입력: Paid Apps Agreement와 필수 tax form 제출이 선행되며 법적 주체와 동일한 계좌의 국가/은행 식별정보/계좌번호/통화/명의자 정보를 등록한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 외부 절차 조사와 문서 기록만 수행했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: 실제 금융·세금·credential 정보는 채팅·Jira·Git에 남기지 않고 사용자가 App Store Connect에 직접 입력한다.
- 위험·미확인: Apple 계정의 법적 주체가 개인/법인 중 무엇인지, 사업자등록번호와 영문 증명 보유 여부, 실제 Console의 현재 계약 상태는 확인되지 않았다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 Apple 리소스는 변경하지 않았다.
- 다음 작업: 사용자가 Account Holder 권한과 한국 사업자 서류 준비 여부를 확인한 뒤 Paid Apps Agreement를 직접 검토·동의한다.

## 2026-09-07 — App Store Connect Agreements 메뉴 미노출 안내

<!-- codex-turn:apple-agreements-menu-troubleshooting -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Apple Console·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: App Store Connect에서 Agreements 메뉴가 보이지 않는 상황의 경로와 우선 확인사항을 설명한다.
- 확인 결과: Paid Apps Agreement는 `Business > Agreements`에 위치한다. 메뉴 미노출 시 현재 계정 role과 선택된 developer team을 먼저 확인하며 계약 체결은 Account Holder만 가능하다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 설명·기록만 변경했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: 상품 생성 전 Paid Apps Agreement·tax·banking 준비와 credential 비저장을 유지한다.
- 위험·미확인: 사용자가 현재 보고 있는 App Store Connect 화면, 계정 role과 선택된 팀은 아직 확인되지 않았다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 Apple 외부 리소스는 변경하지 않았다.
- 다음 작업: 화면에 `Business`가 있는지, 현재 계정 role이 Account Holder인지 확인한다.

## 2026-09-07 — Apple 한국어 계약 메뉴명 확인

<!-- codex-turn:apple-korean-agreement-labels -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Apple Console·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: 한국어 App Store Connect를 사용하는 사용자에게 영문 `Agreements`의 실제 한국어 메뉴명을 안내한다.
- 확인 결과: Apple 한국어 공식 도움말의 경로는 `비즈니스 > 계약 > 유료 앱 > 약관 보기 및 동의하기`이며 마지막에 `동의`를 선택한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 공식 도움말 확인과 문서 기록만 수행했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 확인한다.
- 유지한 계약: 계약 동의는 계정 소유자가 직접 검토하며 실제 계정·세금·은행 정보는 저장소나 채팅에 기록하지 않는다.
- 위험·미확인: 실제 사용자 화면에서 `계약` 탭과 `유료 앱` 행이 노출되는지는 아직 확인되지 않았다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 Apple 외부 리소스는 변경하지 않았다.
- 다음 작업: 사용자가 `계약` 탭에서 `유료 앱` 행의 상태를 확인한다.

## 2026-09-07 — Apple 이름 확인 문서 안내

<!-- codex-turn:apple-legal-name-verification-document -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Apple Console·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: Paid Apps 진행 중 표시된 한국어 `이름 확인 문서` 요구의 의미와 안전한 제출 기준을 설명한다.
- 확인 결과: 이는 Apple 계정의 법적 이름과 공식 문서의 이름을 확인하는 단계다. 개인사업자는 사업자등록증/사업자등록증명, 법인은 사업자등록증명 또는 법인등기사항증명서처럼 법적 이름과 발급기관이 명확한 최신 문서를 우선한다.
- 언어 구분: 이 업로드에서 `문서 언어: 한국어`를 선택했다면 한국어 원문을 제출한다. 이후 한국 tax form이 요구하는 최근 90일 이내 영문 사업자등록증명은 별도 요구사항이다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 공식 Apple 조직 확인 안내 검토와 기록만 수행했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 확인한다.
- 유지한 계약: 세금·사업자·계정 문서는 채팅·Jira·Git에 올리지 않고 Apple 화면에 사용자가 직접 제출한다.
- 위험·미확인: 현재 Apple 계정이 개인/개인사업자/법인 중 무엇인지와 화면에 표시된 확인 대상 이름이 확인되지 않아 한 문서를 단정할 수 없다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 Apple 외부 리소스는 변경하지 않았다.
- 다음 작업: 화면의 확인 대상 이름과 계정 유형을 기준으로 업로드할 문서를 선택한다.

## 2026-09-07 — Apple 주소 확인 문서 안내

<!-- codex-turn:apple-legal-address-verification-document -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Apple Console·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: Paid Apps 법적 주체 확인 중 표시된 `주소 확인 문서`의 선택과 일치 조건을 설명한다.
- 확인 결과: 현재 사업장/본점 주소가 정확히 적힌 사업자등록증·사업자등록증명 또는 법인등기사항증명서를 사용한다. 이름 확인에 쓴 문서가 주소까지 포함하고 입력값과 일치하면 동일 문서를 다시 사용할 수 있다.
- 불일치 처리: 문서 파일을 수정하지 않고 Apple 입력 주소를 공식 문서 표기와 맞추거나 사업자/법인 주소 변경을 먼저 처리한다. 이전 주소 문서와 임의 가림·편집본은 사용하지 않는다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 외부 서류 안내와 기록만 변경했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 확인한다.
- 유지한 계약: 개인·사업자 문서는 채팅·Jira·Git에 올리지 않고 Apple 검증 화면에 사용자가 직접 제출한다.
- 위험·미확인: 사용자가 Apple에 입력한 주소와 공식 문서 주소가 실제로 일치하는지는 확인하지 않았다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 Apple 외부 리소스는 변경하지 않았다.
- 다음 작업: 입력 주소와 제출 문서의 전체 주소를 대조한 뒤 사용자가 직접 업로드한다.

## 2026-09-07 — Apple 디지털 서비스법 심사 대기 확인

<!-- codex-turn:apple-dsa-compliance-review-pending -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Apple Console·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: Apple Business 규정 준수의 디지털 서비스법 상태가 `심사 중`일 때 필요한 후속 조치를 설명한다.
- 확인 결과: 27개 국가/지역 대상 정보가 2026-09-07에 갱신되고 `심사 중`이므로 제출은 완료됐으며 Apple 검토를 기다리는 단계다. 추가 요청 전에는 재제출하지 않는다.
- 운영 확인: Account Holder 이메일과 Business 규정 준수 화면에서 `조치 필요`, 추가 문서 요청 또는 거절 상태로 바뀌는지만 확인한다. 다른 tax/banking 메뉴가 열려 있다면 민감정보는 사용자가 직접 입력해 병행할 수 있다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 외부 상태 해석과 기록만 변경했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 확인한다.
- 유지한 계약: Apple 외부 심사를 코드 완료로 간주하지 않고 product 생성 전제와 별도 gate로 관리한다.
- 위험·미확인: Apple의 실제 승인 시점과 추가 자료 요청 여부는 통제할 수 없다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 Apple 외부 리소스는 변경하지 않았다.
- 다음 작업: DSA 심사와 병행해 Paid Apps의 tax/banking 입력 가능 여부를 확인하고, 승인 후 상품 기간 지원을 점검한다.

## 2026-09-07 — Google 출시 심사 중 결제 준비 병행 범위 확인

<!-- codex-turn:google-payment-setup-during-app-review -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Google Console·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: Google 앱이 최종 출시 심사 중인 상태에서 결제 준비를 미리 진행할 수 있는지 구분한다.
- 확인 결과: 앱 release 심사와 별개로 merchant/payment profile, Cloud project/API/service account, RTDN Pub/Sub, license tester와 internal test 준비를 병행할 수 있다. 실제 production 판매는 앱 승인, 결제 프로필 검증과 상품 활성 상태가 모두 충족돼야 한다.
- ID 생성 gate: `subscriptionId/basePlanId`는 naming과 1·3·7·14·30일 prepaid 지원 여부를 ADR-004/Play Console에서 확인한 뒤 생성하며 현재 임의 생성하지 않는다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 외부 준비 범위 설명과 문서 기록만 수행했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 확인한다.
- 유지한 계약: Google prepaid subscription 우선안, provider server verification, 실제 credential 비저장과 production gate를 유지한다.
- 위험·미확인: 사용자가 말한 최종 심사가 앱 release review인지 개발자/merchant verification인지, 현재 payment profile과 Play Billing artifact 준비 상태는 확인되지 않았다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 Google 외부 리소스는 변경하지 않았다.
- 다음 작업: Google 심사 유형을 확인하고 Play Console의 수익 창출 설정에서 결제 프로필 상태와 prepaid 기간 선택지를 점검한다.

## 2026-09-07 — Google prepaid 기간 제약 확인과 상품 생성 보류

<!-- codex-turn:google-prepaid-duration-contract-gap -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Google Console·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: Google 결제 설정 경로와 fixed-term 다섯 기간의 실제 prepaid 지원 여부를 공식 한국어 문서로 확인한다.
- 확인 결과: Google 선불 기본 요금제는 1일, 3일, 1주, 4주, 1개월, 2/3/4/6/8개월, 1년을 제공하며 2주/14일은 없다. 1개월도 고정 720시간인 내부 30일과 expiry 의미가 다를 수 있다.
- 계약 영향: `PREMIUM_14D` exact mapping이 불가능해졌고 `PREMIUM_30D`도 의미 확인이 필요하다. 승인된 자동 fallback 금지에 따라 subscription/basePlan ID 생성과 ADR-004 확정을 재승인 전까지 보류한다.
- 계속 가능한 준비: `설정 > 결제 프로필` 등록, Cloud project/API/service account, RTDN Pub/Sub, license tester와 internal test 기반 준비는 병행 가능하다.
- 변경 파일: `docs/contracts/FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 공식 Store 문서 조사와 계약 위험 기록만 수행했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 확인한다.
- 유지한 계약: provider ID 불변성, Store 서버 검증, 자동 fallback 금지, actual credential 비저장과 ACTIVE MEMBER 구매를 유지한다.
- 위험·미확인: Google 14일 처리 방식과 30일을 달력 1개월로 바꿀지 여부가 미확정이며 Apple exact 기간 전략과 함께 교차 Store 재검토가 필요하다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 Google 리소스는 변경하지 않았다.
- 다음 작업: 결제 프로필 준비를 진행하면서 Google 상품 구성을 혼합 상품형/기간 변경/14일 제외 중 재승인한다.

## 2026-09-07 — 과거 one-time 결정과 현재 Store 유형 재확인

<!-- codex-turn:audit-one-time-vs-provider-native-payment-decision -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Jira·Store·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: 사용자가 기억한 일회성 결제 결정과 현재 문서의 Google prepaid 계약이 왜 다른지 시간순으로 확인한다.
- 확인 결과: 2026-08-24 C9-A에는 모든 상품을 consumable/one-time product로 매핑하는 역사적 권장안이 있었다. 2026-09-05 사용자의 단순 구독제 선호와 C9-S1 승인으로 Apple Non-Renewing Subscription·Google prepaid subscription이 이를 명시적으로 supersede했다.
- 용어 구분: 현재 제품도 구매 건마다 한 번만 결제하고 자동 갱신되지 않지만, 이것은 결제 동작이며 Store의 `one-time product` 상품 유형과 동일한 의미가 아니다.
- 현재 영향: Google의 14일 prepaid 미지원과 1개월/고정 30일 차이 때문에 C9-S1 재검토가 필요하다. one-time 유형을 다시 선택하려면 사용자의 새 승인을 받아 계약을 보정한다.
- 변경 파일: `docs/contracts/FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 계약 이력 감사와 문서 명확화만 수행했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 확인한다.
- 유지한 계약: 새 승인 전 자동 fallback하지 않고 product/basePlan ID 생성을 보류하며 무료권 보존·provider 검증·멱등 원장은 유지한다.
- 위험·미확인: 사용자가 말한 `일회성`이 사용자 과금 동작인지 Store 상품 유형인지 당시 의도는 대화 문구만으로 완전히 단정할 수 없다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 Store 리소스는 변경하지 않았다.
- 다음 작업: Google 상품을 one-time product로 되돌릴지 사용자가 명시적으로 확정하면 C9-S1과 후속 ADR 범위를 수정한다.

## 2026-09-07 — Apple·Google consumable one-time fixed-term 상품 최종 승인

<!-- codex-turn:approve-both-stores-one-time-fixed-term-products -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. 결제 Jira는 아직 생성하지 않았고 Apple/Google Console·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: 사용자의 명시적 승인에 따라 Apple과 Google의 fixed-term 상품을 모두 재구매 가능한 일회성 Store 상품으로 통일하고 승인 계약을 정합화한다.
- 확정 Store 유형: Apple은 offer별 Consumable In-App Purchase 5개, Google은 offer별 consumable one-time product 5개를 사용한다. provider subscription/basePlan과 Store expiry를 사용하지 않는다.
- 기간·stacking: verified product ID를 Billing `PREMIUM_1D/3D/7D/14D/30D`와 exact mapping하고 Billing catalog가 24·72·168·336·720시간을 부여한다. 재구매는 기존 verified paid timeline 뒤에 이어 붙인다.
- 완료·복원: Purchase·SubscriptionEntitlement·ledger Transaction commit 뒤 Apple client는 finish, Google backend는 consume한다. consumable Store restore를 과거 권리 source로 사용하지 않고 Billing ledger/current entitlement가 복원을 담당하며 미완료 transaction은 같은 provider key로 다시 sync한다.
- 유지한 정책: ACTIVE MEMBER purchase, stable `purchaseAccountRefId`, provider server verification, transaction unique, paid-first/free-preserve, refund state table, timeline reflow, 5년/120일/35일 보존과 public ALB/internal Lattice 분리를 유지한다.
- 변경 파일: `AGENTS.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/contracts/FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 애플리케이션 코드가 아닌 계약 문서 변경이므로 Gradle 테스트는 실행하지 않았다. `git diff --check`와 active contract의 subscription/basePlan/acknowledge 충돌 검색을 수행한다.
- 위험·미확인: finish/consume 완료 뒤 provider refund/revoke 조회와 notification coverage, exact product ID naming, 가격·판매 국가·review metadata와 API/Mongo schema는 ADR-004에서 고정해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드, Identity·Learning Core, Store Console, Jira, AWS와 Git 이력은 변경하지 않았다.
- 다음 작업: ADR-004에서 one-time purchase sync/verify/finish/consume, provider event/reconciliation, exact public DTO와 Mongo index를 확정하고 PLAN-007을 작성한다.

## 2026-09-07 — 양 Store one-time 변경 영향 설명

<!-- codex-turn:explain-both-stores-one-time-change-impact -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Jira·Store·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: Apple/Google provider-native subscription에서 consumable one-time product로 변경하며 달라진 책임과 유지되는 제품 정책을 설명한다.
- 설명 요약: Store는 product·가격·transaction·refund 증명을 담당하고 Billing은 product ID mapping, 24·72·168·336·720시간, timeline stacking과 current entitlement 복원을 담당한다. Google basePlan/expiry/acknowledge는 product ID/카탈로그 기간/backend consume으로 대체된다.
- 유지 범위: ACTIVE MEMBER, purchaseAccountRefId, provider verification·unique, paid-first/free-preserve, refund 상태표·timeline reflow, 보존과 ingress 계약은 변경하지 않았다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 설명 기록만 변경했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 형식을 확인한다.
- 위험·미확인: consumable transaction의 notification/refund 재조회 범위와 finish/consume unknown result 수렴은 ADR-004에서 exact contract와 테스트로 고정해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 리소스는 변경하지 않았다.
- 다음 작업: 실제 product ID 생성 전에 ADR-004에서 이름과 provider별 완료·복구 계약을 확정한다.

## 2026-09-07 — RevenueCat 도입 적합성 검토

<!-- codex-turn:assess-revenuecat-for-consumable-fixed-term-payments -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Jira·Store·RevenueCat·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: Apple·Google consumable one-time fixed-term 상품에 RevenueCat을 도입할 때 줄어드는 구현과 Billing에 남는 책임을 공식 계약 기준으로 구분한다.
- 확인 결과: RevenueCat은 Store별 구매 SDK, Offering/Paywall, webhook과 분석을 통합하지만 consumable의 지급·사용·만료는 관리하지 않는다. RevenueCat Entitlement에 consumable을 연결하면 만료 없는 unlock으로 표현되므로 24·72·168·336·720시간 권리의 authoritative source로 사용할 수 없다.
- 계약 영향: 토선생의 product mapping, timeline stacking, 무료권 보존, Reservation, refund/revoke 차단, append-only ledger와 reconciliation은 Billing에 남는다. 표준 SDK가 transaction completion을 기본 수행하므로 현재 `Billing commit 후 finish/consume` 순서를 유지하려면 app-completed/observer 방식이 필요하고, RevenueCat 완료를 채택하려면 ADR-004에서 실패·복구 계약을 재승인해야 한다.
- 보안·계정: 도입 시 RevenueCat App User ID에는 raw userId가 아니라 stable `purchaseAccountRefId`를 사용하고 구매 전 identified login을 강제한다. anonymous alias와 기본 restore transfer가 토선생 owner 정책을 우회하지 않도록 transfer behavior를 별도 확정해야 한다.
- 운영: webhook HMAC/raw-body verification, event ID 멱등성, 최대 5회 retry 이후 reconciliation이 필요하다. one-time refund coverage를 위해 Store Platform Server Notifications 설정도 없어지지 않는다.
- 비용: 2026-09-07 공개 Pro 가격은 월 tracked revenue 2,500 USD까지 무료, 이후 1%다. 정확한 one-time 과금 산정은 도입 시 최신 약관으로 재확인한다.
- 결론: 현재는 직접 Store 연동과 Billing 검증을 기본 권장한다. 원격 Paywall·A/B 테스트·통합 분석의 가치가 추가 비용과 vendor dependency를 넘는 시점에 RevenueCat을 Billing 대체가 아닌 Store adapter/event source로 재검토한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 공식 RevenueCat 문서 조사와 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: 양 Store consumable one-time product, Billing catalog duration, ACTIVE MEMBER, stable `purchaseAccountRefId`, provider 검증·unique, paid-first/free-preserve, refund timeline과 보존 정책은 변경하지 않았다.
- 위험·미확인: RevenueCat을 실제 채택할 경우 standard SDK 대 app-completed 모드, restore transfer, webhook/API source of truth, 장애 시 구매 완료 UX와 one-time 매출 비용 산정은 ADR-004 전에 사용자 승인이 필요하다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 리소스는 변경하지 않았다.
- 다음 작업: RevenueCat 미도입을 유지하면 기존 ADR-004를 직접 Store adapter 기준으로 작성하고, 도입하려면 먼저 별도 선택 계약을 확정한다.

## 2026-09-07 — RevenueCat 도입 편익 설명

<!-- codex-turn:explain-revenuecat-adoption-benefits -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Jira·Store·RevenueCat·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: RevenueCat을 실제 사용했을 때 토선생 팀이 얻는 구체적인 개발·운영·성장 편익을 설명한다.
- 설명 요약: 모바일은 Apple/Google별 상품 조회·구매·오류·복원 처리를 공통 SDK와 Offering으로 줄이고, 운영은 Paywall·상품 노출 원격 변경과 통합 매출/전환 화면을 얻으며, 서버는 서로 다른 Store notification을 공통 RevenueCat webhook으로 받을 수 있다.
- 한계: 이는 반복적인 Store 연동 plumbing을 줄이는 효과다. consumable 기간 계산, stacking, 무료권 보존, Reservation, 환불 차단, ledger와 webhook 누락 복구는 계속 Billing 책임이다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 설명 기록만 변경했으므로 Gradle 테스트는 실행하지 않았고 `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: 양 Store consumable one-time 상품과 현재 Billing 권리·환불 계약을 변경하지 않았다.
- 위험·미확인: 실제 절감 폭은 모바일의 기존 IAP 구현 정도, Paywall 실험 필요성과 RevenueCat standard/app-completed 선택에 따라 달라진다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 리소스는 변경하지 않았다.
- 다음 작업: RevenueCat 도입 여부를 확정한 뒤 ADR-004의 provider adapter와 transaction completion 순서를 해당 방식으로 고정한다.

## 2026-09-07 — RevenueCat 결제 연동 채택

<!-- codex-turn:approve-revenuecat-payment-integration -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. 결제 Jira는 아직 생성하지 않았고 RevenueCat·Store Console·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: 사용자의 명시적 승인에 따라 RevenueCat을 Apple/Google fixed-term 결제의 표준 연동 계층으로 확정하고 기존 직접 Store 완료 계약을 정합화한다.
- 확정 구조: 앱은 RevenueCat 표준 SDK·Offering/Package로 consumable one-time product를 구매한다. RevenueCat은 Store transaction completion·정규화와 HMAC webhook/API를 제공하고 Billing은 기간형 entitlement·stacking·무료권·Reservation·refund ledger의 source of truth를 유지한다.
- 완료 순서 변경: RevenueCat 표준 SDK가 Apple finish와 Google consumable completion을 담당하며 기존 `Billing commit 후 finish/consume` 순서를 supersede한다. Store 완료 후 Billing 반영이 늦으면 callback으로 fail-open하지 않고 `PENDING`에서 webhook/API reconciliation으로 수렴한다.
- 사용자 연결: RevenueCat custom App User ID에는 stable `purchaseAccountRefId`를 사용한다. 익명 구매와 RevenueCat alias/restore만을 근거로 한 다른 토선생 계정으로의 purchase·entitlement 자동 이전을 금지한다.
- 이벤트·복구: Store notifications는 RevenueCat에 연결하고 Billing은 환경별 raw-body HMAC webhook을 수신한다. RevenueCat event ID와 Store transaction ID를 멱등 경계로 사용하고 webhook retry 종료에 대비해 scheduled RevenueCat API reconciliation을 유지한다.
- 권리 경계: consumable RevenueCat Entitlement는 만료 없이 보이므로 fixed-term 권리 판정에 사용하지 않는다. Billing catalog의 24·72·168·336·720시간, timeline reflow, paid-first/free-preserve와 refund 상태표는 변경하지 않았다.
- 변경 파일: `AGENTS.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/contracts/FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 애플리케이션 코드가 아닌 계약·작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`와 direct Store completion 충돌 검색으로 검증한다.
- 위험·미확인: RevenueCat restore/transfer behavior의 exact 설정, webhook public route·HMAC replay window, client sync DTO, API 조회 endpoint/rate limit, environment별 project/app·Offering/Package와 Store notification 연결은 ADR-004에서 확정해야 한다.
- 예상 밖 변경: 없음. 기존 작업 트리의 결제 계약 문서 변경 위에 RevenueCat 결정만 반영했으며 애플리케이션 코드·Identity·Learning Core·외부 리소스는 변경하지 않았다.
- 다음 작업: ADR-004를 RevenueCat 기준으로 작성해 exact public API, webhook decoder/inbox, Mongo schema/index, reconciliation, account restore와 장애 UX를 고정한다.

## 2026-09-07 — Google Play 일회성 제품 등록 절차 확인

<!-- codex-turn:prepare-google-play-one-time-products-for-revenuecat -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Google Play Console·RevenueCat·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: RevenueCat 연동 전에 Google Play에 fixed-term 상품 5개를 등록하기 위한 최신 Console 객체 모델, 입력값과 순서를 확인한다.
- 확인 결과: Google 최신 일회성 제품은 제품마다 하나 이상의 구매 옵션이 필수다. 토선생은 Store 기간을 사용하지 않으므로 `대여`가 아니라 `구입` 옵션을 사용하고, Billing catalog가 24·72·168·336·720시간을 부여한다.
- 권장 식별자: product ID `premium_1d`, `premium_3d`, `premium_7d`, `premium_14d`, `premium_30d`; 각 product의 purchase option ID `standard`. 실제 생성 전 사용자 승인값으로 고정해야 하며 삭제된 product ID는 재사용할 수 없다.
- 초기 설정: 사용자 표시 이름/설명, 세금·규정 준수, 구매 옵션 `구입`, 디지털 `서비스`, 단일 수량, 지역별 가격·판매 국가를 설정하고 구매 옵션을 활성화한다. 선주문·할인·다중 수량은 범위 밖이다.
- RevenueCat 후속: Google app package와 service credential을 연결한 뒤 제품을 import하고 Offering/Package에 연결한다. consumable을 RevenueCat Entitlement에 연결해 기간 권리를 판정하지 않는다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 공식 Google/RevenueCat 문서 확인과 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: 양 Store consumable one-time, RevenueCat 표준 연동, Billing catalog duration·ledger·무료권·환불 계약을 변경하지 않았다.
- 위험·미확인: 사용자의 Play Console에 최신 객체 모델/EAP가 노출되는지, 실제 package name, 5개 가격·판매 국가·세금 분류와 product ID 최종 승인은 아직 확인되지 않았다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 리소스는 변경하지 않았다.
- 다음 작업: product ID·가격을 사용자 승인한 뒤 Play Console에서 5개 제품/구매 옵션을 만들고 RevenueCat에 import한다.

## 2026-09-07 — Google Play 결제 프로필 생성 안내

<!-- codex-turn:guide-google-play-payments-profile-creation -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Google Payments·Play Console·RevenueCat·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: 사용자가 Google Play 일회성 제품 생성 전에 요구받은 결제 프로필을 안전하게 만들 수 있도록 입력 항목과 주의사항을 설명한다.
- 확인 결과: 경로는 `설정 > 결제 프로필`이며 프로필 국가, 법적 이름·주소, 기본 연락처, 공개 판매자 웹사이트·카테고리·지원 이메일·카드 명세서 표시명을 입력한다. 국가는 생성 후 변경할 수 없고 거래 은행 계좌 국가와 같아야 한다.
- 입력 기준: 개인/사업자·조직 유형과 법적 정보는 Play 개발자 계정, 공식 사업자/주소 문서, 세금 정보와 은행 예금주에 일치시킨다. 사업자명은 고객 영수증에, 공개 판매자 정보와 명세서명은 구매자에게 표시될 수 있다.
- 후속 절차: 프로필 제출 뒤 세금 정보, 은행 계좌 등록·인증과 Google이 요구하는 판매자 신원 확인을 완료해야 실제 판매대금 수취가 가능하다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 공식 Google 문서 확인과 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: Google consumable one-time product, RevenueCat 연동과 credential/개인정보 비저장 경계를 변경하지 않았다.
- 위험·미확인: 사용자의 개발자 계정 유형, 기존 payments profile, 실제 법적 주체·공식 주소·은행 국가와 현재 화면의 추가 검증 요구는 확인하지 않았다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 리소스는 변경하지 않았다.
- 다음 작업: 사용자가 민감정보를 공유하지 않은 채 현재 화면의 필드명이나 오류 문구를 알려주면 해당 입력 기준을 확인하고, 제출 후 세금·은행 설정으로 진행한다.

## 2026-09-07 — Google Play 결제 프로필 생성 후 상품 생성 차단 확인

<!-- codex-turn:check-google-play-product-creation-after-payments-profile -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Google Payments·Play Console·RevenueCat·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: 사용자가 첨부한 결제 프로필 화면을 확인하고 아직 일회성 제품을 만들 수 없는 원인과 다음 조치를 안내한다.
- 확인 결과: 결제 프로필은 연결된 것으로 보이며 수입 0원·거래 없음은 정상 초기 상태다. 정산 은행 계좌 미등록과 15% 수수료 프로그램 미등록은 상품 메뉴 잠김의 직접 원인으로 단정할 수 없다.
- 다음 조치: 현재 Play artifact에 Billing 기능이 없으면 모바일 앱에 기술 스택별 RevenueCat SDK를 설치하고 Billing permission이 포함된 새 AAB를 내부 테스트 트랙에 업로드한다. Play 처리가 끝난 뒤 `Play를 통한 수익 창출 > 제품 > 일회성 제품`을 다시 확인한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 화면 분석과 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: Google consumable one-time product 5개, RevenueCat 표준 SDK, Billing의 기간·무료권·원장 source-of-truth 경계를 변경하지 않았다.
- 위험·미확인: 모바일 앱 기술 스택, 현재 업로드 artifact의 Billing permission, Play Console 차단 문구와 앱/계정 검증 상태는 아직 확인되지 않았다.
- 예상 밖 변경: 없음. 애플리케이션 코드, 외부 Console과 기존 사용자 변경은 건드리지 않았다.
- 다음 작업: 앱 기술 스택을 확인해 RevenueCat SDK 설치와 내부 테스트 AAB 업로드를 진행한 뒤 제품 5개를 생성한다.

## 2026-09-07 — Google Payments 판매자 계정 차단 문구 보정

<!-- codex-turn:clarify-google-payments-merchant-account-blocker -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Google Payments·Play Console·RevenueCat·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: 일회성 제품 페이지에 표시된 `Google Payments 판매자 계정을 설정해야 함` 문구를 기준으로 직접 차단 원인과 다음 확인 순서를 보정한다.
- 확인 결과: 정확한 문구가 확인됐으므로 현재 직접 차단 원인은 Billing permission 추정이 아니라 Play 개발자 계정에 판매자 계정 설정 완료·연결이 아직 반영되지 않은 상태다. 결제 프로필 생성과 판매자 계정의 Play 연결/검증 완료를 구분해야 한다.
- 안내 내용: 결제 프로필의 미완료 경고·인증 상태, 상품 페이지의 판매자 계정 연결 동작, 동일 Google/Play 개발자 계정 여부와 신규 설정 반영 지연을 확인한다. 은행 계좌는 정산을 위해 등록하되 해당 문구의 유일한 원인으로 단정하지 않는다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 외부 Console 문구 분석과 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: Google consumable one-time 제품과 RevenueCat/Billing 책임 경계는 변경하지 않았다.
- 위험·미확인: Google이 현재 요구하는 정확한 미완료 필드와 판매자 계정 검토 상태는 Console 상세 화면을 보기 전에는 특정할 수 없다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 계정 상태는 변경하지 않았다.
- 다음 작업: 판매자 계정 설정/검증 상태 화면을 확인해 누락 항목을 완료한 뒤 제품 페이지 접근을 재시도한다.

## 2026-09-07 — RevenueCat 계정 준비와 백엔드 병행 순서 확정

<!-- codex-turn:sequence-revenuecat-account-and-backend-development -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. RevenueCat·Store Console·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: Google 정산 계좌 인증 대기 중 RevenueCat 계정을 먼저 만들어야 하는지와 백엔드 개발 가능 범위를 정리한다.
- 결론: RevenueCat 계정·project와 iOS/Android app 등록은 지금 진행하는 것이 좋지만 Store 제품이나 credential이 없어도 Billing backend의 provider abstraction, 원장·entitlement·webhook contract와 fake 기반 테스트는 개발할 수 있다.
- 보안·환경: 조직 이메일/MFA와 최소 권한을 사용한다. 실제 key·credential은 Git·채팅에 공유하지 않고, 별도 staging RevenueCat project는 별도 bundle/package가 존재할 때 ADR-004에서 확정한다.
- 계약 경계: consumable을 RevenueCat Entitlement에 연결하지 않고 custom App User ID에는 `purchaseAccountRefId`를 사용한다. Store 연결·제품 import·webhook 활성화는 exact ADR·PLAN 승인 후 수행한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 개발 순서와 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: RevenueCat은 연동 계층이고 Billing이 기간·stacking·무료권·Reservation·refund ledger의 source of truth라는 경계를 유지한다.
- 위험·미확인: 실제 iOS bundle ID, Android package name, staging 전용 앱 식별자, RevenueCat restore behavior·webhook 인증/API 계약은 ADR-004에서 확정해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 계정 상태는 변경하지 않았다.
- 다음 작업: RevenueCat 계정/project/app을 안전하게 생성하고, 병행해서 ADR-004와 PLAN-007을 작성·승인한 뒤 payment vertical slice 구현을 시작한다.

## 2026-09-07 — RevenueCat 프로젝트 생성 확인과 다음 설정 안내

<!-- codex-turn:confirm-revenuecat-project-and-next-setup -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. 사용자가 RevenueCat account/project를 생성했으며 Codex는 RevenueCat·Store·Jira·AWS·Git 상태를 변경하지 않았다.
- 작업 목표: 생성된 RevenueCat project 이후 지금 필요한 최소 Console 설정과 백엔드 전환 시점을 정리한다.
- 확인 결과: 공유 URL은 account-scoped이고 별도 브라우저 세션은 로그인 화면으로 이동해 project 내부를 읽지 못했다. 비밀번호·MFA·credential을 요청하거나 입력하지 않았다.
- 안내 내용: `Apps & providers`에서 실제 Android package name의 Google Play app을 먼저 등록한다. Store service credential, 제품 import, Offering/Package와 webhook은 판매자 계정·상품 및 ADR-004 준비 뒤 연결하며 consumable RevenueCat Entitlement는 만들지 않는다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 외부 설정 순서와 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: custom App User ID=`purchaseAccountRefId`, 익명 구매 금지, Billing source-of-truth와 실제 secret 비저장 원칙을 유지한다.
- 위험·미확인: 실제 project app 구성, Android package name, iOS bundle ID와 RevenueCat credential/restore 설정은 아직 확인되지 않았다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 서비스 설정은 변경하지 않았다.
- 다음 작업: RevenueCat Google Play app을 추가한 뒤 ADR-004를 작성해 exact public API, webhook/API adapter, Mongo schema/index와 실패 복구 계약을 승인한다.

## 2026-09-07 — RevenueCat iOS 앱과 In-App Purchase Key 설정 안내

<!-- codex-turn:guide-revenuecat-ios-app-iap-key-setup -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. RevenueCat·App Store Connect·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: RevenueCat의 New App Store app 화면에 필요한 Bundle ID, URL scheme, `.p8` key, Key/Issuer ID, Small Business와 legacy shared secret 입력 기준을 설명한다.
- 입력 기준: 앱 이름은 내부 표시명, Bundle ID는 Xcode/App Store Connect exact 값이다. Paywall preview URL scheme은 선택이며 앱 등록 없이 임의 값을 넣지 않는다.
- Apple credential: App Store Connect `Users and Access > Integrations > In-App Purchase`의 전용 key를 생성해 `.p8`를 RevenueCat에 직접 업로드하고 표시된 Key ID·Issuer ID를 입력한다. 일반 API/APNs key와 혼용하지 않는다.
- 보안: `.p8`와 shared secret은 Git·채팅·Jira·문서에 기록하지 않고 secret manager에 보관한다. 이 작업에서는 credential을 열람·복사·저장하지 않았다.
- 선택 항목: Small Business 날짜는 실제 효력 발생일만 입력하고, legacy shared secret은 StoreKit 1 또는 지원 iOS 범위상 필요한 경우에만 구성한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 문서 안내만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: RevenueCat 표준 SDK와 Billing 권리 원장 경계, consumable one-time 제품 정책은 변경하지 않았다.
- 위험·미확인: 실제 Bundle ID, iOS deployment target, StoreKit mode, Apple key 생성 권한과 Small Business 승인 상태는 사용자 계정/App 프로젝트에서 확인해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 Console 상태는 변경하지 않았다.
- 다음 작업: 정확한 Bundle ID를 확인하고 Apple In-App Purchase Key를 생성·업로드해 iOS app 등록을 완료한 뒤 ADR-004를 작성한다.

## 2026-09-07 — RevenueCat App Store API·notification 설정 분류

<!-- codex-turn:classify-revenuecat-apple-api-notification-settings -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. RevenueCat·App Store Connect·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: RevenueCat iOS app의 추가 설정에서 App Store Connect API key, Apple notification, 환불·구독 옵션과 public identifier의 용도를 구분한다.
- key 구분: `AuthKey_*.p8`는 product import·가격 metadata용 App Store Connect API key이고 `SubscriptionKey_*.p8`는 transaction 검증용 In-App Purchase key다. 둘을 별도 생성·보관·업로드하며 실제 credential을 기록하지 않는다.
- notification 방향: Apple Server Notification v2는 RevenueCat으로 보내고 Billing은 RevenueCat webhook을 받는다. Apple raw notification forwarding URL은 현재 비워둬 중복 ingress를 만들지 않는다.
- 보류 설정: S2S-only 신규 구매 추적과 refund request handling은 account binding·개인정보·멱등 계약을 ADR-004에서 확정하기 전 OFF다. Retention Messaging과 Subscription Offer key는 consumable one-time 범위 밖이다.
- 식별자: 모바일 Public SDK key는 공개 설정값이지만 server secret과 구분한다. `app...` REST API Identifier는 RevenueCat app resource ID일 뿐 SDK key나 product ID가 아니다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 설정 분류와 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: RevenueCat이 Store 연동 계층이고 Billing이 권리·환불 원장의 source of truth라는 경계와 익명 구매 금지를 유지한다.
- 위험·미확인: App Store Connect API key의 실제 role/access 범위, notification v2 URL 반영과 RevenueCat webhook HMAC/API reconciliation은 ADR-004 및 sandbox에서 검증해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 설정은 변경하지 않았다.
- 다음 작업: API key를 최소 권한으로 연결하고 Apple notification v2를 RevenueCat으로 설정하되 나머지 보류 기능은 끈 상태에서 ADR-004를 작성한다.

## 2026-09-07 — Apple Server Notification URL 등록 위치 확인

<!-- codex-turn:confirm-apple-server-notification-url-registration -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. RevenueCat·App Store Connect·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: RevenueCat Apple Server Notification URL을 어디에 등록하는지와 forwarding URL과의 차이를 확인한다.
- 안내 내용: RevenueCat의 수신 URL을 App Store Connect 토선생 앱의 `앱 정보 > App Store 서버 알림` production·sandbox 항목에 Version 2로 등록한다. forwarding URL은 사용하지 않고 Billing 직접 URL도 Apple에 등록하지 않는다.
- 상태 해석: 저장 직후 `No notifications received`는 오류가 아니며 sandbox 또는 production Store event가 RevenueCat에 도착한 뒤 수신 상태가 갱신된다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 설정 확인과 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: `Apple → RevenueCat → Billing webhook` 단일 ingress 방향을 유지한다.
- 위험·미확인: 실제 URL 저장 성공과 sandbox event 수신은 외부 Console과 StoreKit 테스트에서 확인해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 설정은 변경하지 않았다.
- 다음 작업: production·sandbox v2 URL을 저장하고 이후 StoreKit sandbox 구매/환불 이벤트로 수신 상태를 검증한다.

## 2026-09-07 — Apple Server Notification URL 등록 완료 범위 확인

<!-- codex-turn:clarify-scope-after-apple-notification-url-setup -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. 사용자가 App Store Connect에 RevenueCat Apple Server Notification URL을 등록했으며 Codex는 외부 설정을 변경하지 않았다.
- 작업 목표: URL 등록으로 완료된 범위와 전체 Apple/RevenueCat 결제 연동의 남은 작업을 구분한다.
- 완료 범위: Apple Server Notification v2의 목적지가 RevenueCat으로 설정됐다. 이벤트가 없을 때 RevenueCat의 `No notifications received` 표시는 정상이다.
- 남은 범위: exact Bundle ID 확인, In-App Purchase key와 App Store Connect API key 연결 여부 확인, Apple 상품 생성·RevenueCat import/Offering, 모바일 SDK 식별 구매, Billing webhook/API reconciliation·원장 구현과 sandbox E2E가 남는다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 상태 설명과 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: Apple→RevenueCat→Billing webhook 방향과 Billing source-of-truth를 유지한다.
- 위험·미확인: 실제 notification 설정 저장값, 두 Apple key 연결 여부와 sandbox event 수신은 아직 증명되지 않았다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 Git 이력은 변경하지 않았다.
- 다음 작업: RevenueCat iOS app 설정의 필수 key 상태를 확인하고, Apple 상품을 기다리는 동안 ADR-004와 PLAN-007을 작성한다.

## 2026-09-07 — RevenueCat Google Play 연동 필요성 확인

<!-- codex-turn:confirm-google-play-revenuecat-setup-required -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. RevenueCat·Google Play·Jira·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: Apple RevenueCat 설정 뒤 Google Play 연동도 필요한지와 현재 가능한 범위를 확인한다.
- 결론: 양 Store 출시 범위이므로 동일 RevenueCat project에 Google Play app을 별도로 추가해야 한다. Apple 설정은 Google package/service credential/RTDN을 대신하지 않는다.
- 현재 가능: exact Android package name으로 app 등록, Google service account와 최소 권한 credential 준비, RevenueCat 안내에 따른 RTDN 목적지 준비를 진행할 수 있다.
- 승인 후 가능: Google 판매자 계정 접근이 열린 뒤 consumable one-time product 5개를 생성·활성화하고 RevenueCat에 import해 Offering/Package에 연결한다.
- 보안: Google service account JSON과 key는 Git·문서·Jira·채팅에 기록하지 않고 RevenueCat에 직접 업로드하며 secret manager에만 보관한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 설정 순서와 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: Apple/Google 양쪽 consumable one-time 제품, RevenueCat 연동 계층과 Billing source-of-truth를 유지한다.
- 위험·미확인: 실제 Android package name, Google credential 권한과 RTDN topic/subscription 구성은 Console 안내 및 ADR-004에서 exact 확인해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 설정은 변경하지 않았다.
- 다음 작업: RevenueCat `Apps & providers`에서 Google Play app을 추가하고 service credential 단계의 실제 화면을 기준으로 안전하게 설정한다.

## 2026-09-07 — Android package name 생성 시점 확인

<!-- codex-turn:clarify-android-package-name-before-release -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. 첨부 Play Console 화면을 분석했으며 외부 출시·RevenueCat·Jira·AWS·Git 상태는 변경하지 않았다.
- 작업 목표: RevenueCat Google app에 넣는 package name이 실제 Play 출시 후 생기는 값인지 확인한다.
- 확인 결과: package name은 Android build의 `applicationId`로 출시 전에 이미 정해진다. 첨부 화면은 AAB 생성·검토 전송이 완료된 단계이므로 현재 앱에도 package name이 존재하며 이를 얻기 위해 공개 게시할 필요가 없다.
- 확인 기준: Android app module의 `defaultConfig.applicationId`가 authoritative하고 Play Console 앱 URL/상세정보 값과 exact match해야 한다. `namespace`가 별도이면 이를 잘못 사용하지 않는다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 화면·설정 설명과 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: 실제 package name만 RevenueCat Google app identifier로 사용하고 임의의 값 또는 출시 상태로 identity를 결정하지 않는다.
- 위험·미확인: 모바일 저장소가 현재 workspace에 없어 실제 `applicationId` 값 자체는 확인하지 못했다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 Console 상태는 변경하지 않았다.
- 다음 작업: 모바일 source 또는 Play Console에서 exact package name을 확인해 RevenueCat Google Play app을 등록한다.

## 2026-09-07 — RevenueCat Google service account credential 절차 확인

<!-- codex-turn:guide-revenuecat-google-service-account-credentials -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. RevenueCat 공식 문서를 확인했으며 Google Cloud·Play Console·RevenueCat·Jira·AWS·Git 상태는 변경하지 않았다.
- 작업 목표: RevenueCat Google app이 요구하는 Service Account Credentials JSON의 생성 위치, API/role과 Play app 권한을 exact하게 안내한다.
- Cloud 설정: 전용 service account를 만들고 Android Publisher, Play Developer Reporting, Pub/Sub API를 활성화하며 `Pub/Sub Editor`, `Monitoring Viewer` role을 부여한다. service account의 JSON key를 생성한다.
- Play 권한: service account email을 `사용자 및 권한`에 초대하고 토선생 앱을 추가한 뒤 app info read-only, financial/order view, order/subscription manage, store presence manage 네 account permission을 부여한다.
- RevenueCat: JSON을 Google app의 Service account credentials에 직접 업로드하고 validator를 실행한다. Google permission 전파에는 공식 안내상 최대 36시간이 걸릴 수 있다.
- 보안: 기존 Firebase/backend key를 재사용하지 않고 JSON private key를 Git·문서·Jira·채팅에 기록하지 않는다. 이 작업에서 실제 credential을 생성·열람·저장하지 않았다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 공식 문서 확인과 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: Google purchase validation/RTDN은 RevenueCat 연동 계층을 사용하고 Billing이 entitlement/ledger source of truth인 경계를 유지한다.
- 위험·미확인: 사용할 Google Cloud project, service account 실제 상태와 Play Console 한국어 권한명은 계정 화면에서 확인해야 하며 RTDN exact topic 설정은 다음 단계다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 리소스는 변경하지 않았다.
- 다음 작업: 전용 service account와 JSON을 생성하고 Play 권한을 부여해 RevenueCat validator가 `Valid credentials`가 되는지 확인한다.

## 2026-09-07 — Google Cloud service account IAM 역할 위치 안내

<!-- codex-turn:locate-google-cloud-service-account-roles -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Google Cloud·Play Console·RevenueCat·Jira·AWS·Git 상태는 변경하지 않았다.
- 작업 목표: RevenueCat service account의 `Pub/Sub Editor`, `Monitoring Viewer` 역할을 어느 Console에서 부여하는지 안내한다.
- 안내 내용: Google Cloud의 대상 project를 선택한 뒤 `IAM 및 관리자 > IAM`에서 service account principal을 편집하고 역할 두 개를 추가한다. 이는 Play Console의 app/account permission과 별도다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 설정 위치와 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: RevenueCat 공식 credential role과 최소 Play 권한을 구분해 유지한다.
- 위험·미확인: 사용자 Google 계정에 project IAM 수정 권한이 없으면 Project Owner/IAM 관리자 승인이 필요하다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 리소스는 변경하지 않았다.
- 다음 작업: service account 행에 두 역할을 저장한 뒤 JSON key 생성과 Play Console 초대를 진행한다.

## 2026-09-07 — Google service account JSON key 생성 안내

<!-- codex-turn:create-google-service-account-json-key -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. 사용자가 Google Cloud 역할 부여를 완료했으며 Codex는 Cloud·Play·RevenueCat·Jira·AWS·Git 상태를 변경하지 않았다.
- 작업 목표: RevenueCat에 업로드할 Google service account credential JSON의 생성 경로와 안전한 취급을 안내한다.
- 안내 내용: Google Cloud `IAM 및 관리자 > 서비스 계정`에서 전용 계정을 선택하고 `키 > 키 추가 > 새 키 만들기 > JSON`으로 생성한다. 다운로드된 파일을 RevenueCat Google app의 credential field에 직접 업로드한다.
- 보안: JSON은 private key이므로 내용을 공유·기록하지 않고 secret manager에만 보관한다. 이 작업에서 실제 key를 생성·열람·저장하지 않았다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 설정 안내와 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: 전용 service account, 최소 Cloud/Play 권한과 credential 비저장 원칙을 유지한다.
- 위험·미확인: 조직 정책이 user-managed service account key 생성을 차단하면 Google Cloud 조직 관리자의 정책 변경 또는 승인된 대체 절차가 필요하다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 리소스는 변경하지 않았다.
- 다음 작업: JSON을 RevenueCat에 업로드한 뒤 Play Console app/account permission을 부여하고 credential validation을 실행한다.

## 2026-09-07 — Play Console RevenueCat 서비스 계정 권한 선택 확인

<!-- codex-turn:confirm-play-console-revenuecat-permissions -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. 사용자가 Play Console 서비스 계정 초대 화면을 공유했으며 실제 principal/credential은 작업 기록에 남기지 않았다. 외부 상태와 Git 이력은 변경하지 않았다.
- 작업 목표: 한국어 Play Console UI에서 RevenueCat service account에 부여할 exact 최소 권한과 제외 권한을 식별한다.
- 선택 권한: 앱 정보/일괄 보고서 읽기, 재무 데이터·주문·취소 설문 보기, 주문·구독 관리, 앱 정보 관리 네 항목이다. `앱 정보 관리` 설명의 인앱 상품 관리가 RevenueCat 문서의 `Manage store presence`에 대응한다.
- 제외 권한: 관리자, 앱 초안, 출시/테스트, Play Games, 리뷰, 정책, 딥 링크와 Android 개발자 인증은 부여하지 않는다. App access에는 토선생 앱을 추가한다.
- 만료: 지속적인 server validation을 위한 machine principal이므로 access expiry는 기본적으로 설정하지 않고 JSON key rotation/revocation으로 관리한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 권한 화면 분석과 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: RevenueCat 공식 네 Play permission과 최소 권한·credential 비저장 원칙을 유지한다.
- 위험·미확인: 권한 저장 후 Google 전파와 RevenueCat validator 결과는 최대 36시간 동안 pending일 수 있다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 권한은 Codex가 변경하지 않았다.
- 다음 작업: 네 권한과 토선생 app access로 초대를 완료하고 RevenueCat credential validator를 실행한다.

## 2026-09-07 — RevenueCat Google credential validation 대기 상태 진단

<!-- codex-turn:diagnose-revenuecat-google-credential-validation-pending -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. RevenueCat·Google Cloud·Play Console·Jira·AWS·Git 상태는 변경하지 않았다.
- 작업 목표: JSON 업로드 후 표시된 `File saved`와 `credentials need attention / unable to validate`의 의미와 대응 순서를 설명한다.
- 진단: JSON 파일 저장은 성공했지만 RevenueCat의 Google API endpoint/permission 검증이 아직 실패한 상태다. 신규 credential과 permission은 공식 안내상 최대 36시간 전파될 수 있다.
- 점검: service account enabled, Play 사용자 active, 토선생 app access와 네 권한, Android Publisher/Developer Reporting/Pub/Sub API, Pub/Sub Editor/Monitoring Viewer와 signed AAB를 확인한다.
- 대응: 설정 직후에는 key를 재생성하지 않고 전파를 기다려 재검증한다. 36시간 이후에도 실패하면 RevenueCat 상세 validator 결과를 확인하고 필요한 설정만 수정한 뒤 JSON 재업로드로 validation을 다시 실행한다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 외부 validator 상태 분석과 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: credential 최소 권한·비저장 원칙과 실제 Google 호출 없는 로컬 테스트 경계를 유지한다.
- 위험·미확인: generic 메시지만으로 실패 endpoint를 특정할 수 없고 Google Payments 판매자 계정/monetization API 상태의 영향도 실제 validator detail로 확인해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 credential은 변경하지 않았다.
- 다음 작업: 필수 설정 체크리스트를 확인하고 최대 36시간 뒤 RevenueCat validator를 재실행하며, 계속 실패하면 상세 오류를 확인한다.

## 2026-09-07 — ADR-004 결제 기술 계약 확정 의제 재검토

<!-- codex-turn:identify-adr-004-payment-decisions -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. RevenueCat 공식 webhook 문서와 현재 fixed-term 계약을 검토했으며 RevenueCat·Store·Jira·AWS·Git 상태는 변경하지 않았다.
- 작업 목표: Store/RevenueCat 계정 준비 후 ADR-004에서 사용자가 확정해야 할 선택과 출시 입력을 구분한다.
- 재확정 불필요: 양 Store consumable one-time 제품 5개, exact hour duration, ACTIVE MEMBER, `purchaseAccountRefId`, paid-first/free-preserve, stacking/refund state table과 보존 정책은 이미 승인됐다.
- 승인 대상: public route/DTO, sync hint·idempotency, webhook Authorization+HMAC와 durable ack, sandbox/production webhook 분리, restore/transfer/anonymous behavior, S2S-only purchase tracking, refund auto handling, webhook direct apply/API reconciliation 및 Learning Core durable revoke delivery다.
- 공식 확인: RevenueCat은 optional Authorization header와 HMAC signing을 함께 제공한다. HMAC은 `X-RevenueCat-Webhook-Signature`의 timestamp와 raw body로 검증하고 retry마다 재서명되므로 5분 replay tolerance를 적용할 수 있다.
- 기술 작성 대상: Mongo payment collection/index/Transaction/CAS, UTC time, payload limit, unknown provider field/event 처리, timeout/backoff, retention purge와 observability는 승인된 정책을 exact schema로 구체화한다.
- 출시 입력: 실제 product ID/가격/국가, public hostname/ALB/certificate, Store review, RevenueCat secret/quota와 sandbox account는 후속 config/E2E 값이며 ADR·fake 기반 구현을 막지 않는다.
- 변경 파일: `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 계약·공식 문서 분석과 작업 기록만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`로 문서 형식을 확인한다.
- 유지한 계약: RevenueCat은 연동 계층, Billing은 entitlement/ledger source of truth이며 client callback으로 fail-open하지 않는 기존 결정은 유지했다.
- 위험·미확인: RevenueCat REST API exact version/endpoint, 실제 webhook raw payload fixture, public infrastructure inventory와 Learning Core consumer wire는 ADR 작성 중 코드/문서 근거로 추가 확인해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드와 외부 시스템은 변경하지 않았다.
- 다음 작업: 사용자가 권장안 9개를 승인하거나 예외를 선택하면 ADR-004를 exact API·Mongo·RevenueCat contract로 작성한다.

## 2026-09-07 — ADR-004 권장 선택 9개 승인 반영

<!-- codex-turn:approve-adr-004-recommended-payment-decisions -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Jira·RevenueCat·Store·AWS·Git 이력은 변경하지 않았다.
- 작업 목표: 사용자가 승인한 ADR-004 권장 선택 9개를 Billing 단일 기준 계약과 통합 안내서에 반영한다.
- 확정 내용: resource별 public API, UUID idempotent sync와 202 PENDING, Authorization+HMAC 5분/durable inbox fast ack, environment-filtered webhook 두 개, original owner restore·anonymous 금지, S2S-only tracking/refund auto handling OFF, webhook direct apply+API reconciliation과 per-AttemptGroup durable revoke event다.
- 변경 파일: `AGENTS.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/contracts/FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `docs/codex/CURRENT_STATE.md`, `docs/codex/WORKLOG.md`.
- 테스트 결과: 계약 문서만 변경했으므로 Gradle 테스트는 실행하지 않았다. `git diff --check`와 승인/미확정 표현 검색으로 검증한다.
- 유지한 계약: 양 Store consumable five-offer, Billing duration/source-of-truth, paid-first/free-preserve, refund 상태표와 보존 정책은 변경하지 않았다.
- 위험·미확인: exact DTO/error/rate limit, Mongo schema/index, RevenueCat REST API version/endpoint, webhook raw fixture와 Learning Core revoke wire는 ADR-004 작성에서 고정해야 한다.
- 예상 밖 변경: 없음. 애플리케이션 코드, Jira와 외부 리소스는 변경하지 않았다.
- 다음 작업: 사용자의 작성 요청을 받으면 `docs/adr/ADR-004-...md`를 생성해 승인 결정을 구현 가능한 기술 계약으로 구체화한다.

## 2026-09-07 — ADR-004 RevenueCat fixed-term 결제 기술 초안 작성

<!-- codex-turn:write-adr-004-fixed-term-payment-contract -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. 결제 Jira는 없음. 기존 미커밋 변경을 보존하고 commit/push·Jira·Console·배포는 수행하지 않았다.
- 작업 목표: 사용자 작성 요청에 따라 승인된 9개 선택을 public API·RevenueCat adapter·Mongo·LC event 명세로 구체화하고 추가 정책 결정 필요 여부를 확인한다.
- 변경 파일: 신규 `docs/adr/ADR-004-fixed-term-premium-payment-contract.md`; 안내/상태 동기화 `AGENTS.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/contracts/FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `docs/codex/CURRENT_STATE.md`, 이 WORKLOG.
- 작성 내용: 5줄 결론·필독·정책 선택·기술 gate를 앞에 배치하고 DTO/오류/rate limit, HMAC raw-body·durable inbox, v2 API mapping/멱등성, 계정 binding, timeline/refund Transaction, schema v5 index, 공통 시험 guard, LC revoke wire·제출 경합, worker·보존·인증·배포·테스트를 본문/부록에 작성했다.
- 공식 조사: RC API v2 purchase 조회/Store 식별자 검색/customer purchase 목록, OpenAPI owned/refunded와 original_customer_id/string transaction identifier를 확인했다. 공식 문서 읽기만 수행했으며 실제 사용자 RC API·credential·Store 거래를 조회하지 않았다.
- 유지한 계약: consumable one-time 5상품, 24/72/168/336/720시간, paid-first/free-preserve, 원구매자 고정, 기존 무료/phone continuation, refund 상태표와 5년/120일/35일 보존. API v2와 webhook의 다른 ID·enum을 분리했다.
- 결정사항: D1 정상 만료 중 Session과 D2 REFUND_REVERSED는 새로 확인한 미확정 예외 정책이며 권장안을 C9-S9·ADR에 명시했다. 사용자 승인으로 오인하지 않는다. Google obfuscated ID와 raw UUID의 wire 동일성은 미검증 단정 대신 SDK gate로 정정했다.
- 위험·미확인: G1~G8 SDK/owner/refund/review/누락 복구/LC race/Mongo migration/실제 환경 gate. 비동기 revoke를 cross-service 즉시 차단으로 표현하지 않는다. 기술 초안·상대 서버 계약·schema는 아직 미구현이다.
- 테스트 결과: 문서 작업으로 Gradle 미실행. `git diff --check` 통과, 새 ADR의 로컬 Markdown 링크 10개와 JSON 예제 4개 문법 검사 통과.
- 예상 밖 변경: 이번 작업은 위 Billing 문서만 수정했다. 작업 전부터 존재한 문서 수정과 결제 요약 untracked 상태를 보존했고 다른 서버 코드·설정은 수정하지 않았다.
- 다음 작업: D1·D2 확인과 상세 ADR 검토 뒤 PLAN-007, 명시적 Jira 승인, vertical slice 구현 순으로 진행한다. 배포 전 실제 상품·credential·public/internal 경계와 Store sandbox E2E를 확인한다.

## 2026-09-07 — PLAN-007 앱용 무료 사용권·이용 상태 조회 계획 작성

<!-- codex-turn:plan-007-public-free-entitlement-query -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Jira 미생성. 문서 작성만 수행했으며 commit/push·외부 배포·타 서버 변경 없음.
- 목표: 사용자가 요청한 무료 수량·진행·재응시·완료 조회의 exact API/DTO/오류/인증·판정·배포·테스트 계획을 작성한다. 결제 구현은 제외한다.
- 변경 파일: 신규 `docs/plans/PLAN-007-public-free-entitlement-query.md`; 안내 동기화 `AGENTS.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`, `docs/adr/ADR-004-fixed-term-premium-payment-contract.md`, `docs/codex/CURRENT_STATE.md`, 이 WORKLOG.
- 확인 근거: Billing reserve의 lazy Claim/Grant 발급·retained alias/owner/units·determineKind, lifecycle/phone continuation·rebind, 기존 Mongo index/transaction/security. Identity JwtAccessTokenIssuer의 단일 audience/scope/account_type과 public envelope를 읽기 확인했다.
- 계획 결정: GET /api/v1/entitlements, public isSuccess/code/message/result, Billing audience/billing:read/sub, Guest/MEMBER read 허용. 수량과 action을 분리하고 current-owned group만 최소 공개한다. 미확인은 PENDING/null, 저장소/불변식 실패는 503이며 GET에서 지급·hold·consumption·expiry·owner mutation 금지다.
- 유지한 계약: 기존 무료 reserve/confirm/cancel·3년 retained·재가입 제한·source 학습기록 비이전·internal Lattice/SigV4/DTO. paid reader는 별도 후속이며 payment schema v5를 선행하지 않는다.
- 위험·미확인: 미수신 event freshness/owner job 실행 여부는 현재 projection만으로 완전 감지 불가. conservative PENDING과 LOCAL_PROJECTION 의미를 명시했다. Identity Billing aud/read 발급·실배포, public connector/ALB·JWKS·Mongo snapshot E2E는 release gate다. 기존 internal resolver에서 모호한 복수 group은 임의 허용하지 않는다.
- 동작 변경: application code는 변경하지 않았다. PLAN-007을 무료 reader에 배정하고 결제 후속 계획 번호와 wrapper 차이 검토를 안내했다. 이전 미커밋 문서 변경과 결제 초안을 보존했다.
- 테스트 결과: 문서 전용이므로 Gradle 미실행. 새 계획의 Markdown 링크 25개(앵커 5개 포함)와 JSON 예제 4개, whitespace 및 `git diff --check` 검사 통과. 상태표는 확인한 reserve/owner 정책과 대조했으며 실제 Billing/Identity 테스트를 이번 작업에서 실행했다고 주장하지 않는다.
- 예상 밖 변경: 없음. Billing 계획·연결 문서·필수 작업 기록만 변경했으며 기존 WORKLOG는 수정하지 않았다.
- 다음 작업: 사용자의 PLAN-007 구현 승인과 필요 시 Jira 승인 후 reader vertical slice 진행. Billing OFF 배포→Identity audience/read→staging→reader 활성화→프론트 순서와 기존 internal 회귀를 확인한다.

## 2026-09-07 — PLAN-007 검토 보완: command TTL 독립 세션 귀속

<!-- codex-turn:revise-plan-007-durable-session-attribution -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. Jira 없음. 사용자 검토 피드백에 따라 계획/기록만 보완하고 코드·타 서버·배포·Git 이력은 변경하지 않았다.
- 목표: 정상 target confirm 뒤 command 삭제로 영구 PENDING이 되는 설계 문제를 제거하고 Guest 자격 불명 UX를 명확히 한다.
- 코드 근거: ReservationProperties의 terminalCommandRetention 기본 7일, ReservationLifecycleService.confirmOnce의 reserve/confirm purgeAt 설정, Reservation/AttemptSession의 userId/epoch 부재, ReserveContinuationPolicy의 continuation 생략 허용, link의 최신 transition 덮어쓰기를 확인했다. 실제 배포 retention 값은 확인하지 않았다.
- 변경 파일: `docs/plans/PLAN-007-public-free-entitlement-query.md`, `AGENTS.md`, `docs/codex/CONTRACT_DECISIONS.md`, `docs/codex/CURRENT_STATE.md`, 이 WORKLOG.
- 보완 설계: current link의 sessionOwnerEpoch와 경쟁 제어 sessionBindingVersion, Reservation/Session의 immutable epoch snapshot. 기존 reserve/confirm/rebind Transaction에서만 기록하며 GET은 read-only를 유지한다. PHONE_REJOIN 실제 적용만 epoch 증가, USER_MERGED는 보존, source Session 재귀속 금지다.
- migration: continuation exact positive evidence·남아 있는 command·미이전 link 근거로 별도 dry-run/bounded CAS 이관한다. 복구 불가능 legacy는 활성화 gate/503이며 command TTL 연장·현재 owner 일괄 복사·무기한 PENDING으로 해결하지 않는다. wire/수량/ledger/기존 owner 정책은 유지한다.
- UX: ELIGIBILITY_UNKNOWN은 '사용 가능 여부를 확인할 수 없습니다', OWNER_LINK_UNRESOLVED도 실제 작업 실행을 단정하지 않는다. 실제 reservation/command 처리 대기와 bounded polling을 구분했다. Identity §9.2의 Billing aud/read·account_type·workload 분리 요청은 유지했다.
- 테스트 계획: T25~T33에 target confirm 후 command 삭제, 반복 phone rejoin, source 잔존, continuation 없는 후속 replacement, Guest merge/duplicate/NOOP, rebind 경쟁, legacy 복구와 privacy retention을 추가했다.
- 검증: 문서 전용으로 Gradle 미실행. 변경 계획의 로컬 링크 30개(앵커 5개 포함)·JSON 예제 4개·whitespace 및 `git diff --check` 통과.
- 위험·미확인: 신규 metadata와 migration은 미구현이다. 기존 활성 legacy의 증빙 coverage와 old writer rollback 방지, Transaction CAS 경합 검증을 reader 활성화 전에 완료해야 한다.
- 예상 밖 변경: 없음. 기존 미커밋 문서 변경과 과거 WORKLOG를 보존했다.
- 다음 작업: 보완 계획의 쓰기 metadata 범위를 포함해 구현/Jira 승인 후 진행. 실제 무료 지급·LC 기록 이전·Identity 코드 변경은 이 문서 보완에 포함하지 않는다.

## 2026-09-07 — PLAN-007 무료 사용권 public reader 구현

<!-- codex-turn:implement-plan-007-public-reader -->

- 날짜·브랜치: 2026-09-07, Billing `develop`. 사용자의 “좋아 이제 구현해줘” 승인으로 진행. Jira 없음. commit/push·배포·Identity/LC 수정은 수행하지 않았다.
- 목표: 무료 수량·진행·재응시·완료 조회, 사용자 JWT 경계, command TTL 독립 귀속 증빙을 구현한다. 결제·RevenueCat·paid schema는 제외한다.
- 신규 파일: domain/entitlement의 api `EntitlementQueryController`, application `EntitlementQueryService`, `FreeBenefitQueryReader`, `TrialBenefitQueryReader`, `TrialEntitlementQueryEvaluator`, `EntitlementQuerySnapshot`, `EntitlementQueryRateLimiter`, `SessionAttributionMigration`, config `EntitlementQueryProperties`, dto `EntitlementQueryResponse`, exception `EntitlementQueryException`, repository `EntitlementQueryRepository`; entitlement/trial/application `TrialEntitlementPolicy`; global security `IdentityUserJwtDecoder`, `PublicSecurityConfig`, `PublicIngressFilter`, `PublicApiWriter`; global response `PublicResponse`, Mongo `EntitlementSnapshotExecutor`.
- 기존 writer 변경: BillingSubjectLink/Reservation/AttemptSession epoch metadata, BillingSubjectLinkRepository의 reserve/confirm binding CAS 및 rebind epoch 전이, ReserveService·ReservationLifecycleService 연결. 동일 Transaction에서 처리하며 신규 사용자 ID 사본·원장/무료권 지급 정책·internal DTO 변경은 없다.
- 조회 동작: verified current candidates·retained Claim·current owner·Grant units·Reservation·AttemptGroup·epoch 연결을 함께 판정한다. Grant 부재 신규 자격은 1, 완료 번호는 0, 불명은 null/PENDING, 불변식/DB 실패는 503. current-owned 최소 그룹만 공개하고 전화번호 재가입의 이전 Session/답안/결과를 노출·이전하지 않는다.
- 읽기 동작: primary/SNAPSHOT Transaction과 Mongo driver CSOT 총 2초 budget, 최대 두 번 snapshot, batch/상한+1 조회, benefit 20/subject 100/group 100/응답 64KiB 방어. GET은 Claim/Grant/ledger/command/alias/owner를 생성·변경하지 않는다. 서로 다른 benefit/unit 합산 없음, 현재 free reader만 등록한다.
- 인증·ingress: RS256/typ/kid/exact issuer·trusted HTTPS JWKS/audience/iat/exp/nbf/jti/canonical sub, exact billing:read; Guest/MEMBER read 허용. JWKS는 redirect 금지, 5분 cache·30초 refresh 제한·64KiB/20 keys 상한, unknown kid 401/신뢰 upstream 장애 503. separate public port 기본 8083과 기존 internal port 격리, flag OFF=404, no-store·public envelope·trace header. task-local 60-token bucket/초당 1 refill/5분 비활동/10,000 entries.
- 관측: 실제 production Controller에 INTERNAL entitlement_query span, 정상·예외 종료, service/environment/timestamp/outcome/reason/durationMs/traceId/spanId 로그·저카디널리티 metric. raw 예외 대신 안전한 분류만 span error에 기록하며 baggage 비전파를 검증했다.
- 이관: 명시적 <=100 Session batch의 inspect/applyApprovedBatch를 제공하며 일반 startup/GET/scheduler에서 실행하지 않는다. 미이전 link 또는 exact 최신 PHONE_REJOIN continuation positive proof만 자동 CAS 이관한다. command actor/시간만으로 반복 재가입을 추측하지 않으며 추가 transition 증거가 필요한 자료는 BLOCKED다. 운영 이관은 실행하지 않았다. 기존 legacy RESERVED는 writer 배포 전 drain/expire/승인 이관 gate이며 구버전 writer rollback을 금지한다.
- 문서 변경: PLAN-007 구현 상태, docs/openapi/free-entitlements.yaml, docs/runbooks/PLAN-007-public-reader-rollout.md, AGENTS/CONTRACT_DECISIONS/통합 계약/CURRENT_STATE와 이 WORKLOG. 기존 결제 초안·사용자 미커밋 변경과 과거 WORKLOG를 보존했다.
- 테스트 파일: 신규 TrialEntitlementQueryEvaluatorTest, EntitlementQueryControllerTest, EntitlementQueryTraceIntegrationTest, IdentityUserJwtDecoderTest. 기존 ReserveMongoIntegrationTest에 query no-write command listener/document snapshot, command 삭제/8일 경과·반복 phone rejoin·일반 replacement·Guest merge·legacy dry-run/CAS·exact continuation/차단·상한·동시 snapshot/CAS 회귀 추가. OwnerRebindMongoIntegrationTest epoch 보존/증가 assertion 및 SecurityConfigTest 새 Controller mock 보완.
- 추가 발견·보정: 기존 TrialEligibilityMongoIntegrationTest의 schema=3 fixture 3곳을 현재 schema v4로 수정했다(legacy schema 거절 자체 테스트는 유지). ReserveMongoIntegrationTest helper의 고정 mock-1을 실제 reserved mockExamId로 수정했다. 본래 검증하려는 index/continuation 계약에 도달하도록 고친 테스트 fixture이며 production schema/wire 변경이 아니다.
- 테스트 실행: 처음 Docker 미실행으로 Mongo 테스트 실패, Docker Desktop 시작 승인 후 replica-set 테스트 실제 실행. 구현 중 null 수량 unboxing·Spring AuthenticationServiceException 503 직렬화·trace 테스트의 Mongo verifier fixture 문제를 보정했다. 최종 `./gradlew clean test` BUILD SUCCESSFUL, **236 tests / failures 0 / skipped 0**. OpenAPI YAML 파싱·`git diff --check` 통과. 테스트는 가짜 token/JWKS와 로컬 Testcontainers만 사용했다.
- 유지한 계약: 무료 lazy 지급/전화번호당 1회/retained 3년, reserve→Session commit→confirm·5분 hold·ledger/consumption, owner lifecycle·source 학습기록 비이전·bounded fence, command TTL 7일, internal Lattice/SigV4/strict wire/HTTP. payment SDK·endpoint·collection/index 추가 없음.
- 위험·배포 확인: Identity Billing aud/read 실제 발급·token 갱신, trusted JWKS 운영 회전/장애, 실제 public ALB/SG/Lattice 격리·health matcher, Mongo index explain/실제 부하, 전체 legacy coverage는 미검증 외부 gate다. flag/connector/legacy-attest 기본 false 유지. PENDING은 job 진행 또는 최신 upstream 동기화 보증이 아니다.
- 예상 밖 변경: 위 기존 테스트 fixture 보정 외 범위 확장 없음. 시작 시 존재한 사용자 문서/결제 초안 변경은 이번 신규 코드 작업과 구분해 보존했다. 실제 store/AWS/타 서버/Jira/Git 이력 변경 없음.
- 다음 작업: 이 diff 검토 후 사용자 commit/PR, Identity §9.2 aud/read 후속 확인, 승인된 legacy coverage와 staging/네트워크 gate 후 reader 활성화·프론트 연동. 결제는 별도 ADR-004 결정/계획을 따른다.

## 2026-09-07 — 중단 후 무료 재응시 조회 응답 설명

- 날짜·브랜치: 2026-09-07, develop. Jira 없음. 목표는 사용자 질문에 현재 구현 기준으로 응답 의미를 설명하는 것이다.
- 확인 근거: TrialEntitlementQueryEvaluator의 현재 candidate/VERIFIED·owner/epoch·Session/Reservation 연결, OPEN·RETAKE_AVAILABLE 판정, active Reservation/PROCESSING 대기 조건.
- 결론·동작: 정상 RETAKE_AVAILABLE은 availableQuantity 0, newAttempt BLOCKED, usageState INCOMPLETE, hasInProgress false, retake ALLOWED와 current-owned group ID다. 앱 종료만으로 OPEN/ACTIVE가 남는 경우 hasInProgress true일 수 있으나 추가 차감 없는 replacement 가능성과 모순되지 않는다.
- 유지 계약: 새 무료권 복원·지급 없음, 기존 consumption/group/mock 유지·새 Session에서 처음부터 재응시, reserve 최종 판정. reader 기본 OFF와 연동 gate 유지.
- 변경 파일: CURRENT_STATE와 이 WORKLOG만. 애플리케이션·타 서버·Git 이력·Jira 변경 없음. 예상 밖 변경 없음.
- 검증: 코드 읽기 분석만 수행하여 Gradle은 재실행하지 않음. 문서 diff 확인. 위험·미확인: 실제 배포/LC 상태 반영 시점은 확인하지 않음; OPEN을 앱 실시간 접속으로 해석하지 않는다.
- 다음 작업·결정사항: 앱은 신규 수량만으로 이용 불가를 표시하지 말고 retake를 별도로 확인한다. 처리 중이면 PENDING으로 표시하고 실제 시작은 기존 LC reserve 흐름을 따른다.

## 2026-09-07 — 무료 재응시를 화면에서 1회 남음으로 표현

- 날짜·브랜치: 2026-09-07, develop. Jira 없음. 목표: 중단 후 재응시도 사용자에게 1회 남음으로 표시하자는 의견을 기존 계약과 대조한다.
- 근거: EntitlementQueryResponse.Benefit의 availableQuantity/newAttempt/retake/usageState/attemptGroups 분리 계약. 신규 INITIAL 수량과 추가 차감 없는 재응시는 현재 별도 필드다.
- 결론·제안: 현재 FREE_EXAM_ONCE에서 신규 시작 또는 재응시가 ALLOWED이면 화면에 '무료 모의고사 1회 남음'으로 통합 표현할 수 있다. 내부 availableQuantity를 복원하거나 두 가능성을 더해 2개로 표시하지 않는다. GRADING·예약 처리·자격 불명은 별도 안내하며 사용 완료만 0회 의미로 표시한다.
- 유지 계약: 기존 group/consumption 유지, 새 Session 재응시, 추가 지급 없음, reserve 최종 판정과 internal/public DTO 유지. 다른 benefit/단위/복수 source에 고정 1 규칙을 일반화하지 않는다.
- 변경 파일·동작: CURRENT_STATE와 WORKLOG 작업 기록만 추가. 서비스·프론트·API wire·타 서버·Jira·Git 이력 변경 없음. 예상 밖 변경 없음.
- 검증: DTO 읽기·문서 diff 확인. 분석/안내만 수행하므로 Gradle 미실행. 실제 프론트 표시 구현은 미확인이다.
- 다음 작업·위험: 화면 통합 표시 적용 시 GRADING/PENDING을 사용 가능 1회로 확정하지 않도록 상태 우선순위를 지킨다. backend DTO 변경이 필요하면 별도 명시 계약으로 정한다.

## 2026-09-07 — PLAN-007 브랜치·커밋·PR 명령 인계

- 날짜·브랜치: 2026-09-07, develop. Jira 없음. 사용자 요청은 직접 실행할 Git 명령 안내다.
- 확인: git status/branch/remote로 Billing develop의 코드·문서 미커밋 변경과 origin을 확인했다. 기존 결제 ADR-004/요약 문서도 미추적 상태로 남아 있으므로 문서 전체 staging의 포함 범위를 안내한다.
- 제안: codex/free-entitlement-query 브랜치 생성, AGENTS.md/docs/src 명시 staging·staged diff 확인, 사용자 commit/push, develop 대상 gh pr create. 실제 실행은 사용자에게 맡긴다.
- 변경 파일: CURRENT_STATE와 WORKLOG 기록만. 애플리케이션·Git branch/index/commit/remote/PR/Jira 변경 없음. 기존 사용자 변경 보존, 예상 밖 변경 없음.
- 검증: Git 읽기 상태 확인 및 문서 diff 확인. 명령 안내만이므로 Gradle 재실행하지 않음. 기존 테스트 결과는 변경하지 않는다.
- 유지 계약·다음 작업·위험: Git commit/push는 사용자 수행 규칙 유지. 기존 결제 설계 문서 포함 여부를 staged diff에서 확인한 뒤 commit하며, 결제 구현이나 배포를 수행하는 PR로 오인하지 않는다.

## 2026-09-07 — PLAN-007 Identity 작업에서 구현 교차 검토

<!-- codex-turn:01a07ace-3281-78c1-b731-14ed60bc16bc -->

- 날짜·브랜치: 2026-09-07, Billing develop@eb0ae14. PR #9 merge/구현 commit dadb83f를 로컬에서 확인했다.
- 목표: 사용자 요청으로 reader·재응시 표시·귀속 증빙·JWT·internal 경계와 회귀를 확인한다.
- 변경 파일: docs/codex/WORKLOG.md, docs/codex/CURRENT_STATE.md. Identity 작업의 기록도 해당 저장소에 갱신했으며 어느 서비스의 애플리케이션 코드도 변경하지 않았다.
- 확인 내용: 신규 INITIAL 수량과 replacement action은 구현에서 분리됐다. availableQuantity=0/retake=ALLOWED는 현재 계약대로이며 사용자 화면 1회 남음은 명시된 프론트 통합 표시가 필요하다. 별도 표시 수량 필드·앱 구현은 확인되지 않았다.
- 귀속 보완: sessionOwnerEpoch/sessionBindingVersion과 reserve/confirm/owner CAS, command 삭제·8일 경과·반복 rejoin·Guest merge 보존, snapshot 무변경과 동시성 Mongo 테스트를 확인했다.
- 테스트: ./gradlew clean test 성공. 35 suite·236개, 실패·오류·건너뜀 0. git diff --check 통과. sandbox cache 접근 제한 후 승인된 테스트 실행을 사용했다.
- 유지 계약: GET에서 지급·hold·consume·owner 변경 없음, 기존 무료 consumption 재사용, public 사용자 JWT와 internal Lattice/SigV4 분리. 검토 범위에서 추가 차단급 결함 미발견.
- 위험·다음 작업: 기본 OFF 유지. Identity audience/read 발급, legacy 이관 coverage, 운영 ALB/SG/JWKS/staging E2E와 프론트 1회 표시 확인이 남는다. 테스트 통과를 실제 배포·앱 사용 가능으로 보고하지 않는다. Jira·Git mutation·배포는 수행하지 않았다.

## 2026-09-07 — ADR-004 결제 계약 사용자 설명

- 날짜·브랜치: 2026-09-07, develop. Jira 없음. 목표: 사용자가 ADR-004를 이해하도록 제품·구매·기간·환불 흐름으로 풀어 설명한다.
- 근거: ADR-004 전체와 출력에서 생략된 §5.4~5.9를 추가 확인했다. 공식 API 지원이나 실제 RevenueCat 프로젝트 설정은 이번 작업에서 외부 재검증하지 않았으며 초안 내용을 구현 사실로 설명하지 않는다.
- 결론: 양 Store 재구매 가능한 일회성 1/3/7/14/30일 상품, Billing의 24/72/168/336/720시간 계산과 서버 검증 후 지급, payment account reference 고정, paid-first/free-preserve, ledger·inbox/outbox·중복/누락 복구, refund timeline 재배치와 LC 상태별 철회를 안내한다.
- 미확정: D1 정상 만료 전 승인된 현재 Session의 기존 기한 내 완료/만료 전 reserve 5분 confirm 예외, D2 REFUND_REVERSED의 REVIEW_REQUIRED 격리·승인 복구. 권장안은 설명만 하며 사용자 승인으로 처리하지 않는다.
- 위험·정확성: 한 달은 30일, 구매일부터 시작하며 첫 시험/알림 수신 시각부터가 아니다. 환불과 정상 만료 정책을 구분하고 비동기 LC 철회 지연을 즉시 차단 보장으로 표현하지 않는다. 소모성 상품은 시험 횟수 차감 상품을 뜻하지 않으며 소유권/계정 이전은 자동 허용하지 않는다.
- 전달 방식: visualize 스킬의 정적 Mermaid 흐름도로 Store/RevenueCat/Billing/Learning Core 역할을 구분하고 기간·환불은 구체적 예로 설명한다. 새 시각화 파일이나 애플리케이션 코드는 만들지 않는다.
- 변경 파일: CURRENT_STATE와 이 WORKLOG만. ADR 정책·코드·타 서버·상품/Console·Secret·Jira·Git 이력·배포 변경 없음. 기존 사용자 변경과 다른 작업 기록 보존, 예상 밖 변경 없음.
- 검증: 문서 읽기 분석이므로 Gradle 미실행. 문서 diff 확인. 다음 작업은 D1/D2 확인과 무료 public API 구현 이후의 wrapper/인증 재사용 대조, 별도 결제 PLAN·검증 gate이며 이번 설명 자체가 구현 승인은 아니다.

## 2026-09-07 — 4주 상품 및 D1-A·D2-A 권장안 승인 반영

- 날짜·브랜치: 2026-09-07, develop. Jira 없음. 목표: 사용자 승인에 따라 30일 상품을 4주로 바꾸고 남은 두 예외 정책을 확정한다.
- 결정: 1일·3일·1주·2주·4주 판매 구성, `PREMIUM_28D`와 28일·672시간·2,419,200초. package 후보는 premium28d이며 실제 Store/RevenueCat ID는 별도 출시 입력이다. 기존 거래의 duration을 소급 변경하지 않는다.
- D1-A: 정상 만료 전 승인된 현재 Session은 기존 시험·제출 기한 안에서 완료 허용. 만료 전 reserve의 동일 Session confirm은 기존 5분 Reservation 유효기간 안에서 허용한다. 만료된 권리로 새 INITIAL/replacement는 금지, 다음 권리로 기존 group 자동 재결속 없음, 환불에는 유예 없음.
- D2-A: REFUND_REVERSED 및 refunded→owned 재관측은 REVIEW_REQUIRED를 durable 저장하고 경보한다. 자동 지급·기간 복원·Session 재개는 하지 않으며 Store 최종 상태 확인 후 별도 승인 복구 절차로 처리한다. 원장 덮어쓰기와 영구 거절 정책이 아니다.
- 변경 파일: AGENTS.md, ADR-004, CONTRACT_DECISIONS.md, FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md, BILLING_SERVICE_INTEGRATION_CONTRACT.md, PLAN-007의 후속 결제 상태 문구, CURRENT_STATE.md, WORKLOG.md. 기존 작업 기록은 보존했고 WORKLOG는 끝에만 추가했다.
- 유지 계약: one-time consumable·RevenueCat 역할·paid-first/free-preserve·무료/owner wire와 schema·JWT/internal SigV4·환불 차단·보존기간 유지. 애플리케이션·외부 설정·타 서버·Jira·Git 이력·배포 변경 없음. 예상 밖 변경 없음.
- 검증: 문서 전용 변경으로 Gradle 미실행. diff 검토, 28×24=672시간 및 28×86400=2419200초 확인, 잔여 상품/미확정 참조 검색과 git diff --check 수행. 과거 WORKLOG/CURRENT_STATE 이력은 최신 승인으로 supersede함을 명시했다.
- 위험·다음 작업: 실제 상품 생성 여부/ID·가격·RevenueCat credential/provider 지원·sandbox·AWS 미검증. public wrapper·인증 재사용을 대조하고 별도 결제 PLAN 승인 후 Jira/구현. 이번 정책 승인을 결제 구현 또는 production 활성화 승인으로 확대하지 않는다.

## 2026-09-07 — 하루 사용 후 환불 정책 범위 확인

- 날짜·브랜치: 2026-09-07, develop. Jira 없음. 목표: 하루 사용 후 환불의 승인 기준과 확정 이후 차단 정책을 구분해 설명한다.
- 근거: ADR-004 §3·G3/G4·§5.4·§5.9, FIXED_TERM_PREMIUM_PAYMENT_CONTRACT §6/7. 외부 Store 정책이나 RevenueCat 실제 설정은 재검증하지 않았다.
- 확인: provider-confirmed 환불 이후 해당 source 신규/진행/재응시 차단, 선행 Billing GRADING 완료와 COMPLETED 기록 보존은 확정됐다. 하루 사용 시 환불 자동 거절, 사용일/AI 비용 공제, 반복 환불 제재 횟수는 정하지 않았다. 부분 환불·negative balance는 현재 범위 밖이다.
- 위험: 이미 제공한 시험·채점은 환불 뒤 회수할 수 없다. 사용 후 환불 승인 가능성을 자체 정책만으로 막는다고 보장하지 않는다. REFUNDED_AFTER_USE 관찰과 provider 지원 시 consumption evidence 방향은 있으나 자동 Refund request handling은 OFF이며 일반 REFUND_REVIEW 신호는 지원 검증 전 비활성이다. 환불 신청만으로 즉시 인지·차단 가능하다고 설명하지 않는다.
- 변경 파일·동작: CURRENT_STATE와 WORKLOG에 현황 분석만 기록. 기존 미커밋 정책 문서 보존, 정책·애플리케이션·타 서버·Jira·외부 리소스·Git 이력·배포 변경 없음, 예상 밖 변경 없음.
- 검증: 문서 근거와 git diff --check 확인. 분석/기록 작업으로 Gradle 미실행. 다음 작업은 실제 Store/관할 환불 규정 확인과 사용자 고지·소비 증빙·반복 악용 수동 검토 정책의 별도 승인이다.

## 2026-09-07 — Store 환불 정책·한국 청약철회·RevenueCat 공식 조사

- 날짜·브랜치: 2026-09-07, develop. Jira 없음. 목표: 사용 후 환불을 Store 정책과 법규에 맞춰 처리하는 방향의 실제 조건 확인.
- 변경 파일: docs/contracts/REFUND_POLICY_RESEARCH-2026-09-07.md 신규 조사 문서, CURRENT_STATE와 WORKLOG 기록. 기존 미커밋 변경 보존; 이번 승인 계약·코드·외부 설정 변경 없음.
- 조사 결과: Apple 개별 심사·국가법 권리, Google 48시간 이내 가능/이후 개발자 문의와 별도 법적 요청, 전자상거래법 제17조(시행 2026-07-21)의 제공 개시/가분적 미제공 부분/고지·시험 사용 요건 확인. 하루 사용만으로 나머지 기간 전액 거절을 확정할 수 없다.
- RC 최신 공개 문서: Apple·Google Refund Control 선호 전달, 최종 결정 Store, 거래별 사용률/이벤트 미전송, Apple 동의 필요, partial preference 미지원. RC 거래 존재 DELIVERED가 Billing 지급 완료와 같지 않을 수 있음. 실제 project 기능·설정 및 Billing 사전 review event 계약은 미확인.
- 검증: 공식 공개 본문 6개와 로컬 ADR 대조, git diff --check. 분석/문서 전용으로 Gradle 미실행. Apple 일반 약관 페이지는 미국 표시라 한국 근거에서 제외. 외부 개인 계정 부수정보·원문·credential을 기록하지 않음.
- 유지 계약·위험: 최종 환불 차단/무료 보존/ledger 및 자동 refund handling OFF 유지. 부분 자동 환불 미구현을 법적 환불 거절로 해석하지 않음. 상품 가분성·계속거래 등 법적 분류와 개발자 문의 운영·필요 동의·정확한 증빙은 출시 전 확인. 예상 밖 변경 없음. 다음은 이 조건을 반영한 정책/고지 검토이며 Console 활성화·타 서버·Jira·Git 이력·배포는 실행하지 않음.

## 2026-09-08 — 남은 환불 운영 결정과 출시 검증 구분

- 날짜·브랜치: 2026-09-08, develop. Jira 없음. 목표: 이미 승인된 기술 정책과 미정 운영/법률 검증을 구분해 사용자 결정 항목 안내.
- 근거: 2026-09-07 공식 조사 문서 및 CURRENT_STATE. 이번 turn 외부 정책 재조회 없음.
- 제안: 고객지원 채널·담당·첫 응답 목표(2영업일 권장, 법정 환급기한과 별개), 요청 유형별 개별 심사/고지, 최소 사용 증빙 범위·접근·보존, 반복 환불 자동 거절/제재 없이 수동 검토. 신규 제안을 승인으로 처리하지 않는다.
- 확인 필요: 4주 unlimited 법적 분류/잔여기간 반환·공제 기준, Store별 실제 실행 권한·지원 범위와 중복 환불 방지/원장 수렴. 법적 요청에 부분 자동 환불 미구현을 거절 근거로 사용하지 않는다. 추가 운영 경로 구현은 별도 승인 범위다.
- 유지: 이미 확정한 환불 후 차단, 선행 GRADING 완료/COMPLETED 보존, D2-A, RC 자동 refund handling 출시 OFF, 무료권 보존. 사용자 동의/정확성/지원 검증 없는 소비 데이터 외부 제출 없음.
- 변경 파일: CURRENT_STATE와 WORKLOG 기록만. 기존 미커밋 변경 보존, 예상 밖 변경 없음. 코드·ADR 정책·Jira·타 서버·Git 이력·배포·외부 설정 변경 없음.
- 검증: git diff --check. 분석/기록만 변경하여 Gradle 미실행. 다음 작업: 운영 선택 승인 및 법적/Store 실행 검증 후 구체 고지·운영 runbook·결제 계획에 반영.


## 2026-09-08 다우기술 노션 포트폴리오 토선생 소재 선별

- 브랜치: develop. 신규 Jira 없음.
- 목표: 사용자 요청에 따라 토선생 상세 페이지에 넣을 내용을 선별.
- 조사: 기존 포트폴리오 소재·트러블슈팅 문서, 각 서비스 현재 상태와 Learning Core 복구·Saga 테스트 및 MDC 구현을 대조.
- 결정: 빈 종합 피드백과 선택적 채점 복구, 시험 생성·사용권 Reservation Saga를 대표 사례로 추천하고 구조화 로그를 보조 사례로 제안. 인증·저장 모델·S3는 구조 설명에 배치.
- 구분: 구현 및 과거 테스트 기록은 운영 활성화 증거와 다르며 실제 결제·환불은 현재 구현 성과로 사용하지 않음. 처리량·비용·장애 감소 수치 미측정.
- 변경 파일: 이 저장소의 docs/codex/WORKLOG.md와 CURRENT_STATE.md에 조사 기록만 추가. 기존 미커밋 작업 보존.
- 검증: 소스·테스트 정적 조회. 애플리케이션 변경이 없어 Gradle 테스트는 재실행하지 않음.
- 유지: API·AI 계약·feature flag·코드·외부 서비스·Git 이력 변경 없음.
- 다음: 본인 역할과 사례별 설명 가능 범위 확인 후 노션 본문 작성.

## 2026-09-08 — 4주권 하루 이용 후 잔여기간 반환 법령·Store 실행 조사

- 날짜·브랜치: 2026-09-08, develop. Jira 없음. 목표: 잔여기간 반환 의무·공제식·Store 부분 환불 지원과 Billing 원장 보완 범위 확인.
- 공식 근거: 전자상거래법 제17~19조, 콘텐츠이용자 보호지침, Google Console/Android Publisher orders.refund, Apple API 1.19+ refundPreference/ConsumptionRequest, RevenueCat Refund Control/Handling Refunds 공개 본문.
- 결과: 사용 개시만으로 미제공 27일의 반환 배제를 단정할 수 없다. 토선생 법적 분류·정확 공제식은 미확정. 일할 반환·추가 위약금 0은 미승인 제안이며 지침 제25조의 10%는 조건부 잔여대금 기준 권고로 일반 청약철회에 일괄 적용하지 않는다.
- 기술 결과: Google Console은 인앱 부분 환불 지원, 확인한 orders.refund endpoint는 부분 금액 인자 없음. Apple consumable도 GRANT_PRORATED·사용률 권고 가능하나 Apple 최종 심사. RC는 부분 선호/사용률 전송 미지원이고 Google 철회 없는 환불 탐지 제한을 안내하여 자동 반영을 보장할 수 없다.
- 변경 파일: docs/contracts/REFUND_REMAINDER_RESEARCH-2026-09-08.md 신규, CURRENT_STATE.md와 WORKLOG.md 기록. 기존 미커밋/동시 작업 기록 보존. 승인 ADR·코드·다른 서버·Jira·Git 이력·설정·배포 변경 없음, 예상 밖 변경 없음.
- 유지: 무료권/완료 기록 보존, append-only 원장, provider 검증, RC 자동 refund handling OFF. 부분 환불 범위 확대·외부 송금·사용정보 전송을 임의 승인하지 않는다.
- 검증: 공식 본문과 ADR-004 §5.7/§5.9 대조, 예시 28000×648/672=27000 산술 및 git diff --check. 조사/문서만 변경하여 Gradle 미실행. 실제 Store/RC 프로젝트·sandbox/환불 event·법률 자문은 미실시.
- 위험·다음 작업: 실제 상품 분류/철회와 해지 기준·법정 환급 기산점 확인, RC 지원 문의와 Store sandbox 증거 검증 후 부분 금액/누적 환불·접수/효력/정산 시점·운영 증빙 경로를 별도 ADR/PLAN 승인으로 보완한다. 시스템 미구현을 법정 요청 거절 사유로 삼지 않는다.

## 2026-09-08 — 환불 관련 법적 분류의 의미 설명

- 날짜·브랜치: 2026-09-08, develop. Jira 없음. 목표: 사용자가 확인해야 할 분류 대상과 이유를 쉬운 말로 설명.
- 근거·결론: REFUND_REMAINDER_RESEARCH-2026-09-08.md 재사용. 실제 판매 대상, 가분적 미제공 기간, 계속거래 요건, 교육 관련 별도 규정 적용을 구분한다. Store consumable·사업자 업종명과 법적 분류는 동일하지 않으며 분류들은 상호 배타적 선택지가 아니다.
- 변경 파일·동작: CURRENT_STATE.md와 WORKLOG.md 설명 기록만. 기존 변경 보존, 예상 밖 변경 없음. 계약·코드·타 서버·Jira·Git 이력·외부 설정·배포 변경 없음.
- 검증: 기존 조사 문서 대조와 git diff --check. 추가 외부 법령 조회 및 Gradle 테스트는 설명/기록 작업이라 수행하지 않음.
- 유지·위험·다음: 28일 unlimited·무료 보존·환불 차단 계약 유지. 실제 약관/서비스 제공 사실과 사업 형태로 적용 기준을 확인해야 하며 이번 설명을 법적 분류 확정이나 부분 환불 승인으로 간주하지 않는다.

## 2026-09-08 — 법률팀 없는 환불 분류 검토의 필요 정보 안내

- 날짜·브랜치: 2026-09-08, develop. Jira 없음. 목표: 환불 분류·사용분 계산 검토에 필요한 사실과 자료를 구체화.
- 근거·동작: 기존 잔여기간 조사와 승인 제품 계약 기반. 확인된 28일 unlimited/일회성/stacking은 재질문하지 않고 강사·강의/교육시설 여부, 만료 후 콘텐츠 접근, 사업 형태/판매 국가/연령, 가격/할인, 약관/구매 화면/동의, 환불 효력 시점의 추가 확인 필요성을 안내한다.
- 결정·유지: 자료 수집은 분류 확정이나 새 환불 정책 승인이 아니다. 법무팀 대신 단건 외부 자문/기관 상담에 같은 사실 요약을 전달하고 법적 판단과 Store/RC 실행 검증을 분리한다. 불필요한 사업자번호·개인정보·결제 원문 수집 없음.
- 변경 파일: CURRENT_STATE.md와 WORKLOG.md 기록만. 기존 변경 보존, 예상 밖 변경 없음. 코드·계약·다른 서버·외부 설정·Jira·Git 이력·배포 변경 없음.
- 검증: 기존 근거 대조, git diff --check. 설명/기록만으로 외부 법령 재조회·Gradle 미실행.
- 위험·다음: 답변만으로 법적 적합성을 보장하지 않는다. 실제 운영 사실·약관 초안을 확보한 뒤 쟁점별 검토와 실행 경로 검증을 거쳐 별도 승인한다.

## 2026-09-08 — AI 시험 서비스 사업·제공 형태 사용자 확인 기록

- 날짜·브랜치: 2026-09-08, develop. Jira 없음. 목표: 환불 분류 상담에 필요한 사용자 확인 사실과 잠정안을 구분.
- 확인: AI 시험·채점만, 강사/과정/진도/수료 없음, 외부 파일 보유 불가. 만료 후 기존 결과 열람은 변경 가능한 잠정안. 개인사업자·IT 업종 설명, 교육시설 등록 없음, 최초 한국 성인 대상. 등록증·연령 제한 구현·배포 독립 검증 아님.
- 변경 파일: REFUND_REMAINDER_RESEARCH-2026-09-08.md §2.0, CURRENT_STATE.md, WORKLOG.md. 기간형 온라인 서비스라는 분석과 미확정 법적 분류·가격/고지/해지 조건을 분리했다.
- 유지: 기존 완료 기록 보존·탈퇴 privacy·무료권·환불 차단 정책 유지. 결과 열람 잠정안을 보존 계약 변경이나 부분 환불 승인으로 해석하지 않음. 코드·외부 설정·타 서버·Jira·Git 이력·배포 변경 없음. 기존 변경 보존, 예상 밖 변경 없음.
- 검증: 사용자 답변과 기존 조사 대조, git diff --check. 사실 기록만으로 외부 법령 재조회·Gradle 미실행.
- 위험·다음: IT 업종/미등록만으로 교육 관련 법령 적용 제외를 단정하지 않는다. 구매가격·할인/화면·약관·환불 효력 시점 수집 후 단건 검토용 자료를 완성한다.

## 2026-09-08 — 기간권 가격 기록 및 Store 중심 환불 창구 설명

- 날짜·브랜치: 2026-09-08, develop. Jira 없음. 목표: 사용자 제시 가격 기록과 앱 자체 환불 접수 필요 여부 설명.
- 사실·제안: 앞서 정한 상품 순서에 가격 9000/19000/29000/49000/69000원을 연결했다. 할인·세금·Store 등록은 미확인. 구매 화면/약관은 기간·기산점·자동 갱신 없음·환불/문의 안내를 포함하는 초안부터 작성 가능하다고 설명한다.
- 환불 구분: Store 기본 접수·환급과 개발자의 장애/법적 요청 고객지원을 분리. 자체 환불 API/UI가 반드시 필요한 것은 아니며 안내 링크·이메일 등 가능. Store 자동 승인 보장이나 법적 책임 전부 이전으로 해석하지 않고 검증된 결과의 Billing 원장/권리 반영은 유지한다.
- 변경 파일: REFUND_REMAINDER_RESEARCH-2026-09-08.md, CURRENT_STATE.md, WORKLOG.md. 기존 변경 보존, 예상 밖 변경 없음. 코드·승인 ADR·외부 설정·다른 서버·Jira·Git 이력·배포 변경 없음.
- 검증: 기존 공식 조사 및 사용자 답변 대조, git diff --check. 설명/기록만이므로 외부 재조회·Gradle 미실행.
- 위험·다음: Store/RC 부분 반환 증거 경로와 법적 처리 기한 검증은 남는다. 자체 신청 API 불필요 설명을 부분 환불 미지원 상태의 출시 승인으로 간주하지 않는다. 구매 고지 초안·지원 연락 수단과 할인 여부를 후속 정리한다.

## 2026-09-09 — Store 중심 환불 창구 승인 반영·구매 안내 초안 작성

- 날짜·브랜치: 2026-09-09, develop. Jira 없음. 목표: 사용자 승인한 Store 환불+토선생 고객지원 방향을 계약에 반영하고 구매·환불 안내 초안 작성.
- 확정: C9-S10에 Store 기본 신청/심사/환급, 토선생 권리 미반영·장애·법적 요청 지원, 최초 자체 신청 API/심사 UI/직접 송금 제외를 명시했다. 사용자 제시 1/3/7/14/28일 국내 가격과 한국 성인 대상 기록을 연결했다.
- 변경 파일: CONTRACT_DECISIONS.md, FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md, ADR-004, 신규 PREMIUM_PURCHASE_REFUND_NOTICE_DRAFT.md, CURRENT_STATE.md, WORKLOG.md. 기존 미커밋 변경은 보존했으며 이번 변경 범위 밖 파일은 추가 수정하지 않음.
- 안내 초안: 구매 기준 기간 시작/stacking·자동 갱신 없음·무료 보존·가격·환불 안내·고객지원·결제 확인 중 문구와 게시 gate. 미정 연락처는 명시 placeholder, 만료 뒤 결과 영구 열람이나 자동 환불/법정 권리 포기 약속 없음.
- 유지 계약: public payment 기존 4 route·internal SigV4/JWT·무료/owner·D1/D2·append-only ledger·검증된 최종 상태 반영·RC 자동 refund handling OFF 유지. 부분 환불 계산/자동화와 법적 분류는 승인으로 확대하지 않음.
- 검증: git diff --check, 신규 문서 whitespace와 로컬 Markdown 링크 존재 확인. 문서 전용으로 Gradle 미실행. 코드·schema·외부 Store/RC 설정·타 서버·Jira·Git 이력·배포 변경 없음. 예상 밖 변경 없음.
- 위험·다음 작업: 실제 지원 채널·담당자/가격 설정·세금/할인, 고지/동의, 결과 열람 범위, 법적 반환 기준·부분 환불 실제 실행/원장 경로를 확인해야 한다. 이 승인은 최종 약관 게시·결제 구현·판매 gate 해제가 아니다. 후속 결제 PLAN은 별도 승인한다.

## 2026-09-09 — 남은 사용자 결정과 출시 검증 분리 안내

- 날짜·브랜치: 2026-09-09, develop. Jira 없음. 목표: 결제 방향 승인 이후 사용자 선택을 불필요하게 반복하지 않고 잔여 사항 안내.
- 근거: ADR-004 C9-S10/출시 입력/G1~G8, 구매·환불 안내 초안의 미정 항목. 이미 승인된 상품/가격/무료 보존/Store 중심 환불은 유지한다.
- 안내: 실제 지원 채널·담당자, 정상 만료 후 결과 열람, 출시 할인 여부가 남은 운영 선택이다. 결과 열람 유지와 별도 할인 없이 출시를 신규 권장안으로 제시하되 확정하지 않는다. 법적 반환/고지·공제와 Store/RC 지원은 임의 선택이 아닌 검토/검증이다.
- 변경 파일: CURRENT_STATE.md와 WORKLOG.md 기록만. 기존 변경 보존, 예상 밖 변경 없음. 코드·승인 계약·Jira·외부 설정·타 서버·Git 이력·배포 변경 없음.
- 검증: 문서 대조, git diff --check. 설명/기록으로 Gradle 및 외부 정책 재조회 미실행.
- 위험·다음: 운영 선택 없이도 결제 PLAN/고지 초안 작성은 가능하지만 별도 승인 전 구현하지 않는다. 결제 판매 전 법적/Store gate와 연락처·상품/가격·sandbox 검증을 완료해야 하며 미확정 부분 환불을 승인으로 간주하지 않는다.

## 2026-09-09 — C9-S11 운영 확정 및 실제 결제 준비 상태 점검

- 날짜·브랜치: 2026-09-09, develop. Jira 없음. 목표: 지정 고객지원 이메일·만료 후 결과 열람·별도 할인 없음 반영 및 법령/Store/RC/코드 잔여 검증.
- 변경 파일: CONTRACT_DECISIONS.md C9-S11, FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md, ADR-004, PREMIUM_PURCHASE_REFUND_NOTICE_DRAFT.md, REFUND_REMAINDER_RESEARCH-2026-09-08.md 후속 참고, 신규 PAYMENT_READINESS_REVIEW-2026-09-09.md, CURRENT_STATE.md, WORKLOG.md.
- 확정 동작: 지원 메일 안내, 본인 계정·기존 보존 정책 범위의 정상 만료 뒤 결과/피드백 열람, 최초 별도 할인 없음. 무기한 보관·탈퇴 기록 복원·새 유료 시험/임의 재채점 권한으로 확대하지 않고 D1/승인 장애 복구 유지.
- 실제 RC 확인: Apple credential 2개 Valid와 알림 correctly configured/No notifications received, S2S tracking OFF·Refund Control default Do not respond. Google credential Valid와 Pub/Sub 접근 오류 동시 관찰/RTDN 미연결. Store Products(All) 비어 있음, default Offering Test Store 3상품, Billing webhook 미등록, 계정 이메일 미인증. 실제 Key/Issuer/알림 URL·거래/개인 계정 정보는 기록하지 않음.
- Store 조회 한계: Apple 인증 실패 로그인 화면, Google 현재 계정 개발자 가입 안내로 실제 상품/가격/세금/심사·한국 설정 미확인. 계정 전환/가입·Resend·role/키 변경·연결/저장·결제/환불·문의 발송 없음.
- 법령: 제17~18조 재확인, 시행령 제21조의2 시험 사용/제24조 사용분 비용 범위 추가 확인. 69,000원의 잔여27일 단순 비례 산술은 참고일 뿐 공제식/법적 분류 확정 아님. 실제 부분 반환/증거 경로와 개별 자문 필요성을 분리.
- 코드: Billing 결제 application/schema v5 미구현 확인. Identity 현 로컬 Billing audience/read 발급과 구매 scope 미발견, LC owner 기반 history 조회 및 paid revoke 미발견 확인. 타 서버는 읽기만 수행하고 AWS 실배포 상태로 해석하지 않음.
- 검증: 공식 본문·RC 실제 UI·로컬 소스 대조, git diff --check 및 신규 문서/Markdown 링크 검사. 코드 변경 없어서 Gradle 미실행, Store sandbox/E2E 미수행. 기존 미커밋 변경 보존, 예상 밖 변경 없음.
- 다음: RC 가입 이메일 확인·해당 GCP 프로젝트 API/IAM 확인·실제 Store 로그인/상품 구성 확인, 별도 결제 PLAN/상대 서비스 인계와 법률 검토. 이번 확인은 자동 설정 수정·기능 구현·판매 승인 아님.

## 2026-09-10 — Google Cloud 결제 화면과 Pub/Sub 설정 구분 안내

- 날짜·브랜치: 2026-09-10, develop. Jira 없음. 목표: 사용자가 문의한 이전 Google 설정 필요사항 재안내.
- 근거: PAYMENT_READINESS_REVIEW-2026-09-09.md. 당시 credential Valid와 별도 Pub/Sub 접근 오류/RTDN 미연결을 확인했으며 이번 실제 계정 상태는 재조회하지 않았다.
- 안내: Cloud Billing은 결제계정 관리, 이번 직접 점검은 기존 service account와 동일 프로젝트의 Pub/Sub API 활성·IAM Pub/Sub Editor/Monitoring Viewer 및 후속 RevenueCat/Play RTDN 연결이다. 이미 준 권한을 중복/상위 부여하거나 무조건 키를 새로 만들지 않는다.
- 변경 파일: CURRENT_STATE.md와 WORKLOG.md 기록만. 결제 계정 식별자/URL 원문은 기록하지 않았다. 코드·외부 설정·Jira·타 서버·Git 이력·배포 변경 없음, 기존 변경 보존·예상 밖 변경 없음.
- 검증: 기존 점검 근거 대조와 git diff --check. 안내만으로 Gradle 미실행. 다음은 동일 프로젝트 API/IAM 현재 상태 확인이며 카드 등록·결제계정 신규 생성은 요청하지 않는다.

## 2026-09-10 — 카드 등록과 프로젝트 Billing 연결 구분

- 날짜·브랜치: 2026-09-10, develop. Jira 없음. 목표: 사용자가 기억한 카드 등록 선행 작업의 의미 설명.
- 근거·분석: 기존 대화/9월 9일 실제 RC 화면에는 MTR 임계치 초과 대비 결제수단 안내가 있었다. 현재 사용자 Cloud Billing 링크에 대해서는 결제수단 등록과 프로젝트 결제계정 연결이 별도임을 설명한다. 과거 프로젝트 연결을 실제로 지시했거나 현 프로젝트 결제가 비활성이라고 단정하지 않는다.
- 안내: RevenueCat service account 소속 프로젝트에서 결제 메뉴의 활성/연결 상태 확인, 이미 활성이라면 재등록하지 않음. Pub/Sub API·IAM/RTDN은 별도 점검. 우리 Billing 서버 코드 변경과도 구분한다.
- 변경 파일: CURRENT_STATE.md·WORKLOG.md 기록만. 계정 ID/결제수단 정보 저장 없음. 코드·외부 금융/설정·권한·Jira·Git 이력·배포 변경 없음, 기존 변경 보존·예상 밖 변경 없음.
- 검증: 기존 기록 대조·git diff --check, 설명/기록 작업으로 Gradle/실제 콘솔 재조회 미실행. 다음은 올바른 프로젝트 결제 상태 확인이다.

## 2026-09-10 — Google Cloud 무료 체험 화면 인계

- 날짜·브랜치: 2026-09-10, develop. Jira 없음. 목표: 사용자가 기억한 무료 크레딧 신청 화면 열기 및 실제 조건 구분.
- 동작·근거: https://console.cloud.google.com/freetrial 로 이동해 무료 체험 계정 정보/계정 선택 화면 확인. 화면에 $300 크레딧·90일 사용과 일반 계정 활성화 또는 선불 선택 시 요금 청구 안내가 있다. 약 7만원 선결제·90일 뒤 현금 환불은 현재 화면에서 확인되지 않아 미확인으로 남긴다.
- 변경 파일: CURRENT_STATE.md·WORKLOG.md 기록만. 계정 개인정보·결제 식별자 기록 없음. 약관 동의·결제·업그레이드·외부 설정·코드·타 서버·Jira·Git 이력·배포 변경 없음. 기존 변경 보존·예상 밖 변경 없음.
- 검증: 실제 브라우저 DOM 확인 및 git diff --check. 화면 안내/문서 기록으로 Gradle 미실행. 무료 크레딧 사용기간을 현금 환불 기한으로 해석하지 않으며 기존 서비스 계약은 유지한다.
- 위험·다음: 올바른 Google 계정 선택과 결제 단계 조건 확인은 사용자에게 인계했다. 실제 선불/인증 결제 금액·환급 조건·기존 결제계정 연결 상태는 미검증이다.

## 2026-09-10 — Chrome에서 Google Cloud 링크 접근 안내

- 날짜·브랜치: 2026-09-10, develop. Jira 없음. 목표: 복사한 콘솔 주소가 Chrome에서 열리지 않는 상황 안내.
- 분석·동작: 제공된 현재 탭은 Billing 화면이다. authuser는 브라우저별 로그인 순번이라 다른 계정을 선택할 수 있으며 로그인 세션도 공유되지 않는다. 계정 지정 없는 Billing/무료 체험 주소와 수동 계정 선택을 안내하고 실제 Chrome 오류 확인 전 원인은 확정하지 않는다.
- 변경 파일: CURRENT_STATE.md·WORKLOG.md. 기존 변경 보존·예상 밖 변경 없음. 코드·외부 설정·금융 작업·계약·Jira·Git 이력 변경 없음.
- 검증: 제공된 UI 문맥과 URL 구조 확인, git diff --check. 안내/기록 작업으로 Gradle 미실행. 다음: 올바른 계정에서도 접근 실패 시 화면 오류 문구 확인. 결제계정 식별자나 개인 계정 정보는 기록하지 않음.

## 2026-09-10 — Google Cloud 기타 사용료 신청 문구 작성

- 날짜·브랜치: 2026-09-10, develop. Jira 없음. 목표: 사용자 제공 활동비 양식에 맞는 복사 가능한 초안 작성.
- 동작: 프로젝트 활동비/기타, 신청일 제목, Google Cloud 사용료·카드결제 예정, 짧은 세부사항, 수량 1건 및 Pub/Sub·RevenueCat 기반 결제 알림 연동의 프로젝트 관련 구매사유를 제안했다. 실제 금액·통화는 결제 화면 기준 placeholder이며 선불/인증금/환불 조건은 확정하지 않았다.
- 변경 파일: CURRENT_STATE.md·WORKLOG.md 기록만. 기존 변경 보존·예상 밖 변경 없음. 신청 제출·결제·첨부 업로드·외부 설정·코드·계약·Jira·Git 이력 변경 없음.
- 검증: 사용자 양식의 세부사항 128자 이내·구매사유 공백 제외 30자 이상을 충족하는 문구 작성 및 git diff --check. 문안 작업으로 Gradle 미실행.
- 위험·다음: 무료 크레딧을 실제 지출액으로 신청하지 않는다. 실제 청구 화면에서 금액·통화·거래 성격을 확인하고 선불금/환급성 인증금이면 담당자에게 지원 가능 여부 확인 후 제출한다.

## 2026-09-10 — 활동비 신청 Firebase 용도 추가

- 날짜·브랜치: 2026-09-10, develop. Jira 없음. 목표: 사용자가 추가한 Firebase 이용 목적을 신청 문구에 반영.
- 변경 파일·동작: CURRENT_STATE.md·WORKLOG.md 기록, 응답의 세부사항과 구매사유에 Firebase 추가. 구체적인 Firebase 기능이나 유료 요금제·필수 지출은 추정하지 않음.
- 검증: 세부사항 128자 이내·구매사유 공백 제외 30자 이상 문구 확인, git diff --check. 문구 보완만으로 Gradle 미실행.
- 유지·위험·다음: 기존 계약·코드·외부 설정·신청 제출·결제 변경 없음. 기존 변경 보존·예상 밖 변경 없음. 실제 금액·통화·선불/인증금 성격 확인 후 사용자 제출 필요.

## 2026-09-16 — Apple/Google 기간권 상품 등록 접근 확인 및 인계

- 날짜·브랜치: 2026-09-16, develop. Jira 없음. 목표: 사용자 요청한 양 Store의 승인된 일회성 이용권 5종 등록.
- 근거: AGENTS.md, ADR-004, FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md, PREMIUM_PURCHASE_REFUND_NOTICE_DRAFT.md. 1/3/7/14/28일과 9,000/19,000/29,000/49,000/69,000원, Apple consumable·Google one-time consumable, 자동갱신 없음 유지. 실제 product ID는 아직 미확정이며 문서 예제를 등록값으로 사용하지 않았다.
- 실제 확인: 사용자 링크로 인앱 브라우저 접근. Apple은 인증 실패 후 로그인 화면. Google은 브라우저 로그인 순번 차이로 약관 검토 화면으로 이동했고 기존 토선생 계정을 선택한 뒤에도 동일 화면이었다. 이를 실제 개발자 계정 미보유나 상품 미존재 증거로 해석하지 않는다.
- 수행 범위: 페이지 열기·기존 Google 계정 선택·로그인/약관 화면 인계만. 약관 동의·상품 생성·가격 저장·심사/출시 제출·판매 활성화·RevenueCat 변경 없음. 상품 생성 건수는 0건이며 완료로 보고하지 않는다.
- 변경 파일: CURRENT_STATE.md·WORKLOG.md. 기존 미커밋 변경 보존, 예상 밖 변경 없음. 코드·다른 서버·계약·Jira·Git 이력·배포 변경 없음. 개인정보·credential·개발자 계정 식별자 기록 없음.
- 검증: 실제 UI 상태 확인 및 git diff --check. 코드 변경이 없어 Gradle 미실행. Store 상품/금액 저장 검증과 sandbox E2E는 수행하지 못했다.
- 위험·다음: 사용자가 올바른 개발자 계정으로 로그인하고 필요한 약관을 검토한 뒤 앱/상품 목록에서 대상·중복을 먼저 확인한다. 상품 ID·표시명·국내 가격을 등록하고 저장 상태를 검증하되 백엔드/Store gate 미완료 상태에서 임의 판매 활성화하지 않는다.

## 2026-09-16 — Apple 이용권 5종 생성·한국 한정 설정 및 Google 차단 확인

- 날짜·브랜치: 2026-09-16, develop. Jira 없음. 목표: 사용자 로그인 완료 후 기존 상품 등록 요청 계속 수행, 도중 추가된 한국 판매 지역 지정 반영.
- Apple 실제 변경: 기존 상품 부재 확인 후 소모품 `premium1d/premium3d/premium7d/premium14d/premium28d` 생성. 한국어 ‘토선생 프리미엄 1일/3일/1주/2주/4주 이용권’, 24/72/168/336/672시간·AI 피드백 무제한·자동갱신 없음 설명 저장. KRW 9000/19000/29000/49000/69000 저장. 사용 가능 국가 전체 해제 후 대한민국만 지정하고 메인 저장.
- 검증: 각 상품 ‘현재 가격’에서 한국 금액 재확인, 지역 지정 때 대한민국만 체크/1개 선택 확인, 최종 목록 초안 5개·소모품·제출 준비 중 확인. 최초 생성 및 저장 중 상태와 최종 결과를 구분했다. 심사 추가/제출·출시·약관 동의·심사 이미지 업로드는 하지 않음.
- Google 실제 확인: 해당 앱 일회성 제품 화면에 결제 권한을 APK에 추가해야 한다는 안내와 새 APK 업로드 버튼만 표시돼 0건 생성. Android 코드·빌드 업로드는 범위 밖으로 미실행. 기존 로그인 차단은 해소됐으나 빌드 선행 조건이 남음.
- 변경 파일: 신규 STORE_PRODUCT_REGISTRATION-2026-09-16.md, FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md 후속 링크, CURRENT_STATE.md, WORKLOG.md. 기존 사용자 미커밋 변경 보존·예상 밖 변경 없음. 앱의 기존 판매 메타데이터·세금 카테고리·타 서버·RevenueCat·Jira·Git 이력·배포는 변경하지 않음.
- 유지 계약: 기간형 일회성 consumable·무료 보존·승인 가격·한국 대상·Billing duration source of truth 유지. 실제 결제/무료권 지급 없음. 가격표의 해외 자동환산을 해외 판매 승인으로 해석하지 않음.
- 로컬 검증: git diff --check. 코드 변경 없어 Gradle 미실행. sandbox/RevenueCat/Billing E2E 미실행. 외부 생성 사실을 로컬 코드 구현 완료로 보고하지 않음.
- 위험·다음: Google 결제 권한 포함 Android 빌드 업로드 후 5종 등록, Apple 계약 갱신/심사 스크린샷·새 앱 버전, RevenueCat 매핑 및 별도 결제 PLAN/구현·sandbox gate 필요. 상품 ID 생성과 심사/판매 완료를 구분한다. 사용자/credential·거래 원문은 문서에 기록하지 않음.

## 2026-09-16 — Android 결제 권한 포함 빌드 의미 설명

- 날짜·브랜치: 2026-09-16, develop. Jira 없음. 목표: Google 상품 생성 선행 조건을 앱 개발 관점에서 설명.
- 설명: `com.android.vending.BILLING` manifest 선언과 RevenueCat/Play Billing 의존성의 manifest 병합, 새 versionCode의 AAB/APK 업로드·Play 처리 확인을 구분했다. 사용자 런타임 권한 요청·자동 결제 허용·Billing 백엔드 배포가 아니며 상품 등록과 구매 UI/검증 구현 완료도 별개다. 내부 테스트 트랙을 통한 준비를 안내하되 즉시 production 출시를 요청하지 않는다.
- 변경 파일: CURRENT_STATE.md·WORKLOG.md 기록만. 앱 저장소/최종 빌드는 미검사이므로 실제 원인을 특정 소스 파일 누락으로 단정하지 않음. 기존 상품·결제·무료 계약 유지, 코드·외부 설정·업로드·배포·Jira·Git 이력 변경 없음. 기존 변경 보존·예상 밖 변경 없음.
- 검증: 직전 Google UI 안내와 등록 기록 대조, git diff --check. 설명만이므로 Gradle 미실행. 다음: 앱 담당자가 SDK/merged manifest·versionCode 확인 후 승인된 테스트 배포 절차로 업로드하고 상품 등록 가능 여부 재확인.

## 2026-09-16 — Apple 상품 생성과 심사 제출 상태 구분

- 날짜·브랜치: 2026-09-16, develop. Jira 없음. 목표: 상품만 생성했고 심사는 미제출인지 사용자 확인에 답변.
- 근거·동작: 직전 실제 확인 및 STORE_PRODUCT_REGISTRATION-2026-09-16.md 기준 5종 초안/제출 준비 중, 이름·설명·가격·한국 지역 저장 완료. 심사 추가/제출·판매 개시 미수행임을 확인했다. 콘솔 재조회 없이 마지막 확인 상태로 설명.
- 변경 파일: CURRENT_STATE.md·WORKLOG.md 기록만. 코드·외부 설정·계약·Jira·Git 이력·배포 변경 없음, 기존 변경 보존·예상 밖 변경 없음.
- 검증: 기존 등록 근거 대조와 git diff --check. 설명 작업으로 Gradle 미실행. 다음: RevenueCat·앱/백엔드 연동·sandbox 검증 및 심사 자료 준비 후 별도 요청 시 새 앱 버전과 첫 IAP 심사 제출. 신규 상품/심사 승인으로 확대하지 않음.

## 2026-09-16 — Apple 즉시 심사 제출 선행 조건 안내

- 날짜·브랜치: 2026-09-16, develop. Jira 없음. 목표: 현재 상품 등록만으로 바로 제출해도 되는지 설명.
- 근거·동작: STORE_PRODUCT_REGISTRATION-2026-09-16.md의 미완료 조건 기준으로 RevenueCat/앱/Billing 연동, sandbox 구매·지급·환불 검증, 심사 스크린샷/접근 안내·새 앱 버전, 계약·정산 확인을 안내했다. 상품 초안과 동작하는 결제 기능을 구분하고 테스트를 위해 심사 승인을 먼저 받을 필요는 없음을 설명.
- 변경 파일: CURRENT_STATE.md·WORKLOG.md 기록만. 심사 제출/외부 설정·코드·계약·타 서버·Jira·Git 이력·배포 변경 없음. 기존 변경 보존·예상 밖 변경 없음.
- 검증: 등록 기록 대조, git diff --check. 설명만으로 Gradle/Store 테스트 미실행. 위험·다음: 실제 앱 결제 동작은 아직 확인하지 않았으며 연동 및 sandbox gate 이후 별도 요청으로 제출한다. 질문 자체를 심사 실행 승인으로 취급하지 않는다.

## 2026-09-16 — 사용자 RevenueCat 상품·Offering 설정 읽기 검증

- 날짜·브랜치: 2026-09-16, develop. Jira 없음. 목표: 사용자가 직접 완료한 Apple import와 premium Offering 패키지 매핑 확인.
- 경과: 앞선 직접 설정 요청에서는 import 대상 5개 체크까지만 수행한 뒤 사용자의 직접 작업 요청으로 중단했다. 이번에는 완료 주장 대신 실제 저장된 Offering/Products를 확인했으며 외부 쓰기 없음.
- 실제 결과: premium Offering 5개 custom package가 동명의 Apple premium1d/3d/7d/14d/28d와 일대일 연결. Apple Entitlements 열은 모두 Attach, Google 제품 미등록, default 3개 패키지 별도 존재. package 순서는 현재 UI 28/14/7/3/1일이며 순서를 수정하지 않음. Apple Missing Metadata와 이메일 미인증 배너 유지. 기본 Offering 지정 여부/SDK 실제 상품 조회는 미검증.
- 변경 파일: STORE_PRODUCT_REGISTRATION-2026-09-16.md §6.1 실제 RC resource ID 매핑, CURRENT_STATE.md, WORKLOG.md. 기존 미커밋 변경 보존·예상 밖 변경 없음. 코드·계약 정책·타 서버·Jira·Git 이력·배포 변경 없음.
- 검증: 실제 Offering 상세 패키지와 Products의 Entitlement 열 확인, git diff --check. 코드 변경 없어 Gradle 미실행. sandbox/실구매·Billing 지급·환불 E2E 미실행.
- 유지·다음: 기간은 Billing이 관리하고 RC Entitlement에 연결하지 않는 계약 유지. 앱은 premium Offering을 명시 선택해 연동·상품 조회 검증 후 Billing 및 sandbox 구매/환불 검증을 수행한다. 이메일 인증/Apple 심사 metadata 보완과 Google 결제 권한 빌드·상품 등록은 별도다. 사용자 설정을 Codex 생성 결과로 보고하지 않음.

## 2026-09-16 — Apple Missing Metadata 보완 항목 확인

- 날짜·브랜치: 2026-09-16, develop. Jira 없음. 목표: RevenueCat에 표시된 Missing Metadata의 실무 보완 항목 설명.
- 실제 확인: Apple 목록은 5개 초안/제출 준비 중. 1일권 상세에서 한국어 이름·설명, 가격 일정, 1개 판매지역 존재와 심사 정보 스크린샷 미첨부/추가 정보 빈칸 확인. 이번에 다른 4종 상세를 재조회하지 않았으며 스크린샷이 유일한 미충족 조건이라고 단정하지 않음.
- 안내: 앱 실제 구매 화면이 준비되면 해당 상품·기간·가격이 보이는 심사용 캡처를 상품별 등록하고 구매 화면 접근/기간형 소모품 설명을 추가. 선택 사항인 프로모션 이미지와 구분. SDK/Billing 연동·sandbox 검증 후 첫 IAP는 새 버전과 제출하며 지금 외형만 갖춰 제출하지 않음.
- 변경 파일: CURRENT_STATE.md·WORKLOG.md 기록만. 외부 저장/업로드·심사 제출·상품 설정·계약·코드·Jira·Git 이력·배포 변경 없음. 기존 변경 보존·예상 밖 변경 없음.
- 검증: 실제 UI 읽기와 git diff --check. 설명 작업으로 Gradle/실구매 테스트 미실행. 다음: 실제 결제 UI·동작 준비, 심사 스크린샷/접근 정보 보완 후 Apple 및 RevenueCat 상태 확인.

## 2026-09-16 — RevenueCat Google Pub/Sub 권한 오류 진단

- 날짜·브랜치: 2026-09-16, develop. Jira 없음. 목표: 사용자 요청에 따라 Google Cloud·RevenueCat을 읽고 수정 대상 확인.
- 확인 사실: RevenueCat credential 프로젝트와 동일한 Cloud 프로젝트의 enabled APIs에 Google Play Android Developer API, Google Play Developer Reporting API, Cloud Pub/Sub API가 모두 있다. 프로젝트 IAM의 RevenueCat용 서비스 계정은 모니터링 뷰어와 Pub/Sub 라이트 편집자만 표시된다. Pub/Sub API 대시보드는 최근 1일 요청 3건/오류율 100%를 표시하지만 이 집계만으로 개별 오류 원인을 단정하지 않는다.
- RevenueCat: Play 구매 검증·인앱 카탈로그·구독 카탈로그 검증은 Valid credentials. Google developer notifications는 미연결이고 topic 선택지는 No options. 이번 로드에서 사용자 제보 오류 문구 자체는 보이지 않았으며 Client Email 필드도 빈 값이라 업로드 credential의 principal 직접 대조는 미완료다. 프로젝트 일치와 해당 프로젝트 IAM의 역할 누락은 확인했다.
- 분석·권장: 공식 https://www.revenuecat.com/docs/service-credentials/creating-play-service-credentials 의 일반 Pub/Sub Editor(`roles/pubsub.editor`)와 Monitoring Viewer(`roles/monitoring.viewer`) 요구에 비해 Lite 역할은 일반 Pub/Sub 권한을 제공하지 않는다. 해당 서비스 계정에 일반 Pub/Sub 편집자를 보완하고 모니터링 뷰어를 유지한 뒤 RC topic 조회/RTDN을 재검증한다. Lite 제거는 다른 용도 확인 후 별도 판단하며 관리자 역할 확대·JSON 키 재생성을 우선 조치로 권하지 않는다.
- 수행 경계: 화면 조회와 Google 기존 계정/프로젝트 선택만. API 활성·IAM 변경·topic 생성·RC Connect/Save·키 발급/업로드·Play 설정·실구매 없음. Firebase 별도 프로젝트와 혼동하지 않는다. topic 실존 여부와 권한 보완 후 성공은 아직 검증하지 않았다.
- 변경 파일: CURRENT_STATE.md·WORKLOG.md만 기록. 기존 미커밋 변경 보존·예상 밖 변경 없음. 코드·계약·다른 서버·Jira·Git 이력·배포 변경 없음. credential/키/계정 식별자/결제 원문 미기록.
- 검증·다음: 실제 UI 및 공식 가이드 대조, git diff --check. 코드 변경이 없어 Gradle 미실행. 사용자 승인 후 IAM 역할 보완·RC topic 재조회, 이후 Play 실시간 알림 연결/테스트가 필요하며 Google 상품 생성용 Billing 권한 빌드 문제와는 별도다.

## 2026-09-16 — 승인된 Google Pub/Sub Editor 역할 추가

- 날짜·브랜치: 2026-09-16, develop. Jira 없음. 목표: 직전 진단의 권한 누락을 사용자 명시 승인으로 수정.
- 실제 변경: 대상 프로젝트 IAM의 RevenueCat 서비스 계정 행을 다시 확인하고 정확한 `roles/pubsub.editor` 검색 결과인 게시/구독 편집자를 추가·저장했다. 기존 모니터링 뷰어·Pub/Sub 라이트 편집자는 유지했고 개인 계정 역할은 변경하지 않았다.
- 검증: Google 정책 업데이트 완료 알림과 저장된 서비스 계정 역할 3개를 확인했다. RC 새 페이지에서 Valid credentials 및 Google developer notifications의 `Play-Store-Notifications — Will be generated by RevenueCat` 옵션 표시를 확인했다. 이전 No options와 달라졌으나 실제 topic 생성/연결 완료로 해석하지 않는다.
- 범위·유지 계약: IAM 역할 한 개 추가만 외부 변경. 키 발급/업로드·API 변경·RC Connect/Save·topic 생성·Play RTDN 설정·실구매·배포 없음. 상품/무료권/결제 계약·코드·타 서버·Jira·Git 이력은 유지.
- 변경 파일: CURRENT_STATE.md·WORKLOG.md 기록만. 기존 미커밋 변경 보존·예상 밖 변경 없음. credential/키/계정 식별자 기록 없음.
- 테스트·위험·다음: UI 저장/후속 조회 및 git diff --check. 코드 변경이 없어 Gradle 미실행. IAM 전파에는 몇 분 걸릴 수 있다는 콘솔 안내 확인. 별도 승인으로 RC 알림 연결과 Play RTDN 설정/전달 테스트가 필요하며 이번 작업은 알림 종단 검증 완료가 아니다.

## 2026-09-16 — 서비스 계정의 불필요 Pub/Sub Lite 역할 제거

- 날짜·브랜치: 2026-09-16, develop. Jira 없음. 목표: 사용자가 명시 요청한 RevenueCat 서비스 계정 Lite 권한 제거.
- 실제 변경·검증: 같은 서비스 계정 편집 폼에서 역할 3이 Pub/Sub 라이트 편집자인지 확인한 뒤 해당 역할만 삭제. 변경 요약의 삭제 1개/추가 없음 확인 후 저장했고 정책 업데이트 알림과 최종 게시/구독 편집자·모니터링 뷰어 두 역할을 확인했다. 개인 계정 역할은 변경하지 않음.
- 유지·복구: 역할 부여만 제거했으며 서비스 계정·키·topic·데이터 삭제 없음. 필요하면 같은 IAM 역할을 다시 부여할 수 있다. 상품/결제/무료권 계약·코드·RevenueCat/Play 설정·타 서버·Git·Jira·배포 변경 없음.
- 변경 파일·테스트: CURRENT_STATE.md·WORKLOG.md 기록, git diff --check. 코드 변경 없어 Gradle 미실행. 기존 미커밋 변경 보존·예상 밖 변경 없음. 식별자/credential 미기록.
- 위험·다음: IAM 전파에 몇 분이 걸릴 수 있다. 앞선 일반 역할 추가 후 RC 생성 예정 topic 옵션 표시까지 확인했고 실제 RTDN 연결/전달 테스트는 후속으로 남는다.

## 2026-09-16 — 권한 정리 후 후속 작업 순서 안내

- 날짜·브랜치: 2026-09-16, develop. Jira 없음. 목표: 사용자 다음 작업 질문에 현재 완료/미완료 기준 안내.
- 동작·결정: 바로 다음은 RC Google developer notifications topic 선택/Connect, 생성 결과와 Play publisher 권한 확인, Play RTDN topic 등록 및 일회성 상품 알림 포함·테스트 수신 검증이다. 그 뒤 Android 결제 권한 빌드/Google 5종 상품/RC premium 패키지 매핑, 별도 Billing 결제 PLAN·구현·sandbox gate 순서를 설명한다. 테스트 알림 성공을 실제 구매/환불 검증 완료로 취급하지 않는다.
- 변경 파일: CURRENT_STATE.md·WORKLOG.md 기록만. 직전 UI 확인 재사용, 이번 외부 재조회/설정·코드·계약·타 서버·Jira·Git·배포 변경 없음. 기존 변경 보존·예상 밖 변경 없음.
- 테스트·위험·다음: git diff --check. 설명 작업으로 Gradle 미실행. topic 옵션은 생성 예정이지 생성 완료가 아니고 IAM 완료와 RTDN 연결도 별개다. 실행은 별도 사용자 요청 후 진행한다.

## 2026-09-16 — RC Connect 후 topic 생성 권한 오류의 실제 Google 로그 진단

- 날짜·브랜치: 2026-09-16, develop. Jira 없음. 목표: 사용자 Connect 실패 제보의 원인을 read-only 확인.
- 확인 사실: 프로젝트 IAM에 일반 게시/구독 편집자·모니터링 뷰어가 유지됨. RC Play credential은 Valid, 연결은 미완료. Google 로그 탐색기의 최근 CreateTopic 기록은 16:26:37 생성 작업 후 16:26:45 status code 6과 Resource already exists를 표시했다. 후자 authorizationInfo의 `pubsub.topics.create`는 `granted=true`. principal은 앞서 수정한 서비스 계정과 같아 실제 생성 호출의 계정 일치도 확인했다.
- RC 재조회: topic 선택지는 생성 예정이 아닌 실제 전체 경로가 붙은 기존 Play-Store-Notifications로 변경됐다. 실제 topic이 존재하며 확인된 최근 실패는 중복 생성이라는 근거다. 오류 문구만으로 생성 권한 부족/전파 지연/관리자 필요를 단정하지 않는다. 최초 연결의 다른 단계 실패 원인과 이후 연결 성공은 미검증.
- 안내·근거: RC 새로고침 후 기존 topic 선택으로 연결 재시도 권장. https://www.revenuecat.com/docs/service-credentials/creating-play-service-credentials/troubleshooting 도 확인했지만 일반 가이드의 권한 확대/키 재발급 문구보다 이번 실제 로그를 우선하여 불필요한 credential 변경을 하지 않는다.
- 수행 범위·변경 파일: 외부 화면/로그 읽기만, Connect·IAM 변경·키 발급/업로드·topic 생성/삭제·Play 설정 없음. CURRENT_STATE.md·WORKLOG.md만 기록. 기존 미커밋 변경 보존·예상 밖 변경 없음. 코드·계약·타 서버·Jira·Git·배포 변경 없음. 계정/키/프로젝트 식별자·로그 원문은 문서에 복제하지 않음.
- 검증·위험·다음: UI와 감사 로그 대조, git diff --check. 코드 변경 없어 Gradle 미실행. 기존 topic 연결 후 subscription/publisher 권한과 Play RTDN 테스트를 검증해야 하며 이번 진단을 알림 연결 완료로 보고하지 않는다.

## 2026-09-16 — Google 설정과 결제 개발 병행 권고

- 날짜·브랜치: 2026-09-16, develop. Jira 없음. 목표: Google 설정 대기 중 결제 구현 우선 여부 설명.
- 근거: ADR-004 상태·§1~3과 docs/plans 목록을 재확인했다. 제품 정책 승인과 기술 상세 초안/결제 PLAN 부재를 구분하며 코드 전체 재리뷰나 구현 완료 판정은 하지 않았다.
- 안내·결정: Google 설정은 실제 연동 검증의 선행 조건이나 fake provider 기반 Billing 개발을 막지는 않는다. 다음은 실제 RevenueCat 지원/인증/이벤트 매핑 및 public wrapper 차이 검토를 포함한 별도 PLAN 작성·승인이고 이후 catalog/purchase account→거래 검증/기간권 원장→paid-first/무료 보존→환불/revoke/LC 차단 순서로 구현한다. PLAN-008은 현재 목록 기준 후보이지 작성 완료가 아니다.
- 범위·유지: 이번은 안내만. 계획서 신규 작성·구현·Jira 생성·타 서버·외부 설정·Git·배포 없음. 승인된 무료/일회성 기간권 계약 유지. RTDN 오류는 단순 시간 경과 해결로 단정하지 않고 별도 추적한다.
- 변경 파일·검증: CURRENT_STATE.md·WORKLOG.md 기록, git diff --check. 코드 변경 없어 Gradle 미실행. 기존 변경 보존·예상 밖 변경 없음.
- 위험·다음: 실제 Store/RC 필드·webhook 인증 기능은 구현 전 검증, 차이가 있으면 계약 보완 승인 필요. Google 상품/RTDN 및 양 Store sandbox 구매·환불 E2E·배포 gate 전 production flag OFF. 사용자 요청 후 결제 PLAN 작성으로 진행한다.

## 2026-09-22 — Google Ads 프로모션 사용 조건 조회 및 안내

- 날짜·브랜치: 2026-09-22, develop. Jira 없음. 목표: 사용자 제공 Google Ads 프로모션의 현재 상태와 사용하는 방법 확인.
- 실제 확인: 프로모션 등록 완료/추가 지출 요건 미충족, 인정 지출 진행률 약 91%, 광고 계정 가용 잔액 소진. 상세 패널의 인정 지출을 기준으로 부족분을 계산해 사용자에게 안내한다. 지출 기한은 2026-11-03, 크레딧 사용 기한은 적립 후 60일이다. 계정/쿠폰 코드·결제수단·거래 원문은 기록하지 않는다.
- 근거·분석: https://support.google.com/google-ads/answer/6388096 및 /answer/16915411 확인. 충전 자체가 지출 요건 충족은 아니며, 요건 충족 후 자격 확인에 최대 35일, 적립 뒤 향후 광고비에 자동 적용된다. 이전 비용 소급 상계·현금 환급이 아니고 VAT 차감 국가의 선불 결제는 세금을 고려해야 한다. 이 계정의 구체적인 세금 원인은 별도 거래내역을 확인하지 않아 단정하지 않는다.
- 안내·위험: 부족한 인정 광고 지출을 채우려면 현재 소진된 잔액에 사용자 예산 범위의 추가 충전이 필요할 수 있다. 정확 충전액은 세금/현재 집계/결제 화면으로 확인하고 크레딧 적립 전·소진 후 추가 비용이 생길 수 있으므로 필요한 경우 사용자가 캠페인 일시중지로 지출을 통제한다. Google Cloud/Firebase 크레딧과 혼동하지 않는다.
- 범위·변경 파일: UI/공식 도움말 읽기 및 CURRENT_STATE.md·WORKLOG.md 기록만. 충전·쿠폰 등록·광고/예산/결제 설정·정지·문의·약관 동의·코드·계약·타 서버·Jira·Git·배포 변경 없음. 기존 미커밋 변경 보존·예상 밖 변경 없음.
- 검증·다음: 실제 프로모션 상세 상태/산술/공식 조건 확인, git diff --check. 코드 변경 없어 Gradle 미실행. 필요 시 사용자가 추가 지출 예산을 정하고 직접 결제한 뒤 인정 지출과 지급 상태를 확인한다. 금액 소진과 크레딧 지급 완료를 구분한다.

## 2026-09-22 — Google Ads 충전액과 프로모션 인정 광고비 차이 확인

- 날짜·브랜치: 2026-09-22, develop. Jira 없음. 목표: 사용자 질문의 반영 지연/수수료 여부를 결제 내역으로 확인.
- 실제 확인: 결제 요약의 현재 잔액은 사용자 말과 같은 15원. 당월 카드에서 캠페인별 비용, 초과 광고게재 조정, 예상 세금 및 요금의 VAT를 펼쳐 확인했다. 캠페인 합계-조정액=직전 프로모션 인정 광고비이며 여기에 VAT+잔액을 합하면 충전액과 일치한다. 직전 개요의 음수 잔액 표시와 이번 결제 요약을 구분하고 이번 상세를 최신 근거로 안내했다.
- 결론·유지: 차액은 확인된 VAT로 설명되며 단순 반영 지연이나 별도 결제 수수료라고 추측하지 않는다. 프로모션 조건은 세금 포함 충전액이 아닌 인정 광고 지출 기준. 화면상 VAT는 예상 항목이므로 확정 세금계산서와 동일하다고 단정하지 않는다.
- 변경 파일·범위: CURRENT_STATE.md·WORKLOG.md 기록만. 실제 결제/광고/예산/프로모션 설정·문의·코드·계약·타 서버·Jira·Git·배포 변경 없음. 기존 변경 보존·예상 밖 변경 없음. 계정/쿠폰/결제수단 식별자·거래 원문은 저장하지 않음.
- 검증·위험·다음: 실제 결제 요약 및 산술 대조, git diff --check. 코드 변경 없어 Gradle 미실행. 추가 광고비를 지출하기로 하면 사용자가 세금과 조정을 고려해 예산/충전액을 정하고 인정 지출 및 크레딧 지급 상태를 확인한다. 이번은 지출 승인이나 충전 실행이 아니다.

## 2026-09-22 — ADR-004 승인·미확정·검증 대기 분리 리뷰

- 날짜·브랜치: 2026-09-22, develop. Jira 없음. 목표: ADR-004가 전부 확정됐는지 사용자 요청에 따라 확인. 계약 수정/구현 요청으로 확대하지 않음.
- 근거: ADR-004 헤더·§2.2/§3/§4/§5/부록, CONTRACT_DECISIONS C9-S1~S11 및 C1-R1, FIXED_TERM_PREMIUM_PAYMENT_CONTRACT, 구매·환불 고지 초안, 9/9 readiness와 9/16 Store 등록 §6.1, 현재 작업 기록, docs/plans 목록 대조. 외부 Console/공식 provider 지원을 이번에 새로 검증하지 않음.
- 승인 사실: 1/3/7/14/28일·국내 5가격·일회성 consumable·RevenueCat 표준 SDK completion, Billing 기간/원장, 구매 시각 시작·기간 stacking/reflow, ACTIVE MEMBER/opaque reference·다른 계정 자동 이전 금지, 유료 우선/무료 보존, provider-confirmed 환불 상태별 차단·GRADING 완료·COMPLETED 보존. D1 정상 만료와 D2 환불 취소 수동 검토, Store 기본 환불 창구·지원 이메일·만료 후 본인 기록 열람·출시 할인 없음도 승인됨.
- 승인 기술 방향과 미완성 상세 구분: 4 public route/read·purchase scope/sync 멱등·202, Authorization+HMAC 방향/환경 분리/주기 reconciliation/보존/ALB-Lattice 경계는 이미 승인. DTO·오류·rate limit·public envelope 일치, provider 필드/SDK identifier 변환과 실제 signature 지원, LC exact revoke route/wire·paid GRADING commit gate, schema v5·exam owner guard·이관/rollback과 worker 상세는 초안 검토/계획 승인 대상. 기존 승인 숫자나 인증 방향을 새 미정으로 재분류하거나 임의 변경하지 않음.
- 남은 사용자/법률·운영 항목: 28일권 부분 사용 후 반환 의무 적용·사용분 공제·반올림·해지 효력, Store 거절 시 법적 요청 처리, 필요한 부분 환불의 실제 Store/RC 증거/원장 경로 및 최종 구매 고지/동의. 일할 공식·위약금은 미승인. 지원 연락처는 확정이나 담당/접수·응대 운영, D2 별도 승인 복구 실행 절차는 완료 증거 없음. 기술 미구현을 법정 반환 거절 근거로 사용하지 않음.
- 검증 대기: G1 거래 식별자, G2 소유권, G3 consumed one-time 환불, G4 실제 사전 review 신호, G5 누락 복구, G6 LC 제출 경합/전달, G7 paid/free 공통 guard, G8 Store/RC/AWS/staging E2E는 선택지 승인만으로 해결되지 않는다. REFUND_REVIEW 일반 event 미확인으로 자동 adapter OFF 유지. 실제 환경 입력·sandbox fixture가 필요함.
- 문서 노후화: ADR §2.2의 상품/Console 전부 미수행, §3.1/§5.1의 모든 Store ID 미확인 및 §4의 9/9 실상품/Offering 미연결을 현재 상태로 읽으면 9/16 Apple 5종/한국 가격·지역/RC premium 매핑과 맞지 않는다. C.1 public JWT 미구현 설명도 이후 PLAN-007 사용자 reader 구현 기록과 시점 구분이 필요하다. Google 상품/RTDN·Billing webhook·판매 완료는 이후 성공 증거가 없으므로 완료 처리하지 않음. 이번은 지적/기록만이며 역사 문서나 계약 본문은 수정하지 않음.
- 다음 작업: 승인 정책은 유지하면서 노후화 상태와 기술 초안을 정리하고, 사용자 결정(법률/운영)과 개발 검증 항목을 분리한 결제 PLAN을 작성·승인받는다. 로컬 plans에는 PLAN-001~007만 있고 결제 PLAN은 없음. 구현 가능 공통 기반과 출시 차단 조건을 분리한다.
- 변경 파일·검증: CURRENT_STATE.md·WORKLOG.md만 추가. 문서 대조 및 git diff --check, 코드 변경 없어 Gradle 미실행. 기존 미커밋 변경 보존·예상 밖 변경 없음. ADR/정책/코드·타 서버·Jira·외부 설정·Git 이력·배포 변경 없음.

## 2026-10-06 — 환불 상담 운영 및 SNS 변경 영향 검토

- 브랜치 develop, 신규 Jira 없음. 사용자 요청은 운영 제안 검토와 타 서버 변경 영향 확인으로 한정.
- Identity TMI-192 단일 SNS/계정 찾기 및 TMI-197 문의 API, JWT issuer·Guest upgrade 코드, LC 최신 변경/삭제 런북과 Billing ADR-004를 읽었다. 로컬 HEAD 기준이며 원격 최신/배포 완료를 확인하지 않았다.
- 결론: 상담·수동 검토는 가능하나 Store 실제 환불/검증된 reversal과 분리한다. 공제 공식·법적 반환 기준은 미확정. 단일 SNS/힌트 조회는 userId/paid ownership 변경 근거가 아니며 기존 Billing 연결 설계를 유지한다. purchase scope 발급, 문의 결제 연결, 삭제 후 최소 이용 증거와 continuation E2E는 후속 확인 대상.
- 변경 파일: REFUND-SNS-REVIEW-2026-10-06.md 신규, CURRENT_STATE.md 갱신, 본 기록 append. 승인 정책·API·애플리케이션·타 저장소·외부 설정·Jira·Git 이력 변경 없음. 기존 미커밋 변경 및 LC 진행 작업 보존, 예상 밖 수정 없음.
- 검증: 정적 코드/문서 대조와 git diff --check. 분석/문서만 변경하여 Gradle 미실행. Store 현재 기능/법률·실제 데이터는 재검증하지 않음.
- 다음: 문의를 주 상담 창구로 추가할지 사용자 승인 후 운영/사용분 판단 기준과 결제 PLAN을 보완. Store/RC 환불 증거 및 staging E2E 전 활성화하지 않는다.

## 2026-10-06 — 결제 기록과 사용 기록 구분 재확인

- 브랜치 develop, Jira 없음. 목표: 현재 결제 기록 저장 여부 확인.
- src/main 파일 목록, EntitlementLedgerEntry/Repository, AttemptSession/Repository, AGENTS 결제 미구현·5년 보존 계약 대조. 현재 무료 지급/예약/소비와 세션 상태·시각 저장 구현은 존재하나 유료 Purchase/payment ledger는 아직 계획이다. 실제 DB/Store 거래를 조회하지 않았다.
- 기존 Attempt projection 재사용 가능성과 유료 구매별 연결/삭제 후 보존 검증 필요를 구분한다. 신규 사용량 저장소나 정책을 승인한 것은 아니다.
- 변경: CURRENT_STATE와 WORKLOG 기록만. 코드·계약·타 서버·외부 변경 없음, 기존 변경 보존·예상 밖 수정 없음. 정적 확인 및 git diff --check, 코드 변경 없어 테스트 미실행.
- 다음: 결제 계획에서 구매와 기존 이용 projection 연결·최소 증거 보존을 정의한다.

## 2026-10-06 — 환불 문의 접수 및 REFUND 분류 승인 기록

- 브랜치 develop, Jira 없음. 사용자 승인에 따라 Identity 문의 기반 환불 상담과 REFUND 분류 추가 방향을 확정 기록했다. 실제 타 서버 변경 요청으로 확대하지 않았다.
- 변경: ADR-004, CONTRACT_DECISIONS C9-S10, FIXED_TERM_PREMIUM_PAYMENT_CONTRACT, CURRENT_STATE, WORKLOG. 기존 자체 UI 제외를 상담 접수 범위에서 대체하며 Store 직접 신청/환급·검증된 최종 provider 반영 유지. 접수만으로 취소하지 않는다.
- 남은 결정: 잔여기간 반환/공제/해지 효력·운영 담당/응답/구매자 확인·최종 구매 고지. 부분 환불 실행/원장·provider 지원 및 기술 상세는 검증/계획 승인 대상. 정책을 임의 확정하지 않음.
- 검증: 문서 대조 및 git diff --check. 문서만 변경하여 Gradle 미실행. 기존 변경 보존, 예상 밖 수정 없음. Identity 코드·Jira·외부 설정·Git 이력·배포 변경 없음.
- 다음: 운영 권장안 승인 및 법률/Store 확인을 분리하고 결제 PLAN에 반영한다.

## 2026-10-06 — 환불 운영 기준 사용자 선택 반영

- 브랜치 develop, Jira 없음. 사용자 선택: 팀 건별 범위/금액 판단, 실제 환불 확정 시각, 2영업일 이내 1차 답변, userId 필수, 약관 프론트 담당.
- 변경 파일: CONTRACT_DECISIONS·ADR-004·FIXED_TERM_PREMIUM_PAYMENT_CONTRACT·CURRENT_STATE·WORKLOG. 기존 인증 계약상 userId는 검증된 로그인/현재 계정에서 자동 귀속하는 해석을 명시했다. 타 서버 DTO 변경은 하지 않았다.
- 유지: Store 직접 요청/실제 환급, 검증된 최종 상태만 원장 반영, 법정 권리 우선, 로그인 불가/탈퇴는 별도 구매자 확인. 실제 확정 시각과 webhook 수신/팀 승인/입금 시각 구분, 부분 환불/공제 공식·법적 효력 미확정 gate 유지.
- 검증: 문서 대조 및 git diff --check. 문서 변경만으로 Gradle 미실행. 기존 변경 보존, 예상 밖 수정 없음. 코드·타 서버·Jira·외부 설정·Git 이력 변경 없음.
- 다음: Identity REFUND 인증별 처리 인계 및 provider 시각/부분 환불 증거·운영 감사 절차를 결제 계획에서 구체화. 프론트 약관과 실제 동작 일치 확인 후 출시.

## 2026-10-06 — Store별 실제 환불 및 부분 환불 공식 문서 조사

- 브랜치 develop, Jira 없음. Google Play 도움말, Apple refundPreference/Send Consumption Information/고객 지원, RC Handling Refunds/Refund Control/설정 공식 페이지 직접 조회.
- 확인: Google Console 인앱 부분 금액/비율 환불, Apple 심사 선호와 직접 환급의 차이, RC 부분 선호 및 거래별 사용률 미지원. Apple12시간 정보 응답과 동의·Google 탐지 지연/제한 확인. 신규 Google reviewrefund 설명은 일반 Billing review event 계약으로 확대하지 않음.
- 변경: STORE-REFUND-CAPABILITIES-2026-10-06.md 신규, CURRENT_STATE 및 본 기록. 계약/자동 refund flag/코드/타 서버/Jira/외부 설정/실제 주문·환불/사용자 데이터 전송 변경 없음. 기존 변경 보존·예상 밖 수정 없음.
- 검증: 공식 문서 읽기·git diff --check. 코드 변경 없어 Gradle 미실행. 실제 거래 fixture/부분 환불 금액·시각·권리 종료/E2E는 미검증이며 법률 판단 아님.
- 다음: Store별 운영 차이를 사용자에게 설명하고 부분 환불 원장/권리 계약·Apple 사용정보 전달 도입 여부를 별도 승인받는다.

## 2026-10-06 — 부분 환불 잔여 권리 종료 승인 및 전체 흐름 설명

- 브랜치 develop, Jira 없음. 사용자 승인: 부분 환불 뒤 해당 구매의 남은 권리 종료. 직전 Google/Apple 운영 차이 승인도 C9-S10에 반영.
- 변경: CONTRACT_DECISIONS·ADR-004·FIXED_TERM_PREMIUM_PAYMENT_CONTRACT·CURRENT_STATE·WORKLOG. 다른 구매/무료권 유지, 실제 부분 반환액과 권리 종료 구분, 기존 reflow·GRADING/COMPLETED 예외 유지.
- 정책 확장만 반영했으며 부분 환불 exact provider 증거/금액·시각/중복·후속 reversal은 PLAN 설계 대상. 결제 전체 흐름은 설계 설명이지 구현/배포 완료가 아니다.
- 검증: 문서 대조·git diff --check. 문서 변경만으로 Gradle 미실행. 기존 변경 보존·예상 밖 수정 없음. 타 서버·애플리케이션·Jira·외부 설정·실제 환불·Git 이력 변경 없음.
- 다음: 결제 PLAN에 부분 환불 원장/권리 종료 및 실제 Store fixture·LC 차단 검증을 포함한다.

## 2026-10-06 — 결제·환불 논의 종합 리뷰

- 날짜·브랜치: 2026-10-06, develop. 신규 Jira 없음. 목표: 최근 결제/환불 논의의 승인 정책·미완성 설계·문서 정합성을 검토.
- 변경 파일: docs/contracts/PAYMENT_REFUND_DISCUSSION_REVIEW-2026-10-06.md 신규, CURRENT_STATE 갱신, 본 기록 append. 기존 미커밋 변경 보존, 예상 밖 변경 없음.
- 확인: C9-S10의 REFUND 상담·Store별 운영·2영업일 첫 응답·부분 환불 뒤 해당 구매 종료 승인과 ADR 상세·안내 초안·AGENTS·기존 Store 조사 및 Billing 파일 목록 대조. 정책 방향은 일관되나 부분 반환 금액/증분·누적/후속 환불 dedupe, 금융과 권리 상태 분리, 확정 시각 fallback·지연 reflow와 구매별 최소 이용 증거 계약이 미완성이다. AGENTS 범위 제외 및 구매 안내 현행화 필요를 지적했다.
- 유지·결정: 승인 정책을 재선택하거나 계약 본문을 수정하지 않음. 검토 결과와 후속 PLAN 검증 사례만 기록. 무료 Claim/Grant·owner·internal wire·인증·ledger 불변식·자동 refund OFF 유지. 코드·schema·타 서버·Jira·외부 설정·Git commit/push·배포 변경 없음.
- 테스트: 정적 문서 대조·로컬 링크 확인·이번 문서 diff 검사. 분석/문서만 변경하여 ./gradlew clean test 미실행. 실제 Store/RC 최신 지원·거래·법률·배포는 새로 검증하지 않음.
- 위험·다음: 기존 전액 환불 terminal 처리로 추가 금전 환불을 누락하거나 중복 합산하지 않도록 모델·증거를 정의하고, 실제 시각 미제공/지연 수신 timeline 예제를 고정한다. 최소 이용 증거와 운영 감사·최종 고지를 보완한 결제 PLAN 승인, provider fixture·LC 경합·staging gate 후 활성화한다.

## 2026-10-06 — 사용자 전달 환불 리뷰 사실 재검증

- 브랜치 develop, Jira 없음. PAYMENT_REFUND_DISCUSSION_REVIEW 전문과 ADR refund status/effectKey/terminal/reflow/source snapshot, AGENTS 부분 환불 제외, 구매 안내 및 AttemptGroup 필드 대조.
- R1~R4 타당. 코드 결함 발견이 아닌 부분 환불 승인 뒤 기술 초안/문서의 공백이다. 실제 확정 시각은 승인 정책, 최초 검증 시각 fallback은 초안으로 구분. purchase별 snapshot 방향은 이미 있으나 상세 증거/보존/환불 연결은 미완성이다.
- 변경: CURRENT_STATE 및 WORKLOG만. 기존 변경 보존·예상 밖 수정 없음. 계약·AGENTS·안내·코드·타 서버·Jira·외부·Git 변경 없음. 정책 재승인 요구 대신 ADR 상세/현행 문서 정리 후 PLAN 권장.
- 검증: 정적 대조·git diff --check. 코드 변경 없어 Gradle 미실행. 실제 provider/법률/배포 및 이전 리뷰의 링크 검사 결과는 재검증하지 않음.
- 다음: 권리 종료 한 번/금전 추가 반환 별도 누적, 지연 시각 예제, 기존 projection 활용 증거와 문서 정합성 보완을 승인받아 작성한다.

## 2026-10-06 — 구매별 이용 증거 설계 및 환불 문서 정합화

- 브랜치 develop, Jira 없음. 사용자 요청: 반복 부분 환불/알림 지연 의미 설명, 구매별 이용 증거 보완과 문서 정합성 수정. 결제 PLAN 승인 전이므로 application 구현으로 확대하지 않았다.
- 변경 파일: AGENTS.md, ADR-004, CONTRACT_DECISIONS, BILLING_SERVICE_INTEGRATION_CONTRACT, FIXED_TERM_PREMIUM_PAYMENT_CONTRACT, PREMIUM_PURCHASE_REFUND_NOTICE_DRAFT, CURRENT_STATE, WORKLOG.
- ADR §5.8.1에 인증된 문의자→검증된 구매→불변 source→기존 Group/Session/usage ledger 최소 증거 연결을 명시했다. 무료·취소·실패/replacement·완료 뒤 학습 원본 삭제를 구분하며 완료 당시 boolean/version 외 답안·피드백 원문은 복제하지 않는다. 같은 Transaction·멱등성·command/inbox TTL 독립·제한된 운영 조회/감사·ledger와 학습 개인정보 보존 구분 및 필수 테스트를 추가했다. exact schema/운영 DTO/최소 summary 보존·purge는 PLAN gate.
- 최신 범위에서 부분 환불을 일괄 제외하던 AGENTS/통합 계약을 수정하고 자동 심사/공제·직접 송금은 계속 제외했다. 안내 초안에 REFUND·2영업일 첫 답변·userId 자동 연결·Google/Apple 차이·부분 환불 잔여권 종료·다른 권리 보존·프론트 담당을 반영. 과거 WORKLOG/리뷰를 수정하지 않으며 역사적 승인과 현재 규칙을 구분했다.
- 설명: 반복 환불은 일반 상품 기능 제안이 아니며 통상 처리 후 종료. 외부 Store 추가 반환/정정 사실 누락 방지와 중복 알림 방어는 별개다. Store 확정 시각과 Billing 관측 시각이 다를 수 있어 지연 reflow/fallback은 미확정 유지; 소급/수신시각 기준을 임의 승인하지 않았다.
- 검증: git diff --check 통과, 현재 계약의 범위 제외/안내 불일치 문구 검색·정적 대조. 코드 변경 없어 Gradle 미실행. 기존 사용자 변경/PLAN-007 보존, 이번 예상 밖 수정 없음. 타 서버·API·schema·외부 설정/환불·Jira·Git 이력 변경 없음.
- 위험/다음: R3 문서 현행화와 R4 설계 보완을 수행했지만 결제 application은 미구현. R1 exact 환불 증거/금액 식별 및 R2 시각/fallback, provider fixture·LC/삭제 E2E·최종 고지 검증 후 결제 PLAN 승인과 구현으로 진행한다.

## 2026-10-06 — 환불 금액·시각 예외 권장안 설명

- 브랜치 develop, Jira 없음. 사용자 요청은 1/2번 권장안 상담이며 확정/구현 요청이 아님. C9-S2와 ADR §5.7 reflow 시각 원문 확인.
- 제안 R1: 정상 건은 팀 검토 후 처리 종료, 반복 환불 UI/자동 집행 없음. 검증된 외부 추가 반환·정정만 별도 원장에 반영, 권리 종료/reflow 중복 없음. 증분/누적 금액과 환불 식별자 검증, 모호한 금액은 전액 추정하지 않고 대사. provider 지원 확인 필요.
- 제안 R2: providerConfirmedAt nullable, firstVerifiedAt, appliedAt 분리. 최종 환불 검증 뒤 원 권리 차단, 아직 시작하지 않은 후속 기간은 최초 Billing 반영 기준으로 앞당겨 지연 중 소급 소진 방지. 기존 정상 시작시각을 뒤로 미루거나 이미 시작한 다른 slot을 재지급하지 않음. 중복 처리/후속 정확 시각 확보는 감사 증거 보완이지 재시작 근거가 아님. 지연 동안 원 권리 접근 가능성과 일시 서비스 비용을 설명하고 경보/재조회로 관리, 소급 과금/환불액 임의 공제 없음.
- 기존 C9-S2의 refundConfirmedAt 기준을 변경하는 제안임을 명시, 승인 전 ADR/결정 원장 변경 안 함. 금전 확정 시각과 법적 효력/공제 기준을 동일시하지 않음.
- 변경: CURRENT_STATE·WORKLOG만. 정적 대조 및 git diff --check, 코드 변경 없어 Gradle 미실행. 기존 변경 보존·예상 밖 수정 없음. 코드/타 서버/Jira/외부/Git 변경 없음.
- 다음: 사용자 승인 시 R1/R2 계약을 보완하고 PLAN에 지연/중복/정정/시작 경계 테스트를 포함한다.

## 2026-10-06 — R1/R2 환불 금전 예외·반영 시각 승인 반영

- 브랜치 develop, Jira 없음. 사용자 두 권장안 승인에 따라 C9-S2/S10·ADR §5.7·결제 요약·AGENTS·CURRENT_STATE·WORKLOG 갱신.
- 정상 환불 처리 종료, 외부 실제 추가 반환/정정만 금융 기록 보완, 중복 무효·불명 금액 대사 확정. 최초 접근 종료/reflow와 후속 금융 상태 분리. provider 식별·금액 exact 모델은 PLAN gate 유지.
- providerConfirmedAt nullable/firstVerifiedAt/appliedAt 분리 및 최초 성공 Transaction 반영 기준 재배치 확정. 미시작 후속 권리만 앞당기고 기존 시작 지연·진행 slot 재지급·지연 소급 소진 금지. 정확 시각 후속 확보는 감사 근거만 보완. Mongo commit timestamp/LC 실제 차단/법적 기산점과 구분.
- 검증: 문서 검색·git diff --check. 문서만 변경하여 Gradle 미실행. 기존 변경 보존·예상 밖 수정 없음. 코드/schema·타 서버·외부/Jira/Git 변경 없음.
- 다음: 결제 PLAN에서 실제 provider 금액 식별/정정·시각 필드와 중복/지연/이미 시작한 slot/unknown commit 테스트 구체화 후 구현 승인.

## 2026-10-06 — 결제부터 환불까지 단계별 PLAN 작성

- 브랜치 develop, 신규 Jira 없음. 사용자 계획서들 작성 요청에 따라 PLAN-008~013 신규 생성. 구현/외부 검증/배포로 확대하지 않았다.
- 파일: PLAN-008-fixed-term-payment-roadmap,009-payment-catalog-and-account,010-purchase-ingestion-and-timeline,011-paid-reservation-and-usage-evidence,012-refund-ledger-and-access-revocation,013-payment-rollout-and-operations(.md). ADR-004·AGENTS에 검토 대기 계획 안내, CURRENT_STATE 및 WORKLOG 갱신.
- 범위: Phase0 선행 계약·실제 provider 확인 gate와 5개 vertical slice, 책임/의존/DTO/Transaction/index/flag/실패 복구/테스트·완료 조건·reader-first/rollback·보존·Identity/LC/앱 인계 구분. R1 금전 추가 정정과 단일 권리 종료, R2 appliedAt·늦은 provider 시각, 구매별 source/최소 증거 및 LC GRADING/revoke 경합 반영.
- 확인 근거: ADR-004·PLAN-007 및 PublicResponse/PublicSecurityConfig/schema-v4 initializer/Reservation application 소스. PublicResponse 재사용과 LC source wire는 기존 초안과 차이가 있어 제안/승인 대기로 명시. 실제 RC HMAC·partial money fields를 가정하지 않고 fixture/ADR 수정 선행 gate 설정. 자동 심사·직접 송금·paid 이전·원본 학습 저장 제외 유지.
- 검증: 문서 whitespace 및 상대 파일 링크 검사, git diff --check. 계획서만 변경하여 ./gradlew clean test 미실행. 실제 Store/RC/AWS/타 서버 최신 배포/법률 검증 미수행. 기존 사용자 변경·PLAN-007 보존, 이번 예상 밖 변경 없음.
- 다음: 사용자 PLAN 검토/승인→Phase0 계약 동결/필요 재승인→사용자 요청 시 Jira 생성→009부터 구현. fake 로직 개발 완료와 실제 판매 준비 완료를 구분하며 모든 payment flag 기본 OFF.

## 2026-10-06 — PLAN-008 전체 로드맵 설명

- 브랜치 develop, Jira 없음. 사용자 요청에 따라 PLAN-008 전문 확인 후 전체 순서·Phase0·완료 기준·서비스별 인계 설명.
- 변경: CURRENT_STATE/WORKLOG 기록만. 가격/기간/환불 R1·R2 유지, envelope 등 기술 제안은 승인 대기. 설명 요청을 구현·계획 승인으로 확대하지 않음.
- 검증: 문서 대조·git diff --check. 코드 변경 없어 Gradle 미실행, 외부 provider/배포 검증 없음. 기존 변경 보존·예상 밖 수정 없음. 코드·타 서버·Jira·외부·Git 변경 없음.
- 다음: 전체 계획 검토 후 009 상세 또는 Phase0 계약 확인, 사용자 승인 뒤 구현 준비.

## 2026-10-06 — PLAN-008 사용자 결정 항목 구분

- 브랜치 develop, Jira 없음. 목표: PLAN-008에서 지금 승인할 사항과 기술 검증/출시 입력 구분.
- 안내: 009~013 단계별 진행과 PublicResponse 재사용을 권장. 제품 정책 재선택 불필요, provider·LC·schema는 근거 확인 및 기술 승인 절차, 운영 담당/환경/고지는 배포 전 준비. 구현/Jira 승인으로 확대하지 않음.
- 변경: CURRENT_STATE/WORKLOG만. 기존 계약·코드·외부·타 서버·Git 변경 없음, 기존 변경 보존·예상 밖 수정 없음. 문서 대조·git diff --check, 코드 변경 없어 테스트 미실행.
- 다음: 사용자 선택 확인 후 009 설명 또는 Phase0 세부 계약 검증으로 진행.

## 2026-10-06 — PLAN-008 진행 구조·PublicResponse 승인

- 브랜치 develop, Jira 없음. 사용자 두 선택 승인 반영: 009~013 단계별 진행, 사용자 public 결제4 API 응답 통일.
- 변경: PLAN-008/009/010·ADR-004 §5.3·CONTRACT_DECISIONS·CURRENT_STATE·WORKLOG. direct DTO는 result 내부, 오류도 PublicResponse로 명시. top-level traceId 임의 추가 없이 기존 추적 로그 유지, internal204/provider200는 대상 제외.
- 범위: 나머지 기술 초안·각 slice 구현·Jira·배포는 별도 승인. 기존 변경 보존·예상 밖 수정 없음, 타 서버/코드/외부/Git 미변경.
- 검증: git diff --check, 문서만 변경하여 Gradle 미실행. 다음: PLAN-009 상세 검토와 Phase0 해당 계약 검증.

## 2026-10-06 — PLAN-009 상품·구매 연결·인증 설명

- 브랜치 develop, Jira 없음. PLAN-009 전문 확인 후 앱 상품 조회/구매 reference/권한 분리·단계 범위 설명.
- 가격은 Store SDK 표시와 검증된 거래 금액을 구분, 계정 reference는 환경별 stable이고 생성 자체로 무료/유료권을 지급하지 않음. 009는 RevenueCat 결제·webhook·환불 구현 단계가 아님.
- CURRENT_STATE/WORKLOG만 갱신, 계약·코드·타 서버·Jira·외부/Git 변경 없음. 기존 변경 보존·예상 밖 수정 없음. git diff --check, 설명 작업이라 Gradle 미실행.
- 다음: PLAN-009 기술 검증/구현 승인 및 Identity purchase scope 인계. 실제 판매 flag는 전체 gate까지 OFF.

## 2026-10-06 — Identity REFUND 구현 확인 및 Billing 인계 상태 갱신

- 브랜치 develop, Jira 없음. Identity 로컬 HEAD cc076442를 읽기 전용 검사; 변경된 타 서버 작업기록은 보존. SupportRequest/ActorResolver/Controller/Service/Error, ServiceTests/WebTests와 문의 API 문서·기본 설정 확인.
- 사실: REFUND enum, JWT/current active DB userId 사용, ACTIVE Guest/Member 문의 가능, 익명401 SUPPORT_REFUND_AUTH_REQUIRED, 본문 userId unknown400, service의 저장/멱등 응답 전 필수 검사 구현. 관련 단위/MVC 테스트 소스 있음. 이번 테스트 실행·remote merge·ECS 배포·Slack/프론트 실호출 확인 없음. 접수/worker 기본 false.
- 변경: PLAN-008/013·ADR-004·CONTRACT_DECISIONS·FIXED_TERM_PREMIUM_PAYMENT_CONTRACT·PREMIUM_PURCHASE_REFUND_NOTICE_DRAFT·CURRENT_STATE·WORKLOG의 구현/인계 상태. 승인 정책/API 변경 없음. Identity 수정/테스트 빌드 생성하지 않음.
- 남음: 실제 배포/flag·프론트 문의·구매 연결/소유권 검증·로그인 불가 이메일/Store 안내. Guest 문의는 구매 가능 의미 아님. billing:purchase 발급은 이번 변경과 별개로 후속 유지.
- 검증: 정적 코드/테스트 대조 및 git diff --check. 문서만 변경해 Gradle 미실행. 기존 변경 보존·예상 밖 수정 없음. Jira·외부 설정·Git 이력 변경 없음.

## 2026-10-06 — PLAN-010 구매 검증·기간권 흐름 설명

- 브랜치 develop, Jira 없음. PLAN-010 전문 확인 후 앱 sync/RC webhook/reconciliation, 검증된 owner/transaction, Purchase·기간권·ledger 원자 저장, 중복/유실·PENDING 복구와 구매 시각 기준 stacking 설명.
- Identity purchase scope 작업은 사용자 요청대로 후속 유지하되 실제 구매 API 활성화 요건에서 제거하지 않음. 009·010 구현/승인 완료 또는 판매 가능으로 처리하지 않음.
- CURRENT_STATE/WORKLOG만 갱신. 코드·정책·타 서버·외부/Jira/Git 변경 없음. 기존 변경 보존·예상 밖 수정 없음. 문서 확인·git diff --check, 코드 변경 없어 Gradle 미실행.
- 다음: 010 상세 질문/승인 및 provider 선행 계약 검증 또는 011 설명. 시험 연결/환불은 다음 slice.

## 2026-10-06 — PLAN-011 유료 시험 승인·구매별 이용 증거 설명

- 브랜치 develop, Jira 없음. 목표: PLAN-011의 유료 기간권과 기존 시험 시작/재응시 연결을 사용자에게 설명. 계획 전문을 확인하고 paid-first/free-preserve, 사용자 공통 guard, 불변 구매 source, 최소 이용 증거와 정상 만료/환불 차이를 정리.
- 변경 파일: CURRENT_STATE·WORKLOG만 갱신. 코드·외부 계약·정책·타 서버·Jira·Git 이력 변경 없음. 기존 변경 보존, 예상 밖 변경 없음.
- 검증: 계획 문서 확인 및 git diff --check. 설명/기록만 수행하여 Gradle 테스트 미실행.
- 유지: reserve→Learning Core Session commit→confirm, 무료 consumption 보존, command TTL과 독립적인 이용 증거. 010 보류는 승인 아님; 결제 구현·판매 활성화 미수행.
- 위험/다음: Learning Core의 source 구분 wire와 paid 채점 시작 승인, 공통 guard migration은 구현 전 계약 검증 필요. 011 상세 검토/승인 후 다음 계획 검토, 실제 배포 전 전체 E2E gate 유지.

## 2026-10-06 — PLAN-011 추가 확정 사항 안내

- 브랜치 develop, Jira 없음. PLAN-011 전문을 재확인해 이미 정한 상품 정책과 LC source 응답/채점 시작 gate의 기술 승인 대상을 분리했다. 공통 guard는 일생 1회 제한이 아니라 동시 시작·진행 정합성 장치이며 해제/재응시 상세 전이는 검증 대상이다.
- CURRENT_STATE·WORKLOG만 변경. 외부 계약·코드·타 서버·Jira 변경 없음, 사용자 질문을 구현/기술 계약 승인으로 간주하지 않음. 기존 변경 보존, 예상 밖 변경 없음.
- 검증: 문서 확인, git diff --check. 코드 변경 없는 안내이므로 Gradle 미실행.
- 다음/위험: capability-gated v2의 exact route/DTO/version/IAM과 LC paid GRADING gate 합의, guard 전이·legacy migration·refund 경합 테스트 설계 후 구현 승인. 실제 판매/배포는 전체 연동 gate 전 OFF 유지.

## 2026-10-06 — PLAN-013 출시 검증·운영 설명

- 브랜치 develop, Jira 없음. 목표: PLAN-013 전문을 확인하고 실제 연동 검증, legacy/schema 이관, 단계별 배포, 안전 중단, 문의·보존·경보 및 판매 gate를 쉬운 용어로 안내.
- 변경 파일: CURRENT_STATE·WORKLOG. 코드·외부 계약·타 서버·Jira·배포 변경 없음. 기존 변경 보존, 예상 밖 변경 없음.
- 검증: 문서 확인 및 git diff --check. 설명 기록만 변경하여 Gradle/Store sandbox/실제 인프라 검증 미실행.
- 유지: 판매 중단과 기존 결제 복구/환불 처리를 분리; 문의 2영업일은 첫 응답; 최소 금융 증거 5년과 무료 Claim 3년·backup35일을 혼용하지 않음.
- 위험/다음: provider 인증·부분 환불 실제 증거, 운영 담당/권한·보존 manifest·D2 복구 절차·staging E2E가 남음. 상세 승인/구현과 외부 배포 승인은 별도이며 이번 설명을 승인으로 간주하지 않음.

## 2026-10-06 — PLAN-013 출시 검증·운영 방향 승인 반영

- 브랜치 develop, Jira 없음. 사용자 승인에 따라 PLAN-013 상태와 승인 범위를 기록하고 CONTRACT_DECISIONS·CURRENT_STATE·WORKLOG를 동기화했다.
- 동작/계약: 연동 검증·migration·단계별 활성화·판매 중단/기존 거래 처리 분리·문의/보존/백업/경보 방향 승인. 외부 API/제품 정책 변경 없음. 미확정 상세·운영 담당/권한·배포/판매/환불 실행은 별도 승인 유지.
- 검증: git diff --check. 문서만 수정하여 Gradle 미실행; 실제 sandbox/인프라/판매 gate 검증 미수행.
- 기존 변경 보존, 이번 범위의 예상 밖 변경 없음. 코드·타 서버·Jira·Git 이력·외부 설정 변경 없음.
- 다음/위험: 선행 계획 기술 계약 검증과 구현 범위 확정, 이후 실제 운영 담당/환경/보존 manifest·D2 절차 및 출시 체크리스트 준비. 계획 방향 승인을 gate 완료로 해석하지 않는다.

## 2026-10-06 — PLAN-008~013 종합 리뷰

- 날짜·브랜치: 2026-10-06, develop. 신규 Jira 없음. 목표: 사용자 작성 결제 계획 6개의 승인 계약·구현 순서·완료 조건 검토.
- 조사/판정: PLAN-008~013 전문, 최신 AGENTS·ADR-004·C9, 기존 AttemptGroup event service/controller/repository·public security 대조. 분할과 R1/R2·PublicResponse·부분 환불 방향은 정합. paid GRADING 승인 확인의 wire(기존 STALE도204), bounded revoke fan-out/root deny, 공통 guard 상태 전이/온라인 이관 CAS, staging 환불 processor/publisher 선행 순서 구체화를 지적. provider/schema의 명시된 Phase 0 gate는 미완성임을 구분하며 정책을 재선택하지 않음.
- 변경 파일: docs/plans/PLAN-008-013-REVIEW-2026-10-06.md 신규, CURRENT_STATE 갱신, 본 기록 append. 기존 사용자 미커밋 변경 보존, 예상 밖 변경 없음.
- 유지/동작: 리뷰 문서만 추가. 계획/ADR/정책 본문·기존 무료 wire/owner/ledger·코드/schema·타 서버·Jira·외부 설정·Git commit/push·배포 변경 없음. 기존 승인 상태 유지, 이번 리뷰를 구현 승인으로 해석하지 않음.
- 검증: 정적 코드/문서 대조, git diff --check 및 리뷰 상대 링크 검증. 분석/문서 작업으로 ./gradlew clean test 미실행. 실제 Identity/LC 배포·Store/RC/AWS·법률은 새로 검증하지 않음.
- 위험/다음: GRADING ack를 paid Job 승인으로 사용할 exact 계약과 대량 fan-out의 장애/재시작·선행 GRADING 증거를 동결하고, guard 전이/online coverage 및 flag 상태표를 각 계획에 보완. provider/auth/partial evidence·manifest를 관련 slice 구현 전에 닫은 후 승인된 범위로 진행.

## 2026-10-06 — 종합 리뷰 F1~F4 재검증

- 브랜치 develop, Jira 없음. 사용자 제시 리뷰를 PLAN-011/012/013 및 AttemptGroupEventController/Service와 직접 대조. 네 지적 모두 타당하며 현재 무료 버그 단정이 아닌 미구현 paid 기술 계약 공백이다.
- 확인: Controller는 service 결과와 무관하게 정상204; inactive Session/부적합 상태/유효 Trial link 부재는 STALE, 동일 inbox 재전송은 duplicate. 따라서 paid exact Session의 durable 승인 확인은 별도 계약 필요. 012 무제한 fan-out·011 guard 전이/이관·013 활성화 순서도 상세 미완성. 010 PUBLIC span 표현 보정 역시 타당.
- 변경 파일: CURRENT_STATE·WORKLOG만. 계획/ADR/코드·타 서버·Jira·외부/Git 이력 미변경. 기존 변경 보존, 예상 밖 변경 없음.
- 검증: 정적 문서/코드 대조, git diff --check. 분석 작업으로 Gradle/실제 provider·AWS 테스트 미실행.
- 유지/다음: 기존 상품/환불 R1·R2 정책 유지. F1→F2→F3→F4 상세 설계 및 ADR 영향 검토 후 구현; source v2 승인만으로 채점 승인 계약 완료로 간주하지 않음. 대량 환불은 단순 분할만으로 안전하지 않으며 선행 GRADING 증거와 모든 authorization의 root deny 검증이 필요.

## 2026-10-06 — F1~F4 선택지·장단점 제안

- 브랜치 develop, Jira 없음. 목표: 구현 전 계약 공백의 선택지를 사용자에게 설명. F1 전용 멱등 paid 승인 command 대 versioned event ack, F2 구매 root 차단/원자적 durable job+분할 전파 대 전체 단일 Transaction, F3 공통 exact 전이표와 제한 이관 대 online CAS backfill, F4 환불 선행 자동 gate 대 수동 순서 관리 비교.
- 권장안은 미승인 제안이다. guard INITIAL 취소/replacement 취소/GRADING/terminal/만료·환불을 구분하며 과거 작업의 새 guard 삭제를 exact version으로 방지한다. 이관 제한은 관련 writer/event 재시도와 복구를 포함하고 진행 중 시험을 임의 취소하지 않는다. 승인 command는 원자적 GRADING 증거와 환불 공통 CAS, TTL 독립성 및 LC Job 멱등성 필요.
- 변경 파일: CURRENT_STATE·WORKLOG만. 계약/계획 본문·코드·타 서버·Jira·외부 설정/Git 이력 변경 없음. 기존 변경 보존, 예상 밖 변경 없음.
- 검증: git diff --check. 선택지 설명/기록만 수행해 Gradle 미실행.
- 위험/다음: 선택 승인 후 exact wire/상태 전이/작업 cursor·fencing/flag 표와 ADR 영향 작성. 이관 중단 허용·예상 시간은 실측/운영 승인 필요. root 차단은 LC 즉시 동기 차단 보장이 아니며 전파 지연 관리·사전 승인 exact Session 보존 필수. timeline reflow의 크기 한계도 별도 검증한다.

## 2026-10-06 — F1~F4 A안 사용자 승인 반영

- 브랜치 develop, Jira 없음. 사용자 승인에 따라 paid 전용 채점 승인·구매 root deny/분할 전파·guard 전이/짧은 제한 이관·자동 활성화 gate를 문서화했다.
- 변경 파일: PLAN-008/010/011/012/013, ADR-004, CONTRACT_DECISIONS, CURRENT_STATE, WORKLOG. 기존 무료204/API·상품/환불 R1/R2 유지; paid 승인 API는 신규 계약 방향이며 exact wire는 미확정. 환불 전체 group 단일 Transaction 초안은 root+job 원자 저장/후속 bounded 전파로 명시 대체했다.
- 동작: 코드 없음. guard 전이표와 단계별 flag 표, 관련 writer drain/재시도 및 coverage, 선행 GRADING 영속 증거·재전송·LC Job 멱등성을 추가. 010 PUBLIC span을 HTTP SERVER/업무 INTERNAL로 정합화.
- 검증: git diff --check 및 수정 문서 정적 검토. 문서 변경만으로 Gradle 미실행; 실제 Store/RC/LC/인프라 미검증. 기존 변경 보존, 예상 밖 코드/타 서버 수정 없음.
- 위험/다음: route/DTO/IAM/승인·fan-out schema, cursor/fencing·완료 검증, 긴 timeline 한계와 실제 이관 시간/실행 runbook은 구현 전 동결. 이번 승인으로 Jira 생성/코드 구현/타 서버 변경/배포/판매/외부 환불을 실행하지 않았다.

## 2026-10-06 — 세부 기술 계약의 확정 주체 안내

- 브랜치 develop, Jira 없음. PLAN-011~013의 미확정 항목 확인 후 사용자 정책/운영 승인, 개발 상세 작성, LC 합의/provider 실제 검증을 구분해 설명.
- 변경 파일: CURRENT_STATE·WORKLOG만. 승인된 F1~F4 방향 유지; 사용자에게 임의 field/숫자 선택을 요구하지 않으며 상세 확정을 완료한 것으로 주장하지 않음. 기존 변경 보존, 예상 밖 변경 없음.
- 검증: 문서 정적 확인·git diff --check. 분석 기록만 변경해 Gradle 미실행. 코드/타 서버/Jira/외부·배포 미변경.
- 다음/위험: exact API·승인 증거/멱등/보존·fan-out checkpoint/fencing·guard CAS·flag 계약 초안 작성 후 검토. 실제 provider 지원·LC 합의와 이관 제한 시간/운영 권한·복구/판매 승인 별도.

## 2026-10-07 — 운영·RevenueCat 인증/부분 환불 공식 지원 조사

- 브랜치 develop, Jira 없음. 목표: 이관 시간·담당 권한·예외/출시 승인과 provider 지원을 조사해 권장안/장단점 제공.
- 확인: RC 공식 webhook HMAC 헤더·raw body·재시도 재서명 지원, Google 부분 금액 미수신 제한(currency 문맥), Google Orders 부분 반환 상태/금액/시각 문서. RC 계정 실측·소모품 실제 환불 데이터·Apple 부분 금액 검증은 미완료.
- 변경 파일: PAYMENT_OPERATIONS_PROVIDER_RESEARCH-2026-10-07.md 신규, CURRENT_STATE, WORKLOG. 기존 계약/PLAN/코드·타 서버·Jira·권한·외부 메시지/배포 변경 없음. 기존 변경 보존, 예상 밖 수정 없음.
- 권장/위험: 이관 시간은 DB inventory·리허설 후 산정(현재 운영 수치 미확인). 담당/대체자·최소 권한·통제된 복구/단계 출시 권장. RC 증거 부족 시 Google 읽기 전용 대사 adapter는 별도 신뢰 계약 확대 승인이 필요한 후보이며 이번에 구현하지 않음. 금액 unknown과 환불 사실 미확인은 구분.
- 검증: 공식 페이지 검색/열람, 로컬 runbook·설정 대조, 문서 diff/로컬 링크 검사. 코드 변경 없어 Gradle 미실행. Secret·실거래·운영 DB 미조회.
- 다음: RC 계정 HMAC/test delivery·실제 consumable 환불 fixture/지원 확인, 부족 시 보완 경로 승인, migration 도구 이후 실측 및 담당자 지정. 법정 의무/스토어 판단을 기술 제한으로 면제하지 않음.

## 2026-10-07 — Google Orders 부분 환불 보완 승인·문서 검증

- 브랜치 develop, Jira 없음. 사용자 승인 범위: RC 부족 시 Google 소모성 상품 부분 환불의 인증된 읽기 조회 보완. 공식 orders.get/Orders schema·RC currency 제한/purchase v2 문서 재확인.
- 변경 파일: AGENTS, ADR-004, PLAN-012, CONTRACT_DECISIONS, CURRENT_STATE, PAYMENT_OPERATIONS_PROVIDER_RESEARCH-2026-10-07, WORKLOG. 기존 RC-only 환불 신뢰 경계에 제한적 Google 조회 예외 명시. 신규 지급/자동 환불/수동 입력/owner 이전 제외, 기존 R1/R2·무료 계약 유지.
- 검증 사실: package/order GET·androidpublisher scope, 부분 환불 성공 상태/시각/세금 포함 금액 존재. scope 자체 읽기 전용 아님, GET/최소 권한 필요. RC v2 Web Billing refund를 Store API로 오인하지 않음. RC/Google 이중 합산·식별자 발명 금지.
- 검증: git diff --check·문서 대조. 문서 변경만으로 Gradle 미실행. 실제 Google 권한/주문/API·RC 계정/지원 질의·실제 환불 미수행. 코드·타 서버·Jira·배포 변경 없음, 기존 변경 보존·예상 밖 수정 없음.
- 위험/다음: 실제 consumed one-time partial fixture, 조회 주체/권한·환경/owner mapping, 이력 완전성·누적/정정 모델 확인 후 구현/활성화. Apple 부분 금액 검증 별도. raw 개인정보/응답/credential 미수집.

## 2026-10-07 — Google 상품 미등록 상태의 다음 작업 안내

- 브랜치 develop, Jira 없음. PLAN-009를 확인해 관련 Phase0 상세 계약→검토/승인→Jira→구현 순서를 안내. 상품 catalog와 stable 구매 계정 연결 기반은 실제 Google 상품 없이 설계/fake 검증 가능하며 실제 매핑 없는 Google 상품은 비활성 유지.
- 변경 파일: CURRENT_STATE·WORKLOG. 상품 미등록은 사용자 제공 사실이며 콘솔 확인 미수행. 코드·계약 본문·타 서버·Jira·외부 변경 없음. 기존 변경 보존, 예상 밖 변경 없음.
- 검증: 계획 정적 확인·git diff --check. 안내 기록만 변경해 Gradle 미실행.
- 유지/위험/다음: Identity purchase scope·실제 provider/Store fixture와 배포 gate는 면제하지 않음. 현재는 다음 작업 설명 요청이며 상세 작성/구현/이슈 생성 승인은 아님. 009 API/보안·index/limiter 계약 동결부터 진행 권장.

## 2026-10-07 — PLAN-009 세부 기술 계약 초안 작성

- 브랜치 develop, Jira 없음. 목표: 승인된 상품/계정 기반 방향을 구현 가능한 요청/응답·인증·저장·limiter·테스트 초안으로 작성. ADR §5.1~5.3/A와 현재 PublicSecurityConfig/IngressFilter/ApiWriter/JwtDecoder/Response/IndexInitializer를 대조했다.
- 변경 파일: contracts/PLAN-009-payment-foundation-technical-contract.md 신규, PLAN-009·ADR-004 링크, CURRENT_STATE·WORKLOG. 코드/타 서버/Jira/외부 설정·Git 이력 미변경, 기존 변경 보존·예상 밖 변경 없음.
- 내용: exact 두 route/권한/empty body/query·에러/헤더, Google 미등록 비노출, account/ref Transaction/unique/unknown commit, catalog snapshot, Mongo 고정1분60/10 limiter 제안·subset exact index, 활성화와 legacy 무료 회귀 테스트. 무료 writer의 scope/Allow/Retry-After 고정 위험을 분리했다.
- 유지: PublicResponse·무료/SigV4·상품 기간/가격·새 혜택 무지급. 신규 index/limiter·subset·catalog 발행은 제안으로 표시하고 ADR을 임의 승인으로 바꾸지 않음. reference 암호화/미구매 계정 보존·Identity ACTIVE/token 잔존은 선행 동결 대상으로 남김.
- 검증: git diff --check 및 신규 문서 로컬 링크 검사/정적 대조. 문서 작성만으로 Gradle 미실행; 실제 provider/Store/DB/배포 검증 미수행.
- 다음/위험: 기술 초안 검토 및 미확정 privacy/발급 계약 동결→Jira 승인→009 구현. 실제 Google ID/테스트 구매는 등록 후, paid 전체 v5/판매 완료로 간주하지 않음.

## 2026-10-07 — PLAN-009 기술 초안 설명

- 브랜치 develop, Jira 없음. 사용자 요청에 따라 상품 조회/구매 계정 연결, scope·본인 식별, 원자 get-or-create, index·fixed-window limiter, 오류·flag·Google 미등록 조건을 쉬운 용어로 설명.
- 변경 파일: CURRENT_STATE·WORKLOG만. 초안/정책/코드·타 서버·Jira·외부 변경 없음. 기존 변경 보존, 예상 밖 변경 없음.
- 검증: 초안 정적 재확인·git diff --check. 설명 기록만 변경하여 Gradle 미실행.
- 유지/위험/다음: 사용자 질문을 승인으로 간주하지 않음. 미구매 계정/reference 보존/암호화·Identity ACTIVE/token 잔존 및 limiter/schema 제안 검토 후 계약 동결→Jira/구현 승인. 실결제와 판매 활성화는 후속 gate.

## 2026-10-07 — 009 상세 선택지 코드 확인·권장안

- 브랜치 develop, Jira 없음. Identity issuer/login/reissue/recovery/session security/withdrawal/enum·TTL 설정을 읽기 전용 확인하고 Billing decoder/초안 보존·schema와 대조했다.
- 확인: read/audience 합성, ACTIVE login/refresh, 탈퇴 refresh 폐기; SUSPENDED enum 존재. Billing JWT는 현재 상태 미조회. default30분이며 배포 override 미확인, MEMBER-only purchase 발급 정책은 별도 작업.
- 변경 파일: PLAN-009-foundation-options-review-2026-10-07.md 신규, CURRENT_STATE·WORKLOG. 고정 window/subset·조건부 미구매 삭제/필드 암호화·상태 조회 선택 장단점 제시. 동기 조회는 신규 계약, 30일은 미검증 보존 후보로 명시. 기존 정책/코드/타 서버/Jira/외부 변경 없음, 기존 변경 보존·예상 밖 수정 없음.
- 검증: 정적 소스/문서 대조·git diff --check·로컬 링크 검사. 코드 변경 없어 Gradle 미실행. 운영 token/DB/법률·배포 확인 미수행.
- 위험/다음: 즉시 Store 결제 차단으로 오인 금지, 이미 결제된 거래 복구 유지. 보존을 위한 탈퇴 전달·암호화 lookup schema와 ACTIVE 조회 신규 wire는 선택 승인 뒤 ADR/인계 보완. 다음 사용자 선택 대기이며 자동 승인/구현하지 않음.

## 2026-10-07 — DB 저장 암호화 여부 안내

- 브랜치 develop, Jira 없음. application.yml/env 참조·ADR/runbook 확인 및 MongoDB 공식 Atlas encryption-at-rest 문서 검색. Atlas는 기본 AES256 저장 암호화 항상 활성이나 이 저장소만으로 운영 DB 종류/설정 확정 불가.
- 변경 파일: CURRENT_STATE·WORKLOG만. 운영 DB/Secret·배포 미조회. 저장 암호화는 DB 권한으로 조회한 평문까지 가리는 필드 암호화와 다름을 설명. 코드/계약/타 서버/Jira/외부 변경 없음, 기존 변경 보존·예상 밖 수정 없음.
- 검증: 공식 https://www.mongodb.com/docs/atlas/security-encryption-at-rest-overview/ 및 로컬 설정 대조, git diff --check. 코드 변경 없어 Gradle 미실행.
- 다음/위험: 운영 Atlas 사용 여부 확인 후 실제 적용 여부 확정. self-hosted는 볼륨/DB 설정 별도 확인. 암호화 선택 질문을 필드 암호화 생략 승인으로 해석하지 않음.

## 2026-10-07 — Reference 필드 암호화 생략 승인 반영

- 브랜치 develop, Jira 없음. Atlas 사용 사용자 확인 및 purchaseAccountRefId 필드 암호화 생략 승인을 반영. 앞선 네 A 선택(요청 제한/subset/조건부 보존/JWT 만료 수용)도 명시하되 보존30일 미확정 유지.
- 변경 파일: PLAN-009-payment-foundation-technical-contract, ADR-004, CONTRACT_DECISIONS, CURRENT_STATE, WORKLOG. referenceId=_id 유지, at-rest/TLS/최소권한/로그 제외·소유 검증 유지. Store token/credential 암호화까지 생략하지 않음. 동기 상태 API 미추가.
- 검증: git diff --check·문서 정합성 대조. 코드 변경 없어 Gradle 미실행. 실제 Atlas/ECS 설정/키·DB·타 서버/Jira/배포 미변경. 기존 변경 보존·예상 밖 변경 없음.
- 위험/다음: DB 조회 권한 침해 시 reference/사용자 연결 노출 위험은 접근 통제로 관리. Identity ACTIVE purchase 발급, 미구매/탈퇴 삭제 manifest·schema 상세 검토 후 구현 승인. 기존 JWT 만료 전 잔존 허용을 즉시 탈퇴 차단으로 해석하지 않음.

## 2026-10-07 — 009 남은 결정사항 재확인

- 브랜치 develop, Jira 없음. 최신009 초안의 승인/미확정 구분 확인. 정책 잔여는 탈퇴한 미구매 계정/reference 삭제 유예·늦은 결제/미해결 예외 범위이며30일은 미확정 후보. 사용자에게 기술 field 선택을 추가 요구하지 않음.
- CURRENT_STATE·WORKLOG만 변경. 코드/정책/계약 본문·타 서버·Jira/외부 미변경, 기존 변경 보존·예상 밖 변경 없음.
- 검증: 정적 문서 확인·git diff --check. 코드 변경 없어 Gradle 미실행.
- 다음/위험: 탈퇴 증거 전달/대사·삭제 manifest와 Identity ACTIVE purchase 발급 검증 후 contract 동결 및 구현 승인. 단순 로컬 Purchase 부재를 미구매 증명으로 사용하지 않으며 전체 결제 출시 gate는 별도 남음.

## 2026-10-07 — 미구매 탈퇴 계정 보존 선택지 안내

- 브랜치 develop, Jira 없음. 7/30/90일 운영 후보의 최소 보관 대 복구 여유를 비교하고30일 조건부 삭제를 제안. 법정기간/Store 최대 지연 보장값으로 주장하지 않으며 새 승인은 받지 않음.
- CURRENT_STATE·WORKLOG만 변경, 코드/계약/타 서버/Jira/외부 미변경. 기존 변경 보존·예상 밖 변경 없음. git diff --check, 설명 기록만으로 Gradle 미실행.
- 유지/위험: authoritative 탈퇴시각·확인된 inactive 처리/재생성 차단, 로컬 미구매만으로 삭제 금지, 미해결 케이스 최소 증거·재검토 기한과 금융 증거 별도 보존. 삭제 후 늦은 거래는 새 계정 자동 지급/동일 번호 연결 금지. RC 밖 아직 미관측 거래 가능성과 완전성 검증 필요.
- 다음: 사용자 보존안 선택 후 탈퇴 전달·대사·purge manifest/개인정보 고지 및 실제 지연 검증을 동결. 추천 기간만으로 실제 삭제/배포를 승인받은 것으로 해석하지 않음.

## 2026-10-07 — 미구매 탈퇴 계정15일 조건부 삭제 승인

- 브랜치 develop, Jira 없음. 사용자15일 선택/승인을 ADR-004·009 기술 계약/PLAN·CONTRACT_DECISIONS·CURRENT_STATE·WORKLOG에 반영.
- 기준/동작: authoritative withdrawnAt+15일, 거래 대사/미해결 확인 후 명시적 purge. 금융 기록/미해결 최소 증거는 별도 보존, 지연 event/구매 경합/재생성 방지 조건 추가.15일을 unconditional TTL/법정 기간/물리삭제 SLA로 해석하지 않음.
- 검증: git diff --check·정적 문서 대조. 문서만 변경해 Gradle 미실행. 코드/실제 삭제·타 서버/Jira·설정/배포 미변경. 기존 변경 보존, 예상 밖 수정 없음.
- 남음/다음: 주요009 정책 선택은 완료. 탈퇴 전달·대사/삭제 worker 주기·미해결 재검토/최소 tombstone 보존·schema·Identity ACTIVE purchase 발급 기술 계약 검토가 필요하며 새로운 개인정보 수명/사용자 영향 발생 시 재승인. 이후 Jira/구현 승인, 실제 판매 gate 별도.

## 2026-10-07 — 탈퇴 수신·계정 상태·거래 대사·삭제 절차 설계

- 브랜치 develop, Jira 없음. 목표는 승인된15일 조건부 삭제의 기술 설계이며 코드 구현/운영 실행이 아니다.
- 변경: docs/contracts/PLAN-009-withdrawal-reconciliation-and-purge-contract.md 신규; ADR-004, PLAN-009, PLAN-009-payment-foundation-technical-contract, CURRENT_STATE 및 이 기록에 연결/상태 추가. 기존 사용자 변경 보존.
- 조사/동작: Identity 기존4필드 wire·단일 LC JWT publisher·탈퇴 transaction을 읽기 확인. Billing 독립 delivery/누락 feed, local lifecycle/cleanup 분리, 공통 writer CAS, provider 부재 증명, token drain, 조건부 삭제/늦은 거래 격리/복원 절차 및 테스트를 제안했다.
- 유지: 신규 무료 지급/무료 Claim 삭제/자동 환불/paid owner 이전 없음, 금융5년·backup35일/15일 기산점 유지. 타 저장소·Jira·코드·실제 계정/설정/배포 변경 없음.
- 결정/위험: 매시간 batch100, READY24시간 경보, 일일 재대사/7일 담당 검토 등은 미승인 초기값. RC 빈 응답만으로 미구매 확정 금지, coverage/예외 보존/Identity feed/token 운영수명 확인 전 purge OFF. 009 반복 무변경과 ADR activity 요구 충돌 발견: 별도 cursor 갱신을 제안하고 합의 전 해당 구현 중단.
- 검증: git diff --check 및 신규 문서 로컬 링크 확인. 문서만 변경해 Gradle 미실행. 예상 밖 이번 작업 변경 없음. 다음은 기술안/운영 보존 검토와 Identity 계약 인계, ADR 정합성 동결 후 Jira·구현 승인이다.

## 2026-10-07 — 탈퇴 cleanup 운영 주기 사용자 승인 반영

- 브랜치 develop, Jira 없음. 사용자 승인한 매시간 삭제 대상 점검·매일 미확인 재확인·7일 이내 담당 검토를 설계/ADR/PLAN에 반영하는 문서 작업.
- 변경 파일: PLAN-009-withdrawal-reconciliation-and-purge-contract, ADR-004, PLAN-009-payment-catalog-and-account, CONTRACT_DECISIONS, CURRENT_STATE, WORKLOG. 최초 미해결 기준 검토 기한과 재시도에 의한 기한 연장 금지를 명시했다.
- 유지/결정: withdrawnAt+15일 조건부 삭제, 금융 보존/무료 불변, 실제 purge OFF 유지. batch/lease/24시간 경보·첫 검토 뒤 반복 담당 검토·예외 보존기간은 승인으로 확대 해석하지 않음.
- 검증: git diff --check 통과. 문서만 변경하여 Gradle 미실행. 기존 변경 보존, 이번 작업의 예상 밖 변경 없음. 코드/타 서버/Jira/배포/실제 삭제 미변경.
- 위험/다음: provider 미구매 증명 범위·Identity 누락 복구와 token drain·예외 최소정보 보존 manifest 및 cursor 정합성 동결 후 Jira/구현 승인 필요.

## 2026-10-07 — Identity 결제 계정 lifecycle 서버 인계서 작성

- 브랜치 develop, Jira 없음. 목표: 사용자 요청에 따라 Identity 담당자가 검토할 확정 정책/미합의 기술 계약과 회신 양식 작성.
- 변경: docs/contracts/IDENTITY-PAYMENT-ACCOUNT-LIFECYCLE-HANDOFF.md 신규, 탈퇴·대사·삭제 설계에 인계 링크, CURRENT_STATE와 WORKLOG 갱신. Identity wire/JWT issuer/TTL 로컬 사실 재확인, 운영 배포 검증 아님.
- 내용/유지: ACTIVE MEMBER purchase 발급 모든 경로·탈퇴 경합, 기존 UserWithdrawn4필드·LC JWT 보존/Billing SigV4 독립 delivery, snapshot/feed gap과 원천 보존, userId 비재사용/token 최대 수명 검증, 오류/retry·staging 테스트·회신 항목. 15일 조건부/승인된 점검 주기 유지. 새 route/IAM/feed 기술 제안은 확정으로 취급하지 않음.
- 검증: git diff --check와 신규 문서 로컬 링크 검증 통과. 문서 작업으로 Gradle 미실행. 기존 변경 보존, 이번 작업 범위 밖 예상 변경 없음. 코드/Identity 수정·다른 채팅 전송·Jira·배포 없음.
- 위험/다음: Identity 회신 후 exact wire/feed/보존·발급 경합 계약 동결. Billing provider coverage·예외 보존/cursor 정합성 별도 마무리와 구현 승인 필요; 실제 purge OFF 유지.

## 2026-10-07 — 첨부 Identity 인계 검토 회신 확인

- 브랜치 develop, Jira 없음. 사용자 첨부 회신의 핵심 지적을 읽고 issuer/LC publisher/backfill 소스 일부를 재대조했다.
- 변경: docs/contracts/IDENTITY-PAYMENT-HANDOFF-REVIEW-2026-10-07.md 신규 및 CURRENT_STATE/WORKLOG. 기존 계약/코드/타 서버/Jira/외부 설정은 수정하지 않음.
- 결과: scope 합성/equalsIgnoreCase·모든2xx ACK·bounded backfill 확인. 발급/replay 경합·원천 보존·snapshot commit 경계·Retry-After 상세 합의가 필요하며 첨부 조사와 이번 재검증 범위를 구분함.
- 유지/위험: 15일 조건부·승인 운영 주기 유지, 새 보존/사용자 제한 승인 없음. 운영 token/coverage/provider 부재 증명 미검증으로 판매/purge 안전성 승인 불가.
- 검증: git diff --check·신규 문서 로컬 링크 확인. 문서 분석으로 Gradle 미실행. 기존 변경 보존, 예상 밖 이번 변경 없음. 다음은 공동 기술 계약 보완안 작성/검토 후 구현 승인.

## 2026-10-07 — Identity–Billing 공동 기술 계약 권장 초안 작성

- 브랜치 develop, Jira 없음. 사용자 요청에 따라 반복 검토 대신 구현용 발급/원천/복구/재개 규격을 작성했다.
- 변경 파일: IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md 신규, Identity 인계서·탈퇴 설계·PLAN-009에 링크, CURRENT_STATE·WORKLOG 갱신.
- 설계: 공통 control Transaction commit을 purchase 발급 선형화 지점으로 정의, replay 원 exp 유지. 탈퇴 atomic sequence capture·목적지 독립 보관, Mongo 고정 T/H snapshot과 feed 연속 checkpoint/legacy COMPLETE 증거 구분.204-only·retry20회·Retry-After·BLOCKED_AUTH·lease fencing/재개·capture 유지 rollback 및 테스트 제안.
- 유지/승인 구분:15일 조건부와 매시간/매일/7일 검토 유지. 새 source120일·snapshot7일·token상한30분/이관은 미승인 권장값. 코드·Identity·Jira·실제 운영/외부 전송 변경 없음, 기존 변경 보존·예상 밖 이번 변경 없음.
- 위험: snapshot 서버/driver 기능·history window, counter 처리량, token 과거 수명/전체 발급 경합, legacy 완전성·원천 보존 및 Billing provider 부재 증명 미검증. 단순 tombstone scan으로 COMPLETE 판정 금지.
- 검증: git diff --check·신규 문서 로컬 링크 통과, 문서 작업으로 Gradle 미실행. 다음: 사용자 보존/상한 선택과 Identity 공동 검토·이관 측정, ADR/schema exact 동결 후 Jira/구현 승인. 판매/자동삭제 승인 아님.

## 2026-10-07 — 공동 기술 계약 초안 Identity 회신 재검토

- 브랜치 develop, Jira 없음. 사용자 첨부 R1~R5 및 추가 명세를 공동 계약과 로컬 Identity control/LoginService에 대조했다.
- 변경: IDENTITY-BILLING-TECHNICAL-CONTRACT-REVIEW-2026-10-07.md 신규, CURRENT_STATE/WORKLOG. 공동 계약 원문/코드/타 서버/Jira/외부 전송·설정은 미변경.
- 판정: baseline 승인·고정H2·consumer GAP 분리·control 이관·일반 발급 불명 결과 규격 공백 모두 타당. snapshot 마지막 marker+baseline만 bounded 원자 확정, 정상 지연 ACK와 위조 구분, receipt 없는 로그인503 권장 등 추가 설명.
- 위험/유지: majority/restore stream·기산시각/canonical bytes/retry schema/operation 보존 상세 필요. 제품15일/운영 주기 유지,120일/7일/30분은 미승인. 실제 구현 장애로 단정하지 않음.
- 검증: git diff --check 및 신규 문서 로컬 링크 통과. 정적 문서 검토로 Gradle 미실행. 기존 변경 보존, 예상 밖 이번 변경 없음. 다음은 단일 공동 기술 계약 개정안에 보완 후 양 서버 합의·정책/구현 승인.

## 2026-10-07 — 공동 기술 계약 R1~R5 직접 보완

- 브랜치 develop, Jira 없음. 사용자 보완 요청에 따라 기존 IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md 본문 개정; Identity 인계서 링크 설명, CURRENT_STATE/WORKLOG 갱신. 별도 반복 리뷰 문서 생성 없음.
- 변경: snapshot BASELINE/RESYNC/FEED/ACK 상태 조회와 bounded 완료 marker, 고정H2 scan/빈 page 규칙, 정상 source cleanup과 consumer GAP 구분. 신규/control 누락 legacy 이관·flag gate, 일반 로그인 unknown503와 기존 reissue replay/만료401 구분. majority+j/snapshot·복원 새 stream incarnation, 관측 capturedAt 기산, retry counters/status/헤더, canonical bytes·operation metadata 수명/테스트 추가.
- 유지/결정: 제품/15일/승인 운영 주기·기존4필드/LC 인증/무료 정책 유지. source120일·snapshot7일·token30분 및 새 operation/coverage metadata 보존/clock·commit budget은 제안, 실제 승인 아님. 코드/Identity/Jira/배포·외부 전송 미변경.
- 검증: git diff --check와 개정 문서 로컬 링크 검증, 기존 모호 문구 정적 대조. 문서 작업으로 Gradle 미실행. 기존 변경 보존·예상 밖 이번 변경 없음.
- 위험/다음: Identity exact API/error/이관 합의·Mongo/TTL/clock fixture·보존 승인·provider coverage 남음. 개정본 공동 검토 뒤 ADR/PLAN 동기화와 Jira/구현 승인; 판매/purge OFF 유지.

## 2026-10-07 — Identity 개정본 재검토 §7 확인

- 브랜치 develop, Jira 없음. 새 첨부는 이전 R1~R5와 개정본 판정 갱신 §7을 함께 포함함. 이전 지적을 모두 미해결로 반복하지 않고 최신 §7을 현재 로컬 문서와 대조했다.
- 변경: CURRENT_STATE/WORKLOG만. R1~R5 문서 대응 수용, manifestDigest 산출/응답 누락과 consumer-only restore/RESYNC 세대 fencing 부재를 확인. 권장: 별도 manifest hash 대신 contentDigest 명명+저장 manifest metadata 검증, consumerRecoveryGeneration을 snapshot/cursor/ACK/local CAS에 바인딩. 이는 아직 계약 수정/확정 아님.
- 추가: 전체 readiness와 purchase 한정 fail-closed 표현 충돌·SCANNED/LEGACY_PARTIAL 용어 혼재 확인. 현 로컬 5줄 결론2는 commit 기준120일을 쓰지 않아 첨부 요약 지적은 버전차 가능; 보존 기산은 현재 capturedAt 관측 기준임.
- 검증: 문서 대조와 git diff --check. 기록 작업으로 Gradle 미실행. 코드/타 서버/Jira/외부·계약 변경 없음. 기존 변경 보존, 예상 밖 수정 없음.
- 위험/다음: 복구 세대 authority/초기화·old worker barrier와 hash 명명·readiness/enum 정리 후 fixture 검증. 새 보존/TTL/배포 승인을 얻은 것으로 처리하지 않음.

## 2026-10-07 — 공동 계약 contentDigest·consumer 복구 세대 보완

- 브랜치 develop, Jira 없음. 사용자 보완 요청에 따라 공동 기술 계약 본문·CURRENT_STATE·WORKLOG만 수정했다.
- 변경: manifestDigest를 이미 정의한 contentDigest로 통일, status manifest/ACK equality와 소유/stream/H/generation/expiry 별도 검증 명시. consumerRecoveryGeneration authority/운영 role begin API·멱등/CAS·복구 barrier/local 설치·old worker/ACK fencing·새 baseline/완료 조건·schema/tests 추가. startup 실패와 runtime purchase-only gate·상태 enum 구분.
- 유지: 기존4필드 push/LC/무료/15일/운영주기 유지, 제안 보존·token상한/이관 승인 변경 없음. 추가 generation metadata는 기존 제안 보존 범위이며 자동 승인/영구 보존 아님.
- 검증: 문서 diff/링크/잔존 명명 정적 검사. 문서만 변경해 Gradle 미실행. 기존 사용자 변경 보존, 예상 밖 이번 변경 없음. 코드/Identity/Jira/외부 전송/운영 미변경.
- 위험/다음: 상대 exact route/auth·복구 barrier 실현성 및 source/consumer restore fixture 검증 후 계약 합의. 판매/purge·구현 승인과 개인정보 보존 승인은 별도 유지.

## 2026-10-07 — 공동 기술 계약 제안안 사용자 승인 반영

- 브랜치 develop, Jira 없음. 사용자의 제안안 승인을 현 개정본 정책·기술 설계 채택으로 기록했다.
- 변경: 공동 기술 계약 승인 상태/§3, ADR-004, CONTRACT_DECISIONS, PLAN-009, Identity 인계서, CURRENT_STATE/WORKLOG. 원천120일·snapshot7일·operation/coverage 최소 보존·token30분/skew60초와 설계/초기값 승인 범위 명시.
- 유지:15일 조건부 및 기존 운영 주기/무료·LC 계약 유지. 상대 서버 합의·법적/기술 검증 완료로 주장하지 않음. 실제 이관 시간·실행/코드 착수/Jira·배포/판매/purge/개별 예외 연장은 별도 승인.
- 검증: git diff --check·문서 정적 대조. 문서만 변경하여 Gradle 미실행. 기존 변경 보존, 예상 밖 이번 수정 없음. 코드/타 서버/외부 전송·설정 미변경.
- 다음: Identity에 승인된 개정본 기준 합의 요청, fixture/이관 측정 후 작업 분할·Jira 및 구현 승인. 불가능한 계약이나 추가 개인정보/이용 제한이 발견되면 재보고.

## 2026-10-07 — Identity 확인 전달 후 구현 작업 분할 안내

- 브랜치 develop, Jira 없음. 사용자 Identity 확인 완료를 계약 확인으로 기록하며 구현·배포 검증 완료로 확대하지 않았다.
- CURRENT_STATE/WORKLOG만 갱신. 기존009/010 범위 확인 후 공동 fixture 및 Mongo/restore 실현성 선행, Identity3개(I1 control/token, I2 capture/delivery, I3 snapshot/feed/recovery)·Billing3개(B1 account/lifecycle, B2 recovery consumer, B3 대사/purge)와 공동 staging으로 나누는 권장안 제시.
- 의존: I1/I2/B1은 schema/fixture 합의 뒤 병행 가능. B2는 I3 fixture로 병행 개발하되 실제 연동은I3 완료 후. B3 후보/worker skeleton은009, 실제 거래 확인은010, 활성화는013 gate. 금융 ingestion·refund와 동일 lifecycle CAS 검증, 무료 불변·flags OFF 유지.
- 검증: 문서 정적 대조·git diff --check. 기록 작업으로 Gradle 미실행. 기존 변경 보존·예상 밖 수정 없음. 코드/타 서버/Jira/배포/외부 메시지 없음.
- 다음: 첫 작업은 공동 fixture/exact schema·index/오류 매핑/이관 가정 검증과 B1/I1 상세 완료 기준·Jira 승인. provider 미구매 증명/운영 fixture는 남은 gate이며 Identity 확인으로 대체되지 않는다.

## 2026-10-07 — TMI-137 결제 에픽 아래 구현 작업 생성

- 브랜치 develop. 사용자 요청으로 Jira TMI-137 결제 기능 에픽/기존 자식 없음/작업 유형을 확인 후 parent 연결 작업8개 생성.
- 생성: TMI-199 공통C0 fixture/schema·실현성; TMI-200 Identity I1 발급/control; TMI-201 Billing B1 상품/account·탈퇴 수신(두 PR); TMI-202 Identity I2 journal/delivery; TMI-203 Identity I3 snapshot/feed/generation; TMI-204 Billing B2 recovery; TMI-205 Billing B3 대사/purge; TMI-206 공통C1 staging/복원 gate.
- 각 이슈에 범위·완료조건·선행/후속·기본OFF/실행 승인 구분·계약 파일 경로 기재. 모두 해야 할 일/미할당, 부모 에픽은 수정하지 않음. 실제 dependency link는 생성하지 않고 description에 의존 명시.
- 로컬 변경: CURRENT_STATE/WORKLOG만. Jira parent=TMI-137 검색으로8개 연결 재확인, git diff --check. 코드 변경 없어 Gradle 미실행. 기존 변경 보존·예상 밖 로컬 수정 없음. 구현/타 서버 코드/배포/판매/삭제 미수행.
- 다음: TMI-199부터 계약 fixture/실현성 gate 후 TMI-200/201/202 병행. TMI-205 실제 대사는PLAN-010, 판매/환불 준비와 purge 활성화는PLAN-013 별도 gate 유지.

## 2026-10-07 — TMI-199 공통 fixture·schema·Mongo 로컬 검증

- 브랜치 develop, 에픽 TMI-137/작업 TMI-199을 먼저 읽고 완료 기준에 맞춰 진행. 기존 dirty 문서 보존, Git commit/push/상태 변경 없음.
- 신규: PaymentLifecycleContractFixtureTest(39), PaymentLifecycleMongoFeasibilityTest(10), contracts/payment-lifecycle/v1의 snapshot-digest/protocol-cases/wire-examples/billing-indexes JSON4종, docs/contracts/TMI-199-PAYMENT-LIFECYCLE-FOUNDATION.md.
- 문서 수정: PLAN-009-payment-foundation-technical-contract/ADR-004에 stable account/ref와 별도 lifecycle/cursor activity 쓰기 정합성 반영. Identity 인계에 새 fixture 교차 실행 요청 연결, CURRENT_STATE 및 이 기록 갱신. production src/main/build dependency·Identity 코드/설정 미변경.
- 검증: Docker29.6.1/로컬 mongo7.0.14 disposable replica set. 실제 고정 atClusterTime User/H 읽기·후속 commit feed 연결, counter/journal 원자 rollback·snapshot write conflict, generation fencing,16index 옵션/partial unique, account/activity 분리 및 단일 멤버 stepdown/재선출 통과. unknown commit/SnapshotTooOld는 failCommand 주입이며 다중 노드/실제 eviction 검증으로 주장하지 않음. BSON Date nanos 손실/text 보존 확인, wire 정밀도 동일 근거를 Identity에 확인 필요.
- 테스트: 분리 contract test 성공 후 ./gradlew clean test285 tests/failures0/errors0/skipped0. 최초 clean test sandbox Gradle lock 실패는 승인된 권한 재실행으로 해결. git diff --check·로컬 링크/JSON 정적 검증 수행. 이49개는 test-only oracle/실험이며 production controller/worker 완료가 아님.
- 유지: UserWithdrawn4필드/무료·LC/보존/flag 불변. 실제 Store/provider/운영 Atlas·AWS 호출/판매/purge 없음. 테스트는 자기 random DB만 정리. 예상 밖 이번 변경 없음.
- 미완료/다음: 신규 fixture 독립 Identity 실행/DTO T·count·오류 envelope/index 후보 수락, 실제 다중노드·history 만료·성공 commit 응답유실/운영TTL·control 이관 실측/인증 route gate 남음. TMI-199 전체 완료로 표시하지 않음. Jira 전환·댓글 권한을 임의 추론하지 않아 변경하지 않았다. 상대 확인 후 TMI-200/201/202 착수 기준 동결.

## 2026-10-07 — TMI-199 Identity 독립 검증 회신 확인

- 브랜치 develop / TMI-199, 목표: 사용자 전달 검증 결과의 최신 §7을 확인하고 최초 미완료 기록을 갱신.
- 변경: docs/contracts/TMI-199-PAYMENT-LIFECYCLE-FOUNDATION.md, CURRENT_STATE 및 이 기록. Identity 독립44건·전체1233건/실패0/오류0/기존skip6은 상대 보고로 표시. JSON4종 SHA-256은 Billing 로컬에서 직접 일치 확인. Identity 테스트 source는 읽기만 했으며 해당 테스트 XML은 로컬 검색에서 찾지 못해 직접 실행 검증으로 주장하지 않음.
- 판단: 실제 엔티티 converter 왕복에서 User/outbox 시각은 동일 밀리초. 저장 전 고정밀 값과 혼합 시 digest 불일치이며 현행 publisher 결함 입증은 아님. 실제 Mongo·다중노드·운영 검증과 분리.
- 검증: 문서 변경만으로 Gradle 재실행 생략. git diff --check 및 결과 문서 로컬 링크 검사. 기존 사용자 변경 보존, 예상 밖 변경 없음.
- 유지/다음: 기존4필드/보존/무료/결제 정책·코드·fixture·flag 불변. 신규 authoritative 시각·exact DTO/숫자 타입·오류/auth/index 동결과 미검증 Mongo/staging gate 담당 배정 후 C0 완료 판단. Jira 완료 전환·댓글·타 서버 수정·배포 없음.

## 2026-10-07 — TMI-199 잔여 기술 선택 권장안

- 브랜치 develop / TMI-199. 목표: 사용자가 요청한 시각·DTO/인증/index·Mongo 검증 분담 권장안 설명.
- 근거: 승인 공동 계약 §5.2~5.7/§6, wire-examples와 TMI-199 결과 확인. FOUNDATION에 승인 전 권장안 추가, CURRENT_STATE 및 이 기록 갱신.
- 권장: 신규 영속 withdrawnAt 밀리초 통일/기존 canonical 정밀도·event 보존, wire 숫자 문자열/DB int64·T BSON Timestamp 구별, 신규 internal 전용 오류/최소권한 SigV4/additive index. 200/202/203/204 구현 통합 테스트 후206 운영 동등 staging; 실제 담당자/환경 승인 별도.
- 유지/위험: 기존 API/보존/무료/결제 정책 불변. Identity ingress·exact 규격 수락·운영 검증 미완료. C0 종료 조건은 제안이며 Jira 종료/후속 이관을 임의 실행하지 않음. 코드·타 서버·fixture·배포 미변경, 기존 변경 보존·예상 밖 변경 없음.
- 검증: 문서만 변경하여 Gradle 미실행, git diff --check 실행. 다음: 권장안 합의 후 공동 규격 동결 및 후속 이슈별 검증 기준 반영.

## 2026-10-07 — TMI-199 후속 기술 권장안 사용자 승인 반영

- 브랜치 develop / TMI-199. 목표: 사용자의 권장안 승인 반영, 기존 미검증 사실과 실행 권한 구분.
- 변경: IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT §4에 승인 규칙과200/202/203/204/206별 통과 기준 추가, TMI-199-PAYMENT-LIFECYCLE-FOUNDATION 승인 상태 갱신, CURRENT_STATE 및 이 기록.
- 동작/유지: 신규 영속 withdrawnAt 밀리초 통일·과거 event/digest 불변, wire decimal/영속 int64·T 별도, internal 오류/최소권한 SigV4/additive index 채택. 원래 wire4필드·무료/보존 정책·코드/fixture/타 서버 불변. 승인 범위 밖 Jira 전환·댓글·배포·판매·삭제 없음.
- 검증: 문서만 수정하여 Gradle 재실행 생략. git diff --check 실행. 기존 사용자 변경 보존, 예상 밖 변경 없음.
- 위험/다음: 이번 후속 exact 규격 상대 수락·endpoint nullable 목록·index manifest 검증·실제 담당자/환경 권한은 여전히 필요. 공동 C0 완료 판단 뒤 후속 구현 진행, 운영 gate 미충족 기능은 비활성 유지.

## 2026-10-07 — TMI-199 exact 필드·인덱스·숫자 경계 보완

- 브랜치 develop / TMI-199을 읽고 완료 기준 확인. 목표: 사용자 제시3개 남은 항목의 Billing 보완과 상대 수락 자료 작성.
- 변경: TMI-199-EXACT-WIRE-AND-INDEX-SPEC 신규, 공동 계약/FOUNDATION 링크, numeric-boundaries/identity-indexes 신규 JSON, PaymentLifecycleContractFixtureTest/PaymentLifecycleMongoFeasibilityTest 추가 테스트 및 index helper 추출, CURRENT_STATE/이 기록.
- 동작: nullable 필수 키와 상태별 생략·nested DTO exact안 작성. Billing16 기존 manifest byte 유지/Identity13 물리 명세 추가. 숫자 canonical/Long.MAX_VALUE/overflow/sequence0/U32 등39건+increment1건, Mongo index 재생성/방향/unique·partial/TTL 및 int64 9/10/MAX 정렬1건 추가.
- 테스트: ./gradlew clean test326 tests/failures0/errors0/skipped0. 최초 sandbox cache lock 실패 후 승인 실행 성공. JSON parse·로컬 링크·git diff --check 통과. 외부 서비스/운영 DB 미호출.
- 유지: production 코드·의존성·기존4필드/event digest·무료/보존/LC 불변. Identity 저장소 미변경. 신규 nested wire는 상대 수락안이며 이미 공동 확정했다고 주장하지 않음. 기존 사용자 변경 보존·예상 밖 변경 없음. Jira 상태/댓글·커밋/푸시·배포 없음.
- 다음/위험: Identity에 exact안·신규 fixture2개 전달 후 수락/숫자 독립 실행 필요. 실제 initializer/schema/collation/운영 이관·다중노드 등 gate는 후속 분담 유지. 해당 회신 후 C0 완료 판단.

## 2026-10-07 — TMI-199 Identity exact 수락·독립 실행 회신 반영

- 브랜치 develop / TMI-199. 목표: 사용자 첨부 최신 §8 수락·검증 내용을 확인하고 공동 C0 상태 갱신.
- 변경: TMI-199-EXACT-WIRE-AND-INDEX-SPEC §8/승인 상태, FOUNDATION 최신 결론·gate 상태, 공동 기술 계약 최신 수락, CURRENT_STATE 및 이 기록. 기존 기록 보존.
- 확인: Identity가 exact §5 wire/null/상태별 생략·nested DTO/오류·숫자와 §6 물리 manifest 수락. 신규42건/전체1276건 실패0·기존skip6은 상대 보고이며 실행/전체 XML 직접 재검증과 구분. 신규 fixture2종 SHA-256은 Billing에서 재계산해 일치 확인.
- 검증: 문서 변경만으로 Gradle 재실행 생략, git diff --check·문서 로컬 링크 검사. 기존 사용자 변경 보존·예상 밖 변경 없음. 코드·fixture·외부 API 동작 불변.
- 판단/다음: 기술 수락·독립 검증 보류 해소, 공동 규격 동결. 관련 변경 병합 확인 및 별도 Jira 종료 승인 남음. 병합 확인/Jira 댓글·전환·타 서버 수정·배포/판매/삭제 미수행.200/202/203/204/206 실제 구현·운영 gate 유지.

## 2026-10-07 — TMI-199 양측 병합 확인 및 Jira 완료

- 브랜치 develop. 사용자 '확인되면 지라 닫아줘' 승인으로 양측 병합 확인 후 TMI-199 완료 전환.
- 근거: GitHub API로 Billing PR#10 MERGED/develop/c7baf0b(2026-10-07T07:54:59Z), Identity PR#53 MERGED/develop/78a6e6ae(07:56:06Z) 확인. 로컬 develop의 계약/테스트 파일 반영 확인. 최초 sandbox 네트워크 오류 후 승인된 읽기 호출 성공.
- Jira: 기존 해야 할 일에서 허용 transition41 완료로 전환, 재조회 status=완료 확인. 댓글/다른 이슈 변경 없음.
- 로컬 변경: CURRENT_STATE와 WORKLOG만. 코드·타 서버·Git commit/push·배포 없음. 문서/상태 작업으로 Gradle 미실행, git diff --check 수행. 예상 밖 변경 없음.
- 유지/다음: C0 공동 계약·fixture 준비 완료이지 판매/자동삭제·실제 운영 검증 완료 아님. Billing201 및 Identity200/202 후속 구현과203/204/206 검증 gate 유지.

## 2026-10-07 — TMI-199 이후 작업 안내

- 브랜치 develop. 목표: 사용자에게 다음 구현 단위 설명. TMI-201/200/202 Jira 범위·상태 읽기 확인, 모두 해야 할 일.
- 변경: CURRENT_STATE와 이 기록만. Billing201의 상품 GET/구매 계정 POST 및 탈퇴 수신 두 PR 구분, Identity200/202 병행 가능·실제 연동 선행조건 안내.
- 유지: 신규 flag OFF, 실제 RevenueCat 지급/환불/자동삭제 제외. 무료 Claim/Grant·LC 계약 불변. 코드/타 서버/Jira/배포 변경 없음, 기존 기록 보존·예상 밖 변경 없음.
- 검증: 설명 작업으로 Gradle 미실행, git diff --check. 다음: 명시적 구현 요청 후 TMI-201 첫 단위 착수, Identity200/202는 해당 저장소에서 별도 진행.

## 2026-10-12 — 현재 진행 상태 재확인

- 브랜치 develop/c7baf0b. 목표: 사용자 재개 질문에 완료/미착수/다음 작업 구분 설명.
- 확인: 로컬 log·production 파일 목록·CURRENT_STATE/WORKLOG, Jira199 완료 및201/200/202 해야 할 일 실시간 조회. 마지막 단계는 TMI-199 공동 계약/테스트 병합·종료이며 Billing201 구현은 미착수. 기존326건 테스트 결과는 과거 결과로 구분.
- 변경: CURRENT_STATE 및 이 기록만. 기존 미커밋 기록 보존·예상 밖 변경 없음. 코드/타 서버/Jira/운영 설정 변경 없음.
- 검증: 상태 조회·기록 작업으로 Gradle 재실행 생략, git diff --check. 실제 배포/운영 준비 완료로 주장하지 않음.
- 다음: 명시적 구현 요청 후 Billing201 상품 조회·구매 계정 기반부터 시작. Identity200/202는 해당 저장소 별도 작업 및 실제 연동 gate 유지.

## 2026-10-12 — TMI-199~202 범위 설명

- 브랜치 develop / 관련 Jira199~202. 목표: 공동 계약·권한 발급·Billing 계정·탈퇴 원천 전달의 역할과 완료 결과 구분.
- 근거: 직전 조회한 Jira 범위 및 현재 기록.199 완료,200/201/202 후속 구현 범위 설명.200/202 공통 control/탈퇴 Transaction 조율 및202의 publisher OFF/capture 유지 설명.
- 변경: CURRENT_STATE/WORKLOG만, 코드·계약·Jira·타 서버·배포 변경 없음. 기존 기록 보존·예상 밖 변경 없음.
- 검증: 설명 작업으로 Gradle 미실행, git diff --check. 신규 구매 검증/기간권 지급/환불/실제 purge는 이 단계 완료로 간주하지 않음.
- 다음: 명시적 구현 요청 후 Billing201 착수; Identity200/202는 해당 저장소 별도 진행.

## 2026-10-12 — TMI-201 계획서 존재·위치 확인

- 브랜치 develop / TMI-201. PLAN-009 및 foundation/withdrawal 세부 계약 확인. 사용자에게 상위 계획과 보조 계약 연결,201 범위 밖 대사/자동삭제 구분 안내.
- 변경: CURRENT_STATE/WORKLOG만. 구 헤더의 Jira 미생성·과거 미승인 표현 잔존을 확인하고 최신 공동 계약/199 수락과 구분. 계획 본문·코드·Jira 변경 없음, 예상 밖 변경 없음.
- 검증: 문서 확인 작업으로 Gradle 미실행, git diff --check. 다음: 구현 전에 계획 상태·201 두 단위 범위 동기화, 이후 명시적 구현 요청에 따라 진행.

## 2026-10-12 — TMI-201 계획·세부 계약 최신 상태 정리

- 브랜치 develop / TMI-201. 목표: 사용자 요청대로 PLAN-009의 과거 상태/승인 표현과 실제 구현 범위를 정리.
- 변경: PLAN-009-payment-catalog-and-account, PLAN-009-payment-foundation-technical-contract, PLAN-009-withdrawal-reconciliation-and-purge-contract, CURRENT_STATE/WORKLOG. 기존 미커밋 기록 보존.
- 내용:199 완료·exact 수락 반영,201 PR1/PR2 포함/제외·테스트 분담 명시. account/ref 안정성+별도 lifecycle/cursor 쓰기 합의 동기화.203/204 복구·205 실제 대사/삭제·206/013 운영 활성화 분리. 과거 중복 승인 설명 정리하되 미검증 provider coverage·worker 운영 초기값·예외 보존 gate는 유지.
- 유지: API/무료/보존 정책 변경 없음, 새 승인 추론 없음. 코드·fixture·타 서버·Jira·커밋/푸시·배포 미변경. 예상 밖 변경 없음.
- 검증: 문서만 수정하여 Gradle 미실행. git diff --check·수정3문서 로컬 링크 검사. 다음: 명시적 요청 후 TMI-201 PR1 구현, 실제 연동/활성화는 상대 구현·staging gate 후.

## 2026-10-12 — TMI-201 구현 계획 상세 설명

- 브랜치 develop / TMI-201. 목표: 사용자 요청으로 최신 계획의 기능·내부 처리·완료 기준을 설명.
- 확인: PLAN-009 및 foundation 기술 계약의 API/가격/계정/cursor/limiter/security, 기존 탈퇴 계획의 수신/차단 범위. 실제 결제·지급·환불·물리 삭제와 구분.
- 변경: CURRENT_STATE/WORKLOG만. 코드·계약·Jira·타 서버·배포 미변경, 기존 변경 보존·예상 밖 변경 없음.
- 검증: 설명 작업으로 Gradle 미실행, git diff --check. 다음: 구현 요청 후 PR1→PR2 진행, 실제 활성화는 Identity200/202 및 staging gate 이후.

## 2026-10-12 — Google Play 일회성 상품 등록 가능 상태 확인

- 날짜·브랜치: 2026-10-12, feat/TMI-201-billing-product-purchase-account. 목표: 사용자 제공 Google Play Console에서 빌드 업로드 후 상품 등록 가능 여부 읽기 확인. 이번 조회의 별도 Jira 없음.
- 확인 동작: 테스트 및 출시 화면에서 프로덕션 2.0.1/출시율100%·내부 테스트2.0.1 표시를 확인. Play를 통한 수익 창출→제품→일회성 제품 목록은 결과 없음이며 만들기 링크가 노출됐다. 링크에서 상품 ID·이름·설명 입력 및 이후 사용 가능 여부/가격 단계가 있는 생성 폼에 정상 접근했다. 현재 상품 등록 시작 가능으로 판단.
- 한계·위험: 정확 Billing/RevenueCat SDK 포함 여부·버전, 가격 단계 진입/저장 성공, 상품 활성화·RC 매핑·sandbox 결제는 미검증. 빈 필수 입력 상태의 다음 버튼 비활성을 SDK 차단으로 해석하지 않음. 업로드/프로덕션 표시와 전체 결제 판매 준비 완료를 구분.
- 변경 파일: docs/codex/CURRENT_STATE.md 갱신, WORKLOG append만. 기존 미커밋 변경 보존, 예상 밖 변경 없음. 상품 정보 입력/생성·가격/판매국가/콘솔 설정 저장·타 서버·코드·외부 계약·Jira·Git 이력·배포 변경 없음.
- 검증: 실제 로그인된 Console 화면 읽기 및 git diff --check. 코드 변경 없는 조사로 Gradle 미실행. 실제 계정/앱 식별자·credential·고객 거래를 기록하지 않음.
- 다음: 상품 등록 요청 시 승인된 5개 기간권의 ID/설명·한국 가격·구매 옵션을 구체화하고 별도 등록 진행. RC 매핑과 실제 결제·환불/staging gate를 통과한 뒤 판매 검토.

## 2026-10-12 — TMI-201 API·cursor·탈퇴 후 유료권 질의 답변

- 브랜치 feat/TMI-201-billing-product-purchase-account / TMI-201. 목표: 사용자 이해 확인 및 승인 계약 설명.
- 근거: foundation API/Retry-After/cursor 규격, ADR-004 §5.10 callback 유실 탐색과 paid 자동 이전 금지, withdrawal 설계. products catalog/SDK 분리·빈배열, 동일user/environment ref, 검증 JWT sub·200 purchase 권한,201 consumer/202 publisher 구분.
- 변경: CURRENT_STATE/WORKLOG 기록만. 동일 번호 재가입 유료권 자동 복구 금지 설명이며 새 이전/환불 정책 승인·구현 아님. 다른 작업의 분기/기록 변경 보존, 이번 예상 밖 수정 없음.
- 검증: 문서 근거 확인·git diff --check, 코드 변경 없어 Gradle 미실행. 다음: 계획 구현/타 서버 연동은 별도 요청과 기존 gate 유지.

## 2026-10-12 — 탈퇴 후 재가입 유료권 비복구 확인

- 브랜치 feat/TMI-201-billing-product-purchase-account / 관련 TMI-201. 목표: 사용자의 탈퇴 후 잔여 유료권 복구 불필요 의견을 기존 제품 범위에 명확히 반영.
- 변경: CONTRACT_DECISIONS, ADR-004, FIXED_TERM_PREMIUM_PAYMENT_CONTRACT, CURRENT_STATE/WORKLOG. 재가입 유료권 비복구/비이전·수동 복구 기능 제외와 탈퇴 전 안내 명시.
- 유지: 같은 계정 재로그인·무료 미완료 승계·금전 기록 보존·Store/법정 환불 처리 불변. 법적 권리 포기/자동 환불·revoke로 해석하지 않음. 코드·UI·타 서버·Jira·배포 미변경, 기존 작업 보존·예상 밖 수정 없음.
- 검증: 문서 변경으로 Gradle 미실행, git diff --check. 다음: 프론트 탈퇴 안내에 반영하고 기존201 범위 유지, 실제 UI 구현은 별도 작업.

## 2026-10-12 — Google 5종 상품 등록 입력안 안내

- 브랜치: feat/TMI-201-billing-product-purchase-account. 이번 안내의 별도 Jira 없음. 목표: 상품 생성 시 각 입력값을 설명.
- 근거/결정: STORE_PRODUCT_REGISTRATION-2026-09-16 및 FIXED_TERM_PREMIUM_PAYMENT_CONTRACT의 Apple 실제 premium1d/3d/7d/14d/28d·한국어 이름/설명·9000/19000/29000/49000/69000원과 대한민국 판매 방향을 재사용. Google 생성 완료로 표현하지 않음.
- 변경 파일: CURRENT_STATE 갱신·WORKLOG append만. 입력안 설명이며 Store 입력/저장·상품 생성/아이콘 업로드·가격/세금 설정·RC 매핑·코드·외부 계약·타 서버·Jira·Git 이력·배포 변경 없음. 기존 사용자 변경 보존, 예상 밖 변경 없음.
- 유지: fixed-term consumable one-time·재구매·자동갱신 없음·Billing 기간/stacking·무료권 보존. Google 기간형 rental/자동갱신 상품으로 변경하지 않음.
- 검증: 문서 대조·git diff --check, 설명/기록만으로 Gradle 미실행. 실제 Google 구매 옵션·세금 포함 최종 가격과 RC/SDK completion은 등록/E2E에서 확인 필요.
- 다음: 상품 등록 요청 시 해당 입력안을 실제 폼에 적용하고 가격·한국 판매/구매 옵션을 검증. 실제 판매는 기존 결제 gate 이후.

## 2026-10-12 — Google 1일권 상품 폼 입력만 수행

- 날짜·브랜치: 2026-10-12, feat/TMI-201-billing-product-purchase-account. 별도 Jira 없음. 목표: 사용자 명시 요청대로 1일권 폼을 채우고 생성 전 중단.
- 실제 동작: 제품 ID premium1d, 이름 토선생 프리미엄 1일 이용권, 설명 '24시간 동안 모의고사와 AI 피드백 무제한 이용. 자동 갱신 없음.' 입력. 다음 단계 구매 옵션 ID premium1d·유형 구입, 대한민국 외/새 지역 사용 불가를 폼에 적용하고 대한민국 가격 KRW9000 입력. 최종 가격 행 KRW9000/사용 가능 확인. 활성화/임시보관함 저장 미실행, 아이콘 미등록. 미저장 폼을 다음 사용자 작업용으로 유지.
- 변경 파일: CURRENT_STATE·WORKLOG 기록만. 생성/영구 저장·판매 활성화·RC 매핑·다른 상품·코드/계약·타 서버·Jira·Git 이력·배포 변경 없음. 기존 변경 보존·예상 밖 변경 없음.
- 검증: 실제 입력값/가격 행 UI 확인, /private/tmp/google-premium1d-unsaved.jpg 화면 증거 저장 및 git diff --check. 코드 변경 없어 Gradle 미실행. SDK consumption/결제·세금 적합성·실제 상품 저장은 검증하지 않음.
- 유지/다음: 기존 fixed-term/자동갱신 없음/한국 판매·9000원 계약 유지. 새로고침/이탈 시 미저장 값 유실 가능. 사용자가 검토 후 별도 생성 요청하기 전 저장/활성화하지 않는다.

## 2026-10-12 — Google 1일권 설명 간소화

- 브랜치 feat/TMI-201-billing-product-purchase-account, 별도 Jira 없음. 목표: 사용자 지정 짧은 상품 설명 반영.
- 동작: 미저장 Google 폼의 설명만 `토선생 1일 무제한 이용권`으로 변경, 실제 입력값과 스크린샷 확인. 생성/임시보관함 저장/활성화 미실행, 상품 이름·ID 유지. 입력 화면 유지.
- 변경 파일: CURRENT_STATE 갱신·WORKLOG append, 증거 /private/tmp/google-premium1d-description-unsaved.jpg. 코드·도메인/외부 계약·타 서버·Jira·Git 이력 변경 없음, 기존 변경 보존·예상 밖 변경 없음.
- 검증: UI 값 확인·git diff --check, 코드 변경 없어 Gradle 미실행. 기간/가격/자동갱신 없음 정책 유지. 미저장 값 유실 가능하며 다음은 사용자 검토/별도 생성 요청 후 진행.

## 2026-10-12 — TMI-201 PR1 상품·구매 계정 기반 구현

- 브랜치: feat/TMI-201-billing-product-purchase-account. 목표: 사용자 구현 요청에 따라 승인된 PLAN-009의 PR1만 구현. Jira TMI-201 현황 조회만 수행했으며 상태/완료 변경 없음.
- 변경 파일: domain/payment의 config·API·DTO·service·repository·exception, payment-foundation-indexes.json, public security/filter/writer와 application.yml, payment 테스트4종·기존 SecurityConfigTest, PLAN-009/기반 계약·runbook·CURRENT_STATE·WORKLOG. 기존 정책/외부 작업 문서 변경은 보존했다.
- 동작: GET products는 서버 catalog의 유효한 활성 매핑만 반환하며 빈 목록과 장애를 분리. POST purchase-account는 verified sub/environment로 stable ref를 원자 생성/반환하고 lifecycle fence와 별도 ACCOUNT_LOOKUP cursor를 갱신. 기존 account/ref를 회전하지 않으며 cursor lease/backoff 보존. 알려진 WITHDRAWN은403, 불명 연결은503. 무료 데이터 생성·소비 없음.
- 인증/운영: read/purchase+MEMBER·public/internal connector 격리·no-store·16KiB 응답·body/query 거절·기본OFF flag. Mongo fixed-minute limiter60/10과429 Retry-After, HMAC subject, privacy-safe service/time/trace 로그와 INTERNAL span. 수락 manifest16개 중 PR1의12개만 additive initializer로 검증하고 core schema v4 유지, drop/recreate 없음.
- 검증: 최종 ./gradlew clean test363개 성공·실패0·오류0·skip0. 동시20요청 수렴·환경/사용자 격리·부분 실패 rollback·cursor 보존·withdrawn/inactive ref·limiter·index drift·오류 라벨 주입·Controller 권한/오류/관측·기존 무료/internal 회귀 포함. 최초 실패의 기존 MVC bean 구성과 failpoint primary invalidation 문제를 수정하고 전체 재실행했다. 새 의존성/실제 외부 API 호출 없음.
- 유지/결정: 무료 TrialClaim·Grant·Reservation·owner 계약 불변. 결제 지급/환불/재가입 복구 기능 추가 없음. PR1 구현은 TMI-201 전체 완료·판매/자동삭제 승인 아님. flag OFF, commit/push·배포/타 서버 수정 없음.
- 위험/다음: PR2 strict 탈퇴 수신·비활성·cleanup 후보 및 생성-탈퇴 동시성 구현 필요. 실제 성공 commit 응답유실/다중노드 failover·네트워크 timeout budget·실제 catalog migration/Identity/ALB/RC/Store 연동은 미검증으로 staging gate 유지. 후속204 recovery/205 거래 대사·purge 별도. 다음은 PR1 리뷰 후 PR2 구현.

## 2026-10-12 — TMI-201 PR2 구현 계획 설명

- 브랜치: feat/TMI-201-billing-product-purchase-account. 목표: 2차 구현 범위/동작/완료 기준 설명. PLAN-009와 탈퇴 수신·대사·삭제 계약을 확인했다.
- 내용: Identity SigV4 탈퇴 수신, strict decode·inbox 멱등, 동일 Transaction의 lifecycle/account/ref 차단·cleanup 후보, 계정 없는 탈퇴 및 발급과 경합 처리. 15일은 후보 자격 시각이지 자동 삭제 시각이 아니며 복구204/대사·삭제205/실제 공동 검증206과 구분했다.
- 변경 파일: CURRENT_STATE·WORKLOG 기록만. 기존 코드/문서 변경 보존. 인증·무료권·환불·재가입 정책 불변, Jira/타 서버/외부 설정/commit/push/배포 변경 없음.
- 검증: 문서 대조와 git diff --check. 설명 작업으로 Gradle 재실행 없음(PR1 직전363개 통과 기록 유지). 위험: 탈퇴 전달 전 local ACTIVE는 Identity 실시간 상태를 보장하지 않으며 staging/누락 복구 gate 필요. 다음: 사용자 요청 시 PR2 구현, 신규 flag OFF 유지.

## 2026-10-12 — Google 나머지 4종 상품 입력 준비

- 브랜치 feat/TMI-201-billing-product-purchase-account. 이번 외부 폼 작업의 별도 Jira 없음. 목표: 1일권처럼 나머지도 준비하라는 사용자 요청 수행. 앞선 생성/저장 보류 요청이 있어 입력만/초안 저장까지 범위를 비동기 확인하고, 답변 전에는 미저장 준비만 수행.
- 동작: 4개 별도 Google 생성 탭에 premium3d/7d/14d/28d, 기존 승인 프리미엄 이름, `토선생 3일/1주/2주/4주 무제한 이용권` 설명 입력. 구매 옵션 ID는 각 제품 ID와 동일·유형 구입, 대한민국 외/새 지역 사용 불가, 대한민국KRW19000/29000/49000/69000 폼 적용. 실제 가격 행 확인. 4주권의 KRW57000 초과 다중수량 결제 불가 안내 확인(단일 상품 가격 오류로 단정하지 않음).
- 상태: 활성화/임시보관함 저장 미실행·아이콘 미등록. 기존1일권과 4종 입력 탭은 handoff로 유지. /private/tmp/google-premium3d-unsaved.jpg, google-premium7d-unsaved.jpg, google-premium14d-unsaved.jpg, google-premium28d-unsaved.jpg 화면 증거 확보.
- 변경 파일: CURRENT_STATE·WORKLOG 기록만. 코드/계약·타 서버·Jira·배포·Git commit/push 변경 없음. 동시 진행의 PR1 구현/기록과 기존 사용자 변경 보존, 이번 범위의 예상 밖 변경 없음.
- 검증: DOM/가격 행 확인·git diff --check. 코드 변경 없어 Gradle 재실행 없음. 증거 합성용 시스템 Python에 Pillow가 없어 합성 이미지 생성은 생략, 원본4개 사용. 실제 상품 저장/SDK 결제/세금 적합성·RC 매핑은 검증하지 않음.
- 위험/다음: 미저장 폼은 새로고침/탭 종료 시 유실 가능. 저장 범위 답변 후 승인 범위에서 진행하며 판매 활성화는 별도. 제품ID는 생성 후 변경/재사용 불가.

## 2026-10-12 — Google 나머지 4종 초안 저장 완료

- 날짜·브랜치: 2026-10-12, feat/TMI-201-billing-product-purchase-account. 별도 Jira 없음. 목표: 사용자 `초안 저장까지` 승인에 따라 준비된 나머지 네 상품 저장.
- 동작: premium3d/7d/14d/28d의 임시보관함 저장 실행. 각 상세 화면에서 기존 프리미엄 이름·토선생 해당 기간 무제한 이용권 설명·동일 구매 옵션 ID·구입·대한민국·임시 상태 확인. 저장 전 가격은 KRW19000/29000/49000/69000 확인. 최종 목록의 네 상품 활성 구매 옵션 0 확인. 활성화 버튼은 누르지 않았고 아이콘 업로드 없음.
- 관측: 최종 목록에는 premium1d도 존재하며 활성 구매 옵션 1로 표시됐다. 이전 미저장 기록과 다른 외부 상태이며 이번 작업에서 1일권 저장/활성화 또는 비활성화는 수행하지 않았다. 기존 1일권 탭과 최종 목록 유지.
- 변경 파일: CURRENT_STATE 갱신·WORKLOG append만. 기존 동시 작업 보존, 코드·외부 계약·타 서버·Jira·commit/push·배포 변경 없음.
- 검증: 각 저장 후 상세 임시 상태와 최종 목록 UI 확인, 증거 /private/tmp/google-products-drafts.jpg 확보. git diff --check 수행. 코드 변경 없어 Gradle 재실행 없음.
- 유지·위험·다음: fixed-term 구입·한국 가격·Billing 기간/stacking 계약 유지. RC 매핑·SDK 결제·세금 적합성은 미검증. 다음은 아이콘 준비 및 별도 활성화 요청/기존 연동 gate 확인. 1일권 외부 상태는 관측 사실만 기록하고 변경하지 않았다.

## 2026-10-12 — Google 1일권 구매 옵션 비활성화

- 날짜·브랜치: 2026-10-12, feat/TMI-201-billing-product-purchase-account. 별도 Jira 없음. 목표: 사용자 변경 요청에 따라 기존 활성 1일권도 판매 비활성 상태로 변경.
- 동작: premium1d 상세의 활성 상태 확인 후 구매 옵션 수정의 비활성화 및 확인 dialog 적용. 저장 완료 표시와 상세 재진입 후 비활성 상태 확인. 이름·토선생 1일 무제한 이용권 설명·대한민국 KRW9000 유지. 나머지 네 초안 수정 없음, 삭제/활성화 미실행.
- 변경 파일: CURRENT_STATE 갱신·WORKLOG append. 코드·도메인 계약·타 서버·Jira·commit/push·배포 변경 없음, 기존 작업 보존.
- 검증: UI 저장 완료·비활성 상태 확인, /private/tmp/google-premium1d-inactive.jpg 증거 저장, git diff --check. 코드 변경 없어 Gradle 미실행.
- 유지·결정·위험·다음: fixed-term 구입·가격/기간 정책 유지. 1일권은 생성 전 초안이 아니라 생성된 구매 옵션의 비활성 상태다. RC/SDK 실제 결제는 미검증. 다음은 아이콘/연동 검증 및 별도 판매 활성화 요청 후 진행.

## 2026-10-12 — Apple 소모품 상품 심사 준비 요건 확인

- 날짜·브랜치: 2026-10-12, feat/TMI-201-billing-product-purchase-account. 별도 Jira 없음. 목표: 기존 생성 상품의 심사 제출 준비 항목 안내.
- 근거: STORE_PRODUCT_REGISTRATION-2026-09-16와 Apple 공식 Submit an In-App Purchase, In-App Purchase information, Configure In-App Purchases for review in the sandbox, configuring overview/App Review 문서 확인.
- 결과: 첫 소모품 유형은 새 앱 버전과 동일 submission에 제출. 실제 결제/기간권 이용 가능한 빌드, 상품별 review screenshot/메모와 metadata, 심사 계정/경로, 활성 Paid Apps Agreement·은행/세금, 사전 sandbox 테스트 필요. 1024x1024 홍보 이미지는 일반 상품 심사용 스크린샷과 구분. 한국 storefront 제한을 심사 메모에 명시해야 하며 첫 유형 승인 후 동일 유형 추가 상품은 조건 충족 시 별도 제출 가능.
- 서비스 gate: App Review는 sandbox 구매를 사용하므로 승인된 SANDBOX→staging 분리에서 제출 빌드의 로그인/구매 account/Billing 사용권 조회·시험 이용이 실제로 연결되는지 검증 필요. 임의 production sandbox 허용 또는 환경 계약 변경 없음. 무료/paid domain 계약 유지.
- 변경 파일: CURRENT_STATE 갱신·WORKLOG append만. 코드·ASC 입력/제출·계약 동의·타 서버·Jira·commit/push·배포 변경 없음. 기존 변경 보존.
- 검증: 등록 기록/공식 문서 대조·git diff --check. 분석만 수행해 Gradle 미실행. 현재 ASC 상태/상품별 누락 필드/앱 구현·계정 계약 실시간 상태는 미확인. 다음: 결제 E2E와 심사용 자료 준비 후 ASC 실제 누락 항목 점검.

## 2026-10-12 — Apple 심사 전 상품 조회 설명

- 날짜·브랜치: 2026-10-12, feat/TMI-201-billing-product-purchase-account. 별도 Jira 없음. 목표: 등록만으로 심사 전 조회 가능한지와 실제 앱 조회 흐름 설명.
- 내용: Apple 공식 sandbox 심사 안내 및 RevenueCat Displaying Products/Sandbox/Creating Offerings 문서 대조. 승인 전 실제 Store 상품 조회/구매는 sandbox에서 가능, production 제공은 심사 승인 필요. 상품 등록만으로 SDK 조회 성공 보장하지 않으며 유료 계약·상품 구성·Bundle ID/SDK iOS 키·RC Offering 매핑과 실제 앱 검증 필요.
- 우리 흐름: SDK getOfferings에서 premium Offering 패키지의 Store 상품/현지 가격을 읽고 Billing catalog의 ID/기간 정책과 결합. 9/16 Apple 5종 Offering 연결 기록은 있으나 SDK 조회 성공 미검증. 서버 catalog는 Store 등록에서 자동 생성되지 않음. 소모품 RC Entitlement 미연결·Billing 기간 판정 유지.
- 변경 파일: CURRENT_STATE·WORKLOG만. 코드·외부 설정·타 서버·Jira·commit/push·배포 변경 없음. 기존 변경 보존.
- 검증: 공식 문서/등록 기록 대조·git diff --check, 분석 작업으로 Gradle 미실행. 실제 iOS SDK 및 계약 현재 상태는 미확인. 다음: 개발/TestFlight 빌드에서 premium 5개 상품 조회 확인 후 sandbox 구매/권리 반영 검증.

## 2026-10-12 — TMI-201/202/205 책임과 탈퇴 상태 질의 설명

- 브랜치: feat/TMI-201-billing-product-purchase-account. 관련 Jira: TMI-201/202/205. 목표: 사용자 네 가지 이해 확인.
- 결정/동작 설명: endpoint 수신201·Identity 발송202, WITHDRAWN은 Billing lifecycle/account 상태이며 userId/ref 값 변경 아님. Identity 로그인/발급 차단과 Billing 구매 경로 차단을 분리한다. 발급-탈퇴 공통 DB Transaction/fence,201의 비삭제·후보 기록과205의 거래 확인 후 조건부 삭제를 구분했다.
- 변경 파일: CURRENT_STATE·WORKLOG만. 코드/정책/타 서버/Jira/외부 설정 변경 없음, 기존 변경 보존. 검증: 기존 계약 대조·git diff --check, 설명 작업으로 Gradle 미실행. 위험/다음: 전달 지연은 DB 직렬화만으로 해결되지 않으며 후속 복구·연동 gate 유지, PR2 구현은 별도 요청 시 진행.

## 2026-10-12 — Billing 구매 계정의 탈퇴 표시 의미 설명

- 브랜치: feat/TMI-201-billing-product-purchase-account / TMI-201. 목표: 로그인 계정과 결제 관리 기록 구분.
- 설명: userId·구매 연결 ID의 관리 기록을 보존한 채 로컬 상태를 ACTIVE에서 WITHDRAWN으로 바꾸는 의미. Identity 통지에 따른 신규 사용 차단이며 별도 회원가입/ID 변경/즉시 삭제/자동 환불이 아니다.
- 변경 파일: CURRENT_STATE·WORKLOG만. 정책·코드·외부 설정·Jira 변경 없음. 검증: git diff --check, 설명 작업으로 테스트 재실행 없음. 위험/다음: Identity 실시간 상태와 로컬 상태를 혼동하지 않으며 기존 PR2/연동 gate 유지.

## 2026-10-12 — TMI-201 PR2 탈퇴 수신·구매 계정 차단 구현

- 브랜치: feat/TMI-201-billing-product-purchase-account / Jira TMI-201. 목표: 사용자 요청으로 PR2 구현, PR1 및 기존 문서 변경 보존.
- 변경 파일: PaymentWithdrawalController/Decoder/Service/record/Exception, PaymentProperties·PaymentFoundationInitializer·SecurityConfig·application.yml·payment-withdrawal-indexes.json, withdrawal decoder/controller/observability/security 테스트 및 기존 foundation/catalog/security 테스트, PLAN-009·두 세부 계약·runbook·CURRENT_STATE·WORKLOG.
- 동작: POST /internal/v1/payments/accounts/withdrawal/events의 기존4필드 strict 검증·canonical digest 후 inbox/lifecycle CAS/account WITHDRAWN/ref inactive/cleanup 후보를 한 snapshot/majority+j Transaction으로 반영하고204. 동일 event/digest DUPLICATE·동일 탈퇴시각 새 event NOOP; 다른 digest/탈퇴시각409, 장애503. 구매 계정 없는 탈퇴도 terminal fence만 생성하며 새 구매 계정은 만들지 않는다. 계정/ref ID/cursor/무료 기록 보존, 발급과 동일 lifecycle 쓰기로 경합 수렴.
- 개인정보/보존: inbox는 식별 event/digest/결과/수신/purge 시각만 저장(user/ref/payload 제외),120일 TTL. lifecycle/account에 withdrawnAt Date+canonical text를 남겨 legacy 나노초 충돌 판정. cleanup WAITING 후보는 원 탈퇴+15일(밀리초 올림)이며 반복 알림으로 기산/lease 재설정 없음. 실제 삭제·provider 대사·지급·환불·재가입 권리 이전 없음.
- 인증/운영: 신규 withdrawal flag 기본OFF, public 결제 flag와 독립. transactional index/환경 matching/startup 검증. TEST는 Identity role만; 운영은 기존 Lattice IAM+SigV4 신뢰 경계/SG 전제 유지하며 앱 내 서명 검증 추가 안 함.4개 additive index를 별도 manifest로 준비, 기존core v4/drop 금지 유지. 개인정보 없는 service/time/trace 로그·INTERNAL span/저카디널리티 metric.
- 검증: ./gradlew clean test393개 성공·실패0·오류0·skip0(PR1 대비30개). 실제 Mongo replica-set의 발급-탈퇴10회 경합/동시 duplicate/rollback/unknown commit label·transient 주입/환경격리·overflow·TTL drift/무료불변, Controller 권한/응답·strict decoder·span 종료 확인.16개 manifest 수락 fixture 일치·git diff --check 통과. 최초 sandbox Gradle cache lock 권한 오류 뒤 승인된 실행으로 검증, 코드 테스트 실패 없음.
- 위험/미수행: 실제 Lattice wrong role/unsigned/direct task 차단은 AWS policy/SG staging 검증 대상이며 mock principal 테스트로 대체하지 않음. 실제 성공 commit 응답유실/다중노드 failover·Identity 발송·recovery coverage·운영 migration 미검증. Identity는 wire 확인만 읽었고 타 서버 수정/외부 API/배포/Jira 상태/commit/push 없음.
- 결정/다음: TMI-201 PR1+PR2 로컬 구현 완료, 사용자 PR 검토/병합/종료와 실제 활성화는 별도. Identity202 발송과 연동 검증,204 recovery/205 실제 거래 확인·조건부 삭제/206 공동 gate를 후속으로 유지. 기존무료/owner·결제/환불 제품 계약 불변.

## 2026-10-12 — PLAN-009 연결 Jira 범위 재확인

- 날짜·브랜치: 2026-10-12, feat/TMI-201-billing-product-purchase-account. 목표: 사용자 PLAN-009 개발의 Jira 종료 범위 질문 답변.
- 근거/결과: 최신 PLAN-009 직접 구현은 TMI-201의PR1/PR2. 관련 트랙199 계약,200 권한,202 탈퇴 전달,203/204 누락 복구,205 조건부 대사/purge,206 공동 staging.199~206 전부를201 한 작업으로 포함하지 않으며205는010 거래 증거,206/판매·삭제 활성화는013 후속 gate와 연결.
- 실제 Jira 조회:199 완료,200~206 해야 할 일. 로컬 PLAN-009의PR2 최종 회귀 검증 중 표기는 Jira 종료/병합/배포 완료와 구분. Jira 상태 변경 없음.
- 변경 파일: CURRENT_STATE 갱신·WORKLOG append만. 코드·계약·타 서버·외부 설정·commit/push·배포 변경 없음, 동시 작업 보존.
- 검증: PLAN-008/009·CURRENT_STATE와 Jira 조회 대조, git diff --check. 설명 작업으로 Gradle 재실행 없음. 위험/다음: Jira 상태와 실제 개발 진행은 다를 수 있으므로201 검증·리뷰·병합 확인 후 별도 종료 처리, 후속010 및 공동 검증 진행.

## 2026-10-12 — TMI-201 전체 구현 코드 설명

- 브랜치: feat/TMI-201-billing-product-purchase-account / TMI-201. 목표: 사용자 요청대로 PR1/PR2 전체 파일과 실행 흐름 설명.
- 확인/내용: payment16개 production 파일, public/internal 인증/응답,16개 index와 runbook 확인. 실제 코드 발췌와 파일 링크로 catalog·stable ref/별도 lifecycle/cursor·탈퇴 atomic commit·멱등/경합·limiter/flag/보존 후보 설명. 실제 Store 조회/구매 지급/환불/삭제가 미포함임을 구분.
- 변경 파일: CURRENT_STATE·WORKLOG만. 코드/계약/타 서버/외부 설정/Jira/commit/push/배포 변경 없음. 기존 동시 작업 기록 보존.
- 검증: 소스 대조·git diff --check, 설명 작업으로 Gradle 재실행 없음. 직전 전체393개 성공 기록을 인용하며 실제 Identity/AWS/Store 검증으로 확대하지 않음. 위험/다음: flag OFF와 staging/recovery gate 유지, 사용자 코드 검토 후 후속 요청에 따라 진행.

## 2026-10-12 — TMI-204 전용 계획서 존재 확인

- 브랜치: feat/TMI-201-billing-product-purchase-account / TMI-204. 목표: 사용자 질문에 따라 저장소 계획 문서 유무 확인.
- 결과: docs 파일명/내용 검색상204 전용 구현 계획 없음. PLAN-009, 탈퇴 수신·대사·삭제 설계, 공동 기술 계약 및 TMI-199 exact 규격에 snapshot/feed/checkpoint/generation·복원/ACK 검증 기준 존재. 설계가 전혀 없는 것이 아니라 구현 단위 문서로 모으는 작업이 필요함을 안내.
- 변경 파일: CURRENT_STATE·WORKLOG만. 코드/계약/타 서버/Jira/외부 설정 변경 없음. 검증: rg 검색·git diff --check, 분석 작업으로 Gradle 미실행. 위험/다음: 상대202 진행은 사용자 전달 기준,203 실제 구현 확인과 별도로204 계획 작성 가능. 이번 요청에서 신규 계획서는 작성하지 않음.

## 2026-10-12 — TMI-204 탈퇴 누락 복구 구현 계획 작성

- 브랜치: feat/TMI-201-billing-product-purchase-account / TMI-204(상위137). 목표: 승인된 공동 계약과201 코드를204 구현 단위로 구체화.
- 변경 파일: docs/plans/TMI-204-withdrawal-recovery.md 신규, PLAN-009 연결, CURRENT_STATE 갱신·WORKLOG append. 코드/production schema/의존성/타 서버/Jira/외부 설정·commit/push/배포 변경 없음.
- 내용/결정: 기존203 exact wire·numeric/canonical·generation/coverage 의미 재사용.201 applier 추출 및 모든 account/push/복구/ACK 쓰기의control CAS,가짜 event 없는 snapshot facts·bounded page commit/7일 staging 재해시·최종marker,고정H2 feed/ACK outbox/응답 유실,restore barrier·운영 세대 설치·old worker 보호,임시 자료 정리/기본OFF·관측/테스트/4개 PR 단계 정리. 신규로컬7개 collection/index와worker·운영 도구/서명 의존성은 제안이며 동결/승인 필요로 구분.
- 유지: UserWithdrawn4필드·기존public DTO·무료권/재가입 유료 미이전·15일 조건부정리/보존 불변.204 임시 복구자료 정리와205 실제 구매계정 purge를 분리. Identity202 진행은 사용자 전달이며203 실제 구현/배포 완료를 주장하지 않음.
- 검증: 공동 계약/수락 exact 규격/fixture/201소스 대조, Markdown 상대 링크·git diff --check. 문서 작업으로 Gradle 미실행. 신규코드/실제203/AWS/복원/부하 검증 없음.
- 위험/다음: local schema/validator·signer module/version·운영설치 도구 동결, 공통control hotspot/2초budget·page20 검증,203 exact 지원/stream 운영 입력·실제generation 복원 gate 필요. 계획 검토 및 구현 승인 후PR1부터 진행, 판매/자동삭제 활성화 별도.

## 2026-10-12 — TMI-204 계획 상세 설명

- 브랜치: feat/TMI-201-billing-product-purchase-account / TMI-204. 목표: 사용자에게 복구 필요성과 구현 계획 전체를 쉽게 설명.
- 내용: 최신 계획 원문 확인 후 실시간push 재시도와 별도 복구의 차이,snapshot T/H와feed H2,페이지 검증·checkpoint/ACK 유실,복원 전후generation·공통control CAS/barrier,coverage 구분,저장소7종·임시자료 보존,201 변경·203 의존·4개PR·테스트/승인 항목 설명.
- 변경 파일: CURRENT_STATE·WORKLOG만. 코드/계획 본문/계약/타 서버/Jira/외부 설정/배포 변경 없음. 제품/보존/무료·유료 이전 정책 및 기본OFF 유지.
- 검증: 계획 원문 대조·git diff --check, 설명 작업으로 Gradle 미실행. 위험/다음: 로컬schema·worker 상세는 아직 제안, 구현/판매 승인 아님. 사용자 검토 후 기술안 동결 및 구현 승인 필요.

## 2026-10-12 — TMI-204 11개 이해 확인 질의

- 브랜치: feat/TMI-201-billing-product-purchase-account / TMI-204. 목표: 사용자의 계획 해석 확인 및 오해 교정.
- 설명/결정: Billing DB 진행기록을 기준으로Identity203 API를능동조회하며상대DB직접접근아님.피드생성은계속하고H2만고정.페이지최대20의원자적용과전체검증marker구분,ACK는commit된범위확인.새generation은복구실행세대이며일반retry마다변경않음,version CAS와별개.과거자료소실은증거없이COMPLETE불가이나현재stream CAUGHT_UP과구분·예외범위승인별도.7개recovery collection은모두신규제안이며기존201 lifecycle/서비스는연결변경.
- 변경파일: CURRENT_STATE·WORKLOG만. 코드/계획/계약/타서버/Jira/외부설정/배포변경없음.검증: git diff --check,설명작업으로Gradle미실행.위험/다음: legacy gap을자동성공/판매·purge허용으로해석하지않음,기술안검토·승인후구현.

## 2026-10-12 — 복구 불가능한 legacy 범위의 종료 방안 제안

- 브랜치: feat/TMI-201-billing-product-purchase-account / TMI-204. 목표: UNKNOWN 상태 장기 방치 우려에 대한 권장 운영 방향 제시.
- 제안: 과거 completeness와worker 종료/출시cohort/계정별purge를분리.출시전실제운영구매account/ref/거래·과거발급·복원현황검증후 영향없음증명이가능하면 capture/발급fence/oldtoken drain·snapshot/feed기준점으로신규범위활성화 승인 검토.과거UNKNOWN을COMPLETE로재표기하지않음.영향건은별도case로분리·7일내최초검토와명시결정기한·승인된최소보존/expiry처리,거래부재를추측한삭제나무기한보존금지.
- 변경파일: CURRENT_STATE·WORKLOG만. 제품/공동계약/계획/코드/Jira/외부설정변경없음,운영데이터조회없음. 검증: git diff --check,설명작업으로Gradle미실행.위험/다음: 현DB빈값만으로과거부재를증명하지않으며제안은승인전.사용자동의후204 gate/종료조건을구체화하고실제환경근거를확인해야함.

## 2026-10-12 — 기존 결제 대상 없는 최초 출시의 legacy 범위 승인 반영

- 브랜치: feat/TMI-201-billing-product-purchase-account / TMI-204. 목표: 사용자 기존대상없음확인 및 방향승인을 문서에 반영.
- 결정: 기존구매계정/ref발급/실거래없음을사용자확인으로기록(운영DB/Store조회아님).과거전체legacy COMPLETE는최초결제출시필수조건에서제외,결제시작기준점이후capture·ACTIVE MEMBER발급fence·oldtoken/요청drain·201/203/204연동은검증.과거불명완전성은사실대로두고관련증명작업을출시필수에서제외·무기한재시도않음.기존대상발견시재평가.
- 변경파일: TMI-204계획§2.3/위험/완료gate,ADR004,공동기술계약,CONTRACT_DECISIONS,CURRENT_STATE,WORKLOG.기존변경보존.코드/타서버/Jira/외부설정/commit/push/배포변경없음.
- 유지: wire/coverage enum 의미불변,기준점이후GAP·복원generation보호·205거래확인/보존·조건부purge/무료정책불변.이번승인은한정출시범위방향이며전체204기술안/구현/실제판매·삭제실행승인아님.
- 검증: git diff --check·문서상대링크확인.문서변경으로Gradle미실행.위험/다음: 실제이관시기존대상없음과기준점근거기록,신규DB/adapter세부안동결·구현승인과203실제연동/출시승인은후속.

## 2026-10-12 — 첨부 Identity TMI-203 구현 계획 검토

- 브랜치: feat/TMI-201-billing-product-purchase-account / TMI-203·204.목표: 사용자첨부계획의Billing연동정합성확인.
- 근거: 첨부203계획전문,공동계약§5.3~5.7,TMI-199 exact§5~6/Identity13개manifest,204계획과비교.최초출시legacy COMPLETE면제는snapshot필터/빈응답면제가아님을정확히반영했고7개route·추가6index도기준과일치.중대한계약충돌발견없음.
- 보완권장(새제품정책아님):요약의복구마다새세대표현을INITIAL/BILLING_RESTORE/RESYNC로한정;baseline첫ACK성공후응답유실·동일expectedCheckpointVersion=null재전송을currentgeneration/소유/유효manifest검증뒤204로수렴시키는회귀명시;source정상정리로기존적용coverage를무효화하지않는경계테스트와실제미적용구간GAP구분.
- 변경파일: CURRENT_STATE·WORKLOG만.첨부/계획본문/Identity코드/공동계약/Jira/외부설정변경없음.검증: 문서대조·git diff --check,Gradle/실제Mongo/IAM미실행.첨부의202코드확인주장은이번독립검증결과로승격하지않음.
- 위험/다음: 고정T실현성·인증principal신뢰경계·실제history/복원검증은구현gate유지.큰설계재분할없이보완문구/회귀확인후0단계실험부터진행가능하며구현·배포승인은별도.

## 2026-10-12 — TMI-203·204 단순화 방향 확인

- 브랜치: develop / TMI-203·204. 목표: 사용자의 과도한 검증 복잡도 축소 요청에 유지할 안전성과 축소 대상 분리.
- 결정/제안: 단순화 방향에 사용자 동의. 과거 전체 완전성 증명·일시적 복구 지연을 정상 서비스와 과도하게 결합하지 않는 방향으로 재설계. durable capture/재시도/멱등 적용/commit 뒤 진행 저장/탈퇴 상태 비복원/최소 인증·동시성 검증은 유지. exact snapshot·ACK·generation 제거까지 승인받은 것으로 확대하지 않는다.
- 주의: 기존 토큰의 유효기간·진행 요청 경합 보호가 필요하며 Identity 발급 차단만으로 대체하지 않는다. 누락 대상을 특정하지 못하면 개별 계정만 삭제 보류할 수 있다고 보장하지 않는다. 확인 불가를 완료/미구매로 간주하거나 보류 자료를 무기한 보존하는 정책도 도입하지 않는다.
- 변경 파일: CURRENT_STATE·WORKLOG만, 기존 변경 보존. 공동 계약/계획 본문/Identity/코드/Jira/외부 설정/배포 변경 없음. 검증: 현재204계획 gate/control 확인 및 git diff --check; 문서 기록 작업으로 Gradle 미실행.
- 다음: 203·204의 축소안을 비교해 shared wire/완료조건/삭제 안전조건 영향을 먼저 합의한 후 관련 계획·계약 동기화. 이번 방향 동의는 판매·삭제 활성화 승인이 아님.

## 2026-10-12 — Identity203 단순화 인계서 작성

- 브랜치: develop / TMI-203·204. 목표: 사용자 요청대로 Identity 전달용 단순화 요청 정리.
- 변경: docs/contracts/TMI-203-204-SIMPLIFICATION-HANDOFF.md 신규, CURRENT_STATE 갱신, WORKLOG append. 5줄 결론/필독/결정·회신/위험 구성, 기존 변경 보존.
- 내용: durable capture·재시도·멱등·commit 후 checkpoint·탈퇴 단조성·기존 token/복원 보호 유지. 과거완전성 출시 gate·일시지연 일괄차단·불필요한 수동검토 축소 목표. T/digest/H2/ACK/generation은 삭제 확정 아닌 안전한 대체 비교 요청.205 재검토기준과203 오류운영 분리, 범위 불명시 개별 격리 불가 명시.
- 검증: git diff --check 및 인계서 상대링크 존재 확인. 문서 작업으로 Gradle/실제 Mongo/IAM/상대코드 검증 미실행. 공동 exact 계약·코드·Identity·Jira·외부 메시지·배포 변경 없음.
- 위험/다음: 상대 권장 최소안·wire/index/보존 영향·경합/복원 테스트 회신 후 공동 계약과204 계획 개정. 단순화 방향 승인을 기술 장치 제거·판매/삭제 승인으로 확대하지 않음.

## 2026-10-12 — Identity Billing 단일 진행 authority 회신 검토

- 브랜치: develop / TMI-203·204. 목표: 사용자 첨부 공동 단순화 회신 전체 검토.
- 결과: 원격 consumer 상태/ACK를 제거하고 local checkpoint/epoch/CAS·restore 격리는 유지하는 구조를 권장. 원천120일이 ACK 기반 가변 보존이 아니므로 중복 진행 관리를 줄일 논리적 근거 있음. T/H/digest·H2 유지,202 push 응답 유지,cleanup port와205 책임 이동을 명시한 점 타당.
- exact 보완: source availability의 관측 일관성·floor/interval 범위·16KiB 초과 및410에서도 필요한 최소 복구정보 전달 경로 동결; snapshot baseline H와 실제 연속 원event 적용 checkpoint를 분리해 C 이전 원천만료 판단을 오용하지 않음; revision/old client 거절·배포 호환을 조사 후 동결. 새 제품 정책 재선택보다 구현 규격 마무리 항목임.
- 변경: CURRENT_STATE·WORKLOG만. 공동 계약/계획/코드/Identity/Jira/외부 발송/배포 변경 없음. 사용자 수락·구현 승인으로 간주하지 않음. 검증: 첨부 전문과 기존 공동 계약/인계 방향 대조, git diff --check. Gradle·실제Mongo/IAM/상대코드 재검증 미실행.
- 위험/다음: local progress 유실 및 source 만료는 여전히 자동 정상화 불가. 권장안 채택 후 공동 exact/fixture/index·204와205/운영gate 영향 동기화 필요.

## 2026-10-12 — TMI-204 Billing 단일 authority 계획 개정

- 브랜치: develop / TMI-203·204. 목표: 사용자 요청으로 Identity 단순화 회신을 Billing 구현 계획에 반영.
- 변경 파일: docs/plans/TMI-204-withdrawal-recovery.md 본문 개정, PLAN-009, CONTRACT_DECISIONS, ADR-004, 공동 기술 계약, TMI-199 exact, 탈퇴·purge 설계의 개정 안내 및 CURRENT_STATE/WORKLOG. 기존 사용자 변경 보존, 예상 밖 범위 변경 없음.
- 내용: 원격 ACK/Identity consumer generation·ACK worker/flag·별도 scans 제거; local checkpoint/epoch/coverage·CAS/복원 격리, T/H/digest/H2 유지. 기본4+최소 operation 기록1의5개 collection 후보, state baseline/feed 적용 범위 분리, availability410/상한/일관성 동결 요구, 자동retry/circuit·판매/삭제 영향 구분·Phase0+4PR·회귀 기준 정리.
- 계약: 기존4필드 event/push204·public DTO·상품/무료/재가입/보존 불변. 공동exact 기존 본문/fixture는 구버전으로 보존하고 상단에서 변경 범위와 구현 보류를 명시. 계획 채택과 신규wire/index 공동동결을 구분. Identity 저장소·Jira·코드·의존성·실제배포/판매/purge 변경 없음.
- 검증: git diff --check,204/009의 로컬 파일링크16개 존재 확인,구ACK/generation 요구 잔존 검색(제거 설명/구버전 문맥만). 문서 변경이므로 Gradle/실제Mongo/IAM/상대서버 테스트 미실행.
- 위험/다음: Identity에 개정 계획 전달 후 새exact revision·availability/오류·manifest/index·구배포호환·202 cleanup/205 증거소비 합의. 운영 의존성과 구현 착수/실제활성화는 별도 승인. 실제 운영 상태나새계약배포완료를 주장하지 않음.

## 2026-10-12 — PLAN009·ADR·공동 계약 문서 개정 인계서

- 브랜치: develop / TMI-203·204, 영향199·202·205·206. 목표: 사용자 요청대로 새 방향을 각 문서 본문에 반영하는 구체 인계 작성.
- 변경: docs/contracts/TMI-203-204-DOCUMENT-REVISION-HANDOFF.md 신규, CURRENT_STATE 갱신·WORKLOG append. 현재 안내문/본문 변경 상태, 문서·절별 기존→대체 지침, 유지 event/token/capture/보존·local fence,새exact/fixture/index·배포호환·회신/완료기준 포함.
- 주의: remote recovery generation과202 resumeGeneration·source stream 구분,복구ACK와push204 구분,baseline과원event증거 구분. snapshot 생성 stream 귀속·마지막scanCursor의새의미·410 availability는미결로표시. 기존본문/fixture일괄치환·운영index삭제·Identity/Jira수정없음.
- 검증: 신규 인계서 로컬링크 존재 확인·git diff --check. 문서 작업으로 Gradle·실제Mongo/IAM/상대코드/배포 검증 미실행. 기존 변경 보존, 예상 밖 변경 없음.
- 다음: Identity exact 제안과 호환 회신 수락 후 Billing에서 공동본문·활성명세·계획/205/206 동기화. 코드착수/배포/판매/삭제 별도 승인.

## 2026-10-12 — PLAN010~013 서비스별 수정 범위 안내

- 날짜·브랜치: 2026-10-12, feat/TMI-201-billing-product-purchase-account. 목표:009 외 계획도 Billing만 수정하는지 설명. 별도 Jira 없음.
- 근거/결과:010~013 계획 대조.010은 Billing 구매 검증·기간권·sync/webhook 및 앱RC SDK completion 연동,011은Billing paid Reservation/이용 증거 및LC source reader·채점 승인 연동,012는Billing 환불/권리 종료와LC durable revoke consumer/실사용 차단,013은Identity·LC·앱·Store/RC·AWS 공동E2E/배포운영. 상대 수정·배포는 별도 계약 승인/담당 작업이며 Billing 저장소에서 묶어 수정하지 않음.
- 변경 파일:CURRENT_STATE·WORKLOG만. 코드·정책/exact계약·타 서버·Jira·외부설정·commit/push·배포 변경 없음, 기존 동시 변경 보존.
- 검증:계획 서비스 인계/완료 gate 확인·git diff --check, 설명만으로Gradle 미실행. 위험/다음:Billing 단독 개발 완료를 앱 결제/LC차단/staging 완료로 간주하지 않음. 각 계획 착수 시 상대 wire/작업 분담 확정.

## 2026-10-12 — Identity 단순화 인계·exact 미결안 공동 검토

- 브랜치: develop / TMI-203·204. 목표: 첨부2종의 기술수락 요청 검토 및 Billing 회신 정리.
- 변경: docs/contracts/TMI-203-204-EXACT-AMENDMENT-REVIEW.md 신규,CURRENT_STATE·WORKLOG. 기존 동시 작업 기록 보존,예상 밖 변경 없음.
- 결과: local authority/atomic checkpoint·restore,명시stream body·key unique/binding,페이지availability·최초410구간1개·baseline/feed 분리 방향 타당.503 details는생략해variant축소 권장;순번검증/논리expiry/pagehole/7일run·120일keybinding 동결요구. versioned v2 prefix는권장후보이며확정아님.
- 유지: 계획/공동exact본문/fixture/index/코드/타서버/Jira·외부설정변경없음. 기술검토를새공동exact동결·구현/판매/삭제승인으로해석하지않음.
- 검증: 첨부전문·204/기존exact대조,새문서로컬링크·git diff --check. 문서작업으로Gradle/실제Mongo/IAM/배포검증미실행. 상대203코드부재보고는실배포부재증거아님.
- 다음: Identity exact보완수락·route/index/DTO/error명세후공동본문과fixture동기화. 실제runtime/복원/watchdog는206에서검증.

## 2026-10-12 — recovery-v2-candidate-1 검토

- 브랜치: develop / TMI-203·204.목표: Identity새exact후보의앞선보완반영과기술채택가능성확인.
- 변경: TMI-203-204-EXACT-AMENDMENT-REVIEW.md§6추가,CURRENT_STATE/WORKLOG.첨부전문·기존13index대조결과후보10index의유지/변경/제외일치.주요보완반영으로큰재설계없이후보채택권장.
- 잔여: sourceinterval저장/overflow원자성,429/message/FAILEDpage상태표,500RESPONSE_LIMIT_EXCEEDED의일괄5xxretry예외를새exact작성시완결. 실제route/index/배포/IAM/복원gate와구분.
- 유지: 계약본문/계획/fixture/코드/Identity/Jira/배포변경없음.권장수락을공동동결·구현/판매/삭제승인으로승격하지않음.기존변경보존,예상밖변경없음.
- 검증: 첨부/manifest정적대조·git diff --check.문서검토이므로Gradle/Mongo/IAM미실행.다음: 후보채택뒤새exact/schema/manifest/fixture작성·검증과활성문서동기화,별도구현요청.

## 2026-10-12 — Identity 최종 방향 확인·Billing204 운영값 제안

- 브랜치: develop / TMI-203·204.목표: 첨부의v1/range방향확인과§3.3운영값회신.
- 변경: TMI-204-OPERATIONS-PROPOSAL.md신규,기존검토서상단최신방향링크,CURRENT_STATE·WORKLOG.코드/계획운영값/공동exact/fixture/Identity/Jira/배포변경없음,기존변경보존.
- 내용: 미구현전제의v1최초구현·별도range/prefix병합방향을직전v2/10index후보와구분.204정상5초/빈30초·1~60초backoff20회뒤5분probe·bounded snapshot재생성·Retry-After·경보억제·watchdog·분리된epoch설치권한제안.기존202/205규칙불변,수치최종수락/담당지정별도.
- 위험:capturedAt없는wire에서withdrawnAt/sequence로만료시각추정금지.권한/원천range저장/실제부하·DB복원미검증.원천/운영미구현전제는사용자전달이며실배포독립확인아님.
- 검증:첨부전문·기존후보대조,git diff --check·신규링크확인.문서작업으로Gradle/Mongo/IAM미실행.다음:양측운영값/range schema·exact동결후새fixture와활성문서정합화,별도구현요청.

## 2026-10-12 — 사용자 승인204 계획·Jira 갱신

- 브랜치: develop / TMI-204(상위137).목표: 사용자 요청대로 최신 방향/운영값/완료기준을계획과Jira에동기화.
- 변경파일: TMI-204-withdrawal-recovery.md,TMI-204-OPERATIONS-PROPOSAL.md,CURRENT_STATE,WORKLOG. 기존변경보존,예상밖파일변경없음.
- 내용: v1최초구현/명시streambody·keybinding/source floor+별도range,local epoch/5개collection후보/ACK제거. poll5초/빈30초,backoff1~60초20회후5분probe,lease60/20,catchup10page/30초,추가snapshot2run/24h,Retry-After/명시nonretryable/경보/운영권한과회귀기준반영. 기존202/205·보존/무료정책유지.
- Jira: 기존TMI-204를읽고제목·description만수정,재조회로v1/5분probe/빈30초/ACK제거와상태해야할일·parent137·미할당유지확인.다른이슈/댓글/상태/담당변경없음. URL https://to-teacher.atlassian.net/browse/TMI-204 .
- 검증: git diff --check·계획/운영문서로컬링크확인·구1시간retry/versioned미결표현잔존확인.문서/Jira작업으로Gradle/Mongo/IAM미실행.코드/의존성/타서버/배포/commit/push없음.
- 위험/다음: 신규exact/range schema·index/429/message·manifest/fixture 공동동결,실제담당/채널/권한/부하·복원검증 및별도구현착수/활성화승인필요.채택된운영수치는아직실환경에적용하지않음.

## 2026-10-12 — Identity203 내부 코어 보고·저장 규격 검토

- 브랜치: develop / TMI-203·204.목표: 첨부구현보고와공동확정요청검토.
- 변경: TMI-203-204-EXACT-AMENDMENT-REVIEW.md§7,CURRENT_STATE,WORKLOG만. source range/index/version·100batch·FAILED409/429제안이204방향과일치함을기록.100제한의조회누락방지·선택밖동일from unique충돌의재선택수렴을DBadapter검증으로요청.
- 확인범위: 첨부전문과현204계획/운영값대조.1492개/1486통과6skip·신규17개는상대보고이며소스독립검토/실행아님. API/DB/cursor/IAM미완성으로실연동가능상태를주장하지않음.
- 검증: git diff --check,문서검토로Gradle/Mongo/IAM미실행.기존변경보존,예상밖변경없음.계약본문/계획/fixture/코드/타서버/Jira/배포미변경.
- 다음: 새exact/schema/message/index최종명·fixture공동확인,실제동일T/Mongo/CAS검증과후속controller/adapter구현.제품정책재선택이나API/판매/삭제승인으로확대하지않음.

## 2026-10-12 — Identity 현재 구현·fixture 직접 확인

- 브랜치: Billing develop / 대상Identity feat/TMI-203-withdrawal-snapshot-feed-recovery / TMI-203·204.목표: 사용자질문에현재작성상태읽기확인.
- 확인: Identitygit상태·src/main전역참조검색,RecoveryProtocol/SnapshotAccessRules·RecoverySchemaTests·recovery-r1파일목록/schema,최근WORKLOG와XML열람.새JSON6종·strictcodec/고정오류·상태규칙/추가range코어존재,미커밋작업.코드상HTTP/DB복구adapter연결없고codec주석에도미등록명시.
- 검증: 기존로컬XML6종80tests(6+3+8+7+33+23)/실패0/오류0/skip0직접확인.테스트재실행이나미커밋현재전체변경에대한완전검증은아님. schema는공동수락pending명시,manifest테스트기준16개존재.공동동결/실배포/API준비로확대하지않음.
- 변경: Billing CURRENT_STATE/WORKLOG만,Identity파일/Gradle/DB/외부/Jira미변경. git diff --check.기존동시변경보존,예상밖변경없음.
- 다음: Billing에서이미작성된recovery-r1명세/fixture독립검증·공동확인후204개발병행가능.203실제HTTP/Mongo/cursor/builder/IAM연동은후속이며별도승인없이구현착수하지않음.

## 2026-10-12 — TMI-204 Phase0 recovery-r1 Billing 독립 검증

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 목표: 사용자 승인에 따라 Identity 새 exact/fixture를 Billing에서 독립 검증.
- 변경: build.gradle(test-only networknt1.5.6), WithdrawalRecoveryFixtureTest, recovery-r1 JSON8종, 독립 검증 결과 문서, 204 계획 진행 메모, CURRENT_STATE/WORKLOG. 기존 dirty 문서와 v1 fixture 보존. Identity Java 구현 복사·Identity 수정 없음.
- 동작: 요청22/응답20/상태7과 숫자 경계·오류 retry/details·index 정적 확인·feed 관계·범위 조회/승격·공유 SHA-256 검증, 총58개 추가. 작업 중 source에 추가된 range/hash fixture도 포함. 운영 decoder/worker/DB initializer 구현 아님.
- 발견/결정: D0/D1/U32 JSON Schema pattern의 끝 개행 허용을 실제 재현. Identity decimal String.matches는 정적 확인상 거절하며 Billing 독립 oracle도 거절. 공유 원본을 고치지 않고 known-gap 재현 테스트와 Identity 전달용 보완 요청을 기록. 이를 schema 완전 적합/공동 동결로 포장하지 않음.
- 테스트: 최초 targeted53개 중 숫자 개행1개 실패로 차이 발견; 후속 범위 테스트 타입 추론 compile 오류 수정. 최종 ./gradlew clean test 성공, XML합계451개/실패0/오류0/skip0(신규58개). Gradle 캐시/로컬 disposable Testcontainers 접근 승인 사용. git diff --check 통과. 기존 Mongo 회귀는 실행했으나 신규 Identity 복구 Mongo adapter/실AWS/Store/Identity API/배포는 미검증.
- 유지 계약: 기존 UserWithdrawn4필드·push204·무료/유료 정책·보존기간·기존199 fixture 불변. runtime dependency/운영코드/Jira/commit/push/판매·삭제 활성화 변경 없음.
- 위험/다음: schema 끝 개행 등 scalar 음성 fixture 공동 보완·hash 재확인/수락 후 exact 동결. 실제 T/H snapshot·range delete/evidence 원자성·unique 재선택·unknown commit·cursor/IAM과 PR1~4 구현은 별도. 이번 통과는 상대 API 구현·병합·실배포 완료가 아님.

## 2026-10-12 — TMI-203·204 scalar-end-1 보완본 독립 재검증·수락

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 목표: Identity 인계의8종문자열종료보완을 Billing에서 재검증. 구현전 TMI-204 Jira 본문/완료기준 읽기확인, Jira 쓰기없음.
- 변경: recovery-r1 wire.schema/hash 동기화·scalar-cases/README추가, WithdrawalRecoveryFixtureTest의known-gap거절변경/272개입력+coverage1개추가, 독립검증문서·204계획·CURRENT_STATE·WORKLOG. Identity구현복사없음, 기존dirty보존, 예상밖변경없음.
- 검증: 이전schema대조상8개pattern끝조건만변경. Billing networknt1.5.6에서정상8허용·개행/제어문자264거절, schema/strictdecimal동시거절. 최신8개입력SHA256일치·전달schema/scalarhash일치. ./gradlew clean test724개통과/실패0/오류0/skip0(복구fixture331), git diff --check통과. Gradle캐시/Testcontainers접근승인사용. 이전451개결과재사용아님.
- 결정: recovery-r1/scalar-end-1공유wire/schema/fixture보완본 Billing 기술수락, Identity인계와양측확인근거기록·끝개행미결닫음. Phase0전체/local schema/index/API·배포/실Mongo/IAM/복원완료승인으로확대하지않음.
- 유지: 기존199fixture·요청/응답shape·숫자범위·runtime·의존성·무료/유료/보존정책불변. Identity/외부설정/Jira/commit/push/배포미변경.
- 위험/다음: semantic검증은계속필수. 실제source transaction/cursor/HTTP/IAM/복원검증은별도. 다음204 PR1준비와local schema/index·공동본문정합화; 신규기능활성화없음.

## 2026-10-12 — TMI-204 PR1 초안 착수 후 사용자 요청으로 구현 중단

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 최초목표: PR1기반구현. TMI-204 Jira읽기와현재계획/기존서비스 확인후착수했으나 사용자가 미동결사항부터해결하도록변경요청, 즉시추가구현중단.
- 변경파일: PaymentWithdrawalService(공통applier위임), 신규PaymentWithdrawalApplier/WithdrawalFact, recovery/RecoveryContractException·RecoveryPayloadDecoder·IdentityWithdrawalRecoveryClient 초안, CURRENT_STATE/WORKLOG. 기존dirty보존, 예상밖변경없음. 초안미커밋유지하며무단삭제/되돌림없음.
- 초안동작: 기존탈퇴처리를caller ClientSession에추출, snapshot fact에는eventId/inbox생성없음. strict response shape/숫자/UTF8/시간/순번검사와fakeable client interface초안. 실제worker/HTTPclient/신규DBschema/fence/활성화없음. PR1전체·신규decoder검증완료를주장하지않음.
- 유지: public/internal wire변경없음, 기존push서비스Transaction경계유지의도. 아직전용회귀추가전이므로보장완료아님. Identity/Jira쓰기/의존성/배포/commit/push없음.
- 미결정리: local5collection exact필드/null/state/index, barrier의account/push/recovery쓰기허용표, bounded unresolved GAP와초과UNKNOWN/205삭제보류소비,구공동본문정합화. AWS signer의module/version/credential/실권한은실HTTP단계gate로분리. 문자열schema보완은재논의하지않음.
- 다음: 정책선택과내부기술명세를구분해권장안승인/동결후구현재개. 코드추가수정은멈추고현재상태clean test만검사; 결과는다음추가기록참조.
- 중단시점검사: ./gradlew clean test 성공(XML724개/실패0/오류0/skip0), git diff --check통과. 신규decoder/applier전용테스트추가전이므로기존회귀통과와새기능완료를구분한다.

## 2026-10-12 — TMI-204 1·2 방향 승인과 GAP 보관 설명

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 목표: 사용자1·2승인기록과3번상세설명.
- 변경: 204계획§3,CURRENT_STATE,WORKLOG만. 5collection/exact명세·단순lag정상유지/복원·control소실관련쓰기차단·승인복구방향승인. 정확필드/전이표는작성필요,3번상한/UNKNOWN/해제조건미승인.
- 설명: 필요한원천event소실과일시통신실패구분,101~110같은구간1개와event10개구분,인접구간만합침. overflow UNKNOWN은상세범위완전성불명이며미래follow-up성공만으로과거완료처리금지. 관련205자동삭제보류는전체구매자동차단/무기한개인정보보존권한아님.
- 검증: 계획현재본문대조·git diff --check. 문서/설명만으로Gradle재실행없음. 기존미완성코드보존·구현재개/Jira/외부/배포/commit/push없음,예상밖변경없음.
- 다음: 3번단순한보관/초과/해제안설명후동결,1·2exact명세와함께구현재개범위확정.

## 2026-10-12 — TMI-204 GAP 관리가 필요한 이유와 과설계 경계 설명

- 브랜치: feat/TMI-204-billing-withdrawal-recovery / TMI-204. 사용자질문: 그렇게까지관리할필요성.
- 변경: CURRENT_STATE/WORKLOG만. 탈퇴누락을정상으로오인하거나삭제안전성을추측하지않는목적과,상세과거구간관리라는선택적구현방식을분리해설명.
- 제안: 현재탈퇴상태snapshot복구와과거event완전성을분리하고,과거불명최소표시·현재stream정상화와205개별거래/삭제증거확인을중심으로더단순화가능성검토. snapshot만으로삭제허가/과거완료를선언하지않음. 기존계약변경승인아님.
- 테스트: 설명/기록만,git diff --check; Gradle재실행없음. 코드/Jira/타서버/배포/commit/push없음,미완성변경보존.
- 다음: 사용자의복잡성허용범위를확인하고205의과거완전성의존을검토한뒤단순화정확규격제안. 구현중단유지.

## 2026-10-12 — TMI-204 snapshot 중심 단순화 승인 반영

- 브랜치: feat/TMI-204-billing-withdrawal-recovery / TMI-204·205. 사용자5단계흐름승인반영.
- 변경: 204계획§2.0/2.4/5.4/6, PLAN-009탈퇴·purge설계최신안내, CURRENT_STATE/WORKLOG. 코드중단유지·기존초안/dirty보존,예상밖변경없음.
- 결정: 평상시push+연속feed;필요원본소실이면현재상태snapshot검증후H기준재개;과거event불명최소표시.상세GAP배열/상한초과설계대체.현재정상화≠과거event완전성,목록부재로탈퇴상태복원금지.단순통신오류는기존retry,반복snapshot은기존budget유지.
- 205: 전체과거불명만으로전계정영구보류하지않되계정별탈퇴/거래/환불/보존/동시성증거부족은보류. snapshot성공을미구매·자동삭제승인으로사용금지.
- 유지: Identitywire/source floor·range/보존/제품정책·기본OFF. Identity/Jira/배포/commit/push변경없음.세부worker전이·local스키마·구공동본문정합화는후속.
- 검증: 현재계획·205조건대조,git diff --check.문서만으로Gradle재실행없음.다음:승인방향의최소저장/전이명세반영후구현재개범위확정.

## 2026-10-12 — Identity에 알릴 snapshot 재동기화 범위 설명

- 브랜치: feat/TMI-204-billing-withdrawal-recovery / TMI-203·204. 사용자질문에전달용요약제공.
- 변경: CURRENT_STATE/WORKLOG만. Billing복구전이/과거불명최소표시/205별도판정을공유하되API추가·source range삭제·보존확대요청과구분.
- 확인요청: 동일T의원천목록과H,READY완전성/만료·실패거절,원천사용자삭제시snapshot포함범위·legacyCoverage/knownGaps의의미유지. snapshot에없음은탈퇴하지않았음이나삭제안전성증명아님.
- 검증: 승인계획/직전Identityexact대조,git diff --check.설명만으로Gradle미실행. 코드재개/메시지전송/Jira/타서버/배포/commit/push없음,예상밖변경없음.
- 다음: 사용자전달후필요한범위회신확인, Billing최소저장/전이규격정리.새source보존의무나상대구현완료로확대하지않음.

## 2026-10-12 — TMI-204 계획·Jira 반영 및 Identity snapshot 범위 회신 수신

- 브랜치: feat/TMI-204-billing-withdrawal-recovery / TMI-204. 앞선사용자의계획/Jira수정요청을계속수행하며새Identity회신을반영.
- 변경: docs/plans/TMI-204-withdrawal-recovery.md, CURRENT_STATE/WORKLOG, Jira TMI-204 description만. 최신승인/중단상태·잔존WITHDRAWN snapshot범위·회귀조건·Identity인계/후속작업정합화. 기존dirty/미완성코드보존,예상밖변경없음.
- 회신구분: T시점DB잔존모든WITHDRAWN대상,원event소실/tombstone잔존은복구·tombstone삭제/복원소실은불가. Identity자동물리삭제경로미발견은상대코드조사보고이며독립운영검증/무기한보존보장아님. count/digest는제공snapshot무결성일뿐사라진과거완전성아님.
- Jira: 기존본문읽기후승인된snapshot재동기화·과거불명최소표시·205계정별증거·회신·추가테스트를반영,기존운영숫자/보존조건유지. 재조회로반영및제목불변/상태해야할일/parentTMI-137/미할당확인. URL https://to-teacher.atlassian.net/browse/TMI-204 . 댓글/상태/다른이슈변경없음.
- 검증: git diff --check·Jira재조회. 문서/Jira만으로Gradle재실행없음. runtime/Identity/배포/commit/push없음.
- 다음: local최소저장/재동기화전이규격·구공동본문정합화후중단PR1재개범위확인. 실제API/Mongo/IAM/복원및판매/삭제gate유지.

## 2026-10-12 — TMI-204 사용자 재개 요청·PR1 제어기반 구현

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 목표: 승인된snapshot중심복구의공통적용·strictdecoder·로컬제어기반구현. 착수전TMI-204 Jira읽기확인,내용/상태수정없음.
- 변경파일: PaymentWithdrawalApplier/WithdrawalFact 및 기존PaymentWithdrawalService위임완성, PaymentAccountService fence연결, recovery/{RecoveryPayloadDecoder,RecoveryContractException,IdentityWithdrawalRecoveryClient,PaymentRecoveryFence,PaymentRecoverySchema}, PaymentProperties/application.yml, payment-recovery-control-schema.json. 테스트 PaymentFoundationIntegrationTest/RecoveryPayloadDecoderTest/RecoveryConfigurationTest. 계획·runbook·CURRENT_STATE/WORKLOG갱신. 기존중단초안/dirty변경보존,예상밖변경없음.
- 동작: caller ClientSession내fact/event반영·가짜inbox없음;strict16KiB/UTF8/BOM/중복/trailing/coercion/UUID/시각/numeric·manifest/page/feed관계와exact오류15종. 전용client는interface만.
- 제어: 필수flagOFF기존201유지,ON일때control부재failclosed.요청전ticket고정·transaction내epoch/version/barrier CAS,중복/NOOP도쓰기. OPEN/PURCHASE_BLOCKED/ALL_BLOCKED허용표문서화. startup은control schema/index/설치존재검증만,DDLhelper는명시단계용·자동epoch없음.
- 저장범위: 승인5collection중control header만exact구현(schemaVersion/version int64·epochv4·환경/consumerunique·TTL없음). 다른4collection/control job/C/baseline등은후속migration이며전체PR1/204완료로확대하지않음.
- 테스트: targeted Mongo/decoder통과. 최종 ./gradlew clean test749개성공/실패0/오류0/skip0(기존724+신규25), XML직접집계. 복구응답14/config1/Mongo신규10 포함. staleepoch·transactionretry재캡처금지·barrier·snapshotfact와진행실패rollback·pushNOOP·versionoverflow·schema drift·동시발급/탈퇴를로컬replica-set검증. git diff --check통과.
- 유지: public/internal wire/기존4필드event·canonicaldigest·무료/유료/15일/120일보존계약불변, runtime dependency추가없음. 기존test-onlynetworknt변경은이전작업. 실제Identity/AWS/Store/운영DB호출·타서버/Jira/commit/push/배포없음.
- 위험/다음: flagfalse유지. snapshot/rows/receipt/checkpoint/lease영속처리·자동재동기화·retry/retention/운영installer·SigV4는미구현. decoder단독검증은snapshot전체digest/expiry/HTTP인증증거아님. 공통control쓰기부하·oldworker물리격리·실Mongo/IAM/복원은206 gate. 다음PR2 snapshot영속단계와PR3feed·PR4운영으로이어감.

## 2026-10-12 — TMI-204 PR2 snapshot 영속 처리 코어

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 목표: 사용자 개발 요청에 따라 이전 커밋 d232d2e 위에서 snapshot 저장·처리 단계 구현. 시작 worktree clean, 착수 전 Jira TMI-204 읽기만 수행.
- 변경파일: recovery/WithdrawalSnapshotProcessor.java, PaymentSnapshotSchema.java, PaymentRecoveryFence.java; PaymentProperties.java/application.yml; payment-recovery-control-schema.json/payment-recovery-snapshot-schema.json; WithdrawalSnapshotIntegrationTest/RecoveryConfigurationTest; 204계획·foundation runbook·신규 snapshot-processing runbook·CURRENT_STATE/WORKLOG. 예상 밖 변경 없음.
- 동작: operation 선저장/동일 key 재시도, REQUESTING→POLLING/APPLYING→VERIFYING→COMPLETE와 FAILED/EXPIRED. 최대20행 fact/row/receipt/progress 동일transaction, 가짜inbox 없음. HTTP 밖 dispatch 후 동일 epoch/60초 lease 검증, 늦은 응답·만료 차단. 전체 staging keyset count/digest 검증 후에만 T/H baseline·checkpoint 설치, 기존 C 역행/stream 임의 교체 거절. historicalUncertainty boolean만 저장하며 과거 전체완전성이나 삭제안전성을 선언하지 않음.
- 스키마: 기존control reader version1/2 허용·snapshot writer version2 요구, 4collection/9index 추가. startup은 exact검증뿐이며 운영DDL/validator 자동수정·epoch자동생성 없음. 명시 migration/drain 필요, PR1 binary 무조건 rollback 불가. snapshot flag 기본OFF, processor는 비등록 step코어이며 scheduler 없음.
- 보존: source expiry와local시작+7일 중빠른 시점 적용. 만료행/receipt 각20개와빈run 정리메서드, snapshot OFF에서도fence필수로호출가능. 실제 주기실행/overdue경보·120일 operation정리는 후속, 운영활성화 금지.
- 테스트: 초기 compile accessor와 operation 필수 streamId 누락을 수정한 뒤 targeted13개 성공. source조기만료·checkpoint역행·flag/fence조합 추가 후 ./gradlew clean test **765개 성공/실패0/오류0/skip0**, XML합계 확인(이전749+신규16). Mongo7 replica-set에서응답유실/재시작/빈snapshot/digest실패/페이지중복/원자rollback/lease탈취/epoch교체/expiry/batch정리/transient·unknowncommit/schema drift검증. git diff --check통과.
- 유지: recovery-r1/scalar-end-1 sharedwire/fixture, 기존public/internal계약과무료권·결제/탈퇴정책불변. dependency추가없음. 실제Identity/AWS/Store/운영DB 호출,타서버수정,Jira쓰기,commit/push/배포 없음.
- 위험/다음: PR2전체나TMI-204완료아님. BUILDING5분·poll/backoff·실패후추가run예산·전체hashpass의실행시간조율·retention scheduler를연결하고 feed/실HTTP·SigV4·운영installer로이어갈것. count/digest는잔존tombstone snapshot무결성만증명. 실부하/복원/IAM/보존기한운영검증 전 flag와판매/삭제gate유지.

## 2026-10-12 — TMI-204 feed·영속 재시도와 기본 OFF worker

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 사용자 이어서 개발 요청에 따른 후속 단계. 착수 전 Jira TMI-204 본문/완료 조건 읽기, 시작 worktree clean. Jira 쓰기/상태 변경 없음.
- 변경파일: recovery/WithdrawalFeedProcessor·WithdrawalFeedWorker·RecoveryRetryPolicy·RecoveryRemoteException 신규, PaymentProperties/PaymentRecoveryFence/PaymentSnapshotSchema/WithdrawalSnapshotProcessor와 application.yml/control·snapshot schema 수정. 테스트 WithdrawalFeedIntegrationTest/WithdrawalFeedWorkerTest/RecoveryRetryPolicyTest 신규·RecoveryConfigurationTest 확장. 204계획·snapshot/feed runbook·CURRENT_STATE/WORKLOG 갱신. 예상 밖 변경 없음.
- 동작: baseline H 이후 고정H2/20event·availability/순번·event canonical digest 대조, inbox/applier/receipt/C/job 동일transaction. page 성공commit만 failure0, 새scan은 C에서 시작. snapshot 시작시 feedJob 제거로 in-flight 무효화, snapshot 완료전 feed 중지. 목록부재를 ACTIVE로 되돌리거나 과거전체완료로 해석하지 않음.
- 재시도: dispatch 선저장·60초 lease·epoch검증, crash도1회소비. 실패20회까지증가/포화·누적attempts보존·5분±20%probe. poll5/빈30초±20%, 한tick10page/30초후1초양보. Retry-After 정수초/HTTP-date parser·유효긴대기보존·invalid/overflow분류. cursor1시간/410은C부터새scan하되circuit우회금지. 인증/계약/nonretryable500중단,원본소실SNAPSHOT_REQUIRED와503재시도구분·단순lag구매barrier불변.
- 스키마/실행: localcontrol version3 feedJob 추가, readers1/2/3·snapshot2/3·feed3, pages.kind FEED/resource revision3·기존index옵션불변. startup exact검증·명시이관필요, runtimeDDL없음. feed-enabled 기본false. worker는 ApplicationReady 이후만 실행하고 실제client Bean이없으면ON startup실패. 제한된tick/page metric만추가, 개인정보/예외원문로그없음.
- 검증: targeted feed/기존snapshot/설정/retry테스트통과. 최종 ./gradlew clean test **788개 통과/실패0/오류0/skip0**, XML집계(765+신규23). Mongo replica-set18개신규로멀티page/push중복/내용충돌rollback/receipt실패rollback/목표변경/20회재시작/probe/긴대기/503·소실구분/auth/10page/cursor/옛epoch/중간snapshot/crash/unknowncommit/transient/MAX/7일receipt정리및원본소실→명시snapshot→H이후feed검증. worker2/retry2/config1추가. git diff --check통과.
- 유지: 기존public/internal/recovery-r1 wire·공유fixture·무료권·결제/탈퇴정책불변. 새runtime의존성없음. Identity exact는읽기만, 실제외부API·운영DB·IAM·타서버수정·commit/push/배포없음.
- 남은범위: source소실→새snapshot자동생성/coordinator·BUILDING5분/최초+추가2run예산은미완료. SNAPSHOT_REQUIRED에서정지하며수동core연결테스트를자동완료로보고하지않음. 실제SigV4/client/timeout·경보/heartbeat/watchdog·만료cursor/operation120일/독립retention·운영migration/복원/부하/E2E검증은후속. feed receipt는7일/20개batch정리코어만있어운영활성화전에스케줄연결필수. 전체204완료/Jira닫기/판매·삭제활성화아님.

## 2026-10-12 — TMI-204 자동 snapshot 조율·독립 보존·관측

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 사용자 후속개발 요청에 따라 실행. 착수 전 Jira TMI-204 재조회. 이전턴의 미커밋 feed/runtime·문서/테스트 변경을 모두 보존했다. 예상 밖 변경 없음.
- 변경파일: WithdrawalSnapshotProcessor 자동tick/retry·PaymentRecoveryFence/PaymentSnapshotSchema/WithdrawalFeedProcessor의version4호환, PaymentProperties/application.yml·control/snapshot schema. 신규 WithdrawalSnapshotWorker/PaymentRecoveryRetention/PaymentRecoveryRetentionWorker/PaymentRecoveryMonitor. Snapshot/FeedIntegrationTest·Configuration/WorkerTest 확장. 자동조율runbook·feed안내·204계획·CURRENT_STATE/WORKLOG 갱신.
- 동작: 검증된feed GAP에서자동snapshot시작,BUILDING동일ID5초poll/5분overdue,dispatch영속실패count/attempts·20회5분probe·긴Retry-After보존. 확정SNAPSHOT_BUILD_TIMEOUT/SOURCE_EXPIRED만추가2run/60·300초대기,runNumber및환경/consumer최근24시간operation3건상한. budget소진은기간경과로자동해제하지않음. 불명failure/hash/내용충돌/auth/반복GAP중단,localexpiry를원격확정으로추정하지않음. 이전operation epoch귀속검증.
- 보존: 별도기본OFF worker·client불필요. 매batch기존snapshot20행+20receipt,feedreceipt20개,operation120일20개와해당만료job정리. 만료cursor는새runId로무효화하되failure/nextAttemptAt유지. baseline/C/과거불명및account/lifecycle/ref/inbox/결제원장/205후보삭제없음. ALL_BLOCKED시거절유지.
- 관측: 60초DB샘플heartbeat·auth/contract/source/budget/build/circuit·feed5/30분·긴대기/overdue/monitor실패counter. 프로세스내최초/1시간/상승/해소억제,민감label/원문로그없음. 실제알림전송/replica간dedup/외부watchdog설치는미수행.
- 스키마: control4자동metadata·snapshotresource4, 신규 ix_payment_recovery_operation_budget(environment,consumer,createdAt ASC) nonunique/simple/noTTL/noPartial. 총5collection유지. 자동/retention은4필수,기존reader1~4/수동snapshot2~4/feed3~4. 자동startup은기존job retry증거누락거절. runtimeDDL/암묵budget초기화없음,명시migration/drain필요.
- 테스트: targeted snapshot/feed통과후전체 ./gradlew clean test **801개 성공/실패0/오류0/skip0**, XML직접집계(기존788+13). 자동BUILDING/5분·runbudget/24시간후차단·unknown/localexpiry·auth/긴대기·20회재시작/probe·독립7/120일정리·경보중복/해소·자동GAP복구/반복차단·cursor정리budget·startup누락증거/flag검증. git diff --check통과. fakeclient/가짜시계/로컬replica-set만사용.
- 외부확인/유지: Identity withdrawalrecovery Snapshot/FeedService·MongoStore/Reader·Cursor가존재하나main참조검색에서service호출HTTPcontroller/Bean연결은미확인; 실제배포상태로확대하지않음. Billing build.gradle/src검색상AWS SDK/outboundSigV4없음. 새운영의존성임의추가금지에따라추가승인필요,다른인증으로대체하지않음. 기존recovery-r1/sharedfixture/public/internal/무료권·결제정책불변. 타서버/Jira쓰기/운영DB/API/commit/push/배포없음.
- 위험/다음: 실SigV4client·3/5초timeout/16KiB/Retry-After실헤더연결·IdentityAPI준비확인·운영migration/epochinstaller/복원·대용량전체hashpass시간조율·알림채널/watchdog/부하·staging E2E gate가남음. 기능flags OFF,전체204완료/판매/자동계정삭제승인아님. 다음의존성추가승인후실adapter를구현하고별도실환경검증할것.

## 2026-10-12 — TMI-204 Identity recovery SigV4 HTTP client

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 사용자 “어 해줘”로 승인한 AWS SDK v2 인증/서명 모듈 추가 및 HTTP adapter 구현. 착수 전 Jira TMI-204 재조회, Identity protocol/exact 문서와 SDK 기준만 읽기 확인. 이전 feed/자동snapshot/retention 미커밋 변경 보존, 예상 밖 변경 없음.
- 변경파일: build.gradle; 신규 RecoveryHttpConfiguration/RecoveryHttpTransport/SigV4IdentityRecoveryClient 및 두 테스트; PaymentProperties/application.yml; IdentityWithdrawalRecoveryClient/RecoveryPayloadDecoder/WithdrawalSnapshotProcessor/PaymentSnapshotSchema; RecoveryConfigurationTest/WithdrawalSnapshotIntegrationTest. client runbook 신규, 자동조율 runbook·204 PLAN·CURRENT_STATE/WORKLOG 갱신.
- 동작: SDK BOM2.29.52/auth/http-auth-aws·DefaultCredentialsProvider, vpc-lattice-svcs/ap-northeast-2 고정. 환경별 exact HTTPS generated host 검증·빈설정/다른환경/경로/query/port/userinfo 거절. 요청 1회 직렬화→UTF-8 SHA256→최종서명→동일 bytes 전송. JDK HTTP/1.1·connect3초/본문포함HTTP5초·16KiB chunk bounded·redirect NEVER·proxy/cookie/다른인증fallback 없음. 임의 inbound header/baggage 미전파, credential/raw payload/error cause 미보존.
- 오류: 401/403 AUTH, 408/425/429·network/5xx TRANSIENT, 검증된 RESPONSE_LIMIT_EXCEEDED500 nonretryable 우선, source409/410는 strict dispatch binding 후만 처리. 실제 Retry-After seconds/date·중복/invalid/24시간초과신호와 영속 notBefore 연결. BUILDING 성공응답 긴대기 재시작 보존, HTTP수동step도 대기/dispatch count 준수. shared wire에는 retryAt 추가하지 않고 로컬 SnapshotStatus에만 보유.
- 설정/이관: http-enabled 기본false, identity-base-url/allowed-host/environment 빈값. OFF면 credential/HTTP bean 미생성. HTTP ON은 fence+control4/현행validator/index/기존job retry metadata 검증필수이며 runtimeDDL/자동초기화없음. worker는 독립flag이고 실서버·기능활성화없음.
- 검증: 대상테스트 성공 후 ./gradlew clean test **848개 성공/실패0/오류0/skip0**, XML 집계(직전801+47). exact4POST/nullable cursor/Long.MAX_VALUE문자열·독립 HMAC oracle 및 실제 loopback wire 서명검산·credential매요청해석·invalidUTF8/sourcebinding·auth/typed500/긴대기·16KiB경계/chunk초과·본문stall·redirect/응답유실POST1회·OFF/config·Mongo재시작/수동대기/schema gate 확인. 초기 테스트 컴파일 변수/import 오류를 수정한 뒤 최종 전체 성공. git diff --check 통과. fake credential/가짜 loopback서버/로컬replica-set만사용; 실제TLS/IAM검증아님.
- 유지계약: recovery-r1/scalar-end-1 공통fixture·4route/body·operation멱등성·무료/owner/결제정책 유지. 타서버/Jira쓰기/운영API/DB/commit/push/배포없음. SDK 버전은 Identity 기준 정렬이며 최신/보안감사 완료 주장이 아님.
- 위험/다음: 실제Identity HTTP readiness·IAM4route/wrong환경/unsigned/directbypass·TLS/DNS/taskrole회전·credential해석지연(HTTP5초 밖)·SDK보안검토·운영migration/epochinstaller/복원·외부경보/watchdog·outbound trace 계측·대용량hash조율/staging E2E 남음. 전체TMI-204완료/판매/자동삭제승인으로확대하지않는다.

## 2026-10-12 — TMI-203·204 ALB·workload JWT 방향 변경 인계 검토

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 사용자 첨부 인계의 Billing 회신 요청 검토. 방향 승인은 확인하되 exact 공동안·구현/배포/권한변경 미승인 경계를 유지했다. 기존 미커밋 코드와 문서는 보존, 예상 밖 변경 없음.
- 변경파일: docs/contracts/TMI-203-204-ALB-WORKLOAD-JWT-BILLING-REPLY-2026-10-12.md 신규, CURRENT_STATE/WORKLOG 기록. 코드/설정/기존 SigV4 SDK/확정 ADR·AGENTS는 수정하지 않았다.
- 확인: SecurityConfig LATTICE_AWS_IAM permitAll·TEST 전용role·DISABLED, public connector/local-port allowlist, 신규SigV4 exactDNS/defaultOFF를 직접 확인. 운영 ECS/ALB/env/IAM은 조회하지 않았으며 로컬기본값으로 실제미배포/비활성화를 단정하지 않았다. 첨부 AWS/SCP 확인은 Identity 보고로 구분했다.
- 회신안: workload 전용connector/TG/chain과 기존public/LC격리, 누락된 trial eligibility event까지4개Identity수신route 영향검토, Billing4개복구POST유지. 환경별 URNissuer/목적별aud/sub·RS256/kid/TTL60/skew30·새JWT/동일operation재시도·401/403 후보. 초기배포publicJWKset·서비스별privatekey·정상rotation양방향배포/old발급종료후최소120초·긴급폐기우선 권장. Bearer가body서명이아님을명시,정책수락전구현하지않음.
- 검증: git diff --check 및 신규문서상대링크확인. 코드변경없는분석/문서작업이므로 Gradle 재실행안함. 직전848개는SigV4구현결과이지신규JWT검증아님. 기존fixture/hash/멱등성/무료권/복구wire/보존/구매정책유지.
- 위험/다음: 현재AGENTS/ADR의SigV4전용규칙과충돌하여전환구현보류. 운영배포inventory·실제origin/네트워크공개범위·양방향auth exact/키운영·범위한정문서개정/새authfixture수락후수신→발신OFF구현권장. 타서버/메시지/Jira/AWS/Secret/commit/push/배포없음.

## 2026-10-12 — Identity JWT 재검토의 환경·포트·exact 경계 보완

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 사용자 전달 Identity 검토를 확인하고 기존 Billing 회신 §7 및 CURRENT_STATE/WORKLOG만 보완. 미커밋 구현 보존·예상 밖 변경 없음.
- 정적 확인: Identity application.yml의 WITHDRAWAL_ENVIRONMENT 외부주입, RecoveryHttpConfiguration path-only chain, deploy workflow develop=test/main=staging tag를 읽었다. 운영 task/env/네트워크는 조회하지 않았고 main=production으로 추론하지 않았다.
- 제안: test/staging/production trust 분리 및 payment/source환경 대응표. test/staging 동일SANDBOX 같은DB는 현재control scope 충돌가능하므로 별도DB 또는 한쌍 선활성화 권장. 양서버 workload전용connector·public/다른port거절, TLS target은인증서검증/mTLS와다름을명시하고기존HTTP구간위험수락은미결유지. NumericDate/int64/TTL/nbf/중복claim·401/403분리·publicport404·RSA publicJWK검증·rotation120초전제 후보보완.
- 유지: 기존SigV4-only계약·bodyfixture/hash·무료권/구매/보존/LC/user JWT는변경하지않았다. 기술후보보완이지공동동결/구현/배포승인아님. 코드/설정/타서버/AWS/Secret/Jira/메시지/commit/push변경없음.
- 검증: diff --check·문서상대링크검사. 문서분석작업으로Gradle미실행; 기존848개결과를새JWT검증으로재사용하지않음.
- 위험/다음: 실제환경/DB/source stream/origin/port/TLS·공개노출·긴급키폐기담당/소요시간은운영확인필요. 확인뒤auth exact/fixture와범위한정AGENTS/ADR개정을공동수락하고OFF구현진행.

## 2026-10-12 — TMI-204 ALB/workload JWT 계획·Jira 승인 반영

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 사용자 명시 요청으로 TMI-204 계획/Jira 본문 수정. 작업 전 이슈 조회, 기존 미커밋 구현 보존·예상 밖 변경 없음.
- 변경파일: docs/plans/TMI-204-withdrawal-recovery.md §2.5 및 인증/단계/검증 문구, Billing ALB-JWT 회신의 후속승인 상태, CURRENT_STATE/WORKLOG. 별도AGENTS/ADR개정은구현전선행단계로명시했으며이번에변경하지않음.
- 채택: 기존ALB/양쪽전용connector·Identity4이벤트/Billing4복구·환경별trust/DB격리·배포publicJWK/RS256·TTL60/skew30/nbf/오류/rotation·Bearer보장차이를계획기준으로승인기록. 실제환경/주소/port/TLS/키운영·공동fixture최종수락은미확정. 도메인wire/hash/보존/무료권/LC/user계약불변.
- Jira: TMI-204 description만수정하고재조회해최신승인/구현순서/완료기준확인. 기존상세복구정책보존,낡은PR1중단·SigV4유지목표정합화. 제목/해야할일/미할당/상위TMI-137유지. Identity203/다른이슈/댓글/상태전환없음.
- 검증: diff --check 및 변경문서상대링크검사통과. 코드없는문서작업이라Gradle미실행;848개는과거SigV4검증기록으로구분. 코드/SDK제거/Secret/AWS/타서버/commit/push/배포없음.
- 다음: 실제배포inventory와운영매핑확인→auth규격/fixture·범위한정ADR/AGENTS개정→receiver/connector→sender OFF구현→staging/키교체/복원검증. 이번계획승인은실제활성화/판매/자동삭제실행승인아님.

## 2026-10-12 — TMI-204 ALB/workload JWT Billing OFF 구현

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 사용자 “이걸로 해서 이어서 개발” 요청으로 실행. 착수 전 Jira204 읽기확인. 이전미커밋복구코어/자동조율/retention/SigV4·문서변경보존·예상밖범위변경없음. 신규운영의존성추가없음.
- 변경파일: 신규 WorkloadJwtProperties/Codec/IngressFilter/AuthenticationFilter/SecurityConfiguration; SecurityConfig, PaymentProperties/application.yml. 기존SigV4 client의공통코어를 HttpIdentityRecoveryClient로분리하고legacy wrapper보존, JwtIdentityRecoveryClient/RecoveryHttpConfiguration모드선택추가. 신규 WorkloadJwtCodecTest/IngressTest/ConfigurationTest·PaymentWithdrawalJwtControllerTest·JwtIdentityRecoveryClientTest 및 workload-auth-r1 시간fixture/README. AGENTS/ADR002·004/공동계약/CONTRACT_DECISIONS/PLAN009·204·SigV4안내·신규WORKLOAD-JWT-AUTH-R1와기록정합화.
- 발급/검증: RS256·별도배포RSApublic/privateJWK·kid/alg/use/key_ops·private필드/손상거절·sign/verify쌍점검. header/claim duplicate·trailing/UTF8·정수시간·TTL1~60/skew30/nbf관계·jti UUIDv4·환경issuer검증. rawtoken/key/원인없는오류. 목적/sub/audience인가분리로401/403,원격JWKS조회없음. normalrotation/oldkey폐기회귀. publickey최대20/64KiB·token16KiB.
- 수신: 신규TLS1.2/1.3·RSA PKCS12 connector/targetport설정,public/internalport충돌·키/환경/transactional조건기동검증. enabled때다른port의Identity4route404,신규port businessOFF404,LC등미허용route는인증뒤403. 임의forwarding/중복Authorization/querytoken거절,인증뒤기존strictdecoder/service처리·204불변. LC기존security권한은미변경. 실제ALB→taskTLS설정없이는활성화금지.
- 발신: authentication=sigv4(기존기본)/workload-jwt명시선택. JWT모드는각시도새JWT/jti와같은operation/body;동일16KiB/3·5초/Retry-After/error분류·DBretry코어재사용. trustedHTTPSorigin/allowedhost/환경매칭,인증실패시SigV4/무인증fallback없음. HTTP기본OFF·private/public키미주입·실credential조회/서버호출없음.
- 계약: 사용자승인예외를Identity–Billing범위로AGENTS/ADR에명시. 기존LC/user·bodyfixture/hash·무료권/owner/구매/보존·DBschema불변. 신규auth시간fixture13개는Billing검증초안이며Identity공동수락아님. 실제trust/DB/source매핑·TLS/SG/키담당/배포값은추정하지않음.
- 테스트: 초기실Controller컨텍스트에서EntitlementQueryProperties등록누락2건을확인후수정. 대상테스트통과후최종 ./gradlew clean test **896개통과/실패0/오류0/skip0**(직전848+48), XML직접집계. 시간/numeric/overflow/duplicate/서명·unknownkid/rotation·포트/forwarding/OFF·실ControllerMockMvcJWT→decode→204/실패service미호출·TLSconnector설정·발신freshJWT/samebody/explicitmode/noAWS 및기존SigV4/무료/replicaset회귀. 실제TLShandshake/ALB/SG·IdentityAPI검증아님. diff/문서링크검사통과.
- 후속/위험: Identity독립authfixture/규격수락·상대receiver/sender구현과실제환경/key/ALB/TLS/SG·직접task차단·긴급rotation검증필요. 기존legacy sender가있으면receiver전환에맞춘배포계획필수. TLSkeystore와JWT signingkey는별도역할. outboundtrace/운영migration/epochinstaller/복원/외부watchdog/대용량hash후속유지. TMI204전체완료아님. 이번Jira쓰기/타서버/실DB/API/AWS/Secret/commit/push/배포없음.

## 2026-10-13 — Learning Core 수신 API와 SigV4 인증 구현 구분

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 사용자의 LC→Billing 구현 여부 질문에 정적 확인으로 답변. 기존 dirty 변경 보존.
- 근거: ReservationController의 reserve/confirm/cancel/status/phone continuation과 AttemptGroupEventController의 서비스 호출 구현 존재. SecurityConfig의 LATTICE_AWS_IAM 분기는 외부 IAM 인증·격리를 전제로 permitAll한다. Billing 자체 LC SigV4 검증기가 구현됐다는 의미는 아니다. 현재 AWS signer 사용은 Identity recovery 발신 코드다.
- 변경: CURRENT_STATE 및 이 기록만. 기존 API/인증 계약과 코드는 유지. Gradle 미실행(분석 작업), diff 검사 수행. LC 발신 구현/실제 AWS 배포는 미확인으로 구분한다.
- 다음: SigV4 제거 시 발신 코드 제거와 LC 수신 인증 계약 변경을 구분하고, Lattice 의존 수신 경로를 ALB permitAll로 노출하지 않도록 별도 인증 전환 범위를 확정한다. Jira/타서버/배포/commit/push 변경 없음.

## 2026-10-13 — Identity SigV4 제거 인계 범위·이행 회신

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 첨부 인계의 Billing 확인·회신 요청 수행, 기존 dirty 보존.
- 변경: TMI-204-SIGV4-REMOVAL-BILLING-REPLY-2026-10-13.md 및 CURRENT_STATE/WORKLOG. runtime/설정/의존성/fixture/타서버/Jira 변경 없음.
- 확인: recovery authentication 기본 sigv4/두 client 분기, AWS SDK main 사용은 recovery에 한정. Identity 수신 guard는 receiver OFF일 때 기존 Lattice 경로를 차단하지 않으므로 수신 permitAll 및 OFF 동작도 함께 변경해야 함. LC 경로·user JWT·body/hash·도메인 유지.
- Identity README/time-cases 읽기 및 diff: 절대시각/숫자/null 포함16개와 Billing offset13개는 다른 자료. 동기화/독립 실행/새 공동 수락 미완료로 명시. 이전896 테스트를 제거 후 결과로 재사용하지 않음.
- 검증: 문서 diff 검사, 분석 작업으로 Gradle 미실행. 운영 활성 사용처/ALB/SG/TLS/키는 조회하지 않았음.
- 다음: 범위 한정 JWT 단일화 구현 및 fixture/전체 회귀→실제 환경 inventory와 구 sender 중지/기록 보존 이행→staging 검증. 외부 발송/배포/판매/삭제 활성화 승인 아님.

## 2026-10-13 — Identity–Billing SigV4 제거·JWT 단일화 구현

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 사용자 명시 승인에 따라 구현. TMI-204 제목/상태 읽기 확인, 기존 미커밋 기능·문서 변경 보존.
- 발신: HttpIdentityRecoveryClient/RecoveryHttpTransport를 중립 immutable Request로 변경, SigV4 wrapper·credential·signer·Lattice origin 분기·명시 AWS BOM/auth/http-auth-aws 의존성 제거. Jwt client의 새 토큰/동일 body·operation·응답/예외/Retry-After/timeout/16KiB/redirect 금지 유지.
- 설정/수신: recovery.authentication과 env selector 제거, RetiredRecoveryAuthenticationConfiguration이 구 설정의 존재를 OFF/빈값/JWT값에서도 기동 오류로 처리. 발신 HTTP/수신 receiver 스위치 독립 유지. WorkloadIngressFilter는 OFF/다른포트 Identity4route 차단, SecurityConfig에서 해당 IAM permitAll/TEST role 경로와 구 withdrawal IAM 의존검사 제거. ON 필수 JWT/환경/transaction/index/TLS 검사는 workload 설정에 유지. LC/user 인증은 변경하지 않음.
- 테스트: 기존 SigV4 HTTP 테스트를 공통 JWT HTTP 회귀로 이관, 실제 loopback JWT 검증·signing 실패/원인 비노출·반복 발급 보존. trial/owner/withdrawal MVC를 실제 JWT chain으로 전환. 기존 tracing 전용 loopback 테스트는 test-only guard registration mock/permitAll chain으로 인증 범위와 분리(운영 bypass 없음); 인증/OFF/port는 별도 실제 chain 및 guard 테스트.
- 공유fixture: Identity workload-jwt-r1/time-cases16개 동일 복사, raw token을 signed payload에 넣어 실제 WorkloadJwtCodec 독립 실행. SHA256 양측 0d38a6fa289bb244c0aff0c91124de5f920fcd9c9c308536d3d754ece446f695. 기존 Billing13개와 recovery body/hash 불변. 상대 공동수락·배포증거를 대신하지 않음.
- 검증: 첫 clean test914개 중 인증/trace 기존테스트3건 실패를 경계 변경에 맞춰 보완. Mongo PaymentFoundationInitializer 초기화1건 실패는 대상/최종전체 재실행에서 재현되지 않았으며 원인을 확정하거나 runtime을 변경하지 않음. 최종 ./gradlew clean test XML 집계 **917개 성공/실패0/오류0/skip0**. diff 검사 통과. 실제 AWS/Store/Identity 호출 없음.
- 문서: AGENTS·ADR002/004·결정서·공동계약·PLAN009/204·WORKLOAD-JWT-AUTH-R1·폐기 runbook 안내·Billing 회신 §6·fixture README·CURRENT_STATE/WORKLOG 갱신. 역사적 SigV4 검증 기록 유지.
- 위험/다음: 운영 active SigV4/구 설정·양측 JWT 준비/키·ALB/SG/TLS/직접 task 차단/교체·복원 검증 후 전환. 구 sender 있으면 발신 중지/진행 요청 정리/미처리 durable 기록 유지, source capture·LC 전달 중지 금지. 기본OFF 유지, Jira쓰기/타서버/배포/판매/자동삭제/commit/push 없음.

## 2026-10-13 — 구현 완료 범위 설명

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 사용자 완료 여부 질문에 최신 CURRENT_STATE·204 계획·운영 runbook 및 recovery 코드 정적 확인.
- 결론: SigV4 제거/JWT 단일화 로컬 구현 완료와 TMI-204 전체 완료를 구분. 후자는 운영 epoch 설치/이관·복원, outbound tracing·대용량 hash 조율 및 실환경 연동/운영 gate가 남아 있음. 새로운 범위 추가나 실제 배포 승인 아님.
- 변경: CURRENT_STATE/WORKLOG만. 직전917개 통과를 기존 검증 결과로 인용하며 이번 Gradle 재실행 없음. diff 검사. 코드/기능플래그/Identity/Jira/commit/push/배포 변경 없음. 다음은204 잔여 로컬 구현을 정리한 뒤206 실환경 검증으로 진행.

## 2026-10-13 — LC→Billing JWT 전환 인계 검토·회신 초안

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 목표: 첨부 LC 제안서를 최신 Billing 구현·승인 규칙과 대조하고 검토용 회신 작성. 관련 LC 이력 TMI-116/128/122는 첨부 보고이며 Jira 조회/변경 없음.
- 검토 결과:6POST·3/6필드 reserve와 confirm/cancel/status/phone/event 업무는 일치. Billing cancel은 CALLER_ABORTED도 허용. LC IAM 분기는 외부 Lattice 인증 전제 permitAll이며 Identity JWT 검증기는 LC 신뢰/인가를 지원하지 않음. LC 전환은 현 Identity-only 승인 예외 밖이다.
- 제안:LC 전용 서명/키·issuer·sub,TTL60/skew30·배포 공개JWK·reservations/attempt-events 목적별audience,기존 body 없는401/403·다른port/OFF404. 첨부TTL120/원격JWKS/새인증JSON과 차이를 명시,connector 공유/분리와 독립flag·legacy통과차단·키운영·실제활성inventory는 동결/승인 필요. 기존 업무/무료권/owner/Identity 계약 불변.
- 변경 파일:docs/contracts/LC-BILLING-WORKLOAD-JWT-REVIEW-2026-10-13.md 신규,CURRENT_STATE 갱신·WORKLOG append. 기존 코드/문서 미커밋 변경 보존. 코드·정본 ADR/AGENTS·타서버·AWS·Jira·commit/push·배포·외부 메시지 전송 없음.
- 검증:security/DTO/strictdecoder/통합계약/r1 문서 정적 대조·git diff --check·문서 링크 존재 확인. 코드 변경 없어 Gradle 미실행, 직전917개 검증은 LC JWT 검증 증거가 아님.
- 위험/다음:실제 LC sender·AWS 상태는 첨부 보고일 뿐 독립 검증 없음. 양측 범위·claim/오류·connector 계약 동결 후 Billing OFF 구현/LC 독립fixture·업무회귀,실네트워크/키/E2E 후 별도 활성화. 구현/완료 또는 배포 일정을 확정하지 않음.

## 2026-10-13 — LC JWT 검토 의견과404 전환·복구 보완

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 목표: 사용자 전달 후속 검토 의견을 회신 초안에 반영. 관련 LC 이력116/128/122, 신규 Jira/조회/쓰기 없음.
- 내용: TTL60·배포 공개키·목적/환경audience·빈401/403·workloadTLS 공유 가능 방향 접수. 정본 계약 확정/코드 변경 승인은 아니라는 입력 유지. LC/Identity 키·권한·수신flag 독립, IAM permitAll 차단 조건 명시.
- 404 보완: LC 전달 보고의 Reservation 계약 오류/AttemptGroup DEAD_LETTER 분류 유지. Billing 정확 수신/업무route 검증 후 LC발신 단계활성화, 실패기록/원command/event 보존. Reservation status-first/동일key·Session증거/기존TTL·terminal불변, AttemptGroup 동일eventId/payload/digest로 원인 해결·담당승인 후bounded requeue. JWT/jti는fresh, rawtoken미저장. 새로운무조건retry/repairAPI/DB직접수정/fallback 없음.
- 변경 파일: LC-BILLING-WORKLOAD-JWT-REVIEW-2026-10-13.md,CURRENT_STATE·WORKLOG. 기존 사용자/동시변경 보존. 코드·정본ADR/AGENTS·LC/Identity·AWS·Jira·외부전송·commit/push·배포 변경 없음.
- 검증: 앞선 회신/최신상태 대조·git diff --check. 문서 작업으로Gradle 미실행. LC 분류와기존requeue도구는 상대보고이며 독립코드검증 없음.
- 위험/다음: exact문자열·오류precedence·독립flag/connector설정·복구담당/상한·활성inventory는 공동동결 필요. 기존복구도구 부재 시 별도 승인/구현 선행. 그 뒤Billing OFF구현·교차fixture/404보존복구E2E와 실제키/네트워크 검증 후 활성화.

## 2026-10-13 — 공동 계약에 LC JWT 활성화·404 보존/복구 절차 추가

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 목표: 사용자 명시 요청에 따라 공동계약에 수신 준비 선행 및 전환 실패작업 보존/복구 추가. 관련 LC116/128/122는 인계 이력, 신규 Jira/쓰기 없음.
- 변경 파일: BILLING_SERVICE_INTEGRATION_CONTRACT.md 갱신일/적용범위 안내/§16 신규,LC-BILLING-WORKLOAD-JWT-REVIEW-2026-10-13.md 연결,CURRENT_STATE·WORKLOG. 기존 작업 보존.
- 동작/결정:TTL60/배포공개키/환경·목적audience/빈401·403은 권장방향으로 기록,정확문자열·설정·오류precedence는 공동동결 대상. Billing 준비 검증 후 LC발신 ON,기존404 분류 유지. Reservation 원key/Session증거·status-first·TTL/terminal불변,AttemptGroup DEAD_LETTER 원event/digest 보존 후 담당승인bounded requeue. 증거보존은 기존retention정책 내이며 무기한 개인정보 보존 아님.
- 유지:업무wire/멱등성/무료권/owner/Identity승인범위 유지. LC 전환 코드/ADR/AGENTS/실환경승인으로 확대하지 않음. 키·권한·flag독립 및IAM permitAll 우회차단/복구도구확인·교차fixture를 선행조건으로 명시.
- 검증:문서링크/§16 구조·git diff --check. 문서만 변경해Gradle 미실행. LC 분류/복구도구·실network/key는 보고/미검증으로 유지. 코드·타서버·AWS·외부전송·Jira·commit/push·배포 변경 없음.
- 위험/다음:exact 인증계약 수락/승인 및ADR·AGENTS동기화 후OFF구현,양측fixture/404복구/staging 검증을 수행한다. 이번계약절 추가는 운영활성화 완료가 아님.

## 2026-10-13 — TMI-204 운영 도구·분할 hash·발신 추적 구현

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 사용자 잔여1·2번 구현 요청. 착수 전 AGENTS 전체/TMI-204 완료조건 읽기. 기존dirty보존. 작업 중 별도 LC JWT 검토/공동계약§16 및 기록이 추가됐음을 확인해 보존했으며 이번 구현에 합치거나 수정하지 않음.
- 파일: RecoveryOperator/RecoveryOperatorCli·RecoveryWorkerScheduling·RecoveryTracingTransport 신규, WithdrawalSnapshotProcessor/Worker·RecoveryHttpConfiguration·local snapshot manifest·build.gradle·Integration/Tracing/Scheduling 테스트·운영runbook/PLAN204/auth계약/CURRENT_STATE/WORKLOG.
- 운영: 별도 main/task(서버bean/HTTP없음), env credential·기본dry-run·명시apply/approved/격리확인, exactepoch/versionCAS·operationID동일요청재실행/다른요청거절,control+최소receipt동일Tx. INITIAL/ROTATE ALL_BLOCKED,RECOVER PURCHASE_BLOCKED,OPEN은현epoch/동일streambaseline+feedCAUGHT_UP/C==target필수. 일관복원만명시preserve,그외baseline/job/cursor폐기하되fact/원장보존. 옛stream/C증거120일,새epoch로기존snapshotbudget초기화안함.
- schema:기존operations에optional operator object만추가,index/collection추가없음. startup자동DDL없음. 알려진이전validator만별도명령으로additivecollMod/누락schema초기화·exact검증. 구validator상태의pre-block은승인유지보수CAS/drain선행을runbook에명시. 미지drift/활성barrier상태migration거절. 실제DDL/운영credential/도구실행없음.
- hash:한step10page/200행또는monotonic30초,DB호출2초/마지막Tx추가가능명시. constant-memorydigest와exactlease로다음tick계속,process/lease소실은처음부터검증,epoch/run/count/hash최종검증후만baseline설치. snapshot전용scheduler와unqualifiedjob용scheduler분리. memoryexpiry/terminal정리,중간checkpoint없음.
- tracing: 고정4operation/outcome의CLIENTspan,자체traceparent만전파,baggage/tracestate/외부traceparent제거. JWT/URI/operationID/body/rawexceptiontag없음. 공통응답/예외/retry·서명·wire불변.
- 검증: 초기대상74개중새테스트2건(schema2에서feed호출/Mockitocontext검증누락)을테스트setup/assertion보완. 최종 ./gradlew clean test bootJar **931통과/실패0/오류0/skip0**, XML집계·서버main패키징확인. replica-set CAS경합/receipt실패rollback/unknowncommit/중복충돌/oldticket/일관·불명복원/OPENgate/기존validator이관·drift거절/hash양보·재시작tamper·oldepoch/trace비노출/스케줄러격리검증. diff/링크검사.
- 유지:외부event/recovery-r1/scalar-end-1/authfixture/hash/LC·user인증/무료권·계정·원장·보존정책유지. 운영receipt는기존120일정리,worker기본OFF. 새제품선택없음.
- 위험/다음:206에서실제RBAC/구worker접근제거·일관restore/source가용증거/ALB·TLS·키회전/실부하·이관시간/독립watchdog·알림채널검증. CLI확인flag는격리/권한의증명이아니며별도승인필수. 자주재시작되면hash처음부터비용반복. 이번실제DB/API/AWS/Jira쓰기/타서버/commit/push/배포없음. 예상밖코드변경없음,동시문서변경은위와같이보존.

## 2026-10-13 — LC 공동계약404 구분·재처리 미준비 보정

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 목표: 사용자 후속 검토3항목을 계약/회신에 정확히 반영.
- 변경: BILLING_SERVICE_INTEGRATION_CONTRACT§16.3/16.5/16.6,LC-BILLING-WORKLOAD-JWT-REVIEW,CURRENT_STATE/WORKLOG. body 없는OFF/오포트404는Reservation CONTRACT_ERROR·AttemptGroup DEAD_LETTER,업무code OPERATION_NOT_FOUND는별도임을명시. 둘 다취소/미소비 확정이아님.
- 결정/유지:LC 보고에서승인재처리도구를찾지못함을미준비gate로반영,별도승인/구현/검증선행·DB직접수정금지. exact issuer/subject/audience·시간·오류순서/설정·공동fixture·ADR/AGENTS동기화·작업분담은미완료. 기존무료/owner/업무·Identity승인인증유지.
- 검증:문서대조·git diff --check,코드변경없어Gradle미실행. LC 코드·도구전체는독립검증하지않았으며상대보고로표시. 기존동시204코드/931검증기록보존,이번LC검증으로혼동하지않음.
- 위험/다음:세부계약공동동결/범위승인후양측OFF구현·재처리도구준비/fixture/E2E·운영gate. 코드·AGENTS/ADR인증규정·타서버·AWS·Jira·외부전송·commit/push·배포변경없음.

## 2026-10-13 — TMI-204 구현 완료 범위 안내

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 목표: 사용자 구현 완료 질문에 최신 기록 기준으로 답변.
- 변경 파일: CURRENT_STATE.md, WORKLOG.md. 승인된 TMI-204 Billing 로컬 구현 완료와 전체 결제/실환경 활성화 미완료를 구분했다.
- 검증: 직전 clean test/bootJar 931개 통과 기록 확인. 이번에는 코드 변경이 없어 Gradle 재실행 없이 diff 검사만 수행.
- 유지/결정: 기능 기본 OFF, 기존 코드·동시 LC 문서 변경 보존. Jira 종료·배포·commit/push 없음.
- 위험/다음: TMI-206 실제 연동·권한·복원·부하·알림 검증과 활성화 승인 필요. 로컬 테스트 결과로 운영 준비 완료를 주장하지 않는다.

## 2026-10-13 — TMI-205 다음 작업 순서 확인

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 목표: 204 이후205 진행 가능 범위 설명.
- 확인: PLAN-009 및 withdrawal-reconciliation-and-purge 계약,204 계획.205는 거래 확인 후 미구매 탈퇴 계정 연결정보 조건부 정리이며 PLAN-010 거래 증거/provider coverage에 의존한다. 독립205 계획은 확인되지 않아 상세 계획 정리부터 권장.
- 변경: CURRENT_STATE/WORKLOG만.15일·매시간 대상 점검/매일 미확인 재확인/7일 이내 담당 검토 유지. snapshot 또는 RC 빈 결과만으로 삭제 금지,구매 기록5년 보존 유지.
- 검증: 문서 조회와 diff 검사. 코드 변경 없어 Gradle 재실행 없음. Jira 최신 상태는 이번에 조회하지 않음.
- 다음/위험: 최신204 연결과 provider 증거·삭제 경합 규격을205 계획으로 정리한 후 구현. 실제 삭제 활성화는206/013 검증·승인 별도. 기존 변경 보존,코드/Jira/외부서비스/commit/push/배포 변경 없음.

## 2026-10-13 — LC 수정본 전달 버전 식별 보완

- 브랜치: feat/TMI-204-billing-withdrawal-recovery. 목표: 상대의 동일첨부 회신과 실제 수정본을 대조하고 재전달 파일 준비.
- 확인:현재 정본§16.3에body없는OFF/오포트404→CONTRACT_ERROR와업무OPERATION_NOT_FOUND 구분이이미반영됨. 실제 상대첨부는이번에받지않아구버전/전달누락여부확정못함. 도구준비/exact공동동결은미완료유지.
- 변경 파일:통합계약§16에LC-JWT-TRANSITION-R2식별자,LC-BILLING-WORKLOAD-JWT-CONTRACT-R2-2026-10-13.md 전달용스냅샷 신규,CURRENT_STATE/WORKLOG. 스냅샷은정본별도계약아니며변경요점/§16전문포함.
- 검증:정본문구재조회·전달본§16정본일치·git diff --check. 문서작업이라Gradle미실행. 기존동시변경보존,코드/타서버/AWS/Jira/외부전송/commit/push/배포없음.
- 다음:사용자가R2파일을새첨부로전달해문구검토요청. 상대보고재처리도구구현/세부규격동결·운영gate는별도진행.


### 2026-10-13 — LC JWT Billing B 최종 검증 재확인

- 최종 소스/fixtureREADME 정리 후 `./gradlew test bootJar` 재실행 성공. 최종 XML 합계1,127개/실패0/오류0/skip0, 실행 JAR 빌드 확인. 앞선 clean test/bootJar와 동일 결과다.
- frozen fixtures-1 SHA256/문서링크/diff 검사 통과. 후속 수정 없이 Billing B 로컬 완료로 기록한다. 실제LC/BillingTLS·키배포·C/배포/활성화는별도다.

## 2026-10-13 PLAN-010 purchase-r1 상세 기술 초안

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 사용자 요청: 구매기록/기간권/중복/탈퇴후결제 및통합조회 상세규격작성.
- 변경: docs/contracts/PLAN-010-purchase-technical-spec-r1.md 신규, PLAN010 링크/상태보충, CURRENT_STATE/WORKLOG. 기존ADR제품정책을재선택하거나기술초안을확정계약으로표시하지않음.
- 내용: Purchase/entitlement/timeline/ledger모델·멱등business key/immutable digest·기간계산,입력effect/lifecycle CAS/pending·탈퇴/삭제후격리, sync응답/오류·paid/examAvailability 호환필드후보·paid-first,provider실증/DBindex/subset/보존/worker/flags/단계별테스트.
- 유지: sync terminal120일·inbox120일·금융5년·backup35일,무료필드/수량의미/조회무변경·reserve최종판정·구매/환불/재가입정책. 유료전용GET신설없음. provider지원·신규암호화키/운영보존을실증완료로간주하지않음.
- 검증: 실제controller/DTO/PublicResponse/PaymentOffer·foundation/withdrawal index·ADR/205규격정적대조,문서링크/JSON예시/diff검사. 코드미변경으로Gradle미실행,과거테스트를010검증으로재사용하지않음.
- 미확인/다음:기술후보리뷰·신규effect/schema/crypto·RC서명/원구매자/SDKIDfixture·미결case보존·workerexact준비후OFF코어구현.011/012/013·205PR3/206실활성화별도.
- 범위:기존및동시LC코드/문서/검증기록변경보존. 타서버/DB/credential/외부API/Jira/배포/commit/push변경없음.

## 2026-10-13 공동 테스트 환경 검증 범위 정리

- 사용자 요청: 「그럼 이건 한번에 다른 거까지 검증하자」.
- runbooks/BILLING-JOINT-STAGING-VALIDATION-2026-10-13.md에 연결/JWT·무료 시험·Identity 이벤트/복구·조회/계정·lifecycle·관측 검증과 선행 조건/순서를 작성했다. 실제 사용자 트래픽 여부 확인, 미구현 결제·C 도구·실삭제 gate 및 미실행 결과 구분을 명시했다.
- 문서만 변경. 배포·기능 활성화·타저장소/DB/Jira 변경·commit/push 없음. 기존 별도 변경 보존. 문서 변경이므로 Gradle 미실행.

## 2026-10-13 구현·계획 복잡도 단순화 검토

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 목표: 사용자가 감수할 수 있는 지연/수동 운영과 금전·삭제 정합성 위험을 구분해 축소 후보 제시. 분석이며 변경 승인으로 확대하지 않음.
- 근거: PLAN-010-purchase-technical-spec-r1.md의 §5.6 입력 source/effect/lifecycle pending, provider 처리 budget/worker, lookup key 회전 계획; CleanupEvidence.java의 Coverage 세 버전과 unavailable ports, CleanupConfiguration.java의 실제 provider 미연결/OFF 보호, CleanupPurger.java의 FinalPolicy·lease/version/epoch/최종 Tx.
- 권장 후보: (1) 별도 범용 payment_evidence_effects 대신 입력 row terminal CAS와 pending 갱신을 같은 Tx로 하고 미매핑/보존 인계 case만 별도 유지; (2) 신규 sync는 durable 접수 후 공통 worker 경로, 이미 반영된 결과만 즉시 반환; (3) cleanup adapter/coverage/drain 정책을 하나의 배포형 revision으로 관리하는 방안; (4) 자동 온라인 lookup key 회전 대신 writer 중단·검증된 수동 이관 절차. 기존 초안이 자동 회전 구현을 완료했다는 뜻은 아님.
- 위험/조건: worker-only는 반영 지연과 적체 위험, 입력별 pending 방식은 source별 원자 갱신/TTL 인계 회귀 필수, 통합 정책은 작은 변경에도 READY 증거 재확인 필요, 수동 key 교체는 일시 중단·운영 실수 위험. 실제 외부 조회 coverage 지원은 미확인이라 단일 revision 자체를 미구매 증명으로 삼지 않음. 미해결 보관을 무기한 승인하거나15일 조건부 삭제 정책을 변경하지 않음.
- 유지: 인증/owner/environment, provider 거래 검증, unique/멱등, 구매·권리·원장 Tx, 탈퇴·삭제 경합, 무료·유료 분리, 환불/개인정보 보존 계약. 테스트 개수 자체를 줄이는 것을 목표로 하지 않음. 구현된205 삭제 잠금/최종 확인은 유지 권장.
- 변경 파일: CURRENT_STATE.md와 WORKLOG.md의 분석 기록만. 정적 대조 및 diff whitespace 검사, 코드 미변경으로 Gradle 미실행. 이전 테스트를 이번 단순화 검증으로 사용하지 않음. 기존 dirty/동시 작업 보존, 타서버/Jira/DB/배포/commit/push 변경 없음.
- 다음: 사용자 선택 후 PLAN-010 및 필요한205 규격을 수정하고, 기존 schema가 있는 변경은 reader 호환/증거 무효화 이관을 명시한 뒤 구현. 이번에는 정본 계약과 코드 미수정.

## 2026-10-13 단순화 후보 기존안 대비 설명

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 사용자 요청: 네 가지 단순화 후보의 기존 방식/변경 방식/장단점 설명.
- 정적 근거: PLAN-010 기술 초안의 effect/source/pending, 5초 조회 후 worker, lookup hash old/new 이관 규격과 CleanupEvidence.Coverage 세 버전 재확인. 1·2·4는 계획 단계이며3은 타입 구현/실제 provider 미연결임을 구분.
- 설명 보완: pending 카운터를 무조건 제거하는 제안 아님. source terminal 전이와 카운터 갱신은 동일 Tx, 미매핑/TTL 인계는 보존. 공통 worker는 단일 서버 강제 아님. 정책 단일 revision은 cleanup 전용이며 일반 배포마다 증거 무효화하지 않음. 키 대상은 거래 lookup hash이며 기존 초안에 자동 교체 시스템이 이미 구현됐다는 주장은 하지 않음.
- 장단점: 중복 상태/실행 경로 감소 대 반영 지연, 세부 정책 독립 변경 포기 대 재확인 증가, 온라인 이관 복잡도 감소 대 점검/수동 검증. 거래 고유성·삭제 최종 확인·인증·보존 계약은 유지 권장.
- 변경: CURRENT_STATE/WORKLOG 기록만. diff 검사, 코드 변경 없으므로 Gradle 미실행. 기존 변경 보존, 타서버/DB/Jira/배포/commit/push 없음. 다음: 선택 승인 후 규격 개정, 아직 적용하지 않음.

## 2026-10-13 단순화 1·3·4 선택 승인과2 지연 설명

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 사용자 승인: 접수 중심 관리, 삭제 정책 단일 revision, 고정 lookup 키/keyVersion·교체 기능 선개발 제외. worker-only는 소요시간 질문이며 미승인이다.
- PLAN-010 초안의5초tick/외부조회5초budget을 확인. 적체 없는 첫 처리 대기0~5초에 외부 조회·DB·앱 재조회가 더해지며, provider 반영 지연/재시도/적체에는 상한 보장이 없음을 설명. 예시 계산은 실측이나 SLA가 아니다. 1초tick은 추가 선택 가능한 제안이지 확정값 아님.
- CURRENT_STATE/WORKLOG 승인 기록만 변경. 코드·기술규격 본문·schema·Jira·배포·타서버·commit/push 변경 없음, 기존 변경 보존. 정적 조회/diff검사, 코드 미변경으로 Gradle 미실행.
- 다음: 승인된1·3·4 기술규격 동기화 및3의 기존 증거/validator 이관 설계;2는 사용자 결정 후 반영. 인증·멱등·원자지급·삭제 최종 확인 유지.

## 2026-10-13 구매 worker 1초poll 부하 검토

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 요청:1초주기의 부하 확인. PLAN-010 §5.10·5.12와 payment 소스 파일 목록을 정적 확인; 실제 구매worker/운영 부하 테스트는 아직 없음.
- 추산: idle DB query rate=q*N/T. sync/inbox 각1조회·서버2대 가정은1초4QPS,5초0.8QPS. 일일345600 대69120조회이며 실제 claim/fence/작업write와 재시도 부하는 제외한 예시, 실배포 topology/DB비용 확인값 아님.
- 조건: due index로 실행 가능한 작업만 bounded 조회, 실제predicate/sort의 explain 검증, 빈큐에서 외부API/heartbeat write/Tx 금지 방향, tick중첩 방지, lease 중복선점 보호, nextAttemptAt/backoff 유지. 1초tick과 같은 거래1초재조회·전체회원 스캔은 다름. 내부240회/분·동시4budget은 provider실quota 보장 아님.
- 판단: 인덱스 기반 소수poll은 설계상 큰 부하를 뜻하지 않지만 초안만으로 안전성 단정 불가. 1초는 설정 가능 후보로 두고 staging idle/backlog·다중인스턴스에서 examined docs/keys·DBCPU/latency·lease경합·queue age를 검증; 문제 있으면 주기 조정. 기존2~5초 예시는 SLA가 아님.
- 변경: CURRENT_STATE/WORKLOG 분석기록만. diff검사, 코드미변경으로Gradle미실행. 코드/계약본문/flags/실DB/Jira/외부API/타서버/commit/push 변경 없음, 기존 변경 보존. 다음:2번 선택 후 규격반영 및구현/부하검증.

## 2026-10-13 단순화 방향 승인 문서 반영

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 사용자 「좋아 그렇게 하는 걸로 하자」로 공통background/초기1초poll·부하확인 후 조정 방향 승인. 앞서 승인된1·3·4와 함께 문서 정합화.
- 변경: PLAN-010 계획/상세 기술규격, TMI-205 기술규격, CONTRACT_DECISIONS, CURRENT_STATE, WORKLOG. 범용 evidence_effects 후보/index 삭제, source counted/pending 동일Tx·예외case 인계 보존, 신규sync durable202·기반영거래200/외부조회 worker 단일화,1초 due poll 및빈큐/중첩/부하gate,고정lookup키를 반영했다. 지급 ledger effect unique는 유지한다.
- 205는 단일cleanupPolicyVersion 목표만 반영. 현재 세 필드와r1/r2 manifest는 구현 사실로 보존하고 새revision/호환reader/기존proof재확인·구writer격리 필요를 명시했다. 이관 코드나schema를 문서 승인만으로 적용하지 않음.
- 앱 Retry-After:5·sync12회/분은 유지. 앞선 앱2초재조회 가정 예시를 SLA로 쓰지 않으며1초tick과UI갱신을구분. 전체010기술규격/구현·provider검증·실부하·배포 승인으로 확대하지 않음.
- 검증: 문서정적대조·git diff --check 통과. 코드 미변경으로 Gradle 미실행. 기존 dirty/동시변경 보존, 타서버/실DB/설정/Jira/배포/commit/push 없음. 다음:source/case exact schema 및205호환이관 규격 정리 후 승인 범위 구현, staging 부하 측정.

## 2026-10-13 다음 구현 순서 안내

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 사용자 요청: 다음 작업 안내. PLAN010 상세규격 §6.1/6.3과 최신 승인 기록 정적 대조.
- 권장: 첫slice의 접수/case schema·index·pending 원자 갱신 규격을 기존 문서에서 확정 후 OFF 구매/기간권 코어, sync/통합조회, 공통worker/provider 실증·부하검증 순서.205실거래 연결 때 정책revision 이관을 포함하며 실삭제는 별도gate 유지. 별도 큰 계획서를 반복 작성할 필요 없음.
- 변경: CURRENT_STATE/WORKLOG 안내기록만. 코드/정책/DB/Jira/배포/타서버/commit/push 변경없음. 코드미변경으로Gradle미실행, diff검사. 기존변경보존. 다음:사용자 착수 요청 후 해당 이슈/완료조건 확인과 첫slice 범위 준비.

## 2026-10-13 TMI-210 Jira 우선 생성

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 직전 사용자 구현 요청으로 코드/기반/상위이슈를 읽었지만 편집 전 사용자가 중단하고 Jira 우선 생성을 요청함. 신규 구현 파일/실행중 빌드 없음.
- Jira TMI-137 에픽·프로젝트 작업 유형/필수필드 확인, parent/PLAN010 검색으로 동일 첫slice 이슈 부재 확인 후 TMI-210 생성. 제목: [Billing PLAN-010 1차] 접수 중심 구매·기간권 원장 코어 구현. parent=TMI-137, 상태=해야 할 일을 재조회 확인. 담당자 지정/기존 이슈 수정/추가 상태전환 없음.
- 포함: 접수/case 저장규격, source pending 원자갱신,검증관측/fake 기반구매·기간권·원장·timeline,중복/탈퇴/삭제경합·Mongo회귀. 제외: public API/실provider/1초worker·부하/205실삭제·정책revision 이관/011·012/배포. 전체010완료와 첫slice완료 구분.
- 로컬 변경: PLAN010 Jira링크/범위, CURRENT_STATE, WORKLOG. 문서 diff검사, 코드변경없어 Gradle 미실행. 기존dirty/동시변경 보존. 타서버/DB/배포/commit/push 없음.
- 다음: TMI-210 완료조건에 맞춰 코어 구현 재개. 이번 요청에서는 Jira 생성까지만 수행.

## 2026-10-13 TMI-209→210 브랜치 분리 사전 안내

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 사용자 질문:210새브랜치가209작업과섞이는지확인.
- git status/log/branch/worktree 읽기결과 현재209와로컬develop HEAD=ee164ac,209전용커밋없음. cleanup·LC JWT·결제계획 변경이 미커밋/미추적으로 같은checkout에 존재.210브랜치없고210코어신규코드는아직없음. 원격fetch/PR최신조회는수행하지않음.
- 권장:지금branch만생성하면미커밋파일도따라가므로먼저기존변경을작업범위별검토하고사용자가커밋/PR정리,가능하면develop병합후210분기. 일괄git add로209에모든작업을섞지않음. 병합전종속브랜치는가능하지만기반커밋포함/추후정리비용을설명.
- 변경:CURRENT_STATE/WORKLOG기록만,기존파일보존. 브랜치생성/전환·stage·commit/push·stash·Jira/배포/코드변경없음. 정적확인/diff검사,코드미변경으로Gradle미실행. 다음:사용자방향에따라기존dirty변경범위분류부터진행.

## 2026-10-13 기존 변경 분류 및 Git 예외 범위 확인

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 사용자「이번작업에 한해서 해줘」가commit/push예외까지포함하는지확인질문. 읽기전용분류를먼저수행했고Git쓰기미수행.
- 분류:209cleanup코드/설정/schema/테스트·205계획/안내,LC JWT보안/컨트롤러회귀/fixture/script/인증계약,결제통합조회/단순화/210문서,clock회귀/운영인계. application.yml·AGENTS·통합계약·공통기록은복수범위로hunk검토필요.
- 주의:기존WORKLOG diff가5005줄삭제/1576줄추가로관측됨(이번항목추가전). 과거기록축약/재작성변경은기존dirty로보존,의도확인없이되돌리거나커밋하지않음. LC검토문서도큰변경으로별도검토필요.
- 변경:이기록및CURRENT_STATE만갱신. stage/commit/push/branch/stash/Jira/코드/배포변경없음. 정적diff분류,코드미변경으로Gradle미실행. 다음:예외범위확인후검증·단위별stage/커밋계획,과거기록보존문제부터해결.


## 2026-10-13 — 사용자 승인에 따른 작업 기록 원문 복구

- commit/push/PR 생성 전 점검에서 WORKLOG가 CURRENT_STATE 계열 내용으로 덮여 있고 LC JWT 검토서는 통합계약 내용과 일치함을 확인했다. 사용자에게 보고하고 「백업 후 원문 복구·새 기록 보존」 승인을 받았다.
- HEAD의 기존 WORKLOG 원문을 byte 순서 그대로 보존하고 현재 파일의 LC B 최종 검증 항목 이후 신규 기록을 append했다. 덮어쓴 상태의 두 파일은 /private/tmp/billing-pre-commit-backup.J6RctO 에 별도 백업했다. CURRENT_STATE의 기존 작업 요약도 유지한다. 사라진 개별 작업 기록을 새로 지어내지 않는다.
- LC JWT 검토서는 HEAD 원문을 복구하고 최신 구현/수락 문서 연결만 덧붙였다. 원문 복구는 과거 검토를 최신 승인보다 우선한다는 뜻이 아니다.


## 2026-10-13 기존 작업 커밋·push·PR 준비 검증

- 사용자 명시 예외 승인: 이번 작업의 commit/push/PR 생성까지. 병합·210구현·배포는 범위 밖. feat/TMI-209-billing-cleanup-foundation의 기존 cleanup/LC JWT/결제문서를 범위별 커밋으로 분리한다.
- clean test bootJar 재실행: 1,127개 통과/실패0/오류0/skip0, BUILD SUCCESSFUL. LC fixture 3개 SHA256 일치, diff whitespace 검사 통과. 실제 Store/RevenueCat/AWS/배포·부하 검증을 수행한 것은 아니다.
- 사용자 승인으로 WORKLOG 및 LC 검토서의 덮어쓴 내용을 /private/tmp/billing-pre-commit-backup.J6RctO 에 백업하고 HEAD 원문+새 기록을 보존했다. 이전 기록 삭제 없음.
- 동시 작업에서 추가된 REVENUECAT-GOOGLE-REGISTRATION-2026-10-13.md와 CURRENT_STATE/WORKLOG의 해당 항목은 별도 작업으로 보존하고 이번 커밋에서 제외한다. 기존 공통문서의 이번 범위만 snapshot으로 stage한다.
- 코드 동작 변경 없이 정리/검증/버전관리 수행. Jira 상태는 변경하지 않는다. 다음:develop 대상 PR 생성·연결확인;병합은사용자판단후별도,210은병합된기반에서착수.


## 2026-10-13 PR #13 생성·push 확인

- 사용자 이번 작업 한정 예외 승인으로 commit/push/PR 생성 완료. cleanup65bb8dd, LC JWT379d543, 결제문서7ebfae8을 기존209브랜치에 분리했다. PR: https://github.com/Too-Much-I/app-back-end-billing/pull/13 (develop 대상). 생성 후 OPEN/대상branch/세 커밋을 재조회하고 현재 chat에 PR을 첨부했다.
- clean test bootJar 1,127통과/실패0/오류0/skip0, fixture3개SHA256/diff검사통과. 이후 코드수정없음. PR 병합·배포·210브랜치/코드·Jira 상태변경 없음.
- WORKLOG/LC검토서 원문복구 및 백업은 사용자 별도승인 후 수행. 백업:/private/tmp/billing-pre-commit-backup.J6RctO. 다른 작업의 RevenueCat Google등록 문서와 두 공통기록의해당항목은 working tree에 보존·PR제외.
- 이번 결과기록은 별도문서커밋으로 같은PR에 반영한다. 다음:PR검토/병합은별도승인,이후최신develop기반210착수. 남은별도작업변경은임의stash/삭제/커밋하지않음.


### 2026-10-13 — LC JWT Billing B 최종 검증 재확인

- 최종 소스/fixtureREADME 정리 후 `./gradlew test bootJar` 재실행 성공. 최종 XML 합계1,127개/실패0/오류0/skip0, 실행 JAR 빌드 확인. 앞선 clean test/bootJar와 동일 결과다.
- frozen fixtures-1 SHA256/문서링크/diff 검사 통과. 후속 수정 없이 Billing B 로컬 완료로 기록한다. 실제LC/BillingTLS·키배포·C/배포/활성화는별도다.

## 2026-10-13 PLAN-010 purchase-r1 상세 기술 초안

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 사용자 요청: 구매기록/기간권/중복/탈퇴후결제 및통합조회 상세규격작성.
- 변경: docs/contracts/PLAN-010-purchase-technical-spec-r1.md 신규, PLAN010 링크/상태보충, CURRENT_STATE/WORKLOG. 기존ADR제품정책을재선택하거나기술초안을확정계약으로표시하지않음.
- 내용: Purchase/entitlement/timeline/ledger모델·멱등business key/immutable digest·기간계산,입력effect/lifecycle CAS/pending·탈퇴/삭제후격리, sync응답/오류·paid/examAvailability 호환필드후보·paid-first,provider실증/DBindex/subset/보존/worker/flags/단계별테스트.
- 유지: sync terminal120일·inbox120일·금융5년·backup35일,무료필드/수량의미/조회무변경·reserve최종판정·구매/환불/재가입정책. 유료전용GET신설없음. provider지원·신규암호화키/운영보존을실증완료로간주하지않음.
- 검증: 실제controller/DTO/PublicResponse/PaymentOffer·foundation/withdrawal index·ADR/205규격정적대조,문서링크/JSON예시/diff검사. 코드미변경으로Gradle미실행,과거테스트를010검증으로재사용하지않음.
- 미확인/다음:기술후보리뷰·신규effect/schema/crypto·RC서명/원구매자/SDKIDfixture·미결case보존·workerexact준비후OFF코어구현.011/012/013·205PR3/206실활성화별도.
- 범위:기존및동시LC코드/문서/검증기록변경보존. 타서버/DB/credential/외부API/Jira/배포/commit/push변경없음.

## 2026-10-13 공동 테스트 환경 검증 범위 정리

- 사용자 요청: 「그럼 이건 한번에 다른 거까지 검증하자」.
- runbooks/BILLING-JOINT-STAGING-VALIDATION-2026-10-13.md에 연결/JWT·무료 시험·Identity 이벤트/복구·조회/계정·lifecycle·관측 검증과 선행 조건/순서를 작성했다. 실제 사용자 트래픽 여부 확인, 미구현 결제·C 도구·실삭제 gate 및 미실행 결과 구분을 명시했다.
- 문서만 변경. 배포·기능 활성화·타저장소/DB/Jira 변경·commit/push 없음. 기존 별도 변경 보존. 문서 변경이므로 Gradle 미실행.

## 2026-10-13 구현·계획 복잡도 단순화 검토

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 목표: 사용자가 감수할 수 있는 지연/수동 운영과 금전·삭제 정합성 위험을 구분해 축소 후보 제시. 분석이며 변경 승인으로 확대하지 않음.
- 근거: PLAN-010-purchase-technical-spec-r1.md의 §5.6 입력 source/effect/lifecycle pending, provider 처리 budget/worker, lookup key 회전 계획; CleanupEvidence.java의 Coverage 세 버전과 unavailable ports, CleanupConfiguration.java의 실제 provider 미연결/OFF 보호, CleanupPurger.java의 FinalPolicy·lease/version/epoch/최종 Tx.
- 권장 후보: (1) 별도 범용 payment_evidence_effects 대신 입력 row terminal CAS와 pending 갱신을 같은 Tx로 하고 미매핑/보존 인계 case만 별도 유지; (2) 신규 sync는 durable 접수 후 공통 worker 경로, 이미 반영된 결과만 즉시 반환; (3) cleanup adapter/coverage/drain 정책을 하나의 배포형 revision으로 관리하는 방안; (4) 자동 온라인 lookup key 회전 대신 writer 중단·검증된 수동 이관 절차. 기존 초안이 자동 회전 구현을 완료했다는 뜻은 아님.
- 위험/조건: worker-only는 반영 지연과 적체 위험, 입력별 pending 방식은 source별 원자 갱신/TTL 인계 회귀 필수, 통합 정책은 작은 변경에도 READY 증거 재확인 필요, 수동 key 교체는 일시 중단·운영 실수 위험. 실제 외부 조회 coverage 지원은 미확인이라 단일 revision 자체를 미구매 증명으로 삼지 않음. 미해결 보관을 무기한 승인하거나15일 조건부 삭제 정책을 변경하지 않음.
- 유지: 인증/owner/environment, provider 거래 검증, unique/멱등, 구매·권리·원장 Tx, 탈퇴·삭제 경합, 무료·유료 분리, 환불/개인정보 보존 계약. 테스트 개수 자체를 줄이는 것을 목표로 하지 않음. 구현된205 삭제 잠금/최종 확인은 유지 권장.
- 변경 파일: CURRENT_STATE.md와 WORKLOG.md의 분석 기록만. 정적 대조 및 diff whitespace 검사, 코드 미변경으로 Gradle 미실행. 이전 테스트를 이번 단순화 검증으로 사용하지 않음. 기존 dirty/동시 작업 보존, 타서버/Jira/DB/배포/commit/push 변경 없음.
- 다음: 사용자 선택 후 PLAN-010 및 필요한205 규격을 수정하고, 기존 schema가 있는 변경은 reader 호환/증거 무효화 이관을 명시한 뒤 구현. 이번에는 정본 계약과 코드 미수정.

## 2026-10-13 단순화 후보 기존안 대비 설명

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 사용자 요청: 네 가지 단순화 후보의 기존 방식/변경 방식/장단점 설명.
- 정적 근거: PLAN-010 기술 초안의 effect/source/pending, 5초 조회 후 worker, lookup hash old/new 이관 규격과 CleanupEvidence.Coverage 세 버전 재확인. 1·2·4는 계획 단계이며3은 타입 구현/실제 provider 미연결임을 구분.
- 설명 보완: pending 카운터를 무조건 제거하는 제안 아님. source terminal 전이와 카운터 갱신은 동일 Tx, 미매핑/TTL 인계는 보존. 공통 worker는 단일 서버 강제 아님. 정책 단일 revision은 cleanup 전용이며 일반 배포마다 증거 무효화하지 않음. 키 대상은 거래 lookup hash이며 기존 초안에 자동 교체 시스템이 이미 구현됐다는 주장은 하지 않음.
- 장단점: 중복 상태/실행 경로 감소 대 반영 지연, 세부 정책 독립 변경 포기 대 재확인 증가, 온라인 이관 복잡도 감소 대 점검/수동 검증. 거래 고유성·삭제 최종 확인·인증·보존 계약은 유지 권장.
- 변경: CURRENT_STATE/WORKLOG 기록만. diff 검사, 코드 변경 없으므로 Gradle 미실행. 기존 변경 보존, 타서버/DB/Jira/배포/commit/push 없음. 다음: 선택 승인 후 규격 개정, 아직 적용하지 않음.

## 2026-10-13 단순화 1·3·4 선택 승인과2 지연 설명

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 사용자 승인: 접수 중심 관리, 삭제 정책 단일 revision, 고정 lookup 키/keyVersion·교체 기능 선개발 제외. worker-only는 소요시간 질문이며 미승인이다.
- PLAN-010 초안의5초tick/외부조회5초budget을 확인. 적체 없는 첫 처리 대기0~5초에 외부 조회·DB·앱 재조회가 더해지며, provider 반영 지연/재시도/적체에는 상한 보장이 없음을 설명. 예시 계산은 실측이나 SLA가 아니다. 1초tick은 추가 선택 가능한 제안이지 확정값 아님.
- CURRENT_STATE/WORKLOG 승인 기록만 변경. 코드·기술규격 본문·schema·Jira·배포·타서버·commit/push 변경 없음, 기존 변경 보존. 정적 조회/diff검사, 코드 미변경으로 Gradle 미실행.
- 다음: 승인된1·3·4 기술규격 동기화 및3의 기존 증거/validator 이관 설계;2는 사용자 결정 후 반영. 인증·멱등·원자지급·삭제 최종 확인 유지.

## 2026-10-13 구매 worker 1초poll 부하 검토

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 요청:1초주기의 부하 확인. PLAN-010 §5.10·5.12와 payment 소스 파일 목록을 정적 확인; 실제 구매worker/운영 부하 테스트는 아직 없음.
- 추산: idle DB query rate=q*N/T. sync/inbox 각1조회·서버2대 가정은1초4QPS,5초0.8QPS. 일일345600 대69120조회이며 실제 claim/fence/작업write와 재시도 부하는 제외한 예시, 실배포 topology/DB비용 확인값 아님.
- 조건: due index로 실행 가능한 작업만 bounded 조회, 실제predicate/sort의 explain 검증, 빈큐에서 외부API/heartbeat write/Tx 금지 방향, tick중첩 방지, lease 중복선점 보호, nextAttemptAt/backoff 유지. 1초tick과 같은 거래1초재조회·전체회원 스캔은 다름. 내부240회/분·동시4budget은 provider실quota 보장 아님.
- 판단: 인덱스 기반 소수poll은 설계상 큰 부하를 뜻하지 않지만 초안만으로 안전성 단정 불가. 1초는 설정 가능 후보로 두고 staging idle/backlog·다중인스턴스에서 examined docs/keys·DBCPU/latency·lease경합·queue age를 검증; 문제 있으면 주기 조정. 기존2~5초 예시는 SLA가 아님.
- 변경: CURRENT_STATE/WORKLOG 분석기록만. diff검사, 코드미변경으로Gradle미실행. 코드/계약본문/flags/실DB/Jira/외부API/타서버/commit/push 변경 없음, 기존 변경 보존. 다음:2번 선택 후 규격반영 및구현/부하검증.

## 2026-10-13 단순화 방향 승인 문서 반영

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 사용자 「좋아 그렇게 하는 걸로 하자」로 공통background/초기1초poll·부하확인 후 조정 방향 승인. 앞서 승인된1·3·4와 함께 문서 정합화.
- 변경: PLAN-010 계획/상세 기술규격, TMI-205 기술규격, CONTRACT_DECISIONS, CURRENT_STATE, WORKLOG. 범용 evidence_effects 후보/index 삭제, source counted/pending 동일Tx·예외case 인계 보존, 신규sync durable202·기반영거래200/외부조회 worker 단일화,1초 due poll 및빈큐/중첩/부하gate,고정lookup키를 반영했다. 지급 ledger effect unique는 유지한다.
- 205는 단일cleanupPolicyVersion 목표만 반영. 현재 세 필드와r1/r2 manifest는 구현 사실로 보존하고 새revision/호환reader/기존proof재확인·구writer격리 필요를 명시했다. 이관 코드나schema를 문서 승인만으로 적용하지 않음.
- 앱 Retry-After:5·sync12회/분은 유지. 앞선 앱2초재조회 가정 예시를 SLA로 쓰지 않으며1초tick과UI갱신을구분. 전체010기술규격/구현·provider검증·실부하·배포 승인으로 확대하지 않음.
- 검증: 문서정적대조·git diff --check 통과. 코드 미변경으로 Gradle 미실행. 기존 dirty/동시변경 보존, 타서버/실DB/설정/Jira/배포/commit/push 없음. 다음:source/case exact schema 및205호환이관 규격 정리 후 승인 범위 구현, staging 부하 측정.

## 2026-10-13 다음 구현 순서 안내

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 사용자 요청: 다음 작업 안내. PLAN010 상세규격 §6.1/6.3과 최신 승인 기록 정적 대조.
- 권장: 첫slice의 접수/case schema·index·pending 원자 갱신 규격을 기존 문서에서 확정 후 OFF 구매/기간권 코어, sync/통합조회, 공통worker/provider 실증·부하검증 순서.205실거래 연결 때 정책revision 이관을 포함하며 실삭제는 별도gate 유지. 별도 큰 계획서를 반복 작성할 필요 없음.
- 변경: CURRENT_STATE/WORKLOG 안내기록만. 코드/정책/DB/Jira/배포/타서버/commit/push 변경없음. 코드미변경으로Gradle미실행, diff검사. 기존변경보존. 다음:사용자 착수 요청 후 해당 이슈/완료조건 확인과 첫slice 범위 준비.

## 2026-10-13 TMI-210 Jira 우선 생성

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 직전 사용자 구현 요청으로 코드/기반/상위이슈를 읽었지만 편집 전 사용자가 중단하고 Jira 우선 생성을 요청함. 신규 구현 파일/실행중 빌드 없음.
- Jira TMI-137 에픽·프로젝트 작업 유형/필수필드 확인, parent/PLAN010 검색으로 동일 첫slice 이슈 부재 확인 후 TMI-210 생성. 제목: [Billing PLAN-010 1차] 접수 중심 구매·기간권 원장 코어 구현. parent=TMI-137, 상태=해야 할 일을 재조회 확인. 담당자 지정/기존 이슈 수정/추가 상태전환 없음.
- 포함: 접수/case 저장규격, source pending 원자갱신,검증관측/fake 기반구매·기간권·원장·timeline,중복/탈퇴/삭제경합·Mongo회귀. 제외: public API/실provider/1초worker·부하/205실삭제·정책revision 이관/011·012/배포. 전체010완료와 첫slice완료 구분.
- 로컬 변경: PLAN010 Jira링크/범위, CURRENT_STATE, WORKLOG. 문서 diff검사, 코드변경없어 Gradle 미실행. 기존dirty/동시변경 보존. 타서버/DB/배포/commit/push 없음.
- 다음: TMI-210 완료조건에 맞춰 코어 구현 재개. 이번 요청에서는 Jira 생성까지만 수행.

## 2026-10-13 TMI-209→210 브랜치 분리 사전 안내

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 사용자 질문:210새브랜치가209작업과섞이는지확인.
- git status/log/branch/worktree 읽기결과 현재209와로컬develop HEAD=ee164ac,209전용커밋없음. cleanup·LC JWT·결제계획 변경이 미커밋/미추적으로 같은checkout에 존재.210브랜치없고210코어신규코드는아직없음. 원격fetch/PR최신조회는수행하지않음.
- 권장:지금branch만생성하면미커밋파일도따라가므로먼저기존변경을작업범위별검토하고사용자가커밋/PR정리,가능하면develop병합후210분기. 일괄git add로209에모든작업을섞지않음. 병합전종속브랜치는가능하지만기반커밋포함/추후정리비용을설명.
- 변경:CURRENT_STATE/WORKLOG기록만,기존파일보존. 브랜치생성/전환·stage·commit/push·stash·Jira/배포/코드변경없음. 정적확인/diff검사,코드미변경으로Gradle미실행. 다음:사용자방향에따라기존dirty변경범위분류부터진행.

## 2026-10-13 기존 변경 분류 및 Git 예외 범위 확인

- 브랜치: feat/TMI-209-billing-cleanup-foundation. 사용자「이번작업에 한해서 해줘」가commit/push예외까지포함하는지확인질문. 읽기전용분류를먼저수행했고Git쓰기미수행.
- 분류:209cleanup코드/설정/schema/테스트·205계획/안내,LC JWT보안/컨트롤러회귀/fixture/script/인증계약,결제통합조회/단순화/210문서,clock회귀/운영인계. application.yml·AGENTS·통합계약·공통기록은복수범위로hunk검토필요.
- 주의:기존WORKLOG diff가5005줄삭제/1576줄추가로관측됨(이번항목추가전). 과거기록축약/재작성변경은기존dirty로보존,의도확인없이되돌리거나커밋하지않음. LC검토문서도큰변경으로별도검토필요.
- 변경:이기록및CURRENT_STATE만갱신. stage/commit/push/branch/stash/Jira/코드/배포변경없음. 정적diff분류,코드미변경으로Gradle미실행. 다음:예외범위확인후검증·단위별stage/커밋계획,과거기록보존문제부터해결.


## 2026-10-13 — 사용자 승인에 따른 작업 기록 원문 복구

- commit/push/PR 생성 전 점검에서 WORKLOG가 CURRENT_STATE 계열 내용으로 덮여 있고 LC JWT 검토서는 통합계약 내용과 일치함을 확인했다. 사용자에게 보고하고 「백업 후 원문 복구·새 기록 보존」 승인을 받았다.
- HEAD의 기존 WORKLOG 원문을 byte 순서 그대로 보존하고 현재 파일의 LC B 최종 검증 항목 이후 신규 기록을 append했다. 덮어쓴 상태의 두 파일은 /private/tmp/billing-pre-commit-backup.J6RctO 에 별도 백업했다. CURRENT_STATE의 기존 작업 요약도 유지한다. 사라진 개별 작업 기록을 새로 지어내지 않는다.
- LC JWT 검토서는 HEAD 원문을 복구하고 최신 구현/수락 문서 연결만 덧붙였다. 원문 복구는 과거 검토를 최신 승인보다 우선한다는 뜻이 아니다.


## 2026-10-13 기존 작업 커밋·push·PR 준비 검증

- 사용자 명시 예외 승인: 이번 작업의 commit/push/PR 생성까지. 병합·210구현·배포는 범위 밖. feat/TMI-209-billing-cleanup-foundation의 기존 cleanup/LC JWT/결제문서를 범위별 커밋으로 분리한다.
- clean test bootJar 재실행: 1,127개 통과/실패0/오류0/skip0, BUILD SUCCESSFUL. LC fixture 3개 SHA256 일치, diff whitespace 검사 통과. 실제 Store/RevenueCat/AWS/배포·부하 검증을 수행한 것은 아니다.
- 사용자 승인으로 WORKLOG 및 LC 검토서의 덮어쓴 내용을 /private/tmp/billing-pre-commit-backup.J6RctO 에 백업하고 HEAD 원문+새 기록을 보존했다. 이전 기록 삭제 없음.
- 동시 작업에서 추가된 REVENUECAT-GOOGLE-REGISTRATION-2026-10-13.md와 CURRENT_STATE/WORKLOG의 해당 항목은 별도 작업으로 보존하고 이번 커밋에서 제외한다. 기존 공통문서의 이번 범위만 snapshot으로 stage한다.
- 코드 동작 변경 없이 정리/검증/버전관리 수행. Jira 상태는 변경하지 않는다. 다음:develop 대상 PR 생성·연결확인;병합은사용자판단후별도,210은병합된기반에서착수.


## 2026-10-13 PR #13 생성·push 확인

- 사용자 이번 작업 한정 예외 승인으로 commit/push/PR 생성 완료. cleanup65bb8dd, LC JWT379d543, 결제문서7ebfae8을 기존209브랜치에 분리했다. PR: https://github.com/Too-Much-I/app-back-end-billing/pull/13 (develop 대상). 생성 후 OPEN/대상branch/세 커밋을 재조회하고 현재 chat에 PR을 첨부했다.
- clean test bootJar 1,127통과/실패0/오류0/skip0, fixture3개SHA256/diff검사통과. 이후 코드수정없음. PR 병합·배포·210브랜치/코드·Jira 상태변경 없음.
- WORKLOG/LC검토서 원문복구 및 백업은 사용자 별도승인 후 수행. 백업:/private/tmp/billing-pre-commit-backup.J6RctO. 다른 작업의 RevenueCat Google등록 문서와 두 공통기록의해당항목은 working tree에 보존·PR제외.
- 이번 결과기록은 별도문서커밋으로 같은PR에 반영한다. 다음:PR검토/병합은별도승인,이후최신develop기반210착수. 남은별도작업변경은임의stash/삭제/커밋하지않음.

## 2026-10-13 TMI-210 브랜치 이동 확인

- 브랜치:feat/TMI-210-purchase-ingestion-timeline-core. 사용자요청:브랜치이동확인. HEAD=d4f290e(PR13 merge),로컬develop/origin/develop과동일이며cfdbf3f가ancestor임을확인. develop...HEAD=0/0,210코어코드/커밋아직없음.
- 점검전남은변경:WORKLOG에기존기록이반복추가된115줄과미추적REVENUECAT-GOOGLE-REGISTRATION-2026-10-13.md. 이전작업의6줄잔여상태와다름을알리고보존,자동복구/삭제/커밋하지않음.
- 변경:CURRENT_STATE·WORKLOG확인기록만. Git상태/log/ancestor정적확인,코드미변경으로Gradle미실행. 원격fetch/PR조작/branch전환/구현/commit/push/Jira/배포없음. 다음:210착수가능,공통기록잔여는코드와분리해보존.

## 2026-10-13 TMI-210 구매 접수·기간권 코어 구현

- 브랜치: `feat/TMI-210-purchase-ingestion-timeline-core`. 목표: 사용자의 구현 요청에 따라 TMI-210 첫 slice 구현. Jira 본문은 읽기 확인했으며 변경하지 않았다. 기존 WORKLOG 반복 기록과 별도 RevenueCat Google 등록 문서는 사용자 요청대로 보존했다.
- 변경 파일: `domain/payment/purchase`의 PurchaseLookupKey/PurchaseObservation/PurchaseCore/PurchaseCoreSchema, PaymentRecoveryFence의 전용 writer·barrier 반환, purchase 단위/통합 테스트와 CleanupIntegrationTest 2회귀. PLAN-010·purchase-r1·TMI-210 runbook·CURRENT_STATE·본 append 기록. 실제 환경설정/무료 reader/Identity/LC 코드 변경 없음.
- 동작: 검증된 관측의 환경/app/고정 keyVersion·digest·원구매자 매핑 확인, inbox와 pending 증가 원자 접수, Purchase+기간권+timeline+원장+입력 완료/pending 감소 원자 처리. 1/3/7/14/28일·commit 순서 stacking·원구매시각 보존. 탈퇴/inactive/불일치/미매핑은 case 인계이며 pending 총수 보존. refund-first 및 미매핑 reversal은 후속 지급 차단. 원장/권리 자동 복원·계정 재생성 없음.
- 유지 계약: mandatory epoch/control, ALL_BLOCKED 차단·PURCHASE_BLOCKED 금융 사실만 허용, current ACTIVE ref만 최초 지급. 무료/인증/기존 schema 불변. 실제 provider I/O·public sync/reader·1초 worker·012 기존권리 환불/reflow·205 실금융 reader/coverage·정책revision 통합은 연결하지 않았다. 명시 생성형 코어만 추가해 실제 판매/자동 삭제 활성화 없음.
- 저장 규격: payment-purchase-core-r1 programmatic manifest에 6 collection/필수·null/BSON/명명 index/TTL 고정. startup 자동 DDL 없고 ALL_BLOCKED+writer 격리 아래 명시 설치만 제공. source 완료/인계 후만120일 purgeAt;119일 explicit handoff. case 종료/자동 인계·overdue 운영은 후속 gate이며 무기한 보존 승인 아님. 키 교체는 maintenance 이관이며 같은 version으로 실제키만 바꾸는 실수 자동 탐지 보장은 없음.
- 테스트: 최초 focused 31개 중 unknown commit 주입의 서버 종료 계열91이2초 예산 초과로1실패. 기존 저장소 패턴대로 오류1/UnknownTransactionCommitResult와112/TransientTransactionError를 분리한 뒤 targeted 통과. 최종 `./gradlew clean test bootJar` **1,168통과/실패0/오류0/skip0**(baseline 대비41추가), Mongo replica-set 실제 트랜잭션/동시성·순서·schema/rollback 검증. 실제 서버 재시작/운영 부하 또는 provider 검증 결과 아님.
- 위험/다음: 실제 Store/RevenueCat 검증 adapter·공통 worker/lease·case 해결/보존·public API 호환·012 revoke/환불·205 coverage·운영 부하/배포 gate 미완료. TMI-210 내부 코어와 PLAN-010 전체 완료 구분. Jira 상태/외부 메시지/실제 DB/schema·키/배포·commit/push 변경 없음.

## 2026-10-13 TMI-210 이후 작업 안내

- 브랜치: feat/TMI-210-purchase-ingestion-timeline-core. 사용자 다음 작업 질의에 CURRENT_STATE/구현 안내를 읽어 확인했다. 코어 검토·사용자 커밋/PR 정리 후 PLAN-010 2차 public sync/통합 reader의 exact DTO·호환·기본OFF 구현을 권장한다. 실제 provider/공통 worker·205 금융 조회 연결·011/012/운영 활성화는 별도다.
- 변경: CURRENT_STATE/WORKLOG 기록만. 코드/정책/외부 API/무료 조회 계약 유지, 새 Jira 생성/상태변경·외부 메시지·Git/배포 없음. 테스트 재실행 없음(직전1168통과와 구분). 다음: 2차 구현 범위·계획/이슈 정리 후 승인된 구현 진행.

## 2026-10-13 PLAN-010 2차 상세 계획 작성

- 브랜치: develop, HEAD726b5ca(PR14 merge/210포함), 시작 시 clean. 사용자 요청에 따라 sync·통합 entitlement 조회의 상세 계획을 작성했다. 원격 PR/Git 상태 변경은 하지 않았다.
- 변경: PLAN-010-phase-2-public-sync-and-entitlement-query.md 신규, 상위 PLAN010 링크, CURRENT_STATE·본 append. 요청/응답/오류/nullable·command/pending·HMAC hint·completion·동일snapshot·guard미준비 표시·flags·분할/회귀/배포조건을 기술 권장안으로 명시했다.
- 코드 대조: 무료 조회는 Spring snapshot/로컬token bucket, payment는 직접session/분산limiter다. ON에서는 동일 session 읽기와 ADR 분산60회 이관, OFF는 기존무료 유지. provider/worker 미연결이면 production sync ON거절, clienthint로지급금지. processingHint·신규409/command보존은 확정사실이 아닌 동결대상이다.
- 유지: 가격/기간/탈퇴/환불 정책·무료DTO 의미·인증경계·실제판매/삭제OFF. 실provider/worker·011/012/205연결은 후속. Jira생성/수정·타서버·코드/설정·commit/push/배포 없음. 문서 작업으로 Gradle 미실행, diff/상대링크 정적 검증. 예상 밖 변경 없음. 다음: 상세안 검토·기술후보동결→사용자승인Jira생성→OFF구현.

## 2026-10-13 PLAN-010 2차 사용자 상세 설명

- 브랜치: develop. 계획서를 재조회해 앱 결제 후 sync 접수/200·202,멱등key·토큰sub·pending/source역할,통합paid/무료/이용판정·동일snapshot·011미준비·기본OFF와 실제provider후속을 설명했다. 구매 수와 미처리 입력 수,APPLIED와현재 이용가능을 구분했다.
- 변경: CURRENT_STATE/WORKLOG 기록만, 기존계획/코드/정책/외부계약 유지. Jira/Git조작/타서버/배포/구현 없음. 설명 작업으로 테스트 미실행,문서 재조회·diff 검사. 기존 미커밋 계획변경 보존,예상 밖 변경 없음. 다음: 기술권장안 검토/동결 및 승인 후 Jira·구현.

## 2026-10-13 통합 응시가능 boolean 계획 반영·질의 설명

- 브랜치: develop. 사용자 요청에 따라 2차계획/purchase-r1에 examAvailability.canTakeExam 추가,예시/테스트기준 동기화. ALLOWED=true/BLOCKED=false/PENDING=null 권장파생으로 기존확인불가를0/false로숨기지않는정책 유지. 현재 새시험/승인재응시 요약이며 기존시험진행/기록열람과구분,실제reserve최종판정 유지.
- RevenueCat SDK의 앱 신호는 sync trigger이지 지급증거가 아님,사용자sub/requestkey/거래중복 식별 분리,command는 확인요청 접수장부,pending은 구매수아님,asOf는 조회기준시각임을 설명. 변경은 위두문서/CURRENT_STATE/WORKLOG만. 코드/API배포/Jira/타서버/Git조작 없음. 문서diff검사,문서작업으로테스트미실행. 기존변경보존/예상밖변경없음,다음은계획검토후구현.

## 2026-10-13 TMI-211 Jira 생성

- 브랜치: develop. 사용자 명시 생성 요청에 TMI-137/210과 상위/PLAN010 검색을 읽어 중복 확인 후 TMI-211 「[Billing PLAN-010 2차] 결제 sync·무료/유료 통합 조회 API 구현」 생성. 재조회로 parent137·해야 할 일·미할당 확인.
- 범위: sync202/200·본인인증/멱등/pending·통합조회/canTakeExam true/false/null·동일snapshot·요청제한·기본OFF/테스트. 실제provider/worker·011/012/205활성화 제외,미확인기술값동결·운영gate 유지. 계획이현재로컬문서임을본문명시.
- 변경: Jira 신규1건,2차계획키/상태·CURRENT_STATE/WORKLOG. 기존Jira상태/댓글/타서버/코드/설정/Git조작/배포없음. 원격생성재조회·문서diff검사,코드미변경으로Gradle미실행. 기존변경보존/예상밖변경없음. 다음:TMI211 상세규격정합화후승인된OFF구현.

## 2026-10-13 환불 confirm Session 정책 변경 승인

- 사용자 승인으로 exact confirm-first Session 기한 내 완료 허용, refund-first confirm/신규/replacement 차단을 확정했다. 별도 paid 채점승인API 제외·durable revoke/최소증거 유지. 기존reserve응답 출처추가 방향과 LC exact 동결/증거/guard/회귀 경계를 정책 변경 문서에 기록했다. ADR/PLAN/계약/AGENTS에 superseding 안내를 반영하고 과거 이력은 보존했다.
- 문서만 변경, 코드/LC저장소/외부회신/DB/배포/Jira/commit/push 없음. Gradle 미실행. 4번 전송 방식 새선택은 미확정.

## 2026-10-13 TMI-211 sync·통합 entitlement OFF 구현

- 브랜치: feat/TMI-211-payment-sync-entitlement-query. 사용자 구현 요청에 따라 기존 TMI-211/137과 2차계획을 대조해 Billing만 구현했다. Jira는 읽기 확인만 했고 상태·댓글은 변경하지 않았다.
- 변경 파일: payment/sync·query·PaymentSyncController·PaymentQueryConfiguration 신규, payment flags/limiter/schema verifier·public security/writer·무료 query service/controller/DTO·application.yml 연결. contract/MVC/Mongo/기동 gate 테스트47개 추가, 기존 전체 MVC security fixture에 새 decoder mock 추가. 2차계획/ADR004/purchase-r1·TMI211 runbook·CURRENT_STATE 및 본 append 동기화.
- 동작: untrusted hint HMAC/strict body·MEMBER scope/sub/ACTIVE account·멱등 command/pending 원자 접수202, 기존 본인 verified200; 타인409/탈퇴403/계정미발급409/DB503. source와 command 완료 분리·epoch/version/pending 유지, client 신호만으로 지급하지 않음. paid/free 같은 Spring snapshot·기간/stacking·unresolved PENDING·누락503, 기존 무료 DTO는 확장OFF 불변. 통합 canTakeExam은 판정기미준비시null. sync12/통합60 분산quota·429잔여초.
- 검증: 최종 ./gradlew clean test bootJar 성공, XML집계1,215통과/실패0/오류0/skip0(기존1,168 대비47추가). 실제 disposable Mongo replica-set에서 duplicate/동시접수·sourcecompletion·탈퇴·타인거래·epoch/overflow·schema drift·5기간/경계·반복read·동시commit snapshot·quota 검증. 최초 전체실행의 기존SecurityConfigTest6건은 새decoder fixture누락으로 보완후clean재실행 성공. git diff --check 통과. 실제provider/Store/외부HTTP·staging E2E 미실행.
- 유지/위험: 무료원장/reserve·구매/환불제품정책·Identity/LC 코드 불변. flags기본OFF, 실제provider/worker가 없어 sync ON은 모든runtime profile에서기동거절; MVC에서만mock과기동후flag로검증. command TTL/purge/운영120일정리 미구현·실접수차단, admission/환불/205금융coverage 별도. standalone schema 설치는격리/ALL_BLOCKED 명시호출만, 운영DB수정없음.
- 동시 변경: 작업 중 AGENTS/ADR/PLAN011~013 등 별도 환불 confirm Session 정책문서 추가를 관측해 보존했다. 이번코드에서 그정책/환불동작을 구현한 것으로 보고하지 않는다. 기존미커밋계획보존,WORKLOG과거기록미수정,commit/push/배포/외부메시지없음. 다음: 변경검토→실제provider/SDK거래매핑·공통worker/운영보존→011/012 및실환경 gate.

## 2026-10-13 기존 이용증거 재사용 검토

- 사용자 확인 요청으로 양측 source 읽기. LC v1 evidence/failureCode 존재·Billing 검증과 상세저장 부재·무료전용 consumer/purchase귀속 보완 필요를 확인했다. PLAN011 검토 기록 추가, 새버전/증거이벤트보다 기존wire+Billing내부저장 권장.
- 코드/타저장소/실환경/계약확정/배포/Jira/commit/push 변경 없음. 정적 문서검사만, Gradle미실행.

## 2026-10-13 TMI-211 병합 후 다음 작업 안내

- 사용자 병합 완료 후 로컬 HEAD ea30a62(PR15 merge)와80526e9 구현 포함·점검전 clean 확인. 원격 fetch/PR 상태 변경은 하지 않았다.
- PLAN010과 CURRENT_STATE를 대조해 실제 RevenueCat 인증/SDK hint→거래 식별 매핑 선행 확인 및3차 provider adapter·webhook·공통worker/reconciliation 계획을 권장. HMAC 지원/서명시간은 실증 전 공식기능으로 가정하지 않음. 011/012와실판매는후속.
- 변경: CURRENT_STATE 및 본 append만. 코드/정책/타서버/Jira/외부설정/Git/배포불변, 분석작업이라Gradle미실행. 다음: provider공식규격 확인 후3차계획/필요한계약보완 정리.

## 2026-10-13 PLAN010 3차 RevenueCat 공식 조사·계획 작성

- 브랜치 develop, HEAD ea30a62. 사용자 요청에 공식 webhooks/v2 purchase/customer resources/event field·SDK source/reference와 로컬210/211·ADR/PLAN을 대조했다. 공식문서에 HMAC timestamp.raw-body 지원 및 재전송 재서명이 명시돼 기존방향 유지 가능; 실제프로젝트 HMAC/권한/SDK거래 fixture 검증과 구분했다.
- 변경: PLAN-010-phase-3-revenuecat-worker-reconciliation 신규, 상위PLAN링크·CURRENT_STATE·본append. adapter/ingress/worker/reconciliation 단계, source key·금액불명보완·hash-only sync 계정목록대조·strict schema이관·lease/epoch/pending·공유quota·보존/테스트/기본OFFgate를 권장안으로 작성. 구매/환불/무료정책과 기존코드계약은 수정하지 않았다.
- 위험: Android orderId null/앱SDK버전미확인, 원구매자/alias·실데이터타입·부분환불미검증. USD revenue를 STORE_PAID로 오인하지 않음,API 목록완료를205미구매증거로 사용하지 않음. exact adapter/schema/운영보존과 실제환경 검증은 후속.
- 검증: 공식페이지열람·코드정적대조·diff/로컬링크 검사. 문서작업이라Gradle미실행(기존1215통과를이번검증으로재사용하지않음). 실제provider/Secret/dashboard/Store·타서버·Jira·commit/push/배포없음. 기존기록변경보존/이번범위밖수정없음. 다음: 기술권장안 검토→exact선행규격과Jira 승인→OFF분할구현.

## 2026-10-13 PLAN010 3차 설명

- develop. 사용자 설명 요청으로3차계획 전체를 재조회했다. 기존210원장/211접수·조회와 다음adapter/webhook/worker/reconciliation의 연결,계정별hash대조·중복신호와한번지급·금액불명보완·DBpoll/외부quota·검토대기·실거래gate를 설명했다.
- CURRENT_STATE와본append만 변경,기존문서/정책/코드/외부계약 유지. 구현/Jira/배포/타서버/Git조작없음. 설명작업이라Gradle미실행,문서조회/diff검사. 기존변경보존/추가예상밖변경없음. 다음: 권장안검토후exact규격/Jira승인.

## 2026-10-13 LC 유료 시험 인계 명세 준비

- 사용자「어 준비해줘」로 reserve/status 출처·confirm-first 환불예외·revoke 후보·오류/auth/retry·기존v1증거 재사용·분담/배포/PAID01~14 공동 테스트를 LC-BILLING-PAID-EXAM-HANDOFF-R1.md에 작성했다. 신규필드/route/auth/오류는 양측미수락 후보이며 기존정책과구분했다. PLAN011/012에 참조 추가.
- source읽기/문서만, LC코드/문서·외부메시지·Jira/DB/배포/활성화/commit/push 없음. 기존별도변경보존·Gradle미실행.

## 2026-10-13 PLAN010 3차 예외 처리 설명

- develop. 사용자6번질문에 금액불명후보완은검증된구매와분리/추가원장만·재지급금지,일시조회실패는기존권리취소없이신규PENDING·backoff,24시간재시도예산소진권장안은운영검토인계이며자동환불/삭제가아님을 설명했다. 인증실패는즉시차단/알림으로구분.
- 변경CURRENT_STATE/본append만,정책/기술안신규확정·구현·Jira·외부연동없음. 기존동시LC문서보존. 설명작업으로테스트미실행,문서/diff정적확인. 다음:권장안검토/세부규격동결.

## 2026-10-13 TMI-212 생성

- develop. 사용자Jira생성승인에 TMI137/하위·RevenueCat기존이슈를조회해중복확인후 TMI212 「[Billing PLAN-010 3차] RevenueCat 검증·webhook·공통 worker·누락 복구」 생성. A~D순서·금액만불명인검증구매지급/추후보완·중복방지·schema/보존·OFF·테스트/활성화경계를본문기록.
- 변경: 신규Jira1건·3차계획키/상태·CURRENT_STATE/본append. 기존이슈수정/상태전환/댓글·코드/외부설정/타서버/Git/배포없음. 기존LC별도문서변경보존. 생성후이슈재조회·diff검사,문서/이슈작업으로Gradle미실행. 다음:TMI212 exact선행규격정리후단계별OFF구현.

## 2026-10-13 LC R1 검토 5항목 보완

- 현행LC strictdecoder/operation·error category/public saga 및gradingdeadline을읽기확인했다. R2에source operation보존/legacy조건·제출기한선택대기·오류/보상·최대16KiB/역방향독립trust·충돌우선/보존age/lifecycle후보를작성했다. 사용자제출기간질문은미응답상태이며값미확정.
- 코드/LC문서/외부회신/Jira/DB/배포/commit/push 없음. 문서검사만·Gradle미실행.

### 사용자 확인: 새 제출 제한 없음

- 사용자「제한이 돼? 안하는 거 아니었어?」에 따라 미승인30/60분/24h후보철회. 작성자의 기존마감가정 오류를설명하고 R2§2를새timeout없음으로보완했다. 현행grading제한·terminal/삭제/탈퇴경계유지,장기간OPEN guard/증거보존은공동확인. 이전질문/기록은역사로보존. 코드/배포변경없음.

## 2026-10-13 LC 인계 잔여 항목 재검토

- 사용자「다른 것도 확인해줘」로 source/confirm복구·오류/보상·역방향보안·순서/보존/lifecycle를정적검토. status/confirm command의존·RESERVED보상경합·higher예외null제거충돌 확인, R2보완/검토기록 작성. 120일eventage신규제약 기본안철회·inbox와protected예외보존분리·source예외고정·새confirmedAt필드불필요가능성 명시. 장기OPEN다음시험/guard는LC공동확인 필요.
- concurrent PurchaseCore/Schema/LookupKey 및010문서 개발변경보존. 코드/LC수정/외부회신/DB/Jira/배포/commit/push 없음. Gradle미실행.

## 2026-10-13 TMI-212 선행 A adapter·금액 보완 구현

- 브랜치 feat/TMI-212-revenuecat-worker-reconciliation. 구현전TMI212본문/완료조건과현재210/211코어를확인했다. 전체3차중선행A부터진행: B수신/Cworker/D대사·reference영속화/운영보존은아직남아있으며전체완료로보고하지않는다.
- 변경: payment/provider의ReadClient/PurchaseDecoder/HttpTransport/Failure/ReferenceCipher,PurchaseLookupKey API namespace,PurchaseCore 금액보완,PurchaseCoreSchema r2명시이관 및관련테스트. adapter-r1/구현안내·ADR004·3차계획·CURRENT_STATE/본append동기화.
- 동작: RC고정host/서버project·계정목록/단건읽기·strictJSON·owner/original/상품/store/env/quantity/time/status·pagination/HTTP오류/RetryAfter검증. API USD revenue는money로사용하지않음. source API key와webhook key분리·Store거래HMAC불변. metadata용AES256GCMhelper추가(저장/키주입미연결).
- 금액: 검증구매null금액지급유지,null→known은MONEY_CONFIRMED원장+Purchase projection/pending같은Tx,retention확장·기간/timeline불변. known→null유지,known충돌case. concurrent/unknowncommit/transient/blockedgrant순서검증. r2는ledgerenum만명시maintenance로변경,운영DDL자동없음·기존r1정확검증/ALL_BLOCKED/격리필수.
- 검증: 집중테스트성공후 ./gradlew clean test bootJar 성공,XML1,268통과/실패0/오류0/skip0(53추가). fake provider transport/JSON과Mongo replica-set·명시이관회귀. 중간테스트Java선언/람다컴파일오류수정후전체재실행. 실제RC/Store·정상HTTPS transport E2E·실키/대시보드미실행. diff/링크정적검사.
- 유지/다음: runtimebean/자동외부호출/새flagON없음,기존syncON기동차단유지. 승인없는Jira상태/댓글/commit/push/배포/타서버변경없음. 동시LC R2/환불정책/PLAN011·012문서변경은별도작업으로보존,이번코드의정책변경아님. 다음은B의provider수신·durableunknown/review와C/D저장규격·worker/보존,이후실환경gate. TMI212는열린상태유지.

## 2026-10-13 기존 새 시험 흐름의 그룹 전환 승인

- 사용자 설명(기존group아래Session재시작)과「어그정도면될거같다」승인으로 별도포기버튼없이 동일source replacement/환불source차단/다른권리INITIAL 새group전환 방향을 R2§2/8·PLAN011·결정서에 반영. 원source/이력보존·전환실패시이전Session만선종료금지·cross-service durable/CAS/status-first 설계경계 명시.
- 문서만, 코드/LC저장소/DB/Jira/외부전송/배포/commit/push 없음. 기존010개발변경보존·Gradle미실행.

## 2026-10-13 무료 도중 구매와 그룹 source 보존

- 사용자가 무료그룹손실사례를지적해 이전전환표현의범위를교정했다. 기존유효TRIAL replacement보존·paid-first는신규INITIAL·환불/만료재응시불가시만다른권리group전환, Claim/consumption/paid기간불변을R2§9/PLAN011/결정서에명시. 새기능/무료권복구승인아님. 코드/LC/배포변경없음·Gradle미실행.


## 2026-10-13 공통 무료 모의고사권과 유료 전환 복원 승인

- 사용자 「어 그렇게 하는 게 좋아보인다」에 따라 [정책](../contracts/FREE-EXAM-PASS-AND-PAID-SWITCH-RESTORATION-2026-10-13.md) 작성, R2 §9/PLAN011/013/ADR004/AGENTS/결정서/통합·요약 계약 동기화. 구매만으로 미지급·paid confirm 시 미완료 최초 trial 종료+별도복원1개·root lineage당1회·원Claim/consumption보존. 공통source wire 재검토·reader/retention/owner/경합 공동동결 후속, 초대보상은 확장경계만.
- 문서만 변경. 기존 동시 PLAN010 개발 변경 보존. 코드/LC/DB/Jira/외부전송/배포/commit/push 없음. Gradle 미실행; diff 및 로컬 링크 검사로 검증.

## 2026-10-13 TMI-212 B webhook 수신 구현

- 브랜치 feat/TMI-212-revenuecat-worker-reconciliation. 사용자「어 해줘」로 A후속B착수. Jira212본문/완료조건을읽었으며상태/댓글은변경하지않았다. 공식Webhook페이지에서Authorization/HMAC·deliverytimestamp재서명규격을재확인했고dashboard/실키는조회하지않았다.
- 변경: provider의WebhookAuthentication/Decoder/Filter/Properties,WebhookReceipt,RevenueCatWebhookConfiguration,PublicIngressFilter exact분리,application.yml기본OFF설정,PurchaseCore/Schema r3및PaymentSyncService key검사. 관련단위/실Springchain/Mongo테스트와webhook-r1/runbook·ADR004·3차계획·CURRENT_STATE/본append.
- 동작: 환경별host/localport/정확route·중복Authorization/서명·HMAC rawbody/±300초·UTF-8/256KiB/strictJSON,fullbinding/수량/상품검증. 정상관측은기존inbox접수만하며apply/외부호출없음. 금액만없으면null로접수,TEST/unknown은IGNORED,환불/소유등불명은case로보존. 같은event의known의미digest충돌409,commit완료후200/저장불명503. ignored는pending없음,review는case에pending보존·TTL제거후에도중복수렴.
- schema: r3는source/case nullable관측·IGNORED·receptionDigest/receptionLookupKeyVersion추가. r1→r2→r3명시maintenance,writer격리/ALL_BLOCKED·전체preflight·부분이관재개,기존index/금융schema불변. 자동DDL/구writerrollback없음. sync는null관측을구매관측과구분하고두형태의lookup버전을검증한다.
- 검증: 집중테스트중app_id누락의Map.get(null)예외를수정. unknowncommit회귀는stepdown91이아닌기존계약용error1/label로정정후동시중복/같은Txcommit재시도검증. 집중95개성공후최종 ./gradlew clean test bootJar **1,324통과/실패0/오류0/skip0**. 처음clean호출의sandbox Gradle lock접근거절은승인된권한으로재실행해성공. 실제로컬replica-set+fakeprovider/MVC이며실RC/Store/ALB/TLS검증아님. diff/문서링크검사.
- 유지/위험: 기존user/internal JWT·무료/기간권/환불정책불변,수신기본OFF·syncON차단유지. owner미해결globalcase는기존paid reader의UNKNOWN을유발하므로운영해결절차없이수신ON하지않는다. quantity/family실fixture,reference암호화영속화·Cworker/공유quota/lease·D대사/알림/검토/보존은남았다. TMI212전체완료아님. 다음C상세worker/schema와자동처리연결. 실제판매/DB이관/배포/타서버변경/commit/push/Jira쓰기없음.
- 예상밖코드변경없음. 작업중추가된AGENTS·무료권복원정책/LC R2/PLAN011~013/결정서·통합문서는다른작업변경으로보존했고본구현범위에포함하지않았다.


## 2026-10-13 무료권 사용·귀속·전환 복구 추가 확정

- 사용자 선택과 「1번은 그렇게 하는 게 좋아보여 좋아 2번 그렇게 하자」를 반영: 만료 가까운 순/지급순·무기한 후순위, 복원권 현재 무기한, EXAM_PASS/SUBSCRIPTION 및 내부 지급 출처. 보상권 계정귀속/탈퇴재가입 자동복구없음/정상merge 미사용이전·기존 trial 유지, Billing confirm/원그룹차단/복원 원자확정과 LC status-first 후속복구 정책 확정. exact DTO/fixture/이관·held/merge경합·보존 설계는 후속.
- 정책 정본/R2/PLAN011·013/결정서/현재상태 문서만 수정. 동시 제품 코드 보존·타저장소/DB/Jira/외부전송/배포/Git변경 없음. Gradle 미실행, diff/정책 링크 검사 수행.


## 2026-10-13 무료권 전환 단순화 승인

- 사용자가 선택한 세 가지를 정책 정본/R2/PLAN011·013/ADR·AGENTS/통합·결정서에 반영: Billing confirm/복원/원group재응시차단 같은Tx, LC 순차종료와 기존접수 경합완료 허용; 최초trial consumption ID당 복원1회; 현재무기한·지급순, 만료기능후속. 직전 MEMBER전용 정정에 따라 보상권 Guest merge확장제외, 기존trial merge유지. 중복지급방지/status-first/원장보존 유지.
- 코드·타저장소·DB·Jira·외부전송·배포·commit/push 변경 없음. 동시 개발 변경 보존. 문서 diff/정책링크 검사, Gradle 미실행.

## 2026-10-13 TMI-212 C1 source worker·sync 완료 복구 구현

- 브랜치 feat/TMI-212-revenuecat-worker-reconciliation. 사용자「어 해줘」후Jira212완료조건을재조회했다. C를source금융처리/동일Tx잠금경계C1→외부조회/공유quota/runtime C2로나눠진행,전체C/212완료로보고하지않는다.
- 변경: PaymentSourceWork/PaymentSourceWorker,PurchaseCore의최초ticket/같은Tx guard apply·worker실패case인계,PaymentSyncService guarded bind/complete,PurchaseCoreSchema r4명시이관,PurchaseCoreIntegrationTest/PaymentSyncIntegrationTest. worker-r1/runbook·3차계획·ADR004·CURRENT_STATE/본append.
- 동작: inbox별30초lease/owner·leaseVersion·generation·최초epoch와guard쓰기검증. source원장반영뒤중단시terminal행work로sync완료재개. 같은계정/lifecycle/store/app/거래hash/PENDING만최대100개bind/complete,다른거래/다른sourcebinding은건드리지않음. tick최대100source/동일tick재claim금지·신규/재개교대,순수sync hint/ignored는지급안함. blocked는backoff,24시간후sourcecase인계·pending보존,terminalsync실패는보류/failure코드.
- schema: optionalwork object와nonunique dueindex,기존row는claim초기화·금융schema/외부wire불변. 명시r1→r2→r3→r4,writer격리/ALL_BLOCKED후DDL중간상태재개·drift거절,자동DDL/운영이관없음. case에는work복제안함. 구writerrollback불가.
- 검증: 집중Mongo테스트성공후 ./gradlew clean test bootJar **1,338통과/실패0/오류0/skip0**(14추가),diff검사/문서링크검사. lease만료·epoch교체·ALL_BLOCKED·동시worker·apply후중단·bind후실패·101command분할·withdraw/review pending·24시간예산·schema재개/불일치. 실제로컬replica-set이며실RC/Store/ALB/운영키호출없음.
- 남음/운영경계: 명시생성형tick만구현,자동scheduler/실외부조회/공유quota·새flagON없음. C2는계정조회합치기·페이지/Retry-After/AUTH·sync독립lease·실런타임조건, D는reference영속화/정기대사·알림/재개/120일보존인계. work없는legacy terminal의미완료sync는C2회수필요. 기존syncON기동차단유지. Jira쓰기/commit/push/배포/타서버변경없음.
- 예상밖코드변경없음. 동시수정된무료권복원/사용순서/LC/PLAN011·013/AGENTS·계약문서는다른작업으로보존했으며이번worker에서구현하지않았다.


## 2026-10-13 source 일회성 이관 가능성 검토

- 사용자 요청으로 Billing/LC 코드 읽기 검토. [결과](../plans/PLAN-011-SOURCE-MIGRATION-REVIEW-2026-10-13.md): trial allocation/group/ledger 근거 기반 EXAM_PASS 이관 후 필수값 전환 가능. 미연결 LC legacy/발신불명 PREPARED/terminal replay 범위 점검 필요, reserve 성공부터 필수. 실제 DB/배포 이력 미확인·운영이관 미실행. command TTL 복구는 별도 보완.
- 문서만 추가, 타저장소 읽기 전용·코드/DB/Jira/배포/commit/push 없음. 문서 검사, Gradle 미실행.


## 2026-10-13 production source 읽기 전용 inventory

- 사용자 prod 확인 후 LC 원격 Mongo 조회: Session1493 모두source/예약/group누락, operation collection없음. COMPLETED1012/ABANDONED333/IN_PROGRESS136/상태누락12(active10). 식별자·credential·개별문서 없이 건수만 기록. 동일 연결의 접근가능 DB 중 billing명칭 없음, 별도 설정 요청. Billing부재로 단정하지 않음.
- source-inventory 읽기스크립트 추가·문법검사 및 prod LC 실행, DB쓰기/이관/LC파일변경/배포/Git조작 없음. 결과문서와 원검토/current동기화, diff검사. Gradle미실행(제품코드변경없음). 단순 무료source일괄채우기 부적합, 활성legacy 처리계약 후속.


## 2026-10-13 Billing production DB 미구축 확인

- 사용자 「아직 안만들었어」로 Billing prod DB 미구축 확인. 접속설정 대기 해소, 기존 Billing source backfill 불필요로 점검문서/검토/current 갱신. 신규 schema/writer는 reserve 성공 이후 source 필수 설계, LC 기존 활성146건 처리와 history 경계는 후속. DB 직접조회에 의한 0건 판정이 아님. DB생성/이관/코드/배포변경 없음. 문서 diff검사.


## 2026-10-13 LC 기존 시험 보존 승인

- 사용자 「좋아」로 기존 시험 기존규칙/기록 보존, 전환후 새시험·새재응시 Billing/source필수 정책 확정. 전환전 대상증거 기반 legacy경계, source없음 자동허용금지·신규소비/복원연결금지. 활성수는전환전재점검, 상태누락임의허용없음. prod점검/PLAN011·013/R2/결정서/current 문서동기화. 코드/DB/타저장소/배포변경없음·diff검사.

## 2026-10-13 TMI-212 C2a 외부 조회 접수·공용 프로젝트 budget 기반

- 브랜치 `feat/TMI-212-revenuecat-worker-reconciliation`, 사용자 「어 해줘」 후 Jira TMI-212를 읽고 C2를 진행했다. 계정 scan 연결 전에 최초 dispatch 권한으로 접수하는 경계와 서버/환경 합산 budget을 선행 구현했다. C2 전체 완료가 아니다.
- 변경 파일: PurchaseCore guarded receive, RevenueCatBudgetSchema/RevenueCatProjectBudget/RevenueCatBudgetedTransport, RevenueCatBudgetIntegrationTest/PaymentSyncIntegrationTest, provider-dispatch-r1·runbook/worker-r1/3차 PLAN/ADR004/CURRENT_STATE/본 append.
- 동작: 외부 관측 receive의 동일Tx epoch+lease guard(중복 접수도 검사), 실패 시 pending/접수/guard쓰기 rollback. 명시 주입 공용 Mongo의 RC project `_id`로 최근60초240회·동시 permit4 공유,30초만료·5초HTTP전제·응답전후permit검사·실패도rate소비/slot만반환. 401/403 영속 project 차단, 자동재개 없음, version CAS/전체호출자격리/활성slot없음의 명시 maintenance 재개. 실제 공유 DB/키를 만들거나 연결하지 않았다.
- schema: 공용 revenuecat_read_budgets r1 추가, strict validator·기본 `_id_` 외 index 없음·TTL 없음·개인/거래/credential 없음. 명시 maintenance DDL/verify-only constructor, drift 자동수정 없음. payment core/sync/cursor schema와 외부DTO/금융원장 불변. 운영 의존성/라이브러리 신규 추가 없음.
- 검증: 집중 테스트 후 `./gradlew clean test bootJar` **1,356통과/실패0/오류0/skip0**, C1대비18추가. 두 인스턴스/환경·동시8요청·240회/60초·30초만료·늦은응답·다른요청AUTH·DB장애·차단재개·시계역행/nanoseconds→BSON·schema drift, 접수guard rollback/staleepoch/중복/ALL_BLOCKED 검사. diff/문서link검사. 테스트는 로컬 replica-set과 fake HTTP만 사용, 실RC/Store/Atlas호출 없음.
- 잔여/위험: C2 계정별 durable lease/합치기·pagination checkpoint·sync completion/legacy terminal·Retry-After/backoff/24h review·실공유DB설정/runtime은 미완료. 공용DB를환경별로분리하면합산제한이안되므로배포전topology검증필수. 프로세스장기정지/네트워크취소지연시물리적HTTP동시성절대보장아님(유효permit기준). D정기대사/reference영속화·보존/알림 및 Store E2E후속. 모든runtime활성화/판매/205삭제OFF유지.
- 예상밖 코드 변경 없음. 기존 A/B/C1 변경과 동시 LC legacy/source·무료권 정책/AGENTS 문서 변경은 보존했다. Jira 수정/댓글·타서버 코드·운영DB·배포·commit/push 없음.


## 2026-10-13 LC operation 마무리 방식 승인

- 사용자 권장표 「좋아 이렇게 해줘」 반영. R2 §10에 기존operation의 confirm증거/후속종료 미완료 상태·worker재시도·TTL미삭제·guard, 새Session사용가능/이전제출차단/SUCCEEDED 로컬Tx 후성공응답 확정. 접수Job만완료·부분접수 미완료·legacy ID목록 방식 우선검토. R2 §1/6의 TRIALfallback 제거, §8 최초trial→paid 무중첩예외 명시. 무료권정책/legacy점검/PLAN011·013/결정서/current 동기화.
- exact전환wire/enum/공개중간오류·역방향인증/보존은 공동동결후속. 코드/LC저장소/DB/외부전송/Jira/배포/commit/push 변경없음. 문서 diff/로컬링크검사, Gradle미실행.


## 2026-10-13 LC §10.2 파생 Job 문구 보완

- 사용자 전달 LC 재검토 §8 확인. 새Job차단은 종료후 사용자 신규제출/새녹음/재응시 유래에 한정, 원 durable 접수에서 파생되는 Summary 최초생성과 시스템재시도는 생성시각과 무관하게 기존 멱등/lifecycle하에 허용. 원접수근거 필수·신규권한부여없음. R2/무료권정책/fixture설명/current 동기화.
- 설계방향 이견없음 회신을 공동wire/테스트통과/구현·배포승인으로 확대하지 않음. 코드/DB/LC저장소/외부전송 변경없음. diff검사·Gradle미실행.


## 2026-10-13 LC 공동 기술 계약 R1 초안 작성

- 사용자 「어 만들어줘」에 따라 reserve/status 전환wire·INITIAL/REPLACEMENT문제선택·공개오류/영속거절·TTL후복구·역방향JWT/이벤트/digest·보존 및 fixture제작목록을 구체화. 기존3필드요청에restartContext 추가 영향 명시, 정책과기술후보구분. terminal tombstone/실배포값/삭제continuation 상세는 명시적잔여.
- Billing/LC DTO코드 읽기확인, 문서/R2/PLAN011·012/current동기화. 코드/DB/타저장소/외부전송/배포/Git조작 없음. fixture실제제작/hash/테스트통과 주장없음. 문서diff/링크검사, Gradle미실행.

## 2026-10-13 TMI-212 C2b 계정별 조회·페이지 복구·sync 연결·OFF runtime

- 브랜치 feat/TMI-212-revenuecat-worker-reconciliation. 사용자 「어 해줘」 후 Jira212 완료조건을 읽고 C2a에 이어 계정별 실제 read adapter 연결을 구현했다. TMI212 전체/실판매 완료가 아니다.
- 파일: PaymentAccountLookupSchema/Work/Worker, PaymentProviderWorkerProperties/Runtime/Configuration 신규. PaymentSyncService 검토 인계·새 요청 marker 반영, CleanupPurger lookup잠금 확인, application.yml 기본OFF 설정, PublicSecurityConfig 차단 이유 정합화. PaymentSyncIntegrationTest/CleanupIntegrationTest/PaymentProviderWorkerConfigurationTest/PaymentPhaseTwoConfigurationTest. account-lookup-r1/runbook·PLAN010 3차/worker-r1/provider-dispatch-r1/ADR004/current/본append.
- 동작: PENDING sync부터 계정별 묶기→최초epoch/30초lease→기존 source/legacy terminal 우선 완료→Tx밖 RC 한페이지→guarded receive/apply/bind/complete→암호화 cursor 전진. 페이지전중단/재조회 멱등수렴, 잘못된owner/탈퇴 무지급. 404/빈목록은pending보존,Retry-After와5초~1h jitter중늦은시각,command24h예산 검토. AUTH/불명은marker먼저저장후100개씩인계·중단중crash에도HTTP자동복귀없음·새미확인sync도같은검토상태. 정상drain시IDLE/시도초기화해과거backoff가새구매를지연시키지않음.
- schema: 기존 cursor에 optional lookup·암호화page envelope/명시strict-validator이관,기존2index보존·sync-command-r1필드불변. writer격리/ALL_BLOCKED/기존행적합성검사·drift거절. 205는lookup.owner가남으면삭제보류,자동삭제OFF. 계정generation은1생성/검증,승인재개도구는D후속으로명시.
- runtime: provider.worker.enabled=false/poll1000ms/batch100,실boundedHTTPS+project공유budget,독립executor ApplicationReady후실행/종료정리·privacy-safe phase metric. ON시foundation/recovery/core/sync/cursor/sharedschema·catalog매핑·키검사. budget은명시별도DB/동일MongoClient cluster,모든환경동일cluster+DB필수·실topology미검증. fake/ready=true우회없음. 공개sync ON차단은D보존/검토/rollout gate전유지.
- 테스트: 집중회귀후 최종 `./gradlew clean test bootJar` **1,380통과/실패0/오류0/skip0**,직전1,356대비24추가. 초기fixture 공유MongoClient조기종료와차단문구 assertion실패를수정했고backoff초기화보완뒤전체재실행했다. 계정격리/합치기/실decoder·budget·runtime배선무발신,암호화/페이지crash·epoch/lease/두worker/legacy terminal,101건AUTH·review·예산,빈목록404/429·잘못된owner/탈퇴·cipher/page상한·schema/삭제잠금/새구매재시도 검증. diff/링크검사 통과. 실제로컬replica-set/fakeHTTP이며실RC/Store/Atlas호출없음.
- 위험/다음: D정기대사·구매별암호화reference영속화·terminal/cursor보존/cleanup·검토/AUTH 계정command재개·알림,공유DBtopology/대량적체explain·부하/StoreE2E 남음. 반환batch100은총DB스캔100상한아님. 실제schema이관/키설정/배포/판매/205삭제활성화 없음.
- 외부API/Identity·LC/userJWT/지급정책·금융원장계약보존. 예상밖코드변경없음,동시LC공동계약/무료권정책등기존문서변경보존. 타서버파일·Jira쓰기/상태·commit/push변경없음.

## 2026-10-14 TMI-212 D1 접수 보존 인계·완료 sync 정리 기반

- 브랜치 feat/TMI-212-revenuecat-worker-reconciliation. 사용자 구현 요청 후 Jira212 완료조건 확인, 기존 C2b 작업에 이어 D의 보존·정리 vertical slice를 구현했다. D 전체와 운영 활성화 완료를 뜻하지 않는다.
- 변경 파일: PaymentRetentionWorker/PaymentHourlyTask 신규, PurchaseCore.handoffAged 최초ticket overload, PaymentProviderWorkerProperties/Configuration/Runtime·application.yml 연결. PaymentSyncIntegrationTest/PaymentProviderWorkerConfigurationTest, retention-r1 규격/3차PLAN/CURRENT_STATE/본append.
- 동작: 시간당 단계별 최대100건 RECEIVED119일이상 기존 case 인계(Transaction으로증거/pending책임보존), 완료sync APPLIED/REJECTED·counted=false·updatedAt120일경과 후보 점검. 실제삭제는 terminal-sync-delete-enabled=false 기본값이며 ON에서도 최초epoch/OPEN barrier/선택version/현재state/count/시각조건을 같은Tx 재검사한다. 금융원장·구매·기간권·case·계정/cursor·미해결sync는삭제하지않고 pending증감도없다. 실패task는다음시간재시도,다른workerphase실패와격리,개수metric만기록.
- 계약: 기존 core-r4/sync-r1 schema/index/외부DTO·인증·지급정책 유지. runtime DDL/새TTL/외부서비스/의존성추가없음. provider inbox기존TTL를끄는기능이아니며 terminal source→미완료sync의장기보호를해결했다고주장하지않는다. 공개sync ON차단유지.
- 테스트: 집중회귀 후 `./gradlew clean test bootJar` **1,388통과/실패0/오류0/skip0**,직전1,380대비8추가. 로컬replica-set에서119/120일exact경계·dry-run·미해결보존·batch·updatedAt/환경격리·old epoch/ALL_BLOCKED/PURCHASE_BLOCKED·두worker경합 검증. 시간당실패cadence/기본OFF/runtime phase회귀 통과. diff정적검사·신규규격/PLAN로컬링크검사통과. 실제RC/Store/Atlas호출없음.
- 남은 D: 정기계정대사/공정순환·구매별암호화reference영속화·terminal inbox TTL경계의미완료sync근거보호·검토/AUTH계정command명시재개·운영알림/오래된cursor정리. 이후 전체통합·공유DBtopology·대량적체explain/부하·Store E2E가 필요하다. batch는반환상한이며총DB스캔상한이아니다. 후보metric은점검관측누계이지유일계정/거래수아니다.
- 배포전: 실제삭제는별도승인·dry-run/복원/경합검증후활성화. 이번에는운영데이터삭제/외부알림/판매/205활성화없음. 예상밖코드변경없고기존A~C2b·동시LC/무료권문서변경보존. 타저장소·Jira쓰기·commit/push변경없음.


## 2026-10-14 LC wire draft-1 검토 반영

- LC 코드읽기로 삭제continuation 일반reserve/confirm전transfer와 기존EXAM_CREATION_PROCESSING/BILLING_TEMPORARILY_UNAVAILABLE RetryAfter1 확인. draft-2 §9에replay우선·삭제source매핑/후속transfer·누락context/phonepaid충돌·타입조합/OTHER제외·거절시각/상태·기존공개pending유지·canonical/digest/역순예외·32fixture명세 추가. 성공/실패receipt TTL 및 lifecycle삭제는 활성화gate로명시, 영구보존승인아님.
- 문서/R2/PLAN011/current동기화, 코드/DB/실서비스/타저장소/배포/외부전송변경없음. fixture bytes/hash생성·검증 주장없음. diff/링크검사·Gradle미실행.


## 2026-10-14 LC wire draft-2 재검토 문구 반영

- 사용자 첨부 마지막 재검토를 기준으로 주요보완 이견없음 접수. draft-3으로 갱신하고 command operationId header는 reserve/confirm/cancel만, status는기존userId/operationId body·Idempotency-Key불필요·조회무command/ledger 명시. §8오류표현을 마무리guard기존409/일시장애기존503/신규두409로정리.
- 잔여scope/보존/삭제/배포gate 유지, fixture생성/hash/독립검증·공동최종동결 주장없음. 코드/DB/LC저장소/외부전송/배포/테스트변경없음. diff/문구검사.


## 2026-10-14 draft-3 전달본 분리

- LC에서 전달첨부가draft-2와동일하다는 회신에 로컬정본 재확인: draft-3 §1status body/§8오류구분 반영됨. 첨부실물은이번메시지에없어전달오류원인단정안함. revision파일명 DRAFT-3 전달snapshot을동일bytes로작성. SHA256 ca812773863877ed7a067c69f1eabc4c0ad15a1fa6abbf64f71f16a103799603. 수정문구assert/동일bytes/diff검사. 외부전송/fixture제작/제품코드/DB변경없음.

## 2026-10-14 TMI-212 D2 정기조회·원장완료·선택재개·참조저장·운영요약

- 브랜치 feat/TMI-212-revenuecat-worker-reconciliation. 사용자 「어 해줘」 후 Jira212를 읽고 D1 이후 구현. D전체/운영판매 완료를 뜻하지 않는다.
- 변경 파일: PaymentAccountLookupSchema/Work/Worker, PaymentSyncSchema/Service, PaymentLookupResumer 신규, PurchaseReferenceSchema/Store 신규, RevenueCatProjectBudget auth조회, PaymentProviderAlerts 신규, ProviderWorkerProperties/Configuration와application.yml. PaymentSyncIntegrationTest/ProviderWorkerConfigurationTest, review-resume/reconciliation/purchase-reference exact·운영안내·C2b/D1문서우선규칙·PLAN010/ADR004/current/본append.
- 정기조회: 기존공유lease/epoch와projectbudget 사용, sync없이최근90일활동/구매계정및활성·예정권리대상조회. 페이지중단암호화checkpoint·중복수렴,전체순회24h/1000페이지상한,완료후pending5m/활성6h/기타1d,실패기존backoff/RetryAfter·AUTH/불명검토. 빈목록/404를부재·환불·삭제증거로쓰지않는다. 최근대상밖/사라진provider기록의전수복구를주장하지않는다.
- 원장완료/재개: source TTL후 exact verified/allowed/ready Purchase·entitlement·ledger·계정/lifecycle 확인으로 PENDING sync만완료,같은Tx pending1감소·지급없음·case있으면차단. 기존source가있으면원래경로우선. 운영library는선택same-account최대100 unbound lookup오류만원version/사유·lease/generation·epoch검사후재개,createdAt보존/retryStartedAt새24h/최초backoff5초·pending불변. 상충account보류사유해제금지,partial선택/rollback,기존PENDING/활성lease있으면거절. account-only정기조회보류도exact사유로명시재개가능. projectAUTH해제/금전case해결은별도이며HTTP/자동재개를추가하지않았다.
- 참조/관측: APPLIED검증구매에만purchaseId별독립AES256-GCM/AAD(env/project/app/txHMAC)참조저장,첫corecommit후저장실패는페이지재시도로수렴. 다른참조·변조·staleepoch거절,원장보존일기준읽기만료·목록재조회로연장없음. 시간당개수/age/AUTH/sample freshness gauge와일일review/AUTH장애·복구요약,전송interface구현. 개인/거래/토큰/raw예외로그없음. 실제Slackadapter없고alertsON에sink없으면기동거절. 전송실패는금융상태와분리,인스턴스단위throttle라replica/재시작중복가능.
- schema/API: sync-r2 optional retryStartedAt, cursor-r2 optional reconcileAt/reconcileStartedAt, reference-r1전용collection/기본_id_only/TTL없음.기존writer격리/ALL_BLOCKED/exact이관·구행검사·verify-only기동,기존index보존. 외부DTO/Identity·LC/userJWT/지급·무료권계약불변. 기존C2b r1불변표현은개정문서로명시대체했다.
- 검증: 집중테스트와전체1,407통과후재개backoff/상충사유보완을추가하고최종 `./gradlew clean test bootJar` **1,408통과/실패0/오류0/skip0**,D1대비20추가. 실제로컬replica-set/fakeHTTP로TTL후완료·guardrollback·24h재개·partial/overflow/oldlease/AUTHblock·schema이관·정기누락/6h/1d/다페이지crash/404/lease공유·암호화/충돌/변조/만료/재접수·요약OFF/개인정보/전송실패검증. diff/관련로컬링크검사통과. 실RC/Store/Atlas호출없음.
- 남은작업/배포전: 외부Slackadapter/채널·담당자/운영재개도구권한·감사,5년reference physical purge와금융erasable정리연계·오래된cursorlease정리,실schema/키/공유DBtopology·explain/대량부하·StoreE2E·복원검증. 공개sync ON차단·worker/reconciliation/alerts/terminal삭제기본OFF·판매/205삭제활성화없음.
- 예상밖코드변경없음. 기존A~D1코드및동시LC draft문서/AGENTS변경은보존했고타저장소·운영데이터·Jira쓰기/상태·commit/push변경없음.


## 2026-10-14 LC 단순화 후속 인계

- 사용자 인계요청으로 최신 단순화 요약문 작성. 전체답안접수 여부로 단일mock선정·공통기접수완료·Billing복원전담·불변group차단/버전제거·terminalAt재사용·정본문서통합방향. 원대상/접수판정 경합 및 신규INITIAL/REPLACEMENT구별 최소wire는 공동검토로 남겨 단일후보가 무검증을 뜻하지않게 명시. 기존draft3전달snapshot보존·정본초안에후속참조. 코드/DB/LC저장소/외부전송/배포없음. diff/링크검사, Gradle미실행.

## 2026-10-14 TMI-212 Slack Incoming Webhook 전송

- 브랜치 feat/TMI-212-revenuecat-worker-reconciliation. 사용자 구현승인 후 Jira212 읽기확인, 채널생성은나중에하고기존운영요약의외부전송코드만구현했다.
- 파일: PaymentSlackSink/Properties/Configuration, application.yml, PaymentSlackSinkTest 신규, Slack 연결runbook 신규·기존reconciliation runbook/PLAN010 phase3/current/본append. 기존 A~D2 코드와동시 LC 문서작업은보존했다.
- 동작: 공식Slack HTTPS host/path allowlist·no redirect/proxy·연결2초/전체대기5초/응답2KiB. whitelist 환경/enum/시각/개수/고정안내문만전송,식별자/토큰/원문/동적channel없음. 200/ok만성공,429/503 정수초·HTTP-date Retry-After와최소1시간,잘못된/중복/overflow header보류,401/403/404/410 프로세스내차단. 재시작초기화·replica/응답유실중복허용,영속알림원장없음. 예외/URL비로깅·interrupt보존·client종료.
- 설정: worker/alerts/slack 세flag 기본OFF,Slack OFF는URL/HTTPclient없음,ON은유효URL필수. 실제Webhook은나중에배포secret으로제공. CleanupAlerts는범위밖이며provider요약만연결했다. 기존업무API/DTO/DBschema/Identity·LC인증·지급/환불계약변경없음.
- 검증: `./gradlew clean test bootJar` **1,429통과/실패0/오류0/skip0**,직전1,408대비21추가. fake transport로URL우회거절/3종요약/음수·환경거절/전송실패·영구차단/Retry-After날짜·초·과거·누락·중복·overflow/응답상한·sanitized오류/interrupt·close/OFF·ON기동검증. 기존로컬replica-set전체회귀포함. 실제Slack/RevenueCat/Store호출없음. diff와관련문서로컬링크검사통과.
- 위험/배포전: 채널·담당자·Webhooksecret·egress/TLS·실전송확인필요,APM/HTTPdebug의secret URL노출방지·전송실패metric외부감시필요. 시간당관측이라즉시pager아님. 실제연동·활성화미실시,D전체/판매완료아님. 기존운영도구권한/감사·5년physicalpurge/cursor정리·실환경검증후속.
- 이번범위의예상밖변경없음. 동시LC문서추가는타작업으로보존. 타저장소/운영데이터/배포/Jira쓰기/commit/push변경없음.


## 2026-10-14 요약 대기 시험의 직전 문제 제외 승인

- 사용자 「어 그렇게 하는 게 좋을 거 같은데」에 따라 전체답안durable접수/채점요약대기 신규시험은 직전mock제외, 다른적격후보0확인시동일허용 확정. 부분접수동일·완료후기존선정 유지. 조회장애와0후보구별·동일operation선정고정·INITIAL판정/fixture사항 인계에명시. source/시작사유exactwire는후속.
- 문서/PLAN011/결정서/current만수정, LC코드/DB/실서비스/배포/외부전송없음. diff·링크검사, Gradle미실행.

## 2026-10-14 TMI-212 Slack 설정 확인·서버 연결 대기

- 사용자 승인으로 비공개 billing-alerts-test 채널 및 Billing Alerts Test 앱 생성, 읽기 scope 없이 Incoming Webhook 설정. 사용자가 최종 허용한 뒤 Success 및 해당 채널 Webhook 행을 UI로 확인했다. Secret URL/토큰은 저장소와 응답에 기록하지 않았다.
- 후속 secret 등록·테스트 발송 요청: 로컬에는 .github 배포 workflow가 없고 runbook은 환경변수명만 정의함을 확인. 열린 AWS ECS 화면은 만료 세션이며 재로그인 화면까지 이동했다. 과거 화면의 서비스 목록을 현재 상태 증거로 쓰지 않았다. Billing 실제 배포/secret 대상은 미확인이다.
- 서버 secret 등록/권한변경/배포/flag 활성화/실제 Webhook POST는 수행하지 않음. 사용자 AWS 로그인과 배포 대상 확인 후 계속한다. 코드 변경 없음, Gradle 미실행. 기존 동시작업 보존, Jira/commit/push 변경 없음.

## 2026-10-14 Slack 연결 대상 ECS 재확인

- 사용자 재로그인 후 서울 리전 staging 클러스터 서비스목록을 현재시각으로조회. 전체10개/다음페이지없음,Identity·Learning Core·AI서비스만보이고Billing이름서비스없음. 다른배포위치부재를단정하지않고사용자대상확인을요청한다.
- Secret등록/조회·실제Webhook발송·권한변경·배포·활성화없음. 코드변경/Gradle실행없음,문서기록만추가. 비밀URL/실제AWS식별자미기록,기존변경보존.


## 2026-10-14 LC 유료 시험 공동 검토 정본 통합

- 사용자 「좋아 해줘」에 따라 최신 승인과 LC 최소wire를 통합 R2 draft-1로 작성. startReason3/단일mock·전체접수직전제외·transition존재조건(완료원대상도포함)·불변revoke같은group새ID충돌·code+terminalAt 정리. 변경이력분리 및 종전후보문서/PLAN011~013/결정서에새기준참조. phone삭제/보존/실환경gate는확정으로가장하지않음.
- S01~S16/V01~V07 검증범위23개, 실제fixture/hash/독립실행없음. draft3snapshot/Afixtures불변, 코드/DB/외부전송/배포없음. diff/로컬링크/ID검사, Gradle미실행.

## 2026-10-14 TMI-212 운영 재개 도구 범위 확인

- 브랜치 feat/TMI-212-revenuecat-worker-reconciliation. 사용자 다음작업확인 승인에 PaymentLookupResumer·공용budget해제·RecoveryOperator/CLI·fence·기존통합테스트/계약을정적검토했다.
- 재개library의최대100건/같은계정·version/generation·lease/PENDING·case차단·원자rollback·새24h예산은존재. 실행도구/실행자권한·운영사유/작업ID·감사receipt는미연결이다. 기존failure enum은운영사유가아니며 boolean격리는실제중지증명이아니다.
- 신규 gap-review 문서와current/본append만변경. CLI inspect/dry-run/apply·원자감사·project AUTH별도명령을권장하고ALL_BLOCKED에서는재개가거절됨을명시. schema/감사보존·권한배선은구현전명세후속. 제품정책/외부API/코드/DB/배포/Jira/Git쓰기없음.
- 검증: 정적소스/기존테스트확인과diff/문서링크점검. 코드변경없어Gradle미실행,이전1429통과를새검증으로주장하지않음. 기존동시LC문서및코드작업보존,이번범위예상밖변경없음.


## 2026-10-14 LC 통합 R2 draft-1 검토 수신

- 첨부 §9 기준 주요기술1~3 이견없음 확인. draft-2에 inboxTTL후 retained group최초ID/digest로204/409, 원늦은완료가신규guard해제금지, 새source/사유권리부족이면기존시험보존을 본문/S09/S12/V01에명시. 기존불변식회귀이며 제품정책/field추가없음. LC/Billing 잔여담당·S15/V07미확정정상분기가짜통과금지명시.
- 문서만, fixture생성/테스트/코드/DB/외부전송/배포없음. diff/링크/ID검사.


## 2026-10-14 paid R2 오프라인 fixtures-1 제작

- 사용자실행요청에 S01~14/S16/V01~06 자료제작: Node 생성기(임시키private메모리만)/별도Python strictJSON/canonical/OpenSSL 검사기. publicJWK/PEM·signedtoken22·canonical9·invalid13·HTTP판정표12·wire6/status3·상태givenwhen26, manifest/SHA256SUMS. 12파일bytes/hash/별도검사성공, 변조탐지·덮어쓰기거절확인.
- 상태시나리오/실HTTP·Mongo경합/runtime은미실행으로표시, S15/V07보류·기존A불변. LC독립검증/최종동결미완료. 제품코드/DB/실서비스/배포/외부전송없음. 검증기실행/diff/링크검사, Gradle미실행.

## 2026-10-14 TMI-212 운영 재개 CLI·원자 감사 구현

- 브랜치 feat/TMI-212-revenuecat-worker-reconciliation. 사용자 「어 해줘」 구현승인 후 Jira212 목표/완료조건을읽었다. 신규운영시스템/관리자API없이기존재개library에독립CLI를연결했다. Jira는읽기만했다.
- 변경파일: PaymentReviewOperator/Cli·PaymentOperatorSchema 신규, PaymentLookupResumer/RevenueCatProjectBudget의same-session·read-only 분기, PaymentRecoveryFence.check 신규(기존write acquire불변), build.gradle 명시task. CliTest 신규·PaymentSyncIntegrationTest 회귀추가. 운영runbook/exact 신규·기존재개계약/운영안내/PLAN010/current/본append.
- 동작: inspect기본, resume는기본읽기전용사전검사,명시apply+writer격리확인필수. account한번최대100선택·버전/generation/최초epoch·활성lease/PENDING·case/소유검사유지. AUTH는SHARED scope의공유budgetDB별도명령/사유CREDENTIALS_FIXED·rate starts보존,금융/계정재개와독립. 외부HTTP/서버/worker기동없음.
- 감사: 단일Mongo인증principal에서actorDigest생성,익명/다중principal거절·자칭operator옵션없음. request/target digest·표준사유·건수/완료시각·작업ID receipt를해당재개와같은Tx로저장,실패시rollback. 같은ID/동일입력은ALREADY_APPLIED,다른입력/주체거절. 응답유실은같은ID로result조회,NOT_FOUND가미진행확정은아님. 실패전체원문/credential/거래원문비로깅,내부commandhandle은제한된운영터미널만.
- schema: additive payment_operator_receipts r1,strict/error/simple·기본_id_unique_only,TTL/자동purge없음,명시격리설치·기동verify만. account Billing DB와auth공유DB각자원자성,DB간전역Tx주장없음. 기존업무DTO/API/무료권/Identity·LC인증/지급·환불·sync/cursor schema불변. 새로운금융강제처리없음.
- 검증: 집중Cli/Sync회귀통과후전체실행성공. inspect진행수/잠금표시최종수정반영후 `./gradlew clean test bootJar` 재실행 **1,455통과/실패0/오류0/skip0**,직전1429대비26추가. 실제로컬Mongo replica-set에서dry-run전체document불변·apply/replay/다른사유/다른actor·동시동일ID·stale/epoch/ALL_BLOCKED·receipt실패account/AUTH rollback·schema미설치/drift/TTL거절·인증principal/CLI인자숫자경계·비밀출력방지검증. 운영인증/실role/실API실행아님. diff/로컬문서링크검사통과.
- 운영gate: 개인별운영DB자격증명/최소권한·실행환경감사와원입력관리·writer실제중지·감사보존기간확정필요. Boolean격리는정지증명이아니고공유DB계정은개인식별을보장하지않는다. 보존미확정으로자동삭제없으며무기한보존승인아님. 실제schema설치/worker/판매/자동삭제활성화/배포·운영DB실행/키발급없음. 5년physicalpurge·cursor정리·실환경검증은남음.
- 이번범위예상밖변경없음. 작업중추가된LC정본/fixture/기타기존변경보존,타저장소/Jira쓰기/commit/push없음.


## 2026-10-14 fixtures-2 escape/나노초 수정

- LC 읽기검토 두지적 재현·수정: scalar문자열serializer/JSONroundtrip, 정수초+Decimal나노초clock. 새revision2 생성(구1 전체bytes불변), canonical18/clock5/invalid13/token31/HTTP판정12/wire6/status3·13파일hash 성공. 구escape9개실패/구floatclockmutation실패·파일변조거절·기존revision덮어쓰기거절·새검사기구1회귀통과.
- 가짜키public만저장·private메모리, 제품코드/DB/배포/외부전송없음. LC독립/실HTTP/업무state26미실행·S15V07보류. 실행검사/diff/링크검사, Gradle미실행.

## 2026-10-14 TMI-212 만료 참조·lookup lease 정리 부분 구현

- 브랜치 feat/TMI-212-revenuecat-worker-reconciliation. 사용자 「어 해줘」 승인 후 AGENTS·보존 계약·Jira212를 읽고 구현. Jira는 읽기만 수행.
- 변경 파일: PaymentReferenceRetentionWorker 신규, PaymentProviderWorkerConfiguration/Properties·application.yml 연결, PaymentSyncIntegrationTest 10회귀·WorkerConfigurationTest OFF 검증 보완. reference-retention-r1 신규·purchase-reference 계약/PLAN010/current/본 기록 갱신.
- 동작: provider runtime 매시간 단계별 최대100개, 기본 read-only 점검. 독립 reference-retention-enabled=true에서만 참조 삭제·만료lease 해제. 최초epoch/OPEN·같은Tx fence와version 재검사, 현재 Purchase deadline/금액/READY·기간권/원장5년·pending/case/비terminal sync/접수/lookup 보호. 누락·불명은 보류. `_id` 순회로 보류 행 다음도 점검하며 오류는 별도 건수. 참조 원문·식별자 로그 없음.
- lease: 만료owner/until만 비우고 row/lease version 증가. retry attempt·generation·페이지cursor·reference·실패/검토 상태·스케줄·pending을 보존. 활성/갱신 lease는 해제하지 않고 stale worker는 거절한다.
- 범위 조정 사전 설명: 금융 Purchase.accountId/ownerRefId·entitlement.accountId가 필수라 임의 unset/가짜ID로 바꾸지 않았다. 암호화 참조 행 내 연결만 제거하며 금융 사용자 연결 physical purge는 schema/reader 이관 명세가 필요하여 미완료. 전체D/5년privacy purge 완료로 보고하지 않는다. 신규 ledger/reversal 타입은 현재 보류하며 PLAN012 통합 회귀 필요.
- 검증: 집중 Sync/Configuration 회귀 성공 후 최종 `./gradlew clean test bootJar` 성공, **1,465개 통과/실패0/오류0/skip0**, 직전 대비10추가. 로컬 Mongo replica-set에서 dry-run 불변·현재 보존일 연장·기간권/원장 보호·금액불명/검토/미해결·환경/원구매누락·barrier·만료/renew/stale lease·동시삭제 수렴 검증. diff whitespace 검사 통과.
- 유지: 외부 API/wire·사용자/Identity/LC JWT·지급/환불/무료권·금융 원장·205 계정 삭제 불변, 기존 schema/index/TTL 변경 없음. 운영 flags OFF·실서비스/실DB/배포/Secret/Jira 쓰기/commit/push 없음.
- 남은 작업/위험: 금융 erasable 연결 모델·reader 명세, PLAN012 reversal 보존 연동, 운영 explain/부하·복원/backup35일·장기보류 처리 확인 뒤 별도 활성화. 기존 및 LC 동시 변경 보존, 이번 수정에서 예상 밖 범위 변경 없음.

## 2026-10-14 TMI-212 금융 사용자 연결 제거 이관 설계

- 브랜치 feat/TMI-212-revenuecat-worker-reconciliation. 사용자 후속 「어 해줘」에 따라 남은 schema/reader 이관 설계를 작성했다. PurchaseCore/Schema·SyncService·PaidEntitlementReader·CleanupPurger·참조 보존 계약을 정적 조사했다.
- 변경 파일: docs/plans/TMI-212-financial-link-erasure-plan.md 신규, PLAN010 3차·CURRENT_STATE·본 기록. 구매별 accountId/ownerRefId null+connectionErasedAt, 기간권 accountId null과 linked-only partial unique 제안. 기존 전체 unique에서 서로 다른 계정 null/sequence 충돌 위험을 명시했다.
- 지급 재관측/미소유 sync·참조 재생성·sourceObservationKey 및 PLAN011 사용 증거 역연결·PLAN012 늦은 반환·writer 격리와 reader-first/rollback 순서를 정리했다. 기존 금융 사실/HMAC 유지가 무기한 보관 승인·익명화 완료는 아님을 명시. 전체금융 최종삭제·후속반환·public오류 exact는 미확정이다.
- 이번은 설계 문서만 변경. 제품 코드/DB/index/외부 API 계약·보존기간·무료/인증·운영 flags 불변. 정적 diff/로컬 문서 링크 검사 수행, 코드 변경 없어 Gradle 재실행 없음. 직전1465통과는 이 설계의 실행 검증 결과가 아니다.
- 기존 dirty 및 LC 동시 변경 보존, 이번 예상 밖 변경 없음. Jira쓰기·외부메시지·실DB·배포·commit/push 없음. 다음은 남은 exact와 연계 보존 범위를 고정한 뒤 OFF 구현이다.

## 2026-10-14 TMI-212 금융 연결 제거 복잡성 검토

- 사용자 요청: 감수 가능한 위험 대비 과도한 설계 여부 확인. 설계 원문/현행 schema 및 직전 코드 조사 근거 대조. 최종 보존 처리가 미정인 채 nullable 중간 상태를 먼저 구현하는 범위가 과함을 확인했다.
- 신규 문서 TMI-212-financial-link-erasure-simplification-review.md와 CURRENT_STATE/본 기록만 갱신. 최종 보존안 우선, 만료일 확인 후 개발 순서 분리, 초기 writer정지 CLI/소량 작업, 오래된 금전 예외 기존검토, writer가 보장하는 단일 retention 기준을 권장했다. Transaction/중복지급/개인정보/nullable unique 보호는 유지 필요.
- 코드·기존 설계 확정 내용·보존정책·DB/index/flags 변경 없음. 실제 만료일/legacy 운영 자료 미조회. 테스트 미실행(문서 정적 분석), diff/문서 링크 검사 수행. 기존 변경 보존·예상 밖 수정 없음, Jira/외부메시지/배포/commit/push 없음. 다음은 단순안 수락 후 계획 개정이며 정책을 자동 변경하지 않는다.

## 2026-10-14 5년 금융 정리 후속 분리 승인 반영

- 사용자 「좋아 이렇게 하는 걸로 해서 수정해줘」에 따라 5년 만료 금융 정리/nullable 이관을 현재 결제 출시 선행 조건에서 분리했다. 결제·환불 구현을 우선하며 보존 기준과 만료 전 후속 작업을 유지한다.
- 변경: 금융 연결 제거 설계 상단에 후속/역사적 제안 표시, PLAN010 3차의 즉시 구현·D 차단 표현 조정, PLAN013 보존/출시 gate 구분, reference 정리 계약·CURRENT_STATE·본 기록 갱신. 금융 전체 삭제 범위/자동지급 연령 exact를 새로 확정하지 않았다.
- 최초 만료일/legacy 자료 미조회. 실제 대상 확인과 만료 전 담당/기한 지정 필요, 이미 만료·임박한 자료는 별도 처리. 미구매 탈퇴 계정15일·접수120일·Trial3년·backup35일 및 다른 인증/결제/환불/복원 gate 유지. 기존 정리 코드는 보존하고 삭제flagOFF 유지.
- 문서 작업으로 Gradle 미실행, diff/로컬 링크 검사 수행. 코드/API/schema/DB/보존기간/실환경 flags 불변. 예상 밖 변경 없음·기존 동시 변경 보존. Jira쓰기/외부메시지/배포/commit/push 없음. 다음은 현재 결제·환불 잔여 구현/검증 확인이며 이번 승인만으로 판매/완료 처리하지 않는다.

## 2026-10-14 결제·환불 잔여 작업 확인

- 사용자 「다음 작업 해줘」를 직전 예고한 잔여 구현/환불 범위 확인으로 진행. CURRENT_STATE·PLAN010/012·LC R2·public/controller/worker/core/admission 코드와 Jira TMI-137 하위 목록을 읽었다.
- 확인: sync ON 무조건기동실패, PaymentSyncService Spring bean 선언 없이 worker 내부에서만 생성. controller는 ObjectProvider로 없으면503. 환불 core는 PLAN012 검토 인계, admission은 interface/unavailable. 최신 confirm-before-refund 정책과 구 PLAN012 본문 역사적 문구를 구별했다.
- 신규 문서 TMI-212-next-work-review-2026-10-14.md에 A public sync 실제 연결/준비 검사→B PLAN011/012 공동 exact 및 구현→C 실환경 gate 순서와 완료 기준 작성. current/본 기록 외 코드·계약·DB 수정 없음. 불필요한5년정리 선행조건은 추가하지 않았다.
- 정적 diff/문서 링크 검사, Gradle 미실행(분석만). 기존1465테스트는 새연결 성공근거 아님. 실제API/배포/Store/키/Slack전송·Jira쓰기·commit/push없음. 기존 및 LC 동시변경 보존, 예상밖 수정 없음. 다음 구체적 구현은 A이며 기본OFF·판매보류 유지.

## 2026-10-14 TMI-212 public sync 실제 service 연결 구현

- 사용자 「어 해줘」로 A 구현 승인. Jira TMI-212 목표·완료조건 재조회 후 Billing만 변경. 현재 브랜치 feat/TMI-212-revenuecat-worker-reconciliation.
- 변경 파일: PaymentProviderWorkerConfiguration의 조건부 public service bean, PaymentProviderWorkerRuntime의 동일 service 노출, PublicSecurityConfig의 구조적 준비 조건, PaymentSyncService 주석. SyncIntegrationTest/WorkerConfigurationTest/PhaseTwoConfigurationTest 보완. public-sync-wiring runbook 신규·PLAN010/이전sync/D2운영/다음작업계획/current/본기록 갱신.
- 동작: worker가 실제 schema/key/app/catalog/budget을 검증하여 생성한 동일 service를 공개 controller에 주입. 별도API용 설정이나 ready=true 없음. sync ON은 worker/fence와 기존JWT/ingress/transaction/limiter 조건 필요. worker/config/schema누락은기동실패. syncOFF는service bean미노출, worker는기존별도flag로준비가능. 기본OFF·ApplicationReady후worker시작 유지, 앱요청은DB접수만수행.
- 검증: 신규9테스트, 최종 `./gradlew clean test bootJar` **1,474통과/실패0/오류0/skip0**, 2분19초. 실제Spring context/로컬Mongo schema로동일객체·budget미설치/cursor drift·잘못된key/API설정/catalog·OFF·fence/public설정검증. MockMvc+실service/rate limiter/DB+fake provider worker로202중복접수→한번지급→200→탈퇴403확인. 인증완료principal주입테스트이므로해당통합경로자체를실JWT/네트워크E2E로주장하지않으며기존전체보안회귀도통과.
- 중간 테스트 구성 오류(TraceCorrelation 생성자 인자, context가 테스트MongoClient를먼저닫는수명주기)를수정후재실행성공. 실RevenueCat HTTP는호출하지않음. 문서링크/diff검사통과.
- 불변: 외부DTO/상태/202·Retry-After/200·인증scope·원장/지급정책·schema/보존기간·무료권/Identity/LC계약.5년정리/Slack/실제삭제ON을새기동의존성으로추가하지않음. 실키/DB설정/flags/배포/판매/Slack발송/Jira쓰기/commit/push없음.
- 위험·다음: 정상기동은실Store지원·credential유효성·운영준비증명이아님. PLAN011/012공동exact및환불processor/revoke·입장구현,실Store/ALB/Identity/복원/운영권한검증후별도활성화. 기존미커밋및LC동시변경보존,이번예상밖범위변경없음.

## 2026-10-14 프론트용 Notion API 1개 초안 작성

- 브랜치 feat/TMI-212-revenuecat-worker-reconciliation. 사용자 요청으로 지정 Notion 결제 페이지(3f9dcc5dbaeb80618ec9ed3e5d8a5966)에 POST /api/v1/payments/sync 하나만 작성했다. TMI-211/212 구현 코드의 decoder/result/controller/service/limiter/응답 writer를 기준으로 인증·요청·202/200·오류·멱등 재시도·문의 안내를 정리했다.
- 외부 변경은 지정 페이지 본문뿐이며 공유 권한은 변경하지 않았다. 로컬 변경은 CURRENT_STATE와 본 기록. 기본OFF·미배포·실Store 미검증과 SDK transactionId 매핑 확인 필요를 명시하고 후속 API는 사용자 형식 수정 후 작성한다.
- 검증: 노션 본문 전체와 JSON 코드 블록 렌더링 확인 및 화면 저장, diff whitespace 검사. 코드 변경 없어 Gradle 재실행 없음. 직전1474통과는 이 문서 작업의 신규 테스트가 아니다.
- 기존 dirty/LC 동시 변경 보존, 예상 밖 변경 없음. 코드/API/정책/flags/실DB/배포/Jira쓰기/commit/push 없음. 실제 credential·거래 원문을 기재하지 않고 가짜 예시만 사용했다.


## 2026-10-14 — PLAN-011 R2 구현 계획 정리

- 사용자 「어 정리해줘」에 따라 기존 PLAN-011을 현재 R2 정책 중심으로 재작성하고 직전 전문은 PLAN-011-HISTORY-BEFORE-R2-2026-10-14.md에 보존했다.
- 별도 채점 승인 API/TRIAL fallback/종전 복원 금지 등 과거 요구를 현행에서 제거하고 P0 계약·schema → P1 source/복구 → P2 reserve/guard → P3 confirm/복원 → P4 LC 마무리 → P5 이용 증거/통합으로 정리했다.
- 사용자 전달 LC 인수 보고의 독립89·전체948 통과와 원본14파일 동일을 보고 근거로 접수했다. 이번 턴에서 LC 테스트 재실행/공동 최종 수락은 하지 않았다.
- 관련 PLAN-011/012/013, TMI-116/TMI-128/TMI-122 및 TMI-125. S15/V07 및 실환경/보존·배포 gate 유지, 기존 금융5년 정리 후속 분리와 구분했다.
- 변경은 계획/이력/CURRENT_STATE/본 기록뿐. 기존 dirty 코드·문서는 보존했으며 제품/LC/DB/Jira/배포/commit/push 변경 없음. 계약/fixture 원본 hash·문서 링크·diff 확인, 문서 작업이라 Gradle 미실행. 예상 밖의 이번 변경 없음.

## 2026-10-14 프론트 직접 호출 API 목록 확인

- 사용자 질문에 public ingress/controller mapping을 정적 확인. products/purchase-account/sync/entitlements 네 개이며 RevenueCat webhook과 internal API는 프론트 대상이 아님을 안내했다. TMI-211/212 연계 문서 범위 확인으로 코드·계약·노션·Jira·배포 변경 없음.
- 변경 파일은 CURRENT_STATE/본 기록뿐. 기존 dirty 변경 보존, 예상 밖 변경 없음. 코드 미변경으로 Gradle 미실행. 다음은 사용자 요청 시 나머지 세 API를 수정된 노션 형식으로 작성한다.


## 2026-10-14 — PLAN-011 P0 저장/Transaction·공동 합의안 작성

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. 목표: 사용자 「어 만들어줘」에 따라 저장 구조/Transaction 설계와 공동 계약 마무리 자료 작성.
- 변경: PLAN-011-STORAGE-TRANSACTION-DESIGN-R1.md, LC-BILLING-PAID-EXAM-R2-IMPLEMENTATION-AGREEMENT-R1.md 신규, PLAN-011 링크/CURRENT_STATE/본 기록 갱신.
- 확인: Reservation/Group/Session/Grant/command의 trial 중심 구조, status의 command 의존, 무료 Spring executor와 payment explicit session 차이, 결제 strict schema. 단일 session adapter·source별 reference·guard/receipt·복원 unique·원자 거절 commit·T1~T6 설계 제안.
- 공동 확인 J1~J4 및 S15/V07 미확정 유지. 기존 R2/fixtures-2 최종 수락/제품 구현/배포를 완료로 보고하지 않음. 관련 PLAN-011/012/013·TMI-116/TMI-128/TMI-122/TMI-125.
- 검증: 문서 링크/diff 및 R2 계약·fixtures-2 SHA256SUMS/파일 hash 확인. 제품 코드 변경이 없어 Gradle 미실행. 실DB/AWS/LC 조회·Jira 변경/외부 회신/commit/push/배포 없음. 기존 dirty 변경 보존, 예상 밖의 이번 변경 없음.
- 다음: 공동 확인 및 Billing schema/session adapter 설계 수락 후 승인 범위 구현. 보존/phone 정상 경로를 추정 구현하지 않는다.

## 2026-10-14 프론트용 Notion API 네 개 문서화

- 브랜치 feat/TMI-212-revenuecat-worker-reconciliation. 사용자 요청/중단 후 재개에 따라 지정 Notion 결제 페이지(3f9dcc5dbaeb80618ec9ed3e5d8a5966)의 기존 sync·사용자 오류 표를 보존하고 products, purchase-account, entitlements 세 API를 추가했다. TMI-201/211/212 관련 구현 정적 확인이며 이슈 변경 없음.
- Controller/DTO/service/limiter/응답 writer 및 기존 reader 계약을 기준으로 인증·body/query 없음·응답/필드·빈값·오류·재시도 규칙 작성. 신규 구역은 사용자 형식의 상세 토글과 오류 표를 사용했다. 초기 브라우저 붙여넣기 서식 손실은 추가한 블록만 수정하고 native HTML paste로 토글/표/코드 블록을 확인했다.
- products는 null이 아닌 []이며 SDK 가격/매핑과 구분, purchaseAccountRefId는 사용자·환경별 stable, 통합 무료 status와 paid.status 독립·확장OFF 시 필드생략·현 canTakeExam null/ADMISSION_NOT_READY fallback을 명시했다. 재응시 1회 UI와 신규 수량의 의미를 분리했다.
- 외부 변경은 지정 노션 문서뿐이며 공유 권한·다른 API 문서는 불변. 로컬 변경은 CURRENT_STATE/본 기록뿐. 기존 dirty/LC 동시 작업 보존, 예상 밖 변경 없음. 코드·API·DB·flags·실Store·배포·Jira·commit/push 없음.
- 검증: 노션 본문/마지막 문단·토글·표·JSON 렌더링과 완료 화면 확인, 로컬 diff whitespace 검사. 코드 변경 없어 Gradle 미실행. 실배포·실SDK transactionId 검증 완료를 주장하지 않으며 후속 구현 시 특히 시험 판정 설명을 갱신해야 한다.

## 2026-10-14 머지 준비 상태 안내

- 사용자 질문에 현재 브랜치 feat/TMI-212-revenuecat-worker-reconciliation, status/log/current와 diff whitespace를 확인했다. HEAD는 TMI-211 PR15 병합 커밋이며 TMI-212 코드/운영 문서와 LC·PLAN-011 계약/fixture가 미커밋 상태로 혼재한다.
- 기본OFF 구현의 PR 통합을 권장하되 변경 범위 구분·최종 tree 테스트·CI/리뷰 확인이 선행돼야 한다고 안내. 현재1474통과는 이전 실행 기록이며 새 실행 또는 최종 코드 리뷰 결과가 아니다. 원격 PR/CI 상태는 조회하지 않았다.
- CURRENT_STATE/본 기록만 갱신. 코드·기존 변경 보존, 예상 밖 변경 없음. commit/push/PR 생성/머지/Jira/배포/활성화 없음. 다음 요청 시 PR 준비 범위부터 정리한다.


## 2026-10-14 — LC 합의안 R1 회신 반영·J3/J4 보완안

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. 목표: 사용자 전달 LC 회신의 exact 실패 관측 및 owner 표 요청 보완. 관련 PLAN-011/012/013·TMI-116/TMI-128/TMI-122/TMI-125.
- 변경: LC-BILLING-PAID-EXAM-J3-J4-ADDENDUM-R1.md 신규, 합의안/저장설계 후속 링크, CURRENT_STATE/본 기록.
- Billing 현 오류4-field/status/subject link/account/withdrawal CAS를 읽고 status 전용 RESERVE_REJECTED_BEFORE_CREATION·OPERATION_OWNER_MISMATCH 제안, local guard terminal 조건·reserve worker 재발행 금지·same-key 우선순위·source별 내부 owner snapshot·version 증가와 ownership 변경 분리를 작성했다.
- 신규 code/fixture 명세는 후보이며 제품/runtime/공동 동결 완료 아님. ACTIVE MEMBER 근거의 실제 연결과 S15/V07 미확정 명시. 원 R2/fixtures-2 유지.
- 문서 링크·diff 및 원본 hash 검증. 제품 변경이 없어 Gradle 미실행. LC 코드/DB/AWS/Jira/배포/외부 메시지/commit/push 변경 없음, 기존 dirty 보존. 다음은 LC exact 검토 및 합의 후 별도 fixture revision 제작.

## 2026-10-14 테스트 Billing 주소와 사용자 audience 안내

- feat/TMI-212-revenuecat-worker-reconciliation. 사용자 질문에 application.yml·공동 인계·CONTRACT_DECISIONS의 사용자 JWT 발급 계약 확인. 기존 서비스 주소 양식에 맞춘 billing-test.to-teacher.com을 예정 표기로 제안하며 DNS/ALB/실배포 확인과 구분했다.
- Identity가 기존 LC audience를 보존하며 tosunsaeng-billing과 route scope를 발급하는 방향, 앱은 동일 Bearer 토큰 사용·별도 aud 요청 불필요·구토큰은 재발급 필요를 설명. 현재 Identity 코드/실토큰/배포 완료는 이번에 확인하지 않았다.
- CURRENT_STATE/본 기록만 갱신. 제품 코드/인프라/노션/배포/Jira/commit/push 없음. 정적 문서 확인으로 Gradle 미실행, 기존 동시 변경 보존·예상 밖 변경 없음. 다음은 환경 구성 시 예정 host와 실제 발급 토큰의 별도 검증.


## 2026-10-14 — LC J3/J4 R1 회신 반영 draft-2

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. 사용자 첨부 LC 검토 회신 전체를 읽고 J3/J4 문서를 draft-2로 보완했다. 관련 PLAN-011/012/013, TMI-116/TMI-128/TMI-122/TMI-125.
- 변경: J3/J4 보완안, 상위 합의안/저장설계 링크, CURRENT_STATE/본 기록. PREPARED·새 Session 부재·commit marker·조회 전후 lease/version/owner·원자 guard 해제·local 시각·strict 오류·owner-blocked를 명확히 하고 J3-08~12 fixture 제작 명세를 추가했다.
- 기존 Identity 결제/withdrawal 계약과 Billing account 생성 코드를 읽어 local ACTIVE의 한계를 확인했다. 권위/전달/최신성/deny 순서/장애·worker 근거 질문을 정리했으며 새 Identity API/이벤트/claim 방식은 선택하지 않았다.
- S15/V07 및 MEMBER_GRANT 정상 경로 미확정 유지. LC current client/worker 미지원은 사용자 전달 조사 결과로 접수, LC 자체 읽기/실행 없음. 원 R2/fixtures-2 변경 없음.
- 문서 링크/diff·원 계약/fixture hash 확인. 문서 변경이므로 Gradle 미실행. 코드/DB/Jira/배포/외부 전송/commit/push 없음, 기존 dirty 보존. 다음은 draft-2 LC 검토 및 Identity 증거 연동 계약 확인.


## 2026-10-14 — J3/J4 중복 검토 회신 확인

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. 사용자 새 첨부와 직전 첨부 byte 대조 결과 동일, 둘 다 draft-1 대상이다. 현재 정본 draft-2 확인. 관련 TMI-116/TMI-128/TMI-122/TMI-125.
- CURRENT_STATE/본 기록만 갱신. 계약/fixture/제품/DB/배포 변경 없음. 문서 확인이므로 Gradle 미실행, diff 검사. 다음은 draft-2 검토와 Identity MEMBER 증거 계약 확인.


## 2026-10-14 — LC J3/J4 draft-2 재검토 접수

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. 사용자 첨부 전체 및 신규 후속절 확인. J3 보완 해소·추가 이견 없음, J4 증거 연동 미확정 유지로 접수했다. 관련 TMI-116/TMI-128/TMI-122/TMI-125.
- 변경: J3/J4 보완안에 접수 후속절, CURRENT_STATE/본 기록. 검토 확인을 공동 최종 동결/fixture 제작/구현 승인으로 확대하지 않음. 다음은 J3 수락 범위와 별도 fixture, J4 Identity 증거 계약 확인.
- 문서 diff 검사. 문서 접수라 Gradle 미실행. 제품/원 R2/fixture/DB/Jira/배포/외부 메시지/commit/push 변경 없음, 기존 변경 보존.


## 2026-10-14 — J4 ① MEMBER 증거 보존·기존 탈퇴 연동 승인 반영

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. 사용자 「j4도 1번으로 해줘」와 앞선 계정 유형/탈퇴 전제·지연 수용을 결정 문서로 기록했다. 관련 PLAN-011/012/013·TMI-116/TMI-128/TMI-122/TMI-125.
- 변경: PLAN-011-J4-MEMBER-EVIDENCE-DECISION-2026-10-14.md 신규, PLAN-011/저장설계/합의안/J3J4 상단 승인 참조, CURRENT_STATE/본 기록. 최초 증거 전달 exact와 부재 복구/지연 운영 기준은 후속.
- 문서 링크/diff 검사, 문서 변경이라 Gradle 미실행. 원 R2/fixtures·제품/LC/Identity/DB/Jira/배포/외부 회신/commit/push 변경 없음. 기존 변경 보존. 다음은 최초 MEMBER 증거 전달 계약 구체화·공동 확인.


## 2026-10-14 — J4/S15/V07 승인 정책 문서 동기화

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. 사용자 「어 해줘」에 따라 최근 S15 삭제②·V07 권장 및 J4① 승인을 통합했다. 관련 PLAN-011/012/013·TMI-116/TMI-128/TMI-122/TMI-125.
- 변경: PLAN-011-J4-S15-V07-DECISIONS-2026-10-14.md 신규, PLAN-011/저장설계/공동합의안/J3J4 최신 승인 참조 및 PLAN-011 잔여 표현 갱신, CURRENT_STATE/본 기록.
- 명시 사용자 삭제와 단순 Session 부재/중도 이탈 분리. 기존 삭제 continuation 재응시 복구 제외, 삭제 사실 전달·confirm 경합·새 INITIAL wire는 별도 기술 계약으로 남김. V07 보존기간 숫자는 승인되지 않았으므로 발명하지 않음.
- 문서 링크/diff·기존 R2/fixtures-2 hash 검사, 문서 작업이라 Gradle 미실행. 원 계약/fixture·제품/DB/LC/Identity/Jira/배포/외부 전송/commit/push 변경 없음, 기존 dirty 보존. 다음은 LC 삭제 기술안/phone·보존 exact 및 Identity 최초 MEMBER 증거 확인.


## 2026-10-14 — PLAN011 네 잔여 기술안 작성

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. 사용자 요청에 따라 MEMBER 최초 증거/삭제 전달·경합/phone 권리선택/보존기간 권장안 작성. 관련 TMI-116/TMI-128/TMI-122/TMI-125.
- Billing purchase-account JWT/scope/계정Tx·phone 코드 및 LC 삭제 store/ExamSessionManager 읽기. 기존 경로 재사용과 필요한 최소 새 deletion route 제안, phone INITIAL 예외는 새 wire 검토로 명시.
- PLAN-011-REMAINING-TECHNICAL-PROPOSAL-R1.md 신규, 통합결정/PLAN011 링크·CURRENT_STATE/본 기록 갱신. 보존 숫자·지연 SLO·새 route는 후보이며 승인/구현 완료 아님.
- 문서 링크/diff·R2/fixtures-2 hash 검사. 코드 미변경이라 Gradle 미실행. LC/Identity 수정·DB/AWS/Jira/배포/외부 전송/commit/push 없음. 기존 dirty 보존. 다음은 한 문서로 LC/Identity 공동 검토.


## 2026-10-14 — PLAN011 남은 기술안 LC·Identity 검토 접수

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. LC 첨부 전체·Identity 사용자 회신 검토, LC DeletionAccess/생성 saga 일부 읽기. 관련 PLAN011/012/013·TMI116/128/122/125·LC193/136.
- 변경: PLAN-011-REMAINING-TECHNICAL-REVIEW-RESPONSE-R2.md 신규·R1 후속 링크·CURRENT_STATE/본 기록. M1 pending/선보강, 삭제 checkpoint/기존 생성 복구 예외·exact 오류, phone 응답/수혜 예제 제안.
- 핵심 보정:120일 실행 상한/LC 새 issuedAt은 모든 증거 폐기 뒤 앱 과거 UUID 신규 재입력 차단 못함. 자동 tombstone 폐기 미승인·대안 비교, 보존 기간/개인정보 무기한 정책 확정 금지.
- 문서 링크/diff·원본 R2/fixture hash 확인. 코드 미변경이라 Gradle 미실행. Identity 코드/환경 미검증, LC 수정/DB/Jira/배포/외부 전송/commit/push 없음. 다음은 M/D/P exact 검토·V 최초key 증거와 폐기 계약 선택.


## 2026-10-14 — 재가입 paid-first 유지 및 나머지 단순화 승인

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. 사용자 인용 재가입 정책은 유지하고 나머지 단순화안을 채택한다는 요청 반영. 관련 PLAN011/012/013·TMI116/128/122/125·LC193/136.
- 변경: PLAN-011-SIMPLIFICATION-DECISION-2026-10-14.md 신규, 계획/설계/통합결정/기술안/회신/합의안 최신 승인 참조, CURRENT_STATE/본 기록.
- 생성 중 삭제 queue 대신 미접수 재시도, 앱 purchase-account MEMBER 보강,120일 멱등보장 및 terminal 상세120일 정리·기간후 UUID 신규처리 위험 수용 명시. 금융/소비/복원unique/unknown 증거는 별도 유지.
- 문서 링크/diff·원본 계약/fixtures hash 검사. 제품/DB/purge/LC/Identity/Jira/외부 전송/배포/commit/push 없음, 문서 작업이라 Gradle 미실행. 다음은 공동 exact 변경 동기화와 새로운 fixture.


## 2026-10-14 — LC·Identity 단순화 정책 동기화 접수

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. 사용자 전달 양측 회신을 근거로 정책 동기화 완료 접수, 타 저장소 독립 검증은 하지 않음. 관련 PLAN011/012/013·TMI116/128/122/125.
- 단순화 결정 문서 후속절/CURRENT_STATE/본 기록만 갱신. 이전 영구 key 보장 요구 해소·금융/소비/권한/미완료 및 Identity 탈퇴 보존 분리 명시.
- 다음은 exact 오류·120일 경계·phone snapshot/owner/복원 경합·새 계약/fixture 독립 검증. 문서 diff 검사, 문서 작업이라 Gradle 미실행. 제품/DB/fixture/LC/Identity/Jira/배포/외부 전송/commit/push 변경 없음.


## 2026-10-14 — 단순화 exact 계약 R1 작성

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. 사용자 요청에 따라 정책 재논의 없이 오류/120일 경계/phone 귀속·복원 경합을 문서화했다. 관련 PLAN011/012/013·TMI116/128/122/125·LC193/136.
- LC ErrorStatus/삭제 advice/BaseResponse/phone client/terminal retention 및 Billing ReserveCommand 읽기. 공개 result null은 생략됨 확인, 기존 code 재사용과 삭제 endpoint 안내 변경 구분.
- LC-BILLING-PAID-EXAM-SIMPLIFIED-EXACT-R1.md 신규·PLAN011/단순화결정 참조·CURRENT_STATE/본 기록. E/T/P/C19개 검증영역은 제작 명세이며 실제 fixture/테스트 통과 아님.
- 문서 링크/diff·원본 R2/fixtures-2 hash 검사. 제품 코드 변경 없으므로 Gradle 미실행. LC/Identity 수정/DB/Jira/배포/외부전송/commit/push 없음. 다음은 양측 exact 검토·수락 후 별도 fixture 생성/독립 검증.


## 2026-10-14 — MEMBER 대기·same-key 재개 exact draft-2

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. LC 회신의 stale PREPARED cleanup으로 앱 재개가 막히는 지적 반영. LC saga/reconciliation/operation 저장 코드 읽기 확인. 관련 TMI116/128/122/125.
- 단순화 exact 문서 draft-2·CURRENT_STATE/본 기록 변경. Billing 비종료 waiting receipt/status 및 LC waitingReason/lease CAS·cleanup/격리 제한적 재개·응답유실 E02-a~h 제안. 새 규격 검토 필요, 기존 offline 결과로 검증됐다고 보고하지 않음.
- 문서 링크/diff·원본 계약/fixture hash 확인, 코드 미변경이라 Gradle 미실행. LC/Identity 코드/DB/Jira/배포/외부전송/commit/push 없음. 다음은 대기 상태/status/재개 exact 공동 수락 후 fixture 검증.


## 2026-10-14 — exact draft-3 격리 예외 우선순위

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. LC 사용자 전달 후속 의견에 따라 NEEDS_REVIEW/retry 소진 행 겹침 해소. 관련 TMI116/128/122/125.
- exact 계약 draft-3 §3.4/E02-f 및 CURRENT_STATE/본 기록만 변경. MEMBER 대기→소진 영속 원인+현재 status/owner/lease/version/무예약·무Session 조건으로 좁은 예외, retry_exhausted 단독·불명/혼재 원인은 운영 확인.
- diff·문서 링크 검사, 문서 작업이므로 Gradle 미실행. 코드/DB/fixture/LC/Identity/Jira/배포/외부전송/commit/push 변경 없음. 다음은 수정 우선순위 공동 확인 후 fixture 명세 수락.


## 2026-10-14 — LC exact draft-3 이견 없음 접수

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. 사용자 전달 LC 회신의 격리 예외/E02-f 반영·이전 의견 해소 기록. 관련 TMI116/128/122/125.
- exact 문서 접수 후속절/CURRENT_STATE/본 기록만 갱신. 공동 최종 수락/fixture 생성/구현으로 확대하지 않음. 삭제 route/owner/자료별 보존은 별도 유지.
- 문서 diff 검사, 문서 접수로 Gradle 미실행. 제품/fixture/LC/DB/Jira/배포/외부전송/commit/push 변경 없음. 다음은 검토된 범위 공동 수락과 별도 fixture 제작·독립 검증.


## 2026-10-14 — 단순화 exact 한정 수락·새 fixture 제작

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. 사용자 「어 해줘」로 수락 기록/fixture 준비. LC 이전 회신은 검토 이견 없음으로 접수하고 신규 bytes 인수/독립 검증은 대기 표시. 관련 PLAN011/012/013·TMI116/128/122/125.
- 신규 수락/검증 문서, fixture10파일, Node 생성기·Python 독립 모형 검사기, PLAN011 참조·CURRENT_STATE/본 기록. 원 exact snapshot/계약 hash를 fixture에 포함, exact 원문은 변경하지 않음.
- 오프라인80벡터 통과, 상태28 미실행 표시·계약 traceability.5변조 거절·생성기 덮어쓰기 금지, 기존 R2 검사 통과. 최초 불완전 생성은 표 공백 차이 coverage 검사 실패로 중단 후 수정·최종 검증 완료.
- 실제 HTTP/worker/DB/Transaction·LC 독립 검증 미완료, 삭제 route/owner/최종 보존 제외. Java 변경 없어 Gradle 미실행. 코드/DB/LC/Identity/Jira/외부전송/배포/commit/push 없음. 다음은 LC 원본hash 인수·독립검증.


## 2026-10-14 — 단순화 fixture LC 독립검증 완료 접수

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. 사용자 전달 LC 결과10파일 동일·81검사/전체1,029 통과(포함), 실패/오류/skip0 접수. 관련 TMI116/128/122/125.
- 수락 기록/CURRENT_STATE/본 기록 갱신. LC 테스트 자체 재실행/코드 확인 없음. manifest 제작 당시 상태/fixture bytes 보존, 상태28 및 삭제 route/owner/보존 미완료 구분.
- diff 검사, 접수 문서 작업으로 Gradle 미실행. 제품/DB/fixture/LC/Identity/Jira/외부전송/배포/commit/push 변경 없음. 다음은 한정 범위 구현과 남은 경로 exact 준비, 실제 구현 승인은 별도.


## 2026-10-14 — PLAN-011 구현 기준·착수 범위 동기화

- 브랜치: feat/TMI-212-revenuecat-worker-reconciliation. 관련 PLAN-011/012/013, TMI-116/TMI-128/TMI-122/TMI-125.
- 요청: 양측 오프라인 검증 뒤 계획·ADR 동기화 및 구현 범위 정리 제안에 사용자 「해줘」. 이번에는 문서 동기화로 수행했으며 제품 구현/활성화 완료로 확대하지 않음.
- 변경: PLAN-011 본문의 오래된 LC 검증/phone 미확정 문구 갱신, §5.0에 착수 순서·검증·서비스 분담·보류 범위 추가. ADR-004 현재 결정과 AGENTS 우선 규칙, 저장 Transaction 설계의 MEMBER 비종료 receipt·120일·phone 보완 및 CURRENT_STATE 갱신.
- 구현 순서: 단일 ClientSession/schema/index와 OFF 구성 → MEMBER 증거·WAITING_MEMBER_EVIDENCE/status/replay → paid-first reserve/confirm/복원 unique/guard → LC 마무리·최소 이용 증거/조회 통합. 실제 코드·schema 설치는 수행하지 않음.
- 유지: 원 계약/fixture bytes, 기존 사용자/서비스 인증·status body·command header,120일 승인 위험, 재가입 target paid-first/복원1회. 원본 manifest false는 제작 당시 기록으로 보존.
- 검증: verify-paid-exam-simplified-fixtures-r1.py의80벡터/9파일hash 통과. git diff --check 통과. 문서 상대 링크 확인. Java 변경이 없어 Gradle/LC 테스트 재실행 없음; LC81/1,029는 앞선 사용자 보고. 실제 상태28·HTTP·Transaction 미실행.
- 위험/다음: 삭제 route/인증·과거 owner 삭제 coverage·자료별 purge 공동 계약, 이후 실제 구현의 replica-set rollback/unknown commit/경합 검증 필요. 운영 키/port/TLS·배포는 별도.
- 범위: 기존 dirty 변경 보존. 이번 변경은 문서6개(AGENTS, PLAN-011, 저장 설계, ADR-004, CURRENT_STATE, WORKLOG)뿐이며 예상 밖 변경 없음. LC/Identity·제품·DB·fixture·Jira·외부 회신·commit/push·배포 변경 없음.

## 2026-10-14 시험 생성 자격 거절 오류 안내

- 사용자 질문에 Billing Reservation exception/R2 계약과 LC ErrorStatus·BillingExamCreationSaga 매핑을 읽기 확인. 기존 402 ENTITLEMENT_INSUFFICIENT 및 사용자 문구 확인, 처리 중409/일시503과 구분하고 신규 refund409는 계약/후속 상태로 안내했다.
- feat/TMI-212-revenuecat-worker-reconciliation, CURRENT_STATE/본 기록만 수정. 코드·LC 문서·노션·Jira·배포 변경 없음. 정적 확인으로 Gradle 미실행·실환경 응답 미검증. 기존 dirty 보존·예상 밖 변경 없음. 프론트는 시작 시402에 조회 갱신/이용권 안내, 자동 반복 재시도 금지.


## 2026-10-14 — PLAN-011 Jira 구현 작업 우선 등록

- 브랜치 feat/TMI-212-revenuecat-worker-reconciliation. 사용자 「지라 먼저 생성해줘」 승인으로 TMI-137 하위 작업 TMI-213~217 생성.
- 일반 Rovo 검색 timeout 뒤 JQL로 기존 PLAN-011 검색 및 에픽/유형 확인. 기존 검색 결과는 PLAN010의210/211/212로 신규 구현 과제 중복 없음 확인.
- 213 공통 Transaction/schema,214 MEMBER 증거·영속 대기/복구,215 paid-first confirm/복원,216 LC 연동·실제 검증,217 삭제/owner/보존 공동 계약. 각 본문에 범위·완료조건·선행·고정 계약hash와 오프라인/runtime 경계를 명시.
- 생성 후5개 재조회로 parent137/해야 할 일/미할당 확인. 기존 이슈 본문·상태·담당·댓글 변경 없음. 신규 이슈 의존 순서는 본문으로 기록.
- Billing PLAN-011 및 CURRENT_STATE 링크/진행 상태 동기화. WORKLOG append. 문서 diff 검사, 제품 코드 변경 없어 Gradle 미실행.
- 코드/DB/배포·LC/Identity·동결 contract/fixture·commit/push 변경 없음. 기존 dirty 보존, 예상 밖 변경 없음. 다음 실제 구현은 TMI-213,217의 공동 계약/실환경 gate는 별도.

- [TMI-213](https://to-teacher.atlassian.net/browse/TMI-213): 공통 Transaction·저장 기반
- [TMI-214](https://to-teacher.atlassian.net/browse/TMI-214): MEMBER 증거·대기·같은 key 복구
- [TMI-215](https://to-teacher.atlassian.net/browse/TMI-215): 유료 우선 reserve·confirm·무료권 복원
- [TMI-216](https://to-teacher.atlassian.net/browse/TMI-216): LC 연동·이용 증거·실제 경합 검증
- [TMI-217](https://to-teacher.atlassian.net/browse/TMI-217): 삭제 전달·과거 owner·자료별 폐기 공동 계약

## 2026-10-14 개인정보처리방침 결제 보존·RevenueCat 확인

- 사용자 요청으로 Billing PurchaseCore/암호화참조/retention/cleanup 및 provider transport를 정적 확인. 거래·계정연결·상품/금액·권리시각 저장과5년 정책,120일 접수/완료sync 예외,금융전체purge 후속·삭제OFF를 구분했다. Billing RC 고객 DELETE 경로는 찾지 못했고 GET 전용 연동을 확인했다. 실DB/실배포/앱SDK 설정은 미조회.
- 국가법령정보센터 시행령6조의 계약/청약철회·대금결제/공급5년·불만분쟁3년 및 개인정보보호법28조의8, RevenueCat 공식 privacy/DPA/customer deletion 문서를 조회. 미국 저장·processor 역할·대시보드/API 고객 삭제 지원을 확인했으며 RC의 일반6년 문구를 최종사용자 보유기간으로 확정하지 않았다. 실제 SDK 수집항목·삭제/backup SLA 및 국외이전 법적 근거는 확인 필요.
- CURRENT_STATE/본 기록만 변경. 개인정보처리방침 게시·정책 확정·RC삭제·지원메일 발송·코드/외부계정/배포/Jira 변경 없음. 코드 미변경으로 Gradle 미실행. 기존 dirty 보존·예상 밖 변경 없음. 다음은 RC 보유/삭제 기간 확인 및 탈퇴 삭제와 환불재조회 보호 범위 결정 후 고지·구현 정합화.


## 2026-10-14 — TMI-213 공통 Transaction·유료 시험 저장 기반 구현

- 사용자 요청: 기존 feat/TMI-* 방식으로 브랜치 전환 후 구현. TMI-213 Jira 본문/완료조건을 읽고 feat/TMI-213-paid-exam-transaction-foundation으로 신규 전환, 기존 dirty 문서/fixture 보존. Git metadata/Gradle cache sandbox 제한은 승인된 실행으로 해결했다.
- 변경: PaymentStore.transactionWithTemplate/중첩방지 scope 및 Spring wrapping Mongo 오류 retry. MongoTransactionExecutor/EntitlementSnapshotExecutor 중첩 거절. Reservation/Allocation/Grant/Ledger/Group/Session repository에 MongoOperations 주입을 허용하여 동일 ClientSession 바인딩. 기존 무료 호출 방식 유지.
- 저장: EntitlementSourceSnapshot·기존 Reservation/group/session optional reader, PaidExamStore waiting receipt·immutable source/response CAS·guard 획득/확정/해제·FIFO, MEMBER restoration grant/ledger factory, paid-exam-foundation-r1 strict schema/index·명시 maintenance install/read-only verify. MEMBER owner epoch는 null·TRIAL만 실제 epoch 필수. 기존 grant/ledger unique 불변.
- 설정: billing.reservation paid-foundation-enabled/paid-admission-enabled 기본false. foundation ON은 schema 정확검증, admission ON은 후속 runtime 미완료로 기동거절. 공개 API/업무 wire/실제 paid resolver·confirm은 변경하지 않았다.
- 검증: 최종 ./gradlew clean test1,497개 실패0/오류0/skip0. 신규23개(단위3/실제 Mongo20) 포함. raw결제+6repo+guard/receipt 9단계rollback·동시CAS·중복복원/FIFO·늦은완료보호·source roundtrip·strictschema/TTL·중첩거절·unknowncommit 검증. 기존 simplified80벡터/hash 재검사와 diff/링크 통과. 기존 VM/deprecated 경고 유지.
- 중간 결과: session-bound API 호출 및 AssertJ 타입 추론 컴파일 수정, Mongo transient의 Spring wrapping을 재시도에서 놓치는 경합을 수정. commit91은 topology재탐색/2초기한 영향을 포함하므로 기존 방식의 code1+UnknownTransactionCommitResult로 commit재시도만 검사. 최종 테스트는 모든 수정 뒤 재실행했다.
- 문서: TMI-213 runbook 신규, AGENTS/ADR004/PLAN011/CURRENT_STATE 동기화, WORKLOG append. source factory는 자격 판단기가 아니며 후속 업무Tx에서 owner/account/purchase fence를 반드시 구현. schema 설치/strict 기존 collection paid writer 전환·운영 rollback은 별도.
- 범위/위험: 실제 계약28개 전체 실행·LC/Identity 수정·운영 DB·외부 HTTP·배포/활성화·Jira 상태/댓글·commit/push 없음. 기존 dirty를 보존했고 예상 밖 변경 없음. 실제 유료 선택·confirm/phone/환불/삭제 통합은 미완료다.
- 다음: TMI-214 MEMBER 증거·대기/API/status 복구 →215 paid reserve/confirm/복원 →216 LC 연동·검증.217 삭제/과거owner/자료별purge는 별도 공동계약. 운영키/port/TLS/DB 설치 및 판매활성화는 별도 승인·검증이다.

## 2026-10-14 — TMI-213 병합·TMI-214 MEMBER 증거 및 operation 복구

- 요청/목표: 사용자 「커밋 푸시 머지한 다음 브랜치 파서 다음꺼 구현」. 기존 TMI-213 변경을 3d5660f833f21464d6d3a8aef5b3a231f3d6f0e6로 commit/push하고 PR17을 develop에 merge(bbdd8f4c90dc9149802269afe1bf0fa2465900b8). 로컬 develop fast-forward 후 feat/TMI-214-member-evidence-operation-recovery 생성. 이 승인으로 해당 commit/push/merge를 수행했으며 후속214 변경은 로컬에 남긴다.
- 이슈: TMI-214 완료조건을 읽고 MEMBER evidence·대기·same-key recovery 단위를 구현. Jira 상태/댓글 변경 없음.
- 변경: VerifiedMemberEvidence/MemberEvidenceConfiguration 신규, PaymentAccountService/Controller/Properties에 opt-in 증거 writer. 검증 sub·trusted issuer/payment environment·MEMBER·iat/verifiedAt만 저장하고 raw JWT 없음. 신규/기존 ref 안정성, evidence/lifecycle/reference/cursor 원자성 및 WITHDRAWN 우선 유지.
- 복구: MemberOperationRecovery 신규. 현재 account/lifecycle/recovery epoch→기존 owner→digest/replay→MEMBER 조건 순서. 최초 성공/source immutable replay, waiting commit 후503·status read-only·사용자 same-key reserve callback만 재개. shared ClientSession Admission 경계에서 실제 Reservation 확인 후 receipt CAS, J3 pre-creation conflict는 영속 commit 후409/status 전용409. 자연 account version 증가는 owner변경으로 취급하지 않는다. 실제 paid resolver/confirm/복원/phone 선택 연결은215이며 기존 trial reserve를 MEMBER로 강제하지 않는다.
- 저장/API: PaidExamSchema recovery-r2는 v2 requestSnapshot 필수, v1 보존·자동 snapshot 발명 금지. 명시 maintenance upgrade 외 startup mutation없음. noTTL/terminal+120일·millisecond ceiling, 잔존 결과는120일 이후도 재현. ReservationLifecycleService status body 조회 유지, 유료 receipt의 source만 optional entitlementSource로 반환하며 legacy 무료 응답은 생략. pending/owner/J3는 기존 오류4-field/Retry-After 규칙 유지.
- 검증: 신규26개(실Mongo MemberOperationRecoveryIntegrationTest19, evidence 단위3, public account1, status HTTP2, startup1) 추가. 최종 ./gradlew clean test JUnit XML1,523개 failures0/errors0/skipped0. 실제 Mongo rollback·동시 same-key·탈퇴/evidence 및 탈퇴/admission 경합·unknown commit·r1 upgrade·command부재/120일·source 모순 검증. 전체 실행 중 기존 PurchaseCoreIntegrationTest.parallelDistinctTransactionsAndDuplicateDeliveryConverge에서 PaymentStore Mongo/retry 오류 경로의 일시503이1회 발생; 해당 테스트 단독통과 후 최종 전체재실행통과. 정확한 원인은 추가로 확정하지 않았고 기존 재시도 한도나 테스트를 완화하지 않았다. 기존 deprecated 경고 유지.
- 원본 검증: python3 scripts/contracts/verify-paid-exam-simplified-fixtures-r1.py OFFLINE_CHECKS_PASSED(hashFiles9, 오류22/공개4/시간14/MEMBER29/phone11=80). state28/http 미실행 표기 유지. docs/contracts 및 src/test/resources/contracts 변경 없음. git diff --check 통과.
- 문서: TMI-214 runbook 신규, AGENTS/ADR004/PLAN011/CURRENT_STATE 동기화와 본 기록 append. 별도 작업의 RevenueCat 수동 삭제 기한 CURRENT_STATE/WORKLOG 추가를 보존했다. 이번 구현 자체에 예상 밖 변경 없음.
- 남은 범위:214 admission boundary를215 paid resolver/confirm/복원/원trial owner·guard와 연결,216 LC lease/cleanup/격리 예외·전체28시나리오,217 별도삭제/owner/purge. flags기본OFF, paid-admission ON기동거절 유지. 운영schema/실DB/LC·Identity저장소/실환경/배포·활성화 변경 없음. LC strict reader 선배포·운영키/port·보존 gate 전 활성화하지 않는다.

## 2026-10-14 — TMI-214 병합 및 TMI-215 유료 예약·복원 구현

- 요청/브랜치: 사용자 commit/push/merge 후 다음 구현 요청. TMI214 commit `3691f9882c3a8e21c0223fe2aee987ba984749dd`, PR18 develop merge `ebaf8d88644a6e7ac70970da5dbe63b049a26d76`; 원격 확인·PR attachment 완료. `feat/TMI-215-paid-reservation-restoration`는 병합된 origin/develop에서 생성. 별도 RevenueCat 문서를214 commit에서 제외하고 보존했다.
- 선행: Jira TMI215 완료 조건 읽기 및216 경계 확인. 이슈 상태/댓글/담당은 변경하지 않았다. 승인된 R2/simplified exact·저장 설계·ADR을 구현 기준으로 사용했다.
- 변경: PaidReservationService/PaidTrialFunding·RestartContext/ExamTransition, source/previous/confirmRejection과 AttemptGroup/Session 차단·완료 필드, session-bound trial 저장소, reserve decoder/hash·status DTO, PaidExamStore/Schema와 payment ledger r5 명시 이관, 관련 테스트·runbook·PLAN011/ADR004/AGENTS/CURRENT_STATE.
- 동작: 기존 owner→동일 key replay 우선, source 고정, paid-first·무기한 무료 FIFO, reserve 실패/취소/만료의 원 시험 보존. confirm·복원·원 group 재응시 차단·새 current·원장/receipt는 동일Tx. 최초 trial 소비 원장 증명 및 소비당 unique 복원1회, phone target paid INITIAL/no transition/원 mock echo. 기존 무료 재응시는 현재 VERIFIED/candidate→Claim·retention 확인, MEMBER 복원권은 별도 owner 증거. 응답 유실/unknown commit·환불 선행 영속 거절·성공 replay 유지.
- 검증: 최종 clean test 1,546개(신규23: 실Mongo22+strict wire1 포함), 실패/오류/skip0. frozen simplified80벡터/hash9파일 및 R2 canonical18/clock5/invalid13/token31/HTTP판정12/wire6/status3/hash13파일 모두통과. fixture bytes 수정/재생성 없음, git diff --check 통과. 중간 테스트 source 컴파일 실수는 수정 후 검증했으며 assertion 완화·재시도 한도 변경 없음.
- 위험/범위: 새 서비스는 비활성 내부 Transaction 구현. HTTP admission·계정 없는 Guest 경로 결합·상태 이벤트 MEMBER owner·guard 완료·reader/expiry worker 배선은216, lifecycle 삭제/보존217, 실제 refund/outbox는012·운영013 gate다. paid-admission ON 기동거절 유지. 환불 상대는 테스트 Transaction이며 provider/refund 전체 구현 검증이 아니다. 실제28상태 전체·LC/Identity/E2E 통과로 합산하지 않는다.
- 외부 계약: status body userId/operationId와 command header 유지, 기본OFF·기존 사용자 API/A·고정fixture 유지. 원 운영 schema 수정/키/Secret/외부 서비스 호출·배포/활성화 없음. 신규215는 commit/push하지 않았다.
- 예상 밖 변경: 별도 작업의 RevenueCat/PLAN012 계획·이력·LC 인계서와 해당 CURRENT_STATE/WORKLOG 항목이 dirty 상태이며 본 작업은 이를 수정/포함하지 않고 보존했다. 나머지 코드 변경은 위215 범위다.
- 다음:215 diff 검토/커밋 후216 HTTP·reader·이벤트/expiry 및 LC 실제 finalize 검증. 운영 설치 전 r3/r5 명시 maintenance와 reader-first·구 writer drain,217/012/013 gate 확인.

## 2026-10-14 — TMI-215 커밋·PR19 병합 및 TMI-216 Billing 연결 구현

- 요청/브랜치: 사용자 「좋아 커밋 푸시 머지한 다음 브랜치 파서 다음꺼 구현해줘」. TMI216 Jira 읽기 후 승인 범위로 작업. 215 commit34ad2f1/push·PR19 생성/첨부·head 확인 merge5d16ba4 완료. feature branch는 `feat/TMI-216-lc-paid-exam-integration`; 이번216 변경은 미커밋/미푸시다.
- 보존: 215 commit은 해당 구현·기록만 선택했고 CURRENT_STATE/WORKLOG의 병행 RevenueCat/PLAN012 부분은 index-only 구분으로 제외했다. 기존/동시 PLAN012 계획·history·LC/Identity 인계·P0/단순화검토와 문서 이력은 유지했다. WORKLOG 과거 내용 변경 없음.
- 구현: PaidExamRouter·PaidAttemptEventService·PaidExamQueryReader 신규, PaidExamConfiguration/기존 ReservationController·lifecycle·ReserveService·PhoneContinuationService/상태event/query service 연결. source별 owner/lifecycle·기존 phone exact fence·inbox/상태/guard/최소구매이용원장 단일ClientSession, late completion/new guard 보호, 유료기간·무료권/FIFO 수량·재응시/pending snapshot read, legacy command replay·sourceaware OFF writer 차단. 저장소 dispatch 실패도 기존503, 차단된 trial phone discovery 제외.
- schema: PurchaseCoreSchema r6 strict EXAM_EVENT/EXAM_STATUS union, exact r4/r5→r6 명시maintenance·ALL_BLOCKED/격리 조건, r5 재실행/기존원장 보존. SubjectOwnerRebindRepository는 MongoOperations로 동일 session 참여. 자동DDL/index삭제/실DB이관 없음.
- 문서: AGENTS/ADR004/PLAN011 동기화, TMI216 런타임 안내·공동28대응표 신규. LC HEAD001b575 및 P2/P2.1/P3진행은 읽기 확인만. E06-owner message/T04 operation120일 단일보관 서술 보완을 공동gate로 기록, 원본 fixture 변경 없음.
- 검증: 최종 `./gradlew clean test` **1,570개, 실패0/오류0/skip0**, 신규24개 포함(런타임20·r5→r6 migration1·legacy rollback1·storage dispatch1·phone discovery1). PaidReservationIntegrationTest의 기존22개는 shared test support로 helper만 추출. 실Mongo7.0.14 replica set·기존실서비스/서명 JWT MVC·기본 security 회귀. 개발 중 추가테스트의 HTTP503/UTC밀리초 기대값과 Mockito 중첩 초기화를 수정한 뒤 선별/전체 재실행 통과. 기존 deprecated 경고 유지.
- 무결성: frozen simplified 검사 hashFiles9/벡터80 및 R2 hashFiles13/canonical18/clock5/token31/invalid13/HTTP모형12/wire6/status3 통과. 이는 실제상태28 실행통과가 아니다. doc 상대링크/대응표메서드 참조·git diff --check 통과. config/fixture bytes 변경 없음.
- 경계/다음: Billing 계정기반 연결 검증이며 LC finalize/lease/cleanup/Summary/삭제·보존 및 전체28 공동PASS 아님. Guest/legacy source coverage·구worker drain, LC P3~P6/공동E2E, TMI217 삭제/과거owner/purge, PLAN012 provider환불/revoke와013 실환경gate 필요. 기본OFF, 실서비스/TLS/ALB/Store/DB/배포/활성화·LC/Identity수정·Jira쓰기·외부회신 전송 없음. 기대 범위 밖 제품파일 변경 없음; 병행 문서변경은 이번 구현과 별개다.


## 2026-10-14 — TMI-216 E06-owner fixture 문구 정정

- 날짜: 2026-10-14 / 관련 TMI-216 / 브랜치 feat/TMI-216-lc-paid-exam-integration
- revision: `lc-billing-paid-exam-simplified-exact-r1-fixtures-2`
- 변경: E06-owner message를 `The operation owner does not match the current owner.`로 정정했다. HTTP409·OPERATION_OWNER_MISMATCH·retryable=false·기대 OWNER_BLOCKED는 그대로다.
- 기존 fixtures-1의 10파일은 작업 전후 SHA-256이 동일하다. 새 버전의 데이터 차이는 해당 message 하나이며 README/revision/manifest/hash만 함께 갱신했다. 계약 snapshot과 나머지 데이터 파일은 byte 동일하다.
- 생성기를 수정하고 검사기에 owner message exact 비교를 추가했다. 검사기 기본 대상은 fixtures-2이며 구 revision을 검증 성공으로 표시하지 않는다.

## 전달 자료와 해시

- 원본: `src/test/resources/contracts/lc-billing-paid-exam-simplified-exact-r1-fixtures-2`
- ZIP: `build/handoffs/lc-billing-paid-exam-simplified-exact-r1-fixtures-2.zip` (build 정리 시 삭제될 수 있음)
- SHA256SUMS 자체 SHA-256: `4e91332dfa549da63d13f3e07a1ec208018e02a353ccd780f12b8570eb2d4845`
- ZIP SHA-256: `206c83a6ba8865650a07db3a56f67b210a0ad795d8b123cce811ef10ab2c6cb2`
- 계약 SHA-256: `79bcfe3d79225c21d712791266bea10d4aed9eed3dc0529ddf86f7b29673a482`

## 검증 결과와 다음 단계

`python3 scripts/contracts/verify-paid-exam-simplified-fixtures-r1.py`: errors22/public4/time14/member29/phone11 = 80벡터 통과, 9파일 hash 모두 OK. 기존 잘못된 E06-owner는 CONTRACT_ERROR, 정정본은 OWNER_BLOCKED로 판정되는 회귀 확인. ZIP10파일을 원본 bytes와 재대조했다. 상태28개는 미실행 유지다.

LC는 새 ZIP/hash를 대조하고 자체 독립 검증을 재실행하면 된다. 생산자 manifest의 lcIndependentValidation=false는 유지한다. 이번에는 fixture/도구/기록만 변경해 전체 Gradle은 재실행하지 않았다. runtime 오류를 발견·수정했다는 의미가 아니며 정책/API/제품 코드/DB/배포/타 저장소/외부 발송/commit/push 변경 없음. 기존 TMI-216 및 PLAN-012 작업을 보존했고 예상 밖의 이번 변경은 없다. T04와 다른 공동 gate는 별도다.

## 2026-10-14 — TMI-216 병합·TMI-217 삭제/owner/보존 공동 계약 준비

- 요청: 사용자 커밋·푸시·머지 후 다음 브랜치 작업 지시. TMI216 commit `780e677c6da9c7a10aae5112fa6c8c0fdda8e0d8`, PR20 https://github.com/Too-Much-I/app-back-end-billing/pull/20, develop merge `89ae574acc8f51028e4e53f662facac41dfcfc36` 확인. PR attachment 완료. remote head/CLEAN/CodeRabbit SUCCESS 확인 뒤 exact head로 merge했다.
- 보존: 216 구현/fixtures-2/해당기록만 커밋. 병행 PLAN012/RevenueCat 문서와 CURRENT_STATE/WORKLOG 부분은 index-only 분리해 제외하고 작업트리에 유지했다. 새 `feat/TMI-217-deletion-owner-retention-contract`는 병합된 origin/develop에서 생성했다.
- 다음 범위: Jira TMI-217 읽기에서 제품코드 아닌 공동계약 준비 과제임을 확인. LC-BILLING-EXAM-DELETION-RETENTION-R1 및 TMI-217-DELETION-OWNER-RETENTION-VERIFICATION 신규, PLAN011/CURRENT_STATE 동기화. 신규POST/별도목적audience/기존LC키·connector/OFF gate,7field·exact오류·최소creationOwner·Session-only 과거owner 삭제·현재group차단·phone/생성/confirm양순서·삭제후INITIAL·자료별보존표를 제안했다.
- 단순화/미확정: 다른eventId 동일Session은409로 제안해 별도alias저장 제외(종전 미수락204후보와 차이 명시). LC 수락/actorcoverage/최종deny·무기한권리의개인연결제거/운영책임·새fixture는 후속. MEMBER/120일/paid-first/원소비복원1회 정책 유지, 영구key 요구 없음.
- 조사: Billing Session에불변actor없음·phone source fence는짧은보존·MEMBER account version≠epoch·현재deleted 일괄conflict 및group-level deny 확인. LC HEAD001b575 DeletionCommandService/Worker를 읽기만 확인, 상대진행/배포전체증거 아님.
- 검증: 기존 XML1,570/실패0/오류0/skip0 확인, simplified80/hash9 및R2검사 재실행통과. 새문서는링크·케이스ID·git diff --check 검사, 제품변경없어Gradle미재실행. 새 W/A/O/R/V는미실행 계획이며 runtime/fixture PASS로 보고하지 않는다.
- 경계/다음: 이번217제품code/schema/DB/새fixture/LC·Identity/Jira쓰기/외부회신/배포/활성화 변경0. 기존A/R2/simplifiedbytes불변, 이번 예상밖변경없음. 공동회신→범위수락→새fixture/hash·양측독립검증→별도제품구현/실경합/운영gate. 217문서는 로컬 미커밋이다.

## 2026-10-14 — TMI-216 fixtures-2 LC 독립 재검증 접수

- 목표/브랜치: 사용자 LC 회신 접수, feat/TMI-217-deletion-owner-retention-contract. LC 보고서 원문 읽기·Billing 원본 SHA256SUMS/계약/error-cases hash 직접 대조, ZIP hash는 인계 기록과 대조했다.
- 결과: E06-owner 문구 불일치 해소. LC oracle82(80벡터 포함), actual-client49(오류22 포함), 전체1,149 실패0/오류0/기존skip1은 LC 보고 근거. Billing에서 LC 테스트 재실행/전체재합산 없음. 실제 client는loopback, Billing 서버/TLS/JWT chain/DB/E2E 증거 아님.
- 변경: fixtures-2 인계 후속, TMI216 런타임/상태대응표, TMI217 검증계획, CURRENT_STATE 및 본 기록. 기존작업/동시PLAN012 문서 보존. fixture/manifest bytes·제품/API·DB·배포·타저장소·Jira·외부전송·commit/push 변경0.
- 검증: hash3개 대조·git diff --check. 문서만 변경해Gradle미실행. 이번 예상밖변경없음. 다음: TMI217 공동계약 검토 및 상태28/T04/삭제보존·실환경 활성화gate 별도 검증.

## 2026-10-14 — TMI217 PR21 병합·LC 기술 인계

- 사용자 커밋/푸시/머지 후 다음작업 요청으로 계약안/검증계획·LC fixtures2 접수만 commit ee4537c28909dd7635ea07ad31a9e5c834a2ced0, PR21 https://github.com/Too-Much-I/app-back-end-billing/pull/21 생성/첨부·exact head/CLEAN 확인 후 develop merge2e646cb2262f7e438999bfc7beb92cf2298fabb1 완료. 체크목록은 비어있었으며 CI통과로 표현하지 않는다. 병행PLAN012/RevenueCat 기록은 index-only 분리해 보존했다.
- 새브랜치 feat/TMI-217-lc-deletion-handoff 생성. 사용자 정책기확정 지적에 따라 제품정책 재선택과 기술규격 공동확인을 구분하고 LC 인계자료를 작성했다. 생성중삭제거절/미복원/권리보존/120일·paidfirst 변경0.
- 변경: TMI-217-LC-TECHNICAL-HANDOFF/CURRENT_STATE/본기록. 링크/diff 검사, 제품변경없어Gradle미실행. LC외부발송/타저장소/새fixture/제품/DB/배포/Jira쓰기 없음. 기존dirty보존, 예상밖변경없음. 다음은LC규격검토→fixture/독립검증→제품구현. 인계자료는 로컬미커밋.

## 2026-10-14 — TMI217 LC 회신 대조·draft2 보완

- 요청: 사용자 LC 검토 회신 확인. feat/TMI-217-lc-deletion-handoff에서 Billing 정본과 LC HEAD136602a 현재작업트리의 UserMergedTransactionService/DeletionCommandService/DeletionWorker/DeletionOperation/BillingWorkloadTokenIssuer 읽기 대조. UserMerged는같은Session userId이전, 삭제는접수후생성대기·prepare후새학습허용, signer두목적 확인. 정적검토이며LC테스트/실환경검증 아님.
- 수정: 정본draft2의 phone/UserMerged분리·검증된merge/현재귀속 조건·trial한정, 단계분리 구현차이·eventId보존coverage 명시, LC sender/Billing receiver 및24시간200회격리우선 문구. 검증계획W11/O09/O10 추가(48개), 인계/CURRENT_STATE/본기록. 제품정책 재선택없음·전체공동수락아님.
- 검증: 상대링크·48개ID중복·diff검사. 문서만수정해Gradle미실행. 기존dirty보존, 예상밖변경0. 제품code/LC·Identity수정/fixturebytes/DB/배포/외부전송/Jira/commit/push 없음. 다음: 수정안 LC재검토·merge귀속/eventId보존coverage 확인 후fixture/제품구현.

## 2026-10-14 — TMI217 단일 UserMerged 확인 승인

- 사용자 「어 그렇게 하는 걸로 하면 될 거 같아」로 같은Session 게스트→회원 단일이전 확인·연속병합 계보추적 제외 승인. feat/TMI-217-lc-deletion-handoff에서 정본draft3/검증계획O08·O09/인계/CURRENT_STATE/본기록 반영.
- phone 재가입 별도, 단일증거+현재귀속 일치시 삭제 인정. 지연은 원event재확인·소실/불일치 운영확인, 기존retry/격리 유지. 원문정리 독립·필요ACK전 신규생성차단 유지. 별개Session의 개별이전까지 금지하지 않음.
- 링크/48개ID유일성/diff검사, 제품변경없어Gradle미실행. 제품/fixture/타저장소/DB/배포/Jira/외부발송/commit/push 변경0. 기존병행작업보존, 예상밖변경0. 공동기술coverage검토·fixture/제품구현은 후속.

## 2026-10-14 — PR22 병합·Session 생성 owner 내부 기반

- 요청/브랜치: 사용자 commit/push/merge후다음구현. 계약보완 commit01d1350e96c40565f7697ddbec73640bce6cd0d9·PR22 https://github.com/Too-Much-I/app-back-end-billing/pull/22 생성/첨부, CLEAN/CodeRabbit SUCCESS/exacthead 확인후 develop merge3b00a6113e2838727eb813b1dcceb5d732fa8098. 새 feat/TMI-217-session-owner-evidence 생성. 별도PLAN012기록 index-only 제외·보존.
- 범위: Jira217 읽기(계약준비 과제) 확인. 이번사용자 후속구현요청에 따라 외부wire와독립된 Billing 내부 생성근거만 구현했으며217전체완료/삭제계약동결로 표시하지 않음.
- 변경: SessionCreationOwner immutable record, AttemptSession optional creationOwner, PaidReservationService의검증된Transaction writer. paid/MEMBER grant는account/null epoch, trial은subject/검증epoch. 최초근거불변·legacy부재unknown/backfill0·로그reference가림. 단일merge현재삭제권한판정/새route/Guest구writer/최종보존은미구현.
- 테스트: 선별실Mongo5개통과, 최종 ./gradlew clean test1,577개 실패0/오류0/skip0(신규통합5+단위2 포함). 생성원자성/receipt실패rollback·confirm/replay/계정refresh불변·trial link변경후과거근거유지·legacy부재·epoch종류검증. 최초sandbox Gradlecache lock제한은승인실행으로해소. 기존deprecated경고유지. simplified80/hash9·R2검사·문서링크/diff통과.
- 문서: 안내/PLAN011/ADR004/CURRENT_STATE/본기록. 자동DDL/validator/index설치·운영DB·LC/Identity·fixturebytes·Jira쓰기·외부발송·배포/활성화변경0. 기존paid flags OFF 유지. 운영attempt_sessions별도validator/reader coverage·개인연결보존은rollout gate.
- 다음: 수정계약 공동coverage와단일merge/eventId증거확인, Guest/legacy writer범위확정후삭제receiver/ACK/경합구현. 현재근거는최초귀속일뿐삭제권한허용아님. 병행012문서보존·이번예상밖변경없음. 새구현은로컬미커밋/미푸시.

## 2026-10-14 — RevenueCat 수동 삭제 기한 권장

- 목표: 탈퇴 후 필요한 처리 뒤 수동 삭제하는 운영안의 기한 제안. 확인한 현재 브랜치 feat/TMI-214-member-evidence-operation-recovery의 기존 변경은 보존하며 CURRENT_STATE/WORKLOG만 기록한다.
- 검토: 개인정보보호법21조의 불필요한 개인정보 지체 없는 파기 기준 확인(https://www.law.go.kr/lsLinkCommonInfo.do?lsJoLnkSeq=1027063705). 일반 건 즉시 착수·5영업일 이내 내부 처리목표, 실제 미결 거래/환불 건30일 이내 최종 검토·처리를 권장한다. 법정 유예기간이나 RevenueCat 완전삭제 SLA로 주장하지 않는다.
- 결정/위험: 사용자 승인 전 제안이며30일 일괄보유·무기한 미결연장을 허용하지 않는다. 예외는 구체적 법적 근거/필요·최소 항목·담당자/다음 기한을 정하고 목적 종료 시 지체 없이 삭제. Billing 기존15일 연결 정리와5년 금융기록 정책은 별개로 유지한다.
- 검증/범위: 문서 기록만, 코드 미변경으로 Gradle 미실행. 외부 고객삭제·정책 게시·Jira·배포·commit/push 없음. RC 비동기 삭제/백업 SLA 미확인. 다음은 사용자 기한 선택 후 수동 운영 안내 정합화.



## 2026-10-14 — PLAN-012 환불 선행 설계 보완

- 요청/목표: 진행 중인011과 병행해012 계획 구체화. 작업 도중 현재 브랜치가 feat/TMI-214-member-evidence-operation-recovery에서 feat/TMI-215-paid-reservation-restoration으로 변경된 것을 관측했다. 이 작업은 branch 전환/코드수정을 하지 않았고 동시011 변경을 보존했다.
- 변경: PLAN-012 본문 갱신, PLAN-012-HISTORY-BEFORE-REFRESH-2026-10-14에 이전 전문 보존, CURRENT_STATE/WORKLOG 기록. 최신 정책/R2/한정수락/011·013과 PurchaseCore reversal 경로를 정적 대조했다.
- 계약: confirm 선행 exact Session 보호·불변 group revoke·전용 역방향 JWT·appliedAt reflow·부분 환불 잔여권 종료 유지. 종전 GRADING-only/채점 승인 API/SigV4/version revoke는 현재 계획에서 제거. source/공통Tx/guard/이용증거의011 분담과 P0~P4·테스트·판매전 활성화 표 명시. 고정 계약/fixture bytes 수정 없음.
- 결정/미확인: 금융 source 매핑·schema/index·worker exact는 P0 후속이며 provider 실제 부분 환불 지원/양측 runtime 미검증. RC5영업일/30일 삭제안은 추천 상태로 유지. 장기금융/RC삭제 구현·정책 변경 승인 아님.
- 검증: 문서 diff/상대 링크와 이력 원문 보존 검사 수행. Java 변경 없는 계획 작업으로 Gradle 미실행. Jira 생성/수정·외부메시지·Store 조회/환불·LC/Identity·DB·배포·commit/push 없음. 이번 작업 변경은 위 문서4개이며 별도 진행 코드 변경은 건드리지 않았다.
- 다음: 사용자 계획 검토 후012 P0 exact를 작성하고 구현 단위/Jira 별도 승인. 011 TMI-215 공통 경합·216 실제 통합·217 삭제보존과 연결, 판매 활성화는013 별도 gate.


## 2026-10-14 — PLAN-012 LC 환불·Session 인계

- 요청/목표: 환불 선행/confirm 선행 및 같은 그룹 재응시 차단을 LC에 전달할 문서 작성. 현재 feat/TMI-215-paid-reservation-restoration, 별도 진행 중인011 코드 변경 보존.
- 변경: docs/contracts/PLAN-012-LC-REFUND-SESSION-HANDOFF-2026-10-14.md 신규, PLAN-012 링크·CURRENT_STATE·WORKLOG 갱신. exact S1 보호/새 S2 차단/응답유실 replay, 서비스 분담·LC 회신 항목·H01~13 공동 테스트 정리.
- 유지 계약: 기존 confirm 성공/환불 거절 replay·R2 불변 event/group 두409·역방향JWT·lifecycle 우선·기본OFF. 신규 wire/기한/오류·이어풀기 기능 없음. 프론트/문의 운영 요지는 포함하되 외부 문서는 수정하지 않음.
- 검증: 문서 상대 링크·diff 검사. 코드 미변경으로 Gradle 미실행, LC 코드 독립 검증/외부조회 미수행. 고정 fixture/정책·Jira·DB·배포·외부 메시지·commit/push 변경 없음. 이번 범위 밖 동시 코드 변경은 보존했고 문서4개 외 직접 변경 없음.
- 다음: 사용자가 LC에 공유 후 실제 구현 위치·상태/테스트·보존 미확정 회신 확인,012 P0 및215/216/217 접점 정리. 인계서 작성은 상대 수락·활성화 완료가 아님.


## 2026-10-14 — LC 환불 인계 검토 회신 접수

- 근거: 사용자 전달 LC 회신. feat/TMI-215-paid-reservation-restoration에서 PLAN-012/LC 인계서/CURRENT_STATE/본 기록만 정합화하고 진행 중 코드 변경 보존.
- 반영: 정책 일치·P2 거절 응답/증거 기반 구현 보고와 P2.1→P3 다음 순서, P4 보호Session/P5 수신/P6 보존 후속을 구분. 단일 group revoke 문서로 논리 inbox/deny 역할 통합, 별도 inbox 추가 금지. eventId/group 유일성과 원자성·최초 보호 대상 불변 유지.120일 inbox TTL을 단일 문서 전체 삭제로 전용하지 않음.
- 검증/한계: 문서 diff 검사, H01~13 공동 검증 미완료. LC 코드/테스트 독립 실행 없음, Java 변경 없어 Gradle 미실행. 고정 계약/fixture bytes·Jira·외부메시지·DB·배포·commit/push 변경 없음. 이번 범위 밖 코드 변경은 보존.
- 다음: LC는 기존 P2.1→P3, Billing은011 및012 P0 기술 상세 진행. 두 서버 단계 번호를 혼동하지 않으며 P5/P6 실제 수신·보존 완료 전 활성화하지 않음.


## 2026-10-14 — LC 환불 인계 재검토 3점 보완

- 목표/근거: 사용자 전달 재검토 의견을 feat/TMI-215-paid-reservation-restoration에서 반영. LC 인계서·CURRENT_STATE·WORKLOG만 수정, 기존 코드/문서 변경 보존.
- 내용: 현재 LC P2는011 reserve/status 읽기·검증·증거 및 confirmRejection 해석이며012 수신/Session 보호 착수가 아님을 명시. 앱 이탈·통신/일시 채점 실패와 lifecycle 종료 확정 구분. same-key 복구120일 보장/lifecycle 한계·삭제/종료 재생성 금지, 과거 결과 replay와 현재 이용 허용 구분. H03/H09 테스트 기대값 정합화.
- 유지/검증: 단일 group revoke·금융/receipt/보호 증거 보존 구분·고정wire/fixture 불변. 문서 diff 검사, 코드 미변경으로 Gradle 미실행. LC 독립 코드검증·Jira·외부메시지·DB·배포·commit/push 없음. 이번 직접 수정은 문서3개이며 범위 밖 변경 없음.
- 다음: LC 회신/구현 검증은 별도, 기존011 진행과012 P0 준비를 분리 유지. 인계로 후속 구현/활성화를 승인하지 않음.



## 2026-10-14 — PLAN-012 복잡도·단순화 검토

- 요청: 일부 위험을 수용해012 단순화 가능성 확인. feat/TMI-215-paid-reservation-restoration에서 계획/현재 PurchaseCore reversal 경계 정적 검토, 실제 환불 구현 완료와 구분했다.
- 변경: PLAN-012-SIMPLIFICATION-REVIEW-2026-10-14 신규 및 CURRENT_STATE/WORKLOG. provider별 금액근거 고정·모호한정정 기존case·최소job/기존worker/운영도구 재사용 권장. 기존 LC 단일문서/채점API 제외는 이미 반영된 단순화로 구분.
- 유지/위험: 승인 전 제안으로 계획/코드 변경 없음. 실제누적액 제공 미확인·운영검토지연·대량처리속도 tradeoff 명시. root/confirm CAS·동일Session 보호·멱등원장·reflow·durable전달/핵심검증 유지. 금전사실 누락/수동DB정정 허용 아님.
- 검증: 문서diff/링크 확인, 코드미변경으로 Gradle 미실행. provider실조회·LC/Identity·Jira·배포·commit/push 없음. 진행중215 변경보존, 이번문서3개 외 변경없음. 다음은 사용자 선택 뒤012 P0의 최소구현범위 정리,011 계속 진행.


## 2026-10-14 — PLAN-012 최소 구현 방향 승인 반영

- 요청/브랜치: 사용자 「어 그렇게 하자」. 현재 feat/TMI-216-lc-paid-exam-integration이며 이번 작업에서 branch 전환 없음. PLAN-012/단순화검토/CURRENT_STATE/WORKLOG만 변경, 동시011 구현 보존.
- 결정: 팀 수동심사 유지·실제검증 플랫폼별 결과 고정매핑·불명확한 금액/귀속 durable case+Slack·root 먼저 차단 후 작은묶음 순차처리·전용관리화면 제외. 전액/부분 실제검증으로 자동화범위 확인, 예외빈도 낮다고 가정하지 않음. 중복알림은 자동 멱등처리.
- 보호/미확인: confirm/refund 원자성·Session/lifecycle·다른권리·reflow·outbox/lease/CAS/재시도 유지. 귀속불명 임의차단·금액발명·반환사실누락 금지. 실제 provider증거/운영담당·적체기준/exact는 후속.012 착수/실거래/공동테스트/배포승인 아님.
- 검증: 문서 diff 검사, 코드미변경으로 Gradle 미실행. LC/Identity·고정fixture·Jira·외부메시지·DB·commit/push 변경 없음. 다음은011 계속 진행,012 P0에서 최소 지원경로/검증표 정리.


## 2026-10-14 — PLAN-012 P0 기술 초안·Identity 문의 정적 확인

- 요청/브랜치: 사용자 「어 해줘」로012 P0 및 Identity 연동 필요 확인. feat/TMI-216-lc-paid-exam-integration, 기존011 코드 변경 보존.
- 확인: PurchaseObservation STORE_PAID는 구매액, RC REST refunded 금액null/webhookCANCELLATION review, 현재reversal PLAN012보류·paid root CAS·기존worker/lease/operator/alerts 대조. Identity 로컬HEAD eec6018b support 소스에서 인증REFUND·ACTIVE·거래필드부재·Slack문의번호본문·RECEIVED/90일 확인. 실배포/전체감사 아님.
- 변경: P0-MINIMAL-TECHNICAL-DESIGN-R1 및 Identity SUPPORT-HANDOFF 신규, PLAN012/CURRENT_STATE/WORKLOG. 최소증거/기존collection확장/outbox/index·순차worker수치 후보·검증표 작성. 현재operator는ACCOUNT/AUTH뿐임과환불case확장 필요 명시, 문의보존/구매연결 운영미확인 분리.
- 검증/범위: 문서 diff/상대링크 검사. 코드 미변경으로 Gradle 미실행. 실제Store/RC/Identity API·법률조회·LC/Identity수정·Jira·외부발송·DB·배포·commit/push 없음. 이번문서5개 외 직접변경 없음.
- 다음: Identity운영조회/결과기록/90일보존경계 회신, provider전액/부분증거와 schema/worker exact 검토 후P0동결.012착수/활성화 아님,011진행 유지.


## 2026-10-14 — Identity PLAN-012 운영 회신 접수

- 근거: 사용자 전달 및 Identity PLAN-012-support-Identity-response-2026-10-14.md 원문 읽기. feat/TMI-216-lc-paid-exam-integration, 다른 구현 변경 보존.
- 변경: Identity 인계서·P0·CURRENT_STATE·WORKLOG. API재사용 확인과 문의90일/멱등7일·이메일선택/소유미검증·GUEST접수·권한조회/구매검증 분리·Slack복제본 별도보존을 반영. 담당/답변기록위치/연락·보존기간·인계성공은 미확정운영gate 유지.
- 검증/범위: 문서diff검사, 코드미변경으로 Gradle 미실행. Identity코드·실DB/권한·법률·외부발송·Jira·배포·commit/push 변경없음.012착수/공동테스트 통과 아님. 다음은012 P0 기술검토와 운영항목 확정,011 계속 진행.


## 2026-10-14 — 환불 문의 담당·Notion·연락 창구 방향 승인

- 요청: 본인담당/팀원대행, Notion기록 적합성 질문 및 기존지원메일/최소보존방향 수락. feat/TMI-216-lc-paid-exam-integration에서 인계/P0/CURRENT_STATE/WORKLOG만 수정.
- 반영: 제한된Notion 최소기록·공개공유금지/금융원장은Billing, 지원메일 tosunsaeng093@gmail.com, 금융5년/해당불만분쟁최소3년/불필요원문분리. 정확기산점/실권한·페이지·이메일본인확인·SaaS개인정보처리 및 복제본삭제는 미확인으로 분리.
- 검증: 문서diff검사, 코드미변경으로 Gradle 미실행. 외부Notion페이지/권한·메일·Jira·DB·LC/Identity·배포·commit/push 변경없음. 실제 개인정보 입력·법적 적합성 검증/012착수 아님. 기존 동시구현 보존.



## 2026-10-14 — Notion 환불 빈 기록 양식 작성

- 요청/브랜치: 지정 Notion 페이지 3f9dcc5dbaeb80cbbfaef77ab7410ad6에 양식작성. feat/TMI-216-lc-paid-exam-integration. 고객 실데이터 없이 담당/메일/건별기록/답변/체크리스트/최소보존 주의 작성.
- 공유확인: 웹게시 탭은 게시 전, 일반권한 초대받은 사용자만. 상위문서 사용자2명 상속을 확인했고 사용자가 팀원이라고 확인했다. 권한변경/초대/공개게시 없음. 담당자만 접근하도록 운영 유의, 전체 워크스페이스 보안감사 완료 아님.
- 검증: UI/스크린샷으로 결과 확인. 초기 HTML붙여넣기 서식불량을 텍스트양식으로 정리했고 자체생성 중복초안만 제거, 원 사용자콘텐츠 없음. 증빙 /private/tmp/billing-refund-notion-template.png. Notion 공식 privacy/data-residency/DPA 검색으로 해외처리 가능·삭제/backup 조건 확인 필요 안내. 실제 방침문서/국외이전 적법성/정확보존기산점 검증은 미완료.
- 범위: 노션요청페이지와 CURRENT_STATE/WORKLOG만 변경. 코드/DB/Identity·LC/Jira/메일발신/배포/commit/push 없음. 기존 동시작업 보존, 코드미변경으로 Gradle 미실행. 다음은 실제정보입력전 방침/처리조건·기록별삭제 운영 확인.


## 2026-10-14 — PLAN-012 환불 테스트 준비 읽기 확인

- 브랜치 feat/TMI-217-deletion-owner-retention-contract. 목표: Store/RC 설정과 실제 환불 검증 준비 구분. 신규 Jira 없음.
- RC premium 양 Store 5종 연결, Google 5종 활성 옵션, Apple 5종 초안/제출 준비 중 직접 확인. RC webhook 미등록, Google credentials valid/connected이나 알림 수신 없음, SDK data 없음. 과거 Draft 기록과 현재 차이를 분리했다.
- 변경: PLAN-012-REFUND-TEST-READINESS-2026-10-14.md 신규 및 CURRENT_STATE/WORKLOG. 코드 reversal review/금액 불명 처리 정적 확인. 실제 거래·Orders 인증·Apple 세부 알림·Billing 배포 미검증.
- 검증: 콘솔 UI·정적 파일 조회, 문서 diff 검사. 코드 변경 없어 Gradle 미실행. 정책/계약·PLAN011 진행/012 후속 유지, 외부 설정/거래/환불/DB/배포/Jira/commit/push 없음. 기존 동시작업 보존, 이번 예상 밖 변경 없음.
- 다음: 테스트 앱/계정 및 수신 인증 호환성 확인→테스트 구매/알림→환불 증거 확보. 불명 금액은 추정하지 않으며 실제 실행은 별도 승인.



## 2026-10-14 — PLAN-012 내부 구현 규격 구체화

- 요청: 기술 규격 구체화. 현재 feat/TMI-217-lc-deletion-handoff, 동시 작업의 branch 변경을 따라 읽기만 했으며 직접 전환하지 않았다. 신규 Jira 없음.
- 조사: PurchaseObservation/Schema/Core, paid reserve/confirm/source/AttemptSession, 기존 source worker, R2와 TMI217 보존 제안. INITIAL은 confirm 때 group 생성하므로 RESERVED 정리 phase가 필요함을 명시했다.
- 변경: PLAN-012-IMPLEMENTATION-SPEC-R1 신규, PLAN012/P0 연결, CURRENT_STATE/WORKLOG. 필드/타입/unique·금액unknown·legacy digest 호환·root CAS·confirm 증거·예약/group 단계·worker 수치·운영flag·F01~12 테스트를 정리했다.
- 유지: 제품 정책/R2 wire/기존 fixture bytes, 수동 심사·검증된 Store 사실만 반영. 실제 provider 매핑/복수 Session 보호 충돌/TMI217 증거/큰 timeline/운영 보존은 gate이며 완료로 위장하지 않는다.
- 검증: 정적 조회·문서 링크/diff 검사. 문서만 작성해 Gradle 미실행. 코드/외부설정/LC·Identity/DB/실거래/Jira/commit/push 변경 없음. 기존 동시작업 보존, 이번 예상 밖 변경 없음.
- 다음: 구현 단위/Jira 준비 후 OFF 구현. 실Store·양측통합·실Mongo/배포 조건 검증은 별도.



## 2026-10-14 — PLAN012 R1 예외 범위 재검토

- 요청: 불필요하게 넓힌 일반 예외 확인. feat/TMI-217-lc-deletion-handoff. PLAN-012-IMPLEMENTATION-SPEC-R1 정적 재검토, 코드/실환경 추가검증 없음.
- 발견/권장: malformed·중복 Retry-After 즉시 REVIEW_REQUIRED는 기본 backoff+관측으로 축소 검토(상대 계약 대조 필요). 정상 긴 대기 지시를 무시하는 변경은 제외. group 증거 하나 부족할 때 purchase 전체cursor 정지 대신 해당group durable 미결 기록과 나머지 진행 제안(미결을 완료로 표시 금지). legacy 포인터 일반 이관은 실제 대상 조사 후 한정하고 없으면 신규writer부터 적용. 실제 provider 확인 전 범용 금액adapter/추가복구 도구 확장 금지.
- 유지: root/confirm 공통CAS·다른권리보존·중복금액방지·예약정리·고정event/outbox·lease/version·Session보호. 정책이나 정확성 핵심 제거를 권하지 않음.
- 변경은 CURRENT_STATE/WORKLOG 분석기록만, 설계 본문·제품·공동계약·Jira·외부설정·DB·배포 변경 없음. 승인 후 필요한 설계만 수정. 문서 diff 검사, Gradle 미실행. 기존 동시작업 보존, 예상 밖 변경 없음.


## 2026-10-14 — PLAN012 예외 축소 승인 반영

- 사용자 「어 그렇게 하는 걸로 해줘」. 현재 feat/TMI-217-session-owner-evidence, 직접 branch 전환 없음. 기존 병행 작업 보존.
- R1/PLAN/P0 수정: malformed/중복/overflow Retry-After 기본 backoff+관측(유효 긴 지시/24h 한도 유지), 증거불명 group만 기존group refundReview에 보류 후 cursor 전진, 실제legacy 조사 후 대상한정 이관. 가짜outbox/추정Session 금지, 보류/cursor 원자성과 재확인/미완료집계·purge 보호 및 F06/F07/F09 회귀 반영. 남아 있던 두 절 제목 외국어 표기도 한국어로 정리.
- CURRENT_STATE/WORKLOG 갱신. root CAS·금융멱등·Session/다른권리 보호·공동wire/기존sender 불변. 별도 범용 복구/관리 화면 추가 없음.
- 검증: 문서 diff/링크 검사, 코드 변경 없어 Gradle 미실행. 제품/fixture/타저장소/외부설정/DB/Jira/배포/commit/push 변경 없음. 이번 예상 밖 변경 없음. 다음: 구현 착수 시 현재schema·LC 전송규격 대조 및 승인범위 회귀.


## 2026-10-14 — 기존 결제 구현의 단순화 여지 검토

- 요청: 복잡하게 구현된 부분과 작은 위험으로 단순화 가능성 확인. PaymentSourceWork/Worker, ReviewOperator, ProviderRuntime/Configuration/Alerts, RC ProjectBudget/BudgetedTransport 및 schema검사 호출을 정적 확인했다. PLAN012 reversal은 후속 미구현임을 구분.
- 산출물: PLAN-012-IMPLEMENTED-COMPLEXITY-REVIEW-2026-10-14, CURRENT_STATE/WORKLOG. 반복schema검사 구성진입점 통합, 응답후permit 운영검사 조건부축소, 미구현refund flag3→2 후보를 제안. 잠금/금융보호 제거 권고 없음.
- 현재 feat/TMI-217-session-owner-evidence 동시 코드 변경 보존. 제품·기술규격 본문·Jira·외부·DB·배포·commit/push 변경0. 정적 문서검사, Gradle/성능실측 미실행. 이번 예상밖변경 없음. 다음: 승인된 후보만 보장/테스트 대조 후 범위 확정.



## 2026-10-14 — RC 응답 후 DB 검사 축소 가능성 확인

- 요청: 응답후 permit검사 필요성 확인. 현재 feat/TMI-217-trial-session-owner-evidence, 직접 branch전환 없음. RC ReadClient/HttpTransport/BudgetedTransport/decoder, BudgetIntegrationTest 및 AccountLookupWorker guard 경로 정적 조회.
- 확인: HTTP5초/permit30초, 늦은응답 및 다른요청AUTH차단 뒤200응답 거절을 기존테스트로 명시. 정상200 post검사만 생략하면 진행중응답유예를 허용하며 신규발신·자기401/403차단/거래검증/업무lease/epoch는 유지 가능. finally release DB실패 영향은 여전히 존재. 성능병목·빈번한permit만료는 실측하지 않음.
- 변경: 복잡성검토문서 후속절/CURRENT_STATE/WORKLOG만. 정책 변경 자동승인 아님, 코드/테스트 수정 없음. 기존테스트 읽기만, Gradle 미실행. 문서diff검사. 외부/DB/Jira/배포/commit/push 없음. 병행코드 보존, 이번 예상밖변경 없음. 다음: 진행중200응답유예 수락 시 좁은 구현·회귀.


## 2026-10-14 — PLAN012 스위치 두 개로 확정

- 요청: 환불 스위치2개 계획 반영. feat/TMI-217-trial-session-owner-evidence. processing-enabled로 환불 반영+정리를 묶고 publisher-enabled로 LC 전달 분리, 기본OFF/독립fanout flag없음. ON/OFF 적체보존·OFF/ON 기동거절·재개 회귀 명시.
- 기존 구현 schema반복검사/RC응답후검사는 유지하며 앞선 후보는 미채택으로 표시. 구현 리팩터링을 PLAN012 선행조건으로 두지 않음.
- 변경: PLAN012/P0/내부규격/복잡성검토/CURRENT_STATE/WORKLOG. 문서diff·링크검사, Gradle 미실행. 제품/설정/DB/배포/타저장소/Jira/commit/push 변경 없음. 동시코드변경 보존, 이번예상밖변경없음. 다음: 승인된 두flag기준 OFF구현/회귀 준비.



## 2026-10-14 — PLAN012 Jira 3단계 생성

- 사용자 Jira생성 요청. TMI137 에픽/유형metadata 조회, Rovo검색 앱미설치403으로 Jira JQL 조회 대체, 부모/환불제목 검색에서 중복 PLAN012 없음 확인.
- TMI-219 금융코어·차단·reflow, TMI-220 예약/시험정리·보호Session·LC전달, TMI-221 운영조회·Slack·실Store/LC통합검증 생성. 모두 작업/부모TMI137/해야할일/미배정. 219 blocks220,220 blocks221 관계 및 부모/상태를 재조회 확인.
- 승인된 두flag/예외축소/기존schema·RC검사유지·미검증provider gate·실거래/권한/배포별도승인 및 테스트 완료조건 기재. 기준문서가 로컬변경 포함임을 명시했다.
- 현재 feat/TMI-217-trial-session-owner-evidence. PLAN012/CURRENT_STATE/WORKLOG 기록만 로컬변경. 코드·설정·DB·배포·commit/push 없음, 동시011 코드 보존. 문서diff검사, Gradle미실행. 예상밖변경없음. 다음 첫 구현대상은219.

## 2026-10-14 — PLAN012 문서 병합 준비

- 사용자 commit/push/merge 및 다음 TMI-219 구현 승인. codex/plan012-refund-plan 별도 worktree로 PLAN012 문서9개와 이 대화 기록만 분리. 원 작업 폴더 TMI217 코드/ADR/PLAN011·기록은 수정하지 않았다.
- Jira219 원문 확인. 문서 diff 검사, 제품 코드 변경 없어 Gradle 미실행. DB/배포/활성화 없음. 병합 후 새 브랜치에서 OFF 코어 구현 예정.

## 2026-10-14 — PR24 병합·TMI-219 환불 코어 구현

- 사용자 commit/push/merge후다음구현 승인. PLAN012 문서11파일 commit28fdc0099b99c7de4ac228b847ad4cd3cb7e65cc·PR24 https://github.com/Too-Much-I/app-back-end-billing/pull/24 생성/첨부, MERGEABLE/exacthead 확인 후 develop mergec6f8044a0d9e29d9dcd38a0f28e3e8d710da4dc1 확인. 신규 codex/TMI-219-refund-ledger는 병합 develop 기반 별도 worktree다. 원 작업 폴더의 TMI217 코드·문서·기록과 PLAN012 사본을 수정하지 않았다.
- Jira219 완료조건 읽기. PurchaseCore/PurchaseObservation 확장, RefundEvidence/PurchaseRefundCore/RefundCoreSchema 및 두flag/기동차단 추가. 원금≠반환액·UNKNOWN/null·누적차이/authority중복방지·최초root차단/appliedAt/reflow/정리책임 단일ClientSession·기존paid rootCAS/confirm replay/복원권보존 구현. r6→r7 명시유지보수, actual schema 설치 없음. 기존manifest·RC검사/외부wire 유지.
- 테스트: 최초 선별21개 중 테스트 자체 비동기완료 대기순서 오류1을 수정했고 첫 전체clean test 성공. 추가 schema/flag/digest 경계 보강 후 최종 검증은 아래 후속 기록에 남긴다. 실제 Mongo replica-set failpoint/rollback/동시append·confirm/환불 양순서 검증, 실제 provider HTTP/LC E2E 아님. 기존 simplified/R2 offline+hash 검사 통과, 공동상태28/HTTP 실검증으로 보고하지 않음.
- 안내/PLAN012/R1/AGENTS/CURRENT_STATE 갱신, 링크/diff검사 통과. 실제RC/Google 금액매핑·큰timeline부하·TMI220정리/LC전달·TMI221운영은 남음. 처리flag ON은 기동거절하며 신규앱API/운영자금액입력·새범용복구·관리화면 없음. 실DB/외부설정/판매활성화/LC·Identity/Jira쓰기 없음. 구현 변경은 로컬 미커밋이며 예상밖범위변경 없음.

### TMI-219 최종 검증

- 최종 코드 기준 `./gradlew clean test` BUILD SUCCESSFUL(3m16s), XML 집계1,602개/실패0/오류0/skip0. 신규25개: RefundEvidenceTest4, PurchaseRefundIntegrationTest18, PaidRefundIntegrationTest3. legacy bytes/digest 및 v2증거누락409/저장version불일치차단 보강 포함. 기존 deprecation/JVM 경고 외 실패 없음.
- simplified/R2 offline+hash 검사 및 변경문서 로컬link/diff 검사 통과. 원 작업 폴더 TMI217 상태 재확인·변경보존, PR24 MERGED/c6f8044 재확인. 신규코드 미커밋/미푸시, Jira 상태 미변경. 실제 Store/LC HTTP·대규모 timeline 성능·운영 schema/권한/배포 검증은 수행하지 않았다.
- 다음: TMI-220에서 refundWork 순차정리/보호증거/outbox/LC전달 연결, TMI-221 운영 및 실Store 공동검증. 기능 ON 전 해당 gate와 TMI217 보존 조건을 충족해야 한다.

## 2026-10-14 — TMI219 병합·TMI220 선행 보호Session 계약 조사

- 사용자 commit/push/merge후다음구현 승인. TMI219 18파일 commit09dbb9856c3fe776ad745803cb37ca454b51465f·PR25 https://github.com/Too-Much-I/app-back-end-billing/pull/25 생성/첨부. CodeRabbitSUCCESS/MERGEABLE/exacthead 확인 후 develop mergee8f55eec3c97bdb963df046bd8d80a718355565f 확인. 병합develop에서 codex/TMI-220-refund-cleanup-delivery 생성. 원작업폴더TMI217 변경보존.
- Jira220 읽기·PLAN012R1/R2·LC인계/기존paid runtime 대조. R1§3.2의복수completionAllowed 우려를 실Mongo characterization 신규1개로재현: 같은group replacement확정뒤 S1/S2 모두ACTIVE/confirmedAt/terminal없음·S1completionAllowed. 선별test성공. 신규runtime코드변경없음.
- LC /Users/msde76/app-back-end-learning-core HEADf59cf28 작업트리 읽기: AccessRevocationStore 비보호SessionaccessRevoked, ExamAcceptedWorkPolicy 접수작업도먼저거절, GradingService 연결확인. LC실행/배포조회는없음. Billing과LC 공동E2E 성공주장없음.
- 단일confirmedSessionId/기접수작업완료의 의미를 임의변경할수없어 환불전달구현중단. 새wire/배열/자동terminal/접수작업권한확대없음. 기술확인인계서/CURRENT_STATE/본기록작성. 기존코어OFF·ON기동거절유지, Jira쓰기/외부메시지/LC수정/실DB/배포없음. 전체clean test실행중이며최종결과별도append. 예상밖변경없음.
- 최종 `./gradlew clean test` BUILD SUCCESSFUL(3m6s), XML 집계 1,603개/실패0/오류0/skip0. 신규 재현1개 포함이며 LC 실행·공동 HTTP·배포 검증을 뜻하지 않는다. TMI220 테스트와 문서는 미커밋이며 제품 구현은 공동 보호 의미 확인 대기다.

## 2026-10-14 — TMI-220 기접수 작업 경계 문서 보완

- 브랜치 codex/TMI-220-refund-cleanup-delivery. 사용자 「이렇게 하자 문서 보완해줘」에 따라 clarification/R2/LC 인계/PLAN012/R1/ADR004/환불 정책/AGENTS/CURRENT_STATE를 동기화했다. 기존 조사·재현 테스트와 원 작업 폴더의 TMI217 변경은 보존했다.
- 환불 전 confirm·재시작 전 영속 접수 작업만 완료, 신규 제출/새 녹음/새 Session 금지. 일부 문항으로 전체 완료/Summary를 추정하지 않으며 삭제·탈퇴·확정 종료 우선. Billing 과거 Session 이벤트는 해당 Session/이용 증거만 반영, 현재 group/Session/새 guard 불변. 단일 confirmedSessionId/wire/fixture/인증/금융 규격 유지.
- C01~C06와 H11~H13 예정 회귀를 명시했다. 이번은 문서만 변경해 Gradle 재실행 없음; 이전 1,603 통과를 보완 구현 검증으로 재사용하지 않는다. diff/로컬 링크 정적 검증 수행. 코드/LC 저장소/Jira/외부 발송/commit/push/DB/배포 변경 없으며 예상 밖 변경 없음.
- 다음: LC에 보완 문서를 전달하고 영속 접수·순서 판정/과거 이벤트 허용 전이 근거를 대조한 뒤 담당 범위 구현 및 공동 경합 검증. 정책 방향 승인을 구현 완료·공동 E2E 통과로 표시하지 않는다.

## 2026-10-14 — TMI-220 LC 회신 접수·테스트 번호 정정

- codex/TMI-220-refund-cleanup-delivery에서 사용자 반영 요청 수행. LC 정적 회신을 clarification §3.1·PLAN012 R1·LC 인계서에 반영하고 CURRENT_STATE 갱신. pendingAt 최초 접수 사용 금지, retry0 receipt/3일 TTL 및 전체접수 marker의 한계, 서버 시각만으로 commit 순서 추정 금지, Callback/기존 시스템 재시도/Summary/원 Session terminal·Billing late event 검증 범위를 명시했다.
- 기존 H11~H13 내용/번호 유지, 신규 중복 세 항목만 H14~H16으로 정정하고 표 연속성 복구. LC 회신의 새 H11/H12/H13→H14/H15/H16 및 C01~C06 매핑 추가. 과거 WORKLOG는 보존한다. 공유 fixture ID/bytes·wire·단일 보호 ID·정책은 불변이다.
- 문서 변경만으로 Gradle 미실행. diff/문서 링크·H01~H16 유일성 검사 수행. 기존 코드/재현 테스트 변경은 보존하며 이번 코드/LC 저장소/Jira/외부 발송/commit/push/DB/배포 변경 없음. 예상 밖 변경 없음. 다음은 양측 담당 구현 및 독립/공동 경합 검증이며 이번 회신을 구현·공동 E2E 완료로 표시하지 않는다.

## 2026-10-14 — TMI-220 Billing 과거 Session 이벤트 보완 구현

- 브랜치 codex/TMI-220-refund-cleanup-delivery. 사용자 보완 요청, Jira220 읽기 및 최신 승인 문서 대조 후 PaidAttemptEventService를 수정했다. LC 저장소는 변경하지 않았다. 과거 ACTIVE S1의 confirm/completionAllowed/source/owner/lifecycle 검증 후 terminal만 반영하고 현재 group/S2/guard를 쓰지 않는다. 과거 GRADING은 STALE/204로 저장해 현재 group을 채점 중으로 바꾸지 않는다. exact owner-rebind terminal cleanup과 inbox/이용 원장 단일Tx는 유지한다.
- HistoricalPaidSessionEventIntegrationTest 실Mongo8개 추가: 실제 환불코어 후 late 완료/실패, 중복·역순, group/S2/guard 보존, missing confirm/permission·source mismatch, 삭제/탈퇴, 원장실패 rollback, 동시duplicate, 새pending guard 보존. LC 접수/부분문항/Summary 판정을 가짜 통과로 대체하지 않으며 C04~C06/H16의 Billing 범위다.
- 선별 테스트 성공 후 첫 clean test는 1,611개 중 신규 테스트 데이터1건 실패: pendingOperationId만 세팅해 guard all-or-none schema 위반. 테스트의 pendingReservationId/pendingGroupId를 함께 설정하도록 수정했고 전체 재실행 중이다. 제품 코드 수정 사유가 아니며 최종 결과는 후속 기록한다.
- runbook·PLAN012 R1·clarification·AGENTS·CURRENT_STATE 갱신. diff/문서 링크 정적검사 통과. wire/fixture·단일confirmedSessionId/금융root/기본OFF 불변. 원 작업폴더TMI217 및 기존 dirty 변경 보존, 이번 범위 밖 코드/LC/Jira/외부 메시지/commit/push/실DB/배포 변경 없음.
- 다음: LC exact 접수/Callback/시스템 재시도/Summary 보완 및 실제 공동 HTTP 경합, TMI217 최종 삭제 접점 대조, TMI220 환불 정리/outbox/publisher. 이번 보완을 TMI220 전체 완료·공동 E2E·활성화 승인으로 표시하지 않는다.
- 최종 `./gradlew clean test` BUILD SUCCESSFUL(3m12s), XML 집계 1,611개/실패0/오류0/skip0. 신규8개 모두 통과. 앞선 실패는 테스트 guard 데이터의 all-or-none 수정으로 해소됐다. diff/문서 링크 검사 통과, 실제 LC/Store/배포 검증 미수행. 변경은 로컬 미커밋이다.

## 2026-10-14 — PR26 병합·TMI-220 환불 순차 정리/전달 코어

- 사용자 commit/push/merge후후속구현 요청으로 기존 보완14파일 commit6de78ac3afc36986f3308b27c0a53b8f5b5546f0·PR26 생성/첨부. MERGEABLE/exacthead 확인(조회 당시 check rollup 없음) 뒤 develop mergee48efb79b66e8519a4e4aa96b610a9736dcef05a 확인. 새 codex/TMI-220-refund-fanout 브랜치를 만들고 사용자의 추가 요청에 따라 feat/TMI-220-refund-fanout으로 이름 변경했다. 현재 신규 브랜치는 미푸시이며 원 작업 폴더 TMI217과 무관한 변경은 보존했다.
- PaidReservationService 성공confirm 포인터 및 refund 취소/실제confirm 거절receipt 연결, RefundFanout/RefundFanoutSchema 추가. source/계정/lease/recovery fence·건별Tx, RESERVED→GROUPS,20단계·30초lease, 증거불명review/cursor와 재확인/첫outbox 원자화. 기존 source/guard/무료권 보존, legacy추정·가짜CONFIRMhash·전체단일Tx 없음.
- RefundAccessPublisher/RefundAccessHttpTransport 추가. 불변 canonical JSON 문자열/digest·전용RS256 JWT, 빈204/두409/auth/retry 분류, Retry-After fallback·긴대기/24h보류,5초/16KiB/redirect없음, 응답유실/오래된lease결과 방어. 기존 Identity/LC 수신·공유 fixture·금융규격은 변경하지 않았다.
- schema는 isolated/ALL_BLOCKED 아래 명시 설치만 제공,실DB설치없음. 두tick에5초annotation은 있으나 Spring bean/provider연결·운영재개CLI/감사/Slack·LC공동E2E·TMI217최종보존은 후속이다. 기존 processingON 기동거절/기본OFF 유지. 운영자동실행·판매가능으로 보고하지 않는다.
- 선별 Mongo/로컬HTTP 테스트 통과 및 첫 전체clean test BUILD SUCCESSFUL(3m28s). 이후 DONE재확인실패의nextAttemptAt불변·groupversionoverflow검사 및 publisher새generation/24시간 경계2개를 포함해 최종 전체재실행 중. 최종 XML 결과는 아래 append한다.
- runbook·AGENTS·PLAN012 R1·CURRENT_STATE 동기화, diff/문서링크 검사 통과. 신규코드는 로컬미커밋, 타저장소/Jira쓰기/실제Store거래/배포/활성화 변경 없음. 실제공동E2E/미검증provider 지원 완료 아님. 예상밖범위 변경 없음.
- 최종 `./gradlew clean test` BUILD SUCCESSFUL(3m27s), XML 집계1,645개/실패0/오류0/skip0. 신규34개(RefundFanoutIntegrationTest15, RefundAccessPublisherTest19). 실제 replica-set Mongo·모의LC transport·테스트키 서명·로컬 HTTP 경계 결과이며 실제LC/Store/ALB E2E 아님. 최종 diff/링크 검사 통과. 새 브랜치에는 commit/push하지 않았다.

## 2026-10-14 — Notion 프론트 결제 안내 최신화

- 사용자 지정 결제 페이지(3f9dcc5dbaeb80618ec9ed3e5d8a5966)를 브라우저에서 수정했다. 기존 네 API 구조/표 유지, 종합 판정 미구현 문구를 PaidExamQueryReader·ExamAvailability 코드의 true/false/null 및 미연결 fallback 구분으로 교체했다.
- 무료권 복원·hasPendingSync·최종 reserve/confirm 판정·환불 문의/권리 종료·기접수 작업 예외·병합/로컬 구현/배포 대기 범위를 추가하고 화면 내용으로 반영을 확인했다. 실제 Store/LC 공동 검증 완료로 표현하지 않았다.
- 이번은 Notion과 작업 기록 변경만 수행. 기존 로컬 구현 보존, 코드/타 저장소/Jira/배포/flag/commit/push 변경 및 Gradle 재실행 없음.

## 2026-10-16 — TMI-220 병합 및 TMI-221 운영 조회

- 사용자 명시 요청으로 TMI220을 전체 clean test1645/실패0/오류0/skip0 재검증(3m41s)하고 commit04511c4·push·PR27 생성/첨부했다. exact head/MERGEABLE 및 조회 시 CodeRabbit SUCCESS를 확인한 후 develop merge17edd0d를 확인했다. 로컬 전체 테스트와 원격 단일 상태를 구분하며 별도 배포 CI 성공은 주장하지 않는다.
- 같은 관리 worktree에서 `codex/TMI-221-refund-operations` 생성. 원 폴더의 별도 TMI217 dirty 보존. 후속1차는 RefundOperationsReader/CLI·Gradle task·회귀·runbook: 읽기전용 purchase/outbox 조회, 미전송/OPEN 보류 및 DONE 분리,100건상한과 truncated, scope/인증사용자/입력검사·원문비노출. 기존 금전·권리·outbox·flag/기동gate는 유지했다.
- 초기 선별8개 통과 후 차단work 완료오판 방지 회귀1개 추가. 최종 결과는 아래 기록. 권한·실제DB성능/인덱스·배포는 미검증, audit 재개/Slack/공동E2E는 후속이며 신규 구현은 미커밋/미푸시다. Jira/타 저장소/실Store/실DB/키/배포/판매 활성화 변경 없음.
- 최종 `./gradlew test --tests '*RefundOperations*'` 성공9개/실패0/오류0/skip0(실Mongo6·CLI3), diff 검사 통과. 신규 조회 slice 이후 전체 테스트는 재실행하지 않았으며 선행1645개 결과와 구분한다.

## 2026-10-16 — TMI-221 구현 우선 후 묶음 검증

- 사용자 요청에 따라 재개/재확인/감사·Slack·실행구성 코드를 먼저 작성한 뒤 묶음 검증했다. RefundOperator/Schema, RefundOperationsCli/Reader, RefundAlerts/RuntimeConfiguration과 PaymentSlackSink/ProviderAlerts/OperatorSchema/PaymentProperties 및 테스트·runbook·PLAN012·현재상태를 변경했다.
- operator는 dry-run 기본·명시apply/격리, 인증 Mongo actor digest·reason·대상hash·새generation·시각 감사와 대상 변경 단일Tx, 같은operation replay/충돌·result 조회를 구현했다. live lease/epoch/version/generation 및 미래Retry-After를 검사하고 원본문/event/effect/cursor 불변. group 불명은 STILL_OPEN, 증거확인 때 기존 fanout helper로 최초outbox/RESOLVED 처리. 기존 payment_operator_receipts를 구형 OR 신규형 validator로 명시 확장하며 별도 audit collection/TTL 없음. 구schema만 허용하는 구버전 rollback은 자동허용하지 않는다.
- Slack은 기존sink 재사용·집계만 전송,1분poll·인증/5분초과지연/회복·KST09시 일일요약과 실패metric. 실패로 금융/검토상태 변경 없음. replica/재시작 중복알림 가능·조회수치 단일snapshot 아님을 문서화. 실제Slack 발송/secret 읽기 없음.
- Spring processing/publisher 조건 구성 코드는 추가했지만 PaymentRefundConfiguration의 ON거절은 유지. RC decoder가 최종 RefundEvidence를 생산하지 않는 실제 차이와 미검증 Google Orders 증거를 확인했으며 임의상태→증거 승격 없음. 현재 branch에 TMI217 Session purge가 없어 해당후속 코드/병행작업은 건드리지 않음. provider adapter·삭제연동·실제LC/Store 검증까지 완료한 것은 아니다.
- 첫 환불/Slack 선별105개 중1실패는 새 알림kind에 구runbook만 기대한 테스트였고 TMI221/212 매핑 회귀로 보완했다. 이후 감사schema 호환·미래Retry-After·CLI apply격리 회귀 포함 최종 `./gradlew clean test` BUILD SUCCESSFUL3m41s, XML1666/실패0/오류0/skip0. diff검사 통과. 기존9개조회테스트 포함 신규branch 총21개 증가, 테스트 결과는 실Store/LC/ALB 검증이 아니다.
- 동일 codex/TMI-221-refund-operations 로컬미커밋/미푸시. 사용자 기존변경/원폴더TMI217 보존. DB DDL·배포/판매/flag활성화·Jira/외부메시지/commit/push 없음. 다음은 실제provider fixture 확보→adapter·TMI217 삭제연결→실환경 검증이며 운영권한/schema업그레이드/키/Slack 설정은 별도 승인이다.

## 2026-10-16 — TMI-221 금융 검토 건 조회 및 남은 구현 경계 확인

- 브랜치 codex/TMI-221-refund-operations. Jira221을 읽고 기존 승인 R1/테스트 준비/TMI217 계약과 코드를 대조했다. 실제 provider 필드/누적 의미는 실제 fixture 후 고정하는 규격이며 TMI217 삭제 계약은 이 브랜치 draft-3/최종 writer 부재다. RC status를 새 최종 증거로 임의 승격하거나 가짜 purge hook을 만들지 않았다. 금융 case 재조회/해결이 남아 있다는 점도 명시했다.
- RefundOperationsReader/CLI 및 두 테스트에 case-list/case-show를 추가했다. 결제환경·환불/revoke 필터, OPEN/legacy 처리, UUID 정렬100건/nextAfter, 상세 RESOLVED 조회와 비민감 필드 allowlist다. snapshot은 각 요청에 한정하며 페이지 간 완전한 export는 보장하지 않는다. case 조회가 검토 완료/재조회/상태 변경을 뜻하지 않는다. DB 읽기만 수행하며 원문/사용자/거래/actor를 출력하지 않는다.
- runbook·PLAN012 규격·CURRENT_STATE 갱신. 기존221 미커밋 변경 보존, 원 작업폴더TMI217/타저장소·공유wire·schema·provider·Jira·배포·commit/push 변경 없음. 예상 밖 변경 없음. 회귀는 읽기 무변경/환경격리/legacy/RESOLVED/타구매제외/페이지/CLI 변조 거절을 추가했으며 전체 clean test 실행 중이다. 결과는 아래 별도 append한다.
- 다음: 실제 sanitized 환불 증거로 adapter/금융case 재조회·해결 연결, TMI217 최종 writer 통합 후 보존 경합, LC/Store 공동 검증. 배포 전 schema/RBAC·키/TLS/Slack 및 기본OFF 해제는 별도 승인이다. 이번 조회 보완을 전체 구현완료로 표시하지 않는다.
- 첫 전체 실행1670개 중 신규 페이지 테스트1개 실패: 테스트 seed를 relaxed JSON으로 복제하면서 BSON long이 int로 변환되어 schema가 거절했다. nested 값은 수정하지 않으므로 `new Document(seed)`로 타입을 보존하도록 테스트만 수정했다. 제품schema/검증 완화 없이 clean test 전체 재실행 중이다.
- 최종 `./gradlew clean test` BUILD SUCCESSFUL3m43s. XML 집계1670개/실패0/오류0/skip0, 신규4개 포함 조회/CLI14개 통과. `git diff --check` 통과. 실환경 검증/배포 없음, 로컬미커밋/미푸시 유지. 이번 변경은 Reader/CLI·각 테스트·runbook/PLAN012/CURRENT_STATE/WORKLOG에 한정하며 이전221 변경과 구분한다.

## 2026-10-16 — TMI-221 후속 구현 선행 재확인

- 사용자 계속 진행 요청으로 codex/TMI-221-refund-operations 및 원폴더 TMI217 작업 상태/로컬 branch 목록, provider decoder, 테스트 자료 경로, PLAN012 R1 §2.2 및 삭제 계약 draft-3를 읽었다. 실제 환불 응답의 필드/완전성을 고정할 자료와 최종 Session 삭제 writer는 확인되지 않았다. 원폴더 TMI217의 dirty ReserveService/문서는 보존했다.
- 승인된 규격은 실제 fixture 확보 후 adapter 고정이며, 테스트 앱/거래 준비 없이 실제 구매·환불 실행을 이번 요청으로 추정하지 않았다. 또 다른 대체 구현으로 완료를 표시하지 않고 테스트 앱·기존 테스트 거래 응답 준비 여부를 사용자에게 확인한다. 실제 provider/금융 case 재조회·해결, 삭제 연결과 공동 검증은 미완료다.
- 변경은 CURRENT_STATE와 이 append 기록뿐. 분석 작업으로 Gradle 미실행; 직전1670개 통과를 새 구현 검증으로 재사용하지 않는다. diff 정적검사 수행. 외부 API/wire/schema·금액/권리·타저장소·Jira·배포·commit/push 변경 및 예상 밖 변경 없음. 기본OFF/ON거절 유지. 다음은 준비된 테스트 환경/응답 확보와 TMI217 최종 구현 확인이며 실제 결제/환불/설정 변경은 별도 승인한다.

## 2026-10-16 — TMI217 삭제 선행 구현 가능 범위 검토

- 사용자 확인 요청으로 Billing 두 worktree와 LC HEAD6f6675f의 로컬 코드/문서를 읽었다. LC 생성중삭제409는 이미 구현됐고 Billing paid 생성owner/원폴더 무료writer 보완도 확인했다. 따라서 모든 삭제 기반이 없거나 Store가 선행이라는 해석을 정정한다. 새 route/JWT purpose/sender/receiver/ACK·단일merge 권한근거는 미완료이며 공동draft-3 수락 기록은 확인되지 않았다.
- TMI-217-IMPLEMENTATION-READINESS-2026-10-16.md와 CURRENT_STATE/WORKLOG만 작성. 권장 순서는 공동 exact/owner/eventId·ACK 확인→삭제 연동 양측 구현→환불 경합→별도 최종purge다. 현재 paid 상태event의 Session deleted 검사도 이미 있으므로 후속은 실제writer 경합 검증으로 구분했다. 물리purge 미정 수치를 구현 승인으로 발명하지 않는다.
- 정적 확인/diff검사만, Gradle·실환경/DB/Store/공동E2E 미실행. 원폴더TMI217/LC/기존221 dirty 보존. 제품/wire/schema/Jira/외부발송/commit/push/배포 및 예상 밖 변경 없음. 다음은 LC 기술수락과 Billing/LC 구현 분담 확인이며 사용자에게 새로운 제품선택을 불필요하게 요구하지 않는다.

## 2026-10-16 — TMI217 삭제 전달·ACK 공동 보완안 작성

- 사용자 문서 구체화 요청에 따라 LC DeletionTarget/Operation/Worker/UserMergedTransactionService와 Billing SessionCreationOwner/OwnerRebindService/SubjectOwnerRebind를 정적 대조했다. LC prepare의 billingResolved=true/생성허용 선행cleanup·완료30일TTL과 Billing 짧은 ACTIVE fence의 한계를 확인했다.
- TMI-217-DELETE-DELIVERY-ACK-ADDENDUM-R1.md 추가, 기존 LC 인계서/CURRENT_STATE/본기록 연결. 기존 target 불변7필드 및 동일Session 최초fact 재사용, 단일 subject merge 근거/LC exact근거, cleanup과 전달검토/ACK 및 생성허용 분리, linked 증거TTL 제외와 최종purge 별도 범위를 제안했다. D1~D6는 상대 수락 요청이며 수락/동결 완료로 선언하지 않았다. 단일merge 후 source 인가/phone이전 뒤 Session-only 경계도 명시적 검토 항목이다.
- 제품코드·공유fixture/wire·타저장소·DB·Jira·외부메시지·commit/push/배포 변경 없음. 기존 dirty 변경 보존, 예상 밖 변경 없음. 문서 링크/diff 검사, Gradle은 문서작업이라 미실행. 다음은 LC 항목별 수락/수정/coverage회신→정본/fixture→각서버 구현이며 실제환불 자료는 선행조건이 아니다. 미정 개인정보 보존을 임의 연장하지 않는다.

## 2026-10-16 — 이전 ACK 제거 결정과 삭제204 구분

- 사용자 질문으로 TMI204 계획 및 TMI203-204 문서개정 인계서, 삭제draft-3를 대조했다. 제거 대상은 원격 복구완료 ACK/checkpoint API·ACKworker였으며 기존push204는 유지한다고 명시돼 있다. 삭제 문서의 ACK는 LC→Billing 삭제POST의 commit 후 empty204 성공응답/LC 로컬 전달완료이며 추가 ACK API나 역방향호출이 아니다.
- 용어 혼동을 설명하고, 새시험을 해당 성공응답까지 기다리는 조건은 별도의 draft-3 공동수락 대상임을 구분한다. 정책 변경/새 상대 수락 선언 없음. CURRENT_STATE/본기록만 변경, 코드/외부/Jira/배포 변경 없음. 정적diff검사, 분석이므로 Gradle미실행. 기존 변경 보존·예상밖변경 없음.

## 2026-10-16 — 결제 전체 진행 상태 설명

- 사용자 상태질문에 CURRENT_STATE·TMI212 provider 안내·PLAN013·현재Git을 확인했다. 상품/구매계정/구매·기간권/통합조회·유료시험 기반, 병합된 환불코어와 로컬미커밋221 운영기능을 구분해 보고한다. 현재 진행은217 삭제 공동보완안이며 실제provider/금융case 해결/삭제연동/통합검증·판매활성화는 미완료다.
- 기록 두 파일만 갱신. 직전1670 테스트통과는 과거 로컬 결과이며 이번 Gradle 미실행, 실제배포/외부설정 조회 없음. 제품/외부계약/Jira/commit/push/배포 변경 없고 기존dirty 보존·예상밖변경 없음. 다음은217 공동계약확인→삭제구현, 실제환불증거 확보→provider연결, 전체통합검증/별도판매승인이다.

## 2026-10-16 — TMI217 보완안 단순화 검토

- 사용자 복잡도 질문에 보완안R1 전체를 대조했다. 재접수 최초fact의 cross-operation 참조와 양측 merge증거는 단순화 후보, durable전달/commit204/삭제scope 및 현재승인안의 생성차단은 유지 대상으로 구분했다.
- SIMPLIFICATION-REVIEW 작성·CURRENT_STATE/본기록 갱신. already-deleted exact인가 후 no-op204는 draft-3 다른event409 변경 후보로만 제안하며 같은event/body충돌·unknown대상 성공추정 금지는 유지한다. LC단일소유인가 선택지는 독립방어 감소를 명시했고 사용자/LC수락 없이 적용하지 않는다.
- 코드/정본/공유fixture/타저장소/Jira/외부전송/배포/commit변경 없음. Gradle미실행, 문서diff검사. 기존dirty 보존·예상밖변경 없음. 다음은 사용자 단순화 범위 선택 후 LC공동계약 보완이며 이번에 자동 승인하지 않았다.

## 2026-10-16 — TMI217 단순화1·2 사용자 승인 반영

- 사용자 두안 수락에 따라 TMI-217-DELETION-SIMPLIFIED-R2.md 작성, draft-3/R1/LC인계서에 해당절대체 우선순위 명시, 검증계획 W10/W11/O03/O08/O09 수정 및 CURRENT_STATE 갱신. 이미삭제exactSession의다른event204/no-op 및 LC사용자인가단일책임이 최신 기준이다. Billing별도merge증거/backfill·LCoperation간원fact추적제외. 같은전송불변/같은event다른body409/대상연결/현재권리보호는 유지한다.
- 사용자 위험수락과 LC공동수락·구현을 구분했다. no-op도 처리event 충돌검사를 유지할 최소멱등저장은 exactschema 후속이며 무한배열/무비용주장 없음. 기존creationOwner/다른소유검사 코드는 제거하지 않았다. 원문/204분리·기본OFF·최종purge별도 유지.
- 문서검사만, Gradle미실행. 제품/fixture/타저장소/DB/Jira/외부메시지/commit/push/배포 변경없음. 기존dirty보존·예상밖변경없음. 다음은 R2 S1~S4 LC회신 및 저장/경합fixture 확정 후 각서버 구현이다.

## 2026-10-16 — LC 삭제 단순화 R2 방향 수락 접수

- 사용자 전달 LC TMI_217_DELETION_SIMPLIFIED_R2_REVIEW.md 원문을 읽고 S1~S4 방향수락을 확인했다. 같은event충돌우선/다른event이미삭제204/no-op, LC인가책임/Billing영향검사, 원문정리와필요204 대기분리·미전달30일TTL보호가 일치한다.
- R2/CURRENT_STATE/본기록에 방향수락과 exact미동결을 구분했다. 다음은 최소deny/event멱등저장·인증/오류·LCpayload/전체성공·보존명세 및 fixture다. 새정책선택/구현/테스트PASS로 확대하지 않는다.
- 문서diff검사만, Gradle미실행. LC/제품/공유fixture/DB/외부전송/Jira/commit/push/배포변경없음. 기존dirty보존·예상밖변경없음.

## 2026-10-16 — TMI217 저장·오류·공통사례 초안

- 사용자 요청으로 exact R2 draft-1 및 새 draft fixture cases.json/README 작성, R2/LC인계/CURRENT_STATE 연결. Billing 최초fact/event별receipt 분리·unique/nonTTL index·같은event충돌우선/다른event no-op receipt 원자성, LC기존target전달·lease/전체204·cleanup독립/30일TTL보호를 명시했다. 별도ACK API/merge이력 없음.
- 415오류·재시도구체수치·120일detailReviewAt은제안으로표시하고 LC E1~E5 공동확인을요청한다. 미전달/최초deny/금융보존과자동purge를혼동하지않는다. 기존draft의singlecollection과충돌하는저장규격은본draft에서명시대체제안이며아직DB설치없음.
- 새catalog는wire12/state18/delivery14=44개 설명형 시나리오. JSON구문/필수7필드/ID중복검사통과, SHA256 cases.json=375ab7785126aac64e6336f782b396ddaf75d7e6c5d56bf082c9c0f27b866b27. byte-level 실행fixture/oracle/crypto/제품테스트가아니며 PASS로표시하지않았다. 기존fixturebytes미변경.
- 문서링크/diff정적검사, 제품변경없어Gradle미실행. LC/제품코드/DB/Jira/외부전송/commit/push/배포변경없음. 기존dirty보존·예상밖변경없음. 다음은LC회신→exact/bytefixture동결→각서버구현·독립/실Mongo검증이다.

## 2026-10-16 — LC E1~E5 조건부 회신 보완

- LC TMI_217_DELETION_EXACT_R2_DRAFT_1_REVIEW.md 원문을 읽고 draft-2 작성. 기존cleanup요청24h와delivery첫시도24h 분리,200번째유효성공허용/다음claim금지,send직전시간재검사·crash소진·no자동reset,Retry-After정각/overflow/최소재개시각,인증신뢰키401/403 및HTTP/압축/503분류를 구체화했다. requestedAt는논리접근차단시각이며commit완료wallclock이아님을명시했다.
- LC linked상세검토시각은최초operation완료+120일 제안,일부ACKED/전체미완료·완료후에도기존30일TTL과분리. delivery격리만으로안전cleanup을중단하지않음. draft-1/인계/CURRENT_STATE 연결. 제품정책재선택·새합의완료로표시하지않았다.
- 신규 draft-2 bytes7개(Unicode/canonical,순서,손상UTF8,lone surrogate,1.0,duplicate) 및 경계/경합명세31개,구ID매핑·SHA256SUMS 추가. JSON/ID유일성/canonical기대bytes hash/손상UTF8/escaped surrogate/120일날짜 정적확인통과. 기존44개hash불변. 전체scalar/JWTraw·독립decoder/실Mongo/worker는미실행이며PASS수에합산하지않음.
- 제품코드/LC/DB/Jira/외부전송/commit/push/배포변경없음. 기존dirty보존·예상밖변경없음. Gradle은문서/fixture준비라미실행, diff/링크/hash검사. 다음은LC draft-2 경계재확인 및남은raw/독립실행뒤동결·별도제품구현이다.

## 2026-10-16 — TMI217 draft-2 제한적 검증 회신·draft-3 명확화

- 브랜치 codex/TMI-221-refund-operations. LC DRAFT_2_VALIDATION 원문을 확인하고 hash2/raw7/추가거절3의 test-only 독립 결과와 미실행 경계·경합31개를 구분했다. 제품 서버 통과나 공동 동결로 확대하지 않는다.
- exact draft-3·새 cases/README/SHA256SUMS 작성, draft-2/인계/CURRENT_STATE 연결. retryNotBefore는 기존 하한과 새 유효 후보의 max이며 누락/invalid/중복/더 이른 값으로 지우지 않는다. correlationId는 필수 ASCII1~128자 [A-Za-z0-9._-] 불투명 문자열 제안으로 UUID 강제를 배제한다. 기존 wire/멱등성/200회·24h/cleanup 독립은 유지한다.
- 새 자료의 스케줄6개·추적값8개·길이경계3개·정상503 구조를 정적 대조했다. 이는 실제 HTTP parser/worker/DB 검증이 아니다. 기존 fixture 바이트 보존 및 diff/hash 검사, 문서·자료 작업이라 Gradle 미실행.
- 기존 dirty 보존·예상 밖 변경 없음. 제품코드/LC/DB/Jira/외부전송/commit/push/배포 변경 없음. 다음은 LC 두 명확화 재확인과 공동 규격 동결, 별도 구현·runtime 경합 검증이다.

## 2026-10-16 — TMI217 LC 수락 접수·구현 기준 동결

- 브랜치 codex/TMI-221-refund-operations. LC ACCEPTANCE 원문을 읽고 두 규칙 수락 및 draft-3 > draft-2 > draft-1 구현 기준 동결을 접수했다. 세 초안 SHA-256이 상대 기록과 일치함을 확인하고 원문을 바꾸지 않은 별도 Billing ACCEPTANCE 문서를 작성했다. 인계/CURRENT_STATE 갱신.
- 기존 하한 보존·correlationId 형식 및 R2 정책은 불변이다. 상대의 제한적 독립 검사와 미실행 서버 경계·경합31개/전체scalar·JWT 검증을 구분했다. 새 제품 결정 없음; 다음은 남은 fixture 검증과 별도 구현이다.
- 문서/hash/diff 검사, 제품 변경이 없어 Gradle 미실행. 기존 dirty 보존·예상 밖 변경 없음. 제품/원본fixture/LC/DB/Jira/외부전송/commit/push/배포 변경 없음. 기본OFF·별도 활성화/최종purge gate 유지.

## 2026-10-16 — TMI217 후속 Billing 삭제 수신 구현

- 브랜치 codex/TMI-221-refund-operations. Jira TMI217을 읽었으며 원 이슈는 계약 준비 과제로 확인했다. 사용자 후속 구현 요청으로 구현하고 Jira 범위/댓글/상태는 변경하지 않았다. 사용자 추가 승인으로 원폴더의 ReserveService 및 ReserveMongoIntegrationTest 미커밋 변경만 현재 작업트리에 통합했다. 원본 작업트리·LC/Identity는 수정하지 않았고 기존221 변경을 보존했다.
- 신규 examdeletion 패키지(Decoder/Service/Schema/Properties/Controller/Configuration), 전용 목적 ingress·기존 JWT chain, 기본OFF 설정 추가. 무료 생성owner 환경근거, 삭제 Session 상태 이벤트 차단, 실패 group 마지막 confirm Session 보존, 검증된 삭제 fact 이후 새INITIAL 분기를 연결했다. 단일ClientSession으로 receipt충돌→fact no-op→target/source/environment·현재owner fence→pending검사→exactSession/필요group·guard/fact/receipt를 확정한다. 사용자인가는LC책임, 별도merge근거 없음. 금융/무료소비/복원권 불변·원문 미보관·기존body/fixture 불변이다.
- schema는 명시maintenance만, strict validator/unique 조회index/noTTL. 기본OFF/기동선행조건/read-only schema검증. 실제 DB 설치/ALB/키/LC sender/배포/활성화/최종purge는 수행하지 않았다. 운영 legacy 증거가 없으면409이며 환경/owner를 backfill하지 않는다.
- 신규75회귀: decoder43(실제B01~B07·B03 serializer/request 거절분리), MVC10, JWT필터3, 구성2, replica-set Mongo17(204 commit·중복/충돌·동시event·rollback·unknowncommit·현재/과거/phone/Guest merge·새INITIAL/무료보존·환불fanout경합·늦은완료). 통합한 기존무료owner4회귀 포함 최종전체1749/실패0/오류0/skip0, clean test3m54s. 첫 선별실행의 테스트 Runnable method-reference 컴파일오류와 surrogate 입력의 UTF-8 변환전 손실 문제는 테스트를 수정하고 재실행했다. 최초전체 통과 후 추가경합/최종수정까지 재실행한 수치다.
- 동결초안3개 및 기존fixture4개SHA-256 불변, 통합ReserveService 원본hash 일치, git diff --check·runbook 로컬링크 검사통과. AGENTS/인계/runbook/CURRENT_STATE/본기록 갱신. 예상밖변경없음. 새계약/보존기간을 추가 승인하지 않았고 공통31개 전체·실환경 E2E 통과로 확대하지 않는다. 다음은LC sender·204저장/TTL 연결 및 양측공동검증, 별도배포승인이다. commit/push/외부메시지/Jira변경없음.

## 2026-10-16 — 남은 결제 작업 상태 설명

- codex/TMI-221-refund-operations에서 CURRENT_STATE와217/221 안내를 대조했다. 삭제 Billing receiver 구현 완료와 상대 연결 미확인, 실제provider 환불증거 adapter/금융case 재조회·해결 미구현, 공동검증·실환경 운영/배포·최종purge를 분리해 안내한다. 221 안내의 삭제writer 부재 표현은 이전 기록이며 현재 미구현 항목으로 반복하지 않는다.
- CURRENT_STATE/본기록만 갱신. 제품/LC/DB/Jira/외부발송/commit/push 변경없음. 새 테스트 없음(상태 설명), 직전1749통과를 재실행으로 표시하지 않는다. 최신LC/실배포 상태는 별도 확인이 필요하다. 기존dirty 보존·예상밖변경없음.

## 2026-10-16 — TMI221/217 병합·테스트 배포 사전 확인

- 사용자 명시 요청으로 commit/push/merge/test 배포 진행. 현재 작업트리와 원본 dirty 분리 보존, origin/develop17edd0d 확인. 누적 diff·runbook·테스트 XML1749/실패0/오류0/skip0 확인 및 diff 검사. 제품 변경 없이 기존 검증 결과를 재확인했으며 새 테스트 실행으로 표시하지 않는다.
- GitHub 인증 정상/배포 workflow0개. 저장소 Dockerfile/배포 정의 없음. AWS CLI credential/profile/region 부재로 실제 테스트 인프라 조회와 배포 불가. 신규 인프라를 추정 생성하거나 production 변경/flag 활성화를 하지 않는다. Git 작업은 계속 진행하되 테스트 배포는 인증 및 대상 확인 뒤 수행한다.
- CURRENT_STATE/본기록 갱신. 예상 밖 변경 없음, LC/Identity/원본 작업트리·DB/Jira 불변. 병합 결과는 후속 기록, 남은 작업은 테스트 AWS 접근 및 기존 ECS/ALB/DB 대상 확인이다.

### 병합 결과

- 구현 commit744de3b를 원격 작업 브랜치에 push, PR28 생성/현재 채팅 첨부 후 develop에 merge60d5f6c로 병합했다. GitHub MERGED 상태와 fetch 결과를 확인했다. main/production은 변경하지 않았다. 병합 당시 CLEAN/MERGEABLE, 원격 check 목록은 비어 있었다.
- AWS 인증이 없어 테스트 서버 배포는 수행하지 못했다. 배포 정의도 저장소에 없으므로 인증 복구 후 실제 테스트 인프라를 먼저 조회해야 한다. 1749회귀는 기존 제품 변경 최종 결과의 재확인이다. 본 결과/CURRENT_STATE는 병합 후 로컬 문서 기록으로 남긴다.

## 2026-10-16 — 열린 AWS 콘솔 테스트 인프라 조회

- 사용자 요청으로 Chrome AWS 콘솔/CloudShell에서 서울 리전 ECS cluster/services/task families, ECR repositories, ALB 목록만 읽었다. 기존 공용 클러스터에 운영/test가 혼재하고 ALB는 internet-facing이다. Billing 서비스/이미지 repository/task family는 없다. 로컬 CLI 인증 문제와 브라우저 조회 가능성을 구분했다.
- 재배포 아닌 최초 생성이 필요하므로 테스트 전용 인프라 생성 범위·비용 확인을 요청한다. 기존 운영 service/네트워크/권한/DB/키 변경 없음, 새 인프라/배포/활성화 없음. CURRENT_STATE/본기록만 로컬 갱신, 분석 작업으로 테스트 미실행. 예상 밖 코드 변경 없음. 다음은 생성 승인 후 상세 네트워크·DB·최소권한/비용 확인이다.

## 2026-10-16 — 배포 준비·Atlas 권한 양식 지정

- 사용자 신규 테스트 인프라 승인 후 codex/billing-test-deployment 브랜치에서 Dockerfile/.dockerignore를 추가하고 bootJar 성공. CloudShell에서 비공개 tosunsaeng-billing-test ECR(IMMUTABLE/scanOnPush)을 생성 확인했다. amd64 Docker 빌드 시작, 최종 완료는 이번 턴 미확인. ECS/권한/외부노출은 미적용이다.
- 사용자 요청대로 Atlas 새 사용자 양식의 Specific Privileges에 readWrite, DB to-teacher-billing-test만 지정하고 collection 빈 값/다른 role0 확인. 계정명/비밀번호는 건드리지 않고 Add User 제출도 하지 않았다. 사용자 최종 생성 및 secret 준비 후 배포 계속. 제품 코드 변경/테스트 재실행 없음, 기존 변경 보존. CURRENT_STATE/본기록 갱신.

## 2026-10-16 — Atlas 생성 확인·Secret 입력 인계

- 사용자 생성 완료 후 Atlas 목록에서 전용 사용자 readWrite@to-teacher-billing-test 확인(설정 적용 중 배너). 기존 관리자 계정 불변. Docker amd64 빌드 종료0 확인, 아직 ECR 이미지 push/실행 검증 아님.
- AWS Secrets Manager 신규 양식의 다른 유형/키 MONGODB_URI만 준비. 값·저장·IAM 권한 변경은 하지 않음. 사용자에게 전용 연결 URI 직접 입력과 tosunsaeng/test/billing/mongodb 이름 저장을 안내한다. 비밀번호/URI 원문 조회·기록 없음. 제품 수정/테스트 재실행 없음, 문서 갱신 외 예상 밖 변경 없음. 다음은 Secret 확인 후 최소권한/테스트 ECS·ALB 연결이다.

## 2026-10-16 — Atlas 프로젝트 오인 정정

- 이전 Project 0을 테스트 대상으로 오인했던 점을 사용자 지적 후 정정. 사용자가 연 tosunsaeng-test 프로젝트와 Cluster0을 확인했다. 두 프로젝트의 클러스터 이름이 같으므로 환경 식별은 프로젝트까지 포함해야 한다. 테스트 사용자 목록에는 LC/Identity만 있고 Billing 없음.
- 테스트 프로젝트 새 사용자 양식의 readWrite@to-teacher-billing-test만 입력, collection 비움. 계정명/비밀번호 입력 및 최종 생성은 사용자 담당. 기존 Project 0 계정은 삭제하지 않았고 연결 URI도 AWS에 저장/사용하지 않았다. 잘못 생성된 계정 정리는 별도 확인 대상이다.
- CURRENT_STATE/본기록 갱신, 코드·DB 데이터·AWS/기존 사용자 변경 없음, 테스트 미실행(UI 확인·양식 준비). 다음은 올바른 테스트 사용자 생성 확인과 해당 프로젝트 연결정보 준비다.

## 2026-10-16 — 테스트 프로젝트 사용자·연결 호스트 검증

- tosunsaeng-test 프로젝트 사용자 목록에서 tosunsaeng-billing-test/readWrite@to-teacher-billing-test 생성 확인. 동일 프로젝트 Cluster0 Connect→Drivers에서 정확한 연결 호스트 확인, 비밀번호 없는 URI 틀만 사용자에게 안내. 다른 프로젝트 계정은 사용/삭제하지 않는다.
- 외부 설정 변경 없이 조회만 수행, 로컬 상태/작업기록 갱신. 제품 변경·테스트 재실행 없음. 다음은 사용자 AWS Secret 저장 확인이며 실제 DB 연결·ECS 배포 미완료다.

## 2026-10-16 — 테스트 DB Secret 저장 확인·실행권한 적용 전 확인

- Secrets Manager 저장 성공 UI와 describe-secret으로 이름/ARN/교체 메타데이터만 확인. get-secret-value는 실행하지 않았으며 URI/비밀번호를 읽거나 기록하지 않았다. Billing 명칭 IAM role 조회 결과0개.
- 다음 적용은 테스트 ECS 전용 execution role과 Billing 이미지 pull/전용로그쓰기/해당 DB Secret 읽기 최소권한이다. 보안상 신규 접근 부여 직전 사용자 확인을 요청하며 아직 역할/정책/네트워크/서비스 변경 없음. CURRENT_STATE/WORKLOG만 갱신, 코드 변경·테스트 재실행 없음. Secret 저장이 DB 연결 성공을 뜻하지 않음.

## 2026-10-16 — 테스트 execution role 적용 검증·네트워크 사전 조회

- 브랜치 codex/billing-test-deployment. 직전 승인으로 생성한 테스트 execution role의 inline policy 적용 결과를 CloudShell에서 확인했다. 테스트 ECR pull/전용 logs 쓰기/테스트 Mongo Secret 1개 읽기로 제한하며 attached managed policy0, inline policy1개다. 비밀값은 조회하지 않았다.
- 기존 ALB SG, LC 테스트 SG, 사설 subnet 두 개와 정상 NAT 경로를 읽기 조회했다. 기존 LC는 ALB 전용 수신 및443/27017 송신을 사용하며 Billing에는 Redis 권한을 복제하지 않는다. CloudShell Docker 사용 가능, Billing log group 없음 확인.
- 다음 적용 직전 확인 대상: 새 Billing 테스트 SG의 ALB발8082 수신과443/27017 송신. 공인 IP·외부 listener·DNS·Atlas allowlist 변경은 아직 하지 않는다. 이미지 push/ECS 실행/DB health도 미완료다.
- 변경 파일 CURRENT_STATE/WORKLOG, 제품 변경 없음으로 Gradle 미실행. 기존 Dockerfile/.dockerignore와 사용자 dirty 보존, 예상 밖 파일 변경 없음. 업무 flag 기본OFF/인증 계약 불변. 잘못 만든 Project 0 계정은 여전히 별도 정리 확인 대상이다.

## 2026-10-16 — Billing 테스트 전용 보안그룹 생성

- 브랜치 codex/billing-test-deployment. 사용자 직전 승인 범위로 중복 그룹 없음 확인 후 tosunsaeng-billing-test-sg 생성. 새 그룹의 기본 전체 송신 규칙을 제거하고 TCP443/27017 IPv4 송신 및 기존 ALB SG발 TCP8082 수신만 추가했다. describe-security-groups 재조회로 ingress1/egress2와 다른 IPv6/전체포트 규칙 부재 확인.
- 기존 서비스/SG/ALB listener/DNS/Atlas 설정 변경 없음. 새 SG는 아직 task 미연결이고 DB 연결/이미지 push/ECS 실행은 후속. 비밀값 조회 없음, 업무 flags 기본OFF 유지.
- 변경 파일 CURRENT_STATE/WORKLOG, 제품 코드 불변으로 Gradle 미실행. AWS 실제 설정 재조회 및 diff 검사로 검증. 기존 dirty 보존/예상 밖 변경 없음. 다음은 이미지 업로드와 테스트 ECS 구성 및 health 검증이며 외부 공개는 별도다.

## 2026-10-16 — 테스트 ECS 초기 배포 및 DB 포함 health 확인

- 브랜치 codex/billing-test-deployment. 사용자 승인으로 로컬 Docker archive 생성/CloudShell 업로드/해시 일치/ECR immutable60d5f6c push 후 registry logout. 실제 ECR digest로 task1을 등록하고 private Fargate service1대 생성했다. 전용 execution role/SG/Secret 참조만 사용하고 application task role/공인IP/ALB연결 없음. 테스트 로그그룹 신규 생성·14일 보존.
- Atlas 테스트 프로젝트에 기존 NAT /32 Active 확인, 접근목록 변경 없음. ECS running1/pending0/rollout COMPLETED, task/container RUNNING/HEALTHY, 실행digest 일치 확인. 로그 집계 started=true/failed=false/exception0, 원문/Secret 미출력. DB health 포함 성공이며 transaction/schema/업무 기능 검증으로 확대하지 않는다.
- ECR scan COMPLETE: HIGH2/MEDIUM8/LOW4/UNDEFINED4. HIGH는 OpenSSL DTLS CVE-2026-84782의 libssl3/openssl 두 package finding. 외부 공개 전 기본 이미지 보완/재스캔 및 실제 영향 검토가 남는다.
- 변경 파일: 새 배포 runbook, CURRENT_STATE/WORKLOG. 기존 Dockerfile/.dockerignore와 dirty 보존. 제품 코드/다른 서버/ALB/DNS/업무flag/schema 변경 없음, 예상 밖 변경 없음. Gradle 재실행 없음(배포 작업), 로컬 이미지 curl 존재 및 실제AWS health/digest 검증. 다음은 이미지 보안 보완과 인증/schema/ALB 연결 범위 확인. 임시 archive 미삭제, 서비스 실행 비용 발생, 잘못 만든 Project0 계정 미사용/정리 미수행.

## 2026-10-16 — 테스트 이미지 보안 패치 및 연결 준비 확인

- Dockerfile에 공식 Ubuntu openssl/libssl3 upgrade 추가. security1 이미지 archive 해시 일치 후 ECR 업로드·registry logout, digest 고정 task revision2 배포. 새 task RUNNING/HEALTHY 및 service running1/pending0/rollout COMPLETED 확인.
- ECR scan COMPLETE: HIGH/CRITICAL 없음, MEDIUM2/UNDEFINED4 잔존. 전체 clean test 1749개 성공(실패0/오류0/skip0). 잔여 취약점과 실제 transaction/schema/공동 E2E는 별도 검증 대상.
- 사용자 DNS 제공자는 가비아. Billing ACM 인증서 부재 및 실제 LC test task의 Billing JWT/base URL 미설정 확인. 제안 도메인·TLS keystore·LC 공개키·schema/기능 gate 준비 필요. ALB/DNS/SG·상대 서비스·비밀값·업무flag 변경 없음. 외부 공개 전 정확 범위 확인 필요.
- Dockerfile/runbook/CURRENT_STATE/WORKLOG 갱신, 기존 dirty 보존. 예상 밖 변경 없음. 이번 후속 commit/push 없음. 잘못 만든 Project0 계정은 미사용이며 별도 삭제 확인 대상.

## 2026-10-16 — Billing 테스트 도메인 인증서 준비

- 사용자 승인 도메인 billing-test.to-teacher.com / billing-workload-test.to-teacher.com에 ACM DNS검증 RSA2048 인증서1개 요청. ARN suffix13f095e9-1b68-47b1-a215-d1acc8383b06, 서울/테스트 계정. private key 조회/입력 없음.
- 가비아 로그인·to-teacher.com 기존22개 레코드 확인. ACM에서 반환한 CNAME2개만 TTL600 미저장 초안 준비. 최종 저장은 AWS 계정의 해당 두 이름 인증서 발급/갱신 검증을 허용하므로 적용 직전 확인 요청. 기존 DNS/ALB/SG/업무flag/타 서버 불변.
- CURRENT_STATE/WORKLOG만 기록 추가, 제품 코드 변경 없음으로 Gradle 미실행. 실제 UI/ACM 반환값 대조. 기존 dirty 보존/예상 밖 변경 없음. 인증서 ISSUED·DNS 반영·실제 연결은 아직 미완료.

## 2026-10-16 — DNS 검증 레코드 저장 및 ACM 발급 완료

- 사용자 「어」로 적용 직전 승인받은 검증 CNAME2개만 가비아에 저장. 완료 알림과 목록24개/두 exact 값·TTL600 확인. 기존22개 레코드 수정 없음.
- ACM 서울 certificate13f095e9-1b68-47b1-a215-d1acc8383b06 최종 ISSUED 및 두 도메인 SUCCESS 확인. CloudShell dig는 미설치로 독립 DNS 질의 미실행이며 ACM의 실제 DNS 검증 성공을 근거로 기록한다.
- 트래픽 CNAME/ALB listener/SG/LC JWT·TLS/업무flag 변경 없음. 다음은 인증·전용 connector 준비와 제한된 ALB 연결이며 외부 공개 범위 적용 직전 확인이 필요하다.
- CURRENT_STATE/WORKLOG/runbook 기록만 변경. 제품 코드 불변으로 Gradle 미실행, UI와 AWS 상태로 검증. 기존 dirty 보존/예상 밖 변경 없음, commit/push 없음.

## 2026-10-16 — LC 테스트 workload 키 생성 사용자 인계

- LC 키가 없다는 사용자 응답으로 새 준비 진행. AWS billing/workload/tls 이름 검색은 Mongo Secret만 반환(다른 이름 저장소 부재까지 증명하지 않음). CloudShell boto3/cryptography import 확인.
- 사용자 실행용 명령을 터미널에 입력만 했다. 계정889384901776 확인·기존 동일 Secret 거절·LC 전용RSA3072 생성·PRIVATE_JWK 비밀 저장·공개 JWK set만 출력한다. 대상은 tosunsaeng/test/learning-core/billing-workload-jwt, kid lc-test-billing-20261016-r1. Enter 실행은 사용자에게 인계하며 생성 성공으로 기록하지 않는다. 개인키/토큰 조회·출력 없음, 권한 추가/LC 배포 없음.
- 실제 코드 확인: Billing workload ON은 독립 port/TLS PKCS12 파일+password, requireTransactions/initializeIndexes, scope, 환경/공개키를 요구한다. 현 ECS는 이 조건 미충족. ACM ALB 인증서를 task TLS 파일로 간주하거나8082 우회 연결하지 않는다.
- 기록만 변경, 제품 코드 불변/Gradle 미실행. CURRENT_STATE/WORKLOG 갱신·diff 검사. 기존 dirty 보존/예상 밖 변경 없음. 다음은 사용자 키 생성 확인 후 공개키 대조와 TLS 파일 안전 주입 준비, schema·제한된 ALB 접근 범위 확인이다.

## 2026-10-16 — LC 키 생성 확인 및 Billing TLS 사용자 인계

- 사용자 실행 후 CREATED 출력과 describe-secret 메타데이터의 AWSCURRENT 확인. LC secret ARN suffix billing-workload-jwt-mRtG3m, version f141d0ee-7a5c-4933-bb30-b14c8579e55d. GetSecretValue 미실행. 공개 출력만 파싱: 정확7개 공개field/kid/RSA modulus512 base64url문자, /private/tmp/lc-test-billing-public-jwks.json에 보관(저장소 외부). 실제 서명/E2E 검증은 아직 아니다.
- cryptography PKCS12 지원 확인. 사용자 실행용 TLS 생성 명령을 입력만 함: 테스트 계정 확인·동일Secret 있으면 중단, 자체서명RSA3072/SAN billing-workload-test.to-teacher.com/365일/serverAuth PKCS12와 랜덤 비밀번호를 tosunsaeng/test/billing/workload-tls에 저장하고 fingerprint/만료만 출력. 비밀값 입력/생성 실행은 사용자 인계이며 성공으로 기록하지 않는다.
- 외부 ACM 검증 TLS와 내부 ALB target TLS를 구분한다. ALB target TLS의 인증서 신뢰 검증을 주장하지 않으며 JWT/SG/source 제한이 필요하다. 실제 Secret 읽기 권한 추가·ECS 파일주입/ALB 연결·LC 배포·schema/기능ON은 미수행. 후속에 제한적 권한·네트워크 적용 직전 확인 필요.
- CURRENT_STATE/WORKLOG만 변경, 제품 코드 불변으로 Gradle 미실행. UI/메타데이터 확인 및 diff 검사, 기존 dirty 보존/예상 밖 변경 없음.

## 2026-10-16 — Billing TLS 생성 확인·실행역할 권한 적용 준비

- 사용자 실행 후 CREATED, 인증서 fingerprint/만료 출력과 describe-secret AWSCURRENT 확인. Secret ARN suffix workload-tls-tgOfqJ, version2be0814f-3837-4882-90bd-2fa16a09a1c8. 만료2027-10-16T10:31:41Z(한국19:31), SHA256 082cd179b0666b891d27f8697c37caa81bca7a78e0f72e9f8f5e3a3b0aa677c6. Secret 값·개인키·비밀번호 미조회.
- 현재 billing-test-execution-minimal을 읽기 확인: 전용ECR/log/Mongo Secret만 허용. 다음은 기존 policy 보존 후 TLS Secret 정확1개에 GetSecretValue만 추가하는 별도 inline policy 적용 직전 승인이다. LC private JWT Secret 권한은 Billing에 주지 않는다.
- 현재 배포 컨테이너의 TLS 파일 로딩·서명·실제 handshake는 아직 검증하지 않았다. ALB/DNS/SG/LC 배포/schema/flag 변경 없음. 기록만 변경, Gradle 미실행/실제UI·메타데이터 및 diff검사. 기존 dirty 보존/예상 밖 변경 없음.

## 2026-10-16 — 테스트 execution role TLS Secret 읽기 권한 적용

- 사용자 적용 직전 승인 「어」 후 list-role-policies로 새 policy 중복 없음 확인. tosunsaeng-billing-test-execution-role에 billing-test-workload-tls-read를 별도 추가했다. Action secretsmanager:GetSecretValue, Resource arn:aws:secretsmanager:ap-northeast-2:889384901776:secret:tosunsaeng/test/billing/workload-tls-tgOfqJ 단1개. get-role-policy로 동일 명세 재조회 확인.
- 기존 billing-test-execution-minimal은 수정하지 않았다. LC private JWT/다른 Secret/쓰기/삭제 권한 확대 없음. 비밀값 조회 없음. 서비스 task/SG/ALB/DNS/schema/업무flag 변경 없음. 실제 ECS의 Secret 주입·TLS 기동은 후속 검증 대상.
- CURRENT_STATE/WORKLOG/runbook 기록 갱신. 제품 코드 불변으로 Gradle 미실행, 실제AWS 권한조회·diff검사. 기존 dirty 보존/예상 밖 변경 없음. 다음은 TLS 파일 주입과 schema/scope 환경 준비 후 제한된 connector 연결이다.

## 2026-10-16 — TLS 파일 주입·Java 호환·격리 HTTPS 사전 검증

- 브랜치 codex/billing-test-deployment. scripts/ops/billing-tls-preflight.sh 추가 및 runbook/CURRENT_STATE/WORKLOG 갱신. 실제 secret 없이 로컬 동일 이미지 도구 확인 후 가짜 인증서 정상 성공·손상keystore 거절·hostname mismatch 거절 확인. 잘못된 입력은 단계만 출력한다.
- 사용자 요청한 배포 검증으로 Fargate 일회성 tosunsaeng-billing-test-tls-preflight:1/task2798e512fc6342e48696a2e23be90174 실행. 기존 patched digest/실행역할/사설subnet/SG, 비root10001, task role·공인IP·port mapping·ALB 없음. 실제 TLS Secret2필드만 주입했으며 Mongo URI/LC private key 미제공. 해당 실행은 짧은 Fargate 비용이 발생하며 task는 STOPPED/exit0으로 자동 종료됐다.
- 로그 TLS_PREFLIGHT_OK java_pkcs12=true fingerprint=true verified_https=true 확인. Java keytool PKCS12/PrivateKeyEntry·기대지문·잔여기간·curl CA+hostname 검증 성공. 내부127.0.0.1 OpenSSL 서버 검증이며 Spring/Tomcat connector/ALB·JWT·업무E2E 성공으로 확대하지 않는다. 비밀키/비밀번호를 출력하거나 모델로 읽지 않았다.
- Identity 실제 test task44의 scope 환경변수만 읽기 대조: tosunsaeng-billing-test. workload test/payment SANDBOX/독립8443 후보와 schema4·트랜잭션 요구를 runbook에 정리. 기본 initializer가15개collection/index 및 FREE_EXAM_ONCE 정의를 생성하므로 다음 적용 전 대상DB 상태 확인과 변경범위 확인 필요. 실행서비스/DB/ALB/DNS/SG/LC/업무flag는 미변경.
- ./gradlew clean test 성공3m52s, XML1749/실패0/오류0/skip0. sh -n/diff 검사 통과. 기존 dirty 보존, 예상 밖 변경 없음. 운영 helper 외 제품Java/외부계약 불변. commit/push/머지 없음.

## 2026-10-16 — 기본 테스트 DB 초기화·실제 Billing HTTPS 기동

- 브랜치 codex/billing-test-deployment. 사용자 승인범위는 비어 있는 테스트DB 기본스키마·LC 수신HTTPS8443이며 ALB외부연결/결제·환불·삭제 및 LC배포는 제외. Atlas tosunsaeng-test/Cluster0에 Identity·LC DB만 있고 Billing DB 부재를 확인한 뒤 진행했다.
- scripts/ops/billing-workload-entrypoint.sh 추가. URI의 기대host/database 불일치 시 비밀값 없이 종료, TLS base64→비root10001/umask077 임시PKCS12·공개CA파일, 지문/잔여기간 검증 후 기존Java 실행. 실제 secret값을 코드·로그에 넣지 않는다. app image digest는 기존security1 그대로다.
- task3는 기존task2에서 승인env만 변경: LC receiverON/IdentityOFF,8443,test/SANDBOX,scope tosunsaeng-billing-test,requireTransactions/initializeIndexes/schema4. LC 공개JWK와 TLSSecret2필드 참조만 추가. private LC Secret 권한/주입 없음. MONGODB_URI guard 통과 및 TLS bootstrap 성공, 실제Tomcat8443/ApplicationStarted 확인(error집계0).
- Atlas에서 Billing 기본15개collection 및 benefit_definitions FREE_EXAM_ONCE 1개/기존승인policy 확인. 지급Grant나 사용자시험/결제자료 생성 아님. 기본initializer에 따른 index 설치·정합성 점검은 앱 기동 범위이며 모든index별 독립인벤토리/실Transaction 경합은 이번 미수행.
- 새task c003b0b394b74ae59f495b4262f89175 RUNNING/HEALTHY·기존patched digest일치. health명령은 DB포함8082 health성공, CA+hostname 검증8443 status unsigned401,8082 같은route404를 검사. service running1/pending0/단일task3 rollout COMPLETED/loadBalancers=[] 확인. 기존서비스·SG/DNS/ALB/상대배포 변경 없음. AWS capability verifier는 replica-set/session 지원 확인이며 실제Transaction commit 검증과 구분한다.
- sh구문/가짜URI wronghost·wrongDB거절/정상target TLS필수조건 확인. ./gradlew clean test 성공3m56s, XML1749/실패0/오류0/skip0. diff검사 통과. 기존 dirty 보존/예상 밖 변경 없음. runbook/CURRENT_STATE/WORKLOG 갱신, commit/push 없음. 다음은 정확host/path/source 제한 ALB·DNS 적용 전 승인 및 LC signer/업무E2E다.

## 2026-10-16 — 승인된 workload ALB·DNS 연결

- 브랜치 codex/billing-test-deployment. 적용 직전 host/path/source/port 범위 안내 후 사용자 「어」 승인으로 기존 ALB443 인증서 추가, HTTPS8443 target group, Billing SG의 ALB발8443 ingress, listener90~95 allow/96 host404 및 ECS서비스 연결을 적용했다. 기존 listener규칙 비교보존, LC/Identity·업무flag·키권한은 불변이다.
- 가비아 billing-workload-test CNAME/TTL600 추가저장(24→25), 앱용host는 미연결. 외부 CloudShell POST status404/TLS_VERIFY0 확인. 새 Billing task00f72182287f4eeca57c259b9d7b44f7 RUNNING/HEALTHY 확인, 배포최종상태는 후속검증한다.
- 비밀값·taskRole·공인IP 없는 일회성 동일네트워크 probe1은 예약/status401 후 phone404에서 기대값불일치로 종료. 코드 확인상 owner-rebind/attempt featureOFF의 정상404이며 기능을 켜지 않고 probe2 기대값을 수정했다. 최종결과는 후속append한다.
- runbook/CURRENT_STATE/WORKLOG만 갱신, 제품코드/외부wire 불변으로 Gradle미실행(직전1749성공 별도). 기존 dirty보존·예상밖 변경 없음·commit/push/Jira 없음. signed LC E2E·실업무/환불/삭제 활성화는 남는다.

### ALB 연결 최종 검증

- target group8fcb6e1f4e6f6660 healthy/8443, ECS서비스 running1/pending0/COMPLETED 확인. probe2 task48750e29964a4859ac82d0b03c299d0a STOPPED/exit0 및 ALB_NETWORK_PROBE_OK; probe1도STOPPED/exit1로 남은실행없음.
- 허용네트워크 예약/status/confirm/cancel401, OFF phone/attempt404, 비허용health/public/deletion/Identity404, GET status404. 모든curl은 실제DNS/CA검증이며 -k 없음. 외부CloudShell404/TLS_VERIFY0과 함께 확인했다. LC 실제서명/권한별JWT/업무E2E 성공으로 확대하지 않는다.
- 문서diff검사통과. 코드/테스트/상대서버 변경 없음. 다음은 LC담당의 전용키참조·baseURL·sender배포 및 공동JWT/업무검증이며 별도승인범위다.

## 2026-10-16 — 프론트 3서버 통합 API 정리

- 브랜치 codex/billing-test-deployment. 사용자 요청으로 Identity/LC/Billing 사용자API를 통합 정리했다. Identity HEAD b05d6f18, LC2e24f10 및 현지 Controller/문서 읽기, Billing60d5f6c·현지 DTO/reader 확인. 상대저장소 수정·실서비스 호출 없음.
- docs/contracts/FRONTEND-INTEGRATED-API-GUIDE-2026-10-16.md 신규. 인증/프로필/SNS복구/문의, 결제4API, 모의고사/챌린지/학습/알림/삭제 목록과 결제→시험→환불 흐름, canTakeExam3값/paid.status READY·PENDING, LC공개오류를 기록했다. 기존8월문서의 미구현표현을 최신상태로 오인하지 않도록 설명했다.
- 앱용Billing주소 미연결/서버용주소 앱사용금지, Identity/LC 배포·flag 미검증을 명시. 제품계약·코드·인프라·Notion·Jira·commit/push 변경 없음. CURRENT_STATE/WORKLOG 갱신, 기존dirty보존/예상밖 변경 없음.
- 문서 정적검사·git diff --check 수행. 코드변경 없어 Gradle미실행. 다음은 프론트가 필요한 기능별 DTO/fixture 합의와 실제public배포·사용자JWT/SDK·LC공동검증이다.

## 2026-10-16 — Notion 시험 생성 API 작성

- 브랜치 codex/billing-test-deployment. 사용자 지정 Notion 페이지3f9dcc5dbaeb80618ec9ed3e5d8a5966의 기존sync 상세양식을 읽고, 이미 있던5번 시험생성 제목 아래 설명·상세토글·5개섹션·요청/성공/오류예시·오류표를 추가했다. 기존1~4본문 유지.
- LC CreateSessionResult/QuestionDTO/BaseResponse/ErrorStatus와 기존계약 읽기 확인. LC주소·사용자토큰·body없음·동일키재시도/의도적새시작 구분·종합판정null/402·409·429·503 대응을 기록했다. 예시문항은설명용이며 실배포/공동검증완료로표시하지 않았다.
- UI에서 토글 내 제목/코드블록/오류표 렌더링 확인 및 스크린샷 저장. 코드/설정/상대저장소/Jira/배포/commit/push 변경 없음. 문서작업으로 Gradle미실행. CURRENT_STATE/WORKLOG 갱신·기존dirty보존. 다음은 프론트검토와 실제LC공동검증이다.

## 2026-10-16 — 일괄 테스트 준비: 환불 런타임 기동 보완

- 사용자 보완 요청, codex/billing-test-deployment 작업트리. TMI-220 조회 후 Billing 코드만 수정했다. 원본 billing 작업트리와 기존 미커밋 변경은 보존했다.
- PaymentRefundConfiguration의 무조건 ON 거절을 test 단일 profile/workload test/SANDBOX/실제 test 이름 DB/recovery fence 조건부 허용으로 한정 변경했다. 기본 OFF, OFF/ON 오류, production/staging 차단과 기존 런타임·schema·키·알림 의존성 검증은 유지한다. DB 이름 검사는 실환경 격리 보장이 아니다.
- LC origin/private JWK 환경변수 바인딩 추가, cleanup purge/alert 미구현 오류 분리. 실제 Store 증거 adapter·cleanup adapter를 가짜 성공으로 대체하지 않았으며 자동삭제 차단 유지.
- 신규 기동 회귀 14건과 활성화 준비 안내 추가. 최초 선택 테스트 31건 중 테스트 구성 오류 1건을 발견했고, Spring Environment 교체 대신 active profile만 지정하도록 수정했다. 전체 clean test 결과는 후속 기록에 남긴다.
- 최종 검증: `./gradlew clean test` 성공(4분37초), XML 합계 1,763건/실패0/오류0/skip0. `git diff --check` 통과. 예상 밖 추가 변경 없음. 로컬 회귀이며 실제 Store·LC 공동 E2E 검증 결과가 아니다.
- API/body/schema·제품 정책 불변. AWS/Atlas/RevenueCat/LC/Identity 변경, 배포·활성화·Jira 변경·commit/push 없음. 다음은 실제 Store 증거/cleanup 연결과 외부 설정 준비이며 이번 보완을 전체 기능 ON 가능 또는 공동 E2E 완료로 해석하지 않는다.

## 2026-10-16 — 승인된 테스트 Secret 권한 및 기존 문의 채널 연결

- 사용자 `켜서 올려줘` 후 현재 상태 확인: Billing task3/running1, LC task39/running1이나 환불 receiver OFF. bootJar 성공. 활성화에 필요한 키권한·Slack 선택을 확인 요청했고 `그대로 해줘`로 승인받았다.
- Billing test execution role에 refund-workload-jwt 및 Identity test support-slack 정확2개 GetSecretValue 정책 추가·재조회 일치 확인. 기존 정책·운영/상대 역할은 보존. Secret 원문 조회·출력·재생성 없음.
- 일회성 task definition tosunsaeng-billing-test-refund-secret-probe:1 및 task 2ffb0593e0964b2687cbcf2205fc35b6 실행. 기존 이미지/사설네트워크, DB비밀값 미주입·task role 없음·포트없음. 비어있지 않은 서명키 주입과 curl HTTPS Slack200/ok 검사. 실제 문의-테스트 메시지 23:20 수신을 UI로 확인하고 /private/tmp/billing-test-slack-proof-20261016.png 저장.
- 제품코드 추가 변경·DB설치/삭제·상시서비스/LC/Identity 변경·배포·commit/push 없음. 기존 미커밋 보존. 전체회귀1763건은 직전 코드검증이며 이번은 실환경 Secret/네트워크 검증이다. Billing schema 설치·새 이미지 배포·LC receiver 준비·서명/업무 공동검증은 남는다.
- 최종 일회성 태스크 STOPPED/EssentialContainerExited/exit0 확인. 실패/실행 중 태스크를 남기지 않았으며 문서 diff 검사 통과.

## 2026-10-16 — 테스트 HMAC 준비 및 스키마 설치 중단 진단

- codex/billing-test-deployment. 사용자 승인한 payment-runtime Secret을 암호학적 난수32byte로 생성하고 테스트 실행 역할에 정확1개 GetSecretValue 정책을 추가/재조회했다. 기존 Secret 덮어쓰기·운영역할 변경·비밀값 출력 없음.
- scripts/ops/BillingTestSchemaBootstrap.java 추가: test DB/host exact 검사, writer중지·empty guard, 기존 명시 installer, read-only verify, 별도 OPEN CAS. 로컬 임시 Mongo install/verify/reinstall/open 성공, 잘못된 host·비어있지 않은 업무자료 거절. 컨테이너는 중지/미삭제. 예외는 클래스·숫자코드·Billing 소스위치만 출력하도록 보완/컴파일했다.
- clean test 1763건/실패0/오류0/skip0(3분56초), bootJar·amd64 이미지 빌드 성공. 실제 DB는15 collection/기본혜택1건 외0건 확인. Billing만 중지/잔여task0 확인 후 schema revision2 install: MongoCommandException/exit1. revision3 read-only verify는 foundation/recovery/paid 뒤 RefundCoreSchema validator 불일치로 실패. 일부 DDL 및 ALL_BLOCKED control1건이 남아 있고 환불 schema 완료는 아니다. 최초 오류코드 미수집으로 collMod 권한원인은 아직 추정이다.
- 기존 task3 desired1 복구 후 running1/pending0 확인; 컨테이너 health는 조회시 UNKNOWN으로 HEALTHY 완료를 주장하지 않는다. LC task39 refund receiver OFF/8080만 확인했고 상대서버 무변경. 새이미지 ECR 업로드·교체/환불 ON/OPEN 전환·데이터삭제는 하지 않았다.
- CURRENT_STATE/runbook 갱신. 기존 dirty와 원본 작업트리 보존, 예상 밖 코드 변경 없음. API/body/환불정책 불변. Jira/commit/push 없음. 다음은 DB 설치 오류코드/권한 확인 및 승인된 유지보수로 미완료 schema 수렴, LC receiver 준비 후 새 이미지 공동 활성화다. 스키마 검증 실패를 우회하지 않는다.

## 2026-10-16 — collMod 진단 및 승인된 테스트 스키마 설치

- codex/billing-test-deployment, TMI-220 테스트배포 후속. writer 중지/자동서비스복구를 포함한 동일 installer 진단으로 collMod line81/code8000 확인. Atlas 테스트 사용자 readWrite only 확인 후 최소 collMod 추가·회수 승인을 사용자에게 받았다.
- custom role billing-test-schema-collmod는 to-teacher-billing-test/all collections/collMod만 포함. 해당 사용자에 임시 연결, 기존readWrite/다른DB/비밀번호 보존. schema task04d0a8e59a3d4535bf6aa8c91ca0b7c0 STOPPED/exit0/BILLING_SCHEMA_OK install 확인. 설치 및 exact verify 성공, 데이터삭제/샘플결제/OPEN 전환 없음.
- 기존 task3 desired1/running1/pending0/COMPLETED 복구 확인. 새이미지배포/환불ON/상대서버/Jira/commit/push 없음. 제품코드 변경 없어 Gradle 재실행 없음(직전1763개 성공과 별개인 실DB검증). 기존dirty보존, 예상밖 코드변경 없음.
- **주의: 임시권한 회수 저장이 Atlas 로그인만료로 거절됨.** 로그인 화면으로 전환하고 사용자에게 재로그인 요청. 권한이 아직 남아 있으므로 회수완료로 보고하지 않는다. 로그인 후 사용자 custom role 연결만 제거하고 재조회해야 한다. 새 권한부여/스키마 재실행 없이 회수를 최우선으로 진행한다.
- CURRENT_STATE/활성화 runbook 갱신. 실환경 성공 증거 /private/tmp/billing-schema-success-20261016.png. 후속은 임시권한회수, LC receiver 준비 및 새이미지 공동 활성화다.

## 2026-10-17 — 로그인 복구 후 임시 collMod 회수

- codex/billing-test-deployment, TMI-220 테스트배포 후속. 사용자 로그인 완료 회신 후 기존 Atlas 테스트 프로젝트에 접속하여 Billing 테스트 사용자 custom role 연결만 제거/저장했다. 사용자 목록의 readWrite@to-teacher-billing-test 단독 표시 확인. 비밀번호·다른DB/사용자·운영 권한 불변. 역할 정의 자체는 미삭제이며 사용자에게 부여하지 않는다.
- CURRENT_STATE/runbook 갱신, 이전 WORKLOG 보존. 제품코드·데이터·스키마·배포·feature flag·Jira·commit/push 변경 없음. 코드 변경 없어 Gradle 미실행, UI 저장결과 및 문서 diff 검사로 검증. 기존dirty보존/예상밖 변경 없음. 다음은 LC receiver 준비 확인과 새이미지 배포·공동 활성화다.

## 2026-10-17 — 새 이미지 준비 및 업로드 차단

- 새 amd64 이미지 billing-test:refund-20261017 빌드/export 완료(앞선 실행), tar /private/tmp/billing-test-refund-20261017.tar. 기존 Billing task3 및 LC task39/receiver false 확인. 환불 발신을 켜거나 LC 설정을 수정하지 않는다.
- CloudShell 업로드 메뉴가 대화상자를 열지 않음. 첫 터미널 선택·페이지 새로고침·끊긴 터미널 재연결 및 화면 기준 메뉴 선택 후에도 미해결. 새 이미지 업로드/ECR push/task 교체 미완료. 사용자 업로드 도움 요청 대상으로 남긴다.
- CURRENT_STATE/WORKLOG만 갱신. 제품 코드/DB/권한/배포/flag/Jira/commit/push 무변경, 기존 dirty 보존. Gradle 재실행 없음.

## 2026-10-17 — CloudShell 업로드 복구 및 Billing task4 배포

- 사용자 재시도 요청. 네이티브 Chrome에서 Billing 전용 탭 활성화/파일 선택으로 130MiB 이미지 tar 업로드 성공. 기존 웹 메뉴 조작 실패를 해소했다. 다른 LC 터미널 명령은 실행하지 않고 전용 f9d0b0a2 세션 사용.
- 테스트 ECR refund-20261017 push 완료, digest 1b15b2b1037c8d1ef5511d5860ca2fb85c1fc8edbd30f0948a4917c04b807666. scan COMPLETE/HIGH·CRITICAL0/MEDIUM2/UNDEFINED4 확인.
- 현 service task3/running1/pending0 확인 후 image만 교체한 task4 등록 및 Billing 테스트 서비스 update. 등록 가능 설정 비교 ONLY_IMAGE_CHANGED True. 최종 ECS PRIMARY COMPLETED/running1/pending0, task4 RUNNING/HEALTHY 및 새 ALB target healthy 확인(기존 target draining).
- 환불OFF·ALL_BLOCKED·LC/Identity·DB·권한·네트워크 불변. 새 기능 활성화/Store/업무 E2E는 수행하지 않았다. 제품코드 무변경으로 Gradle 미재실행(직전1763건과 별개인 배포검증). CURRENT_STATE/runbook/WORKLOG 갱신. 기존dirty보존, 예상밖 코드변경 없음. Jira/commit/push 없음. 증빙 /private/tmp/billing-test-revision4-healthy-20261017.jpg.

## 2026-10-17 — 누락 스키마 보완 및 승인된 삭제 ALB 경로 추가

- 브랜치 codex/billing-test-deployment, TMI-217/220 테스트 연결 후속. LC task41 receiver/TLS/공개키 대응을 읽기 확인했으며 상대 설정은 무변경.
- BillingTestSchemaBootstrap에 PaymentSyncSchema·DeletionSchema install/verify 및 빈 테스트DB에 한정한 OPEN guard 보완. javac 성공 후 실제 maintenance task6 exit0/BILLING_SCHEMA_OK install. writer 중지 후 finally 서비스 복구, task4 desired1/running1/pending0/COMPLETED 및 ALB healthy 확인. ALL_BLOCKED 유지·자료삭제 없음.
- 사용자 명시 승인 후 priority89에 삭제 POST 한 경로만 추가. 지정 host/NAT /32/HTTPS8443 유지, 기존19개 규칙 재조회 불변 검증. 운영/다른 route·IAM 권한 무변경. JWT 검증 설정 및 업무 flag 무변경.
- CURRENT_STATE·활성화 runbook·WORKLOG 기록. 기존 dirty/원본 작업트리 보존, 예상 밖 제품코드 변경 없음. Gradle 미재실행(maintenance 실제 검증 수행), Jira/commit/push 없음. 다음은 의존 설정 묶음 배포 및 안전한 fresh-test barrier 전환, 정상서명/시험/환불/삭제 공동 검증이며 아직 완료 아님.

## 2026-10-17 — 묶음 활성화 task5 기동 실패와 task4 복구

- codex/billing-test-deployment/TMI-217·220. 사용자 계속 요청으로 의존 flag·기존 Secret3개 주입을 task5에 묶어 등록했다. IAM/ALB/SG·LC/Identity는 무변경, 실제 Store 및 자동 purge는 OFF 유지. JWKS HTTPS200/키1개 확인.
- writer중지·빈 DB/schema 검증 후 maintenance7 OPEN exit0. barrier OPEN은 유지되며 과거 완전성 증거로 간주하지 않는다. task5 RefundAccessPublisher의 private JWK key_ops 누락으로 exit1. RSA3072·알고리즘·용도·필드 존재만 출력했고 비밀값은 미출력.
- 즉시 task4 복구 요청, running1/pending0·ALB healthy 확인. 확인 시 rollout IN_PROGRESS/container health UNKNOWN. probe3 STOPPED/exit1이며 새 인증/업무 성공으로 기록하지 않는다.
- 동일 Secret의 동일 키에 sign metadata만 넣는 사용자 직접 실행 명령 준비(미실행). 안내가 명령 준비보다 먼저 나가 사용자 빈 Enter가 발생한 점을 설명하고 실제 명령을 준비했다. 후속은 실행 확인→task5 재배포→인증/업무 검증. CURRENT_STATE/runbook/WORKLOG 갱신, 제품코드 무변경으로 Gradle 미실행, 기존dirty보존·예상밖 변경/Jira/commit/push 없음.

## 2026-10-17 — 사용자 서명키 보완 확인 및 task5 활성화 배포 완료

- codex/billing-test-deployment/TMI-217·220. 사용자 실행 성공과 private JWK key_ops=[sign] 확인(비밀값 미출력). 같은 task5를 force deployment하여 PRIMARY COMPLETED/running1/pending0·RUNNING/HEALTHY·ALB healthy 확인했다. 이전 task4는 트래픽에서 제외/draining. barrier OPEN 유지.
- 같은 사설 네트워크에서 probe3 재실행 task768adcecbcf94931841d1d91d84f382c exit0/ALB_ACTIVATION_PROBE_OK. 6개 workload 경로의 무인증/invalidJWT401, OFF/비허용4경로404, GET삭제404. 정상서명 및 업무E2E는 미실행으로 분리한다.
- 시험/상태/삭제/환불후속 의존 flag 묶음ON, provider/Store/자동purge/Identity/phone OFF·public8083 외부미노출 유지. IAM/네트워크·LC/Identity/운영·제품코드 무변경. 기록3문서만 갱신, 기존dirty보존·예상밖 변경 없음. Gradle미실행(배포검증), diff검사 수행. Jira/commit/push 없음. 다음은 LC와 정상서명·시험생성·환불·삭제204 공동 업무검증.

## 2026-10-17 — 공동 업무E2E 선행조건 확인

- codex/billing-test-deployment/TMI-217·220. 공동 검증 runbook 및 LC 현행 준비 기록/테스트 README·application.yml 읽기, AWS task41/running1/pending0 확인. 실제 상태 writer/publisher=false, 삭제sender 미설정/defaultfalse로 전체 상태·삭제204 연결은 미준비다.
- LC 범위는 기본 읽기 전용이므로 임의 활성화하지 않았다. 격리 테스트 계정 미지정, Store/환불증거 미준비이며 사용자/원장/시험자료를 생성하지 않았다. 정상서명/업무E2E 미실행·통과 주장 없음. 로컬용 auth 스크립트를 실환경에 무변경 실행하지 않았다.
- 다음은 LC 담당의 세 flag 활성화 또는 사용자 명시적 LC 테스트 설정 변경 승인, 테스트계정 지정·안전한 인증 전달 경로 확인이다. 비밀번호/토큰을 채팅으로 요청하지 않는다. CURRENT_STATE/WORKLOG만 갱신, 제품코드/배포/DB/IAM/Jira/commit/push 무변경·기존dirty보존. 분석 작업으로 Gradle 미실행, diff 검사 수행.

## 2026-10-17 — LC 테스트 삭제 전달 DB 준비와 승인된 테스트 자료 정리

- codex/billing-test-deployment/TMI-217·220. 사용자 LC 세 flag 활성화와 빈 DB 준비 승인 후 최초 유지보수는 challenge_10s_attempts 2건 발견으로 쓰기 전 중단(exit1). task41 desired1/running1/pending0/COMPLETED, health200 및 두 target healthy 복구 확인. 이를 DB 준비 성공으로 처리하지 않았다.
- 후속 사용자가 해당2건 보존 불필요·진행을 명시 승인했다. LC writer desired0/drain 후 정확 test host/database, 시험·생성operation·삭제operation/target·상태outbox0 및 rollout 기존 네 준비필드를 확인했다. challenge ID목록2개만 대상으로 삭제2건/잔여0 검증. 다른 사용자 자료·S3·운영DB·Identity·Billing DB는 변경하지 않았다. 별도 backup은 없고 복구를 보장하지 않는다.
- 유지보수24df20cca3544370bb406f026aaa8ae9 exit0. 삭제 target의 prepared/billingDelivery.state/billingDelivery.nextAttemptAt/_id nonunique/nonpartial/nonTTL index 설치·옵션 재검증 및 linked target expiry 부재 확인 후 billingDeliveryVerified=true 저장. NOT_E2E 표식으로 저장소 준비와 전체연동 검증을 구분했다. finally task41 desired1 복구 요청.
- 다음은 세 flag만 ON으로 한 LC test 배포 및 건강 확인. 이미지/키/IAM/네트워크·업무 계약 변경 없음. 제품코드 변경이 없어 Gradle 미실행, 실제 유지보수로 검증했다. CURRENT_STATE/WORKLOG 갱신, 기존dirty보존·예상밖 로컬 코드 변경/Jira/commit/push 없음.

### LC task42 활성화 배포 확인 중

- task41을 복제해 ATTEMPT_GROUP_EVENT_WRITER_ENABLED·ATTEMPT_GROUP_EVENT_PUBLISHER_ENABLED·BILLING_EXAM_DELETION_SENDER_ENABLED만 true로 등록했다. ECS environment 배열 재정렬 때문에 최초 전체배열 비교가 중단됐고 서비스는41을 유지했다. 이름별 env 비교·세 값 외 전체 등록설정 exact 비교 성공 후42 배포를 요청했다. 이미지·Secret·IAM·포트·네트워크는 불변이다.
- task42 Tomcat8080/8444 기동 및 양 target healthy 확인, 신규running1/pending0·failed0, 구41running0로 교체 진행. 이 시점 rollout IN_PROGRESS라 최종완료와 구분한다. 일회성 유지보수 정의1·2는 INACTIVE 처리해 재실행 노출을 줄였으며 로그는 보존했다.
- 지정 계정은 Firebase UID이며 사용자 로그인 불가 확인. Identity UUID/JWT·구매 권한·Store 사실은 임의 생성하지 않았다. 실제 정상서명/시험 생성·환불·삭제204 업무 E2E는 미완료다. 최종 ECS 안정화 확인 후 상태를 갱신한다.

### LC task42 배포 완료

- ECS 최종 재조회 task42/running1/pending0/단일 COMPLETED, 세 flag 모두true, 외부 health200 확인. 앞선 새8080/8444 target healthy 후 구task draining을 거쳐 교체됐다. services_stable waiter가 IN_PROGRESS에서도 반환한 중간 결과를 완료로 취급하지 않고 별도 rollout COMPLETED를 확인했다.
- CURRENT_STATE·본 WORKLOG·테스트 환불 활성화 runbook에 기록. 제품코드 변경 없음·Gradle 미실행, 실제 ECS/DB/HTTP 기동 검증 및 git diff --check 통과. 이번 범위 밖 dirty와 원본 작업트리 보존, 예상 밖 코드 수정/Jira/commit/push 없음. 후속은 정상 로그인 가능한 계정으로 시험 생성·상태 전달·삭제204 및 별도 검증된 환불증거 공동E2E다. 실제 Store/운영·자동purge는 이번 요청으로 확대하지 않았다.

## 2026-10-17 — 앱용 테스트 결제 연결 사전 점검

- codex/billing-test-deployment/TMI-212·PLAN010/013 후속. 사용자 요청으로 앱용 API·provider 활성화 선행조건을 실제 AWS/RevenueCat에서 읽기 점검했다. task5에는 provider 설정/Secret이 없고 sync/catalog/worker/ingress는 기본OFF, 앱8083는 connector만 있고 ECS port/ALB 연결과 billing-test DNS가 없다. 기존 서버용8443는 보존했다.
- RC 프로젝트70a245ef: Play appc95ad9b387, Apple app3987acee69, bundle com.toteacher.app. premium1d/3d/7d/14d/28d가 각 store에 등록돼 있고 Google Published/Apple Ready to Submit이다. 이는 실제 구매/검증 통과 증거가 아니다. Offering package 대응·Billing DB catalog는 후속 대조 대상이다. Test Store 월/연/lifetime 상품은 승인된 Apple/Google 소모성 상품에 혼합하지 않았다.
- Secret API keys 없음·active integrations0 확인. UI에서 billing-test-purchase-read / V2 / Purchases Configuration Read only, 다른 권한No access 초안 준비만 했다. Generate는 누르지 않았고 비밀값을 읽거나 저장하지 않았다. 키 이름은 테스트용이지만 프로젝트 범위 권한이므로 production 구매까지 조회 가능한 점을 사용자에게 안내한다. 생성은 사용자 handoff, 저장·최소IAM 연결은 별도 적용 범위다.
- 공식 https://www.revenuecat.com/docs/integrations/webhooks 에서 HMAC header t/v1·raw body·재서명·설정후1회secret표시를 재확인했다. 신규 webhook 화면에는 Authorization만 보였으나 이를 HMAC 미지원으로 단정하지 않았다. integration 미생성·signing 미설정·실delivery 미검증이며 Authorization-only로 낮추지 않는다.
- 사용자에게 테스트 public host 네API allowlist/DNS/ALB8083, provider Secret/실행역할 exact읽기, 별도 budgetDB의 revenuecat_read_budgets 컬렉션 최소 읽기쓰기·schema 준비를 묶어 승인 요청했다. 현재 AWS/DNS/IAM/DB·RC 영속 설정·제품코드 무변경. Gradle 미실행(읽기 점검/문서), diff 검사 수행. 기존dirty보존·예상밖 변경/Jira/commit/push 없음. 후속은 승인·사용자 키 발급/안전저장, 상품/예산schema·HMAC실지원 연결, 테스트배포와 인증/결제처리 검증이다.

## 2026-10-17 — 승인된 앱용 접속 경로·RevenueCat Secret 준비

- 브랜치 codex/billing-test-deployment, TMI-212·PLAN010/013 후속. 사용자 public4route/DNS/ALB8083 및 provider Secret/최소권한/budget collection 준비 승인 적용. 키는 사용자가 CloudShell getpass로 직접 입력했고 성공 메시지를 확인했다. 비밀값 조회·출력·문서 저장 없음. 테스트 execution role에 새 Secret 하나의 GetSecretValue만 독립 policy로 추가 후 exact 재조회했다. ECS에는 아직 주입하지 않았다.
- Atlas tosunsaeng-billing-test의 기존 Billing DB readWrite는 보존, to-teacher-revenuecat-budget.revenuecat_read_budgets에만 readWrite 추가·UI 저장 확인. 비밀번호/다른 사용자/운영 권한 불변. collection/schema 설치 및 실제 DB 권한 검증은 아직이다.
- 기존 ALB443에 billing-test host의 GET products/entitlements, POST purchase-account/sync를 rules100~103으로 추가,104 host404. public8083 전용 TG 및 ALB SG에서만 들어오는8083 rule 추가. 기존 workload/타서비스 rule 불변. task5에서 portMapping8083만 추가한 task6 배포, 서비스 LB8083 추가. 이미지/flag/기존secret·역할·네트워크 설정 보존. 최종 task6 단일 COMPLETED/running1/pending0, 새8083/8443 healthy(구8443 draining).
- 사용자 가비아 재로그인 뒤 CNAME billing-test→기존 staging ALB, TTL600 저장 완료 알림 확인. 기존25개 레코드 수정 없음. CloudShell resolver는 마지막 조회 시 전파 대기. TLS 검증을 끄지 않은 curl connect-to 검증: entitlement/account 무인증401, OFF products/sync404, actuator/internal삭제/DELETE account404. 이는 DNS 경유 앱결제 성공이 아니다.
- 문서 CURRENT_STATE/WORKLOG 갱신. 제품코드 무변경·Gradle 미실행(실인프라 연결 작업); 기존dirty/원본 작업트리 보존, 예상밖 코드 변경·Jira·commit/push 없음. 후속: DNS 정상조회, catalog/Offering package 대조, budget strict schema 설치, 필요한 암호화 설정과 provider Secret 주입, worker/sync 활성화·sandbox 검증. webhook은 네 public API 노출 승인과 분리하며 HMAC 검증을 낮추지 않는다.

## 2026-10-17 — 테스트 RevenueCat worker·카탈로그 준비

- codex/billing-test-deployment/TMI-212·PLAN010/013. 사용자 후속 진행 요청. RevenueCat premium Offering의 premium1d/3d/7d/14d/28d package와 Apple/Google 10개 rcProductId 연결을 UI로 대조했다. default Test Store package는 제외하며 RC 상품 설정은 수정하지 않았다.
- /private/tmp/BillingProviderPrepare.java를 Java21로 컴파일한 일회성 ECS 도구로 DB/host를 exact 확인. 실제 catalog0 확인 후 별도 budgetDB에 기존 RevenueCatBudgetSchema.installUnderMaintenance/verify를 실행하여 성공(exit0). provider worker OFF 상태에서 수행했고 다른 프로젝트 호출자 존재는 RC 신규키/기존환경 조사에 근거한 전제다. budget을 쓰는 다른 환경도 같은 DB를 사용해야 한다.
- 두 번째 준비 task는 기존 catalog0 재확인 후 검증된 SANDBOX10행만 단일 transaction으로 등록했다. 기존 PaymentOffer 기간/기준가격·catalogVersion1·KRW, packageId와 storeProductId는 premium{days}d. PROVIDER_CATALOG_INSTALLED_10 및 budget OK·exit0 확인. 구매·고객·권리 샘플을 만들지 않았다.
- 승인된 테스트 provider Secret의 기존 apiKey를 보존하고 거래 lookup HMAC32byte·reference AES용32byte를 최초 생성·저장, 값 미출력. lookup/reference version은 test-v1. 기밀 원문은 코드·문서에 기록하지 않았다.
- task6 기반 task7에 catalog/sync/provider worker=true, project/app mapping·별도budgetDB·키버전·세 Secret 참조만 추가하여 배포 시작. 기존 이미지·타flag·port/ALB/역할 불변. webhook/reconciliation/자동purge는 활성화하지 않았다. DNS 정상해석 확인. 최종 배포/HTTP 결과는 아래 후속 기록 참조.
- 제품코드 무변경; 임시도구 javac 성공, 실제DB exact schema와 등록 로그 확인. Gradle 미실행(실환경 설정 작업). 기존dirty·원본 작업트리 보존, 예상밖 코드 수정/Jira/commit/push 없음. 실제 RevenueCat 구매 응답·정상로그인 sync E2E·웹훅 서명/자동수렴은 미검증.

### task7 기동 실패·정상 revision 복구

- task7 startup 원인은 Purchase reference schema mismatch; 기존 정상 task6를 유지한 상태에서 task7 종료를 확인하고 service를 task6로 복구 요청했다. 원문 provider/Secret을 출력하지 않고 제한된 startup 진단만 읽었다.
- PurchaseReferenceSchema는 reference collection 설치에 ALL_BLOCKED/격리를 요구하고 PaymentAccountLookupSchema는 기존 cursor validator 정합화에 collMod를 요구한다. 자동기동에서 검증을 우회하지 않는다. 기존 collMod 임시권한은 회수돼 있으므로 cursor 컬렉션에만 임시권한·테스트 중지·설치/검증·회수·재배포를 사용자에게 요청했다. 실제 reference/cursor 설치와 DB barrier 변경은 아직 수행하지 않았다. 모든 구매/시험 자료 삭제 없음.

## 2026-10-17 — 테스트 cursor/reference 명시 유지보수

- codex/billing-test-deployment/TMI-212·PLAN010/013. 사용자 테스트 서비스 중지·cursor 전용 임시 collMod·스키마 설치·회수·재배포 승인. Atlas 로그인 만료 중 쓰기 없이 대기하고 재로그인 뒤 진행했다.
- task6 단일 COMPLETED/running1 확인 후 desired0, running/pending0 및 잔존 서비스 task 검사. 기존 DB-wide 역할은 부여하지 않고 billing-test-cursor-collmod를 정확 to-teacher-billing-test.payment_reconcile_cursors에만 생성·임시 부여했다. 기존 Billing DB 및 budget collection readWrite/비밀번호/타사용자 불변.
- 임시 BillingCursorPrepare(Java21 컴파일 성공)를 기존 배포 이미지의 명시 schema 코드로 실행. DB/host·단일 SANDBOX/billing control·OPEN·epoch/version 검증, CAS로 ALL_BLOCKED, reference collection 설치 및 cursor validator 설치/기존r1의 경우r2 upgrade 경로만 허용했다. 두 exact schema 검증 및 기존 모든 컬렉션 건수 동일 확인 후 epoch 유지·version 증가로 OPEN 복귀. 일회성 task exit0, FENCE_BLOCKED/SCHEMAS_OK_COUNTS_PRESERVED/FENCE_OPEN 로그 확인. 삭제/drop/recreate/구매 생성 없음.
- Atlas 사용자에서 임시 역할 제거 저장 후 기존 두 readWrite만 남음을 UI 확인. 재배포 최종 결과는 후속 기록한다. 운영/LC/Identity·wire/제품코드/Secret값 변경 없음. Gradle 미실행(인프라 유지보수); 명시 도구 컴파일/실DB 검증 수행. 기존dirty·원본 작업트리 보존, Jira/commit/push 없음. 정상로그인·Store 구매·RC 응답·webhook E2E는 별도 미검증.

### task7 최종 정상 배포 확인

- Atlas 임시 역할 제거 후 configuring/deploying 표시 종료를 확인하고 task7 desired1로 재배포했다. 단일 COMPLETED/running1/pending0, task HEALTHY 및 public8083/workload8443 모두 healthy. 앱 target은 초기 실패 후 기존 healthy threshold5의 연속 점검을 거쳐 정상화됐으며 헬스체크 조건을 낮추지 않았다.
- 실제 billing-test.to-teacher.com DNS/TLS 검증 유지: GET products/entitlements, POST purchase-account/sync 무인증401. public host의 POST internal삭제·GET actuator/health·DELETE purchase-account404. 사용자 토큰을 임의 생성하거나 실제 구매를 만들지 않았다. provider/cursor 기동 검증 통과는 RC 실응답·결제/환불 E2E 성공을 뜻하지 않는다.
- 종료된 cursor 준비1개/provider 준비2개 task definition을 상태·대상 검증 후 INACTIVE로 정리, 데이터/로그 삭제 없음. 임시 custom role 정의는 미할당 상태로 남고 사용자 collMod 권한은 회수됐다. 스크린샷 /private/tmp/billing-cursor-permission-revoked.png 및 /private/tmp/billing-task7-completed.png. CURRENT_STATE/WORKLOG만 이번에 갱신하며 기존dirty 제품코드 불변. git diff --check 통과. 후속은 frontend sandbox E2E 및 별도 webhook 연결 승인/구성이다.

## 2026-10-17 — Sandbox 웹훅 연결 사전 확인

- codex/billing-test-deployment/TMI-212·PLAN010/013. 사용자 후속 요청에 따라 프론트 로그인 E2E와 별개인 웹훅 준비 조사. RC Active0 및 웹훅 목록 없음, 생성폼 name/URL/Authorization/environment/app/event 항목 확인. billing-test-sandbox, 기존 test host의 /api/v1/payments/revenuecat/events, Sandbox only를 미저장 초안으로 작성했다. Authorization 비어 있음·Add webhook 미실행. screenshot /private/tmp/billing-webhook-sandbox-draft.png.
- Billing RevenueCatWebhookFilter 경로·Authorization 및 X-RevenueCat-Webhook-Signature, 300초 HMAC 검증과 환경변수 설정 확인. RC 생성폼에 signing secret 항목은 없으므로 생성 후 제공 여부 확인 필요하며 지원을 추정해 수신ON하지 않는다. 인증 약화/운영 이벤트 전송 금지. 기존 네 public API 외 단일 POST 공개·test Secret 항목·수신ON 재배포 범위를 사용자에게 확인한다.
- 변경 파일 CURRENT_STATE/WORKLOG뿐, AWS/DB/키/RC 저장·제품코드/타저장소/Jira/commit/push 변경 없음. Gradle 미실행(코드 무변경), 기존dirty 보존. 후속은 한정 범위 승인 및 비밀값 비노출 전달, 실제 서명delivery 검사와 frontend sandbox 구매검증이다.

## 2026-10-17 — Sandbox 웹훅 등록·비밀값 직접 입력 인계

- codex/billing-test-deployment/TMI-212·PLAN010/013. 사용자 테스트 webhook 단일 POST/Secret/수신ON 재배포 승인 후 RC billing-test-sandbox를 기존 test host /api/v1/payments/revenuecat/events 및 Sandbox only로 등록(whintgr337a41788a). 아직 Authorization 없음/HMAC OFF이며 ALB 경로 미추가·Billing ingress OFF로 실제 접수되지 않는다. 운영 이벤트 제외, event 필터 All events/app All apps이며 수신 decoder mapping은 기존 Apple/Play로 유지할 예정이다.
- 공식 https://www.revenuecat.com/docs/integrations/webhooks 의 HMAC 설정/일회성 secret 표시/재전송마다 timestamp 재서명 설명 및 생성 후 HMAC 토글 확인. 지원 추정이 아닌 문서·화면 확인이며 실제delivery 검증은 아님.
- 사용자 직접 인증값 설정/HMAC생성 후 기존 test revenuecat Secret에 webhookAuthorization/webhookSigningSecret만 병합하도록 CloudShell getpass2개 명령 시작. 기존 필드/버전변경 여부 검사, 값 출력 없음. 저장 성공 전에는 ECS/ALB 변경하지 않는다. screenshot /private/tmp/billing-webhook-created.png. 비밀값·DB·제품코드·타서버·Jira/commit/push 무변경, 기존dirty 보존. Gradle 미실행(외부설정 작업); 문서검사 수행. 다음은 사용자 입력 성공 확인→한정ALB/secret주입/수신ON→서명 test event 검증.

## 2026-10-17 — 웹훅 Secret 저장 확인·task8 활성화

- codex/billing-test-deployment/TMI-212·PLAN010/013. 사용자 완료 회신 뒤 CloudShell WEBHOOK_SECRET_SAVED 확인. RC HMAC true, Save 활성 상태여서 설정 저장 후 disabled 확인. 인증값 원문 조회/출력 없음.
- task7 기반 동일 이미지/기존설정 보존, BILLING_PAYMENT_PROVIDER_INGRESS_ENABLED=true와 host, 기존 Secret의 webhookAuthorization/webhookSigningSecret 참조만 추가한 task8 등록·배포 요청. IAM 권한 확대 없음. 기존 ALB443 priority99가 비었음을 확인하고 test host/정확 POST/정확 webhook path만 public8083 target에 추가했다. 다른 규칙/운영/LC/Identity/DB 무변경.
- RC Send Test Event 화면 준비, 아직 발송 전이며 최종 rollout/실서명 검증은 후속 기록. 제품코드/Gradle 변경·실행 없음(설정 작업), 기존dirty 보존. 이번 문서2개만 갱신, Jira/commit/push 없음.

### task8 완료·실제 RC 서명 delivery 검증

- task8 단일 COMPLETED/running1/pending0 및 새8083/8443 target healthy 확인, 구 target draining은 정상교체 상태. 기존 네 앱API는 무인증401 유지, webhookPOST401/GET404, actuator404 확인. 인증 fallback·헬스체크 기준 완화 없음.
- RC 최초 All apps TEST는 별도 Test Store app 식별자로 전달되어422. decoder는 앱 whitelist를 TEST ignored 이전에 검사하므로 예상된 범위거절이다. whitelist를 늘리지 않고 RC app필터를 실제 iOS로 잠시 지정해 TEST 전송200, Play로 지정해 TEST 전송200 확인. 이후 All apps/Sandbox only로 복구·저장, All events 유지. 두 TEST는 ignored durable 접수 경로이며 구매/권리 생성 근거가 아니다. 실제 거래/환불 E2E와 혼동하지 않는다.
- RC HMAC ON·저장완료와 실제delivery200으로 Authorization/HMAC 검증 일치 확인. 비밀값/사용자 거래 원문 문서·로그 기록 없음. 증거 /private/tmp/billing-webhook-ios-200.png, /private/tmp/billing-webhook-google-200.png. 변경문서 CURRENT_STATE/WORKLOG, 제품코드·타서버·운영·Jira·commit/push 무변경. 기존dirty 보존. git diff --check 수행. 다음은 frontend 정상로그인 Store sandbox 구매 및 sync/웹훅 권리반영·LC시험 공동E2E.

## 2026-10-17 — 추가 worker 활성화·Identity 준비 점검

- codex/billing-test-deployment/TMI-212·PLAN010/013. 사용자 자동정리·주기적RC조회·Identity연동 점검 및 준비된 기능 활성화 승인. CleanupConfiguration은 purge/alerts ON을 실제adapter/sink 부재로 거절하며 reconciliation도 unavailable evidence인 것을 확인. 보호를 우회하거나 테스트DB라는 이유로 미구매 증거를 발명하지 않았다.
- PaymentRetentionWorker는 완료APPLIED/REJECTED·countedfalse·updatedAt120일 경과 sync만 삭제. PaymentReferenceRetentionWorker는 5년 경과와 구매/권리/원장/미해결·lease 보호를 재확인해 reference만 삭제, 재무원장/계정은 삭제하지 않는다. 기존 배포에서 두 worker의 exact schema 생성자 검증이 적용되고 cursor-r2/reference는 앞선 유지보수 설치 완료다. periodic RC는 기존공유budget 분당240/동시4를 유지하며 pending5분·유효권6시간·기타24시간 및 최근90일 대상 규칙을 유지한다.
- 실제 Billing test8/SANDBOX·test Mongo Secret과 기대DB/host·공유budgetDB를 검사하여 세 provider flag(reconciliation, terminal-sync-delete, reference-retention)만 true인 후속 revision 배포를 요청했다. 이미지/기존설정/키/역할·보존기간 불변. 새 자료 생성·수동삭제 없음. 결과 후속기록.
- Identity test44에는 Billing 전용 private JWK 설정이 없고 phone/Billing owner 발신OFF이며 Billing에도 Identity public JWK 미설정. 일반 workload JWT ON은 Billing 전용 준비 증거가 아니다. 단순flag ON으로 해결할 수 없어 Identity/phone·탈퇴 수신은 그대로OFF. 배포된 Identity 이미지의 최신계약 포함 여부·전용키/환경scope·전송경로·DB 준비 검증이 후속이다. 서비스 발견시 기존/운영 Identity 설정 metadata도 읽혔으나 변경은 없다. 이후 test 서비스만 조회했다.
- 이번 제품코드/타저장소/Identity·LC배포/Jira/commit/push 무변경, 기존dirty 보존. 문서 CURRENT_STATE/WORKLOG 갱신. Gradle 미실행(코드무변경 설정작업), 정적보호조건 및 실제 배포설정 검증. 계정purge 및 전체Identity연동 완료로 보고하지 않는다.

### task9 최종 검증

- 새 revision9 세 flag true 배포 요청 성공 및 최종 ECS 단일 COMPLETED/running1/pending0 확인. 새public/workload target healthy, 구target draining. 기존 앱4API/웹훅POST 무인증401, webhookGET/actuator404 회귀 확인. /private/tmp/billing-task9-completed.png 증거 저장.
- 보존기간/DB대상/환경·원장보호 불변. 실제 만료자료 삭제건수·주기적조회 성공은 별도 업무 검증이며 이번 기동/API 점검만으로 완료를 주장하지 않는다. 계정자동삭제 adapter 구현 및 Identity test 배포계약/키·경로·capture/발신·수신 준비가 남는다. 문서 diff --check 통과, 예상밖 이번 코드변경 없음.

## 2026-10-17 — TMI-205 실제 거래 확인 연결 (자동삭제 활성화 미완료)

- 브랜치 `codex/billing-test-deployment`. 사용자 요청 범위는 탈퇴 구매 계정 거래 확인 구현, Identity/phone은 제외. 구현 전 TMI-205 원문을 읽었다. 이슈는 RC404/빈 목록만으로 미구매 확정 금지 및 provider coverage/token drain 검증을 활성화 gate로 명시한다. Jira 수정/댓글/전환 없음.
- 추가: CleanupLocalReader, CleanupProviderReader와 두 테스트 클래스, `docs/runbooks/TMI-205-live-evidence-readers.md`. 변경: CleanupConfiguration, PaymentProviderWorkerConfiguration/Runtime, RevenueCatReadClient/BudgetedTransport/HttpTransport, 기존 CleanupIntegrationTest의 runtime 증거 검사, CURRENT_STATE/이 로그.
- 실제 session-bound 구매/권리/timeline·pending/inbox/case/sync·조회lease 확인을 연결. 환불/취소도 금융자료 보존. 소유 불명 같은환경 case/inbox는 보류하며 다른환경·ignored는 제외. DB timeout을 NONE으로 바꾸지 않는다. 실제 provider 설정/공유budget/transport를 재사용해 전체 ref와 page(최대100)를 확인하고 빈결과는 ABSENT_OBSERVED까지만 반환한다. 404/부분조회·순환cursor·미해석거래·401/403·기한초과를 보류한다. 잔여 5초 HTTP 예산을 전달하며 budget DB 처리는 기존 timeout을 유지, 늦은 결과 채택 금지.
- coverage 발급은 여전히 없음. purge ON 거절/기본 OFF와 원장·보존기간·CAS/lease/epoch/15일·매시간/24시간/7일 기준·public/internal wire를 유지했다. local/provider 연결 완료와 실제 자동삭제 완료를 구분한다. RC 미관측 거래 부재/앱·스토어 coverage/구요청 drain/늦은거래 실검증, 정책버전 단일화 schema 이관, Slack sink·운영 보존 기준이 후속이다. 이번 변경으로 보호조건을 임의 완화하거나 별도 승인 서비스를 만들지 않았다.
- 검증: 첫 targeted 실행은 변경된 bean 메서드에 대한 기존 테스트 호출 수정이 필요해 compile 실패, 수정 후 cleanup targeted 통과. 잔여 HTTP timeout 전달 보완 후 Java21 `./gradlew clean test` **1,788 tests / failures0 / errors0 / skipped0**, 3분54초. 실제 replica-set Mongo Testcontainers 포함. 실제 Atlas/RevenueCat/Store 호출 없음. `git diff --check` 통과.
- 기존 dirty 배포·refund 설정/문서/scripts는 보존. 예상 밖 이번 변경 없음. 실제 DB·설정·배포·Identity/LC·commit/push 무변경. 다음은 현재 삭제 계약의 부재/요청종료 근거 확인이며, 이를 RC 조회만으로 대체하려면 별도 정책 변경 수락이 필요하다. 자동삭제를 켠 것으로 보고하지 않는다.

## 2026-10-17 — TMI-205 관측 기반 삭제 승인 및 조건부 purge 연결

- `codex/billing-test-deployment`. Jira TMI-205를 다시 읽고 기존 absolute coverage gate와 code의 요청 수명을 대조했다. 사용자가 ‘탈퇴15일+로컬 구매/미처리없음+전체ref 정상RC조회+토큰/진행요청만료’ 및 늦은 거래 수동 검토/구매자 연결 확인 어려움 위험을 명시 수락했다. 기존 RC 밖 미관측 거래 부재 증명 요구를 이 범위에서 대체했다. Jira 자체 수정은 요청하지 않아 하지 않았다.
- 추가 `PurchaseTokenLifetime`: 구매 계정 및 sync HTTP, VerifiedMemberEvidence에서 token exp-iat 최대30분/skew최대60초 검사. 초과는401, 상품/권리 read token 규격은 변경하지 않는다. 기존 DB 전후 authorizationDeadline·2초Tx는 유지. Identity 실제 발급 TTL/탈퇴 발급 차단은 상대 확인이 필요하며 코드/설정 수정 없음.
- 추가 `CleanupDeletionPolicy`: 승인 관측 기준으로 proof 발급, 15일/로컬pending0/계정활동32분 경과·complete provider 조회·최대1시간 proof 및 동일버전 최종검사를 사용한다. r2 storage의 기존 세 버전 필드에는 단일 코드 version observed-absence-r1을 동일 기록한다. 이전 proof를 변환하지 않고 재검증하며 실제 필드 단일화/schema이관은 하지 않는다.
- CleanupConfiguration에 실제 LocalReader/ProviderReader/policy와 purge-enabled 조건부 Purger 연결. reconciliation만 ON이면 READY 기록 후 삭제하지 않고, purge는 reconciliation/fence/실runtime/schema가 필요하다. 계정/ref가 처음부터 없는 lifecycle은 빈 ref 집합을 확인해 외부조회 없이 local조건으로 판단한다. 원장/무료 자료와 보존기간은 불변, 늦은 거래는 기존 durable REVIEW_REQUIRED/no recreation 유지. 실제 서버 flag/DB/배포는 미변경.
- 변경 파일: PaymentController/PaymentSyncController/VerifiedMemberEvidence, CleanupConfiguration/ProviderReader/Processor/Purger/Evidence, 신규 policy/token helper와 unit tests, 기존 core/integration/HTTP tests. 문서: 신규 `TMI-205-OBSERVED-ABSENCE-R1.md`, ADR004/공동 lifecycle 계약/205계획·technical spec/reader runbook/CURRENT_STATE. 기존 dirty와 다른배포/refund 변경은 보존, 예상 밖 이번 변경 없음.
- 검증: 토큰 경계 보완 단계 `./gradlew clean test` 성공(3분56초). 승인 정책/최종 runtime 포함 전체 **1,798개 중1개 실패**: 기존 PurchaseCoreIntegrationTest.parallelDistinctTransactionsAndDuplicateDeliveryConverge, PurchaseCore.apply→PaymentStore.runTransaction:68의 일시불가 예외. 해당 경로/시간한도는 수정하지 않았다. 이어서 구매 클래스 전체+cleanup+token+두HTTP+MEMBER 테스트 **171개/실패0/오류0/skip0** 재실행 통과(43초). 신규 expiry후 재생성금지/실local runtime purge/이전proof거절 포함. 실패는 재현되지 않았지만 원인을 확정하거나 전체1798개가 한 실행에서 통과했다고 주장하지 않는다. 실제 provider/Atlas/Store 호출 없음. diff --check 통과.
- 후속: 배포 전 전체 동시성 회귀 재확인, Identity 실제 30분TTL/탈퇴 발급·refresh차단/userId재사용금지 확인, worker구버전격리/schema/운영알림·중단 절차 확인 후 별도 테스트서버 활성화. Slack sink와 운영 보존 문제를 이번 구현으로 완료했다고 하지 않는다. commit/push/Jira/Identity·LC/실제DB·서버변경 없음.

## 2026-10-17 — TMI-205 전체 재검증·Identity 배포 전제 확인

- 사용자 후속 확인 요청. Java21 `./gradlew clean test` 전체 1,798개/실패0/오류0/skip0, 3분54초 성공. 이전 구매 동시성 실패는 이번 전체 실행에서 재현되지 않았다. 원인 확정이나 수정 완료로 바꾸지 않고 이전 기록을 보존한다.
- Identity 읽기 전용 정적 확인: PurchaseControlService ACTIVE/type 검증·version control touch와 탈퇴 Transaction의 동일 control transition, refresh 폐기·재발급 거절, User 신규 UUID 생성 확인. 구매 기능 ON 선행 조건은 PurchaseConfiguration/Properties에 있다. Identity 테스트 실행·수정은 하지 않았다.
- CloudShell ECS 읽기 전용 조회: Identity test task45/running1, JWT_ACCESS_TOKEN_TTL=PT30M, AUTH_SESSION_FENCE_ENABLED=true. 구매 scope/fence/control writes 및 withdrawal publisher는 environment 미설정, 해당 Secret 주입 없음. 로컬 기본false이며 배포 이미지/다른 설정원까지 동일하다는 보장은 하지 않는다. 발급·탈퇴 경합 실검증과 기능 활성화 확인은 담당 후속이다.
- 추가 문서 `docs/contracts/TMI-205-IDENTITY-ACTIVATION-CHECK-2026-10-17.md`, CURRENT_STATE 갱신. 자동삭제 OFF/미배포 유지. 이번 제품코드·Identity/LC·실제DB·AWS설정·Secret·Jira·commit/push 변경 없음, 기존 dirty 보존. diff --check 검사. 다음은 Identity 실제 구매/탈퇴 준비 확인과 Billing 배포 준비다.

## 2026-10-17 — 사용자 승인 커밋·푸시·develop 병합 준비

- 사용자 명시 요청으로 이번 Git 작업에 한해 commit/push/merge 수행. 브랜치 `codex/billing-test-deployment`, 원격 develop과 HEAD 동일(60d5f6c) 및 기존 PR 없음 확인. 이전 테스트 배포 준비(Dockerfile/ops/refund test activation/문서)와 TMI-205 관측 기반 삭제 변경을 함께 반영한다. 원본 Billing 작업트리·Identity/LC는 변경하지 않는다.
- 검증은 직전 Java21 clean test 1,798개 전부 통과(이후 제품코드 변경 없음), diff --check, ops 셸 문법 검사. 비밀값 패턴 검사는 기존 PaymentSlackSinkTest의 fixture만 탐지했고 새 파일에서 발견되지 않았다. 이전 동시성 실패 기록은 보존한다.
- 새 제품정책·wire·기본OFF 변경 없음. Git 병합은 서버 재배포나 자동삭제/Identity 연동 활성화가 아니다. PR 검사와 원격 병합 결과는 실행 후 사용자에게 보고한다. Jira·실제DB·AWS 설정 변경 없음.

## 2026-10-17 — TMI-205 테스트 구매·탈퇴 설정 활성화

- 브랜치 codex/billing-test-deployment. 사용자 명시 승인 범위: 테스트 ALB 탈퇴 POST 1개·Identity 구매 scope/fence/탈퇴 발신·Billing 수신. 최종 답변에 따라 실제 사용자 탈퇴 E2E는 하지 않는다. 운영/LC 변경 없음.
- PR29 develop merge a432079 제품 트리 bootJar/amd64 이미지 빌드·ECR push, test10의 실제 설정을 보존한 test11 배포. 기존 Identity key/phone receiver 설정 보존, withdrawal ON. ALB priority87에 승인 host/source/method/path만 추가하고 기존 규칙 불변 확인.
- Billing ECS COMPLETED·실행1개, 대상 healthy. 공개4API 무인증401. 기존 Identity 키를 사용하는 빈 JSON 인증 진단 exit0: unsigned401/signed400/wrong audience403/expired401. 최초 진단의 mongosh await 문법 오류는 async wrapper로 수정했고 제품 오류로 집계하지 않는다. 사용자 데이터·키/토큰 출력 없음.
- 읽기 전용 DB 점검: cleanup jobs0건/validator없음, recovery control SANDBOX/billing/schemaVersion1/OPEN. 필요한 r2/control4 미충족으로 cleanup reconciliation/purge는 OFF 유지. 버전 숫자만 바꾸거나 준비 증거를 발명하지 않는다.
- Identity test46 이미지·capture·phone·키 보존하여 test47에 승인 환경변수5개만 변경, 배포 요청. 안정화는 후속 기록. 새 scope는 새 토큰 발급부터 적용.
- 완료된 임시 preflight/auth 진단 task 정의3개는 INACTIVE로 전환, 로그는 보존. 제품코드 변경 없음; 전체 테스트는 직전1798통과 이후 변경 없어 재실행하지 않았고 이번에는 bootJar 및 실제 비파괴 연결 점검 수행.
- 변경 문서: 이 기록·CURRENT_STATE·TMI-205-TEST-ACTIVATION-2026-10-17.md. 예상 밖 제품 diff 없음. 다음: Identity 안정화, 정식 cleanup schema 유지보수, 프론트 전용 계정 E2E. 실제 탈퇴204/자동삭제/복구 전체 검증 완료로 기록하지 않는다.
- 최종: Billing test11·Identity test47 모두 ECS COMPLETED/실행1/pending0, 신규 ALB 대상 healthy. Identity 구 대상은 draining. 구매 scope/fence·탈퇴 publisher 및 Billing withdrawal ON 대조, cleanup reconciliation/purge는 미설정/default false. Identity 시작 성공/확인한100로그 ERROR0. diff --check 통과. 기존 사용자 계정/탈퇴/결제 자료를 진단용으로 변경하지 않았다. 이번 문서 commit/push/Jira 변경 없음.

## 2026-10-17 — TMI-205 cleanup 활성화 후속 사전 확인

- 사용자 자동삭제 준비·활성화 요청. 브랜치 codex/billing-test-deployment, 기존 문서 변경 보존. CleanupSchema/Configuration·RecoveryOperator·기존 bootstrap을 읽어 exact r2/control4 및 writer isolation 요구 재확인.
- Atlas 테스트 프로젝트의 Billing 계정은 test DB readWrite와 RC budget collection readWrite만 표시된다. cleanup jobs collMod 임시 권한·테스트 Billing 중지·검증 설치·권한 회수의 적용 직전 승인을 요청했다. 아직 외부 권한/DB/서비스 설정은 변경하지 않았다.
- 기존 fresh-test bootstrap의 empty 제한을 우회하거나 RecoveryOperator OPEN의 baseline/feed 조건을 발명하지 않는다. control 이관은 현재 자료 확인 후 명시 유지보수로 수행해야 한다. 실제 삭제 활성화 직전 조건·대상을 확인하며 실제 사용자 탈퇴 E2E는 수행하지 않는다.
- 변경 파일: WORKLOG/CURRENT_STATE만 추가 기록. 제품코드 변경 없어 테스트 미실행; 소스·Atlas 권한 읽기 확인. 다음: 승인 후 유지보수/이관 검증 및 cleanup 활성화. 운영·Identity·LC 변경 없음.

## 2026-10-17 — TMI-205 cleanup 테스트 유지보수·ON 배포

- 사용자 적용 직전 승인: 테스트 cleanup jobs 한정 collMod 임시 부여/중지/설치/회수 및 조건부 물리삭제 ON. 브랜치 codex/billing-test-deployment, 기존 문서 변경 보존.
- 추가 파일: scripts/ops/billing-test-cleanup-maintenance.js와 async fake DB 테스트, docs/runbooks/TMI-205-cleanup-test-maintenance-2026-10-17.md. 제품 Java/API·보존 조건·provider 계약 변경 없음.
- 테스트: Node8개 및 Java21 clean test1798개/실패0/오류0/skip0(3분59초). 최초 도구 DB 비동기 처리 누락으로 실DB dry-run 두 번 실패했으나 쓰기는 없었고, explicit await 및 async fake 회귀 후 dry-run exit0. 서버 중지 전 가드도 설치를 거절했으며 정상 종료 확인 후에만 실행했다.
- Atlas 전용 cleanup collMod 역할만 일시 연결, 원래 readWrite 권한 보존. Billing test11 desired0/ALB300초 drain/모든 writer STOPPED 확인. 최소 v1 control·cleanup0·정확 validator/index 검사 후 ALL_BLOCKED CAS → cleanup r2 설치/검증 → control4·기존 OPEN 복원. epoch·기존 inbox/phone/product 자료 보존. 설치 exit0.
- 임시 역할 연결 제거 저장 및 별도 새 DB connectionStatus에서 collMod=false 검증. 설치 후 control Long4/OPEN·cleanup0·strict/error·인덱스4개 확인. 역할 정의만 비연결로 남으며 로그/진단 기록은 보존.
- test11 동일 이미지/키/role/container 유지하고 reconciliation/purge 두 env만 true인 test12 등록/서비스 기동. 기동 로그 Started/확인100개 ERROR0, task definition 두 flag true 확인. ALB 최종 안정화는 후속 기록.
- 실제 사용자 탈퇴/구매/삭제 사례는 수행하지 않았다. 운영·Identity·LC 설정 변경 없음. cleanup 전용 Slack sink 미구현으로 alerts OFF 유지. 다음은 최종 헬스 확인과 프론트 전용 계정 E2E이며 자동삭제 성공 사례를 주장하지 않는다. commit/push/Jira 변경 없음.
- 최종 test12 ECS COMPLETED/실행1/pending0, public/workload ALB 모두 healthy, 공개4API HTTPS 무인증401. 두 cleanup flag 및 기존 withdrawal=true 재대조. 임시 task 정의5개는 STOPPED 확인 후 INACTIVE, 로그 보존. diff --check 통과. 기존 문서 외 예상 밖 제품 변경 없음. 실제 삭제 사례/cleanup Slack 연결은 남는다.

## 2026-10-17 미귀속 결제 전체 사용자 조회 차단 보완

- 사용자 승인: 미귀속 환불 식별 지연 위험을 수락하고 작은 격리 수정·commit/push/merge/테스트 재배포 요청. 브랜치 codex/billing-test-deployment, 신규 Jira 변경 없음.
- PaidEntitlementReader의 환경 전체 미귀속 case/inbox 검사만 제거. 본인 계정 환불/검토·탈퇴·복구 barrier·무쓰기 조회 불변. 예약/확정 제품 코드는 변경하지 않고 Mongo 회귀 추가.
- 두 관련 통합 클래스 통과, 전체 clean test/bootJar 진행 중. 기존 유지보수 문서/스크립트 변경은 보존하며 이번 제품 수정과 구분한다.
- 실DB 진단: PLAY_STORE 구매2건 VERIFIED/ALLOWED/READY 및 sync APPLIED, 미귀속 OWNER_UNRESOLVED case2건의 전역 조회 영향 확인. 자료/계정/토큰 상세를 기록하지 않는다.
- 다음: 전체 회귀 완료 후 사용자 요청 Git 작업 및 테스트 이미지만 교체. 운영·Identity·LC 설정 변경 없음, 실제 앱 인증 E2E는 별도다.
- 전체 Java21 clean test/bootJar 1801개/실패0/오류0/skip0(4분3초). commit2eef569 push·PR30 develop merge3d27016 완료, 빌드 소스와 develop의 src/main/src/test/빌드 설정 동일 확인. 기존 유지보수 미커밋 파일 제외.
- 검증 JAR SHA256 4065e39450058d0257b0845022d55f195368a5b63bba26b86564fbbd48319fcb. 기존 test12 이미지 digest 기반으로 동일 런타임에 JAR만 교체한 isolation-2eef569 digest510db0b4209549ddd4c494112e1a4eb7fcdaa1a00f14566519b2f0a5f5e73f81 준비. CloudShell 빌드 문자열 초회 문법 오류는 실행 전 실패, 등록 초회 빈 tags 거절은 서비스 변경 전 실패. 수정 후 테스트 전용 배포 진행 중.
- test13 등록 및 서비스 이미지 교체 요청 성공. 기존 test12 환경변수·secret 참조 동일 assertion 통과. 새 태스크 8082/8083/8443 기동·Started 확인, 조회한 기동 로그26개 ERROR0/WARN1. 이 시점은 ALB 안정화/구 태스크 종료 전이며 실제 인증 사용자 GET 확인은 아직 아니다.
- 최종: ECS test13 COMPLETED/desired1/running1/pending0, 새 태스크 HEALTHY·두 ALB 대상 healthy. 앱 target 초기 unhealthy는 이후 healthy로 수렴했으며 old/new subnet·SG·ALB 동일 확인, 네트워크 변경 없음. 이전 target은 draining, 현재 서비스 task는 새 이미지1개. 공개4API HTTPS401 확인(전환 중); 완료 뒤에도 동일 검사 수행. DB write/실제 결제/사용자 JWT 발급은 하지 않았으므로 인증된 사용자 paid/시험 E2E는 프론트 재조회 대상. 작업기록의 기존 유지보수 변경은 보존하고 별도 미커밋 유지.

## 2026-10-17 구버전 무료 시험 공존 확인

- 브랜치 codex/billing-test-deployment. 사용자 요청에 따라 LC 코드 읽기만 수행, 결과를 LC-legacy-free-coexistence-review-2026-10-17.md에 기록했다.
- 동일 생성 API/전역 전환·LegacyExamWriterGuard·고정 manifest로 현재 구신 공존은 불가. 신규 명시적 경로 및 legacy 무료 출처/lifecycle 공존 보완 권장. 구버전 무료는 신앱 무료권 소비와 별개이며 단순 URL/버전 헤더로 우회를 완전히 막을 수 없음.
- 제품 코드/LC 파일/실DB/prod 설정·배포·Jira·Git 변경 없음, 정적 조사로 테스트 미실행. 기존 미커밋 변경 보존. 다음은 LC 공존 설계 인계 및 사용자 승인 후 구현이다.

## 2026-10-17 자동삭제 Slack 연결·복구 활성화 준비

- 브랜치 codex/billing-test-deployment. 사용자 구현 요청 범위에서 CleanupAlerts/Configuration/Worker, PaymentSlackSink와 cleanup/Slack 테스트를 변경했다. 관련 TMI-205/209·204의 기존 정책/복구 구현을 유지하며 Jira는 변경하지 않았다.
- 기존 전송기 재사용으로 요약/장애/복구를 연결했다. OFF는 DB 요약 조회/전송 없음, ON은 Slack bean 필수. false/예외 전송 실패 지표와 시간당 재시도, 오류 없는 실제 처리 tick의 복구 통지를 추가했다. 별도 알림 DB·채널·원문 전송 없음.
- recovery HTTP/JWT·snapshot/feed worker는 이미 구현됐다. Identity 코드는 읽기만 확인했다. 기존 control의 baseline 없는 OPEN 상태를 새 INITIAL/가짜 checkpoint로 우회하지 않는 활성화 안내를 추가하고 과거 runbook에 최신 링크를 붙였다.
- 테스트: Java21 clean test 1804개/실패0/오류0/skip0. 이후 기동 검증 순서를 앞당기고 설정 ON/OFF 구성 테스트를 추가해 관련 최종 재검증 중. git diff --check 통과.
- API/body·JWT·구매보호·삭제조건·보존기간 불변. 기존 LC 공존/테스트 유지보수 dirty 파일은 이번 범위가 아니며 보존했다. 예상 밖 제품 코드 변경 없음.
- 실제 Slack·양서버 복구 E2E, 환경 키/ALB/최초 baseline 적용은 남는다. 커밋/push/배포/DB·Secret·Identity/LC 수정 없음. 다음은 최종 테스트 결과 기록 후 환경 활성화 별도 진행이다.
- 최종 보완 후 cleanup/Slack 관련126개 실패0/오류0/skip0 통과(25초). 전체1804개 결과는 그 직전 실행이며 두 결과를 합산하지 않는다. 최종 diff 공백 검사 통과. 다음 작업은 테스트 환경 Slack 수신과 Identity recovery 연결/최초 baseline 검증이며 운영 활성화는 별도다.

## 2026-10-17 cleanup Slack 테스트 배포·Identity 준비 확인

- 사용자 후속 진행 승인. 브랜치 codex/billing-test-deployment, 기존 dirty 변경 보존. Java21 bootJar 통과, JAR SHA256 bd79bb733ae765003569bd7b6236ef701ec84f0f00f21b34ee4a2604b6bad85e. 직전 전체1804/관련126 테스트 결과 재사용, 이번 테스트 재실행 없음.
- ECS 읽기: Billing test13 실행1/pending0, Slack transport true·webhook Secret 참조 있음. Identity test47 실행1이나 recovery 설정/수신 JWK 주입 미확인, Billing recovery private JWK 주입 없음. 외부 설정원 부재까지 단정하지 않는다.
- 기존 test13 runtime digest 기반 JAR 교체 이미지 cleanup-alerts-bd79bb73 생성, digest 5f1723774ca6beb43c27af0a610d8e0fecf512aa70071400b08eacf2334954a9. 업로드 동명 파일 충돌은 기존 파일 보존 후 새 파일명으로 해결. task14 등록·서비스 변경 요청, image와 cleanup alerts true만 변경 assertion 통과. 기존 Secret/IAM/네트워크/DB·Identity/LC·prod 변경 없음.
- Identity-recovery-test-readiness-request-2026-10-17.md 작성. 키/전용 경로·실stream·baseline·E2E 준비가 필요해 recovery는 OFF 유지. 실제 탈퇴/구매·임의 checkpoint·barrier 조작 없음. Slack 수신/ECS 안정화는 후속 확인 중이며 아직 완료로 기록하지 않는다. 커밋/push/merge·Jira 변경 없음.
- 후속 확인: task14 RUNNING/HEALTHY·service running1/pending0, 두 ALB 새 target healthy 및 이전 target draining. 초기8083 unhealthy는 정상으로 수렴했다(30초 간격/정상5회). 21:24 기존 문의-테스트 채널에 자동삭제 요약 수신, 보류0/기한초과0. 증빙 /private/tmp/billing-cleanup-slack-received-20261017.png. 공개4API 무인증401 확인. 실제 사용자 구매/탈퇴·복구E2E는 미실행이며 rollout 최종 완료 여부 추가 확인 중.
- 최종 AWS services-stable waiter 성공(BILLING_TEST14_STABLE). 정상 새 태스크와 Slack 실제 수신 확인 완료. 다음은 Identity 준비 요청에 따른 복구 전용 키/경로·baseline 검증이다. 이번 범위 외 설정/운영 변경 없음.

## 2026-10-17 prod 세 서버 배포의 구앱 호환 검토

- codex/billing-test-deployment. 사용자 경로 분리 완료 안내에 맞춰 LC 최신 코드/공존 인계서와 Identity JwtAccessTokenIssuer를 읽기 확인했다. v1은 TransitionFreeExamService, v2는 paid saga로 분리됐다. 기존 미분리 조사 문서는 과거 시점 기록이다.
- v1 legacy-create 기본false, owner fence/runtime 및 기존 Session manifest를 요구한다. 진행 중 paid/미완료 operation은 v1 거절을 유지한다. Identity는 기존 audience를 보존하면서 Billing audience를 추가하지만 실제 prod의 구토큰 수용/키·설정은 이번에 확인하지 않았다.
- 코드 분리만으로 prod 무영향을 확정하지 않는다. prod 무료 생성 ON·기존 Session 준비·로그인/토큰갱신/생성/기존 제출/결과 smoke 확인 필요. 심사 중 강제 업데이트/구경로 차단 금지. API 변경·타 서버 수정·DB·배포·Jira/Git 변경 없음, 정적 확인이라 테스트 미실행. 기존 dirty 보존, 예상 밖 변경 없음.

## 2026-10-17 prod 실제 설정·기동 조건 후속 점검

- 브랜치 codex/billing-test-deployment. 목표는 세 서버 배포 시 구앱 무료 경로 호환 여부의 읽기 검증이다.
- LC PaidExamTestActivation은 정확히 test profile/환경만 허용한다. paid ON prod 기동 거절과 paid OFF 시 v1 필수 LegacyExamRuntime 미생성의 양쪽 위험을 확인했다. legacy-create flag만으로 해결되지 않는다.
- 서울 ECS 전체 cluster/service 및 태스크 설정·ALB를 읽기 조회했다. LC prod7/Identity prod12 실행1, Billing은 test14만 확인. LC prod 태스크 legacy-create 설정 부재·creation saga false. Identity audience는 LC 유지, issuer staging 호스트, 최소버전 양 플랫폼2.0.1. 실제 운영 host→prod target 연결 확인.
- 변경 파일: prod-coexistence-readiness-2026-10-17.md 신규, CURRENT_STATE/WORKLOG. API/인증/원장 계약은 변경하지 않았으며 기존 dirty 보존, 예상 밖 제품 코드 변경 없음.
- 정적 검토/AWS describe만 수행해 Gradle 미실행. 운영 DB manifest/control/index·구앱 실제 로그인/생성/제출/결과 E2E는 미확인. 비밀키/토큰/사용자 자료 조회와 서버·DB·배포·Jira·commit/push 없음.
- 다음은 LC 운영 readiness 보완과 운영 데이터 준비 검증, Billing prod 준비 후 구앱/신앱 smoke다. test 설정 복사나 안전 검사 제거로 우회하지 않는다.

## 2026-10-17 LC 운영 기동 준비 후속 구현

- 사용자 후속 「어 해줘」로 직전 제안한 LC 준비 코드 보완 착수. Billing 브랜치 codex/billing-test-deployment, LC feat/lc-review-coexistence. 기존 dirty 보존.
- LC의 환경별 기동 검증·application.yml·테스트와 작업 문서 보완. production 승인 기본 OFF, prod 단독 profile과 production wire 환경 매핑. test 승인 복사 불가, 기존 manifest/owner/index·인증 검증 유지. Billing은 CURRENT_STATE/WORKLOG만 갱신.
- clean test1396개 중1395통과/skip1. Docker API 기본값 불일치로 첫 Mongo 시작 실패 후 테스트 프로세스 API1.44 지정으로 rollout 검증 통과. 전체 Mongo 검증 진행. 제품 API·원장·보존기간·v1/v2 정책 불변.
- 운영 DB/설정/활성화/배포·Identity 수정·commit/push 없음. 다음은 운영 DB/인증 준비 검증과 Billing prod 준비 및 실제 구앱 호환 smoke. 코드 준비가 실제 운영 무영향 보증은 아니다.
- 최종 LC 전체 Mongo336개 통과/실패·skip0, diff 공백 검사 통과. clean test1395통과/기존skip1과 구분해 기록한다. 예상 밖 변경 없이 기존 사용자 dirty 보존.

## 2026-10-17 LC commit/push·Billing 운영 기반 준비

- 사용자 commit/push·운영 DB/키 확인·Billing prod 구성 요청. LC feat/lc-review-coexistence의 이번 8파일만9fb8f71 commit/push, 공유 기록/DS_Store/기존 scripts 제외. main/develop 머지·제품 배포 없음. Billing codex/billing-test-deployment의 제품 dirty 보존.
- AWS 메타데이터·운영 DB 연결 읽기 성공. LC manifest2collection/creation operations 없음·owner guards 존재, Billing DB는 현재 접속 계정에서 미발견. 기존 전용 Billing prod Secret/task/role도 미발견. 비밀값·개별 자료 출력 없음.
- 사용자 운영 전용 범위 승인 후 빈 Secret8개·ECS execution role(exact8 Secret+표준ECR/log)·로그30일·ALB SG-only inbound task SG·8083/8443 target group 생성. 모든 Secret 버전0, actual ECS task/service 및 DNS/listener는 아직 미생성. 기존 서비스/데이터/키 값 무변경.
- Atlas 권한 선택 안전 검토 거절 후 exact Project0/tosunsaeng-billing-prod/readWrite@to-teacher-billing-prod 승인 재확인. 해당 권한 UI만 준비, 사용자 비밀번호·제출은 대기. 우회/credential 자동생성 없음.
- 문서 billing-prod-foundation-2026-10-17.md/CURRENT_STATE/WORKLOG 갱신. 기존 LC1395+Mongo336 결과 재사용, 제품 코드 미변경으로 재실행 없음. 운영 준비 완료/활성화/E2E로 주장하지 않는다. 다음은 사용자 계정 생성 후 Secret 저장·키/TLS·schema·서비스·route 준비다.

## 2026-10-17 운영 DB 확인·키 생성 사용자 실행 준비

- 브랜치 codex/billing-test-deployment. 사용자 후속 요청으로 운영 기반 준비 지속. 사용자가 생성한 계정의 MongoDB Secret 저장 후 read-only 인증·collection metadata·connectionStatus 검사 성공: to-teacher-billing-prod, 컬렉션0, readWrite 역할1개만 확인. 실제 자료 write 없음.
- CloudShell에 환불/복구용 독립 RSA3072 RS256 JWK(sign/verify 구분), limiter HMAC32byte, ALB→task 전용 자체서명 TLS365일/PKCS12 생성·기존 빈Secret4개 저장 명령을 붙여넣었다. credential 생성은 사용자 Enter 실행 대기이며 완료로 기록하지 않는다. 기존 Secret 값이 있으면 중단하고 비밀값/URI는 출력하지 않는다.
- CURRENT_STATE/WORKLOG 기록만 갱신, 제품 코드/외부 계약 불변. 제품 테스트는 미실행(인프라 준비), 실제 DB 읽기 검사만 성공. 미커밋 기존 제품 변경 보존. 다음은 사용자 실행 후 키 검증·Java PKCS12 호환 검사·상대 공개키 준비·명시 schema·OFF 배포. 운영 기능 ON/상대 서버 변경 없음.

## 2026-10-17 운영 키 저장 후 독립 검증

- codex/billing-test-deployment. 사용자 생성 실행 성공4건 확인 후 Secret을 프로세스 내부에서만 읽어 검증했다. RSA3072 독립2키 서명/검증, JWK sign/verify·공개키 private필드 부재, HMAC32byte 통과. TLS 키/인증서/SAN/지문 일치 및 Java keytool PKCS12 읽기 성공. 임시 PKCS12는 권한0600 임시 디렉터리에서 검사 후 정리했다. 비밀값 출력 없음.
- CURRENT_STATE/운영기반 runbook/WORKLOG 갱신, 제품 코드·wire·서비스·DB 무변경. 테스트 전용 bootstrap의 prod 차단을 확인해 그대로 실행하지 않음. 스키마 전용 초기화/권한, 상대 수신키/RevenueCat, 실제 앱 기동과 ALB TLS 검증은 남는다. 제품 변경이 없어 Gradle 미실행; 이번 검증은 실제 Secret 암호학/Java 저장소 검사이며 공동 E2E가 아니다. 기존 dirty 보존.

## 2026-10-17 운영 전용 초기 설치 도구·로컬 검증

- codex/billing-test-deployment에서 BillingProdSchemaBootstrap.java 추가. 명시 prod DB/host/PRODUCTION/격리/DB명 승인 및 컬렉션0을 모두 요구. 기존 무료 initializer와 payment/paid/refund/sync/deletion/snapshot/cursor/reference/cleanup installer 재사용, PRODUCTION ALL_BLOCKED만 생성하며 open/삭제/재설치 없음.
- 최초 로컬 설치는 entitlement_grants 선행 부재로 거절됐다. 무료 index initializer를 앞에 연결한 뒤 새 로컬DB에서41collection 설치/전체 verify 성공, 같은 DB install 재실행 exit1로 거절. 기존 테스트 전용 도구와 runtime 계약 무변경. 최초 JAR 추출 디렉터리 execute 권한 부재로 javac 실패, 이번 임시 추출물에만 owner 권한 보완 후 컴파일 성공.
- 전체 clean test1806/실패0/오류0/skip0,4분5초 성공. 도구 로컬 DDL 검증은 standalone Mongo7.0.14이며 전체 회귀의 transaction 테스트와 구분. 로컬 컨테이너 billing-prod-bootstrap-check-20261017 중지, 자료 삭제 없음.
- 사용자 '승인할게'로 운영DB 내8collection 임시collMod·설치후회수 범위를 승인. 대상은 purchases/subscription_entitlements/payment_timelines/payment_ledger/payment_provider_inbox/payment_purchase_cases/payment_reconcile_cursors/payment_account_cleanup_jobs. 이번 턴에서는 실제 권한 부여/운영DB DDL/서비스배포/flag ON 미실행. 다음은 실행 패키지 준비→권한 추가→운영 빈DB·writer 부재 재확인→설치/verify→권한 회수→OFF 배포. 기존 dirty 보존, commit/push 없음.

## 2026-10-17 운영 스키마 실제 설치·권한 회수·OFF 서비스 준비

- codex/billing-test-deployment. 승인된 prod8collection collMod custom role만 사용자에 임시 부여했다. inspect exit0/collection0 및 기존 Billing prod 서비스 부재 확인 후 install-empty task revision2 exit0/ALL_BLOCKED, 별도 verify revision3 exit0/41collection/ALL_BLOCKED 성공. 최초 RunTask override8192자 제한은 실행 전 거절, 별도 task definition으로 해결했다. 기존DB 재설치·레코드 삭제 없음.
- 임시 역할 계정 연결 제거·저장 후 Atlas 새로고침에서 readWrite@to-teacher-billing-prod만 확인하고 반영 중 배너 소멸 확인. custom role 정의 자체는 미할당 보존. 비밀값·사용자 레코드 출력 없음.
- 사용자 이번 변경 commit/push·Billing prod OFF 배포 예외 승인 수신. 기존 검증 image digest를 고정한 prod:1/service 생성(1task 요청), 모든 업무flag 기본OFF/자동DDL OFF/transaction 요구ON. 외부 listener와 DNS는 아직 연결하지 않음. 실제 startup/health 확인 중이며 앱 연결·결제 준비 완료가 아니다.
- 변경파일은 운영 bootstrap 및 기반 runbook/CURRENT_STATE/WORKLOG. runtime API/body·JWT·보존/삭제 계약 불변, 기존 cleanup Slack 등 dirty 보존. 직전 clean test1806통과/실패0/skip0 이후 제품코드 무변경이라 Gradle 재실행 없음. 실환경 검증은 위 schema installer/verify이며 구매·환불·탈퇴E2E를 대신하지 않는다. 다음은 health 확인·이번 범위 Git 전달, DNS/ALB·상대 공개키·RC·backup/capacity 준비 후 별도 활성화다.
- 후속 기동 확인: prod:1 task a61012db80ab46d7bdf13981fb501b2c RUNNING/HEALTHY, 실행1·대기0. healthcheck는 localhost HTTP8082 /actuator/health200을 요구. Tomcat/앱 Started, 최근100로그 Exception class 없음. 외부 ALB/TLS·앱E2E 미검증. 이번 운영 도구/기록4파일만 stage하며 기존 cleanup Slack/테스트 도구 등은 변경 없이 보존했다. 예상 밖 제품 변경 없음.

## 2026-10-17 prod 업무 ON 요청의 필수 설정 점검

- 사용자 신규 Billing 전체 활성화 요청에 따라 CloudShell에서 Secret metadata만 조회. prod revenuecat/identity-workload-public-jwks/learning-core-workload-public-jwks에 AWSCURRENT 버전이 모두 없음. prod:1 task definition은 MONGODB_URI만 참조하고 enabled=true flag 없음. 비밀값 조회·출력 없음.
- 설정 미준비 상태에서 flag만 바꾸거나 테스트 신뢰키로 대체하지 않았다. 외부 호출 부재와 무관하게 worker가 자발 실행됨을 안내. 운영 RC/상대 공개키·DNS/ALB 연결, 기존 운영 gate 검증을 선행해야 한다. 제품/DB/배포/기능 변경 없음, 문서2개 기록만 추가. read-only 점검이므로 Gradle 미실행. 기존 dirty 보존, 새 commit/push 없음. 앞선 운영 준비 commit6ebb94e push는 완료됐다.

## 2026-10-17 운영 LC 공개키 등록·Identity 인계·RC 준비

- 사용자 '어 해줘'와 exact LC 공개키 등록 승인에 따라 신규 운영 LC Secret을 점검했다. PRIVATE_JWK 내부에서 공개필드만 추출, RSA3072 및 서명/검증 성공. 기존 Billing 대상 Secret이 빈 것을 확인하고 BILLING_WORKLOAD_LEARNING_CORE_PUBLIC_JWK_SET으로 등록·재조회 일치 확인. private필드 복사 없음/기존LC Secret·역할·서비스 변경 없음/수신OFF 유지. 공개키 fingerprint 33d8c4cfe945f66bbd72920c0b551a8c06b53da9f50bd60dd3268715640c6102. 확인 화면 /private/tmp/billing-prod-lc-public-key-20261017.png.
- 사용자 기존RevenueCat 프로젝트·앱 사용 및 Identity 인계문서 작성 선택. docs/contracts/Identity-billing-production-key-readiness-2026-10-17.md 작성, 외부 메시지 발송 없음. Identity 운영 전용 Billing키는 현재 inventory에서 미발견. 사용자 로그인/LC용 키 재사용하지 않음.
- RC 로그인 화면 확인. test task에서 프로젝트70a245ef·budget DB to-teacher-revenuecat-budget 확인. Secret의 URI는 프로세스 내부에서 hostname 동등성만 비교해 test/prod 클러스터가 다름을 확인했다. 코드 PaymentProviderWorkerConfiguration은 동일 MongoClient로 budget DB에 연결하므로 DB명만 복사하면 공동 budget이 아니다. 별도 공유 연결/최소권한 보완 또는 프로젝트 분리 검토 없이 worker ON 금지. 사용자 선택은 기존 프로젝트 유지이며 임의 프로젝트 분리/limit완화는 하지 않는다.
- CURRENT_STATE/WORKLOG와 Identity 인계문서만 로컬 변경. 제품 API/코드/DB/배포/활성화/commit/push 없음, 기존 dirty 보존. 실제 암호학·Secret 저장재조회 확인이며 공동HTTP/E2E 아님. 제품코드 무변경으로 Gradle 미실행, diff 검사 수행. 다음은 RC 로그인·운영키 준비와 shared budget 연결 보완, Identity 공개키 수령이다.

## 2026-10-17 저빈도 테스트 budget 예외·운영 RevenueCat 키 폼

- 사용자 로그인 완료 및 테스트 저빈도 위험수용 응답. 기존 프로젝트·앱 유지, 초기 환경별 budget 분리 예외를 별도 runbook에 명시했다. 합산rate/공유차단 보장 없음, 기존429/Retry-After/검증·대기 정책 불변. 무제한/무료fallback/키재사용/테스트서버 변경 없음.
- 기존 IAB 로그인 탭과 별개로 새 로그인된 탭2에서 프로젝트70a245ef 확인. API키 생성 폼에 billing-production-read/V2/Purchases Read only, 기타No access 준비. Generate/키생성/Secret 저장은 미실행이며 사용자 직접 제출 대기. 결제조회 코드의 /v2/projects/.../customers/.../purchases 및 /purchases/... 사용을 확인했다. 실제 권한/API 검증은 키 저장후 수행한다.
- 변경파일은 예외 runbook/CURRENT_STATE/WORKLOG, 제품코드·DB·배포·업무ON·commit/push 없음. 문서·UI 준비만으로 Gradle 미실행, diff 검사. 기존 dirty 보존. 다음은 사용자 키 생성→숨김입력으로 운영 Secret 저장→API/상품/budget 준비 확인이다.

## 2026-10-17 운영 RC 키 숨김 입력 인계

- 사용자 키 생성 완료 보고에 따라 Billing CloudShell terminal1에서 getpass 명령을 시작했다. TTY/숨김입력 보장, 대상 tosunsaeng/prod/billing/revenuecat의 version0 선행 조건, key형식 검사, BILLING_PAYMENT_PROVIDER_API_KEY 필드 저장 및 값 비출력 재조회 일치 검사를 준비했다. 사용자가 직접 붙여넣고 Enter해야 저장된다. RC 화면의 실제 키는 읽거나 복사하지 않았다.
- 현재는 입력 대기이며 저장/RC API 권한 검증/업무활성화 완료가 아니다. 오류는 예외class만 출력한다. 변경은 작업기록2파일, 제품코드·DB·배포·commit/push 없음. Gradle은 문서/입력준비 작업으로 미실행. 기존 dirty 보존. 다음은 저장 성공 확인 후 RC 읽기권한·운영상품/budget/Secret 추가항목 검증이다.

## 2026-10-17 운영 RC 키 저장 성공·읽기 경로 점검

- 사용자 입력 후 CloudShell BILLING_PROD_RC_KEY_SAVED_AND_VERIFIED 확인. 운영 Secret에서 키를 프로세스 내부에서 읽어 공식 api.revenuecat.com/v2/projects/70a245ef/customers/{새 임의UUID}/purchases?environment=production&limit=1로 GET1회 수행. redirect 금지/timeout10초, 키·응답원문·고객정보 미출력. 404 JSON resource_missing 확인(401/403 아님). 존재하지 않는 자료의 읽기 경로 점검이며 정상구매200/권한전체/원장반영 성공을 주장하지 않는다.
- 신규고객 생성·실구매/환불·제품코드·DB·배포·ON·commit/push 없음. CURRENT_STATE/WORKLOG만 추가, 기존 dirty 보존. 운영 API점검으로 Gradle 미실행, diff 검사. 다음은 환경별 budget 준비·상품매핑·lookup/reference/webhook Secret 추가항목 및 Identity 공개키/DNS/ALB다.


## 2026-10-17 — Identity 운영키 인계서 상태 정합성 수정

- 브랜치: codex/billing-test-deployment. 목표: 결론2와 위험 항목의 발신키 준비 상태 충돌 해소.
- 변경: Identity-billing-production-key-readiness-2026-10-17.md, CURRENT_STATE, WORKLOG.
- 근거: 기존 prod 키 저장·검증 완료 기록. Identity→Billing 이벤트 검증 공개키와 Billing→Identity 복구 발신키를 구분하고 과거 미발견 표현 수정.
- 검증: 수정 문구 확인 및 대상 문서 diff 검사. 문서만 변경하여 제품 테스트 미실행.
- 유지/위험: workload-auth-r1 계약 및 기능 OFF/ALL_BLOCKED 유지. AWS 현재 상태 재조회 없음. Identity 공개키 전달/상대 등록·실연동 확인은 후속. 코드/Secret/설정/배포/외부 메시지 변경 없음, 기존 작업 보존.

## 2026-10-17 운영 RevenueCat 상품 매핑·budget 권한 사전 확인

- 브랜치 codex/billing-test-deployment. test ECS 설정에서 APP_STORE/PLAY_STORE 각각 premium1d/3d/7d/14d/28d 확인. 운영 catalog 설치 또는 RC 상품 상태 검증을 대신하지 않는다.
- RevenueCatBudgetSchema는 별도 DB의 revenuecat_read_budgets strict validator 및 _id_ 단일 index를 검증한다. 신규 생성만 지원하며 불일치 수정/collMod는 하지 않는다. PaymentCatalogService의 환경·기간·가격·중복 매핑 검사는 유지한다.
- Atlas Project 0 화면에서 운영 Billing 계정의 기존 readWrite@to-teacher-billing-prod만 확인. 별도 budget DB 접근 추가는 보안 권한 확장이므로 사용자 action-time 승인 요청 후 진행한다. 아직 권한/DB/Secret/배포/활성화 변경 없음.
- 작업기록2파일만 변경, 기존 dirty 보존. 제품 로직 미변경으로 Gradle 미실행; 정적 코드 확인 및 ECS/Atlas 읽기 점검. 다음: budget DB 한정 권한 승인·빈 DB schema 설치/검증, catalog 대조, 운영 Secret 보완. 사용자/거래 자료 및 비밀값 미출력.

## 2026-10-17 운영 RevenueCat budget DB 설치 완료

- 브랜치 codex/billing-test-deployment. 사용자 직전 권한 추가 승인에 따라 Atlas Project 0의 tosunsaeng-billing-prod에 readWrite@to-teacher-revenuecat-budget만 추가. 기존 readWrite@to-teacher-billing-prod/비밀번호/다른 사용자 유지. Atlas 배포 배너 해소와 실제 connectionStatus 역할2개 확인.
- CloudShell 독립 임시 venv의 pymongo4.10.1 사용. 초기 --user 설치는 기존 venv 정책으로 실패하여 별도 /tmp 환경으로 분리했으며 서버 런타임 의존성은 변경하지 않았다. URI는 Secrets Manager에서 메모리로만 읽고 host 확인. 실제 budget collection0 및 PRODUCTION/billing ALL_BLOCKED를 사전 확인했다.
- RevenueCatBudgetSchema r1의 validator를 원문 패턴·BSON long minimum 포함 대응시켜 revenuecat_read_budgets 신규 설치. strict/error options 전체 일치, _id_ index1개, 문서0 재조회 확인(PROD_RC_BUDGET_INSTALLED_VERIFIED). 기존 데이터 삭제/collMod/업무 barrier 해제/서비스 설정·배포 변경 없음. 승인된 환경별 budget 분리 예외 유지.
- 변경 파일은 CURRENT_STATE/WORKLOG만. 제품 로직 미변경으로 Gradle 미실행, DB 구조 실검증 및 diff 검사. Java verify 기동·상품catalog·추가 Secret·DNS/ALB·E2E는 남음. 이번 결과는 결제 활성화 완료가 아니다. 기존 dirty 보존, commit/push 없음.

## 2026-10-17 운영 상품 catalog 등록

- 브랜치 codex/billing-test-deployment. 사용자 상품 매핑 확인·운영 등록 요청 수행. RevenueCat 프로젝트70a245ef Products 및 premium Offering UI에서 APP_STORE app3987acee69/PLAY_STORE appc95ad9b387의 premium1d/3d/7d/14d/28d 상품·패키지 연결 확인. Test Store 상품 제외, RevenueCat 설정 변경 없음.
- 운영 payment_products 기존0건 확인. 기존 unique index3개와 PRODUCTION/billing ALL_BLOCKED를 확인한 단일 Mongo transaction(majority write concern)으로 catalogVersion1 총10행 삽입. offer 기간86400/259200/604800/1209600/2419200초·기준가격9000/19000/29000/49000/69000KRW는 PaymentOffer 구현 유지. BSON long 숫자, saleEnabled=true 상품 매핑을 준비했지만 ECS API/worker flag 및 barrier는 변경하지 않았다.
- rcProductId(1/3/7/14/28일 순): Apple prod2b3350ec70/prod0c6f7c904a/prodb754238599/prod2bb863a58c/prodc1485631a1; Google prod61b23b6254/prod5b51dba40d/prodd55bc1da76/prod83987f38b4/prod903d4e96c7. packageId는 각 storeProductId와 동일.
- PROD_CATALOG_REGISTERED_VERIFIED rows10/offers5/stores2/catalogVersion1 확인, _id 제외 전필드 재조회 일치. RC 화면의 Google Published/Apple Ready to Submit은 관측 상태이며 Apple 심사/판매승인이나 Store 실구매 검증을 의미하지 않는다.
- 변경 파일은 기록2개, 제품 코드 미변경으로 Gradle 미실행. 실제 DB transaction·재조회 및 diff 검사. 삭제/덮어쓰기/가격변경/실구매/배포/LC·Identity 변경/commit·push 없음, 기존 dirty 보존. 다음은 추가 운영 Secret·DNS/ALB·상대 공개키 및 정상 기동/public API/E2E 검증이다.

## 2026-10-17 운영 Secret 보완 명령 인계·네트워크 읽기 점검

- 브랜치 codex/billing-test-deployment. 운영 RC Secret 필드명만 출력해 API_KEY만 확인. 기존 ALB Billing 규칙은 test 대상, 두 prod 호스트 DNS unresolved, ACM 목록에 해당 prod SAN 인증서 없음. 기존 인증서·경로·역할 무변경.
- CloudShell terminal1에 사용자 실행용 명령 준비(Enter 미실행). 정확히 API_KEY만 있는 Secret일 때만 기존값 보존하고 독립32byte lookup/reference키, webhook Authorization/signing값 및 prod-v1 버전/ID 추가. 저장 직전 VersionId 확인·저장후 전필드 일치 검사, 비밀값 비출력. 실제 키 생성/저장은 사용자 직접 실행 후 확인한다.
- 기존 provider 요구 형식 확인, webhook 실제 송신측 서명 호환/등록 완료를 주장하지 않으며 ingress OFF 유지. DNS/인증서/ALB 공개는 후속, 서버/제품코드/DB/commit·push 무변경. Gradle은 설정 점검·인계 작업이므로 미실행, 문서 diff 검사. 기존 dirty 보존.

## 2026-10-17 운영 Secret 저장 독립 확인·ACM 신청 인계

- 브랜치 codex/billing-test-deployment. 사용자 실행 보고 후 terminal에는 AssertionError가 있었으나 읽기 조회에서7필드/버전2개 존재 확인. lookup/reference는 각32byte, prod-v1 ID, Authorization/signing 형식 정상. AWSPREVIOUS와 비교해 API키 동일, 신규4값 독립 확인. 오류 발생 assertion 위치는 출력되지 않았으므로 특정 실행 경위를 확정하지 않으며 이미 저장된 구성의 재실행 거절과 부합한다. 재생성/덮어쓰기하지 않았다.
- 가비아 로그인 유지 확인. billing-prod.to-teacher.com 및 billing-workload-prod.to-teacher.com exact SAN의 AWS ACM RSA2048/DNS 검증 신청 명령을 준비했다. 동일 pending/issued 인증서 재사용 및 idempotency token 적용. 사용자 Enter 실행 전이며 기존 인증서/listener/DNS/서버/업무ON 변경 없음.
- 비밀값 비출력. 변경 기록2파일, 코드 미변경으로 Gradle 미실행, 실제 Secret 형식·원값 보존 읽기 검사 및 diff 검사. 기존 dirty 보존. 다음은 인증서 신청 결과·DNS 검증 레코드와 ALB 연결이며 신규 공개 범위를 확인 후 적용한다.

## 2026-10-17 운영 앱 DNS·HTTPS·ALB 연결

- 브랜치 codex/billing-test-deployment. 사용자 exact 승인 범위: 인증서 DNS2개, 앱 hostname CNAME, 네 API의 exact path/method, 그 외404; workload는 인증서만 준비. Gabia 저장 후3개 레코드 재조회 확인, ACM ISSUED, 기존 listener 인증서는 추가 전후 보존 검증.
- ALB110~113 forward/114 deny404를 추가하고 기존 규칙 불변 확인. 최신 서비스 prod:2를 재조회하고 environment/secret/image 보존, public connector8083/port mapping만 추가해 prod:3 및 public TG 연결 배포 시작. 변경 직전 taskDefinition 일치 검사. LC/Identity·DB·키값·기능ON은 변경하지 않음.
- 초기 target initial 및 TLS hostname mismatch 관측; 검증 우회 없이 전파/배포 완료 확인 진행. 제품 코드 미변경으로 Gradle 미실행. 현재 로컬 DNS 조회 미해결도 관측. 기록3파일 외 기존 dirty 보존, 이번 commit/push 없음. 최종 실행 결과는 아래 후속 기록으로 남긴다.

- 후속 검증: prod:3 실행1/대기0, TG8083 healthy, 이전 배포 DRAINING/새 배포 IN_PROGRESS. 공개 DNS resolver NOERROR 및 정확한 CNAME/TTL600 확인; CloudShell/local negative cache 잔존. DNS 전파와 분리한 curl --connect-to(운영 hostname/SNI·CA검증 유지, -k 미사용) 앱4경로·internal POST·루트·DELETE 총7건 exit0/404 확인. 최초 TLS mismatch는 이후 같은 hostname 검증 성공으로 해소. 업무OFF 예상404이며 인증된 구매/E2E가 아니다. 일반 DNS와 배포 COMPLETED 확인이 남는다. diff --check 통과.

- 최종 ECS 재조회 PRIMARY COMPLETED/실행1/대기0, 이전 deployment 제거 확인. 일반 CloudShell DNS curl exit6은 잔존하므로 전환경 DNS 전파 완료로 보고하지 않는다. 증거 화면 /private/tmp/billing-prod-public-connection-20261017.png. 현재 완료 범위는 DNS 저장·공개 resolver 반영·인증서·ALB·OFF 배포이며 업무 활성화/실구매 검증은 별도다.

## 2026-10-17 운영 결제 활성화 준비 후속 점검

- 브랜치 codex/billing-test-deployment, 목표 DNS 후속 검증과 활성화 의존성 식별. CloudShell 일반 DNS HTTPS products exit0/404 확인, prod:3 COMPLETED/실행1/대기0. local resolver 미해결은 남지만 외부 접속 성공을 확인했다.
- metadata 조회에서 Identity/LC 공개키·RC Secret AWSCURRENT 모두 존재. 현재 태스크의 Secret 이름은 MONGODB_URI/BILLING_WORKLOAD_IDENTITY_PUBLIC_JWK_SET/BILLING_WORKLOAD_PRIVATE_JWK3개. 비밀값 미조회/비출력. Identity 공개키 미준비라는 이전 인계는 현재 metadata상 갱신됐으며 실제 키 검증은 별도.
- PublicSecurityConfig/PaymentProviderWorkerConfiguration 기준 앱 활성화는 사용자 JWT issuer/JWKS, limiter, schema 검증, scope/attribution, recovery fence, provider worker/API·lookup·reference키, catalog/app/budget 매핑을 요구한다. 현재 구성은 connector만 ON이며 단순 flag 변경만으로 결제 준비 완료가 아니다.
- 코드/서버/DB/키/외부 메시지 변경 없음, 기록2파일만 갱신·기존 dirty 보존. 분석/읽기 작업이라 Gradle 미실행. 다음은 운영 설정 연결 및 검증 후 별도 범위 승인으로 업무 활성화; 자동삭제/실제 Store 환불/LC·Identity 변경은 포함하지 않는다.

## 2026-10-17 앱 결제 활성화 승인 후 Identity 선행조건 확인

- 사용자 앱 결제 설정 연결/검증 후 ON 승인, 자동삭제/Store 환불 실행/타서버 변경 제외. 브랜치 codex/billing-test-deployment. 최신 ECS/Secret metadata/운영 DB 최소 count 및 control/공개JWKS 읽기 검사 수행. 운영 변경 전 선행조건 불충족을 발견해 ON 미수행.
- Identity prod:13에 구매 control/fence/scope 세 환경변수 없음, 관련 Secret 이름 없음, envFiles/command/SPRING_APPLICATION_JSON override 없음. 로컬 application.yml 기본false·JwtAccessTokenIssuer purchaseFence 조건 확인. 실제 사용자 토큰을 발급/열람하지 않았다. 운영 JWKS TLS검증 포함200/public1개/비밀필드 없음. 기존 staging 표기 issuer 불변.
- Billing prod:3은 여전히 OFF, control PRODUCTION/billing ALL_BLOCKED/schema1/version1. 상품10개와 budget collection 존재 확인. 조회한 accounts/purchases/trial/links 컬렉션 count0은 전체 사용자/운영 데이터 부재 증명으로 사용하지 않는다. 별도 Secret 실제 값은 Mongo 접속을 위해 메모리에서만 사용하고 비출력.
- Identity 인계서 §7 보완, CURRENT_STATE/WORKLOG 갱신. 제품 코드/서버/DB write/Secret/commit/push/외부 메시지 없음, 기존 dirty 보존. Gradle은 코드변경 없어 미실행, diff 검사. 다음은 Identity 구매 권한 준비와 탈퇴 연동 회신, Billing 설정/스키마 검증 및 승인된 ON 작업 재개다. 타서버 변경에는 별도 사용자 지시가 필요하다.

## 2026-10-17 LC 운영 인증·TLS 연결 인계 응답

- 브랜치 codex/billing-test-deployment. LC 요청문 읽기 후 운영 refund 공개키 및 LC 등록키 SPKI 대조. 공개7필드만 tmp 파일에 저장하고 Node crypto RSA3072·지문 일치 검증. private JWK는 AWS 프로세스 메모리에서 공개부분 비교에만 사용, 비밀필드 출력/저장 없음. 저장 public set과도 일치 확인.
- 운영 scope 기준 tosunsaeng-billing-prod는 Identity 실설정과 Billing scope source 코드로 대조했으나 Billing 태스크 미주입임을 명시. workload 예정 origin과 미연결 상태 구분: prod:3 ports8082/8083, receiverOFF/LC키 미주입, workload host rule0/DNS미해결. Secret 등록만으로 runtime 완료를 주장하지 않는다.
- PaymentRefundConfiguration이 test/SANDBOX로 제한해 운영 ON에는 별도 보완 필요 발견·인계. 코드/gate 우회 없음. 회신 문서 신규 및 상태/작업기록 갱신, 실제 공개키는 Git 아닌 tmp 파일. Gradle 미실행(문서/읽기 작업), Node 검증/diff 검사. 서버/DB/Secret/LC 저장소/외부 메시지/commit/push 무변경. 기존 dirty 보존. 다음은 담당별 TLS/태스크 설정 준비와 운영 gate 보완·공동검증.

## 2026-10-17 TMI-220 환불 운영 기동 조건 보완

- 브랜치 codex/billing-test-deployment. 사용자 운영지원 코드 보완 승인, 실제 활성화/배포 제외. Jira TMI-220을 읽어 두flag/기본OFF·원장·불변outbox·Session보호·exact204·배포 별도 기준 확인, Jira write 없음.
- PaymentRefundConfiguration에 prod/production/PRODUCTION/실제 *-billing-prod DB(길이63 이하)/fence의 정확 조합만 추가. 기존 test 조합 보존, 혼합환경 거절. 별도 승인/팬아웃flag 추가 없음. 실제 Store DB 이름을 사용하며 OFF에서 Store/키를 요구하지 않는다. RefundRuntimeConfiguration 주석 정합화. 기존 schema·transaction·provider·publisher 인증·알림 의존성은 수정하지 않았다.
- PaymentRefundProductionActivationTest 신규32개: 정상 두조합/잘못된profile·environment·DB·길이·fence, OFF 기본/무의존, 실제 Store DB 및 runtime 의존성 누락 거절. 코드2파일·테스트1파일·신규runbook·LC회신 후속표시·상태/작업기록만 이번 변경. 기존 cleanup Slack 등 dirty는 그대로 보존, 예상 밖 편집 없음.
- 첫 ./gradlew clean test는1838개 중1실패(RefundEvidenceTest의 기존 pipeline incomplete 메시지 기대). 기존 접두사를 보존하도록 오류문구 수정 후 전체 clean test 재실행 중. 최초 sandbox는 Gradle cache lock 접근 제약으로 시작 전 실패해 승인된 실행으로 진행했다. 테스트는 fake/local이며 실제 AWS/Store/Identity/LC 호출 추가 없음.
- 외부 wire/body/hash/보존/금액·자동환불 실행·권리정책 변경 없음. 운영 prod:3에는 미배포이며 runtime 전체기동/LC E2E는 미확인. 후속은 TLS/키/scope·schema/증거/알림/Identity구매 준비 확인 및 별도 승인 배포·공동검증. commit/push 없음.

- 2026-10-18 KST 최종 검증 완료: 수정본 ./gradlew clean test BUILD SUCCESSFUL, 3분59초. XML 전체1838개/실패0/오류0/skip0, 신규 PaymentRefundProductionActivationTest32개 포함. 최초 실패1건은 기존 메시지 접두사 보완 후 전체 재실행으로 해소. diff --check 통과. 실제 운영 환경에서 application context 전체 기동 또는 LC 공동E2E를 실행한 결과는 아니다.

## 2026-10-18 Billing–LC 운영 연결 준비 정의

- 브랜치 codex/billing-test-deployment. 사용자 다음 연결 준비 요청. LC 최신 인계 §7~9를 읽고 수신ON에 sealed runtime/구앱 전환 조건이 남음을 확인, 범위를 미배포 Billing 설정 준비로 제한하고 사용자에게 설명했다.
- AWS 최신 Billing서비스prod:3·LCprod:8(미배포9), 각TG/역할/Secret 참조를 읽었다. 기존 exact Secret read 허용과 AWSCURRENT 확인 후 Billingprod:4만 등록. LC공개키/환불signer/TLS3필드 총5참조와 workloadproduction/port8443/scope 추가, receiver/refund/deletion 명시OFF. 기존 image/ports/entrypoint/역할/env/secret 보존. 중복등록 전 latest==service 확인, 등록 전 서비스revision 재대조, 등록 후 정렬 기반 전체필드 일치 및 서비스 revision/LB 불변 확인.
- prod:4는 구성 보관용: TLS파일 materialization 시작명령·8443mapping/TG/DNS/ALB/LCorigin·운영코드 이미지 반영 미완료. 실제 연결 성공으로 보고하지 않는다. 실행prod:3 running1/pending0 유지. API/wire/원장/키값/IAM/DB/LC코드·서비스 변경 및 배포·ON·외부 메시지 없음.
- 신규 runbook/상태/작업기록 갱신, 증거 /private/tmp/billing-prod-task4-prepared-20261018.png. 제품코드 미변경이라 Gradle 미실행, 문서diff 검사. 기존 dirty 보존/예상밖 편집 없음/commit·push 없음. 다음은 LC 전환 조건·origin 확정 후 명시 연결 변경 승인 및 최종 설정 합치기.

## 2026-10-18 운영 앱 설정 OFF 연결·배포 시도

- 브랜치 codex/billing-test-deployment. 사용자 3번 설정 사전 연결 승인에 따라 RC/user JWT/limiter를 연결하되 업무OFF·DB ALL_BLOCKED 유지. prod:4 기반 prod:5 전체필드 검증 후 배포했다. 참조8개 추가, 실제 Identity issuer 보존, 상품10개/budget DB/키형식 사전 확인. Secret값·권한·네트워크·외부계약 변경 없음.
- prod:5 Secret 주입·Started/컨테이너 HEALTHY는 확인했지만 ALB health checks 실패로 태스크가 종료됐다. 기존prod:3은 healthy, 외부4API404·내부404 유지. 서비스prod:3 원복 요청, 실패prod:5 정의 보존. 배포 성공/구매 활성화로 보고하지 않는다.
- 두 subnet/SG/ALB AZ/NACL/routes 읽기 점검에서 명백한 차단 근거 없음. health grace0이지만 앱 기동 후에도 실패했으므로 단일 원인 확정하지 않음. DB control singleton PRODUCTION/ALL_BLOCKED/version1 확인. 제품코드·LC/Identity·DB 수정 없음.
- 신규 docs/runbooks/billing-prod-app-config-2026-10-18.md 및 CURRENT_STATE/WORKLOG만 이번 로컬 변경. 기존 cleanup/환불 dirty 보존. 코드 미변경으로 Gradle 미실행, 문서diff 검사. commit/push/Jira/외부메시지 없음. 다음은 원복 안정화·ALB 원인 진단 후 OFF 재배포이며 Identity 구매 준비와 LC 전환은 별도다.
- 원복 새prod:3에서도 unhealthy 발생. 기동 경합 가설 확인용 health grace300을 잠시 적용했으나 정상복귀 미확인으로 원래0 복원. 진행 배포에 stop_service_deployment(ROLLBACK)을 요청해 마지막 성공 배포로 복귀하도록 했다. ALB 건강 판정/인증/네트워크는 완화하지 않았다.
- 직전 기록 정정: stop/grace0 코드는 실행 전 IN_PROGRESS assertion에서 중단돼 실제 적용되지 않았다. 재조회에서 prod:3 SUCCESSFUL/COMPLETED·실행1/대기0·신규target healthy 확인. grace300 유지. 기동 유예 후 안정화 관측을 근거로 prod:5 업무OFF 재배포를 진행했다. ALB 건강 판정·인증·네트워크 변경 없음.
- 최종 prod:5 재배포 COMPLETED/실행1/대기0, 신규태스크 HEALTHY/Started·ALB healthy 확인. 이전targets draining. HTTPS4API+내부1경로404, DB ALL_BLOCKED v1 재확인. grace300 유지 외 서비스네트워크/검사기준/IAM/Secret값/DB 불변. RC/user JWT/limiter 설정 주입·OFF 기동만 완료이며 구매/RC실호출/정상JWT/E2E 미검증. 증거 /private/tmp/billing-prod-app-config-20261018.png. runbook/상태 최종 갱신·diff검사, 기존 dirty 보존/commit·push 없음.

## 2026-10-18 LC 운영 전환 준비 여부 확인

- 브랜치 codex/billing-test-deployment. 사용자 준비 여부 질문에 LC_PRODUCTION_CUTOVER_RUNBOOK_2026-10-18.md 전체와 Billing 연결 준비/현재 상태를 대조했다. 실행안 작성과 실제 전환 준비를 구분한다.
- Billing prod:5 OFF 기동은 직전 확인 완료. 운영 환불 지원 이미지·TLS 시작명령/port/네트워크·공동 리허설과 Identity 구매 준비, LC 봉인/배출/생성제한·검증계정은 남음. LC 문서의 prod:3/4는 최신 실행상태가 아니다. LC 파일은 수정하지 않았다.
- CURRENT_STATE/WORKLOG만 기록. 코드/서버/DB/계약 변경 및 AWS 재조회 없음. 분석 작업이라 Gradle 미실행, diff 검사. 기존 dirty 보존. 다음은 생성제한 전에 Billing 검증 이미지·workload TLS/네트워크 변경안부터 준비하는 것.

## 2026-10-18 Billing 운영 전환 로컬 후보 준비

- 브랜치 codex/billing-test-deployment. 사용자 준비 요청에 기존 Dockerfile/시작스크립트·수신filter 및 LC 실행안/인계를 대조했다. Dockerfile.workload·전용dockerignore·network-none smoke script·전환 준비 runbook 추가, CURRENT_STATE/WORKLOG 갱신. 기존 제품코드/dirty 보존.
- 로컬 linux/amd64 후보 빌드 성공. 기존 TLS 스크립트를 image에 포함해 비root 기동, PKCS12/DB/fingerprint 검사를 연결한다. 정상/DB불일치/지문불일치3case 통과. 가짜 자료·Java launch stub이며 실제 JVM전체기동/운영TLS 인증 검증 아님. 기본 Dockerfile 불변.
- HEAD6ebb94e+기존 미커밋 환불/cleanup/Slack 포함 후보임을 명시. image/JAR hash는 runbook에 기록. 운영 후보를 HEAD 단독 출시본으로 오인하지 않는다. 전체 clean test bootJar 실행 중, shell구문/diff 검사 통과.
- exact LC POST 경로/8443 TG·host·기존앱8083보존/실제origin·출구대역 미확정 및 공동전환 조건 문서화. 업무API/원장/보존/인증계약 변경 없음. AWS 재조회/등록/배포/네트워크/DB/LC·Identity/commit·push 없음. 다음은 테스트완료·Git기준고정·명시이미지전달과 상대origin/대역 확인, 승인된 실제 연결·리허설.
- 최종 clean test bootJar BUILD SUCCESSFUL 4분7초. XML1838개 실패0/오류0/skip0. 이미지smoke3개/shell구문/diff통과. 현재 후보의 로컬 검증만 완료, 실제 운영 TLS·정상JWT·환불/삭제 E2E 미실행. 예상밖 변경 없음.

## 2026-10-18 운영 후보 커밋·전달 착수

- 브랜치 codex/billing-test-deployment. 사용자 이번 변경 커밋/이미지전달 및 LC주소·대역 확정 승인. 후보에 포함된 기존 환불/cleanup/Slack 변경과 관련 기록을 보존해 함께 고정한다. 코드 추가수정 없음.
- 직전1838개 Java 및 이미지3case 통과 유지, 이번 Node maintenance8개 통과·diff검사·준비문서 민감값패턴 검사 통과. 로컬 후보와 Git기준을 연결한 뒤 원격 digest를 확인할 계획이며 아직 push/이미지전달 성공을 주장하지 않는다.
- AWS CLI 자격증명없음 및 기존 콘솔로그아웃 확인. 사용자 재로그인 요청, 실제운영 origin/NAT 대역은 이전 추정값으로 확정하지 않는다. 서버/DB/활성화/LC 저장소 변경 없음.

## 2026-10-18 운영 후보 이미지 전달·LC 주소/대역 확인 완료

- codex/billing-test-deployment, 소스57d6639 커밋 완료. AWS 재로그인 뒤 기존 ECR에 새 prod-workload-57d6639 태그 전달, 원격digest e5666d537391eebc7cffe91896a756f93b93d8acef2d480030e1b56e9e7597f9 확인. 배포/기능 활성화가 아니다.
- LC prod:8/Billing prod:5의 private subnet 두 개→동일 NAT와 공인13.124.57.130 확인. LC origin api-prod.to-teacher.com의 DNS/공개443 인증서 검증 및 기존host rule8080 확인. exact 환불POST/8444 전달은 미적용, 공유NAT이므로 JWT 필수. 구앱 경로 변경 없음.
- runbook/LC 회신/CURRENT_STATE/WORKLOG만 후속 갱신. 코드/외부계약 불변, LC/Identity 저장소 무변경. 이전 Java1838/smoke3/Node8 통과 유지, 이번 TAR hash/config/원격manifest 대조 및 diff 검사. 문서만 변경하므로 Gradle 재실행 없음.
- CloudShell 홈 용량 부족으로 최초 업로드 실패. 불완전파일은 실행하지 않음. 사용자 승인으로 실패 부분파일만 삭제, 공간 확보를 위해 임시이동한 기존 아카이브는 복원. 비밀 인증값은 출력/저장소에 기록하지 않음.
- 예상밖 제품 변경 없음. Git push/merge·서비스배포·DB·ALB/SG/IAM 변경 없음. 다음은 별도 승인된 전용TLS/경로 연결, LC readiness/봉인·구 writer 배출과 공동 검증이다.

## 2026-10-18 LC 8444 실제 적용 전 조건 대조

- 사용자 연결 진행 요청. codex/billing-test-deployment에서 LC 규칙 준비안/전환 실행안/AccessEventRolloutGate/PaidExamTestActivation을 읽고 AWS ECS·task·listener·TG를 재조회했다. LC prod8 실행1/대기0,8080만,8444 대상0,78/79 빈자리,creation-saga=false 확인.
- receiver 단독ON 불가 및 기존시험 sealed runtime/index·공통flag 필요. 생성제한 수단/일정·계정·봉인 승인은 미확정이므로 빈 TG 전달이나 gate 우회 없이 적용을 보류한다. 규칙79만의 부분적용도 하지 않았다.
- 변경은 Billing runbook/CURRENT_STATE/WORKLOG만. 코드·외부계약·LC/Identity·AWS·DB·commit/push 변경 없음. AWS 읽기 및 코드 대조·diff 검사, 제품코드 무변경으로 Gradle 미실행. 예상밖 변경 없음. 다음은 LC 준비 완료 확인 및 생성제한/최종봉인 범위 확정 후 승인된 전환이다.

## 2026-10-18 Billing 운영 LC receiver·8443 연결

- 사용자 이번 Billing 한정 배포/네트워크·LC receiver ON 및 기본 FREE_EXAM_ONCE 정의1건 최초생성 승인. LC 수신 단독ON 불가와 Billing receiver를 구분했다. 브랜치 codex/billing-test-deployment, 기존미커밋 문서 보존.
- prod5→6 명시이미지 e5666d5…·LC receiver/index check·운영DB guard·8443 mapping 추가, 전체정렬대조 차이0. 초기배열비교 실패는 env 순서차이로 독립확인했다. 기존앱8083·역할·Secret·SG·health300·업무OFF 불변.
- 운영 스키마 verify15a19359… exit0/41collection/ALL_BLOCKED. 가비아 재로그인 후 workload-prod CNAME1개추가·기존29개보존, ALB129차단→120/121/122 exactPOST+NAT13.124.57.130/32 전달 적용·기존rules 불변readback. ECS service에8443 TG추가 배포.
- 새task37ae7ab9… bootstrap TLS_OK/Tomcat8443/컨테이너·양TG healthy. NAT probe2164e7e3…8개401/404 기대값·exit0, 로컬 허용외404. 일반TLS/DNS 사용, CloudShell 일부negative DNS 관측은 별도 기록. 정상서명 업무E2E는 미실행.
- 제품코드 변경없음·Gradle 미실행, 문서diff검사. runbook/CURRENT_STATE/WORKLOG만 추가갱신. LC/Identity·Jira·Git commit/push 변경없음. 배포후DB/구task종료 최종확인 중이며 구매·유료시험·환불·삭제 활성화와 혼동하지 않는다.
- 후속 prod6 PRIMARY COMPLETED/실행1/대기0 확인. 배포후 verify03665110… STOPPED/exit0/41collection/ALL_BLOCKED. 구매·자동삭제 활성화 없음, 정상서명 공동업무 검증 미실행.
- 최종8083/8443 모두 신규target healthy만 남고 구prod5 STOPPED(exit143) 확인. DNS/운영완료 화면 저장, 문서diff검사 통과. 사용자 요청 Billing 수신·연결 범위 완료, 업무ON·공동E2E는 별도.

## 2026-10-18 LC 통합 전환 조건부 Billing 회신

- 브랜치 codex/billing-test-deployment. LC 최신 통합 전환 문서의 삭제6개ON/집계OFF 및 회신 요청을 읽고 Billing 기동 의존성·ACCOUNT fence·삭제 route·환불 publisher 조건을 대조했다. 관련 TMI-217/TMI-220, Jira 변경 없음.
- Billing-LC-combined-cutover-readiness-response-2026-10-18.md 신규 작성, CURRENT_STATE/WORKLOG 갱신. 조건부 flag 표·ALL_BLOCKED→OPEN 전제·삭제POST 제한·정상서명 A~F 검증 순서를 기록했다. PURCHASE_BLOCKED는 ACCOUNT writer를 허용하지 않으며 ALL_BLOCKED도 전체 무료 writer의 maintenance 대체가 아님을 명시했다.
- wire/원장/멱등성/보존/인증 계약 불변. LC storage/전달 준비를 선언으로 충족하지 않고 Identity·coverage·유입제한/검증계정/영향범위 확정 후 실행한다. 환불 publisher는 LC8444 실제 준비 전OFF 유지.
- 환경변수명·상대링크·diff 정적검사 수행. 문서만 변경해 Gradle 미실행; LC 시작명령8개 통과는 상대 보고이며 이번 실행 결과 아님. AWS 재조회·코드·서버·DB·LC/Identity·외부전송·commit/push 없음. 기존 dirty 문서는 보존했고 예상 밖 제품 변경 없음.
- 다음: LC 저장소/삭제 전달 사전검증 근거와 Identity/무료 attribution 준비를 맞추고, 생성·구매계정 제한 방식/시간·검증자료·OPEN/업무ON/삭제route 실행 범위를 확정한다. 지금 중단·봉인하지 않는다.

## 2026-10-18 통합 전환 선행조건 후속 대조

- codex/billing-test-deployment. 사용자 후속 진행 요청으로 Identity 최신 capture/연결 문서와 LC combined readiness·BillingDeletionConfiguration/Sender, Billing DeletionService 및 reader rollout을 읽었다. TMI-217/TMI-220 관련, Jira 변경 없음.
- Identity 작업이 Billing prod7 배포 요청/ALB123/PURCHASE_BLOCKED version2를 기록했음을 발견했다. 상대 보고와 AWS 실측을 구분하고 구 prod6/ALL_BLOCKED 덮어쓰기 금지를 회신 §7 및 CURRENT_STATE에 반영했다. 이번 변경 파일은 해당 회신/CURRENT_STATE/WORKLOG뿐이며 기존 dirty 보존.
- 삭제 사전검증은 exact target 한정 일회성 도구 제안으로 구체화. 현재 LC tick은 전체PENDING 선택이므로 운영 그대로 실행 불가, fake ID는 Billing204 근거가 될 수 없음. LC 구현/수락·검증자료·storage/coverage/Identity scope 근거 확보가 다음 단계다.
- 외부 계약/제품 코드 불변, 운영 DB/서버/AWS/LC·Identity 파일/외부전송/commit·push 무변경. 문서 정적 diff 검사, 코드 변경 없어 Gradle 미실행. 예상 밖 제품 변경 없음. 실행을 막는 미확인 조건을 기록했으며 중단/OPEN/삭제를 수행하지 않았다.

## 2026-10-18 Billing 업무 준비 운영 읽기 확인

- codex/billing-test-deployment, 사용자 Identity·기존 권리/유료시험·삭제/OPEN 준비 요청. CloudShell Billing terminal1 재연결 후 ECS·target health 및 DB snapshot 읽기 수행. prod7 완료/8083·8443 healthy, Identity prod15 정상·scope/탈퇴/phone발신OFF 확인. DB 단일control PURCHASE_BLOCKED/version2/schema1, 기존 구매/권리/예약/시험/소유 연결0·상품10, 읽기exit0. 실제 키/URI/개인자료 비출력.
- billing-prod-business-readiness-2026-10-18.md 및 조건부 activation.draft.json 추가, 통합회신/CURRENT_STATE/WORKLOG 갱신. 12flag·live보존·exactCAS/epoch보존·승인/선행조건을 준비했으며 등록 가능한 task나 실행명령이 아님. 기존 schema verify의 ALL_BLOCKED 요구를 발견, DB를 되돌리지 않고 후속 읽기검증 구성 필요로 기록.
- JSON파싱/12env 매핑/diff검사 통과, 제품코드 변경 없어 Gradle 미실행. wire/인증/원장/보존 불변. 서버/DB/ALB/Secret/권한/LC·Identity 변경·commit/push/Jira 없음, 기존 dirty 보존·예상 밖 제품 변경 없음.
- 다음: Identity scope/발신 및 LC 지정대상 삭제검증 준비, 현재barrier 호환 schema 읽기검증, 유입제한/OPEN/업무ON 적용 승인. 빈 Billing자료를 LC기존 사용자 권리부재 또는 phone 준비완료로 해석하지 않는다.

## 2026-10-18 현재 barrier 보존 스키마 검증 도구 보완

- codex/billing-test-deployment. 사용자 후속 진행 요청으로 scripts/ops/BillingProdSchemaBootstrap.java의 verify에 explicit expected barrier를 추가했다. 기본 ALL_BLOCKED 및 install-empty의 ALL_BLOCKED 제한 유지, 기존 설치/업무/API 계약 불변. 검증 전후 단일control·환경/consumer·Long schema1/version·epoch 및 전체문서 동일성 확인. DB update/repair/ON 기능을 추가하지 않았다.
- BillingProdSchemaBootstrapTest.java 신규 독립 회귀21개 컴파일·실행 통과. 초기 JAR 압축해제 디렉터리 실행권한 부재로 컴파일 실패했으나 지정 임시 디렉터리 접근권한 보완 후 성공. 실제 AWS/DB 호출 없는 합성자료 테스트다.
- 전체 clean test 최초 sandbox 캐시권한 실패 후 승인 실행했으나 턴중단으로 완료/결과XML 부재. 재실행 중이며 완료 전 통과로 기록하지 않는다. 문서/CURRENT_STATE/WORKLOG 갱신 및 diff검사.
- CloudShell Billing terminal1 재연결 후 Java21·기존JAR 위치만 읽기 확인. 배포본 hash 대조/보완도구 전달·운영 schema 검증은 미실행. 서버/DB/ALB/권한/LC·Identity/commit·push/Jira 변경 없음. 기존 dirty 보존, 예상밖 제품 변경 없음. 다음은 전체테스트 완료와 exact 배포 클래스패스로 read-only verify, Identity/LC 준비 회신이다.
- 중단 이후 브라우저 연결 갱신으로 기존 탭이 다른 작업 세션 소유임을 확인, 해당 작업을 침범하지 않고 운영 실행 보류. 새 런타임에서 시도한 hash 명령은 변수 부재로 실행되지 않았으며 원격 변경 없음. Billing 전용 세션 확보가 필요하다.
- 최종 전체 clean test BUILD SUCCESSFUL 4분10초, XML1838개 실패0/오류0/skip0. 독립 검증도구21개도 통과, diff검사 통과. 로컬 검증 완료이며 실제 운영 schema 재검증/기능ON은 완료로 표시하지 않는다.

## 2026-10-18 삭제 전달 검증 절차만 확정

- codex/billing-test-deployment. 사용자가 Billing 수신→204→LC 확인 저장 절차만 요청해 운영검증 작업은 중단하고 문서 범위로 한정. TMI-217 신규 절차 문서/CURRENT_STATE/WORKLOG만 변경, 이전 dirty 코드/문서 보존.
- 양측 controller/service/client/sender/delivery/worker 정적 근거로 지정대상·최초event/body·empty204 framing·fact/receipt1건·영속ACKED·전체대상 대기·중복불변 조건을 명시. 운영 정상/중복과 격리 장애주입을 분리, 일반tick·가짜ID·gate선행true·ACK수동수정 금지. 외부계약 불변이며 LC 수락/도구 구현/공동실행은 미완료.
- 문서링크/diff검사, 코드 무변경으로 Gradle 미실행. 이번 AWS/DB/서버/LC·Identity/외부전송/commit·push 없음. 예상밖 변경 없음. 다음은 LC 절차수락 및 지정대상 한정 실행 수단 확인; 운영 활성화는 이 요청에 포함하지 않음.

## 2026-10-18 운영 유료 시험·삭제 및 후속 상품판매·sync 활성화

- codex/billing-test-deployment. 사용자 `켜줘` 및 삭제 경로1개 승인으로 Billing만 운영 변경. 실제204/LC ACKED E2E는 배포후 프론트 검증·서버readback으로 분리하며 사전 전용도구 개발은 요구하지 않음. LC gate/봉인/전달완료 증거를 위조하지 않고 LC·Identity 코드/배포는 무변경.
- AWS 독립조회 Identity prod16·Billing prod7 COMPLETED/1/0. ALB125 exact workload host/POST deletion/source NAT /32→HTTPS8443 추가 후 기존 모든 규칙 동일 확인. 기존 app8083/인증/SG 보존.
- 운영DB snapshot 읽기: PURCHASE_BLOCKED/v2, 실제 purchases/payment_accounts/subscription_entitlements/reservations/attempt_groups/attempt_sessions/billing_subject_links/entitlement_grants/trial_claims0. 첫 읽기 명령의 Python 문자열 문법 오류는 DB접속 전 실패, 수정 후 성공. purchase 컬렉션 오기 payment_purchases는 실제 purchases로 정정 후 재확인했다.
- prod7 복제·지정12env만 ON으로 prod8 등록/배포, 같은 이미지·역할·Secret·포트·나머지환경 보존. Started1/errors0 및 신규양포트healthy 확인. running1/pending0 이후 다음 등록 직전 단일COMPLETED 확인. 단일control exact7필드 CAS majority+j로 OPEN/v3, epoch 보존 및 readback 성공(exit0). 사용자 구매·시험·삭제 데이터를 생성/삭제하지 않음.
- 사용자 후속 상품판매·sync 요청: 제품10개 saleEnabled10·공유budget collection/index 조회 성공·RC Secret참조 준비 확인. prod8 기반 catalog/sync/provider-worker3개만ON prod9 등록/배포 요청. 주기재조회/webhook/환불처리·발신/자동삭제/보존정리/snapshot/feed는 OFF 유지.
- 적용 runbook 신규, activation JSON·통합회신·삭제검증절차·CURRENT_STATE/WORKLOG 갱신. 제품코드 무변경, 직전 전체1838/도구21 테스트기록 유지·이번 Gradle 미실행. diff검사 통과. 기존 dirty 보존·예상밖 제품파일 변경 없음. commit/push/Jira 없음. 다음은 prod9 기동안정화와 실제 프론트 정상로그인/Store구매/sync/시험·삭제검증이다.
- 최종15:03 KST prod9 단일COMPLETED/running1/pending0·신규8083/8443healthy. 구target draining 잔여는 유지, 강제종료 없음. 신규Started1/errors0/failure0. 요청15flagtrue, 제외webhook/재조회/환불/자동삭제false readback. HTTPS app4API 무인증401/app host 내부삭제404. 실제 정상서명204/LC ACKED·Store검증은 미실행. 증거 `/private/tmp/billing-prod9-enabled-20261018.png`, JSON파싱·최종diff검사 통과.

## 2026-10-18 — 운영 환불·재조회 후속 활성화

- 브랜치 codex/billing-test-deployment. 사용자 요청에서 snapshot/feed를 제외하고 백그라운드 기능 준비를 점검했다. LC prod10 환불 receiver ON 및 Billing signing key_ops/sign·LC 공개키 일치를 독립 읽기 확인했다. 비밀키/URI는 출력하지 않았다.
- 운영 control schemaVersion1/version3/OPEN, cleanup0건. 자동삭제는 schema4 명시이관이 필요하여 OFF 유지. webhook host 미설정, 실제 발신·HMAC·전용경로 정합성 미확인으로 OFF 유지.
- 첫 배포 시도는 안전 검토가 실행 전 보류했다. 정확한 운영 기능3개와 LC origin을 사용자에게 제시하고 재승인받았다. prod9를 복제해 refund-processing/refund-publisher/provider-reconciliation true·LC origin만 추가한 prod10을 등록하고 서비스 배포 요청했다. 나머지 설정/이미지/Secret/역할/네트워크 보존.
- background activation runbook·CURRENT_STATE·WORKLOG만 이번 로컬 변경. 제품코드/LC·Identity/DB쓰기/commit/push/Jira/실제Store환불 변경 없음. 코드 무변경으로 Gradle 미실행. 실제 환불·정상서명 전달 E2E는 별도이며 새 서버 기동 결과는 후속 기록한다. 기존 dirty 보존, 예상밖 제품파일 변경 없음.
- prod10 기동은 SPRING_PROFILES_ACTIVE 누락으로 환불 운영격리 검사에서 실패. 기존prod9 HEALTHY 유지, 검사/인증 완화 없이 prod profile을 명시한 prod11 배포 요청. 저장소 profile 참조/추가 설정 없음 대조 완료. diff검사 통과.
- prod11은 refundAlerts의 필수 PaymentProviderAlerts.Sink 부재로 기동실패. 운영 Slack flag/Secret 참조 없음 확인, no-op 대체/검사우회 없음. 기존 정상prod9 기반 재조회만ON·prod profile을 명시한 prod12 등록/배포요청. 환불처리/발신·자동삭제·웹훅·snapshot/feed OFF 유지. 실제 업무 E2E는 미실행.
- prod12 RUNNING/HEALTHY·실행1/대기0·새8083/8443healthy. 초기ALB unhealthy는 기동 후 healthy5회/30초 조건을 거쳐 해소됐으며 네트워크변경 없음. 공개상품/이용권 무인증401. 재조회true·환불2flag/cleanup/webhookfalse readback. 이전targetdraining 및 ECS IN_PROGRESS 잔여, 전체COMPLETED는 아직 미확인. 실제 환불/사용자 데이터 테스트 없음.
- 후속최종 prod12 단일COMPLETED/1/0 확인. 새ALB2개healthy, 구targetdraining 보존. 재조회ON/환불2flag·cleanup·webhookOFF 재확인. 최종기록/정적diff검사 통과. 이번 목표 중 환불은 운영Slack 연결, 자동삭제는 명시DB이관, webhook은 실제발신정합성/경로 연결이 남으며 모두ON 완료로 보고하지 않는다.

## 2026-10-18 — 운영 Slack 채널 준비

- 웹훅 최종: 새 HMAC키 반영 후에도RC401 지속. RC Authorization을 AWS 저장 기존값으로 재등록한 뒤 공식TEST422(인증통과 후 SANDBOX/Test Store 거절), 새키 직접서명 PRODUCTION TEST200. prod14 단일COMPLETED/1/0·HEALTHY·양포트healthy 확인. 모든 요청flagON 유지·snapshot/feedOFF. 실제Store구매/환불/자동삭제E2E 미실행. 키만 원인으로 단정한 설명은 이 기록으로 정정. 제품코드/Gradle/commit/push 없음, diff검사 및 기존dirty보존.

- 후속 사용자 Rotate 완료: 실제 RC 새 키 표시창 확인 후 해당값을 getpass로 운영 Secret signing secret 필드에만 전달/저장 성공, 기타필드 보존. prod14 동일 task definition forceNewDeployment 요청, HMAC ON 확인. Secret 출력/테스트키/다른서버/제품코드 변경 없음. 새 task 전환 뒤 RC 공식 TEST 인증 재확인 예정.

- prod14 최종 단일COMPLETED/1/0·HEALTHY·양포트healthy, startup1/failed0/errors0 확인. 요청flag ON/control4 OPENv5/cleanup0·snapshot/feedOFF readback. RC 공식 TEST401, AWS저장값 직접서명한 PRODUCTION TEST200(ignored inbox, 구매/환불 없음). RC값 불일치 미해결로 signing secret 사용자 Rotate·창유지 요청. 기본 RC TEST는 SANDBOX/Test Store라 후속422 예상, 인증/환경 검사 완화 없음. 이번 제품코드/Gradle/commit/push 변경 없음. 전체 RC연동/E2E완료로 기록하지 않음.

- 통합후속: 사용자 signing secret 직접저장 성공 및 RC HMAC ON/Production only 확인. 승인된 prod13 desired0 후 약6분 drain·실제 모든 task STOPPED 확인. exact validator/index/control/cleanup0 재검증 후 control1 OPENv3→ALL_BLOCKEDv4→schema4 OPENv5 CAS/epoch보존 성공. collMod/추가DB권한/구매자료삭제 없음. prod14 cleanup3flag+webhookON/host지정, desired1 재기동 요청. 최종기동/TEST는 확인 중. 기존dirty보존·제품코드변경없음.

- 통합 적용 승인 후 ALB109 exact 운영 host/POST/webhook path→8083 추가, 기존규칙 불변 확인. RC 운영 전용 웹훅 whintgrd6cb7cb418 등록·기존 AWS Authorization 비표시 연결, 사용자가 HMAC ON. Save 비활성/키 미표시 확인, 키 보관 또는 직접 Rotate 요청. CloudShell getpass로 signing secret 필드만 기존 version 일치 검사 후 저장하도록 준비. 비밀값 출력 없음. prod13 최종COMPLETED/1/0·HEALTHY·양포트healthy 확인. 자동삭제 control 이관 및 ingress ON은 새 키 입력 후 통합 배포로 남음. 서버중지/DB쓰기/자동삭제 미실행.

- 후속 설치 확인 완료. 사용자 exact 승인으로 slack-alerts Secret 저장 및 prod execution-role에 해당 Secret만 읽기 허용. Slack probe 성공 후 prod13에 Slack/provider alerts/환불처리/LC발신 ON 배포 요청. URL/개인키/URI 출력 없음. 사용자 추가 요청으로 자동삭제·웹훅 최종 통합 적용 준비: 운영 DB validator·인덱스 exact match, cleanup0/control1 OPENv3 확인(collMod 불필요). RevenueCat 공식 HMAC 규격 일치 확인, Production only 양식 미등록 상태 준비. DB쓰기/공개경로/RC등록은 아직 없음. 제품코드 변경 없어 Gradle 미실행, 기존 dirty 보존.

- 브랜치 codex/billing-test-deployment. 사용자 운영Slack 보완 및 환불·자동삭제·웹훅 전체 진행 요청. 비공개 billing-alerts-prod 채널을 명시승인받아 생성, 본인1명 확인. 기존 테스트채널/앱 권한은 변경하지 않았다.
- Billing Alerts Prod 앱 생성양식에서 incoming-webhook1개·event0개만 준비하고 Create and Install 단계는 새credential/권한설치로 사용자에게 인계. 사용자의 완료회신 후 최신화면/스크린샷이 여전히 설치전임을 확인했다. 중복생성/권한승인/비밀값출력 없음.
- 이번 AWS/DB/서버/제품코드 변경 없음. 환불OFF/prod12 유지. WORKLOG/CURRENT_STATE만갱신, 코드무변경으로Gradle미실행. 다음은 해당화면의 직접설치완료 확인·운영Webhook Secret 안전저장/최소읽기권한·환불ON, 이후 승인된 자동삭제DB이관/웹훅정합성이다. snapshot/feed·실제Store환불 제외, 기존dirty보존.

## 2026-10-18 — 운영 주소 SANDBOX 구매 지원 영향 검토

- 브랜치 codex/billing-test-deployment. 목표: 실제 과금 없이 운영 주소에서 테스트 구매 지원. Jira 미지정.
- 변경: PLAN-production-sandbox-purchase-support.md 신규, CURRENT_STATE와 본 기록. 구매 core/조회/예약/provider runtime의 단일 환경 의존성과 workload 매핑을 정적 확인했다.
- 결론: 웹훅 환경 허용만으로 완료 불가. 전용 테스트·심사 계정과 구매 전 환경 선택 규칙을 먼저 명시한다. 승인된 환경분리 계약의 제한 확장 영향 및 단계적 구현·이관 확인 항목을 보고했다.
- 제품코드·운영설정·DB·외부서비스·LC/Identity·commit/push 무변경. 기존 dirty 보존. 실제 거래 환경 덮어쓰기·인증완화·전체사용자 허용 없음.
- 검증: 소스 정적 확인 및 git diff --check. 코드 변경이 없어 Gradle 미실행, 실제 구매 E2E 미실행. 다음: 전용 계정 범위 확인 후 ADR·환경 선택/저장된 source 유지 설계와 OFF 구현. 신규 지원 완료·활성화로 기록하지 않는다.

## 2026-10-18 — 운영 SANDBOX 구매 통합 구현

- 브랜치: codex/billing-test-deployment. Jira 미지정. 사용자 최신 승인: 테스트도 실제 구매처럼 동일 계정/이용권에 합산하고 환경 표시는 거래 식별용으로만 유지한다. 앞선 allowlist/계정별 모드 제안은 폐기했다.
- 변경 파일: PaymentProperties/StoreEnvironmentPolicy/application.yml, provider decoder2/ReadClient/ReferenceStore/WebhookConfiguration, PurchaseCore/RefundCoreSchema/MixedStorePurchaseSchema/PaidEntitlementReader, PaymentSyncService/Schema/SyncTransactionKeys, AccountLookupWorker/LookupResumer/ProviderWorkerConfiguration/SourceWorker, provider 테스트2/PaymentSyncIntegrationTest/PaidReservationIntegrationTest. ADR-004·최초계획 상단·신규 billing-production-sandbox-purchases 안내·CURRENT_STATE/본기록 동기화. 상세 파일 그룹은 안내서 §6.
- 동작: 기본OFF인 PRODUCTION 전용 sandbox-purchases-enabled. 운영 account/workload scope 불변, 검증된 Store 환경별 HMAC으로 거래/event 식별, 공통 timeline 합산 및 기존 유료 예약/confirm 재사용. 별도 계정 관리/테스트 entitlement/관리화면·의존성 추가 없음. 신규 구매 필드 storeEnvironment는 실제 다른 Store 환경만 기록하며 legacy는 기존 environment로 읽는다.
- sync 원문/외부 wire 불변, 내부 SANDBOX 후보 HMAC만 추가. 같은 문자열의 두 환경 구매가 있으면 임의 선택하지 않고 기존409 PURCHASE_BINDING_CONFLICT. 기존 미귀속 PENDING의 동일 요청 재호출은 payloadDigest/생성시각/시도수 보존하며 후보 보완. 실제·테스트 거래가 별도로 지급돼도 중복 observation으로 재지급하지 않는다.
- REST 조회는 기존 tick/page budget으로 production→sandbox 순차 처리. cleanup 부재 확인도 양쪽 완료 필요, 한쪽실패는UNKNOWN. 구 cursor drain/명시 검토 없이 새 모드로 전환하지 않는다. mixed cursor 접두사 포함255자 한도 유지(초과시보류). 구매·탈퇴·원장 보존/인증/lease/CAS 유지.
- 스키마: purchases r8 optional storeEnvironment, sync optional sandboxTransactionKey 명시 maintenance 함수. exact old/target schema와 데이터 검증·ALL_BLOCKED/격리 필수. 인덱스/TTL/기존자료 backfill 없음, runtime 자동DDL 없음. 실제DB 이관 미실행.
- 예상 범위 내 추가 보완: 혼합 환불 회귀가 기존 PaidEntitlementReader의 취소 slot/timelineEnd 검증 오류를 드러냈다. audit slot은 보존하고 유효 slot만 현재 timelineEnd와 대조하도록 변경, 단일 환경 환불 회귀 추가. 무관한 파일 변경 없음; 기존 운영준비 문서/ops bootstrap dirty는 수정하지 않고 보존.
- 검증: 최초 전체1843에서 새 테스트1 실패(구매시각 기준 기대값), 수정 후 선택101에서 환불후 reader 오류1 확인·제품수정 및 선택101통과. 최종 `./gradlew clean test` **1849 tests, 실패0, 오류0, skip0**, 4분8초. git diff --check 통과. 로컬 Testcontainers 사용, 실제 AWS/Store/RevenueCat/Atlas/LC/Identity 호출 없음.
- 운영 설정·배포·외부 메시지·Jira·commit/push·LC/Identity 코드 변경 없음. 새flag OFF 유지, 실제 Store E2E는 미실행. 다음: 별도승인 후 구lookup cursor drain·두validator 이관·RevenueCat 실제app 양환경 수신·ON배포 및 프론트 테스트. 실제 Store 환불 실행·snapshot/feed 범위는 확장하지 않는다.

## 2026-10-18 — 운영 SANDBOX 지원 배포·스키마 이관

- 브랜치 codex/billing-test-deployment, Jira 미지정. 사용자 운영 적용 및 두 컬렉션 한정 collMod·중지·이관·회수 승인에 따라 진행. LC/Identity·기존 dirty 보존, commit/push 없음.
- 변경 파일: billing-production-sandbox-purchases 안내 §7·CURRENT_STATE·본 WORKLOG. 제품 소스 추가 변경 없음. 검증된 JAR·이미지와 임시 컴파일 migration runner로 기존 두 명시 migration 함수만 실행했다.
- 사전 prod14 COMPLETED/1/0, control schema4 OPENv106, cursor3/중간cursor0, collMod없음. 임시 billing-prod-mixed-store-maintenance는 운영 purchases/payment_sync_commands의 collMod만 부여하고 기존 readWrite 유지. 서버 desired0과 실제 STOPPED 확인 전에는 이관하지 않았다. ALB300초 종료 대기 중 사전 검사 두 번 중단, DB 변경 없음.
- 최종 중지 후 control OPEN142→ALL_BLOCKED143 CAS·epoch보존, MixedStorePurchaseSchema/PaymentSyncSchema migration 및 verify 성공. 인덱스·TTL·기존 구매 원문/원장 변경 없음. 임시 역할 연결 회수 저장 후 실제 connectionStatus에서 collMod없음 재확인, OPEN144 CAS 복원.
- bootJar 성공, workload 이미지 valid/wrong-db/wrong-cert smoke3 성공, diff 검사 통과. 이전 전체1849 통과 결과 유지(이번 제품 변경 없음, 전체 재실행 없음). JAR/TAR 양측 hash 일치 및 ECR digest는 안내 §7에 기록.
- prod15는 기존 prod14 설정·Secret·권한·network를 보존하고 이미지와 BILLING_PAYMENT_SANDBOX_PURCHASES_ENABLED=true만 변경. 새 task RUNNING/HEALTHY·8083/8443 target healthy, products 무인증 HTTPS401. 배포 중 구 정의 task가 잠시 재생성됐으나 실행 task는 prod15만 남음; 구 target draining 완료는 최종 확인 중.
- RevenueCat billing-production을 Both Production and Sandbox로 저장, HMAC/Authorization/앱 제한 유지. 테스트 webhook/LC/Identity/실제 Store 환불 실행/snapshot-feed 변경 없음. 거래 환경별 HMAC·원장/환불 분리·공통 timeline 합산 계약 유지. 별도 데이터 삭제 없음, CloudShell 임시 이동 아카이브 원위치 복원.
- 위험/미확인: 실제 Store SANDBOX 구매→sync→이용권→시험 생성 및 환불 E2E는 프론트 후속. 설정 성공을 구매 검증 성공으로 기록하지 않는다. mixed 자료 생성 후 구버전 rollback 금지. 다음은 ECS COMPLETED 확인 및 프론트 Store 테스트다.

### 같은 작업 최종 확인

- prod15 단일 PRIMARY/COMPLETED·desired1/running1/pending0·HEALTHY, 8083/8443 각각 healthy1·구 target 없음 확인. SANDBOX flag true 및 배포 image digest 대조 완료. 무인증 webhook401 추가 확인.
- 운영 적용 완료. 기존 데이터 삭제·보존기간/인덱스 변경 없음, 미연결 임시 역할 정의는 보존하고 사용자 collMod 권한은 회수됨. RevenueCat 양환경 설정 저장 증빙 확보. 다음은 프론트 실제 Store 구매/환불 E2E이며 이번에는 실제 구매·환불을 실행하지 않았다.

## 2026-10-18 — 결제 직후 sync 이상 읽기 진단

- 브랜치 codex/billing-test-deployment, Jira 미지정. 사용자 방금 운영 결제 후 sync/조회 이상 보고. 읽기만 수행, 코드·DB·서버·RevenueCat 설정·Jira·commit/push 변경 없음.
- 변경 문서는 billing-prod-sync-diagnosis-2026-10-18.md/CURRENT_STATE/본기록. 최근 sync·계정별 구매·entitlement·case·source·cursor를 비식별로 대조했다.
- 최신17:07 iOS sync PENDING/source미귀속, 과거16:39 구매는 VERIFIED/READY와1일권 존재하지만 최신거래와 다름. 동일계정 quantity review가 기존유효권 조회까지 unknown으로 만드는 코드 확인. 이전 동일거래 sync도 PENDING이며 old replay는 candidate 보완 후 settled 재대조 없이 반환. 계정 lookup attempts9/17:33:11 예정.
- Android17:04 sync APPLIED·SANDBOX1일권 확인, quantity case도 있어 sync/조회 성공 구분. 실제 quantity 필드 값/누락은 원문 미조회로 확정하지 않음.
- 보완 제안: 불완전 구매증거의 REST 수렴과 기존권리 차단 범위 분리, 기존 PENDING의 검증구매 수렴, 새sync 처리기회와 과거backoff 분리(요청한도/Retry-After 보호). 구현/배포/기존자료 복구는 미수행. 추가결제·강제지급·case삭제 없음.
- Gradle 미실행(진단·문서만), 기존 dirty 보존. 다음은 승인된 범위의 보완 구현·회귀·배포 및 실제거래 기반 재확인이다.

## 2026-10-18 — 수량 검토·기존권리 조회·sync replay 로컬 수정

- 브랜치 codex/billing-test-deployment, Jira 미지정. 사용자 「일단 수정해줘」에 따른 Billing 로컬 수정. 기존 dirty 전부 보존, 이번 범위 밖 추가 수정 없음.
- 변경 파일: RevenueCatWebhookDecoder.java, PaidEntitlementReader.java, PaymentSyncService.java, RevenueCatWebhookTest.java, PaymentSyncIntegrationTest.java 및 진단 runbook/CURRENT_STATE/본기록.
- quantity 누락/null은 QUANTITY_REQUIRES_LOOKUP으로 구분하고 명시적 1 이외 수량은 QUANTITY_UNSUPPORTED 유지. 둘 다 직접 지급 없음, 기존 REST 재조회 검증 유지. 새 worker/별도 상태 저장소·schema/의존성 없음.
- null observation의 수량 case만 기존 검증된 ACTIVE 권리를 가리지 않게 변경. 권리 없음은 PENDING, 환불·소유권 불명·다른 case 차단 유지. 과거 case와 pendingEvidence/삭제보호는 보존.
- unbound PENDING same-key replay는 기존 worker 검증구매 완료 함수를 같은 Tx에서 재사용. payload/거래환경/계정/원장/READY/관련검토 확인, command만 APPLIED·pending 1회 감소. bound/review 자동재개·재지급·기간연장 없음.
- 검증: 최초 targeted 169건 중168통과/1실패는 신규 테스트가 기존 원장 2건을1건으로 가정한 기대값 오류. 지급 전후 건수 불변 검사로 보완했으며 최종 전체 clean test 실행 중. 누락/명시2 구분, concurrent replay, bound command 보존, 정상권리/환불 차단, 엄격한 REST 수렴 회귀 추가. diff 검사 통과.
- public API·인증·기간권·환불·원장·멱등성 계약 유지. 운영/실제 거래/Secret/DB·LC/Identity·Jira·commit/push 변경 없음.
- 위험·다음: 미검증 새거래의 기존 계정 backoff 지연은 그대로다. 기존 수량 case를 자동종결하지 않는다. 배포 전 전체 회귀 결과 확인, 배포 후 기존 key 재요청·실제 최신거래 적용 확인 필요. 이 코드 수정은 운영 구매 성공/E2E 완료가 아니다.

### 최종 회귀 결과

- `./gradlew clean test` BUILD SUCCESSFUL, XML 합계1,858건/통과1,858/실패0/오류0/skip0. 실행4분4초. 신규9건 포함, 최초 테스트 기대값 오류 해소. `git diff --check` 통과.
- 기존 dirty와 이번 변경 범위 대조 완료, 예상 밖 추가 변경 없음. 운영 배포·기존자료 강제 변경 없음. 다음은 별도 운영 적용과 프론트의 같은 key 재요청/최신 결제 확인이다.

## 2026-10-18 — sync 보완 운영 배포

- 브랜치 codex/billing-test-deployment, Jira 미지정. 사용자 「어 배포해줘」 승인으로 Billing 운영 이미지 교체만 수행. 변경 기록: 진단 runbook/CURRENT_STATE/본기록. 제품 코드 추가 변경·commit/push 없음, 기존 dirty 보존.
- 이전 prod15 정상 확인 → bootJar·이미지 smoke3 성공 → TAR 양측 hash 일치 → ECR sync-fix-20261018 전달 → 컨테이너 image 외 동일 대조 → prod16 rolling 배포. digest/hash는 진단 runbook §6 참조.
- prod16 단일PRIMARY/COMPLETED·desired1/running1/pending0·RUNNING/HEALTHY, 실행digest 일치·신규양포트healthy. 기동표본38건 Started/ERROR0. 기존target draining은 AWS의 연결정리 잔여이며 ECS 완료와 구분했다. 첫검사 assertion은 draining잔여 조건에서 발생, 재대조 정상.
- HTTPS 상품/이용권/sync 무인증401 확인. 직전 전체1858/실패0/오류0/skip0 결과 유지, 이번 제품코드 무변경으로 전체 재실행 없음. diff 검사 통과.
- Secret/기능flag/네트워크/DB·schema/control·LC/Identity·RevenueCat 설정 변경 없음. 구매·case·sync 강제변경/추가결제 없음. CloudShell 공간 확보용 기존아카이브 임시이동 후 원위치 복원, 삭제 없음.
- public sync 응답형식·인증·원장/환불/멱등성 유지. 예상 밖 변경 없음. 남은 확인: 프론트의 기존 same-key sync 재요청·실제 최신거래 검증 및 이용권 조회. 미검증 거래의 기존backoff는 그대로이며 배포성공을 구매E2E 성공으로 기록하지 않는다.

## 2026-10-18 — 운영 SANDBOX 결제 실제자료 읽기 확인

- 브랜치 codex/billing-test-deployment, Jira 미지정. 사용자 정상반영 확인 및 SANDBOX 지원/결제 점검 요청. 변경 파일은 진단runbook/CURRENT_STATE/본기록뿐, 기존 dirty 보존·예상 밖 수정 없음.
- prod16 COMPLETED/running1/pending0, 양포트healthy1·구draining 제거 확인. 운영PRODUCTION/SANDBOX허용·sync·구매계정ON, control4/OPEN.
- 최근sync6건(iOS3/Android3) APPLIED, SANDBOX VERIFIED/ALLOWED/READY 구매와 exact거래/앱/계정·purchaseId 일치. 구매별 entitlement1·PURCHASE_VERIFIED 원장1. 관련3계정 active1/pendingSync0·scheduled1/2/0, 겹침/공백0. 이전17:07 iOS는17:33완료, 신규17:35 Android는약5초완료 관측.
- quantity case는보존. rawIDs/Secret/거래원문 출력·기록없음. CloudShell 재기동 후 /tmp 임시pymongo 준비만 수행, 제품·설정·DB·배포·commit/push 변경없음.
- 기존 public API/인증/원장/환경구분 계약 유지. 문서diff검사 통과, Gradle 미실행(읽기확인/문서만). 신규Store결제·정상로그인API·시험생성·실PRODUCTION거래/환불E2E는 이번 검증 밖. 다음은 프론트 시험생성 확인이며 추가구매를 요구하지 않음.

## 2026-10-18 — 방금 iPhone 구매 적용 여부 읽기 확인

- 브랜치 codex/billing-test-deployment, Jira 미지정. 18:09 KST 운영DB 읽기만 수행. 최근 APP_STORE sync4건의 거래후보와 exact앱/계정 구매 및 entitlement를 대조했다.
- 최신18:05:16 구매는 sync18:05:19.683 접수/18:05:25.100 APPLIED, 18:05:40 구매는 sync18:05:43.557 접수/18:05:49.177 APPLIED. 두 구매 VERIFIED/ALLOWED/READY, 오류없음. 신규1일/3일은 기존권리 뒤10/21~10/22/10/22~10/25 16:39:50 KST로 저장돼 반영 대기가 아니다.
- 변경파일 CURRENT_STATE/본기록만. 실제 사용자·거래ID/Secret/원문 미기록. 제품코드·DB·설정·배포·commit/push 무변경, 기존 dirty 보존·예상 밖 수정없음. 문서 diff검사 통과, Gradle 미실행(읽기 진단만). 계약 불변, 앱 화면/인증된API응답은 직접 확인하지 않았으므로 표시문제 여부는 별도다.

## 2026-10-18 — 이용권 GET 로그 읽기 진단

- 18:14 KST 직전1시간 운영 CloudWatch 조회: 전체133 이벤트 중 entitlement_query68건 모두 READY. 마지막18:11:30 KST. 최근 표본 reason NONE. 코드상 정상200 경로이며 READY는 무료 benefit 집계로 paid ACTIVE를 증명하지 않는다.
- 응답body/paid.status/canTakeExam은 로그에 없고 ALB access log OFF. 특정 앱/계정 연결 및 인증 이전 거절을 이 로그로 확정할 수 없다. 프론트 상태코드·응답JSON·X-Trace-Id 대조 필요; 토큰 요청하지 않음.
- CURRENT_STATE/본기록만 변경. 제품·DB·설정·배포·Git 이력 무변경, Gradle 미실행(읽기 진단). 기존 변경 보존.

## 2026-10-18 — 사용자별 API 요청 제한 설명

- PaymentRateLimiter 및 세 controller 확인: 환경/route/사용자/고정 분 bucket. sync12, entitlement60, products60, purchase-account10이며 서로 횟수 공유 없음. 같은 사용자 기기·토큰은 공유, sync 멱등성 처리 전 제한하므로 same-key도 포함. 429에는 다음 분까지 Retry-After 제공.
- 문서2개만 갱신, 제품·서버·DB·Git 이력 무변경. 정적 확인으로 Gradle 미실행; 실제 앱의429 원인은 이 확인으로 확정하지 않음.

## 2026-10-18 — 최신 iOS canTakeExam null 읽기 진단

- 18:43 KST 최신 APP_STORE sync 계정에서 OWNER_UNRESOLVED case5 확인. 계정ACTIVE/회원증거있음/활성권리1 VERIFIED·ALLOWED·READY지만 reader의 unresolved 분기가 paid PENDING을 반환하여 admission RECONCILIATION_PENDING/null로 연결된다. 수량case2는 기존 예외 범위이며 이 원인과 구분.
- 예약guard 없음/WAITING_MEMBER_EVIDENCE0/대기환불inbox0. 운영 admission/memberEvidence/entitlementQuery 모두true. 최신sync18:42:53은 조회 당시PENDING. 실제 앱 trace 연결 및 provider owner/alias 불일치 상세는 미확인. 인증완화·case삭제·권리지급·설정변경 없음.
- CloudShell 재개 후 임시 pymongo 도구 복원, 읽기만 수행. 문서2개만 수정, 제품·DB·배포·Git 이력 무변경. Gradle 미실행(진단), 개인정보·비밀값 미출력.

## 2026-10-18 — 소유자 확인 대기 생성시각 확인

- 앞서 확인한18:42:53.976 APP_STORE sync로 계정을 고정하고 OWNER_UNRESOLVED5건 createdAt만 대조. KST18:05:19.198/18:05:43.066/18:10:20.281/18:11:13.650/18:42:53.605, 모두 REVIEW_REQUIRED. 해당 case 구매시각은 없어 실제 결제시각으로 해석하지 않음.
- 문서2개만 변경, 제품·DB·설정·배포·Git 이력 변경없음. Gradle 미실행(읽기 조회), 기존 변경 보존.

## 2026-10-18 — 소유자case 잔존 코드 원인

- PurchaseCore.receiveWebhook/complete, PaymentAccountLookupWorker.source, PaidEntitlementReader 대조. REVIEW 별도case 보존 후 다른 정상관측 완료가 기존case를 해소하지 않음. 정규화 없는case는 lookup의 거래키 매칭에서 제외되지만 reader의 계정단위 PENDING에는 포함됨. 정상구매와5건 exact 연결 및 provider별 최초 소유불일치 원인은 미확인으로 유지.
- 문서만 변경, 제품/DB/설정/배포 무변경, 정적조회로 테스트 미실행.

## 2026-10-18 — 정상권리 보호·구매 대기 exact 해결 구현 및 배포 준비

- 브랜치 codex/billing-test-deployment. 사용자 요청으로 PaidEntitlementReader/PurchaseCore/WebhookReceipt/RevenueCatWebhookDecoder/PaymentProperties/PaymentProviderAlerts 및 관련 테스트·application.yml 수정, ReviewPurchaseCandidate/PurchaseReviewResolutionSchema/offline manifest exporter와 runbook 추가. 기존 다른 변경은 보존.
- 독립 검증된 ACTIVE 권리는 정규화 없는 수량/소유case로 숨기지 않음. 권리 없음·만료·정규화 충돌·환불·회원/예약 보호 유지. 신규 후보는 검증 증거가 아니며 전체 거래 식별자 일치와 VERIFIED/ALLOWED/READY 구매·원장 확인 후 같은Tx로 해결. 원장·case 삭제/재지급 없음. 인증된 same-digest 재전달만 legacy 후보 보완 허용.
- 최종 ./gradlew clean test bootJar: 1,878개 통과, 실패/오류/skip0. linux/amd64 이미지 빌드 및 정상/잘못된 DB/인증서 smoke 통과. API 응답·인증·rate limit 계약 유지.
- 사용자 승인: 운영 payment_purchase_cases 한 컬렉션 임시 collMod·서비스 일시중지·선택필드 r9 이관·회수·재배포. 신규 한정 역할은 생성됐으나 아직 미할당, 운영 서버·DB는 변경 전. CloudShell 홈 용량 부족으로 업로드 실패; 기존 아카이브2개와 실패 업로드를 /tmp/billing-upload-space-20261018로 이동 보존하여 재업로드 진행. 이전 아카이브는 작업 후 영속 경로 복원 필요.
- 기존 OWNER_UNRESOLVED5건의 exact 연결은 미확인, 삭제/임의해결하지 않음. 다음은 이미지 해시/ECR 전달·schema 이관·권한회수·배포 안정화. 프론트 인증 응답/E2E는 미검증, commit/push 없음.

## 2026-10-18 — r9 이관·prod17 기동 오류와 타입 보정

- 이미지/manifest 수신 해시 검증·ECR 전달 완료. digest 5ee01e8371b646f6942c6c8b5dd39074316adb75c796603b00643cec7d42d9bd. 한 컬렉션 collMod 역할만 임시 연결, 서비스0/ALL_BLOCKED 확인 후 이관, case10·index 불변, 권한회수/OPEN 복원 및 prod17 배포.
- 최초 기동은 schema mismatch로 종료. exporter relaxed JSON이 case validator의 digestVersion/revision/version 숫자6곳의 BSON Long 타입을 int32로 손실했고 Python 동등비교가 숫자값만 비교해 놓쳤음. 잘못된 검증완료 주장을 교정하며 상세 장애를 사용자에게 알림. 기존 구매·case는 무변경.
- exporter Extended JSON/Java round-trip 검증 추가·컴파일/실행 성공; canonical manifest 해시 b9a9c9ad88f061b7bd76ee46239999fbc59fc3cc8b529e22f2cf4bec4e8dc76b. 동일 승인 컬렉션·권한 범위에서 서비스 중지/ALL_BLOCKED CAS 후 타입 보정, 6개 schema canonical 타입 일치·case10/index 불변 확인. 임시 권한 재회수 확인·OPEN CAS 복원, 동일 prod17 재기동 요청. 제품이미지 변경 없음.
- 이전 CloudShell 아카이브2개 원위치 복원·로컬과 해시 일치, 새 이미지 원본 로컬/ECR 보존. 파일 영구삭제 없음. 영향 계정 read-only: VERIFIED 활성권리1, 미정규화case7, 다른 차단case0, 환불inbox0, 최신sync APPLIED. 실제 사용자 응답은 미검증.

## 2026-10-18 — prod17 기동·양포트 health 복구 확인

- 타입 보정 뒤 동일 prod17 RUNNING/HEALTHY, Started=true/기동실패false/새로그 ERROR0. ALB 8083/8443 각각 healthy1 확인(기존30초/연속5회 health 설정 유지). 앱 HTTPS 무인증401, CloudShell의 workload 요청404는 발신IP 제한 경계로 정상서명 검증과 구분.
- task definition은 기존 대비 image+review-resolution=true만 변경. control OPEN·임시 collMod effective 권한 없음, 기존 readWrite2개만 유지. schema6개 타입 포함 exact, case10/index 보존. 기존5건은 원문 인증 재전달 없이는 추측 해결하지 않음.
- 제품코드 최종 전체테스트1,878통과 유지, 이후 변경은 exporter·문서이며 exporter 컴파일/Java round-trip 검증 성공. git diff --check 통과, 기존 사용자 변경 보존, commit/push·LC/Identity 변경 없음. 다음은 프론트 entitlement 재조회/시험 생성 및 향후 exact 거래case 자동해결 관측.

- 최종 ECS 상태 prod17 PRIMARY/COMPLETED·desired1/running1/pending0, 실행 태스크HEALTHY·양포트healthy1. 이전 deployment 제거 확인으로 배포 완료. 로컬 증거 `/private/tmp/billing-review-prod17-health-20261018.png`, 권한회수 `/private/tmp/billing-review-role-revoked-20261018.png`. 사용자 인증 응답·실제 자동해결 E2E는 미검증으로 유지.

## 2026-10-19 — 자동해결 실운영 확인 착수·로그인 대기

- 사용자 프론트 정상 동작 및 iPhone 반영 정상 확인을 접수. 구매 반영과 기존case RESOLVED는 별개이며, 요청한 자동해결 실운영 사례를 읽기 확인하려 함.
- 운영 AWS 탭이 로그아웃 상태라 재로그인 화면까지만 연결. 새 DB·로그 조회를 아직 수행하지 못했으며 자동해결 성공/실패를 추정하지 않음. 다음은 로그인 후 해결case·exact 거래·검증구매/원장 및 잔존case 집계 조회. 재전송·실구매·DB수정은 이번 확인에서 수행하지 않음.
- 변경파일 CURRENT_STATE/WORKLOG만, 기존 dirty 보존. 제품·설정·DB·배포·Git 이력 변경없음. Gradle 미실행(읽기 진단/문서만), 계약불변·예상 밖 변경없음.

## 2026-10-19 — 자동해결 운영 자료 읽기 확인

- 사용자 로그인 완료 후 AWS CloudShell 재연결, 임시 pymongo 도구 복원. 운영DB read-only 집계와 현재 task 설정 조회; 실제 사용자/거래 식별자·Secret·원문 미출력.
- prod17 COMPLETED/running1, REVIEW_RESOLUTION flag=true. payment_purchase_cases10건: QUANTITY_REQUIRES_LOOKUP2, QUANTITY_UNSUPPORTED3, OWNER_UNRESOLVED5. resolvedObservationKey0/reviewCandidate0, 모두 배포완료(2026-10-18 19:28 KST) 전 자료. 이후 신규case0·구매0·sync0.
- 따라서 실제 자동해결 성공 사례는 아직 없고 처리할 신규후보도 관측되지 않음. 단위/통합테스트 통과·기능ON과 운영E2E 완료를 구분. 기존5건은 과거 기록으로 남으며 사용자 확인의 iPhone 정상반영과 모순되지 않음. 추가구매/웹훅 재전달/DB수정 없이 확인 완료; 원본 인증 webhook 재전달은 별도 요청 시 수행.
- 변경파일 CURRENT_STATE/WORKLOG, 제품·서버설정·DB·배포·commit/push 무변경. Gradle 미실행(읽기 조회·문서만), git diff --check 통과. 기존 dirty 보존·예상 밖 변경없음.

## 2026-10-19 — 기존 RevenueCat webhook 재전달 경로 확인

- 브랜치 codex/billing-test-deployment. 사용자 승인에 따라 새 구매 없이 기존 운영 webhook 재전달로 자동해결 검증을 시도.
- billing-production 설정의 전송 이력과 해당 고객 Event Details의 운영 integration을 확인. 10/18 18:42 KST APP_STORE/SANDBOX NON_RENEWING_PURCHASE는 Sent, 최초 응답 HTTP200. 두 상세 화면에 원본 재전달 버튼이 확인되지 않아 실제 replay는 실행하지 못함. 화면의 Send test event를 원본 재전달로 대체하지 않고 인증/서명 우회·수동 DB해결도 하지 않음.
- 실제 자동해결/중복지급 방지 E2E는 미검증으로 유지. 다음은 공급자 지원 원본 재전달 방법 확인 또는 향후 정상 거래에서 검증된 구매와 대상 case의 exact 연결 관측. 이번 조사로 기존 case가 해결됐다고 기록하지 않음.
- 변경파일 CURRENT_STATE/WORKLOG만. 제품·서버·DB·설정·배포·commit/push 무변경, 기존 dirty 보존. Gradle 미실행(브라우저 읽기 조사와 문서만); 계약불변·이번 작업의 예상 밖 변경 없음.

## 2026-10-19 — 기존10건 RevenueCat 원본과 검증 구매 읽기 대조

- 브랜치 codex/billing-test-deployment. 사용자 요청은 과거case의 원본 연결 가능성 확인; CloudShell에 거래/구매계정 식별자를 비표시 입력으로 전달하는 읽기 대조를 추가 승인받음. 원본은 메모리에만 유지, DB쓰기/HTTP replay/신규구매 없음.
- RevenueCat billing-production 기존10개 NON_RENEWING_PURCHASE의 화면 원본을 확인. 운영 lookup key를 출력 없이 사용해 event HMAC으로 case10건, transaction HMAC으로 검증 purchase10건 연결. 모두 VERIFIED/ALLOWED/READY, 각 구매의 PURCHASE_VERIFIED ledger1. ownerRefId·상품·구매시각·Store환경·accountId 일치. current ACTIVE account/lifecycle과 case lifecycle 일치10/10 확인.
- 초기 점검의 purchase.lifecycleId 비교는 제품 코드의 비교 대상이 아니었음. PurchaseCore는 account.userId로 현재 lifecycle을 조회하므로 이 기준으로 재검증해10/10 확인. 초기 false를 실제 귀속 충돌로 해석하지 않음.
- dashboard 원본은 quantity누락·family false·current/original owner동일. case 사유는 소유5·수량누락2·구 수량미지원3. dashboard 재직렬화 SHA256은 저장 receptionDigest와 불일치하여 인증 same-digest replay로 사용할 수 없음. 표시/숫자 직렬화 등 원인 미확정이며 원문 변조나 실제 거래 충돌로 단정하지 않음.
- 비표시 입력4096자 제한으로 첫 입력 JSON 파싱 실패 후 압축 비표시 입력으로 성공. 연결 일시 단절은 페이지 새로고침 후 복구. 원문·키·계정/거래ID는 문서와 결과에 미기록.
- 결론: 검증 구매와 연결 가능10건, 현재 자동해결 재전달 근거는 미완료. 현재 승인한 읽기 범위에서 RESOLVED/카운터 변경 없음. 후속은 과거10건 한정 종결 및 감사근거 처리 설계/구현; 정상 구매/기간은 변경하지 않으며 구 QUANTITY_UNSUPPORTED를 일반 자동허용으로 확대하지 않음.
- 변경파일 CURRENT_STATE/WORKLOG만. 제품·DB·배포·commit/push 변경없음. Gradle 미실행(읽기 점검), 기존 dirty 보존. 신규 제품 동작/계약 변경 없음.

## 2026-10-19 — 과거 검토10건 한정 종결 도구 구현

- 브랜치 codex/billing-test-deployment. 사용자 구현 요청/이어서 진행에 따라 scripts/ops/resolve_legacy_purchase_reviews.py와 test_resolve_legacy_purchase_reviews.py 및 운영안내 추가. CURRENT_STATE/WORKLOG 갱신.
- 기본dry-run, exact10개 고유 hash manifest·사유5/2/3·cutoff·현재 ACTIVE계정/lifecycle·VERIFIED/ALLOWED/READY구매·구매원장·fingerprint·기존case해시 검사. 운영apply는 명시 manifest SHA256/중지확인/ALL_BLOCKED 필요. 모든case 확인 후 하나의 majority/snapshot Tx에서 RESOLVED 감사필드/version·counted/pendingEvidence만 변경, 같은 감사 재실행 NOOP.
- dashboard hash 불일치에 대한 제한을 문서화: 기존 receptionDigest는 CAS전제이고 인증 replay 증명이 아니다. 원문/candidate 조작 없음, 구 수량미지원의 일반 자동허용 없음. 승인manifest를 실제 원본 대조 없이 생성하지 않는다.
- 로컬15 unittest 통과, git diff --check 통과. 최초 테스트 배치 추가 중 테스트변수 위치 오류1건 수정 후 재실행 통과. batch는 메모리 대역이며 실제 Mongo 트랜잭션 rollback/unknown commit 검증은 미완료. Java제품 코드 변경없어 Gradle 미실행.
- 운영manifest 생성·dry-run·실DB 적용은 미실행. 운영중지/DB쓰기/권한/schema/배포/commit/push 없음. 기존 dirty 변경은 보존, 이번 예상 밖 제품 변경 없음. 다음은 승인 대상 manifest와 실Mongo 검증 후 별도 운영 적용.

## 2026-10-19 — 종결 manifest/dry-run 착수·CloudShell 충돌 보류

- 브랜치 codex/billing-test-deployment. 사용자 후속 요청으로 실제 목록/운영dry-run 준비. 로컬15 unittest 재통과, git diff --check 통과.
- 기존 AWS CloudShell 탭 접근이 다른 작업의 브라우저 세션 귀속으로 거절됨. 해당 터미널의 명령/작업을 중단하거나 변경하지 않았으며 사용자에게 AWS 작업 종료 여부 확인 요청. 별도 탭도 동일 CloudShell 터미널에 연결될 수 있어 기존 터미널 임의 조작은 피함.
- 실제 manifest 생성·운영dry-run·DB쓰기·서버중지 미실행. 문서2개만 갱신, 제품/계약/배포/commit/push 변경없음. 기존 dirty 보존. 다음은 충돌 없는 Billing 전용 터미널에서10건 manifest 생성 및 읽기 전용 점검; 종결 적용은 별도.

## 2026-10-19 — 재시작 후 실제10건 manifest·운영 dry-run 완료

- 브랜치 codex/billing-test-deployment. 사용자의 재시작 후 재시도 요청으로 브라우저 연결 복구. Billing전용 CloudShell terminal 선택·환경 재기동 및 임시 pymongo 복원 후 진행. 다른 서비스 터미널/설정 변경 없음.
- RevenueCat 운영 webhook10개 원본 재조회, 비표시 압축 입력으로 메모리 대조. event/transaction HMAC과 owner/시각/상품/Store환경/계정 exact 재검사. 실제 도구 source를 메모리 실행하여 snapshot/majority 읽기Tx에서 run(apply=False) 수행: matched10/wouldResolve10.
- CloudShell `/home/cloudshell-user/billing-review-closure-20261019-cal5zt4e` 생성, manifest.json/manifest.sha256/dry-run.json 보존. dir0700/file0600 지정, 원문·Secret·개인/거래ID는 미저장. actorDigest는 실제 실행 AWS principal ARN의 SHA256. 승인manifest를 임의 새 case로 확대하지 않음.
- DB쓰기0, prod17/running1 출력 확인. 실Mongo 읽기Tx는 통과했으나 쓰기Tx rollback/unknown commit·실제종결은 미실행. 운영중지/control/schema/배포/commit/push 없음.
- 변경파일 CURRENT_STATE/WORKLOG/종결runbook, 제품 무변경·기존 dirty 보존. diff 검사 수행. Gradle 미실행(읽기 점검/문서). 증거 screenshot `/private/tmp/billing-review-dryrun-20261019.png`. 다음은 별도 운영 적용 승인 후 writer중지·ALL_BLOCKED·동일manifest 종결 및 불변 검증.

## 2026-10-19 승인된 과거 구매 검토 10건 운영 종결·복구 완료

- 사용자 재시도 요청과 앞선 운영 적용 승인 범위로 실행. Billing prod17만 desired0, 기존 task STOPPED 확인 후 exact manifest 재검사 및 control OPEN→ALL_BLOCKED CAS 수행.
- 실Mongo snapshot/majority 쓰기Tx abort 후 wouldResolve10 유지 확인, 이어서 10건을 한 Tx로 RESOLVED commit. 같은 apply 재실행 resolved0 검증.
- purchases/subscription_entitlements/payment_ledger 전체 건수·digest 불변. case state/reason/receptionDigest 보존 및 counted=false, lifecycle.pendingEvidence 정확 감소 검증. 새 지급/기간 변경/삭제/schema 변경 없음.
- control OPEN 복원 후 같은 prod17 desired1 재시작. 최종 desired1/running1/pending0·COMPLETED, ALB8083/8443 healthy, 앱 HTTPS 무인증401 확인. Identity/LC 및 다른 CloudShell 터미널 변경 없음.
- 접근 제한된 기존 CloudShell 폴더에 apply-baseline/apply-result 기록. 원문·비밀값·거래/사용자ID는 출력/저장소에 기록하지 않음. 자동해결 신규 E2E나 원본 webhook replay가 아닌 독립 검증 기반 운영자 종결.
- 로컬15테스트 재통과 및 git diff --check 통과. 제품 코드 변경/Gradle 재실행/commit/push/새 이미지 배포 없음. 기존 dirty 변경 보존.

## 2026-10-19 운영 배포 변경 Git 반영·Jira 현행화

- 브랜치 codex/billing-test-deployment. 사용자가 최근 코드 commit/push와 Jira 정리를 명시 승인했다. 이번 범위에서 Git 직접 작업 예외를 적용하며 PR생성/병합·재배포·운영 DB 변경은 하지 않는다.
- 기존 미커밋 SANDBOX Store환경 지원·sync 수렴·정상권리 보호·review 해결/schema 및 운영 도구·배포/종결 기록을 보존하여 반영한다. 신규 제품 로직 수정은 하지 않았다. 원격 미전달 기존 commit 57d6639/3ca7d52도 포함한다.
- 다시 컴파일한 main class480개가 보관 배포 JAR의 해당 class와 전부 byte 일치. JAR SHA256 fd5e6080e3fee09409abc3f96612594cf6cbda0132b64aa5f8dbc74ecba9e2c0. 운영 이미지 digest 기록과 구분하며 새 이미지 빌드/배포 없음.
- `./gradlew test --rerun-tasks` BUILD SUCCESSFUL(4m32s), 별도 종결 도구15테스트 통과. diff 검사 및 변경48파일 자격증명 주요 패턴 검사에서 발견0(완전한 비밀정보 부재 증명은 아님).
- Jira 원문/완료조건과 상태 전환 목록을 확인했다. TMI-212/220/217/205는 구현/운영 적용 근거와 남은 검증을 기록해 검토 중, TMI-204/221/137은 진행 중으로 갱신했다. 실제 E2E 미검증을 완료로 닫지 않았다. Rovo 통합 검색403 후 알려진 이슈 직접 API는 정상 동작했다.
- 사용자 결정: 시험 삭제 검증은 앱 기능 제공 전으로 유예, 통합 RC budget 개발은 현재 후속에서 제외. 해당 문서와 Jira에 기록하고 기존 설정/서버별 제한 유지. 금융5년 연결정보 정리는 장기유예이며 관련 파일명의 TMI-212와 실제 RevenueCat 이슈 제목을 혼동하지 않는다.
- 변경파일: 기존 배포 변경 전체와 CURRENT_STATE/WORKLOG, 삭제 검증 절차·RC budget 예외 문서. 타저장소 무변경. 이번 작업은 최근 Billing 범위 정리이며 전체 프로젝트 Jira 감사나 모든 이슈 완료를 뜻하지 않는다.

## 2026-10-19 develop PR 병합 진행

- 사용자 요청으로 원격 최신 fetch, clean 작업트리와 develop 대비 기존4commit 확인. [PR31](https://github.com/Too-Much-I/app-back-end-billing/pull/31)을 develop 대상으로 생성·이 채팅에 연결했다.
- 최초 head6447a80에서 GitHub MERGEABLE/CLEAN 및 CodeRabbit SUCCESS 확인. GitHub workflow 파일 없음. 직전 전체1,878개/실패·오류·skip0 및 종결도구15개 통과, 이번 문서만 변경하여 전체 테스트 재실행 없음.
- 로컬 merge-tree는 sandbox Git쓰기 제한으로 미실행이며 충돌 없음 판단은 GitHub 결과를 근거로 한다. 이 문서 commit 이후 최종 head와 검사 상태를 확인하여 일반 merge를 실행한다. admin 우회·force push·브랜치 삭제·서버 재배포·운영 DB 작업은 하지 않는다.
- 변경파일 CURRENT_STATE/WORKLOG. API·인증·원장·보존 계약 불변. 최종 merged 시각/merge SHA는 PR 감사 기록과 최종 응답을 기준으로 한다. 미완료 E2E와 snapshot/feed 준비는 이번 병합으로 완료되지 않는다.

## 2026-10-19 main/prod·develop/test GitHub Actions 구현 준비

- PR31 develop 병합7aca4f0 확인 후 사용자 요청으로 main 운영 기준 및 브랜치별 자동배포 준비. 해당 작업트리를 origin/develop으로 fast-forward, 타저장소 workflow는 읽기 참고만 했다.
- `.github/workflows/deploy.yml`과 `docs/runbooks/github-actions-deployment.md` 추가. main/prod·develop/test exact 분기, 별도 OIDC 변수, clean test/bootJar·Python15/Node8·workload image smoke, immutable digest, live task 설정 보존 및 stale branch/동시배포 거절·최종 ECS/ALB/HTTPS401 확인 포함.
- GitHub 저장소 Actions Secret/변수/environment 목록에 기존 설정 없음. Billing 전용 AWS 배포 role의 존재/최소권한·trust는 추가 준비 필요. Identity/LC role 신뢰 확대·Secret 읽기·DB이관·기능ON·desiredCount 변경은 workflow에 넣지 않았다.
- YAML 파싱/6개 shell step bash -n 통과, Node8통과, diff 검사. 제품코드 변경 없어 직전1,878개 테스트를 재실행하지 않았으며 클라우드 workflow 실행/배포 통과로 주장하지 않는다.
- main 병합과 자동배포 활성화는 OIDC 역할/변수 연결 및 환경별 호환 확인 후 진행한다. 이번 실제 AWS/IAM/서버/DB 변경 없음. 권한 연결 전 PR 준비까지만 수행한다.

## 2026-10-19 Billing 자동배포 OIDC 연결 및 최초 실행

- 브랜치 codex/billing-test-deployment. 사용자 승인 범위에서 Billing test/prod 전용 GitHub OIDC 역할 2개와 AWS_TEST_ROLE_ARN/AWS_PROD_ROLE_ARN 변수를 등록했다. 환경별 exact 서비스/태스크 family·실행역할 PassRole 및 기존 Billing ECR만 허용한다. 다른 환경 PassRole와 Secret 원문 읽기 거절을 IAM simulate로 확인했다. Identity/LC 역할은 변경하지 않았다.
- PR32 develop 병합0d7a131, main 승격 PR33 draft 생성·첨부. Actions39199919048 첫 실행은 테스트/build 성공 후 AWS 인증 실패. GitHub OIDC 설정 API에서 immutable subject 활성화를 확인하여 이름-only trust를 동일 org/repo 고유ID 및 exact branch 형식으로 교정·재조회 일치 확인 후 재실행했다. 권한 확대나 wildcard 전환은 없다.
- 기존 test14/prod17 서비스 정상, 단일 billing container/X86_64, circuit breaker rollback 및 minimumHealthyPercent100 확인. CloudShell test DB 직접 읽기는 TLS handshake 실패하여 완료로 기록하지 않았고 네트워크/TLS 완화·DB 변경은 하지 않았다.
- 변경 파일: github-actions-deployment.md, CURRENT_STATE.md, WORKLOG.md. API·상품·인증·원장·보존 계약 불변. 예상 밖 제품코드 변경 없음. 최초 test 배포 확인 후 main 승격·prod 실행을 진행하며, 전체 자동배포 및 앱 E2E 완료와 구분한다.
- 후속 결과: 04:58:51 UTC Actions39199919048 attempt2 SUCCESS, test15 이미지/ECS/ALB/HTTPS401 검증 통과. PR33 MERGEABLE/CLEAN·deploy/CodeRabbit SUCCESS 확인 후 draft 해제·exact head0d7a131 일반 merge. 04:59:09 UTC main merge70dac8e 완료. 운영 자동배포 결과 확인 중이며 DB/schema/기능설정 변경 없음.
- prod18 배포 후 Actions39202204049 마지막 상태검사가 즉시 실패. 별도 AWS에서 desired/running1·pending0·단일 COMPLETED, 예상 digest23fe350e...175d2e2 일치, 8083/8443 healthy와 이전 target draining 확인. prod17 대비 image/등록metadata 외 전체 task definition 동일, 로컬 HTTPS401. 실제 서비스 배포는 정상이며 workflow SUCCESS와 구분한다.
- ECS stability waiter는 rolloutState COMPLETED 이전 반환 가능하므로 최종 검사 최대18회/10초 재확인 및 이미지 불일치 명시 오류 추가. 검사 조건/rollback 검출은 유지한다. YAML·전체 shell bash -n 및 모의 지연 성공/18회 timeout 각 통과. 제품/DB/기능스위치 변경 없으며 workflow 후속 배포 검증은 계속한다.

## 2026-10-19 test/prod 자동배포 최종 완료

- PR34 develop merge edbb558682487f277d65eac733310e3b48528c29. 테스트 실행39203646982는 05:31:35 UTC SUCCESS. 테스트 완료 전 PR35 승격은 안전검사에서 보류되어 우회하지 않고 성공을 기다렸다. 이후 PR35 main merge d8076de45886808b1678b4a6f227495b8da5e855 완료.
- 운영 실행39205063261은 05:50:14 UTC SUCCESS. 양측 전체 test/bootJar, Python15·Node8, OIDC, workload image valid/wrong-db/wrong-cert smoke, 배포 및 최종 예상image/ECS/ALB/HTTPS401 검사가 모두 통과했다. 첫 운영 실행39202204049 실패 기록은 보존하며 재실행 성공으로 구분한다.
- main에 최신 제품코드와 배포 workflow를 반영했고, 앞으로 develop push는 test, main push는 prod다. Secret/기능스위치/기존설정 유지, 자동 schema 이관 없음. Identity/LC 역할·서비스·데이터 무변경, 예상 밖 변경 없음. 기존 앱 구매/환불 E2E·별도 후속 항목은 자동배포 성공으로 완료 처리하지 않는다.
- 변경파일 CURRENT_STATE/WORKLOG/github-actions-deployment.md는 결과문서만이므로 diff 확인 후 [skip ci] 커밋·PR로 develop/main에 정리한다. 이번 결과기록을 위한 추가 제품 테스트는 하지 않으며 위 실제 두 실행을 근거로 한다.


## 2026-10-21 PLAN-014 초대 공유 보상 구현

- 사용자 「브랜치 파서 구현해줘」에 따라 `codex/invite-share-rewards`에서 구현. 최신 develop d137362 기준이며 기존 미커밋 변경은 `backup/pre-invite-alignment-20261021`/stash에 보존했다. 기존 ReserveService/통합테스트는 최신 develop과 byte 동일하여 재적용하지 않았다.
- 변경: domain/reward의 config/properties/controller/service/schema, 기존 PublicIngressFilter/PublicSecurityConfig/PublicApiWriter, PaymentRateLimiter route bucket, grant/ledger INVITE factory, application.yml 기본OFF, HTTP/config/Mongo 테스트3개, PLAN014/공개계약/운영안내/ADR/AGENTS 및 본 기록.
- 동작: MEMBER 전용 GET(read)/POST(purchase), single lowercase UUIDv4 key/body·query 없음, 구매이력 없이 purchase-account 준비, 단일 ClientSession의 quota≤3/공통sequence/grant/ledger/receipt. 실제 수신자·전송 추적 없음. 성공 replay는 원 snapshot이며 한도 검사보다 우선, current WITHDRAWN은 replay보다 우선. LIMIT_REACHED도 commit 후409. 기존 시험 guard current/pending 필드 불변.
- 저장: 독립 additive invite-share-reward-r1, counter/receipt unique·INVITE source partial unique·issuance owner 조회 index, 자동DDL/TTL 없음. terminal120일 이후 operator-only bounded 상세purge, counter/grant/ledger 보존. INVITE GRANTED ledger에 환경/owner/source 최소귀속을 추가해 orphan 증거로도0초기화 거절. 운영 개인정보 제거는 기존 lifecycle 정책과 함께 활성화 전 확인.
- 계약 유지: 기존 payment 오류·LC EXAM_PASS/wire·공동 fixture·paid-first·기존 복원 정책 불변. 초대권은 최초trial이 아니므로 paid 전환 복원 없음. OFF 중 기존 권리 조회·사용 유지.
- 선별 검증: 초대26개 + 기존 PaymentController11개 =37개 통과. 실제 replica-set에서20회동일key/한도경합·최종쓰기실패전체rollback·unknowncommit·120일경계·orphan/counter소실·withdrawn·reserve/confirm/replacement/cancel/expiry/paid-first/reader 확인. 동시경합503은 bounded transaction 소진 후 원 key backoff 재시도로 수렴하도록 검사했다. 초기 unknowncommit 장애주입의 errorCode91은 서버종료 의미까지 섞여 타임아웃이 발생했으므로 기존 검증 패턴 errorCode1+UnknownTransactionCommitResult로 범위를 맞췄다.
- 전체 clean test 진행 중. 기본 기존 deprecated/unchecked 경고는 유지한다. 새 테스트는 실제 Atlas/AWS/RevenueCat/Identity/LC를 호출하지 않았다.
- 다음: 전체검증 결과 확인, 앱 공유 activity/component·동일key 영속화/계정전환 실기기 검증, 운영 schema/readiness/ALB allowlist 및 배포·활성화. 앱/Identity/LC 코드, 운영DB/설정, Jira, 외부회신, commit/push/merge 변경 없음.

- 전체 1차 실행은1,904개 중 기존 SecurityConfigTest6개가 새 InviteRewardProperties mock 누락으로 context 생성 실패했다. 전용 초대26개는 통과했다. 기존 보안 테스트에 설정 mock1개만 추가한 선별6개는 통과했고 최종 전체 clean test를 재실행한다. 제품 동작을 바꿔 실패를 우회하지 않았다.

- 최종 `./gradlew clean test` 성공(4m32s). JUnit XML 합계1,904개, 실패0/오류0/skip0. 초대 전용26개 포함(합산하지 않음). 전체 실행은 새 SecurityConfigTest mock 보완까지 포함한다. `git diff --check`와 신규 문서 상대링크 검사 통과. 기존 deprecated/VM 경고 외 미해결 테스트 실패 없음. 최종 브랜치 `codex/invite-share-rewards`, commit/push/merge/배포 없음.
