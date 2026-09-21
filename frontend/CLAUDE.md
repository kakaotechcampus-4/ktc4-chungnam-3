# CLAUDE.md

## 작업 범위

- `frontend/` 아래에서만 작업한다. `backend/`, `AI/`, `docs/`, `.github/` 는 건드리지 않는다.
- backend 코드를 참고해야 하면 `git show develop:경로` 로 읽는다. 체크아웃하지 않는다.

## git

- **커밋하지 않는다.** `git add`, `git commit`, `git push`, `git stash` 금지.
- 브랜치를 전환하거나 새로 만들지 않는다. `checkout`, `switch`, `branch` 금지.
- 파일만 워킹트리에 만들고, 나머지는 사람이 한다.

## 구조

- 디렉토리 구조와 설계 근거는 `frontend/STRUCTURE.md` 를 따른다.
- STRUCTURE.md 에 없는 디렉토리나 파일을 임의로 만들지 않는다.
  필요해 보이면 만들지 말고 먼저 물어본다.

## 의존 방향

features/ ──┐
├──> domain/ <──── runtime/
shared/ ───┘

- `runtime/` 은 `features/` 와 `app/navigation` 을 import 하지 않는다.
- `features/` 끼리 서로 import 하지 않는다. 공유가 필요하면 `domain/` 이나 `shared/` 로 올린다.
- `domain/` 은 아무것도 import 하지 않는다.
- `features/` 는 `domain/extraction/` 을 직접 import 하지 않는다. `shared/api/mappers/` 를 거친다.

## 백엔드 계약

- 백엔드에 Controller 가 아직 없다. API 계약은 존재하지 않는다.
- `domain/extraction/` 만 백엔드 코드에 실재하는 타입이다. 여기에 없는 필드를 추가하지 않는다.
- 나머지 `domain/` 은 미확정 앱 모델이다. 값 목록을 확정적으로 쓰지 않는다.
- 서버 응답 형태를 추측해서 코드에 넣지 않는다. 모르면 물어본다.

## 코드

- 라이브러리를 임의로 추가하지 않는다. `package.json` 수정 전에 물어본다.
- 주석은 짧게. 설계 근거는 STRUCTURE.md 의 역할이다.
