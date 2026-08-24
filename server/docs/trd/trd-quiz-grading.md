---
document_type: trd
status: review
scope: server
---

# [TRD · Server] 최소 UUID 기반 퀴즈 제출·채점 설계

- 상태: 구현 완료, 서버 테스트 통과
- 제품 정책: [퀴즈 생성·풀이·결과·복습 PRD](../../../docs/prd/prd-quiz-learning.md#본-퀴즈-풀이와-제출)
- 사용자 흐름: [퀴즈 생성부터 복습까지](../../../docs/ux/flow-quiz-solving.md#b-본-퀴즈-풀이와-제출)
- API 계약: [학습자료·퀴즈·복습 API 계약](../../../docs/contracts/contract-api-quiz-learning.md#본-퀴즈-제출과-자기평가)
- 데이터 계약: [학습자료와 퀴즈 데이터 계약](../../../docs/contracts/contract-data-quiz-learning.md#본-퀴즈-회차와-채점-결과)

## 1. 목적과 결정

이 설계는 본 퀴즈 제출·결과 조회·단답형 판정 수정에서 중복 방지와 동시성 복구를 과하게 구현하지 않고 MVP에 필요한 최소 불변식만 남긴다.

확정한 방향은 다음과 같다.

- 본 퀴즈 제출은 클라이언트가 만든 UUID를 attempt 리소스 식별자로 사용한다.
- UUID를 유지한 같은 실행 안에서는 같은 ID로 재요청할 수 있지만, 새로고침·앱 종료 뒤 미제출 답안과 요청 복구를 보장하지 않는다.
- 서로 다른 UUID의 동일 답안을 서버가 추정해 합치지 않는다. 다시 풀어 새 UUID로 제출하면 별도 attempt가 될 수 있다.
- 단답형 판정 수정은 원하는 현재 `outcome`을 저장하는 멱등 `PUT`이다. 별도 멱등 키와 공개 revision 없이 마지막 커밋을 현재 값으로 사용한다.
- 쓰기 성공 응답은 부분 delta가 아니라 전체 결과 projection을 반환한다. 클라이언트는 성공 결과를 통째로 교체한다.

이전 구현의 `Idempotency-Key`, request fingerprint, replay entity/table과 공개 grading·summary revision은 제거했다.

## 2. 범위

### 포함

- 단답형 문제 세트의 최종 제출과 규칙 기반 자동 채점
- attempt 결과 조회
- 답을 작성한 단답형의 현재 판정 수정
- 같은 client-generated UUID의 attempt 중복 생성 방지
- 최신 판정으로 점수와 복습 대상 수 재계산
- 관련 DB migration, MVC·도메인·MySQL 통합 테스트

### 후속 범위

- 객관식·빈칸·서술형 제출과 결과 projection
- 서술형 자기평가와 미완료 자기평가 재진입
- 공개 복습 세션 생성·조회·전체 제출·결과 API
- 문제 세트 생성 작업과 상태 조회
- Web/App 실제 API 연결과 현재 화면의 미제출 상태 처리

후속 기능도 이 문서의 최소 원칙을 따른다. 자연스러운 리소스 ID가 있으면 그 ID를 사용하고 별도 replay/fingerprint 테이블을 추가하지 않는다.

## 3. 남기는 불변식

복구 편의 기능을 제거하더라도 다음은 데이터 정합성과 권한을 위해 유지한다.

- Access Token의 `userId`로 QuizSet과 attempt 소유권을 확인한다.
- `quiz_attempts.public_id`는 parse 가능한 UUID 문자열이며 전역 unique다.
- QuizSet은 현재 사용자 소유이고 `READY`여야 제출할 수 있다.
- 알 수 없거나 중복된 question ID와 문제 유형에 맞지 않는 답안 모양은 거절한다.
- 빠진 응답은 입력 오류가 아니라 미응답 오답으로 확정한다.
- attempt와 모든 문항 결과는 한 트랜잭션에서 함께 저장한다.
- `(attempt_id, question_id)`는 unique이며 FK로 원본 attempt와 question을 보장한다.
- 제출 답안과 최초 `automaticOutcome`은 불변이다.
- 사용자 수정은 최신 `userOverrideOutcome`만 바꾸며 다른 attempt나 문제의 허용 답안에 전파하지 않는다.
- `COMPLETED` attempt의 답을 작성한 `SHORT_ANSWER`만 수정할 수 있다.
- 답안, 허용 답안과 학습자료 본문을 일반 로그에 기록하지 않는다.

보안 token, 인증번호와 Refresh Token digest는 이 단순화 대상이 아니다.

## 4. 데이터 모델

### `quiz_attempts`

| 값 | 의미 |
| --- | --- |
| `public_id` | 클라이언트가 생성한 UUID. `VARCHAR(36)` unique 유지 |
| `quiz_set_id`, `user_id` | 소유권과 원본 문제 세트 |
| `status` | `SELF_ASSESSMENT_REQUIRED` 또는 `COMPLETED` |
| `automatic_correct_count`, `automatic_graded_count` | 제출 시 자동 판정 요약. 이후 override로 다시 쓰지 않음 |

`summary_revision`은 두지 않는다. 현재 채점 점수와 복습 수는 문항 결과에서 조회 시 계산한다.

### `quiz_question_results`

| 값 | 의미 |
| --- | --- |
| `submitted_answer` | 최종 제출 원문, 불변 |
| `automatic_outcome` | 제출 시 자동 판정, 불변 |
| `user_override_outcome` | 최신 사용자 판정. 수정 전에는 `null` |
| `review_resolved` | 복습으로 해결됐는지 여부 |

현재 판정은 `user_override_outcome != null`이면 override, 아니면 `automatic_outcome`이다. `grading_revision`, `corrected_at`과 수정 이력 테이블은 두지 않는다.

### 복습 snapshot

복습 세션을 구현할 때는 snapshot 문항 행 자체가 생성 시점의 대상 목록을 보존한다. `source_summary_revision`은 두지 않는다.

## 5. 공개 HTTP 경계

### 5.1 본 퀴즈 제출

`PUT /api/v1/quiz-sets/{quizSetId}/attempts/{attemptId}`

- Headers: `Authorization`, `Content-Type: application/json`
- 처음 보는 UUID: attempt와 결과를 생성하고 `201 Created`
- 같은 사용자·같은 QuizSet의 기존 UUID: body를 다시 검증·비교·적용하지 않고 기존 attempt를 `200 OK`
- 다른 사용자 또는 QuizSet이 사용한 UUID: `409 ATTEMPT_001`
- 별도 `Idempotency-Key`, key hash, payload fingerprint와 replay response를 사용하지 않음

클라이언트는 UUID v4를 만들지만 서버는 UUID로 parse 가능한지만 확인한다. UUID version을 서버 보안 조건으로 사용하지 않고 문자열은 canonical lowercase 형태로 저장한다.

### 5.2 결과 조회

`GET /api/v1/quiz-attempts/{attemptId}/result`

- 현재 사용자 소유 attempt만 조회한다.
- 단답형은 현재 `outcome`만 공개한다.
- `gradingRevision`, `summary.revision`, `automaticOutcome`, 내부 override 필드를 노출하지 않는다.
- summary는 현재 문항 결과에서 계산한다.

### 5.3 단답형 판정 수정

`PUT /api/v1/quiz-attempts/{attemptId}/short-answer-gradings/{questionId}`

```json
{
  "outcome": "CORRECT"
}
```

- 같은 현재 outcome이면 아무 값도 바꾸지 않고 `200 OK`
- 다른 outcome이면 `userOverrideOutcome`을 교체하고 `200 OK`
- `expectedRevision`과 `Idempotency-Key`를 받지 않음
- 성공 `data`는 결과 조회와 같은 전체 `QuizAttemptResult`
- 동시에 다른 값이 요청되면 마지막으로 커밋된 요청이 현재 값

웹은 한 화면에서 수정 요청을 하나씩 보내고 성공 결과를 통째로 적용한다. 다중 탭의 오래된 화면까지 서버가 병합하지 않으며 결과 재조회로 최종 상태에 수렴한다.

## 6. 처리 흐름과 트랜잭션

### 6.1 제출

1. `attemptId`를 UUID로 parse하고 canonical 문자열로 바꾼다.
2. 현재 사용자 소유 QuizSet을 쓰기 잠금으로 조회하고 `READY`인지 확인한다.
3. 같은 UUID attempt가 있으면 소유자와 QuizSet을 비교한다.
4. 같은 리소스면 최초 attempt를 반환한다. 다른 리소스면 `ATTEMPT_001`이다.
5. 새 UUID면 payload와 question ID·답안 모양을 검증한다.
6. 단답형을 `ShortAnswerGrader`로 판정한다.
7. attempt와 모든 question result를 한 트랜잭션으로 저장하고 flush한다.
8. 제출 응답을 반환한다.

같은 QuizSet의 동시 제출은 QuizSet 행 잠금으로 직렬화한다. 서로 다른 QuizSet이 같은 UUID를 동시에 사용한 비정상 경합은 DB unique 제약이 막고 `ATTEMPT_001`로 매핑한다. 이 드문 경합을 복구하기 위한 별도 replay transaction이나 advisory lock은 추가하지 않는다.

### 6.2 단답형 판정 수정

1. 현재 사용자 소유 attempt를 쓰기 잠금으로 조회한다.
2. attempt 상태, question 소속·유형, 미응답 여부를 검증한다.
3. 현재 outcome과 요청 outcome이 같으면 변경하지 않는다.
4. 다르면 최신 `userOverrideOutcome`을 저장한다.
5. 같은 트랜잭션 안에서 현재 점수와 복습 대상 수를 다시 계산한다.
6. 전체 결과 projection을 반환하고 커밋한다.

attempt 행 잠금은 revision 충돌을 만들기 위한 것이 아니라 override 저장과 결과 집계를 한 트랜잭션 결과로 맞추기 위한 최소 직렬화다. 별도 조건부 revision update는 사용하지 않는다.

### 6.3 네트워크 실패

| 상황 | 처리 |
| --- | --- |
| 서버 commit 전 연결 실패 | 같은 화면이 남아 있으면 같은 UUID로 제출 재시도 |
| 서버 commit 후 응답 유실 | 같은 UUID 재요청이 기존 attempt를 반환 |
| 새로고침·종료로 UUID와 답안 유실 | 복구하지 않고 다시 풀어 새 UUID로 제출 |
| 단답형 수정 응답 유실 | 같은 outcome을 다시 `PUT`하거나 결과 조회 |

서로 다른 UUID로 생긴 동일 답안 attempt와 다중 탭의 일시적인 stale UI는 MVP에서 수용한다.

## 7. 코드 책임 분리

기존 451줄의 `QuizAttemptService`는 다음 사용 사례 단위로 분리했다.

| 클래스 | 책임 |
| --- | --- |
| `QuizAttemptSubmissionService` | UUID 제출, 소유권·상태·payload 검증, 자동 채점과 저장 |
| `QuizAttemptResultService` | 소유권 확인과 전체 결과 projection |
| `ShortAnswerGradingService` | 최신 override 저장 후 전체 결과 반환 |
| `QuizAttemptResultProjector` | 현재 문항 결과에서 공통 result·summary 조립 |
| `ShortAnswerGrader` | 외부 의존성 없는 정규화와 완전 일치 판정 |

Controller는 HTTP method/path, 인증 principal, 입력 DTO와 `201/200` 선택만 담당한다. 서비스는 `Controller → Service → Repository` 방향을 유지한다.

## 8. 제거·변경 대상

### 삭제

- `QuizAttemptSubmission`
- `QuizAttemptSubmissionRepository`
- `ShortAnswerGradingIdempotency`
- `ShortAnswerGradingIdempotencyRepository`
- `QuizRequestDigest`

### 변경

- `QuizAttemptController`: 제출 `POST`를 UUID path `PUT`으로 변경하고 두 쓰기 API의 `Idempotency-Key` 제거
- `QuizAttempt`: 서버 UUID 생성 대신 client public ID를 받는 factory, `summaryRevision` 제거
- `QuizQuestionResult`: `gradingRevision`, `correctedAt`과 조건부 revision update 제거
- `QuizAttemptRepository`: 전역 public ID 조회와 사용자 소유 쓰기 잠금 조회
- 요청·응답 DTO: `expectedRevision`, `gradingRevision`, summary revision 제거
- `V5__create_quiz_grading.sql`: replay 두 테이블과 revision 컬럼 제거
- 복습 내부 모델: `sourceSummaryRevision` 제거

V5는 아직 `dev`에 병합되지 않은 신규 migration이므로 V6 보정 migration을 만들지 않고 V5 자체를 수정한다. 이미 V5를 적용한 개인 로컬 DB는 개발 데이터 초기화 후 다시 적용하며 운영 migration 이력으로 취급하지 않는다.

학습자료 생성과 QuizSet 비동기 생성에 쓰는 기존 `Idempotency-Key` 계약은 별개다. 전역 CORS 허용 header와 다른 도메인의 digest 구현은 제거하지 않는다.

## 9. 오류 매핑

| 조건 | 응답 |
| --- | --- |
| UUID·outcome·답안 형식 오류 | `400 COMMON_001` |
| JSON 파싱 실패 | `400 COMMON_002` |
| attempt/question/QuizSet 없음 또는 타인 소유 | `404 COMMON_003` |
| `READY`가 아닌 세트, 완료 전 attempt, 수정 불가 유형, 미응답 단답형 | `409 ATTEMPT_001` |
| 다른 사용자·QuizSet의 UUID 재사용 | `409 ATTEMPT_001` |
| 예상하지 못한 DB·집계 실패 | `500 COMMON_999` |

다른 사용자 리소스의 존재 여부와 제출 답안은 오류에 포함하지 않는다.

## 10. 테스트 우선 구현·검증 기록

### 1단계: 실패 재현

- MVC: 새 제출 `PUT`이 현재 매핑되지 않음을 확인
- MVC: body-only 단답형 수정이 현재 멱등 header·revision 계약 때문에 실패함을 확인
- MVC: 새 결과 JSON에 revision 필드가 없어야 한다는 테스트의 실패 확인
- MySQL: client attempt ID 대신 서버 UUID와 replay 행이 생성되는 현재 동작 확인

### 2단계: 최소 구현

- 신규 UUID 제출 `201`, attempt와 question result 한 세트 저장
- 같은 사용자·QuizSet·UUID 재요청 `200`, 행 증가 없음, 최초 답안 유지
- 다른 사용자 또는 QuizSet의 UUID 재사용 `409`
- 잘못된 UUID `400`, 타인 QuizSet `404`, `READY`가 아닌 세트 `409`
- 단답형 자동 판정과 결과 projection
- body-only 판정 수정, 같은 outcome no-op, 다른 outcome last-write-wins
- 수정 뒤 제출 답안·최초 자동 판정 불변, 점수·복습 수 재계산
- 결과 JSON에 grading·summary revision이 없음

### 3단계: 통합 검증

- 같은 QuizSet의 동시 동일 UUID 제출은 attempt 한 개만 생성
- 동시에 상반된 단답형 수정이 모두 정상 종료되고 최종 결과와 summary가 DB 현재값과 일치
- 활성 복습 snapshot 문항 목록은 원본 단답형 판정 수정 뒤에도 바뀌지 않음
- `ShortAnswerGraderTest`의 Unicode 정규화·완전 일치 규칙 유지
- 집중 MVC·도메인·MySQL Testcontainers 테스트 후 `server/gradlew test`
- `git diff --check`

동시 요청의 정확한 승자 순서, 새로고침 뒤 답안 복원, 서로 다른 UUID의 동일 payload dedupe는 테스트하거나 보장하지 않는다.

## 11. 소비자 동기화

서버 구현은 공유 API·데이터 계약을 기준으로 완료했다. Web/App은 후속 단계에서 다음을 제거해야 한다.

- 7일 미제출 답안 복구를 필수로 보는 상태·저장 규칙
- attempt UUID와 payload snapshot의 지속 저장 의무
- `expectedRevision`, `gradingRevision`, `summary.revision` 기반 병합·충돌 UI
- 단답형 수정의 부분 summary delta 적용

Web/App 코드는 이번 서버 설계 단계에서 수정하지 않는다. 실제 연동 전 각 애플리케이션 TRD와 adapter를 이 계약에 맞춰 갱신한다.
