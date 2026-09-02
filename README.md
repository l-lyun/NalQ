# 📚 OpenMD

> **읽은 글이, 풀어본 지식이 되도록.**

### 가지고 있던 글에서 시작해, 문제를 풀고 이해를 확인하는 AI 학습 서비스

글을 읽는 것에서 멈추지 않고, 직접 떠올리고 확인하며 다시 보는 학습 루프를 만듭니다.

` 글 가져오기 → 문제 풀기 → 피드백 확인 → 틀린 문제 다시 풀기 `

<br>

## ✨ 읽기에서 복습까지, 하나의 흐름으로

### 📥 가지고 있던 글을, 바로 학습자료로

텍스트를 붙여 넣거나 Notion 단일 페이지를 한 번 복사합니다. 가져온 내용을 확인하고 다듬은 뒤 나만의 학습자료로 저장합니다.

### 🎯 내 방식대로 만드는 AI 퀴즈

객관식·빈칸 채우기·단답형·서술형, 세 단계 난이도, 최대 `5`·`10`·`15`문제 중 원하는 조건을 고릅니다. 퀴즈는 학습자료 원문을 근거로 만듭니다.

### ✅ 정해진 답은 자동으로, 서술형은 스스로

객관식·빈칸 채우기·단답형은 정해진 규칙으로 바로 채점합니다. 서술형은 AI의 단정 대신 모범 답안과 핵심 포인트를 보며 `정답`·`보완 필요`·`오답`으로 스스로 평가합니다.

### 🔎 틀린 이유는 원문에서, 다음 행동은 복습으로

내 답, 정답·모범 답안, 판정, 해설과 관련된 학습자료 본문을 함께 보며 놓친 부분을 찾습니다. 가장 최근에 완료한 퀴즈의 미해결 문제만 모아 다시 풀 수 있습니다.

> 서비스 소개는 현재 MVP 제품 요구사항을 기준으로 합니다. 세부 정책과 영역별 상태는 [제품 문서 지도](./docs/README.md)에서 확인할 수 있습니다.

<br>

## 🧰 Tech Stack

| 영역 | 기술 |
| --- | --- |
| Web | React 19, TypeScript 6, Vite 8, React Router 7, TanStack Query 5, Axios, SEED Design |
| Server | Java 21, Spring Boot 4.1, Gradle, Spring Data JPA, Spring Security, Flyway, SpringDoc OpenAPI |
| Data | MySQL 8.4, Redis 7.4 |
| App | Expo SDK 57, React Native 0.86, React Native WebView |
| Test & Quality | JUnit 5, Testcontainers, Node.js Test Runner, TypeScript, Oxlint |

<br>

## 🗂️ 저장소 구조

OpenMD는 서버, 웹, 모바일 앱을 한 저장소에서 관리하는 monorepo입니다.

```text
openmd/
├── server/  # Java 21, Spring Boot 4, Gradle
├── web/     # React, TypeScript, Vite, pnpm, SEED Design
└── app/     # Expo, React Native, TypeScript, pnpm
```

```text
Browser ─────────────┐
                      ▼
                 Web ── API ──▶ Server ──┬──▶ MySQL
                      ▲                       └──▶ Redis
Mobile App ── WebView ─┘
```

- `web/`은 인증, 홈, 학습자료, 퀴즈, 마이페이지의 사용자 화면과 상태를 담습니다.
- `server/`는 인증, 학습자료, Notion 가져오기, 퀴즈 생성·채점·복습 API와 데이터를 다룹니다.
- `app/`은 OpenMD 웹 서비스를 표시하고 네이티브 탐색·오류 상태를 처리하는 WebView 앱 셸입니다.

<br>

## 🚀 로컬 개발 환경

### 📌 사전 준비

- Java 21
- Node.js 22.12 이상 (`.tool-versions`는 22.22.0)
- pnpm 11.18.0
- Docker Desktop 또는 Docker Engine + Compose
- MCP를 사용할 경우 Codex CLI

### 1. 🐳 인프라 실행

MySQL과 Redis만 Docker Compose로 실행합니다. 루트 `.env.example`의 값은 로컬 개발용 기본값이며 실제 환경에서는 변경해야 합니다.

```bash
cp .env.example .env
docker compose up -d
docker compose ps
```

데이터는 `mysql-data`, `redis-data` named volume에 보존됩니다.

### 2. ☕ 서버 실행

주요 의존성은 Spring Web MVC, Validation, Data JPA, Security, MySQL Driver, Data Redis, Lombok, JUnit입니다.

```bash
cd server
./gradlew test
./gradlew bootRun
```

기본 포트는 `8080`입니다. `server/.env.example`은 변수 목록 예시이며 Spring Boot가 파일을 자동으로 읽지는 않습니다. 필요하면 IDE run configuration에 넣거나 shell에 export한 뒤 실행합니다.

```bash
cp .env.example .env
set -a
source .env
set +a
./gradlew bootRun
```

### 3. 🌐 웹 실행

React Router, TanStack Query, Axios와 SEED Design으로 구성됩니다. 인증, 홈, 학습자료, 퀴즈, 마이페이지 관련 route와 기능 코드가 `web/src`에 나뉘어 있습니다.

```bash
cd web
pnpm install
pnpm dev
pnpm typecheck
pnpm build
```

기본 개발 서버는 `http://localhost:5173`입니다. API 주소는 `web/.env.example`의 `VITE_API_BASE_URL`로 변경할 수 있습니다.

SEED는 `@seed-design/react`, `@seed-design/css`, `@seed-design/vite-plugin`으로 구성되어 있습니다. `src/main.tsx`에서 `base.css`를 불러오고 Vite plugin이 theme 및 component recipe CSS를 처리하므로 전역 Theme Provider는 필요하지 않습니다. 향후 SEED CLI component를 추가할 때는 다음처럼 실행합니다.

```bash
pnpm dlx @seed-design/cli@latest add ui:action-button
```

### 4. 📱 앱 실행

Expo SDK 57 TypeScript 프로젝트이며, `react-native-webview`로 웹 서비스를 표시하는 앱 셸을 구성합니다.

```bash
cd app
pnpm install
pnpm start
# 또는 pnpm ios / pnpm android
```

WebView가 열 웹 URL은 `app/.env.example`의 `EXPO_PUBLIC_WEB_URL`로 설정합니다.

<br>

## 📖 제품 문서

| 관심사 | 원장 | 상태 |
| --- | --- | --- |
| 제품 목적·범위·원칙·용어 | [OpenMD 제품 기반](./docs/product.md) | 초안 |
| 학습자료 가져오기 | [학습자료 만들기 PRD](./docs/prd/prd-content-import.md) | 검토 중 |
| 퀴즈 생성·풀이·결과·복습 | [퀴즈 PRD](./docs/prd/prd-quiz-learning.md) | 검토 중 |
| 전체 문서와 영역별 구현 상태 | [OpenMD 문서 지도](./docs/README.md) | 계속 갱신 |

<br>

## 🛠️ 개발 도구

<details>
<summary>🤖 Codex MCP와 디자인 연동 설정</summary>

<br>

MCP 설정은 trusted repository에서 읽히는 프로젝트 로컬 [`.codex/config.toml`](./.codex/config.toml)에 저장했습니다. credential이나 OAuth token은 저장소에 들어가지 않습니다. 설정 추가 후에는 Codex 앱을 재시작하거나 새 CLI session을 여세요.

```bash
codex mcp list
codex mcp get seed-docs
codex mcp get figma
```

### SEED Docs MCP

`seed-docs`는 공식 `@seed-design/docs-mcp`를 `npx`로 실행합니다. 최초 실행에는 npm registry 네트워크가 필요합니다. 현재 package의 MCP tools로 다음 범위를 확인했습니다.

- React component 문서, 사용 예시, 주요 props
- component design guideline
- `get_rootage`를 통한 color, typography, spacing token과 component spec
- icon 검색과 React import 정보

예시 질문:

```text
SEED Design에서 버튼을 구현할 때 사용할 React component와 주요 props를 찾아줘.
```

검증 시 일반 버튼은 `ActionButton`이며 `variant`, `size`, `layout`, `loading`, `disabled` 등의 사용법을 조회할 수 있었습니다. 현재 `@seed-design/docs-mcp@0.7.0`에서는 일부 오래된 문서에 적힌 도구명과 실제 도구명이 다르므로, 연결 후 `discover_seed_docs` 또는 MCP `tools/list` 결과를 기준으로 사용하세요. Foundation 목록이 비어 나오는 경우 token은 `get_rootage`로 조회합니다.

### Figma Remote MCP

Figma official Remote MCP endpoint가 등록되어 있습니다. 사용자가 직접 OAuth를 완료해야 합니다.

```bash
cd /path/to/openmd
codex mcp login figma
```

브라우저에서 Figma 로그인과 `Allow access`를 완료한 뒤 Codex를 재시작하고 `/mcp` 또는 `codex mcp list`로 확인합니다. 가능하면 Figma의 `whoami` tool로 인증 계정도 확인하세요.

현재 repository에서는 endpoint/config 인식까지 확인했습니다. OAuth 인증과 접근 권한이 있는 Frame URL이 제공되지 않아 실제 Frame 조회는 아직 `BLOCKED`입니다.

목표 workflow는 다음과 같습니다.

```text
Figma Frame URL
  -> Figma MCP로 layout, component, variable, screenshot 조회
  -> SEED Docs MCP로 대응 component, props, token, icon 조회
  -> 대응 관계 검토
  -> React 구현 및 build 검증
```

Figma가 반환한 React/CSS를 그대로 복사하지 않고, SEED component와 token으로 매핑합니다. 인증 후 다음 prompt로 연결을 검증할 수 있습니다.

```text
이 Figma Frame URL의 exact node를 get_design_context,
get_variable_defs, get_screenshot으로 읽고,
SEED Docs MCP에서 대응되는 React component, 주요 props,
color/typography/spacing token을 찾아 대응표로 정리해줘.
Figma 출력의 CSS를 그대로 복사하지 말고 아직 UI는 구현하지 마.

<FRAME_URL>
```

공식 문서: [Codex MCP](https://developers.openai.com/codex/mcp/), [SEED Vite setup](https://seed-design.io/react/getting-started/installation/vite), [SEED Docs MCP](https://seed-design.io/ai-integration/docs-mcp), [Figma Remote MCP](https://developers.figma.com/docs/figma-mcp-server/remote-server-installation/).

</details>

<br>

## ✅ 검증

루트 검증 하네스는 웹 정적 검증과 서버 테스트를 같은 명령으로 실행합니다.

```bash
./scripts/verify.sh fast  # 웹 typecheck·lint·build + Testcontainers 제외 서버 테스트
./scripts/verify.sh all   # fast 검증 + Testcontainers 통합 테스트
```

애플리케이션별로 확인할 때는 다음 명령을 사용합니다.

| 대상 | 명령 |
| --- | --- |
| Web | `pnpm -C web verify` |
| Server fast | `./server/gradlew -p server fastTest` |
| Server integration | `./server/gradlew -p server integrationTest` |
| App | `pnpm -C app test && pnpm -C app typecheck` |
| Docker Compose | `docker compose config --quiet` |
