# 완료 시험 재답변 — Billing 무차감 조회

- 날짜: 2026-10-21
- 브랜치: `codex/feedback-reanswer-eligibility`
- 기준: [R1 draft-5](../contracts/LC-BILLING-FEEDBACK-REANSWER-ELIGIBILITY-R1-DRAFT.md)
- 사용자 구현 승인에 따른 Billing 구현. LC/Identity 구현·공동 fixture 동결·배포·활성화 완료를 의미하지 않는다.

## 연결

`POST /internal/v1/entitlements/reanswer-eligibility`에 정확한 `{"userId":"lowercase canonical UUID"}`를 보낸다. 전용 audience는 `tosunsaeng-billing:reanswer-eligibility:{env}`다. 기존 LC issuer/sub/public key와 workload TLS connector를 재사용한다. 예약용 audience로 호출하면 403이다.

설정은 `app.billing.reanswer-eligibility.enabled`, 환경 변수는 `BILLING_REANSWER_ELIGIBILITY_ENABLED`이며 기본 false다. OFF·다른 local port는 인증 전에 빈 404, query·인증 실패는 빈 401, 검증된 다른 목적/subject/method는 빈 403이다. 기존 route/fixture는 유지한다. public ALB 경로를 추가하지 않는다.

ON은 기존 LC receiver, payment entitlement query, MEMBER evidence writer, recovery fence 및 통합 paid reader가 필요하다. 현재 빈 의존성으로 paid foundation/recovery/admission과 기존 schema 검증이 선행된다. 의존성이 없으면 기동 실패하며 조회 때 metadata·recovery barrier도 확인한다. 새 컬렉션·index·DDL·secret은 없다.

## 판정

모든 업무 조회를 기존 EntitlementSnapshotExecutor의 read-only Mongo snapshot 안에서 수행한다. checkedAt은 snapshot 시작 전 고정하며 snapshot 재시도로 갱신하지 않는다.

- WITHDRAWN은 DENIED/ACCOUNT_WITHDRAWN.
- 현재 유효한 유료권 또는 실제 발급된 가용 무료권이 있으면 ALLOWED.
- 최초 무료권의 예상 발급 수량과 소비한 그룹의 replacement/retake 가능성은 제외한다.
- 최초 체험은 현재 phone eligibility·Claim·link·grant 귀속과 기존 정합성 검사를 유지한다.
- 복원·초대권은 현재 owner/ACTIVE/MEMBER 근거 및 가용 단위를 확인한다. hold·소비된 단위는 제외한다.
- 하나가 확실하면 다른 source의 PENDING이나 단순 hasPendingSync만으로 거절하지 않는다. 일반 시험 생성 guard도 차단 조건으로 복사하지 않는다.
- 둘 다 없음이 확정되면 DENIED/ENTITLEMENT_INSUFFICIENT. 불명은 PENDING/ENTITLEMENT_STATE_UNCONFIRMED.
- 저장소·snapshot·정합성 실패는 503이다. 데이터 손상을 권리 없음으로 숨기지 않는다.

ALLOWED의 validUntil은 무료권 조건이면 checkedAt+5초, 유료만이면 해당 active slot 종료와 +5초 중 빠른 값이다. timestamp는 Instant.toString()의 UTC 문자열이며 소수초 최대 9자리를 보존한다. DENIED/PENDING도 validUntil:null을 명시한다. 발급·hold·차감·원장·guard·operation·receipt는 쓰지 않는다.

## 오류와 LC 책임

잘못된 JSON/Content-Type/encoding/16KiB 초과는 400 INVALID_REQUEST, 정확한 문구는 `The request is invalid.`다. snapshot/저장소 실패는 503 BILLING_TEMPORARILY_UNAVAILABLE, `Billing is temporarily unavailable.`, retryable:true, Retry-After:1이다. 기존 4필드 InternalApiError와 요청별 UUID v4 correlationId를 사용한다. 모든 정상/계약 오류/인증 응답은 no-store다.

LC 공개 매핑은 앱 자체 4xx 유지, 정상 부족 402, 정상 탈퇴 401, timeout/PENDING/일시 장애 503, 서버 간 인증·주소·계약 오류 500 COMMON500+운영 경보다. 이 Billing 변경은 LC exception mapper/경보를 구현하지 않는다. LC에서 내부 401을 앱 401로 전달하면 안 된다.

LC는 connect 2초/request 5초, 자동 재시도 0, monotonic/wall clock 검사 및 현재 owner/delete/withdrawal/session refund deny를 적용해야 한다. Job과 승인 증거는 기존 QuestionGradingJob에 같은 Transaction으로 저장하고 checkedAt/validUntil은 BSON string으로 보존한다. 동일 Job replay는 기존 증거를 사용한다. 별도 durable Billing 승인 시스템은 없다.

## 검증 범위와 활성화 전 확인

새 strict wire/응답·오류/나노초 경계 테스트, 기존 workload 필터의 새 목적·교차 목적 차단 테스트, startup OFF/누락 의존성 테스트, 일회용 Mongo replica-set에서 paid/free/미발급trial/탈퇴/hold/소비/MEMBER/guard/복구 barrier 및 전체 컬렉션 조회 전후 동일성을 검증한다. 실제 외부 HTTP/TLS/E2E·LC 독립 parser/Job Transaction 검증은 별도다.

실환경 ON 전 LC 구현·공동 검증, 양측 키/audience·route·시계 동기화·설정 readiness를 확인한다. 오류500 경보는 LC 담당이다. 5초는 관측 유효성이지 commit/deny 전달 보장이 아니다. 서로 다른 동시 Job 여러 건이 접수될 수 있는 승인된 한계는 유지한다.

롤백은 새 flag OFF로 해당 route를 빈404 처리한다. 신규 데이터가 없으므로 이 기능의 역이관은 없다. 기존 paid/trial/초대/공개 조회 설정은 변경하지 않는다.

## 실행 결과

2026-10-21 최종 `./gradlew clean test`: 1,933개, 실패0/오류0/skip0(4m39s). 신규29개는 wire/controller16·workload3·startup2·Mongo integration8개이며 전체 합계에 포함된다. 최초 전체 실행에서 기존 구매 동시성 테스트1건이 bounded transaction 재시도로 실패했고, 해당 테스트 단독 및 전체 재실행은 코드 변경 없이 통과했다. 이 이력은 WORKLOG에 보존한다. git diff --check 통과. 배포/공동E2E 결과로 확대하지 않는다.
