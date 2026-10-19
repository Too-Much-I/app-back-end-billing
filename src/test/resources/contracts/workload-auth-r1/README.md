# workload-auth-r1 시간 경계 fixture

2026-10-12 Billing 실행 가능한 초안. Identity 공동 수락/실제 연동 검증은 아직 아니다.
기존 payment-lifecycle/recovery-r1 및 TMI-199 fixture/hash를 변경하지 않는다.

time-cases.json은 기준시각 2026-10-12T00:00:00Z에 더할 정수 초 offset과 기대 인증 결과다.
nbfOffset=null이면 claim 자체를 생략한다. 동일한 trusted issuer/key, 올바른 sub/aud/jti를 사용한다.
WorkloadJwtCodecTest.sharedTimeFixture가 매 실행 생성한 RSA 테스트 키로 전체 case를 검증한다.
실제 키나 토큰은 fixture에 포함하지 않는다. private key 공유/배포 자료로 사용하지 않는다.

시간외 잘못된 JSON숫자·overflow·duplicateclaim/header·키/서명·포트/권한/실Controller·발신회귀는 Billing Java 테스트에 있다.
공동수락 전에 Identity에서 동일 fixture를 독립 실행하고 인증/인가 HTTP fixture 범위를 함께 확인해야 한다.
