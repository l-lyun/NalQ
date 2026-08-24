---
document_type: trd
status: draft
scope: server
---

# [TRD · Server] OpenAPI와 Swagger UI 운영 설계

- 상태: 초안
- 적용 영역: `server/`
- 관련 API 계약: [이메일 기반 자체 인증](../../../docs/contracts/contract-api-authentication.md)
- 관련 데이터 계약: [사용자·인증 데이터](../../../docs/contracts/contract-data-authentication.md)
- 관련 서버 설계: [브라우저 Refresh Token HttpOnly Cookie 전환](trd-browser-refresh-cookie.md)

## 목적

Spring MVC 컨트롤러에서 OpenAPI 설명을 생성하고 Swagger UI로 로컬 API를 탐색할 수 있게 하되, 기존 API 계약의 의미와 인증 보안 경계를 훼손하지 않는 운영 방식을 정한다.

이 문서는 서버 내부의 생성·노출·검증 방법만 책임진다. Refresh Token 수명, 웹·앱별 저장 방식, 쿠키 전환 여부와 같은 제품·공유 계약은 확정하지 않는다.

## 현재 기준

- 서버는 Spring Boot 4.1.0과 `spring-boot-starter-webmvc`를 사용한다.
- Access Token은 `Authorization: Bearer <access token>`으로 전달하는 5분 수명의 JWT다.
- Refresh Token은 별도 인증 헤더나 쿠키가 아니라 로그인 응답 및 갱신·로그아웃 JSON body로 전달한다.
- `/api/v1/auth/**`는 공개이고, 나머지 요청은 기본적으로 Access Token 인증이 필요하다.
- 갱신 성공 시 Access/Refresh Token이 모두 교체된다. 이미 회전한 Refresh Token 재사용은 단순 실패 확인이 아니라 해당 세션 또는 token family 폐기로 이어질 수 있다.
- Refresh Token의 최종 절대 수명과 웹·앱별 전달·보관 정책은 아직 열린 질문이다.

## 핵심 결정 제안

| 항목 | 제안 | 이유 |
| --- | --- | --- |
| 문서 역할 | `docs/contracts/`를 의미의 원장으로 유지하고, 생성 OpenAPI를 실행 가능한 HTTP 계약의 투영으로 사용한다. | 코드 주석이 회전·재사용·복구 정책까지 중복 소유하지 않게 한다. |
| 라이브러리 | `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0`을 정확한 버전으로 고정한다. | springdoc 3.1.0이 Spring Boot 4.1.0으로 갱신된 현재 WebMVC 조합이다. |
| OpenAPI 버전 | 우선 springdoc 기본인 OpenAPI 3.1을 사용한다. | 현재 도구의 기본을 따르되, 클라이언트 생성기를 도입할 때 호환성을 별도 검증한다. |
| 인증 모델 | 전역 기본은 `bearerAuth`, 공개 `/api/v1/auth/**` operation은 `security: []`로 명시한다. | 서버의 기본 거부 정책과 같은 방향이며 공개 API에 Swagger UI가 Bearer를 실어 보내지 않게 한다. |
| Refresh Token | OpenAPI `securitySchemes`로 등록하지 않고 요청·응답 DTO의 민감한 body 필드로 표현한다. | 현재 계약상 Refresh Token은 Bearer나 쿠키가 아니다. |
| UI 자동화 | 로그인·갱신 응답에서 토큰을 자동 추출하는 커스텀 JavaScript를 넣지 않는다. | 회전 시점 불일치와 오래된 토큰 재실행이 세션 폐기로 이어질 수 있다. |
| 운영 노출 | 로컬에서만 기본 활성화하고 운영에서는 API docs와 UI를 모두 비활성화한다. | 문서 UI를 운영 API의 공개 진입점으로 만들 필요가 없다. |
| 검증 | 전체 JSON snapshot보다 경로·상태·schema·security를 검증하는 좁은 계약 테스트를 먼저 둔다. | 생성기의 비본질적 정렬 변화는 피하면서 실제 계약 파손을 잡는다. |

참고: [springdoc 공식 시작 문서](https://springdoc.org/)는 WebMVC UI 3.1.0과 기본 `/v3/api-docs`, `/swagger-ui.html` 경로를 안내하고, [3.1.0 릴리스](https://github.com/springdoc/springdoc-openapi/releases/tag/v3.1.0)는 Spring Boot 4.1.0 갱신을 명시한다.

## 구성 경계

### 중앙 구성

`global/openapi/OpenApiConfiguration` 한 곳에서 다음만 정의한다.

- API 제목, 설명, 서버 애플리케이션 버전
- `bearerAuth`: `type=http`, `scheme=bearer`, `bearerFormat=JWT`
- 문서 대상 `/api/v1/**`
- 전역 Bearer 보안 요구

배포 host나 port는 문서에 하드코딩하지 않는다. 같은 origin에서 제공하는 상대 주소를 기본으로 하고, 프록시가 생기면 전달 헤더 정책과 함께 다룬다.

초기 API 수가 적으므로 인증/사용자용 `GroupedOpenApi`를 나누지 않는다. 실제로 독립 소비자나 공개 범위가 갈릴 때만 그룹을 추가한다.

### 컨트롤러와 DTO annotation

- 컨트롤러에는 `@Tag`, 안정적인 `operationId`, 짧은 summary, 실제 HTTP 성공·오류 상태만 둔다.
- 공개 인증 operation에는 빈 security 요구를 명시한다.
- 보호 operation에는 전역 `bearerAuth`가 적용되는지 생성 결과로 검증한다.
- Bean Validation으로 이미 표현되는 길이·필수 제약은 `@Schema`에 중복 작성하지 않는다.
- `@Schema`는 필드의 의미, 민감성, 형식과 안전한 예제가 자동 추론되지 않을 때만 사용한다.
- 비밀번호, 인증 코드, Access/Refresh Token의 `default` 값은 두지 않는다. 예제는 `<redacted>` 또는 명백한 placeholder만 사용한다.
- 공통 `{ success, data, error }` envelope와 `ApiResponse<T>`의 구체 응답 schema가 올바르게 풀리는지 테스트한다.

긴 오류 정책, Refresh Token 원자 회전과 클라이언트 직렬화 규칙은 [인증 API 계약](../../../docs/contracts/contract-api-authentication.md)이 계속 소유한다. OpenAPI 설명에는 해당 동작을 안전하게 사용하는 데 필요한 짧은 경고와 안정 오류 코드만 둔다.

## 인증 API의 Swagger UI 사용 흐름

1. 테스트 계정으로 `POST /api/v1/auth/sessions`를 실행한다.
2. 응답의 `data.accessToken`만 Swagger UI의 `Authorize`에 넣는다.
3. `GET /api/v1/users/me` 같은 보호 API를 호출한다.
4. `POST /api/v1/auth/sessions/refresh`에는 최신 `data.refreshToken`을 JSON body로 직접 넣는다.
5. 갱신 성공 후 `Authorize`의 Access Token과 다음 요청 body의 Refresh Token을 모두 새 값으로 교체한다.
6. `DELETE /api/v1/auth/sessions/current`에도 현재 Refresh Token을 body로 전달한다.

Swagger UI 설명에는 다음 경고를 표시한다.

- 이전 Refresh Token을 다시 실행하면 세션 또는 token family가 폐기될 수 있다.
- 공유·운영 계정이 아니라 테스트 계정만 사용한다.
- 토큰을 URL, query, 정적 예제, 문서 설정에 넣지 않는다.
- `persistAuthorization`은 `false`로 유지한다.

현재 UI는 서버와 같은 origin에서 제공되므로 Swagger UI 자체를 위해 CORS origin을 추가하지 않는다. Refresh Token을 HttpOnly Cookie로 전환할 때는 [브라우저 Cookie 서버 설계](trd-browser-refresh-cookie.md)와 API 계약에 따라 cookie security scheme, CSRF와 credentialed CORS를 함께 변경한다.

## 환경별 노출 정책

| 환경 | OpenAPI JSON/YAML | Swagger UI | Try it out |
| --- | --- | --- | --- |
| 로컬 | 활성 | 활성 | 활성 |
| 자동 테스트 | 필요한 테스트에서만 활성 | 비활성 | 비활성 |
| 공유 개발·스테이징 | 필요 시 내부 접근 제어 뒤에서 활성 | 필요 시 내부 접근 제어 뒤에서 활성 | 테스트 계정과 테스트 외부 연동에서만 활성 |
| 운영 | 비활성 | 비활성 | 비활성 |

권장 설정 경계는 다음과 같다.

```properties
springdoc.api-docs.enabled=${OPENMD_OPENAPI_ENABLED:false}
springdoc.swagger-ui.enabled=${OPENMD_SWAGGER_UI_ENABLED:false}
springdoc.paths-to-match=/api/v1/**
springdoc.default-consumes-media-type=application/json
springdoc.default-produces-media-type=application/json
springdoc.swagger-ui.persist-authorization=false
springdoc.swagger-ui.disable-swagger-default-url=true
```

로컬 프로필 또는 로컬 환경 변수만 두 기능을 활성화한다. 운영에서 문서가 꼭 필요해지면 같은 애플리케이션 port에 무조건 공개하지 않고 내부 네트워크, 별도 관리 port 또는 인증 프록시 중 배포 구조에 맞는 방법을 먼저 결정한다.

문서 기능이 활성화된 환경에서는 다음 경로를 Spring Security에서 허용해야 한다.

- `/v3/api-docs/**`
- `/v3/api-docs.yaml`
- `/swagger-ui/**`
- `/swagger-ui.html`

`SecurityFilterChain`의 허용 규칙과 `BearerAccessTokenFilter.shouldNotFilter`의 공개 경로 판단을 함께 맞춘다. 그렇지 않으면 `permitAll`이어도 문서 요청에 우연히 포함된 잘못된 Bearer가 커스텀 필터에서 401을 만들 수 있다. 문서가 비활성화된 환경에서는 위 경로에 별도 허용 규칙을 만들지 않는다.

## 계약 검증

OpenAPI 구현은 서버 테스트 우선 정책을 따른다. 먼저 전용 Spring context 테스트를 작성하고 다음 실패를 확인한다.

- 현재 7개 `method + path`가 생성 문서에 존재한다.
- 공개 인증 6개 operation은 무인증이고 `GET /api/v1/users/me`는 `bearerAuth`다.
- 가입은 `202`, 나머지 현재 성공 응답은 실제 컨트롤러 상태와 일치한다.
- 로그인·갱신 응답은 구체화된 `ApiResponse<SessionTokens>` schema를 가진다.
- 갱신·로그아웃은 `RefreshTokenRequest` JSON body를 가지며 header/query token으로 표현되지 않는다.
- 검증 오류와 인증 오류가 공통 error envelope로 표현된다.
- 문서와 예제에 실제 비밀값이나 설정 secret이 포함되지 않는다.
- 문서 활성 환경에서는 docs/UI 경로가 열리고, 비활성 환경에서는 노출되지 않는다.

전체 생성 JSON을 그대로 snapshot으로 고정하지 않는다. 이후 웹·앱이 생성 타입이나 클라이언트를 실제로 소비할 때 다음 단계를 추가한다.

- CI에서 OpenAPI artifact 생성
- 이전 승인 artifact와 breaking change 비교
- 생성 타입 및 클라이언트 컴파일
- Controller method/path와 OpenAPI operation 집합 비교

## 단계별 도입

### 1단계: 로컬 탐색과 최소 계약

- OpenAPI 계약 테스트를 먼저 작성하고 의도한 실패를 확인한다.
- WebMVC UI starter, 중앙 구성과 환경 토글을 추가한다.
- 인증·사용자 API의 operation, schema와 security만 보강한다.
- 문서 경로의 Security 설정과 Bearer 필터 제외 규칙을 함께 추가한다.
- 좁은 테스트와 서버 전체 테스트를 실행한다.

### 2단계: 소비자 계약 강화

- 웹 또는 앱이 실제로 OpenAPI 생성물을 소비하기 시작할 때 operationId 안정성 규칙을 승인한다.
- CI artifact와 breaking change 검사를 도입한다.
- 공통 오류 응답의 재사용 component를 실제 반복 수준에 맞춰 정리한다.

### 3단계: 배포 문서가 필요할 때

- 내부 문서 소비자, 접근 제어와 보존 기간을 먼저 확정한다.
- 필요할 때만 관리 port, 인증 프록시 또는 별도 정적 문서 게시를 선택한다.
- Swagger UI 커스텀 스크립트나 토큰 자동화는 구체적인 반복 비용과 안전한 상태 모델이 확인된 뒤 별도 결정한다.

## 열린 질문

- Refresh Token의 최종 절대 수명은 API 계약에서 아직 확정되지 않았다. 현재 설정 기본값을 OpenAPI의 확정 정책이나 예제로 복제하지 않는다.
- 첫 OpenAPI 소비자가 웹, Expo 앱, 사람용 Swagger UI 중 무엇인지에 따라 2단계의 artifact 위치와 생성 도구가 달라진다.
- 공유 개발·스테이징 환경에서 문서를 제공할 실제 필요와 접근 제어 방식은 배포 설계 시 확정한다.
