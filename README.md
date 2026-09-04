# NalQ

비즈니스 기능 없이 개발 환경만 구성한 monorepo입니다.

```text
openmd/
├── server/  # Java 21, Spring Boot 4, Gradle
├── web/     # React, TypeScript, Vite, pnpm, SEED Design
└── app/     # Expo, React Native, TypeScript, pnpm
```

## Prerequisites

- Java 21
- Node.js 22.12 이상 (`.tool-versions`는 22.22.0)
- pnpm 11.18.0
- Docker Desktop 또는 Docker Engine + Compose
- MCP를 사용할 경우 Codex CLI

## Infrastructure

MySQL과 Redis만 Docker Compose로 실행합니다. 루트 `.env.example`의 값은 로컬 개발용 기본값이며 실제 환경에서는 변경해야 합니다.

```bash
cp .env.example .env
docker compose up -d
docker compose ps
```

데이터는 `mysql-data`, `redis-data` named volume에 보존됩니다.

## Server

주요 의존성은 Spring Web MVC, Validation, Data JPA, Security, Spring AI, MySQL Driver, Data Redis, Lombok, JUnit입니다.

```bash
cd server
./gradlew test
./gradlew bootRun
```

기본 포트는 `8080`입니다. 필요한 변수 목록은 `server/.env.example`을 참고합니다.

실제 퀴즈 생성을 로컬에서 확인할 때는 `OPENAI_API_KEY`와 `OPENMD_QUIZ_GENERATION_ENABLED=true`를 서버 프로세스 환경에 함께 넣습니다. 키는 저장소에 커밋하지 않습니다. 웹과 서버의 기본 허용 주소가 `http://localhost:5173`이므로 브라우저도 같은 주소로 접속합니다.

Windows에서는 `server/.env`에 `OPENAI_API_KEY=...`를 포함한 로컬 설정을 넣고 아래 명령을 실행하면 됩니다. 값은 출력하지 않으며 `server/.env`는 Git에서 제외됩니다.

IntelliJ에서는 `NalQ Server (local)` 실행 구성을 사용합니다. 이 구성은 키를 복사하지 않고 `server/.env`를 실행 시점에 읽습니다. 기본 `ServerApplication` 실행은 dotenv 파일을 자동으로 읽지 않습니다.

```powershell
# 터미널 1: 저장소 루트
.\scripts\dev-server.ps1

# 터미널 2
cd web
pnpm dev
```

브라우저는 반드시 `http://localhost:5173`으로 엽니다. `127.0.0.1`은 기본 CORS 허용 주소와 다릅니다.

```bash
cp .env.example .env
set -a
source .env
set +a
./gradlew bootRun
```

## Web

React Router, TanStack Query, Axios와 SEED Design을 설치했습니다. 아직 route별 화면이나 비즈니스 기능은 없습니다.

```bash
cd web
pnpm install
pnpm dev
pnpm dev:mock
pnpm typecheck
pnpm build
```

`pnpm dev`는 실제 퀴즈 API를 사용하는 기본 개발 서버를 `http://localhost:5173`에 엽니다. 서버 없이 고정 화면만 확인할 때는 `pnpm dev:mock`을 사용합니다. API 주소는 `web/.env.example`의 `VITE_API_BASE_URL`로 변경할 수 있습니다.

SEED는 `@seed-design/react`, `@seed-design/css`, `@seed-design/vite-plugin`으로 구성되어 있습니다. `src/main.tsx`에서 `base.css`를 불러오고 Vite plugin이 theme 및 component recipe CSS를 처리하므로 전역 Theme Provider는 필요하지 않습니다. 향후 SEED CLI component를 추가할 때는 다음처럼 실행합니다.

```bash
pnpm dlx @seed-design/cli@latest add ui:action-button
```

## App

Expo SDK 57 TypeScript 프로젝트이며 `react-native-webview`는 설치만 되어 있습니다. 아직 WebView wrapper나 Native 기능은 구현하지 않았습니다.

```bash
cd app
pnpm install
pnpm start
# 또는 pnpm ios / pnpm android
```

향후 WebView URL은 `app/.env.example`의 `EXPO_PUBLIC_WEB_URL`을 사용합니다.

## Codex MCP

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

## Validation

2026-08-15 기준 결과입니다.

| 대상 | 명령 | 결과 |
| --- | --- | --- |
| Server | `./gradlew test --no-daemon --rerun-tasks` | PASS — context test 1개 |
| Web install | `pnpm install --frozen-lockfile` | PASS |
| Web static checks | `pnpm typecheck`, `pnpm lint` | PASS |
| Web production build | `pnpm build` | PASS — Vite 8.2.1, 589 modules |
| App install | `pnpm install --frozen-lockfile` | PASS |
| Expo compatibility | `pnpm exec expo install --check` | PASS — dependencies up to date |
| App typecheck | `pnpm exec tsc --noEmit` | PASS |
| Infrastructure | `docker compose config --quiet` | PASS |
| MCP config | `codex mcp list` | PASS — `seed-docs`, `figma` enabled |
| SEED Docs MCP | Inspector `tools/list`, ActionButton, Rootage, icon calls | PASS |
| Figma Frame | OAuth + authorized Frame query | BLOCKED — 사용자 인증과 Frame URL 필요 |

재검증 명령은 다음과 같습니다.

```bash
(cd server && ./gradlew test)
(cd web && pnpm install && pnpm typecheck && pnpm build)
(cd app && pnpm install && pnpm exec expo install --check && pnpm exec tsc --noEmit)
docker compose config
codex mcp list
```
