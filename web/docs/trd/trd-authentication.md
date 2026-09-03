---
document_type: trd
status: implemented
scope: web
---

# [TRD · Web] 웹 인증 상태·토큰·API 통합 설계

- 상태: 구현 동기화
- 소유 영역: `web/`
- 구현 기준: 브라우저 전용 HttpOnly Cookie 세션 계약
- 관련 원장:
  - [이메일 기반 자체 인증 PRD](../../../docs/prd/prd-local-authentication.md)
  - [인증 흐름](../../../docs/ux/flow-authentication.md)
  - [인증 API 계약](../../../docs/contracts/contract-api-authentication.md)
  - [사용자·인증 데이터 계약](../../../docs/contracts/contract-data-authentication.md)
  - [로그인 전 공개 랜딩](../../../docs/ux/screen-public-landing.md)
  - [첫 진입 온보딩과 NalQ 가이드](../../../docs/ux/screen-onboarding.md)
  - [NalQ 디자인 기준](../../../DESIGN.md)

## 1. 목적

이 문서는 이메일 로그인을 웹에서 구현할 때 인증 런타임 상태, 토큰 수명주기, Axios 요청 계층, TanStack Query 캐시와 라우트 보호를 어떻게 나눌지 정의한다.

사용자에게 보이는 로그인 화면 구조와 문구는 별도 화면 명세가 책임진다. 이 문서는 제품 정책이나 API 계약을 새로 확정하지 않고, 승인된 계약을 웹에서 소비하는 방법과 계약 변경이 필요한 지점을 제안한다.

## 2. 확정 계약과 구현 기준

- 브라우저 로그인은 `POST /api/v1/auth/web/sessions`, 갱신은 `POST /api/v1/auth/web/sessions/refresh`, 로그아웃은 `DELETE /api/v1/auth/web/sessions/current`를 사용한다.
- 세 요청은 `withCredentials: true`와 고정 헤더 `X-OpenMD-CSRF: 1`을 보내며, 서버는 정확한 허용 Origin도 함께 검증한다.
- Refresh Token은 JavaScript가 읽을 수 없는 HttpOnly Cookie로만 발급·회전·삭제한다. 요청·응답 JSON에는 원문을 포함하지 않는다.
- 응답 JSON은 `accessToken`, `accessExpiresAt`, `refreshExpiresAt`을 포함한다. Access Token과 두 만료 시각만 `tokenVault`의 private 메모리에 둔다.
- Access Token은 5분 유효한 JWT이며 보호 요청의 `Authorization: Bearer`로 전달한다.
- Cookie는 운영에서 `__Host-openmd_refresh`, `Secure`, `HttpOnly`, `SameSite=Lax`, `Path=/`를 사용한다. 로컬 HTTP 개발은 서버 설정으로 `openmd_refresh`, `Secure=false`를 사용한다.
- Refresh Token Rotation은 strict fail-closed다. 재사용이 감지되면 해당 세션을 폐기하며 grace window를 두지 않는다.
- `AUTH_009`는 브라우저 세션 변경 요청의 Origin/CSRF 검증 실패를 뜻한다.
- 현재 사용자 객체는 TanStack Query의 `['auth', 'me']`만 소유하고 별도 store에 복사하지 않는다. 인증 phase는 작은 external store로 구독하며 Zustand를 도입하지 않는다.

## 3. 책임 분리

| 상태 종류 | 소유자 | 예시 | 보존 범위 |
| --- | --- | --- | --- |
| 폼 상태 | 로그인/가입 화면 | 이메일, 비밀번호, 필드 오류, 제출 중 | 해당 화면 수명 |
| 인증 런타임 상태 | Auth session 모듈 | 부트스트랩 상태, Access Token, 만료 시각 | 현재 문서 메모리 |
| 현재 사용자 서버 상태 | TanStack Query | `/api/v1/users/me` 응답 | Query cache |
| 도메인 서버 상태 | TanStack Query | 학습자료, 풀이 기록 | Query cache, 사용자별 격리 |
| Refresh Token | browser cookie jar | 서버가 발급·회전·삭제 | HttpOnly Cookie |
| 화면 이동 의도 | Router | 로그인 전 원래 목적지 | 메모리성 location state |
| 자동 온보딩 표시값 | Local Storage | 계정별 `auto-shown` 값 | 브라우저·WebView 로컬 저장소 |
| 자동 온보딩 진입 허가 | Onboarding 모듈 | 회원가입 성공 직후의 일회성 admission | 현재 JavaScript 문서 메모리 |

TanStack Query를 전역 상태 저장소처럼 사용하지 않는다. Query는 서버 응답과 요청 수명주기를 담당하고, token vault와 인증 phase external store는 작은 framework-neutral 인증 런타임 모듈이 담당한다. 사용자 객체를 다른 store에 복제하지 않으며 Zustand는 현재 인증 범위에 사용하지 않는다.

## 4. 토큰 보관 원칙

### Access Token

- 모듈 내부 메모리에만 저장한다.
- 함께 받은 `accessExpiresAt`을 메모리에 보관해 만료 직전 요청을 감지한다.
- React 컴포넌트 props, URL, Router state, Query key, 오류 로그와 분석 이벤트에 넣지 않는다.
- 새로고침이나 탭 종료로 사라지는 것을 정상 동작으로 본다.
- 보호 요청에만 `Authorization` header로 붙인다. 공개 인증 요청에는 붙이지 않는다.

### Refresh Token

- FE 코드가 값을 받거나 읽거나 직렬화하지 않고 서버가 `Set-Cookie`로 발급·회전·삭제한다.
- 토큰 원문을 Query cache, React state, token vault, 브라우저 JavaScript 저장소, Router state, URL과 애플리케이션 로그에 넣지 않는다.
- `refreshExpiresAt`은 사용자 세션 수명 판단을 위한 메타데이터일 뿐 Refresh Token 원문이나 인증 근거가 아니다.

## 5. 인증 상태 모델

```text
bootstrapping
  ├─ refresh 성공 + me 성공 ──> authenticated
  ├─ refresh AUTH_005 ─────────> anonymous
  └─ network/5xx ──────────────> bootstrap-error

bootstrap-error
  └─ 사용자 재시도 ───────────> bootstrapping

anonymous
  └─ login 성공 + me 성공 ───> authenticated

authenticated
  ├─ access 갱신 성공 ────────> authenticated
  ├─ refresh 실패 AUTH_005 ───> anonymous
  └─ logout 시작/완료 ─────────> anonymous
```

런타임 상태는 다음 네 값으로 제한한다.

- `bootstrapping`: 최초 진입에서 cookie 기반 세션 복구 여부를 확인 중
- `bootstrap-error`: 네트워크나 서버 오류로 세션 유무를 판정할 수 없어 재시도를 기다림
- `authenticated`: 유효한 Access Token과 현재 사용자 정보가 있음
- `anonymous`: 복구 가능한 세션이 없거나 로그아웃됨

`refreshing`은 전역 화면 상태로 승격하지 않고 내부 single-flight Promise로 관리한다. 이미 보이는 보호 화면 전체를 갱신 때마다 로딩 화면으로 바꾸지 않는다. refresh의 `AUTH_005`일 때만 `anonymous`로 전이하고 network/5xx는 현재 자격과 cache를 유지한다.

## 6. API 계층

### 클라이언트 구분

- `publicApi`: 가입, 인증 코드 발송·확인, 로그인처럼 Access Token이 필요 없는 요청
- `protectedApi`: `/users/me`와 향후 보호 API 요청
- 브라우저 로그인·갱신·로그아웃 transport만 credentials와 CSRF 헤더를 포함한다.
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
AUTH_005 실패 -> 인증 메모리와 개인 cache 제거
network/5xx 실패 -> 현재 자격과 cache를 유지하고 호출자에게 오류 전달
finally -> refreshPromise 비움
```

동시에 여러 보호 요청이 401을 받아도 한 탭에서는 refresh endpoint를 한 번만 호출한다. 원 요청에는 내부 재시도 표식을 두고 최대 한 번만 재시도한다. refresh 요청 자체와 로그인·로그아웃 요청은 response interceptor의 자동 refresh 대상에서 제외해 재귀와 무한 루프를 막는다.

탭 내부에서는 Promise single-flight를 사용한다. 여러 탭 사이에서는 표준 Web Locks API의 exclusive lock으로 refresh 요청을 직렬화해 같은 Cookie의 동시 사용을 줄인다. Web Locks를 지원하지 않는 브라우저에서는 탭 내부 single-flight만 보장되며 서버의 strict fail-closed 정책이 최종 안전장치다.

## 7. 세션 부트스트랩

1. 앱 시작 상태를 `bootstrapping`으로 둔다.
2. body 없이 credentials와 CSRF 헤더를 포함한 refresh를 호출한다.
3. 성공하면 Access Token 메모리를 복구하고 `/users/me`를 조회해 `authenticated`로 전이한다.
4. refresh가 `AUTH_005`면 로컬 인증 상태와 개인 cache를 정리하고 `anonymous`로 전이한다.
5. refresh 또는 `/users/me`가 network/5xx로 실패하면 `bootstrap-error`로 전이하고 사용자의 명시적 재시도를 제공한다. 이 실패를 자격 없음으로 단정하거나 cookie/cache를 삭제하지 않는다.

부트스트랩이 끝나기 전에 보호 라우트를 로그인 화면으로 즉시 보내지 않는다. 그렇지 않으면 유효한 cookie가 있는 사용자에게 로그인 화면이 깜빡이고 원래 목적지를 잃을 수 있다.

## 8. 라우트 보호

| 인증 상태 | 공개 인증 라우트 | 보호 라우트 |
| --- | --- | --- |
| `bootstrapping` | 부트스트랩 완료 대기 | 부트스트랩 완료 대기 |
| `bootstrap-error` | 세션 확인 재시도 제공 | 세션 확인 재시도 제공 |
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

`/users/me`의 Query 옵션은 `staleTime: 5분`, `gcTime: Infinity`, stale 상태의 window focus refetch, reconnect 시 always refetch, polling 없음으로 고정한다. `AUTH_005`와 모든 4xx는 Query retry하지 않고 network/5xx만 최대 한 번 재시도한다. React의 `useCurrentUser`는 phase가 `authenticated`일 때만 활성화하며, 로그인과 bootstrap은 같은 options를 `fetchQuery`로 명시 실행한다. query function은 `AbortSignal`을 Axios에 전달해 logout/cache 정리의 `cancelQueries`가 실제 HTTP 요청을 취소하게 한다.

### Mutation

- 로그인, 가입, 이메일 인증, 재발송, 로그아웃은 `useMutation` 대상이다.
- 로그인 mutation 성공 시 Access Token을 메모리에 기록한 뒤 `me`를 조회하고, 둘 다 성공한 후 인증 완료로 전이한다.
- 가입용 인증 메일 mutation은 이메일만 전송한다. 비밀번호와 `signUpToken`은 가입 화면의 React 메모리에만 유지하고 Router state나 브라우저 저장소에 넣지 않는다.
- 브라우저 최종 가입 결과가 불명확한 network/코드 없는 5xx이면 최종 가입을 반복하지 않고 refresh mutation을 정확히 한 번 실행한다. 성공하면 `me`를 조회하고, 실패하면 이메일만 담은 Router state로 로그인 화면을 replace 이동한다.
- 로그아웃 mutation은 서버 응답 성공 여부와 관계없이 `finally`에서 로컬 인증 메모리와 개인 cache를 제거한다.
- 인증·가입 mutation은 자동 retry하지 않는다. 사용자의 명시적 재시도로 중복 요청과 숨은 재전송을 통제한다.
- 보호 Query는 인증 오류를 Query retry로 반복하지 않는다. 인증 갱신은 Axios single-flight 한 계층에서만 수행한다.
- 일반 네트워크 Query retry 횟수는 앱 공통 QueryClient 정책에서 별도로 정하되, 4xx와 `AUTH_005`는 retry하지 않는다.

### Cache 정리와 사용자 격리

로그아웃 또는 refresh의 `AUTH_005` 최종 실패 시 다음 순서를 사용한다.

1. 진행 중인 개인 Query를 취소한다.
2. 인증 메모리에서 Access Token과 만료 시각을 제거한다.
3. `['auth', 'me']`와 `['private', ...]` 범위의 cache를 제거한다.
4. 민감한 화면 local state를 unmount한다.
5. 공개 경로로 replace 이동한다.

다른 사용자가 같은 브라우저에서 로그인했을 때 이전 사용자의 개인 cache를 재사용하지 않는다. 사용자 ID를 개인 Query key에 포함하고 세션 교체 때 기존 개인 cache를 제거한다.

## 10. 요청별 흐름

### 회원가입

1. 인증 메일 요청은 `POST /api/v1/auth/email-verifications`에 이메일만 전송한다. 개발 환경도 같은 서버 계약을 사용한다.
2. 인증 완료 뒤 받은 `signUpToken`과 비밀번호는 화면 메모리에서만 최종 가입 요청까지 유지한다.
3. 최종 가입 성공 시 Access Token을 메모리에 기록하고 `/users/me`를 조회한다. 식별된 사용자 ID의 `nalq:onboarding:auto-shown:v1:{userId}`가 없으면 값을 기록하고, 현재 JavaScript 문서 메모리에 일회성 admission을 만든 뒤 `/onboarding`으로 replace 이동한다.
4. admission은 Router state의 사용자 ID·난수 식별자와 문서 메모리 값을 함께 대조한다. 따라서 일반 로그인, 세션 복원, 직접 URL 진입과 새로고침은 로컬 표시값 유무와 관계없이 자동 온보딩을 열지 않는다.
5. Local Storage 조회·기록이 실패하거나 이미 표시값이 있으면 자동 온보딩을 반복하지 않고 홈으로 replace 이동한다. 수동 `/profile/guide`는 이 저장소와 admission을 읽거나 변경하지 않는다.
6. `AUTH_011`은 계정 생성은 완료됐으나 브라우저 세션 발급에 실패한 결과이므로 최종 가입을 반복하지 않는다. 비밀번호를 전달하지 않고 이메일과 고정 안내 식별자만 Router state에 담아 로그인 화면으로 이동한다.
7. network 또는 안정적인 오류 코드가 없는 5xx처럼 가입 결과가 불명확하면 Cookie 도착 여부를 확인하기 위해 refresh를 한 번만 호출한다. 성공하면 `/users/me` 조회 후 3~5번의 신규 사용자 판단을 적용하고, 실패하면 6번과 같은 로그인 안내로 이동한다.

### 로그인

1. 로그인 mutation이 브라우저 session endpoint에 이메일과 비밀번호를 credentials 및 CSRF 헤더와 함께 보낸다.
2. 서버는 Refresh Cookie를 발급하고 body의 Access Token과 만료 시각을 반환한다. FE는 body 값만 token vault에 저장한다.
3. 기존 `['auth', 'me']`와 `['private', ...]` cache를 취소·제거한다.
4. `/users/me`를 조회해 현재 사용자 cache를 만든 뒤 `authenticated`로 전이한다.
5. 원래 목적지 또는 기본 진입점으로 이동한다.

로그인 성공 후 `me`가 일시적으로 실패하면 세션 자체를 즉시 폐기하지 않고 복구 UI를 제공한다. `AUTH_005`라면 인증 정리를 수행한다.

### 갱신

1. transport는 body 없이 credentials 및 CSRF 헤더를 보내며 브라우저가 HttpOnly Cookie를 자동 첨부한다.
2. 서버는 Refresh Token을 회전해 Cookie를 교체하고 새 Access Token과 만료 시각을 body로 반환한다.
3. FE는 Access Token과 만료 시각만 vault에 교체한다. Refresh Token 원문은 어떤 JavaScript 경계에도 들어오지 않는다.

### 로그아웃

1. transport는 body 없이 credentials 및 CSRF 헤더를 보내며 서버가 Refresh Cookie를 만료시킨다.
2. FE는 응답 성공·실패와 무관하게 진행 중인 개인 Query를 취소한 뒤 token vault와 개인 cache를 제거한다.
3. 공개 기본 주소 `/`로 replace 이동해 로그인 전 랜딩을 보여준다.

### 공개 랜딩과 온보딩 라우트

- `/`는 인증 phase 판정이 끝난 뒤 anonymous이면 공개 랜딩, authenticated이면 기존 홈 셸을 렌더링한다. `bootstrapping`과 `bootstrap-error` 동안 랜딩 카피를 먼저 노출하지 않는다.
- 보호 URL의 anonymous 접근은 기존 `AuthGate`가 `/login`으로 보내고 안전한 내부 원래 목적지를 보존한다.
- `/onboarding`은 `AuthGate` 밖으로 나가지 않되 일반 앱 셸 안에는 넣지 않아 하단 탭을 노출하지 않는다. 현재 사용자와 회원가입 직후 admission이 모두 일치할 때만 렌더링한다.
- `/profile/guide`는 인증 앱 셸의 마이페이지 하위 경로다. 같은 콘텐츠 컴포넌트를 `guide` 모드로 사용하며 닫기와 안전한 뒤로 가기는 `/profile`로 수렴한다.
- 캐러셀 상태는 화면의 React state 한 곳에서만 소유한다. URL과 history에는 장 번호를 저장하지 않으며, 비순환 버튼·수평 swipe·방향키가 같은 인접 장 전이를 호출한다.

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
| `AUTH_010` | 가입정보 화면 | 닉네임 중복 상태로 되돌리고 다른 닉네임 확인 유도 |
| `AUTH_011` | 가입정보 화면 | 최종 가입 재시도 없이 이메일만 미리 채운 로그인 화면으로 이동 |
| 네트워크 실패 | 각 화면/부트스트랩 | 자격 없음과 구분, 입력 보존, 명시적 재시도 |

클라이언트 분기는 서버 `message` 문자열이 아니라 안정적인 `error.code`를 사용한다.

## 12. Cookie·CORS·CSRF 계약

### Cookie

- `HttpOnly`: 필수
- `Secure`: 운영 필수; 로컬 개발 예외 방식은 서버 설정으로 명시
- `SameSite=Lax`
- 운영 이름은 `__Host-openmd_refresh`, 로컬 HTTP 이름은 `openmd_refresh`
- `Domain`을 지정하지 않는 host-only cookie
- `Path=/`
- `Max-Age`는 서버 Redis session의 절대 만료까지 남은 시간과 일치
- 회전 시 새 cookie를 동일 속성으로 덮어쓰기
- 로그아웃 시 발급 때와 동일한 Name, Domain, Path로 만료시키기

### CORS

- 서버는 브라우저 세션 경로에만 credentialed 요청을 허용한다.
- `Access-Control-Allow-Origin`은 와일드카드가 아니라 환경설정의 정확한 origin이다.
- FE Axios는 로그인·refresh·logout에서만 `withCredentials: true`를 사용한다.

### CSRF

브라우저 로그인·refresh·logout은 고정 `X-OpenMD-CSRF: 1` 헤더를 보내며 서버는 이 헤더와 정확한 `Origin`을 함께 검증한다. 검증 실패는 `AUTH_009`로 처리한다. 고정 헤더는 브라우저의 CORS preflight를 유발하고, 서버가 승인하지 않은 origin에서는 실제 credentialed 요청을 만들 수 없게 하는 장치다.

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

## 15. 브라우저 Cookie 계약 반영 체크리스트

- [x] 웹 로그인 응답에서 Refresh Token 원문 제거 및 `Set-Cookie` 발급
- [x] refresh 요청 body 제거 및 성공 시 회전 Cookie 재발급
- [x] logout 요청 body 제거 및 성공·멱등 결과에서 Cookie 만료
- [x] Access Token, `accessExpiresAt`, `refreshExpiresAt` body 응답 유지
- [x] 운영·로컬 Cookie 속성 확정
- [x] credentialed CORS와 정확한 allowed origin 반영
- [x] Origin 검증과 `X-OpenMD-CSRF: 1` 헤더 계약 반영
- [x] 웹과 네이티브의 세션 endpoint surface 분리
- [x] strict fail-closed RTR와 Web Locks 기반 FE 교차 탭 직렬화 적용
- [x] 새로고침 Cookie refresh 및 `/users/me` 복구 적용

## 16. 열린 질문

- Refresh Token의 최종 절대 수명
- 로그아웃 직후 남은 5분 이내 Access Token을 서버에서 차단할 수준
- 로그인 성공 후 `me`가 네트워크 오류로 실패했을 때의 최종 화면 문구와 재시도 UX
- Web Locks를 지원하지 않는 브라우저까지 교차 탭 직렬화를 확장할지 여부

## 17. 구현 검증 기준

### 정적 검증

- `pnpm typecheck`
- `pnpm lint`
- `pnpm build`

### 인증 동작 검증

- Refresh Token 원문은 응답 body, token vault와 어떤 JavaScript 저장소에도 노출되지 않는다.
- 새로고침하면 Cookie refresh와 `/users/me`를 통해 인증 상태를 복구한다.
- 여러 보호 요청의 동시 401이 한 탭에서 refresh 한 번으로 합쳐진다.
- refresh의 `AUTH_005` 실패 뒤 원 요청이 반복되지 않고 개인 cache가 제거된다.
- refresh의 network/5xx 실패는 현재 자격과 cache를 유지하고 다음 명시적 요청에서 다시 시도할 수 있다.
- bootstrap의 network/5xx 실패는 anonymous로 오판하지 않고 재시도 화면을 제공한다.
- 로그아웃 API 실패 상황에서도 로컬 개인 데이터와 Access Token이 제거된다.
- 로그인한 사용자가 바뀌어도 이전 사용자의 Query cache가 노출되지 않는다.
- 로그인 후 `me` network/5xx 실패는 자격을 즉시 폐기하지 않고 명시적으로 재시도할 수 있다.
- 가입용 인증 메일 요청은 이메일만 포함하고 비밀번호는 최종 가입 요청 전까지 React 메모리에만 남는다.
- 최종 가입의 network/코드 없는 5xx는 refresh를 한 번만 호출하고, `AUTH_011` 또는 복구 실패는 최종 가입을 반복하지 않고 로그인 화면으로 이동한다.
- 가입 복구용 로그인 Router state에는 이메일과 고정 안내 식별자만 포함하며 비밀번호와 `signUpToken`은 포함하지 않는다.
- Router의 원래 목적지 복귀가 외부 URL redirect를 허용하지 않는다.
- anonymous `/`는 승인된 공개 랜딩만, authenticated `/`는 기존 홈을 보여준다.
- 최종 가입과 가입 복구 성공만 계정별 표시값을 기록하고 `/onboarding` admission을 만들며, 일반 로그인과 세션 복원은 만들지 않는다.
- 자동 온보딩을 나가거나 새로고침한 뒤 Router state만으로 다시 열리지 않는다.
- `/profile/guide` 열람 전후로 계정별 자동 표시값이 바뀌지 않는다.

### 현재 브라우저·보안 검증

- `localStorage`, `sessionStorage`, IndexedDB, Query cache와 Router state에 토큰 원문이 남지 않는다.
- 콘솔과 오류 객체에 request config를 복사하거나 token 원문을 기록하지 않는다.

### Cookie 계약 검증

- DevTools에서 Refresh Cookie가 `HttpOnly`, 운영 `Secure`, 승인된 `SameSite`와 Path를 가진다.
- credentialed CORS는 승인 origin만 허용한다.
- 승인된 CSRF 방어 없이 cookie 기반 refresh/logout이 성공하지 않는다.
- 브라우저 저장소와 콘솔·오류 추적 payload에 토큰 원문이 남지 않는다.
- Web Locks 지원 환경에서 여러 탭의 refresh 요청이 직렬화된다.
