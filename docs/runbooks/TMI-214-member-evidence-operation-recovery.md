# TMI-214 — MEMBER 증거·operation 복구 구현

## 1. 5줄 결론

1. TMI-213은 PR #17로 develop에 병합했고 `feat/TMI-214-member-evidence-operation-recovery`에서 후속 구현했다.
2. 기존 purchase-account는 검증된 Identity JWT의 최소 MEMBER 증거를 계정·탈퇴 fence와 같은 Transaction에 저장한다. 기존 purchaseAccountRefId를 유지한다.
3. MEMBER 소유 operation은 대기 commit 후503, status 읽기, 같은 key 재개, 영속 J3 실패 및 최초 reserve snapshot 재현을 지원한다.
4. 신규 flag는 기본 OFF다. paid resolver/confirm/복원 writer 연결은 TMI-215이며 현재 paid-admission ON은 계속 기동 거절한다.
5. 본 구현은 LC lease/cleanup 수정, 전체 공동 상태28개 완료, 운영 schema 설치·배포·판매 활성화를 포함하지 않는다.

## 2. 반드시 읽어야 하는 범위

### 코드와 Transaction

- `VerifiedMemberEvidence`: 인증된 principal의 sub/issuer/account_type/iat와 서버 payment environment로 구성한다. scope/audience·시간도 확인하며 signature 검증은 기존 Identity decoder 책임이다. JWT 원문은 객체 필드·Mongo·로그에 보관하지 않는다. 시각은 canonical Instant 문자열로 나노초를 보존한다.
- `PaymentAccountService`: 신규 생성/기존 계정 조회에서 evidence를 account/reference/cursor/lifecycle와 같은 ClientSession으로 저장한다. 기존 String 호출은 증거를 생성하지 않는다. 탈퇴와 공유하는 lifecycle CAS가 선행하며 WITHDRAWN을 되살리지 않는다. evidence는 과거 MEMBER 유형 증명이지 현재 ACTIVE 증명이 아니다.
- `MemberOperationRecovery`: MEMBER_ACCOUNT 귀속의 회복 로직이다. recovery epoch/barrier와 현재 account/lifecycle를 확인하고, 저장된 owner identity를 확인한 다음 digest/replay, 신규 MEMBER 조건 순으로 진행한다. account version의 자연 증가는 허용하되 역행·원 account 교체는 거절한다.
- 대기에는 불변 session/mock/phone 요청 snapshot, digest, owner, receivedAt/waitingSince를 남긴다. 예약·allocation·소비·복원은 생성하지 않는다. 실제 대기 없이 같은 Transaction에서 결론 난 요청은 waitingSince를 남기지 않는다.
- 후속 paid resolver가 `Admission`을 공급하고 동일 Context에서 예약·원장·guard를 작성한다. 별도 Transaction/외부 호출은 허용하지 않는다. reserve 메서드에서만 callback을 실행하며 status는 실행하지 않는다. 현재 무료 ReserveService에 MEMBER 조건을 일괄 적용하지 않았다.
- callback 성공은 실제 Reservation의 operation/digest/session/mock/subject/source를 검증한 뒤 receipt와 원자적으로 확정한다. callback 오류는 전체 rollback한다. `Optional.empty`는 exact pre-creation TRANSITION_CONTEXT_CONFLICT 전용이며 권리 부족·일시 장애를 이 값으로 바꾸면 안 된다.
- 최초 응답/source를 저장해 command cache가 없어도 재현한다. status는 실제 Reservation lifecycle을 읽으며 source는 고정 receipt에서 읽는다. 모순·source 변경·근거 소실은503이고 임의404/terminal/pending으로 바꾸지 않는다.

### HTTP 계약과 호환

기존 `POST /internal/v1/reservations/status` body의 userId/operationId를 유지한다. recovery flag가 켜지면 receipt를 먼저 확인하고 없을 때 기존 무료 reader로 이어진다. 새 public API·Identity API·추가 사용자 JWT claim은 없다.

| 상황 | 응답 |
| --- | --- |
| commit된 MEMBER 대기 | 503 MEMBER_EVIDENCE_PENDING, retryable=true, Retry-After:1 |
| 현재 귀속/탈퇴 차단 | 409 OPERATION_OWNER_MISMATCH, false, Retry-After 없음 |
| reserve의 영속 context 거절 replay | 409 TRANSITION_CONTEXT_CONFLICT, false |
| status의 영속 pre-creation 거절 | 409 RESERVE_REJECTED_BEFORE_CREATION, false |
| 저장소/복구 barrier/근거 모순 | 기존503 BILLING_TEMPORARILY_UNAVAILABLE |
| receipt 없음 | 기존 status reader 결과. 전체 부재는 기존404 OPERATION_NOT_FOUND |

오류는 기존 code/message/retryable/correlationId 네 필드다. 성공 status의 `entitlementSource`는 유료 receipt가 있을 때만 EXAM_PASS/SUBSCRIPTION을 반환하고 legacy 응답은 null 생략이다. LC strict reader 선배포 gate를 유지한다. transition/phone INITIAL 선택·최종 reserve route 연결은 TMI-215 범위다.

### 설정과 schema

| 설정 | 기본 | 조건 |
| --- | --- | --- |
| BILLING_PAYMENT_MEMBER_EVIDENCE_ENABLED | false | purchase-account ON, trusted Identity issuer/JWKS, recovery fence 필수 |
| BILLING_PAID_EXAM_FOUNDATION_ENABLED | false | paid-exam-recovery-r2 schema 검증만 수행 |
| BILLING_PAID_EXAM_RECOVERY_ENABLED | false | foundation bean, evidence writer/purchase-account, recovery fence·환경 매핑·issuer 필수 |
| BILLING_PAID_ADMISSION_ENABLED | false | 후속 runtime 전 ON 기동 거절 유지 |

신규 설치는 `PaidExamSchema.installUnderMaintenance`의 r2 validator다. 기존 r1 설치는 쓰기 격리 후 `upgradeRecoveryUnderMaintenance(db, true)`를 명시 실행해야 한다. 기존 validator/index를 먼저 exact 확인하고 receipt validator만 확장한다. startup은 수정하지 않는다. r1 row는 그대로 보존하며 request snapshot을 hash에서 발명하지 않는다. r1 대기를 재개하려면 검증된 이관/운영 조치가 필요하므로 자동 재개는503이다.

v2 receipt는 기존 key unique와 no-TTL을 유지하고 requestSnapshot을 필수 저장한다. 미완료는 자동 삭제하지 않는다. terminal 상세 보존 eligibility는 terminal+120일이며 BSON Date는 올림해 sub-ms 때문에 조기 폐기되지 않게 한다. LC 첫 durable 접수시각은 wire에 없으므로 Billing이 발명하지 않는다. exact120일 경계 함수는 Instant로 비교하고, 보장창 밖에도 기록이 남으면 원 결과를 재현한다. 이 구현에 purge worker는 없다.

## 3. 사용자 추가 결정

이번 구현을 위한 정책 선택은 없다. 배포·활성화는 기존 선행 조건을 충족한 뒤 별도로 진행한다. 운영 데이터나 환경변수를 변경하지 않았다.

## 4. 남은 위험·다음 단계

- TMI-215: source별 자격/paid-first·trial owner와 원 group fence·confirm/복원·phone INITIAL 및 최종 reserve adapter. 본 MEMBER admission callback을 실제 resolver와 같은 Transaction으로 연결해야 한다.
- TMI-216: LC 사용자 HTTP/worker/lease/cleanup/NEEDS_REVIEW 좁은 예외·응답 유실·최종 마무리 실제 연동. Billing status만으로 LC guard를 임의 해제하지 않는다.
- TMI-217: 별도 삭제 route·과거 owner 판정·자료별 purge와 재유입. 기존 신규 생성 중 삭제 미접수 정책을 유지한다.
- 현재 source 구조는 내부 snapshot이다. 새 사용자 권리 선택·phone trial 소유를 MEMBER account로 위장하지 않는다.
- offline fixture와 이번 실제 Mongo/HTTP 단위 검증을 공동28개 전체 통과로 합산하지 않는다.

## 5. 검증 기록

최종 `./gradlew clean test`는1,523개·실패/오류/skip0(신규26 포함)이다. 중간 전체 실행에서 기존 PurchaseCore 동시성 테스트의 일시503이1회 발생했고 단독 및 최종 전체 재실행은 통과했다. 정확한 일시 원인은 미확정이며 기존 재시도 한도/테스트는 변경하지 않았다. offline80/hash 및 diff 검증도 통과했다. 상세는 CURRENT_STATE/WORKLOG에 기록한다. 테스트는 가짜 Identity principal, disposable Mongo7 replica set·failpoint만 사용한다. 실제 Identity/LC/DB/RevenueCat/Store는 호출하지 않는다.

신규 검증: 대기 commit 및 유실 응답 status·read-only, 보강 후 같은 key 재개, 최초 snapshot·source replay,7일 command 부재/120일 이후 잔존 결과, payload conflict, J3 영속 거절/status, admission rollback·동시 재개, 탈퇴와 evidence/admission 경합, unknown commit 동일 commit 재시도, source 모순, r1→r2 명시 schema upgrade, 최소 evidence와 public principal 경계, workload status 오류4필드·Retry-After·body 조회, 기본 OFF/startup 조건.

외부 변경은 TMI-213 commit/push/PR #17 병합뿐이다. TMI-214 코드·기록은 검토 가능한 로컬 변경으로 남긴다. 동시에 추가된 RevenueCat 수동 삭제 기한 관련 CURRENT_STATE/WORKLOG 기록은 별도 작업으로 보존한다.
