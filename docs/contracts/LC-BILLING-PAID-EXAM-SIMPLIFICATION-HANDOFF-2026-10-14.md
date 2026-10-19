# LC–Billing 유료 시험 단순화 인계 — 2026-10-14

> 2026-10-14 공동 기술 검토 기준 통합: [유료 시험 계약 R2](LC-BILLING-PAID-EXAM-CONTRACT-R2.md)를 우선 참조한다. 최신 승인 정책·LC 최소wire 제안을 한 곳에 정리했으며 본문의 종전 기술 후보는 변경 이력이다. 새정본의 exact 공동수락/fixture/구현·배포는 미완료이고 phone/삭제/보존 gate는 별도 확인한다.

- 사용자 승인 근거: 단순화·전체 답안 접수 기준 문제 선택 정리에 대한 「좋아 그럼 이거 인계하게 다시 정리해줘」
- 상태: 최신 제품/설계 방향 인계. draft-3의 아래 항목을 변경하며 exact wire·저장/보존·fixture는 LC 공동 확인 후 동결한다. 코드·DB·배포 변경 없음.
- 종전 기술 초안: [wire R1](LC-BILLING-PAID-EXAM-WIRE-R1.md), [전달한 draft-3 snapshot](LC-BILLING-PAID-EXAM-WIRE-R1-DRAFT-3.md). snapshot은 수정하지 않는다.

## 1. 문제는 LC가 하나만 선정

| 이전 시험 상태 | 새 시험 문제 | 기존 접수 작업 |
| --- | --- | --- |
| 일부 필수 답안만 접수 | 같은 mockExamId로 처음부터 시작 | 접수된 문항 채점만 마무리; 새 문항 추가 접수 금지 |
| 모든 필수 답안 접수 완료, 채점/요약 대기 | 직전 mockExamId를 제외한 후보에서 기존 선정 규칙 적용; 다른 적격 후보가 하나도 없을 때만 동일 문제 허용 | 채점·요약 완료 허용; 전체 완료 조건 충족 시 기록 표시 |
| 시험 완료 또는 기존 시험 없음 | 기존 문제 선정 규칙으로 선택 | 기존 완료 기록 유지 |

기준은 화면 열람/클라이언트 제출 주장/요약 완료 시각이 아닌 LC의 durable 필수 답안 접수 증거다. 같은 요청 replay에는 최초 고정한 판정을 사용한다. 새로운 시험은 새 Session이며 이전 답안/점수/Job/요약을 승계하지 않는다. 다음 시험은 숫자상 다음 문제라는 뜻이 아니다. 2026-10-14 사용자 「어 그렇게 하는 게 좋을 거 같은데」로 전체 접수 후 채점/요약 대기 중에는 직전 문제를 제외하고 남은 적격 후보에 기존 LC 선정 정책을 적용하는 변경을 승인했다. 나머지 적격 후보가 실제로 0개임이 확인될 때만 직전 문제를 허용한다. catalog 조회 실패/선정 오류를 후보 없음으로 취급하지 않는다. 이미 완료된 시험 이후에는 기존 선정 규칙을 유지한다. 새 시험은 유효한 Billing 권리 확정이 필요하며 전체 제출만으로 추가 무료권을 부여하지 않는다.

`restartContext.initialMockExamId`와 두 문제 후보 방식은 제거하는 방향이다. LC가 선택한 mockExamId 하나를 보내고 Billing은 권리/그룹을 판정한다. 삭제 continuation·phone과 기존 group 문제 echo 검증은 이 변경에 맞춰 공동 수정해야 한다.

중요: 모든 답안이 접수됐지만 Billing group 상태가 아직 OPEN인 경합에서, 다음 문제를 기존 REPLACEMENT로 오인하면 안 된다. LC의 최초 접수 판정과 원 대상 고정, 이를 Billing이 인증된 요청에서 어떻게 구분할지(예: 최소 시작 사유)는 공동 wire 과제다. 단순 mock 불일치를 근거로 권한을 허용하거나 기존 echo 검사를 통째로 삭제하지 않는다. 문제 후보 1개 승인은 이 근거 필드까지 불필요하다는 뜻이 아니다.

## 2. 모든 권리 전환에 동일한 LC 마무리 규칙

새 시험 confirm 성공 증거 뒤 LC에서 원 Session의 신규 제출을 차단하고 새 Session을 활성화한다. 원 Session 종료 전에 durable하게 접수한 문항의 채점·결과 저장, 이후 생성되는 Summary Job과 시스템 재시도는 허용한다. 일부 접수만으로 전체 COMPLETED를 만들지 않는다. 결과는 원 시험에 남는다.

이 완료 처리 규칙은 최초 trial→paid뿐 아니라 다른 권리 전환에도 동일하게 적용한다. LC에서 TRIAL_TO_PAID/OTHER_SOURCE_CHANGE에 따라 완료 처리를 다르게 하지 않는다. 해당 구분은 공통 전환 정보로 단순화하고 복원 자격 판단은 Billing 내부에 둔다. 원/신규 group·Session의 exact 연결은 유지한다.

이로써 draft-3의 OTHER_SOURCE_CHANGE 제외 사유 중 'LC 완료 정책 미정'은 해소된다. 다만 어떤 권리로 새 INITIAL/REPLACEMENT가 가능한지, phone/삭제 owner·continuation 검증은 별개다. 이 인계만으로 모든 전환 조합을 활성화하거나 무효/환불 권리의 replacement를 허용하지 않는다. 삭제/탈퇴·이미 terminal인 Session을 부활시키지 않는다.

## 3. Billing의 무료권 복원 및 소비

복원은 최초 체험 consumption당 최대1회이고 복원권/초대권 소비는 재복원하지 않는다. 원 TrialClaim·consumption을 삭제하거나 다시 열지 않는다. 구매만으로 복원하지 않고 승인된 paid confirm 전환 때만 별도 grant/ledger로 기록한다. paid 기간은 기존 timeline을 따른다.

Billing의 유료 확정·복원·원 그룹 재응시 차단은 같은 Transaction이다. 이미 접수된 시험이 전환 뒤 늦게 완료돼도 확정 복원권을 회수하지 않는 기존 허용을 유지한다. 전체 제출/요약 대기라는 사실만으로 새 복원 자격을 만들거나 기존 자격을 철회하는 변경은 이번에 추가하지 않는다. 최초 trial 여부·원 consumption·Billing의 완료/전환 판정 경합은 기존 정책에 따라 fixture에서 확인한다.

## 4. 기존 operation으로 끝까지 처리

문제/원대상/접수판정을 고정 → reserve → 확인대기 새 Session commit → Billing confirm/status 성공 확인 → LC 로컬 마무리 Transaction → 성공 응답 순서다.

LC 로컬 마무리에서 새 Session 활성·exact 이전 신규제출 차단·operation 성공을 함께 저장한다. 마무리 전 guard 유지·TTL 삭제 금지, 같은 operation의 영속 복구를 유지한다. 기존 Job 채점 완료까지 기다리지 않는다. confirm 성공을 cancel하거나 새 operation으로 다시 지급하지 않는다. 동일 key/digest 결과 replay는 신규 current Session stale 검사보다 우선하며 owner 인증/fence를 우회하지 않는다.

## 5. 그룹별 불변 차단 사실

revoke는 그룹당 최초 차단 내용·eventId·보호할 confirmedSessionId(null 포함)를 고정하고 동일 내용을 재전송하는 방향으로 단순화한다. accessVersion·high-water·버전별 stale/semantic history는 제거한다. 같은 eventId 다른 내용, 같은 group 다른 차단 내용은 충돌로 처리한다. eventId·group의 유일성 및 원자적 반영은 필요하다.

최초 confirm 전에 환불됐다면 보호 Session을 새로 추가하지 않는다. 최초 확정 차단을 후속 금액 정정/추가 환불 때문에 다시 발행하거나 보호 대상을 바꾸지 않는다. 금전 원장은 Billing에 계속 기록한다. 종료/차단된 Session을 다시 허용하는 기능은 추가하지 않는다.

제거 가능한 정확한 version/semantic 필드, 최초 reason 고정·중복/충돌 오류, 그룹 단위 outbox 유일성, 같은 event 재시도의 bytes/hash를 공동 동결한다. dedupe 및 deny/예외 증거 보존·삭제 후 replay 방지·개인정보 purge는 여전히 필요하다. 버전 제거로 보존 문제가 사라지지는 않는다. 별도 역방향 JWT·배포형 trust·수신OFF/port/auth 경계는 유지한다.

## 6. 거절 시각과 기존 오류

`confirmRejection.rejectedAt`은 제거하고 기존 `terminalAt`을 거절 시각으로 사용한다. `confirmRejection.code`는 유지한다. 거절 object는 CANCELED에서만 허용하고 일반취소와 구분한다. 거절/성공 증거의 영속 복구와 command TTL 이후 재현은 유지한다.

status는 기존 body userId/operationId 조회, Idempotency-Key 불필요다. 마무리/guard 기존409·일시 장애 기존503 및 Retry-After1, 신규 두409 후보의 구분은 유지한다. 내부 CONTRACT_ERROR/auth circuit을 일시 retry로 합치지 않는다.

## 7. 기존 시험 및 문서

출시 전 시험은 서버 측 exact 목록으로 구분해 기존 규칙/기록을 보존한다. 신규 시험·의도적 새 재응시부터 Billing/source 필수. source 없음만으로 legacy를 허용하거나 기존 시험을 소비/복원 증거로 바꾸지 않는다.

다음 공동 정본은 현재 유효 규칙만 한 번씩 기술하고 draft별 변경 이력은 별도로 보관한다. 이번 인계는 새 전체 정본/fixture를 이미 제작했다는 뜻이 아니다. draft-3의 두문제후보·전환유형별완료·version·rejectedAt 조항을 그대로 구현하지 않는다.

## 8. LC 회신 요청 및 fixture 수정

다음을 확인해 달라.

1. 전체 필수 답안의 durable 접수 판정·부분 접수/요약 대기/완료에 대한 단일 문제 선정, 원 Session 접수와 준비의 경합·동일요청 재현.
2. 단일 mock 요청에서 계속 재응시와 전체접수후 신규시험을 구별할 최소 wire 및 INITIAL/REPLACEMENT 검증, phone/삭제 continuation 적용.
3. 전환 유형과 무관한 LC 마무리·기접수/파생Summary 완료, 복원 판단 Billing 전담의 공통 transition 응답.
4. 그룹별 최초 불변 revoke와 eventId/group 충돌, version/semantic history 제거, terminalAt 재사용.
5. 기존 fixture 명세에서 두후보/type/version/rejectedAt 케이스를 교체하고 부분접수→같은문제·전체접수대기→다음문제·지연완료·불변차단/후속금액정정 케이스를 추가.

기존 draft-3 fixture 명세를 그대로 생성하면 안 된다. 최소 wire·불변 revoke와 남은 보존/삭제·phone/실환경 조건을 확인한 뒤 새 공동 정본/revision → fixture제작 → bytes/hash대조 → 독립검증 → 최종동결 순서로 진행한다. 코드/DB/배포 활성화는 이번 인계 작업에서 수행하지 않았다.


## 9. LC 최소 wire 검토 후 문제 선정 확정 — 2026-10-14

LC 검토에서 기존 selectExam이 완료 횟수와 catalog 순서를 사용하므로 요약 대기 시험과 같은 mock가 다시 선택될 수 있음을 확인했다. 사용자 승인으로 §1의 전체 접수·채점/요약 대기 행을 직전 mock 제외 방식으로 수정했다. 접수 완료를 실제 COMPLETED로 표시하거나 완료 횟수 원장에 가짜 완료를 기록하지 않는다.

LC는 durable 접수 상태와 원 대상, 선정된 mock를 operation 최초 준비 시 고정한다. 같은 요청 replay에서는 이후 요약 완료나 catalog 변화로 재선정하지 않는다. 모든 답안 접수 증거는 알림 tracking flag에 의존하지 않게 보장해야 하며, 접수 marker 없음만으로 INCOMPLETE를 추정하지 않는다. 이 부분은 LC 제안의 구현 검토 항목이고 현재 구현 완료를 뜻하지 않는다.

추가 fixture: (1) 다른 적격 mock가 있으면 직전 mock 제외, (2) 실제로 다른 적격 mock가 없으면 같은 mock 허용, (3) catalog 장애 시 같은 mock fallback 금지, (4) 어떤 mock가 선택돼도 전체접수 후 신규 시작은 권리 판정이 필요한 INITIAL이며 기존 consumption REPLACEMENT로 자동 처리하지 않음, (5) 같은key 사유/mock 고정 및 마지막접수/준비 순서 경합. `startReason` exact 명칭·phone/삭제 적용·transition 조건·불변revoke 충돌/보존은 LC 최소wire 검토를 바탕으로 공동 정본에서 확정한다.
