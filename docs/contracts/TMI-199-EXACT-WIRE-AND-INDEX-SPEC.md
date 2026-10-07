# TMI-199 exact wire·index 수락안

- 작성일: 2026-10-07 / develop / TMI-199
- 상태: 2026-10-07 Identity §5 전체 wire 및 §6 manifest 수락·추가42건 독립 검증 회신 확인. 본 버전 공동 구현 규격 동결. 관련 변경 병합 확인·Jira 종료 승인은 별도이며 운영 검증 완료 아님.

## 1. 5줄 결론

1. 기존 UserWithdrawn4필드와 digest 규칙은 변경하지 않는다.
2. 아래 API는 필수 nullable와 상태별 생략을 구분하며 무조건 null/빈 값으로 대체하지 않는다.
3. Billing16개 기존 index와 Identity13개 추가 manifest를 이름·순서·옵션 기준으로 사용한다.
4. 숫자 경계39개와 증가 overflow, 실제 BSON int64 정렬/unique 검증을 추가한다.
5. production 코드·Identity 저장소·Jira 상태는 변경하지 않는다. 공동 수락 후 C0 종료 판단, 운영 검증은 기존 분담을 유지한다.

## 2. 반드시 읽을 내용

[공동 계약](IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md)의 route/인증/보존/복구 의미를 구체화한다. 아래 새 nested DTO와 상태별 필드 목록은 상대 수락 대상이다. 사용자 정책 승인이 이 세부 wire의 상대 수락을 대신하지 않는다.

## 3. 결정 및 수락 항목

Identity는 §5 전체 필드와 §6 manifest를 수락하고 신규 numeric-boundaries fixture를 독립 실행했다(§8). 아래 본문에 남은 '수락안/상대 수락 대상'은 작성 당시 표현으로, 이 버전 범위에서는 수락 완료로 갱신한다. 향후 변경은 다시 합의한다. 별도 상품 정책 선택은 없다. Billing에서 실행한 Identity index 실험은 상대 운영 initializer 검증이 아니다.

## 4. 위험·미확인

실제 controller/security handler·서명 cursor는 미구현이다. 이 문서는 그 검증 통과를 의미하지 않는다. 환경별 DB 분리와 UUID 전역 유일성이 기존 manifest 전제다. source의 실제 schema/collation/중복 진단 및 staging IAM은 후속 gate다. 직렬화 예제와 테스트 oracle을 운영 검증으로 대체하지 않는다.

## 5. API 필드 규격

공통: 나열 필드는 모두 필수/non-null이 기본이다. `?`는 **키 필수·값 null 허용**이고 키 생략 허용이 아니다. 상태별로 별도 명시한 필드만 생략한다. 추가 필드/중복 키/잘못된 타입을 거절한다. 요청/응답16KiB, page 최대20, overflow 시 조용한 절삭 금지. scope/consumer/environment는 인증·설정에서 얻으며 body에서 받지 않는다.

타입: `D0`=0..9223372036854775807 canonical decimal string, `D1`=1..동일 상한. `U32`=0..4294967295 canonical decimal string. 소수/지수/부호/공백/선행0·JSON number coercion 금지. DB 숫자는 BSON int64, T만 BSON Timestamp. version은 최초1, 미생성은null. UUID는 lowercase canonical, 새 operation/generation/stream/snapshot ID는v4. 사용자 UUID는v4로 제한하지 않는다. 시각은 UTC Instant canonical text; 신규 withdrawnAt은 동일 영속 밀리초, 기존 정밀도는 보존한다.

모든 아래 route는 POST, prefix `/internal/v1/billing/withdrawals`다. snapshots 생성 및 recoveries는 Idempotency-Key 필요. 데이터 route의 generation header/운영 role 예외는 공동 계약 그대로다.

| suffix | 요청 body 필드 | 성공 응답 필드 |
| --- | --- | --- |
| /recoveries | streamId, expectedGeneration?, reason(INITIAL/BILLING_RESTORE/RESYNC) |200: consumerRecoveryGeneration, recoveryState(RECOVERING) |
| /snapshots | body 없음 | 신규/BUILDING202: snapshotId,status. 기존 READY/FAILED/EXPIRED 재호출200: 아래 status DTO |
| /snapshots/status | snapshotId |200: 아래 status DTO |
| /snapshots/page | snapshotId,cursor? |200: items,nextCursor?,done(boolean) |
| /feed/page | streamId,afterSequence(D0),scanCursor? |200: items,scanCursor,targetThroughSequence(D0),nextAfterSequence(D0),scannedThrough(D0),done(boolean) |
| /checkpoints BASELINE/RESYNC | streamId,mode,throughSequence(D0),snapshotId,contentDigest,expectedCheckpointVersion?(D1) |204 body 없음 |
| /checkpoints FEED | streamId,mode,throughSequence(D0),scanCursor |204 body 없음; 위 baseline 전용 필드 생략 필수 |
| /checkpoints/status | streamId |200: exists(boolean),consumerRecoveryGeneration?,recoveryState?,version?(D1),verifiedStateBaseline?,verifiedFeedCheckpoint?,coverage |

### 5.1 상태별 필드

- snapshot status: 공통 `snapshotId,status`; READY에서만 `manifest` 필수, 다른 상태에서는 **생략**한다. FAILED에서만 `failureCode` 필수(상대 수락안: SNAPSHOT_HISTORY_LOST/SNAPSHOT_BUILD_TIMEOUT/SNAPSHOT_BUILD_FAILED), 그 외 생략. BUILDING은 Retry-After:5. FAILED 상세 예외나 사용자 ID를 반환하지 않는다.
- manifest: `snapshotId,streamId,consumerRecoveryGeneration,T,H(D0),itemCount(D0),pageCount(D1),contentDigest,legacyCoverage,knownGaps,expiresAt`. T는 `{seconds:U32,increment:U32}`. null 없음. 빈 snapshot도 pageCount="1". digest는 lowercase64hex, 별도 manifestDigest 없음.
- snapshot item: `userId,withdrawnAt`. 마지막 page만 nextCursor=null/done=true, 그 외 nonempty opaque cursor/done=false. items는 항상 배열이며 null 아님.
- feed item: `sequence(D1),event`; event는 기존 `eventId,schemaVersion(integer1),userId,withdrawnAt` 전체. 완료 page에도 scanCursor는 ACK 검증용으로 non-null 유지. 첫 요청만 scanCursor=null. nextAfterSequence/scannedThrough는 같은 연속 검증 지점, target 이하이며 done은 target 도달과 동치. 누락 구간을 빈 성공으로 바꾸지 않는다.
- expectedGeneration=null은 INITIAL에서만, 이후는 현재 generation 필수. expectedCheckpointVersion=null은 현재 세대 checkpoint 미생성일 때만 허용하며 이전 세대 version을 복사하지 않는다.
- checkpoint exists는 **현재 세대 checkpoint 존재**를 뜻한다. 세대가 있으나 baseline 전이면 generation과 RECOVERING은 존재하고 exists=false/version/baseline/feed=null. 세대 자체가 없으면 generation/recoveryState도null. exists=true면 generation/state/version/baseline은 non-null; feed ACK 전 verifiedFeedCheckpoint는null이다.
- verifiedStateBaseline: `{throughSequence:D0,snapshotId,verifiedAt}`. snapshotId는 비개인 run 참조이며 manifest/rows/digest를 영구 보존하지 않는다. verifiedFeedCheckpoint: `{throughSequence:D0,verifiedAt}`. verifiedAt은 해당 증거 최초 commit 관측값을 재사용한다.
- coverage: `{legacy:COMPLETE|PARTIAL|UNKNOWN,stream:UNKNOWN|CAUGHT_UP|LAGGING|GAP,throughSequence:D0|null,assessedAt:Instant}` 모든 키 필수. 미적용 throughSequence=null, 적용된0은"0". generation 소실로 legacy를 COMPLETE로 승격하지 않는다.
- knownGaps: 항상 배열. 원소 `{kind:LEGACY_HISTORY|SOURCE_RANGE,fromSequence:D1|null,toSequence:D1|null}`; LEGACY_HISTORY는 양끝null, SOURCE_RANGE는 양끝 필수/from<=to. 상세 개인자료 없음.16KiB 초과는 조용히 잘라 READY를 만들지 않고 실패/설계 검토한다. 이 최소 요약 shape는 상대 수락 대상이다.

### 5.2 오류

새 application internal error는 `code,message,retryable(boolean),correlationId` 전부 필수/non-null. 기존 Billing error correlation 형식을 유지한다.400 INVALID_REQUEST,422 UNSUPPORTED_CONTRACT, withdrawal409 EVENT_ID_CONFLICT, 복구409/410 세부 코드는 공동 계약대로다. 임시 저장소 장애503.401/403은 자동 반복하지 않는다. 앱/인프라401·403을 같은 JSON으로 가정하지 않는다. 새 response optional 필드 추가도 reader-first로 명세를 갱신한다.

## 6. 인덱스 전체 명세와 테스트 근거

- [Billing16개 manifest](../../src/test/resources/contracts/payment-lifecycle/v1/billing-indexes.json)는 기존 byte를 유지한다.
- [Identity13개 manifest](../../src/test/resources/contracts/payment-lifecycle/v1/identity-indexes.json)는 기존 후보 표의 물리 규격이다.
- keys의 JSON 순서가 index key order이며 모든 방향은1이다. unique는 명시값. partialFilterExpression이 없으면 partial 없음, expireAfterSeconds가 없으면 TTL 없음, sparse=false, collation=simple가 전제다. 다른 collection 기본 collation이면 이 전제를 명시 확인 후 적용하며 자동 변경하지 않는다. Mongo 기본 _id index는 목록 밖의 필수 기본 index다.
- Billing TTL은 rate purgeAt 및 withdrawal inbox purgeAt만 expireAfterSeconds=0.120일/2분은 각 purgeAt writer의 계약이지 TTL 초 값이 아니다. Identity13개에는 TTL 없음; journal/snapshot/metadata는 승인 보존에 따른 명시 cleanup이다.
- initializer는 기존 key order·방향·unique·partial·TTL·collation/sparse를 확인하고 불일치 fail-fast. 운영 drop/recreate 금지. 이번 테스트는 disposable DB의 생성/재생성과 핵심 옵션/unique 동작을 확인하며 production initializer는 만들지 않는다.
- [숫자 경계 fixture](../../src/test/resources/contracts/payment-lifecycle/v1/numeric-boundaries.json): Long.MAX_VALUE 수용, 초과값·sequence0 거절, D0의0 허용, U32 상한/초과, 잘못된 JSON 타입/숫자 표현39건.
- Billing oracle은 신규39건 + increment overflow1건, Mongo 실험은 Identity manifest와9/10/MAX의 int64 정렬·중복·active partial unique1건 추가. Identity의 기존44건에 이 fixture가 자동 포함됐다고 주장하지 않는다.

## 7. 완료 체크

- Billing 문서·fixture·테스트 보완 후 전체 clean test 실행 결과를 WORKLOG에 기록한다.
- Identity의 이 exact 문서/신규 manifest 수락 및 신규 경계 fixture 독립 결과를 받아야 공동 규격 동결이다.
- 다중노드/실제 history/commit 응답유실 등은 공동 계약의200/202/203/204/206 분담·활성화 gate 그대로다. Jira 종료와 배포는 별도다.

### Billing 실행 결과

2026-10-07 `./gradlew clean test`:326 tests, failures0, errors0, skipped0. 기존285건에 신규41건을 추가했다. JSON parse·문서 링크·git diff --check 통과. 최초 sandbox Gradle lock 실패 후 승인된 실행으로 통과했다. 실제 외부 서비스/운영 Mongo 호출 없이 disposable Mongo에서 검증했다.

신규 fixture SHA-256(Identity 독립 실행 시 동일 입력 확인):

- numeric-boundaries.json: `f72a4e61d40825d540d60aa135839510edf3981658fd65b7fe337873a1bc24eb`
- identity-indexes.json: `74d69edbfa244c619c9cb778da46abb8c851568dc1f673921e8443a0f557e55f`

## 8. Identity 수락 회신 반영 — 2026-10-07

사용자 첨부 「TMI-199 exact wire·index 수락안」의 §8을 근거로 기록한다. §1~7은 Billing 원문이며 신규 상대 결과는 §8이다.

- Identity가 이 버전 §5 전체 wire/null/상태별 생략·nested DTO·오류와 §6 물리 manifest를 구현 기준으로 수락했다. 신규 영속 밀리초/기존 정밀도 보존·D0/D1/U32·BSON 타입·v4 범위도 수락했다.
- 상대 보고: 숫자39 + overflow1 + hash1 + manifest1 = 신규42건 통과. 기존 독립44건도 전체 실행에 포함. 전체1276 tests/failures0/errors0/기존 skipped6, 신규 skipped0. 전체 수치는 당시 checkout XML 합산 보고이며 이전1233+42로 추정하지 않는다.
- Billing에서 신규 JSON2종의 SHA-256을 다시 계산하여 위 값과 일치 확인했다. Identity 테스트 실행·XML 전체 합산은 이번에 재실행/직접 검증하지 않았으며 상대 회신으로 구분한다.
- 기술 수락·공통 fixture 독립 검증 보류 사유는 해소됐다. 관련 변경 병합 확인과 양측 기록 완료 후 별도 승인으로 Jira 종료 가능하다. 이번에는 병합 여부를 확인하지 않았고 Jira를 수정하지 않았다.
- 실제 Identity initializer/controller·Mongo 다중노드/응답유실·history 손실·Billing old worker·IAM·snapshot budget/barrier 검증은200/202/203/204/206의 기존 gate로 유지한다. production 코드/판매/자동삭제 활성화 승인이 아니다.
