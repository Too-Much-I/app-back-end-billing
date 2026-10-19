# TMI-217 — 공동 검토·fixture·실행 검증 계획

> 2026-10-16: [단순화 R2](../contracts/TMI-217-DELETION-SIMPLIFIED-R2.md)를 우선 적용한다. 아래 최초draft 기반 owner독립검증/최초event 추적 요구는 대체되며 사용자인가 LC책임·Billing대상/영향검사로 분담한다. 모든 케이스는 계속 미실행이다. exact 저장/fixture는 공동확인 후 확정한다.

- 날짜: 2026-10-14 / 브랜치: `feat/TMI-217-deletion-owner-retention-contract`
- 대상: [삭제·owner·보존 계약안](../contracts/LC-BILLING-EXAM-DELETION-RETENTION-R1.md) `lc-billing-exam-deletion-retention-r1-draft-3`
- 상태: 검증 **계획**. 아래 케이스는 미실행이고 아직 frozen fixture가 아니다.

## 1. 5줄 결론

1. TMI-216은 commit780e677·PR20·develop merge89ae574로 병합했다. 기존 전체 테스트1,570개 통과 기록을 확인했고 새 제품 테스트라고 합산하지 않는다.
2. TMI-217은 Jira상 제품 구현 아닌 공동 계약 준비다. 새 route·과거 owner·자료별 보존을 검토 가능한 문서로 작성했다.
3. 기존 A/R2/simplified fixtures는 유지한다. E06-owner 정정 fixtures-2는 2026-10-14 LC 독립/loopback actual-client 재검증 회신으로 해소됐다. 새 삭제 계약 검증을 뜻하지 않는다.
4. 계약/오프라인 body·JWT/HTTP 모형·실제 서비스/실Mongo 경합·배포를 분리해 판정한다.
5. LC 회신·보존 운영 결정 전에는 미확정 성공 경로를 PASS fixture로 고정하지 않는다.

## 2. 반드시 읽어야 하는 실행 경계

기존80벡터·1,570개 결과는 새 삭제 route나 owner/purge 구현을 검증하지 않는다. 아직 새 collection·DDL·flag·API·생성/삭제 code는 없다. HTTP 모형 통과도 실제 security chain/TLS/port/DB 무호출 증거가 아니다. 서비스별 구현자는 공동 fixture와 별도로 자기 제품 코드를 대상으로 검증한다.

## 3. 사용자 결정과 공동 회신

제품 정책 재선택은 없다. 신규 audience/route/body/오류, 최소 creationOwner coverage, 과거 source 삭제 scope, 다른 eventId 동일Session409, ACK 전 생성 차단 및 자료별 최종 개인 연결 제거를 LC와 검토한다. 담당자·장기 미완료 종료/hold 재검토·삭제 deny 최종 제거 조건은 운영 결정을 남긴다. 미확정 기간을 임의120일/5년으로 채우지 않는다.

## 4. 주요 위험과 검증 단계

| 단계 | 완료 증거 | 아직 주장할 수 없는 것 |
| --- | --- | --- |
| 계약 검토 | LC 실제 회신·변경점·범위 한정 수락 기록 | 상대 구현 완료 |
| fixture 제작 | 새 revision·계약 snapshot/hash·파일별 SHA256SUMS·SUMS 자체 hash·ZIP bytes | 실제 HTTP/Transaction |
| 독립 오프라인 | Billing/LC 각자 parser/crypto/canonical 구현·케이스별 결과 | 업무 원자성·실키 배포 |
| 제품 통합 | replica-set 실제 rollback/CAS/중복/unknown commit 및 서명 JWT HTTP·writer DB무호출 검사 | 운영 origin/TLS/키 회수 |
| 실환경 | 승인된 origin/port/TLS·key trust·schema/legacy coverage·producer drain·backup 복구 | 자동 판매활성화 승인 |

## 5. 세부 fixture·실행 목록

아래 W/A는 오프라인과 제품 양쪽에서 검사하고, O/R/V는 제품 상태·실Mongo 또는 복구 실행이 필수다. 모두 현재 NOT_EXECUTED다.

| ID | 입력/경합 | 기대/확인 |
| --- | --- | --- |
| W01 | 정상7필드·wire 순서 변경 | 같은 canonical bytes/hash |
| W02 | 한글/slash/quote/literal backslash+n·실제control 혼합 | scalar escaping/round-trip, normalization0 |
| W03 | duplicate/trailing/unknown/null/coercion/손상UTF8/lone surrogate |400, 저장0 |
| W04 | schema2·unknown field+schema2 | 각각422/400 우선순위 |
| W05 | raw16,383/16,384/16,385 bytes | 상한 허용/허용/413; UTF8 byte기준 |
| W06 | UUID casing/version·opaque ID 경계·UTC ms 형태 | 승인된 형식 exact, 오류400 |
| W07 | 각 업무 오류/code/message/retryable/header | 네필드 exact,503만 Retry-After1 |
| W08 | 같은event 같은body 재전송·응답유실 | 최초commit 확인/204, effects1 |
| W09 | 같은event 다른body/다른Session |409 EVENT_ID_CONFLICT |
| W10 | 다른event·새삭제operation의 이미삭제 exactSession | 인증/대상연결 확인후204/no-op, 최초사실·현재group/guard 불변. 다른group/unknown은거절 |
| W11 | 같은전송retry와 별도새삭제요청 | retry는원event/body, 새요청은새event가능. 처리한같은event/다른body409 보존, operation간원fact참조 불필요 |
| A01 | LC 올바른issuer/sub/새aud | 새route만 허용 |
| A02 | 기존reservation/attempt aud→삭제, 새aud→기존여섯POST |403, DB호출0 |
| A03 | Identity/user/역방향Billing key·동일kid 다른keySet | 인증/인가 계약대로401/403, fallback0 |
| A04 | RS256 원문서명·rotation·JOSE/추가payload·TTL/skew ±1ns | 기존 A 규칙 보존 |
| A05 | Bearer exact·query 빈 component·method |401/403 우선순위, DB호출0 |
| A06 | receiver/business OFF·틀린 localPort·위조 forwarded header | body없는404/no-store, DB호출0 |
| A07 | shared TLS Identity/LC ON-OFF·미기동 | 기존OR 기동 유지, transport 실패와HTTP 구분 |
| A08 | trailing slash/encoded path·기존user/Identity path inventory | alias 우회0·기존chain 유지 |
| O01 | 현재trial Session 귀속+epoch 일치 | Session deny·현재group 재응시/복원 차단 |
| O02 | 과거source A→target B 이후 A의 exactSession 삭제 | A만deny, B 권리·guard 유지 |
| O03 | phone B가 A의과거Session 삭제 주장 | LC사용자인가에서거절. Billing은LC독립인가를반복하지않으며 대상연결/현재target권리보호 검증 |
| O04 | MEMBER paid/복원권·account refresh version | 원계정 귀속, version을epoch로 오인0 |
| O05 | source 탈퇴 후 지연 삭제 | 증거 있는exact삭제만, ACTIVE복구0 |
| O06 | 같은owner 오래된Session 삭제·현재새Session | 새group/session/guard 보존 |
| O07 | group/session 미존재·상호불일치·owner 근거소실 |409 unresolved/owner 구분, payload보존 |
| O08 | 게스트A→회원B 이후 같은Session | LC현재소유인가·유효전송, Billing대상연결/영향판정. Billing전용merge증거 요구0 |
| O09 | Billing과거merge근거부재 / 현재group영향불명 | 전자만으로삭제거절0, 후자는임의group변경/성공0. legacy대상연결unknown은거절 |
| O10 | USER_MERGED로 paid/MEMBER복원권 이전 주장 | 기존trial merge 승인 확대0 |
| R01 | LC 생성commit 선행→삭제 | 공개409, 삭제command/queue0 |
| R02 | LC 삭제commit 선행→생성 | 삭제fence 차단, 부분Session0 |
| R03 | 원문purge 완료·Billing 장애 | 원문삭제 진행, 최소전달증거 유지·새생성차단 |
| R04 | Billing pending reserve→삭제 |503·pending guard 해제0·원operation 복구 |
| R05 | 삭제→원시험 restartContext reserve |INITIAL 새권리판정·삭제원인 복원0; 원귀속불명은거절 |
| R06 | paid confirm/복원→삭제 | 지급1 유지·다른권리 보존 |
| R07 | 삭제/phone rebind 양순서 | 삭제선행재응시차단 / 이전선행target보존 |
| R08 | 삭제/상태완료 양순서 | 삭제선행완료재생성0 / 완료금융증거보존 |
| R09 | Transaction 중간실패·unknowncommit·동시중복 | 부분deny0·effects1·sameevent 재확인 |
| R10 | 삭제된성공key replay·새key 생성 | 기존공개404·재생성0 / ACK후 INITIAL·새권리판정 |
| R11 | 새 INITIAL 권리부족·원무료소비 | 기존권리부족·삭제로 복원0 |
| R12 | group 차단이 새pending guard와 경합 | exact oldgroup/session만 변경, 새guard 해제0 |
| V01 | 상세120일 -1ms/정각/+1ms | 기산점별 경계 정확, 시각재설정0 |
| V02 | 24시간/200회격리 및120일경과·미완료/미ACK/hold | 조기격리연장/자동해제0·자동purge0, 담당/검토기한 기록 |
| V03 | 상세삭제·살아있는무료권/복원unique/deny | 중복지급0·재응시차단 유지 |
| V04 | 과거UUID 모든상세제거후 직접재입력 | 보장밖 신규처리위험수용; 영구key 요구0 |
| V05 | old backup 복원 | 원시각/현재deny·expiry·coverage 확인전트래픽0 |
| V06 | Claim3년·금융5년·backup35일 |120일 일괄TTL0·기존정책 유지 |
| V07 | 최종deny/개인연결/무기한권리 최소증거 제거 | 운영결정/공동검토 대기, 정상폐기fixture 보류 |

## 6. 구현 분할안·인계 방법

1. 공동 회신을 원문 기록하고 route/owner/폐기별 수락·보류 표를 작성한다. 상대 회신 없이는 TMI-217 완료로 표시하지 않는다.
2. 확정된 W/A 중심 새 `lc-billing-exam-deletion-retention-r1-fixtures-1`을 제작한다. 가짜 키/토큰만 사용하고 private key/실제사용자 원문 저장0. O/R/V는 명시 NOT_EXECUTED를 유지한다.
3. LC는 ZIP 파일 bytes와 SHA256SUMS 자체 hash를 확인하고 Billing 생성기/검사기를 복사하지 않는 독립 oracle로 대조한다. case ID/건수와 skip을 확인한다.
4. 제품 구현은 (a) 최소owner writer/reader·명시schema (b) 생성/삭제fence 및 삭제sender/receiver (c) INITIAL·Session-only deny/phone 경합 (d) 승인된보존worker 순서로 분리한다. 각 단계 기본OFF, 운영DDL 자동실행0.
5. 공동 실상태 O/R/V·legacy coverage·인증/실환경을 확인한 뒤 별도 활성화한다. 미정 purge와 실패경로를 숨긴 전체PASS 보고는 금지한다.

이번에는 공동 계약안/본 계획/PLAN-011/CURRENT_STATE/WORKLOG만 작성·동기화했다. LC/Identity·제품code·DB·배포·새fixture·Jira쓰기·외부회신 전송 없음. 문서 링크/케이스ID/공백 검사를 수행하며 새 제품코드가 없어 Gradle 재실행은 하지 않는다.


2026-10-14 사용자 승인 반영: 같은 Session의 게스트→회원 단일 이전만 검증한다. 연속 병합을 추적·복구하는 기능은 제외한다. O08/O09는 단일 이전의 성공 근거와 지연/증거누락을 다루며48개 항목은 계속 미실행이다. phone 재가입 및 별개 Session의 귀속 판정은 유지한다.
