# TMI-205 자동삭제 전 Identity 확인

- 날짜: 2026-10-17. Billing의 읽기 전용 확인이며 Identity 변경·활성화 승인이 아니다.
- Billing의 관측 기반 삭제 정책은 `TMI-205-OBSERVED-ABSENCE-R1.md`를 따른다.

## 확인 결과

Identity 로컬 코드의 구매 발급은 ACTIVE MEMBER 확인, Mongo Transaction 내 version이 있는 UserSessionControl 갱신을 사용한다. 탈퇴 Transaction도 같은 control을 WITHDRAWN으로 변경하며 refresh session을 폐기한다. 재발급은 WITHDRAWN 사용자/탈퇴 폐기 세션을 거절한다. User 생성 경로는 새 UUID를 생성한다. 이는 정적 확인이며 실제 배포 경합 테스트 결과가 아니다.

AWS 테스트 서비스 `tosunsaeng-identity-test-service`의 task definition 45, running 1에서 다음 설정을 읽었다. Secret 값은 읽지 않았다.

| 설정 | task definition 값 |
| --- | --- |
| JWT_ACCESS_TOKEN_TTL | PT30M |
| AUTH_SESSION_FENCE_ENABLED | true |
| AUTH_PURCHASE_CONTROL_WRITES_ENABLED | 미설정 |
| AUTH_PURCHASE_FENCE_ENABLED | 미설정 |
| AUTH_PURCHASE_SCOPE_ENABLED | 미설정 |
| BILLING_WITHDRAWAL_PUBLISHER_ENABLED | 미설정 |

위 항목을 주입하는 Secret 참조도 없었다. 로컬 application.yml의 구매 세 flag 기본값은 false다. 배포 이미지/다른 설정원까지 일치한다고 단정하지 않으며, 미설정을 실제 기능 활성화의 증거로 취급하지 않는다.

## Identity 담당 확인 요청

1. 배포 이미지의 구매 scope/fence/control writer 적용 여부와 실제 유효 설정을 확인한다. flag만 켜지 말고 기존 control migration·source stream·readiness 선행 조건을 따른다.
2. 정상 MEMBER 로그인·재발급에 billing:purchase가 있고 최대 30분인지, 탈퇴가 먼저 확정되면 발급/재발급이 거절되는지 확인한다. 토큰 원문은 전달하지 않는다.
3. 재가입은 새 userId이며 탈퇴 userId를 재활성화하지 않는 경계를 유지한다.
4. 별도로 준비 중인 탈퇴 발신/수신·전용 키 연동을 완료한다. 이번 점검이 그 연동 완료를 뜻하지 않는다.

Billing 자동삭제는 아직 미배포/OFF다. 위 실환경 확인과 Billing 새 이미지·schema/worker/중단 절차 확인 후 활성화한다. 이번 문서는 외부 전송하지 않았다.
