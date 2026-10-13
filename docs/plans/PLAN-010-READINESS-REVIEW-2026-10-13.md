# PLAN-010 착수 검토 — 구매 수신·기간권과205 연결

- 날짜: 2026-10-13 / 브랜치: feat/TMI-209-billing-cleanup-foundation
- 범위: 저장소 계획·ADR·현재 코드 정적 검토. 코드 구현/정본 계약 변경/판매·삭제 활성화 승인이 아니다.

후속 2026-10-13 사용자 승인: 아래 검토 당시 paid reader 분리 계획은 기존 `/api/v1/entitlements` 무료·유료 통합 확장으로 대체됐다. paid 전용 route는 신설하지 않는다. 현재 코드 미구현 판정과 provider/schema·205 선행 검증은 유지한다. 상세는 개정 PLAN-010/ADR-004/결정서를 따른다.

## 1. 5줄 결론

1. PLAN-010은 존재하지만 개요 수준이며, 현재 구매 수신·Purchase/timeline·sync/paid reader·RevenueCat adapter 구현은 없다.
2. 009 상품/구매계정과 탈퇴·204 복구·205 삭제 코어를 기반으로 시작할 수 있다.
3. 먼저 provider 검증 기준과 내부 schema/정규화 규격을 닫고, fake 코어부터 작은 단계로 구현하는 것이 적절하다.
4. 205의 lifecycle 쓰기 CAS·pendingEvidence·미매핑 격리는010 수신부터 반영해야 한다. 삭제 구현 후 나중에 덧붙이는 문제가 아니다.
5. 실제 판매/유료 입장은011·012·013 gate, 실제 삭제는205 PR3/206 gate 이후다.010 완료만으로 켜지 않는다.

## 2. 반드시 읽어야 하는 내용

현재 PaymentController는 products와 purchase-account만 제공한다. payment 패키지에는 catalog/account/withdrawal/recovery/cleanup이 있고 실제 Purchase/SubscriptionEntitlement/RevenueCat 수신 코어는 없다. 기존 테스트1066개 통과를010 구매 검증 완료로 볼 수 없다.

010은 앱 결제창을 직접 구현하는 작업이 아니다. 앱 SDK/Store에서 결제된 사실을 검증해 Billing 구매 원장과 기간권에 한 번만 반영하고, 앱에 처리 상태·기간을 조회해 주는 서버 작업이다. 무료 사용권과 유료 기간권을 하나의 수량으로 합치지 않는다.

## 3. 구현 전 정리할 사항

| 항목 | 확인·보완 내용 |
| --- | --- |
| RevenueCat 증거 | 10월7일 조사 문서는 공식 HMAC 규격을 확인했다고 기록하지만 프로젝트 설정/실제 delivery는 미검증이다. 이를 미지원이라고 단정하거나 실제 검증 완료로 간주하지 않는다. 현행 문서/프로젝트·raw signature·재시도 fixture, SDK iOS/Android 식별자와 v2 owner 매핑을 재확인한다. 임의 Authorization-only 전환 금지. |
| DB·정규화 exact | 거래 business key/hash·최소 암호화 lookup reference·Purchase/기간권/ledger/command/inbox·unique/index·nullable 금액·오류/보존·worker lease를 명시한다. mutable 처리상태를 event digest에 넣어 동일 event가 재수신 시 충돌하지 않도록 immutable 정규화 필드와 후속 binding 판정을 구분한다. |
| 탈퇴·삭제 경합 | mapped inbox/sync durable 최초 접수부터 동일 lifecycle 쓰기 CAS와 pending 증감 effect를 원자 반영한다. 처리 완료 시 최초 한 번만 감소. 탈퇴 전후 늦은 거래는 금융 사실 보존과 권리 활성화를 분리하고 inactive ref/삭제된 ref/미매핑 자료의 보존·격리·증거 범위를 exact하게 닫는다. 계정 자동 재생성·재가입 지급은 금지한다. |
| 012와의 경계 | public purchaseStatus와 access/financial/processing 상태를 분리하고 refund-first deny/tombstone과 REVIEW_REQUIRED를 구체화한다.012 전에는 테스트용 기존 구매에 환불이 도착하는 경우도 안전한 미지원 상태로 분류하며 실제 환불 지원/판매 완료로 표시하지 않는다. |
| 시각·기간 | ADR §5.7은 최초 VERIFIED commit sequence·구매시각과 앞선 끝 중 늦은 시작·지연 관측 재정렬 금지를 이미 명시한다.010의 '추후 동결할 안' 표현을 정합화하고, 동일 거래 재시도·구매 지연·동시 append·정상 만료 fixture로 고정한다. 새 상품 제한을 추가하지 않는다. |

## 4. 사용자 결정과 미확인

상품 종류/가격/28일·stacking·재가입 이전 금지·환불 정책을 다시 정할 필요는 없다. 위는 대부분 구현 규격과 검증이다. 실제 provider가 승인 계약의 증거를 제공하지 못하거나 새로운 신뢰 경로가 필요한 경우에만 대안을 보고하고 별도 승인받는다.

이번에는 RevenueCat/Apple/Google/AWS 실제 조회·credential·sandbox 결제를 수행하지 않았다. 앱 SDK 버전·실상품/권한·실제 서명/거래 필드 지원 상태는 이 정적 검토로 확정하지 않는다. PLAN-010의 'Jira 미생성'은 문서 표기이며 현재 Jira 전체를 조회해 부재를 검증한 결과는 아니다.

## 5. 권장 구현 분할

1. **준비 규격:** provider 조사/fixture 확보 계획, 내부 모델·index·상태·탈퇴/205 writer 목록·수신 인증 경계 확정. 실제 fixture 확보가 늦으면 외부 adapter 연결과 fake 코어 완료 범위를 분리한다.
2. **구매·기간권 코어:** 검증된 관측 포트/fake, transaction unique·owner/catalog snapshot·timeline/ledger·동시 append·refund-first 차단, 기본OFF.
3. **앱 API:** sync durable PENDING/멱등성/본인 귀속과 paid reader/no-store/오류·조회 무변경. 가짜 관측을 운영 지급에 연결하지 않는다.
4. **수신·복구:** 검증된 RC ingress/REST adapter·inbox/worker·callback 유실·pagination/재시도·lifecycle/pending/미매핑 경합. 문서/실fixture 확인 전 지원 필드를 발명하지 않는다.
5. **205 PR3 연결:** 실local evidence·provider coverage/drain·늦은 금융 입력 격리를 삭제 코어와 검증. RC empty/404만으로 삭제 허용 금지.

이 분할은 작업 제안이며 이번 요청으로 코드 구현/Jira 생성/운영 활성화를 수행하지 않는다. 다음 산출물은010 상세 구현 규격 및 단계별 완료 기준이다.

## 6. 근거·검증

- [PLAN-010](PLAN-010-purchase-ingestion-and-timeline.md): API·수신/정규화·worker·flag·완료 기준.
- [ADR-004](../adr/ADR-004-fixed-term-premium-payment-contract.md): §5.2 소유,§5.4~5.7 인증/관측/timeline 및 환불 경계.
- [과거 provider 조사](../contracts/PAYMENT_OPERATIONS_PROVIDER_RESEARCH-2026-10-07.md): 공개 문서 확인과 실프로젝트/거래 검증의 구분.
- [PaymentController](../../src/main/java/web/tosunsaeng/billing/domain/payment/api/PaymentController.java): 현재 제공 public endpoint.
- [PLAN-012](PLAN-012-refund-ledger-and-access-revocation.md): 금융 상태/권리 종료/환불 멱등성.
- [205 기술 규격](../contracts/TMI-205-cleanup-technical-spec-r1.md): §5.5 실제 금융 writer/pending/삭제 경합.
- [205 PR2](../runbooks/TMI-205-pr2-purge-core.md): fake 기반 삭제 코어와 실제 연결의 경계.

코드 변경 없는 정적 검토이므로 Gradle은 재실행하지 않았다. 기존 별도 LC/PR1/PR2/문서 변경은 보존하고 검토문서·현재상태·WORKLOG만 추가했다. Jira/타서버/배포/commit/push 변경 없음.
