# 구버전 무료·신버전 이용권 시험 공존 검토

- 사용자 요청: 기존 앱은 계속 무료 응시, 새 앱은 결제/무료권 필요. 코드 확인만 요청했으며 LC 변경·prod 조회/배포는 하지 않았다.
- LC 로컬 ExamRestController는 POST /api/v1/exams 하나만 제공한다. Idempotency-Key는 controller에서 optional이며, ExamServiceImpl이 paidExamEnabled/creationSagaEnabled 전역 설정으로 paid saga/billing saga/legacy startNew를 선택한다. 클라이언트별 분기 없음.
- LegacyExamWriterGuard는 cutover state가 하나라도 설치되면 기존 startNew를 거절한다. OFF만으로 무료 생성 경로를 복원할 수 없다.
- LegacyExamRuntime.requireKnown은 고정 manifest의 기존 세션 또는 유효한 paidReservation 증거를 요구한다. 새 legacy writer만 허용하면 이후 생성된 무료 세션의 제출/채점 등이 거절될 수 있어 lifecycle까지 함께 설계해야 한다.
- 권장: 기존 v1 생성 호환 경로 유지, 신앱 전용 명시적 생성 경로(예: v2)에서 항상 사용권 검사. 기존 무료 세션을 서버가 별도 출처로 기록하고 소유권/삭제/탈퇴/종료 검사는 유지. cutover·manifest·동일 사용자 구신 동시 생성 경합 및 환불 이벤트의 출처 분리 보완. URL/정책은 제안이며 확정 wire 아님.
- 헤더 누락이나 Billing 실패를 legacy 무료 fallback으로 쓰지 않는다. 앱 버전/별도 URL은 위조 방지가 아니므로 구경로를 열어둔 기간 신클라이언트의 우회 호출 위험은 남는다. 강제 방지에는 별도 서버 신뢰 기준이 필요하며 이번에 추가하지 않는다.
- 기존 답변의 두 경로가 동일 사용권 소비를 강제한다는 일반안은 이번 사용자 의도와 구분한다. 구버전 무료 응시는 유료권/신규 무료권을 소비하지 않는 호환 예외로 설계해야 한다. 다만 동일 사용자 활성 세션 경합·신경로 중복 소비 방지와 기존 기록 보존은 필요하다.
- 검증: LC 정적 읽기만 수행. 테스트/제품 코드/DB/외부 설정/배포 변경 없음. prod 실효 상태는 미확인. 다음은 LC 담당에게 공존 설계·구현 범위를 인계하고 우회 위험/구버전 지원 기간을 확정하는 것이다.
