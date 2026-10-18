# Identity 탈퇴 복구 테스트 준비 요청

## 5줄 결론

1. Billing snapshot/feed 코어와 JWT client는 구현돼 있다.
2. 2026-10-17 ECS 조회에서 Identity test47에 탈퇴 복구 플래그/전용 수신키 주입이 확인되지 않았다. 다른 설정원까지 OFF임을 입증한 것은 아니다.
3. Billing test13에도 `BILLING_WORKLOAD_PRIVATE_JWK` 주입이 없어 키 준비가 필요하다.
4. 기존 탈퇴 이벤트 발신 키와 환불 LC 발신 키를 임의 재사용하지 않는다.
5. 이번 요청은 준비 확인용이며 Identity 코드·배포·DB 변경이나 기능 활성화를 수행하지 않았다.

## 필요한 준비

- Identity: `app.withdrawal-recovery.runtime-enabled`, `snapshot-jobs-enabled`, HTTP 및 source 준비 확인. runtime/snapshot 설정은 현재 application.yml의 WITHDRAWAL_RECOVERY 별칭과 동일하다고 가정하지 말고 실제 바인딩을 확인한다.
- `WITHDRAWAL_RECOVERY_HTTP_ENABLED`, `WITHDRAWAL_RECOVERY_WORKLOAD_PORT`, TLS certificate/private-key 파일 및 ALB 전용 경로 준비.
- `/internal/v1/billing/withdrawals/snapshots`, `/snapshots/status`, `/snapshots/page`, `/feed/page` 네 POST. 마지막 세 suffix도 동일 namespace 아래다. 공개 앱 port 거절 유지.
- Billing 테스트 공개 JWK만 Identity의 `WITHDRAWAL_RECOVERY_BILLING_PUBLIC_JWKS`에 등록. issuer `urn:tosunsaeng:workload:billing:test`, audience `tosunsaeng-identity:withdrawal-recovery:test`를 현행 계약과 대조한다. 비밀키는 전달하지 않는다.
- 실제 source environment/stream ID와 snapshot 생성 가능한 DB/index·단일 ACTIVE 상태, HTTPS origin을 전달한다. 앱의 SANDBOX와 source 환경을 임의 치환하지 않는다.
- Billing은 대응 private JWK 주입·host/env·schema 확인 후 최초 snapshot을 시작한다. 기존 control에 baseline이 없으면 구매 차단을 수반할 수 있어 점검 시점을 별도 조율한다.

## 미확인·다음 순서

새 제품 정책은 없다. 전용 키 생성·Secret 권한/경로 개방은 실제 적용 전 승인 범위를 확인한다. 양방향 실JWT·원문 누락 snapshot·feed 추격·중복/응답 유실 검증 후 운영 적용을 판단한다. 기존 탈퇴 push/구매/자동삭제 설정을 이번 준비로 임의 변경하지 않는다.

근거: Identity RecoveryRuntimeConfiguration/RecoveryJobsConfiguration/RecoveryWorkloadConfiguration/application.yml 읽기, Billing RecoveryHttpConfiguration/WorkloadJwtProperties, ECS test47/test13 읽기 점검. 운영 서버와 실제 사용자 데이터는 미조회·미변경.
