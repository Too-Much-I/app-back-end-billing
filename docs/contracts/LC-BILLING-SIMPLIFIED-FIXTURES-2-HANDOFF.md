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
