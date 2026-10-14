# TMI-212 공개 sync 실제 서비스 연결

## 1. 결론

- 공개 sync가 worker에서 생성한 동일 PaymentSyncService 객체를 Spring bean으로 사용한다. API용 key/app 설정을 따로 만들지 않는다.
- 기존 sync ON 무조건 기동 실패를 worker flag·recovery fence와 실제 pipeline 구성 검증으로 교체했다.
- 기본 sync/worker OFF, 외부 API·DB schema·지급/환불 정책 불변이다.
- 구조적 기동 준비는 실Store/운영 준비의 증명이 아니다. 환불/유료 입장 준비 전 판매 보류는 유지한다.
- 5년 정리·Slack·정기조회·실제 삭제ON을 새 필수 기동 조건으로 추가하지 않는다.

## 2. 구성과 실패 처리

`billing.payment.sync-enabled=true`이면 `publicPaymentSyncService` bean이 실제 `PaymentProviderWorkerRuntime`에 의존한다. runtime은 기존 foundation/recovery/purchase/sync/cursor/reference/shared-budget schema 및 key/app/catalog/HTTP client 설정 검사를 거쳐 생성된다. 검증한 runtime의 service를 그대로 반환하므로 준비 여부를 선언하는 `ready=true` 속성이 없다. scheduler-only runtime은 service를 노출하지 못한다.

`PublicSecurityConfig`는 sync ON에서 worker.enabled와 recovery.fence-required를 요구하며 기존 public connector/JWT/limiter/transaction 설정 검사도 유지한다. worker OFF/누락은 실패, schema drift·budget 미설치·잘못된 key/catalog/client 구성도 pipeline 생성 실패로 이어진다. 검증 과정은 runtime DDL이나 외부 provider 조회를 수행하지 않는다.

worker는 기존 ApplicationReadyEvent 이후 시작한다. ingress/webhook receiver flag는 기존대로 독립이며 앱 sync 요청에서 직접 provider를 호출하지 않는다. 앱 요청은 기존 DB 접수/202·Retry-After:5, 반영 완료 거래는200으로 수렴한다. worker 일시 장애 시 대기/재시도와 기존 운영 알림을 사용한다. 정상 기동이 provider credential의 실제 유효성을 증명하지는 않는다.

## 3. 활성화 전 운영 확인

명시 schema 설치·같은 RC project의 공통 budget DB·환경/Store/app/catalog/key 일치·Identity 사용자 토큰·ALB ingress·실Store sandbox와RC 검증을 확인한다. 알림/운영자 권한·재개 절차·복원 검증은 배포 checklist로 관리한다. 환불 processor/revoke와 paid admission 준비를 확인한 뒤 별도 판매 승인을 받는다. 이번 구현은 환경변수 활성화·실키 주입·실제 배포가 아니다.

## 4. 테스트 범위

실제 ApplicationContextRunner로 pipeline과 public service bean의 동일 객체 연결을 확인한다. 실제 local replica-set schema로 정상 기동, shared-budget 미설치/cursor drift 실패를 검사한다. ApplicationReady를 발행하지 않아 production transport를 외부로 dispatch하지 않는다.

MockMvc controller에는 실제 구성한 service와 실제 rate limiter/DB를 연결하고 인증 완료된 테스트 principal을 주입한다. fake provider를 사용하는 account worker로202→검증·지급→200, 같은 요청 중복·한 번 지급·탈퇴 후403을 확인한다. 이 통합 테스트 자체는 실제 JWT 서명/네트워크 E2E가 아니며 기존 controller security/JWT 회귀와 함께 실행한다.

## 5. 근거

- [구성](../../src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentProviderWorkerConfiguration.java)
- [공개 보안](../../src/main/java/web/tosunsaeng/billing/global/config/security/PublicSecurityConfig.java)
- [DB·MVC 회귀](../../src/test/java/web/tosunsaeng/billing/domain/payment/sync/PaymentSyncIntegrationTest.java)
