---
document_type: trd
status: implemented
scope: web
last_updated: 2026-09-03
---

# [TRD] 웹 앱 셸과 최상위 탭 상태 보존

## 책임과 상위 원장

이 문서는 [제품 기반의 최상위 탭 전환 모션](../../../docs/product.md#최상위-탭-전환-모션)과 홈·학습·마이페이지 화면 명세를 웹에서 구현하는 앱 내부 경계를 기록한다. 사용자 행동과 화면 구조는 상위 원장을 따르며 이 문서가 새 정책을 정하지 않는다.

## 라우트와 마운트 경계

- 최상위 브라우저 라우터는 `createBrowserRouter`와 `RouterProvider`로 구성한다. 저장 전 편집 화면은 Data Router의 blocker를 사용해 헤더 back뿐 아니라 브라우저·WebView history 이동도 같은 이탈 확인으로 모은다.
- `/learning/quizzes`에서 시작한 생성 흐름의 검색·페이지·펼침·스크롤 복귀 상태는 노션 가져오기와 직접 입력 하위 route에도 전달한다. 노션 OAuth로 문서 밖을 왕복할 때는 검증된 내부 복귀 상태만 `sessionStorage`에 임시 보관하고 callback 처리와 함께 소비한다.
- 인증된 `/`, `/learning`, `/profile`은 하나의 `AuthenticatedAppShell` 부모 route를 공유한다. `/profile/*` 하위 route는 마이페이지 안의 계정·서비스 정보 화면이며 공통 하단 탭을 숨긴다.
- 처음 방문한 탭 화면만 지연 마운트하고, 이후에는 `inert`와 `aria-hidden`으로 비활성화한 채 마운트를 유지한다. 따라서 탭별 스크롤·React 로컬 상태·Query observer가 탭 모션 때문에 초기화되지 않는다.
- 공통 바텀 탭은 움직이지 않는 셸 평면에 두고 콘텐츠 panel만 수평 전환한다. 탭 클릭과 브라우저·시스템 back의 `POP` 모두 상대 위치와 관계없이 기존 panel이 왼쪽으로 나가고 새 panel이 오른쪽에서 들어온다. reduced motion에서는 위치 이동 없이 짧은 opacity 전환만 사용한다.
- 학습 메인 `/learning`은 공통 탭 panel 안에서 별도 `learning-route-enter` 모션을 재생하지 않는다. 학습 하위 route에만 내부 진입 class를 부여해 홈→학습과 학습→홈 모두 공통 앱 셸의 210ms 전환을 정확히 한 번 사용한다.
- 같은 목적지의 중복 선택은 무시한다. 전환 중 다른 탭 선택은 앞선 중간 목적지를 history에서 replace해 최신 목적지만 남긴다.
- `/learning/materials`, `/learning/materials/:materialId`, `/learning/quizzes`, `/learning/new`는 학습 하위 route로 등록하며 공통 탭을 숨긴다. `AuthenticatedLearningPage`는 현재 URL을 route ID로 해석해 각각 자료 목록, 자료 편집, 퀴즈 목록, 새 문제 만들기 페이지를 직접 렌더링하며 별도 `window.history` 화면 상태로 route를 흉내 내지 않는다.
- 퀴즈 생성·풀이·결과·복습 route는 기존 immersive route를 유지하고 앱 셸 탭 모션을 적용하지 않는다.

## Query와 adapter 경계

- 홈과 학습의 학습자료 Query는 기존 `private` Query key 및 5분 fresh 정책을 그대로 사용한다. 탭 전환은 invalidate나 refetch를 호출하지 않는다.
- 학습 메인의 최근 퀴즈는 `['private', 'quiz-review', 'latest']`, 복습 후보는 `['private', 'quiz-review', 'candidates', 3]`으로 분리한다. 두 영역은 로딩·오류·재시도를 독립적으로 처리하고, 퀴즈 제출·자기평가·복습 상태 변경 뒤에는 `['private', 'quiz-review']` prefix를 무효화한다.
- 새 복습 시작은 후보 또는 최근 퀴즈가 제공한 `sourceAttemptId`로 `POST /api/v1/review-sessions`를 호출하고 반환된 `reviewSessionId` route로 이동한다. 미완료 자기평가와 활성 복습 ID가 있으면 새 세션을 만들지 않고 해당 route를 우선한다.
- 홈 방문 요약은 `['private', 'home-visits', 'today']` key를 사용한다. `VITE_HOME_VISITS_API_ENABLED=true`일 때만 `POST /api/v1/home-visits`를 호출한다.
- API가 배포되지 않은 기본 환경의 `mock-unavailable` adapter는 방문일을 만들지 않고 `summary=null`만 반환한다. 홈은 닉네임 환영 문구를 유지하고 방문일 문장만 숨긴다.
- 개발용 퀴즈 fixture를 사용하는 경우 홈에 mock 경계를 명시한다. 실제 응답의 `quizTitle`이 없으면 `materialTitle`로 대체하지 않고 해당 복습 영역 오류로 처리한다.

## 검증 기준

- `web/`의 `pnpm verify`가 통과한다.
- 320 CSS px에서 가로 overflow가 없고 공통 탭의 현재 상태가 텍스트와 `aria-current`로 전달된다.
- 홈 스크롤과 학습 검색 상태가 홈·학습·마이페이지 왕복 후 유지된다.
- 탭 클릭과 `POP`은 상대 위치와 관계없이 기존 panel이 왼쪽으로 나가고 새 panel이 오른쪽에서 들어온다.
- 학습 메인 탭 진입에는 학습 내부 route 모션이 중첩되지 않고, 학습 하위 route에는 내부 진입 모션이 유지된다.
- 탭 왕복 뒤 이미 방문한 panel과 component instance가 유지되고 모션 때문에 같은 Query를 새로 만들지 않는다.
- `/learning/materials/:materialId`와 `/learning/new` 직접 진입은 해당 학습 내부 화면을 표시하며 공통 탭은 숨긴다.
