# API 계약: 학습자료·퀴즈·복습

- 상태: 검토 중
- 소유 영역: 학습자료, 퀴즈 생성·채점, 복습
- 소비 영역: 웹·앱 클라이언트
- 관련 기능명세: [학습자료 만들기](../../features/02-content-import.md), [퀴즈 생성·풀이·결과·복습](../../features/03-quiz-generation.md)
- 관련 흐름: [학습자료 만들기](../../flows/content-import.md), [퀴즈 생성부터 복습까지](../../flows/quiz-solving.md)

## 문서 책임

이 문서는 웹·앱·서버가 공유하는 HTTP 입력·출력, 상태, 권한, 멱등성, 정렬과 오류 의미를 정의한다. 문제 품질·채점·복습 정책은 기능명세가, 화면 사이의 중단·복구는 흐름 문서가 책임진다.

## 공통 계약

### 인증과 소유권

- 모든 엔드포인트는 유효한 Access Token이 필요하다.
- 서버는 Access Token에서 얻은 `userId`로 학습자료, 문제 세트, 본 퀴즈 회차와 복습 세션의 소유권을 판단한다. 요청 body의 `userId`는 받지 않는다.
- 존재하지 않거나 현재 사용자 소유가 아닌 리소스는 모두 `404 COMMON_003`으로 응답해 타인의 리소스 존재를 노출하지 않는다.

### 응답 봉투

서버의 기존 `ApiResponse`와 `ApiError(fields)` 모양을 유지한다. 클라이언트는 `error.message`가 아니라 안정적인 `error.code`로 분기한다.

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "COMMON_001",
    "message": "입력값이 올바르지 않습니다.",
    "fields": [
      { "field": "selectedTypes", "reason": "하나 이상 선택해 주세요." }
    ]
  }
}
```

- 모든 시각은 ISO 8601 UTC 문자열이다.
- 식별자는 예시의 문자열처럼 opaque하게 취급한다.
- 외부 생성 서비스와 Notion의 원본 응답, 모델명, 프롬프트, 내부 검증 상세와 stack trace는 공개 응답·오류에 포함하지 않는다.

### 멱등 키

- 명시된 쓰기 요청은 `Idempotency-Key` 헤더가 필수다.
- 키 범위는 `현재 사용자 + HTTP method + 정규화 path`다.
- 같은 키와 같은 의미의 payload 재요청은 최초 처리 결과와 같은 리소스 식별자를 반환한다.
- 같은 키에 다른 payload를 보내면 새 오류 코드를 만들지 않는다. 일반 payload 불일치는 `COMMON_001`, 이미 전이된 attempt·복습 상태와의 충돌은 각각 `ATTEMPT_001`, `REVIEW_001`로 처리한다. 생성 중인 같은 자료에 대한 충돌은 `QUIZ_001`이다.
- 멱등 결과를 확정하기 전에 클라이언트가 키를 바꾸면 중복 리소스가 생길 수 있으므로, 응답 유실 재시도에는 반드시 같은 키를 사용한다.

## 엔드포인트 목록

| 사용자 행동 | Method / Path | 성공 |
| --- | --- | --- |
| 학습자료 저장 | `POST /api/v1/learning-materials` | `201 Created` |
| 학습자료 수정 | `PATCH /api/v1/learning-materials/{materialId}` | `200 OK` |
| Notion 페이지 일회성 복사 | `POST /api/v1/learning-material-imports/notion` | `200 OK` |
| 문제 세트 생성 접수 | `POST /api/v1/learning-materials/{materialId}/quiz-sets` | `202 Accepted` |
| 자료의 활성 생성 조회 | `GET /api/v1/learning-materials/{materialId}/quiz-sets/active` | `200 OK` |
| 문제 세트 상태·풀이 데이터 조회 | `GET /api/v1/quiz-sets/{quizSetId}` | `200 OK` |
| 본 퀴즈 최종 제출 | `POST /api/v1/quiz-sets/{quizSetId}/attempts` | `201 Created` |
| 미완료 서술형 자기평가 회차 조회 | `GET /api/v1/quiz-sets/{quizSetId}/attempts/pending-self-assessment` | `200 OK` |
| 서술형 자기평가 저장 | `PUT /api/v1/quiz-attempts/{attemptId}/essay-assessments/{questionId}` | `200 OK` |
| 결과 조회 | `GET /api/v1/quiz-attempts/{attemptId}/result` | `200 OK` |
| 최신 복습 현황 조회 | `GET /api/v1/quiz-reviews/latest` | `200 OK` |
| 최신 대상 복습 세션 생성 | `POST /api/v1/review-sessions` | `201 Created` 또는 `200 OK` |
| 복습 세션 조회·재개 | `GET /api/v1/review-sessions/{reviewSessionId}` | `200 OK` |
| 복습 문항 답안 저장 | `POST /api/v1/review-sessions/{reviewSessionId}/responses` | `200 OK` |
| 복습 서술형 자기평가 저장 | `PUT /api/v1/review-sessions/{reviewSessionId}/essay-assessments/{questionId}` | `200 OK` |

## 학습자료

### 저장

`POST /api/v1/learning-materials`

Headers: `Authorization`, `Content-Type: application/json`, `Idempotency-Key`

```json
{
  "title": "운영체제 스케줄링",
  "content": "학습자료 본문",
  "sourceType": "PASTE"
}
```

- `content`: 필수, 공백뿐인 값 불가, 최대 20,000자.
- `sourceType`: 필수, `PASTE` 또는 `NOTION`.
- 제목의 필수 여부와 기본값은 [학습자료 기능명세의 열린 질문](../../features/02-content-import.md#열린-질문)이 확정될 때 이 계약을 갱신한다.

```json
{
  "success": true,
  "data": {
    "materialId": "mat_123",
    "title": "운영체제 스케줄링",
    "contentLength": 8240,
    "contentEditStatus": "EDITABLE",
    "createdAt": "2026-08-20T01:00:00Z"
  },
  "error": null
}
```

이 요청이 학습자료를 만드는 유일한 MVP 저장 경계다. 붙여넣기와 Notion 복사 결과를 위한 `importId`, preview, draft, 만료 API는 없다.

### 수정

`PATCH /api/v1/learning-materials/{materialId}`

```json
{
  "title": "수정한 제목",
  "content": "수정한 본문"
}
```

- 보낸 필드만 수정한다. 빈 요청은 `COMMON_001`이다.
- `title`은 본문 잠금과 관계없이 수정할 수 있다.
- `contentEditStatus=EDITABLE`일 때만 `content`를 수정할 수 있다. 생성 접수부터 `LOCKED_GENERATING`, 첫 성공 뒤 `LOCKED_PERMANENT`다.
- 성공한 문제 세트가 전혀 없는 생성이 실패하면 `LOCKED_GENERATING`에서 `EDITABLE`로 돌아간다. 하나라도 성공한 이력이 있으면 영구 잠금을 유지한다.

### Notion 단일 페이지 일회성 복사

`POST /api/v1/learning-material-imports/notion`

```json
{
  "pageId": "opaque-notion-page-id"
}
```

```json
{
  "success": true,
  "data": {
    "sourceType": "NOTION",
    "title": "복사한 페이지 제목",
    "content": "복사한 본문",
    "warnings": [
      {
        "code": "UNSUPPORTED_BLOCK_OMITTED",
        "message": "일부 지원하지 않는 블록은 제외됐어요. 저장 전에 내용을 확인해 주세요."
      }
    ]
  },
  "error": null
}
```

- 서버는 사용자가 선택한 페이지 하나를 요청 시점에 한 번 읽는다.
- 성공 응답은 프론트 편집용 값이며 서버 학습자료·draft·import job을 만들지 않는다.
- 복사된 본문이 20,000자를 넘더라도 잘라 저장하지 않고 초과 경고와 함께 반환할 수 있다. 20,000자 저장 제한은 사용자가 프론트에서 수정한 뒤 학습자료 저장 요청에서 검증한다.
- `warnings[].code`는 저장을 막지 않는 사용자 검토 신호다. 외부 블록 타입이나 파싱 예외 원문을 노출하지 않는다.
- 자동·수동 동기화 엔드포인트는 제공하지 않는다.

## 문제 세트 생성

### 생성 접수

`POST /api/v1/learning-materials/{materialId}/quiz-sets`

Headers: `Authorization`, `Content-Type: application/json`, `Idempotency-Key`

```json
{
  "selectedTypes": ["MULTIPLE_CHOICE", "FILL_BLANK", "SHORT_ANSWER", "ESSAY"],
  "difficulty": "NORMAL",
  "maxQuestionCount": 10
}
```

- `selectedTypes`: 중복 없는 1개 이상. 값은 `MULTIPLE_CHOICE`, `FILL_BLANK`, `SHORT_ANSWER`, `ESSAY`.
- `difficulty`: `EASY`, `NORMAL`, `HARD`.
- `maxQuestionCount`: 필수, `5`, `10`, `15` 중 하나.
- 요청을 접수하면 새 불변 `quizSetId`를 만들고 학습자료 본문을 원자적으로 잠근다.
- 같은 학습자료에 `GENERATING` 세트가 있으면 새 요청을 받지 않는다.
- 서버가 생성 작업 자체를 접수할 수 없으면 `503 QUIZ_002`를 반환하고 QuizSet을 만들거나 본문 잠금 상태를 바꾸지 않는다.

```json
{
  "success": true,
  "data": {
    "quizSetId": "qset_123",
    "materialId": "mat_123",
    "status": "GENERATING",
    "pollAfterSeconds": 3,
    "requestedConfig": {
      "selectedTypes": ["MULTIPLE_CHOICE", "FILL_BLANK", "SHORT_ANSWER", "ESSAY"],
      "difficulty": "NORMAL",
      "maxQuestionCount": 10
    },
    "createdAt": "2026-08-20T01:05:00Z"
  },
  "error": null
}
```

### 자료의 활성 생성 조회

`GET /api/v1/learning-materials/{materialId}/quiz-sets/active`

- 현재 인증 사용자가 소유한 학습자료에서 `GENERATING` 상태인 QuizSet 하나를 찾는다.
- 활성 생성이 없으면 정상 빈 상태이므로 새 오류 코드를 만들지 않고 `200 OK`, `data=null`을 반환한다.
- 활성 생성이 있으면 반환한 `quizSetId`로 [상태·풀이 데이터 조회](#상태풀이-데이터-조회)를 계속한다.

```json
{
  "success": true,
  "data": {
    "quizSetId": "qset_123",
    "materialId": "mat_123",
    "status": "GENERATING",
    "requestedConfig": {
      "selectedTypes": ["MULTIPLE_CHOICE", "ESSAY"],
      "difficulty": "NORMAL",
      "maxQuestionCount": 10
    },
    "pollAfterSeconds": 3
  },
  "error": null
}
```

활성 생성이 없을 때의 응답은 다음과 같다.

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

### 상태·풀이 데이터 조회

`GET /api/v1/quiz-sets/{quizSetId}`

- `GENERATING`, `READY`, `FAILED` 모두 `quizSetId`, `materialId`, `status`, `requestedConfig`를 반환한다.
- `GENERATING`은 다음 조회 권고값 `pollAfterSeconds`도 반환한다.
- `READY`는 유효 문제가 1개 이상이고 요청한 유형이 각각 최소 1문제 포함됐다는 뜻이다. 실제 수가 최대 문제 수보다 적을 수 있다.
- `FAILED`에는 문제를 포함하지 않는다. 사용자에게는 재시도 가능한 일반 안내만 제공하고 외부 생성 서비스 상세는 노출하지 않는다.
- 문제는 `number` 오름차순이다.

`GENERATING` 예시:

```json
{
  "success": true,
  "data": {
    "quizSetId": "qset_123",
    "materialId": "mat_123",
    "status": "GENERATING",
    "requestedConfig": {
      "selectedTypes": ["MULTIPLE_CHOICE", "ESSAY"],
      "difficulty": "NORMAL",
      "maxQuestionCount": 10
    },
    "pollAfterSeconds": 3,
    "questions": [],
    "failure": null
  },
  "error": null
}
```

`FAILED`의 `failure` 예시:

```json
{
  "code": "SOURCE_INSUFFICIENT",
  "message": "선택한 조건으로 충분한 문제를 만들지 못했어요. 자료나 문제 유형을 확인해 주세요.",
  "retryable": false
}
```

- `failure.code`는 `SOURCE_INSUFFICIENT` 또는 `GENERATION_FAILED`다.
- `SOURCE_INSUFFICIENT`: 유효 문제가 0개이거나 선택 유형을 각각 최소 한 문제 충족하지 못했다. 같은 입력의 즉시 반복보다 자료·유형 확인을 안내하며 기본 `retryable=false`다.
- `GENERATION_FAILED`: 접수 뒤 내부 생성 작업을 완료하지 못했다. 내부·LLM 상세는 숨기고 재시도가 가능하면 `retryable=true`다.
- 두 값은 비동기 QuizSet 작업 결과이지 HTTP `ApiError.code`가 아니다. 네트워크 실패를 `FAILED`로 추정해서는 안 된다.

`READY`의 각 문제 공통 필드:

```json
{
  "questionId": "question_1",
  "number": 1,
  "type": "MULTIPLE_CHOICE",
  "topic": "프로세스 스케줄링",
  "prompt": "일반 텍스트 문제"
}
```

유형별 풀이 전 공개 필드는 다음과 같다.

| 유형 | 추가 필드 | 규칙 |
| --- | --- | --- |
| `MULTIPLE_CHOICE` | `choices: [{ choiceId, text }]` | 4개 또는 5개. 정답 표시 없음 |
| `FILL_BLANK` | `segments: [{ kind: "TEXT", text } 또는 { kind: "BLANK", blankId }]` | 순서 보존, `BLANK` 1개 또는 2개 |
| `SHORT_ANSWER` | 없음 | 일반 텍스트 입력 |
| `ESSAY` | 없음 | 일반 텍스트 입력 |

정답, 허용 답안, 모범 답안, 핵심 포인트, 해설, 원문 근거와 내부 생성 메타데이터는 `READY` 풀이 데이터에 포함하지 않는다.

## 본 퀴즈 제출과 자기평가

### 최종 제출

`POST /api/v1/quiz-sets/{quizSetId}/attempts`

Headers: `Authorization`, `Content-Type: application/json`, `Idempotency-Key`

```json
{
  "responses": [
    {
      "questionId": "question_1",
      "selectedChoiceId": "choice_2"
    },
    {
      "questionId": "question_2",
      "blankAnswers": [
        { "blankId": "blank_1", "answer": "프로세스" }
      ]
    },
    {
      "questionId": "question_3",
      "text": "라운드 로빈"
    },
    {
      "questionId": "question_4",
      "text": "기아를 막기 위해 대기 시간을 반영한다."
    }
  ]
}
```

- `responses`에서 빠진 문제는 미응답 오답이다. 누락을 입력 검증 실패로 보지 않는다.
- 한 문제는 최대 한 번 포함하고 `type`은 보내지 않는다. 서버가 `questionId`의 실제 유형에 맞는 답안 필드를 검증한다.
- 빈칸 응답은 작성한 항목만 `blankAnswers: [{ blankId, answer }]`로 보낸다. 2개 중 1개처럼 일부 빈칸만 보내는 것을 허용하되 빠진 빈칸은 미응답으로 보고 문항 전체를 오답 처리한다.
- 누락한 `blankId`를 빈 문자열 답안으로 채우지 않는다. 알 수 없거나 중복된 `blankId`와 실제 유형에 맞지 않는 답안 모양은 `COMMON_001`이다.
- 단답형은 허용 답안에 대해 공백·대소문자 수준의 규칙 정규화만 적용하며 LLM을 호출하지 않는다.
- 제출 시에만 새 불변 본 퀴즈 회차를 만든다. 중간 위치·답안 저장 엔드포인트는 없다.

```json
{
  "success": true,
  "data": {
    "attemptId": "attempt_123",
    "status": "SELF_ASSESSMENT_REQUIRED",
    "automaticGrading": {
      "correctQuestionCount": 2,
      "gradedQuestionCount": 3
    },
    "pendingEssayQuestionIds": ["question_4"],
    "createdAt": "2026-08-20T01:20:00Z"
  },
  "error": null
}
```

- `status`: `SELF_ASSESSMENT_REQUIRED` 또는 `COMPLETED`.
- 작성한 서술형이 있으면 전자, 없으면 후자다. 미응답 서술형은 자동 `INCORRECT`이므로 대기 목록에 넣지 않는다.
- `automaticGrading`의 분모와 분자는 객관식·빈칸·단답형만 포함한다. 서술형을 0점으로 합산하지 않는다.
- 자기평가에 필요한 서술형 상세는 결과 조회와 같은 공개 상세 모양으로 제공하되, `COMPLETED` 전에는 전체 완료 결과로 표시하지 않는다.

### 미완료 서술형 자기평가 회차 조회

`GET /api/v1/quiz-sets/{quizSetId}/attempts/pending-self-assessment`

- 현재 인증 사용자와 QuizSet에 연결된 `SELF_ASSESSMENT_REQUIRED` attempt를 조회한다.
- 복원할 attempt가 없으면 정상 빈 상태이므로 새 오류 코드를 만들지 않고 `200 OK`, `data=null`을 반환한다.
- `pendingEssayQuestionIds`는 문제 번호 오름차순이다.
- 이 응답은 최종 제출로 이미 생성된 attempt를 다시 찾는 계약이다. 중간 답안이나 서버 본 퀴즈 draft를 만들거나 조회하지 않는다.

```json
{
  "success": true,
  "data": {
    "attemptId": "attempt_123",
    "quizSetId": "qset_123",
    "status": "SELF_ASSESSMENT_REQUIRED",
    "pendingEssayQuestionIds": ["question_4", "question_7"]
  },
  "error": null
}
```

복원할 attempt가 없을 때의 응답은 다음과 같다.

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

### 서술형 자기평가

`PUT /api/v1/quiz-attempts/{attemptId}/essay-assessments/{questionId}`

Headers: `Authorization`, `Content-Type: application/json`, `Idempotency-Key`

```json
{
  "assessment": "PARTIAL"
}
```

- `assessment`: `CORRECT`, `PARTIAL`, `INCORRECT`.
- 작성된 서술형이며 아직 자기평가 전인 문항에만 적용한다.
- 같은 키 재요청은 같은 결과를 반환한다. 이미 저장된 문항을 다른 평가로 바꾸려는 요청은 `ATTEMPT_001`이다.
- 마지막 대기 문항을 저장하면 attempt `status`가 원자적으로 `COMPLETED`가 된다.

```json
{
  "success": true,
  "data": {
    "attemptId": "attempt_123",
    "questionId": "question_4",
    "assessment": "PARTIAL",
    "status": "COMPLETED",
    "remainingSelfAssessmentCount": 0
  },
  "error": null
}
```

## 결과

`GET /api/v1/quiz-attempts/{attemptId}/result`

- `SELF_ASSESSMENT_REQUIRED` 상태에서도 저장된 자동 채점과 자기평가용 서술형 상세를 읽을 수 있다. 최종 결과 화면은 `COMPLETED`에서만 완료로 표현한다.
- `questionResults`는 `number` 오름차순이다.
- 원문 근거는 사용자에게 읽을 `sourceExcerpt` 문자열만 제공하고 내부 위치·retrieval·모델 메타데이터는 제외한다.

```json
{
  "success": true,
  "data": {
    "attemptId": "attempt_123",
    "quizSetId": "qset_123",
    "status": "COMPLETED",
    "summary": {
      "autoGrading": {
        "correctQuestionCount": 2,
        "gradedQuestionCount": 3
      },
      "essaySelfAssessment": {
        "correctCount": 0,
        "partialCount": 1,
        "incorrectCount": 0
      },
      "reviewQuestionCount": 2
    },
    "questionResults": [
      {
        "questionId": "question_1",
        "number": 1,
        "type": "MULTIPLE_CHOICE",
        "topic": "프로세스 스케줄링",
        "prompt": "일반 텍스트 문제",
        "choices": [
          { "choiceId": "choice_1", "text": "선입선출" },
          { "choiceId": "choice_2", "text": "우선순위" },
          { "choiceId": "choice_3", "text": "라운드 로빈" },
          { "choiceId": "choice_4", "text": "최단 작업 우선" }
        ],
        "response": { "selectedChoiceId": "choice_2" },
        "representativeAnswer": { "selectedChoiceId": "choice_3" },
        "outcome": "INCORRECT",
        "unanswered": false,
        "explanation": "학습을 위한 해설",
        "sourceExcerpt": "근거가 되는 원문 일부",
        "reviewRequired": true
      }
    ]
  },
  "error": null
}
```

- 자동 채점 `outcome`: `CORRECT`, `INCORRECT`.
- 서술형 `outcome`: `CORRECT`, `PARTIAL`, `INCORRECT`.
- 각 `questionResults` 항목은 다른 문제 조회 없이 렌더링할 수 있어야 한다. 객관식은 `choices: [{ choiceId, text }]`, 빈칸은 풀이 때와 같은 순서의 `segments`를 포함한다.
- 객관식 `response.selectedChoiceId`와 `representativeAnswer.selectedChoiceId`, 빈칸 `response.blankAnswers[].blankId`와 `representativeAnswer.blankAnswers[].blankId`는 각각 함께 반환된 보기·segment 식별자를 그대로 참조한다.
- 미응답은 `response=null`, `unanswered=true`, `outcome=INCORRECT`, `reviewRequired=true`로 포함한다. 별도 미응답 집계를 반환하지 않는다.
- `representativeAnswer`는 결과 설명에 필요한 대표 정답만 공개한다. 빈칸·단답형의 허용 정답 전체를 반환하지 않는다. 서술형은 `modelAnswer`와 `keyPoints`를 제공한다.
- 결과의 원래 `outcome`은 복습 뒤에도 바뀌지 않는다. `reviewRequired`는 현재 해결 여부에 따라 조회 시점에 계산할 수 있다.

## 복습 세션

### 최신 복습 현황

`GET /api/v1/quiz-reviews/latest`

- 현재 사용자의 가장 최근 `COMPLETED` 본 퀴즈 회차 하나만 조회한다.
- `attemptNumber`는 해당 `quizSetId` 안에서 현재 사용자가 완료한 본 퀴즈 회차의 1부터 시작하는 순번이다.
- `reviewQuestionCount`는 그 회차의 현재 미해결 문항 수다.
- 그 회차를 원본으로 한 활성 복습 세션이 있으면 `activeReviewSessionId`를 반환한다.

```json
{
  "success": true,
  "data": {
    "sourceAttemptId": "attempt_123",
    "quizSetId": "qset_123",
    "attemptNumber": 2,
    "reviewQuestionCount": 2,
    "activeReviewSessionId": "review_123"
  },
  "error": null
}
```

완료한 본 퀴즈가 없으면 오류나 별도 상태 코드를 만들지 않고 다음과 같이 `200 OK`로 반환한다.

```json
{
  "success": true,
  "data": {
    "sourceAttemptId": null,
    "quizSetId": null,
    "attemptNumber": null,
    "reviewQuestionCount": 0,
    "activeReviewSessionId": null
  },
  "error": null
}
```

최신 완료 회차는 있지만 미해결 문항이 없으면 세 식별 필드는 최신 회차 값으로 반환하고 `reviewQuestionCount=0`, `activeReviewSessionId=null`로 반환한다.

### 최신 대상 세션 생성

`POST /api/v1/review-sessions`

Headers: `Authorization`, `Content-Type: application/json`, `Idempotency-Key`

```json
{
  "sourceAttemptId": "attempt_123"
}
```

- `sourceAttemptId`는 [최신 복습 현황](#최신-복습-현황)에서 받은 가장 최근 완료 회차여야 한다.
- 해당 회차의 `reviewRequired=true` 문항을 번호 오름차순으로 snapshot해 서버 복습 세션을 만든다.
- 과거 회차를 임의 선택하거나 여러 회차를 합치지 않는다. 최신 회차가 바뀌었거나 대상이 없으면 `REVIEW_001`이다.
- 새 세션을 만들면 `201 Created`, 같은 `sourceAttemptId`의 활성 세션이 이미 있으면 새로 만들지 않고 `200 OK`로 기존 세션을 반환한다.
- 더 오래된 회차의 미완료 복습 세션은 최신 회차의 새 복습을 막지 않으며 복습 탭 대상에 합치지 않는다.

```json
{
  "success": true,
  "data": {
    "reviewSession": {
      "reviewSessionId": "review_123",
      "sourceAttemptId": "attempt_123",
      "status": "IN_PROGRESS",
      "reviewQuestionCount": 2,
      "questions": []
    }
  },
  "error": null
}
```

### 세션 조회·재개

`GET /api/v1/review-sessions/{reviewSessionId}`

- `status`: `IN_PROGRESS`, `COMPLETED`.
- 문항은 원래 `number` 오름차순이고 한 세션에 각 문항이 한 번만 존재한다.
- 문항별 `processingStatus`는 `PENDING`, `SELF_ASSESSMENT_REQUIRED`, `GRADED`다.
- `PENDING` 문항은 풀이 전 공개 모양만 포함한다. `GRADED` 문항은 저장된 응답과 판정을 함께 반환해 응답 유실 후 복구할 수 있다.
- `SELF_ASSESSMENT_REQUIRED` 서술형은 저장된 `answer`, `modelAnswer`, `keyPoints`, `explanation`, `sourceExcerpt`를 반환한다. 이 상태가 하나 있으면 최상위 `nextQuestionId`는 다른 미처리 문항이 아니라 해당 서술형 `questionId`다.
- `nextQuestionId`는 번호가 가장 낮은 미처리 문항이며 모두 처리되면 `null`이다.

```json
{
  "success": true,
  "data": {
    "reviewSessionId": "review_123",
    "sourceAttemptId": "attempt_123",
    "status": "IN_PROGRESS",
    "nextQuestionId": "question_4",
    "questions": [
      {
        "questionId": "question_4",
        "number": 4,
        "type": "ESSAY",
        "topic": "기아 방지",
        "prompt": "에이징의 목적을 설명하세요.",
        "processingStatus": "SELF_ASSESSMENT_REQUIRED",
        "answer": "오래 기다린 프로세스의 우선순위를 높인다.",
        "modelAnswer": "대기 시간이 긴 프로세스의 우선순위를 점차 높여 기아를 방지한다.",
        "keyPoints": ["대기 시간 반영", "우선순위 상승", "기아 방지"],
        "explanation": "에이징은 장기 대기 프로세스가 실행 기회를 얻도록 돕습니다.",
        "sourceExcerpt": "대기 시간이 길수록 우선순위를 높이는 에이징으로 기아를 방지한다."
      }
    ]
  },
  "error": null
}
```

### 문항 답안 저장

`POST /api/v1/review-sessions/{reviewSessionId}/responses`

Headers: `Authorization`, `Content-Type: application/json`, `Idempotency-Key`

```json
{
  "questionId": "question_1",
  "selectedChoiceId": "choice_2"
}
```

- body는 본 퀴즈 제출의 응답 항목과 같은 모양이며 `questionId`와 유형별 답안 필드만 보낸다. `type`은 보내지 않는다.
- 빈칸은 `blankAnswers: [{ blankId, answer }]`를 사용하며 일부만 보내는 것을 허용한다. 빠진 빈칸이 있으면 문항 전체를 오답 처리하고 누락 항목을 빈 문자열로 보정하지 않는다.
- 객관식·빈칸·단답형은 응답 저장과 판정을 한 번에 수행하고 `CORRECT`면 `RESOLVED`, 아니면 `UNRESOLVED`다.
- 서술형은 응답을 저장한 뒤 `SELF_ASSESSMENT_REQUIRED`를 반환하고 모범 답안·핵심 포인트·해설·원문 근거를 공개한다.
- `Idempotency-Key`는 문항 제출마다 새로 만들며 응답 유실 시 같은 문항·같은 payload와 함께 재사용한다.
- 이미 처리한 문항을 다른 키나 답으로 덮어쓰지 않는다. 같은 키 재요청만 기존 결과를 반환하며 같은 세션에서 재출제하지 않는다.

자동 채점 응답 예시:

```json
{
  "success": true,
  "data": {
    "questionId": "question_1",
    "processingStatus": "GRADED",
    "outcome": "CORRECT",
    "reviewStatus": "RESOLVED",
    "sessionStatus": "IN_PROGRESS",
    "nextQuestionId": "question_2",
    "representativeAnswer": { "selectedChoiceId": "choice_2" },
    "explanation": "학습을 위한 해설",
    "sourceExcerpt": "근거가 되는 원문 일부"
  },
  "error": null
}
```

서술형 답안 저장은 `processingStatus=SELF_ASSESSMENT_REQUIRED`, 저장한 `answer`, `modelAnswer`, `keyPoints`, `explanation`, `sourceExcerpt`를 반환한다. `reviewStatus`는 자기평가 전까지 `null`이고 `nextQuestionId`는 제출한 서술형 `questionId`다.

### 복습 서술형 자기평가

`PUT /api/v1/review-sessions/{reviewSessionId}/essay-assessments/{questionId}`

Headers: `Authorization`, `Content-Type: application/json`, `Idempotency-Key`

```json
{
  "assessment": "CORRECT"
}
```

- `assessment`: `CORRECT`, `PARTIAL`, `INCORRECT`.
- `CORRECT`면 `RESOLVED`, `PARTIAL` 또는 `INCORRECT`면 `UNRESOLVED`다.
- 각 판정 뒤 `nextQuestionId`를 반환한다. 마지막 문항이면 세션을 `COMPLETED`로 바꾸고 `nextQuestionId=null`을 반환한다.
- `UNRESOLVED` 문항도 같은 세션에는 다시 넣지 않으며 다음 복습 세션의 후보로 남긴다.
- 복습 응답·판정은 원래 attempt 응답·판정을 수정하지 않는다.

```json
{
  "success": true,
  "data": {
    "questionId": "question_4",
    "assessment": "PARTIAL",
    "reviewStatus": "UNRESOLVED",
    "sessionStatus": "COMPLETED",
    "nextQuestionId": null
  },
  "error": null
}
```

## 오류

### 최소 안정 코드

| 조건 | HTTP | 코드 | 복구 |
| --- | --- | --- | --- |
| 필드 누락·형식·허용 enum/개수, 일반 멱등 payload 불일치 | `400` | 기존 `COMMON_001` | `fields`에 따라 입력 수정 또는 원래 payload로 재시도 |
| 읽을 수 없는 JSON | `400` | 기존 `COMMON_002` | 요청 본문 수정 |
| 없거나 소유하지 않은 리소스, 접근할 수 없는 Notion 페이지 | `404` | 기존 `COMMON_003` | 목록·권한·페이지 선택 확인 |
| 예상하지 못한 서버 오류 | `500` | 기존 `COMMON_999` | 잠시 후 같은 멱등 키로 재시도 |
| 인증 정보 없음·잘못됨·만료 | `401` | 기존 `AUTH_005` | 갱신 또는 재로그인 |
| 잠긴 학습자료 본문 수정 | `409` | `MATERIAL_001` | 제목만 수정하거나 새 자료 만들기 |
| 학습자료 본문 20,000자 초과 | `413` | `MATERIAL_002` | 본문을 줄인 뒤 같은 저장 흐름 재시도 |
| 같은 학습자료에 이미 `GENERATING` 작업이 있음 | `409` | `QUIZ_001` | 기존 생성 상태 확인 |
| 생성 작업을 접수할 수 없는 일시적 서버 상태 | `503` | `QUIZ_002` | 새 QuizSet이 만들어지지 않았음을 확인하고 같은 멱등 키로 재시도 |
| `READY`가 아닌 세트 제출, 제출·자기평가 상태 또는 attempt 멱등 payload 충돌 | `409` | `ATTEMPT_001` | 최신 문제 세트·attempt 상태와 결과 확인 |
| 완료·처리된 복습 세션/문항 변경 또는 복습 멱등 payload 충돌 | `409` | `REVIEW_001` | 세션을 재조회해 다음 미처리 문항으로 이동 |

- MVP의 안정 오류 코드는 `COMMON_001/002/003/999`, `AUTH_005`, `MATERIAL_001/002`, `QUIZ_001/002`, `ATTEMPT_001`, `REVIEW_001`로 제한한다.
- Notion, 외부 생성 서비스, LLM 또는 세부 검증 단계별 공개 오류 코드를 추가하지 않는다.
- 비동기 생성 실패는 정상 상태 조회의 `status=FAILED`로 전달한다. HTTP 오류와 혼용하지 않는다.
- `error.message`는 사용자가 다음 행동을 이해할 수준으로 쓰고 내부 예외·LLM·Notion 상세를 노출하지 않는다.
- 입력 오류의 `fields`는 `responses[0].selectedChoiceId`나 `responses[1].blankAnswers[0].blankId`처럼 클라이언트가 해당 입력을 찾을 수 있는 경로를 사용한다.

## 정렬·불변성과 보존

- 문제, 결과, 복습 문항은 원래 문제 `number` 오름차순이다.
- 문제 세트는 생성마다 새로 만들며 생성 성공 뒤 내용을 수정하지 않는다.
- 본 퀴즈 attempt는 최종 제출마다 새로 만들고 제출 응답과 판정을 수정하지 않는다. 서술형 자기평가는 비어 있던 판정을 한 번 완성하는 전이만 허용한다.
- 복습 세션은 원래 attempt의 학습 상태를 참조하지만 원래 응답과 판정을 변경하지 않는다.
- 서버에는 본 퀴즈 중간 진행·답안 API가 없다. 최종 제출 전 기기 임시 기록의 7일 보존과 사용자 분리는 클라이언트 책임이며 HTTP 계약이 아니다.

## 개인정보와 로그

- 학습자료 본문, 사용자 답안, 모범 답안과 원문 근거는 민감한 학습 콘텐츠로 취급한다.
- 요청·응답 본문, Notion 접근 자격, 외부 생성 서비스 원문과 `Idempotency-Key` 원문을 일반 애플리케이션 로그에 남기지 않는다.
- 운영 로그는 요청 추적 식별자, 사용자 내부 식별자, 리소스 식별자, 공개 오류 코드와 상태 전이에 필요한 최소 메타데이터만 남긴다.

## 호환성과 폐기

- 이 계약은 기존 인증 응답 봉투와 오류 규칙을 확장하되 인증 계약을 변경하지 않는다.
- `importId`, 서버 preview·draft·만료, 파일 업로드, Notion 동기화, 서버 풀이 draft API는 MVP에서 폐기된 전제다. 과거 초안 소비자가 있다면 이 계약 구현 전에 제거하고 `POST /api/v1/learning-materials` 직접 저장으로 전환한다.
- 문제 유형별 필드나 enum을 바꾸면 웹·앱의 입력·결과 읽기와 서버 채점을 동시에 갱신해야 한다. 새 필드는 하위 호환 가능한 선택 필드를 우선하고, 기존 enum 의미를 재사용하지 않는다.

## 열린 질문

- [학습자료 기능명세의 열린 질문](../../features/02-content-import.md#열린-질문)이 입력·검증 계약에 영향을 주므로 확정 시 이 문서를 함께 갱신한다.
- 재연결 뒤 페이지 선택 복원 여부는 [학습자료 흐름의 열린 질문](../../flows/content-import.md#열린-질문)이 책임진다.
- `Idempotency-Key` 결과의 최소 보존 기간. 비동기 생성의 종료 전과 일반적인 네트워크 응답 유실 재시도 동안에는 만료할 수 없다.
