# PLAN-011 production source 데이터 점검

- 날짜: 2026-10-13, 최초 건수 조회 08:41 UTC
- 환경: 사용자가 LC 로컬 실행 설정의 조회 대상 환경을 `prod`로 확인함. 실제 배포 task 설정과의 별도 대조는 미실행.
- 방식: mongosh 읽기 전용 collection 목록/집계. URI·DB credential·문서 식별자·개별 시험 내용 출력/저장 없음. DB 수정·이관·배포 없음.

## LC 결과

| 대상 | 결과 |
| --- | --- |
| exam_sessions | 1,493건 |
| entitlementSource 누락/null/빈 값 | 1,493건 |
| billingReservationId 누락/null/빈 값 | 1,493건 |
| attemptGroupId 누락/null/빈 값 | 1,493건 |
| exam_creation_operations | collection 없음 |

| status | active | 건수 |
| --- | --- | --- |
| COMPLETED | false | 1,012 |
| ABANDONED | false | 333 |
| IN_PROGRESS | true | 136 |
| 누락 또는 null | true | 10 |
| 누락 또는 null | false | 2 |

active=true 합계 146건은 유효 권리·제출 가능 증명이 아니다. 특히 상태 없는 10건은 LC 기존 데이터/reader 동작 검토가 필요하다. 조회는 개별 읽기 집계이며 여러 조회를 묶은 고정 snapshot은 아니다. 실행 중인 서비스에서 바뀔 수 있으므로 실제 전환 직전 재점검한다.

## 판단

현재 조회한 LC 데이터는 Billing 예약/그룹의 연결 증거가 없다. 전부 EXAM_PASS로 backfill하거나 TrialClaim/consumption을 만들어 연결해서는 안 된다. source 누락만의 문제로 가정한 일괄 이관 대상이 아니다.

권장 후보는 기존 데이터를 Billing 연동 이전의 학습 기록으로 보존하고, 신규 Billing 예약 성공 이후에는 source를 필수로 쓰는 경계다. 기존 history 열람을 source 누락 때문에 차단하지 않는다. 기존 활성 146건의 진행/새 시험 시작/접수된 채점 처리는 LC와 별도로 확정한다. 이번 점검만으로 강제 종료·삭제·무료권 지급/차감 또는 무기한 grandfather 권한을 승인하지 않는다.

따라서 현재는 모든 문서에 source required를 적용할 수 없다. 신·구 데이터가 구분되는 조회/작성 경계가 필요하며, 없는 source를 매번 무료로 추론하는 일반 fallback은 만들지 않는다. 순수 일회성 source 채우기 방식의 채택은 이 실데이터 결과를 반영해 재검토해야 한다.

## Billing 확인 범위

LC 연결에서 `authorizedDatabases=true`로 보이는 DB 중 이름에 billing이 포함된 대상은 없었다. 이는 Billing production DB 부재의 증거가 아니다. 별도 cluster·다른 이름·권한 제한 가능성이 있다. 로컬 Billing에서는 실제 접속 설정을 찾지 못했다. 이후 사용자가 「아직 안만들었어」로 Billing production DB 미구축을 확인했다. 이는 사용자 확인이며 DB 직접 조회 결과는 아니다. 기존 Billing production 데이터 이관은 불필요하고 새 schema/writer에서 reserve 성공 이후 source를 필수로 설계한다. PREPARED 등 출처 미결정 단계까지 일괄 required를 적용하지 않는다. DB 생성·schema 설치는 이번에 수행하지 않았다.

## 점검 도구와 한계

[scripts/inspection/source-inventory.js](../../scripts/inspection/source-inventory.js)는 SOURCE_INVENTORY_URI/DATABASE/ENV/ROLE 환경변수를 받아 collection 건수와 누락 건수만 출력한다. URI를 명령행 인자로 전달하지 않는다. collection별 maxTimeMS 10초, raw exception 출력 없음. 이 스크립트는 연결 정합성/원장 검증이나 migration 도구가 아니다. production LC에서 실행 성공했고 Billing에서는 미실행이다. PREPARED의 정상 누락·terminal command별 snapshot 차이는 별도 해석해야 한다.

다음은 LC 기존 활성/상태 누락 시험의 처리 계약 검토와 실제 전환 전 최종 inventory다. Billing은 새 DB 설치·schema 검증을 배포 단계에서 수행하며 기존 데이터 backfill은 필요 없다. staging/test 확인은 이번 prod 요청에 포함해 실행하지 않았다.


## 사용자 확정: 기존 시험 보존

사용자 「그냥 두면 안돼?」 및 제안에 대한 「좋아」로 다음 정책을 확정했다.

- 전환 전에 존재한 시험은 기존 이용 규칙과 기록을 유지한다. 활성 146건을 이 전환 때문에 일괄 종료·삭제·source backfill하지 않는다. 상태 누락을 진행 허용으로 새로 해석하거나 기존 terminal 시험을 부활시키지 않는다.
- 전환 후 새 시험 생성과 의도적인 새 재응시는 Billing 예약/확정 및 EXAM_PASS/SUBSCRIPTION 필수 규칙을 따른다. 기존 시험 조회·이미 접수된 제출/채점·같은 요청의 응답 유실 복구를 신규 재응시로 오인하지 않는다.
- 기존 시험 이력을 무료권 소비/복원이나 유료 구매의 이용 증거로 임의 연결하지 않는다. 이 승인으로 TrialClaim·grant·consumption을 새로 만들거나 기존 권리 제한을 해제하지 않는다.
- source 누락 자체를 legacy 판정 조건으로 사용하지 않는다. 전환 전 확정한 대상 집합 또는 동등하게 신뢰 가능한 서버 측 경계를 사용하고 이후 새 writer가 만든 시험에는 적용하지 않는다. 클라이언트 시각/주장으로 legacy를 허용하지 않는다.
- 원래 Session을 일괄 수정할 필요는 없다. 경계 저장 방식·원장 밖 최소 migration manifest·새 writer 차단/전환 순서는 LC와 정한다. 실제 전환 직전 inventory를 다시 확정하며 이번 조회의 146건을 고정된 미래 대상 수로 사용하지 않는다.

Billing prod DB는 신규 구축, LC 기존 시험은 보존하는 방식으로 진행한다. 이번 승인은 정책 기록이며 DB 생성/수정·LC 코드 변경·배포는 실행하지 않았다.


### 기존 시험 목록 방식 검토 승인

사용자 승인으로 출시 전 시험 ID 목록을 보호된 서버 저장소에 보관하는 방식을 우선 검토한다. 일반적인 writer generation 시스템 확장은 이번 기본안에 포함하지 않는다. 실제 목록에는 정확한 매칭 정보가 필요하며 작업 보고서에는 hash/revision/건수만 남긴다. 원 Session을 일괄 변경할 필요는 없다. writer drain·최종 목록 확정·새 writer 전환 및 목록의 보존/조회 실패 처리는 LC 상세 설계에 포함한다. 이 승인으로 production 목록을 새로 수집하거나 DB에 저장하지 않았다.
