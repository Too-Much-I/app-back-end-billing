# PLAN-014 — 초대 공유 무료 모의고사권 보상

- 작성일: 2026-10-21
- 상태: 2026-10-21 사용자 요청으로 Billing 구현 및 로컬 검증 완료(전체1,904개/실패0·오류0·skip0). 기본 OFF이며 앱·실환경·배포·양측 검증 완료는 아님. 아래 설계 당시 미구현 표현은 역사적 범위로 읽는다.
- 근거: 사용자 「그냥 초대만 하면」 및 「이 기준으로 계획서 작성」. 2026-10-21 「어 그렇게 하는 걸로 하자」로 iOS 완료/Android 대상 선택·이후 취소 위험 수용 확정.

## 1. 5줄 결론

1. MEMBER가 초대 링크를 공유할 때 iOS는 공유 완료 신호, Android는 공유 대상 앱 선택 신호로 본인에게 무료 모의고사권 1장을 지급한다. 친구 가입·인증·결제는 요구하지 않는다.
2. 계정당 누적 최대 3장이다. 사용·취소·시험 삭제로 지급 한도가 다시 늘어나지 않는다.
3. 수신자를 추적하지 않으므로 동일 친구 반복 공유·자기 공유를 판별하지 않는다. 해당 위험은 승인된 단순화 범위다.
4. 공통 EXAM_PASS에 Billing 내부 출처 INVITE를 붙이고, 무기한·지급순 사용·유료 우선·초대권 재복원 없음 정책을 유지한다.
5. 공유창만 열고 닫으면 미지급이며, 지급 조건 신호 후 대상 앱에서 취소하는 위험은 허용한다. 실제 전달 증명은 요구하지 않고 서버는 원자적 3회 한도와 중복 요청 방지를 보장한다.

## 2. 사용자가 반드시 읽어야 하는 내용

### 지급 조건

| 항목 | 정책 |
| --- | --- |
| 지급 대상 | 인증된 MEMBER 본인. Guest 지급 없음 |
| 지급 시점 | iOS 외부 공유 앱 activity의 sharedAction, Android 외부 공유 앱 chooser 대상 선택 callback 후 서버에 요청. 서버 Transaction commit 시 확정 |
| 지급량/상한 | 지급 조건을 만족한 공유 시도 1건당 1장, 동일 계정 누적 최대 3장 |
| 제외 | 공유창만 열고 닫음, 지급 조건 신호 전 취소·오류·신호 불명. 단순 앱 복귀/timeout도 미지급 |
| 허용 위험 | 지급 조건 신호를 받은 뒤 대상 앱 안에서 취소해도 지급될 수 있음. 실제 전송/수신 확인·지급 회수는 하지 않음 |
| 인정 공유 경로 | 외부 공유 앱을 통한 동작만 인정. 링크 복사·공유창 표시만으로는 지급하지 않음 |
| 친구 행동 | 가입·휴대전화 인증·구매 불필요. 초대받은 친구에게 별도 보상 없음 |
| 중복 친구/자기 초대 | 수신자 식별·차단 없음. 연락처/전화번호 수집하지 않음 |
| 유료 이용 중 | 초대권 지급·보유 가능. 시험 입장은 기존 유료 우선 유지 |
| 소진 이후 | 누적 지급 횟수 유지. 남은 권리가 0이어도 3장을 이미 받았다면 추가 지급 없음 |
| 사용 정책 | 기존 무료권과 지급순 사용, 만료 없음. reserve hold/cancel/expiry는 원 allocation만 복구 |
| 전환 복원 | INVITE 소비는 최초 trial→paid 복원 대상 아님 |
| 탈퇴/재가입 | 기존 보상권 정책상 다른 userId에 권리 자동 이전 없음. 본 계획의 한도는 사람/전화번호가 아닌 계정 단위이며 새 계정 반복 생성 방지는 추가하지 않음 |

서버는 실제 친구에게 전송됐다는 증명을 받지 않는다. 앱이 직접 API를 호출하는 구조이므로 변조된 클라이언트가 공유 없이 요청할 위험도 남는다. 이를 이유로 가입 검증·연락처 분석·추천 계보 시스템을 추가하지 않고, 계정당 3장 상한과 정상 인증을 강제한다.

앱 표시 예: “초대 링크를 공유하면 무료 모의고사권 1장! 계정당 최대 3장.” 지급 조건 신호 전 취소에는 성공 안내를 하지 않으며 서버 지급 확정 뒤 보상 완료를 안내한다. “친구 가입 완료 보상” 또는 “친구 3명 인증”으로 안내하지 않는다.

## 3. 사용자가 결정해야 하는 사항

지금 정책을 다시 선택할 필요는 없다. 다음 기술 항목을 구현 준비에서 확인한다.

- iOS/Android의 실제 공유 SDK가 제공하는 완료/취소 신호를 대상 앱별 검증한다. 일부 OS의 대상 선택 callback을 실제 발송 완료로 오표시하지 않는다.
- iOS는 외부 공유 앱 activity의 sharedAction, Android는 외부 공유 앱 chooser 대상 선택 신호를 연결한다. 링크 복사 등 비앱 동작은 제외하고, 분류 불명은 지급하지 않는다. Android 기본 RN Share의 즉시 sharedAction은 사용하지 않는다. 조건 신호를 제공하지 않는 경로는 미지원으로 두고 공유창 표시/앱 복귀/timeout을 대체 근거로 쓰지 않는다.
- 두 public API 경로와 역할은 확정했다. §10의 세부 응답/오류 설계 및 기존 scope/lifecycle 연결은 구현 전 앱/Billing 대조로 동결한다. Identity 신규 claim/API는 기본적으로 추가하지 않는다. 기존 사용자 JWT와 MEMBER 증거로 충족되는지 확인한다.

## 4. 주요 위험과 미확인 사항

- 실제 친구 수·전달 성공 보장은 없다. 부정 공유 위험을 수용한 소규모 프로모션이다.
- 단순 client callback을 서버 검증된 전송 증거로 저장/표현하지 않는다.
- 회원 유형과 현재 ACTIVE는 다르다. 탈퇴와 지급이 경합하면 기존 lifecycle fence와 같은 Transaction 경계에서 판정하고, 지연 요청으로 탈퇴 계정을 재활성화하지 않는다.
- 계정당 누적 상한의 근거는 단기 API 응답 캐시나 sync command TTL과 분리해야 한다. 120일 후 같은 요청이 새 요청으로 들어와도 동일 계정 지급 합계가 3을 초과하면 안 된다.
- 기존 무료 reader/resolver가 일반 INVITE grant를 읽고 사용할 수 있는지는 구현 시 확인한다. EXAM_PASS라는 이름만으로 통합 완료를 가정하지 않는다.
- 개인정보 보존은 별도 기존 정책을 따른다. 상한 유지가 탈퇴 개인정보의 무기한 보존을 허용하는 뜻은 아니다.

## 5. 구현 계획

### P0 — 앱 공유 동작 및 API 계약 확인

1. OS/대상별 완료·취소·오류·앱 종료 callback 표 작성. 서버에 실제 수신자나 공유 본문을 보내지 않는다.
2. 아래 두 API 경로와 역할을 확정한다. 미배포 계획이며 세부 wire는 §10을 구현 준비 기준으로 앱과 대조한다.
   - 지급: `POST /api/v1/rewards/invite-shares`, 필수 lowercase UUID v4 `Idempotency-Key`. 본문 없음, userId/수량/수신자 입력을 받지 않음.
   - 조회: 초대 화면 진입·응답 유실 복구용 `GET /api/v1/rewards/invite-shares`. awardedCount/limit/remainingAwards를 반환하는 최소 조회. 기존 entitlements는 실제 사용 가능한 권리 조회로 유지.
   - 지급 성공: 지급 사실·동일 요청 재현 여부·누적 횟수/남은 지급 횟수. 지급 완료가 현재 미사용 1장을 보장하지는 않음.
   - 한도 소진: 정확한 업무 코드와 HTTP status를 확정하고 무조건 재시도하지 않음.
   - 인증/탈퇴/일시 장애/키 충돌: 기존 public envelope·오류 규칙 재사용. 신규 구매 sync에 편승하지 않음.
3. 앱은 공유 시도마다 새 key를 만들고 지급 요청 전에 보존한다. 지급 조건 신호 후 네트워크 재시도는 같은 key. 조건 신호 전 취소에는 지급 API를 호출하지 않는다. 조건 신호 뒤 대상 앱 내부 취소는 별도 취소/회수하지 않는다.

### P1 — Billing 원자적 지급

저장 구조는 계정별 누적 지급 상태와 기존 grant/ledger 및 지급 멱등 기록으로 확정한다. 정확한 collection/field/index 이름은 기존 저장소 구현과 대조해 정한다. 별도 친구·추천 관계 저장소는 만들지 않는다.

- 인증된 JWT sub 및 신뢰 환경으로 계정 식별. ACTIVE MEMBER 확인, 기존 탈퇴 fence와 경합 직렬화.
- 같은 key의 성공 결과 재현은 한도 검사보다 먼저 처리한다. 3번째 지급 성공 응답이 유실돼도 재시도에서 “한도 초과”로 바뀌지 않는다. 현재 인증·lifecycle 거절이 우선이다.
- 누적 횟수 <3 조건부 갱신 + INVITE grant 1개 + append-only 지급 ledger + 멱등 결과를 단일 Mongo ClientSession Transaction으로 저장한다.
- env/user별 상태 unique, env/user/operation별 unique, grant와 지급 원장 연결 unique 등 정확한 index/validator를 구현 전 고정한다.
- 부분 쓰기·동시 요청·unknown commit은 같은 key로 기존 결과를 확인한다. 단순 count 후 insert만으로 상한을 구현하지 않는다.
- 지급 횟수는 소비/hold/cancel/expiry/시험 삭제로 차감하지 않는다. counter 단독 대신 원장과 대조 가능한 근거를 보존한다.
- 새로운 공통 보상 프레임워크·coupon/출석/친구 관계·서버 자동 메시지 발송은 제외한다.

### P2 — 기존 무료권 사용 및 앱 연결

- 통합 entitlements에 지급권 반영, 유료 우선·무료 보존·FIFO 확인.
- INVITE 권리의 reserve/confirm/cancel/expiry/replacement 및 최소 이용 증거 확인.
- LC wire는 EXAM_PASS를 유지하고 세부 INVITE 출처를 LC 분기 요구로 확장하지 않는다. 호환성 문제가 있으면 기존 공동 계약을 검토한다.
- 앱은 서버 지급 확정 후 성공 안내와 권리 재조회를 수행한다. 서버 실패/응답 유실은 “확인 중” 또는 재시도 표시, 낙관적 권리 증가는 금지한다.
- 한도 소진 후 공유 자체는 허용할 수 있으나 추가 보상 가능 안내를 하지 않는다.

### P3 — 검증 및 활성화 준비

- 기본 OFF 전용 지급 flag를 둔다. 정확한 property 이름은 구현 시 정한다. OFF에서는 신규 지급 금지, 이미 지급된 권리의 사용은 유지한다.
- 명시 schema 적용·앱 대응 버전·staging 검증 후 별도 활성화. 이번 계획서는 schema 설치/배포/운영 ON을 수행하지 않는다.
- 총 지급/중복 재현/한도 거절/실패를 개인정보 없이 관측. 공유 수신자·본문·토큰은 로그 금지.

## 6. 부록 — 검증 목록 및 근거

| ID | 검증 | 기대 결과 |
| --- | --- | --- |
| I01 | 정상 공유 1~3회 | 각각 1장, 누적 3장 |
| I02 | 4회 이상·사용 후 재요청 | 추가 지급 없음, 한도 복원 없음 |
| I03 | 동일 key 반복/응답 유실 | 지급/원장 1회, 3번째 성공 재현 |
| I04 | 서로 다른 key 동시 20개 | 같은 계정 총 지급 최대 3, 부분 쓰기 없음 |
| I05 | Transaction 실패/unknown commit | 재시도로 중복 없이 수렴 |
| I06 | 공유창만 열기/닫기·조건 신호 전 취소/오류/불명 | 지급 요청 없음. Android 기본 Share 즉시 성공으로 지급하지 않는지 확인 |
| I06-b | iOS 완료 / Android 대상 선택 | 각 조건 신호로 지급 요청 1회. 실제 전송 증명 불필요 |
| I06-d | 링크 복사·비앱 동작·분류 불명 | iOS 성공 신호가 있어도 링크 복사는 지급하지 않음. Android 복사/커스텀 action도 대상 앱 선택으로 간주하지 않음 |
| I06-c | 조건 신호 후 대상 앱 내부 취소 | 지급 허용, 회수하지 않음. 중복 callback은 같은 key로 수렴 |
| I07 | 공유 완료 후 앱 종료·네트워크 끊김 | 보존 key로 재시도, UI/실권리 일치 |
| I08 | Guest/타 userId 입력/탈퇴 경합 | 거절, 지연 요청으로 부활·타 계정 지급 없음 |
| I09 | 유료+초대권/여러 무료권 | 유료 우선·무료 보존·지급순 사용 |
| I10 | 초대권 reserve/confirm/cancel/expiry/replacement | exact allocation 복구, 추가 소비/복원권 생성 없음 |
| I11 | 소비 후 paid 전환·시험 삭제 | INVITE 재복원 없음, 누적 지급 횟수 불변 |
| I12 | 장기 보존/120일 뒤 과거 key | 캐시 소실과 무관하게 동일 계정 최대 3장 유지 |
| I13 | flag OFF/재개 | 신규 지급 중단, 기존 권리 사용·미확인 결과 안전 복구 |
| I14 | 동일 친구·자기 공유/클라이언트 직접 요청 | 서버가 수신자 차단을 주장하지 않음. 인증된 동일 계정 상한은 우회 불가 |

근거:

- [공통 무료권·유료 전환 복원 정책](../contracts/FREE-EXAM-PASS-AND-PAID-SWITCH-RESTORATION-2026-10-13.md): MEMBER 귀속, 무기한/FIFO, INVITE 재복원 제외, EXAM_PASS 공통 분류.
- [PLAN-011](PLAN-011-paid-reservation-and-usage-evidence.md): 기존 예약·source·복원 연동. 초대 지급 기능 완료를 뜻하지 않음.
- 현재 `PaidReservationService`의 EXAM_PASS 경로는 통합 검토 대상이며 이번 계획에서 코드 변경/실행 검증하지 않았다.

이번 변경은 본 계획서와 CURRENT_STATE/WORKLOG뿐이다. 기존 dirty 변경 보존, 다른 저장소·제품·API·DB·fixture·배포·Jira·commit/push 변경 없음.


## 7. 2026-10-21 공유 callback 사전 확인

### 확인 결과와 한계

- 로컬 주요 프로젝트 디렉터리의 모바일 manifest/pubspec/package 파일을 검색했으나 토선생 모바일 앱 저장소를 식별하지 못했다. 실제 프레임워크/SDK/버전 및 단말 실행 검증은 미완료다. 웹 프론트 프로젝트를 모바일 앱으로 간주하지 않았다.
- [Android 공식 공유 문서](https://developer.android.com/develop/ui/compose/sharing/send)의 Get information about sharing은 선택한 target component/chooser 결과를 알려준다. 이 신호만으로 대상 앱 안에서 최종 전송했는지, 이후 취소했는지를 보장할 수 없다.
- [React Native 공식 Share 문서](https://reactnative.dev/docs/share)는 iOS의 dismissedAction과 Android의 항상 sharedAction으로 resolve되는 동작을 구분한다. 토선생이 React Native를 사용한다는 확인은 아니며, 라이브러리의 success 이름만으로 지급하면 안 되는 구체적 예시다.
- Apple completion callback 문서는 JS/Markdown 추출 제한으로 본문 검증을 완료하지 못했다. iOS 전체 공유 대상의 실제 전송·취소 보장으로 일반화하지 않는다.

### 당시 계획에 미치는 영향 — 후속 §9 승인으로 대체

현재 승인 정책을 변경하지 않는다. 다만 “공유 완료”를 실제 메시지 발송으로 해석하면 일반 Android 공유 경로만으로는 충족을 보장할 수 없다. “공유창에서 대상 앱 선택 시 지급, 대상 앱 안에서 취소해도 지급될 수 있음”으로 완화하는 안은 별도 사용자 선택이며 이번에는 적용하지 않았다. 공유창 표시/단순 앱 복귀/timeout을 완료로 처리하지 않는다.

다음 확인에는 모바일 저장소 경로 또는 사용 SDK/버전이 필요하다. 실제 기기에서 ①공유창 취소 ②대상 선택 후 취소 ③실제 전송 ④링크 복사 ⑤앱 종료/복귀를 iOS/Android·지원 대상별로 비교한다. 완료 판별이 가능한 경로만 기존 정책을 충족한다고 기록한다. 서버 최대3장·멱등 지급 설계는 그대로 유지할 수 있다.


## 8. 2026-10-21 앱 저장소 확인 후속

- 대상: https://github.com/Too-Much-I/app-front-end , 기본 develop의 `9e2dc16b51d9d9fb3bb8caa2d4cfefd602f05f14`. 임시 checkout 읽기만 수행했으며 앱 저장소 변경 없음.
- package.json/pnpm-lock 기준 Expo 57.0.11, React Native 0.86.2. 직접 의존성에 react-native-share/expo-sharing 없고 src에서 Share 호출·초대/보상 기능을 발견하지 못했다. 현재 사용 중인 공유 기능의 버그 수정이 아니라 신규 구현 대상이다.
- [앱 package.json](https://github.com/Too-Much-I/app-front-end/blob/9e2dc16b51d9d9fb3bb8caa2d4cfefd602f05f14/package.json), [lock](https://github.com/Too-Much-I/app-front-end/blob/9e2dc16b51d9d9fb3bb8caa2d4cfefd602f05f14/pnpm-lock.yaml) 확인.
- [RN 0.86.2 Share.js](https://github.com/facebook/react-native/blob/v0.86.2/packages/react-native/Libraries/Share/Share.js)는 iOS success/dismissed를 구분한다. 대상 activity의 성공이며 상대방 실제 수신 증명은 아니다.
- [RN 0.86.2 Android ShareModule](https://github.com/facebook/react-native/blob/v0.86.2/packages/react-native/ReactAndroid/src/main/java/com/facebook/react/modules/share/ShareModule.kt)은 startActivity(chooser) 직후 sharedAction을 resolve한다. 즉 기본 Share 성공을 보상 조건으로 쓰면 공유창 취소 전 지급될 수 있다. 이 방식은 현재 취소 미지급 정책을 충족하지 않는다.

### 조사 당시 구현 후보 — 후속 §9에서 지급 기준 승인

- iOS: 기본 Share의 sharedAction 처리. 허용 activity와 대상별 취소를 실기기에서 확인한다. 링크 복사는 후속 §11 승인으로 제외한다.
- Android: 기본 Share 성공 값을 사용하지 않고 chooser target 선택 callback을 제공하는 최소 native 연동 또는 Expo/RN 호환 라이브러리를 검토한다. 아직 패키지를 선정/추가하지 않았다.
- 이 후보는 공유창에서 아무 대상도 선택하지 않고 닫는 경우 지급하지 않지만, 대상 앱을 선택한 뒤 그 앱 안에서 취소하는 경우는 지급될 수 있다. 이 범위 수용은 사용자에게 명확히 확인한 뒤 정책에 반영한다.
- Expo Development Build/실기기에서 callback·앱 종료·중복 호출을 검증한다. 기본 RN만으로 실제 메시지 발송까지 확인하는 것으로 표현하지 않는다.

SDK 공식 source 정적 확인만 수행했다. 앱 빌드·실기기·서버 지급 테스트는 미실행이며 제품/정책 변경 없이 확인 결과만 추가했다.


## 9. 2026-10-21 최종 지급 신호 승인

사용자 「어 그렇게 하는 걸로 하자」로 iOS 공유 완료 신호 / Android 공유 대상 앱 선택 신호를 지급 기준으로 확정했다. 공유창만 열고 닫기는 미지급, 조건 신호 후 대상 앱 내부 취소 위험은 허용한다. §7~8의 승인 대기 표현은 조사 당시 기록이며 이 절과 본문 지급 조건이 현재 기준이다.

누적3장·같은 key 중복지급 방지·MEMBER 전용·무기한/FIFO·paid-first·INVITE 재복원 제외는 유지한다. 실제 수신자/전송 증명이나 채널별 최종 발송 추적은 추가하지 않는다. Android chooser 연동 방법·SDK 선정과 activity/component 구분은 기술 준비에서 명시한다. 링크 복사 등 target 앱 선택이 아닌 동작은 후속 §11 승인에 따라 미지급한다.

이번 승인은 지급 정책 확정이다. 앱/서버 코드 구현·정확한 API 동결·실기기 통과·schema 설치·배포 완료를 뜻하지 않는다. 다음은 이 기준에 맞춰 최소 API/저장 계약과 앱 callback 연결을 준비하는 것이다.


## 10. 두 API·저장·앱 흐름 확정 및 실행 계획

사용자 「이렇게 해서 확정하고 계획서 작성해줘」에 따라 조회/지급 두 API, 공유 시도별 key, 같은 Transaction의 한도·grant·원장·결과 저장, 서버 확정 뒤 앱 안내를 구현 기준으로 확정한다. 아래 필드/오류 이름은 이 기준을 구체화한 기술 설계이며 기존 배포 API가 아니다. 프론트와 exact wire 대조를 마친 뒤 구현한다. 제품 정책을 재선택하는 단계는 아니다.

### 10.1 API 설계

공통: 기존 PublicResponse(isSuccess/code/message/result), 사용자 JWT의 검증 sub, 환경은 서버 설정. body/query로 userId나 수량을 받지 않는다. no-store, HTTPS와 기존 public 보안 경계를 유지한다. 정확한 기존 scope 적용은 Identity 발급·앱 토큰을 대조하며 새 scope/claim을 이 문서만으로 추가하지 않는다. 신규 무료 회원이 결제를 해야 보상받는 의존성은 만들지 않는다. MEMBER 및 lifecycle 준비 경로를 P0에서 확인한다.

| API | 요청 | 성공 result 설계 |
| --- | --- | --- |
| GET /api/v1/rewards/invite-shares | body/query 없음 | awardedCount, limit=3, remainingAwards=3-awardedCount |
| POST /api/v1/rewards/invite-shares | body/query 없음, Idempotency-Key 필수 lowercase UUID v4 | operationId, awardedQuantity=1, awardedCount, limit=3, remainingAwards |

- GET은 현재 누적 지급 횟수의 읽기 전용 조회다. 최초 조회가 grant/원장/counter를 생성하지 않는다. 정상적인 최초 계정은 0/3/3, 저장 실패를 0으로 위장하지 않는다.
- POST는 최초 지급과 동일 성공 재현 모두 HTTP200/SUCCESS를 사용한다. 같은 key는 저장한 원 성공 snapshot을 반환하므로 awardedCount는 그 지급 당시 값이다. 최신 횟수는 GET, 현재 보유권은 기존 GET /api/v1/entitlements로 조회한다.
- awardedQuantity=1은 그 operation의 지급 사실이지 이번 HTTP 재시도가 새 1장을 지급했다는 뜻이 아니다. 앱은 같은 operation 성공을 여러 번 보상 toast로 표시하지 않는다.
- GET 전체 횟수만으로 응답 유실된 특정 operation의 성공을 추정하지 않는다. 해당 POST를 같은 key로 재시도해 확인한다.
- 한도 소진 설계: HTTP409 / INVITE_REWARD_LIMIT_REACHED, “초대 보상은 계정당 최대 3회까지 받을 수 있습니다.” 자동 재시도 금지. 동일 성공 key 재현은 한도 거절보다 우선하되 현재 인증/탈퇴 deny 우선.
- 인증 없음401, Guest/권한 거절403, key/요청 형식 오류400, rate limit429, 일시 장애503은 기존 public 규칙과 정합성을 확인한다. 실패 result=null. 정확한 기존 코드/문구/Retry-After 및 기능 OFF 동작은 P0 wire 표에서 확정한다. 전체 다른 public 오류를 변경하지 않는다.

### 10.2 저장 및 Transaction 순서

논리 모델 3개를 사용하고 기존 repository/ledger를 재사용한다.

| 논리 자료 | 최소 역할 | 불변 조건 |
| --- | --- | --- |
| 계정별 invite reward 상태 | 환경/user별 awardedCount·갱신 version | 0~3, 소비/취소로 감소하지 않음 |
| operation 지급 결과 | 환경/user/key·grant 연결·원 성공 응답·시각 | 같은 key 지급1회, 다른 사용자와 격리 |
| 기존 grant/append-only ledger | INVITE EXAM_PASS 1장·지급 출처/operation | 무료 체험 Claim으로 위장하지 않음 |

한 Transaction에서 lifecycle fence 확인 → 같은 operation 성공 확인 → 한도 조건부 증가 → grant/원장/성공 결과 저장 → commit한다. 실패 시 모두 rollback. 기존 lifecycle과 충돌하는 별도 Tx로 ACTIVE 확인만 먼저 끝내지 않는다. env/user 상태 unique와 operation unique, grant 지급 연결 unique를 명시 schema로 적용한다. 동시 최초 생성 충돌과 unknown commit은 같은 key로 재확인하며 새 key를 만들지 않는다.

멱등 상세 기록은 기존 120일 보장 방향에 맞춰 설계하되, 계정 누적 지급/원장 근거는 상세 응답 TTL과 분리한다. 120일 후 과거 key가 새 시도로 취급돼도 계정 누적3 한도는 유지한다. 탈퇴 후 권리·증거 보존/삭제는 기존 lifecycle 정책과 대조하고 자동 TTL로 미완료 근거를 지우지 않는다.

### 10.3 앱 상태와 실패 복구

- 화면 진입: GET 조회, 잔여 보상 횟수 표시. 미조회/장애를 “3회 남음”으로 추정하지 않는다.
- 버튼: 중복 탭 방지, 로그인 계정에 묶인 UUID 생성·저장 후 공유창 실행.
- 조건 신호 전 취소/오류: 지급 호출 없음. Android 기본 Share의 즉시 성공은 조건 신호가 아니다.
- 조건 신호 수신: 저장한 key로 POST. callback 중복에도 새 key를 만들지 않는다.
- 지급 확정: operation별 완료 처리, 보상 안내 후 횟수/entitlements 조회. 조회 실패가 이미 확정된 지급을 실패로 되돌리지 않는다.
- 응답 유실/앱 재실행: 현재 로그인 계정이 일치할 때 보존 key로 재시도. 로그아웃/계정 전환 뒤 이전 요청을 새 계정으로 보내지 않는다.
- 한도409: 추가 보상 종료 표시·GET 갱신. 무료권이 없어도 3회 한도를 초기화하지 않는다.
- 앱이 조건 신호를 영속화하기 전에 종료돼 근거가 없는 경우 공유 완료를 발명하지 않는다. 원격 친구 전달 확인이나 자동 소급 보상은 추가하지 않는다.

### 10.4 작업 순서·완료 기준

| 단계 | 산출물 | 완료 기준 |
| --- | --- | --- |
| P0 | exact wire/오류·기존 JWT scope/lifecycle·schema/index 표, Android callback 방식 | 신규 회원·탈퇴·OFF·재시도 동작 모순 없음, 프론트 소비 가능 |
| P1 | Billing 두 API·원자적 지급·read-only 조회 | I01~05/I08/I12/I13 및 3번째 성공 재현·계정 격리 통과 |
| P2 | 앱 공유/영속 key/지급 안내·기존 EXAM_PASS 사용 연결 | I06 계열/I07/I09~11/I14, 계정 전환·중복 callback 확인 |
| P3 | 고정 버전 staging 실기기·서비스 통합 보고서 | iOS/Android 취소/조건신호/이후취소·20개 동시요청·응답유실 및 권리 사용 검증 |

Billing의 단위/replica-set Mongo 검사는 구현마다 수행한다. 앱 정적 검사/Development Build와 실기기 검증은 프론트 담당 범위다. 최종 OFF 배포·schema maintenance·운영 활성화는 별도 실행 승인과 검증을 따른다. 별도 친구 관계 저장소, 추천 코드 귀속 검증, 자동 메시지 발송, 범용 프로모션 엔진은 만들지 않는다.


## 11. 공유 앱만 인정 — 링크 복사 제외 확정

사용자 「공유 앱을 통한 것만」으로 링크 복사를 보상 대상에서 제외했다. iOS는 외부 공유 앱의 activity 완료 신호, Android는 외부 공유 앱의 대상 선택 신호만 인정한다. 공유창만 표시·취소, 링크 복사 및 앱을 통한 공유로 구분되지 않는 동작은 지급하지 않는다. iOS에서 sharedAction만 검사하지 않고 activity 종류를 함께 분류한다. Android도 실제 외부 앱 선택과 복사/시스템 action을 구분한다.

허용 activity/component 식별 및 SDK callback 검증은 구현 작업이다. callback이 불명확하다고 모든 성공을 허용하지 않는다. 대상 앱에서 신호 이후 취소할 수 있는 위험, 수신자 미추적, 계정당 누적3장 및 멱등 지급 정책은 유지한다. 코드·앱·DB·배포 변경은 수행하지 않았다.


## 12. 구현 기술 규격 R1

[PLAN-014 기술 규격 R1](PLAN-014-TECHNICAL-SPEC-R1.md)에 GET/POST의 정확한 역할·응답/오류 설계, 기존 billing:read/purchase 재사용, purchase-account MEMBER 준비, 두 신규 collection과 기존 grant/ledger 연결, 공통 grantOrder, 단일 ClientSession Tx 및 앱 조건신호 영속 복구를 정리했다. 기존 제품 정책은 유지한다. 기술 설계이며 앱 wire 대조·코드 구현·실기기·배포 완료는 아니다. 이후 exact 기술 상세는 이 규격을 기준으로 관리한다.


### 프론트 인계용 exact wire

[프론트·Billing API 계약 R1](../contracts/INVITE-SHARE-REWARD-PUBLIC-API-R1.md)에 요청/응답 예시·필드·route별 오류 메시지·Retry-After·같은key/계정격리·공유판정·120일 자동재시도 경계를 정리했다. 신규 초대 route의 wire는 해당 문서를 기준으로 대조하며 기존 payment 오류 메시지는 변경하지 않는다. 아직 미구현·미배포이며 프론트 공동 검증 완료는 아니다.
