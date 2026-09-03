---
document_type: api-contract
status: review
scope: shared
---

# [API Contract] 생성 결과 알림

- 소유 영역: `server/`
- 소비 영역: `web/`, WebView 기반 `app/`
- 관련 기능명세: [퀴즈 생성 결과 알림](../prd/prd-quiz-generation-notifications.md)
- 관련 데이터: [알림 데이터](contract-data-notifications.md)

## 목적

인증 사용자가 퀴즈 생성 화면 밖이나 다음 foreground 진입에서 최근 생성 성공·실패를 조회하고, 개별 또는 일괄 읽음 상태를 여러 클라이언트에서 일관되게 관리하도록 한다. 실시간 전송과 OS 푸시를 이 계약에 포함하지 않는다.

## 인증과 권한

- 모든 endpoint는 Access Token 인증이 필요하다.
- 사용자는 자신의 알림만 조회하고 읽음 처리할 수 있다.
- 다른 사용자의 `notificationId`는 존재 여부를 구분하지 않고 `404 COMMON_003`으로 처리한다.
- `quizSetId`, `materialId`, `notificationId`는 권한 증명이 아니다.

## 알림 모양

```json
{
  "notificationId": "7d02e54b-2dc3-4fc4-a681-0ceae00e3ee3",
  "payloadVersion": 1,
  "type": "QUIZ_GENERATION_READY",
  "quizSetId": "qset_123",
  "materialId": "123",
  "targetName": "운영체제 스케줄링 퀴즈",
  "failureCode": null,
  "actionType": "FOCUS_QUIZ_IN_LIST",
  "targetAvailable": true,
  "readAt": null,
  "createdAt": "2026-09-03T01:05:00Z"
}
```

- `notificationId`는 불변 공개 식별자다.
- `payloadVersion`의 초기값은 `1`이며 이후 FCM data payload도 같은 의미 버전을 사용한다.
- `type`은 `QUIZ_GENERATION_READY | QUIZ_GENERATION_FAILED`다.
- `failureCode`는 실패 알림에서 `SOURCE_INSUFFICIENT | GENERATION_FAILED`, 성공에서는 `null`이다.
- `actionType`은 성공에서 `FOCUS_QUIZ_IN_LIST`, 실패에서 `RECONFIGURE_QUIZ`다.
- `targetAvailable=false`이면 클라이언트는 action을 추정 실행하지 않고 대상 없음 안내 뒤 읽음 처리한다.
- `targetName`은 알림 생성 당시 `quizTitle` snapshot이며 90일 보존 중 QuizSet 이름이 바뀌어도 변경하지 않는다.
- 상대 시각과 사용자 문구는 `createdAt`, `type`, `failureCode`를 사용한 표현 책임이며 API의 안정 문자열로 보내지 않는다.

## 최신 알림 목록

`GET /api/v1/notifications?cursor={opaque}`

- `cursor`는 선택이며 없으면 최신 항목부터 조회한다.
- 한 응답은 20개를 넘지 않는다. 검색·종류·읽음 filter와 가변 page size는 지원하지 않는다.
- 로그인 완료, 새로고침, foreground visible 복귀와 online 복귀 때 첫 페이지를 조회할 수 있다. 이 조회는 알림을 읽음 처리하지 않는다.

```json
{
  "success": true,
  "data": {
    "items": [],
    "unreadCount": 0,
    "nextCursor": null,
    "hasNext": false
  },
  "error": null
}
```

- 정렬은 `createdAt DESC, notificationId DESC`의 안정적인 최신순이다.
- `nextCursor`는 마지막 항목보다 오래된 다음 페이지 경계를 나타내는 opaque 문자열이며 클라이언트가 내부 값을 해석하거나 만들지 않는다.
- 새 알림이 첫 페이지 앞에 추가돼도 이미 받은 cursor의 다음 페이지 경계는 바뀌지 않는다.
- `unreadCount`는 조회 시점에 보존 기간 안에 있는 사용자의 전체 읽지 않은 알림 수다. 화면은 100 이상을 `99+`로 줄여도 API 값은 정확한 정수다.
- 90일 보존 기간이 지난 알림은 `items`와 `unreadCount`에 포함하지 않는다.

## 개별 읽음

`PUT /api/v1/notifications/{notificationId}/read`

Body 없음.

```json
{
  "success": true,
  "data": {
    "notificationId": "7d02e54b-2dc3-4fc4-a681-0ceae00e3ee3",
    "readAt": "2026-09-03T01:07:00Z",
    "unreadCount": 2
  },
  "error": null
}
```

- 처음 성공한 요청이 서버 `readAt`을 기록한다.
- 이미 읽은 알림의 재요청은 기존 `readAt`을 바꾸지 않고 현재 결과를 반환하는 멱등 동작이다.
- 알림 항목 선택이 읽음 요청의 제품 진입점이다. 목록 열기와 Snackbar 표시에는 이 endpoint를 호출하지 않는다.

## 모두 읽음

`PUT /api/v1/notifications/read-all`

```json
{
  "throughNotificationId": "7d02e54b-2dc3-4fc4-a681-0ceae00e3ee3"
}
```

```json
{
  "success": true,
  "data": {
    "readAt": "2026-09-03T01:08:00Z",
    "updatedCount": 4,
    "unreadCount": 1
  },
  "error": null
}
```

- `throughNotificationId`는 사용자가 `모두 읽음`을 선택할 때 목록에서 본 가장 최신 알림이다.
- 서버는 현재 사용자의 해당 알림과 같거나 더 오래된, 보존 기간 안의 unread 알림만 읽음 처리한다. 그 뒤 도착한 더 새로운 알림은 unread로 남긴다.
- 같은 경계를 다시 요청해도 이미 읽은 시각을 바꾸지 않는 멱등 동작이다.

## 내 퀴즈 대상 focus 지원

성공 알림의 `FOCUS_QUIZ_IN_LIST`를 완료하기 위해 [내 퀴즈 목록](contract-api-quiz-learning.md#내-퀴즈-목록-조회이름-검색)은 선택적인 `focusQuizSetId`를 지원한다.

- `focusQuizSetId`가 있으면 `query`와 함께 보내지 않고, 현재 정렬과 page size에서 해당 `READY` QuizSet을 포함하는 page를 반환한다.
- 요청한 QuizSet이 없거나 사용자 소유가 아니거나 `FAILED`로 목록에서 제외된 경우 `404 COMMON_003`이다.
- 클라이언트 route는 반환된 page를 반영한 뒤 해당 카드로 focus한다. 알림 목록 응답에 변할 수 있는 page 번호를 저장하지 않는다.

## 오류 응답

| 조건 | HTTP 상태 | 안정적인 오류 코드 | 사용자 복구 가능성 |
| --- | --- | --- | --- |
| 잘못되거나 만료된 cursor | `400` | `COMMON_001` | 첫 페이지부터 다시 조회 |
| `throughNotificationId` 누락·형식 오류 | `400` | `COMMON_001` | 최신 표시 항목으로 다시 요청 |
| 알림·focus QuizSet 없음 또는 다른 사용자 소유 | `404` | `COMMON_003` | 대상 없음 안내 뒤 최신 목록 조회 |
| 인증 만료 | `401` | `AUTH_005` | 재인증 뒤 같은 사용자 알림 재조회 |
| 일시적 서버 오류 | `500` | `COMMON_999` | 기존 목록을 유지하고 재시도 |

## 동작 규칙

- 알림 생성은 공개 POST가 아니다. QuizSet terminal 전이와 같은 서버 트랜잭션에서 내부적으로 한 건 생성한다.
- QuizSet 하나에는 성공·실패 중 terminal 알림 한 건만 존재한다. 중복 worker나 재시도가 두 알림을 만들지 않는다.
- 목록 조회와 읽음 요청 실패를 QuizSet `FAILED`로 해석하지 않는다.
- foreground polling은 HTTP cache로 새 결과를 숨기지 않아야 하며 background polling은 하지 않는다.
- 목록 첫 페이지 응답의 새 `notificationId`를 기준으로 Snackbar 후보를 판정한다. Snackbar 전달 이력은 서버 `readAt`과 별개다.

## 외부 제공자 경계

- 현재 외부 알림 제공자를 사용하지 않는다.
- FCM 도입 시 이 계약의 `notificationId`, `payloadVersion`, `type`, 대상 식별자, `failureCode`, `actionType`, `createdAt`을 OpenMD가 소유하는 data payload 의미로 재사용한다.
- FCM token 등록, 플랫폼 권한, 제공자 message ID, 전송 시도·재시도는 별도 기기·전달 계약으로 추가한다. 제공자 원시 오류를 이 알림 목록 API에 노출하지 않는다.

## 개인정보와 로그

- 알림에는 퀴즈 이름과 리소스 식별자만 포함한다. 학습자료 본문, 문제, 정답, 답안과 외부 생성 원문은 포함하지 않는다.
- 일반 로그에 전체 응답이나 사용자 대면 대상 이름을 남기지 않고 문제 해결에 필요한 `notificationId`, `userId`, `quizSetId`, 공개 종류와 결과만 최소 기록한다.

## 호환성과 변경

- 기존 QuizSet 상태 API는 유지한다. 생성 화면과 이 기기 pending 감시는 계속 해당 API를 사용한다.
- 기존 `GET /api/v1/quiz-sets`는 `FAILED`를 목록에서 제외하고 `focusQuizSetId`를 추가하므로 서버·웹 소비자를 함께 전환해야 한다.
- 아직 배포 전이므로 알림 기능을 포함한 최초 배포 이후 terminal 전이부터 알림을 생성한다. 기존 terminal QuizSet의 소급 생성과 migration endpoint는 제공하지 않는다.
- FCM·WebSocket·SSE는 이 버전의 동작 조건이 아니다.
