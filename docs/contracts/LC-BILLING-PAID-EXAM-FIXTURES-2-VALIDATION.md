# 유료 시험 R2 fixtures-2 — escape·나노초 회귀 보완

- 날짜: 2026-10-14
- revision: `lc-billing-paid-exam-r2-fixtures-2`
- 기준 계약: R2 draft-2. 제품 정책/wire 변경 없음.
- 상태: Billing 별도 오프라인 검사 완료, LC 자체 독립검증·실제 HTTP/업무/Transaction 검증은 미완료.

## 수정 이유와 원본 보존

LC 읽기 검토의 두 지적을 재현했다. 기존 JSON.stringify 결과에 정규식으로 control escape를 치환하는 생성기는 literal 역슬래시+n/t/b/f/r의 의미를 바꿀 수 있었다. 기존 검사기의 datetime.timestamp float 경로는 나노초를 보존하지 못했다. 기존9/22 사례의 통과 자체가 잘못됐다는 뜻은 아니지만 그 결과를 이 경계까지 확대할 수 없었다.

fixtures-1 전체13파일(SHA256SUMS 포함)의 수정 전/후 SHA-256 일치를 확인했다. 기존 A fixtures와 draft-3 snapshot도 수정하지 않았다. 새 revision은 새 ephemeral RSA test key로 서명했으며 private key를 디스크/보고서에 기록하지 않았다.

## 수정 및 결과

- 생성기: Unicode scalar별 문자열 escaping. 역슬래시와 실제 control 문자를 구분하며 lone surrogate 거절. 생성된 canonical bytes를 JSON.parse한 결과와 원 입력의 의미가 같은지도 검사한다.
- 검사기: 시각을 정수 날짜/초와 최대9자리 소수부로 분리해 정수 나노초/Decimal로 계산. float timestamp와 datetime 소수부 절삭 경로를 제거했다.
- canonical18개: 기존9 + literal backslash n/t/b/f/r 5개 + 연속 역슬래시 길이1~4와 실제 newline/tab 혼합4개.
- exact clock5개: 정수초 경계 ±1ns와 날짜 경계를 epoch nanoseconds로 직접 비교한다.
- token31개: 기존22 + expiry/iat skew/nbf skew 각각 경계 -1ns/정확경계/+1ns 9개. expiry는 정확경계부터거절,iat/nbf미래허용경계는정확경계부터수락하는 기대를 확인했다.
- invalid event13·HTTP판정표12·wire6/status3은 같은 검증 범위를 유지한다. 상태 시나리오26은 계속 미실행이며 S15/V07은 보류한다.
- SHA256SUMS 대상13파일 모두 통과. HTTP는 판정모형이고 실제 Spring chain/port 테스트가 아니다.

## 회귀가 오류를 잡는지 확인

1. 구 escape 표현식을 새 문자열9개에 적용하면9개 모두 원 문자열 의미와 달라짐을 확인했다. 수정 serializer는 원 입력/기대 bytes/hash와 모두 일치했다.
2. 임시 검사기 복제본에 구 float epoch 함수를 넣으면 새 회귀에서 실패했다. 수정 검사기는 exact nanosecond5개와 token 경계31개 모두 통과했다.
3. 임시 복제본 clock fixture에 공백1byte를 추가하면 hash 검사 실패했다.
4. 기존 fixtures-2 디렉터리에서 생성기를 재실행하면 덮어쓰기를 거절하고 SHA256SUMS는 불변이었다.
5. 수정 검사기로 fixtures-1을 명시해 실행해 기존 파일12/canonical9/invalid13/token22/HTTP12/wire6/status3 통과를 확인했다.

## 재현·LC 전달

저장소 루트에서 실행한다. Python3/OpenSSL/Node는 로컬 도구이며 네트워크 호출 없음.

```sh
python3 scripts/contracts/verify-paid-exam-fixtures-r2.py
python3 scripts/contracts/verify-paid-exam-fixtures-r2.py src/test/resources/contracts/lc-billing-paid-exam-r2-fixtures-1
```

기본 경로는 fixtures-2다. [새 README](../../src/test/resources/contracts/lc-billing-paid-exam-r2-fixtures-2/README.md), [manifest](../../src/test/resources/contracts/lc-billing-paid-exam-r2-fixtures-2/manifest.json), [SHA256SUMS](../../src/test/resources/contracts/lc-billing-paid-exam-r2-fixtures-2/SHA256SUMS), [생성기](../../scripts/contracts/generate-paid-exam-fixtures-r2.mjs), [검사기](../../scripts/contracts/verify-paid-exam-fixtures-r2.py)를 원본 bytes 그대로 전달한다. 기존 생성기 재실행으로 revision1을 갱신하지 않는다.

LC는 새 revision의 hash를 먼저 대조하고 LC 자체 parser/crypto/serializer·고정clock으로 독립 검증한다. Billing 검사기 재실행을 LC 독립검증으로 보고하지 않는다. fixture 입력/bytes 변경 시 또 다른 revision이 필요하다. 이번 작업은 도구/가짜fixture/문서뿐이며 제품 코드·DB·배포·외부 메시지 전송 없음. Java 제품 코드 변경이 없어 Gradle 전체 테스트는 실행하지 않았다.
