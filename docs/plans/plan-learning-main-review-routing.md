---
document_type: execution-plan
status: in-progress
scope: web-server
---

# [Execution Plan] 학습 메인 복습 목록과 route 뎁스 정상화

- 관련 PRD: [퀴즈 생성·풀이·결과·복습](../prd/prd-quiz-learning.md)
- 관련 UX: [학습 메인·내 학습자료·내 퀴즈](../ux/screen-learning.md), [퀴즈 생성부터 복습까지](../ux/flow-quiz-solving.md)
- 관련 Contract: [학습자료·퀴즈·복습 API](../contracts/contract-api-quiz-learning.md)
- 관련 TRD: [웹 앱 셸과 최상위 탭 상태 보존](../../web/docs/trd/trd-app-shell-navigation.md)

## 목표와 완료 조건

- 학습 메인을 `최근 퀴즈` → `새 문제 만들기` → `복습할 퀴즈` → `내 퀴즈 전체 보기` → `내 학습자료 전체 보기` 순서로 제공한다.
- 복습할 퀴즈는 최근 퀴즈와 중복되지 않는 최대 3개이며, 활성 복습 우선·최근 학습 활동 순으로 정확히 선별한다.
- 상태별 사용자 문구를 `전체 문제 다시 풀기`, `자기평가 이어하기`, `복습 이어하기`, `틀린 문제 N개 복습하기`, `결과 보기`로 통일한다.
- 학습 route가 URL만 바뀌고 메인 화면에 머무르지 않도록 뎁스와 화면을 일치시키고, 하위 route에서 전역 하단 내비게이션을 숨긴다.

완료 시 최근 퀴즈 중복 제외, `reviewQuestionCount=0` 제외, 활성 복습 우선, `lastLearningActivityAt DESC`, 동일 시각 `quizSetId ASC`, 상태별 행동과 직접 URL·back을 서버·웹 테스트와 실제 브라우저에서 확인한다.

## 범위와 비범위

### 범위

- 전용 복습 후보 조회와 선택한 QuizSet의 최신 완료 회차 복습 시작 계약
- 서버 후보 선별·정렬·제한과 복습 시작 검증
- 웹 학습 메인 데이터 조합, 정보 위계와 상태별 행동
- React Router route와 학습 화면 뎁스 정상화
- 관련 서버·웹 테스트와 실제 브라우저 확인

### 비범위

- 본 퀴즈·복습 내부의 풀이, 채점과 snapshot 정책 변경
- 내 퀴즈·내 학습자료의 검색·편집 기능 재설계
- 최상위 홈·학습·프로필 탭 전환 모션 변경
- 앱 네이티브/WebView 셸 변경
- 과거 회차 선택과 여러 회차 누적 복습

## 확정 결정

- 후보 조회는 `GET /api/v1/quiz-reviews/candidates?limit=3` 전용 경계로 제공한다.
- `limit` 기본값은 3, 허용 범위는 1..3이다.
- 후보는 QuizSet별 최신 완료 `MAIN`을 기준으로 하며 전역 최신 완료 `MAIN`의 QuizSet은 제외한다.
- 미해결 문항이 하나 이상인 후보만 활성 복습 우선, `lastLearningActivityAt DESC`, 동일 시각 `quizSetId ASC`로 정렬한다.
- 기존 `POST /api/v1/review-sessions`와 `sourceAttemptId` body는 유지하고, 서버가 해당 회차가 선택한 QuizSet의 사용자 최신 완료 `MAIN`인지 검증한다.
- 소유권, 미해결 0, 같은 원본의 활성 세션 재사용과 snapshot 규칙은 유지한다.
- 웹은 페이지네이션된 QuizSet 목록을 순회하거나 첫 페이지만 필터링하지 않는다.

## 작업

- [x] T001 공유 Contract의 후보 조회·복습 시작 형태 확정 — `docs/contracts/contract-api-quiz-learning.md`
- [x] T002 서버 조회·복습 시작 테스트를 먼저 추가하고 의도한 실패 확인 — 후보 endpoint `404`, 후보 service API 부재로 compile 실패 확인
- [x] T003 서버 후보 projection·query·controller와 선택 QuizSet 검증 구현 — 집중 테스트 PASS
- [ ] T004 웹 Contract 타입·API query option 추가
- [ ] T005 학습 route를 실제 페이지와 뎁스에 연결하고 중복 하단 내비게이션 제거
- [ ] T006 학습 메인 정보 위계·상태별 문구·부분 로딩/오류 구현
- [ ] T007 route·정렬·행동 우선순위 회귀 테스트와 모바일 브라우저 시나리오 확인
- [ ] T008 구현 결과를 웹 TRD에 동기화하고 저장소 전체 검증 실행

## 진행 기록

| 날짜 | 상태 | 결과 또는 차단 사유 |
| --- | --- | --- |
| 2026-08-28 | T001 완료 | 전용 후보 조회, limit, 필드, 정렬·안정 tie-breaker와 QuizSet별 복습 시작 계약 확정 |
| 2026-08-28 | T002 완료 | endpoint 부재 `404`와 service/repository API 부재 compile failure로 RED 확인 |
| 2026-08-28 | T003 완료 | 후보 조회와 선택 QuizSet 최신 완료 회차 검증 구현, 집중 테스트 PASS |
| 2026-08-28 | 서버 검증 PASS | `./gradlew fastTest`, `./gradlew integrationTest`, `./gradlew test`, `git diff --check` PASS |

## 남은 검증

- Web 후속: `pnpm -C web verify`, route·상태 테스트, 모바일 브라우저 시나리오
- 저장소 후속: 웹 구현 완료 뒤 루트 `./scripts/verify.sh fast`

## 남은 위험

- 이번 단계는 서버 계약과 구현까지만 완료했다. 웹은 새 후보 API를 아직 소비하지 않으며 학습 route 뎁스 결함도 T005까지 남아 있다.
- 후보 선별은 사용자 소유 QuizSet과 관련 attempt를 batch 조회해 메모리에서 정렬한다. 데이터 규모가 커지면 동일 계약을 보존하는 전용 DB projection으로 최적화할 수 있다.
