# TMI-221 — 환불 운영 조회·재개·알림

> 2026-10-16 후속: 아래 1차 읽기전용 설명은 조회 action에 한정한다. 쓰기는 §5의 apply/격리/감사 규칙을 따른다. 실제 provider/LC 공동검증·TMI217 삭제 접점은 미완료이며 processing ON 거절 유지.

- 날짜: 2026-10-16 / 브랜치: `codex/TMI-221-refund-operations`
- 선행: TMI-220 commit `04511c4`, PR27 develop merge `17edd0d`.

## 1. 결론

1. 별도 프로세스의 읽기 전용 `refundOperator` CLI를 추가했다. 관리 화면은 없다.
2. `purchase-show`는 순회 상태·OPEN 보류·미전송 건수를 구분한다. DONE만으로 완료라 하지 않는다.
3. `outbox-show`는 전달 상태·generation/leaseVersion·재시도 시각만 출력한다.
4. userId·Store 거래·구매 연결 ID·본문·JWT·lease owner는 출력하지 않는다. 제한된 운영 터미널용이며 Slack 원문 전송용이 아니다.
5. 감사 기반 재개·재확인·Slack·실행 구성 코드를 추가했다. 실제 provider/LC E2E·TMI217 연결·운영 활성화는 남는다.

## 2. 사용과 권한

별도 권한 있는 운영자가 인증된 단일 Mongo principal로 실행한다. 필요한 collection 읽기·schema/index 조회만 가능한 최소권한 계정을 사용한다. CLI는 `connectionStatus`의 인증 사용자 1명 조건을 확인하지만 담당자의 조직상 권한을 자동 승인하지 않는다. DB RBAC/접근 제한은 운영 전 확인해야 한다.

환경변수는 `BILLING_REFUND_OPERATOR_MONGO_URI`, `BILLING_REFUND_OPERATOR_DATABASE`, `BILLING_REFUND_OPERATOR_ENVIRONMENT`다. URI는 secret 환경으로 제공하고 명령어·문서·로그에 쓰지 않는다. environment는 test/staging/production, 결제 환경은 각각 SANDBOX/SANDBOX/PRODUCTION이며 서로 다른 배포 DB를 정확히 선택해야 한다.

```sh
./gradlew refundOperator --args='--action=purchase-show --target=<purchase UUID v4>'
./gradlew refundOperator --args='--action=outbox-show --target=<event UUID v4>'
./gradlew refundOperator --args='--action=case-list'
./gradlew refundOperator --args='--action=case-list --after=<previous nextAfter UUID v4>'
./gradlew refundOperator --args='--action=case-show --target=<case UUID v4>'
```

target은 내부 구매 또는 outbox ID이며 실제 고객 ID를 사용하지 않는다. UUID lowercase v4만 허용한다. 조회 action에 apply 등 추가 인자/중복 인자는 DB 연결 전에 거절한다. 없는 대상/다른 환경은 NOT_FOUND, 환불 작업 없는 구매는 NO_REFUND_WORK다. 실패는 종료코드1이며 0건/완료로 위장하지 않는다. 쓰기 실패는 unknown commit일 수 있어 같은 operation-id로 result를 먼저 확인한다.

Spring/worker/provider HTTP/개인키/DDL를 시작하지 않는다. 기존 r7 및 fanout schema를 검증하며 없는 schema를 자동 설치하지 않는다. DB snapshot Transaction 안에서 조회한다. DB 연결 자체의 인증/세션 처리 외 업무 데이터 변경은 없다.

## 3. 출력 해석과 한계

- `case-list`: 해당 결제 환경의 OPEN 환불/revoke 검토 건만 `_id` 오름차순 최대100건 조회한다. legacy REVIEW_REQUIRED의 reviewState 부재는 OPEN으로 읽는다. 정상 구매 검토 건은 제외한다. `hasMore`/`nextAfter`로 다음 페이지를 조회하며 총건수나 생성시각순 목록이 아니다. 페이지 간 상태 변경·새 UUID 삽입은 가능하므로 완전한 감사 export로 사용하지 않고 다음 점검은 첫 페이지에서 다시 시작한다.
- `case-show`: 해결된 건도 조회할 수 있다. caseId/내부purchaseId(없으면 null), 상태, 허용목록 reason(그 밖은 OTHER), version, 생성/해결시각만 반환한다. 문의 원문/actor/사용자/거래/정규화 증거는 출력하지 않는다. DB schema의 조회 권한은 필요하며 내부ID도 Slack에 복사하지 않는다.
- case 조회는 운영자 검토 완료/금액 확인/재조회 요청이 아니다. 금융 case의 provider 재조회·자동 해결 연결은 아직 미구현이다. `group-recheck`는 Session 증거 재확인이며 금융 case 해결 기능이 아니다. OPEN을 직접 RESOLVED로 바꾸는 명령은 제공하지 않는다.
- `enumerationDone`: phase DONE 여부만 뜻한다.
- `cleanupComplete`: phase DONE + work READY + OPEN 보류0 + 미전송0일 때만 true. 실제 환불 금융 검증/은행 반환/LC Job 완료·배포 승인까지 뜻하지 않는다.
- 각 건수는 최대100, 101번째가 있으면 `...Truncated=true`. 총건수로 사용하지 않는다.
- 제한된 터미널에는 최대100개 reviewGroupIds/undeliveredEventIds와 recoveryEpoch를 출력한다. userId·Store 거래/구매 연결 ID·본문은 출력하지 않는다. 내부 작업 ID도 Slack에 복사하지 않는다.
- 조회별 maxTime5초와 기존 PaymentStore Transaction 예산을 적용한다. 반환 행 제한은 전체 스캔 비용 제한과 다르며 실제 데이터에서 인덱스/응답 시간을 검증해야 한다. 필요 시 별도 명시 schema 변경 후 운영 적용한다.
- 현행 refund processing ON 거절/기본OFF 유지. 실DB·키·Store·LC 접근 또는 실제 운영 조회를 수행하지 않았다.

## 4. 검증

후속 회귀에는 감사 실패 rollback·멱등 재실행/다른 actor 충돌·live lease/stale epoch·미래 Retry-After 재개 거절·불명 증거 OPEN 유지·확정 증거 후 최초 outbox 생성·기존 감사 schema 업그레이드·Slack 지연/인증/회복과 실패 보존·OFF 무의존/ON 차단을 포함한다. 전체 결과는 WORKLOG를 따른다.

## 5. 명시 재개·재확인

- CLI 환경에 `BILLING_REFUND_OPERATOR_SCOPE`를 추가한다. 기존 consumerScopeId와 exact 일치하는 설정이어야 한다.
- action: `outbox-resume`, `fanout-resume`, `group-recheck`, `result`, `schema`. 재개는 target·operation-id·version(leaseVersion)·generation·epoch·reason 필수, recheck는 group도 필수다. result는 operation-id만 사용한다.
- 기본 dry-run. 실제 쓰기는 `--apply=true --writers-isolated=true`가 모두 있어야 한다. 실제 worker/producer 격리는 운영자가 확인해야 하며 이 인자가 자동으로 서버를 중단하지 않는다. live lease·세대/버전·복구 epoch·OPEN barrier 조건도 DB에서 재검사한다.
- 사유는 CREDENTIALS_FIXED / DEPENDENCY_RECOVERED / CONTRACT_FIXED / EVIDENCE_RECHECKED. BLOCKED_AUTH는 CREDENTIALS_FIXED만, group-recheck는 EVIDENCE_RECHECKED만 허용한다. 자유 텍스트/환불액/보호 Session 입력은 없다.
- 원인 해결 후 단일 대상만 재개한다. generation/leaseVersion을 증가시키고 해당 재시도 구간의 attempts·retryStartedAt만 새로 시작한다. 원 event/body/digest/구매 effect/phase/cursor는 보존한다. 미래 Retry-After를 우회해 재개하지 않는다.
- group-recheck는 현재 영속 근거만 재검증한다. 불명은 STILL_OPEN, 증거가 확인되면 기존 fanout 경로로 불변 outbox 생성과 RESOLVED를 같은 Tx에 저장한다. 삭제된 자료를 발명하거나 강제 완료하지 않는다.
- 기존 `payment_operator_receipts`를 재사용한다. 명시 `schema` apply는 기존 validator를 구 ACCOUNT/AUTH 형식 OR 새 환불 감사 형식으로 확장하며 새 collection/index/TTL을 만들지 않는다. 설치 후 구 validator만 허용하는 구버전으로 무조건 rollback하지 않는다. 실제 설치는 미수행이다.
- actorDigest는 CLI의 인증된 Mongo 사용자/db로 계산한다. 공유 DB 계정은 실제 개인을 식별하지 못하므로 개인별 계정을 사용한다. target digest·action·reason·generation·완료 시각과 요청 digest를 기록하고 대상 변경과 단일 Tx로 commit한다. 감사 저장 실패면 변경도 rollback한다.
- 응답 유실은 같은 operation-id의 result를 먼저 확인한다. 같은 요청 재실행은 기존 결과, 같은 id의 다른 actor/body는 충돌이다. 임의 새 id로 반복 재개하지 않는다.

## 6. 알림·실행 구성과 남은 범위

- 기존 PaymentSlackSink를 재사용하며 TMI-221 고정 문구/집계값만 보낸다. 60초 poll, 인증 차단은 다음 poll에 즉시 알림, 가장 오래된 미완료가5분 초과면 지연 경고, 회복 알림, KST09시 이후 일일 요약이다. 반복 장애/실패 알림은1시간 간격, 프로세스 재시작/복수 replica에서 중복 알림은 가능하다.
- fanout DONE이더라도 OPEN group/미전송 outbox는 계속 집계한다. Slack 장애는 DB 책임/상태를 변경하지 않는다. 조회 실패는 실패 metric으로 남기고 복구로 보고하지 않는다. 집계는 단일 snapshot이 아니며 순간적 수치 차이는 운영상 허용한다.
- `RefundRuntimeConfiguration`에 processing/publisher 두 flag 조건의 fanout/alerts/transport/publisher bean을 연결했다. publisher 전용 `billing.payment.refund.learning-core-origin`·`private-jwk` 설정은 기본 빈 값, 실제 키 없음. OFF는 DB/키/Slack 의존성을 만들지 않는다. processing은 기존 foundation/recovery·schema·Slack sink를 요구한다.
- `PaymentRefundConfiguration`의 ON 거절은 유지한다. 현재 RC decoder의 refunded→REFUND는 확인했으나 RefundEvidence를 생산하는 검증 완료 adapter는 없으므로 단순 상태 문자열을 새 최종 증거로 승격하지 않았다. Google 부분 환불 실제 Orders 증거도 미검증이다.
- TMI217 Session purge가 현재 브랜치에 없어 최종 보존 hook을 임의로 만들거나 상대 작업을 수정하지 않았다. 기존 금융/reference purge는 알 수 없는 환불 원장 타입을 보수적으로 보존하며 outbox/감사에는 TTL이 없다. 이것을 최종 Session 삭제 경합 검증 완료로 해석하지 않는다.
- 남은 구현/검증: 실제 provider fixture에 따른 adapter, TMI217 최종 삭제 writer와 보존 연결, LC Job/Callback/Summary 공동 E2E. 실제 DB schema 설치·Slack secret 설정·배포/활성화는 별도 승인이다.

### 6.1 2026-10-16 남은 연결의 선행 조건

현재 규격 R1 §3.1은 실제 provider 필드/완전성 확보 후 adapter를 고정하도록 정한다. 따라서 RC `refunded` 문자열만으로 새 RefundEvidence를 발급하거나 구매액을 반환액으로 대체하지 않았다. sanitized 테스트 거래 응답에서 거래/환경/귀속·최종상태·반환 누적액/통화/세금기준·식별자를 확인한 뒤 구현한다. 신규 증거 관측에는 별도 key를 사용해야 하며 이전 digestVersion1 inbox를 덮어쓰지 않는다. 실제 Store 조회/환불 실행 권한을 이번 구현 승인에서 추정하지 않는다.

TMI217 계약은 이 브랜치에서 draft-3이며 최종 Session 삭제 writer가 없다. 현재 증거 보존을 유지하고 새 purge/형식적인 no-op hook은 만들지 않는다. 해당 writer 통합 뒤 fanout/recheck/과거Session 처리와 삭제 우선 경합을 검증해야 한다. 금융 case 재조회·해결도 provider 증거 연결 뒤 원 case 책임과 pending 수량을 원자적으로 수렴시켜야 하며 단순 상태 편집으로 대체하지 않는다.

실제 replica-set Mongo 회귀: DONE/미전송 분리, OPEN group 보류, 전달 완료, 차단 work, 환경 격리, 건수 상한, 업무 데이터 무변경. CLI 회귀: 두 조회 action, 쓰기/중복/비정규 입력 거절, 실패 출력 비밀 비노출. 결과는 WORKLOG/CURRENT_STATE를 따른다.
