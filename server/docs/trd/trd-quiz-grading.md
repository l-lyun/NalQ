---
document_type: trd
status: review
scope: server
---

# [TRD · Server] 퀴즈·복습 통합 저장 모델

- 상태: 목표 설계 확정, V5 SQL 반영·Java 구현 동기화 전
- 제품 정책: [퀴즈 생성·풀이·결과·복습 PRD](../../../docs/prd/prd-quiz-learning.md)
- 사용자 흐름: [퀴즈 생성부터 복습까지](../../../docs/ux/flow-quiz-solving.md)
- API 계약: [학습자료·퀴즈·복습 API 계약](../../../docs/contracts/contract-api-quiz-learning.md)
- 데이터 계약: [학습자료와 퀴즈 데이터 계약](../../../docs/contracts/contract-data-quiz-learning.md)

## 1. 목적과 범위

본 퀴즈와 복습을 서로 다른 저장 모델로 구현하지 않고 동일한 attempt·문항·제출·채점 구조로 처리한다. 복습은 별도 채점 시스템이 아니라 최초 `MAIN`에서 아직 해결하지 못한 문제만 담긴 `REVIEW` attempt다.

이번 단계는 사용자 화면에 필요한 문제 유형별 데이터까지 포함한 목표 저장 모델을 확정한다. 기존 Java entity, repository, service, migration과 테스트의 동기화는 후속 구현 범위다.

## 2. 핵심 결정

- `quiz_attempts`에 `attempt_type=MAIN|REVIEW`와 자기참조 `source_attempt_id`를 둔다.
- 모든 `REVIEW`는 최초 `MAIN`을 직접 가리키며 review-to-review 체인을 만들지 않는다.
- `quiz_review_sessions`, `quiz_review_session_questions`는 만들지 않는다.
- 한 회차의 문제 목록은 `quiz_attempt_questions`가 담당한다. 복습 시작 시 문항 행부터 생성해 snapshot을 고정한다.
- 공통 `quiz_question_answers`와 `answer_role`은 제거하고 객관식·단답형·서술형·빈칸형 원장을 분리한다.
- 사용자 제출은 유형별 정답 원장과 분리된 `quiz_submitted_answers`에 저장한다.
- 문제 유형 값은 현재 서버 `QuestionType`과 같은 `MULTIPLE_CHOICE|FILL_IN_THE_BLANK|SHORT_ANSWER|ESSAY`를 사용한다.
- 외부 생성 결과의 문제 번호는 저장하지 않는다. 유형별 검증을 통과한 후보만 원래 배열 순서대로 모아 서버가 `question_number=1..N`을 부여한다.
- 생성 요청 수나 유형별 목표 수를 성공 조건으로 저장하지 않는다. 유형별 원장이 완전한 유효 문제 하나 이상을 확정하면 `READY`, 하나도 없으면 `FAILED`다.
- 생성 실패 원인은 `quiz_sets.failure_code`에 보존하고 메시지와 재시도 가능 여부는 서버 정책으로 계산한다.
- 최초 자동 판정과 현재 최종 판정을 `automatic_grading_result`, `final_grading_result`로 구분한다.
- `grading_method`는 최종 판정의 출처이며 조회 시 항상 `final_grading_result`를 사용한다.
- 복습 성공은 원본 `MAIN` 문항의 `review_resolved_at`으로 기록하고 원래 오답 판정은 바꾸지 않는다.
- `attemptId`는 클라이언트가 정한 불변 `MAIN` 회차의 공개 식별자다. 같은 경로에 이미 존재하는 회차는 저장된 현재 상태를 반환하며 별도 request hash·fingerprint·replay 원장은 만들지 않는다.
- 생성 요청의 `requestedConfig`는 서버 DB에 저장하지 않는다. 생성 접수 성공 응답에서만 요청값을 echo하고 이후 상태 조회는 서버 상태와 실제 확정 문제 정보만 반환한다.
- MVP에는 worker lease·별도 생성 잠금 테이블, 교차 문제 참조를 막는 복합 FK, 유형별 제출 테이블을 추가하지 않는다. 일반 트랜잭션과 기존 unique/check 제약, 제출 서비스의 문제 소속 검증을 사용한다.
- 성능 개선용 보조 인덱스는 이번 migration에 추가하지 않는다.

## 3. 물리 데이터 모델

별도 설명이 없는 유형별 하위 테이블은 서버의 기존 관례대로 `id BIGINT` PK를 사용한다. 외부에 식별자를 노출하는 선택지와 빈칸만 `public_id`를 추가하며, 감사 컬럼은 공통 엔티티 규칙을 따르므로 아래 표에서 생략한다.

### 3.1 `quiz_sets`, `quiz_questions`

`quiz_sets`에는 기존 상태와 함께 nullable `failure_code`를 둔다.

| 컬럼 | 의미 |
| --- | --- |
| `status` | `GENERATING|READY|FAILED` |
| `failure_code` | `FAILED`일 때 `SOURCE_INSUFFICIENT|GENERATION_FAILED`, 그 외 상태는 `null` |

요청한 유형·난이도·최대 문제 수와 실제 생성 문제 수는 이 테이블의 성공 판정용 컬럼으로 추가하지 않는다. 실제 문제 수와 포함 유형은 확정된 `quiz_questions`에서 계산한다.

| 컬럼 | 의미 |
| --- | --- |
| `question_number` | 문제 세트 안의 표시 순서 |
| `question_type` | 문제 유형. 기존 모호한 `type` 이름을 명시적으로 변경 |
| `topic` | 결과·복습에서 표시할 주제 |
| `prompt` | 문제 본문 |
| `explanation` | 제출·채점 후 공개 가능한 해설 |
| `source_excerpt` | 결과 근거로 사용할 원문 발췌 |

문제 행에는 유형별 보기·정답을 두지 않는다. `FILL_IN_THE_BLANK`의 `prompt`에는 `[1]`, `[2]` 번호 마커를 포함한다. 문제와 유형별 원장은 문제 세트가 사용되기 시작한 뒤 수정하지 않는다.

### 3.2 객관식 `quiz_question_choices`

| 컬럼 | 의미 |
| --- | --- |
| `public_id` | 공개 `choiceId`로 사용하는 UUID |
| `question_id` | `MULTIPLE_CHOICE` 문제 |
| `choice_value` | 사용자에게 표시할 보기 문구 |
| `is_correct` | 서버 채점용 정답 여부. 풀이 전 외부 비공개 |

MVP는 문제당 보기 3개 이상 5개 이하, `is_correct=true`인 보기 정확히 하나를 요구한다. 모든 보기를 저장하므로 오답을 정답 테이블에 표현하기 위한 역할 값은 필요하지 않다.

보기는 불변이고 의미 있는 별도 순서를 요구하지 않으므로 순서 컬럼을 두지 않는다. API 배열은 조회마다 흔들리지 않도록 내부 `id` 오름차순으로 만든다.

### 3.3 단답형 `quiz_short_answer_answers`

| 컬럼 | 의미 |
| --- | --- |
| `question_id` | `SHORT_ANSWER` 문제 |
| `answer_value` | 허용 답안 원문 |
| `normalized_value` | 자동 채점 비교값. 외부 비공개 |

모든 행이 허용 답안이므로 `answer_role`을 두지 않는다. 한 문제는 답안을 하나 이상 가져야 하고 정규화 뒤 빈 문자열 또는 중복 값이 없어야 한다. 결과의 `representativeAnswer`는 내부 `id`가 가장 작은 행의 `answer_value`를 사용한다.

### 3.4 서술형 `quiz_essay_answer_guides`

| 컬럼 | 의미 |
| --- | --- |
| `question_id` | `ESSAY` 문제와 일대일 연결하는 PK/FK |
| `model_answer` | 결과와 자기평가에 표시할 모범 답안 |
| `key_points` | 하나 이상의 핵심 포인트 문자열을 담은 JSON 배열 |

모범 답안과 핵심 포인트는 풀이 전 공개하지 않는다. 공통 해설과 원문 근거는 `quiz_questions`에서 읽는다.

### 3.5 빈칸형 원장

`quiz_fill_in_the_blanks`는 입력칸을 정의한다.

| 컬럼 | 의미 |
| --- | --- |
| `public_id` | 공개 `blankId`로 사용하는 UUID |
| `question_id` | `FILL_IN_THE_BLANK` 문제 |
| `blank_number` | `prompt`의 `[n]`과 연결하는 1부터 시작하는 번호 |

`quiz_fill_in_the_blank_answers`는 각 빈칸의 허용 답안을 저장한다.

| 컬럼 | 의미 |
| --- | --- |
| `blank_id` | `quiz_fill_in_the_blanks.id` FK |
| `answer_value` | 허용 답안 원문 |
| `normalized_value` | 자동 채점 비교값. 외부 비공개 |

한 문제는 빈칸 1개 또는 2개를 가진다. `blank_number`는 1부터 연속이고 `(question_id, blank_number)`는 unique다. 각 번호 마커는 `prompt`에 정확히 한 번 나타나야 하며 각 빈칸은 허용 답안을 하나 이상 가진다. 별도 segment 테이블은 만들지 않는다.

### 3.6 생성 후보 확정

외부 생성 응답은 바로 영속화할 문제가 아니라 후보 목록이다. 서버는 다음 순서로 최종 문제를 확정한다.

1. 후보의 외부 번호는 버리고 배열 위치만 임시 순서로 사용한다.
2. 공통 `prompt`, `topic`, `explanation`, `source_excerpt`와 유형별 상세 구조를 메모리에서 검증한다.
3. 검증을 통과한 후보만 원래 상대 순서대로 모은다.
4. 유효 후보에 `question_number=1..N`을 새로 부여한다.
5. 짧은 최종 트랜잭션에서 공통 문제·유형별 하위 행·QuizSet 상태를 함께 저장한다.

무효 후보에는 `quiz_questions`와 유형별 하위 행을 만들지 않는다. 일부 후보가 제외되어도 유효 후보가 하나 이상이면 `READY`, 하나도 없으면 `FAILED`다.

### 3.7 `quiz_attempts`

| 컬럼 | 의미 |
| --- | --- |
| `public_id` | 클라이언트가 생성한 UUID, 전역 unique |
| `attempt_type` | `MAIN` 또는 `REVIEW` |
| `source_attempt_id` | `REVIEW`가 가리키는 최초 `MAIN`; `MAIN`에서는 `null` |
| `status` | `IN_PROGRESS`, `SELF_ASSESSMENT_REQUIRED`, `COMPLETED` |
| `submitted_at` | 전체 답안 제출 시각 |
| `completed_at` | 자기평가까지 끝난 완료 시각 |

저장된 문항 결과에서 점수를 계산하므로 자동 정답 수·채점 수 캐시 컬럼과 summary revision은 두지 않는다.

### 3.8 `quiz_attempt_questions`

| 컬럼 | 의미 |
| --- | --- |
| `attempt_id`, `question_id` | 회차와 원본 문제 연결 |
| `source_attempt_question_id` | `REVIEW` 문항이 가리키는 원본 `MAIN` 문항 |
| `sequence_number` | 해당 회차 안의 표시 순서 |
| `automatic_grading_result` | 최초 자동 판정 `CORRECT|INCORRECT`, 서술형·미채점은 `null` |
| `final_grading_result` | 현재 판정 `CORRECT|PARTIAL|INCORRECT` |
| `grading_method` | `AUTOMATIC|USER_OVERRIDE|SELF_ASSESSMENT` |
| `review_resolved_at` | 원본 `MAIN` 문항이 복습으로 해결된 시각 |

이 행은 제출 전 복습 문항 snapshot도 표현하므로 `quiz_question_results`보다 `quiz_attempt_questions`라는 이름을 사용한다.

정상 채점 조합은 서비스가 검증한다.

- `AUTOMATIC`: `automatic_grading_result`와 `final_grading_result`가 존재하고 같다.
- `USER_OVERRIDE`: 최초 자동 판정은 유지하고 최종 판정만 교체한다.
- `SELF_ASSESSMENT`: 자동 판정은 `null`이고 최종 판정이 존재한다.
- 제출 전: 세 채점 컬럼이 모두 `null`이다.

### 3.9 `quiz_submitted_answers`

| 컬럼 | 의미 |
| --- | --- |
| `attempt_question_id` | 답안을 제출한 회차 문항 |
| `selected_choice_id` | 객관식 선택 `quiz_question_choices.id`; 그 외 유형은 `null` |
| `blank_id` | 빈칸형 입력 `quiz_fill_in_the_blanks.id`; 그 외 유형은 `null` |
| `answer_value` | 빈칸·단답형·서술형 제출 원문; 객관식은 `null` |

객관식은 `selected_choice_id`만 가진 한 행, 빈칸형은 작성한 빈칸마다 `blank_id + answer_value` 한 행, 단답형과 서술형은 `answer_value`만 가진 한 행을 저장한다. 미응답은 0행이다. 사용자 원문은 채점용 문자열로 덮어쓰지 않고 정규화가 필요하면 채점 시 메모리에서 계산한다.

DB는 다음 세 가지 행 모양만 허용하는 CHECK로 기본 구조를 보조한다.

- 객관식: `selected_choice_id`만 존재
- 빈칸형: `blank_id`, `answer_value`만 존재
- 단답형·서술형: `answer_value`만 존재

`(attempt_question_id, blank_id)`는 unique로 두어 같은 빈칸의 중복 제출을 막는다. 선택지·빈칸이 해당 회차 문항의 원본 문제에 속하는지, 단일 선택 객관식과 텍스트 답안이 문항당 최대 한 행인지와 문제 유형별 컬럼 조합은 제출 서비스가 검증한다.

## 4. 본 퀴즈 처리

1. Access Token의 사용자 기준으로 `READY` QuizSet과 문제 소유권을 확인한다.
2. path의 UUID attempt가 이미 존재하면 사용자·QuizSet 일치를 확인하고 최초 확정 결과를 반환한다.
3. 새 UUID이면 `selectedChoiceId`, `blankAnswers[{blankId, answer}]`, `text`를 문제 유형에 맞게 검증한다.
4. `MAIN` attempt, 모든 회차 문항과 제출 답안을 생성한다.
5. 객관식·빈칸·단답형을 자동 채점하고 최초·최종 판정과 `AUTOMATIC` 방식을 저장한다.
6. 작성한 서술형이 있으면 `SELF_ASSESSMENT_REQUIRED`, 없으면 `COMPLETED`로 확정한다.
7. 위 저장을 하나의 트랜잭션에서 commit한다.

유형별 정답 원장은 제출·채점 서비스 내부에서만 조회한다. 풀이 전 DTO에는 `is_correct`, 허용 답안, 모범 답안, 핵심 포인트, 내부 정규화 값, 해설과 원문 근거를 넣지 않는다.

## 5. 텍스트 자동 채점 정규화와 사용자 수정

단답형과 빈칸형은 각 허용 답안의 `normalized_value`와 다음 규칙으로 정규화한 사용자 제출 원문을 완전 일치 비교한다.

1. Unicode NFC
2. Unicode 공백을 ASCII 공백으로 바꾸고 연속 공백 축약
3. 앞뒤 공백 제거
4. `toLowerCase(Locale.ROOT)`

구두점 제거, 번역, 약어 확장, 오타·의미 유사도 보정과 LLM 판정은 하지 않는다.

답을 하나 이상 제출한 단답형·빈칸형의 사용자 판정 수정은 최초 `automatic_grading_result`를 바꾸지 않고 `final_grading_result`와 `grading_method=USER_OVERRIDE`만 갱신한다. 완전 미응답은 수정할 수 없으며 전체 수정 이력 테이블은 만들지 않는다. 공통 판정 수정 서비스가 두 유형을 처리하고, 단답형 전용 기존 API 경로는 호환 별칭으로 같은 서비스에 위임한다.

## 6. 복습 처리와 동시성

### 6.1 시작·재진입

1. 최신 완료 `MAIN` attempt를 현재 사용자 소유 쓰기 잠금으로 조회한다.
2. 같은 사용자·원본 MAIN에 미완료 `REVIEW`가 있으면 새로 만들지 않고 반환한다.
3. 없으면 원본 문항 중 `final_grading_result IN (INCORRECT, PARTIAL)`이고 `review_resolved_at IS NULL`인 후보를 고른다.
4. `REVIEW` attempt를 만들고 대상마다 `source_attempt_question_id`를 가진 회차 문항을 생성한다.
5. commit 뒤 원본 MAIN 잠금을 해제한다.

화면의 중복 클릭 방지는 사용자 경험을 위한 보조 장치다. 실제 중복 방지는 짧은 원본 MAIN 비관적 잠금 구간에서 보장한다. 성능용 보조 인덱스는 후속 측정 전까지 추가하지 않는다.

### 6.2 제출·채점

1. `REVIEW` attempt와 source `MAIN`을 같은 소유권 연결로 확인한다.
2. 모든 snapshot 문항의 최종 답안을 한 번 받는다.
3. 제출 답안을 저장하고 본 퀴즈와 같은 채점기를 사용한다.
4. 복습 문항이 `CORRECT`면 연결된 원본 문항의 `review_resolved_at`을 기록한다.
5. `PARTIAL|INCORRECT`면 원본 해결 시각을 비워 다음 복습 후보로 남긴다.
6. 서술형 자기평가가 남으면 `SELF_ASSESSMENT_REQUIRED`, 아니면 `COMPLETED`로 바꾼다.
7. 제출·채점·원본 해결 갱신을 하나의 트랜잭션에서 commit한다.

복습 성공 뒤에도 원본 `MAIN.final_grading_result`는 바꾸지 않는다. 원래 결과와 복습 해결 여부를 함께 보존한다.

## 7. 소유권과 데이터 노출 경계

- 개별 ID 존재 여부가 아니라 사용자 → QuizSet → attempt → 회차 문항 전체 연결을 확인한다.
- `REVIEW.source_attempt_id`는 같은 사용자의 최초 `MAIN`이어야 한다.
- `REVIEW.source_attempt_question_id`는 source MAIN에 속하고 양쪽 `question_id`가 같아야 한다.
- 타인 소유 또는 존재하지 않는 연결은 동일한 `404 COMMON_003`으로 처리한다.
- 풀이 전 응답은 공개 필드 allowlist를 사용하고 영속 entity를 직접 직렬화하지 않는다.
- 결과 응답은 객관식 정답 선택지, 단답형 대표 답안, 빈칸별 대표 답안, 서술형 모범 답안·핵심 포인트만 공개하며 전체 허용 답안과 `normalized_value`는 포함하지 않는다.

## 8. DB 제약과 서비스 검증

DB는 PK, FK, public ID unique, 회차 안의 문항·순서 unique와 enum 범위만 보장한다. 다음 교차 행 규칙은 서비스 트랜잭션이 검증한다.

- `REVIEW.source_attempt_id`가 실제 `MAIN`인지
- source MAIN과 REVIEW의 사용자·QuizSet이 같은지
- 원본·복습 회차 문항의 `question_id`가 같은지
- 문제 유형별 원장 행 수와 제출 컬럼 조합
- 선택지와 빈칸이 제출 대상 문제에 속하는지
- 객관식 보기 3~5개와 단일 정답, 단답형·빈칸형 허용 답안 존재 여부
- 유효 후보만 연속 `question_number=1..N`을 가지는지
- 빈칸 번호와 `prompt` 마커의 일대일 대응
- 채점 결과와 `grading_method` 조합
- `review_resolved_at`이 원본 MAIN 문항에만 기록되는지

목표 migration에는 별도 `CREATE INDEX`와 조회 성능 최적화용 보조 인덱스를 추가하지 않는다. PK·FK·unique 제약을 위해 DB가 내부적으로 만드는 인덱스는 데이터 무결성의 일부다.

## 9. migration과 구현 전환

이번 PR은 아직 병합되지 않았으므로 보정 migration을 만들지 않고 `V5`를 목표 구조로 직접 갱신한다. 이미 이전 V5를 적용한 개인 개발 DB는 개발 데이터를 초기화한 뒤 다시 적용한다.

후속 Java 구현에서는 다음 기존 모델을 새 SQL에 맞춰 제거·교체해야 한다.

- `QuizQuestion.representativeAnswer`
- `quiz_short_answer_accepted_answers` 매핑
- 공통 `QuizQuestionAnswer`와 `quiz_question_answers`
- `QuizQuestionResult`와 `submittedAnswer`
- `ReviewSession`, `ReviewSessionQuestion`
- attempt의 자동 채점 count 캐시

새 구현은 `QuizQuestionChoice`, `QuizShortAnswerAnswer`, `QuizEssayAnswerGuide`, `QuizFillInTheBlank`, `QuizFillInTheBlankAnswer`, `QuizAttemptQuestion`, `QuizSubmittedAnswer`와 `MAIN|REVIEW` attempt를 사용한다. 클래스 이름은 구현 시 기존 패키지 관례에 맞춰 다듬을 수 있지만 위 물리 테이블·컬럼 의미를 바꾸면 안 된다.

구현 순서는 다음과 같다.

1. 목표 migration과 유형별 무결성 테스트를 먼저 작성한다. 객관식 보기 3·4·5개 성공, 2·6개 실패와 무효 후보 제거 뒤 연속 번호 재부여를 포함한다.
2. 유형별 entity·repository를 추가하고 기존 공통 정답 매핑을 제거한다.
3. 생성 결과 검증과 `READY|FAILED`, `failure_code` 확정을 구현한다.
4. 제출 저장과 자동 채점을 새 FK·답안 원장에 연결한다.
5. 풀이 전·결과 DTO projection을 API 계약에 맞춘다.
6. 기존 본 퀴즈·복습 회귀 테스트와 서버 전체 테스트를 실행한다.

## 10. 남은 범위

- Java entity·repository·service·테스트와 migration을 이 목표 모델에 동기화해야 한다.
- 실제 조회 패턴을 측정한 뒤 필요한 성능 인덱스를 검토한다.
