# LC–Billing 완료 시험 재답변 무차감 조회 — R1 초안

- 날짜: 2026-10-21 / revision: `lc-billing-feedback-reanswer-eligibility-r1-draft-5`
- 상태: 2026-10-21 사용자 「좋아 그렇게 하는 걸로 구현해줘」로 Billing 구현 승인. 본 draft-5에 따른 로컬 구현 범위는 [구현 안내](../runbooks/FEEDBACK-REANSWER-ELIGIBILITY.md)를 따른다. LC 공동 동결·구현·배포·활성화는 별도다. 아래 draft별 과거 미구현 기록은 작성 당시 상태다.
- 근거: LC `FEEDBACK_REANSWER_ENTITLEMENT_HANDOFF.md`(2026-10-21 읽기 확인), Billing `ExamAvailability`, `PaidQueryResponse`, `PaidExamQueryReader`.

## 1. 결론과 책임

현재 유효 유료권 또는 이미 발급돼 사용 가능한 무료권 1장 이상이면 **권리 보유 조건**을 충족한다. 조회는 발급·hold·차감·원장·operation/receipt를 생성하지 않는다. 완료 시험 여부, 문항, 현재 사용자 소유권, 삭제·탈퇴·해당 Session의 환불 차단, 기존 closure의 재답변 예외 적용은 LC 책임이다. Billing ALLOWED는 이 조건을 대신하지 않는다.

이미 소비한 그룹의 retake 가능 상태는 잔여 무료권이 아니다. `canTakeExam`은 newAttempt OR retake이므로 그대로 사용하지 않는다. 마지막 무료권 소비 후 잔여0이면 완료 직후에도 재답변을 거절한다. 새로운 Job 접수 전에 확인하되 이미 영속 접수된 같은 Job은 이후 잔여권 소진만으로 중단하지 않는다.

## 2. 서버 간 인증·경로 제안

| 항목 | 제안 |
| --- | --- |
| HTTP | `POST /internal/v1/entitlements/reanswer-eligibility` |
| 목적 audience | `tosunsaeng-billing:reanswer-eligibility:{env}` (단일 문자열) |
| issuer/sub/key | 기존 LC workload 계약의 환경별 issuer, `learning-core-service`, LC 전용 배포형 공개 JWK 신뢰 재사용 |
| 토큰 | 기존 RS256/정수 NumericDate/TTL60초/skew30초/매 HTTP 시도 fresh token·jti/원격 key fetch 금지 그대로 |
| transport | 기존 workload TLS connector와 LC 네트워크 경계, redirect 금지, no-store, 본문·응답 최대16KiB |
| 활성화 | 신규 조회 업무 flag 기본 OFF. 기존 LC receiver ON 및 snapshot reader/recovery readiness 필수. `app.billing.reanswer-eligibility.enabled` / `BILLING_REANSWER_ELIGIBILITY_ENABLED`, 기본false. 현재 통합 paid reader 빈의 foundation/recovery/admission 준비를 재사용 |

새 목적과 route를 **별도 확장**한다. 기존 A 계약의 여섯 route·fixtures bytes/hash를 고치거나 기존 reservation audience로 신규 조회를 허용하지 않는다. 사용자/Identity/다른 LC 목적 token으로 조회할 수 없다. 앱 토큰 전달, LC의 Billing DB 직접 조회, reserve→cancel 조회 대체는 제외한다.

기존 ingress 우선순위를 재사용한다: OFF/잘못된 local port는 빈404, 활성 경계에서 query(빈 `?` 포함)·인증 실패는 빈401, 신뢰된 토큰의 목적/method 인가 실패는 빈403. 정상 인증 후 body를 검사한다. 인증 실패에는 DB를 호출하지 않는다. 공개 ALB에 신규 internal route를 노출하지 않는다.

## 3. 요청·응답 제안

요청은 아래 정확한 단일 필드다. userId는 LC가 현재 인증·소유권 검사로 확인한 lowercase canonical UUID다. 사용자 입력을 그대로 전달하지 않는다. query·사용자 지정 identity header·Idempotency-Key는 사용하지 않는다. 조회 재시도는 최신 상태를 다시 읽으며 과거 결과를 replay하지 않는다.

```json
{"userId":"11111111-1111-4111-8111-111111111111"}
```

duplicate/unknown/missing/null field, trailing token, scalar coercion, 손상 UTF-8, 잘못된 UUID casing을 거절한다. Content-Type은 JSON, 기존 strict decoder 규칙을 적용한다.

정상 판정은 wrapper 없는 HTTP200과 아래 필드다. 예시 시각은 가상 값이다.

```json
{"userId":"11111111-1111-4111-8111-111111111111","decision":"ALLOWED","reason":"ENTITLEMENT_AVAILABLE","checkedAt":"2026-10-21T08:00:00.000Z","validUntil":"2026-10-21T08:00:05.000Z"}
```

| decision | reason | validUntil |
| --- | --- | --- |
| ALLOWED | ENTITLEMENT_AVAILABLE | checkedAt보다 큰 UTC 시각 |
| DENIED | ENTITLEMENT_INSUFFICIENT | null |
| DENIED | ACCOUNT_WITHDRAWN | null |
| PENDING | ENTITLEMENT_STATE_UNCONFIRMED | null |

모든 응답에서 userId exact echo, checkedAt 필수. timestamp는 UTC `Z`의 ISO-8601 문자열, fractional digits 0~9; LC는 Instant 정밀도로 해석하고 float 변환하지 않는다. unknown 필드/enum·잘못된 조합은 계약 오류이며 ALLOWED로 추론하지 않는다. paid 여부·잔여 수량·구매 ID는 보내지 않는다. LC의 공개 API URL/DTO는 변경하지 않는다.

malformed/oversize는400 `INVALID_REQUEST`로 제안한다. 현행 ReservationController/decoder와 동일하게 최대16KiB를 넘으면400이며 draft-2의400/413 표현을 정정한다. 저장소·snapshot 일시 장애는503 `BILLING_TEMPORARILY_UNAVAILABLE`, `Retry-After: 1`이다. 정확한 내부 오류 JSON은 §8을 따른다. PENDING 역시 부족 확정이 아니다.

앱 요청 형식·사용자 토큰 오류는 LC의 기존400/401/403을 유지한다. 정상 Billing 판정의 DENIED/ENTITLEMENT_INSUFFICIENT만 공개402, DENIED/ACCOUNT_WITHDRAWN만 공개401 ACCOUNT_WITHDRAWN으로 매핑한다. Billing timeout·일시 장애·PENDING은 공개503 BILLING_TEMPORARILY_UNAVAILABLE 및 Retry-After:1이다. LC→Billing 인증·주소·계약 오류(내부400/401/403/404·잘못된 응답)는 공개500 COMMON500과 운영 경보로 처리한다. Billing 내부401을 앱401로 그대로 전달하지 않는다. 서버 내부 원인 분류·기존 circuit·요청 내 자동 재시도 금지는 유지한다. upload-url에서의 성공은 submit 승인이 아니다.

## 4. Billing 판정 범위

한 read-only Mongo snapshot에서 현재 귀속·lifecycle·recovery readiness와 paid/free를 읽는다. 잠금·write fence를 추가해 조회를 금융 command로 만들지 않는다. 같은 snapshot의 일관성을 보장하되 응답 뒤까지 권리를 보장하지 않는다.

1. 현재 WITHDRAWN이면 DENIED. recovery/readiness 불명이나 귀속 불명은 PENDING/일시 장애로 구분한다.
2. 유료는 검증된 ACTIVE 권리이며 `startsAt <= checkedAt < endsAt`여야 한다. scheduled·환불/revoke 권리는 제외한다. MEMBER 근거와 현재 owner 확인이 필요하다.
3. 무료는 현재 사용자에게 귀속되고 사용 가능한 수량만 인정한다. hold/소비/삭제·lifecycle 차단 권리와 기존 그룹의 replacement 권리는 제외한다. **미발급 최초 무료권은 제외한다.** current VERIFIED·유효 Claim 부재 등으로 발급 가능함을 증명해도 재답변 자격으로 인정하지 않는다. 이미 발급된 사용 가능 무료권이 필요하며 조회에서 발급하지 않는다. 기존 public reader의 예상 신규 수량을 그대로 가져와 ALLOWED로 판정하지 않는다.
4. 복원·초대권은 기존 ACTIVE/owner/MEMBER 근거·availableUnits 기준을 재사용한다. 미래 만료 정책을 새로 추가하지 않는다.
5. paid/free 중 적어도 하나가 확실히 사용 가능하면 ALLOWED. 양쪽 모두 사용 불가가 확인돼야 INSUFFICIENT다. 한쪽이 불명이고 나머지가 없으면 PENDING. 이미 확인된 권리가 있는데 단순 hasPendingSync=true만으로 막지 않는다.

시험 생성 pending guard를 그대로 재답변 차단 조건으로 복사하지 않는다. 다만 권리의 hold·귀속을 판정할 수 없는 상태는 PENDING이다. 기존 public reader는 변경하지 않고 재답변용 predicate를 별도로 둔다.

## 5. 조회 직후 변경·영속 접수 경계

**최대5초 관측 기반 접수 경합은 사용자 수락 사항**이다. 서로 다른 동시 제출에서 여러 Job이 접수될 수 있으며1건 상한을 보장하지 않는다. 아래 LC 저장 필드 매핑과 서버 간 wire의 공동 확정은 별도로 남는다. Billing 조회와 LC Job 저장은 분산 Transaction이 아니므로 그 사이 무료권 소진·환불·탈퇴를 원자적으로 차단한다고 보장할 수 없다.

- Billing checkedAt은 snapshot 읽기를 시작하기 직전 고정한다. 무료 조건으로 허용하면 validUntil=checkedAt+5초, 유료만으로 허용하면 min(checkedAt+5초, 해당 유료권 endsAt)이다. 지나간 값은 허용 응답으로 재사용하지 않는다.
- LC는 요청 시작부터 경과한 monotonic 시간과 응답의 validUntil을 함께 검사한다. 최초 접수 Transaction의 최종 guard 검사 때 경과시간<5초 AND 현재시각<validUntil이어야 한다. 정확한 경계는 거절/재조회한다. 시계 동기화 전제가 필요하며 JWT skew30초를 자격 유효시간에 더하지 않는다. DB commit 지연까지 포함한 정확한5초 원자적 보장은 아니다.
- upload-url은 사전 확인, submit은 새 조회. 사용자/Session/문항이 다른 요청이나 새 녹음에 조회 결과를 캐시·공유하지 않는다.
- LC 접수 Transaction에서 현재 owner·삭제·탈퇴·Session 차단과 허용된 완료시험 재답변 경로를 재검사한다. 그 시점에 이미 관측한 deny가 있으면 Billing ALLOWED보다 deny가 우선이다.
- 새 컬렉션 없이 기존 `QuestionGradingJob`과 재답변 승인 증거를 동일 Transaction으로 저장한다. 기존 시험·문항·retryCount 식별자를 재사용하고 `firstUserSubmittedAt`을 최초 접수 시각으로 사용한다. 승인 유형·계약revision·조회 당시 사용자·checkedAt·validUntil을 추가한다(§10). JWT 원문/새 Billing 승인 receipt는 저장하지 않는다.
- 응답 유실 후 같은 요청은 현재 actor/소유권/lifecycle을 검사하고 기존 durable Job을 재현한다. Job이 없으면 새 조회한다. commit 불명은 먼저 기존 Job 여부를 확인하며 새 Job을 중복 생성하지 않는다.
- 접수 후 잔여권 소진·일반 기간 만료만으로 해당 Job의 dispatch/Callback을 막지 않는다. 삭제·탈퇴·해당 Session의 기존 환불/lifecycle deny는 계속 우선한다. 재답변 증거만으로 이를 우회하지 않는다. 다른 구매의 환불을 사용자 전체 Job 취소로 확대하지 않는다.
- submissionsClosedAt을 지우지 않는다. 완료시험의 승인된 재답변 Job만 별도 경로로 판단하며 교체된 미완료 시험·retry0 Summary 조건은 유지한다.

**수락된 한계:** 조회 뒤 무료권 소진·권리 변경 또는 아직 전달되지 않은 환불/탈퇴 deny 때문에 서로 다른 Job 여러 건이 접수될 수 있다. 같은 요청의 중복 Job 방지는 유지하지만 서로 다른 요청의 접수를1건으로 제한하지 않는다. 5초는 LC 최종 guard 검사의 관측 유효성 기준이며 DB commit 완료나 환불/탈퇴 이벤트 전파의 상한이 아니다. 이를 원자적으로 막기 위한 Billing durable 승인·fence/취소 전달 등 별도 쓰기 프로토콜은 추가하지 않는다. 이미 관측한 환불·삭제·탈퇴·소유권 deny 우선은 유지한다.

## 6. 공동 검증·남은 수락

| 범위 | 필수 사례 |
| --- | --- |
| 인증 | 새 목적 정상, 기존 목적/Identity/user token 거절, flag OFF·다른 port·query·strict body·no-store·DB 무호출 |
| 권리 | paid 활성/시작·종료 exact 경계, scheduled/환불 제외, free0/1/held, 초대·복원, 소비된 retake만 존재, 미발급trial만 있으면 INSUFFICIENT(다른 권리 없음이 확인된 경우)·발급 없음, 한쪽불명/다른쪽허용, WITHDRAWN |
| 무차감 | 반복 조회 전후 Claim/grant/ledger/reservation/receipt/guard 데이터 불변 |
| 접수 | upload만 성공 후 submit 시 소진, 조회후소진/만료/환불·탈퇴 전달 경합,5초 exact 경계, stale 응답/시계불명, 같은 Job 응답유실·unknown commit·경합, 서로 다른 동시 제출의 복수 Job 접수 허용(1건 상한 없음) |
| LC | 완료시험/미완료교체 구분, owner 변경·삭제, durable 재답변 dispatch/Callback, retry0 Summary 불변, 마지막 무료권 소비후402 |

미발급 최초 무료권 제외와 최대5초 관측 방식·복수 Job 접수 경합 수락은 확정됐으므로 재선택 대상으로 남기지 않는다. draft-3은 LC의 기존 Job 저장·공개 오류 제안을 수용하고 §8~10에 내부 오류·timeout/재시도·시각 검사·저장 매핑안을 구체화했다. draft-4의 시각 문자열 저장은 유지하고, 서버 간 오류의 일괄503 매핑은 사용자 정정에 따라 draft-5의 서버 연동 결함500/일시 장애503 구분으로 대체했다. 다음 LC 회신에서 신규 목적/route와 보완된 §8~10의 exact 규격을 공동 확인한다. 기존 fixture를 덮어쓰지 않고 별도 revision으로 golden bytes/HTTP와 시나리오를 제작해 독립 검증한다. 이후 구현·공동E2E·실환경 활성화를 구분한다.

이번 작성은 Billing 문서·작업기록만 변경했다. 제품 코드·DB·기존 계약/fixture·LC/Identity·Jira·commit/push·배포는 변경하지 않았다. 테스트 실행 대신 원문/현행 reader/auth 계약의 정적 대조와 diff 검사를 수행했다.

## 7. draft-2 변경 기록

2026-10-21 사용자 회신과 LC 인계 문서의 수락 사항을 대조했다. draft-1의 미발급trial 인정 제안 및 경합 시 “한 건” 표현을 위 정책으로 대체했다. 기존 환불·삭제·탈퇴·소유권 보호와 무차감·read-only 범위는 유지한다. 문서와 작업기록만 수정했으며 기존 A 계약/fixture 및 제품 구현·배포에는 변경이 없다.

## 8. draft-3 내부 오류 JSON·공개 매핑

현행 `InternalApiError` 4필드를 그대로 사용한다. 아래 code/message/retryable 조합은 exact이며 correlationId는 Billing이 응답마다 생성하는 lowercase UUID v4다. 사용자/Job/멱등성 식별자가 아니다. JSON property 순서는 의미 판정에 사용하지 않는다. wrapper·추가 필드·null·누락·중복은 불허한다. Content-Type은 `application/json`(UTF-8), 모든 응답 no-store다.

```json
{"code":"INVALID_REQUEST","message":"The request is invalid.","retryable":false,"correlationId":"11111111-1111-4111-8111-111111111111"}
```

```json
{"code":"BILLING_TEMPORARILY_UNAVAILABLE","message":"Billing is temporarily unavailable.","retryable":true,"correlationId":"22222222-2222-4222-8222-222222222222"}
```

| 내부 응답 | 적용 | LC 처리 |
| --- | --- | --- |
| 400 + INVALID_REQUEST | body 형식·크기·Content-Type 위반 | 공개500 COMMON500; 내부 계약 오류·자동 재시도 금지·운영 경보/차단 |
| 503 + BILLING_TEMPORARILY_UNAVAILABLE + Retry-After:1 | snapshot/저장소 일시 장애 | 공개503 BILLING_TEMPORARILY_UNAVAILABLE |
| 200 DENIED/ENTITLEMENT_INSUFFICIENT | 권리 부족 확정 | 공개402 ENTITLEMENT_INSUFFICIENT |
| 200 DENIED/ACCOUNT_WITHDRAWN | 탈퇴 확인 | 공개401 ACCOUNT_WITHDRAWN |
| 200 PENDING/ENTITLEMENT_STATE_UNCONFIRMED | 판정 불명 | 공개503 BILLING_TEMPORARILY_UNAVAILABLE |
| 빈401/403/404 | 인증/인가/ingress 차단 | 공개500 COMMON500; 내부 인증/인가/설정 장애 분류 유지·자동 재시도 금지·운영 경보 |

내부 DENIED/PENDING은 계속 §3의200 판정 DTO다. 사용자 탈퇴의 **공개401**과 workload 인증 실패의 **내부 빈401**을 혼동하지 않는다. 예상하지 않은 상태·body 조합/깨진JSON/응답 초과는 내부 계약 오류로 구분하고 앱에는 공개500 `COMMON500`으로 반환하고 운영 경보를 발생시킨다. 신규 Job/승인 증거는 생성하지 않는다. 내부 오류 상세·token/body 원문은 공개 응답에 노출하지 않는다. 일시 장애의 공개503에만 `Retry-After: 1`을 사용한다. 서버 연동 결함의 공개500에는 Retry-After를 부여하지 않으며, HTTP client의 요청 내 자동 재시도0회와 인증/계약 장애 circuit을 유지한다. 신규 endpoint는 기존 exception advice의 적용 대상이 아니므로 구현 시 명시적으로 연결하고 타 controller의 오류는 바꾸지 않는다.

## 9. 호출·재시도·시각 검증 제안

- 기존 LC 기본값인 connect timeout `PT2S`, HTTP request timeout `PT5S`를 재사용한다. Java HttpRequest의 timeout을 의미하며 단순 socket idle timeout이 아니다. 이 조회에서는 각각2초/5초보다 큰 설정을 허용하지 않는 안이다. 기존 예약 호출 설정은 변경하지 않는다.
- 하나의 upload-url/submit 시도에서 자격 조회는1회, HTTP client 내부 자동 재시도0회다. redirect 금지. timeout·일시 네트워크 장애·PENDING/계약상503이면 신규 Job 없이 공개503 BILLING_TEMPORARILY_UNAVAILABLE로 반환한다. 내부400/401/403/404·잘못된 응답 및 잘못된 endpoint/인증·신뢰 설정으로 확인된 연결 실패는 신규 Job 없이 공개500 COMMON500과 운영 경보로 처리한다. 단순 연결 실패만으로 잘못된 주소라고 단정하지 않고 확인된 원인으로 분류하며 원인 증거를 내부에 유지한다. 후속 앱 재요청은 기존 같은 Job 식별자를 유지한다. 기존 durable Job이 없다면 새 조회·새 JWT/jti를 사용한다. background reserve/status worker를 추가하지 않는다.
- 로컬에서 유효시간이 지난200을 받으면 같은 요청 안에서 재조회하지 않고 공개503·Retry-After:1로 종료한다. 후속 요청에서 새로 확인한다.5초를 늘리기 위해 같은 요청의 시작 시계를 재설정하지 않는다. 인증/계약 오류에 대한 자동 재시도는 없다.
- LC는 네트워크 호출 직전 monotonic 시작값을 기록한다. 응답 수신 시와 Job Transaction 최종 guard 검사 시 경과시간이 각각 `0 <= elapsed < 5초`여야 한다. 시계가 없거나 값이 비정상이면 승인하지 않는다.
- ALLOWED는 `checkedAt < validUntil <= checkedAt+5초`, userId exact match가 필수다. UTC Instant 파싱과 조합 검사를 통과한 뒤 응답 수신 시 LC wall clock에서 `checkedAt <= receivedAt < validUntil`, 최종 guard에서 `receivedAt <= guardNow < validUntil`도 확인한다. 단순 응답 지연/validUntil 만료/elapsed5초 도달은 신규접수를 보류(공개503)한다. 미래 checkedAt·wall clock 역행은 시각 전제 위반, 필드 손상·5초를 넘긴 유효구간은 계약 오류로 구분하여 공개500 COMMON500 및 운영 경보/시각 동기화 점검 대상으로 처리한다. 어떤 경우에도 신규 승인으로 사용하지 않는다.
- JWT의30초 skew는 이 검사에 추가하지 않는다. 서버 시계가 어긋나 정상 호출을 보류할 수 있으므로 양측 시각 동기화는 활성화 gate다. wall clock만으로 양측 시각 오차가 없음을 입증하지 않으며, 이것이 분산 원자성을 제공한다는 뜻도 아니다.
- 유료만으로 허용 시 Billing이 validUntil을 해당 유료권 endsAt 이하로 제한한다. LC에는 endsAt을 별도로 전달하지 않으므로 이 제한의 계산은 Billing fixture에서 검증한다. DENIED/PENDING은 validUntil=null이며 승인 증거로 저장하지 않는다.
- §5의 “경계는 거절/재조회”는 현재 시도 거절 후 **후속 요청의 새 조회**를 뜻한다. 동일 요청 안에서 무제한 조회하는 구현을 허용하지 않는다.

## 10. LC 기존 Job 저장안 수용 및 검증

새 컬렉션을 추가하지 않는 LC 제안을 수용한다. 기존 시험·문항·retryCount 및 same-Job 유일성 규칙을 그대로 사용한다. 아래는 승인 증거의 논리 필드이며 LC의 정확한 Java/BSON 이름은 구현 전 매핑표로 확정한다.

| 값 | 저장 의미 |
| --- | --- |
| 승인 유형 | `COMPLETED_EXAM_REANSWER_ENTITLEMENT` 제안. 일반 제출/retry0와 구분 |
| 계약 revision | 공동 동결된 정확한 revision. draft를 최종 revision으로 위장하지 않음 |
| 조회 당시 사용자 | Billing 응답 userId, LC가 접수한 현재 사용자와 동일 |
| checkedAt / validUntil | 수락한 ALLOWED 응답의 검증된 UTC ISO-8601 문자열을 **BSON string**으로 그대로 저장. 소수초0~9자리 보존, BSON Date 변환 금지 |
| 접수 시각 | 기존 `firstUserSubmittedAt`과 기존 저장 타입/정밀도 유지. 중복 재시도에서 덮어쓰지 않음 |

시각 검사는 저장 전과 읽은 뒤 모두 문자열을 `Instant`로 파싱해 수행한다. 문자열 사전순 비교, float epoch 변환, BSON Date 왕복, millisecond 절삭/반올림으로 나노초를 잃지 않는다. `firstUserSubmittedAt`을 나노초 승인값의 대체값으로 사용하지 않으며 해당 필드의 밀리초 저장값만으로 최초 guard 검사의 나노초 경계를 재구성하지 않는다. 최초 guard 검사는 메모리의 원 정밀도로 수행하고, 이후 같은 Job은 저장한 승인 증거를 사용한다.

Job과 위 증거를 같은 Transaction으로 저장하고 최초 접수 이후 불변으로 유지한다. 승인 필드가 일부만 있거나 기존 Job에 증거가 없으면 성공증거를 발명·소급 보강하지 않는다. 증거 없는 기존 Job의 기존 처리 경로는 유지하며 새 재답변 예외 권한을 자동 부여하지 않는다. firstUserSubmittedAt이 기존 규칙상 다른 값으로 이미 고정된 Job은 신규 승인인 것처럼 재사용하지 않는다.

같은 Job replay/dispatch/Callback은 저장 증거를 사용하고 이미 지난 validUntil이나 잔여권 소진만으로 재거절하지 않는다. validUntil은 **최초 신규 접수용**이며 후속 채점 마감이 아니다. 현재 소유권·삭제·탈퇴·해당 Session의 기존 환불 deny 검사는 계속 적용한다. 조회 당시 사용자 snapshot을 현재 owner로 덮어쓰거나 소유권 변경만으로 재승인하지 않는다.

추가 검증: exact 오류 JSON/message·correlationId·빈 ingress 응답 구분, 앱 자체400/401/403 유지, 내부400/401/403/404·잘못된 응답→공개500 COMMON500/운영 경보/Retry-After 없음, timeout·일시 장애·PENDING→공개503, 원인별 내부 분류/자동재시도 금지, 정상200 DENIED의 공개402/401 구분, timeout 경계/자동재시도0/후속요청 fresh token, 미래 checkedAt·wall clock 역행·elapsed5초·validUntil exact 경계, checkedAt/validUntil의 BSON string 저장·읽기 왕복에서 `.123456789Z` 보존 및 ±1ns 경계, 기존 firstUserSubmittedAt 저장 타입 유지, Job+증거 원자성/부분증거 거절/firstUserSubmittedAt 불변/증거 없는 legacy, 동일 Job 재현과 서로 다른 Job 복수 접수 허용.

## 11. draft-3 변경 및 남은 범위

2026-10-21 LC 회신의 QuestionGradingJob 저장·공개 오류 매핑을 반영했다. 기존 Billing 오류 DTO/문구, LC timeout 기본값과 firstUserSubmittedAt 필드를 정적 확인했다. §8~10은 공동 검토용 exact 제안이며 전체 wire 공동 수락이나 실행 검증 완료로 표시하지 않는다. 코드·DB·fixture·LC 저장소·commit/push·배포는 변경하지 않았다.

## 12. draft-4 보완 기록 (오류 일괄503 제안은 draft-5에서 대체)

2026-10-21 LC 회신에 따라 checkedAt/validUntil은 검증된 원 문자열을 BSON string으로 저장하도록 명시했다. 기존 firstUserSubmittedAt의 타입·의미는 유지한다. 당시 내부400/401/403/404 및 잘못된 응답을 일괄 공개503으로 제안했으나 이 매핑은 철회됐으며 현행 기준은 §3/8/9와 아래 draft-5 기록이다. 정상200의 권리 부족/탈퇴 판정만 공개402/401로 매핑한다.

두 보완을 본문·매핑표·검증 목록에 함께 반영했다. 전체 공동 동결 또는 fixture/제품 구현 완료로 표시하지 않는다. 문서 정합성 및 diff 검사만 수행했으며 제품 코드·DB·기존 fixture·LC/Identity·commit/push·배포는 변경하지 않았다.

## 13. draft-5 공개 오류 원인별 구분 — 사용자 정정 반영

| 원인 | LC 앱 응답 |
| --- | --- |
| 앱 요청 형식·사용자 토큰 오류 | 기존400/401/403 유지 |
| 정상 판정의 응시권 부족 확정 | 402 ENTITLEMENT_INSUFFICIENT |
| 정상 판정의 탈퇴 확인 | 401 ACCOUNT_WITHDRAWN |
| Billing timeout·일시 장애·판정 보류 | 503 BILLING_TEMPORARILY_UNAVAILABLE, Retry-After:1 |
| LC→Billing 인증·주소·계약 오류 | 500 COMMON500 + 운영 경보, Retry-After 없음 |

2026-10-21 사용자 정정으로 draft-4의 서버 간 결함 일괄503 제안을 대체했다. Billing 내부401은 LC 서버 간 인증 문제이며 사용자 토큰 재발급/로그아웃 유도로 이어질 앱401로 전파하지 않는다. COMMON500은 LC 현행 ErrorStatus의 기존 코드임을 읽기 확인했다. 운영 경보에는 원인 category·상태·안전한 correlationId만 사용하고 토큰·원문·사용자 식별정보를 노출하지 않는다. 기존 경보/circuit을 재사용하며 새로운 복구 시스템을 추가하지 않는다.

시각 문자열 저장·기존 QuestionGradingJob/firstUserSubmittedAt·무차감·5초 관측 한계는 유지한다. 공동 인증/응답 규격과 fixture 확정 및 제품 구현은 여전히 후속이다. 이번 수정은 문서·작업기록만이며 코드·DB·fixture·commit/push·배포 변경 없음.
