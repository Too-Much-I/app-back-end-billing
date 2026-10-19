# Identity 공유 workload-jwt-r1

2026-10-13 Identity 로컬 저장소의 payment-lifecycle/workload-jwt-r1/time-cases.json을 바이트 동일하게 동기화했다. 16개이며 기존 Billing workload-auth-r1의 13개와 별개로 유지한다.

IdentitySharedJwtFixtureTest는 기대값/시계만 파싱하고 iat/exp/nbf 값은 원문 token을 잘라 JWT payload에 삽입한다. float/string/null/int64 초과를 정규화하지 않는다. 임시 RSA 키로 서명한 뒤 실제 WorkloadJwtCodec으로 독립 검증한다.

이 테스트는 운영 키/ALB/TLS 검증이나 상대의 공동 수락을 뜻하지 않는다. 기존 recovery-r1/scalar-end-1 body/hash는 변경하지 않는다.
