# TMI-199 공통 계약 fixture·schema·Mongo 검증 결과

- 작성일: 2026-10-07 / 브랜치: develop / Jira: [TMI-199](https://to-teacher.atlassian.net/browse/TMI-199), 에픽 TMI-137
- 범위: Billing 저장소의 test-only 실현성 검증·공유 fixture·구현 규격 후보. production endpoint/schema initializer/Identity 코드는 변경하지 않았다.
- 기준: [승인 공동 계약](IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md), [009 기반 계약](PLAN-009-payment-foundation-technical-contract.md), [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md).

## 1. 5줄 결론

1. 두 서버가 재사용할 가짜 JSON fixture와 Java 계약 oracle, Mongo 실현성 테스트를 추가했다(§6.1).
2. 로컬 Mongo7.0.14에서 고정 atClusterTime/User/H, 원자 counter/journal, stale-generation write conflict와 additive index가 동작했다(§5.1).
3. 단일 멤버 stepdown/재선출과 오류 주입을 검증했지만 다중 노드 failover·실제 history 만료·운영 설정 검증은 아니다(§5.1).
4. account/ref 자체는 반복 변경하지 않고 별도 lifecycle fence와 activity cursor를 갱신하도록 ADR 우선 해석을 구체화했다(§5.3).
5. Identity 최초44건 및 후속42건 독립 검증 회신과 exact wire/index 수락을 확인했다. 기술 수락 보류는 해소됐으며 병합 확인·Jira 종료 승인과 후속 운영 gate는 별도다(§5.5, exact 규격 §8).

## 2. 사용자가 반드시 읽어야 하는 내용

이 작업은 결제 구현 전 준비다. 앱용 결제 API, 실제 탈퇴 publisher/consumer, RC adapter, 운영 index/삭제 작업은 추가하지 않았다. 테스트 oracle은 규칙 예시를 실행한 것이지 아직 없는 production Controller/worker가 검증됐다는 뜻이 아니다. 기존 무료 코드는 수정하지 않았다.

fixture JSON은 모두 가짜 값이다. 상대 저장소에는 자동 복사하지 않았다. 후속 Identity 회신 §7에서 같은 fixtureVersion1의 독립 실행 완료를 확인했다. 이는 production serializer/decoder·API 연동 완료와는 구별한다(§6.3).

## 3. 사용자가 결정해야 하는 사항

기존 승인된 원천120일·snapshot7일·token30분/15일 조건부 삭제를 다시 선택할 필요는 없다. 아래 exact DTO 후보·Identity 복구 route의 인증/envelope는 상대 구현 합의가 필요하다. snapshot precision 차이·Mongo 기능 미지원·이관 중단 등 계약 변경/추가 사용자 영향이 발견되면 별도 승인 후 바꾼다.

운영 Atlas/실제 credential을 읽거나 설정을 바꾸지 않았다. 실제 이관 시간과 운영 테스트 실행, Jira 완료 전환도 이번 작업에 포함하지 않는다.

## 4. 주요 위험과 미확인 사항

- **Instant 정밀도:** BSON Date는 밀리초뿐이다. Identity의 실제 User/outbox MappingMongoConverter 왕복에서는 동일 밀리초 시각이 확인됐다. 저장 전 고정밀 payload와 저장 후 snapshot을 혼합하면 digest가 달라진다. 현행 DB 조회 기반 publisher의 결함을 입증한 것은 아니다. 신규 journal/wire/snapshot은 동일 authoritative Instant를 사용하도록 구현 규격을 동결해야 하며, 과거 event를 반올림하거나 digest를 재생성하지 않는다. converter 검증은 실제 Mongo Transaction 검증이 아니다.
- 고정 snapshot은 로컬7.0.14에서 실제 동작했지만 운영 readConcern/driver/history window·5분 budget을 보장하지 않는다. 큰 사용자 목록의 처리량/transaction retry/counter 경합은 측정 필요다.
- 현재 Identity 전역 exception handler는 BaseResponse를 사용한다. 신규 복구 internal API의 body가 Billing InternalApiError와 같다고 가정하지 않는다. 신규 route 전용 오류 매핑·security handler까지 상대 합의가 필요하다.
- fixture에는 실제 서명 cursor/인증 검증이 없다. 권한·cursor 서명·모든 production DTO strict decoder는 I3/B1/B2 구현 테스트 대상이다.
- 미구매 provider coverage·과거 token·control 실제 누락 건수·운영 권한은 미검증이다. 테스트 통과만으로 삭제/판매 flag를 켜지 않는다.

## 5. 현재 작업과 직접 관련된 결과

### 5.1 검증 수준

| 항목 | 수행 방식 | 판단 한계 |
| --- | --- | --- |
| digest | Node crypto로 별도 산출한 고정 expected와 Java SHA-256 비교 | production serializer 아님 |
| strict v1/ACK/feed/204-only | JSON table과 test-only oracle 실행 | Identity/Billing 실제 controller 교차검증 필요 |
| snapshot/User/H | 첫 snapshot 응답 T를 다음 독립 readConcern snapshot 요청에 명시, 이후 commit과 대조 | 5분 장기/운영 기능 검증 아님 |
| sequence 원자성 | snapshot/majority+j Transaction abort·경합, counter/journal/User 확인 | 장시간 부하·다중 writer 배포 검증 아님 |
| old generation | old snapshot 뒤 새 generation update, old CAS 쓰기 충돌 확인 | 실제 worker/ACK outbox 구현 검증 아님 |
| UnknownTransactionCommitResult | disposable Mongo failCommand로 commit 오류 주입, 동일 transaction retry | 성공 commit 응답이 네트워크에서 유실된 상황 전체 검증 아님 |
| SnapshotTooOld | failCommand code286를 오류로 관측 | 실제 history eviction 발생을 기다린 테스트 아님 |
| 재선출 | 단일 멤버 force stepdown 후 primary 복귀/majority 자료 유지 | 다중 멤버 replica failover/durable rollback 아님 |
| index |16개 candidate index를 두 번 생성·key order/unique/partial/TTL 검증, active ref unique 충돌 | 운영 initializer/migration 실행 아님 |
| account/activity | 별도 cursor 갱신과 lifecycle write, stable account version/ref 유지 | 실제 service 구현 검증 아님 |

### 5.2 fixture DTO·오류 매핑 후보

후속 상세 명세는 [exact wire·index 수락안](TMI-199-EXACT-WIRE-AND-INDEX-SPEC.md)을 따른다. 기존 fixture4종을 변경하지 않고 numeric-boundaries/identity-indexes를 추가했다. 새 nested DTO·상태별 필드·물리 manifest의 Identity 수락은 별도이며 아래 최초 후보 설명의 미정 필드를 해당 수락안에서 구체화한다.

`wire-examples.json`은 **최종 wire 동결 후보**다. 기존 UserWithdrawn4필드는 변경 없다. 복구 header는 X-Consumer-Recovery-Generation, ACK는 contentDigest, sequence/H/through/count/version은 비음수 canonical decimal string을 후보로 제시한다. operation schemaVersion만 JSON integer1이다. T는 ISO 시간이 아닌 BSON timestamp의 `{seconds,increment}` unsigned32 decimal string pair이며 Java Instant로 변환하지 않는다. 미생성 expectedCheckpointVersion은null, nullable field와 생략 가능 필드는 DTO 동결 시 목록으로 고정한다. 현재 예제는 필수 nullable를 명시한다. itemCount/pageCount의 문자열 선택은 기존 공동 문서의 미정 부분을 채우는 후보로 상대 수락 전 production 구현하지 않는다.

| 경계 | 확정/후속 |
| --- | --- |
| Identity→Billing withdrawal | body 없는204, Billing 기존 InternalApiError(code,message,retryable,correlationId) 확장 후보.400/422/409/503 기존 계약 유지 |
| Billing public | PublicResponse 유지. products read, purchase-account purchase+MEMBER, no-store 및 기존9장 오류 표 |
| Billing→Identity recovery | direct success DTO 유지, internal 오류 envelope는 Identity 신규 전용 mapping 제안; 기존 public BaseResponse를 자동 변경하지 않음 |
| auth/IAM | 기존 Billing Lattice route allowlist 추가와 Identity 복구 ingress/호출 role는 별도 배포 입력. 실 ARN/host 없이는 검증 완료 불가 |

API별 all-fields strict validation·body16KiB·unknown field·UUID casing·timestamp 미래 skew는 production decoder에서 추가 검증한다. 이번 oracle이 모든 decoder 조건을 대체하지 않는다.

### 5.3 반복 account 호출과 activity cursor 정합성

ADR-004 §5.10을 우선하며 account/ref 안정성과 **모든 collection 무변경**을 구별한다.

1. 최초 생성 Transaction에서 lifecycle ACTIVE fence 쓰기 → account/ref 생성 → `payment_reconcile_cursors(accountId,kind=ACCOUNT_LOOKUP)` 생성.
2. 반복 호출은 lifecycle state/version CAS 쓰기 → 기존 account/ref 연결 검증 → 별도 cursor `lastActivityAt=max(기존,now)` 갱신 → 같은 ref 반환. account의 version/activeRefId/createdAt은 변경하지 않는다.
3. cursor의 worker lease/version/provider cursor를 통째로 덮어쓰지 않는다. activity 갱신이 pending backoff/notBefore를 앞당기거나 이관 중 worker를 살리지 않게 한다. 009에는 cursor만 준비하고 실제 RC worker는010에서 OFF 기반 구현한다.
4. cursor 누락은503+정합성 확인이며 reader가 새 ref로 복구하지 않는다. legacy cursor backfill이 필요하면 별도 승인된 migration. 생성 rollback·응답유실·unique race에서 account/ref/cursor 함께 수렴.
5. lifecycle/limiter/cursor 쓰기는 정합성·보안 목적이다. Claim/Grant/ledger/사용권은 쓰지 않는다. 탈퇴·purge와 같은 lifecycle 쓰기 경계를 공유한다.

이는 승인 ADR과 공동 계약의 별도 cursor안 구체화이며 신규 무료/유료 지급 정책이 아니다.

### 5.4 schema subset·Identity migration 준비

Billing `billing-indexes.json`의16개 index는 foundation+withdrawal candidate manifest다. core schema4를5로 올리지 않고 별도 subset 완료 상태를 둔다. index key order/name/unique/partial/TTL 불일치는 fail-fast; 운영 drop/recreate 금지. account/ref/lifecycle/cursor에는 business TTL 없음, inbox120일 TTL과 limiter2분 cleanup만 별도다. 기존9개 foundation index에 lifecycle2종/inbox/cleanup/cursor를 합쳤으며 자세한 실제 key는 fixture가 기준이다.

Identity 소유 후보 manifest(이 저장소에서 운영 생성 안 함):

| collection | index 후보 | key 순서/option |
| --- | --- | --- |
| withdrawal_stream | ux_withdrawal_stream | environment,streamId unique |
| withdrawal_stream | ux_withdrawal_active_stream | environment unique, partial {status:ACTIVE} |
| withdrawal_journal | ux_withdrawal_sequence / ux_withdrawal_event | streamId,sequence unique / eventId unique |
| withdrawal_journal | ix_withdrawal_capture_expiry | capturedAt,_id; TTL 없음 |
| withdrawal_deliveries | ux_withdrawal_destination / ix_withdrawal_due | eventId,destination unique / destination,status,nextAttemptAt,_id |
| withdrawal_snapshot_runs | ux_withdrawal_snapshot_operation / ux_withdrawal_snapshot | environment,consumer,consumerRecoveryGeneration,requestOperationId unique / snapshotId unique |
| withdrawal_snapshot_rows | ux_withdrawal_snapshot_user | snapshotId,userId unique |
| withdrawal_consumer_checkpoints | ux_withdrawal_consumer_checkpoint | streamId,consumer,consumerRecoveryGeneration unique |
| withdrawal_consumer_recovery_controls | ux_withdrawal_recovery_control | environment,streamId,consumer unique |
| withdrawal_recovery_operations | ux_withdrawal_recovery_operation | environment,consumer,streamId,operationId unique |

UserSessionControl은 기존 user_session_controls/@Version/sessionEpoch/revision/activeLogoutId/markingRequired/Firebase revocation 필드를 보존한다. 새 lifecycle state/type 복제가 필요하면 User와 drift 방지 Transaction을 함께 설계한다. 측정 계획은 다음과 같다.

- 읽기 진단으로 상태/type별 control 누락·불일치·진행 logout 집계. 개인 ID를 로그/이슈로 내보내지 않는다.
- fake/테스트 DB에서 bounded backfill과 동시 withdraw/upgrade 충돌·재시작 검증.
- 처리량/가장 오래된 transaction/old writer drain을 측정해 barrier 예상·최대 허용 시간을 보고. 시간 초과 시 rollout 중단/호환 writer 유지 계획 포함.
- 운영 진단·이관은 승인된 담당자가 별도 실행. 여기서 실제 누락률0이나 운영 중단 시간을 추정해 확정하지 않는다.

### 5.5 남은 gate와 작업 이동 기준

2026-10-07 최신: [exact 규격 §8](TMI-199-EXACT-WIRE-AND-INDEX-SPEC.md)의 Identity 수락/추가42건 독립 실행 회신으로 아래 기술 동결·회신 대기는 해소됐다. 후속 검증의 담당 작업/통과 기준은 공동 계약 §4에 명시했다. 관련 변경 병합은 아직 확인하지 않았으며 Jira 종료·production ready로 자동 전환하지 않는다. 아래는 최초 미완료 항목의 추적 기록이다.

TMI-199 전체 종료 전: 동일 fixture hash·Identity 독립 실행 회신은 충족됐다(§6.3). 신규 journal의 authoritative 시각, exact T·count·null·숫자 범위/영속 sequence 타입, 오류 envelope·인증 경계 및 Identity 후보 index를 기술적으로 동결해야 한다. 다중 노드 failover/실제 history 만료·실제 성공 commit 응답 유실은 로컬 추가 실험 또는 승인된 staging 검증 계획으로 담당·환경·완료 기준을 명시적으로 배정해야 한다. 미검증을 통과로 바꾸지 않는다.

B1/I1/I2는 이미 검증된 규칙으로 구현 상세를 준비할 수 있지만 미동결 wire/보존/이관 가정을 임의 채우지 않는다. 이번 테스트 코드가 생산용 코드로 자동 승격되지는 않는다. Jira 상태는 변경하지 않았다.

## 6. 부록 — 파일·실행

### 구현 전 기술 동결 권장안 — 2026-10-07 사용자 승인

아래 권장안은 사용자 승인으로 채택됐다. 공동 계약 §4의 후속 기술 선택 승인에 동일 기준과 작업별 검증 통과 기준을 반영했다. 아래 '권장/제안'은 선택 근거를 설명하는 표현이며 사용자 재승인 대기를 뜻하지 않는다. 다만 후속 exact 규격의 Identity 수락·nullable 전체 목록·index 검증과 실제 담당자/환경 배정은 별도이고 Jira 완료·배포 승인은 아니다.

- 시각: 신규 탈퇴 Transaction에서 withdrawnAt을 한 번 밀리초로 절삭해 User/outbox/journal에 같은 값으로 저장한다. snapshot은 영속 User 값을 사용한다. 재전송에서 시각을 만들지 않고 기존 event/digest는 변경하지 않는다. 기존 자료 backfill은 저장된 값을 복사한다. capturedAt은 별도 capture 시도 관측시각, T는 Mongo 논리 timestamp로 구분하며 기존 canonical decoder의 나노초 지원은 유지한다.
- DTO: 현재 fixture를 기준으로 필수 nullable와 생략 허용 목록을 endpoint별로 동결한다. sequence/H/count/version은 wire canonical decimal string, 영속 값은 BSON int64/Java long(0..Long.MAX_VALUE, event sequence는1부터), overflow는 오류로 처리한다. T의 seconds/increment만 unsigned32 decimal string pair이며 DB에서는 BSON Timestamp다. schemaVersion은 JSON integer1, boolean은 JSON boolean을 유지한다. count/pageCount 및 version의 추가 최소값은 각 상태 규칙을 따른다.
- 오류/인증: 새 internal route만 direct DTO 및 code/message/retryable/correlationId 오류 형식을 통일하는 안을 권장한다. 기존 public BaseResponse·LC JWT는 변경하지 않는다.400/401/403/409/410/422/503의 기존 사유를 구분하며 snapshot BUILDING202 등 route별 정상 응답과 withdrawal push204-only ACK를 혼용하지 않는다. 양방향 새 Billing 전용 통신은 Lattice AWS_IAM+SigV4 권장, Identity ingress 준비와 배포 승인은 별도다. 일반 Billing task는 데이터 복구 route, 새 recovery generation 발급은 별도 운영 role로 분리하며 direct 접근을 차단한다. Lattice 자체 오류는 앱 envelope를 보장하지 않으므로 client는 status-first로 처리한다.
- index: Billing16개와 Identity 후보 표를 additive manifest로 구체화하고 versioned initializer에서 key order/name/unique/partial/TTL을 검증한다. 이름·타입·중복 진단을 완료하기 전 운영 적용하지 않는다. 자동 drop/recreate 및 business state TTL 삭제는 금지한다.
- 검증 배정 제안: TMI-202 Identity capture에서 다중노드 failover·원자 journal·성공 commit 응답 유실을, TMI-203 Identity recovery에서 실제 history 만료·고정 T 실패 폐쇄를, TMI-204 Billing에서 복원/old-worker fencing·ACK 유실을 로컬 통합 검증한다. TMI-200은 control 이관·발급/탈퇴 경합을 검증한다. TMI-206 공동 staging에서 운영 동등 Mongo/권한/5분 snapshot·복원·barrier를 최종 확인한다. 실제 담당자 이름과 환경 실행 권한은 별도 배정한다.
- C0 종료 권장 기준: 양측 exact 규격 수락과 각 잔여 검증의 담당 작업·통과 기준 기록 후 foundation 완료 판단. 이는 Jira 범위 조정/완료 승인이 아니며, 미검증은 후속 이슈로 명시하고 관련 기능 활성화 gate로 유지한다. 모든 운영 실험 완료까지 개발 전체를 기다리게 하지는 않되 판매·자동삭제를 검증보다 먼저 켜지 않는다.

### 6.1 공유 파일

- [snapshot-digest.json](../../src/test/resources/contracts/payment-lifecycle/v1/snapshot-digest.json): 빈 목록·0/3/6/9 소수초의 고정 digest.
- [protocol-cases.json](../../src/test/resources/contracts/payment-lifecycle/v1/protocol-cases.json): v1 strict decode·baseline/generation·고정 feed·HTTP ACK 사례.
- [wire-examples.json](../../src/test/resources/contracts/payment-lifecycle/v1/wire-examples.json): 가짜 manifest/ACK/header/error, 실제 cursor 서명 아님.
- [billing-indexes.json](../../src/test/resources/contracts/payment-lifecycle/v1/billing-indexes.json): test-only additive candidate16개.
- [계약 oracle](../../src/test/java/web/tosunsaeng/billing/contract/PaymentLifecycleContractFixtureTest.java), [Mongo 실험](../../src/test/java/web/tosunsaeng/billing/contract/PaymentLifecycleMongoFeasibilityTest.java).

### 6.2 실행과 결과

`./gradlew test --tests 'web.tosunsaeng.billing.contract.*'`로 분리 실행한다. Docker 없이 skip하지 않고 실패하도록 했다. 컨테이너는 로컬 disposable mongo:7.0.14, 테스트마다 random DB를 만들고 해당 DB만 정리한다. failpoint는 테스트 컨테이너에서만 활성화한다. production dependency나 build 설정은 추가하지 않았다.

최종 `./gradlew clean test` 결과:285 tests, failures0, errors0, skipped0. 신규 계약 oracle39개+Mongo 실험10개 포함. 최초 sandbox 실행은 Gradle cache lock 권한으로 실패했고 승인된 권한으로 재실행해 성공했다. source fixture cross-check/운영 설정/Store API 호출은 수행하지 않았다.

### 6.3 Identity 후속 검증 회신 반영

2026-10-07 사용자 전달 「TMI-199 Billing 공통 fixture 결과 — Identity 검토」의 최신 §7을 기준으로 한다. 최초 정적 검토 §1~6의 독립 실행 미완료 판단은 최신 결과로 갱신한다.

| 항목 | 최신 결과 | 근거와 한계 |
| --- | --- | --- |
| 독립 fixture 실행 | PaymentLifecycleFixtureTests40건 + WithdrawalTimestampCompatibilityTests4건 통과 | Identity 회신 기준; Billing에서 상대 테스트 재실행하지 않음 |
| Identity 전체 테스트 |170 suites /1233 tests /failures0 /errors0 /기존 skipped6, 신규 skipped0 | 회신 기준; 이번 로컬 조회에서 해당 테스트 XML은 찾지 못해 실행 산출물 직접 검증과 구별 |
| 공유 JSON4종 | 보고된 SHA-256과 Billing 로컬 파일 모두 일치 | Billing에서 shasum으로 직접 재확인; index manifest 해시 일치는 Identity index 생성 검증이 아님 |
| 시각·기존 wire | 실제 엔티티 converter 왕복의 User/outbox 동일 밀리초, 기존 mapper/Boot Jackson4필드 유지 | source 테스트 확인 및 회신; 실제 서버·replica-set·배포 ObjectMapper 검증 아님 |

다음은 추가 제품 정책 선택이 아니라 구현 전 기술 동결이다. 동일 영속 시각을 신규 journal과 snapshot의 근거로 삼는 방안을 권장하되 기존 event/digest는 변경하지 않는다. DTO·오류·인증·index와 남은 Mongo 실험의 담당/gate를 정리한 뒤 공동 C0 완료를 판단한다. 코드·fixture·운영 flag·Jira 상태는 이번 회신 반영으로 변경하지 않았다.
