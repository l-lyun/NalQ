---
document_type: runbook
status: ready
scope: app
---

# NalQ 푸시 실기기 빌드와 수신 검증

이 Runbook은 macOS 또는 Windows에서 Android APK와 iOS ad hoc 앱을 EAS 클라우드로 만들고, 실제 단말이 준비되면 퀴즈 결과 푸시 수신까지 검증하는 절차다. `device-preview`는 Release 내부 배포 빌드이므로 설치 뒤 Metro 없이 실행된다. 푸시 선택, cold start 목적지 이동과 읽음 처리는 아직 구현 범위가 아니다.

## 맥북에서 이어서 시작하기

저녁 실기기 테스트는 이 브랜치의 최신 변경을 받은 뒤 진행한다. 아래 명령은 저장소 루트에서 시작하며, 현재 작업 트리에 저장하지 않은 변경이 없는지 먼저 확인한다.

```sh
git status --short
git fetch origin
git switch codex/push-bridge-foundation
git pull --ff-only origin codex/push-bridge-foundation
cd app
pnpm install --frozen-lockfile
pnpm typecheck
pnpm test
export EXPO_PUBLIC_WEB_URL='https://YOUR_PREVIEW_WEB_HOST'
pnpm check:device-build -- --platform ios
```

검증 주소를 실제 값으로 바꾸고 아래 4절의 EAS 로그인·preview 환경·기기 등록·APNs 자격 설정을 완료한 뒤 실행한다. 해당 절의 `pnpm dlx` 명령은 macOS에서도 동일하다.

```sh
pnpm dlx eas-cli@latest build --platform ios --profile device-preview
```

Android도 테스트한다면 `app/google-services.json`을 별도로 준비하고 아래처럼 점검한다. 3절의 파일 업로드 명령에서 경로는 `./google-services.json`을 사용한다.

```sh
export GOOGLE_SERVICES_JSON="$PWD/google-services.json"
pnpm check:device-build -- --platform android
pnpm dlx eas-cli@latest build --platform android --profile device-preview
```

아래 PowerShell 예제의 `$env:NAME = 'value'`는 macOS에서 `export NAME='value'`로, 상대 경로의 역슬래시는 `/`로 바꾼다. 서버 플래그는 앱 빌드 터미널이 아니라 실제 검증 서버 프로세스에 전달해야 한다. 실제 자격 확인·원격 빌드·수신은 아직 미검증이며, 외부 설정 없이 위 빌드가 성공한다고 간주하지 않는다.

## 1. 준비 완료 기준

- 검증할 브랜치의 `web`, `app`, `server`가 같은 계약 버전으로 준비돼 있다.
- WebView가 접근할 수 있는 검증용 HTTPS 웹 주소가 있고, 그 웹이 같은 브랜치의 서버 API를 사용한다.
- EAS `preview` 환경에 `EXPO_PUBLIC_WEB_URL`이 있고 Android 빌드에는 `GOOGLE_SERVICES_JSON` file 변수가 있다.
- Android `com.nalq.app` Firebase 앱과 FCM V1 발송 자격, iOS `com.nalq.app` APNs 자격과 테스트 기기 provisioning이 준비돼 있다.
- 검증 서버가 기기 등록, delivery enqueue와 push scheduler를 실제 프로세스 환경 변수로 전달받는다.

`EXPO_PUBLIC_WEB_URL`은 앱 번들에 들어가는 공개 값이다. 토큰이나 비밀을 넣지 않는다. 검증 웹과 API가 이 브랜치보다 이전 버전이면 WebView handshake 또는 기기 등록이 동작하지 않는다.

## 2. 저장소 사전 검증

`app/`에서 의존성을 설치하고 정적 검사를 실행한다.

```powershell
pnpm install --frozen-lockfile
pnpm exec tsc --noEmit
pnpm test
```

iOS만 점검할 때는 검증 HTTPS 주소를 현재 PowerShell 프로세스에 설정한다.

```powershell
$env:EXPO_PUBLIC_WEB_URL = 'https://YOUR_PREVIEW_WEB_HOST'
pnpm check:device-build -- --platform ios
```

Android는 Firebase에서 다운로드한 `google-services.json`을 `app/google-services.json`에 놓는다. 이 경로는 Git에서 제외된다.

```powershell
$env:EXPO_PUBLIC_WEB_URL = 'https://YOUR_PREVIEW_WEB_HOST'
$env:GOOGLE_SERVICES_JSON = (Resolve-Path .\google-services.json).Path
pnpm check:device-build -- --platform android
```

사전점검은 HTTPS, 앱 식별자, EAS project ID, 알림 channel, 내부 배포 프로필, Firebase JSON의 `com.nalq.app` package 일치를 확인한다. credential 내용은 출력하지 않는다. EAS 빌드에서도 `eas-build-pre-install` hook이 같은 검사를 실행해 필수 값이 없으면 native build 전에 종료한다.

## 3. Android Firebase와 EAS 준비

Firebase Console에서 Android 앱을 package `com.nalq.app`으로 등록하고 앱 설정 파일 `google-services.json`을 다운로드한다. 이 파일은 앱이 Firebase 프로젝트와 FCM에 등록될 때 쓰이는 client 설정이다.

FCM V1 서비스 계정 private key JSON은 다른 파일이다. Expo Push Service가 Android FCM으로 발송할 때 쓰는 서버 자격이며 `GOOGLE_SERVICES_JSON`에 넣지 않는다. private key 파일을 저장소나 `.env`에 넣지 말고 EAS credential 저장소에 업로드한다.

```powershell
pnpm dlx eas-cli@latest login
pnpm dlx eas-cli@latest env:set --name EXPO_PUBLIC_WEB_URL --value https://YOUR_PREVIEW_WEB_HOST --environment preview --visibility plaintext
pnpm dlx eas-cli@latest env:set --name GOOGLE_SERVICES_JSON --value .\google-services.json --type file --environment preview --visibility secret
pnpm dlx eas-cli@latest credentials --platform android
```

`credentials` 메뉴에서는 Android `com.nalq.app`의 `Google Service Account` → `Push Notifications (FCM V1)`에 Firebase 서비스 계정 private key를 등록한다. Firebase Cloud Messaging API가 활성화돼 있어야 한다. `google-services.json`의 Firebase project와 EAS에 올린 FCM V1 서비스 계정의 project가 같은지도 확인한다.

설정 이름만 확인하고 값은 출력하지 않는다.

```powershell
pnpm dlx eas-cli@latest env:list --environment preview
```

Android 내부 배포 APK를 요청한다.

```powershell
pnpm dlx eas-cli@latest build --platform android --profile device-preview
```

완료된 EAS build URL을 Android 실기기에서 열어 APK를 설치한다. Play Store를 거치지 않으므로 기기에서 해당 출처의 앱 설치 허용이 필요할 수 있다.

## 4. iOS EAS 준비

iOS에는 `google-services.json`이 필요하지 않다. Expo가 APNs로 보내기 위한 push key와 `com.nalq.app` 서명 자격이 필요하다. paid Apple Developer 계정으로 EAS 자격 설정을 진행한다.

```powershell
pnpm dlx eas-cli@latest login
pnpm dlx eas-cli@latest env:set --name EXPO_PUBLIC_WEB_URL --value https://YOUR_PREVIEW_WEB_HOST --environment preview --visibility plaintext
pnpm dlx eas-cli@latest device:create
pnpm dlx eas-cli@latest credentials --platform ios
pnpm dlx eas-cli@latest build --platform ios --profile device-preview
```

`device:create`이 보여 주는 URL이나 QR을 테스트 iPhone에서 열어 UDID를 등록한다. 그 뒤 만든 `device-preview` 빌드만 해당 ad hoc provisioning 목록의 기기에 설치할 수 있다. `credentials` 또는 첫 build prompt에서 Push Notifications를 활성화하고 APNs key 생성·사용을 확인한다. 새 기기를 추가했다면 provisioning profile을 갱신하는 새 빌드가 필요하다.

빌드 상세에서 bundle identifier가 `com.nalq.app`인지 확인하고 build 산출물의 `aps-environment` entitlement가 빠지지 않았는지 별도로 확인한다. 설치 후 권한 요청과 Expo push token 취득 성공은 앱 등록 준비의 증거다. APNs 발송 자격은 background·종료 상태에서 실제 푸시가 도착해야 검증 완료로 기록한다.

### iOS Simulator 1차 검증

Expo 공식 안내에 따라 Xcode 14 이상, macOS 13 이상, iOS 16 이상 Simulator에서도 원격 푸시를 테스트할 수 있다. 로컬 실행 전 Xcode 라이선스 동의·초기 설치와 iOS Simulator runtime 설치를 완료한다. `ios-simulator` 프로필은 EAS `preview` 환경을 사용하며 `device-preview`의 실기기 ad hoc 빌드와 구분한다.

푸시 지원 OS 조건과 앱 빌드 도구 조건은 다르다. 현재 앱의 Expo SDK 57 네이티브 빌드에는 Xcode 26.4 이상이 필요하다. Xcode 26.3에서는 ExpoModulesJSI의 `JavaScriptCodable+Date.swift`에서 `abs` overload 컴파일 오류가 재현됐다. 지원되는 Xcode로 업데이트하거나 EAS Build를 사용한다. 근거: [Expo 도구 최소 버전](https://expo.dev/changelog/sdk-56#tool-version-bumps), [동일 빌드 오류와 유지관리자 안내](https://github.com/expo/expo/issues/48522).

```sh
pnpm dlx eas-cli@latest build --platform ios --profile ios-simulator
pnpm dlx eas-cli@latest build:run --platform ios
```

맥의 로컬 서버를 사용할 경우 `ios-simulator-local` 프로필은 공개 웹 주소를 `https://localhost:15174`로 고정한다. 이 주소에서 해당 브랜치의 웹과 API를 제공하고, 테스트 Simulator에 로컬 인증서 신뢰를 설정한 뒤 빌드·설치한다. EAS에는 빌드 소스와 공개 주소만 전달하며 로컬 인증서 private key나 서버 비밀은 업로드하지 않는다. 이 프로필은 localhost가 맥을 가리키는 Simulator 전용이며 실제 iPhone에는 사용하지 않는다.

```sh
pnpm dlx eas-cli@latest build --platform ios --profile ios-simulator-local
```

Simulator에서 권한 요청, Expo token 취득·기기 등록, foreground 억제와 background 수신을 1차 확인한다. payload 주입만 확인한 경우에는 Expo/APNs 경유 수신 성공으로 기록하지 않는다. 실제 iPhone의 서명·provisioning·설치, 재설치 시 SecureStore 동작과 background·일반 종료 수신은 별도 실기기 인수 항목이다. Simulator 성공만으로 실기기 검증 완료로 판정하지 않는다.

### macOS 로컬 빌드 검증 기록 (2026-09-08)

- Xcode 26.6과 iOS 18.5 Simulator에서 Release 빌드·설치·실행, 로컬 HTTPS 웹 로그인, 알림 권한 요청·허용, 서버 `IOS / ACTIVE` 등록을 확인했다.
- `CODE_SIGNING_ALLOWED=NO`로 만든 앱은 실행되더라도 ExpoNotifications의 Keychain 접근이 `ERR_NOTIFICATIONS_KEYCHAIN_ACCESS` (`-34018`)로 실패했다. Simulator에도 `Sign to Run Locally` ad-hoc 서명을 적용해야 한다. 이 서명에는 Apple Developer 인증서가 필요하지 않으며, 실기기 provisioning을 대신하지 않는다.
- iOS 26.5 새 Simulator는 첫 데이터 마이그레이션과 dyld 캐시 생성에서 정체돼, 이미 초기화된 iOS 18.5 Simulator로 검증했다. CLI의 `Booted` 표시만으로 홈 화면 준비 완료를 판정하지 않는다.
- 같은 native token 이벤트를 중복 처리하던 수정 전에는 등록 `revision`이 `20 → 60`으로 계속 증가했다. 중복 제거 수정 후에는 약 6분간 `revision=199`, `token_version=1`이 유지됐다. 로그인·foreground에서 수행하는 정상 등록 요청은 유지한다.
- `simctl push`를 이용한 background 알림 배너를 실제 화면에서 확인했다. 이 결과는 Expo/APNs 경유 수신 성공을 의미하지 않는다.
- 별도의 실제 Expo 발송 요청은 `InvalidCredentials`로 거절됐다. 응답은 `com.nalq.app` 프로젝트의 APNs 자격 증명이 없음을 명시했다. Expo 로그인과 해당 프로젝트의 APNs 키 설정 후 실제 발송을 다시 검증해야 한다.
- 테스트 웹·API·DB와 이메일 수신기는 모두 로컬이다. 테스트 SMTP는 인증 메일을 로컬 파일로만 저장하므로 실제 이메일 받은편지함에는 도착하지 않는다. 가입 API가 메일 발송에 실패하면 로컬 SMTP 프로세스가 실행 중인지도 확인한다.

## 5. 검증 서버에서 발송 활성화

세 플래그의 기본값은 모두 `false`다. 기기 등록에는 registration, 신규 delivery 생성에는 delivery, 주기적 발송에는 scheduler가 모두 필요한 범위대로 활성화돼야 한다.

직접 서버 프로세스를 시작한다면 같은 PowerShell 프로세스에 다음 값을 설정한 뒤 실행한다. 실제 퀴즈 완료 사건을 만들 검증 환경에는 기존에 승인된 LLM key와 퀴즈 생성 worker 설정도 필요하다. 이 Runbook을 위해 새 운영 key를 만들지 말고 기존 검증 자격을 재사용한다.

```powershell
$env:OPENMD_PUSH_REGISTRATION_ENABLED = 'true'
$env:OPENMD_PUSH_DELIVERY_ENABLED = 'true'
$env:OPENMD_PUSH_SCHEDULER_ENABLED = 'true'
$env:OPENMD_PUSH_EXPO_ACCESS_TOKEN = 'YOUR_EXPO_ACCESS_TOKEN_IF_ENHANCED_SECURITY_IS_ENABLED'
```

Expo access token security를 사용하지 않는 프로젝트라면 `OPENMD_PUSH_EXPO_ACCESS_TOKEN`은 빈 값으로 둘 수 있다. 활성화한 프로젝트에서는 secret store에서 주입하며 로그나 저장소에 남기지 않는다.

현재 `infra/production/compose.yml`의 `server.environment`는 `OPENMD_PUSH_*`를 전달하지 않는다. `production.env`에 값만 추가해도 컨테이너에 들어가지 않는다. 운영 데이터와 분리된 검증 환경·테스트 계정에서만 아래 내용을 예를 들어 `compose.push-test.yml`이라는 로컬 override로 저장하고, 기본 Compose와 함께 명시적으로 적용한다.

```yaml
services:
  server:
    environment:
      OPENMD_PUSH_REGISTRATION_ENABLED: "${OPENMD_PUSH_REGISTRATION_ENABLED:-true}"
      OPENMD_PUSH_DELIVERY_ENABLED: "${OPENMD_PUSH_DELIVERY_ENABLED:-true}"
      OPENMD_PUSH_SCHEDULER_ENABLED: "${OPENMD_PUSH_SCHEDULER_ENABLED:-true}"
      OPENMD_PUSH_EXPO_ACCESS_TOKEN: "${OPENMD_PUSH_EXPO_ACCESS_TOKEN:-}"
```

다음 명령은 `app/`이 아닌 저장소 루트에서 실행한다. `config --quiet`은 전체 secret이 섞인 Compose 결과를 화면에 출력하지 않고 구성 유효성만 검사한다.

```powershell
docker compose --env-file .\infra\production\production.env -f .\infra\production\compose.yml -f .\compose.push-test.yml config --quiet
docker compose --env-file .\infra\production\production.env -f .\infra\production\compose.yml -f .\compose.push-test.yml up -d server
docker compose --env-file .\infra\production\production.env -f .\infra\production\compose.yml -f .\compose.push-test.yml exec server printenv OPENMD_PUSH_REGISTRATION_ENABLED OPENMD_PUSH_DELIVERY_ENABLED OPENMD_PUSH_SCHEDULER_ENABLED
```

마지막 명령은 비밀이 아닌 세 flag만 출력한다. access token 값은 화면 공유나 기록에 남기지 않는다. 검증이 끝나면 세 flag를 `false`로 되돌리거나 override 없이 서버를 다시 시작한다.

## 6. 실기기 수신 시나리오

테스트 계정과 퀴즈 제목에는 개인정보나 운영 데이터를 사용하지 않는다. 서버 DB를 확인할 때도 `push_token`, `installation_key_digest`, `push_token_digest`는 조회하거나 복사하지 않는다.

1. 앱을 새로 설치하고 로그인한다. 첫 로그인 직후 OS 알림 권한 요청이 한 번 나타나는지, 허용 뒤 앱 이용이 계속되는지 확인한다.
2. 서버에서 해당 테스트 사용자에게 `push_devices.status=ACTIVE`, 올바른 `platform`, 새 `revision`과 `token_version`이 생겼는지만 확인한다.
3. 앱을 foreground에 둔 채 웹이나 다른 기기에서 퀴즈 생성 성공·실패를 만든다. 앱 내 기존 Snackbar/알림함은 동작하고 OS banner, 알림 목록, 소리, 진동, badge는 생기지 않아야 한다.
4. 앱을 background로 보내고 새 결과를 만든다. 제목과 성공·실패 문구가 표시되고, delivery가 ticket/receipt 처리 상태로 진행하는지 확인한다.
5. 최근 앱 화면에서 앱을 닫아 프로세스가 없는 일반 종료 상태를 만든 뒤 새 결과를 만든다. OS 설정의 `강제 중지`는 알림 전달 자체를 막을 수 있으므로 이 종료 시나리오에 사용하지 않는다. OS 알림 표시까지만 확인하며, 알림을 눌렀을 때 목적지 이동은 이번 단계의 합격 조건이 아니다.
6. 알림 권한을 거절한 새 설치에서도 로그인과 퀴즈 이용이 막히지 않고 서버가 `DENIED` 등록 의도에 `DISABLED`, `revision=0` 비영속 결과를 반환하는지 확인한다. 같은 로그인에서 OS prompt가 반복되지 않아야 한다.
7. 로그인 상태에서 네트워크를 끊고 앱을 foreground 복귀시킨 뒤 네트워크를 복구하고 다시 foreground로 전환한다. 등록 재시도가 회복되고 중복 active 기기가 생기지 않는지 확인한다.
8. OS 설정에서 알림 권한을 거절로 바꾸고 foreground 복귀한 뒤 기존 서버 기기가 token 없는 `DISABLED` 상태로 갱신되는지 확인한다. 다시 허용하고 foreground 복귀하면 token 등록이 회복돼야 한다.
9. 네트워크 단절 중 로그아웃을 시도하거나 해제 API 응답을 잃는 조건을 만든 뒤 연결을 복구해 앱을 다시 시작한다. 보존된 해제 의도가 재전송돼 해당 기기가 `REVOKED` 또는 비활성 상태가 되고 이후 결과를 새로 받지 않는지 확인한다. 이미 provider에 전달된 알림은 늦게 나타날 수 있다.
10. 다른 계정으로 로그인했을 때 이전 계정 binding을 재사용하지 않는지 확인한다. 앱 데이터 삭제·재설치 뒤 iOS SecureStore 등 설치 자격이 유지되면 기존 연결을 갱신하고, 자격이 유실됐으면 새 installation으로 복구해야 한다. 두 경우 모두 다른 정상 기기 binding은 유지돼야 한다.
11. provider가 실제 token을 교체한 경우 foreground 복귀 또는 token listener 뒤 같은 installation의 `token_version`이 증가하고 이전 token이 활성 상태로 남지 않는지 기록한다. 단순 문자열 모의 입력은 실기기 token rotation 검증으로 세지 않는다.
12. 가능하면 같은 계정으로 두 기기를 등록한다. foreground 기기에서는 OS 표시가 억제되고 background 기기에는 표시되는지 확인한다.

각 실행에는 플랫폼·OS 버전, 앱 build ID, 권한 상태, 앱 상태, notification ID, delivery state와 시각만 기록한다. Expo ticket 성공은 단말 표시 증명이 아니므로 실제 표시 결과를 별도로 기록한다.

## 7. 판정과 문제 분리

- 빌드 전 사전점검 실패: HTTPS, app ID, EAS 환경 변수 또는 Firebase client 파일 문제다.
- 앱에서 Expo token 취득 실패: Android `google-services.json`/Firebase API 제한, iOS entitlement/APNs provisioning, EAS project ID 또는 네트워크를 확인한다.
- 기기 등록 API가 생기지 않음: 검증 웹·서버 버전, `OPENMD_PUSH_REGISTRATION_ENABLED`, CORS/cookie와 bridge handshake를 확인한다.
- delivery가 생기지 않음: `OPENMD_PUSH_DELIVERY_ENABLED`와 결과 확정 시각 이후 생성 여부를 확인한다. 등록 전 결과는 소급 발송하지 않는다.
- ticket이 진행하지 않음: `OPENMD_PUSH_SCHEDULER_ENABLED`, 서버 egress, Expo access token 설정을 확인한다.
- ticket은 성공했지만 기기에 없음: provider receipt, 비활성 token, OS 권한·집중 모드·배터리 정책과 앱 foreground 여부를 확인한다.

외부 계정 자격 생성·업로드, EAS 원격 build 실행, 앱 설치와 실제 수신은 계정 및 단말 소유자가 수행해야 한다. 저장소 준비는 그 직전까지 완료되어 있다.

## 공식 근거

- [Expo Android FCM V1 자격](https://docs.expo.dev/push-notifications/fcm-credentials/)
- [Expo 내부 배포 빌드](https://docs.expo.dev/build/internal-distribution/)
- [EAS 환경 변수와 file 변수](https://docs.expo.dev/eas/environment-variables/)
- [EAS build profile](https://docs.expo.dev/build/eas-json/)
- [Expo push notification setup](https://docs.expo.dev/push-notifications/push-notifications-setup/)
