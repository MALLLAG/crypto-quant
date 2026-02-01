# [기능명] 테크스펙

> **작성일**: YYYY-MM-DD
> **작성자**: [작성자]

---

## 1. 개요

### 1.1 목적
[이 기능이 해결하려는 문제와 목적을 명확하게 설명]

- **문제 정의**: [현재 어떤 문제가 있는가?]
- **해결 방안**: [이 기능이 어떻게 문제를 해결하는가?]
- **기대 효과**: [구현 후 어떤 가치를 제공하는가?]

### 1.2 범위

#### 포함 (In Scope)
- [이 기능에 포함되는 것 1]
- [이 기능에 포함되는 것 2]

#### 제외 (Out of Scope)
- [이 기능에 포함되지 않는 것 1]
- [이 기능에 포함되지 않는 것 2]

### 1.3 용어 정의

| 용어 | 설명 | 예시 |
|------|------|------|
| 용어1 | 정의 | 사용 예시 |
| 용어2 | 정의 | 사용 예시 |

---

## 2. 요구사항

### 2.1 기능적 요구사항

#### FR-001: [요구사항 제목]
- **설명**: [상세 설명]
- **입력**: [입력 데이터/조건]
- **출력**: [예상 출력/결과]
- **검증 기준**: [어떻게 테스트할 것인가]

#### FR-002: [요구사항 제목]
- **설명**: [상세 설명]
- **입력**: [입력 데이터/조건]
- **출력**: [예상 출력/결과]
- **검증 기준**: [어떻게 테스트할 것인가]

### 2.2 비기능적 요구사항

#### NFR-001: 성능
- **응답 시간**: [예: API 응답 시간 < 100ms (p95)]
- **처리량**: [예: 초당 1000 요청 처리]
- **측정 방법**: [어떻게 측정할 것인가]

#### NFR-002: 보안
- **인증**: [인증 요구사항]
- **인가**: [권한 요구사항]
- **데이터 보호**: [암호화, 마스킹 등]

#### NFR-003: 가용성
- **SLA**: [예: 99.9% 가용성]
- **장애 복구**: [복구 전략]

#### NFR-004: 확장성
- **수평 확장**: [확장 전략]
- **제한 사항**: [알려진 제한]

### 2.3 제약 조건
- [기술적 제약]
- [비즈니스 제약]
- [시간/리소스 제약]

---

## 3. 시스템 설계

### 3.1 아키텍처 개요

```
[아키텍처 다이어그램 - ASCII 또는 설명]

┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Presentation│────▶│ Application │────▶│   Domain    │
│   Layer     │     │    Layer    │     │   Layer     │
└─────────────┘     └─────────────┘     └─────────────┘
                          │
                          ▼
                   ┌─────────────┐
                   │Infrastructure│
                   │    Layer    │
                   └─────────────┘
```

### 3.2 도메인 모델

#### 3.2.1 값 객체 (Value Objects)

```kotlin
/**
 * [값 객체 설명]
 *
 * 불변 규칙:
 * - [규칙 1]
 * - [규칙 2]
 */
@JvmInline
value class ExampleId private constructor(val value: String) {
    companion object {
        context(_: Raise<DomainError>)
        operator fun invoke(value: String): ExampleId {
            ensure(value.isNotBlank()) { InvalidExampleId("ID는 비어있을 수 없습니다") }
            return ExampleId(value)
        }
    }
}
```

#### 3.2.2 엔터티 (Entities)

```kotlin
/**
 * [엔터티 설명]
 *
 * 비즈니스 규칙:
 * - [규칙 1]
 * - [규칙 2]
 */
data class ExampleEntity(
    val id: ExampleId,
    val name: ExampleName,
    val status: ExampleStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

#### 3.2.3 도메인 오류 (Domain Errors)

```kotlin
sealed interface ExampleError {
    data class NotFound(val id: ExampleId) : ExampleError
    data class ValidationFailed(val reason: String) : ExampleError
    data class BusinessRuleViolation(val rule: String) : ExampleError
}
```

### 3.3 API 설계

#### 3.3.1 REST API

##### POST /api/examples
새 Example 생성

**Request**
```json
{
  "name": "string (required, 1-100자)",
  "description": "string (optional, 최대 500자)",
  "type": "TYPE_A | TYPE_B"
}
```

**Response (201 Created)**
```json
{
  "id": "string",
  "name": "string",
  "status": "PENDING",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

**Error Responses**

| 상태 코드 | 오류 코드 | 설명 |
|-----------|-----------|------|
| 400 | VALIDATION_ERROR | 입력값 검증 실패 |
| 401 | UNAUTHORIZED | 인증 필요 |
| 409 | DUPLICATE | 중복된 리소스 |

##### GET /api/examples/{id}
Example 조회

**Path Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| id | string | Yes | Example ID |

**Response (200 OK)**
```json
{
  "id": "string",
  "name": "string",
  "status": "ACTIVE",
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z"
}
```

#### 3.3.2 내부 인터페이스

```kotlin
/**
 * Example 저장소 인터페이스
 */
interface ExampleRepository {
    suspend fun save(example: Example): Unit
    suspend fun findById(id: ExampleId): Example?
    suspend fun findByStatus(status: ExampleStatus): List<Example>
    suspend fun delete(id: ExampleId): Unit
}

/**
 * 외부 서비스 게이트웨이
 */
interface ExternalServiceGateway {
    context(_: Raise<ExternalError>)
    suspend fun call(request: ExternalRequest): ExternalResponse
}
```

### 3.4 데이터 모델

#### 3.4.1 데이터베이스 스키마

```sql
-- Example 테이블
CREATE TABLE examples (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED'))
);

-- 인덱스
CREATE INDEX idx_examples_status ON examples(status);
CREATE INDEX idx_examples_created_at ON examples(created_at DESC);
```

#### 3.4.2 엔터티 매핑

```kotlin
/**
 * DB 엔터티 <-> 도메인 모델 매핑
 */
data class ExampleEntity(
    val id: String,
    val name: String,
    val description: String?,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    suspend fun toDomain(): Either<MappingError, Example> = either {
        Example(
            id = ExampleId(id),
            name = ExampleName(name),
            status = ExampleStatus.valueOf(status),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
```

### 3.5 시퀀스 다이어그램

```
[주요 플로우에 대한 시퀀스 다이어그램]

Client          Controller       UseCase         Repository      Database
  │                 │               │                │               │
  │  POST /examples │               │                │               │
  │────────────────▶│               │                │               │
  │                 │   execute()   │                │               │
  │                 │──────────────▶│                │               │
  │                 │               │    save()      │               │
  │                 │               │───────────────▶│               │
  │                 │               │                │   INSERT      │
  │                 │               │                │──────────────▶│
  │                 │               │                │      OK       │
  │                 │               │                │◀──────────────│
  │                 │               │      Unit      │               │
  │                 │               │◀───────────────│               │
  │                 │   Created     │                │               │
  │                 │◀──────────────│                │               │
  │   201 Created   │               │                │               │
  │◀────────────────│               │                │               │
```

---

## 4. 기술적 결정사항

| 항목 | 선택 | 대안 | 선택 이유 |
|------|------|------|-----------|
| 기술1 | 옵션A | 옵션B, 옵션C | [상세한 선택 이유] |
| 기술2 | 옵션X | 옵션Y | [상세한 선택 이유] |
