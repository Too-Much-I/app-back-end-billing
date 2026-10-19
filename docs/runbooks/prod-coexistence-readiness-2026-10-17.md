# 세 서버 prod 배포·구앱 공존 읽기 점검

## 1. 5줄 결론

1. LC의 v1 무료/v2 이용권 경로 분리는 코드에서 확인했다. 그러나 현재 코드를 그대로 prod에 올려도 무영향이라고 판단할 수 없다.
2. PaidExamActivationGate가 호출하는 PaidExamTestActivation은 active profile이 정확히 test가 아니면 기동을 거절한다.
3. paid를 OFF로 두는 것도 해결책이 아니다. v1 TransitionFreeExamService는 paid ON에서만 만들어지는 LegacyExamRuntime을 요구한다.
4. 서울 리전 ECS 전체 cluster/service 조회에서 Billing은 test 서비스만 확인됐다. LC prod7/Identity prod12는 각각 실행1이다.
5. 이번은 코드·AWS 설정 읽기 점검이다. 제품 코드/DB/설정/배포 변경이나 실제 사용자 E2E는 수행하지 않았다.

## 2. 반드시 읽어야 하는 내용

운영 배포 전에 LC의 운영용 readiness 경로를 보완해야 한다. test profile을 prod에 복사하거나 준비 검사를 제거하는 방법은 권장하지 않는다. 기존 Session manifest, owner transaction, 필수 index, 운영 전용 인증 준비를 확인한 운영용 활성화 경로가 필요하다.

`BILLING_LEGACY_EXAM_CREATE_ENABLED=true`만 추가해서는 부족하다. TransitionFreeExamService.start는 enabled·owner.enabled·LegacyExamRuntime 모두를 요구하며 없으면 EXAM_CREATION_PROCESSING으로 거절한다. 기존 Session에 대해서도 requireKnown을 수행하므로 운영 manifest 준비가 없으면 구앱 생성/조회/채점 호환을 보장할 수 없다.

## 3. 결정 사항

새 상품 정책 선택은 없다. 기존 승인대로 v1 무료/v2 이용권을 유지한다. 후속은 LC 운영 readiness 보완과 운영 DB 준비 확인, Billing prod 준비, 구앱 로그인·토큰갱신·시험 생성·기존 제출·결과 조회 검증이다. 실제 변경/활성화는 별도 작업이다.

## 4. 위험과 미확인

- 현재 prod DB의 legacy manifest/index/control은 이번에 조회하지 않았다. 없다고 단정하지 않는다.
- Identity의 신규 purchase fence/scope 활성화 시 control/readiness 준비를 확인해야 한다. 현재 로그인 issuer/key를 임의 교체하면 기존 토큰에 영향을 줄 수 있다.
- prod Identity의 JWT_ISSUER 값은 identity-staging 호스트다. 이름만 보고 변경하지 말고 기존 LC 검증값과 맞춰 보존해야 한다.
- 실제 구앱이 사용하는 base URL/앱 버전은 이 점검에서 확인하지 않았다. 운영·staging 호스트가 함께 존재한다.
- 최소 버전은 현재 Android/iOS 모두 2.0.1이다. 심사 중 임의 상향하지 않는다.
- Billing prod 부재는 현재 계정 서울 리전 ECS 조회 범위의 결과이며 다른 계정/리전/배포 방식까지 단정하지 않는다.

## 5. 실제 확인 결과

| 항목 | 결과 |
| --- | --- |
| LC prod | tosunsaeng-learning-core-prod:7, desired/running 1/1, profile prod |
| LC prod 환경 | BILLING_CREATION_SAGA_ENABLED=false, USER_MERGED_WRITER_ENABLED=true. 태스크 환경에 paid-exam/legacy-create 활성화 설정 없음 |
| Identity prod | tosunsaeng-identity-prod:12, desired/running 1/1, profile prod |
| Identity JWT | audience tosunsaeng-learning-core, issuer https://identity-staging.to-teacher.com. 비밀키 값은 조회하지 않음 |
| Identity 문의 | SUPPORT_INQUIRY_ENABLED=false |
| 앱 최소 버전 | APP_ANDROID_MINIMUM_VERSION/APP_IOS_MINIMUM_VERSION=2.0.1 |
| Billing ECS | test:14 실행1, prod 서비스 미발견 |
| 운영 ALB | identity-prod host→Identity prod TG, api-prod host→LC prod TG. LC host 규칙에는 v1/v2 path 제한 없음. challenges 별도 규칙 유지 |

태스크 환경변수 부재는 외부 설정원까지 없다는 증명이 아니다. 현재 배포된 구이미지가 새 코드와 동일하다고 가정하지 않으며 위 실패 예측은 로컬 최신 코드를 현재 설정으로 교체하는 경우에 대한 분석이다.

## 6. 근거

- LC `application/ExamServiceImpl.java`: v1 TransitionFree, v2 paid saga 분리.
- LC `application/TransitionFreeExamService.java`: legacy-create 기본 false, runtime/owner/transaction 필수.
- LC `rollout/LegacyExamRuntimeConfiguration.java`: paid-exam.enabled=true 조건과 verifySealed.
- LC `application/PaidExamActivationGate.java`, `rollout/PaidExamTestActivation.java:26`: test-only 기동 조건.
- Identity `identity/global/security/jwt/JwtAccessTokenIssuer.java:109`: 기존 audience와 Billing audience 합집합.
- AWS CloudShell boto3 ECS list/describe, ELB describe 읽기 호출. Secret 내용/사용자 자료 조회 없음.
- 정적 검토 및 AWS 설정 조회만 수행했으므로 Gradle/실제 생성 테스트는 재실행하지 않았다.
