# 초대 공유 보상 — 프론트·Billing API 계약 R1

- 날짜: 2026-10-21 / revision: `invite-share-reward-public-api-r1`
- 상태: 2026-10-21 사용자 요청으로 Billing 구현 및 로컬 검증 완료(전체1,904개/실패0·오류0·skip0). 기본 OFF이며 앱·실환경·배포·양측 검증 완료는 아님. 아래 설계 당시 미구현 표현은 역사적 범위로 읽는다.
- 근거: [PLAN-014](../plans/PLAN-014-invite-share-exam-pass-reward.md), [기술 규격 R1](../plans/PLAN-014-TECHNICAL-SPEC-R1.md).

## 1. 5줄 결론

1. 결제 권한이 있는 MEMBER가 공유 앱을 통해 초대하면 무료 모의고사권 1장, 계정당 누적 최대3장이다. 실제 결제 이력·친구 가입은 필요 없다.
2. GET/POST `/api/v1/rewards/invite-shares`로 남은 보상 조회와 지급 요청을 제공한다. 두 API는 아직 배포되지 않았다.
3. iOS 공유 앱 완료 / Android 공유 앱 선택 신호만 인정한다. 링크 복사·공유창만 닫기는 제외, 조건신호 뒤 대상앱 내부 취소 위험은 허용한다.
4. POST는 body 없이 Idempotency-Key를 보내며 유실 시 같은 계정·같은 key로 재시도한다. server commit 확인 전 앱이 무료권을 늘리지 않는다.
5. 요청 terminal 결과는120일 재현 보장, 계정 누적3 근거는 별도 유지한다. 기능 OFF여도 이미 받은 무료권은 기존 경로로 사용할 수 있다.

## 2. 프론트가 반드시 알아야 하는 내용

- 아래 HTTP path는 배포 환경의 Billing public base URL에 붙인다. 이 문서는 host를 새로 지정하지 않는다.
- 인증은 기존 Identity 사용자 access token. audience=`tosunsaeng-billing`, `account_type=MEMBER`.
- GET: `billing:read`. POST: `billing:purchase`. 지급 권한이 없는 MEMBER에게 보상 가능 안내를 하지 않는다. 서버 최종 거절을 따른다.
- 사용자는 검증된 JWT sub로 결정한다. userId/수신자/환경/지급수량을 body/query에 넣지 않는다.
- 초대 화면 진입 시 기존 `POST /api/v1/payments/purchase-account`로 계정/MEMBER 증거 준비 가능. 이 호출은 실제 구매가 아니며 보상을 위해 RC 로그인이나 구매를 요구하지 않는다.
- 보상 횟수와 현재 이용권 수는 다르다. 보유·사용 가능 권리는 기존 `GET /api/v1/entitlements`로 확인한다.
- iOS/Android 모두 기본 RN Share의 단순 성공값만으로 공통 처리하지 않는다. 특히 Android 기본 Share는 창 실행 직후 성공하므로 대상 선택 callback을 연결한다.

## 3. 확정 범위와 남은 확인

제품 기준·두 route·same-key 복구는 승인된 방향이다. 본 문서의 exact 필드/오류/상태표를 프론트와 대조한 뒤 양측 구현에 사용한다. 새 제품 정책 선택은 요구하지 않는다.

프론트 회신 항목: 현재 MEMBER 토큰의 두 scope 사용 가능 여부, 외부 공유앱 activity/component 판별 방법, 아래 응답/오류 분기·키 보존/계정전환 처리 가능 여부. 실제 앱 선택/완료/복사/취소 callback은 실기기로 검증한다. 외부 메시지를 이 문서 작성 과정에서 발송하지 않았다.

## 4. 위험과 한계

서버는 수신자·전송 성공을 검증하지 않는다. 같은 친구/자기 공유/클라이언트 직접 요청의 위험을 수용하되 서버의 누적3·멱등 지급은 강제한다. 이전에 발급된 JWT와 lifecycle 전달 사이의 지연은 기존 계약 한계이며 수신한 탈퇴 deny가 우선한다. 미배포404를 이용권 없음이나 이미 지급 완료로 해석하지 않는다.

## 5. 공통 HTTP 규칙

- HTTPS public connector, 기존 사용자 JWT 검증 및 public ingress 규칙.
- 허용 method: GET/POST. 이 경로의 그 외 method405와 `Allow: GET, POST`.
- 두 method 모두 body 없음. `{}`/공백 body도400. query도 금지(빈 `?` 포함).
- `Authorization: Bearer <access token>`. POST는 `Idempotency-Key` 단일 header 필수, lowercase UUID v4. 누락/중복/다른 버전/대문자400. GET에는 key가 필요 없다.
- JSON envelope는 `isSuccess`, `code`, `message`, `result`. 숫자는 정수이며 null이나 문자열 숫자로 보내지 않는다. 필드 순서는 의미 없음.
- 응답 `Content-Type: application/json`, `Cache-Control: no-store`, 기존 `X-Trace-Id`. token·수신자·공유본문·개인정보를 로그에 남기지 않는다.
- 기능 OFF/잘못된 connector는 기존 ingress에 따라404. 해당 route가 공개된 경우 인증/권한→형식→현재 lifecycle/account→기존 receipt→한도 순서다. POST 계정 준비 문제는 기존 receipt를 무권한으로 공개하는 근거가 아니다.
- 클라이언트는 message가 아닌 HTTP/code로 분기한다. 알 수 없는 오류는 성공으로 처리하거나 새 key로 우회하지 않고 확인 불가로 표시한다.

### 5.1 GET — 남은 보상 횟수

```http
GET /api/v1/rewards/invite-shares
Authorization: Bearer <access token>
```

HTTP200:

```json
{
  "isSuccess": true,
  "code": "SUCCESS",
  "message": "요청에 성공했습니다.",
  "result": {
    "awardedCount": 1,
    "limit": 3,
    "remainingAwards": 2
  }
}
```

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| awardedCount | integer 0~3 | 현재 누적 초대 보상 지급 수. 사용해도 감소하지 않음 |
| limit | integer, 항상3 | 계정별 총 지급 한도 |
| remainingAwards | integer 0~3 | limit-awardedCount. 현재 무료권 수가 아님 |

GET은 지급/계정 생성 등 쓰기를 하지 않는다. 정상 신규 회원은0/3/3. DB/schema/근거 불명은503이지0이 아니다. WITHDRAWN은403. GET 성공이 POST의 최종 권한·현재 상태를 예약하지 않는다.

### 5.2 POST — 보상 요청

```http
POST /api/v1/rewards/invite-shares
Authorization: Bearer <access token>
Idempotency-Key: 00000000-0000-4000-8000-000000000001
```

Body 없음. 예시 key는 문서용이며 앱은 시도별로 새 UUID를 생성한다.

최초 지급 및 같은 요청 성공 재현 모두 HTTP200:

```json
{
  "isSuccess": true,
  "code": "SUCCESS",
  "message": "요청에 성공했습니다.",
  "result": {
    "operationId": "00000000-0000-4000-8000-000000000001",
    "awardedQuantity": 1,
    "awardedCount": 2,
    "limit": 3,
    "remainingAwards": 1
  }
}
```

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| operationId | lowercase UUID v4 string | 요청 header key와 동일 |
| awardedQuantity | integer, 항상1 | 이 operation이 지급한 수량. 재시도로 새1장이 생겼다는 뜻 아님 |
| awardedCount/limit/remainingAwards | GET과 같은 타입 | 해당 operation 최초 commit 당시 snapshot |

응답은 grantId/내부 원장 ID를 노출하지 않는다. 같은 key 재현 시 최신 count로 응답을 바꾸지 않는다. 최신 count는GET, 현재 권리는entitlements를 재조회한다. 3번째 성공 응답 유실 뒤 같은 key 재시도는200, 새로운4번째 key는409다. 포괄적인202 PENDING이나 새 polling API는 추가하지 않는다.

### 5.3 오류 계약

다음 메시지는 신규 초대 route의 문구다. 기존 payment route의 메시지를 일괄 변경하지 않는다.

| HTTP | code | message | 후속 |
| --- | --- | --- | --- |
| 400 | INVALID_REQUEST | 올바르지 않은 요청입니다. | 요청 형식 수정. 자동 재시도 금지 |
| 401 | UNAUTHENTICATED | 인증이 필요합니다. | 기존 토큰 갱신 절차 후 같은 사용자/key로 제한적 재시도 |
| 403 | FORBIDDEN | 접근 권한이 없습니다. | Guest/scope부족. 준비/로그인 안내, 반복 재시도 금지 |
| 403 | PAYMENT_ACCOUNT_WITHDRAWN | 탈퇴한 계정은 초대 보상을 받을 수 없습니다. | 중단. 보존 key를 다른 계정으로 재사용 금지 |
| 404 | NOT_FOUND | 요청한 경로를 찾을 수 없습니다. | 미배포/OFF 가능, 보상 기능 사용 중단. pending 결과를 성공/실패로 단정하지 않음 |
| 405 | METHOD_NOT_ALLOWED | 허용하지 않는 요청 방식입니다. | method 수정 |
| 409 | PURCHASE_ACCOUNT_REQUIRED | 구매 계정 연결을 먼저 완료해 주세요. | POST만. 기존 purchase-account 보강 후 같은 key 재시도 |
| 409 | INVITE_REWARD_LIMIT_REACHED | 초대 보상은 계정당 최대 3회까지 받을 수 있습니다. | POST만. 추가 보상 종료·GET 갱신 |
| 429 | RATE_LIMITED | 잠시 후 다시 시도해 주세요. | Retry-After 이후 동일 key |
| 503 | BILLING_TEMPORARILY_UNAVAILABLE | 초대 보상 정보를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요. | Retry-After 이후 동일 key. 새 key 금지 |

오류 공통 예시:

```json
{
  "isSuccess": false,
  "code": "INVITE_REWARD_LIMIT_REACHED",
  "message": "초대 보상은 계정당 최대 3회까지 받을 수 있습니다.",
  "result": null
}
```

- 429의 Retry-After는 서버가 계산한 양수 정수초. 503은5초. 이 두 상태에만 backoff를 결합해 대기한다. header가 프록시 등으로 소실되면 최소5초 fallback; 반복 실패에서 지연을 늘리고 무한 foreground polling하지 않는다.
- scope 부족403은 method에 맞는 scope를 WWW-Authenticate에 표시한다. Guest/탈퇴를 잘못된 scope 증거로 표시하지 않는다.401은 Bearer challenge.
- 한도409는 terminal 결과를 저장한다. 인증/형식/계정준비/429/503은 terminal 실패 receipt를 만들지 않는다. 응답유실/5xx에는 실제 commit이 있었을 수 있으므로 같은 key로 확인한다.

## 6. 공유 판정·요청 상태

| 이벤트 | 앱 행동 |
| --- | --- |
| 새 공유 시작 | 현재 사용자/서버환경에 묶인 key 생성·영속 저장. 공유창은 이후 실행 |
| iOS 외부 공유 앱 activity 완료 | 조건 충족 상태 영속화 후 POST |
| Android 외부 공유 앱 component 선택 | 조건 충족 상태 영속화 후 POST |
| 링크 복사/비앱 action/분류 불명 | POST 안 함. iOS sharedAction이라도 제외 |
| 공유창만 닫기/조건 신호 전 오류 | POST 안 함 |
| 조건 신호 뒤 대상앱 내부 취소 | 지급 가능, 회수/추가 취소 API 없음 |
| callback 중복 | 같은 key 유지, 중복 POST는 억제 |
| 앱 종료/네트워크 유실 | 조건 충족을 영속 저장한 key만 같은 계정에서 재시도 |
| 조건 신호 영속화 전 종료 | 전송 완료를 추정하거나 소급 지급 요청하지 않음 |
| 로그아웃/환경·계정 전환 | 기존 요청 중단·격리. 새로운 계정 token으로 재전송 금지 |

프론트 상태 예: READY_TO_SHARE → SHARE_OPEN → REWARD_PENDING → AWARDED/LIMIT_REACHED. SHARE_OPEN 자체는 복구 후 보상 재요청 근거가 아니다. 다른 오류/취소는 별도로 표시하며 단순 앱 복귀/timeout을 완료로 전환하지 않는다.

REWARD_PENDING 동안 다음 보상 공유는 막고 같은 operation 확인에 집중한다. 서버200 뒤 앱에서 operation별 안내를 한 번만 처리하고 count/entitlements를 재조회한다. 마지막 조회 실패는 이미 확인된 지급 성공을 취소하지 않는다. 카운트를 클라이언트에서 무조건+1 하지 않는다.

## 7. 멱등·보존·OFF

- key 범위: 서버환경 + 검증 사용자 + 이 route operation. 다른 사용자나 환경과 결과를 공유하지 않는다.
- 첫 terminal commit의 시각부터120×24시간 재현 보장. 정확히120일 경계부터 상세 정리 가능하며 남아 있는 receipt는 재현한다. HTTP 시각은 보장 기산점이 아니다.
- 앱은 오래된 미확인 요청을 무기한 자동 재전송하지 않는다. 최초 시도부터120일 이상 지난 요청은 자동 복구 중단·조회/문의 안내로 전환한다. 새 key로 재포장하지 않는다.
- 상세 정리 후 오래된 key가 새 요청으로 취급될 위험은 기존 승인 범위다. 누적 지급 근거는 별도 보존하므로 계정 합계3을 초과할 수 없다.
- 기능 OFF는 신규 두 API404. 이미 받은 EXAM_PASS는 기존 조회/사용 가능. pending key는 보존해 재활성화 때 확인할 수 있으나 OFF에서 성공을 추정하지 않는다.
- 무료권은 무기한/FIFO, paid 우선·무료 보존. INVITE 소비의 유료 전환 재복원 없음. 취소/expiry는 기존 exact hold 복구만 수행한다.

## 8. 공동 인수 체크리스트

| 케이스 | 기대 |
| --- | --- |
| 신규 무료 MEMBER | purchase-account 준비만으로 지급 가능, Store 구매 불필요 |
| 정상1~3회/4회 | 3회까지200, 다른4번째 key409, 사용해도 횟수 복원 안 됨 |
| 같은key 반복/세번째 성공 유실 | grant1개·동일 snapshot200, 중복 안내 없음 |
| 서로 다른key 동시20개 | 총 지급3 이내, 원장/권리/누적 불일치 없음 |
| 공유창 닫기/복사/분류불명 | 지급 POST 없음 |
| 대상앱 선택 후 취소 | Android 지급 허용, 실제 전달 완료로 표기하지 않음 |
| 회원 준비409/일시 장애 | 기존API 보강 또는 대기 후 동일key |
| 로그아웃·계정전환·탈퇴 | 타계정 재전송 없음, 탈퇴 deny 우선 |
| 앱 종료 전후·응답 유실 | 조건신호 영속 여부로 복구 구분, 새key 우회 없음 |
| OFF 및120일 | 기존 권리 사용 유지, pending 보존/장기 자동재시도 중단 |
| 유료권+초대권/초대권 시험 | 유료 우선·무료 보존·정상 reserve/confirm, INVITE 재복원 없음 |

인수 증거에는 앱/Billing commit, 환경, SDK/OS·공유 대상 버전, 케이스/결과만 기록한다. 실제 token·수신자·개인정보는 기록하지 않는다. 정적 계약 검토와 단위/Mongo/실기기/서비스 E2E 결과를 구분한다.

## 9. 변경 범위

프론트 전달용 문서와 Billing 작업 기록만 작성했다. 원격 메시지 발송·프론트 수정·제품 코드/API 배포·DB/schema·Jira/commit/push 변경 없음. 이 계약의 구현과 실제 전달은 후속이다.
