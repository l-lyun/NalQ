# Repository Guide

This repository is a monorepo with three applications:

- `server/`: Java 21, Spring Boot 4, and Gradle
- `web/`: React, TypeScript, Vite, and pnpm
- `app/`: Expo, React Native, TypeScript, and pnpm

Before making changes, inspect the existing structure and conventions inside the application you are working on.

Keep changes scoped to the relevant application. Do not modify the other applications unless the task explicitly requires it.

After changes, run the relevant build, test, or type-check command for that application.

## Working Model

This repository uses project-scoped specialist agents defined in `.codex/agents/`.
Treat them as focused roles for a small, reviewable unit of work, not as an automatic delivery pipeline.

- Start with one specialist that best matches the user's request.
- Use a second specialist only when the user explicitly asks for delegation or when a small, independent review materially improves confidence.
- Never chain planning, design, publishing, implementation, testing, and review automatically.
- Finish the requested stage, present its artifact or changes, and let the user choose the next stage.
- The primary agent owns task routing, scope control, integration, and the final answer.
- A specialist must not delegate to another specialist unless the user explicitly asks for that delegation.

## Specialist Routing

Choose the specialist from the requested outcome, not merely from words appearing in the prompt.

| Requested outcome | Specialist | Typical output |
| --- | --- | --- |
| Clarify product behavior, requirements, priorities, or acceptance criteria | `product_planner` | Feature, flow, decision, or product document |
| Define screen structure, interaction, UX states, or a design artifact | `seed_ui_designer` | Screen specification, wireframe, prototype, or design artifact |
| Turn an approved UI specification into SEED-based presentation code | `seed_publisher` | Visual components and responsive layout without business integration |
| Connect approved web UI to routing, state, forms, and APIs | `frontend_engineer` | Working `web/` behavior and relevant tests |
| Implement server behavior from an approved feature specification | `backend_engineer` | Test-first `server/` implementation |
| Independently design or strengthen server tests | `backend_test_engineer` | Unit, integration, contract, or regression tests under `server/src/test/` |
| Implement Expo/React Native application behavior | `app_engineer` | Working `app/` behavior and relevant tests or type checks |
| Plan or implement repository infrastructure explicitly requested by the user | `infra_engineer` | Reviewed infrastructure artifacts; no unapproved external changes |
| Check an artifact or implementation against approved requirements | `acceptance_reviewer` | Read-only Korean findings and acceptance result |

If the requested outcome spans multiple stages, complete only the stage the user asked for. For example, “화면 설계해줘” stops after a reviewable design artifact; it does not imply publishing or feature implementation.

## Document Source of Truth

- Start documentation work from `docs/README.md`; it routes agents to the smallest relevant set of documents.
- For work that designs, implements, or reviews user-visible UI, read the root `DESIGN.md` as the source of truth for global visual atmosphere, information hierarchy, layout, and responsive principles.
- Treat feature, flow, and screen specifications as the source of truth for product behavior and states. Treat current SEED Docs and Rootage as the source of truth for exact components, props, and tokens; `DESIGN.md` must not override or freeze outdated SEED APIs.
- If `DESIGN.md`, an approved specification, and current SEED documentation conflict, do not silently choose one. Report the conflict and its impact before changing the agreed behavior or visual direction.
- Product and UX decisions belong in `docs/`, not in chat history alone.
- Distinguish confirmed requirements, assumptions, and open questions. Never silently promote an assumption to a decision.
- Update the relevant source document when an implementation request changes an approved contract or behavior.
- Prefer a focused feature, screen, flow, contract, or decision document over a single growing PRD.

## Backend Test-First Policy

All backend feature implementation is test-first even when the user does not mention tests:

1. Read the relevant feature specification and existing server conventions.
2. Add or update the smallest meaningful test and confirm the intended failure.
3. Implement the minimum production change that satisfies the behavior.
4. Run the focused test, then the relevant broader server test suite.

Do not skip the failure check unless the environment makes it impossible; report that limitation explicitly. The detailed server and test rules are in `server/AGENTS.md` and `server/src/test/AGENTS.md`.

## Context Efficiency

- 작업 시작 시 전체 코드베이스나 파일 전문을 선제적으로 읽지 않는다.
- 먼저 파일명, 디렉터리 구조, 심볼과 참조를 검색해 관련 범위를 좁힌다.
- 필요한 파일의 관련 구간만 읽고, 판단에 근거가 부족할 때만 주변 코드나 연결된 파일로 범위를 확장한다.
- 이미 확인한 내용은 불필요하게 다시 읽지 않는다.
- 코드 리뷰는 변경 diff부터 확인하고, 변경의 영향을 판단하는 데 필요한 코드만 추가로 읽는다.

## Code Review Rules

### Review language and scope

- 모든 리뷰 의견은 한국어로 작성한다.
- P2 또는 P3 수준의 문제도 리뷰어의 판단에 따라 제시할 수 있다.
- P2 또는 P3 수준의 의견에는 `[수정 필요]` 또는 `[수정 불필요]`를 명시하고, 그 판단 근거를 간결하게 설명한다.
- 주요 사용자 흐름의 실패, 빌드 또는 런타임 오류, 데이터 손실이나 오염, 명백한 권한 우회, 애플리케이션 간 계약 파괴처럼 실제 동작에 큰 영향을 주는 문제에 집중한다.
- 코드 스타일, 네이밍, 사소한 리팩터링, 미세 최적화, 일반적인 테스트 보강, 현실적인 악용 가능성이 낮은 심층 보안 강화는 리뷰하지 않는다.
- 문제를 지적할 때는 영향과 재현 조건을 간결하게 설명하고, 실질적인 해결 방향을 함께 제시한다.
- 해당 수준의 문제가 없으면 불필요한 의견을 만들지 않는다.
