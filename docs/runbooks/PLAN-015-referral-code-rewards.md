# PLAN-015 추천인 코드 구현·운영 안내

- 날짜: 2026-10-22 / 브랜치: `feat/TMI-223-referral-code-rewards` / Jira: TMI-223
- 기준: [설계](../plans/PLAN-015-referral-code-rewards.md), [기술 규격 R1](../plans/PLAN-015-TECHNICAL-SPEC-R1.md), [프론트 계약](../contracts/REFERRAL-REWARD-PUBLIC-API-R1.md)
- 범위: Billing 구현·테스트. 2026-10-22 사용자 후속 요청으로 commit/push·테스트 배포·추천인ON/공유OFF 준비를 승인받았다. 운영 변경은 제외하며 실행 기록으로 완료 여부를 구분한다.

## 1. 구현 요약

1. 코드 발급·적용 두 POST만 추가했다. 별도 내 상태 GET·조회 DTO·조회 rate bucket은 없다.
2. 입력자와 추천인의 EXAM_PASS 각1개, 지급 원장2개와 성공 적용 기록을 동일 ClientSession Transaction으로 확정한다.
3. 계정당 성공1회·자기추천금지·추천인무상한이다. 가입시점/전화/기기 검사는 추가하지 않는다.
4. 기존 유료우선/FIFO/예약 취소·만료·replacement·재답변을 재사용한다. 추천권은 유료전환 복원 대상이 아니다.
5. 기본OFF·명시 schema 설치·공유동시ON거절이다. 실제 활성화·앱 E2E와 개인정보 정리 운영 준비는 별도다.

## 2. 코드와 Transaction 경계

`ReferralRewardController`는 MEMBER/billing:purchase, strict code/body/key, 최대1,024 byte·UTF-8과 요청별 bucket을 처리한다. `PublicIngressFilter`와 `PublicSecurityConfig`는 public connector의 두 exact POST만 허용한다. 다른 포트/forwarded header 우회는 불가하다.

`ReferralRewardService`는 기존 purchase-account MEMBER 증거와 lifecycle을 읽는다. ACCOUNT recovery fence를 같은 session에 획득하고 인증 deadline을 commit 직전에 다시 확인한다. 신규 적용의 양쪽 lifecycle CAS·guard nextGrantOrder·grant/ledger·적용 문서는 한 Transaction이다. 두 사용자 ID의 고정 순서로 쓰며 현재/생성 중 시험 guard 필드는 보존한다. 별도 quota/counter/PENDING/outbox를 만들지 않는다.

같은 성공key/같은 정규화code 및 다른key/기존code는 기존200이며 재지급하지 않는다. 다른key의 재조회는 새 key를 기록하지 않는다. 같은 성공key/다른code는409 IDEMPOTENCY_KEY_CONFLICT, 다른key/다른code는409 REFERRAL_ALREADY_APPLIED다. 입력자 탈퇴는 replay보다 우선하고 추천인의 이후 탈퇴는 기존 성공을 취소하지 않는다. 거절 요청은 key를 예약하지 않는다.

코드는 SecureRandom 10자리, 환경+코드 및 환경+owner unique다. 기존 코드는 재호출 시 그대로 반환하며 충돌은 새 Transaction 후보를 최대5회 생성한다. DB unique/일시 경합은 기존 bounded retry로 수렴하고 소진503은 같은 key/body로 재시도한다. unknown commit은 기존 PaymentStore의 동일 Transaction commit 재시도를 사용한다.

## 3. 저장·명시 maintenance

`ReferralRewardSchema` revision `referral-reward-r1`은 두 collection, 7개 index를 추가한다. 기존 paid revision은 변경하지 않는다.

| 자료 | 제약·역할 |
| --- | --- |
| referral_codes | 환경+code unique, 환경+ownerUserId partial unique; 계정별 고정 코드 |
| referral_applications | 환경+applicantUserId unique, 환경+applicantUserId+operationId unique; 최초 성공과 양쪽 상세 |
| entitlement_grants | REFERRAL source partial unique; 기존 MEMBER 지급순 index 사용 |
| entitlement_ledger | REFERRAL 지급귀속 및 기존 dedupe unique; 기존 소비 기록 방식 유지 |

`installUnderMaintenance(db, true)`는 기존 paid schema를 검사하고 누락된 신규 collection/index만 설치한다. 기존 validator 불일치는 실패하며 drop/collMod 자동수정하지 않는다. startup은 verify만 수행한다. 실제 설치에는 모든 writer 정지·격리와 별도 승인이 필요하다.

`purgeDetailsUnderMaintenance(db, true, now, limit)`은 적용 후120일 경계(밀리초 올림) 이후 최대1000건 detail만 null로 정리한다. 최초key/code/applicationId/적용1회 근거는 유지한다. `cleanupWithdrawnUnderMaintenance`는 DB의 WITHDRAWN·정확 탈퇴시각·확인시각을 확인하고 본인 적용120일 보장이 끝났을 때 본인 적용문서와 코드 owner/account 연결 및 타인 detail의 referrerUserId를 정리한다. 원장과 무료권은 건드리지 않는다. writer 격리 하의 멱등 단계이며 자동 scheduler/HTTP purge는 없다.

owner 없는 code tombstone은 재발급 방지를 위해 남긴다. **그 보존기간·재식별 가능성의 법적 검토는 아직 미완료이며 무기한 보존 정책 승인으로 취급하지 않는다.** 실제 ON 전 정리 담당·주기·tombstone 정책을 확정해야 한다. 일반 구매계정15일 삭제를 추천 원장 전체에 적용하지 않는다.

## 4. 배포 전과 rollback

1. 앱 두 POST 계약·계정별 key 보관/가입 완료 후 적용을 확인한다.
2. 실환경 기존 공유 flag 확인 후 공유OFF를 별도로 승인받는다. 이미 지급된 INVITE 무료권·quota는 유지한다.
3. writer 격리 후 신규 schema 설치·verify. 신규 Secret/Identity claim은 필요 없다.
4. 새 코드 기본OFF 배포, public ALB에 두 exact POST만 연결. 기존 API/LC/Identity는 유지한다.
5. MEMBER account/evidence, paid foundation/admission/recovery/integrated reader 준비와 schema 확인 후 `REFERRAL_REWARD_ENABLED=true`. `INVITE_SHARE_REWARD_ENABLED`와 동시ON은 기동 거절한다.
6. 전용 두 테스트 회원으로 최초지급/재시도/이용권조회/시험 사용을 확인한 뒤 운영 전환을 별도 승인한다.

rollback은 referral OFF·공유OFF 유지다. 이미 지급된 REFERRAL을 읽을 수 있는 이미지로만 되돌린다. 신규 grant를 알지 못하는 구버전 rollback이나 권리 회수·DB drop은 하지 않는다.

2026-10-29 종료/일시중단 구분: `REFERRAL_PROMOTION_ENDED` 기본 false. 이벤트 종료는 enabled=true + promotion-ended=true로 배포한다. 새 적용·코드 발급/재조회410 `REFERRAL_PROMOTION_ENDED`, 기존 성공 같은 코드 재요청200 유지. 기능 enabled=false는 인증된 요청에503 `REFERRAL_TEMPORARILY_UNAVAILABLE`이며 종료 표시로 사용하지 않는다. 혼합 버전/설정 배포 중에는 기존 태스크에서 접수될 수 있으므로 전체 교체 완료 시점을 종료 기준으로 사용한다. 새 DB/schema/API 없음. 테스트 배포는 진행 상태(false)를 유지한다.

## 5. 검증·남는 위험

로컬 가짜 JWT 및 disposable replica-set Testcontainers로 HTTP/strict JSON/권한·환경/기능OFF, 양쪽지급·단일Tx rollback·unique·동시성·unknown commit·탈퇴경합·120일 정리·기존 INVITE·paid-first/예약/재시작/재답변을 검사한다. 최종 전체 테스트 수치와 실패 이력은 WORKLOG/CURRENT_STATE에 기록한다.

최종 `./gradlew clean test`: **1,970개(신규33개), 실패0/오류0/skip0, 4m41s**. 추천 Controller6·Configuration4·Mongo통합22 및 재답변 추가1개. 첫 전체 실행의 보안 slice 설정mock 누락6건은 보완 후 전체 재통과했다. 보고서는 `build/reports/tests/test/index.html`, 집계 원본은 `build/test-results/test/`이다.

다계정·상호추천·직접 API를 호출하는 기존 회원의 최초 적용은 승인된 위험이다. 실제 Identity 탈퇴 전달 지연과 앱 재시도 구현은 로컬 테스트의 보장이 아니다. 실DB 설치·외부 연동·실기기 E2E·성능 부하는 이번 구현 검증에 포함하지 않는다.

## 6. 2026-10-22 테스트 전환 실행 기록

- 사용자 commit/push·테스트 배포·추천인ON/공유OFF 승인. 제품 commit `1e757278bbd2eba057168a27a5da1271f4e0917a`, [PR43](https://github.com/Too-Much-I/app-back-end-billing/pull/43) develop 병합 `ad606c0459a62ffc70439fff2954721d51181150`. main/prod 무변경.
- [테스트 Actions39734263409](https://github.com/Too-Much-I/app-back-end-billing/actions/runs/39734263409) 실행. 기존 live 설정을 보존한 image-only 배포다. 추천인 활성화 완료로 간주하지 않는다.
- 배포 전 test task22, `INVITE_SHARE_REWARD_ENABLED=true`, DB `to-teacher-billing-test` 확인. 추천인 flag는 미설정(default OFF).
- `ReferralRewardMaintenance.java`: 배포 JAR 의존성으로 컴파일 및 설정 누락 시 DB 접근 전 실패 확인. 시험용 DB/host/동일 URI·writer 정지·SANDBOX OPEN control 검사, additive 설치·검증 후 control 불변 확인. 자동 purge는 실행하지 않는다.
- `referral-test-alb.py`: 승인된 테스트 host의 exact POST 두 경로만 priority97/기존8083으로 추가하는 도구. 기존 priority98 공유 route/메서드/host·8083 검증과 priority 충돌 거절. CloudShell 첫 호출은 shell newline 인용 오류로 실행 전 실패; 재호출 후 결과를 읽기 전에 UI 도구가 unavailable해져 실제 적용 여부 미확인. **재실행 전에 priority97을 조회해야 한다.**
- `referral-test-rollout.py`: prepare→stop→install→activate의 명시 단계 도구. 새 image digest 및 기존 설치 task DB/Secret 일치, service writer 정지, 설치 exit0·동일image를 검사하고 reward 두 flag만 변경한다. Python 문법 검사 통과, 실제 실행하지 않았다.
- 로컬 AWS 자격증명 없음·브라우저 제어 도구 이용 불가로 스키마 설치/ON·OFF 전환 중단. 테스트 서비스 중지·DB 변경·새 역할/Secret/운영 변경 없음. 전체 테스트와 코드 배포 성공은 앱 지급 E2E나 활성화 성공을 의미하지 않는다.
- 다음: AWS 제어 복구 후 ALB97 조회, 새 이미지 안정화 확인, 명시 schema 설치 및 추천인ON/공유OFF, HTTPS 신규401/공유404와 task/targets 확인. 보존 정책 확정은 운영 활성화 전 조건으로 유지하며 이번 테스트 전환으로 purge/tombstone 무기한 보존을 승인하지 않는다.

### 후속 적용 결과 (2026-10-22 16:06 KST)

- 위 중단 상태를 대체: test ALB97 생성·재조회 성공, Actions SUCCESS·test23 안정화 후 설치 실행. 최초 설치 task8163c7489b114f8ea1f3ea0e6e060e9f는 exit1로 실패했고 기존 서버를 재개했다. 실패 단계 미확정이며 성공으로 취급하지 않는다.
- 후속 읽기 대조에서 referral_codes/referral_applications 및7개 index의 실제 설치 확인. 독립 schema verify task f0fc8d6a8add46bc90683254bd2d78cf의 exit0·PAID_SCHEMA_OK·REFERRAL_VERIFY_OK와 기존 SANDBOX/billing/OPEN 확인 후 전환. recovery control 전체 불변은 단정하지 않는다.
- test24: 추천인true·공유false, image는 test23과 동일. HTTPS 두 추천 POST401(인증필요), 기존 공유POST404, entitlements401 확인. 기존 INVITE grant/원장 보존. 운영 변경 없음.
- 전체 ECS rollout COMPLETED 재조회와 로그인한 두 회원의 지급·재시도 E2E는 별도로 남긴다. API401 확인은 인증된 지급 성공을 의미하지 않는다.
