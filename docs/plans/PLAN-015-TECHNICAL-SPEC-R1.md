# PLAN-015 추천인 코드 기술 규격 R1

- 작성: 2026-10-22 / 제품 정책: [PLAN-015](PLAN-015-referral-code-rewards.md)
- 상태: 제품 정책·§4 보존 방향 사용자 수락(2026-10-22), [TMI-223](https://to-teacher.atlassian.net/browse/TMI-223). 같은 날 사용자 브랜치/구현 승인에 따라 Billing 로컬 구현. [운영 안내](../runbooks/PLAN-015-referral-code-rewards.md)·[public 계약](../contracts/REFERRAL-REWARD-PUBLIC-API-R1.md) 참고. 아래 제안은 초기 설계 근거이며 코드 tombstone 정책·공동 E2E·배포는 별도다.

## 1. 5줄 결론

1. 한 추천 코드는 여러 회원이 사용할 수 있다. 성공 적용 1회 제한은 입력자 계정에만 적용한다.
2. Billing의 코드 발급·코드 적용 두 POST API를 사용한다. 2026-10-22 사용자 승인으로 별도 내 상태 조회 GET은 제외한다. 가입 시점·추가 전화 인증·추천인 누적 상한은 없다.
3. 입력자와 추천인의 무료권 두 개를 적용 기록·원장과 함께 한 Transaction으로 확정한다.
4. REFERRAL 출처만 추가하고 기존 무료권 소비·FIFO·유료 우선을 재사용한다. 공유 보상은 별도 OFF 전환한다.
5. §4의 계정 유지 중 최소근거·상세120일·탈퇴 후 연결정리 방향은 사용자 수락됐다. 코드 tombstone 세부정책은 별도 검토한다.

## 2. 앱과 구현자가 반드시 알아야 할 내용

회원가입은 Identity에서 먼저 완료한다. 프론트는 MEMBER 토큰으로 기존 purchase-account 준비 후 추천 적용을 호출한다. 구매나 RC SDK 로그인은 필요 없다. 코드 오류로 회원가입을 되돌리지 않는다. 추천인도 본인 코드 발급 전에 계정 준비가 완료돼 있어야 하며, 입력 요청에 추천인의 토큰을 보내지 않는다.

기존 사용자 JWT issuer/audience/서명·만료·scope 검증 유지. 신규 claim·Identity endpoint는 없다. environment는 서버가 결정하고 사용자 ID는 JWT sub에서만 받는다. 코드에 가입 시각이나 휴대전화 정보를 넣지 않는다.

## 3. HTTP 규격 제안

### 공통

- 기존 public connector·HTTPS·`isSuccess/code/message/result` envelope·`Cache-Control: no-store`·`X-Trace-Id` 재사용.
- 두 POST 모두 MEMBER + billing:purchase. 다른 method405, 기능OFF503 REFERRAL_TEMPORARILY_UNAVAILABLE, query(빈 query 포함)400. 명시적 종료는410 REFERRAL_PROMOTION_ENDED이며 기존 성공 적용 재현200은 유지한다. 기존 entitlements의 billing:read는 변경하지 않는다.
- 코드 발급 POST는 body 없음(공백/{}도400). 코드 적용 POST는 application/json, UTF-8 body 최대1,024 byte; 초과413, 다른 media type415.
- 적용 decoder는 중복 필드/unknown field/trailing token/null/비문자 code를400으로 거절한다. code 입력 원문 최대64자, ASCII 공백을 양끝에서 제거하고 ASCII 소문자만 대문자로 바꾼다. 내부공백/Unicode 치환은 허용하지 않는다.
- 코드: `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` 중 SecureRandom으로10자리. 환경·code unique 충돌 시 새 Transaction에서 최대5회 재생성, 소진503. 코드값·토큰·userId는 로그/metric label에 기록하지 않는다.
- rate limit은 기존 HMAC limiter의 별도 bucket: 발급10/분, 적용10/분. 내 상태 조회 bucket은 만들지 않는다. 추천인의 보상 상한이 아니라 호출자별 요청 제한이다.

### API와 result

| API | 입력 | 성공200 result |
| --- | --- | --- |
| POST `/api/v1/rewards/referral-code` | body 없음, key 불필요 | `{"code":"ABCDEFGHJK"}` |
| POST `/api/v1/rewards/referrals` | `{"code":"ABCDEFGHJK"}`, 단일 lowercase UUID v4 Idempotency-Key | `{"applicationId":"<uuid>","applied":true,"applicantAwardedQuantity":1,"referrerAwardedQuantity":1}` |

발급은 계정별 unique로 동일 코드에 수렴하므로 별도 key가 필요 없다. 내 코드 재표시는 발급 POST 재호출로 기존 코드를 반환한다. 적용 결과가 불명이면 같은 계정·key/body로 적용 POST를 재시도해 기존 성공 결과를 받는다. 앱은 미확인 코드/key를 계정별로 보관하며, 이를 잃으면 임의 코드로 자동 재전송하지 않는다. 잔여권은 기존 entitlements를 조회하며 잔여권만으로 추천 적용 성공을 추정하지 않는다. 서버의 계정당 적용1회·멱등 근거는 유지한다. 추천인 누적 수령 건수·코드 소유자 정보는 응답하지 않는다.

### 멱등성과 오류 우선순위

인증·입력·기능 gate 이후 입력자 lifecycle/recovery를 확인한다. 탈퇴는 과거 성공 재현보다 우선한다. 기존 성공 적용이 있으면 신규 추천인의 현재 상태를 다시 검사하지 않고 아래 비교로 처리한다. 상대방의 이후 탈퇴가 이미 확정된 보상을 취소하지 않는다.

| 조건 | 결과 |
| --- | --- |
| 최초 성공key + 같은 정규화 code | 기존200, 재지급 없음 |
| 최초 성공key + 다른 code | 409 IDEMPOTENCY_KEY_CONFLICT |
| 다른key + 이미 적용한 같은 code | 기존200, 신규 key 기록·지급 없음 |
| 다른key + 다른 code | 409 REFERRAL_ALREADY_APPLIED |
| 신규 요청 + 자기 코드 | 409 SELF_REFERRAL_NOT_ALLOWED |
| 코드 없음/추천인 탈퇴 | 422 REFERRAL_CODE_UNAVAILABLE |
| 입력자 계정 미준비 | 409 PURCHASE_ACCOUNT_REQUIRED |
| 추천인 코드가 있지만 저장 근거 누락·불일치 | 503 BILLING_TEMPORARILY_UNAVAILABLE, 개인정보 없는 내부 경보 |

별도 모든 요청 receipt를 만들지 않는다. key/body는 **최초 성공 적용만** 고정한다. 4xx 거절은 key를 예약하지 않는다. 다른key로 기존 성공을 조회한 것도 새 key/body 계약을 생성하지 않는다. 이 한정 범위를 앱 계약에 명시한다. 같은 사용자의 서로 다른 코드 동시 요청은 성공 commit 한 건만 인정한다.

기존401 UNAUTHENTICATED/403 FORBIDDEN·PAYMENT_ACCOUNT_WITHDRAWN/429 RATE_LIMITED를 재사용한다. 일시 DB·recovery 차단·재시도 소진은503이며 Retry-After 기본5초,429는 limiter 계산값이다. 오류를 무료권0이나 지급 완료로 표시하지 않는다. 429/503·응답유실만 같은 계정·같은key/body로 재시도한다.

## 4. 사용자 수락 보존 방향과 세부 검토

2026-10-22 사용자 수락: 계정 유지 중 코드·최초 적용 근거 유지, 관계 상세 적용 후120일, 탈퇴 후 보장기간 종료 시 연결정리, 무료권·사용 원장 별도 정책 유지. 아래 세부 삭제 계산·코드 tombstone 정책은 구현 전 개인정보처리방침과 기존 탈퇴 정리에 대조한다. 이 수락은 실제 삭제 실행 승인이 아니다.

- ACTIVE 계정의 자기 코드와 입력1회 근거는 계정 유지 동안 보존한다. code/applicantUserId/applicationId/최초key/정규화code/appliedAt·고정 응답 재현 근거만 남기고 IP/기기/전화번호를 추가 저장하지 않는다.
- 양쪽 사용자를 직접 연결하는 상세(referrerUserId·양쪽 grant/ledger 참조)는 적용 후120일 보존을 제안한다. 그 이후에는 코드 적용 비교·1회 방지에 필요한 최소값만 유지한다. 성공 응답의 고정 수량은 상세 제거 후에도 재현할 수 있다.
- 탈퇴가 반영되면 즉시 코드 신규 사용을 거절한다. 탈퇴 계정의 코드 및 입력1회 연결은 `max(탈퇴확인시각, 적용시각+120일)` 이후 명시 정리 대상으로 삼는 안을 제안한다. 코드 owner 제거 시 다른 ACTIVE 입력자의 추천인 연결도 끊는다. 재가입 새 UUID에는 과거1회 제한을 이식하지 않는다.
- 삭제된 코드의 재발급 방지를 위한 owner 없는 code tombstone은 개인정보 연계 가능성까지 검토 후 보존 정책에 포함한다. 당장은 임의 재사용하지 않는다. 상세·코드 삭제를 먼저 켜고 비교 근거를 잃는 순서는 금지한다.
- grant/ledger/시험 사용근거는 별도 기존 정책을 따른다. 추천 관계 정리가 상대방의 미사용 무료권 삭제로 이어지면 안 된다. 구매계정 자동삭제의15일을 추천 원장 전체에 적용하지 않는다.

최초 구현은 상세 정리 자동 scheduler 없이 제한된 명시 maintenance 함수로 시작하는 안이다. 다만 실제 서비스에 켜기 전 승인된 기간에 맞춰 실행할 담당·절차가 있어야 한다. 자동TTL로 적용 문서 전체를 지우지 않는다.

## 5. 저장·동시성 규격

모든 schemaVersion은 BSON Long1, 시각은 UTC BSON Date, UUID는 lowercase canonical v4다. grant 환경은 기존 서비스 환경(test/staging/production), account/lifecycle은 기존 payment 환경 매핑을 사용한다. 클라이언트가 SANDBOX를 선택해 보상을 재수령할 수 없다.

| 컬렉션 | 최소 필드 | 인덱스 |
| --- | --- | --- |
| referral_codes | _id, schemaVersion, environment, code, ownerUserId, accountId, createdAt | environment+code unique, environment+ownerUserId unique(소유 연결이 있는 문서만 partial) |
| referral_applications | _id=applicationId, schemaVersion, environment, applicantUserId, normalizedCode, operationId, appliedAt, detailEligibleAt, detail 객체 | environment+applicantUserId unique; environment+applicantUserId+operationId unique; detailEligibleAt+_id 정리용 |

detail에는 referrerUserId와 양쪽 grantId/ledgerId를 둔다. REFERRAL grant.sourceId=`<applicationId>:APPLICANT` 또는 `:REFERRER`, ledger dedupeKey=`REFERRAL:<environment>:<sourceId>`. grant에는 environment/sourceType/sourceId partial unique, 지급 원장에는 기존 dedupe unique를 적용한다. 별도 quota/누적counter/PENDING/outbox 없음. 세부 validator는 기존 schema에 additive하게 추가하고 startup은 verify만 수행한다.

### 신규 적용 Transaction

1. 서버에서 grant/ledger/application UUID와 적용시각을 생성해 Transaction 재시도 동안 고정한다.
2. recovery ticket을 캡처하고 같은 ClientSession에서 ACCOUNT writer fence 획득. 입력자 JWT authorizationDeadline과 ACTIVE MEMBER 증거 확인.
3. 적용 unique 조회·재현 분기. 신규면 코드 owner 확인 후 양쪽 account/lifecycle·MEMBER 근거를 검증한다.
4. userId 정렬 순서로 양쪽 ACTIVE lifecycle version CAS와 기존 MEMBER guard의 nextGrantOrder를 갱신한다. 현재 시험 current/pending 필드는 보존한다. lifecycle 원문이 없는 계정을 임의 ACTIVE로 만들지 않는다.
5. EXAM_PASS quantity1 두 개와 GRANTED 원장 두 개, 성공 적용 한 개 insert. commit 직전 입력자 인증 유효기간 재확인.
6. 전체 commit 뒤200. unique 충돌은 기존 기록 재조회, transient는 기존 bounded Transaction 재시도, unknown commit은 같은 commit만 재시도 후 불명503. 한쪽만 따로 지급하거나 수동 복구 queue를 만들지 않는다.

탈퇴와 지급은 lifecycle CAS/Transaction commit 순서로 결정한다. 추천인용 JWT·새 외부 인증 API는 만들지 않으며 코드 발급 때 검증한 MEMBER 계정 근거와 기존 lifecycle을 사용한다. 다른 입력자들이 같은 추천인에게 동시 추천하면 해당 추천인 grantOrder 경합은 기존 bounded retry로 처리한다. 누적 건수가 늘어도 전체 원장을 스캔하지 않는다.

## 6. 구현 변경 지점과 배포

- 새 ReferralController/Service/Properties/Schema 및 decoder. 기본OFF `REFERRAL_REWARD_ENABLED`, 기존 public ingress/security/rate limiter/error writer에 두 exact POST route만 추가. 별도 상태 GET·조회 DTO·조회 bucket·ALB GET 연결은 만들지 않는다. 발급 재호출의 동일 코드 반환과 적용 동일 key/body 재호출의 중복 지급 방지를 검증한다.
- EntitlementGrant·EntitlementLedgerEntry에 REFERRAL 생성·귀속, 명시 schema/index 추가. 기존 memberGrant/FIFO 경로 재사용.
- ReanswerService의 INVITE/TRIAL_PAID_RESTORATION source 허용 목록에 REFERRAL 추가. PaidExamQueryReader/Reservation/restore/무료권 schema의 source 가정 전수 점검·회귀 테스트. REFERRAL을 최초trial이나 복원권으로 오인하지 않음.
- 기존 InviteRewardService.count의 INVITE 누적3 검증은 유지한다. 추천 지급은 공유 quota에 포함하지 않으며 기존 INVITE 자료를 REFERRAL로 이관하지 않는다.
- ON gate는 기존 MEMBER account/evidence·recovery fence·foundation/admission/integrated reader 및 새 schema verify. 공유flag와 추천flag 동시ON은 신규 referral 설정에서 기동 거절하는 안으로 중복 제도 운영을 방지한다.
- 테스트 공유 ON 이력이 있으므로 live 확인→공유OFF→schema 설치→추천OFF 코드 배포→신규 exact path 연결→검증 후 추천ON. 기존 앱 무료권 조회/사용은 보존한다. 이번에는 실제 flag/ALB/DB를 변경하지 않는다.
- 롤백은 referral flagOFF, 공유는OFF 유지. 신규 지급된 REFERRAL을 읽을 수 있는 이미지로만 rollback한다. REFERRAL을 모르는 구버전으로 무조건 되돌리지 않는다.

## 7. 검증·완료 조건

단위: 코드 정규화/크기/strict JSON/JWT·scope/Guest/flag/오류/비로깅. Mongo replica-set: 코드 발급 경합·unique 충돌, 같은코드 여러 입력자 각각 양방향1개, 한 입력자 서로 다른코드 경합, 같은key 다른body, 다른key 동일코드, 한쪽 insert 실패 전체rollback, unknown commit, 양쪽 탈퇴 경합, grantOrder 동시성, 정리 후 재지급 방지. 기존 무료권: paid-first/FIFO/hold 취소·expiry/replacement/재답변/추천권 재복원 금지/기존 INVITE3개 사용자 호환.

완료 순서: 보존안 승인·API 프론트 대조 → Jira 승인/등록 → 구현 및 clean test → 테스트 schema/배포 승인 → 실제 두 테스트 회원 지급·재시도·조회·시험 사용 검증 → 운영 별도 승인. 법적 보존 적합성·다계정 억제·실제 가입시점 제한을 이 테스트의 보장으로 주장하지 않는다.

초안의 정적 대조 근거는 InviteRewardService/Configuration/Controller/Schema, EntitlementGrant/LedgerEntry, ReanswerService와 PLAN-014 운영 기록이다. 이후 사용자 승인으로 TMI-223 및 Billing 로컬 구현을 진행했다. 실제 구현·검증 결과는 운영 안내/WORKLOG를 따른다. 타 저장소·실서버·DB·배포는 변경하지 않았다.
