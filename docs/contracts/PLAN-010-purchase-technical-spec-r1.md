# PLAN-010 purchase-r1 — 구매·기간권·통합 조회 상세 기술 규격 초안

> 2026-10-13 TMI-211 구현 기준: [2차 계획](../plans/PLAN-010-phase-2-public-sync-and-entitlement-query.md)과 [구현 안내](../runbooks/TMI-211-public-sync-entitlement-query.md)의 sync-command-r1·202/200·조건부 CONTACT_SUPPORT·통합 paid/examAvailability/canTakeExam·분산 quota를 OFF 구현했다. 이 범위의 기술 후보 표기는 현재 코드/안내로 갱신한다. 신규 schema는 PaymentSyncSchema 명시 설치/검증이며 기존 core schema는 변경하지 않는다. 실제 provider/worker 미연결로 sync ON 기동은 거절, terminal120일 정리/미해결 보존·실거래 매핑·운영 활성화는 별도다.

> 2026-10-13 사용자 승인 보충: 아래 접수 중심 상태 관리·공통 background 처리/1초poll·고정 lookup 키 방향은 승인됐다. cleanup은 별도 규격의 단일 cleanupPolicyVersion 목표를 따른다. 나머지 DTO/schema/provider 미확인 사항과 구현·활성화 승인을 일괄 확정한 것은 아니다. 이전 별도 effect/5초tick/즉시 외부조회 권장안은 이 개정으로 대체한다.

- 작성일: 2026-10-13 / 브랜치: feat/TMI-209-billing-cleanup-foundation
- 상태: 사용자 요청으로 작성한 구현 검토용 권장안. 제품 정책은 기존 승인을 따르며 신규 DTO/enum/schema/flag는 이 문서의 기술 제안이다. 코드 구현·외부 증거 검증·Jira 생성·배포 승인 아님.
- 기준: [PLAN-010](../plans/PLAN-010-purchase-ingestion-and-timeline.md), [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md), [결정서](../codex/CONTRACT_DECISIONS.md), [205 cleanup 규격](TMI-205-cleanup-technical-spec-r1.md).

## 1. 5줄 결론

1. 검증된 구매 관측을 하나의 Purchase business key로 묶고 구매·기간권·ledger·timeline·처리 완료를 한 Transaction으로 반영한다.
2. 최초 지급은 ACTIVE 구매 계정과 검증된 원구매자 연결이 충족될 때만 한다. 앱 callback·alias·빈 API 응답은 증거가 아니다.
3. sync는 특정 구매 반영을 요청하고, 기존 `/api/v1/entitlements`는 무료·유료·시험 이용 가능성을 읽기만 한다. paid 전용 GET은 만들지 않는다.
4. 모든 실제 금융 입력부터 lifecycle 쓰기 CAS/pending 효과를 연결하여205 삭제와 경합시킨다. 탈퇴/삭제 후 늦은 거래를 버리거나 계정을 재생성하지 않는다.
5. 내부 fake 코어와 provider 실증을 분리한다.011/012/013 이전 판매·유료 입장,205 PR3/206 이전 실제 삭제는 활성화하지 않는다.

## 2. 반드시 읽어야 하는 내용

010은 Store 결제창 구현이나 Billing 자체 결제 실행이 아니다. 앱의 RevenueCat 표준 SDK가 구매·transaction completion을 담당한다. Billing은 검증·원장·기간 계산·상태 조회·누락 복구를 담당한다. paid 시험 승인/guard는011, 환불 금액/권리 종료/reflow/LC revoke는012다.

이 초안은 provider가 없는 필드를 제공한다고 가정하지 않는다. 과거 [공개 문서 조사](PAYMENT_OPERATIONS_PROVIDER_RESEARCH-2026-10-07.md)와 실제 프로젝트/SDK/Store fixture 검증은 다르다. provider 인증과 field 지원이 맞지 않으면 해당 adapter 연결을 중단하고 대안을 승인받는다. 내부 모델을 fake로 검증하는 것은 실제 구매 검증 성공이 아니다.

## 3. 사용자 결정과 기술 확정 경계

상품 1/3/7/14/28일, 가격, 자동 갱신 없음, paid-first/free-preserve, 계정별 stable ref, 재가입 권리 이전 금지, 환불 정책을 다시 선택하지 않는다. 다음 표는 구현자가 증거를 확인하고 닫을 기술 항목이며 새로운 승인 시스템을 만들자는 뜻이 아니다.

| 항목 | 이 초안의 권장안 | 실제 연결 전 필요한 근거 |
| --- | --- | --- |
| 인증·거래 식별 | ADR Authorization+raw HMAC/서명 시간·RC 검증 구매만 신뢰 | 현재 문서/실프로젝트 지원·서명 bytes·iOS/Android SDK→RC→Store ID 대응 |
| API/DB | 아래 DTO/enum·BSON·멱등 효과/index subset | 프론트 수락·schema manifest/fixture·기존009/205 호환 테스트 |
| 탈퇴 후 최초 관측 | 금융 증거 최소 보존·REVIEW_REQUIRED, 자동 신규 권리 없음 | 기존 owner/reference/구매 시각 증거. 기존 정책으로 결론이 안 나면 개별 정책 검토, 자동 환불/재지급 금지 |
| 미구매 부재 증명 |010의 조회 완료와205의 삭제 허용은 분리 | 미관측 Store 거래/페이지/계정/ref 범위 및 token/request drain 검증 |
| retention | 정규화 금융5년·provider inbox120일·backup35일 유지 | 미결 case/command 최소정보 보존 종료·법적/운영 검토. 미결을 임의5년/무기한 보관으로 확정하지 않음 |
| 판매·삭제 | 모든 신규 flag OFF |011/012/013·205 PR3/206 완료 근거와 별도 활성화 승인 |

## 4. 주요 위험·미확인

- webhook account ID는 last-seen일 수 있다. current alias나 client userId만으로 원구매자를 정하지 않는다.
- 동일 event의 DB 처리 상태/현재 account binding을 digest에 넣으면 재전송이 conflict가 된다. digest는 수신한 불변 의미에만 의존한다.
- lifecycle을 단순 조회만 하면 삭제와 write-skew가 생긴다. 최소 inbox 수신부터 같은 lifecycle에 쓰기 CAS가 필요하다.
- 구매 record를 찾았다는 것과 사용 가능한 기간권이 있다는 것은 다르다. 처리/금전/권리 상태를 분리한다.
- 앱의 요청 실패·Store pending·RC404·empty는 미구매/환불 확정이 아니다. 권리 부여나 삭제의 근거로 쓰지 않는다.
- 무료 top-level status를 통합 status로 바꾸면 기존 앱 의미가 깨진다. 기존 필드 유지·추가 영역 호환 검증이 필요하다.
- 늦은 구매를 확인하고 현재시각부터 새 기간을 주면 승인된 구매시각 기준과 어긋난다. 이미 종료된 기간도 원장에는 기록한다.

## 5. 구현 규격

### 5.1 역할과 불변식

`VerifiedPurchaseObservation`은 인증·환경·상품·거래 key 검증을 마친 내부 타입이다. HTTP request DTO에서 직접 생성하지 않는다. source는 RC_WEBHOOK/RC_API, 종류는 OWNED/REFUNDED/REQUIRES_LOOKUP/IGNORED로 나눈다. Google Orders는012 부분 환불 보완용이지 신규 구매 지급 source가 아니다.

서비스 분담:

- SyncService: 검증된 JWT 사용자·멱등 키→durable command, 상태 응답.
- ProviderIngress: 인증/strict bounds→불변 관측 digest·inbox 접수, fast ack.
- PurchaseObservationService: owner/catalog/transaction 검증·이미 처리된 효과 판별.
- TimelineService: account sequence/기간 계산·Purchase/기간권/ledger 원자 반영.
- ReconcileWorker: 최소 암호화 reference로 provider 재조회·누락 복구.
- UnifiedEntitlementReader: 무료 reader와 paid projection을 같은 조회 snapshot에서 읽음.
- PaymentEvidenceWriter: 입력 접수/해소의 lifecycle CAS·pending 멱등성,205 LocalReader 연결.

새 SDK 의존성은 도입하지 않는다. fake adapter는 test source에만 두고 runtime port 미설치는 OFF 또는 명시적 미준비 실패다.

### 5.2 타입·정규화·식별자

| 값 | 권장 규격 |
| --- | --- |
| Billing 생성 ID | lowercase UUIDv4 string; userId는 기존 Identity canonical UUID 계약 유지, 임의v4 제한 추가 금지 |
| environment/store | SANDBOX/PRODUCTION, APP_STORE/PLAY_STORE. adapter는 실제 provider enum을 명시 매핑 |
| 시각 | UTC Instant, 저장 BSON Date(ms). 구매 ms를 임의 현재시각으로 대체 금지. 계산 overflow/비정상 미래시각은 review |
| version/sequence/duration/attempts | BSON int64. version/sequence>0, attempts≥0, MAX 증가 거절. wire가 필요한 수치는 안전 범위/형식을 해당 DTO에 명시 |
| 금액 | Decimal128 + ISO 통화 + amountBasis/source; finite decimal만 허용. unknown=null,0과 구분; 상품 표시가격으로 실제 결제액 추정 금지 |
| 실제 provider 식별자 | 원본 opaque 문자열, 숫자 coercion/대소문자/공백 임의변환 금지; 로그/응답에 노출하지 않음 |
| lookup key | ADR의 별도 lookup HMAC key. versioned canonical tuple의 UTF-8 byte를 입력으로 사용, 문자열 이어붙이기 delimiter 충돌 금지 |

transactionKey 입력 tuple은 `(environment,store,appId,verifiedStoreTransactionIdentifier)`다. event key에는 provider/project/환경/eventId를 포함한다. RC purchase resource ID는 별도 rcPurchaseKey이며 Store transaction key를 대체하지 않는다. Google order identifier가 없으면 client token으로 우회하지 않는다.

lookup hash는64자리 lowercase hex를 제안하고 keyVersion을 별도 보관한다. keyVersion을 business unique tuple에 넣어 같은 거래의 중복 지급을 허용하지 않는다. 초기에는 안전한 비밀 설정의 고정 key 하나를 사용하며 자동/무중단 교체 기능은 구현하지 않는다. 유출 등으로 교체가 필요하면 관련 writer 중단·입력 유실 방지·기존 데이터 이관·중복/조회 검증·재개 절차를 별도 수립한다. 설정값만 교체하지 않으며 기존 key는 이관 전 성급히 폐기하지 않는다. 사용자/workload JWT 키 정책 변경이 아니다. crypto 세부 포맷은 승인된 환경 key 관리와 함께 adapter 단계에서 동결한다.

provider 재조회 reference는 최소 암호화 값만 저장한다(AEAD envelope의 algorithm/keyId/nonce/ciphertext/tag와 environment/record-purpose AAD 제안). 실제 secret/receipt/JWS 원문 저장 금지. 기존 purchaseAccountRefId 자체 필드 암호화 생략 승인은 그대로이며 provider token/reference 암호화 규칙을 제거하는 근거가 아니다.

### 5.3 Purchase·기간권 모델

필드 표에서 `?`는 명시 null 허용, 그 외 필수다. worker의 lease/nextAttemptAt/purgeAt 같은 조건부 필드는 해당 상태가 아닐 때 unset이며 null과 혼용하지 않는다. 운영 validator에서는 타입/상태별 필수 관계를 함께 검사한다.

| collection | 필수/조건부 데이터 |
| --- | --- |
| purchases | _id,environment,store,appId,transactionKey,lookupKeyVersion,accountId?,ownerRefId?,rcPurchaseKey?(없으면 unset),purchaseState,accessState,processingStatus,version,firstVerifiedAt,lastVerifiedAt,retentionExpiresAt?; purchasedAt?/productSnapshot?/money?는 증거 불명/환불 선행이면 null; encryptedRefs는 확인된 최소 값만 |
| subscription_entitlements | _id,purchaseId,accountId,sequence,durationSeconds,originalStartsAt,originalEndsAt,startsAt,endsAt,revokedAt?,version. 모든 기간은 [start,end) |
| payment_timelines | _id=accountId,accountId,environment,nextSequence,timelineEnd?,version; sequence 최초1, successful append 때만 증가 |
| payment_ledger | _id,effectKey,type,purchaseId,entitlementId?,occurredAt,sourceKind,sourceObservationKey,immutable detail. 실제 금액/기간 snapshot만, 원문·token 없음 |

`productSnapshot={offerCode,durationSeconds,catalogVersion,storeProductId,rcProductId}`를 최초 지급 때 고정한다. saleEnabled=false는 과거 구매 검증을 막지 않으며 과거 매핑을 새 상품으로 재해석하지 않는다. 기간 초는86400/259200/604800/1209600/2419200이다.

권장 상태 분리(012와 함께 구현 전 enum 동결):

- purchaseState: VERIFIED 또는 REFUNDED/REVOKED의 검증된 거래 상태. 증거 부족은 null 허용하고 운영 처리 상태로 표현한다. REFUND_REVIEW를 provider의 가상 event로 만들지 않는다.
- accessState: NOT_GRANTED/ALLOWED/REVOKED. 최초 지급 전/owner 불명은 NOT_GRANTED. 환불 선행은 REVOKED. 실제 기간 활성은 accessState+기간에서 계산한다.
- processingStatus: READY/NEEDS_LOOKUP/REVIEW_REQUIRED/BLOCKED_AUTH. 이것을 금전 상태로 사용하지 않는다.
- monetaryStatus 제안: KNOWN/UNKNOWN/PARTIAL_REFUND/REFUND_AMOUNT_UNKNOWN.010은 검증된 구매액/미상만 저장하고012 이전 부분 환불액 계산·누적을 구현하지 않는다.

기존 원장 overwrite 금지. `PURCHASE_VERIFIED` effect는 purchaseId당1개이며 기간권도 purchaseId unique다. immutable 관측과 최초 지급 snapshot이 다르면 기존 기간을 재작성하지 않고 review한다.

### 5.4 최초 지급 Transaction

외부 provider I/O는 Transaction 밖이다. 동일 관측을 처리하는 Transaction은 다음 순서로 수행한다.

1. dispatch 시 캡처한 복구 epoch, 업무 입력용 control 쓰기 fence 확인. 전체 복구 ALL_BLOCKED이면 중단한다.
2. 원구매자 reference→기존 account/lifecycle 관계, 현재 상태/버전, catalog와 거래 business key를 검증한다. account/ref 자동 생성 금지.
3. 이미 처리된 transaction이면 기존 effect/기간권으로 수렴한다. 다른 owner/product/immutable purchase time 충돌은 review이며 다른 계정에 지급하지 않는다.
4. 최초 지급은 ACTIVE lifecycle 쓰기 CAS, account timeline 쓰기 CAS, 최초 sequence 확보. WITHDRAWN/미매핑은 §5.7로 분기한다.
5. start=max(purchasedAt,기존 유효 timeline 끝),end=start+duration. now로 시작을 밀지 않는다. 미래 별도 구매가 먼저 반영됐어도 나중 관측을 구매시각으로 재정렬하지 않는다. ADR의 최초 VERIFIED commit sequence 기준을 따른다.
6. Purchase+단일 entitlement+PURCHASE_VERIFIED ledger+timeline root와 담당 입력의 완료/pending 해소를 같은 Mongo snapshot/majority+j Transaction에 기록한다.
7. commit 확인 후만 APPLIED. unknown commit은 동일 session commit 재시도/기존 business effect 확인; 새로운 sequence/idempotency key로 보상 지급 금지.

기존 PaymentStore의2초 bounded transaction을 우선 재사용한다. identity 복구 control와 별도로 금융 입력의 epoch fencing이 필요하므로 명시 PAYMENT_INPUT/PAYMENT_APPLY writer 후보를 추가한다. RECOVERY/PUSH 권한을 가장하지 않는다. OPEN은 허용, PURCHASE_BLOCKED에서는 durable 관측 접수·기존 금융 사실 보존만 허용하고 **신규 권리 지급은 보류**한다. 판매 OFF와 금융 사실 수신 OFF를 동일시하지 않는다. 정확 barrier 테스트/manifest 호환 후 구현한다.

### 5.5 중복 처리와 입력 모델

| 입력 | idempotency/business key | 같은 입력 재전송 |
| --- | --- | --- |
| sync | environment+JWT userId+Idempotency-Key | 같은 store/hint 의미면 같은 operation, 다르면409 |
| webhook | environment+providerEventKey | 같은 immutable semantic digest면200, 다르면409 |
| RC API 관측 | verified transactionKey + 검증 관측 의미 | API 호출 횟수를 구매 횟수로 세지 않고 기존 Purchase/effect와 비교 |
| 지급 | purchaseId + PURCHASE_VERIFIED | unique effect/entitlement로 한 번만 지급 |

digestVersion=1은 source schema/event의 소비하는 알려진 필드·명시 null을 canonical JSON으로 직렬화해 SHA-256한다. JSON 순서/whitespace·unknown optional field·delivery signature timestamp는 제외한다. 검증 전 받은 불변 owner ID는 포함할 수 있지만 현재 DB binding 결과·처리 상태·조회 시각·재시도 횟수는 넣지 않는다. 안정적 request digest에는 provider hint를 평문 보관하지 않으며 digest/암호화 참조도 개인정보 연결정보로 관리한다.

권장 입력 모델:

- payment_sync_commands: _id,environment,userId,operationId,payloadHash,hintEncrypted,state,attempts,version,purchaseId?,errorCode?,createdAt; 처리 중 nextAttemptAt와 lease 조건부. state=PENDING/PROCESSING/APPLIED/REJECTED/REVIEW_REQUIRED/BLOCKED_AUTH. public DTO로 운영 상세를 그대로 노출하지 않는다.
- payment_provider_inbox: _id,environment,providerEventKey,digestVersion,digest,eventType,normalizedObservation,state,attempts,version,receivedAt,purgeAt. normalizedObservation은 allowlist만, raw JSON 없음. state=RECEIVED/PROCESSING/APPLIED/IGNORED/QUARANTINED/BLOCKED_AUTH.
- 기존 payment_reconcile_cursors: ACCOUNT_LOOKUP kind/lastActivityAt/unique index를 유지한다. 추가 필드 environment,version,lastSuccessAt?,cursorEncrypted(조건부),lease(조건부)는 legacy reader-first 이관한다. activity와 provider scan 진행은 별개이며 GET entitlement는 갱신하지 않는다.

미해결 command는 TTL 삭제하지 않는다. provider inbox는120일 보존이므로 unresolved 증거는 만료 전에 아래 durable case에 최소 정보로 인계하며 pending count를 조용히 감소시키지 않는다. 모든 관측/사용자 연결의 보존을 무기한으로 확정하지 않는다.

### 5.6 205 접수 기록 중심 pending 연결 — 단순화 승인

범용 `payment_evidence_effects` collection은 만들지 않는다. sync command/provider inbox/독립 reconcile 작업의 접수 row가 처리 상태를 소유한다. 해당 row에 lifecycleId와 counted를 두어 pending 가산·해소를 한 번만 수행한다. 독립 reconcile 입력의 durable row 형태는 cursor의 진행 상태와 혼동하지 않도록 schema 동결 시 정한다. 미매핑 또는 inbox 만료 전 인계가 필요한 예외만 별도 durable case로 남긴다. case의 exact schema/index·보존 종료는 후속 기술 확정 항목이며 삭제 proof collection/TTL을 재사용하지 않는다.

- mapped durable 최초 접수: source unique 등록/counted=true 설정+같은 lifecycle.version 증가+pendingEvidence+1을 같은 Tx로 commit한다. duplicate는+1하지 않는다.
- 처리 완료: exact source row의 미완료/counted=true 상태를 terminal/counted=false로 CAS하고 lifecycle.version 증가/pendingEvidence-1을 같은 Tx로 수행한다. 구매 기록/기간권이 생성돼야 하는 입력이면 그 효과와도 같은 Tx다. 음수/누락·다른 lifecycle은 오류다.
- review로 이동해도 미해결 증거가 남으면 counted를 해소하지 않는다. provider inbox→case 인계는 동일한 추적 key로 멱등 처리하며 원 source의 counted 해제와 case의 counted 설정을 같은 Tx에서 수행한다. 이때 pending 총수는 불변이며 인계 완료 전 TTL로 원본이 소실되지 않도록 운영한다.
- 같은 거래의 sync와webhook이 각각 입력을 접수하면 둘은 서로 다른 pending 입력일 수 있다. 거래 지급 effect는 하나이며 각 입력 terminal만 한 번씩 해소한다.
- account 불명/삭제 후 입력: durable case로 격리, account/ref/lifecycle upsert 없음. 전체 영향 범위가 불명한 case는205의 해당 환경/app coverage를 불명으로 제한하며 안전한 pending0으로 숨기지 않는다. 매핑이 나중에 검증되면 case/새 연결/pending을 원자적으로 귀속한다. 추정 userId 매핑 금지.
- 205 LocalReader는 Purchase/ledger/기간권·sync/inbox/reconcile/case/cursor의 미처리/lease·ref를 실제 조회한다. 필수 collection 미설치=UNKNOWN. Purchase 존재=금융보존이며 미결 입력=보류다. pending0만으로 삭제하지 않는다. provider 전체 scan 성공은 ABSENT_OBSERVED일 뿐205 삭제 coverage 확인을 대체하지 않는다.

삭제와 먼저 commit한 쪽이 이긴다. 입력 먼저면 lifecycle version/pending이 바뀌어 proof가 무효이고, 삭제 먼저면 입력은 미매핑 case로 보존한다. 모든 실제 writer의 위 경합 테스트가 완료되기 전 purge flag는 켤 수 없다.

### 5.7 탈퇴·늦은 거래 판정표

| 상태 |010 처리 | 금지 |
| --- | --- | --- |
| ACTIVE +검증된 원구매자/현재 ref | 최초 구매 기록·기간권 지급 가능 | client hint만으로 지급 |
| 기존 Purchase 확인 +탈퇴 | 해당 기존 거래의 검증된 후속 사실 보존/필요 case; 신규 이용권 생성하지 않음 | 원거래 재지급·다른 user 이전 |
| WITHDRAWN +처음 발견한 과거 구매 | owner/구매 증거가 충분하면 정규화 금융 사실 기록, access NOT_GRANTED·REVIEW_REQUIRED; 부족하면 case 격리 | ACTIVE 복귀·기간 재시작·자동 환불 |
| 탈퇴 이후 시각의 구매/시각 불명 | 금융 증거 최소 보존·review, 시각만으로 정당/부당 결제 단정 금지 | 현재시각으로 구매시각 대체 |
| inactive reference/alias/TRANSFER | 기존 transaction 조회용으로만 활용, 최초 지급은 격리/재검증 | 새 계정 소유 추정 |
| account/ref 이미 삭제 | durable 미매핑 case | ref/account/lifecycle 복구 upsert |
| 동일 번호 재가입 | 새 user/account는 별개, 과거 거래 자동 연결 없음 | 전화번호로 복원/무료 재지급 |

이 표는 탈퇴한 사용자의 금융 사실을 버리지 않는 기술 처리다. 개별 환불 가능성/금액·미지급 보상 결정은 기존 문의·팀 검토·Store 결과 정책을 따른다. review를 자동 지급/보상 승인으로 사용하지 않는다. 원거래 과거 이용권이 아직 남아 있는 경우의 기존 탈퇴 차단과 별개로 기간을 신규 생성하지 않는다.

### 5.8 sync API 후보

`POST /api/v1/payments/sync`, JWT billing:purchase+MEMBER+Billing ACTIVE lifecycle, body/query/header의 userId 금지. Idempotency-Key는 lowercase UUIDv4. content-type JSON, body16KiB 이하, query 없음. request는 정확히 `{ "store":"APP_STORE", "transactionId":"test-store-transaction" }` 두 필드, unknown/duplicate/trailing/coercion 거절. hint는1~255 opaque string, Google purchaseToken/receipt를 받지 않는다.

operationId는 header 값을 그대로 사용하고 같은 사용자/환경에 결속한다. DTO의 purchaseStatus는 VERIFIED/REFUNDED/REVOKED 또는 null(대기) 제안; partial 금액은 public access 판단용 status로 합치지 않는다. 012 monetary 상세를 여기에 임의 추가하지 않는다.

```json
{"isSuccess":true,"code":"SUCCESS","message":"요청에 성공했습니다.","result":{"operationId":"11111111-1111-4111-8111-111111111111","status":"PENDING","purchaseId":null,"purchaseStatus":null}}
```

위는 durable 접수 후 HTTP202, Retry-After:5. 아래는 HTTP200 예시이며 message는 기존 PublicResponse.success 구현을 재사용한다(문구 재정의 금지).

```json
{"isSuccess":true,"code":"SUCCESS","message":"요청에 성공했습니다.","result":{"operationId":"11111111-1111-4111-8111-111111111111","status":"APPLIED","purchaseId":"22222222-2222-4222-8222-222222222222","purchaseStatus":"VERIFIED"}}
```

명백한 오류는 PENDING으로 감추지 않는다.400 INVALID_REQUEST,401 UNAUTHENTICATED,403 FORBIDDEN/PAYMENT_ACCOUNT_WITHDRAWN,409 IDEMPOTENCY_KEY_CONFLICT/PURCHASE_BINDING_CONFLICT,422 UNSUPPORTED_PRODUCT/UNSUPPORTED_CONTRACT,413/415,429 RATE_LIMITED,503 BILLING_TEMPORARILY_UNAVAILABLE. user-safe 고정 메시지/result=null·no-store,429/503 Retry-After. 저장 실패는202가 아니다.

장기 검토/인증 장애의 public 표현은202 PENDING을 유지하되 장기 대기를 숨기지 않도록 `processingHint=RETRY_LATER|CONTACT_SUPPORT` 선택형 추가 후보를 별도 프론트 검토한다. exact 확정 전 기존4필드 DTO에 몰래 추가하지 않는다. 명백한 owner mismatch는409다. APPLIED 재요청은 current 거래 상태를 읽고 지급을 재실행하지 않는다. 탈퇴한 JWT의 재요청은 account 상태 검사에서403이며 원장 작업 자체는 유지한다.

rate limit은 기존 분산 고정1분 방식을 재사용: sync12회/분/user·environment.429 Retry-After는 현재 bucket 종료까지 남은 정수초(최소1). GET 통합 entitlement는60회/분, 무료/paid를 별도 quota로 중복 부과하지 않는다.

### 5.9 통합 entitlement 응답 후보

`GET /api/v1/entitlements`, billing:read·검증된sub, Guest/MEMBER 본인조회, body/query 없음. 기존 envelope 및 asOf/consistency/status/benefits의 타입·의미는 불변이다. 특히 top-level status는 기존 무료 projection 상태이며 유료 포함 종합상태로 바꾸지 않는다. 추가 영역은 **paid**, **examAvailability** 두 개다. 확장OFF일 때 둘 다 생략, ON일 때 둘 다 필수다. field 부재를 권리 없음으로 해석하지 않는 프론트 계약을 요구한다.

| 추가 영역 | 필수 필드와 null 의미 |
| --- | --- |
| paid | status=READY/PENDING; active=boolean 또는 PENDING이면null; activeOfferCode/activeEndsAt/continuousAccessEndsAt/nextStartsAt는 string/Instant 또는null; hasPendingSync=boolean, 해당 저장소 실패는503 |
| examAvailability | canTakeExam=boolean 또는null; action=ALLOWED/BLOCKED/PENDING; newAttempt/retake=같은enum; preferredSource=PAID/FREE_NEW/FREE_RETAKE 또는null; hasInProgress=boolean 또는null; reasonCodes=문자열 배열(빈 배열 허용) |

2026-10-13 사용자 요청으로 canTakeExam 요약을 추가한다. action의 ALLOWED→true/BLOCKED→false/PENDING→null로 파생하는 안이며 판단 불명을 false로 숨기지 않는다. 현재 새 시험 시작 또는 승인된 재응시 가능 여부이고 진행 중 시험 존재/기록 열람 여부가 아니다. reserve 최종 판정·기존 무료 필드 의미는 불변이다. 2차 계획의 null/호환 fixture로 검증하며 실제 코드 변경은 후속이다.

reasonCodes 후보: IN_PROGRESS,GRADING_IN_PROGRESS,RESERVATION_PENDING,NO_ENTITLEMENT,ELIGIBILITY_UNKNOWN,OWNER_LINK_UNRESOLVED,RECONCILIATION_PENDING,PAID_STATE_UNKNOWN,ADMISSION_NOT_READY. 문자열 literal은 구현 fixture에서 동결한다. FREE_RETAKE는 원 Session 이어풀기가 아니라 기존 consumption의 새 replacement다. 여러 혜택/그룹을 하나로 줄이지 않고 상세 선택은 기존 benefits/attemptGroups에 남긴다.

추가 영역만의 예시(기존 무료 result 필드 옆에 추가, 전체 HTTP 응답 아님):

```json
{
  "paid": {
    "status": "READY", "active": true, "activeOfferCode": "PREMIUM_7D",
    "activeEndsAt": "2026-10-20T00:00:00Z",
    "continuousAccessEndsAt": "2026-10-20T00:00:00Z",
    "nextStartsAt": null, "hasPendingSync": false
  },
  "examAvailability": {
    "canTakeExam": true,
    "action": "ALLOWED", "newAttempt": "ALLOWED", "retake": "BLOCKED",
    "preferredSource": "PAID", "hasInProgress": false, "reasonCodes": []
  }
}
```

asOf는응답 전체 하나이며 같은 read-only snapshot에서 무료/paid/진행 projection을 읽는다. 외부 provider 조회·cursor activity 갱신·TTL 처리·계정 생성 없음. 하나의 저장소 장애를 부분 정상 응답으로 숨기지 않고503한다. 기존 무료 PENDING과 paid READY가 동시에 있을 수 있다. active=false는 확인된 비활성일 때만 사용하고 미준비는null/PENDING이다. active가 없으면 activeOfferCode/activeEndsAt/continuousAccessEndsAt=null, 다음 slot이 있으면nextStartsAt만 지정한다.

| 조회 시점 상황 | paid/무료 표시 | 시험 이용 요약 |
| --- | --- | --- |
| paid 유효·guard확인·진행없음 | 기간 표시, 무료 수량 보존 | ALLOWED,PAID 우선 |
| paid 없음·무료 신규 가능 | active=false,기존 수량 | ALLOWED,FREE_NEW |
| 새 무료0·기존 그룹 재응시 가능 | availableQuantity는0 유지,retake ALLOWED | ALLOWED,FREE_RETAKE; UI는1회 남음 표현 가능 |
| 실제 진행 중 Session/GRADING·진행 요청 존재 | 보유 권리 자체는 유지 | 새 시작 BLOCKED 또는 진행 정합성 불명 PENDING; 그룹 OPEN만으로 재응시 차단 금지 |
| 모든 권리 없음이 확인됨 |0/비활성 | BLOCKED,NO_ENTITLEMENT |
| paid/owner/guard판정 불명 | unknown을0/false로 바꾸지 않음 | 안전한 경로 전체 조건을 확인하지 못하면PENDING |
|011 공통guard/paid admission 미준비 | paid 기간 조회 자체는 가능 | paid 시작 ALLOWED 금지,ADMISSION_NOT_READY |

PAID 보유 사실만으로 action을 ALLOWED로 만들지 않는다. 긍정 판정에는 선택된 경로의 자격과 공통 진행/guard 조건이 모두 필요하다. 불명인 다른 경로 때문에 확인된 안전 경로까지 무조건 막지는 않되, 진행/owner 등 공통 조건이 불명하면PENDING이다. preferredSource가null이면 프론트가 source를 추측하지 않는다. 여러 가능한 무료 그룹을 '합계 응시권'으로 더하지 않는다.

위 '확인된 안전 경로'는 우선순위 조건까지 검증된 경우다. 유료 상태가 불명이면 신규 무료권을 대신 소모하도록 FREE_NEW를 권장하지 않는다. paid active가 확인됐으나 paid admission이 미준비인 경우에도 무료 차감 fallback으로 우회하지 않는다. 반대로 유료/공통 guard 조건이 확정됐으면 무료 자격 불명만으로 유료 이용 가능 판정을 막지 않는다. 기존 그룹의 재응시 우선순위는011 resolver와 일치시킨다.

010은 통합 reader의 정보 조립·nullable/호환을 구현하고,011 공통guard/resolver와 동일한 권한 판정 함수/계약 테스트를 연결하기 전 신규 paid admission의 ALLOWED를 노출하지 않는다. 최종 reserve는 조회 시점 이후 상태 변경을 다시 판정한다. 프론트는전화번호 인증/구매sync완료/시험시작·중단·완료/재가입반영 후 재조회한다.

### 5.10 provider ingress·REST·재시도

webhook 전용 경로는 `/api/v1/payments/revenuecat/events`. 사용자 JWT/workload principal로 우회 불가. ADR의256KiB·UTF-8·압축거절·Authorization+raw HMAC·±300초delivery시간·depth32/string16KiB를 유지한다. 원문은 요청 검증 메모리 범위에서만 다루고 저장/로그/trace에 남기지 않는다. duplicate/trailing/coercion 거절, unknown optional은 버리고 authenticated unknown type은durable ignored200. TEST와missing environment/app의 안전한 비금전 처리는adapterfixture로 분리한다.

HTTP200은최소inbox/pending commit 확인 후, 충돌409·잘못된계약422·일시저장실패503. lookup이 필요한 관측은원문을저장하지말고최소encryptedref와needslookup상태만저장한다. provider알림수신은기존paid권리판정과분리한다.

RC REST는ADR §5.6의v2endpoint 후보·host/project/customer/path allowlist를실문서/fixture와대조한뒤연결한다. connect2초/response5초,response1MiB,page100,redirect금지. pagination완료이전빈페이지/예산종료는완료아님. next_page URL을그대로따라가지않고검증된cursor만사용한다.

합산240회/분·동시4는초기서버budget,분산인스턴스/환경합산으로제어한다. 실제 provider 허용량은 별도 확인하며 이 수치를 공식 quota로 간주하지 않는다. SDK public key·v1key·임의추가key로quota우회금지. 신규 sync는 durable 접수 후202로 반환하고 요청 thread에서 외부 조회하지 않는다. 이미 검증·반영된 거래는 기존 사용자/환경/거래 귀속을 확인한 뒤200으로 반환할 수 있다. sync/webhook/reconciliation은 공통 background 처리 코어를 사용하되 각각의 인증·입력 멱등성은 유지한다. 정상 webhook 증거가 충분하면 불필요한 REST 재조회 없이 정규화한다.

worker:lease30초,token/version/epoch CAS,초기1초poll(설정 가능)·batch최대100; 5초~1시간 bounded jitter,실패기간24시간뒤DEAD_LETTER/운영검토(미해결 입력을삭제하지않음).401/403은BLOCKED_AUTH,429/503의유효Retry-After는기본backoff보다늦은시각을존중한다. 잘못된header/장기대기 exact 처리는provider공통fixture로확정하며Identity push/204 pull retry값을복사하지않는다. oldlease/epoch는결과쓰기불가. API직후crash는동일key재조회로수렴한다.

1초는 DB에서 실행 시각이 된 작업을 확인하는 주기이며 외부API 재시도/앱 polling 주기가 아니다. due index와 limit으로 bounded 조회하고 빈큐에서 provider 호출·주기적 heartbeat 갱신·업무 Tx를 만들지 않는다. 실행 중 tick은 겹치지 않으며 다중 인스턴스는 lease/CAS로 선점한다. 실행시간/적체/외부 반영 지연 때문에1초 내 시작이나 완료를 보장하지 않는다. staging에서 실제 predicate/sort의 explain, examined keys/docs, idle/backlog·다중 인스턴스의 DB CPU/latency·lease 충돌·queue age를 측정하고 부하에 따라2초/5초로 조절한다. 현재 측정/구현은 미완료다. 앱 Retry-After:5와 sync12회/분은 유지하므로 과거 앱2초 재조회 가정의2~5초 예시를 SLA로 사용하지 않는다.

reconciliation은PENDING5분,ACTIVE/SCHEDULED6시간,terminal최근90일일1회라는ADR초기값을유지한다.90일은5년금융보존/환불신고조회한도가아니며오래된거래조회는012와연결한다. ACCOUNT_LOOKUP은최근활동우선+bounded전체순환으로callback유실을복구한다. RC에아직기록되지않은Store거래까지복구됐다고주장하지않는다.

### 5.11 012 이전 환불·terminal 보호

refund-first의 검증된 transactionKey를 Purchase deny/tombstone으로 보존한다. purchaseState는 검증된 REFUNDED/REVOKED, accessState는 REVOKED다. account/product 불명은 null·REVIEW_REQUIRED이며 key 없는 신호는 case 격리다. 뒤늦은 owned와 REFUND_REVERSED는 자동 지급/복구하지 않는다.

이미테스트지급된구매에refund가오면durable REVIEW_REQUIRED로수신하고012미준비를명시한다.010만으로금액원장·reflow·시험차단이완료됐다고표시하지않는다. 실제판매·paid admission은012processor/revoke경로준비전계속OFF여야한다. 부분환불금액/누적식별은012전용모델로확정한다.

### 5.12 schema/index·보존·이관

신규독립subset `payment-purchase-r1`(manifest는구현시작성)을권장한다. 기존foundation/withdrawal/recovery/cleanup-r2를전체결제v5완료로승격하지않는다. 기존index이름/keyorder/partial/TTL불변,아래는모두ascending·simple collation·sparse/hidden없음이다.

| collection/index | key순서 | option |
| --- | --- | --- |
| purchases/ux_purchase_store_transaction | environment,store,appId,transactionKey | unique |
| purchases/ux_purchase_rc_id | environment,rcPurchaseKey | unique,partial rcPurchaseKey type string |
| purchases/ix_purchase_reconcile | processingStatus,nextAttemptAt,_id | nonunique |
| purchases/ix_purchase_retention | retentionExpiresAt,_id | nonunique |
| subscription_entitlements/ux_subscription_purchase | purchaseId | unique |
| subscription_entitlements/ux_subscription_sequence | accountId,sequence | unique |
| subscription_entitlements/ix_subscription_window | accountId,revokedAt,startsAt,endsAt | nonunique |
| payment_timelines/ux_payment_timeline_account | accountId | unique |
| payment_ledger/ux_payment_ledger_effect | effectKey | unique |
| payment_ledger/ix_payment_ledger_purchase | purchaseId,occurredAt | nonunique |
| payment_sync_commands/ux_payment_sync_operation | environment,userId,operationId | unique |
| payment_sync_commands/ix_payment_sync_due | state,nextAttemptAt,_id | nonunique |
| payment_sync_commands/ttl_payment_sync_purge | purgeAt | expireAfterSeconds=0 |
| payment_provider_inbox/ux_payment_provider_event | environment,providerEventKey | unique |
| payment_provider_inbox/ix_payment_provider_due | state,nextAttemptAt,_id | nonunique |
| payment_provider_inbox/ttl_payment_provider_purge | purgeAt | expireAfterSeconds=0 |

신규 접수 row 필드와 예외 case의 index는 manifest 리뷰 대상이며 기존 index를 대체하지 않는다. 미해결 case에 무조건 business TTL을 붙이지 않는다. sync terminal command는 기존 ADR대로 완료 후120일, 미해결 command는 purgeAt unset이다. provider inbox는 receivedAt+120일이며 필요한 미해결 자료는 만료 전에 case로 원자적 인계한다. 금융5년은 최종 provider 거래·기간권 종료·마지막 reversal 중 가장 늦은 시점을 기준으로 한다.120일 inbox TTL로 금융 business dedupe가 사라지면 안 된다. retentionExpiresAt을 확정할 수 없으면 null로 두고 이유를 기록하며 삭제 대상에서 제외한다.

reader-first → flag OFF/writer 격리 → 기존 index/row 검증 → 명시 subset 설치 → OFF 코드 → staging 검증 순서다. startup drop/recreate/collMod 금지, 알려지지 않은 revision은 fail-fast한다. 기존 cursor의 새 필드는 bounded migration으로 보완하고 필드 부재를 작업 완료로 해석하지 않는다. migration 중 기존 account 발급과 활동 갱신을 격리하고, 새 writer는 명시 version과 기존 lastActivityAt을 보존한다. rollback은 worker 중단·drain 후 새 상태를 이해하는 reader로만 수행한다.

### 5.13 flag·실행 순서

후보 설정은 `billing.payment.sync-enabled`, `entitlement-extension-enabled`, `provider-ingress-enabled`, `provider-worker-enabled`, `reconciliation-enabled`이며 모두 false다. 기존 products/account/무료 reader/cleanup flag와 분리한다. 별도 `paid-reader-enabled`는 만들지 않는다.

- entitlement-extension OFF: 기존 무료 응답만 반환. ON은 새 schema/paid reader가 필요하다.011 미준비이면 paid 시작 요약은 PENDING/ADMISSION_NOT_READY다. 기존 무료 route를 끄지 않는다.
- sync ON: JWT/ACTIVE lifecycle/schema/crypto/durable queue와 실제 처리 경로가 준비돼야 한다. 실제 provider 대신 fake를 연결하거나 운영에서 처리 worker 없이 계속 접수하지 않는다.
- ingress ON: 전용 인증·TLS/allowlist·durable inbox/pending/격리가 필요하다. worker 정지 중 접수는 제한된 용량·적체 경보·재개 절차가 있을 때만 허용하며 장기 미처리를 정상으로 보지 않는다.
- worker/reconciliation ON: 검증된 source adapter·암호화 키·schema·fence/lease·분산 quota가 필요하다. 필수 runtime adapter 누락은 기동 실패다.
- 판매 중단과 금융 입력 중단은 별개다. 기존 금융 사실은 수신·보존·복구할 수 있어야 한다. production flag 변경은 별도 승인이다.

## 6. 부록 — 구현 단계·검증·남은 기술 항목

### 6.1 단계별 완료 기준

#### TMI-210 첫 코어 구현 경계 (2026-10-13 사용자 착수 승인)

- 외부 wire/API/worker를 연결하지 않는 명시 생성형 코어로 구현한다. Spring bean/flag/스케줄러를 추가하지 않는다. 실제 provider 검증을 대신하는 공개 입력 경로는 없다.
- 내부 검증 관측은 environment/store/app, 고정 lookupKeyVersion의 transactionKey·providerEventKey, 원구매자 purchaseAccountRefId, product/time/금액의 최소 정규화 값이다. raw receipt/token/event 전문은 받거나 저장하지 않는다. 키는 길이 구분 UTF-8 tuple의 HMAC-SHA256이며 키 버전이 바뀐 관측을 같은 코어가 혼용하지 않는다. 금액 미상은 null, 알려진 금액은 Decimal128·ISO 통화이며 표시가격으로 채우지 않는다.
- payment_provider_inbox가 입력 상태를 소유한다. digestVersion/version/attempts는 long, counted는 boolean, 매핑 시 accountId/lifecycleId를 동결한다. receive의 unique source+pending 증가와 apply의 source 완료+pending 감소는 동일 Tx다. 입력 불변 digest는 DB의 현재 매핑/처리 상태와 분리한다.
- 미매핑/불일치/탈퇴 등 예외는 payment_purchase_cases로 최소 관측을 원자 인계한다. source와 case를 동시에 counted=true로 두지 않는다. 인계는 pending 총수 불변+ lifecycle.version 증가다. case unique(environment,providerEventKey)가 source 만료 후 중복도 방지한다. case 해결/자동 보존 종료는 미구현·운영 gate이며 무기한 보관 정책 승인이 아니다.
- source retentionDeadline=receivedAt+120일, 완료/인계 뒤에만 purgeAt=retentionDeadline을 설정한다. 미해결 source는 TTL로 증거를 먼저 잃지 않도록 purgeAt을 비운다. 119일 이후 explicit handoff 코어를 제공하고 자동 인계/overdue 경보는 후속 worker 활성화 선행 조건으로 둔다. 운영에서120일 초과를 정상 보존 정책으로 간주하지 않는다.
- Purchase에는 최초 금융 사실 PURCHASE_OBSERVED, 권리 지급에는 PURCHASE_VERIFIED ledger를 각각 고유하게 남긴다. PURCHASE_BLOCKED에서는 검증 금융 사실만 저장하고 입력을 미완료로 유지한다. OPEN 복귀 후 동일 입력으로 원래 purchasedAt 기반 지급 가능하다. WITHDRAWN/과거 inactive 신규 거래는 NOT_GRANTED·REVIEW_REQUIRED로 남기며 자동 재지급하지 않는다.
- refund-first는 검증 transactionKey의 REFUNDED/REVOKED·REVOKED tombstone과 관측 원장으로 후속 지급을 막는다. 이미 지급된 구매의 환불은012 미구현 검토 case로만 보존하며 기간권 종료/reflow 완료로 보고하지 않는다. 운영 연결/판매는 계속 불가하다.
- PAYMENT_INPUT/PAYMENT_APPLY 전용 fence writer는 mandatory epoch/control을 사용한다. ALL_BLOCKED 차단, PURCHASE_BLOCKED 금융 접수/보존 허용·신규 지급 보류. schema 설치는 ALL_BLOCKED+writer 격리 아래 별도 명시 작업이며 기존 initializer/운영 자동 설치와 분리한다.
- exact collection 필드·index/type는 [PurchaseCoreSchema의 payment-purchase-core-r1 programmatic manifest](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCoreSchema.java), 상태 전이/검증은 코어 및 테스트로 고정한다. 기존 무료/계정/cleanup schema를 임의 변경하지 않는다. 접수/case 구조를 검증한 뒤 public sync·실provider·1초worker·205 실증거 연결은 후속이다.
- 로컬 구현·검증 결과: [TMI-210 안내](../runbooks/TMI-210-purchase-core.md). clean test bootJar 1,168통과/실패·오류·skip0. actual provider 검증/HTTP·worker/012환불·205 실제 coverage·배포는 미완료다.

| 단계 | 산출물 | 완료 기준 |
| --- | --- | --- |
| 준비 | 본 초안 검토·DTO/enum·접수/case schema/index·crypto·provider fixture 목록 | 알려지지 않은 지원/보존 가정을 분리, 정본 ADR 동기화와 구현 승인 |
| 코어 | provider-neutral 관측·Purchase/timeline/ledger/접수·fake | 중복/owner/동시 append/refund-first·탈퇴/삭제 경합 replica-set 통과, 운영 OFF |
| API | sync/통합 reader/JWT/호환 flag | 기존 무료 fixture 불변,202/200/오류·PENDING/no-store·guard 미준비 표시 |
| adapter/복구 | 검증된 RC 수신/REST·worker/pagination/quota | 실 schema/서명 증거와 local fake 회귀 구분, 누락/응답 유실/역순/장기 pending 검증 |
|205 연결 | 실제 LocalReader·coverage·모든 pending writer·미매핑 격리 | 구매 발견 보존·미결 보류·삭제 양쪽 commit 순서·실 coverage/drain 검증 |

### 6.2 필수 테스트

1. 동일 sync key/body는 동일 operation, 다른 body409, 다른 user/environment 격리, 서명/audience/scope/Guest/탈퇴 거절.
2. 동일 webhook event/digest200, 다른 digest409, 현재 binding이 바뀌어도 같은 digest, unknown optional 무저장, raw-byte 서명·UTF-8·size/depth·시각 경계.
3. sync+webhook+API의 같은 transaction, unique 경합, unknown commit, 별도 구매 동시 append, 다섯 상품 duration·지연 순서·UTC 끝 시각 exclusive.
4. alias/TRANSFER/family share/anonymous/환경/상품/quantity 오류, inactive 신규 지급 금지, 환불 선행 뒤 owned 부활 금지.
5. pending+1/-1 멱등, inbox→case 인계, 삭제/탈퇴/구매 반영의 양쪽 commit 순서, 삭제된 account upsert 금지, 미매핑 영향 불명은 coverage UNKNOWN.
6. 통합 reader의 기존 무료 필드/envelope, 확장 OFF 생략, paid PENDING/false 구분, 진행/GRADING/guard 불명·여러 benefit, retake를 신규 수량에 합산하지 않음, snapshot/DB 장애503.
7. provider401/403/429/423/5xx, Retry-After, 페이지 누락/악의적 next_page/redirect, timeout/lease 만료/epoch 변경, dead-letter 뒤 미결 자료 보존.
8. BSON 숫자 타입/Long.MAX_VALUE/Decimal128 비유한값/date overflow, 기존 index option 불변, 미지 drift 거절, legacy cursor 이관, raw reference/token/개인정보 비로깅.
9.012 전 refund는 durable 검토·판매 OFF,012 통합 후에만 실제 환불 지원 완료.011 전 paid admission 긍정 판정 금지.

코드 구현마다 `./gradlew clean test`, 필요 시 bootJar를 실행한다. 실제 Store/RC/AWS/Identity를 local 테스트에서 호출하지 않는다. sandbox 증거는 staging에서 별도 확보한다.

### 6.3 구현 전 닫을 기술 항목

기술 제안 수락을 실제 provider 지원 확인과 혼동하지 않는다. 공개 DTO의 enum/장기 검토 표시, crypto envelope/hash 회전, 미해결 case의 목적·최소 필드·보존 종료, cursor migration manifest, quota/Retry-After exact fixture,012 금전 enum 연결을 준비 단계에서 닫는다. terminal sync120일은 기존 계약이므로 재선택하지 않는다. 외부 증거 부족에 따른 신뢰 경로 변경이나 새 제품 제한이 필요할 때만 사용자에게 별도 확인한다. 법적 보존 의무·운영 담당을 추정해 설정하지 않는다.

### 6.4 근거와 이번 검증

- [PaymentController](../../src/main/java/web/tosunsaeng/billing/domain/payment/api/PaymentController.java), [기존 무료 DTO](../../src/main/java/web/tosunsaeng/billing/domain/entitlement/dto/response/EntitlementQueryResponse.java): 현재 route/응답과 후속 확장 구분.
- [foundation manifest](../../src/main/resources/payment-foundation-indexes.json): 기존 index/ACCOUNT_LOOKUP 보존.
- [PaymentStore](../../src/main/java/web/tosunsaeng/billing/domain/payment/repository/PaymentStore.java): 기존2초 Transaction/unknown commit.
- [205 PR2](../runbooks/TMI-205-pr2-purge-core.md): 실제 evidence 미연결/purge ON 거절.
- [PLAN-012](../plans/PLAN-012-refund-ledger-and-access-revocation.md): 금액과 권리 종료 분리.

이번에는 문서 작성과 정적 대조만 수행했다. 코드/manifest/운영 DB/credential/Jira/타서버/배포/commit/push 변경 없음, Gradle 미실행. 이전1066 테스트를010 구현 검증으로 재사용하지 않는다.
