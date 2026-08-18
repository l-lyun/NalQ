# OpenMD 프런트엔드 에이전트 사용 가이드

이 문서는 Figma 없이 제품 요구사항과 SEED Design을 바탕으로 `web/` 화면을 설계하고 구현하는 방법을 정리한다.

## 관련 파일

| 파일 | 역할 |
| --- | --- |
| `web/AGENTS.md` | `web/` 작업에서 자동으로 적용되는 필수 작업 규칙 |
| `web/DESIGN.md` | OpenMD의 디자인 방향, 판단 기준과 예외 기록 |
| `.codex/agents/seed-ui-designer.toml` | 읽기 전용 UI 설계 에이전트 |
| `.codex/agents/seed-publisher.toml` | SEED 기반 퍼블리싱 에이전트 |
| `.codex/agents/frontend-engineer.toml` | 상태, 폼, 라우팅과 API 통합 에이전트 |

세 에이전트는 모두 `gpt-5.6-sol`, reasoning effort `medium`을 사용한다.

## 에이전트별 실제 지침 요약

### `seed_ui_designer`

- 코드를 수정하지 않는 읽기 전용 에이전트다.
- 제품 요구사항을 사용자 목표, 정보 위계, 주·보조 행동과 화면 상태로 분해한다.
- SEED Docs MCP에서 실제 컴포넌트, props, 토큰과 아이콘을 확인한다.
- 구현 전에 사용할 SEED 컴포넌트 매핑표와 반응형·접근성 명세를 작성한다.
- Figma가 없어도 제품 요구사항, `DESIGN.md`, 기존 패턴과 SEED 문서를 기준으로 판단한다.

### `seed_publisher`

- 확정된 UI 명세를 `web/`의 실제 React UI로 구현한다.
- SEED 컴포넌트와 semantic token을 우선하고, 기존 SEED 동작을 raw HTML/CSS로 재구현하지 않는다.
- 레이아웃, 타이포그래피, 아이콘, 반응형, 접근성과 화면 상태 표현을 담당한다.
- API, 인증, 서버 상태와 복잡한 비즈니스 로직은 담당하지 않는다.
- custom UI가 필요하면 SEED와 `web/src/shared`를 먼저 확인하고 예외를 기록한다.

### `frontend_engineer`

- 퍼블리싱된 화면에 라우팅, 상태, React Query, 폼 검증, API와 비즈니스 규칙을 연결한다.
- 기존 SEED 시각 구조와 접근성을 보존한다.
- 확정되지 않은 API 계약을 사실처럼 추측하지 않고 adapter, TODO 또는 mock 경계를 사용한다.
- 기능 요구사항과 UI가 충돌하면 임의로 바꾸지 않고 선택지를 보고한다.

각 TOML 파일의 `developer_instructions`가 실제로 에이전트에 전달되는 전체 한국어 프롬프트다.

## 권장 작업 순서

```text
제품 요구사항
→ seed_ui_designer의 UI 명세와 SEED 매핑
→ seed_publisher의 시각적 구현
→ frontend_engineer의 기능·API 연결
→ 시각 검증과 typecheck/lint/build
```

설계 조사는 독립적으로 진행할 수 있지만 `seed_publisher`와 `frontend_engineer`가 같은 파일을 동시에 수정하게 하지 않는다.

## 프롬프트 예시

### 새 화면 전체 구현

```text
이메일 회원가입 화면을 구현해줘.

사용자는 이메일, 비밀번호와 닉네임을 입력해 가입할 수 있어야 해.
이메일 중복, 입력 검증 오류, 제출 중 상태와 가입 실패를 보여줘야 해.

seed_ui_designer가 먼저 요구사항을 정보 구조, 화면 상태와 SEED 컴포넌트
매핑으로 정리하게 해줘. 그 결과를 기다린 다음 seed_publisher가 UI를 구현하고,
완료 후 frontend_engineer가 폼 검증과 API 연결을 구현하게 해줘.
쓰기 작업은 순차적으로 진행하고 마지막에 시각 검증과 전체 검사를 실행해줘.
```

### 설계안만 받기

```text
seed_ui_designer를 사용해서 검색 결과 화면의 UI 명세만 작성해줘.
검색 전, 로딩, 결과 있음, 결과 없음, 오류 상태를 포함하고 SEED Docs MCP로
후보 컴포넌트를 검증해줘. 아직 코드는 수정하지 마.
```

### 퍼블리싱만 하기

```text
이 요구사항을 기준으로 seed_publisher가 `web/`에 시각적 UI만 구현하게 해줘.
실제 API 대신 명시적인 props와 mock 데이터를 사용하고, SEED 컴포넌트와
semantic token을 우선해줘. 비즈니스 로직은 추가하지 마.
```

### 기능 연결만 하기

```text
현재 완성된 SEED UI는 변경하지 말고 frontend_engineer가 실제 라우팅,
React Query, 폼 검증과 API를 연결하게 해줘. loading, empty, retry, error와
success 상태를 모두 연결하고 typecheck, lint, build를 실행해줘.
```

## 요청에 포함하면 좋은 정보

- 사용자가 이 화면에서 달성하려는 목표
- 반드시 필요한 입력과 출력 데이터
- 주 행동과 완료 조건
- 로딩, 빈 상태와 오류 처리 요구사항
- API가 준비됐는지 여부
- 모바일과 데스크톱 중 더 중요한 환경
- 반드시 재사용해야 하는 기존 화면이나 컴포넌트

정보가 부족하면 에이전트는 `web/DESIGN.md`에 따라 가장 단순하고 재사용 가능한 SEED 패턴을 선택한다.
