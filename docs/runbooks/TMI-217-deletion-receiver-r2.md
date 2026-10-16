# TMI-217 후속 — Billing 삭제 수신 R2

- 날짜: 2026-10-16 / 작업 브랜치: `codex/TMI-221-refund-operations`
- 구현 기준: [공동 동결 접수](../contracts/TMI-217-DELETION-EXACT-R2-ACCEPTANCE.md). draft-3 > draft-2 > draft-1. 원문·fixture bytes 불변.
- Jira TMI-217 원문은 계약 준비 과제다. 이번 제품 코드 작업은 사용자 후속 구현 요청으로 수행했으며 Jira 범위·상태를 변경하거나 완료 처리하지 않았다.

## 1. 결론

1. Billing 삭제 POST의 strict 요청 해석·canonical digest·전용 LC JWT 목적·업무 오류·commit 후 empty204를 연결했다.
2. 최초 삭제 fact와 event receipt를 동일 Mongo Transaction에 저장한다. 같은 event 충돌 우선, 이미 삭제된 exact 대상의 새 event는 receipt만 추가한다.
3. 현재 사용자/현재 Session이면 해당 group 재응시·복원 자격을 차단하고 exact guard만 해제한다. 과거 Session/다른 현재 owner면 해당 Session만 차단한다.
4. 무료 생성 owner 기록을 사용자 승인으로 원 작업트리에서 통합했다. 원 작업트리는 수정하지 않았다. 삭제를 이유로 무료권·금융·소비 원장을 되돌리지 않는다.
5. 기본 OFF다. LC sender·204 저장·TTL 보호·공동 전체 경합·실환경 배포·최종 물리 purge 완료가 아니다.

## 2. 처리 범위

- `POST /internal/v1/exam-deletions/events`: 기존 workload TLS connector에서 기존 LC 키로 검증한 `tosunsaeng-billing:exam-deletion-events:{env}`만 허용한다. 다른 목적/서비스/method는403, 잘못된 JWT/query는401, OFF/다른 connector는404. 인증 오류는 body 없음/no-store다.
- Content-Type 단일 application/json 및 선택적 UTF-8 charset만 허용, Content-Encoding은 없음/identity만 허용한다. body 최대16,384 bytes, UTF-8만 허용한다. duplicate/unknown/null/타입 coercion/trailing/lone surrogate를 거절한다. raw body 로그 없음.
- deletedAt은 exact UTCms, schemaVersion은 정수1, userId는 canonical UUID, 나머지 세 UUID는v4. Session은 기존 opaque 규칙이다. Unicode 정규화 없이 동결 canonical escaping과 SHA-256을 적용한다.
- DB receipt 재현 → fact no-op → 실제 Session/group/source/environment 연결 → 현재 owner 쓰기 fence·pending 검사 → Session deny/필요 group·guard/fact/receipt 순서다. `creationOwner.userId`를 독립 삭제 인가 근거로 재검사하지 않는다. 생성 시 증거는 environment/reference 결속에만 사용한다.
- trial link의 sessionBindingVersion, MEMBER lifecycle 및 현재 group/guard CAS로 기존 writer와 직렬화한다. 미확인 대상은409, 진행 중 예약은503이다. commit 불명은 기존 transaction executor가 같은 Tx commit을 재시도하며 실패 시503; 같은 요청 재전송으로 확인한다.
- 일반 무료 상태 이벤트도 삭제 Session의 신규 상태 전진을 거절한다. RETAKE_AVAILABLE 전이 시 마지막 confirm Session을 유지하여 activeSessionId가 비어도 대상 판정에 사용할 수 있다. 과거 자료의 해당 증거 부재를 임의로 backfill하지 않는다.
- 현재 group의 명시 삭제 fact가 있고 현재 소유/guard 조건이 맞으면 기존 restartContext는 새 INITIAL 자격 판정으로 연결한다. 원 consumption 재사용/무료권 복원은 하지 않는다. 다른 group의 guard가 있거나 fact가 없으면 우회하지 않는다.

## 3. 저장·기동

`DeletionSchema.installUnderMaintenance(db, true)`는 **운영자가 별도로 writer 정지/환경을 확인한 명시 maintenance 경로**에서만 호출한다. 애플리케이션 기동·스케줄러가 설치/변경하지 않는다. 기존 collection의 규격 불일치는 자동 복구하지 않고 거절한다. 신규 collection:

- exam_deletion_facts: environment/session unique, environment/group 조회. 최초 사실 immutable.
- exam_deletion_receipts: environment/event unique, environment/detailReviewAt/_id 조회. detailReviewAt=최초 처리+120일, 자동 삭제시각 아님.

둘 다 strict validator, BSON long version1, partial/TTL 없음. 실제 DB에는 설치하지 않았다.

`app.billing.exam-deletion.enabled` / `BILLING_EXAM_DELETION_ENABLED` 기본false. ON 구성은 paid admission/foundation runtime, LC receiver·신뢰 environment·기존 recovery 보호와 새 schema 검증을 요구한다. 기존 TLS·DB 및 reader/event 선행 조건도 유지한다. 이 기동 검사가 실제 LC 준비나 운영 승인 대신은 아니다.

## 4. 검증 범위와 남은 작업

- DeletionRequestDecoderTest: 실제 decoder·serializer로 원문 B01~B07 실행. B03은 serializer-only이며 요청으로는 거절한다. 필수/타입/UUID/UTCms/크기/UTF-8/scalar 경계 및 immutable 결과를 검사한다.
- DeletionControllerTest/DeletionWorkloadTest: body/헤더/no-store/오류와 실제 서명 JWT 필터의 purpose·port·OFF·401/403. MVC/필터 테스트이며 실제 ALB/TLS 네트워크 검증은 아니다.
- DeletionMongoIntegrationTest: replica-set Testcontainers의 실제 Tx/unique index·동시 두 event·receipt 실패 rollback·unknown commit·commit204·과거Session/phone/Guest merge·소비보존·새 INITIAL·환불 fanout 경합·늦은 완료 거절. 운영 Mongo에서 실행한 것이 아니다.
- ReserveMongoIntegrationTest: 통합한 무료 생성 owner 4회귀를 포함한다. 실제 owner migration fixture와 원본 Session 불변을 검사한다.
- 최종 실행 수치는 CURRENT_STATE/WORKLOG를 따른다. 기존 공통 경계31개를 전부 구현·실행했다고 합산하지 않는다. 특히 LC 재시도200회/24시간·retryNotBefore·cleanup/ACK/TTL은 LC 구현 및 공동 검증 범위다.

남은 gate: LC sender/204 저장 연동, 전체 scalar/JWT·서비스 간 경합 대응표, legacy creation/environment·마지막 Session coverage, 실 schema/network/key inventory, rollback/운영 확인 및 최종 purge 정책. 증거가 없는 legacy 대상은409로 남기며 삭제 성공으로 추정하지 않는다. 기존 사용자 변경은 보존했다. LC/Identity·원 작업트리·실DB·Jira·배포·commit/push 변경 없음.

## 5. 주요 코드

- [삭제 패키지](../../src/main/java/web/tosunsaeng/billing/domain/examdeletion/DeletionService.java): decoder/controller/schema/config/service.
- [무료 생성 writer](../../src/main/java/web/tosunsaeng/billing/domain/reservation/application/ReserveService.java).
- [삭제 후 새 INITIAL](../../src/main/java/web/tosunsaeng/billing/domain/reservation/paid/PaidReservationService.java).
- [실Mongo 검증](../../src/test/java/web/tosunsaeng/billing/domain/reservation/paid/DeletionMongoIntegrationTest.java).
