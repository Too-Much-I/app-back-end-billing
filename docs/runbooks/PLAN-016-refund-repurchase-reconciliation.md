# PLAN-016 환불·재구매 자동 연결 구현

- 날짜: 2026-10-23 / 브랜치: `codex/plan-016-refund-repurchase-reconciliation`
- 승인: 사용자 「좋아 이렇게 하는 걸로 브랜치 새로파서 구현해줘」.
- 기준: [최소 계획](../plans/PLAN-016-refund-repurchase-reconciliation.md).

## 1. 5줄 결론

1. 인증된 RevenueCat API `status=refunded`를 기존 RefundEvidence·환불 Transaction에 연결했다.
2. 같은 구매의 정확한 기술 보류만 기존 case에서 종결하고 counter를 같은 Transaction에서 감소시킨다.
3. 권리 종료 원장까지 확인된 REFUND_AMOUNT_UNKNOWN은 OPEN으로 남지만 정상 새 구매의 이용을 막지 않는다.
4. 새 공개 API·컬렉션·index·기능 flag·복구 큐는 없다. 기존 조회 worker와 환불 outbox를 사용한다.
5. 최종 `./gradlew clean test` 2,021개 실패0/오류0/skip0 통과. 실제 DB·설정·배포·commit/push는 수행하지 않았다.

## 2. 반드시 읽을 내용

### 입력과 멱등성

API의 기존 환경·Store·상품·owner·완전한 익명 alias·quantity 검사를 통과한 최종 refunded만 evidence로 만든다. `revenue_in_usd`/상품가격/구매시각을 환불액/환불시각으로 사용하지 않는다. 반환액과 확정시각은 확인 가능한 정보가 없어 null로 둔다.

환불 API facts만 `api-refund-r2`로 바꿔 별도 observation key를 만든다. 과거 `api-purchase-r1`의 증거 없는 환불 observation 및 digest1 bytes는 변경하지 않는다. 동일 거래의 종료 효과는 기존 ACCESS_REVOKED 원장으로 한 번만 적용한다. 정상 구매 observation key는 불변이다.

CANCELLATION webhook은 최종 환불 증거가 아니다. 정확한 거래/상품/환경/owner/구매시각 후보를 기존 reviewCandidate에 저장할 수 있는 경우만 저장한다. 다른 회원 alias·불완전한 거래 근거·다른 이벤트 종류를 추정해 보완하지 않는다. 실제 환불은 API 조회로 확인한다.

### 보류 정리와 금액 확인

- 자동 종결 대상: exact `REVERSAL_REQUIRES_PLAN_012`, 후보가 일치하는 `EVENT_REQUIRES_LOOKUP`.
- purchase의 REFUNDED/REVOKED·access REVOKED·READY, refund effectId/appliedAt와 ACCESS_REVOKED 원장 일치를 확인한다.
- 동일 환경·계정·lifecycle·Store·앱·거래·lookup version·owner·상품·구매시각만 매칭한다.
- reviewState/resolvedObservationKey/purchaseId를 기존 case에 기록하고 counted는 false로 바꾼다. counted=true였던 책임만 1회 감소한다.
- 금액만 모르는 case는 OPEN/UNKNOWN을 보존하고 counted=false로 분리한다. 조회 시에도 연결된 구매와 종료 원장을 같은 snapshot에서 재확인한다. 나중에 금액이 확인되면 해당 UNKNOWN case를 종결한다.
- REFUND_AMOUNT_CONFLICT·owner/환경 불명·실제 sync/정합성 문제는 예외 대상이 아니다. 기존 운영 조회에서 UNKNOWN 이유로 계속 확인 가능하므로 검토 건수가 0이라는 보장은 없다.
- 한 Transaction의 자동 종결 상한은 100건이다. 반복 apply/인증 webhook replay로 다음 묶음이 수렴하며 이미 분리된 UNKNOWN은 반복 처리하지 않는다.

### 기존 복구 건

이미 환불된 구매는 기존 appliedAt/effectId·후속 이용권 기간·보상 연장·ACCESS_REVOKED를 유지한다. 새 observation의 검증/보존시각이나 버전은 갱신될 수 있다. 거래 근거가 없는 과거 case는 운영 확인으로 남는다. 전체 과거 데이터의 자동 이관을 구현하지 않았다.

## 3. 추가 결정과 배포 전 확인

제품 정책 추가 선택은 없다. 배포 시 기존 refund processing, review-resolution, provider worker와 reconciliation 설정 및 r7/r9 schema 준비 여부를 확인한다. 이 작업은 해당 설정을 변경하지 않았다.

정기 조회 주기는 기존 그대로다. 활성/예약 이용권은 통상 6시간, 그 외 최근 계정은 24시간 주기이며 앱 sync가 있으면 기존 sync worker가 조회한다. 웹훅 수신 즉시 별도 조회 작업을 추가하지 않는다. 정기 reconciliation OFF 또는 인증/계약 오류로 운영 격리된 worker는 이 코드만으로 재개되지 않는다.

테스트 서버에서 실제 provider 환불→재구매를 확인한 뒤 운영에 반영한다. 실제 과거 건 재처리는 별도 운영 절차다. API·schema는 유지하지만 금액 확인만 남은 case의 counted 의미가 달라졌으므로 rollback 시 구 reader는 다시 PENDING을 표시할 수 있다. 데이터 snapshot 되돌리기는 하지 않는다.

## 4. 위험·미확인

- 실제 RevenueCat/Store/LC/운영 DB를 이번에 호출하지 않았다. HTTP fixture·로컬 replica-set 검증과 실환경 E2E는 구분한다.
- 실제 provider에서 정확한 거래 후보를 확보할 수 없는 webhook은 수동 확인 대상이다.
- 금액 확인은 별도 운영 책임이다. 이번 변경으로 실제 환불액이 새로 확인되거나 Store 환불이 실행되는 것은 아니다.
- 이전 완료그룹 INITIAL hotfix는 기존 작업이며 이번 자동 연결과 구분한다.

## 5. 변경 파일·검증

제품: RevenueCatPurchaseDecoder, RevenueCatWebhookDecoder, WebhookReceipt, PurchaseCore, RefundReviewPolicy(신규), PaidEntitlementReader.

검증: RevenueCatAdapterTest, RevenueCatWebhookTest, PurchaseRefundIntegrationTest, PaymentSyncIntegrationTest. 실제 decoder→core, 기존 periodic worker→API fake response→core→reader, 양방향 순서·중복/경합·unknown commit·case 실패 rollback·legacy bytes·보상 기간 불변·불명 거래 차단을 확인한다. 최종 실행 결과는 아래와 같다.

기존 미커밋 문서·운영 스크립트·PaidReservationService/RefundFanoutIntegrationTest 긴급 수정은 보존했다. PaidReservationService는 이번에 변경하지 않았다. RefundFanoutIntegrationTest와 RefundOperationsReaderTest의 공용 환불 fixture는 구매시각을 원 구매와 일치하도록 각 한 줄 정정했다(환불 확정시각은 별도 유지).


## 6. 실행 결과와 다음 단계

- 선별 provider/refund/sync 테스트 통과 후 전체 검사에서 두 기존 공용 fixture가 원 구매시각 대신 NOW를 넣던 불일치를 발견했다. 거래 검사 완화 없이 fixture를 원 구매시각으로 정정하고, 불일치 환불 거절 테스트를 추가했다.
- 최종 `./gradlew clean test`: 2,021개, 실패0/오류0/skip0, 4m49s. 이번 신규 16개 포함이며 기존 테스트와 합산하지 않는다.
- `git diff --check`, 계획/운영 안내 상대 링크 검사 통과. 기존 deprecated/VM 경고만 남았다.
- 테스트 리포트: `build/reports/tests/test/index.html`, `build/test-results/test/TEST-*.xml` (다음 clean에서 재생성).
- 실제 provider/Store E2E·테스트 서버 배포는 미실행. 다음 단계는 해당 브랜치 변경 검토 및 테스트 서버 반영이다.
- 예상 밖 제품 변경 없음. 기존 사용자/선행 작업 변경은 보존했고, 기존 두 테스트의 공용 fixture 구매시각 정정 외 관련 없는 변경은 추가하지 않았다.
