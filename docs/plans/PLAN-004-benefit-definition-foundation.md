# PLAN-004: BenefitDefinition foundation

- 상태: 구현 완료·Jira 완료 승인 대기
- 작성일: 2026-08-28
- 대상 저장소: `app-back-end-billing`
- Jira: `TMI-115` — `[Billing] BenefitDefinition foundation 구현` (`해야 할 일`, 담당자 미지정)
- 선행 작업: `PLAN-003` 구현 완료, `TMI-113` 완료
- 관련 계약: `docs/codex/CONTRACT_DECISIONS.md` 1C, `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`, `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`

## 1. 목표

현재 Claim·candidate alias·Grant와 repository에 하드코딩된 `FREE_EXAM_ONCE`를 versioned `BenefitDefinition` catalog 참조로 바꾼다.

```text
BenefitDefinition(FREE_EXAM_ONCE)
        ├─ TrialClaim
        ├─ TrialCandidateAlias
        └─ EntitlementGrant
```

이 작업은 무료권의 지급·hold·consume 정책을 변경하지 않는다. 공통 혜택의 이름과 정책 metadata를 한 document에서 관리하고, 사용자별 record는 stable `benefitCode`만 참조하도록 만드는 foundation이다.

```text
Identity verified event
→ TrialEligibility만 반영

최초 reserve
→ FREE_EXAM_ONCE definition 확인
→ TrialClaim·Grant lazy 생성
→ grant hold
→ 기존 Reservation lifecycle 계속 사용
```

## 2. 범위

### 2.1 포함

- `benefit_definitions` collection
- 현재 유일한 `FREE_EXAM_ONCE` definition
- stable `benefitCode` 형식과 code constant
- unit 기반 one-time benefit metadata
- versioned catalog seed와 exact policy drift fail-fast
- Claim·candidate alias·Grant의 `benefitCode` reference
- 기존 `benefitType`·`grantType` 하드코딩 정리
- BenefitDefinition을 확인한 뒤 무료 Claim/Grant를 발급하는 reserve 경계
- Mongo schema version과 index initializer 갱신
- ADR-001 collection/index field 계약 보정
- initializer idempotency·drift와 기존 reserve/lifecycle 전체 회귀 테스트

### 2.2 제외

- `PREMIUM_SUBSCRIPTION` 실제 definition seed
- `SubscriptionEntitlement`
- App Store·Google Play 상품, 가격, receipt/notification 검증
- renewal, cancel, refund, expiry와 grace period
- 구독과 무료권의 resolver 우선순위
- Reservation의 GRANT/SUBSCRIPTION authorization source 확장
- TrialClaim을 verified event에서 미리 생성하는 eager issuance
- TrialClaim `claimedAt`·3년 retention 변경
- 앱이 호출하는 상품·사용권 조회 API
- coupon, 출석, 추천과 credit balance
- AttemptGroup 상태 event consumer와 owner rebind
- Identity·Learning Core 코드 또는 실제 AWS 배포 변경

구독은 다음 제품 업데이트에서 별도 계약과 vertical slice로 구현한다. 이 계획에 빈 Subscription entity나 미확정 Store field를 추가하지 않는다.

## 3. 현재 baseline과 문제

현재 구현에는 다음 기반이 있다.

- phone당 무료 1회 `TrialClaim`
- `FREE_EXAM_ONCE` total 1-unit `EntitlementGrant`
- append-only `GRANTED`, `RESERVED`, `RELEASED`, `CONSUMED` ledger
- allocation hold와 Reservation confirm/cancel/expiry
- AttemptGroup·AttemptSession projection
- versioned Mongo index initializer와 replica-set Transaction test

하지만 `FREE_EXAM_ONCE`가 다음 위치에 서로 다른 field 이름으로 고정돼 있다.

- `TrialClaim.benefitType`
- `TrialCandidateAlias.benefitType`
- `EntitlementGrant.grantType`
- alias/grant repository query
- index key와 factory method

이 상태에서 새 benefit을 추가하면 code·query·index 조건이 여러 곳으로 퍼진다. Definition이 존재하는지, 현재 policy version과 Grant 수량이 일치하는지도 한 곳에서 검증할 수 없다.

## 4. 도메인 책임

| aggregate | 책임 | 이번 작업 |
| --- | --- | --- |
| `BenefitDefinition` | 공통 혜택 종류·소비 정책 catalog | 신규 |
| `TrialEligibility` | 현재 phone verification projection | 변경 없음 |
| `TrialClaim` | phone candidate의 무료권 중복 수급 방지 | code reference만 정리 |
| `EntitlementGrant` | subject가 보유한 실제 one-time unit | code reference·definition 기반 생성 |
| `EntitlementLedger` | 지급·hold·release·consume 이력 | event 동작 변경 없음 |
| `Reservation` | 시험 시작 hold·멱등성 | 변경 없음 |
| `AttemptGroup` | 최초 사용 건·replacement 연결 | 변경 없음 |

BenefitDefinition은 사용자 소유권이나 소비 상태를 가지지 않는다. Claim·Grant·Reservation ID, userId, phone candidate와 balance를 catalog에 저장하지 않는다.

## 5. `benefit_definitions` 계약

### 5.1 document

```text
_id = benefitCode,
displayName,
entitlementModel,
unitType,
defaultGrantUnits,
policyVersion,
active,
createdAt
```

최초 definition은 다음 의미로 고정한다.

```text
benefitCode=FREE_EXAM_ONCE
displayName=무료 모의고사 응시권
entitlementModel=UNIT
unitType=EXAM_ATTEMPT
defaultGrantUnits=1
policyVersion=1
active=true
```

- `benefitCode`는 uppercase ASCII snake case 1~64자 stable identifier다.
- `benefitCode`를 Mongo `_id`로 사용해 built-in `_id_` unique index로 유일성을 보장한다. 같은 key의 중복 secondary unique index는 만들지 않는다.
- `displayName`은 운영·향후 조회용 표시 metadata이며 authorization 식별자로 사용하지 않는다.
- `entitlementModel=UNIT`은 수량형 one-time Grant임을 뜻한다. 이번 schema에는 subscription 값을 넣지 않는다.
- `unitType=EXAM_ATTEMPT`는 한 unit이 모의고사 최초 사용 건 하나임을 뜻한다.
- `defaultGrantUnits=1`은 무료 TrialClaim이 발급하는 최초 unit 수다.
- `policyVersion`은 definition의 해석 version이다. 기존 version document를 실행 중 덮어쓰지 않는다.
- `active=false`인 definition으로 새 Claim/Grant를 발급하지 않는다. 기존 audit document는 삭제하지 않는다.

### 5.2 불변식

1. `FREE_EXAM_ONCE`는 정확히 `UNIT`, `EXAM_ATTEMPT`, 1 unit, policy v1이다.
2. code·model·unit·version 불일치는 startup에서 fail-fast한다.
3. catalog seed는 user·candidate·Claim·Grant를 생성하지 않는다.
4. display name은 dedupe, authorization, ledger key에 사용하지 않는다.
5. 새로운 definition 등록만으로 resolver 동작이 자동으로 생기지 않는다. 지원 코드와 contract가 함께 배포돼야 한다.

## 6. 기존 reference 정리

현재 Billing은 미배포 상태이므로 schema v3에서 다음 field 이름을 일관되게 정리한다.

| collection | v2 | v3 |
| --- | --- | --- |
| `trial_claims` | `benefitType` | `benefitCode` |
| `trial_candidate_aliases` | `benefitType` | `benefitCode` |
| `entitlement_grants` | `grantType` | `benefitCode` |

- Java getter/factory/repository query도 `benefitCode` 용어로 통일한다.
- `TrialClaim`, alias와 Grant는 모두 `FREE_EXAM_ONCE` definition을 참조한다.
- ledger의 eventType, sourceType/sourceId, dedupeKey와 Reservation wire DTO는 변경하지 않는다.
- raw JSON/API response에는 benefitCode를 새로 노출하지 않는다.

기존 데이터가 있는 환경에서는 application startup 중 field rename이나 index drop/recreate를 하지 않는다. v2 document가 존재하면 별도 migration 또는 비운영 DB 재생성 후 v3를 활성화한다.

## 7. catalog seed와 startup validation

`BenefitCatalogInitializer`는 index/collection initializer와 명시적인 순서로 실행한다.

1. Mongo schema version이 v3인지 확인한다.
2. `benefit_definitions` collection 존재를 확인한다.
3. `FREE_EXAM_ONCE`가 없으면 승인된 seed를 insert한다.
4. 이미 있으면 code를 제외한 policy field를 exact compare한다.
5. 같은 code의 policy drift가 있으면 임의 update하지 않고 startup을 실패시킨다.
6. 재실행은 document를 추가하거나 timestamp를 갱신하지 않는 no-op이다.

production에서 catalog drift를 자동 수정하지 않는다. definition 변경은 새 policy version과 migration·배포 계획으로 처리한다.

local/test는 실제 운영 DB를 호출하지 않는다. replica-set Testcontainers 또는 test fixture에서 동일 initializer를 검증한다.

## 8. reserve 연동

최초 Claim/Grant가 필요한 INITIAL reserve에서 다음 순서를 유지한다.

1. current TrialEligibility VERIFIED와 candidate를 확인한다.
2. 기존 active/unexpired TrialClaim을 찾는다.
3. 새 Claim이 필요하면 `FREE_EXAM_ONCE` definition을 조회한다.
4. definition이 active이고 policy v1과 지원 model인지 확인한다.
5. Claim·aliases·subject link에 `benefitCode`를 기록한다.
6. `defaultGrantUnits=1`로 Grant를 생성하고 `GRANTED` ledger를 append한다.
7. 기존 로직대로 unit을 hold하고 Reservation을 만든다.

definition 누락·inactive·지원하지 않는 policy는 무료권 없음으로 위장하지 않는다. 이는 사용자 자격 부족이 아니라 Billing configuration drift이므로 fail-closed `503 BILLING_TEMPORARILY_UNAVAILABLE`와 식별자 없는 operational metric으로 처리한다.

기존 Claim을 찾은 경우에도 연결된 Grant의 benefitCode가 Claim·definition과 일치해야 한다. 불일치는 invariant violation으로 fail-closed하고 새 Claim/Grant를 만들지 않는다.

## 9. Mongo index와 schema version

- Billing Mongo schema version을 `2 → 3`으로 올린다.
- `benefit_definitions`를 versioned initializer 관리 collection에 추가한다.
- definition의 `benefitCode`는 `_id_` unique index를 사용한다.
- `ux_active_trial_candidate` key를 `{benefitCode, keyVersion, candidate}`로 변경하고 partial filter `{active:true}`를 유지한다.
- grant source unique index key를 `{sourceType, sourceId, benefitCode}`로 변경한다. 최종 index 이름은 ADR과 initializer에서 동일하게 고정한다.
- 기존 reservation, command, AttemptGroup, Session과 ledger index는 변경하지 않는다.
- 운영 중 index를 application이 임의 drop/recreate하지 않는다.

Billing은 아직 미배포이므로 최초 production은 v3 greenfield schema로 준비한다. 보존해야 할 v2 데이터가 발견되면 배포를 중단하고 field/index migration을 별도 승인한다.

## 10. package 구조

```text
web.tosunsaeng.billing.domain.benefit
├── application
│   └── BenefitCatalog
├── domain
│   └── entity
│       └── BenefitDefinition
├── repository
│   └── BenefitDefinitionRepository
└── config
    └── BenefitCatalogInitializer
```

- free trial 전용 Claim·alias는 `domain.entitlement.trial`에 유지한다.
- Grant·ledger는 `domain.entitlement`에 유지한다.
- Mongo 공통 collection/index orchestration은 `global.infrastructure.mongodb`에 유지한다.
- subscription package와 빈 interface/strategy는 만들지 않는다.

## 11. 테스트 계획

### 11.1 단위 테스트

- benefitCode 형식과 필수 policy field 검증
- FREE_EXAM_ONCE v1 exact policy 검증
- inactive/unsupported definition 발급 차단
- Claim·alias·Grant가 같은 benefitCode를 사용하는지 검증
- definition metadata가 DTO·log에 노출되지 않는지 검증

### 11.2 Mongo initializer 통합 테스트

- 빈 v3 DB에서 collection과 FREE_EXAM_ONCE seed 생성
- initializer 재실행 no-op
- 같은 code 중복 방지
- policy field drift 시 fail-fast
- v2/v3 schema mismatch 시 startup 거절
- 변경된 alias/grant unique index key·name·partial option exact 비교
- application이 기존 불일치 index를 drop/recreate하지 않는지 검증

### 11.3 reserve/lifecycle 회귀

- 최초 reserve가 definition 기반 Claim·Grant·GRANTED/RESERVED ledger를 생성
- same candidate concurrent reserve가 하나의 Claim/Grant로 수렴
- missing/inactive definition이 부분 Claim 없이 503으로 rollback
- cancel/expiry가 동일 Grant를 release
- confirm이 동일 Grant를 consume
- REPLACEMENT는 definition/Grant를 새로 만들거나 차감하지 않음
- 기존 strict decoder·idempotency·CAS·unknown commit 테스트 유지
- 최종 `./gradlew clean test` 전체 성공

## 12. 구현 순서

1. ADR-001에 BenefitDefinition collection·reference·schema v3 index 계약 반영
2. BenefitDefinition entity/repository/catalog validator 구현
3. versioned collection/index initializer와 seed 구현
4. Claim·alias·Grant field와 repository query를 benefitCode로 정리
5. ReserveService의 새 Claim/Grant 발급을 BenefitCatalog 기반으로 변경
6. initializer·reserve·transaction 회귀 테스트 추가
7. 전체 테스트, `git diff --check`와 금지 payload/log 검증
8. CURRENT_STATE·WORKLOG와 PLAN/Jira 상태 갱신

## 13. 완료 조건

- FREE_EXAM_ONCE 정책 metadata가 한 BenefitDefinition에서 관리된다.
- Claim·alias·Grant가 같은 stable benefitCode를 참조한다.
- definition 누락·inactive·drift에서 부분 entitlement를 만들지 않고 fail-closed한다.
- 기존 무료 reserve/confirm/cancel/expiry와 replacement wire 동작이 바뀌지 않는다.
- 새 subscription·Store·public API 코드가 포함되지 않는다.
- Mongo v3 collection/index/seed가 replica-set Testcontainers에서 검증된다.
- 전체 Gradle 테스트와 diff 검사가 성공한다.
- production caller는 기존 Learning Core saga·Lattice staging E2E gate 전까지 활성화하지 않는다.

## 14. 주요 위험과 대응

| 위험 | 대응 |
| --- | --- |
| catalog만 추가하고 하드코딩 분기가 남음 | Claim·alias·Grant·repository 검색과 test fixture를 benefitCode로 함께 정리 |
| displayName을 authorization key로 사용 | 모든 관계·query·ledger는 stable benefitCode만 사용 |
| seed drift를 runtime update | exact compare 후 fail-fast, 변경은 versioned migration |
| v2 field/index가 있는 DB에서 자동 변경 | startup migration 금지, preflight 후 별도 migration 또는 비운영 DB 재생성 |
| subscription schema 선구현 | PREMIUM_SUBSCRIPTION과 SubscriptionEntitlement를 명시적으로 제외 |
| definition lookup 장애가 402로 오인됨 | configuration drift는 503과 operational metric으로 분리 |
| catalog 도입이 지급 시점을 바꿈 | eligibility event는 projection만, Claim/Grant는 최초 reserve lazy 생성 유지 |

## 15. 후속 작업

PLAN-004 완료 후 원래 순서로 돌아간다.

```text
AttemptGroup 상태 event consumer
→ 재가입 owner rebind
→ Learning Core saga/outbox
→ Lattice staging E2E
```

구독은 다음 제품 업데이트에서 Store·기간·갱신 정책을 승인한 뒤 별도 PLAN으로 작성한다.
