# PLAN-015 추천인 코드 양방향 무료 응시권 보상

- 날짜: 2026-10-22
- Jira: [TMI-223 — 추천인 코드 양방향 무료 응시권 보상 구현](https://to-teacher.atlassian.net/browse/TMI-223). 제품·보존 방향 수락, Billing 로컬 구현·실DB/배포 별도.
- 최신 상태(2026-10-22): 사용자 브랜치/구현 승인으로 TMI-223 Billing 로컬 구현. [운영 안내](../runbooks/PLAN-015-referral-code-rewards.md) 참조. 아래 설계 당시 미구현 문구는 역사적 범위이며 공동 E2E·실DB·활성화·배포는 별도다.
- 기존 기능: [공유 보상 운영 안내](../runbooks/PLAN-014-invite-share-rewards.md)
- 구현 기준: [기술 규격 R1](PLAN-015-TECHNICAL-SPEC-R1.md). API·멱등성·양방향 Transaction 구체화, 보존 방향 수락·tombstone 세부검토 별도.

## 1. 5줄 결론

1. 회원이 유효한 추천 코드를 처음 적용하면 입력자와 추천인에게 무료 응시권을 각각 1개 지급한다.
2. 회원가입 화면에서만 입력 UI를 제공하되 서버는 가입 시점·가입 경과일·추가 휴대전화 인증을 검사하지 않는다.
3. 입력자는 계정당 1회, 자기 코드 금지. 추천인의 수령 횟수는 무제한이며 다계정 악용 위험을 수용한다.
4. 코드 적용·양쪽 지급·원장을 하나의 Mongo Transaction으로 확정해 한쪽만 지급되거나 재시도로 추가 지급되지 않게 한다.
5. 공유 보상은 사용하지 않는다. 기존 코드·발급권은 보존하고 실제 flag OFF는 별도 적용한다. 로컬 구현은 기본OFF다.

## 2. 사용자가 반드시 알아야 하는 내용

### 확정 제품 정책

| 항목 | 기준 |
| --- | --- |
| 입력 | 회원가입 화면의 선택 입력. 미입력 가입 허용 |
| 회원 조건 | 코드 보유·입력 모두 MEMBER. 구매 이력 불필요 |
| 입력자 제한 | 계정당 성공 적용 1회. 확정 후 코드 변경 불가 |
| 추천인 제한 | 자기 코드 적용 금지. 추천받는 횟수·보상 수량 상한 없음 |
| 지급 | 성공 적용 한 건당 입력자 1개 + 추천인 1개 |
| 추가 자격 | 서버 가입 시점/7일 제한/별도 휴대전화 인증/기기 중복 검사 없음 |
| 기존 공유 보상 | 사용하지 않음. 기존 권리 회수·기록 삭제 없음 |

회원가입 화면에서 코드를 입력해도 서버 적용은 Identity 회원가입·로그인 완료 후 MEMBER 토큰으로 요청한다. 추천 코드 오류나 Billing 장애 때문에 Identity 회원가입 자체를 취소하지 않는다. 보상 성공은 Billing commit 응답 이후 안내하고 실패·응답 유실은 같은 요청으로 재확인한다.

직접 API를 호출하는 기존 회원도 아직 적용 이력이 없으면 최초 1회 사용할 수 있다. 한 사람이 여러 회원 계정을 만들거나 두 계정이 서로 추천하는 것도 자기 계정 코드 금지만으로 막히지 않는다. 이 위험을 막기 위한 추가 인증·추천 관계 그래프·상한은 이번 범위에 넣지 않는다.

## 3. 결정사항과 기술 제안 구분

위 제품 정책은 사용자 확정이다. 아래 API 이름·코드 형식·내부 저장 구조는 구현 전 검토할 기술 제안이며 이미 배포된 API가 아니다.

- 추천 코드는 Billing이 소유한다. Identity에 추천 관계나 무료권 지급을 구현하지 않는다.
- 코드는 회원가입 즉시 Identity가 발급하지 않고, 가입 후 Billing 준비가 완료된 회원의 최초 코드 발급 요청에서 생성한다. 생성 후 계정별 고정이며 재발급/변경 기능은 만들지 않는다.
- 기존 purchase-account 준비 경로와 검증된 MEMBER 증거를 재사용한다. 이는 실제 구매나 RevenueCat SDK 로그인을 요구하는 것이 아니다.
- 무료권은 기존 EXAM_PASS 방식: 무기한·FIFO, 유료권 우선 사용, 유료 전환으로 추천권 재복원 없음. 새 만료·금액 환산 규칙을 추가하지 않는다.
- 배포 환경별 코드/적용 이력을 분리한다. Store의 SANDBOX/PRODUCTION 거래 구분으로 동일 앱 환경에서 추천 보상을 두 번 지급하지 않는다. 기존 reward 서비스 환경(test/staging/production)과 payment 환경 매핑을 명시적으로 사용한다.
- 탈퇴·보존 세부기간은 기존 보존 정책과 대조 후 구현 전 확정한다. 모든 추천 기록을 금융 기록으로 간주해 일괄 5년 보존하거나 무기한 개인정보 보존하지 않는다.

## 4. 주요 위험과 미확인 사항

1. **공유 기능은 실제 OFF 전환이 필요할 수 있다.** 10월21일 runbook에는 test19 `INVITE_SHARE_REWARD_ENABLED=true`가 기록돼 있다. 이번에는 실서버를 조회하거나 변경하지 않았다. 앱에서 숨기는 것만으로 서버 지급을 막지 않는다.
2. 무제한 보상은 다계정으로 이용권을 누적할 수 있다. 사용자 수용 범위이며 일반 rate limit·멱등성은 유지한다.
3. 추천인 토큰은 요청에 포함하지 않는다. 코드 발급 당시 저장된 MEMBER 근거와 현재 lifecycle을 확인한다. 상대방의 실시간 Identity 계정 상태를 새 API로 매번 조회하지 않으며 기존 탈퇴 전달 지연 한계는 남는다.
4. 탈퇴가 지급보다 먼저 확정되면 전체 지급을 거절한다. 지급이 먼저 commit되면 상대방의 이미 지급된 권리를 이후 탈퇴만으로 회수하지 않는다. 양쪽 lifecycle과 지급 순서 guard를 같은 Transaction에서 보호한다.
5. 추천 기록 상세의 120일 멱등 응답 보장과 계정당 1회 중복 방지 근거의 보존은 다르다. 상세 정리 후에도 계정이 유지되는 동안 성공 적용 최소 근거를 남겨 재지급을 막는다. 탈퇴 후 식별 연결 제거 시점은 기존 lifecycle 보존 설계와 함께 확정해야 한다.

## 5. API 제안과 앱 흐름

기존 Billing public host·JWT audience·envelope를 재사용한다. 신규 scope/로그인 토큰은 만들지 않는다. 두 POST 모두 MEMBER + 기존 `billing:purchase`를 사용한다. 2026-10-22 사용자 승인으로 별도 내 상태 조회 GET은 제외한다.

| API 제안 | 역할 |
| --- | --- |
| POST `/api/v1/rewards/referral-code` | body 없이 내 코드 생성 또는 기존 코드 반환. 계정 unique로 반복 요청도 동일 코드 |
| POST `/api/v1/rewards/referrals` | `{"code":"ABCDEFGHJK"}` + lowercase UUID v4 `Idempotency-Key`로 적용 |

코드는 암호학적 난수 기반 10자리 대문자 영숫자(혼동 문자 제외)를 제안한다. UUID/userId/전화번호를 코드로 쓰지 않는다. 입력은 앞뒤 공백 제거·대문자 정규화 후 길이/문자 검증하고, 코드 unique 충돌은 제한 횟수로 다시 생성한다. 코드는 공유 가능한 식별자이지 인증 수단이 아니다. 임의 코드 소유자 조회 API는 제공하지 않는다.

적용 성공 result 예시: `{"applicationId":"<uuid>","applied":true,"applicantAwardedQuantity":1,"referrerAwardedQuantity":1}`. 상대방 userId·이메일·보유권 수는 반환하지 않는다. 계정 준비 누락은 기존 `PURCHASE_ACCOUNT_REQUIRED` 흐름을 재사용하고, 보유권 최종 표시는 기존 `GET /api/v1/entitlements`를 사용한다. 추천인이 받은 누적 건수·목록·랭킹 API는 초기 범위에서 제외한다.

같은 계정·같은 key·같은 정규화 body는 기존 성공 반환, 같은 key·다른 body는 `409 IDEMPOTENCY_KEY_CONFLICT`. 이미 적용한 계정이 새 key로 같은 코드를 보내면 기존 성공을 반환하되 지급하지 않는다. 다른 코드는 `409 REFERRAL_ALREADY_APPLIED`로 거절한다. 따라서 앱 key 소실이 계정당 1회 보장을 깨지 않는다.

제안 오류: 잘못된 형식400 `INVALID_REQUEST`, 존재하지 않거나 사용할 수 없는 코드422 `REFERRAL_CODE_UNAVAILABLE`, 자기 코드409 `SELF_REFERRAL_NOT_ALLOWED`. 기존 인증401/권한403/탈퇴403/기능OFF404/요청제한429/일시장애503을 유지한다. 상대방 탈퇴·준비 불명은 상세 개인정보 없이 unavailable 또는 일시장애로 구분한다. 인증 복구 후 동일 계정 요청만 재전송하고 429/503은 Retry-After를 따른다. 형식·자기추천·이미 적용 오류는 자동 반복하지 않는다.

앱 순서: 코드 선택 입력 → Identity 가입/로그인 → purchase-account 준비 → 추천 코드 적용 → 성공 시 이용권 재조회. 가입 중 임시 보관한 코드/key는 가입 완료한 계정에 귀속하고 로그아웃·다른 계정으로 넘어가 재전송하지 않는다. 추천인 계정의 다음 이용권 조회에서도 지급분이 보인다. 푸시·Slack 지급 알림·앱 링크 자동 적용은 초기 범위 밖이다.

내 코드는 발급 POST 재호출로 기존 값을 반환한다. 적용 결과가 불명이면 같은 계정·key/body로 적용 POST를 재시도한다. 현재 보유권은 기존 entitlements로 확인하며, 잔여권만으로 추천 적용 성공을 추정하지 않는다. 앱이 미확인 코드/key를 잃으면 임의 코드로 자동 재전송하지 않는다. 별도 상태 GET·조회 DTO·rate-limit bucket·ALB GET 연결은 만들지 않으며 서버의 적용1회·멱등 근거는 유지한다.

## 6. 저장과 원자적 지급 설계

### 최소 신규 컬렉션

- `referral_codes`: environment, ownerUserId, accountId, code, createdAt. `(environment,ownerUserId)`와 `(environment,code)` unique. 코드 발급 전에 ACTIVE MEMBER 계정/근거를 확인한다.
- `referral_applications`: environment, applicantUserId, referrerUserId, normalizedCode, applicationId, 최초 operationId, 양쪽 grantId/ledgerId, appliedAt 및 응답 근거. `(environment,applicantUserId)` unique로 한 번의 성공 적용을 강제한다. 계정·operationId unique도 둔다. 별도 PENDING 상태/worker/outbox/누적 quota 카운터는 만들지 않는다.

상세 보존기간 후 제거할 필드와 계정당1회 최소 근거를 schema에서 구분한다. TTL로 성공 적용 문서 전체를 삭제하지 않는다. 새로운 금전 원장이나 추천 전체 목록 캐시는 추가하지 않는다.

### 지급 Transaction

1. 기존 recovery fence와 입력자 인증·MEMBER 계정 준비 확인.
2. 기존 적용/멱등 결과 확인. 신규 적용이면 코드 owner 조회·자기추천/양쪽 ACTIVE 근거 확인.
3. 두 계정의 lifecycle 쓰기 보호와 지급순 guard 갱신은 userId 정렬 순서로 진행해 불필요한 경합을 줄인다. 추천인의 새 사용자 JWT를 발명하지 않는다.
4. 성공 적용 unique 문서 + 양쪽 무료권 두 개 + GRANTED 원장 두 개를 같은 ClientSession/Transaction으로 commit한다.
5. duplicate-key/응답 유실은 기존 적용을 재조회한다. commit 결과가 불명이면 503으로 같은 요청 재확인을 유도하고 한쪽 지급만 별도 보상하지 않는다.

동시에 다른 추천 코드로 요청해도 성공 commit 한 건만 인정한다. 아직 시험 guard가 없는 회원은 기존 MEMBER guard 준비를 재사용하며 current/pending 시험 상태는 건드리지 않는다. 추천 코드 적용은 최초 무료 TrialClaim 발급·소비와 독립이다.

### 기존 공유 보상과 분리

새 지급 sourceType은 `REFERRAL`을 제안한다. sourceId는 applicationId와 수령 역할(APPLICANT/REFERRER)을 구분해 양쪽 지급을 유일하게 식별한다. 기존 `INVITE` sourceType을 무조건 재사용하지 않는다. 현행 `InviteRewardService.count()`는 사용자 INVITE grant/원장을 최대4개 조회해 누적3과 비교하므로 무제한 추천 지급을 섞으면 검증을 깨뜨린다.

기존 grant/ledger 생성·FIFO order·무료권 reader/reserve/cancel/expiry/replacement를 재사용하되 REFERRAL 허용 목록과 명시 validator/index를 확장한다. `ReanswerService` 등 sourceType을 열거하는 경로도 포함한다. REFERRAL을 TRIAL로 취급하거나 유료전환 복원 대상으로 추가하지 않는다. 전체 추천 횟수를 매 요청 원장 scan으로 집계하지 않는다.

## 7. 구현·전환 순서

1. 문서/API 초안 검토, 보존 최소항목·기간 확인, Jira와 구현 범위 확정.
2. 별도 기본OFF referral flag, 코드 발급/적용 두 POST, 양방향 Transaction, additive schema 및 기존 무료권 소비/조회 호환 구현.
3. 공유 기능 live flag 확인 후 OFF 전환. 기존 공유 API의 정상 성공 의미를 추천 적용으로 바꾸거나 기존 자료를 삭제하지 않는다.
4. 테스트 schema 설치·신규 exact ALB path·배포·기능ON·회원 앱 연동 검증. 기존 공유권 보유자의 조회/사용도 확인.
5. 운영은 별도 승인 후 적용. Identity/LC 코드는 기본 읽기 대상으로 유지하고 프론트에 가입 완료 후 호출·오류 처리 계약을 인계한다.

## 8. 검증 목록과 근거

필수 검증: 신규 적용 양쪽1개 / 같은key 반복 / 다른key 같은코드 / 다른코드 중복 / 동시 다른코드 / 한쪽 저장 실패 전체rollback / unknown commit / 코드 생성 경합·충돌 / 자기추천·Guest·탈퇴 / 추천인 기존 JWT 없이 서버 근거 확인 / 여러 입력자 동시 추천 무제한 / 기존 공유3개 받은 회원 / 유료우선·무료FIFO·cancel·expiry·replacement·재답변 / REFERRAL 재복원 금지 / flagOFF·보존 후 재지급 방지 / 환경 격리.

정적 확인 근거:

- `domain/reward/InviteRewardService.java`: 누적3·단일사용자 지급·lifecycle·guard·원장·receipt의 기존 Transaction.
- `domain/reward/InviteRewardSchema.java`: 공유 전용 컬렉션/인덱스·INVITE 귀속 schema.
- `domain/entitlement/domain/entity/EntitlementGrant.java`, `EntitlementLedgerEntry.java`: MEMBER 무료권/INVITE 지급 팩토리.
- `domain/reanswer/ReanswerService.java`: 현재 무료 sourceType 열거 확인 필요 경로.
- [기존 공유 운영 기록](../runbooks/PLAN-014-invite-share-rewards.md): 테스트 ON 이력, flag OFF 후에도 기존 무료권 사용 유지.

최초 설계 작성 당시에는 문서·작업기록만 변경했다. 이후 사용자 승인으로 Jira 생성·두 POST 단순화·Notion 인계·Billing 로컬 구현을 진행했다. 실제 테스트 결과와 현재 범위는 운영 안내 및 WORKLOG를 따른다. 실DB/설정OFF/배포/commit/push는 별도다.
