# TMI-212 — 금융 기록 사용자 연결 제거 이관 설계

> 2026-10-14 사용자 승인으로 **현재 구현 순서에서 제외한 후속 검토안**이다. 아래 nullable 모델·partial unique·이관 순서는 과거 제안이며 착수 기준이 아니다. 5년 보존 종료 정리 기능은 현재 결제 출시의 선행 조건에서 분리한다. 기존 보존기간을 연장하거나 삭제 의무를 면제한 것은 아니다.
>
> 현재는 보존 기준과 후속 작업만 유지하고 결제·환불 구현을 우선한다. 실제 최초 보존 만료일과 legacy 유입 여부를 확인하고, 만료 전 담당·일정을 정해 최종 삭제 범위/오래된 재수신 방지/예외 보존을 확정한 뒤 구현한다. 실제 최초 만료일은 아직 조회하지 않았다. 이미 만료되었거나 임박한 자료가 발견되면 이 유예를 그대로 적용하지 않고 별도로 처리한다.
>
> 미구매 탈퇴 계정15일·접수120일·Trial3년·backup35일 정책 및 현재 처리/복원 보호는 유지한다. 이미 구현한 암호화 reference 정리 코드는 제거하지 않고 실제 삭제flag는OFF를 유지한다. 금융 기록 전체 삭제 또는 특정 자동 지급 연령 제한의 exact를 승인한 것은 아니다.

## 1. 5줄 결론

1. 별도 승인 서비스나 관리 웹을 만들지 않고 기존 Transaction·epoch fence·시간당 bounded worker를 확장하는 안을 권장한다.
2. 구매별 보존 만료 후 Purchase.accountId/ownerRefId와 해당 기간권.accountId를 null 처리한다. 같은 계정의 새 구매·유효 이용권·구매 연결 ID는 유지한다.
3. 지급 중복 방지용 거래 키와 금융 사실은 이번 사용자 연결 제거에서 삭제하지 않는다. 이것을 완전 익명화 또는 무기한 금융 기록 보관 승인으로 해석하지 않는다.
4. null 허용 schema, 기간권 unique index, 과거 거래 재수신·sync 처리를 먼저 바꿔야 한다. 기존 writer에 단순 unset을 추가하면 안 된다.
5. 이 문서는 코드 조사에 근거한 **이관 제안**이다. 제품 코드·DB schema·배포는 변경하지 않았다. 후기 금융 기록 삭제와 늦은 환불 처리 규격은 별도 마무리가 필요하다.

## 2. 사용자가 반드시 읽어야 하는 내용

예를 들어 같은 사용자가 2026년과 2032년에 각각 구매했다면, 보존기간이 끝난 2026년 구매에서 사용자로 찾아가는 연결만 제거한다. 2032년 구매와 현재 계정은 유지한다. 계정 전체를 지우는 방식은 새 구매까지 영향을 주므로 사용하지 않는다.

**권장 구현은 필드의 명시적 null + 제거 완료 시각이다.** 새 연결 collection을 만들어 모든 reader를 이관하는 방법보다 변경 범위가 작다. 대신 기존 null 비허용 코드와 unique index를 빠짐없이 바꿔야 한다. 사용자 ID를 다른 고정값이나 가짜 UUID로 바꾸는 방법은 쓰지 않는다.

기존 [참조 정리](../contracts/TMI-212-reference-retention-r1.md)는 암호화 조회 참조만 지운다. 이 설계는 그 다음 단계이며 205의 **미구매 계정 삭제**와 구분한다. 탈퇴 즉시 구매 기록을 지우거나 동일 번호 재가입자에게 복원하는 변경이 아니다.

## 3. 결정·미확정 경계

- 권장: 아래 null 모델·구매별 원자 처리·reader-first 이관. 별도 운영 승인 collection/API는 추가하지 않는다.
- 기존 보존기간은 그대로다. 최종 provider transaction·기간권 종료·마지막 reversal 중 가장 늦은 기준의 5년을 사용한다. 단순 조회 시각으로 연장하지 않는다.
- 거래 HMAC·금융 사실을 사용자 연결 제거 뒤 얼마나 더 남길지, 언제 최종 삭제할지는 이 문서에서 새로 정하지 않는다. 개인정보성이 사라졌다고 단정하지 않는다. 운영 활성화 전 기존 5년 정책과 일치하는 최종 삭제 규격을 닫아야 한다.
- 연결 제거 이후 **진짜 추가 반환/정정**을 받으면 자동 재연결하지 않는다. PLAN012에서 거래별 금전 사실 기록과 보존 재계산을 정해야 한다. 현재 reversal 처리 자체가 후속이라 이 부분을 구현 완료로 간주하지 않는다.
- null 거래에 대한 public sync 오류의 exact 코드·HTTP 상태는 기존 오류 목록과 프론트 계약을 검토해 구현 전에 고정한다. 다른 소유자/연결 제거 여부는 사용자에게 구분해 노출하지 않는 권장안이다.

## 4. 위험·미확인 사항

- accountId만 지워도 다른 source/sync/사용 증거에서 purchaseId로 역추적되면 연결이 남는다. 모든 역방향 연결을 확인하기 전 전체 개인정보 제거로 표시하지 않는다.
- `payment_ledger.sourceObservationKey`는 inbox/case와 이어질 수 있다. 미해결 case는 그대로 보존하고 해당 구매 제거를 보류한다. terminal 접수·sync도 기존 보존이 끝나 실제 삭제된 뒤 진행한다. 원장 자체를 덮어써 연결을 없애지 않는다.
- PLAN011 구매별 사용 증거와 LC 계약은 별도 동시 작업 중이다. Session/Reservation/ledger에 추가되는 purchase 연결의 보존·분리 방식도 적용 목록에 포함해야 한다. 임의로 LC 데이터나 시험 기록을 삭제하지 않는다.
- HMAC 거래 키는 가명 식별자이지 익명화 증명이 아니다. lookup key 교체 이후 이전 거래 탐색 가능성도 별도 검증이 필요하다.
- 실환경 자료·부하·복원본·개인정보 법적 보존 예외는 이번에 검증하지 않았다. 사용자 연결 제거와 금융 기록 최종 삭제를 혼동하지 않는다.

## 5. 구현·이관 상세

### 5.1 제안 schema

| Collection | 변경안 | 유지 |
| --- | --- | --- |
| purchases | accountId/ownerRefId는 필수이되 UUID v4 또는 null. connectionErasedAt는 optional date | 거래 unique 키, 금액, 상품 snapshot, 상태, purchaseId, version, 보존일 |
| subscription_entitlements | accountId는 필수 UUID v4 또는 null | purchaseId·기간·sequence·원 schedule·revokedAt·version |
| payment_ledger | 이번 단계 schema/내용 불변 | 지급·반환 사실과 멱등 effectKey |
| payment_accounts / purchase_account_refs / payment_timelines | 이번 구매별 제거에서는 불변 | 현재 계정의 다른 구매와 신규 구매 처리 |

Purchase의 정상 상태는 accountId/ownerRefId 모두 UUID이며 connectionErasedAt 없음이다. 제거 상태는 두 필드 모두 null이고 connectionErasedAt=date다. 한쪽만 null, null인데 완료 시각 없음, 정상 연결인데 제거 시각 있음은 validator로 거절한다. 기간권의 null은 같은 구매 제거 Transaction에서만 쓴다. 소급 fake owner를 채우거나 다시 연결하지 않는다.

기존 `ux_entitlement_sequence(accountId, sequence)`는 여러 계정의 null·동일 sequence가 충돌한다. 제안 새 index는 `ux_entitlement_linked_sequence`, key 순서(accountId ASC, sequence ASC), unique=true, partialFilterExpression={accountId:{$type:"string"}}, TTL/sparse/hidden 없음·simple collation이다. 기존 purchase unique/index와 entitlement purchase unique는 유지한다. 기존 sequence index를 런타임에서 몰래 교체하지 않는다.

### 5.2 reader·writer 변경점

- PurchaseCore: 거래 키로 기존 구매를 찾은 뒤 연결 제거 여부를 **owner 충돌 판정 전에** 처리한다. 동일 정상 구매 재관측은 재지급·새 기간권·참조 재생성·사용자 재연결 없이 완료 처리한다. 불변 금전/상품 사실 충돌이나 반환 신호는 별도 검토로 보낸다. owner 해석 실패 때문에 과거 거래를 새 구매로 만드는 우회는 금지한다.
- 공개 sync: 제거된 구매를 현재 사용자 구매로 APPLIED 표시하거나 거래 존재를 상세 노출하지 않는다. 새 동기화 요청을 정상 지급으로 처리하지 않고 공통 비공개 사유로 terminal 처리하는 exact를 정한다. 접수 counted/pendingEvidence 해소도 같은 Transaction이다.
- PaidEntitlementReader: 현재 사용자 accountId와 종료 전 slot만 읽는 구조를 유지한다. 만료된 null 연결 구매는 기존 사용자 목록에 합치지 않는다. 유효 slot에 null 구매가 연결된 비정상 상태는 조용히 0으로 바꾸지 않는다.
- PurchaseReferenceStore/정기조회: 제거된 거래에 암호화 참조를 다시 만들지 않는다. 계정 정기 목록에 과거 거래가 다시 나타나도 지급/기간 갱신을 하지 않는다.
- 운영 CLI/205 정리: null 구매를 미구매 증거로 사용하지 않는다. 205의 eligibility를 넓히거나 기존 구매 계정을 자동 삭제하지 않는다.

### 5.3 구매별 제거 Transaction

1. 현재 환경의 보존 만료·연결 존재 구매를 소량 선택한다. 현재 reference 존재 여부에 의존하지 않아 앞 단계에서 참조를 이미 삭제한 구매도 찾는다.
2. 최초 epoch/OPEN barrier와 purchase/version을 재확인한다. 보존일·기간권/반환·미해결 금액/업무·현재 lease를 확인한다.
3. 해당 거래의 inbox/case/sync, sourceObservationKey 역연결, PLAN011 사용 증거 등 남은 연결을 확인한다. 미해결 또는 보존 중이면 삭제하지 않는다. 계정 단위 보수적 보류는 가능하나 다른 구매까지 삭제하지 않는다.
4. 같은 Transaction으로 Purchase 두 연결을 null 처리하고 connectionErasedAt 고정·version 증가, exact 기간권 accountId=null·version 증가, 남은 encrypted reference 삭제를 수행한다.
5. 원장·금전·duration·balance·pending은 이 작업으로 바꾸지 않는다. 같은 구매 재시도는 제거 완료로 수렴한다. 진행 중 예외는 전부 rollback한다.

매시간 최대100 후보, 개별 구매 Transaction, 기본 dry-run/별도 기본OFF flag를 권장한다. 기존 참조 삭제 flag만으로 금융 연결까지 삭제하지 않는다. 오류/보류 행은 다음 순회로 넘겨 한 행이 전체 큐를 막지 않게 한다. 로그/metric은 건수·일반 사유만 남긴다.

### 5.4 안전한 이관 순서

1. nullable 계약/재수신·sync exact 확정 및 reader/writer 호환 코드 작성. 삭제는OFF.
2. writer를 격리하고 ALL_BLOCKED에서 기존 자료·index·validator 검사. 새 partial unique index 생성·검증 후 기존 전체 sequence unique 제거, validator 명시 이관. 새 schema manifest를 verify-only 기동에 반영한다.
3. null 읽기·재수신 처리가 가능한 버전을 전 배포하고 OFF 상태 회귀. 기존 행은 연결을 유지하며 대량 backfill이 필요 없다.
4. 대상 dry-run 및 source/사용 증거 역연결·복원/부하 검증 후 별도 활성화.
5. null 행이 생긴 뒤 구버전 writer/schema로 rollback 금지. 문제 시 삭제만OFF하고 호환 버전을 유지한다. 지운 ID를 임의로 복구하지 않는다. backup 복원 시 트래픽 전 만료 제거를 다시 적용한다.

### 5.5 필수 테스트·완료 기준

- 동일 계정의 오래된 구매/최근 구매 분리, 활성·예정 기간권 보존.
- 둘 이상의 다른 계정 sequence=1 제거 후 unique 충돌 없음.
- expiry 경계·보존일 연장·reversal·금액 불명·case·terminal source/sync 잔존 보호.
- 제거와 신규 구매/환불/lease/epoch 경합, 중간 오류 rollback, 중복 worker 한 번 수렴.
- 제거 후 계정 목록/API/webhook 재관측: 재지급·재연결·참조 재생성 없음.
- 제거 후 sync의 정보 비노출·pending 책임 해소, 유효 slot 불일치 오류 유지.
- schema 정상/반쪽null 거절·구버전 기동 거절·reader-first/복원 회귀.
- PLAN011 이용 증거 역연결 검증과 PLAN012 반환 결과 보존 회귀 전 운영 활성화하지 않음.
- 구현 후 `./gradlew clean test bootJar`; 실Store/실DB 결과와 로컬 fixture 결과 분리 기록.

## 6. 코드 조사 근거

- [PurchaseCoreSchema](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCoreSchema.java): 필수 UUID 연결과 전체 계정/sequence unique.
- [PurchaseCore](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCore.java): 기존 거래 owner 비교, 동일 구매 완료, source pending 해소와 append-only ledger.
- [PaymentSyncService](../../src/main/java/web/tosunsaeng/billing/domain/payment/sync/PaymentSyncService.java): account 소유 비교·기존 구매로 완료 처리.
- [PaidEntitlementReader](../../src/main/java/web/tosunsaeng/billing/domain/payment/query/PaidEntitlementReader.java): 사용자 계정→현재 slot→구매 연결.
- [CleanupPurger](../../src/main/java/web/tosunsaeng/billing/domain/payment/cleanup/CleanupPurger.java): 미구매 계정 정리로 금융 연결 제거에 그대로 재사용하지 않음.
- [참조 정리 r1](../contracts/TMI-212-reference-retention-r1.md): 현재 구현 범위·미완료 항목.
