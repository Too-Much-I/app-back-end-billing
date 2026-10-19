# PLAN-012 P0 — 최소 환불 처리 기술 설계 R1

> 2026-10-14 후속: [내부 구현 규격 R1](PLAN-012-IMPLEMENTATION-SPEC-R1.md)에서 필드/index·예약 우선 정리·confirm 증거·worker/flag/시험 기준을 구체화했다. 본문의 후보와 다르면 후속 규격을 따른다. 실제 provider 매핑/공동 검증/배포는 여전히 별도 gate다.

- 날짜: 2026-10-14 / 조사 브랜치: feat/TMI-216-lc-paid-exam-integration
- 상태: 승인된 단순화의 기술 초안. 기존 코드 정적 대조 완료, 신규 필드/index/worker 값은 구현 전 검토 대상. 실제 provider 증거·schema manifest·LC 통합 검증이 남아 있어 P0 전체 동결/완료가 아니다.
- 기준: [PLAN-012](PLAN-012-refund-ledger-and-access-revocation.md), [LC 인계](../contracts/PLAN-012-LC-REFUND-SESSION-HANDOFF-2026-10-14.md). 011 진행 유지,012 코드 착수·실제 환불·배포 승인 아님.

## 1. 5줄 결론

1. 기존 접수/원장/case/worker를 확장하고 범용 환불 프레임워크·관리 화면·Identity 자동 환불 호출은 만들지 않는다.
2. 현재 PurchaseObservation.money는 STORE_PAID 구매 금액이다. 환불 금액으로 재사용하지 않고 별도 검증 증거를 추가해야 한다.
3. 권리 종료 사실과 반환액 확정을 분리한다. 귀속과 최종 환불이 확실하면 금액 불명이어도 종료하고 금액만 검토에 남긴다.
4. 최초 종료 Tx 뒤 구매별 cursor로 group을 순차 처리하고 group별 불변 outbox를 전달한다. 기존 lease/CAS/worker 운영 방식을 재사용한다.
5. Identity 문의 접수는 현재 API로 가능하지만 구매 연결·운영 결과 기록은 없다. 90일 문의 보존과 법정/업무 기록 분리는 운영 전 확인이 필요하다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 2.1 현재 구현 사실과 필요한 확장

| 현재 정적 확인 | 012에서 할 일 |
| --- | --- |
| PurchaseCore reversal은 REVERSAL_REQUIRES_PLAN_012 검토로 인계, 최초 역순 관측 fence 존재 | 기존 권리 종료·reflow·금액/후속 전달 구현 |
| PurchaseObservation.money.basis=STORE_PAID, Decimal128 | 별도 refund evidence 추가. 구매액/반환액 의미 혼용 금지 |
| RC REST decoder의 status=refunded는 REFUND, money=null | 최종 증거/귀속 검증 후 종료 경로 연결, 원 통화 반환액은 별도 확인 |
| RC webhook CANCELLATION/REFUND_REVERSED는 review 종류 | CANCELLATION 문자열만으로 최종 환불 단정 금지. 실제 payload 매핑 검증 후 지원 |
| purchases/entitlement/timeline version 및 paid 예약의 touch/CAS | 같은 쓰기 경계에 환불 연결. 동시 confirm 테스트 필수 |
| payment_purchase_cases·PaymentReviewOperator ACCOUNT/AUTH | case 재사용, refund case 조회/검증 재조회 재개 기능은 최소 확장 필요. 이미 지원한다고 보고하지 않음 |
| PaymentProviderAlerts 일일 요약·인증장애, PaymentSlackSink | refund 미해결/적체 요약 추가. 기존 case 총건수만으로 열린 환불 건수를 표현하지 않음 |

### 2.2 자동 처리 매핑표

| 입력 | 권리 처리 | 금액 처리 | 아직 확인할 것 |
| --- | --- | --- | --- |
| 기존 검증 거래의 RC REST refunded | 최초 종료 가능 경로 | 원 통화 실제 반환액 미확인 시 null/case | 실제 소모성 환불·환경/owner 매핑 |
| RC webhook 환불 후보 | 현재는 review→인증된 재조회 | 자동 전액 추정 금지 | 전액/부분 최종 상태 구분·실제 payload |
| Google Orders 성공 부분 환불 | 기존 구매 exact 연결·최종성 확인 뒤 종료 | 동일 통화/세금 기준의 검증 누적액 차이만 반영하는 후보 | history 완전성·total/tax·중복/추가반환·주문 접근 권한 |
| 금액만 누락/상충, 최종 환불 자체는 확실 | 종료 유지 | durable case, 검증된 재조회로 보완 | 플랫폼별 권위 금액 기준 |
| 귀속/거래/최종 상태 불명 | 임의 구매 종료 금지 | case·Slack | 연결 근거 복구 |
| 동일 알림/동일 누적 금액 | 최초 결과 유지 | 추가 entry0 | 자동 멱등 회귀 |
| 낮은 누적/정정/REFUND_REVERSED | 자동 권리 복원 금지 | case, 원장 덮어쓰기 금지 | 검증 증거로 처리할 정정 경로 |

Google 누적 경로가 실제 지원된다고 아직 확정하지 않는다. RC 금액과 Google 금액을 더하지 않는다. Store 직접 승인 환불도 문의 유무와 무관하게 처리한다. 예외 빈도는 측정 전 미상이다.

## 3. 사용자 결정 / 외부 확인 필요 항목

2026-10-14 사용자 운영 선택: 본인 담당/팀원 대행, 접근 제한 Notion 답변·최소 처리기록, tosunsaeng093@gmail.com 연락 창구. 금융5년·해당 불만/분쟁 최소근거3년 기준/불필요 원문 분리 방향을 채택한다. [운영 승인 상세](../contracts/PLAN-012-IDENTITY-SUPPORT-HANDOFF-2026-10-14.md) 참조. 실제 권한/페이지 생성·본인확인 상세·정확 보존 기산점·SaaS 개인정보 처리 적합성 확인은 별도이며 금융 원장을 Notion으로 이전하지 않는다.

2026-10-14 Identity 회신 접수: 기존 REFUND API 유지 가능. 문의90일/멱등7일, 선택 이메일 소유 미검증, ACTIVE GUEST 접수 가능, 실제 운영 권한/답변관리 미준비를 확인했다. [접수 기록](../contracts/PLAN-012-IDENTITY-SUPPORT-HANDOFF-2026-10-14.md)을 따른다. 문의번호로 만료 전 인증 userId를 읽되 구매 귀속은 Billing에서 다시 검증한다.7일 후 재접수는 새 환불 effect의 근거가 아니다.

운영 전 남은 항목은 담당/읽기 감사·고객지원 기록 위치·이메일 누락/로그인 불가 연락과 본인확인·최소 근거 보존 항목/기간·만료 전 인계 확인·Slack 복제본 보존이다. 이 항목들이 코드 구현 전체를 중단시키는 조건은 아니지만 준비 없이 운영 가능으로 선언하지 않는다. 문의 전체TTL 연장/원문 전체 복제는 하지 않는다.

새 제품 정책 선택은 없다. 다음은 운영·기술 확인이며 이번 문서로 자동 승인하지 않는다.

- 실제 Apple/Google 전액·Google 부분 환불 테스트 거래, 읽기 권한과 sanitized 증거 확보. 실환불 실행은 별도 승인.
- Identity 문의의 권한 있는 조회 방법·담당/답변 기록·90일 이후 필요한 기록 인계. [Identity 안내](../contracts/PLAN-012-IDENTITY-SUPPORT-HANDOFF-2026-10-14.md) 참조.
- 최초 환불 시점의 exact Session 판정을 위해011 confirm 귀속이 충분한지 실제 저장 모델 검증. timestamp만으로 순서를 만들지 않음.
- 신규 validator/index manifest·이전/롤백·기능 flag 정확 이름, 긴 timeline 한계. 본문 후보를 배포된 규격으로 취급하지 않음.

## 4. 주요 위험과 미확인 사항

- 운영자 CLI는 현재 계정 조회 재개/인증 복구용이지 환불 금액 편집기가 아니다. 기존 기능으로 환불 case가 해결된다고 가정하지 않는다.
- 초기 refund root와 reflow Tx 크기 제한은 그대로 남는다. 과대 timeline은 기존 격리·경보 대상이며 사용 제한/Tx 분리 정책을 몰래 추가하지 않는다. 판매 전 실제 상한/성능 검증 필요.
- case가 생성됐다는 사실과 운영자가 확인한 사실은 다르다. Slack 실패/중복에도 DB의 미해결 책임이 유지되어야 한다.
- 후속 group 정리 전에 Session 증거가 변경/삭제될 수 있다. 보호 대상을 currentSession이나 같은 millisecond 비교로 선정하지 않는다. 필요한 증거 보존은 TMI-217과 연결한다.
- active guard 때문에 다른 유효권이 있어도 바로 새 시험을 못 시작할 수 있다. 보호 Session 종료·예약 정리 후 기존 규칙대로 해제하며 timeout으로 임의 해제하지 않는다.

## 5. 최소 저장·처리 설계 (구현 전 후보)

2026-10-14 스위치 승인: 처리+정리는 processing-enabled 하나, LC 전달은 publisher-enabled로 분리한다. 별도 fanout flag는 제외하고 내부 규격 §7의 두 스위치 구성표를 따른다. 기존 schema 검사/RC 응답 후 검사는 유지한다.

2026-10-14 승인 후속: 아래 초기 후보 중 잘못된 Retry-After 즉시 검토, 증거불명 group에서 전체 cursor 정지, 일반 legacy 이관 전제는 [내부 구현 규격 R1](PLAN-012-IMPLEMENTATION-SPEC-R1.md)의 기본 backoff/해당 group만 보류/실재 대상 한정 이관으로 대체한다. 이하 후보는 변경 이력으로 읽으며 새 구현 요구를 중복하지 않는다.

### 5.1 저장 경계

새 범용 observation collection은 추가하지 않는다. 기존 normalizedObservation에 선택적인 refundEvidence를 추가하는 새 내부 revision을 제안한다. 이전 row는 필드 부재를 금액 미상으로 읽고 구매 money를 환불액으로 backfill하지 않는다.

| 위치 | 추가 후보 | 의미 |
| --- | --- | --- |
| normalizedObservation.refundEvidence | revision, source, providerConfirmedAt(nullable), amountMode=UNKNOWN/CUMULATIVE, amount(nullable Decimal128), currency/basis(nullable), evidenceKey | 검증 adapter만 구성. UNKNOWN은 amount/currency/basis 모두 null. 초기 범위는 검증된 누적 경로만, 임의 증분 지원 제외 |
| purchases.refund | effectId, appliedAt, providerConfirmedAt(nullable), refundedTotal(nullable), currency/basis(nullable), amountSource(nullable), version | 최초 종료 effect/appliedAt 불변, 검증 금액 갱신은 CAS. currency/기준 변경은 case |
| purchases.refundWork | state=PENDING/RUNNING/DONE/REVIEW_REQUIRED, afterGroupId(nullable), owner/until(nullable), leaseVersion, nextAttemptAt, attempts, startedAt, failure(nullable) | 구매당 하나의 순차 fan-out 진행. 기존 provider inbox 처리 완료와 별개 책임 |
| payment_ledger | ACCESS_REVOKED, REFUND_AMOUNT_CONFIRMED, TIMELINE_REFLOWED 종류 후보 | 기존 effectKey unique 재사용. 금액 entry에 delta/누적/기준·증거key, 종료 effect와 연결 |
| payment_purchase_cases | refund reason, purchaseId(nullable), inquiryId(nullable), reviewState, reviewedAt/actorDigest(nullable) 후보 | 원문 문의·영수증 복제 없음. 조회/검토 감사 및 재조회 책임 |
| payment_access_outbox (신규 최소 collection 후보) | 불변 R2 event/body/digest·group·purchaseId와 전송 work | LC wire에 purchaseId·version을 새로 추가하지 않음. 미전송 행 자동 TTL 금지 |

UUID/식별자는 기존 canonical 규격, DB 시각은 UTC millisecond, version/attempts는 BSON long과 overflow 거절, 금액은 Decimal128로 한다. 부분 필드 조합 금지/all-or-none validator는 exact manifest에서 고정한다. 금융 보존5년 정책과 outbox/보호 증거 보존을 혼용하지 않는다.

기존 ux_purchase_transaction, ux_entitlement_purchase, ux_entitlement_sequence, ux_payment_ledger_effect는 유지한다. 추가 index 후보는 다음과 같으며 전부 이름·순서·옵션을 manifest 검증 대상으로 한다.

| 후보 이름/위치 | ordered keys | 옵션 |
| --- | --- | --- |
| ix_purchase_refund_due / purchases | environment, refundWork.state, refundWork.nextAttemptAt, _id | non-unique, no TTL, no partial |
| ux_access_outbox_group / payment_access_outbox | environment, producer, consumerScopeId, attemptGroupId | unique, no TTL, no partial |
| ux_access_outbox_event / payment_access_outbox | eventId | unique, no TTL, no partial |
| ix_access_outbox_due / payment_access_outbox | environment, work.state, work.nextAttemptAt, _id | non-unique, no TTL, no partial |

group source→purchase 조회 index는011의 최종 필드 경로를 대조한 뒤 추가한다. 그 경로/confirmed 증거·schema revision 번호·case 확장 manifest 미확정이므로 index 전체 동결 완료가 아니다. 자동 startup DDL/drop은 금지하고 명시 migration·reader 선행으로 적용한다.

### 5.2 원자적 처리

1. provider 조회/인증/정규화는 Tx 밖. 기존 lifecycle/recovery fence와 source lease 검증 후 구매·account·entitlement·timeline을 재확인한다.
2. 최초 root 종료·고정 appliedAt/effect·reflow·금액 entry 또는 case·refundWork=PENDING을 한 ClientSession Tx로 저장한다. 이전 성공은 조회해 replay. source 접수 책임을 이관하기 전에 inbox를 완료/폐기하지 않는다.
3. 최초 지급이 없던 reversal fence와 기존 grant 종료를 구분한다. 종료된 구매에 늦은 owned가 와도 지급하지 않는다. refund-first confirm 거절과 confirm-first 성공 replay는011 공통 쓰기로 경합한다.
4. worker가 해당 purchase source group을 _id 오름차순으로 bounded 조회한다. root 후 신규 group/confirm을 금지하는 공통 fence가 cursor 완전성 전제다. 신규 삽입/증거 purge가 이 전제를 깨면 완료로 표시하지 않는다.
5. 각 group Tx에서 환불 당시 confirm 영속 증거로 보호 S1을 결정하고 최초 outbox 삽입·해당 예약/guard 정리·cursor 이동을 원자 처리한다. 기존 outbox는 내용 변경 없이 재사용한다. 동일 timestamp로 선후를 추정하지 않는다. terminal/lifecycle deny 우선.
6. group 열거 종료는 fan-out DONE이지 LC 전송 완료가 아니다. 미전송 outbox가 전달 책임을 유지하며 별도 적체로 관측한다. 금융 권리 종료는 LC 장애로 rollback하지 않는다.

### 5.3 순차 처리·실패 운영 후보

- 기존 provider 접수 poll1초/batch100은 변경하지 않는다. 새 fan-out/publisher는 초기5초 poll·한 구매당 최대20 group·순차 실행을 제안한다. throughput SLA가 아니라 튜닝 가능한 초기값이다.
- lease30초와 version fencing, 개별 Tx/HTTP 전에 유효성 확인·필요 갱신. 작업1회20개를 한 거대 Tx로 묶지 않는다. lease를 잃은 작업은 cursor/전송완료를 쓰지 못한다.
- HTTP timeout5초 후보, backoff5초~1시간+jitter·24시간 소진 후 REVIEW_REQUIRED. 401/403 BLOCKED_AUTH,409/영구계약오류는 검토. 429/503 Retry-After는 초/HTTP-date 검증 후 backoff보다 늦은 시각 사용, 잘못됨/24시간 초과는 검토. exact 파서/중복header 동작은 기존 계약 대조 후 동결한다.
- 재전송은 event/body 고정·JWT/jti만 새 발급, exact204만 성공. crash/응답유실은 동일 event 재전송으로 수렴한다.
- 일반 금액 예외는 기존 KST09시 일일 Slack 요약에 열린 환불 수·최장대기·기준 초과 수 추가. 신규 일반 예외를 중대 장애로 매번 호출하지 않는다. 인증/전파 적체는 별도 장애 알림. 전파 경보 초기60초 후보는 기존013 기준을 대조하며 순차 처리 실측 뒤 운영값 확정.
- Slack에는 집계·환경·runbook만, 사용자/거래 원문 없음. 담당자는 별도 인증된 최소 CLI로 case를 조회하고 검증 재조회만 재개한다. 현재 ACCOUNT/AUTH CLI에 refund case 대상·감사 추가가 필요하며 금액 직접 입력·강제성공 기능은 만들지 않는다.

## 6. 검증표·P0 종료 조건

| 검증 | 증거 / 현재 상태 |
| --- | --- |
| Apple/Google 전액 | 실제 최종 상태·원 통화 반환액 유무·귀속·중복 관측 확보 필요 |
| Google 부분/추가 반환 | 동일 누적 재조회·중복·상충·성공/대기 구분, Orders 권한/완전성 필요 |
| 금액 미상 | 종료+case 분리, 구매 money를 반환액으로 쓰지 않는 테스트 필요 |
| schema | 새 optional reader/manifest/index·기존자료 migration·rollback 제한 확정 필요 |
| 순차 worker | page crash/lease유실/동시poll·checkpoint/outbox 원자성·증거 유지 실제 Mongo 검증 필요 |
| 011 연동 | confirm/refund 양순서·기지급복원권·새guard 보호·same-key120일/lifecycle, LC H01~13 필요 |
| 운영/문의 | 문의ID→인증user→구매 확인 수단·처리기록/보존·Slack 실제 수신·재개 권한 확인 필요 |

P0는 매핑과 schema/worker 규격을 검토해 고정하고 실제 provider 지원 증거의 확보/미확보를 표시한 뒤 종료한다. 지원 미확보를 fake 테스트로 완료 처리하지 않는다. 다음 구현 단위는 P1 금융 정규화이며 별도 착수 요청/Jira 승인 뒤 진행한다.

### 정적 근거

- [PurchaseObservation](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseObservation.java), [PurchaseCore](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCore.java), [schema](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCoreSchema.java)
- [RC REST decoder](../../src/main/java/web/tosunsaeng/billing/domain/payment/provider/RevenueCatPurchaseDecoder.java), [webhook decoder](../../src/main/java/web/tosunsaeng/billing/domain/payment/provider/RevenueCatWebhookDecoder.java)
- [paid reservation](../../src/main/java/web/tosunsaeng/billing/domain/reservation/paid/PaidReservationService.java), [worker](../../src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentSourceWorker.java), [lease](../../src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentSourceWork.java)
- [operator](../../src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentReviewOperator.java), [alerts](../../src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentProviderAlerts.java)
- 이번에는 로컬 정적 읽기/문서만 수행. 실환경/Store/RC 조회·Gradle·상대 코드 변경·외부 메시지·Jira·DB·배포 없음.
