# TMI-212 C2a 외부 조회 기반 구현 안내

2026-10-13. TMI-212 전체 또는 C2 전체 완료가 아니다.

## 구현한 부분

- [RevenueCatBudgetSchema](../../src/main/java/web/tosunsaeng/billing/domain/payment/provider/RevenueCatBudgetSchema.java): 공용 budget collection r1의 명시 maintenance 생성·exact validator/index 검증. 운영 자동 DDL 없음.
- [RevenueCatProjectBudget](../../src/main/java/web/tosunsaeng/billing/domain/payment/provider/RevenueCatProjectBudget.java): project별 최근60초240회/유효 slot4, 트랜잭션 CAS, 만료30초, 실패 호출도 count 유지, AUTH 영속 차단과 maintenance version CAS 재개.
- [RevenueCatBudgetedTransport](../../src/main/java/web/tosunsaeng/billing/domain/payment/provider/RevenueCatBudgetedTransport.java): 동일 project/HTTPS 경로 검증, permit 전후 확인, HTTP401/403 차단 저장, finally slot 반환. 일반 provider readclient 앞에 명시적으로 조합한다. runtime에 자동 등록하지 않는다.
- [PurchaseCore](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCore.java): 외부 관측 접수의 최초 ticket/동일 Transaction lease guard overload. 중복 관측이라도 만료 lease/과거 epoch는 거절한다.

## 배포 전 조건

[exact 규격](../contracts/TMI-212-provider-dispatch-r1.md)을 따른다. 같은 RC project를 호출하는 모든 환경/인스턴스가 **동일 공용 Mongo DB/collection**을 사용해야 한다. 로컬 테스트의 두 인스턴스 공유 성공은 실제 staging/production 연결을 검증한 것이 아니다. 호출 제한용 DB에는 개인·거래·credential을 저장하지 않는다. 새 네트워크/credential/실제 DB는 만들지 않았다.

호출자 전체 격리 후 `RevenueCatBudgetSchema.installUnderMaintenance(sharedDb, true)`로 설치한다. flag나 readiness=true로 이 검사를 우회하지 않는다. 서로 다른 DB에 같은 collection 이름을 만드는 것은 공유 제한이 아니다. 스키마 불일치는 자동 수정하지 않는다. runtime은 기존 sync ON 거절/OFF 유지다.

AUTH 차단은 credential 수정 후 전체 caller 격리, live slot 소멸, 현재 row version 확인 뒤 `resumeUnderMaintenance(expectedVersion, true)`로만 해제한다. 자동 재개/시도 초기화/예산 초기화 없음. 요청당 외부 timeout5초와 slot30초를 유지하며 긴 프로세스 정지 때 물리적인 외부 작업 종료까지 보장하지 않는다. 만료된 응답은 폐기한다.

## 남은 구현

계정 단위 durable lease·due scan/요청 합치기, 페이지별 관측 접수/다음 cursor commit, sync source 연결·legacy terminal 회수, Retry-After/backoff·24시간 review, 공용 budget DB 설정 및 runtime 기동 검증/자동 scheduling은 다음 C2 작업이다. 별도 D의 정기 대사/reference 암호화 영속화/보존·알림도 미완료. 자동삭제/판매/Store 검증은 활성화하지 않았다.

## 테스트

`./gradlew clean test bootJar`: **1,356 통과 / 실패 0 / 오류 0 / skip 0**, C1 대비 18개 추가. 실제 RC/Store/운영 DB 호출 없이 로컬 replica-set으로 실행했다. `git diff --check` 통과.

로컬 replica-set에서 두 인스턴스/환경의 공용 slot·240회 경계·동시 acquire·30초 만료와 늦은 응답·실패 시 slot 반환/요청 수 보존·401/403 영속 차단·명시 재개·DB 장애의 HTTP 차단·시계 역행·BSON millisecond 변환·schema drift를 검증한다. 금융 테스트는 guard rollback·stale epoch/lease·duplicate 접수 차단·ALL_BLOCKED/PURCHASE_BLOCKED를 포함한다. 최종 전체 실행 결과는 WORKLOG/CURRENT_STATE에 기록한다.
