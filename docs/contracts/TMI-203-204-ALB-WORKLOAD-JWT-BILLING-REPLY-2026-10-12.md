# Identity 회신 — ALB·workload JWT 전환 Billing 검토

- 날짜: 2026-10-12 / Billing 브랜치: feat/TMI-204-billing-withdrawal-recovery
- 대상: TMI-203·204, 영향 TMI-199·202·206 및 Identity 이벤트
- 상태: 사용자 첨부 인계의 방향 승인 확인에 따른 **Billing 기술 검토·권장안**. 아래 exact 후보는 공동 동결/구현/배포 승인이 아니다. 실제 메시지 전송·Jira 수정 없음.
- 후속 사용자 승인(2026-10-12): §3·§7 권장안을 [TMI-204 계획 §2.5](../plans/TMI-204-withdrawal-recovery.md)에 채택하고 TMI-204 본문에 반영한다. 아래 검토 당시의 후보/미승인 표현은 이 계획 채택으로 갱신하되 양 서버 exact fixture 최종 수락·실제 환경 확인·코드/배포 승인을 뜻하지 않는다. 실제 port/origin/source 환경/TLS 구성/키 운영 담당은 미확인으로 유지한다.

## 1. 5줄 결론

1. 기존 ALB + 서버 전용 JWT 방향은 구현 가능하다. 구매/탈퇴/snapshot/feed의 도메인 로직은 유지할 수 있다.
2. Billing의 현재 Lattice 수신 모드는 애플리케이션에서 해당 route를 `permitAll` 처리하므로 ALB만 연결해서는 안 된다.
3. workload 전용 발급기·검증기·connector/security chain을 먼저 만들고 사용자 JWT와 Learning Core 경로를 분리하는 안을 권장한다.
4. 인계 영향 목록에 `/internal/v1/eligibility/trial/events`도 추가해야 한다. 실제 전환 여부와 sender 배포 상태는 별도 확인한다.
5. 로컬 기본 OFF는 확인했으나 운영 배포/환경변수/ALB 경로는 미확인이다. 미배포·최초 적용을 단정하지 않으며 기존 SigV4를 지금 제거하지 않는다.

## 2. 반드시 읽어야 하는 내용

### 2.1 확인한 Billing 구현

| 항목 | 로컬 확인 사실 | 확인하지 못한 것 |
| --- | --- | --- |
| 복구 outbound | `SigV4IdentityRecoveryClient`, `RecoveryHttpTransport`, `RecoveryHttpConfiguration` 존재. SDK2.29.52, Lattice DNS 검증, 기본 HTTP OFF | 실행 중 ECS/task/env, 실제 Identity 호출·활성화 |
| 이벤트 inbound | `SecurityConfig`의 DISABLED/TEST/LATTICE_AWS_IAM 모드. Lattice 모드는 외부 IAM 인증을 전제로 허용 route를 permitAll | 실제 Lattice/ALB/SG 구성, 배포된 인증 모드 |
| 사용자 API | 별도 public connector/사용자 JWT chain. `PublicIngressFilter`가 해당 port의 internal 경로를 허용하지 않음 | 실제 ALB listener/target group 매핑 |
| 현재 검증 기록 | 직전 SigV4 구현 전체 로컬 테스트 848개 성공 기록 | 새 JWT 방식의 독립 검증·실환경 보안 검증 |

`application.yml`의 internal mode=disabled, recovery HTTP/feed/snapshot worker=false는 **저장소 기본값**이다. 운영 환경의 override가 없다는 증거가 아니다. 첨부의 Identity ECS/ALB 조회 및 SCP 거부는 상대 확인 내용이며 Billing이 독립 재검증한 결과가 아니다.

### 2.2 주소만 변경하면 안 되는 이유

현재 `LATTICE_AWS_IAM` 모드는 Lattice와 격리된 task ingress를 신뢰하고 애플리케이션에서 workload JWT를 검증하지 않는다. 해당 port를 ALB로 노출하면서 이 모드를 유지하면 그 신뢰 전제가 깨진다. 기존 SigV4 header 유무나 forwarding header로 ALB 경유를 인정해서도 안 된다.

권장안은 기존 ALB를 재사용하되 **workload 전용 target group + 별도 connector + 우선순위가 분리된 security chain**을 사용하는 것이다. 새 ALB가 필수라는 뜻은 아니다. 새 connector의 port는 인프라 충돌 점검 후 확정한다.

- workload connector에서는 정확한 Identity POST route만 JWT 검증 뒤 허용하고 나머지는 거절한다.
- 사용자 public connector의 internal 거절 규칙은 유지한다. 기존 public allowlist를 통째로 넓히지 않는다.
- 기존 Learning Core Reservation/Attempt 경로는 이번 Identity–Billing 전환에 자동 포함하지 않는다. 새 workload connector에서는 거절한다.
- 기존 internal connector의 Lattice 모드는 기존 격리가 입증된 경우에만 유지한다. 입증되지 않으면 해당 경로 활성화도 보류한다.
- ALB는 host/path를 정확히 전달하고 task workload port는 해당 ALB SG에서만 접근하도록 제안한다. 같은 ALB의 SG만으로 호출 서비스 신원을 식별할 수 없으므로 JWT 검증은 항상 필요하다.
- 공개 ALB의 internal 경로 노출 허용 여부는 별도 확인한다. 경로 이름만으로 비공개가 되지 않는다. 외부 TLS와 ALB→task 구간의 TLS/격리 요구도 함께 동결한다.

## 3. 공동 확정이 필요한 권장안

아래는 **새 exact 후보**다. 현행 ADR/AGENTS의 SigV4 전용 규칙을 자동 대체하지 않는다. 구현 전에 해당 범위만 명시적으로 supersede하고 사용자 승인 및 양 서버 수락을 기록한다.

### 3.1 issuer·audience·subject·route 후보

`{env}`는 `staging` 또는 `production`이며 SANDBOX→staging, PRODUCTION→production으로 고정 매핑한다. 토큰/body가 환경을 선택하지 않는다. issuer/audience는 다음 논리 식별자를 제안하며 실제 HTTPS origin과 혼동하지 않는다.

- Identity issuer 후보: `urn:tosunsaeng:workload:identity:{env}`
- Billing issuer 후보: `urn:tosunsaeng:workload:billing:{env}`

| 발급자 → 검증자 | sub | audience 후보 | 허용 POST route |
| --- | --- | --- | --- |
| Identity → Billing | identity-service | `tosunsaeng-billing:withdrawal-events:{env}` | `/internal/v1/payments/accounts/withdrawal/events` |
| Identity → Billing | identity-service | `tosunsaeng-billing:trial-eligibility-events:{env}` | `/internal/v1/eligibility/trial/events` |
| Identity → Billing | identity-service | `tosunsaeng-billing:trial-owner-events:{env}` | `/internal/v1/eligibility/trial/owner/events` |
| Identity → Billing | identity-service | `tosunsaeng-billing:user-merge-events:{env}` | `/internal/v1/owners/merge/events` |
| Billing → Identity | billing-service | `tosunsaeng-identity:withdrawal-recovery:{env}` | recovery-r1의 4 POST(아래) |

복구 4 route: `/internal/v1/billing/withdrawals/snapshots`, `/internal/v1/billing/withdrawals/snapshots/status`, `/internal/v1/billing/withdrawals/snapshots/page`, `/internal/v1/billing/withdrawals/feed/page`.

각 audience는 한 목적만 허용한다. 위 URN·audience 문자열은 상대 수락 전 확정값이 아니다. 기존 사용자 `tosunsaeng-billing` audience, `billing:read`/`billing:purchase`, LC workload purpose를 재사용하지 않는다. 검증된 issuer/sub/audience 조합으로 service principal과 consumer=billing을 고정 매핑한다. payload의 기존 userId/consumerScopeId 검증은 별도로 유지한다.

### 3.2 JWT와 요청 규칙

- RS256만 허용, 별도 RSA workload 키(최소2048bit), protected header의 kid 필수. 사용자 signing key·cursor/support HMAC 키와 분리하고 환경/서비스 간 private key를 공유하지 않는다.
- `iss/sub/aud/iat/exp/jti` 필수. aud는 위 목적의 단일 문자열로 제안한다. iat/exp는 정수 NumericDate, `0 < exp-iat <= 60초`, `iat <= now+30초`, `now < exp+30초` 검사. nbf가 있으면 정수·시간 관계와 `nbf <= now+30초`를 검사한다.
- jti는 발급 시 새 UUID v4로 제안하되 일회용 소비 저장소는 만들지 않는다. 존재만으로 replay 방지가 되지 않는다. 허용 skew 때문에 exp 뒤 최대30초까지 검증에 통과할 수 있다.
- 매 HTTP 시도 직전에 새 JWT를 발급한다. 재시도 eventId/operationId/body는 동일하게 유지한다. 토큰을 outbox/command에 저장하거나 로그에 남기지 않는다.
- `Authorization: Bearer` 단일 header만 허용하며 중복/병합 헤더·query token은 거절한다. 토큰의 jku/x5u/임의 issuer로 key URL을 선택하지 않는다. 지원하지 않는 alg/critical header도 거절한다.
- 인증 실패401, 검증된 principal의 목적/subject/route 권한 불일치403으로 제안한다. 공개 오류는 고정값으로 반환하고 내부 운영 분류만 저카디널리티로 남긴다.
- clock 동기화/오차 경보가 필요하다. skew를 늘려 장애를 감추지 않는다.

추천 초기 보장은 **TLS + 60초 JWT + route 권한 + 기존 event/command 멱등성**이다. 일반 Bearer JWT는 method/path/body를 암호학적으로 서명하지 않는다. 별도 body 서명을 추가하지 않는 선택은 공동 보안 수락사항으로 명시해야 하며, SigV4와 동일한 보장이라고 쓰지 않는다. 같은 토큰의 유효기간 내 재사용 자체는 가능하다. 멱등성은 같은 event/operation의 중복 효과를 막을 뿐 탈취된 credential의 임의 요청을 막는 수단은 아니다.

### 3.3 키 제공·교체 책임 — 초기 단순안

초기는 인계서가 허용한 **배포된 public JWK set** 방식을 권장한다. 호출 서버만 private key를 가지고 상대는 배포 설정에 pin된 공개키만 보유한다. 새 JWKS 공개 endpoint·runtime 외부 key 조회는 필수로 만들지 않는다.

| 책임 | 담당/제안 |
| --- | --- |
| Identity signing key | Identity 환경 운영자 생성·보관·교체, Billing에는 공개키만 제공 |
| Billing signing key | Billing 환경 운영자 생성·보관·교체, Identity에는 공개키만 제공 |
| Private key 주입 | 각 서버 전용 Secret/최소 read 권한. 실제 생성·권한 변경은 이번에 수행하지 않음 |
| Public key 검증 | kid 유일성·RSA/RS256·공개키만 수락, 설정 누락/손상은 startup fail-closed |
| Unknown kid | 401, 네트워크 refresh 없음; 공격자가 임의 URL 조회를 유도하지 못함 |
| Cache/timeout | 원격 조회 미사용이라 JWKS HTTP cache/timeout 없음. key set은 현재 배포 설정의 수명이며 교체 완료 조건에 폐기키 제거를 포함 |
| 정상 rotation | 상대 검증기에 old+new 먼저 배포→모든 replica 수락 확인→발급기 new 전환→old 발급 replica 완전 종료 확인→마지막 old 발급 후 최소120초 경과 확인→상대에서 old 제거 |
| 긴급 유출 | 유출키 신뢰 제거·호출 중단/인증 blocked를 우선. 정상 rotation의 overlap을 강제하지 않음 |

120초는 최대 TTL60+미래 iat 허용30+expiry skew30을 포함한 최소 경계이며, 배포 시간 자체를120초로 가정하지 않는다. old 발급기가 남아 있거나 실제 발급 종료를 증명할 수 없으면 제거 시점을 계산할 수 없다. 상대 전체 replica의 새 key 배포 확인 및 키 폐기 작업 추적이 필요하다. 키 rotation/유출 복구 runbook과 담당자를 정해야 한다.

장점은 JWKS 장애·refresh 경쟁·공개 endpoint 운영이 없다는 점이다. 단점은 교체 시 양쪽 배포가 필요하고 긴급 폐기도 배포 지연 영향을 받는다는 점이다. 무중단/신속 원격 rotation이 요구되면 고정 HTTPS JWKS로 바꾸되 cache 상한·stale 신뢰 상한·unknown kid refresh rate limit·timeout·긴급폐기 전파를 별도 동결한다. 두 방식을 자동 fallback으로 섞지 않는다.

## 4. 주요 위험과 미확인 사항

- Billing 운영 배포/실제 host/SG/인증 모드 미확인. 따라서 기존 client 없음을 전제로 삭제하거나 최초 적용으로 확정하지 않는다.
- 인계 부록에 없는 Trial eligibility route를 누락하면 신규 phone 자격 이벤트만 기존 인증에 남을 수 있다. 전환 범위/순서를 이벤트별로 추적해야 한다.
- LC→Billing 기존 경로를 Identity JWT에 열거나 Lattice permitAll을 ALB에 노출하면 서비스 권한 격리가 깨진다.
- 전용 JWT라도 signing key 유출 시 허용 목적의 요청을 위조할 수 있다. Secret 접근 제한·rotation·비로깅·운영 감사가 필요하다.
- 기존 ADR-002/AGENTS와 방향이 충돌한다. 새 방향 승인 사실과 아직 미확정 exact를 분리해 기록하며 구현 전 범위 한정 개정이 필요하다.
- 기존848개 성공은 이전 구현의 로컬 결과다. 새 JWT 동작/ALB 보안을 검증한 수치로 재사용하지 않는다.

## 5. 수정 범위·PR 순서 권장

1. **계약 PR:** 인증 전용 새 revision 작성. ADR-002·관련 ADR-004/PLAN-009/TMI-204·공동계약·AGENTS/결정서의 Identity–Billing 범위만 정합화. 기존 TMI-199 및 recovery body fixture/hash/과거 수락 기록은 보존. 새 auth fixture에는 issuer/aud/sub/키/시간/header/route 음성 사례 추가.
2. **Billing 수신 PR:** 전용 connector/security chain·Identity workload 검증기·목적별4route 권한·기본 OFF. public 사용자/LC 경로 거절 회귀. 기존 strict decoder/inbox/transaction은 변경하지 않음.
3. **Billing 발신 PR:** 자체 workload 발급기·HTTPS JWT recovery adapter·exact origin pin·기본 OFF. 기존16KiB/timeout/redirect차단/Retry-After/401·403 중단 정책과 DB 복구 코어 재사용. 인증방식은 명시 선택하며 실패 시 SigV4/JWT 상호 fallback 없음.
4. **공동 검증 PR/운영 gate:** Identity 신규 receiver/sender와 상호 검증. deployment inventory→키 배포→receiver 준비→sender 기능별 전환→staging 검증→승인 활성화. 기존 SigV4 제거는 실제 사용처 없음과 rollback 계획 확인 뒤 별도 수행.

필수 테스트: 사용자 JWT·LC JWT·wrong env/issuer/aud/sub·잘못된 alg/kid·서명 변조·만료/미래iat/과도 TTL·중복 Authorization·무토큰·wrong method/path/port·forwarding 위조·direct task 차단·flag OFF. 정상/긴급 rotation, 키 누락/손상·clock 장애·응답 유실/같은event/operation중복·auth 중단/승인 재개, snapshot/feed 및 무료권 회귀를 포함한다.

현재 변경은 검토 문서와 작업기록뿐이다. 코드/SDK 제거·배포설정·AWS/키/Secret/Jira 변경은 하지 않았다. Identity에 실제 메시지를 보내지 않았으며 이 문서를 전달 자료로 사용할 수 있다.

## 6. 근거

- [현재 internal 인증](../../src/main/java/web/tosunsaeng/billing/global/config/security/SecurityConfig.java), [모드](../../src/main/java/web/tosunsaeng/billing/global/config/security/InternalIngressProperties.java)
- [사용자 connector/chain](../../src/main/java/web/tosunsaeng/billing/global/config/security/PublicSecurityConfig.java), [public ingress 제한](../../src/main/java/web/tosunsaeng/billing/global/config/security/PublicIngressFilter.java)
- [현재 SigV4 설정](../../src/main/java/web/tosunsaeng/billing/domain/payment/recovery/RecoveryHttpConfiguration.java), [구현 안내](../runbooks/TMI-204-identity-sigv4-client.md), [기본 설정](../../src/main/resources/application.yml)
- [ADR-002](../adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md), [TMI-204 계획](../plans/TMI-204-withdrawal-recovery.md)
- 사용자 첨부: 「Billing 인계 — 기존 ALB 재사용·서버 전용 JWT 인증으로 전환」, 2026-10-12. 첨부의 AWS 조사 결과는 상대 보고로 인용했으며 Billing 운영 현황으로 일반화하지 않았다.

## 7. Identity 재검토 반영 — 환경·포트·경계값 보완 후보

2026-10-12 Identity 검토 회신을 반영한 추가 제안이다. **공동 exact 동결이나 구현 승인으로 취급하지 않는다.** 이 절의 환경안은 §3.1의 staging/production 두 환경만으로 충분하다는 가정을 보완한다. 기존 source/cursor/DB 환경값은 변경하지 않는다.

### 7.1 환경명은 세 종류를 구분한다

1. 배포 대상: 어떤 ECS 서비스에 실행되는가.
2. workload trust environment: 어떤 상대 issuer/audience/key/origin을 신뢰하는가.
3. 데이터 환경: Billing SANDBOX/PRODUCTION 및 Identity WITHDRAWAL_ENVIRONMENT/stream/cursor가 어디에 귀속되는가.

Identity workflow 정적 확인상 develop은 test 대상, main은 staging tag의 기존 서비스 대상이다. **main=production이라는 근거가 아니다.** WITHDRAWAL_ENVIRONMENT는 외부 주입이며 workflow의 tag에서 자동 결정되지 않는다. 실제 task definition/env 값은 이번에 조회하지 않았다.

| Identity 배포 대상(정적 근거) | workload trust 후보 | Billing payment 환경 후보 | Identity source 환경 | 활성화 조건 |
| --- | --- | --- | --- | --- |
| develop → test 서비스 | test | SANDBOX | 실제 WITHDRAWAL_ENVIRONMENT 확인 필요 | test 전용 Billing origin/DB·키·stream 대응 확인 |
| main → staging tag 서비스 | staging | SANDBOX | 실제 WITHDRAWAL_ENVIRONMENT 확인 필요 | staging 전용 Billing origin/DB·키·stream 대응 확인 |
| 실제 production 대상 | production | PRODUCTION | 실제 값/대상 미확인 | production 배포 inventory 확인 전 OFF |

**권장: test와 staging을 별도 신뢰영역으로 취급한다.** `{env}` 후보를 test/staging/production으로 확장해 issuer/audience/key/origin을 분리한다. Billing의 payment environment enum에 TEST를 임의 추가하지 않고 별도 workload 환경 설정으로 구분한다. 이름만 분리해서는 부족하다. 현재 Billing 복구 control은 payment environment/consumer로 조회하므로 test와 staging이 같은 DB에서 모두 SANDBOX를 사용하면 충돌할 수 있다. 초기안은 DB도 분리하거나 한 쌍만 먼저 활성화한다. 같은 DB에 병행 수용하려면 별도 schema/namespace 설계가 필요하므로 이번 인증 변경에 숨겨 넣지 않는다.

SANDBOX→staging 자동 매핑 대신 배포 시 `(workload 환경, payment 환경, Identity source 환경, origin, key set, stream)`의 승인된 대응을 설정/검증해야 한다. source environment는 기존 저장값을 유지한다. JWT 환경 전환만으로 stream 교체·cursor 재해석·DB 값 치환을 하지 않는다. 현재 배포값이 확인되지 않은 칸을 추측으로 채워 동결하지 않는다.

### 7.2 양쪽 receiver의 포트 격리

Identity `RecoveryHttpConfiguration`을 직접 읽어 path matcher 기반이며 local port 조건이 없음을 확인했다. 다음은 양쪽 모두에 적용할 후보다.

| 수신 서버 | 전용 workload connector | public connector | 그 밖의 connector |
| --- | --- | --- | --- |
| Billing | Identity의 승인된4 POST만 JWT/목적 검증 뒤 허용 | internal route 거절 유지 | 기존 LC 등 계약만 유지; 신규JWT를 우회 수용하지 않음 |
| Identity | Billing recovery4 POST만 JWT/목적 검증 뒤 허용 | recovery prefix 전체 거절 | recovery 신규 요청 거절; 기존LC/user 계약은 유지 |

정확 port 번호는 양 서버의 실제 listener/컨테이너 port 충돌 점검 후 확정한다. matcher가 path 또는 port 한쪽만 확인해 다른 chain으로 떨어지지 않도록, workload port의 미허용 경로와 다른 port의 workload 경로를 모두 거절하는 회귀를 둔다. forwarded host/port/header로 connector 신뢰경계를 선택하지 않는다. flag OFF에서도 해당 경로가 public/user chain으로 빠지지 않아야 한다.

ALB host/path allowlist와 전용 target group을 양방향으로 확인한다. task는 private subnet/public IP 없음 및 해당 ALB SG→전용port 제한을 권장한다. 실제 구성이 그렇다고 확인한 것은 아니다.

ALB→task 구간은 아직 미확정이다. 권장 보완안은 HTTPS target으로 구간 암호화하되, **ALB의 HTTPS target 연결은 target 인증서 검증 기반의 mTLS/서버 신원 보장과 같지 않다**는 점을 명시하는 것이다. SG/네트워크 격리가 별도로 필요하다. 기존 HTTP target을 유지하려면 TLS 종료 뒤 평문 구간의 제한된 신뢰경계와 위험을 명시 수락해야 한다. 어느 안이든 기존 ALB 재사용만으로 내부 평문 전송·공개경로 노출까지 승인되었다고 보지 않는다.

### 7.3 시간·JSON·오류 exact 후보

- iat/exp/선택 nbf는 **0 이상 signed int64 범위의 JSON integer token**만 수락한다. 문자열·소수·지수표기·overflow는401. 시간 비교는 now를 임의로 초 단위 내림하지 않고, 안전한 범위 검사/연산을 사용한다.
- `1 <= exp-iat <= 60`, `iat <= now+30초`, `now < exp+30초`. nbf가 있으면 `iat <= nbf < exp` 및 `nbf <= now+30초` 모두 필요하다.
- protected header와 payload의 duplicate key·trailing token은401. JOSE compact/JWS/base64url 및 알고리즘을 검증하고 token parser가 중복 claim을 마지막 값으로 덮어쓰지 못하게 한다.
- 최소 경계 fixture: TTL0/1/60/61, iat=now+30 허용/그 초과 거절, now=exp+30 거절/직전 허용, nbf=iat 허용·nbf<iat 및 nbf=exp 거절, nbf=now+30 허용/초과 거절, Long.MAX_VALUE·초과·연산 overflow 거절.
- issuer는 고정 trusted 설정에서만 선택한다. 비신뢰 issuer·unknown kid·잘못된 서명/키/시간/claim 형식은401. 신뢰 issuer/key로 검증된 토큰의 **형식상 정상이나 다른** subject/audience 및 미허용 method/route는403.
- 단일 문자열 aud 대신 배열/null/누락은 형식 오류401로 제안한다. 사용자 키/LC 키는 workload trusted key set에 없으므로401; workload 키로 서명됐으나 잘못된 목적이면403이다.
- public/잘못된 connector에서 workload 경로를 차단하는 ingress 응답은 인증 분류와 별개로404를 제안한다. flag OFF의 허용되지 않은 경로도404 후보. 새401/403 규칙을 기존 business error body/204 계약과 혼용하지 않는다.

Spring 기본 JWT validator가 audience를 인증 단계에서 거절하면401이 될 수 있다. 제안된403을 구현하려면 서명/구조/issuer/시간 인증과 subject/audience/route 인가 단계를 의도적으로 분리하고 HTTP fixture로 결과를 고정해야 한다. 설정만 바꿔 자동으로 동일해진다고 가정하지 않는다.

### 7.4 배포 public JWK set 검증·교체 보완

public JWK set은 유효 RSA public n/e, 최소2048bit, 유일한 비어 있지 않은 kid를 검증한다. 초기 exact 후보는 `kty=RSA`, `alg=RS256`, `use=sig`, `key_ops=[verify]`를 명시적으로 요구한다. d/p/q/dp/dq/qi/oth 등 private parameter 또는 대칭키 k를 포함하면 설정 전체를 거절한다. token이나 JWK의 임의 URL에서 공개키를 내려받지 않는다. 키가 하나라도 손상/중복/부적합하면 일부만 조용히 수락하지 않고 startup 실패한다. 공개키 배포 자료는 해당 발급자의 승인된 경로로 전달·검증해야 한다.

old/new 교체와120초 경계 제안은 유지한다. 실제 clock 오차·old 발급 replica 종료·receiver 전체 key 배포 확인 및 긴급 폐기 배포시간을 검증한다. 키가 유출됐을 때120초 대기를 강제하지 않는다. 신속 폐기 요구를 배포형 key set으로 만족하지 못하면 운영 승인 전에 JWKS 등 대안을 검토한다.

### 7.5 다음 공동 확인에 필요한 운영 사실

문서 왕복만으로 확인할 수 없는 것은 다음이다. 양 서버 담당자가 Secret/토큰/private key 없이 설정명과 대응만 확인해 주면 된다.

1. 실제 test/staging/production 대상, Billing 배포/DB 분리, 현재 WITHDRAWAL_ENVIRONMENT와 stream 귀속.
2. 양방향 정확한 HTTPS origin·connector port·target group, 공개 노출 허용 범위, ALB→task TLS/SG 구성.
3. 정상 rotation 및 긴급 key 폐기 담당자·가능한 배포시간.

그 뒤 이 절의 후보(분리 trust 환경, 양쪽 connector, 시간/키/401·403 경계, body 서명 없는 보안 모델)를 공동 확정하고 인증 전용 fixture/규칙 개정으로 진행한다. 이번 보완은 기존 SigV4-only 규칙의 실제 개정이나 OFF 구현 착수가 아니다.
