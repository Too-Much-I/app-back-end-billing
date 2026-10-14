# TMI-216 공동 상태28 검증 대응표

- 기준: `lc-billing-paid-exam-simplified-exact-r1-fixtures-1`, 원본 bytes 불변.
- 상태는 이번 Billing 코드의 테스트 범위다. 행의 모든 요구를 실행했다는 의미가 아니며 R2 상태26과 합산하지 않는다. 오프라인81개 결과도 runtime으로 세지 않는다.
- `R` = `PaidExamRuntimeIntegrationTest`, `P` = `PaidReservationIntegrationTest`, `M` = `MemberOperationRecoveryIntegrationTest`. 모두 Billing `src/test/java/...`의 실제 테스트 메서드다. 각 클래스 XML/전체 clean test 결과는 실행 기록과 함께 확인한다.
- LC 읽기 확인: HEAD `001b575`, P2/P2.1 완료 보고·P3 진행. 이번 작업에서 LC 테스트/코드 수정은 하지 않았다. LC 자체 보고를 Billing 실행 결과로 합산하지 않는다.

| ID | Billing 범위 | 실제 테스트/증거 | 남은 경계 |
| --- | --- | --- | --- |
| E01 | Billing 부분 검증 | R.httpMemberWaitPreserves503AndAuthenticationFailuresDoNotTouchReceipts | LC 공개503 매핑은 LC P3 검증 필요 |
| E02 | 부분 검증 | 아래 a~h 참조 | LC worker reserve0·전체 조합 미완료 |
| E02-a | Billing 부분 검증 | R.httpMemberWaitPreserves503AndAuthenticationFailuresDoNotTouchReceipts; M.evidencePreservesReferenceAndStatusWaitsUntilSameKeyReserveResumes | LC 대기/같은 Session 복구 |
| E02-b | Billing 부분 검증 | M.pendingCommitsBeforeErrorAndLostResponseIsRecoveredReadOnly | LC status-first 대기 복구 |
| E02-c | Billing 검증 | M.evidencePreservesReferenceAndStatusWaitsUntilSameKeyReserveResumes; R.pendingAdmissionAndMemberRefreshAreReadOnly | LC 관측 worker 별도 |
| E02-d | LC 미실행 | M.competingSameKeyResumesMakeOneReservation는 Billing 중복만 | LC lease/version·cleanup 양순서 |
| E02-e | LC 미실행 | 해당 없음 | LC cleanup 복원 조건 |
| E02-f | 부분 검증 | M.withdrawalWinsOverEvidenceRefreshReplayAndPayloadConflict | LC NEEDS_REVIEW 원인증거·auth/삭제 격리 |
| E02-g | Billing 부분 검증 | M.unknownCommitRetriesCommitWithoutReexecutingAdmission; R.signedHttpReserveConfirmBodyStatusAndReplayUseRealTransactions | LC 응답유실 local finalize |
| E02-h | 부분 검증 | M.pendingSurvives120DaysAndTerminalEligibilityNeverRoundsEarlier | LC 대기창/격리·purge는 별도 |
| E03 | Billing 검증 | M.withdrawalAndEvidenceRaceNeverResurrectAccount; M.withdrawalCompetesWithAdmissionThroughSameLifecycleWriteFence | Identity 실환경 발급 fence 별도 |
| E04 | 미실행 | 해당 없음 | LC P4/TMI-217 삭제 |
| E05 | 미실행 | 해당 없음 | LC P4/TMI-217 삭제 후 replay |
| E06 | Billing 부분 검증 | R.httpPrecreationFailureHasDurableStatusAndDoesNotBecomeNotFound; M.withdrawalWinsOverEvidenceRefreshReplayAndPayloadConflict | E06-owner LC 독립/loopback actual-client 재검증 완료(2026-10-14); 전체 E06/runtime 공동검증과 구분 |
| T01 | 부분 검증 | PaidExamFoundationTest.guaranteeUsesExactInstantAndDoesNotRenewOnRetry | LC 저장경계/실제 purge 별도 |
| T02 | Billing 부분 검증 | M.successReplayDoesNotRequireFreshEvidenceOrCommandCacheAndDoesNotReselectSource; M.pendingSurvives120DaysAndTerminalEligibilityNeverRoundsEarlier | LC lease/login 재개 |
| T03 | 미실행 | 해당 없음 | TMI-217 자료별 purge |
| T04 | Billing 부분 검증 | M.successReplayDoesNotRequireFreshEvidenceOrCommandCacheAndDoesNotReselectSource; M.terminalFailureCommitsAndReplaysAfterSevenAnd120Days | LC operation120일 단일보관: 원 fixture 서술 공동 보완 필요 |
| T05 | 미실행 | P.sameKeyConcurrentTrialPaidConfirmRestoresExactlyOnce는 unique 부분만 | TMI-217 실제 key 폐기 |
| T06 | 미실행 | 해당 없음 | 백업 복원·LC 조기격리/운영 |
| P01 | Billing 부분 검증 | P.phoneTargetPaidStartsInitialAndRestoresTargetWithoutTransition | LC phone reader/E2E 별도 |
| P02 | 부분 검증 | P.trialOwnerFencePrecedesPayloadConflictOnReplay; 기존 PhoneContinuationServiceTest | phone 전체 paid불명/fallback0 조합 공동검증 필요 |
| P03 | 부분 검증 | P.revokedPhoneBindingCannotUseExistingFreeReplacement; PhoneContinuationServiceTest.convertedTrialIsNotAdvertisedAsPhoneContinuation | discovery 이후 전체 paid 변동 조합/E2E |
| P04 | 부분 검증 | P.phoneTargetPaidStartsInitialAndRestoresTargetWithoutTransition; P.sameKeyConcurrentTrialPaidConfirmRestoresExactlyOnce | 서로 다른 sourceA/targetB 반복재가입 전체 조합 필요 |
| P05 | 부분 검증 | 기존 ReserveRequestDecoderTest; P.sameSourceRestartIsReplacementAndNewAfterSubmissionIsInitial | LC P2.1 응답 변조/echo 판정 별도 |
| C01 | 미실행 | 해당 없음 | LC 삭제공통 fence/TMI-217 |
| C02 | 부분 검증 | P.confirmAndRefundRaceConvergeThroughPurchaseWriteConflict; P.confirmAndPreviousCompletionRaceNeverDuplicatesRestorationOrReleasesNewGuard; R.completionDuringNewReserveKeepsPendingOperationAndConfirmStillSucceeds | 삭제 경합/실제 PLAN-012 refund writer·LC finalize 별도 |
| C03 | 부분 검증 | P.sameKeyConcurrentTrialPaidConfirmRestoresExactlyOnce; P.oldOwnerEpochChangeBeforeConfirmDeniesAndKeepsPreviousGroup; R.lateOldTrialCompletionDoesNotReleaseNewPaidGuard | phone 두owner 전체 경쟁·LC guard 공동검증 필요 |

전 행 공동 PASS는 아니다. LC P3~P6 결과·TMI-217 삭제/보존과 PLAN-012/013 실환경 증거를 연결한 뒤 갱신한다. 기존 fixture `NOT_EXECUTED_REQUIRES_IMPLEMENTATION` 문자열을 덮어써 생산자 원본을 변경하지 않는다. C/삭제·과거owner·purge 미실행을 오프라인 통과로 대체하지 않는다.
