# TMI-212 D2 — 구매별 암호화 재조회 참조

## 1. 규격

`payment_purchase_references`를 Billing DB에 명시 생성한다. 기본 `_id_` index만 사용하고 `_id`는 기존 purchaseId다. 모든 field는 필수: `_id/environment/project/store/appId/transactionKey/lookupKeyVersion/accountId` string, `envelope` object(revision long1/keyId/nonce/ciphertext string), `version` positive long, `retentionExpiresAt` date. additionalProperties=false/strict/error/simple collation, TTL없음. writer격리·ALL_BLOCKED에서 설치하고 runtime은 verify-only다. 기존 금융 collection 규격은 바꾸지 않는다.

## 2. 저장·사용

인증된 RC 목록의 정상 Item을 core에 적용한 뒤 APPLIED인 경우만 저장한다. exact 환경/store/app/HMAC/keyVersion/owner reference/account/purchase를 다시 대조한다. 실제 RC reference는 기존 독립 AES256-GCM key로 암호화하고 AAD에 environment/project/app/transactionHMAC을 사용한다. plaintext/receipt/token은 저장·로그하지 않는다. envelope 이동·변조·다른키·다른거래는 복호화 실패다.

같은 reference 재관측은 중복 저장/원장 효과가 없고, 기존과 다른 reference면 조용히 덮어쓰지 않고 조회 검토 대상으로 보류한다. 최초 epoch/계정 lease guard를 저장 Transaction에서도 유지한다. core commit 후 reference 저장 전 중단되면 페이지 checkpoint를 하지 않으므로 다음 재조회에서 reference 저장을 복구한다. 검토/무소유/탈퇴 후 미지급 관측은 이 collection에 새로 저장하지 않는다.

읽기는 환경/프로젝트/계정/purchase exact로 검증하고 보존 종료 이후에는 제공하지 않는다. 보존일은 Purchase.retentionExpiresAt을 사용하고 목록 재조회 자체로 연장하지 않는다. 나중에 금전/기간 보존일이 바뀌면 읽기 때 현재 Purchase를 확인한다. 이 저장소는 금융 사실이나 삭제 자격의 source of truth가 아니다.

## 3. 운영 경계

2026-10-14 갱신: [명시 참조 정리 r1](TMI-212-reference-retention-r1.md)을 구현했다. 현재 구매·기간권·원장 보존일과 미해결 업무를 확인하며 기본은 읽기 점검, 삭제flag는OFF다. 아래 후속 항목 중 **참조 physical purge 코드는 구현했고 금융 사용자 연결 제거·실제 활성화는 아직 후속**이다.

실제5년 만료 physical purge·erasable 연결 제거는 후속 명시 보존 작업과 함께 활성화한다. 현재 TTL/자동삭제를 추가하지 않는다. key 분실/암호문 손상은 검토 상태이며 평문 fallback없음. 정상 키교체는 sender/worker 정지 후 승인된 절차로 수행한다. 실키/RC 호출/판매활성화는 이번 로컬 구현 검증과 별개다.
