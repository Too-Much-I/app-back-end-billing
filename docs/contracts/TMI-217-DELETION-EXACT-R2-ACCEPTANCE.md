# TMI-217 deletion exact R2 — Billing 수락 접수·구현 기준 동결

- 날짜: 2026-10-16 / revision: `lc-billing-deletion-exact-r2-draft-3`
- 근거: LC `docs/codex/TMI_217_DELETION_EXACT_R2_ACCEPTANCE.md` 원문 및 사용자 전달.

## 1. 결론

1. LC가 두 미결 규칙과 구현 기준 계약을 수락한 사실을 Billing에서 확인·접수했다.
2. 적용 우선순위는 [draft-3](TMI-217-DELETION-EXACT-R2-DRAFT-3.md) > [draft-2](TMI-217-DELETION-EXACT-R2-DRAFT-2.md) > [draft-1](TMI-217-DELETION-EXACT-R2-DRAFT-1.md)이다.
3. 양측 구현 기준 기술 계약은 동결 상태로 기록한다. 향후 의미 변경은 새 revision과 공동 수락이 필요하다.
4. 원문 해시를 보존하기 위해 세 초안은 수정하지 않는다. 원문의 재확인 대기 표기는 이 수락 기록으로 상태만 갱신한다.
5. 전체 fixture 완성·검증, 제품 구현, DB 설치, 배포·활성화 완료나 승인을 뜻하지 않는다.

## 2. 확정 규칙

- retryNotBefore: 기존 하한과 새 유효 후보 중 최댓값을 보존한다. 누락·invalid·중복·더 이른 헤더로 단축하지 않는다. 다음 발신은 backoff와 하한 중 최댓값이며 I/O·lease 재획득도 우회하지 않는다. 24시간 경계와 재시도 한도, 운영 확인 상태 및 자동 reset 금지는 유지한다.
- correlationId: 새 삭제 route 업무오류의 필수 non-null 문자열. ASCII `[A-Za-z0-9._-]` 전체 일치 1~128자이며 UUID 필수가 아니다. 다른 API·인가·멱등성 규격을 변경하지 않는다.
- LC 사용자 삭제 인가 책임, Billing exact 대상·현재 권리 영향 확인, 동일 event 충돌 우선, 이미 삭제된 대상의 새 event no-op204, 안전한 원문 정리와 전달 분리, 필요한204 전 신규 시험 차단을 유지한다.

## 3. 검증·미완료 경계

Billing에서 아래 세 원문 SHA-256이 LC 수락 기록과 일치함을 확인했다. 새 사례 해시도 재확인했다. LC의 하한6개·correlationId8개와 길이경계3개·정상503 검사는 상대 독립 test-only 결과이며 Billing runtime 결과로 합산하지 않는다.

서버 경계·경합31개, 전체 scalar/JWT suite, 실제 HTTP/DB/worker 통합 검증은 남는다. 기본 OFF와 기존 활성화 gate를 유지하며 최종 물리 purge는 별도다. 새 제품 정책 선택은 없고 다음은 남은 fixture 제작·독립 검증 및 별도 구현 작업이다.

## 4. 동결 원문 식별

| 원문 | SHA-256 |
| --- | --- |
| draft-1 | `20b07017d8d91d5d60c5d454887e84ad705befecd99ad205f65625268b95f088` |
| draft-2 | `b9b71cbe877bed7213a0274114771f4795303d9ce2757f8a3ec4cd435f0af730` |
| draft-3 | `daafb38a3133e343049dc028368ccffdb902ac2186493754621602a879c0257c` |

draft-3 cases.json: `56dfc463ad2ad77865342ee104682b51aefe36f05dfac788f3cbac450ec1b75d`.

이번은 수락 접수 문서·인계·상태·작업 기록만 변경했다. 제품/fixture 원본/LC/DB/Jira/외부전송/배포/commit/push 변경 없음. Gradle은 문서 작업이라 실행하지 않았다.
