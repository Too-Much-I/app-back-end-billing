# 자동삭제 Slack 연결·탈퇴 누락 복구 준비

## 5줄 결론

1. 자동삭제 알림은 기존 PaymentSlackSink에 연결했다. 새 채널·별도 알림 DB를 만들지 않는다.
2. 일일 검토 요약, worker 장애, 작업 재개 알림만 전달하며 삭제 승인/삭제 성공 통지가 아니다.
3. Billing snapshot/feed·JWT HTTP·worker는 기존 구현을 재사용한다. Identity에도 HTTP/runtime 구현이 존재한다.
4. 실제 키·ALB 경로·Identity stream·최초 baseline은 별도 환경 점검과 활성화 작업이다.
5. 이번에는 Billing 코드/테스트/문서만 변경했다. 테스트·운영 설정, 실제 데이터와 타 서버는 변경하지 않았다.

## 반드시 읽을 내용

### 자동삭제 알림

`BILLING_PAYMENT_CLEANUP_ALERTS_ENABLED=true`는 reconciliation ON과 기존 `BILLING_PAYMENT_PROVIDER_SLACK_ENABLED=true` 및 유효한 webhook 설정을 요구한다. 누락 시 기동을 거절한다. 기본값 OFF는 유지한다.

- 매일 서울 시각 09시 이후 첫 작업에서 BLOCKED 건수·가장 오래된 보류 시간·검토 기한 초과 건수를 보낸다.
- 실패 알림은 최대 시간당 한 번, 실패한 요약/복구 알림도 한 시간 이후 재시도한다. 복구 알림은 실제 후보를 처리한 오류 없는 tick에서만 보낸다.
- Slack 실패는 삭제 판단·DB job 상태를 바꾸지 않는다. 실패 지표 `billing_cleanup_alert_failure`로 관찰한다.
- 사용자/계정 ID·구매 원문·키는 보내지 않는다. 별도 건별 조사에는 기존 권한 있는 운영 절차를 사용한다.
- 기존 결제·환불 알림과 전송기를 공유하므로 Slack 인증 차단과 Retry-After도 공유한다. 재기동 시 알림 중복 가능성은 허용한다.
- 알림 OFF에서는 요약 DB 조회와 Slack 호출을 하지 않는다. 업무 실패 지표는 계속 기록한다.

### 복구 활성화 순서

1. Identity 담당자가 recovery runtime/HTTP/snapshot jobs, 실제 source stream·환경·전용 JWT 수신키와 TLS 경로 준비를 확인한다. 기존 탈퇴 이벤트 발신 ON만으로 복구 API 준비가 끝난 것은 아니다.
2. Billing의 recovery identity-base-url/allowed-host/identity-environment 및 workload 환경·서명키·audience를 맞춘다. HTTPS origin은 path/query/사용자정보/명시 port를 받지 않는다. private key는 전달하지 않는다.
3. 기존 recovery/snapshot schema와 control을 읽기 확인한다. **기존 OPEN control에 baseline이 없으면 INITIAL을 재실행하거나 checkpoint를 임의 작성하지 않는다.**
4. 초기 기준점 수립은 쓰기 영향이 있으므로 별도 점검 창에 수행한다. 기존 control은 정확한 epoch/version으로 BLOCK → 필요 시 ROTATE → RECOVER 절차를 따르고, 실제 Identity stream으로 최초 snapshot을 명시 시작한다. 변경된 version은 매 단계 다시 읽는다. baseline 생성 전 OPEN으로 우회하지 않는다.
5. Billing recovery HTTP/snapshot/snapshot-worker/feed와 fence를 준비된 조합으로 활성화한다. snapshot count/digest와 적용 완료, 이후 feed CAUGHT_UP 및 checkpoint를 확인한 뒤 RecoveryOperator OPEN 조건을 충족시킨다.
6. 실제 탈퇴 누락·중복·응답 유실·snapshot 이후 feed·기존 구매 보호를 테스트 환경에서 검증한 뒤 운영에 적용한다. retention ON은 별도 삭제 범위 검토 사항이다.

원격 ACK는 추가하지 않는다. 진행 상태는 Billing 로컬에서 관리한다. 원본 이력이 소실되면 남은 WITHDRAWN tombstone snapshot으로 현재 상태를 맞추고 과거 불확실성은 남긴다. snapshot 무결성이 삭제된 과거 tombstone의 완전성을 증명하지는 않는다.

## 사용자 결정 사항

새 제품 정책 결정은 없다. 실제 환경 적용 시점과 최초 복구의 구매 차단 점검 창은 별도 확정한다. 이번 코드 변경은 활성화 승인이 아니다.

## 위험·미확인

- 실제 Slack 수신과 양 서버 JWT/ALB/DB E2E는 이번 로컬 테스트로 확인하지 않는다.
- 기존 test control OPEN/baseline 없음 기록은 재점검 대상이며 이번에 실DB를 조회하지 않았다.
- RecoveryMonitor는 지표/heartbeat다. 이번 cleanup Slack 연결이 recovery 정지 감시까지 제공하지 않는다. prod에서는 기존 모니터링으로 recovery 지표를 관찰할 필요가 있다.
- 알림 유실/재기동 중복은 허용하지만 삭제·구매 보호를 완화하지 않는다.

## 근거

- Billing: CleanupConfiguration, CleanupAlerts, CleanupWorker, PaymentSlackSink, RecoveryHttpConfiguration, WithdrawalSnapshotProcessor/Worker, WithdrawalFeedProcessor/Worker, RecoveryOperator.
- Identity 읽기 확인: RecoveryHttpConfiguration/Filter, RecoveryWorkloadConfiguration, RecoveryRuntimeConfiguration, RecoveryJobsConfiguration. `/internal/v1/billing/withdrawals` recovery namespace.
- 기존 상세 절차: [복구 운영 도구](TMI-204-operator-and-bounded-verification.md), [자동 복구](TMI-204-automation-and-retention.md). 과거 SigV4/HTTP 미구현 표현은 현행 JWT 구현 기준으로 해석한다.
