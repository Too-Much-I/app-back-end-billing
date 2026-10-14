# TMI-220 — 단일 보호 Session과 이전 접수 작업의 계약 확인

- 작성일: 2026-10-14 / Billing 브랜치: `codex/TMI-220-refund-cleanup-delivery`
- 대상: Billing PLAN-012 / LC 환불 수신·채점 작업. 사용자 보완 방향 승인·문서 반영 완료. 외부 발송/LC 변경·공동 E2E·배포 완료가 아니다.
- 근거: [내부 R1 §3.2](../plans/PLAN-012-IMPLEMENTATION-SPEC-R1.md), [공동 R2 §4~5](LC-BILLING-PAID-EXAM-CONTRACT-R2.md), [기존 LC 인계](PLAN-012-LC-REFUND-SESSION-HANDOFF-2026-10-14.md).

> 후속 구현: 사용자 「보완해줘」에 따라 [Billing 과거 Session 이벤트](../runbooks/TMI-220-historical-session-events.md)를 보완했다. 아래 조사 당시 target conflict는 해당 경로에서 변경됐으며 과거 GRADING은 STALE, 검증된 terminal은 S1에만 반영한다. 환불 정리/publisher·LC 접수 작업 보완·공동 E2E는 여전히 미완료다. 이전 조사·미실행 기록을 공동 성공 결과로 바꾸지 않는다.

## 1. 5줄 결론

1. TMI-219는 commit `09dbb98`, PR25, develop merge `e8f55ee`로 병합했다. 새 TMI-220 브랜치를 생성했다.
2. 같은 유료 group의 REPLACEMENT confirm 뒤 기존/새 Session이 모두 ACTIVE·confirmedAt 보유·terminalAt 없음인 사례를 실제 Mongo 테스트로 재현했다.
3. 사용자 승인: 환불 전 confirm되고 재시작 전에 영속 접수된 이전 Session의 작업만 마무리한다. 단일 confirmedSessionId와 신규 사용 차단은 유지한다.
4. LC 작업 트리 정적 확인상 비보호 Session을 accessRevoked=true로 만들고, 접수 증거가 있어도 채점/요약의 processable 검사에서 거절한다.
5. LC의 기접수 작업 처리와 Billing의 과거 Session 이벤트 처리를 함께 보완한다. 이번 요청은 문서 보완이며 환불 worker/publisher 구현·공동 검증은 후속이다.

## 2. 반드시 읽을 사례

1. 유료 구매 P로 group G / Session S1을 confirm한다.
2. S1에 일부 답안을 접수한 뒤 의도적으로 재시작하여 같은 G에 S2를 REPLACEMENT confirm한다.
3. 기존 정책은 S1의 신규 제출을 막되, 이미 durable 접수한 작업의 채점/결과/시스템 재시도를 기존 lifecycle 안에서 허용한다.
4. 이후 P가 환불된다. 단일 confirmedSessionId에 S2를 지정하면 현재 LC 수신기는 S1에 accessRevoked=true를 설정한다.
5. 현재 LC의 접수작업 정책은 accessRevoked를 먼저 거절하므로, S1의 접수 증거만으로 채점/요약이 허용되지는 않는다.

Billing 재현 테스트는 1~2의 저장 상태를 검증한다. 3의 실제 답안 접수와 4~5의 LC HTTP/Job 실행을 공동 E2E로 검증한 것은 아니다. 4~5는 조회한 LC 코드에 근거한 분석이다. 실제 운영 사고가 발생했다는 보고가 아니다.

## 3. 승인된 보완 기준과 구현 확인 사항

**사용자 승인:** 공동 wire의 단일 confirmedSessionId는 유지하고, 이전 Session의 신규 제출 차단과 기존 durable 접수 작업의 마무리를 구분한다. 보호 ID 배열·새 event version·새 승인 API는 추가하지 않는다. 이 사례에서는 S1이 과거 Session, S2가 환불 전 confirm된 보호 대상이다. 다른 문서의 S1/S2는 예시 이름일 뿐 권한 자체가 아니다.

양측 구현 기준:

- LC: 환불 전 confirm되고 재시작 전에 영속 접수된 S1의 exact 작업과 그 작업의 기존 시스템 재시도만 완료 허용한다. 새 답변 제출·새 녹음 재시도·새 Session 생성은 허용하지 않는다. 클라이언트 주장이나 completionAllowed 하나만으로 접수를 추정하지 않는다.
- 일부 문항 접수는 해당 문항 처리의 근거일 뿐 전체 완료/새 Summary 생성 권한이 아니다. Summary는 기존 별도 영속 접수·완료 요건을 충족해야 한다.
- 삭제·탈퇴·확정 종료와 원 소유권 검증은 계속 우선한다. 앱 이탈·통신 실패만으로 확정 종료를 추정하지 않으며 모든 accessRevoked를 무시하는 우회는 금지한다.
- Billing: source·confirm·Session 귀속·기존 완료 허용 근거와 lifecycle을 검증한 과거 S1의 늦은 상태 이벤트를 S1 기록 및 해당 이용 증거에만 반영한다. LC가 작업별 영속 접수를 판정하며 Billing에 답안/Job 원문을 복제하지 않는다.
- S1의 늦은 이벤트는 group.activeSessionId·S2 상태·새 operation의 guard를 변경하거나 해제하지 않는다. 기존 event 멱등성·revision/상태 전진 규칙을 유지하고 종료된 S1을 부활시키지 않는다.

추가 제품 선택은 없다. 실제 영속 접수 근거와 환불/재시작의 순서 판정, 과거 Session 이벤트의 허용 전이·원장 반영은 양측 구현 시 대조한다. 이 문서 승인을 코드 구현 완료·공동 E2E 통과·타 저장소 변경 승인으로 기록하지 않는다.

### 3.1 LC 회신 반영 — 2026-10-14

근거: LC `docs/codex/TMI_220_LC_ACCEPTED_WORK_REVIEW.md`(develop e894408 및 feat/billing-paid-exam-p6 작업 트리 기준 정적 검토). 사용자 전달 회신을 접수했으며 상대 코드 구현·실행 결과로 취급하지 않는다.

- retry0 receipt의 acceptedAt, 불변 필수 문항 집합/전체접수 marker, 접수차단 operation·confirm snapshot을 재사용한다. Job.pendingAt은 재시도에서 바뀌므로 최초 접수 근거로 사용하지 않는다. Job 존재/제출표시/파일·결과 존재만으로 권한을 부여하지 않는다.
- receipt는 전체접수 완료 후 3일 TTL이라는 LC 보고를 반영한다. TTL 뒤 전체접수 marker/필수집합을 쓸 때도 기존 요건을 검증하며 부분접수·다른 retryCount에 일반화하지 않는다. 기존 retryCount>0 작업도 exact 과거 접수 증거가 필요하며 새 녹음 retry 생성 권한으로 확대하지 않는다.
- 재시작 경계는 원 Session 접수차단 Transaction/operation, confirm·환불 순서는 권위 있는 저장 사실과 fence를 사용한다. 서로 다른 서버의 시각 숫자 비교만으로 commit 선후를 단정하지 않는다. 동시각·응답유실·마무리 지연을 공동 테스트 입력으로 포함한다.
- LC는 dispatch·Callback 저장·누락 작업 복구·기존 시스템 재시도·Summary 및 원 Session terminal/outbox까지 동일한 좁은 검증을 적용한다. 전체접수 뒤 파생 Summary는 필수집합과 기존 채점/요약 요건이 모두 충족되어야 하며 부분접수만으로 허용하지 않는다.
- 삭제·탈퇴·owner/source/확정 종료를 처리 직전에 재검증한다. 확정 종료 후 새 mutation 금지와 이미 저장된 성공 결과의 멱등 no-op/조회는 구분한다. 증거 불명은 거절/보류하며 소급 증거·owner alias를 만들지 않는다.
- Billing은 과거 Session 이벤트의 허용 전이·이용 원장 범위 및 현재 group/Session/guard 불변을 담당한다. 양측 구현과 실제 HTTP 경합 검증은 미완료다.

### 필수 회귀 기대값

| ID | 사례 | 기대값 |
| --- | --- | --- |
| C01 | S1 confirm→작업 영속 접수→S2 재시작→환불 | S1 접수 작업/기존 시스템 재시도만 완료, 보호 S2는 기존 정책 유지 |
| C02 | S1 신규 제출·새 녹음 재시도·환불권으로 새 Session | 거절, 기존 작업으로 위장한 새 입력 허용0 |
| C03 | S1 일부 문항만 접수 | 해당 문항만 처리, 전체 완료/Summary 자동 허용0 |
| C04 | 삭제·탈퇴·확정 종료 후 지연 작업/이벤트 | lifecycle 우선, 부활0; 일시 통신 실패와 구분 |
| C05 | S1 늦은 상태·중복·역순과 S2/새 생성 경합 | S1에만 정합 반영, S2/group 포인터/새 guard 변경0 |
| C06 | confirm·접수·source/owner 근거 부재 또는 불일치 | 권한 추정0, 기존 오류/보류 규칙 적용 |

위 항목은 실행 예정 기준이며 현재 통과 결과가 아니다. LC 단위/Job·Billing Mongo·양측 HTTP 경합 결과를 분리해 기록한다.

인계서 ID 정정: 기존 H11~H13(다른 권리/TTL·전파/인증)은 유지한다. LC 회신에 적힌 “새 H11~H13”만 H14~H16으로 매핑한다. C01·C02→H14, C03→H15, C04·C05→H16, C06은 공통 증거 부재/불일치·TTL·pendingAt 변경·동시각 음성 경계다. 이는 문서 번호 정리이며 공유 fixture ID/bytes 변경이 아니다.

## 4. 현재 구현을 멈춘 범위와 위험

- 미정인 보호 대상을 하나로 임의 선택하거나 복수 Session을 자동 terminal 처리하지 않는다.
- 임의 confirmedSessionId 배열/새 body/별도 revoke를 만들지 않는다. 최초 group당 단일 불변 outbox 규칙을 유지한다.
- 계획의 증거불명 group 보류는 실제 의미 충돌을 해결한 것으로 간주할 수 없다. 같은 정상 재응시마다 보류되는 운영을 기본 정상 처리로 확정하지 않는다.
- 사용자 승인 범위의 Billing 코드만 작업하며 LC 저장소는 읽기 전용이다. 상대 코드 변경·메시지 발송·Jira 쓰기·DB 설치·배포/활성화는 하지 않았다.
- TMI-219 금융 root는 그대로 유지한다. 기능은 OFF이고 processing ON 기동 거절도 유지한다.

## 5. 조사·검증 결과

- Billing 신규 `RefundProtectionContractIntegrationTest.sameGroupReplacementRetainsTwoConfirmedNonTerminalSessions`: 실제 replica-set Mongo에서 정상 INITIAL→confirm→동일group REPLACEMENT→confirm, 두 Session ACTIVE·동일 purchase·confirmedAt 있음·terminalAt 없음, S1 completionAllowed=true, G.activeSessionId=S2를 검증. 선별 실행 1개 통과.
- 이 테스트는 현행 동작을 기록하는 characterization이다. 복수 Session 보호를 새 제품 정책으로 승인하거나 특정 LC 처리 결과를 가짜 성공으로 고정하지 않는다.
- 기존 작업 폴더 TMI-217 변경은 보존했고 새 브랜치는 별도 worktree에 있다. 실제 Store/LC HTTP 검증은 미수행.
- 전체 clean test 결과는 CURRENT_STATE/WORKLOG에 최종 기록한다.

## 6. 상세 정적 근거

Billing:

- `PaidReservationService.finishConfirm`은 REPLACEMENT에서도 `allowOldCompletion`을 호출하고 새 Session을 activate한다.
- `allowOldCompletion`은 이전 ACTIVE Session에 completionAllowed=true를 저장하지만 terminal로 바꾸지 않는다.
- `PaidAttemptEventService.process`는 ACTIVE 과거 Session과 group.activeSessionId가 다르면 EVENT_TARGET_CONFLICT로 거절한다. 승인된 기접수 완료와 이 동작의 정합성도 확인 대상이다.
- [재현 테스트](../../src/test/java/web/tosunsaeng/billing/domain/reservation/paid/RefundProtectionContractIntegrationTest.java).

LC 읽기 대상: `/Users/msde76/app-back-end-learning-core`, 조회 당시 HEAD `f59cf28`와 현재 작업 트리. 배포 코드/전체 변경을 독립 검증한 것은 아니다.

- `domain/exams/access/AccessRevocationStore.accept`: confirmedSessionId 외 동일group Session에 accessRevoked=true.
- `domain/exams/application/ExamAcceptedWorkPolicy.processable`: accessRevoked이면 false. questionAllowed/summaryAllowed가 이 조건을 먼저 적용.
- `domain/exams/application/ExamGradingService`: canProcessSession/acceptedQuestionWork/acceptedSummaryWork가 위 정책에 의존.

다음 순서는 보완 문서 LC 전달·구현 근거 대조 → 양측 담당 범위 구현 → 실제 재현/회귀다. 이번 문서 보완을 TMI-220 구현 완료 또는 공동 검증 통과로 표시하지 않는다.
