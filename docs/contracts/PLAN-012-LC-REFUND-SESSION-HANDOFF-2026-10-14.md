# Billing → Learning Core: 환불 후 exact Session 보호·재응시 차단 인계

- 날짜: 2026-10-14
- 대상: LC 유료 시험 생성·operation 복구·접근 철회 수신 담당
- 관련: [PLAN-011](../plans/PLAN-011-paid-reservation-and-usage-evidence.md), [PLAN-012](../plans/PLAN-012-refund-ledger-and-access-revocation.md), TMI-215/216/217, LC TMI-116/128/122/125
- 상태: 승인된 제품 정책과 기존 공동 기술 규격의 구현 접점을 정리한 인계서. LC 최신 코드 독립 검증·상대 수락·발송·배포 완료를 뜻하지 않는다. 신규 wire/오류/시간 제한을 추가하지 않는다.

### LC 검토 회신 접수 — 2026-10-14

사용자 전달 회신으로 정책이 기존 LC 계획과 일치함을 확인했다. 아래는 상대 보고이며 Billing의 LC 코드 독립 검증 결과가 아니다.

현재 LC는 **PLAN-011 유료 시험 연동 구현 중**이다. P2는 reserve/status 응답 읽기·검증·증거 저장 기반이며 confirmRejection 해석을 포함한다. **PLAN-012 환불 이벤트 수신·Session 보호는 후속 구현이고 이번 인계로 착수하지 않는다.** 아래 후속 단계는 계획상의 분담이며 착수/완료 보고가 아니다.

| LC 단계 | 회신 상태/범위 |
| --- | --- |
| P2 | PLAN-011 reserve/status 응답 읽기·검증·증거 저장 기반 구현 보고. confirmRejection 해석 포함, 환불 이벤트 수신·Session 보호 구현 아님 |
| P2.1 → P3 | 다음 진행 순서. P2.1 상세는 LC 기존 계획을 따르며 여기서 추정하지 않음. P3는 같은 key 복구·confirm/환불 선후에 따른 확정/정리 |
| P4 | 기존 exact Session 완료 허용·새 Session 보호 승계 금지, 구현 후속 |
| P5 | 환불 이벤트 수신·인증·중복/충돌 처리, 구현 후속 |
| P6 | 보존기간·삭제·과거 소유자 경계 검증, 후속 |

LC 저장은 **그룹별 단일 revoke 문서**로 접수 멱등성과 deny/보호 예외를 함께 처리한다. 별도 inbox collection을 추가하지 않는다. eventId/group 유일성·원자성·최초 불변 사실은 유지한다. 이 문서의 종전 inbox/deny 표현은 논리적 역할이지 두 저장소 요구가 아니다. 단일 문서의 폐기 기준은 P6에서 확인하며 옛 inbox120일 표현을 문서 전체 TTL 삭제로 전용하지 않는다. 원본 R2/fixture bytes는 그대로 보존한다.

H01~H13 공동 검증은 미완료다. 이번 LC 회신은 검토·기록만 수행한 결과이며 새 코드/배포 변경을 뜻하지 않는다. LC P2~P6과 Billing PLAN-012 P0~P4는 서로 다른 단계 번호다.

## 1. 5줄 결론

1. 환불 전에 Billing confirm이 성공한 **바로 그 Session**만 기존 lifecycle 안에서 완료를 허용한다.
2. 같은 AttemptGroup이어도 **새 Session을 만드는 재응시**는 환불된 source로 허용하지 않는다.
3. 같은 operation/key 복구는 합의된120일 멱등 보장과 기존 lifecycle 범위를 따르며 삭제·종료된 Session을 재생성하지 않는다.
4. 환불이 먼저 commit됐다면 confirm은 영속 거절하고 LC는 권위 결과 확인 뒤 대기 Session을 보상한다.
5. LC는 최초 불변 revoke·보호 Session을 저장하고 중복/전파 지연/terminal·삭제 경합을 검증한다. 유료 입장은 실제 통합 검증 전 OFF다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 사용자가 확인한 사례

시험 확정 → 앱에서 나가기 → Store 환불 → 같은 그룹으로 처음부터 다시 보기: **불가**. 현재 의도적 재시작은 새 operation/key·새 Session이므로 기존 group이나 mock가 같아도 보호 예외가 아니다.

반면 응답이 유실돼 같은 요청을 다시 보내고 원래 결과/Session 식별을 복구하는 것은 신규 사용이 아니다. 동일 key 복구는 합의된120일 멱등 보장 및 기존 lifecycle 범위를 따른다. 삭제·종료된 Session을 재생성하거나 무기한 재시도를 보장하지 않으며, 새 이어풀기 기능을 추가하지 않는다. 과거 성공 결과의 재현과 현재 시험 이용 허용은 구분한다.

**기존 lifecycle에서 종료가 확정된 Session은 부활시키지 않는다. 앱 이탈·통신 실패·일시적인 채점 실패만으로 종료로 판단하지 않는다.** 기존 정책상 재시도 가능한 채점 작업과 확정 terminal을 구분하며, 앱을 닫았다는 사실 자체로 보호 예외를 제거하지 않는다.

| 경우 | 기대 결과 |
| --- | --- |
| confirm 성공 S1, 이후 환불, S1은 여전히 유효 진행 중 | S1의 기존 제출·채점·완료 허용 |
| 같은 group G1에 새 S2 생성/REPLACEMENT | 환불된 source로 거절. S1 예외를 S2에 복사하지 않음 |
| 같은 key·같은 요청의 응답 유실 retry | 합의된120일 멱등 보장·lifecycle 범위에서 최초 결과 복구. 삭제·종료 Session 재생성 금지 |
| 앱 이탈·통신 실패·일시 채점 실패만 발생 | 종료 확정으로 추정하지 않음. 기존 lifecycle/재시도 규칙 적용 |
| S1이 기존 lifecycle에서 종료 확정 또는 삭제됨 | 환불 예외로 재개·재생성하지 않음 |
| reserve 성공·LC Session 저장 뒤 환불, 아직 confirm 전 | reserve만으로 보호되지 않음. confirm 영속 거절 확인 후 정리 |
| 다른 유효 paid/free 권리가 있음 | 기존 guard·새 자격 판정으로 별도 시작 가능. 종료 source의 재사용 우회 아님 |
| 탈퇴·삭제 등 lifecycle deny가 있음 | 보호 Session 예외보다 우선 |

### 2.2 서비스 책임

| Billing | Learning Core |
| --- | --- |
| Store 최종 환불 증거 검증·반환액/원장·해당 구매 잔여권 종료·reflow | Store 금액/환불 여부를 앱 주장으로 판정하지 않음 |
| confirm/refund 공통 쓰기/CAS로 순서 확정 | confirm/status 권위 결과 확인 후 Session 활성/보상 |
| 구매/source 귀속·confirm 증거·보호 대상 고정 | source·operation·exact Session 연결 유지 |
| root 즉시 차단, group별 불변 outbox·재시도 | 단일 group revoke 문서에 멱등성/deny/보호 예외 원자 저장 후204 |
| 기존 guard의 exact 전이·늦은 상태 보호 | operation/Session lifecycle·중복 생성·신규 제출 통제 |

환불은 문의 접수만으로 발생하지 않는다. Google 운영자 처리/Apple 고객 신청 안내는 문의·결제 운영 경계이며 LC에 환불 실행 API를 요구하지 않는다. Identity는 기존 REFUND 접수 경로를 유지하며 이번 정책만으로 신규 endpoint가 필요하지 않다.

## 3. 사용자가 결정해야 하는 사항 / LC 회신 요청

제품 정책 재선택은 없다. LC 담당자는 아래를 코드/테스트 기준으로 회신해 주기 바란다.

1. S1 보호와 같은 group의 S2 차단을 각각 어느 생성·제출·Job 경로에서 적용하는가?
2. confirm 응답 유실·revoke 선도착 때 status-first와 기존 operation 복구가 어디에 연결되는가?
3. R2 불변 event/group unique·두 충돌 오류·역방향 JWT 규격과 실제 수신 구현 사이 차이가 있는가?
4. terminal·삭제·탈퇴·늦은 이벤트가 보호 예외로 우회되지 않는가? TMI-217에 남길 보존/과거 owner 문제는 무엇인가?
5. 아래 공동 테스트의 담당/완료·미완료 결과와 reader/receiver 선배포 조건은 무엇인가?

기존 합의와 코드가 다르면 새 fallback이나 임시 성공 처리를 넣지 않고 차이를 보고한다. 이 문서 작성만으로 상대 수락 또는 실제 기능 구현 완료를 기록하지 않는다.

## 4. 주요 위험과 미확인 사항

- group 동일성만 확인하면 환불 뒤 무제한 재응시 우회가 생긴다. 예외는 exact Session 하나이며 currentSession으로 대체하지 않는다.
- 늦게 온 revoke가 이미 terminal인 Session을 다시 열어서는 안 된다. 먼저 온 revoke도 confirm 성공 증거를 무시한 무조건 종료 근거가 아니다.
- confirm/refund 선후는 Billing Tx/CAS 기준이다. providerConfirmedAt·LC 수신시각·클라이언트 발생시각 비교로 바꾸지 않는다.
- Billing root 차단과 LC 반영 사이 전파 지연은 존재한다. 신규 요청은 Billing에서 최종 차단하며 LC는 전파·복구 지연을 관측한다. 즉시 전역 동기 차단을 보장한다고 보고하지 않는다.
- command7일 TTL에만 보호/거절 근거를 의존하지 않는다. 최신120일 receipt 계약과 금융/미완료/group 보존은 서로 다르다. 자료별 purge·과거 owner는 TMI-217과 별도 확인한다.
- R2의 오프라인 fixture 통과는 실제 HTTP/security chain·Mongo·시험 lifecycle 검증이 아니다. 이번 작업에서는 LC 코드를 조회하거나 실행하지 않았다.

## 5. 현재 작업과 직접 관련된 기술 연결

### 5.1 confirm/refund 양방향 경합

**confirm 선행:** Billing 최초 CONFIRMED 성공은 환불 뒤에도 replay한다. LC는 같은 operation의 성공/귀속 증거를 확인하고 기존 로컬 마무리 절차를 따른다. 이미 적용된 삭제/terminal/lifecycle deny는 우회하지 않는다. 응답 유실을 이유로 confirm 취소·무료권 재지급·새 key 발급을 하지 않는다.

**환불 선행:** Billing은 RESERVED→CANCELED, confirmRejection={code:ENTITLEMENT_REVOKED}, terminalAt, 권리 정리를 원자 확정한다. LC는 status에서 일반 취소와 환불 거절을 구분한다. RESERVED·통신 실패·404만으로 안전 보상이라 단정하지 않고 기존 복구/오류 분류를 유지한다. 이미 성공한 confirm을 사후 거절로 덮어쓰지 않는다.

새 제출 마감·paid 전용 채점 승인 API는 추가하지 않는다. 기존 상태 이벤트 v1의204를 채점 승인 증거로 재해석하지 않는다. protected Session 진행 중 guard를 유지하며 늦은 원 group 상태가 새 operation/group의 guard를 해제하지 않도록 한다.

### 5.2 revoke 수신 계약

정확 규격은 [R2 §5](LC-BILLING-PAID-EXAM-CONTRACT-R2.md)와 [최신 한정 수락](LC-BILLING-PAID-EXAM-SIMPLIFIED-R1-ACCEPTANCE.md)을 참조한다. 기존 원본/fixture bytes는 수정하지 않는다.

- POST /internal/v1/entitlements/access/events, AttemptGroupAccessRevoked v1. eventId/producer/scope/userId/group/reason/occurredAt/confirmedSessionId는 원문 exact 규격을 유지한다.
- confirmedSessionId는 **필수 nullable**. null이면 보호 대상 없음이며 생략·현재 Session 추론·미래 Session 자동 보호가 아니다.
- 최초 group별 eventId/body/digest/보호 대상은 불변. additional refund/금액 정정으로 새 version/event/보호 Session을 만들지 않는다. accessVersion/gradingSessionId 제안은 구현하지 않는다.
- 동일 eventId+digest는204, 같은ID 다른내용은409 EVENT_ID_CONFLICT, 같은 logical group 다른ID는409 GROUP_REVOKE_CONFLICT. 최초/중복 모두 durable commit 또는 기존 commit 확인 뒤 body 없는204.
- LC는 별도 inbox 없이 단일 group revoke 문서에 eventId/digest·deny·보호 예외를 원자 저장한다. eventId/group 유일성을 모두 보장한다. revoke-before-session도 deny 보존. 늦은 Session/status가 deny를 해제하지 않는다. 최초 보호 대상으로 지정된 Session도 귀속/confirm 증거 및 lifecycle 검사를 통과해야 한다.
- 최소 최초 사실/보호 예외 보존과 문서 폐기 기준은 LC P6·TMI-217에서 확인한다. 단일 문서에 종전 inbox TTL을 그대로 적용해 진행 중 증거를 지우지 않는다. 증거 부재를 신규 권한으로 간주하지 않으며 폐기 후 보장 범위는 별도 보존 계약을 따른다.

### 5.3 인증·장애·공개 오류

Billing→LC 전용 RS256 workload JWT와 TLS/전용 connector, R2 issuer/sub/audience·시간·키 규격을 적용한다. LC→Billing/user/Identity trust와 분리하며 SigV4/무인증 fallback 금지. 기본 OFF, 실환경 origin/port/key 검증 전 활성화하지 않는다.

Billing은 동일 event/body를 재전송하고 JWT/jti만 새로 발급한다. exact204만 성공이며 응답 유실은 중복으로 수렴한다. 인증 오류는 BLOCKED_AUTH, 계약 충돌은 검토, 일시 장애는 기존 재시도/경보로 처리한다. 세부 publisher retry 값은 PLAN-012 P0에서 기존 worker와 대조한다.

공개 오류는 기존 R2 §4.1 기준으로 앱 reader와 확인한다. 영속 환불 거절409 ENTITLEMENT_REVOKED, 진행409 EXAM_CREATION_PROCESSING, 일시503 BILLING_TEMPORARILY_UNAVAILABLE을 구분한다. 신규 권리 부족의 기존 오류와 모든 실패를 환불 오류로 통합하지 않는다. 구매 재시도나 새 결제를 자동 유도하지 않는다. 실제 앱 매핑 구현 완료는 이번 문서의 확인 범위가 아니다.

프론트 전달 요지: 현재 시험 완료 가능과 다시 응시 가능은 다르다. 응답 유실은 같은 key로 복구하고, 의도적 재시작은 새 자격 확인이 필요하다. 환불 거절은 안내·권리 새로고침/문의로 연결하며 자동 결제하지 않는다. 이번에는 프론트/Notion 문서를 직접 수정하지 않았다.

## 6. 부록 — 공동 테스트·완료 기준

아래 H 번호는 인계 점검표이며 기존 R2 V01~V06 또는 공유 fixture 식별자를 바꾸지 않는다.

| ID | 사례 | 기대 결과 |
| --- | --- | --- |
| H01 | S1 confirm→환불→S1 제출/채점 | 기존 lifecycle 내 완료, 신규 마감 없음 |
| H02 | H01 후 같은 G1 새 S2 재응시 | 종료 source 사용 거절, S1 예외 승계0 |
| H03 | confirm 성공 응답 유실→환불→같은 key retry/status | 합의된120일 경계·lifecycle 확인, 최초 결과 재현/삭제·종료 S1 재생성0, 신규 Session/복원 지급0 |
| H04 | reserve/LC 저장→환불→confirm | 영속 CANCELED/confirmRejection 확인 뒤 orphan 정리 |
| H05 | confirm/refund 동시 실행 | 공통 Tx commit 순서로 H01/H04 중 하나, 이중 성공/부분 쓰기 없음 |
| H06 | revoke가 LC confirm 응답/마무리보다 먼저 도착 | status-first·귀속 확인, 보호 S1만 기존 절차로 마무리, 새 S2 허용0 |
| H07 | revoke가 Session 저장보다 먼저 도착, null/명시S1 각각 | deny 영속, null에서 보호 추론0, 지정S1도 confirm/lifecycle 확인 |
| H08 | duplicate·응답유실·같은ID다른내용·같은group다른ID | 동일204/두409 exact, 최초 보호 대상 불변 |
| H09 | 앱 이탈/통신·일시 채점 실패와 확정 terminal/삭제/탈퇴 각각에 지연 revoke·status | 일시 실패만으로 종료/보호 제외0, 종료 확정·삭제 대상 부활0, lifecycle 우선 |
| H10 | protected 진행·늦은 원group 완료·새 operation guard | 진행 guard 유지, exact 대상 외 해제0 |
| H11 | 다른 구매/무료/기지급 복원권 존재 | 보존, 새 시작은 guard/자격 통과 후만 |
| H12 | command TTL 소실·단일 revoke 문서 보존/폐기 경계·전파 장애 | 영속 근거에서 replay/deny, 진행 중 증거 보존·자료 없음으로 권한 부여0·적체 경보 |
| H13 | 역방향 JWT 오류·OFF/다른port·실제 DB 장애 | R2 오류/무변경 경계, 미commit204 없음 |

Billing 담당: 금융/root/source/confirm·guard·불변 outbox·발신 및 Mongo 경합. LC 담당: receiver 원자성·operation/status 복구·Session 제출/Job/lifecycle·공개 오류. 양측 공동: H03~H10의 실제 HTTP/응답유실·동시성 및 실환경 gate.

코드 구현 후 각 저장소 단위/실제 Mongo/HTTP/공동 E2E 결과를 분리해 보고한다. 테스트 시 실제 결제/환불을 무단 실행하지 않는다. LC reader/receiver와 보호 예외가 준비된 뒤 publisher·환불 processor를 검증하고 유료 admission/판매는 마지막에 별도 승인한다. 구 GRADING-only consumer로 무조건 rollback하지 않는다.

### 근거와 변경 범위

- [확정 정책](PAYMENT-CONFIRMED-SESSION-REFUND-POLICY-2026-10-13.md)
- [PLAN-012](../plans/PLAN-012-refund-ledger-and-access-revocation.md), [PLAN-011](../plans/PLAN-011-paid-reservation-and-usage-evidence.md)
- [R2 원본](LC-BILLING-PAID-EXAM-CONTRACT-R2.md), [단순화 수락](LC-BILLING-PAID-EXAM-SIMPLIFIED-R1-ACCEPTANCE.md)
- 이번 범위: Billing 문서 작성/링크/작업 기록. 코드·fixture·LC/Identity·Jira·외부 메시지·DB·배포·commit/push 변경 없음. 실제 전달은 사용자가 이 문서를 공유하는 단계다.
