# PLAN-011 단순화 승인 — 재가입 유료 우선 유지

> 구현 규격 제안: [단순화 exact R1](LC-BILLING-PAID-EXAM-SIMPLIFIED-EXACT-R1.md). 오류·120일 정각/보존·phone snapshot/복원 경합과 E/T/P/C 테스트 명세를 정리했다. 공동 수락/신규 fixture 실행은 후속이다.

- 날짜: 2026-10-14
- 상태: 사용자 정책/설계 방향 승인. exact 공동 계약·새 fixture·제품 구현·배포는 후속.
- 근거: 재가입 유료 우선·조건부 복원안을 제외한 나머지 단순화안 채택 요청. 여기서 “제외”는 재가입 무료 우선 단순화를 채택하지 않고 사용자에게 인용된 유료 우선 정책을 유지한다는 의미다.
- 관련: PLAN-011/012/013, TMI-116/TMI-128/TMI-122/TMI-125, LC 삭제 TMI-193/TMI-136.

## 1. 5줄 결론

1. 재가입은 target의 유효 paid 우선, 없으면 정상 이전된 기존 무료 재응시 권리를 사용한다.
2. 자격 있는 최초 trial→paid confirm은 원 consumption ID당 한 번 target에게 복원권을 지급한다.
3. 생성 operation 진행 중 삭제 요청은 접수/대기열 저장하지 않고 재시도 안내로 거절한다.
4. MEMBER 증거가 없으면 앱이 기존 purchase-account를 호출해 보강하고 worker는 별도 증거 수집 시스템을 만들지 않는다.
5. 멱등 결과 보장 및 종료 후 상세 복구 기록은120일로 제한하며 이후 과거 key가 새 요청으로 처리될 수 있는 위험을 수용한다. 금융/소비/복원/진행 증거는 별도 보존한다.

## 2. 확정 동작

### 2.1 재가입은 유료 우선·조건부 복원

target 계정의 유효한 paid로 새 시험을 시작한다. paid가 없으면 정상 이전된 기존 무료 continuation을 사용한다. paid 존재/자격이 불명인 상태를 무료 fallback으로 처리하지 않는다. 원 계정 paid/이미 지급된 복원권을 target에 자동 이전하지 않는다.

유료 confirm 시 원 trial이 미완료 최초 체험이고 원 consumption 기준 복원 이력이 없으며 명시 사용자 삭제 대상이 아닌 경우 target에 복원권1개를 지급한다. 이미 복원된 소비·복원권/초대권 소비·삭제한 시험은 추가 지급하지 않는다. 재가입만으로 지급하지 않는다. 원 consumption ID unique로 계정 변경/반복 재가입에도1회를 유지한다. 기존 rebind GRADING pending/COMPLETED NOOP·owner epoch 검증은 유지한다.

이 수혜 정책은 사용자 승인으로 확정한다. phone echo/새 INITIAL snapshot/귀속 근거 및 실제 경합 검증은 공동 기술 작업이다. 재가입 무료 우선 제안은 채택하지 않는다.

### 2.2 생성 중 삭제는 접수 거절

생성 중은 Billing reserve/confirm 및 LC 새 Session 활성·이전 제출 차단·operation 마무리까지 완료되지 않은 상태다. Session 문서 존재만으로 생성 완료를 판정하지 않는다. 응시 중인 정상 Session을 삭제하지 못한다는 의미가 아니다.

삭제 요청과 생성 operation 준비는 같은 사용자 fence/Transaction으로 순서화한다. 생성이 먼저면 삭제를 pending operation으로 저장하지 않고 거절한다. 삭제가 먼저 접수됐다면 기존 삭제 절차와 신규 생성 차단을 유지한다. 검사 직후 생성이 끼어드는 check-then-act 경합을 허용하지 않는다.

안내 문구는 “시험 준비 중입니다. 잠시 후 다시 시도해 주세요.”다. 정확 public HTTP/code는 기존 LC 오류 계약을 대조해 공동 동결한다. 내부 서버 queue/자동 삭제 재실행을 만들지 않는다. 생성이 장애로 멈추면 기존 operation을 복구한 뒤 사용자가 삭제를 다시 요청한다.

따라서 종전 REQUEST_RECORDED 상태에서 활성 생성 종료를 기다리는 queue 및 삭제 deny를 우회하는 finalize 특례는 이 승인 범위에서 제외한다. 삭제 접수 후 Billing ACK와 원문 정리를 분리하는 규칙은 유지한다. Billing 장애로 개인정보 삭제 자체를 무한 중단하지 않는다.

### 2.3 MEMBER 증거는 앱이 보강

앱이 현재 유효한 Identity JWT로 기존 purchase-account를 호출하면 Billing이 검증된 MEMBER 정보를 저장하는 안을 사용한다. 기존 계정/ref 유지·WITHDRAWN 선행 거절·원문 JWT 미보관을 지킨다. 재가입/재결제 요구가 아니다.

worker는 증거를 임의 생성하거나 사용자 token을 저장/재사용하지 않고 pending/관측 상태와 원 operation 증거를 보존한다. 앱 복귀·동일 key 재시도로 보강 후 진행한다. status-only worker가 reserve를 새로 발행하도록 확장하지 않는다. 증거 없음은 GUEST/권리 부족과 구분한다. 신규 실시간 Identity 조회/전체 회원 projection은 추가하지 않는다.

### 2.4 120일 보장과 상세 기록 정리

사용자가 승인한 단순화는 **동일 key 결과의 멱등 보장기간을120일로 제한**하는 것이다. 기간 종료 후 앱이 과거 UUID를 재입력하고 남은 중복 근거가 없으면 새 요청/새 시험·추가 무료권 소비가 발생할 수 있다. 이 위험을 방지하기 위한 새 서버 발급 시작권/영구 최초-key 저장 체계는 이번 범위에서 만들지 않는다.

기산점 제안은 종전 검토의 구분을 유지한다: 요청 재실행 상한은 최초 operation 생성시각, 종료 후 상세 receipt 보존은 최초 terminalAt부터120일. 상세 결과가 더 오래 남으면 그것을 사용해 replay해도 되지만 외부 무기한 보장을 하지 않는다. 정확 정각/clock·public 문구는 공동 동결한다.

앱은120일 지난 요청을 자동 재전송하지 않도록 한다. 앱이 오래된 UUID를 새 요청처럼 보내는 것을 서버가 완전히 구별한다는 주장은 하지 않는다. 기존 operation age 검사·새 issuedAt은 과거 UUID 신규 재입력 탐지 수단이 아니다.

진행 중/unknown commit/격리 작업은120일 경과만으로 폐기하지 않는다. 기존24시간/200회 격리·운영 복구 경계가 우선하고120일까지 계속 자동 retry하는 기능을 만들지 않는다. 최소 결과/금융 근거로 안전한 수동 확인이 불가능하면 과거 성공/미소비를 추정하지 않는다.

## 3. 삭제해도 되는 기록과 유지할 기록

| 대상 | 정책 |
| --- | --- |
| 종료된 상세 복구 receipt·응답 재현용 기록 | terminal 후120일 정리 방향. 미해결/분쟁/법정 근거는 별도 확인 |
| 최초-key 전용 tombstone |120일 이후 중복 보장 해제를 전제로 정리 가능. 영구 저장 체계는 제외 |
| 미완료 operation/unknown 결과·필수 복구 증거 | 해결/안전 종료 전 자동 TTL 삭제 금지 |
| 결제·환불 원장 | 기존 승인 보존 유지.120일로 단축하지 않음 |
| 무료권 소비·원 consumption 복원1회 증거 | 관계 권리/승인된 보존기간 유지. 멱등기간 종료로 재복원 허용하지 않음 |
| 삭제/revoke 차단·보호 Session 증거 | source 보존/진행 상태에 맞춰 별도 유지. 과거 key 위험 수용이 환불권리 재사용 허용은 아님 |
| 시험 답안·결과 | 기존 학습 삭제 정책. 상세 복구120일과 무관 |

이번 승인으로 V07의 모든 개인정보 기간/폐기 조합이 확정된 것은 아니다. 다만 “120일 후에도 모든 과거 key를 반드시 식별해야 해서 purge할 수 없음”은 더 이상 제품 요구가 아니다. 중복 방지 이외의 금융/소비/권한 근거가 필요한 자료는 그대로 보존한다.

## 4. 공동 변경·회귀 항목

- LC: 생성 중 삭제 미접수·생성/삭제 fence 양순서, 앱 재시도 안내, MEMBER 앱 보강·same-key 복구,120일 요청 정책 및 삭제 후 과거 성공 접근 차단.
- Billing: confirm/복원/원 그룹 차단 동일 Transaction, 원 consumption unique, phone target 수혜/owner 검사, 상세/최소 근거 분리 purge.
- 공동:120일 내 응답 유실/재전송 수렴, 기간 이후 과거 key 재입력의 보장 제외, 남은 소비/복원 unique 유지, unknown 작업 비폐기.
- 코드 구현/새 fixture 승인 전 기존 A/R2/fixtures-2를 수정하지 않는다. 새 규칙은 새 보완 revision에 반영한다.

## 5. 기존 문서와 관계

[회신 반영 R2](PLAN-011-REMAINING-TECHNICAL-REVIEW-RESPONSE-R2.md)의 생성 중 삭제 queue/기존 생성 finalize 특례 및 V07의 기간 후 무조건 같은-key 식별 요구는 위 범위에서 대체한다. 기록상 당시 위험 분석은 유지한다. [J4/S15/V07 정책](PLAN-011-J4-S15-V07-DECISIONS-2026-10-14.md)의 사용자 삭제 미복원/다른권리 보존·MEMBER 증거/탈퇴 deny 방향은 유지한다.

## 6. 작업 범위

문서와 참조·작업 기록만 변경했다. 실제 코드/DB/purge/LC/Identity/배포·Jira·외부 전송·commit/push 없음. 문서 링크/diff·기존 계약/fixture hash 검사만 수행하며 Gradle은 재실행하지 않는다.


### 2026-10-14 LC·Identity 정책 동기화 회신 접수

사용자 전달 회신에 따라 LC 구현 계획과 Identity 검토 문서에 재가입 target paid-first/원 consumption당 target 복원1회, 생성 중 삭제 미접수, 기존 purchase-account 앱 보강,120일 멱등 보장·기간 이후 과거 key 재입력 위험 수용이 반영됐음을 접수한다. 이번 접수에서 타 저장소 문서/코드나 테스트를 독립 재확인하지 않았다.

이 범위의 제품 정책 판단은 종료한다. 영구 최초-key 저장 요구와 「과거 key 식별 불가 시 무조건 폐기 불가」의 이전 반대 의견은 해소됐다. 금융/소비/복원1회/권한 차단/미완료 증거의 별도 보존은 유지한다. Identity 탈퇴 기록의 기존 보존기간을 새120일 멱등 정책으로 변경하지 않는다.

남은 공동 기술 확정은 (1) MEMBER pending·생성 중 삭제·삭제 후 replay의 exact HTTP/code/envelope, (2)120일 기산점/정각/clock·상세 receipt와 최소 근거 폐기 구분, (3)phone INITIAL/REPLACEMENT snapshot·owner epoch·복원 수혜/중복/경합 표, (4)새 계약 revision 및 별도 fixture bytes/hash·양측 독립 검증이다. 정책을 다시 선택하지 않고 이 규칙을 구현 가능한 계약으로 작성한다.

양측 회신은 문서 반영/검사 결과이며 제품 구현·runtime 테스트·배포 승인이나 신규 exact 계약 최종 수락은 아니다.
