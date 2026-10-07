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
