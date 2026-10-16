# TMI-217 삭제 연동 단순화 R2 — 사용자 승인 기준

> 후속: [저장·오류 exact draft-1](TMI-217-DELETION-EXACT-R2-DRAFT-1.md) 및 공통 사례44개를 작성했다. 초안 검토용이며 공동동결/실행PASS는 아니다.

- 날짜: 2026-10-16 / 상태: **단순화1·2 사용자 승인, LC 공동 exact 수락·구현은 미완료**.
- LC 회신 확인(2026-10-16): LC `docs/codex/TMI_217_DELETION_SIMPLIFIED_R2_REVIEW.md` 원문에서 S1~S4의 설계 방향 수락을 확인했다. 사용자 인가 LC책임·요청별event/동일전송불변·필요204 전 생성차단·event충돌 우선/이미삭제no-op에 이견 없음. 아래 "LC 동의 미확정"은 방향 수락에 한해 해소됐으며 exact저장/오류/보존/fixture 및 구현·배포는 계속 미완료다.
- 우선순위: 이 문서가 draft-3 및 전달·ACK 보완안 R1의 재삭제/소유권 규칙을 대체한다. 그 외 wire7필드·전용인증·원문정리/성공응답 분리·금융보존 정책은 유지한다.

## 1. 5줄 결론

1. 이미 삭제된 exact Session은 유효한 LC 요청과 대상 연결 확인 뒤 추가효과 없이204로 처리한다. 새 삭제 operation마다 최초 eventId를 찾아 재사용할 필요는 없다.
2. 같은 전송의 재시도는 원 eventId/body를 유지하고, 보존된 같은 eventId의 다른 body는409다.
3. 사용자 삭제 권한은 LC가 책임진다. Billing은 전용인증·환경·Session/group 연결 및 변경 영향 범위를 검사한다.
4. Billing의 새 guestMergeEvidence 및 삭제 목적의 과거 merge backfill은 제외한다. LC 인가 오류를 Billing이 독립 재검증하지 못하는 위험을 사용자가 수락했다.
5. 삭제로 다른 사용자의 현재group/guard/권리나 금융기록을 변경하지 않는다. 기본OFF·최종purge 별도·LC 수락 전 활성화 금지는 유지한다.

## 2. 재삭제·전달 규칙

| 상황 | 승인 방향 |
| --- | --- |
| 같은 전송 재시도/응답유실 | 최초 eventId/body 고정, 새 JWT 사용, commit 확인 후204 |
| 보존된 같은eventId + 다른body |409 EVENT_ID_CONFLICT. 이미 삭제됐어도 충돌을 숨기지 않음 |
| 다른eventId + 이미 삭제된 같은Session/같은group | 유효한 LC 인증/형식/환경/대상 연결 확인 뒤204 no-op |
| Session 없음, 정확한 삭제사실도 없음 | 성공추정 금지, 기존 unresolved 경로 |
| 다른group/환경 또는 불일치 | 거절. 단순 sessionId 일치만으로204 금지 |

새 요청의 eventId/deletionOperationId/deletedAt/userId가 최초 삭제와 다르다는 이유만으로 DELETION_FACT_CONFLICT를 반환하지 않는다. LC가 해당 새 요청의 사용자 권한을 검증해야 한다. Billing은 최초 삭제 시각/사실/영향을 덮어쓰지 않고 현재group·새guard를 다시 차단하지 않는다. 삭제 금전역분개/무료권 복원/Session 재생성은 없다.

LC의 원문 삭제 전 불변전달자료 영속은 유지한다. 기존 target에 자기 요청의 자료와 전달 상태를 보존하고 **다른 삭제 operation의 최초 fact 참조/재사용 구조 및 이를 위한 LC 전역 Session unique 요구는 제거**한다. 각 전송을 중복 처리하지 않는 저장장치는 유지한다.

Billing의 최초Session deny와 event 멱등성은 구분한다. 다른event의 no-op도 처리한 eventId/body의 충돌을 보존기간 내 검사할 수 있어야 한다. 기존 단일fact 저장모델에서 이를 어떻게 최소 저장할지는 exact schema에서 확정한다. 무한 event 배열/최초event 연결망을 만들거나 새 event receipt 비용이 전혀 없다고 설명하지 않는다. 추가 요청 전체를 금융 원장에 기록할 의무는 없다. 저장/경합 fixture 확정 전 구현 완료로 표시하지 않는다.

## 3. 사용자 인가의 LC 단일 책임

LC는 사용자 토큰·현재 Session 소유권·merge/탈퇴/삭제 접수 경합을 검증한 뒤 자신이 관리하는 exact Session의 삭제 사실만 전송한다. 임의 클라이언트 userId/sessionId를 검증 없이 중계하지 않는다. 게스트→회원 전환은 기존 LC 사용자/Session 귀속을 기준으로 처리하며 Billing에 같은 이전을 다시 증명하기 위한 전용 근거를 만들지 않는다.

Billing은 다음을 유지한다.

- 전용 workload JWT/issuer/subject/audience/환경/connector 및 strict body 검증.
- sessionId→attemptGroupId의 실제 연결 확인. retained 삭제fact가 이 연결을 증명하면 이미삭제 no-op 가능.
- 현재group 재응시/복원 차단과 guard 변경은 **현재 귀속·activeSession·대상 source가 일치하는 경우만** 허용. 읽기만 한 current owner가 경합 중 변경되지 않도록 기존 Tx/CAS fence 사용.
- 과거Session 또는 현재owner가 다른 경우에는 exactSession deny만 적용하고 target의 group/guard/권리 보존.
- 현재group 영향에 필요한 근거가 불명인 대상은 임의 성공/차단하지 않고 unresolved로 둔다. Billing의 과거 merge 증거가 없다는 이유만으로 LC의 사용자 인가를 다시 거절하지는 않는다.

creationOwner 등 이미 구현된 내부 자료를 삭제하거나 다른 기능의 소유 검사까지 완화하지 않는다. BillingSubjectLink의 guestMergeEvidence 신규저장, 삭제를 위한 Identity merge이력 추가, 과거 전환 사용자 backfill은 이번 범위에서 제외한다. paid 권리 이전·일반 회원간/연속병합 지원을 새로 승인한 것이 아니다.

**감수하는 위험:** LC 인가 버그/침해가 유효한 잘못된 삭제요청으로 이어지면 Billing은 사용자의 삭제 권한을 독립적으로 재판정하지 못한다. 인증과 대상검사는 이 위험을 완전히 제거하지 않는다. 반면 Billing의 변경영향 검사로 다른 현재group/권리를 무조건 삭제하는 것은 막는다.

## 4. 유지하는 처리·보존 경계

- 원문제거 전 최소전달자료 영속, 네트워크 I/O는 Tx 밖, LC 로컬 성공기록은 원전송과 결속한다.
- 필요한 삭제 POST의 empty204 성공응답 전 신규시험 차단 방향 유지. 이는 별도 ACK API/원격checkpoint 신설이 아니다.
- 원문 정리는 영속준비 이후 Billing 장애와 독립 진행. 전달검토가 안전한 원문정리를 중단시키지 않는다.
- 미전달자료를 기존30일 TTL로 삭제하지 않으며, 전달 완료/삭제deny/멱등자료별 최종 purge는 별도다. 무기한 보존 승인 아님.

## 5. LC에 확인할 사항·다음 단계

기존 R1 D1~D6 중 D1/D3은 이 문서로 대체한다. D2/D4/D5/D6의 시각·원문/성공응답 분리·인증은 유지하되 D6의 다른event409 및 Billing독립소유검사 인용은 제외한다.

1. **S1:** 사용자 삭제 인가를 LC에서 완결하고 인증된 정확한 Session 삭제만 전송할 수 있는가.
2. **S2:** 새 삭제요청은 새event 가능, 같은전송 retry는 원event 유지로 구현할 수 있는가.
3. **S3:** 원문정리와 성공응답 대기 분리, 필요한204 전 새시험 차단은 기존 방향대로 연결 가능한가.
4. **S4:** Billing 현재group/guard 영향 판정과 반복삭제 no-op/event충돌의 exact 저장·경합 사례를 함께 확인한다.

사용자 방향은 확정됐으나 LC 동의/fixture/schema는 아직 확정되지 않았다. 위 회신 뒤 저장·오류표와 fixture를 새 revision으로 맞추고 각 서버 구현을 진행한다. Store 환불 자료는 선행조건이 아니다.

2026-10-16 회신 반영: **LC S1~S4 방향 수락 완료, exact 규격은 미동결**이다. 다음 공동명세는 (1) 최초Session deny와 event별 최소멱등증거 분리, (2) 전용인증/7필드/오류별retry·격리, (3) LC요청별 불변payload/전체필요204·원문정리후복구, (4) 미전달30일TTL 제외 및 목적별보존이다. 이미삭제no-op event도 같은ID/다른body 충돌을 검사해야 한다. 새 제품정책 재선택이나 원문정리 중단을 요구하지 않는다.

## 6. 검증 기준 변경과 범위

W10은 다른event 같은삭제대상의204/no-op, W11은 operation간 원event재사용이 아니라 같은전송 불변/새요청 독립 검증으로 변경한다. O03/O08/O09는 LC 사용자 인가 및 Billing대상/현재권리 보호로 분담한다. Billing merge증거 소실만으로 삭제거절을 기대하지 않는다. 이외 환경/위조/상충group/unknown거절, 중복·삭제/merge/phone/환불 경합과 새guard 불변은 유지한다.

이번은 사용자 승인 문서 반영만이며 코드·타저장소·실DB·외부메시지·Jira·배포 변경 없음. 새 테스트 실행 없음, 기존 결과를 새 정책 통과로 재사용하지 않는다.
