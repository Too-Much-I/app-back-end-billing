# PLAN-009 상세 선택지 — 코드 확인과 권장안

- 날짜/브랜치: 2026-10-07 / develop / Jira 없음. 분석·제안이며 승인/구현 아님.
- Identity는 읽기 전용 확인. 운영 배포/env·DB·실제 발급 token·법률 적합성을 확인한 것은 아니다.

## 1. 5줄 결론

1. 분산 fixed window60/10은 두 기반 API에 적합한 초기안이며 엄격한 rolling60초 보장은 아니다. [초안](PLAN-009-payment-foundation-technical-contract.md)
2. additive subset migration과 exact index 검증을 권장하며 전체 v5 완료로 표시하지 않는다.
3. 활성 회원의 stable reference 유지와 탈퇴 후 조건부 정리를 구분해야 한다. 미구매 기록에 금융5년을 일괄 적용하지 않는다.
4. Identity 로그인/refresh는 ACTIVE 검사, 탈퇴는 refresh 폐기를 수행하지만 Billing JWT 검증에는 현재 계정 상태 조회가 없다. [근거 §6]
5. ACTIVE MEMBER purchase scope 발급 추가와 기존 token의 상태 변경 반영 방식을 별도로 정해야 한다. 상태 조회 추가는 신규 서비스 계약이고 Store 결제 완전 차단 보장은 아니다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 실제 확인된 동작

- Identity JwtAccessTokenIssuer는 Billing audience와 billing:read를 합성하고 account_type을 기록한다. ACTIVE 상태 인자를 받지 않으며 caller가 선택한 scope를 합성하므로 발급기 자체가 purchase 권한의 상태별 배제를 보장하지 않는다. 정상 ACTIVE MEMBER 전용 billing:purchase 정책 구현은 확인되지 않았다.
- application.yml의 Access Token 기본 TTL은 PT30M. 운영 환경 override는 미확인이다.
- LoginService는 비ACTIVE 거절. TokenReissueService는 DB 현재 User를 확인하며 WITHDRAWN/비ACTIVE 거절. recovery 경로도 SessionSecurityService의 상태 검사 사용.
- UserWithdrawalTransactionService는 tombstone/CAS, phone binding revoke, 활성 refresh 폐기, UserWithdrawn outbox를 저장한다. 이것만으로 Billing의 과거 Access Token이 무효화되는 것은 아니다.
- UserStatus에 SUSPENDED가 있지만 조사한 소스에서 일반 정지 변경·Billing 상태 fan-out의 완성된 계약은 확인하지 못했다. phone eligibility revoke를 유료 계정 정지로 재해석하면 안 된다.
- Billing IdentityUserJwtDecoder는 signature·claim·만료를 검증한다. token blacklist/현재 Identity DB 상태를 이 경로에서 확인하지 않는다. 설정이 기본30분/허용skew60초라면 상태 변경 직전 token의 수용 잔여 시간은 약31분까지 가능하다. 운영 실제 보장 시간이 아니다.

## 3. 사용자가 결정해야 하는 사항

### 1. 요청 제한

| 선택 | 장점 | 단점 |
| --- | --- | --- |
| A 고정 UTC1분 products60/account10 — 권장 | Mongo 원자 카운터가 단순, 여러 task 합산 가능, 추가 인프라 없음 | 경계에 최대 두 구간 한도 집중 가능, 요청마다 DB 쓰기 |
| B rolling60초 또는 token bucket | 순간 집중을 더 세밀히 제어 | 저장/동시성·테스트 복잡, 두 방식의 burst 의미도 별도 정의 필요 |

009는 과금/지급 API가 아니고 account 중복은 unique로 별도 방어하므로 A 추천. 익명 공격 방어는 per-user limiter 이전 ingress/WAF 영역이며 이 제한으로 대체하지 않는다. 금융 API 한도로 자동 재사용하지 않는다.

### 2. 인덱스/스키마

| 선택 | 장점 | 단점 |
| --- | --- | --- |
| A 009 subset만 additive 적용 — 권장 | 무료/기존 schema 보호, 작고 명확한 검증/실패 범위 | 버전과 subset coverage를 별도 관리해야 함 |
| B 결제 전체 v5 미리 적용 | 전체 구조 준비 여부 관리가 단순해 보임 | 미확정 구매/환불 모델을 선반영, 변경 비용·영향 커짐 |

subset은 index 자동 임의 생성/drop 의미가 아니다. 승인 manifest·dry run·배포 후 exact 비교가 필요하며 실제 운영 migration은 별도 승인.

### 3. 미구매 보존과 암호화 — 두 결정을 분리

보존 A(권장): ACTIVE 회원은 stable account/reference 유지. 탈퇴 확인 즉시 신규 용도 비활성, 지연 거래 대사를 위한 제한적 보존 뒤 미구매 연결정보 삭제. 초기 검토값은 탈퇴 확정+30일이지만 법정 기간·provider 보장값이 아니며 실제 최대 결제 지연/대사 주기 검증과 개인정보 고지 승인 전 확정하지 않는다. 이미 검증된 거래/미해결 분쟁·결제는 해당 최소 증거의 별도 보존을 따른다. 실제 미구매 판단을 로컬 Purchase 부재만으로 하지 않는다. 오래된 확인 불가 건을 무기한 전체 보존하지 않도록 별도 사유·최소 증거·재검토 기한을 정한다.

- 장점: 같은 회원의 reference 안정성과 개인정보 최소화 균형.
- 단점: Billing이 탈퇴 사실을 확실히 알아야 하고 대사/삭제 worker·미해결 예외 절차가 필요. 현재 009 두 route만으로 완성되지 않음.
- 대안 B: 결제하지 않은 계정도 장기 일괄 보존. 복구는 쉬우나 불필요한 개인정보 연결 보존이 늘고 거래 없는 기록의 금융5년 근거를 자동 주장할 수 없어 비권장.
- 회원 상태에서 비활동만으로 reference를 주기 삭제·재발급하는 방식은 stable ID 계약과 충돌하므로 선택지에서 제외.

암호화 A(권장): 사용자 연결정보/reference 원문은 application-level 인증된 암호화와 별도 keyed lookup을 사용하고 DB 저장 암호화/TLS·접근 통제·로그 제외를 병행한다. 값 복원이 필요하므로 일방향 hash만으로 대체하지 않는다. key는 데이터와 분리·rotation/backfill/복구 검증 필요. 현재 referenceId=_id 초안에서 원문이 _id에 남으면 보호 효과가 제한되므로 opaque 내부 _id+lookup hash+encrypted reference 등 schema 수정안을 별도 동결해야 한다. userId equality index 역시 보호 범위와 대체 key를 함께 설계한다.

- 장점: DB 사본 노출 시 직접 사용자 연결을 줄일 수 있음.
- 단점: key 관리/암복호화·조회 index/회전 복잡도, key 장애 시 발급 실패. 모든 metadata 노출을 제거하는 것은 아님.
- 대안 B: DB 저장 암호화+TLS/권한만 사용하고 application은 평문 field 유지. 단순하고 빠르지만 DB 조회 권한/논리 export가 유출되면 그대로 노출. ADR의 lookup 암호화 의도보다 약해 별도 계약 변경 검토 필요.

### 4. 상태 변경 뒤 기존 token 처리

공통 선행: ACTIVE MEMBER에만 billing:purchase 부여, GUEST/WITHDRAWN/SUSPENDED 배제 및 explicit scope 경로도 검사. 기존 read/workload 계약은 보존.

| 선택 | 장점 | 단점/범위 |
| --- | --- | --- |
| A JWT 만료까지 수용 | 현재 Billing 방식과 가장 가까움, Identity 장애 의존 낮음 | 상태 변경 후 유효 token 잔여시간 동안 API 허용; 허용 지연을 제품 정책으로 승인해야 함 |
| B 구매 계정 API에서 Identity 현재 상태 동기 확인 — 보수적 권장 | 조회 시점 ACTIVE MEMBER 검증, 별도 token 만료 대기 축소 | 신규 내부 API·Lattice/IAM/timeout 필요, Identity 장애 시 새 구매 준비503; 009 초안의 동기 API 없음 방향 변경 필요 |
| C 계정 상태 event를 Billing에 복제 | 요청마다 Identity 호출 없이 로컬 차단, 탈퇴 보존 처리에도 활용 | event 지연·역순·revision·정지 해제·초기 coverage/대사 필요; 즉시 차단 보장 아님 |

B 추천은 구매 준비 API의 상태 확인 강화이지 실제 Store 결제 직전까지 원자적으로 ACTIVE를 보장하는 것은 아니다. 이미 reference를 받은 앱은 Store를 직접 호출할 수 있고 조회 직후 탈퇴도 가능하다. 앱은 구매 진입 때 재확인하되 UI 규칙만 보안 경계로 보지 않는다. 이미 결제된 거래의 sync/webhook/환불 복구를 상태 검사 실패로 버리지 말고 최소 영속 기록/검토로 수렴시킨다. 지급/환불 처리 정책을 임의 추가하지 않는다.

B 조회만으로 요청이 없는 탈퇴 사용자를 찾아 삭제할 수 없으므로 보존 A에는 별도 탈퇴 전달/대사도 필요하다. C 전체 구축 전 최소 탈퇴 전달 범위를 별도 설계할 수 있지만 기존 owner/phone event를 계정 lifecycle로 오용하지 않는다. 세 선택은 동일한 보장 수준이 아니다.

## 4. 주요 위험과 미확인 사항

- 30일 보존은 평가할 운영 후보이지 승인/법률 결론이 아니다. 무조건30일 삭제 또는 금융5년 연장은 제안하지 않는다.
- 30분TTL은 로컬 기본값, 60초skew는 허용 상한. 실제 token 수용 시간·배포 flags는 외부 검증 필요.
- ACTIVE 상태 조회 B와 암호화 A는 기존 기술 초안의 schema/API 범위를 바꾼다. 승인 후 ADR/Identity 인계 동기화가 필요하며 이번에 코드를 고치지 않는다.
- 기존 refresh 폐기/phone revoke는 이미 발급된 사용자 JWT·Store SDK 결제까지 취소하는 기능이 아니다.

## 5. 현재 작업과 직접 관련된 다음 단계

선택 승인 후 009 초안의 limiter/subset을 동결하고 encryption/보존 manifest 및 계정 상태 증거 계약을 별도 보완한다. Identity는 읽기 대상이며 수정·메시지 전송·운영 설정 변경하지 않았다. 이후 Jira/구현 승인과 actual staging 검증은 별도.

## 6. 부록 — 소스 근거

- [Identity 발급기](../../../identity/src/main/java/web/tosunsaeng/identity/global/security/jwt/JwtAccessTokenIssuer.java)
- [TTL 기본 설정](../../../identity/src/main/resources/application.yml)
- [로그인 상태 검사](../../../identity/src/main/java/web/tosunsaeng/identity/domain/auth/local/application/LoginService.java)
- [기존 재발급](../../../identity/src/main/java/web/tosunsaeng/identity/domain/auth/session/application/TokenReissueService.java), [재발급 복구](../../../identity/src/main/java/web/tosunsaeng/identity/domain/auth/session/application/ReissueRecoveryService.java), [상태 검사](../../../identity/src/main/java/web/tosunsaeng/identity/domain/auth/session/application/SessionSecurityService.java)
- [탈퇴 Transaction](../../../identity/src/main/java/web/tosunsaeng/identity/domain/user/application/UserWithdrawalTransactionService.java), [상태 enum](../../../identity/src/main/java/web/tosunsaeng/identity/domain/user/domain/enums/UserStatus.java)
- [Billing JWT 검증](../../src/main/java/web/tosunsaeng/billing/global/config/security/IdentityUserJwtDecoder.java)
- [기술 초안](PLAN-009-payment-foundation-technical-contract.md), [ADR](../adr/ADR-004-fixed-term-premium-payment-contract.md)

코드 읽기와 문서 diff/링크 검사만 수행. Gradle·실제 token/DB/Store/AWS 검증 미실행. 법적 필수 보존기간을 새로 판단하지 않았으며 기존 결제5년/무료Claim3년 정책을 변경하지 않는다.
