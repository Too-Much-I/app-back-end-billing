# TMI-217 삭제 전달·단일 이전 증거·ACK 보완안 R1

> **대체 안내(2026-10-16):** 사용자가 단순화1·2를 승인했다. [R2](TMI-217-DELETION-SIMPLIFIED-R2.md)가 §2.1의 operation간 최초fact 참조/LC전역Session unique 요구, §2.2의 양측독립소유검사/guestMergeEvidence 및 D1/D3을 대체한다. 이 부분은 미채택 과거제안으로 읽는다. 같은전송 불변·원문정리/204 분리·다른현재권리 보호는 유지한다. 새field/schema 구현이나 LC 수락 완료 아님.

- 작성: 2026-10-16 / Billing `codex/TMI-221-refund-operations`
- 상태: **LC 항목별 수락 요청용 제안**. 사용자 문서 구체화 요청에 따른 작성이며 공동 동결·제품 구현·활성화 완료가 아니다.
- 기준: [draft-3](LC-BILLING-EXAM-DELETION-RETENTION-R1.md). 기존 7필드 wire·인증·오류·정책은 그대로 사용하고 아래 내부 저장/처리 접점을 구체화한다. 새 fixture는 수락 뒤 작성한다.

## 1. 5줄 결론

1. LC 기존 삭제 target에 불변 전송 사실과 ACK 상태를 추가한다. 원문 삭제 전에 영속하며 재시도 때 새 eventId/body를 만들지 않는다.
2. Billing은 기존 trial subject에 단일 Guest→Member 이전 증거를 함께 기록하는 안을 제안한다. 모든 Session을 순회하는 범용 owner 이력은 만들지 않는다.
3. 원문 정리와 Billing 전달은 독립 진행한다. 필요한 모든 ACK와 기존 안전조건을 확인한 때만 새 시험 생성을 허용한다.
4. 기존 LC의 준비 완료 즉시 생성 허용 및 완료30일 TTL은 이 전달 증거에 그대로 적용하면 안 된다.
5. 실제 금전 provider 자료와 독립적으로 구현 가능하며 최종 물리 purge는 별도다. 미확정 보존을 무기한 보관 승인으로 해석하지 않는다.

## 2. 반드시 읽어야 하는 동작

### 2.1 최초 전송 사실 — 기존 LC target 재사용 제안

`learning_record_deletion_targets`의 EXAM target에 다음 내부 정보를 보존한다. collection/필드 명칭은 LC 수락 대상이며 외부 body에 추가하지 않는다.

| 정보 | 제안 규칙 |
| --- | --- |
| billing classification | LINKED / UNLINKED / UNRESOLVED. 미연결은 명시적인 기존 자료 검증으로만 판정. 일부 Billing metadata 누락을 UNLINKED로 취급하지 않음 |
| billingFact | draft-3의 eventId/schemaVersion/userId/deletionOperationId/attemptGroupId/sessionId/deletedAt 7필드 및 신뢰 environment, canonical digest |
| eventId | 첫 fact 저장 시 UUIDv4 한 번 생성. DB unknown commit이면 먼저 기존 fact 확인. 새 키로 우회하지 않음 |
| deletedAt | LC 삭제 접수의 접근 차단이 commit된 원 시각. 현재 operation.requestedAt이 그 경계인지 LC가 확인하고 UTC millisecond로 고정. callback 봉인/재시도 시각을 대신 넣지 않음 |
| 전달 결과 | PENDING / ACKED / NEEDS_REVIEW, ackedAt nullable. 실행 lease/재시도는 기존 체계 재사용; 전달 재시도와 원문 정리 실패를 서로 소진시키지 않음 |
| 최소 조회키 | environment + sessionId로 원 fact를 찾을 수 있어야 함. 원문 Session/삭제 command/30일 operation TTL에 의존하지 않음 |

LINKED target의 최초 fact 저장과 대상 봉인/원문 Session 제거는 같은 LC Transaction에 참여한다. 외부 HTTP는 Tx 밖이다. fact 저장 실패/불명인 대상은 최소 사실이 확인될 때까지 그 대상의 원본 제거를 진행하지 않는다. **이미 fact가 영속된 뒤의 Billing 장애는 원문 정리를 막지 않는다.** 다른 안전하게 준비된 target까지 불필요하게 멈추지 않는다.

권장 인덱스 의미는 `(billingEnvironment, aggregateId)` unique, `targetType=EXAM` 및 billingFact 존재에 한정한 partial이다. 실제 이름/필터·구자료 충돌 inventory는 LC schema 명세에서 동결한다. startup 자동 DDL을 요구하지 않는다. 기존 target `_id=deletionId:type:aggregateId`만으로는 다른 삭제 operation의 동일 Session 중복을 방지하지 못한다.

새 삭제 요청에서 이미 fact가 있는 Session을 만나면 **최초 fact를 참조**한다. 이전 deletionOperationId/deletedAt/userId를 새 요청으로 덮어쓰지 않는다. 새 target은 원 fact 참조와 자기 cleanup 정보만 가질 수 있고 두 번째 billingFact를 만들지 않는다. 새 요청자의 권한은 별도로 검증하며 다른 actor/내용의 모순을 기존 사실로 덮어 성공시키지 않는다. 원 fact가 소실됐다면 새 eventId를 재전송으로 위장하지 않고 운영 확인한다.

Billing은 기존 draft-3대로 commit 확인 후 exact empty204만 성공 처리한다. 409는 새 eventId로 재시도하지 않으며, 인증/계약 오류는 기존 격리·명시 재개 대상이다. transport 재시도는 같은 body/eventId, 새 JWT/jti를 사용한다. 기존 24시간/200회 자동 재시도 상한은 유지하며120일로 늘리지 않는다.

### 2.2 단일 Guest→Member 증거 — Billing subject 단위 제안

현재 creationOwner는 최초 생성자이며 현재 소유자 증명 전체가 아니다. 현재 `SubjectOwnerRebind`는 ACTIVE Session 중심의 짧은 fence이고 target user도 직접 보존하지 않아 장기 삭제 인가의 단독 근거로 사용하지 않는다.

권장안은 기존 BillingSubjectLink에 선택적 불변 `guestMergeEvidence` 하나를 두는 것이다. 최소 내용: 신뢰 environment, 인증된 Identity eventId/payloadDigest, sourceUserId, targetUserId, 당시 sessionOwnerEpoch, appliedAt. subjectRefId는 해당 link의 기존 키로 결속한다. 이는 **그 subject의 게스트→회원 이전 한 번**만 증명하며 여러 epoch의 일반 owner 이력 배열이 아니다.

- 인증된 USER_MERGED의 실제 source→target owner CAS와 같은 Transaction에서 기록한다. 동일 사건 재전송은 동일 근거 유지, 다른 사건/다른 target으로 기존 증거를 덮어쓰지 않는다. 충돌은 기존 충돌/운영 확인 경로로 처리한다.
- NOOP/STALE/CONFLICT나 현재 target 상태만 보고 과거 증거를 합성하지 않는다. 기존 inbox digest만 남고 원 source/target/subject 연결이 소실됐다면 자동 backfill하지 않는다.
- USER_MERGED는 epoch를 유지한다. phone 재가입은 epoch를 증가시키고 이 증거를 생성/덮어쓰기하지 않는다. MEMBER paid/복원권을 trial merge로 이전하지 않는다.
- exact Session.creationOwner의 sourceUserId·TRIAL_SUBJECT reference·epoch·environment가 증거와 일치해야만 이전 대상 B의 권한을 인정한다. 현재 subject owner/epoch만 같다는 이유로 다른 Session을 포함하지 않는다.
- LC는 기존 UserMerged의 ExamSession.userId 변경과 같은 Transaction에서 자기 exact Session의 단일 이전 근거를 보존하는 안을 검토한다. LC의 현재 actor 검사와 Billing의 증거 검사는 각자 수행하며 새로운 body 필드를 요구하지 않는다.

| 삭제 actor/상황 | Billing 인가·영향 제안 |
| --- | --- |
| creationOwner와 actor 일치, 검증된 merge 없음 | 기존 draft-3 소유 확인. 현재 자기 Session/group면 해당 group 차단, 오래된 Session이면 Session만 |
| 검증된 Guest A→Member B, B가 삭제 | 위 exact 증거 및 LC 현재 actor 확인을 전제로 B 인정. 현재 link도 B/같은epoch이면 기존 현재 group 규칙 적용 |
| 검증된 merge 뒤 A가 삭제 주장 | 생성자라는 이유만으로 A를 허용하지 않음. LC의 원 삭제 fact 선행 여부/경합이 불명하면 owner conflict 및 운영 확인. UserMerged는 phone 재가입과 다름 |
| B가 phone 재가입 대상 C로 권리를 넘긴 뒤 B의 원 Session 삭제 | 단일merge 근거와 원 epoch가 유지돼 있으면 B의 과거 Session만 대상. C의 group/guard/권리 불변. 현재owner 불일치를 target 권리 차단 근거로 쓰지 않음 |
| 근거 누락/상충/두 번째 merge | 자동 허용/현재owner backfill 없음. 기존 owner 오류와 명시 재확인 |

위 인가 표는 draft-3의 모호한 과거 owner 접점을 좁히는 **공동 수락 대상**이다. 생성자 일치가 무조건 삭제 허용이라는 구현은 하지 않는다. 삭제/merge 경합은 같은 subject/link 쓰기 fence로 순서화하고 미완료 guard를 임의 해제하지 않는다.

subject link 또는 증거가 현재 purge 대상이 되는 경우 참조 Session의 삭제 인가가 아직 필요하면 함께 지우면 안 된다. 이는 TrialClaim의 3년 개인정보 purge를 연장하라는 승인이 아니다. 기한 충돌 대상은 운영 활성화 전 최소 연결 분리/종료조건을 정해야 하며, 초기 코드에서 Claim/alias purge를 무조건 중단하거나 무기한 보존하지 않는다. 이 안의 coverage를 증명할 수 없으면 해당 legacy 인가를 성공으로 가정하지 않는다.

### 2.3 ACK와 원문 정리의 두 조건 분리

현재 `DeletionWorker.prepare()`는 모든 target 준비 후 `RestartEvidence(...billingResolved=true...)`로 `allowNewLearning()`을 호출한다. 새 전달에서는 상수 true를 제거하고 실제 영속 ACK 집계를 사용해야 한다.

1. 기존 owner fence에서 생성 중 삭제409 및 삭제 접근 차단을 유지한다.
2. inventory를 고정하고 target별 LINKED/UNLINKED/UNRESOLVED를 판정한다. LINKED는 원문 제거 전 fact를 저장한다.
3. **원문 정리 단계는 ACK 유무와 독립**적으로 진행한다. 단계 순서에 `SAFE_TO_START_LEARNING`이 있어 원문 worker까지 멈추지 않도록 LC의 기존 ordinal 전이를 분리한다. 새 범용 saga/queue는 요구하지 않는다.
4. 전달 worker는 저장한 fact만 전송한다. exact204 뒤 자기 lease/version 및 원 event/digest 일치를 확인해 ACKED를 commit한다. commit 불명은 같은 fact 재조회/재전송으로 수렴한다.
5. 새 시험 허용은 owner Transaction에서 아래 조건을 재조회한 뒤 `writeBlocked=false`와 safeToStartLearningAt을 한 번만 기록한다. HTTP 응답 메모리값만으로 허용하지 않는다.

필요 조건: inventory/digest 고정 및 target coverage 일치, 모든 callback fence·읽기 차단/기존 challenge 안전조건 충족, UNRESOLVED=0, 모든 LINKED의 **원 fact ACKED**, 진행 중 생성 없음, 탈퇴/다른 lifecycle deny 없음, 로컬 commit 결과 확정. UNLINKED는 검증된 분류가 있어야 ACK 제외 대상이다. 다른 삭제 operation의 기존 ACK를 임의 Session에 재사용하지 않는다.

ACK가 모두 와도 S3 삭제 완료까지 반드시 기다리게 하는 새 정책은 추가하지 않는다. 기존 안전 봉인/PUT capability/읽기 차단 조건을 충족하면 원문 잔여 정리와 새 시험은 기존 정책 안에서 병행 가능하다. 반대로 원문 정리가 끝나도 ACK 미완료면 새 시험 차단은 유지한다. 전체 operation 완료는 cleanup 완료와 필요한 ACK를 모두 요구한다.

기존 `NEEDS_REVIEW`가 전체 cleanup을 멈추는 구조도 분리 검토한다. 전달 인증/409/재시도 소진은 **전달의** 검토 상태이며 안전하게 준비된 원문 정리를 중단시키는 사유가 아니다. owner/참조/저장 증거 자체가 불명인 경우의 원문 삭제 안전 중단과 구분한다.

## 3. 보존·최종 purge의 경계

- 현재 LC operation.complete/finishRetention과 worker는30일 expiresAt을 설정한다. linked 원 fact·미ACK·참조 중인 최소 이전근거에는 이 TTL을 전파하지 않는다. ACK 후에도 재요청/deny 판정에 필요한 최소 사실은 별도 최종폐기 결정 전 자동 삭제하지 않는다.
- 일반 challenge/비연결 삭제의 기존30일 정책을 일괄 변경하지 않는다. 신규 linked 경로의 명시 schema/reader와 TTL 대상 inventory가 필요하다.
- draft-3의 전달 상세120일은 여전히 제안이다. 자동 final purge를 이번 구현에서 제외하고 운영 전 목적별 기간/최소항목/담당/종료조건을 확정한다. 금융5년을 삭제 원문/모든 메타데이터의 보존 근거로 사용하지 않는다.
- Billing `exam_deletion_facts`는 draft-3의 단일 사실 저장소를 유지한다. duplicate204에 필요한 최소 fact와 Session deny의 참조 수명은 함께 대조하며 ACK만으로 곧바로 지우지 않는다.

## 4. LC 회신 요청표

각 항목에 `수락 / 수정 필요(구체 위치·대안) / coverage 미확인`으로 회신한다. 아직 수락됐다고 기록하지 않는다.

| ID | 수락 요청 |
| --- | --- |
| D1 | 기존 target의 불변7필드 저장·동일Session 원 fact 재사용, unique partial 의미 및 기존30일 TTL 제외 |
| D2 | deletedAt의 실제 접근차단 commit 경계, unknown commit/재접수에도 원값 유지 |
| D3 | 단일 merge의 Billing subject 증거/LC exact Session 근거 및 §2.2 인가 표. 기존 자료 coverage/legacy 불명 분리 |
| D4 | cleanup과 ACK 분리, prepare의 billingResolved 상수 제거, 전체 LINKED ACK 후 원자적 새시험 허용 |
| D5 | 전달 NEEDS_REVIEW가 안전한 원문 정리를 막지 않음. 미ACK 상태의 자동TTL 금지·최종purge 별도 |
| D6 | draft-3 route/7필드/새audience/오류 그대로, 기본OFF·공동fixture 후 각 서버 구현 |

## 5. 후속 구현·검증

공동 회신 반영 → 새 revision/fixture → Billing 무료writer 선행 변경 통합 → Billing 삭제 수신/인가/INITIAL 분기 → LC fact/sender/ACK 연결 → 양측 삭제·merge·환불 경합 검증 순서다. LC 코드는 LC 작업에서 변경한다. 외부 발송/메시징이나 Jira 변경은 이번 작성에 포함하지 않는다.

추가 검증 초점: fact commit ACK 유실 후 event1개, 두 삭제요청의 같은Session fact1개, body불일치409, mixed LINKED/UNLINKED/UNRESOLVED의 ACK조건, ACK 일부/유실·원문완료에도 생성차단, 전달401에도 안전한 원문정리, 재시작 후 ACK 복구, 완료30일 뒤 최소fact 유지, 단일merge/phone양순서·과거source 권리범위, 삭제/환불/기접수완료의 동시Tx 및 새guard 불변. 기존48개 케이스와 매핑하되 새 실행 PASS로 표시하지 않는다.

## 6. 코드 근거·이번 범위

- LC DeletionTarget: 현재 billingFact/ACK 없음, `_id`는 deletionId/type/aggregateId, expiresAt 존재.
- LC DeletionOperation/DeletionWorker: ordinal stage·prepare의 billingResolved=true, safe checkpoint 선행 cleanup, complete/finishRetention 및 target30일 TTL.
- LC UserMergedTransactionService: 현재 ExamSession owner를 일괄 변경하므로 불변 exact 이전근거는 별도 보완 필요.
- Billing SessionCreationOwner/OwnerRebindService/SubjectOwnerRebind: 최초 owner와 subject CAS는 있으나 짧은 ACTIVE fence로 장기 이전증거를 대체할 수 없음.
- 2026-10-16 로컬 코드 정적 대조. 제품코드/공유wire/fixture/타저장소/DB/배포 변경 및 테스트 실행 없음. 기존 dirty 변경 보존. 이번 내부 구조는 구현 완료 사실이 아닌 제안이며 LC 공동수락 전 사용하지 않는다.
