---
document_type: trd
status: review
scope: server
---

# [TRD · Server] 퀴즈·복습 통합 저장 모델

- 상태: 목표 SQL 반영, Java 구현 동기화 전
- 제품 정책: [퀴즈 생성·풀이·결과·복습 PRD](../../../docs/prd/prd-quiz-learning.md)
- 사용자 흐름: [퀴즈 생성부터 복습까지](../../../docs/ux/flow-quiz-solving.md)
- API 계약: [학습자료·퀴즈·복습 API 계약](../../../docs/contracts/contract-api-quiz-learning.md)
- 데이터 계약: [학습자료와 퀴즈 데이터 계약](../../../docs/contracts/contract-data-quiz-learning.md)

## 1. 목적과 범위

본 퀴즈와 복습을 서로 다른 저장 모델로 구현하지 않고 동일한 attempt·문항·제출·채점 구조로 처리한다. 복습은 별도 채점 시스템이 아니라 최초 `MAIN`에서 아직 해결하지 못한 문제만 담긴 `REVIEW` attempt다.

이번 단계는 공유 문서와 `V5__create_quiz_grading.sql`의 목표 모델만 갱신한다. 기존 Java entity, repository, service와 테스트의 동기화는 후속 구현 범위다. 객관식 보기·선택 모드와 빈칸 위치 등 화면 렌더링 메타정보도 후속 설계로 남긴다.

## 2. 핵심 결정

- `quiz_attempts`에 `attempt_type=MAIN|REVIEW`와 자기참조 `source_attempt_id`를 둔다.
- 모든 `REVIEW`는 최초 `MAIN`을 직접 가리키며 review-to-review 체인을 만들지 않는다.
- `quiz_review_sessions`, `quiz_review_session_questions`는 만들지 않는다.
- 한 회차의 문제 목록은 `quiz_attempt_questions`가 담당한다. 복습 시작 시 문항 행부터 생성해 snapshot을 고정한다.
- 정답 원장은 `quiz_question_answers`, 사용자 제출은 `quiz_submitted_answers`로 분리한다.
- 문제의 `representative_answer`와 단답형 전용 허용 답안 테이블을 제거한다.
- 최초 자동 판정과 현재 최종 판정을 `automatic_grading_result`, `final_grading_result`로 구분한다.
- `grading_method`는 최종 판정의 출처이며 조회 시 항상 `final_grading_result`를 사용한다.
- 복습 성공은 원본 `MAIN` 문항의 `review_resolved_at`으로 기록하고 원래 오답 판정은 바꾸지 않는다.
- UUID attempt 자체를 재시도 원장으로 사용하며 hash·fingerprint·replay 테이블은 만들지 않는다.
- 성능 개선용 보조 인덱스는 이번 migration에 추가하지 않는다.

## 3. 물리 데이터 모델

### 3.1 `quiz_questions`

| 컬럼 | 의미 |
| --- | --- |
| `question_type` | 문제 유형. 기존 모호한 `type` 이름을 명시적으로 변경 |
| `prompt` | 문제 본문 |
| `explanation` | 제출·채점 후 공개 가능한 해설 |
| `source_excerpt` | 결과 근거로 사용할 원문 발췌 |

문제 행에는 정답을 두지 않는다. 문제와 정답 원장은 문제 세트가 사용되기 시작한 뒤 수정하지 않는다.

### 3.2 `quiz_question_answers`

| 컬럼 | 의미 |
| --- | --- |
| `question_id` | 정답이 속한 문제 |
| `answer_value` | 화면 표시와 결과 설명에 사용하는 원문 |
| `normalized_value` | 단답형 비교 전용 값. 외부 비공개 |
| `answer_role` | `CORRECT`, `ACCEPTED`, `EXAMPLE` |

복수 선택 정답은 선택값마다 `CORRECT` 행을 둔다. 단답형은 대표 `CORRECT`와 추가 `ACCEPTED` 행을 사용하고, 서술형 예시 답안은 `EXAMPLE`로 저장한다.

문제 생성 서비스는 유형별 정답 개수를 검증하고, 단답형 정규화 뒤 중복되거나 빈 문자열이 되는 답안을 거절한다. 정답 원문은 정규화 결과로 덮어쓰지 않는다.

### 3.3 `quiz_attempts`

| 컬럼 | 의미 |
| --- | --- |
| `public_id` | 클라이언트가 생성한 UUID, 전역 unique |
| `attempt_type` | `MAIN` 또는 `REVIEW` |
| `source_attempt_id` | `REVIEW`가 가리키는 최초 `MAIN`; `MAIN`에서는 `null` |
| `status` | `IN_PROGRESS`, `SELF_ASSESSMENT_REQUIRED`, `COMPLETED` |
| `submitted_at` | 전체 답안 제출 시각 |
| `completed_at` | 자기평가까지 끝난 완료 시각 |

저장된 문항 결과에서 점수를 계산하므로 자동 정답 수·채점 수 캐시 컬럼과 summary revision은 두지 않는다.

### 3.4 `quiz_attempt_questions`

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

### 3.5 `quiz_submitted_answers`

| 컬럼 | 의미 |
| --- | --- |
| `attempt_question_id` | 답안을 제출한 회차 문항 |
| `answer_value` | 사용자가 제출한 원문 |

복수 선택은 선택값마다 한 행을 저장한다. 단답형과 서술형은 한 행, 미응답은 0행이다. 사용자 원문은 채점용 문자열로 덮어쓰지 않고 정규화가 필요하면 채점 시 메모리에서 계산한다.

## 4. 본 퀴즈 처리

1. Access Token의 사용자 기준으로 `READY` QuizSet과 문제 소유권을 확인한다.
2. path의 UUID attempt가 이미 존재하면 사용자·QuizSet 일치를 확인하고 최초 확정 결과를 반환한다.
3. 새 UUID이면 문제 유형별 제출 모양과 선택값·답안 개수를 검증한다.
4. `MAIN` attempt, 모든 회차 문항과 제출 답안을 생성한다.
5. 객관식·빈칸·단답형을 자동 채점하고 최초·최종 판정과 `AUTOMATIC` 방식을 저장한다.
6. 작성한 서술형이 있으면 `SELF_ASSESSMENT_REQUIRED`, 없으면 `COMPLETED`로 확정한다.
7. 위 저장을 하나의 트랜잭션에서 commit한다.

정답 원장은 제출·채점 서비스 내부에서만 조회한다. 풀이 전 DTO에는 정답, 허용 답안, 예시 답안, 내부 정규화 값을 넣지 않는다.

## 5. 단답형 정규화와 사용자 수정

단답형은 정답 원장의 `normalized_value`와 다음 규칙으로 정규화한 사용자 제출 원문을 완전 일치 비교한다.

1. Unicode NFC
2. Unicode 공백을 ASCII 공백으로 바꾸고 연속 공백 축약
3. 앞뒤 공백 제거
4. `toLowerCase(Locale.ROOT)`

구두점 제거, 번역, 약어 확장, 오타·의미 유사도 보정과 LLM 판정은 하지 않는다.

사용자 판정 수정은 최초 `automatic_grading_result`를 바꾸지 않고 `final_grading_result`와 `grading_method=USER_OVERRIDE`만 갱신한다. 전체 수정 이력 테이블은 만들지 않는다.

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
- 결과 응답에도 대표·예시 답안만 공개하며 전체 허용 답안과 `normalized_value`는 포함하지 않는다.

## 8. DB 제약과 서비스 검증

DB는 PK, FK, public ID unique, 회차 안의 문항·순서 unique와 enum 범위만 보장한다. 다음 교차 행 규칙은 서비스 트랜잭션이 검증한다.

- `REVIEW.source_attempt_id`가 실제 `MAIN`인지
- source MAIN과 REVIEW의 사용자·QuizSet이 같은지
- 원본·복습 회차 문항의 `question_id`가 같은지
- 문제 유형별 정답·제출 행 수와 역할 조합
- 채점 결과와 `grading_method` 조합
- `review_resolved_at`이 원본 MAIN 문항에만 기록되는지

이번 SQL에는 별도 `CREATE INDEX`와 조회 성능 최적화용 보조 인덱스를 추가하지 않는다. PK·FK·unique 제약을 위해 DB가 내부적으로 만드는 인덱스는 데이터 무결성의 일부다.

## 9. migration과 구현 전환

`V5`는 아직 `dev`에 병합되지 않은 신규 migration이므로 V6 보정 migration을 만들지 않고 V5를 직접 갱신한다. 이미 이전 V5를 적용한 개인 개발 DB는 개발 데이터를 초기화한 뒤 다시 적용한다.

후속 Java 구현에서는 다음 기존 모델을 새 SQL에 맞춰 제거·교체해야 한다.

- `QuizQuestion.representativeAnswer`
- `quiz_short_answer_accepted_answers` 매핑
- `QuizQuestionResult`와 `submittedAnswer`
- `ReviewSession`, `ReviewSessionQuestion`
- attempt의 자동 채점 count 캐시

새 구현은 `QuizQuestionAnswer`, `QuizAttemptQuestion`, `QuizSubmittedAnswer`와 `MAIN|REVIEW` attempt를 사용한다. SQL만 먼저 갱신한 현재 커밋에서는 기존 서버 애플리케이션과 테스트가 새 schema에 아직 맞지 않는 것이 의도된 후속 작업 상태다.

## 10. 후속 설계

- 객관식 보기 식별자·문구와 단일·복수 선택 모드
- 빈칸 위치 식별자와 빈칸별 정답 연결
- Java entity·repository·service·테스트의 새 schema 동기화
- 실제 조회 패턴을 측정한 뒤 필요한 성능 인덱스 검토
