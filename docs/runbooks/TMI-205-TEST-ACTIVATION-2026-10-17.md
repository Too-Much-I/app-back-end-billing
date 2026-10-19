# TMI-205 테스트 탈퇴 연동 활성화

## 범위

사용자 승인: Billing 새 코드 배포, 전용 ALB 탈퇴 POST 경로 추가, Identity 구매 scope/fence·탈퇴 발신 및 Billing 수신 활성화. 실제 계정 탈퇴는 하지 않고 설정·기동 확인까지만 수행한다. 운영/LC는 변경하지 않는다. 자동삭제는 OFF다.

## 사전 확인

- Billing 실제 기준은 test10이다. 이전 task9 이후 추가된 Identity 전용 public JWK Secret·receiver ON과 휴대전화 경로(priority88)를 보존한다.
- Identity 실제 기준은 test46이다. control writer/capture 및 source binding 준비를 보존하며 capture OFF 구 revision으로 롤백하지 않는다.
- 최신 병합 코드 PR29/develop a432079의 제품 트리로 bootJar와 amd64 Docker 이미지 빌드 성공. 직전 전체 Java21 테스트 1,798개 통과 후 제품코드 변경 없음.
- 이미지 `cleanup-a432079`의 ECR digest: `sha256:24a2aeca36cb6b97ff8bd8b4bf1bd385ae3701623cd682fdaa088cdd26d1d589`. archive 로컬/CloudShell SHA256 일치. ECR scan COMPLETE, findingSeverityCounts 빈 맵 확인(모든 종류의 취약점 부재 보장은 아님).
- 읽기 전용 ECS 진단 exit0: payment_account_cleanup_jobs 0건/validator 없음, payment_recovery_controls 1건/SANDBOX/billing/schemaVersion1/OPEN. 따라서 cleanup reconciliation/purge ON은 현재 기동 조건을 충족하지 않는다. 버전 숫자만 4로 바꾸거나 증거를 발명하지 않는다.

## 적용 진행

- 사용자 적용 직전 승인으로 priority87에 POST `/internal/v1/payments/accounts/withdrawal/events`만 추가. 기존 host `billing-workload-test.to-teacher.com`, source `13.124.57.130/32`, HTTPS8443 target 유지. 기존 규칙 불변 대조 성공.
- Billing test11 등록·배포 요청: test10의 이미지와 BILLING_PAYMENT_WITHDRAWAL_ENABLED=true만 변경. cleanup reconciliation/purge는 OFF 유지. 최종 안정화·Identity 적용 결과는 아래 후속 기록으로 남긴다.

## 후속 적용 기록

- Billing test11의 workload/public 대상 모두 healthy 확인. 이전 test10은 draining으로 전환했다. 헬스 체크 기준은 변경하지 않았다.
- Identity 기존 서명키로 빈 JSON 요청만 전송한 진단: unsigned401/signed400/wrong audience403/expired401, exit0. 인증/TLS/route 검증이며 업무204나 실제 탈퇴 E2E가 아니다. 최초 진단은 mongosh top-level await 문법 오류였고 async wrapper로 수정한 두 번째 실행이 통과했다.
- Identity test47 배포 요청: test46을 복제하고 AUTH_PURCHASE_FENCE_ENABLED, AUTH_PURCHASE_SCOPE_ENABLED, BILLING_WITHDRAWAL_PUBLISHER_ENABLED를 true로, withdrawal origin/audience를 테스트 Billing 전용 값으로 설정했다. 이미지·키·capture·phone 설정은 보존했다. 안정화 결과는 후속 확인한다.
- 별도 제품코드 수정·사용자 탈퇴·데이터 삭제·운영/LC 변경 없음. cleanup reconciliation/purge는 OFF다.
- 최종 Billing test11·Identity test47 모두 ECS rollout COMPLETED, 각각 실행1개/pending0 및 신규 대상 healthy 확인. Identity 구 대상은 정상 draining이다. 공개4API 무인증401 유지. 새 구매 scope는 새 토큰 발급부터 적용된다.
- 최종 task definition 대조: Identity purchase fence/scope·withdrawal publisher=true, Billing withdrawal=true. cleanup reconciliation/purge는 미설정으로 application.yml의 false 기본값 적용. 정리 플래그는 켜지 않았다.
- Identity 신규 시작 로그 요약: Started 확인, 확인한100개 로그에 startup failure/ERROR 없음. 임시 진단 task 정의3개 INACTIVE 처리, 로그 보존. diff --check 통과. 실제 로그인 토큰/탈퇴 E2E는 사용자 요청대로 미실행.

## 남은 경계

정리 r2 schema·제어 버전4의 정식 유지보수 적용, cleanup 재확인 활성화와 실제 전용 계정 탈퇴/구매 차단 E2E는 별도다. 이번 설정 활성화를 실제 탈퇴 journal/delivery 원자성·Billing204·자동삭제 성공으로 해석하지 않는다. 복구 snapshot/feed는 양방향 키/receiver/원천 기준점이 별도 준비돼야 한다.
