# 프론트 통합 API 안내 — Identity · Learning Core · Billing

기준일: 2026-10-16. 로컬 컨트롤러·DTO 정적 확인 기준. Identity HEAD b05d6f18, LC HEAD 2e24f10, Billing HEAD 60d5f6c 및 현재 작업파일을 확인했다. 이 문서는 API 목록과 연동 흐름 안내이며 모든 필드·오류를 대체하는 OpenAPI 명세나 배포 완료 선언이 아니다.

## 1. 5줄 결론

1. Identity는 로그인·회원·문의, LC는 시험·학습·기록, Billing은 상품·구매 연결·결제 확인·무료/유료 이용권 조회를 담당한다.
2. 앱은 Identity Access Token을 전달한다. Firebase ID Token과 서버 전용 workload JWT를 대신 사용하지 않는다.
3. 구매는 Billing 계정 연결 → RevenueCat SDK 구매 → Billing sync → 이용권 재조회 순서다. SDK 성공만으로 권리를 지급하지 않는다.
4. 시험 시작 버튼은 Billing의 종합 판정을 참고하되 최종 허가는 LC의 시험 생성 요청에서 다시 확인한다.
5. 현재 Billing 서버용 주소만 연결됐다. 앱용 Billing API는 아직 연결·활성화 전이며 아래 목록의 코드 존재와 실서비스 사용 가능 여부를 구분한다.

## 2. 반드시 읽어야 하는 내용

### 주소와 배포 상태

| 서비스 | 테스트 주소 | 현재 확인 범위 |
| --- | --- | --- |
| Identity | `https://identity-test.to-teacher.com` | 기존 ALB host 확인. 이번 작업에서 개별 API 배포·flag·실응답 미검증 |
| LC | `https://api-test.to-teacher.com` | 기존 ALB host 확인. 이번 작업에서 개별 API 배포·flag·실응답 미검증 |
| Billing 앱용 | `https://billing-test.to-teacher.com` | 예정 주소. 인증서 준비됨, 앱용 DNS/ALB/public connector 미연결·미활성화 |
| Billing 서버용 | `https://billing-workload-test.to-teacher.com` | ALB·DNS·기본 접근차단 검증 완료. **앱에서 호출 금지** |

서버용 Billing은 테스트 NAT 출처 및 제한된 LC POST 경로만 전달하며 전용 JWT가 필요하다. 프론트 주소로 재사용하거나 앱에 서버 비밀키를 넣지 않는다. production 주소/활성화 상태는 본 문서에서 확정하지 않는다.

### 인증·오류·요청 키 공통 규칙

- 인증 API에는 `Authorization: Bearer <Identity Access Token>`을 보낸다. 일반 본인 API의 userId는 서버가 검증된 JWT `sub`에서 읽는다. 요청에 임의 userId를 추가하지 않는다.
- `aud`는 JWT payload의 수신 대상이다. Identity가 기존 audience와 `tosunsaeng-billing`을 포함해 발급하며 앱이 토큰을 수정하지 않는다.
- Billing 조회는 `billing:read`, 구매 계정/sync는 MEMBER + `billing:purchase`가 필요하다. Identity 코드상 구매 권한은 별도 구매 발급 gate와 ACTIVE 판정을 통과해야 추가된다. MEMBER라고 무조건 발급되는 것으로 가정하지 않는다.
- 예전 토큰은 자동 변경되지 않는다. 401은 갱신 후 제한적으로 재시도하고 무한 갱신하지 않는다. 403은 권한·계정 상태를 확인한다.
- 사용자 API는 `isSuccess`, `code`, `message`, `result` envelope를 사용한다. HTTP와 code로 분기하고 message 문구에 의존하지 않는다. 성공 code와 null/생략 규칙은 서비스별로 다르다.
- `Idempotency-Key`가 필요한 작업은 작업별 lowercase UUID v4를 생성·보관한다. 동일 작업의 응답 유실은 같은 key/내용으로 재시도하고, 실패했다는 이유만으로 새 key를 만들지 않는다. 서로 다른 API의 key를 공유하지 않는다.
- 429/503은 해당 API의 `Retry-After`가 있으면 준수한다. 대기 시간이 모든 API에서 같지는 않다. 조회 실패를 이용권0개·결제 실패로 바꾸지 않는다.
- S3 Presigned URL 업로드에는 Identity Bearer를 보내지 않는다. URL의 지정 HTTP method와 Content-Type을 따른다.

## 3. 프론트 API 전체 목록

아래는 현재 로컬 코드에 존재하는 사용자용 경로 목록이다. 기능별 flag·권한·배포 확인 전 실제 사용 가능으로 간주하지 않는다. 내부 event/callback/JWKS/actuator는 앱 기능 목록에서 제외했다.

### 3.1 Identity — 인증·계정·문의

| Method | Path | 프론트 용도 |
| --- | --- | --- |
| GET | `/api/v1/app/version?platform=android\|ios` | 최소/최신 앱 버전. platform은 둘 중 하나, 무인증 |
| GET | `/api/v1/policies/consents` | 가입 전 정책 조회 |
| POST | `/api/v1/auth/check-email` | LOCAL 이메일 중복 확인 |
| POST | `/api/v1/auth/signup` | LOCAL 가입 |
| POST | `/api/v1/auth/guest` | 게스트 계정·토큰 생성 |
| POST | `/api/v1/auth/login` | LOCAL 로그인 |
| POST | `/api/v1/auth/reissue` | 토큰 갱신 |
| POST | `/api/v1/auth/logout` | 해당 RefreshSession 로그아웃 |
| POST | `/api/v1/auth/logout-all` | 전체 기기 RefreshSession 로그아웃 |
| POST | `/api/v1/auth/firebase/exchange` | Firebase 인증 결과를 Identity 토큰으로 교환 |
| POST | `/api/v1/auth/firebase/signup` | SNS 신규 회원 가입 완료 |
| POST | `/api/v1/auth/firebase/guest/prepare` | 게스트 승격/기존 회원 병합 분기 확인 |
| POST | `/api/v1/auth/firebase/guest/upgrade` | 게스트 ID를 유지한 회원 승격 |
| POST | `/api/v1/auth/firebase/guest/merge` | 기존 회원 계정으로 병합 |
| POST | `/api/v1/auth/firebase/auth-methods/sync` | SNS 인증수단 동기화 |
| GET | `/api/v1/users/me` | 프로필 조회 |
| GET | `/api/v1/users/me/consents` | 내 동의 상태 조회 |
| PUT | `/api/v1/users/me/consents` | 동의 갱신/철회 |
| POST | `/api/v1/users/withdraw` | 탈퇴 |
| GET | `/api/v1/users/me/merges` | 병합 진행 조회 |
| GET | `/api/v1/users/me/merges/{mergeId}` | 지정 병합 진행 조회 |
| POST | `/api/v1/auth/account-recovery/prepare` | 로그인 불가 시 계정 찾기 준비 |
| POST | `/api/v1/auth/account-recovery/lookup` | 최근 전화 인증으로 가입 SNS 힌트 조회. 자동 로그인·결제 복구 아님 |
| POST | `/api/v1/support/inquiries` | AUTH/GENERAL/REFUND 문의 접수 |

SNS 연결·해제 화면을 구현할 때만 다음 절차를 추가한다. 이름만 보고 Firebase 연결을 임의 실행하지 말고 각 단계의 request ID·인증·복구 계약을 따른다.

| Method | Path | 용도 |
| --- | --- | --- |
| POST | `/api/v1/auth/firebase/providers/link/prepare` | 연결 준비 |
| POST | `/api/v1/auth/firebase/providers/link/start` | 연결 작업 시작 |
| POST | `/api/v1/auth/firebase/providers/link/complete` | 연결 결과 확정 요청 |
| POST | `/api/v1/auth/firebase/providers/link/status` | 지정 작업 상태 |
| POST | `/api/v1/auth/firebase/providers/link/pending` | 미완료 작업 확인 |
| POST | `/api/v1/auth/firebase/providers/link/cancel` | 연결 취소 요청 |
| POST | `/api/v1/auth/firebase/providers/link/failure-report` | 실패 보고 |
| POST | `/api/v1/auth/firebase/providers/unlink` | 연결 해제 요청 |
| POST | `/api/v1/auth/firebase/providers/unlink/status` | 해제 상태 확인 |

### 3.2 Billing — 앱용 4개

| Method | Path | 권한 | 입력/결과 핵심 |
| --- | --- | --- | --- |
| GET | `/api/v1/payments/products` | billing:read | body/query 없음. catalogVersion, products 목록 |
| POST | `/api/v1/payments/purchase-account` | MEMBER + billing:purchase | body/query 없음. purchaseAccountRefId 조회·발급 |
| POST | `/api/v1/payments/sync` | MEMBER + billing:purchase | Idempotency-Key 필수, JSON store/transactionId |
| GET | `/api/v1/entitlements` | billing:read | 무료 benefits, 활성화 시 paid·examAvailability 통합 조회 |

- products의 각 상품은 offerCode/durationSeconds/autoRenew/storeProducts, Store 매핑은 store/productId/packageId다. 서버가 SDK를 호출해 가격을 가져오는 API가 아니다. 앱은 허용된 매핑과 RevenueCat SDK Offering/Store 상품을 맞춰 가격·구매 UI를 표시한다. 정상 빈 목록과 조회 오류를 구분한다.
- purchaseAccountRefId는 **동일 사용자·동일 환경**에서 상품/기기와 무관하게 동일하다. 실제 userId와 다르며 RevenueCat custom App User ID로 사용한다. 계정 연결 전 익명 구매 금지. 계정 전환 시 이전 사용자 구매 연결을 재사용하지 않는다.
- sync 입력 예: `{"store":"APP_STORE","transactionId":"<검증 대상 Store transaction hint>"}`. Android store는 `PLAY_STORE`. SDK 반환 구조별 거래 ID 매핑을 확인하며 productId·userId·purchaseAccountRefId를 transactionId 대신 보내지 않는다. 클라이언트 값은 검증 힌트이지 결제 증거 자체가 아니다.
- sync는 미반영이면 HTTP202/status=PENDING 및 Retry-After:5, 반영됐으면 HTTP200/status=APPLIED. operationId/purchaseId/purchaseStatus를 반환하고 검토 필요 시 processingHint=CONTACT_SUPPORT가 올 수 있다. PENDING은 결제 실패나 재구매 신호가 아니다.
- `/api/v1/payments/entitlement`는 만들지 않는다. 일반 결제 이력·직접 환불 실행용 public API도 현재 이 4개에 없다.

### 3.3 LC — 모의고사

| Method | Path | 용도 |
| --- | --- | --- |
| POST | `/api/v1/exams` | 시험 생성. Billing 연동 기준 Idempotency-Key 필수, body 없음 |
| GET | `/api/v1/exams/history` | 완료 시험 이력 |
| GET | `/api/v1/exams/{examId}/retries` | 재답변 문항·회차 |
| GET | `/api/v1/exams/{examId}/questions/{questionNumber}/prompt` | 문항 문제 |
| GET | `/api/v1/exams/{examId}/questions/{questionNumber}/upload-url` | 음성 업로드 URL |
| POST | `/api/v1/exams/{examId}/questions/{questionNumber}/submit` | 업로드 완료 후 제출 |
| POST | `/api/v1/exams/{examId}/grading/retry` | 실패·지연 채점 복구 |
| GET | `/api/v1/exams/{examId}/status` | 전체 채점 상태 |
| GET | `/api/v1/exams/{examId}/summary` | 종합 점수·피드백 |
| GET | `/api/v1/exams/{examId}/questions` | 문항별 결과 |
| GET | `/api/v1/exams/{examId}/questions/status` | 문항별 채점 상태 |

시험 생성 응답 유실은 같은 key로 재시도한다. 사용자가 의도적으로 새로 시작할 때만 새 key를 사용한다. reserve/confirm/cancel은 LC가 Billing과 처리하므로 앱이 직접 호출하지 않는다. upload-url·submit의 회차/본문 규격은 LC DTO를 따른다.

### 3.4 LC — 챌린지·학습·알림·기록 삭제

| Method | Path | 용도 |
| --- | --- | --- |
| GET | `/api/v1/challenges/today` | 오늘 챌린지 |
| GET | `/api/v1/challenges/today/questions/{questionNumber}` | 문제 조회 |
| POST | `/api/v1/challenges/today/questions/{questionNumber}/attempt` | 응시 준비 |
| POST | `/api/v1/challenges/attempts/{attemptId}/upload-url` | 음성 업로드 준비 |
| POST | `/api/v1/challenges/today/questions/{questionNumber}/answer` | 답변 제출 |
| GET | `/api/v1/challenges/history` | 참여 이력 |
| GET | `/api/v1/challenges/{challengeDate}/results` | 날짜별 결과 |
| GET | `/api/v1/learning/units?part=…&level=…` | 학습 단원 목록 |
| GET | `/api/v1/learning/units/{id}?level=…` | 학습 단원 상세 |
| GET | `/api/v1/learning/media/{id}/url` | 학습 미디어 URL |
| GET | `/api/v1/learning/capabilities?lessonId=…` | 학습 기능 지원 정보 |
| POST | `/api/v1/learning/submissions` | 학습 답변 생성 |
| POST | `/api/v1/learning/submissions/{id}/upload-url` | 학습 음성 업로드 URL |
| POST | `/api/v1/learning/submissions/{id}/submit` | 학습 답변 제출 |
| GET | `/api/v1/learning/submissions/{id}` | 학습 답변 상태·결과 |
| POST | `/api/v1/learning/submissions/{id}/retry` | 학습 처리 재시도 |
| PUT | `/api/v1/learning/lessons/{id}/completion` | 학습 완료 기록 |
| PUT | `/api/v1/notifications/devices` | 알림 기기 등록·갱신 |
| DELETE | `/api/v1/notifications/devices/{installationId}` | 알림 기기 연결 해제 |
| DELETE | `/api/v1/learning-records` | 학습 기록 삭제 접수. body/query 없음, Idempotency-Key 필수 |
| GET | `/api/v1/learning-records/deletion` | 삭제 진행·canStartLearning 조회 |

챌린지는 MEMBER 전용이며 각 기능 flag·배포 확인이 필요하다. 학습 기록 삭제는 탈퇴·결제 취소·무료권 복원이 아니다. 삭제 접수202는 완료가 아니며 status 문자열 하나로 새 학습을 허용하지 말고 canStartLearning을 따른다. 필요한 Billing 확인이 끝나기 전에는 새 시험이 막힐 수 있다.

## 4. 결제 → 시험 → 환불 화면 연결

1. Identity 로그인/회원 승격으로 필요한 audience·scope가 포함된 사용자 토큰을 받는다.
2. 구매 화면 준비 시 Billing products와 purchase-account를 호출한다. 두 요청은 선후 의존성이 없지만 **실제 구매는 계정 연결 및 상품 확인 후** 시작한다.
3. RevenueCat SDK에 purchaseAccountRefId를 연결하고 허용된 Store 상품으로 구매한다. Apple/Google 결제창은 SDK가 띄운다.
4. SDK 구매 완료 후 Billing sync에 거래 힌트를 보내고, PENDING이면 결제 확인 중으로 표시한다. 재구매시키지 않는다.
5. entitlements를 다시 읽어 무료 수량·유료 종료 시각·시험 가능 판정을 표시한다.
6. 시험 시작 시 LC POST exams를 호출한다. LC의 최종 자격 확인 결과를 따른다.
7. 환불은 Identity 문의 category=REFUND로 접수한다. 문의 접수만으로 환불 완료·이용권 종료를 표시하지 않는다.

### 이용권 필드와 화면 판정

| 필드 | 의미/프론트 처리 |
| --- | --- |
| asOf | 서버가 이번 조회를 계산한 기준 시각. 만료 시각 아님 |
| benefits[].availableQuantity | 무료 혜택별 신규 사용 가능 수량. null은 미확인, 0으로 바꾸지 않음 |
| benefits[].newAttempt / retake | 신규/기존 그룹 재응시 가능 판정. 무료 신규권과 재응시를 구분 |
| paid.status | READY 또는 PENDING. ACTIVE라는 enum이 아님 |
| paid.active | 유료권 활성 여부 true/false/null |
| paid.activeEndsAt | 현재 유료권 종료 시각 |
| paid.continuousAccessEndsAt | 이어지는 유료 기간 전체의 종료 시각 |
| paid.nextStartsAt | 다음 예정 시작 시각 |
| paid.hasPendingSync | 미해결 결제 확인 존재. 이것만으로 이용권 없음/입장 차단을 확정하지 않음 |
| examAvailability.canTakeExam | true=가능, false=불가, null=판정 불가. 최종 허가는 LC 생성 시 재검증 |
| examAvailability.reasonCodes | 판정 사유. ADMISSION_NOT_READY는 판정 준비/연결 미완료 |

현재 코드는 종합 true/false 판정기를 포함한다. 과거 문서의 “항상 null, 미구현” 설명을 현재 전체 구현 상태로 재사용하지 않는다. 다만 통합 reader/기능이 꺼졌거나 준비되지 않으면 paid/examAvailability가 생략되거나 null 판정이 가능하고, 현재 테스트 public API는 아직 활성화하지 않았다. null을 true로 치환해 입장시키지 않는다. “이용권을 확인할 수 없어요. 잠시 후 다시 시도해 주세요.”를 표시하고 지속 시 문의로 연결한다.

### 시험 시작 주요 오류 — LC 응답 기준

| HTTP/code | 앱 처리 |
| --- | --- |
| 400 IDEMPOTENCY_KEY_INVALID | UUID v4 키 형식 수정 |
| 402 ENTITLEMENT_INSUFFICIENT | 사용 가능한 응시권 없음. 구매/자격 안내 |
| 409 ENTITLEMENT_REVOKED | 환불·취소 권리. 재구매 자동 유도 없이 권리 재조회 |
| 409 EXAM_CREATION_PROCESSING | 동일 key로 처리 상태 수렴. 새 key 자동 생성 금지 |
| 409 EXAM_TRANSITION_CONFLICT | 상태 재조회 후 안내 |
| 409 IDEMPOTENCY_KEY_CONFLICT | key 재사용 오류 수정 |
| 429 BILLING_RATE_LIMITED | 대기 후 재시도, Retry-After가 있으면 준수 |
| 503 BILLING_TEMPORARILY_UNAVAILABLE | 일시 확인 불가. 응시권0/결제실패로 해석 금지 |

LC/Billing의 내부 오류 이름이 항상 그대로 앱에 전달되는 것은 아니다. 위 표는 LC 공개 ErrorStatus 기준이며 전체 오류 목록은 아니다.

### 환불 문의

`POST /api/v1/support/inquiries`: Identity Bearer + Idempotency-Key + application/json. category=REFUND, message(10~2000자), 선택 replyEmail/context. userId를 본문에 넣지 않는다. 신규201/중복200, result.inquiryId와 status=RECEIVED를 보관한다. 인증 없는 REFUND는401 SUPPORT_REFUND_AUTH_REQUIRED다.

이메일은 선택이며 소유 인증된 값이 아니다. 환불은 담당자 검토와 Store 확정 뒤 반영한다. 문의 조회/자동 이메일 답변/앱 직접 환불 실행 API는 제공하지 않는다. 탈퇴 후 같은 번호 재가입만으로 이전 구매를 자동 복구하지 않는다.

## 5. 결정·주의·남은 검증

- 이번 정리는 새 정책 선택이나 배포 승인 요청이 아니다. 프론트 연동 시작 전에 환경별 기능 ON 목록과 실제 응답 fixture를 서버 담당과 확인한다.
- LC/Identity는 읽기만 확인했다. 로컬 변경/HEAD가 실제 테스트 배포와 같다는 보장은 없다.
- Billing public4API 배포·사용자JWT 발급 gate·RevenueCat SDK 실제 거래 ID 매핑·Store sandbox·LC 공동 E2E가 남는다.
- `/internal/**`, AI callback, RevenueCat webhook은 프론트 호출 금지. 사용자 JWT와 workload JWT는 교환 가능한 인증이 아니다.
- 이 목록은 내비게이션용 요약이다. SNS 복구/refresh rotation/음성 제출 등의 필수 body·header는 아래 계약과 해당 Controller/DTO를 확인한다.

## 6. 상세 근거

- Billing: `src/main/java/web/tosunsaeng/billing/domain/payment/api/PaymentController.java`, `PaymentSyncController.java`, `domain/entitlement/dto/response/EntitlementQueryResponse.java`, `domain/payment/query/{PaidQueryResponse,ExamAvailability}.java`, `domain/reservation/paid/PaidExamQueryReader.java`.
- [Billing 배포 상태](../runbooks/BILLING-TEST-BOOTSTRAP-2026-10-16.md), [sync runtime 연결](../runbooks/TMI-212-public-sync-wiring.md).
- Identity 기준 저장소 `/Users/msde76/identity`: `docs/contracts/frontend-firebase-auth-integration-guide.md`, `frontend-firebase-auth-integration-appendix.md`, `guest-merge-progress-api.md`, `support-inquiry-api.md`, `app-version-api.md`, `single-sns-account-recovery-runbook.md`; `src/main/java/web/tosunsaeng/identity/domain/**`의 Controller와 `global/security/jwt/JwtAccessTokenIssuer.java`.
- LC 기준 저장소 `/Users/msde76/app-back-end-learning-core`: `docs/contracts/ten-second-challenge-frontend-api.md`, `notification-device-api.md`, `learning-record-deletion-api.md`, `docs/answer-learning/api.md`; `src/main/java/web/tosunsaeng/domain/**`의 Controller와 `global/error/code/status/ErrorStatus.java`.
- LC의 기존 `docs/contracts/FRONTEND_API_HANDOFF.md`는2026-08-28 기준으로 현재 기능 미구현 여부를 판단하는 유일한 근거로 사용하지 않는다.
