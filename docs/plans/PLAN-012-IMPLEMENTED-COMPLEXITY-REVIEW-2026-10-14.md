# 결제 기반 코드·PLAN012 복잡성 검토

> 2026-10-14 최종 선택: 구현된 schema 반복검사와 RC 응답 후 permit검사는 유지한다. 아래 리팩터링 후보는 현재 작업 대상이 아니다. 미구현 PLAN012 스위치만 처리+정리/LC전달의 두 개로 승인되어 내부 구현 규격 §7에 반영됐다. 아래 과거 추천을 코드 변경 승인으로 해석하지 않는다.

- 날짜: 2026-10-14. 정적 검토, 변경 제안이며 구현 승인/성능 실측 아님. 동시 TMI217 작업은 수정하지 않는다.

## 1. 결론

1. PLAN012 환불 엔진은 아직 구현 전이다. PurchaseCore의 REVERSAL_REQUIRES_PLAN_012 경로와 기존 결제 기반 코드를 구분해야 한다.
2. 같은 schema를 여러 객체 생성자에서 반복 검증하는 구조는 구성 진입점별 한 번으로 모을 수 있다. 검증 내용 자체는 줄이지 않는다.
3. RevenueCat 호출량 permit은 acquire/전후 validate/release가 각각 DB Tx다. 특히 응답 후 validate는 금융 소유권 검증과 다른 운영 제한이다. 완화 후보지만 전역 인증 중단 의미를 먼저 구분해야 한다.
4. 신규 환불 processing/fanout 스위치는 하나로 묶고 publisher만 독립시키는 설계가 가능하다. 이미 구현된 변경으로 취급하지 않는다.
5. 업무 CAS/멱등성/불변 outbox·다른 권리 보존은 유지한다. 복잡하다는 이유만으로 금융 보호를 제거하지 않는다.

## 2. 선택지와 감수할 위험

| 대상 | 현재 근거 | 단순화 후보 | 위험/조건 |
| --- | --- | --- | --- |
| schema 검사 배치 | PaymentSyncService, PaymentSourceWorker/Work, RetentionWorker, ReferenceRetentionWorker 생성자가 같은 schema를 반복 verify | Spring runtime 구성·독립 CLI/maintenance 등 각 진입점에서 필요한 schema 집합을 한 번 검증하고 의존 객체에 전달 | 별도 진입점을 빠뜨리면 미검증 실행. 진입점 기동 테스트와 직접 생성 경로 통제가 필요. 요청마다 반복한다고 과장하지 않음 |
| RC 응답 후 permit validate | RevenueCatBudgetedTransport.get의 acquire→validate→HTTP→validate→release. ProjectBudget.validate는 읽기뿐 아니라 version 쓰기 Tx | 발신 전 공통 quota/인증차단 검사와 release는 유지하고, 성공 응답은 permit 종료만으로 버리지 않도록 응답 후 운영 검사를 축소 검토 | 다른 요청의 인증차단 직전에 이미 보낸 요청 결과가 반영될 수 있음. 실제 응답 인증/내용/owner/recovery epoch 검증은 유지. 긴 호출로 quota 동시성 slot이 만료되는 문제는 HTTP deadline과 별도 검사 필요. 침해 시 결과 차단을 맡고 있다면 대체 확인 전 제거 금지 |
| 신규 flag 3개 | PLAN012 §7 processing/fanout/publisher | refund 처리+후속정리 1개, LC publisher 1개로 축소 | 정리만 별도 중단하려면 처리 전체를 멈춰야 함. 활성화/장애 절차·독립 LC 수신 순서 유지. 아직 설계 후보 |

첫 항목은 우선 권장한다. 둘째는 전후 validate 전체 삭제가 아니라 응답 후 운영 제한과 금융 검증을 분리하는 좁은 검토다. 성능이 문제라는 실측은 없으며 구현된 안정 코드를 환불 개발의 필수 선행 리팩터링으로 만들지 않는다. 세 번째는 아직 없는 설정 조합을 줄이는 데 유효하다.

## 3. 유지할 복잡성

PaymentSourceWork의 owner/leaseVersion/generation과 guard 쓰기는 여러 instance 및 재개 후 오래된 worker가 결과를 덮어쓰는 것을 방지한다. 단일-thread scheduler여도 서버가 하나라는 보장은 없으므로 삭제하지 않는다. fresh/running 교차 조회는 신규 접수와 재시도 기아 방지이므로 단순한 한 목록 선착순으로 바꿀 근거가 부족하다.

PaymentReviewOperator의 권한/대상 version/멱등 receipt는 운영자 중복 실행을 막는다. 관리 화면을 만들지 않는 것과 복구 감사/대상 검증을 없애는 것은 다르다. 전역 호출 제한을 process-local로 바꾸면 같은 RC 프로젝트의 환경별 quota 합산이 깨질 수 있으므로 이번에는 권하지 않는다.

## 4. 사용자 선택 및 미확인

지금은 검토만 요청받았다. 위 후보는 추가 승인 없이 제품/계약에 적용하지 않는다. 응답 후 permit 검사 완화는 인증 중단의 실제 운영 의미와 HTTP timeout을 확인한 뒤 정확 범위를 정한다. 전체 Billing 감사나 실환경 성능 분석을 수행한 것은 아니다.

## 5. 근거

- `src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentSourceWork.java`
- `src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentReviewOperator.java`
- `src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentProviderWorkerRuntime.java`
- `src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentProviderWorkerConfiguration.java`
- `src/main/java/web/tosunsaeng/billing/domain/payment/provider/RevenueCatProjectBudget.java`
- `src/main/java/web/tosunsaeng/billing/domain/payment/provider/RevenueCatBudgetedTransport.java`
- [PLAN012 내부 규격](PLAN-012-IMPLEMENTATION-SPEC-R1.md)

## 6. 검증 범위

### 후속: RevenueCat 응답 후 permit 검사 확인

2026-10-14 정적 코드·기존 테스트 확인. HTTP는 connect2초/전체 대기5초이고 permit은30초다. 따라서 평상시 느린 HTTP만으로 permit 만료가 흔하다는 근거는 없다. 긴 process 정지/시계 변화 등 예외에서 발생할 수 있다. 기존 `lateResponseIsNotReturnedAndDoesNotKeepSlot`, `anotherCallsAuthFailureFencesAlreadyRunningResponse` 테스트가 늦은 결과 및 다른 호출401/403 이후 진행 중200응답의 거절을 명시한다. 검사는 단순 중복이 아니라 운영 차단 보장이다. 다만 검사 직후부터 실제 금융 commit까지 전역 중단을 원자 보장하는 장치는 아니다.

좁은 완화는 가능하다: 발신전 acquire/validate와 본인 응답401/403의 blockAuth, finally release를 유지하고 정상200응답 뒤의 permit validate만 생략한다. 이 경우 다른 요청이 전역AUTH차단을 설정하기 전에 이미 발신한 정상 응답은 처리될 수 있다는 정책을 명시해야 한다. 신규발신 차단은 유지한다. permit 만료를 거래 진위 판정으로 쓰지 않되 별도 작업lease/recovery epoch가 만료되면 반영은 여전히 거절된다.

검증 경로는 HTTP TLS/고정host·5초/크기 제한 → ReadClient 상태/Content-Type → decoder 환경/customer/original_customer/product/store/status → AccountLookupWorker renew 및 core.receive/apply의 work.guard/recovery fence다. 이 검증을 생략하는 제안이 아니다. finally release의 DB 실패도 응답 반환을 실패시킬 수 있으므로 post validate 하나를 줄여도 DB장애와 완전히 분리되지 않는다. 기존 테스트 실행은 하지 않았으며 새 동작으로 PASS라고 주장하지 않는다.

권장: 호출당 DB Tx 하나를 줄이는 선택지이지 필수 수정은 아니다. 위험은 진행중 정상응답에 대한 짧은 유예이고, 금융 중복/귀속검사를 제거하는 위험은 아니다. 사용자 수락 전 코드/테스트/기존 보장 변경 없음. 승인 시 두 기존 테스트의 기대값을 좁은200응답 정책에 맞추고401/403 전역차단/이후 발신차단/작업lease유실/decoder거절/다른slot 보존 회귀를 함께 실행한다.

정적 읽기/문서 검사만. 코드·DB·배포·Jira·외부 시스템·다른 저장소 변경 없음, Gradle 미실행. 기존 동시 변경 보존. 다음은 사용자 선택 후 좁은 변경 범위/회귀 확정이다.
