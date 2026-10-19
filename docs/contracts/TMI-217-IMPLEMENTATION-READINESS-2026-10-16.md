# TMI-217 삭제 연동 선행 확인

## 1. 결론

1. 삭제 연동은 RevenueCat/Store 환불 응답과 독립적으로 먼저 진행할 수 있다.
2. Billing 생성 owner 근거와 LC 생성 중 삭제409는 이미 구현돼 있다. 전체 삭제 기능이 없는 것으로 설명하지 않는다.
3. 새 삭제 sender/receiver·ACK 연결·단일 merge 증거 검증은 아직 확인되지 않았다.
4. 공동 draft-3의 route/오류/eventId 보존·owner 근거를 먼저 수락해야 하며 현재 문서를 동결 완료로 간주하지 않는다.
5. 최종 물리 purge는 별도 단계로 분리할 수 있다. 미정인 보존 수치를 발명하거나 무기한 개인정보 보존 승인으로 해석하지 않는다.

## 2. 현재 구현과 권장 순서

| 항목 | 로컬 확인 사실 | 다음 작업 |
| --- | --- | --- |
| Billing 생성 소유자 | paid runtime의 creationOwner는 병합됨. 기존 무료 ReserveService 보완은 원폴더 TMI217에 미커밋 상태 | 해당 변경을 보존·통합하고 writer coverage 확인 |
| LC 생성 중 삭제 | DeletionCommandService가 같은 owner Transaction에서 activeGuard/nonterminal 생성 확인 후409; 새 삭제 자료 삽입 전 검사 | 기존 동작 유지, 공동 경합 검증 |
| 삭제 전달 | Billing 새 route 및 LC 삭제용 JWT purpose/sender가 없음. LC issuer Purpose는 기존 두 개 | 7필드·전용 audience·오류/포트 계약 수락 후 양측 별도 구현 |
| 중복 | draft-3는 동일event/body204, 다른event 동일Session409 | LC가 재시도/재접수에 최초eventId를 유지하는 저장 위치와 보존 조건 확인 |
| 단일 게스트→회원 이전 | 생성자 기록만으로 현재 회원의 삭제 권한을 증명하지 못함 | 기존 인증된 단일 UserMerged와 exact Session의 연결을 어떤 최소 영속 자료로 확인할지 합의. phone 과거 Session은 이전하지 않음 |
| 원문/ACK | LC P4.1은 sender/ACK를 후속으로 명시 | 원문 정리는 독립 진행, 필요한 Billing ACK 전 새 시험 차단. 기존 continuation은 삭제 전달의 대체 아님 |
| 삭제 후 시험 | Billing previous()는 deleted/replacementBlocked를 거절 | 검증된 삭제 fact에 한해 기존 restartContext로 INITIAL·새권리 판정; 무료권 반환/복원0 |
| 환불 경합 | 현재 paid 상태 event는 group와 Session의 deleted/lifecycleDenied를 검사함 | 실제 삭제 writer와 동일Tx 경합, 과거Session 완료·fanout/recheck 증거 보존 테스트 추가 |
| 최종 purge | LC P6.1은 120일 보관 및 읽기 후보 검사, 실제 purge 아님 | deny/귀속·무기한 무료권/복원unique·미ACK/hold의 폐기 조건은 후속 결정 |

권장: **공동 삭제 기술 계약 수락 → Billing 삭제 수신/권한·새시험 분기와 LC 전달/ACK를 각 저장소에서 구현 → 환불 경합 검증 → 별도 최종 purge**. 실제 provider 자료 확보가 이 작업의 선행 조건은 아니다. LC 구현을 Billing 작업 승인만으로 대신 수정하지 않는다.

## 3. 공동 확인이 필요한 사항

- draft-3의 7필드/route/audience/오류를 사용한다는 범위 한정 수락. 기존48개 검증 목록은 계획이며 통과 아님.
- LC 최초 eventId/body의 불변 보존 및 ACK 전 신규시험 차단 위치.
- 단일 UserMerged의 영속 증거·현재 귀속 대조와 legacy 근거 부재 처리. 현재 owner를 과거 owner로 backfill하지 않음.
- 초기 구현에서 자동 최종 purge를 제외하는 범위와 운영 활성화 전 목적별 보존/종료 조건. 보존 미정 때문에 원문 개인정보 정리를 늦추지 않음.

새 제품 정책 재선택보다는 구현 호환 확인이 중심이다. 최종 보존 운영은 별도 남는다. 이번 확인으로 공동 수락이나 새 코드 착수를 선언하지 않는다.

## 4. 근거·검증 범위

- [공동 draft-3](LC-BILLING-EXAM-DELETION-RETENTION-R1.md), [LC 인계](TMI-217-LC-TECHNICAL-HANDOFF.md), [검증 계획](../plans/TMI-217-DELETION-OWNER-RETENTION-VERIFICATION.md).
- Billing 환불 worktree HEAD17edd0d의 PaidAttemptEventService 및 원폴더 HEADd2c1cd2/dirty ReserveService·TMI217 runbook을 읽었다. 원폴더는 수정하지 않았다.
- LC HEAD6f6675f/feat/billing-paid-exam-p6-1의 로컬 DeletionCommandService/DeletionWorker/BillingWorkloadTokenIssuer와 P4.1/P6.1 기록을 읽었다. 배포 상태 확인은 아니다. LC 코드/문서 수정·메시지 전송 없음.
- 분석 및 Billing 작업 문서만 작성. Gradle/외부 API/실DB/공동 E2E 미실행. 기존 테스트 결과를 새 삭제 구현 검증으로 재사용하지 않는다. 기존 dirty 변경 보존, 예상 밖 변경 없음.
