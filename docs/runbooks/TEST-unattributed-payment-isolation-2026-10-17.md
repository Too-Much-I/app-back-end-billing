# 미귀속 결제의 조회 차단 범위 축소

## 승인 및 변경

사용자는 미귀속 환불의 식별이 늦어지는 동안 기존 이용권 사용이 허용될 위험을 수락했다. 미귀속 case/inbox 때문에 환경 전체 paid 조회가 PENDING이 되는 조건만 제거한다. 신규 정책 엔진·구매별 예외·관리 화면은 추가하지 않는다.

- 계정이 없는 미귀속 자료는 기존 검토 흐름에 보존하며 이용권 지급 근거로 사용하지 않는다.
- 본인 accountId의 case 및 미처리 REFUND/REVOKE 조회 보류, 구매 processingStatus 검사, 탈퇴 및 recovery barrier 검사는 유지한다.
- 예약·확정은 이미 해당 구매의 VERIFIED/ALLOWED/READY와 source 연결을 검사한다. 전역 미귀속 차단은 없어 제품 코드를 변경하지 않고 실제 Mongo 회귀를 추가했다.
- API/DTO/DB schema/키/기능 flag/기존 자료는 변경하지 않는다. 기존 미귀속 2건을 삭제하거나 임의 해결하지 않는다.

## 검증

PaymentSyncIntegrationTest에 미귀속 RECEIVED inbox 및 REVIEW_REQUIRED case 각각의 기존 유료권/구매 계정 없는 사용자 조회와 무쓰기·무지급 검증을 추가했다. PaidReservationIntegrationTest에는 미귀속 case가 있어도 정상 구매 reserve/confirm이 성공하고 case가 보존됨을 추가했다. 본인 환불 보류·환불 선행 confirm 거절 기존 회귀는 유지한다.

관련 두 통합 테스트 클래스 및 Java21 전체 clean test/bootJar 통과(4분3초). 사용자 승인 commit/push/develop 병합·테스트 서버 이미지 교체는 진행 중이며 완료 기록 전 배포 성공으로 간주하지 않는다. 운영/Identity/LC 배포는 제외한다.
