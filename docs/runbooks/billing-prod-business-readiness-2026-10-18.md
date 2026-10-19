# Billing 운영 업무 준비 실측

## 1. 5줄 결론

1. 2026-10-18 AWS 직접 조회: Billing prod7 단일 COMPLETED/실행1/대기0, 8083·8443 healthy.
2. Identity prod15 단일 COMPLETED/실행1/대기0/8081 healthy. 구매 control/fence ON이나 scope·Billing 탈퇴 및 phone 발신 OFF.
3. 운영 Billing snapshot 읽기에서 구매·권리·예약·시험·소유 연결 0건 확인. 현재 Billing 내부 attribution 이관 대상 없음.
4. control은 PRODUCTION/billing 단일1건, schemaVersion1/version2/PURCHASE_BLOCKED. 변경하지 않았다.
5. 조건부 설정12개와 OPEN 전환 초안 준비. Identity 발급/발신·LC 삭제 검증·유입 제한이 남아 실제 활성화하지 않았다.

## 2. 반드시 읽을 내용

Billing DB에 자료가 없는 것과 기존 앱 사용자에게 권리가 없는 것은 다르다. 기존 LC 시험/사용자 자료는 이번 DB 집계 대상이 아니다. 구앱 무료 경로를 변경하지 않는다. 또한 trial_eligibility=0이고 Identity PHONE_ELIGIBILITY_PUBLISHER_ENABLED=false이므로 신앱 무료 자격 공급은 별도 준비가 필요하다. 이미 로그인한 토큰의 구매 scope 발급 여부를 설정만으로 증명하지 않는다.

## 3. 실행 전에 맞출 사항

- Identity 담당: AUTH_PURCHASE_SCOPE_ENABLED 및 정상 ACTIVE MEMBER 토큰의 billing:purchase 발급, 탈퇴 발신과 정상 인증 수신 검증. phone 무료 경로는 eligibility/owner 발신 준비도 필요하다. Billing 작업에서 Identity를 변경하지 않는다.
- LC 담당: 저장소/삭제 writer 근거, exact 대상 한정 전달·204 저장 검증 및 검증계정/자료, 기존 시험 봉인·생성 제한 준비.
- 전환 담당: 공개 purchase-account 및 신규 시험 유입 제한 방식/시간, 업무 ON·삭제 POST allowlist·OPEN 실행 범위 승인. 환불/판매 worker/자동삭제는 이번 묶음에 자동 포함하지 않는다.

## 4. 적용 초안과 위험

[조건부 JSON](billing-prod-business-activation.draft.json)은 ECS 등록 파일이 아니며 실행 승인도 아니다. 최신 live task 전체를 다시 읽고 이12개만 조건 충족 후 병합한다. Identity receiver·withdrawal ON 및 기존 LC receiver/TLS/Secret/이미지를 보존한다. legacy-attribution-verified 후보 true는 이번 빈 inventory를 근거로 하되, 적용 직전 전체 inventory를 재검사하여 변동 시 다시 판단한다.

DB는 최신 단일 control의 _id/environment/consumer/schemaVersion/epoch/version/barrier를 읽어 exact CAS로 OPEN, version만1 증가, epoch 유지, matched/modified1 및 readback 확인하는 절차다. 현재 version2를 실행 상수로 고정하지 않는다. 실패/응답유실은 재조회하고 무조건 재실행하지 않는다. OPEN은 다른 writer도 허용하므로 제외 기능 OFF 확인이 필수다.

기존 배포용 BillingProdSchemaBootstrap.verify는 ALL_BLOCKED를 명시 요구했다. 후속 로컬 보완으로 `verify`에서만 `BILLING_EXPECTED_RECOVERY_BARRIER=PURCHASE_BLOCKED`를 명시해 현재 차단을 보존한 검사가 가능하다. 미설정은 기존 ALL_BLOCKED, 허용값은 ALL_BLOCKED/PURCHASE_BLOCKED/OPEN의 exact 값이며 install-empty는 ALL_BLOCKED만 허용한다. 검증 전후 단일 control의 환경/consumer/Long schema1·양수version/UUID v4 epoch 및 전체 문서 동일성을 검사한다. 값을 바꾸는 기능은 추가하지 않았다. 검사 중 control 변경 시 실패하고 다시 읽어야 한다.

보완 소스: [검증 도구](../../scripts/ops/BillingProdSchemaBootstrap.java), [독립 회귀21개](../../scripts/ops/BillingProdSchemaBootstrapTest.java). 기존 schema verifier들을 그대로 재사용하며 DB를 ALL_BLOCKED로 되돌리지 않는다. **보완 도구를 운영에 전달·실행하지 않았고 운영 schema exact 재검증은 아직 남았다.** 실제 실행은 배포 이미지와 일치하는 클래스패스 및 expected host/DB/환경을 확인한 뒤 `verify`만 호출한다. CloudShell에 남은 이름만 같은 JAR를 검증 없이 재사용하지 않는다.

후속 운영 실행 시도 중 브라우저 연결이 갱신됐고 기존 CloudShell 탭은 다른 작업 세션이 소유한 것으로 확인됐다. 해당 탭을 가로채거나 다른 LC 터미널로 명령을 보내지 않았다. Billing 전용 연결이 확보된 뒤 최신 task/barrier 및 JAR 일치부터 다시 읽어야 한다. 준비된 변경은 운영 검증 도구뿐이며 제품 서버 이미지 재배포나 전체 OPEN 승인과 무관하다.

## 5. 읽기 결과

CloudShell Billing 첫 번째 터미널에서 ECS/target health 조회 및 mongosh snapshot transaction의 countDocuments만 실행했다. Secret은 메모리/자식 프로세스 환경에만 전달, URI·문서 원문·사용자 ID 미출력. 읽기 transaction은 abort하고 종료, exit0/READINESS_READ_ONLY_OK 확인.

| 자료 | 건수 |
| --- | ---: |
| payment_accounts / payment_account_lifecycles / purchase_account_refs | 각각0 |
| purchases / subscription_entitlements / payment_timelines / payment_ledger | 각각0 |
| reservations / reservation_allocations / reservation_operation_receipts | 각각0 |
| attempt_groups / attempt_sessions / billing_subject_links / entitlement_grants | 각각0 |
| trial_claims / trial_candidate_aliases / trial_eligibility | 각각0 |
| exam_deletion_facts / payment_withdrawal_inbox / payment_sync_commands | 각각0 |
| payment_products | 10 |
| payment_recovery_controls | 1 |

빈 inventory는 관측 시점의 Billing 범위 증거이며 원천 completeness·신규 발신·미래 쓰기를 보장하지 않는다. 기존 상품10개는 변경하지 않았고 사용자 권리를 지급하지 않았다.

## 6. 검증·변경 범위

- 조건부 JSON 파싱/환경변수12개 application.yml 매핑 및 git diff --check 통과.
- 후속 검증도구 보완: 독립21개 컴파일/실행 및 전체 clean test1838개 실패0/오류0/skip0, 4분10초 통과. 이 결과는 운영 스키마 읽기 실행의 대체가 아니다.
- 제품코드 변경 없어 Gradle 미실행. 운영 ECS/DB 읽기 성공은 정상서명 업무 E2E나 새 설정 기동 성공이 아니다.
- 변경: 본 문서·조건부 JSON·회신 링크·CURRENT_STATE/WORKLOG. 기존 dirty 보존, 예상 밖 제품 변경 없음.
- 서버/DB/ALB/키/권한/LC·Identity/commit·push/Jira 변경 없음. 다음은 위 선행조건 회신과 barrier 호환 schema 읽기검증 구성, 승인된 통합 전환이다.
