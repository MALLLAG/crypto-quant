# [기능명] 테크스펙

> **작성일**: YYYY-MM-DD

---

## 1. 영향받는 서비스/패키지 매트릭스

> 가장 먼저 채우는 섹션. 이 매트릭스가 PLAN과 PR 분할의 골격이 됩니다.

| Repo | 레이어 | 변경 종류 | 인터페이스/스키마 변경 |
|------|--------|----------|----------------------|
| 예) `<interface-repo>` | proto/schema | optional 필드 추가 | `service.proto` |
| 예) `<service-a>` | application | use case 분기 추가 | - |
| 예) `<api-gateway>` | presentation | GraphQL 스키마 확장 | `schema.graphql` |
| 예) `<client-app>` | UI | 신규 폼 필드 | codegen |

---

## 2. 개요

### 2.1 목적

- **문제 정의**: 현재 어떤 문제가 있는가? (구체적 file:line + commit hash로 근거)
- **해결 방안**: 이 기능이 어떻게 해결하는가
- **기대 효과**: 구현 후 가치

### 2.2 범위

#### 포함 (In Scope)

> repo별로 그룹핑하면 PLAN으로 그대로 옮기기 쉬움.

**[repo-A]**
- [변경 항목]

**[repo-B]**
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
- **검증 기준**: (구체적 file:line, 스키마 field 등)

### 3.2 비기능적 요구사항

(성능 / 보안 / 가용성 / 확장성 — 해당되는 항목만)

### 3.3 제약 조건

- (기술적 / 비즈니스 / 외부 의존)

---

## 4. 인터페이스 / 스키마 Backward Compatibility 체크

> 인터페이스/스키마 변경이 있으면 모두 채울 것. 없으면 "해당 없음".

### 4.1 변경되는 스키마 파일

| 파일 | 변경 종류 | Backward compat 영향 |
|------|----------|---------------------|
| 예) `<repo>/.../service.proto` | optional 필드 추가 | ✓ 안전 |

### 4.2 금지 변경 체크리스트 (이벤트 소싱·메시지 큐·공개 API 공통)

- [ ] 기존 필드 타입 변경 없음
- [ ] 기존 필드 제거 없음 (replay / 구버전 클라이언트에서 깨짐)
- [ ] field number / 식별자 변경 없음
- [ ] enum value 의미 변경 없음
- [ ] oneof / union case 제거 없음

### 4.3 새 인터페이스 정의 시 체크

- [ ] 에러 표현 방식 결정 (typed error vs 시스템 status code)
- [ ] 도메인 에러는 typed로, 인프라 에러만 시스템 status로
- [ ] 버저닝 전략 명시

---

## 5. 시스템 설계

### 5.1 도메인 모델

#### 값 객체 (Value Objects)
#### 엔터티 (Entities)
#### 도메인 오류 (Domain Errors)

### 5.2 API / 인터페이스 설계

#### gRPC / proto

```protobuf
// repo: <interface-repo>/.../service.proto
```

#### REST / GraphQL

```
[METHOD] /api/[resource]
```

#### 내부 인터페이스 (Repository / Gateway / ACL)

### 5.3 데이터 모델

```sql
-- repo: <repo>
-- 테이블 변경 / 신규
```

### 5.4 Cross-repo 데이터 흐름

> 여러 repo를 가로지르는 시퀀스.

```
<client> → <api-gateway> → <service-a> → <service-b> → ...
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
