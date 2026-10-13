# LC–Billing workload JWT — A 최종 수락의 Billing 정본 반영

- 확정일/반영일: 2026-10-13
- 계약 revision: `lc-billing-workload-auth-r1`
- fixture revision: `lc-billing-workload-auth-r1-fixtures-1`
- 근거: 사용자 전달 LC 최종 수락 기록. LC 기록의 승인 근거는 사용자 요청 「확정해줘」다.
- 관련 LC Jira: TMI-116 / TMI-128 / TMI-122
- 상태: LC가 Billing 제안의 A 인증 규칙·fixtures-1 hash·발신 설정 명칭을 최종 수락했으며 Billing 정본에 반영했다. 상세 startup 조합표·B/C 구현·배포·활성화는 미승인/미완료다.

## 1. 동결 범위

[인증 명세](LC-BILLING-WORKLOAD-AUTH-R1.md)의 §2 인증 규칙과 §3 테스트 기대 결과를 A 정본으로 사용한다. RS256·환경별 issuer/키·두 audience·TTL60/skew30·배포 공개키·exact Bearer·추가 payload claim 무시·query 거절·교차 verifier·오류 우선순위·TLS 미기동 구분을 포함한다. 요약으로 원문의 규칙을 대체하지 않는다.

[발신 설정 매핑](LC-BILLING-WORKLOAD-SENDER-MAPPING-R1.md)의 신규 세 명칭과 공통 준비 gate 의미를 확정한다.

| property | env |
| --- | --- |
| app.billing.workload-jwt.sender-enabled | BILLING_WORKLOAD_JWT_SENDER_ENABLED |
| app.billing.workload-jwt.environment | BILLING_WORKLOAD_JWT_ENVIRONMENT |
| app.billing.workload-jwt.private-jwk | BILLING_WORKLOAD_JWT_PRIVATE_JWK |

기본 OFF·키 기본 빈 값, ON 검증/OFF 미파싱, 기존 BILLING_BASE_URL/업무 flags/timeout 유지와 PT2S/PT5S 기본값을 따른다. 운영 issuer/sub/audience/TTL/skew override나 SigV4/무인증 fallback은 추가하지 않는다. S3 공용 AWS credential provider는 보존한다.

업무 발신 ON·JWT 준비 OFF의 기동 거절 원칙은 수락됐다. 어떤 flag가 발신/필수 bean을 구성하는지의 상세 조합표와 실제 wiring 검증은 B 준비에서 확정한다. phone→saga·reconciliation→saga를 유지하며 writer OFF/publisher ON은 drain/Transaction 의존성 확인 없이 새 제약으로 금지하지 않는다.

## 2. fixtures-1 동결

| 파일 | SHA-256 |
| --- | --- |
| public-jwks.json | db1fcf9eaeec3591a8c422ead344af61cbf25d6808ab377d61e935517f2ae0b2 |
| token-cases.json | e783060182033842a40e698b0ed31ee30db8691e71438bc6f707f0becd8c5641 |
| http-cases.json | 3c3ca0d33f247c9915b0e59a875858d46d583fb5e8fe79f88b9903a9529674a1 |

이번 반영으로 bytes를 변경하거나 재생성하지 않았다. 변경 시 새 revision/hash와 양측 수락이 필요하다.양측 token72/hash1 독립 검증과 Billing 전체1004·LC 전체713 결과는 [접수 회신](LC-BILLING-WORKLOAD-FIXTURES-1-BILLING-RECEIPT-2026-10-13.md)을 따른다. 서로 다른 저장소 결과를 합산하지 않는다. HTTP27은 기대 결과를 수락한 것이며 실행 통과가 아니다.

## 3. 다음 단계와 승인 경계

- B 준비: 상세 startup 조합표·sender 키 점검·S3 provider 영향·양측 구현 계획과 ADR/AGENTS 변경안을 작성하고 수락한다.
- B 착수 전 승인 범위에 맞춰 ADR/AGENTS를 동기화한다. 현재 LC Lattice runtime 계약은 이번 정본 상태 반영만으로 변경하지 않는다.
- B 구현은 별도 승인 후 기본 OFF로 진행한다. HTTP27·키 startup·fresh token·비로깅·업무 retry를 실제 sender/chain으로 검증한다.
- C 승인 재처리 도구·복구 E2E, 실제 TLS/ALB·origin/port/키 담당·회수 SLA·미완료 inventory·Mongo·배포/활성화는 별도 승인·검증이다.
- Billing 수신 준비 후 LC 발신을 활성화한다. 기존 원본 증거·status-first·terminal 불변·업무 body/DTO/멱등성/원장과 Identity/user 계약은 유지한다.

이번 작업은 Billing 문서·기록 반영과 기존 hash 재확인이다. LC 파일·런타임 코드·설정·Jira·외부 회신 전송·배포를 변경하지 않았다. 작업 중 별도 TMI-205 코드/문서 변경을 보존했다.
