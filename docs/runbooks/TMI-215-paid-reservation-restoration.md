# TMI-215 — 유료 우선 예약·확정·최초 체험 복원

## 1. 5줄 결론

1. `PaidReservationService`가 같은 ClientSession에서 유료 우선 선택, 무료권 hold/consume/release, confirm·복원·원 그룹 재응시 차단·receipt/원장을 처리한다.
2. reserve 실패/취소/만료는 이전 시험을 종료하지 않는다. confirm 성공 후에도 원 Session의 기접수 완료를 허용하며 새 guard를 해제하지 않는다.
3. 최초 trial consumption의 원장 근거를 확인하고 원 소비당 복원 1회 unique를 적용한다. phone target paid는 INITIAL/새 group/원 mock/echo/transition 없음이다.
4. 테스트는 로컬 replica set의 실제 서비스/Transaction 검증이다. LC HTTP·전체 상태28·기존 상태 이벤트의 MEMBER 귀속·통합 reader/expiry worker 배선은 TMI-216이다.
5. 기본 OFF와 paid-admission ON 기동 거절을 유지한다. 운영 schema 설치·배포·활성화·LC/Identity 수정은 하지 않았다.

## 2. 반드시 읽어야 하는 내용

승인된 R2/simplified exact 및 fixtures bytes는 변경하지 않았다. 이 구현은 TMI-214의 MEMBER recovery에 admission callback으로 연결되는 비활성 내부 서비스다. HTTP controller가 새 resolver를 사용한다고 보고하지 않는다. 기존 무료 resolver는 restartContext를 무시하지 않고 503으로 거절한다. 계정 없는 Guest의 legacy 무료 흐름은 기존 경로에 남아 있다.

- ACTIVE paid 선택 시 무료 grant를 발급/hold하지 않는다. 정상 기간 만료는 이미 잡은 5분 Reservation의 confirm을 막지 않는다.
- reserve source/transition/응답은 최초 snapshot으로 재현한다. current Session 변경보다 기존 operation replay가 먼저이며, 현재 owner와 lifecycle deny는 replay보다 먼저다.
- `RESTART_INCOMPLETE`의 같은 source는 REPLACEMENT, source 변경/NEW_AFTER_SUBMISSION/NEW_AFTER_COMPLETION은 INITIAL이다. context 없는 기존 group를 임의 INITIAL로 바꾸지 않는다.
- phone은 현재 link/transition/epoch를 확인한다. target paid 없이 유효한 trial은 REPLACEMENT, target paid는 새 INITIAL이다. 기존 계정 paid/복원권을 이전하지 않는다.
- confirm은 이전 group 완료/삭제·소비 원장을 다시 확인한다. 원 완료 선행이면 복원하지 않고 confirm 선행이면 이후 완료로 지급을 취소하지 않는다.
- refund 선행은 CANCELED + `confirmRejection.code=ENTITLEMENT_REVOKED` 및 terminalAt를 영속한 뒤 409를 반환한다. confirm 성공 replay는 뒤늦은 refund로 바꾸지 않는다.
- 최초 trial 복원은 신규 grant이며 원 소비를 다시 열지 않는다. 이미 지급된 소비의 수혜자를 덮어쓰거나 복원권을 다시 복원하지 않는다.
- 지급된 무기한 MEMBER 무료권은 grantOrder로 선택하며, 기존 trial 후보는 현재 VERIFIED/candidate→Claim·retention을 확인한 뒤 지급 시각과 비교한다. 복원 grant에 유효기간을 추가하지 않았다.

## 3. 스키마와 운영 gate

| 스키마 | 명시 maintenance | 유지 조건 |
| --- | --- | --- |
| paid-exam-admission-r3 | 기존 r2에서 `PaidExamSchema.upgradeAdmissionUnderMaintenance` | r1/r2 원본 유지, restart request snapshot만 r3. 미완료 TTL 없음 |
| payment-purchase-core-r5 | 기존 r4에서 `PurchaseCoreSchema.upgradeExamUsageUnderMaintenance` | ALL_BLOCKED/writer 격리, 기존 provider ledger와 EXAM_OPERATION strict union, 기존 index 유지 |

운영에서 메서드를 실행하지 않았다. r3/r5 지원 writer/reader를 함께 준비해야 하며 구 writer로 무조건 rollback하지 않는다. user source/legacy 범위·삭제/탈퇴 owner·purge(TMI-217), PLAN-012 실제 환불 writer·revoke, PLAN-013 운영 gate는 별도다.

## 4. 검증 범위·남은 작업

`PaidReservationIntegrationTest`는 disposable Mongo7.0.14 replica set에서 실제 unique index·다중 document Transaction을 사용한다. paid 선택, source replay, FIFO/release, phone INITIAL, 복원1회, 원 완료 선행/후행, owner epoch, 동시 confirm, 환불/완료 경합, unknown commit, receipt-write 실패 rollback 및 schema 이관을 검증한다. 환불 경합의 상대 writer는 테스트의 purchase/entitlement Transaction이며 PLAN-012 provider/refund/outbox 전체 구현 통과가 아니다.

최종 clean test 1,546개(신규23: 실Mongo22+strict wire1 포함), 실패/오류/skip0. 전체 수에 신규23개가 포함되며 합산하지 않는다. frozen simplified/R2 검사기는 오프라인 검사이며 상태28 전체 실행 완료로 합산하지 않는다.

다음 TMI-216에서 HTTP 선택/응답 converter, MEMBER 상태 이벤트 귀속·current guard 완료, 통합 entitlement reader, expiry dispatch, LC finalize와 전체 상태 대응표를 연결한다. C/배포 작업이나 별도 삭제 route를 이 변경에 포함하지 않는다.

## 5. 변경 위치

- `domain/reservation/paid`: resolver/confirm/복원·동일 Tx trial funding·restart/transition·receipt/schema.
- Reservation/AttemptGroup/AttemptSession 및 repository: source/원 대상/차단과 기접수 완료 표현.
- reserve strict decoder/hash 및 status DTO: optional restart·transition·confirmRejection 구조. 기존 command header/status body 유지.
- PurchaseCoreSchema: 최소 EXAM_OPERATION 원장 type과 명시 r5 이관.

별도 작업의 RevenueCat/PLAN-012 문서 변경은 보존했다. 이 작업은 해당 문서를 수정하거나 승인하지 않는다.
