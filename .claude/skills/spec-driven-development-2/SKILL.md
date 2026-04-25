---
name: spec-driven-development-2
description: 여러 독립 git repo로 구성된 워크스페이스(synthetic monorepo)에서 한 기능이 여러 repo에 걸칠 때 사용하는 테크스펙·구현 계획 워크플로우. cross-repo 영향 분석, 인터페이스/스키마 backward compatibility, 동일 이름 브랜치·PR 생성을 spec 단계부터 정리합니다.
---

# 스펙 주도 개발 — Synthetic Monorepo

여러 독립 git repo로 구성된 워크스페이스에서 한 기능이 여러 repo를 가로지를 때, spec 단계에서 **영향 범위·인터페이스 backward compatibility·repo별 작업**을 명시적으로 정리하는 워크플로우.

> **Synthetic monorepo**: 여러 독립 git repo를 워크스페이스 루트로 묶어 단일 dependency graph로 다루는 개발 모델. 각 repo는 자기 git 히스토리·CI·릴리스를 유지함.
>
> **Skill 범위**: spec 작성 → 각 repo에 동일 이름 브랜치 생성 → PR 생성. 코드 리뷰·머지는 사람이 수동 진행 (범위 밖).

---

## 핵심 룰

코드는 분산돼있어도 dependency graph는 하나 — spec도 그래야 합니다.

- **Spec은 워크스페이스 루트에**: `<workspace>/docs/spec/`이 단일 source of truth. 한 repo의 `docs/`에 두면 다른 repo PR에서 안 보임
- **영향 분석을 가장 먼저**: TECH-SPEC의 첫 섹션은 "영향받는 서비스/패키지 매트릭스"
- **인터페이스/스키마는 spec 단계에서 검증**: proto / GraphQL / OpenAPI / 이벤트 스키마의 backward compatibility를 미리 체크. 구현 후 발견하면 비용 큼
- **동일 이름 브랜치**: 영향받는 모든 repo에 `feat/<feature-name>` — cross-repo PR 추적이 쉬워짐
- **점진적 상세화**: 영향 매트릭스 → 요구사항 → 설계 순. 처음부터 완벽할 필요 없음

---

## 워크플로우

### 1. 영향 분석

1. **영향 repo 식별**: 워크스페이스 dependency graph를 따라
2. **인터페이스/스키마 변경 여부**: 있으면 backward compatibility 의무 발생
3. **호환성 경계**: 이벤트 소싱·메시지 큐·공개 API 등 replay/구버전 클라이언트가 영향받는 구조에선 append-only 강제

### 2. TECH-SPEC 작성

- 위치: `<workspace>/docs/spec/NNN-기능명/TECH-SPEC.md` (NNN: 3자리, 기존 폴더 최대 번호+1, 없으면 001부터 / 기능명: kebab-case)
- [TECH-SPEC.template.md](./TECH-SPEC.template.md) 사용
- "영향받는 서비스/패키지 매트릭스"를 먼저 채워 골격을 잡고, 나머지 섹션을 그 위에 빌드
- 첨부물(다이어그램, 외부 문서)은 같은 폴더에

### 3. PLAN 작성 (사용자 승인 후에만)

- 위치: `<workspace>/docs/spec/NNN-기능명/PLAN.md`
- [PLAN.template.md](./PLAN.template.md) 사용
- repo별 작업 항목·체크리스트가 핵심
- spec이 짧거나 영향 repo가 1개면 생략 가능

### 4. 브랜치 + PR 생성

- 영향받는 모든 repo에 `feat/<feature-name>` 브랜치 생성
- 각 repo에서 작업 → push → PR 생성

---

## 파일 참조 규칙

cross-repo 변경은 파일 위치 모호성이 크므로:

- 항상 **repo 이름부터** 시작: `<repo>/path/to/file.ext:42`
- 안정적 참조에는 commit hash 추가 (path/line은 시간이 지나면 drift)
- 인터페이스 스키마는 line number보다 **field/operation 식별자** 명시 (proto field number, GraphQL field name 등)
