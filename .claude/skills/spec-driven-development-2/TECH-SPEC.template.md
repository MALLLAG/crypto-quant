# [기능명] 테크스펙

> **작성일**: YYYY-MM-DD

---

## 1. 영향받는 서비스/패키지 매트릭스

> 가장 먼저 채우는 섹션. 이 매트릭스가 PLAN과 PR 분할의 골격이 됩니다.
>
> **중요**: `Repo` 컬럼은 `mani.yaml`의 project name과 **정확히 일치**해야 합니다 (PLAN의 모든 cross-repo 명령이 이 이름으로 실행됨).
> 검증: `mani list projects <name>` 또는 `mani list projects | grep <name>`.

| Repo (mani name) | 레이어 | 변경 종류 | 인터페이스/스키마 변경 |
|------------------|--------|----------|----------------------|
| 예) `transaction-service-interface` | proto | optional 필드 추가 | `events.proto` (field number N) |
| 예) `transaction-service` | application | use case 분기 추가 | - |
| 예) `public-api-service` | presentation | GraphQL 스키마 확장 | `schema.graphql` |
| 예) `web` | UI | 신규 폼 필드 | Relay codegen |

---

## 2. 개요

### 2.1 목적

- **문제 정의**: 현재 어떤 문제가 있는가? (구체적 `<project>/file:line` + commit hash로 근거)
- **해결 방안**: 이 기능이 어떻게 해결하는가
- **기대 효과**: 구현 후 가치

### 2.2 범위

#### 포함 (In Scope)

> repo별로 그룹핑하면 PLAN으로 그대로 옮기기 쉬움. 헤더는 mani project name 사용.

**`<mani-project-A>`**
- [변경 항목]

**`<mani-project-B>`**
- [변경 항목]

#### 제외 (Out of Scope)

- [이번 spec에 포함되지 않는 것 + 그 이유]

### 2.3 용어 정의

| 용어 | 설명 |
|------|------|
| 용어1 | 정의 |

---

## 3. 요구사항

### 3.1 기능적 요구사항

#### FR-001: [요구사항 제목]

- **설명**:
- **입력**:
- **출력**:
- **검증 기준**: (구체적 `<project>/file:line`, 스키마 field 등)

### 3.2 비기능적 요구사항

(성능 / 보안 / 가용성 / 확장성 — 해당되는 항목만)

### 3.3 제약 조건

- (기술적 / 비즈니스 / 외부 의존)

---

## 4. 인터페이스 / 스키마 Backward Compatibility 체크

> 인터페이스/스키마 변경이 있으면 모두 채울 것. 없으면 "해당 없음".

### 4.1 변경되는 스키마 파일

| 채널 | 파일 | 변경 종류 | Backward compat 영향 |
|------|------|----------|---------------------|
| proto | 예) `<project>/.../service.proto` | optional 필드 추가 | ✓ 안전 |
| GraphQL | 예) `<project>/.../schema.graphql` | nullable field 추가 | ✓ 안전 |
| REST | 예) `[POST] /payments/cancel` | response 필드 추가 | ✓ 안전 |
| Webhook | 예) `payment.cancelled` payload | optional field 추가 | ✓ 안전 |
| MQ | 예) topic `tx-events` 메시지 스키마 | optional field 추가 | ✓ 안전 |

### 4.2 공통 금지 변경 체크리스트 (모든 채널)

- [ ] 기존 필드 타입 변경 없음
- [ ] 기존 필드 제거 없음 (replay / 구버전 클라이언트에서 깨짐)
- [ ] field number / 식별자 변경 없음
- [ ] enum value 의미 변경 없음
- [ ] oneof / union case 제거 없음

### 4.3 새 인터페이스 정의 시 체크

- [ ] 에러 표현 방식 결정 (typed error vs 시스템 status code)
- [ ] 도메인 에러는 typed로, 인프라 에러만 시스템 status로
- [ ] 버저닝 전략 명시

### 4.4 채널별 backward compat (해당되는 항목만)

#### gRPC / proto + 이벤트 소싱
- [ ] 이벤트 proto는 append-only (`transaction-service-interface/.../events.proto` 같은 persisted event)
- [ ] field number 재사용 없음 (`reserved` 처리)
- [ ] message 자체 제거 없음

#### GraphQL
- [ ] 기존 type/field 제거 없음 (`@deprecated` 처리만 허용)
- [ ] non-null → nullable 변경 없음 (반대는 안전)
- [ ] 기존 enum value 제거 없음
- [ ] Relay connection / pagination 계약 유지
- [ ] argument 추가 시 default value 또는 nullable

#### REST API
- [ ] path 변경 없음 (신규 path만 추가)
- [ ] HTTP method 변경 없음
- [ ] response status code 의미 변경 없음
- [ ] 기존 request/response field 제거·타입 변경 없음
- [ ] required → optional 외 변경 없음

#### Webhook (PG → 서버 / 서버 → 가맹점)
- [ ] 기존 event type name 제거 없음
- [ ] payload field 제거·타입 변경 없음 (구버전 가맹점 핸들러가 깨짐)
- [ ] 새 event type 추가 시 옵트인 (구독 메타에 명시 또는 가맹점 설정 토글)
- [ ] retry / signature 검증 방식 변경 없음 (있다면 마이그레이션 spec 별도)

#### MQ (Kafka 등 — 워크스페이스에 존재할 때)
- [ ] 기존 topic 제거·이름 변경 없음
- [ ] 메시지 스키마 backward compat (Avro/Proto evolution rule)
- [ ] consumer group / partition key 의미 변경 없음
- [ ] retention / replication 변경은 별도 운영 spec

---

## 5. 시스템 설계

### 5.1 도메인 모델

#### 값 객체 (Value Objects)
#### 엔터티 (Entities)
#### 도메인 오류 (Domain Errors)

### 5.2 API / 인터페이스 설계

#### gRPC / proto

```protobuf
// project: <mani-project-name>
// file: .../service.proto
```

#### REST / GraphQL

```
[METHOD] /api/[resource]
```

#### 내부 인터페이스 (Repository / Gateway / ACL)

### 5.3 데이터 모델

```sql
-- project: <mani-project-name>
-- 테이블 변경 / 신규
```

### 5.4 Cross-repo 데이터 흐름

> 여러 repo를 가로지르는 시퀀스. project 이름은 mani 기준.

```
<client-project> → <api-gateway-project> → <service-a> → <service-b> → ...
```

(주요 시나리오 1~3개)

---

## 6. 기술적 결정사항

| 항목 | 선택 | 대안 | 선택 이유 |
|------|------|------|-----------|
| | | | |

---

## 7. 참고

- 관련 문서, 이전 spec 번호, 외부 문서 등
