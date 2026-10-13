# PLAN-010 2차 — 결제 확인 접수·무료/유료 통합 조회

- 작성일: 2026-10-13 / 조사 브랜치: develop (`726b5ca`, PR #14 병합 포함)
- 상태: 2026-10-13 사용자 구현 요청에 따라 TMI-211 OFF 구현. 실행 결과·제약은 [구현 안내](../runbooks/TMI-211-public-sync-entitlement-query.md) 참조. 실제 provider/worker·배포/판매 활성화는 미완료다.
- Jira: [TMI-211](https://to-teacher.atlassian.net/browse/TMI-211), 상위 TMI-137, 해야 할 일·미할당(2026-10-13 생성/재조회).
- 선행: [TMI-210 코어](../runbooks/TMI-210-purchase-core.md), [PLAN-010](PLAN-010-purchase-ingestion-and-timeline.md), [purchase-r1](../contracts/PLAN-010-purchase-technical-spec-r1.md).

## 1. 5줄 결론

1. `POST /api/v1/payments/sync`로 앱의 결제 확인 요청을 durable 접수한다. 앱 요청 자체는 지급 증거가 아니다.
2. 기존 `GET /api/v1/entitlements`에 `paid`와 `examAvailability`를 추가하고 기존 무료 응답은 유지한다.
3. 실제 RevenueCat 검증·공통 worker는 다음 단계다. 이번에는 fake로 내부 연결을 검증하고 production sync 활성화는 막는다.
4. 무료·유료·진행 상태를 한 snapshot에서 읽는다. PLAN-011 판정기 미연결이면 종합 이용 가능성을 임의로 ALLOWED로 표시하지 않는다.
5. 새 두 기능은 기본 OFF, 독립 flag로 준비하며 실제 판매·자동 삭제·환불/paid reserve는 이번 범위가 아니다.

## 2. 사용자가 반드시 읽어야 하는 내용

이 단계는 **앱이 Billing에 “내 결제 확인해 줘”, “내 무료권과 유료 이용기간 보여 줘”라고 요청하는 통로**다. 스토어 구매창이나 RevenueCat SDK를 서버에서 구현하는 작업은 아니다.

- 새 확인 요청: DB 접수 성공 후 `202 PENDING`. 실제 provider 검증·지급은 공통 background 처리로 넘긴다.
- 이미 반영된 본인 거래: `200 APPLIED`. 만료/환불된 거래도 “확인 처리 완료”일 수 있으므로 APPLIED를 시험 이용 가능으로 해석하지 않는다.
- 통합 조회: 무료 수량과 유료 종료일을 따로 표시한다. 재응시를 UI에서 “1회 남음”으로 표현할 수 있지만 기존 `availableQuantity`에 중복 가산하지 않는다.
- 신규 handler를 구현했다고 실제 접수를 켜지 않는다. 처리 worker가 없는데 사용자에게 끝없는 PENDING만 반환하는 운영을 허용하지 않는다.
- 유료권 조회 가능과 paid 시험 시작 구현 완료는 다르다. 신규 종합 필드가 PENDING이어도 기존 무료 필드의 의미를 재정의하거나 기존 무료 reserve를 차단하지 않는다.

## 3. 사용자가 결정해야 하는 사항

새 가격·기간·환불·탈퇴 정책 선택은 없다. 사용자 구현 요청으로 아래 DTO/오류/flag/상태를 이번 OFF 구현 기준으로 채택했다. command exact schema는 PaymentSyncSchema와 구현 안내에 고정한다. 실제 provider wire·SDK 거래 매핑·운영 보존은 이 구현으로 검증됐다고 간주하지 않는다.

별도 승인/확인은 구현 뒤 운영 활성화에 필요하다: 실제 provider 거래 식별자 대응, SDK hint 확보 가능 여부, frontend 추가 필드 처리, worker와 미해결 건 운영/보존, PLAN-011/012 준비, 실제 ingress·JWT·부하 검증. 이번 요청으로 Jira 생성이나 외부 작업은 하지 않는다.

## 4. 주요 위험과 미확인 사항

| 확인된 코드 또는 공백 | 계획의 처리 |
| --- | --- |
| TMI-210은 명시 생성형 코어이며 검증 provider/worker 없음 | client hint를 PurchaseObservation으로 변환해 바로 지급하지 않음. production sync ON은 준비 전 기동 거절 |
| PaymentStore는 직접 ClientSession, 무료 조회는 Spring snapshot Transaction | paid reader는 무료 조회와 같은 session-bound MongoTemplate/DB 경로를 사용. PaymentStore.database()의 session 없는 find를 섞지 않음 |
| 무료 rate limiter는 task-local token bucket, ADR004 §5.3은 분산 합산 요구 | 확장 OFF는 기존60 burst/초당1 refill 유지. 확장 ON은 통합 route 하나에 분산 고정1분60회 적용하고 local limiter는 중복 적용하지 않음. 이관/429 차이를 fixture와 프론트 인계에 명시 |
| 공통 paid/free guard·resolver는 PLAN-011 범위 | 기본 unavailable 판정 port. 미연결은 PENDING/ADMISSION_NOT_READY, 시험 시작 가능으로 추정하지 않음 |
| sync hint와 RC 검증 거래 ID의 실제 대응 미검증 | 임의 purchaseToken/receipt 대체 금지. 실 fixture 매핑 성공 전 sync 활성화 금지 |
| source inbox와 sync command는 별개 durable 입력 | 각 입력의 pending을 정확히 해소. generic evidence effect collection 추가 금지 |
| TMI-210은 지급 후 환불을 case로만 보존 | 미해결 reversal이 해당 권리에 있으면 paid 상태 확인 불가. 실 revoke/reflow 구현 전 판매 활성화 금지 |

## 5. 구현 규격과 작업 순서

### 5.1 포함/제외

포함: sync strict decoder/controller/서비스·command repository/명시 schema, 본인 구매 lookup, 분산 sync rate limit, source 처리 결과를 command에 결속하는 내부 completion port와 fake, paid local reader/통합 DTO·snapshot, ingress/security/설정/오류/계약 테스트.

제외: 실제 RC/Apple/Google 호출, webhook 서명 구현, 1초 scheduler/lease worker, Store finish/consume, 새 purchase account 발급 정책, paid reserve/공통 guard 구현, 부분 환불/잔여권 종료/reflow, 205 실제 coverage·삭제 활성화, Identity/LC/프론트 저장소 수정. 외부 SDK 의존성을 추가하지 않는다.

### 5.2 sync 요청·인증

```http
POST /api/v1/payments/sync
Authorization: Bearer <Identity user access token>
Content-Type: application/json
Idempotency-Key: 11111111-1111-4111-8111-111111111111

{"store":"APP_STORE","transactionId":"test-store-transaction"}
```

- 기존 RS256/issuer/JWKS/kid·시간/sub 검증 재사용. audience `tosunsaeng-billing`, scope `billing:purchase`, 서명된 `account_type=MEMBER`. DB lifecycle/account ACTIVE와 현재 ref도 확인한다.
- sub는 Identity canonical UUID, 임의 UUIDv4 제한을 추가하지 않는다. operationId만 lowercase UUIDv4다. header 중복/쉼표 결합도 거절한다.
- body 정확 두 필드, store=APP_STORE/PLAY_STORE. transactionId는1~255 opaque 문자열, 제어문자/빈 값 거절, trim/대소문자 변환/숫자 coercion 없음. 최대16KiB, query 없음, duplicate/unknown/trailing JSON 거절.
- userId/appId/environment/가격/기간/account reference/receipt/token을 받지 않는다. environment/app은 배포의 store별 매핑에서 선택한다. hint는 검증되지 않은 Store transaction 식별자이며 Google purchaseToken 대용으로 쓰지 않는다.
- 구매 계정이 없으면 `409 PURCHASE_ACCOUNT_REQUIRED` 권장: 기존 purchase-account API를 먼저 호출하도록 안내한다. sync에서 계정/alias를 자동 생성하지 않는다. WITHDRAWN이면403, 무결성 불명/저장소 오류는503.
- 신규 접수 및 재조회 모두 사용자와 환경에 결속한다. 탈퇴 JWT의 기존 command 조회도403. endpoint가 OFF거나 잘못된 port면 기존 ingress의404 우선순위를 유지한다.

### 5.3 sync 처리·중복 계약

1. strict decode·JWT·분산 rate limit 후 서버 app/env로 untrusted lookup hash와 payload digest를 계산한다. digest는 store/app/env/거래 hash의 고정 형식이고 현재 DB 상태와 분리한다.
2. mandatory PAYMENT_INPUT fence와 기존 lifecycle write CAS를 같은 Tx에서 획득한다. ALL_BLOCKED는503; PURCHASE_BLOCKED에서는 입력 접수·기존 금융 조회만 가능하다.
3. `(environment,userId,operationId)` 기존 command가 있으면 digest를 대조한다. 불일치409, 일치하면 현재 결과를 조회하며 pending 증가/새 작업을 하지 않는다.
4. 신규 key라면 본인 account에 이미 결속된 동일 store/app/transaction의 검증 Purchase와 effect를 확인한다. 일치하는 기존 반영 결과만200. 다른 owner의 거래는 일반409로만 거절하며 해당 거래의 ID/금액/소유자 정보를 노출하지 않는다.
5. 미확인 거래는 command PENDING/counted=true 저장과 lifecycle.version/pendingEvidence+1을 같은 Tx로 commit한 후202. commit 불명은 동일 key 재시도로 수렴하고 저장 실패를202로 숨기지 않는다.
6. provider 검증은 Tx 밖에서 다음 worker가 수행한다. 이 단계의 HTTP 서비스는 provider를 호출하지 않는다. 앱의 hint만으로 PurchaseCore.receive/apply를 호출하지 않는다.
7. 후속 worker는 durable command에 연결된 observation key를 재사용한다. TMI-210 provider source와 command가 각각 counted일 수 있으며 별개 입력이다. provider source가 완료됐어도 command는 completion Tx가 끝나기 전까지 pending을 유지한다.
8. 내부 completion은 정확한 command/version·관측 연결·현재 epoch·본인 account/env/거래·Purchase/effect를 확인한 뒤 command terminal 및 pending-1을 함께 commit한다. source 반영과 completion 사이 장애는 pending이 남고 재시도로 마무리한다. 이를 하나의 Tx인 것처럼 보고하거나 카운터를 두 번 감소시키지 않는다.
9. withdrawal 이후 background 금융 사실 보존은 계속 가능하지만 신규권 지급은 금지한다. HTTP403 때문에 durable 금융 작업을 삭제하지 않는다. review 입력은 counted를 유지하며 임의 APPLIED로 바꾸지 않는다.

fast path도 terminal command를 기록해 동일 key/digest를 고정한다. source와 command를 잇는 completion API는 내부 Java port이며 외부 관리 endpoint를 만들지 않는다. 다른 command가 같은 거래를 확인해도 지급 effect는 하나다.

### 5.4 성공·대기·실패 응답

기존 PublicResponse `{isSuccess,code,message,result}`를 그대로 사용한다. 성공 message는 기존 함수를 재사용한다. 모든 응답 no-store, trace header, 민감정보 비노출.

202와 `Retry-After: 5`:

```json
{"isSuccess":true,"code":"SUCCESS","message":"요청에 성공했습니다.","result":{"operationId":"11111111-1111-4111-8111-111111111111","status":"PENDING","purchaseId":null,"purchaseStatus":null}}
```

200:

```json
{"isSuccess":true,"code":"SUCCESS","message":"요청에 성공했습니다.","result":{"operationId":"11111111-1111-4111-8111-111111111111","status":"APPLIED","purchaseId":"22222222-2222-4222-8222-222222222222","purchaseStatus":"VERIFIED"}}
```

4개 result 필드는 항상 존재한다. PENDING은 두 purchase 필드 null, APPLIED는 non-null. purchaseStatus=VERIFIED/REFUNDED/REVOKED; 검토 중인 reversal을 REFUNDED라고 추정하지 않는다. APPLIED 재조회는 snapshot의 현재 거래 상태를 반영하며 기간권을 재지급하지 않는다.

검토 상태의 public 표현은 **202 result에 `processingHint=CONTACT_SUPPORT`를 조건부 추가**한다. 단순 backoff에는 필드를 생략한다. 수동 검토를 단순 재시도로 해결된다고 안내하지 않는다. operationId·X-Trace-Id로 문의하고 provider 원문 제출을 요구하지 않는다. 이번 신규 OFF wire이며 실제 프론트 연동 검증은 별도다.

| HTTP | code / 처리 |
| --- | --- |
|400|INVALID_REQUEST|
|401|UNAUTHENTICATED, WWW-Authenticate: Bearer|
|403|FORBIDDEN 또는 PAYMENT_ACCOUNT_WITHDRAWN; 부족 scope 안내는 billing:purchase|
|404/405|NOT_FOUND / METHOD_NOT_ALLOWED, 활성 sync의 Allow: POST|
|409|IDEMPOTENCY_KEY_CONFLICT / PURCHASE_BINDING_CONFLICT / PURCHASE_ACCOUNT_REQUIRED(신규 제안)|
|413/415|PAYLOAD_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE(신규 decoder fixture에서 명시)|
|422|UNSUPPORTED_CONTRACT / UNSUPPORTED_PRODUCT — 검증으로 확인된 경우만. provider 미조회/빈 조회를 여기에 매핑하지 않음|
|429|RATE_LIMITED, 고정1분 bucket 종료까지 남은 정수초 ceil·최소1을 Retry-After로 제공|
|503|BILLING_TEMPORARILY_UNAVAILABLE, Retry-After:5|

실패는 `isSuccess=false,result=null`, 기존 고정 메시지 또는 위 신규 코드용 안전한 고정 메시지다. sync는12회/분/user/environment이며 기존 products60/account10과 bucket을 분리한다. limiter boolean 인자를 route enum으로 확장하되 기존 bucket key/제한을 변경하지 않는다. 통합 조회 ON에서는 ENTITLEMENTS 단일 bucket60회/분을 추가하고 무료/paid 별도 quota나 local limiter를 중복 부과하지 않는다. OFF에서는 기존 무료 limiter/429 Retry-After:5를 유지하고, ON에서는 bucket 종료까지 남은 초를 반환한다. 분산 limiter DB 장애는503이다. 모든 재조회도 quota에 포함된다. 프론트는 동일 key/body로 재시도하고 Retry-After를 준수한다.

### 5.5 command 저장·보존 권장안

`payment_sync_commands` 별도 source collection, generic payment_evidence_effects 없음. exact manifest는 `PaymentSyncSchema`의 sync-command-r1이며 이름·key 순서·옵션은 [구현 안내 §2](../runbooks/TMI-211-public-sync-entitlement-query.md)에 기록했다. 기존 payment core manifest는 변경하지 않는다.

필수: `_id`(UUIDv4), environment,userId,operationId,store,appId,transactionKey,lookupKeyVersion,payloadDigest,digestVersion(1L),accountId,lifecycleId,counted(bool),state,version(positive long),attempts(nonnegative long),createdAt/updatedAt(BSON Date), purchaseId?/errorCode?/sourceObservationKey? (명시null). state=PENDING/APPLIED/REJECTED/REVIEW_REQUIRED/BLOCKED_AUTH. lease/nextAttemptAt는 실제 worker 단계의 별도 schema 이관이다.

- hint 평문 저장 대신 동일 고정 HMAC lookup을 사용한다. 후속 provider는 검증된 account 범위에서 조회해 canonical 거래 hash를 대조한다. 실제 API가 raw hint 재조회 참조를 필요로 하면 암호화 envelope와 retention을 먼저 정리하고 adapter 단계에 추가한다. hash만으로 provider API를 호출할 수 있다고 가정하지 않는다.
- 인덱스 제안: `ux_payment_sync_operation(environment,userId,operationId)` unique; `ix_payment_sync_account_state(environment,accountId,state,createdAt)`; `ix_payment_sync_due(state,createdAt)`; `ix_payment_sync_source(environment,sourceObservationKey)` nonunique/partial 없이. 모두 ascending/simple, TTL 없음.
- terminal 재시도 기록은120일 보존 후 명시 정리하는 안을 권장한다. 미해결을 TTL로 지워 pending을 잃지 않는다. 해결/case 인계와120일 초과 예외 보존 운영을 다음 worker/205 단계에서 닫기 전 실접수 ON 금지. payment 금융5년 보존을 미검증 command 전체의 무기한 보존 근거로 사용하지 않는다.
- 초기 schema 설치는 writer 격리/ALL_BLOCKED 하의 별도 명시 작업, startup은 verify-only. 불일치나 Int32 version, unknown field를 거절한다. 기존 TMI-210 case manifest와 달라 아직 sync command를 해당 case에 그대로 복사하지 않는다.

### 5.6 통합 조회 DTO

`GET /api/v1/entitlements`: billing:read, 검증 sub, Guest/MEMBER 본인, query/body 없음. **기존 asOf/consistency/status/benefits 및 하위 필드는 그대로**다. status는 무료 projection 상태이며 종합 유료 상태가 아니다.

확장 OFF면 paid/examAvailability 모두 생략한다. ON이면 두 object는 필수이며 아래 nullable 필드를 null로 직렬화한다. DTO 전체에 NON_NULL을 적용해 기존 null 의미를 훼손하지 않는다.

| Object | 필드 / 타입 |
| --- | --- |
|paid|status: READY/PENDING; active: Boolean?; activeOfferCode: 5종 offer 또는null; activeEndsAt/continuousAccessEndsAt/nextStartsAt: UTC ISO Instant 또는null; hasPendingSync: boolean|
|examAvailability|canTakeExam: Boolean?; action/newAttempt/retake: ALLOWED/BLOCKED/PENDING; preferredSource: PAID/FREE_NEW/FREE_RETAKE 또는null; hasInProgress: Boolean?; reasonCodes: string[]|

2026-10-13 사용자 요청: 현재 응시 가능 여부를 간단히 사용할 `examAvailability.canTakeExam`을 추가한다. 이는 새 시험 시작 또는 승인된 재응시 중 가능한 경로가 있는지의 요약이며, 진행 중 시험의 존재나 결과 열람 가능 여부가 아니다. action=ALLOWED이면 true, BLOCKED이면 false, PENDING이면 null로만 파생한다. nullable 처리는 기존 확인불가 정책을 유지하는 기술 권장안이다. 별도 판단 로직을 만들지 않으며 상세 action/이유와 모순되지 않아야 한다. 공통 판정기 미연결은 null, 저장소 장애는 기존503. 조회 시점의 안내이며 실제 시작은 reserve에서 최종 판정한다.

reasonCodes 권장 literal: IN_PROGRESS,GRADING_IN_PROGRESS,RESERVATION_PENDING,NO_ENTITLEMENT,ELIGIBILITY_UNKNOWN,OWNER_LINK_UNRESOLVED,RECONCILIATION_PENDING,PAID_STATE_UNKNOWN,ADMISSION_NOT_READY. 기존 무료 Reason enum은 변경/재매핑하지 않고 추가 요약만 이 literal을 쓴다.

```json
{
  "paid":{"status":"READY","active":true,"activeOfferCode":"PREMIUM_7D","activeEndsAt":"2026-10-20T00:00:00Z","continuousAccessEndsAt":"2026-10-23T00:00:00Z","nextStartsAt":"2026-10-20T00:00:00Z","hasPendingSync":false},
  "examAvailability":{"canTakeExam":null,"action":"PENDING","newAttempt":"PENDING","retake":"PENDING","preferredSource":null,"hasInProgress":null,"reasonCodes":["ADMISSION_NOT_READY"]}
}
```

위는 **기존 result에 추가할 부분만**이며 전체 응답은 기존 PublicResponse/무료 필드를 포함한다. guard 미연결의 실제 2차 기본 예시다.

### 5.7 조회 판정·일관성

- asOf 하나를 snapshot 안에서 고정한다. 무료 readers와 paid/account/lifecycle/command/진행 정보는 같은 read-only Mongo transaction/session에서 읽는다. provider 호출·account 생성·cursor 갱신·기간 만료 write·복구 fence write를 하지 않는다. 별도 rate limit 관리와 도메인 무변경은 구분한다.
- 유효 paid는 본인 account·ACTIVE lifecycle, VERIFIED/ALLOWED Purchase와 not-revoked entitlement의 `[startsAt,endsAt)`로 판정한다. 탈퇴 기록이 확인되면 잔여 기간이 있어도 paid active=false이며 과거 다른 계정의 거래를 연결하지 않는다. 기존 무료 DTO는 그대로 유지한다.
- activeEndsAt은 현재 slot 끝, continuousAccessEndsAt은 바로 연결되는 유효 후속 slot의 끝(공백에서 중단), nextStartsAt은 asOf보다 뒤인 첫 유효 slot의 시작이다. 마지막 종료일만 max해서 공백까지 사용 가능으로 표시하지 않는다.
- 확인된 비활성이면 READY/false·active 관련3필드 null; scheduled만 있으면 nextStartsAt은 값 가능. PENDING이면 active 및 확정할 수 없는 시각/offer는 null이다.
- 진행 중 구매 sync가 있다고 기존 확인된 유효 paid를 무조건 PENDING으로 만들지 않는다. hasPendingSync는 본인 PENDING/REVIEW_REQUIRED/BLOCKED_AUTH 존재를 표시한다. 관련 reversal/owner/복구 상태 불명이 현재권 판단을 깨면 paid=PENDING. DB 오류/collection 부재/schema 불일치는503이며 false로 숨기지 않는다.
- 아직 구매 계정이 없는 정상 Guest/MEMBER는 자동 발급하지 않고, 해당 환경의 mapping/recovery 정합성을 확인한 경우에만 paid READY/false다. 복구 barrier/미매핑 case로 관련 범위가 불명하면 PENDING. ALL_BLOCKED에서는 통합 조회503; PURCHASE_BLOCKED는 이미 검증된 상태만 읽고 공통 admission은 확정하지 않는다.
- 초기 paid slot scan 상한1000+1 권장. 초과/중복 active/깨진 timeline은503, 잘라서 더 짧은 정상 종료일을 만들지 않는다. 이 상한은 소유권 수 제한이 아니라 초기 응답 계산 보호이며 실분포/부하 후 재검토한다.
- PLAN-011 공통 판정 port가 미준비면 examAvailability는 PENDING/ADMISSION_NOT_READY. 연결 후만 아래 표의 긍정 결과를 노출한다. 무료와 유료 각각의 readers가 따로 시험 시작 권한을 발급하지 않는다.

| 확인된 상태(011 판정기 연결 후) | 종합 표시 |
| --- | --- |
|유효 paid, 공통 진행/guard 이상 없음|ALLOWED, PAID; 무료 수량 유지|
|paid 비활성 확정, 무료 신규 가능|ALLOWED, FREE_NEW|
|신규 무료0, 승인된 기존 그룹 재응시 가능|ALLOWED, FREE_RETAKE; 수량0 유지, UI1회 표시는 가능|
|진행/GRADING/reserve 확인|새 시작 BLOCKED; 보유 기간/무료권은 그대로|
|모든 권리 없음 확정|BLOCKED, NO_ENTITLEMENT|
|paid/공통 owner/guard 불명|PENDING; 무료 차감 fallback 금지|
|paid 유효, 무료 자격만 불명|공통조건이 확인됐다면 paid 경로 허용 가능|

실제 시작의 최종 판정은 reserve다. 조회 이후 상태 변경에 의한 reserve 거절을 프론트가 처리한다. 전화번호 인증/구매 반영/시험 시작·중단·완료/재가입 반영 후 재조회한다. 로그·응답에 Claim/Grant/fingerprint/이전 user/provider key를 추가하지 않는다.

### 5.8 flags·배포 조건

후보 설정: `billing.payment.sync-enabled=false`, `billing.payment.entitlement-query-enabled=false`. 현재 PaymentProperties의 `billing.payment` prefix를 사용한다. 기존 public connector·무료 query enabled/catalog/account flag와 분리하고 신규 필드/조합은 fixture로 동결한다.

| 상태 | 동작 |
| --- | --- |
|sync OFF|404, 신규 body/DB 처리 없음|
|sync ON + provider/worker 필수 구성 없음|production 기동 거절. fake는 명시 test profile에서만 주입|
|paid 확장 OFF|기존 무료 wire/쿼리 경로 유지, paid schema 조회 안 함|
|paid 확장 ON + schema/snapshot 준비|local paid 표시; guard 미준비면 새 종합 판정 PENDING|
|paid 확장 ON + 무료 route OFF/미설치 필수 schema|잘못된 조합 기동 거절|

기존 ALB public allowlist에 sync만 추가하는 인프라 계획을 남기되 실제 AWS 변경은 별도다. internal port/다른 path/다른 서비스 JWT 우회 금지. 새로운 판매 허용 flag나 수동 증명 시스템은 만들지 않는다. 배포 gate는 기존 운영 절차로 확인한다.

### 5.9 구현 분할

1. **규격/fixture**: ADR/purchase-r1의 신규 후보 동기화, sync request/응답/오류·확장OFF/ON golden fixture, command manifest/보존/limiter 명칭 고정. 실제 hint 대응 불명은 실활성화 blocker로 유지.
2. **sync OFF 구현**: strict decode/security/분산12회 제한, command 접수/fast path/멱등/lifecycle fence, 내부 completion+fake 테스트. provider/worker 없이 production ON 거절.
3. **통합 reader OFF 구현**: 동일 snapshot의 paid reader, additive DTO, admission unavailable 기본 port, 기존 무료 fixture 그대로 통과.
4. **회귀/인계**: 실제 MVC/connector/auth, Mongo 경합/unknown commit, schema/manual rollout·프론트 인계. 전체 clean test/bootJar 후 코드검토·사용자 Git 정리. provider/worker/011/012/205는 후속 이슈로 분리.

## 6. 부록 — 테스트·완료 기준·근거

### 6.1 필수 테스트

- sync valid202/기존200, 같은 key 같은/다른 body, 서로 다른 user의 같은key, 다른account transaction 유출 금지, env/store/app 분리.
- JWT signature/aud/scope/시간/sub/account_type, Guest403, DB WITHDRAWN403, missing account409, OFF404·오포트404·405Allow·413·415·query/중복header 거절.
- sync 분산12회/통합조회ON 분산60회 및 동시 limit 경합·분 경계 Retry-After ceil; 기존 products/account 제한·무료OFF local limiter 회귀, ON 중복quota 없음.
- 접수/pending Tx rollback·unknown commit·source/command 이중입력 각각 해소·completion 중복/다른관측/epoch불일치·withdrawal/delete 양쪽 commit 순서.
- terminal금융조회에서 source 소실만으로 새 지급 금지; source 적용 뒤 command 완료 전 장애/재시도. review를 정상0 pending으로 해소하지 않음.
- 무료 old fixture OFF 완전 동일, ON 신규 nullable 필드·no-store, 기존 Guest projection PENDING 유지, 조회가 지급/계정생성/도메인write 하지 않음.
- paid 경계 start inclusive/end exclusive,5기간/stacking/공백/scheduled-only/만료/탈퇴/환불선행/미해결 reversal/빈 계정/미매핑불명/hasPendingSync.
- 무료·paid 동시 변경 때 같은 snapshot 증명(서로 다른 session을 사용하는 부정 fixture 포함), DB 장애/scan초과/깨진 timeline503, 처리 불명을0으로 숨기지 않음.
- guard 미연결에서 새종합 ALLOWED 없음, 연결 fake에서는 위 판정표 및 paid-first/free-preserve. 기존 무료/internal/Identity·LC JWT 회귀.
- canTakeExam은 ALLOWED/true·BLOCKED/false·PENDING/null exact 대응, 확장OFF 생략/ON null 명시, 새 시작 또는 재응시 가능의 OR 요약과 일치. 진행 중 시험 존재를 true로 오인하지 않음.
- metrics/log/trace에 user/transaction/raw body/키 없음. payment_sync INTERNAL span·entitlement_query 기존 span, baggage 없음.

### 6.2 완료 기준

이번 완료는 API/reader **기본 OFF 코드와 fake/Mongo/HTTP 회귀**다. 실제 결제 검증 성공/판매 가능한 서비스 완성을 뜻하지 않는다. 승인된 fixture·명시 schema·코드·문서 일치, 모든 신규 정책 후보 동결 또는 명시 blocker 처리, `./gradlew clean test bootJar` 및 링크/diff 검증, runtime fake 금지 확인이 필요하다.

실제 접수/판매 활성화 전에는 provider/SDK 매핑·공통 worker 재개/보존/알림·frontend 호환·011 guard/paid admission·012 refund/revoke·205 미매핑 coverage·staging E2E·운영 승인까지 별도로 확인한다.

### 6.3 조사 근거와 이번 검증

- [기존 무료 DTO](../../src/main/java/web/tosunsaeng/billing/domain/entitlement/dto/response/EntitlementQueryResponse.java), [조회 서비스](../../src/main/java/web/tosunsaeng/billing/domain/entitlement/application/EntitlementQueryService.java), [snapshot](../../src/main/java/web/tosunsaeng/billing/global/infrastructure/mongodb/EntitlementSnapshotExecutor.java).
- [무료 task-local limiter](../../src/main/java/web/tosunsaeng/billing/domain/entitlement/application/EntitlementQueryRateLimiter.java), [payment 분산 limiter](../../src/main/java/web/tosunsaeng/billing/domain/payment/application/PaymentRateLimiter.java).
- [현재 payment controller](../../src/main/java/web/tosunsaeng/billing/domain/payment/api/PaymentController.java), [ingress](../../src/main/java/web/tosunsaeng/billing/global/config/security/PublicIngressFilter.java), [writer](../../src/main/java/web/tosunsaeng/billing/global/config/security/PublicApiWriter.java), [PublicResponse](../../src/main/java/web/tosunsaeng/billing/global/response/PublicResponse.java).
- [TMI-210 코어](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCore.java), [manifest](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCoreSchema.java), [정본 ADR](../adr/ADR-004-fixed-term-premium-payment-contract.md), [011 계획](PLAN-011-paid-reservation-and-usage-evidence.md).
- 이번은 로컬 develop의 문서/코드 정적 대조와 계획 작성이다. 원격 PR 상태/실환경/provider/Jira는 조회·수정하지 않았고 테스트는 재실행하지 않았다. TMI-210 기존1,168통과 결과를 신규 API 검증으로 재사용하지 않는다.
