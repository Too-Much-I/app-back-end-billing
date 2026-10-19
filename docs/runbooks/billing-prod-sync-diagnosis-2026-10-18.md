# 운영 sync 읽기 진단 — 2026-10-18

## 1. 결론

- 사용자 운영 결제 직후 sync/조회 이상 보고에 따른 읽기 점검. 제품 코드·DB·설정 변경 없음.
- 17:07:24 KST APP_STORE sync는 PENDING·source 미귀속. SANDBOX 거래 후보는 저장됨.
- 같은 계정의 16:39:50 구매는 VERIFIED/ALLOWED/READY이며 1일 entitlement가 존재한다. 최신 sync 거래 후보와는 다른 거래다. 최신 구매가 이미 지급됐다고 판단하지 않는다.
- 해당 계정 QUANTITY_UNSUPPORTED review1건 때문에 PaidEntitlementReader가 유효 기존 권리까지 PENDING으로 반환하는 코드 경로가 확인됐다. 이는 전체 환경 차단이 아닌 해당 계정 차단이다.
- 이전 iOS sync는 검증된 구매와 거래가 일치해도 PENDING이다. 구 command 재호출은 SANDBOX 후보 보완 후 기존 결과만 반환하며 settled 구매 재대조를 수행하지 않는다.

## 2. 추가 관측과 영향

- 최신 iOS 계정 lookup attempts9, nextAttemptAt 17:33:11 KST. 과거 반복 대기의 재조회 지연이 남아 있다. 최신 거래 확인을 위해 새 결제를 유도하면 안 된다.
- 비교용 17:04 Android sync는 APPLIED이며 SANDBOX 구매/1일 entitlement 확인. 다만 Android에도 QUANTITY_UNSUPPORTED review 관측이 있어 sync 성공과 조회 정상은 구별해야 한다.
- quantity 보류는 webhook decoder가 null 또는 1 이외를 거절하는 경로와 일치한다. 실제 이벤트의 필드 누락/값 자체는 이번에 원문 조회하지 않아 확정하지 않는다. 보류 사실을 수량1로 추정해 강제 지급하지 않는다.

## 3. 보완 방향 (미구현)

- 지원되는 webhook의 불완전 구매 정보는 검증된 REST 조회로 수렴시키되, 신규 구매 확인 미완료가 기존 확정 권리까지 막는 범위를 검토한다. 환불·소유 충돌 등 실제 차단 근거는 구별한다.
- 같은 key의 기존 PENDING도 이미 검증된 동일 거래·계정·원장을 확인해 APPLIED로 수렴하도록 보완한다. 멱등성/귀속/환경 구분은 유지한다.
- 새 구매 sync의 처리 기회를 과거 미해결 요청 backoff와 구분하되, rate limit/Retry-After 하한을 임의 무시하지 않는다.
- 코드 수정·회귀·배포 및 필요한 건의 검증 기반 복구는 별도 후속. case 삭제·상태 강제 APPLIED·추가 지급을 하지 않았다.

## 4. 근거·검증

운영 payment_sync_commands/purchases/subscription_entitlements/payment_purchase_cases/payment_provider_inbox/payment_reconcile_cursors의 상태·시각·매칭 여부만 출력했다. 실제 사용자/거래 ID·Secret·원문 payload는 기록하지 않았다. 코드 근거는 PaymentSyncService.sync, RevenueCatWebhookDecoder, PaidEntitlementReader다. Gradle 미실행(읽기 진단만), 기존 변경 보존.

## 5. 후속 로컬 수정 (운영 미적용)

- 웹훅 quantity 누락/null은 `QUANTITY_REQUIRES_LOOKUP`, 명시적인 1 이외 정수는 기존 `QUANTITY_UNSUPPORTED`로 구분한다. 누락을 1로 간주하지 않는다. 두 경우 모두 직접 지급하지 않고 기존 sync/주기 재조회 경로의 엄격한 REST 검증을 유지한다. 새로운 즉시 조회 worker는 추가하지 않았다.
- normalizedObservation이 없는 위 두 수량 case만 기존 **검증된 ACTIVE 권리**를 가리지 않도록 한다. 권리가 없으면 계속 PENDING이다. 환불·소유권 등 다른 case 및 수신 중 환불 차단은 유지한다. 과거 QUANTITY_UNSUPPORTED는 실제 값/누락을 알 수 없어 검토 기록과 pendingEvidence를 보존한다.
- 같은 key의 unbound PENDING 재요청은 기존 worker의 검증된 구매 완료 로직을 같은 Transaction 안에서 재사용한다. 거래·계정·환경·READY·원장·권리·관련 검토 근거를 검사해 명령만 APPLIED로 수렴하고 pendingEvidence를 한 번 감소시킨다. 새 지급/기간 연장 없음, bound/review 명령 자동 재개 없음.
- public API 요청/응답 형식·인증·환불/멱등성 계약 불변. 이번 변경에 DB schema 이관·설정 추가·새 의존성은 없다.
- 새 sync가 기존 계정 backoff를 상속하는 지연은 **이번 수정에서 변경하지 않았다**. 검증된 구매의 재요청은 HTTP 조회 없이 수렴하지만, 아직 미검증인 거래는 기존 재조회 일정·Retry-After·호출량 제한을 따른다.
- 운영 배포/기존 case 일괄 종결/데이터 수정/commit/push는 하지 않았다. 배포 후 같은 key 재요청과 최신 거래의 실제 검증 결과를 확인해야 한다. 최신 결제 지급 성공으로 보고하지 않는다.
- 검증: `./gradlew clean test` **1,858건 통과, 실패/오류/skip 0**, diff 검사 통과. 누락/복수 수량, 기존권리 보존/환불 차단, REST 검증 수렴, 동시 replay의 중복 지급·pending 중복 감소 방지 회귀 포함. 실제 Store E2E는 아니다.

## 6. 후속 운영 배포 — 진행 중

- 사용자 「어 배포해줘」 승인으로 Billing 운영만 이미지 교체. 사전 prod15 COMPLETED/desired1/running1/pending0 확인. 기존 ECS rolling 설정(최소100%/최대200%·circuit breaker/rollback)을 유지했다.
- bootJar 성공 및 이미지 valid/wrong-db/wrong-cert smoke3 통과. 제품 코드는 위 전체1858 통과 이후 변경 없음. JAR SHA256 `2a16aaa1ce3c118f0b63d7d3dd25281738025019ecf8ac6f0e58bcba2511d3ab`.
- TAR 양측 SHA256 `6706e0936ed7e72bba437aaa26e5171ee7ec0f1e318dff6f6af0cdc28e655be5` 일치. ECR image digest `sha256:64c52e072d5f9c3efa614452fdc6430f47233232215835e1dd4d8f444101636a`.
- prod16 등록 및 서비스 배포 요청. prod15 대비 컨테이너 설정은 image 외 동일함을 코드로 비교했다. Secret·기능 flag·네트워크·DB/schema/control·LC/Identity 변경 없음. 기존 수량 case나 sync를 직접 수정하지 않았다.
- CloudShell 업로드 공간 확보를 위해 기존 아카이브1개를 임시 이동했다가 원위치 복원, 파일 삭제 없음. 새 아카이브는 `/tmp/billing-sync-fix-20261018.tar`에 보존했다.
- 최종 task/ALB/HTTPS 확인은 아래 후속 결과에 기록한다. 배포 요청 수락은 완료가 아니다.

### 운영 적용 완료

- prod16 단일 PRIMARY/COMPLETED, desired1/running1/pending0, RUNNING/HEALTHY. 실행 image digest가 위 ECR digest와 일치한다. 기동 로그 표본38건 중 Started 확인·ERROR0.
- 앱8083·workload8443 새 대상 healthy. 기존 대상은 draining으로 관측돼 ECS 배포 완료와 대상 deregistration 종료 시점은 구분한다. 최초 자동 검증은 이 잔여 draining까지 없어야 한다는 조건에서 중단됐으며, 재대조 결과 새 서버·이미지·기동은 정상이다.
- 운영 HTTPS products/entitlements GET 및 sync POST 무인증401 확인. 사용자 JWT를 만들거나 구매/sync 자료를 직접 변경하지 않았다. 실제 사용자 로그인 기반 APPLIED/이용권 조회 검증은 프론트 후속이다.
- 후속: 기존 결제는 같은 transactionId·같은 Idempotency-Key로 sync 재요청하고 이용권 조회를 확인한다. 추가 결제를 유도하지 않는다. 아직 검증되지 않은 거래의 기존 backoff는 유지된다. commit/push는 하지 않았다.

## 7. SANDBOX 실제 반영 후속 읽기 확인

- 사용자 수정 후 정상 반영 확인 및 SANDBOX/결제 상태 점검 요청. 운영 prod16 COMPLETED·running1/pending0, 8083/8443 각각 healthy1로 구 draining 제거까지 확인했다.
- 실제 task definition의 PAYMENT_ENVIRONMENT=PRODUCTION, SANDBOX_PURCHASES_ENABLED/SYNC_ENABLED/PURCHASE_ACCOUNT_ENABLED=true. control schema4/OPEN. 별도 Billing 테스트 계정 선택이 아니라 검증된 Store SANDBOX 거래를 운영 계정에 합산하는 기존 승인 방식이다.
- 최근 sync6건(APP_STORE3·PLAY_STORE3) 모두 APPLIED, 각 명령의 purchaseId·거래·앱·계정이 SANDBOX VERIFIED/ALLOWED/READY 구매와 일치. 각 구매 entitlement1건·PURCHASE_VERIFIED 원장1건 확인. 기존17:07 iOS 건도17:33에 APPLIED로 수렴했다. 새17:35 Android sync는 약5초 뒤 APPLIED로 관측했다.
- 관련3계정 모두 현재 active1·pendingSync0. 추가 기간은 각각 scheduled1/2/0, 기간 겹침0·공백0. 따라서 개별 후속 slot의 active0은 미지급이 아닌 정상 순차 예약이다.
- 기존 수량 검토 기록은 남아 있으며 자동종결하지 않았다. 원문 수량/누락은 이번에도 조회하지 않았고, 검토 기록 보존과 구매 적용 완료를 구분한다.
- 이번은 읽기 점검. 실제 새 결제·사용자 API 로그인 호출·시험 생성·실결제(PRODUCTION 거래)/환불 E2E는 수행하지 않았다. 제품·운영 설정·DB·배포·commit/push 변경 없음. CloudShell 재시작으로 사라진 임시 pymongo 조회 도구만 /tmp에 다시 준비했다. 문서 diff 검사 통과, Gradle 재실행 없음.
