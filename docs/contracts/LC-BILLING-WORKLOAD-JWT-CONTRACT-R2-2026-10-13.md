# LC JWT 전환 공동계약 — R2 검토 전달본

- 작성일: 2026-10-13
- 식별자: **LC-JWT-TRANSITION-R2**
- 용도: 공동 통합 계약 §16의 수정본 전달용 스냅샷. 정본은 [BILLING_SERVICE_INTEGRATION_CONTRACT.md](BILLING_SERVICE_INTEGRATION_CONTRACT.md)이며 독립 계약으로 관리하지 않는다.
- 상태: 문구 보정 완료. JWT exact 동결·양측 구현·실제 배포/활성화 완료 아님.

## 1. 5줄 결론

1. R1의 포괄적 Reservation404 문구를 body 없는 ingress404→CONTRACT_ERROR로 수정했다.
2. 업무 code=OPERATION_NOT_FOUND는 별도이며 둘 다 취소·미소비 확정이 아니다.
3. LC 승인 DEAD_LETTER 재처리 도구는 미준비로 기록하고 별도 승인/구현/검증을 선행 조건으로 명시했다.
4. issuer/audience·시간·오류 순서·공동 fixture·ADR/AGENTS 동기화는 미완료다.
5. Billing 수신 준비 후 LC 발신 활성화와 원본 보존/status-first/승인 재처리 원칙을 유지했다.

## 2. 반드시 읽어야 하는 변경

이번 검토 요청은 §16.3의 오류 구분과 §16.5의 도구 미준비 명시가 정확한지 확인하는 것이다. 404 문구 보정은 완료했고, 실제 재처리 도구와 인증 세부 동결은 남아 있다.

## 3. 결정해야 하는 사항

정본 §16.6의 exact claim/시간/오류 fixture 및 양측 구현 분담·ADR/AGENTS 개정 범위를 공동 동결한다.

## 4. 주요 위험

이 문서가 있다는 이유로 재처리 도구/인증 구현/배포가 준비됐다고 판단하지 않는다. LC 도구 미발견은 상대 보고이며 Billing의 독립 코드 검증은 아니다.

## 5. 수정된 공동계약 §16 전문

## 16. LC JWT 전환의 공동 활성화·404 보존/복구 절차

- 추가일: 2026-10-13 / 근거: 사용자 공동 계약 추가 요청, [Billing 검토·회신](LC-BILLING-WORKLOAD-JWT-REVIEW-2026-10-13.md).
- 검토 revision: **LC-JWT-TRANSITION-R2** — 빈 ingress404와 업무 OPERATION_NOT_FOUND 분리, 재처리 도구 미준비 명시, §16.6 동결 목록 추가.
- 상태: 공동 검토/동결을 위한 전환 절차 추가. 아래 보존·복구 원칙을 계획·검증에 적용하며, LC 코드의 독립 확인·양측 exact 인증 수락·실제 활성화는 별도 완료 증거가 필요하다.

### 16.1 인증 방향과 적용 범위

권장 방향은 RS256·TTL 최대60초·초기 배포형 공개키·목적/환경별 단일 audience·body 없는401/403이다. 기존 workload TLS connector를 공유할 수 있으나 LC/Identity 키·issuer·principal·인가·수신 활성화 설정은 독립이어야 한다. exact issuer/audience 문자열·설정 이름·시간/오류 precedence는 양측 fixture로 동결한다. 동적 JWKS나 새 인증 오류 JSON을 이번 절에서 추가하지 않는다.

대상은 LC→Billing의 다음 여섯 POST 업무 route다. 기존 body/성공 DTO/업무 오류·멱등성·상태 머신을 변경하지 않는다.

- `/internal/v1/reservations`
- `/internal/v1/reservations/{reservationId}/confirm`
- `/internal/v1/reservations/{reservationId}/cancel`
- `/internal/v1/reservations/status`
- `/internal/v1/reservations/continuations/phone`
- `/internal/v1/attempt-group-events`

JWT 전환 구현은 기존 LC IAM `permitAll` 통과 경로와 public/기존 internal port 우회를 차단해야 한다. JWT 실패 시 SigV4/무인증 fallback을 제공하지 않는다. 실제 legacy 발신이 있으면 차단 버전 배포 전에 양측 전환 계획을 확인한다.

### 16.2 Billing 수신 준비 후 LC 발신 활성화

1. 양측 현재 버전·활성 sender/worker·미완료 Reservation·미전달 outbox·대상 환경/origin/port·키/flags를 확인한다. 기본 OFF라는 코드 값만으로 실제 운영 OFF를 추정하지 않는다.
2. LC JWT sender를 준비하되 발신 OFF를 유지한다. Billing verifier·목적별 인가·정확 route·TLS/네트워크·관련 업무 기능을 먼저 준비한다. 신규 유입 제한과 진행 중 복구의 지속 여부는 inventory에 따라 구분한다.
3. 격리 환경에서 여섯 route의 정상 업무·응답 유실·중복 처리와 OFF/다른 port404,잘못된 token401,유효 token의 잘못된 권한403을 교차 검증한다. LC/Identity 토큰 상호 접근·public 접근·구 IAM 우회 실패와 LC ON/Identity OFF 및 반대 조합도 확인한다.
4. 실제 대상의 TLS/라우팅·키 배포·flags·업무 수신 가능 상태를 양측 담당자가 확인하고 기록한다. health200 또는 인증을 통과한 business400 하나로 전체 준비 완료를 판정하지 않는다. 기존 read-only 조회와 staging의 업무 E2E를 사용하며 새 readiness API를 임의로 추가하지 않는다.
5. **Billing 수신 준비 확인 후 LC 발신을 단계적으로 활성화한다.** 404·AUTH_FAILURE·계약 오류·DEAD_LETTER 증가 시 신규 활성화를 중단하고 origin/route/flag/버전 원인을 조사한다. 진행 중 saga/미전달 outbox 증거를 삭제하거나 worker를 일괄 OFF하지 않는다.

공유 connector를 사용하는 경우 어느 서비스의 OFF가 다른 서비스 수신을 끄거나 우회 허용하지 않아야 한다. 켜진 방향의 필수 신뢰 설정을 검증하고, 해당 방향의 OFF route는 다른 port에서도 차단한다. Identity 기존 네 이벤트·인증 fixture는 회귀 검증한다.

### 16.3 404의 기존 분류와 원본 보존

LC 검토 회신 기준 **body 없는 수신 OFF·잘못된 port404**는 Reservation에서 `CONTRACT_ERROR`, AttemptGroup publisher에서 `DEAD_LETTER`로 분류돼 자동 재시도가 중단된다. 이 분류를 유지한다. **업무 응답의 `code=OPERATION_NOT_FOUND`는 별도 업무 오류**이며 HTTP404 숫자만 보고 수신 계약 오류와 합치지 않는다. 어느 경우도 취소·미소비가 확정됐다는 뜻이 아니다. OFF/오포트404를 무조건 재시도 오류로 바꾸거나 성공/업무 부재로 처리하지 않는다. 이 구분과 원본 보존은 공동 fixture로 검증한다.

| 대상 | 보존할 기존 증거 | 수신 복구 후 처리 |
| --- | --- | --- |
| Reservation command/saga | operationId·원 업무 요청·Session commit 증거·상태/lease·실패 이력 | 기존 status-first로 결과 확인, 원 key/body로 필요한 confirm/cancel/retry만 수행 |
| AttemptGroup outbox | DEAD_LETTER의 원 eventId·canonical payload/digest·업무 버전/순서·실패 이력 | 원인 해결과 담당 승인 후 bounded requeue, 동일 eventId/payload/digest로 재처리 |

404가 발생한 작업은 정상 retention/기존 cleanup 정책 안에서 미해결 상태와 복구 증거를 관리한다. DEAD_LETTER는 복구 증거 삭제나 전환 완료의 근거가 아니다. 보존 만료 전 후속 처리가 필요하면 담당 검토로 다루며 개인정보 보존을 임의로 무기한 늘리지 않는다. token/Authorization/private key 원문을 복구 기록·로그에 남기지 않는다.

### 16.4 Reservation 복구 불변식

- status 조회 실패는 미확정이다. 새 operationId/새 시험 생성으로 자동 대체하지 않는다.
- RESERVED는 기존 TTL·Session commit 증거·confirm/cancel 계약에 따라 처리한다. 전환 지연을 이유로 TTL을 연장하지 않는다.
- CONFIRMED는 기존 결과로 수렴하고 소비를 다시 만들지 않는다. CANCELED/EXPIRED는 자동 repair-confirm하거나 재개하지 않는다.
- OPERATION_NOT_FOUND도 성공이나 미소비로 추정하지 않고 원 saga/Session 증거와 기존 복구 계약을 따른다.
- TrialClaim·Grant·owner fence·continuation·Consumption은 인증 전환 또는404 복구를 이유로 재발급/재작성하지 않는다.

### 16.5 AttemptGroup 재처리와 완료 증거

재처리에는 원 eventId·canonical payload/digest를 사용하고 전송 시도마다 새 JWT/jti/span을 생성한다. eventId·발생 시각·payload를 새로 쓰거나 기존 중복/역순/owner fence 처리를 우회하지 않는다. 재처리 뒤 stale/conflict/업무 거절이 나오면 기존 정책으로 처리한다.

2026-10-13 LC 후속 검토 보고에서는 확인한 코드·스크립트에 `DEAD_LETTER` 승인 재처리 도구를 찾지 못했다. Billing이 LC 전체 구현을 독립 확인한 결과는 아니지만, 현재는 재처리 도구가 준비됐다고 간주하지 않는다. **LC 담당의 별도 승인·구현·검증을 전환 선행 조건으로 둔다.** 권한·작업 상한·감사·재실패 격리·종료 기준을 runbook에 고정하고 DB 직접 수정·새 공개 repair API·무인증 fallback으로 대신하지 않는다. Billing 담당자는 수신 준비와 기존 eventId/digest 멱등 수렴 증거를 제공한다.

공동 완료 기준은 원본 작업의 최종 수렴 또는 담당자가 기록한 미해결 사유/후속 조치다. queue 상태 초기화나 원본 삭제를 성공으로 간주하지 않는다. OFF/다른 port404 뒤 원본 보존, 승인 requeue,응답 유실과404 경합의 status-first,만료/취소 Reservation 불변성을 공동 fixture/E2E에 포함한다. 실제 키·네트워크·양측 코드 및 복구 도구 검증 전에는 전환을 활성화하지 않는다.

### 16.6 다음 동결·구현 준비 항목

1. issuer·subject·목적/환경별 audience의 exact 문자열, 시간 NumericDate/TTL/skew 경계, 수신 OFF/port와401/403의 오류 우선순위 및 설정 이름을 양측 수락본으로 고정한다.
2. body 없는 ingress404→CONTRACT_ERROR와 업무 OPERATION_NOT_FOUND의 구분, 키/시간·교차 목적·OFF/port·원본 보존·승인 재처리 공동 fixture/hash manifest를 준비하고 양측 독립 실행한다.
3. 인증 변경 범위와 기존 IAM 통과 제거·TLS 공유/독립 flags를 ADR-002·AGENTS 및 양측 계획에 동기화한다. 기존 Identity 승인 규격·사용자 JWT·업무 body/멱등성/복구 계약을 유지한다.
4. Billing 수신 구현과 LC sender/승인 재처리 도구의 작업 범위·담당을 확정한다. 도구 검증·실제 네트워크/키·미완료 작업 inventory·staging E2E를 마친 뒤 별도로 활성화한다.

이번 보완은 위 미완료 항목을 확정·구현·배포한 기록이 아니다.
