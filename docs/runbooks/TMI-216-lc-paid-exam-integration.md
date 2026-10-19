# TMI-216 — LC 유료 시험 런타임 연결·이용 증거

## 1. 5줄 결론

1. TMI-215를 commit `34ad2f1`, [PR #19](https://github.com/Too-Much-I/app-back-end-billing/pull/19), develop merge `5d16ba4`로 반영하고 `feat/TMI-216-lc-paid-exam-integration`에서 작업했다.
2. Billing의 계정 기반 paid resolver를 기존 reserve/confirm/cancel/status/expiry에 연결했다. 기존 command는 결제 계정이 나중에 생겨도 legacy 경로를 유지하며 source가 있는 예약은 legacy writer로 처리하지 않는다.
3. 기존 상태 이벤트 v1에서 source별 귀속·inbox·상태·current guard·구매 이용 원장을 단일 Transaction으로 처리한다. 원 시험의 늦은 완료는 새 guard를 해제하지 않는다.
4. 같은 read-only snapshot에서 유료 기간, MEMBER 무료권 수량·재응시, MEMBER/예약 대기를 함께 조회한다. 기본 OFF이며 schema 설치·배포·실환경 활성화는 수행하지 않았다.
5. Billing 실Mongo/서명 JWT MVC 검증과 LC 전체 완료는 다르다. [28개 대응표](TMI-216-shared-state-verification.md)의 LC P3~P6·TMI-217·공동 E2E는 남아 있다.

## 2. 반드시 읽어야 하는 내용

### 구현 경계

- `PaidExamRouter`: retained paid receipt → legacy command → payment account 순서로 writer를 선택한다. paid 오류 뒤 무료 fallback은 없다. lifecycle은 요청 user의 계정 유무 대신 실제 Reservation source로 dispatch하고, 실제 owner/digest/상태 검증은 Transaction 안에서 수행한다.
- `PhoneContinuationService`: 유료 전환으로 재응시가 차단된 원 trial을 continuation으로 다시 광고하지 않는다.
- `ReservationLifecycleService`: confirm/cancel과 scheduler가 호출하는 expiry를 paid runtime에 연결한다. runtime OFF에서 source-aware 예약을 구 무료 lifecycle로 처리하지 않고 503으로 차단한다. status는 기존 body `userId/operationId` 및 MEMBER recovery의 읽기 전용 관측을 유지한다.
- `PaidAttemptEventService`: payment recovery barrier·현재 owner/lifecycle fence·source/group/session 관계를 검사한다. payment account 소유 유료/복원권과 trial link/epoch를 분리한다. phone의 과거 source는 기존 exact Session/시간 제한 fence에서만 상태 완료를 허용하며 target이 과거 Session을 대신 보고하지 못한다.
- inbox·group/session 전이·정확한 current guard 해제·`EXAM_STATUS` ledger가 하나의 ClientSession에서 commit된다. duplicate/stale은 소비/복원/이용 원장을 추가하지 않는다. terminal 뒤 반대 terminal이 와도 다시 열지 않는다. confirm 전 projection 미완료는 재시도 가능한 오류다.
- 구매 이용 원장은 purchase/entitlement/event/group/session/status/시각만 연결한다. 문제·답안·음성·채점 원문을 복제하지 않는다. refund 뒤에도 이미 confirm된 exact Session의 완료는 허용하며 실제 refund/revoke writer는 PLAN-012다.
- `PaidExamQueryReader`: free/paid reader와 같은 Spring snapshot에 참여하고 query timeout/행 수 상한·필수 collection 확인을 적용한다. 무료 잔량에 MEMBER grant를 합산하고 소비된 복원권의 재응시 group도 표시한다. source 차단된 원 trial은 재응시 가능으로 표시하지 않는다. MEMBER 증거는 신뢰 issuer/environment/user를 대조한다. 조회로 계정/receipt/guard/원장을 변경하지 않는다.

### 설정·schema

기존 `BILLING_PAID_EXAM_FOUNDATION_ENABLED`, `BILLING_PAID_EXAM_RECOVERY_ENABLED`, `BILLING_PAID_ADMISSION_ENABLED` 기본 false를 유지한다. admission은 foundation/recovery bean, purchase-account MEMBER 증거 writer, 일치하는 environment, LC receiver, AttemptGroup event 수신, 통합 paid reader가 필요하다. startup이 통과해도 아래 공동 활성화 gate를 통과했다는 뜻은 아니다.

`paid-exam-admission-r3`는 그대로다. `payment-purchase-core-r6`는 기존 r5의 provider/EXAM_OPERATION 원장에 strict `EXAM_EVENT/EXAM_STATUS` variant만 추가한다. `PurchaseCoreSchema.upgradeExamUsageUnderMaintenance`는 exact r4/r5 또는 이미 r6에서만, ALL_BLOCKED와 writer 격리 조건으로 실행한다. 이전 단계 maintenance 재실행도 r5를 손상하지 않는다. 자동 startup DDL·index drop·기존 데이터 재작성은 없다. 실DB에서 실행하지 않았다.

### 외부 계약 유지

새 route·인증 방식·승인 API를 추가하지 않았다. command는 header, status는 body, 기존 AttemptGroup event는 v1이다. MEMBER 대기는 내부503/네필드/Retry-After1, J3는 reserve409 `TRANSITION_CONTEXT_CONFLICT`와 status409 `RESERVE_REJECTED_BEFORE_CREATION`으로 분리된다. `OPERATION_NOT_FOUND`/body 없는 ingress404와 섞지 않는다. 기존 A/R2/simplified fixture bytes/hash는 불변이다.

## 3. 사용자가 결정해야 하는 사항

이번 Billing 연결 구현에 새 제품 정책 선택은 없다. LC P3 이후 증거를 대조하고 TMI-217 삭제·과거 owner·보존 조건, PLAN-012/013을 마친 뒤 별도 배포/활성화 판단이 필요하다. Jira 완료 상태나 공동 수락을 이번에 갱신하지 않았다.

## 4. 위험·미확인 사항

- 계정 없는 Guest와 retained legacy command는 기존 무료 경로다. source-null legacy의 전환 coverage/이관·구 reader/worker drain은 아직 활성화 gate다. 새 strict LC 경로에서 이를 무조건 EXAM_PASS로 추측하거나 payment account/MEMBER 증거를 생성하지 않는다. 이 작업만으로 Guest/legacy 포함 전체 전환이 준비됐다고 해석하지 않는다.
- LC는 읽기 확인 시 HEAD `001b575`(P2/P2.1 merge)였고 CURRENT_STATE에 P3 진행이 기록돼 있었다. LC 마무리 Transaction/worker/MEMBER cleanup·삭제·Summary lifecycle은 여기서 구현하거나 테스트하지 않았다.
- E06-owner는 별도 fixtures-2로 문구 정정했고 2026-10-14 LC 독립/loopback actual-client 재검증 회신으로 해소됐다. 실제 Billing HTTP/TLS/DB 공동 E2E 완료는 아니다. 원 fixtures-1은 보존했다. T04 operation120일 단일 보관 설명 보완은 공동 후속이다.
- 별도 삭제 route/과거 귀속/개인정보 purge는 TMI-217, provider 환불·root 차단·durable revoke는 PLAN-012다. 테스트의 refund 상태 Transaction을 provider 전체 실행으로 보고하지 않는다.
- MockMvc 검증은 실제 Billing decoder/ingress/auth/controller/paid Transaction을 실행하지만 실제 TLS socket·ALB·LC 서버·Store를 실행하지 않는다. 전체 suite의 기존 security/connector 검증과 실환경 E2E도 구분한다.

## 5. 검증·변경 위치

최종 `./gradlew clean test`는 **1,570개, 실패0/오류0/skip0**이다. 신규24개(런타임20·schema1·legacy rollback1·storage error1·phone discovery1)가 전체 수에 포함되며 합산하지 않는다. simplified/R2 오프라인 검사기와 `git diff --check`도 통과했다. `PaidExamRuntimeIntegrationTest`는 replica-set Mongo7.0.14에서 paid event의 원자성/rollback/중복/unknown commit, owner/탈퇴/phone fence, 원 완료와 pending guard, 실제 signed HTTP reserve→confirm→status/replay, MEMBER 보강, J3, expiry 분기, read-only 통합 조회 및 runtime bean 구성을 검증한다. `ReserveMongoIntegrationTest.legacyWriterCannotReplaceSourceAwareGroupWhenPaidRuntimeIsOff`는 구 writer 차단·rollback을, `PhoneContinuationServiceTest.convertedTrialIsNotAdvertisedAsPhoneContinuation`은 종료된 무료 그룹 discovery 제외를 확인한다. `AttemptGroupEventServiceTest.sourceDispatchStorageFailureKeepsTemporaryErrorAndDoesNotWriteInbox`는 저장소 장애의 기존503 분류를 확인한다. `PurchaseCoreIntegrationTest.r5ExamUsageUpgradePreservesLedgerAndRequiresIsolation`은 과거 원장 보존과 명시 이관 조건을 확인한다. 기존 TMI-215 테스트의 setup/helper는 `PaidReservationTestSupport`로 추출했으며 기존 테스트를 삭제하거나 skip하지 않았다.

주요 제품 변경은 `domain/reservation/paid`의 router/event/query/config, 기존 controller/lifecycle/event/query service 연결, session-bound owner fence repository, PurchaseCoreSchema다. 실제 LC/Identity·DB·설정·fixture·Jira·배포를 변경하지 않았다. 별도 작업의 PLAN-012/RevenueCat 문서와 CURRENT_STATE/WORKLOG 이력은 보존한다.
