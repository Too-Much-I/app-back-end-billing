# TMI-217 — LC 기술 검토 인계

- 날짜: 2026-10-14
- 기준: `lc-billing-exam-deletion-retention-r1-draft-3`
- 상태: Billing 제안 정리·LC 검토 요청용. 제품 정책 재선택 요청이 아니다. 외부 전송/상대 수락/제품 구현 완료 아님.

## 1. 5줄 결론

1. 생성 중 삭제 미접수·명시 삭제 시험 재응시 포기·사용 무료권 미복원은 이미 승인됐다.
2. paid-first·원 trial 소비당 복원1회·다른 권리 및 이미 지급한 복원권 보존·120일 멱등 보장도 유지한다.
3. Billing은 삭제 route/body/인증/오류·owner 근거·경합표를 작성했고 PR21로 develop에 반영했다.
4. LC에는 아래 구현 규격의 호환성 확인을 요청한다. 기존 정책을 다시 결정하거나 별도 복구 시스템을 추가하자는 요청이 아니다.
5. 새 삭제 fixture·양측 제품 구현·실환경 검증은 아직 완료되지 않았다. E06-owner 정정의 독립 재검증과 구분한다.

## 2. 전달할 정본

- [공동 계약안 R1](LC-BILLING-EXAM-DELETION-RETENTION-R1.md): wire·owner 표·Transaction·보존 경계.
- [48개 검증 항목](../plans/TMI-217-DELETION-OWNER-RETENTION-VERIFICATION.md): 모두 새 삭제 계약 검증 계획이며 실행 PASS가 아님.
- [현재 단순화 exact](LC-BILLING-PAID-EXAM-SIMPLIFIED-EXACT-R1.md): 이미 수락한 정책/응답/120일 기준. 변경하지 않음.

## 3. LC 확인 요청 — 구현 위치와 호환성

| 항목 | Billing 제안 | LC에 필요한 확인 |
| --- | --- | --- |
| 삭제 접수 | 생성과 같은 사용자 fence에서 판정, 생성 중 기존409·queue0 | 기존 삭제 operation/생성 guard의 동일 Transaction 구현 위치 |
| 전달 | POST `/internal/v1/exam-deletions/events`, 정본7필드·eventId 불변 | 삭제 target에서 원문 제거 전 payload 보존 가능 여부 |
| 인증 | 기존 LC key/issuer/sub, 신규 exam-deletion-events audience·기존TLS connector | sender 목적 확장·기본OFF gate·route/port matrix |
| 중복 | 같은event/같은body204, 같은event다른body409, 다른event같은Session409 | 재요청 시 retained eventId 재사용. 종전 미수락 동일사실204 후보와 다른 단순화안임 |
| 과거owner | 원Session creationOwner 입증, 이전 후 source 삭제는 exact Session만 | 기존 lifecycle/merge/삭제대상과 일치하는지. 현재owner로 과거user를 추정하지 않음 |
| 개인정보 | 원문 정리는 ACK와 독립, 최소증거 보존·새생성은 ACK 후 | 기존 삭제worker checkpoint로 처리 가능한지 |
| 새시험 | 삭제fact 확인 후 기존restartContext로 INITIAL·새권리판정·삭제원인복원0 | 공개API 추가 없이 현재 writer/reader에 반영 가능한지 |
| 보존 | 상세120일·금융/소비/복원/deny 분리 | 미완료/hold·무기한권리 최소증거와 개인연결 최종 제거 조건 |

삭제전달과 신규생성의 경합은 LC/Billing 각자의 Transaction으로 다루며 분산 Transaction을 요구하지 않는다. Billing pending reserve가 남으면 삭제503으로 원 결과를 먼저 수렴한다. 원문 개인정보 정리를 그 ACK 때문에 중단하지 않는다.

## 4. 미확정과 다음 순서

제품 정책은 유지한다. 새 body/audience/오류의 exact 호환·legacy owner coverage·최종 개인 연결 제거 조건은 정본에 표시한 기술/운영 검토 대상이다. 특히 과거 source 삭제로 target 권리를 종료하지 않는 범위와 다른eventId 동일Session409는 상대가 수락했다고 추정하지 않는다.

LC는 항목별 `이견 없음 / 수정 필요(구체 필드·경로) / coverage 미확인`으로 회신하면 된다. 회신 반영 후 새 revision fixture 제작·bytes/hash 대조·양측 독립 검증, 이어 제품 구현·실Mongo/HTTP/상태 경합 검증을 진행한다. 이번 문서 병합을 새 route/purge 활성화로 해석하지 않는다.

## 5. 검증·변경 범위

문서 링크와 diff 검사만 수행했다. 이번에 제품 코드·새fixture·DB·LC/Identity 저장소·배포·외부 메시지를 변경/전송하지 않았다. 새 제품 변경이 없어 Gradle은 재실행하지 않았다. 기존 PLAN-012 병행 변경을 보존했다.


## 2026-10-14 LC 검토 회신 반영

owner 거절 규칙을 phone 재가입으로 한정하고 UserMerged의 동일 Session 이전은 검증된 이전 근거/현재 귀속으로 인정하는 조건을 정본§3.4에 보완했다. paid/MEMBER 복원권 merge 확대는 아니다. 생성중 삭제 접수409, 원문정리/ACK/새생성 단계분리, 삭제용 signer/route/gate는 현재 구현 차이로 확인했다. eventId 보존 경계는 coverage 미확인이다. LC sender/Billing receiver 명칭과24시간/200회 격리 우선도 명확히 했다.

검증 계획은 기존45개에 W11/O09/O10을 추가한48개이며 전부 계획이다. 이번 회신은 기술규격 전체 수락이 아니며 수정안 재검토·증거 coverage 확인이 필요하다.


## 2026-10-14 사용자 승인 — 단일 UserMerged 확인

게스트 A→회원 B의 같은 Session 이전 한 번만 확인한다. 회원 간/연속 병합을 위한 계보 추적·범용 자동 복구는 제외한다. 단일 이전 증거+현재 귀속으로 B의 삭제를 인정하고, 지연은 원event 보존 후 재확인·증거 누락/불일치는 운영 확인으로 둔다. 기존 재시도/격리 한도와 원문정리 독립·필요ACK 전 신규시험 차단은 유지한다. phone 재가입은 별도다. 정본draft-3/O08/O09에 반영했으며 공동 기술수락/코드구현 완료를 뜻하지 않는다.
