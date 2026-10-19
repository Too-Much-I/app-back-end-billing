# LC 유료 시험 인계 R2 잔여 항목 검토

- 날짜: 2026-10-13 / 요청: 「다른 것도 확인해줘」
- 상태: Billing/LC source 읽기 및 초안 보정. code/test/deployment 변경 없음. concurrent PLAN010 개발 변경 보존.
- 결과: 새 제출 마감 없이 진행 가능하나 source 복구·confirm 영속 재현·revoke 예외/보존 규칙은 구현 전 공동 수락 필요.

| 항목 | 확인한 현행 | 권장/남은 작업 |
| --- | --- | --- |
| source 호환 | LC response strict, operation/Session에 paid source 없음 | LC reader+operation snapshot 선배포, Billing snapshot/status/source writer 후배포. 신규 v2 없이 exact field만 추가 |
| 무료 legacy | 기존 무료 근거/paid 활성화 전 inventory 필요 | absent를 무조건 TRIAL 처리 금지. 지금 OFF라는 사실만으로 과거 source 증명 불가 |
| confirm 복구 | Billing status와 confirm 모두 reserve command 존재에 의존. AttemptSession confirmedAt/operation/group 연결은 영속 | PLAN011에서 TTL 후 caller/user/source 귀속·confirm 결과/digest를 검증하는 경로 보완. 기존 코드가 이미 복구된다고 주장하지 않음 |
| 공개 오류 | LC ENTITLEMENT_REVOKED category/public mapping 없음 | client/saga/terminal replay/reconciliation에 동일409 업무오류 추가. 기존 공개 성공 DTO 유지 |
| 보상 | RESERVED는 이후 confirm 가능한 상태 | status RESERVED만 보고 정리 금지. rootdeny+durable 거절/terminal 예약 등 확정증거 확인 후만 보상 |
| 역방향 인증 | LC에는 Identity merge/withdrawal 전용 workload chain이 있으나 새Billing revoke route/trust/connector 없음 | 기존 user/Identity trust 재사용으로 자동 허용하지 않음. Billing 전용 deployed-key RS256/목적audience·flags·port 검증 별도 동결 |
| 예외 Session | R2 higher version에서 기존예외→null을 허용하면 confirm-first 완료정책과 충돌 | 최초 confirmedSessionId는 null 포함 고정. terminal/탈퇴/삭제에 의한 종료는 lifecycle 증거로 처리 |
| 보존 | 제출마감 없음+inbox120일을 혼동하면 protected Session 중단/deny소실 | inbox와 최소 deny/highwater/예외 보존 분리. 신규120일 eventage 제한을 기본안에서 철회 |
| 사용자 이전/삭제 | current user/Session 부재만으로 원구매 귀속 확인 불가 | immutable source/owner fence 확인. 학습/계정 upsert·paid 이전·deleted Session 부활 금지 |
| 다음 시험 행동 | LC legacy ExamSessionManager에는 신규시험 시 in-progress abandon 경로가 있음 | 이는 Billing paid saga의 안전종료 증명 아님. 마감 없이 유효한 기존Session을 어떻게 포기/정리하고 다른권리 INITIAL로 넘어갈지 기존 saga/삭제/guard 경로 공동 확인 |

## 단순화 가능한 부분

- 채점 승인 API/새 제출마감 API/별도 증거 이벤트 없음.
- source는 reserve/status에 추가하고 operation/Session에 저장. client가 source를 선택하지 않음.
- status에 새 confirmedAt 필드가 반드시 필요한 것은 아님. 새 마감이 없으므로 exact CONFIRMED/귀속 증거로 복구 가능한지 먼저 검증한다. Billing 내부 확정 시각/순서 증거는 유지한다.
- 확인한 기존 v1 완료 evidence/failureCode를 Billing 내부에 저장한다.

## 공동 동결 전 실제 남은 것

1. source field/legacy manifest 및 영속 confirm 재현의 정확한 snapshot/digest/인덱스.
2. public409 업무오류와 확정되지 않은 confirm의 대기·보상 fixture.
3. 역방향 issuer/sub/audience/키·flags·connector/401403404 우선순위와 HTTP/token fixture.
4. eventId/version 충돌 우선·최초 예외 고정·lifecycle 최소 보존/삭제후 replay 처리 manifest.
5. 장기 OPEN의 사용자 다음시험 행동/guard 종료 연동. 새시간제한을 만들지 않고 기존 종료/포기 흐름을 활용할지 LC 확인.

근거: Billing ReservationLifecycleService.status/confirmOnce/requireReserve, AttemptSession/Reservation, LC ExamCreationOperation/JwtBillingReservationClient/BillingClientException/BillingExamCreationSaga.publicFailure·terminalFailure/BillingReservationReconciliationService, SecurityConfig의 withdrawal chain, ExamSessionManager의 legacy abandon 경로. 정적 검토이며 runtime/E2E 결과 아님.

[보완 R2](../contracts/LC-BILLING-PAID-EXAM-HANDOFF-R2.md)에 위 명백한 충돌을 수정했다. 새wire/운영보존기간/역방향인증은 후보 상태 유지. Gradle 미실행(문서만 변경).
