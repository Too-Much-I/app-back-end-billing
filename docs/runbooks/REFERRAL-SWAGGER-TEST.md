# 추천인 API 테스트 Swagger

- 주소: https://billing-test.to-teacher.com/swagger-ui/index.html
- 계약: `docs/contracts/REFERRAL-REWARD-PUBLIC-API-R1.md`. 추천 코드 발급·적용 두 POST만 공개한다.
- `BILLING_SWAGGER_ENABLED` 기본 OFF. develop 테스트 배포만 true를 주입하며 SANDBOX·공개 connector·GET을 모두 요구한다. 운영/DB/보상 flag는 변경하지 않는다.
- ALB: 테스트 host의 GET `/swagger-ui.html`, `/swagger-ui/*`, `/v3/api-docs`, `/v3/api-docs/swagger-config`만 기존 공개 target으로 연결. 내부 API와 다른 host/method 제외.
- Authorize에는 테스트 Identity MEMBER Access Token 사용. 기존 audience/scope 검증 유지, 토큰 영속 저장 비활성화.
- Try it out은 테스트 DB에 실제 코드·보상을 생성한다. 양쪽 purchase-account 준비 필요. 이번 배포 점검에서는 사용자 보상 요청을 실행하지 않는다.
- 롤백: 테스트 Swagger flag false, 문서용 ALB 규칙 제거. 다음 배포에서 재활성화되지 않도록 deploy.yml의 test 전용 주입 단계도 제거한다.
- 계약 변경 시 ReferralOpenApiConfiguration 및 문서 테스트를 함께 갱신한다.

## 검증

로컬 HTTP 문서/UI·정확한 두 경로/schema·JWT 유지·OFF/운영/port/method 차단 회귀 테스트. 배포 후 CI 성공·ECS 안정화·문서 200·추천 무인증 401·운영 문서 404 확인. 인증 사용자 보상 E2E와는 구분한다.

## 2026-10-29 적용 결과

- commit a786f95 / PR52 / develop 376d8d9, Actions 40822519020 SUCCESS. test30 → test31, 배포 이미지 일치·모든 target healthy·ECS COMPLETED 검증 통과.
- 승인된 ALB84(UI)·85(OpenAPI) GET 규칙 추가, 기존 테스트 8083 target 사용. 다른 규칙/운영 무변경.
- HTTPS UI/JS/CSS/config/OpenAPI200, 정확한 두 POST 노출, 문서POST404·추천무인증401·초대404·entitlements401·prod docs404.
- 로컬 전체 2033개 테스트 실패0/오류0/skip0. 보상 지급 E2E는 실행하지 않았다.
