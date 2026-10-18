# 환불 운영 실행 조건 보완 — TMI-220 후속

## 1. 5줄 결론

1. 기존 test 전용 환불 기동 제한에 명시적인 prod 조합을 추가했다. 기본 OFF와 기존 두 스위치는 유지한다.
2. prod 프로필 하나·production workload·PRODUCTION payment·실제 `*-billing-prod` DB·recovery fence가 모두 맞아야 한다.
3. test 동작은 유지하고 혼합 프로필·다른 환경/DB·fence 누락·processing OFF/publisher ON은 거절한다.
4. schema/원장/lease/불변 outbox/JWT/HTTPS/exact204/재시도·Slack 의존성은 그대로다. 설정 검증 통과는 업무 준비 완료를 뜻하지 않는다.
5. 운영 서버·DB·키·LC/Identity·Jira는 변경하지 않았으며 실제 배포/활성화/공동 E2E는 후속이다.

## 2. 반드시 읽을 조건

| 항목 | 테스트 | 운영 |
| --- | --- | --- |
| 단일 Spring profile | test | prod |
| BILLING_ENVIRONMENT | test | production |
| BILLING_PAYMENT_ENVIRONMENT | SANDBOX | PRODUCTION |
| 실제 PaymentStore DB | 기존 `...-test` 또는 `..._test` | `[A-Za-z0-9_-]+-billing-prod`, 최대63자 |
| BILLING_PAYMENT_RECOVERY_FENCE_REQUIRED | true | true |

`BILLING_PAYMENT_REFUND_PROCESSING_ENABLED`는 환불 반영/순차 정리, `BILLING_PAYMENT_REFUND_PUBLISHER_ENABLED`는 LC 전달이다. 둘 다 기본false이고 OFF/OFF, ON/OFF, ON/ON만 허용한다. 별도 fanout/승인 스위치를 신설하지 않는다. ON/OFF에서는 처리 결과와 outbox를 보존하고 전달은 하지 않는다.

운영 태스크의 현재 프로필이 없으면 명시적으로 prod 설정을 준비해야 한다. `production`은 wire 환경값이지 Spring profile 별칭으로 허용하지 않는다. DB 접미사는 구성 실수를 검출하는 장치이며 실제 운영 DB 소유권/네트워크 격리 증명이 아니다.

## 3. 변경하지 않는 검사

- PaidExamConfiguration의 schema 확인/실제 transaction·MemberOperationRecovery 의존성.
- RefundRuntimeConfiguration의 환경 매핑/fence/실제 fanout·알림 sink 의존성.
- RefundAccessPublisher의 HTTPS origin·RS256 signer·schema 검사와 exact204, 응답유실 재시도, immutable body/eventId, lease/generation.
- 구매별 원장·환불 전 confirm Session/기접수 예외·삭제/탈퇴/terminal 우선 정책.
- Store 환불 신청/실행 기능을 추가하지 않는다. 검증된 환불 사실의 반영 및 LC 전달에만 해당한다.

## 4. 검증과 미확인

2026-10-18 KST 최종 `./gradlew clean test`: 1838개/실패0/오류0/skip0(3분59초), 신규 운영 설정 회귀32개 포함. 최초 실행의 메시지 호환 실패1개를 기존 접두사 보존으로 수정한 뒤 전체 재실행했다. `git diff --check` 통과.

새 PaymentRefundProductionActivationTest는 정상 두 flag 조합, 환경/프로필/DB 오설정, fence 부재, OFF 무의존, 실제 Store DB 이름 사용, runtime 의존성 누락 시 기동 실패를 검사한다. 기존 테스트와 전체 회귀 결과는 WORKLOG에 기록한다. 단위 configuration runner 성공은 전체 prod application context나 실제 LC E2E 성공이 아니다.

## 5. 배포 전 다음 작업

1. 운영 issuer/공개키·scope·HTTPS origin·TLS/ALB/SG·키 주입과 LC 수신 준비를 확인한다.
2. 운영 DB schema/legacy 증거/보존·복구 control, 실제 provider 결과 및 알림 sink를 점검한다. 불명 데이터를 정상으로 승인하지 않는다.
3. 검증된 코드 배포 후 별도 승인으로 기능 활성화하고 공동 서명/204/중복/응답유실/보호 Session 검증을 한다.

현재 운영 prod:3 이미지에는 이 로컬 변경이 없다. Identity 구매 scope 준비는 별도이며 본 변경으로 해소되지 않는다. 이번에 DB control을 OPEN으로 바꾸거나 운영 기능을 켜지 않았다.
