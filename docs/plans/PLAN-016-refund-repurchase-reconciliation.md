# PLAN-016: 환불·재구매 연결 수정 — 최소 범위

- 작성일: 2026-10-23 / 단순화: 사용자 「그냥 간단하게 해줘」 반영.
- 상태: 사용자 구현 승인으로 새 브랜치에서 구현·전체 로컬 테스트 2,021개 통과. [구현 안내](../runbooks/PLAN-016-refund-repurchase-reconciliation.md). 별도 INITIAL 긴급 수정은 운영 배포 완료.
- 관련: [PLAN-012](PLAN-012-refund-ledger-and-access-revocation.md), [사건 기록](../runbooks/refund-repurchase-recovery-2026-10-23.md).

## 1. 해결할 문제

새 구매는 정상 등록됐지만, 이전 구매의 실제 환불이 기존 환불 처리로 연결되지 않았다. 이 때문에 새 이용권 시작이 밀리고 해결되지 않은 검토 기록이 이용을 막았다.

**기존 환불 처리에 실제 환불 증거를 연결하고, 해결된 해당 거래의 보류만 해제한다.** 신규 API·컬렉션·복구 큐·일반 자동 복구 시스템은 추가하지 않는다.

## 2. 구현할 세 가지

### ① 실제 환불을 기존 처리에 연결

인증된 RevenueCat 조회에서 기존 구매와 환경·거래·소유자가 일치하고 최종 환불이 확인되면 `RefundEvidence`를 구성해 기존 `PurchaseRefundCore`를 호출한다. webhook이 조회를 요구하면 기존 worker에서 정확한 거래를 조회한다. 취소 요청이나 불명확한 신호만으로 환불 처리하지 않는다.

권리 종료·후속 기간 재배치·원장·LC 전달은 기존 Transaction과 outbox를 그대로 사용한다. 이미 처리한 환불은 다시 기간을 바꾸거나 지급하지 않는다. 기존 observation bytes/hash도 덮어쓰지 않는다.

### ② 해결된 해당 거래의 기술 보류만 해제

환불 적용 완료 증거가 있는 **같은 거래의 알려진 기술 보류**만 기존 case 처리로 종결한다. case와 차단 counter는 같은 Transaction에서 한 번만 변경한다. 계정이 같다는 이유로 다른 보류까지 해제하지 않는다.

과거 건의 연결 근거가 부족하거나 기존 복구 기록과 충돌하면 운영 확인으로 남긴다. 과거 모든 오류를 자동으로 찾아 고치는 기능은 만들지 않는다.

### ③ 환불액 확인과 이용 가능 여부를 분리

환불된 구매의 권리 종료가 확인됐고 반환액만 미확정이면 금액 확인 기록은 유지하되, 별도 정상 구매의 이용을 막지 않는다. 금액은 UNKNOWN/null로 두고 추정하지 않는다.

기존 review 구조에서 금액 확인만 남았다는 구분을 최소한으로 표현한다. 새 대사 시스템은 만들지 않는다. 실제 sync·소유권·기간 중첩·LC 전달 등 다른 차단 사유는 기존 규칙을 유지한다. 공개 API 응답은 변경하지 않는다.

## 3. 이번에 하지 않는 것과 수용하는 한계

- 거래 연결이 불명확한 과거 보류의 자동 복구: 운영 확인으로 남으며 즉시 해소되지 않을 수 있다.
- 모든 과거 기록의 일괄 재처리·일괄 보류 해제: 하지 않는다. 필요한 건만 별도 운영 절차로 처리한다.
- 신규 보상·환불 실행·일반 금액 대사 기능: 추가하지 않는다.
- 무료권·phone·삭제·탈퇴 정책 변경: 하지 않는다.

구현 중 기존 저장 구조로 표현할 수 없어 schema 변경이 필요하면 최소 변경 내용을 먼저 기록한다. 자동 DDL이나 기존 증거 덮어쓰기로 우회하지 않는다.

## 4. 구현 순서와 필수 검증

1. 실제 provider 응답을 비식별 fixture로 만들어 decoder→환불 core 연결을 재현하고 수정한다.
2. 해당 거래의 기술 보류 종결과 금액 전용 보류 구분을 연결한다.
3. 아래 회귀 검증과 전체 테스트 후 테스트 서버→운영 순으로 반영한다. 과거 운영 건 재처리는 배포와 구분한다.

기존 14개 검증 항목은 아래 6개 묶음으로 정리한다. 안전성 검증을 생략한다는 뜻은 아니다.

| 검증 | 확인할 내용 | 기존 항목 |
| --- | --- | --- |
| 실제 환불 연결 | 최종 환불만 처리, 거래·환경·owner 불일치 거절 | F01/F02 |
| 환불과 재구매 순서 | 늦은 환불에도 새 권리 기간 보존, 정상 구매 이용 가능 | F03/F07 |
| 금액 미확정 | 금액 추정 없음, 후속 금액 확인으로 권리 재종료 없음, 다른 차단 유지 | F04/F08 |
| 중복·경합 | 중복/역순/응답 유실·구매/confirm 경합에도 원장·기간·counter 한 번만 반영 | F05/F09/F13 |
| 과거 복구 호환 | exact 보류만 종결, 근거 부족은 유지, 수동 복구 기간·원장·기존 hash 보존 | F06/F10/F11 |
| 기존 기능 회귀 | LC 전달 재시도·보호 Session·무료권·복원권·탈퇴·조회 동작 유지 | F12/F14 |

테스트 서버에서 실제 환불→재구매 흐름을 확인한 뒤 운영에 반영한다. 배포 상태 확인과 실제 사용자 이용 성공은 별도로 기록한다. 확정된 원장·기간을 과거 DB snapshot으로 되돌리지 않는다.

## 5. 이미 완료한 긴급 수정

완료된 과거 환불 시험의 `replacementBlocked`가 다른 정상 구매의 새 시험까지 막던 결함은 별도 수정했다. PR45/main `90b7e46`, Actions39944389961로 2026-10-23 20:30 KST 운영 배포·서버 검증 완료.

이 수정은 위 환불 입력 연결 작업 전체의 완료를 뜻하지 않는다. 기존 실패 key는 보존하며 새 시험 시작은 새 key를 사용한다. 사용자 실제 시작 성공은 별도 확인이다.

- [긴급 수정·검증·배포 기록](../runbooks/refunded-completed-group-new-purchase-fix-2026-10-23.md)

## 6. 구현 시 참고

- [RevenueCatPurchaseDecoder](../../src/main/java/web/tosunsaeng/billing/domain/payment/provider/RevenueCatPurchaseDecoder.java), [RevenueCatWebhookDecoder](../../src/main/java/web/tosunsaeng/billing/domain/payment/provider/RevenueCatWebhookDecoder.java): 입력 연결.
- [PurchaseCore](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCore.java), [PurchaseRefundCore](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseRefundCore.java): 기존 원자 처리와 종결.
- [PaidEntitlementReader](../../src/main/java/web/tosunsaeng/billing/domain/payment/query/PaidEntitlementReader.java): 금액 전용 보류와 실제 차단 구분.
- [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md), [TMI-221 운영 안내](../runbooks/TMI-221-refund-operations.md): 기존 정책·운영 절차 유지.

계획 단순화 당시에는 문서만 변경했다. 이후 사용자 승인으로 구현을 진행했으며 현재 결과는 위 구현 안내에서 확인한다. 실제 DB·배포·운영 재처리는 별도다.
