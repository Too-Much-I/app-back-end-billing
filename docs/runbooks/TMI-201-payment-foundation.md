# TMI-201 PR1·PR2 구현·검증 안내

- 날짜: 2026-10-12 / 구현 단위: 상품·구매 계정 기반 및 PR2 탈퇴 consumer. 실제 누락 복구·대사·삭제는 후속.

## 1. 5줄 결론

1. 상품 GET·계정 POST, route별 사용자 권한·16KiB 응답/no-store를 구현했다.
2. 계정/ref/cursor 생성과 lifecycle fence는 snapshot/majority+j Transaction으로 처리한다.
3. limiter는 Mongo 고정 UTC 1분으로 products60/account10, 초과 시 잔여 초 Retry-After를 반환한다.
4. core schema v4는 유지하고 수락된16개 index를 PR1의12개·PR2의4개 별도 manifest로 준비한다.
5. 신규 flag는 기본 OFF. 실제 catalog 매핑·PR2/Identity 연동·배포 검증 없이 켜지 않는다.

## 2. 반드시 읽을 내용

`GET /api/v1/payments/products`는 billing:read, `POST /api/v1/payments/purchase-account`는 billing:purchase와 MEMBER가 필요하다. body/query는 받지 않는다. account는 verified sub와 trusted 서버 환경으로 조회한다. 공개 응답 envelope/정확 DTO는 [PLAN-009 기반 계약](../contracts/PLAN-009-payment-foundation-technical-contract.md)을 따른다. ref는 인증 수단이 아니다. 계정 생성은 지급·결제가 아니다.

## 3. 실행 승인 및 입력

- `BILLING_PAYMENT_CATALOG_ENABLED=false`, `BILLING_PAYMENT_PURCHASE_ACCOUNT_ENABLED=false` 유지.
- `BILLING_PAYMENT_WITHDRAWAL_ENABLED=false` 유지. 수신 단독 활성화는 public flag/JWT/limiter를 요구하지 않지만 internal ingress·transaction/index와 환경 검증은 필수다. staging↔SANDBOX, production↔PRODUCTION이 다르면 시작 실패한다.
- 활성화 전 PUBLIC_CONNECTOR, Identity issuer/JWKS, skew0..60초, transaction/index 검증을 준비한다. 무료 legacy gate를 결제 준비로 대신 충족시키지 않는다.
- `BILLING_PAYMENT_ENVIRONMENT=SANDBOX|PRODUCTION`, 양수 `BILLING_PAYMENT_CATALOG_VERSION`, Secret store의 `BILLING_PAYMENT_LIMITER_KEY`(Base64, decoded32bytes 이상)를 준비한다. 실제 키를 문서/코드에 남기지 않는다. 키 회전은 별도 협조 이관 없이는 금지한다.
- PR1만으로 구매 계정 활성화하지 않는다. PR2 탈퇴 수신 및 Identity200/202, staging 검증과 운영 승인이 필요하다.

## 4. 위험·미확인

실제 ALB exact route allowlist·direct task 차단·Identity purchase 발급·탈퇴 전달·RC/Store 연동은 이번 로컬 테스트로 검증되지 않는다. 추후204 recovery control/generation이 추가되면 모든 관련 writer가 동일 fence를 거치도록 확장해야 한다. 지금 구현을 full recovery-ready로 취급하지 않는다. 과거에 매핑을 발급한 환경에 account/cursor가 누락돼 있다면 승인된 migration 없이는 새 ref로 복구하지 않는다.

## 5. 동작과 운영 절차

### DB와 catalog

`payment-foundation-indexes.json`은 core version을 바꾸지 않는 PR1 subset이다. lifecycle을 포함하고 `payment-withdrawal-indexes.json`은 PR2의 inbox/cleanup4개 index를 추가한다. withdrawal flag가 ON일 때 두 subset을 검증한다. 자동 index drop/recreate 없음. key 순서/방향/unique/partial/TTL/sparse/collation 불일치 시 시작 실패. catalog reader는 자료를 생성하지 않는다.

실제 catalog는 승인된 별도 versioned seed/migration으로 준비한다. 여기서는 가짜 Store ID를 운영 DB에 넣는 seed를 제공하지 않는다. 절차는 (1)양 flag OFF 및 기존 요청 drain (2)환경·스토어·RC app/product/package 실매핑과5상품 기간/기준가격 검증 (3)승인된 migration으로 불변 매핑을 처음에는 saleEnabled=false로 저장 (4)index/중복·catalogVersion·필수 필드 검사 (5)승인된 Store 매핑만 판매 대상 지정 (6)staging/상대 연동 후 flag 활성화다. 정확 운영 migration 명령·rollback은 PLAN-013 실행 승인 대상이다. 기존 product ID를 다른 기간으로 재사용하지 않는다.

서버에 매핑이 없는 Store는 반환하지 않는다. 모든 매핑 비활성은 정상 빈 배열이지만 활성 매핑 정합성 오류/DB 실패는503이다. 실제 표시는 Store SDK 가격을 결합한다.

### 계정·재시도

계정/ref/ACCOUNT_LOOKUP cursor는 하나의 Transaction에서 생성한다. 반복 호출은 같은 ref를 반환하고 account version/createdAt을 바꾸지 않으며 lifecycle version과 cursor lastActivityAt만 조건부 갱신한다. lease/notBefore/nextAttemptAt은 반복 호출에서 덮어쓰지 않는다. WITHDRAWN이면403, 연결 불명은503. auth deadline을 Transaction 안에서 재검사한다.

결제 전용 Transaction은 전체2초 관측 budget/최대32회 transient·unique 경합 시도이며, commit unknown은 동일 session/transaction에서 최대3회 commit 시도 후 불명이면503이다. 성공을 추정하지 않고 클라이언트 재시도는 같은 사용자/환경의 기존 계정으로 수렴한다. driver/network가 budget을 넘긴 외부 지연은 실제 staging에서 관측한다.

### 관측

HTTP span 아래 payment_products/payment_purchase_account INTERNAL span을 생성하고 정상/예외 모두 종료한다. 시간·service·environment·operation·outcome·durationMs·traceId/spanId만 로그한다. user/ref/token/payload·DB 예외 원문을 로그·metric·span에 붙이지 않는다. limiter의subjectKey는 환경+sub length-prefixed HMAC이며 raw userId/IP를 limiter에 저장하지 않는다.

## 6. 테스트·후속

최신 PR2 포함 전체 결과: 2026-10-12 `./gradlew clean test` **393개, 실패0·오류0·skip0**. PR1 대비30개 추가. strict wire/정밀도/미래시각, 실제 Controller204·400/422/409/503, unsigned/wrong role/defaultOFF/public-port 거절, 동일 사용자 발급-탈퇴10회 경합·동시 중복·전체 rollback·환경 격리·overflow·원시 식별자 없는 inbox·15일 후보·무료 불변·4개 index/TTL drift와 span 종료를 검증했다.16개 production index와 수락 fixture가 일치한다. 직접 AWS/Identity 호출·실제 성공 commit 응답유실·다중노드 failover는 검증하지 않았다.

2026-10-12 `./gradlew clean test`: 총363개, 실패0·오류0·skip0. 기존326개에 결제37개를 추가했다. 실제 Controller 정상/예외 span 종료·민감정보 미포함, inactive reference 비회전 및 기존 무료/internal 회귀를 포함한다. JSON manifest 파싱·수락 subset 일치 검증과 문서 diff 검사도 수행했다. 실제 서비스 배포·Store/RevenueCat 호출은 하지 않았다.

PaymentControllerTest는 실제 controller 요청과 route/scope/account_type/flag/오류/응답 헤더를 검사한다. 기존 JWT 서명·audience·issuer 검증은 기존 decoder 회귀를 유지한다. PaymentFoundationIntegrationTest는 disposable replica-set의 동시 생성·rollback·WITHDRAWN·cursor 보존·limiter·index 및 오류 라벨 주입을 검증한다. injected unknown commit은 실제 성공 commit 응답유실/다중노드 failover의 증명이 아니며 후속 gate는 그대로다.

PR2는 탈퇴 strict consumer/inbox·계정/ref 비활성·cleanup 후보 및 생성/탈퇴 동시 Transaction을 구현했다. 실제 구매·기간권·환불·자동삭제는 포함하지 않는다. 병합·Jira 종료는 사용자 후속 요청이며 실제 연동 gate 완료와 구분한다.

## 7. PR2 탈퇴 수신과 배포 조건

- POST `/internal/v1/payments/accounts/withdrawal/events`: Identity만 Lattice AWS_IAM/SigV4 POST 허용. 기존 LC JWT 경로/다른 internal route 권한은 불변이다. 운영 애플리케이션은 기존 Lattice 인증 완료 신뢰 경계를 유지하며 직접 SigV4를 재검증하지 않는다. unsigned/wrong role/direct task 차단은 **실제 Lattice policy·SG 검증이 별도 필수**다. 테스트 principal 성공을 실제 AWS 검증으로 간주하지 않는다.
- Billing public connector/ALB로 이 경로를 열지 않는다. 로컬 public connector guard는 내부 경로를404로 차단한다. consumer flag OFF는 security deny, 실행 중 OFF 변경은503으로 실패하며 정상 수신인 것처럼 ACK하지 않는다.
- 4필드 strict decode→inbox 비교→lifecycle CAS→account WITHDRAWN/ref inactive→cleanup 후보→inbox를 한 Transaction으로 commit한 뒤 body 없는204. 같은 event/digest와 같은 탈퇴시각의 다른 event는204, digest 충돌/다른 탈퇴시각409, 잘못된 payload400/지원하지 않는 schema422, DB 실패503+Retry-After5. 계정 없는 탈퇴도 fence/후보만 만들고 구매 계정을 발급하지 않는다.
- 기존 account/ref ID와 금융 연결은 남는다. 반복 알림은 상태 version/최초 시각/cleanup 일정/cursor lease를 초기화하지 않는다. 과거 탈퇴가 늦게 와도 15일 기산은 수신 시각이 아닌 withdrawnAt이다. Date와 canonical text로 legacy 정밀도를 함께 보존한다. 실제 cleanup worker/거래 확인·권리 이전·자동 환불 없음.
- HTTP 아래 `payment_withdrawal_consume` span을 decode부터 commit까지 생성한다. 정상·오류 종료, 저카디널리티 outcome/duration metric과 식별자 없는 시간/service/environment/trace 로그. exception 원문·event/user/ref/digest/payload/header를 기록하지 않는다. W3C traceparent 및 baggage 미전파는 기존 전역 tracing 설정을 유지한다.
- 배포 전: flag OFF로 코드 준비→16개 index와 트랜잭션 검증→Identity200/202 및 실제 IAM route policy/SG 준비→staging 중복/응답유실/발급-탈퇴 경합 검증→별도 승인하에 consumer 먼저 활성화 후 publisher 활성화.204 recovery/206 coverage gate 충족 전 public 구매 계정·판매를 켜지 않는다. rollback 시 publisher 정지·Identity durable capture 유지, 기존 DB/index를 지우거나 탈퇴 상태를 ACTIVE로 되돌리지 않는다.
