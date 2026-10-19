# TMI-217 삭제 전달·204·LC 저장 검증 절차

> 2026-10-19 사용자 결정: 앱에서 시험 삭제를 현재 제공하지 않으므로 실제 삭제→Billing204→LC 확인 저장 검증은 기능 제공 전으로 유예한다. 기존 코드·설정은 유지하며 아래 절차를 통과했다고 간주하지 않는다. 결제·이용권 조회·시험 생성 검증과 구분한다.

> 2026-10-18 사용자 후속 결정: 실제 삭제 전달 검증은 활성화 전 필수 절차가 아니라 배포 후 프론트 검증과 backend readback으로 수행한다. 아래 정상204/Billing fact·receipt/LC ACKED 확인 기준은 그대로 유지하되 별도 운영 전용 검증도구 구현을 활성화의 선행조건으로 두지 않는다. 미실행 검증을 성공으로 기록하거나 LC gate를 거짓 true로 설정하지 않는다. [적용 기록](../runbooks/billing-prod-business-activation-2026-10-18.md).

## 1. 5줄 결론

1. 검증 단위는 삭제가 승인된 exact Session 1건과 최초 eventId/body 1쌍이다.
2. LC 영속 요청 → 실제 JWT/TLS 전송 → Billing commit → empty204 → LC 영속 ACKED 순서로 확인한다.
3. HTTP204만으로 통과하지 않는다. Billing fact/receipt와 LC ACKED를 재조회해야 한다.
4. 운영은 정상 전달·동일 요청 중복 확인만 수행하고, 응답유실/경합/잘못된 응답 강제 주입은 격리 환경에서 검증한다.
5. Billing 측 실행 기준을 이 절차로 정리했다. LC 수락·한정 도구 구현·실행 및 공동 통과는 아직 아니다. 이번 작업은 문서만이다.

## 2. 사용자가 반드시 읽어야 하는 내용

실행하면 지정 Session에 실제 삭제 사실이 남는다. 임의 운영 사용자/시험을 고르거나 DB에 가짜 구매·소유 증거를 넣지 않는다. 기존 시험 원문 삭제·환불·계정 삭제·전체 업무 활성화는 이 절차의 승인에 포함하지 않는다. Billing의 삭제 사실 기록과 LC 원문 물리 정리는 구분한다.

현재 LC sender의 일반 tick()은 전체 대기열에서 한 건을 선택한다. 운영 사전검증에는 그대로 사용하지 않는다. LC가 **승인 target/event만 처리하고 전체 scheduler·원문 정리 worker를 시작하지 않는 일회성 실행 수단**을 준비한다. 기존 client/sender의 digest·lease/version·owner 검사와 ACK 저장 로직을 재사용해야 하며, 검증을 위해 billingDeliveryVerified를 먼저 true로 넣지 않는다. 구현 방법은 LC가 확인하며 이 문서로 타 저장소 수정 권한을 부여하지 않는다.

## 3. 실행 전 준비·역할

| 담당 | 실행 전에 확보할 것 |
| --- | --- |
| 사용자/운영 담당 | 삭제 허용 검증 Session과 계정, 영향 범위, 실행 시간 승인. 개인 식별자는 접근 제한된 기록에서만 관리 |
| LC | 정상 경로로 생성한 양측 귀속 Session, 정상 삭제 접수 경로의 prepared target 및 영속 billingDelivery, 한정 실행 도구, 전달 index/미전달 TTL 보호, owner/writer 준비 |
| Billing | exact Session/group/source/creationOwner 근거, schema 검증, 삭제 업무 수신 준비 및 ACCOUNT writer에 필요한 OPEN의 별도 승인·적용 |
| 양측 | production scope/목적 JWT/key/TLS 확인, 정확 POST route 허용, 일반 신규 요청/다른 worker의 유입 제한, 중단 시 담당자 |

대상이 없으면 정상 생성 경로로 검증 자료를 준비한 뒤 진행한다. 임의 sessionId로 호출해204를 얻는 방식은 불가다. 운영 일회성 수단이 없으면 격리 리허설까지만 진행하고 운영 증거는 미확보로 둔다.

## 4. 고정 실행 순서

### A. 전송 전 기준선

1. 환경·대상 Session/group·deletionOperationId·eventId·payloadDigest를 exact 대조한다. 요청7필드는 기존 계약 그대로다: eventId/schemaVersion/userId/deletionOperationId/attemptGroupId/sessionId/deletedAt.
2. LC `learning_record_deletion_targets`의 지정 target이 prepared=true, billingDelivery.state=PENDING이고 실제 저장된 body에서 digest가 재계산되는지 확인한다. 대기중 lease/nextAttemptAt/retryNotBefore를 우회하지 않는다.
3. 최초 적용 검증은 Billing `exam_deletion_facts`의 해당 Session과 `exam_deletion_receipts`의 해당 event가 모두 없는 대상을 사용한다. 이미 존재하면 최초 적용으로 보고하지 않고, 내용이 일치하는 경우 중복 검증으로 분리한다.
4. 비밀키·토큰·body 원문을 출력하지 않는다. 사전 자료와 결과의 동일 대상 연결은 접근 제한된 검증 기록에서만 한다.

### B. 실제 전송과 Billing commit

5. LC 한정 실행 수단으로 저장된 최초 body를 `POST https://billing-workload-prod.to-teacher.com/internal/v1/exam-deletions/events`에 전송한다. production 삭제 목적 JWT를 사용하며 TLS 검증/redirect 금지/기존5초 timeout을 유지한다. 인증 토큰은 재발급 가능하지만 eventId/body는 바꾸지 않는다.
6. Billing은 동일 Tx에서 receipt/fact 및 해당 Session deny·필요한 exact group/guard 처리를 commit한 뒤 body 없는204를 반환한다. 200/202 또는 프록시의 임의204는 성공 근거가 아니다.
7. LC client가 받아들인 응답을 확인한다: status204, body0바이트, Transfer-Encoding 없음, Content-Length 없거나 단일0. Content-Encoding은 없거나 단일identity. 모순된 framing은 ACK로 저장하지 않는다.

### C. LC 저장 후 독립 재조회

8. LC sender의 기존 lease token/version/digest/owner 조건을 통과한 Tx가 ACK를 저장해야 한다. DB를 수동 수정해 ACKED로 만들지 않는다.
9. 새 읽기에서 Billing exact receipt와 fact를 재조회한다. 최초 검증은 각각1건, eventId/sessionId/group 및 canonical payloadDigest 일치, 해당 Session deleted=true/completionAllowed=false를 확인한다. 현재 group이면 승인된 현재group/guard 처리, 과거 Session이면 다른 현재group/guard 불변을 확인한다. 검증 대상 유형에 맞는 한쪽만 기대값으로 사용한다.
10. 별도 새 읽기에서 LC 동일 target의 billingDelivery.state=ACKED, ackedAt 존재, nextAttemptAt/leaseToken/leaseUntil/failureCode 해제와 최초eventId/payloadDigest 불변을 확인한다. 메모리 객체나 HTTP 로그만으로 통과시키지 않는다.
11. 같은 deletion operation에 전달 대상이 여러 개면 모두 ACKED가 되기 전 checkpoint/새 생성 제한 해제를 완료로 보지 않는다. ACKED 한 건은 원문 정리 완료나 새 생성 최종 허용을 뜻하지 않는다. 다른 lifecycle/삭제 조건은 유지한다.

### D. 동일 전송 중복 확인

12. 같은 최초 eventId/body를 한 번 더 실제 client로 전송한다. ACKED target을 PENDING으로 되돌리거나 attempt를 초기화하지 않는다. 한정 도구의 재전송은 저장 body를 읽어 전송만 하는 경로여야 한다.
13. 다시 empty204, Billing fact/receipt 각각1건 유지·최초 fact/적용시각/guard 추가변경 없음, LC 기존 ACKED 유지 확인. 새 eventId를 만들어 중복 성공으로 보고하지 않는다.

## 5. 실패·응답유실 판정

| 관측 | 처리·합격 여부 |
| --- | --- |
| Billing commit 미확인 또는 timeout/503 | 실패로 확정하거나 ACKED를 만들지 않음. 기존 backoff/Retry-After/lease 규칙으로 같은event/body 재시도 |
| Billing commit 확인, LC ACKED 없음 | 전달 검증 미완료. 같은event/body 재시도로 duplicate204와 LC 저장 수렴 확인 |
| 401/403 | 인증 준비 실패. 현행 NEEDS_REVIEW 처리 유지, 인증 완화/다른 목적 토큰 금지 |
| 409 conflict/target unresolved | 동일성·귀속 근거 점검. 새로운 ID나 가짜 소유 근거로 우회 금지 |
| 404 또는 200/202/모순된204 | 경로·기능/응답 계약 확인. 성공 처리 금지 |
| 오래된 lease/version의 응답 | ACK 덮어쓰기 금지. 기존 CAS 결과와 최신 target을 다시 확인 |

응답유실·ACK 저장 실패·잘못된204·경합 강제 주입은 격리 테스트에서 수행한다. Billing commit 후 응답만 유실시켜 receipt/fact1건 및 LC PENDING→동일 요청 duplicate204→ACKED를 확인한다. 운영에서 이 장애를 일부러 만들거나 전체 네트워크를 차단하지 않는다. 운영에서 자연 발생하면 위 복구 규칙을 따른다.

## 6. 완료 기록과 인계

검증 기록은 실행 환경/양측 image revision·시각·대상 참조(접근 제한)/최초digest·HTTP 상태/빈 body 판정/Billing fact·receipt 건수/LC ACKED 재조회/중복 결과/미실행 항목을 포함한다. raw 요청·키·토큰·사용자 정보는 공유 문서에 남기지 않는다. fixture/독립 테스트는 버전과 실제 실행 결과로 식별하고 운영 E2E와 구분한다.

**운영 전달 통과 조건:** A~D의 실제 동일 대상 결과가 모두 일치한다. billingDeliveryVerified 기록은 LC가 결과를 검토한 뒤 담당 절차로 수행하며 이 문서는 값을 쓰지 않는다. storageVerified나 원문 purge 완료를 대신하지 않는다. 검증 후 receipt/fact/ACK 증거를 삭제하거나 Session을 부활시키지 않는다.

LC 전달 요지: 지정한 삭제1건으로 저장body 전송→Billing commit/empty204→LC ACKED 독립재조회→같은body 중복204를 확인합니다. 전체 tick 대신 target/event 한정 실행 수단이 필요합니다. 응답유실 강제 주입은 격리 환경에서만 검증합니다. 이 절차의 수락과 한정 실행 가능 여부를 회신해 주세요. 이번 문서 작성은 실행·배포·운영 자료 삭제 승인이 아닙니다.

### 정적 근거

- Billing DeletionController/DeletionService/DeletionRequestDecoder: commit 후204, exam_deletion_facts/exam_deletion_receipts, digest 및 exact Session 처리.
- LC BillingDeletionClient/BillingDeletionSender/BillingDeletionDelivery/DeletionWorker: empty204 판정, lease/version·owner 검사, ACKED 저장 및 전체 전달 대기.
- [통합 회신](Billing-LC-combined-cutover-readiness-response-2026-10-18.md), [업무 준비](../runbooks/billing-prod-business-readiness-2026-10-18.md).
- 이번 코드/DB/서버/타저장소/외부 메시지 변경 없음. 문서·링크/diff 정적검사만 수행, Gradle 미실행. 이전 테스트 결과를 이 절차의 실행 통과로 사용하지 않는다.
