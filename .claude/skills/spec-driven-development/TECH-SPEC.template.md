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

### 1.4 관련 문서
- [관련 테크스펙 또는 문서 링크]
- [외부 API 문서]
- [참고 자료]

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

#### 3.2.3 집합체 (Aggregates)

```kotlin
/**
 * [집합체 설명]
 *
 * 불변식 (Invariants):
 * - [불변식 1]
 * - [불변식 2]
 */
sealed interface ExampleAggregate {
    val id: ExampleId
    // ...
}
```

#### 3.2.4 도메인 이벤트 (Domain Events)

```kotlin
sealed interface ExampleEvent {
    val occurredAt: Instant

    data class ExampleCreated(
        val id: ExampleId,
        override val occurredAt: Instant = Instant.now(),
    ) : ExampleEvent

    data class ExampleUpdated(
        val id: ExampleId,
        val changes: List<String>,
        override val occurredAt: Instant = Instant.now(),
    ) : ExampleEvent
}
```

#### 3.2.5 도메인 오류 (Domain Errors)

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

### 4.1 기술 선택

| 항목 | 선택 | 대안 | 선택 이유 |
|------|------|------|-----------|
| 기술1 | 옵션A | 옵션B, 옵션C | [상세한 선택 이유] |
| 기술2 | 옵션X | 옵션Y | [상세한 선택 이유] |

---

## 5. 엣지 케이스 및 예외 처리

### 5.1 엣지 케이스

| 케이스 | 상황 | 예상 동작 | 처리 방법 |
|--------|------|-----------|-----------|
| EC-001 | [상황 설명] | [예상 결과] | [처리 로직] |
| EC-002 | [상황 설명] | [예상 결과] | [처리 로직] |
| EC-003 | 빈 입력값 | 검증 오류 반환 | 스마트 생성자에서 검증 |
| EC-004 | 중복 요청 | 멱등성 보장 | 중복 체크 후 기존 결과 반환 |
| EC-005 | 동시 수정 | 충돌 감지 | Optimistic Locking |

### 5.2 오류 처리 전략

```kotlin
// 오류 타입별 처리 전략
sealed interface ErrorHandler {

    // 재시도 가능한 오류
    object Retryable : ErrorHandler {
        val maxRetries = 3
        val backoffMs = listOf(100, 500, 2000)
    }

    // 즉시 실패
    object FailFast : ErrorHandler

    // 폴백 처리
    data class Fallback(val fallbackValue: Any) : ErrorHandler
}
```

### 5.3 경계 조건

| 항목 | 최소값 | 최대값 | 초과 시 동작 |
|------|--------|--------|--------------|
| 이름 길이 | 1자 | 100자 | ValidationError |
| 목록 크기 | 0 | 1000 | 페이지네이션 |
| 요청 크기 | - | 1MB | 413 에러 |

---

## 6. 위험 요소 및 완화 방안

| ID | 위험 요소 | 발생 확률 | 영향도 | 완화 방안 | 대응 계획 |
|----|-----------|-----------|--------|-----------|-----------|
| R-001 | [위험 설명] | 높음/중간/낮음 | 높음/중간/낮음 | [사전 대응] | [발생 시 대응] |
| R-002 | 외부 API 장애 | 중간 | 높음 | Circuit Breaker 적용 | 폴백 로직 실행 |
| R-003 | DB 성능 저하 | 낮음 | 높음 | 인덱스 최적화, 쿼리 튜닝 | 캐시 레이어 추가 |

---

## 7. 테스트 전략

### 7.1 단위 테스트

#### 테스트 대상
- 도메인 모델 (값 객체, 엔터티)
- 스마트 생성자 검증 로직
- 비즈니스 규칙

#### 테스트 케이스 예시

```kotlin
class ExampleIdTest {
    @Test
    fun `유효한 ID로 생성 성공`() = runTest {
        val result = either { ExampleId("valid-id") }
        result.shouldBeRight()
    }

    @Test
    fun `빈 문자열로 생성 시 실패`() = runTest {
        val result = either { ExampleId("") }
        result.shouldBeLeft(InvalidExampleId("ID는 비어있을 수 없습니다"))
    }
}
```

### 7.2 통합 테스트

#### 테스트 대상
- Repository 구현체
- 외부 서비스 연동
- API 엔드포인트

#### 테스트 환경
- TestContainers (PostgreSQL)
- WireMock (외부 API 모킹)

### 7.3 E2E 테스트

#### 시나리오
1. [시나리오 1: 정상 플로우]
   - Given: [초기 조건]
   - When: [액션]
   - Then: [예상 결과]

2. [시나리오 2: 오류 플로우]
   - Given: [초기 조건]
   - When: [액션]
   - Then: [예상 결과]

