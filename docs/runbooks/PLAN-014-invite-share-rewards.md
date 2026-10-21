# PLAN-014 초대 공유 보상 구현·운영 안내

- 날짜: 2026-10-21 / 브랜치: `codex/invite-share-rewards`
- 기준: [공개 계약 R1](../contracts/INVITE-SHARE-REWARD-PUBLIC-API-R1.md), [기술 규격](../plans/PLAN-014-TECHNICAL-SPEC-R1.md)
- 범위: Billing 구현과 사용자 승인에 따른 테스트 배포·ON(아래 실행 기록). 코드 기본값은 OFF. 앱/Identity/LC 및 production 변경 없음.

## 구현된 흐름

GET/POST `/api/v1/rewards/invite-shares`는 동일 public connector에서 각각 `billing:read`/`billing:purchase`를 요구하며 MEMBER만 허용한다. POST는 단일 lowercase UUID v4 `Idempotency-Key`와 body 없음 규칙을 적용한다. query·빈 query·공백 body·중복 key header는400이다. 기존 payment route의 오류 문구는 유지한다.

기존 purchase-account로 계정과 MEMBER 근거를 준비한 뒤 지급한다. 구매 이력은 요구하지 않는다. 지급은 recovery/account lifecycle 쓰기 fence → 현재 귀속·증거 확인 → 기존 terminal 결과 → 누적 한도 순서다. 같은 `ClientSession`에서 누적 횟수·공통 지급순 sequence·INVITE grant·GRANTED ledger·terminal receipt를 commit한다. 한도 거절도 receipt를 commit한 뒤409로 응답한다. 탈퇴 거절은 성공 replay보다 우선이다.

세 번째 성공의 재시도는200과 당시 snapshot을 재현한다. quota GET은 현재 누적값을 snapshot으로 읽고 데이터를 생성하지 않는다. 공통 Transaction의 제한된 재시도 소진은503이 될 수 있으므로 앱은 원 key로 backoff 재시도한다. commit 결과 불명 시 내부에서는 같은 Transaction의 commit만 재시도한다.

## 저장·schema

별도 additive revision `invite-share-reward-r1`을 사용한다. 기존 paid `r3` revision을 변경하지 않는다.

- `invite_reward_accounts`: `(environment,userId)` unique. 누적1~3, 초기 지급과 함께 생성. TTL 없음.
- `invite_reward_receipts`: `(environment,userId,operationId)` unique. AWARDED/LIMIT_REACHED만 존재. detailEligibleAt 조회 index, 자동TTL 없음.
- `entitlement_grants`: INVITE `(environment,sourceType,sourceId)` partial unique. 기존 MEMBER 지급순 unique 유지.
- `entitlement_ledger`: INVITE issuance의 환경·ownerUserId·sourceType·sourceId 최소 귀속을 추가하고 partial owner 조회 index를 둔다. 기존 지급/소비 DTO 구조와 공개 wire는 유지한다. receipt 및 grant/counter 소실 뒤에도 남은 원장으로 0 초기화를 차단하기 위한 근거다.

`InviteRewardSchema.installUnderMaintenance(db, true)`는 writer 격리 상태의 명시 maintenance 함수다. 애플리케이션 startup에서는 `verify`만 수행하며 DDL하지 않는다. 기존 validator/index가 다른 경우 임의 교체·drop하지 않는다. 실제 운영 설치는 이번에 실행하지 않았다.

receipt 상세는 terminalAt부터120일 보장한다. BSON millisecond에는 위로 반올림해 보장기간을 줄이지 않는다. 명시 `purgeDetailsUnderMaintenance(db, true, now, limit)`은 정확히 detailEligibleAt부터 최대1000건의 상세만 삭제한다. quota/grant/ledger는 삭제하지 않는다. 상세가 남아 있으면 계속 replay하며 삭제 후 과거 key는 새 지급 시도로 처리될 수 있으나 누적3은 유지한다. 자동 정리 scheduler/API는 추가하지 않았다.

## 설정·사용권

`app.rewards.invite-share.enabled` / `INVITE_SHARE_REWARD_ENABLED` 기본false. OFF는 두 route404이며 기존 INVITE 소비·조회에는 이 flag를 사용하지 않는다.

ON은 public connector, purchase-account, MEMBER evidence, paid foundation/recovery/admission, integrated reader, 기존 recovery control 및 새 schema를 요구한다. 서비스 환경 test/staging/production과 payment SANDBOX/PRODUCTION의 기존 매핑을 검사한다. 새 외부 Secret/Identity claim은 필요 없다. rate limit은 기존 HMAC bucket 방식으로 GET60회/분, POST10회/분이며 별도 route bucket을 사용한다.

초대권은 기존 EXAM_PASS 소비 흐름에 연결된다. 유료 우선·무기한 FIFO, cancel/expiry의 hold 복구, 같은 소비 replacement를 유지한다. INVITE를 유료 전환 복원 대상으로 인정하지 않는다. 보상 지급은 시험 guard의 current/pending 필드를 수정하지 않는다.

## 검증과 남은 배포 조건

테스트는 가짜 JWT·로컬 Mongo replica-set Testcontainers만 사용한다. 초대용 `InviteRewardControllerTest`, `InviteRewardConfigurationTest`, `InviteRewardIntegrationTest`가 HTTP 권한·본문·flag·문구, 기본 OFF/의존성 거절, 원자성·동시성·unknown commit·120일 정리·quota 소실·탈퇴·실제 reserve/confirm/replacement/cancel/expiry/paid 전환 및 reader를 확인한다. 전체 결과는 CURRENT_STATE/WORKLOG에 기록한다.

앱 공유 activity/component 판정·key 영속화·계정 전환, 실기기 공유 취소/복사/앱 종료, LC와 배포 환경 통합, 운영 schema 설치와 ALB의 신규 path 허용은 별도 확인한다. 실제 전송 여부·수신자/친구 중복·자기 공유는 서버가 검증하지 않으며 승인된 누적3 제한으로 위험을 한정한다. 기존 금융/Claim/탈퇴 보존기간을120일로 바꾸지 않는다. 신규 quota/issuance 귀속의 개인정보 제거는 기존 lifecycle 정책과 함께 운영 적용 전에 확인한다.

최종 검증: `./gradlew clean test`1,904개(신규26개 포함), 실패0/오류0/skip0, 4m32s. 보고서 `build/reports/tests/test/index.html`, 원본 JUnit XML `build/test-results/test/`. 기존 보안 테스트의 신규 설정 mock 누락을 보완한 최종 실행 결과다.

## 2026-10-21 테스트 배포·활성화

사용자 이번 요청으로 commit/push 및 테스트 배포·ON을 승인했다. main/production, Identity/LC/app은 변경하지 않았다.

- 제품 commit `bdda48836233664603cb115ab337f62f6fd7cf28`, [PR38](https://github.com/Too-Much-I/app-back-end-billing/pull/38), develop merge `fff61caa538c02f690c6cf19a6125283101af755`.
- [Actions39567515145](https://github.com/Too-Much-I/app-back-end-billing/actions/runs/39567515145) SUCCESS. 테스트/build/image smoke/OIDC/ECS/target 검증 포함.
- 배포 이미지 `sha256:58e89e0635f9f093926463f5cc346fd3fa6c98b50f37e29178b77b2483d302c4`; 최초 자동배포 `tosunsaeng-billing-test:18`.
- 테스트 ALB priority98은 `billing-test.to-teacher.com` + exact `/api/v1/rewards/invite-shares` + GET/POST만 기존 public8083 target에 연결. 기존 fallback404 및 다른 route 유지. flag OFF 때 Billing `NOT_FOUND`404 확인.
- 기존 테스트 writer의 실제 STOPPED를 확인한 뒤 일회성 `tosunsaeng-billing-test-invite-schema:1`/task `2495eb5afb24424094bba08d5445a23e`로 additive r1 설치·verify 성공(exit0). `recoveryUnchanged=true`; 기존 권리/원장/receipt purge나 recovery control 변경 없음.
- `scripts/ops/InviteRewardMaintenance.java`는 배포JAR dependency로 실행하는 테스트 한정 도구다. DB명 test suffix·expected host·writer 정지·SANDBOX OPEN control을 검사한다. application startup에 설치를 연결하지 않았다. 컴파일·Boot PropertiesLauncher 로드·설정 누락 거절 및 실제 테스트DB 설치를 확인했다.
- flag 활성화 task `tosunsaeng-billing-test:19`, `INVITE_SHARE_REWARD_ENABLED=true`. 신규 Secret/IAM/SG 권한 추가 없음.

- HTTP 확인: 활성화 후 GET/POST 무인증401, 응답 `Cache-Control: no-store`. OFF404→ON401 전환을 확인했다. 앱의 실제 MEMBER 로그인·공유·지급3회/재시도는 미실행으로 프론트 E2E에 남긴다. 활성화 배포 중 OFF/ON 태스크 공존 때 POST404가 한 번 관측됐으며 신규 태스크 경로에서401로 확인했다.
- 롤백은 테스트 서비스 task18로 되돌리거나 reward flag만false로 설정한다. additive collection/index는 유지하고 지급된 INVITE 권리·누적한도·원장은 삭제하지 않는다. 다음 develop 배포는 live task19 설정을 기준으로 image만 교체하므로 ON 설정이 유지된다.
- 예상 밖 제품 변경 없음. 로컬 기존 dirty 백업/stash는 보존했다. 추가 변경은 운영도구·배포기록이며 사용자 승인 범위를 넘어 운영 활성화·앱 보상 지급을 수행하지 않았다.

최종 ECS task19 단일 PRIMARY/COMPLETED, desired1/running1/pending0 확인. 신규 ON target의8083/8443 healthy, 이전 OFF target은 연결 종료 중(draining)이며 신규 요청 대상이 아니다. task18과 비교해 reward flag 외 설정은 동일하다.
