# Billing 운영 앱 설정 OFF 배포 완료 — 2026-10-18

## 1. 결론

1. RevenueCat·사용자 JWT·요청 제한 Secret 참조를 prod:5에 연결했다. 업무 기능은 모두 OFF다.
2. Secret 주입과 애플리케이션 기동, 컨테이너 HEALTHY는 확인했다.
3. 최초 ALB 검사 실패 후 prod:3 복구·기동 유예300초를 적용했고, prod:5 재배포는 COMPLETED/실행1/대기0·HEALTHY로 완료됐다.
4. 운영 DB control은 PRODUCTION/ALL_BLOCKED/version1, 외부 앱 네 API와 내부 경로는 HTTPS404였다.
5. 앱 설정 연결 및 OFF 기동 확인까지만 완료다. 정상 로그인·RevenueCat 검증·구매 E2E와 기능 활성화는 미실행이다.

## 2. 변경 및 보존 범위

- 기존 미배포 prod:4를 기반으로 prod:5 등록 후 전체 필드 readback을 대조했다. LC/환불/TLS 준비 참조는 보존했다.
- revenuecat Secret의 API/lookup/reference/webhook 관련 7개 필드와 payment-runtime의 limiter 1개 필드 참조 추가. 비밀값 변경·출력·Git 기록 없음.
- 사용자 issuer는 실제 Identity prod 설정인 `https://identity-staging.to-teacher.com`, JWKS는 `https://identity-prod.to-teacher.com/.well-known/jwks.json`. 이름만 보고 issuer를 바꾸지 않았다.
- RC project `70a245ef`, 기존 Apple/Google 앱 및 각 5개 premium 상품, budget DB `to-teacher-revenuecat-budget`, catalog version1, recovery fence required를 연결했다.
- public connector 외 모든 enabled flag는 false. 상품·구매계정·sync·권리조회·worker·수신·재조회·삭제·환불 OFF, index 자동설치 OFF.
- 이미지·역할·네트워크·LB·포트·Secret 값·DB는 변경하지 않았다. LC/Identity 수정 없음. 로컬 환불 prod 지원 코드는 이번 이미지에 포함되지 않는다.

## 3. 검증과 실패 근거

- Secret 형식(필수 문자열/키 길이), 기존 역할의 exact Secret 접근, 상품10개·budget collection 확인. 공개 JWKS HTTPS200.
- prod:5 컨테이너 RUNNING/HEALTHY, Started BillingApplication 및 Tomcat8082/8083 확인. 조회한 시작 로그에서 Exception class와 APPLICATION FAILED 없음.
- 첫 신규 태스크: UTC15:14:10 startedAt, 15:14:29 애플리케이션 기동, 15:16:43 stoppingAt. stoppedReason은 ELB health checks 실패.
- TG는 HTTP8082 `/actuator/health`, 200 요구, 30초 주기/5초 timeout/healthy threshold5. 서비스 healthCheckGracePeriodSeconds0. 유예시간만이 원인이라고 확정하지 않는다.
- 기존 healthy 태스크는 2b, 실패 태스크는 2a. 동일 task SG, ALB 양 AZ 활성, ALB SG egress 허용, task8082/8083은 ALB SG 허용. 두 subnet NACL allow 및 local/NAT 경로 확인. 이 정보만으로 모든 네트워크 정상이나 원인 확정을 주장하지 않는다.
- 원복 전 외부 앱4개 및 내부 경로 HTTPS404, DB control singleton ALL_BLOCKED/version1 확인.
- 제품 코드 변경이 없어 Gradle 미실행. 기존 테스트 결과를 운영 E2E로 대신하지 않는다.

## 4. 다음 작업

Identity 구매 control/fence/scope 준비 및 LC 운영 전환을 확인한 뒤 별도 업무 활성화와 정상 JWT·RC·구매 E2E를 진행한다. 현재 설정 연결을 구매 가능 상태로 안내하지 않는다. 후속 배포도 기동 유예300초와 ALB 정상 판정 기준을 유지한다.

## 5. 기동 안정화 후속

원복 prod:3의 신규 태스크도 초기 unhealthy가 발생했다. 따라서 신규 Secret 설정만을 원인으로 단정하지 않는다. 기동 유예0과 ALB unhealthy에서 healthy 복귀5회(30초 간격)의 경합 가능성을 확인하기 위해 Billing prod 서비스의 healthCheckGracePeriodSeconds만300으로 조정했다. ALB200/threshold/timeout·SG·JWT·업무OFF·DB 차단은 그대로다. 유예는 ECS의 초기 종료 판단에만 적용되며 unhealthy target을 정상으로 만들거나 정상 target이 있는 ALB의 트래픽 대상으로 허용하는 설정이 아니다.

유예 적용 직후에도 unhealthy가 보여 직접 rollback을 준비했으나, 실행 전 상태 assertion에서 이미 IN_PROGRESS가 아닌 것으로 확인돼 stop 및 grace 원복은 실행되지 않았다. 재조회 결과 prod:3 COMPLETED/실행1/대기0, 신규 target healthy로 정상복귀했다. grace300은 유지됐다. ALB 기준을 유지한 채 충분한 기동 시간을 둔 후 복귀한 관측이며, 모든 실패 원인을 독립 입증한 것은 아니다. 정상복귀를 확인한 뒤 동일 prod:5를 업무OFF 그대로 재배포했다.

## 6. 최종 결과

- prod:5 PRIMARY COMPLETED, RUNNING1/PENDING0, 신규 태스크 HEALTHY·Started 확인, ALB 신규target healthy. 이전target2개는 draining으로 정상 종료 중이다.
- 재배포 뒤 일반 DNS/인증서 검증을 사용하는 HTTPS 앱4API·내부 경로 총5개404. DB ALL_BLOCKED/version1 재확인.
- 실행 task definition의 public connector 외 enabled flag는 모두 false, Secret 참조16개. 이는 ON 상태의 각 검증기/worker가 모두 작동함을 뜻하지 않는다.
- 최초 실패 후 서비스 기동 유예만0→300초로 변경했다. ALB 검사/인증/네트워크/DB/IAM/Secret값은 불변이다.
- 증거 `/private/tmp/billing-prod-app-config-20261018.png`. 문서 diff 검사 통과. 코드/commit/push/Jira 변경 없음, 기존 dirty 보존.
