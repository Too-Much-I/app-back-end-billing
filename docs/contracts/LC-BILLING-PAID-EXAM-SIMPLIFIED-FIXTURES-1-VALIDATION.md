# 단순화 exact R1 fixtures-1 — Billing 오프라인 검증

- 날짜: 2026-10-14 / revision: lc-billing-paid-exam-simplified-exact-r1-fixtures-1
- 상태: Billing 오프라인80벡터 검증 완료. 실제 HTTP/worker/Transaction·상태28시나리오 및 LC 독립검증 미완료.
- [수락 기록](LC-BILLING-PAID-EXAM-SIMPLIFIED-R1-ACCEPTANCE.md)과 함께 읽는다.

## 1. 5줄 결론

1. 새 fixture는 기존 A/R2 bytes를 재생성하지 않고 독립 경로에 제작했다.
2. Node 생성기와 별도 Python 검사기로 오류22·공개예시4·시간14·재개29·phone11, 총80벡터가 통과했다.
3. 상태 시나리오28개는 계약 traceability/미실행 표시만 확인했으며 실제 동작 검증이 아니다.
4. byte 변조 및 재hash한 의미 변조4종을 검사기가 거절했고 생성기는 기존 revision 덮어쓰기를 거절했다.
5. 신규 fixture의 LC 인수·독립검증과 실제 runtime 검증은 별도다.

## 2. 검사 범위

| 대상 | 수 | 실제 검사 | 한계 |
| --- | ---: | --- | --- |
| error-cases | 22 | raw JSON strict decode·route/HTTP/code/retryable 판정 | 실제 ingress/HTTP chain·모든 기존 오류 포괄 아님 |
| public-error-examples | 4 | 기존 code/message/envelope·result 생략·Retry-After 예시 | MVC 직렬화 실행 아님 |
| time-cases | 14 | UTC 정수 millisecond·120일 정각·윤일/연말·detail 미완료/hold 보호 | DB TTL/백업 운영 아님 |
| member-resume | 29 | owner/auth/lease/version/Session/격리 원인 판정 모형 | 실제 CAS·증거 진위·worker 호출을 실행하지 않음 |
| phone-snapshot | 11 | 선택 immutable field·echo/새group·원group/transition 부재 | 전체 DTO·실제 권리 선택/복원 실행 아님 |
| state-scenarios | 28 | E/T/P/C ID·계약 요구와 연결·미실행 표시 | Transaction/경합/복원 실행 안 함 |

E02는 parent 영역이고 E02-a~h가 세부 사례이므로28행을28개의 독립 runtime test 통과로 보고하지 않는다. error model의 Retry-After 실제 transport 처리는 범위 밖이며 public 예시에서 값만 대조한다. JWT/RSA는 이번 wire 변경 대상이 아니므로 가짜 signed token을 새로 만들지 않았다. 기존 인증 fixture를 대체하지 않는다.

## 3. 재현

```sh
node scripts/contracts/generate-paid-exam-simplified-fixtures-r1.mjs
# 위 명령은 최초 생성용. 기존 디렉터리에서는 실패하며 덮어쓰지 않는다.
python3 scripts/contracts/verify-paid-exam-simplified-fixtures-r1.py
# fixture 디렉터리에서:
shasum -a 256 -c SHA256SUMS
```

환경: Node v26.5.0, Python3.14.6. 네트워크·실 서비스/DB 호출 없음. 검사기는 생성기/제품 codec을 import/호출하지 않고 strict JSON, 정수 시각 계산 및 모형을 별도로 작성했다. 기대 결과는 비교에만 사용한다. 양 도구를 Billing에서 작성했으므로 LC 독립검증을 대체하지 않는다.

처음 생성 시 계약 표의 T05 행 공백 차이로 coverage count 검사가 실패했다. 완성되지 않은 산출물은 임시 경로로 옮기고 추출기를 보완해 최종28행을 확인했다. 실패본을 전달하지 않으며 원 fixture revision을 수정한 사건이 아니다.

## 4. 음성 검증 및 원본 보존

- 원본 복제본에1byte 추가 → hash 실패.
- 복제본 SHA256SUMS를 다시 계산한 후 MEMBER 소진 원인 연결을 제거 → 의미 검사 실패.
- 동일 방식으로120일 정각 기대를 창 안으로 변경 → 실패.
- phone status mock를 변경 → 실패.
- 상태 시나리오 execution을 PASS로 변경 → 실패.
- 원 revision에서 생성기 재실행 → 덮어쓰기 거절·SHA256SUMS 불변.
- 기존 paid R2 verifier 재실행 성공(canonical18/clock5/token31 등), 기존 원 계약/fixtures-2 bytes/hash 보존 확인.

정상 검사 결과: OFFLINE_CHECKS_PASSED, hashFiles9/errors22/publicExamples4/time14/memberResume29/phone11/stateScenarios28, httpExecuted=false/stateScenariosExecuted=false/lcIndependentValidation=false.

## 5. 전달·무결성

- 계약 hash: 79bcfe3d79225c21d712791266bea10d4aed9eed3dc0529ddf86f7b29673a482
- SHA256SUMS hash: 35f7d437fb562e1427efe385ffaf1721a60f4780fcaacf495c2a0c7e787c2436
- LC는 원본10파일을 byte 그대로 인수하고 SHA256SUMS 자체 hash와9개 파일을 모두 확인한다.
- contract-snapshot.md, manifest,README와 생성기/검사기를 함께 제공한다. Java/Jackson/Instant 등 LC 자체 구현으로 검증하고 원본 fixture는 고치지 않는다.
- producer manifest의 lcIndependentValidation=false는 제작 당시 상태로 유지하며 LC 결과는 별도 기록한다.

## 6. 미실행·변경 범위

실제 생성/삭제/phone/무료권 복원·금융 원장·worker/lease/Transaction·보존/폐기/E2E는 미실행이다. 삭제 route/auth·과거 owner 삭제·자료별 최종 retention은 제외했다. Java 제품 코드 변경이 없어 전체 Gradle은 실행하지 않았다. 문서/script/test resource만 변경, 실제 Secret/token/private key·사용자 자료 없음. 외부 전송·LC/Identity 수정·DB·Jira·배포·commit/push 없음.
