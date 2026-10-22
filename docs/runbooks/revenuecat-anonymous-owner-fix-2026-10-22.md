# RevenueCat 익명 original 소유권 검증 수정

사용자 승인: 「좋아 그렇게 해자 브랜치 파서 수정해줘」. Billing 및 앱 수정 범위이며 커밋·배포·운영 자료 수정은 수행하지 않는다.

## 원인과 변경

앱의 anonymous configure 후 logIn은 original 고객 ID를 익명으로 남길 수 있다. 기존 current/original 모두 purchaseAccountRefId와 동일해야 한다는 검사가 이 정상 연결을 OWNER_UNRESOLVED로 분류했다. 구매창 닫기 자체를 결제로 처리한 문제가 아니다.

- 앱은 purchase-account 확보 후 configure의 appUserID에 직접 지정한다. 상품 조회도 이 준비를 기다린다. 로그아웃 시 로컬 구매 접근을 닫고 다음 로그인에서 SDK ID를 바꾼다. 구매 직전 SDK ID 일치를 확인한다.
- Billing 웹훅의 익명 original은 지급 증거가 아니다. 현재 UUID와 거래 사실은 검토 후보로만 보관한다.
- 인증된 RevenueCat v2 purchase 및 customer aliases를 함께 검증한다. 전체 별칭이 요청 ref 또는 정확한 `$RCAnonymousID:` + lowercase hex32 형식이고 ref/original/current가 모두 포함돼야 익명 original 예외를 인정한다. 다른 named ID·불완전 페이지·연결 불명은 계속 보류한다. 추가 호출도 기존 총 5초 예산 안에서 수행한다.
- 기존 transaction의 다른 회원 귀속 차단과 원장 멱등성은 유지한다. 새 컬렉션·schema 변경 없음. 운영 Sandbox 동시 수용 정책 유지.

## 기존 검토 건 복구

원본 인증 웹훅의 동일 event/digest 재전송만 후보를 보충할 수 있다. account가 없던 과거 검토 건은 exact 거래·상품·소유·시각·환경이 일치하는 VERIFIED/ALLOWED/READY 구매와 PURCHASE_VERIFIED 원장이 있고 계정/lifecycle ACTIVE일 때만 귀속을 보충해 RESOLVED로 전환한다. counted=false는 그대로여서 pending을 이중 차감하지 않는다.

운영 2건은 이번 코드 수정만으로 없어지지 않는다. 배포 후 API에서 구매 검증이 성공한 다음 원본을 인증 재전송해야 한다. API에서 근거를 찾지 못하면 보류 유지한다. 수동 지급·검토 삭제·digest 변경은 하지 않는다.

## 출시 전 확인

RevenueCat key의 customer_information:customers:read 권한, 실제 alias 응답에 ref 포함 여부, 실제 기기의 최초 로그인/재로그인/취소/계정 변경을 검증한다. 100개를 넘는 별칭은 자동 페이지 확장 대신 보류한다. 앱 mock 검사는 실제 Store 결제 검증을 대신하지 않는다.

## 로컬 검증

- RevenueCat adapter 50개, webhook 52개, PaymentSync integration 140개: 총 242개 실패/오류/skip 없이 통과. 실제 로컬 Mongo replica-set 테스트 포함.
- 신규 검토 건 및 과거 account 없는 검토 건의 원본 재전송·검증 전 보류·검증 후 연결·중복 재전송을 검사했다.
- 앱 typecheck/lint/architecture/naming/수정 파일 format 통과. architecture의 기존 경고 41건 유지. fake native SDK 검사 통과.
- 운영 RevenueCat/Store/DB 변경 및 재처리는 하지 않았다.

- 최종 최신 코드 `./gradlew clean test` 단독 실행: 1,937개, 실패0/오류0/skip0 (관련242개 포함). 최초 전체 실행은 동시 Gradle 실행의 결과 파일 충돌로 실패했으며, 단독 clean 재실행으로 해소했다.
