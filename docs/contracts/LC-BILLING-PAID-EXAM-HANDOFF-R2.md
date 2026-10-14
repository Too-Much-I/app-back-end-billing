# LC–Billing 유료 시험 연동 인계 R2 — 검토 보완 후보

> 2026-10-14 공동 기술 검토 기준 통합: [유료 시험 계약 R2](LC-BILLING-PAID-EXAM-CONTRACT-R2.md)를 우선 참조한다. 최신 승인 정책·LC 최소wire 제안을 한 곳에 정리했으며 본문의 종전 기술 후보는 변경 이력이다. 새정본의 exact 공동수락/fixture/구현·배포는 미완료이고 phone/삭제/보존 gate는 별도 확인한다.

> 2026-10-14 [wire R1 draft-2 §9](LC-BILLING-PAID-EXAM-WIRE-R1.md)에 LC 검토 반영: replay우선·삭제continuation/누락context·조합표·거절snapshot·기존409/503오류유지·digest상세·예외역순·추가fixture. 신규계약은여전히후보,OTHER_SOURCE_CHANGE 최초활성화제외제안·미확정보존/phone충돌 gate명시.

> 2026-10-13 [공동 기술 계약 R1 초안](LC-BILLING-PAID-EXAM-WIRE-R1.md) 작성: 전환 요청/응답·문제 선택·오류/복구·역방향인증/보존·fixture 제작명세. 신규 wire/enum/오류는 LC 공동 수락 전 후보이며 구현/배포 미실행.

> 2026-10-13 legacy 보존 승인: [prod 점검·확정 정책](../plans/PLAN-011-SOURCE-PRODUCTION-INVENTORY-2026-10-13.md)에 따라 기존 LC 시험은 기존 규칙/이력을 유지하고 일괄 종료·무료source backfill하지 않는다. 전환 후 새 시험·새 재응시부터 Billing 및 source 필수 적용. legacy는 source 누락이 아닌 전환 전 대상 증거로 구분하고 소비/복원 증거로 임의 사용하지 않는다. Billing prod DB는 미구축으로 기존 데이터 이관이 불필요하다. 실제 전환 경계와 LC 구현은 후속이다.

> 2026-10-13 최신 단순화 승인: [무료권 정책 §1/3/4](FREE-EXAM-PASS-AND-PAID-SWITCH-RESTORATION-2026-10-13.md) 우선. Billing은 paid confirm·복원 지급·원 그룹 재응시 차단을 같은 Transaction으로 확정하고 LC는 기존 Session을 순차 종료한다. 이미 접수된 무료 제출의 경합 완료와 복원권 동시 보유는 허용한다. 최초 trial consumption ID당 복원 1회, 복원권/초대권 재복원 없음. 현재 무기한·지급순만 구현하고 만료 기능은 후속. MEMBER 전용 보상권의 Guest merge 확장은 제외하며 기존 trial merge는 유지한다. 종전 무중첩 보장·일반 root 계보·현재 만료순 구현·보상권 merge 표현은 이 범위에서 대체한다.

> 2026-10-13 최신 승인: [공통 무료권·유료 전환 복원](FREE-EXAM-PASS-AND-PAID-SWITCH-RESTORATION-2026-10-13.md)을 따른다. 종전 무료 replacement 우선·새 무료권 미지급 결정을 §9와 같이 대체한다. 분류는 사용자 승인 `EXAM_PASS|SUBSCRIPTION`, 세부 지급 출처는 Billing 내부 관리다. exact wire/이관은 양측 동결 전 구현하지 않는다.

- 작성일: 2026-10-13 / 입력: 사용자 전달 LC R1 검토 5항목
- 재검토: 기존 새 시험 흐름을 활용한 다른 권리의 새 그룹 전환 사용자 승인 반영. 새 제출 마감 없음 수락 후 [잔여 항목 검토](../plans/LC-BILLING-PAID-HANDOFF-READINESS-REVIEW-2026-10-13.md) 반영. 아래는 현행 구현 완료를 의미하지 않는다.
- 상태: 공동 확정용 후보. 아래 기술 규칙/새 오류/보존기간/인증 property는 양측 수락 전 제안이다. 사용자 확인에 따라 새 제출 시간 제한을 도입하지 않는다. 구현/배포/활성화 승인 아님.
- 기준: [R1](LC-BILLING-PAID-EXAM-HANDOFF-R1.md)의 제품 정책·기존 API/상태event 유지 및 [confirm-first 승인](PAYMENT-CONFIRMED-SESSION-REFUND-POLICY-2026-10-13.md). 본 문서가 R1의 모호한 호환/기한/오류/역방향 보안/순서 규칙을 대체한다. LC 회신의 수락을 주장하지 않는다.

## 1. source reader와 operation snapshot

현재 LC JwtBillingReservationClient는 strict response decoder이며 ExamCreationOperation은 reservation/group/kind/confirmedAt을 보존하나 entitlementSource는 없다. Session만 확장하면 응답유실·reconciliation 경로가 source를 잃는다.

- reserve/status 응답의 `entitlementSource=EXAM_PASS|SUBSCRIPTION` (사용자 승인 분류; exact wire/legacy 이관은 공동 동결 필요)를 LC client snapshot, ExamCreationOperation 영속 snapshot, ExamSession에 모두 보존한다. reserve snapshot 확보→operation 저장→Session commit 단계에서 같은 immutable source를 사용한다. status 복구와 중복confirm에도 source 일치를 검사한다.
- Billing command snapshot/reservation/group/Session의 source 역시 불변이며 status는 authoritative 저장값을 반환한다. confirm 응답은 기존형태를 유지하고 기저 source는 operation/status로 복구한다.
- reader 선배포는 모든 API/saga/reconciliation worker task 대상이다. 신규field를 읽는 strict schema만 정확히 확장하고 임의 unknownfield 허용으로 바꾸지 않는다.
- reserve 전 PREPARED는 source 미정이 가능하다. 신규 reserve 성공 이후 누락/unknown enum/잘못된 타입/불변 snapshot 충돌은 계약 오류·복구 대상으로 처리하며 EXAM_PASS 기본값을 넣지 않는다. 발신 여부가 불명인 PREPARED는 status로 복구하고 임의 미발신으로 처리하지 않는다.
- 기존 LC 시험은 전환 직전 서버에서 확정한 시험 ID 목록(manifest)으로 구분하는 방식을 우선 검토한다. 원본 Session에 source를 일괄 채우지 않으며 기존 기록/기존 규칙의 접수·Job 복구를 유지한다. source=null·현재 flag OFF·클라이언트 시각은 legacy 증거가 아니다. manifest 부재/조회 실패 시 임의 legacy 허용도 하지 않는다.
- 확인한 prod LC 1,493건에는 Billing 연결이 없고 Billing prod DB는 미구축이다. 따라서 종전 proven-TRIAL fallback/무료 backfill은 이번 prod 전환안에서 제거한다. 다른 환경에서 별도 Billing-linked legacy가 확인되면 그 환경의 증거와 이관안을 별도로 검토한다.
- manifest는 실제 조회 가능한 보호된 서버 저장소에 exact 시험 ID 목록을 보관하고, 보고서에는 revision/hash/건수만 남긴다. 구 writer drain과 최종 inventory 확정으로 신규 시험이 목록에 편입되지 않게 한다. 일반 writer generation 시스템을 새로 만들 필요는 없다. 목록 저장·보존·삭제/탈퇴 연계와 전환 명령은 LC 상세 설계에서 확정한다.
- 모든 API/worker reader 선배포→source/전환 snapshot writer 준비→구 writer drain 및 manifest 확정→신규 Billing 경로 coverage 검증→paid ON. source를 지우는 구버전 worker/rollback을 금지한다. 기존 시험을 참고한 새 시험·새 재응시는 Billing을 거치며 legacy에 소비/복원 증거를 사후 생성하지 않는다.

## 2. 제출 제한을 추가하지 않음

사용자 확인: 「제한이 돼? 안하는 거 아니었어?」. 기존 합의에 새로운 제출 시간 제한은 없었다. 작성자가 제시한 confirm+30분/60분/24시간 후보는 철회한다. R1의 '기존 제출 기한'은 구현에 존재한다고 가정한 잘못된 표현이며 이 절로 대체한다.

- 환불 전에 Billing confirm이 성공한 exact Session에는 **이번 결제 연동으로 새로운 제출 시간 제한을 추가하지 않는다.** 마감 timestamp/submitDeadlineAt/public deadline error도 만들지 않는다.
- 현재 LC gradingStartedAt+gradingDeadline은 채점 시작 후 정책이다. 이를 제출 마감으로 해석하지 않으며 이번 변경으로 제거하거나 연장하지 않는다.
- confirm은 Billing Transaction의 성공 증거로 판정하며 reserve/Session commit만으로 완료 예외가 생기지 않는다. confirmedAt·source는 operation/Session복구에 보존하고 LC는 exact CONFIRMED 상태/귀속 증거로 성공복구할 수 있으므로 새 제출기한 계산용 confirmedAt field는 필요하지 않다. Billing의 영속 confirmedAt/commit 증거는 내부에 유지한다. status confirmedAt 추가는 필요한 기존 복구 경로가 확인될 때만 공동 DTO 후보로 검토한다.
- 제출의 durable 접수/응답 유실/Job 멱등 흐름은 LC 현행을 유지한다. 별도 Billing grading 승인이나 시간 재승인을 요구하지 않는다.
- 현재 Session이 이미 ABANDONED_RESTARTED/FAILED/terminal 또는 삭제/탈퇴로 접근 종료됐다면 부활시키지 않는다. 완료 예외는 새 Session/replacement/기한 연장 기능이 아니다. 기존에 실제 존재하는 학습 종료 규칙은 inventory로 확인하되 새 시간을 발명하지 않는다.
- protected 진행 Session guard는 기존 학습 terminal/새 시험 시작 흐름과 exact CAS로 정리한다. 환불이나 임의 제출 timeout만으로 guard를 해제하지 않는다. 사용자 승인에 따라 별도 포기 버튼/선행 종료 절차를 추가하지 않는다. 유효한 동일 source 재응시는 기존 group 아래 새 Session, 환불 source의 replacement는 거절, 다른 유효 구매/무료권의 INITIAL은 새 group으로 전환한다. 원 group의 purchase/source와 사용 이력은 보존한다. 전환 실패 시 기존 Session만 먼저 종료되거나 기존 source를 새 구매로 덮어쓰지 않도록 LC Session commit/Billing guard 전환·보상·재시도를 공동 설계한다. 이는 양 저장소를 단일 Transaction으로 처리한다는 뜻이 아니다. exact 동결/구현이 남아 있으며 새 기간 제한은 도입하지 않는다.
- 제출 마감이 없으므로 tombstone/예외 보존을 임의30분/60분 또는120일에 만료시키면 안 된다. protected Session이 남아있는 상태에서의 최소 deny/confirm증거 보존·개인정보 purge·오래된event 처리 정책은 §5의 공동 동결 과제다. 보존기간으로 사용자 이용권한을 몰래 종료하지 않는다.

## 3. ENTITLEMENT_REVOKED 오류·보상

현재 LC Category/mapError에는 신규code가 없어서409 ENTITLEMENT_REVOKED는 CONTRACT_ERROR가 된다. 새category만 추가하면 terminal저장/공개응답/worker switch 경로도 빠질 수 있다.

후보:

| 경계 | 처리 |
| --- | --- |
| Billing 최초confirm/refunded source replacement | HTTP409, code ENTITLEMENT_REVOKED, retryable=false; 기존internal envelope 유지 |
| LC client | status409+exact code 조합으로 Category.ENTITLEMENT_REVOKED; 다른HTTP/code 조합은 기존정책/계약오류 |
| LC public | 현행공개BaseResponse형태 유지·HTTP409/code ENTITLEMENT_REVOKED/message '환불 또는 취소된 이용권으로는 시험을 시작할 수 없습니다.' 후보 |
| 재호출된 FAILED_TERMINAL operation | 동일공개업무오류를 재현, 일시503로바꾸지 않음 |
| confirm성공증거 | stored CONFIRMED/exact operation/reservation/session/source 일치이면 Session보존·성공복구; refunded현재권리만으로성공을번복하지 않음 |
| confirm불명 | 최초 reserve operation 기준 status-first. PROCESSING/transport/OPERATION_NOT_FOUND/빈404/증거누락은 취소·미소비확정 아님. 원본/Session보존·복구대기 |
| 보상가능 | 동일operation과exactSession의 CANCELED/EXPIRED 또는 durable confirm거절+rootdeny가 authoritative증거로확정된 뒤만 orphan Session 안전보상. RESERVED 관측만으로는 이후 confirm과 경합하므로 보상하지 않는다. confirmedSession에 cancel/delete 금지 |

Billing 신규최초confirm은 rootdeny를검사하되, 같은성공confirm의멱등재현은 먼저stored성공을반환한다. status는refund뒤에도confirm증거를보존한다. status복구는 command TTL 후에도 caller/user/operation/reservation/session/source 귀속을 영속 Reservation/AttemptSession으로 검증해야 한다. 현재 status와 confirm은 reserve command 존재를 먼저 요구하므로 이 부분은 PLAN011 보완 대상이다. TTL소실 뒤 동일confirm payload 재현은 영속 digest/commit결과 증거와 함께 설계한다. 해당status복구에서필요한거절증거를어떻게표현할지 exact상태/오류fixture로동결한다. OPERATION_NOT_FOUND만으로Session정리하지 않는다.

LC 변경대상: client error mapper/category, saga publicFailure/terminalFailure·failed snapshot, reconciliation 분류/보상, 이미성공confirm의응답유실복구. 앱공개POST /api/v1/exams body없음/Idempotency-Key/성공DTO는유지한다.

## 4. 신규 revoke API 보안·flag 후보

`POST /internal/v1/entitlements/access/events`는 현재LC미구현. **최대16KiB=16,384bytes**이며정확히그크기여야한다는뜻아니다. UTF-8 rawrequest bytes 기준,16,385bytes거절·boundedread·strictdecode전저장/로그금지. 응답body없는204/401/403/404도정책에맞춰시험한다.

별도역방향 profile후보:

| 항목 | 후보 |
| --- | --- |
| issuer | urn:tosunsaeng:workload:billing:{env} |
| sub | billing-service |
| aud | tosunsaeng-learning-core:entitlement-access-events:{env} |
| env | test/staging/production exact, 각환경trust분리 |
| key | 초기는 revoke전용 Billing RSA2048+ signing key와 LC배포형publicJWKset. Identity recovery signing key와독립이권장 |
| JWT | RS256/typJWT/kid, TTL60/skew30, 매HTTP새token/jti. 나머지시간/JOSE/claim/Bearer/query규칙은동결된LC→Billing A profile과동일한규칙을별도fixture로검증 |
| route권한 | exact POST 위route만. 검증전issuer는fixedverifier선택힌트일뿐principal아님, 서비스키union/fallback/원격fetch없음 |
| Billing 발신flag 후보 | billing.payment.access-publisher-enabled 기본false |
| Billing key 후보 | billing.payment.access-workload-jwt.environment/private-jwk |
| LC 수신flag 후보 | app.billing.access-events.receiver-enabled 기본false |
| LC trust 후보 | app.billing.access-events.environment/billing-public-jwk-set |

키/env는ON에필수검증·OFF미파싱,업무발신ON인데인증준비불명구성기동거절. 명칭은LC설정충돌검토후동결한다. 기존LC→Billing키/수신flags와독립이다. 신뢰된다른서비스token403을원하면그서비스의고정verifier가실제enabled trust로서명검증가능해야한다. 읽지않는키/token은401이며issuer문자열만으로403금지.

connector/security chain: LC기존내부connector/다른workload chain inventory를먼저확인해TLS공유가능여부확정. 별도logicalchain/정확localport/routeguard, 살아있는잘못된port·수신OFF404/no-store, 실제port미기동은transport실패. 인증형식/서명/time/query401,검증된wrongsub/aud/method/path403 후보. OFFguard/auth/body 우선순위및wrongmethod route검사는HTTPfixture로동결한다. direct task/다른port/IAM permitAll/legacy TEST가route를열지않는다.

AWSALB/DNS/TLS/SG/port/실제키/회전SLA와Identity user JWT/기존LC→Billing인증분리는별도검증. 기존Lattice/SigV4 revoke초안은 **본역방향JWT후보가양측수락된때** supersede한다. 배포/실키주입/권한확장없음.

## 5. eventId·version·stale 우선순위와 보존 후보

- logical key는 환경+authenticated producer=billing+consumerScopeId+attemptGroupId. accessVersion은이키단위positiveint64단조증가이며account전체version이아니다. rootrefund→fanout에서예약된group/version/body는불변이다.
- 검증/처리순서: 인증·strictdecode→**eventId 원장충돌검사**→**동일group/version 의미충돌검사**→group high-water와stale판정→projection/감사반영. stale먼저204로끝내지않는다.
- 동일eventId+같은canonicaldigest204,같은ID+다른digest409 EVENT_ID_CONFLICT 후보. 낮은version이어도이검사를먼저한다.
- 같은group/version+다른semanticdigest409 ACCESS_VERSION_CONFLICT 후보. semanticdigest는eventId/배달정보를제외한scope/group/originalowner/reason/occurredAt/confirmedSessionId/version으로동결. eventId는다르나같은semanticversion이면duplicate204+새IDdedupe기록 후보.
- 낮은version의처음본event는검증뒤STALE204·최소ID/digest/version감사보존. higher version의confirmedSessionId는 최초 적용한 예외와 동일해야 한다(null도 고정). null→새Session 추가뿐 아니라 기존Session→null 제거도 허용하지 않는다. terminal/탈퇴/삭제에 따른 종료는 LC lifecycle 증거로 별도 판정한다. 어떤version도REVOKED→ALLOWED로돌리지않는다.
- 충돌은이미적용된projection/원장을덮어쓰지않는다. 진단기록은최소hash/식별key/종류/발생·관측시각, rawpayload/token/개인정보로그금지.

보존 규칙 재검토 (새 제출 제한 없음):

1. eventId/canonicaldigest·group/version semanticdigest 및 충돌감사 record의 120일은 수신 멱등 기록 후보이며 보호 Session의 사용권한 만료가 아니다. 정확 보존기간은 양측 manifest로 수락한다. TTL 뒤 모든 eventId 충돌을 계속 탐지한다고 주장하지 않는다.
2. 진행 중 protected Session의 deny/high-water/예외/confirm 귀속 증거는 inbox TTL과 분리해 유지한다. 별도 timeout을 도입하지 않으므로 120일 일괄 purge로 허용된 Session을 차단하거나 root deny를 잃으면 안 된다.
3. 보호 Session이 없는 tombstone, terminal/탈퇴/삭제 이후 최소 replay fence와 개인정보 연결 purge는 LC 실제 deletion/lifecycle 보존 정책에 맞춰 정한다. groupId 재사용·학습/계정 재생성·paid 이전은 금지한다. 금융 최소 증거는 Billing5년 정책을 따른다.
4. R2 초안의 `occurredAt+120일` 신규 수신 제한과 EVENT_TOO_OLD/422는 **기본안에서 철회**한다. stale/conflict 검사를 대신하려는 새 기간 제한을 자동 도입하지 않는다. replay-safe tombstone 폐기가 필요한 경우에만 별도 manifest·복구 계약에서 검토한다.
5. 최초 revoke가 매우 늦게 도착해도 진행 중 confirmed Session 보호와 group 신규사용 deny를 동시에 보존해야 한다. 최소 deny/high-water를 purge하기 전 replay 재유입 차단·확인된 lifecycle 종료·전달 drain을 입증해야 한다. 이를 입증하지 못하면 활성화/purge 준비 미완료로 기록한다.

정확 폐기 시점/삭제 후 idempotency 범위/비식별 replay fence는 공동 확정 전 남은 항목이다. 무기한 사용자 개인정보 저장이나 조회 가능한 학습 원본5년 보존을 이 규칙으로 승인하지 않는다.

### merge/withdrawal/학습삭제 뒤 지연 도착

- event userId는당시Billing paid source owner snapshot이다. 현재LC userId만비교해정상지연event를버리거나target사용자에게paid권리를이전하지않는다. immutable group/session/source귀속과owner전이기록을검증한다.
- merge뒤에도paid자동이전없음. source owner와현재귀속관계가불명/충돌이면409격리·해당groupfail-closed하며기존target의무료/다른group를차단하지않는다. 원owner를새계정으로rewrite/계정생성하지않는다.
- withdrawal의사용차단/삭제fence가완료예외보다우선. confirmedSessionId가있어도탈퇴계정·terminal/삭제Session을부활시키지않는다. 지연revoke는최소tombstone으로commit204하고계정/학습문서를upsert하지않는다.
- 학습삭제뒤missingSession은미사용/무료권복원증거아님. deletedgroup/sessionfence 또는bounded tombstone을이용해deny를저장하고원본을재생성하지않는다. originaluser연결purge뒤확인불가를임의paid이전으로해석하지않음.
- idempotent동일ID/충돌/version검사는delete상태와무관하게보존기간내먼저수행한다. retained tombstone에는필요한group/version/hash/exact예외연결만최소보존하며source/target user 개인정보보존을120일로무조건늘리는승인아님. exact삭제/비식별keymanifest는LC와동결한다.

## 6. 추가 공동 fixtures

- source/legacy: PREPARED 미정 허용·reserve 성공 이후 누락 거절, manifest 포함 기존시험 보존·미포함 신규시험 누락 거절·manifest조회실패 임의허용금지, operation source복구/unknownenum·snapshot충돌, 기존시험 무소비·무복원.
- 제출: 새timeout없음/환불전confirm뒤지연제출허용/접수응답유실/기존grading제한과분리/revoke후exactSession허용/legacyconfirm증거누락/terminal·삭제·탈퇴우선/장기간OPEN의guard·증거보존/다른권리INITIAL 전환실패·응답유실시기존Session보호.
- 오류: 신규code정확mapper/공개409·terminal재호출/성공confirm응답유실뒤refund·status성공확인전보상금지/404·불명보존.
- revoke:16,384/16,385bytes, 독립키ON/OFF·samekid타서비스·wrongaud/issuer·Bearer/query/port·직접우회,DB업무무호출.
- 순서: 낮은version+sameID다른body409,동일version다른의미409,신규IDsameversionsame의미204,충돌기록보존·inbox기간경계·장기지연revoke·예외삭제금지·폐기후replay안전성.
- lifecycle: merge/withdrawal/원본삭제전후delayrevoke,확인불가귀속·deletedtombstone·paid자동이전/학습재생성금지.

## 7. 남은 동결 항목

새 제출 시간 제한 없음 확인,LC operation source·status exact DTO/legacymanifest,공개오류/보상증거,역방향profile/독립키/flags/connector,inbox/digest/highwater·lifecycle비식별 보존manifest 및공동fixture/hash를양측수락한다. 테스트완료/Billing011·012또는LC구현승인으로간주하지않는다.

## 8. 새 시험 시작의 그룹 전환 — 사용자 승인 보완

- 승인: 「어 그정도면 될 거 같다」. 별도 포기 버튼/종료 API를 먼저 만드는 방식은 채택하지 않는다. 기존 새 시험 시작 요청을 활용한다.
- 동일 유효 source: 기존 AttemptGroup 아래 replacement Session. 기존 consumption/source 유지.
- 환불된 source: replacement 차단. 환불 전 confirm된 진행 Session은 자동 종료하지 않고 완료 허용.
- 다른 유효 구매/무료권을 이용한 신규 시작(환불/만료로 재응시 불가인 경우 또는 §9의 승인된 paid 전환): Billing이 source를 판정하여 새 AttemptGroup으로 INITIAL 생성. 구매만으로는 전환하지 않으며 ACTIVE paid의 새 시험 시작은 §9를 따른다. 기존 그룹의 immutable 구매 연결/이력 보존, 이전 Session/guard의 종료·전환을 같은 논리적 operation의 durable 단계로 처리한다. 앱/LC가 임의로 source를 고르지 않는다.
- 사용자에게 별도 종료 단계는 없지만 구현 내부 전환은 필요하다. 기존 단순 abandon 함수를 paid saga 앞에서 바로 실행하지 않는다. 새 요청 실패/unknown commit/응답 유실 때 이전 Session만 먼저 종료하지 않도록 기존 operation과 status-first로 수렴시킨다. 최초 trial→paid의 이미 접수된 작업 완료는 §9/10의 예외를 적용하며 양 서비스 사이의 절대 무중첩을 요구하지 않는다. 그 외 전환의 권한은 이 예외로 확대하지 않는다.
- §9의 최초 trial→paid 전환은 최신 단순화 정책이 우선하며 LC 기존 접수 완료와 복원권 동시 보유를 허용한다. 그 외 요청 중 옛 Session의 제출/완료, 환불, replacement와의 경합도 공동 fixture에 포함한다. 전환 commit 전 확인 불명은 대기·복구이며 새 operation으로 우회하지 않는다. 전환이 확정되면 이전 Session을 다시 사용하지 않는다.
- 기존 새 시험 Idempotency-Key/body없음/성공DTO·무료 owner/epoch 계약 유지. 정확 전환 commit 시점·영속 단계·rollback/보상·guard/schema·이관은 PLAN011 및 LC 공동 확정 전 후보다. 제품 정책 수락이 코드/배포 승인을 의미하지 않는다.

## 9. 무료 시험 도중 구매한 경우 — 최신 승인으로 대체

[공통 무료 모의고사권·유료 전환 복원 정책](FREE-EXAM-PASS-AND-PAID-SWITCH-RESTORATION-2026-10-13.md)을 따른다. 구매만으로 기존 무료 그룹을 종료하지 않는다. ACTIVE paid로 새 시험을 시작하여 confirm에 성공하면 기존 미완료 trial 그룹을 종료하고 별도 무료권 1개를 지급한다. 원 TrialClaim·consumption·그룹 source와 이력을 보존하며 새 paid 그룹에 source를 명확히 기록한다.

복원 지급은 최초 trial의 원 consumption ID당 최대 1회이며 복원권/초대권 소비에는 지급하지 않는다. Billing에 이미 완료로 반영된 시험은 제외하되, 전환과 경합한 기존 접수의 지연 완료는 복원권과 함께 허용한다. paid 기간은 구매/timeline 기준을 유지한다. 실패/응답 유실/완료 경합은 durable 전환·CAS·status-first로 처리하고 이전 Session만 선종료하지 않는다. 기존 무료권의 owner 계약을 모든 미래 보상권에 자동 적용하지 않는다.

종전 무료 replacement 우선·새 무료권 미지급 정책은 이 범위에서 철회한다. 사용 순서·복원권 무기한·계정 귀속·Billing confirm과 LC 후속 복구 규칙도 위 정책에서 승인됐다. exact 공동 wire·schema·LC 전환 구현은 미확정이고 친구 초대 보상 구현은 후속이다.


## 10. LC 후속 마무리·성공 응답 — 사용자 승인

사용자가 2026-10-13 권장표에 「좋아 이렇게 해줘」로 승인했다. 기존 시험 생성 operation이 LC 후속 종료까지 책임진다. 별도 종료 전용 복구 작업 시스템은 추가하지 않는다. 이는 설계 방향 승인으로 LC 코드 수정/배포 완료를 뜻하지 않는다.

### 10.1 operation 완료 조건

1. operation에 원/신규 Session·group 연결과 전환 근거를 고정한다. Billing reserve snapshot/source를 보존하고 새 Session은 확인 대기 상태로 commit한다. 이 단계에서 이전 Session을 미리 종료하지 않는다.
2. Billing confirm 또는 동일 operation의 authoritative status로 성공 증거를 확보한다. 증거를 영속 저장하고 LC 마무리가 남아 있으면 operation을 복구 가능한 미완료 상태로 유지한다. 실제 enum/필드명은 LC 상세 설계에서 확정한다.
3. LC 로컬 Transaction에서 새 Session 사용 가능 처리, exact 이전 Session의 신규 제출 차단, operation의 전환 완료/SUCCEEDED를 함께 저장한다. 이미 terminal/삭제된 대상을 되살리거나 현재 다른 Session을 종료하지 않는다. lifecycle로 새 Session 사용이 불가능해졌다면 임의 활성화하지 않고 기존 종료 우선순위에 따라 안전하게 마무리한다.
4. 정상 생성의 공개 성공 응답은 3의 commit 이후 반환한다. 기존 성공 DTO를 유지하며 이전 Job의 채점 완료까지 기다리지 않는다. 중간 실패/처리 중 응답은 기존 재시도 가능한 API 계약에 맞추되 exact HTTP/code는 공동 확정한다.

confirm 성공만으로 SUCCEEDED/terminal noop/TTL 대상으로 만들지 않는다. 기존 reconciliation worker가 미완료 operation을 이어 처리하고, 영속 confirm 증거가 없거나 불명인 경우 동일 operation status-first로 확인한다. Billing 성공을 cancel하거나 새 operation으로 재지급하지 않는다. 미완료 operation은 TTL로 지우지 않고 재시도·경보/기존 운영복구 대상으로 유지한다. 공개 응답 유실은 같은 key로 동일 Session과 저장된 결과를 재현한다. 마무리 전 다른 요청이 새 전환을 만들지 않도록 기존 operation guard를 유지한다.

### 10.2 접수된 Job과 종료

LC의 durable 접수/Job 증거와 Session 종료를 로컬 CAS/Transaction으로 순서화한다. 종료 전에 서버가 durable하게 접수한 문항/요청에 근거한 작업의 결과·요약·기존 상태event v1 완료를 허용한다. Job 생성 시각이 아니라 원 접수 증거를 기준으로 한다. presigned URL·클라이언트 시각은 접수 증거가 아니다. 접수 응답 유실의 동일요청 재현과 시스템 채점 복구는 신규 제출과 구분한다.

종료 후 사용자 신규 제출/새 녹음·새 재응시로 만드는 Job은 차단하며 모든 ABANDONED callback을 일괄 허용하지 않는다. 이미 접수된 문항에서 종료 후 최초 생성되는 Summary Job과 시스템 채점/요약 재시도는 원 접수 증거에 연결되고 기존 멱등·lifecycle 조건을 만족하면 허용한다. 파생 Job 생성으로 새 문항 접수나 신규 제출 권한을 만들지 않는다. 일부 문항만 접수됐다면 해당 Job 처리만 허용하고 남은 문항을 추가 접수하지 않는다. 기존 전체 완료 evidence를 충족하지 못하면 COMPLETED를 만들지 않는다. 기존 Job 완료가 Session의 신규 제출 권한이나 Billing 원 그룹 재응시 권한을 다시 열면 안 된다.

최초 trial→paid 전환의 기존 접수 지연 완료와 복원권 동시 보유는 승인된 예외다. Billing은 지연 완료를 수용해도 원 그룹 재응시 차단·확정된 복원권을 유지한다. 그 외 전환까지 중첩 허용을 확대하지 않는다.

### 10.3 공동 fixture와 남은 기술 계약

confirm 전 실패/confirm 응답 유실/마무리 Transaction 실패·unknown commit/공개 성공 응답 유실·동시 새 시험을 검증한다. LC 마무리 commit 전 성공 응답 없음, 미완료 operation 재시도 가능·TTL 미삭제·guard 유지, 정상 마무리 후 같은 key 재현을 포함한다. 접수→종료/종료→접수 양순서, 종료 후 기접수 문항의 Summary Job 최초 생성·시스템 재시도 허용, 원 접수 증거 없는 신규 Job 거절, 부분 접수·callback/summary·terminal 상태 비부활을 검증한다.

필요한 신규 reserve/status 전환 근거 필드, source별 INITIAL/REPLACEMENT 및 mockExam 선택, authoritative confirm 거절 증거·영속복구, 역방향 revoke 인증/보존은 여전히 공동 동결 대상이다. source enum만으로 동일 구매·권리 또는 종료 대상임을 판정하지 않는다. 이번 승인으로 미확정 wire를 임의 구현하지 않는다.
