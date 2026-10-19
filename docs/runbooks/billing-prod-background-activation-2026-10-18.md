# Billing 운영 백그라운드 기능 활성화 — 2026-10-18

## 후속 통합 준비

### 자동삭제·웹훅 통합 적용

최종 인증 보완: 사용자 Rotate한 키의 표시값을 비표시 저장·prod14 forceNewDeployment 완료. RC401이 지속되어 HMAC만 원인이라고 단정하지 않고 RC Authorization을 AWS 저장 기존값으로 재등록했다. 이후 RC TEST는422로 바뀌어 Authorization/HMAC 검증을 통과하고 SANDBOX/Test Store 환경·app 검증에서 차단됨을 확인했다. 새 키로 직접 서명한 PRODUCTION TEST는200. prod14 단일COMPLETED/1/0·HEALTHY·8083/8443healthy 최종 확인. 실제 PRODUCTION Store 발신의 정상 반영/환불/자동삭제 E2E는 미실행. 인증 검사·환경 mapping 완화 없음. Secret/사용자 원문 출력 없음.

최종 prod14 단일 COMPLETED/실행1/대기0·RUNNING/HEALTHY·양포트healthy, startup Started1/failed0/error0 확인. 요청 flag 전부 true, snapshot/feed false 재조회. control4/version5/OPEN·cleanup0. RevenueCat dashboard TEST는401로 실패, AWS 저장 Authorization/HMAC으로 보낸 별도 PRODUCTION TEST는200. 후자는 ignored inbox만 생성하며 구매 지급/환불이 아니고 RC 발신 성공을 증명하지 않는다. RC 기본 TEST body는 SANDBOX·Test Store app이므로 인증 정합화 뒤에도 운영 decoder의 환경/app 거절(422)이 예상된다. 인증 약화/운영 매핑 확장 없이 RC의 실제 인증값부터 정합화한다. 사용자에게 기존 미사용 운영 webhook의 signing secret Rotate 및 표시창 유지 요청, 실제 RC 연결 검증은 미완료.

- 사용자 직접 입력으로 운영 RC signing secret 저장 성공 확인. 다른 Secret 필드는 version 일치 검사 후 보존. RC 재조회 화면에서 HMAC ON·Production only 확인.
- 사용자 승인에 따라 prod13 desired0, 모든 서비스 task의 실제 STOPPED 확인 후 운영 DB 이관. ALB drain 때문에 약 6분 종료 대기했으며 조기 DB 변경/강제종료 없음.
- 단일 control exact fields·Long schema1/version3/OPEN, cleanup0, control/cleanup validator·strict/error·index·TTL없음 재검증. validator/index는 이미 일치하여 collMod/권한추가 없이 OPEN→ALL_BLOCKED/version4→schema4/OPEN/version5 CAS 성공. epoch 보존, 구매/사용자 데이터 삭제 없음.
- prod13 기반 prod14에 cleanup reconciliation/purge/alerts 및 provider ingress ON·host billing-prod.to-teacher.com 설정. 기존 환불/LC발신/Slack/재조회 ON 유지, snapshot/feed·실제 Store 환불 실행 제외. desired1 재기동 요청 완료. 기동/TEST webhook 결과는 후속 기록.

최신: prod13 단일 COMPLETED/실행1/대기0, RUNNING/HEALTHY, 8083/8443 healthy 확인. 환불·LC 발신 배포 완료이나 실제 업무 E2E는 미실행. 사용자 통합 승인으로 ALB109 exact webhook path 추가 및 RC Production only 웹훅 whintgrd6cb7cb418 등록 완료. 기존 Authorization 연결·HMAC 사용자 활성화, signing secret 직접 입력 대기. Billing webhook ingress 및 자동삭제는 아직 OFF, DB 이관·최종 통합 배포 전이다. HMAC 키 준비 대기 중 불필요한 운영중지는 하지 않는다.

- 운영 Slack 앱 A0CAYKNTS48 설치 및 비공개 billing-alerts-prod 연결 확인. 사용자 승인으로 전용 slack-alerts Secret과 운영 execution-role의 해당 Secret GetSecretValue만 추가. Webhook URL 비표시 전달, 개인정보 없는 probe 200/ok 확인.
- prod12 기반 prod13: Slack·provider alerts·환불 처리·LC 발신 ON, LC origin https://api-prod.to-teacher.com. 기존 재조회 ON 유지. 배포 완료는 후속 확인이며 DB·네트워크·이미지는 변경하지 않음.
- 자동삭제 DB 읽기 검증: 단일 control schema1/version3/OPEN, cleanup0. control 및 cleanup validator exact match, strict/error·인덱스 일치·TTL 없음 확인. collMod 권한 추가나 validator 재설치는 필요 없음. writer 정지·재확인·명시 control4 이관 후 reconcile/purge/alerts ON이 남음.
- RevenueCat 공식 Webhooks 문서의 Authorization 및 `t=<unix_timestamp>,v1=<hmac_sha256_hex>`·timestamp.rawBody 서명·retry 재서명 규격이 구현과 일치. 운영 Production only 신규 양식만 준비, 저장/발신키 생성/경로 개방은 미실행. https://www.revenuecat.com/docs/integrations/webhooks
- 사용자는 남은 자동삭제·웹훅도 한 번에 포함하도록 요청. 최종 적용에 필요한 운영 삭제·공개 경로·credential 연결 승인을 묶어서 요청한다. snapshot/feed 및 실제 Store 환불 실행은 제외.

> 후속 최종: prod12 단일 PRIMARY **COMPLETED**, 실행1/대기0 확인. 신규8083/8443healthy·재조회ON, 나머지 보류flag OFF. 아래 IN_PROGRESS는 이전 관측이다. 구 target draining은 강제 정리하지 않았다.

> 최종 관측: prod12 RUNNING/HEALTHY, 실행1/대기0, 8083/8443 신규target healthy. 재조회ON, 환불처리/발신·자동삭제·webhook OFF. 상품/이용권 HTTPS 무인증401 확인. 이전target draining과 ECS IN_PROGRESS가 남아 전체 rollout COMPLETED는 아직 확인하지 않았다. 실제 구매·환불 E2E는 미실행.

## 결론

> 후속: prod11은 필수 PaymentProviderAlerts.Sink 빈 부재로 기동 실패. 운영 Slack flag/Secret 참조가 준비되지 않아 환불 ON을 유지할 수 없다. 기존 정상 prod9 기반으로 재조회만 ON·prod profile 명시한 prod12 배포 요청으로 정리했다. 환불 처리/발신은 OFF이며 아래 세 기능 적용 계획은 성공 기록이 아니다.

- 사용자 승인 범위는 환불 결과 처리·LC 발신·RevenueCat 주기 재조회다. 실제 Store 환불 실행은 포함하지 않는다.
- prod9 기반으로 세 flag ON 및 LC origin `https://api-prod.to-teacher.com` 지정 배포를 요청했다. 완료 여부는 후속 실측으로 기록한다.
- LC prod10 수신 ON과 Billing 서명키의 LC 공개키 일치를 읽기 확인했다. 정상 서명 업무 E2E 성공을 뜻하지 않는다.
- 자동삭제는 운영 control schemaVersion1/OPEN/version3, cleanup 0건으로 확인되어 보류한다. CleanupConfiguration은 schemaVersion4를 요구한다. 숫자만 변경하거나 기동 검사를 우회하지 않는다.
- webhook host 미설정이며 실제 RevenueCat 발신·인증·경로 연결은 미확인이다. webhook 및 snapshot/feed는 OFF 유지한다.

## 적용 범위

`BILLING_PAYMENT_REFUND_PROCESSING_ENABLED`, `BILLING_PAYMENT_REFUND_PUBLISHER_ENABLED`, `BILLING_PAYMENT_PROVIDER_RECONCILIATION_ENABLED`를 true로 설정한다. 기존 이미지·역할·Secret·네트워크·나머지 flag를 보존한다.

첫 운영 변경 실행은 안전 검토에서 보류되어 실행되지 않았다. 사용자가 정확한 기능 세 가지·운영 대상·LC 주소·배포를 재승인한 후 실행했다. 개인키 및 DB URI는 메모리 안에서만 사용하고 출력하지 않았다.

## 남은 작업

- 새 task 기동·ALB 및 배포 완료 확인.
- 자동삭제: writer 중지와 control/cleanup 스키마의 명시적 운영 이관 범위 확정 후 활성화. 시험 삭제 receiver와 별개다.
- webhook: 실제 RevenueCat 발신 형식과 현재 Authorization+HMAC 검사 정합성 및 전용 host/path 연결 확인 후 활성화. 인증 약화로 우회하지 않는다.
- 실제 환불·LC 전달·구매 공동 E2E는 미실행.

## 기동 보완

prod10은 `SPRING_PROFILES_ACTIVE` 누락으로 PaymentRefundConfiguration의 운영 격리 검사에서 기동 실패했다. 기존 prod9 HEALTHY는 유지됐다. 환경·DB·fence 및 공개키는 정상이며, 검사 완화 없이 `SPRING_PROFILES_ACTIVE=prod`를 추가한 prod11을 배포 요청했다. 저장소에 별도 application-prod 설정은 없고 profile 참조는 보안 test 예외와 환불 검사뿐임을 확인했다.

prod11은 refundAlerts가 요구하는 PaymentProviderAlerts.Sink 부재로 실패했다. PaymentSlackConfiguration은 Slack enabled일 때만 실제 sink를 제공한다. no-op sink나 검사 삭제를 추가하지 않았다. 운영 Slack Secret·수신 대상 준비 후 환불을 다시 활성화해야 한다. 주기재조회는 독립 기능이므로 승인 범위에서 prod9 기반 prod12로 분리했다.

제품 코드 변경이 없어 Gradle은 실행하지 않았다. 이번 작업은 운영 설정 대조·배포와 기록이며 commit/push/Jira/LC·Identity 변경은 없다.
