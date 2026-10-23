# 완료된 환불 시험 이후 재구매 INITIAL 차단 수정

- 날짜: 2026-10-23 / 브랜치: `codex/hotfix-refunded-completed-group`
- 사용자 요청: 운영 모의고사 진입 장애를 빠르게 수정. PLAN-012/TMI-219~221 후속.

## 1. 5줄 결론

1. 환불 fan-out으로 replacementBlocked인 이전 COMPLETED group이 새로운 유료 구매 INITIAL까지 거절하던 문제를 수정한다.
2. 같은 계정의 환불 완료 증거·NEW_AFTER_COMPLETION·다른 정상 유료 구매가 모두 확인된 경우만 예약/confirm을 허용한다.
3. 이전 group·Session·환불 증거·재응시 차단은 변경하지 않고 새로운 group/Session을 생성한다.
4. schema·공개/internal wire·flag 추가 없음. 기존 실패 key는 계속 실패를 재현하므로 새 시작은 새 key를 사용한다.
5. 이 문서는 구현·검증 기록이며 배포나 해당 사용자 실제 시험 생성 성공을 의미하지 않는다.

## 2. 사용자가 반드시 읽어야 하는 내용

이전 코드의 previous 검사와 finishConfirm 양쪽에 replacementBlocked 일괄 거절이 있었다. 예약만 고치면 confirm에서 다시 실패하므로 두 지점에 동일한 한정 예외를 적용한다.

허용 조건: 이전 group COMPLETED, NEW_AFTER_COMPLETION, source SUBSCRIPTION 및 현재 계정 일치, 과거 Purchase REFUNDED/REVOKED 및 accessState REVOKED, refund.appliedAt/effectId 존재, deleted/lifecycleDenied 아님. 기존 exact 원 Session·group·현재 owner·guard 검증도 유지한다. 새 source는 기존 검증을 통과한 ACTIVE paid이며 구매ID와 entitlementID가 모두 다르다. confirm에서도 새 source의 revoke 검사와 이전 lifecycle 검사를 다시 적용한다.

예외에서는 이전 group block 시각/이유를 덮어쓰거나 완료된 Session의 completionAllowed를 변경하지 않는다. 복원권 발급·환불권 재활성화·실패 receipt 삭제도 없다.

## 3. 사용자가 결정해야 하는 사항

추가 제품 정책 선택 없음. 사용자 「바로 운영 서버에 올려줘」로 운영 반영 승인. main 기준 작업트리에서 이 수정만 반영한다.

## 4. 주요 위험과 미확인 사항

- 이번 긴급 수정은 실제 발생한 **완료된 환불 시험 → 다른 유료 구매** 경로로 한정한다. 미완료 시험·무료권 전환·phone/삭제 예외를 확대하지 않는다.
- 실패가 이미 영속화된 동일 key는 성공으로 바꾸지 않는다. 배포 후 앱의 새 시험 시작(new key)으로 검증해야 한다.
- PLAN-016의 provider RefundEvidence 연결·금액 대사 분리는 별도이며 이 수정으로 완료되지 않는다.
- 실제 운영 사용자 JWT 시험 생성은 실행하지 않았다. 사용자 대신 시험을 만들거나 기존 기록을 변경하지 않는다.

## 5. 구현·검증

- PaidReservationService: completedRefundContext·differentPaidPurchase로 예외 제한, reserve/confirm에 적용, 이전 기록 쓰기 생략.
- RefundFanoutIntegrationTest: 실제 PurchaseCore 환불/fan-out/재구매 및 상태 이벤트로 운영 조건 재현. 수정 전 재현 테스트가 TRANSITION_CONTEXT_CONFLICT로 실패함을 확인했다.
- 신규8개: 성공/confirm·reserve replay 및 과거자료 불변, 잘못된 원Session, lifecycle deny, 비환불 차단, 미완료 차단, 신규 구매 없음/terminal key 유지, 새 구매 confirm전 revoke, reserve후 lifecycle deny.
- 기존 환불 fan-out 선별검증 통과 후 신규2개 보강을 포함한 전체 clean test 진행. 최종 결과는 아래에 기록한다.

## 6. 배포·검증 순서

테스트 통과 → main 기준 변경 파일만 커밋/푸시 → PR main 병합·운영 자동배포 → 대상 사용자의 새 시작 성공 확인. 사용자 긴급 운영 배포 요청으로 테스트 서버 배포는 이번 단계에서 생략한다. 기존 기능 flag/schema 변경 불필요. 완료된 환불 group의 replacementBlocked를 운영에서 해제하는 우회는 사용하지 않는다. 롤백해도 이미 생성된 새 그룹·예약·원장·이용권을 과거 snapshot으로 되돌리지 않는다.
