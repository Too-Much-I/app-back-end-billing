# Billing 운영 LC 수신·8443 연결

## 1. 결론

1. 사용자 승인 범위는 Billing 운영 LC JWT 수신·TLS8443·전용 DNS/ALB 연결이며 LC/Identity 배포는 제외한다.
2. 스키마 verify 태스크 `15a193590bf24d09ad460d061362ddb1`는 exit0,41collection,ALL_BLOCKED를 확인했다. 설치/정리 모드는 실행하지 않았다.
3. 기존 prod:5를 보존해 prod:6 등록, workload 이미지 digest와 LC receiver/index check 및8443 mapping만 변경했다.
4. 전용 host CNAME과 ALB120/121/122 전달·129 나머지404를 적용했다. 기존 listener rule 전체 불변을 대조했다.
5. 최종 기동·TLS/거절 검증 결과는 아래에 기록한다. 이 작업은 구매/유료시험/환불·삭제 업무 활성화가 아니다.

## 2. 변경·보존 범위

- 이미지: `889384901776.dkr.ecr.ap-northeast-2.amazonaws.com/tosunsaeng-billing-test@sha256:e5666d537391eebc7cffe91896a756f93b93d8acef2d480030e1b56e9e7597f9` (소스57d6639).
- `BILLING_WORKLOAD_LEARNING_CORE_RECEIVER_ENABLED=true`, `BILLING_MONGODB_INITIALIZE_INDEXES=true`. 운영 DB/host exact guard를 기존 schema3 설정과 대조해 주입. URI/Secret 원문은 기록하지 않는다.
- index initializer는 기존 schema 대조 후 누락 index를 생성할 수 있으므로 설치된 schema의 사전 verify를 수행했다. BenefitCatalogInitializer의 최초 FREE_EXAM_ONCE 정의1건 생성은 별도 사용자 승인했다. 이는 사용자 Grant/TrialClaim 지급이 아니다.
- 환경변수 배열 순서 정규화 후 등록 task 전체 필드를 기대값과 대조해 차이0 확인. 최초 단순 배열 비교 실패는 순서 차이였으며 다른 설정 변경으로 간주하지 않는다.
- 기존 앱8083/health8082·Secret16참조·역할·private subnet·publicIP DISABLED·SG 유지. SG의 ALB→8443 허용은 기존 설정이며 확대하지 않았다. 기동유예300초 유지.
- Identity receiver·구매·provider·환불processing/publisher·삭제·자동정리 등 기존 OFF flag와 DB ALL_BLOCKED는 유지한다. LC 수신 인증 ON은 유료 예약 업무 준비 완료를 뜻하지 않는다. owner/attempt 업무 OFF 경로는 여전히404다.

## 3. 정확한 외부 연결

- `billing-workload-prod.to-teacher.com` CNAME → `tosunsaeng-staging-alb-447454057.ap-northeast-2.elb.amazonaws.com.` TTL600. 기존29개 레코드를 보존하고1개 추가했다. 기존443 listener 인증서에 workload-prod SAN 포함을 확인했다.
- 모든 전달 규칙은 exact host + POST + source-ip `13.124.57.130/32` + 아래 path를 모두 요구한다. 공유 NAT의 신원 구분은 전용 RS256 JWT가 담당한다.

| 우선순위 | 경로 | 동작 |
| --- | --- | --- |
|120|`/internal/v1/reservations`, `/internal/v1/reservations/status`|HTTPS8443 TG|
|121|`/internal/v1/reservations/continuations/phone`, `/internal/v1/attempt-group-events`|HTTPS8443 TG; 업무OFF 경로는 앱에서404|
|122|`/internal/v1/reservations/*/confirm`, `/internal/v1/reservations/*/cancel`|HTTPS8443 TG; strict 앱 matcher 유지|
|129|위 host의 나머지 요청|404 fixed-response|

- TG `billing-prod-workload-8443/260282bdb28e8091`, 서비스 container billing:8443 추가. 기존8083 TG 유지.
- 삭제 및 Identity event 경로는 이번 ALB 허용 목록에 없다. LC host의79 차단 등 다른 작업의 규칙은 그대로 보존했다. ALB404는 앱의 no-store 헤더를 보장하지 않는다.

## 4. 검증·미확인

- 제품 코드 변경 없음. 기존 Java1838/smoke3 결과와 이번 운영기동 검증은 구분한다. 문서 diff 검사 수행, Gradle 재실행 없음.
- 정상서명 업무 처리/중복/환불·삭제 공동E2E는 별도다. LC의 최종봉인/전환과 Identity 구매 준비를 대신하지 않는다.
- 변경 전 서비스/규칙 snapshot은 CloudShell `/tmp/billing-prod-8443-before.json`, 신규rule 기록은 `/tmp/billing-prod-8443-created-rules.json`이다. 임시파일은 영구 백업이 아니다.

## 5. 실제 기동·거절 검사

- prod:6 task `37ae7ab9adc2420d9d56b30eaf15c1eb`: `BILLING_BOOTSTRAP_TARGET_AND_TLS_OK`, 2026-10-18 13:53:58 KST Tomcat8082/8083/8443(https),13:53:59 Started. 조회33로그의 ERROR0. 컨테이너HEALTHY, 양쪽TG 신규target healthy 확인.
- 동일 private subnet/SG에서 실행한 무인증 probe task `2164e7e300094374a5a60b303771c9e4` exit0, `PROBE_ALL_OK`. curl은 기본 trust/hostname 검증을 사용했고 TLS 검증 우회 없음. 토큰 발급·사용자정보·업무 body 전송 없음.

| 검사 | 결과 |
| --- | --- |
|workload POST reserve, confirm 무인증|401|
|workload POST status 잘못된 Bearer|401|
|workload GET reserve|404|
|Identity trial event, 삭제 event|404|
|앱host로 internal reserve 우회|404|
|기존 앱 상품 API OFF|404|
|허용 외 발신지(로컬) POST reserve|404, 일반 DNS/공개TLS 검증 성공|

- CloudShell resolver 일부 조회는 DNS_PENDING/URLError였지만, 일반 로컬 DNS 요청과 실제 서비스와 같은 subnet의 probe는 정상 DNS/TLS로 완료했다. 전파 지연을 숨기거나 `-k`/임의 Host로 대체하지 않았다.
- 처음 probe 실행은 shell entryPoint 검사에서 멈췄고 작업 미실행. 실제 `/bin/sh -c`를 대조한 후8개 검사만 실행했다.

### 배포 후 검증

- prod:6 PRIMARY COMPLETED/실행1/대기0,8083/8443 모두 신규target healthy만 남음을 확인. 이전prod5 task `f134fdf29d42430f9cfe2b94f7ed7dff`는 연결배출 후 STOPPED(exit143) 확인했다.
- 배포 후 verify 태스크 `03665110414b422d9946c1e42d7ea634` STOPPED/exit0,41collection 및 ALL_BLOCKED 재확인. 스키마 재설치/보존기간 변경/구매·환불 자료 변경 없음.
- 정상서명 업무 승인·무료/유료 시험 생성 E2E는 이번 검사에서 실행하지 않았다. LC 키를 가져와 임의 사용자로 요청하지 않았다.
- 증거: `/private/tmp/billing-prod-8443-complete-20261018.png`, `/private/tmp/billing-prod-workload-dns-20261018.png`. 최종 문서diff검사 통과, 이번 Git commit/push 없음.
