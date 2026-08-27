---
document_type: data-contract
status: review
scope: shared
---

# [Data Contract] 학습자료와 퀴즈

- 소유 영역: 서버 학습자료·퀴즈·복습 도메인
- 관련 기능명세: [학습자료 만들기](../prd/prd-content-import.md), [퀴즈 생성·풀이·결과·복습](../prd/prd-quiz-learning.md)
- 관련 API: [학습자료·퀴즈·복습 API](contract-api-quiz-learning.md)
- 물리 구조 그림: [퀴즈·복습 ERD](../assets/quiz-erd.svg)

## 목적과 경계

학습자료와 원문 출처, 문제 유형별 정의·정답 원장, 본 퀴즈와 복습 회차, 사용자 제출 답안과 채점 결과가 웹·앱·서버에서 같은 의미로 사용되도록 소유권과 생명주기를 정의한다. 물리 테이블과 컬럼의 최종 구현은 서버 TRD와 migration이 책임지며 이 계약의 의미를 위반할 수 없다.

이 문서의 snake_case 이름은 현재 서버의 `quiz_*`, `question_id`, `answer_value`, `normalized_value` 관례에 맞춘 구현 기준 이름이다. V5 migration은 이 목표 물리 구조를 반영했으며 Java 구현은 후속 동기화 대상이다.

## 공유 개념

- **문제 세트와 문제:** 한 번 생성된 문제 세트, 문제, 정답 기준과 원문 근거는 불변이다. 다시 생성할 때는 기존 문제를 수정하지 않고 새 문제 세트를 만든다.
- **문제 유형별 원장:** 공통 문제 행에는 모든 유형이 공유하는 정보만 두고 객관식 보기, 단답형 허용 답안, 서술형 답안 가이드, 빈칸과 빈칸별 허용 답안은 서로 다른 원장에 보존한다.
- **풀이 회차(attempt):** 본 퀴즈와 복습을 공통으로 나타내는 사용자 소유 실행 단위다. `MAIN`은 원본 회차이고 `REVIEW`는 최초 `MAIN`을 원본으로 하는 재풀이 회차다.
- **회차 문항:** 한 회차에 포함된 문제를 고정한다. 복습을 시작할 때 대상 문제를 먼저 저장하므로 제출·채점 전에도 존재할 수 있다.
- **제출 답안:** 사용자가 실제 제출한 선택지 식별자 또는 원문이다. 한 문항에 답안 행이 없으면 미응답이고, 빈칸형은 작성한 빈칸마다 한 행을 가진다.
- **채점 결과:** 서버 최초 자동 채점과 현재 최종 판정을 구분한다. 복습은 원본 회차의 최초 제출·판정을 바꾸지 않고 해결 시점만 기록한다.

HTTP 필드 모양과 공개 오류는 [학습자료·퀴즈·복습 API](contract-api-quiz-learning.md)가 책임진다. 공개 `reviewSession` 리소스는 저장 모델에서 `attemptType=REVIEW`인 attempt이며 별도 복습 세션 원장을 만들지 않는다.

## 문제와 유형별 원장

문제 유형의 서버 기준 값은 `MULTIPLE_CHOICE`, `FILL_IN_THE_BLANK`, `SHORT_ANSWER`, `ESSAY`다. 공개 API도 같은 값을 사용한다. 기존 웹의 `FILL_BLANK`는 서버 기준 `FILL_IN_THE_BLANK`로 전환해야 한다.

### 공통 문제: `quiz_questions`

`quiz_questions`는 모든 문제 유형이 공유하는 다음 값만 가진다.

| 컬럼 | 의미 |
| --- | --- |
| `quiz_set_id` | 문제가 속한 불변 문제 세트 |
| `question_number` | 문제 세트 안의 표시 순서 |
| `question_type` | 서버 기준 문제 유형 |
| `topic` | 결과와 복습에서 사용할 간결한 주제명 |
| `prompt` | 일반 텍스트 문제 본문. 빈칸형은 `[1]`, `[2]` 마커를 포함 |
| `explanation` | 제출·채점 뒤 공개할 해설 |
| `source_excerpt` | 학습자료에서 가져온 근거 문구 |

`status`는 기존 `quiz_sets`에 있고 `topic`, `explanation`, `source_excerpt`는 기존 `quiz_questions`에 있으므로 유형별 테이블에 복제하지 않는다.

### 객관식 보기: `quiz_question_choices`

| 컬럼 | 의미 |
| --- | --- |
| `public_id` | 공개 `choiceId`로 사용하는 불변 식별자 |
| `question_id` | `MULTIPLE_CHOICE` 문제 |
| `choice_value` | 사용자에게 보여줄 보기 문구 |
| `is_correct` | 서버 채점에만 사용하는 정답 여부 |

- MVP 객관식은 보기 3개 이상 5개 이하와 `is_correct=true`인 보기 정확히 하나를 가진다.
- 보기와 문제 세트는 불변이므로 별도 순서 컬럼을 두지 않는다. 서버가 배열을 만들 때는 생성·저장 순서가 흔들리지 않도록 내부 `id` 오름차순을 사용한다.
- 풀이 전 응답은 `public_id`, `choice_value`를 각각 `choiceId`, `text`로 전달하고 `is_correct`는 노출하지 않는다.
- 기존 `quiz_question_answers`에 정답 보기만 저장하지 않는다. 오답 보기도 문제 구성의 일부이므로 모든 보기를 이 원장에 저장한다.

### 단답형 허용 답안: `quiz_short_answer_answers`

| 컬럼 | 의미 |
| --- | --- |
| `question_id` | `SHORT_ANSWER` 문제 |
| `answer_value` | 결과 화면에 사용할 허용 답안 원문 |
| `normalized_value` | 자동 채점에만 사용하는 비교값 |

- 이 테이블의 모든 행은 허용 답안이므로 `answer_role`을 두지 않는다.
- 한 문제는 허용 답안을 하나 이상 가진다. 결과 화면의 `representativeAnswer`는 내부 `id`가 가장 작은 답안의 `answer_value`를 사용한다.
- 정규화 뒤 빈 문자열이 되거나 같은 `normalized_value`가 되는 중복 답안은 문제 확정 전에 제거하거나 거절한다.
- 채점은 사용자 원문을 같은 규칙으로 정규화한 값이 허용 답안 집합에 포함되는지 순서와 무관하게 비교한다.

### 서술형 답안 가이드: `quiz_essay_answer_guides`

| 컬럼 | 의미 |
| --- | --- |
| `question_id` | `ESSAY` 문제와 일대일 연결하며 이 테이블의 PK로 사용 |
| `model_answer` | 자기평가와 결과에서 보여줄 모범 답안 |
| `key_points` | 자기평가 기준이 되는 문자열 목록. MySQL JSON 배열로 저장 |

서술형의 `model_answer`와 `key_points`는 풀이 전에는 공개하지 않는다. `explanation`과 `source_excerpt`는 공통 문제 원장의 값을 사용한다.

### 빈칸 정의와 허용 답안

빈칸형은 공통 `prompt`에 `[1]`, `[2]` 마커를 넣고 별도 segment 원장을 만들지 않는다.

`quiz_fill_in_the_blanks`:

| 컬럼 | 의미 |
| --- | --- |
| `public_id` | 공개 `blankId`로 사용하는 불변 식별자 |
| `question_id` | `FILL_IN_THE_BLANK` 문제 |
| `blank_number` | `prompt`의 `[n]`과 연결되는 1부터 시작하는 번호 |

`quiz_fill_in_the_blank_answers`:

| 컬럼 | 의미 |
| --- | --- |
| `blank_id` | 답안이 속한 `quiz_fill_in_the_blanks.id` |
| `answer_value` | 빈칸에서 인정할 답안 원문 |
| `normalized_value` | 자동 채점에만 사용하는 비교값 |

- 한 문제는 빈칸 1개 또는 2개를 가지며 `blank_number`는 1부터 빠짐없이 이어진다.
- 각 `[n]` 마커는 `prompt`에 정확히 한 번 나오고 같은 번호의 빈칸 행과 일대일로 대응해야 한다.
- 각 빈칸은 허용 답안을 하나 이상 가진다. 대표 답안은 해당 빈칸에서 내부 `id`가 가장 작은 답안이다.
- 모든 빈칸이 각자의 허용 답안 중 하나와 일치해야 문항 전체가 `CORRECT`다. 답이 없거나 하나라도 다르면 문항 전체가 `INCORRECT`다.

### 공통 정답 테이블 폐기

기존 `quiz_question_answers(answer_value, normalized_value, answer_role)`는 목표 구조의 원장이 아니다. 유형별 모양과 검증 규칙이 다르므로 migration과 Java 구현을 동기화할 때 제거하고 위 네 유형별 원장으로 전환한다.

단답형과 빈칸형의 정답 원문 및 사용자 제출 원문은 그대로 보존한다. 문제 저장 시 `normalized_value`를 계산하고, 채점 시 사용자 제출 원문을 같은 규칙으로 메모리에서 정규화한다. 내부 `normalized_value`와 전체 허용 답안 목록은 클라이언트에 공개하지 않는다.

## 문제 세트 생성 결과

`quiz_sets.status`는 `GENERATING`, `READY`, `FAILED`다.

- 유형별 검증을 통과해 저장할 수 있는 문제가 하나 이상이면 `READY`다. 최대 문제 수보다 적거나 요청 유형 일부가 없어도 실패가 아니다.
- 유효 문제가 0개면 `FAILED`이며 빈 문제 세트를 풀이 대상으로 공개하지 않는다.
- 실패 뒤 재조회에서도 원인을 구분해야 하므로 `quiz_sets.failure_code`를 nullable 값으로 둔다. `FAILED`에서는 `SOURCE_INSUFFICIENT` 또는 `GENERATION_FAILED`, 그 외 상태에서는 `null`이다.
- 공개 `message`와 `retryable`은 `failure_code`에 대한 서버 정책으로 계산하며 별도 컬럼으로 복제하지 않는다.
- 선택 유형·난이도·최대 문제 수는 생성 요청 입력이며 서버 DB에 영속화하지 않는다. `requestedConfig`는 생성 접수 성공 응답에서 요청값을 echo할 때만 사용하고 이후 상태 조회에서는 반환하지 않는다.
- 프론트는 `userId + quizSetId` 범위로 `selectedTypes`, `difficulty`, `maxQuestionCount`를 기기 로컬 상태에 보존할 수 있다. 이 값은 손실 가능한 화면 표시용 캐시이며 QuizSet 상태·문제 유효성·채점의 원장이 아니다.
- 로컬 값이 없으면 `GENERATING`은 서버 상태만으로 표시하고, `READY`의 실제 문제 수와 포함 유형은 확정된 `quiz_questions`에서 계산하며, `FAILED`는 저장된 `failure_code`에 따른 일반 안내를 사용한다.

문제 하나를 유효하다고 확정하는 최소 조건은 다음과 같다.

| 유형 | 유형별 필수 조건 |
| --- | --- |
| `MULTIPLE_CHOICE` | 보기 3개 이상 5개 이하, 공개 식별자·문구 존재, 정답 보기 정확히 1개 |
| `FILL_IN_THE_BLANK` | 빈칸 1개 또는 2개, `[1]..[N]` 마커가 각각 정확히 한 번 존재, 빈칸 번호와 마커 일치, 빈칸마다 허용 답안 1개 이상 |
| `SHORT_ANSWER` | 정규화 뒤 비어 있지 않은 허용 답안 1개 이상 |
| `ESSAY` | 비어 있지 않은 `model_answer`와 `key_points` 1개 이상 |

공통 `prompt`, `topic`, `explanation`, `source_excerpt`도 비어 있지 않아야 한다. 외부 생성 결과는 후보 목록이며 후보가 보낸 문제 번호는 저장값으로 사용하지 않는다.

서버는 원래 후보 배열 순서대로 공통 정보와 유형별 상세 구조를 검증한다. 검증을 통과한 후보만 같은 상대 순서로 모은 뒤 최종 `question_number=1..N`을 부여한다. 검증에서 제외된 후보에는 `quiz_questions`를 포함해 어떤 영속 행도 만들지 않는다. 제외된 후보가 있어도 유효 문제 하나 이상을 최종 저장하면 QuizSet은 `READY`이고, 유효 후보가 없으면 `FAILED`다.

## 본 퀴즈와 복습 attempt

### 공통 상태

- `attemptId`: 클라이언트가 안전한 난수원으로 생성한 UUID다. 서버는 원문 UUID를 전역 unique 식별자로 저장하며 hash나 payload fingerprint를 만들지 않는다.
- `attemptType`: `MAIN` 또는 `REVIEW`다.
- `sourceAttemptId`: `MAIN`에서는 없고 `REVIEW`에서는 최초 `MAIN` attempt를 가리킨다. 복습끼리 연결하는 체인을 만들지 않는다.
- `status`: `IN_PROGRESS`, `SELF_ASSESSMENT_REQUIRED`, `COMPLETED`다.

본 퀴즈는 최종 제출 시 attempt와 회차 문항·제출 답안을 한 트랜잭션에서 확정한다. 복습은 시작 시 `IN_PROGRESS` attempt와 대상 회차 문항을 먼저 생성해 문항 snapshot을 고정하고, 모든 문항을 푼 뒤 한 번 제출·채점한다.

같은 사용자와 원본 `MAIN`에 미완료 `REVIEW`는 하나만 허용한다. 서버는 원본 `MAIN` attempt를 짧게 쓰기 잠금한 뒤 기존 미완료 복습 조회와 생성을 같은 트랜잭션에서 수행한다. 화면의 중복 클릭 방지는 이 서버 규칙을 대신하지 않는다.

### 회차 문항 연결

- `(attemptId, questionId)`는 한 번만 존재한다.
- `MAIN` 회차 문항은 `sourceAttemptQuestionId`가 없다.
- `REVIEW` 회차 문항은 최초 `MAIN`의 원본 회차 문항을 `sourceAttemptQuestionId`로 가리킨다.
- `REVIEW.sourceAttemptId`, 원본 회차 문항의 attempt와 양쪽 `questionId`가 모두 일치해야 한다.
- 이 연결 검증은 FK 존재 여부만으로 충분하지 않으므로 소유권 검증과 함께 서비스 트랜잭션에서 보장한다.

## 제출 답안

사용자 제출은 정답 원장과 별도 `quiz_submitted_answers` 행으로 저장한다. 목표 컬럼은 다음과 같다.

| 컬럼 | 의미 |
| --- | --- |
| `attempt_question_id` | 답안을 제출한 회차 문항 |
| `selected_choice_id` | 객관식에서 선택한 `quiz_question_choices.id`; 그 외 유형은 `null` |
| `blank_id` | 빈칸형에서 답한 `quiz_fill_in_the_blanks.id`; 그 외 유형은 `null` |
| `answer_value` | 빈칸·단답형·서술형 사용자 원문; 객관식은 `null` |

| 문제 유형 | 제출 답안 행 |
| --- | --- |
| 객관식 단일 선택 | `selected_choice_id`만 가진 한 행 |
| 빈칸형 | `blank_id`, `answer_value`를 가진 작성 빈칸별 한 행 |
| 단답형 | `answer_value`만 가진 한 행 |
| 서술형 | `answer_value`만 가진 한 행 |
| 미응답 | 0행 |

객관식은 보기 문구를 제출 답안에 복제하지 않고 선택지 FK만 보존한다. 사용자 제출 원문은 정규화 값으로 교체하지 않는다.

DB는 `selected_choice_id`와 `answer_value`가 동시에 존재하지 않는 기본 shape, `(attempt_question_id, selected_choice_id)` 및 `(attempt_question_id, blank_id)` 중복 방지를 보조할 수 있다. 다음 교차 원장 규칙은 제출 서비스가 검증한다.

- `selected_choice_id`가 해당 `attempt_question_id`의 객관식 문제에 속하는지
- `blank_id`가 해당 회차의 빈칸형 문제에 속하는지
- 객관식은 선택 행이 최대 하나인지
- 단답형·서술형은 원문 행이 최대 하나인지
- 빈칸형은 같은 `blank_id`가 중복되지 않는지
- 요청 DTO는 서버의 기존 이름인 `selectedChoiceId`, `blankAnswers: [{ blankId, answer }]`, `text`를 사용하는지

MVP는 이 다형 제출 원장을 유지한다. cross-question choice·blank 제출과 객관식·단답형 중복 제출은 기존 제출 트랜잭션 안에서 문제 범위 조회와 입력 검증으로 거절한다. 이를 위해 복합 FK, 유형별 제출 테이블, 전용 추가 락이나 별도 중복 방지 원장을 새로 도입하지 않는다. 이미 정의된 PK·FK·CHECK·unique 제약과 일반 트랜잭션 경계는 그대로 사용한다.

## 채점 결과

회차 문항은 다음 채점 값을 가진다.

| 값 | 의미 |
| --- | --- |
| `automaticGradingResult` | 서버 최초 자동 판정. 객관식·빈칸·단답형에서 `CORRECT|INCORRECT`, 서술형에서는 `null` |
| `finalGradingResult` | 결과·복습 대상 계산에 사용하는 현재 최종 판정. `CORRECT|PARTIAL|INCORRECT` |
| `gradingMethod` | 최종 판정이 결정된 방식. `AUTOMATIC|USER_OVERRIDE|SELF_ASSESSMENT` |
| `reviewResolvedAt` | 원본 `MAIN` 오답이 복습에서 해결된 시각. 해결 전에는 `null` |

`gradingMethod`는 어떤 결과 컬럼을 선택하는 플래그가 아니다. 서비스와 조회는 항상 `finalGradingResult`를 현재 판정으로 사용하고, `gradingMethod`는 그 판정의 출처만 설명한다.

정상 조합은 다음과 같다.

- `AUTOMATIC`: 자동 판정과 최종 판정이 모두 있고 동일하다.
- `USER_OVERRIDE`: 자동 판정은 불변으로 남고 최종 판정만 사용자가 정한 값이다.
- `SELF_ASSESSMENT`: 자동 판정은 없고 최종 판정은 사용자 자기평가 결과다.
- 제출·채점 전: 세 값이 모두 없다.

MVP에서는 전체 사용자 수정 이력을 별도 테이블로 보존하지 않는다. 단답형·빈칸형 사용자 수정은 해당 사용자·attempt·문항의 `finalGradingResult`와 `gradingMethod`만 바꾸며 정답 원장이나 다른 attempt에 전파하지 않는다. 빈칸형은 제출 답안 행이 하나 이상 있을 때만 수정할 수 있다.

## 복습 대상과 해결

복습 후보는 최초 `MAIN` 회차 문항 중 현재 최종 판정이 `INCORRECT` 또는 `PARTIAL`이고 `reviewResolvedAt`이 없는 문항이다. 복습 attempt가 생기면 선택한 후보를 `REVIEW` 회차 문항으로 저장해 활성 복습의 문제 집합을 고정한다.

복습 문항을 맞히면 `REVIEW` 회차 문항의 `finalGradingResult=CORRECT`를 저장하고 원본 `MAIN` 회차 문항의 `reviewResolvedAt`을 같은 트랜잭션에서 기록한다. 원본의 `finalGradingResult`는 바꾸지 않아 “처음에는 틀렸지만 복습으로 해결함”을 보존한다. 다시 틀리거나 부분 정답이면 원본 `reviewResolvedAt`을 비워 두어 다음 복습 후보로 남긴다.

## 트랜잭션과 불변성

- 본 퀴즈 제출은 attempt, 회차 문항, 제출 답안과 채점 결과를 한 트랜잭션에서 저장한다.
- 복습 시작은 원본 `MAIN` 잠금, 미완료 `REVIEW` 확인과 snapshot 생성을 한 트랜잭션에서 처리한다.
- 복습 제출은 제출 답안, 복습 채점과 원본 `reviewResolvedAt` 갱신을 한 트랜잭션에서 처리한다.
- 같은 UUID 재요청은 먼저 확정된 attempt를 반환하고 request hash·fingerprint·replay 테이블을 만들지 않는다.
- 문제 생성은 외부 후보의 공통 정보와 해당 유형의 상세 구조를 먼저 검증하고, 유효 후보에 서버가 연속 `question_number`를 부여한 뒤 공통·상세 원장을 함께 저장한다. 무효 후보와 유형 상세 저장이 실패한 문제는 부분 문제로 남기지 않는다.
- 유효 문제가 하나 이상이면 해당 문제들과 `quiz_sets.status=READY`, `failure_code=null`을 확정한다. 유효 문제가 없으면 문제 행을 공개 가능한 상태로 남기지 않고 `status=FAILED`와 `failure_code`를 확정한다.
- 동일 QuizSet에 생성 worker가 중복 실행되는 상황을 위해 별도 worker lease, 전용 추가 락이나 새 원장을 MVP에 도입하지 않는다. 생성 저장은 기존 상태 확인과 일반 트랜잭션·unique 제약을 사용하며 이 결정이 생성 성공 판정 규칙을 바꾸지는 않는다.
- 한 번 attempt가 연결된 문제 세트·문제·보기·빈칸·정답 가이드는 수정하거나 물리 삭제하지 않는다.

## 생명주기

- 객관식 보기, 단답형 허용 답안, 서술형 답안 가이드, 빈칸과 빈칸별 허용 답안은 문제와 같은 생명주기를 가진다.
- 회차 문항, 제출 답안과 채점 결과는 attempt와 같은 생명주기를 가진다.
- `REVIEW` attempt는 최초 `MAIN`을 참조하므로 원본을 먼저 물리 삭제하지 않는다.
- 향후 계정·학습자료 삭제 정책을 정할 때 본 퀴즈와 복습 이력도 함께 삭제·익명화하거나 보존 기간을 명시해야 한다.

## 구현 전환 기준

| 조치 | 대상 | 전환 기준 |
| --- | --- | --- |
| 유지·확장 | `quiz_sets` | 기존 식별자·소유권·`status`를 유지하고 nullable `failure_code` 추가 |
| 유지 | `quiz_questions` | `question_number`, `question_type`, `topic`, `prompt`, `explanation`, `source_excerpt` 유지 |
| 제거 | `quiz_question_answers` | 유형별 원장으로 대체하고 `answer_role` 사용 중단 |
| 추가 | `quiz_question_choices` | 모든 객관식 보기와 정답 여부 저장 |
| 추가 | `quiz_short_answer_answers` | 단답형 허용 답안 원문과 정규화 값 저장 |
| 추가 | `quiz_essay_answer_guides` | 서술형 모범 답안과 핵심 포인트 저장 |
| 추가 | `quiz_fill_in_the_blanks` | 공개 빈칸 식별자와 번호 저장 |
| 추가 | `quiz_fill_in_the_blank_answers` | 빈칸별 허용 답안 원문과 정규화 값 저장 |
| 유지·확장 | `quiz_submitted_answers` | `selected_choice_id`, `blank_id` 추가, `answer_value` nullable 전환 |
| 유지 | `quiz_attempts`, `quiz_attempt_questions` | MAIN·REVIEW 통합 회차와 채점 구조 유지 |

migration, Java entity·repository·service와 테스트는 같은 목표 구조를 사용해야 한다. 기존 서버 TRD나 migration에 `quiz_question_answers`가 정답 원장으로 남아 있다면 이 계약과 충돌하므로 구현 전에 함께 동기화한다. 공개 API의 `FILL_BLANK` 소비자는 계약 변경 뒤 `FILL_IN_THE_BLANK`로 전환한다.

이번 PR은 아직 병합되지 않았으므로 `V5__create_quiz_grading.sql`을 목표 구조에 맞게 직접 수정한다. 이전 V5를 적용한 개인 개발 DB는 개발 데이터를 초기화한 뒤 다시 적용한다.

## 확정·제안·열린 질문

### 확정

- 공통 문제와 네 유형별 원장을 분리하고 기존 `quiz_question_answers`와 `answer_role`을 제거한다.
- 객관식은 3~5개의 모든 보기를 저장하며 단일 정답 여부를 `is_correct`로 표현한다.
- 빈칸은 `prompt`의 번호 마커와 `blank_number`로 연결하며 별도 segment 원장은 만들지 않는다.
- 제출 답안은 `selected_choice_id`, `blank_id`, `answer_value`의 유형별 조합으로 보존한다.
- 문제 유형 서버 기준 값은 `FILL_IN_THE_BLANK`다.
- 외부 후보의 문제 번호는 버리고 유효 후보의 원래 배열 순서에 따라 서버가 `question_number=1..N`을 부여한다.
- 무효 후보는 `quiz_questions`와 유형별 하위 행을 모두 남기지 않는다.
- 유효 문제 1개 이상은 `READY`, 0개는 `FAILED`다.
- `requestedConfig`는 서버 DB에 저장하지 않고 생성 접수 성공 응답에서만 echo하며, 프론트가 `userId + quizSetId` 범위의 기기 로컬 상태로 보존한다.
- MVP는 별도 worker lease·전용 추가 락·복합 FK·유형별 제출 테이블을 추가하지 않는다.

### 제안

- 공개 식별자가 필요한 보기와 빈칸은 기존 서버 관례대로 내부 숫자 `id`와 UUID `public_id`를 분리한다.
- 별도 순서 컬럼이 없는 객관식 보기와 허용 답안의 대표값은 내부 `id` 오름차순으로 결정한다.
- 성능용 보조 인덱스는 실제 조회 패턴 측정 뒤 추가한다.

### 열린 질문

- 사용자 계정이나 학습자료를 삭제할 때 파생 문제 세트와 풀이 이력을 삭제·익명화·기간 보존 중 어떻게 처리할지
- 생성·채점 모델과 평가 근거를 어느 수준까지 추적해야 결과를 재현하고 감사할 수 있는지

위 열린 질문은 데이터 보존 범위를 바꾸므로 구현에서 임의로 확정하지 않는다.
