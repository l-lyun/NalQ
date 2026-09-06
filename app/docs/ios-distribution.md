# NalQ iOS 배포 준비

이 문서는 Apple 계정이나 외부 콘솔에 로그인하지 않고 저장소에서 준비한 iOS 배포 설정과, 계정 소유자가 첫 TestFlight 빌드 전에 확정해야 할 값을 구분한다.

## 저장소에 반영된 값

| 항목 | 현재 값 | 상태 |
| --- | --- | --- |
| 앱 표시 이름 | `NalQ` | 현재 제품명 기준 |
| Expo slug | `nalq` | 현재 제품명 기준 |
| 마케팅 버전 | `1.0.0` | 첫 출시 기준, 출시 범위 확정 시 변경 가능 |
| iOS build number | `1` | 첫 빌드 시작값, EAS production 빌드가 원격에서 자동 증가 |
| iOS bundle identifier | `com.nalq.app` | Apple Developer 등록 완료 |
| EAS project | `@hhhyyuns-team/nalq` | Expo 팀 프로젝트 연결 완료 |
| App Store Connect Apple ID | `6807688566` | `NalQ` 앱 레코드 생성 및 EAS 제출 설정 연결 완료 |
| iPad 지원 | 활성화 | 기존 설정 유지, iPad 실기기 검증과 스크린샷 필요 |
| 스플래시 | 흰 배경·`contain`·200pt | `expo-splash-screen`이 `assets/splash-icon.png` 사용 |
| 수출 규정 | 비면제 암호화 미사용 | 현재 앱이 OS의 표준 HTTPS/WebView만 사용하는 범위 기준 |

`bundleIdentifier`는 App Store에 등록한 뒤 기존 앱에서 바꿀 수 없는 앱 정체성이다. `com.nalq.app`은 Apple Developer의 개인 팀에 등록되어 있다.

## EAS 프로필

- `ios-simulator`: 인증서 없이 iOS Simulator에서 네이티브 설정을 점검하는 Release 빌드다. `preview` 환경의 `EXPO_PUBLIC_WEB_URL`에는 Simulator에서 접근 가능한 검증용 HTTPS 주소를 등록해야 한다.
- `production`: App Store/TestFlight용 Release 빌드다. build number는 EAS 원격 값에서 자동 증가한다.
- `submit.production`: App Store Connect의 `NalQ` 앱 ID를 사용해 제출 대상을 자동으로 선택한다.

## 첫 TestFlight 빌드 전에 필요한 계정 작업

1. Simulator 검증 전 EAS의 `preview` 환경에 공개 빌드 변수 `EXPO_PUBLIC_WEB_URL`을 Simulator에서 접근 가능한 검증용 HTTPS 주소로 등록한다.
2. EAS의 `production` 환경에는 같은 이름으로 실제 운영 HTTPS 주소를 등록한다. 두 값은 비밀이 아니지만 각각의 앱 번들에 포함된다.
3. 운영 WebView URL을 실제 iPhone과 iPad에서 검증한 뒤 production 빌드를 실행한다.
4. EAS가 관리하는 배포 인증서와 provisioning profile은 Expo 및 Apple 계정에서 관리한다. 인증서나 `.p8` 파일은 저장소에 커밋하지 않는다.

예시 명령은 `app/`에서 실행한다.

```powershell
pnpm dlx eas-cli@latest login
pnpm dlx eas-cli@latest init
pnpm dlx eas-cli@latest env:create --environment preview --name EXPO_PUBLIC_WEB_URL --value https://YOUR_PREVIEW_HOST --visibility plaintext
pnpm dlx eas-cli@latest build --platform ios --profile ios-simulator
pnpm dlx eas-cli@latest env:create --environment production --name EXPO_PUBLIC_WEB_URL --value https://YOUR_PRODUCTION_HOST --visibility plaintext
pnpm dlx eas-cli@latest build --platform ios --profile production
pnpm dlx eas-cli@latest submit --platform ios --profile production
```

마지막 제출은 빌드를 TestFlight/App Store Connect로 업로드할 뿐 자동으로 App Review를 시작하지 않는다.

## 저장소 밖에서 아직 필요한 출시 자료

- 현재 `assets/icon.png`는 Expo 기본 자산이므로 최종 NalQ 브랜드 자산으로 교체해야 한다. iOS 아이콘은 1024×1024 PNG, 불투명 배경으로 준비한다.
- 스플래시는 `assets/splash-icon.png`를 흰 배경 중앙에 표시하도록 연결되어 있다. 현재 파일도 Expo 기본 이미지이므로 최종 NalQ 이미지로 같은 경로에 교체한 뒤 iPhone과 iPad의 cold launch에서 여백과 크기를 확인한다.
- iPad 지원은 `supportsTablet=true`로 유지한다. 별도 태블릿 UI 대신 웹의 모바일 단일 열과 주요 학습 화면의 최대 640px 본문 폭을 재사용한다. iPad 화면 동작 검증과 App Store용 iPad 스크린샷은 필요하다.
- 운영 WebView URL과 API/Cookie 구성이 HTTPS에서 실제로 동작하는지 iPhone과 iPad에서 확인한다.
- App Store 설명, 키워드, 카테고리, 지원 URL, 개인정보처리방침 URL, 심사용 로그인 계정과 리뷰 메모를 App Store Connect에 입력한다.
- 앱에서 계정을 만들 수 있으므로 승인된 개인정보처리방침과 앱 내부 회원 탈퇴 흐름이 사용자에게 제공되어야 한다. 현재 제품 문서상 두 항목은 아직 운영 확정 전이므로 심사 제출 전 별도 완료가 필요하다.

## 권한 설명 상태

현재 네이티브 앱은 카메라, 사진, 마이크, 위치, 연락처, 추적 권한을 요청하지 않으므로 해당 `Info.plist` 사용 목적 문구를 넣지 않았다. 이후 파일 선택, 촬영, 알림이나 추적 기능을 추가할 때 실제 기능과 함께 최소 권한 및 사용자용 설명을 추가한다.
