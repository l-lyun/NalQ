---
document_type: data-contract
status: review
scope: shared
---

# [Data Contract] 생성 결과 알림

- 소유 영역: `server/`
- 소비 영역: `web/`, WebView 기반 `app/`, 향후 FCM 전달 경계
- 관련 PRD: [퀴즈 생성 결과 알림](../prd/prd-quiz-generation-notifications.md)
- 관련 API Contract: [알림 API](contract-api-notifications.md)

## 목적과 경계

QuizSet terminal 결과를 사용자별로 90일 동안 지속 확인하고 읽음 상태와 중복 없는 결과 사건을 여러 클라이언트가 같은 의미로 해석하도록 한다. 화면의 상대 시각·Snackbar 노출 기록, FCM 기기 token과 제공자 전송 결과는 이 원장의 알림 읽음 상태가 아니다.

## 의미 규칙

- 알림은 한 사용자에게 발생한 불변 사건이며 `notificationId`로 식별한다.
- QuizSet 하나가 `GENERATING`에서 처음 terminal 상태로 전이될 때 `READY` 또는 `FAILED` 중 정확히 한 알림만 존재한다.
- QuizSet terminal 상태와 알림 생성은 같은 트랜잭션이다.
- `readAt=null`은 사용자가 아직 항목 선택 또는 모두 읽음을 하지 않았다는 뜻이다. 목록 조회, Snackbar 표시, FCM 전달 여부를 뜻하지 않는다.
- `FAILED` QuizSet은 내 퀴즈 목록에서 제외되어도 알림의 근거로 서버에 유지한다.
- 알림의 리소스 식별자는 이동 대상을 찾는 입력일 뿐 권한 증명이 아니다.

## 논리 데이터

| 개념 | 의미 | 필수 여부 | 제약 |
| --- | --- | --- | --- |
| `notificationId` | 공개 불변 알림 식별자 | 필수 | 전역 unique |
| `userId` | 알림 수신 사용자 | 필수 | 조회·읽음 소유권 기준 |
| `payloadVersion` | 웹·앱·향후 FCM payload 의미 버전 | 필수 | 초기값 `1` |
| `type` | `QUIZ_GENERATION_READY` 또는 `QUIZ_GENERATION_FAILED` | 필수 | QuizSet terminal 결과와 일치 |
| `quizSetId` | 결과를 만든 QuizSet | 필수 | 한 QuizSet당 terminal 알림 1건 unique |
| `materialId` | 실패 복구가 돌아갈 원본 학습자료 | 필수 | 대상 유실 뒤에도 알림 수명 동안 식별 의미 유지 |
| `targetName` | 알림 생성 당시의 `quizTitle` snapshot | 필수 | 생성 뒤 90일 동안 불변; 이후 QuizSet 이름 변경 영향 없음 |
| `failureCode` | `SOURCE_INSUFFICIENT` 또는 `GENERATION_FAILED` | 조건부 | 실패에서만 필수, 성공은 없음 |
| `actionType` | `FOCUS_QUIZ_IN_LIST` 또는 `RECONFIGURE_QUIZ` | 필수 | 알림 종류와 일치 |
| `readAt` | 사용자 읽음이 처음 확정된 시각 | 선택 | 처음 기록 뒤 불변 |
| `createdAt` | terminal 결과와 알림이 확정된 시각 | 필수 | 정렬·90일 보존 기준 |

물리 테이블명, FK 사용 여부, index와 cursor encoding은 서버 구현 책임이다. 다만 대상 QuizSet·학습자료가 먼저 없어져도 보존 기간 안의 알림은 대상 없음 상태로 읽을 수 있어야 하므로 물리 참조가 알림 조회를 막아서는 안 된다.

## 상태와 수명주기

| 상태 | 진입 조건 | 허용 행동 | 종료 조건 |
| --- | --- | --- | --- |
| unread | terminal 전이와 함께 알림 생성, `readAt=null` | 목록 조회, 개별 읽음, 모두 읽음 | 읽음 확정 또는 90일 만료 |
| read | 개별 선택 또는 모두 읽음으로 `readAt` 최초 기록 | 목록 조회 | 90일 만료 |
| expired | `createdAt`부터 90일 경과 | 사용자 조회·읽음 대상 아님 | 서버가 물리 정리 가능 |

- 읽음 여부는 보존 기간을 늘리거나 줄이지 않는다.
- 사용자 삭제 기능은 제공하지 않는다. 90일 만료 정리는 서버 운영 책임이며 API `items`와 `unreadCount`에서는 만료 즉시 제외한다.
- `모두 읽음`은 요청의 최신 경계보다 나중에 생긴 알림을 읽음 처리하지 않는다.
- 같은 QuizSet의 terminal 처리 재시도는 기존 알림을 반환하거나 유지하며 새 알림을 만들지 않는다.

## 트랜잭션 불변성

- `READY`: 유효 문제 저장, QuizSet `READY`, 실패 코드 제거와 성공 알림을 함께 확정한다.
- `FAILED/SOURCE_INSUFFICIENT`: 공개 가능한 빈 문제 세트를 남기지 않고 QuizSet 실패 코드와 실패 알림을 함께 확정한다.
- `FAILED/GENERATION_FAILED`: worker 예외·중단 복구가 QuizSet 실패를 확정할 때 실패 알림도 함께 확정한다.
- 알림 저장에 실패하면 해당 트랜잭션의 terminal 전이도 성공으로 커밋하지 않는다.
- 외부 FCM 전송은 이 트랜잭션에 포함하지 않는다. 알림 원장이 향후 전달 작업의 내구성 있는 입력이 된다.

## FCM 확장 경계

후속 [푸시 API·브리지 계약 초안](contract-api-push-notifications.md)은 Expo transport data에 최소 식별자만 싣고 나머지 사건 정보를 인증 조회로 가져오는 방식으로 구체화한다. 아래 전체 필드 목록은 이전 FCM 확장 예시이며, 새 transport의 필수 필드 목록은 후속 계약을 따른다. 알림 원장의 데이터 의미는 바뀌지 않는다.

향후 FCM data payload는 최소한 `notificationId`, `payloadVersion`, `type`, `quizSetId`, `materialId`, `failureCode`, `actionType`, `createdAt`을 사용해 inbox와 같은 사건을 식별한다. FCM 수신·열기는 서버 읽음과 자동으로 같지 않으며, 앱에서 사용자가 알림 행동을 선택한 뒤 기존 읽음 API로 동기화한다.

다음 데이터는 알림 원장에 섞지 않고 후속 별도 원장으로 둔다.

- 사용자 기기 등록: 기기 식별자, 플랫폼, FCM token, 활성·폐기 시각
- 기기별 전달: `notificationId + deviceId`, 전송 상태, 시도 횟수, 다음 재시도, provider message ID, 발송 시각
- 운영체제 알림 권한과 사용자 알림 설정

## 호환성과 마이그레이션

- 기존 `quiz_sets`의 `READY | FAILED` 의미는 유지한다.
- `FAILED` QuizSet은 물리 삭제하지 않고 내 퀴즈 목록 조회에서 제외한다.
- 아직 배포 전이므로 알림 기능을 포함한 최초 배포 이후 terminal 전이부터 알림을 생성한다. 이전 terminal QuizSet의 소급 알림과 migration은 없다.
- 알림 종류 추가는 `payloadVersion`, API 소비자와 90일 보존 의미를 함께 검토한다.

## 개인정보와 보존

- 퀴즈 이름은 사용자 생성 콘텐츠의 제목일 수 있으므로 알림 표시와 전송에 필요한 최소 범위로 취급한다.
- 학습자료 본문, 문제·정답·답안과 외부 생성 원문은 알림에 저장하지 않는다.
- 알림은 `createdAt`부터 90일 보관하고 이후 사용자 조회 대상에서 제외해 정리한다.
- 계정 삭제에 따른 알림 처리 방식은 사용자·인증 데이터의 계정 삭제 정책을 따른다.
- 로그에는 전체 알림 본문이나 대상 이름 대신 식별자, 종류와 공개 실패 코드만 최소로 남긴다.
