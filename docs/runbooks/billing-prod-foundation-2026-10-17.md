# Billing prod 기반 구성 — 2026-10-17

> 2026-10-17 앱 연결 후속: 승인된 DNS CNAME3개 저장·ACM 발급·기존443 listener 인증서 추가 완료. billing-prod host의 GET products/entitlements, POST purchase-account/sync만 public8083 TG로 전달하고 나머지는404. prod:2의 최신 설정/Secret/image를 보존해 connector만 추가한 prod:3 배포 진행. 모든 업무 기능 OFF, workload host는 인증서만 준비했다. 아래 prod:1·외부 미연결 내용은 이전 시점 기록이며 최종 검증은 후속 기록 참조.

## 1. 5줄 결론

> 최종 기동 확인: prod:1의 task `a61012db80ab46d7bdf13981fb501b2c` RUNNING/HEALTHY, 실행1·대기0. 컨테이너 health check는 localhost:8082 `/actuator/health`의 HTTP200을 요구한다. Tomcat/애플리케이션 Started 확인, 검사한 최근100로그에서 Exception class 없음. 외부 ALB health/TLS/E2E 통과라는 의미는 아니다.

> 후속 실제 적용: 운영 빈 DB 확인 후 schema task revision 2의 install-empty가 exit 0, `BILLING_PROD_SCHEMA_OK install-empty barrier=ALL_BLOCKED`로 완료됐다. revision 3의 독립 verify도 41collection/exit0/ALL_BLOCKED로 통과했다. 임시 collMod 역할의 사용자 연결을 해제·저장했으며 페이지 재조회에서 원래 readWrite만 표시되고 반영 중 배너도 사라졌다. Billing prod:1/service를 업무 기능 OFF·외부 listener 미연결로 생성해 기동 확인 중이다. 아래 미설치 기록은 이전 시점 기록이다. 결제·삭제 기능은 활성화하지 않았다.

1. 사용자 승인으로 LC 운영 readiness 변경 8개 파일을 9fb8f71로 commit/push했다. 브랜치는 feat/lc-review-coexistence이며 main/develop 머지·배포는 하지 않았다.
2. 운영 LC/Identity DB 접속·컬렉션 메타데이터 읽기는 성공했다. 사용자 레코드 내용은 출력하지 않았다.
3. LC 운영 DB에 exam_paid_rollout_state, exam_legacy_manifest_entries, exam_creation_operations가 없다. user_ownership_guards는 존재하나 전체 readiness 검증 완료는 아니다.
4. Billing 전용 빈 Secret 8개·실행 역할·로그 그룹·보안그룹·target group 2개를 생성했다. 아직 서비스/태스크 실행·DNS/ALB rule 변경은 없다.
5. 사용자가 Atlas 운영 계정을 생성하고 숨김 입력으로 MongoDB Secret을 저장했다. 독립 접속 검사에서 인증 성공·Billing prod DB의 readWrite만 확인했다. 나머지 키/스키마/배포 준비는 남았으며 기능 ON이나 prod 준비 완료로 해석하지 않는다.

## 2. 반드시 읽어야 하는 내용

새 Secret들은 값이 없는 저장 위치다. list_secret_version_ids로 8개 모두 version 0건을 확인했다. 테스트 DB/키/RevenueCat Secret을 운영으로 복사하지 않았다. 실행 역할은 기본 ECS ECR/로그 권한 및 이번 생성한 exact Secret ARN 8개에 대한 GetSecretValue만 갖는다. 애플리케이션용 task role은 생성하지 않았다.

운영용 DB 계정은 Atlas Project 0에서 tosunsaeng-billing-prod, readWrite @ to-teacher-billing-prod만 허용하도록 별도 명시 승인을 받았다. UI에 역할/DB만 입력했고 username/password·Add User 제출은 하지 않았다. 관리 권한과 다른 DB 권한은 포함하지 않는다.

## 3. 승인과 다음 단계

- 2026-10-17 후속: 운영 전용 초기설치 도구 `scripts/ops/BillingProdSchemaBootstrap.java` 준비 및 로컬41collection 설치/verify·기존DB 재설치 거절 검증 완료. 사용자 임시collMod8collection 승인 수신. 아직 Atlas 권한변경·운영DB 설치는 하지 않았다. 도구는 PRODUCTION ALL_BLOCKED를 유지하고 기능 활성화 명령을 제공하지 않는다. 전체 회귀1806건 통과.

- 운영 전용 DB/계정·Secret·ECS 역할/서비스, 기존 ALB의 billing-prod 앱4API와 billing-workload-prod 승인 서버 경로 구성을 승인받았다. 준비 완료 전 결제/삭제 OFF 조건이다.
- 최초 readWrite 선택은 안전 검토에서 scope 불명확으로 거절됐다. exact 프로젝트/계정/DB/권한 사용자 재승인 후 UI 준비만 진행했다. 우회 실행 없음.
- 사용자 계정 생성 후 URI를 Secret에 직접 입력하도록 안내하고 값 비출력으로 접속/권한 검증한다.
- 키/TLS 생성·저장 단계는 사용자 직접 실행이 필요하다. 전용 IAM 허용은 빈 Secret 준비와 구분한다.
- 운영 LC 목록/index 설치는 기존 writer drain·데이터 점검과 별도 작업으로 남는다. 기존 운영 데이터에 테스트 유지보수 도구를 그대로 적용하지 않는다.

## 4. 위험·미확인

- 운영 DB를 조회한 계정이 볼 수 있는 database 이름 중 billing은 없었다. 전체 Atlas의 부재를 무조건 증명하는 것은 아니다.
- 기존 운영 클러스터 UI는 512MB 한도를 표시했다. 보존/백업/용량·복원 요건 충족은 별도 확인이 필요하며 이번에 등급/비용을 변경하지 않았다.
- 아직 운영 Billing image/task definition/service, 인증키 값, RevenueCat 운영값, schema/index/control, DNS·ACM·listener allowlist, 실제 API/E2E는 준비 완료가 아니다.
- 기존 LC/Identity 서비스·운영 데이터·최소 앱 버전·키 값은 변경하지 않았다.

## 5. 생성한 기반 리소스

| 종류 | 이름/범위 |
| --- | --- |
| Secret prefix | tosunsaeng/prod/billing/ |
| 빈 Secret 8개 | mongodb, workload-tls, refund-workload-jwt, payment-runtime, revenuecat, identity-workload-public-jwks, learning-core-workload-public-jwks, recovery-workload-jwt |
| 실행 역할 | tosunsaeng-billing-prod-execution-role |
| 로그 | /ecs/tosunsaeng-billing-prod, 보존30일 |
| task SG | tosunsaeng-billing-prod-task; 기존 ALB SG에서 TCP8082/8083/8443만 inbound, 기본 outbound 유지 |
| public target group | billing-prod-public-8083, HTTP8083 |
| workload target group | billing-prod-workload-8443, HTTPS8443 |
| health | HTTP8082 /actuator/health, 200 |

target group은 기존 ALB와 같은 VPC에 만들었지만 listener에는 아직 연결하지 않았다. 사용자 데이터 삭제·새 구매·환불·자동정리 실행은 없다.

## 6. 검증·전달 기록

- schema 실행: inspect `6d4083cd7654456799fa16baf152f056` exit0/collection0, install `f1e8201ff78341f8b3c0e4595f5b7847` exit0, verify `8a370931b2ae4364981891c836a8961b` exit0/41collection. 최초 RunTask override는8192자 제한으로 실행 전 거절돼 task definition revision2/3로 분리했다. 재설치·데이터 삭제 없음.
- 임시 역할 `billing-prod-schema-maintenance`의 계정 연결을 제거했다. 역할 정의는 미할당 상태로 보존했다. 회수 확인 화면: `/private/tmp/billing-prod-role-revoked-20261017.png`.
- prod 초기 OFF 서비스는 기존 검증 runtime digest `sha256:5f1723774ca6beb43c27af0a610d8e0fecf512aa70071400b08eacf2334954a9`를 고정했다. 테스트 설정/Secret은 복사하지 않고 prod Mongo 참조만 사용한다. 모든 업무 flag 기본OFF, index 자동 생성OFF, transaction 요구ON. 외부 listener/DNS·workload TLS 연결·업무 활성화는 별도이며 아직 앱 트래픽을 받지 않는다.

- 사용자 직접 실행으로 refund-workload-jwt, recovery-workload-jwt, payment-runtime, workload-tls 4개 Secret 값 저장 완료. 독립 RSA3072 서명/검증, private sign/public verify, 공개키 비밀필드 부재, HMAC32byte를 확인했다. TLS는 SAN/키일치/지문 및 CloudShell Java keytool의 PKCS12 읽기 성공. 만료 2027-10-17 13:21:03UTC. 실제 앱 기동/HTTPS 검증과는 구분한다. 자체서명 인증서는 ALB→task용이며 공개 ALB ACM 인증서가 아니다.
- 스키마 도구 조사: 기존 BillingTestSchemaBootstrap은 test DB/SANDBOX 전용이며 운영에 그대로 실행하지 않았다. 운영은 전용 초기 설치 경계와 필요한 maintenance 권한 검토 후 진행한다. 기존 서비스/기능 설정 변경 없음.

- 사용자 Secret 저장 후 읽기 전용 접속 검증 성공: `to-teacher-billing-prod`, 컬렉션 0개, authenticatedUserRoles는 해당 DB의 readWrite 1개뿐이다. URI/비밀번호는 출력하지 않았다. 이번 검사에서 DB write·스키마 설치·서비스 배포·기능 활성화는 하지 않았다.

- LC push 성공: 3cf3e0a → 9fb8f71. 공유 CURRENT_STATE/WORKLOG에는 다른 동시 작업의 기록이 섞여 있어 이번 commit에서 제외하고 보존했다. 전용 운영 준비 문서는 commit에 포함됐다.
- 직전 LC clean test1395통과/기존skip1, Mongo336통과 결과를 사용했다. 이번은 Git 전달·인프라 준비로 제품 테스트 재실행 없음.
- CloudShell Python에서 기존 태스크의 Secret 참조로만 DB 연결, URI/비밀값 비출력. LC to-teacher-app 33 collections, Identity to-teacher-identity 32 collections. 문서 내용·개인정보 조회 없음.
- pymongo 최초 --user 설치는 기존 virtualenv 제약으로 실패. 독립 mktemp 디렉터리에 설치 후 읽기 검사 성공, 기존 환경 패키지 변경 없음.
- 기반 구성 확인 화면: /private/tmp/billing-prod-base-prepared-20261017.png. Atlas 권한 준비 화면: /private/tmp/billing-prod-atlas-permission-ready-20261017.png.
