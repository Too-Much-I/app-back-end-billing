# 공동 기술 계약 초안에 대한 Identity 회신 재검토

- 작성일: 2026-10-07 / develop / 문서·로컬 소스 정적 검토
- 대상: 사용자 첨부 Identity 검토와 [공동 기술 계약](IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md). 구현·운영 장애 확인·계약 수정/승인이 아니다.

## 1. 5줄 결론

1. R1~R5는 모두 실제 초안의 명세 공백으로 확인되며 구현 전 보완이 필요하다.
2. snapshot H baseline·고정 feed H2·정상 원천 정리와 consumer GAP 구분은 특히 완료 판정의 필수 계약이다(§5).
3. control 누락 fail-closed는 초기화/이관 gate와 함께 적용해야 하며 일반 로그인에 존재하지 않는 결과 복구 저장소를 가정하면 안 된다(§6).
4. read/write durability·복원 후 stream identity·capturedAt 기산·retry/schema·canonical bytes도 같은 보완 차수에서 닫아야 한다.
5. 기존 제품/15일/운영 주기는 유지한다. 원천120일·snapshot7일·token상한30분은 여전히 미승인 제안이다.

## 2. 사용자가 반드시 읽어야 하는 내용

설계 방향을 바꿀 필요는 없지만 현 초안을 그대로 구현하면 정상 복구가 거절되거나, 반대로 누락이 있는데 완료했다고 판단할 수 있다. 이번 지적은 문구 다듬기가 아니라 구현 경계의 보완이다.

예를 들어 snapshot이100번까지의 상태를 반영했다면 feed는101번부터 읽어야 한다. 이를 허용하는 baseline 승인 절차가 필요하다. 이후 이번 작업의 목표를150번으로 잡았다면151번이 새로 생겨도 목표를 바꾸지 않아야 한다. 이미150번까지 반영한 뒤 원천1~100번을 보존기간에 따라 정리하는 것은 데이터 누락과 다르다.

## 3. 사용자가 결정해야 하는 사항

R1~R5의 세부 전이는 개발 권장안으로 보완하면 되며 사용자에게 모든 enum/필드 선택을 요구할 필요는 없다. 새 개인정보 증거/receipt 보존·장애로 인한 이용 제한·이관 중단이 추가되면 승인안을 별도로 제시한다. 신규120일/7일/30분 정책을 이번 첨부나 검토로 승인 처리하지 않는다.

## 4. 주요 위험과 미확인 사항

- 실제 사용자/control 누락 수, Mongo/driver snapshot 지원, 운영/과거TTL과 AWS 배포는 확인하지 않았다.
- Identity는 Billing의 실제 적용을 snapshotId만으로 증명할 수 없다. authenticated consumer의 적용 ACK는 신뢰 경계이며 Billing의 durable 검증 기록과 복원 절차로 뒷받침해야 한다.
- snapshot baseline은 현재 탈퇴 상태 적용 증거이지 원 eventId/digest 전체 수신 증거가 아니다. 이 두 종류의 coverage를 섞으면 안 된다.
- 정상 원천 정리 뒤 Billing backup을 과거 checkpoint로 되돌리면 다시 gap/unknown이 될 수 있다. 원천 정리 전의 성공 기록만으로 복원본 완료를 주장하지 않는다.

## 5. 항목별 판정과 권장 보완 — 아직 계약 변경 아님

| 항목 | 판정·현재 근거 | 권장 보완 |
| --- | --- | --- |
| R1 P1 baseline ACK |§5.3은 H부터 feed를 읽지만 초기 checkpoint0→H를 허용할 조건이 없음 |BASELINE/FEED/RESYNC 전이를 구분. snapshot consumer/stream/H/유효성, local count/digest 검증 완료 후 baseline 확정. 낮은/동일 정상 ACK는 authenticated 동일 stream 범위에서204 NOOP로 수렴시키는 안 권장 |
| R2 P1 고정 H2 |본문은 고정 목표지만 feed 요청에는 afterSequence만 있음 |첫 scan에서 목표H2를 정하고 후속 요청의 target 또는 opaque cursor에 바인딩. exclusive after, inclusive H2, 오름차순·연속성·빈 page 규칙 고정 |
| R3 P1 retention/GAP |§5.2/5.3은 만료/GAP가 전체 COMPLETE를 무효화한다고 포괄 서술 |replay floor, consumer verified baseline/checkpoint, 미적용 gap을 분리. 이미 검증된 범위 정상 cleanup은 coverage 유지; 되감기/적용증거 유실은 별도 오류/UNKNOWN |
| R4 P1 control 이관 |§5.1은 누락 거절이나 현행 control()은 transient 기본 객체 반환 |신규가입 원자 생성·승격 동기화·legacy 조건부 backfill/탈퇴 경합·기존 제어필드 보존. 누락률/coverage 및 fence/capture flag 조합 검증 후 fail-closed cutover |
| R5 P2 unknown commit |§5.1은 기존 issuance/rotation 결과 조회를 일반화 |reissue는 기존 durable 결과, 일반 로그인은 결과 불명503+새 인증 요청을 기본안으로 분리. 새 issuance receipt는 필요성이 입증될 때만 별도 승인 |

### 보완 시 주의점

- R1의 원자화는 **모든 snapshot row를 한 Transaction에 다시 쓰는 것**이 아니다. page별 commit은 유지하고 마지막 count/digest/검증 완료 marker와 local baseline H를 하나의 bounded Transaction으로 확정한다. 동일 snapshot 적용 상태에 concurrent writer가 검증 결과를 뒤집지 않도록 version CAS가 필요하다.
- 낮은 ACK를 NOOP로 처리하기 전에 consumer/stream/incarnation 및 원 요청의 유효성을 검증한다. 위조·다른 consumer snapshot은 단순 지연 ACK가 아니다. 현재 checkpoint/coverage를 낮추거나 검증되지 않은 jump를 허용하지 않는다.
- R2는 scan 도중 cleanup으로 필요한 event가 사라지면 explicit replay-unavailable/GAP로 종료한다. 빈 items로 done=true를 내보내지 않는다. 무기한 retention pin을 새로 도입하지 않는다.
- R3에서 push204는 개별 event 적용 증거이고 feed ACK는 연속 범위 증거다. snapshot ACK는 상태 baseline 증거이므로 원 event inbox 보관 의무와 동일하지 않다. consumer별 coverage 기준을 명시해야 한다.
- R4 migration은 기존 activeLogoutId/sessionEpoch 등 보호 상태를 보존하고 동시 탈퇴와 같은 쓰기 경계를 공유해야 한다. control을 일괄 ACTIVE upsert하는 방법은 금지한다.
- R5 일반 로그인503은 rollback 확정이 아니다. 반환하지 못한 token/session이 남을 수 있으므로 기존 session 만료/개수 제한과 정리 경로를 검증한다. retry로 새 token을 발급하는 것과 동일 bytes replay를 구별한다. HTTP 오류 code와 프론트 재로그인 동작은 Identity 기존 API 계약 검토 후 정한다.

### 추가 명세 지적도 수용

1. Mongo Transaction의 readConcern/writeConcern·majority durability, snapshot visibility, failover 조건을 적어야 한다. source counter와 journal의 별도 commit은 금지한다.
2. restore 뒤 동일 streamId sequence rewind 금지. incarnation/새 stream과 이전 consumer coverage의 재검증 경로가 필요하다.
3. transaction 안 wall clock capturedAt은 commit timestamp가 아니다. 현 초안의 “commit 기준120일”과 구현 필드의 의미는 일치하지 않는다. 관측시각 기준+허용 지연 또는 commit 후 확정 기산 방식 중 하나를 명확히 설계해야 하며 임의로 기산을 변경하지 않는다.
4. resumeGeneration/lifetimeAttemptCount/generationAttemptCount/notBefore와 실제 전송 attempt·lease claim을 schema에 연결한다. 만료와 긴 Retry-After 경합은 승인 없는 보존 연장으로 해결하지 않는다.
5. HTTP413/기타4xx/invalid status·중복 Retry-After/날짜 시계·0초/overflow까지 분류한다.
6. UTF-8·canonical field/Instant/UUID·length prefix endian·empty digest·page 순서를 고정한다. 불변 data manifest와 mutable run/expiry/ACK metadata를 분리한다.
7. snapshot 생성 operation의 environment/consumer/scope 바인딩, FAILED/EXPIRED 재호출·새 run 규칙, operation metadata 수명을 정한다. rows7일이 모든 metadata 보존을 자동 승인하지 않는다.

## 6. 부록 — 근거·검증·다음 산출물

- [공동 계약 §5.1](IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md): 누락 control fail-closed와 unknown commit 조회 문구 확인.
- 같은 계약 §5.2~5.3: commit 기준120일/capturedAt, baseline 부재, feed request에 target 부재, 포괄 GAP 문구 확인.
- [Identity SessionSecurityService](../../../identity/src/main/java/web/tosunsaeng/identity/domain/auth/session/application/SessionSecurityService.java): control 미존재 시 new UserSessionControl(userId) 반환을 로컬 재확인.
- [Identity LoginService](../../../identity/src/main/java/web/tosunsaeng/identity/domain/auth/local/application/LoginService.java): 일반 로그인 access 발급/refresh 흐름 확인; reissue와 같은 receipt 기반 복구 계약이 있다는 근거 없음.
- 새 알고리즘 실행/전체 발급 경로/운영 데이터 검증은 수행하지 않았다. 문서만 추가하며 diff/링크 검사, Gradle 미실행.

다음 산출물은 R1~R5 및 위 추가 명세를 반영한 **공동 기술 계약 개정안** 하나다. 별도 제품 계획을 다시 나눌 필요는 없다. 이번 검토는 원문을 수정하거나 Identity에 메시지를 전송하지 않는다.
