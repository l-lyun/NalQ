# Web Frontend Guide

이 지침은 `web/` 아래의 모든 작업에 적용된다.

## 필수 맥락

UI를 계획하거나 구현하기 전에 다음을 수행한다.

1. `web/DESIGN.md`를 끝까지 읽는다.
2. 기존 화면, 공용 컴포넌트와 코드 관례를 확인한다.
3. SEED Docs MCP로 현재 컴포넌트, props, 토큰과 아이콘을 검증한다.
4. Figma URL이 제공된 경우에만 SEED Figma MCP로 해당 노드를 확인한다.

현재 Figma는 필수 설계 원본이 아니다. 제품 요구사항과 `web/DESIGN.md`를 기준으로 판단한다.

## SEED Design 규칙

- custom 컴포넌트보다 `@seed-design/react` 컴포넌트를 우선한다.
- 하드코딩한 CSS 값보다 SEED semantic token과 recipe를 우선한다.
- SEED에 존재하는 상호작용 컴포넌트를 raw HTML/CSS로 다시 만들지 않는다.
- SEED 컴포넌트 이름, props, 토큰과 아이콘을 추측하지 말고 MCP 문서로 확인한다.
- 적합한 SEED 컴포넌트가 없으면 custom 구현 전에 `web/DESIGN.md`에 예외와 이유를 기록한다.
- 접근성 의미, 키보드 조작, focus-visible, loading, disabled와 오류 상태를 보존한다.

## 에이전트 작업 흐름

새 화면이나 의미 있는 UI 작업은 가능한 한 다음 순서로 진행한다.

1. `seed_ui_designer`가 요구사항을 정보 구조, 화면 상태와 SEED 컴포넌트 매핑으로 정리한다.
2. `seed_publisher`가 확정된 명세를 시각적 UI로 구현한다.
3. `frontend_engineer`가 라우팅, 상태, 폼, 쿼리와 API를 연결한다.
4. 완성 화면을 시각적으로 확인하고 프로젝트 검사를 수행한다.

두 쓰기 에이전트가 같은 화면이나 파일을 동시에 수정하게 하지 않는다. 설계와 문서 조사는 병렬화할 수 있지만 퍼블리싱과 기능 통합은 순차적으로 진행한다.

## 범위와 검증

- 명시적인 요청이 없으면 `web/` 밖을 수정하지 않는다.
- 패키지 관리는 pnpm을 사용한다.
- 변경 후 다음 검사를 실행한다.
  - `pnpm typecheck`
  - `pnpm lint`
  - `pnpm build`
