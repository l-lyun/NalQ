---
document_type: trd
status: implemented
scope: server
---

# [TRD · Server] 서버 패키지 구조

- 상태: 구현 동기화
- 적용 영역: `server/src/main/java/com/openmd/server`, `server/src/test/java/com/openmd/server`
- 변경 성격: 공개 API와 비즈니스 동작을 바꾸지 않는 내부 패키지 구조

## 1. 문서 책임

이 문서는 서버 기능을 어느 패키지에 배치하고 패키지 사이에서 어떤 참조를 허용할지 정의한다. 제품 정책과 사용자에게 보이는 동작, API·JSON·데이터 의미는 관련 PRD와 Contract가 원장이며 이 문서는 이를 재정의하지 않는다.

## 2. 기본 구조

`com.openmd.server` 아래에서 사용자 기능은 최상위 도메인으로 나누고, 공통 서버 기반만 `global`에 둔다.

```text
com.openmd.server
├── auth
│   ├── controller
│   │   └── support
│   ├── service
│   ├── repository
│   │   └── redis
│   ├── dto
│   │   ├── request
│   │   ├── response
│   │   ├── command
│   │   └── model
│   ├── domain
│   ├── error
│   ├── security
│   ├── config
│   ├── integration
│   │   └── mail
│   └── util
├── learningmaterial
│   ├── controller
│   ├── service
│   ├── repository
│   ├── dto
│   │   ├── request
│   │   ├── response
│   │   ├── command
│   │   └── model
│   ├── domain
│   └── error
├── quiz
│   ├── controller
│   ├── service
│   ├── repository
│   ├── dto
│   │   ├── request
│   │   └── response
│   ├── domain
│   │   ├── entity
│   │   └── type
│   ├── error
│   └── util
└── global
```

실제 타입이 없는 빈 패키지는 미리 만들지 않는다. `global`의 기존 `api`, `config`, `entity`, `error`, `openapi` 구조는 유지한다.

## 3. 패키지 책임

| 패키지 | 책임 |
| --- | --- |
| `controller` | HTTP 요청 수신, 입력 검증, 인증 주체 추출, Request를 서비스 입력으로 변환, 응답 반환 |
| `controller.support` | 쿠키처럼 HTTP 경계에만 필요한 보조 타입 |
| `service` | 유스케이스 흐름, 트랜잭션 조정, 저장소와 외부 경계 조합 |
| `repository` | JPA·Redis 등 영속성 인터페이스와 어댑터. 구현 기술별 하위 패키지를 허용 |
| `dto.request` | HTTP 입력 모델과 요청 검증 |
| `dto.response` | 외부 응답 또는 컨트롤러가 반환하는 서비스 결과 |
| `dto.command` | 컨트롤러 등 호출자가 서비스 작업에 전달하는 입력 |
| `dto.model` | 계층 사이에서 전달하는 내부 데이터 모델. Entity와 정책은 포함하지 않음 |
| `domain` | 값 검증과 비즈니스 정책. 규모가 작은 도메인은 JPA Entity와 도메인 enum도 함께 배치 |
| `domain.entity` | Entity가 많아 한 패키지의 탐색성이 낮아진 도메인의 JPA Entity |
| `domain.type` | 위와 같은 도메인의 Entity 상태·종류를 표현하는 enum |
| `error` | 도메인별 공개·내부 오류 코드 |
| `security` | 토큰, 인증 주체, 인증 필터, 보안 난수·digest 같은 보안 기능 |
| `config` | Spring Bean과 보안 설정 조립 |
| `integration` | 메일처럼 외부 시스템을 호출하는 어댑터 |
| `util` | 상태와 주입 의존성이 없는 좁은 순수 변환 함수 |

DTO는 계층마다 별도 `dto`를 만들지 않고 도메인 공용 `dto` 아래에서 역할로 분류한다. 모든 DTO 이름에 `Dto` 접미사를 강제하지 않으며 기존 `Request`, `Response`, `Command`와 의미 있는 모델 이름을 유지한다.

`domain.entity`와 `domain.type`은 모든 도메인에 강제하지 않는다. Entity와 enum이 적어 한눈에 탐색되는 동안에는 `domain`에 함께 두고, 영속 모델이 밀집되어 정책·타입과의 구분이 어려울 때만 선택적으로 분리한다. 값 객체가 실제로 생기기 전에는 빈 `domain.vo` 패키지를 만들지 않는다. 정책은 Entity 수와 관계없이 `domain`에 유지해 도메인 동작의 진입점을 드러낸다.

## 4. 참조 규칙

- 기본 호출 방향은 `Controller -> Service -> Repository`다.
- 현재 단계에서는 서비스가 다른 도메인의 Repository를 직접 호출할 수 있다. 예를 들어 학습자료 서비스가 `auth.repository.UserRepository`로 소유자를 조회하는 것은 허용한다.
- Repository는 Service나 Controller를 참조하지 않는다.
- Controller가 영속성 구현을 직접 호출하지 않는다.
- JPA Entity, 도메인 enum과 정책은 DTO로 이동하지 않는다.
- 모든 패키지는 `com.openmd.server` 아래를 유지해 Spring component, entity, repository scanning 범위를 보존한다.
- 여러 기능을 한곳에 모은 `AuthUtils` 같은 포괄 유틸리티는 만들지 않는다.

이 규칙은 엄격한 도메인 격리나 포트·어댑터 계층을 요구하지 않는다. 경계가 실제로 복잡해질 때 별도 추상화를 검토하고, 단순한 교차 조회를 위해 중간 서비스를 만들지 않는다.

## 5. 인증 도메인 분류

### 주요 타입

- `controller`: `AuthController`, `BrowserAuthController`, `UserController`
- `controller.support`: `BrowserRefreshCookie`
- `service`: `AuthService`, `RefreshTokenService`, `TwoStepSignUpService`, `VerificationEmailSender`
- `repository`: `UserRepository`, `EmailVerificationStore`, `RefreshSessionStore`, `SignUpCredentialStore`
- `repository.redis`: Redis Store 구현체
- `dto.request`: HTTP 요청 record
- `dto.response`: `BrowserSessionTokens`, `CurrentUser`, `SessionTokens`, 이메일·닉네임 관련 응답 record
- `dto.command`: `SignUpCommand`
- `dto.model`: 가입 자격, 약관, Refresh Token 발급·회전·세션 내부 record
- `domain`: `User`, `UserStatus`, `PasswordPolicy`
- `error`: `AuthErrorCode`, `BrowserAuthErrorCode`
- `integration.mail`: `SpringMailVerificationEmailSender`

`VerificationEmailSender`는 서비스가 의존하는 외부 발송 경계이므로 `service`에 두고, Spring Mail 구현만 `integration.mail`에 둔다. 별도 `port` 계층을 추가하지 않아도 의존 방향은 서비스 경계에서 외부 구현으로 유지된다.

### 작은 보조 기능 판단

- `VerificationCodeGenerator`는 `SecureRandom`을 주입받고 서비스 테스트가 생성 결과를 통제하는 mock 경계다. 단순 함수로 합치지 않고 클래스를 유지해 `auth.security`에 둔다.
- `VerificationCodeDigest`와 `SignUpTokenDigest`는 보안 digest 책임이므로 `auth.security`에 둔다.
- `EmailNormalizer`만 상태와 주입 의존성이 없는 순수 static 변환이므로 `auth.util`에 둔다.
- `PasswordPolicy`는 범용 유틸리티가 아니라 인증 도메인 정책이므로 `auth.domain`에 둔다.

## 6. 학습자료 도메인 분류

- `controller`: `LearningMaterialController`
- `service`: `LearningMaterialService`
- `repository`: `LearningMaterialCreationStore`, `JpaLearningMaterialCreationStore`, `LearningMaterialInsertTransaction`, `LearningMaterialRepository`, `StoredLearningMaterialMapper`
- `dto.request`: `CreateLearningMaterialRequest`
- `dto.command`: `CreateLearningMaterialCommand`
- `dto.response`: `CreatedLearningMaterial`
- `dto.model`: `NewLearningMaterial`, `StoredLearningMaterial`
- `domain`: `LearningMaterial`, `SourceType`, `ContentEditStatus`
- `error`: `LearningMaterialErrorCode`

`StoredLearningMaterialMapper`는 package-private로 유지하고 함께 사용하는 저장소 구현과 같은 `repository` 패키지에 둔다.

## 7. 테스트와 마이그레이션

- 테스트 package와 경로는 검증 대상의 새 package를 따른다.
- package-private 접근이 필요한 테스트는 대상과 같은 package에 둔다.
- Spring 테스트의 `@Import`, `@MockitoBean`, 중첩 `@SpringBootConfiguration`과 OpenAPI schema FQCN도 새 package에 맞춘다.
- Flyway 자체 동작을 검증하는 `PendingActivationCleanupMigrationTest`는 `auth.migration`에 둘 수 있다.
- Flyway SQL 파일명, 순서와 내용은 이 리팩터링에서 변경하지 않는다.

### 퀴즈 도메인 적용

퀴즈는 채점·UUID 중복 제약·복습 snapshot을 포함해 영속 Entity와 상태 enum이 밀집되어 있으므로 선택적 하위 패키지를 적용한다.

- `quiz.domain.entity`: `QuizSet`, `QuizQuestion`, `ShortAnswerAcceptedAnswer`, `QuizAttempt`, `QuizQuestionResult`, `ReviewSession`, `ReviewSessionQuestion`
- `quiz.domain.type`: `GradingOutcome`, `QuestionType`, `QuizSetStatus`, `QuizAttemptStatus`, `ReviewSessionStatus`, `ReviewQuestionStatus`
- `quiz.domain`: 순수 채점 정책 `ShortAnswerGrader`

이 분리는 Java 내부 탐색 구조만 바꾸며 Entity 이름, JPA 테이블 매핑, enum 저장 문자열과 공개 응답 직렬화는 유지한다.

## 8. 호환성과 검증 기준

이 구조 변경은 클래스명, HTTP method·path·status, JSON 필드, DB schema, Flyway migration, Redis key와 TTL, 비즈니스 규칙을 변경하지 않는다.

검증은 다음 순서로 수행한다.

1. 이동 전 서버 전체 테스트를 기준선으로 실행한다.
2. 문서와 패키지 규칙을 먼저 작성한다.
3. 프로덕션 코드와 테스트를 같은 구조로 이동한다.
4. 컴파일과 변경 범위의 집중 테스트를 실행한다.
5. `./gradlew test --rerun-tasks --no-daemon --max-workers=1` 전체 테스트와 `git diff --check`를 실행한다.

구조 리팩터링은 새 사용자 동작이 아니므로 실패하는 기능 테스트를 인위적으로 추가하지 않는다. 이동 전 통과한 전체 테스트를 회귀 안전망으로 사용하고 이동 뒤 동일 테스트 수와 결과를 확인한다.

## 9. 변경 이력

| 날짜 | 변경 | 결정자 |
| --- | --- | --- |
| 2026-08-24 | 밀집된 도메인의 선택적 `entity`·`type` 하위 패키지 기준과 퀴즈 적용 확정 | 사용자 확정 |
| 2026-08-23 | 도메인 공용 DTO, 실용적 Controller-Service-Repository 구조와 보조 기능 분류 확정 | 사용자 확정 |
