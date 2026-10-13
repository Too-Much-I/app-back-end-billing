# TMI-204 recovery-r1 Billing 독립 검증

- 작성일: 2026-10-12
- 브랜치: feat/TMI-204-billing-withdrawal-recovery
- 대상: Identity feat/TMI-203-withdrawal-snapshot-feed-recovery의 로컬 후보. 원격 병합·배포 검증 아님.

## 5줄 결론

1. Billing에 공유 합성 fixture와 독립 계약 테스트를 추가했다. 최신 대상은 recovery-r1 / scalar-end-1이며 Identity Java 구현은 복사하지 않았다.
2. 요청 22개·응답 20개·상태 7개와 숫자 경계·오류·index·범위 예제를 검증한다.
3. 최초 발견한 끝 개행 허용 차이는 scalar-end-1에서 보완됐다. strict scalar·semantic 검증은 계속 필요하다.
4. 기존 TMI-199 v1 fixture와 운영 코드는 변경하지 않았다. JSON Schema 검증 라이브러리는 testImplementation만 추가했다.
5. 이번 결과는 Phase0 자료이며 공동 동결·Identity 실제 API/Mongo 구현·TMI-204 전체 완료·판매/삭제 활성화를 뜻하지 않는다.

## 사용자가 반드시 읽어야 하는 내용

### 확인 범위

- request: duplicate/trailing JSON, unknown/missing/null, UUID v4, numeric coercion·overflow, cursor 문자/길이, BOM·body limit.
- response: route별 JSON Schema와 필수/null/생략 예제. Schema만으로 semantic decoder를 대체하지 않는다.
- 숫자: D0/D1/U32를 BigInteger 기준으로 각 십진 경계와 고정 seed 임의값에 대조한다.
- feed: empty page/H2/head/done/check range 관계와 Long.MAX_VALUE. 모양이 맞아도 관계가 틀린 응답은 별도 거절한다.
- 상태: 같은 key의 다른 body가 stream 상태보다 먼저 conflict, 만료/실패/BUILDING/삭제 우선순위.
- 오류: retryable 집합, 500 RESPONSE_LIMIT_EXCEEDED 비재시도, 401/403/429 infrastructure body 비강제.
- index: 16개 manifest의 중복 이름·scope key 순서·unique·simple collation·TTL/remote generation 부재 정적 확인. 실제 index 생성·query plan 검증 아님.
- range: 첫 100개 밖 point, 100개 초과 overlap, empty max, 비연속 범위 floor 승격 금지, 인접 prefix 흡수. 테스트 oracle이며 실제 Mongo 쿼리 검증 아님.
- Identity가 추가한 hashes.json의 최신 8개 SHA-256과 Billing resource byte 일치를 테스트한다. hashes.json 자체는 서명 또는 출처 인증이 아니다.

### 최초 발견한 차이 — scalar-end-1 이전 이력

networknt 1.5.6의 JSON Schema pattern 검사에서 D0/D1/U32의 `$`는 최종 개행 앞에도 일치한다.
따라서 JSON 문자열 값 `"1\n"`이 schema를 통과한다. 승인된 canonical decimal 계약에는 맞지 않는다.
Identity RecoveryProtocol.decimal의 Java String.matches 검사는 전체 일치를 요구하므로 정적 확인상 거절하며, Billing 독립 oracle도 거절한다.
현재 production 취약점이나 API 배포 실패를 확인했다는 의미는 아니다.

당시 보완 요청에 따라 Identity가 8개 pattern 종료를 `(?![\s\S])`로 교체했다.
Billing은 전달받은 원본을 동기화하고 known-gap 테스트를 schema 거절 assertion으로 바꿨다.
아래 최신 실행 결과가 이 과거 상태를 대체한다.

### 원본 고정

소스: Identity `src/test/resources/contracts/payment-lifecycle/recovery-r1/`.
Billing은 같은 상대 경로에 별도 보관한다. 기존 `payment-lifecycle/v1/`는 유지한다.
이전 schema SHA-256: `9f5bbf6d7b9a7429ba2cfba1eb41dcd4df317670b85bde3f4e4c351fa3561863`.
scalar-end-1 schema SHA-256: `4602a059c8b05fbbee88175faac5d734e74040a8bfe1a9c4564f45f0544cd953`.
scalar-cases SHA-256: `ad146b15daf6e7bb430c21ddb6707da18fbc6b8a7c23ef5d43584b14a94bf827`.
전체 파일별 해시는 동봉한 `hashes.json`을 따른다.

## 사용자가 결정해야 하는 사항

새 제품 정책 결정은 없다. 수락 범위는 아래 최신 검증 기록을 따른다.
실제 API·DB 연동은 후속 구현/공동 검증 범위다. 이번 작업으로 자동 승인하지 않는다.

## 주요 위험과 다음 단계

- fixture 통과는 실제 source deletion/evidence 원자성, 동시 snapshot T/H, unique 충돌 재선택, unknown commit 복구를 입증하지 않는다.
- 실제 Identity HTTP adapter, signed cursor, Mongo snapshot/builder, IAM 및 Billing SigV4 연동 검증이 남아 있다.
- 내부 decoder의 event integer token 1.0 거절·Instant canonical calendar·UTF-8 invalid byte 등은 production decoder 구현 때 별도 필수 회귀다.
- Billing PR1의 control/applier/schema 및 PR2/3의 snapshot/feed 구현은 아직 시작하지 않았다.
- 전체 실행 결과는 CURRENT_STATE와 WORKLOG의 이번 검증 항목에 기록한다.

## 최초 실행 결과 — 수정본 검증 결과가 아님

`./gradlew clean test` 최종 성공: 전체 451개, 실패/오류/skip 0. 신규 계약 테스트 58개 포함.
공유 schema의 개행 차이는 알려진 제한을 명시적으로 재현하는 테스트이며, schema가 올바르게 거절했다는 뜻은 아니다.
기존 Mongo Testcontainers 회귀도 포함하지만 새 Identity 복구 adapter의 실제 DB 정합성을 증명하지 않는다.
`git diff --check` 통과. Jira 변경·커밋·푸시·배포는 수행하지 않았다.

## scalar-end-1 재검증·Billing 수락

- 2026-10-12 Identity 보완 인계 수신 후 schema/hash/scalar-cases/README 원본을 동기화했다.
- 이전 schema와 구조 대조 결과 변경은 8개 pattern의 종료 조건뿐이다. 기존 요청·응답 shape와 숫자 범위 불변.
- Billing networknt 1.5.6 독립 실행: 정상 8개 허용, 11종 문자열의 prefix/infix/suffix 삽입 264개 모두 거절.
- known-gap 테스트는 이제 schema와 strict decimal 모두 거절함을 검증한다. 기존 숫자 경계·요청/응답·상태·range 회귀도 재실행했다.
- `./gradlew clean test`: 전체 **724개 통과, 실패/오류/skip 0**, 그중 recovery fixture 테스트 331개. 이전 451개 결과를 재사용하지 않았다.
- 최신 manifest의 입력 8개 hash 일치. 사용자 인계의 새 schema/scalar hash도 일치.
- **Billing은 recovery-r1 / scalar-end-1 공유 wire/schema/fixture 보완본을 기술적으로 수락한다.** Identity의 보완 인계와 이 Billing 재검증을 양측 확인 근거로 남기고 문자열 종료 미결 항목을 닫는다.
- 이 수락은 공유 exact 자료의 해당 보완 범위이며 Phase0 전체 종료, Billing 로컬 schema/index 최종 설계, API 구현/배포나 실제 Mongo/IAM/복원 검증 완료의 승인이 아니다.
- README는 Identity 원문이므로 그 안의 Billing 수락 대기·Identity 테스트 실행 안내는 발신 시점 설명이다. Billing 최신 상태는 본 항목이 기준이다.
- runtime/타 서버/기존199 fixture/새 의존성/Jira/commit/push/배포 변경 없음. 기존 dirty 변경을 보존했고 이번 범위의 예상 밖 변경 없음.
- 다음: 204 PR1 기반 구현을 준비하면서 local schema/index와 공동 계약 본문의 이전 규격 표기를 정리한다. 실제 연동/활성화 gate는 유지한다.
