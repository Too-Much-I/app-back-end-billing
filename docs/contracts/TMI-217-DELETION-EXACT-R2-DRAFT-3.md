# TMI-217 deletion exact R2 draft-3 — 하한 보존·오류 추적값

- 날짜: 2026-10-16 / revision: `lc-billing-deletion-exact-r2-draft-3`
- 상태: LC draft-2 제한적 독립 검증 회신 반영. **아래 두 명확화는 공동 재확인 대상이며 전체 동결/제품 테스트 완료가 아니다.**
- [draft-2](TMI-217-DELETION-EXACT-R2-DRAFT-2.md)의 다른 규칙은 유지한다. 기존 fixture 원본/해시는 변경하지 않는다.

## 1. 결론

1. 기존 retryNotBefore는 누락·invalid·더 이른 Retry-After로 지우거나 단축하지 않는다.
2. 다음 발신시각은 backoff·새 유효 header·기존 하한 중 최댓값이다. 기존 하한도24시간 경계 이상이면 자동 발신하지 않는다.
3. correlationId는 UUID로 제한하지 않는다. 아래 ASCII1~128자 형식의 불투명 서버 추적값으로 명시한다.
4. 정상503 원문과 하한 유지·추적값 오류 사례를 새 draft-3 자료로 추가한다.
5. LC의 hash2/raw7/추가거절3 통과는 제한적 오프라인 결과다.31개 HTTP/DB/worker 경합의 PASS로 합산하지 않는다.

## 2. retryNotBefore 규칙

기호: old=저장된 retryNotBefore 또는 없음, receivedAt=응답 수신시각, candidate=새 단일 유효 Retry-After가 나타내는 시각. 정수초는 receivedAt에 더하고 HTTP-date는 해당 절대시각이다. invalid/중복/누락이면 candidate가 없다. overflow는 invalid이며 시각이 감기도록 계산하지 않는다.

- **저장:** newFloor = max(존재하는 old, 유효 candidate). 둘 다 없을 때만 null. 지난 시각도 저장돼 있다면 이번 오류 때문에 지우지 않는다. 과거 candidate만 있으면 그 시각을 저장해도 backoff보다 빨리 보내지 않는다.
- **스케줄:** due = max(receivedAt + boundedBackoffWithJitter, newFloor가 있으면 그 값). 이는 draft-2의 `max(backoff, 새header, 기존하한)`과 같다.
- **격리:** due >= firstAttemptAt+24시간 또는 다음 시도 횟수 소진이면 새 자동 발신 금지. 기존 하한 자체가 경계 이상이어도 RETRY_AFTER_REVIEW를 유지하고 nextAttemptAt/lease를 null로 한다. old/newFloor·원시각·attempts는 보존한다.
- 429/503 이외 응답에 Retry-After가 오면 새 candidate로 사용하지 않지만 **기존 하한은 유지**한다. 일반 I/O/timeout 재시도와 lease 재획득도 저장된 하한보다 일찍 send하지 않는다.
- 성공 ACKED도 원 하한을 임의 변경할 필요가 없다. ACKED는 자동 재전송 대상이 아니다. 재개/reset은 별도 규격 없이 만들지 않는다.

draft-2의 `invalidheader때retryNotBefore=null`은 **기존 하한도 없을 때에만 null**로 정정한다. 유효한 최소대기시각을 잘못된 다음 header가 취소할 수 없다. 이것이 NEEDS_REVIEW 상태의 자동재개를 허용하는 것은 아니다.

## 3. correlationId 형식

이 새 삭제 route의 업무오류 DTO에 한정한다. 다른 API의 추적값 규격을 일괄 변경하지 않는다.

- JSON 문자열, 필수/non-null, 길이1~128 **ASCII 문자**.
- 허용 문자는 `A-Z a-z 0-9 . _ -`뿐이다. 전체문자열 일치 검증, trim/coercion/개행종료 허용 없음.
- 예: `fake-trace_01.test`, 일반 lowercase UUIDv4 모두 유효하다. UUID 버전/형식을 LC가 추가 강제하지 않는다.
- 공백·제어문자·Unicode·빈 문자열·129자·숫자/null은 거절한다. 이 형식은 새 공동 명세 후보이며 LC가 확인한다.
- Billing이 생성하는 불투명 추적값이다. userId/transactionId/payload/토큰을 넣거나 클라이언트 추적 header를 무검증 echo하지 않는다. LC는 이 값으로 사용자 권한·event 중복·응답 귀속을 판정하지 않는다.

정상503은 정확한4필드, code=`BILLING_TEMPORARILY_UNAVAILABLE`, message=`Billing is temporarily unavailable.`, retryable=JSON boolean true, 위 correlationId다. 필드 순서는 자유이며 duplicate/추가/누락/잘못된타입은 계약격리다. 파싱불가/HTML503의 일시재시도 및 응답크기/압축 우선검사는 draft-2대로 유지한다. 이검사는 sender 계약이며 실제HTTP 구현검증과 구분한다.

## 4. 자료·검증 및 수락 요청

새 [draft-3 자료](../../src/test/resources/contracts/lc-billing-deletion-exact-r2-draft-3/README.md): 하한 보존 사례6개, 정상503 원문1개, correlationId 입력8개. 사례의 기대시각은 jitter를 포함해 주어진 backoffDueAt를 입력으로 하며 실제랜덤함수를 테스트한 것이 아니다.

LC 원문 `TMI_217_DELETION_EXACT_R2_DRAFT_2_VALIDATION.md`에서 관측한 결과: 원본 파일해시2개 일치, canonical3/strict거절4의 raw7개 및 추가거절3개. 이는 상대의 독립 test-only 검사기 결과이며 Billing이 새 제품 HTTP를 실행한 결과가 아니다. draft-2 boundary31개는 미실행이다.

이번 Billing 검사는 새 JSON구조/기대하한 산술/문자집합·정상503구조/hash의 정적 자료 대조다. 실제 sender/decoder/DB/worker 검증을 대체하지 않는다. LC에 §2·§3 재확인을 요청하고, 남은 전체scalar/JWT/실경합 검증은 별도 추적한다. 구현 이전 계약동결과 구현 후 runtime검증 완료를 혼동하지 않는다.

## 5. 변경 범위

문서·새 draft 자료만 변경. 기존 rawfixture/LC파일/제품코드/DB/Jira/외부발송/배포 변경 없음. 기존 dirty 변경 보존, 기본OFF 유지. 실제 자동purge나 새 수동재개 기능은 추가하지 않는다.
