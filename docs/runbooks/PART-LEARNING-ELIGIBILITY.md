# 파트별 유료 콘텐츠 — 무차감 조회 구현

## 운영 배포 설정 — 2026-10-27

사용자 코드·설정 동시 배포 요청으로 prod 자동배포는 현재 task 정의에서 이 기능 flag 하나만 true로 교체하고 새 이미지와 함께 배포한다. test 설정·다른 flag·secret·role·port는 그대로 유지한다. 이미지 및 flag readback 검증을 함께 수행한다. 운영 긴급 OFF rollback 뒤에는 이 workflow의 prod ON 단계도 제거/변경해야 다음 배포에서 다시 켜지지 않는다. DB/schema 변경은 없다. 최초 OFF 후보 실행은 AWS 배포 전 취소했다. 실제 결과는 작업 기록에 추가한다.

- 날짜: 2026-10-23 / 브랜치: codex/part-learning-eligibility
- 기준: [review-2 수락](../contracts/LC-BILLING-PART-LEARNING-ELIGIBILITY-R1-REVIEW.md) + LC `lc-billing-part-learning-content-only-amendment-r1`.
- 콘텐츠 전용 보완 원문 SHA-256: `a2c32dec1f9525cdbc9555373590644ee795453ab0ef5eb2d3ebcf901830ff2b`. 사용자 구현 요청 후 전달된 보완을 최신 범위로 수락한다. 기존 review-2 snapshot/hash는 유지한다.

## 5줄 결론

1. POST /internal/v1/entitlements/part-learning-eligibility를 별도 추가한다.
2. 현재 단일 ACTIVE 유료권만 인정하며 무료권 조회·발급·차감·예약·기간 변경·원장 쓰기는 없다.
3. LC 호출 범위는 유료 본문/모범답안/음성 제공뿐이며 submit/create/retry/worker에는 신규 이용권 검사를 붙이지 않는다.
4. 전용 audience·기본 OFF flag·기존 read-only snapshot/paid reader를 사용하고 기존 재답변은 유지한다.
5. 서버 구현 테스트와 LC 콘텐츠 연결/실환경 검증은 구분한다. 배포/활성화/commit/push는 이번 범위가 아니다.

## API와 설정

요청은 검증된 userId 단일 canonical lowercase UUID JSON, 응답은 userId/decision/reason/checkedAt/validUntil 다섯 필드다. ALLOWED/ACTIVE_PAID_ENTITLEMENT, DENIED/PAID_ENTITLEMENT_REQUIRED, DENIED/ACCOUNT_WITHDRAWN, PENDING/ENTITLEMENT_STATE_UNCONFIRMED 조합을 사용한다. 비허용 validUntil은 명시 null, 정상200 wrapper 없음/no-store.

전용 audience `tosunsaeng-billing:part-learning-eligibility:{env}`, 기존 LC issuer/sub/키/워크로드 TLS 사용. 예약/재답변 목적 토큰은 신규 route를 허용하지 않는다. OFF/다른 port404, 활성 인증 실패401·인가 실패403은 빈 body. 본문 UTF-8/strict JSON/16KiB는 기존 decoder를 재사용한다. 내부400 INVALID_REQUEST 및503 BILLING_TEMPORARILY_UNAVAILABLE의 기존4필드 오류 JSON/Retry-After 규칙 유지. LC 앱 매핑은 부족402/탈퇴401/일시503/연동결함500.

`app.billing.part-learning-eligibility.enabled` / `BILLING_PART_LEARNING_ELIGIBILITY_ENABLED=false` 기본값. 기존 LC receiver/payment reader/MEMBER writer/recovery fence 및 통합 paid readiness 준비가 필요하다. 신규 schema/collection/index/secret 없음. 배포 때 exact 내부 POST 전달 설정과 양측 시계/issuer/env를 별도 확인한다. 기존 reanswer flag와 독립이며 rollback은 새 flag만 OFF로 한다.

## 조회와 최신 콘텐츠 전용 정책

checkedAt은 snapshot 시작 전 고정. 유효기한은 min(checkedAt+5초, 현재 activeEndsAt). 후속 scheduled/continuousAccessEndsAt으로 연장하지 않는다. free/retake는 고려하지 않으며 MEMBER 증거·현재 owner/lifecycle/recovery·기존 유료권 정합성을 유지한다. 단순 hasPendingSync는 유효 paid를 거절하지 않지만 불명 권리/귀속은 PENDING, 중첩 등 정합성 오류는503이다.

LC는 요청 간 캐시 없이 콘텐츠 제공 시 최신 조회를 호출하고 기존 엄격 wall/monotonic 검사를 한다. submit 승인 증거/기한을 저장하지 않는다. 직접 채점 요청 가능성 및 이전 콘텐츠 재사용 위험은 사용자 수락 범위이며 세트 시작기록/토큰/receipt로 다시 막지 않는다. 기존 인증/소유권/삭제/탈퇴/상태/중복 보호와 학습300회 계획은 LC 책임이다. 기존 모의고사/재답변 불변. URL 기존 만료는5초와 별개이며 이미 제공된 콘텐츠 회수 시스템 없음.

review-2의 submit Billing 호출과 접수 전 유효시간 검사는 콘텐츠 전용 보완으로 대체한다. LC C01~C06이 최신 연동 검사 목록이다. 이번 Billing 저장소는 LC 코드/설정을 수정하지 않는다.

## 검증

선별 PartLearning26개 통과(기간중첩 추가 전). 최초 fixture 시작시각 오가정으로 경계1개 실패 후 실제 저장 slot 시각을 기준으로 수정해 통과. 전체 clean test는 아래 후속 결과 기록을 따른다. 테스트는 일회용 Mongo replica-set 및 가짜 키/사용자만 사용하며 운영DB 무접속.

계약 strict/400/503/null 응답, 전용 목적/교차목적/OFF/port/auth, 기동OFF/의존성누락, 실제 read-only snapshot 및 전체 컬렉션 조회 전후 불변, 무료권 제외·MEMBER 불명·환불/탈퇴·시작/종료나노초경계·pending sync·중첩·recovery를 검증한다. LC 콘텐츠 라우팅 C01~C06·실HTTP/TLS·실환경 E2E는 이 테스트의 증명 범위 밖이다.

## 최종 검증 결과 — 2026-10-23

`./gradlew clean test` 성공(4m39s). 전체 1997개, 실패0/오류0/skip0; 신규 PartLearning 27개 포함(합산하지 않음). 기간중첩 추가 사례도 통과. 기존 deprecated 컴파일 경고는 유지. git diff --check 통과. 실환경/LC C01~C06 테스트와 배포는 미실행.
