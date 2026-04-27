---
name: spec-driven-development-2
description: 여러 독립 git repo로 구성된 워크스페이스(synthetic monorepo)에서 한 기능이 여러 repo에 걸칠 때 사용하는 테크스펙·구현 계획 워크플로우. cross-repo 영향 분석, 인터페이스/스키마 backward compatibility, 동일 이름 브랜치·PR 생성을 spec 단계부터 정리합니다.
---

# 스펙 주도 개발 — Synthetic Monorepo

여러 독립 git repo로 구성된 워크스페이스에서 한 기능이 여러 repo를 가로지를 때, spec 단계에서 **영향 범위·인터페이스 backward compatibility·repo별 작업**을 명시적으로 정리하는 워크플로우.

> **Synthetic monorepo**: 여러 독립 git repo를 워크스페이스 루트로 묶어 단일 dependency graph로 다루는 개발 모델. 각 repo는 자기 git 히스토리·CI·릴리스를 유지함.
>
> **Skill 범위**: spec 작성 → 각 repo에 동일 이름 브랜치 생성 → PR 생성. 코드 리뷰·머지는 사람이 수동 진행 (범위 밖).
>
> **도구**: 워크스페이스는 [mani CLI](https://manicli.com/)로 multi-repo 운용. 본 skill의 모든 cross-repo 명령은 `mani.yaml`의 project name을 정본으로 사용합니다.

---

## 핵심 룰

코드는 분산돼있어도 dependency graph는 하나 — spec도 그래야 합니다.

- **Spec은 워크스페이스 루트에**: `<workspace>/docs/spec/`이 단일 source of truth. 한 repo의 `docs/`에 두면 다른 repo PR에서 안 보임
- **영향 분석을 가장 먼저**: TECH-SPEC의 첫 섹션은 "영향받는 서비스/패키지 매트릭스"
- **매트릭스 라벨 = mani project name**: TECH-SPEC §1의 repo 라벨은 `mani.yaml`의 project key와 정확히 일치해야 함. PLAN의 모든 cross-repo 명령이 이 이름으로 실행됨
- **인터페이스/스키마는 spec 단계에서 검증**: proto / GraphQL / OpenAPI / 이벤트 스키마의 backward compatibility를 미리 체크. 구현 후 발견하면 비용 큼
- **동일 이름 브랜치**: 영향받는 모든 repo에 `feat/<feature-name>` — cross-repo PR 추적이 쉬워짐
- **점진적 상세화**: 영향 매트릭스 → 요구사항 → 설계 순. 처음부터 완벽할 필요 없음

---

## 워크플로우

### 0. Pre-flight (spec 시작 전)

영향 후보 영역의 working tree가 깨끗하고 origin과 동기화돼있어야 후속 단계가 깨끗하게 굴러갑니다.

```sh
# 후보 영역 fetch (예: 결제 코어 변경이면 service + interface)
mani run fetch --tags-expr 'service || interface' --parallel

# 더러운 working tree 사전 발견 (출력이 비어있으면 OK)
mani run status --tags-expr 'service || interface'
```

dirty repo가 발견되면 spec 시작 전 commit/stash 처리.

---

### 1. 영향 분석

**1.1 인벤토리**: `mani list projects --tags <tag>` 또는 `mani list projects --tags-expr '...'`로 후보 좁히기.

**1.2 영향 repo 확정**: Claude에 위임. Claude는 변경 대상의 case 변형(camelCase/snake_case/PascalCase)·간접 참조·노출 채널을 종합해 영향 repo를 확정. 도구는 자율 선택 (`mani exec --parallel grep`, 직접 Read, Explore 서브에이전트 — 큰 워크스페이스면 Explore로 컨텍스트 격리).

검토 채널 (해당되는 것만): gRPC/proto · GraphQL · REST path · Webhook event type · MQ topic · 공유 DTO 클래스

**검증 원칙**: 모든 영향 주장에 raw grep 출력이 근거로 따라붙어야 함. 근거 없는 주장은 false positive. 호환성 경계(이벤트 소싱·MQ·공개 API)면 append-only 강제.

**1.3 결과 정리**: 영향받는 repo 이름은 mani project name 그대로 TECH-SPEC §1 매트릭스로 옮김.

---

### 2. TECH-SPEC 작성

- 위치: `<workspace>/docs/spec/NNN-기능명/TECH-SPEC.md` (NNN: 3자리, 기존 폴더 최대 번호+1, 없으면 001부터 / 기능명: kebab-case)
- [TECH-SPEC.template.md](./TECH-SPEC.template.md) 사용
- §1 "영향받는 서비스/패키지 매트릭스"를 먼저 채워 골격을 잡고, 나머지 섹션을 그 위에 빌드
- 매트릭스 행의 repo 라벨은 `mani list projects`로 검증된 정확한 이름 사용
- 첨부물(다이어그램, 외부 문서)은 같은 폴더에

---

### 3. PLAN 작성 (사용자 승인 후에만)

- 위치: `<workspace>/docs/spec/NNN-기능명/PLAN.md`
- [PLAN.template.md](./PLAN.template.md) 사용
- 템플릿 상단의 `IMPACTED` 변수에 §1 매트릭스의 repo 이름들을 콤마 구분으로 채움
- repo별 작업 내용은 여전히 수동 (구체 코드 변경)이지만 브랜치 생성·푸시·PR은 mani 명령으로 표준화
- spec이 짧거나 영향 repo가 1개면 생략 가능

---

### 4. 브랜치 + PR 생성

`IMPACTED` 변수는 PLAN.md에서 정의됨 (§1 매트릭스 → mani project name 콤마 리스트).

```sh
# 영향 repo 모두 main 기반인지 + 클린 상태인지 마지막 확인
mani run preflight --projects $IMPACTED

# 동일 이름 브랜치 일괄 생성
BRANCH="feat/<feature-name>"
mani exec --projects $IMPACTED "git checkout -b $BRANCH"

# (각 repo에서 코드 변경 — 본 skill 범위 밖)

# 푸시
mani exec --projects $IMPACTED --parallel 'git push -u origin HEAD'

# PR 일괄 생성 (본문에 spec 폴더 링크)
mani exec --projects $IMPACTED \
  "gh pr create --fill --draft --body 'Spec: docs/spec/NNN-기능명/'"

# PR 매트릭스 자동 채움 — 출력 결과를 PLAN.md PR 매트릭스 표에 옮김
mani exec --projects $IMPACTED 'gh pr view --json url,title,state'
```

---

## 파일 참조 규칙

cross-repo 변경은 파일 위치 모호성이 크므로:

- 항상 **mani project name부터** 시작: `<project>/path/to/file.ext:42` (project name은 `mani list projects`로 검증)
- 안정적 참조에는 commit hash 추가 (path/line은 시간이 지나면 drift)
- 인터페이스 스키마는 line number보다 **field/operation 식별자** 명시 (proto field number, GraphQL field name 등)
