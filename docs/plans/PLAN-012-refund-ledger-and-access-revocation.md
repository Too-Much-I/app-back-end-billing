# PLAN-012: 전액·부분 환불 원장·기간 재배치·시험 접근 철회

- 작성일: 2026-10-06 / 갱신일: 2026-10-14
- 상태: PLAN-011 진행 중 선행 설계 보완. 승인된 제품 정책은 유지하고 내부 금융/schema/worker exact는 P0에서 동결한다. 코드 구현·LC 변경·실제 환불·배포 승인이 아니다.
- 관련: 상위 TMI-137, PLAN-011 TMI-213~217. 이번에 PLAN-012 Jira 생성·조회·변경은 하지 않았다.
- 선행: [PLAN-010](PLAN-010-purchase-ingestion-and-timeline.md), [PLAN-011](PLAN-011-paid-reservation-and-usage-evidence.md), [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md).
- 기준: [confirm 선행 보호 정책](../contracts/PAYMENT-CONFIRMED-SESSION-REFUND-POLICY-2026-10-13.md), [R2 §5 revoke](../contracts/LC-BILLING-PAID-EXAM-CONTRACT-R2.md), [단순화 한정 수락](../contracts/LC-BILLING-PAID-EXAM-SIMPLIFIED-R1-ACCEPTANCE.md). 한정 수락은 전체 환불 runtime 완료가 아니다.
- 개정 전 상세 전문은 [이력](PLAN-012-HISTORY-BEFORE-REFRESH-2026-10-14.md)에 보존한다. 종전 GRADING-only·별도 채점 승인 API·SigV4·group/version revoke는 현재 요구사항이 아니다. 고정 공동 계약/fixture bytes는 변경하지 않는다.
- LC 전달용: [환불 후 exact Session 보호·재응시 차단 인계](../contracts/PLAN-012-LC-REFUND-SESSION-HANDOFF-2026-10-14.md). 담당 분담·같은 group 새 Session 차단·공동 테스트와 회신 요청을 정리했으며 실제 발송/상대 수락은 별도다.

## 1. 5줄 결론

1. 문의·팀 검토·Store 처리·Billing 반영을 분리하고 검증된 최종 환불/revoke만 반영한다.
2. 부분 환불도 해당 구매 잔여권을 종료하며 다른 구매·무료권·기지급 복원권은 보존한다.
3. 환불 전 confirm된 exact Session만 기존 lifecycle 안에서 완료 가능하며 신규·replacement와 환불 선행 confirm은 차단한다.
4. 구매 root 차단·최초 appliedAt·reflow·금전 사실·분할 작업 등록을 원자 확정하고 LC 전달은 불변 outbox로 복구한다.
5. 지금은 011 source/confirm/guard 접점을 맞춘다. 실제 금융 증거·Mongo 경합·역방향 JWT·LC 수신 검증 뒤 유료 입장을 켠다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 사용자·운영 흐름

문의 REFUND → 인증된 userId와 대상 구매 연결 → 팀 검토·2영업일 내 1차 답변 → Google 지원 Store 경로 실행 또는 Apple 고객 신청 안내 → Store 확정 증거 수신/조회 → Billing 금액 기록·잔여권 종료 → LC 신규 사용 차단 전달 → 결과 안내.

2영업일은 Store 환불 완료 보장이 아니다. 문의만으로 이용권을 종료하지 않는다. 구매별 최소 이용 증거를 검토에 사용하되 자동 반환액 계산·일반 반복 환불·직접 송금·자동 심사는 제외한다. RC 자동 Refund Control/usage 공유도 범위 밖이다.

### 2.2 상황별 처리

| 상황 | Billing / LC 처리 |
| --- | --- |
| 문의 또는 처리 중 신호만 있음 | 최종 환불로 간주하지 않고 현재 유효 권리 유지 |
| 최종 환불 확정·반환액 미상 | 권리 종료, 금액 UNKNOWN/null 대사. 0원/전액 발명 금지 |
| RESERVED, 환불이 먼저 commit | confirm 거절. CANCELED + confirmRejection.code=ENTITLEMENT_REVOKED + terminalAt 원자 저장. LC는 권위 결과 확인 뒤 orphan 보상 |
| confirm이 먼저 commit된 OPEN/진행 Session | 그 exact Session만 기존 제출·채점/lifecycle 안에서 완료 허용. 새 제출 마감·별도 승인 API 없음 |
| RETAKE_AVAILABLE / 새 Session | 종료 source의 신규/replacement 금지. 과거 confirm 예외 승계 금지 |
| GRADING / COMPLETED | 보호 exact Session의 진행·결과 보존. terminal 재개 금지 |
| 보호 Session 진행 중 | 사용자 guard 유지, 종료 때 exact group/operation/version CAS 해제 |
| 다른 구매·무료권·복원권 | 보존. 다른 권리 시작도 기존 guard와 신규 자격 판정 필요 |
| 이미 종료한 구매의 추가 반환/정정 | 검증된 금전 차이만 기록. 재종료/reflow/revoke 재생성 없음 |
| REFUND_REVERSED / 뒤늦은 owned | REVIEW_REQUIRED. 자동 권리 복원/재지급 없음 |
| 탈퇴/삭제/lifecycle deny | 보호 예외로 우회하거나 새 계정에 구매·과거 기록 이전 금지 |

환불/confirm 순서는 Billing 공통 Transaction/CAS의 성공 순서다. Store 발생 시각·클라이언트 시각·worker가 본 현재 상태로 추정하지 않는다. 정상 만료 D1과 환불 D2를 구분한다.

### 2.3 011과의 분담

| 접점 | 011 제공 | 012 연결 |
| --- | --- | --- |
| 구매 귀속 | immutable purchase/entitlement/group/Reservation/Session source | 종료 대상 exact 매칭·다른 source 보존 |
| confirm 경합 | 단일 ClientSession·root 쓰기 경계·성공/거절 replay | 같은 root를 갱신하는 환불 Tx |
| 보호 Session | command TTL과 독립된 confirm 귀속 증거 | 환불 당시 보호 대상 고정 |
| guard | 생성/진행/terminal exact CAS | 보호 진행 유지·환불 선행 예약만 정리 |
| 이용 증거 | reserve/confirm/cancel/expiry·상태 최소 증거 | 구매별 검토 조회. 답안/음성/피드백 복제 없음 |
| LC | source reader·confirm/status-first·원 시험 전환 | 불변 revoke/outbox·역방향 JWT·LC deny |
| 삭제·보존 | TMI-217 별도 공동 계약 | 지연 revoke·과거 owner·삭제 후 재유입과 연계 |

금융 조사·P0 설계는 011과 병행 가능하다. 실제 시험 결합은 TMI-215, 공동 경합 검증은 TMI-216과 맞춘다. 011에서 임시 환불 엔진을 중복 구현하지 않는다.

## 3. 사용자가 결정해야 하는 사항

### 2026-10-14 단순화 승인

사용자 「어 그렇게 하자」로 아래 최소 구현 방향을 승인했다. [검토 근거](PLAN-012-SIMPLIFICATION-REVIEW-2026-10-14.md)를 따르며 환불 구현 착수·실거래 검증·배포 승인은 아니다. PLAN-011은 계속 진행하고012는 후속 계획으로 유지한다.

- 환불 여부/금액의 팀 수동 심사를 유지한다. 자동화 대상은 검증된 Store 결과의 원장·권리 반영이지 환불 심사가 아니다. 문의 없이 Store에서 직접 승인된 환불도 검증 후 반영한다.
- 플랫폼별 실제 검증된 데이터 경로/금액 기준만 고정 매핑한다. 모든 증분/누적·provider 조합을 지원하는 범용 엔진은 만들지 않는다. 아래 §5.1은 필요한 지원 경로의 불변식이며 모든 변형 구현 요구가 아니다.
- 반환액 누락·금액 상충·구매 연결 불명은 기존 durable case에 미해결 상태/최소 증거를 남기고 Slack으로 알린다. 명확한 환불 사실과 금액 불명을 분리하며 검증된 최종 환불은 금액 확인 대기만으로 권리 종료를 미루지 않는다. 거래 귀속/최종 사실 자체가 불명하면 임의 구매를 종료하지 않는다.
- 같은 알림 중복은 정상 자동 멱등 처리다. 매번 사람 검토로 넘기지 않는다. 추가 반환/정정도 버리지 않고 검증된 재조회 근거로 보완하며 임의 금액/DB 수정은 허용하지 않는다.
- 신규 사용은 root에서 먼저 차단하고 시험 정리/LC 전달은 작은 묶음으로 순차 처리한다. 초기 고속 병렬화·별도 다단계 작업 관리 시스템은 제외한다. 진행 위치·기존 lease/CAS·불변 outbox·실패 재시도/적체 경보는 유지한다.
- 전용 관리 화면은 만들지 않는다. 기존 문의·Store Console·최소 구매 증거 조회·case 재개 도구를 사용하고 Slack은 알림 창구로만 사용한다. 처리 상태/결과는 서버에 남기며 Slack 메시지 유실이 작업 유실이 되지 않게 한다.
- 전액/부분 환불 테스트로 플랫폼별 자동 반영 범위를 확인하고 미지원/불명확한 경우를 구분한다. 예외 빈도가 낮다고 가정하지 않으며 특히 Google 부분 환불 보완 조회의 실제 증거를 확인한다. 운영 후 예외 수/적체를 보고 추가 자동화를 결정한다.

감수하는 위험은 일부 금액 확인·LC 전파 지연과 담당자의 예외 검토 업무다. 중복 방지·confirm/refund 원자성·exact Session 보호·다른 권리 보존·reflow·전달 복구와 핵심 테스트는 유지한다. 무기한 방치나 유실을 승인하지 않는다. 운영 담당/적체 알림 기준·batch/retry exact는 P0/P4에서 정한다.

잔여권 종료·confirm 선행 보호·appliedAt reflow·Google 읽기 보완은 재결정하지 않는다. 개발자가 P0에서 다음을 확인해 exact로 고정한다.

- RC/Google 실제 환불 필드·통화/세금·증분/누적 의미·중복 식별.
- 기존 schema에 추가할 필드/unique/index, root CAS와 worker cursor·batch·lease·retry.
- R2 역방향 JWT/receiver 구현 대응 및 보존/삭제 미확정 범위.
- 판매 전 실제 권한·sandbox 거래·경보 담당·복구 절차.

provider 지원 부족으로 정책/지원 범위 변경이 필요하거나 긴 timeline 처리로 사용자 제한이 추가되면 별도 승인받는다. 실제 환불 실행·권한 변경·판매 활성화도 별도다.

RevenueCat 고객 수동 삭제의 5영업일/30일 안은 아직 추천이다. 이 계획에서 승인 정책으로 바꾸지 않는다. 필요한 처리 뒤 삭제 방향과 재조회 의존성만 참조하며 RC 삭제 자동화는 포함하지 않는다.

## 4. 주요 위험과 미확인 사항

| 위험 | 처리 / 확인 |
| --- | --- |
| RC+Google 같은 환불 이중 합산 | 관측 event dedupe와 금융 effect dedupe 분리 |
| 반환액·통화·세금 불명 | null/검토. 최종 환불 증거와 금액 확정은 별도 |
| 환불 선행 후 늦은 구매 | 기존 reversal fence 유지·신규 지급 금지 |
| 무제한 시험 전체 group 한 Tx | root 차단 후 bounded fan-out, 처리 commit 뒤 checkpoint 전진 |
| 긴 timeline | group 분할만으로 해결되지 않음. P0 크기/시간 검증·기존 격리 조건 유지 |
| fan-out 중 terminal/새 guard | 환불 당시 영속 증거에서 보호 대상 결정, exact source만 정리 |
| confirm 증거 누락 | currentSession/command 추측 금지. 신규 사용 차단 유지·검토, 임의 보호/종료 금지 |
| LC 전달 실패 | 금융 rollback 금지·durable retry·인증 차단·지연 경보 |
| RC 고객 삭제 후 조회 불가 | 필요한 최소 증거/미결 책임 인계. 삭제를 미구매/환불취소로 해석 금지 |

현재 [PurchaseCore](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCore.java)는 최초 reversal fence/검토 경로를 갖지만 기존 지급분 종료는 PLAN-012에 남겨 둔다. 이를 환불 원장·reflow·LC 전달 완료로 보고하지 않는다. 011 구현 상태는 해당 계획/runbook을 따른다. 이번에 코드 테스트·외부 provider 조회는 수행하지 않았다.

## 5. 현재 작업과 직접 관련된 구현 계획

- 2026-10-14 예외 축소 승인: 신규 publisher의 잘못된 Retry-After는 기본 backoff/관측으로 처리한다(유효한 긴 대기·총 재시도 한도는 유지). 증거 불명 group만 기존 group에 영속 보류하고 나머지 정리를 진행하며 미해결은 완료로 숨기지 않는다. legacy는 실재 대상 확인 후 한정 이관하고 대상이 없으면 신규 writer부터 적용한다. 정확 규격/회귀는 내부 구현 규격 R1에 반영했다. 기존 다른 sender 계약 변경이나 제품 코드 구현은 아니다.

- 2026-10-14 구체화: [내부 구현 규격 R1](PLAN-012-IMPLEMENTATION-SPEC-R1.md). P0 후보의 저장/인덱스·금액 처리·예약→그룹 순차 처리·confirm 증거·5초/20건/30초 lease·재시도/경보/flag를 내부 구현 기준으로 정리했다. 아래 후보 표현은 이 범위에서 후속 규격을 따른다. 실제 provider 매핑/LC·Mongo/배포 검증을 완료했다는 뜻은 아니다.

- P0 산출물: [최소 기술 설계 R1](PLAN-012-P0-MINIMAL-TECHNICAL-DESIGN-R1.md). 현재 코드/필요 확장과 금융매핑·schema/index·순차worker 후보를 분리했다. 실제 provider 증거·manifest·경합 검증 전 전체 동결은 아니다.
- Identity 운영 확인: [환불 문의 인계](../contracts/PLAN-012-IDENTITY-SUPPORT-HANDOFF-2026-10-14.md). 기존 REFUND 접수는 가능하나 구매 연결/운영 결과 조회·90일 문의보존 경계는 확인 필요. 코드 변경·발송 없음.

### 5.0 구현 순서

| 단계 | 산출물 | 완료 조건 |
| --- | --- | --- |
| P0 증거·저장 상세 | provider 매핑표, 내부 금융/index, 공통 CAS·분할 처리·OFF 의존표 | 기존 코드/fixture 대조·LC 분담 확정. 실제 Store 증거 미확보는 gate로 남김 |
| P1 금융 정규화 | 기존 접수→공통 worker의 환불 처리, 추가 반환·unknown·정정 dedupe | delta/누적 중복·역순·통화/환불 선행 테스트 |
| P2 최초 종료·reflow | root/appliedAt/원장/timeline/fan-out job 단일 Tx | Mongo rollback·unknown commit·append/confirm/refund 경합, TMI-215 결합 |
| P3 분할 정리·LC 전달 | bounded fan-out·불변 outbox·JWT publisher·LC consumer | TMI-216과 실제 V01~V06·보호 Session/guard/응답유실 검증 |
| P4 운영·활성화 | 검토 조회·대사·경보·재개·배포 표 | sandbox 증거/양측 E2E/보존 gate·운영 승인, 신규 입장은 마지막 |

2026-10-14 사용자 요청으로 TMI-137 하위 작업을 생성했다. [TMI-219](https://to-teacher.atlassian.net/browse/TMI-219) 금융 정규화·root 종료/reflow(P1~P2) → [TMI-220](https://to-teacher.atlassian.net/browse/TMI-220) 예약/group 정리·보호 Session·LC 전달(P3) → [TMI-221](https://to-teacher.atlassian.net/browse/TMI-221) 운영 조회·Slack·실Store/LC 통합검증(P4) 순이며 Blocks 관계로 연결했다. 모두 해야 할 일/미배정이다. P0 내부규격과 각 이슈 완료조건을 구현 시 확인한다. Jira 생성은 실제 배포/거래/활성화 승인이 아니며 진행 중인011 브랜치/코드를 변경하지 않았다.

### 5.1 금융 증거·정규화

- 기존 durable 접수→공통 백그라운드 처리 흐름을 재사용한다. 환불 전용 동기 경로·중복 상태 관리 시스템을 추가하지 않는다.
- accessState/최초 effect/appliedAt과 통화·구매액·반환 누적액·금액 기준·출처를 분리한다. 내부 exact 필드/enum은 P0에서 기존 schema와 동결하고 public enum을 임의 추가하지 않는다.
- 기존 exact decimal/Decimal128 규격을 대조하며 float 금지, unknown=null. 동일 통화·세금 포함 기준·원 거래에서만 비교한다.
- INCREMENTAL은 검증된 refund identity당 한 번, CUMULATIVE는 authoritative 총액과 기반영액의 차이만 append-only 기록한다. 공통 식별/하나의 누적 기준 없이 양 feed 합산 금지.
- 동일 증거 no-op, 늦은 낮은 누적액·음수·구매액 초과·통화 불일치는 검토. 검증된 정정은 새 entry이며 overwrite/재지급이 아니다.
- unknown 해소·추가 반환·정확 시각 보완은 최초 access effect/reflow/outbox를 반복하지 않는다. 금융 dedupe는120일 inbox TTL과 분리해 금융 보존 범위 안에서 유지한다.
- 새 observation collection을 기본 전제로 두지 않는다. 기존 접수/원장으로 부족한 업무 유일성만 추가한다. unique/CAS·reader-first schema·명시 migration/rollback을 P0에 포함한다.

Google 보완은 승인된 Orders GET만 사용한다. 후보 경로는 GET /androidpublisher/v3/applications/{packageName}/orders/{orderId}. 서버 package와 검증 Purchase의 reference에 결속하며 앱 orderId로 귀속/지급하지 않는다. partialRefundEvents의 PROCESSED_SUCCESSFULLY·processTime·refundDetails.total/tax는 실제 소비 완료 거래에서 완전성·누적/증분 의미를 검증한다. refund ID 미제공 시 시각+금액을 유일 ID로 발명하지 않는다. androidpublisher scope 자체는 읽기 전용이 아니므로 GET allowlist·최소 Play 권한을 확인한다. 조회 빈도/quota/오래된 주문 가용성은 P0 미확정이다. 자동 환불 실행·운영자 임의 금액 입력·원문/buyer 정보 저장은 제외한다. 상세 기존 조사 내용은 이력 §4를 보존한다.

### 5.2 최초 종료 Tx·분할 작업

1. provider I/O는 Tx 밖에서 인증/정규화. Tx 안에서 환경·구매/source·중복·version 재검사.
2. root 접근 종료 + 최초 appliedAt/effect + 금전 entry 또는 금액불명 책임 + 후속 reflow + durable fan-out job을 같은 Tx에 확정.
3. reserve/confirm/replacement도 같은 root 쓰기/CAS로 순서화. read-only 검사로 경합을 해결했다고 보지 않음.
4. stable source/group 정렬·cursor/index·lease/fencing으로 분할. 개별 group 정리·최초 outbox·진행 위치를 원자 처리하고 crash 뒤 재개.
5. group 정리 완료까지 root 차단을 미루지 않음. 이미 confirm된 성공은 receipt/source 증거에서 replay.
6. 보호 Session은 root 종료 이전 confirm 증거로 고정. worker 실행 때 currentSession으로 변경하거나 terminal 부활 금지.
7. revoked hold를 가용 수량으로 돌리지 않음. 보호 Session guard 유지, 늦은 정리가 새 구매/group/operation 해제 금지.
8. unknown commit/중복은 기존 effect/job/outbox 조회로 수렴. lease 유실 worker의 늦은 checkpoint 쓰기 거절.

한 group당 최초 eventId/body/digest만 유지한다. 추가 금액 정정으로 새 deny version·보호 Session 변경을 만들지 않는다. batch/lease는 기존 worker 재사용을 우선하되 부하 검증 전 값 확정으로 표시하지 않는다.

### 5.3 시각·reflow

providerConfirmedAt은 없으면 null, firstVerifiedAt은 최초 검증, appliedAt은 최초 성공 권리 종료 Tx에 저장한 기준 시각이다. Mongo 실제 commit timestamp가 아니며 unknown commit 후 새 값으로 재배치하지 않는다.

미시작 후속 slot만 기존 sequence/duration으로 앞당긴다. candidateStart=max(appliedAt, preceding valid slot end)를 기존 일정과 대조하며 기존 시작을 늦추거나 overlap을 만들지 않는다. 시작/완료된 다른 권리는 유지한다. 불변식 위반은 rollback/검토이지 임의 기간 소진이 아니다.

| 예제 | 결과 |
| --- | --- |
| A 오전10시 환불을 오후6시 반영, B는 내일 시작 예정 | B를 오후6시로 앞당길 수 있음, 원 duration 유지 |
| B가 기존 일정대로 오후5시 이미 시작 | 기존 일정 유지, 오후6시 재시작 없음 |
| 앞에 유효 C가 남음 | B를 C 종료보다 앞당기지 않음 |
| source 이미 만료·뒤 slot 진행 중 | 금전 기록만 추가, 지난 기간 재지급 없음 |
| provider 시각 보완/추가 반환 | 증거/금전만 보완, 기존 appliedAt/reflow 불변 |

### 5.4 LC 불변 revoke·인증·재시도

[R2 §5](../contracts/LC-BILLING-PAID-EXAM-CONTRACT-R2.md)의 route/body/canonical/오류/역방향 JWT를 사용하며 새 규격을 발명하지 않는다.

- POST /internal/v1/entitlements/access/events, AttemptGroupAccessRevoked v1. confirmedSessionId 필수 nullable, 최초 body 불변. gradingSessionId/accessVersion/stale-version protocol은 제외.
- 같은 eventId/digest는 commit 확인 후 body 없는204. 같은ID 다른내용 EVENT_ID_CONFLICT, 같은 logical group 다른ID GROUP_REVOKE_CONFLICT. 2026-10-14 LC 회신 기준 별도 inbox 없이 그룹별 단일 revoke 문서에 멱등성/deny/보호 예외/최초 사실을 원자 저장한다. eventId/group 유일성은 유지하며 문서 폐기는 LC P6·TMI-217에서 확인한다. 종전 inbox TTL을 전체 문서에 전용하지 않는다.
- revoke-before-session도 deny 영속. 보호된 exact Session만 완료 가능, 삭제/탈퇴/terminal 우선.
- Billing→LC 전용 RS256 JWT·public JWK set·환경/issuer/sub/audience·TLS/connector. LC→Billing 키/권한과 분리, SigV4/무인증 fallback 금지.
- 원 eventId/body 유지, JWT/jti만 매 전송 새 발급. exact204만 성공; 다른2xx/redirect 성공 간주 금지. 응답 유실은 동일 event 재전송.
- 401/403 BLOCKED_AUTH, 영구 계약 오류/409 충돌은 격리·검토. 일시 장애 bounded backoff/jitter, 소진 후 책임/자료 보존. timeout/Retry-After/lease/횟수는 기존 worker와 대조해 P0에서 exact 고정.
- 전파 실패로 금융 확정 rollback 금지. 적체 나이·실패·인증 차단·보류 수를 기존 경보로 노출하고 개인정보/거래 ID를 metric label에 넣지 않음. 실제 Slack 수신자/채널은 운영 검증.

### 5.5 조회·보존·삭제 경계

- 기존 GET /api/v1/entitlements에서 종료 source·다른 기간/무료권·보호 진행 상태를 구분한다. 보호 Session 완료 가능과 새 시험 가능을 같은 boolean으로 간주하지 않는다.
- 조회 판정은 실제 resolver와 일치시킨다. 대사 중을 모든 권리0개로 숨기거나 종료 구매를 유효하게 반환하지 않는다. API 확장이 필요하면 별도 exact/프론트 문서를 맞춘다.
- 이용 증거는 무료/유료·취소/실패·confirmed 시도/완료를 구분. 자료 소실은 UNKNOWN≠0, userId만으로 거래/사용량 확정 금지.
- 금융5년·inbox120일·backup최대35일과 receipt/group 보존은 별개. 마지막 reversal 등 기존 기산점 유지. 금융 전체 자동 purge·RC 자동 DELETE 제외.
- TMI-217 삭제/과거 owner/자료별 보존 확정 전 관련 purge OFF. RC 삭제 후 조회 실패를 미구매/환불 취소로 처리하지 않는다.

### 5.6 활성화·중단 순서

2026-10-14 승인: `billing.payment.refund.processing-enabled`는 환불 반영과 예약/그룹 정리를 함께 제어하고, `publisher-enabled`는 LC 전달만 별도로 제어한다. 둘 다 기본OFF, 별도 fanout 스위치는 만들지 않는다. processing ON/publisher OFF에서는 outbox를 보존하며 전달 재개를 기다린다. publisher ON은 processing ON을 요구한다. 정확 구성표/회귀는 [내부 규격 §7~8](PLAN-012-IMPLEMENTATION-SPEC-R1.md)을 따른다. 기존 DB 검사·RevenueCat 응답 후 검사는 수정하지 않는다.

아래는 논리 기능 상태다. 정확 설정명/의존 검사와 PLAN-013의 종전 GRADING gate 문구 정합화는 P0/P4에서 수행한다.

| 단계 | 환불 처리·대사 | LC receiver / revoke publisher | 신규 paid admission / 판매 |
| --- | --- | --- | --- |
| OFF 개발/배포 | OFF | OFF / OFF | OFF / OFF |
| staging 준비 | 테스트 환경 ON | receiver 준비 후 publisher ON | OFF / OFF |
| staging 통합 | ON | ON / ON 실제 전달 검증 | staging 입장만 ON, production 판매 OFF |
| production 판매 전 | ON·기존 거래 보호 | ON / ON·경보/복구 확인 | 아직 OFF |
| 최종 승인 후 | ON | ON / ON | 순차 ON |
| 신규 판매 중단 | 기존 처리 유지 | 기존 전달/복구 유지 | OFF / OFF |

보호 Session을 종료하는 구 consumer로 rollback하지 않는다. 인증 침해로 처리 기능까지 중단할 때는 별도 장애 절차로 미처리 책임/재조회 구간을 남긴다. 이번에 flag를 켜지 않는다.

## 6. 부록 — 테스트·완료 기준

| 영역 | 필수 사례 |
| --- | --- |
| 금융 | partial→추가partial/full, RC+Google 동일 사실, 중복/역순, unknown 해소, 낮은누적/초과/음수/통화충돌·검증 정정 |
| 지급 보호 | refund-before-purchase·늦은 owned/REFUND_REVERSED, 탈퇴 후 환불, 다른 구매/무료/복원권 불변 |
| 원자성 | root/ledger/reflow/job 각 단계 rollback, 동시 append/refund, unknown commit, appliedAt 한 번 |
| confirm | confirm-first 성공 replay/완료 보호, refund-first 영속 거절, 응답유실/status-first, reserve만으로 보호 불가 |
| 무료 복원 | paid confirm·복원 선행 후 환불에도 복원권 보존, 환불 선행 confirm 실패는 복원0 |
| guard | 보호 진행 유지·terminal 해제, replacement 정리, 늦은 상태/정리의 새 guard 해제 금지 |
| 분할 복구 | 많은 group·긴 timeline·page 경계/crash·lease 유실/동시 worker, outbox 누락/중복 방지 |
| LC | V01~V06 실제 HTTP/JWT, 최초/duplicate/두409·revoke-before-session·lifecycle 우선·단일 revoke 문서 보존/폐기 경계 |
| 보존/조회 | UNKNOWN≠0, 기존 public/internal 인증·no-store, 미결 삭제 방지, RC 삭제 후 조회 실패 구분 |

- 구현 후 ./gradlew clean test 및 replica-set 통합 검증. 단위/fake·공유 fixture·실제 Mongo·양측 HTTP/업무·Store sandbox 결과를 각각 구분한다.
- 기존011 오프라인 수치를012 runtime 통과로 재사용하지 않는다. 실제 RC/Google 소비 완료 거래의 부분 환불 증거/매핑/권한 검증 전 지원 완료를 선언하지 않는다.
- LC reader/보호 예외/역방향 JWT·실환경 port/TLS·경보/복구·보존 gate 뒤 PLAN-013 최종 승인. 실제 외부 금융 작업은 별도 승인이다.
- 이번 변경은 계획/이력/작업 기록뿐. 제품 코드·외부 DTO·schema·fixture bytes·LC/Identity·운영 DB·Jira·배포·commit/push는 변경하지 않는다.
