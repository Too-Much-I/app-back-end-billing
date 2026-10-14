# LC–Billing 유료 시험 공동 계약 R2 — 통합 검토 정본

- 작성일: 2026-10-14
- revision: `lc-billing-paid-exam-contract-r2-draft-2`
- 상태: 최신 사용자 승인 정책과 LC 제안을 통합한 공동 검토 정본. exact wire·오류·저장/보존은 양측 최종 수락 전이며 fixture 생성/실행·코드/DB/배포 완료를 뜻하지 않는다.
- 적용: Billing PLAN-011/012/013 및 LC 시험 생성·복구·lifecycle 연동.
- 현재 규칙은 이 문서에서 검토한다. 이전 draft-3의 두 문제 후보·전환 type·revoke version·별도 거절시각은 사용하지 않는다. 변경 이력은 [별도 기록](LC-BILLING-PAID-EXAM-CONTRACT-HISTORY.md)을 따른다.

## 1. 역할과 공통 경계

LC가 필수 답안 접수와 문제 선택을 판정하고 Billing이 사용 권리·소비·복원을 결정한다. 실제 source는 `EXAM_PASS` 또는 `SUBSCRIPTION`이며 최초 trial/복원/초대는 Billing 내부 출처다. 사용자에게 새 시험은 새 Session이고 이전 답안·점수·Job·요약을 승계하지 않는다.

공개 `POST /api/v1/exams`는 body 없음·기존 Idempotency-Key·성공 DTO/BaseResponse 유지. 앱은 시작 사유/source/이전 group를 지정하지 않는다. 내부 LC→Billing 여섯 POST는 [기존 A 인증 계약](LC-BILLING-WORKLOAD-AUTH-R1.md)을 유지한다. reserve/confirm/cancel의 operationId는 Idempotency-Key header만 사용한다. status는 `POST /internal/v1/reservations/status` body의 userId/operationId로 조회하고 header를 요구하지 않으며 command/ledger를 만들지 않는다.

내부 요청/응답 상한16KiB, strict JSON의 duplicate/trailing/unknown/coercion/잘못된 식별자 형식 거절 및 기존 opaque Session/mock ID 규칙 유지. 신규 optional object는 absent 허용·명시 null 거절, 명시 nullable field만 null 허용이다. 기존 phone/event DTO에 신규 optional 규칙을 소급 재해석하지 않는다.

## 2. 단일 문제 선정·reserve 요청

기존 reserve userId/sessionId/mockExamId와 기존 phone continuation 계약을 유지한다. Billing-linked 원 시험이 있는 일반 새 시작에 optional restartContext를 추가한다.

| field | 형식/의미 |
| --- | --- |
| restartContext.previousAttemptGroupId | 원 group lowercase UUID v4 |
| restartContext.previousSessionId | 원 Session의 기존 opaque ID; 삭제된 경우 검증된 보존 sourceExamId |
| restartContext.startReason | RESTART_INCOMPLETE / NEW_AFTER_SUBMISSION / NEW_AFTER_COMPLETION |

세 필드는 all-or-none, 부분 object400 INVALID_REQUEST. initialMockExamId는 없다. top-level mockExamId는 LC가 실제 사용할 단일 문제다. 기존 시험 없음/Billing 미연결 legacy만 있는 경우 restartContext 없이 일반 INITIAL이며 legacy 대상은 LC operation에만 저장한다. source 없는 시험에 Billing group/consumption을 사후 생성하지 않는다.

| 시작 사유 | LC 근거/문제 | Billing 판정 |
| --- | --- | --- |
| RESTART_INCOMPLETE | 일부 또는0개의 필수 retry0 답안 접수임을 증명, 같은 mock | 동일 유효 권리면 원group REPLACEMENT; 다른 유효 권리로 전환하면 새group INITIAL 가능 |
| NEW_AFTER_SUBMISSION | 모든 필수 retry0 답안 durable 접수, 전체결과 미완료 | 새group INITIAL·신규 권리 판정; Billing OPEN이어도 REPLACEMENT 금지 |
| NEW_AFTER_COMPLETION | 원 시험의 권위 있는 완료 증거 | 새group INITIAL·신규 권리 판정 |

NEW_AFTER_SUBMISSION은 직전 mock를 제외한 적격 후보에서 기존 완료횟수/sequence 선정 규칙을 적용한다. 다른 적격 후보가 실제0개일 때만 직전 mock 허용. catalog 조회/선정 오류를 후보0으로 처리하지 않는다. NEW_AFTER_COMPLETION/시험없음에는 기존 선정 규칙을 적용한다. 같은 mock가 선택돼도 NEW_AFTER_SUBMISSION/COMPLETION은 INITIAL이다. 기존 완료 count를 가짜로 늘리지 않는다.

startReason은 인증된 LC 서버 증언이다. Billing은 caller/owner·원group/session 귀속과 권리/차단 상태를 검증하고 사용하며 source enum/mock 일치/OPEN 상태만으로 재응시를 추정하지 않는다. 사유가 RESTART여도 무료권 소비 면제/추가 지급이나 환불 권리 replacement를 강제하지 않는다. 유효 권리가 없으면 기존 부족 오류로 거절한다.

### 2.1 접수 증거·순서·재전송

신규 기능의 durable 접수 증거는 OS/알림 tracking flag와 독립적으로 보장한다. marker 부재는 INCOMPLETE 증거가 아니다. 불변 필수 문항 집합·retry0·중복 제거 및 사용자 제출/시스템 복구 Job 구분을 사용한다. receipt TTL 뒤에도 필요한 완료 marker를 보존하며 증거 불명/충돌은 검증·복구한다.

원 Session의 마지막 접수와 operation 준비를 같은 version/fence의 Transaction/CAS로 순서화한다. 준비가 먼저면 최초 INCOMPLETE/같은mock, 마지막 접수가 먼저면 NEW_AFTER_SUBMISSION으로 고정한다. 사유·원대상·선정mock는 canonical 요청 digest와 operation에 불변 저장한다. 이후 접수·요약·catalog 변화가 같은 key의 결과를 바꾸지 않는다.

인증/허용owner fence→canonical 요청 동일성→기존 operation 결과 재현→신규 current Session stale 검증 순서다. 소유권 상실은 replay로 우회하지 않는다. 같은 key 다른 payload는 conflict이며 저장된 성공/실패를 덮어쓰지 않는다. TTL 후에도 영속 결과가 있으면 동일하게 재현한다.

## 3. reserve/status 응답과 조합

기존 reserve/status 응답에 필수 entitlementSource와 optional transition을 추가한다. source는 reserve 성공 이후 필수이며 reserve 전 PREPARED에는 미정 가능하다. 실패/생략 응답으로 이미 저장한 source/transition을 null로 덮어쓰지 않는다.

transition은 `{previousAttemptGroupId, previousSessionId}`만 가진다. type·복원수량·purchaseId를 추가하지 않는다. 동일 operation reserve/status에서 immutable snapshot을 재현한다. startReason echo는 추가하지 않고 LC 저장 요청과 대조한다.

| 요청/결과 | group/mock | transition |
| --- | --- | --- |
| 원대상 없는 INITIAL | 새group, 요청mock | 없음 |
| 원대상 있는 REPLACEMENT | 원group, 원mock=요청mock | 없음 |
| restartContext가 있는 INITIAL | 원group와 다른 새group, 요청mock | 필수·요청의 원group/session과 exact 일치 |
| NEW_AFTER_SUBMISSION/COMPLETION에 REPLACEMENT | 허용하지 않음 | 계약 오류 |

새 source/시작 사유로 유효 권리가 부족하면 새 요청을 거절하고 이전 시험을 먼저 종료하지 않는다. 모든 신규 Session은 요청 sessionId이며 원 Session과 달라야 한다. 완료 후 INITIAL에도 restartContext가 있으면 transition을 반환한다. object 존재가 완료된 원 시험을 종료/재활성화하라는 뜻은 아니다. 불필요한 transition/필수 transition 누락·type/구initialMock 필드는 거절한다. RESTART_INCOMPLETE INITIAL도 같은 문제를 사용하되 권리는 Billing 선택을 따른다.

모든 권리 전환의 LC 완료 처리는 동일하다. OTHER_SOURCE_CHANGE라는 별도 type/완료정책 차이는 없다. 다만 source별 권리 선택/owner/phone 조합의 유효성까지 무조건 허용하는 것은 아니다. exact 원연결/source/kind/mock는 snapshot→operation→Session/복구증거에 보존한다.

## 4. confirm·복원·LC 마무리·오류

`reserve → 확인대기 LC Session commit → confirm/status 성공 증거 → LC 마무리 Transaction → 공개 성공`을 따른다. Billing은 유료 확정·복원 자격 충족 시 지급·원group 재응시 차단을 같은 Transaction으로 처리한다. 구매만으로 지급하지 않는다. 최초 trial consumption당 최대1회, 복원/초대권 소비에는 재복원 없음. 원 Claim/consumption·paid timeline 유지.

전체 접수라는 사유만으로 새 복원 자격을 부여하거나 기존 복원 자격을 철회하지 않는다. Billing 완료 반영과 confirm 전환 경합에 따른 기존 자격 판정을 사용하고, 늦은 완료 때문에 확정 복원권을 회수하지 않는다.

LC는 exact 이전 Session 신규제출 차단·새 Session 활성·operation SUCCEEDED를 로컬 Transaction으로 저장한 뒤 성공 응답한다. 이미 완료/삭제된 대상과 lifecycle deny를 존중하며 다른 현재 Session을 대신 종료하거나 삭제대상을 재생성하지 않는다. 기접수 채점이 끝날 때까지 성공 응답을 기다리지 않는다.

종료 전 durable 접수 문항의 채점·결과 저장·후속 Summary 최초 생성·시스템 재시도는 원 접수 증거와 기존 멱등/lifecycle 조건 아래 허용한다. 종료 후 사용자 신규 제출·새 녹음/재응시 유래 Job은 차단한다. 부분 접수는 전체 완료 evidence 없이는 COMPLETED가 아니다. 완료한 결과는 원 시험에 남고 원group 재응시 차단은 유지한다. 원 시험의 늦은 완료는 원 group/Session에만 반영하며 신규 group 또는 미완료 생성 operation의 guard를 해제하지 않는다.

미완료 operation/guard는 마무리까지 유지하고 TTL 삭제하지 않는다. 같은 operation worker retry·경보로 복구하며 성공confirm 취소/새 operation 재지급 금지. 상태 불명에서 RESERVED/404만으로 Session을 보상하지 않는다.

### 4.1 확정 거절·status

status의 optional `confirmRejection`은 `{code:"ENTITLEMENT_REVOKED"}`만 사용한다. CANCELED에서만 허용하며 시각은 terminalAt이다. RESERVED/CONFIRMED/EXPIRED+거절object는 계약 오류다. Billing은 최초 irreversible 거절에서 RESERVED→CANCELED·거절code·terminalAt·권리정리를 같은 Transaction으로 확정한다. revoked held 권리를 사용 가능 수량으로 되살리지 않는다. 일반cancel에는 거절object가 없다.

이미 CONFIRMED면 첫 성공을 재현하고 거절로 바꾸지 않는다. cancel 성공 DTO가 거절 사유를 생략하면 LC는 status에서 확인해 일반취소/환불거절을 구별한다. authoritative CANCELED/EXPIRED 또는 영속 거절·귀속 증거 확인 뒤만 대기 Session을 안전 보상한다.

| 상황 | 공개 응답 방향 |
| --- | --- |
| 진행 operation/마무리/guard | 기존409 EXAM_CREATION_PROCESSING, Retry-After:1 |
| 일시 저장소/통신 장애 | 기존503 BILLING_TEMPORARILY_UNAVAILABLE, Retry-After:1 |
| 영속 revoke 거절 | 신규409 ENTITLEMENT_REVOKED, “환불 또는 취소된 이용권으로는 시험을 시작할 수 없습니다.” |
| 신규 전환 대상 stale/잘못된 context | 내부409 TRANSITION_CONTEXT_CONFLICT→신규공개409 EXAM_TRANSITION_CONFLICT, “시험 상태가 변경되었습니다. 다시 확인해 주세요.” |

새 공개409는 앱 reader와 공동 검증한다. pending은 새 key 생성 지시가 아니다. 업무OPERATION_NOT_FOUND/ingress404 CONTRACT_ERROR/auth circuit/일시503은 내부 처리·격리/경보를 구분하고 무조건 자동 retry로 통합하지 않는다. confirm/cancel 기존 성공 DTO·public envelope는 유지한다.

성공·거절 및 reservation 생성 전 terminal context실패도 최초 digest/결과를 영속 저장해 same-key replay한다. command7일TTL과 분리하며 TTL 뒤 현재상태로 실패를 다시 계산하지 않는다. 이 영속 receipt의 정확 보존/개인정보 purge는 §7 gate다.

## 5. 불변 revoke 계약

`POST /internal/v1/entitlements/access/events`, 최대16,384 raw bytes, redirect 금지. logical group key는 환경+인증producer+consumerScopeId+attemptGroupId다. Billing은 group당 최초 outbox/eventId/내용 하나를 Transaction/unique 제약으로 고정한다. 이후 금액 정정·추가 환불은 금전 원장만 기록하고 새 revoke/새 보호Session을 만들지 않는다.

| body field — canonical 순서 | 형식 |
| --- | --- |
| eventId | lowercase UUID v4 |
| eventType | AttemptGroupAccessRevoked |
| schemaVersion | 정수1 |
| producer | billing |
| consumerScopeId | 배포 환경의 exact 합의값 |
| occurredAt | 최초 적용 UTC YYYY-MM-DDTHH:mm:ss.SSSZ |
| userId | 당시 owner lowercase canonical UUID |
| attemptGroupId | lowercase UUID v4 |
| reason | PURCHASE_REFUNDED / PURCHASE_REVOKED |
| confirmedSessionId | 필수 nullable opaque Session ID, 최초 보호 대상 또는 null |

버전/high-water/stale/별도 semantic digest는 없다. canonical SHA-256 하나를 사용한다. 동일 eventId·동일digest는204, 같은ID다른내용은409 EVENT_ID_CONFLICT, 같은group에다른ID는 내용이 같아도409 GROUP_REVOKE_CONFLICT다. 검사 순서는 eventId충돌→group충돌→최초적용/duplicate다. LC는 eventId와group 양쪽 unique 및 Transaction으로 inbox/deny를 원자 저장한다. 최초/duplicate는 commit 확인 후 body없는204, 동시first수신도 하나로 수렴한다. inbox TTL 뒤에도 group의 최초 eventId/digest가 남아 있으면 그 증거로 같은ID·같은내용은204, 같은ID다른내용은 EVENT_ID_CONFLICT409, 같은group다른ID는 GROUP_REVOKE_CONFLICT409로 처리한다. inbox 부재만으로 새 최초 사실을 만들지 않는다.

최초 eventId·reason·occurredAt·confirmedSessionId(null 포함)는 불변이다. 차단된 group의 신규사용/replacement는 금지하되 보호된 exact Session의 완료만 허용한다. lifecycle deny/삭제/탈퇴/terminal 종료가 우선이다. 과거 원 owner 정보로 권리를 다른 사용자에게 자동 이전하지 않는다.

### 5.1 canonical bytes와 오류

검증한 값을 표 순서 compact JSON으로 직렬화한다. UTF-8 BOM/마지막LF/불필요공백 없음, null 포함, SHA-256 lowercase64hex. Unicode normalization 없음, 유효 scalar는 그대로 UTF-8, 한글/slash 비escape, quote/backslash JSON escape, U+0000~001F는 lowercase hex `\u00xx`만 사용한다. 유효 surrogate pair는 scalar로 디코딩하며 lone surrogate/손상UTF8는거절한다. schemaVersion은정수, 시각은정확 millisecond Z형식과실제날짜검증, 숫자를float로중간변환하지않는다.

크기413 INVALID_REQUEST→JSON문법/duplicate/unknown/필수/타입/형식400 INVALID_REQUEST→유효형식의미지원schema/producer/scope/reason422 UNSUPPORTED_CONTRACT 순이다. unknownfield와미지원schema가동시면400. 구accessVersion/semantic 필드는unknown으로거절한다. DB일시장애503 LC_TEMPORARILY_UNAVAILABLE 후보. 인증·route 검사는 body보다 먼저다. token/body bytes와escaping 기대값은golden fixtures로확인한다.

### 5.2 역방향 JWT/HTTP

| 항목 | 공동 검토값 |
| --- | --- |
| issuer / sub | urn:tosunsaeng:workload:billing:{env} / billing-service |
| audience | tosunsaeng-learning-core:entitlement-access-events:{env} |
| env | test/staging/production exact |
| 키 | revoke 전용 RSA2048+ RS256, 배포 public JWK set, kid exact |
| 시간/문법 | exp=iat+60, optional nbf=iat, 수신TTL1~60/skew30; 기존 A의 Bearer/JOSE/claim/정수NumericDate·소수초now 규칙 |
| 발신설정 | billing.payment.access-publisher-enabled 기본false, billing.payment.access-workload-jwt.environment/private-jwk |
| 수신설정 | app.billing.access-events.receiver-enabled 기본false, app.billing.access-events.environment/billing-public-jwk-set |

ON키/환경필수검증·OFF미파싱, user/Identity/LC→Billing trust와분리·고정verifier·원격fetch/fallback없음. 재시도는원event/body유지·JWT/jti만새발급. TLS공유는가능하나전용chain/port검증필수.

protected공간 후보는지정connector의 `/internal/v1/entitlements/access` 및하위다. 살아있는서버의OFF/잘못된port는404/no-store→활성query(빈?포함)/JWT실패401/no-store→검증된wrongprincipal/aud/method/path403/no-store→body검증. 포트미기동은transport실패. 기존user/Identity route를가로채지않으며 namespace/chaininventory확인전활성화하지않는다. issuer문자열만으로403처리하지않으며 검증불가능한타서비스token은401이다.

## 6. legacy·phone·삭제 continuation

기존 LC 시험은 전환 직전 서버 시험ID목록으로 구분한다. 원 Session에source를backfill하지않고 기존이용/이력을보존한다. 신규/의도적재응시는Billing필수. source누락/클라이언트날짜/현재flag로legacy를추정하지않는다. 구writer drain·최종inventory·서버manifest보관/보고서hash건수·신규writer 전환을계획한다. 실제prod확인은 [inventory](../plans/PLAN-011-SOURCE-PRODUCTION-INVENTORY-2026-10-13.md)를따르며 전환직전재확인한다.

phone/삭제경로는새단순화만으로owner·권리규칙을바꾸지않는다. 다음은제안과확정대기를구분한다.

| 경로 | 방향 | 공동확인 필요 |
| --- | --- | --- |
| 삭제보존 | sourceExamId/group/mock 및접수증거로restartContext 구성,claim operation결속·confirm후마무리에서transfer | 접수/완료증거보존schema·미보유기존row처리·TTL/실패해제/unknown복구 |
| phone | 기존discovery/exactecho/owner-epoch유지, 기존phone필드와restartContext 배타 | startReason 전달/판정방법, ACTIVE paid와동시선택의정상경로 |
| 일반context누락 | Billing-linked 재응시/전환대상있고유효phone도없으면409 TRANSITION_CONTEXT_CONFLICT | 구writer drain/기존성공replay 예외검증 |

원Session없음만으로삭제경로 INCOMPLETE를만들지않는다. phone+ACTIVE paid의정상선택은미확정이며 기존draft의409거절은임시fail-closed 제안이지사용자에게영구제한을승인받았다는뜻이아니다. 해당조합·삭제증거불명 경로는정상지원정책을확정하기전활성화하지않는다. currentSession으로원대상을임의교체하지않는다.

## 7. 보존·활성화 gate

| 자료 | 기준/미확정 |
| --- | --- |
| Billing금융원장 | 기존5년·backup최대35일 유지 |
| LC미완료operation | 완료/권위실패복구전TTL삭제없음·guard/worker/경보필수 |
| reserve/confirm/실패receipt | commandTTL과분리한영속replay; exact보존·owner/탈퇴/삭제purge 공동동결필수 |
| revoke event inbox | 첫관측기준120일후보,duplicate로무한연장금지 |
| group최초eventId/digest/deny/보호예외 | inboxTTL과분리,열린보호Session 권한을TTL로종료하지않음 |
| terminal tombstone·legacy manifest | 최소매칭/replay증거와개인정보연결분리,exact폐기/재유입차단·삭제연계동결필수 |

무기한 개인정보보존 또는 임의120일전체삭제를 승인하지않는다. 보호예외/최초사실증거소실뒤에도 충돌을계속검출한다고주장하지않는다. 폐기 gate를정하고그전에자동purge를활성화하지않는다.

배포전실origin/port/connector·키회수SLA·namespace확인,reader선배포/source writercoverage·구worker drain,legacy최종목록,phone/삭제 지원정책·보존gate,앱신규409reader,복구/운영경보·E2E가필요하다. 이 gate는오프라인fixture 검증과구분한다.

## 8. 새 공동 fixture 제작 명세

아래는 새revision용 목록이며 실제bytes/token/hash/실행결과는없다. 기존A fixtures-1과draft-3 snapshot은수정하지않는다. 미확정phone/보존을확정한것처럼expected를꾸며넣지않는다.

| ID | 검증 범위/기대 |
| --- | --- |
| S01 | 시험없음/legacy INITIAL, transition없음·legacy무소비무복원 |
| S02 | INCOMPLETE 같은mock·유효권리REPLACEMENT/권리전환INITIAL |
| S03 | SUBMISSION 직전mock제외,실제다른후보0시동일허용·조회장애fallback금지 |
| S04 | SUBMISSION+BillingOPEN/같은mock도INITIAL;COMPLETION기존선정 |
| S05 | marker없음/trackingOFF/receiptTTL/필수retry0중복·시스템Job구분 |
| S06 | 마지막접수/준비 양순서,준비후catalog/요약변화 samekey고정 |
| S07 | samekey사유변경conflict·성공후stale검사보다replay·owner상실거절 |
| S08 | kind/source/group/session/mock/transition필수·불필요 부정조합 |
| S09 | 최초trial복원1회·복원/초대0·동시confirm/지연완료·원장보존; 새source/사유 권리부족 시 이전시험 선종료 없음 |
| S10 | confirm전실패/성공유실/LC마무리실패·unknowncommit·guard/TTL/성공응답시점 |
| S11 | 종료전접수후채점/Summary최초생성·시스템재시도허용,종료후사용자접수거절 |
| S12 | 부분접수비완료·전체evidence·완료/삭제대상비부활; 원시험 늦은완료가 신규group/operation guard를 해제하지 않음 |
| S13 | CANCELED+code+terminalAt/일반cancel·금지조합/cancel후status/성공보존 |
| S14 | commandTTL후성공/실패재현,생성전terminal실패receipt,기존409/503·신규두409 |
| S15 | context누락/phone배타·삭제원문서없음증거/claim/transfer — 정상분기는§6동결후 |
| S16 | legacy목록경계·누락상태기존규칙·manifest조회실패/최종inventory |
| V01 | 단일event 최초/같은ID동일digest204·같은ID다른digest409; inboxTTL 후 retained group 최초증거로 동일 재전송204/새ID409 |
| V02 | 같은group다른ID 동일/다른내용 모두409·동시첫수신/produceroutbox경합 |
| V03 | 최초null/Session불변·후속금액정정재발행없음·다른권리보존 |
| V04 | 단일canonical golden bytes:한글/slash/제어/유효pair/lone surrogate/UTF8/날짜/숫자 |
| V05 | 구version/type등unknownfield·오류우선순위·16384/16385bytes |
| V06 | 역방향JWT/A문법·시간/키/rotation·query/OFF/port/principal/namespace격리 |
| V07 | inboxTTL/group증거분리·지연revoke/삭제/탈퇴/merge·폐기gate — exact는§7동결후 |

공통 JSON/HTTP 기대 결과와 golden bytes를 먼저 맞춘다.fixture manifest에는revision/파일SHA256/고정now/테스트키출처를기록한다. 가짜식별자·키만사용하며 양측독립검증은각저장소결과로기록한다. LC마무리/Transaction/경합fixture는실제양측업무구현검증과구분한다. 최종공동수락·fixture제작→bytes/hash대조→독립검증의근거를남기고제품구현/실배포승인과혼동하지않는다.

## 9. 공동 회신 요청

1. startReason 세enum·단일mock 및 source/kind/transition 조합 수락.
2. 모든 권리 전환의 공통 마무리,전체접수후 신규권리판정 및 Billing복원전담 수락.
3. 불변eventId/group 충돌규칙·canonical필드목록·terminalAt재사용 수락.
4. phone/삭제 접수증거와 정상권리선택·보존/삭제gate의담당/잔여목록확정.

LC의 2026-10-14 통합 R2 draft-1 검토에서 1~3 기술 내용에 추가 이견 없음이 전달됐다. startReason/transition/충돌 명칭 선택을 다시 요구하지 않는다. 이 회신은 최종동결이나 fixture 제작 실행 승인이 아니다. LC는 phone/삭제 접수증거·신규writer/legacy manifest·claim/transfer/lifecycle·receiver namespace, Billing은 phone+paid 정상권리선택·retained 원group/session/owner 증거·실패receipt/금융보존을 확인한다. 양측은 지원조합/삭제/replay/실제키/port/E2E를 동결한다. S15/V07의 미확정 정상분기를 가짜 기대값으로 통과 처리하지 않는다.

1~3을 오프라인 fixture 세부화 기준으로 사용할 수 있으나4의미확정경로까지최종동결/활성화완료로보고하지않는다. 이번정본통합으로코드/DB/외부전송/배포를수행하지않았다.
