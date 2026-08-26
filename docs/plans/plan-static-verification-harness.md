---
document_type: execution-plan
status: review
scope: repository-verification
---

# [Plan] 정적 검증 하네스 도입

이 문서는 사람과 에이전트가 만든 변경을 PR 병합 전에 같은 명령으로 검증하기 위한 저장소 하네스의 원장이다. 1단계의 실제 명령과 CI 구조, 이후 도입할 2단계 웹 행동 테스트와 3단계 계약·아키텍처 검증의 순서를 함께 관리한다.

## 목표와 비범위

### 목표

- 로컬, 전문 에이전트, GitHub Actions가 같은 검증 명령을 사용한다.
- 타입·컴파일·린트·빌드 오류와 기존 서버 테스트 회귀를 PR에서 자동으로 차단한다.
- Docker가 필요 없는 서버 테스트와 Testcontainers 통합 테스트를 분리해 실패 원인을 구분한다.
- 이후 웹 행동 테스트와 애플리케이션 간 계약 검사를 작은 단계로 추가한다.

### 비범위

- CI 통과를 제품 요구사항 충족이나 사람의 최종 리뷰로 간주하지 않는다.
- 이 단계에서 Vitest, React Testing Library, MSW를 설치하거나 웹 행동 테스트를 추가하지 않는다.
- 이 단계에서 OpenAPI breaking-change 검사, ArchUnit, 웹 import 규칙, coverage 기준을 강제하지 않는다.
- 실제 배포, GitHub Ruleset 변경, 외부 리소스 생성과 운영 비밀 주입은 수행하지 않는다.

## 전체 구조

```text
로컬·에이전트
  └─ scripts/verify.sh
       ├─ web: typecheck + lint + build
       ├─ server-fast: Testcontainers 제외
       └─ server-integration: Testcontainers만 실행

GitHub pull request / dev·main push
  ├─ web-static
  ├─ server-fast
  ├─ server-integration
  └─ harness-required: 위 세 작업이 모두 성공해야 성공
```

## 1단계: 기본 검증 하네스

상태: 구현됨

### 로컬 명령

| 목적 | 명령 | 실행 내용 |
| --- | --- | --- |
| 빠른 기본 검증 | `./scripts/verify.sh fast` | 웹 정적 검증 + Docker 없는 서버 테스트 |
| 배포 전 전체 검증 | `./scripts/verify.sh all` | 웹 정적 검증 + 서버 fast + Testcontainers 통합 테스트 |
| 웹만 검증 | `./scripts/verify.sh web` | `pnpm typecheck`, `pnpm lint`, `pnpm build` |
| 서버 빠른 테스트 | `./scripts/verify.sh server-fast` | `./gradlew fastTest` |
| 서버 통합 테스트 | `./scripts/verify.sh server-integration` | `./gradlew integrationTest` |

`web/package.json`의 `pnpm verify`가 웹 정적 검증의 단일 진입점이다. TypeScript에는 `strict: true`를 적용한다.

서버 테스트 분리 기준은 JUnit 태그다.

- `fastTest`: `integration` 태그를 제외한다.
- `integrationTest`: `integration` 태그만 실행한다.
- MySQL·Redis 등 Testcontainers를 사용하는 테스트에는 반드시 `@Tag("integration")`을 붙인다.
- 기본 `test` task는 전체 테스트를 실행하는 기존 의미를 유지한다.

### GitHub Actions

`.github/workflows/verify.yml`은 모든 PR과 `dev`, `main` push에서 실행한다. 경로 필터를 두지 않아 문서나 공유 경계 변경에서도 required check 이름이 사라지지 않게 한다.

| Job | 차단하는 대표 문제 |
| --- | --- |
| `web-static` | TypeScript 타입 오류, lint 위반, Vite 배포 번들 생성 실패 |
| `server-fast` | Java 컴파일 실패, 단위·MVC·보안·계약 테스트 회귀 |
| `server-integration` | MySQL migration·제약·쿼리, Redis 연결·직렬화 등 실제 의존성 회귀 |
| `harness-required` | 선행 job 실패·취소·건너뜀을 하나의 안정적인 필수 체크로 집계 |

워크플로는 `contents: read` 최소 권한만 사용하고 PR에 운영 비밀을 주입하지 않는다. 중복 실행을 취소하며, 서버 테스트가 실패하면 HTML report를 7일간 artifact로 보존한다.

첫 workflow를 추가하는 bootstrap PR에서는 대상 브랜치에 아직 workflow가 없어 `pull_request` 실행이 생성되지 않을 수 있다. 이 경우 PR 코드에 쓰기 권한이나 비밀을 주는 `pull_request_target`으로 우회하지 않는다. 로컬 `./scripts/verify.sh all`을 완료한 뒤 병합하고, `dev` push 실행을 확인한 다음 후속 PR부터 `pull_request` 검증을 required check로 사용한다.

### CI가 아직 잡지 못하는 문제

- 타입과 빌드는 맞지만 로그인, refresh, logout 등의 사용자 행동이 잘못된 경우
- 서버와 웹이 같은 API 응답 구조를 서로 다르게 가정한 경우
- 패키지 계층이나 웹 import 방향이 깨졌지만 컴파일 가능한 경우
- 요구사항 자체를 잘못 해석했거나 테스트와 구현을 함께 잘못 작성한 경우
- UX, 문구, 시각 품질과 실제 브라우저 전체 흐름

이 공백은 2·3단계 자동 검증과 사람의 리뷰를 함께 사용해 줄인다.

## 2단계: 웹 행동 테스트

상태: 제안 — 별도 PR과 사용자 검토 필요

### 도구 후보

- Vitest: 테스트 실행기
- React Testing Library와 `user-event`: 사용자가 보는 DOM과 실제 입력 중심의 컴포넌트 테스트
- MSW: Axios 함수를 직접 mock하지 않고 HTTP 경계에서 결정적인 API 성공·오류 응답 제공

### 적용 순서

1. 테스트 환경, 공통 render helper, MSW server lifecycle을 설정한다.
2. 현재 [웹 인증 TRD](../../web/docs/trd/trd-authentication.md)와 [인증 API 계약](../contracts/contract-api-authentication.md)을 기준으로 fixture를 만든다.
3. API 오류 코드 매핑과 인증 bootstrap의 가장 작은 테스트부터 추가한다.
4. refresh 성공·실패, 동시 401에서 refresh 단일화, logout 후 인증·개인 캐시 제거를 검증한다.
5. 로그인 중복 제출 방지와 인증 route gate를 검증한다.
6. `pnpm verify`와 CI의 `web-test` job에 `vitest run`을 연결하고 `harness-required`가 이를 요구하게 한다.

MSW fixture는 임의 응답을 새 계약처럼 만들지 않는다. 계약과 실제 서버가 충돌하면 테스트로 한쪽을 고정하지 않고 관련 Contract 결정을 먼저 요청한다.

### 2단계 완료 기준

- 테스트가 외부 서버·DB·실제 토큰 없이 반복 실행된다.
- 정상 흐름뿐 아니라 401, 500, 네트워크 실패와 중복 요청 시나리오가 포함된다.
- 테스트를 의도적으로 깨뜨렸을 때 로컬과 CI가 같은 이유로 실패한다.
- `web-test`가 `harness-required`의 필수 선행 job이 된다.

## 3단계: API 계약과 아키텍처 검증

상태: 제안 — 도구와 기준 합의 후 별도 PR 필요

### 3A. API 계약 검증

1. 서버가 만드는 OpenAPI 산출 방식과 기준 파일의 소유 위치를 확정한다.
2. PR에서 기준 OpenAPI와 현재 OpenAPI를 비교해 endpoint, 필수 필드, 타입과 상태 코드의 breaking change를 탐지한다.
3. 웹이 소비할 타입 생성 또는 런타임 decoder 경계를 정한다.
4. 서버 계약 검사와 웹 typecheck를 CI에 연결한다.

기준 OpenAPI를 저장소에 커밋할지 CI artifact로 생성할지, breaking change를 어떤 브랜치 기준으로 비교할지는 이 단계 시작 전에 결정한다. 관련 서버 방향은 [OpenAPI 운영 TRD](../../server/docs/trd/trd-openapi-documentation.md)를 함께 확인한다.

### 3B. 아키텍처 검증

서버는 ArchUnit 후보로 다음 규칙부터 검토한다.

- Repository가 Service나 Controller를 참조하지 않는다.
- Controller가 Repository 구현을 직접 참조하지 않는다.
- 프로덕션 패키지는 `com.openmd.server` 아래에 둔다.
- 패키지 순환 참조를 허용하지 않는다.

웹은 lint import restriction 또는 dependency-cruiser 후보로 다음 규칙부터 검토한다.

- `shared`는 `features`, `pages`, `app`을 참조하지 않는다.
- `features`는 `pages`, `app`을 참조하지 않는다.
- 순환 import를 허용하지 않는다.

현재 코드가 새 규칙을 모두 만족하는지 먼저 report-only로 측정하고, 기존 위반을 기준선으로 숨기는 대신 작은 수정 PR로 해소한 뒤 필수 게이트로 전환한다.

### 3단계 완료 기준

- 의도적인 API breaking change가 계약 job을 실패시킨다.
- 서버와 웹의 아키텍처 위반 예제가 각각 로컬과 CI에서 실패한다.
- 규칙의 소유 문서와 예외 승인 방식이 명시돼 있다.
- 새 job이 안정화된 뒤 `harness-required`에 편입된다.

## 이후 후보

2·3단계가 안정화된 후에만 다음을 검토한다.

- JaCoCo와 Vitest coverage를 report-only로 측정한 뒤 변경 라인 중심으로 점진적 기준 적용
- PIT 또는 프런트 mutation test를 야간·수동 job으로 시작
- dependency review, CodeQL, 비밀 패턴 검사
- 검증된 동일 commit SHA의 artifact만 배포하는 배포 경계

Coverage 수치만 높이는 테스트나 mutation 비용 때문에 PR 피드백이 지나치게 느려지는 구성을 처음부터 required check로 두지 않는다.

## 결과 보고 규칙

에이전트와 개발자는 실행한 명령을 생략하지 않고 아래 상태 중 하나로 보고한다.

- `PASS`: 명시한 명령이 모두 성공했다.
- `BLOCKED`: Docker, 의존성 설치, 권한 등 실행 조건이 없어 검증하지 못했다.
- `PRE-EXISTING FAILURE`: 현재 변경과 무관한 기존 실패임을 재현 근거와 함께 확인했다.

실행하지 않은 검사는 `PASS`로 표현하지 않는다.

## 저장소 밖에서 필요한 설정

워크플로 파일만으로는 Merge 버튼을 막을 수 없다. 저장소 관리자 권한으로 `dev`와 `main` Ruleset에 다음 설정이 필요하다.

- pull request를 통한 변경 요구
- required status check로 `harness-required` 지정
- 필수 체크를 건너뛰는 관리자 우회 허용 여부 결정
- 필요하면 PR 최신화 요구와 승인 수 결정

이 설정은 외부 GitHub 상태 변경이므로 별도 승인 후 적용하고, 적용 뒤 실제 PR에서 실패·성공 시나리오를 확인한다.

## 롤백

- CI 자체 오류로 모든 PR이 막히면 먼저 워크플로 수정 PR로 원인을 고친다.
- 긴급하게 게이트를 해제해야 하면 저장소 관리자가 Ruleset의 `harness-required` 요구를 일시 제거할 수 있다. 복구 후 같은 체크를 다시 등록하고 우회 기간의 병합 commit을 `./scripts/verify.sh all`로 재검증한다.
- 저장소 구현을 되돌릴 때는 이 계획과 함께 `.github/workflows/verify.yml`, `scripts/verify.sh`, Gradle test task, 웹 `verify` script와 `strict` 설정을 한 단위로 검토한다. 테스트의 `integration` 태그만 제거하면 fast job에서 Docker 테스트가 다시 실행되므로 부분 롤백하지 않는다.

## 열린 결정

- GitHub Ruleset에서 관리자 우회를 허용할지 여부
- 2단계 첫 테스트 범위를 인증 전체로 할지 bootstrap·refresh 경계로 제한할지 여부
- 3단계 OpenAPI 기준 파일의 저장 위치와 breaking-change 비교 기준 브랜치
- coverage·mutation·보안 검사 도입 시점과 허용 실행 시간
