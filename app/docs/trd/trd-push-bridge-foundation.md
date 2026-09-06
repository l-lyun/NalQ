---
document_type: trd
status: implemented
scope: app
---

# [TRD · App] 푸시 브리지와 기기 등록·해제

- 상태: 앱 등록·해제 구현 및 자동 검증, 실제 개발 빌드·기기 검증 전
- 소유 애플리케이션: `app/`
- 제품 원장: [퀴즈 생성 결과 OS 푸시 PRD](../../../docs/prd/prd-quiz-push-notifications.md)
- 공유 계약: [푸시 API·브리지 계약](../../../docs/contracts/contract-api-push-notifications.md)
- 관련 앱 TRD: [Expo WebView 앱 셸](trd-webview-shell.md)

## 책임과 현재 범위

이 문서는 Expo 앱이 OS 알림 권한과 Expo push token을 취득하고, 설치 자격·등록·해제 의도를 보안 저장소에 보존하며, 검증된 WebView 최상위 문서를 통해 웹 소유 HTTP 클라이언트에 전달하는 구조를 설명한다.

앱은 `push-v1` capability를 광고한다. 웹이 협상한 같은 bridge session과 인증 epoch에서만 상태 조회·등록 결과를 적용하고, 사용자 Bearer token은 native로 전달하지 않는다. 푸시 선택과 화면 이동은 이번 구현에 포함하지 않는다.

## 문서 수명과 격리된 handshake

1. 같은 origin의 새 최상위 문서 로드가 시작되면 native가 새 `bridgeSessionId`와 2개 UUID로 구성한 문서 nonce를 만들고, 이전 HELLO·인증 상태·retry timer를 폐기한다.
2. `injectedJavaScriptBeforeContentLoaded`는 정확한 origin이고 `window.self === window.top`인 문서에만 `window.NalQNativeBridge.postMessage` facade를 설치한다. Android에서 이 주입 시점만 신뢰하지 않고 `onLoad`로 확인한 최상위 문서에 같은 shim을 다시 설치한다.
3. facade는 WebView의 raw `ReactNativeWebView.postMessage`에 `{ transportVersion, documentNonce, message }` exact wrapper를 추가한다. nonce는 closure 변수이며 facade 함수의 `toString()`이나 DOM event로 공개하지 않는다. raw 메시지, 다른 nonce와 8KiB를 넘는 envelope는 native에서 거절한다.
4. native는 `onLoad` 전 bridge 입력을 받지 않는다. 이전 문서에서 늦게 실행된 주입이 새 session에 인증 상태를 먼저 적용할 수 없게 한다. shim 설치가 완료되면 credential이 없는 `nalq:native-ready` event로 웹 handshake를 다시 시작한다.
5. 웹은 null session의 `WEB_READY`를 보내고 native는 현재 문서 session, `push-v1`, 요청 `replyTo`를 담은 HELLO를 돌려준다. 같은 문서의 Strict Mode 재시도는 session을 바꾸지 않는다.
6. native → web 메시지는 현재 origin·top frame·nonce를 다시 검사하는 private dispatcher를 거쳐 `nalq:native-message`에 JSON 문자열로 전달된다. 이전 문서에 queue된 credential 메시지는 새 nonce에서 거절된다.

Cross-origin iframe은 top facade와 event에 접근할 수 없고 자신의 raw bridge로 nonce wrapper를 만들 수 없다. 동일-origin iframe은 브라우저 same-origin 정책상 최상위 문서의 DOM과 JavaScript 권한이 같으므로 앱만으로 별도 privilege를 만들 수 없다. 따라서 동일-origin script·frame은 배포 웹의 CSP와 공급망을 포함한 신뢰 경계다.

## SecureStore 상태와 완료 기준

단일 versioned 상태를 `nalq.push.state.v1` key로 저장한다.

```text
PushStorageState
├─ installation
│  ├─ installationId / installationKey / createdAt
│  └─ tokenVersion
├─ activeBinding
│  ├─ bindingId / userId / revision
│  └─ platform / permission / pushToken / tokenVersion
├─ pendingRegistration
│  └─ operationId / issuedAt / user·epoch / revision·token snapshot
├─ pendingRevokes[]
│  └─ 당시 installation 자격 / binding / revision / operation
└─ lastRegistrationAck
   └─ 최근 완료 operation / account·epoch / binding·revision
```

- installation UUID와 32바이트 base64url key는 `expo-crypto`로 만든다.
- 저장은 `expo-secure-store`와 iOS `AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY`를 사용한다. 모든 read-modify-write는 process 안의 단일 promise queue로 직렬화하고 durable write가 완료되기 전에 성공 ACK를 보내지 않는다.
- 등록 성공은 active binding, pending 제거와 `lastRegistrationAck`를 한 번에 저장한 뒤 `PUSH_REGISTER_ACK`를 보낸다. ACK가 유실돼 같은 성공 결과가 오면 저장된 완료 항목과 정확히 일치할 때 ACK를 다시 보낸다.
- 로그아웃·탈퇴 시작은 현재 사용자 active binding을 당시 installation key와 함께 pending revoke로 먼저 저장한다. 그 뒤 `SESSION_ENDING_ACK`와 인증 독립 `PUSH_REVOKE`를 보낸다. 성공·404 no-op 결과를 받은 항목만 제거한다.
- 등록 응답이 유실돼 bindingId를 아직 모르는 상태에서 session 종료가 시작되면 해당 pending registration을 지우지 않는다. 앱은 추측한 binding으로 해제하지 않고 다음 동일 계정 상태 조회 또는 계정 전환 등록에서 조정한다. 서버 session 종료와 등록 커밋의 경합 보호가 이 공백의 서버측 필수 방어다.
- 저장 JSON·UUID·key·시각·revision이 손상되면 자동으로 새 설치를 덮어쓰지 않고 실패한다. SecureStore 준비 실패는 WebView 사용을 막지 않으며 다음 등록 trigger에서 재시도한다.

## 권한·토큰과 foreground

- Android는 `quiz-results` notification channel을 권한·토큰 확인 전에 만들고 app config의 default channel로도 등록한다.
- 현재 권한이 미결정일 때만 OS 요청을 실행한다. iOS `AUTHORIZED`, `PROVISIONAL`, `EPHEMERAL`과 공통 granted를 허용으로 해석한다. 이미 거절한 사용자를 반복 prompt하지 않는다.
- 허용이면 EAS `projectId`를 명시해 `getExpoPushTokenAsync`로 Expo token을 받는다. 거절이면 token 없이 `DENIED` 의도를 만든다. iOS·Android 외 플랫폼과 projectId 누락, provider network 오류는 등록 성공으로 처리하지 않는다.
- 로그인 직후 웹 요청, app foreground 복귀와 native push token 변경 listener가 등록 확인을 시작한다. provider 실패는 같은 사용자·epoch 동안 제한된 backoff로 재시도한다.
- 전역 notification handler는 foreground에서 banner, notification list, sound와 badge를 모두 끈다. 앱 내 Snackbar와 알림함은 웹 책임으로 유지한다.

## 상태 조회·등록·재시도

1. 인증된 `AUTH_STATE`와 같은 epoch의 `PUSH_REGISTER_REQUEST`만 처리한다.
2. 권한·token snapshot을 얻은 뒤 웹에 `PUSH_STATE_REQUEST`를 보내 현재 revision을 조회한다.
3. 같은 registration intent는 한 `operationId`, `operationIssuedAt`, revision과 tokenVersion snapshot으로 먼저 SecureStore에 저장한 뒤 `PUSH_DEVICE`를 보낸다.
4. API 전후 또는 저장 중 auth user·epoch나 문서 generation이 바뀌면 결과를 적용하지 않는다. 다른 계정 token으로 이전 작업을 재전송하지 않는다.
5. 응답 유실은 상태 조회의 같은 requestId, 등록·해제의 같은 operationId로 재전송한다. 기본 응답 timeout은 웹 HTTP timeout보다 긴 12초이고 세션 안에서 최대 3회다. 서버 `retryAfterMs`는 최대 24시간 형식으로 검증하고 받은 최소 대기보다 줄이지 않는다.
6. `PUSH_REVISION_CONFLICT`, `PUSH_OPERATION_EXPIRED`, `PUSH_OPERATION_CONFLICT`는 현재 인증·권한·상태를 다시 확인해 새 intent를 만든다. `PUSH_TOKEN_CONFLICT`는 installation을 반복 회전하거나 자동 탈취하지 않고 현재 intent를 종료한다.
7. 익명 revoke의 revision conflict는 최신 연결을 추측해 revision을 바꾸지 않는다. 원래 binding 의도를 durable quarantine으로 남기고, 같은 계정의 후속 인증 상태 조회가 없는 동안 자동 해제를 반복하지 않는다.
8. timer 횟수가 끝나도 pending 등록·해제는 지우지 않는다. 새 bridge, 로그인·foreground trigger에서 다시 조정한다.

## 파일 구조

```text
app/
  App.tsx
  app.json
  src/push/
    bridgeProtocol.ts
    nativeNotificationProvider.ts
    nativePushStorage.ts
    pushRegistrationCoordinator.ts
    pushStorage.ts
  src/shell/OpenMdWebView.tsx
  tests/
    pushFoundation.test.cjs
    pushRegistration.test.cjs
```

## 자동 검증

- top-frame·exact-origin shim, nonce wrapper exact schema, raw/다른 nonce 거절
- nonce의 facade 함수 비노출과 이전 문서 native dispatch 거절
- HELLO `push-v1`, session·epoch·payload exact schema와 UTF-8 8KiB 상한
- 권한·token 뒤 상태 조회, durable registration 뒤 `PUSH_DEVICE`
- 저장 실패 때 ACK 금지, 성공 ACK 유실 재응답
- 문서 reload·계정 전환·session ending과 늦은 등록 결과 fence
- active binding의 pending revoke 선저장과 인증 없는 replay
- provider/HTTP 응답 유실의 bounded retry와 Retry-After 준수
- 설치 동시 생성, concurrent mutation 보존과 손상 상태 fail-closed

검증 명령은 `pnpm exec tsc --noEmit`, `pnpm test`, `git diff --check -- app`이다.

## 실제 기기·운영 후속

- EAS의 iOS APNs key와 Android FCM v1 credential 존재 여부는 저장소만으로 확인하지 않았다.
- Android application id가 확정된 native development build와 iOS provisioning을 준비해 실제 권한 prompt, Expo token, 등록·로그아웃 해제와 재실행 복구를 확인한다.
- iOS·Android foreground에서 banner·소리·진동·badge가 모두 억제되고 다른 background 기기에는 계속 표시되는지 확인한다.
- SecureStore의 삭제·재설치·backup 차이, Android WebView before-content fallback과 token rotation listener는 실기기에서 확인한다.
- 푸시 선택·화면 이동·읽음 동기화는 별도 후속 구현이다.
