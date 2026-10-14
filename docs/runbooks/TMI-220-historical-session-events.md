# TMI-220 — Billing 과거 Session 이벤트 보완

- 날짜: 2026-10-14 / 브랜치: `codex/TMI-220-refund-cleanup-delivery`
- 기준: [승인된 기접수 작업 경계](../contracts/TMI-220-REFUND-PROTECTED-SESSION-CLARIFICATION.md). TMI-220 전체 완료가 아니라 과거 Session 상태 이벤트의 Billing 측 보완이다.

## 1. 5줄 결론

1. 같은 group의 과거 S1도 영속 confirm·completionAllowed·source/owner/lifecycle 근거가 있으면 terminal 이벤트를 반영한다.
2. S1만 완료/실패로 전이하고 현재 group·S2·새 operation guard는 쓰지 않는다.
3. inbox·S1 전이·구매 이용 원장은 기존 단일 Mongo Transaction으로 반영한다.
4. wire/fixture·단일 confirmedSessionId·금융 root·기본 OFF는 변경하지 않는다.
5. LC 접수/Callback/Job 보완과 실제 공동 HTTP 검증, 환불 정리/publisher는 후속이다.

## 2. 반드시 알아야 할 처리 범위

`PaidAttemptEventService`는 인증·strict decode를 거친 기존 LC 상태 이벤트를 받는다. LC가 작업별 최초 접수/전체 완료 요건을 판정하며 Billing은 답안이나 Job을 복제하거나 completionAllowed로 전체 접수를 추정하지 않는다. v1 COMPLETED의 기존 필수 evidence 검증은 HTTP decoder에 그대로 남아 있다.

| 과거 S1 입력 | Billing 처리 |
| --- | --- |
| ACTIVE·confirm 있음·completionAllowed·귀속 일치, COMPLETED | S1 COMPLETED·terminalAt·이용 원장, group/guard 불변 |
| 위 조건에서 RETAKE_AVAILABLE | S1 FAILED만 반영, 현재 group을 재응시 가능으로 바꾸지 않음 |
| 위 조건에서 GRADING | inbox STALE, 현재 group GRADING으로 변경하지 않음. 별도 Session GRADING schema를 신설하지 않음 |
| 이미 terminal인 S1의 새 이벤트 | 기존 STALE 처리, 부활/추가 이용 원장 없음 |
| 동일 eventId/digest | 기존 DUPLICATE, 이중 효과 없음 |
| confirm/완료 허용 근거 없음·source/owner 불일치 | 기존 EVENT_TARGET_CONFLICT |
| group/Session deleted 또는 lifecycleDenied, 계정 탈퇴 | 기존 EVENT_TARGET_CONFLICT, 저장 무효 |

기존 exact Session의 owner-rebind terminal 정리 표시는 유지한다. S1 terminal 전이는 해당 연결에만 적용하며 다른 Session을 정리하지 않는다. 별도 신규 제출/녹음/Session 허용 경로는 추가하지 않는다. 같은 시각 confirm/refund 사례도 시각 대소 비교가 아닌 기존 원자적 confirm·구매 root 경합의 저장 결과를 따른다.

## 3. 결정 사항

추가 제품 선택은 없다. 과거 GRADING에는 새 group 상태를 만들지 않으며 기존 내부 API의 STALE/204 의미를 유지한다. LC가 단순 204를 신규 채점 승인으로 해석해서는 안 된다. 이는 작업별 기접수 검증을 대신하지 않는다.

## 4. 미확인 사항

- LC의 exact 접수·TTL/marker·Callback·시스템 재시도·Summary 구현과 C01~C03 전체 실행은 이번 Billing 테스트가 증명하지 않는다.
- TMI-217 병행 브랜치의 최종 삭제/보존 접점 및 양측 실제 HTTP 경합은 병합·공동 검증 단계에서 확인한다. 이번 테스트의 deleted/lifecycleDenied는 현재 Billing 검사 경계이며 전체 삭제 workflow E2E가 아니다.
- TMI-220 환불 예약/group 정리·불변 outbox·LC publisher는 아직 미구현이며 processing ON 기동 거절을 유지한다.

## 5. 구현 및 검증

- [처리 코드](../../src/main/java/web/tosunsaeng/billing/domain/reservation/paid/PaidAttemptEventService.java)
- [실Mongo 회귀](../../src/test/java/web/tosunsaeng/billing/domain/reservation/paid/HistoricalPaidSessionEventIntegrationTest.java): 실제 환불 코어 반영 뒤 과거 완료/실패, 중복·역순, 현재 group/S2/guard 불변, confirm/permission/source 불명, 삭제/탈퇴, 원장 실패 rollback, 동시 duplicate, 새 pending guard 보존. C04~C06/H16의 Billing 측 범위다.
- [기존 재현 테스트](../../src/test/java/web/tosunsaeng/billing/domain/reservation/paid/RefundProtectionContractIntegrationTest.java)는 변경하지 않았다.
- 단위 service 호출 테스트는 LC가 올바른 완료 evidence를 만들었음을 증명하지 않는다. 전체 실행 결과는 CURRENT_STATE/WORKLOG에 기록한다.

## 6. 다음 순서

Billing 독립 회귀 → LC 보완 결과와 증거/상태 전이 대조 → 환불 정리·전달 구현 및 실제 HTTP 경합 검증. 실제 DB 설치·Store 환불·배포/판매 활성화·외부 발송은 하지 않는다.
