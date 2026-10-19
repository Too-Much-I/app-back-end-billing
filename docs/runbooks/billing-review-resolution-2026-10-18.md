# 정상 이용권 보호 및 구매 대기 기록 해결

## 적용 범위

- 수량/소유확인 (`QUANTITY_REQUIRES_LOOKUP`, `QUANTITY_UNSUPPORTED`, `OWNER_UNRESOLVED`, `OWNERSHIP_UNSUPPORTED`) case의 정규화 증거가 없는 경우에만 기존 VERIFIED/ALLOWED/READY 활성권리를 보존한다. 활성권리가 없거나 만료되면 PENDING을 유지한다.
- 정규화된 소유 충돌, 알려진 환불/취소 및 EVENT_REQUIRES_LOOKUP은 예외 대상이 아니다. 회원증거/예약 대기 차단도 유지한다.
- 조회는 지급·case 수정·계정 변경을 하지 않는다. HTTP/JSON 계약과 요청 제한은 그대로다.

## 정상 반영과 case 연결

- `BILLING_PAYMENT_REVIEW_RESOLUTION_ENABLED` 기본 OFF. ON은 `PurchaseReviewResolutionSchema` r9 준비를 요구한다.
- 인증된 NON_RENEWING_PURCHASE에서 수량 누락 또는 alias 불확실성으로 보류되었으나 현재/원 소유 ID가 일치하고 Store/app/product/거래/시각이 존재하는 경우에만 별도 `reviewCandidate`를 보관한다. 원문 거래ID·alias를 보관하지 않는다. 명시 quantity!=1·family share·owner mismatch·환불/transfer는 자동 해결 대상이 아니다.
- 후보는 검증 증거가 아니다. 별도 검증된 구매의 환경/Store/app/거래해시/키버전/owner/product/구매시각 및 계정/lifecycle가 전부 일치하고 VERIFIED/ALLOWED/READY일 때만 해결한다.
- 구매 적용과 해결을 같은 Transaction에서 처리한다. 구매가 먼저인 경우 webhook 접수 시 기존 PURCHASE_VERIFIED ledger와 연결한다. case/version 및 lifecycle pendingEvidence를 원자적으로 갱신하고 중복 전달은 재지급/재차감하지 않는다.
- case는 삭제하지 않고 reviewState=RESOLVED·purchaseId·resolvedObservationKey·resolvedAt을 남긴다. 미해결 알림 집계와 reader에서는 이 해결 증거가 있는 건만 제외한다. 구매 원장과 보존 보호는 유지한다.
- 구case에 후보가 없으면 같은 receptionDigest의 인증된 webhook 재전달 때만 후보를 보완할 수 있다. 시간/같은 계정이라는 이유로 연결하지 않는다. 운영 기존5건은 아직 재전달·해결하지 않았다.

## 배포

사용자는 Billing 일시 중지, 운영 payment_purchase_cases에만 임시 collMod, r9 이관/검증/권한 회수/재배포를 승인했다. 다른 collection validator/index/자료 삭제나 LC·Identity 변경은 제외한다.

1. 전체 테스트·이미지 검증.
2. 현재 서비스/이미지/flag/control 저장, 준비된 후보 전달.
3. 단일 collection 임시 권한 부여, 서비스 desired0 및 running0 확인.
4. control ALL_BLOCKED CAS, r8/r9 exact schema와 기존 자료 검증 후 해당 collection validator만 이관.
5. 임시 권한 회수 확인, control 원래 상태 CAS 복원.
6. 후보 이미지와 review-resolution flag만 변경한 새 task 배포, 양포트 health/기동/기존권리 읽기 확인.

실행 결과는 완료 후 별도로 기록한다. 실제 사용자 JWT 응답과 프론트 화면은 서버 DB 진단과 구분한다.

## 2026-10-18 운영 적용 기록

- 최종 ECS `prod17` PRIMARY/COMPLETED·desired1/running1/pending0, 실행 태스크 HEALTHY, 8083/8443 healthy1. 이전 deployment 제거까지 확인해 배포 완료. 프론트의 인증된 실제 조회/시험 생성 확인은 별도다.

- 전체 `./gradlew clean test bootJar`: 1,878개 통과, 실패/오류/skip 0. 이미지 smoke 정상/잘못된 DB/잘못된 인증서 확인 통과.
- 이미지 TAR SHA-256 `4468d96aa36ad7cbf1060880eda91b6823db7319aa68aac0ce48b60b04bf6829`, r9 manifest SHA-256 `80a81614afe30c1d9947bb6786c5df5a04daf2fd2d7368a8a6296ebdfeba8147`; CloudShell 수신 해시 일치.
- 배포 이미지 digest `sha256:5ee01e8371b646f6942c6c8b5dd39074316adb75c796603b00643cec7d42d9bd`.
- 기존 prod16 정상 상태와 6개 collection exact schema/기존 자료 적합성 확인. 서비스 desired/running/pending 0 확인 후 control OPEN→ALL_BLOCKED CAS, `payment_purchase_cases` validator만 r9 이관. case 10건·인덱스 불변, target schema 검증 통과.
- 전용 임시 역할만 부여했다 회수했고, 새 DB 연결의 effective collMod 부재 확인. control은 epoch를 보존하며 version CAS로 OPEN 복원.
- 최초 prod17 기동은 schema mismatch로 실패했다. offline exporter의 relaxed JSON이 validator 안 Long 숫자6곳을 int32로 변환했고 Python 숫자값 비교는 이를 놓쳤다. 제품 코드·사용자 자료 문제가 아니라 이관 도구의 타입 손실이었다. exporter를 Extended JSON + Java Document round-trip 타입 검증으로 보완했다. canonical SHA-256 `b9a9c9ad88f061b7bd76ee46239999fbc59fc3cc8b529e22f2cf4bec4e8dc76b`.
- 서비스 중지 유지·동일 한정 임시 권한으로 case validator 타입만 보정, 6개 collection을 canonical BSON 타입까지 대조했다. case10건·인덱스 불변, 권한 재회수와 OPEN 복원 후 동일 prod17 재기동. 제품 이미지 변경 없이 정상 기동·RUNNING/HEALTHY·8083/8443 각각 healthy1 확인. 연속 health 임계값은 기존 30초/5회 유지.
- 이미지+review flag 이외 task definition 변경 없음 확인. 새 태스크 기동 로그 Started=true/기동실패false/ERROR0. 앱 HTTPS 무인증401, 외부 CloudShell의 workload 호출은 기존 발신IP 경계에서404(정상서명 업무 검증 아님).
- 영향 계정 읽기 확인: ACTIVE 검증권리1, 미정규화 보류case7, 다른 차단case0·환불대기0, 최신sync APPLIED, control OPEN. 사용자 JWT 조회/시험 생성·원문 webhook 재전달에 의한 case 해결은 프론트/후속 검증 사항.
- 기존5건을 임의 해결하지 않음. 정상권리 보호와 실제 프론트 응답 확인은 구분하며 기존 webhook 재전달에 의한 자동 해결은 아직 미검증.
- r9 새 필드가 기록된 후 prod16으로 단순 rollback하지 않는다. 필요시 r9를 읽는 새 이미지에서 해결 flag OFF로 대응하고 schema downgrade를 자동 수행하지 않는다.
