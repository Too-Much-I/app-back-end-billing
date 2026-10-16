# TMI-217 deletion exact R2 draft-2 — LC E1~E5 보완

> 후속 [draft-3](TMI-217-DELETION-EXACT-R2-DRAFT-3.md)가 retryNotBefore 하한 보존·correlationId 형식을 명확히 한다. LC 제한적 독립 raw 검증은 통과했으며 경계·경합31개는 미실행이다. 기존 fixture는 보존한다.

- 날짜: 2026-10-16 / revision: `lc-billing-deletion-exact-r2-draft-2`
- 상태: **LC 조건부 수락 의견 반영, 재확인 전 미동결**. 제품 구현·공동 테스트 통과가 아니다.
- [draft-1](TMI-217-DELETION-EXACT-R2-DRAFT-1.md)의 저장/route/7필드·단순화 정책은 유지하고 아래 시간·오류·상태·보존 규칙을 우선한다. 구 fixture44개 원본은 변경하지 않는다.

## 1. 결론

1. delivery의 운영 격리와 안전한 원문 cleanup을 분리한다. 기존 operation 전체24시간 예산도 전달 대기만으로 cleanup을 멈추지 않는다.
2. 200회/24시간은 **다음 발신 금지 경계**다. 경계 전에 허용된200번째 시도의 유효204는 저장 가능하다.
3. LC 상세 검토시각은 cleanup과 필요한 전달이 모두 끝난 최초 completedAt+120일이다. 자동purge 시각이 아니다.
4. 서명 자체가 불신이면401, 신뢰키로 검증된 wrong-purpose는403이다. 신규 요청/응답 framing과 재시도 경계를 명시한다.
5. 별도 raw byte/canonical 벡터 및 경계·경합 명세를 추가한다. 포장 검증과 실제 decoder/DB 검증은 구분한다.

## 2. 발신·재시도 exact 경계

이 규칙은 **delivery 전용**이며 cleanup attempt/backoff에 적용하지 않는다. target별 기존 version CAS 및 lease를 사용한다. poll은 fixed-delay5초, tick최대20건 순차, 같은instance tick중첩0, lease30초, HTTP timeout5초, 응답최대16,384bytes다.

- 처음 실제발신을 claim할 때 firstAttemptAt를 UTCms로 고정하고 attempts를0→1로 **HTTP 전** 영속한다. 재시도/lease복구로 최초시각을 바꾸지 않는다.
- 다음 claim/send를 허용하려면 `now < firstAttemptAt+24h`와 `attempts < 200`이 모두 필요하다. 최초claim에서는 firstAttemptAt가 아직없으므로 now로 정한다. claim뒤 HTTP직전에도 lease/version/24시간 경계를 확인한다. 이때 이미증가한 attempts=200은 해당200번째 시도를 금지하는 조건이 아니다.
- attempts=199에서200번째발신은 가능하다.200번째 유효empty204는 lease/version/digest가 여전히일치하면 저장한다. 발신뒤24시간을지났어도 위조건을충족한성공응답은저장한다. 실패/timeout이면200회소진으로NEEDS_REVIEW,201번째발신0.
- 발신전영속증가뒤crash 또는send직전기한경과는시도를소진한것으로허용한다. 별도failureCount를추가하지않는다. backoff의n은그시점영속 attempts다.
- n번째 실패뒤 `min(300, 2^min(n-1,9))`초 + [0,1초) jitter. crash lease복구도이n으로보수계산한다. 계산은bounded exponent로overflow를피한다.
- now가24시간정각이면다음claim/send금지. nextAttemptAt가정확히그경계이거나그이후면자동due를해제하고NEEDS_REVIEW. 기존 firstAttemptAt/attempts/원payload는보존한다.
- 자동 budget reset 없음. 수동재개로원시각/attempts를초기화하는기능은이계약에없다. 기한/횟수소진 건의재개정책은후속명시규격없이우회하지않는다.

### Retry-After

429/503의단일header만적용한다. 양의/0 정수초 또는 유효HTTP-date, 중복/음수/숫자overflow/잘못된date는invalid로backoff사용. 시각덧셈overflow도invalid다. 과거date/0은backoff를줄이지않는다.유효하면 max(응답수신시각+backoff, header시각)을사용한다. 결과가24시간경계이상이면RETRY_AFTER_REVIEW로격리하고자동nextAttemptAt/lease는null로한다. header가유효한미래시각이면별도 `retryNotBefore`(date|null)에보존해향후명시재개가그시각을우회하지않게한다. invalidheader때retryNotBefore=null. 기존보존된더늦은최소시각은단축하지않는다.

## 3. 인증·HTTP 분류 보완

### 요청

- 기존A 검증기를사용한다. 신뢰할수없는key/unknownkid/잘못된서명/issuer/만료는401. 해당삭제verifier의신뢰키로서명검증된유효LC token의wrong-purpose/subject는403. issuer문자열만보고403으로분류하지않는다.
- OFF/틀린connector404가본문보다우선, 정상connector에서인증/인가후Content-Type/Content-Encoding/크기/JSON을검사한다.
- Content-Type은 `application/json`, 선택적단일 `charset=utf-8`(대소문자무시,quoted UTF-8 허용)만허용한다. 다른media/charset·중복/다른parameter는415 INVALID_REQUEST. 이세부제한은양측fixture확인대상이다.
- Content-Encoding은없거나identity만허용. gzip/deflate/br/중복·복합값은415 INVALID_REQUEST, 압축해제안함. wire16,384bytes상한은그대로다.

### 응답

- 최종응답 empty204만성공이다. nonempty204·기타최종1xx/2xx·3xx/redirect·지원하지않는압축응답·16,384bytes초과응답은CONTRACT_ERROR 격리한다. 일반interim100/103은클라이언트가처리하고최종응답으로성공판정하지않는다. protocol upgrade101은거절한다. body상한은상태코드와무관하게적용한다.
- 401/403은AUTHENTICATION_FAILED.400/404/405/409/413/415/422및기타영구4xx는계약/운영확인. EVENT_ID_CONFLICT와TARGET_UNRESOLVED는각고정failureCode로남긴다.
- 408/429/500/502/504등5xx·timeout/I/O는일시재시도하며이일시실패의failureCode=null 허용. 진단원문은저장하지않는다.
- 503 body가JSON으로파싱되면4필드/code/message/retryable=true/correlationId형식을검증하고모순·추가/누락필드는CONTRACT_ERROR. 파싱불가/HTML/empty503은일시재시도다. JSON scalar/array처럼파싱은되지만업무오류가아닌503도계약격리한다. 기존503 code/message/Retry-After:1은정상receiver계약이며sender는프록시실패를별도로다룬다.
- 결과저장시target의cleanup갱신으로version이달라졌다면성공도저장하지않고재조회/같은전송으로수렴한다. cleanup진행으로deliverypayload를변경하지않는다. 유실204때이미삭제204/no-op이이를안전하게지원한다.

## 4. LC 상태·cleanup·보존

### 4.1 시각과 상태

LC 회신으로 requestedAt는 owner guard를touch하고 writeBlocked=true operation을저장한**성공Tx의논리접근차단시각**임을확인했다. DB commit완료wall-clock은아니다. BSON에서읽은UTCms requestedAt를deletedAt에한번고정한다. callback seal/GRADING drain시각과혼동하지않는다.

delivery에 draft-1 필드와 `retryNotBefore(date|null)`를사용한다. 상태불변식:

| 상태 | 조건 |
| --- | --- |
| PENDING 최초 | attempts0,firstAttemptAt/ackedAt/leasepair/failureCode/retryNotBefore=null; nextAttemptAt는due시각 |
| PENDING 시도후 | attempts>=1/firstAttemptAt고정,ackedAt=null; leasepair동시존재또는null; retry시nextAttemptAt존재 |
| ACKED | ackedAt필수·최초성공기록시각고정,leasepair/nextAttemptAt/failureCode=null; 원firstAttemptAt/attempts보존 |
| NEEDS_REVIEW | ackedAt=null,leasepair/nextAttemptAt=null,고정failureCode필수; 원시각/시도/payload/retryNotBefore보존.자동due제외 |

일시HTTP실패의failureCode는null, DB저장실패는원상태유지/재조회하며저장성공을가정하지않는다. ACKED를원문정리성공과동일시하지않는다.

### 4.2 cleanup 독립

delivery NEEDS_REVIEW를operation NEEDS_REVIEW에그대로복사하지않는다. 요청+24시간의기존전체작업기한도**delivery대기만으로**안전하게준비된cleanup을스케줄제외하지않도록분리한다. cleanup자체오류/소유·참조불명에대한기존안전중단은유지한다. 새일반queue/engine은없으며기존cleanup단계와target delivery상태를분리해실행한다.

cleanup완료+미전달이면operation전체완료아님/새시험차단유지/복구자료보존. 모든필요204+기존안전checkpoint완료면새시험허용가능하며안전한잔여S3정리는독립진행. operation.completedAt는둘다끝난최초시각에만한번기록한다.

### 4.3 LC 상세120일

LC operation에선택적 `billingDetailReviewAt`를두고 **최초completedAt+120일**로한번설정한다. linked target/command의상세정리검토는이operation시각을참조하며각target ACK시각에별도120일clock을만들지않는다. 일부ACKED여도전체미완료면기산점없음/기존30일TTL금지다.

linked전달복구에필요한 operation/target/command는미완료뿐아니라완료직후에도기존complete/finishRetention/expiry전파의30일TTL대상에서제외한다. reviewAt도달은자동삭제/멱등보장종료아님. 이초안은자동purger를추가하지않는다. Billing receipt는최초processedAt+120일의detailReviewAt를그대로사용한다. 최초deny·금융·미해결자료와분리하고실제삭제뒤영구event충돌검출을약속하지않는다. 기존UNLINKED자료의30일정책은일괄변경하지않는다.

## 5. fixture와 회신 요청

새 [draft-2 자료](../../src/test/resources/contracts/lc-billing-deletion-exact-r2-draft-2/README.md)에 raw bytes/canonical 기대값과정밀경계명세를둔다. 구44개는설명형목록으로보존한다. draft-2의canonical-only벡터는scalar인허가완료를뜻하지않는다. 새route서명JWT/전체scalar byte suite·실Mongo동시성·실LCworker검증은여전히미실행이며현재묶음을완성fixture로동결하지않는다.

LC 재확인: (1)claim/send/유효200번째응답과24h경계, (2)cleanup예산분리, (3)completedAt+120일/TTL보호, (4)인증/415/503/응답상한, (5)추가벡터/구ID매핑. 기본OFF/배포미승인유지. 사용자제품정책재선택없음.

## 6. 범위

LC `TMI_217_DELETION_EXACT_R2_DRAFT_1_REVIEW.md` 원문에근거한보완이다. 방향/저장분리수락과본세부조건의동결은구분한다. 제품코드·LC파일·DB·배포·외부메시지/Jira 변경없음. 정적자료검사외runtime테스트미실행이며기존통과건수를재사용하지않는다.
