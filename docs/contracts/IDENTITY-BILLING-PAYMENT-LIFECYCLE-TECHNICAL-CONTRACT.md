# Identity–Billing 공동 기술 계약: 구매 권한·탈퇴 capture·복구·재개

- 작성일: 2026-10-07 / 상태: 정책·기술 설계 사용자 승인, 아래 후속 exact 규격 상대 수락·운영 검증 별도 / Jira TMI-199 (에픽 TMI-137)
- 최신 수락: 2026-10-07 Identity가 [exact wire/index §5~6](TMI-199-EXACT-WIRE-AND-INDEX-SPEC.md)을 수락하고 신규42건 독립 검증을 회신했다(해당 문서 §8). 본문에 남은 이 버전 exact 규격 상대 수락 대기는 해소됐다. 실제 구현·운영 검증 및 병합/Jira 종료 승인은 별도다.
- 개정: 2026-10-07 R1~R5 및 추가 명세 보완. baseline/scan/coverage·control 이관·경로별 commit 불명 처리·durability/restore·보존 기산·retry/canonical 규격을 구체화했다. 개정은 승인 상태를 변경하지 않는다.
- 추가 개정: Identity 재검토 §7 반영. ACK digest를 contentDigest로 통일하고 consumerRecoveryGeneration·복구 시작/old worker fencing, readiness 영향 범위·enum을 정리했다. 운영/보존 승인은 여전히 별도다.
- 기준: [Identity 인계서](IDENTITY-PAYMENT-ACCOUNT-LIFECYCLE-HANDOFF.md), [회신 검토](IDENTITY-PAYMENT-HANDOFF-REVIEW-2026-10-07.md), [Billing 삭제 설계](PLAN-009-withdrawal-reconciliation-and-purge-contract.md), [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md).
- 2026-10-07 최종 사용자 승인: 본 개정안의 보존·token 상한·운영 초기값 및 기술 설계를 채택한다. 앞선 개정 이력과 본문에 남은 ‘제안/승인 전’ 표현 중 이 범위의 사용자 선택은 이 승인으로 갱신한다. Identity의 상대 계약 수락·실측/fixture·exact index 구현 가능성 검증은 대체하지 않는다. 실제 이관 시간/실행, 코드 구현 착수, Jira 변경, 배포·판매·자동삭제, 개별 예외 보존 연장은 별도다. 기존4필드 탈퇴 wire/LC 계약/무료 정책은 유지한다.

## 1. 5줄 결론

1. 구매 token은 ACTIVE MEMBER 확인·공통 fence 쓰기·서명 결과 저장이 성공한 Transaction commit을 기준으로 승인한다. 응답 지연으로 만료시각을 늘리지 않는다(§5.1).
2. 탈퇴 최소 사실은 같은 Transaction의 순번 journal에 capture하고, Billing 발송 OFF와 무관하게 남긴다. capturedAt 관측시각 기준 원천120일 보존은 **사용자 승인**됐다(§3, §5.2).
3. 과거분은 고정 snapshot, 신규분은 commit과 원자적인 sequence feed로 연결한다. 로컬 `SNAPSHOT_APPLIED`와 과거 coverage의 `COMPLETE`를 구별한다(§5.3).
4. Billing204만 완료로 처리하고, 인증 오류는 BLOCKED_AUTH·일시 오류는 bounded retry·중단 시 capture 유지로 재개한다(§5.4).
5. token/coverage/보존·Mongo snapshot 검증 전 판매/purge를 활성화하지 않는다. 완료 기준과 owner별 테스트를 §5.5/§6에 명시한다.

## 2. 사용자가 반드시 읽어야 하는 내용

제품 정책은 그대로다. 미구매 탈퇴 계정은 withdrawnAt+15일 이후 거래 확인까지 끝나야 삭제한다. 매시간 대상 점검·매일 미확인 재확인·최초 미해결 후7일 이내 담당 검토는 승인된 값이다. 이 문서는 그 안전성을 뒷받침할 두 서버의 기술 규격이다.

“탈퇴 후 새 token 발급 금지”는 **발급 승인 commit** 기준이다. 승인 commit이 탈퇴보다 먼저라면 네트워크가 늦어 탈퇴 후 token 응답이 도착할 수는 있다. 이때 기존 승인대로 원 만료까지만 유효하며 새 만료로 재서명하지 않는다. Billing이 탈퇴를 수신했다면 구매 계정 사용은 local 상태로 거절한다. 이미 열린 Store 결제창까지 취소할 수 있다는 보장은 하지 않는다.

“현재 남은 tombstone을 모두 읽었다”와 “과거 모든 탈퇴를 복구했다”는 다르다. 삭제된 과거 자료가 있는지 증명하지 못하면 `coverage.legacy=PARTIAL` 또는 `UNKNOWN`으로 보고한다. 정상 빈 결과·완료 화면으로 감추지 않는다.

## 3. 승인된 사항과 별도 실행 승인

| 권장 결정 | 제안값/의미 | 현재 상태 |
| --- | --- | --- |
| 새 탈퇴 사실 원천 보존 | capture 시도 시작 관측시각 capturedAt 기준120일, userId/withdrawnAt/eventId 포함; commit 시각과 구분 | 사용자 승인; 허용 지연/오차는 §5.2, 법적 적합성 검증 완료라는 뜻은 아님 |
| snapshot 사용자 자료 보존 | run 시작 후7일 이내 삭제, 완료 시 더 일찍 정리 가능 | 사용자 승인; Identity User tombstone 수명을 변경하지 않음 |
| 복구 operation/coverage metadata | snapshot/recovery operation 생성 후120일; 현 stream 최소 checkpoint 유지, 폐기 stream 종료 후120일 | 최소 필드/목적 범위 사용자 승인; 개인정보 재연결 가능성·삭제 구현 검증 필요 |
| purchase token 수명 상한 | 새 발급 최대30분, clock skew 최대60초 | 사용자 승인; 운영/과거 설정 검증·Identity 합의 필요 |
| capture 이관 | 관련 탈퇴 writer의 짧은 barrier, 실제 상한은 측정 후 승인 | 무중단이라고 가정하지 않음; 승인 전 운영 실행 금지 |
| 미해결 장기 증거 |120일 전에 개별 최소 case/종료일 승인 또는 명시 GAP 처리 | 무기한 pending 원문 보존 금지; 개별 보존 승인 없는 자동 연장 금지 |

route·retry·복구 세대·index 설계와 기술 초기값은 사용자 채택 완료이며 양 서버 검토/검증 후 구현 규격으로 동결한다. 담당자 최초 검토7일은 유지하되 snapshot7일/원천120일과 서로 다른 목적이다. coverage gap 때문에 추가 이용 제한이 필요하면 별도 사용자 승인 없이 적용하지 않는다. 원천 보존을 기존 금전5년/무료Claim3년/미구매계정15일 정책과 혼용하지 않는다.

## 4. 주요 위험과 미확인 사항

### 2026-10-07 TMI-199 후속 기술 선택 승인

사용자는 동일 영속 시각·숫자/오류/인증/index 규격 방향과 단계별 장애 검증 권장안을 승인했다. 이 절은 본문의 해당 후보/제안 표현을 사용자 승인 상태로 갱신한다. 앞선 공동 설계에 대한 Identity 확인과 fixture 독립44건 통과는 확인됐지만, 이번 후속 세부 규격의 상대 수락·endpoint별 전체 nullable 목록·물리 index manifest 검증까지 완료했다는 뜻은 아니다. Jira 완료·배포·판매·자동삭제·실제 이관 시간 승인은 포함하지 않는다.

- 신규 탈퇴의 `withdrawnAt`은 Transaction에서 한 번 밀리초로 절삭하여 User/outbox/journal에 동일 값으로 저장한다. snapshot은 저장된 User 값을 사용한다. backfill은 기존 영속 값을 복사하며 과거 event/digest는 변경하지 않는다. 재전송에서 시각을 재생성하지 않는다. 기존 canonical decoder의 나노초 지원은 유지한다. `capturedAt` 관측 기산과 Mongo 논리 timestamp `T`는 별개다.
- sequence/H/through/count/version은 wire canonical decimal string, 영속 값은 BSON int64/Java long이다. 범위는0..Long.MAX_VALUE이며 event sequence는1부터다. overflow·부호·선행0을 거절하며 각 상태의 최소값 제약을 추가 적용한다. `T`는 BSON Timestamp, wire에서는 unsigned32 범위의 `{seconds,increment}` 문자열 pair다. schemaVersion은 JSON integer1, boolean은 JSON boolean을 유지한다. 필수 nullable/생략 허용은 endpoint별로 명시하고 임의 호환 처리하지 않는다.
- 신규 internal API만 direct DTO와 `code,message,retryable,correlationId` 오류 envelope를 사용한다. 기존 public BaseResponse와 LC 계약은 그대로다. 기존400/401/403/409/410/422/503 사유 및 route별 성공 응답을 유지한다. withdrawal push의204-only 정책을 snapshot202 응답 등에 확대 적용하지 않는다. Lattice 자체 거절은 앱 JSON을 보장하지 않으므로 client는 status-first로 분류한다.
- 신규 양방향 Billing 전용 통신은 Lattice AWS_IAM+SigV4로 한다. Identity ingress 준비·역할/환경별 route allowlist·direct 접근 차단은 배포 gate다. 일반 Billing task의 데이터 복구 권한과 새 recovery generation 발급용 운영 권한을 분리한다. 인프라 미지원 시 unsigned/JWT 임의 fallback 없이 계약 변경 승인을 요청한다.
- index는 additive manifest와 versioned initializer로 이름/key order/unique/partial/TTL을 비교한다. 불일치는 fail-fast하며 자동 drop/recreate하지 않는다. 운영 전 중복/타입 진단·상대 manifest 수락이 필요하다. 기존 business state를 TTL로 지우지 않는다.

후속 검증 책임과 통과 기준은 다음과 같다. 작업 소유 서비스 기준의 분담이며 실제 담당자 이름·환경 사용 권한은 별도 지정한다.

| 작업/소유 | 수행 단계 | 필수 통과 기준 |
| --- | --- | --- |
| TMI-200 / Identity | control·발급 구현 통합 테스트 | 발급/탈퇴 공통 CAS 경합 수렴, 기존 보호 필드 보존, 이관 누락 진단 및 중단/재개 검증 |
| TMI-202 / Identity | capture 구현, disposable 다중노드 Mongo | failover에도 User/counter/journal/delivery 원자성·중복 방지 유지; 실제 성공 commit 응답 유실 후 동일 transaction 결과 수렴 |
| TMI-203 / Identity | recovery 구현, history 제어 테스트 환경 | 실제 history 소실 시 부분 snapshot READY 금지, T 변경 fallback 금지, 새 run으로만 재시작 |
| TMI-204 / Billing | recovery 구현 통합 테스트 | 복원 후 old worker/ACK 쓰기 차단, ACK 응답 유실·중복에서 checkpoint/적용 증거 수렴 |
| TMI-206 / 공동 | 운영 동등 staging, 별도 실행 승인 | 실제 Mongo/driver의 고정 snapshot5분 budget·IAM 허용/거절·복원 coverage·barrier 실측 및 rollback 검증 |

C0는 양측 exact 규격 수락 및 남은 검증의 담당 작업/통과 기준 기록 후 완료 판단한다. 미검증을 통과로 간주하지 않으며 해당 후속 기능의 활성화 gate로 유지한다. 모든 운영 실험을 개발 전체의 선행조건으로 두지는 않지만 판매·자동삭제는 관련 gate 통과 전 OFF다. Jira 완료/후속 이슈 수정은 별도 승인 후 수행한다.

### 남은 구현·운영 위험

- Identity의 현행 issuer는 계정 상태 없이 scope를 선택하고 equalsIgnoreCase로 UUID를 확인한다. 로그인 일부의 ACTIVE 검사만으로 아래 공통 계약이 충족되지 않는다.
- Mongo 고정 read snapshot을 여러 page에 재사용하는 서버/driver 기능·history window가 운영 환경에서 가능한지 검증해야 한다. 지원이 안 되면 현재 시점 조회로 대체하지 않고 snapshot 시작을 실패시킨다.
- global journal counter는 탈퇴 Transaction을 직렬화한다. 탈퇴 처리량/지연·transaction retry 부하를 측정하고 부적합하면 partition별 vector watermark 설계를 별도로 검토한다. 이번 버전은 단일 환경 stream이다.
- token 과거 최장 TTL과 old writer/replay 경로가 남으면 새30분 설정만으로 과거 token 소멸을 증명할 수 없다.
-120일 이후 원천 소실 구간은 feed가 복구할 수 없다. Identity tombstone 보존 현황/backup으로 증명 가능한 기간을 확인해야 한다. gap을 자동 보존 연장으로 해결하지 않는다.
- Billing의 RevenueCat 미관측 거래·미구매 증명과 cursor activity 충돌은 별도 gate이며 이 계약으로 해결됐다고 하지 않는다.

## 5. 공동 구현 규격

### 5.1 구매 권한과 탈퇴의 선형화 지점

#### A. 공통 발급 경계

Identity의 사용자별 기존 session-security control을 재사용하는 것을 우선하되, 모든 purchase 발급과 탈퇴가 **같은 document를 조건부 쓰기**해야 한다. read-only 상태 확인만으로 대체하지 않는다. 제안 필드: `userId`, `state`, `accountType`, `authEpoch`, `version`, `lastPurchaseTokenExpiresAt`. 기존 schema가 같은 불변식을 제공하면 중복 control을 만들지 않는다.

1. user 상태/type과 control을 Transaction 안에서 읽고 ACTIVE MEMBER를 확인한다. control 누락/불일치는 fail-closed하며 발급 과정에서 탈퇴 계정을 ACTIVE로 upsert하지 않는다.
2. default/명시 scope에 들어온 `billing:purchase`는 먼저 제거한다. 신뢰된 위 조건을 통과한 사용자 발급에만 policy가 추가한다. Guest는 purchase 없이 기존 read를 유지하고 비활성 계정은 기존 로그인/재발급 거절 정책을 유지한다. 타 scope/audience 계약은 변경하지 않는다.
3. `authEpoch/version` exact CAS 쓰기로 발급을 fence한다. `issuedAt`을 이번 시도의 고정 시각으로 정하고 `exp=issuedAt+configuredTTL`, `configuredTTL<=30분`을 검증한다. 기존 wire claim은 유지하며 authEpoch는 이번 계약에서 사용자 JWT 필수 claim으로 추가하지 않는다.
4. 로컬 signer로 서명하고 refresh/암호화 retry 응답이 필요한 경로는 기존 보안 보존 정책에 따라 같은 Transaction에 저장한다. control의 `lastPurchaseTokenExpiresAt=max(기존, exp)`도 쓴다. raw JWT를 새 audit/로그에 저장하지 않는다.
5. commit을 확인한 결과만 HTTP 응답 후보로 반환한다. rollback/unknown commit을 성공으로 반환하지 않는다. 조회 가능한 durable 결과가 있는 경로만 §5.1 E에 따라 복구하고, 일반 로그인에는 존재하지 않는 issuance receipt를 가정하지 않는다. 응답 직전 만료/기존 응답 deadline을 확인하고 만료했으면 성공 반환하지 않는다.

탈퇴 Transaction도 같은 control을 WITHDRAWN/epoch 증가로 쓰고 세션 폐기·탈퇴 journal을 함께 commit한다. 탈퇴가 먼저면 발급 Transaction은 충돌 후 상태 재검사에서 거절된다. 발급이 먼저면 원 token은 기존 expiry 정책을 따르고 이후 신규 발급은 거절된다. transaction retry 시 이전 시도의 JWT는 외부에 내보내지 않는다.

signup/upgrade/merge의 기존 transaction 뒤 무조건 서명하는 경로는 이 공통 발급 service를 호출하도록 바꾼다. 가입/승격 성공과 token 발급 실패가 함께 발생할 수 있으므로 사용자에게 재로그인 가능한 기존 오류 흐름으로 수렴시키며 계정 transaction 자체를 임의 rollback한 것처럼 응답하지 않는다.

#### B. response replay

암호화 refresh 응답 replay도 ACTIVE/type/epoch를 공통 Transaction에서 검증하고 control을 touch한다. **기존 token bytes/iat/exp를 그대로 반환**하며 TTL을 연장하지 않는다. 탈퇴가 먼저 commit하면 replay 거절, replay 승인 뒤 탈퇴면 응답 지연 가능성은 원 expiry로 제한된다. 복구 API가 실제 token을 발급하지 않는다면 발급 경로로 분류하지 않는다.

#### C. lowercase 및 drain

신규 발급 sub는 canonical lowercase UUID와 exact match해야 한다. legacy uppercase는 현황 조사 후 repair/거절 전환을 합의하며 단순 lowercase 변환으로 ownership을 바꾸지 않는다.

새 규격 전체 전환 이후의 token은 발급 commit이 탈퇴보다 앞서므로 `withdrawnAt + 최대TTL + skew`가 보수적 상한이 되려면 withdrawnAt의 서버시각 정의/clock 오차도 검증돼야 한다. 실제 control에 기록된 최대 exp를 우선 증거로 사용한다. 구버전 token은 과거 TTL·마지막 구버전 발급 가능 시각까지 확인한다. Billing 진행 요청 drain 상한도 별도로 더한다. 이를 계산할 수 없으면 purge는 차단한다. UserWithdrawn v1에 expiresAt 필드를 추가하지 않고 환경 cutover 증빙/필요 시 별도 최소 복구 계약으로 전달한다.

#### D. control 초기화·이관·flag gate (R4)

- 신규 가입은 User와 control을 같은 Transaction에서 생성한다. Guest 승격/merge는 신뢰된 상태/type과 기존 control의 필요한 필드를 원자 갱신한다. 기존 sessionEpoch/activeLogoutId/보호 작업·fence를 덮어쓰거나 초기화하지 않는다.
- 기존 control 누락은 별도 bounded migration으로 처리한다. User 상태와 version을 읽고 User에도 조건부 쓰기를 수행해 탈퇴 writer와 충돌하도록 하며, control unique(userId) insert와 함께 commit한다. 탈퇴가 먼저면 WITHDRAWN 상태만 반영하거나 기존 탈퇴 경로로 수렴하며 ACTIVE로 복구하지 않는다. 탈퇴 writer 역시 control 부재를 ACTIVE 기본 객체로 해석하지 않고 terminal control을 원자 생성해야 한다.
- 이미 control이 있는 경우 missing field만 승인된 reader-first 규칙으로 보완한다. 타입 불일치/진행 보호 작업은 진단 대상으로 분리하고 일괄 replace 금지다.
- cutover gate는 신규 writer 전수 적용, 승인된 대상 범위의 누락/불일치 0, concurrent withdrawal/upgrade 테스트, 기존 보호 필드 보존, 구버전 writer drain이다. 수치는 개인정보 없는 집계로 기록한다. 현행 control()의 transient 기본 객체를 durable 준비 완료로 세지 않는다.
- `purchaseScope.enabled=true`는 `issuanceFence.required=true`, `withdrawalCapture.required=true`, control migration gate 충족을 요구한다. 정적 설정이 모순된 **새 배포 instance는 startup 실패**로 서비스에 투입하지 않으며 기존 호환 instance를 유지한다. 실행 중 purchase 전용 gate가 미충족되면 purchase 발급/전용 readiness만 차단하고 이를 Identity 전체 ALB health에 자동 연결하지 않는다. 기존 인증의 공통 DB/보안 자체 장애는 원래 전체 장애 정책을 따르며 안전하지 않은 로그인 fallback은 금지한다. 기존 서비스까지 영향을 주는 cutover/rollback은 사전 영향 검토·승인이 필요하다.

#### E. 경로별 결과 불명·만료 응답 (R5)

| 경로 | unknown commit/유실 처리 | 응답·클라이언트 동작 제안 |
| --- | --- | --- |
| 기존 reissue recovery | 기존 인증된 operation/암호화 결과 저장소로 조회. 같은 request 바인딩과 epoch/deadline 검증 뒤만 원 bytes replay | 불명은503 AUTH_ISSUANCE_UNRESOLVED; 기존 retry 계약 유지 |
| 일반 로그인·SNS 발급·가입/승격 후 발급 | driver의 bounded commit retry 후에도 불명이면 결과를 반환하지 않고503. 새 인증 요청은 새 발급으로 처리 | AUTH_ISSUANCE_UNRESOLVED; 자동 signup/merge 재실행 대신 로그인/기존 인증 복구 |
| 저장된 replay가 만료됨 | 재서명/TTL 연장 금지, 기존 결과로 성공 응답 금지 |401 AUTH_REPLAY_EXPIRED; 클라이언트 재로그인. 원 source refresh를 새 요청으로 무한 재사용하지 않음 |

오류 이름은 Identity public envelope에 매핑할 신규 제안이며 기존 오류와 중복/프론트 호환성을 합의 후 적용한다. 일반 경로의503은 rollback 보장이 아니다. 반환하지 못한 token/refresh session이 남을 수 있어 기존 session 만료·개수 제한·보안 정리로 관리하고 commit이 불명인 session을 무조건 revoke/재사용하지 않는다. 새 receipt/raw JWT 저장·새 보존기간은 이번 개정에 추가하지 않는다. 기존 reissue 암호화 보존은 그대로다.

### 5.2 탈퇴 사실 capture·보관

#### A. journal과 목적지 분리

환경별 `withdrawal_stream`의 `streamId`와 단일 `lastSequence`를 사용한다. counter 증가, 최소 journal insert, 목적지 delivery 생성, User/control 탈퇴 및 refresh폐기는 **하나의 Mongo Transaction**이다. sequence는64비트 정수이며 JSON에는 양의 decimal string으로 전송한다. 같은 counter 쓰기 충돌을 통해 작은 sequence의 commit이 뒤늦게 나타나는 문제를 막는다. rollback된 counter 증가도 rollback한다. 변경될 수 있는 withdrawnAt을 cursor로 사용하지 않는다.

원천 최소 필드: `streamId, sequence, eventId, userId, withdrawnAt, capturedAt`. `capturedAt`은 최종 성공한 capture Transaction 시도의 시작 관측시각이며 **commit timestamp가 아니다**. 승인 시 expiry=capturedAt+120일로 한다. attempt 실행/commit 관측 budget30초와 최대 clock 오차60초를 초기 gate로 제안한다. 따라서 정확한 commit 후120일 보장이 아니라 bounded 오차를 가진 관측시각 기준이다. unknown commit/중단으로 bound를 증명하지 못하면 해당 cleanup을 차단하고 확인하며 capturedAt을 재시도/발송 시각으로 갱신하지 않는다. 유실 증빙 때문에 임의 보존 연장하지 않고 별도 case 승인을 따른다. 사용자15일 기산점은 항상 withdrawnAt이다. schemaVersion은1 고정. 전화번호·SNS·purchase ref·token·raw JSON은 넣지 않는다.

목적지 delivery는 `eventId,destination,status,resumeGeneration,lifetimeAttemptCount,generationAttemptCount,notBefore,nextAttemptAt,leaseOwner,leaseUntil,leaseVersion,lastFailureCode,deliveredAt,reviewDueAt`를 두고 payload는 journal 참조로 얻는다. 전송 attempt와 lease claim은 별도다(§5.4). 기존 LC outbox를 당장 이 구조로 교체할 필요는 없다. Billing capture와 LC 기존 outbox를 같은 탈퇴 Transaction에 저장하고 LC cleanup이 Billing journal을 지우지 않도록 하는 단계적 이관을 허용한다.

`capture.required=true`는 cutover 이후 필수다. `billing.publisher.enabled=false`여도 journal과 Billing PENDING delivery는 저장된다. 이미 capture를 보장한 환경에서 capture 미지원 구버전으로 rollback할 수 없다. 중단하려면 publisher만 끄고 capture 호환 버전은 유지한다. capture 장애 시 탈퇴 Transaction이 부분 commit되지 않도록 하며 탈퇴 실패/지연 영향은 이관 승인에 포함한다.

#### B.120일 만료와 미전달

승인된다면 capturedAt+120일에 명시적 cleanup을 실행한다. business journal을 TTL로 조용히 삭제하지 않는다. consumer별 미적용 가능 범위는 만료7일 전 경보·담당 검토를 시작하고, 승인된 연장이 없으면 만료 시 replay availability와 consumer 적용 증거를 분리해 기록한 뒤 사용자 연결을 제거한다. 긴 Retry-After/notBefore도 원천 expiry를 늘리지 않는다.

- `replayFloor`: 더 이상 원 event를 재생할 수 없는 연속 prefix 끝. capturedAt 순서와 sequence가 어긋나 중간 row만 만료하면 별도 unavailable interval로 관리하고 floor를 그 너머로 건너뛰지 않는다.
- `verifiedStateBaseline`, `verifiedFeedCheckpoint`: consumer별 snapshot 상태와 연속 event 적용 증거. source cleanup만으로 이미 증명된 적용 범위를 취소하지 않는다.
- `coverageGap`: 해당 consumer가 아직 검증하지 못한 필요한 범위가 사라진 경우. `{streamId,consumer,fromSequence,toSequence,reason,evidenceKind}`만 기록하고 user/event 목록을 복제하지 않는다. 해당 범위의 stream coverage에만 영향을 주며 legacy COMPLETE를 무조건 취소하지 않는다. legacy 증거 자체가 부족하면 별도 PARTIAL/UNKNOWN이다.
- push204는 개별 event 적용 증거다. feed ACK는 연속 원 event 적용 증거다. snapshot baseline은 상태 적용 증거일 뿐 원 event/digest 수신은 아니다. delivery가 PENDING이어도 이미 같은 event를 포함한 feed ACK가 있으면 미적용 GAP로 세지 않는다. snapshot-covered 원 event는 replay 불가를 명시하되 상태 baseline을 무효화하지 않는다.
- 만료된 prefix로 되감는 요청은410 REPLAY_UNAVAILABLE. 기존 정상 consumer는 checkpoint 이후를 계속 읽는다. Billing 복원으로 checkpoint/검증 증거가 소실되면 Identity의 예전 ACK만 믿지 않고 UNKNOWN/GAP로 다시 평가한다.

완료·실패 delivery도 journal과 함께 사용자 재연결 reference를 정리한다. 별도 금융/분쟁 보존 필요 시 해당 case 승인 기준을 적용하고 일반 원천을 무기한 보존하지 않는다. 기존 Identity User tombstone 보존은 이120일로 임의 변경하지 않는다.

### 5.3 과거 snapshot + sequence feed

#### A. capture cutover

capture 지원 writer 배포 후 구버전의 진행 중 탈퇴 Transaction을 drain하고 writer barrier 안에서 stream 시작 marker를 commit한다. 모든 이후 탈퇴가 journal에 남는다는 검증 뒤 barrier를 해제한다. 운영 중단 시간은 사전 측정/사용자 승인 없이는 실행하지 않는다. 이 marker 이전 사용자 이력은 legacy 범위다.

#### B. 제안 internal 복구 API (Identity가 제공)

Billing 전용 role, environment 분리, workload 인증 방식/IAM은 양 서버 배포 계약으로 검증한다. Identity가 이 목적의 Lattice ingress를 갖췄다고 가정하지 않는다. 지원되지 않으면 승인된 별도 authenticated batch 전달 방식으로 설계를 변경하고, unsigned/public feed를 열지 않는다.

| route 제안 | request | response |
| --- | --- | --- |
| POST /internal/v1/billing/withdrawals/recoveries | 별도 운영 role, Idempotency-Key 필수; {streamId,expectedGeneration:null 또는 UUID,reason:INITIAL 또는 BILLING_RESTORE 또는 RESYNC} |200 {consumerRecoveryGeneration,recoveryState:RECOVERING}; Identity CAS로 새 세대 발급 |
| POST /internal/v1/billing/withdrawals/snapshots | body 없음, Idempotency-Key lowercase UUIDv4 필수 |202 {snapshotId,status:BUILDING}, Retry-After:5 |
| POST /internal/v1/billing/withdrawals/snapshots/status | {snapshotId} |200 상태/manifest; READY 전 page 제공 금지 |
| POST /internal/v1/billing/withdrawals/snapshots/page | {snapshotId,cursor:null 또는 opaque} |200 {items,nextCursor,done} |
| POST /internal/v1/billing/withdrawals/feed/page | 첫 요청 {streamId,afterSequence,scanCursor:null}; 이후 {streamId,afterSequence,scanCursor} |200 {items,scanCursor,targetThroughSequence,nextAfterSequence,scannedThrough,done} |
| POST /internal/v1/billing/withdrawals/checkpoints | {streamId,mode:BASELINE 또는 RESYNC,throughSequence,snapshotId,contentDigest,expectedCheckpointVersion} 또는 {streamId,mode:FEED,throughSequence,scanCursor} |204, mode별 검증 후 consumer 적용 완료 checkpoint |
| POST /internal/v1/billing/withdrawals/checkpoints/status | {streamId} |200 {exists,consumerRecoveryGeneration,recoveryState,version,verifiedStateBaseline,verifiedFeedCheckpoint,coverage}; 없음은 exists=false, 숫자0과 구분 |

internal public wrapper 없음, strict schema·16KiB 요청/응답 상한·page 최대20건(크기에 따라 더 작게), redirect 금지. invalid400, 인증401/403, snapshot 미완료409 SNAPSHOT_NOT_READY, snapshot 만료410 SNAPSHOT_EXPIRED, cursor 만료410 CURSOR_EXPIRED, source 재생 불가410 REPLAY_UNAVAILABLE, 검증 범위 gap409 COVERAGE_GAP, CAS409 CHECKPOINT_CONFLICT, 일시 장애503. cursor는 환경/consumer/stream/snapshot 또는 scan에 귀속되고 임의 URL이 아니다. phase0에서 exact DTO/오류 형식을 동결해야 하며 이 route가 현재 존재한다는 뜻은 아니다.

feed item은 `{sequence,event:{eventId,schemaVersion,userId,withdrawnAt}}`다. 이는 복구 envelope이고 기존 push wire는4필드를 유지한다. 정상 feed item도 Billing의 동일 event 수신 서비스/멱등 처리에 넣는다. HTTP v1 payload에 sequence를 추가하지 않는다.

checkpoint/status는 authenticated consumer 본인 상태만 반환하며 별도 consumerId 입력으로 타 consumer를 지정하지 못한다. 상태 값은 Identity가 받은 ACK 기록이지 Billing 복원본의 local 적용을 대신하는 증거가 아니다. sequence item은1부터, baseline/after/H/H2는0 허용 decimal string이며 선행0·부호·64비트 overflow를 거절한다.

복구 데이터 API(snapshot 생성/status/page, feed, checkpoint ACK)는 필수 `X-Consumer-Recovery-Generation` header의 lowercase UUID를 검증한다. 이 header는 인증 수단이 아니며 principal/stream에 귀속된 Identity 현재 generation과 exact match해야 한다. recoveries와 checkpoint/status는 세대 확인/발급 경로이므로 이 header를 요구하지 않지만 별도 role/consumer 권한 검사는 유지한다. 불일치409 RECOVERY_GENERATION_MISMATCH, 복구 준비 미완료409 RECOVERY_NOT_READY. 세대 검사를 낮은 ACK204 처리보다 먼저 한다.

snapshot item은 `{userId,withdrawnAt}`의 **별도 사실 DTO**이며 가짜 eventId를 합성하지 않는다. Billing은 `(snapshotId,userId)` dedupe와 user/withdrawnAt semantic 수렴을 사용한다. 현재 lifecycle이 같은 사실이면 NOOP, 다른 시각이면 conflict/담당 검토이며 자동 교정하지 않는다. snapshot은 journal 원 event의 eventId/digest 충돌 확인을 대체하지 않는다.

#### C. 고정 snapshot 작성

snapshot session의 동일 `atClusterTime=T`에서 User WITHDRAWN 집합과 `lastSequence=H`를 읽는다. User 집합은 `_id` keyset pagination하되 모든 page는 **동일 T**다. H는 같은 T의 commit된 journal counter다. T 뒤 늦게 commit한 탈퇴는 capture의 sequence>H로 feed에 나타나므로 과거 withdrawnAt을 가져도 빠지지 않는다.

snapshot materialization을 서버가 수행해 durable rows와 manifest를 만들고 최종 count/digest/sealed=true를 원자 확정한다. 초기 생성 budget5분, page20을 제안한다. snapshot history 소실/timeout/서버 재시작으로 동일 T 복구 불가 시 FAILED로 닫고 부분 export를 READY로 노출하지 않는다. 새 snapshot/run으로 다시 시작한다. 변경 중인 User live pagination으로 슬쩍 전환하지 않는다. 고정 read snapshot 기능은 실제 Mongo/driver로 fixture 검증 전 구현 확정 금지다.

불변 data manifest는 `snapshotId,streamId,consumerRecoveryGeneration,T,H,itemCount,pageCount,contentDigest,legacyCoverage,knownGaps,expiresAt`다. 실행 status/ACK/lease/cleanup 결과는 별도 mutable run metadata로 분리한다. knownGaps는 bounded 요약이며16KiB를 넘는 상세는 별도 승인된 pagination으로 조회하고 배열을 무한 확장하지 않는다. READY 뒤 rows/data manifest는 불변이다. resume cursor는 같은 READY snapshot에서만 재사용한다. 개인정보 rows/cursor 매핑은 시작+7일 이내 제거하는 안이며 expiry를 retry로 늘리지 않는다.

canonical item은 UTF-8(BOM 없음)의 compact JSON, 필드 순서 userId→withdrawnAt, UUID는 lowercase exact, Instant는 Java Instant.toString()과 동일한 UTC Z/0·3·6·9자리 소수초 표현이다. parse 후 나노초까지 보존하며 부동소수 변환 금지다. 각 item 앞에 **UTF-8 byte 길이 unsigned32-bit big-endian**을 붙여 전체 userId ASCII 오름차순 stream의 SHA-256을 계산한다. digest는 lowercase64 hex, 빈 목록은 zero-byte SHA-256이다. page별 JSON/length는 전체 digest에 넣지 않는다. 페이지 index0부터 연속, item 중복/역순 금지, 반환 경계는 READY 때 고정한다. 빈 목록은 items=[]인 최종 page1개로 pageCount=1이다. 양 서버가 0/밀리/마이크로/나노초·빈 목록·다중 page golden fixture를 공유한다.

snapshot 생성 멱등 키는 `(environment,authenticatedConsumer,consumerRecoveryGeneration,Idempotency-Key,scope=ALL_WITHDRAWN)`에 바인딩한다. body 없음은 유지한다. 재호출은 같은 run을 반환하고 BUILDING은202, READY/FAILED/EXPIRED는200 status로 응답한다(만료 page는410). FAILED/EXPIRED 재시작은 **새 key**이며 같은 key로 다른 run을 조용히 만들지 않는다. operation metadata는 생성 후120일 제안으로 rows7일과 별도 승인 대상이다. 최소 key digest/consumer/runId/status만 남기고 사용자 rows/contentDigest를 복제하지 않는다. 이 기간 뒤 동일 key 재사용은 새 operation일 수 있으므로 caller는 새 작업에 항상 새 UUID를 사용한다. snapshot-related user staging/중복 기록도7일 정리 대상이다.

#### D. Billing 완료 판정

ACK의 `contentDigest`는 §5.3 C의 item stream SHA-256과 동일하며 READY status 응답의 data manifest에 포함한다. 별도 manifest 전체 digest는 만들지 않는다. Identity는 digest equality와 별개로 저장된 snapshot의 consumer/환경/stream/generation/H/유효기간을 검증한다. rows hash가 이 metadata나 원격 Billing commit을 암호학적으로 증명한다고 간주하지 않는다.

각 page를 local Transaction으로 적용하고 page checkpoint를 함께 commit한다. 중복 page는 NOOP. 전 page/count/순서/digest 검증 뒤 마지막 **검증 완료 marker+local baseline H+coverage version**만 bounded Transaction으로 CAS 확정한다. 모든 row를 한 Transaction에 다시 넣지 않는다. 이것이 SNAPSHOT_APPLIED이며 source expiry 전에 끝내야 한다. user staging은7일 정리하지만 state baseline/consumer 증거는 별도 lifecycle로 보존한다.

**R1 baseline ACK:** 최초 BASELINE은 현재 recovery generation의 checkpoint 미생성에서만 허용한다. Identity는 authenticatedConsumer와 snapshot 소유/환경/stream/consumerRecoveryGeneration/READY/유효기간, contentDigest, throughSequence==H를 검증한다. snapshotId만 전달해 임의 jump를 허용하지 않는다. RESYNC는 §5.7의 새 generation을 먼저 발급한 뒤 expectedCheckpointVersion으로 명시 전환한다. 이전 generation의 checkpoint는 역사적 관측치일 뿐 복원본의 적용 증거가 아니므로 새 baseline H를 그 값으로 자동 올리지 않는다. 같은 generation 안의 checkpoint 감소는 거절한다. 동일 baseline 요청 재전송은 유효 manifest와 저장된 적용 증거가 일치하면204이며 baseline 상태를 되돌리지 않는다. snapshot 만료 뒤 검증 자료가 정리되었다면410 SNAPSHOT_EXPIRED이며 이미 저장한 checkpoint는 유지한다. ACK 유실 확인은 새 scan의 현재 checkpoint 대사 또는 새 snapshot/RESYNC로 수행한다. 명시 resync가 상태를 복구해도 과거 eventId/digest 수신 증거를 생성하지 않고 legacy PARTIAL/UNKNOWN은 유지한다. Identity는 Billing의 적용 ACK를 신뢰하는 경계이지 원격 commit을 직접 증명하는 서버가 아니다.

**R2 고정 scan:** 첫 feed 요청에서 majority 관측 lastSequence를 inclusive 목표H2로 고정하고 서명된 scanCursor에 environment/consumer/stream/consumerRecoveryGeneration/startExclusive/H2/issuedAt/expiresAt을 바인딩한다. cursor 유효기간은1시간 제안이다. 이후 모든 page는 같은 cursor를 사용하며 startExclusive<=afterSequence<=H2, 순번 오름차순 `(afterSequence,H2]`만 반환한다. after>H2는400이다. 요청 변경으로 목표를 늘리지 않으며 live 관측치는 완료 판정에 쓰지 않는다. nextAfterSequence와 scannedThrough는 반환된 마지막 **연속** 순번 또는 이미 H2인 경우 H2다. done은 scannedThrough==H2일 때만 true. after<H2인데 items가 비었거나 다음 순번이 없으면 replay floor/interval을 확인해410 또는409 GAP, 조사 중503이지 done=true가 아니다. cleanup과 scan이 경합해 필요한 자료가 사라져도 같은 규칙이다. cursor 만료 시 마지막 local checkpoint부터 새 목표로 scan을 열고 기존 작업을 완료했다고 표시하지 않는다.

**FEED ACK:** local page 적용+연속 checkpoint commit 후 보내며 scanCursor의 consumer/stream/consumerRecoveryGeneration/유효성/허용 범위를 검증한다. 정상 동일·낮은 throughSequence는204 NOOP, 높으면 이미 확인된 baseline 이후 연속 적용에 대한 consumer attestation으로 전진한다. 위조 cursor/다른 consumer/범위초과는 감소 ACK라도 거절한다. 지연되어 cursor가 만료되면 새 scan으로 현재 checkpoint를 대사한다. Identity는 단순 응답 발급 사실만으로 실제 적용을 증명했다고 하지 않는다. push 선행 NOOP도 feed checkpoint는 페이지 순서대로 전진한다. H2 도달 시 해당 scan만 CAUGHT_UP, H2 뒤 신규 자료는 다음 scan으로 처리한다.

**R3 증거 보존:** baseline/checkpoint metadata에는 userId/event 목록 없이 consumer/stream/consumerRecoveryGeneration/H/throughSequence/runId/version/검증결과만 둔다. 연결 가능한 contentDigest와 rows는 snapshot7일 뒤 제거하고 원 event 비교에 재사용하지 않는다. 운영 stream 유지 동안 현재 checkpoint와 필요한 baseline 증거를 보존하고 폐기 stream metadata는 종료 후120일 제안으로 별도 승인 대상이다. 정상 source cleanup은 이 적용 증거를 무효화하지 않지만 restore로 증거가 유실되면 UNKNOWN으로 시작한다.

최종 coverage 결과는 `{legacy:COMPLETE|PARTIAL|UNKNOWN, stream:UNKNOWN|CAUGHT_UP|LAGGING|GAP, throughSequence, assessedAt}`다. **legacy COMPLETE**는 고정 snapshot scan 성공에 더해 authoritative User history가 필요한 과거 범위에서 삭제/유실되지 않았다는 운영 증거와 gap 부재가 필요하다. 증거가 없으면 PARTIAL/UNKNOWN이며 tombstone 전체를 읽었다는 이유로 승격하지 않는다. 향후 새 commit은 정상 lag일 수 있고 이를 이미 확인된 H2 범위의 gap과 혼동하지 않는다.

모든 과거 coverage가 필요하지 않은 별도 cohort 활성화는 영향 분석/승인 후에만 가능하다. 초기 구현에서 gap 사용자를 추정으로 건너뛰어 전역 purge를 켜지 않는다.

### 5.4 실패·중단·재개 exact 권장값

| 항목 | Billing 목적지 권장 규격 |
| --- | --- |
| ACK |204만 DELIVERED. 다른2xx는 CONTRACT_ERROR로 dead-letter |
| timeout |connect3초, 전체 request5초; redirect NEVER |
| 실행 |poll5초, batch20, lease60초, leaseVersion CAS; request 중 lease 유효성 유지 |
| retry |408/425/429/5xx·network timeout. 지수 상한 min(3600초,5초×2^(attempt-1)), full jitter, 최소1초 |
| Retry-After |429/503에서 정수초와 RFC HTTP-date 지원. 유효 미래값은 backoff보다 이른 retry 금지 |
| 긴 Retry-After |24시간 초과면 BLOCKED_RETRY_AFTER+운영 경보, notBefore 유지; 값을300초로 잘라 조기 재전송하지 않음 |
| 잘못된 헤더 |음수/overflow/parse 실패는 reason 기록 후 기본 backoff. 과거 HTTP-date는 추가 지연0. 값/payload 전문 비로깅 |
| 횟수 |자동 request 최대20회 후 DEAD_LETTER.24시간 미전달 경보는 횟수와 독립 |
| 인증 오류 |401/403 즉시 BLOCKED_AUTH, 자동 busy retry 금지. 설정/권한 확인 후 승인된 재개 |
| 영구 오류 |400/404/405/409/422/다른2xx/redirect는 DEAD_LETTER와 분류. payload를 고쳐 같은 ID 재전송 금지 |
| 중단 |publisher OFF는 새 lease 획득만 중단. 진행 요청은 bounded 종료/결과 CAS. capture/feed 보존은 계속 |
| 재개 |원 eventId/payload 유지. 재개 작업별 resumeGeneration을 증가시키고 자동20회 budget을 새로 부여, lifetime 시도 횟수 보존 |

동일 payload를 처리할 수 없는 계약 수정은 reader-first 배포/합의 후 재개한다. event 충돌은 임의 ID 재발급으로 해결하지 않는다. stale lease 결과는 delivered/재시도 상태를 덮어쓰지 않는다. API 호출 성공 뒤 lease 소실은 다음 시도에서 duplicate204로 수렴한다.

추가 분류:413과 위에서 retry/auth로 명시하지 않은 모든4xx는 DEAD_LETTER다. 최종1xx/범위 밖 status·파싱 불가능한 HTTP는 PROTOCOL_ERROR로 자동20회 budget 내 retry하고 경보한다. redirect3xx는 따라가지 않고 DEAD_LETTER다. Retry-After가 여러 header value로 오면(HTTP-date 내부 쉼표는 분리하지 않음) AMBIGUOUS_RETRY_AFTER로 차단/담당 검토한다. 정수는 부호 없는 decimal 초,0은 추가 지연0이며 기본 최소1초 backoff는 유지한다. HTTP-date는 수신 시각의 UTC clock 기준으로 계산하고 clock 오차 gate60초를 넘으면 CLOCK_UNTRUSTED로 차단한다. 유효 notBefore는 `max(기본 backoff 종료, header시각)`이며24시간 초과는 자동 재개하지 않는다. parse/overflow 실패는 표의 기본 backoff 규칙을 따른다.

전송 직전 leaseVersion/resumeGeneration CAS로 generationAttemptCount와 lifetimeAttemptCount를 증가시키고 durable dispatch marker를 남긴다. transport 호출 시도/응답 유실/unknown outcome은1회로 센다. lease 획득만으로는 증가하지 않는다. marker 직후 crash로 실제 전송 여부를 증명하지 못해도 보수적으로1회를 소비하며 횟수를 되돌리지 않는다. generation의20번째 실패 후 DEAD_LETTER,20번째204는 성공이다. resumeGeneration 변경은 원천 유효/권한 승인·notBefore 검증 후에만 가능하며 expiry 이후에는 새 event를 꾸며 재개하지 않고 replay-unavailable/coverage 절차로 넘긴다.

운영 재개는 별도 권한과 사유/설정 버전/건수만 audit한다. 대량 전건 reset 버튼으로 원 실패를 지우지 않는다. BLOCKED_AUTH/DEAD_LETTER 최초 검토는7일 이내, 원천 만료 경보는 별도로 적용한다. 기존 LC publisher의 timeout/retry/2xx 정책은 유지하며 공유 transport의 확장이 LC 동작을 바꾸지 않는지 회귀 테스트한다.

### 5.5 단계별 활성화·rollback gate

| 단계 | capture | Billing publisher | purchase 신규 발급 | 실제 purge |
| --- | --- | --- | --- | --- |
| 개발/계약 미합의 |운영 변경 없음|OFF|기존 상태|OFF|
| capture cutover 완료 |ON 필수|OFF|OFF|OFF|
| consumer 및 staging 검증 |ON|환경별 단계 ON|OFF|OFF|
| snapshot/feed·token gate 완료 |ON|ON|승인 후 ON|OFF|
| provider 부재 증명/보존/복원 검증 완료 |ON|ON|별도 판매 gate|별도 승인 후 ON|

rollback은 purchase 추가 발급/publisher를 독립 OFF 할 수 있지만 capture 호환 writer·미전달 journal·Billing known-withdrawn 차단은 유지한다. 이미 발급한 token은 새 설정으로 소급 없어지지 않는다. schema additive reader-first 적용 후 old writer 재투입 금지. 발급 TTL 확대나 capture 보존 변경은 gate 재검토 사유다.

추가 gate: control migration 대상의 누락/불일치0과 기존 보호상태 보존, 모든 발급/replay·탈퇴 writer의 동일 fence, capturedAt 관측 기산 승인/clock·commit budget 검증, snapshot Mongo 기능 검증을 통과해야 한다. fence OFF+purchase ON, capture 미지원 writer 혼합, 승인되지 않은 metadata 보존 조합은 활성화 불가다.

### 5.6 Mongo durability·복원 stream 계약

- 원천 capture/control 발급/탈퇴 Transaction은 readConcern=snapshot, writeConcern={w:majority,j:true} commit을 요구한다. journal counter·User·delivery는 같은 session/Transaction이며 counter 먼저 commit 후 insert하는 구현 금지다. 지원 불가 설정의 신규 instance는 startup 실패로 처리한다. 실행 중 결제 전용 준비 상태와 전체 인증 health의 영향 구분은 §5.1 D를 따른다.
- feed/ACK 판정은 majority-committed 읽기와 majority+j 쓰기를 사용한다. 고정 snapshot은 지원되는 snapshot read의 동일 atClusterTime T로 User와 H를 읽으며 다른 T나 local read로 fallback하지 않는다. 실제 driver API/서버 최소버전·snapshot history5분 gate는 staging에서 검증한다.
- driver bounded commit retry 이후 UnknownTransactionCommitResult는 rollback으로 간주하지 않는다. 기존 transaction 결과가 확정되지 않았는데 counter를 새 별도 작업으로 재발급하지 않는다. request retry는 기존 eventId unique/동일 탈퇴 사실로 수렴한다. failover 테스트에서 counter/journal 간 구멍·중복이 생기면 source gate 실패다.
- streamId는 환경별 **incarnation UUID**다. 정상 majority failover는 incarnation을 유지하지만 backup restore/sequence rewind 가능 복원은 기존 stream에 새 쓰기를 금지한다. restore barrier에서 구stream을 CLOSED_RESTORE로 표시하고 새 streamId/sequence0으로 capture를 재개한 뒤 새 snapshot과 coverage 평가를 한다. 과거 source 최대 sequence를 복원본 counter만 보고 추정하지 않는다.
- Billing은 streamId가 다르면 숫자 sequence끼리 비교하거나 ACK를 이전하지 않는다. old cursor는409 STREAM_REPLACED, 새 baseline/RESYNC 절차는 새 stream checkpoint를 만든다. 원 eventId를 복원해 다시 전달할 경우 기존 wire 그대로 유지하며 기존 inbox가 있으면 중복 수렴한다. 복원으로 source 또는 consumer 증거가 소실된 범위는 UNKNOWN/GAP다. 새 stream 생성만으로 legacy COMPLETE가 되지 않는다.
- 두 서버 복원 후 known-withdrawn 차단/coverage·token drain/purge 대상 재대사 전에 사용자 트래픽/자동 삭제를 연결하지 않는다. 최대35일 backup 보존은 source120일·snapshot7일의 대체 증거가 아니다.

### 5.7 consumer 복원·RESYNC generation

이 절은 §5.3/5.6과 함께 적용하는 규격이며 아래 schema 부록보다 우선한다.

1. **발급 authority:** Identity가 `(environment,consumer,streamId)`별 현재 `consumerRecoveryGeneration`을 관리한다. 값은 재사용하지 않는 lowercase UUIDv4이며 보통 ACK마다 증가하는 checkpoint version, publisher resumeGeneration, source streamId와 서로 다른 식별자다. INITIAL·Billing-only restore·RESYNC마다 별도 운영 role의 recoveries 요청으로 새 값을 발급한다. 일반 worker는 세대를 만들거나 변경할 권한이 없다.
2. **복구 시작:** Billing의 복구 관련 writer/worker·ACK 전송과 해당 purge를 정지하고 진행 Transaction을 drain한다. 복원 시 구프로세스의 DB 연결/쓰기 접근을 제거하고 복원 대상 DB를 격리한다. 트래픽 차단만으로 drain 완료라고 하지 않는다. 이어 recoveries의 expectedGeneration CAS로 Identity 세대를 교체하고 coverage를 UNKNOWN/recoveryState=RECOVERING으로 설정한다. 이전 적용 증거는 새 세대의 증거로 복사하지 않는다.
3. **멱등성과 crash:** recoveries operation은 환경/consumer/stream/Idempotency-Key와 canonical body에 귀속한다. 같은 key/같은 요청은 같은 generation을 반환, 다른 요청은409이다. 응답 유실 시 같은 key로 재조회하며 expectedGeneration 충돌을 새 세대 자동 발급으로 우회하지 않는다. 이후 더 새 세대가 발급되었으면 옛 operation 결과로 재설치하지 않는다. operation 최소 metadata 보존은 이미 제안한120일 범위에 포함해 별도 승인받는다.
4. **local 설치:** 복구 담당자가 Identity 현재 세대를 확인해 Billing 복원본의 durable recovery-control에 설치한다. 이때 local coverage=UNKNOWN, checkpoint는 새 세대 미생성으로 초기화한다. Identity 발급 이후 local 설치 실패 시 worker/purge는 계속 OFF이며 같은 operation으로 복구한다. 작업자가 checkpoint/status를 읽었다는 이유만으로 자기 세대를 자동 갱신할 수 없다.
5. **old worker 차단:** snapshot/scan/queue job/ACK outbox에 generation을 기록한다. page 적용·중복 처리 결과·검증 marker·local checkpoint·ACK enqueue를 쓰는 Transaction은 동일 local recovery-control의 current generation을 exact CAS **쓰기**로 확인한다. 설치와 같은 control을 경합하므로 읽기만 하는 검사는 불충분하다. 오래된 작업은 폐기/재스케줄하며 데이터·증거를 갱신하지 않는다. stale ACK는 Identity에서 낮은 순번이라도409 RECOVERY_GENERATION_MISMATCH다. 새 세대 header만 붙여 옛 payload를 재전송하지 않는다.
6. **remote CAS:** Identity의 snapshot READY 확정/scan 발급/baseline·FEED ACK commit도 현재 generation과 같은 제어 행을 CAS한다. ACK 처리 도중 새 복구가 시작되면 이전 세대 ACK commit이 충돌해야 한다. 동시에 시작한 복구는 expectedGeneration CAS로 하나만 이기며 다른 요청은409다. snapshot·page cursor와 고정 scanCursor에 generation을 바인딩하고 현재 세대와 다른 자료는 적용하지 않는다.
7. **복구 완료:** 새 snapshot 전 page/digest 검증 marker+local baseline이 새 세대로 commit된 뒤 BASELINE/RESYNC ACK를 보낸다. 이 ACK만 recoveryState를 BASELINED로 바꿀 수 있다. 그 전 FEED ACK는409 RECOVERY_NOT_READY다. 새 세대 고정 H2까지 연속 적용/ACK하면 CAUGHT_UP이다. legacy=PARTIAL/UNKNOWN은 그대로 남을 수 있으며 CAUGHT_UP이 과거 전체 COMPLETE를 뜻하지 않는다. old ACK로 UNKNOWN을 해제하지 않는다.
8. **push·복원 경계:** 기존4필드 push wire에는 generation을 추가하지 않는다. push consumer의 local 적용/증거 쓰기도 설치된 recovery-control을 통과해야 하며 복구 barrier 중은503으로 재시도시킨다. push204만으로 새 세대 snapshot/feed 완료를 선언하지 않는다. Identity까지 복원되면 source §5.6의 새 stream과 새 consumer generation을 함께 시작해 복원된 옛 UUID를 현재 값으로 재사용하지 않는다.

상태 이름은 다음처럼 분리한다. source snapshot run은 BUILDING/READY/FAILED/EXPIRED, Billing local snapshot은 SNAPSHOT_APPLIED, consumer recoveryState는 RECOVERING/BASELINED/CAUGHT_UP, coverage.legacy는 COMPLETE/PARTIAL/UNKNOWN, coverage.stream은 UNKNOWN/CAUGHT_UP/LAGGING/GAP이다. 과거 설명의 SCANNED/LEGACY_PARTIAL을 새 wire enum으로 추가하지 않는다. 새 generation 시작과 증거 소실은 coverage.stream=UNKNOWN이다.

일반 ACK마다 세대를 바꾸지 않으며 세대 metadata는 기존 제안한 current stream 최소 증거/폐기 stream120일 보존 범위를 따른다. 이 절은 새로운 영구 개인정보 보존 승인이 아니다.

## 6. 부록 — schema, 검증, 담당별 완료 기준

### 6.1 제안 schema/index manifest

TMI-199 후속 [exact wire·index 수락안](TMI-199-EXACT-WIRE-AND-INDEX-SPEC.md)에 API 필수/null/상태별 생략, numeric 경계와 Identity13개 물리 manifest를 구체화했다. 사용자 승인 방향을 따르는 세부안이며 해당 버전의 Identity 수락 전 공동 동결로 간주하지 않는다.

| Identity collection | unique/index | 요구 |
| --- | --- | --- |
| withdrawal_stream |unique(environment,streamId); 환경별 active stream 제약 |incarnation,lastSequence,captureCutover,replayFloor,unavailableIntervals,status; counter business TTL 없음 |
| withdrawal_journal |unique(streamId,sequence), unique(eventId); (capturedAt,_id) |원자 capture, 명시 cleanup/gap |
| withdrawal_deliveries |unique(eventId,destination); (destination,status,nextAttemptAt,_id) |resumeGeneration/lifetimeAttemptCount/generationAttemptCount/notBefore/leaseVersion·ACK/retry·payload 비복제 |
| withdrawal_snapshot_runs |unique(environment,consumer,consumerRecoveryGeneration,requestOperationId); unique(snapshotId) |scope binding·불변 data manifest와 mutable metadata 분리, FAILED/EXPIRED 동일 key는 같은 run |
| withdrawal_snapshot_rows |unique(snapshotId,userId) |bounded 임시 개인정보, 명시 purge |
| withdrawal_consumer_checkpoints |unique(streamId,consumer,consumerRecoveryGeneration) |version,verifiedStateBaseline,verifiedFeedCheckpoint,coverageGap; current 적용 증거와 replay availability 분리 |
| withdrawal_consumer_recovery_controls |unique(environment,streamId,consumer) |current generation·version·recoveryState, 모든 ACK/세대 전환의 공통 CAS |
| withdrawal_recovery_operations |unique(environment,consumer,streamId,operationId) |canonical 요청 바인딩·동일 세대 재응답,120일 metadata 제안 |

물리 index 이름/key order/partial filter는 Identity 기존 schema initializer와 합의해야 한다. 동작 요구를 @Indexed 자동 생성으로 대체하지 않는다. Billing snapshot inbox/checkpoint도 additive subset이며 무료 inbox와 혼합하지 않는다. 원천120일·snapshot7일 승인 전 production schema/삭제 job을 활성화하지 않는다.

### 6.2 최소 테스트 matrix

| 영역 | 필수 사례 |
| --- | --- |
| scope |Guest default/explicit purchase 제거; ACTIVE MEMBER만 합성; 비활성 신규 token 거절; 기존 read/aud/type 유지 |
| 발급 fence |발급 먼저/탈퇴 먼저 commit, sign 뒤 rollback, unknown commit, transaction retry, signup 후 탈퇴 |
| replay |탈퇴 전후 epoch, 암호화 응답 replay, exp/iat 불변, 지연 응답이 만료되면 성공 반환 안 함 |
| capture |publisher OFF 저장, LC 성공 후 정리와 독립, counter rollback/동시 commit, 구버전 writer 차단 |
| snapshot |T에 보이는 tombstone·H 읽기 일관성, 과거 withdrawnAt의 늦은 commit은 seq>H, 동일 timestamp/페이지 중복 |
| 완료 |부분 page·digest 불일치·history expired·7일 만료는 COMPLETE 불가; rows 전부 읽어도 legacy 증거 없으면 PARTIAL |
| feed |push 선행/응답 유실/연속 gap/만료 floor/ACK 유실·감소·범위초과; H2 이후 정상 lag |
| retry |204-only,401/403, HTTP-date/긴 Retry-After,20회 초과, stale lease, 수동 재개와 원 ID 유지 |
| 개인정보 |원천 만료 전 경보·gap 후 purge, 임시 snapshot 사본 정리, 로그 raw/user/token 비포함 |
| 운영 |staging SigV4 route/환경/role·direct 우회 실패, capture 유지 rollback, legacy token drain·복원 후 coverage 검증 |

개정 필수 회귀: H>0 최초 BASELINE·CAS RESYNC·잘못된 consumer/snapshot·만료 후 ACK 유실 확인, 낮은 정상 ACK204와 위조 거절; H2 뒤 신규 commit/빈 중간 page/cleanup 경합; 정상 source cleanup 뒤 적용 coverage 유지 및 증거 유실 복원 시 UNKNOWN; 신규/legacy control·동시 탈퇴·보호필드 보존; 일반 로그인 불명503·expired replay401; capturedAt과 commit 차이/지연 bound; majority failover·새 incarnation 복원; canonical empty/nanosecond/byte length/page 독립 golden fixture;20번째 성공/실패·dispatch marker crash·중복 Retry-After/overflow/413; snapshot FAILED/EXPIRED 동일 key와 metadata 보존.

fake provider/로컬 replica-set 테스트로 검증하고 운영 사용자/credential을 fixture로 사용하지 않는다. 실제 Lattice/운영 Mongo 기능 검증은 별도 승인 gate다.

추가 개정 테스트: contentDigest 응답/ACK golden fixture와 metadata 위조 거절; source stream 유지 상태의 Billing-only restore·RESYNC/old ACK 경합; 동일·낮은 ACK도 old generation이면409; local old worker/page/queue/ACK enqueue CAS 실패; recovery 응답 유실/동시 begin/local 설치 실패; 새 marker 이전 FEED ACK 거절·UNKNOWN 유지; Identity+Billing 복원 새 stream/세대; 잘못된 신규 배포 startup 실패와 runtime purchase-only gate가 기존 로그인 health를 불필요하게 내리지 않는지 검증한다.

### 6.3 담당별 산출물

- Identity: 공통 issuer/control의 구현 가능성·전체 진입점 목록, 원천 최소 보존 승인안, capture 이관 측정, snapshot Mongo/driver 기능 검증, source history coverage, 목적지 retry/재개 tests.
- Billing:204 consumer/semantic snapshot 수렴·checkpoint Transaction, known-withdrawn account fence·진행 요청 deadline, 원천 gap 시 purge 차단, provider 대사·예외 보존 검증.
- 공동: exact route/auth/DTO/errors와 snapshot manifest, 운영 TTL/clock 증빙, 법적 보존 판단이 필요한 항목·담당자, staging 및 rollback 완료 보고. 서명/commit/응답의 의미를 같은 용어로 사용한다.

### 6.4 근거 및 검증 범위

현행 source 사실은 [인계 회신 검토](IDENTITY-PAYMENT-HANDOFF-REVIEW-2026-10-07.md#6-부록--근거와-검증-범위)에 연결했다. 이번에는 [Identity issuer](../../../identity/src/main/java/web/tosunsaeng/identity/global/security/jwt/JwtAccessTokenIssuer.java)와 [reissue recovery](../../../identity/src/main/java/web/tosunsaeng/identity/domain/auth/session/application/ReissueRecoveryService.java)의 로컬 source를 추가 대조했다. 이는 운영 배포/새 알고리즘 검증이 아니다. 본 문서의 신규 sequence/snapshot/fence 규격은 제안이며 아직 구현하지 않았다.

문서 diff/링크 검증만 수행한다. 코드/타 서버/실제 보존/외부 계정 변경 없음. 합의 뒤 ADR/각 PLAN 동기화와 Jira·구현 승인이 필요하다.
