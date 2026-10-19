# 테스트 환불 후속 처리 활성화 준비

## 2026-10-17 LC 삭제 전달 준비 및 세 flag 배포

- **최종 결과:** task42 단일 COMPLETED/running1/pending0, 세 flag true 재확인, 새8080/8444 target healthy 및 외부 health200. 아래 배포요청·확인중 표현은 당시 이력이며 현재 기동·배포 완료다. 정상서명/업무E2E까지 완료됐다는 뜻은 아니다.

- 최초 유지보수는 테스트 challenge 2건을 발견해 DB 쓰기 전 중단했다. 사용자 후속 미보존 승인 후 writer를 중지하고 test host/DB·건수를 재확인해 challenge_10s_attempts 정확2건만 삭제했다. 별도 백업은 없으며 복구를 보장하지 않는다. 시험/생성/삭제/outbox 자료0 및 기존 rollout 조건을 검증했다.
- 삭제 전달 target index(nonunique/nonpartial/nonTTL) 설치·재검증 및 linked target expiry 부재 확인 후 billingDeliveryVerified 저장 성공(exit0). 이는 저장소 준비로서 공동 HTTP/E2E 완료가 아니다. 자동 purge·S3·운영/Identity·Billing 자료는 변경하지 않았다.
- LC task42는 상태 writer/publisher·삭제sender 세 flag만 ON이다. ECS가 environment 배열 순서를 바꿔 최초 직접 비교가 중단됐으나, 이름별 비교로 세 값 외 등록 설정 불변을 확인한 후 배포 요청했다. 이미지·Secret·roles·ports·네트워크 보존. 실제 건강/rollout 결과는 후속 확인 중이다.
- 사용자는 지정 UID 계정으로 로그인 불가라고 확인했다. 사용자 JWT·구매 증거를 만들지 않으며 정상 사용자 시험 생성 검증은 별도다.

## 2026-10-17 서명키 보완 후 task5 배포 완료

- 사용자가 준비된 명령을 실행한 뒤 PRIVATE_JWK의 key_ops=[sign]을 값 출력 없이 확인했다. 동일 키 유지이며 LC 공개키/설정 변경 없음.
- task5 force deployment 완료: 단일 PRIMARY COMPLETED, desired1/running1/pending0, task5 RUNNING/HEALTHY, ALB healthy. Spring 기동 및8082/8083/8443 connector 확인.8083의 외부 ALB/SG 허용은 추가하지 않았다.
- 아래 task5 묶음 flag가 활성화됐고 recovery barrier OPEN을 유지한다. 실제 Store 검증/실행, provider sync/worker/ingress, 자동 purge, Identity receiver, phone continuation은 이번에 활성화하지 않았다.
- 실제 동일 네트워크 ALB probe3 재실행은 exit0/ALB_ACTIVATION_PROBE_OK. reserve/status/confirm/cancel/attempt-event/deletion 6경로의 unsigned 및 invalid JWT를401로 거절했다. OFF phone·Identity, 외부 health·public API, GET deletion은404. HTTPS는 공인 CA 검증을 유지했다.
- 이전 failed probe는 그대로 실패 기록이며 이번 재실행 성공으로 구분한다. 정상 서명 principal/업무 payload·시험 생성·환불 전달·삭제204 저장은 아직 공동 E2E 미실행이다. 계정·결제·시험 데이터를 생성하지 않았다.
- 제품코드 수정 없이 배포 및 실행 검증만 수행하여 Gradle은 재실행하지 않았다. IAM/ALB/SG·LC/Identity·운영 환경과 기존 dirty는 보존했다.

## 2026-10-17 task5 기동 실패 및 복구

- Identity 테스트 JWKS HTTPS200/공개키1개 확인. 기존 task4 이미지·entrypoint·네트워크를 보존해 task5 등록: paid foundation/recovery/admission, MEMBER evidence·purchase account, 통합 reader와 비공개8083 connector, attempt events·삭제 receiver·reservation expiry, refund processing/publisher·Slack를 묶음 설정했다. provider sync/worker/webhook·자동 purge·Identity receiver는 켜지 않았다. 앱용 ALB/SG 노출 추가 없음.
- 기존 writer를 중지한 뒤 maintenance7은 새 테스트DB의 업무자료 없음·schema 검증을 통과해 ALL_BLOCKED→OPEN CAS 성공(exit0). 실제 과거 탈퇴 완전성 증명을 만든 것이 아니다. OPEN 상태는 이후 앱 rollback과 별개로 유지된다.
- task5의 RefundAccessPublisher 기동에서 Invalid workload signing key configuration/exit1 발생. 비밀값 없이 metadata만 검사하여 RSA3072/RS256/sig·개인키 필드 존재, key_ops 누락 확인. 같은 키의 공개 JWK verify 메타데이터와 발신 private JWK sign 메타데이터는 별개다.
- task4 복구 요청 후 running1/pending0/ALB healthy 확인. 확인 시 ECS rollout IN_PROGRESS/container health UNKNOWN이므로 완전한 rollout 완료라고 기록하지 않는다. probe3은 STOPPED/exit1이며 인증 검증 성공 아님. 새 기능은 미활성화 상태다.
- Secret 동일 키에 key_ops=[sign]만 추가하는 명령을 미실행 상태로 준비하고 사용자 실행을 요청했다. 키 재생성·값 노출·LC 키/설정 변경 없음. 실행 확인 뒤 task5 재배포 및 실제 인증 probe를 다시 수행해야 한다.

## 2026-10-17 누락 schema 및 삭제 ALB 경로 보완

- 실제 업무 collection은 비어 있고 기본 benefit definition과 recovery control만 존재함을 읽기 점검했다. PaymentSyncSchema·DeletionSchema 설치/검증을 bootstrap에 추가하고 OPEN은 writer 중지 및 업무 자료 없음 조건으로 한정했다. 복구 증거나 과거 snapshot을 생성하지 않았다.
- 유지보수 task6으로 install/verify 성공(exit0, BILLING_SCHEMA_OK install). payment_sync_commands·exam_deletion_facts·exam_deletion_receipts가 대상이다. finally 복구 절차 후 기존 Billing task4 desired1/running1/pending0/PRIMARY COMPLETED 및 ALB healthy 확인. 추가 collMod 권한 부여 없음.
- 사용자 실행 직전 승인으로 ALB priority89에 POST `/internal/v1/exam-deletions/events`만 추가했다. host `billing-workload-test.to-teacher.com`, source `13.124.57.130/32`, 기존 HTTPS8443 target을 유지한다. 기존19개 rule을 재조회 비교하여 불변 확인. rule96의 나머지 경로404도 보존했다.
- LC task41의 receiver·8444/TLS 경로와 Billing 발신 공개키 대응(`key_ops=verify` 포함)을 확인했다. LC/Identity 설정은 수정하지 않았다.
- 아직 Billing 업무 flag·ALL_BLOCKED는 그대로다. ALB 규칙 추가는 삭제 업무 처리/정상 JWT/204 E2E 성공을 뜻하지 않는다. 신규 runtime Secret 주입 및 의존 flag의 묶음 배포, fresh-test fence 전환 전제 확인, 양방향 서명·업무 검증이 남는다. provider/Store 증거 및 자동 purge는 별도다.
- bootstrap javac 성공, 실제 maintenance install/verify 성공. 제품코드 변경 없이 이번 후속에서 Gradle 전체 회귀는 재실행하지 않았다. 이전1763건을 신규 공동 테스트 결과로 간주하지 않는다.

## 2026-10-17 이미지 전용 배포 완료

- CloudShell 파일 업로드를 네이티브 Chrome 선택창으로 완료하고 테스트 ECR에 `refund-20261017` 등록. digest `sha256:1b15b2b1037c8d1ef5511d5860ca2fb85c1fc8edbd30f0948a4917c04b807666`.
- ECR scan COMPLETE: HIGH/CRITICAL 없음, MEDIUM2/UNDEFINED4. 모든 취약점 없음이라는 의미는 아니다.
- 기존 task3 등록 가능 설정을 복제하고 image만 변경하여 task4 등록/서비스 교체. 비교 결과 `ONLY_IMAGE_CHANGED True`. 네트워크·키·권한·feature flag 보존.
- ECS running1/pending0/PRIMARY COMPLETED, task4 RUNNING/HEALTHY·digest 일치, 새 ALB target healthy. 이전 target은 확인 시 draining이었다.
- 환불 processing/publisher OFF 및 DB ALL_BLOCKED 유지. LC 수신 준비·공동 활성화/업무 E2E는 후속이며 이번 이미지 배포로 완료 처리하지 않는다.
- 증빙: `/private/tmp/billing-test-revision4-healthy-20261017.jpg`. 코드 재수정·DB 변경·commit/push 없음.

## 2026-10-17 임시 권한 회수 저장 완료

사용자 재로그인 후 `tosunsaeng-billing-test`의 `billing-test-schema-collmod` 연결을 제거하고 Update User 저장 성공을 확인했다. 사용자 목록에는 기존 `readWrite @ to-teacher-billing-test`만 남는다. 아래 10월16일 회수 미완료 기록은 당시 상황이며 이번 저장으로 해소했다. custom role 정의 자체는 삭제하지 않았다. 다른 사용자·비밀번호·DB 데이터·스키마·배포·기능 스위치는 이번에 변경하지 않았다.

## 2026-10-16 collMod 오류 해결·설치 성공 (권한 회수 미완료)

- 정확 오류: MongoCommandException code8000, RefundCoreSchema.upgradeUnderMaintenance의 collMod(line81). Atlas 사용자에는 readWrite@to-teacher-billing-test만 있었다.
- 사용자 승인 후 `billing-test-schema-collmod` custom role(collMod, DB=to-teacher-billing-test, all collections)을 생성하고 Billing 테스트 사용자에 임시 연결했다. 운영/다른DB/비밀번호 변경 없음.
- 동일 보호조건으로 재실행한 task `04d0a8e59a3d4535bf6aa8c91ca0b7c0`은 STOPPED/exit0 및 `BILLING_SCHEMA_OK install`. 모든 installer 후 exact verify가 성공했다. barrier OPEN 작업은 하지 않았다.
- 기존 서비스 task3 desired1/running1/pending0, rollout COMPLETED 확인. 신규 이미지·환불 활성화·LC/Identity 수정은 아직 없다.
- 임시 역할 연결 제거 후 Update User를 눌렀으나 `The request requires user authentication`으로 거절됐다. **회수 완료 아님**. 재로그인 화면을 사용자에게 인계했으며 로그인 후 custom role 연결 제거/저장/재조회가 최우선이다. 역할 정의 자체는 남아도 사용자 연결이 없어야 하며 다른 사용자의 권한을 변경하지 않는다.

## 2026-10-16 스키마 설치 준비·중단 기록

- 요청 제한 HMAC용 테스트 Secret `tosunsaeng/test/billing/payment-runtime` 생성 및 테스트 실행 역할의 `billing-test-payment-runtime-read` 단일 Secret 읽기 정책을 사용자 승인 후 적용했다. 비밀값은 출력하지 않았다. 기존 키를 덮어쓰지 않는 생성 방식을 사용했다.
- `scripts/ops/BillingTestSchemaBootstrap.java`는 앱 자동 기동과 분리된 일회성 도구다. URI의 실제 DB/host를 기대값과 대조하고 test DB만 허용한다. install은 writer 중지 확인 및 기존 업무 collection이 비어 있어야 가능하며, 기존 installer/정확 검증을 재사용한다. 임의 삭제·샘플 결제·복구 이력 발명은 하지 않는다.
- 로컬 임시 Mongo에서 install/verify/재설치/open 성공, 잘못된 host와 비어 있지 않은 업무 collection의 install 거절을 확인했다. 전체 `clean test` 1,763건/실패0/오류0/skip0(3분56초), bootJar 및 amd64 이미지 빌드 성공. 임시 Mongo는 중지했고 삭제하지 않았다.
- 실제 테스트 DB read-only inventory: 15개 collection, benefit_definitions 1건 외 0건. Billing 서비스만 desired=0/running=0/pending=0 및 잔여 서비스 task 부재를 확인한 뒤 schema task revision2로 설치를 시도했다.
- 설치는 `MongoCommandException`/exit1로 중단됐다. 일부 DDL이 적용됐을 수 있으므로 설치 완료나 원복으로 기록하지 않는다. 원인/현재 manifest 진단 전 재설치·권한 확대·barrier OPEN·환불 활성화는 하지 않는다.
- 조회 시 LC task39는 refund receiver(`BILLING_ACCESS_EVENTS_RECEIVER_ENABLED`) OFF, port8080만 노출됐다. Identity issuer는 테스트 HTTPS 주소로 확인했으나 연동 성공 검증은 아니다. 양 서버 설정을 변경하지 않았다.
- revision3 read-only 검증은 foundation/recovery/paid 검증을 통과한 뒤 RefundCoreSchema.verify의 validator 대조에서 실패했다. payment/recovery/paid 및 구매 collection들이 생성됐고 recovery control은 1건이다. 환불 upgrade의 collMod 단계가 의심되지만 최초 MongoCommandException의 숫자 코드는 미수집이므로 권한 문제로 확정하지 않는다. 자동재설치/validator 우회/인덱스 삭제는 금지한다. 기존 task3 desired1로 재가동을 요청했다.

## 2026-10-16 Secret 권한·Slack 실연결 확인

- 사용자 승인 후 `tosunsaeng-billing-test-execution-role`에 `billing-test-refund-and-slack-read` 정책을 추가하고 재조회 일치를 확인했다. GetSecretValue는 테스트 refund-workload-jwt와 기존 Identity test support-slack 정확 2개 Secret만 허용한다. 기존 정책·운영 역할은 변경하지 않았다.
- 상시 서비스가 아닌 일회성 `tosunsaeng-billing-test-refund-secret-probe:1`에서 기존 테스트 이미지/사설 네트워크/실행 역할로 두 값을 주입했다. DB Secret·task role·public IP·port mapping 없이 서명키의 비어 있지 않음과 Slack HTTPS 200/ok를 검사한다. 서명키 내용/서명 유효성/LC JWT 인가 검증은 아니다.
- 실제 문의-테스트 채널에 `[Billing test] 환불 알림 연결 확인입니다...` 메시지 1건 수신 확인(2026-10-16 23:20 KST). 사용자/거래정보 없이 테스트 설명만 전송했다. 기존 앱 표시명은 Inquiry Notification이다.
- 후속 ECS 설정: `BILLING_PAYMENT_PROVIDER_SLACK_ENABLED=true`, `BILLING_PAYMENT_PROVIDER_SLACK_WEBHOOK_URL`은 기존 support-slack의 `webhookUrl` JSON key 참조. 서명키는 refund-workload-jwt의 `PRIVATE_JWK` 참조. 실제 비밀값은 문서에 기록하지 않는다.
- 상시 Billing task3/feature flag, LC/Identity 배포 및 DB는 변경하지 않았다. 새 JAR 빌드는 성공했지만 새 이미지 배포·환불 ON은 미완료다. Billing paid/payment/refund 스키마 설치와 LC 수신 TLS/기능/연결 확인 후 합쳐 배포해야 한다.

## 이번 보완 범위

검증된 환불 자료를 반영한 뒤의 fanout·LC 발송 런타임을 테스트 환경에서 기동할 수 있도록 보완했다. Store 환불 실행, 실제 Store 자료 정규화 adapter 완성, 전체 기능 활성화 또는 배포 완료를 뜻하지 않는다. 기본값은 OFF다.

기동 조건은 active profile이 test 하나, workload environment=test, payment environment=SANDBOX, 실제 PaymentStore DB 이름이 `-test` 또는 `_test`로 끝남, recovery fence required=true다. DB 이름 검사는 오설정 방지일 뿐 데이터·권한 격리의 증명이 아니다. 테스트 전용 DB 계정·외부 연결 대상을 별도로 확인한다. staging/production은 계속 기동 거절한다.

| processing | publisher | 동작 |
| --- | --- | --- |
| OFF | OFF | 기본 비활성 |
| ON | OFF | 테스트 제한 조건 및 기존 의존성 확인 후 내부 정리 |
| ON | ON | 위 조건에 더해 LC 발송 설정 필요 |
| OFF | ON | 기동 거절 |

## 한 번에 배포할 때 준비할 것

- `BILLING_PAYMENT_REFUND_LEARNING_CORE_ORIGIN`: 테스트 LC HTTPS origin.
- `BILLING_PAYMENT_REFUND_PRIVATE_JWK`: 테스트 전용 서명키를 Secret에서 주입. 문서·로그에 원문을 남기지 않는다.
- 기존 processing/publisher 설정과 paid foundation·MEMBER recovery 의존성, 정확한 schema/index 및 recovery fence 상태.
- 실제 알림 Sink 설정. 없는 Sink를 성공하는 임시 구현으로 대체하지 않는다.
- Billing 실행 역할의 해당 Secret 읽기 권한, LC 수신 공개키의 `key_ops: [verify]`, issuer/subject/audience/scope 일치.
- LC TLS connector·target group·수신 기능, Billing→LC 네트워크 경로. 공개 포트의 workload 우회 차단 유지.

이 항목을 묶어 준비한 뒤 한 번의 테스트 배포에서 함께 켤 수 있다. 설정만 준비됐다는 이유로 실제 JWT/업무 E2E가 통과했다고 기록하지 않는다. 이번 변경은 자동 schema 설치나 실제 DB 변경을 하지 않는다.

## 아직 같이 켜면 안 되는 항목

- 실제 Store→환불 증거 정규화: 실제 제공 데이터와 인증·거래 귀속 검증을 연결해야 한다. 테스트 전용 허위 증거 API나 수동 금액 입력을 추가하지 않았다.
- 구매 계정 자동 삭제: 현재 local/provider/coverage adapter는 확인 불가를 반환한다. 테스트 DB라는 이유로 미구매 증거를 만들어서는 안 된다. purge는 계속 기동 거절한다.
- cleanup 알림: 실제 delivery sink 연결이 없으므로 해당 활성화도 계속 거절한다. 환불 알림 Sink와 구분한다.

API/body/schema와 환불 정책은 변경하지 않았다. Identity·LC 코드/설정, AWS·Atlas·RevenueCat 및 배포 상태는 이번 보완으로 변경하지 않았다.
