# TMI-220 — 환불 순차 정리·LC 전달 코어

- 날짜: 2026-10-14 / 브랜치: `feat/TMI-220-refund-fanout`
- 이전 보완 commit `6de78ac`, [PR26](https://github.com/Too-Much-I/app-back-end-billing/pull/26), develop merge `e48efb7` 후 별도 브랜치에서 구현했다.
- 기준: [PLAN012 내부 R1](../plans/PLAN-012-IMPLEMENTATION-SPEC-R1.md), [R2 wire](../contracts/LC-BILLING-PAID-EXAM-CONTRACT-R2.md), [기접수 작업 보완](../contracts/TMI-220-REFUND-PROTECTED-SESSION-CLARIFICATION.md).

## 1. 5줄 결론

1. 성공 confirm Transaction이 lastConfirmedSessionId를 기록한다. command TTL/현재 activeSession/시각 비교로 보호 대상을 추정하지 않는다.
2. 환불 root 뒤 RESERVED→GROUPS를 건별 Transaction, tick 최대20단계로 정리한다. INITIAL처럼 group 없는 예약도 처리한다.
3. 보호 증거 불명은 group refundReview를 cursor와 함께 저장하고 다음 group으로 진행한다. 재확인은 outbox 최초 생성과 RESOLVED를 원자 처리한다.
4. 불변 canonical JSON body/digest를 저장하고 RS256 JWT만 새로 발급해 LC에 전달한다. 빈204만 성공, 인증 차단·재시도·24시간 보류를 구분한다.
5. 실제 LC/Store 검증·운영 연결은 미완료다. 기본 OFF와 processing ON 기동 거절을 유지하며 자동 schema 설치·배포는 없다.

## 2. 변경 동작

### 예약과 보호 근거

- `PaidReservationService.finishConfirm`은 SUBSCRIPTION 성공 확정과 동일 Tx에서 lastConfirmedSessionId를 쓴다. 실패·reserve·expiry·상태 이벤트는 갱신하지 않는다.
- fan-out의 취소는 기존 release 로직으로 proposed Session·pending guard·예약·RESERVE receipt·사용 원장을 정합화한다. 기존 confirmed Session/current guard·다른 권리에는 영향을 주지 않는다.
- confirmRejection=ENTITLEMENT_REVOKED를 저장하되 가짜 CONFIRM 요청/hash는 만들지 않는다. 나중에 실제 confirm 요청이 도착하면 그 요청 hash로 거절 receipt를 저장해 같은 요청을 재현한다. status에서도 거절을 조회할 수 있다.
- 탈퇴 계정도 환불 정리는 가능하지만 계정을 활성화하거나 새 권리를 지급하지 않는다. 자료가 이미 사라졌다면 임의로 소유권을 복구하지 않는다.

### 순회·원자성

- `RefundFanout.tick`: 5초 fixed-delay annotation, 인스턴스 내 순차/synchronized, 최대20단계. 각 단계가 예약1건·group1건 또는 phase 전환이며 한 번에20개를 한 Tx로 묶지 않는다.
- 30초 DB lease, leaseVersion/generation, recovery fence를 매 단계 검사한다. Tx 끝에 lease가 만료되면 group/cursor/outbox 변경을 rollback한다. crash 후 lease 만료로 재개한다.
- group 없는 INITIAL은 취소만 하며 가짜 group/event를 만들지 않는다. 마지막 RESERVED 부재를 확인한 뒤 GROUPS로 전환한다.
- 증거 없는 group은 replacementBlocked와 OPEN refundReview를 남기고 cursor를 전진한다. DONE은 열거 완료일 뿐 OPEN review/미전송 outbox까지 해결됐다는 뜻이 아니다.
- `recheckGroup`은 저장된 증거를 다시 읽는 내부 코어 메서드다. caller가 보호 Session/금액을 지정하거나 cursor를 되감지 않는다. 실제 운영 호출의 권한·감사 CLI 연결은 TMI-221 후속이다.

### 전달

- body는 R2 필드 순서의 compact canonical JSON **문자열**로 저장한다. schemaVersion은 정수1, occurredAt은 구매 최초 appliedAt UTC 밀리초다. 내부 저장 표현만 문자열이며 wire/fixture는 불변이다.
- 최초 ACCESS_REVOKED ledger의 reason/effect와 구매 환경·account·group/source/Session 증거를 대조한다. 현재 purchaseState 변경이나 추가 환불로 event/body/보호ID를 바꾸지 않는다.
- `RefundAccessPublisher`는 독립 lease 뒤 DB Tx 밖에서 HTTP 호출한다. HTTPS origin·고정 route·RS256 전용 audience를 사용하며 body 서명을 추가하거나 SigV4 fallback을 만들지 않는다.
- `RefundAccessHttpTransport`: 전체 응답5초,16KiB상한, redirect/proxy/cookie/자동retry 없음. 응답유실 뒤 동일 body에 새 JWT/jti를 발급한다.
- exact 빈204→DELIVERED,401/403→BLOCKED_AUTH, 두409 및 다른 계약 오류→REVIEW_REQUIRED.408/429/5xx/I/O는 bounded backoff.429/503의 유효 Retry-After를 존중하고 잘못된/중복/overflow header는 기본 backoff다. 유효한 긴 대기는 조기 재전송하지 않고 보류한다.
- 오래된 lease/generation의 응답은 DB 결과를 덮어쓰지 못한다. 최초 dispatch부터24시간 경계를 적용하며 event/body/cursor 초기화는 없다.

## 3. schema와 이관

`RefundFanoutSchema`는 r7 검증 후 source 조회 index와 payment_access_outbox validator/unique/due index를 명시 유지보수 함수로 설치한다. isolated writers·ALL_BLOCKED가 필요하며 drift는 거절한다. 기존 manifest·index를 drop하지 않는다. outbox에 TTL/임의 추가 index를 허용하지 않는다. 실제 운영 설치는 하지 않았다.

lastConfirmedSessionId는 신규 성공 confirm writer부터 기록한다. 운영 legacy 존재/coverage는 조회하지 않았다. 증거 없음을 정상 후보 하나로 추측하거나 범용 backfill을 만들지 않는다. 실제 대상이 있으면 승인된 한정 이관과 TMI217 보존 접점을 먼저 확인한다.

## 4. 운영 연결과 남은 gate

이번 클래스는 explicit constructor 기반 코어다. tick에 5초 annotation은 있으나 Spring bean을 등록하거나 기존 기동 gate를 해제하지 않았다. 따라서 운영 앱에서 자동 실행되는 상태가 아니다. 이는 미검증 provider를 켜지 않기 위한 경계이며 다음 작업을 남긴다.

- 실제 검증 provider adapter와 기본 OFF Spring 구성·LC 주소/전용 키·환경 대응 연결. ON/OFF 및 ON/ON runtime 허용은 검증 후 별도 기동 규격 테스트가 필요하다. 현재는 기존 processing ON 거절 유지다.
- LC 기접수 작업 보완·R2 실제 HTTP/응답유실·공동 상태 검증. 테스트의 가짜 LC 응답은 공동 E2E가 아니다.
- TMI217 병합 후 진행 증거/OPEN review/outbox 보존과 삭제 경합 확인. 이번 작업은 purge를 추가하지 않는다.
- TMI221 운영 재개 CLI·actor/reason 감사·Slack 집계/즉시 알림. 코어의 REVIEW_REQUIRED/BLOCKED_AUTH 저장은 구현됐지만 운영자가 안전하게 재개할 경로는 후속이다.
- 실제 schema 설치·키 교체·Store 거래·배포/판매 활성화는 별도 승인이다.

## 5. 검증 범위

- `RefundFanoutIntegrationTest`: 실제 replica-set Mongo의 예약/group 정리·거절 replay·보호 pointer·command 소실·20단계 초과·증거불명 보류와 재확인·rollback·lease 만료·동시worker·schema drift·OFF 보존·JWT 재발급/응답유실.
- `RefundAccessPublisherTest`: 상태 분류·두409·Retry-After 및 로컬 실제 HTTP redirect/oversize 차단. 서명검증은 테스트 생성 RSA키만 사용한다.
- 실제 LC Job/Callback/Summary, 실제 TLS/ALB·Store/provider·대량 운영 데이터·TMI217 삭제 경합은 미검증이다. 최종 전체 결과는 CURRENT_STATE/WORKLOG를 따른다.

## 6. 파일

- [예약 writer](../../src/main/java/web/tosunsaeng/billing/domain/reservation/paid/PaidReservationService.java)
- [순차 정리](../../src/main/java/web/tosunsaeng/billing/domain/reservation/paid/RefundFanout.java)
- [schema](../../src/main/java/web/tosunsaeng/billing/domain/reservation/paid/RefundFanoutSchema.java)
- [publisher](../../src/main/java/web/tosunsaeng/billing/domain/reservation/paid/RefundAccessPublisher.java)
- [HTTP transport](../../src/main/java/web/tosunsaeng/billing/domain/reservation/paid/RefundAccessHttpTransport.java)
