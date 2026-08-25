---
document_type: trd
status: review
scope: app
---

# [TRD · App] 앱 WebView 퀴즈 상태 설계

- 상태: 검토 중
- 소유 영역: `app/`
- 관련 원장:
  - [퀴즈 PRD](../../../docs/prd/prd-quiz-learning.md)
  - [퀴즈 생성부터 복습까지의 흐름](../../../docs/ux/flow-quiz-solving.md)
  - [학습자료·퀴즈·복습 API 계약](../../../docs/contracts/contract-api-quiz-learning.md)
  - [웹 퀴즈 화면 상태 설계](../../../web/docs/trd/trd-quiz-solving.md)

## 문서 책임

이 문서는 Expo 앱의 WebView 실행 환경에서 퀴즈 화면 상태와 생성 조건 로컬 값을 다루는 방법을 정의한다. 풀이 상태 모델은 웹 원장이, 사용자 흐름과 서버 계약은 상위 원장이 책임진다.

## 확정 구현 매핑

- MVP에서 본 퀴즈와 복습의 현재 문항·미제출 답안은 WebView 화면 메모리에만 둔다. 앱 종료·WebView 재생성·새로고침 뒤에는 복원하지 않고 첫 문항부터 다시 푼다.
- WebView 내부 `localStorage`에는 `userId + quizSetId` 범위의 생성 요청 조건 `selectedTypes`, `difficulty`, `maxQuestionCount`만 저장할 수 있다. 답안·현재 문항·attempt ID·제출 payload는 저장하지 않는다.
- 제출 시작 시 attempt ID와 payload를 현재 WebView 메모리에 한 번 만들고, 응답을 받지 못했지만 같은 화면이 살아 있을 때만 같은 값으로 `PUT`을 재요청한다. WebView는 hash나 payload fingerprint를 계산하지 않는다.
- 틀린 문제 다시 풀기도 현재 문항과 미제출 답안을 WebView 메모리에만 유지한다. 전체 답안은 한 번 제출하고 응답을 받지 못했지만 화면 상태가 남아 있을 때만 같은 review session에 같은 payload로 재시도한다.
- 로그아웃 시에는 현재 사용자의 생성 조건 로컬 값을 삭제한다.
- WebView가 최종 제출 payload를 만들 때도 웹 원장의 `questionId` 중심 응답과 `blankAnswers` 매핑을 사용한다. HTTP 계약에 없는 `type`이나 누락 빈칸의 빈 문자열을 추가하지 않는다.
- 생성 조건 로컬 값은 서버 중간 draft가 아니며 다른 기기로 동기화하지 않는다. 값이 유실돼도 생성 상태 조회와 퀴즈 진행에는 영향을 주지 않는다.

## 현재 앱 구조와 수명주기

- 현재 `app/`은 WebView 셸이므로 웹과 동일한 저장 로직을 WebView 문서 컨텍스트 안에서 실행한다.
- 생성 조건을 읽을 때는 현재 인증된 `userId`와 `quizSetId`가 모두 일치하는 값만 사용한다. 계정 전환이나 로그아웃 시 이전 사용자 값을 표시하지 않는다.
- 저장소 접근 실패나 손상된 생성 조건 값은 서버로 전송하지 않고 폐기한다. 생성 중에는 일반 안내를, `READY`에서는 서버 문제 목록으로 계산한 실제 문제 수·유형을 표시한다.

## 향후 확장

- 미제출 답안 이어풀기나 더 강한 복구 보장이 필요해지면 별도 제품·API 결정을 거쳐 서버 draft 방식으로 발전시킬 수 있다. MVP에는 포함하지 않는다.

## 기술 검증 기준

- 같은 `userId + quizSetId`의 생성 조건만 WebView 로컬에서 복원하며 다른 사용자·문제 세트나 손상된 값은 사용하지 않는다.
- 앱 종료·재실행, WebView 재생성, 새로고침 뒤 현재 문항과 미제출 답안을 복원하지 않고 첫 문항부터 다시 시작한다.
- 제출 결과를 확인하지 못했지만 WebView 화면 상태가 남아 있으면 같은 attempt ID와 payload로 재시도하고, 화면 상태를 잃으면 자동 복구하지 않는다.
- 로그아웃하면 현재 사용자의 생성 조건 로컬 값을 삭제한다.
- 생성 조건 로컬 값이 삭제·초기화되거나 기기가 바뀌어도 서버 상태와 실제 문제 목록으로 화면을 표시한다.
