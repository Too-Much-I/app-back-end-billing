# TMI-204 PR1 제어 기반 구현

> 후속 구현: [snapshot 영속 처리](TMI-204-snapshot-processing.md)에 control version2·4collection·snapshot 코어와 이관 제한을 기록했다. 아래는 PR1 시점 범위이며 최신 미구현 여부는 후속 안내를 따른다.

- 날짜: 2026-10-12 / branch: feat/TMI-204-billing-withdrawal-recovery
- 상태: 공통 applier·strict 응답 decoder·control fence 구현. 전체204/실제복구worker 완료 아님.

## 5줄 결론

1. 기존 push 처리와 향후 snapshot/feed가 같은 ClientSession에서 탈퇴 상태를 적용하도록 공통 applier를 추출했다.
2. snapshot fact는 userId/withdrawnAt만 적용하며 가짜eventId/inbox를 만들지 않는다.
3. 구매 계정 발급·push의 앞단에 기본OFF local epoch/CAS fence를 연결했다. 중복/NOOP도 fence를 통과한다.
4. recovery-r1/scalar-end-1의 성공응답/오류 strict decoder와 fakeable client interface를 구현했다. 네트워크 adapter/worker Bean은 없다.
5. 이번 저장규격은 5collection 전체 중 control header만 구현한다. 나머지 snapshot/rows/pages/operations와 control job/진행필드는 후속 migration 대상이다.

## 사용자가 반드시 읽어야 하는 내용

### 현재 동작

- fence-required=false: 기존201 동작 유지, recovery writer는 거절. startup은 recovery DB를 조회/생성하지 않는다.
- fence-required=true: startup에서 기존 collection/index/validator와 환경별 control 존재를 검증한다. 없거나 규격이 다르면 시작 실패. 요청에서 control을 자동 생성하지 않는다.
- ACCOUNT/PUSH는 Transaction 전에 epoch ticket을 한 번 얻는다. Mongo transient retry에서도 재발급하지 않는다.
- Transaction 안에서 동일 environment/consumer/epoch와 현재 version/barrier를 확인하고 version을 CAS 증가시킨 뒤 기존 처리를 수행한다.
- snapshot/feed의 향후 호출자는 dispatch 때 ticket을 잡고 같은 transaction에서 RECOVERY fence→applier→receipt/checkpoint를 수행해야 한다. applier 자체는 commit하지 않는다.
- flags는 배포 시 고정설정으로 취급한다. hot reload로 구버전 writer와 fence writer를 혼합하지 않는다.

### Control header exact v1

collection: `payment_recovery_controls`. 모든 필드는 필수·non-null, 미등록 field는 거절한다.

| 필드 | BSON 타입/의미 |
| --- | --- |
| _id | string, 내부 식별자 |
| environment | SANDBOX 또는 PRODUCTION |
| consumer | billing |
| schemaVersion | int64, 1 |
| epoch | lowercase UUIDv4/RFC variant2 |
| version | int64, 1 이상; MAX에서 추가쓰기 거절 |
| barrier | OPEN/PURCHASE_BLOCKED/ALL_BLOCKED |

`ux_payment_recovery_control`: environment ASC, consumer ASC, unique=true, sparse=false, simple collation, partial/TTL/hidden 없음. schema resource는 `src/main/resources/payment-recovery-control-schema.json`. 기존payment/core index와v4 version을 변경하지 않는다.

startup은 **검증만** 한다. `initializeEmpty`는 명시 migration/test helper이며 request/startup에서 호출하지 않는다. 기존collection drift를 collMod/drop/recreate로 자동수정하지 않는다. 초기control/epoch설치도 별도 운영단계이며 이helper는 설치하지 않는다.

### Barrier 허용표

| barrier | 구매계정 발급/조회 POST | 탈퇴 push | 승인 recovery 적용 |
| --- | --- | --- | --- |
| OPEN | 허용 | 허용 | ticket 필요 |
| PURCHASE_BLOCKED | 503 | 허용 | ticket 필요 |
| ALL_BLOCKED | 503 | 503 | 거절 |
| 필수control없음/epoch불일치 | 503 | 503 | 거절 |

PURCHASE_BLOCKED는 최초구매준비 등 구매writer만 막아야 할 때 사용한다. ALL_BLOCKED는 복원격리/drain에 사용한다. recovery허용으로 바꾸는 운영권한/설치도구는후속이며 worker가스스로barrier를해제하지않는다. 단순lag를barrier전환에자동연결하지않는다.

### Strict decoder 범위

- raw16KiB/invalidUTF8/BOM/중복키/trailing/추가·누락필드·coercion 거절.
- UUID·canonical UTC nanos·D0/D1/U32·page20 및manifest count/pageCount/emptyhash/stream·snapshot binding 검증.
- snapshot 페이지 내 순서·중복/cursor done, feed exact after/H2/end/head/floor·연속순번/중복event 검사.
- event4필드와canonicaldigest는기존PaymentWithdrawalDecoder재사용. 기존push의시간정규화계약은변경없음.
- error exact HTTP/code/message/retryable/details와source missing/range검증.500 RESPONSE_LIMIT_EXCEEDED는retryable=false.401/403/429는이앱JSONdecoder를필수로하지않는다.
- 예외에원문/원래parser cause를넣지않는다. payload/user/cursor/digest보유record의toString은redacted.
- 전체snapshot 페이지간순서/count/digest·expiry·cursor요청binding·HTTPstatus/header·Retry-After는후속worker/transport검증책임이다. decoder통과만으로baseline설치하지않는다.

## 사용자가 결정해야 하는 사항

상품정책추가선택은없다. 실제flag활성화·DDL·epoch설치·운영권한은별도배포승인대상이다.
5collection방향은유지하되미사용4collection을빈문서로미리만들지않았다. control진행필드를추가할때도명시적인schema개정/이관검증이필요하다.

## 주요 위험과 남은 작업

- snapshot/receipt/checkpoint/lease 영속처리·재동기화·retry budget·retention worker는아직없다. 현재schema는baseline완료/과거이력불명표시를저장하지않는다.
- Identity클라이언트는interface뿐이다. SigV4 credential/HTTP구현·실API/Mongo/IAM/복원검증은없다.
- 공통control은쓰기경합점이다.현재2초transaction budget을유지하며실부하검증전확장/활성화하지않는다.
- control CAS만으로복원된옛프로세스를물리격리할수없다. 운영DB접근제거/drain/새epoch설치가필수다.
- source의tombstone소실은snapshot으로복원되지않는다. count/digest통과는과거전체완전성증거가아니다.
- PR1의제어기반이후 PR2 snapshot·PR3 feed·PR4 운영순으로진행한다. runtime adapter/worker 미구현이므로 recoveryflag는false 유지.

## 검증

신규 단위: shared성공shape,rawstrict,정규시각/UUID/nanos,불연속·빈prefix·H2변경·MAX,exact오류15종,defaultOFF.
replica-set: snapshot fact+진행실패원자rollback/가짜inbox없음/push NOOP,missingcontrol,barrier,staleepoch,중복/NOOP CAS,versionoverflow,validator drift,동시계정/탈퇴,Transaction retry의옛epoch고정.
최종 전체 실행 결과는 WORKLOG/CURRENT_STATE에 기록한다. 실제운영/상대API를호출하지않는다.

2026-10-12 최종 `./gradlew clean test`: **749개 통과, 실패/오류/skip 0**. 이번 신규25개(응답14·설정1·Mongo10) 포함. `git diff --check` 통과.
