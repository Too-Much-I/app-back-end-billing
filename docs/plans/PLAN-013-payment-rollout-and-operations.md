# PLAN-013: 결제·환불 연동 검증·배포·문의 운영·보존

- 작성일: 2026-10-06 / 상태: 출시 검증·운영 방향 사용자 승인(2026-10-06) / Jira 미생성
- 승인 범위: 실제 연동 검증, 안전한 데이터 이관, 단계별 활성화, 판매 중단과 기존 거래 처리 분리, 문의·보존·백업·경보 운영 방향. 미확정 기술 상세·운영 담당/권한·실제 배포/판매/환불 실행 승인을 대신하지 않으며 체크리스트 통과를 의미하지 않는다.
- 선행: [전체 로드맵](PLAN-008-fixed-term-payment-roadmap.md), PLAN-009~012. 실제 배포/Secret/환불 실행 권한을 이 문서가 부여하지 않는다.

## 1. 5줄 결론

1. 개발 완료 뒤 실제 Store/RC·Identity·LC·AWS staging gate를 통과해야 판매를 검토한다.
2. 신규 판매/입장과 구매 복구·환불 처리 flag는 분리한다.
3. schema v5는 additive reader-first로 적용하고 구버전 free-only writer로 무조건 rollback하지 않는다.
4. REFUND 분류·인증된 userId 귀속은 Identity 로컬 코드에서 확인됐으며 배포/프론트 연동은 후속이다. Billing은 구매·이용 증거와 검증된 원장 반영을 맡는다.
5. 보존·복구·대사·D2 운영 절차가 없는 상태를 완료라고 선언하지 않는다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 서비스별 인계/증거

| 대상 | 요구사항 | 완료 증거 |
| --- | --- | --- |
| Identity | 기존 audience/read 유지, ACTIVE MEMBER만 purchase; REFUND에 인증 userId 귀속/로그인 불가 예외 | JWT 실제 controller contract tests, 문의 분류/인증·owner 음성 테스트 |
| LC | paid source reader-first, reserve capability 계약, GRADING commit gate, revoke inbox/deny+삭제 일관성 | 모의·실제 staging 경합/duplicate/tombstone/삭제 테스트 |
| 앱 | RC standard SDK·stable reference 로그인/전환, PENDING sync, Store 가격·기간, 두 entitlement reader, REFUND/약관 | Android/iOS sandbox 각5종·계정 전환·앱 종료 테스트 |
| 운영 | refund role·문의 담당·2영업일 첫 응답·승인/결과 감사·예외 소유자 확인 | 합성 문의→구매 연결→권한 검증→처리/미처리 사유 추적 |

이 저장소에서 다른 서비스 코드를 바꾸지 않는다. 9/16 Apple 상품/Offering 등록과 Google 미완료 기록은 현 설정 검증으로 갱신해야 한다. Secret 원문·고객 거래·receipt를 Jira/문서/fixture에 복사하지 않는다.

## 3. 사용자가 결정해야 하는 사항

계획 승인과 별도로 운영자·최소 IAM 역할·환경 입력·프론트 최종 고지 검토·실제 sandbox 테스트/배포 승인이 필요하다. 법정 반환 기준·Store 예외를 담당자가 임의 무시하지 않는다. D2는 자동 복구가 아니라 검증된 증거/승인/중복 방지된 보상 entry로 처리할 별도 실행 runbook을 출시 전 승인받는다. 그전 일반 mutation endpoint나 원장 직접 수정은 제공하지 않는다.

## 4. 주요 위험과 미확인 사항

- RC HMAC·partial evidence 부족은 credential 설정만으로 해결됐다고 간주하지 않음. 부족 시008 Phase0로 돌아가 명시 재승인.
- public ALB exact path와 internal Lattice 경계를 실제 네트워크 negative test로 확인. 헤더 값만으로 신뢰 경계 판단 금지.
- 무료 owner epoch/source/guard legacy coverage가 없으면 rollback·activation 실패. 거래를 받은 이후 구버전 배포/DB drop을 롤백 수단으로 쓰지 않음.
- 사용자에게 2영업일 첫 응답을 Store 환급 완료 SLA로 약속하지 않음. Apple usage 공유12시간과 별개, 자동 Refund Control OFF 유지.

## 5. 현재 작업과 직접 관련된 구현·운영 순서

### Migration

1. 현 collection/index/legacy active group·Reservation/owner epoch read-only inventory.
2. 009~012의 schema v5 exact manifest 동결(신규 refund evidence/usage/rate limit index 포함). index name/key order/unique/partial/TTL mismatches fail-fast, dry run 보고.
3. source/access nullable reader를 먼저 배포하고 feature flag OFF. 신규 writer의 guard/source 경계를 준비.
4. 2026-10-06 F3 A안 승인: 최초 이관은 관련 writer의 짧은 제한/drain 뒤 bounded migration으로 legacy active guard/evidence backfill한다. reserve/confirm/cancel/expiry/event/owner writer를 함께 통제하고 이벤트는 유실 없이 재시도한다. 진행 시험 임의 취소 금지. 확실한 Trial provenance만 legacy TRIAL 분류. 불명은 drain/승인 이관 후 활성화. progress checkpoint/재시도/재검증을 제공하고 기존 index 자동 삭제 안 함. 실제 제한 시간·범위는 inventory와 dry run 후 운영 승인하며 긴 제한이 필요하면 온라인 이관을 별도 승인받는다.
5. snapshot·guard coverage·schema gate 통과 후 v5 전체 완료 선언. paid 기록 존재 뒤 rollback은 새 필드 이해 버전만 허용.

### 환경·활성화

서울 Region/같은 account·VPC, prod/staging cluster와 Lattice 환경 분리는 ADR-002 방향 유지, 현 인프라 존재를 추정하지 않음. ECS task role과 GitHub deploy/execution role 분리. prod/staging provider ingress URL·Authorization/HMAC·payload env를 분리하고 SANDBOX가 production 권리 생성 못 하게 함.

public connector/ALB allowlist는 `/api/v1/payments/products`, `/purchase-account`, `/sync`, `/entitlement`, `/revenuecat/events` 각각의 전체 경로와 method를 명시한다. 기존 `/api/v1/entitlements`는 유지. `/api/**` wildcard 확대 금지. internal direct task/public port/encoded slash/wrong method/다른 환경 principal 우회 테스트.

적용 순서: 모든 Billing flag OFF 배포→Identity/LC reader·consumer·전용 채점 승인 gate 배포→staging ingress/구매·복구·환불 processor/revoke publisher 준비→app sandbox sync→준비 조건 자동 검사 후 staging paid admission→환불/대사 시나리오 실행→전체 gate 검토→production 단계 승인. 환불 기능 활성화는 유료 입장보다 먼저이며 뒤의 환불은 테스트 실행을 뜻한다. 새 production 판매/구매 UI는 마지막에 활성화한다. Store 외부 환불과 진행 중 결제는 판매 OFF 뒤에도 도착할 수 있다.

2026-10-06 F4 A안 승인: 아래 최소 조건을 자동 활성화 gate로 검사하고 운영자의 최종 판매 승인을 병행한다. ON 설정만으로 상대 정상 동작을 증명하지 않으며 계약/E2E 증거와 경보를 함께 확인한다.

| 단계 | ingress/구매 worker/대사 | 환불 processor/전파 worker·publisher | LC deny·채점 승인 gate | paid admission | production 신규 판매 |
| --- | --- | --- | --- | --- | --- |
| 초기 비활성 배포 | OFF | OFF | 배포/검증 중 | OFF | OFF |
| staging 구매 복구 검증 | ON | ON·준비 확인 | 준비 확인 | OFF | OFF |
| staging 유료·환불 시험 | ON | ON | 준비 확인 | ON | OFF |
| production 준비 | ON | ON·준비 확인 | 준비 확인 | OFF | OFF |
| production 최종 승인 | ON | ON | 준비 확인 | ON | ON |
| 일반 판매/입장 장애 중단 | 유지 | 유지 | 기존 처리 유지 | OFF | OFF |

paid admission ON인데 환불 processor/publisher·LC 계약·schema/guard coverage 조건이 빠진 조합은 거절한다. 런타임 장애의 탐지·입장 차단 기준은 runbook에서 정하며 config 검사만으로 건강 상태를 보장하지 않는다. 인증 침해 시의 ingress 중지는 별도 사고 대응 절차를 따른다. 표의 staging/prod 설정과 credential은 상호 독립이다.

### 장애·rollback

신규 catalog 판매 노출/구매 진입·paid admission을 차단하되 paid reader·기존 sync·검증 ingress·reconciliation·환불/LC revoke 처리는 유지한다. 인증 침해 등 수신을 중지해야 하면 별도 incident 절차로 저장되지 않은 이벤트 재조회/대사·손실 구간을 기록한다. 일괄 flag OFF를 안전 rollback으로 간주하지 않는다. 기존 free 시험은 회귀 조건 내에서 유지한다.

### 보존·운영 조회

정규화 Purchase/entitlement/financial ledger/환불 business dedupe는 `max(providerFinalAt,entitlementEndsAt,lastReversalAt)+UTC calendar5년`, 실제 확정 시각 미상 시 기준을 무엇으로 보수적으로 유지할지 manifest에서 명시하고 조기 삭제 금지. inbox120일·terminal sync/outbox120일, 미해결은 단순 TTL 삭제 안 함. 연결 lookup hash/암호화 provider ref/erasable user 정보는 명시 worker로 제거. Trial claimedAt+3년·24시간 purge와 혼용 안 함.

최소 purchase usage summary와 개별 Session/Group lookup의 필요기간을 구분한 retention 표를 구현 전에 작성한다. 상담90일이나 학습 삭제가 금융 의무 보존을 무조건 삭제하지 않으며 금융5년이 원본 학습 개인정보 전체 보존 근거도 아님. 필요한 최소 ledger 증거 확보 뒤 lookup purge 테스트. 삭제 기록은 건수/성공 여부만. backup35일 rolling, restore 격리→현재 만료 purge→금융/deny/outbox 대사→트래픽 연결.

문의 운영자는 인증된 계정 및 선택된 purchase의 owner/environment를 검증하고 제한된 증거 query만 사용한다. 문의 본문/Slack에 결제 원문·토큰·전체 학습 결과를 추가하지 않는다. Store 실제 실행과 Billing 반영을 구분해 접수/판정/실행/확정/반영을 추적하며 일반 반복 환불 UI는 만들지 않는다. 별도 runner는 dry-run·정확 대상·승인 참조·idempotency·감사 조건으로 승인받고 raw DB 수정은 금지한다.

### 관측

UTC timestamp/service/environment/operation/outcome/durationMs/traceId/spanId. business INTERNAL spans는 실제 controller/worker 아래서 정상·예외 종료. 원문/사용자·구매·Session ID를 metric label/span attribute에 넣지 않음. pending15분, revoke outbox oldest60초,24시간 retry 소진,401/403 BLOCKED_AUTH 경보. 경보 수신자와 replay 결과를 실제 확인하며 설정 파일 존재만으로 완료 아님.

## 6. 부록 — 판매 gate 체크리스트

- [ ] PLAN 승인 및 Jira 범위 동결; 구현/회귀 테스트 결과 기록.
- [ ] 실제 product IDs/5기간/한국 가격·Store 심사·RevenueCat Offering·Google RTDN·Apple notifications/key 검증.
- [ ] SDK hint/owner mapping, HMAC 인증/재서명 retry, 환경 분리, 실제 provider 부분 금액·시각·중복 근거 확보.
- [ ] paid-first 무료 불변·동시 admission·purchase/refund/confirm/GRADING 경합 통과.
- [ ] iOS/Android 구매 직후 앱 종료·응답 유실·중복·누락 복구, 전액·부분 환불 후 단일 source 종료와 R2 지연 사례 통과.
- [ ] LC deny-before-session·Job gate·401/403·dead-letter replay·삭제 후 최소 증거 확인.
- [ ] 문의/사용자 확인·프론트 약관/동의·본인 결과 열람·실제 담당/권한·D2 복구 runbook 승인.
- [x] Identity 로컬 cc076442의 REFUND 분류·JWT/현재 활성 계정 userId 귀속·익명401/본문 userId400 및 테스트 코드 확인(2026-10-06). ACTIVE MEMBER/GUEST 모두 문의 허용, 구매 권한 아님.
- [ ] REFUND 실제 배포·접수/Slack flag·프론트 호출·대상 구매 연결 E2E. 기존 AUTH/GENERAL 익명 경로 및 이메일/Store 로그인 불가 예외 안내 유지.
- [ ] replica set/index/legacy coverage/backup restore·purge·Secret rotation·경보 수신·안전 rollback 확인.
- [ ] 운영자가 구체적 활성화 순서/중단 기준 승인. 외부 금융 작업은 별도 승인 하에 수행.

각 구현 단계는 `./gradlew clean test`를 실행한다. 이번 계획서 작성에서는 문서/링크 검사만 수행하며 실제 외부 테스트 성공을 주장하지 않는다.
