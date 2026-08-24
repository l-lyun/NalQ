# Server Guide

이 지침은 `server/` 전체에 적용된다. 루트 `AGENTS.md`와 함께 따른다.

## 기술과 구조

- Java 21, Spring Boot 4.1, Gradle Wrapper를 사용한다.
- 현재 패키지 루트는 `com.openmd.server`다. 기능을 추가하기 전에 인접 패키지와 기존 전역 응답·오류·엔티티 관례를 확인한다.
- 공개 API를 바꿀 때는 `docs/contracts/`의 관련 계약과 클라이언트 영향을 함께 확인한다.

## 기능 구현은 테스트 우선

서버 프로덕션 코드를 변경하는 모든 기능·버그 수정은 사용자가 따로 요청하지 않아도 다음 순서를 따른다.

1. `docs/README.md`에서 관련 PRD, UX, API Contract를 찾고 인수 조건을 정리한다.
2. 인수 조건을 재현하는 테스트를 먼저 작성하거나 수정한다.
3. 해당 테스트가 의도한 이유로 실패하는지 실행해 확인한다.
4. 테스트를 통과시키는 최소 구현을 작성한다.
5. 좁은 테스트부터 관련 서버 전체 테스트까지 실행한다.

이미 테스트가 통과한다면 현재 구현이 요구사항을 충족하는지 확인하고 불필요한 프로덕션 코드를 추가하지 않는다. 실패 확인을 실행할 수 없으면 이유와 대체 검증을 결과에 명시한다.

## 서버 경계

- 컨트롤러는 HTTP 변환·검증·권한 경계에 집중하고 도메인 규칙을 숨기지 않는다.
- 외부 OpenAI·Notion 연동은 교체 가능한 경계 뒤에 두고, 테스트가 실서비스나 실 API 키에 의존하지 않게 한다.
- 명세와 기존 코드가 충돌하거나 공개 계약의 핵심 결정이 비어 있으면 임의로 고정하지 말고 중단해 보고한다.
- 관련 없는 `web/` 또는 `app/` 파일을 수정하지 않는다.

## 패키지 규칙

- 상세 기준은 [서버 패키지 구조 TRD](docs/trd/trd-package-structure.md)를 따른다.
- 최상위 기능 도메인은 `auth`, `learningmaterial`이고, 각 도메인은 기본적으로 `controller`, `service`, `repository`, 공용 `dto`, `domain`, `error`로 나눈다.
- DTO는 도메인 공용 `dto/request`, `dto/response`, `dto/command`, `dto/model`에 역할별로 두고 Entity·도메인 enum·정책을 DTO로 분류하지 않는다.
- 기본 참조 방향은 `Controller -> Service -> Repository`다. 서비스의 다른 도메인 Repository 직접 호출은 허용하지만 Repository가 Service나 Controller를 참조하면 안 된다.
- 상태나 주입 의존성이 없는 좁은 순수 함수만 `util`에 두고, 서로 다른 책임을 모은 포괄 `Utils` 클래스를 만들지 않는다.
- 새 패키지도 `com.openmd.server` 아래에 두어 component, entity, repository scanning 범위를 유지한다.

## 검증

- Windows: `server/gradlew.bat test`
- macOS/Linux: `server/gradlew test`

변경 보고에는 먼저 작성한 테스트, 실패 확인 결과, 최종 테스트 결과와 남은 위험을 포함한다.
