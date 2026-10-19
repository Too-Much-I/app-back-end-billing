# TMI-205 관측 기반 미구매 계정 삭제 — 사용자 승인

2026-10-17 / codex/billing-test-deployment

## 결론

사용자는 ‘이 조건으로 단순화 — 지연 거래의 수동 검토 위험 수용’을 명시 선택했다. 이전 계약의 **RC 밖 미관측 Store 거래까지 부재 증명** 조건만 이 범위에서 대체한다. API/body·금융 보존기간·무료권·Identity 발급 정책은 변경하지 않는다. 실제 배포/활성화 완료가 아니다.

## 삭제 조건

1. Identity 탈퇴 사실을 반영한 WITHDRAWN 상태이며 탈퇴 후 15일 경과.
2. 로컬 구매/기간권/timeline 없음, pendingEvidence0, 미해결 inbox/case/sync 및 조회 lease 없음. 연결 불명 같은 환경 자료도 보류.
3. 모든 현재/과거 ref의 인증된 RC 구매 조회가 해당 환경에서 정상 종료. 404·부분 page·미지원 거래·인증/일시 오류는 보류. 처음부터 구매 계정/ref가 없었던 lifecycle은 조회할 ref가 없음을 확인하고 로컬 조건으로 판단.
4. 구매 요청 토큰은 Billing에서도 exp-iat≤30분, skew≤60초. 마지막 계정 조회 활동에서 최소32분 경과(미래 iat/skew 각각60초 포함). DB 작업은 기존 authorizationDeadline 전후 검사 및 2초 Tx bound를 유지한다. 탈퇴15일 자체로 Store 결제 종료를 주장하지 않는다.
5. proof 최대1시간과 같은 lifecycle/account/activity/epoch, 최종 로컬 재검사·공통 fence/CAS·30초 lease·원자 삭제·100문서 상한 유지. READY 직후 시도, 지연으로 증거가 만료되면 재조회.

## 수용한 위험과 변경하지 않는 책임

- 성공한 RC 전체 조회도 아직 전달되지 않은 Store 거래까지 없다는 증명이 아니다. 이 드문 지연 위험을 수용한다. 후속 거래는 durable 수동 검토로 격리하며 자동 지급/계정 재생성/재가입 이전하지 않는다. 삭제된 ref 때문에 구매자 연결 확인이 어려워지고 문의 처리가 길어질 수 있다.
- `NO_PURCHASE_CONFIRMED`는 위 관측 기반 정책의 판정값이다. 모든 과거/미래 Store 거래 부재라는 절대적 의미가 아니다.
- Identity의 탈퇴 후 발급/refresh 차단·userId 재사용 금지 계약은 유지한다. Billing의 TTL 검사만으로 Identity 발급 차단을 증명하지 않는다. 활성화 전 상대 설정/토큰 수명 확인이 필요하며 Identity 수정은 이번 범위 밖이다.
- known purchase는 환불/취소 상태라도 보존. 삭제 대상은 계정/ref/lookup cursor/cleanup job/불필요 lifecycle뿐이며 금융·무료·시험 자료를 추가 삭제하지 않는다.

## 증거 버전·배포

`CleanupDeletionPolicy.VERSION=observed-absence-r1` 한 값으로 판단 규칙을 관리한다. 설치된 r2 schema의 세 필드에는 같은 값을 기록한다. 기존 서로 다른 버전의 proof는 새 정책으로 변환하지 않고 재확인한다. **물리 필드의 cleanupPolicyVersion 단일화/schema 이관은 이번에 하지 않았다.** 별도 schema 변경 없이 기존 validator를 재사용한다.

reconciliation ON은 실제 provider runtime/schema/budget을 요구하며 purge ON은 reconciliation/fence를 요구한다. 기본 OFF 유지. schema 설치·worker 이관·Identity 토큰/탈퇴 차단·관측/중단 절차 확인 후 별도 서버 활성화한다. Slack sink 미연결과 운영 미해결 보존 기준은 자동으로 해결됐다고 보지 않는다. RevenueCat 고객 자체 삭제/실 Store 환불 실행은 포함하지 않는다.

## 검증 근거

기존 실제 PurchaseCore 수신 선행→purge 재검증, purge 선행→늦은 거래 수동 검토/no recreation 테스트를 유지한다. 신규 토큰 TTL 경계·계정/sync HTTP 거절·만료 요청의 삭제 후 재생성 금지·실 local reader와 runtime purge·policy 수명/활동/이전 proof 거절을 검증한다. 전체 결과는 WORKLOG에 별도 기록한다. 실제 sandbox Store E2E는 이번 로컬 테스트가 대신하지 않는다.
