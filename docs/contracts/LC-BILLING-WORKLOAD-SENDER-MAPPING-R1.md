# LC 발신 설정 명칭 매핑 — A 확정·startup 조합표

- 작성일: 2026-10-13. LC 기존 설정은 읽기 확인했다. 신규 세 명칭·공통 준비 gate 의미는 LC 최종 수락을 Billing 정본에 반영했다. 사용자 승인으로 상세 startup 조합표를 Billing 문서에 확정했다. LC의 표 수락·실제 기동 검증은 남아 있으며 LC 설정·코드·배포는 변경하지 않았다.
- 기존 source: LC `src/main/resources/application.yml`의 `app.billing`·`app.attempt-group-events`, BillingSagaConfiguration, 두 SigV4 client. 현재 JWT sender 설정은 발견하지 못했다.

| 목적 | 현재 LC 설정/env | 권장 매핑 |
| --- | --- | --- |
| 두 client의 Billing origin | app.billing.base-url / app.attempt-group-events.billing-base-url → BILLING_BASE_URL | 이름 유지. B 전환 시 JWT 수신 HTTPS origin에 연결. 실제 주소/port는 운영 gate |
| Reservation saga | app.billing.creation-saga-enabled → BILLING_CREATION_SAGA_ENABLED | 업무 flag 유지. JWT 활성화 flag와 구분 |
| phone continuation | app.billing.phone-continuation-enabled → BILLING_PHONE_CONTINUATION_ENABLED | 유지. 인증 전환으로 권한·body 변경 없음 |
| saga reconciliation | app.billing.reconciliation.enabled → BILLING_RECONCILIATION_ENABLED | 유지. 미완료 saga를 일괄 끄는 전환 절차 금지 |
| AttemptGroup 작성/발신 | ATTEMPT_GROUP_EVENT_WRITER_ENABLED / ATTEMPT_GROUP_EVENT_PUBLISHER_ENABLED | 기존 업무 flag 유지. JWT flag와 별개 |
| Reservation timeout | BILLING_CONNECT_TIMEOUT / BILLING_READ_TIMEOUT | 기존 기본 PT2S/PT5S 유지 |
| AttemptGroup timeout | ATTEMPT_GROUP_CONNECT_TIMEOUT / ATTEMPT_GROUP_READ_TIMEOUT | 기존 기본 PT2S/PT5S 유지 |
| JWT 발신 준비 flag | 신규 app.billing.workload-jwt.sender-enabled → BILLING_WORKLOAD_JWT_SENDER_ENABLED | 기본 false. 두 client 공통 인증 준비 flag 확정. 업무 발신 ON인데 JWT 준비 OFF면 기동 오류 원칙 수락. 상세 flag 조합은 아래 확정표를 따름. SigV4/무인증 fallback 없음 |
| trust 환경 | 신규 app.billing.workload-jwt.environment → BILLING_WORKLOAD_JWT_ENVIRONMENT | test/staging/production exact. payment SANDBOX/PRODUCTION과 구분 |
| LC 서명키 | 신규 app.billing.workload-jwt.private-jwk → BILLING_WORKLOAD_JWT_PRIVATE_JWK | Secret 주입, 기본 빈 값. ON에서 LC RSA key 검증, OFF에서 읽지 않음. Identity/user 키 재사용 금지 |
| issuer/sub/audience·TTL/skew | 신규 운영 override 없음 | 환경으로 A 명세의 exact issuer·두 audience 도출, sub 고정. TTL 60초·skew 30초 고정. A exact 규칙 최종 수락 완료 |
| 기존 SigV4 region | BILLING_AWS_REGION | B 승인 범위의 두 signer 의존성 제거 대상. 다른 AWS 사용처는 별도 확인하며 현재 설정 삭제 안 함 |

`BILLING_WORKLOAD_JWT_SENDER_ENABLED`는 인증 방식 선택 옵션이 아니다. B 전환 버전에서 SigV4 또는 무인증으로 자동 전환하지 않는다. 실제 활성 사용처/진행 중 작업을 확인하고 수신 준비 후 발신 활성화하며, legacy drain·롤백과 기동 오류의 영향은 B 범위 승인 전에 양측 수락한다.

2026-10-13 LC가 위 신규 세 명칭과 공통 flag 의미를 최종 수락했다. [최종 수락 기록](LC-BILLING-WORKLOAD-A-ACCEPTANCE-R1.md)을 따른다. 상세 startup 조합표는 아래 사용자 승인안으로 정리했다. LC 공동 수락·런타임 설정 적용은 아직 완료되지 않았다. 공동 fixture의 claim 문자열과 설정 이름을 혼동하지 않는다.

## Startup 조합표 — 2026-10-13 사용자 승인

승인 근거: 사용자가 제시한 조합표에 「좋아 그렇게 해줘」로 동의했다. Billing 문서의 구현 기준으로 확정하며 LC 코드/설정은 수정하지 않는다. LC의 상세 표 수락·실제 startup 테스트와 B 코드 구현 승인은 별도다.

- J: JWT sender-enabled, S: creation-saga-enabled, H: phone-continuation-enabled, R: reconciliation.enabled, W: AttemptGroup writer-enabled, P: publisher-enabled.
- 최초 기본값은 모두 OFF다. 아래 거절 규칙이 허용 사례보다 우선하며 기존 DB/index·lease·alert·timeout 검증도 유지한다.

### 거절 규칙

| 조건 | 판단 |
| --- | --- |
| H ON + S OFF | 기존 phone→saga 의존성으로 기동 거절 |
| R ON + S OFF | 기존 reconciliation→saga 의존성으로 기동 거절 |
| J OFF + (S ON 또는 P ON) | 발신이 가능한데 JWT 준비 OFF: 기동 거절 |
| J ON + env/서명키·kid·RSA 유효성/서명검증 오류 | 기동 거절 |
| 위 조건 없음 + 필요한 기존 업무/저장소 조건 충족 | 기동 허용. 실제 발신은 기존 업무 flag를 따름 |

첫 세 규칙으로 J/S/H/R/W/P의 64가지 boolean 조합을 판단한다. 키·업무·저장소 조건이 정상이라는 전제에서 22개 조합 허용/42개 거절이다. 이는 규칙 계산이며 runtime 테스트 통과가 아니다.

### 용도별 허용 조합

| 용도 | J | S | H | R | W | P | 필요한 조건 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 전체 비활성화 | OFF | OFF | OFF | OFF | OFF | OFF | private JWK 미파싱 |
| 키·인증 준비만 | ON | OFF | OFF | OFF | OFF | OFF | 키 검증, 발신 없음 |
| 시험 생성 연동 | ON | ON | OFF | OFF | OFF | OFF | Reservation origin/timeout·기존 saga 조건 |
| 시험 생성+phone continuation | ON | ON | ON | OFF | OFF | OFF | 기존 continuation 계약 |
| 시험 생성+예약 복구 | ON | ON | OFF | ON | OFF | OFF | 기존 recovery DB/index·lease·alert 조건 |
| 이벤트 적재만 | OFF | OFF | OFF | OFF | ON | OFF | 기존 writer Transaction/index 조건, 발신 없음 |
| 이벤트 적재·발신 | ON | OFF | OFF | OFF | ON | ON | writer·publisher 각각의 조건 |
| 기존 이벤트 발신만 | ON | OFF | OFF | OFF | OFF | ON | 기존 outbox/index·gate/lease·HTTP 조건. writer Transaction 불필요 |
| 전체 연동 | ON | ON | ON | ON | ON | ON | 모든 기능의 기존 선행 조건 |

### 키 준비 조건

- J OFF: private JWK 미파싱. S/P ON이면 키 파싱 전에 잘못된 flag 조합으로 거절한다.
- J ON: exact 환경, LC 전용 RSA≥2048·RS256 서명용 private JWK, nonblank kid와 키 유효성을 검증하고 로컬 public 성분으로 sign/verify 점검한다. 실제 secret/token은 오류·로그에 남기지 않는다.
- 동일 kid만으로 키 일치를 판단하지 않는다. LC signer의 public n/e와 Billing 배포 LC JWK의 kid/n/e가 일치해야 한다. 상대 키를 startup에서 원격 fetch하지 않으며 배포 manifest 대조·서명 smoke 검증으로 활성화 전에 확인한다. 로컬 self-check가 Billing 배포키 일치의 증거는 아니다.
- J ON/S OFF/P OFF는 키 준비만 하는 구성이다. S/P ON에서 동일 HTTPS origin과 기존 timeout을 필수 검증한다.

### Writer OFF / publisher ON source 확인

2026-10-13 LC source를 읽기 확인했다.

- AttemptGroupEventConfiguration의 Transaction manager/template는 W ON에서만 생성된다.
- AttemptGroupOutboxPublisher는 P ON에서 생성되며 TransactionOperations를 주입받지 않는다. OutboxStore·PublisherStateStore는 MongoTemplate의 단일 document lease/CAS 갱신을 사용한다.
- StateCoordinator/Backfill/SummaryProjection의 Transaction 의존성은 ObjectProvider다. publisher에 writer Transaction을 필수로 요구하는 경로는 확인하지 못했다.
- 따라서 `P→W` 의존성을 추가하지 않고 W OFF/P ON의 기존 outbox drain을 허용한다. eventId/body를 바꾸거나 새 이벤트를 생성하지 않으며 DEAD_LETTER를 자동 재처리하지 않는다.
- 현재 AttemptGroupEventIndexValidator는 W ON에서만 실행된다. B에서 P 단독 ON의 필수 outbox index 점검을 포함해야 한다. 이번에 LC initializer/DB index는 변경하지 않았다.

정적 확인은 실제 ApplicationContext·Mongo drain 성공의 증거가 아니다. B 검증에 64 flag 조합, ON/OFF 키 파싱·키 부정 사례, W OFF/P ON bean 생성·필수 index 점검·기존 event 전송·lease 경쟁·새 event 미생성을 포함한다. fixtures-1 bytes/hash는 유지한다.

Billing 수신 준비 후 LC 발신을 활성화하며 기존 worker/복구 증거를 일괄 중단/삭제하지 않는다. S3 공용 AWS provider는 보존하고 두 Billing signer만 승인 범위에서 제거한다. 배포·활성화는 별도다.
