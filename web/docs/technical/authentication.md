# 웹 인증 상태·토큰·API 통합 설계

- 상태: 초안
- 소유 영역: `web/`
- 구현 전제: HttpOnly Cookie 기반 Refresh Token 계약이 승인되고 서버에 반영되어야 함
- 관련 원장:
  - [이메일 기반 자체 인증 기능명세](../../../docs/features/01-local-auth.md)
  - [인증 흐름](../../../docs/flows/authentication.md)
  - [인증 API 계약](../../../docs/contracts/api/authentication.md)
  - [사용자·인증 데이터 계약](../../../docs/contracts/data/authentication.md)
  - [OpenMD 디자인 기준](../../../DESIGN.md)

## 1. 목적

이 문서는 이메일 로그인을 웹에서 구현할 때 인증 런타임 상태, 토큰 수명주기, Axios 요청 계층, TanStack Query 캐시와 라우트 보호를 어떻게 나눌지 정의한다.

사용자에게 보이는 로그인 화면 구조와 문구는 별도 화면 명세가 책임진다. 이 문서는 제품 정책이나 API 계약을 새로 확정하지 않고, 승인된 계약을 웹에서 소비하는 방법과 계약 변경이 필요한 지점을 제안한다.

## 2. 현재 계약과 목표 계약의 차이

### 현재 확인된 계약·구현

- Access Token은 5분 유효한 JWT이며 `Authorization: Bearer`로 전달한다.
- 로그인 응답은 Access Token과 Refresh Token을 모두 JSON body로 반환한다.
- 갱신과 로그아웃은 Refresh Token을 JSON body로 받는다.
- 서버 CORS는 `allowCredentials(false)`이다.
- `/api/v1/auth/**`는 현재 CSRF 검사에서 제외된다.
- Refresh Token 기본 절대 수명은 서버 설정상 30일이지만 계약의 최종 수명은 열린 질문이다.
- Refresh Token은 매 갱신마다 회전하며 동일 토큰의 동시 사용은 하나만 성공한다.

### 사용자가 선택한 웹 목표

- 웹 Refresh Token은 JavaScript가 읽을 수 없는 HttpOnly Cookie로 전달·보관한다.
- Access Token은 웹 런타임 메모리에만 보관한다.
- Access Token과 Refresh Token을 `localStorage`, `sessionStorage`, IndexedDB 또는 TanStack Query cache에 저장하지 않는다.

### 계약 충돌과 구현 게이트

HttpOnly Cookie 방식은 현재 API 계약·서버 구현과 충돌한다. 아래 백엔드/API 계약 변경이 승인되고 구현되기 전에는 이 문서의 쿠키 흐름을 실제 동작으로 고정하지 않는다.

FE가 `withCredentials`만 켜는 것으로는 전환할 수 없다. 로그인·갱신 응답의 `Set-Cookie`, 갱신·로그아웃의 cookie 소비, credentialed CORS, CSRF 방어와 cookie 삭제 규칙이 서버에서 함께 바뀌어야 한다.

## 3. 책임 분리

| 상태 종류 | 소유자 | 예시 | 보존 범위 |
| --- | --- | --- | --- |
| 폼 상태 | 로그인/가입 화면 | 이메일, 비밀번호, 필드 오류, 제출 중 | 해당 화면 수명 |
| 인증 런타임 상태 | Auth session 모듈 | 부트스트랩 상태, Access Token, 만료 시각 | 현재 문서 메모리 |
| 현재 사용자 서버 상태 | TanStack Query | `/api/v1/users/me` 응답 | Query cache |
| 도메인 서버 상태 | TanStack Query | 학습자료, 풀이 기록 | Query cache, 사용자별 격리 |
| Refresh Token | 브라우저 cookie jar | 서버가 발급·회전한 opaque token | HttpOnly Cookie |
| 화면 이동 의도 | Router | 로그인 전 원래 목적지 | 메모리성 location state |

TanStack Query를 전역 상태 저장소처럼 사용하지 않는다. Query는 서버 응답과 요청 수명주기를 담당하고, Access Token과 인증 부트스트랩 상태는 작은 인증 런타임 모듈이 담당한다.

## 4. 토큰 보관 원칙

### Access Token

- 모듈 내부 메모리에만 저장한다.
- 함께 받은 `accessExpiresAt`을 메모리에 보관해 만료 직전 요청을 감지한다.
- React 컴포넌트 props, URL, Router state, Query key, 오류 로그와 분석 이벤트에 넣지 않는다.
- 새로고침이나 탭 종료로 사라지는 것을 정상 동작으로 본다.
- 보호 요청에만 `Authorization` header로 붙인다. 공개 인증 요청에는 붙이지 않는다.

### Refresh Token

- FE 코드가 값을 받거나 읽거나 직렬화하지 않는다.
- 서버가 `Set-Cookie`로 발급·회전·삭제한다.
- 브라우저는 허용된 인증 요청에만 cookie를 자동 첨부한다.
- Query cache, 브라우저 JavaScript 저장소와 애플리케이션 로그에 토큰 원문이 존재해서는 안 된다.

## 5. 인증 상태 모델

```text
bootstrapping
  ├─ refresh 성공 + me 성공 ──> authenticated
  └─ refresh 자격 없음 ───────> anonymous

anonymous
  └─ login 성공 + me 성공 ───> authenticated

authenticated
  ├─ access 갱신 성공 ────────> authenticated
  ├─ refresh 실패 AUTH_005 ───> anonymous
  └─ logout 시작/완료 ─────────> anonymous
```

권장 런타임 상태는 다음 세 값으로 제한한다.

- `bootstrapping`: 최초 진입에서 cookie 기반 세션 복구 여부를 확인 중
- `authenticated`: 유효한 Access Token과 현재 사용자 정보가 있음
- `anonymous`: 복구 가능한 세션이 없거나 로그아웃됨

`refreshing`은 전역 화면 상태로 승격하지 않고 내부 single-flight Promise로 관리한다. 이미 보이는 보호 화면 전체를 갱신 때마다 로딩 화면으로 바꾸지 않는다. 갱신이 최종 실패할 때만 `anonymous`로 전이한다.

## 6. API 계층

### 클라이언트 구분

- `publicApi`: 가입, 인증 코드 발송·확인, 로그인처럼 Access Token이 필요 없는 요청
- `protectedApi`: `/users/me`와 향후 보호 API 요청
- 두 클라이언트 모두 쿠키 계약 승인 후 필요한 인증 endpoint에서 credentials를 포함한다.
- `protectedApi`만 Access Token 요청 interceptor와 인증 실패 response interceptor를 사용한다.

공개 인증 요청에 만료된 Bearer Token을 붙이지 않아 로그인·갱신 흐름이 보호 API interceptor와 섞이지 않게 한다.

### Access Token 첨부

보호 요청 직전에 다음 순서를 적용한다.

1. 메모리에 Access Token이 없으면 refresh single-flight를 요청한다.
2. `accessExpiresAt`이 현재 시각에 clock skew 여유를 더한 값보다 이르면 refresh single-flight를 요청한다.
3. 갱신된 Access Token을 `Authorization: Bearer`로 첨부한다.
4. 만료 예측과 무관하게 서버가 `AUTH_005`를 반환하면 한 번만 갱신 후 원 요청을 재시도한다.

백그라운드 timer로 주기적으로 토큰을 회전하지 않는다. 보호 요청이 있거나 세션 부트스트랩이 필요한 시점에만 갱신해 불필요한 회전과 다중 탭 경쟁을 줄인다.

### Single-flight refresh

모듈 범위에 `refreshPromise` 하나를 둔다.

```text
refreshPromise가 없음 -> refresh 요청을 만들고 저장
refreshPromise가 있음 -> 같은 Promise를 기다림
성공 -> 새 Access Token/만료 시각 저장
실패 -> 인증 메모리와 개인 cache 제거
finally -> refreshPromise 비움
```

동시에 여러 보호 요청이 401을 받아도 한 탭에서는 refresh endpoint를 한 번만 호출한다. 원 요청에는 내부 재시도 표식을 두고 최대 한 번만 재시도한다. refresh 요청 자체와 로그인·로그아웃 요청은 response interceptor의 자동 refresh 대상에서 제외해 재귀와 무한 루프를 막는다.

이 Promise는 한 브라우저 문서 안에서만 직렬화를 보장한다. 여러 탭은 같은 회전 Cookie를 공유하므로 교차 탭 동시 갱신 정책이 승인되기 전에는 완전한 안전성을 보장할 수 없다.

## 7. 세션 부트스트랩

새로고침 뒤 Access Token은 의도적으로 사라지지만 HttpOnly Refresh Cookie는 남을 수 있다.

1. 앱 시작 상태를 `bootstrapping`으로 둔다.
2. body 없이 refresh endpoint를 credentials 포함으로 한 번 호출한다.
3. 성공하면 응답의 Access Token과 만료 시각을 메모리에 기록한다.
4. Access Token으로 `/api/v1/users/me`를 조회한다.
5. 두 단계가 성공하면 `authenticated`로 전이한다.
6. refresh가 `AUTH_005`이면 정상적인 비로그인 상태로 보고 `anonymous`로 전이한다.
7. 일시적 네트워크 실패는 자격 없음과 구분해 재시도 화면을 제공한다. 네트워크 실패만으로 cookie 삭제를 가정하지 않는다.

부트스트랩이 끝나기 전에 보호 라우트를 로그인 화면으로 즉시 보내지 않는다. 그렇지 않으면 유효한 cookie가 있는 사용자에게 로그인 화면이 깜빡이고 원래 목적지를 잃을 수 있다.

## 8. 라우트 보호

| 인증 상태 | 공개 인증 라우트 | 보호 라우트 |
| --- | --- | --- |
| `bootstrapping` | 부트스트랩 완료 대기 | 부트스트랩 완료 대기 |
| `anonymous` | 접근 허용 | 로그인으로 이동하며 원래 목적지 저장 |
| `authenticated` | 홈 또는 명시된 다음 목적지로 이동 | 접근 허용 |

- 원래 목적지는 내부 same-origin route만 허용해 open redirect를 막는다.
- 로그인 성공 후 원래 목적지가 있으면 복귀하고, 없으면 제품이 정한 기본 진입점으로 이동한다.
- 로그아웃 후 history back으로 보호 화면의 개인 데이터가 다시 보이지 않도록 cache를 먼저 제거하고 공개 경로로 replace 이동한다.
- 네트워크 장애를 비로그인으로 오판해 로그인으로 보내지 않는다.

## 9. TanStack Query 역할

### Query

- 현재 사용자: `['auth', 'me']`
- 개인 데이터: `['private', userId, ...resourceKey]`
- Query key에 이메일, Access Token, Refresh Token을 넣지 않는다.
- `enabled`만으로 인증 판정을 대신하지 않는다. 인증 상태가 `authenticated`일 때 보호 Query를 활성화한다.

`/users/me`는 인증 사실을 만드는 저장소가 아니라 서버의 현재 사용자 표현이다. Access Token이 없는 상태에서 cache에 남은 `me`만 보고 인증됨으로 판단하지 않는다.

### Mutation

- 로그인, 가입, 이메일 인증, 재발송, 로그아웃은 `useMutation` 대상이다.
- 로그인 mutation 성공 시 Access Token을 메모리에 기록한 뒤 `me`를 조회하고, 둘 다 성공한 후 인증 완료로 전이한다.
- 로그아웃 mutation은 서버 응답 성공 여부와 관계없이 `finally`에서 로컬 인증 메모리와 개인 cache를 제거한다.
- 인증·가입 mutation은 자동 retry하지 않는다. 사용자의 명시적 재시도로 중복 요청과 숨은 재전송을 통제한다.
- 보호 Query는 인증 오류를 Query retry로 반복하지 않는다. 인증 갱신은 Axios single-flight 한 계층에서만 수행한다.
- 일반 네트워크 Query retry 횟수는 앱 공통 QueryClient 정책에서 별도로 정하되, 4xx와 `AUTH_005`는 retry하지 않는다.

### Cache 정리와 사용자 격리

로그아웃 또는 refresh 최종 실패 시 다음 순서를 사용한다.

1. 진행 중인 개인 Query를 취소한다.
2. 인증 메모리에서 Access Token과 만료 시각을 제거한다.
3. `['auth', 'me']`와 `['private', ...]` 범위의 cache를 제거한다.
4. 민감한 화면 local state를 unmount한다.
5. 공개 경로로 replace 이동한다.

다른 사용자가 같은 브라우저에서 로그인했을 때 이전 사용자의 개인 cache를 재사용하지 않는다. 사용자 ID를 개인 Query key에 포함하고 세션 교체 때 기존 개인 cache를 제거한다.

## 10. 요청별 흐름

### 로그인

1. 로그인 mutation이 이메일과 비밀번호를 보낸다.
2. 서버는 Refresh Token을 HttpOnly Cookie로 설정하고 body에는 Access Token과 만료 정보만 반환한다.
3. FE는 Access Token을 메모리에 저장한다.
4. `/users/me`를 조회해 현재 사용자 cache를 만든다.
5. 원래 목적지 또는 기본 진입점으로 이동한다.

로그인 성공 후 `me`가 일시적으로 실패하면 세션 자체를 즉시 폐기하지 않고 복구 UI를 제공한다. `AUTH_005`라면 인증 정리를 수행한다.

### 갱신

1. FE는 Refresh Token body 없이 credentials 포함 요청을 보낸다.
2. 브라우저가 HttpOnly Cookie를 첨부한다.
3. 서버는 회전된 Refresh Token을 새 `Set-Cookie`로 덮어쓴다.
4. body에는 새 Access Token과 만료 정보만 반환한다.
5. FE는 메모리 Access Token을 원자적으로 교체한다.

### 로그아웃

1. FE는 Refresh Token body 없이 credentials 포함 요청을 보낸다.
2. 서버는 세션을 폐기하고 동일한 cookie 속성으로 만료 `Set-Cookie`를 반환한다.
3. FE는 응답 성공·실패와 무관하게 로컬 인증 메모리와 개인 cache를 제거한다.
4. 공개 경로로 replace 이동한다.

## 11. 오류 처리

| 오류 | 처리 책임 | FE 동작 |
| --- | --- | --- |
| `COMMON_001` | 폼/API adapter | `fields`를 해당 필드 오류로 연결하고 입력 보존 |
| `AUTH_001` | 로그인 화면 | 이메일·비밀번호를 구분하지 않는 공통 오류 표시 |
| `AUTH_003` | 인증 코드 화면 | 코드 오류 표시, 입력 수정 허용 |
| `AUTH_004` | 인증 코드 화면 | 현재 코드 폐기 안내와 재발송 행동 제공 |
| `AUTH_005` 보호 요청 | Axios 인증 계층 | single-flight refresh 후 원 요청 1회 재시도 |
| `AUTH_005` refresh | Auth session 모듈 | 인증 메모리·개인 cache 제거 후 재로그인 안내 |
| `AUTH_008` | 가입/재발송 화면 | 입력 보존, 명시적 재시도 제공 |
| 네트워크 실패 | 각 화면/부트스트랩 | 자격 없음과 구분, 입력 보존, 명시적 재시도 |

클라이언트 분기는 서버 `message` 문자열이 아니라 안정적인 `error.code`를 사용한다.

## 12. Cookie·CORS·CSRF 요구사항

아래 값은 FE 내부 결정이 아니라 API 계약과 배포 토폴로지에서 승인해야 한다.

### Cookie

- `HttpOnly`: 필수
- `Secure`: 운영 필수; 로컬 개발 예외 방식은 서버 설정으로 명시
- `SameSite`: 웹과 API의 실제 site 관계를 확인해 `Lax` 또는 `None` 결정
- `Domain`: 가능한 한 host-only cookie 선호
- `Path`: refresh와 logout에 전송되며 다른 API에는 불필요하게 노출되지 않는 범위
- `Max-Age`/`Expires`: 서버 Redis session의 절대 만료와 일치
- 회전 시 새 cookie를 동일 속성으로 덮어쓰기
- 로그아웃 시 발급 때와 동일한 Name, Domain, Path로 만료시키기

### CORS

- credentialed 요청을 허용하도록 서버 `allowCredentials(true)`가 필요하다.
- `Access-Control-Allow-Origin`은 와일드카드가 아니라 승인된 정확한 origin이어야 한다.
- FE Axios 요청은 승인된 인증 endpoint에서 credentials를 포함한다.
- 개발·운영 origin과 HTTPS 구성을 계약과 환경설정에 함께 기록한다.

### CSRF

Refresh Cookie가 자동 첨부되므로 refresh와 logout을 포함한 상태 변경 endpoint의 CSRF 방어를 다시 설계해야 한다. `SameSite`만으로 충분하다고 가정하지 않는다.

서버는 배포 토폴로지에 맞춰 Origin/Referer 검증과 CSRF token/header 방식 중 적용 정책을 확정해야 한다. FE가 보내야 하는 CSRF header와 token 발급·회전·만료 방식이 있다면 API 계약에 포함한다. 현재의 `/api/v1/auth/**` CSRF 무시 설정을 그대로 두고 쿠키 방식만 도입하지 않는다.

## 13. WebView·네이티브 경계

이 문서는 브라우저 웹 소비자만 설계한다. 현재 계약은 네이티브 앱이 Refresh Token을 OS 보안 저장소에 보관하는 방향도 포함하므로, 웹을 cookie-only로 바꾸면서 모든 소비자에게 동일 응답을 강제하면 앱 계약이 깨질 수 있다.

백엔드는 다음 중 하나를 제품·앱·서버 계약에서 별도로 결정해야 한다.

- WebView도 서버 cookie jar를 사용하고 네이티브가 Refresh Token을 직접 소유하지 않음
- 웹 cookie 계약과 네이티브 secure-storage 계약을 분리된 인증 surface로 제공
- 초기 출시 소비자를 하나로 제한하고 다른 소비자 계약을 후속 확정

FE TRD에서 이 선택을 확정하지 않는다.

## 14. 후보 모듈 구조

```text
web/src/
  app/
    providers/
      queryClient.ts
    router/
      AuthGate.tsx
  features/
    auth/
      api/
        auth.api.ts
        auth.types.ts
      model/
        authSession.ts
        authRefresh.ts
        auth.queries.ts
        auth.mutations.ts
      ui/
        ...
  pages/
    login/
      ...
  shared/
    api/
      publicApi.ts
      protectedApi.ts
      apiError.ts
```

실제 파일 수는 구현 시 책임이 분리되는 최소 수준으로 조정한다. 이 구조를 빈 파일이나 계층으로 선행 생성하지 않는다.

## 15. 백엔드/API 계약 선행 변경 체크리스트

- [ ] 웹 로그인 응답에서 Refresh Token 원문 제거
- [ ] 로그인 성공 시 Refresh Token `Set-Cookie` 발급
- [ ] refresh 요청 body에서 웹 Refresh Token 제거하고 cookie에서 읽기
- [ ] refresh 성공 시 회전된 cookie 재발급
- [ ] logout 요청 body에서 웹 Refresh Token 제거하고 cookie에서 읽기
- [ ] logout 성공·멱등 결과에서 cookie 만료 처리
- [ ] Access Token과 `accessExpiresAt`의 body 응답 유지 여부 확정
- [ ] Refresh 절대 만료 시각을 body에 계속 노출할지 확정
- [ ] Cookie Name, Domain, Path, Secure, HttpOnly, SameSite, Max-Age 확정
- [ ] credentialed CORS와 정확한 allowed origin 반영
- [ ] CSRF 방어와 필요한 FE header 계약 확정
- [ ] 로그인·refresh·logout controller 및 통합 테스트 수정
- [ ] 웹과 네이티브/WebView의 Refresh Token 전달 계약 분리 여부 확정
- [ ] 다중 탭 동시 refresh 정책 확정

## 16. 열린 질문

- Refresh Token의 최종 절대 수명
- 웹과 API의 운영 domain/site 구성 및 `SameSite` 값
- CSRF 방어 방식과 FE가 전달할 header 계약
- 여러 탭이 동시에 refresh할 때 회전 토큰 경쟁을 조정하는 방식
- 웹과 네이티브/WebView가 같은 인증 endpoint를 사용할지 여부
- 로그아웃 직후 남은 5분 이내 Access Token을 서버에서 차단할 수준
- 로그인 성공 후 `me`가 네트워크 오류로 실패했을 때의 최종 화면 문구와 재시도 UX

## 17. 구현 검증 기준

### 정적 검증

- `pnpm typecheck`
- `pnpm lint`
- `pnpm build`

### 인증 동작 검증

- 로그인 응답·애플리케이션 저장소·Query cache에서 Refresh Token 원문을 읽을 수 없다.
- 새로고침 뒤 cookie가 유효하면 refresh와 `me`를 거쳐 보호 화면으로 복구된다.
- cookie가 없거나 만료되면 로그인 화면으로 이동한다.
- 여러 보호 요청의 동시 401이 한 탭에서 refresh 한 번으로 합쳐진다.
- refresh 실패 뒤 원 요청이 반복되지 않고 개인 cache가 제거된다.
- 로그아웃 API 실패 상황에서도 로컬 개인 데이터와 Access Token이 제거된다.
- 로그인한 사용자가 바뀌어도 이전 사용자의 Query cache가 노출되지 않는다.
- 네트워크 장애가 `anonymous`로 오판되지 않으며 재시도할 수 있다.
- Router의 원래 목적지 복귀가 외부 URL redirect를 허용하지 않는다.

### 브라우저·보안 검증

- DevTools에서 Refresh Cookie가 `HttpOnly`, 운영 `Secure`, 승인된 `SameSite`와 Path를 가진다.
- credentialed CORS는 승인 origin만 허용한다.
- 승인된 CSRF 방어 없이 cookie 기반 refresh/logout이 성공하지 않는다.
- 브라우저 저장소와 콘솔·오류 추적 payload에 토큰 원문이 남지 않는다.
- 다중 탭 refresh 시나리오는 정책 확정 후 별도 검증한다.
