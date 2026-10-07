# PLAN-007: 앱용 무료 사용권·이용 상태 조회

- 작성일: 2026-09-07
- 상태: 사용자 구현 승인 후 Billing 코드·로컬 회귀 검증 완료 / 실제 배포·Identity 연동 gate 대기
- 구현: command TTL 독립 귀속 증빙, 읽기 snapshot·상태 판정, 사용자 JWT·전용 public connector, 보수적 legacy migration을 반영했다. [배포·이관 안내](../runbooks/PLAN-007-public-reader-rollout.md), [OpenAPI](../openapi/free-entitlements.yaml).
- Jira: 미생성. 이번 구현에서 Jira·commit/push·외부 배포·Identity/LC 수정은 수행하지 않았다.
- 로컬 검증: `./gradlew clean test` 성공, 236개 테스트·실패/skip 0. 실제 AWS/Identity·운영 legacy coverage는 별도 gate다.
- 대상: Billing 무료 entitlement public reader. Identity는 계약 확인용 읽기만 수행했다.
- 선행: [ADR-001](../adr/ADR-001-free-trial-internal-api-and-mongo-contract.md), [ADR-002](../adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md), [ADR-003](../adr/ADR-003-retained-trial-owner-rebind-contract.md), [PLAN-006](PLAN-006-retained-trial-owner-rebind.md).
- 결제와의 관계: [ADR-004 초안](../adr/ADR-004-fixed-term-premium-payment-contract.md)은 후속 결제 트랙이다. 이 계획은 결제와 독립적으로 진행한다. 결제의 4주 상품·D1-A·D2-A는 2026-09-07 후속 승인됐으며 무료 reader 범위는 변경하지 않는다.

## 1. 5줄 결론

1. `GET /api/v1/entitlements`를 Identity 사용자 JWT와 `billing:read`로 호출하고, 검증된 `sub`의 무료 혜택만 조회한다. [API·JWT](#5-api와-정확한-응답-계약)
2. Grant가 없어도 VERIFIED candidate에 retained Claim이 없다면 새 무료 수량 1을 표시하되, 조회에서 Claim·Grant·Reservation을 생성하지 않는다. [판정표](#6-상황별-판정표)
3. 새 수량·진행·재응시는 별도 축이다. 새 수량 0이어도 기존 미완료 group의 replacement가 가능할 수 있다. [계산](#7-읽기-알고리즘과-reserve-정합성)
4. owner 이전 전에는 이전 계정의 group을 반환하지 않고, 연동 불명은 PENDING/null, 저장소 실패는 503으로 구분한다. [재가입](#8-동일-전화번호-재가입-처리)
5. Billing reader OFF 배포 → Identity Billing audience/scope 발급 → staging → reader 활성화 → 프론트 순서이며, public ingress와 internal Lattice는 분리한다. [배포](#11-feature-flag와-배포-순서)

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 이번에 구현할 것과 제외할 것

포함:

- 로그인 사용자 본인의 benefit별 새 무료 수량, 진행 중 여부, 기존 group 재응시 가능성, 사용 완료 의미 조회.
- 사용자 JWT 검증, 전용 public route와 응답/오류/캐시·관측 규격.
- 기존 정책을 재사용하는 순수 판정기와 일관된 읽기 snapshot, bounded batch repository 조회.
- 세션 귀속 조회의 선행 보완: 기존 reserve/confirm/owner rebind의 쓰기 Transaction에 최소 `sessionOwnerEpoch` 증빙을 남기고, legacy coverage를 조회 활성화 전에 검증한다(§8.2~8.5). GET 자체는 여전히 읽기 전용이다.
- public/internal ingress 분리와 feature flag, 단위·MVC·Mongo·인증 회귀 테스트.

제외:

- 결제·상품 구매·RevenueCat·paid entitlement 생성/조회 구현, paid-first resolver와 payment schema v5.
- Claim/Grant 사전 지급 및 GET에서 reserve/confirm/cancel 실행, 만료 복구·purge·alias 추가, owner 이전. 기존 쓰기 경로의 최소 귀속 증빙 보완은 §8 범위로만 포함한다.
- 앱의 시험 생성 API 변경, 기존 Session 이어풀기, 과거 답안·결과·owner 이전.
- 신규 무료 프로모션 지급 정책, coupon·credit, 전체 사용자 사용권 검색 API.
- Identity·Learning Core 저장소 수정, 외부 인프라 배포, Jira 생성·수정.

### 2.2 수량의 의미

`availableQuantity`는 **현재 무료 정책에 따라 새 INITIAL에 사용할 수 있는 미예약 수량**이다. 영구 발급 완료 수량이나 reserve 승인 보증이 아니다.

- 최초 reserve 전: 아직 저장된 Grant는 없어도 정책상 받을 수 있는 1개를 표시한다.
- reserve hold 후: 새 수량은 0. 아직 최종 무료시험 완료를 뜻하지 않는다.
- confirm 후: 새 수량은 0. OPEN/RETAKE_AVAILABLE에서는 추가 차감 없는 replacement 가능성이 남는다.
- COMPLETED 후: retained 기간 안에는 새 수량 0, 재응시 불가, 사용 완료로 표시한다.
- 판정 근거가 부족하면 수량은 null. 저장소 장애면 응답 자체가 실패하며 0으로 대체하지 않는다.

프론트는 `availableQuantity > 0`만으로 시험 버튼을 켜지 않는다. `newAttempt`와 `retake`를 각각 확인하고 실제 시작은 기존 Learning Core 흐름과 Billing reserve가 최종 판단한다.

### 2.3 계획 작성 당시 조사 사실 (아래 미구현 서술은 구현 전 기준)

- 확인된 코드: `ReserveService.resolveClaim`은 VERIFIED candidate·retained alias·Claim·current owner를 검사한 뒤 최초 지급한다. 이 메서드는 alias 비활성화·추가·Grant 생성도 하므로 조회에서 직접 호출할 수 없다.
- 확인된 코드: confirm에서 held→consumed와 AttemptGroup OPEN이 연결된다. consumed=1을 모의고사 완료로 표시하면 안 된다.
- 확인된 코드: phone owner rebind는 OPEN/RETAKE_AVAILABLE 이전, GRADING pending, COMPLETED NOOP다. 이전 Session·답안·결과 owner를 이전하는 계약이 아니다.
- 확인된 코드: ReservationProperties.terminalCommandRetention 기본값은 7일이며 confirm은 reserve/confirm command 모두에 purgeAt을 설정한다. Reservation/AttemptSession에는 userId·생성 당시 owner epoch가 없다. command는 장기 세션 귀속의 유일한 근거가 될 수 없다.
- 확인된 코드: Billing SecurityConfig는 내부 workload 경계를 전제로 하며 public 사용자 JWT reader가 아직 없다.
- 사용자 제공 사실 및 Identity 로컬 문서: account_type PR #39 병합. 실제 운영 배포는 미확인이다. 로컬 JwtAccessTokenIssuer는 현재 단일 설정 audience를 발급하며 Billing audience·billing:read가 이미 적용됐다고 볼 수 없다.

## 3. 사용자 결정사항과 이 계획의 선택

요청 범위 안에서 다음 기술 선택을 이 계획의 구현 기준으로 고정한다. 기존 무료 지급·차감·재가입 정책을 새로 선택할 필요는 없다.

| 항목 | 선택 |
| --- | --- |
| URL | GET `/api/v1/entitlements`, path/query/body의 조회 대상 지정 없음 |
| 사용자 인증 | Identity RS256 JWT, Billing audience, billing:read, sub; Guest/MEMBER 동일 read 권한 |
| wrapper | public 전용 `isSuccess/code/message/result` envelope, internal에는 적용 안 함 |
| 수량 불명 | null + PENDING; 추측 0/1 금지 |
| 정상 연동 대기 | 200 + result.status=PENDING + Retry-After: 5 |
| 저장소/불변식 오류 | 503 실패 envelope, result=null |
| multi-benefit/group | benefits 배열과 benefit별 attemptGroups 배열; 단위 혼합 총합 없음 |
| 조회 일관성 | 동일 Mongo read-only snapshot, 주 저장소 읽기, 조회에서 업무 write 금지 |
| paid reader | 현재 무료 전용. 기존 `/api/v1/payments/entitlement` 계획은 별도 유지, 자동 합치거나 구현하지 않음 |

실제 hostname·issuer/JWKS·port·ALB/SG·Identity audience 발급 배포 증거는 staging 전 필요하다. 이는 기술 검증·환경 입력이며 무료 조회 제품 정책의 재승인이 아니다.

## 4. 주요 위험과 미확인 사항

### 4.1 이벤트 지연을 항상 알아낼 수는 없다

Billing은 아직 수신하지 않은 Identity/LC event를 볼 수 없다. JWT에는 phone eligibility revision이나 owner rebind 완료 증명이 없고, 현재 owner inbox에는 user별 처리 대기 목록이 없다. **projection이 없다는 이유로 미인증/미사용이라고 확정하거나, 오래된 updatedAt만으로 최신/지연을 단정하지 않는다.**

- projection 없음: GUEST/MEMBER 모두 PENDING, reason=ELIGIBILITY_UNKNOWN. MEMBER만으로 VERIFIED를 추론하지 않는다.
- 명시적 REVOKED projection: 현재 알려진 자격은 부적격. 이후 미수신 재인증이 있는지는 알 수 없음을 LOCAL_PROJECTION 계약으로 명시한다.
- current candidate가 다른 owner의 retained Claim에 묶임: 새 지급은 막고, 이전 완료를 확인할 수 없으면 PENDING/OWNER_LINK_UNRESOLVED. 이를 '승인된 이전 작업이 실제 실행 중'이라고 단정하지 않는다.
- 내부에서 이미 불변식 위반을 확인했다면 pending 대신 503과 경보로 분리한다.
- 기존 정상 projection 이후 아직 관측되지 않은 revoke/완료까지 정확히 감지해야 한다면 별도 trusted revision barrier 계약이 필요하다. 이번 계획에서 wire field를 몰래 추가하거나 Identity를 동기 호출하지 않는다.

따라서 이 API는 조회 시점에 **Billing에 반영된 상태**를 제공한다. snapshot은 여러 collection의 섞인 읽기를 막지만 미수신 event 문제까지 해결하지는 않는다.

### 4.2 기타 release gate

| Gate | 조건 |
| --- | --- |
| JWT | Identity의 Billing aud/scope 추가, 재로그인/refresh 발급·기존 audience 유지 검증 |
| network | ALB에서 internal permitAll 경로로 우회할 수 없도록 별도 public connector와 SG·route 검사 |
| repository | readConcern snapshot 설정과 replica set 검증; 단순 readOnly annotation만으로 완료 처리 금지 |
| session attribution | 신규 writer의 durable epoch 증빙과 조회 대상 legacy active Session의 복구 가능한 증거 coverage. command TTL 연장이나 무기한 PENDING으로 대체하지 않음 |
| policy parity | read evaluator와 reserve의 동일 snapshot fixture 판정이 일치 |
| multi-group | 전역 findFirst 금지. 기존 internal resolver가 모호하다고 거절하는 조합을 public에서 ALLOWED로 표시하지 않음 |
| privacy | 재가입 시 source group/Session/결과 비노출, lookup 후보·JWT·내부 식별자 로그 비포함 |
| token lifecycle | stateless JWT는 이미 발급된 토큰의 즉시 폐기를 보장하지 않음. 본 API는 최신 계정 상태 확인 서비스가 아님 |

계정 탈퇴 즉시 모든 Billing read를 막는 별도 deny marker가 필요하면 다른 lifecycle 계약으로 검토한다. 이번 조회의 빈 자격을 '계정이 활성 상태'라는 증거로 사용하지 않는다.

## 5. API와 정확한 응답 계약

### 5.1 Request·header

```http
GET /api/v1/entitlements
Authorization: Bearer <Identity Access Token>
Accept: application/json
```

- query/body/추가 path 없음. `?userId=...`, `?phone=...`, body에 userId 등은 400 INVALID_REQUEST. 앱에서 userId를 header로 보내도 actor에 사용하지 않는다.
- 인증은 먼저 수행한다. 다른 사용자의 ID·전화번호·benefit owner를 입력받는 조회 기능은 만들지 않는다.
- Idempotency-Key 불필요. 반복 GET은 command/inbox/ledger를 만들지 않는다.
- 응답 Content-Type `application/json`, 성공·대기·오류 모두 `Cache-Control: no-store`. ETag/304·CDN cache·사용자 응답 서버 cache 없음.
- 정규화된 유효 W3C traceId를 `X-Trace-Id`에 반환한다. bearer token·userId·candidate는 응답 헤더에 넣지 않는다.

### 5.2 Wrapper

```json
{
  "isSuccess": true,
  "code": "SUCCESS",
  "message": "요청에 성공했습니다.",
  "result": {
    "asOf": "2026-09-07T06:00:00Z",
    "consistency": "LOCAL_PROJECTION",
    "status": "READY",
    "benefits": [
      {
        "benefitCode": "FREE_EXAM_ONCE",
        "displayName": "무료 모의고사 응시권",
        "unit": "EXAM_ATTEMPT",
        "availableQuantity": 1,
        "eligibility": "VERIFIED",
        "usageState": "NOT_STARTED",
        "newAttempt": "ALLOWED",
        "hasInProgress": false,
        "retake": "BLOCKED",
        "reasonCodes": [],
        "attemptGroups": []
      }
    ]
  }
}
```

Identity의 공개 envelope 형식과 맞추되 소스를 복사하지 않고 Billing public DTO를 정의한다. HTTP status를 항상 200으로 감싸지 않으며 error도 같은 envelope로 직렬화한다. 기존 internal body 없는 204와 오류 DTO는 그대로 유지한다.

### 5.3 DTO와 enum

| field | 타입/값 | 의미 |
| --- | --- | --- |
| asOf | UTC Instant 문자열 | 한 snapshot 판정에서 고정한 서버 기준 시각. Identity 전체 동기화 watermark가 아님 |
| consistency | LOCAL_PROJECTION | Billing에 commit된 projection 기준 |
| status | READY / PENDING | action 중 PENDING 또는 수량/진행 여부에 null이 있으면 PENDING, 그 외 READY. usageState=UNKNOWN만으로 대기를 뜻하지는 않음 |
| benefits | 배열 | benefitCode 정렬, code당 한 항목. 현재 구현은 FREE_EXAM_ONCE reader만 등록 |
| benefitCode | 승인 catalog code | Claim/Grant ID가 아닌 공개 혜택 종류 식별자 |
| displayName | catalog 문자열 | 현재 `무료 모의고사 응시권`; 사용자 자유 입력 아님 |
| unit | EXAM_ATTEMPT | 현재 단위. 다른 단위 도입 시 별도 reader/계약 검토 |
| availableQuantity | 0 이상 integer 또는 null | 새 INITIAL 미예약 수량, 알 수 없으면 null |
| eligibility | VERIFIED / NOT_ELIGIBLE / UNKNOWN | 현재 Binding 근거. 계정 유형과 별개 |
| usageState | NOT_STARTED / INCOMPLETE / COMPLETED / MIXED / UNKNOWN | reservation/소비/완료 증거의 요약. consumed만으로 COMPLETED 아님 |
| newAttempt | ALLOWED / BLOCKED / PENDING | 새 INITIAL 가능성. 수량·자격·현재 guard를 함께 판정 |
| hasInProgress | boolean 또는 null | 본인에게 귀속된 OPEN/GRADING 진행 Session 존재; 신규 생성 대기/단순 재응시 가능과 구분 |
| retake | ALLOWED / BLOCKED / PENDING | 기존 group에서 추가 차감 없는 새 replacement 가능성 |
| reasonCodes | enum 배열 | 중복 제거·정렬. 알 수 없는 상태를 자유 문자열로 설명하지 않음 |
| attemptGroups | 배열 | current owner가 본인인 retained group의 최소 상태, attemptGroupId 정렬 |

`AttemptGroupView`는 `attemptGroupId`(lowercase UUID), `state`(OPEN/GRADING/RETAKE_AVAILABLE/COMPLETED), `hasInProgress`(boolean|null), `retake`(ALLOWED/BLOCKED/PENDING), `reasonCodes`만 포함한다. group ID는 조회 대상을 지정하는 입력이나 권한 token이 아니며 LC 요청 권한을 대신하지 않는다.

공개 reasonCodes:

- `ELIGIBILITY_UNKNOWN`: 자격 projection 없음 또는 자격 판정 근거 불충분.
- `PHONE_NOT_ELIGIBLE`: 명시적으로 현재 phone 자격 없음.
- `OWNER_LINK_UNRESOLVED`: Claim owner가 현재 사용자에게 연결되지 않았고 안전한 이전 완료를 확인하지 못함.
- `RESERVATION_PENDING`: 아직 RESERVED 또는 PROPOSED 상태.
- `RESERVATION_EXPIRY_PENDING`: expiresAt은 지났지만 release/terminal commit이 아직 안 됨.
- `COMMAND_PENDING`: 관측 가능한 PROCESSING reserve.
- `ATTEMPT_IN_PROGRESS`: 본인 OPEN 진행 Session 존재.
- `GRADING_IN_PROGRESS`: 채점·요약 중, replacement 불가.
- `RETAKE_AVAILABLE`: 기존 consumption으로 replacement 가능.
- `REJOIN_RESTART_AVAILABLE`: phone owner 이전 완료 뒤 새 Session으로 처음부터 재응시 가능.
- `BENEFIT_COMPLETED`: 현재 retained 무료 혜택 사용 완료로 새 지급 불가.
- `NO_NEW_UNITS`: 확인된 미예약 신규 수량 0.
- `RECONCILIATION_PENDING`: 승인된 전이 중 필요한 projection 일부를 아직 확인하지 못함.
- `AMBIGUOUS_ATTEMPT_CONTEXT`: 여러 대상 중 기존 시작 계약으로 선택할 수 없음.

reason는 금전/전화번호/이전 계정/내부 에러 상세를 담지 않는다. 새 enum은 reader-first 문서·클라이언트 대응 후 추가한다. 앱은 알 수 없는 action/enum을 사용 가능으로 해석하지 않는다.

### 5.4 0개지만 재응시 가능한 성공 예시

```json
{
  "isSuccess": true,
  "code": "SUCCESS",
  "message": "요청에 성공했습니다.",
  "result": {
    "asOf": "2026-09-07T06:00:00Z",
    "consistency": "LOCAL_PROJECTION",
    "status": "READY",
    "benefits": [{
      "benefitCode": "FREE_EXAM_ONCE",
      "displayName": "무료 모의고사 응시권",
      "unit": "EXAM_ATTEMPT",
      "availableQuantity": 0,
      "eligibility": "VERIFIED",
      "usageState": "INCOMPLETE",
      "newAttempt": "BLOCKED",
      "hasInProgress": false,
      "retake": "ALLOWED",
      "reasonCodes": ["NO_NEW_UNITS", "RETAKE_AVAILABLE"],
      "attemptGroups": [{
        "attemptGroupId": "a4cddc2b-2e1a-4f92-897c-55e68d963fda",
        "state": "RETAKE_AVAILABLE",
        "hasInProgress": false,
        "retake": "ALLOWED",
        "reasonCodes": ["RETAKE_AVAILABLE"]
      }]
    }]
  }
}
```

### 5.5 자격 확인 대기 예시

HTTP 200, `Retry-After: 5`:

```json
{
  "isSuccess": true,
  "code": "SUCCESS",
  "message": "요청에 성공했습니다.",
  "result": {
    "asOf": "2026-09-07T06:00:00Z",
    "consistency": "LOCAL_PROJECTION",
    "status": "PENDING",
    "benefits": [{
      "benefitCode": "FREE_EXAM_ONCE",
      "displayName": "무료 모의고사 응시권",
      "unit": "EXAM_ATTEMPT",
      "availableQuantity": null,
      "eligibility": "UNKNOWN",
      "usageState": "UNKNOWN",
      "newAttempt": "PENDING",
      "hasInProgress": null,
      "retake": "PENDING",
      "reasonCodes": ["ELIGIBILITY_UNKNOWN"],
      "attemptGroups": []
    }]
  }
}
```

빈 attemptGroups만으로 '진행 시험 없음'을 추론하지 않는다. hasInProgress=null은 확인하지 못한 상태다. 여기서 PENDING은 **판정 미확정**이며 실제 job/event가 처리 중이라는 보장이 아니다. 특히 projection이 없는 Guest에게는 '사용 가능 여부를 확인할 수 없습니다'로 안내하고 '곧 완료됩니다'라고 표시하지 않는다. 기존 paid sync의 `202 PENDING`은 command 접수이므로 이 GET의 200 PENDING과 구분한다.

### 5.6 실패 응답과 코드

```json
{
  "isSuccess": false,
  "code": "ENTITLEMENT_QUERY_UNAVAILABLE",
  "message": "사용권 정보를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.",
  "result": null
}
```

위는 HTTP 503, Retry-After: 5. 정확한 failure reason은 privacy-safe 로그/metric만 사용한다.

| HTTP | code | 조건/클라이언트 처리 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | query/body 등 허용하지 않은 입력 |
| 401 | UNAUTHENTICATED | 토큰 없음·잘못된 서명/issuer/aud/kid/type/sub/시간/필수 claim. 유효하지 않은 key ID 자체는 노출하지 않음 |
| 403 | FORBIDDEN | 인증은 유효하나 billing:read 없음 |
| 404 | NOT_FOUND | public reader flag OFF 또는 지원하지 않는 path |
| 405 | METHOD_NOT_ALLOWED | exact path의 잘못된 method; Allow: GET |
| 429 | RATE_LIMITED | 사용자별 요청 제한; Retry-After 포함 |
| 503 | AUTHENTICATION_TEMPORARILY_UNAVAILABLE | 사용 가능한 cached JWKS 없이 신뢰된 JWKS upstream 자체가 불가. 토큰을 승인하지 않음 |
| 503 | ENTITLEMENT_QUERY_UNAVAILABLE | DB/timeout/snapshot 실패/불변식 위반/필수 catalog 결손/읽기 상한 초과 |

JWKS 조회가 정상인데 kid가 없거나 일치 key가 없으면 401이다. 임의 unknown kid가 upstream 호출 폭주를 유발하지 않게 refresh를 제한한다. 실패 시 부분 성공 benefits/빈 배열/0개를 반환하지 않는다. 401은 Bearer challenge, 403은 insufficient_scope 의미를 유지하고 raw validator exception은 response에 노출하지 않는다.

## 6. 상황별 판정표

표의 수량은 현재 `FREE_EXAM_ONCE`의 신규 사용 가능 수량이다. `N`은 확인된 Grant available, `?`는 null. A/H/C는 내부 available/held/consumed이며 응답에는 직접 내보내지 않는다. 다른 데이터가 일관되고 해당 group/subject 판정이 모호하지 않다는 조건이다.

| 상황 | 수량 | usageState | newAttempt | hasInProgress | retake | 응답/이유 |
| --- | ---: | --- | --- | --- | --- | --- |
| VERIFIED, retained candidate match 없음, Grant 없음 | 1 | NOT_STARTED | ALLOWED | false | BLOCKED | 200 READY; lazy 지급 예상, write 없음 |
| 같은 candidate가 여러 keyVersion alias로 같은 Claim에 매칭 | 한 번만 계산 | 아래 Claim 상태 | 아래와 같음 | 아래와 같음 | 아래와 같음 | alias 수를 수량으로 합산하지 않음 |
| 본인 retained Claim, ACTIVE Grant A=1/H=0/C=0, group 없음 | 1 | NOT_STARTED | ALLOWED | false | BLOCKED | 200 READY |
| 최초 RESERVED, A=0/H=1/C=0, PROPOSED | 0 | INCOMPLETE | BLOCKED | false | PENDING | 200 PENDING / RESERVATION_PENDING |
| RESERVED의 expiresAt 경과, 아직 hold release 안 됨 | 0 | INCOMPLETE | PENDING | false 또는 기존 group 기준 | PENDING | RESERVATION_EXPIRY_PENDING; 조회에서 복구 안 함 |
| cancel/expiry release commit 완료, A=1/H=0/C=0, group 없음 | 1 | NOT_STARTED | ALLOWED | false | BLOCKED | Claim 유지, 조회로 재지급한 것이 아님 |
| confirm 완료, C=1, 본인 OPEN/ACTIVE Session | 0 | INCOMPLETE | BLOCKED | true | ALLOWED | ATTEMPT_IN_PROGRESS; 새 Session replacement이지 이어풀기 아님 |
| C=1, RETAKE_AVAILABLE | 0 | INCOMPLETE | BLOCKED | false | ALLOWED | RETAKE_AVAILABLE |
| C=1, GRADING | 0 | INCOMPLETE | BLOCKED | true | BLOCKED | GRADING_IN_PROGRESS; 정상 진행 상태, 자격 연동 대기와 다름 |
| C=1, COMPLETED | 0 | COMPLETED | BLOCKED | false | BLOCKED | BENEFIT_COMPLETED |
| replacement RESERVED/PROPOSED, C=1 | 0 | INCOMPLETE | BLOCKED | false 또는 확인된 Session 기준 | PENDING | RESERVATION_PENDING; 추가 사용량 없음 |
| PROCESSING reserve 관측 | 증명되는 N, 불명 시 ? | 근거별 | PENDING | 근거별/null | PENDING | COMMAND_PENDING, 200 PENDING |
| current projection 없음, GUEST/MEMBER 무관 | ? | UNKNOWN 또는 확인된 본인 요약 | PENDING | 근거별/null | PENDING | ELIGIBILITY_UNKNOWN; 무자격 지급 없음 |
| 명시적 REVOKED | 0 | 확인된 본인 요약 또는 UNKNOWN | BLOCKED | 본인 진행 증거별 | BLOCKED | PHONE_NOT_ELIGIBLE; Grant unit은 변경하지 않음 |
| VERIFIED, retained Claim의 owner가 다름, 미사용/미완료 | ? | UNKNOWN | PENDING | null | PENDING | OWNER_LINK_UNRESOLVED, source group 비노출 |
| VERIFIED, 같은 번호의 retained 사용 완료가 입증됨, owner 다름 | 0 | COMPLETED | BLOCKED | false | BLOCKED | BENEFIT_COMPLETED; group/과거 기록 비노출 |
| phone owner 이전 완료, 미사용 Claim/Grant | 1 | NOT_STARTED | ALLOWED | false | BLOCKED | 새 Claim/Grant 없음 |
| phone owner 이전 완료, OPEN 또는 RETAKE_AVAILABLE, target Session 아직 없음 | 0 | INCOMPLETE | BLOCKED | false | ALLOWED | REJOIN_RESTART_AVAILABLE; 승인된 group ID만 반환 |
| owner 이전 후 exact target replacement confirm | 0 | INCOMPLETE | BLOCKED | true | ALLOWED | 일반 본인 OPEN 판정으로 수렴 |
| target confirm 후 7일 이상 경과·reserve/confirm command 삭제 | 0 | INCOMPLETE | BLOCKED | true | ALLOWED | 동일 sessionOwnerEpoch로 판정 유지, command 재조회 불필요 |
| A→B→C 반복 phone rejoin, A/B의 source Session 잔존 | 0 | INCOMPLETE | BLOCKED | C Session 없으면 false | ALLOWED(기존 group 조건 충족 시) | 낮은 epoch의 Session을 C 진행으로 표시하지 않음 |
| retained 만료 alias만 있고 current VERIFIED, reserve 기준 신규 가능 | 1 | NOT_STARTED | ALLOWED | false | BLOCKED | 만료 alias를 조회 중 삭제/비활성화하지 않음 |
| Claim 있으나 Grant/owner 연결 결손, 다중 Claim candidate 충돌, unit 음수·합계 불일치 | — | — | — | — | — | 503, 지급 가능한 신규로 추정 금지 |
| 여러 benefit/여러 owned subject의 정상 group | benefit별 | 독립 계산/MIXED | 독립 판정 | 배열 집계 | 독립 판정 | 전역 단일 group/총수량 가정 금지 |
| 기존 LC/phone resolver에서 선택 불가능한 다중 actionable group | 확인된 수량 또는 ? | 근거별 | PENDING(영향 범위) | 근거별 | PENDING | AMBIGUOUS_ATTEMPT_CONTEXT; 임의 group 선택 금지 |
| DB 장애/조회 timeout | — | — | — | — | — | 503 result=null; 0개로 숨기지 않음 |

OPEN은 Billing에서 앱의 현재 접속 여부를 뜻하지 않는다. 앱이 나가도 곧바로 별도 abandoned event가 온다고 가정하지 않는다. 사용자에게 보이는 `hasInProgress`는 귀속 가능한 OPEN/GRADING Session의 존재이고, 앱 foreground/실시간 접속 신호가 아니다.

무료 사용 완료는 현재 retained 혜택 정책의 완료 증거만 요약한다. 서로 다른 번호·이미 보존기간이 끝난 모든 과거 계정 이력을 찾아 lifetime 완료를 계산하지 않는다.

## 7. 읽기 알고리즘과 reserve 정합성

### 7.1 순수 판정과 읽기 전용 snapshot

1. JWT principal을 확정하고 공개 가능한 무료 reader 목록을 선택한다. 현재 FREE_EXAM_ONCE만 구현한다. 임의 BenefitDefinition 행을 넣는 것만으로 새 지급 정책이 자동 적용되지는 않는다.
2. replica set primary의 명시적인 readConcern SNAPSHOT 트랜잭션을 시작하고 하나의 `asOf`로 retention·expiry를 계산한다. read-only annotation만 믿지 않고 실제 session/transaction options를 테스트한다.
3. catalog, 현재 환경 expected consumerScopeId의 TrialEligibility, 본인 retained current owner links, active command를 조회한다.
4. VERIFIED/nonempty candidate이면 **read-only** active alias lookup을 수행한다. benefitCode/keyVersion/candidate/retention 조건과 claimedAt+3년 logical expiry는 reserve와 동일하게 적용한다.
5. 동일 Claim의 복수 alias는 dedupe한다. current candidate가 서로 다른 retained Claim 여러 개를 가리키는 것은 현재 무료 정책 불변식 위반으로 503이며 여러 혜택 정상 보유와 혼동하지 않는다.
6. Claim/Grant/link/group/active Reservation/AttemptSession을 bounded batch로 읽고 shared evaluator에 immutable snapshot으로 전달한다. 다른 사용자의 기록은 candidate 중복 수급 방지에 필요한 최소 policy 판정만 내부에서 사용한다.
7. 지급 예상량과 실제 Grant.available을 중복 합산하지 않는다. Claim 없음·새 지급 자격 증명일 때만 defaultGrantUnits를 예상 수량으로 사용한다. Claim 존재·Grant 결손은 오류다.
8. newAttempt는 수량 외에도 current owner, eligibility, active command/reservation, 진행 group guard·GRADING을 검증한다. retake는 현재 candidate로 reserve가 해석 가능한 retained Claim/consumption·group/source를 검증한다.
9. current owner가 JWT sub인 group만 공개 변환하고 §8의 durable epoch와 exact group/Reservation/Session 연결로 귀속을 판정한다. phone source Session은 공개하지 않으며 command 보존 여부로 hasInProgress를 바꾸지 않는다. 복구 불가능한 legacy 증빙 결손은 release gate/503 오류이며 무기한 PENDING으로 감추지 않는다.
10. 읽기 트랜잭션을 종료하고 DTO를 반환한다. 금지 write가 있었으면 테스트 실패로 처리한다. snapshot 실패는 새 snapshot으로 최대 2회 재시도, 총 조회 budget 2초를 넘으면 503.

primary/snapshot은 같은 instant에 서로 다른 version을 섞지 않기 위한 장치다. 조회 직후 다른 reserve/owner event가 commit하면 프론트 결과는 달라질 수 있다. GET 결과를 reserve 캐시/예약 보증/허가 token으로 사용하지 않는다.

### 7.2 공통 정책 재사용 범위

`ReserveService.reserve/processOnce/resolveClaim`, `PhoneContinuationService.resolve`, owner rebind command handler를 GET에서 호출하지 않는다. read 목적에 맞게 순수 `TrialEntitlementPolicy`와 snapshot DTO를 분리하고 write 경로의 기존 predicate와 비교한다.

- 공유할 것: active catalog 검증, VERIFIED/candidate, retained 경계, candidate→Claim 유일성, owner 일치, grant 수량 불변식, INITIAL/REPLACEMENT 판정 조건.
- 공유하지 않을 것: Claim/alias/Grant 발급·deactivateExpiredMatches·holdOne·active Session abandon·command insert·owner CAS.
- reserve 내부에 공유 predicate를 적용할 때 기존 실패 코드·Transaction 경계·멱등 순서·wire를 유지한다. 동작 변경이 필요한 불일치는 조회 구현에 끼워 넣지 않고 보고한다.
- 판정 fixture를 신규/재응시/완료/다른 owner/만료 경계별로 read·reserve에 함께 적용한다. 단지 비슷한 조건문 두 벌을 복사해 완료로 간주하지 않는다.
- reader는 ACTIVE Grant 여부도 검증한다. reserve가 동시 unique index에 의해 거절될 수 있는 상황을 확정적 성공이라고 반환하지 않는다.

### 7.3 여러 혜택·그룹과 집계

- benefit별 reader가 `benefitCode+unit`로 독립 결과를 만든다. 다른 정책·단위의 권리를 하나의 balance로 합치지 않는다.
- 같은 benefit의 여러 **정당한 source**는 source별 판정 뒤 합산할 수 있지만 현재 FREE_EXAM_ONCE의 신규 INITIAL 수량은 current candidate가 선택한 Claim 또는 미발급 자격에서만 나온다. 본인 명의의 모든 과거 번호 Grant를 무조건 합산하지 않는다.
- 본인 owned group은 현재 자격에서 당장 재응시할 수 없더라도 진행/완료로 표시할 수 있다. retake는 별도 검증한다. 과거 owned group이 있다는 이유로 다른 current candidate의 신규 eligibility를 무조건 차단하지 않고 기존 reserve 판정에 맞춘다.
- 현재 Claim 하나에 복수 nonterminal group 같은 불변식 오류와 서로 다른 claim/benefit의 정상 복수 group을 구분한다.
- benefit hasInProgress는 current-owned group 중 true가 있으면 true, 모두 확정 false면 false, 나머지는 null. retake는 **기존 시작 계약으로 도달 가능한** ALLOWED가 있으면 ALLOWED, 전부 BLOCKED면 BLOCKED, 나머지는 PENDING이다. 모호한 group을 목록만 보고 ALLOWED로 만들지 않는다.
- usageState는 미사용 source만 있으면 NOT_STARTED, 미완료 사용만 있으면 INCOMPLETE, 확인된 사용 source가 모두 완료이고 남은 신규 자격이 없으면 COMPLETED, 미사용/미완료/완료가 공존하면 MIXED, 증거가 불충분하면 UNKNOWN이다.
- stable sort로 동일 상태는 동일 배열 순서를 유지한다. 초기 방어 상한은 무료 benefit 20개, 읽기 subject/group 각각 100개, 상한+1 조회로 초과를 탐지한다. 이는 보유량 제품 제한이 아니라 응답·쿼리 방어다. 넘으면 503/경보이며 silently truncate하지 않는다. 승인된 확장 시 pagination/상한 재검토가 필요하다.
- 최대 serialized response 64 KiB. 기존 **internal** 16 KiB 계약은 그대로다. HTTP 응답을 쓰기 전에 크기 초과를 검증해 잘린 JSON을 반환하지 않는다.

## 8. 동일 전화번호 재가입 처리

### 8.1 이전 전

새 사용자의 VERIFIED candidate가 retained Claim을 찾더라도 current link가 다른 사용자라면 사용권을 넘겨받은 것이 아니다. 미사용/미완료는 PENDING/null, 새 group/무료권 지급 없음, source group ID·userId·Session·결과·이전 시각 비노출이다.

현재 Binding에 연결된 retained 완료 증거가 확실하면 신규 무료 수량 0·COMPLETED라는 **무료 정책 요약만** 반환한다. source의 시험 목록·완료 일시·mockExamId를 노출하지 않는다. 전체 완료 기록을 새 계정의 학습 이력처럼 복원하지 않는다.

source가 GRADING이라면 owner rebind가 pending일 수 있지만 새 사용자에게 source의 채점 상태를 상세 노출하지 않는다. 외부 응답은 OWNER_LINK_UNRESOLVED로 통일한다.

### 8.2 이전 후

- active link.userId가 JWT sub와 같고 PHONE_REJOIN transition context가 승인된 group과 맞는지 확인한다. legacy fence 존재만으로 신규 사용 권한을 부여하지 않는다.
- OPEN/RETAKE_AVAILABLE은 기존 consumption을 재사용할 수 있음을 표시한다. availableQuantity=0, retake=ALLOWED, reason=REJOIN_RESTART_AVAILABLE.
- target이 아직 새 Session을 만들지 않았다면 source의 기존 ACTIVE Session 때문에 hasInProgress=true로 표시하지 않는다. 아래 durable epoch와 exact group/Reservation/Session 연결을 사용하며 command와 시각 비교만으로 owner를 추론하지 않는다.
- **정상 confirm 후 command가 삭제되어도 판정은 유지한다.** command는 멱등성 자료이지 장기 소유권 자료가 아니다. 'TTL 때문에 증거가 없어졌으니 재조회하며 PENDING'이라는 이전 초안은 폐기한다.
- 최초 target replacement 성공 이후에는 일반 본인 진행 판정으로 수렴한다. 조회는 LC의 internal phone continuation discovery나 reserve exact echo를 대체하지 않는다.
- COMPLETED는 기존 정책대로 owner 이전 NOOP이며 조회가 이를 초기화하지 않는다.

### 8.3 기존 필드만으로 가능한 판정과 한계

| 근거 | 가능한 것 | 불충분한 점 |
| --- | --- | --- |
| link.userId/ownerVersion | 현재 권리 owner·이전 횟수 판정 | 각 Session 생성 당시 owner를 저장하지 않음 |
| link.ownerTransitionId + Reservation.continuationId | 마지막 PHONE_REJOIN을 exact echo한 Reservation과 그 proposedSessionId의 positive evidence | continuation은 all-or-none 선택 필드. 같은 target의 후속 일반 replacement에는 없을 수 있음 |
| Reservation의 subject/group/session/operation 연결 | 동일 사용 건·Session의 연결과 CONFIRMED 확인 | 연결만으로 전·후 owner의 구분은 안 됨 |
| command.userId | command가 남아 있을 때 actor 교차검증 | confirm 뒤 기본 7일 TTL로 삭제되므로 장기 기준 불가 |
| legacy fence | exact pre-rebind source event 허용 | bounded cleanup 대상이며 신규 사용자 Session 귀속 증명 아님 |

마지막 transition 필드는 다음 rebind/Guest merge에서 바뀐다. continuationId 불일치/부재만으로 source Session이라고 단정할 수도 없다. 따라서 positive legacy 증거로는 활용하되 모든 새 Session에 대해 일관된 귀속을 보장할 최소 증빙을 추가한다.

### 8.4 최소 durable 증빙과 쓰기 범위

신규 userId 사본이나 phone fingerprint를 추가하지 않고 **subject 내부의 세션 귀속 세대**를 정수로 저장한다. 이는 조회 보조 증빙이며 LC Session의 실제 owner를 변경하거나 신규 사용 권한을 발급하는 token이 아니다.

| document | 추가 field | 생성·변경 규칙 |
| --- | --- | --- |
| BillingSubjectLink | sessionOwnerEpoch: positive int64 | 신규 link는 1. 실제 APPLIED PHONE_REJOIN 때만 +1. duplicate/NOOP/pending에서는 변경 없음 |
| BillingSubjectLink | sessionBindingVersion: nonnegative int64 | 초기 0, reserve/confirm의 귀속 검증 CAS 때 증가시키는 경쟁 제어용 필드 |
| Reservation | sessionOwnerEpoch: positive int64 | 신규 INITIAL·일반 REPLACEMENT·phone continuation 모두 reserve 시 현재 link 값 snapshot, 이후 불변 |
| AttemptSession | sessionOwnerEpoch: positive int64 | reserve에서 Reservation과 같은 값으로 생성, confirm은 exact 일치 검증; 나중에 source Session 값을 target epoch로 바꾸지 않음 |

- 실제 계정 통합인 USER_MERGED는 기존 학습 소유권 migration 계약을 유지하므로 sessionOwnerEpoch를 보존한다. ownerVersion은 기존대로 증가한다. Guest merge를 PHONE_REJOIN과 동일한 '이전 세션 단절'로 바꾸지 않는다.
- reserve Transaction에서 current link.userId·ownerVersion·epoch·sessionBindingVersion을 검증하고 sessionBindingVersion CAS를 증가시킨 뒤 Reservation/Session 증빙을 같은 Transaction에 저장한다. 신규 link 생성의 경우 그 Transaction에서 함께 초기화한다.
- confirm Transaction은 Reservation/Session/subject/group/operation exact 연결과 current link owner·epoch를 확인하고 같은 link의 sessionBindingVersion CAS를 증가시킨다. 그 뒤 기존 CONFIRMED/ACTIVE/group 전이·consumption·command 종료와 함께 commit한다. epoch 불일치·경쟁은 기존 실패/재시도 계약으로 수렴하며 임의 repair-confirm하지 않는다.
- 동일 link에 대한 쓰기를 넣는 이유는 read-only owner 검사만으로 생길 수 있는 reserve/confirm 대 rebind의 write skew를 막기 위해서다. rebind가 같은 document를 CAS 변경하므로 한쪽 Transaction이 재시도·기존 pending 판단으로 수렴한다. 이 CAS는 GET에서는 수행하지 않는다.
- PHONE_REJOIN의 link owner CAS와 epoch 증가는 동일 Transaction에서 수행한다. 최초 duplicate 확인을 통과한 실제 이전만 증가시키며, inbox duplicate·NOOP·rollback은 추가 증가시키지 않는다. 기존 source Session·Reservation 증빙은 그대로 둔다.
- epoch보다 작은 source 세대의 Session은 현재 사용자의 진행 Session이 아니다. epoch가 같아도 group.activeSessionId/Session ACTIVE 또는 GRADING의 exact 연결과 CONFIRMED Reservation을 확인해야 한다. PROPOSED, FAILED, ABANDONED_RESTARTED는 진행으로 세지 않는다. 미래 epoch·Reservation/Session 불일치는 503 불변식 오류다.
- A(1)→B(2)→C(3) phone rejoin이면 B가 confirm한 Session은 2, C가 새로 confirm한 Session은 3이다. A/B Session이 남거나 command가 모두 삭제돼도 C의 현재 진행 여부를 구분할 수 있다. 뒤의 USER_MERGED는 3을 유지한다.
- epoch 불일치는 '이전 사용자의 Session임'을 설명할 뿐 group 상태 전이를 다시 결정하지 않는다. 기존 legacy-source GRADING/terminal event, 신규 reserve·재응시 가능성은 기존 ADR-003 상태표를 따른다.
- wire DTO·기존 ownerVersion 의미·Claim/Grant/consumption·reserve/confirm 멱등 key는 유지한다. metadata 추가를 이유로 ownerVersion을 reserve마다 증가시키지 않는다. 같은 command replay는 새 Session/epoch/추가 CAS 효과를 만들지 않는다.
- 최소 증빙은 원 Reservation/Session/link의 승인 보존·연결 purge 범위 안에서 유지하며 command TTL에 맞춰 별도 삭제하지 않는다. raw userId·source event·phone을 추가 보존하지 않고 epoch도 기존 subject 연결의 개인정보 수명주기에 포함한다. 보존기간 연장·새 무기한 이력 collection은 만들지 않는다.

### 8.5 Reader-first와 legacy 증빙 복구

추가 field와 nullable reader를 구현했다. 새 collection/index 없이 기존 document를 읽으며, 별도의 [data migration manifest v1](../runbooks/PLAN-007-public-reader-rollout.md)로 증빙 coverage를 검증한다. payment schema v5와는 별개다. 자동 이관은 미이전 subject와 exact latest continuation positive proof에 한정하며 command actor 단독/시간 추측은 BLOCKED다. 추가 transition chain 근거가 필요한 legacy는 활성화 전에 별도 증빙·이관 승인을 받아야 한다.

1. reader OFF 상태에서 nullable field를 읽을 수 있는 버전을 배포한다. 기존 epoch 부재를 GET에서 현재 값으로 채우거나 무조건 1로 해석하지 않는다.
2. 신규 writer를 전환한다. 신규 subject는 1을 쓰고, 기존 link의 최초 epoch 초기화는 현재 ownerVersion을 baseline으로 exact CAS 저장한다. 이 baseline은 과거 PHONE_REJOIN 횟수를 복원했다는 뜻이 아니다. 이미 존재하는 Session에는 현재 baseline을 일괄 복사하지 않는다.
3. ownerVersion logical 1이며 transition이 없는 legacy link는 미이전 증거를 검증한 뒤 연결된 Reservation/Session을 세대 1로 이관할 수 있다. 최신 PHONE_REJOIN의 exact continuationId·CONFIRMED Reservation·Session 연결은 현재 target Session의 positive 증거로 사용할 수 있다.
4. 이외 legacy는 아직 남아 있는 command actor와 exact 연결, 검증 가능한 owner transition 근거로만 별도 migration에서 증빙을 확정한다. command는 일회성 migration 자료로만 사용할 수 있고 완료 뒤 read는 의존하지 않는다. timestamp 단독·최신 owner 일괄 복사·continuation 없음=source 같은 휴리스틱은 금지한다.
5. migration은 dry-run→대상/예외 집계→승인된 bounded CAS write→coverage 재검증 순서다. live reserve/rebind와 경쟁하면 source version이 달라진 대상을 재판정한다. 조회/일반 startup에서 자동 bulk rewrite하지 않는다. old writer가 재배포되어 증빙 없는 Session을 만들지 못하게 rollout/rollback gate를 둔다.
6. 복구 불가능한 활성 legacy Session은 수정이 필요한 데이터·연동 gate다. reader ON 전에 해결해야 하며, 필요하면 검증 가능한 LC 소유 증거를 확보하는 별도 승인 절차를 요청한다. 이번 계획만으로 타 서버 조회/owner 변경·Session 삭제·무료권 재지급을 수행하지 않는다.
7. 해결되지 않은 legacy 결손이 runtime에서 발견되면 503 ENTITLEMENT_QUERY_UNAVAILABLE와 privacy-safe LEGACY_SESSION_ATTRIBUTION_MISSING 경보로 분류한다. 자동으로 회복되지 않는 결손을 일반 PENDING으로 무한 polling하지 않는다. 정상 신규 Session은 command 삭제 전후 동일한 결과여야 한다.

조회 DTO에는 continuationId, mockExamId, sessionId, Claim/Grant/subjectRefId, candidate/keyVersion/fingerprint, source/target userId, ownerVersion, sessionOwnerEpoch, sessionBindingVersion, eventId, payload/digest를 넣지 않는다. **승인된 current-owned AttemptGroup ID와 최소 상태만 예외적으로 공개**한다. app이 이 ID를 다른 서버로 보낸다고 권한이 생기지는 않는다.

## 9. Identity JWT 발급 인계

### 9.1 Billing 검증 규칙

- RS256만 허용, typ=JWT, nonblank kid. 신뢰된 환경별 HTTPS issuer/JWKS만 사용하며 token의 jku/x5u를 따라가지 않는다.
- JWKS의 승인 RSA signing key와 kid로 검증한다. 현재·회전 중 이전 key는 배포 allowlist/JWKS에 있는 기간만 허용하고 하나의 현재 kid에 영구 고정하지 않는다.
- aud에 `tosunsaeng-billing`이 반드시 포함돼야 한다. LC-only·Identity workload token으로 우회 불가.
- exp/iat/jti/sub 필수. exp>iat, expiry와 future iat, nbf가 있다면 not-before를 검증한다. 최대 clock skew 60초. sub는 lowercase canonical UUID이며 user UUID version을 임의로 v4에 제한하지 않는다.
- scope는 Identity의 공백 구분 `scope` 문자열에서 exact token `billing:read`를 검사한다. substring·client claim·body 역할을 신뢰하지 않는다.
- GUEST/MEMBER 모두 read 허용. `account_type`은 자격 판정 근거가 아니며 이 read API의 추가 MEMBER gate로 쓰지 않는다. 기존 승인 JWT 검증 조건을 충족하면 account_type 부재만으로 무료 reader를 막는 별도 조건은 추가하지 않는다.
- 다른 사용자의 userId, phone, 임의 X-User-Id/account_type 헤더는 actor에 영향을 주지 않는다.
- JWKS cache 갱신 실패 시 유효한 cached key가 있으면 정책에 따라 검증하고, 검증 가능한 key가 없으면 fail-closed. unknown-kid 반복 공격은 bounded refresh로 제한한다.

### 9.2 Identity에 요청할 별도 변경

```text
사용자 Access Token aud:
  기존 tosunsaeng-learning-core 유지
  + tosunsaeng-billing

사용자 scope:
  기존 scope 유지
  GUEST와 MEMBER에 billing:read 추가
```

- 이번 무료 조회 때문에 billing:purchase를 추가하지 않는다. paid scope는 후속 결제 계약이다.
- issuer/kid/JWKS/RS256/sub/iat/exp/jti와 PR #39 account_type 발급을 보존한다. raw phone·candidate·무료 수량을 JWT에 넣지 않는다.
- 현재 `JwtAccessTokenIssuer.audience(List.of(properties.audience()))`는 단일 audience이므로 다중 audience 지원을 별도 구현해야 한다. 설정 문자열에 쉼표를 넣는 것만으로 배열 두 개가 되는 것으로 간주하지 않는다.
- 기본 scope fallback만 바꾸지 말고 명시 scope를 전달하는 로그인·Guest·승격·merge target·refresh 등 모든 사용자 발급 경로를 테스트한다. workload token에는 사용자 Billing audience/read scope를 추가하지 않는다.
- 기존 토큰은 저절로 바뀌지 않는다. 앱은 정상 refresh 또는 재로그인으로 새 사용자 token을 받아야 한다. Billing은 이관 때문에 audience/scope 검증을 느슨하게 하지 않는다.
- 이미 PR #39가 병합됐다는 사실과 Billing audience/read 발급 완료·운영 배포 증빙을 분리한다. 여기서는 Identity 변경을 수행하지 않는다.

## 10. Public ingress와 paid 조회의 관계

### 10.1 무료 public reader를 결제보다 먼저 연다

이전 무료-only의 앱 직접 Billing 호출 없음은 **이 GET 한 개에 한해** 확장한다. 승인된 기존 ALB 재사용·별도 Billing target group 방향을 이용하되 payment URL 전체를 미리 열지 않는다.

- public ALB: 환경별 Billing hostname + exact GET `/api/v1/entitlements`만 별도 public target port로 라우팅, 그 외 fixed reject. 인증은 JWT chain.
- internal: 기존 Lattice AWS_IAM+ECS task role+SigV4 경로·method 권한을 유지한다.
- Billing 현재 LATTICE 모드의 permitAll은 네트워크 인증 전제다. public/internal **별도 connector/port + application ingress guard** 없이 ALB를 기존 internal task port에 연결하지 않는다.
- SG는 public port에 ALB SG만, internal port에 승인 Lattice 경로만 허용한다. public connector는 `/internal/**`를 거절하고 internal connector는 이 public handler를 받지 않는다. 위조 가능한 header/Host로 ingress 신뢰를 판단하지 않는다.
- `latticeOnly` 기존 설정은 internal connector의 배타성을 의미하도록 이관 검증한다. public 추가를 이유로 내부 fail-closed 검사를 단순 false 처리하지 않는다.
- direct task, 인코딩·중복 slash, method 변형, forged principal, wrong role/환경, actuator 우회를 검사한다. 관리 endpoint를 public listener에 같이 노출하지 않는다.
- 서브도메인·certificate/target port/SG 실제 inventory가 이 격리를 지원하지 않으면 활성화하지 않고 별도 infra 변경안을 보고한다. 로컬 편의를 위해 운영 경계를 완화하지 않는다.

### 10.2 Paid endpoint와 역할

| endpoint | 이번 계획 | 후속 의미 |
| --- | --- | --- |
| GET `/api/v1/entitlements` | 무료 benefit 수량·진행·재응시 구현 | 조회 모델의 확장 지점. 유료 기간을 무료 개수로 합산하지 않음 |
| GET `/api/v1/payments/entitlement` | 구현·노출하지 않음 | ADR-004의 active/scheduled paid 기간 조회 |
| payments products/account/sync/webhook | 모두 제외 | 결제 PLAN에서 구현 |

무료 reader는 paid 목록을 비었다고 반환하거나 paid 미보유를 판단하지 않는다. `benefits`가 현재 무료 조회 범위임을 프론트 계약에 명시한다. 나중에 통합 화면이 필요하면 각 API를 조합하거나 별도 typed paid section을 승인하며, 자동갱신/기간형 권리를 EXAM_ATTEMPT 수량으로 환산하지 않는다.

이번 public envelope는 Identity 앱 규격과 정렬했다. ADR-004는 아직 미구현 초안으로 direct DTO를 제안했으므로 **두 문서의 wrapper는 현재 다르다**. 결제 구현 전 public wrapper를 통일할지 해당 ADR을 갱신·검토해야 하며, 이번 무료 계획으로 기존 내부 DTO나 모든 paid wire를 소급 변경하지 않는다. 공통 JWT verifier·public ingress·error serializer는 결제 단계에서 재사용할 수 있지만 도메인 resolver는 분리한다.

PLAN-007 번호는 이번 무료 reader가 사용한다. 이전 결제 초안의 PLAN-007 예정 표기는 역사적 순서이며 실제 결제 계획은 이후 번호로 작성한다. payment schema v5/paid guard는 무료 조회에 선행하지 않는다.

## 11. Feature flag와 배포 순서

새 설정 `billing.entitlement-query.enabled=false`를 기본값으로 둔다. flag OFF이면 reader route는 404/fail-closed, 기존 internal route는 영향 없음. public connector/JWT 설정 유효성 검증과 app flag는 별도로 관리해 비활성 배포에서 staging smoke를 준비할 수 있다.

| 단계 | 작업 | 완료 조건 |
| --- | --- | --- |
| 1 Billing 개발 | reader·JWT·격리된 public connector·durable epoch writer·테스트 | fake JWT/JWKS·Mongo 회귀, command 삭제 독립성 통과, 결제 코드 없음 |
| 2 Billing OFF 배포 | public route/flag 비활성 상태의 인프라·reader 배포 | internal negative/smoke 통과, 일반 트래픽에 조회 미노출 |
| 3 Identity 후속 | aud 배열 Billing 추가, 양 계정 유형 billing:read | 모든 사용자 발급/refresh·workload 분리·LC 기존 aud 회귀 |
| 4 staging | legacy 귀속 증빙 coverage 완료 후 제한된 환경에서 reader ON, 실제 Identity→Billing | 정상 조회, command TTL 삭제·반복 rejoin·Guest merge·legacy backfill·reserve/rebind 경쟁·JWT/JWKS E2E |
| 5 Billing 활성화 | 운영 동일 gate와 feature flag 점진 활성화 | JWKS/ALB/SG/replica set·retention·consumer readiness 검증, rollback 준비 |
| 6 프론트 | 화면·새 token 획득·갱신 흐름 | null/0/재응시 구분, reserve 거절 후 새로고침, 계정 전환 데이터 제거 |

초기 남용 완화는 **task별** 검증된 subject에 60회/분의 메모리 token bucket과 429/Retry-After를 적용한다. 합산 글로벌 사용자 한도가 아니며 task 수만큼 총 허용량이 늘 수 있다. 제한 자료는 메모리에만 두고 5분 비활동 만료·최대 10,000개로 bounded 관리한다. cache eviction은 권한/과금 판정을 바꾸지 않는다. JWT·DB·금융 원장에 rate-limit 기록을 쓰지 않고 새 운영 의존성을 추가하지 않는다. 운영 전체 유입량 제한이 필요하면 ALB 앞 기존 WAF 등의 별도 인프라 한도로 검토하며, 실제 부하 gate는 task 수를 포함해 측정한다.

rollback은 reader flag OFF·해당 ALB rule 비활성화이며 internal eligibility/owner consumer를 함께 끄지 않는다. epoch writer 활성화 후에는 증빙을 기록하지 않는 구버전으로 무조건 rollback하지 않는다. reader를 끄더라도 증빙 호환 writer를 유지하거나 별도 쓰기 중단·재이관 절차를 검증한다. Identity token에 추가된 audience/read를 제거할 필요는 없고 Billing에서 LC-only 토큰을 허용하는 fallback을 만들지 않는다.

## 12. 프론트 갱신·관측 계약

- 최초 로그인/계정 변경/화면 진입, 전화번호 인증 완료, 시험 시작 응답·중단·완료, 재가입 owner 처리 후 foreground에서 GET을 재조회한다.
- 전화번호 인증이나 LC 이벤트 직후 projection 반영 전에는 이전 로컬 상태가 보일 수 있다. 프론트는 해당 mutation 직후 조회가 Identity/LC 변경 완료 증거라는 가정을 하지 않는다.
- PENDING은 판정 불명과 실제 처리 대기를 모두 표현하므로 reasonCodes별 문구/갱신 정책을 구분한다. ELIGIBILITY_UNKNOWN은 특히 Guest에서 '사용 가능 여부를 확인할 수 없습니다'로 표시하고 처리 job 존재·곧 완료를 약속하지 않는다. OWNER_LINK_UNRESOLVED도 '권리 연결 여부를 확인할 수 없습니다'이며 실제 승인 작업 실행 중이라는 단정은 금지한다.
- RESERVATION_PENDING/COMMAND_PENDING처럼 관측된 처리 대기는 Retry-After 5초, foreground 최대 6회/30초 자동 갱신 후 '처리 상태를 확인해 주세요·새로고침'을 제공한다. projection 없는 Guest는 기본적으로 화면 진입·자격 변경·수동 재시도 시 조회하고 단지 GUEST라는 이유로 polling을 계속하지 않는다. 전화번호 인증 완료 직후처럼 알려진 변경이 있을 때만 같은 bounded 재조회를 적용한다. Retry-After는 다음 재시도 최소 간격이지 완료 예정 시각이 아니다.
- GRADING은 정상 진행이며 API 전체를 PENDING으로 만들 필요가 없다. LC 채점 polling 계약은 따로 유지한다.
- 실제 시작은 기존 `POST /api/v1/exams`와 Idempotency-Key 흐름. 이 API가 반환한 group ID·retake 표시로 Session owner나 continuation을 우회하지 않는다.
- reserve가 상태 변경을 이유로 거절하면 조회를 갱신한다. 네트워크 재전송은 기존 시험 생성 멱등성 계약대로 처리하며 버튼 재클릭으로 새 group을 무조건 만들지 않는다.
- HTTP 실패 시 '조회 실패'를 표시하고 마지막 결과가 있더라도 오래된 정보임을 표시한다. 초기값으로 0을 저장하지 않고 nullable 상태를 보존한다.
- 계정 전환/로그아웃 시 화면 메모리의 이전 benefits/group 배열을 폐기한다. no-store 응답을 영구 디스크·공유 cache에 저장하지 않는다.
- 실제 Controller 아래 INTERNAL span `entitlement_query`, 로그 timestamp/service=billing/environment/operation/outcome/durationMs/traceId/spanId. 예외에서도 종료하고 baggage를 전파하지 않는다.
- metric label은 operation/outcome/reason 등 저카디널리티 값만 사용한다. userId/groupId/Claim/Grant/candidate/eventId/JWT/digest·요청/응답 전문은 로그·span attribute·metric label에 넣지 않는다.
- GET은 DB 업무 write가 없지만 privacy-safe 접근 로그/metric은 남을 수 있다. 이를 Claim·Grant·원장 side effect와 구분한다.

## 13. 부록 A — 구현 작업·예상 파일

| 순서 | 작업 | 예상 위치/완료 산출물 |
| --- | --- | --- |
| A1 | public DTO·OpenAPI·오류 계약 | `domain/entitlement/api/EntitlementQueryController`, `dto/response/EntitlementQueryResponse`, public envelope |
| A2 | 읽기 application·순수 policy | `domain/entitlement/application/EntitlementQueryService`, `TrialEntitlementQueryEvaluator`, `FreeBenefitQueryReader` registry, immutable snapshot |
| A3 | 기존 reserve predicate 분리 | `domain/entitlement/trial/application/TrialEntitlementPolicy`, ReserveService 최소 연결; 지급·Transaction 순서 불변 |
| A4 | bounded batch read repository | 기존 trial/grant/group/reservation/session/command repository의 find 계열 또는 전용 EntitlementQueryRepository |
| A5 | read-only transaction 설정 | global Mongo config의 전용 read snapshot executor; write executor 기본 설정 회귀 방지 |
| A6 | JWT·feature flag·network gate | global/config/security의 public chain/decoder/entrypoint/denied handler/connector guard, entitlement query properties |
| A7 | 앱 문서·contract fixture | public contract 예제와 frontend/Identity handoff, application-test fake key/JWKS fixture |
| A8 | unit/MVC/integration/E2E checklist | 도메인 판정·보안·Mongo snapshot·mutation 없음·internal 회귀 |
| A9 | durable session attribution | BillingSubjectLink/Reservation/AttemptSession field와 repository CAS, ReserveService/ReservationLifecycleService/OwnerRebindService의 기존 Transaction 내 metadata 보완 |
| A10 | legacy data migration·coverage | 별도 dry-run/bounded CAS migration, epoch writer rollout·rollback gate, command 삭제/반복 rejoin fixture |

기존 `domain/global` 구조를 유지한다. 정확한 파일 수는 구현 시 기존 support class 재사용에 따라 줄일 수 있다. 별도 payment package·SDK·collection을 만들지 않는다.

### A.1 쿼리·index 계획

- 초기에는 기존 schema v4 index를 사용한다: `ux_trial_scope_user`, `ux_active_trial_candidate`, `ux_subject_link_claim`, `ix_subject_link_user_active`, `ux_grant_source_type`, `ix_group_claim_created`, `ux_active_reservation_subject`, `ux_active_session_subject`, `ux_active_create_command_user`.
- candidate alias는 `(benefitCode,keyVersion,candidate,active=true,retentionExpiresAt>asOf)`, current owner는 userId+active에 scope/retention filter, grant는 sourceType/sourceId/benefitCode, group은 claimId 집합으로 읽는다.
- projection 누락 조회는 정상 empty로 읽되 위 PENDING 정책을 적용한다. read path에 `deactivateExpiredMatches`, save/insert/findAndModify/delete/TTL 갱신을 연결하지 않는다.
- 순차 N+1 대신 claim/subject/session ID 목록을 bounded `$in` 조회한다. batch read 후 ID→entity map으로 합친다. 같은 ID의 duplicate/잘못된 연결은 503 불변식 오류다.
- staging/replica-set explain으로 인덱스 사용·응답시간을 확인한다. collection/index 추가는 초기 필수가 아니지만 §8의 기존 document field 추가와 versioned data migration은 필요하다. 추가 index가 필요하면 별도 manifest/migration으로 제안하며 기존 index 자동 drop/recreate 금지.
- 무료 조회에 payment schema v5나 `exam_owner_guards` 신설을 끼워 넣지 않는다. 현재 write-side guard를 읽어 표현하며, 현재 writer 범위 밖 다중 선택은 AMBIGUOUS로 표시한다.

## 14. 부록 B — 테스트와 완료 기준

| ID | 테스트 | 기대 |
| --- | --- | --- |
| T01 | VERIFIED/no Claim/no Grant 반복 GET | 1/ALLOWED, 모든 업무 collection count/content/version 불변 |
| T02 | same phone retained COMPLETED, 다른 user | 0/COMPLETED, source user/group/Session/결과 미노출 |
| T03 | 복수 keyVersion alias가 동일 Claim | 1건만 계산, 다른 Claim 충돌은 503 |
| T04 | A/H/C 각 정상 상태·잘못된 합계·비활성 Grant | 판정표 일치, 불변식 오류를 신규 수량으로 대체하지 않음 |
| T05 | RESERVED/PROPOSED/expiry 경과/release commit | 0/PENDING→1, GET이 release/purge 수행 안 함 |
| T06 | confirm OPEN, RETAKE_AVAILABLE, GRADING, COMPLETED | 신규 0과 재응시/완료를 정확히 분리 |
| T07 | same-phone owner 이전 전/중/후 | 전 null/PENDING·source 정보 없음, 후 0+retake; 미사용 이전은 1 |
| T08 | rejoin source ACTIVE Session vs target replacement | source로 hasInProgress=true 만들지 않음, target exact confirm 후 true |
| T09 | owner rebind GRADING pending·COMPLETED NOOP | 읽기에서 owner/Grant 복원/새 지급 없음 |
| T10 | GUEST/MEMBER 자격 부재·REVOKED·VERIFIED | read 권한은 동일, account_type만으로 지급/자격 추론 금지 |
| T11 | 2 benefit reader와 각 복수 독립 group fixture | code/unit/수량/진행 독립, 전역 first/total 없음; 새 실제 상품 추가 안 함 |
| T12 | 여러 source 중 current candidate 불일치·internal 선택 모호 | 과거 Grant 전부 합산/임의 REPLACEMENT ALLOWED 금지 |
| T13 | retained 만료 직전/동일 시각/직후 | reserve의 logical expiry 기준 일치, GET alias mutation 없음 |
| T14 | 조회와 reserve/confirm/cancel/owner event 동시 실행 | 완전한 이전 또는 이후 snapshot, Claim/grant 혼합 read 없음 |
| T15 | transaction retry·DB timeout·catalog 결손·limit 초과 | 503 result=null, 부분 benefits/0개 반환 금지 |
| T16 | JWT alg/issuer/aud/kid/signature/exp/iat/nbf/jti/sub/scope | 401/403 구분, expired+skew boundary, none/HS256/LC-only/workload 거절 |
| T17 | guest read, account_type 임의 header/body | 검증 sub만 actor, query/body userId 400·정보 미조회 |
| T18 | trusted JWKS 정상 rotation/unknown kid/outage | allowlisted key 검증, fail-closed 401/503·bounded refresh |
| T19 | public flag OFF/ON 및 network bypass | OFF 미노출, public→internal/direct/wrong role·method 실패 |
| T20 | 기존 internal eligibility/reserve/status/owner/attempt event | 기존 SigV4/테스트 principal·내부 DTO·HTTP 계약 유지 |
| T21 | 실제 production Controller·업무 span | tracing 정상·예외 종료, no-store·wrapper·privacy 검증 |
| T22 | 같은 snapshot의 pure read·reserve policy fixtures | INITIAL eligibility/REPLACEMENT 조건 일치, 기존 reserve 오류/멱등 회귀 |
| T23 | 동일 사용자 요청 제한·여러 ECS task | task별 429/Retry-After·bounded memory, 글로벌 합산 한도로 오인하지 않음·개인식별정보 metric 비노출 |
| T24 | 예기치 않은 event 지연과 프론트 갱신 | missing=UNKNOWN, 오래된 local 상태를 최신 barrier로 오인하지 않음 |
| T25 | target replacement confirm 후 reserve/confirm command 실제 삭제 | 7일 경과·삭제 전후 동일 진행/재응시 판정, query에서 command 재생성 없음 |
| T26 | A→B→C 반복 PHONE_REJOIN과 source Session/Reservation 잔존 | 이전 epoch는 새 사용자 진행으로 표시 안 함; 각 target confirm 뒤 같은 group/new Session 판정 |
| T27 | continuation 세 필드를 생략한 target의 후속 일반 replacement | 새 Reservation/Session에도 현재 epoch 저장, command 삭제 후 정상 조회 |
| T28 | PHONE_REJOIN duplicate/NOOP/pending/rollback·USER_MERGED | 실제 phone 이전에서만 epoch 증가, Guest merge는 보존, 기존 owner 정책 회귀 |
| T29 | reserve/confirm 대 owner rebind 동시 Transaction | link CAS write conflict/retry로 선형화, 이전 epoch Session을 새 owner 것으로 오판 안 함 |
| T30 | legacy 미이전/positive continuation/command 기반 backfill·증거 소실 | 검증된 대상만 CAS 이관, 모호한 대상은 activation blocker/503, GET write/무기한 PENDING 없음 |
| T31 | epoch 일치하나 Session PROPOSED/FAILED/다른 group·미래 epoch | epoch 하나만으로 진행 허용 안 함, exact state/연결 검사 |
| T32 | command TTL·fence cleanup 뒤 epoch 증빙 유지·보존 purge | TTL 독립, 기존 개인정보 보존 연장 없음, source 증빙을 target으로 rewrite 안 함 |
| T33 | projection 없는 Guest·알려진 인증 직후·진짜 reserve 대기 UX | 불명은 '확인할 수 없습니다', 처리 대기만 bounded polling, 완료 시간 약속 없음 |

write 없음 검증은 mock verify만으로 끝내지 않는다. replica-set 테스트에서 모든 관련 document/count/version/ledger 전후 비교와 Mongo command listener로 insert/update/delete/findAndModify/쓰기 aggregation이 발생하지 않음을 검증한다. read-only snapshot의 start/commitTransaction은 업무 write가 아니다. 자동 catalog initializer/테스트 setup write는 GET 계측 구간 밖에서 수행한다.

완료 기준:

1. 위 DTO·오류·상태표가 contract test/OpenAPI와 일치하고 선택 미정 placeholder가 없다.
2. 순수 reader/evaluator와 실제 production Controller 경로, JWT·internal 회귀가 통과한다.
3. `./gradlew clean test` 전체 통과. Docker/Testcontainers 미실행·skip이면 Mongo gate 미완료로 보고한다.
4. `git diff --check`, 변경 파일/Secret·개인정보 비노출 검사 통과. 결제·타 서버·Jira·배포 변경 없음.
5. Identity aud/read 발급 완료와 실제 staging token 검증, ALB/Lattice/SG·JWKS·snapshot, durable writer rollout 및 활성 legacy 귀속 coverage gate 증빙 후에만 운영 reader 활성화한다.
6. 배포되지 않은 상태의 코드 완료와 실제 프론트 사용 가능 상태를 구분해서 보고한다.

## 15. 부록 C — 조사 근거

Billing에서 확인한 파일:

- [ReserveService](../../src/main/java/web/tosunsaeng/billing/domain/reservation/application/ReserveService.java): VERIFIED/no candidates 거절, alias retention lookup, lazy issue·owner 검증, determineKind, unit 불변식.
- [ReservationLifecycleService](../../src/main/java/web/tosunsaeng/billing/domain/reservation/application/ReservationLifecycleService.java): confirm/cancel/expiry의 쓰기·멱등 흐름. GET에서 호출 금지.
- [ReservationProperties](../../src/main/java/web/tosunsaeng/billing/domain/reservation/config/ReservationProperties.java), [IdempotencyCommand](../../src/main/java/web/tosunsaeng/billing/domain/reservation/domain/entity/IdempotencyCommand.java): terminal command 기본 보존 7일과 lifecycle 종료 purgeAt. 이는 배포 실설정값 확인을 대신하지 않는다.
- [ReserveContinuationPolicy](../../src/main/java/web/tosunsaeng/billing/domain/reservation/application/ReserveContinuationPolicy.java), [Reservation](../../src/main/java/web/tosunsaeng/billing/domain/reservation/domain/entity/Reservation.java): continuation 생략 허용과 영속 Reservation의 연결 필드. [BillingSubjectLink](../../src/main/java/web/tosunsaeng/billing/domain/entitlement/trial/domain/entity/BillingSubjectLink.java)의 최신 ownerVersion/transition만으로 모든 과거 Session 생성 epoch를 복원할 수 없음.
- [PhoneContinuationService](../../src/main/java/web/tosunsaeng/billing/domain/reservation/application/PhoneContinuationService.java): owned PHONE_REJOIN group discovery, GRADING pending, 복수 continuation fail-closed.
- [OwnerRebindService](../../src/main/java/web/tosunsaeng/billing/domain/ownerrebind/application/OwnerRebindService.java): source→target CAS, active reservation/PROCESSING, phone GRADING pending·COMPLETED NOOP.
- [TrialEligibility](../../src/main/java/web/tosunsaeng/billing/domain/eligibility/trial/domain/entity/TrialEligibility.java): VERIFIED/REVOKED current projection. 없음을 미인증으로 확정할 별도 상태 없음.
- [BenefitDefinition](../../src/main/java/web/tosunsaeng/billing/domain/benefit/domain/entity/BenefitDefinition.java): code/displayName/EXAM_ATTEMPT/defaultGrantUnits=1. free reader 등록은 이 정책 검증을 유지.
- [BillingSubjectLinkRepository](../../src/main/java/web/tosunsaeng/billing/domain/entitlement/trial/repository/BillingSubjectLinkRepository.java), [TrialCandidateAliasRepository](../../src/main/java/web/tosunsaeng/billing/domain/entitlement/trial/repository/TrialCandidateAliasRepository.java): scoped current owner와 retained alias 조건, write 메서드 분리 필요.
- [AttemptGroupRepository](../../src/main/java/web/tosunsaeng/billing/domain/attempt/repository/AttemptGroupRepository.java), [AttemptSession](../../src/main/java/web/tosunsaeng/billing/domain/attempt/domain/entity/AttemptSession.java): group status/activeGuard와 Session 상태. Session 문서에 사용자 owner field가 직접 있는 구조는 아님.
- [MongoTransactionConfig](../../src/main/java/web/tosunsaeng/billing/global/config/mongodb/MongoTransactionConfig.java), [MongoTransactionExecutor](../../src/main/java/web/tosunsaeng/billing/global/infrastructure/mongodb/MongoTransactionExecutor.java): 현재 기본 manager/template이며 새 read snapshot 옵션을 명시적으로 검증해야 함.
- [SecurityConfig](../../src/main/java/web/tosunsaeng/billing/global/config/security/SecurityConfig.java), [InternalIngressProperties](../../src/main/java/web/tosunsaeng/billing/global/config/security/InternalIngressProperties.java): TEST/LATTICE 내부 경계와 latticeOnly 전제.
- [BillingMongoIndexInitializer](../../src/main/java/web/tosunsaeng/billing/global/infrastructure/mongodb/BillingMongoIndexInitializer.java): 기존 schema v4 query 지원 index.

Identity read-only 확인 파일(저장소 경계 밖 소스는 복사하지 않음):

- `identity/src/main/java/web/tosunsaeng/identity/global/security/jwt/JwtAccessTokenIssuer.java`: 단일 audience List, scope 문자열, account_type, RS256/kid/typ/iat/exp/jti.
- `identity/src/main/java/web/tosunsaeng/identity/global/response/BaseResponse.java`: isSuccess/code/message/result 공개 envelope.
- `identity/src/main/resources/application.yml`: 기본 audience는 tosunsaeng-learning-core; 실제 운영값 증거로 사용하지 않음.
- `identity/docs/codex/CURRENT_STATE.md`: PR #39 account_type 병합과 Billing audience/read 후속 인계. 문서의 테스트 결과를 이번 Billing 실행 결과로 인용하지 않는다.
