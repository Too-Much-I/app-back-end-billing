# TMI-219 — 환불 원장·구매 차단·기간 재배치

- 날짜: 2026-10-14 / 브랜치: `codex/TMI-219-refund-ledger`
- 기준: [내부 R1](../plans/PLAN-012-IMPLEMENTATION-SPEC-R1.md), [PLAN-012](../plans/PLAN-012-refund-ledger-and-access-revocation.md), [Jira](https://to-teacher.atlassian.net/browse/TMI-219).

## 1. 5줄 결론

1. 기존 inbox→PurchaseCore→공통 worker 경계를 유지하면서 검증된 환불 evidence 처리 코어를 추가했다.
2. 원 구매 금액과 실제 반환 누적액을 분리한다. UNKNOWN은 null이며 신규 case에 책임을 남긴다.
3. purchase root 차단·고정 effect/appliedAt·해당 entitlement 종료·미시작 timeline 재배치·ledger·refundWork 등록은 같은 Mongo Transaction이다.
4. 기존 confirm의 purchase/version 쓰기와 경합한다. confirm 선행 replay·복원 무료권은 보존하고 환불 선행 confirm은 기존 ENTITLEMENT_REVOKED로 거절한다.
5. **운영 활성화 불가**다. TMI-220 정리/전달과 실제 provider 매핑 검증 전 processing ON은 기동 오류다. 앱 API·LC wire는 변경하지 않았다.

## 2. 사용자가 반드시 읽어야 하는 내용

수동 문의 심사나 Store 환불 실행을 자동화한 구현이 아니다. 검증 adapter가 만든 내부 `RefundEvidence`만 처리한다. 아직 실제 RC/Google 반환액 필드를 추측해서 연결하지 않았다. 기존 RC `REFUND_REVERSED`는 검토 경로이며 자동 복원하지 않는다. 기존 evidence 없는 reversal도 기존 fence/검토 경로를 유지한다.

두 flag는 `billing.payment.refund.processing-enabled` / `publisher-enabled`이고 환경변수는 `BILLING_PAYMENT_REFUND_PROCESSING_ENABLED` / `BILLING_PAYMENT_REFUND_PUBLISHER_ENABLED`다. 기본 false이며 신규 secret은 없다.

| 설정 | 이번 구현 |
| --- | --- |
| OFF / OFF | 정상 비활성 기동. 기존 효과/진행/접수는 삭제하지 않음 |
| OFF / ON | processing 의존성 오류 |
| ON / OFF, ON / ON | 미완성 pipeline 기동 거절. 향후 TMI-220/실제 adapter 준비 후 완화 |

테스트는 Spring 기동이 아니라 내부 코어를 명시 구성하여 수행한다. 기능 준비 완료를 숨기기 위한 세 번째 스위치는 없다.

## 3. 동작과 저장 변경

- `PurchaseObservation`에 optional `refundEvidence`를 추가했다. 기존 생성자/legacy body·digest bytes는 보존하며 evidence 포함 입력만 digestVersion=2다.
- 같은 providerEventKey는 저장된 digestVersion으로 비교한다. legacy event에 새 evidence를 붙여 재전송해도 원 row를 덮어쓰지 않는다. 추가 확인은 **새 observation key**로 접수해야 한다.
- `RefundEvidence`는 source·evidenceKey·nullable providerConfirmedAt·UNKNOWN/CUMULATIVE 금액을 표현한다. Decimal128/ISO 통화·millisecond 시각을 검사한다. 앱 요청 DTO/수동 금액 입력이 아니다.
- 최초 환불은 `purchases.refund`와 `refundWork`를 만들고 `ACCESS_REVOKED`를 한 번 기록한다. RESERVED/Session 정리는 아직 실행하지 않는다.
- 미래 slot만 sequence 순으로 max(appliedAt, 앞 유효 slot 종료)에서 기존 시작을 늦추지 않고 이동한다. 이미 시작한 다른 slot과 original schedule은 보존한다. timeline CAS로 동시 append와 수렴한다.
- 반환 누적액 증가만 차이 원장을 append한다. 동일액은 no-op, 감소/원 구매액 초과/통화 충돌/비교 가능한 원 구매 총액 부재는 case다. unknown을 구매금액으로 대체하지 않는다.
- RC API와 webhook은 같은 RC authority다. Google과 RC는 합산하지 않으며 authority가 달라지면 검토한다. Google은 이미 원 구매 근거가 있는 PLAY_STORE 구매의 보완으로만 허용한다.
- 추가 반환은 기존 effectId/appliedAt/refundWork를 유지하고 reflow/ACCESS_REVOKED를 반복하지 않는다. 탈퇴 계정을 ACTIVE로 되돌리지 않는다.
- 금액 불명 case는 자동 해결하지 않는다. 후속 금액이 확인돼도 과거 case/pending 책임은 운영 검토가 해소해야 한다(TMI-221). case가 있는 동안 완료로 오표시하지 않는다.

## 4. 명시 schema 이관

`RefundCoreSchema.upgradeUnderMaintenance(db, true)`는 **현행 r6→r7**의 명시 작업이다. 모든 writer 격리 + recovery control 전부 ALL_BLOCKED가 필요하다. 실제 환경에 실행하지 않았다.

1. 원복 가능한 사전 백업·reader 배포와 유지보수 승인 확보.
2. r6 schema 또는 허용된 r6/r7 중간 상태를 모든 collection에서 먼저 검증.
3. purchases/관측 inbox/case/ledger validator 확장 및 아래 index 추가. 기존 index drop 없음.
4. r7 exact 검증. 중단 시 같은 작업 재실행 가능. 임의 drift는 수정하지 않고 거절.
5. OFF writer 배포. 구 binary는 새 exact schema를 지원하지 않으므로 무조건 rollback 금지.

| collection / index | ordered keys | 옵션 |
| --- | --- | --- |
| purchases / ix_purchase_refund_due | environment, refundWork.state, refundWork.nextAttemptAt, _id | 전부 asc, non-unique, TTL/partial/sparse 없음 |
| payment_purchase_cases / ix_payment_case_refund_review | environment, reviewState, reason, createdAt, _id | 동일 |

기존 `ux_payment_ledger_effect`에 refund-access / refund-amount / refund-reflow key를 사용한다. reservation/group/outbox schema는 TMI-220 범위이며 이번에는 만들지 않는다. legacy 문서를 강제 backfill하지 않는다. 새 관측과 refund의 금액 필드는 schema에서도 all-null 또는 완전한 금액 묶음을 요구한다.

기존 반복 schema 검사/RC 응답 후 permit 검사는 유지했다. 기존 reference retention은 미지원 ledger type이면 보류하므로 새 환불 원장이 있는 자료를 자동 정리하지 않는다. 금융 삭제·case 해소·미전송 증거 보존의 최종 운영은 별도다.

## 5. 검증 범위

- 신규 단위: UNKNOWN/0 구분, strict Decimal128·통화/시각/long, legacy bytes·digest, Store/source 제한, 미완성 flag 거절.
- replica-set Mongo: unknown→확인→추가→중복, RC/Google 충돌, 상한/통화/원금 불명, 중간/지연 reflow, 탈퇴/환불 선행, unknown commit·동시 refund/append, root/entitlement/timeline/ledger/case 쓰기 실패 전체 rollback, 명시 이관 중단/재개/drift, OFF 기존 효과 보존.
- 기존 paid runtime과 실제 Mongo: 동일 millisecond에서 confirm/환불 양순서, 동시 경합, 같은 key replay, 기존 Session/무료 복원권 불변.
- `REFUND_REVERSED` 및 legacy 흐름은 기존 provider/코어 회귀도 함께 실행한다. 최종 전체 테스트 수는 CURRENT_STATE/WORKLOG에 기록한다.

## 6. 남은 작업·위험

- TMI-220: refundWork RESERVED→GROUPS 순차 처리, 마지막 confirm 증거·증거불명 보류, 불변 outbox/역방향 JWT 전달, 보호 Session과 guard/삭제보존 경합.
- TMI-221: case 조회/해소·재조회·pending 책임 정리, Slack, 실제 Store/LC 통합. 과거 금액 불명 case를 단순히 추가 관측만으로 닫지 않는다.
- RC/Google 실제 필드·누적 금액 의미·authority·증거 key 생성은 검증 adapter 후속이다. synthetic fixture 통과가 실Store 지원 검증이 아니다.
- timeline은 원자적 전체 재배치를 유지한다. 매우 큰 timeline의 처리 한계·부하 측정은 판매 전 gate이며 임의 구매 횟수 제한은 추가하지 않았다.
- 실제 schema 설치·실제 환불/권한/키·배포·판매 활성화·LC/Identity 변경 없음. 새 앱용 환불 API 없음.
- 기존 작업 폴더 `/Users/msde76/billing`의 TMI-217 변경은 보존했다. 이 구현은 앱에 연결된 별도 worktree에 있다.
