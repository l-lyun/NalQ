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
- 생성 결과 알림 경계: [알림 API 계약](contract-api-notifications.md)

## 문서 책임

이 문서는 웹·앱·서버가 공유하는 HTTP 입력·출력, 상태, 권한, 멱등성, 정렬과 오류 의미를 정의한다. 문제 품질·채점·복습 정책은 기능명세가, 화면 사이의 중단·복구는 흐름 문서가 책임진다.

## 공통 계약

### 인증과 소유권

- 모든 클라이언트 API 엔드포인트는 유효한 NalQ Access Token이 필요하다. Notion OAuth callback만 브라우저가 아닌 제공자 redirect를 받으므로 Bearer Token 대신 서버가 발급한 일회성·만료형 OAuth state로 요청의 사용자와 복귀 위치를 검증한다.
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

- `Idempotency-Key` 헤더는 학습자료 생성 `POST /api/v1/learning-materials`에서만 사용한다. 비동기 QuizSet 생성 접수에는 별도 멱등 키·request fingerprint·replay 원장을 두지 않는다.
- QuizSet 생성 접수 응답을 확인하지 못한 클라이언트는 같은 요청을 즉시 재전송하지 않고 먼저 해당 자료의 활성 생성을 조회한다. `GENERATING` 세트가 있으면 그 식별자로 상태 조회를 이어가고, 활성 생성이 없을 때만 새 생성 요청을 보낸다.
- 본 퀴즈 최종 제출은 클라이언트가 `crypto.randomUUID()` 수준의 안전한 난수원으로 만든 UUID v4를 `attemptId`로 사용한다. 서버는 parse 가능한 UUID 형식과 DB unique 제약만 확인하며 hash·fingerprint 충돌 방지 체계를 추가하지 않는다.
- attempt UUID는 비밀 정보가 아니므로 서버에서 hash하지 않는다. 서버는 `attemptId`의 전역 unique 제약과 소유권·QuizSet 일치 검증만 수행한다.
- 같은 `attemptId`의 재요청은 payload fingerprint를 계산하거나 비교하지 않고 최초로 확정된 attempt를 반환한다. 클라이언트 버그로 같은 UUID에 다른 답안을 보낸 경우에도 최초 제출 우선이며 새 회차를 만들지 않는다.
- 다른 사용자 또는 QuizSet이 이미 사용한 UUID이면 `409 ATTEMPT_001`과 `fields.field=attemptId`를 반환한다. 자동으로 새 UUID를 만들어 재전송하지 않는다.
- 복습 최종 제출은 이미 존재하는 `reviewSessionId` 하나에 한 번만 확정되므로 별도 요청 키를 만들지 않는다. 같은 세션 재요청은 최초 제출 결과를 반환한다.
- 서술형 자기평가와 자동 채점 판정 수정처럼 특정 리소스에 원하는 현재 값을 저장하는 `PUT`은 저장된 현재 값 자체로 반복 요청을 처리한다. 별도 멱등 키, payload fingerprint와 공개 revision을 두지 않는다.

## 엔드포인트 목록

| 사용자 행동 | Method / Path | 성공 |
| --- | --- | --- |
| 학습자료 저장 | `POST /api/v1/learning-materials` | `201 Created` |
| 학습자료 목록 조회·제목 검색 | `GET /api/v1/learning-materials` | `200 OK` |
| 학습자료 상세 조회 | `GET /api/v1/learning-materials/{materialId}` | `200 OK` |
| 학습자료 수정 | `PATCH /api/v1/learning-materials/{materialId}` | `200 OK` |
| Notion 연결 상태 조회 | `GET /api/v1/integrations/notion/connection` | `200 OK` |
| Notion OAuth 승인 시작 | `POST /api/v1/integrations/notion/authorizations` | `201 Created` |
| Notion OAuth callback | `GET /api/v1/integrations/notion/callback` | `302 Found` |
| Notion 접근 페이지 조회 | `GET /api/v1/integrations/notion/pages` | `200 OK` |
| Notion 연결 해제 | `DELETE /api/v1/integrations/notion/connection` | `200 OK` |
| Notion 페이지 일회성 복사 | `POST /api/v1/learning-material-imports/notion` | `200 OK` |
| 문제 세트 생성 접수 | `POST /api/v1/learning-materials/{materialId}/quiz-sets` | `202 Accepted` |
| 자료의 활성 생성 조회 | `GET /api/v1/learning-materials/{materialId}/quiz-sets/active` | `200 OK` |
| 내 퀴즈 목록 조회·이름 검색 | `GET /api/v1/quiz-sets` | `200 OK` |
| 문제 세트 상태·풀이 데이터 조회 | `GET /api/v1/quiz-sets/{quizSetId}` | `200 OK` |
| 퀴즈 이름 변경 | `PATCH /api/v1/quiz-sets/{quizSetId}` | `200 OK` |
| 본 퀴즈 최종 제출 | `PUT /api/v1/quiz-sets/{quizSetId}/attempts/{attemptId}` | `201 Created` 또는 `200 OK` |
| 미완료 서술형 자기평가 회차 조회 | `GET /api/v1/quiz-sets/{quizSetId}/attempts/pending-self-assessment` | `200 OK` |
| 서술형 자기평가 저장 | `PUT /api/v1/quiz-attempts/{attemptId}/essay-assessments/{questionId}` | `200 OK` |
| 결과 조회 | `GET /api/v1/quiz-attempts/{attemptId}/result` | `200 OK` |
| 단답형·빈칸 현재 판정 수정 | `PUT /api/v1/quiz-attempts/{attemptId}/grading-overrides/{questionId}` | `200 OK` |
| 단답형 현재 판정 수정(호환 경로) | `PUT /api/v1/quiz-attempts/{attemptId}/short-answer-gradings/{questionId}` | `200 OK` |
| 최신 복습 현황 조회 | `GET /api/v1/quiz-reviews/latest` | `200 OK` |
| 학습 메인 복습 후보 조회 | `GET /api/v1/quiz-reviews/candidates` | `200 OK` |
| 선택한 QuizSet의 복습 세션 생성 | `POST /api/v1/review-sessions` | `201 Created` 또는 `200 OK` |
| 복습 세션 조회·재개 | `GET /api/v1/review-sessions/{reviewSessionId}` | `200 OK` |
| 복습 전체 답안 제출 | `PUT /api/v1/review-sessions/{reviewSessionId}/submission` | `200 OK` |
| 복습 결과 조회 | `GET /api/v1/review-sessions/{reviewSessionId}/result` | `200 OK` |
| 복습 서술형 자기평가 저장 | `PUT /api/v1/review-sessions/{reviewSessionId}/essay-assessments/{questionId}` | `200 OK` |

## 학습자료

### 이번 학습자료 연동 범위

- **확정·이번 구현:** 기존 학습자료 저장·목록·상세·수정을 유지하고, 사용자별 Notion Public OAuth 연결, 연결 워크스페이스의 접근 페이지 조회와 단일 페이지 일회성 복사를 서버·웹에 연결한다.
- **확정·후속 구현:** 네이티브 앱의 OAuth 복귀 UX를 추가한다. 이 절의 서버 요청·응답과 오류 의미는 웹·앱 공통이며 플랫폼별 화면 경로를 필드로 고정하지 않는다.
- **비범위:** 학습자료 삭제, Notion 동기화와 실제 외부 문제 생성 서비스 연동은 이번 작업에서 다루지 않는다.

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

### 목록 조회와 제목 검색

`GET /api/v1/learning-materials?page=1&size=6&query=운영체제`

- `page`: 선택, 1부터 시작하며 기본값은 `1`이다.
- `size`: 선택, 기본값은 `6`이고 `1..20`만 허용한다.
- `query`: 선택, 제목 부분 검색어다. 생략하거나 빈 값이면 제목 필터를 적용하지 않는다.
- 현재 인증 사용자가 소유한 학습자료만 반환한다.
- 정렬은 `updatedAt DESC`, 같은 시각에는 `materialId DESC`로 고정한다.
- `content`는 목록에서 반환하지 않는다. 목록 항목은 화면 구분과 상태 안내에 필요한 최소 필드만 포함한다.
- `page < 1`, `size < 1`, `size > 20` 또는 해석할 수 없는 값은 `400 COMMON_001`이다.
- 전체 페이지 범위를 벗어난 양수 `page`는 오류가 아니다. 요청한 `page`와 전체 집계를 유지하고 `200 OK`, `items=[]`를 반환한다.
- 검색어가 바뀐 클라이언트는 `page=1`부터 다시 조회한다.

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "materialId": "123",
        "title": "운영체제 스케줄링",
        "sourceType": "PASTE",
        "contentEditStatus": "EDITABLE",
        "updatedAt": "2026-08-26T01:00:00Z"
      }
    ],
    "page": 1,
    "size": 6,
    "totalElements": 13,
    "totalPages": 3
  },
  "error": null
}
```

- `page`와 `size`는 적용된 요청값이고 `totalElements`는 현재 검색 조건의 전체 자료 수다.
- `totalPages`는 `totalElements`와 `size`로 계산하며 검색 결과가 없으면 `0`이다.
- `contentEditStatus`는 공개 enum `EDITABLE`, `LOCKED_GENERATING` 중 하나다. 같은 학습자료에 `GENERATING` QuizSet이 있으면 `LOCKED_GENERATING`, 없으면 `EDITABLE`이다.
- 목록 응답은 활성 `quizSetId`나 과거 QuizSet 상태를 포함하지 않는다. 선택한 자료의 활성 생성 복원은 별도 [자료의 활성 생성 조회](#자료의-활성-생성-조회)를 사용한다.

검색 결과가 없을 때도 같은 모양을 유지한다.

```json
{
  "success": true,
  "data": {
    "items": [],
    "page": 1,
    "size": 6,
    "totalElements": 0,
    "totalPages": 0
  },
  "error": null
}
```

### 상세 조회

`GET /api/v1/learning-materials/{materialId}`

```json
{
  "success": true,
  "data": {
    "materialId": "123",
    "title": "운영체제 스케줄링",
    "content": "학습자료 전체 본문",
    "contentLength": 8240,
    "sourceType": "PASTE",
    "contentEditStatus": "EDITABLE",
    "createdAt": "2026-08-20T01:00:00Z",
    "updatedAt": "2026-08-26T01:00:00Z"
  },
  "error": null
}
```

- `contentLength`는 저장·검증과 같은 Unicode code point 계산을 사용한다.
- `contentEditStatus`는 목록과 같은 규칙으로 계산하며 별도 학습자료 잠금 컬럼을 공개하지 않는다.
- 존재하지 않거나 현재 사용자 소유가 아닌 자료는 `404 COMMON_003`이다.
- 경로의 `materialId`를 요구된 형식으로 해석할 수 없으면 `400 COMMON_001`이다.
- 활성 QuizSet과 과거 퀴즈 목록은 상세 응답에 합치지 않는다.

### 수정

`PATCH /api/v1/learning-materials/{materialId}`

```json
{
  "title": "수정한 제목",
  "content": "수정한 본문"
}
```

성공 응답의 `data`는 수정 후 [상세 조회](#상세-조회)와 같은 학습자료 상세 모양이다. 따라서 소비자는 별도 재조회 없이 저장된 `title`, `content`, `contentLength`, `contentEditStatus`, `updatedAt`을 반영할 수 있다.

- 보낸 필드만 수정한다. 빈 요청은 `COMMON_001`이다.
- `title`은 본문 잠금과 관계없이 수정할 수 있다.
- `contentEditStatus=EDITABLE`일 때만 `content`를 수정할 수 있다. 같은 학습자료의 QuizSet이 `GENERATING`인 동안에만 `LOCKED_GENERATING`이며 생성이 `READY` 또는 `FAILED`로 끝나면 `EDITABLE`이다.
- 공개 상태는 `EDITABLE`, `LOCKED_GENERATING`뿐이며 영구 잠금 상태는 두지 않는다.
- 학습자료 수정은 이미 만들어진 QuizSet·풀이·결과를 바꾸지 않는다. 이후 새 QuizSet은 생성 접수 시점의 최신 저장 본문을 근거로 한다.

### 확정된 Notion HTTP 계약

아래 여섯 경로, 요청·응답 필드, HTTP status와 여섯 공개 오류의 의미는 이번 구현의 확정 계약이다.

#### Notion 연결 상태 조회

`GET /api/v1/integrations/notion/connection`

```json
{
  "success": true,
  "data": {
    "status": "CONNECTED",
    "workspaceName": "개인 학습 공간"
  },
  "error": null
}
```

- `status`는 `DISCONNECTED`, `CONNECTED`, `REAUTH_REQUIRED` 중 하나다.
- `workspaceName`은 Notion이 제공한 연결 워크스페이스의 표시 이름이다. Notion의 정상 token 응답에서도 이름이 없을 수 있으므로 모든 상태에서 `null`을 허용하며, 클라이언트는 `CONNECTED|REAUTH_REQUIRED`인데 값이 없으면 일반적인 "연결된 Notion 워크스페이스" 표시를 사용한다. 내부 `workspaceId`는 클라이언트에 반환하지 않는다.
- NalQ 사용자 한 명은 활성 Notion 워크스페이스 연결을 최대 한 개만 가진다. 다른 워크스페이스로 바꾸려면 현재 연결 해제를 먼저 완료해야 한다.
- `REAUTH_REQUIRED`는 로컬 연결의 워크스페이스 의미를 보존하지만 현재 접근 자격으로 페이지 목록·복사를 계속할 수 없는 상태다. 같은 워크스페이스 재인증만 기존 연결을 갱신할 수 있다.
- Notion access token·refresh token과 제공자 원시 연결 정보는 이 응답을 포함한 어떤 클라이언트 응답에도 포함하지 않는다.

#### Notion 연결 데이터와 암호화

- 서버는 Notion 연결을 NalQ `user_id`당 unique 한 행으로 저장한다. 행은 내부 `workspace_id`, nullable 표시용 `workspace_name`, 암호화한 access token, 선택적인 암호화 refresh token, 암호화 key version, `CONNECTED|REAUTH_REQUIRED` 상태와 생성·수정 시각을 가진다.
- Notion token 응답에는 token 만료 시각이 없으므로 추측한 만료 시각이나 `expiresAt`을 연결 행에 저장하지 않는다. OAuth 승인 시작 응답의 `expiresAt`은 일회성 승인 요청의 만료일 뿐 token 수명이 아니다.
- token은 각각 별도의 nonce를 사용하는 AES-256-GCM으로 암호화한다. AAD는 `userId + workspaceId + tokenType`을 결합해 다른 사용자·워크스페이스·token 종류로 암호문을 옮겨 사용할 수 없게 한다.
- 개발 환경의 암호화 key는 환경 변수로 주입하고, 운영 key는 secret manager에서 공급한다. DB에는 key 원문 대신 복호화할 key version만 저장한다.
- Notion 사용자 이름·이메일·프로필 등 사용자 개인정보, `bot_id`와 OAuth 원시 응답은 저장하지 않는다. 연결은 학습자료 가져오기에 필요한 read content capability만 요청·사용한다.
- token 갱신은 같은 `user_id` 연결 행을 잠근 한 요청만 수행한다. 갱신 성공 시 새 access token과 제공된 refresh token을 한 트랜잭션에서 교체하고, 갱신을 촉발한 원 요청을 한 번만 다시 호출한다.
- refresh token이 없거나 갱신에 실패해 재동의가 필요하면 기존 연결 메타데이터를 삭제하지 않고 `REAUTH_REQUIRED`로 전환한다. Notion token 갱신 실패를 NalQ `401`로 노출하지 않는다.

#### Notion OAuth 승인 시작과 callback

`POST /api/v1/integrations/notion/authorizations`

```json
{
  "returnUri": "https://app.openmd.example/learning/import/notion"
}
```

```json
{
  "success": true,
  "data": {
    "authorizationUrl": "https://notion.example/authorize/opaque-request",
    "expiresAt": "2026-09-01T06:10:00Z"
  },
  "error": null
}
```

- `returnUri`는 서버에 미리 등록된 정확한 복귀 URI 중 하나여야 한다. 임의 origin·부분 일치·요청 Host에서 조합한 URI를 허용하지 않는다.
- 서버는 승인 요청마다 예측할 수 없는 일회성 state를 만들고 현재 `userId`, `returnUri`, 만료 시각과 연결한다. state 원문과 Notion 자격은 일반 로그에 남기지 않는다.
- `authorizationUrl`은 클라이언트가 그대로 열어야 하는 만료형 opaque URL이다. 웹은 현재 페이지를 이 URL로 이동하는 full-page redirect를 사용한다. 서버 계약은 팝업, WebView 또는 네이티브 deep link를 요구하지 않는다.
- 연결이 없으면 사용자는 자신이 권한을 줄 수 있는 임의의 워크스페이스를 승인할 수 있다. 기존 연결이 있으면 같은 워크스페이스의 재인증·접근 페이지 추가에만 승인 결과를 사용할 수 있다.
- 기존 연결이 있는데 callback에서 다른 워크스페이스가 확인되면 새로 발급된 자격을 저장하지 않고 폐기하며 기존 `CONNECTED|REAUTH_REQUIRED` 상태를 유지한다. callback은 `outcome=failed&error=NOTION_WORKSPACE_MISMATCH`로 복귀시키고, 클라이언트는 기존 연결을 계속 사용하거나 현재 연결 해제 뒤 다른 워크스페이스 연결을 시작한다. 이 오류는 현재 자격이 무효하다는 뜻이 아니며 재인증을 요구하지 않는다.
- 재인증·접근 페이지 추가 전의 `pageId`는 OAuth state, 서버 연결 정보나 프론트 복원 상태에 저장하지 않는다. callback 뒤 사용자는 페이지 목록을 새로고침하고 다시 선택한다.

`GET /api/v1/integrations/notion/callback`

- 이 경로의 query는 Notion OAuth protocol 입력이며 웹·앱이 직접 구성하지 않는다. 서버는 일회성 state, 만료, 사용자 연결 상태와 승인 결과를 검증한다.
- 기존 연결을 전제로 시작한 재인증·접근 페이지 추가 state는 해당 연결이 해제되거나 다른 자격으로 바뀌면 무효하다. 이후 도착한 callback은 자격을 저장하거나 연결을 다시 만들지 않고 `NOTION_CONNECTION_REQUIRED`로 복귀시킨다.
- 성공하면 자격을 암호화해 저장하고 `returnUri?outcome=connected`로 `302 Found` 복귀시킨다. 사용자가 승인을 취소하면 연결을 만들거나 바꾸지 않고 `returnUri?outcome=cancelled`, 실패하면 `returnUri?outcome=failed&error={Notion 공개 오류 코드}`로 복귀시킨다.
- state가 만료·유실·재사용되어 신뢰할 수 있는 `returnUri`를 복구할 수 없으면 서버는 요청값에서 복귀 주소를 만들지 않는다. 환경별로 등록한 고정 웹 복귀 URI에 `outcome=failed&error=NOTION_CONNECTION_REQUIRED`를 붙여 보낸다. 이 코드는 이 callback 문맥에서 "유효한 OAuth 승인 흐름을 계속할 수 없음"도 뜻한다. 웹은 query 제거와 연결 상태 재조회 뒤 `DISCONNECTED`면 최초 연결을, `CONNECTED|REAUTH_REQUIRED`면 해당 상태에 맞는 승인 흐름을 처음부터 다시 시작할 수 있게 한다.
- callback redirect query는 `outcome=connected|cancelled|failed`와 `failed`일 때 여섯 Notion 공개 오류 중 하나인 `error`만 허용한다. `connected|cancelled`에는 `error`를 붙이지 않는다. Notion authorization `code`, access·refresh token, OAuth `state`, 원시 오류와 내부 예외는 복귀 URI에 포함하지 않는다.
- 웹은 복귀 즉시 callback query를 주소에서 제거한 뒤 `GET /api/v1/integrations/notion/connection`을 다시 조회한다. `outcome=connected`이면 페이지 선택으로, `cancelled|failed`이면 입력 방식 선택으로 이동한다. redirect query를 연결 상태의 원장으로 사용하지 않는다.
- 현재 구현의 소비자는 웹이지만, API의 연결 상태와 오류 의미는 플랫폼 중립이다. 네이티브 앱에서 사용할 복귀 URI 등록과 외부 브라우저 복귀 UX는 후속 범위다.

#### Notion 외부 호출과 재시도

- 서버는 Notion 연결 timeout을 3초, 개별 응답 읽기 timeout을 15초, 한 NalQ 요청의 전체 Notion 처리 시간을 20초로 제한한다.
- 한 외부 호출의 재시도는 최대 한 번이다. `429`와 `529`는 `Retry-After`를 존중하고, 읽기 `GET`의 `5xx`는 한 번 재시도한다. 남은 전체 처리 시간 안에 재시도할 수 없으면 `NOTION_TEMPORARILY_UNAVAILABLE`로 끝낸다.
- Notion `400`, `403`, `404`는 재시도하지 않는다. Notion `401`은 위 token 갱신 절차를 한 번 수행하고 원 요청을 한 번 다시 호출하며, 계속 실패하면 `NOTION_REAUTH_REQUIRED`다.
- 이 MVP에는 자체 복합 rate limiter, Redis 대기열이나 background retry 작업을 추가하지 않는다. 클라이언트는 연결·재인증·목록·복사·해제 요청이 진행 중인 동안 같은 작업과 관련된 버튼을 비활성화한다.

#### Notion 접근 페이지 조회

`GET /api/v1/integrations/notion/pages?cursor=opaque-cursor&query=운영체제`

- `cursor`: 선택, 직전 성공 응답의 `nextCursor`를 그대로 사용하는 opaque 문자열이다.
- `query`: 선택, 페이지 선택을 돕는 제목 검색어다. 생략하거나 정리 결과가 빈 문자열이면 제목 필터를 적용하지 않는다.
- 한 응답은 수정 시각 최신순으로 최대 20개를 반환한다. 더 보기는 `nextCursor`를 사용하고, 새로고침은 `cursor` 없이 현재 `query`의 첫 목록을 다시 요청한다.
- `query`가 바뀌면 클라이언트는 현재 항목과 이전 `nextCursor`를 버리고, `cursor`가 없는 새 검색의 첫 요청부터 시작한다. 서로 다른 `query`에서 받은 cursor를 재사용하지 않는다.
- 서버는 현재 연결된 워크스페이스에서 사용자가 integration 접근을 허용한 일반 페이지와 데이터베이스의 개별 행 페이지만 반환하고 데이터베이스 자체는 제외한다. 하위 페이지는 상위 페이지 본문에 포함한다는 의미가 아니라 별도로 선택 가능한 항목이다.

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "pageId": "opaque-notion-page-id",
        "title": "운영체제 정리",
        "lastEditedAt": "2026-09-01T05:30:00Z"
      }
    ],
    "nextCursor": null
  },
  "error": null
}
```

- `pageId`와 `nextCursor`는 opaque 문자열이며 클라이언트가 해석하거나 권한 증명으로 사용하지 않는다.
- 제목이 없는 페이지의 `title`은 빈 문자열일 수 있다. 페이지 선택 화면의 대체 표시는 저장될 학습자료 제목이 아니며, 학습자료 저장에는 사용자가 유효한 제목을 입력해야 한다.
- 목록에 없는 페이지 접근을 추가하려면 같은 워크스페이스 OAuth 승인을 다시 진행한 뒤 cursor 없는 첫 목록을 새로고침한다. 재승인 전 `pageId`를 자동 선택하거나 복원하지 않는다.
- Notion 검색에서 접근 가능한 페이지가 누락되는 경우, 웹은 사용자가 입력한 Notion URL에서 page UUID를 추출해 같은 `pageId` 요청으로 아래 가져오기 API를 호출할 수 있다. 별도 URL 가져오기 endpoint를 만들지 않으며 서버는 입력 URL이나 추출한 `pageId`를 가져오기 기록으로 저장하지 않는다.

#### Notion 연결 해제

`DELETE /api/v1/integrations/notion/connection`

```json
{
  "success": true,
  "data": {
    "status": "DISCONNECTED"
  },
  "error": null
}
```

- 서버는 Notion 쪽 접근 권한 철회를 먼저 수행한다. revoke `200`이면 로컬 정보를 삭제한다. revoke 응답이 유실되거나 성공 여부가 불명확하면 같은 token을 Notion introspection endpoint로 확인하고 `active=false`일 때만 이미 철회된 것으로 판단해 삭제한다.
- 연결 해제와 token 갱신이 겹치면 해제 성공 응답 전에 새로 발급된 자격까지 철회된 것을 확인해야 한다. 성공한 해제는 Notion에 유효한 현재 자격을 남기거나 갱신 중인 자격을 로컬 연결 삭제 뒤에 저장하지 않는다.
- introspection 결과가 `active=true`이면 로컬 정보를 유지하고 연결 해제를 실패 처리한다. introspection 자체가 실패해 상태를 확인할 수 없어도 로컬 정보를 유지한다.
- 제공자 일시 장애로 철회 결과를 확인할 수 없으면 `503 NOTION_TEMPORARILY_UNAVAILABLE`을 반환하고 로컬 연결을 보존한다. 클라이언트는 같은 연결 해제를 다시 요청할 수 있다.
- 해제가 완료되면 해당 사용자가 먼저 시작하고 아직 소비하지 않은 OAuth 승인 요청도 무효해진다. 늦게 도착한 callback이 연결을 다시 만들어서는 안 된다.
- 성공 재요청에서 이미 로컬 연결이 없으면 현재 `DISCONNECTED` 결과를 반환한다.

#### Notion 단일 페이지 일회성 복사

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
    "content": "# 운영체제\n\n프로세스와 스레드의 차이..."
  },
  "error": null
}
```

- 서버는 현재 사용자에게 연결된 워크스페이스에서 사용자가 선택한 페이지 하나를 요청 시점에 한 번 읽는다. `pageId`를 알고 있다는 사실만으로 권한 검증을 생략하지 않는다.
- 서버는 같은 요청 안에서 Notion Page API로 최신 제목을 먼저 읽고, 성공한 뒤 Markdown API를 `include_transcript=false`로 순차 호출한다. 두 호출을 병렬 실행하거나 첫 호출 결과 없이 Markdown 호출을 시작하지 않는다.
- 하위 페이지는 현재 페이지 본문에 자동 병합하지 않는다. 사용자가 하위 페이지를 별도로 선택해 이 API를 호출해야 별도 가져오기 결과가 된다.
- 같은 사용자가 같은 페이지를 여러 번 호출하는 것을 허용한다. 이 API는 멱등 키나 중복 원장을 두지 않고 매 요청 시점의 페이지를 새로 읽는다.
- Markdown 응답의 첫 줄을 제목으로 간주해 제거하지 않는다. fenced·inline code 영역 밖의 Notion enhanced Markdown 줄바꿈 표시인 정확한 `<br>`만 Markdown 강제 줄바꿈인 두 칸과 줄바꿈(`  \n`)으로 치환한다. 코드 영역의 `<br>` 리터럴과 그 밖의 enhanced Markdown·링크는 응답 텍스트 그대로 유지한다.
- 이미지·영상·파일 등 미디어 본체와 녹취는 본문에서 제외하되 제공된 캡션·대체 텍스트는 일반 텍스트로 유지한다. 미디어 원본 URL, 다운로드 자격과 녹취 원문을 반환하지 않는다.
- Markdown 응답이 truncated 상태이거나 `unknown_block_ids`가 하나 이상이거나 코드 영역 밖의 본문에 Notion이 생성한 `<unknown>` 또는 속성을 가진 `<unknown ...>` 태그가 있으면 추가 회수를 시도하지 않고 `NOTION_CONTENT_INCOMPLETE`로 전체 실패한다. 사용자가 fenced·inline code에 작성한 같은 리터럴은 불완전 표시로 판정하지 않는다. 이 실패에는 부분 `content`를 반환하지 않는다.
- 성공 응답은 프론트 편집용 값이며 서버 학습자료·draft·import job을 만들지 않는다.
- 빈 `content`도 가져오기 성공으로 반환한다. 서버는 제목과 본문을 길이 때문에 자르지 않고 전체 반환한다.
- 성공 응답 `data`는 `sourceType`, `title`, `content`만 포함한다. `contentLength`, `pageId`, Notion URL, workspace 정보와 `warnings`를 추가하지 않는다.
- 클라이언트는 제목의 앞뒤 Unicode 공백을 제거한 값이 1..255 code point이고, 본문에 Unicode 비공백 문자가 하나 이상 있으며 전체 본문이 1..20,000 code point일 때만 `저장하고 퀴즈 만들기`와 `자료 저장`을 활성화한다. 서버는 최종 `POST /api/v1/learning-materials`에서 같은 저장 제한을 다시 검증한다.
- 자동·수동 동기화 엔드포인트는 제공하지 않는다.

## 문제 세트 생성

### 생성 접수

`POST /api/v1/learning-materials/{materialId}/quiz-sets`

Headers: `Authorization`, `Content-Type: application/json`

```json
{
  "selectedTypes": ["MULTIPLE_CHOICE", "FILL_IN_THE_BLANK", "SHORT_ANSWER", "ESSAY"],
  "difficulty": "NORMAL",
  "maxQuestionCount": 10,
  "generationPrompt": "네트워크 부분에 집중해서 자격증 시험 스타일로 내줘.",
  "contentRevision": "8ae74b792008072a214e1e571fff8b049f283ed70382d8e192518c18231ff2f0"
}
```

- `selectedTypes`: 중복 없는 1개 이상. 값은 서버 `QuestionType`과 같은 `MULTIPLE_CHOICE`, `FILL_IN_THE_BLANK`, `SHORT_ANSWER`, `ESSAY`.
- `difficulty`: `EASY`, `NORMAL`, `HARD`.
- `maxQuestionCount`: 필수, `5`, `10`, `15`, `20` 중 하나.
- `generationPrompt`: 선택. 앞뒤 Unicode 공백을 제거한 값이 비어 있으면 `null`로 취급하고, 비어 있지 않으면 최대 300 Unicode code point다. 출제 초점과 스타일 선호이며 선택 유형·난이도·문제 수·학습자료 근거·보안 규칙을 바꾸지 못한다.
- `contentRevision`: 필수. 사용자가 OpenAI 전송 범위를 확인한 학습자료 본문의 UTF-8 바이트를 SHA-256으로 계산한 64자 lowercase hex다. 제목은 revision 계산에 포함하지 않는다.
- 요청을 접수하면 서버는 현재 사용자 소유의 학습자료 행을 트랜잭션 안에서 잠근 뒤 현재 본문의 `contentRevision`을 계산해 요청값과 원자적으로 대조한다. 일치할 때만 새 불변 `quizSetId`를 만들고 생성 이벤트를 발행한다.
- 현재 revision이 요청값과 다르면 `409 QUIZ_003`을 반환하고 QuizSet이나 OpenAI 생성 작업을 만들지 않는다. 클라이언트는 최신 본문과 전송 범위를 다시 확인한 뒤 새 revision으로 요청한다.
- 접수 시점의 학습자료 제목으로 `{학습자료명} 퀴즈` 기본 이름을 만들어 QuizSet에 저장한다. 전체가 255 Unicode code point를 넘으면 학습자료 제목 부분만 최대 252 code point로 줄여 ` 퀴즈` 접미사를 보존한다.
- 같은 사용자에게 학습자료와 관계없이 `GENERATING` 세트가 있으면 새 요청을 받지 않는다.
- 서버가 트랜잭션 전에 worker·queue capacity를 예약할 수 없으면 `503 QUIZ_002`를 반환하고 QuizSet을 만들거나 본문 잠금 상태를 바꾸지 않는다. reservation 뒤 commit된 작업이 서버 종료 경쟁 등으로 executor에서 예외적으로 거절되면 성공 응답을 소급해 바꾸지 않고 해당 QuizSet을 `FAILED / GENERATION_FAILED`와 실패 알림으로 종결한다.
- 이 요청은 `Idempotency-Key`를 받지 않는다. 접수 응답 유실 여부는 [자료의 활성 생성 조회](#자료의-활성-생성-조회)로 확인하며, 활성 생성이 없을 때만 새 QuizSet 생성을 요청한다.
- 성공 응답의 `requestedConfig`는 선택 유형·난이도·최대 문제 수만 echo한다. `generationPrompt`는 응답하거나 DB에 영속화하지 않고 현재 생성 작업의 메모리 데이터로만 사용한다.
- 클라이언트는 성공 응답의 `quizSetId`와 `requestedConfig`를 현재 사용자 범위의 기기 로컬 상태에 연결할 수 있다. 이 값은 생성 중·성공 화면의 요청 조건 표시용이며 서버 상태나 성공 판정의 근거가 아니다.

```json
{
  "success": true,
  "data": {
    "quizSetId": "qset_123",
    "materialId": "123",
    "quizTitle": "운영체제 스케줄링 퀴즈",
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
- 클라이언트는 반환된 `quizSetId`로 같은 사용자·기기의 로컬 `requestedConfig`를 조회할 수 있다. 로컬 값이 없어도 활성 생성 조회와 polling은 동일하게 진행한다.

```json
{
  "success": true,
  "data": {
    "quizSetId": "qset_123",
    "materialId": "123",
    "quizTitle": "운영체제 스케줄링 퀴즈",
    "status": "GENERATING",
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

- 모든 상태는 `quizSetId`, `materialId`, 비어 있지 않은 `quizTitle`, `status`를 반환하며 `requestedConfig`는 반환하지 않는다.
- `GENERATING`은 공통 필드와 다음 조회 권고값 `pollAfterSeconds`만 반환한다. foreground·online 클라이언트는 이 값을 다음 조회 간격의 단일 기준으로 사용하고 별도 고정 간격을 만들지 않는다. background에서는 polling을 중단한다. 로컬 요청 조건이 없으면 클라이언트는 일반 생성 진행 상태를 표시한다.
- `READY`는 공통 필드와 `questions`를 반환한다. 최종 유효 문제가 요청 수의 80% 이상이라는 뜻이며 클라이언트는 로컬 요청 조건 유무와 관계없이 `questions`에서 실제 문제 수와 포함 유형을 계산한다. 실제 수가 최대 문제 수보다 적거나 요청한 유형 일부가 포함되지 않아도 된다.
- `FAILED`는 공통 필드와 `failure`만 반환하고 문제를 포함하지 않는다. 로컬 요청 조건이 없으면 일반 실패 안내와 새 조건 선택 행동을 제공하며, 외부 생성 서비스 상세는 노출하지 않는다.
- 외부 생성 후보의 문제 번호는 공개 계약에 사용하지 않는다. 서버는 유형별 검증을 통과한 후보의 원래 배열 순서에 따라 `number=1..N`을 새로 부여한다.
- 검증에서 제외된 후보는 `questions`에 포함하지 않으며 문제는 빈 번호 없이 `number` 오름차순으로 반환한다.

`GENERATING` 예시:

```json
{
  "success": true,
  "data": {
    "quizSetId": "qset_123",
    "materialId": "123",
    "quizTitle": "운영체제 스케줄링 퀴즈",
    "status": "GENERATING",
    "pollAfterSeconds": 3
  },
  "error": null
}
```

`FAILED` 예시:

```json
{
  "success": true,
  "data": {
    "quizSetId": "qset_123",
    "materialId": "123",
    "quizTitle": "운영체제 스케줄링 퀴즈",
    "status": "FAILED",
    "failure": {
      "code": "SOURCE_INSUFFICIENT",
      "message": "학습 자료에서 충분한 문제를 만들지 못했어요.",
      "retryable": false
    }
  },
  "error": null
}
```

- `failure.code`는 `SOURCE_INSUFFICIENT` 또는 `GENERATION_FAILED`다.
- `SOURCE_INSUFFICIENT`: 두 번의 정상 구조 LLM 응답이 모두 학습자료 근거 부족을 표시하고 최종 유효 문제가 요청 수의 80%에 미달했다. 공개 메시지는 자료 자체의 결함을 단정하지 않고 `학습 자료에서 충분한 문제를 만들지 못했어요.`를 사용한다. 기본 `retryable=false`다.
- `GENERATION_FAILED`: 네트워크 재시도 소진, 거절, 응답 잘림·구조 오류, 검증·보완 후 80% 미달 또는 저장·워커 실패다. 자료 부족과 모델 품질 문제가 애매하면 이 값을 사용한다. 내부·LLM 상세는 숨기고 공개 메시지는 `문제를 만드는 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요.`를 사용한다. `retryable=true`다.
- 상태 재조회에서도 같은 실패 의미를 반환할 수 있도록 서버는 QuizSet의 `failure_code`를 보존한다. `message`와 `retryable`은 저장된 코드에 대한 공개 정책으로 계산한다.
- 두 값은 비동기 QuizSet 작업 결과이지 HTTP `ApiError.code`가 아니다. 네트워크 실패를 `FAILED`로 추정해서는 안 된다.
- QuizSet terminal 상태와 사용자 알림의 원자성·중복 방지는 [알림 데이터 계약](contract-data-notifications.md#트랜잭션-불변성)을 따른다.

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
| `MULTIPLE_CHOICE` | `choices: [{ choiceId, text }]` | 가변 길이 3~5개. 정답 표시 없음 |
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

클라이언트는 객관식 `choices` 길이를 4개로 가정하지 않고 3개, 4개, 5개를 모두 렌더링·선택·제출할 수 있어야 한다. 배열 순서는 서버가 확정한 보기 순서를 그대로 사용한다.

## 퀴즈 관리 확장 계약

이 절은 홈·학습의 퀴즈명 표시와 검색 가능한 `내 퀴즈` 관리 화면을 위한 계약이며 서버에 구현되었다. 웹·앱 소비자는 학습자료 제목을 `quizTitle`로 대신하지 않고 아래 이동 판단 필드를 사용한다.

### 퀴즈 이름

- QuizSet은 부모 학습자료의 현재 `materialTitle`과 분리된 비어 있지 않은 `quizTitle`을 가진다.
- `quizTitle`은 1~255 Unicode code point다. 앞뒤 Unicode 공백을 제거한 결과가 비어 있으면 `400 COMMON_001`과 `fields.field=quizTitle`을 반환한다.
- 퀴즈 이름은 사용자 범위에서 unique일 필요가 없다.
- 학습자료 제목을 나중에 바꿔도 기존 QuizSet의 `quizTitle`은 자동으로 바뀌지 않는다.
- 생성 접수 시 기본 이름은 접수 시점의 학습자료 제목으로 만든 `{학습자료명} 퀴즈`다. 255 Unicode code point를 넘으면 학습자료 제목 부분만 최대 252 code point로 줄여 접미사를 보존한다. 기존 QuizSet은 migration 시점의 연결 학습자료 제목으로 같은 규칙을 적용해 backfill한다.

### 내 퀴즈 목록 조회·이름 검색

`GET /api/v1/quiz-sets?page=1&size=6&query=운영체제`

성공 알림에서 특정 QuizSet을 찾을 때는 `GET /api/v1/quiz-sets?size=6&focusQuizSetId=qset_123`을 사용한다.

- 현재 인증 사용자가 소유한 QuizSet 중 `READY`와 `GENERATING`만 반환한다. `FAILED`는 서버 상태와 알림 근거로 유지하지만 내 퀴즈 목록에서는 제외한다.
- `query`는 선택이며 앞뒤 공백을 제거한 뒤 `quizTitle`을 대소문자 구분 없이 부분 검색한다. `materialTitle` 검색은 이 parameter의 의미에 포함하지 않는다.
- `focusQuizSetId`는 선택이며 `query`와 함께 보내지 않는다. 현재 정렬과 요청 `size`에서 해당 `READY` QuizSet을 포함하는 page를 찾아 반환하며 이때 `page`는 보내지 않는다. 대상이 없거나 다른 사용자 소유이거나 `FAILED`이면 `404 COMMON_003`이다.
- 기본 정렬은 `updatedAt DESC, quizSetId DESC`다. 이름 변경 성공 시 `updatedAt`이 바뀌므로 목록 상단 순서가 바뀔 수 있다.
- `page`는 1부터 시작하고 `size`의 기본값은 6이다.

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "quizSetId": "qset_123",
        "quizTitle": "운영체제 중간고사 대비",
        "materialId": "123",
        "materialTitle": "운영체제 핵심 정리",
        "status": "READY",
        "questionCount": 10,
        "createdAt": "2026-08-26T00:10:00Z",
        "updatedAt": "2026-08-28T01:00:00Z",
        "latestCompletedAttemptId": "550e8400-e29b-41d4-a716-446655440000",
        "pendingSelfAssessmentAttemptId": null,
        "activeReviewSessionId": "review_123",
        "reviewQuestionCount": 2,
        "lastLearningActivityAt": "2026-08-28T00:30:00Z"
      }
    ],
    "page": 1,
    "size": 6,
    "totalElements": 1,
    "totalPages": 1
  },
  "error": null
}
```

- 이 목록 응답의 `status`는 `GENERATING | READY`다. 개별 QuizSet 상태 조회는 기존 `FAILED`를 계속 지원한다.
- `questionCount`는 `READY`에서 1 이상이고 `GENERATING`에서는 `null`이다.
- `latestCompletedAttemptId`는 해당 QuizSet에서 가장 최근에 완료된 현재 사용자의 `MAIN` attempt ID이며 완료 회차가 없으면 `null`이다. 전체 결과 또는 다시 풀기 맥락에 사용한다.
- `pendingSelfAssessmentAttemptId`는 해당 QuizSet에서 가장 최근의 `SELF_ASSESSMENT_REQUIRED` `MAIN` attempt ID이며 없으면 `null`이다. 서술형 자기평가 재진입에 사용한다.
- `activeReviewSessionId`는 해당 QuizSet에서 `COMPLETED`가 아닌 가장 최근 `REVIEW` attempt의 공개 ID이며 없으면 `null`이다. 활성 복습 재개에 사용한다.
- `reviewQuestionCount`는 `latestCompletedAttemptId`에서 현재 판정이 `INCORRECT|PARTIAL`이고 아직 복습으로 해결되지 않은 문항 수다. 완료 본 퀴즈가 없거나 대상이 없으면 `0`이다.
- `lastLearningActivityAt`은 해당 QuizSet에서 현재 사용자의 `MAIN|REVIEW` attempt 중 가장 최근 `updatedAt`이며 시도 이력이 없으면 `null`이다. QuizSet 이름 변경 시각과 혼용하지 않는다.
- 목록은 문제 본문, 정답, 제출 답안과 전체 학습자료 본문을 포함하지 않는다.
- 검색 결과 없음은 같은 page 모양에서 `items=[]`, `totalElements=0`, `totalPages=0`으로 반환한다.
- `totalElements`와 `totalPages`는 `FAILED`를 제외한 목록을 기준으로 계산한다.

### 퀴즈 이름 변경

`PATCH /api/v1/quiz-sets/{quizSetId}`

```json
{
  "quizTitle": "운영체제 기말 대비"
}
```

```json
{
  "success": true,
  "data": {
    "quizSetId": "qset_123",
    "quizTitle": "운영체제 기말 대비",
    "updatedAt": "2026-08-28T01:05:00Z"
  },
  "error": null
}
```

- 현재 사용자가 소유한 QuizSet만 변경할 수 있으며 없거나 소유하지 않으면 `404 COMMON_003`이다.
- 제목은 생성 결과와 독립적인 사용자 메타데이터이므로 `GENERATING`, `READY`, `FAILED` 모두에서 변경할 수 있다.
- 이름 변경은 문제·정답·풀이·결과·복습 snapshot을 바꾸지 않는다.
- 성공 뒤 홈 최신 복습, 최근 퀴즈, 내 퀴즈 목록과 QuizSet 상세의 관련 캐시를 무효화하거나 응답값으로 갱신한다.

### 기존 응답 보강

- `GET /api/v1/quiz-sets/{quizSetId}`의 모든 상태 응답에 비어 있지 않은 `quizTitle`을 추가한다.
- `GET /api/v1/quiz-reviews/latest`의 완료 회차가 있는 응답에 비어 있지 않은 `quizTitle`을 추가한다. 완료 회차가 없으면 `quizTitle=null`이다.
- `materialTitle`은 부모 자료 맥락이며 `quizTitle`의 fallback이 아니다.

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

### 단답형·빈칸 현재 판정 수정

`PUT /api/v1/quiz-attempts/{attemptId}/grading-overrides/{questionId}`

기존 소비자 호환을 위해 `PUT /api/v1/quiz-attempts/{attemptId}/short-answer-gradings/{questionId}`도 역사적 이름의 호환 별칭으로 같은 요청·응답·허용 유형과 오류 의미를 유지한다. 기존 web/app 소비자는 즉시 경로를 변경하지 않아도 되며, 신규 소비자는 공통 `grading-overrides` 경로를 사용한다.

Headers: `Authorization`, `Content-Type: application/json`

```json
{
  "outcome": "CORRECT"
}
```

- `outcome`: `CORRECT` 또는 `INCORRECT`. 사용자가 확인한 현재 판정으로 교체할 값이다.
- 현재 사용자 소유이며 `COMPLETED`인 `MAIN` attempt에서 답을 작성한 `SHORT_ANSWER` 또는 `FILL_IN_THE_BLANK`에 적용한다. 빈칸형은 일부 빈칸만 작성했더라도 제출 답안이 하나 이상 있으면 적용할 수 있다. 객관식·서술형, 완전 미응답 단답형·빈칸형과 완료 전 attempt는 `409 ATTEMPT_001`이다.
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
    "reviewAvailable": true,
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
- 답을 작성한 단답형·빈칸형은 화면에 표시할 현재 유효 `outcome`을 제공한다. 서버가 보존하는 `automaticOutcome`과 `userOverrideOutcome`은 현재 결과 화면에서 직접 사용하지 않으므로 이 조회 projection에 반복하지 않는다.
- 서술형 `outcome`: `CORRECT`, `PARTIAL`, `INCORRECT`.
- 각 `questionResults` 항목은 다른 문제 조회 없이 렌더링할 수 있어야 한다. 객관식은 `choices: [{ choiceId, text }]`, 빈칸은 풀이 때와 같은 `prompt`와 `blanks: [{ blankId, number }]`를 포함한다.
- 객관식 `response.selectedChoiceId`와 `representativeAnswer.selectedChoiceId`, 빈칸 `response.blankAnswers[].blankId`와 `representativeAnswer.blankAnswers[].blankId`는 각각 함께 반환된 보기·빈칸 식별자를 그대로 참조한다.
- 미응답은 `response=null`, `outcome=INCORRECT`로 포함한다. 별도 `unanswered` 필드나 미응답 집계를 반환하지 않으며 클라이언트는 `response=null`로 답하지 않음을 표시하고 판정 수정 행동을 제공하지 않는다. 일부 빈칸만 작성한 경우 `response.blankAnswers`가 비어 있지 않으므로 판정 수정 행동을 제공할 수 있다.
- `representativeAnswer`는 결과 설명에 필요한 대표 정답만 공개한다. 빈칸·단답형의 허용 정답 전체를 반환하지 않는다. 서술형은 `modelAnswer`와 `keyPoints`를 제공한다.
- `summary.scoredGrading`의 분자·분모는 객관식·빈칸·단답형이며, 단답형·빈칸형은 최신 `outcome`을 분자 계산에 사용한다. 최초 제출 응답의 `automaticGrading`은 제출 시점 자동 판정 요약이므로 별도 의미를 유지한다.
- `summary`는 저장된 문항 결과와 복습 해결 상태를 기준으로 조회 시 계산한다. 클라이언트는 쓰기 성공 응답의 전체 결과를 적용하고 필요하면 이 결과 API를 다시 조회한다.
- `reviewAvailable`은 이 회차가 해당 QuizSet에서 가장 최근에 완료한 `MAIN`이고, 현재 미해결 문항 또는 이 회차를 원본으로 한 활성 복습 세션이 있을 때만 `true`다. 결과 화면은 `summary.reviewQuestionCount`만으로 과거 회차의 복습 시작 가능 여부를 추측하지 않는다.
- 서버가 보존하는 객관식·빈칸과 단답형의 `automaticOutcome`은 복습 뒤에도 바뀌지 않는다. 조회 projection의 단답형·빈칸형 `outcome`은 사용자 수정으로 바뀔 수 있고 복습 자체는 이를 변경하지 않는다. 복습 대상 여부는 서버가 현재 판정과 복습 해결 상태로 계산하며 결과 화면은 `summary.reviewQuestionCount`를 사용한다.

## 복습 세션

API의 `reviewSession`은 클라이언트가 복습 실행을 식별하는 공개 리소스 이름이다. 데이터 원장에서는 별도 복습 세션이 아니라 `attemptType=REVIEW`인 quiz attempt이며, `reviewSessionId`는 해당 REVIEW attempt의 공개 ID다. REVIEW attempt는 최초 `MAIN` attempt를 `sourceAttemptId`로 직접 가리키고 본 퀴즈와 같은 문항·제출 답안·채점 결과 구조를 사용한다.

복습 시작 시 대상 문항을 REVIEW attempt의 문항 목록으로 먼저 고정한다. 풀이·제출·채점 응답은 본 퀴즈와 동일한 문제 유형별 필드와 response 모양을 사용하며 복습 전용 답안 형식을 추가하지 않는다.

### 최신 복습 현황

`GET /api/v1/quiz-reviews/latest`

- 현재 사용자의 가장 최근 `COMPLETED` 본 퀴즈 회차 하나만 조회한다. 최신은 `completedAt DESC, 내부 attempt id DESC`로 결정하며 완료 시각이 같으면 나중에 저장된 회차가 우선한다. 그 회차에서 현재 최종 판정이 `INCORRECT|PARTIAL`이고 `reviewResolvedAt=null`인 문항이 복습 대상이다.
- `attemptNumber`는 해당 `quizSetId` 안에서 현재 사용자가 완료한 본 퀴즈 회차의 1부터 시작하는 순번이다.
- `quizTitle`은 해당 QuizSet의 현재 사용자 지정 이름이다. 홈·학습의 주 제목은 이 값을 사용한다.
- `materialTitle`은 해당 QuizSet이 참조하는 학습자료의 현재 제목이다. 클라이언트는 이 값을 하드코딩한 일반 제목으로 대체하거나 `attemptNumber`를 완료 시각처럼 표시하지 않는다.
- `completedAt`은 최신 완료 본 퀴즈 회차가 완료된 시각이며 공통 계약에 따라 ISO 8601 UTC 문자열이다.
- `totalQuestionCount`는 해당 `quizSetId`의 전체 문항 수다. `전체 문제 다시 풀기`의 대상 수와 레이블은 이 값을 사용하며 `reviewQuestionCount`와 혼용하지 않는다.
- `reviewQuestionCount`는 그 회차의 현재 미해결 문항 수다.
- 그 회차를 원본으로 한 활성 복습 세션이 있으면 `activeReviewSessionId`를 반환한다.

```json
{
  "success": true,
  "data": {
    "sourceAttemptId": "550e8400-e29b-41d4-a716-446655440000",
    "quizSetId": "qset_123",
    "attemptNumber": 2,
    "quizTitle": "운영체제 중간고사 대비",
    "materialTitle": "운영체제 핵심 정리",
    "completedAt": "2026-08-26T00:20:00Z",
    "totalQuestionCount": 10,
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
    "quizTitle": null,
    "materialTitle": null,
    "completedAt": null,
    "totalQuestionCount": 0,
    "reviewQuestionCount": 0,
    "activeReviewSessionId": null
  },
  "error": null
}
```

최신 완료 회차는 있지만 미해결 문항이 없으면 세 식별 필드와 `quizTitle`, `materialTitle`, `completedAt`, `totalQuestionCount`는 최신 회차 맥락 값으로 반환하고 `reviewQuestionCount=0`, `activeReviewSessionId=null`로 반환한다.

이 응답 보강은 학습 메인의 `최근 퀴즈` UI가 전체 재풀이와 틀린 문제 풀이를 같은 QuizSet 맥락으로 정확히 표시하기 위한 공유 계약이며 서버 DTO와 웹 `LatestReview` 타입에 동기화되어 있다. 여러 API를 연쇄 조회하거나 일반 문구를 하드코딩하는 방식은 이 계약의 대체 구현으로 보지 않는다.

### 학습 메인 복습 후보

`GET /api/v1/quiz-reviews/candidates?limit=3`

- 현재 사용자의 QuizSet별 최신 `COMPLETED MAIN` 회차를 기준으로 `reviewQuestionCount > 0`인 항목만 반환한다. QuizSet 안의 최신도 `completedAt DESC, 내부 attempt id DESC`로 결정한다. 과거 회차를 선택하거나 여러 회차의 미해결 문항을 합치지 않는다.
- [최신 복습 현황](#최신-복습-현황)의 전역 최신 완료 `MAIN`과 같은 `quizSetId`는 학습 메인의 최근 퀴즈와 중복되므로 후보에서 제외한다.
- 활성 복습 세션이 있는 후보를 먼저, 그 안과 나머지 후보는 `lastLearningActivityAt DESC`, 동일 시각에는 `quizSetId ASC`로 정렬한다.
- `limit`은 선택이며 기본값은 `3`, 허용 범위는 `1..3`이다. 범위를 벗어나면 `400 COMMON_001`과 `fields.field=limit`을 반환한다.
- `activeReviewSessionId`는 후보의 `sourceAttemptId`를 원본으로 하고 `COMPLETED`가 아닌 가장 최근 REVIEW attempt의 공개 ID다. 다른 원본 회차의 활성 복습은 이 필드에 포함하지 않는다.
- `pendingSelfAssessmentAttemptId`는 해당 QuizSet에서 가장 최근의 `SELF_ASSESSMENT_REQUIRED MAIN` attempt ID이며 없으면 `null`이다. 소비자는 이 값이 있으면 복습 행동보다 자기평가 재개를 우선한다.
- `lastLearningActivityAt`은 해당 QuizSet에서 현재 사용자의 `MAIN|REVIEW` attempt 중 가장 최근 `updatedAt`이다. QuizSet 이름 변경 시각은 포함하지 않는다.
- 후보가 없으면 `items=[]`인 `200 OK`를 반환한다. 문제 본문, 정답, 제출 답안과 전체 학습자료 본문은 반환하지 않는다.

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "quizSetId": "qset_456",
        "quizTitle": "네트워크 기초 퀴즈",
        "materialTitle": "TCP/IP 핵심 정리",
        "sourceAttemptId": "550e8400-e29b-41d4-a716-446655440001",
        "pendingSelfAssessmentAttemptId": null,
        "activeReviewSessionId": "review_456",
        "reviewQuestionCount": 2,
        "lastLearningActivityAt": "2026-08-28T01:30:00Z"
      }
    ]
  },
  "error": null
}
```

### 선택한 QuizSet의 최신 대상 세션 생성

`POST /api/v1/review-sessions`

Headers: `Authorization`, `Content-Type: application/json`

```json
{
  "sourceAttemptId": "550e8400-e29b-41d4-a716-446655440000"
}
```

- `sourceAttemptId`는 현재 사용자가 소유하며 해당 QuizSet에서 가장 최근에 완료한 `MAIN` 회차여야 한다. [최신 복습 현황](#최신-복습-현황) 또는 [학습 메인 복습 후보](#학습-메인-복습-후보)의 값을 사용한다.
- 해당 회차에서 현재 최종 판정이 `INCORRECT|PARTIAL`이고 아직 해결되지 않은 문항을 번호 오름차순으로 snapshot해 서버 복습 세션을 만든다. 별도 저장 `reviewRequired` 플래그는 두지 않는다.
- 생성된 활성 세션의 snapshot은 원본 회차 단답형·빈칸형 판정 수정 뒤에도 중간 변경하지 않는다. 원본 회차의 최신 `reviewQuestionCount`와 활성 세션의 남은 문항 수는 별도 값이며, 수정된 대상 여부는 다음 세션 생성부터 반영한다.
- 소유하지 않은 회차, `MAIN|COMPLETED`가 아닌 회차, 해당 QuizSet의 과거 완료 회차, 대상이 없는 회차는 모두 `REVIEW_001`이다.
- 새 세션을 만들면 `201 Created`, 같은 `sourceAttemptId`의 활성 세션이 이미 있으면 새로 만들지 않고 `200 OK`로 기존 세션을 반환한다.
- source MAIN을 짧게 잠근 서비스 트랜잭션에서 기존 활성 세션을 먼저 확인해 같은 원본의 활성 세션을 하나만 유지한다. 별도 멱등 키나 payload fingerprint를 만들지 않는다.
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
      "pendingEssayQuestionIds": [],
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
- 제출 전 현재 문항과 답안은 열린 클라이언트 화면의 메모리에만 둔다. 화면을 이탈한 뒤 세션을 다시 조회하면 고정된 문제 목록만 받고 첫 문항부터 다시 풀며, 서버 draft 답안이나 `nextQuestionId`는 제공하지 않는다.
- `pendingEssayQuestionIds`는 아직 자기평가하지 않은 서술형 문제 ID를 문제 번호 오름차순으로 반환한다. `SOLVING`과 `COMPLETED`에서는 빈 배열이다.
- `SELF_ASSESSMENT_REQUIRED`에서는 이 목록과 결과 조회를 함께 사용해 제출된 서술형의 자기평가 상세를 복원한다.

```json
{
  "success": true,
  "data": {
    "reviewSessionId": "review_123",
    "sourceAttemptId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "SOLVING",
    "pendingEssayQuestionIds": [],
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
- 응답에는 `reviewSessionId`, `sourceAttemptId`, `status`, `reviewAvailable`, `summary`, `questionResults`를 포함한다. `summary`는 이번 재풀이의 자동 채점 수, 서술형 자기평가 수, 해결·미해결 수를 구분한다.
- `reviewAvailable`은 원본 `sourceAttemptId`가 해당 QuizSet의 최신 완료 `MAIN`이고 현재 미해결 문항 또는 활성 복습 세션이 있을 때만 `true`다. 복습 중 다른 탭에서 더 최신 `MAIN`이 완료되면 기존 복습 결과는 남은 오답 수와 관계없이 새 복습 시작 행동을 제공하지 않는다.

## 오류

### 최소 안정 코드

여섯 Notion 오류 코드, HTTP status와 사용자 복구 의미는 확정이다.

| 조건 | HTTP | 코드 | 복구 |
| --- | --- | --- | --- |
| 필드 누락·형식·허용 enum/개수 | `400` | 기존 `COMMON_001` | `fields`에 따라 입력 수정 |
| 읽을 수 없는 JSON | `400` | 기존 `COMMON_002` | 요청 본문 수정 |
| 없거나 소유하지 않은 NalQ 리소스 | `404` | 기존 `COMMON_003` | 목록과 현재 사용자 소유권 확인 |
| 예상하지 못한 서버 오류 | `500` | 기존 `COMMON_999` | 학습자료 생성은 같은 멱등 키로 재시도하고, QuizSet 생성은 활성 생성 조회 후 없을 때만 새 요청하며, 제출은 같은 attempt 또는 review session 식별자로 재시도 |
| 인증 정보 없음·잘못됨·만료 | `401` | 기존 `AUTH_005` | 갱신 또는 재로그인 |
| 활성 Notion 연결 없이 페이지 목록·복사를 요청했거나 callback의 유효한 OAuth 승인 state를 복구할 수 없음 | `400` 또는 callback `302` query | `NOTION_CONNECTION_REQUIRED` | 연결 상태를 다시 조회한다. `DISCONNECTED`면 최초 연결, `CONNECTED|REAUTH_REQUIRED`면 현재 상태에 맞는 승인을 처음부터 다시 시작하거나 붙여넣기로 전환 |
| Notion 자격을 갱신할 수 없거나 제공자 재동의가 필요함 | `409` | `NOTION_REAUTH_REQUIRED` | 현재 워크스페이스와 기존 편집 상태를 보존하되 `pageId`는 보존하지 않고, 같은 워크스페이스 재인증 뒤 페이지를 재선택 |
| 기존 연결이 있는데 OAuth callback에서 다른 워크스페이스가 확인됨 | callback `302` query | `NOTION_WORKSPACE_MISMATCH` | 기존 연결을 계속 사용하거나, 현재 연결을 해제한 뒤 다른 워크스페이스 연결을 새로 시작 |
| 선택 페이지 없음, 현재 연결에서 공유되지 않음 또는 접근 권한 철회 | `400` | `NOTION_PAGE_NOT_ACCESSIBLE` | 기존 편집 상태를 보존하고 다른 접근 가능 페이지 선택·권한 확인 또는 붙여넣기로 전환 |
| `truncated`, `unknown_block_ids` 또는 코드 영역 밖의 `<unknown>`·`<unknown ...>`으로 완전한 본문을 확인할 수 없음 | `400` | `NOTION_CONTENT_INCOMPLETE` | 부분 본문을 사용하지 않고 선택 페이지를 보존해 재시도하거나 붙여넣기로 전환 |
| Notion timeout·rate limit·일시 장애 또는 연결 해제 철회 결과를 확인할 수 없음 | `503` | `NOTION_TEMPORARILY_UNAVAILABLE` | 연결·선택·편집 상태를 보존하고 같은 작업을 재시도하거나 붙여넣기로 전환 |
| 생성 중인 학습자료 본문 수정 | `409` | `MATERIAL_001` | 제목만 수정하거나 생성 종료를 확인한 뒤 본문 저장 재시도 |
| 학습자료 본문 20,000자 초과 | `413` | `MATERIAL_002` | 본문을 줄인 뒤 같은 저장 흐름 재시도 |
| 같은 사용자에게 이미 `GENERATING` 작업이 있음 | `409` | `QUIZ_001` | 기존 생성 상태 확인 |
| 생성 작업을 접수할 수 없는 일시적 서버 상태 | `503` | `QUIZ_002` | 새 QuizSet과 본문 잠금이 생기지 않았으므로 같은 조건으로 새 생성 요청 |
| 확인한 학습자료 본문 revision과 현재 본문이 다름 | `409` | `QUIZ_003` | 최신 본문과 전송 범위를 다시 확인한 뒤 새 revision으로 생성 요청 |
| `READY`가 아닌 세트 제출, attempt UUID의 소유자·QuizSet 불일치, 자기평가 상태 또는 수정 불가 자동 채점 문항 | `409` | `ATTEMPT_001` | 최신 문제 세트·attempt 상태와 결과 확인 |
| 완료된 복습 세션 재변경 또는 이미 확정된 서술형 평가 변경 | `409` | `REVIEW_001` | 세션과 결과 재조회 |

- MVP의 안정 오류 코드는 `COMMON_001/002/003/999`, `AUTH_005`, `MATERIAL_001/002`, `NOTION_CONNECTION_REQUIRED`, `NOTION_REAUTH_REQUIRED`, `NOTION_WORKSPACE_MISMATCH`, `NOTION_PAGE_NOT_ACCESSIBLE`, `NOTION_CONTENT_INCOMPLETE`, `NOTION_TEMPORARILY_UNAVAILABLE`, `QUIZ_001/002/003`, `ATTEMPT_001`, `REVIEW_001`로 제한한다.
- Notion 오류는 위 여섯 가지 사용자 복구 의미로만 공개한다. 제공자 원시 오류명, HTTP 본문, 세부 block 타입, token 갱신 실패 원문과 내부 예외를 새 공개 코드나 `fields`로 전달하지 않는다.
- 공개 `401 AUTH_005`는 NalQ Access Token 인증 실패에만 사용한다. Notion `401`은 내부 갱신 뒤 성공하거나 `409 NOTION_REAUTH_REQUIRED`로 변환한다.
- 비동기 생성 실패는 정상 상태 조회의 `status=FAILED`로 전달한다. HTTP 오류와 혼용하지 않는다.
- `error.message`는 사용자가 다음 행동을 이해할 수준으로 쓰고 내부 예외·LLM·Notion 상세를 노출하지 않는다. 정확한 사용자 문구는 이 계약의 안정 필드가 아니며 클라이언트는 여섯 Notion `error.code`와 [학습자료 흐름의 보존·복구 규칙](../ux/flow-content-import.md#취소와-실패)으로 분기한다.
- 입력 오류의 `fields`는 `responses[0].selectedChoiceId`나 `responses[1].blankAnswers[0].blankId`처럼 클라이언트가 해당 입력을 찾을 수 있는 경로를 사용한다.

## 정렬과 조회 경계

- 문제, 결과, 복습 문항은 원래 문제 `number` 오름차순이다.
- 문제 세트·attempt·채점 결과·복습 snapshot의 불변성과 생명주기는 [학습자료와 퀴즈 데이터 계약](contract-data-quiz-learning.md)을 따른다.
- 서버에는 본 퀴즈 중간 진행·답안 API가 없다. 최종 제출 전 상태의 지속 저장과 새로고침·종료 뒤 복구를 HTTP 계약으로 요구하지 않는다.

## 개인정보와 로그

- 학습자료 본문, 사용자 답안, 모범 답안과 원문 근거는 민감한 학습 콘텐츠로 취급한다.
- 요청·응답 본문, Notion 접근 자격과 외부 생성 서비스 원문을 일반 애플리케이션 로그에 남기지 않는다. `attemptId`, `reviewSessionId`, `quizSetId`는 비밀이 아니지만 운영 로그에는 문제 해결에 필요한 식별자만 최소로 남긴다. 학습자료 생성의 `Idempotency-Key` 원문은 로그에 남기지 않는다.
- Notion access token과 갱신 자격은 서버에서 암호화해 저장하고 클라이언트에 반환하지 않는다. 서버는 제공자가 허용하는 갱신 절차를 사용하며, 갱신할 수 없으면 연결 메타데이터를 임의 삭제하지 않고 `REAUTH_REQUIRED` 상태로 전환한다.
- 연결 해제는 제공자 권한 철회 확인 뒤 로컬 token과 연결 메타데이터를 삭제한다. 철회 확인이 일시적으로 실패하면 로컬 자격을 보존하되 일반 API 사용에는 위 공개 상태와 오류 규칙을 적용한다.
- 운영 로그는 요청 추적 식별자, 사용자 내부 식별자, 리소스 식별자, 공개 오류 코드와 상태 전이에 필요한 최소 메타데이터만 남긴다.

## 호환성과 폐기

- 이 계약은 기존 인증 응답 봉투와 오류 규칙을 확장하되 인증 계약을 변경하지 않는다.
- `importId`, 서버 preview·draft·만료, 파일 업로드, Notion 동기화, 서버 풀이 draft API는 MVP에서 폐기된 전제다. 과거 초안 소비자가 있다면 이 계약 구현 전에 제거하고 `POST /api/v1/learning-materials` 직접 저장으로 전환한다.
- 문제 유형별 필드나 enum을 바꾸면 웹·앱의 입력·결과 읽기와 서버 채점을 동시에 갱신해야 한다. 새 필드는 하위 호환 가능한 선택 필드를 우선하고, 기존 enum 의미를 재사용하지 않는다.

## 열린 질문

- 여섯 Notion 오류의 정확한 사용자 문구와 화면 표현은 [학습자료 흐름의 열린 질문](../ux/flow-content-import.md#열린-질문)이 책임진다. 오류 코드, 보존 상태와 복구 행동은 확정 계약이다.
