# LC–Billing J3/J4 보완안 R1 — 실패 조회·owner/lifecycle

> 2026-10-14 최신 사용자 승인: [J4·S15·V07 통합 결정](PLAN-011-J4-S15-V07-DECISIONS-2026-10-14.md). 명시적 사용자 시험 삭제는 원 재응시 포기·사용 무료권 미복원, 다른 권리/기지급 복원권 보존. V07은 진행 중 증거 유지·종료 후 최소 보존 방향 확정. 아래 삭제 continuation 복구 제안은 이 범위에서 대체하며, phone·삭제 전달/경합·보존기간 exact는 후속이다.

> 2026-10-14 사용자 승인: [J4 ① 채택](PLAN-011-J4-MEMBER-EVIDENCE-DECISION-2026-10-14.md). Identity 확인 MEMBER 증거 보존 + 기존 탈퇴 이벤트/복구, 전파 지연 허용. 아래 방식 미선택 표현은 이 범위에서 대체한다. 최초 증거 전달 exact·구현/공동 검증은 후속이며 실시간 ACTIVE 증명으로 해석하지 않는다.

- 날짜: 2026-10-14 / revision: lc-billing-paid-exam-j3-j4-r1-draft-2
- 상태: LC 검토 회신을 반영한 제안. 신규 오류 의미·owner 상세의 공동 수락/fixture 제작/구현은 미완료.
- 관련: PLAN-011/012/013, TMI-116/TMI-128/TMI-122, TMI-125.
- 기준: [구현 합의안 R1](LC-BILLING-PAID-EXAM-R2-IMPLEMENTATION-AGREEMENT-R1.md), [저장·T2 설계 R1](../plans/PLAN-011-STORAGE-TRANSACTION-DESIGN-R1.md).

> draft-2: 사용자 전달 LC R1 검토 회신을 반영했다. PREPARED·신규 Session 부재·lease/version/owner 재검증·관측시각 분리 및 strict 오류/owner-blocked 분류를 보완했다. ACTIVE MEMBER 연동은 미확정으로 유지한다.

## 1. 5줄 결론

1. J1/J2는 LC의 요구 방향 확인으로 접수하며 모든 경로의 구현/테스트 완료로 기록하지 않는다.
2. J3는 기존 status route와 4-field 오류 형식을 유지하고 예약 생성 전 영속 거절만 나타내는 전용 code를 제안한다.
3. LC worker는 해당 증거와 자기 operation의 불변 요청/owner/단계를 검증한 뒤 로컬 실패와 guard 해제를 함께 저장한다. reserve 재발행은 하지 않는다.
4. J4는 source별 owner snapshot과 신규/confirm/replay/lifecycle 비교를 나눈다. 일반 version 증가는 소유권 변경으로 취급하지 않는다.
5. S15/V07은 보류하며 이 문서의 신규 기대값은 별도 fixture revision에서 검증한다. R2/fixtures-2 원본은 변경하지 않는다.

## 2. 사용자가 반드시 읽어야 하는 내용

현재 InternalApiError는 code/message/retryable/correlationId다. status는 성공 RESERVE command와 Reservation이 없으면 OPERATION_NOT_FOUND를 반환한다. 아직 영속 실패 receipt를 읽는 구현은 없다. 아래는 이 차이를 해결하는 새 제안이다.

J3의 첫 범위는 **예약 생성 전 TRANSITION_CONTEXT_CONFLICT를 영속 확정한 경우**로 제한한다. 권리 부족·malformed·인증 실패·일시 장애 전체를 동일 영구 실패로 바꾸지 않는다. confirm 이후의 ENTITLEMENT_REVOKED는 기존 R2 CANCELED+confirmRejection으로 처리한다.

## 3. J3 exact 관측 제안

### 3.1 reserve 및 status

| 상황 | HTTP / code / retryable | 서버 근거 |
| --- | --- | --- |
| 최초 pre-reserve context 거절 및 동일 요청 재전송 | 409 / TRANSITION_CONTEXT_CONFLICT / false | RESERVE FAILED_TERMINAL receipt가 commit됨. reservationCreated=false |
| 같은 key·다른 canonical payload | 409 / IDEMPOTENCY_KEY_CONFLICT / false | 최초 digest와 불일치. 최초 결과 변경 없음 |
| status에서 위 영속 거절 발견 | 409 / RESERVE_REJECTED_BEFORE_CREATION / false | 아래 유일 key·귀속·최초 실패를 검증한 receipt만 반환 |
| 아직 결과 불명/저장소 장애 | 503 / BILLING_TEMPORARILY_UNAVAILABLE / true, Retry-After:1 | terminal 증명 아님 |
| receipt/Reservation 없음 | 기존404 / OPERATION_NOT_FOUND / false | 부재일 뿐 안전 취소·미소비 증명 아님 |
| 현재 owner 검증 거절 | 409 / OPERATION_OWNER_MISMATCH / false (신규 제안) | terminal receipt 증명이 아닌 접근/귀속 오류. 이 응답만으로 보상/guard 해제 금지 |

status는 기존 POST /internal/v1/reservations/status body userId/operationId만 사용한다. command header·새 route·추가 성공 DTO는 없다. 신규 code는 LC strict error reader/category에 먼저 반영해야 한다. OPERATION_OWNER_MISMATCH는 서비스 JWT principal 401/403과 구분되는 업무 오류이며 public 사용자 오류를 이 문서에서 새로 확정하지 않는다.

오류 body의 네 field는 모두 필수다. 예시는 다음과 같다.

```json
{"code":"RESERVE_REJECTED_BEFORE_CREATION","message":"The reserve operation was rejected before reservation creation.","retryable":false,"correlationId":"00000000-0000-4000-8000-000000000001"}
```

correlationId는 응답별 진단 UUID이며 재전송 때 달라질 수 있다. 귀속/멱등성/실패 증거로 비교하지 않는다. 최초 실패 재현은 HTTP/code/retryable/고정 message와 업무 결과 동일성을 뜻하며 correlationId bytes 동일을 뜻하지 않는다. reserve 고정 message는 `The transition context conflicts with the current exam.`으로 제안한다. owner 거절 고정 message는 `The operation owner does not match the current owner.`다. 세409에 Retry-After는 없다.

### 3.2 증거와 우선순위

서비스 인증/route/body strict 검증 → 현재 caller/owner 접근 허용 검사 → 해당 key receipt 조회 → reserve라면 digest 일치 검사 → 기존 결과 재현 → 신규 operation에만 가변 context 검사를 적용한다. 권한 없는 요청에 기존 digest/결과를 공개하지 않는다. 인증된 허용 owner의 같은 key 다른 payload는 context stale 검사보다 먼저 IDEMPOTENCY_KEY_CONFLICT다.

receipt key는 environment/검증된 callerService/userId/operationId/RESERVE이며 실패 당시 request digest·previous group/session·제안 session/mock·startReason·owner snapshot·최초 terminalAt·원 code를 저장한다. 예약과 terminal receipt의 상호 배타성은 동일 unique operation 경계와 Transaction으로 보장한다. 동일 key에 성공/예약 근거가 있으면 새 terminal 실패를 만들지 않는다.

status 전용 code는 이 key에 대한 `FAILED_TERMINAL + TRANSITION_CONTEXT_CONFLICT + reservationCreated=false`가 commit됐을 때만 낸다. 현재 context를 다시 검사해 만들어내지 않는다. receipt/Reservation 간 모순은 503과 격리/경보로 처리하고 terminal code를 반환하지 않는다.

wire에 digest나 owner snapshot을 추가하지 않는 최소안이다. 귀속은 인증된 Billing 연결의 해당 status 요청과 서버의 exact key 조회 보장에 의존한다. LC는 불변 operation의 userId/operationId로 보낸 요청과 응답을 직접 결속하고 correlationId나 issuer 문자열만으로 신뢰하지 않는다. 이 방식으로 부족하면 proof object 추가를 별도 공동 결정하며 조용히 DTO에 추가하지 않는다.

### 3.3 LC worker 종료와 예외

1. worker는 exact 로컬 operation이 **PREPARED**이고 Session commit marker가 없으며, reservationId/confirm 성공 근거가 없음을 확인한다. 로컬 불변 caller/user/key/요청과 현재 owner/fence, 자신이 보유한 lease/version을 함께 캡처한다.
2. 해당 operation의 **신규 Session ID**로 Session 부재를 확인한다. 비활성/pending이라도 존재하면 모순으로 격리한다. 이전 시험의 Session 존재는 이 조건에 해당하지 않으며 원 시험을 삭제/종료하지 않는다.
3. 고정 userId/operationId로 기존 status만 호출한다. 새 reserve·신규 key를 발행하지 않는다.
4. 전용 category는 **status 호출의 HTTP409 + exact RESERVE_REJECTED_BEFORE_CREATION + boolean false**이고 code/message/retryable/correlationId 네 필드의 필수/strict 타입/unknown/duplicate/trailing 검증이 모두 통과할 때만 선택한다. 문자열 false·null·누락·추가 field는 허용하지 않는다. message/correlationId는 문자열이며 귀속 증거가 아니다. correlationId 변화는 충돌이 아니다.
5. status 이후 같은 로컬 Transaction에서 operation PREPARED/version·lease 소유/유효성·현재 owner/fence·신규 Session 부재/commit marker 부재를 다시 검사한다. 생성 HTTP와 worker는 동일 operation version을 조건부 쓰고, Session 생성도 이 fence에 참여해야 한다. 사전 조회만으로 새 Session 삽입 경합을 막았다고 보지 않는다.
6. 조건이 유지된 경우만 자기 operation의 terminal 실패(EXAM_TRANSITION_CONFLICT 매핑)와 자기 pending guard 해제를 같은 Transaction/CAS로 저장한다. RESERVED/SESSION_COMMITTED/SUCCEEDED에서 역방향 실패 전이를 하지 않는다. concurrent HTTP가 진전했거나 CAS가 실패하면 최신 상태를 읽고 다시 분류하며 이전 status 결과를 그대로 적용하지 않는다.
7. 오류 DTO에는 remote terminalAt이 없다. LC는 `observedAt`/로컬 실패 기록 시각을 자기 clock 기준으로 기록하며 Billing 최초 실패 시각이라고 이름 붙이거나 추정하지 않는다. Billing receipt의 terminalAt은 Billing에 남는다.
8. local commit 불명은 같은 operation을 재조회해 수렴한다. 다른 operation guard나 이전 current group은 건드리지 않는다.
9. 전용 code가 reserve에서 오거나 HTTP/code/retryable 조합이 틀리면 CONTRACT_ERROR/격리다. code 문자열만 추출해 terminal로 처리하지 않는다. OPERATION_NOT_FOUND·빈 ingress404·401/403·503도 위 terminal 증거가 아니다.
10. OPERATION_OWNER_MISMATCH는 별도 업무 `owner-blocked` 분류다. 서비스 JWT 인증 실패용 전역 auth circuit을 열지 않으며, 이 응답만으로 Session 보상/guard 해제하지 않는다. 명시 owner/lifecycle 복구·검토 경로로 보내고 default 무한 retry나 새 public 오류에 임의 매핑하지 않는다.

탈퇴/삭제로 현재 actor가 차단된 경우 lifecycle가 작업을 종료/격리할 수 있으나 J3 오류만으로 권한을 되살리지 않는다. lifecycle 정리와 금융 결과 보존의 후속은 S15/V07에서 확정한다.

### 3.4 TTL·삭제

command7일TTL 뒤에도 receipt가 유지되는 동안 동일 key reserve 실패 및 status 전용409를 재현한다. TTL은 미완료 LC operation 복구보다 앞서 증거를 제거하지 않는다. receipt의 정확 보존기간·owner 연결 제거·폐기 후 status 및 지연 재전송 대응은 V07 미확정이다. 따라서 이번 제안을 무기한 조회 보장으로 쓰지 않으며 삭제 기능 활성화 전 해당 규격이 필요하다.

## 4. J4 source별 owner snapshot·T2 제안

### 4.1 공통 snapshot

내부 ownerSnapshot은 environment, callerService(검증된 LC principal의 고정 매핑), requestUserId, ownerKind, ownerRefId, ownerUserIdAtReserve, ownerRevisionAtReserve, capturedAt를 가진다. 이는 제안 저장 필드이며 wire에 추가하지 않는다. 동일 userId라도 ownerKind/ref를 섞어 권리를 이전하지 않는다.

| source/내부 출처 | ownerKind/ref 및 추가 snapshot | 현재 근거/제안 경계 |
| --- | --- | --- |
| EXAM_PASS 최초 trial | TRIAL_SUBJECT / subjectRefId; consumerScopeId, trialClaimId, sessionOwnerEpoch, sessionBindingVersion, ownerVersion | billing_subject_links의 active/current user/보존·epoch를 기존 규칙대로 검사. epoch와 일반 binding version 구분 |
| SUBSCRIPTION | PAYMENT_ACCOUNT / payment_accounts._id; paidSubjectRefId, lifecycleId, accountVersionAtReserve, lifecycleVersionAtReserve, purchaseId, entitlementId | account/lifecycle ACTIVE·환경/user 동일, source 구매 귀속·revoke 검사. activeRefId 회전은 구매 소유권 이전 아님 |
| EXAM_PASS 복원권 | MEMBER_GRANT / grantId; grantOwnerUserId, grantOwnershipEpoch(신규 제안), grantVersionAtReserve, originatingTrialConsumptionId | grant 소유는 MEMBER 계정 귀속. 원 trial subject는 중복/복원 증거일 뿐 현재 owner로 사용하지 않음 |
| EXAM_PASS 초대권 | MEMBER_GRANT 모델 확장 경계만 | 실제 지급·정상 사용 지원은 이번 구현 승인 범위로 확대하지 않음 |

ownerRevisionAtReserve와 각 VersionAtReserve는 관측/감사 근거다. 현재 값과 무조건 같아야 한다는 조건이 아니다. worker가 pendingEvidence를 갱신하거나 동일 owner가 다른 작업을 수행해도 version은 증가할 수 있다. 현재 owner identity/epoch/state를 재검증하고 그 **현재 version**으로 CAS한다. 명시적 ownership epoch 변화는 별도 lifecycle 규칙을 따른다.

#### ACTIVE MEMBER 증거 — 기존 계약 확인과 남은 선택

[기존 withdrawal 계약 §5](PLAN-009-withdrawal-reconciliation-and-purge-contract.md)는 payment_account_lifecycles.ACTIVE를 **Billing이 탈퇴를 아직 받지 않은 로컬 상태이며 실시간 Identity ACTIVE 보장이 아님**으로 정의한다. 기존 Identity 결제 계약은 purchase token 발급 시 ACTIVE MEMBER 검사·발급/탈퇴 공통 fence와 withdrawal push/feed 복구를 규정한다. 이는 사용자 JWT가 없는 LC worker의 현재 MEMBER 증언 계약이 아니다.

현재 확인 범위에서 account_type을 계속 제공하는 신규 projection/조회 계약은 확인하지 못했다. 구매 당시 MEMBER 확인, purchaseAccountRefId 존재, withdrawal feed coverage를 현재 MEMBER/ACTIVE 증명으로 임의 승격하지 않는다. 기존 구매 요청마다 Identity를 동기 조회하지 않는 계약도 그대로 유지한다.

| Identity 공동 질문 | 확정해야 하는 내용 |
| --- | --- |
| 권위·의미 | Identity가 accountType=MEMBER와 accountState=ACTIVE를 각각 어떤 원 상태에서 판단하는지. 계정 유형 변경 가능성과 탈퇴/정지 범위 |
| 전달 | 기존 증거 확장 가능 여부 또는 별도 서비스 조회/이벤트 projection 중 필요한 방식. 지금 새 API/claim/event를 확정하지 않음 |
| 결속 | exact userId·environment·producer/consumer 목적·검증된 서비스 신원, 원문 사용자 JWT 저장/재사용 금지 |
| 최신성 | owner epoch와 일반 version 분리, observedAt/validUntil 또는 feed watermark/지연 기준, worker에서도 독립 검증 가능 |
| deny 순서 | Identity 탈퇴 commit부터 Billing 관측까지 허용 지연과 T2의 순서 기준. 서비스 간 같은 Transaction처럼 설명하지 않음 |
| 장애/복구 | 증거 없음/조회 실패/coverage 불명은 미확인으로 보류·복구. GUEST로 재분류하거나 trial로 우회하지 않음 |

위 질문은 다음 연동 설계 입력이다. projection과 동기 조회 어느 쪽도 이번 문서에서 채택하지 않았다. 이 계약이 없으면 MEMBER_GRANT 정상 사용까지 수락/활성화하지 않는다. paid도 현재 owner/lifecycle 검증 범위와 지연 한계를 확인해야 하며 로컬 ACTIVE만으로 최신 Identity 상태를 보장하지 않는다.

복원권에 payment account 생성을 강제해 phone subject를 대체하지 않는다. MEMBER 여부는 userId나 payment account 존재만으로 추정하지 않는다. 현재 actor의 ACTIVE MEMBER 증언/동기화 근거를 LC·Identity와 대조해야 하며 R2 userId만으로 그 증명이 완료됐다고 주장하지 않는다. 이 연결은 J4 수락 전 남은 확인 항목이다. grantOwnershipEpoch 초기값/갱신은 지급·탈퇴 deny와 함께 구현 제안으로 검증한다.

### 4.2 비교 시점

| 시점 | Billing | LC |
| --- | --- | --- |
| 신규 reserve | 현재 caller/owner·source 자격·현재 원 대상 검사, snapshot 저장 | 현재 actor/학습 삭제 deny 검사, 접수 증거·원 대상 고정 |
| reserve 성공/실패 replay | 현재 접근 허용 owner 검사 후 key/digest로 최초 결과. 가변 current Session으로 최초 결과 변경 금지 | 요청 actor와 로컬 operation 귀속 확인. 삭제 시험 재생성 금지 |
| T2 confirm 전 | Reservation snapshot과 요청 연결, 현재 owner identity/state/epoch 검증 후 current version CAS. source revoke와 같은 쓰기 경계 | pending Session과 operation 귀속 확인 |
| confirm 성공 replay/status | 현재 접근 가능한 owner에 최초 성공/현재 상태 제공. 단순 기간 만료/refund로 과거 성공을 거절로 바꾸지 않음 | 성공 증거를 받은 뒤에도 현재 lifecycle deny 검사. 성공 증거는 학습 복구/접근 허가 자체가 아님 |
| LC 마무리 | LC 완료를 추정하거나 ack 요구 안 함 | 현재 actor·삭제/탈퇴 fence와 exact operation을 검사하고 local Tx. deny면 활성/재생성하지 않고 lifecycle 복구 정책 적용 |

### 4.3 lifecycle 조합표

| 사건 | 최초 trial | paid | MEMBER 복원권 | 성공 증거/기접수 |
| --- | --- | --- | --- | --- |
| owner 변동 없는 일반 version 증가 | 현재 귀속 검사 후 CAS | 현재 account/lifecycle 검사 후 CAS | 현재 grant owner/epoch 검사 후 CAS | version 숫자 차이만으로 과거 성공 삭제 금지 |
| phone rejoin | 기존 epoch/owner 이전·bounded source event fence 유지, 정상 continuation은 S15 | 이전하지 않음 | 자동 이전/재지급 없음 | 과거 source actor에 일반 신규 요청/replay 권한을 주지 않음 |
| 기존 Guest merge | 기존 trial 계약 범위 유지. source/target replay 상세는 기존 owner fence와 대조 | paid ownership 자동 이전 없음 | MEMBER 보상권 merge 확장 제외 | 원 결과의 새 actor 승계로 추정하지 않음 |
| 탈퇴 | 기존 trial 보존/deny 규칙 | WITHDRAWN이 신규 reserve/confirm보다 먼저면 권리 사용 거절 | 사용자 귀속 deny, 재가입 자동 복구 없음 | 금융 성공은 보존하되 학습자료 재생성/접근 허용 금지 |
| LC 시험/학습 삭제 | Claim 재개방·복원 근거 아님 | 구매 취소/미사용 증거 아님 | 새 지급 근거 아님 | 삭제 이후 기접수 결과도 lifecycle deny 우선. 삭제 continuation은 S15 |
| refund/정상 만료 | 다른 source 영향 없음 | R2의 confirm 순서/D1 적용 | 다른 source 영향 없음 | refund 보호 Session도 삭제/탈퇴 deny를 우회하지 않음 |

owner 변경/탈퇴 선행 confirm에서 T3를 자동으로 ENTITLEMENT_REVOKED로 기록하지 않는다. 그 코드는 구매 환불/revoke 거절이다. J4의 OPERATION_OWNER_MISMATCH는 접근 거절이며 금융 terminal/hold 정리는 기존 expiry 또는 별도 승인 lifecycle 절차가 담당한다. 정확한 owner 이관 복구 응답·삭제 뒤 내부 조회 권한은 S15/V07과 함께 확정한다.

## 5. 신규 fixture 제작 명세 (미제작)

원 fixtures-2는 유지한다. 별도 `lc-billing-paid-exam-j3-j4-fixtures-1` 제안 revision에서 아래 기대값을 공동 수락한 뒤 제작한다. 이 목록은 실행/통과 결과가 아니다.

| ID | 기대 |
| --- | --- |
| J3-01 | terminal context 거절 commit 후 reserve409, 동일 key 동일 digest replay·correlationId 독립 |
| J3-02 | 같은 key 다른 digest는 conflict, current context 변화보다 우선 |
| J3-03 | 응답 유실 후 status 전용409·retryable=false, worker 자기 pending만 terminal/해제 |
| J3-04 | command TTL 뒤 retained receipt로 동일 reserve/status 결과 |
| J3-05 | OPERATION_NOT_FOUND/빈404/401/403/503/owner mismatch는 terminal 증거 아님 |
| J3-06 | 잘못된 caller/user/operation 귀속·응답 혼선·로컬 성공 증거 충돌 시 격리, worker reserve 호출0 |
| J3-07 | 경쟁 요청의 성공/실패 receipt 상호 배타·commit 불명·local guard CAS 재실행 |
| J3-08 | 전용 code의 wrong HTTP/route/retryable 및 누락/unknown/duplicate/trailing/잘못된 타입은 CONTRACT_ERROR |
| J3-09 | PREPARED여도 신규 pending/비활성 Session 존재 시 격리. 이전 Session만 있으면 부재 검사 대상 아님 |
| J3-10 | HTTP 진행→worker CAS와 worker CAS→HTTP 양순서. lease 만료/owner 변경/commit marker 추가 시 stale 결과 적용 금지 |
| J3-11 | remote terminalAt 추정 금지, local observedAt·correlationId 변화가 업무 결과 변경 아님 |
| J3-12 | ownerMismatch는 owner-blocked, 전역 auth circuit·Session·guard 불변, worker reserve 호출0 |
| J4-01 | source별 필수/불필요 owner snapshot·가짜 trial subject 거절 |
| J4-02 | 같은 owner 일반 version 증가 허용, 실제 owner/epoch/state 변경은 별도 거절 |
| J4-03 | confirm/탈퇴 양 commit 순서·success replay와 학습 deny 분리 |
| J4-04 | source별 refund/만료·다른 무료권 보존·원 claim 보존 |
| J4-05 | 삭제 후 Summary/시스템 retry도 lifecycle deny 우선, 자료 재생성 없음 |

phone 정상 지원/merge target의 상세 재현/보존 폐기 후 재유입·MEMBER 증언 연동의 미확정 성공 사례는 제작하지 않는다. JSON 오류 모형/receipt 구조 검증과 실제 HTTP/Tx/LC worker 실행을 별도 기록한다.

## 6. 부록 — 접수 상태·확인 요청·근거

| 항목 | 요구 방향 | 실제 검증 |
| --- | --- | --- |
| J1/J2 | LC 이견 없음 접수 | 모든 경로/flag·guard 및 unknown commit runtime 미검증 |
| J3 | 이 문서의 전용 status code·4-field 오류·worker 종료 조건 공동 검토 요청 | 신규 fixture/코드 미실행 |
| J4 | source별 owner snapshot·version/epoch 구분·lifecycle 표 공동 검토 요청 | MEMBER 증언 연동·S15/V07 미확정 |
| S15/V07 | 보류 | 정상 지원·폐기 기대값 미동결 |

LC에 확인할 핵심은 (1) J3 전용 status code를 현재 worker category로 분리해 terminal 처리 가능한지, (2) J4 source별 owner 검증과 현재 actor/lifecycle의 비교 위치가 맞는지, (3) ACTIVE MEMBER 근거의 실제 전달/동기화 방식이다. 이 문서로 새로운 제품 정책을 선택하거나 구현 승인했다고 처리하지 않는다.

근거: [현재 오류 DTO](../../src/main/java/web/tosunsaeng/billing/global/response/InternalApiError.java), [오류 handler](../../src/main/java/web/tosunsaeng/billing/global/exception/InternalApiExceptionHandler.java), [Reservation 오류](../../src/main/java/web/tosunsaeng/billing/domain/reservation/exception/ReservationException.java), [현재 status](../../src/main/java/web/tosunsaeng/billing/domain/reservation/application/ReservationLifecycleService.java), [trial link](../../src/main/java/web/tosunsaeng/billing/domain/entitlement/trial/domain/entity/BillingSubjectLink.java), [payment account](../../src/main/java/web/tosunsaeng/billing/domain/payment/application/PaymentAccountService.java), [withdrawal CAS](../../src/main/java/web/tosunsaeng/billing/domain/payment/application/PaymentWithdrawalApplier.java).

이번 draft-2는 LC 회신의 코드 조사 결과를 전달 근거로 접수했다. LC client의 code-only reader 및 worker 신규 category 부재는 이번에 자체 재검증하지 않았다. Billing 기존 Identity/withdrawal 계약은 읽기 대조했다. 이번 작업은 Billing 코드 읽기·보완안/기록 작성이다. LC 회신은 사용자 전달 근거로 접수했고 LC 코드/테스트를 재실행하지 않았다. 기존 계약/fixtures/제품/DB/Jira/배포/외부 전송/commit/push는 변경하지 않았다.


### 2026-10-14 LC draft-2 재검토 접수

사용자 전달 회신의 후속절에 따라 J3 PREPARED/신규 Session 부재·strict status 오류·동일 operation fence·원자 terminal/guard·시각 구분·owner-blocked 및 J3-08~12 보완은 이번 변경 범위에서 추가 이견 없음으로 접수한다. 앞선 draft-1 지적을 미반영으로 다시 열지 않는다. 이 확인은 최종 공동 동결/신규 fixture 제작·검증/제품 구현 승인이 아니다.

J4 기술 방향과 미확정 범위 명시에도 추가 지적은 없으나 증거 연동은 미확정이다. 다음은 J3 수락 범위 기록 후 별도 승인된 독립 fixture 제작과, J4 Identity 증거 전달·최신성·탈퇴 선후관계 계약 확인으로 나눈다. S15/V07 및 MEMBER_GRANT 정상 기대값은 계속 보류한다. R2/fixtures-2 bytes는 유지한다. LC 이번 검토는 문서 diff 검토이며 테스트 재실행 결과가 아니다.
