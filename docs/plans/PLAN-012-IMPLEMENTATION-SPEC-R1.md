# PLAN-012 내부 구현 규격 R1

- 작성일: 2026-10-14. 상태: 사용자 요청에 따른 구현 설계. 코드·DB 적용·공동 wire 변경·판매 활성화 승인 아님.
- 우선순위: 승인 제품 정책/R2 공동 wire → 본문 내부 구현 규격 → P0의 과거 후보. 실제 provider 필드는 검증 전까지 지원 완료로 표시하지 않는다.
- 근거: [전체 계획](PLAN-012-refund-ledger-and-access-revocation.md), [P0 조사](PLAN-012-P0-MINIMAL-TECHNICAL-DESIGN-R1.md), [테스트 준비](../contracts/PLAN-012-REFUND-TEST-READINESS-2026-10-14.md).

> 2026-10-14 사용자 승인: 잘못된 Retry-After는 기본 backoff, 증거 불명은 해당 group만 영속 보류하고 나머지 진행, legacy 이관은 실제 대상이 있을 때만 한정한다. 공동 wire·인증·금융/Session 보호는 유지한다.

## 1. 결론

1. 접수는 기존 inbox, 금전은 기존 ledger, 예외는 기존 case를 확장한다. 새 collection은 LC 전달 outbox 하나다.
2. 구매 차단·기간 재배치·최초 환불 effect·정리 진행을 한 Tx로 확정한다. 금액 불명은 권리 종료와 별도로 검토한다.
3. 정리는 RESERVED 예약 → 그룹 순서다. INITIAL 예약에는 아직 그룹이 없으므로 그룹 조회만으로 완료하지 않는다.
4. 그룹의 마지막 성공 confirm 증거를 보존하고 root 차단 이후 고정한다. timestamp 비교·만료된 command에 의존하지 않는다.
5. 내부 기능은 OFF로 개발 가능하다. 실제 provider 매핑·LC 통합·schema 설치·보존 검증은 활성화 조건이다.

## 2. 반드시 읽을 처리 경계

### 2.1 공개/공동 API

새 앱용 환불 실행 API는 만들지 않는다. Identity REFUND 문의, 기존 Billing sync/entitlements를 유지한다. GET은 조회만 한다. `canTakeExam`은 신규 입장 판정이지 보호 Session 이어서 완료할 권한이 아니다. 다른 유효권이 있어도 기존 guard가 입장을 막을 수 있다.

LC route/body/오류/인증은 [R2 §5](../contracts/LC-BILLING-PAID-EXAM-CONTRACT-R2.md)를 그대로 참조한다. 본 규격의 DB 필드·case reason을 외부 DTO에 추가하지 않는다. body의 occurredAt은 구매 최초 appliedAt이며 fan-out 실행 시각으로 갱신하지 않는다. reason/confirmedSessionId/eventId/digest는 최초 outbox에서 고정한다.

### 2.2 내부 입력

검증 adapter만 기존 observation에 `refundEvidence`를 구성한다. 외부 사용자/CLI는 구성할 수 없다. optional field 부재는 legacy 증거 미상이며 새 증거의 명시 null과 구분해 읽는다.

| 필드 | 타입/규칙 |
| --- | --- |
| revision | BSON long, 정확히1 |
| source | REVENUECAT_API / REVENUECAT_WEBHOOK / GOOGLE_ORDERS |
| evidenceKey | 64자리 lowercase SHA256. 검증된 정규화 증거 식별; 출처 인증 대용 아님 |
| providerConfirmedAt | UTC millisecond date 또는 null |
| amountMode | UNKNOWN / CUMULATIVE |
| amount / currency / basis | UNKNOWN이면 모두 null. CUMULATIVE이면 Decimal128 비음수/ISO4217/STORE_REFUND_GROSS 모두 필수 |

`STORE_REFUND_GROSS`는 원 결제통화·세금 포함 실제 반환 누적액이다. 세전액을 자동 승격하지 않는다. 검증된 완전한 이력에서만 누적을 산출한다. 실제 provider가 이 기준을 제공하지 않으면 UNKNOWN이다. 초기 구현은 INCREMENTAL 모드를 지원하지 않는다. source별 필드 경로/완전성은 실제 fixture 확보 후 adapter에 고정한다.

새 optional 필드를 기존 digest에 무조건 포함하면 legacy 중복 판정이 달라진다. 기존 row는 기존 digest 알고리즘을 유지하고, 새 evidence 포함 관측은 별도 digestVersion=2로 저장한다. 동일 providerEventKey 재수신은 저장된 version으로 비교한다. 추가 조회 증거는 원 inbox를 덮어쓰지 않고 별도 정규화 관측 key로 접수한다. key 구성·canonical bytes는 P1 테스트 fixture로 고정한다.

## 3. 저장 규격

### 3.1 purchases

기존 purchaseState/accessState/version을 권리의 단일 기준으로 유지한다. 별도 refund access enum을 중복 관리하지 않는다.

`refund`는 최초 종료 때 추가한다. effectId(UUIDv4), appliedAt(date), providerConfirmedAt(date|null), refundedTotal(Decimal128|null), currency/basis/amountSource(string|null), amountVersion(long>=0). 금액 관련4필드는 모두 null 또는 모두 존재. 최초 reason은 최초 ledger/outbox와 일치하며 추가 REVOKE/REFUND 관측으로 전파 reason을 바꾸지 않는다. effectId/appliedAt은 불변, amountVersion은 금액 변경 때만 증가한다.

`refundWork`: phase=RESERVATIONS/GROUPS/DONE, state=READY/REVIEW_REQUIRED, afterId(string|null), owner/until(nullable pair), leaseVersion(long>=1), generation(long>=1), nextAttemptAt(date|null), retryStartedAt(date), attempts(long>=0), failure(string|null). 실행 중 여부는 lease로 표현하고 RUNNING 상태를 중복 저장하지 않는다. phase 변경 시 afterId=null. DONE이면 owner/until/nextAttemptAt=null. amount case 미해결이 fan-out DONE을 막지 않는다.

### 3.2 confirm 증거 및 전달

- `attempt_groups.lastConfirmedSessionId` nullable opaque string: 성공 confirm Tx만 기록. Session의 sourceSnapshot·group·confirmedAt 존재를 함께 검증한다. RESERVED/상태event/expiry는 변경하지 않는다.
- paid confirm은 이미 purchase/entitlement version을 touch한다. 환불도 같은 purchase version에 CAS하여 커밋 순서를 만든다. root 차단 뒤 성공 confirm/group 삽입이 불가능하므로 마지막 confirm 포인터가 안정된다. 별도 시계 비교나 전역 순번은 불필요하다.
- legacy는 해당 유료 group의 실제 존재 여부와 증거 coverage부터 읽기 조사한다. 대상이 없으면 신규 confirm writer부터 적용하며 범용 backfill worker/이관 시스템을 만들지 않는다. 대상이 있으면 확인된 목록만 영속 Reservation CONFIRMED/Session/source/group 증거로 한정 이관한다. 현재 activeSession을 무조건 복사하거나 여러 미완료 후보를 임의 선택하지 않는다. 미입증 group은 아래 최소 보류 기록을 남긴 뒤 cursor를 전진해 나머지를 처리한다.
- 단일 보호 S1이라는 R2 계약과 기존 completionAllowed의 복수 과거 Session이 충돌하는 사례는 자동 탈락시키지 않는다. 구현 경합 테스트로 확인하고 실제 복수 보호가 필요하면 LC 공동 보완 gate로 보고한다.
- `payment_access_outbox`: _id/eventId(UUIDv4), environment, producer, consumerScopeId, attemptGroupId, purchaseId, refundEffectId, body(기존 R2 exact), digest, createdAt, deliveredAt(nullable), work. body/digest는 immutable. work는 READY/BLOCKED_AUTH/REVIEW_REQUIRED/DELIVERED와 위 lease/generation/retry 필드를 사용한다.
- 미전송 outbox·미해결 case는 TTL 없음. 전달 완료 상세 폐기는 TMI-217 보존 계약 확인 전 구현하지 않는다. 금융5년을 모든 메시지 원문 보존 근거로 쓰지 않는다.

- 증거 불명 group은 기존 `attempt_groups.refundReview`에 `{effectId, state: OPEN, reason: REFUND_EVIDENCE_MISSING, createdAt, resolvedAt: null}`을 저장한다. effectId는 구매 refund effect와 일치해야 한다. 새 collection·가짜 provider event·임의 confirmedSessionId/outbox를 만들지 않는다. 보류 기록과 purchase cursor 전진은 같은 Tx다. 구매 source 조회 index로 해당 구매의 OPEN group을 찾아 기존 검토 경로에서 재확인한다. 증거 확보 뒤 같은 Tx에서 최초 불변 outbox 삽입과 RESOLVED/resolvedAt을 기록한다. 재확인 실패는 OPEN 유지, 미해결 증거 purge 금지다. 재조회는 cursor를 되감지 않으며 동시 실행은 기존 lease/CAS와 outbox unique로 수렴한다.

### 3.3 원장·case

기존 `ux_payment_ledger_effect`를 사용한다. 신규 type은 ACCESS_REVOKED / REFUND_AMOUNT_CONFIRMED / TIMELINE_REFLOWED다. effectKey는 각각 `refund-access:<purchaseId>`, `refund-amount:<purchaseId>:<amountVersion>`, `refund-reflow:<effectId>:<entitlementId>`. amountVersion CAS와 원장 삽입은 같은 Tx다. 금액 delta·total·currency·basis·source·evidenceKey와 이전/새 schedule을 detail에 보존한다. 무한대/NaN/이진float·Decimal128 범위 초과는 거절한다.

기존 case는 원 providerEventKey unique를 유지한다. reviewState=OPEN/RESOLVED 추가, legacy REVIEW_REQUIRED는 OPEN으로 읽는다. purchaseId/inquiryId/resolvedAt/actorDigest는 nullable; 원문 문의/이메일/영수증은 복제하지 않는다. reason은 REFUND_AMOUNT_UNKNOWN / REFUND_AMOUNT_CONFLICT / REFUND_BINDING_UNRESOLVED / REFUND_FINALITY_UNRESOLVED / REFUND_REVERSED / REFUND_EVIDENCE_MISSING / REFUND_RETRY_EXHAUSTED를 내부 값으로 사용한다. 전달 충돌은 outbox 자체 REVIEW_REQUIRED로 관리하여 가짜 provider inbox를 만들지 않는다.

### 3.4 추가 index

모든 아래 key는 순서대로 ascending, simple collation, sparse=false, TTL/partial 없음. 자동 startup 생성/drop 금지. 기존 index는 보존한다.

| collection / name | ordered keys | unique |
| --- | --- | --- |
| purchases / ix_purchase_refund_due | environment, refundWork.state, refundWork.nextAttemptAt, _id | false |
| reservations / ix_reservation_refund_source | sourceSnapshot.purchaseId, status, _id | false |
| attempt_groups / ix_group_refund_source | sourceSnapshot.purchaseId, _id | false |
| payment_access_outbox / ux_access_outbox_group | environment, producer, consumerScopeId, attemptGroupId | true |
| payment_access_outbox / ux_access_outbox_event | eventId | true |
| payment_access_outbox / ix_access_outbox_due | environment, work.state, work.nextAttemptAt, _id | false |
| payment_purchase_cases / ix_payment_case_refund_review | environment, reviewState, reason, createdAt, _id | false |

purchaseId는 기존 내부 unique ID로 source와 대조하며 대상 purchase의 environment/account도 검사한다. schema 구현 시 실제 collection/필드와 대조한다.

## 4. 금액과 권리의 독립 처리

| 검증 결과 | 권리 | 금액 |
| --- | --- | --- |
| 귀속/최종 환불 불명 | 종료하지 않고 case | 기록하지 않음 |
| 최종 환불 확실·금액 불명 | 한 번 종료 | null + OPEN case |
| 동일 authority/통화/기준의 누적T, 기존C | 종료 유지 | T>C이면 차이T-C append, T=C이면 no-op |
| 최초 확인 총액T | 종료 유지 | 기존 기록액0에서 T append. 과거 unknown을 0원 환불로 표시하지 않음 |
| T<C/기준 변경/구매액 초과/금액 모순 | 종료 유지 | case. 음수로 자동 정정하지 않음 |
| REFUND_REVERSED | 자동 복원하지 않음 | case/인증된 재조회 |

RC와 Google 금액을 더하지 않는다. Google 경로를 채택한 구매는 이후 같은 authority로 확인한다. RC의 다른 금액은 비교 자료이지 추가 반환액이 아니다. 비교 가능한 원 구매 총액이 없으면 상한을 추정하지 않고 금액 case에 남긴다. 검증 정정용 추가type/수동 금액 입력은 초기 범위 밖이며 사실을 case에 보존하고 별도로 기술 검토한다.

## 5. Transaction과 순차 처리

### 5.1 root

provider I/O는 Tx 밖이다. Tx 안에서 기존 recovery fence, 대상 purchase/account/환경/기존 effect를 검사하고 purchase version CAS→accessState REVOKED→entitlement revokedAt→timeline version CAS/reflow→ledger/case→refundWork READY를 동일 ClientSession으로 commit한다. 탈퇴 계정도 환불을 기록하되 ACTIVE로 되돌리지 않는다.

appliedAt/effectId는 성공 저장값으로 replay한다. unknown commit은 동일 effect를 조회한 뒤 재시도한다. 경합으로 rollback한 Tx의 임시 시각은 확정값이 아니다. 미시작 slot만 sequence순으로 max(appliedAt, 선행 유효 slot 종료)를 기준으로 기존 시작을 늦추지 않고 재배치한다. 이미 시작한 다른 slot/무료권은 유지한다. reversal fence 뒤 구매가 도착해도 재지급하지 않는다.

timeline은 초기 구현에서 원자적 reflow를 유지한다. 거대한 timeline을 잘라내거나 부분 commit하지 않는다. 부하 시험에서 Tx 한계에 도달하면 판매 전 gate로 보고하며 구매 횟수 제한을 임의 추가하지 않는다.

### 5.2 phase RESERVATIONS

sourceSnapshot.purchaseId 일치 및 RESERVED인 대상을 _id 오름차순, afterId보다 큰 범위로 조회한다. 각 1건 Tx에서 root/lease/source/version을 재검사하고 기존 release 처리로 CANCELED·confirmRejection ENTITLEMENT_REVOKED·receipt/status·최소 이용원장·pending guard를 정합화한 뒤 cursor를 전진한다. 이미 terminal이면 기존 결과를 유지한다. 성공 confirm을 취소하지 않는다. group이 아직 없는 예약에는 가짜 group/outbox를 만들지 않고 LC confirm/status 복구로 수렴한다.

root 차단 후 이 구매 source로 신규 reserve가 commit될 수 없어야 한다. 마지막에 같은 source의 RESERVED 부재를 동일 Tx에서 확인해 GROUPS로 전환한다. lease를 잡았더라도 전체를 한 Tx로 묶지 않는다.

### 5.3 phase GROUPS

sourceSnapshot.purchaseId 일치 대상을 _id 오름차순으로 순회한다. 각 group Tx에서 lastConfirmedSessionId/source 검증, replacementBlocked 설정, 동일 group unique outbox 최초 삽입, cursor 전진을 commit한다. Session이 terminal이어도 기록상 보호ID를 변경하지 않는다. LC lifecycle deny 우선으로 부활을 막는다. 보호 진행 중 guard는 유지하고 terminal 확인 때만 exact group/session/operation/version으로 해제한다. 다른 pending operation·새 guard를 지우지 않는다.

DONE은 열거 완료이지 전체 정리/LC 전달 완료가 아니다. OPEN refundReview 또는 미전송 outbox가 있으면 운영에서는 미완료로 표시한다. 증거 불명 group만 보류해도 구매 root 차단은 유지하며 다른 group 정리는 진행한다. DB 저장 실패/lease 유실은 보류 성공으로 위장하지 않고 cursor 전진을 rollback한다. 미전송 outbox는 독립적으로 재송신한다. 환불 root 뒤 새 group/confirm이 삽입되는 경로가 있으면 구현 오류로 테스트를 실패시킨다. 순회 완료 전 또는 해당 group 미해결 중 관련 증거 purge를 금지한다.

## 6. worker·운영 수치

새 fan-out/publisher는 fixed-delay 5초, tick당 최대20건, 같은 instance에서 순차 실행한다. 복수 instance는 DB lease로 배제한다. 기존 provider poll1초/batch100은 유지한다. lease30초, 각 Tx/HTTP 전 확인·필요 갱신, 만료 후 결과 저장 금지다. leaseVersion/generation은 BSON long overflow를 거절한다.

HTTP timeout5초, redirect 금지, body 없는 exact204만 성공이다. 일시 I/O/408/429/5xx는 재시도한다. base=min(3600,5×2^(attempt-1))초, jitter 배율[1,2), 최종3600초 상한이다. 최초 처리 시도부터24시간 이상이면 REVIEW_REQUIRED. 인증된 담당자가 사유를 남기고 generation 증가/새 retryStartedAt으로 재개한다. event/body/cursor/금융 effect는 초기화하지 않는다.

401/403은 즉시 BLOCKED_AUTH, 그 외4xx/2xx/3xx는 REVIEW_REQUIRED. 429/503에만 Retry-After를 해석한다. 단일 header의 비음수 정수초 또는 HTTP-date, 과거 date는 now, 다음 시각은 max(backoff,header)다. header 부재·중복·잘못된 값·표현 범위 overflow는 기본 backoff를 사용하고 원문 없이 파싱 실패 사유/집계만 남긴다. 이 사유만으로 수동 검토를 만들지 않으며 총24시간 재시도 한도는 유지한다. 유효하게 해석된 미래24시간 초과 지시는 무시하고 조기 재송신하지 않고 보류한다. 정확히24시간은 파싱상 허용하지만 총 재시도 기한에 도달하면 전송하지 않고 보류한다. 이 fallback은 신규 PLAN012 publisher 범위이며 기존 Identity 등 다른 sender를 일괄 변경하지 않는다. 구현 시 LC 공동 전송 규격과 충돌하면 해당 범위만 확인한다. JWT 시간 검증·TLS는 유지한다.

일반 case는 기존 KST09시 일일 집계에 건수/최장경과/7일 초과 미검토 수를 넣는다. 인증 실패는 즉시 알림, fan-out 또는 outbox의 가장 오래된 미완료가5분 초과면 지연 경고, 회복 때 알림이다. 5분은 초기 운영 임계값이며 완료 SLA가 아니다. 60초 후보는 채택하지 않는다. Slack 실패에도 DB 책임은 남는다. 개인/거래ID는 Slack/metric label에 보내지 않는다.

운영은 기존 인증 CLI에 case-list/case-show/refund-recheck/outbox-resume 상당의 기능을 확장한다. 금액 편집/강제 APPLIED/새 환불 실행은 제공하지 않는다. actorDigest·reason·실행시각·대상·generation을 감사 기록에 저장한다. 재조회가 미확정이면 case는 OPEN이며 Slack 읽음만으로 RESOLVED 처리하지 않는다.

OPEN group 보류도 일일 요약의 미해결 수/최장 대기에 포함한다. purchase fan-out DONE만으로 경보를 해제하지 않는다. 별도 관리 화면이나 범용 복구 시스템은 추가하지 않는다.

## 7. OFF 구현·이관·활성화

2026-10-14 사용자 승인: 설정 prefix `billing.payment.refund` 아래 `processing-enabled`, `publisher-enabled` 두 개만 두고 기본false로 한다. 별도 `fanout-enabled`는 만들지 않는다.

| 설정 | 담당 범위 |
| --- | --- |
| processing-enabled | 검증된 환불의 원장·권리 종료·reflow 및 예약/시험 그룹 순차 정리를 함께 활성화 |
| publisher-enabled | DB에 저장된 불변 outbox를 LC로 전달/재시도 |

processing은 검증된 입력 adapter+schema+기존 worker가 필수이며 미검증 provider 매핑을 조용히 ON하지 않는다. publisher는 processing ON과 LC TLS/key/주소를 요구한다. OFF/OFF는 비활성 배포, ON/OFF는 환불 반영·정리 및 outbox 축적, ON/ON은 전달 포함 처리다. OFF/ON은 구성 오류로 거절한다. LC 장애 때 processing은 유지하고 publisher만 끌 수 있으며 미전송 자료를 삭제/완료 처리하지 않는다. 전부 OFF면 새 외부 secret 의존성은 없다. 키 누락/구 인증 fallback은 금지한다. processing OFF도 이미 저장한 환불 효과를 되돌리지 않으며 미처리 접수·진행 위치·outbox를 보존한다.

이관은 현행 payment-purchase-core-r6 이후 revision(구현 시 최신 manifest에 맞춤)과 paid schema의 명시 upgrade다. reader 대응→관련 writer 격리/maintenance→validator/index 추가→증거 coverage 확인→OFF writer 배포→staging 검증 순이다. 구 writer rollback은 새 처리 중단/미완료 책임 보존이 필요하며 validator를 자동 downgrade하지 않는다.

production paid admission은 processing/publisher 모두 ON과 LC 수신 검증·Store 증거·TMI217 보존 gate가 전제다. 판매 중단 시 기존 환불 처리는 유지한다. 침해로 publisher를 멈추는 경우 별도 장애 절차로 책임을 보존한다. 환경 간 키/Store 증거를 재사용하지 않는다. 기존 DB schema 반복검사·RevenueCat 응답 후 permit검사는 그대로 유지하며 이 계획의 선행 리팩터링으로 요구하지 않는다.

## 8. 테스트·완료 기준

| ID | 필수 검증 |
| --- | --- |
| F01 | legacy digest 재생, 새 evidence digest, 동일 key 다른 내용 거절 |
| F02 | unknown→확인→추가누적→중복/낮은누적/통화충돌, RC/Google 이중합산0 |
| F03 | root/ledger/reflow/work 각 쓰기 rollback, unknown commit 동일effect, 동시 purchase append |
| F04 | confirm 선행/환불 선행 실제 Mongo 양방향, 동일 millisecond도 commit 순서 판정 |
| F05 | INITIAL RESERVED 그룹 부재, replacement RESERVED와 기존 S1, phase경계/lease유실/crash |
| F06 | 마지막 confirm 증거 보존, command 소실, 삭제/terminal/복수 과거Session 감지, TMI217 purge 경합. legacy 대상0이면 이관 불필요, 대상 존재 시 한정 이관/추정 금지 |
| F07 | 20건 초과 page·동시worker·cursor/outbox 원자성·중복 event/body 불변. 중간 group 증거 불명에도 후속 처리, 보류저장/cursor 원자성, 재확인 뒤 최초outbox, OPEN 남은 DONE을 전체 완료로 오표시하지 않음 |
| F08 | 후속 slot 앞당김/시작한 slot 불변, 다른 구매·무료/복원권 불변, paid confirm 복원과 양순서 |
| F09 | LC exact204/응답유실/409/인증/Retry-After 경계, JWT/port/TLS 회귀. malformed/중복/overflow 기본backoff, 유효한 긴 대기 조기재전송 금지,24h 소진/다른 sender 불변 |
| F10 | reader/resolver 일치, 보호 S1과 신규 입장 구분, 지연 worker의 새 guard 해제 금지 |
| F11 | schema 혼재/중단재개/구 writer 거절, 금융/미완료 보존, 거대 timeline 측정 |
| F12 | Slack 실패에도 case 유지, 감사 기록을 남기는 재개,24h 보류. 두flag OFF/OFF·ON/OFF·ON/ON, OFF/ON 기동거절, publisher 재개 시 동일outbox 재전송, processing OFF 시 접수/진행 보존, OFF 무의존 |

단위·fixture·replica-set Mongo·LC 상호 HTTP·Store sandbox 결과는 구분한다. 구현 뒤 clean test를 실행한다. provider 실데이터 없이 F01~12 내부 구현은 진행할 수 있지만 실Store 지원 완료/판매 가능으로 선언하지 않는다.

## 9. 외부 확인이 남은 항목

RC/Google 실제 필드/금액기준·권한·Webhook 인증 호환성, LC R2 수신/역방향 JWT, TMI217 증거 보존, 장기 보유의 정확 기산점/삭제 운영이다. 새 제품 결정이 아닌 검증 gate지만 기존 방침으로 처리할 수 없는 사실이 나오면 별도로 보고한다. 이번 조사는 정적 코드/문서뿐이며 외부 설정/코드/fixture/DB/Jira 변경은 없다.
