# Identity–Billing 인계: 단일 진행 관리 계약의 문서 개정 지침

- 작성일: 2026-10-12 / Jira: TMI-203·204, 영향 TMI-199·202·205·206
- 상태: 단순화 방향과 Billing204 계획 반영 완료. **본문 개정 방법을 전달하는 문서이며 새 exact 동결·구현·Jira 변경·배포 승인이 아니다.**
- 수신: Identity203 담당자 및 Billing204/공동 계약 담당자. Identity는 자기 저장소 문서·구현 영향을 검토하고 Billing 문서는 Billing에서 수정한다.

## 1. 5줄 결론

1. Identity는 원천과 snapshot/feed를 제공하고 Billing만 checkpoint·local epoch·consumer coverage를 소유하도록 문서 본문을 맞춘다.
2. Billing204 계획은 이미 개정했지만 ADR·공동 계약·exact 등은 현재 개정 안내를 붙인 단계이며 옛 본문 전체가 갱신된 것은 아니다.
3. 공동 계약의 원격 ACK/generation 절을 로컬 commit·epoch/복원 절로 교체하고 API·index·fixture를 새 revision으로 동결한다.
4. 기존 push204·capture·서명 인증·T/H/digest·H2·보존·무료/유료 정책은 유지하며 관련 없는 ACK/generation까지 일괄 삭제하지 않는다.
5. 먼저 exact 대안과 호환 전환을 회신하고, 양측 합의 후 문서 본문·manifest·테스트·운영 인계를 함께 갱신한다.

## 2. 반드시 읽어야 하는 내용

### 2.1 현재 반영 상태와 작업 경계

| 문서 | 지금 상태 | 다음 작업 |
| --- | --- | --- |
| [TMI-204 계획](../plans/TMI-204-withdrawal-recovery.md) | 본문을 단일 authority로 개정 | 새 exact 수락 결과를 반영해 후보 필드/index/운영값 동결 |
| [PLAN-009](../plans/PLAN-009-payment-catalog-and-account.md) | §2 후속 범위·204 연결 개정 | §5~6의 구현/검증 참조가 구 remote 계약을 요구하지 않는지 정리 |
| [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md) | 상단 개정 방향 안내 | 현재 유효한 결정과 과거 승인 이력을 구분하고 새 계약 revision 연결 |
| [공동 기술 계약](IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md) | 상단 안내, 구 본문 보존 | 아래 §5.1 표에 따라 관련 절 실제 개정 |
| [TMI-199 exact](TMI-199-EXACT-WIRE-AND-INDEX-SPEC.md) | 기존 동결 버전 유지·상단 안내 | 새 revision의 API/오류/index 명세 및 fixture 작성; 기존 수락 이력 보존 |
| [결정서](../codex/CONTRACT_DECISIONS.md) | 2026-10-12 단순화 계획 채택 기록 | 새 exact 수락 시 revision·적용 범위·상대 수락 근거 추가 |
| [탈퇴·purge 설계](PLAN-009-withdrawal-reconciliation-and-purge-contract.md) | 상단205 영향 안내 | §5.1/5.3~5.6/§6의 증거·복구·완료 조건 갱신 |
| Identity203 계획·202 cleanup 문서 | Billing에서 변경하지 않음 | Identity 담당자가 대응 개정 및 영향 회신 |

현재 상단 안내만 보고 새 API가 이미 확정·배포되었다고 판단하지 않는다. 이번 요청으로 양 저장소 코드나 Jira를 함께 수정하지 않는다.

### 2.2 문서에 공통으로 넣을 대체 설명

> Identity는 durable 탈퇴 원천, 고정 T/H snapshot, 고정 H2 feed 및 재생 가능 범위를 제공한다. Billing은 검증된 탈퇴 적용과 로컬 진행 위치를 같은 Transaction에서 저장하고 다음 위치부터 조회한다. 별도 복구 완료 ACK나 Identity의 consumer checkpoint·recovery generation은 사용하지 않는다. Billing 로컬 epoch/version CAS와 복원 시 구프로세스 격리는 유지한다. snapshot 상태 baseline과 실제 원 event 연속 적용 증거는 구분한다. 정상 지연은 자동 재시도하고 실제 GAP·복원·차단 기능 손상은 별도로 판단한다. 판매 허용과 조건부 삭제 허용은 서로 다른 판단이다.

**유지할 것:** 기존 UserWithdrawn4필드/eventId·digest/실시간 push204,202 capture·delivery, ACTIVE MEMBER/token/drain, Lattice IAM/SigV4, T/H·canonical hash·H2/1시간 cursor,16KiB·page20,7일/120일/15일 및 금융자료 보존, 무료/재가입 정책.

**제거할 것:** 복구 전용 원격 /recoveries·/checkpoints·/checkpoints/status, X-Consumer-Recovery-Generation, Identity consumer 완료 상태, Billing ACK outbox/worker/flag·remote confirmation·별도 scans collection.

제거는 새 계약의 설계 범위다. 이미 존재하는 DB/API를 조사 없이 실제 삭제하라는 뜻이 아니다.

## 3. 결정·회신이 필요한 사항

제품 정책을 다시 선택하는 요청이 아니라 다음 기술 규격을 함께 마무리하는 요청이다.

1. **revision/전환:** 실제 배포 여부를 확인하고 versioned 경로 또는 미배포 상태의 통제된 전환 중 권장안을 제시한다. 경로 이름·revision 번호를 본 문서에서 임의 확정하지 않는다.
2. **availability:** 필수/null/생략·타입·범위·상한·410 정보 취득 방법·source 일관성 규격과 예시를 제시한다.
3. **snapshot 요청:** 원격 generation 제거 뒤 stream 귀속을 어디서 검증할지 정한다. 기존 생성 body가 없다는 이유로 요청에 없는 stream을 받았다고 가정하지 않는다. current stream 변경 시 멱등 key·run 재조회 의미도 동결한다.
4. **DTO/index:** cursor의 마지막 page null 정책 등 ACK 제거로 근거가 사라진 필드도 명시적으로 유지/변경을 선택한다. 정확 manifest와 변경표를 제출한다.
5. **운영:** local epoch 설치·DB 일관 복원 범위·독립 watchdog·205 증거 소비·retry/circuit 초기값을 동결한다. Billing5개 collection은 아직 후보이며 wire로 전파하지 않는다.

## 4. 주요 위험과 금지사항

- ACK 문자열 전체 치환 금지: 기존 push 응답/다른 결제 프로토콜 ACK는 이번 제거 대상이 아니다.
- generation 전체 삭제 금지:202 delivery resumeGeneration/leaseVersion, source stream/run fence, Billing local epoch는 유지한다.
- Billing local epoch를 consumerRecoveryGeneration의 새 wire 의미로 재사용하거나 원격 상태만 숨겨 남기는 혼합 구현 금지.
- source availability는 원천의 존재 증거이지 Billing 반영 증거가 아니다. C 이하라고 모두 원 event를 처리했다고 간주하지 않는다.
- 원천 만료나 신규 snapshot으로 과거 GAP을 지우지 않는다. 잘못된 근거의 삭제·무기한 개인정보 보존 모두 금지한다.
- 정상 지연을 전체 판매 차단에 자동 연결하지 않되, 실제 capture/탈퇴 차단 실패·복원 중에도 무조건 판매를 계속한다고 쓰지 않는다.

## 5. 문서별 상세 수정 지침

### 5.1 공동 기술 계약 — 절별 교체표

| 위치 | 변경 방법 | 유지/주의 |
| --- | --- | --- |
| 요약·§3·§4 역할/검증표 | source 가용성은 Identity, consumer 적용/epoch는 Billing으로 표시. ACK 유실 검증 대신 local commit 불명/복원 검증 연결 | 보존기간·token 규격 유지 |
| §5.1 발급/탈퇴 | remote consumer coverage를 구매 gate 근거로 참조하는 부분만 새 조건에 연결 | 기존 발급 선형화·ACTIVE MEMBER·구 token/진행 요청 보호 유지 |
| §5.2.B cleanup | consumer ACK를 이용한 완료 판정을 제거. 삭제와 replayFloor/중간 unavailable interval을 원자 기록. Billing은 로컬 적용 범위와 대조 | 원천120일 고정, push delivery 상태를 feed 처리만으로 DELIVERED로 변경하지 않음 |
| §5.3.A cutover | 기준점·최초 snapshot/feed 연동 유지 | 과거 legacy COMPLETE 출시 면제는 기존 승인 범위만 |
| §5.3.B API | 원격3개 route/header 제거, snapshot4기능 유지 및 availability 추가 | 최종 경로/revision·성공/오류 DTO는 새 exact 참조 |
| §5.3.C snapshot | manifest/cursor/멱등 scope의 remote generation 제거, consumer/환경/stream/run 바인딩 명시 | 같은 T의 User/H·불변 rows/count/digest·5분 build·7일 expiry 유지 |
| §5.3.D 완료 판정 | local page commit → 전체 검증 marker/state baseline H → feed commit/C → H2 관측 완료로 교체 | BASELINE/FEED ACK와 remote confirmation 없음; baseline과 원event 적용 범위 분리 |
| §5.4 실패·재개 | 기존202 publisher 규격과204 pull 규격을 별도 표로 분리 |202 기존20회/관리 재개 정책을204 circuit 제안으로 바꾸지 않음 |
| §5.5 활성화/rollback | remote checkpoint 대신 local fence/index/호환성/검증된 baseline 사용. lag/GAP/restore별 영향 구분 | 모든 gate를 true로 대체하거나 무료/Identity 전체 health와 자동 결합 금지 |
| §5.6 source 복원 | 새 source stream·옛 endpoint 격리·durability 유지. Billing 새 baseline/epoch 절 연결 | 정상 failover와 restore 구분 |
| §5.7 consumer 복원 | 제목과 본문을 Billing local epoch/복원으로 교체. remote begin/CAS/ACK 삭제 | 구worker DB 접근 제거→새 local epoch→일관 C replay 또는 snapshot; remote 권한 없이 local 운영 권한 필요 |
| §6.1 schema | Identity remote3종 제외·snapshot operation index 개정, Billing 신규 후보 분리 | 구 manifest 자동 수정/운영 index drop 금지 |
| §6.2~6.3 테스트·담당 | 원격 ACK 시나리오 대신 local atomicity·epoch·restore·availability 시나리오, watchdog 책임 추가 | 기존 event/numeric/canonical/auth 회귀는 계속 유지 |

### 5.2 exact 문서·fixture

기존199 동결 명세와 실행/수락 기록은 역사적 버전으로 보존한다. 새 revision 문서를 별도로 작성하고 현재 기준 링크를 전환하는 방식을 권장한다. 새 fixture도 revision별로 구분하고 변경 전 fixture를 새 계약의 성공 증거로 보고하지 않는다.

- API 표: 유지4기능/삭제3개 route, generation header 제거, status/manifest/feed의 필수·null·생략·타입을 모두 다시 명시한다.
- manifest: remote generation만 제거하는지, source legacy/knownGaps와 availability 관계까지 명시한다. contentDigest 정규화 알고리즘은 유지한다.
- feed: H2/cursor 소유자·환경·stream·TTL 서명 유지. 마지막 page의 scanCursor non-null은 기존 ACK 용도였으므로 새 의미를 명시하고 단순 관성으로 생략하지 않는다.
- availability: 요청 범위에 필요한 prefix floor/중간 hole·관측 경계 포함. 구간 상한/16KiB 초과 처리,410 때 최소 정보 전달을 고정한다. silent truncation·빈 성공 금지.
- 오류: 원격 checkpoint/generation 전용 오류 제거, auth/strict/stream 교체/source gap·expiry 유지 및 정확 HTTP 코드 동결. 새 필드의 reader-first/버전 전환 전략 포함.
- index: Identity 원13개 중 remote3개 제거와 snapshot operation scope 변경을 새 manifest에 기록한다. 실제 남는 index 총수·이름·key order·unique/partial/TTL/collation은 manifest 검증으로 확정한다. Billing 기존16개는 유지하고204 신규5개 collection 후보는 별도 manifest로 둔다.

### 5.3 PLAN-009·ADR-004·결정서

- PLAN-009:201 완료 내용·public API·구매 계정·탈퇴 수신 계약은 유지.203을 source 조회,204를 local 복구,205를 local 증거+거래 대사로 표현한다. 후속 테스트에서 원격 ACK 확인을 삭제하고 새 exact revision 링크로 교체한다.
- ADR-004: 상품·환불·결제 원장 본문은 건드리지 않는다. 복구 관련 최신 결정에서 remote generation/ACK를 local epoch/checkpoint로 대체하고 변경 범위·발효 revision·구현/배포 미승인 상태를 명시한다.2026-10-07 승인 이력은 삭제하지 않는다.
- CONTRACT_DECISIONS: 방향 채택 기록에 이어 **새 exact 공동 수락 시에만** revision·수락 근거·영향 구간을 기록한다. 과거 기록을 나중 합의처럼 고쳐 쓰지 않는다.

### 5.4 purge·배포·작업 기록

- 탈퇴·purge 설계 §5.1/5.6: 새 source API/local 복구 책임과 revision 참조로 변경.
- §5.3~5.5:205가 local state baseline·원event 적용 범위·unresolved gap·provider 거래 확인을 사용한다고 명시. 단순 C 숫자 또는 CAUGHT_UP만으로 삭제 허용 금지. 충분한 증거와 대상 범위가 확인돼야 한다.
- §6: 원격 checkpoint 테스트를 local 증거 소실/동시 거래/복원 회귀로 교체.15일 자격·매시간 점검·매일 재확인·7일 담당 검토는 유지한다.
-206/PLAN-013/runbook: read4기능 IAM, local 운영 설치 권한, old writer/endpoint 격리, Billing 정지 watchdog,source expiry 경보, 구버전 rollback 금지 조건을 반영한다. 실제 이관/판매/삭제는 별도 승인이다.
- CURRENT_STATE는 현재 기준을 갱신하고 WORKLOG는 append만 한다. Jira 범위 변경은 별도 사용자 승인 후 수행한다.

## 6. 실행 순서·회신 형식·완료 기준

1. Identity가203/202 실제 구현·배포 여부와 영향 문서/port를 확인한다. 이 인계서는 그 상태를 확인했다고 주장하지 않는다.
2. 위 §3의 exact 미결 항목을 권장안·DTO 예시·manifest/fixture 변경표로 회신한다.
3. 양측이 새 revision/전환을 수락한 뒤 공동 계약 본문과 exact를 갱신한다. Billing 문서는 Billing 담당자가 수정한다.
4.203·204 계획/205·206 gate/결정서를 같은 revision으로 연결하고 관련 없는 과거 문서를 무차별 치환하지 않는다.
5. 문서 링크·구용어 잔존 맥락·예시의 null/타입·index 정합성을 확인한다. 구현 뒤에는 별도로 계약/replica-set/실제 연동 검증을 수행한다.

회신에는 **수락한 책임 분리, exact 미결 항목의 제안, 수정 파일/절, 기존 구현 재사용·변경 범위, 배포 호환, 테스트/남는 위험**을 포함해 주세요. 완료는 안내문만 추가한 상태가 아니라 활성 문서 본문과 새 exact/fixture가 같은 의미를 가리키는 상태다. 실제 코드·운영 검증 완료와 구분한다.
