# Billing 운영 전환 준비 묶음

## 1. 5줄 결론

1. 기본 Dockerfile을 바꾸지 않고 별도 `Dockerfile.workload`에 기존 TLS materialization 시작 스크립트를 포함했다.
2. 후보는 현재 작업트리의 운영 환불 지원 및 기존 미커밋 cleanup/Slack 변경을 함께 포함한다. HEAD만의 재현 가능한 출시 이미지가 아니다.
3. 실제 운영의 최신 확인본은 prod:5이며 앱 설정 OFF 배포만 완료했다. 이번 준비로 서비스·DB·키·ALB를 변경하지 않는다.
4. LC 환불 HTTPS origin·운영 발신 대역·양측 활성화 순서는 아직 확인 필요다. 예정 주소를 동작하는 연결로 간주하지 않는다.
5. 로컬 검증 이후에도 commit/review·레지스트리 전달·원격 digest 확정·실환경 TLS/공동 리허설이 남는다.

## 2. 반드시 읽을 내용

후보 이미지는 `/app/workload-entrypoint.sh`로 시작한다. 운영 Mongo host/DB의 exact 비교, Secret의 PKCS12 복원, 인증서 fingerprint/유효기간 검사 후 Java를 실행한다. 비밀파일은 비root UID10001의 /tmp에0600으로 생성하며 Secret 원문을 이미지에 넣지 않는다. host/SAN은 별도 TLS preflight로 대조한다. fingerprint만 맞는 것을 host 검증 완료로 간주하지 않는다.

기본 Dockerfile·업무 코드·인증 규칙은 이번에 수정하지 않았다. EXPOSE8443은 네트워크 개방이나 connector ON이 아니다. 양 receiver OFF이면 실제8443은 열리지 않는다. 모든 업무OFF 후보 기동으로 ON 상태 기동 검증을 대신하지 않는다.

## 3. 적용 전에 확정할 항목

- LC 담당: 실제 운영 환불 HTTPS origin, exact 수신 route, 운영 발신 NAT/출구 대역, TLS·키·scope 및 readiness/봉인 조건 확인.
- 사용자: 신규 생성 제한 수단/응답·시간대·최대 허용시간·검증 계정. 코드 전달 및 운영 변경은 별도 실행 범위로 승인.
- Billing: 후보에 포함된 기존 dirty 변경을 검토하고 commit 기준을 고정한 뒤 이미지 재생성. 현재 로컬 후보를 HEAD commit 이미지로 표시하거나 임의 push하지 않는다.

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
