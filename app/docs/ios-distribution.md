# NalQ iOS 배포 준비

이 문서는 저장소에 확정된 iOS 배포 설정과 계정 소유자가 TestFlight 업로드 때 실행할 작업을 구분한다. 명령은 별도 표기가 없으면 `app/`에서 실행한다.

## 저장소에 반영된 값

| 항목 | 현재 값 | 상태 |
| --- | --- | --- |
| 앱 표시 이름 | `NalQ` | 현재 제품명 기준 |
| Expo slug | `nalq` | 현재 제품명 기준 |
| 마케팅 버전 | `1.0.0` | 첫 출시 기준, 출시 범위 확정 시 변경 가능 |
| iOS build number | 로컬 시작값 `1` | EAS 원격 값이 원장이며 production 빌드가 자동 증가 |
| iOS bundle identifier | `com.nalq.app` | Apple Developer 등록 완료 |
| EAS project | `@hhhyyuns-team/nalq` | Expo 팀 프로젝트 연결 완료 |
| App Store Connect Apple ID | `6807688566` | `NalQ` 앱 레코드 생성 및 EAS 제출 설정 연결 완료 |
| iOS 앱 아이콘 | `assets/nalq-app-icon.png` | NalQ 전용 불투명 정사각 PNG, iOS에만 연결 |
| iPad 지원 | 활성화 | 기존 설정 유지, iPad 실기기 검증과 스크린샷 필요 |
| 스플래시 | 따뜻한 배경·`contain`·220pt | `expo-splash-screen`이 `assets/nalq-splash-dragon.png` 사용 |
| 수출 규정 | 비면제 암호화 미사용 | 현재 앱이 OS의 표준 HTTPS/WebView만 사용하는 범위 기준 |

`bundleIdentifier`는 App Store에 등록한 뒤 기존 앱에서 바꿀 수 없는 앱 정체성이다. `com.nalq.app`은 Apple Developer의 개인 팀에 등록되어 있다.

## 운영 주소

- production 앱의 `EXPO_PUBLIC_WEB_URL`은 `https://nalq.app`이다. 이 공개 값은 WebView가 열 주소로 앱 번들에 포함된다.
- 운영 웹은 `VITE_API_BASE_URL=https://api.nalq.app`으로 빌드한다. 앱이 API 주소를 별도로 주입하거나 API를 직접 호출하지 않는다.
- `app/eas.json`의 production 프로필은 EAS의 `production` 환경을 사용한다. 로컬 `.env`나 preview 환경 값으로 production 빌드를 대신하지 않는다.

## EAS 프로필

- `ios-simulator`: 인증서 없이 iOS Simulator에서 네이티브 설정을 점검하는 Release 빌드다. `preview` 환경의 `EXPO_PUBLIC_WEB_URL`에는 Simulator에서 접근 가능한 검증용 HTTPS 주소를 등록해야 한다.
- `production`: App Store/TestFlight용 Release 빌드다. build number는 EAS 원격 값에서 자동 증가한다.
- `submit.production`: App Store Connect의 `NalQ` 앱 ID `6807688566`을 사용해 제출 대상을 자동으로 선택한다.

## TestFlight 빌드 전 확인

1. `whoami` 결과가 `hhhyyuns-team/nalq`에 접근 가능한 Expo 계정인지 확인한다. 이 저장소에는 이미 `extra.eas.projectId`가 있으므로 `eas init`을 다시 실행하지 않는다.
2. EAS production 환경의 `EXPO_PUBLIC_WEB_URL`이 `https://nalq.app`인지 확인하고, 다르면 `env:set`으로 바로잡는다. 이 값은 비밀이 아니므로 `plaintext`로 저장한다.
3. `check:device-build`를 iOS 범위로 실행한다. 플랫폼을 생략하면 Android용 `GOOGLE_SERVICES_JSON`까지 검사하므로 iOS TestFlight 점검에는 `--platform ios`를 명시한다.
4. EAS 원격 build number를 조회한다. `app.json`의 `ios.buildNumber`는 원격 버전이 초기화되기 전 시작값이며, production 빌드에서는 `appVersionSource: remote`와 `autoIncrement: buildNumber`가 적용된다.
5. EAS가 관리하는 distribution certificate와 App Store provisioning profile이 `com.nalq.app`에 연결됐는지 확인한다. profile의 상태가 active인 것만으로는 충분하지 않다. Apple Developer App ID에 Push Notifications capability를 활성화한 뒤 `aps-environment` entitlement가 포함되도록 App Store provisioning profile을 다시 발급한다.
6. 실제 푸시 발송에 사용할 APNs push key도 같은 Apple Developer 팀과 EAS 프로젝트에 연결한다. provisioning profile의 entitlement와 APNs 발송 키는 별개의 자격이다.

```bash
pnpm dlx eas-cli@latest login
pnpm dlx eas-cli@latest whoami
pnpm dlx eas-cli@latest env:list --environment production
pnpm dlx eas-cli@latest env:set --environment production --name EXPO_PUBLIC_WEB_URL --value https://nalq.app --visibility plaintext
EXPO_PUBLIC_WEB_URL=https://nalq.app pnpm run check:device-build -- --platform ios
pnpm dlx eas-cli@latest build:version:get --platform ios --profile production
pnpm dlx eas-cli@latest credentials --platform ios
```

`credentials`에서는 production 프로필을 선택해 build credentials를 확인한다. 인증서, provisioning profile, `.p8` private key와 로그인 정보는 출력물, 저장소, PR에 남기지 않는다.

## 빌드와 TestFlight 업로드

사전 확인이 끝나면 같은 릴리스 커밋에서 production 빌드를 만든다. 빌드가 성공하면 결과에 표시된 EAS build ID를 기록하고, 그 ID를 지정해 제출한다. 다른 브랜치의 최신 빌드를 잘못 선택할 수 있으므로 `--latest`를 사용하지 않는다.

```bash
pnpm dlx eas-cli@latest build --platform ios --profile production
pnpm dlx eas-cli@latest submit --platform ios --profile production --id <EAS_BUILD_ID>
```

다음 항목을 릴리스 기록에 남긴다.

- Git commit SHA, EAS build ID, marketing version과 실제 원격 build number
- profile이 `production`, bundle identifier가 `com.nalq.app`인지 여부
- App Store Connect 제출 ID와 처리 상태
- TestFlight 설치 뒤 로그인·쿠키 유지·로그아웃, 알림 권한, foreground 억제, background와 일반 종료 상태의 실제 Expo/APNs 수신 결과

`submit`은 App Store Connect로 바이너리를 업로드할 뿐 App Store 심사를 시작하지 않는다. 내부 테스터는 Apple의 처리 완료 뒤 사용할 수 있고, 외부 테스터 배포에는 별도의 TestFlight 베타 심사가 필요하다.

현재 로컬 Simulator에서는 알림 권한과 등록, 주입한 background 배너까지 확인했지만 실제 Expo 발송은 `InvalidCredentials`로 거절됐다. `com.nalq.app`의 APNs 자격을 연결하고 TestFlight 설치본에서 실제 수신을 확인하기 전에는 원격 푸시 검증을 완료로 기록하지 않는다.

2026-09-08 production 빌드 1.0.0 (2)는 App Store provisioning profile `WCJ39M32JR`에 Push Notifications capability와 `aps-environment` entitlement가 없어 Xcode 서명 단계에서 실패했다. 이후 Apple Developer에서 `com.nalq.app`의 Push Notifications를 활성화하고 같은 profile을 재생성했다. 이후 EAS에서 Push Notifications 활성화 상태를 확인하고 새 profile `8JA7LAW6KN`을 생성·연결했다.

같은 날 `NalQ Push` APNs 키를 Production 환경·`com.nalq.app` topic 한정으로 발급했다. 이 키는 TestFlight 설치본을 위한 것이며 Sandbox 빌드의 원격 푸시까지 지원한다고 가정하지 않는다. 다운로드한 키를 EAS 프로젝트에 연결했다. 실제 수신은 별도 확인한다.

## 저장소 밖에서 아직 필요한 출시 자료

- iOS는 `assets/nalq-app-icon.png`의 NalQ 아이콘을 사용한다. 원본은 1254×1254 RGB PNG이며 투명도가 없다. Expo prebuild가 App Store용 1024×1024 RGB AppIcon을 투명도 없이 생성함을 확인했다. 최상위 `assets/icon.png`와 Android adaptive icon은 이번 iOS 변경 범위에서 유지한다.
- `supportsTablet=true`를 유지하면 iPad 화면 동작 검증과 App Store용 iPad 스크린샷이 필요하다. iPhone 전용 출시가 제품 결정이면 첫 제출 전에 `false`로 변경한다.
- 운영 WebView URL과 API/Cookie 구성이 HTTPS에서 실제로 동작하는지 iPhone과 iPad에서 확인한다.
- App Store 설명, 키워드, 카테고리, 지원 URL, 개인정보처리방침 URL, 심사용 로그인 계정과 리뷰 메모를 App Store Connect에 입력한다.
- 앱에서 계정을 만들 수 있으므로 승인된 개인정보처리방침과 앱 내부 회원 탈퇴 흐름이 사용자에게 제공되어야 한다. 현재 제품 문서상 두 항목은 아직 운영 확정 전이므로 심사 제출 전 별도 완료가 필요하다.

## 권한 설명 상태

현재 네이티브 앱은 카메라, 사진, 마이크, 위치, 연락처, 추적 권한을 요청하지 않으므로 해당 `Info.plist` 사용 목적 문구를 넣지 않았다. 이후 파일 선택, 촬영, 알림이나 추적 기능을 추가할 때 실제 기능과 함께 최소 권한 및 사용자용 설명을 추가한다.

2026-09-08 후속 검증: 아이콘 포함 [EAS 빌드 1.0.0 (3)](https://expo.dev/accounts/hhhyyuns-team/projects/nalq/builds/bd5d156f-1576-4d98-8501-5904e61a5cc9)가 FINISHED로 완료됐고 App Store Connect 업로드·처리도 완료됐다. 본인 1명만 포함한 `NalQ Internal Test` 내부 그룹에 빌드를 배정했으며, Apple 화면에서 iPad Pro 11(4세대), iOS 26.6.1의 `설치됨 1.0.0 (3)`을 확인했다. 설치는 PASS이며, 실제 원격 푸시 수신과 알림 탭 이동·로그아웃 검증은 아직 미실행이다. App Store 공개 심사는 제출하지 않았다.


후속 통합: PR #64와 #61을 dev에 순서대로 병합하고 #66에 통합했다. 홈 본문은 375px 뷰포트에서 375px, 834/1194px에서 640px이며 가로 넘침이 없음을 실제 Home 컴포넌트 렌더링으로 확인했다. 이 후속 변경에는 PUSH_OPEN 선택·읽음 재시도와 운영 Compose의 푸시 플래그 전달도 포함되므로 새 iOS 빌드가 필요하다. 위 1.0.0 (3) 설치 기록을 후속 선택 구현의 실기기 검증으로 취급하지 않는다. 상점 PR #65는 제외한다.
