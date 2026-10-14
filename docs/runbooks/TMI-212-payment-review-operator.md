# TMI-212 결제 조회 재개 CLI

## 1. 결론

- `paymentReviewOperator`는 서버/worker/외부HTTP를 시작하지 않는 독립 운영 명령이다. 기본은 inspect, resume도 `--apply` 없이는 읽기 전용 사전검사다.
- 재개는 다시 검증하도록 대기 상태를 돌릴 뿐 지급·환불·삭제·구매 case 강제 해결을 하지 않는다.
- account 재개와 공유 project AUTH 해제를 분리한다. AUTH 해제는 SANDBOX만의 작업이 아니며 같은 RC project 전체 호출자에 영향을 준다.
- 실제 변경과 작업 완료 receipt를 같은 DB Transaction으로 저장한다. 동일 작업ID/동일 입력은 기존 결과, 다른 입력/실행 주체는 거절한다.
- 실제 운영 전 개인별 운영 DB 자격증명·최소 권한·감사 보존기간·실행 기록 보관 위치를 확정해야 한다. 이번 구현은 운영 활성화가 아니다.

## 2. 인증과 실행 준비

배포용 앱 자격증명을 재사용하지 말고 승인된 운영 환경의 별도 자격증명을 사용한다. CLI는 Mongo `connectionStatus`의 단일 authenticated user를 요구하며 익명/여러 principal이면 거절한다. 실행자 digest는 인증된 `(auth DB, username)`의 SHA-256이다. 자칭 `--operator`/`--approved` 입력을 인증으로 사용하지 않는다. DB 권한은 Mongo가 강제한다.

**DB 계정을 공유하면 사람별 신원은 구분할 수 없다.** 개인별 운영 자격증명과 승인된 실행 환경의 접속/실행 기록을 함께 사용한다. 이 CLI가 IAM/SSO/DB role을 생성하거나 개인 권한 배포를 대신하지 않는다. credential 자체가 있는 사람이 직접 DB를 수정할 위험도 별도 접근 통제로 다룬다.

환경변수는 승인된 secret 주입으로 제공하고 실제 값은 CLI 인자·shell history·저장소에 넣지 않는다.

| 환경변수 | 내용 |
| --- | --- |
| `BILLING_REVIEW_OPERATOR_MONGO_URI` | 운영 전용 DB credential을 포함한 접속 설정 |
| `BILLING_REVIEW_OPERATOR_DATABASE` | account는 Billing DB, auth는 모든 호출자가 공유하는 budget DB |
| `BILLING_REVIEW_OPERATOR_SCOPE` | account는 SANDBOX/PRODUCTION, auth는 반드시 SHARED |

최소 권한 검토: 읽기 모드는 대상 collection find·listCollections/listIndexes·connectionStatus. account apply는 recovery control/lifecycle/cursor/sync update와 receipt insert/find만 필요하다. auth apply는 해당 shared budget update와 receipt insert/find만 필요하다. schema 생성은 별도 유지보수 권한에서만 실행하며 일반 operator에 drop/delete/collMod/bypassDocumentValidation 권한을 주지 않는다. receipt는 insert/find 전용을 권장한다. 역할의 실제 적용은 배포 전 검증 대상이다.

root logger는 이 독립 CLI 프로세스에서 OFF로 두어 driver의 URI/원문 예외 출력을 막고 고정 오류문만 출력한다. 터미널 inspect에 표시되는 command ID/epoch도 내부 운영 handle이므로 Slack/일반 로그에 복사하지 않는다. userId·purchaseAccountRefId·거래원문·credential은 출력하지 않는다.

## 3. 명령

아래 `<...>`는 확인한 값으로 바꾼다. 실제 credential 예시는 제공하지 않는다. `--kind=account|auth`는 필수이며 action은 생략하면 inspect다. 오타/중복/다른 action의 옵션은 거절한다.

```sh
./gradlew paymentReviewOperator --args='--kind=account --target=<account-uuid>'
```

현재 cursor version/generation/epoch/barrier, 검토 command ID/version/failure를 최대100개 보여준다. `truncated=true`이면 더 존재한다. 대량 자동 전체 재개는 제공하지 않는다. 첫100개 중 선택 처리하고 그 작업들이 끝난 뒤 다시 조회한다. source-bound/case/상충 사유 등 실제 재개 적합성은 아래 dry-run/apply에서 검증한다.

```sh
./gradlew paymentReviewOperator --args='--kind=account --action=resume --target=<account-uuid> --operation-id=<new-uuid-v4> --version=<cursor-version> --generation=<generation> --epoch=<epoch-uuid> --failure=RETRY_EXHAUSTED --reason=PROVIDER_RECOVERED --selected=<command-uuid>:<version>,<command-uuid>:<version>'
```

- 기본 결과 `DRY_RUN`: 동일 business 전제검사만 수행, collection 생성/변경·fence version 증가·receipt 생성 없음. 시점 확인이지 예약/미래 성공 보장이 아니다.
- 실제 재개는 **같은 입력**에 `--apply --writers-isolated`를 추가한다. 관련 worker/계정 writer 중단 및 진행 중 작업 drain을 운영자가 먼저 확인한다. 이 flag가 실제 서버 정지를 수행하거나 증명하지 않는다.
- 선택대상은 최대100개 같은계정, lowercase UUIDv4와 양의Long version. `--selected=none`은 현재 cursor의 exact 실패 사유에 대한 command 없는 계정조회 재개에만 사용한다.
- `--failure`: BLOCKED_AUTH/RETRY_EXHAUSTED/PROVIDER_REVIEW/PROVIDER_CONTRACT/PAGE_LIMIT.
- `--reason`: PROVIDER_RECOVERED/CREDENTIALS_FIXED/CONTRACT_FIXED/MANUAL_REVIEW_COMPLETED. 자유문 대신 표준사유를 기록하고 상세 조사 근거는 접근 제한된 운영 티켓에서 operation ID로 연결한다. 감사 대조/응답유실 재시도를 위해 credential을 제외한 원래 작업입력도 그 제한된 기록에 보관한다. receipt의 digest에서 대상목록을 역복원할 수는 없다.
- ALL_BLOCKED에서는 거절한다. writer 격리와 recovery barrier를 혼동하거나 CLI를 위해 전역 barrier를 자동 OPEN으로 바꾸지 않는다. 기존 OPEN/PURCHASE_BLOCKED 규칙을 따른다.

공유 인증 차단은 credential 수정 후 모든 project caller를 격리하고, shared budget DB·SHARED scope로 별도 실행한다. 전송이나 credential 유효성 검사를 대신하지 않는다.

```sh
./gradlew paymentReviewOperator --args='--kind=auth --target=<project>'
./gradlew paymentReviewOperator --args='--kind=auth --action=resume --target=<project> --operation-id=<new-uuid-v4> --version=<budget-version> --reason=CREDENTIALS_FIXED'
```

apply는 같은 명령에 `--apply --writers-isolated`를 추가한다. 활성 slot이 남아있거나 version이 바뀌면 거절한다. rate starts는 보존한다. AUTH 해제가 계정 검토 건을 자동 재개하지 않는다. 계정 재개도 AUTH를 자동 해제하지 않는다.

```sh
./gradlew paymentReviewOperator --args='--kind=account --action=result --operation-id=<same-uuid-v4>'
```

result는 `APPLIED`/`NOT_FOUND`; 실행은 `DRY_RUN`/`APPLIED`/`ALREADY_APPLIED`와 건수/완료시각을 반환한다. 일반 오류 출력만으로 commit 실패를 단정하지 않는다. 응답유실 때 **새 ID를 만들지 말고** 같은 ID로 결과를 조회한다. NOT_FOUND도 진행 중 commit이 없음을 증명하지 않으므로 동시에 재실행하지 말고 기존 실행 종료를 확인한 뒤 같은 ID/입력으로 재시도한다. 이미 완료한 ID를 새로운 인증 장애나 새로운 재개에 재사용하지 않는다. 결과 조회는 이전 성공을 알려주는 것이지 현재 차단 해제를 보장하는 것은 아니다.

## 4. exact schema와 명시 설치

account Billing DB 및 auth 공유 budget DB 각각 `payment_operator_receipts` r1을 사용한다. 자동기동 설치/TTL/secondary index 없음. 기본 unique `_id_` 하나, simple collation, strict/error validator를 비교하고 불일치 시 거절한다. 다른 업무 schema/index는 변경하지 않는다.

| 필드 | 타입/제약 |
| --- | --- |
| `_id` | lowercase UUIDv4 operation ID, DB 내 유일 |
| `schemaVersion` | BSON long 1 |
| `action` / `scope` | ACCOUNT/AUTH; SANDBOX/PRODUCTION/SHARED |
| `requestDigest` | canonical 정렬된 입력·주체의 SHA-256 hex64 |
| `targetDigest` / `actorDigest` | target 및 인증 DB principal의 SHA-256 hex64 |
| `reason` | 위 표준 운영사유 enum |
| `count` | BSON long 0~100 (AUTH는1) |
| `completedAt` | BSON date, 최초 성공시각 |

모든 필드 필수, 추가필드 거절. receipt는 검토 source/원장 대체가 아니라 작업 멱등성/감사 용도다. 원문계정/사용자/거래/credential은 복제하지 않는다. 해시는 익명화 보증이 아니므로 접근을 제한한다. 작업 실패는 receipt가 없을 수 있으며 실행환경 감사/고정종료코드로 별도 추적한다. 원격 별도 감사서비스는 추가하지 않는다.

```sh
./gradlew paymentReviewOperator --args='--kind=account --action=schema'
./gradlew paymentReviewOperator --args='--kind=account --action=schema --apply --writers-isolated'
```

auth용은 kind=auth와 shared DB/scope를 사용한다. 최초 명시설치는 collection만 생성하고 기존 collection은 verify만 한다. 알 수 없는 schema를 덮어쓰거나 기존index를 삭제하지 않는다. 운영명령 중 스키마 자동 수정 없음.

**보존기간/삭제 정책은 미확정**이다. 자동 TTL·purge는 구현하지 않았으며 무기한 보존을 승인한 것이 아니다. 실제 운영 전에 기간·backup·실행자 연결관리 및 receipt 삭제 후 오래된 작업 재사용 방침을 확정해야 한다. 테스트 DB에서만 현재 검증한다.

## 5. 한계·배포 전 점검

- CLI 코드와 Mongo 인증 확인은 개인별 운영권한 발급/배포 완료를 뜻하지 않는다. `writers-isolated`는 운영자의 확인 선언이다.
- 하나의 account apply 또는 하나의 auth apply와 receipt는 각각 자기 DB에서 원자적이다. shared budget DB와 Billing DB의 전역 Transaction을 보장하지 않는다.
- 새로운 HTTP/관리자화면/스케줄러를 열지 않으며 기존 기본OFF·구매/삭제·무료권·Identity/LC 계약을 유지한다.
- 실제 credential·최소권한·schema 설치·보존정책·중지/복구·응답유실훈련은 별도 운영 gate다. 본 구현에서 운영 DB 재개·외부 API 호출·배포는 하지 않았다.
