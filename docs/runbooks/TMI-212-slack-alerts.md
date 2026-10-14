# TMI-212 Slack 요약 알림 연결

## 1. 결론

- `PaymentSlackSink`가 기존 provider 알림 인터페이스를 Incoming Webhook으로 전송한다. 기본OFF이며 채널은 나중에 연결한다.
- 일반 검토는 한국시간9시 이후 일일요약, RevenueCat 인증 차단/해제는 장애/복구 요약이다. 시간당 관측이므로 즉시 호출하는 긴급 pager가 아니다.
- 개인·계정·거래 식별자 없이 환경, 시각, 개수, 대기시간과 고정 TMI-212 안내만 보낸다.
- 로컬 fake transport 테스트는 실제 Slack 전달 성공·채널 권한을 증명하지 않는다.
- 이번 범위는 TMI-212 provider 요약이다. 미구매 계정 정리의 `CleanupAlerts`를 새로 연결한 것이 아니다.

## 2. 연결 순서

1. 환경별 수신 채널과 검토 담당자를 정한다. Slack 앱에서 Incoming Webhook을 활성화하고 해당 채널용 URL을 발급한다.
2. URL은 채팅·문서·Git에 붙이지 않고 배포 secret으로 넣는다. `BILLING_PAYMENT_PROVIDER_SLACK_WEBHOOK_URL`이 그 값을 참조한다.
3. 아래 세 설정이 모두 true이고 기존 worker의 DB/schema/provider 준비조건이 충족되어야 실제 요약이 전송된다. 별도 승인 전에는 false를 유지한다.

| 설정 | 기본값 | 의미 |
| --- | --- | --- |
| `BILLING_PAYMENT_PROVIDER_WORKER_ENABLED` | false | 기존 provider worker 실행 |
| `BILLING_PAYMENT_PROVIDER_ALERTS_ENABLED` | false | 요약 전송 허용 |
| `BILLING_PAYMENT_PROVIDER_SLACK_ENABLED` | false | Slack Sink 생성 |

4. staging에서 승인된 테스트 알림으로 수신 채널·중복 허용·담당자 대응을 확인한 뒤 운영 활성화를 별도로 승인한다. Slack 설정만 켠다고 구매/삭제/판매를 허용하지 않는다.

Slack OFF는 URL과 HTTP client 없이 기동된다. Slack ON인데 URL이 없거나 잘못됐으면 기동 실패다. Webhook 채널은 Slack 설정에 귀속되며 요청에서 channel을 덮어쓰지 않는다. Sandbox/production은 서로 다른 URL/채널을 권장한다.

## 3. 전송 및 실패 규격

- HTTPS `hooks.slack.com/services/...`만 허용한다. 포트·userinfo·query·fragment·인코딩 우회·다른 호스트는 거절한다.
- 연결 제한2초, 응답 body를 포함한 대기 제한5초, 응답 최대2KiB. 리다이렉트와 시스템 proxy를 사용하지 않는다. 사내 proxy가 필수라면 활성화 전 별도 검토가 필요하다.
- 성공은 HTTP200과 `ok` 응답뿐이다. 실패 시 false를 반환하고 기존 전송실패 metric으로 관측한다. raw 예외·응답·URL은 로그에 남기지 않는다.
- 429/503은 Retry-After 정수초 또는 HTTP-date를 반영하고 최소1시간 대기한다. 헤더가 없거나 과거이면1시간, 중복/잘못된 값/overflow면 재시도를 보류한다. 유효한 긴 대기는 그대로 존중한다.
- 401/403/404/410은 URL/권한/채널 설정 문제로 보고 프로세스 내 추가 전송을 차단한다. 운영자는 실패 metric을 보고 Slack 설정을 확인·수정한 뒤 재시작한다. 잘못된 Retry-After 보류도 같은 방식으로 명시 복구한다.
- 다른 오류는 기존 요약기의 최소1시간 재시도 간격을 따른다. 실패 때문에 구매 원장을 rollback하거나 추가 결제 처리를 하지 않는다.
- 대기/차단과 성공 전송 억제는 인스턴스 메모리다. 재시작 시 초기화되고 replica 간 중복/응답유실 뒤 중복이 가능하다. 정확히 한 번 전달이나 영속 알림 보관함을 보장하지 않는다.

## 4. 운영 확인

- Webhook URL은 인증 비밀이다. HTTP wire/debug logging, APM URL 수집, actuator config 노출에서 경로 전체가 유출되지 않도록 마스킹/접근 제한한다. 코드의 비로깅만으로 외부 관측 도구까지 보호된다고 보지 않는다.
- 전송 실패 metric은 Slack 외부 모니터링으로 확인한다. Slack 자체의 장애를 같은 Slack 알림만으로 탐지할 수 없다.
- 실제 네트워크 egress/TLS, 채널 접근권한, secret 배포, 담당자, 실전송은 별도 검증 대상이다. 이번 구현에서 외부 Slack 호출·채널 생성·배포·활성화는 수행하지 않는다.
- 코드: `PaymentSlackProperties`, `PaymentSlackConfiguration`, `PaymentSlackSink`. 테스트: `PaymentSlackSinkTest`. 기존 업무API/DTO/DB schema/Identity·LC 인증 계약 변경 없음.
