# Billing 테스트 서버 초기 배포 — 2026-10-16

## 후속 보안 패치·연결 조사

### 제한된 workload ALB·DNS 연결 — 2026-10-16

- 적용 직전 사용자 승인 후 기존 ALB443에 인증서를 추가하고 `billing-test-workload-8443` HTTPS8443/IP target group을 생성했다. health는 내부 HTTP8082 `/actuator/health`이며 health 경로를 외부 forward하지 않는다. Billing SG는 기존 ALB SG에서8443만 추가 허용, 기존8082/egress 및 다른 서비스 규칙은 보존했다.
- listener priority90~95는 `billing-workload-test.to-teacher.com` + source `13.124.57.130/32` + POST + 개별 LC 경로를 모두 요구한다. 예약/status/phone continuation/`*/confirm`/`*/cancel`/attempt-group-events만 전달,96은 해당host 나머지를404로 차단한다. lifecycle wildcard는 앱의 strict path 검증을 대체하지 않는다. 기존규칙이 추가 후 그대로 존재함을 비교했다.
- 가비아 CNAME `billing-workload-test` → 기존 staging ALB DNS,TTL600 저장 완료(24→25개). 앱용 billing-test 트래픽 레코드는 추가하지 않았다. CloudShell 외부 POST status는404/TLS_VERIFY0으로 실제 DNS·공인인증서·출처차단 확인.
- ECS 서비스만 기존 task3/이미지/업무flag 그대로 target group에 연결했다. LC/Identity 서버 배포·키권한·판매/환불/삭제 활성화는 하지 않았다. 허용 NAT를 공유하는 다른 workload도 네트워크상 도달 가능하므로 서비스 구분은 전용JWT가 담당한다.
- 일회성 무비밀 probe는 같은 테스트 사설 네트워크에서 인증 없는 호출만 수행한다. 첫 실행은 예약/status401 뒤 phone continuation404로 중단했다. 해당 기능의 owner-rebind flag가OFF인 정상차단을401로 잘못 기대한 검증 오류이며, 기능을 켜지 않고 기대값을 수정했다. 최종 검증 결과는 아래 후속 기록으로 남긴다.
- 최종검증: target group `8fcb6e1f4e6f6660` healthy/8443, 서비스 running1/pending0/단일deployment COMPLETED. probe2 task `48750e29964a4859ac82d0b03c299d0a` STOPPED/exit0, `ALB_NETWORK_PROBE_OK`. 예약/status/confirm/cancel 무인증401, OFF phone/attempt404, 비허용health/public/deletion/Identity404 및 GET status404를 실제DNS·공인CA 검증 HTTPS로 확인했다. probe1도STOPPED/exit1이며 남은 실행probe는 없다. 사용자/시험/결제 업무자료나 실제JWT를 넣지 않았다.
- ALB→target HTTPS는 구간 암호화이며 target 인증서 trust 검증을 뜻하지 않는다. LC signed JWT·업무E2E/paid·삭제schema gate는 별도로 남는다. 제품코드 불변으로 Gradle 재실행 없이 네트워크 검증을 수행한다(직전1749개 성공은 별도 기록).

### 실제 Billing HTTPS 기동 완료 — 2026-10-16

- 사용자 승인 후 task revision3 적용, task `c003b0b394b74ae59f495b4262f89175` RUNNING/HEALTHY 및 service running1/pending0/rollout COMPLETED. 기존security1 digest 유지. 아래 task1/2·schema미설치/LC receiverOFF 표현은 당시 이력이다.
- [배포 entrypoint](../../scripts/ops/billing-workload-entrypoint.sh)는 기대 Mongo host/database를 먼저 검사하고 TLS PKCS12를 private 임시파일로 만든 뒤 지문/잔여기간 확인 후 Java를 실행한다. base64 env는 Java 실행 전 해제, TLS password는 설정용으로 유지한다. 실제 비밀값은 저장소에 넣지 않는다.
- 환경: workload `test`, payment `SANDBOX`, scope `tosunsaeng-billing-test`, workload8443, LC receiverON·Identity receiverOFF. Mongo initializeIndexes/requireTransactions ON, 기본schema4. Atlas에서 대상DB 미존재 확인 후 기본15개collection 및 FREE_EXAM_ONCE 정의1개 생성 확인했다. 사용자에게 무료권을 지급한 것이 아니다.
- 강화된 container health:8082 DBhealth 성공 + 검증TLS8443의 무인증 status401 +8082 우회status404. Tomcat HTTPS 기동 및 health 통과를 확인했다. LC signed 요청·업무통합·ALB handshake 검증은 아직 아니다.
- 현재 ALB연결0/공인IP없음/SG8443확대없음. public connector/Identity 수신/결제·환불·삭제·LC발신은 이번에 켜지 않았다. 다음은 정확 host/path/source 제한을 갖춘 ALB 연결의 적용 직전 승인이다.
- 전체회귀1749개 성공(실패0/오류0/skip0),3m56s. rollback 시 task2로 앱을 되돌려도 새로 만든 DB/index는 남는다. 자동DB삭제/인덱스drop은 하지 않는다.

### TLS 주입 사전 검증 — 2026-10-16

- [일회성 검증 스크립트](../../scripts/ops/billing-tls-preflight.sh)를 동일 patched 이미지에서 실행했다. 제품 Java 소스/실행 서비스는 변경하지 않았다.
- ECS task definition `tosunsaeng-billing-test-tls-preflight:1`, task `2798e512fc6342e48696a2e23be90174`는 STOPPED/exit0이며 로그 `TLS_PREFLIGHT_OK java_pkcs12=true fingerprint=true verified_https=true` 확인.
- 기존 execution role로 TLS Secret의 두 field만 주입했다. Mongo URI/LC private key는 제공하지 않고 task role·공인IP·ALB·port mapping 없이 기존 사설 subnet/SG를 사용했다.
- 비root10001·umask077·일회성 task 임시파일로 PKCS12를 만들고 Java keytool 읽기/PrivateKeyEntry, 기대 SHA256, 하루 이상 잔여 유효기간, loopback OpenSSL server와 curl의 인증서 신뢰+hostname 검증을 확인했다. `-k`는 사용하지 않는다. 스크립트는 비밀값 대신 단계/성공만 출력하며 task 종료로 임시파일 수명이 끝난다.
- 이는 **Secrets 주입·Java keystore 호환·loopback HTTPS 검증**이다. Spring/Tomcat 실제 workload connector, ALB target HTTPS, LC JWT 서명/인가·업무 E2E 성공을 뜻하지 않는다.

다음 실제 기동 준비값: `BILLING_WORKLOAD_JWT_ENVIRONMENT=test`, `BILLING_PAYMENT_ENVIRONMENT=SANDBOX`, scope는 배포된 Identity test revision44의 `PHONE_ELIGIBILITY_BINDING_CONSUMER_SCOPE_ID=tosunsaeng-billing-test`와 일치시킨다(상대 설정 변경 없음). workload port는8082/8083과 분리한8443 후보다. TLS Secret→파일 주입과 LC 공개key 연결, `require-transactions=true`, `initialize-indexes=true` 및 schemaVersion4가 필요하다. initializer는 기본15개 collection/index와 승인 FREE_EXAM_ONCE 정의를 생성할 수 있으므로 실제 적용 전 대상 DB/기존 자료 확인이 필요하다. payment/paid/refund/deletion schema·활성화는 별개이며 한꺼번에 켜지 않는다.

인증 준비 후속: LC 테스트 JWT Secret 생성 및 공개키 확인, Billing 내부 TLS Secret 생성(만료2027-10-16 19:31 KST) 확인. 사용자 승인 후 실행역할에 별도 `billing-test-workload-tls-read` policy로 `workload-tls-tgOfqJ` 정확1개 GetSecretValue만 추가·재조회했다. 기존 minimal policy는 유지하며 Billing에 LC private key 읽기 권한을 주지 않는다. 실제 TLS 파일 주입·connector·schema·ALB 연결은 아직 미완료다.

DNS 후속 완료: 사용자가 두 도메인과 적용 직전 검증 CNAME 저장을 승인했다. 가비아에 검증 레코드2개(TTL600)만 추가하여22→24개 확인. ACM 서울 `arn:aws:acm:ap-northeast-2:889384901776:certificate/13f095e9-1b68-47b1-a215-d1acc8383b06`은 ISSUED, `billing-test.to-teacher.com`/`billing-workload-test.to-teacher.com` 모두 SUCCESS다. 아래 인증서 부재·도메인 미승인 표현은 조사 당시 기록이며 이 항목으로 갱신한다. 트래픽 CNAME·ALB 연결·실제 앱/LC 접속은 아직 미완료다.

최종 확인: revision2만 남은 service running1/pending0/rollout COMPLETED. 새 task `fbe9a861727c482ea6a5036bca9393d3`는 RUNNING/HEALTHY이며 아래 patched digest와 일치한다. 이전 revision1은 서비스 deployment 목록에서 빠졌다. 업무/공동 E2E 성공을 뜻하지 않는다.

아래 초기 배포 기록은 이력을 보존한다. 후속 이미지는 `60d5f6c-security1`, task revision2이며 실제 ECR digest는 `sha256:81023a17ed3165d93e9417150a0875486dd29fb2da5885165586d32a05fd8571`이다. Dockerfile에서 공식 Ubuntu 저장소의 openssl/libssl3 업그레이드를 추가했다. archive SHA-256 `e6bf4ae5cc1001c5da0c37a38df86a82612950bbb24ca955c346c983c7db62c4`를 로컬/CloudShell에서 대조했다. 비밀값/네트워크/업무flag 변경 없이 이미지 교체만 요청했다.

- ECR 재스캔 COMPLETE: HIGH/CRITICAL finding 없음, MEDIUM2/UNDEFINED4. 잔여 MEDIUM은 freetype/libfreetype6의 CVE-2026-95512, UNDEFINED는 curl/libcurl4의 USN-8932-1 및 libpng 계열 USN-8899-1이다. 잔여 위험/패치 검토가 필요하며 보안 문제가 전혀 없다는 뜻은 아니다.
- `./gradlew clean test`: 4m3s, 1749개/실패0/오류0/skip0. 제품 Java 소스/계약 불변.
- AWS 계정 Route53 hosted zone0개이며 사용자가 DNS는 가비아라고 확인했다. 현재 ACM 인증서 목록에는 Billing 도메인이 없다. 기존 ALB443에는 다른 서비스 host rule만 존재한다.
- Secrets Manager에서 billing/workload 이름으로 확인된 항목은 Billing Mongo Secret1개, SSM billing 이름 검색 결과0개다. 다른 이름/외부 저장소에 키가 없다고 단정하지 않는다.
- 실제 LC test task revision32에는 Billing 관련 flag 이름4개만 있고 BILLING_BASE_URL/WORKLOAD JWT 환경변수 및 Billing Secret 참조가 없다. LC 코드는 읽기만 확인했으며 상대 배포를 변경하지 않았다.

연결 권장안은 앱용 `billing-test.to-teacher.com`과 서버 전용 `billing-workload-test.to-teacher.com`을 분리하는 것이다(아직 생성/승인된 주소 아님). public connector와 workload TLS connector/target group을 분리하고 workload에는 테스트 NAT source 제한+전용 JWT를 적용한다. 건강검사용8082를 업무 포트로 우회 사용하지 않는다. 다음 준비가 선행되어야 한다.

1. 도메인명 확정, ACM DNS검증용 CNAME과 가비아 ALB CNAME 등록. 실제 값은 인증서 요청 후 생성되므로 임의로 만들지 않는다.
2. LC 담당이 테스트 전용 private JWK를 안전하게 준비/저장하고 Billing에 public JWK set만 제공. private key를 채팅/저장소에 전달하지 않는다. LC sender/base URL/env 설정·배포는 상대 담당 범위다.
3. Billing workload TLS keystore/비밀번호의 안전한 준비·주입 및 환경 exact 매핑 확인. ALB 인증서는 task의 TLS keystore를 자동 공급하지 않는다.
4. 필요한 명시 Mongo schema와 feature gate, shared fixture/E2E 확인 후 기능 활성화. 도메인 연결만으로 유료 입장/환불/삭제를 활성화하지 않는다.

이번 조사에서는 인증서 발급·DNS·ALB/SG 확대·실키 생성·LC 변경을 하지 않았다. 외부 공개 직전 정확한 host/path/source/port 범위를 사용자에게 확인해야 한다.

## 1. 결론

- 사용자 승인으로 기존 ECS 클러스터에 Billing 테스트 서비스만 새로 생성했다.
- `tosunsaeng-billing-test-service`, task definition `tosunsaeng-billing-test:1`, 1대 RUNNING/HEALTHY 및 rollout COMPLETED를 확인했다.
- 결제·public·workload 기능은 기본 OFF이며 인덱스 설치/transaction 요구도 OFF다. 서버 실행 성공은 업무 연동 완료가 아니다.
- 공인 IP 없음, ALB 연결 없음, DNS/기존 서버/Atlas allowlist 변경 없음.
- ECR 기본 스캔에서 HIGH 2건이 발견돼 외부 공개 전에 기본 이미지 보안 보완을 검토해야 한다.

## 2. 배포 구성

| 항목 | 적용 |
| --- | --- |
| 코드 | develop merge `60d5f6c504c0ee56186d4fc344d54c31a68c1497` / PR28 |
| 이미지 | private ECR `tosunsaeng-billing-test`, immutable tag `60d5f6c` |
| 실제 실행 digest | `sha256:7f48b76ce1e3716272136bd89bc088e100ff66287788b51ca0b5a33370a1e458` |
| 실행 | Fargate Linux X86_64 / CPU512 / memory1024 / desired1 |
| 네트워크 | 기존 테스트 private subnet2개 / 공인IP DISABLED / 전용 SG |
| SG | ALB SG발 TCP8082 수신, IPv4 TCP443/27017 송신만 |
| 인증정보 | Secrets Manager `tosunsaeng/test/billing/mongodb`의 `MONGODB_URI` 키 참조. 값 미조회 |
| IAM | 전용 execution role만 사용, application task role 없음 |
| 로그 | `/ecs/tosunsaeng-billing-test`, 14일. 결제 자료 보존기간과 별개 |
| health | 내부 `curl -fsS --max-time 5 http://localhost:8082/actuator/health`, 30초 주기/timeout10초/retry3/startPeriod120초 |
| 복구 | deployment circuit breaker 및 rollback 설정. 첫 배포이므로 이전 성공 revision이 없다는 한계 있음 |

Dockerfile과 .dockerignore는 배포 준비 브랜치의 미커밋 파일이다. 이미지는 이전 bootJar 결과에서 빌드했으며 비밀정보는 포함하지 않았다. CloudShell로 Docker archive를 업로드한 뒤 SHA-256 `31b6b7619dd7090e76c398f63933144673f1666db55b2e2bfead9cdd5d940650` 일치를 확인했다. Docker load/push의 manifest 형식 변환으로 로컬 OCI manifest digest와 ECR digest는 다르며 실행은 ECR digest로 고정했다. ECR push 뒤 Docker logout으로 해당 registry 로그인정보를 제거했다. 임시 archive는 로컬 `/private/tmp`와 CloudShell에 남아 있다.

## 3. 검증 범위

- Atlas `tosunsaeng-test` 프로젝트 IP Access List에 기존 테스트 NAT의 단일 /32가 Active임을 확인했다. 새 허용 규칙을 만들지 않았다.
- ECS task와 container HEALTHY, 서비스 running1/pending0/COMPLETED 및 실행 digest 일치를 확인했다.
- Mongo health를 비활성화하지 않았으며 healthcheck 성공으로 DB 포함 기동 상태를 확인했다. Secret 원문/정확 DB명 재확인, collection 쓰기, transaction/schema 설치 검증은 수행하지 않았다.
- 기동 로그22개에서 started=true, APPLICATION FAILED=false, exception class 목록 없음만 집계했다. 원문 로그/URI를 출력하지 않았다.
- 제품 소스 변경 없음으로 Gradle은 재실행하지 않았다. 직전1749회귀와 이번 실환경 health 확인은 별개다.

## 4. 위험과 다음 작업

- ECR 스캔 COMPLETE: HIGH2/MEDIUM8/LOW4/UNDEFINED4. HIGH는 동일 `CVE-2026-84782`의 `libssl3`와 `openssl` (`3.0.2-0ubuntu1.29`) 두 package finding이다. 스캔 설명상 DTLS 재전송 문제이며 실제 Billing 도달 가능성/수정 버전은 아직 검증하지 않았다. 취약점이 없다고 판단하지 않는다. 외부 공개 전에 기본 이미지 갱신/재스캔 및 나머지 발견사항을 검토한다.
- ALB target/listener/DNS 연결 및 public/workload TLS·JWT 키/상대 연동·명시 schema 설치는 후속 승인/검증 사항이다. 지금은 LC/App 호출 가능 상태가 아니다.
- 업무 flags와 자동삭제/환불은 OFF. 실제 provider adapter, Store 검증 및 공동 E2E를 생략하지 않는다.
- 테스트 서비스는 실행 중이므로 Fargate 비용이 발생한다. 기존 운영 서비스는 변경하지 않았다.
- 이전 잘못 생성한 Project0 Atlas 계정은 사용하지 않으며 별도 사용자 확인 후 정리 대상이다.
