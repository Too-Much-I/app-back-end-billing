# PLAN-007 무료 조회 배포·세션 귀속 이관

## 1. 5줄 결론

1. 사용자 조회는 `GET /api/v1/entitlements` 하나이며 기본 OFF다. [OpenAPI](../openapi/free-entitlements.yaml)
2. 기존 internal 포트 8082와 별도 public 포트 기본 8083을 사용한다. 실제 SG/ALB/Lattice 구성은 이 작업에서 배포하지 않았다.
3. 신규 reserve/confirm은 `sessionOwnerEpoch`를 저장하고 동일 link의 `sessionBindingVersion` CAS로 owner 이전과 직렬화한다.
4. 기존 활성 Session 증빙은 별도 bounded migration으로 확인한다. 조회가 기존 증빙을 고치거나 command TTL을 연장하지 않는다.
5. Identity audience/read 발급, 기존 자료 coverage, 실제 staging 보안·부하 검증 후에만 reader를 활성화한다.

## 2. 반드시 읽을 내용

- [PLAN-007](../plans/PLAN-007-public-free-entitlement-query.md)의 DTO·정책·배포 순서를 유지한다. 결제·RevenueCat·paid 조회는 구현하지 않았다.
- 0개는 새 INITIAL 수량이다. OPEN/RETAKE_AVAILABLE 그룹에서 새 Session으로 추가 차감 없이 시작하는 가능성과 별개다.
- PENDING은 처리 완료 예정이 아니다. projection 없는 Guest에게는 “사용 가능 여부를 확인할 수 없습니다”라고 안내한다. 실제 예약 처리 대기만 5초 간격 최대 6회 자동 갱신한다.
- 응답에 이전 사용자/Session/답안/결과, Claim/Grant/phone candidate/epoch/continuationId를 노출하지 않는다.
- 무료 조건·unit·replacement 상태 순수 predicate를 reserve와 공유한다. GET은 reserve/confirm/expiry/rebind handler를 호출하지 않는다.
- 조회는 primary + Mongo SNAPSHOT Transaction이다. Mongo driver CSOT로 server selection/socket/commit을 포함한 2초 budget, 최대 두 번 snapshot을 시도한다. write Transaction 옵션은 바꾸지 않는다.

## 3. 배포자가 확정·확인할 환경 입력

| 환경변수 | 기본값 | 활성화 조건 |
| --- | --- | --- |
| BILLING_ENTITLEMENT_QUERY_ENABLED | false | 모든 gate 완료 후 true |
| BILLING_PUBLIC_CONNECTOR_ENABLED | false | 별도 target port/SG 준비 후 true, reader OFF와 독립 |
| BILLING_PUBLIC_PORT | 8083 | internal SERVER_PORT와 다름, 1024~65535 |
| BILLING_USER_JWT_ISSUER | 빈 값 | Identity의 exact HTTPS issuer |
| BILLING_USER_JWT_JWKS_URI | 빈 값 | 환경별 HTTPS JWKS. redirect/jku/x5u 사용 안 함 |
| BILLING_USER_JWT_CLOCK_SKEW | 60s | 0~60초 |
| BILLING_LEGACY_ATTRIBUTION_VERIFIED | false | 아래 coverage를 확인한 배포 승인 attest. 자동 이관 스위치가 아님 |
| BILLING_MONGODB_INITIALIZE_INDEXES | false | reader ON 전에 true, schema v4 exact index 검증 |
| BILLING_MONGODB_REQUIRE_TRANSACTIONS | false | reader ON 전에 true, 실제 replica set |
| BILLING_TRIAL_EXPECTED_CONSUMER_SCOPE_ID | 빈 값 | Identity와 합의한 환경별 opaque scope |

값 자체가 gate 완료 증거는 아니다. 실제 ingress 차단·coverage 보고서와 staging 검증을 별도 보관한다. 운영값·실제 ARN·credential은 이 문서에 입력하지 않는다.

Identity 후속은 사용자 JWT의 기존 LC audience를 유지한 배열에 `tosunsaeng-billing` 추가, Guest/MEMBER의 기존 scope에 `billing:read` 추가다. 명시 scope 경로·refresh/로그인/승격/merge 발급까지 확인한다. workload JWT와 `billing:purchase`는 이번 인계에서 제외한다. 기존 토큰은 refresh/재로그인이 필요하다.

## 4. 주요 위험·미확인

- 로컬 projection snapshot은 아직 수신하지 않은 Identity/LC event의 최신성을 보장하지 않는다. reserve가 최종 판정한다.
- JWT는 stateless다. 탈퇴 즉시 token 폐기 여부를 이 조회가 보장하지 않는다.
- 구버전 writer rollback은 새 증빙이 없는 세션을 다시 만들 수 있다. reader만 OFF로 내리고 epoch writer는 유지한다.
- 자동 복구 가능한 legacy 범위를 보수적으로 제한했다. command actor 하나/시간만으로 반복 재가입의 owner 세대를 추정하지 않는다. 아래 두 positive proof 밖은 BLOCKED이며 별도 검증 자료·이관 승인이 필요하다.
- PHONE_REJOIN 다음 USER_MERGED 등으로 최신 phone continuation 경로를 더는 증명할 수 없는 이전 세대 Session은 재응시 ALLOWED로 추측하지 않고 RECONCILIATION_PENDING이다. 과거 학습 데이터를 임의 이전하지 않는다.
- task별 rate limit 60 tokens, 초당 1 refill, 5분 비활동 만료, 최대 10,000 subject다. task 수에 따라 합산 허용량이 증가한다. 부하 검증은 실제 task 수로 수행한다.

## 5. 배포·이관 절차

### 5.1 Writer와 public ingress

1. reader OFF·public connector OFF로 호환 writer를 배포한다. 신규 link epoch=1/bindingVersion=0, reserve/confirm의 CAS, replay 무변경을 확인한다.
2. 내부 통신은 기존 Lattice AWS_IAM/SigV4 포트와 SG를 유지한다. 기존 `latticeOnly`를 false로 바꾸지 않는다.
3. 별도 public 포트 SG는 ALB SG만 허용한다. ALB는 Billing hostname의 exact GET 한 경로만 public target에 전달하고 나머지는 reject한다. public health 용도로 `/actuator/**`를 열지 않는다. 별도 health route를 추가하지 않는 구성에서는 target health check를 인증 없는 GET `/api/v1/entitlements`, success matcher `401,404`로 검증할 수 있다(reader ON=401, OFF=404). 이는 listener liveness만 확인하며 DB/consumer readiness는 schema 초기 검증과 별도 staging gate로 확인한다. 실제 ALB health 설정은 배포 전에 검증한다.
4. public 포트→internal/actuator 차단, internal→public 차단, direct task/위조 forwarded header/다른 환경 role 우회 실패를 실제 AWS에서 검증한다.

### 5.2 Attribution migration manifest v1 (schema v4의 additive field)

대상: 조회 가능한 retained link의 활성/재응시 그룹과 연결된 Session 및 RESERVED Session. 새 collection/index는 없다. 개인정보 수명주기는 기존 parent와 동일하다.

운영 진입점은 Spring bean `SessionAttributionMigration`이다. 일반 앱 startup·scheduler·HTTP route에서 실행하지 않는다. 승인된 운영 실행 환경에서 **명시적 Session ID 목록, 한 batch 최대 100건**으로 호출한다. ID 목록은 접근 통제된 작업 입력이며 로그·Jira·결과 보고서에 복사하지 않는다.

1. read-only inventory로 대상 목록을 확정하고 `inspect(sessionIds)`를 호출한다. 결과는 inspected/eligible/alreadyCovered/blocked/applied **건수만** 포함한다.
2. 다음 positive proof만 자동 이관 대상으로 분류한다.
   - ownerVersion logical 1 + transition 없음: 미이전 subject. 정확한 Reservation/Session subject/group/operation/session 연결을 확인한다.
   - 최신 PHONE_REJOIN transitionId = CONFIRMED Reservation.continuationId: 현재 target의 positive 증거. 동일 Session 연결 검증 필수.
3. 기존 link에 epoch가 없으면 현재 ownerVersion을 baseline으로 초기화한다. 기존 epoch와 일부 증빙이 이미 있으면 일치해야 한다. 기존 Session epoch를 현재 값으로 덮어쓰지 않는다.
4. 이외 대상은 BLOCKED다. 특히 command가 지워진 일반 replacement, 반복 재가입/Guest merge를 command actor나 timestamp 하나로 역추정하지 않는다. 보존 중 command와 검증 가능한 transition chain 또는 별도 승인된 LC 소유 증거를 확보한 뒤 추가 이관 계약을 검토한다. 이 작업은 타 서버 자료 조회/이전을 수행하지 않는다.
5. dry-run 건수·예외 범위에 대한 운영 승인을 받은 뒤 같은 목록으로 `applyApprovedBatch(sessionIds)`를 명시 호출한다. 각 Session 단위 Transaction 안에서 link binding CAS와 Reservation/Session version/null epoch CAS를 수행한다. 경합 실패는 rollback되며 batch를 재조회한다. 이전 batch에서 완료된 건은 COVERED로 수렴한다.
6. inventory를 끝까지 재확인해 blocked=0, 누락 활성 Session=0을 확인한다. 일부 batch의 성공을 전체 coverage로 해석하지 않는다. source Session 자료가 잔존해도 이전 epoch로 유지하며 삭제/재귀속하지 않는다.
7. 확인 보고 후 `BILLING_LEGACY_ATTRIBUTION_VERIFIED=true`를 배포 승인 값으로 설정한다. runtime 결손은 503 + 안전한 `LEGACY_SESSION_ATTRIBUTION_MISSING` 분류로 감지한다. 무기한 PENDING으로 숨기지 않는다.

command TTL·fence cleanup·retention purge는 기존 계약 그대로다. 이관 중 무료권/ledger/owner를 변경하거나 command를 재생성하지 않는다. 기존 legacy RESERVED의 confirm도 증빙 없이는 fail-closed하므로 writer 전환 전 해당 짧은 hold를 drain/expire하거나 별도 승인 이관을 완료한다.

### 5.3 최종 활성화

- Billing OFF 배포 → Identity audience/read 발급 → legacy coverage → staging ON → 운영 reader ON → 프론트 연동 순서.
- 정상 조회/미인증/다른 audience·scope/키 회전/unknown kid/신뢰 JWKS 장애(401과 503), no-store/응답 크기·rate limit을 확인한다.
- 새 무료 대상·이미 사용한 번호·중단 후 재가입·완료 번호·여러 subject에서 reserve와 표시 의미를 비교한다.
- GET 전후 document/count/version/ledger 불변, Mongo snapshot/CSOT·주 저장소·인덱스 explain/부하, 중복·동시 reserve/confirm/rebind를 확인한다.
- 승인 없는 실제 이관·배포·feature flag 활성화는 이번 코드 작업에서 수행하지 않는다.

## 6. 근거와 테스트

- [조회 구현](../../src/main/java/web/tosunsaeng/billing/domain/entitlement/application/EntitlementQueryService.java), [순수 판정](../../src/main/java/web/tosunsaeng/billing/domain/entitlement/application/TrialEntitlementQueryEvaluator.java), [읽기 repository](../../src/main/java/web/tosunsaeng/billing/domain/entitlement/repository/EntitlementQueryRepository.java).
- [JWT verifier](../../src/main/java/web/tosunsaeng/billing/global/config/security/IdentityUserJwtDecoder.java), [public 보안](../../src/main/java/web/tosunsaeng/billing/global/config/security/PublicSecurityConfig.java), [이관 구현](../../src/main/java/web/tosunsaeng/billing/domain/entitlement/application/SessionAttributionMigration.java).
- [Mongo snapshot·command 삭제·CAS 테스트](../../src/test/java/web/tosunsaeng/billing/domain/reservation/repository/ReserveMongoIntegrationTest.java), [상태표·다중 그룹](../../src/test/java/web/tosunsaeng/billing/domain/entitlement/application/TrialEntitlementQueryEvaluatorTest.java), [실제 HTTP·trace](../../src/test/java/web/tosunsaeng/billing/domain/entitlement/api/EntitlementQueryTraceIntegrationTest.java).
