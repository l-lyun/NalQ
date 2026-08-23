# 퀴즈 채점 서버 설계

- 상태: 초안 — 서버 설계 구체화, 미구현
- 제품 정책: [퀴즈 생성·풀이·결과·복습 기능명세](../../../docs/features/03-quiz-generation.md#채점-수정과-서술형-자기평가)
- 사용자 흐름: [퀴즈 생성부터 복습까지](../../../docs/flows/quiz-solving.md#d-결과)
- 공유 계약: [학습자료·퀴즈·복습 API 계약](../../../docs/contracts/api/quiz-learning.md#단답형-현재-판정-수정)

## 1. 문서 책임

이 문서는 단답형의 서버 자동 판정과 사용자 판정 수정에 필요한 서버 내부 모델, 트랜잭션, 동시성·멱등성 경계와 검증 기준을 정의한다. 수정 가능한 문제 유형은 기능명세가, 공개 HTTP 요청·응답과 오류 의미는 API 계약이 원장이다.

이번 변경은 설계 문서만 확정한다. 퀴즈 서버 도메인은 아직 구현돼 있지 않으므로 DB migration, Java 코드와 운영 프론트 연동은 후속 작업이다.

## 2. 채점 원칙

### 2.1 자동 판정

단답형 문제는 대표 답안과 하나 이상의 허용 답안을 서버에 가진다. 제출 시 사용자 답과 허용 답안 각각을 다음 순서로 정규화한 뒤 완전 일치 여부를 비교한다.

1. Unicode NFC 정규화
2. 앞뒤 공백 제거
3. 연속 Unicode whitespace를 ASCII 공백 하나로 축약
4. `toLowerCase(Locale.ROOT)` 소문자 변환

구두점 제거, 띄어쓰기 전체 제거, 번역, 약어 확장, 형태소 분석, 편집 거리와 임베딩·LLM 의미 유사도는 사용하지 않는다. 자동 채점은 같은 입력에 항상 같은 결과를 내고 외부 서비스 장애에 영향을 받지 않아야 한다.

| 허용 답안 | 사용자 답 | 자동 판정 | 이유 |
| --- | --- | --- | --- |
| `fifo` | `FIFO` | `CORRECT` | 대소문자만 다름 |
| `fifo` | ` fifo ` | `CORRECT` | 앞뒤 공백만 다름 |
| `fifo` | `선입선출` | `INCORRECT` | 번역·동의어 확장을 하지 않음 |
| `fifo` | `first in first out` | `INCORRECT` | 약어 확장을 하지 않음 |
| `fifo`, `선입선출`, `first in first out` | 세 표현 중 하나 | `CORRECT` | 생성 시 각각 허용 답안으로 저장됨 |

정규화한 허용 답안끼리 중복되면 문제 저장 시 하나로 축약한다. 빈 문자열이 되는 허용 답안은 문제 생성 검증 실패다. 채점 시 사용자 답 원문은 수정하지 않는다.

### 2.2 사용자 판정 수정

- 답을 작성한 `SHORT_ANSWER`만 `CORRECT|INCORRECT`로 수정할 수 있다.
- 객관식·빈칸·서술형과 미응답 단답형은 수정할 수 없다.
- 사용자 수정은 해당 사용자·attempt·question 결과에만 적용한다. 문제의 허용 답안과 다른 attempt에는 전파하지 않는다.
- 사용자는 최신 판정을 다시 수정할 수 있다. 최초 자동 판정으로 되돌아온 값도 사용자 수정 이력의 최신 값으로 취급한다.
- 전체 변경 이력 테이블은 MVP에 만들지 않는다. 최초 자동 판정과 최신 사용자 판정·수정 시각·revision만 보존한다.

## 3. 논리 상태 모델

정확한 테이블명은 퀴즈 도메인 구현 시 정하되 다음 의미는 분리해 저장한다.

### 문제 쪽

| 값 | 규칙 |
| --- | --- |
| 대표 답안 | 결과 설명에 노출할 한 개의 답안 |
| 허용 답안 목록 | 자동 판정에만 사용하는 1개 이상의 원문. 풀이 전과 결과 응답에서 전체 목록을 공개하지 않음 |

### attempt 문항 결과 쪽

| 값 | 규칙 |
| --- | --- |
| 제출 답안 | 최초 제출 원문을 불변 보존 |
| `automaticOutcome` | 제출 시 계산한 `CORRECT|INCORRECT`, 불변 |
| `userOverrideOutcome` | 사용자가 마지막으로 저장한 `CORRECT|INCORRECT`, 수정 전에는 `null` |
| 현재 `outcome` | `userOverrideOutcome != null`이면 그 값, 아니면 `automaticOutcome` |
| `gradingSource` | override가 없으면 `AUTOMATIC`, 있으면 `USER_OVERRIDE` |
| `gradingRevision` | 자동 판정 직후 `0`, 실제 사용자 수정 저장마다 1 증가 |
| `correctedAt` | 최신 사용자 수정 저장 시각, 수정 전에는 `null` |

JPA 낙관 잠금용 entity version과 공개 `gradingRevision`은 같은 의미로 재사용하지 않는다. entity version은 다른 필드 변경에도 증가할 수 있지만 공개 revision은 단답형 판정 수정의 동시성만 표현해야 한다.

### attempt 요약 쪽

| 값 | 규칙 |
| --- | --- |
| `summaryRevision` | attempt 생성 시 `0`, 단답형 판정·서술형 자기평가·복습 해결 상태처럼 공개 summary 값이 실제로 바뀔 때마다 1 증가 |

문항별 `gradingRevision`은 같은 문항의 수정 충돌을 감지하고, attempt의 `summaryRevision`은 서로 다른 단답형, 서술형 자기평가와 복습 해결까지 포함한 전체 요약 순서를 표현한다.

## 4. 서버 구성 경계

향후 `quiz` 도메인은 다음 책임으로 나눈다.

- Controller: 결과 조회와 단답형 판정 수정 HTTP 변환, 인증 사용자 전달
- Service: 소유권·attempt 상태·문항 유형·미응답 검증, 트랜잭션과 멱등·동시성 조정
- Repository: attempt 문항 결과 조건부 갱신, 결과 요약 조회와 멱등 결과 저장
- Domain: 현재 판정 계산, 수정 가능 여부와 revision 전이

자동 채점은 별도 외부 의존성이 없는 `ShortAnswerGrader`의 순수 동작으로 둔다. Java의 단순 정규화 함수만을 위해 주입 가능한 클래스를 추가하지 않고, grader 내부 package-private 함수로 정규화한다. 입력과 허용 답안 목록을 받아 판정하는 도메인 동작은 독립 단위 테스트 대상으로 유지한다.

## 5. 처리 흐름

### 5.1 최종 제출 자동 채점

1. 현재 사용자 소유 QuizSet과 모든 question을 조회한다.
2. 제출 payload를 문제 유형별로 검증하고 누락은 미응답으로 확정한다.
3. 답을 작성한 단답형을 `ShortAnswerGrader`로 판정한다.
4. 제출 답안, `automaticOutcome`, `gradingRevision=0`을 attempt 문항 결과에 저장한다.
5. 객관식·빈칸·단답형의 최초 자동 판정으로 제출 응답의 `automaticGrading`을 계산한다.
6. attempt와 모든 문항 결과, 제출 멱등 결과를 한 트랜잭션에서 확정한다.

### 5.2 단답형 판정 수정

1. Access Token 사용자 기준으로 attempt를 찾고 `SELECT ... FOR UPDATE` 성격의 쓰기 잠금을 획득한다. 없거나 타인 소유이면 동일하게 `404 COMMON_003`이다.
2. 잠금 안에서 `현재 사용자 + PUT + 정규화 path + Idempotency-Key`의 기존 처리 결과와 현재 `summaryRevision`을 확인한다.
3. question 소유권, attempt `COMPLETED`, 답을 작성한 `SHORT_ANSWER` 조건을 검증한다.
4. 요청 `expectedRevision`과 저장된 문항 `gradingRevision`을 비교한다.
5. 같은 현재 outcome 요청도 잠금 안에서 최신 상태를 다시 확인한 뒤 no-op 응답을 반환하고 두 revision을 올리지 않는다.
6. 다른 outcome이면 `WHERE grading_revision = :expectedRevision` 조건부 갱신으로 최신 override, 문항 revision과 수정 시각을 저장한다. 갱신 행이 0개면 `409 ATTEMPT_001`이다.
7. attempt의 `summaryRevision`을 1 증가시키고, 잠금 이후의 현재 read로 모든 문항 결과를 집계해 채점 점수와 원본 attempt의 `reviewQuestionCount`를 계산한다.
8. 변경된 문항 결과, 같은 `summaryRevision`의 summary와 멱등 결과를 저장하고 커밋한다.

클라이언트는 성공 응답 전까지 기존 판정과 summary를 유지한다. `409 ATTEMPT_001`이면 결과를 다시 조회한 뒤 최신 revision으로 새 요청을 만든다.

## 6. 트랜잭션과 계산

- 사용자 override 갱신과 summary 계산은 attempt 쓰기 잠금을 보유한 하나의 DB 트랜잭션에서 수행한다. 같은 attempt의 서로 다른 문항 수정도 이 잠금으로 직렬화한다.
- summary를 attempt 행에 캐시한다면 조건부 갱신과 같은 트랜잭션에서 함께 수정한다. MVP에서는 우선 문항 결과에서 집계해 중복 상태를 줄이는 방안을 권장한다.
- `scoredGrading.correctQuestionCount`는 객관식·빈칸의 자동 판정과 단답형 현재 outcome의 `CORRECT` 수다.
- `scoredGrading.gradedQuestionCount`는 객관식·빈칸·단답형 수이며 사용자 수정으로 바뀌지 않는다.
- `reviewQuestionCount`는 원본 attempt의 현재 판정과 복습 해결 상태를 기준으로 계산한다.
- 저장이나 집계 중 하나라도 실패하면 override, 문항 revision, summary revision, summary와 멱등 결과를 모두 rollback한다.
- 응답에는 공개 `summary.revision`을 포함한다. 클라이언트는 더 큰 revision을 이미 반영했다면 늦게 도착한 과거 summary를 화면에 적용하지 않고 결과를 다시 조회한다.
- 서술형 자기평가와 복습 판정도 summary 값에 영향을 주므로 source attempt 쓰기 잠금을 같은 순서로 먼저 획득한다. 복습 저장은 immutable한 `sourceAttemptId`를 조회한 뒤 `source attempt → review session` 순서로 잠가 교착 순서를 고정한다.
- `summaryRevision`은 summary 구성 값이 실제로 달라질 때만 증가한다. 복습 `UNRESOLVED`처럼 원본 `reviewQuestionCount`가 그대로인 저장은 증가시키지 않지만 응답에는 현재 revision을 반환한다.

## 7. 멱등성과 응답 유실

- `Idempotency-Key`는 필수이며 원문을 애플리케이션 로그에 남기지 않는다. 저장 시에는 digest와 요청 payload digest를 사용한다.
- 같은 키·같은 payload의 즉시 재시도는 최초 성공 응답을 반환하고 revision을 추가로 올리지 않는다.
- 같은 키에 다른 payload는 `409 ATTEMPT_001`이다.
- 동일 key의 저장 결과보다 더 최신 문항 `gradingRevision`이나 attempt `summaryRevision`이 이미 존재하면 과거 응답을 다시 적용하게 하지 않고 `409 ATTEMPT_001`을 반환해 결과 재조회를 유도한다.
- 단답형 수정 멱등 결과는 attempt가 보존되는 동안 유지한다. 정리 정책을 도입할 때도 일반적인 응답 유실 재시도 기간보다 먼저 삭제하지 않는다.

## 8. 복습 세션 경계

원본 attempt의 현재 `reviewQuestionCount`와 활성 복습 세션의 queue는 다른 상태다.

- 복습 세션 생성 시 당시 `reviewRequired=true` 문항을 snapshot한다.
- 생성된 활성 세션은 원본 단답형 판정이 이후 바뀌어도 문항을 중간 추가·삭제하지 않는다.
- 단답형 수정 응답과 최신 복습 현황의 `reviewQuestionCount`는 원본 attempt의 현재 후보 수를 반환한다.
- 활성 세션의 남은 문항 수는 복습 세션 조회 응답을 원장으로 삼는다.
- 수정된 판정의 복습 대상 여부는 다음 복습 세션 생성부터 반영한다.

이 선택은 진행 중인 세션에서 현재 문항이 갑자기 사라지거나 새 문항이 끼어드는 문제를 막는다. 두 수가 일시적으로 다를 수 있으므로 클라이언트가 합치거나 같은 값으로 가정하지 않는다.

## 9. 공개 오류 매핑

| 조건 | 응답 |
| --- | --- |
| outcome enum 누락·오류, 음수 revision, 멱등 키 형식 오류 | `400 COMMON_001` |
| JSON 파싱 실패 | `400 COMMON_002` |
| attempt/question 없음 또는 타인 소유 | `404 COMMON_003` |
| 완료 전 attempt, 수정 불가 유형, 미응답 단답형 | `409 ATTEMPT_001` |
| revision 충돌, 동일 멱등 키 payload 충돌 | `409 ATTEMPT_001` |
| 예상하지 못한 저장·집계 실패 | `500 COMMON_999` |

오류 응답은 사용자의 답, 허용 답안과 다른 사용자의 리소스 존재 여부를 노출하지 않는다.

## 10. 기술 검증 기준

### 자동 판정

- `fifo`, `FIFO`, 앞뒤 공백과 연속 공백 차이는 정의한 정규화 뒤 동일하게 판정된다.
- `fifo`만 허용 답안일 때 `선입선출`, `first in first out`은 자동 오답이다.
- 각 표현을 허용 답안으로 저장하면 각각 자동 정답이다.
- Unicode 조합형 차이는 NFC 뒤 동일하고 구두점·번역·약어는 임의로 같아지지 않는다.
- LLM, 네트워크와 시스템 기본 Locale 없이 같은 입력에 같은 결과를 낸다.

### 판정 수정

- 답을 작성한 단답형만 수정되고 제출 답안과 `automaticOutcome`은 변하지 않는다.
- 수정 성공 시 현재 outcome, revision, 채점 점수와 복습 수가 같은 트랜잭션 결과다.
- 다시 수정하면 최신 override가 현재 outcome이 되고 revision이 1 증가한다.
- 같은 현재 outcome 요청은 revision과 수정 시각을 바꾸지 않는다.
- 객관식·빈칸·서술형·미응답 단답형과 타인 리소스는 저장되지 않는다.

### 동시성·장애

- 같은 revision의 두 요청 중 하나만 조건부 갱신에 성공하고 다른 요청은 `ATTEMPT_001`이다.
- 같은 attempt의 서로 다른 단답형을 동시에 수정해도 attempt 잠금으로 직렬화되고, 두 번째 성공 응답의 `summaryRevision`과 집계에는 첫 번째 수정이 포함된다.
- 단답형 수정과 서술형 자기평가·복습 해결이 경합해도 source attempt 잠금으로 직렬화되고 모든 summary 변경이 단조 증가하는 `summaryRevision`에 반영된다.
- 응답 전달 순서가 뒤집혀도 낮은 `summary.revision`의 응답으로 최신 점수와 복습 수를 되돌리지 않는다.
- 같은 멱등 키 재요청은 중복 revision을 만들지 않는다.
- 저장 실패와 summary 집계 실패는 변경 전 판정·revision·요약을 유지한다.
- 활성 복습 세션은 판정 수정 뒤에도 snapshot이 변하지 않고 다음 세션만 최신 대상을 사용한다.

실제 구현에서는 도메인 단위 테스트, Service 트랜잭션 테스트, MVC 계약 테스트와 MySQL 조건부 갱신 통합 테스트를 최소 범위로 작성한다. 구현은 `server/AGENTS.md`의 테스트 우선 순서를 따른다.

## 11. 후속 구현 범위

- 퀴즈·문항·attempt·문항 결과·복습 세션 DB 모델과 Flyway migration
- 최종 제출 자동 채점과 결과 조회
- 단답형 판정 수정 Controller·Service·Repository와 멱등 저장소
- OpenAPI와 공개 오류 계약 테스트
- 프론트의 fixture callback을 실제 API 호출과 revision 기반 충돌 복구로 교체

최초 자동 판정과 최신 override만 보존하는 선택, 활성 복습 세션 snapshot을 유지하는 선택은 이번 초안의 권고안이다. 구현을 시작하기 전에 이 두 선택을 사용자와 최종 확인하고, 변경이 필요하면 제품 정책과 공유 계약을 먼저 수정한다.
