# LC fixtures-1 독립 검증 접수·Billing 회신

- 날짜: 2026-10-13 / 관련 LC Jira: TMI-116·TMI-128·TMI-122
- revision: `lc-billing-workload-auth-r1-fixtures-1`
- 상태: 양측 A token/byte hash 독립 검증 완료. 최종 계약·설정 명칭 수락, HTTP·B/C 구현·배포 완료는 아니다.

## 1. 접수 결과와 독립 확인

LC 회신의 원문 token 72개와 hash 검사 1개 성공을 접수했다. Billing은 LC fixture 네 파일(세 JSON·SHA256SUMS)을 읽기 대조해 Billing 제작본과 동일 bytes임을 확인했다. LC의 현재 JUnit XML도 읽기 확인했다.

| 저장소/범위 | 결과 | 근거 |
| --- | --- | --- |
| Billing token/hash | 73개 통과 | 기존 WorkloadJwtCodec로 raw token 검증, 앞선 실행 기록 |
| Billing 전체 clean test | 1,004개, 실패/오류/skip 0 | 앞선 Billing 실행 결과. LC 결과와 합산하지 않음 |
| LC token/hash | 73개, 실패/오류/skip 0 | LC `LcBillingSharedFixtureTest` XML 73개 확인 |
| LC 전체 clean test | 713개, 실패/오류/skip 0 | LC 회신 및 현재 test XML 합계 확인. Billing에서 LC Gradle을 재실행하지 않음 |
| 양측 fixture bytes | 모두 동일 | 세 JSON와 SHA256SUMS 직접 byte 비교 |

LC는 Billing codec·test 소스를 복사하지 않고 JCA `SHA256withRSA`와 Jackson strict JSON으로 test-only oracle을 구현했다. Billing이 해당 source를 읽기 확인했다. raw 서명 입력과 nanosecond clock을 유지하며 기대 결과는 결과 비교에만 사용한다. 이를 런타임 verifier나 HTTP 인가 구현으로 해석하지 않는다.

| 파일 | 양측 동일 SHA-256 |
| --- | --- |
| public-jwks.json | `db1fcf9eaeec3591a8c422ead344af61cbf25d6808ab377d61e935517f2ae0b2` |
| token-cases.json | `e783060182033842a40e698b0ed31ee30db8691e71438bc6f707f0becd8c5641` |
| http-cases.json | `3c3ca0d33f247c9915b0e59a875858d46d583fb5e8fe79f88b9903a9529674a1` |

LC 검증 보고·인수 기록은 LC 저장소 `docs/codex/LC_BILLING_FIXTURES_1_VALIDATION.md`와 `LC_BILLING_FIXTURES_1_RECEIPT.md`다. 원본 artifact는 [fixture README](../../src/test/resources/contracts/lc-billing-workload-auth-r1/README.md)를 따른다. 이번 접수로 fixture를 수정하거나 재생성하지 않았다.

## 2. 설정 매핑 검토 접수

LC가 신규 세 명칭을 충돌 없이 사용 가능한 구현 후보로 검토했음을 기록한다. [매핑안](LC-BILLING-WORKLOAD-SENDER-MAPPING-R1.md)의 이름·기본 OFF·기존 URL/flag/timeout 유지에 기술적 추가 이견은 없다. 이는 최종 명칭 수락이나 실제 환경변수 적용을 뜻하지 않는다.

- `app.billing.workload-jwt.sender-enabled` / `BILLING_WORKLOAD_JWT_SENDER_ENABLED`
- `app.billing.workload-jwt.environment` / `BILLING_WORKLOAD_JWT_ENVIRONMENT`
- `app.billing.workload-jwt.private-jwk` / `BILLING_WORKLOAD_JWT_PRIVATE_JWK`

B 준비에서는 saga·phone·reconciliation·publisher의 정확한 기동 조합과 서명키/public key 일치·OFF 미파싱을 검증해야 한다. LC의 S3용 공용 AWS provider는 보존하며 두 Billing signer의 의존성과 구분한다.

## 3. 다음 공동 수락 대상

1. A 인증 후보·fixtures-1 hash와 신규 설정 명칭의 최종 수락 기록.
2. 매핑안의 startup flag 조합표 수락과 B 변경 범위 승인. 기존 phone→saga 및 reconciliation→saga 의존성은 유지한다. writer OFF/publisher ON은 기존 outbox drain 가능성과 실제 Transaction bean 의존성을 확인하며 임의로 금지하지 않는다.
3. B 구현 착수 전에 ADR/AGENTS·양측 계획을 승인 범위에 맞춰 동기화한다. 현재 LC Lattice runtime 계약을 문서 검토만으로 교체하지 않는다.
4. 별도 승인 후 Billing 수신/LC sender를 기본 OFF로 구현하고 HTTP27·키 startup·fresh token·비로깅·업무 retry 회귀를 실제 구현으로 검증한다. C 도구·TLS/E2E·배포/활성화는 별도다.

HTTP27·키 기동 부정 fixture 전체·status-first·승인 requeue·실제 TLS/ALB·Mongo integration은 이번 공동 token 검증의 통과 범위에 포함하지 않는다. 이번 Billing 작업은 접수·정적 대조·문서 보완뿐이며 테스트나 다른 저장소 파일을 변경하지 않았다.
