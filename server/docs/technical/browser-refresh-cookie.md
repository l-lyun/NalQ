# 브라우저 Refresh Token HttpOnly Cookie 전환 설계

- 상태: 구현됨 (웹 클라이언트 전환 전)
- 적용 영역: `server/`
- 관련 API 계약: [인증 API](../../../docs/contracts/contract-api-authentication.md)
- 관련 데이터 계약: [사용자·인증 데이터](../../../docs/contracts/contract-data-authentication.md)
- 관련 웹 TRD: [웹 인증 상태와 토큰 관리](../../../web/docs/technical/authentication.md)

## 1. 목적

기존 Redis 기반 Refresh Token Rotation(RTR), 절대 만료와 재사용 탐지는 유지하면서 브라우저 웹에 Refresh Token 원문을 노출하지 않는 HTTP 경계를 설계한다.

이 문서는 서버 내부의 컨트롤러, Cookie 발급·삭제, CORS·CSRF, 오류 변환, Redis 연동과 검증 방법을 책임진다. 사용자에게 보이는 흐름은 기능명세와 인증 흐름이, 웹·앱이 공유할 요청·응답 의미는 인증 API 계약이 책임진다.

## 2. 현재 기준

- 로그인은 `SessionTokens`에 Access Token과 Refresh Token을 함께 담아 JSON body로 반환한다.
- refresh와 logout은 `RefreshTokenRequest` JSON body에서 Refresh Token을 읽는다.
- `AuthService`는 로그인에서 `RefreshTokenService.issue`, 갱신에서 `rotate`, 로그아웃에서 `revoke`를 호출한다.
- Redis는 현재 digest와 사용된 digest tombstone을 저장한다. 같은 토큰의 재사용을 발견하면 해당 session/family를 폐기한다.
- Access Token은 5분 수명의 Bearer Token이며 브라우저 런타임 메모리에만 보관하는 것이 목표다.
- CORS는 `allowCredentials(false)`이고 `/api/v1/auth/**` 전체가 CSRF 검사에서 제외돼 있다.

현재 RTR 저장 모델은 Cookie 전환 때문에 바꾸지 않는다. 우선 변경 대상은 Refresh Token을 HTTP 요청·응답으로 운반하는 API 계층이다.

## 3. 목표와 비범위

### 목표

- 브라우저 JavaScript와 JSON 응답에서 Refresh Token 원문을 제거한다.
- 발급·갱신·삭제 Cookie의 속성을 한 구성 경계에서 동일하게 만든다.
- 기존 RTR의 절대 만료, 원자 회전과 재사용 탐지를 유지한다.
- 기존 앱/네이티브 body 계약과 브라우저 cookie 계약을 명시적으로 분리한다.
- Cookie가 자동 첨부되는 요청에 정확한 CORS와 CSRF 방어를 적용한다.
- 네트워크·Redis 5xx를 자격 없음으로 오판해 유효할 수 있는 Cookie를 삭제하지 않는다.

### 비범위

- Access Token을 Cookie로 옮기거나 서버 HTTP Session으로 전환
- Redis 세션 형식과 Refresh Token 원문 비저장 원칙의 전면 변경
- 모든 기기 로그아웃과 Access Token 즉시 denylist
- 네이티브 앱의 OS 보안 저장소 구현
- 다중 탭 경합을 완화할 grace 정책의 승인

## 4. API surface 분리

브라우저와 네이티브가 같은 endpoint에서 body/Cookie 중 하나를 암묵적으로 선택하게 하지 않는다. User-Agent나 임의 header로 소비자를 추론하지 않는다.

### 기존 body surface

다음 endpoint는 앱과 마이그레이션 중인 소비자를 위해 현재 계약을 유지한다.

- `POST /api/v1/auth/sessions`
- `POST /api/v1/auth/sessions/refresh`
- `DELETE /api/v1/auth/sessions/current`

기존 응답은 `SessionTokens`를 사용하고 Refresh Token을 Cookie 인증 근거로 읽지 않는다.

### 브라우저 cookie surface

- `POST /api/v1/auth/web/sessions`
- `POST /api/v1/auth/web/sessions/refresh`
- `DELETE /api/v1/auth/web/sessions/current`

브라우저 로그인·refresh 성공 body는 다음 전용 DTO를 사용한다.

```text
BrowserSessionTokens
  accessToken
  accessExpiresAt
  refreshExpiresAt
```

Refresh Token 원문 필드는 두지 않고 `refreshExpiresAt`은 세션 만료 안내를 위해 유지한다.

브라우저 refresh/logout은 body 계약을 제공하지 않는다. Cookie가 없을 때 기존 body를 fallback으로 읽지 않는다.

## 5. 서버 책임 분리

```text
BrowserAuthController
  ├─ BrowserRefreshCookie
  │    ├─ request Cookie 추출
  │    ├─ 발급/회전 Set-Cookie 생성
  │    └─ 만료 Set-Cookie 생성
  ├─ AuthService
  │    ├─ login
  │    ├─ refresh
  │    └─ logout
  └─ BrowserSessionResponseMapper
       └─ SessionTokens에서 Refresh Token 원문 제거

AuthService
  └─ RefreshTokenService
       └─ RefreshSessionStore
            └─ RedisRefreshSessionStore
```

- `AuthService`, `RefreshTokenService`와 `RedisRefreshSessionStore`는 토큰 전달 방식을 알지 않는다.
- 브라우저 컨트롤러는 application service가 반환한 `SessionTokens`를 즉시 Cookie와 공개 body로 분리한다.
- Cookie 문자열 조립과 속성은 `BrowserRefreshCookie` 같은 단일 컴포넌트가 소유한다.
- 발급·회전·삭제가 서로 다른 Cookie 이름, Domain 또는 Path를 만들지 않도록 같은 설정 객체를 사용한다.
- 민감 DTO의 기본 `toString`, 예외 메시지와 구조화 로그에 토큰이 들어가지 않게 한다.

## 6. Cookie 정책

### 운영·공유 환경

```http
Set-Cookie: __Host-openmd_refresh=<opaque>; Path=/; Max-Age=<remaining-seconds>; Secure; HttpOnly; SameSite=Lax
```

| 속성 | 제안 |
| --- | --- |
| 이름 | `__Host-openmd_refresh` |
| `HttpOnly` | 항상 `true` |
| `Secure` | 운영·공유 환경에서 항상 `true` |
| `Domain` | 설정하지 않음 |
| `Path` | `__Host-` 규칙에 맞춰 `/` |
| `SameSite` | same-site 배포면 `Lax`, cross-site가 불가피하면 `None; Secure` |
| `Max-Age` | Redis session의 남은 절대 수명 |
| `Expires` | 현재 구현에서는 생략하고 `Max-Age`를 기준으로 만료를 관리 |

- 회전할 때 Cookie 수명을 새 30일로 재설정하지 않는다. 최초 세션의 Redis `absoluteExpiresAt`을 넘지 않는다.
- `Domain`을 생략해 API host 전용으로 제한한다.
- `Path`는 Cookie 기밀성 경계가 아니므로 서버는 모든 요청에서 Cookie 원문을 로깅하지 않아야 한다.
- 운영 topology가 same-site가 아니면 `SameSite=None`만 바꾸는 것으로 끝내지 않고 CSRF 정책을 함께 강화한다.

### 로컬 개발

HTTP `localhost`를 유지하면 운영 prefix와 분리된 `openmd_refresh` 이름과 `Secure=false`를 개발 환경에서만 허용한다. 프론트와 API는 `localhost`와 `127.0.0.1`을 섞지 않는다.

운영과 같은 Cookie 동작을 검증해야 하는 통합 환경은 HTTPS를 사용하고 운영 속성을 그대로 적용한다.

권장 환경설정 경계:

```properties
openmd.auth.browser.cookie.name
openmd.auth.browser.cookie.secure
openmd.auth.browser.cookie.same-site
openmd.auth.browser.cookie.path
openmd.auth.browser.allowed-origins
```

`Domain`은 기본 설정 항목으로 제공하지 않고 host-only를 유지한다. 도메인 공유가 실제 요구로 승인될 때만 추가한다.

## 7. 요청 흐름

### 로그인

1. 브라우저용 컨트롤러가 기존 로그인 DTO를 검증한다.
2. `AuthService.login`이 기존 방식으로 Access/Refresh Token을 발급한다.
3. Refresh Token은 응답 직전에 HttpOnly Cookie로 변환한다.
4. body에는 Access Token과 승인된 만료 메타데이터만 넣는다.
5. 로그인 실패 시 기존 유효 Cookie를 임의로 삭제하거나 덮어쓰지 않는다.

기존 Cookie가 있는 상태에서 다른 계정 로그인이 성공했을 때 이전 session/family를 폐기할지는 세션 한도 정책과 함께 확정한다.

### refresh

1. 브라우저 컨트롤러가 지정된 Cookie 하나를 읽는다.
2. Cookie가 없거나 형식이 잘못되면 service 호출 없이 `401 AUTH_005`와 만료 Cookie를 반환한다.
3. `AuthService.refresh(cookieValue)`가 Redis session을 비소모 방식으로 검사해 현재 digest, user와 절대 만료를 확인한다. 사용된 digest면 기존 strict 정책대로 session/family를 폐기한다.
4. 사용자 활성 상태 조회와 session ID에 결합된 Access Token 발급을 완료한다. 이 단계의 DB·서명 실패는 Redis current digest를 바꾸지 않는다.
5. 실패 가능성이 있는 후속 외부 작업이 남지 않은 시점에 기존 Redis 원자 RTR로 Refresh Token을 회전한다.
6. 성공하면 회전된 Refresh Token을 같은 속성의 `Set-Cookie`로 덮어쓰고 body에는 미리 발급한 Access Token과 승인된 만료 메타데이터만 반환한다.

확정적인 `AUTH_005`에는 만료 Cookie를 함께 보낸다. Redis timeout, 연결 실패와 5xx에서는 브라우저가 재시도할 수 있도록 기존 Cookie를 변경하지 않는다.

### logout

1. Cookie가 있으면 `AuthService.logout(cookieValue)`로 Redis session/family 폐기를 시도한다.
2. Cookie가 없거나 이미 폐기됐으면 기존 멱등 정책대로 성공 처리한다.
3. 발급과 동일한 이름·Domain·Path에 `Max-Age=0`을 적용한다.
4. Redis 장애가 발생해도 응답에서 로컬 Cookie는 만료시키고 서버 폐기 실패는 5xx로 알린다.

## 8. CORS와 CSRF

일반 stateless Bearer API와 브라우저 Refresh Cookie API는 자격 증명 전송 방식이 다르므로 CORS와 CSRF 경계를 경로별로 분리한다.

### CORS

- 브라우저 세션 경로는 환경별 정확한 scheme·host·port allowlist만 허용한다.
- credentialed CORS에서 wildcard origin을 사용하지 않는다.
- 허용 method는 실제 `POST`, `DELETE`, `OPTIONS`로 제한한다.
- 허용 header에는 `Content-Type`과 승인된 CSRF header만 둔다.
- preflight가 Spring Security 인증보다 먼저 처리되도록 현재 `CorsConfigurationSource` 경계를 유지한다.
- body surface와 브라우저 surface의 CORS 정책을 경로별로 분리한다.

### CSRF

Spring Security 기본 CSRF 검사는 `/api/v1/**`에 대해 제외한다. 이 설정은 인증·인가를 해제하지 않는다. 보호 API의 Bearer filter와 `authenticated()` 규칙은 그대로 적용하며, 유효한 Access Token이 없는 요청은 계속 `401 AUTH_005`다.

브라우저 Refresh Cookie API인 `/api/v1/auth/web/**`는 기본 CSRF token 대신 기존 `BrowserSessionRequestGuard`에서 다음 두 조건을 모두 요구한다.

1. 모든 브라우저 세션 변경 요청의 `Origin`을 정확한 allowlist와 비교한다.
2. JSON 요청에 단순 form이 만들 수 없는 custom header를 요구해 CORS preflight를 강제한다.

고정 header는 비밀값이 아니며 Origin/CORS 검증을 대체하지 않는다. 배포가 cross-site라서 `SameSite=None`을 사용하거나 방어 심도를 높이기로 승인하면 Spring Security의 SPA CSRF 지원과 `CookieCsrfTokenRepository` 기반 double-submit token을 사용한다.

이 경우 JavaScript가 읽는 CSRF Cookie는 인증 자격이 아니므로 Refresh Cookie와 분리한다. Refresh Cookie의 `HttpOnly`는 해제하지 않는다.

Security 설정은 다음 경계를 유지한다.

- `/api/v1/**`는 Spring 기본 CSRF ignore 대상이지만 인증·인가 대상에서는 제외하지 않음
- `/api/v1/auth/web/**`는 정확한 browser origin allowlist와 `X-OpenMD-CSRF: 1` 검증을 service·Redis 접근 전에 수행
- Origin/CSRF 실패는 Redis rotate 이전에 `403 AUTH_009`로 종료
- `AUTH_009`는 `AUTH_005`와 구분해 refresh loop를 막음

## 9. RTR, 다중 탭과 응답 유실

현재 Redis Lua는 먼저 도착한 갱신 하나만 성공시키고, 사용된 digest가 다시 오면 session을 즉시 삭제한다. Cookie jar를 공유하는 여러 탭에서는 탭별 single-flight만으로 이 경합을 막을 수 없다.

### 기본 제안

- 첫 출시까지는 현재 fail-closed 재사용 탐지를 유지한다.
- 웹은 탭 내부 single-flight와 같은 origin 탭 간 refresh 조정을 모두 적용한다.
- 서버는 클라이언트 조정을 신뢰하지 않고 기존 재사용 탐지를 유지한다.
- 동시 갱신 또는 성공 응답 유실이 발생하면 재로그인이 필요할 수 있음을 수용 기준과 테스트에 명시한다.

### 후속 선택지

사용성 문제가 확인되면 Redis 회전 결과를 다음처럼 확장하는 방안을 별도로 승인한다.

```text
ROTATED     현재 digest를 최초 사용, 정상 회전
CONCURRENT  직전 digest가 짧은 grace 안에 재도착, family 유지
REUSED      grace 이후 사용된 digest 재도착, family 폐기
INVALID     세션 없음·상태 불일치
```

`CONCURRENT`는 새 Refresh Token을 다시 반환하지 않고 `409`로 응답한다. 다른 성공 응답이 Cookie jar를 갱신한 뒤 클라이언트가 한 번만 다시 시도한다. 이를 도입하면 grace 길이, 새 오류 코드, 응답 유실 복구와 재사용 탐지 지연을 별도 보안 결정으로 기록해야 한다.

이번 문서에서는 grace를 확정하지 않는다.

## 10. 오류와 Cookie 정리

| 조건 | HTTP/코드 | Cookie |
| --- | --- | --- |
| 로그인 자격 불일치 | `401 AUTH_001` | 기존 Cookie 변경 없음 |
| refresh Cookie 없음·형식 오류·만료 | `401 AUTH_005` | 만료 |
| 회전 토큰 재사용 | `401 AUTH_005`, family 폐기 | 만료 |
| Origin/CSRF 실패 | `403 AUTH_009` | 변경 없음 |
| Redis·서버 일시 장애 | `5xx` | 변경 없음 |
| logout Cookie 없음·이미 폐기 | `200` | 만료 |

- Cookie 원문, `Cookie`와 `Set-Cookie` 전체 header는 로그·추적·분석 payload에서 제외한다.
- 오류 handler가 확정적 인증 실패와 일시 장애를 구분할 수 있어야 한다.
- 만료 Cookie도 발급 Cookie와 동일한 identity 속성을 사용한다.

## 11. OpenAPI

- browser login/refresh 응답 schema에는 Refresh Token 필드가 없어야 한다.
- Cookie 인증은 `type: apiKey`, `in: cookie` 보안 scheme으로 문서화할 수 있지만 실제 값을 예제나 Swagger 설정에 넣지 않는다.
- HttpOnly Cookie는 JavaScript가 읽을 수 없으므로 Swagger 커스텀 스크립트로 추출하지 않는다.
- 문서 UI에서 browser session endpoint를 시험할 때는 같은 origin과 테스트 계정만 사용한다.
- 기존 body endpoint의 schema와 경고는 마이그레이션 동안 유지한다.

## 12. 단계적 전환

1. 공유 API 계약과 운영 topology, Cookie·CSRF 열린 질문을 승인한다.
2. 기존 body endpoint를 건드리지 않고 browser session endpoint와 계약 테스트를 추가한다.
3. Cookie 발급·회전·삭제, CORS, Origin/CSRF와 OpenAPI 테스트를 통과시킨다.
4. 웹을 cookie transport로 전환하고 body Refresh Token 직렬화가 사라졌는지 확인한다.
5. 토큰 원문 없이 로그인·refresh·logout 성공률, `AUTH_005`, 재사용과 CSRF 거절을 관측한다.
6. 앱/WebView 소비자가 모두 정리된 뒤에만 기존 body surface의 폐기를 별도 결정한다.

같은 endpoint가 Cookie와 body를 동시에 발급하거나 읽는 과도기 모드는 금지한다. 웹의 자동 body fallback도 금지한다.

## 13. 테스트 우선 구현 기준

구현 단계에서는 다음 순서로 실패를 먼저 확인한다.

### 컨트롤러·계약

- browser 로그인 body에 Refresh Token이 없고 `Set-Cookie`에만 존재
- refresh가 body 없이 Cookie만 사용
- 회전 성공마다 Cookie 값이 바뀌고 절대 만료가 늘어나지 않음
- logout이 멱등이며 동일 속성으로 Cookie 만료
- native body endpoint의 기존 request/response 회귀 없음

### 보안

- `HttpOnly`, 환경별 `Secure`, `SameSite`, host-only와 Path
- 허용 origin만 credentialed CORS 성공
- wildcard origin 거절
- Origin/CSRF 실패가 Redis 접근 전에 `403`
- 로그와 OpenAPI artifact에 Refresh Token 원문 없음

### RTR·복구

- 동일 Cookie 동시 갱신은 하나만 성공
- 현재 strict 정책에서는 이전 Cookie 재사용 시 family 폐기
- refresh 5xx에서는 Cookie 만료 header가 없음
- `AUTH_005`에서는 Cookie가 만료됨
- 성공 응답 유실과 다중 탭 시나리오의 승인된 복구 결과

실제 브라우저 통합 테스트에서는 credential mode, preflight, Cookie jar, 새로고침 부트스트랩과 로그아웃 후 재진입을 확인한다.

## 14. 열린 질문

- 운영 웹과 API가 same-site인지, `SameSite=Lax`를 사용할 수 있는지
- 로컬 개발을 HTTPS로 통일할지 HTTP localhost 예외를 둘지
- 기존 Cookie가 있는 상태의 새 로그인에서 이전 session/family를 폐기할지
- native body surface의 폐기 조건과 일정

이 질문 중 API 요청·응답이나 소비자 동작을 바꾸는 항목은 인증 API 계약에서 승인한 뒤 구현한다.

## 참고

- [HTTP Cookie 초안 표준](https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis/)
- [MDN Set-Cookie](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie)
- [Spring Security CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html)
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
