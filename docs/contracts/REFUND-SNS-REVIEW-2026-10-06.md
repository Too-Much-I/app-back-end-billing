# 환불 상담 및 SNS 변경 영향 검토

## 1. 5줄 결론

1. 문의를 받아 수동으로 환불을 검토하는 운영은 가능하나 이번 검토는 정책 승인이나 구현이 아니다.
2. 상담 접수, 사용 사실 확인, Store 환불 실행, Billing 원장 반영은 별개 단계다.
3. Identity 단일 SNS와 계정 찾기는 Billing 구매 소유자를 바꾸는 기능이 아니다.
4. Identity 문의 기능을 활용할 수 있지만 구매 검증/환불 실행과 결제 권한 발급은 아직 별도 작업이다.
5. 로컬 소스 기준이며 실제 배포·Store 기능·법적 공제 공식은 이번에 검증하지 않았다.

## 2. 반드시 읽을 내용

권장 운영: 앱 문의 또는 지원 이메일로 접수 → 인증된 구매 소유자/Store 거래 확인 → 해당 구매에 연결된 이용기간·완료/실패·장애 증거 확인 → Store별 절차로 환불 처리 또는 안내 → 검증된 최종 provider 상태로 Billing reversal. 이메일·전화·문의 내용만으로 구매 소유자를 확정하지 않는다. 문의 접수는 환불 승인이나 이용권 취소가 아니다.

Apple 환불을 개발자가 항상 직접 승인·집행할 수 있다고 약속하면 안 된다. Store 직접 환불 요청도 존재한다. Google과 Apple의 실행 가능 범위는 각각 확인해야 한다. 문의 창구는 법정 반환 의무나 잔여기간/사용분 기준을 대신하지 않는다. 기간형 무제한 상품에 임의의 시험당 가격을 곱해 공제하는 정책은 승인되지 않았다.

## 3. 결정할 사항

- 기존 Store 기본 창구 + 이메일 보조를 유지할지, 앱 문의를 주 상담 창구로 추가할지 승인 필요. Store 직접 요청은 계속 허용.
- 담당자·응답 목표·본인 확인·승인/거절 사유·처리 감사 절차.
- 잔여기간 반환과 공제 기준은 적용 법규 및 Store 지원 확인 후 확정. 부분 환불을 수동 운영한다 해도 결제 원장 계약 보완이 필요하다.

## 4. 위험과 미확인

- 문의 API/Slack 기본 OFF, 실제 프론트·Slack·ECS 활성화 미확인. 문의 본문으로 receipt/token/결제수단 정보를 수집하지 않는다.
- 문의 기록 90일은 결제 원장 5년을 대체하지 않으며 Slack 보관도 별도다.
- 학습 기록 삭제로 원본 결과가 사라져도 최소 결제 사용 증거가 필요하다. 원본 답안/피드백을 무조건 보존하는 근거로 사용하지 않는다. 최소 필드·보존근거·기간은 후속 계약 대상.
- JWT는 Billing audience/read를 발급하지만 purchase를 ACTIVE MEMBER 전용으로 부여하는 구현은 확인되지 않았다. 만료 전 Access JWT 즉시 폐기 한계도 별도 보안 검토 대상이다.

## 5. SNS 변경의 직접 영향

Identity는 회원당 승인 SNS 하나를 강제하며 Firebase UID가 같아도 다른 SNS를 자동 승인하지 않는다. 계정 찾기는 전화 인증 뒤 SNS 힌트를 안내할 뿐 토큰 발급·회원 병합·권리 이전을 하지 않는다. 기존 회원 정상 로그인은 같은 JWT sub를 사용하므로 Billing purchaseAccountRefId도 유지하는 설계가 맞다. Guest 신규 승격도 기존 userId를 유지한다. Guest가 기존 회원에 병합되는 경로는 별도 기존 계약이며 Guest 유료 구매는 여전히 범위 밖이다.

탈퇴 후 새 userId를 받은 경우는 계정 찾기와 다르다. 같은 전화/SNS/Store 계정이라는 이유로 paid 권리를 자동 이전하지 않는다. 무료 PHONE_REJOIN과 유료 복구를 혼용하지 않는다. 앱 RevenueCat ID는 인증된 최종 Billing 구매 참조값을 따라야 하며 Firebase 전화 조회나 SNS 힌트로 변경하면 안 된다.

Learning Core 최신 커밋 제목에 SNS가 있지만 실제 변경에는 학습 기록 삭제 기동 검증이 포함된다. 제목만으로 인증 변경을 추론하지 않는다. 삭제 런북은 Billing continuation OPEN/미해결 증거를 자동 purge하지 않으며 terminal retention/E2E를 남은 gate로 명시한다. 환불 이용량을 현재 화면의 시험 목록 개수만으로 계산하면 삭제·무료 시험·실패 시험을 오판할 수 있다.

## 6. 부록 — 확인 근거

- Identity 로컬 HEAD fb04fbe6(TMI-197), SNS TMI-192. `domain/auth/federation/application/SingleSocialIdentityPolicy.java`: 승인 provider/subject exact 검사. `domain/auth/accountrecovery/RecoveryAccountResolver.java`: 활성 번호 owner 조회, 탈퇴 NOT_FOUND, 불명/정지 fail-closed.
- Identity `domain/auth/federation/application/FirebaseGuestUpgradeTransactionService.java`: 기존 userId 유지. `global/security/jwt/JwtAccessTokenIssuer.java`: sub=userId, Billing audience/read 추가, purchase 전용 정책 없음.
- Identity `docs/contracts/support-inquiry-api.md`, `domain/support/SupportRequest.java`: POST /api/v1/support/inquiries, AUTH/GENERAL, 기본 OFF, 문의/Slack만. 환불 전용 category/거래 연결/관리자 환불 기능은 없다.
- LC 로컬 HEAD 377b043, `docs/codex/LEARNING_RECORD_DELETION_RUNBOOK.md`: continuation 증거 및 E2E gate. 진행 중인 challenge 변경은 보존하고 수정하지 않았다.
- Billing [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md), 기존 refund 연구와 C9-S10/S11 승인 기록: Store 기본 처리, provider-confirmed reversal, 부분 사용 반환 미확정.
- 정적 코드·문서 검토만 수행. 원격 최신/배포/실제 데이터/법률/Store 현재 지원 확인이나 테스트 실행 결과가 아니다.
