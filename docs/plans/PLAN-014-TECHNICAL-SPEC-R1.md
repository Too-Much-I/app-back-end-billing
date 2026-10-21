# PLAN-014 초대 공유 보상 — 기술 규격 R1

- 날짜: 2026-10-21 / 기준: [확정 계획](PLAN-014-invite-share-exam-pass-reward.md)
- 상태: 2026-10-21 사용자 요청으로 Billing 구현 및 로컬 검증 완료(전체1,904개/실패0·오류0·skip0). 기본 OFF이며 앱·실환경·배포·양측 검증 완료는 아님. 아래 설계 당시 미구현 표현은 역사적 범위로 읽는다.
- 조사: 현재 Billing 작업트리 정적 확인. 앱 develop `9e2dc16b51d9d9fb3bb8caa2d4cfefd602f05f14`의 Expo57.0.11/RN0.86.2 조사 결과를 사용한다.

## 1. 5줄 결론

1. GET/POST `/api/v1/rewards/invite-shares`를 추가한다. 앱 본문/수신자 정보 없이 사용자 JWT와 POST의 Idempotency-Key로 처리한다.
2. GET은 `billing:read`, POST는 기존 `billing:purchase`와 MEMBER를 요구하는 설계다. scope 이름과 달리 결제 구매는 필요 없다. 기존 purchase-account로 MEMBER/account 근거를 준비한다.
3. 계정 상태1개와 terminal receipt, 기존 grant/ledger를 사용한다. 최대3·지급1회·원장은 동일 ClientSession Transaction으로 확정한다.
4. iOS 외부 공유 앱 완료 / Android 외부 공유 앱 선택만 인정하고 링크 복사·비앱·분류 불명은 제외한다. 서버는 실제 전송 증명을 주장하지 않는다.
5. 초기 구현은 기본 OFF이며 schema·앱/LC 호환·경합 및 실기기 검증 후 별도 활성화한다. 기존 지급권은 발급 OFF로 사용 금지하지 않는다.

## 2. 반드시 읽어야 하는 내용

### 기존 구현을 재사용하는 경계

| 현재 코드에서 확인한 사실 | 적용 방식 |
| --- | --- |
| `VerifiedMemberEvidence`가 billing:purchase·account_type=MEMBER·aud/iss/sub/시간을 검증 | 지급 POST가 같은 권한 경계를 사용. 새 claim/scope/Identity API 없음 |
| `PaymentAccountService`가 purchase-account에서 account/ref/memberEvidence 및 lifecycle을 준비 | 초대 기능 진입 시 기존 API로 보강 가능. Store SDK 로그인/결제는 요구하지 않음 |
| payment 환경은 SANDBOX/PRODUCTION, paid exam 환경은 test/staging/production | 별도 의미 유지. 서버의 기존 환경 정합성 검사를 재사용하며 SANDBOX만 보고 test/staging을 추정하지 않음 |
| `PaidExamStore` guard의 nextGrantOrder가 복원권 지급 순서 관리 | INVITE도 같은 sequence를 원자적으로 사용. 새 counter로 FIFO를 나누지 않음 |
| memberGrants가 environment/ownerUserId/ACTIVE/availableUnits를 조회 후 grantOrder로 정렬 | INVITE grant를 같은 구조로 연결. validator·reader·예외 복원 경로는 추가 회귀 필요 |
| PublicIngressFilter가 현재 path마다 method/scope 하나만 결정 | 초대 경로의 GET/POST별 method/scope/feature gate 추가. 기존 route 동작 불변 |

앱 공유 준비 및 서버 보상은 분리한다. 신규 회원도 구매 없이 기존 purchase-account 호출로 준비할 수 있다. Identity가 billing:purchase를 발급하지 않는 상태는 권한을 우회하지 않고 기존 회원 토큰 갱신/발급 준비로 해결한다. 서버는 WITHDRAWN을 JWT나 evidence 보강으로 ACTIVE로 되돌리지 않는다.

## 3. 결정 사항과 구현 확인 경계

제품 정책 추가 선택은 없다. R1은 아래 API·저장·오류 설계를 제안 구현 기준으로 명시한다. 앱에 전달 후 현재 토큰/SDK와 대조하며, 실제 운영에 반영된 계약이라고 안내하지 않는다.

- schema revision 번호는 구현 시 최신 manifest 다음 값으로 배정한다. 지금 다른 작업의 revision을 선점하지 않는다.
- 현재 환경에서 purchase-account/memberEvidence가 켜져 있는지와 앱의 billing:purchase 발급은 활성화 전 확인한다.
- iOS activity/Android component 허용 매핑은 실기기에서 확인한 공유 앱만 지원 목록에 넣는다. 앱 패키지를 추측해 보상 허용하지 않는다. 복사/저장/인쇄/내부 커스텀 action과 분류 불명은 제외한다.
- 탈퇴 purge에서 누적 보상 근거 제거 시점은 기존 lifecycle 정책과 함께 검증한다. 상세 receipt 120일 정책을 금융·Claim·미완료 삭제 정책으로 확대하지 않는다.

## 4. 주요 위험

- 앱 callback을 변조할 수 있다. 수신자/최종 전송 인증·기기 attestation·추천 관계 저장은 추가하지 않는다. 서버가 보장하는 것은 인증된 계정의 누적3 및 원자적 지급이다.
- Android 기본 RN Share는 chooser 실행 직후 성공이므로 지급 근거로 금지한다. chooser의 외부 앱 선택을 따로 받아야 한다.
- sharedAction이라고 모두 허용하면 iOS 링크 복사에 지급할 수 있다. activity 분류와 성공을 함께 확인한다.
- 일반적인 JWT는 탈퇴 즉시 무효화되지 않는다. 기존 Identity→Billing lifecycle 전달 지연은 남으며 현재 ACTIVE라고 절대 보증하지 않는다. 전달된 deny와 지급은 같은 lifecycle 쓰기 경합으로 직렬화한다.
- 누적 counter 소실 시 0으로 초기화하면 3회 한도를 우회한다. 과거 지급 근거가 있는데 상태가 없거나 불일치하면 503/운영 확인, 자동 복구·재발급 금지.

## 5. API 규격

### 5.1 공통

- 공개 Billing connector·기존 사용자 JWT issuer/JWKS, aud=`tosunsaeng-billing`. 서비스 workload JWT는 사용할 수 없다.
- 인증 주체: 검증된 lowercase UUID sub. GET/POST 모두 MEMBER, Guest403. GET billing:read, POST billing:purchase. request의 사용자/환경/수량은 신뢰하지 않는다.
- query는 빈 query 포함 금지. body 없음(공백/빈 JSON도 body이므로400), unsupported method405. POST의 Idempotency-Key는 단일 header, lowercase UUID v4만 허용한다.
- GET에는 key 불필요, 와도 지급/상태 생성 없음. POST에는 플랫폼·수신자·공유 본문·clientSuccess를 받지 않는다. 앱 지급 조건은 클라이언트 책임이다.
- 응답은 기존 PublicResponse 및 Cache-Control:no-store, 기존 추적 header를 사용한다. 성공200. redirect 금지.
- 기존 public path와 동일한 raw path/port 검증을 사용하고 internal/다른 connector 우회 금지. GET/POST 요청별 scope로 WWW-Authenticate를 출력한다.

### 5.2 조회

`GET /api/v1/rewards/invite-shares`

```json
{"isSuccess":true,"code":"SUCCESS","message":"요청에 성공했습니다.","result":{"awardedCount":1,"limit":3,"remainingAwards":2}}
```

- 현재 시점 누적 지급 수, 0≤awardedCount≤3, remainingAwards=3-awardedCount. 보유권/사용 가능 시험 수가 아니다.
- 정상 신규 사용자로 lifecycle/account/지급 근거가 모두 없는 경우는 0/3/3을 반환 가능하나 collection/schema/DB 장애를 신규로 처리하지 않는다.
- WITHDRAWN이면403. 상태가 없는데 INVITE grant/receipt/ledger 연결 근거가 있으면503. 활성 계정의 정상 최초 보상 이전 상태만 0으로 처리한다.
- 조회는 account·grant·counter·ledger를 생성하거나 evidence를 갱신하지 않는다. 동일 읽기 snapshot에서 자료를 대조한다.

### 5.3 지급

`POST /api/v1/rewards/invite-shares`, `Idempotency-Key: <lowercase UUID v4>`, body 없음.

```json
{"isSuccess":true,"code":"SUCCESS","message":"요청에 성공했습니다.","result":{"operationId":"00000000-0000-4000-8000-000000000001","awardedQuantity":1,"awardedCount":2,"limit":3,"remainingAwards":1}}
```

- operationId는 header 값을 echo. 응답 수치는 해당 지급 Tx 당시 snapshot이다. 같은 key 성공은 같은 result를 반환하며 최신 수치가 필요하면 GET을 호출한다.
- 동일 계정에 최대3 grant를 만든다. grantId/ledgerId/수신자 정보는 public result에 넣지 않는다.
- 모든 권한/lifecycle/recovery 검증 후 기존 receipt를 먼저 확인하고, 그 다음 한도를 확인한다. 세 번째 성공 재호출도200이며 새 grant/sequence 증가 없음.
- 계정 연결/검증 MEMBER 증거가 없다면409 PURCHASE_ACCOUNT_REQUIRED. 앱은 기존 purchase-account 성공 뒤 원 key로 재개한다. 이 준비 실패에는 terminal receipt를 생성하지 않는다.
- payment account/ref는 기존 경로에서 준비하며 보상 POST가 그 서비스의 독립 Transaction을 중첩 호출하지 않는다.

### 5.4 오류·재시도

기존 public failure envelope(result=null)를 사용한다. 신규 code/문구는 구현 시 PublicResponse에 명시적으로 추가한다. 다른 API의 동일 code 문구는 변경하지 않는다.

| HTTP | code | 앱 처리 / receipt |
| --- | --- | --- |
| 400 | INVALID_REQUEST | key/body/query 형식 수정, 자동 재시도 없음 / 없음 |
| 401 | UNAUTHENTICATED | 기존 토큰 갱신 절차 후 같은 사용자·같은 key 재시도 / 없음 |
| 403 | FORBIDDEN | scope 부족/Guest, 기존 인증 경로로 해결 / 없음 |
| 403 | PAYMENT_ACCOUNT_WITHDRAWN | “탈퇴한 계정은 초대 보상을 받을 수 없습니다.” / 없음, 기존 receipt 접근보다 우선 |
| 404 | NOT_FOUND | flag OFF/다른 port 등 기존 ingress 정책. 새 지급 시도 중단 / 없음 |
| 405 | METHOD_NOT_ALLOWED | GET/POST 이외, Allow: GET, POST / 없음 |
| 409 | PURCHASE_ACCOUNT_REQUIRED | 기존 purchase-account로 준비 후 동일 key / 없음 |
| 409 | INVITE_REWARD_LIMIT_REACHED | “초대 보상은 계정당 최대 3회까지 받을 수 있습니다.” / terminal receipt 저장 |
| 429 | RATE_LIMITED | Retry-After 이후 같은 key / 없음 |
| 503 | BILLING_TEMPORARILY_UNAVAILABLE | Retry-After:5, 같은 key. 새 공유/새 key로 장애 우회 금지 / 불확정 commit은 먼저 조회 |

기존 rate limiter 방식과 설정을 재사용하되 초대 GET/POST 버킷을 구분한다. 응답 유실 복구를 무제한 호출하지 않으며 앱은 backoff와 중복 요청 방지를 사용한다. malformed/인증 실패를 영속 업무 실패로 기록하지 않는다. 동일 경로에 body가 없으므로 같은 key 다른 유효 payload는 현재 존재하지 않는다. 향후 body/version을 추가하면 별도 canonical 계약이 필요하다.

## 6. 저장 규격

서비스 환경은 아래 `environment`에 test/staging/production을 쓰고 paymentEnvironment는 서버 설정의 SANDBOX/PRODUCTION을 별도 검증한다. 사용자 입력으로 선택하지 않는다. 두 설정이 기존 배포 정책과 불일치하면 기동 거절한다.

### 6.1 신규 collection: invite_reward_accounts

- `_id`: 서버 UUID v4; `schemaVersion`: int64 1.
- `environment`, `userId`, `accountId`: 신뢰된 환경/sub/기존 payment account ID.
- `awardedCount`: int32 0~3; `version`: 양수 int64; `createdAt/updatedAt`: BSON UTC Date.
- unique `(environment,userId)`. TTL 없음. 첫 성공 지급 Transaction에서 생성한다.
- 지급 후 상태를 삭제·초기화하지 않는다. version 조건으로만 누적 증가하며 overflow와 범위 위반은 fail closed.

### 6.2 신규 collection: invite_reward_receipts

- `_id`: UUID v4; schemaVersion int64 1; environment/userId/accountId/operationId.
- `state`: AWARDED 또는 LIMIT_REACHED. PROCESSING/별도 worker/lease는 만들지 않는다.
- `grantId/ledgerEventId`: AWARDED에만 존재. LIMIT_REACHED에는 없음.
- `httpStatus`, `responseSnapshot` 또는 `errorCode`: 최초 terminal 결과 고정.
- `terminalAt`, `detailEligibleAt`: BSON Date. 후자는 terminalAt+120×24시간.
- unique `(environment,userId,operationId)`. 정리 조회 index `(detailEligibleAt,_id)`.
- 별도 자동 TTL은 초기에는 설치하지 않고 명시 maintenance로 eligible terminal 상세만 삭제한다. 정확히 detailEligibleAt부터 상세 재현 보장 종료. 오래된 상세가 남아 있으면 계속 재현한다.
- 상세 삭제 후 같은 key는 신규 요청으로 취급될 수 있으나 같은 계정 누적3은 유지한다. 최소 지급/원장 및 계정 상한 근거를 함께 지우지 않는다. 제한 거절 receipt는 상태를 변경하지 않는다.

### 6.3 기존 grant/ledger 확장

- grant: benefitCode=FREE_EXAM_ONCE, sourceType=INVITE, sourceId=성공 receipt의 서버 `_id`, ownerUserId=sub, environment=서비스환경, subjectRefId=null.
- grantOrder: 기존 paid guard의 nextGrantOrder에서 할당. 무기한, totalUnits=1/availableUnits=1/heldUnits=0/consumedUnits=0, ACTIVE, version1.
- sourceId를 client operationId 대신 서버 지급ID로 사용한다. 사용자 간 같은 key 충돌 및 상세120일 이후 재사용과 원장 unique를 혼동하지 않는다.
- INVITE partial unique index `(environment,sourceType,sourceId)`. 기존 MEMBER owner/grantOrder unique와 validator를 유지·확장한다.
- ledger: 기존 GRANTED event, grantSequence=1, aggregateId=grantId, INVITE 지급ID 기반 unique dedupe key. trialClaimId/subjectRefId는 null; trial 복원 factory를 재사용해 잘못된 출처를 만들지 않는다.
- 신규 전용 grant/ledger factory 및 schema 허용값 필요. 원장 필드 구조는 현재 EntitlementLedgerEntry를 유지하며 임의 결제 Purchase를 만들지 않는다.

index 이름은 신규 schema manifest에서 명시하고 startup DDL 금지. schema 변경은 현재 최신 revision 이후로 명시 maintenance를 통해 적용한다.

## 7. Transaction·경합·장애

1. 트랜잭션 밖에서 검증 JWT, 현재 flag 및 recovery fence ticket을 준비한다. key 정규형 검사. ID/now는 동일 실행 재시도에서 안정적으로 유지한다.
2. 기존 PaidExamTransactions의 단일 ClientSession에 reward repository도 묶는다. PaymentStore의 별도 세션을 열지 않는다.
3. 기존 recovery fence/account writer 규칙과 현재 ACTIVE lifecycle/account를 확인하고 동일 lifecycle row에 CAS 쓰기하여 탈퇴와 직렬화한다. memberEvidence/account 귀속과 JWT authorizationDeadline을 확인한다.
4. 동일 key receipt가 있으면 현재 접근 권한 확인 뒤 원 terminal 결과를 반환한다.
5. invite_reward_accounts를 조회한다. 누락인데 과거 지급 근거가 있으면503. 완전 최초이면 현재 account로 상태를 생성한다.
6. count=3이면 LIMIT_REACHED receipt를 저장·commit하고409 반환한다. 이 업무 결과를 exception으로 rollback하지 않는다.
7. count<3이면 version/count 조건으로1 증가. 같은 사용자의 기존 paid guard에서 nextGrantOrder를 할당한다. guard가 없으면 기존 MEMBER_ACCOUNT owner 형식으로 생성; 있으면 owner 검사 후 sequence/version만 갱신한다.
8. grant/ledger/AWARDED receipt를 기록한다. guard의 currentGroup/currentSession/pendingOperation/pendingReservation 등 시험 필드는 변경하지 않는다.
9. token deadline/recovery 검사를 기존 패턴대로 마친 뒤 commit 성공 확인 후200. CAS/unique 충돌은 제한된 기존 Tx 재시도 정책으로 수렴한다. 종료 시점 불명은 같은 key 재조회, 추가 보상 발명 금지.

초대 지급은 시험 생성 중에도 가능하다. guard 경합은 Tx 충돌로 재시도하며 시험 생성 상태를 종료하거나 guard를 지우지 않는다. 복원권 동시 지급도 같은 sequence를 사용한다. count 증가/sequence/grant/ledger/receipt 중 하나라도 실패하면 같은 Tx 전체 rollback한다.

WITHDRAWN 선행이면 지급 거절. 지급 commit 선행이면 기존 탈퇴 정리 규칙으로 권리를 처리하고 보상 지급 횟수를 취소하지 않는다. 지연 Identity 전달 전의 시간차는 기존 lifecycle 계약 한계로 남긴다.

## 8. 앱 연결 규격

| 단계 | 앱 저장/행동 |
| --- | --- |
| 화면 진입 | MEMBER 확인, 기존 purchase-account 준비 후 reward GET. 결제 SDK 구매 불필요 |
| 공유창 열기 전 | 로그인 사용자/서버환경과 UUID v4 key를 묶어 보존. 이 상태만으로 지급 재시도 금지 |
| 지급 조건 | iOS 외부 공유 activity 성공 / Android 외부 공유 component 선택. 복사/비앱/불명 제외 |
| 조건 충족 | 조건 충족 상태를 먼저 보존하고 같은 key로 POST, 동시 callback 중복 억제 |
| 200 | 완료 저장, operation별 안내 1회. reward GET와 entitlements 재조회 |
| 409 계정 준비 | purchase-account 보강 뒤 동일 key, 새 공유 요구 없음 |
| 409 한도 | terminal 완료, 추가 보상 안내 중단·조회 갱신 |
| 장애/응답 유실 | 보존된 조건충족 key만 재시도. 앱 종료 후 같은 로그인 사용자일 때 복구 |
| 로그아웃/계정 전환 | 이전 계정 요청 중단. 새 계정 JWT로 이전 key/공유 상태 전송 금지 |

조건 신호 후 대상앱 내부 취소는 허용하고 회수 API를 만들지 않는다. 조건 신호 영속화 전 앱이 종료되면 소급 성공을 추정하지 않는다. 여러 완료 공유를 동시에 처리하는 범용 큐는 만들지 않고 UI에서 한 보상 요청 확인 후 다음 보상 공유를 허용한다. 서버는 우회 동시요청에도3을 보장한다.

## 9. flag·사용권 연결·검증

설계 property: `app.rewards.invite-share.enabled`, env `INVITE_SHARE_REWARD_ENABLED`, 기본false. OFF는 신규 두 경로404, 기존 EXAM_PASS 소비·조회에는 영향 없음. OFF 동안 유실된 결과는 보존하고 재개 후 같은 key로 확인한다. OFF를 성공/실패 결과로 저장하지 않는다.

ON 조건: public connector, purchase-account/memberEvidence, paid foundation·MEMBER grant reader/resolver, 기존 recovery/schema readiness. 의존성이 부족하면 기동 거절하고 무료 trial 우회 경로로 지급하지 않는다. RC 외부 응답·webhook 성공 자체를 지급 근거로 삼지 않는다. 실제 paid admission 활성화 조합과 LC EXAM_PASS 대응은 통합 검증한다.

필수 검증은 PLAN014 I01~I14 및 I06-b/c/d에 추가한다.

- GET/POST 같은 path의 scope/method/flag/다른port/unknown field와 body 거절. GET에 쓰기 없음.
- 신규 무료 MEMBER: 실제 결제 없이 purchase-account 준비→보상→무료 시험 시작.
- count2에서 여러 요청, 동일 key20회, 복원권 지급과 동시, 생성 guard와 동시. 최대3·FIFO·guard 불변.
- 각 쓰기 실패 rollback, unknown commit, 세 번째 성공 유실→200 재현. 한도 terminal409 재현.
- receipt 정확120일 직전/경계/정리 후, state 누락+원장존재 fail closed, counter 무초기화.
- 탈퇴 양순서, 지연JWT, evidence 갱신, 계정 전환 후 저장 key 격리.
- INVITE 소비/cancel/expiry/replacement와 paid 전환에서 추가 복원 없음, 통합 reader 수량 일치.
- 실제 iOS/Android 공유창 닫기·앱 선택·선택후취소·복사·분류불명·앱 종료·중복 callback.

이번 문서 작성에서 테스트 실행 없음. 다음 구현은 P0 앱 wire 대조/최신 schema 확인 → Billing 단위·Mongo 구현 → 앱 연결 → staging E2E 순이다. 출시/판매 설정·운영 DB는 변경하지 않았다.

## 10. 조사 근거

- `src/main/java/web/tosunsaeng/billing/global/config/security/PublicIngressFilter.java`, `PublicSecurityConfig.java`, `PublicApiWriter.java`
- `src/main/java/web/tosunsaeng/billing/global/response/PublicResponse.java`
- `src/main/java/web/tosunsaeng/billing/domain/payment/application/VerifiedMemberEvidence.java`, `PaymentAccountService.java`
- `src/main/java/web/tosunsaeng/billing/domain/reservation/paid/PaidExamStore.java`, `PaidReservationService.java`, `MemberOperationRecovery.java`, `PaidExamSchema.java`
- `src/main/java/web/tosunsaeng/billing/domain/entitlement/domain/entity/EntitlementGrant.java`, `EntitlementLedgerEntry.java`

기존 코드의 존재가 신규 INVITE 실행 통과를 뜻하지 않는다. 기술 규격은 확인한 구조에 대한 변경 설계이며 제품 정책/LC wire/기존 A fixture는 변경하지 않는다.


### 프론트 인계용 exact wire

[프론트·Billing API 계약 R1](../contracts/INVITE-SHARE-REWARD-PUBLIC-API-R1.md)에 요청/응답 예시·필드·route별 오류 메시지·Retry-After·같은key/계정격리·공유판정·120일 자동재시도 경계를 정리했다. 신규 초대 route의 wire는 해당 문서를 기준으로 대조하며 기존 payment 오류 메시지는 변경하지 않는다. 아직 미구현·미배포이며 프론트 공동 검증 완료는 아니다.

## 11. 구현 반영 (2026-10-21)

[구현·운영 안내](../runbooks/PLAN-014-invite-share-rewards.md)를 따른다. 별도 additive schema `invite-share-reward-r1`을 추가했고 기존 paid r3/A·공동 fixture는 변경하지 않았다. quota/grant 소실을 issuance ledger만 남은 경우에도 판정하도록 INVITE의 GRANTED ledger에 environment/ownerUserId/sourceType/sourceId 최소 귀속과 partial 조회 index를 추가했다. 이는 기존 trial/결제 원장 의미나 공개 계약을 바꾸지 않는다.
