# TMI-212 C2a — 외부 조회 접수 경계와 프로젝트 공유 budget

2026-10-13 / feat/TMI-212-revenuecat-worker-reconciliation. C2의 선행 구현 단위이며 계정 scan·runtime 전체 완료가 아니다.

후속 갱신: [C2b](TMI-212-account-lookup-r1.md)에서 계정 scan·pagination·sync handoff·기본OFF runtime을 연결했다. Retry-After는 해당 계정 nextAttemptAt에 적용하며 프로젝트 전체 cooldown 기능은 추가하지 않았다. 프로젝트 공통 제한은240회/60초·동시4와AUTH차단이다. 아래 C2a 당시 잔여 기록과 실제 활성화를 구분한다.

## 1. 5줄 결론

1. 외부 응답 접수도 최초 dispatch의 recovery epoch와 유효 lease를 같은 Mongo Transaction에서 검사한다.
2. RC project당 최근 60초 최대 240회, 유효 외부 호출 최대 4개를 **명시적으로 주입한 공용 Mongo 저장소**에서 공유한다. 환경을 budget key에 넣지 않는다.
3. HTTP 실패도 요청 수에 포함한다. concurrency slot만 반환하며 프로세스 중단 때 30초 뒤 회수한다. 실제 HTTP timeout은 기존 5초다.
4. 401/403은 프로젝트 호출을 영속 차단한다. 자동 재개하지 않는다. DB/잠금 실패 때 외부 호출하지 않는다.
5. 기본 runtime 미연결/OFF·sync ON 차단을 유지한다. 계정 합치기·페이지 checkpoint·command backoff·공용 DB 배포 연결은 C2 후속이다.

## 2. 사용자·운영 주의사항

서로 다른 환경이 같은 RC project를 사용하면 동일한 budget DB/collection을 주입해야 한다. 별도 환경 DB 두 개에 각각 생성하는 것은 공유 제한을 만족하지 않는다. 운영 연결이 확인되기 전 활성화하지 않는다. 새 운영 SDK나 실제 DB/키 설정은 추가하지 않는다.

앱의 명시 생성형 read transport에 budget을 감싸며 요청 직전/응답 직후 permit 유효성을 검사한다. DB 장애 시 네트워크 fallback이 없다. acquire commit 결과 불명일 때 호출하지 않고 예약한 slot은 만료되게 둔다. 해제 실패도 응답을 성공으로 전달하지 않으며 재조회/멱등 접수로 복구한다. 프로세스 장시간 정지 또는 OS/네트워크 취소 지연에서 외부 서버가 실제 처리 중인지 완전히 증명할 수는 없다. 4개는 유효 permit 기준이며 5초 HTTP timeout·30초 slot·만료 응답 거절로 제한한다.

## 3. exact 저장·이관

공용 DB의 `revenuecat_read_budgets` collection r1. `_id`=RC project 설정값(string), `version` long>=1, `updatedAt` date, `blockedAuth` bool, `starts` date 배열(max240), `slots` 배열(max4). slot은 `token` UUIDv4 string, `until` date. additionalProperties=false, 모든 field 필수. 인덱스는 기본 unique `_id_`만 사용하며 TTL/secondary/partial 없음. 사용자/환경/거래/credential/payload 저장 없음. 정상 acquisition은 현재 시각보다 60초 이내 starts만 보존하고 슬롯 만료를 회수한다. 같은 시각 경계에서는 정확히 60초 지난 요청은 제외한다. 서버별 시계 동기화는 기존 인프라 운영 전제이며 updatedAt보다 과거인 호출은 대기한다.

DDL은 모든 project caller를 멈춘 별도 명시 maintenance 함수로만 생성/검증한다. runtime constructor는 verify-only, drift면 실패. 기존 payment schema/command/cursor는 이번 단위에서 변경하지 않는다. 자동 drop/repair/backfill 없음. 금전 상태를 복제하는 관리 원장이 아니라 호출 예산만 저장한다. auth 차단 해제는 credential 수정·모든 caller 중단·해당 project 현재 version 확인 후 명시 유지보수 CAS로만 수행한다. 재개는 starts를 보존하고 살아있는 slot이 있으면 거절한다.

## 4. 금융 경계·남은 범위

`PurchaseCore.receive(observation, dispatchTicket, leaseGuard)`는 PAYMENT_INPUT fence와 guard를 중복 접수 확인보다 먼저 검사한다. guard 실패/epoch 변경/ALL_BLOCKED이면 inbox/pending 및 guard 변경 전체 rollback. 기존 명시 직접 접수와 webhook 계약은 유지한다. worker는 오래된 응답을 새 ticket으로 바꿔 접수하면 안 된다.

이번 코드는 source 원장 처리 C1과 외부 read adapter A 사이의 선행 경계다. 아직 account-level lease·due scan·pagination checkpoint·24시간 command review·Retry-After 공용 cooldown·자동 worker/runtime bean·reference 영속화·실환경을 완성했다고 보고하지 않는다. 429/503의 기존 read client 파싱은 유지하고 다음 C2 dispatcher에서 next-at에 반영한다.
