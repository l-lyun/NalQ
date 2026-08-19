# Repository Guide

This repository is a monorepo with three applications:

- `server/`: Java 21, Spring Boot 4, and Gradle
- `web/`: React, TypeScript, Vite, and pnpm
- `app/`: Expo, React Native, TypeScript, and pnpm

Before making changes, inspect the existing structure and conventions inside the application you are working on.

Keep changes scoped to the relevant application. Do not modify the other applications unless the task explicitly requires it.

After changes, run the relevant build, test, or type-check command for that application.

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
