# 유료 시험 R2 fixtures-1 제작·오프라인 검증 기록

> 2026-10-14 후속: [fixtures-2 검증](LC-BILLING-PAID-EXAM-FIXTURES-2-VALIDATION.md)에서 escape/나노초 경계 도구 보완. fixtures-1 bytes는 보존했다. 현재 검사기 기본은 fixtures-2이므로 아래 과거 명령으로 fixtures-1을 재현할 때 명시 경로 인자를 사용한다.

- 날짜: 2026-10-14
- revision: `lc-billing-paid-exam-r2-fixtures-1`
- 기준: `lc-billing-paid-exam-contract-r2-draft-2`, 정확한 문서 bytes SHA-256은 fixture manifest에 기록
- 승인: 사용자 「어 해줘」 — 확정 범위 오프라인 fixture 제작/bytes/hash 검사
- 결과: Billing 측 별도 오프라인 검사 완료. LC 독립 검증·실제 API/서비스/Transaction/경합 검증·최종 계약 동결은 완료 아님.

## 전달 파일

[fixture README](../../src/test/resources/contracts/lc-billing-paid-exam-r2-fixtures-1/README.md), [manifest](../../src/test/resources/contracts/lc-billing-paid-exam-r2-fixtures-1/manifest.json), [SHA256SUMS](../../src/test/resources/contracts/lc-billing-paid-exam-r2-fixtures-1/SHA256SUMS).

생성기와 별도 검사기:

- [Node 생성기](../../scripts/contracts/generate-paid-exam-fixtures-r2.mjs): 임시 RSA2048 test key로 서명, private key는 메모리에서만 사용. 기존 revision 디렉터리가 있으면 실행 거절.
- [Python 검사기](../../scripts/contracts/verify-paid-exam-fixtures-r2.py): 생성기 코드를 가져오지 않고 strict JSON/수동 canonical serializer·Python datetime/Decimal 및 OpenSSL RSA 검증으로 확인. 예상 결과는 비교에만 사용한다. 이는 LC 팀의 독립 검증을 대체하지 않는다.

## 검사 범위와 결과

| 대상 | 건수 | 확인 내용 |
| --- | --- | --- |
| 해시 대상 파일 | 12 | SHA256SUMS와 exact bytes 대조 |
| canonical vectors | 9 | event field 순서·null·reordered/공백·한글/escape/surrogate pair·Unicode normalization 구별·16KiB 경계, canonical bytes/hash |
| 잘못된 event 입력 | 13 | 크기초과/duplicate/trailing/old field/미지원schema·reason/float/null누락/시각/손상UTF8/lone surrogate의400/413/422 |
| signed token | 22 | RS256/JWK↔PEM,서명변조·issuer/sub/aud/kid·TTL/skew/소수초경계·JOSE/claim·회전keyset |
| HTTP 판정표 | 12 | 독립 로컬 판정모형으로 Bearer/query/OFF/port/method/미소유chain 기대값 확인 |
| reserve 예시 | 6 | 요청/응답의source별kind/group/mock/transition 연결 구조 |
| status 예시 | 3 | 일반cancel/revoke거절/confirmed replay 구조 |
| 상태 시나리오 | 26 | 구체적인 given/when/then 자료 작성, 실제 업무 실행은 미실행 |

HTTP 판정표는 실제 HTTP request나 Spring security chain 검증이 아니다. wire 예시는 production decoder 테스트가 아니다. 상태 시나리오의 `execution=NOT_EXECUTED_REQUIRES_IMPLEMENTATION`은 fixture 자체가 원장·CAS·경합 검증을 통과했다고 오해하지 않도록 표시한 것이다. SQLite/메모리모형 등으로 Mongo Transaction 통과를 대신 주장하지 않는다.

S01~S14/S16 및 V01~V06에 대응하는 자료를 준비했다. 이는 모든 조합을 빠짐없이 runtime 검증했다는 coverage 수치가 아니다. S15/V07 정상분기는 보류하며 임의 기대값을 생성하지 않았다. 기존 A fixtures-1은 수정하지 않았다.

## 재현

저장소 루트에서:

```sh
python3 scripts/contracts/verify-paid-exam-fixtures-r2.py
```

결과: OFFLINE_CHECKS_PASSED, hashFiles12/canonical9/invalid13/tokens22/httpDecisionTable12/wireExamples6/statusExamples3. 표준 Python3·OpenSSL만 사용하며 네트워크 접속 없음.

추가 검사: 임시 복제본의 canonical 파일에 공백1byte를 추가했을 때 검사 실패 확인. 생성기를 기존 revision에 재실행했을 때 overwrite 거절 및 SHA256SUMS 불변 확인. `git diff --check` 성공. 제품 Java 코드 변경이 없어 Gradle 전체 테스트는 이번 작업에서 실행하지 않았다.

## LC 인수 요청

원본 fixture bytes를 수정·재생성하지 말고 먼저 SHA256SUMS를 대조한다. 기존 A 자료와 분리된 revision이다. LC 자체 parser/crypto/serializer로 token·canonical/invalid 기대를 독립 검증하고, 지원 예정 wire/HTTP/상태 시나리오의 기대값 차이를 보고한다. fixture 기대값을 검증 결과 산출 로직에 입력하지 않는다.

일치 결과는 검증 범위와 revision/hash를 함께 기록한다. 의견 차이가 있으면 원본을 고치는 대신 새 revision을 합의한다. S15/V07·실제key/port/보존/E2E는 계속 별도 gate다. 이번 제작/검사로 제품 코드·DB·실환경·배포·외부 메시지 전송은 수행하지 않았다.
