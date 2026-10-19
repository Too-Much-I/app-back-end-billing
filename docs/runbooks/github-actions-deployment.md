# Billing 브랜치별 자동 배포

- main push → tosunsaeng-billing-prod-service, develop push → tosunsaeng-billing-test-service. 수동 실행도 두 브랜치만 허용한다.
- 테스트/bootJar/workload image smoke 후 현재 ECS task definition의 이미지만 digest로 교체한다. Secret 참조, 환경변수, 역할, 포트, health, 기능 스위치를 보존한다. schema 설치/DB 변경/기능 ON은 자동 실행하지 않는다.
- ECR은 기존 prod도 사용하는 tosunsaeng-billing-test 저장소를 유지한다. 태그는 환경·commit·run·attempt 조합이며 latest를 사용하지 않는다.
- GitHub 변수 AWS_TEST_ROLE_ARN/AWS_PROD_ROLE_ARN이 필요하다. 미설정 시 AWS 접근·배포 전에 실패한다.

## AWS 연결 선행 조건

Billing 전용 test/prod OIDC 역할을 분리한다. issuer token.actions.githubusercontent.com, audience sts.amazonaws.com,
subject `repo:Too-Much-I@288205644/app-back-end-billing@1344377675:ref:refs/heads/develop` 또는 끝이 `main`인 값을 역할별 exact 제한한다.
이 저장소는 GitHub immutable subject를 사용한다. 이름만 포함한 구형 subject로는 인증되지 않으며 wildcard로 완화하지 않는다.
기존 Identity/LC 역할 신뢰를 확대하거나 장기 AWS 액세스 키를 GitHub에 저장하지 않는다.

권한은 해당 ECR push/read, ECS 서비스/task 읽기·새 task definition 등록·대상 서비스 UpdateService,
기존 해당 환경 task/execution role의 iam:PassRole(ecs-tasks.amazonaws.com 조건), ALB target health 읽기로 제한한다.
Secret 원문 읽기·DB·IAM 변경·타 서비스 UpdateService는 필요하지 않다.

## 2026-10-19 연결 기록

- Billing 전용 tosunsaeng-billing-test-github-deploy / tosunsaeng-billing-prod-github-deploy 역할 생성, 각 develop/main exact trust·인라인 BillingDeployOnly 정책 재조회 일치 확인.
- 기존 실행 역할만 PassRole, 환경별 exact ECS 서비스/태스크 family 제한, 기존 Billing ECR만 push/read. Secret 읽기/DB/IAM 변경 권한 없음. 기존 Identity/LC 역할 변경 없음.
- GitHub AWS_TEST_ROLE_ARN/AWS_PROD_ROLE_ARN 등록·조회 확인. [PR32](https://github.com/Too-Much-I/app-back-end-billing/pull/32) develop 병합, [첫 test 실행](https://github.com/Too-Much-I/app-back-end-billing/actions/runs/39199919048) 시작.
- test14/prod17 기존 서비스 healthy 운영·각1개 container/X86_64 확인. test 기존 command는 /app/billing.jar를 실행한다. 두 서비스 배포 circuit breaker/rollback 활성화 확인.
- CloudShell에서 test DB 직접 읽기는 TLS handshake 오류로 실패했다. 인증/TLS를 완화하지 않았으며 DB 호환 확인 완료로 기록하지 않는다.
- [main 승격 PR33](https://github.com/Too-Much-I/app-back-end-billing/pull/33)은 최초 draft로 준비하고 test 배포 통과 후 병합했다.
- 첫 실행은 테스트/build 성공 후 OIDC subject 불일치로 실패해 ECS 변경 없음. GitHub OIDC API의 immutable subject prefix를 확인해 양쪽 역할의 동일 저장소·브랜치 식별 형식만 교정했다. 권한은 불변이다.
- 재실행 attempt2는 04:58:51 UTC SUCCESS: test15 배포·digest 일치·ECS/ALB healthy·HTTPS 무인증401까지 통과했다. DB 변경 없이 기동 검사도 통과했다.
- PR33을 04:59:09 UTC main에 일반 merge 완료(`70dac8efb26e1518b7dbc943f394f34a7e0883cf`). 이로써 main도 최신 배포 코드와 workflow를 포함하며 운영 자동배포 최종 결과는 후속 확인한다.
- [첫 prod 실행](https://github.com/Too-Much-I/app-back-end-billing/actions/runs/39202204049)은 최종 즉시 상태검사 실패. 실제 prod18은 단일 COMPLETED/running1/pending0·예상 이미지 일치·8083/8443 healthy·HTTPS401, image 외 task설정 동일을 별도 확인했다. ECS waiter와 rolloutState 반영 시점 차이에 대응해 최종 검사에 최대180초 제한 재확인을 추가했다. 이미지 불일치/rollback은 여전히 실패한다. 후속 실행 결과는 별도 확인한다.

## 다음 변경 시 사용법

1. 기능 변경은 develop 대상으로 검토·병합한다. Actions에서 test 배포 성공을 확인한다.
2. 운영 반영할 때 develop → main PR을 병합한다. main push로 prod 배포가 실행된다.
3. 실패하면 실행 로그와 ECS rollback 여부를 확인한다. schema 불일치는 명시 maintenance 대상이며 검사 해제나 기능 OFF로 숨기지 않는다.
4. 문서만 바꾸는 결과 기록은 실제 diff가 문서뿐임을 확인한 뒤 `[skip ci]`로 불필요한 재배포를 생략할 수 있다. 제품·workflow 변경에는 사용하지 않는다.

브랜치별 역할은 신규 장기키 없이 GitHub OIDC를 사용한다. 두 브랜치의 push는 실제 배포를 일으키므로 작업 중 수동 ECS 배포와 겹치지 않게 한다. 애플리케이션 구매/환불 E2E는 인프라 배포 성공과 별도다.

운영과 test DB schema 호환은 기존 fail-fast 검사로 유지하며 workflow에서 자동 이관하지 않는다.
수동 변경과 동시 배포는 task definition 재확인으로 검출하지만 AWS UpdateService 자체는 CAS API가 아니므로 배포 중 수동 변경을 피한다.
