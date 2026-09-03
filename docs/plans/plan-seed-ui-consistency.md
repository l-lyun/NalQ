---
document_type: execution-plan
status: draft
scope: web
---

# [Execution Plan] SEED UI 일관성 개선

- 관련 디자인 기준: [NalQ Design System](../../DESIGN.md)
- 관련 PRD: [학습자료 만들기](../prd/prd-content-import.md), [퀴즈 학습](../prd/prd-quiz-learning.md), [마이페이지 계정 관리](../prd/prd-mypage-account-management.md)
- 관련 UX: [학습 화면](../ux/screen-learning.md), [퀴즈 흐름](../ux/flow-quiz-solving.md), [퀴즈 피드백 화면](../ux/screen-quiz-feedback.md), [마이페이지 화면](../ux/screen-mypage.md)
- 관련 Web TRD: [웹 앱 셸](../../web/docs/trd/trd-app-shell-navigation.md), [웹 퀴즈 화면 상태](../../web/docs/trd/trd-quiz-solving.md)
- 감사 기준: `2629665` (`2026-09-03`), `web/package.json`과 `web/src/`의 현재 구현

## 문서의 책임

이 문서는 현재 웹 UI를 SEED Design의 의미·상태·접근성 패턴에 더 가깝게 맞추기 위한 제안과 단계별 실행 계획이다. 새 사용자 정책이나 화면 흐름을 정하는 PRD가 아니며, SEED 컴포넌트의 코드나 props를 별도 원장으로 복제하지 않는다. 실제 구현 직전에는 설치 버전과 공식 문서를 다시 확인한다.

## 배경과 목적

NalQ 웹은 SEED semantic token과 `@seed-design/react` 컴포넌트를 넓게 사용하고 있다. 다만 퀴즈 선택지, 저장 성공 피드백, disclosure, 안내·결과 상태, 반복 목록 일부는 로컬 HTML/CSS 조합으로 같은 의미를 다시 만들고 있다. 현재 조합이 곧 접근성 결함이라는 뜻은 아니지만, SEED recipe와 snippet이 제공하는 상태·반응형·키보드 동작에서 점차 벗어날 위험이 있다.

목적은 다음과 같다.

- 핵심 학습 흐름에서 같은 의미의 컨트롤과 피드백이 같은 SEED 패턴을 사용하게 한다.
- 로컬 CSS가 소유하는 선택·오버레이·상태 표현을 줄이고 SEED 업데이트를 따라가기 쉽게 한다.
- 모바일 WebView와 넓은 웹 화면에서 동일한 정보 구조를 유지하면서 적합한 표현으로 전환한다.
- 교체가 필요 없는 합성 패턴과 React Native 앱 셸을 감사 오탐에서 제외한다.

## 비목표와 제외 범위

- 제품 정책, 퀴즈 채점 규칙, 저장 API, 라우팅, URL 상태 계약을 바꾸지 않는다.
- 화면 정보 구조나 문구를 전면 재설계하지 않는다.
- SEED 토큰 값을 복사하거나 NalQ 전용 범용 컴포넌트 라이브러리를 새로 정의하지 않는다.
- `server/`와 API Contract는 변경하지 않는다.
- `app/`의 Expo/React Native 네이티브 셸에 `@seed-design/react`를 직접 적용하지 않는다. WebView 안의 웹 콘텐츠만 이 계획의 대상이다.
- 감사만으로 패키지 업그레이드, snippet 일괄 생성, 모든 raw HTML 제거를 결정하지 않는다.
- `window.confirm` 교체, 기존 `ContentDialog` 정리, 하단 내비게이션 재설계는 이번 후보에서 제외한다. 별도 UX·기술 검토가 필요하다.

## 확정 요구사항과 감사 기준

### 확정 요구사항

- 기존 PRD·UX·Contract가 정한 사용자 행동과 복구 경로를 보존한다.
- 저장 실패, 미응답 제출, 생성 실패처럼 사용자의 다음 행동에 필요한 정보는 자동으로 사라지는 피드백으로 바꾸지 않는다.
- 선택, 성공, 실패, 생성 중 상태를 색만으로 전달하지 않는다.
- 키보드 조작, focus-visible, 레이블, `aria-live`, dialog focus trap과 닫힌 뒤 focus 복귀를 유지하거나 개선한다.
- 작은 WebView, 긴 한국어 문구, 글자 확대, safe area와 고정 하단 UI 겹침을 검증한다.
- 구현 시점의 설치 패키지와 공식 SEED 문서가 이 문서의 예시 매핑보다 우선한다.

### SEED 패키지와 공식 snippet의 경계

현재 웹에는 `@seed-design/react@2.3.0`, `@seed-design/css@2.5.0`, `@seed-design/vite-plugin@2.1.0`이 고정되어 있다. `web/seed-design.json`은 CLI 생성 위치를 `web/seed-design`으로 지정하지만, 감사 기준 커밋에는 생성된 `web/seed-design/` 디렉터리가 없다.

- `@seed-design/react` 직접 import: `Box`, `Text`, `Field`, `Checkbox`, `BottomSheet`, `ContentDialog` 같은 runtime primitive와 compound component를 제공한다.
- `seed-design/ui/*` import: 공식 문서가 CLI로 프로젝트에 추가하도록 안내하는 **소스 snippet**이다. 별도 npm 패키지에서 완제품을 가져오는 경계로 간주하지 않는다.
- `SegmentedControl`, `RadioGroup`, `RadioSelectBox`, `Snackbar`, `ResponsiveDialog`, `Accordion`, `Callout`, `ResultSection`, `ListButtonItem`의 권장 사용 예시는 모두 snippet 층을 포함할 수 있다. 채택하려면 `web/seed-design.json`, Vite/TypeScript alias, 생성 파일의 소유·갱신 방식을 먼저 정한다.
- snippet은 수정 가능한 시작 코드이므로 로컬 파일이 생기는 것 자체가 예외는 아니다. 다만 원본과의 차이, 버전 요구사항, 재생성 시 덮어쓰기 정책을 리뷰 가능하게 남긴다.

## 감사 결과

### 잘 적용된 영역

- `web/src/main.tsx`는 SEED base CSS를 불러오고, 화면 전반은 semantic color·spacing token을 사용한다.
- 인증·가입·학습 폼은 `Field`와 `TextField`, 문제 유형 다중 선택은 `Checkbox`, Notion 페이지 단일 선택은 `RadioGroup`을 사용한다.
- 주요 행동은 `ActionButton`, 로딩은 `Skeleton`·`ProgressCircle`, 페이지 경고와 저장 실패는 필요한 곳에서 `PageBanner`를 사용한다.
- 퀴즈의 모바일 맥락 오버레이는 `BottomSheet`, 이름 변경과 확인은 `ContentDialog`를 사용해 focus 관리 기반을 이미 갖고 있다.
- 홈과 학습 목록은 SEED `List` compound component 위에 전체 행 버튼을 합성한다. 이는 공식 List 문서의 `List.Content asChild` 패턴과 같은 구조다.
- 텍스트·색·간격을 로컬 숫자와 HEX로 복제하기보다 SEED component와 token을 우선한다.

### 커스텀 예외와 오탐 방지 기준

| 현재 예외 | 감사 판단 |
| --- | --- |
| `ChoiceFieldset`과 객관식 답안의 native `fieldset`/`input` | native radio 자체는 접근 가능한 기반이다. 문제는 raw HTML 사용이 아니라 SEED와 같은 시각·상태 recipe를 로컬 CSS가 중복 소유한다는 점이다. |
| `button`으로 구현한 내비게이션·문항 번호·목록 행 | raw `button` 자체는 문제가 아니다. 의미에 맞는 native element이고 SEED가 전용 recipe를 제공하지 않거나 공식 합성 패턴을 따르는 경우 유지할 수 있다. |
| `List.Item` + `List.Content asChild` + `button` | SEED의 `ListButtonItem` snippet도 같은 compound component 합성 패턴을 사용한다. 현재 구현을 비-SEED 재구현으로 단정하지 않는다. |
| `AppBottomNavigation`과 `LearningBottomNavigation` | 현재 확인된 SEED React 전용 bottom-navigation component가 없고 제품 내비게이션 의미가 별도다. 키보드·현재 위치·터치 영역을 충족하면 raw button만으로 교체 대상으로 보지 않는다. |
| `app/` Expo 셸 | React Native 네이티브 UI는 `@seed-design/react` 직접 적용 범위가 아니다. WebView 안 `web/`과 네이티브 셸을 분리해 감사한다. |
| 로컬 안내·빈 상태 컴포넌트 | 모든 보조 문구를 Callout이나 ResultSection으로 바꾸지 않는다. 강조가 필요한 안내 또는 한 영역을 대체하는 결과 상태일 때만 매핑한다. |

## 개선 후보 요약

우선순위는 사용자 흐름에 미치는 영향과 반복 범위를 기준으로 한 제안이며, 제품 우선순위 확정이 아니다.

| 후보 | 현재 파일과 맥락 | 사용자 영향 | 추천 매핑 | 우선순위·수정 판단 |
| --- | --- | --- | --- | --- |
| 객관식 답안 | `web/src/pages/quiz/QuizFlowPage.tsx`의 `QuestionAnswer`, `.quiz-objective-options` | 가장 자주 반복되는 핵심 답안 선택의 선택·focus·disabled 표현이 로컬 CSS에 묶임 | `RadioSelectBoxRoot`/`RadioSelectBoxItem`; 밀도가 우선이면 `RadioGroup` | 높음 · **[수정 필요]** |
| `ChoiceFieldset` | 같은 파일의 난이도·최대 문제 수와 서술형 자기평가·채점 수정 | 한 화면에 유사한 분절 컨트롤이 반복되고 의미가 서로 다름 | 즉시 콘텐츠 전환이면 `SegmentedControl`; 제출 값 선택이면 `RadioGroup`/`RadioSelectBox` | 높음 · **[수정 필요, 최종 매핑 재확인]** |
| 저장 성공 피드백 | `web/src/pages/profile/ProfileSubPages.tsx`의 inline 성공 문구, `LearningManagementPages.tsx`의 이름 변경 live region과 학습자료 `saved` route state | 성공이 레이아웃에 남거나 시각적으로 보이지 않아 피드백이 화면마다 다름 | 앱 공통 `SnackbarProvider` + positive `Snackbar`; 접근성 live announcement 유지 | 높음 · **[수정 필요]** |
| 퀴즈 오버레이 | `QuizFlowPage.tsx`의 공통 `SheetFrame`과 7개 sheet | 데스크톱에서도 모든 보조 작업이 BottomSheet로 고정됨 | 모바일 BottomSheet·넓은 화면 Dialog를 묶는 `ResponsiveDialog` snippet 검토 | 중간 · **[수정 필요 여부 검토]** |
| disclosure | `LearningManagementPages.tsx`의 `MaterialDisclosureCard`, `components/QuizManagementCard.tsx` | 펼침 상태·heading·chevron·disabled가 두 구현과 CSS에 분산됨 | controlled, `multiple` Accordion; URL `expanded` Set 유지 | 중간 · **[수정 필요]** |
| 안내문 | `LearningPrimitives.tsx`의 `LearningNotice`, `QuizFlowPage.tsx`의 `.quiz-notice` | 정보성 안내가 semantic tone·icon·링크 정책 없이 동일한 회색 상자로 표현됨 | 정보 중요도에 따라 neutral/informative `Callout`; 평문은 유지 | 중간 · **[수정 필요, 사용처 선별]** |
| full/empty/error | `HomeStates.tsx`의 `FullError`, `LearningManagementPages.tsx`의 `EmptyState`·`InlineFailure`, 생성·제출 결과 화면 | 같은 결과와 복구 행동의 정렬·크기·읽기 순서가 화면마다 다름 | 전체/영역 대체 상태만 `ResultSection`; field·inline·page banner 오류는 유지 | 중간 · **[수정 필요, 범위 선별]** |
| 반복 list row | `HomePrimitives.tsx`의 `InteractiveList`, `LearningPrimitives.tsx`의 `LearningActionList`, `LearningManagementPages.tsx`의 `PreviewRow` | 구현 중복은 있으나 현재 전체 행 클릭과 disabled semantics는 동작함 | `ListButtonItem` snippet 도입 비용·확장성 검토 | 낮음 · **[수정 불필요, snippet 채택 시 정리]** |

## 후보별 실행 제안

### 1. 객관식 답안 → RadioSelectBox/RadioGroup

**감사 근거**

- `QuestionAnswer`가 `fieldset` 안에 native radio와 전체 행 label을 만들고, 번호·선택 배경·focus outline을 `.quiz-objective-options`가 소유한다.
- 선택 상태는 단일 문자열로 제어되며 서버 payload나 채점 계약을 바꿀 필요가 없다.

**제안**

- 긴 답안 전체를 터치하는 현재 행동을 보존하려면 `RadioSelectBoxRoot`와 `RadioSelectBoxItem`을 1열로 사용한다.
- 답안이 짧고 카드형 강조가 과하면 `RadioGroup` large item을 비교 후보로 둔다.
- 번호는 label 콘텐츠에 유지하고, 선택 표시를 색만으로 구분하지 않는다.

**접근성·반응형**

- 문제 제목과 그룹을 `aria-labelledby`로 연결하고 각 choice ID를 안정적인 `value`로 유지한다.
- 화살표 키, Tab 진입, Space 선택, focus-visible, 글자 확대 시 행 높이 증가를 확인한다.
- 모바일에서 최소 터치 영역과 줄바꿈을 보장하고, 넓은 화면에서도 읽기 열은 1열로 유지한다.

### 2. ChoiceFieldset → SegmentedControl 또는 radio 계열

**감사 근거**

- `ChoiceFieldset`은 난이도, 최대 문제 수, 서술형 자기평가, 채점 수정에 재사용된다.
- 공식 SegmentedControl은 작은 고정 집합의 즉시 전환에 맞고 한 화면에 반복 사용하지 않는 것을 권장한다. 현재 난이도와 문제 수는 모두 생성 전 폼 값이라 자동으로 콘텐츠를 전환하지 않는다.

**제안**

- 로컬 segmented look-alike는 제거하되 네 사용처를 일괄적으로 SegmentedControl에 매핑하지 않는다.
- 사용자 선택 즉시 설명·미리보기·화면이 바뀌는 한 그룹만 SegmentedControl 후보로 삼는다.
- 현재 행동을 유지한다면 난이도·문제 수·자기평가·채점 수정은 `RadioGroup` 또는 강조가 필요한 경우 `RadioSelectBox`가 우선 후보이다.
- 제품/UX가 난이도 또는 문제 수를 “즉시 모드 전환”으로 확정할 때만 그 한 그룹을 SegmentedControl로 채택한다.

**접근성·반응형**

- legend/label, description, error 연결을 유지한다.
- SegmentedControl 후보는 2~4개 짧은 label, 모바일 너비와 한 화면 1개 원칙을 검증한다.
- radio 후보는 긴 번역·글자 확대에서 세로 배치로 자연스럽게 전환되는지 확인한다.

### 3. 저장 성공 → Snackbar

**감사 근거**

- 닉네임 저장 성공은 form 안에 inline positive text로 남는다.
- 퀴즈 이름 변경 성공은 보이지 않는 live region에만 전달된다.
- 학습자료 변경은 `{ saved: true }` route state를 보내지만 목록에서 사용자 가시 성공 피드백을 소비하지 않는다.

**제안**

- 앱 셸에서 한 개의 `SnackbarProvider`를 소유하고 저장 성공 시 positive Snackbar를 생성한다.
- 성공 문구는 “닉네임을 변경했어요”, “퀴즈 이름을 변경했어요”, “학습자료를 저장했어요”처럼 결과를 직접 말한다.
- route state는 한 번 소비한 뒤 같은 history entry에서 재표시되지 않게 정리한다.
- 저장 실패·검증 오류는 Snackbar로 옮기지 않고 현재 field/PageBanner/inline 복구 위치에 유지한다.

**접근성·반응형**

- Snackbar의 live announcement와 기존 수동 live region이 중복 낭독되지 않게 한다.
- 기본 하단 내비게이션, 퀴즈 고정 action, safe area와 겹치지 않도록 `SnackbarAvoidOverlap` 또는 동등한 공식 API를 확인한다.
- 연속 저장 시 교체/queue 정책을 정하고 한 번에 하나만 보이게 한다.

### 4. quiz overlay → ResponsiveDialog 검토

**감사 근거**

- `SheetFrame`이 퀴즈 나가기, 생성 준비, 풀이 현황, 미응답 제출, 결과 목록, 채점 수정 등 서로 다른 7개 작업을 모두 BottomSheet로 표현한다.
- 모바일에는 맥락 보존형 BottomSheet가 적합하지만, 풀이 현황·결과 목록·수정 폼은 넓은 화면에서 중앙 Dialog가 더 안정적인 탐색 폭을 줄 수 있다.

**제안**

- `ResponsiveDialog` snippet의 breakpoint 기반 BottomSheet/Dialog 전환을 먼저 프로토타입으로 비교한다.
- 확인 목적이 강한 나가기·제출은 AlertDialog가 더 맞는지 별도로 확인하되 이번 계획에서 자동 변경하지 않는다.
- 공통 frame을 교체해 open state, dismissible, header/body/footer 구조와 콜백 계약은 유지한다.

**접근성·반응형**

- controlled open, Escape, outside interaction, 제출 중 dismiss 제한, initial focus와 focus return을 회귀 검증한다.
- dialog 전환 breakpoint는 공식 기본값을 우선하고, 콘텐츠 순서와 action 위계가 viewport에 따라 바뀌지 않게 한다.
- 모바일 키보드와 safe area, 데스크톱 최대 높이와 내부 스크롤을 확인한다.

### 5. disclosure → Accordion

**감사 근거**

- 학습자료와 퀴즈 목록은 직접 만든 trigger, `aria-expanded`, `aria-controls`, 회전 chevron과 detail 영역을 공유하지만 구현이 두 파일에 나뉜다.
- 퀴즈 목록은 URL의 `expanded` ID Set으로 여러 항목을 동시에 펼치며 검색·페이지·focus와 함께 보존한다.

**제안**

- 목록 단위로 controlled `Accordion multiple`을 사용하고 item value에 material/quiz ID를 연결한다.
- 기존 `expanded` search parameter를 source of truth로 유지한다. Accordion 도입이 URL 계약을 바꾸지 않는다.
- trigger title/description, detail fetch, 생성 중 action overlay와 rename dialog는 content slot 안에서 보존한다.

**접근성·반응형**

- `headingLevel`을 페이지 outline에 맞추고 trigger 전체가 클릭 영역이어야 한다.
- 검색/페이지 변경, refetch 중 disabled, 펼친 상세 로딩 실패, focus 복귀를 확인한다.
- 긴 제목과 상태 문구가 모바일에서 chevron과 겹치지 않아야 한다.

### 6. 안내문 → Callout

**감사 근거**

- `LearningNotice`와 `.quiz-notice`는 neutral weak Box와 Text로 저장·동기화·자동 채점 제외 안내를 표현한다.
- 메시지의 정보성/주의/상태 의미가 시각적으로 구분되지 않고, 향후 링크나 icon을 넣을 때 로컬 변형이 늘어날 수 있다.

**제안**

- 사용자가 행동 전에 알아야 하는 지속 안내만 `Callout`으로 바꾼다.
- 동기화되지 않음, 기존 퀴즈에는 변경 미반영, 서술형 자동 점수 제외는 informative 또는 neutral 후보이다.
- 일반적인 field description, 결과 본문, 짧은 보조 문구는 Callout으로 감싸지 않는다.

**접근성·반응형**

- tone을 텍스트와 함께 사용하고 decorative icon은 숨긴다.
- 긴 문구·링크가 한 열에서 줄바꿈되며 컨테이너를 가로 overflow시키지 않는지 확인한다.

### 7. full/empty/error → ResultSection

**감사 근거**

- 홈 전체 오류는 제목·설명·재시도 CTA를 직접 조합한다.
- 학습 관리의 empty/error는 범위가 페이지 전체, 목록 영역, 기존 데이터 위 부분 오류로 서로 다르지만 같은 로컬 helper를 사용한다.
- 퀴즈 생성·제출 결과 화면에도 한 영역을 대체하는 결과 표현이 있다.

**제안**

- 페이지 또는 의미 있는 영역 전체를 대체하고 다음 action을 제공하는 상태에만 `ResultSection`을 사용한다.
- 우선 검증 대상은 홈 full error, 학습자료/퀴즈 목록의 초기 empty·full error, 퀴즈 생성 full error이다.
- stale data를 유지하는 refetch 오류, field 오류, 저장 실패 PageBanner처럼 맥락 안에 남아야 하는 오류는 현재 패턴을 유지한다.
- 빈 상태를 숨기는 것이 제품 의도인 홈 일부 section은 억지로 ResultSection을 추가하지 않는다.

**접근성·반응형**

- status 변경 시 focus 이동 또는 live announcement 필요 여부를 상태별로 정한다.
- action label은 “다시 시도”, “검색어 지우기”, “퀴즈 만들기”처럼 결과에서 벗어나는 구체 행동이어야 한다.
- 작은 영역에는 medium, 전체 화면에는 large 후보를 시각 검증하고 지나친 최소 높이를 강제하지 않는다.

### 8. 반복 list row → ListButtonItem snippet 검토

**감사 근거**

- 홈 `InteractiveList`와 학습 `LearningActionList`는 `List.Item`·`List.Content asChild`·button·title/detail/suffix를 직접 합성한다.
- 이는 공식 `ListButtonItem` snippet이 사용하는 구조와 같으며 현재 raw button은 오탐 대상이다.
- `PreviewRow`는 List 밖의 단일 탐색 행이라 같은 snippet으로 옮길지 별도 판단이 필요하다.

**제안**

- 다른 snippet 도입으로 `web/seed-design/ui` 소유 방식이 생긴 뒤 중복 감소 효과를 다시 측정한다.
- disabled data attribute, 복합 detail, action label suffix, divider와 item radius를 snippet props로 보존할 수 있을 때만 교체한다.
- `PreviewRow`가 실제 목록으로 묶이지 않으면 ListButtonItem을 억지로 사용하지 않는다.

**접근성·반응형**

- 중첩 button이 생기지 않게 하고 suffix에 별도 상호작용이 추가될 경우 event·focus 순서를 검증한다.
- 전체 행 클릭, disabled, 긴 제목 줄바꿈, 스크린 리더 목록 위치 정보가 유지되어야 한다.

## 단계별 적용 순서

- [ ] **T001 — 구현 기준 고정:** 설치 package, 공식 snippet 요구 버전, 생성 경로/alias, snippet 갱신 정책을 확인한다. 매핑이 열린 후보는 UX 검토 결과를 기록한다.
- [ ] **T002 — 선택 컨트롤 정합화:** 객관식 답안을 먼저 RadioSelectBox/RadioGroup으로 전환한 뒤 `ChoiceFieldset` 네 사용처를 의미에 따라 분리한다. 답안 payload와 퀴즈 상태 모델은 변경하지 않는다.
- [ ] **T003 — 공통 피드백 기반:** 앱 셸에 Snackbar provider/overlap 처리를 추가하고 닉네임·퀴즈 이름·학습자료 저장 성공을 연결한다. 실패 피드백은 기존 맥락에 유지한다.
- [ ] **T004 — 학습 관리 disclosure:** material/quiz 목록을 controlled Accordion으로 검증하고 URL `expanded`, 검색, pagination, focus, 상세 fetch를 보존한다.
- [ ] **T005 — 의미 상태 컴포넌트:** 선별된 `LearningNotice`/`.quiz-notice`를 Callout으로, full/empty/error를 ResultSection으로 바꾼다. inline 오류와 평문을 일괄 변환하지 않는다.
- [ ] **T006 — 반응형 퀴즈 오버레이:** ResponsiveDialog 프로토타입을 작은 WebView와 `md` 이상에서 비교하고 승인된 경우 공통 `SheetFrame`을 교체한다.
- [ ] **T007 — 선택적 목록 정리:** snippet 소유 경계가 안정된 경우에만 반복 List 합성을 `ListButtonItem`으로 정리한다.
- [ ] **T008 — 회귀 검증:** 정적 검사, 핵심 사용자 흐름, 접근성·반응형 시나리오를 통과시키고 변경 전후 스크린샷을 리뷰한다.

각 단계는 독립 PR로 나눌 수 있다. T001 없이 snippet 파일을 일괄 생성하지 않으며, T002~T006의 사용자 가시 결과를 검토한 뒤 다음 단계로 진행한다.

## 완료 및 검증 기준

### 기능 보존

- 문제 조건 선택, 객관식 답 선택, 자기평가, 채점 수정 값이 변경 전과 같은 request payload를 만든다.
- 학습자료·퀴즈 검색, pagination, 펼침 URL, 스크롤·focus 복원이 유지된다.
- 저장 성공은 한 번만 보이고 실패 시 입력과 복구 action이 유지된다.
- 퀴즈 overlay의 열기·닫기·제출 중 dismiss 제한이 유지된다.

### 접근성

- 라벨이 있는 단일 선택 그룹에서 키보드 선택과 focus-visible이 동작한다.
- Accordion trigger의 heading/expanded 관계, dialog title/description, focus trap과 focus return이 올바르다.
- 성공·로딩·오류가 중복 또는 누락 없이 보조 기술에 전달되고 색만으로 상태를 구분하지 않는다.
- 200% 글자 확대와 키보드만으로 핵심 흐름을 완료할 수 있다.

### 반응형·시각

- 320px 수준의 작은 WebView, 일반 모바일, `md` 이상 화면에서 가로 overflow와 action 겹침이 없다.
- Snackbar가 safe area, 하단 내비게이션과 퀴즈 action을 가리지 않는다.
- 긴 한국어 문제·선택지·자료 제목이 잘리지 않고 행과 overlay가 내용에 맞게 늘어난다.
- semantic token과 SEED recipe를 사용하며 기존 로컬 look-alike CSS는 사용처가 사라진 범위에서 제거된다.

### 저장소 검증

- `web/`에서 `pnpm verify`를 통과한다.
- 관련 기존 `.test.mjs`와 추가된 상태/route 테스트가 통과한다.
- snippet 생성 파일과 alias가 clean checkout에서도 해석되며 lockfile 변화가 의도와 일치한다.
- 제품/UX 원장을 바꾸지 않았다면 이 계획의 진행 기록만 갱신한다. 사용자 행동이 달라지면 구현 전에 책임 원장의 승인을 받는다.

## 구현 전 재확인할 현행 SEED 문서/API

공식 문서는 2026-09-03 감사 시점에 확인했다. 구현 직전 아래 항목의 사용 가능 버전, import 경로, props와 snippet 코드를 다시 확인한다.

- [Snippet 개념](https://seed-design.io/react/components/concepts/snippet): 생성 코드의 소유·수정·갱신 방식
- [Segmented Control](https://seed-design.io/react/components/segmented-control): `value`, `onValueChange`, 접근 가능한 이름, item 수와 사용 맥락
- [Radio Group](https://seed-design.io/react/components/radio-group): field label/error 통합, item size/tone, hidden input
- [Select Box](https://seed-design.io/react/components/select-box): `RadioSelectBoxRoot`, item label/description, 1열 layout
- [Snackbar](https://seed-design.io/react/components/snackbar): provider 위치, adapter strategy, timeout, pause, `SnackbarAvoidOverlap`
- [Dialog](https://seed-design.io/react/components/dialog): `ResponsiveDialogRoot`, `dialogBreakpoint`, dismiss/root props, action과 focus 관계
- [Accordion](https://seed-design.io/react/components/accordion): controlled `values`, `multiple`, `headingLevel`, responsive size
- [Callout](https://seed-design.io/react/components/callout): neutral/informative tone, actionable/dismissible 경계
- [Result Section](https://seed-design.io/react/components/result-section): medium/large, primary·secondary action, full/region result 용도
- [List](https://seed-design.io/react/components/list): `ListButtonItem` snippet, `List.Content asChild`, nested interactive suffix 제한
- 현재 `@seed-design/react`와 `@seed-design/css` changelog/upgrade guide: 설치 버전에서 위 API가 실제 export되는지와 snippet 최소 버전

## 제안 상태와 열린 질문

### 현재 제안

- 객관식 답안과 저장 성공 피드백을 첫 사용자 가시 개선으로 우선한다.
- disclosure와 상태 표현은 목록 단위로 묶어 교체해 화면 내 혼용 기간을 줄인다.
- ResponsiveDialog와 ListButtonItem은 공식 snippet 채택 경계를 정한 뒤 적용한다.

### 열린 질문

- 난이도와 최대 문제 수는 단순 폼 값인가, 선택 즉시 결과 설명이나 미리보기를 바꾸는 모드인가? 답에 따라 SegmentedControl 채택 여부가 달라진다.
- 객관식 답안은 카드형 RadioSelectBox가 장문 읽기에 적합한가, 더 조용한 RadioGroup이 NalQ의 문서형 분위기에 적합한가?
- Snackbar provider는 인증 전 화면까지 포함한 최상위에 둘지, signed-in 앱 셸에 둘지? 인증 메일 재요청 성공도 같은 정책에 포함할지?
- 연속 저장 성공 Snackbar는 즉시 교체할지 queue할지? 화면 이동 뒤에도 이전 성공을 보여줄 범위는 어디까지인가?
- 퀴즈 overlay 중 어떤 항목을 ResponsiveDialog로 묶고, 나가기·미응답 제출을 AlertDialog로 분리할지?
- Accordion 전환 시 현재 여러 항목 동시 펼침을 계속 허용하는가? 현재 URL 계약 보존을 기본 제안으로 두지만 UX 승인이 필요하다.
- Callout tone과 ResultSection asset을 어디까지 사용할지? 승인된 아이콘/asset 언어가 없으므로 장식 asset은 기본적으로 추가하지 않는다.
- 공식 snippet을 저장소에 생성한 뒤 업데이트는 수동 diff, CLI backup, 별도 검증 중 어떤 방식으로 관리할지?

열린 질문의 답이 나오기 전에는 해당 선택을 확정 요구사항이나 구현 결정으로 승격하지 않는다.

## 진행 기록

| 날짜 | 상태 | 결과 또는 차단 사유 |
| --- | --- | --- |
| 2026-09-03 | 계획 | 현재 웹 구현과 SEED v2 공식 문서를 비교해 후보, 오탐 기준, 적용 순서와 검증 기준을 작성함 |

## 결정 로그

- 이 문서는 제품 정책을 새로 정하지 않으므로 PRD가 아니라 Execution Plan으로 둔다.
- raw HTML 여부가 아니라 의미·상태 recipe 중복과 사용자 영향으로 수정 대상을 판정한다.
- 공식 snippet은 외부 완제품 패키지가 아니라 저장소가 소유하는 생성 소스라는 경계를 적용한다.
- 현재 구현과 공식 권장 맥락이 충돌할 수 있는 SegmentedControl, ResponsiveDialog, ListButtonItem은 감사만으로 최종 선택을 확정하지 않는다.
