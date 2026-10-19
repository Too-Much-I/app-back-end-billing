# TMI-212 D2 운영 준비

## 1. 5줄 결론

- 정기조회·선택적 lookup 재개·구매 원장으로 오래된 sync 완료·암호화 재조회 참조를 연결했다.
- sync-r2/cursor-r2/구매 reference collection을 명시 설치·이관해야 한다. runtime 자동 DDL은 없다.
- worker/reconciliation/alerts/terminal-sync-delete/public sync는 기본OFF다. 공개 sync의 무조건ON거절은 [실제 pipeline 연결·준비 검사](TMI-212-public-sync-wiring.md)로 대체했다. 실환경/판매승인은 별도다.
- 운영 요약과 Incoming Webhook Slack adapter는 구현됐으며 기본OFF다. 실제 채널/담당자/권한 연결은 아직 없다.
- 실제 Store·공유 budget topology·대량 부하·운영 복원 검증을 완료했다고 보지 않는다.

## 2. 구현 근거

- [정기조회](../contracts/TMI-212-reconciliation-r1.md): ACCOUNT_LOOKUP 재사용, 한 claim 한 페이지,24시간/1000페이지,활성6h/최근1d/pending5m.
- [재개/완료](../contracts/TMI-212-review-resume-r1.md): 선택 요청 최대100,원장 확인,최초epoch·generation·version,별도 retryStartedAt.
- [암호화 참조](../contracts/TMI-212-purchase-reference-r1.md): purchaseId별 AES-GCM envelope·exact AAD·검증된 구매 연결.
- [D1 보존](../contracts/TMI-212-retention-r1.md):119일 접수case인계·완료sync120일 명시정리.

## 3. 이관·기동

1. writer격리, ALL_BLOCKED 및 기존 schema/index 확인.
2. 기존 sync-r1은 `PaymentSyncSchema.upgradeResumeUnderMaintenance(db,true)`, 기존 cursor-r1은 `PaymentAccountLookupSchema.upgradeReconciliationUnderMaintenance(db,true)` 실행. 신규 DB는 최신 install 경로 사용.
3. `PurchaseReferenceSchema.installUnderMaintenance(db,true)`로 전용 collection 생성. 공유 budget은 기존 별도DB 설치/검증 절차 유지.
4. 새 writer를 배포하되 기본OFF로 유지. 과거 worker/purger 자동 rollback 금지.
5. 실환경 검증·활성화 승인 뒤에만 flag 변경. 이번 작업은 실제 이관·배포 절차를 실행하지 않았다.

## 4. 명시적 운영 재개

2026-10-14: 실행은 [Payment Review Operator CLI](TMI-212-payment-review-operator.md)를 따른다. 별도 인증 DB principal·읽기전용사전검사·선택재개·원자receipt·응답유실결과조회를 추가했다. 아래 도구 연결 필요는 실제 운영 자격증명/최소권한/보존기간/실행환경 구성에 한해 남는다.

HTTP/앱 endpoint를 추가하지 않았다. `PaymentLookupResumer`는 승인된 운영 도구에서만 생성·호출할 library이며 그 도구의 운영자 인증·권한·실행 기록 연동은 활성화 전에 필요하다. 현재 version/generation·선택 command와 사유를 읽고, 기존 pending을 drain하고 활성 lease가 없음을 확인한다. 최대100건 같은계정 요청만 재개하며 일부선택/rollback이 가능하다. 선택하지 않은 review는 유지한다.

AUTH는 공용 project block 해제가 별도다. credential 문제 해결과 모든 project 호출자 격리 뒤 기존 budget maintenance로 먼저 해제한다. 계정 재개만으로 인증 실패가 사라진다고 보지 않는다. core의금전/소유검토case를 이 도구로 강제해결하지 않는다.

## 5. 관측·알림

시간당 DB snapshot으로 pending/review/case 개수, 가장 오래된pending시간,7일 이상review,project AUTH와 성공sample시각을 gauge로 기록한다. source/sync/case 건수는 서로 겹칠 수 있으며 유일 거래 수로 합산하지 않는다. read 실패 시 마지막 gauge를 유지하고 runtime failure counter가 증가하므로 sample시각으로 신선도를 확인한다. 일반 review는 일일요약, AUTH는 장애/복구 요약으로 분리한다. worker/schema/fence 장애는 phase failure metric에서 감시한다.

`billing.payment.provider.worker.alerts-enabled=false`가 기본이다. ON은 명시 `PaymentProviderAlerts.Sink` bean이 없으면 기동실패다. false일 때 sink가 있어도 호출하지 않는다. Incoming Webhook 전송은 [Slack 연결 안내](TMI-212-slack-alerts.md)를 따른다. sink에는 환경·enum·시각·개수·고정runbook키만 전달하고 사용자/계정/거래ID·URL·토큰·본문을 포함하지 않는다. 실패는 전송실패 metric만 남기고 금융작업을 rollback하지 않는다.

일일요약은 한국시간9시 이후 성공한날 1회, AUTH반복/실패재시도는 최소1시간 간격이다. 전송중복방지는 인스턴스 로컬이므로 여러 replica/재시작에서는 중복 알림이 가능하다. 운영 채널은 요약 알림 중복을 허용하거나 기존 공용 전달계층에서 합친다. 새 per-user알림원장·승인 collection은 만들지 않는다.

## 6. 남은 작업과 주의점

- 실제 Slack 채널·담당자·Webhook 비밀 설정 및 운영 재개 도구의 인증/감사 연결.
- 5년 reference physical purge와 금융/erasable 연결 cleanup의 연계, 오래된 cursor lease 정리. 현재 자동삭제로 숨기지 않는다.
- 정기조회는90일최근대상+활성권리 기준이며 사라진 provider자료/90일이전무활동미확인거래를 모두 복구한다고 보장하지 않는다.205 미구매 확인 근거로 재사용하지 않는다.
- 실제 DB explain/부하, budget 공유 topology, Store sandbox/HMAC/SDK거래 매핑, 운영 복원·key교체. fixture 결과는 실환경 증명이 아니다.
- 실제 판매는 PLAN011/012와 별도 승인 이후. 기능 스위치를 준비완료 증명 대신 사용하지 않는다.
