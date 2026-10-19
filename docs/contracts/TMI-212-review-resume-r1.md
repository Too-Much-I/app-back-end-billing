# TMI-212 D2 — 원장 기반 완료·선택적 조회 재개

## 1. 결론

2026-10-14 후속: [독립 운영 CLI](../runbooks/TMI-212-payment-review-operator.md)를 구현한다. 아래의 호출 도구 미구현 표현은 CLI inspect/dry-run/apply/result·DB principal digest·동일Transaction receipt 범위에서 대체한다. 개인별 실제권한/운영 배포·감사 보존기간 확정은 여전히 후속이다. 별도 additive receipt-r1을 명시 설치하며 기존 sync/cursor schema는 변경하지 않는다.

- inbox TTL 뒤에도 exact 거래·계정의 VERIFIED/ALLOWED Purchase와 entitlement/지급 ledger가 있으면 PENDING sync를 완료할 수 있다.
- 이 경로는 지급하지 않는다. 미확정/다른 소유자/검토 case가 있는 거래는 완료하지 않는다.
- 조회 오류로 인계된 unbound sync만 운영 점검 후 최대100개씩 명시 재개한다. core의 구매 검토 case 해결은 별도다.
- sync-command-r2는 선택 date `retryStartedAt`만 추가한다. 기존 행은 createdAt을 사용한다.
- 공개 API·판매·외부 알림·자동 삭제 활성화와 무관하며 운영 HTTP endpoint를 추가하지 않는다.

## 2. 저장·이관

기존 sync field/index/상태를 유지하고 retryStartedAt만 선택 필드로 허용한다. 모든 writer 격리 및 ALL_BLOCKED에서 정확한 r1 또는 r2 validator/index와 기존 행 적합성을 확인한 뒤 명시 collMod한다. startup은 verify-only, 구버전 자동 rollback은 금지한다. 재개 때 createdAt은 보존, retryStartedAt/updatedAt=현재 시각, attempts(재개 횟수)·version 증가. bind/complete는 retryStartedAt을 갱신하지 않는다. 자동 재시도 예산은 retryStartedAt(없으면 createdAt)+24시간이다.

## 3. 원장 기반 완료

PENDING/count=true인 exact command의 환경/store/app/transaction HMAC/keyVersion/account를 구매와 대조한다. 계정/lifecycle/user 연결, VERIFIED·ALLOWED·READY, entitlement와 PURCHASE_VERIFIED ledger를 확인하고 해당 거래의 미해결 case가 없을 때만 command를 APPLIED/count=false로 바꾸고 pending을1감소한다. 동일 Transaction의 epoch/호출자 lease guard를 유지한다. 기존 source 키가 있더라도 source 삭제만으로 실패시키지 않는다. 이미 APPLIED이면 중복 완료로 수렴한다. 금액·기간·원장은 변경하지 않는다.

## 4. 운영 재개

명시적인 maintenance 라이브러리 호출만 제공한다. 운영자 인증·권한 검증은 호출 도구의 책임이며 앱/스케줄러 bean으로 노출하지 않는다. 입력은 격리 확인, accountId, expected cursor version/generation, 동일 LookupFailure 사유, 선택 commandId→expectedVersion(최대100)이다. command가 없는 정기조회 보류는 현재 cursor.failure가 exact 사유와 같을 때만 빈 목록으로 계정 조회를 재개한다. 실제 사용 전 operator와 도구 권한 확정이 필요하다.

모든 대상은 같은 환경/계정/lifecycle, REVIEW_REQUIRED 또는 BLOCKED_AUTH, counted=true, 해당 errorCode, sourceObservationKey=null이어야 한다. 거래 case가 있으면 거절한다. 활성 account lease가 있거나 기존 PENDING 요청이 있으면 먼저 drain하고 재시도한다. 계정이 WITHDRAWN이어도 금융 확인의 재개는 허용하지만 지급 정책은 core가 계속 거절한다.

한 Transaction에서 대상 전부 검증/변경, lifecycle version touch(pending 불변), cursor generation/leaseVersion/version 증가, lease/page 초기화, due=now로 갱신한다. overflow·stale version·하나라도 다른 대상이면 전부 rollback한다. 선택하지 않은 검토 요청은 그대로다. 동일 재개 요청 반복은 stale CAS로 거절한다. 예전 worker는 generation/leaseVersion/owner로 차단한다.

project AUTH 해제는 기존 공용 budget maintenance 절차로 별도 수행한다. 계정 재개는 project AUTH를 자동 해제하지 않는다. 원인을 고치지 않고 재개하면 동일 검증에서 다시 차단된다. 재개는 증거 확정/권리 지급/환불/계정 삭제 승인이 아니다.

## 5. 미완료·배포 경계

[정기 조회](TMI-212-reconciliation-r1.md)와 [구매 reference 영속화](TMI-212-purchase-reference-r1.md)는 같은 D2에 연결했다. provider source/case의 운영 해결과 외부 알림 채널 연결은 남아 있다. source 없이 확정 구매로 완료하는 것은 금융 사실을 새로 추론하는 것이 아니라 이미 같은 원장에서 완료된 지급을 확인하는 경로다. source에서 이전된 미해결 case는 TTL 없이 보존한다. public sync ON 차단은 D/실환경 검증이 닫힐 때까지 유지한다.
