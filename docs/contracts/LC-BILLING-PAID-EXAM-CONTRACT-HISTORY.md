# LC–Billing 유료 시험 계약 변경 이력

현재 공동 검토 기준은 [통합 R2](LC-BILLING-PAID-EXAM-CONTRACT-R2.md)다. 이 파일과 과거 snapshot은 현재 구현 규칙을 추가하는 문서가 아니다.

| 단계 | 기록 |
| --- | --- |
| R1 기술 draft-1 | 두 문제 후보, 전환 type, version 기반 revoke 및 오류 후보 제시 |
| draft-2 | replay 우선순위, 삭제 continuation, 기존409/503 유지, canonical bytes·역순 예외 명확화 |
| [draft-3 snapshot](LC-BILLING-PAID-EXAM-WIRE-R1-DRAFT-3.md) | status body 조회·command header 범위 및 오류 표현 정리. 전달 bytes는 그대로 보존 |
| [단순화 인계](LC-BILLING-PAID-EXAM-SIMPLIFICATION-HANDOFF-2026-10-14.md) | 모든 권리 전환의 공통 LC 마무리, 단일 문제, 불변 group revoke, terminalAt 재사용 승인 |
| 2026-10-14 문제 선정 승인 | 전체 답안 접수·요약 대기에는 직전 mock 제외, 실제 다른 적격 후보0일 때만 동일 허용 |
| 통합 R2 draft-1 | LC 최소wire 검토의 세 startReason·공통transition·단일canonical event/group충돌 반영, 구version/type/두후보/rejectedAt 제거. phone/삭제/보존 공동확정 대기는 유지 |

LC에서 종전 보완 방향에 이견이 없다고 한 것은 새 통합 R2 exact wire·fixture/hash·양측실행·구현/배포 완료의 증거가 아니다. 현재 문서만 통합했으며 기존 A 인증/fixtures-1을 변경하지 않았다.

- 2026-10-14 통합 R2 draft-2: LC의 공동회신1~3 이견없음 접수. inboxTTL후 최초group증거 재전송, 이전늦은완료/신규guard격리, 새권리부족시이전시험보존을 명시. 정책/필드 추가 없음, S15/V07 잔여 및 담당 분리 유지.
