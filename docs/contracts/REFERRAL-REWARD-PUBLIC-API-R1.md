# 추천인 보상 public API R1

- 기준: PLAN-015 / TMI-223 / 2026-10-22 사용자 승인 두 POST
- Billing 로컬 구현 규격. 배포·ON·프론트 공동 실행 검증은 별도다.

## 공통

기존 Billing public HTTPS host와 Identity 사용자 JWT를 사용한다. audience `tosunsaeng-billing`, scope `billing:purchase`, `account_type=MEMBER`. 코드 발급자와 입력자 모두 기존 `POST /api/v1/payments/purchase-account` 준비가 필요하지만 구매·RevenueCat SDK 로그인은 필요 없다. 사용자·환경·수량을 클라이언트가 지정하지 않는다.

기존 `isSuccess/code/message/result` envelope, Cache-Control:no-store, X-Trace-Id를 유지한다. query(빈 query 포함)는400, 다른 method405/Allow:POST, OFF404다. 각각 호출자별10회/분 제한이며 추천인 보상 상한은 없다. 토큰·code·사용자ID를 로그에 남기지 않는다.

## 1. 내 코드 발급

`POST /api/v1/rewards/referral-code`, body 없음(공백/{} 금지), Idempotency-Key 불필요.

```json
{"isSuccess":true,"code":"SUCCESS","message":"요청에 성공했습니다.","result":{"code":"ABCDEFGHJK"}}
```

10자리 영문대문자·숫자 코드이며 I/O/0/1은 제외한다. 최초 발급 이후 같은 코드 반환, 발급만으로 보상 지급 없음. 별도 GET을 호출하지 않고 이 POST로 다시 표시한다.

## 2. 코드 적용

`POST /api/v1/rewards/referrals`, Content-Type:application/json(UTF-8), 단일 lowercase UUIDv4 Idempotency-Key 필수.

```json
{"code":"ABCDEFGHJK"}
```

body 최대1,024 byte, code 원문최대64자. 양끝 ASCII space 제거·ASCII 소문자 대문자화 후10자리만 허용한다. 내부공백/Unicode 치환·unknown/중복필드·null/비문자·trailing JSON은400이다.

```json
{"isSuccess":true,"code":"SUCCESS","message":"요청에 성공했습니다.","result":{"applicationId":"44444444-4444-4444-8444-444444444444","applied":true,"applicantAwardedQuantity":1,"referrerAwardedQuantity":1}}
```

200은 양쪽 지급 확정 또는 기존 성공 재현이다. 지급수량은 현재 잔여권이 아니며 잔여권은 기존 `GET /api/v1/entitlements`로 확인한다.

| 조건 | 결과 |
| --- | --- |
| 최초 성공 key + 같은 정규화 code | 기존200, 재지급 없음 |
| 최초 성공 key + 다른 code | 409 IDEMPOTENCY_KEY_CONFLICT |
| 다른 key + 기존 code | 기존200, 새 key 기록 없음 |
| 다른 key + 다른 code | 409 REFERRAL_ALREADY_APPLIED |

4xx 실패는 key를 예약하지 않는다. 입력자 탈퇴는 기존 성공보다 우선한다. 429/503/응답유실은 같은 계정/key/body로만 재시도한다. 앱은 미확인 key/code를 계정별 보관하고 로그아웃·다른 계정으로 재전송하지 않는다. key/code 유실 시 임의 새 코드로 자동 전송하거나 잔여권만으로 성공을 추론하지 않는다.

## 오류와 앱 처리

| HTTP | code | 처리 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 요청 형식 수정, 자동 반복 금지 |
| 401 | UNAUTHENTICATED | 인증 복구 후 같은 계정 요청만 재시도 |
| 403 | FORBIDDEN | Guest/권한 부족, 중단 |
| 403 | PAYMENT_ACCOUNT_WITHDRAWN | 탈퇴, 중단 |
| 404 | NOT_FOUND | 경로/배포 확인. 이벤트 종료로 판단하지 않음 |
| 410 | REFERRAL_PROMOTION_ENDED | 명시적 이벤트 종료, 신규 적용 중단 |
| 503 | REFERRAL_TEMPORARILY_UNAVAILABLE | 기능 OFF·일시 중단, Retry-After 이후 재시도 |
| 405 | METHOD_NOT_ALLOWED | POST 사용 |
| 409 | PURCHASE_ACCOUNT_REQUIRED | purchase-account 준비, 구매 불필요 |
| 409 | IDEMPOTENCY_KEY_CONFLICT | key 관리 오류 확인 |
| 409 | REFERRAL_ALREADY_APPLIED | 다른 코드 적용 이력, 추가 적용 불가 |
| 409 | SELF_REFERRAL_NOT_ALLOWED | 자기 코드 불가 |
| 422 | REFERRAL_CODE_UNAVAILABLE | 없거나 사용 불가 코드; 상대방 개인정보 표시 금지 |
| 413 | PAYLOAD_TOO_LARGE | body 크기 수정 |
| 415 | UNSUPPORTED_MEDIA_TYPE | application/json UTF-8 사용 |
| 429 | RATE_LIMITED | Retry-After 이후 재시도 |
| 503 | BILLING_TEMPORARILY_UNAVAILABLE | 확인 불가, Retry-After(기본5초) 이후 재시도 |

프론트 순서: 회원가입 UI 선택입력 → Identity 가입/로그인 성공 → purchase-account → 코드 적용 → entitlements 재조회. 추천 실패로 회원가입을 취소하지 않는다. 회원가입 시점의 UI 제한만 사용하며 서버 가입시점·추가전화·기기 검사는 없다. 공유 보상 POST와 함께 호출하지 않는다.

종료는 `REFERRAL_PROMOTION_ENDED=true`로 별도 설정하며 기본 false다. enabled=true를 유지해야 종료 전 성공한 같은 코드 요청을 기존 200으로 재현할 수 있다(같은/다른 key 모두 기존 규칙 유지). 다른 코드의 409 및 탈퇴 차단은 유지한다. 코드 발급·재조회는 종료 시 410이다. enabled=false는 종료가 아닌 503이며 재현도 일시 중단한다. 미인증/권한 부족은 기존 401/403이 우선한다.
