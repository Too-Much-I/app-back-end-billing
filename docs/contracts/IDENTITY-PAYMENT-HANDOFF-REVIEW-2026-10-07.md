# Identity 구매 권한·탈퇴 인계 회신 검토

- 작성일: 2026-10-07 / 브랜치: develop / 문서 검토, 구현·계약 확정 아님
- 대상: 사용자 첨부 Identity 정적 검토 회신과 [Billing 인계서](IDENTITY-PAYMENT-ACCOUNT-LIFECYCLE-HANDOFF.md).

## 1. 5줄 결론

1. 회신은 기존 인계의 미확정 gate를 구체화하며 제품 정책을 뒤집지 않는다.
2. issuer의 default/explicit scope 경로와 lowercase 검증 공백은 로컬 코드에서 재확인했다(§6).
3. LC publisher의 모든2xx 성공 처리와 Billing204-only 요구 차이, backfill의 bounded 조회는 재확인했다(§6).
4. 토큰 최종 발급 경계·독립 탈퇴 원천 보존·snapshot과 commit 경계는 다음 공동 설계 대상이다.
5. 현재 판매/purge 안전성이 검증됐다고 볼 수 없으며 코드·외부 계약·운영 설정은 이번에 변경하지 않는다.

## 2. 사용자가 반드시 읽어야 하는 내용

현재 정상 구매 발급이나 공개 API를 통한 임의 scope 주입 취약점이 입증됐다는 뜻은 아니다. 향후 purchase scope를 추가할 때 default/explicit 모두 ACTIVE MEMBER 제한을 반드시 적용해야 한다는 의미다. 단순 설정 추가는 불충분하다.

기존 LC 탈퇴 처리는 LC 계약에 따른 구현이다. Billing204-only·BLOCKED_AUTH·Retry-After 요구와 다르므로 Billing destination adapter/정책을 분리해야 하며 LC를 일괄 수정할 이유는 없다.

과거 outbox가 사라졌더라도 tombstone이 남으면 별도 snapshot 사실 계약으로 복구할 수 있다. 하지만 원 eventId를 복원했다고 주장하거나 name UUID를 원 event로 둔갑시키면 안 된다. tombstone도 없는 범위는 coverage gap으로 보고한다.

## 3. 사용자가 결정해야 하는 사항

15일·매시간 점검·매일 재확인·7일 이내 담당 검토는 유지한다. 새 사실 원천/예외 개인정보의 최소 필드·보존기간, coverage 공백에 따른 사용자 제한 또는 서비스 중단이 필요할 때만 추가 정책 승인을 요청한다.

구매 scope 필터·발급 fence·ACK·Retry-After·snapshot 기술 규격은 먼저 개발 권장안을 작성해 양 서버 검토를 받는다. 이번 회신을 구현 승인으로 취급하지 않는다.

## 4. 주요 위험과 미확인 사항

- 운영 최장 token lifetime·과거 설정·모든 발급 및 암호화 응답 replay 경합은 미검증이다.
- withdrawnAt/_id 순회만으로 watermark 이전 시각을 가진 늦은 commit을 포착할 수 있다고 보장할 수 없다. snapshot 시점과 durable 증분 원천의 commit 경계를 연결해야 한다.
- 별도 원천을 만드는 것 자체로 무기한 보존이 허용되지는 않는다. capture OFF와 publisher OFF를 구분하고 LC cleanup과 독립된 coverage/보존 계약이 필요하다.
- lowercase 변환으로 저장된 소유 ID를 임의 변경하지 않는다. legacy 데이터 조사 후 검증/이관안을 정한다.
- Billing provider 미구매 증명/예외 보존은 Identity 완료와 별개의 미해결 gate다.

## 5. 다음 공동 설계 권장안 — 미확정

| 항목 | 권장 방향 | 완료 근거 |
| --- | --- | --- |
| purchase scope | trusted ACTIVE MEMBER 상태에서만 합성, Guest default/explicit 모두 차단 | 전체 issuer 진입점 및 상태별 테스트 |
| 발급 경합 | 서명·commit·응답/replay 시점을 분리하고 공통 fence/발급 상한 설계 | 탈퇴 경합·rollback·응답 유실·지연 테스트 |
| capture/delivery | authoritative 원천과 destination별 상태/cleanup 분리 | Billing OFF/LC 완료 뒤에도 복구 가능한 coverage |
| Billing ACK | exact204만 완료, 다른2xx는 성공 처리 금지 | adapter/publisher 회귀 |
| auth/retry | BLOCKED_AUTH 및 동일 event 재개, Retry-After 범위/날짜 지원 여부 명시 | 정수1~300초 현행 지원과 신규 요구 차이 합의 |
| snapshot/feed | 기존 event 재전송과 과거 snapshot 사실을 구분, commit-consistent cutover | 늦은 commit/동일 timestamp/cursor gap 테스트 |
| token drain | 최종 발급 가능 시각+최대TTL+skew+Billing 진행 요청 수명 고려 | 실제 설정/과거 token/모든 경로 검증 |

다음 문서는 위 항목의 공동 기술 계약 보완안이다. source 보존·snapshot 합의 → purchase 발급 경합 설계 → OFF 구현/테스트 → staging → 단계 활성 순서이며 실제 판매/삭제는 별도 승인이다.

## 6. 부록 — 근거와 검증 범위

- [JwtAccessTokenIssuer](../../../identity/src/main/java/web/tosunsaeng/identity/global/security/jwt/JwtAccessTokenIssuer.java): orderedScopes의 default/explicit 선택 후 read 추가, equalsIgnoreCase 검증을 재확인했다.
- [UserWithdrawnPublisher](../../../identity/src/main/java/web/tosunsaeng/identity/domain/user/withdrawalevent/application/UserWithdrawnPublisher.java): 200~299 성공과408/425/429/5xx retry 분기를 재확인했다.
- [UserWithdrawnBackfillService](../../../identity/src/main/java/web/tosunsaeng/identity/domain/user/withdrawalevent/application/UserWithdrawnBackfillService.java): withdrawnAt/_id 정렬과 최대100건 query를 재확인했다.
- 상세 signup/upgrade/merge·response replay 경합, cleanup30일·name UUID·Retry-After1~300초는 첨부 Identity 회신의 조사 결과로 사용하며 이번 Billing 검토에서 전체 경로 실행 검증은 하지 않았다.
- 문서만 작성, Gradle/외부 서비스 호출 없음. Identity 코드/기존 Billing 계약 변경 없음. 실제 파일 diff 및 신규 문서 링크를 정적으로 검사한다.
