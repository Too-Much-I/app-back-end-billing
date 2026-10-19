# PLAN-011 source 일회성 이관 검토

> 후속 실데이터 확인: [production 점검](PLAN-011-SOURCE-PRODUCTION-INVENTORY-2026-10-13.md)에서 LC 1,493건 모두 Billing 연결 없음, active 146건을 확인했다. 단순 EXAM_PASS 일괄 backfill 대상이 아니며 기존 학습 기록과 신규 Billing 시험의 경계 검토가 필요하다. Billing production DB는 사용자가 미구축으로 확인했으므로 기존 Billing 데이터 이관은 불필요하다. 아래 미조회 표현은 최초 코드 검토 시점 기록이다.

- 날짜: 2026-10-13
- 요청: 출시 전 데이터 점검·일회성 source 이관 후 필수값 처리 가능성 확인
- 결론: 코드 구조상 조건부 가능. 운영 데이터 건수·배포 이력·실제 이관 가능 판정은 미확인. 구현/DB 이관/배포 승인이나 실행 완료가 아니다.

## 확인한 코드

- Billing `ReserveService.reserveOnce`의 현행 예약 경로는 TrialEligibility/TrialClaim에서 grant를 선택하고 INITIAL allocation에 grantId를 기록한다. grant sourceType은 TRIAL_CLAIM이다. paid 구매 저장 코드가 존재하는 것과 paid Reservation이 활성 사용된 것은 구분한다.
- `ReservationLifecycleService.confirmOnce`는 INITIAL consumption ledger를 만들고 AttemptGroup에 trialClaimId와 consumptionLedgerEventId를 저장한다. REPLACEMENT는 기존 group을 사용하므로 개별 replacement에 allocation이 없어도 원 consumption으로 연결 가능하다.
- Reservation/AttemptSession/IdempotencyCommand.ResponseSnapshot에는 아직 entitlementSource가 없다. LC ExamCreationOperation/ExamSession에도 없다. field/schema/response writer 추가는 후속 구현이다.
- LC ExamCreationOperation의 PREPARED 단계에는 아직 reserve 결과가 없을 수 있다. LC operation은 reservationId/group/session으로, Session은 billingReservationId/attemptGroupId로 Billing 사실과 대조할 수 있다.
- LC ExamSessionManager.startNewAttempt에는 Billing 연결 필드 없이 Session을 만드는 경로가 존재한다. 이 경로로 생성된 실데이터가 존재하는지는 확인하지 않았다.
- Billing status는 reserve command가 있어야 동작하며 기본 terminal command retention은 7일이다. source 이관만으로 command 만료 후 status/confirm 복구 문제가 해결되지 않는다.

확인 경로: `src/main/java/web/tosunsaeng/billing/domain/reservation/application/ReserveService.java`, `ReservationLifecycleService.java`, reservation/attempt 엔티티와 설정. LC는 `/Users/msde76/app-back-end-learning-core/src/main/java/web/tosunsaeng/domain/exams/` 아래 billing client·ExamCreationOperation·ExamSession·ExamSessionManager를 읽기만 했다.

## 일회성 이관으로 줄일 수 있는 범위

Billing 근거가 확인된 기존 무료 예약/시험은 EXAM_PASS로 채운다. 최초 trial이라는 세부 출처는 Billing grant/Claim/ledger에 유지한다. 신규 SUBSCRIPTION은 새 resolver가 검증된 구매를 선택해 기록한다. 현재 구매 유무나 배포 날짜만으로 과거 시험 출처를 판정하지 않는다.

대상은 활성 Session뿐 아니라 응답 재현/복구에 사용되는 보존 중 operation·command snapshot·Reservation·group·Session이다. terminal 이력은 동일 필수 reader를 통과하는지에 따라 함께 이관하거나 명시적인 history 경계로 분리한다. source만 채우고 원래 사용자/owner epoch/상태/소비/성공 증거를 바꾸지 않는다.

| 대상 | 처리 후보 |
| --- | --- |
| INITIAL의 allocation→grant/Claim 및 귀속이 일치 | EXAM_PASS로 이관 |
| REPLACEMENT의 group→원 consumption ledger→grant/Claim이 일치 | EXAM_PASS로 이관 |
| 아직 reserve를 보내지 않은 PREPARED | 출처를 꾸며 넣지 않음. 실제 reserve 성공 때 기록 |
| reserve 발신/응답 유실 가능성이 있는 PREPARED | Billing 영속 근거로 복구한 뒤 판정. 미발신으로 추정하지 않음 |
| Billing 연결 없는 LC legacy Session 또는 근거 소실/충돌 | 자동 무료 분류 제외. 실제 건수 확인 후 별도 처리; 삭제/강제 종료를 이번 검토로 승인하지 않음 |

## 권장 전환 절차

1. 환경별 읽기 전용 inventory로 위 분류의 건수를 확인한다. 실제 배포 revision과 paid 시험 사용 이력도 대조한다. 개인정보/원문 token은 작업 기록에 남기지 않는다.
2. 새 시험 시작과 관련 writer/복구 worker를 짧은 점검 구간에 멈추거나 drain하여 고정된 대상 집합을 만든다. 제출·상태 이벤트가 대상 row를 갱신하는 경로도 조정한다. 앱 강제 업데이트만으로 서버 writer를 격리할 수는 없다.
3. strict LC reader와 source를 보존하는 새 writer, Billing response/schema를 호환 순서로 준비한다. 누락을 무료로 간주하는 영구 fallback을 만들지 않는다.
4. 승인된 명시적 migration으로 근거가 확인된 row만 채운다. 사전 dry-run/충돌 확인, missing-only 조건부 갱신·version 검사·재실행 멱등성·최소 작업 manifest를 둔다. 이미 다른 source인 값을 덮어쓰지 않는다. 실제 schema/validator에 맞춘 도구는 아직 없다.
5. 새 writer 기준으로 재점검해 source가 필요한 경로의 누락/충돌 0과 양측 연결 일치를 확인한 뒤 필수값 검증 및 시험 시작을 활성화한다. source를 쓰지 않는 구버전 writer를 재투입하지 않는다.

필수 시점은 **reserve 성공 응답 및 이를 저장한 이후**다. PREPARED 등 출처 미결정 단계까지 모든 document에 무조건 required를 걸지 않는다. 전환 이후 필수 경로에서 source 누락/미지원 값/충돌이 나오면 계약 오류·복구 대상으로 처리하고 EXAM_PASS 기본값을 넣지 않는다.

## 남는 확인

실제 대상 0건이면 데이터 backfill은 생략하고 새 writer/필수값 검증만 적용 가능하다. 증빙 가능한 trial 데이터만 있다면 일회성 이관으로 상시 legacy 추론을 제거할 수 있다. Billing 미연결 legacy/충돌이 있다면 그 범위를 해결하기 전 전체 이관 완료로 보고하지 않는다. 기존 기한 없는 Session을 편의를 위해 강제 만료시키지 않는다.

실제 DB에 접속하거나 조회·수정하지 않았으며, 운영/테스트 데이터가 모두 무료이거나 0건이라는 결론은 내리지 않았다. 다음 운영 확인은 환경별 분류 건수와 연결 완전성이다. command TTL 이후 영속 복구는 PLAN-011의 별도 필수 보완으로 남는다.
