---
document_type: execution-plan
status: draft
scope: web
---

# [Execution Plan] 실제 퀴즈 생성 흐름과 알림 UI 후속 수정

- 관련 PRD: [학습자료 만들기](../prd/prd-content-import.md), [퀴즈 생성·풀이·결과·복습](../prd/prd-quiz-learning.md), [퀴즈 생성 결과 알림](../prd/prd-quiz-generation-notifications.md)
- 관련 UX: [학습자료 만들기 흐름](../ux/flow-content-import.md), [퀴즈 생성 흐름](../ux/flow-quiz-solving.md), [퀴즈 생성 화면](../ux/screen-quiz-generation.md), [알림 목록](../ux/screen-notifications.md)
- 관련 Contract: [학습·퀴즈 API](../contracts/contract-api-quiz-learning.md), [알림 API](../contracts/contract-api-notifications.md)
- 관련 TRD: [웹 퀴즈 상태](../../web/docs/trd/trd-quiz-solving.md), [웹 앱 셸](../../web/docs/trd/trd-app-shell-navigation.md), [웹 학습자료](../../web/docs/trd/trd-learning-materials.md)

## 목표와 완료 조건

- Notion에서 가져온 글을 확인·저장한 뒤 실제 서버 API로 퀴즈를 생성하고 풀 수 있다.
- 학습자료 가져오기·편집·퀴즈 조건 화면의 좌상단 뒤로가기가 이미 지나온 두 화면을 반복해서 왕복시키지 않는다.
- 사용자가 입력한 선택적 추가 요청이 `generationPrompt`로 생성 API에 한 번 전달된다.
- 사용자는 최대 문제 수로 5·10·15·20을 선택할 수 있다.
- 평상시 개발·빌드는 실제 API를 사용하고, fixture 화면은 명시적인 mock 실행 명령에서만 사용한다.
- 종과 알림 목록은 기존 NalQ·SEED 정보 위계 및 목록 패턴과 일치한다.

완료는 실제 계정으로 `Notion 가져오기 → 편집 확인 → 저장하고 퀴즈 만들기 → 사용자 추가 요청 포함 생성 → 풀이`를 통과하고, 브라우저 history 시나리오와 알림 화면을 모바일·데스크톱에서 확인했을 때 판단한다.

## 확인된 문제와 근거

### P001 실제 API와 mock 실행 경계가 기본 실행에서 뒤섞임

- `web/src/features/quiz/model/quizFeature.ts`는 `VITE_QUIZ_API_ENABLED=true`가 아니면 개발 환경에서 자동으로 mock을 선택한다.
- `web/.env.example`의 기본값은 `false`이고 `pnpm dev`, `pnpm build`는 실제 API 여부를 명령 이름에서 드러내지 않는다.
- 이 상태에서는 Notion 학습자료 저장 자체가 성공해도 이후 퀴즈 화면이 fixture로 전환되거나 운영 빌드에서 퀴즈 route가 비활성화될 수 있다. 화면 성공 표시만으로 실제 `quiz_sets` 생성 여부를 판단할 수 없다.

### P002 Notion 가져오기와 편집 화면의 history 왕복

- `NotionImportPage.finishImport`는 `/learning/materials/new`를 새 history entry로 추가한다.
- `LearningMaterialCreatePage.requestBack`도 Notion 화면을 새 entry로 추가한다.
- 따라서 `Notion 선택 → 편집 확인 → 좌상단 뒤로가기 → 브라우저/화면 뒤로가기`에서 편집 화면이 다시 나타나는 왕복 history가 만들어질 수 있다.

### P003 사용자 추가 요청이 웹에서 누락됨

- 서버 계약과 `GenerateQuizRequest`는 선택 필드 `generationPrompt`를 최대 300 Unicode code point로 지원한다.
- `OpenAiQuizGenerator`도 생성 작업 전체를 provider user prompt에 포함한다.
- 그러나 웹 `QuizConditions`와 조건 화면에는 `generationPrompt` 필드와 입력 UI가 없어서 현재 클라이언트 요청에는 전달될 수 없다.

### P004 알림 utility와 목록의 시각 위계 불일치

- 종은 현재 `Icon` 크기 `x5`라 화면 명세의 44×44 터치 목표 안에서 시각적으로 작게 보인다.
- 알림 목록은 `List.Item` 안에 별도 raw `button`을 중첩하고, 읽지 않은 행 전체 배경·96~104px 최소 높이·별도 divider를 함께 사용한다.
- 결과적으로 홈·학습·마이페이지의 평평한 SEED 목록보다 카드처럼 무겁고, 결과 문구·대상명·시각의 위계도 흐려진다.

## 범위와 비범위

### 범위

- 실제 API 기본 실행과 명시적 mock 실행 명령 분리
- Notion 가져오기 성공 이후 학습자료 저장·퀴즈 생성 route 연결
- 확인된 Notion 선택/편집 및 퀴즈 진입 history entry 정리
- 선택적 추가 요청 입력, 길이 안내·검증, `generationPrompt` 요청 전달
- 종 아이콘의 소폭 확대와 badge 위치 회귀 확인
- 알림 목록을 기존 SEED `List` 계열과 동일한 평평한 행 구조·간격·타이포 위계로 정리
- 관련 타입·라우팅·요청 payload 회귀 테스트와 실제 브라우저 E2E

### 비범위

- Notion OAuth·페이지 검색·Markdown 변환 서버 계약 변경
- 퀴즈 생성 프롬프트의 서버 보관 또는 응답 echo 추가
- 알림 검색·분류·삭제·설정 기능
- 새로운 알림 종류, OS push/FCM, 앱 네이티브 셸 변경
- #48의 전역 SEED UI 일관성 문서 작업

## 기술 접근

### 실제 API와 mock 분리

- `pnpm dev`와 production `pnpm build`는 실제 API route를 사용하도록 한다.
- fixture가 필요한 경우 `pnpm dev:mock`처럼 이름으로 드러나는 별도 명령만 사용한다.
- mock adapter와 fixture는 즉시 삭제하지 않고 시각 회귀용으로 격리한다. 실제 API 실행에서는 mock 데이터와 “개발용 샘플” 문구가 나타나지 않아야 한다.
- 실행 모드의 단일 원장을 `quizRuntimeMode`에 유지하고 퀴즈·알림·학습 관리 adapter가 같은 값을 사용하게 한다.

### Notion 이후 route와 history

- Notion 페이지 복사 성공 후 편집 화면 전환은 중간 선택 entry를 정리하도록 `replace` 여부를 명시한다.
- 편집 화면의 좌상단 뒤로가기는 원래 학습 진입 맥락으로 한 번 복귀하고, Notion 편집 화면을 새 history entry로 다시 쌓지 않는다.
- fallback 직접 URL 진입은 `/learning/new` 또는 저장된 `returnTo`로 수렴하며 `navigate(-1)`만으로 목적지를 추측하지 않는다.

### 사용자 추가 요청

- 조건 화면에 선택 입력 `추가 요청`을 제공하고 300자 제한과 현재 글자 수를 표시한다.
- 공백만 입력하면 필드를 생략하고, 값이 있으면 앞뒤 Unicode 공백을 정리해 `generationPrompt`로 전송한다.
- 추가 요청은 문제 유형·난이도·문제 수·학습자료 근거를 덮어쓰지 않는다는 설명을 제공한다.
- 서버 응답과 local requested-config에는 추가 요청을 새로 저장하지 않는다.

### 알림 UI

- icon-only 행동의 44×44 터치 영역은 유지하고 종 glyph만 한 단계 키운다.
- 항목 전체를 하나의 SEED 목록 행동으로 만들고 raw button 중첩과 고정 최소 높이를 제거한다.
- 읽지 않음은 작은 marker와 텍스트 강조로 구분하고 행 전체 brand 배경은 제거하거나 가장 약한 의미 표현으로 제한한다.
- 결과 문구를 item title, 대상명을 detail, 상대 시각을 낮은 위계 metadata로 두며 긴 대상명 두 줄과 focus-visible을 유지한다.

## 작업

- [x] T001 퀴즈 runtime mode와 `dev`·`dev:mock`·`build` 명령을 실제 API 기본으로 정리하고 학습자료·퀴즈·알림 adapter를 같은 mode로 통일 — `web/package.json`, `web/.env.example`, `web/src/features/quiz/model/quizFeature.ts`, `web/src/features/learning-material/api/learningMaterialManagementAdapter.ts`
- [x] T002 Notion 복사 결과 저장 후 실제 퀴즈 생성 route와 history entry를 회귀 테스트로 고정 — `web/src/pages/learning/NotionImportPage.tsx`, `web/src/pages/learning/LearningMaterialCreatePage.tsx`, 관련 route 테스트
- [x] T003 사용자 추가 요청·5·10·15·20 문제 수 타입·입력·검증·API payload를 연결 — `web/src/pages/quiz/`, `web/src/features/quiz/api/`
- [x] T004 종 glyph와 badge 조합을 작은 화면에서 조정 — `web/src/features/notification/ui/NotificationCenter.tsx`, 앱 셸 스타일
- [x] T005 알림 목록을 기존 SEED 목록 패턴과 정렬하고 로딩·빈 상태·오류 상태를 보존 — `web/src/pages/notifications/`
- [ ] T006 관련 웹 회귀 테스트와 `pnpm verify` 실행 — 회귀 테스트 54개, typecheck, lint, build는 통과. `pnpm verify`는 기존 third-party notices 불일치에서 차단
- [ ] T007 실제 계정과 실제 Notion 페이지·OpenAI API로 전체 E2E 및 history·320px·키보드 확인 — 실제 API 생성·알림 이동·풀이 진입과 320px는 통과, 실제 Notion OAuth와 키보드 수동 확인은 남음
- [x] T008 구현 후 실제 결정과 명령을 관련 웹 TRD에 동기화

## 검증

- 집중 테스트: runtime mode, `generationPrompt` trim/누락/300자 경계, Notion→편집→퀴즈 route, deterministic back destination
- 관련 전체 검사: `pnpm verify`, 루트 `./scripts/verify.sh fast`
- 실제 API 확인: 생성 요청 body에 사용자가 입력한 `generationPrompt`가 있고 DB에 실제 QuizSet·문항이 생성되는지 확인
- 사용자 가시 확인: mock 문구 미노출, Notion 원문으로 생성·풀이, 좌상단 back 연속 사용 시 왕복 없음, 종·badge 겹침 없음, 알림 행 위계·읽지 않음·긴 제목·focus-visible 확인
- 뷰포트: 320px WebView 기준과 데스크톱 폭

## 진행 기록

| 날짜 | 상태 | 결과 또는 차단 사유 |
| --- | --- | --- |
| 2026-09-04 | 계획 | 실제 테스트와 코드 대조에서 P001~P004 확인, 구현 전 WIP 계획 작성 |
| 2026-09-04 | 구현 중 | T001~T005·T008 구현. 웹 회귀 테스트 54개·typecheck·lint·build와 서버 fastTest 통과. 격리 DB의 실제 OpenAI API에서 4/5(80%)와 브라우저 5/5 READY를 확인하고 320px 풀이까지 진입 |

## 열린 질문과 차단 사항

- `pnpm verify`의 라이선스 검사는 이번 변경과 무관한 기존 `THIRD_PARTY_NOTICES` 불일치로 차단된다.
- mock은 삭제하지 않고 명시적 실행으로 격리한다.

