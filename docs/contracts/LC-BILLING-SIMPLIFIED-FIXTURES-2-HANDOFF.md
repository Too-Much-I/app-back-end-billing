# Simplified fixtures-2 — E06-owner 문구 정정 인계

- 날짜: 2026-10-14 / 관련 TMI-216 / 브랜치 feat/TMI-216-lc-paid-exam-integration
- revision: `lc-billing-paid-exam-simplified-exact-r1-fixtures-2`
- 변경: E06-owner message를 `The operation owner does not match the current owner.`로 정정했다. HTTP409·OPERATION_OWNER_MISMATCH·retryable=false·기대 OWNER_BLOCKED는 그대로다.
- 기존 fixtures-1의 10파일은 작업 전후 SHA-256이 동일하다. 새 버전의 데이터 차이는 해당 message 하나이며 README/revision/manifest/hash만 함께 갱신했다. 계약 snapshot과 나머지 데이터 파일은 byte 동일하다.
- 생성기를 수정하고 검사기에 owner message exact 비교를 추가했다. 검사기 기본 대상은 fixtures-2이며 구 revision을 검증 성공으로 표시하지 않는다.

## 전달 자료와 해시

- 원본: `src/test/resources/contracts/lc-billing-paid-exam-simplified-exact-r1-fixtures-2`
- ZIP: `build/handoffs/lc-billing-paid-exam-simplified-exact-r1-fixtures-2.zip` (build 정리 시 삭제될 수 있음)
- SHA256SUMS 자체 SHA-256: `4e91332dfa549da63d13f3e07a1ec208018e02a353ccd780f12b8570eb2d4845`
- ZIP SHA-256: `206c83a6ba8865650a07db3a56f67b210a0ad795d8b123cce811ef10ab2c6cb2`
- 계약 SHA-256: `79bcfe3d79225c21d712791266bea10d4aed9eed3dc0529ddf86f7b29673a482`

## 검증 결과와 다음 단계

`python3 scripts/contracts/verify-paid-exam-simplified-fixtures-r1.py`: errors22/public4/time14/member29/phone11 = 80벡터 통과, 9파일 hash 모두 OK. 기존 잘못된 E06-owner는 CONTRACT_ERROR, 정정본은 OWNER_BLOCKED로 판정되는 회귀 확인. ZIP10파일을 원본 bytes와 재대조했다. 상태28개는 미실행 유지다.

LC는 새 ZIP/hash를 대조하고 자체 독립 검증을 재실행하면 된다. 생산자 manifest의 lcIndependentValidation=false는 유지한다. 이번에는 fixture/도구/기록만 변경해 전체 Gradle은 재실행하지 않았다. runtime 오류를 발견·수정했다는 의미가 아니며 정책/API/제품 코드/DB/배포/타 저장소/외부 발송/commit/push 변경 없음. 기존 TMI-216 및 PLAN-012 작업을 보존했고 예상 밖의 이번 변경은 없다. T04와 다른 공동 gate는 별도다.


## 2026-10-14 — LC 독립 재검증 회신 접수

사용자 전달 및 LC `docs/codex/LC_BILLING_SIMPLIFIED_FIXTURES_2_VALIDATION.md` 원문을 읽기 확인했다. 보고서의 ZIP/SHA256SUMS/계약 hash는 본 인계 값과 일치하며 SHA256SUMS·계약·error-cases hash를 Billing 로컬 원본과 직접 대조했다. **E06-owner 불일치는 해소됐다.**

LC 보고: ZIP10파일 byte 일치·fixtures-1 보존, 독립 oracle82개(80벡터+hash/scope1+구원본/delta1), actual client49개(오류22개 및 기존검사 포함) 통과. 전체 `./gradlew test`1,149개 실패0/오류0/기존 조건부skip1이며 앞선 Mongo208개나 Billing1,570개와 합산하지 않는다. 이번 Billing 작업에서 LC 테스트를 재실행하지 않았다.

실제 client 검증은 loopback HTTP server/테스트 signer를 사용하며 실제 Billing 서버·TLS/JWT 수신 chain·DB/E2E 통과가 아니다. 구 잘못된 문구는 CONTRACT_ERROR로 거절하는 회귀를 유지한다. 생산자 manifest의 lcIndependentValidation=false는 생산 시점 원본으로 보존하고 이 회신을 별도 증거로 사용한다.

E06-owner 문구 재검증 대기는 종료한다. 상태28 전체 실행·T04·삭제/보존·실환경/활성화 gate는 남는다. 제품 코드/API/DB/배포/fixture bytes 변경 없음.
