# Billing 공동 테스트 환경 검증 체크리스트

- 작성일: 2026-10-13
- 요청: 「그럼 이건 한번에 다른 거까지 검증하자」
- 상태: 공동 검증 범위 정리. 아래 실환경 항목은 미실행이며 배포·활성화 승인을 뜻하지 않는다.
- 기준: 각 최신 승인 계약/ADR. 이 문서는 인증·업무 계약을 변경하지 않는다.

## 1. 한 번의 테스트 환경 검증에 묶을 범위

| 묶음 | 확인할 동작 | 성공 기준 |
| --- | --- | --- |
| 연결·배포 구성 | 실제 DNS/TLS/ALB/SG/connector, 환경·키 일치, 수신 준비 후 발신 ON | 승인된 경로로만 도달, LC 공개 kid/n/e와 Billing trust 일치, Identity/LC 신뢰 분리 |
| JWT 보안 | HTTP27 실환경 재현, unsigned/만료/잘못된 환경·audience·서비스, query/빈 ?, OFF/다른 포트/direct 우회 | 계약의 빈 401/403/404와 no-store, 인증 실패 시 업무 처리 없음. 소켓 미기동은 transport 실패로 기록 |
| 무료 시험 | eligibility 수신 → 최초 reserve → LC Session commit → confirm → AttemptGroup 상태 | Claim/grant/consumption 각 1개, 상태·원장 일치, 기존 응답 계약 유지 |
| 응답 유실·재시도 | 같은 operation/event/body 재전송, HTTP 시도별 fresh JWT, status-first reconciliation | 동일 Session/Reservation/소비로 수렴, 중복 지급·이중 차감 없음 |
| 취소·만료·재응시 | cancel/5분 expiry, confirm 경합, replacement와 phone continuation | Claim 재개방 없음, terminal 불변, 기존 consumption 재사용, authoritative continuation exact echo |
| Identity 이벤트·복구 | 승인된 네 이벤트/네 복구 POST의 JWT, duplicate/conflict/revision 및 누락 대사 | inbox/projection 수렴, 구 IAM/SigV4 우회 차단, LC/user 인증과 격리. 양측 구현·설정 준비 후 실행 |
| 사용자 조회·구매 계정 기반 | GET /api/v1/entitlements, 구현된 products/purchase-account, Guest/MEMBER·scope·본인 귀속 | 조회가 발급/소비하지 않음, Guest 구매 거절, stable reference, 오류/PENDING 구분. 실제 지급 검증과 구분 |
| 탈퇴·owner lifecycle | 탈퇴 후 신규 구매 계정 차단, phone/merge 이전·GRADING pending·COMPLETED NOOP | lifecycle/owner fence 유지, 무료권 중복 지급·유료권 자동 이전 없음 |
| 운영 관측 | 민감정보 비로깅, 실패 집계·경보, 진행 중 saga/outbox·legacy 발신 inventory | 토큰/개인정보 원문 없음, 실패 원인 추적 가능, 진행 중 증거 보존 |

HTTP fixture의 일부 정상 body는 인증 경계용이다. 업무 E2E에는 별도 유효 body와 격리된 테스트 계정을 사용한다. 프록시가 빈 query를 제거하면 애플리케이션만으로 검출할 수 없으므로 실제 경로에서 관측한다. 실환경의 업무 무호출은 요청별 비식별 추적 및 상태/원장 변화 없음으로 확인하며 단순 HTTP 코드만으로 주장하지 않는다.

## 2. 실행 전 준비와 순서

1. 대상이 실제 사용자 트래픽 없는 격리 환경인지 확인한다. 이름에 staging이 있다고 안전한 테스트 환경으로 간주하지 않는다.
2. Identity/LC/Billing의 정확한 배포 revision, 승인 명세/fixture hash, enabled flags, 환경과 키의 공개 식별정보를 기록한다. private JWK/실제 token/사용자 개인정보는 기록하지 않는다.
3. 양측 구현 상태·Mongo schema/index/replica set·테스트 계정과 업무 데이터·경보·미완료 saga/outbox·legacy 발신 사용처를 확인한다. 준비되지 않은 묶음은 BLOCKED로 기록한다.
4. 배포 및 테스트 환경 활성화 범위를 별도 확정한 뒤 OFF 구성을 배포한다. 둘 다 receiver OFF인 공유 TLS는 connector 미기동이 정상이다.
5. Billing 수신 준비를 확인한 뒤 해당 발신을 켠다. Identity 방향도 수신 준비를 확인한 뒤 발신을 켜며 사용자 공개 경로는 별도 준비 조건을 따른다.
6. 연결/보안 → 정상 업무 → duplicate/응답 유실/경합 → lifecycle/복구 → 관측 순으로 실행한다. 실패 주입은 격리 데이터와 승인된 테스트 경로에서만 수행한다.
7. 결과와 남은 오류를 보존한다. 종료 시 테스트 발신을 먼저 멈추고 미완료 작업을 확인한 뒤 수신/flags를 정리한다. 원본 이벤트·원장을 일괄 삭제하지 않는다.

## 3. 함께 통과했다고 보고하면 안 되는 범위

- 실제 RevenueCat/Apple/Google 구매·지급·기간 stacking·부분 환불/reflow/시험 차단은 PLAN-010~012의 해당 구현·fixture·sandbox 준비 후 별도 묶음으로 추가한다. 상품/구매 계정 API 성공만으로 결제 E2E 완료를 주장하지 않는다.
- C 승인 DEAD_LETTER 재처리 도구가 준비되기 전에는 재처리 성공 검증을 실행하지 않는다. 이번에는 원본 보존/분류를 확인하며 DB 직접 수정이나 새 event/operation으로 우회하지 않는다.
- TMI-205 삭제 코어 로컬 검증은 실거래 증거 provider/drain 준비와 다르다. UNKNOWN/미준비 상태에서 purge ON이나 실제 삭제를 실행하지 않는다.
- 키 회전·긴급 회수 실습은 키 담당/회수 SLA와 별도 실습 범위 확정 후 수행한다. production 전환/Store 판매 활성화는 별도다.

## 4. 결과 기록

각 케이스에 ID, 서비스 revision, 환경/경로, 비밀 없는 설정 요약, fixture revision/hash, 기대/실제 결과, 원장/상태 불변식 증거, PASS/FAIL/BLOCKED/NOT_RUN을 기록한다. 로컬 테스트와 실환경 결과를 구분한다. BLOCKED/NOT_RUN을 통과로 합산하지 않는다. 실행 결과는 별도 날짜별 보고서로 작성한다.

근거: [Billing B 로컬 검증](../contracts/LC-BILLING-WORKLOAD-JWT-B-BILLING-VALIDATION-2026-10-13.md), [LC JWT 계획](../plans/PLAN-LC-BILLING-WORKLOAD-JWT.md), [통합 계약](../contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md), [무료 reader rollout](PLAN-007-public-reader-rollout.md), [결제 운영 계획](../plans/PLAN-013-payment-rollout-and-operations.md).
