---
document_type: trd
status: implemented
scope: web
---

# [TRD] 퀴즈 생성 결과 알림

## 책임

- 서버 알림 원장을 foreground에서 조회하고 종 badge, Snackbar, 별도 알림 페이지에 같은 상태를 제공한다.
- 생성 요청의 `quizSetId`를 사용자별 localStorage에 한 건 보존해 생성 화면을 떠난 뒤에도 terminal 상태를 확인한다.
- Snackbar 전달 이력은 서버 `readAt`과 분리해 같은 사용자·기기에서 `notificationId`당 한 번만 표시한다.

## 상태 경계

- `openmd.quiz-generation.pending.v1:{userId}`: 현재 기기의 pending `quizSetId`, `materialId`, 저장 시각. 생성 요청 성공과 활성 생성 복원에서 갱신하고 terminal 확인 뒤 제거한다.
- `openmd.notification.snackbar-delivered.v1:{userId}`: 최근 90일의 Snackbar claim. 최대 200건으로 제한하며 서버 읽음 상태를 변경하지 않는다.
- TanStack Query `['private', 'notifications']`: 첫 페이지 badge·Snackbar 상태와 알림 페이지 pagination을 무효화하는 공통 prefix다.

## 실행 흐름

1. 인증 shell이 mount되면 알림 첫 페이지를 조회한다.
2. 문서가 visible이고 online이며 pending이 있으면 QuizSet 상태를 `pollAfterSeconds`로 확인한다.
3. terminal을 확인하면 pending을 제거하고 알림·내 퀴즈 query를 무효화한다.
4. 첫 페이지의 unread 항목 중 이 기기에서 claim하지 않은 ID만 Snackbar 후보로 사용한다.
5. 생성 route에서는 Snackbar를 생략하고, 한 건은 결과별 Snackbar, 여러 건은 합산 Snackbar를 표시한다.
6. background와 offline에서는 polling을 중단하고 visible·online 복귀 시 TanStack Query의 focus/reconnect 재조회를 사용한다.

## 화면 연결

- 일반 인증 shell 우측 상단에 SEED `IconBellLine`, `NotificationBadge`, `NotificationBadgePositioner`를 배치한다.
- `/notifications`는 최신순 cursor 목록, 개별 읽음, `모두 읽음`, `더보기`를 제공한다.
- 성공은 `/learning/quizzes?focus={quizSetId}`, 실패는 `/learning/{materialId}/quiz`로 이동한다.
- 내 퀴즈 조회는 `focusQuizSetId`를 서버에 전달하고 반환된 page의 대상 카드로 focus한다.

## 검증

- `pnpm exec node --test`
- `pnpm verify`
