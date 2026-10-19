# Billing 브랜치별 자동 배포

- main push → tosunsaeng-billing-prod-service, develop push → tosunsaeng-billing-test-service. 수동 실행도 두 브랜치만 허용한다.
- 테스트/bootJar/workload image smoke 후 현재 ECS task definition의 이미지만 digest로 교체한다. Secret 참조, 환경변수, 역할, 포트, health, 기능 스위치를 보존한다. schema 설치/DB 변경/기능 ON은 자동 실행하지 않는다.
- ECR은 기존 prod도 사용하는 tosunsaeng-billing-test 저장소를 유지한다. 태그는 환경·commit·run·attempt 조합이며 latest를 사용하지 않는다.
- GitHub 변수 AWS_TEST_ROLE_ARN/AWS_PROD_ROLE_ARN이 필요하다. 미설정 시 AWS 접근·배포 전에 실패한다.

## AWS 연결 선행 조건

Billing 전용 test/prod OIDC 역할을 분리한다. issuer token.actions.githubusercontent.com, audience sts.amazonaws.com,
subject repo:Too-Much-I/app-back-end-billing:ref:refs/heads/develop 또는 main을 역할별 exact 제한한다.
기존 Identity/LC 역할 신뢰를 확대하거나 장기 AWS 액세스 키를 GitHub에 저장하지 않는다.

권한은 해당 ECR push/read, ECS 서비스/task 읽기·새 task definition 등록·대상 서비스 UpdateService,
기존 해당 환경 task/execution role의 iam:PassRole(ecs-tasks.amazonaws.com 조건), ALB target health 읽기로 제한한다.
Secret 원문 읽기·DB·IAM 변경·타 서비스 UpdateService는 필요하지 않다.

현재는 워크플로 구현 단계다. 역할 실제 존재/권한·두 환경 ECS 호환·GitHub 변수 설정·첫 실행 성공을 확인하기 전 자동배포 완료로 보고하지 않는다.
운영과 test DB schema 호환은 기존 fail-fast 검사로 유지하며 workflow에서 자동 이관하지 않는다.
수동 변경과 동시 배포는 task definition 재확인으로 검출하지만 AWS UpdateService 자체는 CAS API가 아니므로 배포 중 수동 변경을 피한다.
