# 결제 잔여 작업 확인 — TMI-212 이후 착수 순서

> 후속: 사용자 승인으로 A의 [public sync 연결](../runbooks/TMI-212-public-sync-wiring.md)을 구현했다. 아래 무조건 기동 차단/service bean 부재는 조사 당시 상태다. B/C와 기본OFF·판매 보류는 유지한다.

## 1. 5줄 결론

1. 다음 구현 단위는 **공개 sync의 실제 서비스 연결·기동 준비 검사**다. 단순히 ON 차단 한 줄만 제거하면 안 된다.
2. 백그라운드 worker는 PaymentSyncService를 직접 생성하지만 공개 controller에 주입할 Spring bean은 현재 발견되지 않았다.
3. 환불은 검증 관측을 받아도 PLAN012 검토 대상으로 넘기는 단계다. 기존 구매의 권리 종료·금액 원장·기간 재배치가 완료된 것은 아니다.
4. 유료 시험 입장/실제 canTakeExam 판정은 PLAN011이며 LC R2는 공동 검토 상태다. 해당 wire를 임의로 확정하지 않는다.
5. 5년 금융 정리는 후속 분리 승인을 유지한다. 실제 판매/배포/Store 검증·Jira 완료 전환은 이번 조사 범위가 아니다.

## 2. 사용자가 반드시 읽어야 하는 내용

현재 “구매를 검증하여 원장에 반영하는 내부 처리”와 “앱이 실제 운영 API로 그 처리를 요청할 수 있음”은 다르다. PublicSecurityConfig는 sync-enabled=true이면 무조건 예외를 던진다. 이 차단을 지워도 PaymentSyncController의 ObjectProvider에 실제 서비스가 없으면503이 된다. 따라서 가장 먼저 해야 할 작업은 이 두 경계를 함께 연결하는 것이다.

환불을 먼저 판매 환경에서 켜거나 결제가 이미 완성됐다고 보면 안 된다. PurchaseCore의 non-PURCHASE 경로는 REVERSAL_REQUIRES_PLAN_012 검토 인계이며, webhook의 CANCELLATION도 바로 환불 사실로 단정하지 않는다.

## 3. 다음 구현 범위 제안

### A. TMI-212 마무리 — public sync 연결

- 현재 worker와 같은 환경/app/key/catalog 설정으로 공개 PaymentSyncService를 명시 구성한다. controller용 service와 worker용 service의 검증 기준이 달라지지 않도록 구성 경계를 재사용한다.
- 무조건 기동 실패를 실제 dependency/config/schema 준비 검사로 교체한다. worker/회복 fence/기존 JWT·public ingress/등록 schema가 없으면 실패한다. 임의 ready=true나 테스트 profile 우회는 만들지 않는다.
- 기본OFF는 유지한다. 로컬에서 구조적 준비를 검사할 수 있다는 것과 실Store/운영 준비 확인을 구분하며 코드가 실계정 검증을 증명한다고 주장하지 않는다.
- 정리·알림이 모두 무조건ON이어야 한다고 새 의존성을 늘리지 않는다. 5년 정리 기능은 준비 조건에 넣지 않는다. 운영 알림/권한/보존 대응은 기존 배포 체크리스트에서 확인한다.
- 실제 HTTP controller→Mongo 접수→공통 worker→검증된 구매→재조회 완료 경로를 fake provider/local replica-set으로 검증한다. 중복·응답 유실·타사용자·탈퇴·Guest·잘못된 token·실패시503/202를 기존 계약대로 검사한다.
- 성공 조건: 같은 서비스 설정으로 context 기동 및 전체 흐름 검증, 부족한 설정은 실패, OFF 차단 유지, 외부 wire/지급정책 불변, clean test bootJar 성공. 운영 ON/판매 승인은 별도다.

### B. PLAN011/012 구현 준비

PLAN011은 LC R2 exact 수락 상태를 확인한 뒤 유료 reserve/confirm·공통 진행 상태·최소 구매 이용 증거·canTakeExam 판정을 구현한다. 현재 ExamAdmissionReader는 interface/unavailable 기본 구현이며 paid 가능 여부를 추정하지 않는다. LC 동시 작업 파일을 임의 수정하지 않는다.

PLAN012는 검증된 환불 입력 모델부터 기술 규격을 닫고 구현한다. 최초 접근 종료와 금전 사실을 분리하고 source root 차단/기간 재배치/후속 처리 등록을 원자적으로 처리한다. 부분 반환액 증거가 없으면0/전액으로 추정하지 않는다. RC/Google 실제 지원과 fixture 검증은 별도 gate다.

최신 승인 정책은 **환불 전 confirm된 exact Session은 기존 제출 기한 안에서 완료 가능**, 이후 신규/재응시는 차단이다. 기존 PLAN012 본문에 남은 GRADING-only/전용 승인 API/SigV4 문구는 최신 R2/정책으로 대체되는 역사적 내용이며 새 코드 기준으로 사용하지 않는다. 구현 착수 전에 실행 대상 섹션을 최신 정본과 동기화한다.

### C. 운영 검증

schema 명시 설치·실제 Identity JWT·ALB/키·RC/Store sandbox·Slack secret/시험 발송·운영자 권한과 복원 검증. 환불 처리/revoke 준비보다 판매·유료 입장을 먼저 활성화하지 않는다. 서비스 배포와 외부 설정 변경은 별도 승인이다.

## 4. 사용자가 결정할 사항

이번 확인으로 새로운 상품 정책 결정은 발견되지 않았다. A의 내부 연결을 다음 구현 대상으로 권장한다. 새 Jira 생성/댓글/상태 변경은 요청하지 않았으므로 수행하지 않았다. PLAN011/012의 미확정 서비스 간 기술 계약은 별도 공동 수락이 필요하다.

## 5. 검증 범위·위험

코드/문서 정적 조사와 Jira TMI-137 하위 issue 조회만 수행했다. 하위 목록에는 TMI-212까지의 관련 작업이 보이고 다수 상태가 '해야 할 일'이지만 이를 로컬 구현 부재로 해석하지 않았다. 새 PLAN011/012 이슈 존재 여부를 프로젝트 전체에서 확인한 것은 아니다. Gradle/실서비스 호출은 수행하지 않았으며 이전1465통과를 이번 연결 구현 성공으로 재사용하지 않는다.

## 6. 근거

- [공개 기동 차단](../../src/main/java/web/tosunsaeng/billing/global/config/security/PublicSecurityConfig.java)
- [공개 sync 주입 경계](../../src/main/java/web/tosunsaeng/billing/domain/payment/api/PaymentSyncController.java)
- [worker의 직접 service 생성](../../src/main/java/web/tosunsaeng/billing/domain/payment/worker/PaymentProviderWorkerConfiguration.java)
- [환불 검토 인계](../../src/main/java/web/tosunsaeng/billing/domain/payment/purchase/PurchaseCore.java)
- [시험 가능 판정 경계](../../src/main/java/web/tosunsaeng/billing/domain/payment/query/ExamAdmissionReader.java)
- [LC R2 공동 검토 상태](../contracts/LC-BILLING-PAID-EXAM-CONTRACT-R2.md)
- [환불 계획](PLAN-012-refund-ledger-and-access-revocation.md)
