# TMI-212 C1 — durable source worker와 sync 완료 경계

2026-10-13 / feat/TMI-212-revenuecat-worker-reconciliation. 사용자 C 후속 구현 요청. C 전체/운영 활성화 완료가 아니다.

후속 C2a: [외부 dispatch 규격](TMI-212-provider-dispatch-r1.md)에 접수 시 최초 ticket/lease 경계와 공용 프로젝트 budget을 추가했다. 아래 C2 미완료 표현 중 공유 quota/AUTH의 명시 생성형 기반은 구현됐으며 계정 scan·runtime 통합은 계속 미완료다.

최신 C2b: [계정 조회 규격](TMI-212-account-lookup-r1.md)의 계정 scan/암호화 checkpoint·sync 재시도/완료·기본OFF 실제 runtime을 구현했다. 아래 C1 당시 C2 미완료 기록은 역사적 경계이며 현재 잔여는 D의 정기 대사·보존/알림/운영 재개와 실환경/공개sync 활성화 gate다.

## 1. 결론

1. 이미 인증·검증되어 inbox에 접수된 관측을 처리하고 같은 거래의 대기 sync를 완료한다. 외부 조회 없이 webhook→원장→sync를 연결한다.
2. lease30초와 recovery epoch를 최초 claim에서 고정하고 금융 Transaction 안에서 다시 검사한다. 기존 apply가 새 epoch를 다시 capture하는 경로를 worker에 사용하지 않는다.
3. 원장 commit 뒤 worker 중단 시 source에 남은 작업 잠금/다음 시각으로 sync 완료를 재개한다. 업무state와별도효과장부를복제하지않고inbox에작업메타데이터만둔다.
4. 한 tick 최대100 source, source당최대100 sync. 완료된sync의pending만차감하고검토case의pending은유지한다. 새source입력이늘어도이전완료작업회수를번갈아처리한다.
5. 외부계정조회합치기·프로젝트공유quota·API pagination·AUTH차단·sync독립lease는C2후속이다. 자동scheduler와사용자sync ON은아직열지않는다.

## 2. exact 저장 규격

purchase-core-r4는`payment_provider_inbox.work` optional object만추가한다. fields는`owner` string|null,`until` date|null,`leaseVersion` long>=1,`generation` long>=1,`fenceVersion` long>=0,`nextAttemptAt` date|null,`retryStartedAt` date,`attempts` long>=1,`failure` string|null. 내부ID를로그/toString에노출하지않는다. case로인계할때work는복제하지않는다. financialdigest와업무state/pending의의미는불변. generation은C1에서1로생성하고추후승인재개규격을위해검사하며자동증가/재개하지않는다.

claim은같은MongoTransaction에서recoveryfence+행CAS,owner랜덤UUID/leaseVersion증가/30초until. 금융apply와sync bind/complete의같은Transaction에서lease owner/version/generation/until+최초epoch를검사하고fenceVersion을증가한다. 기간내동일worker간호출도versionCAS로경합한다. 잠금갱신은유효한기존lease만허용,만료후연장/새epoch취득없음.

core가terminal로전환되어도work를보존하고sync연결을마칠때까지nextAttemptAt을남긴다. terminalsource의purgeAt은기존120일이며C1작업을120일넘게방치해도된다는뜻이아니다. SOURCE RECEIVED는TTL없음. 검토이관은기존case/pending규칙,IGNORED는처리대상아님. 24시간실패예산이면RECEIVED는case로인계하며금융권리는변경하지않는다. terminal의미완료sync는계속남기고운영보류하며원장을재지급하지않는다.

한row를같은tick에서다시claim하지않는다. 재시도는min(1시간, 5초×2^min(attempts-1,10)×(1+jitter)), jitter는[0,1)이다. 테스트는주입한값으로경계를고정한다. 업무원장의attempts와worker시도횟수를혼용하지않는다. failure는PURCHASE_BLOCKED/SYNC_PENDING/TEMPORARILY_UNAVAILABLE/RETRY_EXHAUSTED만코드가설정하고payload/외부예외를저장하지않는다.

## 3. 이관·잔여 범위

r3→r4 writer격리/ALL_BLOCKED,sourcevalidator collMod와`ix_payment_provider_work_due`(environment,work.nextAttemptAt,work.until;nonunique/partial없음/TTL없음)추가. r3/r4중간상태만명시재개가능,다른drift면중단. 기존row의work없음은첫claim에초기화하므로대량backfill없음. runtime은verify-only이고구버전writer자동rollback금지.

C1은명시생성형tick이며Spring scheduler/외부호출없음. C2에서실readadapter·공유quota·계정단위scan/페이지중단재개·sync독립retry를완성한뒤runtime조건을검증한다. D의reference영속화/정기누락복구·case운영/보존/실환경은별도다. 새로운제품정책이나Identity/LC계약변경없음.

과거에work없이이미terminal로된source와남아있는sync의복구는C2의sync쪽scan에서다룬다. C1의terminal재개는새worker가처음claim한뒤원장이반영된row만대상이다. 120일보존경계와보류된terminal작업의운영재개/알림을닫기전자동scheduler/수신활성화를승인하지않는다. C1을외부검증/공유quota/대사완료로보고하지않는다.
