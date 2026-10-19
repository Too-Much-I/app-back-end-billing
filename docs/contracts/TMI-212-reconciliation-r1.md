# TMI-212 D2 — 정기 계정 조회

## 1. 결론

- 앱 sync 없는 계정도 RevenueCat 구매 목록을 주기적으로 다시 확인한다.
- 최근90일 활동 또는 구매 확인 계정, 기간 종료 전인 활성/예정 이용권 계정이 대상이다.
- 기존 계정 lease·암호화 페이지·공용 project budget·검증/core 원장을 재사용한다.
- cursor-r2 명시 이관이 필요하며 reconciliation-enabled는 기본OFF다.
- 빈 목록/404/실패는 미구매·환불·삭제 근거가 아니고 기존 권리는 유지한다.

## 2. 실행·저장

cursor에 선택 nullable date `reconcileAt`, `reconcileStartedAt`만 추가한다. 기존 row/lookup 필드·index는 유지한다. 정확한 r1→r2 validator만 writer격리/ALL_BLOCKED/전체행검사 뒤 collMod한다. startup은 verify-only다. sync-r2 이관도 함께 필요하다.

주 runtime tick은 pending sync 우선 후 별도 flag가 켜졌을 때 정기조회도 최대batch100개를 선택한다. 두 경로는 같은 계정 lease를 공유한다. reconcileAt 오름차순, 최근활동 내림차순, id순으로 선택해 성공 계정은 다음주기까지 제외한다. index가 없는 전체 join/정렬 비용은 실부하 검증 대상이다. 반환100개가 스캔100개를 보장하지 않는다.

한 claim당 한 페이지, 전체순회 최대1000페이지, 순회 시작부터24시간까지 기존 backoff/Retry-After로 재시도한다. 중간 페이지는 암호화 cursor와 시작시각을 저장한다. 순회 완료 후 pending이면5분, 활성/예정 권리면6시간, 나머지는1일 뒤로 예약한다. sync 전용 조회는 기존 즉시재시도 규격을 유지한다. 빈 성공 목록도 다음 순회로 넘어갈 뿐 미구매로 확정하지 않는다. 404는 재시도,AUTH/계약오류/검토/상한은 보류 후 명시 재개한다.

목록은 mutable하며 snapshot 완전성을 주장하지 않는다. 페이지 도중 거래 삽입은 다음 반복순회와 거래 unique로 수렴한다. sync worker가 중간 순회를 먼저 마쳤다면 다음 정기조회는 첫페이지부터 다시 시작할 수 있으며 중복 지급하지 않는다. 여러 인스턴스의 선택 경합은 기존 cursor version/lease/epoch로 수렴한다.

## 3. 활성화·미확인

`billing.payment.provider.worker.reconciliation-enabled=false`, 환경변수 `BILLING_PAYMENT_PROVIDER_RECONCILIATION_ENABLED`. worker enabled도 필요하다. 실키/공유DBtopology·Store매핑·부하·운영 대응 검증 전 켜지 않는다. 계정 조회 재개는 review-resume-r1을 따른다. [구매별 암호화 reference](TMI-212-purchase-reference-r1.md)는 연결했으며5년physical삭제 연동은후속이다. 이 기능은205 미구매 검증기가 아니다.
