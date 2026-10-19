# 과거 구매 검토 10건 한정 종결

## 1. 결론

- `scripts/ops/resolve_legacy_purchase_reviews.py`는 기본 읽기 전용이며 서버 기능이 아니다.
- 10/18 19:28 KST 이전 소유 미해결5·수량누락2·구 수량미지원3건만 승인 목록으로 처리한다.
- 수신 원본을 재전달했다고 가장하거나 reviewCandidate/normalizedObservation을 만들어 넣지 않는다.
- 검증 구매와의 연결을 확인한 운영자 종결이며 구매·이용권·원장을 변경하지 않는다.
- 로컬15테스트 통과. 2026-10-19 실제 manifest/dry-run과 실Mongo 쓰기 rollback·10건 종결·멱등 재실행 검증 완료(§7).

## 2. 변경 범위와 중요한 제한

기존 case의 수신 해시·reason·state·생성시각을 유지한다. 기존 선택 필드인 reviewState=RESOLVED,
resolvedAt, purchaseId, resolvedObservationKey, actorDigest를 기록하고 counted=false 및 version 증가만 수행한다.
counted였던 건만 같은 트랜잭션에서 lifecycle.pendingEvidence를 감소한다. 삭제/재지급/기간 변경은 없다.
새 schema, 일반 자동허용 조건, API, 정기 worker는 추가하지 않는다.

현재 dashboard 원본과 저장 receptionDigest는 동일원문 증명이 안 됐다. manifest의 receptionDigest는
운영 DB snapshot을 실행 직전 재검사하기 위한 값이며 dashboard 원본 해시와 같다는 주장이 아니다.
dashboardEvidenceDigest는 독립 대조 근거의 식별값으로 승인 manifest에 보존하되 제품 case에 복제하지 않는다.
실제 원본 대조 없이 DB에서 fingerprint만 생성한 목록으로 종결하지 않는다.

## 3. 실행 자료와 승인

manifest 최상위 필드: purpose=`legacy-review-2026-10-19`, actorDigest(권한 있는 담당자 식별의 SHA256), entries.
entries는 정확히10개이며 각 항목은 아래 SHA256/HMAC 문자열만 포함한다.

- eventKey: 운영 lookup key로 계산한 원 RevenueCat event 식별자.
- receptionDigest: 기존 case에 저장된 값. 실행 중 변경되면 중단.
- purchaseFingerprint: 독립 검증 구매의 store/app/transactionKey/keyVersion/owner/account/환경/시각/상품 fingerprint.
- sourceObservationKey: 해당 PURCHASE_VERIFIED 원장의 관측 키.
- dashboardEvidenceDigest: 대조한 dashboard 원본의 canonical JSON SHA256. 수신 해시 대체 금지.

원문·비밀키·사용자/거래 ID는 manifest에 넣지 않는다. 승인 manifest와 해시·담당자·대조 결과는 접근 제한된
운영 기록에 보존한다. 해시도 외부 공개하지 않는다. actorDigest는 인증 토큰이 아니며 도구 접근 권한은 별도로 제한한다.

## 4. 운영 적용 순서

1. 대조 결과에서10개 exact 목록을 생성·독립 검토하고 manifest SHA256을 확정한다.
2. pymongo가 준비된 관리 환경에서 기본 dry-run을 실행한다. DB URI는 비표시 입력한다.
3. 운영 적용 승인 후 Billing writer를 중지·drain하고 기존 절차로 ALL_BLOCKED를 설정한다.
4. 같은 manifest로 `--apply --writers-stopped --approved-manifest-sha256 <승인해시>`를 지정한다.
5. majority/snapshot 트랜잭션에서10건을 재검사한다. 모든 전제 통과 후 case·counter만 변경한다.
6. 결과10건과 원장/이용권 불변·counter 차이를 확인하고 기존 복구 절차로 서버를 재개한다.

도구는 서버를 중지하거나 control을 전환하지 않는다. `--writers-stopped`는 운영자 확인이지 실제 프로세스
중지를 탐지하는 기능이 아니다. 변경 중 다른 writer가 없음을 운영 절차로 확인해야 한다.
실행 오류는 비밀정보 누출 방지를 위해 일반 오류만 출력한다. commit 응답 유실 시 실패라고 단정하거나
수동으로 카운터를 되돌리지 말고 같은 manifest로 dry-run/재실행한다. 동일 감사 종결은 NOOP다.

## 5. 검증 범위

`python3 -m unittest discover -s scripts/ops -p 'test_resolve_legacy_purchase_reviews.py'`

15개 로컬 검증: 거래 fingerprint 각 필드·수신 해시·cutoff·환불/정규화case·탈퇴/lifecycle·원장·counter·
동일 감사 재실행·10개 유일 목록·dry-run 무변경·batch 재실행·마지막 건 실패·OPEN 적용 거절.
batch는 메모리 저장소 대역 테스트이며 실제 Mongo rollback/unknown-commit 테스트가 아니다.
초기 구현 단계에서는 운영 적용과 실Mongo 검증을 수행하지 않았으며 후속 적용은 §7에 기록한다. 앱 Java 코드는 변경하지 않아 Gradle 전체 테스트는 미실행.

## 7. 승인된 운영 종결 결과 (2026-10-19)

- Billing 서비스만 desired0 전환 후 기존 task STOPPED를 확인했다. 기존 연결 drain 동안에는 DB 종결을 실행하지 않았다.
- 같은 manifest 해시 및 10건 전제를 재검사하고 단일 PRODUCTION control을 버전 CAS로 OPEN→ALL_BLOCKED 전환했다.
- snapshot/majority 실제 쓰기 트랜잭션을 abort하여 10건 미해결 유지 확인 후, 같은 도구로 10건을 한 트랜잭션에서 RESOLVED 처리했다.
- 같은 manifest 재적용 결과 resolved=0. 원 case state/reason/receptionDigest 불변, counted=false 및 lifecycle pendingEvidence 감소를 검증했다.
- purchases/subscription_entitlements/payment_ledger 전체 건수·digest 불변. 신규 지급·기간 변경·원장 수정·자료 삭제 없음.
- 접근 제한된 기존 CloudShell 폴더에 apply-baseline.json/apply-result.json 보존. 원문이나 비밀값은 출력하지 않았다.
- control OPEN 복원 후 동일 prod17 desired1로 재시작했다. 최종 desired1/running1/pending0·COMPLETED, ALB8083/8443 healthy 및 앱 HTTPS 무인증401을 확인했다.
- 자동 해결 신규 E2E나 webhook 원본 replay가 아니라, 독립 검증 구매에 연결한 승인된 과거 10건 운영자 종결이다.

## 6. 운영 읽기 점검 결과 (2026-10-19)

- RevenueCat 원본10건을 재확보해 승인된 비표시 입력으로 전달. 원문/비밀값은 파일이나 로그에 저장하지 않음.
- 원 event HMAC→case와 거래 HMAC→검증구매, owner/상품/구매시각/환경/계정을 재대조하여 manifest 생성.
- 실제 도구 `run(..., apply=False)`를 운영 Mongo snapshot 트랜잭션에서 실행: `matched=10`, `wouldResolve=10`.
- DB쓰기0. ECS prod17/running1 유지. 서비스 중지·control변경·case종결·배포 없음.
- CloudShell 산출물: `/home/cloudshell-user/billing-review-closure-20261019-cal5zt4e` 아래 manifest.json,
  manifest.sha256, dry-run.json. 생성 시 디렉터리0700/파일0600. manifest 원문/해시는 채팅·저장소에 미기록.
- actorDigest는 실행 AWS principal ARN의 SHA256으로 설정. 사람의 승인 사실은 이 채팅과 운영 기록에 별도로 남김.
- 이번은 실Mongo 읽기Tx 검증이며 쓰기 rollback/unknown commit 검증이나 실제 종결 완료가 아니다.
