# [기능명] 구현 계획

> 관련 테크스펙: [TECH-SPEC.md](./TECH-SPEC.md)

---

## 공통 브랜치 이름

```
feat/<feature-name>
```

영향받는 모든 repo에 위 이름으로 동일하게 생성.

---

## Repo별 작업

### `<repo-A>`

- [ ] 변경 사항 (TECH-SPEC §1, §2.2 참조)
  - [ ] [구체 작업 1]
  - [ ] [구체 작업 2]
- [ ] 빌드 / 테스트 / lint
- [ ] 커밋 + push
- [ ] PR 생성

### `<repo-B>`

- [ ] 변경 사항
  - [ ] ...
- [ ] 빌드 / 테스트 / lint
- [ ] 커밋 + push
- [ ] PR 생성

### `<repo-C>`

- [ ] ...

---

## PR 매트릭스 (생성 후 채움)

| Repo | 브랜치 | PR URL |
|------|--------|--------|
| `<repo-A>` | `feat/<feature-name>` | |
| `<repo-B>` | `feat/<feature-name>` | |
| `<repo-C>` | `feat/<feature-name>` | |

---

## 코드 품질 검사 (repo별)

각 repo에서 PR 생성 전 통과해야 할 명령어:

| Repo | 명령어 |
|------|--------|
| `<repo-A>` | `<build/lint/test command>` |
| `<repo-B>` | `<build/lint/test command>` |

---

## 완료 기준 (PR 생성까지)

- [ ] 영향받는 모든 repo에 동일 이름 브랜치 생성
- [ ] 각 repo에서 작업 완료 + 코드 품질 검사 통과
- [ ] 각 repo에 PR 생성

> 코드 리뷰 / 머지는 별도 진행 (이 계획의 범위 밖).
