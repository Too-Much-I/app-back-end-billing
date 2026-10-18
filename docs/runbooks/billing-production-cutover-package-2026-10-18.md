# Billing 운영 전환 준비 묶음

## 1. 5줄 결론

1. 기본 Dockerfile을 바꾸지 않고 별도 `Dockerfile.workload`에 기존 TLS materialization 시작 스크립트를 포함했다.
2. 후보에 포함했던 운영 환불 지원·cleanup/Slack·TLS 변경을 commit `57d66398ba3bf63a4fc2750ec8fd8cc4fdbdf11c`로 고정했다. 아래 빌드 당시 dirty 기록은 이 커밋 전의 이력이다.
3. 실제 운영의 최신 확인본은 prod:5이며 앱 설정 OFF 배포만 완료했다. 이번 준비로 서비스·DB·키·ALB를 변경하지 않는다.
4. LC 환불 origin은 `https://api-prod.to-teacher.com`, 양측 발신 대역은 `13.124.57.130/32`로 확인했다. 전용8444 전달·활성화는 미완료이며 동작하는 환불 연결로 간주하지 않는다.
5. 커밋·로컬 검증 이후 레지스트리 전달 결과는 아래 후속 기록을 따른다. 실환경 전용 TLS/공동 리허설과 서비스 배포는 별도다.

## 2. 반드시 읽을 내용

후보 이미지는 `/app/workload-entrypoint.sh`로 시작한다. 운영 Mongo host/DB의 exact 비교, Secret의 PKCS12 복원, 인증서 fingerprint/유효기간 검사 후 Java를 실행한다. 비밀파일은 비root UID10001의 /tmp에0600으로 생성하며 Secret 원문을 이미지에 넣지 않는다. host/SAN은 별도 TLS preflight로 대조한다. fingerprint만 맞는 것을 host 검증 완료로 간주하지 않는다.

기본 Dockerfile·업무 코드·인증 규칙은 이번에 수정하지 않았다. EXPOSE8443은 네트워크 개방이나 connector ON이 아니다. 양 receiver OFF이면 실제8443은 열리지 않는다. 모든 업무OFF 후보 기동으로 ON 상태 기동 검증을 대신하지 않는다.

## 3. 적용 전에 확정할 항목

- LC 담당: 아래 확인된 origin·route·출구 대역으로 전용 TLS·키·scope 및 readiness/봉인 조건 확인.
- 사용자: 신규 생성 제한 수단/응답·시간대·최대 허용시간·검증 계정. 코드 전달 및 운영 변경은 별도 실행 범위로 승인.
- Billing: 후보 내용은 commit57d6639로 고정했다. 원격 이미지 digest와 연결해 전달하고 Git push·서비스 배포는 별도로 수행한다.

## 2026-10-18 실제 주소·발신 대역 확인

- 수신: `POST https://api-prod.to-teacher.com/internal/v1/entitlements/access/events`.
- Billing origin 설정값: `BILLING_PAYMENT_REFUND_LEARNING_CORE_ORIGIN=https://api-prod.to-teacher.com` (경로는 publisher가 추가). 이번에는 설정 미변경.
- LC→Billing 및 Billing→LC 공인 발신 CIDR: `13.124.57.130/32`. 공유 NAT라 IP만으로 서비스 신원을 구분하지 않는다. 전용 JWT 검증을 유지한다.
- AWS ap-northeast-2, cluster `tosunsaeng-staging-cluster`의 LC prod:8/Billing prod:5가 사용하는 두 private subnet `subnet-0fa638ca8effafa81`, `subnet-0dc3e0343bb98e3e3`의 기본 경로가 available NAT `nat-0a11238673df5ef5d`로 연결됨을 읽기 확인. public IP 할당은 DISABLED. subnet/NAT 변경 시 다시 확인한다.
- api-prod DNS는 기존 ALB로 연결되고 공개443 TLS 인증서 SAN/시스템 trust 검증을 통과했다. 기존 host rule80은 LC8080 TG, rule70은 별도 evaluation 경로다. 전용8444 검증 성공을 뜻하지 않는다.
- 후속 변경안: exact host+POST+위 경로+source CIDR → `lc-prod-access-events-8444` TG. 그 외 동일 내부 경로가 기존8080으로 fallback하지 않도록 차단 규칙도 함께 검토한다. 기존 앱/evaluation rule은 보존한다. 이번에 listener·TG·서비스·SG·IAM·DB·기능 flag 변경 없음.
- LC receiver/readiness·기존 시험 봉인·양측 구 writer 배출·정상서명 공동검증 후 별도 승인으로 연결한다. LC 저장소는 수정하지 않았다.

## 4. 네트워크 변경안 — 미적용

| 구간 | 준비할 exact 변경 | 검증/제약 |
| --- | --- | --- |
| LC→Billing DNS | billing-workload-prod.to-teacher.com → 기존 ALB | 공개443 인증서 SAN/일반DNS 확인. 앱 host와 분리 |
| ALB→Billing | 기존 billing-prod-workload-8443 HTTPS TG → task8443, 서비스에 추가 | 기존 앱8083/health8082 유지. task8443은 기존 ALB SG만 허용 |
| Billing task | 명시 후보 digest·TLS 시작명령·8443 TCP mapping | prod:5 env/Secret/역할 보존. health grace300 유지. 자동schema 설치 금지 |
| LC→Billing ALB rule | 아래 POST 목록만 workload host와 확인된 LC 출구대역으로 전달 | 빈 source-ip 또는0.0.0.0/0로 대체 금지. host 나머지404 |
| Billing→LC | LC가 확정한 운영 origin → 기존 LC HTTPS8444 TG/서비스 | LC 담당 범위. health8080 성공과 TLS8444 성공 구분 |

Billing LC 수신 경로:

- POST `/internal/v1/reservations`
- POST `/internal/v1/reservations/status`
- POST `/internal/v1/reservations/continuations/phone`
- POST `/internal/v1/reservations/{reservationId}/confirm`
- POST `/internal/v1/reservations/{reservationId}/cancel`
- POST `/internal/v1/attempt-group-events`
- POST `/internal/v1/exam-deletions/events` (삭제 기능 준비 후 별도 활성화)

ALB의 동적 경로 wildcard는 앱의 strict matcher를 대신하지 않는다. malformed/다중segment/encoded slash/잘못된method/다른host/8082·8083 우회를 검증한다. `/internal/*` 전체 허용은 하지 않는다. Identity 네 이벤트는 이 LC 변경안에 끼워 열지 않는다. rule priority는 적용 직전 실제 listener를 읽어 충돌 없는 값을 확정한다.

## 5. 태스크 구성·검증 순서

1. 현재 실행 revision 전체를 읽고 prod:5 이후 동시 변경이 있으면 병합 대조부터 한다. 준비 revision의 최신 번호를 무조건 배포하지 않는다.
2. `BILLING_EXPECTED_MONGO_HOST`와 `BILLING_EXPECTED_MONGO_DATABASE`는 운영 승인값으로 지정한다. `MONGODB_URI` 및 TLS3필드(PKCS12/password/fingerprint)는 기존 운영 Secret 참조로 주입한다. URI나 키를 JSON 평문/로그에 넣지 않는다.
3. 기존 workload production 환경·scope·LC public JWK/refund private JWK는 보존한다. LC origin 확정 전 publisher는OFF. Identity receiver/삭제/cleanup/구매를 자동으로 켜지 않는다.
4. 별도 `billing-tls-preflight.sh`로 실제운영 TLS만 주입해 host/fingerprint/Java keystore/HTTPS를 확인한다. 이 문서는 실환경 preflight 실행 완료를 주장하지 않는다.
5. LC 봉인/구 writer 배출·기존 앱 공존 조건을 충족한 전환 시점에 명시 기능 조합을 검증하고 배포한다. receiver만 켜서 업무 gate를 우회하지 않는다.
6. 신규 정상서명/401·403·잘못된목적/공개포트차단·중복·응답유실을 공동 검증한다. 이전 task/ALB draining/worker 종료를 확인한 뒤 생성 제한을 해제한다.

## 6. 로컬 산출물과 검증

- `Dockerfile.workload`, 전용 `.dockerignore`: JAR와 시작 스크립트만 빌드 컨텍스트 허용. 기본 이미지 빌드는 불변.
- `scripts/ops/billing-workload-image-smoke.sh`: 폐기 가능한 network-none 컨테이너에서만 fake Mongo 대상·자체 생성 인증서를 사용한다. 정상 실행/틀린DB/틀린fingerprint를 검사하고 최종 Java launch만 stub한다. 실제 애플리케이션 전체 기동이나 JWT/네트워크 검증이 아니다.
- 빌드 전 기준 HEAD `6ebb94e49500d8c764eb163d634bf44a55d4102e`, 브랜치 codex/billing-test-deployment. 기존 dirty 포함 사실을 명시한다.
- 실행 결과 및 로컬 image ID는 아래 최종 검증에 기록한다. 레지스트리 digest는 push 전이므로 없다.

### 로컬 후보 결과

- `docker build --platform linux/amd64 -f Dockerfile.workload -t billing-workload-candidate:20261018 .` 성공.
- 로컬 image ID: `sha256:8222d0a1f4b8a1e0b922522c1d242227b127cf1c62003a02fee16ad7d6401279` (원격 ECR 배포 digest가 아님).
- JAR SHA256: `b53ab56bf69736f4d139cf357475392c2be2c58faf4bd4948e1c659358f762ac`.
- linux/amd64·UID10001:10001·workload entrypoint 확인.
- `docker run --rm --platform linux/amd64 --network none --entrypoint sh -v "$PWD/scripts/ops/billing-workload-image-smoke.sh:/smoke.sh:ro" billing-workload-candidate:20261018 /smoke.sh <case>`: valid/wrong-db/wrong-cert 모두 통과. 실제 운영 Secret·API·DB 사용 없음.
- shell 구문·git diff 공백 검사 통과. AWS 재조회·등록·배포·네트워크 변경 없음.
- `./gradlew clean test bootJar`: 4분7초 BUILD SUCCESSFUL. XML 집계1838개/실패0/오류0/skip0. 로컬 전체 회귀이며 운영 공동 E2E는 아니다.

### 커밋·ECR 전달 결과 (2026-10-18)

- 소스 커밋: `57d66398ba3bf63a4fc2750ec8fd8cc4fdbdf11c`. Git push/merge는 이번에 수행하지 않았다.
- 전달 태그: `889384901776.dkr.ecr.ap-northeast-2.amazonaws.com/tosunsaeng-billing-test:prod-workload-57d6639`. 이름은 test지만 기존 prod도 사용하는 저장소이며 기존 태그를 덮어쓰지 않았다.
- 원격 digest: `sha256:e5666d537391eebc7cffe91896a756f93b93d8acef2d480030e1b56e9e7597f9`.
- 배포 후보 URI: `889384901776.dkr.ecr.ap-northeast-2.amazonaws.com/tosunsaeng-billing-test@sha256:e5666d537391eebc7cffe91896a756f93b93d8acef2d480030e1b56e9e7597f9`.
- TAR SHA256 `fe7e5dcd5a8e6376f603a1cf7ce345453f4cdba9aad1fdcbb2be3b00facb8d0b` 대조 후 load/tag/push. 원격 manifest config digest는 로컬 `sha256:c3375dd7da2dd92cc4980e7e088c626c734bf795f6ae63fcd907b0adc5dd1d09`와 대조한다. Docker load/push 후 원격 manifest digest가 로컬 index digest와 다른 점을 구분한다.
- 최초 로컬 push는403, 최초 CloudShell 업로드는 홈 용량 부족으로 실패했다. 불완전 파일을 사용하지 않고 재업로드 해시 일치 후 전달했다. 사용자 승인으로 실패 부분파일만 삭제했고 임시 이동한 기존 배포 아카이브는 복원했다. 정상 TAR는 로컬 및 CloudShell `/tmp/billing-image-transfer-57d6639/billing-workload-57d6639.tar`에 남는다.
- 이미지 전달은 ECS 배포가 아니다. prod:5 서비스·기능OFF·DB·키·ALB·LC/Identity는 이번에 변경하지 않았다. 이번 문서 갱신으로 Java 재실행은 생략하고 기존1838개·smoke3·Node8 통과와 diff 검사를 기록한다.
