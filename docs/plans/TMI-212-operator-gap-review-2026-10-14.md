# TMI-212 운영 재개 도구 현황 검토

## 1. 결론

- 핵심 계정 조회 재개는 구현됐고, 실제 운영 실행 도구·접근 제어·감사 연결은 미완료다.
- 새 관리자 화면/API 대신 독립 CLI를 권장한다. 기존 recovery CLI 패턴만 재사용하고 탈퇴 복구 명령과 업무를 섞지 않는다.
- 범위는 조회 재시도이며 구매 강제 승인·환불·권리 지급·삭제·구매 검토 case 해결이 아니다.
- 기본 조회/dry-run → 대상·버전 확인 → 명시 apply 흐름으로 충분하다. 새 승인 서버나 자동 승인 worker는 필요 없다.
- 이번은 정적 확인이다. 코드·운영 데이터·Jira·배포 변경 및 테스트 재실행은 하지 않았다.

## 2. 확인한 구현과 남은 범위

| 항목 | 현재 | 추가할 범위 |
| --- | --- | --- |
| 계정 재개 | `PaymentLookupResumer.resumeUnderMaintenance` | 독립 CLI 입력·조회·dry-run·apply 연결 |
| 경합 방지 | expected version/generation, 활성 lease/PENDING 차단, 최대100개 같은 계정, Transaction rollback | 기존 검사 재사용, CLI stale 입력/동시 실행 검증 |
| 재시도 예산 | 최초 생성일 보존, 새 retryStartedAt·24시간 예산, pending 수 불변 | 운영 결과에 재개 건수·보류 사유 표시 |
| AUTH 해제 | 공용 `RevenueCatProjectBudget.resumeUnderMaintenance` | 계정 재개와 분리한 명령, 공용 project 영향 안내 |
| 권한 | 호출자가 인증·인가해야 한다는 전제, boolean 격리 확인 | 제한된 운영 실행 환경·별도 DB 자격증명, 실행자 근거 연결 |
| 감사 | 현재 재개 메서드에 실행자·작업ID·운영 사유·완료 receipt 없음 | 최소 실행 기록과 재개 변경의 원자성, 응답 유실 후 결과 확인 |

현재 LookupFailure는 BLOCKED_AUTH/RETRY_EXHAUSTED/PROVIDER_REVIEW/PROVIDER_CONTRACT/PAGE_LIMIT다. 이는 실패 분류이지 담당자가 왜 재개했는지에 대한 감사 사유가 아니다. 단순 `--operator` 문자열이나 `--approved`는 인증을 대신하지 않는다.

## 3. 권장 구현 단위

1. 읽기 전용 inspect와 dry-run: 환경/계정·선택 명령의 상태·예상 version/generation·재개 가능 여부를 확인한다. Secret/원문/개인정보는 출력하지 않는다.
2. 계정 조회 재개: 선택 최대100건, 작업ID·표준 운영 사유·expected 값으로 명시 apply. 기존 재개 검사와 pending/금융 보호는 유지한다. 감사 receipt와 변경을 같은 Transaction으로 저장하여 commit 결과를 확인할 수 있게 한다. 같은 작업ID 다른 내용은 거절한다.
3. project AUTH 해제: credential 수정과 모든 공유 호출자 중지 확인 후 별도 실행한다. 계정 재개만으로 AUTH를 해제하지 않는다. budget DB의 변경/receipt는 해당 DB Transaction에 묶고 계정 DB와 전역 원자성을 주장하지 않는다.
4. 최소 테스트: 기본 무변경, 잘못된 입력/환경/권한 배선 거절, stale/동시 실행, 감사 저장 실패 rollback, 성공응답 유실 후 조회, AUTH와 계정재개 독립, 비밀정보 비출력.

## 4. 구현 전 기술 명세로 닫을 부분

- CLI 명령/입력, 작업ID 멱등성과 결과 조회, 최소 감사 필드·보존기간·명시 index/schema. 기존 recovery 기록의120일을 결제 운영 감사에 자동 적용하지 않는다.
- 실행자 신원은 승인된 운영 접속/실행 기록에 연결한다. 외부 인증 서비스를 새로 만들지 않으며, 실제 운영자와 DB 최소 권한은 활성화 전에 설정한다.
- 격리는 현재 boolean 전제만으로 실제 worker 중지를 증명하지 않는다. 초기에는 관련 worker/입력 경로를 운영 절차로 멈추고 진행 중 요청을 비운 뒤 실행하는 방식으로 문서화한다.
- `PaymentRecoveryFence`는 ALL_BLOCKED에서 PAYMENT_APPLY도 거절한다. 운영 CLI가 전역 barrier를 임의 OPEN으로 바꾸면 안 된다. writer 정지와 recovery barrier는 별개로 확인하고, 기존 OPEN/PURCHASE_BLOCKED 허용 조건을 유지한다.
- 자동 서버 기동·HTTP endpoint·지급 로직 추가 및 실환경 재개는 이번 권장 범위에 없다.

## 5. 근거

- [PaymentLookupResumer](../../src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentLookupResumer.java)
- [RevenueCatProjectBudget](../../src/main/java/web/tosunsaeng/billing/domain/payment/provider/RevenueCatProjectBudget.java)
- [RecoveryOperatorCli](../../src/main/java/web/tosunsaeng/billing/domain/payment/recovery/RecoveryOperatorCli.java): 기존 독립 실행 패턴이며 결제조회 재개 명령은 없다.
- [PaymentRecoveryFence](../../src/main/java/web/tosunsaeng/billing/domain/payment/recovery/PaymentRecoveryFence.java)
- [기존 재개 계약](../contracts/TMI-212-review-resume-r1.md), [운영 안내](../runbooks/TMI-212-reconciliation-and-review.md)
- [기존 통합 테스트](../../src/test/java/web/tosunsaeng/billing/domain/payment/sync/PaymentSyncIntegrationTest.java): 재개 예산·부분선택·rollback·stale·AUTH 차단 회귀가 존재한다. 이번 검토에서 재실행하지 않았다.
