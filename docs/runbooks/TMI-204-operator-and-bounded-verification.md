# TMI-204 운영 도구·분할 검증·발신 추적

- 작성일: 2026-10-13
- 범위: Billing 로컬 구현. 실제 운영 실행·격리 권한 검증·복원·활성화는 TMI-206 gate.

## 1. 5줄 결론

1. 운영 도구는 별도 Java main/Gradle task이며 Spring 서버·HTTP route·worker에서 호출하지 않는다.
2. 기본은 dry-run이다. 쓰기는 명시 apply/approved와 예상 epoch/version, 작업 ID로 제한하고 control과 최소 작업 기록을 한 Transaction으로 변경한다.
3. 초기 설치/epoch 교체는 ALL_BLOCKED다. 복구만 허용한 후 baseline+현재 epoch의 CAUGHT_UP 증거가 있을 때 명시적으로 구매 barrier를 연다.
4. snapshot hash는 한 번에 최대10page/200행 또는30초에서 양보한다. 동일 lease의 메모리 digest를 이어가며 프로세스/lease 소실 시 처음부터 재검증한다.
5. 별도 snapshot scheduler와 HTTP CLIENT span을 추가했다. 키·원문·사용자·cursor·baggage는 trace에 기록/전파하지 않는다.

## 2. 반드시 읽어야 하는 내용

epoch는 인증 수단이 아니다. 구 worker가 살아 있으면 새 epoch를 다시 읽을 수 있으므로 **구 프로세스의 DB 접근 제거·drain·복원본 격리**를 먼저 해야 한다. CLI의 approved/writers-isolated 옵션은 이 절차를 수행했다는 확인일 뿐 권한 부여나 자동 증명 기능이 아니다.

일반 앱의 DB 자격증명과 분리된 운영 자격증명으로 실행한다. worker에 도구 실행 권한·운영 credential·DDL 권한을 주지 않는다. Mongo 문서 내부의 특정 field만 역할별로 막는 기능을 이 코드가 제공한다고 주장하지 않는다. 실제 RBAC/배포 권한 확인은 활성화 gate다.

통합 복원 단위: lifecycle/account/ref/inbox/cleanup 후보/control/snapshot·rows·receipts·operations. 일관 복원+동일 source+source 가용 구간 충분함을 확인한 경우에만 preserve-baseline을 선택한다. 그 확인을 CLI가 대신하지 않는다. 선택적/불명 복원이나 source 교체는 기본인 baseline 폐기 후 새 snapshot 경로를 쓴다. 탈퇴 fact·계정·원장은 삭제하거나 ACTIVE로 바꾸지 않는다.

## 3. 실행 규격

별도 승인된 shell/process에 아래 환경변수를 secret injection으로 제공한다. 명령행·Git·작업기록에 URI/credential을 넣지 않는다.

- BILLING_OPERATOR_MONGO_URI: 운영 도구 전용 credential
- BILLING_OPERATOR_DATABASE: 명시적 단일 환경 DB, 1~63자 영숫자/underscore/hyphen
- BILLING_OPERATOR_ENVIRONMENT: SANDBOX 또는 PRODUCTION

검토용 예:

```sh
./gradlew recoveryOperator --args='--action=inspect'
./gradlew recoveryOperator --args='--action=rotate --operation-id=<새 UUIDv4> --expected-epoch=<현재 epoch> --expected-version=<현재 version> --new-epoch=<새 UUIDv4> --stream-id=<확인된 source stream>'
```

위 두 번째 명령도 apply가 없어 변경하지 않는다. 실제 실행은 별도 승인 후 --apply --approved를 추가하고, schema/initial/rotate에는 --writers-isolated도 필수다. shell에 꺾쇠 placeholder를 그대로 실행하지 않는다. 예상 epoch와 stream은 보호된 배포/복원 manifest 또는 승인된 DB 조회로 확인한다. inspect는 개인정보 없는 존재/schema/version/barrier/baseline 유무만 반환한다.

| action | 조건·결과 |
| --- | --- |
| inspect | 읽기만. apply 금지 |
| schema | 알려진 기존 validator에 optional operator receipt만 추가, 누락 collection/index 생성 및 exact 검증. 알 수 없는 drift 거절. index drop/recreate 없음 |
| initial | control 없음, expected-version=0/expected-epoch 생략, new-epoch 필요. schema4/ALL_BLOCKED/과거불명 설치 |
| block | exact epoch/version CAS → ALL_BLOCKED |
| rotate | ALL_BLOCKED, new-epoch. schema4로 올리고 옛 snapshot/feed job·lease/cursor를 제거. 기본 baseline/C 제거 및 과거불명 유지 |
| rotate --preserve-baseline | 승인된 일관 복원에만 사용. 동일 stream과 C>=H 필수. baseline/C 보존, 옛 in-flight job은 제거 |
| recover | schema4/ALL_BLOCKED → PURCHASE_BLOCKED. push/recovery는 허용, 구매계정 발급 차단 |
| snapshot | expected-epoch/version 확인 후 같은 epoch ticket으로 source stream/operation을 영속 준비. HTTP 호출 없음. 이후 승인된 worker가 진행 |
| open | PURCHASE_BLOCKED, schema4, 동일 stream baseline와 현 epoch feed CAUGHT_UP, C==target 및 lastSuccessAt 필요. 명시 OPEN 전환 |

initial/rotate/block/recover/open은 operation-id를 필수로 받고 같은 ID/같은 요청은 ALREADY_APPLIED, 다른 요청은 거절한다. 원래 expected epoch/version 요청을 그대로 재사용한다. snapshot은 기존 snapshot operation 멱등성을 사용하며 응답 유실 뒤 inspect로 version을 다시 확인한다. 작업 ID는 승인 티켓과 외부 운영 기록에서 연결한다. 오류는 원인 원문 없이 고정 메시지/exit1, 자동 재시도 없음.

## 4. 최초 설치·기존 이관·복원 순서

### 최초 설치

1. 기존 writer/worker OFF, DB 역할·환경·source 확인 및 격리.
2. schema dry-run은 변경하지 않는 안내이며 **전체 DDL 실행 성공의 증거가 아니다**. 승인된 apply로 exact schema/index를 확인한다.
3. initial로 control 설치(ALL_BLOCKED). 모든 신규 writer를 fence-required로 준비한다.
4. recover로 복구 쓰기만 허용, snapshot으로 초기 작업 준비.
5. 인증/키/네트워크 gate 후 snapshot/feed worker를 단계적으로 켠다.
6. baseline 검증·feed CAUGHT_UP·탈퇴 보호 확인 후 승인된 open. 과거불명 기록은 지우지 않는다.

### 이미 구 validator가 적용된 DB

새 operator는 전체 최신 schema 검증을 선행하므로 구 validator 상태에서는 block도 거절한다. 먼저 기존 승인된 유지보수 DB 세션에서 exact environment/consumer/epoch/version CAS로 barrier만 ALL_BLOCKED로 바꾸고 version을1 증가시킨다. matched/modified=1을 확인하고 구 writer를 drain/접근 제거한다. **버전 조건 없는 update와 구 process 혼합 사용은 금지한다.** 이 사전 조치는 외부 변경 승인 기록에 남긴다.

그 뒤 schema --apply --approved --writers-isolated를 실행한다. 허용하는 구 형태는 현 manifest의 operation.operator만 없는 정확한 validator다. 미지 schema는 자동 변환하지 않는다. collMod·collection/index DDL은 다중 collection Transaction이 아니므로 중간 실패 시 worker OFF/ALL_BLOCKED를 유지하고 같은 명령으로 재검증한다. 이관이 끝나기 전 새 worker를 시작하지 않는다.

### 복원

1. 영향 writer·purge 차단, 구 process DB 접근 제거, 복원 DB 비공개 격리. source 복원 시 옛 Identity endpoint도 격리.
2. exact schema/index 확인. 작업 기록·원장 등을 한 일관 복원 단위로 확인.
3. rotate로 새 epoch 설치. 기존 요청 ticket/lease의 늦은 결과는 CAS에서 거절된다.
4. 일관 복원과 source 충분함이 확인됐으면 preserve-baseline 뒤 recover/feed replay. 그 외 recover/snapshot→H 이후feed.
5. 운영 추적·탈퇴 보호·사용자 데이터 정합성 확인 후 open. 인증 중단·scope 오류 등은 자동으로 OPEN하지 않는다.

새 epoch는 장애 retry budget을 지우는 수단이 아니다. 기존 snapshot operation 기록은 남고24시간 run budget에서 운영 receipt만 제외한다. 옛 source의 최소 stream/C 증거는 operator receipt에120일 보존하며 snapshot 사용자 목록·digest를 복제하지 않는다.

## 5. 로컬 schema 변경

[snapshot manifest](../../src/main/resources/payment-recovery-snapshot-schema.json)의 payment_recovery_operations validator에 optional operator object를 추가했다. 새 collection/index와 외부 DTO 변경은 없다. 필수: action(enum), expectedEpoch(nullable string), expectedVersion(BSON int64), newEpoch(nullable string), preserveBaseline(bool). 기존 baseline이 있으면 previousStreamId/previousCheckpoint 최소 증거를 함께 기록한다. TTL은 추가하지 않으며 기존 명시 retention이120일 뒤 정리한다. 현재 baseline은 control에 남는다.

## 6. 분할 hash와 scheduler

- snapshot processor 인스턴스마다 하나의 digest/count/마지막 key만 메모리에 유지. 전체 rows 적재나 SHA 내부 상태 직렬화는 하지 않는다.
- 한 step 최대10회×20행. monotonic30초 경계에서 다음 read를 시작하지 않는다. 진행 중 DB 호출의 기존2초 timeout과 마지막 transaction 시간은 경계에 추가될 수 있어 엄격한 wall-clock30초 취소 보장은 아니다.
- 각 chunk 전 epoch/lease/expiry를 재검증/갱신한다. 중간에는 lease를 유지하고 scheduler가1초 뒤 재개한다. 다른 worker는 유효 lease를 빼앗지 못한다.
- process/lease 소실은 해시 첫 행부터 재검증한다. 따라서 잦은 재시작은 비용을 늘리지만 미검증 prefix를 신뢰하지 않는다. chunk 중 checkpoint/baseline 설치 없음.
- 현 run version/count/page 수와 전체 digest 일치 후에만 최종 Transaction으로 baseline/C 설치. 만료·epoch교체·오류에서 부분 검증 결과 폐기.
- snapshot 전용 single-thread scheduler와 기존 unqualified jobs용 별도 single-thread scheduler를 worker ON일 때만 구성한다. hash가 feed/retention/heartbeat 스케줄러를 점유하지 않는다. 대규모 자료의 실제 처리시간·메모리/부하·lease 갱신은206에서 측정한다.

## 7. 발신 추적

RecoveryTracingTransport는 billing_recovery_http CLIENT span과 고정4종 operation·SUCCESS/REMOTE_ERROR/TRANSPORT_ERROR outcome만 기록한다. 로컬 span의 W3C traceparent만 추가하고 전달받은 traceparent/tracestate/baggage는 복사하지 않는다. JWT/operation ID/URI/body/응답/exception 원문은 tag/error에 넣지 않는다. HTTP 오류 분류·Retry-After·JWT 재발급·본문/멱등성은 그대로다.

## 8. 남은 gate

새 제품 정책 결정 없음. 실제 운영자/승인권자·비상 담당·DB 자격증명 격리·ALB/TLS/키·source inventory·복원 리허설·외부 watchdog/알림 채널 및 부하는206에서 확인한다. 로컬 테스트 통과는 실제 배포/판매/자동삭제 허용이 아니다. 도구를 운영 DB에 실행하지 않았고 타 서버·Jira·commit/push는 변경하지 않았다.
