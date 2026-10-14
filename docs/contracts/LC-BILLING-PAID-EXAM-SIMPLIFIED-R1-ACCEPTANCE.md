# LC–Billing 단순화 exact R1 — 범위 한정 수락 기록

- 날짜: 2026-10-14
- 근거: 사용자 「어 해줘」(수락 기록·새 fixture 준비), 사용자 전달 LC draft-3 추가 이견 없음 회신.
- 상태: **Billing의 검토된 범위 수락 및 fixture 제작 완료.** LC 규격 검토 이견 없음과 신규 fixture 인수/독립 검증 완료를 사용자 전달 회신으로 기록한다. 전체 계약·runtime·배포 완료는 아니다.
- 관련: PLAN-011/012/013, TMI-116/TMI-128/TMI-122/TMI-125, LC 삭제 TMI-193/TMI-136.

## 1. 5줄 결론

1. [exact draft-3](LC-BILLING-PAID-EXAM-SIMPLIFIED-EXACT-R1.md)의 아래 검토 범위를 Billing 구현 기준으로 수락한다.
2. 원문 snapshot과 새 fixture revision의 bytes/hash를 고정하며 원문 제작 당시 미완료 문구는 이 기록으로 상태를 구분한다.
3. MEMBER 대기·격리 예외, 공개 오류,120일 경계,phone INITIAL/복원 규칙을 포함한다.
4. 별도 삭제 route/인증·과거 owner 삭제·자료별 최종 보존/폐기와 실환경 gate는 제외한다.
5. LC의 동일 bytes/hash 인수·자체 독립 검증 완료를 접수했다. 실제 상태28시나리오와 제품 구현·배포는 별도다.

## 2. 수락 범위

- 오류·envelope: 내부 MEMBER pending/owner-blocked/J3 terminal 분류, 공개 기존503·삭제 미접수409·삭제 replay404/result 생략.
- MEMBER 대기: Billing 비종료 receipt/status, LC PREPARED waiting, HTTP same-key 재개/worker reserve 금지, cleanup/격리 안전 조건. NEEDS_REVIEW 예외는 MEMBER 대기→소진 영속 원인과 현재 안전 조건을 모두 요구.
- 시간: 최초 접수+D(120일), terminal+D 상세 보존, 정각 경계, 과거 key 재입력의 보장 제외, 미완료/금융/소비/복원 근거 별도 보존.
- phone: target paid-first INITIAL/기존 무료 REPLACEMENT, immutable echo/group/source 검증, 원 consumption당 target 복원1회 및 경합 불변식.
- 생성 중 삭제 미접수 정책·공유 fence 요구와 E/T/P/C 검증 명세. 실제 shared fence 구현은 아직 검증하지 않았다.

## 3. 고정 원본과 새 fixture

| 항목 | 값 |
| --- | --- |
| 계약 revision | lc-billing-paid-exam-simplified-exact-r1-draft-3 |
| 계약 SHA-256 | 79bcfe3d79225c21d712791266bea10d4aed9eed3dc0529ddf86f7b29673a482 |
| fixture revision | lc-billing-paid-exam-simplified-exact-r1-fixtures-1 |
| SHA256SUMS 자체 SHA-256 | 35f7d437fb562e1427efe385ffaf1721a60f4780fcaacf495c2a0c7e787c2436 |
| 구성 | checksum 포함10파일, SHA256SUMS 대상9파일 |

[fixture README](../../src/test/resources/contracts/lc-billing-paid-exam-simplified-exact-r1-fixtures-1/README.md)와 [검증 보고서](LC-BILLING-PAID-EXAM-SIMPLIFIED-FIXTURES-1-VALIDATION.md)를 함께 전달한다. 포함 contract-snapshot.md는 현재 exact 원문과 byte 동일하다. 이후 계약 수정은 새 revision과 영향 검증으로 수행한다. 이 수락 기록을 원문에 덧붙여 snapshot hash를 바꾸지 않는다.

## 4. 제외·한계

신규 삭제 route/보안 allowlist·과거 owner fence coverage·자료별 최종 purge·운영 키/port/TLS·미해결 보존 예외는 별도다. 새 fixture의 phone/재개 검사는 선택 필드/논리 판정 모형이며 실제 HTTP DTO codec/worker/lease/Mongo 동작의 증명이 아니다. C 및 상태 시나리오28개는 미실행이다. 운영 보존5년/3년/35일 전체의 법적/실환경 적합성을 이번에 검증하지 않았다.

## 5. 양측 증거 상태

| 주체/범위 | 근거 | 상태 |
| --- | --- | --- |
| LC 규격 검토 | 사용자 전달 draft-3 최종 보완 이견 없음 | 접수 완료·이번 턴 자체 재검토 아님 |
| Billing 한정 규격 수락/오프라인 검사 | 사용자 요청·본 기록·검증 보고서 | 완료 |
| LC 새 fixture 인수·독립 검증 | 사용자 전달 검증·인수 기록:10파일 동일·81개 통과, 전체1,029개(81 포함) 실패/오류/skip0 | 완료 보고 접수 |
| 실제 양측 runtime/경합/E2E | 실행 안 함 | 미완료 |

## 6. 작업 경계

신규 test resource·생성기/검사기·문서만 작성했다. 기존 A/R2/fixtures-2·제품 코드·LC/Identity·DB·배포·Jira·commit/push 변경 및 외부 메시지 전송 없음.


### 2026-10-14 LC fixture 인수·독립 검증 완료 접수

사용자 전달 LC 검증 결과: ZIP의 fixture10파일 bytes/hash 일치,80벡터+hash/범위1개=81개 통과, 전체 clean test1,029개(81 포함) 실패/오류/skip0. 상태28시나리오는 미실행이다. LC 근거 문서는 docs/codex/LC_BILLING_SIMPLIFIED_FIXTURES_1_VALIDATION.md이며 이번 접수에서 LC 코드/테스트를 자체 재실행하지 않았다.

검토된 한정 계약과 새 fixture의 양측 오프라인 검증 완료를 기록한다. 원 manifest의 lcIndependentValidation=false는 생산 당시 값으로 보존하고 결과는 본 후속절로 갱신한다. 기존 snapshot/fixture bytes는 수정하지 않는다. 남은 삭제 route·과거 owner·자료별 보존과 실제 runtime/Transaction/경합 검증을 이 결과로 완료 처리하지 않는다. 다음은 승인된 범위의 구현 준비/착수 및 미확정 경로의 별도 공동 규격 정리다.
