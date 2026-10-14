# Billing Service 현재 상태

- 2026-10-14 PLAN012 문서 commit28fdc00·PR24 develop mergec6f8044 완료. 별도 worktree의 `codex/TMI-219-refund-ledger`에서 [환불 코어](../runbooks/TMI-219-refund-ledger.md) 구현: refundEvidence/legacy digest/r7 명시schema, root·금액원장·appliedAt reflow·refundWork 단일Tx. 최종 clean test **1,602개/실패0/오류0/skip0(신규25)**, simplified/R2 offline/hash 및 문서link/diff 통과. 실제 Store adapter/예약·그룹정리/LC전달은 후속, 두flag OFF/processing ON 기동거절. 원 작업 폴더의 TMI217 변경 보존. 신규코드 로컬미커밋, 배포/실DB/Jira변경 없음.

- 2026-10-14 PLAN012 승인 설계·운영 인계·R1 규격 및 TMI-219→220→221 분할을 문서 병합 브랜치로 분리했다. processing/publisher 두 flag 기본 OFF, 기존 schema/RC 검사 유지. 사용자 Git 병합 후 TMI-219 구현 승인; 아직 환불 코드·실DB·배포·활성화는 미완료. 병행 TMI217 작업 폴더는 보존한다.

- 2026-10-14 단일병합계약 commit01d1350·PR22 develop merge3b00a61 완료. `feat/TMI-217-session-owner-evidence`에서 [Session 생성근거](../runbooks/TMI-217-session-creation-owner-evidence.md) 구현: paid runtime 신규Session에 검증user/계정 또는trial subject/epoch 불변저장, legacy추정/backfill0. 전체clean test1,577/실패0/오류0/skip0(신규7포함), 기존offline/diff통과. 삭제권한/route/merge검증/Guest coverage/보존은후속, 기본OFF·실DB/배포변경0. 신규구현미커밋, 병행012문서보존.

- 2026-10-14 사용자 승인: TMI217 UserMerged는 같은 Session의 게스트→회원 단일이전만 확인. [정본draft-3](../contracts/LC-BILLING-EXAM-DELETION-RETENTION-R1.md)에 반영, 연속병합 계보추적/범용복구 제외. phone 별도·지연재확인/증거누락운영확인·원문정리/ACK경계 유지. 검증계획48개 미실행, 문서만 변경.

- 2026-10-14 TMI217 LC 회신 대조: [정본 draft-2](../contracts/LC-BILLING-EXAM-DELETION-RETENTION-R1.md)에서 phone/UserMerged owner분리·검증된동일Session이전인정조건 보완. LC HEAD136602a 작업트리 정적확인: 생성후삭제대기/준비후새학습허용/두purpose signer는 구현차이, eventId보존은coverage미확인. sender/receiver·24시간200회격리우선 정정, 검증계획48개 미실행. 제품/fixture/DB/배포변경0.

- 2026-10-14 TMI217 계약안 commit ee4537c·PR21 develop merge2e646cb 완료. `feat/TMI-217-lc-deletion-handoff`에서 [LC 기술 인계](../contracts/TMI-217-LC-TECHNICAL-HANDOFF.md) 작성. 제품정책은 이미승인·재선택불필요, 신규삭제wire/owner/보존규격은 LC검토대기임을 분리했다. 코드/fixture/배포 변경0, 병행012문서보존.

- 2026-10-14 TMI-216 E06-owner LC 독립 재검증 접수·해소: [인계 후속](../contracts/LC-BILLING-SIMPLIFIED-FIXTURES-2-HANDOFF.md). ZIP10파일/hash 일치, LC oracle82·actual-client49 통과 보고, 전체1,149/실패0/오류0/기존skip1. loopback client이며 실제Billing/TLS/DB E2E 아님. 상태28/T04/삭제보존/실환경gate 유지, fixture 원본/제품code 변경 없음.

- 2026-10-14 TMI216 commit780e677·[PR20](https://github.com/Too-Much-I/app-back-end-billing/pull/20) develop merge89ae574 완료. `feat/TMI-217-deletion-owner-retention-contract` 생성 후 [삭제·owner·보존 공동 계약안](../contracts/LC-BILLING-EXAM-DELETION-RETENTION-R1.md)·[검증 계획](../plans/TMI-217-DELETION-OWNER-RETENTION-VERIFICATION.md) 작성. Jira217은 계약 준비 과제, 신규wire/creationOwner/폐기는 제안·LC 회신 대기. 새제품code/fixture/DB/배포변경0, 문서검사·기존offline재검증통과. 병행012기록보존,217로컬미커밋.

- 2026-10-14 TMI-216 E06-owner fixture 문구 정정: [fixtures-2 인계](../contracts/LC-BILLING-SIMPLIFIED-FIXTURES-2-HANDOFF.md). 기존10파일 보존, 새80벡터·hash9·ZIP10 byte 대조 통과. HTTP409/code/retryable 정책 불변, LC 독립 재검증 대기. 상태28 미실행, 이번 제품 코드 변경 없음.

- 2026-10-14 TMI-215 commit `34ad2f1`·push·[PR19](https://github.com/Too-Much-I/app-back-end-billing/pull/19) develop merge `5d16ba4` 완료. 새 `feat/TMI-216-lc-paid-exam-integration`에서 [Billing 런타임 연결](../runbooks/TMI-216-lc-paid-exam-integration.md) 구현: paid HTTP/lifecycle/expiry·v1 상태event/source별 owner·guard/최소이용원장·통합 read-only reader·구 writer OFF우회 차단. r6 명시schema, 기본OFF 유지. 최종 clean test **1,570/실패0/오류0/skip0**(신규24 포함), frozen simplified/R2 검사·diff검사 통과. [상태28 대응표](../runbooks/TMI-216-shared-state-verification.md)는 부분검증/LC·삭제보존·E2E 미완료를 구분한다. LC HEAD001b575/P2.1·P3진행 읽기 확인만; Guest/legacy source coverage·E06-owner/T04 공동보완·TMI217/PLAN012/013 gate 잔여. TMI216 로컬 미커밋, 제품설정/실DB/배포/활성화/LC·Identity/Jira 변경 없음. 병행 PLAN012/RevenueCat 문서 보존.

- 2026-10-14 [TMI-215 유료 예약·복원](../runbooks/TMI-215-paid-reservation-restoration.md): TMI214 commit3691f98/push·[PR18](https://github.com/Too-Much-I/app-back-end-billing/pull/18) develop mergeebaf8d8 후 feat/TMI-215-paid-reservation-restoration 생성. paid-first/FIFO·source/restart·confirm/복원/원 그룹 차단/receipt·원장 단일Tx, phone target INITIAL, 환불/완료 경합과 rollback 구현. 최종 clean test 1,546개(신규23: 실Mongo22+strict wire1 포함), 실패/오류/skip0. 기본OFF·paid-admission ON거절, HTTP/상태 이벤트/reader/expiry·LC 전체28은216, 삭제보존217·PLAN012/013은 별도. 신규215는 미커밋/미푸시. RevenueCat/PLAN012 별도 dirty 보존, 운영DB/배포/활성화/Jira변경 없음.

- 2026-10-14 [TMI-214 MEMBER 복구 구현](../runbooks/TMI-214-member-evidence-operation-recovery.md): 사용자 요청으로 TMI-213을 3d5660f commit/push·[PR17](https://github.com/Too-Much-I/app-back-end-billing/pull/17) develop merge(bbdd8f4)한 뒤 feat/TMI-214-member-evidence-operation-recovery 생성. purchase-account 최소 MEMBER 증거·탈퇴 공통Tx, 영속 MEMBER 대기/같은 key 재개/J3/status·불변 source snapshot, r2 명시 schema upgrade를 구현했다. final clean test1,523개(신규26 포함) 실패/오류/skip0, offline80/hash·diff통과. 중간 기존 PurchaseCore 동시성 테스트1회 일시503 실패 후 단독/전체재실행 통과; 정확한 일시 원인은 미확정이며 재시도한도 변경 없음. 신규 flag기본OFF·paid admission ON거절 유지.214는 미커밋, 실제 paid resolver/confirm 연결215·LC 전체28/연동216·삭제보존217 후속. 별도 RevenueCat 문서 변경 보존, 운영 DB/배포/활성화/Jira 변경 없음.


- 2026-10-14 [TMI-213 기반 구현](../runbooks/TMI-213-paid-exam-foundation.md): feat/TMI-213-paid-exam-transaction-foundation 생성/전환, 기존 dirty 보존. 동일 ClientSession raw/Spring 저장소 결합·중첩차단·Spring Mongo오류 retry, source reader·receipt/guard CAS·복원 grant/원장 unique 및 명시 schema 구현. 기본 OFF/paid admission 기동거절. 최종 clean test1,497개·신규23(3단위/20실Mongo) 포함, 실패/오류/skip0. offline80/hash·diff통과. 실제 업무28·LC/DB/배포/Jira상태/commit/push 없음. 다음214 MEMBER 증거/대기 연결,215 paid resolver/confirm,216 연동,217 삭제보존.

- 2026-10-14 개인정보처리방침 준비 확인: 금융5년/접수120일/backup35일은 보존 정책이며 실제 배포·삭제 활성화 미확인. 금융 전체5년 purge는 후속, 현 Billing provider는 GET 전용으로 RevenueCat 고객 탈퇴 삭제 미구현. RC 공식 privacy/DPA의 processor 역할·미국 저장·삭제 API 지원 확인; 일반 privacy의6년을 토선생 최종사용자 보존기간으로 전용하지 않음. RC 보존/백업 삭제 SLA·앱 SDK 실제 전송항목·국외이전 근거 확인 후 방침 확정 필요.

- 2026-10-14 사용자 요청으로 PLAN-011 Jira TMI-213~217 생성: Transaction 기반/MEMBER 복구/paid confirm·복원/LC 검증/삭제·보존 공동 계약. TMI-137 parent·해야 할 일·미할당 재조회 확인. 구현 선행 순서는 각 본문에 명시. 코드·기존 이슈 상태·DB·배포 변경 없음. 계획 링크와 WORKLOG 반영.

- 2026-10-14 프론트 시험 시작 오류 확인: Billing reserve와 LC 공개 오류 매핑에서 402 ENTITLEMENT_INSUFFICIENT(사용 가능한 시험 응시권이 없습니다.) 확인. 409 EXAM_CREATION_PROCESSING은 처리 중으로 구분. R2 신규 ENTITLEMENT_REVOKED/EXAM_TRANSITION_CONFLICT는 후속 공동 계약이며 현재 LC 구현 완료로 안내하지 않음. 양 저장소 정적 읽기만·실환경 미검증.

- 2026-10-14 PLAN-011/ADR-004/AGENTS·저장 설계 동기화: exact draft-3 한정 수락·양측 오프라인 완료를 현재 기준으로 반영. §5.0에 P0 단일 ClientSession/schema → P1 MEMBER 증거·영속 대기/receipt → P2/P3 선택·confirm/복원 → LC 연동 순서와 완료 증거 명시. phone 확정 범위와 삭제 route/과거 owner·자료별 purge 보류 분리. 검사기80벡터/hash 및 diff 검사 통과, 상태28/runtime 미실행. 문서만·제품/DB/배포 변경 없음. TMI-116/128/122/125.

- 2026-10-14 단순화 fixture LC 독립 검증 완료 접수(사용자 보고): 원본10파일 bytes/hash 일치·81검사 통과·전체 clean test1,029(포함) 실패/오류/skip0. 양측 오프라인 완료, 상태28/runtime 미실행. 원본manifest/fixture 유지·삭제 route/과거owner/최종보존 별도. 코드/배포 승인 아님. TMI-116/128/122/125.

- 2026-10-14 [단순화 exact 한정 수락/fixtures-1](../contracts/LC-BILLING-PAID-EXAM-SIMPLIFIED-R1-ACCEPTANCE.md): 사용자 승인으로 Billing 수락 기록·10파일/별도Node생성기/Python검사기 제작. errors22/public4/time14/resume29/phone11=80벡터 통과·5변조 거절·덮어쓰기 금지. 상태28 미실행, LC독립/runtime/배포 미완료. 원 A/R2 bytes 유지. TMI-116/128/122/125.

- 2026-10-14 LC 단순화 exact draft-3 추가 이견 없음 접수: 격리 우선순위/E02-f 보완 의견 해소. 다음은 한정 공동 수락→새fixture→hash/LC독립검증. 별도 삭제 route/과거owner/자료별 보존 미확정 유지. 검토 확인이며 제작/구현/배포 승인 아님. TMI-116/128/122/125.

- 2026-10-14 단순화 exact draft-3: NEEDS_REVIEW 기본 운영 확인, 동일 MEMBER 대기→소진 영속 원인 및 현재 안전 조건을 모두 충족한 경우만 제한적 재개 예외로 우선순위 명시. retry_exhausted 단독 불충분·E02-f 보완. 문서만, 코드/fixture/배포 없음. TMI-116/128/122/125.

- 2026-10-14 단순화 exact draft-2: LC 의견 반영 MEMBER 비종료 receipt/status pending·LC PREPARED 대기이유·cleanup 전 분기·owner/lease/version same-key HTTP 재개·안전 cleanup/격리 제한적 재개·E02-a~h 추가. worker reserve 금지, 다른 격리/terminal 되돌림 금지. 신규 규격 공동검토/fixture 미실행, 코드/DB 불변. TMI-116/128/122/125.

- 2026-10-14 [단순화 exact 계약 R1](../contracts/LC-BILLING-PAID-EXAM-SIMPLIFIED-EXACT-R1.md) 작성: MEMBER 내부503/public기존503, 삭제 미접수409·삭제 replay EXAM_4004/빈result생략, UTC120일 최초접수/terminal 경계·7일cache 이후증거, phone INITIAL echo/target복원 unique/경합, E/T/P/C19개 검증영역. 새 규격은 공동검토, fixture 미제작·runtime 미검증. 원본 bytes/코드/DB/배포 불변. TMI-116/128/122/125·LC193/136.

- 2026-10-14 LC·Identity 단순화 정책 동기화 회신 접수(사용자 전달 근거): paid-first/target 복원·삭제 미접수·앱 MEMBER 보강·120일 보장/기간후 위험 수용. 정책 재결정 종료, 영구key 요구 해소. exact 오류/기간 경계/phone 귀속·경합/새 계약·fixture 검증만 후속. Identity 탈퇴 보존 불변. 코드/배포 승인 아님. TMI-116/128/122/125.

- 2026-10-14 [단순화 사용자 승인](../contracts/PLAN-011-SIMPLIFICATION-DECISION-2026-10-14.md): 재가입 paid-first/target 최초trial 복원1회 유지, 생성 중 삭제 미접수·앱 MEMBER 보강·120일 이후 같은-key 보장 제외/종료 상세120일 채택. 금융/소비/복원/미완료 근거 보존 유지, 신규 시작권/영구key 시스템 제외. 문서만·runtime/purge 미실행. TMI-116/128/122/125·LC193/136.

- 2026-10-14 [LC/Identity 회신 반영 R2](../contracts/PLAN-011-REMAINING-TECHNICAL-REVIEW-RESPONSE-R2.md): M1 방향 이견없음 접수·pending/보강, 삭제 checkpoint/교착·오류/귀속, phone echo/복원 수혜 후보 보완. 과거 UUID 재입력은120일/새 issuedAt으로 해결 불가→tombstone purge 미승인, 최초key fence/공개 계약 선택 필요. 문서만·미확정 fixture 생성 없음. TMI-116/128/122/125·LC193/136.

- 2026-10-14 [PLAN011 남은 기술안 R1](../contracts/PLAN-011-REMAINING-TECHNICAL-PROPOSAL-R1.md) 작성: 기존 purchase-account 검증 MEMBER 증거 저장, 기존 LC 삭제 worker+최소 Billing 삭제 route/CAS, phone target paid-first 조합, 상세120일/최소 trial3년·금융5년 연계/미완료TTL없음 후보.120일 후 자동 retry 중단·폐기 gate 명시. 모두 공동 검토 제안, 코드/DB/LC/배포/fixture 변경 없음. TMI-116/128/122/125.

- 2026-10-14 [J4/S15/V07 정책 동기화](../contracts/PLAN-011-J4-S15-V07-DECISIONS-2026-10-14.md): J4 MEMBER 증거 보존/기존 탈퇴, S15 명시 사용자 삭제 시 원 재응시 포기·사용 무료권 미복원/다른 권리 보존, V07 진행 중 유지·종료 후 최소 보존 승인 통합. MEMBER 최초 전달·삭제 전달/경합/new INITIAL wire·phone 선택·보존 exact는 후속. R2/fixtures-2 bytes 보존(새 정책 검증 아님). 문서만 변경. TMI-116/128/122/125.

- 2026-10-14 [J4 ① 사용자 승인](../contracts/PLAN-011-J4-MEMBER-EVIDENCE-DECISION-2026-10-14.md): MEMBER→GUEST 없음·탈퇴 외 상태 없음 전제, Identity 검증 MEMBER 증거 보존 + 기존 탈퇴 이벤트/복구·지연 허용 채택. 매 시험 동기조회/새 전체 상태 동기화 제외. 증거 최초 전달 exact·부재 복구·지연 기준 후속, 코드/DB/배포 변경 없음. TMI-116/128/122/125.

- 2026-10-14 LC J3/J4 draft-2 재검토 후속 접수: J3 요청 보완 반영·추가 이견 없음. J4 미확정 범위 설명 일치, 실제 증거 연동은 Identity 공동 확인 필요. J3 수락/별도 fixture와 J4 연동 계약을 분리 진행, 공동동결/fixture 제작/구현 승인 아님. S15/V07 보류. TMI-116/128/122/125.

- 2026-10-14 LC J3/J4 첨부 재접수: 직전 draft-1 검토 회신과 byte 동일 확인. 새 draft-2 검토 결과 아님. 기존 draft-2 보완 유지, 추가 계약 변경 없음·MEMBER 증거 연동 미확정. TMI-116/128/122/125.

- 2026-10-14 [J3/J4 draft-2](../contracts/LC-BILLING-PAID-EXAM-J3-J4-ADDENDUM-R1.md): LC 회신 반영, PREPARED/새 Session 부재/marker·lease/version·owner 재검증과 local Tx terminal/guard 조건, strict4-field 오류·status 전용 category·owner-blocked/auth circuit 분리 명시. 기존 withdrawal 계약의 local ACTIVE≠실시간 Identity ACTIVE 확인·MEMBER 증언 연동 질문 정리(방식 미선택). 추가 fixture 명세만, 제작/코드/공동수락/배포 없음. TMI-116/128/122/125.

- 2026-10-14 프론트 주소/JWT 안내: 기존 서비스 명명 방식에 맞춘 예정 Base URL `https://billing-test.to-teacher.com` 제안, 실제 DNS/ALB/배포 주소로 검증한 것은 아님. 사용자 JWT는 Identity 발급 시 기존 LC aud를 유지하며 Billing aud/scope 추가하는 승인 계약이며 앱이 aud를 header/query에 붙이지 않음. 실제 Identity 배포/발급 상태 미확인.

- 2026-10-14 [J3/J4 보완안](../contracts/LC-BILLING-PAID-EXAM-J3-J4-ADDENDUM-R1.md) 작성: LC J1/J2 요구 확인 접수·runtime 미검증. pre-reserve terminal 전용 status409/기존4-field 오류·worker reserve 재발행 금지·owner snapshot/version/epoch/lifecycle 표 제안. 신규 code·MEMBER 근거 연결 공동 확인, S15/V07 보류. 원 R2/fixtures-2 불변, 코드/DB/배포/Jira 변경 없음. TMI-116/128/122/125.

- 2026-10-14 머지 준비 상태 확인: feat/TMI-212-revenuecat-worker-reconciliation의 TMI-212 코드 및 LC/PLAN-011 문서·fixture가 미커밋 상태로 혼재. 기본OFF 구현을 통합하는 방향은 가능하나 최종 변경 범위 분리·현재 tree 재테스트·PR/CI 확인 후 머지 권장. 직전1474통과 기록만 확인했으며 이번 재실행/원격 PR 확인/머지는 하지 않음. 판매·기능 활성화와 구분.

- 2026-10-14 프론트용 [Notion 결제 API 문서](https://app.notion.com/p/3f9dcc5dbaeb80618ec9ed3e5d8a5966) 네 개 구성 완료: 사용자 수정 sync는 보존하고 products/purchase-account/entitlements를 제목·요약·상세 토글·오류 표로 추가. 실제 DTO/null/생략/제한과 기본OFF·미배포를 구분. 빈 상품은 []이며 종합 canTakeExam은 현 admission 미구현으로 null fallback임을 명시. 코드 변경·Gradle 실행·배포·Jira쓰기 없음.

- 2026-10-14 PLAN-011 P0 [저장/Transaction 설계](../plans/PLAN-011-STORAGE-TRANSACTION-DESIGN-R1.md)와 [공동 구현 합의안](../contracts/LC-BILLING-PAID-EXAM-R2-IMPLEMENTATION-AGREEMENT-R1.md) 작성. 현 Spring Transaction/PaymentStore ClientSession 분리 확인→동일 session adapter 제안, 기존 저장소 확장·사용자 guard/영속 receipt·복원 source unique·T1~T6 경합/복구 명시. J1~J4·S15/V07 공동 확인 대기. R2/fixture 원본 유지, 코드/DB/LC/Jira/배포 변경 없음. 관련 TMI-116/128/122/125.

- 2026-10-14 프론트 API 목록 정적 확인: Billing 사용자 직접 호출은 products/purchase-account/sync/entitlements 네 개. RevenueCat events는 webhook이며 internal route와 함께 앱 호출 대상에서 제외. 노션 추가 작성·코드 변경 없이 안내, Gradle 미실행.

- 2026-10-14 [PLAN-011 구현 계획](../plans/PLAN-011-paid-reservation-and-usage-evidence.md) R2 기준 재정리: 구 설계 전문 이력 보존, P0~P5·Billing/LC/012/013 책임·실제 검증/활성화 gate 명시. 사용자 전달 LC fixtures-2 독립89/전체948(포함) 통과 접수, 자체 재실행 아님. 공동 최종 동결·제품 구현은 별도, S15/V07 미확정. 계약/fixture bytes·코드/DB/배포/Jira 변경 없음. 관련 TMI-116/128/122/125.

- 2026-10-14 프론트 인계 형식 초안: 사용자 지정 [Notion 결제 페이지](https://app.notion.com/p/3f9dcc5dbaeb80618ec9ed3e5d8a5966)에 `POST /api/v1/payments/sync` 하나만 작성. 실제 코드 기준 인증·요청/응답·오류·재시도·문의 안내와 기본OFF/미배포·SDK 거래ID 매핑 미검증을 구분했다. 사용자 형식 수정 후 나머지 API 작성 예정. 코드/배포/Jira 변경 없음, 문서 작업으로 Gradle 미실행.

- 2026-10-14 TMI-212 [공개 sync 실제 연결](../runbooks/TMI-212-public-sync-wiring.md) 구현: 검증한 worker runtime의 동일 PaymentSyncService를 공개 bean으로 연결. 무조건ON기동거절→worker/fence·기존 public JWT/ingress·실제 key/catalog/schema/budget 검사로 대체, 기본OFF. 실제 Spring context+Mongo+MVC/fake provider 경로와 기동실패/OFF 검증, 최종 `clean test bootJar` **1,474통과/실패0/오류0/skip0(9추가)**. 코드상 공개연결 완료이며 실Store/배포/판매/환불/유료입장 완료 아님. 신규5년정리·Slack ON gate 없음. Jira읽기만·기존동시변경보존·commit/push없음. 다음 PLAN011/012 공동 exact·구현과 실환경 gate 확인.

- 2026-10-14 [다음 작업 확인](../plans/TMI-212-next-work-review-2026-10-14.md): 다음 구현은 TMI-212 public sync service bean 연결·실제 준비 검사. 현재 sync ON 무조건기동거절과 controller service bean 부재 확인(worker 내부 new만 존재). PLAN012 환불은 검토 인계, PLAN011 admission은 unavailable 경계·LC R2 공동검토 상태. 5년 정리 후속 분리 유지. 정적 조사/Jira읽기·문서만, 코드/배포/판매/Jira쓰기 없음.

- 2026-10-14 사용자 승인 반영: [5년 금융 정리](../plans/TMI-212-financial-link-erasure-plan.md)는 장기 후속으로 분리, 현재 결제 출시 선행 조건에서 제외. nullable schema/인덱스 이관 착수 보류, 결제·환불 구현 우선. 최초 만료일/legacy 미확인으로 만료 전 담당·일정 지정 필요, 보존기간 연장 아님. 미구매15일·접수120일·Trial3년·backup35일 유지. 기존 reference 정리 코드/삭제OFF 유지, 다른 rollout gate 불변. 문서만 수정·Jira/코드/DB/배포 변경 없음. 아래 이전 설계·미완료 기록은 이 승인 범위에서 과거 상태다.

- 2026-10-14 TMI-212 [금융 연결 제거 단순화 검토](../plans/TMI-212-financial-link-erasure-simplification-review.md): 최종 보존 종료 전 nullable 중간 상태를 선행 구현하는 범위 과다 지적. 최종 보존안 우선·초기 writer정지 CLI·오래된 예외 기존검토·단일 retention 기준 권장. 실제 최초 만료일 미확인, 보존 유예/정책변경 승인 아님. 코드/기존 계획 미변경, 정적 검토만.

- 2026-10-14 TMI-212 [금융 사용자 연결 제거 설계](../plans/TMI-212-financial-link-erasure-plan.md) 작성: 구매별 nullable 연결·제거 시각·linked-only partial unique·reader-first 이관 권장. 과거 거래 재수신·sync·참조 재생성·원장/source/PLAN011 역연결 보호 포함. 코드/schema 변경 없음. 최종 금융 보존 종료·늦은 반환·sync 오류 exact 미확정, 구현/활성화 완료 아님. 문서/diff/링크 검사, Gradle 미실행.

- 2026-10-14 TMI-212 D3 부분 구현: [만료 조회 참조·lease 정리 r1](../contracts/TMI-212-reference-retention-r1.md). 매시간 단계별100개·기본dry-run, 현재 구매/기간권/원장 보존일·미해결 업무 검사 후 reference만 삭제. 만료lease owner/until 해제·version 증가, 페이지/세대/재시도/검토 보존. 금융 계정 연결 제거는 strict schema/reader 이관 필요로 미완료. `./gradlew clean test bootJar` **1,465통과/실패0/오류0/skip0(10추가)**. 실제 삭제flagOFF, 실DB/배포/Jira쓰기/commit/push없음. 기존 LC·결제 dirty 변경 보존.

- 2026-10-14 [paid fixtures-2](../contracts/LC-BILLING-PAID-EXAM-FIXTURES-2-VALIDATION.md): LC지적 escape/floatclock수정·문자열회귀9/tokenns경계9/clock5추가. canonical18/invalid13/token31/clock5·13파일hash 통과, 구도구mutation실패확인. fixtures1모든bytes불변·LC독립/runtime미완료.

- 2026-10-14 TMI-212 운영 재개 CLI 구현: [실행 안내/exact](../runbooks/TMI-212-payment-review-operator.md). 독립 `paymentReviewOperator` inspect/dry-run/resume/result/schema, account와공유AUTH분리·기본무변경·명시apply/격리확인. Mongo인증principal digest·작업ID/표준사유·같은Tx receipt/중복수렴·unknown결과조회. 새receipt-r1명시설치필수,기존sync/cursor/API불변. 최종 `clean test bootJar` **1,455통과/실패0/오류0/skip0(26추가)**. 운영자개인권한/실환경실행·감사보존확정·5년physicalpurge/cursor정리후속,배포/활성화/실DB/Jira쓰기/commit/push없음. 기존LC동시작업보존.

- 2026-10-14 [paid R2 fixtures-1](../contracts/LC-BILLING-PAID-EXAM-FIXTURES-1-VALIDATION.md) 제작: 12파일hash·canonical9/invalid13/token22/HTTP판정표12·wire6/status3 별도Python/OpenSSL검사성공. 상태시나리오26은미실행, S15/V07보류. LC독립검증/실HTTP/업무테스트미완료·제품코드DB변경없음.

- 2026-10-14 LC 통합R2 주요기술1~3 이견없음 접수, draft-2 회귀3항목 반영. 명칭 재선택불필요·S15/V07/보존지원조합담당확인후속. fixture제작/독립검증/구현승인미완료·문서만.

- 2026-10-14 TMI-212 운영 재개 범위 확인: [검토](../plans/TMI-212-operator-gap-review-2026-10-14.md). 계정재개/core CAS 및 project AUTH해제 library는 존재하나 CLI·실행권한·감사receipt 연결미완료. 독립 inspect/dry-run/apply·최소원자감사·AUTH별도명령 권장, 금융강제승인/새관리자API 제외. ALL_BLOCKED와writer격리구별필요. 정적검토/문서만,코드·Jira·운영실행·Gradle재실행없음.

- 2026-10-14 [LC paid 통합 R2](../contracts/LC-BILLING-PAID-EXAM-CONTRACT-R2.md) 작성: 현재규칙단일정본·변경이력분리, 세startReason/단일mock·공통transition·불변revoke/event·group양unique·terminalAt. S16/V7 총23개검증영역·phone삭제보존명시미확정. 문서검사만, fixture생성/실행/코드DB배포없음.

- 2026-10-14 Slack 서버 연결 후속: AWS 재로그인 후 서울 리전의 현재 staging 클러스터 서비스10개를 확인했으나 Billing 이름의 서비스는 없었다. 다른클러스터/리전/별도호스트 부재까지단정하지않음. 사용자에게 Billing 테스트서버배포여부/대상확인필요. Secret등록·실발송·배포·활성화없음.

- 2026-10-14 TMI-212 Slack 외부 설정: 사용자 승인으로 비공개 billing-alerts-test 채널과 Billing Alerts Test 앱을 생성하고 사용자의 허용 후 Incoming Webhook 연결 성공을 UI로 확인했다. URL 원문은 기록하지 않았다. 서버 secret 등록·테스트 발송 요청에 AWS 확인을 시작했으나 세션 만료로 로그인 화면에서 대기. 현재 Billing 배포 대상/secret 위치 미확인, secret 저장·실제 발송·서버 활성화·배포 없음.

- 2026-10-14 사용자확정: 전체접수·요약대기 새시험 직전mock제외, 다른적격후보0이면동일허용·조회장애fallback금지. 부분접수동일/완료후기존선정 유지. 인계/PLAN011/결정서동기화, LC구현/DB변경없음.

- 2026-10-14 TMI-212 Slack 전송 구현: [연결 안내](../runbooks/TMI-212-slack-alerts.md). PaymentSlackSink/Properties/Configuration과환경변수추가, Incoming Webhook 기본OFF·공식HTTPS host만허용·2s연결/5s전체대기/2KiB응답·redirect/proxy차단·429/503 Retry-After·설정오류프로세스내보류. 기존privacy-safe provider요약에연결하며CleanupAlerts는별도. `clean test bootJar` **1,429통과/실패0/오류0/skip0(21추가)**. 실제채널/secret/담당자/전송검증·배포활성화는후속,업무API/schema/금융처리불변. 실Slack호출·Jira쓰기·commit/push없음. 아래D2의Slack adapter미구현표현은이항목으로갱신하며D전체완료아님.

- 2026-10-14 [LC 단순화 인계](../contracts/LC-BILLING-PAID-EXAM-SIMPLIFICATION-HANDOFF-2026-10-14.md) 작성: 부분접수같은문제/전체접수대기다음문제·단일후보, 공통LC마무리, 불변그룹revoke·terminalAt재사용. 최초trial복원1회유지. 최소시작사유wire/phone/보존은공동확인후정본통합·기존fixture명세수정필요. 문서만.

- 2026-10-14 TMI-212 D2 구현: [운영 안내](../runbooks/TMI-212-reconciliation-and-review.md). 정기계정조회(최근90일/활성·예정권리,한claim한페이지/24h/1000페이지), 원장 기반source TTL후sync완료, 선택lookup재개/generation·새24h예산, 구매별AES-GCM참조저장, privacy-safe운영요약/전송인터페이스. sync-r2/cursor-r2/reference전용collection명시이관·기본OFF. 최종 `clean test bootJar` **1,408통과/실패0/오류0/skip0(20추가)**. 실제Slack adapter/채널·운영도구권한·5년physicalpurge/cursor정리·실환경부하/Store검증은남아있고D전체/판매완료아님. 공개sync ON차단유지,운영DB/배포/Jira쓰기/commit/push없음. 기존LC문서변경보존.

- 2026-10-14 LC wire draft-3: LC draft-2 재검토의 주요방향 이견없음 접수, §1 status body조회/command header범위 및 §8 기존409/503·신규두409 표현 정리. 공동동결/fixture실행은미완료·문서만.

- 2026-10-14 TMI-212 D1 보존·정리 기반: [규격](../contracts/TMI-212-retention-r1.md). PaymentRetentionWorker가 worker runtime에서 시간당 단계별 최대100건 RECEIVED119일 case 인계와 terminal sync120일 대상 점검을 수행한다. 실제 sync삭제는 독립 기본OFF, 최초epoch/OPEN/version/state/count 재검사. 원장·계정·case·미해결sync 보존, schema/index/외부DTO 불변. `clean test bootJar` **1,388통과/실패0/오류0/skip0(8추가)**. D전체미완료: 정기대사/reference영속화·terminal inbox TTL와미완료sync 연결보호·운영재개/외부알림 후속. 공개sync ON차단·판매/205삭제OFF유지. 실DB/배포/Jira쓰기/commit/push없음.

- 2026-10-14 LC wire draft-2 검토보완: 기존pending오류유지, replay/삭제continuation/조합/영속거절/digest/역순예외 구체화·32fixture명세. 공동수락/실fixture/테스트/구현미완료·문서만.

- 2026-10-13 TMI-212 C2b 처리 경로 구현: [안내](../runbooks/TMI-212-account-lookup.md), [exact](../contracts/TMI-212-account-lookup-r1.md). 계정별 ACCOUNT_LOOKUP lease/암호화 페이지 checkpoint·기존 source 우선 완료·실RC read adapter+공유 budget 배선·Retry-After/24h 검토/AUTH 인계·기본OFF 전용 runtime. sync-r1 유지, cursor-r1 명시 validator 이관,205 lookup잠금 삭제보호. 최종 clean test bootJar **1,380통과/실패0/오류0/skip0(24추가)**. 공개sync ON은 운영/보존 gate전 계속 거절. D 정기대사·구매별reference영속화·보존/알림/계정재개 및 실환경/부하/Store검증 후속. 기존 동시 LC/정책 문서 보존·운영DB/배포/Jira쓰기/Git없음.

- 2026-10-13 TMI-212 C2a 외부 조회 기반 구현: [안내](../runbooks/TMI-212-provider-dispatch.md), [규격](../contracts/TMI-212-provider-dispatch-r1.md). core.receive 최초 epoch/같은Tx lease guard, 공용 Mongo project rolling 240회/60초·slot4/30초·AUTH 영속 차단·명시 재개/strict schema verify. clean test bootJar **1,356통과/실패0/오류0/skip0(18추가)**. 명시 생성형·runtime 미연결/OFF·syncON차단유지. C2 계정scan/합치기/페이지checkpoint/sync재시도·공유DB배선과D는남음. 실제RC/Store·운영DB/배포/Jira쓰기/Git없음, 동시LC/무료권문서 보존.

- 최종 갱신일: 2026-10-13
- 2026-10-13 [LC paid wire R1 초안](../contracts/LC-BILLING-PAID-EXAM-WIRE-R1.md) 작성: restartContext/transition/source·두문제후보·CANCELED+confirmRejection·LC503/409·역방향revoke profile/digest/보존·공동fixture22항목. 신규계약후보, LC수락/구현/실fixture생성·검증 미완료. 문서만.
- 2026-10-13 LC 후속검토 §8 반영: R2 §10.2 새Job차단을 사용자 신규제출 유래로 한정. 기접수 문항의 종료후 Summary 최초생성/시스템재시도는 원접수증거·멱등/lifecycle조건으로 허용. 정책/fixture문구동기화·문서만, 미확정wire/인증/보존은 공동확정후속.
- 2026-10-13 LC 마무리 설계 승인: 기존operation에서 LC종료까지복구·로컬마무리commit후성공응답, 접수Job만완료·미완료TTL금지, legacy ID목록 우선검토. R2 TRIALfallback/일반무중첩 정리·정책/계획동기화, 코드/DB변경없음.
- 2026-10-13 사용자 승인: LC 기존시험 유지·일괄이관/종료없음, 전환후 신규/새재응시 Billing/source필수. source누락이 아닌 전환전 대상증거로 legacy구분, 기존이력을 소비/복원으로임의연결금지. 점검/PLAN011·013/R2/결정서 반영·문서만.
- 2026-10-13 [prod source inventory](../plans/PLAN-011-SOURCE-PRODUCTION-INVENTORY-2026-10-13.md): LC Session1493 모두source/예약/group연결없음, operation collection없음. 진행136·active상태누락10. 일괄EXAM_PASS이관불가 판단·기존history/신규Billing경계 검토. 사용자가 Billing prod DB 미구축 확인: Billing backfill불필요·신규schema/writer필수값 적용 준비, LC legacy처리만후속. 읽기전용·DB수정없음.
- 2026-10-13 TMI-212 C1 source worker 구현·검증: [안내](../runbooks/TMI-212-source-worker.md), [규격](../contracts/TMI-212-worker-r1.md). inbox30초lease/최초epoch동일Tx검사·coreapply→동일거래sync완료·crash회수·최대100source/100command분할·backoff/24hcase인계·r3→r4명시이관. clean test bootJar **1,338통과/실패0/오류0/skip0(14추가)**. 신규scheduler/외부HTTP없음·syncON차단유지. C2 API계정scan/공유quota/sync독립retry/runtime과D대사/reference영속화/운영보존미완료. 동시무료권/LC정책문서보존·Jira쓰기/타서버/배포/Git없음.
- 2026-10-13 [source 일회성 이관 검토](../plans/PLAN-011-SOURCE-MIGRATION-REVIEW-2026-10-13.md): 코드상 조건부 가능, reserve 성공 이후 필수. 실제 DB건수/미연결legacy 확인 전 전체이관 가능 확정 아님. 읽기 검토·문서만.
- 2026-10-13 무료권 단순화 승인: Billing paid확정/복원/원group재응시차단 원자처리→LC 순차종료. 기존접수 경합완료+복원권 허용, 최초trial consumption당1회, 현재무기한/지급순·만료기능후속. MEMBER보상권 Guest merge확장제외. 정책/R2/계획/ADR·AGENTS 동기화, 문서만 변경.
- 2026-10-13 무료권 추가 정책 확정: 만료순/지급순·복원권 무기한·EXAM_PASS/SUBSCRIPTION, 보상권 계정귀속/재가입 자동복구없음/정상merge 미사용이전, confirm·차단·복원 같은Tx 및 LC status-first 복구. exact 이관/병합경합/보존 후속. 문서만 변경·동시 결제 구현 보존.
- 2026-10-13 TMI-212 B webhook 수신 구현·검증: [안내](../runbooks/TMI-212-webhook.md), [규격](../contracts/TMI-212-webhook-r1.md). 전용 host/local-port/security chain·Authorization+raw HMAC/300초·256KiB/strict UTF-8 JSON·known 의미digest·기존 inbox의RECEIVED/IGNORED/case접수와pending보존, r2→r3명시재개가능이관. HTTP에서지급/외부조회없음, 기본OFF·syncON차단유지. clean test bootJar **1,324통과/실패0/오류0/skip0(56추가)**. Cworker/D대사·reference영속화/운영검토·알림·보존/실환경은미완료, TMI212전체완료아님. 동시LC/무료권정책문서보존·Jira쓰기/Git/배포없음.
- 2026-10-13 [공통 무료권·유료 전환 복원](../contracts/FREE-EXAM-PASS-AND-PAID-SWITCH-RESTORATION-2026-10-13.md) 사용자 승인 반영: 출처별 grant/ledger, paid confirm 시 미완료 최초 trial 종료+root당 복원1개, 원Claim/consumption보존. 종전 무료replacement우선 대체·초대보상구현제외·exact wire/schema/LC공동계약 후속. 문서만 변경, 동시 PLAN010 코드 보존.
- 2026-10-13 무료도중구매 시 재응시보존 명확화: R2§9/PLAN011/결정서에 유효TRIAL replacement유지·구매만으로paid group전환금지·paid-first신규INITIAL만·paid기간재기산없음 반영. 이전다른권리전환은환불/만료재응시불가에한정. 코드/배포변경없음·문서검사만.
- 2026-10-13 기존 새시험 흐름 통한 다른권리 새group전환 사용자승인: R2§2/8·PLAN011·결정서 반영. 별도포기버튼없음·유효동일source replacement/환불source차단·다른권리INITIAL 새group/원source보존. 실패시이전Session선종료금지·durable전환/CAS/status-first 공동설계후속. 코드/LC/외부전송/배포변경없음·문서검사만.
- 2026-10-13 TMI212 선행A 구현·검증: [안내](../runbooks/TMI-212-revenuecat-adapter.md). v2 readclient/strictdecoder·stableAPIkey·boundedHTTP·reference암호화helper·null→known금액보완·core-r2명시이관. clean test bootJar **1,268통과/실패0/오류0/skip0**(53추가). 금액보완은기간불변,known충돌검토. runtime미연결/syncON차단유지. **TMI212전체미완료**:Bwebhook/Cworker/D대사·reference영속화/보존/실환경후속. 동시LC문서보존·Jira쓰기/배포/Git조작없음.
- 2026-10-13 LC 인계 잔여항목 재검토: [기록](../plans/LC-BILLING-PAID-HANDOFF-READINESS-REVIEW-2026-10-13.md). command TTL후confirm/status영속복구·RESERVED보상경합·higher revoke 예외제거·보존충돌 확인. R2에exact예외고정/terminal증거보상·새120일eventage기본안철회·inbox/권한보존분리 보완. LC신규receiver/오류/source·장기OPEN guard행동 공동확정후속. 코드/외부전송/타저장소/배포변경없음·문서검사만·010동시변경보존.
- 2026-10-13 LC R1 검토 보완: [R2 후보](../contracts/LC-BILLING-PAID-EXAM-HANDOFF-R2.md). source operation보존/legacy증명조건·confirm성공확인전보상금지·오류mapper/public후보·최대16KiB/독립역방향JWT·ID/version충돌우선·120일보존/age/lifecycle후보 작성. 새제출timeout없음 사용자확인·작성자30/60분/24h후보철회·현행grading제한별개,양측수락/fixture/구현승인미완료. 코드/LC/배포/외부전송없음·정적검사만.
- 2026-10-13 사용자 승인으로 [TMI-212](https://to-teacher.atlassian.net/browse/TMI-212) 생성: PLAN010 3차 RevenueCat 검증/webhook/worker/누락복구, parent TMI137. 금액만불명인검증구매는지급후금액증거보완·기간재지급금지,기본OFF·실환경/011/012후속 포함. 계획에키연결·기록,기존이슈상태/코드/배포변경없음.
- 2026-10-13 3차계획 주의사항 설명: 검증된 구매/상품/귀속과 금액 보완을 분리해 기간 재지급 없이 기록추가,조회실패/빈목록은기존권리보존·신규미검증PENDING,재시도예산소진은검토인계·자동환불/삭제아님을 설명. 권장안설명만·신규승인/코드변경없음.
- 2026-10-13 LC 유료 시험 인계 R1 준비: [초안](../contracts/LC-BILLING-PAID-EXAM-HANDOFF-R1.md). 기존reserve/status source필드·confirm/refund순서·confirmedSessionId revoke후보·auth/오류/retry·기존event증거 재사용·분담/reader-first/PAID14 공동테스트 작성. 제품정책과새wire후보 구분·양측수락후속, 외부전송/LC수정/코드/배포없음·문서검사만.
- 2026-10-13 PLAN010 3차 사용자 설명: 접수/API와 원장 사이 실제RC 연결, adapter/webhook/worker/누락복구 역할·hash-only대조·1초DBpoll과외부조회분리·중복방지/금액보완·011/012후속을 설명했다. 계획 재조회와 기록만, 기술안 추가승인/코드/Jira/실환경 변경 없음·테스트미실행.
- 2026-10-13 PLAN010 [3차 계획](../plans/PLAN-010-phase-3-revenuecat-worker-reconciliation.md) 작성: 공식문서상 Authorization+HMAC·v2 구매목록/검색/단건·Android nullable orderId/iOS transactionIdentifier 확인. 실제 dashboard/SDK버전/실거래 미확인. hash-only sync는 계정목록+HMAC대조 권장, adapter→ingress→worker→reconciliation/보존으로 분할. source key/금액null보완·worker strict schema 이관·shared quota·기본OFF/011·012판매gate 명시. 코드/ADR정책변경·Jira·실설정·배포없음, 문서검사만·Gradle미실행.
- 2026-10-13 TMI-211 병합 후 다음 단계 확인: 로컬 HEAD ea30a62에 PR15 merge/80526e9 포함, 점검 시작시 clean. 다음은 PLAN010 provider 실제 인증·SDK 거래 식별자 대응 확인→3차 RevenueCat adapter/webhook·공통 worker/reconciliation 상세 계획이다. HMAC 지원은 아직 실증되지 않은 목표이므로 지원 사실을 가정하지 않는다. 011/012·실판매 활성화는 후속, 코드/Jira/배포 변경·테스트 재실행 없음.
- 2026-10-13 기존 상태이벤트 이용증거 재사용 정적검토: [결과](../plans/PLAN-011-EXISTING-EVIDENCE-REVIEW-2026-10-13.md). LC v1 완료evidence/실패code 이미발신·Billing strict검증 확인, 최소증거용 새wire버전 불필요 권장. Billing은 상세evidence/failure 미저장·inbox120일/digest만, paid 귀속/source·consumer분기·장기증거 Transaction저장 필요. 코드/LC/계약동결/배포변경없음·Gradle미실행.
- 2026-10-13 TMI-211 OFF 구현·로컬 검증 완료: [구현 안내](../runbooks/TMI-211-public-sync-entitlement-query.md). sync strict HTTP/인증/멱등 접수·command/pending 원자성·source completion, 무료/유료 동일 snapshot·기간 조회·nullable canTakeExam, 분산 quota/명시 schema/기동 gate 구현. clean test bootJar **1,215통과/실패0/오류0/skip0**(기존 대비47추가), diff 검사 통과. 실제 provider/worker 미연결로 sync ON 기동 거절, admission 미연결 종합 null/PENDING; 두 flag 기본OFF. 실제 RC/Store·011/012·205 실금융/배포는 후속. 동시 환불 정책 문서 변경 보존, Jira수정·commit/push·타서버/배포 없음.
- 2026-10-13 환불 전 confirm exact Session 완료 허용 사용자 승인: [정책 변경](../contracts/PAYMENT-CONFIRMED-SESSION-REFUND-POLICY-2026-10-13.md). paid 채점승인API 제외, 환불선행confirm/신규/replacement 차단·durable revoke/최소이용증거 유지. 기존reserve응답 출처추가 방향 채택, exact wire/LC 공동수락후속. ADR/PLAN010~013/계약/AGENTS 상단 우선적용 반영·역사기록 보존. 코드/타저장소/배포미변경·문서검사만.
- 2026-10-13 사용자 승인으로 [TMI-211](https://to-teacher.atlassian.net/browse/TMI-211) 생성: 「[Billing PLAN-010 2차] 결제 sync·무료/유료 통합 조회 API 구현」. TMI-137 상위/작업/해야 할 일/미할당 재조회 확인,선행210. canTakeExam true/false/null·sync/통합reader·기본OFF·회귀/후속gate 포함. 기존이슈 상태변경/구현/배포 없음,계획에키 연결.
- 2026-10-13 사용자 응시가능 boolean 요청 반영: 2차 계획/purchase-r1에 examAvailability.canTakeExam 추가. ALLOWED=true/BLOCKED=false/PENDING=null 파생 권장안, 새시작/승인재응시 요약이며 진행중/결과열람 아님. sync의 앱신호와 서버검증·request key/sub·접수기록/asOf 의미 설명. 문서만,실코드/API/Jira/배포변경 없음.
- 2026-10-13 PLAN-010 2차 상세 설명: sync는 확인 접수/결과 조회, 통합 reader는 권리/기간 표시이며 Store 결제창·실검증·paid reserve 구현과 구분해 안내. command/source pending은 구매 수가 아니고,011미연결 종합PENDING과 기존무료 불변·기본OFF·기술후보 미동결을 설명. 기록만 추가,계획/코드/정책/Jira/테스트/배포 변경 없음.
- 2026-10-13 PLAN-010 2차 상세 계획 작성: [sync·통합조회 계획](../plans/PLAN-010-phase-2-public-sync-and-entitlement-query.md). local develop726b5ca에 PR14/210포함 확인, 작업전 clean. command/pending·202/200·DTO/null/오류·동일snapshot·guard미준비PENDING·OFF/ON분산limiter이관·fake/runtimegate·단계/테스트 정리. 기술 후보는 검토/동결 후 구현, 실제 provider/worker·011/012/205 후속. 문서만·Jira/코드/테스트재실행/commit/push/배포 없음.
- 2026-10-13 다음 작업 안내: TMI-210 코어 검토/사용자 Git 정리 후 PLAN-010 2차 public sync·기존 entitlement 통합 조회의 exact DTO/호환·기본OFF 구현을 권장. 실제 provider/공통worker→205 실금융 연결은 후속. 이번 상태 확인/기록만이며 신규 Jira·구현·테스트 재실행·Git 변경 없음.
- 2026-10-13 TMI-210 내부 구매 코어 구현·로컬 검증 완료: [범위/규격/검증](../runbooks/TMI-210-purchase-core.md). 정규화 관측·고정 HMAC lookup·접수 중심 pending/case 인계·Purchase/기간권/timeline/원장·mandatory PAYMENT_INPUT/APPLY fence·명시 schema 설치/검증. 중복/5기간/탈퇴/환불선행/삭제 양쪽 순서·장애/동시성 41개 추가, clean test bootJar **1,168통과/실패0/오류0/skip0**. Spring bean/HTTP/worker 미연결, 실제 RC/Store·sync/통합 reader·012 환불·205 실금융 coverage·배포는 후속. Jira/commit/push/타서버 변경 없음. 기존 WORKLOG 반복 기록·별도 RevenueCat 문서는 사용자 요청대로 보존.
- 2026-10-13 사용자 브랜치 이동 확인: 현재feat/TMI-210-purchase-ingestion-timeline-core, HEAD=d4f290e(PR13 merge), 로컬develop/origin/develop과동일·209최종cfdbf3f포함.210신규커밋/코드변경없어착수기반정상. 점검전WORKLOG에기존기록반복추가115줄과미추적RevenueCat등록문서잔존,임의정리/커밋하지않음. 이번은로컬확인·기록만,fetch/branch전환/구현/commit/push없음.
- 2026-10-13 PR 생성 완료: [PR #13](https://github.com/Too-Much-I/app-back-end-billing/pull/13), develop←feat/TMI-209-billing-cleanup-foundation. cleanup65bb8dd/LC JWT379d543/결제문서7ebfae8 세범위커밋push·PR생성/연결확인. 전체1,127테스트및bootJar통과. 원문복구백업보존,별도RevenueCat등록문서·기록은미커밋유지. PR병합/배포/210분기·구현/Jira상태변경없음.
- 2026-10-13 커밋·PR 준비: 이번 작업에 한해 사용자 commit/push/PR 예외 승인. 기존209cleanup/LC JWT/결제문서를범위별커밋으로분리, 전체 clean test bootJar 1,127통과/실패0/오류0/skip0·fixture SHA256검사통과. WORKLOG/LC검토서덮어쓰기복구승인후백업·Git원문/새기록보존.210코어/배포/병합없음. 동시작업의RevenueCat Google등록문서·기록은커밋제외,PR생성은후속결과기록참조.
- 2026-10-13 변경 분류·Git 예외 범위 확인 중: 사용자「이번작업에 한해서 해줘」의 commit/push 예외 범위를 질문했다.209cleanup,LC JWT,결제문서,clock회귀/공동운영문서로분류 가능하나application.yml·AGENTS·통합계약·기록은hunk단위검토필요. WORKLOG에기존약5005줄삭제/1576줄추가가관측돼과거기록정리는의도확인전커밋대상확정하지않음. 상태기록외파일보존,stage/commit/branch/push없음.
- 2026-10-13 TMI-210 브랜치 착수 전 점검: 현재209브랜치와 로컬develop은ee164ac이며209 전용커밋 없이 cleanup/LC JWT/결제문서 미커밋변경이 같은checkout에 있다.210브랜치는 아직없음. 즉시switch -c는 변경을분리하지 않으므로 기존변경을범위별검토/사용자커밋·PR정리한뒤병합된develop에서210분기 권장. 원격최신상태는fetch하지않았으며로컬관측만. Git상태/브랜치수정없음.
- 2026-10-13 Jira 우선 생성: [TMI-210](https://to-teacher.atlassian.net/browse/TMI-210) 「[Billing PLAN-010 1차] 접수 중심 구매·기간권 원장 코어 구현」을 TMI-137 에픽 하위 작업으로 생성하고 parent/해야 할 일 상태 재조회 확인. 접수규격/pending·구매/기간권/ledger·멱등/탈퇴경합·fake/Mongo 테스트를 범위로 기록. 기존이슈수정/상태전환없음. 직전 구현 요청은 Jira 우선 요청으로 중단했고 실제 신규 코드편집은 아직 없음. PLAN010 링크/기록만 갱신, 다음은 TMI210 구현 재개.
- 2026-10-13 다음 작업 안내: 승인된 단순안을 기준으로010 첫 slice의 접수/case 최소schema·index·원자pending 규격을 기존 문서에서 닫고 기본OFF 구매/기간권 코어를 구현하는 순서를 권장. 이어sync/통합조회→공통worker/provider실증·부하검증→205실증거 연결/정책revision이관. 사용자에게 새제품정책 재선택을 요구하지 않으며 미해결case 보존 등 정책 영향 발생 시에만 별도확인. 이번은 안내/정적확인, 구현승인·코드변경 아님.
- 2026-10-13 단순화1~4 방향 승인·문서 반영: PLAN010/상세규격/결정서에 접수row 중심·공통background/초기1초poll·고정lookup키를 반영하고205규격에 단일cleanupPolicyVersion 목표/기존schema 이관 필요를 명시했다. 빈큐 무외부호출·bounded due index·backoff/lease·staging부하검증 유지. 앱 Retry-After:5는불변이라 이전2~5초화면예시는보장이아님. 코드/manifest/Jira/배포미변경, 실제provider/부하·205이관후속.
- 2026-10-13 PLAN-010 1초poll 부하 정적 검토: 구매 worker는 미구현이므로 실측 없음. q개 due 조회×N인스턴스/T초로 추산하며 두 큐 각1조회·서버2대 가정은1초4조회/초 대5초0.8조회/초(lease/실처리 제외). due index·소량조회·빈큐 무외부호출/무갱신·중첩 방지·재시도 nextAttemptAt/합산quota 유지 조건에서1초는 검증 가능한 후보이나 실DB explain/지연/CPU/경합 측정 전 안전성·응답시간 보장하지 않음. 기존240회/분·동시4는 내부 초안budget이며 RC 실제 허용량 확인과 구분. 설정/코드 변경 없음.
- 2026-10-13 사용자 선택 승인: 1번 접수 기록 중심(pending 원자 갱신/예외 case 유지), 3번 cleanupPolicyVersion 단일화, 4번 고정 lookup 키+keyVersion/교체 자동화 제외를 승인했다. 2번 worker-only는 지연 설명 후 결정 예정이며 아직 승인 아님. 현재 초안5초 tick의 대기는 적체 없는 첫 스캔 기준0~5초+provider/DB+앱 재조회이고 SLA/실측이 아니다. 코드·schema/정본 기술규격의 상세 동기화·이관은 후속이며 승인 기록만 반영.
- 2026-10-13 단순화안 기존안 대비 설명: 접수 중심 안도 pendingEvidence 카운터/원자 갱신은 유지하며 범용 effect 중복 기록만 축소하는 후보임을 명확화. worker 단일화는 프로세스 한 대 강제가 아니라 공통 처리 경로, 정책 통합은 앱 전체 빌드가 아닌 cleanupPolicyVersion, 키 교체는 거래 lookup hash 키 대상(사용자/workload JWT 키 전반 변경 아님). 기존 온라인 자동 교체가 구현돼 있다는 의미를 정정·구분. 설명/기록만, 승인·계약·코드·Jira 변경 없음.
- 2026-10-13 복잡도 단순화 검토: PLAN-010 초안 §5.6의 source/effect/pending 중복 상태와 §7의 sync 즉시 조회+worker 이중 실행을 우선 축소 후보로 제안. source 상태의 원자적 pending 갱신+필요한 미매핑 case만 보존, 신규 sync는 durable 접수 후 공통 worker 처리(이미 반영된 건은 즉시 반환)를 권장한다. 구현된205의 Coverage 세 버전/FinalPolicy는 하나의 배포형 정책 revision으로 통합할 가능성을 검토하되 실제 provider coverage 검증·삭제 직전 CAS/lease/증거 유효성은 유지한다. 온라인 key 회전 자동화는 초기 필수 개발에서 제외하고 점검 중 수동 이관을 후보로 제안. 모두 미승인 검토안이며 코드/정본 계약/flags/Jira 변경 없음. 정적 분석만 수행, Gradle 미실행.
- 2026-10-13 공동 테스트 환경 검증 범위 정리(사용자「한번에 다른 거까지 검증하자」): [체크리스트](../runbooks/BILLING-JOINT-STAGING-VALIDATION-2026-10-13.md). JWT/네트워크·무료 시험/retry·Identity 이벤트/복구·조회/계정 기반·탈퇴/owner·관측을 준비 상태에 따라 함께 검증. 미구현 결제/환불·C 재처리·실삭제는 별도 gate, 실환경 결과 전부 미실행. 문서만 작성·타저장소/배포/flags/DB/Jira/commit/push 변경 없음·Gradle 재실행 없음.
- 2026-10-13 PLAN-010 상세 기술 규격 초안 작성: [purchase-r1](../contracts/PLAN-010-purchase-technical-spec-r1.md). 구매/기간권/ledger·business멱등·불변digest·lifecycle/pending effect·탈퇴후review/미매핑격리·sync/통합paid+examAvailability 후보·index/보존·OFF단계/테스트 명시. 무료필드와paid-first보존,정상기간/환불·재가입정책불변. DTO/신규effect·writer는검토안,실provider/crypto/미결보존과011/012·205/206 gate남음. 문서/정적검사만,코드/Jira/외부조회/Gradle없음. 동시LC작업보존.
- 2026-10-13 LC JWT Billing B 로컬 구현완료(사용자「어해줘」,TMI116/128/122): [검증결과](../contracts/LC-BILLING-WORKLOAD-JWT-B-BILLING-VALIDATION-2026-10-13.md). LC독립publickey/receiver·sharedTLS OR기동·fixedissuer verifierregistry·6POST exact인가/guard·IAM/TEST/다른port 우회제거. actualSpringchain HTTP27·filter27·key/flags/legacy negative와 productionJWT controller회귀, clean test+bootJar **1,127개 통과/실패0/오류0/skip0**. 최종 test/bootJar도1,127개 동일통과. 초기2test의새OFF404/unsigned401기대보완. 기본OFF/빈실키·A fixturehash불변, Identity/user·body/원장/retry/TTL불변. 실제양측TLS/키배포/E2E·C도구·배포/활성화별도, 타저장소/Jira쓰기/외부전송/commit/push없음·기존별도작업보존.
- 2026-10-13 통합 entitlement 조회 사용자 승인 반영: paid 전용 GET /api/v1/payments/entitlement 신설 제외, 기존 GET /api/v1/entitlements 무료·유료·시험이용가능성 호환확장. PLAN010/007/008/013·ADR004·결정서·요약/통합계약·AGENTS 정합화. 무료 필드/의미·read scope/본인조회·no-store·reserve최종판정·sync분리 유지. exact DTO/null/flag·frontend호환/rollout 후속, 기존코드/OpenAPI/설정/Jira/배포변경없음. 문서검사만/Gradle미실행.
- 2026-10-13 LC B 갱신후 Billing 잔여 확인: LC commit3d50195 두JWT sender·계획/ADR/AGENTS·startup/index gate 구현을읽기확인, LC test859/Mongo146 XML 실패·오류·skip0과 fixtures1동일bytes 확인. Billing은 Identity-only WorkloadJwtProperties/chain과 LC LATTICE_AWS_IAM permitAll이남아 verifier registry·LC독립receiver/키·TLS OR기동·6routeguard/IAM우회제거·HTTP27 구현필요. 추가 A계약변경 발견없음(범위정적확인), Billing B코드착수/배포미수행. 계획최신상태/기록만갱신·타저장소미수정·테스트재실행없음.
- 2026-10-13 PLAN-010 착수 정적 검토: [검토](../plans/PLAN-010-READINESS-REVIEW-2026-10-13.md). 실제구매/기간권/RC/sync·paid reader미구현 확인, 준비규격→fake구매코어→앱API→실수신/복구→205PR3 권장. provider 과거문서조사와 실검증 구분, immutable digest/owner매핑·lifecycle/pending/미매핑·012상태경계·ADR commit순서 정합화 필요. 문서기록만/코드·Jira·외부조회없음,Gradle미실행. 실제판매/삭제OFF유지.
- 2026-10-13 TMI-205 PR2 로컬 삭제코어 구현/검증: READY증거보존 별도삭제lease·PURGE OPEN-only/control+lifecycle CAS·최종증거검사·전체100문서 원자삭제·unknowncommit/부재구분·직후실행/tick회수·동일tick중복제한, r1보존/r2명시이관. [안내](../runbooks/TMI-205-pr2-purge-core.md). clean test bootJar **1,066통과/실패0/오류0/skip0(27추가)**. 실제 구성은 UNKNOWN-only/purge ON기동거절, 실금융/provider/drain PR3·206별도/205전체완료아님. Jira직접조회 성공/205하위209만 확인, 생성·수정없음. 기존LC/PR1변경보존·타서버/운영DB/배포/commit/push없음.
- 2026-10-13 PR2 증거보존 삭제lease·직후실행 사용자 승인: cleanup-r1 §5.3.1/205계획에 확인claim과삭제claim 분리, READY/proof 유지·30초lease, 직후시도/실패중단tick회수, 만료·변경재검증, OFF시간당확인 및 schema이관/회귀 기준 반영. 문서만 변경, production purge ON 거절 유지·코드/schema/DB/Jira/배포 미변경. 실제PR2 구현과exact validator revision이관 후속.
- 2026-10-13 TMI-205 PR2 착수 전 정적 검토: [대조 기록](../plans/TMI-205-PR2-READINESS-REVIEW-2026-10-13.md). READY proof+삭제lease가 현재 schema와 충돌, READY 1시간 due/proof 만료 경계·PURGE fence/100문서 합계·최종policy/증거검사 규격 보완 후 OFF 구현 권장. 실제 provider/금융/drain은 PR3/206 유지. Jira 검색403으로 PR2 이슈 유무 미확인, 쓰기 없음. 코드/정본변경·테스트 재실행 없이 기록만 추가, 기존 별도 변경 보존.
- 2026-10-13 LC JWT B 구현 계획·Billing ADR/AGENTS 동기화: [계획](../plans/PLAN-LC-BILLING-WORKLOAD-JWT.md) PR0준비→PR1Billing verifier/connector/guard→PR2LC sender(상대담당)→PR3실chain/HTTP27·startup/회귀. ADR002§20·ADR001 인증안내·AGENTS에 여섯LC POST JWT목표/IAM permitAll·production legacy TEST·다른port우회제거·공유TLS/독립trust·기본OFF 명시. 기존기록/Identity/user·업무불변, S3provider보존. LC표수락/상대동기화·B코드승인·C/배포미완료, 실제code/설정/타저장소미변경·별도작업보존·문서검사만.
- 2026-10-13 Identity 발신 시간 증명 단순화 인계 반영: Billing은 별도 clock attestation/runtime 증명 차단이 없어 production 수정 없음. 공동 lifecycle §5.4의 독립 clock gate만 인프라 동기화·운영 대응 전제로 개정, JWT/Retry-After/lease·구매/복원/삭제 보호 유지. 기존 Identity BLOCKED_CLOCK 선택 재개·호환 읽기 보존. [회신](../contracts/TMI-204-CLOCK-OPERATING-ASSUMPTION-REPLY-2026-10-13.md). Retry-After 경계1테스트 추가, clean test **1,039통과/실패0/오류0/skip0**. Identity 발신은 읽기 확인 시 변경 전이며 실제 운영·양측 변경 후 검증/배포는 미완료. 타서버/Jira/데이터/설정 변경 없음, 기존 별도 변경 보존.
- 2026-10-13 startup 조합표 사용자 승인 반영: [매핑표](../contracts/LC-BILLING-WORKLOAD-SENDER-MAPPING-R1.md)에 J/S/H/R/W/P 거절 규칙·64조합(조건정상 전제22허용/42거절)·키 준비 조건 확정. LC source 읽기 결과 publisher/outbox/state store는 writer Tx 없이 MongoTemplate lease/CAS 사용, W OFF/P ON 기존 outbox drain 허용. W 전용 index validator의 P 단독 점검 공백은 B 검증 대상으로 명시. LC 표 수락/실제 startup·drain·HTTP/B 구현/ADR·AGENTS/배포는 남음. 문서·정적 대조만, 별도 코드 변경 보존.
- 2026-10-13 TMI-209(PR1) 구현·로컬 검증 완료: cleanup job/lease/CLEANUP epoch fence·증거판정·15일/시간당/일일/7일일정·기본OFF worker·비식별집계/fake sink·명시schema설치/검증 추가. runtime UNKNOWN-only, purge/실alerts ON은기동거절,물리삭제없음. clean test bootJar **1,038개 통과/실패0/오류0/skip0(신규34)**. [안내](../runbooks/TMI-209-cleanup-foundation.md). PR2삭제코어/PR3실거래/206실운영후속, 전체205완료아님. 동시LC변경·WORKLOG재구성·외부branch전환보존,이번Jira상태/타서버/배포/commit/push없음.
- 2026-10-13 LC A 최종 수락 Billing 정본 반영(TMI-116/128/122): [수락 기록](../contracts/LC-BILLING-WORKLOAD-A-ACCEPTANCE-R1.md). 인증 규칙·fixtures-1 세 JSON hash·신규 세 발신 설정 명칭/공통 gate 의미 확정. 양측 token/hash 검증 완료, fixture bytes 불변. 상세 startup 조합표·B 계획/ADR/AGENTS 동기화·HTTP27·B/C 구현/배포·활성화 미완료. 문서 상태/기록만 반영, 기존 별도 TMI-205 코드 변경 보존·타저장소/Jira/외부전송/테스트 재실행 없음.
- 2026-10-13 LC fixtures-1 독립 검증 접수(TMI-116/128/122): 양측 원문 token72+hash1 통과, LC test XML 73개·전체713개 실패/오류/skip0 읽기 확인. 세 JSON/SHA256SUMS는 Billing과 동일 bytes. Billing 기존 전체1004 결과와 구분, [접수 회신](../contracts/LC-BILLING-WORKLOAD-FIXTURES-1-BILLING-RECEIPT-2026-10-13.md) 작성. 신규 설정 세 명칭은 LC 기술 검토 완료이나 최종 수락은 남음. startup J/S/H/R/W/P 후보표·S3 AWS provider 보존·drain/Transaction 의존 확인 명시. HTTP27·최종 동결·ADR/AGENTS·B/C/배포 미완료. fixture 재생성/코드/타저장소 수정/테스트 재실행 없음, 문서·정적 대조만.
- 2026-10-13 사용자 요청으로 TMI-205 PR1 하위 작업 [TMI-209](https://to-teacher.atlassian.net/browse/TMI-209) 생성: cleanup상태/lease/증거판정/검토일정·fake검증·기본OFF. parent205 재조회 확인,해야할일·미할당. 실제삭제PR2/providerPR3/실Slack/배포제외. 기존205본문/상태변경없이 계획·기록만연결,코드미착수.
- 2026-10-13 TMI-205 PR0 기술 규격 작성: [cleanup-r1](../contracts/TMI-205-cleanup-technical-spec-r1.md). 기존lifecycle.version으로ref/금융변경검출 통합,job/proof/lease BSON·null·전이·CLEANUP/PURGE fence·bounded삭제·unknowncommit·drain·독립schema/index·Slack집계/재시도후보 정리. 실제coverage/drain/예외보존·채널/담당/중단운영검증은별도,문서만·코드/Jira/실연동없음.
- 2026-10-13 LC A fixtures-1 제작·Billing 독립 검증: 테스트용 raw signed token 72개·HTTP 기대 사례 27개·public JWK/SHA256SUMS와 Node 오프라인 제작 스크립트 추가. 기존 WorkloadJwtCodec token/hash 73개 및 전체 clean test **1,004개 통과/실패0/오류0/skip0**. 최종 HTTP/hash 정리 후 대상 테스트 재검증. 신규 HTTP 동작·LC 검증은 미실행. LC 기존 설정/두 SigV4 client 읽기 확인 후 [sender 매핑안](../contracts/LC-BILLING-WORKLOAD-SENDER-MAPPING-R1.md) 작성, 신규 명칭은 LC 수락 대기. LC hash 수락/독립 실행·최종 동결·B/C 구현/배포 미완료. production 코드/LC 파일/설정/키·타서버/Jira/전송/배포 변경 없음.
- 2026-10-13 TMI-205 Slack 알림 구분 승인 반영: 일반 미해결 일일 요약, 지속/다수 계정 장애 즉시 알림, 삭제 안전성 사고 즉시 알림·삭제 중단. DB 상태/기한 기준·Slack 확인으로 삭제 금지·식별자 비노출. 실제 채널/담당·예외 보존/임계치·연동은 미결, 계획§5.8.1 문서만 변경, 실제 발송/설정/Jira 변경 없음.
- 2026-10-13 LC A 최종 검토 보완: 인증 명세 §2.1.1에 exact Bearer/SP·탭/공백 거절·추가 payload claim 허용/무시·JOSE header 거절, §2.4.1에 활성 서비스별 고정 verifier 선택과 서명 검증 후 교차 403/발급 서비스 OFF의 활성 대상 401/대상 OFF의 살아 있는 서버 404 매트릭스 명시. AUTH-08/10/11 포함 총 27개 테스트 그룹. 공동 fixture 제작·독립 실행·최종 계약 수락·코드·배포 미수행, 문서 정합성 검사만.
- 2026-10-13 TMI-205 질의 보완: 미구매는 빈 RC 조회가 아닌 삭제에 필요한 거래 부재 증거 충족이며 실제 coverage 미검증. 미해결 검토는 reason별 오류/미처리/매핑 증거 확인이지 운영자 추측 승인 아님. lease는 cleanup 중복만 제한하고 금융 입력은 lifecycle CAS로 경합. 기존 WITHDRAWN 차단과 JWT 자체 만료/삭제 후 재생성 방지를 구분. 설명·기록만, 구현/정책 변경 없음.
- 2026-10-13 LC 인증 A 단계 명세 작성: [LC–Billing workload-auth-r1 후보](../contracts/LC-BILLING-WORKLOAD-AUTH-R1.md)에 exact claim·시간·배포 키·목적인가·오류 우선순위와 26개 공동 테스트 그룹·A/B/C 분담을 정리. 살아 있는 HTTP 서버의 차단 404와 포트 미기동 전송 오류를 구분하고 모든 query(서버에 전달된 빈 `?` 포함)를 거절하도록 명시. LC 최종 수락·fixture/hash 제작·독립 실행은 미완료. 기존 LC Lattice 계약·코드·설정·배포 변경 없음, 문서 검사만 수행.
- 2026-10-13 TMI-205 계획 상세 설명: 탈퇴15일 후보→거래 확인→보존/보류/조건부 정리 흐름, lease·version·epoch 역할과010 실제 거래 연결 의존성을 사용자 관점에서 설명. 계획/정책 승인이나 구현 착수로 취급하지 않음. 코드/Jira 변경 없음.
- 2026-10-13 LC §16.6 권장 확정 후보 작성: 검토회신§6에LC issuer/sub·2목적audience·r1시간/배포JWK재사용·기존Identity flag유지+LC독립flag·TLS OR기동·404→401→403→업무 우선순위와A계약/fixture→B OFF인증→C LC승인재처리/E2E 분담 제안. LCoffline운영명령은후보,도구/exact/ADR·AGENTS승인·코드변경없음. 별도운영값/담당·SLA는미확정,문서검사만.
- 2026-10-13 TMI-205 상세 계획 작성: [계획서](../plans/TMI-205-withdrawal-reconciliation-and-purge.md). Jira 읽기/201·204 코드 대조 후 PR0 규격→PR1 상태·일정→PR2 삭제 코어→PR3 실제 거래 연결(010 의존)→206/013 gate로 분리.15일·매시간/매일/7일 유지, provider 부재 증명·공통 writer fence·token drain/예외 보존 충족 전 실제 삭제OFF. 신규 schema/flag/PURGE writer는 제안이며 구현 안 함. 별도 LC 변경 보존, Jira 쓰기/배포 없음.
- 2026-10-13 LC R2 문구 검토 완료 회신 접수: 사용자 전달 LC 회신의 §16.3 오류 구분/§16.5 재처리 도구 미준비 명시 적절·추가 수정 없음 기록. 공동계약/회신에 검토 범위 표시, 전달 R2 스냅샷 보존. §16.6 exact 인증·fixture·양측 구현분담·ADR/AGENTS 동기화는 공동확정 대기, 구현/배포/활성화 승인 아님. 문서/기록만 변경.
- 2026-10-13 LC 수정본 전달 혼선 확인: 현재 §16.3에는 빈 ingress404/업무OPERATION_NOT_FOUND 구분이 이미 반영됨을 재확인. 상대가 본 첨부 버전은 미확인, 구버전 수신 가능성으로 설명. 정본§16에 LC-JWT-TRANSITION-R2 식별자 추가·[R2 전달용 스냅샷](../contracts/LC-BILLING-WORKLOAD-JWT-CONTRACT-R2-2026-10-13.md) 작성. 도구/인증동결은 여전히 미완료, 코드/외부전송/배포 변경 없음.
- 2026-10-13 다음 작업 안내: TMI-205 거래 확인·조건부 purge의 상세 계획 정리가 다음 순서다. 기존 계약은 있으나 독립 205 계획은 확인되지 않았다. 15일·매시간/매일/7일 정책 유지, PLAN-010 거래 증거/provider coverage 의존성과 최신204 로컬 복구 연결을 먼저 정리하며 실제 삭제 활성화는206/013 별도 gate다. 이번 코드/Jira 변경 없음.
- 2026-10-13 완료 범위 재확인: TMI-204의 승인된 Billing 로컬 구현은 완료(직전 clean test/bootJar 931개 통과). 전체 결제 개발 완료나 운영 활성화 완료를 뜻하지 않는다. TMI-206 실환경 연동·권한·복원·부하·알림 검증 및 활성화 승인은 별도이며 기본 OFF 유지. 이번 상태 안내는 코드 변경·테스트 재실행·Jira 상태 변경 없음.
- 2026-10-13 LC 공동계약 검토 보정: §16.3의 body 없는 OFF/오포트404→Reservation CONTRACT_ERROR와 업무code OPERATION_NOT_FOUND를 분리, 둘 다 취소/미소비 확정 아님. LC 보고의 승인 DEAD_LETTER 재처리 도구 미발견을 선행 승인/구현/검증 gate로 명시, DB직접수정 금지. §16.6 exact claim/시간/오류precedence·공동fixture·ADR/AGENTS 동기화·구현분담 미완료 목록 추가. 코드/정본인증개정/타서버/배포 변경 없이 문서 보완만.
- 2026-10-13 TMI-204 잔여 로컬 구현: offline RecoveryOperator/CLI(기본dry-run·별도credential·CAS/멱등control+operation Tx·INITIAL/ROTATE/BLOCK/RECOVER/OPEN·검증된feed후구매재개), 알려진 operator receipt validator 명시이관, snapshot hash200행/30초양보·동일lease·crash재검증·전용scheduler, privacy-safe HTTP CLIENT span 추가. 상세 [runbook](../runbooks/TMI-204-operator-and-bounded-verification.md). clean test+bootJar **931개 통과/실패0/오류0/skip0**(14추가), body/hash/무료/LC/user/JWT 불변. 실제운영/권한/복원/부하/외부watchdog는206 gate·기본OFF. 작업 중 별도LC 문서/공동계약§16/기록추가를확인하고보존,이번코드변경과구분. Jira읽기만·타서버/commit/push/배포없음.
- 2026-10-13 사용자 요청으로 공동 통합계약 §16 추가: LC JWT 권장방향·Billing 수신 준비 후 LC 발신 활성화·기존404 분류·원Reservation/Session증거와DEAD_LETTER event/digest 보존·status-first/승인bounded requeue·기존TTL/terminal/owner멱등 유지. 키/인가/flag 독립·IAM permitAll 우회 차단·공동fixture/복구도구검증 명시. 인증exact 동결/코드/배포 승인은 별도, LC/Identity/AWS/Jira 변경 없음. 문서검사만 수행.
- 2026-10-13 LC JWT 후속 검토 반영: TTL60/배포공개키/목적audience/빈401·403/TLS공유 가능 방향의 의견 접수, 회신 초안에404 전환·복구 보완. Billing 준비 후 LC발신 활성화, 기존404 분류 유지·Reservation status-first/원key·Session증거 보존·AttemptGroup DEAD_LETTER 원event/digest 보존 후 승인bounded requeue. LC/Identity 키·인가·flag 독립 및 IAM permitAll 우회 차단 조건 명시. LC 실제구현/복구도구는 미확인, 정본계약/코드/배포 승인 아님. 문서/기록만 변경.
- 2026-10-13 LC JWT 인계 검토: [Billing 회신 초안](../contracts/LC-BILLING-WORKLOAD-JWT-REVIEW-2026-10-13.md). 6POST/body는 Billing 구현과 일치, cancel CALLER_ABORTED도 유지. LC Lattice permitAll과 Identity 전용 JWT의 차이 확인. TTL60/배포 공개키/목적별audience/빈401·403 권장, LC 전환 범위/connector·키 운영은 별도 승인·공동 동결 대상. 현 Identity 승인 예외를 LC에 확대하지 않았으며 코드/정본계약/타서버/AWS/Jira 변경·외부회신 전송 없음. 문서/정적 검토만, Gradle 미실행.
- 2026-10-13 완료범위 재확인: 이번 SigV4 제거/JWT 단일화의 Billing 로컬 구현·917개 회귀는 완료. TMI-204 전체는 운영 epoch 설치/이관·복원 절차, outbound tracing·대용량 snapshot hash 실행 조율 등 후속과 실제 연동/운영 gate가 남아 있어 완료로 보고하지 않는다. 상태 질의에 대한 문서/코드 정적 확인 및 기록만 수행, 테스트 재실행·Jira/배포 변경 없음.
- 2026-10-13 승인 후 Identity–Billing JWT 단일화 로컬 구현 완료: SigV4 wrapper/credential/signer·AWS request 타입/BOM/auth 모듈·인증 선택 설정 제거. 구 설정은 빈 값/JWT값/OFF에서도 기동 오류. Identity 네 이벤트의 IAM/TEST 통과 제거·receiver OFF 모든 포트404, LC 인증 유지. 공통 decoder/응답/retry·도메인 불변. Identity 시간fixture16개 원문token 검증 및 SHA256 동일, 기존13개 유지. 최종 clean test **917통과/실패0/오류0/skip0**. 초기 인증테스트3건 보완, Mongo index초기화1건 실패는 대상/최종전체 재실행에서 미재현. 기본OFF·실배포/공동활성화/키·ALB 검증 미완료. [회신 §6](../contracts/TMI-204-SIGV4-REMOVAL-BILLING-REPLY-2026-10-13.md). Jira는 착수 전 읽기만, 타서버/commit/push 없음.
- 2026-10-13 Identity SigV4 제거 인계 회신: [범위·이행안](../contracts/TMI-204-SIGV4-REMOVAL-BILLING-REPLY-2026-10-13.md). recovery signer/SDK/인증선택 제거와 Identity 4route의 IAM permitAll·receiver OFF 우회 차단을 함께 제안. LC/user 인증 유지. 실제 활성 배포 미확인, Identity16/Billing13 시간 fixture는 서로 다르며 동기화/독립실행 미완료. 이번은 문서 회신만, 코드/Jira/설정/타서버 변경·Gradle 실행 없음.
- 2026-10-13 LC→Billing 구현 범위 정적 확인: Billing Reservation reserve/confirm/cancel/status/phone continuation 및 AttemptGroup event 수신 코드가 존재한다. LATTICE_AWS_IAM 보안 분기는 외부 Lattice 인증을 전제로 permitAll하며 Billing 자체 SigV4 검증 구현과는 다르다. 현재 AWS 서명 코드는 Billing→Identity recovery 발신에 있다. LC 발신 저장소·운영 연동은 이번에 확인하지 않았으므로 완료 여부를 단정하지 않는다. 코드/인증 계약 변경 없이 기록만 갱신, Gradle 미실행.
- 2026-10-12 TMI-204 ALB/workload JWT Billing OFF 구현: WorkloadJwtCodec/Properties·전용TLS connector/guard/chain·4이벤트 목적인가·JWT recovery wrapper와공통HTTP코어분리. RS256/배포JWK/TTL60/skew30·strictJSON숫자/시간/키·401인증/403인가·다른port404·freshJWT/sameoperation·fallback없음. 기존SDK/SigV4명시모드보존,수신/발신기본OFF·키/port빈값. AGENTS/ADR002·004/공동계약/결정서/PLAN009·204에범위한정예외와 [workload-auth-r1](../contracts/WORKLOAD-JWT-AUTH-R1.md) 정합화. clean test **896통과/실패0/오류0/skip0**(신규48),실ControllerMockMvc·13시간fixture포함. 실TLS/ALB/SG/Identity공동수락/환경·키운영미검증·전체204완료아님. 기존dirty보존·예상밖변경없음·실API/Jira쓰기/타서버/commit/push/배포없음.
- 2026-10-12 사용자 승인으로 TMI-204 계획 §2.5·Jira 본문 갱신/재조회완료: ALB+전용workload JWT·양서버connector·4이벤트/4복구route·test/staging/production신뢰분리·배포publicJWK·TTL60/skew30·시간/401·403/rotation·body서명없는보안모델을계획채택. 실제환경/DB/source/origin/port/TLS/키담당·공동authfixture는미확인gate로유지. 기존SigV4목표표현/과거PR1중단표기정합화,이전848테스트는JWT검증아님. Jira제목/해야할일/미할당/parent137유지·다른이슈미수정. 계획/회신/기록만변경·코드/SDK/AWS/배포없음·diff/링크검증통과·Gradle미실행.
- 2026-10-12 Identity JWT 재검토 반영: Billing 회신 §7에 배포/trust/데이터환경 분리표·test/staging 별도신뢰권장(동일SANDBOX DB충돌주의), 양서버전용connector/public거절, ALB→task TLS 미결, 시간/duplicateclaim/401·403/JWK검증 경계후보 추가. Identity workflow main=staging tag/develop=test 및 recovery path-only chain을읽기확인,실제 WITHDRAWAL_ENVIRONMENT/운영배포값은미확인. 문서/기록만변경·exact동결/코드/인프라/Jira변경없음. 다음은실제환경·origin/port/TLS·키폐기운영확인후공동동결. 문서검사통과·코드변경없어Gradle미실행.
- 2026-10-12 TMI-203·204 ALB/workload JWT 인계 검토: 첨부의 방향 승인과 exact 미확정/실행 미승인을 구분했다. 현재 Lattice 수신 permitAll을 ALB에 그대로 연결하면 안 되며 workload 별도 connector/TG/chain·사용자/LC 격리 권장. 인계 영향 목록에 Trial eligibility event 추가, 환경별 issuer/audience/subject 후보·TTL60/skew30·초기 배포 public JWK set/rotation 책임 제안. 실제 Billing 배포/활성값/ALB는 미확인(기본OFF≠실행OFF), 최초적용으로단정안함. [Billing 회신](../contracts/TMI-203-204-ALB-WORKLOAD-JWT-BILLING-REPLY-2026-10-12.md) 작성·기록만변경, 코드/기존SDK/ADR확정문구/타서버/AWS/Jira/commit/push 불변. diff/문서링크검사통과, 분석문서작업이라테스트재실행없음. 구현전범위한정인증계약/AGENTS개정·공동authfixture수락필요.
- 2026-10-12 TMI-204 SigV4 HTTP client 구현: 사용자 승인한 SDK v2 BOM2.29.52/auth/http-auth-aws·DefaultCredentialsProvider, exact4POST·HTTPS/환경host pin·3초connect/5초body포함HTTP·16KiB bounded reader·redirect차단·strict오류/Retry-After연결 추가. BUILDING 긴대기 영속화·HTTP수동step우회방지, HTTP ON은control4/retry metadata필수·기본OFF. 전체 clean test **848개 성공/실패0/오류0/skip0**(직전801+47), fake서버 실제wire독립서명검산·body timeout/크기/응답유실POST1회·Mongo재시작대기보존 검증. 기존dirty보존·예상밖변경없음·wire/무료권/결제정책불변. 상세 [client 안내](../runbooks/TMI-204-identity-sigv4-client.md). 실Identity/AWS/IAM/TLS·credential회전/SDK보안점검·운영이관/복원/외부경보/outbound trace/대용량hash조율은후속,전체204완료아님. Jira쓰기/타서버/실API/commit/push/배포없음.
- 2026-10-12 TMI-204 자동snapshot·retention·관측 구현: control4 retry증거·budget index 추가, BUILDING5초/5분신호·20회probe·확정timeout/EXPIRED의추가2run·반복GAP차단·자동H이후feed수렴. 독립기본OFF정리worker가7일임시자료/1시간cursor/120일operation만정리, 저카디널리티경보신호·heartbeat추가. 최종 clean test801개 통과/실패0/오류0/skip0(이번13추가). 기존미커밋feed변경보존·예상밖변경없음·wire/무료권불변. 실client/SigV4 의존성추가승인·IdentityHTTP연결·운영이관/복원/외부watchdog/대용량hash조율은후속이며전체204/활성화완료아님. 상세 TMI-204-automation-and-retention.md. Jira쓰기/타서버/commit/push/배포없음.
- 2026-10-12 TMI-204 feed·영속 retry 구현: control version3/feedJob·FEED receipt, inbox/탈퇴/C 원자commit·고정H2·cursor1시간·dispatch별실패기록·20회circuit/5분probe·긴Retry-After·10page/30초양보와 기본OFF worker 추가. 전체 clean test788개 통과/실패0/오류0/skip0(신규23), 원본소실→명시snapshot→검증된H이후feed연결도확인. SNAPSHOT_REQUIRED에서새run자동조율은미구현이며실client/SigV4·retention scheduler/경보/이관/실환경검증도후속,전체204완료아님. runbook TMI-204-feed-processing.md. 기존wire/무료권불변·초기clean·예상밖변경없음·타서버/Jira쓰기/commit/push/배포없음.
- 2026-10-12 TMI-204 PR2 snapshot 영속 코어: WithdrawalSnapshotProcessor/PaymentSnapshotSchema·4collection/9index·control version2 및 기본OFF snapshot flag 추가. operation 선저장, 20행 페이지 원자 적용/receipt·lease/epoch·전체 count/digest 검증 후 baseline H 설치, source/local7일 만료와 bounded purge 구현. 전체 clean test765개 성공/실패0/오류0/skip0(신규16), 기존 public/internal·무료/결제 계약 유지. 상세 runbook TMI-204-snapshot-processing.md. 자동scheduler/feed/실HTTP/실패 후 새run budget·120일 정리/운영migration은 후속이며 PR2전체·204전체 완료 아님. 초기 worktree clean, 예상밖변경없음; Jira/Identity/배포/commit/push 미수행.
- 2026-10-12 TMI-204 구현재개/PR1제어기반검증: 공통WithdrawalApplier/Fact·success/error strict decoder·client port, ACCOUNT/PUSH/RECOVERY epoch CAS·기본OFF fence, control header v1 validator/unique index검증추가. 전체clean test749개/실패0/오류0/skip0(이번25추가)통과. snapshot fact/진행rollback·가짜inbox없음·push NOOP·barrier/old epoch/재시도/경합/overflow검증. runbook TMI-204-recovery-foundation.md. 5collection중control header만구현,기타진행저장/snapshot·feedworker/운영installer·실HTTP/IAM/복원은후속. 전체204완료/활성화아님,기존변경보존·Jira/타서버/commit/push없음.
- 2026-10-12 TMI-204계획·Jira갱신완료: snapshot중심복구/과거불명최소표시/5collection·writer방향승인과Identity회신반영. snapshot은T시점DB잔존WITHDRAWN전체,원event만료/tombstone잔존은복구가능·tombstone삭제/복원소실은불가. 상대자동purge경로미발견은무기한보존/운영완전성보장아님. Jira본문수정후재조회확인,제목/상태해야할일/parent137/미할당유지. 코드중단·배포/삭제OFF,Identity코드/203Jira미변경.
- 2026-10-12 TMI-204 단순화 Identity전달범위정리: Billing source소실→snapshot상태수렴→H이후feed·과거불명최소표시를공유. recovery-r1/scalar-end-1/4route·source floor/range·보존계약변경요청없음.동일T의탈퇴상태목록/H일관성·목록포함/보존범위확인필요,누락된과거사용자를부재만으로ACTIVE/삭제안전판정금지. 실제메시지/Jira/타서버변경없음.
- 2026-10-12 TMI-204 snapshot중심단순화 사용자승인: 원본소실시검증된현재탈퇴snapshot으로수렴후H이후feed재개·과거이력불명최소표시. 상세gap누적/상한설계대체,현재동기화와과거event완전성분리.205는계정별탈퇴/거래/보존증거유지·snapshot만으로삭제불가. 계획§2.0·purge문서최신안반영,코드재개/Jira/Identity변경없음.
- 2026-10-12 TMI-204 GAP복잡성필요성재검토설명: 탈퇴누락복구·거짓완료방지는필수이나상세과거구간무한관리/일괄삭제보류가유일해법은아님. 현재상태snapshot수렴과원event이력완전성분리,205개별거래/삭제증거와의의존성축소검토제안. 새단순화승인/계약변경/코드재개없음.
- 2026-10-12 TMI-204 사용자1·2방향승인: 5개collection과exact명세,단순lag정상유지/복원·control소실의관련쓰기차단·승인복구허용. 3번GAP상한/UNKNOWN·삭제보류는설명중·미승인. GAP은통신지연이아닌필요원천소실/누락이며구간수와event수를구분. snapshot상태수렴≠과거event완전성,보류를무기한개인정보보존근거로사용금지. 코드구현재개없음.
- 중단시점 회귀 확인: 현재코드 ./gradlew clean test724개통과/실패·오류·skip0. 신규복구초안전용테스트는없으므로구현완료/안전성검증완료아님.
- 2026-10-12 TMI-204 구현 중 사용자 요청으로 중단: 공통 PaymentWithdrawalApplier/WithdrawalFact·복구 decoder/client interface 초안과 기존 push service 위임 변경이 미커밋으로 남음. 신규복구DB/fence/worker/HTTP연결/활성화 없음. PR1 완료 아님, 신규코드 전용테스트 미작성. 먼저 local schema/index·barrier전이·bounded GAP/overflow·205증거소비 규격을 동결하고 공동본문 정합화하기로 방향전환. SDK/IAM은실HTTP단계 별도. 직전724통과는scalar fixture검증이므로새초안의완료근거로사용하지않음.
- 2026-10-12 TMI-203·204 scalar-end-1 재검증 완료: Identity schema/hash/scalar-cases/README 동기화, known-gap을schema거절검증으로변경. 정상8·음성264통과, 전체clean test724/실패0/오류0/skip0(복구fixture331), 입력8개hash일치. 공유wire/schema/fixture보완범위 Billing 기술수락·문자열종료미결종료. Phase0전체/local schema·index·실API/Mongo/IAM/복원/활성화는별도. 운영/Identity/Jira/기존199변경없음, 상세 TMI-204-RECOVERY-R1-INDEPENDENT-VALIDATION.md 최신항목.
- 2026-10-12 TMI-204 Phase0 Billing 독립 검증: recovery-r1 공유 JSON8종(7개 content+hash manifest)·Billing 자체 계약 테스트58개 추가. 전체 clean test451/실패0/오류0/skip0. test-only networknt1.5.6 추가, 운영코드/Identity/기존v1 fixture 불변. 숫자 schema가 끝 개행을 허용하는 차이를 재현했고 strict oracle 거절과 함께 명시적으로 고정했다. 공동 동결 전 Identity 보완/수락 필요. 결과: docs/contracts/TMI-204-RECOVERY-R1-INDEPENDENT-VALIDATION.md. 실제203/204 HTTP·Mongo복구adapter/IAM 검증·PR1 구현·판매/삭제 활성화는 미완료.
- 2026-10-12 Identity 로컬 직접확인: feat/TMI-203-withdrawal-snapshot-feed-recovery 미커밋작업에 recovery-r1 JSON6종(schema/request/response/error/state/index)·RecoveryProtocol/SnapshotAccessRules 및추가range코어존재.기존보고보다진전,관련6개XML80tests/실패0/skip0확인(재실행아님). RecoveryProtocol은비등록codec,src/main참조검색상복구HTTP/controller/Mongoadapter연결없음.새fixture독립검증은착수가능하나공동수락/API/실배포완료아님.기록만변경.
- 2026-10-12 Identity203 내부코어 보고검토: 기존검토회신§7 추가. 범위/feed/digest코어구현·1492개결과는상대보고,API/Mongo/cursor/IAM미구현. source range2index/version명시이관/100batch·FAILED409/429방향권장수용. feed선택밖range누락금지·unique동일from경합의재선택수렴을adapter검증항목으로명시. 새exact/fixture공동확인후연동,코드/Jira미변경.
- 2026-10-12 사용자 승인으로204계획·Jira TMI-204 제목/본문 갱신 및 재조회 확인: v1최초구현·Billing local checkpoint/epoch·source floor+range·5개저장소후보·원격ACK제거,5초/빈30초·20회후5분probe·snapshot추가2run/24h·경보/권한·가짜시계회귀 반영. Jira상태 해야 할 일/상위137/미할당 유지. 운영회신채택안내 추가. 신규exact동결·실제담당/환경gate·코드착수/배포는별도,코드/타서버변경없음.
- 2026-10-12 최종방향 인계 검토/운영 회신: TMI-204-OPERATIONS-PROPOSAL.md 작성. 사용자미구현전제로전달된v1유지·floor+별도range채택이직전v2/10index후보대체.204 pull5초/빈scan30초·1~60초backoff20회후5분probe·snapshot추가2run/24h·경보/독립watchdog/운영epoch권한을미동결권장값으로제안.실제담당미지정·sourcecapturedAt부재시consumerexpiry추정금지.계획설정/코드/fixture/Jira/배포미변경.
- 2026-10-12 recovery-v2-candidate-1 검토: 기존 검토회신§6에 주요보완반영·후보기술채택권장 기록.고정page20전체오류·logicalexpiry410·503details없음·7일EXPIRED/120일404·v2operation index 방향타당,기존13→후보10 정적일치. source interval저장/overflow·429/message/FAILED page상태표·500nonretryable 우선분류를 새exact작성시 마무리. 실제 공동동결/코드/배포 검증 아님.
- 2026-10-12 Identity 인계/exact 미결안2종 검토: TMI-203-204-EXACT-AMENDMENT-REVIEW.md 작성. local authority·stream body·환경/consumer/key unique+stream binding·bounded availability/410 권장 수용 방향,503 details 생략·순번/만료/멱등120일 경계 보완 제안. v2 prefix는 후보,새exact/fixture 동결 아님. 기존계획/계약본문/코드/Identity/Jira 미변경.
- 2026-10-12 문서 개정 인계서 작성: docs/contracts/TMI-203-204-DOCUMENT-REVISION-HANDOFF.md. 현재 상단안내/204본문 개정 상태를 구분하고 PLAN009·ADR004·공동계약§5~6·exact/fixture·결정서·205/206의 절별 대체 지침/유지사항/수락 순서 명시. snapshot stream 귀속·마지막cursor·availability410 등 exact 미결 항목 포함. 기존 계약본문/코드/Identity/Jira 변경 없음.
- 2026-10-12 PLAN010~013 서비스 범위 설명:010 Billing 구매/기간권과 앱 SDK/sync,011 Billing paid Reservation과LC paid source·채점 승인,012 Billing 환불 원장과LC revoke 수신/차단,013 Identity·LC·앱·Store/RC·AWS 공동검증/운영.009만 다중서비스 작업이라는 해석은 아님. 이 저장소에서 타 서비스 코드는 변경하지 않으며 상대 exact 계약·구현/gate는 별도. 설명/기록만 수행.
- 2026-10-12 TMI-204 계획 개정: 사용자 요청으로 Billing local checkpoint/epoch/coverage 단일 authority 채택. remote ACK/generation·ACK worker/flag·별도 scans 제거, T/H/digest·H2·local CAS/복원 격리 유지.5개 로컬 collection 후보·baseline/feed 범위 분리·availability410/상한·retry/circuit·정상lag/실제GAP gate·4PR 재작성. PLAN009/결정서/ADR/공동exact/205설계에 개정 범위 안내, 기존 exact 본문/fixture 보존. 신규 exact 공동 동결·구현 승인은 별도, 코드/Identity/Jira 변경 없음.
- 2026-10-12 Identity 단순화 회신 검토: Billing local checkpoint/epoch/consumer coverage 단일 소유, 원격 ACK·generation 제거 후보는 합리적 권장안. T/H/digest·H2·local CAS/복원 격리 유지로 중복 관리 축소. 수락/구현 전 availability(410 포함·범위/상한/시점), 원 event checkpoint와 snapshot baseline 분리, 계약 revision/구배포 호환을 exact 동결해야 함. 기존 계약/계획/코드 미변경, 상대 구현·운영 검증 아님.
- 2026-10-12 Identity203·Billing204 단순화 인계서 작성: docs/contracts/TMI-203-204-SIMPLIFICATION-HANDOFF.md. 유지 불변식·축소 목적·T/digest/H2/ACK/generation 대체 검토·실패별 판매/삭제 영향·회신 요구 정리. 사용자 방향 승인과 기술 대안 미확정 구분, 기존 공동 wire 미변경. 코드/Identity/외부 발송/Jira/배포 변경 없음.
- 2026-10-12 TMI-203·204 단순화 방향 사용자 동의: 전체 과거 이력 증명과 정상 서비스 허용의 과도한 결합을 줄이는 재설계 방향. durable 탈퇴 기록/재시도/멱등 적용/저장 후 checkpoint/탈퇴 상태 단조성은 유지한다. 인증·토큰 발급 차단만으로 이미 발급된 토큰 문제가 해결된다고 가정하지 않는다. 불명 범위를 특정할 수 없으면 계정별 격리가 가능하다고 단정하지 않으며 삭제 보류 범위를 별도 설계한다. 현재 공동 exact wire·고정T·ACK·generation 삭제는 미확정이고 기존 규격은 유지, 변경안의 Identity 공동 수락 전 구현하지 않음. 기록만 변경, 코드/배포 미변경.
- 2026-10-12 첨부TMI-203계획검토: 공동exact규격/204와핵심충돌없음.최초출시legacy예외·snapshot미필터·고정T/H/H2·generation·ACK/보존분리·I3추가6index일치확인.보완권장:새세대는INITIAL/RESTORE/RESYNC만,baseline응답유실동일null-version재ACK수렴순서,이미적용한원천만료와미적용필요구간GAP의구체테스트.상대202코드/운영검증은재실행않음,계획/코드수정없음.
- 2026-10-12 최초 결제 출시 legacy 범위 사용자 승인 반영: 기존 구매account/ref발급/실거래 없음은 사용자 확인(운영조회 아님).과거legacy COMPLETE를첫출시필수조건에서제외하고시작기준점이후capture/발급fence·token drain/수신·snapshot/feed검증유지.과거UNKNOWN미변경·무기한재시도금지·이후GAP/복원보호/205조건불변.204계획/ADR004/공동계약/결정서동기화,코드·실제활성화·전체204구현승인아님.
- 2026-10-12 legacy gap 처리 권장안 설명: 과거완전성UNKNOWN을거짓COMPLETE로바꾸지않되 복구작업종료/판매gate/개별삭제판정분리.출시전실제구매계정·ref·거래/과거발급가능성조사후 영향없음증명시capture/token drain/기준점검증을갖춘출시cohort 제안.영향건은bounded case·7일내검토/보존기한별결정,무기한재시도·보관금지.미승인제안이며계획/코드변경없음.
- 2026-10-12 TMI-204 이해 확인: Billing 자체DB checkpoint+Identity203 API 조회(상대DB 직접접근 아님),feed 생성 중단 아닌고정H2,재시도 횟수와generation/version 분리 설명. legacy 소실은복구 증거 없으면 COMPLETE 불가이나새stream 추적과구분,cohort/활성화예외 별도승인.제안7개 recovery collection 모두신규·기존201서비스변경을구분.설명/기록만.
- 2026-10-12 TMI-204 계획 상세 설명: snapshot 고정시점/H·feed 고정목표H2,DB 반영 후ACK/outbox,복구generation·control/barrier,legacy COMPLETE와stream CAUGHT_UP 구분,7개 로컬 저장소·7일 임시자료 정리 및4단계PR/203 의존성을 사례로 설명. 계획/구현 승인 확대 없이 기록만 추가.
- 2026-10-12 TMI-204 전용 계획 작성: docs/plans/TMI-204-withdrawal-recovery.md.201 공통 applier·account/push/recovery control CAS, snapshot bounded 적용/7일 staging hash·marker,고정H2 feed·durable ACK/유실 대사,복원 세대 설치/old worker 차단,203 exact API·운영 role/환경 gate와4개 PR 단계 제시. 신규local7개 collection/index·worker/의존성은 기술 제안으로 구분, 기존16개 index/공동wire 변경 없음. 계획 검토·구현 승인 대기, 코드/타 서버/Jira/배포 변경 없음.
- 2026-10-12 TMI-204 계획서 존재 확인: 로컬 docs에 전용 구현 PLAN은 없고 PLAN-009/탈퇴 설계/공동 기술 계약/TMI-199 exact 규격에 복구 범위와 계약이 분산돼 있음. 다음은 기존 승인 계약을 모아204 전용 구현 계획 작성. 사용자 전달상 Identity202 진행 중이며 실제 상대 진행 재조회 없음. 코드/계약 변경 없음.
- 2026-10-12 TMI-201 전체 코드 설명: payment16개 production 파일과 global 인증/응답·DB manifest를 기준으로 상품 조회/계정 발급/탈퇴 수신, lifecycle CAS·cursor·중복 처리·조건부 삭제 후보 및 테스트를 연결해 안내. 구현/정책 변경 없이 기록만 추가, 직전393개 테스트 결과와 실제 연동 미검증 구분 유지.
- 2026-10-12 TMI-201 PR2 구현·로컬 검증 완료: 탈퇴 POST4필드 strict decoder·inbox, lifecycle/account WITHDRAWN·ref 비활성·15일 cleanup 후보를 단일 Transaction 처리. 중복204/충돌409/DB503, 계정 없는 탈퇴/발급 경합·legacy 나노초 보존, 신규4개 exact index·기본OFF 수신 flag/환경 gate/안전한 tracing 추가. clean test393개 성공(실패/오류/skip0), manifest16개 일치. 실제 삭제/환불/복구·타 서버/Store/AWS 변경 없음. PR1+PR2 코드 완료이나 병합/Jira 종료·실제 연동/배포 별도. 다음 PR 검토 후 Identity202 연동 및204/205/206 후속. 상세 docs/runbooks/TMI-201-payment-foundation.md.
- 2026-10-12 PLAN-009 Jira 범위 재확인: 직접 Billing 구현은 TMI-201(PR1 상품/account·PR2 탈퇴 수신/차단), 관련 계약·Identity·복구/대사·공동검증 트랙은199~206. Jira 실제 조회상199 완료·200~206 해야 할 일. 최신 PLAN-009는PR2 최종 회귀 검증 중이며 Jira/병합/배포 상태와 구분.205는010 거래 증거·206/활성화는013 gate와 연결. 설명/기록만, Jira 수정 없음.
- 2026-10-12 Billing 계정 의미 추가 설명: 별도 로그인 계정이 아닌 userId와 purchaseAccountRefId를 연결하는 결제 관리 기록. WITHDRAWN은 Identity 탈퇴 통지를 반영한 로컬 사용 차단 표시이며 ID 교체·즉시 삭제·환불 아님. 코드/계약 변경 없음.
- 2026-10-12 TMI-201 PR2 질의 확인: 수신 endpoint는201/Billing, 발송은202/Identity. WITHDRAWN은 ID 교체가 아닌 Billing lifecycle/account 상태이며 ref 비활성화와 구분. 발급/탈퇴는 공통 DB fence/Transaction으로 직렬화.201은 차단·cleanup 후보,205는 거래 확인 후 조건부 삭제. 설명/기록만 변경.
- 2026-10-12 Apple 심사 전 상품 조회 안내: 등록된 실제 Store 상품은 승인 전 sandbox/TestFlight에서 조회·구매 시험 가능하나 계약 활성/상품 정보·판매국가/Bundle ID·RC iOS SDK 키·Offering 매핑이 필요. 9/16 premium Offering 5종 연결 기록과 실제 SDK 조회 성공은 구분. Billing catalog API는 별도 서버 설정/배포·flag gate이며 Store 자동 동기화 아님. 이번에는 공식 RC 문서 대조/설명·기록만, 실조회·설정 변경 없음.
- 2026-10-12 Apple IAP 심사 준비 안내: 9/16 등록 기록과 최신 Apple 공식 도움말 대조. 첫 소모품 심사는 새 앱 버전과 함께 제출, 실제 구매/권리 사용 빌드·상품별 심사 스크린샷/메모·심사용 로그인·유효 유료 앱 계약/세금/은행·sandbox E2E 필요. 1024px 홍보 이미지는 일반 IAP 심사 스크린샷과 별개. 한국 storefront 제한과 SANDBOX→staging의 실제 심사 빌드/account 연결 검증 필요. 현재 ASC 실시간 상태/누락 필드·앱 결제 구현은 미확인, 설명/기록만 수행.
- 2026-10-12 Google 1일권 비활성화 완료: 사용자 변경 요청에 따라 premium1d 구매 옵션의 비활성화와 확인 적용. 저장 완료·상세 재진입 후 비활성 상태 확인, 이름/짧은 설명/대한민국 KRW9000 유지. 나머지 4종 초안은 변경하지 않음. 코드·계약·Jira·배포 변경 없음.
- 2026-10-12 Google 나머지 4종 초안 저장 완료: 사용자 `초안 저장까지` 승인 후 premium3d/7d/14d/28d 저장, 각 상세의 임시 상태·짧은 설명·대한민국 구매 옵션 확인. 가격 KRW19000/29000/49000/69000, 최종 목록의 네 상품 활성 옵션 0 확인. 활성화 실행 없음·아이콘 미등록. 목록에는 기존 premium1d의 활성 옵션 1이 관측되어 이전 미저장 기록과 달라졌으며 이번 작업에서 1일권 저장/활성화는 수행하지 않았다. 코드·계약·Jira 변경 없음.
- 2026-10-12 Google 나머지 4종 미저장 입력: 3일/1주/2주/4주 각각 premium3d/7d/14d/28d·기존 프리미엄 상품 이름과 `토선생 {기간} 무제한 이용권` 짧은 설명, 동일 구매 옵션 ID/구입, 대한민국만 사용 가능, KRW19000/29000/49000/69000 입력·가격 행 확인. 기존 생성 보류 요청의 저장 범위를 비동기 확인 중이며 초안 저장/활성화는 실행하지 않았다. 4개 별도 탭과 기존1일권 탭 유지, 가격 화면 증거 확보. 4주권 다중수량 제한 안내 확인. 코드·Jira·계약 변경 없음.
- 2026-10-12 TMI-201 PR2 계획 설명: Identity 탈퇴 push 수신·strict 검증·inbox 멱등·lifecycle terminal 차단·account/ref 비활성·15일 cleanup 후보와 발급-탈퇴 경합 테스트가 범위. 실제 삭제/거래 확인·누락 복구·지급/환불은 후속이며 이번은 설명/기록만, 코드·Jira·배포 변경 없음.
- 2026-10-12 TMI-201 PR1 사용자 요청 구현: 상품 GET·stable 구매 계정 POST, read/purchase+MEMBER 권한·독립 OFF flag, lifecycle/account/ref/cursor Transaction·분산 fixed-minute limiter·12개 additive index·안전한 로그/span 구현. 전체 clean test363개 성공/실패0/skip0. PR2 탈퇴 consumer/생성-탈퇴 경합·실제 catalog migration/Identity/ALB/Store 연동은 미완료이며 TMI-201 전체 완료 아님. 무료권/internal 계약 유지, 타 서버·Jira·commit/push·배포 변경 없음. 상세 docs/runbooks/TMI-201-payment-foundation.md. 다음은 PR1 검토 후 PR2.
- 2026-10-12 Google 1일권 설명 문구 수정: 사용자 선호에 따라 미저장 폼 설명을 `토선생 1일 무제한 이용권`으로 변경·화면 확인. 상품 이름/ID는 유지, 생성/저장/활성화 미실행. 입력 화면 유지 및 스크린샷 확보. 코드·계약·Jira 변경 없음.
- 2026-10-12 Google 1일권 미저장 입력: 사용자 요청으로 생성 폼에 premium1d·토선생 프리미엄 1일 이용권·24시간 설명을 입력하고 다음 단계 구매 옵션 ID premium1d/유형 구입·대한민국만 사용 가능·KRW9000을 입력했다. 활성화/임시보관함 저장은 누르지 않았으며 아이콘 미등록, 입력 화면 유지. 가격 행 실제 확인·스크린샷 확보. 영구 상품 생성/판매 활성화·RC 매핑·코드/Jira 변경 없음.
- 2026-10-12 Google 상품 입력안 안내: 9/16 Apple 등록 기록의 premium1d/3d/7d/14d/28d ID와 한국어 이름·기간 설명·국내 5가격을 Google에도 동일하게 사용할 입력안으로 제시. 한국어/대한민국/KRW, 자동 갱신 없는 일회성 구매·Billing 기간 판정·재구매 가능 소모성 처리 방향 유지. 실제 Google 생성/옵션 설정·아이콘 업로드·RC 매핑 없음. 기록만 변경, Gradle 미실행.
- 2026-10-12 사용자 확인: 탈퇴 후 재가입 잔여 유료권 복구/이전 및 별도 수동 복구 기능을 제공하지 않는 현재 범위 명확화. 결정서/ADR-004/결제 요약 반영. 무료 승계·금융 보존·Store/법정 환불은 불변, 탈퇴 전 안내 필요. 코드/UI/Jira 변경 없음.
- 2026-10-12 TMI-201 질의 설명: products는 Billing catalog이며 앱 SDK 가격/상품과 결합, 정상 빈 목록은[]/장애503 구분. ref는 동일 사용자·환경에서 기기/Store와 무관한 stable 값, 재가입 새userId는 별개. cursor는 callback 유실 거래 탐색/최근 계정 우선 확인 기반.429는 고정 분 경계 잔여 초 Retry-After,201 수신/202 발송, 유료권 동일번호 재가입 자동 복구 금지 계약 설명. 정책/코드 변경 없음.
- 2026-10-12 Google 상품 등록 가능 상태 확인: 사용자 제공 Play Console에서 프로덕션/내부 테스트 2.0.1 표시, 일회성 제품 목록 결과 없음, 일회성 제품 만들기의 상품 ID·이름·설명 입력 폼 접근을 직접 확인했다. 현재 상품 등록 시작 가능하며 과거 Google 등록 미완료 기록과 구분한다. SDK 정확 버전/결제 동작·가격 단계/저장 성공·RevenueCat 매핑·실결제는 이번에 검증하지 않았고 입력/생성/설정 저장 없음. 코드·Jira·배포 변경 없이 기록만 추가.
- 2026-10-12 TMI-201 상세 설명: 최신 PLAN-009/세부 계약 기준으로 상품 catalog와 Store 가격 구분, stable 구매 reference/원자 생성·lifecycle fence·cursor, public JWT/internal SigV4 격리, 탈퇴 차단/cleanup 후보 및 테스트/배포 gate 안내. 구현/계약/Jira 변경 없음.
- 2026-10-12 TMI-201 계획 정리 완료: PLAN-009 및 foundation/withdrawal 세부 문서의 Jira·상태·199 수락 반영. PR1 상품/account+lifecycle/cursor, PR2 탈퇴 수신·차단/cleanup 후보로 구분하고204 복구·205 대사/purge·206/013 운영 gate 분리. 반복 account/cursor 합의·exact index/보존 구표현 갱신, 미검증 운영값은 제안 유지. 문서만 수정, 구현 착수·Jira/배포 변경 없음.
- 2026-10-12 TMI-201 계획서 위치 확인: PLAN-009가 상위 계획, foundation technical contract와 withdrawal 설계가 세부 근거.201 전용 파일은 별도로 생성하지 않음. 헤더의 Jira 미생성/과거 미승인 문구는 최신199 완료·공동 규격 수락을 반영하지 못한 구표현임을 안내. 구현 전 상태/범위 정합성 갱신 필요, 이번에는 설명·기록만.
- 2026-10-12 TMI-199~202 범위 설명:199 공동 규격/fixture 검증 완료,200 Identity 구매 권한/control,201 Billing 상품·stable 계정/탈퇴 수신,202 Identity 원자 탈퇴 capture/독립 delivery. 실제 결제 지급/환불·복구 API·자동삭제·판매 활성화와 구분,199 이후200/201/202 병행 가능하되 Identity 공통 Transaction 변경 조율 필요. 설명만 수행.
- 2026-10-12 재개 상태 확인: 로컬 develop=c7baf0b(TMI-199 PR#10), 결제 production 패키지 추가 없음. Jira199 완료,201/200/202 해야 할 일 재조회 확인. 마지막 작업은 공동 계약/fixture 검증 종료와 다음 Billing201 안내였으며 실제 결제 기반 구현은 아직 시작하지 않음. 무료 핵심/reader 구현과 실제 배포는 구분. 기존 미커밋 CURRENT_STATE/WORKLOG 보존, 상태 설명만 수행.
- 2026-10-07 다음 작업 안내: Jira201/200/202 재조회(모두 해야 할 일). Billing201은 PLAN-009 상품·구매 계정 기반→탈퇴 수신 두 PR 순서, Identity200 구매 scope/control 및202 capture/delivery와 fixture 기반 병행 가능. 실제 연동·활성화는 상대 구현/gate 후. 설명만 수행, 코드·Jira 변경 없음.
- 2026-10-07 TMI-199 완료: 사용자 요청으로 GitHub Billing PR#10(c7baf0b), Identity PR#53(78a6e6ae)의 MERGED/base develop 및 로컬 기준 브랜치 파일 반영 확인. Jira 완료 전환 후 재조회로 완료 확인. 공동 규격/독립 테스트 준비 단계 종료이며200/202/203/204/206 운영·구현 gate 유지. 코드·타 서버·배포 미변경.
- 2026-10-07 TMI-199 Identity exact 수락 회신 확인: §5 wire/null/nested DTO·§6 manifest 수락, 추가42건 독립 검증/전체1276건 실패0·기존skip6은 상대 보고. 신규 JSON2종 해시 Billing 재확인 일치. exact 규격 공동 동결·기술 수락 대기 해소를 문서에 반영. 병합 확인은 미수행, Jira 변경 없음;200/202/203/204/206 구현·운영 gate 유지.
- 2026-10-07 TMI-199 exact 수락안 보완: TMI-199-EXACT-WIRE-AND-INDEX-SPEC에 필수/null/상태별 생략·nested DTO·오류·manifest 옵션 명시. 기존 JSON4개 유지, numeric-boundaries39건/overflow1건/Identity13index+int64정렬 Mongo1건 추가. 전체 clean test326건 실패0/skip0. 새 exact안의 Identity 수락·신규 fixture 독립 실행 남음; 공동 Done·운영 구현·Jira 변경 아님.
- 2026-10-07 TMI-199 후속 권장안 사용자 승인: 신규 withdrawnAt 동일 밀리초·기존 event/digest 보존, decimal wire/BSON int64·T unsigned32, internal 오류/양방향 전용 Lattice SigV4/additive index 및200/202/203/204→206 검증 분담 채택. 공동 계약 §4와 FOUNDATION 동기화. 후속 exact 규격 Identity 수락·nullable/manifest 검증·담당자/환경 배정은 별도; Jira 완료/수정·코드·배포·판매/purge 변경 없음.
- 2026-10-07 TMI-199 잔여 기술 권장안 설명: 신규 withdrawnAt 동일 밀리초·기존 digest 보존, decimal wire/BSON int64와 T unsigned32 분리, internal 오류·최소권한 SigV4·additive index 동결 권장. 200/202/203/204 로컬 검증과206 공동 staging 분담 제안. FOUNDATION에 승인 전 권장안 기록; 사용자/상대 수락·실제 담당/환경 배정·Jira 변경·코드 구현은 하지 않음.
- 2026-10-07 TMI-199 Identity 최신 회신 §7 반영: 독립44건/전체1233건 실패0·기존skip6은 상대 보고, JSON4종 해시는 Billing에서 직접 일치 확인. User/outbox converter 왕복 시각 일치와 저장 전 고정밀 혼합 위험 구분. FOUNDATION §6.3에 반영; 아래 최초 작업의 Identity 교차 실행 대기는 해소. exact 시각/DTO/null/숫자 타입·오류/auth/index 동결과 실제 Mongo/staging gate 배정은 남음. 상대 테스트 XML 직접 확인/재실행은 하지 못했으며 production·Jira 변경 없음.
- 2026-10-07 TMI-199 로컬 수행: src/test/resources/contracts/payment-lifecycle/v1 JSON4종, 계약 oracle39개·Mongo feasibility10개 추가. 고정T/H·rollback/counter·old-generation 충돌·16index·activity 분리·single-member 재선출 실측, unknown commit/SnapshotTooOld는 failpoint 주입으로 구분. ./gradlew clean test285개 성공/실패0/skip0. docs/contracts/TMI-199-PAYMENT-LIFECYCLE-FOUNDATION.md 및 ADR/009 account-cursor 정합성 기록. 새 fixture Identity 교차검증·DTO/envelope/index 수락·다중노드/실제history·운영gate 남음, Jira 완료/상태 변경 안 함. production 코드/외부 설정 미변경.
- 2026-10-07 사용자 요청으로 TMI-137(결제 기능) 에픽 아래 작업8개 생성·parent 확인: TMI-199 공통 fixture/schema, TMI-200 Identity 발급/control, TMI-201 Billing 상품/account·탈퇴 수신, TMI-202 Identity capture/delivery, TMI-203 Identity 복구 API, TMI-204 Billing recovery, TMI-205 대사/purge, TMI-206 공동 staging gate. 모두 해야 할 일·미할당. 선행조건/완료기준은 description에 기록(별도 dependency link 생성 없음). 첫 순서199→200/201/202. 코드/배포 미변경.
- 2026-10-07 사용자 Identity 확인 완료 전달 및 구현 분할 안내: 상대 코드/배포 완료로 해석하지 않음. 공동 fixture/실현성 gate 후 I1 control·발급, I2 capture/publisher, B1 account/lifecycle consumer 병행; I3 recovery API→B2 복구 client·generation; B3 대사/조건부 purge는010 실제 거래 확인 의존, 이후 공동 staging/013 gate 권장. 기존009~013 유지·하위 작업 분리, Jira/구현/외부 변경 없음.
- 2026-10-07 공동 기술 계약 개정안 사용자 승인: capturedAt 원천120일·snapshot7일·operation120일/current stream 최소 증거/폐기 stream120일·신규token30분/skew60초 및 기술 설계/초기값 채택. 계약/ADR/결정/009 PLAN/Identity 인계 동기화. Identity 합의/기술·보존 검증·실제 이관/코드 착수/Jira·배포/판매/purge와 개별 예외 연장은 별도. 코드/외부 설정 미변경.
- 2026-10-07 공동 계약 추가 보완: contentDigest 응답/ACK 통일·저장 metadata 별도 검증, Identity authority의 consumerRecoveryGeneration·privileged recovery begin·local/remote CAS·old ACK/worker 차단·restore barrier/새 세대 baseline 규격 추가. readiness startup/runtime 범위와 상태 enum 정리. 기존 보존/배포 미승인 유지, 코드/타 서버 미변경. 개정본 공동 검토와 fixture 검증 필요.
- 2026-10-07 Identity 개정본 재검토 회신 §7 확인: R1~R5는 문서 대응 수용으로 갱신됨. 추가 P1 consumerRecoveryGeneration(consumer-only restore/RESYNC의 old worker·ACK fencing), P2 manifestDigest 미정의, readiness 영향/enum 정합성 지적 확인. 현 로컬 5줄 결론은 commit 기산120일을 명시하지 않아 해당 요약 지적은 버전차 가능; §3/5.2는 capturedAt 관측 기준. 계약/코드 미변경, 두 프로토콜 보완 권장·정책 승인 여전히 별도.
- 2026-10-07 공동 기술 계약 본문 보완: R1 BASELINE/RESYNC/FEED 및 checkpoint status, R2 고정H2 cursor/연속 done, R3 replay floor와 consumer 증거/GAP 분리, R4 control 원자 초기화/legacy 이관 gate, R5 일반 로그인503·기존 reissue 복구/expired replay 구분 반영. majority/snapshot/restore incarnation·capturedAt 관측 기준·retry counters/HTTP·canonical fixture·snapshot operation 보존 추가. 새 metadata 보존/30초 commit budget 등은 제안이며 승인 확대 없음. 코드/타 서버/Jira/운영 미변경, 개정본 공동 검토 필요.
- 2026-10-07 공동 기술 계약 Identity 회신 재검토: docs/contracts/IDENTITY-BILLING-TECHNICAL-CONTRACT-REVIEW-2026-10-07.md. R1 baseline ACK/R2 고정H2/R3 정상 retention과 consumer GAP/R4 control 초기화·이관/R5 일반 로그인 unknown commit 분리 모두 타당. 원문·코드 변경 없이 개정 권장안과 durability/restore/capturedAt/canonical·retry schema 보완 기록. 신규 보존/TTL 승인 아님, 다음 공동 계약 개정 후 합의 필요.
- 2026-10-07 공동 기술 계약 권장안 작성: docs/contracts/IDENTITY-BILLING-PAYMENT-LIFECYCLE-TECHNICAL-CONTRACT.md. ACTIVE MEMBER 공통 발급/탈퇴 commit fence·원 exp replay, 독립 sequence capture·고정 snapshot T/H와 연속 feed 완료/legacy coverage 분리,204-only/20회 retry/BLOCKED_AUTH·재개/rollback 규격 제안. 원천120일·snapshot7일·신규token상한30분/이관은 별도 승인 대상. 기존15일/운영 주기 유지. 코드·타 서버/Jira/외부 변경 없음, provider·Mongo snapshot/과거TTL·coverage 검증과 양 서버 합의 전 판매/purge OFF.
- 2026-10-07 Identity 인계 회신 검토: docs/contracts/IDENTITY-PAYMENT-HANDOFF-REVIEW-2026-10-07.md. issuer scope/lowercase·LC2xx ACK·backfill bounded 조회 로컬 재확인, 전체 발급/replay 경합·원천 보존/commit-consistent snapshot·Retry-After 동결 필요. 기존15일/운영 주기 유지, 구현/계약 변경 없음. 다음은 공동 기술 보완안이며 판매/purge 검증 완료 아님.
- 2026-10-07 Identity 서버 인계서 작성: docs/contracts/IDENTITY-PAYMENT-ACCOUNT-LIFECYCLE-HANDOFF.md. ACTIVE MEMBER purchase scope 전체 발급 경로, 기존4필드 탈퇴 event/목적지별 durable delivery, Billing SigV4·LC JWT 분리, snapshot/feed gap/원천 보존, token lifetime/userId 비재사용 검증과 회신 양식 명시. 확정 정책/기술 제안 구분, Identity 코드·외부 전송/Jira/배포 미변경. 회신 후 계약 동결·구현 승인 필요.
- 2026-10-07 탈퇴 cleanup 운영 주기 승인: 삭제 대상 매시간 점검, 미확인 건 매일 재확인, 최초 미해결 판정 후 7일 이내 담당자 검토. 설계/ADR/009 PLAN/결정 원장 동기화. 반복 재시도로 검토 기한 연장 금지. batch/lease/24시간 경보·반복 담당 검토 주기·예외 보존/coverage는 별도 제안·검증 유지. 코드/실제 purge/외부 변경 없음.
- 2026-10-07 탈퇴 수신·계정 상태·대사·삭제 설계 초안: docs/contracts/PLAN-009-withdrawal-reconciliation-and-purge-contract.md 작성, ADR/009 PLAN·기반 계약 링크 연결. Identity 기존4필드/LC JWT 유지·Billing 독립 SigV4 delivery 제안, lifecycle CAS·15일 조건부 대사/purge·늦은 거래 격리·복원/테스트 명시. provider 거래 부재 coverage·누락 feed·token drain·예외 보존 미검증으로 실제 purge OFF. 반복 account 무변경/ADR activity 요구 충돌을 보고하고 별도 cursor 갱신안 제안, 합의 전 해당 구현 중단. 코드/Jira/타 서버/배포 미변경.
- 2026-10-07 미구매 탈퇴 계정15일 조건부 삭제 승인: withdrawnAt+15일 후 거래 대사 완료/실제 구매·처리 중 결제·미해결 없음일 때 계정/reference/사용자 연결 정리. 금융 보존/미해결 최소 증거 분리, TTL 일괄 삭제 금지. ADR/009 PLAN·세부 계약/결정 동기화. 주요 제품 선택 완료, 탈퇴 전달·대사/삭제 manifest·Identity purchase 발급·정식 schema 기술 검증/구현 승인 남음.
- 2026-10-07 미구매 탈퇴 보존 선택지 제안: 7일/30일/90일 운영 후보 비교, 30일 조건부 정리 권장(미승인·법정/provider 보장 아님). authoritative withdrawnAt 기준·인지 즉시 신규 용도 차단·로컬/RC 대사 및 미해결 예외 최소 기록·정기 재검토 필요. JWT 잔존/지연 탈퇴 event로 삭제 계정 재생성 방지 및 늦은 거래 격리 조건 유지.
- 2026-10-07 009 남은 결정 안내: 사용자 정책상 미구매 탈퇴 account/reference의 삭제 유예기간·늦은 거래/미해결 예외 처리 미확정(30일 후보, 확정 아님). 탈퇴 전달/대사, Identity purchase 발급, exact schema/보존 manifest는 개발 계약·검증 사항. 운영 입력/Jira/구현 승인과 실제 판매 gate 별도 유지.
- 2026-10-07 purchaseAccountRefId 필드 암호화 생략 승인: Atlas 사용 사용자 확인, 저장 암호화/TLS/최소권한/로그 제외 유지·Store token/credential 별도 보호 유지. ADR/009 초안·결정 기록 동기화. 앞선 A/A/보존A/JWT만료A 선택도 기록; 30일 유예/정식 manifest·구현 미확정. 코드/실제 설정 미변경.
- 2026-10-07 DB 저장 암호화 확인: 로컬 application.yml은 MONGODB_URI 주입만 나타내므로 운영 at-rest 적용 여부 확정 불가. 공식 Atlas 문서는 기본 AES256 저장 암호화 항상 활성 명시; 실제 Atlas 사용 시 적용되는 조건부 사실로 안내. 실제 배포/DB/credential 미조회, 필드 암호화와 구분.
- 2026-10-07 009 선택지 코드 조사: Identity issuer read만 합성·로그인/refresh ACTIVE 검사·탈퇴 refresh폐기/기본TTL30분 및 Billing 현재상태 미조회 확인. 고정 window/subset 권장, 보존 조건부 정리·필드 암호화와 동기 ACTIVE 조회는 미승인 제안으로 구분. docs/contracts/PLAN-009-foundation-options-review-2026-10-07.md에 근거/장단점 기록. 실제 배포/권한·법정 보존 판단/코드 변경 없음.
- 2026-10-07 PLAN-009 기술 초안 설명: 상품 목록/본인 stable 구매 reference 두 기능, JWT read/purchase 분리, 계정 원자 생성·인덱스·분산 요청 제한·Google 미등록 비노출·무료 flag 격리를 설명. 결제/지급/환불 구현 완료 아님. 고정 window/index/privacy·ACTIVE 발급 상세는 검토 대상 유지.
- 2026-10-07 PLAN-009 세부 계약 초안 작성: docs/contracts/PLAN-009-payment-foundation-technical-contract.md. 상품/account API 예제·오류/헤더·무료 security 분리·원자 get-or-create·Mongo fixed-window limiter·foundation subset index·Google 미등록 조건/테스트 명시. PLAN-009/ADR 링크 연결. 신규 schema/limiter·privacy/Identity ACTIVE 계약은 검토·동결 대상이며 코드/Jira/외부 변경 없음.
- 2026-10-07 다음 작업 안내: 사용자 확인상 Google 상품 미등록. 실거래 검증은 등록 후 gate로 유지하고 우선 PLAN-009 관련 Phase0 API/인증/schema·rate limit 상세를 작성·검토한 뒤 Jira/구현 승인 순서 권장. 나머지 paid 채점/환불 보완 계약은 해당 slice 전 동결, Google 미등록을 전체 설계/기반 개발 중단 사유로 삼지 않음.
- 2026-10-07 Google Orders 보완 사용자 승인 반영: RC 부족 시 Google 소모성 부분 환불 읽기 조회를 검증 증거로 허용하는 제한적 예외를 AGENTS/ADR/PLAN-012/결정에 반영. 공식 GET·partialRefundEvents 성공 상태/시각/세금 포함 금액 확인. RC v2 Web Billing 환불 endpoint를 Store 환불로 혼동하지 않음. 실제 consumed 거래/API 권한·누적/멱등 증거·Apple 부분 금액 미확인; 코드/계정 변경/실제 환불 없음.
- 2026-10-07 운영/provider 조사: 공식 RC HMAC·delivery 재서명 문서 확인(계정 실측 미완료). RC Google 부분 금액 미수신 제한과 Google Orders 부분 refund 상태/금액/시각 조회 확인. RC-only 자동 처리 보장 불가; direct 조회 보완은 별도 신뢰 계약 승인 제안일 뿐 미구현. 운영 DB/실제 이관 시간 미측정; 실측 제한 이관·담당/대체자 최소 권한·단계 출시 권장. 상세 PAYMENT_OPERATIONS_PROVIDER_RESEARCH-2026-10-07.md. 계약/코드/외부 변경 없음.
- 2026-10-06 세부 계약 확정 범위 안내: 신규 상품 정책 재선택 없이 API/멱등 승인·환불 job/schema·guard CAS·flag 상세를 개발안으로 작성할 수 있음. LC 합의와 실제 provider 증거는 승인만으로 대체 불가. 운영 제한 시간/담당 권한·복구 절차·실제 판매는 별도 승인, 이번 질문은 추가 승인 아님.
- 2026-10-06 F1~F4 모두 A안 승인 반영: 전용 멱등 paid 채점 승인·영속 exact Session 증거, 구매 root 차단+원자적 durable job/분할 전파, exact guard 전이표와 짧은 관련 writer 제한 이관, 환불 선행 자동 활성화 gate+최종 운영 승인. PLAN-008/010/011/012/013·ADR-004·결정 기록 동기화. route/DTO/IAM/schema 및 실제 이관 시간/실행은 후속 동결, 코드/Jira/타 서버/배포 미변경.
- 2026-10-06 F1~F4 선택지 제안(미승인): 전용 멱등 paid 채점 승인 command, 구매 root 차단+durable 분할 전파, exact guard 전이표+짧은 관련 writer 제한 이관, 환불/회수 선행 자동 활성화 gate를 권장. versioned event ack·전체 단일 Transaction·온라인 이관·수동 체크 대안과 비교. 중단 허용/실제 시간·wire/schema/ADR 변경은 승인 후 상세화, 이번은 기록만 변경.
- 2026-10-06 종합 리뷰 재확인: F1~F4를 현재 Controller/Service와 PLAN-011/012/013에 대조해 모두 타당한 구현 전 계약 공백으로 확인. STALE/duplicate204는 exact Session 채점 승인 증거가 아니며 source v2만으로 해결되지 않음. 대량 환불 root deny/재개 가능한 전파, guard 전이/CAS 이관, 환불 기능 선행 flag 표가 필요. 계약/코드 수정 없이 기록만 갱신.
- 2026-10-06 PLAN-008~013 종합 리뷰: 분할과 최신 R1/R2·PublicResponse·부분 환불 방향은 정합하다. 구현 전 보완은 paid GRADING 승인 ack(기존 event 204는 STALE도 포함), 무제한 group revoke의 bounded fan-out/root deny, 공통 guard 상태 전이·온라인 backfill CAS, staging refund processor/publisher 선행 flag 순서다. 상세 [계획 리뷰](../plans/PLAN-008-013-REVIEW-2026-10-06.md). 계획/ADR 본문·코드·타 서버·Jira·배포 수정 없이 리뷰와 기록만 추가. 정적 문서/코드 대조·diff/링크 검증, Gradle 미실행. 기존 정책 승인과 미완성 exact 계약을 구분하며 이번 리뷰는 구현 승인이 아니다.
- 2026-10-06 PLAN-013 방향 승인: 실제 연동 검증·migration·단계별 활성화·신규 판매와 기존 거래 복구 분리·문의/보존/백업/경보 운영 방향을 사용자 승인으로 기록. 기술 상세·운영 담당/권한·실제 배포/판매/환불 실행 및 판매 gate 검증은 별도이며 구현/외부 변경 없음.
- 2026-10-06 PLAN-013 설명: 실제 Store/RC·앱·Identity/LC 연동, schema/legacy 이관, 단계별 활성화, 판매 중단과 기존 결제/환불 복구 분리, 문의 담당·보존/백업·경보·출시 gate를 안내. 이번 요청은 설명이며 배포/외부 환불/계획 전체 승인 아님.
- 2026-10-06 PLAN-011 확정 항목 안내: 추가 상품 정책은 없으며 LC source 구분의 capability-gated v2와 Billing GRADING commit 후 paid Job 실행을 기술 계약 승인 대상으로 구분. exact route/DTO/version/IAM·guard 해제/legacy coverage·동시성은 상세 설계/검증 필요. 질문을 승인으로 처리하지 않음.
- 2026-10-06 PLAN-011 설명: ACTIVE paid-first/free-preserve, reserve→Session commit→confirm, 사용자 공통 동시 응시 guard, 구매별 불변 source·최소 이용 증거 및 정상 만료/환불 차이를 안내. Learning Core source 계약·채점 시작 승인 연동은 후속 합의/검증 대상. 010 검토 보류를 승인으로 해석하지 않으며 011 구현·활성화도 미수행.
- 2026-10-06 PLAN-010 설명: 사용자 요청으로 Identity billing:purchase 발급은 후속 유지(면제 아님). 구매 sync·webhook·주기 재조회로 검증된 거래를 단일 원장/기간권에 반영하고 PENDING·중복/누락 복구·기간 stacking을 설명. 010만으로 시험/환불/판매 완료 아님, 계획 구현 승인으로 간주하지 않음.
- 2026-10-06 Identity REFUND 확인 반영: 로컬 HEAD cc076442의 분류·JWT/현재 ACTIVE 계정 userId·익명401·body userId400·멱등 재응답 전 검사 및 service/web 테스트 확인. ACTIVE Guest도 문의 허용(구매 권한과 별개). PLAN-008/013·ADR·결제 결정/요약·구매 안내를 구현 확인/배포 미확인으로 갱신. 실제 flag·Slack·프론트·거래 연결·테스트 실행은 미확인. billing:purchase 발급은 별도 후속. 타 서버 미수정.
- 2026-10-06 PLAN-009 설명: 상품 매핑 조회·환경별 본인 구매 reference get-or-create·JWT read/purchase 구분과 무료 불변/판매 OFF 설명. 추가 제품 정책 선택은 없으나 purchase scope/계정 상태·기술 상세 검증 및 구현 승인은 남음. 설명만 수행, 코드/Jira/계약 변경 없음.
- 2026-10-06 PLAN-008 두 선택 승인: 단계별 진행 및 PublicResponse 통일. ADR §5.3 direct DTO를 result 내부로 명시하고 사용자 오류 envelope도 통일, internal204/provider200 유지. PLAN-008/009/010 및 결정 원장 동기화. 전체 세부 PLAN/코드/Jira/배포 승인은 아님.
- 2026-10-06 PLAN-008 결정 항목 안내: 현 시점 사용자 선택은 단계별 진행 구조 및 009의 기존 PublicResponse 재사용 방향 승인이다. provider 인증/거래·부분 환불 증거/LC wire/schema는 조사·기술 동결 대상, 환경 입력·운영 담당·최종 고지는 배포 전 준비로 구분. 이번 질문은 승인으로 간주하지 않음.
- 2026-10-06 PLAN-008 설명: 전체 로드맵과 Phase0 기술 확인,009~013 순서·타 서비스 책임·단계 완료/판매 가능 구분을 사용자에게 설명. 가격/환불 정책 재선택 없음. 질문을 계획 승인으로 간주하지 않았으며 신규 정책·코드·Jira 변경 없음.
- 2026-10-06 결제 계획 6개 작성: PLAN-008 전체 로드맵/Phase0,009 상품·계정·public 인증,010 구매 검증·timeline·sync/복구,011 paid-first·공통 guard·구매별 최소 증거,012 부분 환불 금전 원장/R1·R2·LC revoke,013 migration/문의/보존/E2E/운영. 모두 검토·승인 대기. 기존 ADR direct DTO와 PublicResponse 재사용 제안, LC source 인식 wire, 실제 RC HMAC/부분 금액·식별자/시각과 exact schema는 Phase0 gate로 명시. ADR/AGENTS에 계획 링크 추가했으며 신규 기술 제안을 승인으로 바꾸지 않음. 코드·Jira·타 서버·실제 환경 미변경.
- 2026-10-06 R1/R2 최종 사용자 승인 반영: 정상 환불 1건 처리 종료와 외부 추가 반환/정정 원장 보완 분리. 권리 종료/reflow는 한 번, 불명 금액은 대사. providerConfirmedAt nullable·firstVerifiedAt·appliedAt 분리, 후속 미시작 권리는 최초 Billing 반영 기준으로 앞당겨 지연 소급 소진 금지. 이미 시작한 다른 권리/기존 시작 보장 유지. C9-S2·ADR §5.7·결제 요약·AGENTS 동기화. 이전 미승인 기록은 역사이며 exact provider 금액 식별/정정 schema·PLAN·코드는 여전히 후속이다.
- 2026-10-06 환불 R1/R2 권장안 제시(미승인): 정상 환불 건 처리와 외부 추가 반환/정정 원장 반영을 분리하고 권리 종료는 한 번, 검증된 실제 금액만 증분 기록. 시각은 provider 확정·최초 검증·반영을 분리하며 미시작 후속 이용권은 Billing 최초 반영 기준으로 앞당겨 알림 지연으로 소급 소진시키지 않는 안을 제안한다. 이미 시작한 다른 권리/기존 시작 보장/기간은 유지하고 중복·후속 정정으로 재지급하지 않는다. C9-S2 기존 refundConfirmedAt reflow와 달라 승인 필요, 계약 본문 미변경.
- 2026-10-06 구매별 이용 증거 설계·문서 현행화: ADR-004 §5.8.1에 문의자→검증된 구매→Reservation/Group/Session source·최소 완료/실패 증거, 무료/취소/replacement/삭제 원본 구분, Transaction·TTL 독립·접근 감사/보존 구분과 PLAN 테스트를 추가했다. AGENTS/통합 계약/결제 요약/C9-S3·S10/구매 안내를 REFUND 문의·Store별 처리·부분 환불 잔여권 종료 범위와 정합화. 반복 환불은 일반 기능이 아닌 외부 추가 반환/정정 기록 예외임을 명확히 했다. R3 현행화, R4 설계 보완(코드/schema/보존 exact 미구현); R1 금융 식별·금액 및 R2 지연 시각/fallback은 미확정 유지. 타 서버/PLAN-007 변경 없음.
- 2026-10-06 환불 리뷰 재검증: PAYMENT_REFUND_DISCUSSION_REVIEW의 R1~R4를 ADR §5.5/§5.7/§5.8, AGENTS 62/308행, 구매 안내 초안 및 AttemptGroup 코드와 대조해 타당함을 확인. 현재 코드 버그가 아니라 미구현 결제의 설계 공백/문서 노후화다. 실제 확정 시각 정책은 승인됐으나 fallback·늦은 시각 정정은 미확정, 사용 snapshot 방향은 있으나 구매별 증거 exact 계약은 부족하다. 이번은 확인 요청으로 계약/AGENTS/안내 본문을 수정하지 않음.
- 2026-10-06 결제·환불 논의 리뷰: 상담/Store 집행/검증된 결과 반영 분리와 부분 환불 뒤 해당 구매 종료 방향은 승인 기록과 일치한다. 구현 전 보완은 부분 반환액·증분/누적·후속 환불 dedupe와 금융/권리 상태 분리, 확정 시각 부재 fallback 및 지연 timeline reflow, 구매별 최소 이용 증거다. AGENTS의 부분 환불 범위 밖 설명과 구매 안내 초안은 최신 C9-S10 동기화가 필요하며 이번 리뷰에서 계약 본문은 수정하지 않았다. 상세: [검토 문서](../contracts/PAYMENT_REFUND_DISCUSSION_REVIEW-2026-10-06.md). 신규 Jira·코드·타 서버·외부 설정·배포 변경 없음. 문서 정적 검증, Gradle 미실행.
- 2026-10-06 부분 환불 잔여 권리 종료 승인: 해당 구매만 종료, 다른 유료 구매/무료권 보존, 실제 반환액 기록과 기존 timeline reflow·시험 상태별 차단 방향 유지. Google 직접 Store 처리/Apple 최종 심사 운영도 승인 반영. 부분 환불 정책은 후속 PLAN 설계 대상으로 확장됐지만 코드·금액/시각 증거·멱등 계약은 미구현이며 기존 범위 제외 문구보다 최신 C9-S10을 우선한다.
- 2026-10-06 Store 환불 공식 문서 재확인: Google Console은 인앱 전액/부분 비율·세전 금액 환불 지원(결제수단 제한), Apple은 고객 신청/Apple 최종 심사이며 GRANT_PRORATED+사용률은 의견일 뿐 직접 환급 아님. RC Refund Control은 부분 선호/거래별 사용률 미지원. Apple 정보 응답12시간·동의와 팀2영업일 응대 구분, Google 탐지 지연/부분 환불 증거 검증 필요. 상세 STORE-REFUND-CAPABILITIES-2026-10-06.md. 실제 환불/설정/계약 변경 없음.
- 2026-10-06 환불 운영 추가 승인: 팀 건별 범위/금액 검토, 실제 provider 환불 확정 시각 기준, 2영업일 내 1차 답변, userId 귀속 필수, 약관 프론트 담당. userId는 기존 인증 계약에 맞춰 서버 자동 연결하는 해석으로 기록했으며 Identity 코드는 미수정. 법정 의무/해지 효력·공제 공식과 부분 환불 실행·원장 검증까지 완료된 것은 아니다. C9-S10·ADR-004·결제 요약에 반영.
- 2026-10-06 추가 승인: 환불은 앱 Identity 문의로 접수하고 `REFUND` 분류를 추가하는 방향 확정. 운영자 구매/이용 검토, Store 직접 신청/환급 및 검증된 Billing 반영 유지. ADR-004·C9-S10·결제 요약에 반영, Identity 코드 미수정. 잔여기간/사용분 공제·해지 효력·운영 응대/본인확인·최종 고지는 미확정이며 provider 검증과 결제 PLAN은 별도다.
- 2026-10-06 기록 구분 재확인: 현재 구현의 entitlement_ledger는 무료 Grant 지급/예약/소비 원장이며 attempt_sessions는 시험 상태·시각 projection이다. 유료 Purchase/payment ledger 5년 보존은 승인된 설계이지 현재 결제 저장 구현 완료가 아니다. 결제 사실과 해당 구매의 이용 증거를 구분하되 기존 Attempt projection을 재사용할 수 있으며 별도 중복 이력 저장소를 확정한 것은 아니다.
- 2026-10-06 환불 상담/SNS 영향 검토: 문의→수동 검토는 가능한 운영 제안이며 정책 승인/구현은 아니다. 실제 Store 환불과 검증된 Billing reversal을 분리하고 사용량 공제·잔여기간 반환 기준은 여전히 미확정이다. Identity TMI-192의 단일 SNS/계정 찾기는 전화 증명만으로 로그인·owner 이전을 하지 않으며 기존 userId 기반 Billing 연결을 유지할 수 있다. TMI-197 문의 API는 구현됐지만 기본 OFF, AUTH/GENERAL만 있고 결제 검증/환불 실행 기능은 없다. Billing audience/read는 Identity 발급 코드에 존재하나 ACTIVE MEMBER 전용 purchase 발급은 후속이다. LC 학습 기록 삭제 이후에도 환불 판단용 최소 이용 증거가 필요한 점과 Billing continuation terminal retention/E2E를 출시 전 확인한다. 상세: docs/contracts/REFUND-SNS-REVIEW-2026-10-06.md. 로컬 소스 검토이며 배포 확인 아님.
- 현재 브랜치: `develop`
- 2026-09-22 ADR-004 확정 상태 재검토: 전체 최종 승인 아님. C9-S1~S11과 D1-A/D2-A의 상품·기간·가격·계정 연결·무료 보존·환불 후 차단·지원 창구/열람/할인 정책은 승인됐으나 잔여기간 환불 적용/공제/효력·최종 고지/운영 절차는 미확정이다. §5의 exact DTO/public wrapper, provider 필드·실제 서명 지원, LC revoke wire/GRADING gate, schema v5/migration·운영 기술 상세는 검토·계획 승인 대상. G1~G8은 사용자 임의 선택이 아닌 기술/배포 검증이다. ADR의 상품 미생성·Store ID 미확인·RC 미매핑 문구는 9/16 Apple/Offering 완료 기록보다 오래됐으며 갱신 필요. 결제 PLAN은 로컬 목록에 없음. 계약 원문/승인 상태는 변경하지 않고 검토 기록만 추가했다.
- 2026-09-22 Google Ads 잔액 차이 확인: 결제 요약 실제 조회에서 잔액 15원과 예상 세금 및 요금의 VAT를 확인했다. 캠페인 비용 합계에서 초과 광고게재 조정을 차감한 순 광고비가 직전 프로모션 인정 지출과 일치하고, VAT를 더한 순비용과 잔액이 충전 총액에 정확히 수렴한다. 따라서 이번 차이는 누락 반영이나 별도 수수료가 아니라 부가세 항목으로 설명된다. 직전 개요의 소진 표시보다 이번 결제 요약의 15원 상태를 최신 근거로 사용. 외부 변경 없음.
- 2026-09-22 Google Ads 프로모션 조회: 사용자 제공 광고 계정에서 프로모션은 이미 등록됐으나 지출 조건은 미충족, 광고 가용 잔액 소진 상태임을 실제 UI로 확인했다. 추가 신청 대신 조건에 인정되는 광고 지출 충족→자격 검토→향후 광고비에 자동 적용 순서를 안내. 공식 도움말상 최대 35일 처리, 해당 프로모션은 적립 후 60일 사용 기한. 충전액과 인정 지출은 다르며 세금/집계 지연을 고려한다. Google Cloud 크레딧과 별개이고 충전·광고/예산 변경·쿠폰 적용·문의 제출은 하지 않았다.
- 2026-09-16 결제 개발 우선순위 안내: Google RTDN/상품 설정 완료를 기다리지 않고 결제 설계·fake provider 기반 개발을 병행할 수 있다. 로컬 ADR-004는 기술 상세 초안이고 plans 목록에 결제 PLAN은 아직 없어, 먼저 실제 RC 지원과 인증/필드 매핑·public wrapper 차이를 검증해 별도 결제 PLAN(다음 번호 PLAN-008 후보)을 작성·승인받는 순서를 권장했다. 첫 구현은 catalog·purchase account, 이후 검증 거래/기간권 원장·paid-first·환불/LC 차단으로 나눈다. 이번은 순서 안내만이며 구현/계획서 생성/Jira/외부 변경은 하지 않았다. Google 오류를 단순 대기만으로 해결된다고 보장하지 않으며 Store sandbox/RTDN·환불 E2E 전 production OFF를 유지한다.
- 2026-09-16 RC topic 생성 권한 오류 재진단: 실제 Google 감사 로그의 16:26:45 CreateTopic은 `pubsub.topics.create granted=true`, status code 6/Resource already exists다. 앞선 16:26:37 생성 기록 및 RC에 실제 topic 경로가 표시되는 기존 Play-Store-Notifications 옵션을 확인했다. 따라서 확인된 최근 실패는 권한 거절이 아닌 중복 생성이며 최초 연결의 다른 단계 실패 원인은 아직 미확인. RC 새로고침 후 생성 예정이 아닌 기존 topic을 선택해 연결하는 것이 다음 단계다. 이번 권한 확대·키 변경·topic 삭제/생성·Connect 재시도는 하지 않았다.
- 2026-09-16 다음 작업 안내: 직전 확인 기준 IAM 보완은 완료, 다음은 RC Google developer notifications에서 생성 예정 topic을 선택해 Connect 후 실제 topic 경로·publisher 권한을 확인하고 Play RTDN 설정 및 테스트 알림 수신을 검증하는 단계다. 일회성 상품 알림 포함을 확인한다. 이후 결제 권한 Android 빌드→Google 5종 상품 등록→기존 premium Offering에 Android 매핑, 별도 Billing 결제 PLAN/구현·sandbox 검증으로 진행한다. 이번에는 순서 설명만, 외부 실행/재조회 없음.
- 2026-09-16 불필요 Lite 역할 제거 완료: 사용자 추가 승인으로 RevenueCat 서비스 계정의 Pub/Sub 라이트 편집자 부여만 제거·저장했다. 최종 프로젝트 IAM 역할은 게시/구독 편집자와 모니터링 뷰어 두 개임을 확인. 개인 계정 권한·서비스 계정/키·리소스는 변경/삭제하지 않았으며 필요 시 역할 재부여 가능. RTDN 연결/전달 테스트는 여전히 후속이다.
- 2026-09-16 Google Pub/Sub 권한 보완 완료: 사용자 승인으로 RevenueCat용 서비스 계정의 프로젝트 IAM에 `roles/pubsub.editor`(현재 한국어 UI: 게시/구독 편집자)를 추가했다. 기존 모니터링 뷰어·Lite 역할과 개인 계정 권한은 보존. 정책 업데이트 알림 및 저장된 역할 확인 후 RC 재조회에서 기존 No options 대신 `Play-Store-Notifications — Will be generated by RevenueCat` 선택지가 표시됐다. 이는 생성 예정 옵션이지 topic 생성/RTDN 연결 완료가 아니다. Connect·키 재발급·Play 설정 변경은 하지 않았고 후속 알림 연결/테스트가 남았다.
- 2026-09-16 Google Pub/Sub 권한 진단: RevenueCat credential의 프로젝트와 같은 Google Cloud 프로젝트에서 API 3종(androidpublisher/playdeveloperreporting/pubsub) 활성 및 서비스 계정의 `모니터링 뷰어`·`Pub/Sub 라이트 편집자` 역할을 확인했다. 공식 가이드가 요구하는 일반 `Pub/Sub 편집자`와 Lite는 다르며 일반 역할 보완이 필요하다. RC는 Play 구매/카탈로그 검증 Valid이나 알림 topic 선택지는 No options·미연결이다. 현재 로드 화면에서 과거 오류 문구 자체는 재현하지 못했고 RC Client Email 필드는 비어 있어 credential principal 직접 대조는 제한됐다. IAM 수정·키 재발급·topic 생성/연결은 하지 않았다. 권한 보완 후 RC topic 재조회 및 RTDN 연결/테스트가 다음 단계다.
- 2026-09-16 Apple Missing Metadata 후속: 실제 1일권 상세에서 한국어·가격·판매지역 설정 존재 및 심사 정보의 스크린샷 미첨부를 확인했다. 실제 앱 구매 화면 완성 후 각 상품에 심사용 캡처를 첨부하고 접근 방법/이용기간 설명을 보완하도록 안내한다. 선택 사항인 1024px 프로모션 이미지와 구분하며 심사 제출/업로드는 미실행이다.
- 2026-09-16 사용자 RevenueCat 설정 검증: `premium` Offering(토선생 기간형 프리미엄 이용권)에 custom package `premium1d/3d/7d/14d/28d`가 동명의 Apple 상품에 각각 연결됨을 실제 UI에서 확인했다. 5개 모두 Entitlement 미연결, Google 상품 없음, 기존 default 별도 유지. Apple `Missing Metadata`와 이메일 미인증 배너는 남아 있다. [등록 결과 §6.1](../contracts/STORE_PRODUCT_REGISTRATION-2026-09-16.md)에 실제 RC ID 매핑 기록. 이번에는 읽기만 수행했고 SDK/Billing/sandbox 동작 완료는 아니다.
- 2026-09-16 Apple 즉시 심사 제출 여부 안내: 상품 초안 등록만으로 제출 준비가 완료된 것은 아니다. RevenueCat 상품/Offering, 앱 구매 UI/SDK·Billing 기간권 지급, sandbox E2E, 심사 스크린샷/접근 안내·새 앱 버전, 계약/정산 상태 확인 후 제출한다. 질문을 제출 실행 승인으로 해석하지 않았으며 외부 변경 없음.
- 2026-09-16 Apple 상태 재안내: 마지막 실제 UI 확인상 상품 5종 생성·가격/한국어/한국 지역 설정만 완료했고 모두 초안/제출 준비 중이다. 심사 추가·제출·판매 개시는 수행하지 않았다. 이번 응답은 기존 확인 기록 기준이며 콘솔 재조회/변경 없음.
- 2026-09-16 Android 결제 권한 안내: `com.android.vending.BILLING`을 최종 merged manifest에 포함한 새 AAB/APK를 Play가 인식해야 상품 등록 선행 조건을 충족할 수 있음을 설명했다. RevenueCat SDK 의존성에서 병합될 수 있어 실제 앱 manifest 검증이 필요하다. 런타임 권한/자동 청구/결제 서버 구현 완료를 뜻하지 않으며 앱 코드·업로드·출시는 미실행이다.
- 2026-09-16 로그인 후 등록 결과: Apple `premium1d/premium3d/premium7d/premium14d/premium28d` 소모품 5종 생성, 한국어 표시명/설명·승인 KRW 가격 저장 및 사용자 추가 승인으로 대한민국만 판매 대상 지정. 최종 초안 5개/제출 준비 중 확인. Google은 결제 권한 포함 APK 필요 안내로 생성 불가(0건). [등록 결과](../contracts/STORE_PRODUCT_REGISTRATION-2026-09-16.md) 참조. 심사 제출·계약 동의·RevenueCat/코드/배포 변경 없음. Android 결제 권한 빌드 업로드, Apple 심사 자료/새 버전·계약 확인, RevenueCat 매핑/sandbox E2E가 남았다.
- 2026-09-16 Apple/Google 상품 등록 요청: 승인된 1/3/7/14/28일·9,000/19,000/29,000/49,000/69,000원 일회성 consumable 구성 확인 후 두 콘솔 접근을 시도했다. Apple은 로그인 필요, Google은 현재 계정에서 약관 검토 화면으로 이동하여 앱/상품 목록 접근 불가. 약관 동의·상품 생성·가격 저장·심사 제출·판매 활성화는 하지 않았다. 올바른 개발자 계정 로그인과 사용자 약관 검토 후 재개하며, 실제 상품 ID/중복/가격 선택지는 아직 미확인이다.
- 2026-09-10 활동비 신청 용도 보완: 사용자 확인에 따라 Cloud Pub/Sub·RevenueCat 연동 외 Firebase 이용도 세부사항과 구매사유에 포함했다. 구체적인 Firebase 제품·유료 요금제·청구 필요성은 확정하지 않았다.
- 2026-09-10 프로젝트 활동비 신청 초안 안내: Google Cloud Pub/Sub 기반 Google Play 결제 상태 알림/RevenueCat 연동 목적의 기타 사용료 문구를 작성했다. 실제 결제 금액·통화·선불/인증금 여부는 미확인으로 빈칸 유지, 무료 크레딧을 신청 금액으로 사용하지 않는다. 제출·결제는 수행하지 않았다.
- 2026-09-10 Chrome 주소 이동 안내: 브라우저별 Google 로그인 순번(authuser)이 달라 동일 링크가 다른 계정으로 열릴 수 있다. 계정 지정 없는 Cloud Billing/무료 체험 주소와 올바른 계정 선택을 안내했다. Chrome 오류 화면은 미확인으로 원인을 확정하지 않았으며 외부 설정 변경 없음.
- 2026-09-10 Google Cloud 무료 체험 화면을 인앱 브라우저에 열고 계정 선택 단계에서 사용자에게 인계했다. 실제 화면은 $300 크레딧·90일 사용·일반 계정 활성화/선불 선택 시 청구 안내다. 약 7만원 결제 및 3개월 뒤 현금 환불 조건은 확인되지 않았다. 약관 동의·결제·계정 설정 변경 없음. 다음은 사용자가 올바른 계정을 선택하고 실제 결제 단계의 조건을 확인하는 것이다.
- Jira: 무료/owner lifecycle 관련 Billing `TMI-120`, Identity `TMI-123`, Learning Core `TMI-125` 구현은 각 `develop`에 병합됐다. 무료 reader PLAN-007은 승인 후 구현·로컬 검증을 완료했으며 Jira는 미생성이다. 결제 ADR-004는 초안이며 별도 결제 PLAN·Jira·코드는 아직 생성하지 않았다.

- ADR-004 최신 승인(2026-09-07): 사용자 요청으로 30일 상품을 4주(28일·672시간·2,419,200초), 내부 `PREMIUM_28D`로 변경했다. 판매 구성은 1일·3일·1주·2주·4주이며 자동 갱신 없는 일회성 unlimited와 무료권 보존은 유지한다.
- D1-A 승인: 정상 만료 전 승인된 현재 Session은 기존 시험·제출 기한까지 완료 허용, 만료 전 reserve의 동일 Session confirm은 기존 5분 TTL 내 허용. 만료된 권리의 새 INITIAL/replacement는 금지하고 환불에는 유예를 적용하지 않는다.
- D2-A 승인: REFUND_REVERSED/환불 뒤 owned 재관측은 durable REVIEW_REQUIRED·경보와 담당자 확인·별도 승인 복구로 처리한다. 자동 재지급·기간 복원·Session 재개 및 원장 덮어쓰기는 금지한다.
- 이번 범위는 계약 문서와 기록 갱신뿐이다. 아래 과거 기록의 30일/720시간/미확정 D1·D2는 이 승인으로 대체됐다. 결제 코드·schema v5·Store/RevenueCat 설정·다른 서버·Jira·배포·commit/push는 변경하지 않았다. 기존 미커밋 작업 기록 보존, 예상 밖 변경 없음.
- 검증 및 다음 작업: 문서 diff·duration 산술·잔여 참조를 확인하고 git diff --check 통과. 문서만 변경해 Gradle은 재실행하지 않았다. 실제 상품 ID/가격·provider 지원·sandbox/ingress gate와 public wrapper 대조는 남아 있다. 승인 정책 기준의 별도 결제 PLAN을 작성·승인받은 후 Jira·구현을 진행한다.

## 2026-09-09 Store 중심 환불 창구 승인

- 2026-09-10 카드 등록 재질문: Google Cloud의 결제수단 등록·결제계정 활성·서비스 계정 소속 프로젝트에 결제계정 연결은 서로 다르다. 사용자가 기억한 작업이 프로젝트 결제 연결일 가능성은 있으나 이번 실제 상태/과거 지시를 확인한 것으로 단정하지 않는다. 9/9 RC 화면에도 MTR 기준 초과 시 기능 중단 방지를 위한 별도 결제수단 안내가 있었다. 카드 등록만으로 Pub/Sub API/IAM/RTDN 완료는 아니며 현재 프로젝트 Billing 상태 확인을 안내한다.

- 2026-09-10 Google 설정 재안내: 사용자 Cloud Billing 링크는 결제 계정 화면이며 지난 오류의 직접 점검 대상은 RevenueCat service account 소속 프로젝트의 Pub/Sub API 활성·IAM role과 RTDN 연결이다. 9/9 Valid credential/Pub/Sub 오류 관찰을 재사용했고 실시간 설정 재조회는 하지 않았다. Cloud 결제계정 생성/카드 추가·role 확대·키 재발급은 수행하지 않음. 프로젝트 API와 IAM 확인 후 RevenueCat topic 연결 및 Play RTDN 설정/테스트로 진행한다.

- C9-S11 승인 반영: 지원 이메일 tosunsaeng093@gmail.com, 정상 만료 후 본인 계정의 기존 결과/피드백 열람, 최초 별도 할인 없음. 결정서·요약·ADR·안내문을 동기화했다. 메일 전송/담당자 확인·앱/LC 코드 변경은 하지 않았다. 아래 과거 미정 설명은 이 승인으로 대체한다.
- [2026-09-09 실제 준비 상태 점검](../contracts/PAYMENT_READINESS_REVIEW-2026-09-09.md): RC 이메일 미인증, Google Valid credential과 별도 Pub/Sub 권한 오류·RTDN 미연결 확인. Apple 두 credential Valid·알림 correctly configured이나 No notifications received. S2S tracking OFF, default Refund Control Do not respond 확인. 양 Store Products(All) 빈 목록, default Offering은 Test Store 월간/연간/평생, Billing webhook 없음. 변경/문의/실제 구매는 실행하지 않았다.
- Store 직접 조회는 Apple 로그인 auth failure, Google 현재 브라우저 계정의 개발자 가입 화면으로 미완료. RC 빈 상품 목록을 Store 자체 미등록 증거로 간주하지 않는다. 법 제17~18조와 시행령 제21조의2/제24조 본문 재확인, 정확 공제식/분류·부분 환불 실행 검증은 미완료다.
- 로컬 코드 추가 확인: Billing payment adapter/endpoint·Purchase/SubscriptionEntitlement 미구현/schema v4. Identity는 현재 Billing audience·billing:read를 추가하며 과거 단일 audience 관찰과 달라졌지만 billing:purchase는 확인한 소스에 없다. LC 기존 history owner read는 기간형 paid 권리와 분리되어 있으나 paid revoke route 미발견. 실제 AWS/배포·결제 E2E는 검증하지 않았다.
- 검증: 문서/소스 정적 점검·실제 RC 읽기·공식 법령, git diff --check·로컬 링크 검사. 문서만 변경하여 Gradle 미실행. 기존 미커밋 변경 보존, 예상 밖 변경 없음. 다음은 RC 계정 인증/Google 알림 문제·Store 실제 설정 확인, 법적 검토와 별도 결제 PLAN 승인이다.

- 남은 결정 안내: 제품 핵심·5개 가격·Store 중심 환불은 재선택하지 않는다. 사용자 운영 선택은 실제 지원 채널/담당자, 정상 만료 뒤 기존 결과 열람 확정, 출시 할인 여부다. 결과 열람 유지·별도 할인 없음은 권장 제안이며 아직 승인 아님. 법적 분류/잔여 반환·고지와 Store/RC 실제 지원은 선택이 아닌 검토·검증 대상으로 분리한다. 문구·결제 PLAN 초안 작성은 가능하되 최종 게시/판매에는 미완료 gate 해소가 필요하다.

- C9-S10 확정: Store 기본 환불 신청·심사·환급 + 토선생 고객지원. 최초 범위에 자체 환불 신청 API/심사 UI/직접 송금을 추가하지 않는다. 검증된 최종 결과의 원장·권리·LC 차단은 유지한다.
- [구매·환불 안내 초안](../contracts/PREMIUM_PURCHASE_REFUND_NOTICE_DRAFT.md) 작성. 사용자 제시 국내 가격 9,000/19,000/29,000/49,000/69,000원(1/3/7/14/28일), 구매 기준 시작·기간 연결·자동 갱신 없음·무료 보존·Store 안내/고객지원 문구를 정리했다.
- 결정서·결제 요약·ADR-004 동기화. 실제 지원 연락처/담당자, Store 가격·세금/할인, 만료 후 결과 열람 범위, 고지/동의·법적 분류/반환식/효력 시점·부분 환불 실제 경로는 미확정 gate다. 초안은 게시하지 않았다.
- 기존 미커밋 변경 보존, 예상 밖 변경 없음. 코드·wire·schema·다른 서버·Jira·외부 설정·Git commit/push·배포 변경 없음. 문서 전용으로 Gradle 미실행, diff/링크 검사 수행. 다음은 실제 지원 채널 입력과 고지·법적/Store 경로 검토 후 결제 PLAN 승인이다.

## 2026-09-07 현재 우선 작업 — 무료 사용권 public reader PLAN-007

- 2026-09-08 사용자 판매가격/환불 창구 안내: 기존 순서로 1일 9,000원·3일 19,000원·7일 29,000원·14일 49,000원·28일 69,000원을 조사 사실에 기록했다. 실제 Store 등록·세금/할인 미검증. Store 기본 환불 창구+앱 안내/고객지원 경로를 권장하며 자체 신청 API는 필수로 단정하지 않는다. 단 Store 거절로 법적 요청을 종결하거나 자동 승인/부분 반환 자동 수렴을 보장하지 않는다. 승인 정책·코드·Store 설정 변경 없음.

- 2026-09-08 환불 분류용 사용자 사실 확인: AI 시험·채점만, 강사/정규 과정·진도·수료 없음, 파일 다운로드 보유 불가. 기존 결과 만료 후 열람은 잠정안이다. 개인사업자·설명상 IT 업종, 교육시설 등록 없음, 최초 한국 성인 대상. 상세는 잔여기간 조사 §2.0에 기록했다. 기간형 온라인 AI 서비스라는 사실 설명을 출발점으로 하되 법적 적용 제외/환불식 확정은 아니다. 가격·할인·구매 고지·해지 효력 자료가 남았고 코드·계약 변경 없음.

- 2026-09-08 법률팀 없는 환불 검토 준비 안내: 이미 승인된 기간/자동갱신 없음/stacking과 별도로 실제 교육 제공 방식, 이용 종료 후 콘텐츠 접근, 사업 형태·교육시설 신고 여부, 판매 국가·미성년자, 가격/할인, 구매 화면·약관/동의, 환불 접수·효력 시점을 수집한다. 개인정보/사업자번호 없이 사실 요약과 초안으로 준비하며 미정은 미정으로 남긴다. 내부 사실 정리 후 법적 분류·강행 기준은 외부 단건 자문/관련 기관 상담으로 검토하고 Store/RC 실행 검증은 따로 수행한다. 정보 수집 안내만, 정책 확정 없음.

- 2026-09-08 법적 분류 의미 설명: Store consumable/사업자 업종 선택과 달리 실제 판매 대상(기간형 서비스·콘텐츠), 미제공 기간의 가분성, 계속거래 요건, 교육 관련 별도 규정 적용을 확인하는 과정이다. 서로 배타적인 상품 메뉴가 아니며 사업자가 임의 선택해 법적 의무를 정하지 않는다. 현재 제품은 '28일간 모의고사·AI 피드백 기능을 무제한 이용하는 기간형 온라인 서비스'라는 사실 설명을 출발점으로 하되 법적 분류 확정은 아니다. 기존 조사 근거 재사용, 정책·코드·설정 변경 없음.

- 2026-09-08 잔여기간 반환 추가 조사: [4주권 부분 환불 조사](../contracts/REFUND_REMAINDER_RESEARCH-2026-09-08.md). Google Console 인앱 부분 환불과 Apple API 1.19+ consumable GRANT_PRORATED/사용률 권고를 확인했다. Apple 최종 심사와 RC 부분 선호·거래별 사용률 미지원은 구분하며, Google 철회 없는 환불의 RC 탐지 제한 때문에 부분 금액 자동 수렴은 미검증이다. 한국법의 가분적 미제공 부분/철회 위약금 제한과 지침 제25조의 조건부 권고를 구분했다. 토선생 분류·정확 공제식은 미확정이며 일할 반환·위약금 0은 신규 제안이다. ADR-004 부분 금액/누적 환불/접수·효력 시점과 실행 증거 경로를 별도 승인 후 보완할 필요가 있다. 코드·승인 계약·설정 변경 없음; 문서 diff 검사, Gradle 미실행.

- 2026-09-08 환불 확정 항목 안내: 이미 승인된 환불 후 차단·GRADING/COMPLETED·D2-A·자동 심사 OFF는 재선택하지 않는다. 남은 운영 선택은 고객지원 채널/담당/첫 응답 목표, 단순변심·미사용/부분사용·장애/미제공·중복결제의 심사 구분, 최소 이용 증빙 범위/접근/보존, 반복 환불 수동 검토 원칙이다. 권장 첫 응답 2영업일은 신규 제안이지 승인/법정 환급기한 변경이 아니다. 4주권 법적 분류·잔여기간 반환/공제 기준 및 실제 Store 환불 실행·원장 수렴 경로는 별도 출시 검증이다. 법정 권리를 제품 선택으로 배제하거나 현재 부분 자동 환불 미구현을 거절 사유로 사용하지 않는다. 설명/기록만 반영, 정책 확정·구현·외부 설정 변경 없음.

- 공식 환불 정책 조사: [조사 문서](../contracts/REFUND_POLICY_RESEARCH-2026-09-07.md). Apple 개별 심사, Google 48시간 이내 가능/이후 개발자 문의 및 별도 법적 권리, 한국 전자상거래법 제17조의 가분적 미제공 부분·고지/시험 사용 요건을 확인했다. Store 결과 존중만으로 개발자 법적 요청 처리를 종료할 수 없다. RC 현재 Refund Control은 Apple·Google 선호 전달을 지원하지만 거래별 실제 사용률/이벤트를 자동 전송하지 않고 Apple 동의를 요구한다. RC의 거래 존재=DELIVERED와 Billing PENDING 차이를 검토해야 한다. 기존 OFF·wire 유지, 법적 상품 분류/부분 반환 경로·실제 project 지원/설정은 미확인. 조사/기록만 변경, 코드·설정·약관 게시·Jira 없음.

- 사용 후 환불 정책 확인(2026-09-07): 확정된 것은 provider-confirmed 환불 뒤 해당 권리의 추가 사용 차단·원장 reversal·완료 기록 보존이며, '하루 사용 시 환불 불가/사용일 공제/반복 환불 자동 제재' 기준은 승인된 정책이 아니다. Store가 사용 후 환불을 승인하면 이미 제공한 시험·채점 비용은 회수되지 않는 잔여 위험이 있다. 자동 Refund request handling은 출시 OFF, 일반 REFUND_REVIEW 신호 지원은 미확인으로 자동 adapter도 비활성이다. 현황 설명만 수행했고 정책·코드 변경 없음. 실제 Store/관할 환불 규정과 소비 증빙 전달 가능성은 별도 검증·승인 대상으로 남긴다.

- Identity 작업에서 교차 검토: 로컬 develop에 PR #9 merge eb0ae14 반영 확인. ./gradlew clean test 236개(35 suite, 실패·오류·skip 0)와 git diff --check 통과. 신규 수량 0/retake ALLOWED는 계약대로이며 화면의 1회 남음은 아래 프론트 통합 표시를 적용해야 한다. epoch 기반 command TTL 독립성·read-only Mongo 회귀 확인. 실제 배포·Identity Billing audience/read·legacy coverage·앱 반영은 별도 검증 대상이다.

- Git 인계: 현재 develop의 미커밋 변경을 새 codex/feature 브랜치로 옮겨 사용자 직접 commit/push 후 develop 대상 PR을 만드는 명령을 안내했다. 기존 미커밋 결제 설계 문서도 있으므로 docs 전체 staging 시 함께 포함됨을 구분한다. Codex는 Git 변경·PR 생성을 실행하지 않았다.

- 사용자 화면 선호: 신규 무료 응시와 중단 후 허용된 재응시를 모두 '무료 모의고사 1회 남음'으로 표시하는 방향이다. 현재 FREE_EXAM_ONCE의 newAttempt/retake ALLOWED를 화면에서 통합하되 availableQuantity(새 INITIAL 수량)·ledger 의미는 유지하는 방안을 안내했다. GRADING/예약/자격 불명은 별도 상태로 표시하고 wire/앱 코드는 이번 대화에서 수정하지 않았다.

- 조회 설명 확인: 중단 후 RETAKE_AVAILABLE은 신규 수량 0/newAttempt BLOCKED/usage INCOMPLETE/hasInProgress false/retake ALLOWED이며 current-owned group ID를 제공한다. 단순 앱 종료로 OPEN/ACTIVE가 남으면 hasInProgress true여도 replacement는 가능하다. 실시간 접속 의미가 아니며 reserve가 최종 판정한다. 이번 설명 요청에서는 코드 변경·테스트 재실행 없음.

- [PLAN-007](../plans/PLAN-007-public-free-entitlement-query.md) 사용자 승인 후 구현했다. 결제·RevenueCat·상품·paid schema는 제외했고 Jira는 생성하지 않았다. [OpenAPI](../openapi/free-entitlements.yaml), [배포·이관 안내](../runbooks/PLAN-007-public-reader-rollout.md)를 추가했다.
- link/session/reservation의 sessionOwnerEpoch, link sessionBindingVersion CAS를 기존 Transaction에 구현했다. 실제 PHONE_REJOIN만 epoch 증가, Guest merge는 보존하며 replay는 추가 CAS 없이 기존 결과를 반환한다. command TTL은 변경하지 않았다.
- 조회는 explicit primary/SNAPSHOT·driver CSOT 2초 budget·최대 두 번 시도, bounded batch, 순수 free reader registry와 reserve 공통 predicate를 사용한다. GET 업무 write 없음과 command 삭제 뒤 정상 귀속을 실제 Mongo에서 검증했다.
- legacy migration은 명시적인 <=100 Session batch의 dry-run/CAS 방식이다. 미이전 subject·exact 최신 continuation만 자동 증명하며 그 밖은 BLOCKED로 남긴다. 실제 운영 migration/coverage는 수행하지 않았다. 기존 legacy RESERVED는 writer 전환 전 drain/expire 또는 승인 이관이 필요하다.
- API: GET `/api/v1/entitlements`, public isSuccess/code/message/result envelope, JWT sub·Billing audience·billing:read. Guest/MEMBER 모두 본인 read 허용, phone 자격은 별도 검증이다.
- 판정: lazy Grant 부재라도 VERIFIED/retained Claim 부재면 예상 신규 수량 1. 완료 번호는 0. 신규 수량/진행/재응시/사용 완료를 분리하고 조회에서 발급·차감·예약·owner 변경을 하지 않는다.
- 연동 불명은 200 PENDING/null, 저장소·불변식 오류는 503. source 계정의 group/Session/답안/결과를 새 계정에 공개하지 않고 승인 owner 이전 뒤에만 최소 group 재응시 상태를 표시한다.
- Identity 로컬 JwtAccessTokenIssuer는 account_type을 발급하지만 단일 설정 audience를 유지한다. PR #39 병합은 Billing audience/read 배포 완료가 아니다. 별도 발급 후속·새 token·staging E2E가 필요하다.
- 보안: public connector/ALB exact allowlist/JWT를 기존 internal Lattice/SigV4와 격리한다. reader flag 기본 OFF이며 Billing OFF 배포→Identity→staging→reader ON→앱 순서다.
- 검증: `./gradlew clean test` 성공, 236 tests / 실패 0 / skip 0. Docker replica-set snapshot·command listener 무쓰기·confirm 경합·owner CAS·command 삭제·이관, JWT 로컬 JWKS·scope, 실제 public/internal 포트·production Controller INTERNAL span·baggage 미전파 테스트 포함. OpenAPI YAML 파싱과 `git diff --check` 통과.
- 기존 테스트 fixture 두 종류도 보정했다: schema v4 initializer에 schema=3을 넘기던 테스트 3곳, mockExamId를 mock-1로 고정하던 confirmed-group helper. 운영 코드의 기존 wire·index schema는 변경하지 않았다.
- 다음 작업: Identity aud/read 발급 인계 확인, public ALB/SG/Lattice·JWKS·legacy coverage 및 staging E2E. reader/connector 기본 OFF, 배포·Git commit/push·타 서버 수정 없음. payment ADR-004 D1·D2/별도 PLAN은 후속 트랙이다.

## 2026-09-07 ADR-004 기술 초안 작성 기록

- 사용자 요청으로 [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md)를 작성했다. 기존 제품/9개 선택은 유지하고 public DTO·오류·한도, RC webhook/API v2, 계정 binding, payment Transaction, Mongo v5 index, paid/free guard, LC durable revoke와 배포·테스트 gate를 구체화했다.
- 작성 중 발견한 D1 정상 만료 중 현재 Session의 완료 여부, D2 Apple REFUND_REVERSED 후 복구 방식은 미확정 권장안으로 분리했다. 문서 작성 전의 '추가 제품 결정 없음' 예상과 달리 이 두 예외에는 사용자 확인이 필요하다. C9-S9에 기록했다.
- 공식 Purchase API/OpenAPI에서 owned/refunded, original_customer_id, string store_purchase_identifier와 검색/get route를 확인했고 Customer Resources에서 환경별 사용자 구매 목록·pagination을 확인했다. 앱 SDK 식별자 일치와 consumed 환불은 sandbox 검증 전이다.
- 신규 LC route는 `POST /internal/v1/entitlements/access/events`, event는 `AttemptGroupAccessRevoked` v1 초안이다. 실제 상대 reader·paid GRADING 실행 gate·Lattice 리소스를 배포한 것으로 간주하지 않는다.
- Google obfuscated identifier가 raw UUID와 같은 wire 값이라는 단정을 제거하고 SDK별 변환 검증 gate로 명시했다. 원 사용자 고정·alias 자동 이전 금지는 유지한다.
- 검증: 문서만 변경하므로 Gradle 미실행. `git diff --check`, 새 ADR의 로컬 링크 10개와 JSON 예제 4개 문법 검사를 통과했다. 기존 미커밋 문서 변경을 보존했으며 코드·Jira·외부 리소스·Git 이력은 변경하지 않았다.
- 결제 후속 작업: D1·D2 확인과 ADR 상세 검토 뒤 별도 결제 PLAN 작성. PLAN-007은 무료 reader에 배정됐다. 실제 product ID/가격/국가·credential/hostname은 결제 출시 입력으로 준비한다.

## 2026-09-07 RevenueCat 결제 연동 채택

- 사용자는 Apple/Google fixed-term 결제에 RevenueCat을 사용하는 안을 승인했다. RevenueCat은 표준 모바일 SDK·Offering/Package, Store transaction 정규화, HMAC webhook과 API 조회를 제공하는 결제 연동 계층으로 사용한다.
- 연동 편의성을 실제로 얻기 위해 RevenueCat 표준 SDK가 Apple transaction finish와 Google consumable completion을 맡는다. 기존 `Billing local commit → Apple finish/Google backend consume` 순서는 supersede됐다.
- Store 결제 완료 후 Billing 반영이 늦어질 수 있으므로 앱 callback으로 entitlement를 열지 않는다. RevenueCat HMAC webhook 또는 인증된 API 조회로 Store transaction을 확인하고 event ID·Store transaction unique와 Mongo Transaction으로 반영하며, 그 전에는 `PENDING`으로 둔다.
- RevenueCat custom App User ID에는 사용자·환경별 stable lowercase UUID v4 `purchaseAccountRefId`를 사용한다. 익명 구매와 RevenueCat alias/restore만을 근거로 한 토선생 계정 간 유료권 자동 이전은 허용하지 않는다.
- RevenueCat Entitlement는 consumable을 만료 없이 표현하므로 1·3·7·14·30일 권리 source로 사용하지 않는다. Billing catalog·Purchase·SubscriptionEntitlement·ledger가 기간, stacking, 무료권 보존, Reservation과 환불 차단의 authoritative source다.
- Apple/Google Platform Server Notifications는 RevenueCat에 연결하고 Billing은 RevenueCat webhook을 public provider ingress로 받는다. webhook retry 종료와 API 장애를 전제로 scheduled reconciliation과 운영 경보를 유지한다.
- ADR-004에서 exact RevenueCat project/app·Offering/Package, restore/transfer behavior, webhook route/HMAC·timestamp, client sync DTO, API 조회, Mongo index와 장애 UX를 확정한 뒤 PLAN/Jira/구현으로 진행한다.

## 2026-09-07 ADR-004 권장 선택 승인

- 제품 정책은 재결정하지 않는다. 양 Store consumable one-time, `PREMIUM_1D/3D/7D/14D/30D`, 24·72·168·336·720시간, ACTIVE MEMBER, Billing 발급 `purchaseAccountRefId`, paid-first/free-preserve, stacking/reflow, strict refund 상태표와 5년/120일/35일 보존은 이미 승인됐다.
- 사용자는 (1) public API 분리, (2) client sync hint/idempotency, (3) webhook 인증·fast durable ack, (4) RevenueCat sandbox/production routing, (5) anonymous/restore/transfer, (6) S2S-only 신규 구매 추적, (7) 자동 refund data handling, (8) webhook direct apply 대 API 재조회, (9) Learning Core access-revocation delivery의 권장안을 모두 승인했다.
- 확정안은 resource별 public route, lowercase UUID v4 idempotency와 untrusted Store transaction hint, Authorization+HMAC 5분 replay window, durable inbox 후 200/async worker, 같은 RevenueCat project의 environment-filtered webhook 두 개와 secret 분리, original App User ID 유지·익명 구매 금지, S2S-only tracking/refund auto handling 초기 OFF, 인증된 webhook direct normalize+API reconciliation, per-AttemptGroup durable Lattice revoke event다.
- exact Mongo schema/index/state machine, UTC/exclusive time, raw-body limit, retry/backoff, low-cardinality metric와 secret rotation은 승인 정책을 구현하는 기술 계약으로 ADR에서 작성한다. provider event unknown field는 RevenueCat의 forward-compatible 계약에 맞춰 허용하되 required field/type/size/event handling은 fail-closed 또는 durable ignored disposition으로 명시한다.
- 실제 Store product ID·가격·판매 국가, public hostname/certificate/listener, Store review, RevenueCat secret/API quota와 sandbox test account는 출시 입력이며 config placeholder/fake adapter 기반 백엔드 구현을 막지 않는다.
- RevenueCat 공식 문서는 optional Authorization header와 HMAC signing을 모두 지원하며 HMAC header는 `timestamp + raw body`의 SHA-256 서명이고 retry마다 새 timestamp/signature를 만든다고 확인했다. 5분 window는 delivery retry 간격이 아니라 각 HTTP 전송 시각 검증에만 사용한다.
- 이 승인 선택에 기반한 ADR-004 초안을 작성했다. 후속 상태와 추가 예외 정책은 위 `ADR-004 기술 초안 작성` 절을 따른다. ADR·PLAN 승인 전에는 payment application code를 구현하지 않으며 Jira 변경은 별도 사용자 승인을 받는다.

## 2026-09-07 Google Play 일회성 제품 등록 준비

- Google 최신 일회성 제품 객체 모델은 `일회성 제품 → 구매 옵션 → 선택적 혜택` 구조다. 경로는 `Play를 통한 수익 창출 > 제품 > 일회성 제품 > 일회성 제품 만들기`다.
- 토선생은 Billing이 24·72·168·336·720시간을 관리하므로 Google `대여`가 아니라 `구입` 구매 옵션을 사용한다. 각 제품에는 기본 구매 옵션 하나를 두고 수량 구매·선주문·할인 혜택은 최초 출시 범위에서 사용하지 않는다.
- 권장 영구 product ID는 `premium_1d`, `premium_3d`, `premium_7d`, `premium_14d`, `premium_30d`이며 각 제품의 purchase option ID는 공통 `standard`를 권장한다. 실제 생성 전 사용자의 이름·가격 승인이 필요하다.
- 이름은 `토선생 프리미엄 1일/3일/7일/14일/30일 이용권`, 설명은 정확한 시간 동안 프리미엄 모의고사를 이용하는 자동 갱신 없는 상품임을 명시한다. Google의 분류는 `서비스`, 구매 유형은 `구입`, 단일 거래 수량 옵션은 끈다.
- 제품별 한국 가격과 판매 국가를 입력하고 구매 옵션까지 `활성`으로 만들어야 Play Billing/RevenueCat import에 노출된다. 앱 판매 국가가 제품 판매 국가를 최종 제한한다.
- Play Console 생성 후 RevenueCat에 Google app package/service credential을 연결하고 5개 product를 import한다. RevenueCat Offering/Package에 연결하되 consumable을 RevenueCat Entitlement에는 붙이지 않는다.
- 공식 근거: `https://support.google.com/googleplay/android-developer/answer/16430488?hl=ko`, `https://support.google.com/googleplay/android-developer/answer/14590082?hl=ko`, `https://www.revenuecat.com/docs/projects/connect-a-store`.

## 2026-09-07 Google Play 결제 프로필 생성 안내

- 공식 경로는 Play Console `설정 > 결제 프로필 > 결제 프로필 만들기`다. 기존 Google 결제 프로필이 선택 목록에 있으면 새로 만들기 전에 토선생 개발자 계정과 법적 주체가 동일한지 확인한다.
- 국가/지역은 대한민국으로 선택하며 생성 후 변경할 수 없다. 결제 프로필 국가와 판매대금을 받을 거래 은행 계좌의 등록 국가가 같아야 한다.
- 개인/사업자·조직 유형, 법적 이름, 사업자명과 주소는 Play 개발자 계정과 사업자등록증·공식 주소 문서·세금·은행 예금주 정보에 맞춘다. 사서함 주소는 허용되지 않는다.
- 기본 연락처에는 실제로 Google 확인 요청에 응답할 대표자 또는 공식 담당자를 입력한다. 공개 판매자 정보에는 실제 운영 웹사이트, 교육/디지털 서비스에 가까운 카테고리, 모니터링하는 고객지원 이메일과 구매자가 알아볼 수 있는 명세서 표시명을 입력한다.
- 제출 후 세금 정보, 판매대금 은행 계좌 등록·인증과 필요 시 판매자 신원 확인을 이어서 완료한다. 실제 법적 이름·주소·전화·계좌·세금 식별자는 채팅·Jira·Git에 기록하지 않는다.
- 공식 근거: `https://support.google.com/googleplay/android-developer/answer/7161426?hl=ko`.

## 2026-09-07 Google Play 결제 프로필 생성 후 상품 메뉴 확인

- 첨부 화면 기준 결제 프로필은 Play Console에 연결됐고, 매출 0원·거래 없음은 아직 판매가 없다는 의미다. `지급받을 방법 > 결제 수단 추가`는 정산 은행 계좌 등록이며 상품 생성 메뉴 활성화와는 별개일 가능성이 높지만 실제 판매대금 수취 전에는 완료해야 한다.
- 일회성 제품 메뉴가 없거나 생성할 수 없다면 현재 Play에 업로드된 app bundle이 Google Play Billing 기능을 선언했는지 먼저 확인한다. RevenueCat을 사용할 경우 모바일 앱 기술 스택에 맞는 RevenueCat SDK를 추가한 새 AAB를 내부 테스트 트랙에 업로드하고 Play의 artifact 처리가 끝난 뒤 다시 확인한다.
- 네이티브 Android에서는 최종 merged manifest에 `com.android.vending.BILLING` permission이 있어야 한다. Flutter·React Native 등은 해당 RevenueCat 플러그인의 설치·빌드 절차를 따르므로 앱 기술 스택을 확인하기 전 dependency 구문을 고정하지 않는다.
- artifact 처리 뒤 `Play를 통한 수익 창출 > 제품 > 일회성 제품`에서 제품 5개와 각 `구입` 구매 옵션을 생성한다. 그래도 차단되면 해당 메뉴의 정확한 안내/오류 문구를 확인해 계정 검증, 앱 상태 또는 권한 문제를 구분한다.
- 15% 서비스 수수료 프로그램 등록은 선택 정책이며 상품 생성의 필수 선행조건으로 보지 않는다.
- 공식 근거: `https://support.google.com/googleplay/android-developer/answer/1153481?hl=ko`, `https://www.revenuecat.com/docs/getting-started/installation/android`.

### 2026-09-07 정확한 차단 문구 확인

- 사용자가 확인한 문구는 `Google Payments 판매자 계정을 설정해야 이 페이지에 액세스할 수 있습니다`다. 따라서 현재 직접 차단 원인은 앱 artifact의 Billing permission이 아니라 Google Payments 판매자 계정이 아직 Play Console에서 완료·연결 상태로 인식되지 않는 것이다.
- `설정 > 결제 프로필`에서 미완료 경고, 법적 주체·주소·판매자 정보 확인과 Google이 요구하는 인증 상태를 먼저 확인하고, 상품 페이지의 `판매자 계정 설정` 동작이 있으면 현재 Play 개발자 계정과 동일한 법적 주체의 프로필을 선택해 연결한다.
- 첨부 화면의 은행 계좌 추가는 실제 정산을 위해 필요하지만, 표시된 차단 문구만으로 계좌 미등록이 유일한 원인이라고 단정하지 않는다. 설정을 막 끝냈다면 계정 간 반영 지연, 다른 Google 계정/개발자 계정 선택 여부와 브라우저 세션도 확인한다.
- 판매자 계정이 완료·연결된 뒤에도 제품 생성에는 Billing 기능이 포함된 Play artifact가 필요할 수 있으므로 RevenueCat SDK/AAB 단계는 판매자 계정 차단을 해소한 다음 진행한다.

## 2026-09-07 RevenueCat 계정 준비와 백엔드 선행 개발 순서

- Google 정산 계좌 인증을 기다리는 동안 RevenueCat 계정과 토선생 프로젝트를 생성한다. 조직 소유 이메일과 MFA를 사용하고 팀 권한은 최소화한다.
- 한 RevenueCat project 아래 실제 bundle ID의 iOS app과 package name의 Android app을 등록하는 것을 기본 출발점으로 한다. 별도 staging bundle/package가 실제로 존재할 때만 별도 project/app 격리를 확정하며, sandbox transaction 표시는 production entitlement 판정과 구분한다.
- Google 제품이 아직 없어도 계정·project/app 생성은 가능하다. Store credential 연결, 제품 import, Offering/Package와 webhook 활성화는 계좌/상품 준비와 ADR-004 exact 계약 뒤에 수행한다.
- consumable fixed-term 제품은 RevenueCat Entitlement에 연결하지 않는다. App User ID는 raw userId가 아니라 기존 `purchaseAccountRefId`를 쓰며 익명 구매는 허용하지 않는다.
- Billing backend는 RevenueCat 계정 없이도 provider port/fake adapter, catalog, Purchase·기간형 entitlement·ledger, strict webhook contract, Mongo schema/index와 멱등/환불 테스트를 개발할 수 있다. 실제 secret과 Store 호출은 저장소·테스트에 넣지 않고 sandbox/staging gate에서만 사용한다.
- 구현 선행조건은 RevenueCat 계정 자체가 아니라 ADR-004와 PLAN-007의 승인이다. project/app identifier, SDK/API key 구분, webhook 인증, sandbox 판정, API reconciliation과 restore/transfer behavior를 exact contract로 먼저 고정한다.
- 사용자가 RevenueCat 계정과 토선생 project 생성을 완료했다. 공유된 account-scoped URL은 별도 브라우저 세션에서 로그인이 필요해 내부 설정은 확인하지 않았으며 credential을 요청하거나 입력하지 않았다.
- 다음 Console 작업은 project의 `Apps & providers`에서 실제 Android package name으로 Google Play app을 추가하는 것이다. Google service credential·제품 import·Offering/Package·webhook은 아직 필수가 아니며 Store 판매자 계정/상품과 ADR-004 준비 뒤 연결한다. iOS app도 실제 bundle ID가 확정돼 있으면 같은 project에 등록할 수 있다.
- Apple app 설정만으로 양 Store 연동이 완료되지 않는다. 동일 RevenueCat project에 Google Play app을 별도로 추가하고 package name, Google service credential과 RTDN을 구성해야 한다.
- Google 판매자 계정/은행 인증 대기 중에도 RevenueCat Google app 등록과 service account 준비는 가능하다. 일회성 제품 5개 생성·import·Package 연결과 실제 결제 테스트는 판매자 계정 및 Play product 접근이 열린 뒤 수행한다.
- Android package name은 Play production 출시 후 생성되는 값이 아니라 앱 build configuration의 `applicationId`로 이미 정해진 immutable Store 식별자다. AAB를 Play Console에 업로드한 현재 단계에는 이미 존재하므로 공개 게시를 진행하지 않고도 확인해 RevenueCat에 등록할 수 있다.
- authoritative 확인 위치는 모바일 source의 Android app module `defaultConfig.applicationId`다. `namespace`와 다를 수 있으므로 namespace를 대신 입력하지 않는다. Play Console 앱 URL/앱 세부정보에 표시되는 package와 source `applicationId`가 exact match하는지도 확인한다.

### RevenueCat Google service account credential 설정 기준

- RevenueCat 전용 Google Cloud service account를 만들고 기존 Firebase Admin/backend credential을 재사용하지 않는다. 선택한 Cloud project에서 Android Publisher API, Play Developer Reporting API와 Pub/Sub API를 활성화한다.
- service account의 Cloud role은 RevenueCat 공식 절차 기준 `Pub/Sub Editor`, `Monitoring Viewer`를 부여한다. 그 계정에서 JSON key를 1회 생성해 RevenueCat Google app 설정에 직접 업로드한다.
- 두 Cloud role은 Play Console이 아니라 해당 Google Cloud project의 `IAM 및 관리자 > IAM`에서 service account principal을 편집해 부여한다. 이미 계정이 있으면 principal 행의 수정에서 `역할 추가`를 두 번 사용하고, 생성 중이면 `이 서비스 계정에 프로젝트 액세스 권한 부여` 단계에서 같은 역할을 선택한다.
- 사용자가 RevenueCat service account에 `Pub/Sub Editor`, `Monitoring Viewer` 역할 부여를 완료했다. 다음 단계는 `IAM 및 관리자 > 서비스 계정 > 해당 계정 > 키 > 키 추가 > 새 키 만들기 > JSON`으로 user-managed private key를 생성해 RevenueCat에 직접 업로드하는 것이다.
- JSON은 생성 즉시 한 번 다운로드되는 private credential이다. 내용을 열어 복사하거나 파일명/내용을 기록하지 않고 secret manager에 보관하며 Git·문서·Jira·채팅·일반 공유 드라이브에 저장하지 않는다. RevenueCat validator 통과 뒤에도 rotation/revocation 가능한 key inventory를 유지한다.
- 사용자가 JSON을 RevenueCat에 업로드해 `File saved`까지 도달했으나 validator는 `Service account credentials need attention / unable to validate` 상태다. 이는 JSON 저장 실패가 아니라 Google API access 검증 미완료를 뜻한다.
- 신규 service account·Play permission은 공식 안내상 최대 36시간 전파될 수 있으므로 즉시 key를 삭제/재생성하지 않는다. service account enabled, Play 사용자 active, 토선생 app access와 네 account permission, 세 Google API, 두 Cloud role과 AAB 업로드를 확인하고 기다린 뒤 재검증한다.
- 36시간 뒤에도 실패하면 RevenueCat validator의 endpoint/permission별 상세 결과를 기준으로 수정하고 JSON을 다시 업로드해 검증을 트리거한다. 현재 Google Payments 판매자 설정/제품 API 접근이 진행 중인 점도 monetization endpoint 검증에 영향을 주는지 함께 확인한다.
- Google Play Console `사용자 및 권한`에서 service account email을 사용자로 초대하고 App permissions에 토선생 앱을 추가한다. Account permissions에는 `View app information and download bulk reports (read-only)`, `View financial data, orders, and cancellation survey responses`, `Manage orders and subscriptions`, `Manage store presence` 네 권한을 부여한다.
- 현재 Play Console 한국어 UI에서 위 네 권한은 각각 `앱 정보 보기 및 보고서 일괄 다운로드(읽기 전용)`, `재무 데이터, 주문, 취소 설문조사 응답 보기`, `주문 및 구독 관리`, `앱 정보 관리`다. 마지막 권한 설명에 가격 수정·인앱 상품 관리가 포함돼 RevenueCat 문서의 `Manage store presence`에 대응한다.
- service account 초대에서 `관리자(모든 권한)`, 출시/테스트 트랙, 정책, 리뷰, Play Games, 딥 링크와 Android 개발자 인증 권한은 부여하지 않는다. 머신 연동이 지속돼야 하므로 Play access 만료는 기본적으로 설정하지 않고 key rotation/revocation으로 관리한다.
- `Manage store presence`는 RevenueCat에서 Play product를 생성/수정할 때 필요하다고 공식 문서가 명시한다. 첫 출시에서는 RevenueCat의 product mutation을 사용하지 않더라도 공식 validator 통과와 향후 import/관리 계약을 위해 위 네 권한을 exact 기준으로 삼는다.
- JSON은 private key가 포함된 secret이므로 Git·문서·Jira·채팅에 넣지 않고 RevenueCat에 직접 업로드하며 승인된 secret manager에서만 보관한다. 생성/권한 변경 후 Google 전파에는 최대 36시간이 걸릴 수 있고 RevenueCat `Valid credentials` validator로 최종 확인한다.
- 공식 근거: `https://www.revenuecat.com/docs/service-credentials/creating-play-service-credentials`.

## 2026-09-07 RevenueCat iOS app 등록 입력 기준

- `App name`은 RevenueCat 내부 구분명으로 `토선생 iOS`를 권장한다. `App Bundle ID`는 Xcode target의 `PRODUCT_BUNDLE_IDENTIFIER` 및 App Store Connect 앱 정보와 exact match해야 하며 추측해서 새 값을 만들지 않는다.
- Custom URL Scheme은 RevenueCat Paywall preview/deep link용 선택 설정이다. 현재 앱에 등록된 scheme을 확인하기 전 임의 값을 넣지 않고 최초 연동에서는 비워둘 수 있다. 사용할 경우 Xcode URL Types/Info.plist에도 같은 scheme을 등록해야 한다.
- RevenueCat의 In-App Purchase Key는 App Store Connect의 `사용자 및 액세스(Users and Access) > 통합(Integrations) > 인앱 구입(In-App Purchase)`에서 생성한 전용 `.p8` key다. 일반 App Store Connect API key나 APNs key와 혼용하지 않는다.
- `.p8`는 한 번만 다운로드할 수 있는 credential로 직접 RevenueCat에 업로드하고 승인된 secret manager에만 보관한다. Git·문서·Jira·채팅에 파일이나 내용을 넣지 않는다. Key ID와 Issuer ID는 App Store Connect 표시값을 exact 입력한다.
- Apple Small Business Program 시작일은 실제 승인·효력 발생일이 있을 때만 입력하며 신청일을 임의로 넣지 않는다. 현재 미가입/심사 중이면 비워둔다.
- App-specific shared secret은 StoreKit 1 또는 RevenueCat 안내상 지원 대상 iOS 버전 때문에 필요한 경우에만 추가한다. 새 StoreKit 2 중심 연동에서는 legacy field를 기본적으로 비워두되 실제 iOS deployment target과 SDK 구성을 ADR-004/모바일 구현 전에 확인한다.

### App Store Connect API·Apple notification 추가 설정 기준

- `AuthKey_*.p8` App Store Connect API key는 앞의 `SubscriptionKey_*.p8` In-App Purchase key와 다른 credential이다. 전자는 RevenueCat의 product import·가격 metadata 동기화용이고 후자는 StoreKit transaction 검증용이므로 파일과 Key ID를 혼용하지 않는다.
- App Store Connect `Users and Access > Integrations > App Store Connect API`의 team key를 최소 필요 권한으로 생성해 RevenueCat에 직접 업로드한다. `.p8`는 1회 다운로드 credential이므로 secret manager에만 보관하고 Git·문서·Jira·채팅에 넣지 않는다.
- Apple Server Notification v2는 Store refund/revoke를 RevenueCat이 받도록 RevenueCat 제공 URL을 App Store Connect의 production·sandbox notification URL에 설정하는 방향을 유지한다. Billing은 Apple raw notification을 직접 받지 않고 인증된 RevenueCat webhook을 받는다.
- Apple Server Notification Forwarding URL은 현재 구조에서 비워둔다. RevenueCat과 Billing 양쪽에 Apple 원문을 중복 전달하는 별도 ingress는 ADR 승인 전 추가하지 않는다.
- `Track new purchases from server-to-server notifications`는 SDK에서 아직 식별되지 않은 구매를 RevenueCat이 새 구매로 추적할 수 있으므로 익명 구매 금지·`purchaseAccountRefId` exact binding과 충돌할 수 있다. ADR-004에서 식별/멱등 계약을 확정하기 전에는 OFF다.
- Refund request handling/Apple consumption data 전송은 Apple 환불 심사에 사용 데이터가 전달되는 정책·개인정보 기능이므로 자동 활성화하지 않는다. Retention Messaging API와 Subscription Offer key는 현재 consumable one-time 상품 범위 밖이다.
- RevenueCat Public SDK key는 모바일 앱에 포함 가능한 공개 식별자이고 server secret key와 다르다. 화면의 `REST API Identifier` (`app...`)는 RevenueCat app resource 식별자이며 SDK key나 product ID로 사용하지 않는다.
- Apple notification 설정 시 RevenueCat의 `Apple Server Notification URL`을 복사해 App Store Connect의 토선생 앱 `앱 정보 > App Store 서버 알림`에 production·sandbox URL로 등록하고 Version 2를 선택한다. `Apple Server Notification Forwarding URL`은 복사 대상이 아니며 비워둔다. 저장 직후 `No notifications received`는 실제 sandbox/production 이벤트 전까지 정상이다.
- 사용자가 Apple Server Notification v2 URL의 App Store Connect 등록을 완료했다. 이는 Apple→RevenueCat 알림 목적지 설정 완료를 의미하며, 실제 수신 검증·Apple credential·상품 import·RevenueCat Offering 및 Billing webhook/ledger 구현 완료를 의미하지는 않는다.

## 2026-09-07 RevenueCat 도입 적합성 검토

- RevenueCat은 iOS·Android consumable 구매 SDK, Offerings/Packages, Paywall, 구매 이벤트 정규화, webhook과 매출 분석을 제공하므로 앱의 Store별 결제 UI·SDK 유지보수와 성장 실험은 줄일 수 있다.
- 사용자 관점에서 가장 큰 도입 효과는 Apple·Google별 상품 조회·구매·오류·복원 UI를 공통 SDK와 Offering으로 다루고, 가격·상품 노출·Paywall 문구를 앱 심사/배포 없이 원격으로 바꾸며, 양 Store 매출·전환을 한 화면에서 보는 것이다. Store 정책/API 변경 대응도 RevenueCat SDK 업데이트로 상당 부분 흡수할 수 있다.
- 서버 관점에서는 Apple Notification V2와 Google RTDN의 서로 다른 payload를 각각 해석하는 대신 RevenueCat webhook을 공통 ingress로 받을 수 있다. 다만 webhook 인증·멱등성·누락 reconciliation과 토선생 기간형 권리 원장은 계속 필요하므로 `결제 plumbing 감소`이지 Billing 제거가 아니다.
- 현재 토선생 상품은 1·3·7·14·30일을 Billing catalog가 부여하는 consumable one-time product다. RevenueCat 공식 계약상 consumable을 Entitlement에 연결하면 만료 없이 영구 unlock으로 보이며, 실제 지급·소진 상태는 RevenueCat이 관리하지 않는다. 따라서 기간 계산, timeline stacking, 무료권 보존, Reservation, 환불 차단과 ledger는 계속 Billing이 authoritative하게 관리해야 한다.
- 도입 검토 당시 표준 RevenueCat SDK가 purchase completion을 기본 수행하므로 기존 `Billing Transaction commit → Apple finish/Google consume` 순서를 유지하려면 `purchasesAreCompletedBy=.myApp` 방식이 필요하다고 확인했다. 이후 RevenueCat 채택과 함께 표준 completion 및 webhook/API reconciliation 방식이 승인됐다.
- webhook은 빠른 수신·HMAC 검증·event ID 멱등 처리가 필요하고 최대 5회 재시도 후 중단될 수 있다. at-least-once 중복과 지연을 전제로 inbox, provider 조회와 periodic reconciliation은 여전히 필요하다. 대부분 Store의 one-time refund 전체 수신을 위해 Apple/Google Platform Server Notifications 설정도 RevenueCat 측에 계속 연결해야 한다.
- 사용자 식별은 raw userId 대신 기존 사용자·환경별 stable lowercase UUID v4 `purchaseAccountRefId`를 RevenueCat custom App User ID로 사용하는 것이 현재 privacy/owner 계약과 맞다. 익명 ID alias/restore는 다른 토선생 계정으로 구매가 합쳐지거나 이전될 수 있으므로 구매 전 로그인 강제와 `Keep with original App User ID` 검토가 필요하다.
- 검토 당시 기본 권장안은 직접 StoreKit/Google Play Billing 유지였으나 사용자가 출시 편의성을 우선해 RevenueCat 도입을 승인했으므로 위 `RevenueCat 결제 연동 채택` 결정이 이를 supersede한다. RevenueCat은 Billing 대체가 아닌 Store adapter/event source다.
- 2026-09-07 공개 가격은 월 tracked revenue 2,500 USD까지 무료, 이후 tracked revenue의 1%다. 실제 one-time 매출 산정·세금·계약 조건은 가입 전 견적/약관에서 다시 확인한다.
- 공식 근거: `https://www.revenuecat.com/docs/platform-resources/non-subscriptions`, `https://www.revenuecat.com/docs/migrating-to-revenuecat/sdk-or-not/finishing-transactions`, `https://www.revenuecat.com/docs/integrations/webhooks`, `https://www.revenuecat.com/docs/customers/identifying-customers`, `https://www.revenuecat.com/docs/projects/restore-behavior`, `https://www.revenuecat.com/pricing`.

## 2026-09-07 Apple·Google 일회성 fixed-term 상품 최종 변경

- 사용자가 Apple과 Google 모두 재구매 가능한 일회성 상품으로 통일하는 안을 승인했다. Apple은 offer별 Consumable In-App Purchase 5개, Google은 offer별 consumable one-time product 5개를 사용한다.
- Store product ID는 `PREMIUM_1D/3D/7D/14D/30D`와 환경별 Billing catalog에서 exact mapping하며 권리 기간은 Store subscription expiry가 아니라 각각 24·72·168·336·720시간의 catalog duration으로 계산한다.
- 구매는 자동 갱신되지 않는다. active/scheduled 유료 권리가 있을 때 재구매하면 기존 timeline 뒤에 새 duration을 이어 붙이며 무료권은 보존한다.
- 이 시점에는 Billing 검증과 local commit 뒤 Apple finish/Google backend consume을 확정했으나, 2026-09-07 RevenueCat 채택으로 transaction completion은 RevenueCat 표준 SDK가 담당하는 것으로 supersede됐다. Store transaction unique로 권리를 한 번만 만드는 불변식은 유지한다.
- consumable Store restore를 과거 권리의 source로 사용하지 않고 Billing ledger와 current entitlement API를 사용한다. Store에는 아직 완료되지 않은 transaction 복구와 provider refund/revoke 재검증 책임을 둔다.
- Google prepaid의 14일 미지원과 달력 1개월/고정 30일 불일치는 이 변경으로 해소됐다. 실제 product ID 10개, 가격, 국가와 review metadata는 ADR-004 naming 확정 후 Console에서 생성한다.
- 현재 책임 경계는 `Store=상품·가격·실제 결제·거래/환불`, `RevenueCat=SDK·Offering·transaction completion·검증 데이터/webhook`, `Billing=product ID→offer 매핑·정확한 기간·stacking·권리 복원·무료권 우선순위`다. Store completion 뒤 Billing 반영 지연을 webhook/API reconciliation으로 복구하는 책임이 중요하다.

## 2026-09-07 Apple 결제 외부 준비 시작

- Apple 준비는 상품 ID 생성 전에 Developer Program 활성화, App Store Connect 앱/bundle record 존재, Paid Applications Agreement·세금·은행 정보 완료 여부를 먼저 확인한다.
- 사용자가 토선생 앱이 이미 App Store에 배포됐다고 확인했다. 따라서 Developer Program 가입과 App Store Connect 앱/bundle record는 준비된 것으로 본다. 단, 무료 앱 배포 이력만으로 Paid Applications Agreement·세금·은행 정보가 활성화됐다고 보지는 않으며 App Store Connect에서 별도 확인해야 한다.
- 위 조건이 준비된 뒤 App Store Connect에 offer별 Consumable In-App Purchase 5개를 생성한다. 기간은 Apple 상품 설정이 아니라 Billing catalog에서 관리한다.
- 실제 product ID, 가격, 판매 국가와 현지화는 아직 생성·확정하지 않았다. product ID는 생성 후 변경 제약이 있으므로 naming을 ADR-004에서 확정하기 전 임의 생성하지 않는다.
- 첫 사용자 확인사항은 기존 Apple Developer Program과 App Store Connect 앱 등록이 이미 완료되어 있는지 여부다.
- Apple 공식 절차상 IAP 판매에는 `Account Holder`가 `Business > Agreements > Paid Apps > View and Agree to Terms`에서 Paid Apps Agreement를 체결해야 한다. 계약 동의는 되돌릴 수 없으므로 계정의 법적 주체를 확인한 뒤 진행한다.
- 세금 정보는 `Business > Agreements > Tax Forms`에서 등록한다. 모든 개발자는 미국 세금 양식을 완료해야 하며, 미국 외 계정은 질문에 따라 W-8BEN/W-8BEN-E/W-8ECI 등이 제시된다.
- 한국 기반 개발자는 Apple 안내상 유효한 사업자등록번호와 최근 90일 이내 영문 사업자등록증명 또는 비영리 국세청 고유번호와 최근 90일 이내 영문 증명 서류가 추가로 요구된다. 정확한 법인/개인·영리/비영리 양식 선택은 계정 법적 주체와 세무 확인이 필요하다.
- 은행 정보는 Paid Apps Agreement 체결과 필수 세금 양식 제출 뒤 `Business > Agreements > Bank Accounts > Add Bank Account`에서 입력한다. Apple Developer Program의 법적 주체와 계좌 명의 정보가 정확히 일치해야 한다.
- 사용자가 App Store Connect에서 `Agreements`를 찾지 못했다. Apple 공식 경로는 `Business > Agreements`이며, `Business` 또는 그 안의 `Agreements`가 보이지 않으면 현재 로그인 계정이 Account Holder/Admin/Finance 권한인지와 여러 개발자 팀 중 토선생 앱 소유 팀을 선택했는지 먼저 확인한다. 계약 서명 자체는 Account Holder만 가능하다.
- 한국어 App Store Connect의 공식 메뉴명은 `비즈니스 > 계약 > 유료 앱 > 약관 보기 및 동의하기`다. 영문 안내의 `Agreements`가 한국어 화면에서는 `계약`으로 표시된다.
- Paid Apps 진행 중 `이름 확인 문서` 업로드가 표시됐다. 이는 세금 양식과 별도로 Apple 계정에 등록된 개인/법인의 법적 이름을 공식 사업자 또는 법원 문서로 증명하는 단계로 해석한다. 화면에 표시된 이름과 문서의 이름이 exact match해야 하며, 개인사업자는 사업자등록증/사업자등록증명, 법인은 사업자등록증명 또는 법인등기사항증명서처럼 발급기관과 법인명이 명확한 최신 원본을 우선 사용한다.
- `문서 언어: 한국어`이면 한국어 원문을 제출할 수 있다는 의미로 보고 임의 영문 번역본으로 바꾸지 않는다. 다만 이후 한국 세금 정보 단계에서 요구하는 최근 90일 이내 영문 사업자등록증명은 별도 제출물이다.
- 계정에 표시된 법적 이름이 문서와 다르거나 개인 계정인데 조직 문서를 요구하는 경우에는 임의 문서를 제출하지 않고 계정 유형/법인명 정정 또는 Apple Developer Support 확인을 우선한다.
- 이어서 `주소 확인 문서` 업로드가 표시됐다. 입력한 주소와 동일한 현재 사업장/본점 주소가 이름 확인 문서에 함께 적혀 있다면 동일한 사업자등록증·사업자등록증명 또는 법인등기사항증명서를 주소 확인에도 사용할 수 있다. 문서의 주소가 이전 주소라면 파일을 수정하지 않고 Apple 입력 주소를 공식 문서와 맞추거나 사업자/법인 주소 변경을 먼저 완료한다.
- 주소 문서는 상호/법인명, 전체 주소, 발급기관과 문서 전체가 식별 가능해야 하며 임의 마스킹·편집·잘린 캡처를 사용하지 않는다. 개인 주소 문서는 Apple이 화면에서 허용 유형을 명시한 경우에만 해당 공식 문서를 사용한다.
- 사용자가 Apple 디지털 서비스법 규정 준수 정보를 제출했고 `27개의 국가 또는 지역`, 최종 업데이트 `2026-09-07`, 상태 `심사 중`임을 확인했다. 현재는 제출 완료·Apple 검토 대기 상태로 보며 추가 요청 없이 동일 문서를 수정하거나 재제출하지 않는다. Account Holder 이메일과 Business의 규정 준수 상태에서 `조치 필요` 또는 추가 자료 요청이 생기는지만 확인한다.
- Google 앱은 현재 최종 출시 심사 대기 중이라고 사용자가 확인했다. 앱 release 심사 중에도 merchant/payment profile, RevenueCat Google app 연결에 필요한 Cloud project/API·최소 권한 service account, RTDN Pub/Sub, license tester와 internal test 준비를 병행할 수 있다.
- Google one-time product ID도 생성 후 이름 변경 제약이 있으므로 ADR-004 naming/mapping 확정 전 임의 생성하지 않는다. 앱 승인과 merchant verification/product activation이 끝나기 전에는 production 판매 완료로 간주하지 않는다.
- Google prepaid의 14일 미지원과 1개월 의미 차이를 확인한 뒤 사용자가 양 Store 모두 one-time으로 재승인했다. 이전 C9-S1 provider-native subscription 안은 최신 C9-S1 one-time 계약으로 supersede됐다.

## 2026-09-06 기간제 무제한 결제 당시 확정 — Store 유형은 2026-09-07 supersede

- 사용자는 1·3·7·14·30일 fixed-term premium 결제와 나머지 권장안을 모두 승인했다. 자동 갱신, paid credit와 fixed-unit exam pass는 현재 범위가 아니다. 기간제 unlimited Store 상품은 2026-09-07 consumable one-time으로 재승인됐다.
- 내부 offer는 `PREMIUM_1D/3D/7D/14D/30D`, 공통 benefit은 `PREMIUM_SUBSCRIPTION`, 기간형 권리는 `SubscriptionEntitlement`다. 각 기간은 UTC 24·72·168·336·720시간으로 계산한다.
- 당시에는 Apple Non-Renewing Subscription과 Google prepaid subscription/base plan을 승인했으나 2026-09-07 양 Store one-time product로 대체했다. Billing 발급 lowercase UUID v4 `purchaseAccountRefId`와 server verification 계약은 유지한다.
- 앱은 Billing public API를 직접 호출한다. Identity Access Token은 기존 Learning Core audience에 `tosunsaeng-billing`을 추가하며 Billing은 RS256, exact issuer/JWKS/audience, lowercase UUID `sub`, `iat/exp/jti`, 최대 60초 skew와 `billing:read`·`billing:purchase` scope를 검증한다.
- 같은 user가 기간을 재구매하면 `startsAt=max(providerStart,currentPaidTimelineEndsAt)`, `endsAt=startsAt+duration`으로 이어 붙인다. paid entitlement 상태는 `SCHEDULED/ACTIVE/EXPIRED/REVOKED`, purchase 상태는 `PENDING/VERIFIED/REFUNDED/REVOKED`로 분리한다.
- Reservation resolver는 ACTIVE paid를 먼저 사용하고 무료 `TrialClaim`·Grant·unit은 변경하지 않는다. paid가 없을 때만 기존 `FREE_EXAM_ONCE`를 사용하므로 유료 기간 종료 후 미사용 무료권이 남는다.
- provider-confirmed refund/revoke는 append-only reversal로 기록한다. `RESERVED`는 종료, `OPEN`·`RETAKE_AVAILABLE`은 접근과 replacement를 차단하고, 이미 제출된 `GRADING`만 완료하며 `COMPLETED` history는 보존한다. refund 전 confirm 여부만으로 replacement를 계속 허용하지 않는다.
- 결제 반영은 RevenueCat 표준 SDK client sync, Authorization+HMAC webhook과 periodic RevenueCat API reconciliation이 RevenueCat event/Store transaction unique 및 같은 Transaction service로 수렴한다. 이 내용이 이전 direct Store notification·finish/consume 방식을 supersede한다.
- 전체 승인 계약은 `docs/contracts/FIXED_TERM_PREMIUM_PAYMENT_CONTRACT.md`와 `docs/codex/CONTRACT_DECISIONS.md` C1-A/C2-A/C9-S1~S8에 기록했다. 다음 작업은 결제 ADR와 구현 계획서 작성이며 애플리케이션 코드는 아직 변경하지 않았다.
- 구현 전 남은 입력은 실제 Store product ID, 가격·판매 국가, Store review, exact public API DTO/error/rate limit, Mongo collection/index와 staging quota 기반 운영 조정값이다.
- 계약의 사용자 관점 요약은 "결제를 Store에서 완료하고 Billing 검증·저장이 끝나면 정해진 시간 동안 시험을 횟수 차감 없이 시작할 수 있으며, 유료 기간에는 무료 1회권이 보존된다"이다. 무제한은 동시 시험 무제한이 아니라 기존 단일 active Session/AttemptGroup 제약 안에서 기간 중 신규 시험 횟수를 unit으로 차감하지 않는다는 의미다.
- 구현 관점의 핵심 객체는 상품 종류 `BenefitDefinition`, 판매 기간 `offer`, Store 결제 한 건 `Purchase`, 실제 시작·종료 권리 `SubscriptionEntitlement`, 변경 감사 `ledger`, 시험 시작 승인 `Reservation`, 해당 시험·재응시 묶음 `AttemptGroup`이다.
- public ingress는 기존 public ALB의 Billing 전용 host/path rule+별도 target group, 구매는 ACTIVE MEMBER만, `purchaseAccountRefId`는 사용자·환경별 stable 값으로 확정했다. Guest는 `billing:read`만 받고 purchase와 paid merge는 현재 범위에서 제외한다.
- stacked entitlement 중간 환불 시 해당 slot을 제거하고 뒤의 verified 기간을 즉시 앞으로 재배치한다. 정규화 결제·원장은 5년, provider event inbox는 120일, backup은 최대 35일 보존한다.
- reconciliation 기본값은 PENDING·Store-completed/Billing-pending 5분, ACTIVE/SCHEDULED 6시간, 최근 90일 terminal 일 1회와 100건 batch다. staging RevenueCat API quota 측정 뒤 환경 설정만 보수적으로 조정할 수 있다.
- Identity는 현재 audience 한 개와 공통 default scope를 모든 사용자 token에 넣고 Guest도 발급한다. 따라서 결제에서는 audience list reader-first 변경뿐 아니라 MEMBER token에만 `billing:purchase`, Guest에는 최대 `billing:read`만 발급하는 account-type별 scope 계약이 필요하다.

## 2026-09-06 결제 출시를 위해 사용자가 외부에서 준비할 항목

- Apple은 Developer Program/App Store Connect 권한, Paid Applications Agreement·세금·은행 정보, app/bundle record, 5개 Consumable In-App Purchase product ID·가격·국가·현지화/review metadata, RevenueCat Apple app 연결 credential, App Store Server Notifications→RevenueCat 연결과 sandbox tester가 필요하다.
- Google은 Play Console 개발자·merchant/payment profile, app/package record, 5개 consumable one-time product ID·가격·국가, RevenueCat Google app 연결용 Cloud project/Play Android Developer API/service account 최소 권한, RTDN Pub/Sub→RevenueCat 연결, license tester와 internal test track이 필요하다.
- AWS에서는 실제 public hostname, ACM certificate, 기존 ALB listener의 Billing host/path allowlist·별도 target group·SG, provider notification public route와 Store credential용 Secrets Manager 항목을 환경별로 준비해야 한다.
- 사용자가 직접 선택하거나 승인할 business 값은 영구적으로 사용할 product ID naming, 5개 가격, 판매 국가·세금/현지화, 약관·개인정보·환불 안내와 Store review 제출 정보다. product ID는 생성 뒤 변경·재사용이 제한될 수 있으므로 ADR naming 확정 전에 임의 생성하지 않는다.
- `purchaseAccountRefId`는 Billing이 자동 발급하고 transaction ID/purchase token은 Store가 결제 때 발급하므로 사용자가 신청하지 않는다. Identity audience/scope, API DTO, Mongo index와 refund event는 코드·ADR 작업이다.
- `.p8`, service-account JSON, issuer/key ID 조합, private key와 notification credential은 채팅·Jira·Git에 올리지 않고 환경별 Secret Manager에 직접 등록한다.

## 2026-09-06 환불 뒤 진행 중 시험 이탈·재시작 검토 기록 — 최신 strict revoke로 대체

- 최초 검토에서는 confirm된 AttemptGroup의 replacement를 refund 뒤에도 허용하는 방안을 고려했으나 start→refund→exit→replacement를 통한 사실상 무료 응시 악용 때문에 채택하지 않았다.
- 최종 확정은 provider-confirmed refund 시 `RESERVED`를 종료하고 `OPEN`과 `RETAKE_AVAILABLE`의 추가 진행·replacement를 차단하며, 이미 제출돼 `GRADING`인 Session만 처리 정합성을 위해 terminal까지 완료하고 `COMPLETED` history는 삭제하지 않는 방식이다.
- refund와 reserve/Session commit/confirm race는 먼저 commit된 상태와 CAS로 판정한다. refund가 먼저면 confirm을 revoked error로 거절하고 Learning Core가 생성된 Session을 access-revoked로 보상한다.
- 이 보정은 Learning Core가 refund-revoked AttemptGroup/Session을 실제로 차단할 수 있는 durable event 또는 동등한 fail-closed projection이 필요하다. Billing의 신규 reserve 차단만으로는 이미 발급된 Session의 답안 제출·채점 요청을 막을 수 없다.
- Store가 환불을 승인하기 전에 이미 완료된 시험 가치는 기술적으로 회수할 수 없다. Apple의 지원 가능한 consumption 정보와 Google의 void/refund 관련 사용 evidence를 최소 정규화 상태로 제공하고, refunded-after-use 지표·반복 악용 운영 검토를 두되 앱 주장만으로 환불·차단하지 않는다.

## 2026-09-05 Store 값과 토선생 사용자 연결 경계

- Store product ID는 어떤 상품을 샀는지 판정하는 데 사용하고 provider transaction ID/purchase token은 같은 구매의 중복 반영을 막는 데 사용한다. 이 값들은 구매 증거이지 토선생 계정의 영구적인 owner 식별자가 아니다.
- Apple/Google 계정은 토선생 계정과 1:1이라는 보장이 없다. 한 Store 계정으로 여러 토선생 계정을 사용하거나, 기기의 Store 계정을 바꾸거나, 같은 기기에서 다른 사용자가 로그인할 수 있으므로 Store 측 계정만으로 entitlement를 정하면 다른 토선생 사용자에게 권리가 붙을 수 있다.
- 앱이 Apple ID 자체를 안정적인 사용자 ID로 받는 구조도 아니며 provider별 식별 방식과 복원 semantics가 다르다. Store 계정값에 owner를 결속하면 Apple·Google 간 동일한 소유권 규칙을 만들기 어렵고 개인정보·계정 공유 영향도 커진다.
- 따라서 구매 시점에 로그인된 토선생 사용자에게 Billing이 발급한 opaque `purchaseAccountRefId`를 Store transaction에 함께 넣고, 검증된 transaction의 reference가 현재 사용자에게 발급된 값과 일치할 때만 entitlement를 연결한다.
- Store 값은 상품·거래 검증에, Billing reference는 앱 사용자 소유권 검증에 각각 사용한다. phone rejoin이나 같은 Store 계정만으로 유료 권리를 이전하지 않고 `UserMerged`처럼 승인된 계정 lifecycle만 별도 migration으로 처리한다.
- 같은 Apple 계정이면 같은 토선생 계정이라는 전제는 토선생 로그인이 Sign in with Apple만 사용하고 그 subject를 영구 1:1 계정키로 강제할 때에만 성립한다. 현재 Identity는 별도 토선생 계정과 phone lifecycle을 소유하므로 App Store의 Media & Purchases 계정과 토선생 로그인 계정을 같은 identity로 간주하지 않는다.
- 가족의 Apple 계정 공유는 보조적인 오귀속 사례일 뿐 핵심 근거가 아니다. 더 근본적인 이유는 Apple이 앱에 Apple ID 자체를 결제 owner ID로 제공하지 않고, 앱 계정과 transaction을 연결하도록 개발자가 제공하는 `appAccountToken`을 지원한다는 점이다. 따라서 `purchaseAccountRefId`는 Store 값을 우회하는 값이 아니라 provider transaction에 토선생 계정 context를 안전하게 넣는 표준 연결값이다.
- 쉬운 역할 구분은 `productId=상품표`, `transactionId/purchaseToken=영수증 번호`, `purchaseAccountRefId=받는 토선생 회원 번호`다. Billing은 세 값을 함께 검증해야 정확한 상품을 정확히 한 번, 정확한 사용자에게 지급할 수 있다.

## 2026-09-05 결제 포함 1차 개발 범위 전환 검토

- Learning Core 후속 보정은 PR #29 merge `88b46c6`으로 `develop`과 `origin/develop`에 반영됐다. phone continuation attemptGroupId lowercase UUID v4, migration orphan/owner mismatch preflight, unknown commit inbox 수렴·503 mapping, replica-set 회귀 범위와 CI task가 코드상 보완됐다.
- 재검증에서 Learning Core `./gradlew clean test`는 성공했고 Node migration test 7개와 `git diff --check`도 성공했다. 로컬 `mongoIntegrationTest`는 Docker provider 부재로 initialization 실패했으므로 GitHub CI의 해당 required job 성공 여부와 staging E2E는 별도 production gate다.
- 무료시험·owner lifecycle은 핵심 애플리케이션 개발 완료로 보고 결제 애플리케이션 개발을 시작할 수 있다. 단, 관련 feature flag 활성화와 production 출시는 schema migration, 실제 Lattice/IAM/SG, Mongo replica-set CI, 중복·역순·응답 유실·인증 실패 staging E2E 뒤에만 진행한다.
- 사용자가 1차 개발 범위에 결제를 포함하려는 의사를 밝혔다. 기존 `CONTRACT_DECISIONS`의 deferred 상태와 C9~C11은 credit pack/3일 pass 초안이므로, 사용자 선호인 단순 premium subscription을 구현하기 전에 이를 subscription 계약으로 명시적으로 대체해야 한다.
- 다음 권장 작업은 코딩이 아니라 구독 결제 계약 확정이다. plan/기간·Store product mapping, 제공 권리, 무료권과 resolver 우선순위, 앱→Billing API와 JWT audience, account binding/restore, renewal·grace·cancel·expiry, refund/revoke·notification 및 reconciliation을 확정한 뒤 별도 ADR·PLAN·Jira로 vertical slice를 나눈다.

## 2026-09-05 기간제 무제한 이용권 당시 선택지 — Store 유형은 2026-09-07 supersede

- 사용자가 유료 상품을 `1일`, `3일`, `7일`, `14일`, `30일` 동안 무제한 사용하는 고정 기간 상품으로 정했고, 유료 이용 중에도 전화번호당 무료 1회권은 소비하지 않고 보존하기로 했다.
- 이 상품은 자동 갱신형 월 구독보다 fixed-term access pass에 가깝다. Apple은 limited duration용 Non-Renewing Subscription, Google은 자동 갱신되지 않고 top-up으로 연장하는 prepaid subscription plan을 공식 제공하므로 provider별 native fixed-term 유형을 우선 검토한다. 정확한 5개 기간의 Console 등록 가능 여부는 실제 상품 생성 전 각 Store에서 확인한다.
- Store mapping 권장안은 Apple non-renewing subscription 5개 product와 Google 하나의 subscription product 아래 5개 prepaid base plan을 Billing의 5개 내부 offer code에 매핑하는 방식이다. 두 Store를 모두 consumable one-time product로 통일하는 대안은 server 구현이 단순하지만 restore와 Store 상품 의미가 약해진다. auto-renewable은 고정 기간·무자동갱신 요구와 맞지 않는다.
- account binding은 Billing이 발급한 환경별 비개인 opaque purchase account ID를 Apple `appAccountToken`과 Google obfuscated account ID에 전달하고 canonical user와 서버에서 연결하는 안을 권장한다. raw userId·phone·email을 Store 식별자로 보내거나 device에 entitlement를 결속하지 않는다.
- 앱 API는 앱→Billing direct public API와 Identity access JWT의 Billing audience를 권장한다. Learning Core proxy는 초기 endpoint가 적지만 결제 보안·장애·DTO 책임을 시험 서비스에 결합한다.
- fixed-term lifecycle은 결제 검증 완료 시 시작하고, active pass 재구매는 `max(now, currentEndsAt)`부터 기간을 이어 붙이는 안을 권장한다. 자동갱신이 없으므로 grace/account-hold/cancel-scheduled 상태는 만들지 않고 pending, active, expired, revoked/refunded를 provider payment 상태와 entitlement 상태로 분리한다.
- 당시에는 confirm된 AttemptGroup의 replacement 보존을 권장했으나 2026-09-06 악용 검토에서 폐기했다. 최신 확정은 위 strict revoke 상태표를 따르며 기존 ledger와 완료 history는 삭제하지 않고 무료 TrialClaim도 복원·소비하지 않는다.
- activation은 client purchase sync와 server notification을 함께 사용하고, notification 누락·지연을 보완할 주기적 Store reconciliation을 둔다. client-only 또는 notification-only는 각각 환불 누락과 활성화 지연 위험이 있다.

## 2026-09-05 C9 Store 상품·account binding 당시 승인 — 2026-09-07 supersede

- 사용자가 4번 권장안을 승인해 `CONTRACT_DECISIONS.md`에 C9-S1 active contract로 반영했다. Apple Non-Renewing Subscription, Google prepaid subscription base plan과 Billing 내부 `PREMIUM_1D/3D/7D/14D/30D` exact mapping을 사용한다.
- 앱의 가격·기간 주장은 신뢰하지 않고 Billing이 Store transaction과 product/base plan을 서버에서 검증한 뒤 catalog duration으로만 entitlement를 만든다. provider transaction/event unique로 client retry·notification·reconciliation 중복 지급을 차단한다.
- Billing 발급 lowercase UUID v4 `purchaseAccountRefId`를 Apple `appAccountToken`과 Google obfuscated account identifier에 사용한다. raw userId·phone·email·device ID를 Store에 결속하지 않고 current canonical user와 Billing 내부에서만 연결한다.
- 이미 반영된 구매는 Billing current entitlement 조회로 복원하고 전달 전 앱 종료는 Store purchase query, server notification과 reconciliation으로 수렴한다. Guest `UserMerged` 외 phone proof나 동일 device/Store account만으로 유료 권리를 다른 앱 계정에 이전하지 않는다.
- 기존 C9-A consumable/credit, C10 credit expiry와 3일 pass 초안은 역사적 기록으로 명시했다. 이 시점에는 C1/C2 public API/auth, entitlement activation·stacking, refund/revoke, notification/ack 책임과 reconciliation이 미확정이었으나 이후 위의 "기간제 무제한 결제 계약 전체 승인"에서 모두 확정됐다.

## 2026-09-05 cross-service 기능 완결성 재점검

- 무료시험 phone continuation과 Guest `UserMerged`의 핵심 정상 흐름은 세 저장소에 구현·병합됐다. 그러나 production-safe 완료로 보기는 이르며 Learning Core 후속 보정과 staging gate가 남아 있다.
- 확정 코드 누락 1: Learning Core phone continuation discovery의 `attemptGroupId`는 계약상 lowercase UUID v4인데 client decoder와 saga 최초 검증이 opaque text만 허용한다. 잘못된 Billing 200 응답을 operation snapshot 생성 전에 strict 거절하도록 보완해야 한다.
- 확정 코드 누락 2: Learning Core `user-merged-prepare.js`는 owner UUID, active Session 중복, index와 기존 MERGED guard를 확인하지만 orphan `ExamResult`/`ExamSummary`와 Result/Summary owner↔Session owner 불일치를 검사하지 않는다. apply 전 데이터 정합성 preflight와 Node 회귀 테스트를 추가해야 한다.
- 확정 CI 누락: `build.gradle`은 `check`에서 `mongoIntegrationTest`를 연결하지만 staging workflow가 `./gradlew clean test`만 실행한다. replica-set Mongo Transaction 검증이 required gate에서 실제 수행되도록 workflow를 보정해야 한다.
- 잠재 기능 누락: `UserOwnedTransactionExecutor`는 `UnknownTransactionCommitResult`를 재실행하지 않는 점은 안전하지만 eventId/inbox를 재조회해 commit 여부로 수렴하지 않고 예외를 그대로 전달한다. 해당 오류가 `TransactionSystemException`으로 포장되면 Controller advice의 `DataAccessException` handler 밖에서 500이 될 가능성이 있어, 재조회 수렴과 503 mapping을 실제 예외 형태로 고정하는 테스트가 필요하다.
- 통합 검증 범위도 계획보다 작다. 현재 UserMerged replica-set test는 4개이며 failure injection rollback, non-terminal operation, concurrent duplicate/write/Callback, unknown commit result 수렴 등의 계획 조건이 빠져 있다.
- 일반 Java 483개와 Node migration 6개는 직전 검토에서 성공했지만 Docker daemon 부재로 `mongoIntegrationTest`는 실행되지 않았다. 위 보정과 Docker 가능한 CI 성공, 실제 Lattice/JWT·순서 역전·응답 유실 staging E2E 전에는 관련 production flag를 OFF로 유지한다.

## 2026-09-05 다음 작업 재확인

- 즉시 다음 구현 대상은 Learning Core `TMI-125`다. Billing phone continuation reader-first 계약을 적용하고 `UserMerged` consumer·workload JWT verifier·source deny와 실제 merge ownership migration을 구현한다.
- phone 재가입은 Learning Core owner event를 받지 않는다. 새 target user에게 과거 Session·답안·결과를 이전하지 않고 Billing continuation discovery가 승인한 `OPEN`/`RETAKE_AVAILABLE` group에 새 examId의 replacement Session만 연결한다.
- Billing의 신규 continuation 응답 optional field를 먼저 허용하고, target에 기존 Session이 없을 때 continuation 조회 후 200이면 six-field reserve exact echo, 204면 일반 INITIAL 흐름을 수행한다. 예상하지 못한 REPLACEMENT나 context 불일치는 fail-closed하고 Reservation을 cancel로 보상한다.
- Learning Core 구현·병합 뒤 Docker가 가능한 환경에서 Billing Testcontainers 4개를 포함한 전체 테스트를 재실행한다.
- 마지막 gate는 Lattice exact route/IAM·SG, schema v4 preflight, 세 서비스 feature flag OFF 배포와 staging 중복·역순·응답 유실·인증 실패 E2E다. 이 검증 전 production flag는 활성화하지 않는다.

## 2026-09-05 Learning Core TMI-122·TMI-125 병합 구현 검토

- Learning Core `develop`과 `origin/develop`은 PR #28 merge `8c8208b`에서 일치한다. phone continuation `TMI-122`는 PR #27, UserMerged `TMI-125`는 PR #28로 병합됐다.
- Billing continuation 조회·6-field reserve·새 target Session 연결과 UserMerged exact route/workload JWT/source deny/owner migration의 큰 방향은 승인 계약과 일치한다. `TrialOwnerRebindApproved` Learning Core consumer와 phone 기반 과거 시험 이전은 추가되지 않았다.
- `./gradlew clean test`는 Java 483개가 failures/errors/skipped 0으로 성공했고 Node migration test 6개와 `git diff --check`도 성공했다. `mongoIntegrationTest`는 Docker daemon 부재로 initialization 단계에서 실행되지 못했다.
- 배포 workflow는 현재 `./gradlew clean test`만 실행하므로 `build.gradle`에서 `check`에 연결한 `mongoIntegrationTest`가 CI gate에서 실제 실행되지 않는다. workflow 또는 별도 required workflow 보정이 필요하다.
- UserMerged Mongo 준비 script는 owner UUID·active 중복·index·기존 MERGED guard만 검사하며 계획서의 orphan Result/Summary와 Session-owner 불일치 검사를 구현하지 않았다. apply 전에 해당 preflight와 테스트를 보완해야 한다.
- Learning Core phone discovery decoder는 `attemptGroupId`를 opaque text로 허용하지만 Billing 계약은 lowercase UUID v4다. malformed 200 response를 operation snapshot에 저장하기 전에 strict 거절하도록 decoder와 회귀 테스트를 보완해야 한다.
- Learning Core 작업 트리에는 검토 시작 뒤 기존 TMI-125 Jira 완료 기록인 CURRENT_STATE/WORKLOG 변경이 남아 있으며 애플리케이션 파일은 clean이다. 이를 수정하거나 되돌리지 않았다.

## 2026-09-03 PHONE_REJOIN continuation discovery 구현

- Learning Core 전용 `POST /internal/v1/reservations/continuations/phone`을 추가했다. target userId의 current owner transition이 PHONE_REJOIN이고 group이 OPEN/RETAKE_AVAILABLE이면 Billing 저장값인 reason/id/attemptGroupId/mockExamId를 200으로 반환하고 대상 없음은 204다.
- owner CAS는 `ownerTransitionReason`, `ownerTransitionId`를 같은 update에 저장한다. 다음 owner CAS가 이 값을 교체하므로 context는 current owner epoch에 결속되며 source userId나 raw phone을 새로 저장하지 않는다.
- reserve strict decoder는 기존 3-field request 또는 phone 6-field request만 허용한다. phone context 세 field는 all-or-none이며 current transition ID, expected group, 기존 mockExamId가 모두 일치해야 `PHONE_REJOIN REPLACEMENT`가 된다.
- phone reserve 응답과 status에는 optional continuation reason/id가 남고 attemptGroupId/mockExamId는 existing group 값을 authoritative하게 사용한다. 일반 reserve 응답과 기존 canonical idempotency digest는 변경하지 않는다.
- 핵심 MVC/security/decoder/policy/service/hash 테스트는 성공했다. `./gradlew clean test` 136개 중 비-Docker 132개는 통과했고 Docker daemon 미가동으로 Testcontainers 4개 initialization failure가 남았다.
- production flag는 계속 off다. Learning Core reader-first 적용, Lattice auth policy에 exact continuation route 추가, Docker replica-set test와 staging E2E 전에는 활성화하지 않는다.

## 2026-09-03 PHONE_REJOIN replacement discovery 계약 공백 확인

- 현재 reserve 요청은 Learning Core가 `mockExamId`를 필수로 먼저 보내며 Billing은 기존 nonterminal AttemptGroup의 `mockExamId`와 exact match한 경우에만 `REPLACEMENT`를 반환한다.
- 재가입한 새 userId의 Learning Core에는 기존 Session과 `mockExamId`가 없으므로 임의의 새 값으로 요청하면 Billing은 응답 전 `STATE_CONFLICT`로 거절한다. 따라서 문서의 phone replacement 흐름은 현재 wire 계약만으로 실행할 수 없다.
- 일반 reserve의 fail-closed 성질은 유지하고, phone rejoin에 한해 Billing이 기존 `attemptGroupId`·`mockExamId`와 명시적인 continuation reason/context를 authoritative하게 제공하는 discovery 계약이 추가로 필요하다.
- Learning Core는 유효한 phone-rejoin context를 받은 경우에만 target 명의 새 examId를 만들고 같은 AttemptGroup에 연결한다. source Session·답안·결과 owner는 변경하지 않는다.
- 구체적인 discovery route, context의 식별·만료·1회성, reserve echo field와 오류 계약은 아직 ADR에서 확정하지 않았으며 현재 구현이나 Jira는 변경하지 않았다.

## 2026-09-03 PHONE_REJOIN 상태별 제한 승계 보정

- `TrialOwnerRebindApproved`는 Billing-only delivery이며 phone proof만으로 완료된 과거 시험·답안·피드백을 Learning Core에서 이전하지 않는다. `UserMerged`만 Billing·Learning Core 양쪽에 전달한다.
- Billing은 active Reservation/PROCESSING 확인 뒤 Claim의 AttemptGroup 전체 상태를 조회한다. 이력 없음·`OPEN`·`RETAKE_AVAILABLE`은 owner CAS `APPLIED`, `GRADING`은 503 pending, `COMPLETED`는 owner/fence 불변 `NOOP` 204다.
- phone target의 재응시는 새 무료권이 아니다. 기존 consumption·attemptGroupId·mockExamId를 `REPLACEMENT`로 재사용하면서 source Session을 이전하지 않고 target의 새 key·새 examId로 처음부터 시작한다.
- `OwnerRebindService`, indexed claim 기반 AttemptGroup 조회, 상태별 unit/MVC 회귀 테스트와 replica-set Mongo integration test를 보정했다. Claim·Grant·ledger·AttemptGroup은 변경하지 않는다.
- ADR-003, PLAN-006, 통합 계약, CONTRACT_DECISIONS와 AGENTS를 같은 delivery·상태·Session 정책으로 갱신했다. Identity/Learning Core 코드와 Jira는 변경하지 않았다.
- 상태별 unit/MVC 테스트는 성공했다. `./gradlew clean test`는 123개 중 비-Docker 119개가 통과했고 Docker daemon 미가동으로 Testcontainers 4개가 initialization failure다. production flag는 계속 off다.

## 2026-09-03 PR 생성 안내가 보이지 않는 원인

- 최신 fetch 기준 HEAD `804d4eb`은 로컬 `develop`에 직접 commit됐고 `origin/develop`보다 1 commit 앞서 있어 아직 원격에 반영되지 않았다.
- PR은 source와 base가 서로 다른 원격 branch여야 한다. 현재 commit을 `develop`에 직접 push하면 `develop`을 base로 한 PR source branch가 없으므로 GitHub의 Compare & pull request 안내가 나타나지 않는다.
- 안전한 정리 순서는 현재 commit에서 새 `fix/TMI-120-phone-rejoin-policy` branch를 만든 뒤 local develop pointer를 `origin/develop`로 복원하고 새 branch를 push하여 base `develop` PR을 만드는 것이다.
- repository의 `main`은 `develop`보다 오래된 상태이므로 PR base를 GitHub 기본값에 맡기지 말고 `develop`으로 명시해야 한다.

## 2026-09-02 TMI-120 Billing owner rebind 구현

- schema v4에 `owner_rebind_inbox`, `subject_owner_rebinds`, exact index와 `BillingSubjectLink.ownerVersion/ownerUpdatedAt` reader-first CAS를 추가했다. legacy missing version은 logical 1로 읽고 첫 이전에서 version 2로 수렴한다.
- phone `TrialOwnerRebindApproved`와 Guest `UserMerged` v1을 route별 strict decode하고 canonical digest로 멱등 처리한다. event만으로 Claim·Grant·allocation·consumption은 만들지 않으며 source current link의 owner만 target으로 이전한다.
- phone projection revision/state/candidate-to-Claim 검증, Guest 최대 100 subject all-or-nothing, active Reservation/PROCESSING의 503 pending, Transaction retry·commit 재확인을 구현했다.
- owner 이전 전 active exact Session에는 120일/Claim retention 상한 fence를 만들고 AttemptGroup status consumer가 이전 source event를 그 Session의 상태 전진에만 허용한다. terminal 시 즉시 fence를 종료한다.
- 최대 1시간 cleanup worker는 due source/group/session 연결을 unset하고 24시간 초과를 저카디널리티 경보로 기록한다. 로그와 span에는 source/target/subject/Claim/Session/payload/digest를 넣지 않는다.
- lifecycle decoder, MVC/security, service, AttemptGroup fence, cleanup과 실제 HTTP W3C trace 테스트가 통과했다. replica-set Mongo 통합 테스트도 작성했으나 현재 local Docker daemon 미가동으로 실행 검증이 남아 있다.
- production owner rebind와 cleanup flag는 기본 false다. Identity event별 delivery, Learning Core `UserMerged` consumer/phone replacement, 실제 Lattice/IAM/SG와 staging E2E 전에는 활성화하지 않는다.

## 2026-09-02 TMI-120 이후 작업 순서

- 즉시 다음 작업은 Docker daemon이 가능한 환경에서 `./gradlew clean test`를 다시 실행해 owner rebind를 포함한 replica-set Testcontainers 4개를 검증하고, 실패가 있으면 수정하는 것이다.
- 전체 테스트와 최종 diff 검토가 통과하면 사용자 승인 후 Jira TMI-120을 완료 처리한다. commit·push·PR은 사용자가 수행한다.
- 다음 cross-service 구현은 Identity event별 durable delivery다. `UserMerged`는 Billing/Learning Core, phone rejoin은 Billing delivery만 독립 저장·retry한다.
- 그다음 Learning Core에 `UserMerged` owner consumer/source deny와 phone target의 기존 group replacement Session 처리를 구현한다.
- 마지막으로 schema v4 migration, Lattice IAM/SG, 두 서비스 flag를 staging에 적용해 Identity/Billing/Learning Core 순서 역전·중복·응답 유실 E2E를 통과한 뒤에만 production canary를 진행한다.

## 2026-09-02 Identity durable owner-event fan-out 인계 내용

- 현재 Identity `UserMergedOutbox`는 immutable event 값과 단일 delivery 상태가 같은 document에 있고 `UserMergedPublisher`도 endpoint 하나만 처리한다. 이를 기존 row reader-first 호환을 유지하면서 event core와 `(eventId, consumer)` delivery로 분리해야 한다.
- 신규 Guest merge부터 같은 lifecycle Mongo Transaction에서 기존 `UserMerged` core와 `BILLING`, `LEARNING_CORE` delivery 두 건을 원자 저장한다. phone 재가입은 별도 `TrialOwnerRebindApproved` core와 `BILLING` delivery 한 건만 만든다.
- delivery는 payload를 복제하지 않고 core를 참조하며 status, attempt, nextAttemptAt, lease, failure, published/dead-letter/cleanup 시각을 consumer별로 독립 관리한다. 한 consumer의 성공·실패가 다른 consumer delivery 상태를 바꾸면 안 된다.
- Billing route는 Guest `/internal/v1/owners/merge/events`, phone `/internal/v1/eligibility/trial/owner/events`다. Learning Core route는 Guest `/internal/v1/owners/merge/events`뿐이며 phone route는 없다.
- Identity→Billing은 서울 리전 Lattice SigV4, Identity→Learning Core `UserMerged`는 기존 workload JWT를 유지한다. consumer별 retry/dead-letter/circuit은 독립 관리한다.
- 기존 merge event historical backfill은 하지 않고, consumer 준비·staging E2E 전 publisher flag는 기본 false로 유지한다.

## 2026-09-02 ADR-003 retained trial owner rebind 계약 초안 작성

- `docs/adr/ADR-003-retained-trial-owner-rebind-contract.md` 검토 초안을 작성했다. 승인된 C14·PLAN-006·TMI-120 정책을 바꾸지 않고 two lifecycle wire, strict decoder, canonical digest, owner 상태 전이와 pending HTTP 계약을 실행 가능한 수준으로 고정했다.
- Billing Mongo schema v4는 `owner_rebind_inbox`, `subject_owner_rebinds`, `BillingSubjectLink.ownerVersion/ownerUpdatedAt`을 사용한다. v3 missing ownerVersion은 logical 1로 reader-first 처리하고 첫 CAS에서 version 2로 수렴하며 자동 bulk rewrite/drop은 금지한다.
- phone prerequisite는 source/target revision lower를 503 pending, exact state 불일치를 conflict, higher revision의 반대 상태를 superseded STALE로 판정한다. Guest merge는 source active retained link 최대 100건을 한 Transaction에서 all-or-nothing 이전한다.
- exact pre-rebind group/session status만 legacy source로 허용하고 terminal 또는 `min(appliedAt+120일, Claim retentionExpiresAt)`에 논리 종료한다. 최대 1시간 간격 worker가 24시간 안에 source link를 unset하며 TTL은 safety net으로만 사용한다.
- Identity 기존 `user_merged_outbox` reader-first core/delivery 전환을 포함했다. 당시 포함했던 Learning Core phone route 전제는 2026-09-03 보정으로 제거됐고 정상 replacement Session 흐름으로 대체됐다.
- PLAN-006, CONTRACT_DECISIONS와 통합 계약에서 ADR-003을 owner rebind 단일 기술 기준으로 연결했다. Jira 내용·상태, 애플리케이션 코드, Identity/Learning Core 코드와 AWS resource는 변경하지 않았다.
- 남은 gate는 ADR-003 검토 승인이다. 승인 뒤 TMI-120 Billing schema/consumer 구현을 시작하며 타 서비스 consumer와 staging 순서 역전 E2E 전 production flag는 off다.

## 2026-09-02 ADR-003 승인과 Jira TMI-120 갱신

- 사용자가 ADR-003 전체와 schema v4 collection 이름, Guest merge 100 subject 상한, 최대 1시간 cleanup worker, historical backfill/privileged mutation route 제외를 승인했다. 당시 Learning Core phone route 승인은 2026-09-03 보정으로 폐기됐다.
- ADR-003 상태를 승인으로, PLAN-006 상태를 ADR 승인·TMI-120 구현 대기로 갱신했다. Billing 구현 시작에 필요한 계약 선택은 더 남아 있지 않다.
- Jira TMI-120의 기존 목표·wire·fan-out·IAM·cleanup·완료 조건은 보존하고 ADR-003 승인 기술 기준을 description에 추가했다. 상태 `해야 할 일`, Medium, 담당자 없음과 Resolution 없음은 유지했다.
- 다음 작업은 TMI-120 Billing schema v4, lifecycle별 strict decoder, owner CAS, legacy fence와 cleanup worker 구현이다. Identity/Learning Core consumer와 실제 AWS/staging E2E는 여전히 별도 작업과 production activation gate다.

## 2026-09-02 Guest merge retained subject 100건 상한 설명

- retained subject는 시험 횟수가 아니라 source user가 current owner로 연결된 active·unexpired `BillingSubjectLink` 한 건을 뜻한다. 현재 `FREE_EXAM_ONCE` 범위에서는 정상적으로 0~1건이 예상된다.
- Guest merge 한 event는 source의 모든 retained subject를 target으로 한 Transaction에서 all-or-nothing 이전한다. 100건은 데이터 이상이나 미래의 과도한 benefit 연결 때문에 Mongo Transaction이 무제한 커지는 것을 막는 방어 상한이다.
- 100건을 초과하면 처음 100건만 부분 이전하지 않는다. 전체 이전을 중단하고 409 conflict와 invariant alert로 운영 검토를 요구한다.
- 이 값은 무료 모의고사를 100번 허용하거나 Claim을 100개 만든다는 뜻이 아니다. 향후 정상 제품 모델이 한 사용자에게 100개를 넘는 retained subject를 요구하면 ADR을 갱신해 상한과 batching 전략을 다시 정한다.

## 2026-09-02 owner rebind strict decoder 의미 설명

- strict decoder는 외부 JSON을 단순 DTO로 변환하는 parser가 아니라 route별 승인 field·type·exact 상수·식별자 형식·상호 관계를 모두 확인한 뒤에만 내부 `OwnerRebindCommand`를 만드는 계약 경계다.
- duplicate/unknown field, trailing token, scalar coercion, 잘못된 UUID casing·revision·timestamp와 source==target을 거절한다. phone route는 eventType/producer/scope/reason을 exact 확인하고 Guest route는 기존 UserMerged v1에 없는 field를 허용하지 않는다.
- malformed 형식은 400, 정상 JSON이지만 지원하지 않는 schema/event/producer/reason/scope는 422로 구분하며 검증 전 raw payload를 저장·로깅하거나 canonical digest를 계산하지 않는다.
- phone과 Guest decoder를 분리해 두 lifecycle의 의미를 섞지 않고, 검증이 끝난 공통 값만 내부 command로 수렴시켜 이후 owner CAS·멱등성 service를 재사용한다.

## 2026-09-02 ADR-003 작성 전 사용자 추가 결정 필요 여부

- 제품·보안·보존 수준의 필수 사용자 결정은 더 없다. event/route/schema, pending response, fan-out, IAM, owner CAS, active Reservation, legacy fencing과 24시간/120일 cleanup이 모두 확정됐다.
- ADR-003의 남은 collection/index 이름, schema v4 ownerVersion backfill, exact CAS query, reader-first delivery migration, Learning Core `UserMerged` route와 phone replacement, cleanup scheduler와 metric은 승인 정책 안의 기술 설계로 Codex 권장안을 작성할 수 있다.
- 보수적 기본값은 TMI-120에서 privileged HTTP repair route를 만들지 않는 것이다. 120일 hard cap 뒤 event는 자동 authorization하지 않고 alert·운영 review·원본 event 증적 확인을 거쳐 별도 future repair ADR로 처리한다.
- Billing은 production 미활성 전제를 유지한다. schema v4 startup preflight에서 legacy data/index가 예상과 다르면 자동 rewrite/drop하지 않고 fail-fast하며 별도 migration 승인을 요구한다.
- Identity merge 성공은 downstream 두 consumer의 즉시 성공을 기다리지 않는 existing transactional outbox semantics를 유지한다. `UserMerged` 두 delivery는 독립 retry/dead-letter되고 phone은 Billing delivery만 둔다.
- 따라서 사용자가 별도 운영 repair endpoint나 historical backfill을 현재 범위에 포함하길 원하지 않는 한 추가 질문 없이 ADR-003 초안을 작성할 수 있다.

## 2026-09-02 TMI-120 구현 전 마지막 ADR gate 확인

- 제품 정책, exact event/route/schema, 503/Retry-After, durable fan-out, Lattice IAM action과 legacy cleanup SLA는 모두 승인돼 선택지는 남지 않았다.
- 그러나 통합 계약은 구체적인 `UserMerged` consumer wire를 별도 ADR 확정 전 임의 구현하지 못하게 하고 현재 저장소에는 ADR-001·ADR-002만 있다. 따라서 바로 코드가 아니라 ADR-003 owner rebind contract를 먼저 작성해야 한다.
- ADR-003은 Billing owner-rebind inbox/record collection·index·schema v4 migration, ownerVersion backfill/CAS, cleanup worker와 privileged reconciliation, Identity existing outbox reader-first delivery migration, Learning Core `UserMerged` route와 phone replacement를 실행 가능한 계약으로 고정한다.
- ADR-003 승인 뒤 TMI-120 Billing reader-first consumer 구현을 시작할 수 있다. Identity fan-out과 Learning Core owner consumer는 별도 저장소/Jira 구현이며 Billing feature flag와 production activation gate는 계속 닫아 둔다.
- 이번 확인은 분석·기록만 수행했고 애플리케이션·Jira·승인 계약을 변경하지 않았다.

## 2026-09-02 owner rebind fan-out·IAM·cleanup 승인과 Jira 갱신

- 사용자가 remaining ADR 권장안을 모두 승인했다. Identity는 immutable event core와 `(eventId, consumer=BILLING|LEARNING_CORE)` unique delivery 두 건을 lifecycle Transaction에서 원자 저장하고 delivery별 lease·retry·dead-letter·published·feature flag를 독립 관리한다.
- Lattice IAM action은 `vpc-lattice-svcs:Invoke`만 사용한다. 환경별 exact service ARN, Identity task role Principal, POST와 승인 route를 함께 제한하고 wildcard·cross-environment·불필요 action을 금지한다.
- legacy sourceUserId는 related Session terminal 뒤 daily cleanup으로 24시간 안에 unset한다. terminal 미수렴 hard upper bound는 `min(rebindAppliedAt+120일, Claim retentionExpiresAt)`이고 이후 late event는 privileged reconciliation 대상이다.
- Jira `TMI-120`에 durable fan-out 구조, exact IAM, cleanup SLA/hard cap과 관련 완료 조건을 추가했다. Identity fan-out·실제 AWS resource 구현은 Billing Jira 제외 범위이면서 production 선행 gate로 유지했다.
- PLAN-006, C14, ADR-002 auth-policy example과 전체 통합 계약을 같은 값으로 갱신했다. Jira 상태 `해야 할 일`, Medium, resolution 없음, 담당자 미지정은 변경하지 않았다.
- 후속 ADR에 남은 것은 승인값 변경이 아니라 Identity existing outbox reader-first migration schema, Learning Core exact owner route와 privileged reconciliation runbook이다.

## 2026-09-02 owner rebind delivery·IAM·legacy cleanup 선택지 설명

- consumer별 durable fan-out 권장안은 immutable event core 하나와 `(eventId, consumer)` unique delivery record를 Billing/Learning Core 각각 두는 방식이다. 두 delivery는 lease·attempt·nextAttemptAt·PUBLISHED/DEAD_LETTER를 독립 관리하며 한 consumer 성공이 다른 consumer 실패를 덮지 않는다.
- Identity merge/rejoin Transaction은 event core와 필수 consumer delivery 두 건을 원자적으로 저장한다. publisher flag와 endpoint도 consumer별로 분리하고 global PUBLISHED 하나 또는 동기 순차 POST는 사용하지 않는다.
- 정확한 Lattice IAM action 권장값은 기존 ADR-002와 같은 `vpc-lattice-svcs:Invoke`다. Identity task-role identity policy는 환경별 exact Billing/Learning Core service ARN만, 각 Lattice service auth policy는 exact Identity task role Principal + POST + 승인 route만 허용한다.
- `Action:*`, `Resource:*`, wildcard principal, production↔staging service ARN 교차 허용과 `InvokeWithServiceNetworkContext` 추가는 필요하지 않다. SG는 direct task bypass를 차단하지만 route 권한의 기준은 IAM/Lattice auth policy다.
- legacy-source user 연결 cleanup 권장안은 관련 pre-rebind Session이 terminal이면 daily cleanup으로 24시간 안에 sourceUserId를 unset하고, terminal이 오지 않아도 `min(rebindAppliedAt+120일, Claim retentionExpiresAt)`을 hard upper bound로 삭제하는 방식이다.
- 120일은 Learning Core dead-letter retention 90일에 30일 안전 여유를 더하고 Billing inbox retention과 맞춘 값이다. hard cap 이후 late source event는 자동 허용하지 않고 privileged reconciliation 대상으로 보낸다. eventId/digest/outcome 비식별 멱등성 기록과 user 연결 cleanup은 분리한다.

## 2026-09-02 owner rebind exact wire 승인과 Jira TMI-120 갱신

- 사용자가 ADR 권장 초안을 승인했다. phone 재가입 event type은 `TrialOwnerRebindApproved`, route는 `POST /internal/v1/eligibility/trial/owner/events`, Guest merge route는 `POST /internal/v1/owners/merge/events`로 확정했다.
- phone schema v1은 producer identity, expected `consumerScopeId`, source/target canonical userId, `PHONE_REJOIN`, occurredAt과 1 이상 source/target binding revision을 exact field로 사용한다. raw phone·candidate·Firebase UID·email·credential은 보내지 않는다.
- active Reservation/PROCESSING 또는 projection prerequisite 미수렴은 `503 OWNER_REBIND_PENDING`과 delta-seconds `Retry-After`를 반환한다. Reservation은 남은 expiry seconds를 1~300으로 clamp하고 다른 pending은 5초다.
- 425/202/409는 temporary pending에 사용하지 않고 409는 permanent conflict에만 사용한다. Mongo/서비스 장애 503은 stable error code와 low-cardinality metric으로 pending과 구분한다.
- Jira `TMI-120` 본문에 확정 wire·field·response와 Retry-After 계산을 추가하고 기존 미확정 425/503 문구를 제거한 뒤 저장 결과를 확인했다. Jira 상태·담당자·priority·댓글은 변경하지 않았다.
- PLAN-006과 C14를 같은 exact 값으로 갱신했다. 후속 ADR에 남은 결정은 Identity consumer별 durable fan-out, exact IAM action과 legacy fence cleanup window다.

## 2026-09-02 owner rebind ADR wire 결정 근거

- ADR은 승인된 C14 owner rebind 정책을 바꾸는 문서가 아니라 Identity와 Billing이 같은 wire와 delivery·IAM·cleanup 값을 구현하도록 고정하는 단계다.
- phone 재가입 event `TrialOwnerRebindApproved`는 단순 `PhoneRejoined`보다 Billing 권리 owner 이전이 Identity에서 승인됐다는 의미가 분명하고 기존 Guest merge `UserMerged`와 구분된다.
- phone route `POST /internal/v1/eligibility/trial/owner/events`는 승인된 `/internal/v1/eligibility/{kind}/...` namespace와 하이픈 없는 경로를 유지하며 기존 `/internal/v1/eligibility/trial/events` strict schema를 섞지 않는다.
- Guest merge `UserMerged`는 별도 `POST /internal/v1/owners/merge/events`를 사용하고 기존 v1 payload 의미를 변경하지 않는다.
- `503 OWNER_REBIND_PENDING` + bounded `Retry-After`는 business prerequisite pending에 맞고 기존 5xx retry와 결합할 수 있다. 425는 TLS early-data 의미와 proxy/client 지원 때문에 채택하지 않았다.
- 409는 permanent conflict로 producer가 재시도하지 않아야 하고, 202는 Billing이 durable async worker로 event를 인수한 경우가 아니므로 pending 응답에 사용하지 않는다.

## 2026-09-02 PLAN-006 승인과 Jira TMI-120 생성

- 사용자가 PLAN-006과 D1~D5 권장안을 모두 승인했다. `CONTRACT_DECISIONS.md` C14-A에 lifecycle 분리, stable subject owner CAS, active Reservation retry, bounded legacy-source fencing과 목적 종료 후 source 연결 cleanup을 확정 계약으로 기록했다.
- phone 재가입은 기존 Guest→Member `UserMerged` v1을 재사용하지 않고 Identity의 별도 source→target 승인 event를 사용한다. lifecycle별 decoder는 분리하고 검증 뒤 내부 공통 `OwnerRebindCommand`로 수렴한다.
- Jira `TMI-120` `[Billing] Retained trial owner rebind consumer 및 Transaction 구현`을 TMI `작업`, 기본 Medium, `해야 할 일`, resolution 없음으로 생성하고 저장된 제목·본문·상태를 재확인했다.
- Jira에는 stable subject 불변식, active Reservation pending, late exact Session fencing, schema v4/index, Mongo retry·concurrency, workload security/trace/privacy와 cross-service production gate를 완료 조건으로 포함했다.
- phone event·route·field와 503/Retry-After는 확정됐다. Identity consumer별 durable fan-out, exact IAM action과 cleanup window를 후속 cross-service ADR로 확정한 뒤 reader-first 구현을 시작한다.
- Jira 댓글·담당자·상태 전환은 수행하지 않았고 애플리케이션 코드는 변경하지 않았다.

## 2026-09-02 PLAN-006 decoder와 legacy-source fencing 설명

- lifecycle별 strict decoder는 기존 Guest→Member `UserMerged`와 향후 phone 재가입 승인 event의 서로 다른 wire schema·의미를 각 입구에서 엄격히 검증한 뒤, Billing 내부 공통 `OwnerRebindCommand`로 정규화하는 구조다. event 의미를 재해석하거나 unknown field·coercion을 허용하는 generic decoder가 아니다.
- `OwnerRebindCommand`는 검증 완료된 eventId, source/target userId, lifecycle reason, occurredAt과 digest 같은 공통 처리 정보만 전달하며 실제 owner CAS·Reservation pending·멱등성 로직은 하나의 service에서 재사용한다.
- bounded legacy-source fencing은 owner rebind 전에 Learning Core outbox에 source userId로 이미 저장된 exact AttemptGroup/Session status event가 Billing owner 전환 뒤 늦게 도착해 유실되는 것을 막는다.
- 이는 source 사용자를 새 owner나 actor로 다시 허용하는 기능이 아니다. 인증된 Learning Core workload의 rebind 이전 exact group/session event만 GRADING·terminal 수렴 목적으로 제한적으로 허용하며 새 reserve·replacement·다른 Session은 차단한다.
- fence는 exact subject/group/session, rebind 시각과 허용 transition으로 제한하고 관련 Session terminal 또는 승인 retry window 종료 후 source 연결을 삭제한다.

## 2026-09-01 PLAN-006 retained trial owner rebind 초안

- `docs/plans/PLAN-006-retained-trial-owner-rebind.md`를 새로 작성했다. 다음 Billing vertical slice는 새 Claim·Grant·consumption을 만들지 않고 stable `subjectRefId`의 current owner mapping만 source→target으로 변경하는 작업이다.
- Identity의 기존 `UserMerged` v1은 Guest→Member merge 전용으로 유지하고 phone 재가입에 재사용하지 않는다. phone 재가입은 Identity가 source→target 관계를 명시적으로 승인하는 `TrialOwnerRebindApproved` schema v1과 분리 route를 사용한다.
- 권장 정책은 lifecycle별 decoder + 공통 owner-rebind domain, active RESERVED/PROCESSING 종료까지 최대 5분 retry, pre-rebind exact Session의 source status event를 terminal 수렴 목적으로만 한시 허용, source 연결은 필요 기간 뒤 cleanup하는 방식이다.
- Claim/Grant/ledger/Reservation/AttemptGroup/Session은 stable subjectRef를 사용하므로 rewrite하지 않는다. `BillingSubjectLink.userId` expected-owner CAS와 owner version, event inbox/rebind record, schema v4와 replica-set concurrency test가 Billing 구현 핵심이다.
- Identity의 current UserMerged publisher는 Learning Core 전용 단일 delivery이고 Learning Core에도 consumer 구현이 없다. Identity durable fan-out·Learning Core ownership migration·cross-service E2E 전에는 Billing PLAN 완료만으로 production owner rebind를 활성화하지 않는다.
- D1~D5, cross-service wire ADR와 privacy retention을 승인한 뒤 Jira 생성·구현으로 진행한다. 이번 turn은 계획 문서와 작업 기록만 변경했고 애플리케이션·계약 기준·Jira를 변경하지 않았다.

## 2026-09-01 Learning Core TMI-118 보완 재검토

- 이전 검토에서 지적한 Summary duplicate-key Transaction blocker는 commit `4781723`, PR #26 merge `4f9e74c`로 Learning Core `develop`과 `origin/develop`에 반영됐다.
- `AttemptGroupSummaryCompletionService`는 Transaction 안에서 `DuplicateKeyException`을 삼키지 않는다. 실패한 Transaction을 종료한 뒤 바깥 bounded loop가 전체 작업을 새 Transaction으로 재시도하고, 기존 Summary의 deterministic identity를 검증한 뒤 Job·Session·outbox 수렴을 계속한다.
- Learning Core 현재 develop에서 `./gradlew clean test`를 새로 실행해 총 444개, skipped/failures/errors 0과 `BUILD SUCCESSFUL`을 확인했다. 기존 release blocker는 해소된 것으로 판정한다.
- 새 테스트는 duplicate insert rollback 후 whole-unit retry, unknown commit result retry, 기존 Summary identity conflict와 coordinator terminal-slot duplicate 전파를 검증한다. 다만 실제 replica-set, multi-instance lease, 실제 SigV4·cross-service trace 검증은 mock 단위 테스트가 아니라 staging/release gate로 남아 있다.
- 다음 제품 vertical slice는 Billing owner rebind가 맞다. 단, Identity의 현재 `UserMerged` v1은 ACTIVE GUEST source→기존 MEMBER target canonical merge 계약이며 탈퇴 후 같은 전화번호 재가입 이벤트가 아니다. Billing ADR에서 guest merge와 phone 재가입의 trigger를 구분하고, 기존 eligibility projection 기반 lazy rebind 또는 전용 lifecycle event 필요성을 먼저 확정해야 한다.

## 2026-09-01 Learning Core TMI-118 검토와 다음 작업 판정

- Learning Core `TMI-118`은 commit `63d0f7d`, PR #25 merge `c00d872`로 `develop`과 `origin/develop`에 반영됐고 Jira status/resolution도 `완료`다. 현재 develop에서 `./gradlew clean test` 439개가 성공했다.
- 그러나 신규 AttemptGroup 전용 테스트는 evidence 2개, publisher 3개, coordinator 1개뿐이며 replica-set Mongo Transaction/rollback·concurrency, lease reclaim/half-open 경쟁, 실제 SigV4·trace·privacy 계약을 직접 검증하지 않는다. Jira 완료 조건 전체가 테스트로 입증된 상태는 아니다.
- `AttemptGroupSummaryCompletionService`가 Mongo Transaction 내부 `insert`의 `DuplicateKeyException`을 잡고 같은 Transaction을 계속 사용한다. MongoDB는 duplicate key로 Transaction을 abort하므로 replay에서 이후 Summary Job·Session·outbox 수렴이 실패할 수 있는 release blocker다.
- 따라서 즉시 다음 작업은 Learning Core TMI-118 안정화 수정과 replica-set integration/transport contract test 보강이다. 이것이 끝난 뒤 다음 제품 vertical slice는 Billing의 탈퇴·재가입 `UserMerged` retained subject owner rebind가 맞다.
- Billing에는 UserMerged consumer와 owner-transfer Transaction이 없고 통합 계약도 구체 wire를 별도 ADR 전까지 금지한다. owner rebind Jira도 아직 없으므로 Identity schema·Billing owner 필드/index·active Reservation/AttemptGroup 충돌 정책을 조사해 계획과 ADR을 먼저 확정해야 한다.
- TMI-118 production 활성화는 별개로 Billing consumer 배포·flag, 양쪽 Mongo replica-set/index, Lattice/IAM/SG, publisher idle→writer canary와 상태·오류 E2E gate가 남아 있다.

## 2026-08-31 Billing AttemptGroup 업무 span 보완

- Learning Core 전달사항을 대조한 결과 기존 production Controller에는 명시적인 `attempt_group_event_consume` 업무 span이 없고 기존 첫 trace test가 test 내부에서 span을 수동 생성한 사실을 확인했다.
- 실제 Controller가 payload 크기 확인 뒤 strict decode부터 service의 멱등성·Mongo 처리까지 `attempt_group_event_consume` span으로 감싸도록 `AttemptGroupEventTracing`을 추가했다. Micrometer에 INTERNAL enum이 없으므로 kind를 지정하지 않는 OpenTelemetry 기본 INTERNAL span으로 생성하며 정상·RuntimeException·Error 모두 종료한다.
- embedded Tomcat 실제 HTTP 요청과 capturing SpanProcessor로 inbound traceId 유지, HTTP SERVER와 consume의 다른 spanId 및 descendant 관계, 정확한 span 이름·INTERNAL kind, decoder/service scope, baggage 미전파와 민감 attribute 부재를 검증했다. Spring Security INTERNAL span이 중간에 존재할 수 있다.
- 외부 event JSON, endpoint와 HTTP status, 기존 구조화 로그·metric은 변경하지 않았다. 선택 제안인 `trace_context_missing` metric rename은 호환성 영향을 고려해 이번에는 수행하지 않았다.
- `./gradlew clean test` 전체 138개 테스트가 성공했다. Jira `TMI-117`은 완료 상태를 유지하고 댓글·상태를 변경하지 않았다.

## 2026-08-31 Learning Core trace 연동 전달사항 정리

- `docs/contracts/LEARNING_CORE_ATTEMPT_GROUP_TRACE_HANDOFF.md`에 Learning Core outbox publisher와 Billing consumer가 공유할 W3C Trace Context, 구조화 로그, metric cardinality와 privacy 계약을 복사 가능한 형태로 정리했다.
- 같은 `traceId` 안에서 origin/publisher/consumer가 서로 다른 `spanId`를 가져야 하며, 저장된 traceparent를 그대로 replay하지 않고 publisher span을 만든 뒤 해당 context를 HTTP header에 inject하도록 명시했다.
- 공통 log field는 service/operation/outcome/traceId/eventId/durationMs이며 Billing만 eventAgeMs를 추가한다. baggage, 사용자·group·session 식별자, payload, digest와 credential은 log/trace에서 제외한다.
- 이는 전달 문서 정리이며 Learning Core 코드·설정과 Billing 애플리케이션 동작은 변경하지 않았다. publisher outcome allowlist는 Learning Core 구현 PLAN에서 최종 고정해야 한다.

## 2026-08-31 Jira TMI-117 완료

- 사용자 승인에 따라 Jira `TMI-117`을 전환 ID `41`로 `해야 할 일`에서 `완료`로 변경했다.
- 재조회 결과 status와 resolution은 모두 `완료`이며 resolution 시각은 2026-08-31 17:30:03 KST다. Jira 댓글·담당자·본문은 변경하지 않았다.
- Billing 코드와 기능 플래그는 추가 변경하지 않았다. 실제 Learning Core outbox/publisher, Lattice/IAM/SG와 staging E2E는 후속 production gate로 유지한다.

## 2026-08-31 TMI-117 변경 파일 역할 검토

- 사용자의 요청에 따라 TMI-117에서 추가·수정된 API, decoder, 상태 처리, inbox, repository, 관측성, 보안, 설정과 테스트 파일의 책임을 실제 코드 기준으로 다시 분류했다.
- 파일 수가 늘어난 이유는 HTTP 수신, strict 계약 검증, Mongo Transaction/CAS 상태 전이, 오류 계약, metric/trace와 계층별 테스트를 분리했기 때문이다. 검토 과정에서 애플리케이션 동작이나 계약은 변경하지 않았다.
- 정상 no-op 결과는 `APPLIED/DUPLICATE/STALE`로 204 처리하고, `CONFLICT/PROJECTION_NOT_READY`는 각각 409/503 예외 계약으로 분리된 현재 구현을 확인했다.

## 2026-08-31 TMI-117 PLAN-005 AttemptGroup event consumer 구현 완료

- `POST /internal/v1/attempt-group-events`와 16 KiB strict decoder, canonical SHA-256 digest, target별 evidence/failureCode 검증을 구현했다. endpoint flag는 기본 false이며 TEST mode에서는 Learning Core workload role만 허용한다.
- 동일 `inbound_event_inbox` collection에 `_class`와 user/group/session/evidence/failureCode 없이 최소 document를 저장한다. global eventId·120일 TTL과 Identity partial revision index를 그대로 사용하며 cross-producer same eventId는 digest가 같아도 conflict로 고정했다.
- active Session/group/subject link fencing과 `OPEN/GRADING → GRADING/COMPLETED/RETAKE_AVAILABLE` 단방향 전이를 구현했다. terminal 전이는 AttemptGroup·AttemptSession·inbox를 하나의 Mongo Transaction과 expected-version CAS로 반영한다.
- RETAKE_AVAILABLE은 AttemptSession만 FAILED로 닫고 Claim·Grant·allocation·ledger consumption을 변경하지 않는다. retention으로 subject link가 없을 때 COMPLETED만 익명 audit close를 허용하고 GRADING/RETAKE는 STALE 처리한다.
- Micrometer Tracing OpenTelemetry bridge와 W3C-only ContextPropagators를 추가하고 baggage를 비활성화했다. 구조화 로그에는 service/operation/outcome/traceId/eventId/durationMs/eventAgeMs만 기록하며 식별자와 payload를 제외한다.
- decoder·service·API/security·metric/log/privacy·W3C HTTP propagation과 replica-set inbox/상태/동시성 테스트를 추가했다. `./gradlew clean test` 전체 137개 테스트가 성공했다.
- 실제 Learning Core outbox/publisher, trace exporter/backend, Lattice/IAM/SG와 staging E2E는 구현하지 않았으며 production caller gate를 유지한다. 이후 사용자 승인으로 Jira는 완료 처리했으며 댓글 추가, commit·push는 수행하지 않았다.

## 2026-08-31 PLAN-005 승인 및 Jira TMI-117 생성

- 사용자가 PLAN-005를 승인해 Jira `TMI-117` — `[Billing] AttemptGroup status event consumer 구현`을 `작업` 유형으로 생성했다. 상태는 `해야 할 일`, 담당자는 미지정이다.
- Jira 본문에 strict decoder/digest, shared inbox, active Session fencing, group/session Transaction·CAS, 204/400/409/422/503, approved failureCode, trace/log/metric과 replica-set 테스트 완료 조건을 반영했다.
- Learning Core outbox/publisher, 실제 Lattice/IAM/SG, staging E2E와 owner rebind는 제외 범위와 후속 gate로 유지했다.
- PLAN-005 상태는 사용자 승인·Jira 생성 완료·구현 요청 대기로 갱신했다. 애플리케이션 구현과 Jira 상태 전환은 아직 수행하지 않았다.

## 2026-08-31 PLAN-005 event 처리 결과 분류 설명

- `APPLIED`, `DUPLICATE`, `STALE`, `CONFLICT`, `PROJECTION_NOT_READY`는 AttemptGroup의 업무 상태가 아니라 Billing consumer가 수신 event 한 건을 어떻게 처리했는지 나타내는 내부 outcome이다.
- APPLIED는 새 유효 event를 Transaction으로 반영한 경우, DUPLICATE는 같은 eventId·digest가 이미 commit된 경우, STALE은 새 event지만 이전 Session·이미 닫힌 상태·동일 상태 no-op이라 현재 projection을 바꾸지 않는 경우다. 세 경우 모두 Learning Core가 재전송을 끝낼 수 있도록 204를 반환한다.
- CONFLICT는 같은 eventId의 내용 변조 또는 존재하는 group/session/subject 관계 충돌이므로 409와 운영 조사 대상이다. PROJECTION_NOT_READY는 정상 순서상 group/session이 아직 보이지 않는 일시 상태이므로 inbox에 저장하지 않고 503과 Retry-After 5초로 같은 event 재전송을 요구한다.

## 2026-08-31 PLAN-005 AttemptGroup status event consumer 계획서 작성

- `docs/plans/PLAN-005-attempt-group-status-event-consumer.md`를 작성했다. 작성 당시에는 구현 승인 대기·Jira 미생성이었고, 현재는 위 기록처럼 승인 및 `TMI-117` 생성이 완료됐다.
- 범위는 Billing `POST /internal/v1/attempt-group-events`, strict decoder/digest, shared inbox eventId 멱등성, active Session fencing, group/session Transaction·CAS, stable 204/400/409/422/503, trace·duration/event-age 관측성과 replica-set concurrency test다.
- 기존 Identity inbox entity를 이동하지 않고 같은 `inbound_event_inbox` collection에 AttemptGroup 전용 최소 view를 추가한다. global unique eventId·120일 TTL을 재사용하고 Identity revision partial index는 유지하므로 schema v3와 기존 index를 변경하지 않는다.
- approved 1A·2A·3A·4A를 완료 조건으로 고정했다. RETAKE_AVAILABLE은 새 Claim/Grant/consumption이 아니며 COMPLETED는 불가역이다.
- Learning Core outbox/publisher, 실제 Lattice/IAM/SG와 staging E2E는 이번 Billing 계획의 제외 범위·후속 gate다. consumer를 먼저 배포한 뒤 publisher 별도 PLAN/Jira를 진행한다.

## 2026-08-31 Learning Core TMI-116 수정·merge 재검증 완료

- Learning Core 수정 commit `c3e3c82`와 merge commit `d95d18b`가 local·`origin/develop`에 반영됐다.
- operation-first 복구로 `SESSION_COMMITTED + ENTITLEMENT_CONFIRMING` same-key 요청이 confirm/status 상태 머신을 다시 실행한다. operation이 purge된 경우에만 durable Session fallback을 사용한다.
- Mongo transient/unknown commit 결과는 shared reservation을 cancel하지 않고 operation·Session 관측 결과와 same-key retry로 수렴한다. 확정적인 local `IllegalStateException`이고 operation·Session 전진이 모두 관측되지 않을 때만 cancel 보상을 수행한다.
- Billing success decoder는 scalar/date/enum coercion과 missing required field를 거절하며 confirm `attemptGroupStatus=OPEN`·필수 terminal timestamp, cancel/status의 조건부 timestamp를 검증한다.
- 최신 Learning Core `develop`에서 `./gradlew clean test`가 성공했고 앞서 보고한 세 finding은 해소됐다. 검토 범위에서 새 merge 차단 결함은 확인되지 않았다.
- production 활성화 전 실제 Mongo replica-set transient/unknown commit failure injection, Lattice/IAM/SG와 INITIAL·REPLACEMENT staging E2E gate는 계속 남아 있다.

## 2026-08-31 다음 개발 작업 — AttemptGroup 상태 연동

- TMI-116이 `attemptGroupId`를 Learning Core ExamSession에 저장했으므로 다음 vertical slice는 시험 결과 lifecycle을 Billing AttemptGroup에 전달하는 상태 event 연동이다.
- 상태는 `OPEN → GRADING → COMPLETED` 또는 최종 결과 생성 실패 시 `RETAKE_AVAILABLE`이다. `COMPLETED`는 필수 feedback·유효 score·Summary가 모두 조회 가능할 때만 허용하고 다시 열지 않는다.
- `RETAKE_AVAILABLE`은 무료권 복원이나 새 Grant 발급이 아니다. 기존 consumption·AttemptGroup·mockExamId를 유지한 REPLACEMENT Session만 추가 차감 없이 허용한다.
- 안전한 개발·배포 순서는 event schema와 전이를 최종 동결한 뒤 Billing consumer를 먼저 구현·배포하고, Learning Core가 동일 local Transaction에서 outbox를 적재한 뒤 SigV4 publisher를 활성화하는 순서다.
- 별도 운영 gate인 Mongo replica-set failure injection, Lattice/IAM/SG, INITIAL·REPLACEMENT staging E2E는 이 개발 작업과 구분하되 production flag 활성화 전에 모두 완료해야 한다.

## 2026-08-31 AttemptGroup 상태 연동 미확정 정책 선택지

- 이미 확정된 것은 `COMPLETED` evidence 조건, sequence 없는 at-least-once event, eventId/digest 멱등성, active Session fencing과 stale Session 204 처리다.
- 추가 선택이 필요한 항목은 failureCode의 세분화, 같은 active Session의 out-of-order event 수렴, target projection 누락 분류, Learning Core outbox의 재시도·보존 정책이다.
- 권장 조합은 `1A 고정 저카디널리티 failureCode`, `2A 상태 전이표+Session fencing`, `3A 503/204/409 원인별 분리`, `4A pending 무TTL+지수 backoff와 delivered/dead-letter 기한 보존`이다.
- 사용자가 `1A·2A·3A·4A`를 모두 승인했다. CONTRACT_DECISIONS, ADR-001과 통합 계약에 failureCode allowlist, 상태/Session fencing, 503·204·409 분류, durable outbox와 trace 정책을 반영했다.
- traceId는 failureCode를 대체하지 않고 상세 조사 경로를 제공한다. 현재 Learning Core의 requestId/Sentry만으로는 Billing까지 연결되지 않으므로 W3C traceparent propagation과 Billing tracing 기반을 구현 계획에 포함한다.
- 사용자 추가 승인으로 AttemptGroup publish/consume 구조화 로그에 서비스 식별 `service`, 고정 `operation`·`outcome`, 단계 처리 시간 `durationMs`를 포함한다. Billing consume에는 생성부터 수신까지 지연 `eventAgeMs`도 기록해 서비스 내부 지연과 outbox/network/retry 지연을 분리한다.

## 2026-08-31 상태 전이표·Session fencing 방식 상세 설명

- Billing은 event의 `occurredAt` 최신 여부로 덮어쓰지 않고 먼저 event `sessionId`가 AttemptGroup의 `activeSessionId`와 같고 해당 AttemptSession이 `ACTIVE`인지 확인한다. 다르면 이전·폐기 Session event이므로 204 stale 처리한다.
- fencing을 통과한 뒤 현재 group status와 targetStatus의 허용 전이표를 적용한다. `OPEN→GRADING/COMPLETED/RETAKE_AVAILABLE`, `GRADING→COMPLETED/RETAKE_AVAILABLE`만 전진이며 같은 상태는 duplicate no-op, COMPLETED 이후는 재개방하지 않는다.
- RETAKE_AVAILABLE 전환은 해당 AttemptSession을 FAILED terminal로 닫는다. 이후 같은 Session의 늦은 event는 stale이고, 새 REPLACEMENT confirm이 새로운 activeSessionId와 OPEN을 만든 뒤 새 Session event만 허용한다.
- group/session/inbox 변경은 expected version CAS를 포함한 하나의 Mongo Transaction으로 처리해 동시에 도착한 terminal event 중 하나만 승리하도록 한다.

## 2026-08-31 revision 없이 안전하다는 의미 보완

- revision 없이 보장하는 것은 producer의 정확한 event 생성 순서 복원이 아니라 duplicate 부작용 방지, 과거 Session 격리, 상태 역행 방지와 동시 write 단일 승자다.
- eventId/digest는 같은 event 재전송을 막고, activeSessionId/AttemptSession state는 재응시 세대를 구분하며, 단방향 상태 전이표는 늦은 GRADING이 COMPLETED를 되돌리지 못하게 하고, Mongo document version CAS는 동시 update 중 하나만 commit하게 한다.
- 같은 active Session에서 서로 모순되는 COMPLETED와 RETAKE_AVAILABLE을 모두 발행한 경우 revision 없는 consumer는 어느 것이 producer 기준 최신·정답인지 알 수 없다. 이는 Learning Core가 둘 중 하나만 durable terminal event로 생성해야 한다는 producer 불변식과 outbox Transaction으로 막아야 한다.
- 따라서 event revision은 정상 상태 머신에서는 불필요하지만 모순 terminal event의 우선순위를 consumer가 판정해야 한다면 추가해야 한다.

## 2026-08-31 Billing merge 및 Learning Core TMI-116 구현 리뷰

- Billing PR #3의 merge commit `39e424d`가 local·`origin/develop`에 반영됐고 BenefitDefinition 구현 commit은 `18f1265`다. 현재 Billing 전체 테스트는 성공했다.
- Learning Core `feat/TMI-116-billing-reservation-exam-saga`는 commit `9241a39`로 구현·push됐고 전체 테스트도 성공했다. 공개 API shape, 기본 off flag, Billing URL/DTO, 서울 리전 `vpc-lattice-svcs` SigV4의 정상 흐름은 Billing 계약과 일치한다.
- merge 전 수정이 필요한 핵심 결함이 있다. durable `ENTITLEMENT_CONFIRMING` Session을 operation보다 먼저 replay하면 `SESSION_COMMITTED`의 confirm/status 재조정을 영구 건너뛴다. Mongo commit의 transient/unknown 실패를 일반 local failure로 분류해 동시 same-key winner가 commit 중인데 shared reservation을 cancel할 수 있다.
- Billing 성공 응답 decoder는 unknown/duplicate/trailing field를 막지만 scalar coercion과 missing required field를 완전히 차단하지 않고, confirm의 `attemptGroupStatus=OPEN`·`confirmedAt` exact 검증도 빠져 있다.
- TMI-116 branch의 단일 commit에는 saga 외에도 비용 추정·10초 챌린지·frontend handoff 등 관련 없는 대형 문서 변경이 함께 포함돼 있으므로 PR 범위를 분리하거나 의도된 포함인지 확인해야 한다.
- production caller gate는 유지한다. 위 코드 결함 수정과 회귀 테스트, replica-set failure injection, 실제 Lattice/IAM/SG 및 INITIAL·REPLACEMENT staging E2E 전에는 flag를 활성화하지 않는다.

## 2026-08-28 TMI-115 PLAN-004 BenefitDefinition foundation 구현 완료

- `benefit_definitions` collection과 FREE_EXAM_ONCE의 UNIT·EXAM_ATTEMPT·1-unit·policy-v1 seed를 구현했다. seed 재실행은 no-op이고 누락 시 생성하며, 기존 policy drift는 자동 수정하지 않고 startup을 실패시킨다.
- TrialClaim·TrialCandidateAlias·EntitlementGrant의 저장 field와 repository query를 `benefitCode`로 통일했다. 최초 INITIAL reserve는 BenefitCatalog를 확인해 definition의 unit 수로 Claim·Grant를 lazy 생성하고, 기존 Claim도 Claim–Grant–Definition code와 수량이 일치해야 사용한다.
- Mongo schema를 v3로 올리고 alias unique index와 grant source unique index key를 `benefitCode`로 변경했다. v2 `benefitType`·`grantType` document 또는 이름이 같은 legacy index가 있으면 자동 변경하지 않고 fail-fast한다.
- definition 누락·inactive·Grant reference 불일치는 같은 reserve Transaction을 rollback하고 `503 BILLING_TEMPORARILY_UNAVAILABLE`로 수렴한다. eligibility event만으로 Claim/Grant를 만들지 않으며 기존 confirm·cancel·expiry·replacement wire 동작은 유지했다.
- ADR-001과 AGENTS의 현재 구현 기준을 PLAN-004/TMI-115 및 schema v3 계약으로 갱신했다. PREMIUM_SUBSCRIPTION·SubscriptionEntitlement·Store·public API·AttemptGroup event·owner rebind·타 서비스/AWS는 추가하지 않았다.
- `./gradlew clean test` 전체 96개 테스트가 성공했고 `git diff --check`도 통과했다. Jira는 별도 상태 변경 승인 전까지 `해야 할 일`로 유지한다.

## 2026-08-28 PLAN-004 Jira 작업 생성

- TMI 프로젝트에 `TMI-115` — `[Billing] BenefitDefinition foundation 구현`을 `작업` 유형으로 생성했다. 상태는 `해야 할 일`, 담당자는 미지정이다.
- Jira 본문에 `benefit_definitions`와 FREE_EXAM_ONCE seed, exact drift fail-fast, Claim·alias·Grant의 `benefitCode` 전환, Mongo v3/index 보정, BenefitCatalog 기반 lazy 발급과 Testcontainers 회귀 완료 조건을 반영했다.
- PREMIUM_SUBSCRIPTION·SubscriptionEntitlement·Store lifecycle·구독 Reservation 분기, eager TrialClaim, public API, AttemptGroup event·owner rebind와 타 서비스/AWS 변경은 제외 범위로 명시했다.
- PLAN-004 상태를 사용자 승인·Jira 생성·구현 전으로 갱신했다. 애플리케이션 코드와 Mongo schema 구현은 아직 시작하지 않았다.
- production caller는 PLAN-004만으로 활성화하지 않고 후속 AttemptGroup/Learning Core saga와 Lattice staging E2E gate를 유지한다.

## 2026-08-28 PLAN-005 초안 철회 — Learning Core 작업으로 범위 정정

- 사용자가 구현 대상은 Billing이 아니라 Learning Core라고 정정했다. 잘못 작성한 Billing `PLAN-005-attempt-group-status-event-consumer.md` 초안은 삭제했고 Billing consumer를 현재 작업으로 진행하지 않는다.
- Billing 애플리케이션·계약·Jira는 변경하지 않았다. 기존 `TMI-115` PLAN-004 상태도 그대로 유지한다.
- Learning Core에서는 AttemptGroup event publisher보다 먼저 필요한 `Idempotency-Key + reserve → Session commit → confirm` 생성 saga 계획을 별도 저장소 문서로 작성한다.
- 아래 PLAN-005 작성 기록은 범위 오해가 있었던 이력이며 활성 계획이 아니다.

## 2026-08-28 PLAN-005 AttemptGroup 상태 event consumer 계획서 작성

- `docs/plans/PLAN-005-attempt-group-status-event-consumer.md` 초안을 작성했다. 상태는 사용자 승인 대기이며 Jira는 아직 생성하지 않았다.
- endpoint는 `POST /internal/v1/attempt-group-events`, schema v1과 16 KiB strict decode, canonical digest, shared inbox 멱등성, active Session fencing, group/session CAS와 단일 Mongo Transaction을 구현 범위로 고정했다.
- `AttemptGroup.subjectRefId`는 실제 `userId`가 아니므로 직접 비교하지 않는다. active·unexpired `BillingSubjectLink`로 owner를 resolve하고 group/session/link의 subject·claim 관계와 event userId를 검증한다. expired/anonymized link는 stale 처리하고 mapping을 복원하지 않는다.
- 유효 terminal evidence는 `GRADING` event가 누락돼도 `OPEN`에서 직접 전진한다. `COMPLETED`와 `RETAKE_AVAILABLE` 확정 후 역행 event, abandoned/old Session event는 `STALE` inbox와 204로 수렴한다.
- group/session missing은 `503 ATTEMPT_PROJECTION_NOT_READY`와 `Retry-After: 5`, group-session-owner 구조 충돌은 `409 EVENT_TARGET_CONFLICT` non-retryable로 분리했다. malformed/conflict/missing은 inbox에 저장하지 않는다.
- RETAKE failureCode 초안 allowlist는 `REQUIRED_RESULTS_UNAVAILABLE`, `SUMMARY_UNAVAILABLE`, `GRADING_DEADLINE_EXCEEDED`, `RESULT_INTEGRITY_VIOLATION`이다. provider 원문과 자유 형식 사유는 받거나 저장하지 않는다.
- PLAN-005는 `TMI-113` 완료를 선행 조건으로 하며 PLAN-004 BenefitDefinition과 독립적이다. 이번 사용자 우선순위에 따라 PLAN-005를 먼저 구현할 수 있지만 PLAN-004/TMI-115 상태 자체는 변경하지 않았다.
- Learning Core publisher/outbox, owner rebind, Reservation saga·reconciliation, 실제 Lattice/AWS와 결제는 제외했다. 이번 턴에는 계획서만 작성했고 애플리케이션·ADR·Jira는 변경하지 않았다.

## 2026-08-28 PLAN-004 BenefitDefinition foundation 계획서 작성

- `docs/plans/PLAN-004-benefit-definition-foundation.md`를 작성하고 사용자 승인을 거쳐 Jira `TMI-115`로 구현 범위를 고정했다.
- 범위는 versioned `benefit_definitions` catalog, stable FREE_EXAM_ONCE code와 exact seed/drift validation, Claim·alias·Grant의 benefitCode reference, schema v3/index 보정과 reserve/lifecycle 회귀 테스트다.
- `benefitCode`를 Mongo `_id`로 사용해 built-in uniqueness를 사용하며 별도 중복 secondary unique index는 만들지 않는다. 기존 `benefitType`·`grantType` field는 미배포 v3에서 `benefitCode`로 통일한다.
- 현재 TrialEligibility → 최초 reserve의 lazy TrialClaim·1-unit Grant → hold → Session commit 뒤 confirm 소비 흐름은 변경하지 않는다. definition 누락·inactive·drift는 부분 지급 없이 retryable 503으로 fail-closed한다.
- PREMIUM_SUBSCRIPTION·SubscriptionEntitlement·Store/renewal·구독 Reservation 분기, TrialClaim eager creation, public 상품 API와 AttemptGroup event는 제외했다.
- 다음 단계는 Jira 완료 조건을 기준으로 PLAN-004를 구현하는 것이다. 완료 후 AttemptGroup event consumer → owner rebind → Learning Core saga/Lattice staging E2E 순서로 돌아간다.

## 2026-08-28 BenefitDefinition·무료 Grant·구독 구조 승인과 즉시 영향

- 사용자가 `BenefitDefinition → TrialClaim/EntitlementGrant 또는 SubscriptionEntitlement → Reservation → AttemptGroup` 구조를 장기 Billing 계약으로 승인해 CONTRACT_DECISIONS 1C에 반영했다.
- 현재 구현에 이미 있는 것은 TrialClaim, 무료 1-unit EntitlementGrant, Reservation, AttemptGroup과 ledger/allocation이다. SubscriptionEntitlement는 후속 구독 기능이라 지금 만들지 않는다.
- 현재 즉시 빠진 요소는 BenefitDefinition이다. FREE_EXAM_ONCE가 Claim·alias·Grant와 repository에 문자열로 하드코딩돼 있으므로 실제 catalog 적용 시 definition collection/seed, stable benefitCode, unique index와 startup validation, Claim/Grant reference와 contract test가 필요하다.
- Reservation의 INITIAL/REPLACEMENT는 attempt 관계이므로 유지한다. 구독 구현 때 별도 authorization source type/reference를 추가하고, 무료는 allocation hold·consume, 구독은 active 기간 확인·usage audit로 분기해야 한다. 지금 무료 DTO/collection을 미리 확장하지 않는다.
- 권장 실행 순서는 작은 BenefitDefinition foundation vertical slice를 별도 PLAN/Jira로 구현한 뒤 AttemptGroup event consumer를 진행하거나, AttemptGroup consumer가 catalog와 독립적이므로 먼저 완료하고 구독 착수 직전에 foundation을 구현하는 두 방식이다. 현재 미배포이고 하드코딩 제거 효과가 있어 foundation을 먼저 하는 안을 권장한다.
- 이 작업에서는 계약·상태 문서만 수정했고 애플리케이션 코드·Mongo schema·Jira는 변경하지 않았다. BenefitDefinition 구현에는 별도 계획과 Jira 승인이 필요하다.

## 2026-08-28 TrialEligibility 역할 확인

- TrialEligibility는 Identity의 verified/revoked phone eligibility event를 Billing이 로컬에 반영한 사용자별 현재 자격 projection이다. raw phone은 저장하지 않고 consumerScopeId, userId, bindingRevision, VERIFIED/REVOKED 상태, verified phone에서 파생된 opaque candidate와 event/digest high-water만 저장한다.
- 이 record는 무료권·Claim·Grant나 소비 이력이 아니다. 최초 reserve가 현재 VERIFIED이고 candidate가 존재하는지 확인한 뒤에만 TrialClaim/Grant 생성 또는 기존 Claim 연결을 진행하도록 하는 fail-closed 판단 근거다.
- revoke event가 오면 state를 REVOKED로 바꾸고 candidate를 제거하되 revision high-water tombstone을 유지해 과거 verified event가 상태를 되돌리지 못하게 한다. revoke만으로 기존 TrialClaim·confirmed consumption을 삭제하거나 복원하지 않는다.

## 2026-08-28 BenefitDefinition·Grant·TrialClaim·Ledger 역할 정리

- BenefitDefinition은 “무슨 응시권인가”를 정의하는 catalog/policy다. immutable benefitCode, 표시 이름, one-time/subscription 유형, 소비 방식과 policy version을 가진다.
- EntitlementGrant는 “누가 어떤 경로로 실제 응시권을 받았는가”를 나타내는 발급 instance/batch다. 무료 MVP에서는 subject가 가진 FREE_EXAM_ONCE 1 unit과 available/held/consumed projection이다.
- TrialClaim은 일반 사용 이력이 아니라 `(verified-phone candidate, FREE_EXAM_ONCE)`가 3년 안에 이미 발급됐는지 증명하는 anti-abuse/dedupe record다. 무료권 발급 source로 Grant와 연결된다.
- 실제 시간순 이력은 append-only EntitlementLedger의 GRANTED·RESERVED·RELEASED·CONSUMED event가 담당한다. ReservationAllocation은 특정 시험 hold가 어느 Grant unit을 사용했는지 연결하고, Reservation·AttemptGroup은 시험 생성·재응시 lifecycle을 담당한다.
- 따라서 요약 관계는 `BenefitDefinition(종류) → EntitlementGrant(보유 권리) ← TrialClaim(무료 발급 근거) → Ledger(변경 이력)`이며 현재 BenefitDefinition만 미구현·하드코딩 상태다.

## 2026-08-28 현재 Benefit/Claim/Grant 구조와 구독제 방향 확인

- 현재 구현에는 TrialClaim과 EntitlementGrant가 있지만 BenefitDefinition/catalog collection은 없다. `FREE_EXAM_ONCE`가 TrialClaim·candidate alias·Grant와 repository 조건에 하드코딩돼 있다.
- verified event는 사전 `TrialEligibility` projection만 만들고, 최초 reserve가 기존 phone candidate Claim을 확인한 뒤 필요하면 TrialClaim·subject link·aliases와 `FREE_EXAM_ONCE` total 1-unit Grant·GRANTED ledger를 같은 Transaction에서 lazy 생성한다.
- TrialClaim은 phone당 무료 1회와 claimedAt+3년 dedupe, EntitlementGrant는 사용자의 실제 one-time unit과 available/held/consumed 상태를 담당한다. BenefitDefinition을 도입한다면 두 record가 stable benefitCode로 참조하는 공통 정책 catalog가 된다.
- 향후 유료 모델을 credit 대신 단순 구독제로 한다면 TrialClaim은 무료 1회 전용으로 유지하고 유료 권리는 기간형 SubscriptionEntitlement로 분리하는 것이 적절하다. 구독은 quantity 차감 대신 ACTIVE 상태와 startsAt/endsAt을 검증하며 시험마다 Reservation·Session idempotency와 usage audit는 계속 필요하다.
- 무료 사용권은 기존 hold/confirm/consume을 사용하고, 활성 구독은 unit을 차감하지 않되 Reservation이 어떤 entitlement source로 승인됐는지 기록하는 resolver가 필요하다. 구독 plan·Store 검증·갱신·해지·만료·grace period는 결제 착수 시 별도 계약으로 확정한다.
- 이번 확인에서는 credit 후속 방향을 구독 선호로 기록했지만 현재 무료 MVP 계약·코드와 동결된 결제 계약을 변경하지 않았다.

## 2026-08-28 사전 정의 혜택과 사용자 Claim/Grant 역할 구분

- 프로모션 공통 정보를 한 번만 사전 정의하고 사용자 보유 기록은 그 정의에 연결하자는 방향은 타당하다. 다만 공유되는 사전 정의와 phone/user별 상태를 가진 TrialClaim은 서로 다른 aggregate다.
- 권장 모델은 `BenefitDefinition` 또는 catalog에 immutable `benefitCode`, 표시 이름, unit type, 소비 정책과 policyVersion을 사전 등록하고, 사용자별 `EntitlementGrant`가 benefitCode·source campaign·quantity·expiry를 참조하는 정규화 구조다. 프로모션 이름·정책을 모든 Claim에 복제하지 않아도 된다.
- 사용자에게 무엇이 몇 개 귀속됐는지는 어떤 형태로든 저장해야 한다. “연결만 한다”의 연결 document가 바로 보유 기록이며, 현재는 EntitlementGrant의 subjectRefId·grantType·sourceType/sourceId와 unit projection이 그 역할을 한다. paid/promotion credits처럼 수량이 있는 권리는 amount나 개별 token 없이는 소유량을 판단할 수 없다.
- TrialClaim은 공유 catalog가 아니라 `(phone candidate, FREE_EXAM_ONCE)` 중복 수급 방지와 3년 retention을 가진 사용자별 anti-abuse record다. 하나의 TrialClaim을 여러 사용자·campaign이 공유하거나 빈 Claim pool을 미리 만들면 subject·claimedAt·retention 의미와 unique candidate 불변식이 깨진다.
- 따라서 사용자의 확장성 직관은 “TrialClaim을 미리 생성”보다 “BenefitDefinition을 미리 생성하고 Claim/Grant가 code로 연결”로 구현하는 것이 적절하다. 이 구조에서는 TrialClaim lazy/eager 생성 시점과 catalog 확장은 독립적으로 결정할 수 있다.
- 무료·일회성 exam pass는 개별 entitlement token 방식도 가능하지만 paid 100 credits까지 token 100개로 만들면 document 수가 커진다. one-off benefit은 unit/token, fungible credit는 grant batch+quantity를 쓰고 ReservationAllocation·ledger를 공통 소비 계층으로 두는 hybrid가 권장된다.
- 현재 코드는 benefit definition collection 없이 `FREE_EXAM_ONCE` 문자열이 Claim·alias·grant/repository에 하드코딩돼 있다. 후속 promotion/payment 단계에서 catalog와 resolver를 도입하면 되며, 이 논의만으로 현행 TrialClaim 생성 시점이나 계약을 변경하지 않았다.

## 2026-08-28 TrialClaim 사전 생성 제안 비교

- 현재는 verified event에서 `trial_eligibility` projection만 만들고, 최초 reserve Transaction에서 TrialClaim·candidate alias·subject link·1-unit grant·GRANTED ledger를 생성한 뒤 즉시 hold한다. 멘토 제안은 Claim 생성 시점을 verified event 처리로 앞당기는 계약 변경에 가깝다.
- TrialClaim을 미리 만들면 최초 reserve의 쓰기와 지연은 줄고, phone candidate dedupe와 사용자 권리 귀속을 더 일찍 확정할 수 있다. 반면 무료 시험을 시작하지 않은 모든 verified 사용자에게 Claim·alias·link가 생기고, grant까지 선발급하면 미사용 grant와 revoke·탈퇴·재가입 정합성 부담도 늘어난다.
- 가장 큰 제품 차이는 `claimedAt + 3년`의 시작점이다. 현재는 최초 reserve가 기산점이다. verified 시 TrialClaim을 ACTIVE로 만들면 인증 시점부터 3년이 시작되어 사용하지 않은 사용자도 보존·재수급 제한 대상이 된다. `claimedAt=null`의 ELIGIBLE Claim을 도입하면 별도 상태·CAS·purge 계약이 필요하며 현재 `trial_eligibility`와 역할이 중복된다.
- TrialClaim만 미리 만들고 grant는 reserve에서 만들면 reserve 단순화 효과가 제한적이다. Claim·alias·link·grant까지 선발급하면 의미는 명확하지만 event consumer가 자격 projection과 entitlement issuance를 함께 책임해 결합도와 장애 영향이 커진다.
- TrialClaim은 `FREE_EXAM_ONCE`의 phone dedupe aggregate이므로 이를 미리 생성하는 것만으로 coupon·출석·추천·유료 promotion 확장이 쉬워지지는 않는다. 프로모션 확장의 공통 지점은 catalog/offer definition과 generic EntitlementGrant·ledger·allocation resolver다.
- 현재 제품 계약과 최소 구현에는 lazy TrialClaim을 유지하는 안을 권장한다. verified 직후 사용자에게 권리를 표시해야 하는 명확한 제품 요구가 생기면 사전 Claim과 grant 발급을 함께 재설계하고 claimedAt 기산점·revoke/rejoin·미사용 권리 정책을 ADR에서 다시 승인해야 한다. 이 비교만으로 계약이나 코드를 변경하지 않았다.

## 2026-08-28 멘토 제안과 현재 무료 모의고사 모델 비교

- 멘토의 “무료 모의고사라는 이름으로 미리 생성”은 전역 catalog/benefit definition 하나를 미리 정의하고, 사용자별 grant는 필요할 때 발급해 기존 reserve·hold·confirm·ledger 흐름을 재사용하는 의미로 해석하는 것이 적절하다. 사용자별 grant나 실제 시험 Session을 모두 미리 만드는 방식은 미사용 document와 revoke·expiry 정합성 비용이 커져 권장하지 않는다.
- 현재 구현도 `grantType=FREE_EXAM_ONCE`, `sourceType=TRIAL_CLAIM`, `sourceId`, unit과 ledger/allocation을 사용하므로 공통 entitlement 모델의 기반은 있다. 다만 type·수량과 resolver가 코드에 고정돼 있고 catalog/campaign definition·policy version·expiry·priority는 아직 없다.
- 현재 최종 소비는 feedback 생성 시점이 아니라 Learning Core Session durable commit 직후 confirm이다. feedback·score·summary 최종 성공은 AttemptGroup COMPLETED, 최종 실패는 RETAKE_AVAILABLE로 구분하고 후자는 같은 consumption의 REPLACEMENT Session을 허용한다.
- catalog definition을 추가해도 실제 Learning Core ExamSession은 시험마다 필요하다. Billing의 AttemptSession은 문제·답안·채점 데이터를 저장하는 Session이 아니라 active/stale fencing과 재시도 정합성을 위한 최소 projection이라 제거하면 안 된다.
- 프로모션 확장성은 display name만 추가해서 얻을 수 없다. immutable `benefitCode`/`offerCode`, campaignId, grant type·unit, source, 유효기간, phone/user 제한, stacking·우선순위, policyVersion과 ledger dedupe 규칙이 필요하다. 이름은 변경 가능한 표시값으로만 사용한다.
- 권장 hybrid는 catalog/offer 정의만 사전 생성하고 사용자 Claim/grant는 최초 reserve 또는 승인된 지급 event에서 lazy issue하며, 무료·promotion·paid 모두 allocation과 reserve/confirm/cancel lifecycle을 재사용하는 방식이다. 이 방향은 후속 결제·프로모션 설계 권장안이며 아직 현재 무료 MVP 계약이나 구현을 변경하지 않았다.

## 2026-08-28 현재 무료 모의고사 소비 로직 재확인

- 현재 무료 모의고사는 paid credit 10개를 차감하지 않고 `FREE_EXAM_ONCE` grant의 1 unit을 `available → held → consumed`로 전이한다. paid credit의 시험당 10-credit 차감은 후속 결제 범위다.
- Identity eligibility event 수신만으로 Claim·grant를 만들지 않는다. 최초 INITIAL reserve Transaction에서 current VERIFIED binding과 candidate alias를 확인하고 기존 Claim이 없을 때만 TrialClaim·subject link·candidate alias, total 1 unit grant와 `GRANTED` ledger를 만든다.
- reserve는 grant를 `available 1→0`, `held 0→1`로 잠그고 HELD allocation, `RESERVED` ledger, 5분 `RESERVED` Reservation과 PROPOSED Session을 같은 Transaction에 만든다. 이는 최종 소비가 아니라 다른 동시 요청의 사용을 막는 hold다.
- Learning Core Session durable commit 뒤 confirm에서 Reservation CAS가 승리하면 allocation을 CONSUMED, grant를 `held 1→0`, `consumed 0→1`로 전환하고 `CONSUMED` ledger, AttemptGroup OPEN과 Session ACTIVE를 같은 Transaction에 반영한다. Summary 완료를 기다리지 않는다.
- confirm 전 cancel 또는 5분 expiry는 allocation을 RELEASED, grant를 `held 1→0`, `available 0→1`로 복원하고 `RELEASED` ledger와 Session FAILED를 기록한다. TrialClaim과 claimedAt은 삭제·갱신하지 않으므로 새 무료권을 만드는 것이 아니라 같은 1 unit을 다시 사용할 수 있게 한다.
- confirm 뒤에는 일반 cancel·expiry로 소비를 복원하지 않는다. 최종 결과 실패 시에도 consumed unit은 유지하고 후속 AttemptGroup event가 RETAKE_AVAILABLE을 만든 뒤 같은 consumption·AttemptGroup·mockExamId의 REPLACEMENT Session만 허용한다. 이 event consumer는 다음 작업으로 아직 미구현이다.
- 모든 지급·hold·소비·복원은 append-only ledger와 Mongo Transaction, unique index, version CAS, operation idempotency로 중복 지급·이중 소비를 방지한다.

## 2026-08-28 다음 작업: AttemptGroup 상태 event consumer

- 다음 권장 vertical slice는 Learning Core가 보내는 `POST /internal/v1/attempt-group-events`를 처리해 Billing의 AttemptGroup·AttemptSession projection을 수렴시키는 작업이다. PLAN 번호와 Jira는 아직 생성하지 않았다.
- `GRADING`은 결과 생성 중이라 replacement reserve를 잠시 차단하고, `COMPLETED`는 필수 feedback·valid score·summary가 모두 조회 가능할 때 group을 terminal로 닫는다. `RETAKE_AVAILABLE`은 최종 실패 때 무료권을 환불하거나 새로 지급하지 않고 기존 consumption·AttemptGroup·mockExamId로 새 Session 재응시를 허용한다.
- 구현 범위는 schema v1 strict decoder·canonical digest, shared `inbound_event_inbox` 멱등성, active Session fencing, AttemptGroup/Session version CAS, inbox와 projection의 단일 Mongo Transaction, Learning Core workload route와 replica-set 동시성·retry 검증이다.
- `COMPLETED`에는 evidence boolean 세 개와 `evidenceVersion=1`을 exact validation하고, `RETAKE_AVAILABLE`에는 승인된 low-cardinality `failureCode`만 허용한다. 질문·답안·점수·feedback·summary·AI/provider 원문은 Billing에 저장하지 않는다.
- 구현 계획에서 추가로 고정할 세부 계약은 sequence가 없는 event의 순서 역전 수렴 정책, 존재하지 않는 group/session·owner mismatch 응답과 재시도 정책, `failureCode` allowlist다. 권장 방향은 terminal evidence가 유효하면 OPEN에서 terminal로도 전진 수렴하고, COMPLETED는 절대 다시 열지 않으며, missing prerequisite는 retryable하게 처리하는 것이다.
- 이 작업에는 Learning Core publisher/outbox, 탈퇴·재가입 owner rebind, repair/reconciliation route, 실제 Lattice/IAM/SG 배포와 결제를 포함하지 않는다. 다음 순서는 AttemptGroup consumer → owner rebind → Learning Core saga/outbox·Lattice staging E2E다.

## 2026-08-28 Billing domain/global 패키지 구조 개편 완료

- Billing Java root `web.tosunsaeng.billing` 아래를 `domain`과 `global` 중심으로 재편했다. `domain`은 `eligibility/trial`, `entitlement`, `entitlement/trial`, `reservation`, `attempt`로 분리했고 테스트 package도 같은 구조로 이동했다.
- Reservation API·application·request/response DTO·domain entity·repository·config를 기능 내부로 정리했다. 기존 Reservation package에 섞여 있던 TrialClaim·candidate alias·subject link, grant·ledger, AttemptGroup·Session은 각각 entitlement/trial, entitlement, attempt로 이동했다.
- 공통 Security·Mongo 설정, internal error handler·response, Mongo initializer·transaction helper는 `global`로 이동했다. Reservation과 Trial eligibility의 오류 생성은 feature exception으로 분리하되 공통 handler와 기존 HTTP status·code·response envelope는 유지했다.
- `ReservationConverter`를 추가해 request→command 및 service result→response 변환을 Controller에서 분리했다. URL·JSON DTO 필드·canonical hash 입력과 순서·service 호출 동작은 변경하지 않았다.
- 최종 `./gradlew clean test`에서 총 82개 테스트가 성공했고 실패·오류·skip은 0개다. 이전 package namespace 참조와 빈 legacy package directory도 제거했으며 `git diff --check`를 통과했다.
- Mongo collection·index·document business field, Transaction·CAS·멱등성·Security 정책은 변경하지 않았다. 다만 Spring Data가 기록하는 `_class`가 Java package 이동에 따라 달라질 수 있다. Billing은 아직 미배포 상태이므로 최초 배포 기준으로 적용하되, 기존 환경에 보존할 데이터가 있다면 배포 전에 old `_class` 존재 여부와 migration 필요성을 확인해야 한다.
- 이번 구조 개편에서는 큰 `ReserveService`와 `ReservationLifecycleService` orchestration을 분해하지 않았다. 다음 구조 개선은 실제 후속 기능 경계가 생길 때 transaction 경계를 보존하면서 별도 계획으로 진행한다.

## 2026-08-28 Billing 패키지 구조 비교와 개편 초안

- Identity는 `web.tosunsaeng.identity.domain/{auth,user}/...`와 `global/...`, Learning Core는 `web.tosunsaeng.domain/{exams,withdrawal}/...`와 `global/...`로 기능 우선 패키지를 사용한다. 기능 내부는 필요에 따라 `api`, `application`, `dto`, `converter`, `domain/entity`, `domain/enums`, `repository`, `exception`, `config`로 나뉜다.
- Billing은 현재 최상단에 `config`, `global`, `reservation`, `trialeligibility`가 함께 있고, `reservation` 안에 Reservation뿐 아니라 TrialClaim·candidate alias·subject link·grant·ledger·AttemptGroup·AttemptSession까지 들어 있다. 구현 vertical slice 경계가 장기 domain 경계처럼 굳어진 상태다.
- 권장 목표는 `web.tosunsaeng.billing` 루트를 유지하면서 바로 아래를 `domain`과 `global`로 구분하는 구조다. domain은 `eligibility/trial`, `entitlement`, `entitlement/trial`, `reservation`, `attempt`로 나누고 각 기능 내부에서 api/application/dto/converter/domain/repository/exception/config를 필요한 만큼만 둔다.
- `ReservationProperties`와 `TrialEligibilityProperties`는 각 domain config로, Security·ingress와 공통 Mongo 설정은 global config/infrastructure로 이동한다. 현재 `global/api`의 공통 error envelope·handler는 global response/exception으로 명확히 이름을 바꾼다.
- 우선 단계는 package·import·test package만 옮기고 HTTP DTO, Mongo collection/index, Spring bean, transaction과 동작을 바꾸지 않는 구조 이동이다. 다음 단계에서 feature exception과 converter를 분리하고, 마지막에 큰 orchestration service의 내부 협력 컴포넌트를 분해한다.
- 이번 작업은 구조 분석과 초안 제시만 수행했다. 애플리케이션·테스트 패키지는 이동하지 않았고 Jira도 생성·변경하지 않았다.

## 2026-08-28 TMI-113 완료 처리

- 사용자의 명시적 승인에 따라 Jira `TMI-113`을 `해야 할 일`에서 `완료`로 전환했고 완료 category를 재확인했다.
- 완료 근거는 PLAN-003 Reservation lifecycle 구현과 최종 `./gradlew clean test` 총 82개 성공, 실패·오류·skip 0 결과다.
- Jira 설명·담당자, 애플리케이션 코드, Git 브랜치와 production 활성화 gate는 변경하지 않았다.
- 다음 기능 순서는 AttemptGroup 상태 event consumer이며 별도 계획·Jira 승인 후 진행한다. Learning Core saga·Lattice staging E2E 전에는 production caller를 활성화하지 않는다.

## 2026-08-28 TMI-113 PLAN-003 Reservation lifecycle 구현 완료

- `POST /internal/v1/reservations/{reservationId}/confirm`, `cancel`, `POST /internal/v1/reservations/status`를 구현했다. confirm/cancel은 16 KiB strict JSON, lowercase UUID v4 path·header·body, exact enum·UTC millisecond timestamp와 canonical SHA-256 command hash를 사용한다.
- INITIAL confirm은 HELD allocation과 grant를 CONSUMED로 전이하고 `CONSUMED:<reservationId>` ledger, AttemptGroup OPEN과 Session ACTIVE를 하나의 Mongo Transaction으로 반영한다. REPLACEMENT confirm은 기존 consumption을 유지하고 group Session만 교체한다.
- INITIAL cancel·expiry는 allocation을 RELEASED, grant held를 available로 복원하고 `RELEASED:<reservationId>` ledger를 하나만 남긴다. REPLACEMENT cancel·expiry는 entitlement를 변경하지 않는다. TrialClaim·alias·subject retention은 lifecycle에서 건드리지 않는다.
- confirm·cancel·expiry는 `RESERVED + activeGuard + version` Reservation CAS를 첫 write로 사용한다. CONFIRMED 취소와 CANCELED/EXPIRED 일반 confirm은 409이며 repair route는 추가하지 않았다.
- terminal RESERVE·CONFIRM·CANCEL command는 active guard를 해제하고 `terminalAt + 7일` purgeAt을 기록한다. Reservation·ledger audit에는 TTL을 추가하지 않았다. status는 RESERVE operation과 live Reservation·group을 읽기만 하며 missing operation은 404다.
- expiry worker는 기본 disabled, scan 10초·batch 100 기본값이며 due Reservation별 독립 Transaction과 다중 ECS task CAS 수렴을 사용한다. due batch size와 oldest due lag를 식별자 없는 metric으로 기록한다.
- schema v2와 기존 index를 그대로 재사용했다. 새 collection, index, 결제·AttemptGroup 상태 event·owner rebind·repair·타 서비스 코드는 추가하지 않았다.
- `./gradlew clean test`에서 총 82개 테스트가 성공했고 실패·오류·skip은 0개다. replica-set Testcontainers가 INITIAL/REPLACEMENT, concurrent same-command replay, confirm/cancel/expiry 모든 race, multi-worker, transient retry와 unknown commit을 실제 검증했다.
- Jira `TMI-113`은 사용자 승인 없이 상태를 변경하지 않아 `해야 할 일`로 유지한다. production caller는 Learning Core saga, expiry 운영 활성화, Lattice/IAM/SG와 staging E2E 전까지 열지 않는다.

## 2026-08-28 PLAN-003 Jira 작업 생성

- TMI 프로젝트에 `TMI-113` — `[Billing] Reservation lifecycle 구현`을 `작업` 유형으로 생성했다. 상태는 `해야 할 일`, 담당자는 미지정이다.
- Jira 본문에 confirm·cancel·status, 5분 expiry worker, INITIAL consume/release, REPLACEMENT 무추가차감, terminal command 7일 보존과 confirm/cancel/expiry CAS race 완료 조건을 반영했다.
- AttemptGroup 상태 event, 탈퇴·재가입 owner rebind, repair route, Learning Core saga, Identity 변경, 실제 Lattice/AWS 배포, TrialClaim purge와 결제는 제외 범위로 명시했다.
- PLAN-003 상태를 사용자 승인·Jira 생성·구현 전으로 갱신했다. 애플리케이션 구현과 AGENTS 현재 구현 단위 전환은 아직 시작하지 않았다.
- production Reservation caller는 PLAN-003만으로 활성화하지 않고 후속 상태 event·서비스 연동·AWS staging E2E gate를 유지한다.

## 2026-08-28 PLAN-005 이후 남은 출시 작업 구분

- PLAN-005 재가입 owner rebind까지 완료하면 phone당 무료 1회, reserve lifecycle, 결과 실패 재응시와 탈퇴·재가입 승계를 포함한 Billing 핵심 제품 정책은 완성된다.
- 그러나 실제 출시 전에는 Learning Core의 Billing client·시험 생성 saga·status reconciliation과 AttemptGroup outbox, Identity 실제 SigV4 eligibility delivery/owner transfer 계약, VPC Lattice·IAM·SG·ECS 설정이 남는다.
- staging에서 same-key retry, Session commit 실패, confirm 응답 유실, expiry/confirm race, 최종 결과 실패·재가입과 unsigned/wrong-role/direct-bypass E2E를 통과해야 한다.
- production Mongo index/migration preflight, expiry worker enable, metric·alert, backup/rollback runbook과 rollout도 필요하다.
- TrialClaim 3년 만료 daily purge·24시간 SLA와 35일 backup restore purge는 별도 운영 vertical slice로 남는다. 최초 Claim 만료 전에는 반드시 구현·검증돼야 한다.
- 따라서 PLAN-005는 핵심 Billing 기능의 끝이지 전체 production 출시의 끝은 아니다. 이번 설명에서는 PLAN 번호나 계약을 새로 확정하지 않았다.

## 2026-08-28 PLAN-003 이후 재가입 권리 승계 구현 순서

- PLAN-003 Reservation confirm/cancel/status·expiry lifecycle을 먼저 구현하는 순서가 맞다. owner rebind가 소비·복원·terminal 상태를 안전하게 판정하려면 lifecycle 상태 머신과 CAS가 선행돼야 한다.
- 결과 실패 후 재가입 재응시까지 완성하려면 PLAN-003 다음에 AttemptGroup 상태 event consumer를 구현해 `GRADING`, `COMPLETED`, `RETAKE_AVAILABLE`을 신뢰 가능한 상태로 만든다.
- 그 다음 별도 vertical slice로 old eligibility REVOKED + new VERIFIED + same candidate에 따른 retained subject owner rebind를 구현한다. 새 Claim·grant는 만들지 않고 기존 미사용 unit 또는 same consumption만 새 userId가 사용한다.
- 권장 순서는 `PLAN-003 lifecycle → AttemptGroup event → 재가입 owner rebind → Learning Core saga/Lattice E2E`다.
- PLAN-003 계획은 아직 초안이고 Jira도 미생성이다. 이번 확인에서는 구현·Jira·계약·PLAN을 변경하지 않았다.

## 2026-08-28 Identity phone uniqueness와 Billing owner transfer 전제 정정

- Identity는 verified phone 하나를 동시에 한 Firebase User와 한 ACTIVE MEMBER에만 연결한다. 다른 활성 owner가 점유하면 가입·Guest 승격은 `PHONE_ALREADY_LINKED`로 실패하므로 정상 흐름에서 같은 번호의 활성 계정 두 개는 존재하지 않는다.
- 탈퇴 lifecycle이 기존 PhoneIdentity/active alias와 Firebase phone 점유를 해제한 뒤에만 같은 번호로 새 UUID 가입이 가능하다.
- 재가입 시 Billing에 남은 old `billing_subject_link`는 활성 Identity 계정이 아니라 Claim 3년 dedupe를 위한 과거 owner mapping이다. owner transfer는 두 활성 계정 사이의 이전이 아니라 이 retained mapping을 새 verified userId에 재연결하는 작업이다.
- 이전 권장안의 “기존 활성 계정이면 자동 이전 금지”는 정상 제품 분기가 아니라 Identity uniqueness drift, revoke/verified event 순서와 부분 완료를 막는 fail-closed 방어 조건으로 정정한다.
- Billing transfer의 실질 조건은 old user eligibility `REVOKED`, new user eligibility `VERIFIED`, 같은 retained candidate와 active Claim 일치다. event 순서가 아직 수렴하지 않았으면 이전하지 않고 processing/reconciliation으로 보낸다.
- 이 설명은 아직 owner-transfer 계약 승인이나 PLAN 변경을 의미하지 않는다.

## 2026-08-28 재가입 사용자의 기존 무료시험 권리 승계 공백 확인

- 현행 owner mismatch 차단은 탈퇴 후 새 UUID로 재가입한 사용자가 같은 verified phone candidate의 기존 Claim을 이어받지 못하게 한다. 중복 무료 지급은 막지만 미사용·재응시 가능 권리까지 영구 차단하는 제품 공백이 있다.
- phone당 1회 정책에 맞는 해결은 새 TrialClaim/grant를 발급하는 것이 아니라 기존 Claim·grant·consumption을 유지한 채 `billing_subject_link`의 owner를 검증된 새 userId로 이전하는 것이다.
- 권장 transfer 조건은 새 사용자의 current VERIFIED candidate가 기존 active Claim alias와 일치하고, 기존 owner가 Identity에서 WITHDRAWN/이전 가능 상태임이 인증된 event로 증명되며, active RESERVED/GRADING race가 없는 경우다. current owner가 활성 상태면 자동 이전하지 않는다.
- 미사용 또는 cancel/expiry로 복원된 grant는 같은 Claim의 available unit을 새 owner가 INITIAL reserve한다. OPEN·RETAKE_AVAILABLE은 같은 AttemptGroup·consumption·mockExamId로 새 Session만 시작한다. GRADING은 최종 수렴 전 차단하고 COMPLETED는 추가 무료 응시를 허용하지 않는다.
- 이전 Session·답안·upload·결과·Summary는 새 계정에 승계하거나 노출하지 않는다. old Session을 fencing하고 새 Session에서 처음부터 시작한다.
- Claim ID, `claimedAt`, `retentionExpiresAt`, grant와 consumption ledger는 변경하지 않아 phone당 1회를 유지한다. owner transfer 자체는 멱등 event·Transaction과 비식별 audit ledger가 필요하다.
- 이 방향은 권장안이며 아직 계약을 변경하거나 PLAN-003에 포함하지 않았다. 승인 시 별도 owner-transfer 계약과 구현 계획을 작성하고 production gate에 포함해야 한다.

## 2026-08-28 탈퇴·재가입 시 미완료 AttemptGroup 재응시 확인

- Identity의 현재 재가입 계약은 탈퇴한 계정을 복구하지 않고 새 canonical UUID `userId`를 발급한다.
- Billing은 TrialClaim과 candidate dedupe를 3년 유지하므로 탈퇴·재가입으로 새 무료권을 지급하지 않는다. 기존 Claim의 active subject link는 old userId를 가리킨다.
- 새 userId가 같은 전화 candidate로 reserve하면 기존 Claim을 찾지만 owner mismatch가 발생해 현재 구현은 `ENTITLEMENT_INSUFFICIENT`로 차단한다. 기존 OPEN·RETAKE_AVAILABLE AttemptGroup도 자동 이전하지 않는다.
- 같은 userId라면 OPEN·RETAKE_AVAILABLE은 REPLACEMENT 재응시가 가능하고 GRADING은 최종 처리 전이므로 `COMMAND_PROCESSING`으로 차단한다. 실제 재가입은 새 UUID이므로 이 예외에 해당하지 않는다.
- 재가입 사용자에게 기존 consumption 재응시를 허용하려면 phone candidate 일치만으로 이전하지 않고 Identity가 인증한 owner-transfer/rejoin event와 Billing의 원자적 subject link·AttemptGroup ownership 이전 계약이 필요하다. 현재 `UserMerged`/owner transfer wire 계약은 미확정·미구현이다.
- 이번 확인으로 계약·코드·PLAN을 변경하지 않았다. 현재 권장 동작은 자동 이전 차단이다.

## 2026-08-28 같은 consumption 재응시 처리 설명

- 최초 INITIAL confirm에서 무료 unit을 한 번 `CONSUMED`하고 그 ledger event를 AttemptGroup의 `consumptionLedgerEventId`로 연결하는 것이 목표 계약이다.
- 결과 생성 중에는 AttemptGroup이 `GRADING`이며 새 reserve를 `COMMAND_PROCESSING`으로 막는다. 최종 실패가 확정되면 후속 AttemptGroup event consumer가 group을 `RETAKE_AVAILABLE`로 전환한다.
- 재응시는 새 operationId·새 sessionId로 reserve하지만 동일 AttemptGroup ID와 고정 `mockExamId`를 사용한다. Billing은 이를 `REPLACEMENT`로 판정한다.
- REPLACEMENT는 새 TrialClaim, grant, allocation, `GRANTED`·`RESERVED`·`CONSUMED` ledger를 만들지 않는다. 기존 consumption을 유지한 채 새 Reservation·PROPOSED Session만 만들고 confirm에서 Session pointer를 교체한다.
- PLAN-002 코드에는 OPEN·RETAKE_AVAILABLE group의 REPLACEMENT 판정과 무allocation·무ledger reserve가 구현돼 있다. PLAN-003 confirm은 아직 미구현이며 결과 실패를 RETAKE_AVAILABLE로 만드는 AttemptGroup event consumer도 PLAN-003 이후 후속 작업이다.
- 따라서 계약과 일부 reserve 기반은 준비됐지만 결과 실패부터 재응시까지의 end-to-end 흐름은 아직 구현 완료 상태가 아니다.

## 2026-08-28 무료권 소비 확정과 Summary 완료 시점 재확인

- `reserve`는 무료 unit을 `HELD`로 잠가 다른 시험 생성 요청이 동시에 사용하지 못하게 하는 임시 상태다.
- 현재 확정 계약에서 무료권의 최종 소비는 Summary 생성 때가 아니라 Learning Core ExamSession이 durable commit된 직후 `confirm`에서 일어난다.
- confirm은 allocation을 `CONSUMED`로 바꾸고 AttemptGroup을 `OPEN`으로 연다. 이는 시험 1회가 시작됐다는 entitlement 확정이며 시험 결과가 완성됐다는 뜻은 아니다.
- 필수 피드백·유효 점수·Summary가 사용자에게 조회 가능해지면 별도 AttemptGroup 상태 event로 `COMPLETED` 처리한다.
- 결과 생성이 최종 실패하면 무료 Claim/grant를 새로 지급하거나 consumption을 취소하지 않고 `RETAKE_AVAILABLE`로 전환해 같은 consumption·mockExamId로 새 Session을 허용한다.
- 이번 설명으로 계약이나 PLAN-003을 변경하지 않았다. 소비 확정을 Summary 완료까지 미루려면 기존 ADR·통합 계약·PLAN과 실패·동시성 정책을 함께 변경하는 별도 결정이 필요하다.

## 2026-08-28 PLAN-003 Reservation lifecycle 계획서 작성

- `docs/plans/PLAN-003-reservation-lifecycle.md` 초안을 작성했으며 신규 Jira는 아직 생성하지 않았다.
- 범위는 confirm, cancel, read-only status와 5분 expiry worker다. 네 경로가 같은 Reservation terminal 상태를 공유하므로 하나의 vertical slice로 묶었다.
- INITIAL confirm은 allocation·grant consume, `CONSUMED` ledger, AttemptGroup `OPEN`, Session `ACTIVE`를 한 Transaction으로 처리한다. REPLACEMENT confirm은 기존 consumption을 유지한다.
- INITIAL cancel/expiry는 allocation을 원 grant로 정확히 복원하고 하나의 `RELEASED` ledger를 남긴다. REPLACEMENT는 기존 consumption을 변경하지 않으며 모든 경로에서 TrialClaim은 유지한다.
- confirm/cancel/expiry는 Reservation expected-state/version CAS를 첫 write로 사용해 경쟁 시 terminal 상태 하나만 commit한다. status는 command·ledger를 만들지 않는 조회로 고정했다.
- terminal command 7일 보존, expiry scan interval 10초·batch 100의 configurable 최초 제안, existing Mongo schema v2 index 재사용과 production caller gate를 계획에 포함했다.
- Learning Core saga, AttemptGroup 상태 event, repair, 실제 Lattice/AWS 배포와 결제는 제외했다. 계획 승인 뒤 별도 사용자 승인으로 Jira를 생성한다.

## 2026-08-28 다음 작업: Reservation lifecycle

- 다음 권장 vertical slice는 `confirm`, `cancel`, `status`와 5분 expiry worker를 함께 구현하는 PLAN-003이다.
- PLAN-002의 `reserve`는 무료 시험 unit을 `RESERVED`로 잠근 단계까지만 구현했다. 다음 작업은 Learning Core Session 저장 성공 시 `CONFIRMED`로 최종 소비하고, 저장 실패 시 `CANCELED`, 호출 없이 5분이 지나면 `EXPIRED`로 잠금을 해제한다.
- `status`는 confirm 응답 유실이나 timeout 때 새 command·ledger를 만들지 않고 기존 Reservation의 실제 결과를 조회하기 위한 read-only API다.
- INITIAL confirm은 allocation과 grant를 `held → consumed`로 전환하고 `CONSUMED` ledger, AttemptGroup `OPEN`, Session `ACTIVE`를 하나의 Transaction으로 반영한다. REPLACEMENT confirm은 기존 consumption을 재사용하고 추가 차감하지 않는다.
- INITIAL cancel/expiry는 원래 grant에 allocation을 정확히 복원하고 `RELEASED` ledger를 남긴다. TrialClaim과 `claimedAt`은 유지한다. REPLACEMENT cancel/expiry는 기존 consumption을 변경하지 않는다.
- confirm·cancel·expiry 경쟁은 Reservation 상태 CAS와 Mongo Transaction으로 한 terminal 상태만 승리하게 해야 한다. `CONFIRMED` 일반 cancel과 CANCELED/EXPIRED 자동 repair-confirm은 금지한다.
- 이 lifecycle이 완성되기 전에는 production Learning Core가 reserve route를 호출하도록 활성화하지 않는다. 이후 작업은 AttemptGroup 상태 event·reconciliation, Learning Core saga, 실제 Lattice staging E2E 순서다.
- 이번 작업은 다음 범위를 설명한 분석이며 PLAN-003, Jira, 애플리케이션 코드는 아직 생성하거나 변경하지 않았다.

## 2026-08-28 TMI-112 완료 처리

- 사용자의 명시적 승인에 따라 Jira `TMI-112`를 `해야 할 일`에서 `완료`로 전환했고 완료 category를 재확인했다.
- Jira 설명·담당자, 애플리케이션 코드, PLAN-002 API·Mongo·보안 계약과 Git branch는 변경하지 않았다.
- 완료 근거는 직전 PLAN-002 구현과 `./gradlew clean test` 총 58개 성공, 실패·skip 0 결과다.
- 실제 production 활성화를 의미하지 않는다. confirm/cancel/status·expiry, Learning Core saga와 Lattice staging gate는 후속 작업으로 유지한다.

## 2026-08-28 TMI-112 PLAN-002 initial reserve 구현 완료

- `POST /internal/v1/reservations`에 16 KiB strict JSON, 필수 lowercase UUID v4 `Idempotency-Key`, canonical SHA-256 payload hash와 직접 200 response DTO를 구현했다.
- 첫 reserve의 단일 Mongo Transaction에서 current VERIFIED eligibility, expired alias fencing, candidate/key rotation dedupe, 필요한 TrialClaim·subject link·전체 aliases·`FREE_EXAM_ONCE` grant와 `GRANTED`, INITIAL allocation hold·`RESERVED`, 5분 Reservation·PROPOSED Session과 command response snapshot을 함께 반영한다.
- 같은 operation/payload는 동일 Reservation을 replay하고 다른 payload는 409다. 다른 owner는 402로 차단하고, 같은 candidate·같은 user 동시 요청은 unique/partial unique index와 retry 후 Claim·active command·Reservation·Session 하나로 수렴한다.
- OPEN·RETAKE_AVAILABLE group은 REPLACEMENT로 기존 consumption·mockExamId를 재사용하며 새 allocation과 entitlement ledger가 없다. GRADING은 processing, mockExamId 불일치는 state conflict다.
- Mongo initializer를 schema v2로 확장해 reserve 관련 10개 collection과 ADR-001의 23개 index를 생성·option 검증한다. runtime drop/recreate와 Reservation·Claim audit TTL 삭제는 추가하지 않았다.
- test security에서 Learning Core role은 reserve route만, Identity role은 eligibility route만 허용하고 default disabled는 계속 deny한다. 실제 Lattice auth policy·SG 배포는 범위 밖이다.
- `./gradlew clean test`에서 총 58개 테스트가 통과했고 실패·skip은 0개다. replica-set Testcontainers가 initial atomicity·rollback·동시성·key rotation·expired alias·REPLACEMENT·retry·unknown commit을 실제 실행했다.
- Jira `TMI-112` 상태는 사용자 승인 없이 변경하지 않아 `해야 할 일`로 유지한다. confirm/cancel/status·expiry lifecycle과 Learning Core saga·실제 staging E2E 전에는 production reserve caller를 활성화하지 않는다.

## 2026-08-28 PLAN-002 Jira 작업 생성

- TMI 프로젝트에 `TMI-112` — `[Billing] Free exam initial reserve 구현`을 `작업` 유형으로 생성했다. 상태는 `해야 할 일`, 담당자는 미지정이다.
- PLAN-002의 reserve endpoint, INITIAL·REPLACEMENT 판정, Claim·무료 grant/ledger·allocation·Reservation 단일 Transaction, 멱등성·index·동시성·보안·개인정보 완료 조건을 Jira 본문에 반영했다.
- confirm/cancel/status·expiry, AttemptGroup event·reconciliation, 타 서비스 adapter, 실제 AWS 배포와 결제는 제외 범위로 명시했다.
- Billing lifecycle과 Learning Core saga·실제 Lattice staging 검증 전에는 production reserve caller를 활성화하지 않는 제한을 유지한다.

## 2026-08-28 reserve response 식별자·상태 의미 설명

- `operationId`는 앱이 만든 한 번의 시험 생성 명령 ID이며 Learning Core와 Billing이 같은 `Idempotency-Key`로 사용한다. transport retry에는 유지하고 의도적인 restart에는 새 값을 사용한다.
- `reservationId`는 Billing이 만든 한 번의 entitlement hold 기록 ID다. 후속 confirm/cancel이 이 Reservation을 대상으로 하며 audit 상태 전이를 보존한다.
- `reservationKind`는 새 소비를 준비하는 `INITIAL`인지 기존 consumption을 재사용하는 `REPLACEMENT`인지 Billing이 판정한 값이다.
- `reservationStatus=RESERVED`는 unit을 잠갔지만 아직 최종 소비하지 않은 상태다. Session durable commit 뒤 confirm되면 `CONFIRMED`, commit 실패·만료 시 `CANCELED` 또는 `EXPIRED`로 전이한다.
- `attemptGroupId`는 최초 응시와 허용된 restart를 하나의 consumption으로 묶는 Billing ID다. INITIAL reserve에서 미리 발급하지만 group `OPEN`은 confirm에서 확정한다.

## 2026-08-28 reserve의 sessionId·mockExamId 역할 설명

- `sessionId`는 Learning Core가 만들 예정인 한 번의 ExamSession 식별자다. reserve 시 proposed 값으로 먼저 고정해 응답 유실 retry가 같은 Session으로 수렴하고, 후속 confirm이 실제 durable commit된 바로 그 Session에 대한 것인지 확인한다.
- `mockExamId`는 AttemptGroup에서 사용할 문제지 식별자다. 최초 reserve에서 group에 고정하고 restart·REPLACEMENT가 같은 문제지를 유지하는지 확인해 다른 시험으로 entitlement를 재사용하는 것을 막는다.
- Billing은 시험 내용이나 Session을 생성하지 않으며 두 값은 1~128자 opaque token으로만 저장·비교한다. `sessionId`는 한 Session마다 바뀌고 `mockExamId`는 같은 AttemptGroup 동안 유지된다.

## 2026-08-28 PLAN-002 initial reserve 계획서 작성

- `docs/plans/PLAN-002-free-exam-initial-reserve.md` 초안을 작성했다. 신규 Jira는 아직 생성하지 않았다.
- 범위는 `POST /internal/v1/reservations`의 전체 reserve 판정으로 고정했다. 첫 INITIAL reserve의 단일 Mongo Transaction에서 command 멱등성, current VERIFIED binding, 만료 alias fencing과 dedupe, 필요한 TrialClaim·subject link·무료 grant/ledger, allocation hold, Reservation과 proposed Session projection을 함께 처리한다.
- 기존 `OPEN`·`RETAKE_AVAILABLE` group은 REPLACEMENT로 기존 consumption을 재사용하고 `GRADING`은 processing conflict, 다른 `mockExamId`는 state conflict로 처리한다.
- confirm/cancel/status, expiry worker, AttemptGroup event·reconciliation, Learning Core·Identity adapter와 실제 Lattice 배포는 제외했다. 해당 lifecycle이 완성되기 전 production caller activation은 금지한다.
- 계획 승인 뒤 별도 Jira를 생성하고 `AGENTS.md`의 현재 구현 단위를 PLAN-002로 전환한 다음 구현하는 순서를 권장한다.

## 2026-08-28 다음 구현 단위 확인

- PLAN-001 다음 권장 작업은 current `trial_eligibility`를 사용하는 `FREE_EXAM_ONCE` INITIAL reserve vertical slice다.
- eligibility event 수신과 TrialClaim 지급을 계속 분리한다. 첫 reserve의 단일 Mongo Transaction 안에서 idempotency command, current VERIFIED binding, candidate alias dedupe, 필요한 TrialClaim·subject link·grant·`GRANTED` ledger, allocation hold·`RESERVED` ledger와 Reservation을 함께 만든다.
- TrialClaim이나 grant만 미리 생성하는 별도 작업은 승인 계약을 위반하므로 진행하지 않는다.
- 다음 구현 전 `PLAN-002`와 별도 Jira로 reserve 범위·index·동시성 완료 조건을 고정하고 `AGENTS.md`의 현재 구현 단위를 갱신해야 한다.
- 그 이후 순서는 confirm/cancel/status·5분 expiry, AttemptGroup event·reconciliation, Learning Core saga와 Identity/Lattice staging 연동이다.

## 2026-08-27 TMI-110 완료 처리

- PLAN-001 구현과 33개 전체 테스트 통과를 근거로 Jira `TMI-110`을 `해야 할 일`에서 `완료`로 전환했다.
- Jira 설명·담당자·애플리케이션 코드·Git 브랜치는 변경하지 않았다.

## 2026-08-27 로컬 main·develop 브랜치 생성

- 현재 feature 브랜치의 커밋 `e0694f9`를 기준으로 로컬 `main`과 `develop` 브랜치를 생성했다.
- 현재 checkout은 `feat/TMI-110-trial-eligibility-event-consumer`에 그대로 유지했다.
- 이후 원격에도 `main`과 `develop`이 생성된 것을 확인했고 GitHub 기본 브랜치를 feature 브랜치에서 `main`으로 변경했다.

## 2026-08-27 PLAN-001 Trial eligibility consumer 구현 완료

- `POST /internal/v1/eligibility/trial/events`에 16 KiB bounded 수신, strict JSON decode, schema v1·expected scope 검증과 canonical SHA-256 digest를 구현했다.
- `inbound_event_inbox`와 `trial_eligibility`를 단일 Mongo Transaction으로 반영하며 APPLIED·DUPLICATE·STALE과 `EVENT_ID_CONFLICT`로 수렴한다. verified는 candidate 전체를 교체하고 revoked는 candidate를 제거한 revision tombstone을 유지한다.
- `ux_inbox_event_id`, `ux_inbox_identity_scope_user_revision`, `ttl_inbox_purge_at`, `ux_trial_scope_user`, `ix_trial_key_version`을 versioned initializer가 생성·비교하고 option 불일치 시 fail-fast한다. `auto-index-creation`은 false다.
- internal ingress는 기본 disabled, test Identity role, 필수 설정을 검증하는 Lattice AWS_IAM mode로 분리했다. 실제 AWS principal 검증은 ADR-002대로 Lattice policy와 SG가 담당하며 Lattice 실배포는 이번 범위가 아니다.
- Identity fixture 기반 decoder, MVC·security, transaction retry·unknown commit 재확인과 `mongo:7.0.14` replica-set index·rollback·동시성 테스트를 추가했다.
- `./gradlew clean test`에서 33개 테스트가 통과했다. TrialClaim, grant·ledger, Reservation·AttemptGroup, Identity SigV4 adapter와 실제 Lattice/IAM/SG는 추가하지 않았다.
- Jira `TMI-110`의 상태·담당자는 별도 수정 승인 없이 변경하지 않았다. 저장소 전체가 아직 Git 미추적 상태이므로 사용자가 기준선과 이번 구현 diff를 검토해 commit해야 한다.

## 2026-08-27 PLAN-001 Jira 작업 생성

- Jira `TMI-110`을 생성해 `docs/plans/PLAN-001-trial-eligibility-event-consumer.md`의 구현 범위와 완료 조건을 작업 단위로 고정했다.
- 포함 범위는 `/internal/v1/eligibility/trial/events`, 16 KiB 제한, strict decode, canonical digest, `inbound_event_inbox`, `trial_eligibility`, 단일 Mongo Transaction, 명시적 index 검증과 replica-set Testcontainers 테스트다.
- TrialClaim, grant·ledger, Reservation·AttemptGroup, Identity SigV4 adapter, Learning Core saga와 실제 Lattice 인프라는 이 이슈에서 제외하고 후속 vertical slice로 유지한다.
- 이슈 상태는 `해야 할 일`, 담당자는 미지정이다. 애플리케이션 코드와 계약 문서는 변경하지 않았고 테스트도 실행하지 않았다.

## 2026-08-27 통합 계약 보정 재검증

- ADR-001 inbox의 `consumerScopeId`, APPLIED·STALE disposition, `ux_inbox_identity_scope_user_revision`이 PLAN-001·AGENTS와 일치하도록 보정된 것을 확인했다.
- eligibility 409는 `EVENT_ID_CONFLICT` 전용, `COMMAND_PROCESSING`은 Reservation 전용으로 ADR·통합 계약이 정렬됐다.
- 공개 `POST /api/v1/exams`의 필수 lowercase UUID v4 `Idempotency-Key`, body 없음·기존 성공 DTO 유지, transport retry와 의도적 restart의 key/Session 구분이 AGENTS·통합 계약·Learning Core 계약 문서에 반영됐다.
- Identity ADR은 목표 transport를 VPC Lattice AWS_IAM·ECS task role·SigV4와 새 eligibility route로 바꾸고, 429·503 `Retry-After` 처리와 현재 Bearer adapter의 미이관 상태를 명시했다.
- 검토한 현행 계약 문서에서 구 eligibility route, optional 시험 생성 key, inbox disposition/index 충돌과 새 whitespace 오류는 확인되지 않았다.
- 문서 계약 보정과 실제 구현 완료는 다르다. Identity 실제 adapter는 아직 Bearer이고 `Retry-After`를 읽지 않으며, Learning Core controller는 필수 header와 Billing saga를 아직 구현하지 않았고 Billing도 PLAN-001 구현 전이다. 이는 문서에 명시된 후속 구현 항목이다.
- 아래의 이전 정합성 리뷰 섹션은 당시 발견 기록이며, 현재 판단은 이 재검증 섹션과 상단 `통합 계약 불일치 보정` 섹션이 대체한다. WORKLOG의 과거 기록은 수정하지 않는다.

## 2026-08-27 AGENTS.md 최신 계약 반영

- 저장소 루트 `AGENTS.md`를 다른 앱 서버와 같은 수준의 작업 규칙으로 보강했다.
- 명시적 요청 없이 Billing 저장소만 변경하고 Identity·Learning Core는 계약 확인용 읽기 대상으로만 사용하도록 저장소 경계를 명확히 했다.
- 현재 최소 Entitlement 구현 범위와 Apple/Google 결제·paid credit·pass·coupon·환불·사용자 Billing API의 후속 범위를 분리했다.
- TrialClaim 계약을 `claimedAt + 3년` 보존, 기간 내 재수급 차단, 만료 후 candidate 연결 purge와 재수급 허용으로 최신화했다.
- `/internal/v1/eligibility/trial/events`, strict decode·canonical digest, inbox·revision·projection Transaction과 event 수신만으로 grant하지 않는 규칙을 추가했다.
- workload 인증은 VPC Lattice `AWS_IAM` + ECS task role + SigV4로 고정하고 Identity·Learning Core·repair role의 route 권한 분리와 local/test adapter 원칙을 반영했다.
- Mongo unique index·Transaction·Reservation expiry, 보안·멱등성·범위 이탈을 중심으로 한 코드 리뷰 우선순위를 추가했다.
- Jira 키는 없고 애플리케이션 코드·ADR·PLAN·외부 API와 배포 설정은 변경하지 않았다.

## 2026-08-27 AGENTS.md Billing 전용 정교화

- `AGENTS.md`에서 무료 MVP 전체 제품 범위와 현재 즉시 구현할 PLAN-001 Trial eligibility consumer 범위를 분리했다.
- PLAN-001에는 `inbound_event_inbox`, `trial_eligibility`, strict decode·canonical digest, APPLIED·DUPLICATE·STALE·CONFLICT와 단일 Mongo Transaction만 포함하고 TrialClaim·grant·Reservation은 후속임을 명시했다.
- internal API는 앱용 `BaseResponse`를 사용하지 않고 16 KiB 제한, body 없는 204와 승인된 stable error envelope를 사용하도록 반영했다.
- public client userId 비신뢰 원칙을 유지하면서 인증된 Identity event와 Learning Core internal route가 canonical UUID userId를 body로 전달하는 서비스 계약 예외를 명시했다.
- Reservation의 `Idempotency-Key=operationId`, opaque sessionId/mockExamId, POST status와 repair-confirm 기본 차단을 후속 구현 규칙으로 추가했다.
- 승인된 collection/index 이름, explicit versioned index initializer, inbox 120일 TTL·TrialClaim 3년·Reservation audit 보존의 분리를 Mongo 규칙과 리뷰 항목에 반영했다.
- Jira 키는 없다. 애플리케이션 코드·ADR·PLAN·외부 API·인프라 설정은 변경하지 않았다.

## 2026-08-27 서비스 간 통합 계약서 추가

- `docs/contracts/BILLING_SERVICE_INTEGRATION_CONTRACT.md`를 추가해 앱·Identity·Learning Core·Billing의 책임과 호출 방향을 한 문서에서 확인할 수 있게 했다.
- Identity→Billing의 Trial eligibility event route, SigV4 principal, schema·멱등성·revision 처리, 응답과 at-least-once retry를 정리했다.
- Learning Core→Billing의 reserve→Session commit→confirm, cancel·status, confirm 응답 유실 reconciliation과 AttemptGroup 상태 event를 정리했다.
- VPC Lattice AWS_IAM·환경 격리, 공통 HTTP·식별자·오류·개인정보 경계, TrialClaim 3년 보존, local/test와 배포·변경 순서 및 E2E 체크리스트를 포함했다.
- 이 문서는 상위 흐름 안내서이며 세부 충돌 시 ADR-001·ADR-002가 최종 기준임을 명시하고 `AGENTS.md` 문서 경로에도 연결했다.
- Jira 키는 없다. 애플리케이션 코드·기존 ADR·PLAN·외부 API·AWS 리소스는 변경하지 않았다.

## 2026-08-27 통합 계약 외부 검토 지적 확인

- 첨부 검토의 4건을 Billing ADR·PLAN·AGENTS, Identity publisher 코드와 Learning Core 시험 생성 코드에 대조했다.
- ADR-001 `inbound_event_inbox`에 `consumerScopeId`와 `(producer, scope, user, revision)` unique index가 없고 disposition이 PLAN의 APPLIED·STALE 저장 모델과 다른 지적은 정확하다. PLAN-001 Phase 0에 이미 보정 작업으로 명시돼 있으며 Step 1 전에 ADR을 고쳐야 한다.
- Billing 목표는 Lattice AWS_IAM·task role·SigV4지만 Identity 실제 adapter는 audience 기반 짧은 workload Bearer credential을 사용한다. 이는 알려진 구현 공백이며 Billing local PLAN-001은 진행 가능하지만 staging publisher 활성화 전 Identity adapter·ADR·contract test를 SigV4와 새 route에 맞춰야 한다. publisher는 기본 disabled라 현재 요청을 잘못 보내는 활성 운영 상태는 아니다.
- Identity delivery가 HTTP status만 반환해 `Retry-After`를 읽지 못하는 지적은 맞다. 다만 eligibility endpoint의 구체 계약은 409를 `EVENT_ID_CONFLICT`로만 사용하고 `COMMAND_PROCESSING`을 반환하지 않으므로 error body를 읽어 두 409를 구분해야 한다는 부분은 현재 범위에 불필요하다. ADR 공통 오류표의 eligibility processing 표현은 구체 endpoint 계약과 일치하도록 정리할 필요가 있다.
- Billing 최신 계약은 앱→Learning Core 시험 생성의 필수 UUID v4 `Idempotency-Key`지만 Learning Core 과거 문서는 optional이고 실제 controller는 header를 받지 않는다. Reservation saga 전에 앱·Learning Core를 함께 전환해야 하는 실제 계약/구현 공백이다.
- 앱 종료 후 새 key·새 examId로 restart하는 규칙을 AGENTS와 통합 계약에 보강하자는 제안은 타당하다. `/internal/v1/attempt-group-events`의 hyphen은 eligibility namespace에만 적용된 no-hyphen 규칙을 위반하지 않는다.
- Jira 키는 없다. 이번 확인에서는 계약·애플리케이션·다른 서버 코드를 수정하지 않았다.

## 2026-08-27 통합 계약 불일치 보정

- ADR-001 `inbound_event_inbox`에 `consumerScopeId`, APPLIED·STALE 저장 disposition과 `ux_inbox_identity_scope_user_revision` unique partial index를 반영해 PLAN-001과 정렬했다.
- ADR 공통 409 `COMMAND_PROCESSING`을 Reservation command 전용으로 명확히 하고 eligibility 409는 `EVENT_ID_CONFLICT` 전용으로 유지했다.
- PLAN-001 Phase 0 문서 보정을 완료 상태로 갱신하고 Step 1을 ADR 계약의 실제 document/index 구현·검증 단계로 바꿨다.
- 통합 계약과 AGENTS에 공개 시험 생성의 필수 lowercase UUID v4 `Idempotency-Key`, Request Body·성공 DTO 유지, transport retry와 앱 종료 restart의 새 key·새 examId 구분을 반영했다.
- Identity ADR-002의 이전 Bearer route를 Billing C3-D Lattice AWS_IAM·task role·SigV4와 `/internal/v1/eligibility/trial/events` 목표로 보정하고 429·503 `Retry-After` 처리 계약을 추가했다. 관련 기존 Jira는 `TMI-95`이며 Jira는 변경하지 않았다.
- Learning Core의 Billing 계약 검토 문서에서 optional header를 최신 필수 header 계약으로 보정했다. 실제 Identity adapter와 Learning Core controller·Reservation saga 코드는 아직 변경하지 않았다.
- 애플리케이션 코드·AWS 리소스·외부 배포는 변경하지 않았다.

## 현재 구성

- Java 21, Spring Boot 3.4.2, Gradle Groovy 기반 초기 프로젝트다.
- MongoDB, Web, Validation, Security Resource Server, Actuator 의존성이 준비되어 있다.
- 애플리케이션 이름은 `app-back-end-billing`, 기본 로컬 포트는 `8082`다.
- MongoDB 연결은 `MONGODB_URI` 환경변수로 재정의한다.
- `/actuator/health`만 공개하고 그 밖의 요청은 인증 계약 구현 전까지 차단한다.
- 원격 저장소 `origin`은 `https://github.com/Too-Much-I/app-back-end-billing.git`이다.

## 확정된 경계

- Billing은 상품, 결제 검증·원장, entitlement, credit, pass, TrialClaim, Reservation을 소유한다.
- Identity는 사용자 계정과 토큰 발급을 소유한다.
- Learning Core는 시험 Session과 학습·채점 상태를 소유한다.
- Reservation 기본 흐름은 `reserve → Learning Core Session commit → confirm`, `RESERVED` TTL은 5분이다.
- raw phone과 실제 결제 credential·receipt·token 원문은 저장소, 로그, 작업 문서에 남기지 않는다.
- Billing 계약의 단일 기준은 `docs/codex/CONTRACT_DECISIONS.md`다. 앞으로 Billing 관련 결정과 작업기록은 Billing 저장소 `docs`에만 기록한다.

## 현재 계약 결정 단계

- 결제·credit·pass·coupon·환불 구현은 후속 릴리스로 연기됐다. 기존 계약은 삭제하지 않고 동결한다.
- verified-phone당 무료 모의고사 1회를 위해 Billing은 최소 Entitlement(`TrialClaim`, `FREE_EXAM_ONCE`, reserve/confirm/cancel, reconciliation)를 먼저 구현한다.
- 기존 확정사항은 Billing의 `CONTRACT_DECISIONS.md`로 이관했다.
- 2026-08-26 사용자가 무료 최소 Entitlement 권장안을 전부 승인했고 후속 인프라 확인 뒤 C3-D VPC Lattice + ECS task role + SigV4 + AWS_IAM을 최종 승인했다. 사용자 Billing API와 사용자 Billing audience(C1/C2)는 결제 단계까지 보류한다.
- Billing production 배포 전 production/staging 두 ECS cluster와 환경별 데이터·credential·IAM·Lattice/SG 격리를 준비하는 것이 확정된 배포 gate다. 현재 운영 트래픽을 처리하는 `tosunsaeng-staging-cluster`는 새 production cluster로 트래픽을 안전하게 전환한 뒤 최종 staging으로 사용한다.
- 결제 adapter 전 C9~C11(store 상품 연결, credit 만료, 환불/chargeback)의 승인이 필요하다.
- 보상 전 C12(출석/coupon)의 승인이 필요하다. C13은 `claimedAt + 3년` 보존, 기간 안의 기존 Claim 유지와 만료 후 같은 번호 재수급 허용으로 확정됐다.

## 아직 구현하지 않은 항목

- Billing 도메인 모델, API, Mongo index와 transaction
- Lattice/SigV4 workload 인증과 reserve/confirm/cancel/status 계약 구현
- Apple/Google server verification adapter와 notification 처리
- reconciliation scheduler와 운영 관측성
- Lattice service network/service/listener/target, ECS task role·auth policy·security group

## 2026-08-26 ADR-002 VPC Lattice·ECS SigV4·환경 이관 기준 작성

- `docs/adr/ADR-002-vpc-lattice-ecs-sigv4-and-environment-migration.md`를 승인된 C3-D와 production/staging 이관의 구현 기준으로 추가했다.
- production/staging 별도 Lattice service network, Billing HTTPS 443 listener→HTTP 8082 ECS target, `AWS_IAM`, exact task-role route policy·SG direct bypass 차단을 고정했다.
- Identity role은 eligibility event, Learning Core role은 reservation/status/AttemptGroup event만 호출하고 반대 환경 service와 repair route는 deny한다.
- Java client는 AWS SDK v2 `DefaultCredentialsProvider`, SigV4 service `vpc-lattice-svcs`, region `ap-northeast-2`, exact generated DNS·1초 connect/3초 request timeout·same-key caller retry를 사용한다.
- 현 cluster의 운영 트래픽은 새 production을 먼저 구성·병행 검증하고 전환한다. 전환 후 기존 cluster의 production DB/Secret/role 참조를 제거하고 staging으로 전환한다.
- local shell에 AWS credential이 없어 실제 role/VPC/subnet/SG/Lattice ID는 조회하지 못했다. 임의 ARN을 적지 않고 배포 전 read-only inventory/infra output으로 주입하도록 했다.
- AWS/GitHub Actions·application code는 변경하지 않았다.

## 다음 권장 작업

- 상세 구현 계획은 `docs/plans/PLAN-001-trial-eligibility-event-consumer.md`에 작성했다.
- 즉시 다음 작업은 ADR-001의 Identity `PhoneEligibilityBindingVerified`/`Revoked` event inbox·revision high-water·current binding vertical slice 구현이다.
- v1 DTO/validation, eventId·payload digest 멱등성, Mongo Transaction, unique index, stale/duplicate/conflict response와 fail-closed security test를 함께 구현한다.
- 이 단계에서는 AWS Lattice를 직접 호출하지 않고 inbound workload 경계를 adapter/test principal로 격리해 local test한다.
- 그다음은 TrialClaim·candidate alias·subject link·free grant/ledger와 INITIAL reserve Transaction, 이후 confirm/cancel/status·5분 expiry·AttemptGroup이다.
- Identity·Learning Core SigV4 client와 실제 Lattice/IAM/SG는 Billing API vertical slice가 안정된 후 연동한다.
- credential이 있는 AWS 환경의 role/VPC/subnet/SG read-only inventory와 새 production/staging 이관은 production 배포 준비 gate로 남겨두되, application 개발을 차단하지 않는다.
- 계획 작성 중 same user·scope·revision/different event conflict를 race에서도 강제하기 위해 ADR-001 inbox에 `consumerScopeId`와 `ux_inbox_identity_scope_user_revision` unique partial index를 보강할 필요를 확인했다. 이 문서 보정은 구현 Step 1에서 코드와 함께 수행한다.
- 사용자 승인으로 eligibility 내부 API URL은 `/internal/v1/eligibility/{kind}/...` namespace로 통합한다. 현 Trial route는 `/internal/v1/eligibility/trial/events`이고, 향후 실제 API가 필요할 때 paid는 `/internal/v1/eligibility/paid/...`, coupon은 `/internal/v1/eligibility/coupon/...`를 사용한다. URL namespace만 공통화하며 종류별 DTO·권한·멱등성·aggregate는 분리한다. URL segment에는 hyphen을 사용하지 않는다.
- Trial current projection collection은 `trial_eligibility`, Java package는 `trialeligibility`로 유지한다. index는 `ux_trial_scope_user`, `ix_trial_key_version`, metric은 `billing.trial_eligibility.events`를 사용하고 Billing class와 PLAN 제목·fixture는 `TrialEligibility*` 계열을 사용한다. Identity에 이미 구현된 wire event type `PhoneEligibilityBindingVerified`/`Revoked`는 schema v1 호환성을 위해 유지한다. ADR-001, ADR-002, PLAN-001에 확정 route를 반영했다.
- PLAN-001의 strict decoder는 입력을 저장하기 전에 JSON 구조·타입·계약 상수·event별 field·candidate·시간 순서를 검증하고, configured expected `consumerScopeId`와 exact match하지 않으면 422로 거절한다. canonical digest는 검증된 값을 정렬·정규화한 canonical JSON의 SHA-256이므로 property/candidate 순서와 whitespace만 다른 재전송은 동일 event로 수렴하며 의미가 바뀐 payload는 conflict로 판별한다. raw payload는 저장하지 않는다.
- canonical digest pipeline은 `수신 → strict decode → 값 검증 → 정렬·정규화 → canonical JSON → SHA-256 → digest 저장` 순서다. 이는 이벤트 내용을 암호화하거나 무료권을 차감하는 절차가 아니라, 동일 eventId 재전송의 내용 동일 여부를 비교하기 위한 결정적 지문 생성 절차다.
- PLAN-001은 무료권 지급 구현이 아니라 Identity의 verified/revoked phone eligibility event를 Billing에 안전하게 동기화하는 첫 vertical slice다. Lattice 인증, 16 KiB 제한·strict decode, digest, inbox 멱등성, revision high-water current projection을 거쳐 inbox와 `trial_eligibility`를 한 Mongo Transaction으로 반영한다. 결과는 APPLIED·DUPLICATE·STALE·CONFLICT로 수렴하며 TrialClaim·grant·Reservation은 후속 단계다.

## 다음 작업 전 확인할 사항

- internal API wire DTO, Mongo collection/index/Transaction과 contract test 기준은 `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`로 구체화했다.
- Lattice/ECS/SigV4·환경 이관 기술 기준은 ADR-002로 구체화했다. 남은 작업은 credential이 있는 환경의 read-only AWS inventory와 ADR-001 Identity event consumer vertical slice 구현이다.
- 그다음 store 상품·만료·환불 정책 C9~C11을 확정한다.
- Billing 관련 후속 기록은 Learning Core가 아니라 이 저장소의 `docs/codex`에만 남긴다.
- Mongo transaction 통합 테스트는 replica set Testcontainers로 설계한다.

## 2026-08-25 구현 시작 분석

- 현재 Billing 코드는 health-only 공개 보안 골격뿐이며 도메인 entity, repository, controller와 service는 아직 없다.
- Identity의 `PhoneEligibilityBinding` schema v1 producer와 lease/retry/dead-letter publisher는 구현돼 있다. Billing에는 event inbox, revision high-water, current verified binding consumer와 staging E2E가 없다.
- Learning Core의 `POST /api/v1/exams`는 현재 Billing reserve 없이 기존 진행 Session을 abandon하고 새 `ExamSession`을 즉시 insert한다. Billing 연동 시 이 경계를 `reserve → Session commit → confirm` saga와 동일-operation 복구로 바꿔야 한다.
- 2026-08-25 분석 당시 1차 구현 우선순위는 (1) 최소 계약 승인, (2) workload 보안 경계, (3) phone eligibility consumer의 inbox·high-water·binding Transaction, (4) 무료 1회 TrialClaim·grant ledger, (5) 멱등 Reservation reserve/confirm/cancel/status와 5분 만료, (6) reconciliation·관측성, (7) Learning Core contract client와 staging E2E였다. 최소 계약 승인은 2026-08-26 완료됐다.
- Apple/Google 결제 검증, paid credit, unlimited pass, coupon, 추천·출석 보상과 환불은 최소 무료 Entitlement 출시 뒤의 후속 단계다.
- 2026-08-25 당시 미확정이던 사용자 Billing API·workload 방식·멱등성·오류·AttemptGroup 정책은 2026-08-26 승인됐다. exact wire DTO와 Mongo 설계는 ADR-001로 구체화했고 환경별 Lattice 리소스·role 값은 아직 남아 있다. TrialClaim 보존기간은 같은 날 `claimedAt + 3년`으로 확정됐다.
- 현재 프로젝트 골격과 문서가 모두 Git 미추적 상태다. 기능 구현 전에 사용자가 초기 기준선을 commit해 프로젝트 생성 diff와 첫 기능 diff를 분리해야 한다.
- Jira 키는 없다. 다음 권장 작업은 C1~C8을 한꺼번에 넓게 구현하는 것이 아니라 phone binding 수신부터 무료시험 reservation까지의 vertical slice를 별도 Jira들로 나누는 것이다.

## 2026-08-25 결제 제외 무료 모의고사 1회 구현 백로그

- 이번 출시 범위는 verified-phone candidate당 `FREE_EXAM_ONCE` 1회뿐이다. Apple/Google, 유료 credit, unlimited pass, coupon, 추천·출석, 환불은 구현하지 않는다.
- 첫 reserve의 Mongo transaction 안에서 현재 verified binding을 확인하고, `(benefitScopedCandidate, FREE_EXAM_ONCE)` unique `TrialClaim`, 무료 grant/ledger와 `Reservation`을 함께 만든다. 계정 생성이나 전화번호 인증만으로 사용자 balance를 직접 증가시키지 않는다.
- reserve가 취소·만료되면 allocation은 무료 grant로 정확히 돌아가지만 `TrialClaim` 자체는 삭제하거나 다시 열지 않는다. 계정 merge·탈퇴·재가입에도 claim은 유지하고 현재 verified binding에 맞춰 entitlement owner만 이전한다.
- `Reservation` 기록은 Mongo TTL로 삭제하지 않는다. `expiresAt`을 기준으로 `RESERVED`만 명시적으로 `EXPIRED` 처리하며, `CONFIRMED` consumption과 audit ledger는 보존한다.
- 최소 데이터 경계는 phone eligibility event inbox/high-water/current binding, `TrialClaim`, free entitlement grant/ledger, allocation, `Reservation`, `AttemptGroup`, command idempotency record다. raw phone, last4와 Identity fingerprint는 저장하지 않는다.
- 최소 내부 API는 Learning Core용 reserve/confirm/cancel/status다. 동일 user·operation의 재호출은 같은 결과를 반환하고 payload가 다르면 conflict로 거절한다. 앱에 무료 사용 가능 여부를 미리 보여줄 필요가 없다면 Billing 사용자 조회 API와 Billing audience 추가는 결제 출시까지 미룰 수 있다.
- Learning Core 시험 생성은 같은 operation ID로 `reserve → Session commit → confirm`을 실행한다. Session commit 실패는 cancel하고 confirm 결과가 불명이면 Session을 `ENTITLEMENT_CONFIRMING`으로 유지해 status/retry/reconciliation으로 수렴시킨다.
- 구현 순서는 (1) 승인된 계약의 wire DTO·index 구체화, (2) Lattice/SigV4와 fail-closed 보안, (3) Identity event consumer·replay, (4) TrialClaim·ledger·Mongo unique/transaction, (5) Reservation API와 expiry, (6) Learning Core saga, (7) 양방향 reconciliation·관측성, (8) replica-set Testcontainers와 staging E2E다.
- 출시 조건은 동시 요청에도 한 candidate의 claim이 하나뿐이고, 같은-key retry가 중복 Session/소비를 만들지 않으며, Session commit 실패·confirm 응답 유실·5분 만료·owner 이전이 자동 복구되고, eligibility event가 없거나 인증이 잘못된 요청은 fail-closed 되는 것이다.
- C1/C2는 Billing 사용자 API를 이번에 노출할 때만 출시 차단 계약이다. C6의 전체 entitlement 우선순위는 결제 시 확장하되 이번 resolver는 `FREE_EXAM_ONCE`만 선택한다. 결제 관련 C9~C12는 후속 범위다.

## 2026-08-25 무료 Entitlement 계약 결정 진행

- 사용자와 선택할 이번 범위는 C1~C8과 C13이며, 결제·credit·환불·출석·coupon에 관한 C9~C12는 결정과 구현을 모두 미룬다.
- 즉시 확정이 필요한 출시 차단 항목은 workload 인증(C3), 멱등성(C4), 오류 계약(C5), 동시성과 confirm 불명 복구(C7), AttemptGroup 완료(C8), TrialClaim 보존·번호 재할당(C13)이다.
- Billing 사용자 조회 API를 이번에 제공하지 않으면 앱 호출 경로와 사용자 token audience(C1/C2)는 결제 단계까지 보류할 수 있다. 무료권만 존재하는 이번 resolver는 C6 전체 우선순위를 구현하지 않고 `FREE_EXAM_ONCE`만 서버가 선택할 수 있다.
- 이때 제안한 C1/C2 보류, 필수 앱 key, 행동별 오류, 무료권 자동 선택, C7-A, C8-A, C13-A와 기존 claim 유지 묶음은 2026-08-26 사용자 승인으로 확정됐고 workload 방식은 최종적으로 C3-D Lattice/SigV4로 대체 확정됐다.

## 2026-08-26 workload JWT와 무료권 소비 시점 분석

- Identity는 사용자 RS256 Access Token을 발급하지만 workload issuer는 구현하지 않았다. workload JWT 대신 ECS task role과 Lattice SigV4를 사용하는 C3-D가 후속 승인돼 Identity client-credentials 구현은 현재 범위에서 제외한다.
- 무료권은 전화번호 검증 event 수신이나 시험 완료 때 차감하지 않는다. 첫 `reserve` Transaction에서 current verified binding, unique `TrialClaim`, `FREE_EXAM_ONCE` grant와 allocation을 만들고 사용권을 `RESERVED`로 잠근다.
- Learning Core가 durable Session commit에 성공한 뒤 `confirm`이 Reservation과 ledger를 `CONFIRMED`/`CONSUMED`로 최종 전환한다. Session commit 실패 또는 commit 전 5분 만료는 allocation을 복구하지만 `TrialClaim`은 삭제하지 않는다.
- confirm 뒤에는 일반 cancel로 무료권을 되돌리지 않는다. confirm 결과가 불명이면 Session을 성공으로 노출하거나 즉시 삭제하지 않고 status retry와 reconciliation으로 최종 상태를 확인한다.
- AttemptGroup의 `COMPLETED`는 차감 시점이 아니라 같은 consumption으로 추가 차감 없이 restart할 수 있는 권리를 닫는 시점이다. 결과 생성이 최종 실패하면 확정된 C8-A에 따라 `RETAKE_AVAILABLE`로 전환해 같은 consumption을 재사용한다.

## 2026-08-26 무료 최소 Entitlement 계약 승인

- 사용자가 앞서 설명한 권장안을 전부 승인했다. 계약 단일 기준인 `CONTRACT_DECISIONS.md`에 승인 요약과 상태 전이를 추가하고 선택지의 해당 항목을 확정으로 변경했다.
- 이번 릴리스는 앱 → Learning Core → Billing 내부 호출만 사용하며 Billing 사용자 API와 사용자 token Billing audience는 결제 단계까지 보류한다.
- Identity eligibility event inbox/high-water/fail-closed와 첫 reserve Transaction의 unique TrialClaim·무료 grant·Reservation 생성을 확정했다.
- Learning Core와 Identity의 Billing 내부 호출은 C3-D Lattice/SigV4로 확정했다. task role별 최소 route 권한을 적용하고 privileged repair-confirm은 별도 운영 role이다.
- UUID v4 `Idempotency-Key`는 필수이고 terminal command는 우선 7일 보존한다. 행동별 402/409/429/503 오류와 same-key retry를 확정했다.
- 무료권은 서버가 자동 선택하며 reserve에서 잠그고 Session durable commit 뒤 confirm에서 최종 소비한다. confirm 전 실패는 allocation만 복구하고 TrialClaim은 유지하며 confirm 결과 불명은 status/reconciliation으로 복구한다.
- 사용자당 OPEN AttemptGroup·active Session·생성 command는 각각 하나다. 결과 조회 가능 시 group을 완료하고 결과 생성 최종 실패는 같은 consumption의 `RETAKE_AVAILABLE`로 복구한다.
- TrialClaim dedupe 연결은 `claimedAt + 3년` 보존하고 그 기간의 번호 재할당에도 기존 Claim을 유지한다. 3년 만료 뒤에는 같은 번호의 새 Claim을 허용한다. Lattice/SigV4 staging negative test는 production gate로 남는다.

## 2026-08-26 승인 후 남은 구체화 작업

- workload trust는 Lattice `AWS_IAM` auth policy와 ECS task role ARN으로 관리한다. Learning Core role은 reservation route, Identity role은 eligibility event route만 허용하고 wildcard principal은 금지한다.
- Billing은 Lattice를 거친 ingress만 허용하고 direct task·old ALB 우회 경로를 security group과 routing으로 차단한다. privileged repair-confirm은 별도 운영 role과 route policy로 분리한다.
- TrialClaim의 `retentionExpiresAt`은 immutable `claimedAt + 3년`이다. 만료 alias는 물리 purge 전에도 dedupe matching에서 제외하며 candidate/keyVersion과 사용자·source event 연결을 삭제·비식별화한다. daily purge·24시간 삭제 SLA·35일 rolling backup과 restore-before-traffic purge가 확정됐고 candidate key reference lifecycle은 구현 ADR에 반영한다.
- API DTO 설계는 internal reserve/confirm/cancel/status endpoint, 필수 `Idempotency-Key`, canonical userId·operationId·mockExamId·sessionId 연결, payload hash conflict, stable 오류 envelope와 버전 정책을 OpenAPI/contract test로 고정하는 작업이다.
- Mongo index 설계는 eventId inbox unique, user+scope current binding, candidate alias+benefit unique TrialClaim, user+operation Reservation unique, 사용자당 단일 OPEN group/active command partial unique, allocation·ledger dedupe와 expiry scan index를 정의하는 작업이다. Reservation audit 문서에는 TTL delete index를 두지 않는다.
- 즉시 진행 가능한 작업은 API DTO·collection/index ADR, Lattice/SigV4 client·ingress 보안 설계와 mock contract test다. TrialClaim 기간 숫자는 확정됐고 purge/anonymization 구현 명세만 남았다.

## 2026-08-26 AWS ECS 확인에 따른 C3 재검토

- 사용자가 실제 배포 플랫폼이 AWS ECS라고 확인했다. ECS task role은 컨테이너에 자동 회전되는 AWS 임시 credential을 제공하지만 일반 OIDC workload JWT, issuer와 JWKS를 자동 제공하지 않는다.
- 따라서 앞서 승인한 C3의 `배포 플랫폼 발급 JWT` 전제만 재검토 상태로 변경했다. Identity eligibility event, TrialClaim, Reservation, 멱등성, 오류, 소비·완료와 보존 계약은 그대로 확정 상태다.
- Learning Core → Billing 경로가 VPC Lattice 또는 API Gateway를 거친다면 task role + SigV4 + `AWS_IAM` route policy가 1차 권장이다. 별도 JWT issuer·JWKS·client secret이 필요 없고 IAM role별 최소 권한을 적용할 수 있다.
- 내부 ALB, Cloud Map 또는 ECS Service Connect로 애플리케이션을 직접 호출한다면 이 계층은 task-role SigV4 인증을 자동 검증하지 않는다. 이 경우 별도 OIDC/workload JWT issuer, 애플리케이션 SigV4 검증 adapter 또는 mTLS 중 하나가 추가로 필요하다.
- 후속 확인에서 Identity·Learning Core는 Load Balancer, Service Connect 없음, Billing 미배포, Lattice 없음으로 확인됐고 C3-D greenfield Lattice 구성이 최종 승인됐다.

## 2026-08-26 기존 Identity·Learning Core 인증 방식 대조

- 실제 구현된 공통 인증은 Identity가 사용자 RS256 Access Token을 발급하고 Learning Core가 Identity issuer·JWKS·audience·시간·UUID subject를 Spring OAuth2 Resource Server로 로컬 검증하는 방식이다. 요청마다 Identity introspection을 호출하거나 shared HMAC secret을 사용하지 않는다.
- Identity의 phone eligibility와 user merge publisher는 Bearer workload credential port와 HTTP adapter까지 있으나 `WorkloadIdentityCredentialProvider` production 구현이 없고 publisher flag가 기본 false이므로 현재 재사용 가능한 서버 간 인증 구현은 아니다.
- Learning Core → Python AI 요청은 `Idempotency-Key`만 설정하고 Authorization을 보내지 않는다. 결제·entitlement 차감처럼 권한이 필요한 Billing 내부 API의 선례로 사용하지 않는다.
- 기존 구조와 동일하게 맞추려면 사용자 Access Token을 전달하는 것이 아니라 Identity RS256 issuer/JWKS 메커니즘을 workload 전용 token profile로 확장한다. Billing은 `token_use=workload`, Billing audience, allowlist된 service subject, 5분 TTL과 endpoint scope를 추가 검증한다.
- 이 E안은 비교 대안으로만 남고 후속 C3-D 승인으로 현재 구현 범위에서 제외됐다.

## 2026-08-26 C3 세 방식 비교

- 내부 ECS-to-ECS 호출만 고려한 전체 권장 순서는 VPC Lattice + task role SigV4, Identity-issued workload JWT, API Gateway + AWS_IAM이다.
- VPC Lattice는 task role 임시 credential과 IAM auth policy를 사용해 별도 client secret·JWT issuer 없이 서비스 identity와 network 경로를 함께 관리하고 Identity 장애가 Billing 호출로 전파되지 않는다는 장점이 있다. 서비스 network·route·auth policy·direct bypass 차단과 AWS 종속성이 비용이다.
- Identity workload JWT는 현재 RS256/JWKS/Spring Resource Server 패턴을 재사용하고 direct ALB/Service Connect 경로에도 적용할 수 있으며 cloud portability가 높다. 반면 workload client-credentials·Secret rotation·token endpoint/cache를 세 서비스에 구현하고 Identity 장애와 key 운영을 감수해야 한다.
- API Gateway + AWS_IAM은 task role SigV4, route별 IAM, throttling·WAF·stage·access log가 강점이지만 순수 내부 호출에는 VPC Link/private API, route/stage와 direct bypass 차단이 추가되고 비용·latency·운영 구성이 가장 커질 수 있다.
- Billing이 내부 ECS 서비스에만 쓰이고 새 service network 구성이 가능하면 VPC Lattice를 최종 권장한다. 기존 internal ALB/Service Connect를 유지해야 하거나 AWS 인프라 변경을 피해야 하면 Identity workload JWT를 권장한다. API Gateway는 외부·파트너 공개 또는 조직 표준 gateway가 이미 있을 때 선택한다.

## 2026-08-26 ECS 현재 경로 확인과 Lattice 전환 절차

- 현재 경로는 ECS Console의 서비스 `Load balancing`, `Service Connect`, `Service discovery`, `VPC Lattice configuration`과 AWS CLI `ecs describe-services`의 loadBalancers, serviceConnectConfiguration, serviceRegistries, vpcLatticeConfigurations를 확인해 판별한다.
- EC2 Load Balancer의 scheme이 `internal`이고 ECS 서비스가 해당 target group에 연결돼 있으면 internal ALB 경로다. Service Connect enabled와 namespace/clientAliases가 있으면 Service Connect 경로이며 Cloud Map registry만 있으면 service discovery 직접 경로일 수 있다.
- Lattice 전환은 기존 경로를 즉시 제거하지 않고 task definition named port, Billing Lattice service/listener/target, VPC service network association, Learning Core·Identity task role auth policy와 SigV4 client를 먼저 병렬 배포한다.
- staging에서 허용 role의 서명 요청 성공, unsigned·다른 role 403, 동일 idempotency retry, direct Billing endpoint 우회 차단을 검증한 뒤 `BILLING_BASE_URL`을 Lattice DNS로 전환한다. rollback 기간 뒤 Billing 전용 old ALB/Service Connect route만 제거하고 공유 ALB는 삭제하지 않는다.
- 내부와 사용자 API를 같은 ALB에서 제공 중이면 old ingress를 일괄 차단하지 않는다. `/internal/**` 전용 port/service 또는 routing·security boundary를 먼저 분리해야 한다.

## 2026-08-26 실제 ECS 현황 확인

- 사용자 확인 결과 Identity와 Learning Core는 Load Balancer를 사용하고 Service Connect는 사용하지 않는다. Billing은 아직 ECS에 배포되지 않았고 VPC Lattice도 아직 없다.
- 따라서 기존 Identity·Learning Core inbound Load Balancer를 이전하지 않는다. 두 서비스의 사용자 API 경로는 그대로 두고 outbound Billing 호출만 새 Lattice DNS와 SigV4를 사용한다.
- Billing은 처음부터 public/internal ALB 없이 ECS named port를 VPC Lattice service target에 연결하는 greenfield 구성을 권장한다. Service network를 두 서비스 task가 위치한 VPC에 associate하고 Billing direct task 접근은 security group으로 막는다.
- Learning Core task role에는 reservation reserve/confirm/cancel/status invoke, Identity task role에는 phone eligibility event invoke만 허용한다. repair-confirm은 별도 운영 role로 둔다.
- Billing 애플리케이션은 Lattice auth policy 통과 경로만 내부 API에 허용하도록 배포 profile과 ingress header/identity 검증을 구성하고, unsigned·wrong role·direct path negative test를 production gate로 둔다.
- 향후 앱이 Billing을 직접 호출하는 결제 사용자 API가 생기면 별도 public ALB/API Gateway 경로를 추가하고 `/internal/**` Lattice 경계와 분리한다.
- 현재 조건에서 C3-D VPC Lattice + ECS task role + SigV4가 사용자 승인으로 최종 확정됐다.

## 2026-08-26 C3-D 최종 승인

- 사용자가 `C3-D`를 명시 승인했다. VPC Lattice + ECS task role + SigV4 + AWS_IAM이 Billing workload 인증의 확정 계약이다.
- Identity·Learning Core 기존 사용자 inbound Load Balancer는 유지하고, 미배포 Billing만 ALB 없이 Lattice service target으로 새 배포한다.
- Learning Core와 Identity는 각자 분리된 task role의 임시 credential로 요청을 SigV4 서명한다. 별도 workload JWT issuer·JWKS·client secret과 API Gateway는 이번 내부 API 범위에 만들지 않는다.
- Lattice auth policy는 Learning Core reservation route와 Identity eligibility event route를 분리한다. repair-confirm은 별도 운영 role이며 direct Billing task/old route 우회는 차단한다.
- production gate는 unsigned 요청, wrong role, 권한 없는 route와 direct path가 모두 실패하고 same-key retry와 confirm reconciliation이 staging에서 성공하는 것이다.

## 2026-08-26 C3-D 승인 후 잔여 확정사항 감사

- 무료 모의고사 MVP의 핵심 제품 계약은 구현을 시작할 수 있을 정도로 확정됐다. workload 인증 방식, 무료권 잠금·소비 시점, 멱등성, 오류, 동시성, 재응시와 번호 재할당 정책을 다시 선택할 필요는 없다.
- C13의 TrialClaim 보존기간, 기산점, 만료 후 재수급, 물리 purge SLA와 backup 수명까지 후속 승인으로 해결됐다.
- 구현 전에 ADR로 고정할 기술 명세는 internal API wire DTO·버전·오류 envelope, Mongo collection/index/transaction, Lattice 리소스·실제 task role ARN·route auth policy·security group, SigV4 signer와 local/test 대체 구현이다. 이는 승인된 정책을 바꾸지 않는 한 개발자가 권장안으로 작성해 검토받을 수 있다.
- 운영 전 수치로 고정할 항목은 reconciliation 주기·재시도/경보 기준, repair-confirm 운영 절차, event inbox/tombstone과 audit/command 보존·삭제 job이다. Identity publisher의 기존 retry와 replay 계약은 가능한 한 재사용한다.
- 결제 C9~C11, 출석·coupon C12, 사용자 Billing API·audience C1/C2와 전체 entitlement 우선순위는 현재 무료 MVP의 차단 사항이 아니며 해당 기능 착수 시 확정한다.

## 2026-08-26 TrialClaim 보존기간 선택 준비

- 유한 보존기간이 끝나 candidate/alias를 삭제하면 같은 전화번호의 재등장을 판별할 수 없으므로 이후 무료권 재수급을 허용하게 된다. 반대로 전화번호당 영구 1회를 보장하려면 무료시험 프로그램 운영 중 candidate를 계속 보존해야 한다.
- 선택지는 `claimedAt + 3년`, `claimedAt + 5년`, `무료시험 프로그램 종료 + 1년`으로 구분한다. 개인정보 최소화와 실용적 abuse 방지의 균형안은 3년, 기존의 강한 phone당 1회 정책을 가장 충실히 지키는 안은 프로그램 종료 + 1년이다.
- 유한 기간을 선택하면 기간 종료 후 같은 번호의 새 Claim을 명시적으로 허용하도록 제품 문구를 바꿔야 한다. 만료 시 candidate alias·keyVersion·user/event 연결은 삭제하고, 통계에 필요한 비연결 상태·시각만 비식별화해 남기는 방식을 권장한다.
- 이 절을 작성한 시점에는 사용자 최종 선택 전이었으며, 바로 아래 후속 승인에서 `claimedAt + 3년`으로 확정됐다.

## 2026-08-26 TrialClaim 3년 보존 최종 승인

- 사용자가 보존기간 선택지 B인 `claimedAt + 3년`을 승인했다. 이는 과거 C13의 선택지 문자인 B가 아니라 이번 보존기간 세부 선택지 B를 뜻하며, 계약상 C13-A의 구체적인 기간으로 반영했다.
- `claimedAt`은 Claim 최초 생성 시 고정하고 로그인·merge·탈퇴·revoke·cancel·expiry·restart로 갱신하지 않는다. `retentionExpiresAt`부터 만료 alias는 물리 삭제 여부와 관계없이 dedupe matching에서 제외한다.
- 3년 동안은 번호 재할당, 탈퇴와 재가입에도 기존 Claim이 무료권 재수급을 차단한다. 3년 뒤에는 candidate 연결을 제거하고 같은 번호가 다시 verified되면 새 Claim을 허용한다.
- candidate alias, keyVersion, user와 source event 연결은 삭제·비식별화한다. 개인과 다시 연결할 수 없는 최소 집계만 남길 수 있으며, raw phone·last4·전화번호 암호문은 계속 저장하지 않는다.
- 따라서 제품 계약은 더 이상 `phone당 평생 1회`가 아니라 `verified-phone candidate당 3년 내 1회`다. daily purge·24시간 삭제 SLA와 35일 rolling backup도 후속 승인으로 확정됐다.

## 2026-08-26 TrialClaim 물리 삭제·백업 주기 결정 준비

- 이 값들은 도메인 구현 착수의 선행 조건은 아니지만 C13 보존 계약과 production 운영을 완결하는 상수이므로 지금 확정하는 것이 적절하다.
- 권장안은 logical expiry 즉시 dedupe 제외, 매일 purge 실행과 만료 후 24시간 이내 active DB 연결정보 삭제, 최대 35일 rolling backup 보존이다.
- 복구한 backup은 사용자 트래픽에 연결하기 전에 현재 시각 기준 만료 purge를 먼저 실행해야 하며, backup 속 만료 candidate를 일반 조회·dedupe에 다시 노출해서는 안 된다.
- purge 실패는 재시도하고 24시간 SLA 초과 시 경보한다. 삭제 증적에는 처리 건수·실행 시각·성공 여부만 남기고 candidate, userId와 source event를 기록하지 않는다.
- 7일 backup은 개인정보 최소화에는 유리하지만 장기 장애 복구 여유가 작고, 90일 backup은 복구 여유가 크지만 만료 정보 잔존기간과 접근통제 부담이 커진다. 이 비교 뒤 사용자가 바로 아래 후속 설명을 거쳐 35일 권장안을 최종 승인했다.

## 2026-08-26 TrialClaim purge 대상 명확화

- 3년 만료 시 삭제하는 핵심은 `(benefitType, keyVersion, candidate) → trialClaimId` dedupe alias와 Claim에 붙은 user/source event/phone binding 연결이다. 이 연결을 제거해야 과거 Claim을 같은 번호와 다시 대조할 수 없고 확정된 3년 뒤 재수급이 가능하다.
- `TrialClaim` 자체는 개인·candidate·Reservation·Session으로 역추적할 수 없는 익명 tombstone으로 축약할 수 있다. 남길 수 있는 값은 benefit type, terminal status, 거친 claimed/expired 시각과 식별자 없는 purge 통계뿐이다.
- 시험 Session, 문제, 답안, 점수, 피드백과 Summary는 Learning Core 소유이므로 이 purge 대상이 아니다. Billing의 금액·사용권 감사 event도 candidate/user mapping을 분리 제거한 비식별 core만 보존할 수 있다.
- 아직 유효한 Identity current verified binding은 새 Claim 자격 확인에 필요하므로 TrialClaim 3년 만료만으로 삭제하지 않는다. revoke·phone 교체·탈퇴 event의 별도 lifecycle을 따른다.
- immutable ledger나 Reservation에 userId·candidate를 직접 영구 저장하면 이 삭제 계약을 지킬 수 없다. 구현 ADR은 변경 불가능한 audit core와 삭제 가능한 subject/alias mapping을 분리해야 한다.

## 2026-08-26 24시간 purge·35일 backup 의미 설명

- candidate 삭제를 위해 별도 backup을 만드는 계약이 아니다. MongoDB 전체를 장애·오삭제 복구용으로 주기적으로 snapshot/continuous backup하는 일반 운영 백업에, 삭제 전 candidate 연결정보가 과거 시점 데이터로 잠시 포함될 수 있다는 의미다.
- 예를 들어 2026-09-01에 Claim이 생기면 2029-09-01부터 alias를 dedupe에 사용하지 않고, daily purge가 늦어도 2029-09-02까지 운영 DB의 candidate→Claim 및 Claim→user/event 연결을 제거한다.
- 2029-09-02 이전에 생성된 DB backup에는 삭제 전 연결이 들어 있을 수 있다. 35일 rolling을 선택하면 해당 backup 자체가 생성 후 최대 35일 뒤 만료되며, 애플리케이션은 평소 backup을 조회하지 않고 제한된 운영자만 재해 복구에 사용한다.
- 오래된 backup을 복구하면 삭제됐던 연결이 되살아날 수 있으므로, 격리된 복구 환경에서 현재 시각 기준 purge를 다시 실행한 후에만 사용자 트래픽을 연결한다.
- 이 설명 시점에는 24시간/35일 수치가 권장안이었으며, 후속 사용자 승인으로 `운영 DB 식별 연결 삭제 SLA 24시간 + 전체 DB 재해복구 backup 최대 35일`이 확정됐다.

## 2026-08-26 TrialClaim purge·backup 운영 계약 최종 승인

- logical expiry는 `claimedAt + 3년`에 즉시 적용하고 만료 alias를 dedupe에서 제외한다.
- purge job은 매일 실행하며 만료 후 24시간 안에 운영 MongoDB의 candidate alias와 erasable user/event mapping을 삭제하고 Claim을 비식별 tombstone으로 전환한다.
- 24시간 SLA를 넘긴 항목은 운영 경보를 발생시키고 성공할 때까지 재시도한다. 삭제 증적에는 실행 시각·처리 건수·성공 여부와 저 cardinality 실패 분류만 남기며 candidate, keyVersion, userId와 source event를 남기지 않는다.
- MongoDB 전체 재해복구 backup은 최대 35일 rolling 보존 후 자동 만료한다. backup은 일반 애플리케이션 조회에 사용하지 않고 접근 제한된 복구 용도로만 사용한다.
- 과거 backup을 복구할 때는 격리 환경에서 현재 기준 만료 purge를 먼저 실행하고 검증한 후에만 사용자 트래픽을 연결한다.

## 2026-08-26 무료 Trial 내부 API·Mongo ADR-001 작성

- `docs/adr/ADR-001-free-trial-internal-api-and-mongo-contract.md`를 기술 구현 기준으로 추가했다. 앱-facing Billing API는 만들지 않고 Identity event와 Learning Core reservation/group-status 내부 API만 `/internal/v1`에 둔다.
- Learning Core의 기존 `examId`를 opaque `sessionId`, 문제지 ID를 `mockExamId`, 필수 앱 UUID v4 `Idempotency-Key`를 `operationId`로 구분했다. reserve 전에 sessionId/mockExamId를 선할당하고 durable Session commit 뒤 같은 operation으로 confirm한다.
- Identity `PhoneEligibilityBindingVerified`/`Revoked` schema v1 wire는 재사용하되 과거 Bearer JWT transport는 사용하지 않고 승인된 C3-D Lattice/SigV4 route만 사용한다.
- candidate alias, 삭제 가능한 subject link와 immutable ledger core를 분리하고 active alias/reservation/group/session/command partial unique index를 고정했다. expired alias는 purge 전 reserve에서도 fencing할 수 있어 정확히 3년부터 재수급을 허용한다.
- Reservation audit에는 TTL을 두지 않고 5분 business expiry를 scheduler+Transaction으로 처리한다. inbox 120일과 terminal command 7일 같은 보조 기록만 `purgeAt` TTL을 사용할 수 있다.
- Mongo replica-set Transaction 경계를 eligibility event, reserve, confirm, cancel/expiry, group event와 TrialClaim purge로 나눴고 필수 동시성·unknown commit result test 목록을 정의했다.
- TrialClaim 3년 purge로 subject link가 제거된 AttemptGroup은 더 이상 replacement authorization에 사용하지 않는다. Learning Core의 기존 Session·결과는 삭제하지 않으며 익명 audit projection만 남길 수 있다.
- C3-D Lattice/SigV4 인프라 설계는 후속 ADR-002로 완료했다. 다음 코드 작업은 ADR-001의 Identity event consumer부터 vertical slice로 시작한다.

## 2026-08-26 ADR-001 확정 내용 설명

- ADR-001은 기존 제품 결정을 바꾼 문서가 아니라 서비스 간 요청 형식, 식별자 의미, 상태 전이와 Mongo 강제 장치를 구현 가능한 수준으로 고정한 기술 계약이다.
- 앱은 계속 Learning Core만 호출한다. Identity는 verified/revoked phone binding event를, Learning Core는 reserve/confirm/cancel/status와 최소 AttemptGroup 상태 event를 Billing 내부 `/internal/v1`로 보낸다.
- 앱 UUID v4 `Idempotency-Key`가 operationId이고 Learning Core 기존 examId가 sessionId다. mockExamId는 AttemptGroup 동안 고정하며 의도적인 restart는 새 operation/session을 쓰되 같은 consumption/group/mockExamId를 재사용한다.
- 최초 reserve는 current verified binding을 확인하고 필요한 TrialClaim·free grant·hold를 한 Transaction으로 만든다. Session commit 뒤 confirm에서 최종 consume와 group OPEN을 확정하고, commit 실패 cancel/5분 expiry는 hold만 복원하며 Claim은 유지한다.
- candidate alias, erasable subject link와 immutable audit core를 분리했다. partial unique index로 active Claim, Reservation, group, Session과 command를 하나로 강제하고, 3년 만료 시 alias/user 연결만 제거해 새 Claim을 허용한다.
- 실제 AWS Lattice ARN·role·security group inventory와 애플리케이션 구현은 아직 하지 않았다. 논리 설계는 ADR-002로 완료했고 실값 확인과 vertical slice가 남아 있다.

## 2026-08-26 Reservation confirm과 Summary 완료 시점 재확인

- `confirm`은 Summary 생성 완료 때가 아니라 Learning Core가 proposed `sessionId`의 ExamSession을 MongoDB에 durable commit한 직후, 시험 생성 성공 응답을 앱에 보내기 전에 호출한다.
- confirm은 5분 hold를 최종 consumption으로 전환하고 AttemptGroup을 `OPEN`으로 만드는 entitlement 단계다. 사용자가 문제를 풀고 제출·채점·Summary를 받는 과정은 그 뒤에 진행된다.
- 모든 필수 submit 접수 시 `GRADING`, 필수 feedback·유효 score·Summary가 조회 가능할 때 별도 `AttemptGroupStatusChanged(COMPLETED)` event를 보낸다. Summary 실패가 최종 확정되면 `RETAKE_AVAILABLE`이다.
- Summary까지 confirm을 미루면 5분 Reservation이 시험 중 만료되고 같은 무료권이 다른 Session에 재사용될 수 있으며, 장시간 RESERVED 상태가 되어 확정 계약을 위반한다.

## 2026-08-26 phone eligibility candidate 의미 재확인

- candidate는 raw phone을 Billing에 보내지 않고 같은 verified phone인지 비교하기 위해 Identity가 normalized phone, consumerScopeId와 Identity 전용 secret key로 만든 HMAC-SHA-256 Base64URL 값이다.
- 같은 scope와 keyVersion에서 같은 번호는 같은 candidate를 만들지만 scope 또는 keyVersion이 다르면 값이 달라진다. Billing은 key material을 받지 않고 candidate를 역산하거나 새로 만들 수 없다.
- Identity verified event는 key rotation 동안 여러 `{keyVersion, value}` candidate를 함께 보낼 수 있다. Billing은 하나라도 기존 active alias와 일치하면 같은 phone proof로 판정한다.
- candidate는 event 서명이나 인증수단이 아니다. 호출자 인증은 C3-D Lattice/SigV4가 담당하고 candidate는 중복 Claim 비교에만 사용한다.
- raw phone보다 안전하지만 여전히 pseudonymous data이므로 로그·metric에 넣지 않고 TrialClaim `claimedAt + 3년` 만료 purge 대상에 포함한다.

## 2026-08-26 ADR-002 작성 전 사용자 입력 구분

- 실제 AWS account/region, 환경별 VPC·subnet, ECS cluster/service, task execution/task role ARN과 security group ID는 정책 선택이 아니라 AWS read-only 조회로 채울 배포 사실이다.
- 사용자 결정이 필요한 핵심은 (1) dev/staging/prod별 Lattice service network 분리 여부, (2) 기존 IaC 도구와 source of truth, (3) 별도 Identity/Learning Core/Billing task role 신규 생성 허용, (4) 초기 custom domain 사용 여부, (5) AWS SDK v2 SigV4 signer 운영 의존성 허용이다.
- 권장안은 환경별 service network 분리, 기존 IaC 도구 재사용, 서비스별 task role 분리, 초기 Lattice 기본 DNS 사용, AWS SDK v2 signer를 outbound adapter 뒤에 격리하고 local/test에서는 fake adapter를 사용하는 것이다.
- Billing은 ALB 없이 Lattice target, Learning Core reservation/group-status route, Identity eligibility event route, operations repair route와 direct bypass 차단으로 이미 확정돼 다시 선택하지 않는다.
- ADR-002는 실제 ARN을 repository에 고정 문자열로 박지 않고 환경 설정/infra output 참조로 기록한다. credential과 account secret은 문서·로그에 남기지 않는다.

## 2026-08-26 VPC Lattice 환경 분리 비용 확인

- AWS 공식 VPC Lattice pricing page를 2026-08-26 확인했다. 과금 차원은 provisioned Lattice service 실행 시간, 서비스 처리 데이터 GB와 HTTP request/TCP connection이며 service network 자체는 별도 과금 차원으로 제시되지 않는다.
- 공식 페이지는 VPC association과 service network endpoint를 추가 비용 없이 사용할 수 있다고 명시한다. 따라서 service/network association 개수만 늘리는 것이 직접적인 시간당 비용 원인은 아니다.
- staging/prod에 Billing Lattice service를 각각 상시 배포하면 service network를 공유하든 분리하든 provisioned service는 두 개이므로 기본 service-hour 비용은 동일한 방향이다. staging service를 새로 상시 띄우는 비용은 발생하지만 이는 network 분리 비용이 아니라 두 번째 service 실행 비용이다.
- 미국 동부 공식 예시는 HTTP Lattice service 하나가 시간당 0.025 USD, 730시간 기준 월 18.25 USD이며 실제 서울 리전 단가는 별도 확인이 필요하다. 데이터 처리와 요청 초과분은 사용량에 따라 추가된다.
- 비용만 줄이려고 prod/staging service network를 공유하는 것은 권장하지 않는다. 비용 절감이 필요하면 staging Lattice service를 테스트 기간에만 provision하는 선택지가 직접적이지만 상시 E2E·장애 재현 가능성이 낮아진다.
- 확인 출처: `https://aws.amazon.com/vpc/lattice/pricing/`

## 2026-08-26 staging Lattice 운영 방식 보완

- staging service network/service/listener/target group/IAM policy/SG를 테스트마다 생성·삭제하는 방식은 권장하지 않는다. 설정 drift, DNS/ARN 변경, IAM 전파 대기와 E2E 시작 지연이 생긴다.
- 권장 운영은 staging Lattice 인프라를 상시 유지하고 개발·통합 테스트가 잦은 기간에는 Billing ECS task도 상시 1개 운영하는 것이다. 이 구성이 가장 단순하고 production과 유사하다.
- 비용 절감이 필요하면 Lattice 리소스는 유지한 채 staging Billing ECS desired count를 업무 외 시간에 0으로 낮추고 테스트 전 1 이상으로 올린다. 이는 Fargate compute 비용은 줄이지만 provisioned Lattice service-hour 비용은 계속 발생한다.
- 서비스 자체를 제거·재생성하는 방식은 장기간 staging을 사용하지 않을 때만 IaC 자동화로 수행한다. 수동 Console 삭제·재생성은 권장하지 않는다.
- 현재 규모에서는 network 공유로 작은 비용을 아끼는 것보다 환경 격리와 상시 재현성을 우선해 `환경별 network + staging 인프라 상시 유지`를 기본 권장으로 둔다.

## 2026-08-26 production/staging ECS 운영 형태·이관 순서 확정

- Identity와 Learning Core workflow는 모두 `tosunsaeng-staging-cluster`로 배포하고 `identity-staging.to-teacher.com`, `api-staging.to-teacher.com`으로 health check하지만, 현재는 이 cluster가 실제 운영 트래픽을 처리한다.
- 같은 AWS account·VPC에 새 production ECS cluster를 만들고 production용 Identity·Learning Core service, 환경 설정·Secret·task role·SG·Lattice network/policy·database를 분리해 구성한다.
- 새 production Identity·Learning Core의 health·smoke·데이터 연결·롤백을 검증한 뒤 ALB/DNS 또는 현재 진입점의 운영 트래픽을 새 cluster로 전환한다. 전환 안정화 전에 기존 service를 제거하지 않는다.
- 전환·관찰·롤백 window를 통과한 후 기존 `tosunsaeng-staging-cluster`를 staging으로 확정하고 staging용 Identity·Learning Core·Billing과 별도 Lattice network, role/SG/config/secret·Mongo database를 둔다.
- staging Lattice service/listener/target/IAM/SG는 유지하고 평소 staging ECS service `desiredCount=0`, 테스트 전 필요한 Identity·Learning Core·Billing task를 각각 1 이상으로 scale out한다.
- 모든 target health와 phone event/reservation route smoke test가 성공한 뒤 E2E를 실행하고, 종료 후 staging task를 다시 0으로 낮출 수 있다. 자동화는 start→healthy wait→test→stop 순서이며 테스트 실패 시에도 stop 여부와 로그 보존 정책을 명시해야 한다.
- ECS cluster만 둘로 나누고 같은 production DB, secret, task role 또는 Lattice network를 공유하면 충분한 환경 격리가 아니다. 최소한 데이터베이스·credential·IAM principal과 network policy는 분리해야 한다.
- task를 0으로 내려도 provisioned Lattice service와 staging Mongo/backup 등 다른 managed resource 비용은 남을 수 있다.
- 이 턴에서는 AWS resource를 생성·수정·배포하지 않았다. 클러스터 확장은 구현 후 production 배포 전 인프라 작업으로 남아 있다.

## 2026-08-26 ADR-002 입력 승인·현행 workflow 확인

- 환경별 Lattice service network 분리, `ap-northeast-2`, 같은 AWS account·VPC, 서비스별 application task role 생성, Lattice 기본 DNS와 AWS SDK v2 signer를 확정했다.
- Identity와 Learning Core workflow는 GitHub OIDC deploy role로 AWS credential을 받고 ECR에 image를 push한 뒤, 현재 ECS Service의 Task Definition을 download/render/register/deploy한다.
- 이 workflow는 이미 있는 ECS resource의 application revision을 갱신할 뿐 cluster, Lattice, IAM, SG를 생성하지 않는다. 저장소에 Terraform, CDK, CloudFormation은 확인되지 않았다.
- application task role은 GitHub deploy role과 ECS task execution role과 분리해야 한다. 실제 Identity/Learning Core task role ARN은 AWS `describe-task-definition` 읽기 전에는 확정할 수 없다.
- 최초 인프라는 AWS Console에서 수동 생성했고 이후 application 배포만 GitHub Actions로 갱신했다. 새 production 인프라에 수동 방식을 계속 쓸지 IaC를 도입할지는 ADR-002의 남은 운영 선택이다.

## 2026-08-27 AGENTS·서비스 간 계약 정합성 리뷰

- Billing `AGENTS.md`와 통합 계약의 제품 범위, eligibility route, SigV4 목표, strict decode/digest, event 수신과 무료권 지급 분리, Reservation 순서, AttemptGroup 완료 조건, TrialClaim 3년·purge·backup 정책은 승인 내용과 대체로 일치한다.
- 구현 전 차단 이슈로 ADR-001 inbox schema/index에 `consumerScopeId`와 `ux_inbox_identity_scope_user_revision`이 아직 없고, ADR은 persisted disposition에 `DUPLICATE`·`REJECTED`를 열거하지만 PLAN-001은 APPLIED·STALE만 저장한다. ADR이 최종 기술 기준이므로 Phase 0에서 하나로 보정해야 한다.
- 실제 Identity publisher는 구현돼 있지만 현재 adapter는 Bearer workload JWT를 발급받아 전송하고 Identity ADR도 구 route를 적고 있다. 목표 계약의 VPC Lattice ECS task role·SigV4와 `/internal/v1/eligibility/trial/events`로 staging 연동 전에 별도 Identity 변경이 필요하다.
- Identity publisher는 HTTP status만 받고 `Retry-After`와 error code를 읽지 않는다. 429/503의 `Retry-After` 계약을 지킬 수 없고, eligibility route가 409 `COMMAND_PROCESSING`을 반환하면 이를 retryable processing이 아니라 conflict와 같은 dead-letter로 처리한다. PLAN-001처럼 eligibility 409를 event conflict로만 제한할지 producer를 확장할지 계약을 명확히 해야 한다.
- 앱→Learning Core 시험 생성 operation ID의 정확한 wire source가 통합 계약에서 충분히 명시되지 않았다. 제품 결정은 공개 시험 생성 요청의 필수 `Idempotency-Key`지만 현재 Learning Core controller에는 이 header가 없다. Reservation saga 연결 전 Learning Core 후속 변경과 contract test가 필요하다.
- 앱 종료 뒤 기존 시험을 이어풀지 않고 새 key·새 examId를 사용하는 승인 불변식은 CONTRACT_DECISIONS에는 있으나 AGENTS 핵심 불변식과 통합 계약 흐름에는 명시가 약하다. 실제 Learning Core `startNew`는 기존 진행 Session을 abandon하고 새 Session을 만들어 현재 동작은 맞지만 후속 계약 문서 보강이 필요하다.
- 리뷰 요청은 수정 승인이 아니므로 계약·ADR·AGENTS와 다른 서버 코드는 변경하지 않았다. PLAN-001 시작 전 Billing ADR Phase 0 보정, staging 연동 전 Identity transport/retry 변경, Reservation 연동 전 Learning Core public idempotency 계약 보강 순서가 적절하다.

## 2026-08-31 앱 서버 통합 구조 조사 반영

- 신규 Jira 없이 Learning Core·Identity·Billing 전체 구조 조사에 Billing `develop@39e424d` snapshot을 반영했다. 관련 현재 구현 문맥은 `TMI-115`와 Learning Core `TMI-116`이며 Jira 상태는 변경하지 않았다. 통합 draw.io와 본 문서는 Learning Core `docs/architecture`에 있다.
- Billing은 TrialEligibility projection, BenefitDefinition, TrialClaim·Grant·Ledger, Reservation·Allocation과 AttemptGroup/AttemptSession 최소 projection을 소유하고 문제·음성·채점 결과는 저장하지 않는 것으로 정리했다.
- reserve·confirm·cancel·status와 expiry는 구현됐고, AttemptGroup `OPEN→GRADING→COMPLETED/RETAKE_AVAILABLE` event consumer는 아직 미구현이다. 앱용 공개 Billing API와 Store 결제·구독은 현재 범위 밖이다.
- 강점은 strict internal decode, durable command idempotency, Transaction·unique/partial index·CAS와 append-only ledger다. 주요 남은 gate는 Identity SigV4 transport, AttemptGroup consumer-first 연동, actual Lattice/Mongo failure-injection staging E2E다.
- 애플리케이션·계약·Jira·외부 인프라는 변경하지 않았고 코드 변경이 없어 Gradle 테스트는 실행하지 않았다.

## 2026-08-31 문서 계층·완료 보고 규칙

- 별도 Jira 없이 `AGENTS.md`에 계획·조사 문서의 6단계 읽기 계층과 구현 완료 보고 필수 항목을 추가했다.
- 결론별 파일 근거와 구현 사실·계획·추론 구분을 요구하며, 상세 목록과 표는 부록으로 보존한다.
- 구현 완료 후 변경·계약·테스트·위험·배포 전 확인·예상 밖 diff·다음 확인을 보고한다.
- Billing 애플리케이션과 외부 계약은 변경하지 않았고 Gradle 테스트를 실행하지 않았다.


## 2026-09-08 다우기술 노션 포트폴리오 토선생 소재 선별

- 브랜치: develop. 신규 Jira 없음.
- 목표: 사용자 요청에 따라 토선생 상세 페이지에 넣을 내용을 선별.
- 조사: 기존 포트폴리오 소재·트러블슈팅 문서, 각 서비스 현재 상태와 Learning Core 복구·Saga 테스트 및 MDC 구현을 대조.
- 결정: 빈 종합 피드백과 선택적 채점 복구, 시험 생성·사용권 Reservation Saga를 대표 사례로 추천하고 구조화 로그를 보조 사례로 제안. 인증·저장 모델·S3는 구조 설명에 배치.
- 구분: 구현 및 과거 테스트 기록은 운영 활성화 증거와 다르며 실제 결제·환불은 현재 구현 성과로 사용하지 않음. 처리량·비용·장애 감소 수치 미측정.
- 변경 파일: 이 저장소의 docs/codex/WORKLOG.md와 CURRENT_STATE.md에 조사 기록만 추가. 기존 미커밋 작업 보존.
- 검증: 소스·테스트 정적 조회. 애플리케이션 변경이 없어 Gradle 테스트는 재실행하지 않음.
- 유지: API·AI 계약·feature flag·코드·외부 서비스·Git 이력 변경 없음.
- 다음: 본인 역할과 사례별 설명 가능 범위 확인 후 노션 본문 작성.
