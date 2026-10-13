# TMI-204 snapshot 영속 처리 구현

> 2026-10-13 갱신: hash는200행/30초마다양보하고 exactlease로재개하며 process소실시처음부터재검증한다. 운영도구·scheduler·validator확장은 [새 runbook](TMI-204-operator-and-bounded-verification.md)을 따른다. 외부wire/hash/기존checkpoint설치조건은유지한다.

> 후속: [feed·영속 재시도](TMI-204-feed-processing.md)에서 control version3·FEED receipt 및 기본OFF worker를 추가했다. 아래 version2·미구현 설명은 이 후속 범위를 함께 읽는다. snapshot 자동 조율은 여전히 후속이다.

- 날짜: 2026-10-12 / branch: feat/TMI-204-billing-withdrawal-recovery
- 범위: PR2의 저장·단계 처리 코어. 자동 실행과 전체 TMI-204 완료는 아니다.

## 5줄 결론

1. 생성 요청 전에 operation을 저장하고 응답 유실 후 같은 key로 재시도한다.
2. 페이지 최대 20명의 탈퇴 반영·임시 행·receipt·진행을 하나의 Mongo transaction으로 저장한다.
3. 마지막 페이지 이후 전체 count/digest를 검증해야만 baseline T/H와 checkpoint H를 설치한다.
4. epoch·60초 lease·만료 검증으로 늦은 응답을 차단하고 과거 불명은 boolean만 남긴다.
5. 실제 HTTP·scheduler·feed·자동 재시도 예산은 후속이며 모든 실행 flag는 기본 OFF다.

## 사용자가 반드시 읽어야 하는 내용

`WithdrawalSnapshotProcessor`는 명시적으로 호출하는 비등록 클래스다. `start`는 DB 작업만 수행하고 `step`은 한 번의 HTTP 호출 또는 전체 hash 검증을 수행한다. HTTP는 transaction 밖에서 수행한다. 앱 시작만으로 복구가 실행되지 않는다.

| 상태 | 다음 동작 |
| --- | --- |
| REQUESTING | 저장된 operationId로 생성 요청 |
| POLLING | 같은 snapshotId의 BUILDING 상태 조회 |
| APPLYING | 다음 페이지를 원자 반영 |
| VERIFYING | staging 전체 count/digest 검사 후 기준점 설치 |
| COMPLETE | 추가 호출은 완료 상태 반환 |
| FAILED/EXPIRED | 자동 새 run을 만들지 않고 종료 상태 유지 |

검증은 userId 오름차순 keyset 20행씩, 고정 메모리로 `u32BE(length) + canonical JSON` SHA-256을 계산한다. 매 batch마다 lease를 갱신하며 프로세스 중단 시 처음부터 다시 계산한다. 검증 중 staging 정리와 경합하면 run version/expiry/epoch 확인으로 설치를 거절한다. 한 번의 전체 hash pass는 시간 상한/yield가 아직 없으므로 대용량 운영 전 실행 조율이 필요하다.

snapshot fact는 가짜 event/inbox를 만들지 않는다. 부분 적용 뒤 digest 검증이 실패하면 WITHDRAWN 사실은 유지하지만 baseline/checkpoint는 설치하지 않는다. snapshot에 없는 사용자를 ACTIVE로 바꾸지 않는다. 기존 checkpoint보다 낮은 H 또는 다른 stream으로 자동 교체하지 않는다. worker는 구매 barrier를 해제하지 않는다.

snapshot은 T 시점 Identity DB에 남은 WITHDRAWN 목록이다. count/digest 성공은 사라진 tombstone이나 전체 과거 event의 완전성을 증명하지 않으며 자동 삭제 승인도 아니다.

## 사용자가 결정해야 하는 사항

새 상품 정책 선택은 없다. 실제 DDL·epoch/schemaVersion 전환·flag 활성화·판매/삭제는 별도 배포 승인 대상이다.

## 주요 위험과 남은 작업

- BUILDING 5분 제한, poll/backoff, rolling 24시간 추가 2run 예산, 실패 후 새 run 전이는 아직 연결하지 않았다. 현재 FAILED/EXPIRED는 명시적으로 멈춘다.
- feed 및 source 소실 감지→snapshot 자동 전환은 후속이다. snapshot 완료만으로 CAUGHT_UP을 선언하지 않는다.
- 임시자료 삭제는 `purgeExpiredBatch` 코어만 있다. 독립 scheduler/watchdog·기한 내 삭제 운영 검증이 없어 운영 실행을 허용하지 않는다. operation 120일 정리도 후속이다.
- 실제 Identity/SigV4·Mongo 복원·운영 부하 검증은 미수행이다. 로컬 Testcontainers와 가짜 client 결과를 실환경 검증으로 간주하지 않는다.

## 저장 규격과 이관

exact BSON validator와 index는 다음 resource가 기준이다.

- `src/main/resources/payment-recovery-control-schema.json`: 기존 header에 snapshotJob/baseline/checkpoint/historicalUncertainty 추가. header reader는 int64 schemaVersion 1/2를 허용하나 snapshot writer는 2만 허용한다.
- `src/main/resources/payment-recovery-snapshot-schema.json`: snapshots/rows/pages/operations 4개 collection, unique 5개와 정리용 4개 index. 기존 control 포함 총 5개 collection이다.

snapshotJob은 operation/stream/state/start/expiry/lease/snapshotId/version만 저장한다. READY 전 snapshotId는 null이다. READY 이후에만 snapshots 행을 생성하여 nullable unique 충돌을 피한다. rows에는 최소 userId/정확한 nanos withdrawnAt만 저장한다. receipt에는 raw cursor/명단 대신 digest를 저장하며 상세 knownGaps 배열은 복제하지 않는다.

모든 collection은 strict/error validator, simple collation, 명시 unique 및 순서 지정 index를 사용한다. TTL/partial/sparse/hidden은 없다. 시작 시 검증만 하며 `initializeEmpty`는 명시 additive migration/test helper다. 기존 validator나 index를 자동 수정하지 않는다.

PR1 validator가 설치된 DB는 신규 binary의 exact 검증과 다르므로 무중단 호환을 가정하지 않는다. snapshot OFF 유지 → 관련 writer drain/격리 → 승인된 control validator 확장과 4개 collection/index 추가 → 신규 binary의 검증 및 기존 ACCOUNT/PUSH 확인 → 별도 승인된 schemaVersion 2 설치 순서가 필요하다. 운영 migration 도구는 아직 없고 이번 작업에서 DB를 수정하지 않았다. 구 PR1 binary로 무조건 rollback하면 validator 검사나 version 판정에 실패하므로 허용하지 않는다.

`BILLING_PAYMENT_RECOVERY_SNAPSHOT_ENABLED=false`가 기본이다. true는 fence-required와 control version2를 요구하며 schema 불일치는 startup 실패다. 이 flag 자체는 scheduler 등록이나 실HTTP 연결을 제공하지 않는다.

### 보존과 정리

임시 run/row/receipt 만료는 local 시작+7일과 source expiry 중 빠른 값이다. BSON ms로 내릴 때 만료를 뒤로 미루지 않는다. 만료 batch는 한 run의 rows 최대20개와 receipts 최대20개를 지우고 모두 비었으면 digest/cursor를 담은 run도 지운다. snapshot 실행 OFF여도 fence가 설치돼 있으면 호출 가능하다. ALL_BLOCKED에서는 삭제도 막히므로 운영 격리 중 overdue 처리는 후속 runbook으로 검증해야 한다. baseline/탈퇴 lifecycle과 120일 operation은 이 정리에서 삭제하지 않는다.

## 검증 근거

`WithdrawalSnapshotIntegrationTest`: 페이지 재시작·빈 snapshot·생성 응답 유실·BUILDING·operation 충돌·잘못된 digest·페이지간 중복·transaction rollback·expiry·epoch 교체·lease takeover·batch purge·Mongo transient/unknown commit·schema drift·source 조기 만료·checkpoint 역행 거절.

`RecoveryConfigurationTest`: 기본 OFF에서는 DB를 접근하지 않고 snapshot ON/fence OFF는 거절한다. 최종 전체 실행 결과는 WORKLOG/CURRENT_STATE에 기록한다.
