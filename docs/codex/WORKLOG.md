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
