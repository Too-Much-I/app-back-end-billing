# TMI-212 D1 — 접수 인계·완료 sync 정리

> 2026-10-14 D2 보완: 아래 미완료 항목 중 APPLIED source TTL 뒤 PENDING sync 완료는 [원장 기반 완료](TMI-212-review-resume-r1.md)로 보완했다. 정기조회/reference/선택재개·운영요약도 [D2](../runbooks/TMI-212-reconciliation-and-review.md)를 따른다. 원천 구매 사실이 불명인 건을 자동 완료하거나 case를 지우지는 않는다.

## 1. 결론

- 119일 이상 RECEIVED 접수는 기존 원자적 `RETENTION_HANDOFF`로 case에 인계한다.
- APPLIED/REJECTED이고 counted=false인 sync만 마지막 갱신부터 120일 뒤 명시 삭제 대상으로 삼는다.
- 기본은 대상 점검만 한다. 실제 sync 삭제는 독립 설정으로 OFF다. 205 계정 삭제와 금융 원장 삭제는 하지 않는다.
- 최초 recovery epoch를 배치에 고정하고 개별 Transaction에서 재검사한다. 삭제는 OPEN에서만 가능하다.
- D 전체 완료가 아니다. 정기 provider 조회·구매 reference 보존·검토 재개·terminal source TTL 경계의 미완료 sync 복구·외부 알림은 후속이다.

## 2. 실행 규격

worker 활성화 시 전용 runtime에서 시간당 한 번, 단계별 최대 100개를 처리한다. 시작 즉시 첫 점검을 허용한다. 여러 인스턴스가 같은 대상을 선택해도 transaction/version CAS로 수렴한다. 장애 후 다음 시간에 다시 점검하며 오류를 성공 건수에 넣지 않는다. 시계 동기화는 인프라 전제다.

RECEIVED는 receivedAt+119일 경계 이상만 선택한다. 기존 handoff가 case의 최소 정규화 증거와 pending 책임을 같은 Transaction으로 옮기며 원장은 변경하지 않는다. 이 행에는 handoff 전 purgeAt이 없어 worker 장애로 원본이 TTL 삭제되지 않는다.

terminal sync는 updatedAt+120일 경계 이상, counted=false, APPLIED 또는 REJECTED만 허용한다. deleteEnabled=false이면 읽기만 한다. ON이면 최초 epoch, OPEN barrier, 현재 state/count/version/updatedAt을 Transaction에서 재검사하고 삭제한다. PENDING/REVIEW_REQUIRED/BLOCKED_AUTH는 기간만으로 삭제하지 않는다. 삭제로 lifecycle pending을 증감하지 않는다. 구매·기간권·ledger·case·계정·cursor는 삭제하지 않는다.

기존 core-r4/sync-command-r1 schema·index·외부 DTO는 유지한다. 런타임 DDL과 새 TTL 없음. 기존 provider inbox TTL을 끄는 설정이 아니며, APPLIED source와 아직 완료되지 않은 sync 사이의 120일 경계 보호는 별도 후속이다. 이 잔여가 닫히기 전 public sync ON 거절은 유지한다.

## 3. 운영·배포

`billing.payment.provider.worker.terminal-sync-delete-enabled=false`가 기본이다. 점검/삭제/실패 건수만 metric으로 기록하며 계정·거래 ID나 원문은 label/log에 넣지 않는다. 이 구현은 Slack 발신을 하지 않는다. 실제 삭제 활성화는 운영 승인, 대상 dry-run, 백업/복원·동시성 검증 뒤 별도로 진행한다. 삭제된 sync는 120일 멱등 응답 캐시의 종료이지 금융 기록 삭제가 아니다.

대량 적체에서 batch 상한은 반환 건수이지 DB 스캔 상한이 아니다. 현행 state/createdAt index와 DB timeout을 사용하며 실제 부하/explain·필요 index 추가는 배포 전 확인한다. 일부 실패가 반복되면 metric으로 파악하고 운영 검토한다. 자동 재개·자동 환불·미구매 판정은 없다.
