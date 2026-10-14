# 환불 전 confirm Session 완료 허용 — 정책 변경

> 2026-10-13 사용자 확인: 새 제출 시간 제한은 추가하지 않는다. 아래 “기존 시험/제출 기한”은 새30분/60분/24시간 마감 도입 근거가 아니다. 환불 전 confirm된 exact Session 완료 허용과 기존 채점/lifecycle 종료 규칙을 따른다. 상세 보완은 [인계 R2](LC-BILLING-PAID-EXAM-HANDOFF-R2.md) §2를 우선한다.

- 승인일: 2026-10-13
- 사용자 승인: 「좋아 그정도는 괜찮을 거 같아 그렇게 하는 걸로 해줘」
- 상태: Billing 정책/계획 반영. LC 공동 exact wire 확정·상대 문서 반영·코드 구현·배포 승인/완료 아님.

## 확정 변경

1. provider-confirmed refund/revoke보다 먼저 Billing confirm이 성공한 exact Session은 기존 시험/제출 기한 안에 답안 제출·채점·terminal 완료를 허용한다. group의 다른 Session, 새 INITIAL/replacement, 기한 연장은 허용하지 않는다. 이미 실패/종료한 Session을 재개하는 근거도 아니다.
2. refund가 먼저 commit되면 미확정 Reservation confirm을 거절하고 LC orphan Session을 보상한다. 기존 reserve→LC Session commit→confirm 순서를 유지한다. reserve 성공만으로 완료 예외가 생기지 않는다.
3. 해당 구매의 잔여 권리는 종료한다. 환불 뒤 새 시험/재응시를 차단하고 다른 구매·무료권과 COMPLETED history는 보존한다. 금액 원장·appliedAt reflow·부분 환불 규칙은 변경하지 않는다.
4. paid 전용 채점 승인 API와 결과 재확인 API는 구현 범위에서 제외한다. 무료 v1 이벤트의 204 의미를 승인으로 바꾸지 않는다. 환불과 confirm을 공통 Transaction/CAS로 경합시키고, exact confirmed Session의 영속 귀속 증거로 예외를 판정한다. 7일 command TTL/LC 현재 상태/클라이언트 시각에만 의존하지 않는다.
5. durable revoke outbox는 유지한다. 역할은 구매/group의 신규·replacement 차단과 기존 exact confirmed Session 예외 전달이다. 늦은 상태 이벤트가 root deny를 해제하지 못한다. LC는 confirm 결과가 불명확하면 status-first로 확인하며 reserve만으로 사용을 확정하지 않는다.

## 함께 확정할 LC 계약

- 기존 reserve 응답에 출처 필드를 추가하는 방향을 채택한다. LC reader 먼저 배포→Billing 새 응답→검증 후 paid 활성화. exact 필드/필수성/legacy 해석/롤백 및 미지원 LC 차단은 공동 동결 전이다. 별도 v2가 필수라는 종전 제안은 대체한다.
- revoke wire의 기존 gradingSessionId/GRADING 전용 예외 제안은 그대로 구현하지 않는다. exact confirmed Session 식별·귀속/순서 증거·deny version·중복/역순·revoke-before-session·기한/terminal 경계와 인증 audience/권한을 LC와 먼저 동결한다. 새 field/route를 이 문서에서 임의 확정하지 않는다.
- 사용자 guard는 protected confirmed Session의 유효 진행 중 유지하고 terminal/기한 종료 뒤 exact CAS로 해제한다. 환불 시 무조건 group 종료/guard 해제하지 않는다. RETAKE_AVAILABLE의 새 replacement는 차단한다.
- confirm-first/refund-first, 응답 유실, 전파 지연, 재응시 예약 경합, 기존 confirm 증거 누락, 만료 기한/늦은 이벤트 회귀를 공동 검증한다. protected Session을 중단하는 구버전 deny consumer로 무조건 롤백하지 않는다.

## 4번 이용 증거와 구분

구매별 source snapshot/최소 이용 증거 보존 정책은 유지한다. 별도 유료 승인 API 제거와 무관하다. 기존 상태 이벤트를 확장할지 별도 증거 이벤트로 보낼지에 대한 새 선택은 아직 승인하지 않았다. 답안/음성/피드백 원문을 Billing으로 복제하지 않는다.

이 문서가 ADR004/PLAN011·012 및 통합 계약의 종전 OPEN 제출·채점 차단/GRADING-only 완료, 전용 paid 채점 승인 API, reserve v2 필수 표현보다 우선한다. 과거 승인 기록은 당시 이력으로 보존하며 변경된 정책을 구현에 적용한다. 다른 계약은 변경하지 않는다.
