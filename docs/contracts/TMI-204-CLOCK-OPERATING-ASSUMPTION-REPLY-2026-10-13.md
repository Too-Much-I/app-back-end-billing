# Identity 시간 운영 전제 변경 — Billing 확인·회신

- 날짜: 2026-10-13 / 브랜치: `feat/TMI-209-billing-cleanup-foundation`
- 범위: TMI-202·203 인계에 대한 TMI-204 코드 확인과 공동 문서 정합화. 실배포·활성화 승인이 아니다.

## 1. 결론

1. 별도 시간 증명 요구를 제거하고 인프라 시각 동기화·운영 대응을 전제로 하는 좁은 변경을 Billing 문서에 수락·반영했다.
2. Billing 런타임에는 `RuntimeEvidence`, `clockTrusted`, `BLOCKED_CLOCK`, `CLOCK_UNTRUSTED` 의존성/상태가 없다. 제거할 production 코드는 없다.
3. 공동 계약 §5.4의 독립 clock 오차 차단 의무만 개정했다. wire/body/schema/hash/index·JWT 시간 규격은 불변이다.
4. Identity 발신은 확인한 로컬 코드에서 아직 증명을 요구한다. Identity 구현 완료나 양측 변경 후 검증 완료로 기록하지 않는다.
5. 기존 blocked 자료·실제 인프라 시간 동기화는 조회하지 않았다. 일괄 재개·데이터 변경·배포·Jira 변경은 하지 않는다.

## 2. 코드 확인과 보장 범위

| 확인 대상 | Billing 현재 동작 |
| --- | --- |
| RecoveryHttpConfiguration | 일반 `Clock`과 전용 JWT 설정을 사용한다. ON은 origin/환경/키/fence를 검증하며 외부 시간 증명 bean은 요구하지 않는다. OFF는 client/key를 초기화하지 않는다. |
| JwtIdentityRecoveryClient / WorkloadJwtCodec | 요청마다 토큰 발급. RS256·키·issuer/subject/audience·정수 NumericDate, TTL 1~60초, 30초 skew와 nbf 관계 검사 유지. |
| HttpIdentityRecoveryClient / RecoveryRetryPolicy | 일반 clock으로 정수초·HTTP-date Retry-After를 계산한다. 값의 유효성·중복·overflow·24시간 경계를 검사한다. |
| 복구·정리 | lease/version/epoch·source 유효성·보존·복원 fence·구매/삭제 보호는 유지한다. 이번 작업에서 변경하지 않는다. |

Billing pull의 잘못된/중복 Retry-After 기본 backoff와 장기 probe 정책은 Identity push의 BLOCKED_RETRY_AFTER·20회 DEAD_LETTER 정책과 다르다. 기존 각 계약을 유지하며 이 인계로 통일하지 않는다. 과거 HTTP-date 자체를 보존하는 parser와 기본 backoff보다 이르게 재시도하지 않는 호출부도 구분한다.

Identity 저장소는 읽기만 확인했다. `WithdrawalCaptureConfiguration.PublisherConfiguration`은 `RuntimeEvidence`를 받고, `BillingWithdrawalPublisher`는 `clockTrusted=false`를 BLOCKED_CLOCK으로 분류하며 `BillingWithdrawalRetry`에도 clock 인자를 전달한다. 같은 RuntimeEvidence가 구매 활성화에 쓰인다는 점을 확인했으며 해당 보호를 제거하는 근거로 사용하지 않는다.

## 3. 운영·호환성

앱이 별도 provider로 절대시각 오차를 독립 탐지한다는 보장을 제거한다. 시간 자체의 사용이나 검증을 제거하지 않는다. 시계가 크게 틀리면 JWT뿐 아니라 재시도·lease·만료도 오판할 수 있다. 배포 단계에서 인프라 동기화·이상 알림·영향 작업 중단 및 복구 책임을 확인한다. 신규 clock 서비스·승인 collection/API·상수 true 증명 adapter는 만들지 않는다.

Identity의 기존 BLOCKED_CLOCK은 호환 읽기를 유지하고 원 event/body·원 오류·attempt·notBefore를 보존한다. 원천 만료, 현재 설정과 권한을 확인한 건만 기존 승인 재개 절차를 따른다. Billing 코드에 상태가 없다는 사실을 실제 DB에 legacy 자료가 없다는 증거로 사용하지 않는다. Billing 쪽 새 상태/이관 작업은 필요성이 확인되기 전 추가하지 않는다.

## 4. 검증과 남은 작업

- Billing Retry-After 회귀에 과거 HTTP-date, 0초, 정확히24시간/초과, 중복 header 경계를 추가했다.
- Billing `./gradlew clean test`: 65 suites, **1,039개 통과 / 실패0 / 오류0 / skip0**. 로컬 replica-set Mongo 통합 회귀와 JWT·복구·재시도 테스트 포함. 이번 신규 테스트는 1개이며 기존 미커밋 TMI-209/LC fixture 테스트도 포함된 저장소 전체 결과다. Identity가 제공한 변경 전 수치를 재사용하지 않았다. 문서 링크6개·`git diff --check`도 통과했다. 실제 외부 서버/Store 호출 검증은 아니다.
- Identity 발신 수정 후 별도 증명 없는 ON 구성, JWT 시간 오류, exact204/401/403, Retry-After, 중복·응답 유실·lease 보호를 양측에서 검증해야 한다.
- 실환경 시간 동기화·ALB/TLS·키 교체·blocked 자료 확인과 필요한 선택 재개, 배포·판매·자동삭제 활성화는 별도다.

## 5. 근거

- [공동 계약 §5](IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md)
- [workload JWT 규격](WORKLOAD-JWT-AUTH-R1.md)
- [복구 구성](../../src/main/java/web/tosunsaeng/billing/domain/payment/recovery/RecoveryHttpConfiguration.java)
- [재시도 정책](../../src/main/java/web/tosunsaeng/billing/domain/payment/recovery/RecoveryRetryPolicy.java)
- [JWT 검증](../../src/main/java/web/tosunsaeng/billing/global/config/security/WorkloadJwtCodec.java)
- [재시도 테스트](../../src/test/java/web/tosunsaeng/billing/domain/payment/RecoveryRetryPolicyTest.java)
