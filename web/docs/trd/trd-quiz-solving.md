---
document_type: trd
status: review
scope: web
---

# [TRD · Web] 웹 퀴즈 화면 상태 설계

- 상태: 검토 중
- 소유 영역: `web/`
- 관련 원장:
  - [퀴즈 PRD](../../../docs/prd/prd-quiz-learning.md)
  - [퀴즈 생성부터 복습까지의 흐름](../../../docs/ux/flow-quiz-solving.md)
  - [학습자료·퀴즈·복습 API 계약](../../../docs/contracts/contract-api-quiz-learning.md)

## 문서 책임

이 문서는 퀴즈 생성 조건 표시와 최종 제출 전 본 퀴즈·복습의 화면 상태를 웹에서 구현하는 방법을 정의한다. 이탈·재진입, 제출과 복습의 제품 정책은 기능명세와 흐름이 책임지고, 서버 입출력은 API 계약이 책임진다.

## 확정 구현 매핑

- 본 퀴즈 화면 상태는 `Zustand` store로 관리한다.
- 한 화면 한 문항, 이전·다음, 제출 경고와 자기평가 단계 전환은 `useFunnel` 기반 단계 모델로 구성한다.
- 본 퀴즈와 복습의 현재 문항·미제출 답안은 열린 화면의 Zustand 메모리에만 둔다. 새로고침·화면 이탈·브라우저 종료 뒤에는 복원하지 않고 첫 문항부터 다시 푼다.
- `localStorage`에는 생성 요청 조건만 `userId + quizSetId` 범위로 보존할 수 있다. 값은 `selectedTypes`, `difficulty`, `maxQuestionCount`이며 답안·현재 문항·attempt ID·제출 payload를 섞지 않는다.
- 생성 조건 로컬 값은 생성 중 화면에서 요청 조건을 다시 표시하고 `READY` 화면에서 실제 생성 수·유형과 함께 보여주기 위한 보조 정보다. 값이 없거나 손상돼도 활성 생성 조회와 상태 전이는 서버 응답만으로 계속한다.
- 최종 제출 직렬화는 [API 계약의 응답 모양](../../../docs/contracts/contract-api-quiz-learning.md#최종-제출)을 따른다. 각 항목에는 `questionId`와 유형별 답안 필드만 보내고 `type`은 보내지 않는다. 빈칸은 작성된 값만 `blankAnswers: [{ blankId, answer }]`로 만들며 누락 빈칸을 빈 문자열로 채우지 않는다.
- 사용자가 제출을 확정하면 HTTP 요청 전에 `crypto.randomUUID()`로 UUID v4 `attemptId` 하나를 만들고, 현재 화면이 살아 있는 동안 해당 ID와 제출 payload를 메모리에 유지한다. `randomUUID`를 제공하지 않는 지원 WebView에서는 `crypto.getRandomValues` 기반 UUID v4 helper까지만 호환 fallback으로 허용하며 `Math.random`은 사용하지 않는다.
- 제출 응답을 받지 못했지만 화면 상태가 남아 있으면 같은 attempt ID와 payload로 `PUT`을 재시도한다. 새로고침·이탈로 화면 상태를 잃으면 기존 제출을 자동 복원하지 않고 처음부터 다시 푼다.
- 프론트는 attempt ID, 답안 또는 payload의 hash·fingerprint를 만들지 않는다.
- 틀린 문제 다시 풀기는 본 퀴즈와 같은 풀이 store와 답안 serializer를 재사용한다. 서버 review session은 문제 목록 snapshot만 보존하고, 현재 문항과 미제출 답안은 화면 메모리에만 둔다.
- 다시 풀기 최종 제출은 이미 존재하는 `reviewSessionId`를 대상으로 `PUT /submission`을 사용한다. 응답을 받지 못했지만 화면 상태가 남아 있으면 같은 세션과 payload로 재시도한다.

## 상태 경계

- Zustand 풀이 상태는 문제 풀이 전에 공개 가능한 필드와 사용자 답안만 가진다. 정답·해설·원문 근거를 미리 저장하지 않는다.
- 생성 조건 로컬 값은 현재 인증 사용자 키로만 조회한다. 로그아웃하면 해당 사용자의 생성 조건 값을 삭제하고 계정 전환 뒤 이전 계정 값을 노출하지 않는다.
- storage를 사용할 수 없거나 쓰기에 실패하면 생성 조건 표시만 생략한다. 서버 상태나 퀴즈 풀이·제출에는 영향을 주지 않는다.

## 실제 통합 라우트와 Query 경계

- 퀴즈 생성·자기평가·복습 서버 API가 함께 배포되기 전에는 `VITE_QUIZ_API_ENABLED=false`를 기본값으로 사용한다. 이때 로컬 개발 서버는 인증된 학습 화면의 실제 버튼과 URL을 유지한 채 기존 fixture를 사용하는 명시적 `mock` 모드로 동작하며, 별도 preview URL 없이 조건 설정부터 생성·풀이·결과·복습까지 검토할 수 있다. `VITE_QUIZ_API_ENABLED=true`이면 같은 URL이 실제 API route page를 사용한다. 프로덕션은 API가 비활성일 때 fixture로 대체하지 않고 퀴즈 라우트와 최신 복습 Query를 등록·실행하지 않는다.
- 퀴즈 route와 학습 메인의 퀴즈 관리·최근 퀴즈·복습 후보 adapter는 모두 같은 `VITE_QUIZ_API_ENABLED`에서 runtime mode를 파생한다. 별도 관리 API flag를 두어 route만 켜지고 학습 메인 Query는 꺼지는 분리 배포 상태를 만들지 않는다.
- 인증 라우트는 생성 조건 진입 `/learning/:materialId/quiz`, QuizSet 상태·풀이 `/quiz-sets/:quizSetId`, 본 퀴즈 결과 `/quiz-attempts/:attemptId/result`, 최신 복습 진입 `/review`, 복습 실행 `/review-sessions/:reviewSessionId`로 연결한다.
- 학습 메인의 `전체 문제 다시 풀기`는 `/quiz-sets/:quizSetId`에 `restartMain` route state를 전달한다. 이 intent에서는 미완료 서술형 채점 재개 Query를 건너뛰고 `READY`부터 새 `MAIN` 회차를 시작하며, 일반 진입과 `채점이 남았어요` 행동은 기존처럼 pending 회차를 우선한다.
- 내 퀴즈의 `퀴즈 풀기`도 `/quiz-sets/:quizSetId`에 `{ restartMain: true }` route state를 전달해 항상 새 `MAIN` 회차를 시작한다. 내 퀴즈 목록은 풀이·결과·복습 행동을 결정하지 않으므로 카드별 pending self-assessment Query와 페이지 단위 latest review Query를 실행하지 않는다. 해당 Query는 학습 메인과 일반 QuizSet route처럼 실제 소비 화면에만 남긴다.
- 내 퀴즈 disclosure는 내 학습자료와 같은 `expanded` URL search parameter의 쉼표 구분 ID Set 규칙을 사용한다. 검색·페이지·focus parameter를 갱신할 때 `expanded`를 유지하고, 여러 QuizSet을 동시에 펼칠 수 있다.
- 퀴즈 이름 변경은 펼친 `READY` 카드의 controlled modal에서 수행한다. 현재 제목을 draft로 시작해 공백과 255 Unicode code point 제한을 검증하고, 저장 중 중복 제출과 닫기를 막는다. 성공 시 QuizSet 목록·상세·복습·홈 Query를 기존 invalidation 경계로 갱신하고 dialog trigger로 focus를 복귀하며, 실패 시 draft와 오류를 modal에 유지한다. `GENERATING`·`FAILED`에서는 이름 변경과 풀이를 모두 비활성화한다.
- 홈의 대표 복습 행동은 최신 복습 Query가 대상을 반환할 때 `/review`로 진입해 활성 세션 재개 또는 새 세션 생성을 위임한다. 학습 메인은 최근 퀴즈와 별도로 `GET /api/v1/quiz-reviews/candidates?limit=3`을 조회하고, 후보의 `sourceAttemptId`로 세션을 직접 만든 뒤 반환된 세션 route로 이동한다. 미완료 자기평가와 활성 복습은 새 세션보다 우선한다. 실제 API가 빈 후보를 반환하면 학습 메인에는 복습 빈 상태를 표시하고, 개발 `mock` 모드는 동일 타입 adapter로 이 진입을 검토한다.
- 서버 상태 Query key는 모두 `private` prefix 아래에 두어 기존 로그아웃·세션 종료 시 취소와 캐시 제거 범위에 포함한다.
- 학습자료 목록·상세와 홈·학습이 공유하는 최신 복습 요약은 5분 동안 fresh로 재사용한다. 학습자료 생성, QuizSet 생성·생성 종료, 본 퀴즈 제출·서술형 자기평가·채점 판정 변경, 복습 세션 생성·제출·자기평가 성공 시 관련 feature Query key를 명시적으로 invalidate한다. 현재 풀이 화면이 직접 응답을 적용하는 attempt·review session/result Query는 즉시 재요청으로 화면 상태를 덮지 않도록 stale 표기만 하고 다음 조회에서 새로 가져오며, 홈·학습 요약은 성공 직후 다시 조회할 수 있게 한다.
- 생성 접수 성공 뒤에는 응답의 `quizSetId` 라우트로 교체하고, 활성 생성 재진입도 서버가 반환한 `quizSetId` 라우트로 교체한다. 따라서 polling과 풀이 데이터의 기준은 URL의 서버 리소스 ID다.
- `docs/contracts/contract-api-quiz-learning.md`의 복습 결과 절은 `summary`의 의미만 정의하고 JSON 필드명을 정의하지 않는다. 계약이 확정되기 전 adapter는 전송 필드를 추측하지 않고 `questionResults`에서 현재 화면에 이미 필요한 표시 수치만 계산한다.
- 같은 계약의 복습 `SELF_ASSESSMENT_REQUIRED` 재조회에는 남은 서술형 문항 ID 또는 미평가 상태 표현이 없다. 현재 화면에서 제출 응답의 `pendingEssayQuestionIds`를 가진 흐름은 계속 처리하지만, 화면 상태를 잃은 재진입은 임의 추정하지 않고 일반 조회 실패 경계에서 멈춘다. 서버 계약에 남은 문항 식별 정보가 추가되면 기존 자기평가 화면으로 연결한다.

## 표현 컴포넌트 경계

- `web/src/pages/quiz/`는 API가 준비되기 전에도 검토할 수 있는 표현 전용 화면과 fixture를 제공한다. fixture의 지연·요약 갱신은 개발 미리보기용이며 공개 API 계약으로 해석하지 않는다.
- 내 퀴즈 카드는 접힌 header와 펼친 행동 영역을 분리하고 header만 disclosure trigger로 둔다. `GENERATING`은 펼친 영역 위에 비상호작용 overlay와 진행 표시를, `FAILED`는 같은 overlay와 정적 실패 표시를 렌더링하되 header의 펼치기·접기는 유지한다. 두 상태 모두 별도 불가능 이유 문구나 삭제 행동을 추가하지 않는다.
- 객관식 `choices`는 가변 길이 3~5개다. 풀이·결과·복습 컴포넌트와 화면 답안 상태는 네 개 고정 인덱스를 가정하지 않고 서버 배열 순서를 그대로 렌더링하며 선택값은 배열 위치가 아니라 `choiceId`로 보존한다.
- 프론트는 3개 보기에 빈 행을 추가하거나 5번째 보기를 잘라내지 않는다. 계약 범위를 벗어난 길이는 조용히 보정하지 않고 문제 데이터 오류 상태로 처리한다.
- 틀린 문제 다시 풀기는 본 퀴즈의 `QuizFlowPage`와 문항 입력·이동·전체 제출·서술형 자기평가·결과 표현을 그대로 재사용한다. 풀이 화면의 사용자 노출 차이는 상단 헤더에서 학습자료 제목 뒤에 ` · 복습`을 덧붙이는 것뿐이며, 복습용 풀이 화면이나 문항별 즉시 채점 UI를 따로 만들지 않는다.
- 개발 서버에서만 `/quiz-preview`와 `/quiz-result-preview`를 열어 전체 흐름과 결과 수정을 검토한다. 프로덕션 라우트에는 등록하지 않는다.
- 좁은 문제 진행 막대는 현재 위치를 읽는 `progressbar`로만 제공하고 직접 조작하지 않는다. 문항 이동은 `BottomSheet` 안의 44px 이상 번호 버튼으로 분리해 키보드·터치 목표를 보장한다.
- 단답형·빈칸 채우기 판정 수정은 마지막 저장 확인 판정과 요약을 화면의 기준으로 둔다. 저장 중에는 기존 결과를 유지하고, 성공 응답 뒤에만 현재 판정·점수·복습 수를 한 번에 교체하며 실패하면 이전 상태와 재시도 행동을 유지한다.
- 결과 조회 adapter는 `response=null`이면 `답하지 않음`으로 표시하고 판정 수정 행동을 만들지 않는다. 답을 작성한 `SHORT_ANSWER` 또는 `FILL_IN_THE_BLANK`만 수정 가능하며 현재 판정과 관계없이 `채점 수정` 행동을 제공한다. 빈칸 채우기는 `response.blankAnswers`에 하나 이상의 답변이 있으면 일부 빈칸만 작성했더라도 수정할 수 있고, 완전 미응답이면 수정할 수 없다. 화면에서 사용하지 않는 `automaticOutcome`, `gradingSource`, `correctedAt`, `reviewRequired`, 별도 `unanswered` 필드를 요구하지 않는다.
- 최종 제출 표현 경계는 제출 성공 응답의 `status`를 그대로 사용한다. `COMPLETED`는 결과로, `SELF_ASSESSMENT_REQUIRED`는 `pendingEssayQuestionIds` 순서의 자기평가로 이동하며, 제출 실패는 미응답 확인 시트로 되돌리지 않고 답안을 보존한 별도 재시도 상태로 이동한다.
- 퀴즈 생성 POST에는 `Idempotency-Key`를 보내지 않는다. 접수 응답을 확인하지 못한 재시도에서는 자료의 활성 생성을 먼저 조회하고, `GENERATING` QuizSet이 없을 때만 같은 조건으로 새 생성 POST를 보낸다.
- 서술형 자기평가는 결과 조회 모양의 내 답·모범 답안·핵심 포인트·해설·원문 근거를 읽고 문항별 `CORRECT`·`PARTIAL`·`INCORRECT`를 저장한다. 저장 응답의 `status`와 `remainingSelfAssessmentCount`를 확인한 뒤에만 다음 문항 또는 완료 결과로 이동한다.
- 표현 callback은 실제 API adapter가 연결될 때 서버 성공 응답을 잃지 않도록 최종 제출 결과, 서술형 저장 결과와 판정 수정 결과를 필수 반환한다. 단답형·빈칸 채우기 판정 수정 adapter는 공통 `PUT /api/v1/quiz-attempts/{attemptId}/grading-overrides/{questionId}`에 `{ outcome }`만 보내며, 기존 `short-answer-gradings` 경로는 서버의 기존 소비자 호환 별칭이므로 신규 웹 요청에는 사용하지 않는다. 한 화면에서 요청을 직렬화하고 저장 중 추가 수정 행동을 막는다. 성공하면 서버가 반환한 전체 최신 결과로 현재 결과 상태를 교체하며 로컬 delta로 문항 판정이나 요약을 추정하지 않고, 실패하면 기존 판정과 요약을 유지한 채 재시도 행동을 제공한다. 공개 revision이나 충돌 해결 UI는 두지 않고 마지막으로 서버에 커밋된 판정을 현재 값으로 사용한다.

## 구현 전 확인

- 현재 `web/package.json`에는 Zustand와 `useFunnel` 구현에 필요한 의존성이 없다. 실제 구현 작업에서 저장소 기준에 맞는 패키지와 버전을 검토해 추가한다.
- 생성 조건 로컬 레코드 모양이 바뀌면 이전 값을 안전하게 폐기하거나 명시적으로 이관한다. 알 수 없는 모양을 추측해 사용하지 않는다.

## 기술 검증 기준

- 같은 `userId + quizSetId`의 생성 조건만 기기 로컬에서 복원하며 다른 사용자·문제 세트나 손상된 값은 사용하지 않는다.
- 내 퀴즈 목록 조회만으로 카드 수에 비례한 pending self-assessment 요청이나 latest review 요청이 발생하지 않으며, `expanded` Set은 검색·페이지 전환 뒤에도 URL에서 보존된다.
- 내 퀴즈의 READY 카드 이름 변경은 modal 검증·중복 제출 방지·성공 cache invalidation·focus 복귀를 수행하고, 퀴즈 풀기는 새 `MAIN` intent를 전달한다. `GENERATING`·`FAILED`는 두 행동이 비활성이고 disclosure 자체는 조작할 수 있다.
- 생성 조건 로컬 값이 없어도 생성 중에는 일반 안내를, `READY`에서는 실제 문제 수·유형을 표시하고 서버 상태 전이를 정상 처리한다.
- 생성 POST 응답 유실 뒤 재시도는 활성 생성 조회를 먼저 수행하며, 활성 QuizSet이 있으면 새 POST 없이 해당 식별자로 polling을 이어간다.
- 새로고침·이탈·브라우저 종료 뒤 본 퀴즈와 복습의 현재 문항·미제출 답안을 복원하지 않고 첫 문항부터 다시 시작한다.
- 제출 요청 전에 UUID v4 attempt ID와 payload를 현재 화면 메모리에 한 번 만들고, 응답을 받지 못해도 화면이 살아 있는 동안에만 같은 ID와 payload로 재요청한다.
- UUID 형식 오류나 다른 사용자·QuizSet이 이미 사용한 식별자 충돌은 자동 hash 비교나 새 UUID 재발급으로 복구하지 않는다. 서버가 `ATTEMPT_001`을 반환하면 최초 제출 결과를 다른 회차로 자동 재전송하지 않고 일반 제출 오류로 멈춘다.
- 서버 요청에는 중간 위치나 미제출 답안이 포함되지 않는다.
- 최종 제출, 서술형 자기평가와 복습 요청 경로·필드는 API 계약과 일치하며 TRD에서 별도 별칭을 만들지 않는다.
- 틀린 문제 다시 풀기 세 문제는 세 답을 모두 작성한 뒤 한 번 제출하며 문항 이동 중 서버 채점 요청이 발생하지 않는다.
- 객관식 보기 3개, 4개, 5개 fixture가 풀이·결과·복습에서 모두 렌더링되고 선택한 `choiceId`가 변경 없이 제출된다.
- 답을 작성한 단답형·빈칸 채우기는 공통 `grading-overrides` 경로로 판정을 수정하고, `response=null`인 완전 미응답에는 수정 행동을 제공하지 않는다. 수정 요청 중에는 추가 요청이 발생하지 않고, 성공 응답의 전체 최신 결과가 현재 결과를 교체하며 실패하면 기존 판정과 요약을 유지한다.
