# Billing 운영 유료 시험·삭제 수신·상품판매·sync 활성화

> 최종 2026-10-18 15:03 KST: prod:9 단일 PRIMARY COMPLETED, running1/pending0, 신규8083/8443 healthy. 요청 설정15개 true, DB OPEN/v3. 구 target은 draining 잔여 상태이며 실행 태스크는 prod9만 확인했다. 실제 구매·시험·삭제 E2E와 LC 배포는 별도다.

## 현재 결과

- 사용자 `켜줘` 및 운영 삭제 경로 추가 승인에 따라 prod:7에서 설정 12개만 변경한 prod:8을 등록하고 배포 요청했다. 배포 완료와 DB OPEN 결과는 아래 후속 관측으로 확정한다.
- ALB 우선순위125에 workload-prod host·POST `/internal/v1/exam-deletions/events`·기존 지정 NAT /32만 HTTPS8443으로 전달하도록 추가했다. 기존 모든 규칙 동일성을 재조회했다.
- Identity prod:16, Billing prod:7의 단일 COMPLETED/실행1/대기0를 변경 전에 확인했다. Identity 서버는 변경하지 않았다.
- 운영 DB snapshot 읽기: PURCHASE_BLOCKED/version2, 기존 purchases/payment_accounts/subscription_entitlements/reservations/attempt_groups/attempt_sessions/billing_subject_links/entitlement_grants/trial_claims 각0. Billing 귀속 이관 대상이 없다는 근거이며 LC 기존 시험 봉인 완료를 뜻하지 않는다.
- 실제 삭제204 및 LC ACKED 확인은 배포 후 프론트 검증으로 분리한다. 미실행 결과를 완료로 기록하거나 LC billingDeliveryVerified를 임의로 true로 만들지 않는다.

## 후속 관측

- prod:8 신규 태스크 RUNNING/HEALTHY, 앱8083·workload8443 healthy. 기동 로그 Started1/ERROR0/실패0 확인. 구 prod7은 실행0/draining으로 전환했다.
- 단일 운영 control의 `_id/environment/consumer/schemaVersion/epoch/version/barrier`를 조건으로 majority+j CAS를 실행했다. PURCHASE_BLOCKED/version2 → OPEN/version3, epoch 보존 및 readback 성공(exit0). 사용자 구매·시험·삭제 자료를 생성하거나 삭제하지 않았다.
- 최종 prod9 rollout COMPLETED(15:03 KST), running1/pending0. app4API 무인증401, app host 내부삭제404. 등록 설정15개true 및 제외flag false readback 완료. 새 태스크 Started1/errors0/실패0, 실제 정상서명 업무 성공을 뜻하지 않는다.

## 적용 범위

기존 activation.draft.json의 12개 environment override만 true로 바꿨다. image/roles/secrets/ports/health/기존 environment는 보존했다. 기존 Identity receiver/withdrawal과 LC receiver/TLS는 유지한다. PaidExamSchema/PurchaseCoreSchema/DeletionSchema는 기동 시 검사하며 실패하면 활성화 완료로 취급하지 않는다.

최초 적용에서는 catalog 판매·sync·provider/webhook/재조회·환불 처리/발신·자동삭제/보존정리·snapshot/feed를 제외했다. 이후 사용자가 상품 판매·결제 sync도 요청하여 아래 세 설정만 추가 ON으로 확장했다. LC 코드·배포·기존 앱 경로는 변경하지 않는다.

## 사용자 추가 요청 — 상품 판매·sync

- `BILLING_PAYMENT_CATALOG_ENABLED`, `BILLING_PAYMENT_SYNC_ENABLED`, `BILLING_PAYMENT_PROVIDER_WORKER_ENABLED`를 함께 true로 적용한다. sync는 provider worker에 기동 의존하며 DB 접수 후 공통 worker가 RevenueCat을 조회·검증한다.
- 운영 상품10개 모두 saleEnabled=true였고 공유 budget collection/index 접근 및 OPEN 상태를 확인했다. 상품 가격/기간/매핑·Secret값·권한·스키마를 변경하지 않는다.
- 주기적 reconciliation, webhook ingress, 환불 처리/발신, 자동삭제/보존정리, snapshot/feed는 그대로 OFF다. 실제 Store 거래·정상 로그인 sync·권리 지급은 이번에 수행하지 않는다.
- prod8 단일COMPLETED를 확인한 뒤 prod9 등록/배포했고 정상 완료했다. 이미지와 비밀값·역할 변경 없음. 증거 `/private/tmp/billing-prod9-enabled-20261018.png`.

## 검증과 후속

제품 코드 변경 없음. 직전 전체1838개 및 스키마 도구21개 통과 기록을 유지하며 이번에는 Gradle을 재실행하지 않는다. ECS/ALB 기동·DB barrier·무인증 차단을 확인한다. 실제 정상 사용자 시험/삭제와 Billing fact/receipt·LC ACKED는 후속이다. 기존 ACK 저장·신규 시험 차단 계약은 유지한다.
