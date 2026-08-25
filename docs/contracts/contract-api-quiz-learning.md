---
document_type: api-contract
status: review
scope: shared
---

# [API Contract] 학습자료·퀴즈·복습

- 소유 영역: 학습자료, 퀴즈 생성·채점, 복습
- 소비 영역: 웹·앱 클라이언트
- 관련 기능명세: [학습자료 만들기](../prd/prd-content-import.md), [퀴즈 생성·풀이·결과·복습](../prd/prd-quiz-learning.md)
- 관련 흐름: [학습자료 만들기](../ux/flow-content-import.md), [퀴즈 생성부터 복습까지](../ux/flow-quiz-solving.md)
- 공유 데이터 의미: [학습자료와 퀴즈 데이터 계약](contract-data-quiz-learning.md)

## 문서 책임

이 문서는 웹·앱·서버가 공유하는 HTTP 입력·출력, 상태, 권한, 멱등성, 정렬과 오류 의미를 정의한다. 문제 품질·채점·복습 정책은 기능명세가, 화면 사이의 중단·복구는 흐름 문서가 책임진다.

## 공통 계약

### 인증과 소유권

- 모든 엔드포인트는 유효한 Access Token이 필요하다.
- 서버는 Access Token에서 얻은 `userId`로 학습자료, 문제 세트, 본 퀴즈 회차와 복습 세션의 소유권을 판단한다. 요청 body의 `userId`는 받지 않는다.
- attempt, 문항 결과와 복습 리소스는 요청 경로의 개별 ID 존재 여부만 확인하지 않는다. 현재 사용자의 학습자료 → 문제 세트 → attempt → attempt 문항 연결 전체가 일치하는지 검증하며, 복습은 원본 MAIN attempt와 원본 attempt 문항까지 같은 소유권 연결 안에 있는지 검증한다.
- `quizSetId`, `attemptId`, 복습 ID와 문항 ID는 리소스 식별자일 뿐 권한 증명이 아니다. 클라이언트가 알고 있는 ID만으로 소유권 검증을 생략하지 않는다.
- 존재하지 않거나 현재 사용자 소유가 아닌 리소스는 모두 `404 COMMON_003`으로 응답해 타인의 리소스 존재를 노출하지 않는다.

### 풀이 전 정답 비노출 경계

- 문제 세트 상태 조회와 본 퀴즈·복습의 풀이 전 응답은 각 절에 명시된 공개 필드만 허용하는 allowlist다. 서버의 정답 기준, 허용 답안, 예시·모범 답안, 해설, 원문 근거와 내부 생성 메타데이터는 도메인 모델에 존재하더라도 추가로 직렬화하지 않는다.
- 본 퀴즈와 복습은 같은 풀이 전 공개 모양을 사용한다. 복습 대상이라는 이유로 이전 정답, 이전 제출 답안이나 채점 결과를 풀이 응답에 포함하지 않는다.
- 제출·채점 이후에도 결과 계약이 명시한 대표 정답과 예시·모범 답안만 공개한다. 자동 채점에 사용하는 전체 허용 답안 목록과 내부 정규화 값은 반환하지 않는다.
- 정답 기준은 제출·채점 서비스 내부에서만 읽는다. 풀이 조회 DTO와 결과 DTO는 분리된 응답 경계를 유지한다.

### 브라우저 전송 경계

- stateless Bearer API는 브라우저가 자격 증명을 자동 첨부하는 Cookie API가 아니므로 CSRF token을 요구하지 않는다. 유효한 Access Token과 엔드포인트별 권한 검사는 그대로 적용한다.
- 일반 API CORS는 설정된 정확한 origin에만 허용하며 wildcard origin을 사용하지 않는다. 허용 method는 `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`이고 허용 header는 `Authorization`, `Content-Type`, `Idempotency-Key`다.
- 브라우저 Refresh Cookie endpoint는 이 예외에 포함하지 않는다. 해당 endpoint의 credentialed CORS와 정확한 `Origin` + `X-OpenMD-CSRF` guard는 [인증 API 계약](contract-api-authentication.md#cors와-csrf)을 유지한다.

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
- `materialId`의 wire 형식은 서버 `BIGINT` 식별자의 10진 문자열이다. 클라이언트는 이를 숫자로 변환하거나 산술에 사용하지 않고 opaque 문자열로 보존한다.
- 외부 생성 서비스와 Notion의 원본 응답, 모델명, 프롬프트, 내부 검증 상세와 stack trace는 공개 응답·오류에 포함하지 않는다.

### 쓰기 요청 식별과 재시도

- 학습자료 생성과 비동기 QuizSet 생성 접수처럼 아직 자연스러운 리소스 식별자가 없는 생성 요청만 `Idempotency-Key` 헤더를 사용한다. 기존 키 형식과 재시도 규칙은 해당 엔드포인트 절에서 정의한다.
- 본 퀴즈 최종 제출은 클라이언트가 `crypto.randomUUID()` 수준의 안전한 난수원으로 만든 UUID v4를 `attemptId`로 사용한다. 서버는 parse 가능한 UUID 형식과 DB unique 제약만 확인하며 hash·fingerprint 충돌 방지 체계를 추가하지 않는다.
- attempt UUID는 비밀 정보가 아니므로 서버에서 hash하지 않는다. 서버는 `attemptId`의 전역 unique 제약과 소유권·QuizSet 일치 검증만 수행한다.
- 같은 `attemptId`의 재요청은 payload fingerprint를 계산하거나 비교하지 않고 최초로 확정된 attempt를 반환한다. 클라이언트 버그로 같은 UUID에 다른 답안을 보낸 경우에도 최초 제출 우선이며 새 회차를 만들지 않는다.
- 다른 사용자 또는 QuizSet이 이미 사용한 UUID이면 `409 ATTEMPT_001`과 `fields.field=attemptId`를 반환한다. 자동으로 새 UUID를 만들어 재전송하지 않는다.
- 복습 최종 제출은 이미 존재하는 `reviewSessionId` 하나에 한 번만 확정되므로 별도 요청 키를 만들지 않는다. 같은 세션 재요청은 최초 제출 결과를 반환한다.
- 서술형 자기평가와 단답형 판정 수정처럼 특정 리소스에 원하는 현재 값을 저장하는 `PUT`은 저장된 현재 값 자체로 반복 요청을 처리한다. 별도 멱등 키, payload fingerprint와 공개 revision을 두지 않는다.

## 엔드포인트 목록

| 사용자 행동 | Method / Path | 성공 |
| --- | --- | --- |
| 학습자료 저장 | `POST /api/v1/learning-materials` | `201 Created` |
| 학습자료 수정 | `PATCH /api/v1/learning-materials/{materialId}` | `200 OK` |
| Notion 페이지 일회성 복사 | `POST /api/v1/learning-material-imports/notion` | `200 OK` |
| 문제 세트 생성 접수 | `POST /api/v1/learning-materials/{materialId}/quiz-sets` | `202 Accepted` |
| 자료의 활성 생성 조회 | `GET /api/v1/learning-materials/{materialId}/quiz-sets/active` | `200 OK` |
| 문제 세트 상태·풀이 데이터 조회 | `GET /api/v1/quiz-sets/{quizSetId}` | `200 OK` |
| 본 퀴즈 최종 제출 | `PUT /api/v1/quiz-sets/{quizSetId}/attempts/{attemptId}` | `201 Created` 또는 `200 OK` |
| 미완료 서술형 자기평가 회차 조회 | `GET /api/v1/quiz-sets/{quizSetId}/attempts/pending-self-assessment` | `200 OK` |
| 서술형 자기평가 저장 | `PUT /api/v1/quiz-attempts/{attemptId}/essay-assessments/{questionId}` | `200 OK` |
| 결과 조회 | `GET /api/v1/quiz-attempts/{attemptId}/result` | `200 OK` |
| 단답형 현재 판정 수정 | `PUT /api/v1/quiz-attempts/{attemptId}/short-answer-gradings/{questionId}` | `200 OK` |
| 최신 복습 현황 조회 | `GET /api/v1/quiz-reviews/latest` | `200 OK` |
| 최신 대상 복습 세션 생성 | `POST /api/v1/review-sessions` | `201 Created` 또는 `200 OK` |
| 복습 세션 조회·재개 | `GET /api/v1/review-sessions/{reviewSessionId}` | `200 OK` |
| 복습 전체 답안 제출 | `PUT /api/v1/review-sessions/{reviewSessionId}/submission` | `200 OK` |
| 복습 결과 조회 | `GET /api/v1/review-sessions/{reviewSessionId}/result` | `200 OK` |
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

- `title`: 필수. Unicode 공백 기준으로 앞뒤를 제거한 값을 저장하며, 정리한 결과가 1~255 Unicode code point여야 한다. 공백뿐인 값에 기본 제목을 만들지 않는다.
- `content`: 저장할 원문을 앞뒤 제거하거나 자르지 않는다. Unicode 공백 문자만으로 이루어진 값은 거절하고, 공백·줄바꿈을 포함한 Unicode code point 수가 최대 20,000이어야 한다. 정확히 20,000자는 허용하며 20,001자부터 `413 MATERIAL_002`다.
- `sourceType`: 필수, 정확히 `PASTE` 또는 `NOTION`이다. `NOTION`은 사용자가 Notion 복사 결과에서 시작했다는 출처 표시일 뿐 페이지 연결·동기화 상태가 아니며, 저장 전 내용을 수정해도 `NOTION`을 유지한다.

```json
{
  "success": true,
  "data": {
    "materialId": "123",
    "title": "운영체제 스케줄링",
    "contentLength": 8240,
    "contentEditStatus": "EDITABLE",
    "createdAt": "2026-08-20T01:00:00Z"
  },
  "error": null
}
```

이 요청이 학습자료를 만드는 유일한 MVP 저장 경계다. 붙여넣기와 Notion 복사 결과를 위한 `importId`, preview, draft, 만료 API는 없다.

- 생성 직후 `contentEditStatus`는 항상 `EDITABLE`이다.
- `contentLength`는 요청 검증과 같은 Unicode code point 계산을 사용한다.
- 같은 사용자·같은 `Idempotency-Key`·같은 의미의 payload 재요청은 새 행을 만들지 않고 최초와 동일한 `201 Created` 응답 body를 반환한다. 여기에는 `materialId`, `createdAt`과 `contentLength`가 포함된다.
- 학습자료 생성에서 payload의 의미는 `앞뒤 공백을 제거한 title + 원문 content + sourceType`이다. JSON 속성 순서나 표현만 다른 요청은 같고, 이 세 값 중 하나라도 다르면 `400 COMMON_001`이다.
- 입력 검증 실패와 저장 트랜잭션 롤백은 키를 점유하지 않는다. 같은 키의 동시 요청도 학습자료 한 건만 만들고 두 요청은 같은 성공 결과를 관찰해야 한다.
- 생성 멱등 결과는 연결된 학습자료가 존재하는 동안 만료하지 않는다. MVP에는 학습자료 삭제 API가 없으므로 자동 만료하지 않는다.

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
  "selectedTypes": ["MULTIPLE_CHOICE", "FILL_IN_THE_BLANK", "SHORT_ANSWER", "ESSAY"],
  "difficulty": "NORMAL",
  "maxQuestionCount": 10
}
```

- `selectedTypes`: 중복 없는 1개 이상. 값은 서버 `QuestionType`과 같은 `MULTIPLE_CHOICE`, `FILL_IN_THE_BLANK`, `SHORT_ANSWER`, `ESSAY`.
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
    "materialId": "123",
    "status": "GENERATING",
    "pollAfterSeconds": 3,
    "requestedConfig": {
      "selectedTypes": ["MULTIPLE_CHOICE", "FILL_IN_THE_BLANK", "SHORT_ANSWER", "ESSAY"],
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
    "materialId": "123",
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
- `requestedConfig`는 생성 요청을 다시 보여주기 위한 값이지 `READY|FAILED` 판정 기준이 아니다. QuizSet 물리 컬럼 추가 여부는 이 API 계약이 정하지 않으며, 실제 문제 수와 포함 유형은 확정된 `questions`에서 계산한다.
- `GENERATING`은 다음 조회 권고값 `pollAfterSeconds`도 반환한다.
- `READY`는 유효 문제가 1개 이상이라는 뜻이다. 실제 수가 최대 문제 수보다 적거나 요청한 유형 일부가 포함되지 않아도 된다.
- `FAILED`에는 문제를 포함하지 않는다. 사용자에게는 재시도 가능한 일반 안내만 제공하고 외부 생성 서비스 상세는 노출하지 않는다.
- 문제는 `number` 오름차순이다.

`GENERATING` 예시:

```json
{
  "success": true,
  "data": {
    "quizSetId": "qset_123",
    "materialId": "123",
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
  "message": "학습자료에서 문제를 만들지 못했어요. 자료나 조건을 확인해 주세요.",
  "retryable": false
}
```

- `failure.code`는 `SOURCE_INSUFFICIENT` 또는 `GENERATION_FAILED`다.
- `SOURCE_INSUFFICIENT`: 품질 기준을 충족한 유효 문제가 0개다. 같은 입력의 즉시 반복보다 자료·조건 확인을 안내하며 기본 `retryable=false`다.
- `GENERATION_FAILED`: 접수 뒤 내부 생성 작업에서 최종 확정할 유효 문제를 하나도 남기지 못했다. 검증과 저장을 마친 유효 문제가 1개 이상이면 일부 결과를 사용할 수 없어도 `READY`다. 내부·LLM 상세는 숨기고 재시도가 가능하면 `retryable=true`다.
- 상태 재조회에서도 같은 실패 의미를 반환할 수 있도록 서버는 QuizSet의 `failure_code`를 보존한다. `message`와 `retryable`은 저장된 코드에 대한 공개 정책으로 계산한다.
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
| `FILL_IN_THE_BLANK` | `blanks: [{ blankId, number }]` | 공통 `prompt`의 `[1]`, `[2]` 마커와 `number`로 연결. 1개 또는 2개 |
| `SHORT_ANSWER` | 없음 | 일반 텍스트 입력 |
| `ESSAY` | 없음 | 일반 텍스트 입력 |

빈칸형 예시:

```json
{
  "questionId": "question_2",
  "number": 2,
  "type": "FILL_IN_THE_BLANK",
  "topic": "자료구조 처리 순서",
  "prompt": "큐는 [1] 방식이고 스택은 [2] 방식이다.",
  "blanks": [
    { "blankId": "blank_1", "number": 1 },
    { "blankId": "blank_2", "number": 2 }
  ]
}
```

정답, 허용 답안, 모범 답안, 핵심 포인트, 해설, 원문 근거와 내부 생성 메타데이터는 `READY` 풀이 데이터에 포함하지 않는다.

## 본 퀴즈 제출과 자기평가

### 최종 제출

`PUT /api/v1/quiz-sets/{quizSetId}/attempts/{attemptId}`

Headers: `Authorization`, `Content-Type: application/json`

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
- 단답형은 문제에 저장된 허용 답안 각각과 사용자 답에 Unicode NFC, Unicode whitespace의 ASCII 공백 변환·연속 축약과 앞뒤 제거, `toLowerCase(Locale.ROOT)`를 순서대로 적용한 뒤 완전 일치만 비교한다. 서버에서 Unicode whitespace는 Java `Character.isWhitespace` 또는 `Character.isSpaceChar` 중 하나라도 참인 code point를 뜻한다. 구두점 제거, 띄어쓰기 전체 제거, 번역, 약어 확장, 오타 보정, 형태소·의미 유사도 판정과 LLM 호출은 하지 않는다.
- 예를 들어 허용 답안이 `fifo` 하나면 `FIFO`는 자동 정답이지만 `선입선출`, `first in first out`은 자동 오답이다. 자동 정답으로 인정할 표현은 문제 생성 시 별도 허용 답안으로 저장돼 있어야 한다.
- 제출 시에만 새 불변 본 퀴즈 회차를 만든다. 중간 위치·답안 저장 엔드포인트는 없다.
- `attemptId`는 클라이언트가 최종 제출 직전에 생성한 UUID v4다. 현재 제출 화면이 살아 있는 동안에는 같은 ID와 `responses`로 명시적 재요청을 할 수 있다.
- 처음 보는 `attemptId`이면 `201 Created`로 attempt와 채점 결과를 원자적으로 만든다.
- 같은 사용자·같은 QuizSet의 이미 존재하는 `attemptId`이면 request body를 다시 적용하지 않고 최초 attempt의 현재 상태를 `200 OK`로 반환한다. 서버는 별도 payload fingerprint나 replay 테이블을 만들지 않는다.
- 같은 `attemptId`가 다른 사용자 또는 다른 QuizSet에 이미 존재하면 기존 리소스를 노출하지 않고 `409 ATTEMPT_001`로 처리한다. 이는 난수 충돌보다 잘못된 식별자 재사용을 막기 위한 방어다.
- 클라이언트가 새로고침·종료로 답안과 attempt ID를 잃으면 서버는 미제출 상태나 동일 payload를 탐색하지 않는다. 사용자는 다시 풀어 새 UUID로 제출할 수 있고 서로 다른 UUID의 attempt는 별도 회차로 보존한다.

```json
{
  "success": true,
  "data": {
    "attemptId": "550e8400-e29b-41d4-a716-446655440000",
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
    "attemptId": "550e8400-e29b-41d4-a716-446655440000",
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

Headers: `Authorization`, `Content-Type: application/json`

```json
{
  "assessment": "PARTIAL"
}
```

- `assessment`: `CORRECT`, `PARTIAL`, `INCORRECT`.
- 작성된 서술형이며 아직 자기평가 전인 문항에만 적용한다.
- 이미 같은 평가가 저장된 재요청은 현재 결과를 반환한다. 이미 저장된 문항을 다른 평가로 바꾸려는 요청은 `ATTEMPT_001`이다.
- 마지막 대기 문항을 저장하면 attempt `status`가 원자적으로 `COMPLETED`가 된다.

```json
{
  "success": true,
  "data": {
    "attemptId": "550e8400-e29b-41d4-a716-446655440000",
    "questionId": "question_4",
    "assessment": "PARTIAL",
    "status": "COMPLETED",
    "remainingSelfAssessmentCount": 0
  },
  "error": null
}
```

### 단답형 현재 판정 수정

`PUT /api/v1/quiz-attempts/{attemptId}/short-answer-gradings/{questionId}`

Headers: `Authorization`, `Content-Type: application/json`

```json
{
  "outcome": "CORRECT"
}
```

- `outcome`: `CORRECT` 또는 `INCORRECT`. 사용자가 확인한 현재 판정으로 교체할 값이다.
- 현재 사용자 소유이며 `COMPLETED`인 attempt의 답을 작성한 `SHORT_ANSWER`에만 적용한다. 객관식·빈칸·서술형, 미응답 단답형과 완료 전 attempt는 `409 ATTEMPT_001`이다.
- 최초 자동 판정과 현재 판정의 공유 의미·생명주기는 [데이터 계약의 본 퀴즈 회차와 채점 결과](contract-data-quiz-learning.md#본-퀴즈-회차와-채점-결과)를 따른다.
- 같은 현재 판정을 반복 요청하면 값을 바꾸지 않는 no-op 성공이다. 다른 판정을 요청하면 최신 `userOverrideOutcome`을 교체하고 마지막으로 커밋된 요청을 현재 판정으로 사용한다.
- 별도 `Idempotency-Key`, payload fingerprint, 과거 응답 snapshot, `expectedRevision`, `gradingRevision`과 `summary.revision`을 저장하거나 노출하지 않는다.
- 성공 응답의 `data`는 바로 아래 `GET /api/v1/quiz-attempts/{attemptId}/result`와 같은 전체 결과 projection이다. 클라이언트는 부분 점수 delta를 합치지 않고 성공한 전체 결과로 화면 상태를 교체한다.
- 한 화면에서는 수정 요청을 하나씩 보낸다. 다른 화면이나 탭의 요청과 경합하면 마지막 커밋이 현재 값이며 결과 재조회로 수렴한다.
- 이미 활성 복습 세션이 있으면 그 세션의 snapshot을 추가·삭제하지 않는다. 결과의 `summary.reviewQuestionCount`는 원본 회차의 현재 후보 수이고 활성 세션의 남은 문항 수와 다른 의미이며, 수정된 대상 여부는 다음 복습 세션부터 반영한다.
- 사용자 수정은 해당 attempt 문항에만 적용하며 문제의 허용 답안 목록이나 다른 attempt의 자동 판정에 전파하지 않는다.

## 결과

`GET /api/v1/quiz-attempts/{attemptId}/result`

- `SELF_ASSESSMENT_REQUIRED` 상태에서도 저장된 자동 채점과 자기평가용 서술형 상세를 읽을 수 있다. 최종 결과 화면은 `COMPLETED`에서만 완료로 표현한다.
- `questionResults`는 `number` 오름차순이다.
- 원문 근거는 사용자에게 읽을 `sourceExcerpt` 문자열만 제공하고 내부 위치·retrieval·모델 메타데이터는 제외한다.

```json
{
  "success": true,
  "data": {
    "attemptId": "550e8400-e29b-41d4-a716-446655440000",
    "quizSetId": "qset_123",
    "status": "COMPLETED",
    "summary": {
      "scoredGrading": {
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
        "explanation": "학습을 위한 해설",
        "sourceExcerpt": "근거가 되는 원문 일부"
      },
      {
        "questionId": "question_2",
        "number": 2,
        "type": "SHORT_ANSWER",
        "topic": "큐의 처리 순서",
        "prompt": "큐의 pop 순서를 무엇이라고 하나요?",
        "response": { "answer": "선입선출" },
        "representativeAnswer": { "answer": "fifo" },
        "outcome": "CORRECT",
        "explanation": "큐는 먼저 들어온 데이터가 먼저 나오는 FIFO 구조입니다.",
        "sourceExcerpt": "큐는 FIFO 원칙으로 데이터를 처리한다."
      }
    ]
  },
  "error": null
}
```

- 객관식·빈칸의 자동 채점 `outcome`: `CORRECT`, `INCORRECT`.
- 답을 작성한 단답형은 화면에 표시할 현재 유효 `outcome`을 제공한다. 서버가 보존하는 `automaticOutcome`과 `userOverrideOutcome`은 현재 결과 화면에서 직접 사용하지 않으므로 이 조회 projection에 반복하지 않는다.
- 서술형 `outcome`: `CORRECT`, `PARTIAL`, `INCORRECT`.
- 각 `questionResults` 항목은 다른 문제 조회 없이 렌더링할 수 있어야 한다. 객관식은 `choices: [{ choiceId, text }]`, 빈칸은 풀이 때와 같은 `prompt`와 `blanks: [{ blankId, number }]`를 포함한다.
- 객관식 `response.selectedChoiceId`와 `representativeAnswer.selectedChoiceId`, 빈칸 `response.blankAnswers[].blankId`와 `representativeAnswer.blankAnswers[].blankId`는 각각 함께 반환된 보기·빈칸 식별자를 그대로 참조한다.
- 미응답은 `response=null`, `outcome=INCORRECT`로 포함한다. 별도 `unanswered` 필드나 미응답 집계를 반환하지 않으며 클라이언트는 `response=null`로 답하지 않음을 표시하고 단답형 수정 행동을 제공하지 않는다.
- `representativeAnswer`는 결과 설명에 필요한 대표 정답만 공개한다. 빈칸·단답형의 허용 정답 전체를 반환하지 않는다. 서술형은 `modelAnswer`와 `keyPoints`를 제공한다.
- `summary.scoredGrading`의 분자·분모는 객관식·빈칸·단답형이며, 단답형은 최신 `outcome`을 분자 계산에 사용한다. 최초 제출 응답의 `automaticGrading`은 제출 시점 자동 판정 요약이므로 별도 의미를 유지한다.
- `summary`는 저장된 문항 결과와 복습 해결 상태를 기준으로 조회 시 계산한다. 클라이언트는 쓰기 성공 응답의 전체 결과를 적용하고 필요하면 이 결과 API를 다시 조회한다.
- 서버가 보존하는 객관식·빈칸과 단답형의 `automaticOutcome`은 복습 뒤에도 바뀌지 않는다. 조회 projection의 단답형 `outcome`만 사용자 수정으로 바뀔 수 있고 복습 자체는 이를 변경하지 않는다. 복습 대상 여부는 서버가 현재 판정과 복습 해결 상태로 계산하며 결과 화면은 `summary.reviewQuestionCount`를 사용한다.

## 복습 세션

API의 `reviewSession`은 클라이언트가 복습 실행을 식별하는 공개 리소스 이름이다. 데이터 원장에서는 별도 복습 세션이 아니라 `attemptType=REVIEW`인 quiz attempt이며, `reviewSessionId`는 해당 REVIEW attempt의 공개 ID다. REVIEW attempt는 최초 `MAIN` attempt를 `sourceAttemptId`로 직접 가리키고 본 퀴즈와 같은 문항·제출 답안·채점 결과 구조를 사용한다.

복습 시작 시 대상 문항을 REVIEW attempt의 문항 목록으로 먼저 고정한다. 풀이·제출·채점 응답은 본 퀴즈와 동일한 문제 유형별 필드와 response 모양을 사용하며 복습 전용 답안 형식을 추가하지 않는다.

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
    "sourceAttemptId": "550e8400-e29b-41d4-a716-446655440000",
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

Headers: `Authorization`, `Content-Type: application/json`

```json
{
  "sourceAttemptId": "550e8400-e29b-41d4-a716-446655440000"
}
```

- `sourceAttemptId`는 [최신 복습 현황](#최신-복습-현황)에서 받은 가장 최근 완료 회차여야 한다.
- 해당 회차의 `reviewRequired=true` 문항을 번호 오름차순으로 snapshot해 서버 복습 세션을 만든다.
- 생성된 활성 세션의 snapshot은 원본 회차 단답형 판정 수정 뒤에도 중간 변경하지 않는다. 원본 회차의 최신 `reviewQuestionCount`와 활성 세션의 남은 문항 수는 별도 값이며, 수정된 대상 여부는 다음 세션 생성부터 반영한다.
- 과거 회차를 임의 선택하거나 여러 회차를 합치지 않는다. 최신 회차가 바뀌었거나 대상이 없으면 `REVIEW_001`이다.
- 새 세션을 만들면 `201 Created`, 같은 `sourceAttemptId`의 활성 세션이 이미 있으면 새로 만들지 않고 `200 OK`로 기존 세션을 반환한다.
- source attempt 하나에 활성 세션을 하나만 허용하는 unique 제약이 중복 생성을 막는다. 별도 멱등 키나 payload fingerprint를 만들지 않는다.
- 더 오래된 회차의 미완료 복습 세션은 최신 회차의 새 복습을 막지 않으며 복습 탭 대상에 합치지 않는다.
- snapshot에 저장된 문항 목록 자체가 생성 시점의 복습 대상을 보존하며 별도 source summary revision을 저장하지 않는다.

```json
{
  "success": true,
  "data": {
    "reviewSession": {
      "reviewSessionId": "review_123",
      "sourceAttemptId": "550e8400-e29b-41d4-a716-446655440000",
      "status": "SOLVING",
      "reviewQuestionCount": 2,
      "questions": []
    }
  },
  "error": null
}
```

### 세션 조회·재개

`GET /api/v1/review-sessions/{reviewSessionId}`

- `status`: `SOLVING`, `SELF_ASSESSMENT_REQUIRED`, `COMPLETED`.
- 문항은 원래 `number` 오름차순이고 한 세션에 각 문항이 한 번만 존재한다.
- `SOLVING`에서는 본 퀴즈의 `READY` 문제와 동일한 풀이 전 공개 모양만 반환한다. 정답·모범 답안·해설·원문 근거와 서버 중간 답안은 포함하지 않는다.
- 제출 전 현재 문항과 답안은 클라이언트가 같은 기기에 임시 보존한다. 세션 조회는 서버 draft 답안이나 `nextQuestionId`를 제공하지 않는다.
- `SELF_ASSESSMENT_REQUIRED`에서는 결과 조회를 통해 제출된 서술형의 자기평가 상세와 남은 문항을 확인한다.

```json
{
  "success": true,
  "data": {
    "reviewSessionId": "review_123",
    "sourceAttemptId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "SOLVING",
    "questions": [
      {
        "questionId": "question_4",
        "number": 4,
        "type": "ESSAY",
        "topic": "기아 방지",
        "prompt": "에이징의 목적을 설명하세요."
      }
    ]
  },
  "error": null
}
```

### 전체 답안 제출

`PUT /api/v1/review-sessions/{reviewSessionId}/submission`

Headers: `Authorization`, `Content-Type: application/json`

```json
{
  "responses": [
    {
      "questionId": "question_1",
      "selectedChoiceId": "choice_2"
    },
    {
      "questionId": "question_4",
      "text": "대기 시간이 길수록 우선순위를 높인다."
    }
  ]
}
```

- body는 본 퀴즈 최종 제출과 같은 `responses[]` 모양이며 `questionId`와 유형별 답안 필드만 보낸다. `type`은 보내지 않는다.
- 빈칸은 `blankAnswers: [{ blankId, answer }]`를 사용하며 일부만 보내는 것을 허용한다. 빠진 빈칸이 있으면 문항 전체를 오답 처리하고 누락 항목을 빈 문자열로 보정하지 않는다.
- `responses`에서 빠진 대상 문제는 미응답 오답으로 확정한다. 제출 전에는 문항별 채점 API를 호출하지 않는다.
- 첫 제출은 세션의 전체 답안 저장과 객관식·빈칸·단답형 일괄 채점을 한 트랜잭션에서 수행한다. 자동 `CORRECT`면 `RESOLVED`, 아니면 `UNRESOLVED`다.
- 작성한 서술형이 있으면 `SELF_ASSESSMENT_REQUIRED`, 없으면 `COMPLETED`가 된다. 미응답 서술형은 별도 자기평가 없이 `UNRESOLVED`다.
- 이미 제출된 같은 `reviewSessionId` 재요청은 body를 다시 적용하거나 비교하지 않고 최초 제출의 현재 상태를 반환한다. 세션 식별자와 단일 제출 제약이 중복을 막으므로 별도 멱등 키·hash·payload fingerprint를 저장하지 않는다.
- 자동 판정 또는 이후 서술형 자기평가가 끝나면 source attempt의 현재 결과에서 `reviewQuestionCount`를 다시 계산한다.

제출 응답 예시:

```json
{
  "success": true,
  "data": {
    "reviewSessionId": "review_123",
    "status": "SELF_ASSESSMENT_REQUIRED",
    "automaticGrading": {
      "correctQuestionCount": 1,
      "gradedQuestionCount": 1
    },
    "pendingEssayQuestionIds": ["question_4"],
    "submittedAt": "2026-08-24T05:20:00Z"
  },
  "error": null
}
```

자기평가에 필요한 내 답·모범 답안·핵심 포인트·해설·원문 근거는 [복습 결과 조회](#복습-결과-조회)에서 본 퀴즈 결과와 같은 문항 상세 모양으로 제공한다.

### 복습 서술형 자기평가

`PUT /api/v1/review-sessions/{reviewSessionId}/essay-assessments/{questionId}`

Headers: `Authorization`, `Content-Type: application/json`

```json
{
  "assessment": "CORRECT"
}
```

- `assessment`: `CORRECT`, `PARTIAL`, `INCORRECT`.
- `CORRECT`면 `RESOLVED`, `PARTIAL` 또는 `INCORRECT`면 `UNRESOLVED`다.
- 같은 평가 재요청은 현재 결과를 반환하고, 이미 확정된 평가를 다른 값으로 바꾸려는 요청은 `REVIEW_001`이다.
- 마지막 대기 문항을 평가하면 세션을 `COMPLETED`로 바꾼다.
- `UNRESOLVED` 문항도 같은 세션에는 다시 넣지 않으며 다음 복습 세션의 후보로 남긴다.
- 복습 응답·판정은 원래 attempt 응답·판정을 수정하지 않는다.

```json
{
  "success": true,
  "data": {
    "questionId": "question_4",
    "assessment": "PARTIAL",
    "reviewStatus": "UNRESOLVED",
    "status": "COMPLETED",
    "remainingSelfAssessmentCount": 0
  },
  "error": null
}
```

### 복습 결과 조회

`GET /api/v1/review-sessions/{reviewSessionId}/result`

- `SELF_ASSESSMENT_REQUIRED`에서도 자동 채점 결과와 자기평가할 서술형 상세를 조회할 수 있다. 최종 완료 요약은 `COMPLETED`에서만 완료로 표현한다.
- `questionResults`는 복습 세션에 snapshot된 문항만 원래 `number` 오름차순으로 반환한다.
- 문항 결과의 `response`, 대표 답안 또는 모범 답안, `outcome`, 해설과 원문 근거 모양은 본 퀴즈 결과와 동일하다.
- 응답에는 `reviewSessionId`, `sourceAttemptId`, `status`, `summary`, `questionResults`를 포함한다. `summary`는 이번 재풀이의 자동 채점 수, 서술형 자기평가 수, 해결·미해결 수를 구분한다.

## 오류

### 최소 안정 코드

| 조건 | HTTP | 코드 | 복구 |
| --- | --- | --- | --- |
| 필드 누락·형식·허용 enum/개수 | `400` | 기존 `COMMON_001` | `fields`에 따라 입력 수정 |
| 읽을 수 없는 JSON | `400` | 기존 `COMMON_002` | 요청 본문 수정 |
| 없거나 소유하지 않은 리소스, 접근할 수 없는 Notion 페이지 | `404` | 기존 `COMMON_003` | 목록·권한·페이지 선택 확인 |
| 예상하지 못한 서버 오류 | `500` | 기존 `COMMON_999` | 생성 요청은 같은 멱등 키, 제출은 같은 attempt 또는 review session 식별자로 재시도 |
| 인증 정보 없음·잘못됨·만료 | `401` | 기존 `AUTH_005` | 갱신 또는 재로그인 |
| 잠긴 학습자료 본문 수정 | `409` | `MATERIAL_001` | 제목만 수정하거나 새 자료 만들기 |
| 학습자료 본문 20,000자 초과 | `413` | `MATERIAL_002` | 본문을 줄인 뒤 같은 저장 흐름 재시도 |
| 같은 학습자료에 이미 `GENERATING` 작업이 있음 | `409` | `QUIZ_001` | 기존 생성 상태 확인 |
| 생성 작업을 접수할 수 없는 일시적 서버 상태 | `503` | `QUIZ_002` | 새 QuizSet이 만들어지지 않았음을 확인하고 같은 멱등 키로 재시도 |
| `READY`가 아닌 세트 제출, attempt UUID의 소유자·QuizSet 불일치, 자기평가 상태 또는 수정 불가 단답형 | `409` | `ATTEMPT_001` | 최신 문제 세트·attempt 상태와 결과 확인 |
| 완료된 복습 세션 재변경 또는 이미 확정된 서술형 평가 변경 | `409` | `REVIEW_001` | 세션과 결과 재조회 |

- MVP의 안정 오류 코드는 `COMMON_001/002/003/999`, `AUTH_005`, `MATERIAL_001/002`, `QUIZ_001/002`, `ATTEMPT_001`, `REVIEW_001`로 제한한다.
- Notion, 외부 생성 서비스, LLM 또는 세부 검증 단계별 공개 오류 코드를 추가하지 않는다.
- 비동기 생성 실패는 정상 상태 조회의 `status=FAILED`로 전달한다. HTTP 오류와 혼용하지 않는다.
- `error.message`는 사용자가 다음 행동을 이해할 수준으로 쓰고 내부 예외·LLM·Notion 상세를 노출하지 않는다.
- 입력 오류의 `fields`는 `responses[0].selectedChoiceId`나 `responses[1].blankAnswers[0].blankId`처럼 클라이언트가 해당 입력을 찾을 수 있는 경로를 사용한다.

## 정렬과 조회 경계

- 문제, 결과, 복습 문항은 원래 문제 `number` 오름차순이다.
- 문제 세트·attempt·채점 결과·복습 snapshot의 불변성과 생명주기는 [학습자료와 퀴즈 데이터 계약](contract-data-quiz-learning.md)을 따른다.
- 서버에는 본 퀴즈 중간 진행·답안 API가 없다. 최종 제출 전 상태의 지속 저장과 새로고침·종료 뒤 복구를 HTTP 계약으로 요구하지 않는다.

## 개인정보와 로그

- 학습자료 본문, 사용자 답안, 모범 답안과 원문 근거는 민감한 학습 콘텐츠로 취급한다.
- 요청·응답 본문, Notion 접근 자격과 외부 생성 서비스 원문을 일반 애플리케이션 로그에 남기지 않는다. `attemptId`, `reviewSessionId`와 생성 요청의 `Idempotency-Key`는 비밀이 아니지만 운영 로그에는 문제 해결에 필요한 식별자만 최소로 남긴다.
- 운영 로그는 요청 추적 식별자, 사용자 내부 식별자, 리소스 식별자, 공개 오류 코드와 상태 전이에 필요한 최소 메타데이터만 남긴다.

## 호환성과 폐기

- 이 계약은 기존 인증 응답 봉투와 오류 규칙을 확장하되 인증 계약을 변경하지 않는다.
- `importId`, 서버 preview·draft·만료, 파일 업로드, Notion 동기화, 서버 풀이 draft API는 MVP에서 폐기된 전제다. 과거 초안 소비자가 있다면 이 계약 구현 전에 제거하고 `POST /api/v1/learning-materials` 직접 저장으로 전환한다.
- 문제 유형별 필드나 enum을 바꾸면 웹·앱의 입력·결과 읽기와 서버 채점을 동시에 갱신해야 한다. 새 필드는 하위 호환 가능한 선택 필드를 우선하고, 기존 enum 의미를 재사용하지 않는다.

## 열린 질문

- 재연결 뒤 페이지 선택 복원 여부는 [학습자료 흐름의 열린 질문](../ux/flow-content-import.md#열린-질문)이 책임진다.
