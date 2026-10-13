# PLAN-011 기존 이용 증거 재사용 검토

- 날짜: 2026-10-13 / 요청: 「한번 확인해봐」
- 방법: Billing/LC production source 정적 읽기. 런타임·배포·E2E 확인 아님. LC 수정 없음.
- 결론: 최소 이용 증거를 이유로 기존 AttemptGroupStatusChanged v1 wire를 확장하거나 별도 증거 이벤트를 추가할 필요는 현재 확인한 범위에서 없다. Billing 내부 paid 연결·영속 증거 저장·consumer의 paid 분기는 필요하다. 새 구현/계약 승인으로 해석하지 않는다.

## 있는 정보

- LC AttemptGroupEventPayload/StateCoordinator는 schemaVersion1, occurredAt, userId, attemptGroupId, sessionId, targetStatus를 발신한다.
- COMPLETED에는 requiredFeedbackQueryable/validScoreQueryable/summaryQueryable/evidenceVersion이 이미 포함된다. LC evaluator가 completion evidence를 만든다. 이는 당시 결과 제공 가능 증거이며 사용자 실제 열람 증명이 아니다.
- RETAKE_AVAILABLE에는 정규화된 failureCode가 이미 포함된다. GRADING 이벤트도 기존에 있다. 제출 정확 시각/모든 실패 상세까지 증명한다는 뜻은 아니다.
- Billing strict decoder는 완료 세 boolean=true 및 evidenceVersion1, 실패 code를 검증한다. 신규 필드를 추가하면 기존 strict contract에 걸린다.
- Billing AttemptSession은 proposedAt/confirmedAt/terminalAt/state와 operation/group/session 연결을 보유한다. Group은 현재 상태·completedAt/updatedAt을 보유한다.

## 내부 보완 필요

- Reservation/Group은 현재 무료 trialClaim/consumption 기반이다. 구매/entitlement와 immutable source snapshot 연결은 PLAN011에서 추가해야 한다. LC event의 group/session으로 Billing이 source를 조회하면 되며 LC가 purchaseId를 추가 발신할 필요는 없다.
- AttemptGroupEventService는 evidence/failureCode의 상세 값을 영속 projection에 쓰지 않는다. 완료는 상태/terminal 시각으로, 실패는 FAILED/RETAKE_AVAILABLE로 반영한다. 상세 실패 사유/수신 당시 evidenceVersion과 boolean을 장기 조회하는 구현은 없다.
- EventInbox는 eventId/producer/type/schema/digest/disposition/receivedAt/purgeAt만 저장하고 120일 보존한다. digest로 evidence 원문을 복원할 수 없고 inbox만으로 5년 payment 증거를 유지할 수 없다.
- 기존 event 적용 Transaction에 최소 증거 snapshot/append-only paid ledger effect를 함께 기록하도록 설계한다. duplicate/stale/동일eventId conflict·역순·replacement 후 기존Session이벤트·unknowncommit에서 이중 기록/잘못된 귀속을 막는다. 저장 위치/exact index/schema/retention은 PLAN011·013에서 동결한다.
- consumer는 현재 BillingSubjectLink/trialClaim 검사를 사용한다. paid source에는 승인된 paid owner 경계를 구현해야 하며 무료 검사를 단순 삭제하면 안 된다. 무료 event/epoch/owner fence 회귀를 유지한다.
- confirm-first 환불 완료 예외와 revoke wire는 별도 공동 계약 과제다. 이번 검토는 revoke event 변경까지 불필요하다고 주장하지 않는다.

## 권장

상태 이벤트 v1 재사용 → Billing 내부 purchase→group/session 귀속 → 수신 시 기존 evidence/failureCode의 최소 영속 기록. 사용자 수동 선택이 필요한 새 필드/버전은 현재 발견되지 않았다. 이미 COMPLETED인 legacy 문서에 실제 상세 증거가 없으면 임의 복원하지 않고 확인 범위를 표시한다. 기존 completion state가 승인된 strict 검증을 거친다는 점과 상세 snapshot 부재를 구분한다.

근거 파일: Billing domain/attempt/api/AttemptGroupEventDecoder.java, application/AttemptGroupEventService.java, domain/entity/AttemptSession.java·AttemptGroupEventInbox.java, repository/AttemptSessionRepository.java·AttemptGroupRepository.java; LC domain/exams/attemptgroup/domain/AttemptGroupEventPayload.java 및 application/AttemptGroupStateCoordinator.java. 정책 근거 ADR004 §5.8.1/PLAN011. 코드 변경 없이 정적 검토이므로 Gradle 미실행.
