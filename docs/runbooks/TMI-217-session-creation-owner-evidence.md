# TMI-217 후속 — Session 생성 당시 owner 증거

- 날짜: 2026-10-14 / 브랜치: `feat/TMI-217-session-owner-evidence`
- 사용자 단일 병합 승인 및 후속 구현 요청에 따른 Billing 내부 기반 구현. Jira TMI-217 자체는 공동 계약 준비 범위이므로 이번 기반 구현을 해당 이슈 전체 완료로 표시하지 않는다.

## 1. 5줄 결론

1. 단일 병합 계약 보완을 commit01d1350·PR22·develop merge3b00a61로 병합했다.
2. PaidReservationService가 신규 AttemptSession을 만들 때 검증된 사용자/계정 또는 trial subject/epoch를 같은 Transaction에 기록한다.
3. 최초 owner 증거는 불변이며 재전송·confirm·계정 갱신으로 바꾸지 않는다. legacy 부재를 현재 owner로 추정하거나 backfill하지 않는다.
4. 이 기록은 생성 당시 귀속일 뿐 현재 삭제/접근 권한의 허용 근거 전부가 아니다. UserMerged 단일 이전의 현재 귀속 검증은 후속이다.
5. 신규 삭제 route/인증·ACK/원문정리·최종 purge·legacy writer coverage·운영 설치는 이번에 구현하지 않았다.

## 2. 저장 동작과 외부 계약

내부 optional `AttemptSession.creationOwner`는 environment(test/staging/production), kind(TRIAL_SUBJECT/MEMBER_ACCOUNT), userId, reference, epoch다. userId는 현재 reserve의 인증된 LC 전달값을 기존 MEMBER/owner 검증 뒤 사용한다. field를 public/internal wire에 추가하지 않는다.

- 최초 trial 및 trial replacement: reference는 실제 subjectRefId, epoch는 현재 검증된 sessionOwnerEpoch다.
- 유료 및 MEMBER 복원권: reference는 검증된 payment account ID, epoch=null. account version이나 phone epoch를 발명하지 않는다.
- object는 immutable record이고 다른 값으로 재설정할 수 없다. toString은 user/reference를 노출하지 않는다.
- reserve Transaction이 실패하면 Session/증거도 함께 rollback한다. 기존 receipt replay는 writer를 다시 호출하지 않아 원 증거를 변경하지 않는다.
- 기존 sourceSnapshot·응답 DTO·권리 선택·복원 수량·원장·기본 OFF 설정은 유지한다. 자동 schema DDL/이관/새 unique index 없음.

## 3. 반드시 구분할 한계

현재 계정 기반 paid runtime을 경유하는 Session만 새 writer가 적용된다. 기존 Guest/legacy 무료 writer의 Session은 creationOwner가 없을 수 있다. 따라서 이 구현만으로 전체 Guest→MEMBER 삭제 coverage가 완성됐다고 보고하지 않는다. 기존 source-null과 missing creationOwner는 unknown이다.

생성자 A가 저장되어 있어도 검증된 UserMerged로 B가 현재 소유자가 될 수 있다. creationOwner만 비교해 B를 거절하는 삭제 코드를 추가하지 않았다. phone 재가입과 single UserMerged 증거 연결/지연 재개는 draft-3의 공동 검토 조건을 따른다. 연속 병합 계보 탐색을 구현하지 않는다.

새 필드는 AttemptSession 기존 저장 형식을 확장하지만 기존 receipt r3/payment ledger r6 validator를 바꾸지 않는다. 운영 attempt_sessions의 별도 validator/reader 및 개인정보 연결 보존 조건은 rollout 전에 확인해야 한다. 현재 optional reader는 누락을 허용하며 실제 DB에 설치하거나 기록을 보정하지 않았다. 현재 삭제/보존 gate가 해소되기 전 활성화하지 않는다.

## 4. 검증

- SessionCreationOwnerIntegrationTest: paid confirm/replay/계정refresh 불변, MEMBER grant의 epoch=null, trial 원subject/epoch 보존, legacy 부재/backfill0, receipt 실패 시 Session/증거 rollback.
- SessionCreationOwnerTest: trial epoch 필수/양수, MEMBER epoch 금지, environment·canonical UUID·reference 검증.
- 테스트 DB는 Testcontainers replica-set이며 실제 운영DB/LC/Identity/Store 호출 없음.
- `./gradlew clean test`: 1,577개(신규7개 포함), 실패0/오류0/skip0. 기존 simplified80벡터/R2 오프라인 검사 및 diff 검사도 통과했다. 기존48개 공동 검증 계획 통과를 의미하지 않는다.

## 5. 다음 단계

신규 삭제 wire·eventId 보존 및 단일 merge 증거 coverage 공동 확인 → Guest/legacy writer coverage와 저장 조건 확정 → 삭제 receiver/상태·원문정리/ACK 연결·실제 경합 검증. 현재 변경은 Billing 내부 생성 근거의 좁은 기반이며 과거 owner 자동 복구나 삭제 서비스 전체 구현이 아니다. 기존 PLAN-012 병행 문서는 보존했다.
