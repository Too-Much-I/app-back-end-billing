# 테스트 결제 PENDING 진단 (2026-10-17)

- 사용자 보고: 16:09 KST 전후 paid=PENDING/hasPendingSync=false, RECONCILIATION_PENDING.
- 읽기 확인: AWS Billing 테스트 로그 07:00~07:30 UTC, Atlas tosunsaeng-test의 to-teacher-billing-test만 조회.
- payment_purchase_cases 2건: accountId=null, REVIEW_REQUIRED, OWNER_UNRESOLVED. 확인된 한 건의 receivedAt/createdAt은 07:06:07.925Z.
- payment_sync_commands의 관측된 두 건은 PLAY_STORE/APPLIED, errorCode=null이며 서로 다른 purchaseId를 참조한다.
- purchases 2건 모두 PLAY_STORE, VERIFIED/ALLOWED/READY. subscription_entitlements 2건 및 revokedAt=null 확인.
- PaidEntitlementReader는 환경 내 accountId=null case가 하나라도 있으면 해당 환경의 paid 조회를 unknown으로 반환한다. PaidExamQueryReader가 이를 RECONCILIATION_PENDING/canTakeExam=null로 변환한다. 이는 결제 처리 대기와 별개다.
- 로그의 entitlement_query outcome=READY는 최상위 무료 조회 상태이며 paid READY 증거가 아니다. sync controller는 metric/span만 기록하므로 일반 로그 부재로 미호출을 단정할 수 없다.
- 결론: 테스트 DB의 구매 반영은 확인됐으나, 미귀속 case가 유료 조회를 환경 전체에서 보류시키는 조건이 확인됐다. 사용자별 요청 식별자가 없어 보고자의 거래와의 최종 대조는 미완료.
- 변경 없음: 제품 코드, DB, 외부 설정, 권리 지급, 배포, Jira. 미귀속 case의 원문 원인/안전한 해소 방식은 후속 조사·수정 대상이며 임의 삭제하지 않았다.
