# Billing → Identity: 운영 전용 workload 키 준비 요청

## 1. 5줄 결론

1. Billing prod 서버·41collection 스키마는 준비됐지만 업무 기능은 OFF, 복구 control은 ALL_BLOCKED다.
2. 기존 운영 준비 기록 기준, Identity→Billing 이벤트 검증용 public JWK Secret은 값이 없어 Identity 공개키 전달이 필요하다. 반대 방향인 Billing→Identity 복구 발신키는 별도 운영 Secret에 생성·검증 완료됐다. 키 준비는 발신 활성화나 Identity 공개키 등록 완료를 뜻하지 않는다.
3. Identity는 기존 사용자 JWT/LC용 키와 구분되는 Billing 전용 운영 서명키를 준비하고 공개 JWK set만 전달해 달라.
4. 기존 workload-auth-r1의 production issuer/sub/audience·시간·오류 규격은 변경하지 않는다.
5. 이 문서는 준비 요청이며 Identity 설정/코드 변경·발신 ON·공동 E2E 완료나 외부 메시지 전송이 아니다.

## 2. 반드시 확인할 계약

- 정본: [WORKLOAD-JWT-AUTH-R1](WORKLOAD-JWT-AUTH-R1.md).
- env=production, iss=`urn:tosunsaeng:workload:identity:production`, sub=`identity-service`.
- 대상4POST: withdrawal events, trial eligibility events, trial owner events, user merge events. audience는 정본의 각 목적값에 `:production`을 사용한다.
- 공개 JWK set: `keys` 배열, kty=RSA, alg=RS256, use=sig, 유일 kid, n/e, `key_ops:["verify"]`. RSA≥2048(이번 운영 키 준비는3072 권장). private 필드/사용자 토큰 키/테스트 키는 전달하지 않는다.
- 비밀키는 Identity 소유 Secret에만 둔다. 새 키 생성·입력은 사용자 통제하에 수행하고 기존 운영키를 무조건 교체하지 않는다.
- Billing 수신 저장 위치: `tosunsaeng/prod/billing/identity-workload-public-jwks`, JSON field `BILLING_WORKLOAD_IDENTITY_PUBLIC_JWK_SET`에 public set JSON 문자열을 저장할 예정.

## 3. 회신 요청

1. 운영 전용 서명키의 Secret 이름과 공개 JWK 파일/Secret 위치, kid·공개키 fingerprint. 비밀키 원문 금지.
2. 네 이벤트 중 실제 사용할 발신과 flag 상태, production consumerScopeId 및 Identity 원천 환경/cutover 준비 상태.
3. Billing→Identity 복구를 받을 운영 HTTPS origin·전용 port/public port 차단 준비와 Billing 공개키 등록 여부.
4. 기존 사용자 JWT issuer/audience는 별도 계약대로 유지. 현재 운영 issuer의 staging 표기를 이름만 보고 변경하지 않는다.

## 4. 위험·미확인

- 예정 Billing workload host는 `billing-workload-prod.to-teacher.com`이나 아직 DNS/ALB 연결 완료가 아니다. 준비 회신만으로 발신을 켜지 않는다.
- Billing→Identity 복구 발신키는 별도 운영 Secret `tosunsaeng/prod/billing/recovery-workload-jwt`에 준비돼 있다. 2026-10-17 키 저장·검증 기록 기준이며, Identity→Billing 이벤트 발신키와는 다른 방향·용도다. Identity 측 Billing 공개키 등록과 실제 복구 연동은 별도 확인 대상이며 필요한 경우 public set만 전달한다.
- 실제 탈퇴/복구·401/403·exact204·중복·응답유실 검증과 보존/복원 운영 준비는 별도다.

## 5. 변경·검증 범위

Billing 문서만 작성했다. AWS Secret 이름/버전 및 기존 task definition의 참조를 읽었으며 Identity 코드·Secret·서비스를 변경하지 않았다. 테스트 실행 없는 운영 준비 인계다.

## 6. 2026-10-17 문구 정정

결론 2의 과거 Billing 발신키 미발견 문구를 이후 키 생성·검증 완료 기록에 맞췄다. 근거는 [CURRENT_STATE](../codex/CURRENT_STATE.md)의 같은 날짜 prod 키 저장·검증 완료 기록이다. 이번 정정에서는 AWS를 재조회하지 않았으며 키 생성/교체·설정 변경·발신 활성화·외부 전송을 수행하지 않았다. Identity 전용 운영 서명키 준비 및 공개키 전달 요청 범위는 유지한다.

## 7. 운영 앱 결제 활성화 사전 확인 — 2026-10-17

- 이후 읽기 점검에서 Billing identity-workload-public-jwks에 AWSCURRENT 존재 및 prod:3 참조 확인. 위 값 없음 설명은 과거 상태다. 이번에는 실제 공개 JWK 정합성을 재검증하지 않았다.
- Identity 운영 서비스는 prod:13. AUTH_PURCHASE_CONTROL_WRITES_ENABLED, AUTH_PURCHASE_FENCE_ENABLED, AUTH_PURCHASE_SCOPE_ENABLED가 모두 환경변수에 없으며 관련 Secret 이름도 없다. environmentFiles/command override/SPRING_APPLICATION_JSON 없음. 확인한 Identity application.yml의 기본값은 모두 false다.
- JWT_ISSUER는 기존 https://identity-staging.to-teacher.com이며 이름 때문에 변경하지 않는다. https://identity-prod.to-teacher.com/.well-known/jwks.json은 TLS 검증 포함200, public key1개/비밀필드 없음 확인. JWT_AUDIENCE 설정만으로 최종 발급 audience를 단정하지 않는다.
- Billing 앱 주소의 일반 DNS·HTTPS 연결은 완료했지만 업무OFF다. 운영 recovery control은 PRODUCTION/billing ALL_BLOCKED/schemaVersion1/version1, 상품10개 및 별도 RC budget collection 존재 확인. 이번에 control을 열지 않았다.
- Identity 담당에게 요청: 기존 운영 사용자의 로그인/무료시험 경로를 보존하면서 구매 control·fence·scope 준비/기동 검증과 ACTIVE MEMBER의 billing:purchase 발급 준비를 확인해 달라. 필요한 control 데이터·readiness를 먼저 검증하고 단순 true 설정만으로 통과시키지 않는다. 탈퇴 capture/발신 상태도 함께 회신해야 한다(현재 BILLING_WITHDRAWAL_PUBLISHER_ENABLED=false 관측).
- Billing은 사용자 인증을 완화하거나 구매 scope 검증을 제거하지 않는다. Identity 설정/DB/배포는 이번 범위 제외이므로 변경하지 않았으며, 이 문서는 외부 발송이나 상대 서버 변경 승인이 아니다. Billing의 나머지 RC/JWT/schema/fence 연결 및 실구매 E2E도 아직 완료되지 않았다.
