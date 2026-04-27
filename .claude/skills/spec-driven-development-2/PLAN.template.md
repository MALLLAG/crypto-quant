# [기능명] 구현 계획

> 관련 테크스펙: [TECH-SPEC.md](./TECH-SPEC.md)

---

## 영향받는 repo (mani project names)

TECH-SPEC §1 매트릭스의 `Repo` 컬럼을 그대로 옮긴 콤마 리스트:

```sh
IMPACTED="<project-A>,<project-B>,<project-C>"
BRANCH="feat/<feature-name>"
```

검증:
```sh
mani list projects --projects $IMPACTED
```

---

## 0. Pre-flight

```sh
# 영향 repo 모두 fetch + 클린 상태 확인
mani run preflight --projects $IMPACTED
```

dirty 면 commit/stash 후 진행.

---

## 1. 동일 이름 브랜치 일괄 생성

```sh
mani exec --projects $IMPACTED "git checkout -b $BRANCH"
```

---

## 1.5 머지 순서

빌드 호환성을 위해 의존 순서대로 머지. 인터페이스(proto/schema)가 먼저, 사용처가 나중.

| 순서 | Repo | 의존 근거 |
|------|------|----------|
| 1 | `<interface-repo>` | 다른 repo가 이 proto/schema를 import |
| 2 | `<service-repo>` | 위 interface 사용 |
| 3 | `<gateway-repo>` | service 사용 |
| 4 | `<api-or-client-repo>` | 모든 의존 머지 완료 후 |

> **주의**: 머지 순서를 어기면 SNAPSHOT 의존 또는 빌드 깨짐. interface 변경분이 published 안 된 상태에서 사용처 PR을 머지하면 main이 빌드 안 됨.

---

## 2. Repo별 작업

> 코드 변경은 repo별로 수동. TECH-SPEC §1, §2.2 참조.

### `<project-A>`
- [ ] [구체 작업 1]
- [ ] [구체 작업 2]
- [ ] 빌드 / 테스트 / lint (아래 §3 참조)
- [ ] 변경 커밋

### `<project-B>`
- [ ] [구체 작업 1]
- [ ] 빌드 / 테스트 / lint
- [ ] 변경 커밋

### `<project-C>`
- [ ] ...

---

## 3. 코드 품질 검사 (repo별)

언어/빌드 시스템이 이질적이라 repo별로 명시:

| Repo | 명령어 |
|------|--------|
| `<project-A>` (Scala) | `sbt compile test` |
| `<project-B>` (Kotlin) | `./gradlew build test lintKotlin` |
| `<project-C>` (TS) | `pnpm install && pnpm build && pnpm test && pnpm lint` |

---

## 4. 푸시

```sh
mani exec --projects $IMPACTED --parallel 'git push -u origin HEAD'
```

---

## 5. PR 일괄 생성

```sh
mani exec --projects $IMPACTED \
  "gh pr create --fill --draft --body 'Spec: docs/spec/NNN-기능명/'"
```

---

## 5.5 CI 체크 상태 조회

PR 생성 직후 CI가 깨지는 repo 식별:

```sh
mani exec --projects $IMPACTED --output table 'gh pr checks'
```

깨진 repo가 있으면 §2로 돌아가 수정 → 다시 push (§4) → 다시 §5.5.

---

## 6. PR 매트릭스

```sh
# 출력을 아래 표에 채움
mani exec --projects $IMPACTED 'gh pr view --json url,title,state'
```

| Repo | 브랜치 | PR URL | State |
|------|--------|--------|-------|
| `<project-A>` | `feat/<feature-name>` | | |
| `<project-B>` | `feat/<feature-name>` | | |
| `<project-C>` | `feat/<feature-name>` | | |

---

## 완료 기준 (PR 생성까지)

- [ ] §1 — 영향받는 모든 repo에 동일 이름 브랜치 생성 (`mani exec` 결과 확인)
- [ ] §1.5 — 머지 순서 표 채움 (의존 근거 포함)
- [ ] §2 — 각 repo에서 코드 변경 완료
- [ ] §3 — 각 repo에서 코드 품질 검사 통과
- [ ] §4 — 모든 브랜치 push 성공
- [ ] §5 — 모든 repo에 PR 생성
- [ ] §5.5 — 모든 PR의 CI 통과
- [ ] §6 — PR 매트릭스 표 채움

> 코드 리뷰 / 머지는 별도 진행 (이 계획의 범위 밖).
