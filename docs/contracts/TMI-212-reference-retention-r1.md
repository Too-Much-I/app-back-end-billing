# TMI-212 — 만료 조회 참조·작업 잠금 정리 r1

> 2026-10-14 승인 갱신: 금융 사용자 연결 제거/5년 최종 정리는 [장기 후속](../plans/TMI-212-financial-link-erasure-plan.md)으로 분리하고 현재 결제 출시 선행 조건에서 제외한다. 이 문서의 구현과 기본OFF는 유지하며 코드를 제거하거나 실제 삭제를 활성화하지 않는다. 보존기간 연장·전체 금융 삭제 승인·다른 배포 gate 면제가 아니다.

## 1. 5줄 결론

- 암호화 `payment_purchase_references`만 현재 구매 보존기간 만료 후 명시 삭제한다. 금융 기록과 계정 연결은 삭제하지 않는다.
- 매시간 단계별 최대100개, 기본 읽기 점검이며 `reference-retention-enabled=false`다.
- 현재 Purchase·기간권·원장·미해결 업무를 같은 Transaction에서 확인한다. 참조의 캐시된 보존일만으로 삭제하지 않는다.
- 만료된 계정 조회 lease는 owner/until만 비우고 version/leaseVersion을 증가시킨다. 페이지·세대·재시도·검토 상태는 보존한다.
- 금융 기록의 사용자 연결 제거는 필수 필드/schema·reader 변경이 필요하므로 미완료다. 전체5년 purge 완료나 운영 활성화 승인이 아니다.

## 2. 사용자가 반드시 읽어야 하는 내용

외부 API·지급·환불 정책·5년 보존기간은 바꾸지 않는다. 새 TTL·collection·index도 없다. 205 미구매 계정 삭제와 별개다. 금융 schema는 Purchase.accountId/ownerRefId와 entitlement.accountId를 필수로 요구하므로 지우거나 가짜 ID로 치환하지 않는다.

`billing.payment.provider.worker.reference-retention-enabled` / `BILLING_PAYMENT_PROVIDER_REFERENCE_RETENTION_ENABLED`는 기본false다. provider worker가ON이면 매시간 동일 점검을 수행하고 false에서는 DB 변경 없이 자격만 검사한다. true일 때만 참조 삭제·만료lease 해제를 실행한다. terminal-sync-delete-enabled와 독립이며 실제 활성화는 별도 승인이다.

## 3. 남은 결정·작업

삭제 대상은 암호화 참조 행과 그 행에 있는 accountId 연결뿐이다. 사용자→계정→구매의 금융 연결은 남는다. 이를 제거하려면 nullable/분리 연결 모델, 기존 reader와 중복 구매 처리, 계정 내 신규/오래된 구매 분리, 감사·원장 보존을 명세해야 한다. 이 문서는 해당 schema 변경을 승인하지 않는다.

## 4. 위험·미확인 사항

- 금액 불명·미해결 case는 기간만으로 삭제하지 않는다. 장기보류 사유와 종료 조건은 별도 운영 검토 대상이며 무기한 보관 승인이 아니다.
- PLAN012 reversal ledger 타입은 아직 지원하지 않는다. 새 타입 발견 시 보류하며 환불 구현 때 마지막 reversal 보존 기준과 회귀를 확장해야 한다.
- 반환 최대100은 DB scan 상한이 아니다. 기존 인덱스·DB timeout을 사용하며 운영 explain/부하 확인이 필요하다.
- 인프라 시각 동기화, 복원 후 epoch 차단·만료 정리, 최대35일 backup 소멸은 배포 단계에서 검증한다.

## 5. 상세 동작

### 참조 삭제

환경과 캐시 retentionExpiresAt<=now로 후보를 찾는다. 각 행마다 최초epoch/OPEN barrier를 검사한다. 실행에서는 기존 write fence를 같은 Transaction에서 획득하여 구매·sync·계정worker 등 fence writer와 충돌 시 재검사한다. dry-run은 read-only check다.

현재 Purchase가 존재하고 READY·money확정·retentionExpiresAt<=now이며 참조의 account/store/app/transactionKey/keyVersion이 같아야 한다. 환경계정·ACTIVE/WITHDRAWN lifecycle·pendingEvidence=0·계정cursor가 필요하다. 계정 또는 동일 거래의 case/RECEIVED source, 비terminal sync가 있으면 보류한다. lookup owner/페이지cursor/IDLE 이외 failure가 남아도 보류한다. 만료owner는 잠금 단계에서 해제하고 다음 점검에 다시 확인한다.

해당 기간권 endsAt·revokedAt 및 최신 ledger.occurredAt의 UTC 달력5년이 경과해야 한다. 기간권/ledger 누락도 보류한다. 지원 ledger는 PURCHASE_OBSERVED/PURCHASE_VERIFIED/MONEY_CONFIRMED이며 새 타입은 보류한다. 참조 version exact로 삭제하며 Purchase/entitlement/ledger/lifecycle/pending/계정/alias는 변경하지 않는다. 재실행은 이미 삭제된 참조를 건너뛴다.

### 만료 lease 정리

ACCOUNT_LOOKUP·문자열owner·날짜until<=now만 대상이다. 계정환경·최초epoch/OPEN·현재cursor version/owner/until을 Transaction에서 다시 확인한다. version/leaseVersion overflow는 실패다. owner/until=null, version/leaseVersion+1만 수행한다. generation/attempts/failure/암호화페이지/reference/pages/nextAttemptAt/reconcileAt/reconcileStartedAt을 유지한다. REVIEW/BLOCKED를 자동 재개하지 않는다. 정상 worker renew/claim과 경합하면 Tx/CAS로 재평가한다.

### 배치·관측

참조와lease 각 단계 최대100, `_id` seek를 프로세스 메모리에 유지하며 보류 행도 넘어간다. 끝에 오면 다음 tick부터 처음으로 돌아가고 재시작 시 처음부터 재검사한다. 오류 행은 다음 순회에서 재시도하며 실패를 성공에 넣지 않는다. 여러 인스턴스 중복 점검은 Tx/CAS로 수렴한다. 대상/자격/삭제/lease해제/실패 건수만 metric으로 남기고 식별자·암호문·원문은 출력하지 않는다.

## 6. 근거

- [구현](../../src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentReferenceRetentionWorker.java)
- [참조 규격](TMI-212-purchase-reference-r1.md)
- [접수·sync 보존](TMI-212-retention-r1.md)
- [Mongo 회귀](../../src/test/java/web/tosunsaeng/billing/domain/payment/sync/PaymentSyncIntegrationTest.java)
