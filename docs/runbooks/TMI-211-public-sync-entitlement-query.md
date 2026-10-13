# TMI-211 — public sync·무료/유료 통합 조회 OFF 구현

- 날짜: 2026-10-13 / 브랜치: feat/TMI-211-payment-sync-entitlement-query
- 기준: [2차 계획](../plans/PLAN-010-phase-2-public-sync-and-entitlement-query.md), [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md).
- 범위: Billing 로컬 코드·문서. 실제 provider/worker·Store 검증·배포·판매 활성화가 아니다.

## 1. 구현 결과

`POST /api/v1/payments/sync`는 MEMBER/billing:purchase·검증된 sub·ACTIVE 구매 계정/current ref를 확인한다. 정확한 body는 store/transactionId, 필수 Idempotency-Key는 lowercase UUID v4다. 신규 요청은 command와 lifecycle pendingEvidence를 함께 commit한 뒤 202/Retry-After:5, 이미 반영된 본인 거래는 200이다. 타인 거래는 409, 탈퇴는 403, 계정 미발급은 409, 저장소 오류는 503이다. raw 거래 hint는 저장하지 않고 기존 HMAC lookup을 사용한다.

source 검증·지급은 TMI-210 코어에 맡긴다. bindObservation은 이미 durable한 source의 계정/환경/거래 일치를 확인하며 complete는 command pending만 해소한다. source 반영과 command 완료 사이 장애는 pending을 보존하고 재완료로 수렴한다. REVIEW_REQUIRED는 pending을 보존하고 CONTACT_SUPPORT를 표시한다. 이 메서드를 실제 실행하는 provider/worker는 아직 없다.

`GET /api/v1/entitlements`는 기존 무료 응답에 paid와 examAvailability를 조건부 추가한다. paid는 현재 기간/연속 종료/다음 시작/미처리 sync를 표시한다. 실제 Spring Mongo snapshot에서 무료와 유료를 함께 읽으며 GET은 데이터 변경을 하지 않는다. 불명 reversal은 PENDING, 저장소·누락/불일치 오류는 503이다. 무료 status 의미는 유지한다.

canTakeExam은 action에서 파생한다(ALLOWED=true/BLOCKED=false/PENDING=null). 현재 PLAN-011 판정기는 미구현이므로 기본 응답은 null/ADMISSION_NOT_READY다. 무료 또는 유료권이 있다는 이유로 종합 시험 시작을 허용하지 않으며 기존 무료 응답·reserve는 변경하지 않는다.

## 2. 명시 schema와 요청 제한

`PaymentSyncSchema`가 sync-command-r1의 executable manifest다. `payment_sync_commands` 필수 필드·BSON Long/date·명시 null·추가 필드 거절과 상태/count 일치를 검사한다. 아래 인덱스는 순서까지 고정하며 partial/TTL/sparse/hidden 없음, simple collation이다.

| 이름 | key 순서 | unique |
| --- | --- | --- |
| ux_payment_sync_operation | environment,userId,operationId | true |
| ix_payment_sync_account_state | environment,accountId,state,createdAt | false |
| ix_payment_sync_due | state,createdAt | false |
| ix_payment_sync_source | environment,sourceObservationKey | false |

설치는 모든 writer 격리·ALL_BLOCKED 하의 명시 maintenance 호출뿐이다. 기존 collection/index를 자동 변경하지 않는다. 통합 조회 ON 시 foundation/core/sync/recovery는 verify-only이며 schema 불일치는 기동 실패다. due 인덱스는 현재 상태 조회용이고 실제 worker lease/nextAttemptAt는 후속 이관이다. terminal 120일 정리와 미해결 보존 운영·purge 구현 전 실접수를 켜지 않는다. 이번에 TTL/정리 worker는 추가하지 않았다.

sync는 분산 고정1분12회, 통합 조회 ON은60회다. 429에는 bucket 종료까지 남은 정수초를 전달한다. 확장 OFF는 기존 무료 limiter와 DTO를 유지하며 products/account quota/key는 불변이다. 모든 사용자 응답 no-store.

## 3. 운영 차단과 후속

- billing.payment.sync-enabled=false, entitlement-query-enabled=false 기본값 유지.
- sync ON은 현재 **모든 runtime profile에서 기동 거절**한다. fake provider를 등록해 우회하는 옵션 없음. MVC 테스트만 기동 후 flag를 바꾸고 mock service로 HTTP 계약을 검증한다.
- 실제 provider 인증/SDK hint 매핑·공통 worker·재시도/lease·미해결 운영/보존을 구현한 뒤에야 sync runtime을 연결할 수 있다.
- 유료 조회는 무료 reader·recovery fence·명시 schema·JWT/connector/limiter 준비가 필요하다. paid admission과 환불/revoke/reflow 준비 전 판매 활성화 금지.
- PLAN-011/012, 205 실제 금융 coverage, 실환경 부하·ingress·JWT·Store E2E는 후속이다. 실제 삭제/배포/키 설정은 변경하지 않았다.

## 4. 검증

최종 `./gradlew clean test bootJar`: **1,215개 통과, 실패0/오류0/skip0**, 기존1,168 대비47개 추가. `git diff --check` 통과. 최초 전체 실행에서 기존 SecurityConfigTest의 MVC decoder 의존성 누락6건을 확인해 test mock을 추가했고, 이후 clean 전체 재실행은 모두 통과했다. 운영 인증을 완화하지 않았다.

strict JSON/HTTP 인증·본인 scope·202/200/429/503·flag/port·기존 무료 DTO 회귀, 실제 Mongo replica-set의 command/source pending 원자성·중복/동시 retry·타인 거래·탈퇴 전후·epoch·overflow·schema·5상품/기간 경계·stacking/환불 불명·반복 read 무변경·동시 commit snapshot 일관성을 테스트한다. 전체 실행 결과는 CURRENT_STATE와 WORKLOG에 기록한다. 실제 provider/Store 호출은 없다.
