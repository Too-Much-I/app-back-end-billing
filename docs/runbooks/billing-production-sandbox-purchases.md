# 운영 SANDBOX 구매 통합

## 1. 5줄 결론

1. 사용자 승인: 운영에서 테스트·실제 구매를 같은 계정/이용권에 합산한다. 별도 계정 제한은 없다.
2. `BILLING_PAYMENT_SANDBOX_PURCHASES_ENABLED` 기본 false, PRODUCTION에서만 true를 허용한다.
3. 계정·timeline·workload 환경은 PRODUCTION 유지. observation은 실제 Store 환경, 구매의 `storeEnvironment`는 SANDBOX를 보존한다. 필드가 없는 기존 구매는 `environment`가 원 Store 환경이다.
4. 거래/event key는 실제 Store 환경을 포함한다. 같은 거래 문자열의 실제·테스트 구매가 서로 덮어쓰거나 환불되지 않는다.
5. 로컬 구현이며 운영 스키마 이관·설정·배포·RevenueCat 변경·실제 Store E2E는 별도다.

## 2. 사용자가 반드시 읽어야 하는 내용

사용자 API·요청 body·JWT·purchaseAccountRefId는 바꾸지 않는다. 실제 3일+테스트 1일 구매는 기존 순서/기간 규칙으로 합산한다. 테스트 구매로 운영 시험과 채점 비용이 발생하는 위험은 사용자가 수용했다. SANDBOX는 클라이언트 주장이 아니라 인증된 RevenueCat 응답에서만 판정한다. 미등록 app/product·다른 owner·서명 오류는 계속 거절한다.

기존 account/Member 증거·조회·유료 시험 예약·탈퇴·삭제 로직은 운영 scope를 그대로 사용한다. 거래 환경을 전달하기 위한 LC/Identity wire 변경은 없다. 실제 Store 환불 실행은 추가하지 않는다. 기존 환불 증거 검증 및 불명확 결과의 review 처리도 유지한다.

## 3. 활성화 전 명시 이관

이 문서는 실행 승인이 아니다. 다음은 배포 담당자가 수행할 절차다.

1. 배포 전 기존 account lookup cursor가 진행 중이지 않은지 확인하고 drain한다. mixed 모드의 내부 cursor는 `P:`/`S:` 접두사로 각 Store 페이지를 구별한다. 구 cursor를 임의 변환하지 않는다.
2. writer 중지와 ALL_BLOCKED를 확인한다. 기존 자료·정확한 r7 schema를 먼저 대조한다.
3. `MixedStorePurchaseSchema.upgradeUnderMaintenance(db, true)`로 purchases의 optional `storeEnvironment`만 추가한다. r7/r8 exact schema만 허용하고 기존 자료에 새 validator를 대조한다. 인덱스·기존 environment·원장·digest는 변경하지 않는다.
4. `PaymentSyncSchema.upgradeMixedUnderMaintenance(db, true)`로 optional `sandboxTransactionKey`를 추가한다. 기존 row backfill·TTL/index 변경은 없다. 두 이관은 재실행 가능하며 완료 전 writer를 재개하지 않는다.
5. 새 코드·키·catalog를 준비한 뒤 flag를 켠다. ON 기동에는 두 새 schema가 필수다. RevenueCat 운영 webhook을 실제 app의 두 환경을 받도록 별도로 설정한다. TEST Store 앱을 임의 등록하거나 인증을 완화하지 않는다.
6. 기존 정책에 따라 barrier 복원·배포하고 SANDBOX 구매→sync→조회→시험 생성 및 실제 구매 회귀를 검증한다. 이번 작업에서는 실행하지 않았다.

기존 PENDING sync가 구 transactionKey만 가진 경우 원래 같은 요청/key 재호출 시 미귀속 command에 SANDBOX 후보를 보완한다. raw 거래 ID를 저장하지 않으므로 이미 저장된 command만으로 후보를 발명하지 않는다. 기존 APPLIED/bound command는 바꾸지 않는다.

## 4. 주요 위험과 미확인 사항

- 같은 Store 거래 문자열이 양 환경에서 실제 발견되면 hint만으로 어느 구매인지 추측하지 않고 기존 `409 PURCHASE_BINDING_CONFLICT`로 보류한다. 두 검증된 구매의 원장·권리는 별도로 보존한다.
- 초기 production API 조회가 비어 있어도 sandbox 페이지를 확인하기 전에는 전체 조회 완료로 취급하지 않는다. 자동삭제 부재 확인도 같은 client를 사용하며 두 환경 모두 정상 조회돼야 한다. 기존 5초/페이지 한도를 늘리지 않아 추가 호출로 조회 보류가 늘 수 있다.
- mixed 페이지 cursor는 기존 암호화 저장의 255자 한도 안에서만 허용한다. 접두사를 포함해 초과하면 계약 오류로 보류하며 자르지 않는다. 재시도/페이지 상한은 그대로다.
- API/Webhook/실제 Store 환경별 자료의 운영 E2E는 미실행이다. 기존 refund adapter의 review 정책을 이번 작업에서 넓히지 않는다.
- 새 mixed 자료 생성 후 구버전 rollback을 하지 않는다. flag OFF만으로 기존 SANDBOX 이용권을 회수하지 않는다. 미처리 sandbox 증거를 끝내기 전에 OFF로 내리면 해당 작업이 보류될 수 있으므로 drain/명시 운영 대응이 필요하다.
- 테스트 purchase를 실제 매출 집계에 포함하지 않도록 `storeEnvironment ?? environment` 기준을 사용한다. 새 관리 화면/매출 집계 기능은 추가하지 않았다.

## 5. 구현 범위

- provider decoder: 선택적으로 두 환경을 수용, 원 환경을 observation 및 HMAC에 유지.
- PurchaseCore: 계정 scope 안에 저장·공통 timeline 지급, 구매별 Store 환경 검사.
- sync: 원 거래 hint에서 두 HMAC 후보 생성, 기존 request digest 불변, 실제 검증된 source만 귀속.
- worker/reference: 실제 환경 observation을 운영 계정에 안전하게 연결. 한 번의 tick에 한 페이지, production 후 sandbox 순차 조회로 기존 bounded 처리 재사용.
- schema: purchases r8, sync optional 후보 필드의 명시 이관. 기본 OFF.

## 6. 검증

2026-10-18 최종 `./gradlew clean test`: **1849/통과1849/실패0/오류0/skip0**, `git diff --check` 통과. 신규 검증은 혼합 구매 합산·같은 ID 분리·테스트 환불의 실제 구매 보호·source worker sync 완료·REST 양 환경 순회·cleanup 양쪽조회 실패보류·동일요청 재개·운영 시험 예약/confirm이다. 기존 인증 회귀도 전체 테스트에 포함된다. 실제 운영 서버나 Store를 테스트에서 호출하지 않았다.

혼합 환불 회귀에서 기존 `PaidEntitlementReader`가 취소된 과거 slot의 종료시각까지 현재 timelineEnd와 비교하는 오류를 발견했다. 환불로 timeline이 짧아져도 과거 slot은 audit 목적으로 남으므로, 현재 종료시각 비교는 유효 slot에만 적용하도록 수정했다. 취소·원장 기록 삭제나 실제 유효 slot의 기간 검증 완화는 아니다. 동일 환경 환불 회귀도 추가했다.

### 변경 파일 그룹

- 설정/정책: `PaymentProperties`, `StoreEnvironmentPolicy`, `application.yml`.
- provider: `RevenueCatPurchaseDecoder`, `RevenueCatWebhookDecoder`, `RevenueCatReadClient`, `PurchaseReferenceStore`, `RevenueCatWebhookConfiguration`.
- 원장/조회: `PurchaseCore`, `RefundCoreSchema`, `MixedStorePurchaseSchema`, `PaidEntitlementReader`.
- sync/worker: `PaymentSyncService`, `PaymentSyncSchema`, `SyncTransactionKeys`, `PaymentAccountLookupWorker`, `PaymentLookupResumer`, `PaymentSourceWorker`, `PaymentProviderWorkerConfiguration`.
- 테스트: `RevenueCatAdapterTest`, `RevenueCatWebhookTest`, `PaymentSyncIntegrationTest`, `PaidReservationIntegrationTest`.
- 문서: ADR-004, 최초 영향 계획 상단의 최신 결정, 이 안내서, CURRENT_STATE/WORKLOG.

기존 변경된 운영 bootstrap 스크립트·이전 운영 준비 문서는 이번 작업에서 수정하지 않았다. LC·Identity 코드/설정·기존 공유 fixture도 변경하지 않았다.

## 7. 2026-10-18 운영 적용 기록 (진행 중)

- 사용자 운영 적용 승인 및 purchases/payment_sync_commands 두 컬렉션 한정 임시 collMod·중지·이관·회수 승인.
- 기존 prod14 COMPLETED/1/0, control schema4 OPEN, 조회 cursor3/중간 cursor0 확인. 이후 desired0과 실제 STOPPED를 모두 확인했다. 서비스 running0만으로 충분하다고 판단하지 않았으며, ALB300초 드레인 종료 전 두 차례 사전검사가 중단돼 DB 변경은 수행하지 않았다.
- 동일 빌드 JAR SHA256 `3e3208882f3e42bafa8bef3801e34ebb85ee0bd78c9cb7fa6e20815ac40fca70`; TAR SHA256 `51014d56a9b8a0848b041e3d1ecc073d04032eba45f3d2d07437473e098f96d3` 양측 일치.
- ECR `tosunsaeng-billing-test` 저장소(기존 운영 이미지 저장소 유지)의 새 digest `sha256:07d576da3a9037918ce92d70970a4a6b409e9b4fb08cddd9550853b3db7dba78`. prod15는 prod14의 설정/Secret/권한/네트워크를 보존하고 이미지와 SANDBOX flag true만 변경했다.
- `bootJar`, 이미지 smoke valid/wrong-db/wrong-cert 성공. 새 제품 코드 수정 없이 이전 전체1849 성공 결과에 추가한 패키징 검증이다.
- writers STOPPED 후 control OPEN142→ALL_BLOCKED143 CAS·epoch 보존, 동일 JAR의 두 명시 migration 실행으로 `MIXED_STORE_SCHEMA_OK migrate` 확인. 원장·기존 자료·인덱스·TTL 삭제/재작성 없음.
- 임시 역할은 `billing-prod-mixed-store-maintenance`, 위 두 컬렉션 collMod만 부여했다. 이관 직후 사용자 역할 연결 회수 저장. 실제 회수 재검증·OPEN 복원·prod15 최종기동·RevenueCat 양환경 저장은 후속 결과로 확정한다.
- CloudShell 공간 확보를 위해 기존 아카이브 한 개를 임시 이동했다가 새 TAR 이동 후 원위치로 복원했다. 삭제 없음. 실제 Store 구매·환불 E2E는 미실행이다.

### 적용 후 확인

- 임시 collMod 없음 재확인, `MIXED_STORE_SCHEMA_OK verify` 성공. control ALL_BLOCKED143→OPEN144 CAS·epoch 보존 후 prod15 desired1 배포.
- prod15 RUNNING/HEALTHY, 앱8083·workload8443 새 target healthy. 기동 로그 오류0·세 connector8082/8083/8443 확인. 기존 ALB healthy threshold5/30초 대기 때문에 초기 unhealthy가 관측됐으며 검사 규칙/네트워크를 완화하지 않았다.
- 운영 앱 HTTPS 무인증 products401 확인. RevenueCat billing-production 환경을 `Both Production and Sandbox`로 저장, HMAC/Authorization·기존 app/event 설정 유지. test webhook·실제 Store 환불 실행·snapshot/feed 설정 변경 없음.
- 구 task가 완전히 종료된 뒤 schema를 변경했다. desired1 배포 과정에서 ECS가 구 정의 task를 잠시 재생성했으나 최종 실행 task는 prod15만 남았으며 구 target은 draining 중이다. 최종 COMPLETED 상태는 아래 최종 기록으로 확정한다.

### 최종 결과 — 적용 완료

- prod15 **단일 PRIMARY/COMPLETED, desired1/running1/pending0, RUNNING/HEALTHY**, 8083/8443 target 모두 healthy 하나씩. 구 task/target 제거 완료. 위 진행 중 표기는 작업 순서 기록이다.
- 실제 배포 정의의 SANDBOX flag true와 위 ECR digest 일치 확인. products 및 webhook 무인증 HTTPS401, 인증 약화 없음.
- RevenueCat Both Production and Sandbox 저장 완료. 임시 역할은 사용자에서 분리됐으며 권한 회수를 실제 DB 연결로 확인했다. 역할 정의 자체는 미연결 상태로 보존했다.
- 실제 Store SANDBOX/PRODUCTION 구매 및 환불 E2E는 미실행. 프론트에서 정상 로그인·purchase-account·SDK 구매·sync·이용권·시험 생성 순서로 확인한다. 과거 PENDING sync가 있다면 원래 동일 요청/key로 재호출한다.
