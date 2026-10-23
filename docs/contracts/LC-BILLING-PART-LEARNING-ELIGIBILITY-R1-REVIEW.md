# 파트별 학습 유료권 조회 R1 — Billing 검토 회신

- 날짜: 2026-10-23 / 브랜치: feat/TMI-223-referral-code-rewards
- 대상: LC `lc-billing-part-learning-eligibility-recommendation-r1`
- 범위: LC 문서 및 Billing 구현 정적 대조. 제품 구현·배포·공동 최종 동결 아님.

## 1. 5줄 결론

1. 별도 유료권 전용 route/audience, 무료권 제외, 무차감·무캐시 방향은 사용자 승인 정책과 일치한다.
2. 기존 PaidEntitlementReader를 재사용하되 기존 재답변의 무료권 predicate는 사용하지 않는다.
3. 복수 ACTIVE 중 최대 종료 선택 대신 기존 단일 ACTIVE 및 기간 중첩 정합성 검사를 유지한다.
4. 미래 checkedAt 1초 허용 후보는 추가하지 않고 기존 draft-5의 엄격한 시각 검사를 재사용한다.
5. 아래 exact 값과 설명을 LC에 반영한 뒤 공동 규격 확정·구현·검증을 진행한다. L01~L12는 아직 실행 결과가 아니다.

## 2. 수용 가능한 범위

POST /internal/v1/entitlements/part-learning-eligibility 및 전용 audience `tosunsaeng-billing:part-learning-eligibility:{env}` 확장. 기존 키/issuer/sub/환경값을 재사용하며 새 키나 인증 시스템은 만들지 않는다. 현재 WorkloadIngressFilter에는 이 route가 없으므로 신규 인가/기본 OFF flag 및 배포 시 내부 경로 전달 설정은 필요하다. 기존 재답변 route/flag/fixture는 유지한다.

요청은 검증된 userId 단일 필드의 lowercase canonical UUID이며 v4 제한을 추가하지 않는다. 응답은 userId/decision/reason/checkedAt/validUntil의 다섯 필드, wrapper 없는200이다. LC 제안 네 조합(ALLOWED/ACTIVE_PAID_ENTITLEMENT, DENIED/PAID_ENTITLEMENT_REQUIRED, DENIED/ACCOUNT_WITHDRAWN, PENDING/ENTITLEMENT_STATE_UNCONFIRMED)을 신규 route 규격으로 채택하는 데 이견 없다. 비허용 validUntil은 null이다.

유료 콘텐츠 제공(Part1 포함)과 신규 submit에만 조회한다. 첫 무료 세트·원문 없는 목록/진도·기존 결과/polling·준비만 하는 create·사용자 녹음 upload는 조회하지 않는다. 실제 접수 근거가 있는 replay/retry는 기존 소유권·삭제·탈퇴 검사 아래 재현한다. 실패 상태만으로 접수 증거를 추정하지 않는다. LC 세부 연결/파일 freeze 복구는 LC 구현 테스트로 검증한다.

## 3. 후보 규격에 대한 Billing 답변

| 항목 | Billing 확인/권장 확정값 |
| --- | --- |
| 요청/응답 상한 | 각각 최대16KiB(16,384 bytes). 요청 초과는400 INVALID_REQUEST. 응답 상한은 LC 수신 검사에도 적용 |
| strict body | 기존 ReanswerRequestDecoder의 UTF-8/duplicate/unknown/trailing/type/canonical UUID 검사 재사용 |
| 시각 | UTC Z, 소수부0~9자리, Instant 정밀도 유지 |
| 허용 기한 | min(checkedAt+5초, 현재 단일 ACTIVE slot의 activeEndsAt). continuousAccessEndsAt/미래 scheduled 종료를 사용하지 않음 |
| 복수 ACTIVE | 기존 reader는 기간 중첩을 정합성 실패로 처리. 최대 endsAt을 골라 정상으로 숨기지 않음; 현행 저장소/정합성 실패503 유지 |
| 미래 checkedAt | 허용오차1초 추가 없음. checkedAt<=receivedAt<validUntil, receivedAt<=guardNow<validUntil 유지 |
| 경과시간 | 원격 호출 직전 monotonic 시작, 수신/사용 시 모두0<=elapsed<5초. 같은 요청 안에서 시계를 초기화하거나 자동 재조회하지 않음 |
| 시각 오류 | 단순 만료/지연은503, 미래 checkedAt·시계 역행·손상/모순 시간은500+운영 경보. JWT skew를 적용하지 않음 |
| timeout | 연결2초/요청5초·자동재시도0 제안 수용. LC 실제 client가 총 요청시간을 제한하는지는 구현 검증 대상 |
| 캐시 | 요청 간 없음. HTTP Cache-Control:no-store. 구매 후 다음 요청도 새 조회, callback만으로 허용하지 않음 |

유료 판정은 PaidEntitlementReader의 recovery/readiness·구매/기간권/원장 관련 기존 정합성 및 MEMBER evidence·현재 lifecycle 검사를 유지한다. active=true와 무관한 단순 hasPendingSync는 차단 조건이 아니다. 검증된 귀속 충돌/미처리 refund 증거 등 기존 reader가 불명으로 판정하는 사유를 무시한다는 뜻은 아니다. 무료 컬렉션 조회/예상 trial 발급 판정은 이 신규 endpoint에 필요 없다.

LC §4의 “Mongo 업무 Transaction 밖에서 조회”는 **LC가 원격 Billing HTTP를 자신의 쓰기 Transaction 밖에서 호출**한다는 뜻으로 명확히 한다. Billing 내부는 기존 EntitlementSnapshotExecutor의 read-only Mongo snapshot Transaction을 사용한다. checkedAt은 snapshot 시작 전에 고정하며 snapshot 재시도로 연장하지 않는다. DB 읽기 Transaction은 발급/예약/차감 쓰기가 아니다.

## 4. 오류 exact 재사용 기준

기준 문서: `lc-billing-feedback-reanswer-eligibility-r1-draft-5`, [원문](LC-BILLING-FEEDBACK-REANSWER-ELIGIBILITY-R1-DRAFT.md) §8/9/13.
이번 확인 SHA-256: `51fd0fb8fa43cffcf812fbaae717a43b04c1dfe937dcd30958b5750a819d746e`.
이 hash는 읽은 문서 식별이며 신규 계약/fixture 전체 동결 증거가 아니다.

내부 오류는 code/message/retryable/correlationId의 기존4필드 JSON:

- 400 INVALID_REQUEST / `The request is invalid.` / false.
- 503 BILLING_TEMPORARILY_UNAVAILABLE / `Billing is temporarily unavailable.` / true, Retry-After:1.
- correlationId는 응답별 lowercase UUID v4. 인증 경계 오류는 기존처럼 빈 body이며 오류 JSON으로 강제 파싱하지 않는다.
- OFF/잘못된 local port 빈404, 활성 경계 인증/query 실패 빈401, 인증된 목적/method 인가 실패 빈403 등 기존 ingress 우선순위를 재사용한다.

LC 매핑: 정상 부족402 ENTITLEMENT_INSUFFICIENT, 정상 탈퇴401 ACCOUNT_WITHDRAWN, PENDING/timeout/일시 장애/유효시간 소진503 BILLING_TEMPORARILY_UNAVAILABLE, **내부400도 포함하여** 인증·경로·계약 오류500 COMMON500+운영 경보(Retry-After 없음). 앱 자체400/401/403은 유지한다. 내부401을 사용자 토큰 문제로 전달하지 않는다.

## 5. 검증과 다음 작업

L01~L12 범위에 이견 없다. L04에 유료 기간 중첩 정합성 실패·현재 ACTIVE 종료와 후속 scheduled 시작 경계를, L05에 미래 checkedAt 무허용/monotonic 경계·역행을, L07/L08에 내부400→500과 OFF/다른 port/교차목적 인가를 명시한다. 증거 없는 실패 Job 재시도에 신규 조회를 우회하지 않는 사례도 L02/L11에서 확인한다.

이미 제공한 음성 URL의 만료는5초와 별개이며 회수 시스템을 추가하지 않는다. 조회/제공/접수 사이 짧은 경합을 수용하고 새 승인 receipt/컬렉션/worker/환불 이벤트를 만들지 않는다. 이것이 기존 lifecycle 보호를 제거하는 것은 아니다.

LC에 이 보완을 반영한 단일 wire revision으로 맞춘 후 양쪽 구현·독립 테스트·연동 검증을 진행한다. 신규 route 실제 접근/flag/시계 동기화는 배포 단계에서 확인한다. 이번은 문서만 변경하며 코드·DB·환경·LC/Identity·기존 fixture·Jira·commit/push·배포는 변경하지 않았다. 정적 대조와 git diff --check만 수행, 테스트는 구현 변경이 없어 재실행하지 않았다. 기존 dirty 파일은 보존했다.

## 6. 2026-10-23 review-2 최종 규격 확인 — Billing 측 수락

LC revision `lc-billing-part-learning-eligibility-recommendation-r1-review-2` 원문을 재대조했다. 앞선 보완4개와 검증 목록이 모두 반영됐으며 추가 수정 요청은 없다. Billing은 이 원문의 route/audience·네 응답 조합·유료권 전용 판정·무캐시·16KiB·오류·시각 규격 및 LC 연결 책임 구분을 구현 기준으로 수락한다. §3의 reason/시각 제안 표현도 Billing 측에서는 기재된 값 그대로 수락하며 추가 제품 정책 선택은 없다.

- [수락 대상 원문 snapshot](LC-BILLING-PART-LEARNING-ELIGIBILITY-R1-REVIEW-2-SNAPSHOT.md): LC 파일 bytes 그대로 보관.
- 원문 SHA-256: `c27b981a1c1ec6f208089a1e3eae02f8f2bcefdaa3196d82fcc35dc70a545b99`.
- 참조 draft-5 hash도 기존 기록과 일치함을 재확인했다.
- 원문 상태 문구는 작성 당시 상태로 보존한다. 이번 기록은 Billing 측 규격 수락이며 LC를 대신한 수락·양측 테스트 통과·배포 활성화 선언이 아니다.
- 사용자 전달 LC 반영 보고와 이번 Billing 확인으로 기술 보완 의견은 해소됐다. LC는 위 revision/hash를 동일 구현 기준으로 확인하면 되며 새 정책/검토 체계를 추가하지 않는다. 제품 구현 착수 요청을 받으면 이 기준으로 신규 기본 OFF 조회를 구현한다.
- L01~L12 및 실제 HTTP/Transaction/경합/실환경은 아직 미실행. 구현 시 검증하며 배포/활성화는 별도다. 이번은 snapshot/회신/CURRENT_STATE/WORKLOG만 변경, 제품·DB·LC·fixture·commit/push 변경 없음.
