# LC–Billing 유료 시험 R2 — 공동 구현 합의안 R1

- 날짜: 2026-10-14 / revision: lc-billing-paid-exam-r2-implementation-agreement-r1
- 상태: 공동 최종 수락을 위한 제안. LC 최종 수락·제품 구현·배포 승인을 대신하지 않는다.
- 대상: [R2 draft-2](LC-BILLING-PAID-EXAM-CONTRACT-R2.md), lc-billing-paid-exam-r2-fixtures-2.
- 관련: PLAN-011/012/013, TMI-116/TMI-128/TMI-122, TMI-125.

## 1. 5줄 결론

1. R2 wire를 새로 만들지 않고 검증된 원본 revision/bytes를 공동 구현 기준으로 수락할 범위를 정리한다.
2. Billing은 source·confirm·복원·원 그룹 replacement 차단을 하나의 Transaction으로 저장한다.
3. LC는 기존 operation으로 마무리와 복구를 담당하고 자기 DB commit 뒤 앱 성공을 반환한다.
4. 양측 오프라인 검증은 완료 보고됐으나 실제 HTTP/Transaction/상태 시나리오 검증은 남아 있다.
5. 아래 J1~J4와 S15/V07은 미확정으로 구분하며, 이 문서 작성만으로 공동 계약 동결을 선언하지 않는다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 수락 대상으로 제시하는 원본

| 자료 | 식별자/증거 |
| --- | --- |
| 계약 | lc-billing-paid-exam-contract-r2-draft-2 |
| 계약 SHA-256 | 3210c7eef1fb2f9fdde101c2384c86a5bbde1f9be346da0651c971e357444c52 |
| fixture | lc-billing-paid-exam-r2-fixtures-2 |
| SHA256SUMS 자체 SHA-256 | bb4458f3e7b6ed801795fbd652fc41e2d5e6c6c2d752b9ef693d6f43e4893659 |
| Billing 검증 | [보고서](LC-BILLING-PAID-EXAM-FIXTURES-2-VALIDATION.md)의 오프라인 검사 |
| LC 검증 | 사용자 전달 2026-10-14 독립 검증·인수 기록: 14파일 bytes 동일, 독립89개·전체948개(89 포함), 실패/오류/skip0 |

LC 결과는 전달받은 보고에 근거하며 이번 작성에서 LC 테스트를 재실행하거나 LC 코드를 검토하지 않았다. manifest의 제작 당시 lcIndependentValidation=false는 보존하고 후속 기록으로 검증 상태를 설명한다. 원본 계약/fixture를 수정하지 않는다. 문구/bytes 변경이 필요하면 새 revision과 영향 fixture 검증을 남긴다.

### 2.2 기존 합의 유지

- 공개 시험 생성은 body 없음·기존 Idempotency-Key·성공 DTO 유지. 내부 command header와 status body userId/operationId를 구분한다.
- LC는 restartContext의 원 대상·세 startReason·단일 mock를 고정한다. Billing은 실제 source/kind를 결정하고 source/transition을 reserve/status에서 복구한다.
- EXAM_PASS/SUBSCRIPTION만 외부 source이며 trial/복원/초대 출처와 구매 ID는 Billing 내부다.
- 미완료는 같은 문제, 전체 접수 후 요약 대기는 직전 문제 제외(실제 다른 후보0만 예외), 완료 후 기존 선정 규칙이다. 전체 접수/완료 뒤에는 항상 INITIAL이다.
- 모든 권리 전환에서 신규 제출만 막고 기접수 채점·파생 Summary·시스템 재시도는 완료한다. 새로운 제출 마감·별도 채점 승인 API 없음.
- refund 선행의 영속 CANCELED/confirmRejection과 confirm 성공 선행을 구분한다. terminalAt을 사용하고 rejectedAt을 추가하지 않는다.
- 기존409/503·Retry-After:1과 신규 두409를 구분한다. ingress404/auth 격리를 단순 재시도로 합치지 않는다.
- 012 revoke는 group별 최초 불변 eventId/digest/보호 Session, 기존 R2 reverse JWT/오류 순서를 따른다. A 인증/fixture는 유지한다.

## 3. 공동 확인 및 수락할 사항

| ID | 담당 | 제안/확인 내용 | 완료 증거 |
| --- | --- | --- | --- |
| J1 | LC | 모든 생성 경로가 기존 미완료 operation guard를 확인하고 Billing confirm 뒤 LC 마무리 전 새 key를 차단한다. 기접수/시스템 Job은 별도 구분 | LC operation·접수 증거 설계 및 S05~S12 실제 테스트 |
| J2 | Billing+LC | Billing은 confirm 결과까지만 안다. LC 마무리 완료를 새 ack로 통지하지 않고 LC가 같은 operation/status로 복구한다 | 양측 성공 유실·마무리 실패 재현, 추가 route 없음 |
| J3 | Billing+LC | reservation 생성 전 terminal context 실패의 same-key reserve 재현은 영속 receipt로 처리. status에서 이 실패를 어떤 기존 DTO/오류로 관측하는지 exact 대조 필요 | fixture에 부족하면 새 revision/케이스. null 예약이나 OPERATION_NOT_FOUND를 임의 성공/안전 보상으로 해석 금지 |
| J4 | Billing+LC | T2의 source별 owner snapshot과 LC local lifecycle/삭제가 충돌할 때 owner fence 우선. paid/MEMBER 보상권을 기존 phone subject로 위장하지 않음 | source별 owner/삭제 조합표, 기존 trial 회귀 |
| S15 | Billing+LC/lifecycle | phone+ACTIVE paid 선택·startReason 운반, 삭제 보존 sourceExamId/접수증거·claim/transfer/해제·unknown 복구 | 정상/거절 기대값 공동 확정 후 실제 테스트 |
| V07 | Billing+LC/lifecycle | receipt/group 최초 사실/tombstone/legacy 목록의 보존·폐기·탈퇴/삭제 및 늦은 재유입 | exact 기한·폐기 후 동작·운영 gate 합의. 임의120일 전체삭제/무기한 개인정보 보존 금지 |

J1/J2는 R2 요구를 구현에 매핑하는 확인이며 새로운 제품 선택이 아니다. J3/J4는 구현 누락을 방지하기 위한 기술 확인이다. S15/V07의 정상 동작은 아직 수락 요청 가능한 확정안이 없으므로 성공한 것으로 동결하지 않는다.

## 4. 주요 위험과 미확인 사항

오프라인 HTTP12는 실제 security chain/port/TLS/no-store/DB 무호출 검증이 아니다. wire6/status3도 실제 서비스 결과가 아니다. 상태26개는 미실행이다. 각 저장소 전체 테스트 수를 합산하거나 LC oracle을 운영 codec으로 설치하지 않는다.

011의 Billing 결합 저장에는 서로 다른 Transaction 실행 방식 연결 문제가 있다. [저장 설계 §5.5](../plans/PLAN-011-STORAGE-TRANSACTION-DESIGN-R1.md)의 단일 ClientSession adapter를 Billing 내부에서 검증한다. LC는 Billing 컬렉션/인덱스를 구현할 필요가 없다.

지원하지 않는 phone/삭제 조합을 몰래 일반 INITIAL로 통과시키지 않는다. 보류 때문에 모든 계정의 시험을 영구 거절하는 정책도 만들지 않는다. 지원 범위와 사용자 오류·복구를 확인한 후 해당 기능을 활성화한다.

## 5. 구현 책임과 실패 복구

| 시점 | Billing | LC |
| --- | --- | --- |
| 준비 | 기존 성공/실패 replay 및 신규 owner/context 검사 | 접수 증거·원 대상·mock 고정, 이전 제출과 준비 순서화 |
| reserve | 권리 선택·hold·pending guard·source/transition/receipt 원자 저장 | 응답/요청 대조, 확인 대기 Session·operation 저장 |
| confirm | paid 확정·필요 복원·원 재응시 차단·guard·성공 증거 원자 저장 | 같은 operation으로 confirm, 결과 불명은 status-first |
| confirm 거절 | 영속 거절 commit 후 응답. 이미 성공이면 성공 replay | 권위 terminal/귀속 증거 확인 뒤만 보상 |
| 마무리 | LC 완료를 추정하지 않음 | 이전 신규 제출 차단·새 활성·SUCCEEDED 로컬 commit 뒤 앱 성공 |
| 늦은 작업 | 원 group 상태만 반영. 새 guard 해제 금지 | 기존 접수/파생 작업은 원 시험에서 완료. lifecycle deny 우선 |
| 환불 | 012에서 권리 종료·최초 revoke/outbox·보호 Session 고정 | 최초 이벤트·group 증거로 멱등 차단, 보호 exact Session 완료 허용 |

Billing Tx 성공 뒤 LC 장애는 무료권 재지급/confirm cancel로 되돌리지 않는다. LC 미완료 operation에는 완료 전 TTL을 적용하지 않는다. pending은 새 key 생성 허용이 아니다. 기존 operation worker 재시도·관측/경보를 사용하며 DB 수동 수정으로 정상 복구를 대체하지 않는다.

### 5.1 배포와 테스트 분담

1. 양측 수락 기록에 확정 범위와 J/S/V 미확정 목록을 적는다. 아직 상대 서비스가 수락하지 않은 항목을 완료로 쓰지 않는다.
2. Billing은 schema/동일 session·reserve/confirm/복원/guard, LC는 reader/접수/operation 마무리를 기본 OFF로 구현한다.
3. 정적 fixture와 실제 runtime 검증을 분리하고 same-key replay, unknown commit, 늦은 완료, refund 양순서 및 replica-set 경합을 검증한다.
4. LC reader 선행·구 worker drain·legacy 최종 목록·앱409 reader·012 revoke·실제 origin/port/키/TLS·복구/보존 gate를 확인한다.
5. 공동 E2E 후 배포/활성화 승인 절차를 진행한다. fixture 통과만으로 유료 입장을 ON하지 않는다.

## 6. 부록 — 수락 기록 양식 및 변경 경계

공동 최종 수락 시 다음을 실제 확인 결과로 채운다. 빈 칸은 승인/통과가 아니다.

| 항목 | 현재 |
| --- | --- |
| Billing 최종 수락 일자/범위 | 미기록 — 이번 작업은 합의안 작성 |
| LC 최종 수락 일자/범위 | 미기록 — 전달된 보고는 독립 검증 완료 |
| 원본 revision/hash | §2.1의 값 유지 |
| J1~J4 확인 결과 | 공동 확인 대기 |
| S15/V07 제외/지원 범위 | 보류, 활성화 전 영향 확인 |
| 실제 runtime/E2E 결과 | 미실행 |

이번 변경은 Billing 문서 작성만이다. LC에 외부 메시지를 보내거나 LC 저장소·제품 코드·DB·A 계약/fixture·paid fixture·배포를 변경하지 않았다. 전체 Gradle은 문서 작업이라 실행하지 않았으며 문서 링크와 원본 hash를 확인한다.
