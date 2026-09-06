---
document_type: trd
status: implementation-synced
scope: app
---

# [TRD · App] 푸시 브리지와 설치 자격 저장 기반

- 상태: handshake·보안 저장 기반 구현, `push-v1` 기능 비활성
- 소유 애플리케이션: `app/`
- 제품 원장: [퀴즈 생성 결과 OS 푸시 PRD](../../../docs/prd/prd-quiz-push-notifications.md)
- 공유 계약: [푸시 API·브리지 계약](../../../docs/contracts/contract-api-push-notifications.md)
- 관련 앱 TRD: [Expo WebView 앱 셸](trd-webview-shell.md)

## 책임과 현재 범위

이 문서는 앱의 설치 자격을 기기 보안 저장소에 내구 저장하고, 검증된 WebView 문서와 버전 협상을 시작하는 기반 구현을 설명한다. 웹의 인증 상태 변경, HTTP 등록·해제, OS 권한 요청, Expo push token 발급, 수신·foreground 처리와 푸시 선택 이동은 후속 구현이다.

현재 앱은 `HELLO.capabilities=[]`만 광고한다. 따라서 웹과 native에 브리지 코드가 있어도 `push-v1` 등록 기능은 활성화되지 않는다. `AUTH_STATE` parser는 session, envelope와 payload에 중복된 동일 epoch, phase·userId를 검증하지만 capability 미협상 상태에서는 결과를 적용하지 않는다.

## 문서 수명과 handshake

1. 같은 origin의 새 최상위 문서 로드가 시작되면 native가 새 `bridgeSessionId`를 만들고 이전 HELLO·인증 상태를 폐기한다.
2. 웹은 `bridgeSessionId=null`, `authEpoch=0`, `payload.versions=[1]`인 `WEB_READY`를 전송한다.
3. native는 envelope, UUID, 지원 버전과 8KiB UTF-8 크기 상한을 검증한다.
4. native는 현재 문서 session과 `WEB_READY.messageId`를 `replyTo`로 담은 `HELLO`를 반환한다. React Strict Mode 재실행이나 bounded retry로 한 문서에서 새 `WEB_READY`가 와도 session은 유지한다.
5. native → web 전달은 `nalq:native-message` 고정 `CustomEvent`의 `detail`에 JSON 문자열을 넣는다. 값은 `JSON.stringify`로 문자열 literal화하며 메시지 내용을 실행 코드로 사용하지 않는다.

가장 최근 `WEB_READY`에 대한 HELLO 하나만 캐시한다. 같은 요청 재전송에는 같은 HELLO를 반환하고, 새 요청은 같은 session의 새 HELLO로 교체해 메모리가 무한 증가하지 않게 한다.

## origin과 최상위 문서 검증 경계

앱은 `onMessage.nativeEvent.url`을 파싱하고 구성된 WebView origin과 정확히 일치할 때만 메시지를 해석한다. prefix 비교나 메시지 payload가 주장하는 origin은 신뢰하지 않는다.

`react-native-webview` 13.16.1의 공개 `onMessage` 사건에는 `isTopFrame`이 없다. iOS 구현은 송신 frame URL을 전달하지만 top-frame 여부는 전달하지 않는다. Android의 최신 WebMessageListener 경로도 내부에서 받은 `isMainFrame`을 React Native 사건에 포함하지 않으며, 구형 fallback 경로는 현재 WebView URL을 사용한다. 웹의 `window.self === window.top` 확인은 정상 발신을 제한하지만 native-level 출처 증명은 아니다.

따라서 이번 기반은 installation key, token, 사용자 정보 등 민감한 값을 브리지로 보내지 않고 capability를 비워 둔다. `push-v1` 활성화 전에는 두 플랫폼에서 main-frame 정보를 native까지 보존하는 WebView 경로나 동등한 격리를 구현하고 실기기로 검증해야 한다.

## SecureStore 상태

앱 시작 시 다음 단일 versioned 상태를 `nalq.push.state.v1` key로 초기화한다.

```text
PushStorageState
├─ installation
│  ├─ installationId: UUID
│  ├─ installationKey: 32-byte random base64url
│  ├─ createdAt: UTC instant
│  └─ tokenVersion
├─ activeBinding
├─ pendingRegistration (authEpoch·tokenVersion snapshot)
└─ pendingRevokes[]
```

- UUID와 32바이트 난수는 `expo-crypto`로 만든다.
- 상태는 `expo-secure-store`에 저장하고 iOS는 `AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY` 접근성을 사용한다. 사용자인증 prompt를 요구하지 않아 bootstrap·재시도를 막지 않으며 다른 기기 backup 복원 대상으로 의도하지 않는다.
- 읽기·초기 생성·수정은 process 안의 단일 promise queue로 직렬화한다. write가 성공하기 전에 caller에게 성공을 반환하지 않는다.
- 동시 bootstrap은 설치 자격을 한 번만 생성한다. 동시 변경은 매번 직전 durable 상태를 다시 읽어 pending 변경 유실을 막는다.
- 저장 JSON, UUID, base64url key, UTC instant, revision·epoch가 잘못되면 자동으로 새 설치 자격을 만들어 덮지 않고 실패한다. 이는 기존 key를 잃고 서버 연결을 고아로 만드는 조용한 회전을 방지한다.
- SecureStore 준비 실패는 WebView 이용을 막지 않는다. 다음 푸시 bootstrap에서 재시도하며, 저장 성공 전에는 등록 capability를 활성화하지 않는다.

SecureStore와 Expo token이 삭제·재설치·backup에서 유지되는지는 플랫폼 고정 가정이 아니다. 공유 계약의 새 installation 복구 규칙을 따르고 실제 기기에서 확인한다.

## 파일 구조

```text
app/
  App.tsx
  src/
    push/
      bridgeProtocol.ts
      nativePushStorage.ts
      pushStorage.ts
    shell/
      OpenMdWebView.tsx
  tests/
    pushFoundation.test.cjs
```

- `bridgeProtocol.ts`: 순수 envelope parser, session·epoch 결정, 고정 이벤트 직렬화
- `pushStorage.ts`: 플랫폼 독립 상태 schema, 검증과 직렬 repository
- `nativePushStorage.ts`: Expo Crypto·SecureStore adapter
- `OpenMdWebView.tsx`: 문서 수명주기와 실제 handshake 연결
- `App.tsx`: installation 자격 bootstrap

## 검증

자동 검증은 다음을 다룬다.

- 초기 null-session `WEB_READY`, exact schema와 UTF-8 8KiB 상한
- HELLO session·reply 상관관계, 빈 capability, 고정 문자열 event
- 다른 session, 역행 epoch, 같은 epoch의 다른 사용자 거절
- capability 미협상 `AUTH_STATE` 비활성
- installation 동시 생성 한 번, 동시 pending 변경 보존
- durable write 실패의 성공 오보고 방지
- 손상 상태·잘못된 key·시각을 자동 회전하지 않고 실패
- 32-byte base64url encoding

실기기에서는 iOS·Android SecureStore 생성·재실행 유지, Android fallback을 포함한 iframe 출처, WebView reload의 session 교체와 Strict Mode 재시도를 별도로 확인해야 한다. 이 검증과 실제 `push-v1` capability 활성화는 후속 단위다.
