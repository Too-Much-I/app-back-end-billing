# TMI-205 테스트 cleanup 활성화 유지보수

## 범위와 승인

테스트 `to-teacher-billing-test`만 대상이다. 사용자에게 cleanup jobs 컬렉션 한정 임시 collMod·Billing 중지·검증 설치·권한 회수를 승인받았다. 운영/Identity/LC 설정, 기존 사용자 탈퇴, 구매 원장 삭제는 하지 않는다. 자동삭제 최종 ON은 별도 적용 직전 확인한다.

## 사전 결과

- Billing test11 정상 배포 기준. recovery fence/provider worker·재조회 ON 확인.
- 읽기 전용 실DB 점검: cleanup jobs/구매 계정/구매0건, control1건/v1/OPEN이며 baseline·snapshot/feed job 없음. benefit1·상품10·provider inbox2·eligibility inbox13/projection9 등 기존 자료는 보존한다.
- 기존 광범위 역할은 쓰지 않고 `billing-test-cleanup-collmod`를 생성해 cleanup jobs 하나의 collMod만 추가했다. 원래 test DB 및 RC budget collection readWrite는 보존했다.
- 새 `scripts/ops/billing-test-cleanup-maintenance.js`는 test DB 이름·exact control validator·단일 SANDBOX/billing 최소 control·기존 index·cleanup0건을 검사한다. known v1/OPEN만 허용하고 baseline/job·기존 증거를 추정 변환하지 않는다.
- 외부에서 모든 Billing writer 종료 확인 후, control CAS로 ALL_BLOCKED → r2 validator/index 설치·대조 → control4 및 기존 OPEN 복원 순서다. epoch는 보존하고 version은 두 번 증가한다. 이 OPEN 복원은 기존 정상 DB의 제한된 스키마 유지보수 종료이며 재해복구/과거 완전성 증거가 아니다. 중간 실패는 ALL_BLOCKED 유지한다.
- 초회 실DB dry-run은 dynamic eval 내부 mongosh 비동기 호출 처리 누락으로 사전검사에서 실패(쓰기 없음). 명시 await로 수정하고 async fake DB 테스트8개 및 실DB dry-run exit0 통과했다. 실패를 DB 손상으로 간주하지 않는다.

## 실행 기록

- 실제 변경 전 테스트 Billing desiredCount0 요청. ALB drain/프로세스 종료를 확인한 뒤에만 설치한다.
- 설치·권한회수·재배포 최종 결과는 후속 기록한다. 이 문서는 아직 활성화 성공 보고가 아니다.
- 최종 ON 사용자 승인 수신: 승인 조건을 만족한 테스트 구매 계정 연결정보의 물리 삭제 위험을 명시한 뒤 재확인·purge ON 허용. 구매 원장·운영 데이터 제외.
- 유지보수 도구 async fake DB 테스트8개 및 Java21 clean test 1,798개/실패0/오류0/skip0 통과(3분59초). 신규 앱 이미지는 만들지 않으며 test11과 동일 이미지에서 두 cleanup 플래그만 바꾼 test12를 등록했다. 아직 서비스에 적용하지 않았다.
- 후속: ALB 기본300초 drain 뒤 서비스/독립 Billing writer 종료를 재조회하여 확인했다. 종료 전 설치 시도는 사전 assertion에서 차단됐으며 DB write 없음. 종료 후 maintenance task exit0, `CLEANUP_MAINTENANCE_INSTALLED_R2_CONTROL4` 확인.
- Atlas Billing 계정에서 cleanup collMod 역할 연결 제거·저장 후 원래 readWrite 두 권한만 표시되는 것을 확인했다. 역할 정의는 비연결 상태로 남으며 누구에게도 새로 부여하지 않았다.
- test11→test12 container/secret/image/role 불변 및 cleanup reconciliation/purge 두 env만 true인 것을 대조하고 서비스 desiredCount1로 기동 요청했다. 최종 안정화·실효 권한 조회는 후속 기록한다.
- 사후 별도 새 연결 조회 exit0: control BSON Long4/OPEN, cleanup0건, strict/error validator 및 `_id_`/subject unique/due/review 인덱스 확인. connectionStatus의 실효 privileges에서 collMod=false 확인. 앱 test12 실행1개, ALB initial 상태에서 안정화 대기.
- test12 기동 성공·container HEALTHY·workload/public ALB 모두 healthy 확인. 공개4API HTTPS 무인증401 유지. 점검 로그100개 ERROR0. 완료된 사전검증/설치 task 정의5개는 INACTIVE로 변경(실행 task 모두 STOPPED 검증 후), 로그 보존. ECS 최종 rollout 표시를 확인 중이다.
- 최종 ECS test12 COMPLETED/실행1/pending0, 두 target healthy 확인. cleanup reconciliation/purge=true 및 기존 withdrawal=true 재대조. Identity47의 기존 구매 scope/fence/publisher=true는 유지됐으며 이번 작업에서는 수정하지 않았다. diff --check 통과, commit/push/Jira 변경 없음.

## 남는 제한

실제 탈퇴 E2E·늦은 거래 수동 검토·cleanup 전용 Slack sink는 별도다. 현재 alerts flag는 구현되지 않은 sink 때문에 OFF 유지한다. 전체 재확인 대상과 삭제 성공은 설정 ON만으로 검증됐다고 하지 않는다.
