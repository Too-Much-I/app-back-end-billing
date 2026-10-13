# recovery-r1 공동 검증 묶음

- Identity 작성본, Billing 독립 실행/수락 대기. 실제 API/DB 배포본이 아니다.
- URL `/internal/v1/billing/withdrawals`, event schemaVersion1 유지. 폴더 revision은 URL과 별개다.
- `../v1` TMI-199 원본과 hash/과거 수락 이력은 변경하지 않았다.
- exact: `docs/contracts/TMI-203-recovery-r1-exact.md`.
- 보완 식별자: `scalar-end-1`. Java regex의 최종 개행 허용 차이를 보완했다. 이전 schema hash는 hashes.json의 supersedesSchemaSha256에 보존했다. Billing은 새 파일로 독립 재검증하고 known-gap 허용 assertion을 거절 assertion으로 바꿔야 한다.

## 검증 방법

저장소 루트에서 `./gradlew clean test` 또는 `./gradlew test --tests '*withdrawalrecovery.*'`를 실행한다. 외부 업무 인프라를 호출하지 않는다. JSON Schema draft2020-12 검증기는 testImplementation에만 추가했다.

Billing은 같은 JSON을 자기 언어/검증기로 실행한다. schema는 `$defs/CreateRequest`, `StatusRequest`, `SnapshotPageRequest`, `FeedPageRequest`, `SnapshotStatus`, `SnapshotPage`, `FeedPage`, `Error` 등을 명시 선택해야 한다. root 자체는 route를 선택하지 않는다.

`request-cases.json`의 body는 raw JSON 문자열이다. 파싱한 객체만 검증하면 duplicate/trailing 오류를 놓친다. 16KiB는 UTF8 byte로,숫자는문자열로검증한다. required-nullable과생략을구분한다. `response-cases.json`은구조/숫자경계테스트다. 시각canonical·필드간부등식·연속성·실제서명/권한은별도검증한다.

`range-cases.json` generator는 n=0..count-1에 대해 from=fromStart+n*fromStep,to=toFixed(있을때) 또는from+width의순서로range를만든다. 모든순번은64bit정수문자열이며float로변환하지않는다. 전체목록선단100개를잘라쓰지말고각필요점containment를확인해야한다. requiredAdapterExperiments는아직실Mongo에서실행했다는뜻이아니다.

`identity-indexes.json`은16개선언(기본_id_별도)이다. 운영DDL/자동drop/recreate의승인이아니다. 기존I2 7개key/order/options와operation unique의환경/consumer/key scope를대조한다.

`scalar-cases.json`은8개pattern의정상예제와11종개행/제어문자/공백을정의한다. 각문자열을prefix/suffix또는첫문자뒤infix로삽입한264개는schema에서거절해야한다. 정상8개는그대로허용한다. 기존strictdecoder와semantic검증은제거하지않는다.

`hashes.json`은README/hash파일자신을제외한이묶음JSON8개에대한SHA256이다. 고정입력무결성확인용이며공동수락서명이나배포인증이아니다.
