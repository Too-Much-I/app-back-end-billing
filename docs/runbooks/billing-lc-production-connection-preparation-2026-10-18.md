# Billing–LC 운영 연결 설정 준비 — 2026-10-18

## 1. 5줄 결론

1. Billing 운영 서비스 prod:3을 기준으로 미배포 태스크 prod:4를 등록하고 전 필드 readback 일치를 확인했다.
2. 기존 권한으로 읽을 수 있는 LC 공개키·환불 signer·TLS Secret 참조5개와 production 환경/scope/예정port8443을 추가했다.
3. 실행 서비스는 prod:3 실행1/대기0 그대로다. receiver·환불·삭제는 OFF이며 DNS/ALB/IAM/DB 변경은 없다.
4. LC는 prod:8 실행 중이며 prod:9가 미배포 준비본이다. LC 최신 인계 §9상 수신ON은 기존 시험 목록 확정 및 전환 준비를 요구하므로 임의 기동하지 않는다.
5. 실제 TLS 연결/공동 인증 검증은 미완료다. prod:4는 연결 설정 보관용이지 즉시 배포 가능한 완성본이 아니다.

## 2. 준비한 값

| 항목 | 내용 |
| --- | --- |
| 추가 Secret 참조 | BILLING_WORKLOAD_LEARNING_CORE_PUBLIC_JWK_SET, BILLING_PAYMENT_REFUND_PRIVATE_JWK, BILLING_WORKLOAD_TLS_PKCS12_BASE64, BILLING_WORKLOAD_TLS_KEY_STORE_PASSWORD, BILLING_EXPECTED_TLS_CERT_SHA256 |
| workload 환경 | BILLING_WORKLOAD_JWT_ENVIRONMENT=production |
| 예정 workload port | BILLING_WORKLOAD_JWT_PORT=8443 |
| scope | BILLING_TRIAL_EXPECTED_CONSUMER_SCOPE_ID=tosunsaeng-billing-prod |
| 명시 OFF | Identity/LC receiver, refund processing/publisher, exam deletion |
| 보존 | 이미지·실제8082/8083 port mapping·entrypoint/command·역할·기존 환경변수와 Secret 참조 |

Secret 값은 태스크 JSON에 넣지 않고 JSON-key 참조만 사용했다. 기존 execution role의 exact Secret ARN GetSecretValue 허용 및 AWSCURRENT 존재를 대조했다. 이번에 비밀값을 출력하거나 변경하지 않았다. metadata 확인 전 Secret 필드명 확인에 사용한 읽기는 프로세스 메모리에서만 처리했다.

## 3. 실제 연결이 아직 아닌 이유

- 현재 이미지의 Dockerfile은 Java 직접 실행이다. PKCS12 base64를 파일로 만드는 작업은 자동으로 실행되지 않는다. 승인된 `scripts/ops/billing-workload-entrypoint.sh`에 준하는 명시 시작 명령·운영 host/DB/fingerprint 검증 연결이 필요하다. 준비본에는 이를 아직 넣지 않았다.
- Billing connector는 receiver가 모두OFF면8443을 만들지 않는다. port 설정값만으로 TLS 성공을 주장하지 않는다. 실제 port mapping/TG 서비스 연결/DNS/exact ALB route와 발신 대역 제한도 후속이다.
- billing-prod-workload-8443와 lc-prod-access-events-8444는 준비된 HTTPS TG지만 조회 당시 LoadBalancerArns가 모두 비어 있었다. LC 실행 서비스는8080만 연결돼 있다.
- Billing→LC 환불 origin은 LC 담당의 확정된 HTTPS endpoint를 받아야 한다. 앱용 host를 임의로 대신 넣지 않았다.
- LC 최신 문서의 AccessEventRolloutGate는 paid/saga/signer/receiver/owner·production 승인·sealed runtime/index를 요구한다. 기존 시험 목록 최종 확정을 제외한 상태로 수신만 먼저 켜는 것은 별도 설계/승인 없이 하지 않는다. 기존 구앱 무료 생성을 보장하는 전환 순서를 먼저 정해야 한다.

## 4. 안전한 다음 작업

1. LC 담당과 기존 시험 목록 확정/구앱 유지 전환 순서 및 환불 HTTPS origin을 확정한다. 다른 스레드에 메시지는 보내지 않았다.
2. Billing 로컬 운영 환불 지원 코드를 검증된 이미지로 전달할 준비를 한다. 현재 prod:4 이미지는 기존 배포 이미지라 새 지원 코드를 포함하지 않는다.
3. 최신 서비스 설정과 준비본을 다시 대조하고 TLS 시작 명령/8443 port·TG·DNS/ALB의 exact 변경을 승인 후 적용한다. 기능 ON은 준비 검증 후 별도다.

ECS family 최신 revision만 가져오는 외부 배포 도구는 준비본을 선택할 수 있으므로, 후속 배포에서는 현재 실행 revision과 명시 준비 revision을 비교해야 한다. 이 저장소에는 조회 시 .github workflow 디렉터리가 없었으며 외부 배포 자동화 전체를 조사한 것은 아니다.

## 5. 검증과 변경 범위

- AWS 현재 서비스/태스크/TG/역할/Secret metadata 읽기, 새 task definition register1회, environment/secrets는 이름 기준 정렬해 전 필드 readback 검증.
- 실행 Billing taskDefinition/loadBalancers 불변 및 실행1/대기0 확인. LC 서비스 수정·재배포 없음.
- 제품 코드 미변경으로 Gradle 재실행 없음. 직전 운영 gate 코드의1838개 통과를 이번 연결 검증으로 재사용하지 않는다.
- 문서 diff 검사, 기존 dirty 보존, commit/push 없음. 증거 `/private/tmp/billing-prod-task4-prepared-20261018.png`.
