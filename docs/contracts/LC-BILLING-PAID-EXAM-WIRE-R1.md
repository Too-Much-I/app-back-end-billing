# LC–Billing 유료 시험 공동 기술 계약 R1 — Billing 제안

> 2026-10-14 공동 기술 검토 기준 통합: [유료 시험 계약 R2](LC-BILLING-PAID-EXAM-CONTRACT-R2.md)를 우선 참조한다. 최신 승인 정책·LC 최소wire 제안을 한 곳에 정리했으며 본문의 종전 기술 후보는 변경 이력이다. 새정본의 exact 공동수락/fixture/구현·배포는 미완료이고 phone/삭제/보존 gate는 별도 확인한다.

> 2026-10-14 후속 사용자 승인: [단순화 인계](LC-BILLING-PAID-EXAM-SIMPLIFICATION-HANDOFF-2026-10-14.md)를 함께 읽는다. 두문제후보·전환유형별LC완료·revoke version·rejectedAt은 새 방향으로 대체 예정이며 본 draft-3 그대로 fixture/코드를 만들지 않는다. 최소 wire는 공동확인후 새정본으로 통합한다. 전달 snapshot DRAFT-3은 역사기록으로 보존한다.

- 최초 작성: 2026-10-13
- 보완일: 2026-10-14; LC draft-2 재검토의 status/오류 문구 정합성 반영
- revision: `lc-billing-paid-exam-wire-r1-draft-3`
- 상태: 사용자 요청으로 작성한 공동 검토 초안. 제품 정책은 승인됐으나 이 문서의 신규 field/enum/오류/인증·보존 상세는 LC 수락 및 fixture 동결 전 후보다. 구현·DB·배포·외부 전송 미실행.
- 기준: [R2](LC-BILLING-PAID-EXAM-HANDOFF-R2.md), [무료권 정책](FREE-EXAM-PASS-AND-PAID-SWITCH-RESTORATION-2026-10-13.md), [기존 시험 보존](../plans/PLAN-011-SOURCE-PRODUCTION-INVENTORY-2026-10-13.md).
- 목적: 정책을 다시 선택하지 않고 reserve/status·전환·복구의 구체적인 양측 계약을 검토한다. 신규 wire에 관해서는 이 초안을 함께 검토하며 기존 확정 A 인증 계약을 변경하지 않는다.

## 1. 공통 경계

공개 `POST /api/v1/exams`의 body 없음·필수 lowercase UUID v4 Idempotency-Key·성공 DTO를 유지한다. 앱은 source/purchase/group를 선택하지 않는다. 같은 요청은 같은 operation/Session으로, 의도적 새 시작은 새 operation/Session으로 처리한다.

내부 여섯 POST의 기존 LC→Billing workload JWT A 계약·기존 field의 형식/크기/멱등성은 유지한다. 내부 JSON은 기존 DTO 직접 반환, unknown/duplicate/trailing/coercion 거절이다. 신규 object는 아래 명시된 필드만 허용한다. optional object는 없음으로 표현하고 명시 null은 거절한다. nullable로 지정한 필드만 null을 허용한다. reserve/confirm/cancel command의 operationId는 기존처럼 Idempotency-Key header에서 받고 body에 중복하지 않는다. `POST /internal/v1/reservations/status`는 예외적으로 기존 body의 userId/operationId로 조회하며 Idempotency-Key header를 요구하지 않는다. status는 읽기 전용이고 새 command나 ledger를 만들지 않는다. phone continuation 및 AttemptGroup event에는 각 기존 요청 계약을 적용하며 이 command header 규칙을 일괄 적용하지 않는다.

## 2. reserve 요청과 문제 선택

기존 `userId/sessionId/mockExamId` 및 phone continuation 3필드를 유지하고 다음 optional object를 추가하는 안이다. 기존 요청 3필드만으로 이전·신규 문제 후보를 모두 표현할 수 없어서 필요한 확장이다. `mockExamId`의 기존 의미를 조용히 변경하지 않는다.

| 신규 field | 형식 | 의미 |
| --- | --- | --- |
| restartContext | optional object | Billing에 연결된 기존 시험을 두고 새 시험 시작을 요청할 때 |
| restartContext.previousAttemptGroupId | lowercase UUID v4 | LC가 고정한 원 그룹 |
| restartContext.previousSessionId | 기존 1~128자 opaque Session ID | LC가 고정한 원 Session |
| restartContext.initialMockExamId | 기존 1~128자 opaque mockExamId | 새 INITIAL로 전환할 때 사용할 LC 선정 문제 |

restartContext의 세 필드는 모두 필수이며 부분 객체는 400 INVALID_REQUEST다. phone continuation과 동시 사용은 400이다. LC는 문제 권한/존재/선정 정책을 검증하고 두 후보를 operation에 고정한다. Billing은 시험 내용을 조회하거나 문제를 선정하지 않는다. 기존 group 존재 시 top-level mockExamId는 그 group의 기존 문제를 echo하며, INITIAL 후보는 initialMockExamId다. 각 값은 canonical payload digest에 포함돼 같은 key의 다른 후보는 conflict다.

legacy 시험만 있으면 restartContext를 보내지 않는다. 기존 legacy Session 종료 대상은 LC operation에만 저장한다. Billing 그룹을 만들어 소급 연결하지 않는다.

| Billing 판정 | 응답 kind/group/mock | 원 시험 처리 |
| --- | --- | --- |
| 기존 Billing group 없음 | INITIAL / 새 group / top-level mockExamId | legacy 대상이 있으면 LC 확정 후 처리, 복원 없음 |
| 동일 유효 권리로 재응시 | REPLACEMENT / 원 group / top-level mockExamId | 기존 replacement 규칙, 추가 소비 없음 |
| 최초 trial→ACTIVE paid | INITIAL / 새 group / initialMockExamId | confirm 시 복원 자격·원 consumption당1회, 원 group 재응시 차단 |
| 원 source로 재응시 불가, 다른 유효 권리 있음 | OTHER_SOURCE_CHANGE 대상 후보 | 이 초안의 최초 활성화에서 제외; 아래 §9 적용 |
| 확인 불명·전환 대상 stale | 성공을 강제하지 않음 | 아래 오류/재시도 계약 |

Billing은 이전 group/session/current owner·귀속·원 그룹 문제 및 전환 가능 상태를 저장 근거로 확인한다. 권리 선택은 Billing paid-first 판정이다. source enum이 같아도 다른 구매/권리일 수 있으므로 enum만으로 REPLACEMENT를 결정하지 않는다. 요청한 이전 Session이 stale이면 다른 최신 Session을 대신 종료하지 않는다.

일반 free→paid(복원권/미래 초대권 source 포함)는 OTHER_SOURCE_CHANGE 후보이며 최초 활성화에서 제외한다. 복원은 최초 trial만이고 그 밖의 전환 완료 정책은 아직 공동 동결되지 않았다. 해당 type을 임의 TRIAL_TO_PAID로 바꾸지 않는다.

INITIAL 문제 후보가 기존 문제와 같을 수 있는지는 LC의 기존 문제 선정 정책을 따른다. 새 문제를 강제로 다르게 만드는 제품 정책은 추가하지 않는다. REPLACEMENT는 기존 mockExamId를 유지한다. phone/삭제 continuation은 기존 exact echo/owner 계약을 유지하며 paid 전환과 충돌하면 이번 요청에서 임의 혼합하지 않는다. 삭제 continuation의 실제 매핑과 새 wire 적용안은 §9.2를 따른다.

## 3. reserve/status 응답

기존 응답에 `entitlementSource`와 optional `transition`을 추가한다. reserve 성공과 status의 동일 reservation snapshot에는 불변값을 동일하게 재현한다. 새 confirm/cancel 성공 DTO는 유지하며 field가 없는 응답을 읽어 기존 source/transition을 null로 덮어쓰지 않는다.

| field | 값/조건 |
| --- | --- |
| entitlementSource | reserve 성공 이후 필수 `EXAM_PASS` 또는 `SUBSCRIPTION` |
| transition | Billing-linked 이전 group에서 새 INITIAL로 바뀔 때만 필수 object. 일반 INITIAL/REPLACEMENT에는 없음 |
| transition.type | `TRIAL_TO_PAID` 또는 `OTHER_SOURCE_CHANGE` |
| transition.previousAttemptGroupId | 요청과 같은 원 group |
| transition.previousSessionId | 요청과 같은 원 Session |

전환 식별자는 기존 operationId를 사용한다. 별도 transitionId/version을 추가하지 않는다. 새 group/session/mock는 기존 top-level 응답에 있으므로 중복하지 않는다. TRIAL_TO_PAID는 최초 trial의 소스임을 Billing이 증명할 때만 반환하며 그 외 EXAM_PASS→paid는 OTHER_SOURCE_CHANGE다. 복원 수량이나 purchase ID를 LC에 전달하지 않는다. transition은 reserve에서 고정한 계획이며 실제 복원 commit 증거는 confirm 성공이다.

LC는 replacement 예상 중 INITIAL이 왔다고 무조건 허용하지 않는다. 유효 restartContext와 응답 transition의 exact 원 대상 일치, 새 group, 응답 mock=initialMockExamId를 함께 확인한다. 일반 INITIAL은 요청 mock와 일치, REPLACEMENT는 기존 group/mock와 일치해야 한다. 신규 Session ID는 요청과 동일해야 한다. source/transition/원·신규 연결은 client snapshot→operation→Session/복구 증거에 저장한다.

status에는 다음 optional 결과 object도 제안한다. reserve 응답에는 넣지 않는다.

| field | 값/조건 |
| --- | --- |
| confirmRejection | 해당 reservation의 irreversible confirm 거절이 영속 확정된 경우만 object |
| confirmRejection.code | 이 초안에서는 `ENTITLEMENT_REVOKED`만 |
| confirmRejection.rejectedAt | 최초 거절 Transaction의 UTC Instant, 재시도 때 불변 |

거절은 RESERVED→CANCELED와 confirmRejection 저장을 같은 Billing Transaction에서 수행하는 안이다. 기존 CANCELED를 confirm으로 되돌리지 않는 규칙을 재사용하고 REJECTED reservation enum은 추가하지 않는다. cancel 요청이 들어왔다는 의미로 기록하지 않고 원장에는 revoke로 인한 거절 이유를 분리한다. held 권리가 있다면 기존 revoke 정책에 맞게 정리하며 revoked 권리를 사용 가능 수량으로 되살리지 않는다. 이 상태 전이/원장 변경은 공동 검토 대상이다.

이미 CONFIRMED면 confirmRejection을 만들지 않고 최초 성공을 재현한다. status CONFIRMED의 terminalAt은 기존 성공 확정 시각이며 source/Session/transition을 함께 반환한다. 성공과 거절 증거가 동시에 존재하는 snapshot은 계약 오류다. cancel/expiry terminal과 거절 snapshot은 시간이 지나도 임의 재분류하지 않는다.

## 4. 오류·보상·operation 수명

| 상황 | Billing/LC 내부 처리 | LC 공개 처리 후보 |
| --- | --- | --- |
| revoked 권리 최초 confirm | 409 ENTITLEMENT_REVOKED, retryable=false, 영속 거절 후 반환 | 409 ENTITLEMENT_REVOKED, 기존 BaseResponse, “환불 또는 취소된 이용권으로는 시험을 시작할 수 없습니다.” |
| 원 Session/group가 달라진 전환 | 409 TRANSITION_CONTEXT_CONFLICT, retryable=false | 409 EXAM_TRANSITION_CONFLICT, “시험 상태가 변경되었습니다. 다시 확인해 주세요.” |
| confirm 성공·LC 마무리 미완료 또는 다른 key의 진행 guard 충돌 | 같은 operation 영속 복구, guard 유지 | 기존 409 EXAM_CREATION_PROCESSING, Retry-After: 1 |
| 응답 유실/일시 DB·통신 장애 | status-first, 결과 불명 보존 | 기존 503 BILLING_TEMPORARILY_UNAVAILABLE, Retry-After: 1; 같은 key 재전송 |
| status OPERATION_NOT_FOUND/빈 ingress404/인증 실패 | 성공·거절을 추정하지 않음; 업무 불명/CONTRACT_ERROR/auth circuit을 내부에서 구분 | 기존 공개 장애 매핑 유지; 격리·경보와 자동 retry 정책도 유지, 신규 key 금지 |
| malformed/같은 key 다른 payload | 기존 400/409 계약 유지 | 기존 mapper 유지 |

EXAM_CREATION_PENDING/Retry-After:2 제안은 철회한다. 신규 공개 code 후보는 ENTITLEMENT_REVOKED와 EXAM_TRANSITION_CONFLICT 두409이며 앱 reader 호환을 확인 후 동결한다. 기존 일반 saga/guard/일시 장애를 새 pending으로 일괄 변경하지 않는다. retryable=false context conflict는 해당 실패를 동일 key에 재현한다. 사용자가 새로 시작하려면 기존 operation이 terminal로 정리됐음을 확인하고 새 key를 사용한다. pending을 받은 앱은 자동으로 새 key를 만들지 않는다.

LC는 확정한 operation/reservation/session/source 귀속의 CONFIRMED를 먼저 확인한다. CANCELED/EXPIRED 또는 confirmRejection이 영속 확인된 경우에만 대기 Session을 안전 보상한다. RESERVED 관측만으로 보상하지 않는다. 거절 응답이 유실되면 status의 CANCELED+confirmRejection으로 같은 공개 오류를 재현한다.

기존 operation은 LC 마무리까지 미완료다. LC 로컬 Transaction으로 새 Session 사용 가능·exact 이전 Session 신규 제출 차단·SUCCEEDED를 함께 commit한 뒤 공개 성공을 반환한다. 채점 완료를 기다리지 않는다. 미완료 operation에는 TTL을 적용하지 않고 guard/worker retry/경보를 유지한다. 기존 Job에서 나중에 파생된 Summary 최초 생성과 시스템 재시도는 원 durable 접수 증거에 연결하면 허용한다. 신규 사용자 제출 유래 Job은 종료 이후 차단한다.

Billing의 7일 command TTL은 source/전환/confirm 결과 증거의 수명이 아니다. 영속 Reservation에 caller/user/operation/reservation/session/source/transition, canonical request·confirm digest와 최초 성공/거절 결과를 복구 가능하게 저장하는 안이다. owner 변경 이후 과거 userId만으로 신규 권한을 주지 않는다. 같은 성공 confirm을 TTL 뒤 replay하는 경우에도 최초 성공 증거를 재현한다. lookup/index·개인정보 제거와 활성 작업 증거 보존은 schema 검토에서 함께 정한다.

## 5. Billing→LC revoke 인증·wire 후보

route는 `POST /internal/v1/entitlements/access/events`, raw body 최대16,384 bytes, redirect 금지다. A 계약의 token parser/Bearer/query/시간 검증을 재사용하되 trust/profile/fixture는 역방향 별도다.

| 항목 | 제안값 |
| --- | --- |
| issuer | urn:tosunsaeng:workload:billing:{env} |
| sub | billing-service |
| aud | tosunsaeng-learning-core:entitlement-access-events:{env} |
| env | test / staging / production exact |
| 서명 | RS256, RSA2048 이상, revoke 전용 private key·배포 public JWK set |
| 시간 | exp=iat+60, optional nbf=iat; 수신 TTL1~60/skew30, A의 정수 NumericDate·소수초 now 규칙 |
| 키 | kid exact, 고정 verifier, 원격 fetch/fallback/다른서비스키 union 없음 |
| 발신 | billing.payment.access-publisher-enabled 기본false; billing.payment.access-workload-jwt.environment/private-jwk |
| 수신 | app.billing.access-events.receiver-enabled 기본false; app.billing.access-events.environment/billing-public-jwk-set |

ON일 때 키/환경 필수 검증, OFF 미파싱. 매 HTTP 시도 새 token/jti, event ID/body 불변. Identity recovery·사용자 JWT·LC→Billing 키와 독립. connector는 LC의 기존 workload TLS를 공유 가능하되 정확한 local port/route security chain은 독립 검증한다. 실 origin/port/설정명 충돌·키 담당·회수 SLA는 양측 배포 inventory에서 확정한다.

HTTP 우선순위: 살아 있는 서버의 수신 OFF/잘못된 포트→body 없는404/no-store; 활성 수신의 query(빈 ? 포함)/Bearer/JWT 실패→body 없는401/no-store; 검증된 wrong sub/aud/method/path→body 없는403/no-store; 그 뒤 body 크기/strict JSON/업무 검증. 포트 미기동은 transport 실패다. 다른 서비스 토큰은 실제 설정된 verifier로 검증할 수 없으면401이며 issuer 문자열만으로403을 반환하지 않는다. 인증 실패는 DB/업무 무호출이다.

| body field | 형식 |
| --- | --- |
| eventId | lowercase UUID v4 |
| eventType | AttemptGroupAccessRevoked |
| schemaVersion | 정수1 |
| producer | billing |
| consumerScopeId | 환경별 양측 배포 설정의 exact 값 |
| occurredAt | 최초 권리 종료 적용 시각, UTC RFC3339 millisecond 정밀도·Z, 재발행 때 불변 |
| userId | 당시 owner의 lowercase canonical UUID |
| attemptGroupId | lowercase UUID v4 |
| accessVersion | 환경/producer/scope/group별 positive int64, JSON 정수 |
| reason | PURCHASE_REFUNDED 또는 PURCHASE_REVOKED |
| confirmedSessionId | 필수 nullable opaque Session ID. 최초 revoke 시 보호할 exact Session, 없으면 null |

event body는 위 필드만 허용한다. semantic/canonical digest는 검증 후 위 표 순서의 compact UTF-8 JSON, 공백 없음, §9.6의 string escaping, null 포함, 숫자 정수10진수로 고정하는 후보다. semantic digest만 eventId를 제외한다. raw JSON의 순서/공백 차이는 digest 차이를 만들지 않는다. UTF-8/escaping/숫자 경계의 정확 bytes는 공동 fixture로 동결한다.

검사 순서는 eventId+digest 충돌→동일 group/version 의미 충돌→high-water/stale다. 같은ID다른내용409 EVENT_ID_CONFLICT, 동일version다른의미409 ACCESS_VERSION_CONFLICT. 그 뒤 최초 고정된 confirmedSessionId와의 불변 검사를 stale 판단 전에 수행한다(§9.6). 신규ID 동일version 동일의미는 최소 dedupe 저장 후204. 최초/duplicate/stale는 durable 반영 확인 후 body없는204다. malformed400 INVALID_REQUEST, unknown schema/producer/scope422 UNSUPPORTED_CONTRACT, Mongo일시장애503 LC_TEMPORARILY_UNAVAILABLE 후보. 크기초과413 INVALID_REQUEST 후보. 인증된 wrong route와 body 오류의 우선순위도 fixture에 넣는다.

confirmedSessionId는 최초 적용 값(null 포함)으로 고정하고 higher version도 변경하지 못한다. 별도 lifecycle 종료만 보호 예외를 종료한다. 어떤 event도 REVOKED를 ALLOWED로 되돌리거나 Session/계정을 재생성하지 않는다. 다른 구매·무료권을 user 단위로 일괄 차단하지 않는다.

## 6. 보존·삭제와 legacy 경계

- eventId/digest 충돌 감사는 수신/관측 기준120일 제안. duplicate가 retention을 무한 연장하지 않도록 첫 관측 기준을 사용한다. 이 기간은 이용 권한 만료가 아니다.
- 열린 보호 Session의 deny/high-water/confirm 연결은 실제 lifecycle 종료까지 유지한다. 무기한 제출 허용을120일 TTL로 종료하지 않는다.
- 종료·삭제 이후에도 늦은 event는 권한을 복원하거나 학습자료를 upsert하지 않는다. 최소 replay fence와 개인정보 연결은 분리하고, 삭제/탈퇴 우선 규칙을 적용한다.
- tombstone의 정확 폐기 기간·비식별 키 생성/보존은 LC 삭제 정책과 함께 동결해야 한다. 영구 보존이나 임의120일 삭제를 이번 초안으로 확정하지 않는다. 폐기 전 활성 작업 없음·전달/replay 안전성을 확인할 gate와 검증 담당을 LC 계획에 명시한다.
- 금융 원장/구매별 최소 증거는 Billing 기존5년/백업35일 정책을 유지한다. LC 학습 원문을 Billing으로 복제하지 않는다.
- legacy는 전환 전 서버 시험 ID manifest 방식 우선. 보고서는 hash/revision/건수만 기록한다. 기존 Session source를 EXAM_PASS로 backfill하지 않으며 신규 요청은 manifest에 편입하지 않는다. 원 Session을 일괄 수정하지 않는다.

## 7. 공동 fixtures 제작 명세

아래는 제작할 케이스 목록이다. 실제 fixture 파일/hash/테스트 통과는 아직 없다. 승인 후 가짜 식별자·고정 now·테스트 키만 사용해 token/request/response bytes와 기대 결과를 만든다. token/HTTP 및 업무 fixture는 기존 A fixtures-1을 덮어쓰지 않고 별도 revision/SHA256SUMS로 관리한다. 양측 독립 검증 결과를 별도로 기록한다.

| ID | 입력/경합 | 기대 |
| --- | --- | --- |
| W01 | 일반 INITIAL | 요청mock/새group/source, transition 없음 |
| W02 | 유효 replacement | 원group/mock/consumption 유지 |
| W03 | 최초trial→paid | 새group/initialMock/TRIAL_TO_PAID, confirm시복원1회 |
| W04 | 복원권→paid | OTHER_SOURCE_CHANGE 최초활성화 제외, 권리/복원/Session 변경 없음 |
| W05 | stale 원Session/partial restart/phone동시 | conflict 또는400, 다른Session종료없음 |
| W06 | 동일key 다른문제/대상 | 멱등conflict |
| W07 | reserve/status source/transition 불일치·누락 | 성공후계약오류, PREPARED미정구분 |
| W08 | confirm성공응답유실→환불→status | 성공증거복구·Session보존 |
| W09 | 환불선행confirm/거절응답유실 | 영속CANCELED+거절증거,409재현 |
| W10 | command7일TTL후 성공/거절 replay | 영속증거재현, 재지급없음 |
| W11 | LC마무리실패/unknowncommit/동시요청 | 미완료guard/retry·마무리후성공 |
| W12 | 사용자접수와종료 양순서 | 접수전종료거절/기접수완료허용 |
| W13 | 종료후 Summary최초생성/시스템재시도 | 원접수증거있으면허용, 신규문항없음 |
| W14 | 부분접수/늦은COMPLETED | 완료evidence검증,원group차단·복원유지 |
| W15 | legacy목록안/밖/조회실패 | 기존기록보존·신규source필수·임의허용없음 |
| W16 | terminal/삭제/탈퇴 뒤마무리 | 비부활·다른Session종료없음 |
| R01 | JWT parser/time/key/rotation,OFF·port·query | A와동일규칙의역방향검증·401/403/404/transport구분 |
| R02 | 16384/16385bytes·duplicate/trailing/unknown | 상한/strict 검증,오류시업무무호출 |
| R03 | 같은ID다른body+낮은version | stale전에409 |
| R04 | 같은version동일/다른의미 | duplicate204/conflict409 |
| R05 | 예외Session변경/null전환·장기지연 | 예외불변·deny유지 |
| R06 | 삭제/merge/탈퇴·inboxTTL경계 | 비부활/자동권리이전없음·보호증거유지 |

## 8. 구현 분담·수락 요청

Billing: 권리 선택·restart 검증·응답/status 불변snapshot, confirm/복원/원group차단 원자성, 거절/TTL후 복구 증거, revoke outbox/signer·금융보존, fixture 생산자 측 검증.

LC: 두 문제 후보/원대상 고정, strict reader·operation/Session 영속, 허용된 INITIAL 전환검증, 로컬마무리까지operation복구·공개오류, 접수/파생Job 처리, legacy manifest/삭제연계·revoke receiver 및 독립fixture검증.

LC에 우선 수락 요청할 항목: (1) restartContext와 transition/source/status 확장, (2) 두 문제 후보와 source별 전환 규칙, (3) CANCELED+confirmRejection, 마무리/guard의 기존409·일시 장애의 기존503·신규 두409, (4) 역방향profile/HTTP/이벤트digest, (5) fixture목록. 보존의 terminal tombstone 상세·배포설정 실제값은 담당 근거를 확인한 뒤 채운다. 삭제continuation 매핑은 §9.2 제안의 공동 수락이 필요하다. 이 문서를 전 항목 확정 완료나 구현 착수 승인으로 보고하지 않는다.


## 9. draft-2 보완 규칙 — LC 2026-10-14 검토 반영

이 절은 draft-1의 모호한 경계를 구체화하는 Billing 제안이다. 공동 최종 수락/구현 승인을 뜻하지 않는다. 상충 시 이 절과 수정한 본문을 함께 검토한다.

### 9.1 replay 우선순위

인증·caller/현재 허용 owner fence→canonical 요청 digest→기존 operation 결과 조회/재현→신규 operation의 current Session/group stale 검증 순서다. 기존 key 다른 payload는 멱등 conflict이며 기존 성공/실패를 덮어쓰지 않는다. 동일 key/digest의 저장된 성공 또는 영속 실패는 current Session이 전환으로 바뀌어도 재현한다. owner 권한 상실은 replay로 우회하지 않는다. command TTL 이후 영속 receipt에서도 같은 순서다.

reservation 생성 전 TRANSITION_CONTEXT_CONFLICT도 caller/user/operation/type/digest·실패 code를 영속 실패 receipt에 남긴 후 반환한다. request 검증 실패 전체를 저장한다는 뜻은 아니다. 이 receipt는 command TTL과 분리하고 전환 snapshot 보존 계약을 따른다. 아직 보존기간/안전 purge가 동결되지 않았으므로 이 영속 receipt에 자동 TTL을 붙이는 구현은 허용하지 않는다. receipt 보존·삭제 정책의 공동 동결은 활성화 gate이며 영구 개인정보 보존을 승인하지 않는다. receipt가 없는 오래된 요청의 결과를 보장한다고 주장하지 않는다.

동시 처리 중에는 신규 stale 실패를 덮어쓰지 말고 동일 key unique/Transaction으로 하나의 결과에 수렴한다. 성공 여부 불명인 operation을 current 상태만으로 conflict terminal 처리하지 않는다. 7일 command 소실 후 실패를 가변 상태에서 재계산하는 방식은 금지한다.

### 9.2 삭제 continuation·누락 context·phone

LC 실제 경로는 삭제 보존 sourceExamId/group/mock→PreparedAssignment/operation→일반3필드reserve→commitReservedSession의 transferForCommittedSession이다. phone discovery 경로와 다르다.

신규 writer 제안: 삭제 continuation도 restartContext.previousSessionId=보존된 sourceExamId, previousAttemptGroupId=보존group, top-level mock=보존mock로 보낸다. initialMockExamId는 LC가 별도 선정해 고정한다. Billing은 LC 문서 존재 여부 대신 자신의 retained AttemptSession/group/소비/owner fence로 원 source를 검증하며 최신 Session으로 바꿔 끼우지 않는다. 삭제 보존 사실만으로 Billing 원 권리를 복원하거나 새 trial을 만들지 않는다.

삭제 continuation의 claim은 operation에 결속하고 LC 마무리 성공까지 다른 operation이 사용하지 못하게 한다. 최종 transfer는 confirm 성공 후 LC 마무리 Transaction에서 확정하는 제안이다. 현행 confirm 전 transfer를 그대로 재사용하지 않는다. pre-confirm 실패/authoritative cancel·expiry는 같은 operation의 claim을 안전 해제한다. unknown confirm/마무리 실패는 claim을 유지·복구하며 다른 새 key가 claim을 가져가지 못한다. 현재 releaseCanceledClaim/TTL/삭제 lifecycle와 함께 회귀 검증해야 한다. 마무리 때 원 Session은 없어도 되고, 이미 삭제한 문서를 재생성하지 않는다.

새 operation에서 Billing-linked 재응시/전환 대상 group이 있는데 restartContext와 유효 phone continuation이 모두 없으면 409 TRANSITION_CONTEXT_CONFLICT로 거절한다. 이를 신규 INITIAL로 자동 소비하지 않는다. 구3필드 replacement는 최초 활성화에서 허용하지 않으며 구 writer drain이 필수다. 기존 저장 성공의 동일 operation replay에는 이 신규 검사를 적용하지 않는다. 완료 이력만 있는 경우를 활성 재응시 대상 group 존재로 오인하지 않는다.

유효 phone continuation과 ACTIVE paid가 충돌하면 이번 초안에서는 409 TRANSITION_CONTEXT_CONFLICT·권리/Session 변경 없음으로 제안한다. phone을 무시해 paid INITIAL로 바꾸거나 paid를 무시해 무료를 소비하지 않는다. 명시 재발견/새 선택 절차는 LC와 동결 전이며 이 조합의 end-to-end 정책이 정해지기 전 해당 조합 활성화를 허용하지 않는다. phone과 restartContext 동시 요청400은 유지한다.

### 9.3 source/type/mock 조합과 활성화 범위

| kind | source | transition | group/Session/mock |
| --- | --- | --- | --- |
| 일반 INITIAL | EXAM_PASS 또는 SUBSCRIPTION | 없음 | 새group/새Session, top-level mock |
| REPLACEMENT | 원 권리의 불변 source | 없음 | 원group, 원Session과 다른 새Session, 원mock |
| INITIAL 전환 | SUBSCRIPTION | TRIAL_TO_PAID | 원trial증거, 원group와 다른 새group·원Session과 다른 새Session, initialMock |
| INITIAL 전환 후보 | 정책에 따른 source | OTHER_SOURCE_CHANGE | 최초활성화 제외 |

TRIAL_TO_PAID+EXAM_PASS/REPLACEMENT/같은group/같은Session/잘못된mock, 전환인데transition없음, 일반INITIAL/REPLACEMENT에transition존재는 계약 오류다. 동일 source enum은 동일 권리 증거가 아니다. OTHER_SOURCE_CHANGE에 기접수 완료 허용/무시 중 어느 하나도 임의 적용하지 않는다. Billing 신규 요청은 409 TRANSITION_CONTEXT_CONFLICT로 거절하고 LC가 예상 밖 성공 snapshot을 받으면 계약 오류로 격리한다. 적용 가능한 제품 정책을 최종 삭제하는 결정이 아니라 기술 미확정 경로의 activation 제외다.

### 9.4 거절·cancel·terminal 재현

confirmRejection은 CANCELED에서만 허용한다. RESERVED/CONFIRMED/EXPIRED 조합은 계약 오류다. rejectedAt=terminalAt으로 같은 Transaction의 최초 고정 시각을 사용한다. 일반 CANCELED는 confirmRejection이 없으며 기존 취소 mapper를 유지한다. revoke 거절만 신규 공개 ENTITLEMENT_REVOKED를 재현한다.

cancel 응답은 거절 증거가 없으므로 CANCELED 응답만으로 일반취소/환불거절을 결정하지 않는다. 원 operation status를 읽어 confirmRejection 및 exact 귀속을 확인하고 공개 terminal 결과를 저장한다. status 불명은 증거를 만들어내지 않고 복구한다. 이미 영속 확인한 결과는 이후 생략 응답으로 지우지 않는다. 성공confirm은 후속환불/cancel재시도로 거절로 바뀌지 않는다.

### 9.5 공개 오류와 내부 복구

진행 guard/마무리 미완료는 기존409 EXAM_CREATION_PROCESSING·Retry-After:1, 일시통신/저장소 장애는 기존503 BILLING_TEMPORARILY_UNAVAILABLE·Retry-After:1을 유지하는 안이다. 기존 다른key guard 충돌도 그대로다. 앱은 같은key로 조회/재전송하고 pending을 새key생성 지시로 해석하지 않는다.

업무 OPERATION_NOT_FOUND, ingress404 CONTRACT_ERROR,401/403 auth circuit,관측된 일시503은 내부 category를 유지한다. 격리/circuit/경보가 필요한 경우 무조건 자동 retry로 통합하지 않는다. 사용자에게 일시적으로 복구중임을 표시하는 것과 내부 retry 가능성은 별개다. 신규 두409의 BaseResponse code/message·terminal replay는 앱 reader/LC ErrorStatus와 공동 검증한다.

### 9.6 revoke enum·canonical bytes·불변 예외

reason은 이 초안에서 PURCHASE_REFUNDED/PURCHASE_REVOKED로 통일하는 제안이다. 앞선 미구현 초안의 PAYMENT_* 표현은 대체하며 alias로 받지 않는다. 기존 운영 wire 변경이 완료됐다는 의미가 아니다.

canonical string 규칙 후보: Unicode normalization을 하지 않고 유효 Unicode scalar를 그대로 UTF-8로 인코딩한다. 한글 등 비ASCII와 slash는 escape하지 않는다. quote/backslash는 각각 JSON escape, U+0000~001F는 항상 lowercase hex `\u00xx`로 escape한다(짧은 \n/\t 표기 사용 안 함). lone surrogate/손상 UTF-8는 입력 거절하며 유효 surrogate pair escape는 대응 scalar로 디코딩한다. UTF-8 BOM없음, 마지막 LF없음, 공백없음. int64는1~9223372036854775807 범위의 정수10진수, 파서에서 float로 변환하지 않는다. occurredAt은 정확 YYYY-MM-DDTHH:mm:ss.SSSZ 형태 및 실제 날짜 검증, offset/가변소수초 거절이다. null은 literal null이다.

canonical 필드 순서는 §5 표와 같고 semantic은 eventId만 제거해 나머지 순서를 유지한다. digest는 canonical UTF-8 bytes의 SHA-256, lowercase64hex다. §5의 schema 숫자는정수1, 다른유효정수 schema는422다. 먼저 크기/JSON문법/중복/unknownfield/필수/타입/형식을 검사해400(크기413), 그 뒤 유효형식의 미지원 schema/producer/scope/reason을422로 처리한다. unknownfield와 미지원schema가 함께면400이다. 유효 JWT지만 틀린route권한은 body검사보다403이 먼저다.

eventId conflict→동일version retained증거 conflict→최초 고정 confirmedSessionId 불변검사→stale 순서다. 처음 higher version이 와서 예외를 고정했다면 뒤늦은 lower version의 다른 confirmedSessionId도409 ACCESS_VERSION_CONFLICT, 같은 예외면stale204다. 과거version증거가 없을 때도 group의 고정 예외와 비교한다. 원본이 terminal/삭제됐다는 이유로 null로 바꾸지 않는다. 보존증거 폐기 뒤 비교 불가능한 항목까지 영원히 검증한다고 주장하지 않는다.

wrong path403은 지정workload connector의 `/internal/v1/entitlements/access` 및 그 하위 namespace에 한정하는 후보이며 정확 route 외 경로는 인증 후403으로 차단한다. 인코딩/정규화 경로도 우회 불가하게 fixture로 확인한다. 기존 사용자·Identity route를 이 chain이 가로채지 않는다. namespace 소유와 실제connector/chain inventory 확인 전 이 matching 규칙은 활성화하지 않는다. 수신OFF/잘못된port의404guard 역시 이 namespace에만 적용한다.

### 9.7 추가 fixture와 gate

| ID | 검증 |
| --- | --- |
| W17 | 성공전환후 currentSession변경·commandTTL후 동일key replay, owner권한상실/다른digest 거절 |
| W18 | reserve전 stale실패 영속화·동시요청·TTL후같은실패재현, 성공불명terminal변경금지 |
| W19 | restart누락/삭제sourceExamId/원문서없음/transfer성공·실패·unknown/phone+paid충돌 |
| W20 | kind/source/type/group/session/mock 모든 부정조합,OTHER_SOURCE_CHANGE활성화제외 |
| W21 | confirmRejection상태조합/rejectedAt=terminalAt/일반cancel구분/cancel후status복구 |
| W22 | 기존409/503/Retry-After1·새409reader·ingress404/auth circuit/격리 구분 |
| R07 | 한글/slash/quote/control/유효pair·lone surrogate/손상UTF8/normalization차이 golden bytes |
| R08 | int64경계·날짜/밀리초형식·unknownfield+unknownschema·digest hex/semantic필드순서 |
| R09 | higher먼저/lower다른예외409·같은예외stale·과거version증거없는불변비교 |
| R10 | protected namespace/다른Identity·user route 미가로채기·port/OFF우선순위 |

W01~W22/R01~R10은 제작 명세이며 아직 golden bytes/token/hash/독립실행 결과가 없다. fixture 제작은 실서비스 활성화 없이 가능하다. 최종동결에는 golden bytes 비교·양측독립검증·예외조합 수락이 필요하다.

유료/revoke 활성화 gate: 실패receipt/terminaltombstone/legacy manifest 보존·삭제연계 확정, phone+paid/삭제continuation 지원범위, OTHER_SOURCE_CHANGE 제외의 제품 영향 확인, 실origin/port/키/회수SLA·chaininventory, reader/writerdrain·legacyinventory, 복구/e2e 및운영경보. gate 미완료를 fixture통과로 대체하지 않는다.


## 10. draft-3 문구 정합성 — 2026-10-14

사용자 전달 LC draft-2 재검토에서 §9의 주요 기술 보완 방향에 추가 이견이 없음을 확인했다. 이전 요청을 모두 미해결로 되돌리지 않는다. 이번 revision은 §1의 command header 범위를 reserve/confirm/cancel로 한정하고 status의 기존 body 조회·무command/무ledger 성격을 명시했다. §8의 오류 수락 표현도 마무리/guard 기존409·일시 장애 기존503·신규 두409로 정리했다. wire 동작을 새로 변경하는 제안은 추가하지 않았다.

공동 최종 동결·fixture 제작 실행·golden bytes/token/hash·양측 독립 검증 완료를 뜻하지 않는다. OTHER_SOURCE_CHANGE/phone+paid 제외의 제품 영향, 삭제 continuation 제안 수락, 보존·삭제 manifest 및 실제 connector/키·E2E gate는 기존 미확정 상태를 유지한다. 코드/DB/배포/테스트 실행 변경 없음.
