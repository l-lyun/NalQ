---
document_type: trd
status: draft
scope: app
---

# [TRD · App] Expo WebView 앱 셸 설계

- 상태: 1단계 구현 동기화 — 실기기 검증 필요
- 소유 애플리케이션: `app/`
- 관련 제품 기반: [NalQ 제품 기반](../../../docs/product.md)
- 관련 UX: [홈 화면](../../../docs/ux/screen-home.md), [학습 화면](../../../docs/ux/screen-learning.md), [인증 흐름](../../../docs/ux/flow-authentication.md)
- 관련 Contract: [인증 API](../../../docs/contracts/contract-api-authentication.md)
- 관련 App TRD: [WebView 퀴즈 상태](trd-quiz-solving.md)
- 관련 Web TRD: [웹 인증 상태·토큰·API 통합](../../../web/docs/trd/trd-authentication.md)

## 문서 책임

이 문서는 Expo 앱이 원격 NalQ 웹 애플리케이션을 하나의 WebView로 실행할 때 네이티브 셸이 소유할 URL, 수명주기, 시스템 UI, 뒤로 가기와 복구 경계를 정의한다.

웹 화면의 라우팅·인증 상태·API 호출과 사용자 가시 기능은 `web/` 및 상위 원장이 책임진다. 이 문서는 웹 화면을 React Native 화면으로 다시 구현하거나 제품 동작을 새로 정하지 않는다.

## 목표와 비범위

### 목표

- 현재 반응형 웹 화면과 브라우저 HttpOnly Cookie 인증을 그대로 재사용한다.
- 앱 시작부터 웹 첫 문서 표시까지 빈 화면 대신 명시적인 네이티브 로딩 상태를 제공한다.
- 최초 및 이후 동일-origin 최상위 문서의 network·HTTP 실패와 WebView 렌더러 종료를 사용자가 재시도할 수 있게 한다.
- 앱 내부 URL, 외부 URL과 지원하지 않는 스킴의 경계를 명시한다.
- Android 시스템 뒤로 가기와 iOS WebView 탐색을 브라우저 history에 연결한다.
- 앱별 URL과 보안에 민감하지 않은 빌드 설정을 환경에서 주입한다.

### 비범위

- 푸시 알림, 카메라, 파일 선택, 공유, 생체 인증과 백그라운드 작업
- React Native에서 NalQ API를 직접 호출하거나 Refresh Token을 네이티브 저장소로 옮기는 것
- Expo Router 기반의 여러 네이티브 화면과 네이티브 하단 탭
- 네이티브-웹 메시지 브리지와 임의 JavaScript 주입
- 오프라인 콘텐츠 캐시와 오프라인 편집·동기화
- App/Universal Link, OAuth 복귀와 Notion 외부 인증의 최종 계약
- 스토어 메타데이터, 서명, 제출과 운영 배포

## 현재 구조와 제약

### App

- `app/`은 Expo SDK 57, React Native 0.86과 `react-native-webview` 13.16.1을 사용한다.
- `App.tsx`는 검증된 환경 URL을 단일 `OpenMdWebView`에 전달하고 구성 오류를 네이티브 상태로 표시한다.
- 공개 웹 주소 자리인 `EXPO_PUBLIC_WEB_URL` 예시와 개발 기본값 `http://localhost:5173`을 사용한다.
- `src/shell/`이 로딩·동일-origin 최상위 문서 오류·재시도, 동일 origin URL 정책, 외부 링크, Android 뒤로 가기와 플랫폼별 WebView 복구를 소유한다.
- Expo Router와 네이티브-웹 메시지 브리지는 사용하지 않는다.

### Web

- 웹은 `BrowserRouter` 안에서 로그인·가입·이메일 인증, 홈, 학습과 퀴즈·복습 route를 소유한다.
- 제품의 세 번째 최상위 목적지는 현재 웹 route가 없고 하단 항목도 연결되지 않았다. 앱 셸에서 이를 임시 네이티브 화면으로 만들지 않는다.
- 웹 인증은 `/api/v1/auth/web/**`와 WebView cookie jar의 HttpOnly Refresh Cookie를 사용하고 Access Token만 JavaScript 메모리에 둔다.
- 웹 전역 스타일은 기본적으로 CSS safe-area 값을 소비하며, 네이티브 셸이 inset을 소비한 경우 `data-openmd-safe-area="consumed"`로 이중 적용을 막는 경계를 이미 둔다.
- 홈·학습·퀴즈 화면은 `100dvh`, 단일 세로 스크롤, 320px 이상 폭과 WebView safe area를 전제로 한다.

### 배포와 인증

- 운영 WebView와 API는 HTTPS를 사용해야 한다.
- 운영에서는 웹과 API를 같은 origin으로 reverse proxy하는 구성을 우선한다. 별도 host가 필요하면 최소한 same-site topology, 정확한 CORS/브라우저 Origin 허용목록과 Cookie 속성을 함께 검증한다.
- 웹은 `BrowserRouter`를 사용하므로 운영 호스팅은 `/learning/...`, `/quiz-sets/...` 같은 직접 접근과 새로고침을 `index.html`로 돌리는 SPA fallback을 제공해야 한다.
- 로컬 실기기에서 `localhost`는 Mac이 아니라 기기 자신을 가리킨다. 웹 URL과 웹 번들에 주입되는 API URL은 Mac의 LAN 주소나 HTTPS 개발 주소를 사용하고 서버의 정확한 Origin 허용목록도 같은 값으로 맞춰야 한다.
- HTTP 로컬 개발은 운영 `__Host-` Cookie를 사용할 수 없다. 서버 개발 설정의 별도 Cookie 이름과 `Secure=false` 예외를 사용한다.

## 기술 설계

### 1. 런타임 구조

1차 앱은 `App.tsx` 아래 하나의 `OpenMdWebView`만 둔다.

```text
App
├─ system bar configuration
├─ native shell state
│  ├─ loading
│  ├─ ready
│  └─ recoverable error
└─ OpenMdWebView
   └─ deployed NalQ web
      ├─ BrowserRouter
      ├─ browser Cookie authentication
      └─ web-owned screens and API clients
```

Expo Router는 네이티브 화면이 하나인 1차 셸에는 추가하지 않는다. 네이티브 목적지가 둘 이상 생기거나 incoming link를 네이티브 route로 분배해야 할 때 다시 검토한다.

### 2. 환경 URL

- 앱은 `process.env.EXPO_PUBLIC_WEB_URL`을 읽고 값이 없으면 개발 기본값 `http://localhost:5173`을 사용한다. `EXPO_PUBLIC_` 값은 번들에 포함되는 공개 설정이므로 비밀을 넣지 않는다.
- 시작 시 URL을 파싱하고 scheme과 host를 검증한다. 운영 빌드는 `https:`만 허용하고, 개발 빌드만 명시된 HTTP host를 허용한다.
- 값이 유효하지 않거나 운영 빌드에서 기본 HTTP 주소를 덮어쓰지 않으면 WebView를 열지 않고 구성 오류 상태를 표시한다.
- 허용 여부는 문자열 prefix가 아니라 파싱한 `URL.origin`의 정확한 일치로 판단한다.

### 3. 탐색과 외부 URL

- 초기 URL과 같은 웹 origin의 `http(s)` 문서 탐색만 WebView 안에서 허용한다.
- API origin으로의 XHR/fetch는 웹 코드가 소유한다. API host 자체를 사용자가 보는 최상위 문서로 열지는 않는다.
- 다른 `http(s)` origin과 `mailto:`, `tel:`은 OS의 외부 열기 API로 넘긴다.
- `javascript:`, `file:`, `data:`와 허용하지 않은 custom scheme은 차단한다.
- Android 첫 요청은 `onShouldStartLoadWithRequest`가 호출되지 않을 수 있으므로 초기 환경 URL 검증을 별도로 수행한다.
- 새 창 요청은 별도 WebView를 만들지 않고 같은 URL 정책으로 외부 열기 또는 차단한다.

1차 구현에는 웹-네이티브 메시지 브리지를 만들지 않는다. 이후 메시지가 필요하면 메시지 이름, 방향, payload schema, 버전과 허용 origin을 별도 계약으로 정하고 수신 payload를 신뢰하지 않는다.

### 4. 뒤로 가기

- `onNavigationStateChange` 또는 로드 진행 이벤트에서 `canGoBack`을 네이티브 상태로 유지한다.
- Android 뒤로 가기에서 WebView history가 있으면 `goBack()`하고 앱 종료를 막는다.
- history가 없으면 이벤트를 소비하지 않아 OS 기본 앱 종료 동작을 따른다.
- 웹이 이미 `popstate`, 작성 중 이탈 확인과 화면 내부 history를 관리하는 경우 네이티브에서 같은 이동을 다시 실행하지 않는다.
- iOS의 back/forward gesture는 가입 2단계와 작성 중 이탈 확인이 검증되지 않은 1차 셸에서는 비활성화한다. 이후 실제 기기에서 검증한 뒤 활성화를 다시 제안할 수 있다.
- Android predictive back은 현재 앱 설정을 유지하고 WebView history 연결을 검증한 뒤 별도 활성화한다.

### 5. 로딩과 복구 상태

네이티브 셸은 전체 웹 애플리케이션의 데이터 오류를 해석하지 않고 WebView 문서 자체의 실행 실패만 처리한다.

| 상태 | 소유 계층 | 처리 |
| --- | --- | --- |
| 앱 시작과 첫 문서 로드 | Native | 스플래시 뒤 짧은 로딩 상태 |
| 동일-origin 최상위 문서 network/HTTP 실패 | Native | 간단한 설명과 실패한 문서 URL의 `다시 시도` |
| WebView renderer/content process 종료 | Native | 복구 안내 후 WebView 재생성 또는 reload |
| 인증 bootstrap, API 401/5xx와 화면 데이터 오류 | Web | 기존 웹 상태와 재시도 사용 |
| 사용자가 누른 외부 URL을 열 수 없음 | Native | 현재 WebView를 유지하고 짧은 오류 안내 |

- 최초 로딩과 이후 동일-origin 최상위 문서 탐색 실패에는 전체 native error를 표시한다. 웹이 이미 표시된 뒤 subresource/API 하나가 실패했다고 전체 WebView를 덮지 않는다.
- 재시도는 실패 URL을 동일-origin 정책으로 다시 검증한 뒤 그 최상위 문서 URL로 WebView 인스턴스를 새 key로 재생성한다. 실패 URL이 안전하지 않으면 검증된 초기 URL만 사용한다.
- 별도 네트워크 감지 라이브러리는 1차 범위에 추가하지 않는다. 연결 상태 추정보다 실제 문서 로드 결과를 기준으로 복구한다.
- 오프라인 문서 캐시는 제공하지 않으므로 캐시된 화면을 정상 최신 상태처럼 보장하지 않는다.

### 6. Cookie와 웹 저장소

- 로그인·가입·refresh·logout은 현재 웹 코드를 그대로 실행하고 WebView cookie jar가 `Set-Cookie`와 후속 Cookie 전송을 담당한다.
- React Native 코드에서 Refresh Token을 읽거나 Cookie header를 직접 만들지 않는다.
- 1차 앱은 하나의 비-incognito WebView를 유지하며 정상 탐색 중 불필요하게 remount하지 않는다. remount는 복구 동작으로만 제한한다.
- 네이티브 HTTP 클라이언트나 Safari cookie store와 공유하지 않으므로 수동 Cookie manager와 iOS `sharedCookiesEnabled`에 의존하지 않는다.
- 별도 site의 third-party Cookie를 요구하지 않는 배포 topology를 사용한다.
- 앱 종료·재실행, 로그인·refresh 회전과 logout 뒤 Cookie 유지·삭제는 iOS와 Android 실기기 E2E로 확인한다. 플랫폼별 WebView cookie jar 차이를 코드 추정만으로 통과 처리하지 않는다.
- WebView `localStorage`의 퀴즈 생성 조건과 풀이 메모리 수명은 [WebView 퀴즈 상태 TRD](trd-quiz-solving.md)를 따른다.

### 7. Safe area, 키보드와 시스템 UI

- 1차 기본안은 WebView를 edge-to-edge로 두고 현재 웹의 `viewport-fit=cover`와 SEED safe-area 변수가 inset을 한 번 소비하게 하는 것이다.
- 네이티브 셸은 StatusBar 글자 스타일과 배경 역할만 맞추고 WebView에 별도 상·하단 padding을 중복 적용하지 않는다.
- Android WebView에서 CSS safe-area가 시스템 bar를 보호하지 못하는 것이 실기기에서 확인되면 네이티브 소유 방식으로 전환한다. 이때 네이티브 inset을 적용하고 웹은 첫 paint 전에 `data-openmd-safe-area="consumed"`를 설정하는 명시적인 셸 신호를 소비해야 한다.
- runtime에 임의 JavaScript를 뒤늦게 주입해 safe area를 바꾸는 방식은 첫 화면 점프와 이중 적용 가능성 때문에 기본안으로 쓰지 않는다.
- 키보드가 열린 상태에서 가입 폼, 붙여넣기 textarea와 퀴즈 하단 행동이 가려지지 않는지 두 플랫폼에서 확인한다.

### 8. 구현 파일 구조

1단계 구현은 다음 최소 구조를 사용한다.

```text
app/
  App.tsx
  src/
    shell/
      OpenMdWebView.tsx
      ShellStateView.tsx
      webUrl.ts
      navigationPolicy.ts
```

- `App.tsx`: system bar와 셸 조합
- `OpenMdWebView.tsx`: WebView ref, load/navigation/process/back lifecycle
- `ShellStateView.tsx`: native loading·오류·재시도 UI
- `webUrl.ts`: 환경 URL 파싱과 빌드별 검증
- `navigationPolicy.ts`: 내부·외부·차단 URL 분류

한 번만 쓰는 작은 로직은 `App.tsx`에 유지하고 빈 추상화 계층을 먼저 만들지 않는다.

## 검증

### 정적 검증

- `cd app && pnpm exec tsc --noEmit`
- `cd app && pnpm test`
- `git diff --check`

### 자동화된 정책 단위

- 개발 환경 URL 없음은 localhost 기본값으로 복구하고, 잘못된 URL과 운영 HTTP URL은 거절한다.
- 같은 origin의 route만 내부 탐색으로 분류한다.
- 외부 HTTPS, `mailto:`와 `tel:`은 외부 열기로 분류한다.
- `javascript:`, `file:`, `data:`와 미등록 custom scheme을 차단한다.

Android `BackHandler`와 WebView ref의 실제 결합은 렌더러가 필요한 동작이므로 실기기 수동 검증에서 확인한다.

### 실기기 수동 검증

- iOS와 Android에서 앱 시작 → 로그인 → 종료 → 재실행 뒤 세션 bootstrap이 복구된다.
- 로그인, refresh 회전과 logout 뒤 HttpOnly Cookie가 각 플랫폼에서 유지·갱신·삭제된다.
- 홈 → 학습 → 하위 입력 → 시스템 뒤로 가기의 history와 작성 중 이탈 확인이 중복 실행되지 않는다.
- 외부 HTTPS, `mailto:`와 `tel:`이 WebView를 이탈해 적절한 앱으로 열리고 복귀 시 기존 화면을 유지한다.
- 최초 로드와 이후 동일-origin 최상위 문서 탐색의 네트워크 중단·HTTP 오류, renderer 종료 뒤 재시도가 동작한다.
- 노치, Android edge-to-edge, 홈 인디케이터와 키보드에서 상단 제목·하단 탭·고정 행동이 가려지거나 이중 padding되지 않는다.
- 320px 상당의 작은 viewport, 큰 글자와 긴 한국어 문구에서 웹 화면이 잘리거나 수평 스크롤되지 않는다.

## 단계 제안

### 1단계 — 지금 구현할 최소 셸

- 검증된 `EXPO_PUBLIC_WEB_URL`
- 단일 WebView와 동일-origin allowlist
- native loading·동일-origin 최상위 문서 오류·재시도
- 외부 URL 분리
- Android back/history
- WebView renderer 종료 복구
- 현재 웹 브라우저 Cookie 인증 E2E

### 2단계 — 실제 필요가 생길 때

- App/Universal Link와 WebView route 전달
- Notion/OAuth 외부 브라우저 복귀
- 파일 선택·다운로드·공유
- 푸시 알림과 알림 route
- versioned web-native message bridge
- 네이티브 API 소비와 OS 보안 저장소

## 열린 질문

- 1차 출시를 iOS와 Android에 동시에 할지, 한 플랫폼부터 실기기 검증할지
- 운영 웹/API를 완전한 same-origin으로 배포할지, same-site의 별도 host로 배포할지
- iPad를 지원할지와 현재 `supportsTablet=true`를 유지할지
- 현재 중립적인 native loading·error 문구를 최종 문구로 확정할지와 별도 브랜드 자산이 필요한지
- iOS back/forward gesture를 가입·작성 중 이탈 흐름에도 활성화할지
- Android에서 웹 safe-area 소유가 충분한지, native 소유로 전환할지
- 앱 이름, slug, iOS bundle identifier와 Android package identifier

## 현재 공식 기술 근거

- [Expo WebView](https://docs.expo.dev/versions/latest/sdk/webview/): Expo SDK 57의 권장 `react-native-webview` 버전과 기본 사용법
- [Expo development builds](https://docs.expo.dev/develop/development-builds/introduction/): 실제 앱 개발과 native 설정 검증을 위한 development build 권장
- [Expo environment variables](https://docs.expo.dev/guides/environment-variables/): `EXPO_PUBLIC_` 값의 주입 방식과 공개 설정 경계
- [Expo system bars](https://docs.expo.dev/develop/user-interface/system-bars/): edge-to-edge와 safe area 검증 기준
- [React Native WebView Guide](https://github.com/react-native-webview/react-native-webview/blob/master/docs/Guide.md): Cookie, 웹 탐색과 플랫폼 뒤로 가기
- [React Native WebView Reference](https://github.com/react-native-webview/react-native-webview/blob/master/docs/Reference.md): navigation, loading/error와 renderer lifecycle API
