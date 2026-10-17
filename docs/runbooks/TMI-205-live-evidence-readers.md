# TMI-205 실제 거래 확인 연결

> 후속 구현: 2026-10-17 [관측 기반 삭제 R1](../contracts/TMI-205-OBSERVED-ABSENCE-R1.md) 승인으로 조건부 purger를 연결했다. 아래 coverage 미발급/purge ON 무조건 거절은 직전 단계 기록이다. 기본 OFF/실환경 미활성화는 유지한다.

작성: 2026-10-17 / `codex/billing-test-deployment`

## 5줄 결론

1. cleanup 재확인에 Billing 실제 구매·미처리 자료와 RevenueCat 조회를 연결했다.
2. 기존 provider runtime의 검증된 프로젝트·환경·상품 매핑·공유 호출 예산·HTTP transport를 재사용한다.
3. 빈 목록·404는 Store 전체 미구매 증명이 아니다. 자동 삭제 ON 기동 거절은 유지한다.
4. 15일 후보·매시간 점검·미해결 24시간 재확인·최초 7일 검토 기한과 기존 CAS/lease/epoch는 바꾸지 않는다.
5. 배포·실DB 변경·Identity 연동·실제 자동 삭제는 이번 작업에 포함하지 않는다.

## 구현한 동작

- `CleanupLocalReader`: 같은 Mongo ClientSession에서 구매(환불/취소 포함), 기간권, timeline이 있으면 FINANCIAL_RETAINED. pendingEvidence, 미해결 inbox/case/sync, ACCOUNT_LOOKUP 이외 cursor 또는 lookup lease가 있으면 보류한다.
- 소유자가 아직 연결되지 않은 같은 환경 inbox/case는 어느 계정의 거래인지 확정할 수 없어 보수적으로 보류한다. 다른 환경과 ignored inbox는 제외한다. 이는 프로젝트에 미해결 무소유 자료가 있으면 다수 후보가 보류될 수 있다는 단점이 있다.
- DB 조회 오류/timeout을 NONE으로 바꾸지 않는다. 각 존재 조회는 `_id` projection·limit1·200ms maxTime을 사용하고 기존 전체 2초 Transaction 한도를 유지한다. 큰 inbox의 조회 성능/인덱스 적정성은 실환경 활성화 전 확인해야 한다.
- `CleanupProviderReader`: 현재/과거 ref 전체·pagination을 조회한다. 검증된 거래 발견 시 보존, 해석 불명 거래는 보류한다. 최대 100 page·100 ref, 중복 ref/cursor·deadline 경계를 거절한다. 총 5초 잔여 조회 예산을 HTTP까지 전달하며 늦게 도착한 결과를 폐기한다. budget DB 작업은 기존 timeout을 유지하므로 실제 반환이 deadline보다 늦을 수 있으나 늦은 결과를 증거로 채택하지 않는다.
- provider 401/403, 계약 오류, 404/부분조회, 일시 장애를 기존 reason으로 분리한다. API 읽기 외 Store/RevenueCat 고객 삭제·구매 지급은 하지 않는다.
- reconciliation ON은 실제 provider runtime을 요구한다. 별도 API key/HTTP client/호출 예산을 만들거나 fake fallback으로 기동하지 않는다. provider runtime 구성 시 기존 금융·sync·lookup schema 검증이 선행한다.

## 미완료 경계와 다음 작업

현재 계약/TMI-205는 **local NONE + provider 빈 조회만으로 삭제하지 않는다**. Store의 RC 미관측 거래, 확인 가능한 전체 앱/스토어 범위, 탈퇴 전 token/진행 중 요청 종료와 삭제 후 늦은 거래 격리의 실검증이 남는다. `CoveragePolicy`는 아직 증명을 발급하지 않으며 purge는 기동 거절이다. 이번 변경을 “자동 삭제 구현/활성화 전체 완료”로 해석하지 않는다.

또한 세 proof version을 cleanupPolicyVersion 하나로 합치는 명시 schema 이관은 별도 미완료 사항이다. 이번에는 증거를 생성하지 않으므로 기존 proof/schema를 임의 변환하지 않았다. Slack sink/운영 미해결 보존 기준도 별도 활성화 준비 사항이다.

후속은 위 범위의 근거 확인이다. 단지 RC 조회 결과만을 근거로 삭제하도록 위험을 수용하려면 현재 승인 계약을 바꾸는 별도 결정이 필요하며 이번 구현에서 추정하지 않았다.

## 검증

- 기존 cleanup core/replica-set 회귀 및 실제 로컬 reader replica-set 테스트, provider pagination/404/오류/미확인 거래/시간 경계 테스트를 추가했다.
- 전체 실행 결과는 WORKLOG의 같은 날짜 항목에 기록한다. 실제 Atlas/RevenueCat/Store는 테스트에서 호출하지 않는다.
- public/internal wire·보존기간·삭제 대상·Identity/LC 인증과 설정 불변. 기존 dirty 변경은 보존했다.
# 2026-10-17 후속 승인 및 구현

[관측 기반 삭제 R1](../contracts/TMI-205-OBSERVED-ABSENCE-R1.md) 승인 후 `CleanupDeletionPolicy`와 조건부 purger를 연결했다. 위 ‘coverage 미발급/purge ON 무조건 거절’은 직전 단계 기록이며 현재 구현에서는 승인 기준 충족 시 증거를 생성한다. 기본 OFF와 실환경 미활성화는 유지한다. 구매 계정/sync 토큰 수명 30분 상한도 Billing에서 검사한다. 운영 전 Identity 실제 TTL·탈퇴 발급 차단 확인이 필요하다.
