# Billing → LC 운영 인증·TLS 준비 회신

> 후속 로컬 구현: [환불 운영 실행 조건 보완](../runbooks/refund-production-runtime-readiness-2026-10-17.md)에서 prod/production/PRODUCTION/운영 Billing DB/fence 조합을 허용했다. 아래 test-only 관측은 배포된 prod:3 기준으로 여전히 유효하며 새 코드 배포/기능ON/LC 공동검증 완료가 아니다.

## 1. 5줄 결론

1. 운영 환불 공개 JWK set을 `/private/tmp/billing-production-refund-public-jwks-20261017.json`으로 준비하고 RSA3072·SPKI 지문·verify 전용·비밀필드 부재를 검증했다.
2. 운영 scope 연결 기준은 `tosunsaeng-billing-prod`다. Identity 운영 scope와 일치하며 Billing 환불은 trial expected scope를 재사용한다. 현재 Billing 태스크에는 아직 주입되지 않아 실제 발신 body 검증 완료가 아니다.
3. 운영 workload 예정 origin은 `https://billing-workload-prod.to-teacher.com`이다. 인증서만 준비됐고 DNS/ALB 전달/서비스8443 연결은 미완료다. 아직 호출하지 않는다.
4. LC 공개키는 Billing 운영 Secret에 등록됐고 LC 인계 지문과 일치한다. 그러나 실행 태스크에 미주입·receiver OFF라 실수신 준비 완료는 아니다.
5. 현재 확인한 Billing 환불 기동 검증은 test/SANDBOX만 허용한다. 운영 환불 활성화에는 별도 코드 보완·검증이 필요하며 이 회신으로 활성화하지 않는다.

## 2. LC에 전달할 값

| 항목 | 값·상태 |
| --- | --- |
| BILLING_ACCESS_EVENTS_BILLING_PUBLIC_JWK_SET | 위 JSON 파일 전체. 공개키만 있으며 private JWK를 전달하지 않는다 |
| Billing 환불 kid | billing-production-refund-workload-jwt-20261017 |
| Billing 환불 SPKI SHA256 | 7586c3b985f3df5acdea7ae06d3c3a6390abe3d405c428c7931bbc6315c40a76 |
| BILLING_ACCESS_EVENTS_CONSUMER_SCOPE_ID | tosunsaeng-billing-prod — 운영 연결 기준, 양측 주입/발신 검증은 후속 |
| BILLING_ACCESS_EVENTS_ENVIRONMENT | production |
| BILLING_BASE_URL | https://billing-workload-prod.to-teacher.com — 예정 origin, 연결 미완료 |
| BILLING_WORKLOAD_JWT_ENVIRONMENT | production |
| LC 공개키 등록 | tosunsaeng/prod/billing/learning-core-workload-public-jwks에 등록 확인 |
| LC kid | lc-production-billing-20261017-r1 |
| LC SPKI SHA256 | 01a343c71331a9420ef6dbc8eceec006de85197b7f85f07febbf59e4dfd93b73 |

공개키 파일은 임시 디렉터리의 인계 산출물이다. LC가 검증 후 자신의 승인된 설정 저장소로 등록해야 하며 Git에 실제 키 원문을 추가하지 않았다. 앱용 billing-prod 주소를 서버용 origin 대신 사용하지 않는다.

## 3. 확인 근거와 필요한 작업

- AWS 환불 Secret의 저장 public set이 private key의 공개 부분과 일치하고 kty/alg/use 및 private sign/public verify 구분을 검사했다. 출력/로컬 저장은 공개7필드만 허용했다. Node crypto로 생성 파일을 다시 파싱해 지문 일치 확인.
- LC 운영 Secret의 RSA3072/RS256/sig/key_ops verify/비밀필드 부재 및 SPKI 지문을 독립 확인했다.
- Billing prod:3에는8082/8083만 있고 LC receiver=false, LC 공개키 Secret 참조 없음. 기존443 listener의 workload-prod host rule0개·DNS 미해결 확인. 앞서 ACM 인증서는 해당 SAN 포함 발급/추가 완료했지만 이것을 workload 통신 완료로 간주하지 않는다.
- Identity prod:13의 PHONE_ELIGIBILITY_BINDING_CONSUMER_SCOPE_ID는 tosunsaeng-billing-prod. Billing RefundRuntimeConfiguration은 TrialEligibilityProperties.expectedConsumerScopeId를 환불 body의 scope로 연결한다. Billing의 실제 BILLING_TRIAL_EXPECTED_CONSUMER_SCOPE_ID 주입과 LC의 동일 값 설정을 배포 시 함께 대조해야 한다.
- PaymentRefundConfiguration.validate는 test profile/test workload/SANDBOX/test DB/fence 조건 밖의 processing ON을 거절한다. 운영으로 이름만 치환하거나 gate를 우회하지 않는다. 운영 지원 보완은 별도 구현 작업이다.

## 4. 승인·위험·다음 순서

이번은 요청된 공개 인계 자료 제공이며 서버/Secret/DB/LC 저장소 변경, 메시지 발송, 배포, 공동 E2E가 아니다. 기존 구앱·무료시험 경로를 변경하지 않았다.

다음은 Billing workload DNS·8443/TLS·exact ALB 허용 경로·LC receiver 설정 준비와 LC 수신8444 준비를 각 담당자가 승인된 범위로 진행하는 것이다. 환불 production gate 보완과 정상서명/잘못된목적/무인증/중복/응답유실 공동 검증 전 기능 ON 완료로 기록하지 않는다. Identity 구매 scope 준비와 앱 결제 활성화도 별도 미완료다.

## 5. 검증 범위

제품 코드 미변경으로 Gradle 미실행. AWS 읽기 대조·공개키 Node 검사·문서 diff 검사 수행. 기존 dirty 보존, commit/push 없음. LC 요청 원문: `/Users/msde76/app-back-end-learning-core/docs/codex/LC_PROD_BILLING_CONNECTION_HANDOFF_2026-10-17.md`.
