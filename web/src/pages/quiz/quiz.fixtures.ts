import type {
  QuizAnswer,
  QuizAnswers,
  QuizBinaryOutcome,
  QuizConditions,
  QuizEssayAssessmentResult,
  QuizGenerationReady,
  QuizQuestion,
  QuizResult,
  QuizResultOutcome,
  QuizResultSummary,
  QuizSubmissionResult,
  QuizSubmitPayload,
} from './quiz.types'

export const quizFixtureConditions: QuizConditions = {
  questionTypes: ['MULTIPLE_CHOICE', 'FILL_BLANK', 'SHORT_ANSWER', 'ESSAY'],
  difficulty: 'NORMAL',
  maxCount: 10,
}

export const quizFixtureQuestions: QuizQuestion[] = [
  {
    id: 'q1',
    number: 1,
    type: 'MULTIPLE_CHOICE',
    topic: '스택의 처리 순서',
    prompt: '스택에서 가장 나중에 들어온 데이터가 먼저 나오는 원칙은 무엇인가요?',
    choices: [
      { id: 'fifo', label: 'FIFO' },
      { id: 'lifo', label: 'LIFO' },
      { id: 'round-robin', label: 'Round Robin' },
      { id: 'priority', label: 'Priority' },
    ],
  },
  {
    id: 'q2',
    number: 2,
    type: 'FILL_BLANK',
    topic: '큐와 스택의 원칙',
    prompt: '큐는 [1] 원칙을 따르며, 스택은 [2] 원칙을 따릅니다.',
    blanks: [
      { id: 'q2-b1', label: '1번' },
      { id: 'q2-b2', label: '2번' },
    ],
  },
  {
    id: 'q3',
    number: 3,
    type: 'SHORT_ANSWER',
    topic: '스택 연산',
    prompt: '스택에서 데이터를 꺼내는 연산의 이름은 무엇인가요?',
  },
  {
    id: 'q4',
    number: 4,
    type: 'ESSAY',
    topic: '스택과 큐 비교',
    prompt: '스택과 큐의 데이터 제거 순서가 어떻게 다른지 설명해 보세요.',
  },
  {
    id: 'q5',
    number: 5,
    type: 'MULTIPLE_CHOICE',
    topic: '큐 연산',
    prompt: '큐에 데이터를 추가하는 연산은 무엇인가요?',
    choices: [
      { id: 'enqueue', label: 'Enqueue' },
      { id: 'dequeue', label: 'Dequeue' },
      { id: 'push', label: 'Push' },
      { id: 'pop', label: 'Pop' },
    ],
  },
  {
    id: 'q6',
    number: 6,
    type: 'FILL_BLANK',
    topic: '스택의 순서',
    prompt: '스택은 가장 마지막에 들어온 데이터가 가장 [1] 나옵니다.',
    blanks: [{ id: 'q6-b1', label: '1번' }],
  },
  {
    id: 'q7',
    number: 7,
    type: 'SHORT_ANSWER',
    topic: '큐 연산',
    prompt: '큐에서 데이터를 꺼내는 연산의 이름은 무엇인가요?',
  },
  {
    id: 'q8',
    number: 8,
    type: 'ESSAY',
    topic: '스택의 활용',
    prompt: '스택이 적합한 사용 사례를 하나 들고 그 이유를 설명해 보세요.',
  },
]

export const quizFixtureAnswers: QuizAnswers = {
  q1: { type: 'MULTIPLE_CHOICE', choiceId: 'lifo' },
  q2: { type: 'FILL_BLANK', values: { 'q2-b1': 'FIFO', 'q2-b2': '' } },
  q3: { type: 'SHORT_ANSWER', value: '꺼내기' },
  q4: {
    type: 'ESSAY',
    value: '스택은 마지막에 넣은 값부터 꺼내고 큐는 먼저 넣은 값부터 꺼냅니다.',
  },
  q8: {
    type: 'ESSAY',
    value: '웹 브라우저의 뒤로 가기 기록은 최근 페이지부터 꺼내므로 스택이 적합합니다.',
  },
}

export const quizFixtureResult: QuizResult = {
  summary: {
    correctCount: 3,
    gradedCount: 6,
    essayCorrectCount: 0,
    essayPartialCount: 0,
    essayIncorrectCount: 0,
    reviewCount: 3,
  },
  items: [
    {
      questionId: 'q1',
      number: 1,
      type: 'MULTIPLE_CHOICE',
      topic: '스택의 처리 순서',
      prompt: '스택에서 가장 나중에 들어온 데이터가 먼저 나오는 원칙은 무엇인가요?',
      answer: 'LIFO',
      correctAnswer: 'LIFO',
      outcome: 'CORRECT',
      explanation: '스택은 가장 나중에 저장한 데이터를 가장 먼저 꺼내는 LIFO 구조입니다.',
      sourceExcerpt: '스택은 후입선출(LIFO) 원칙으로 동작한다.',
      editable: false,
    },
    {
      questionId: 'q2',
      number: 2,
      type: 'FILL_BLANK',
      topic: '큐와 스택의 원칙',
      prompt: '큐는 [1] 원칙을 따르며, 스택은 [2] 원칙을 따릅니다.',
      answer: '1번 FIFO · 2번 답하지 않음',
      correctAnswer: '1번 FIFO · 2번 LIFO',
      outcome: 'INCORRECT',
      explanation: '큐는 FIFO, 스택은 LIFO 원칙을 따르며 모든 빈칸을 맞혀야 정답입니다.',
      sourceExcerpt: '큐는 선입선출, 스택은 후입선출 방식으로 데이터를 처리한다.',
      editable: false,
    },
    {
      questionId: 'q3',
      number: 3,
      type: 'SHORT_ANSWER',
      topic: '스택 연산',
      prompt: '스택에서 데이터를 꺼내는 연산의 이름은 무엇인가요?',
      answer: '꺼내기',
      correctAnswer: 'pop',
      outcome: 'INCORRECT',
      explanation: '스택에서 맨 위 데이터를 꺼내는 연산을 pop이라고 합니다.',
      sourceExcerpt: 'pop은 top이 가리키는 데이터를 반환하고 스택에서 제거한다.',
      editable: true,
    },
    {
      questionId: 'q4',
      number: 4,
      type: 'ESSAY',
      topic: '스택과 큐 비교',
      prompt: '스택과 큐의 데이터 제거 순서가 어떻게 다른지 설명해 보세요.',
      answer: '스택은 마지막에 넣은 값부터 꺼내고 큐는 먼저 넣은 값부터 꺼냅니다.',
      correctAnswer:
        '스택은 가장 나중에 들어온 데이터를 먼저 제거하고, 큐는 가장 먼저 들어온 데이터를 먼저 제거합니다.',
      keyPoints: ['스택은 후입선출(LIFO)', '큐는 선입선출(FIFO)', '두 구조의 제거 순서 비교'],
      explanation: '삽입 시점과 제거 순서의 관계를 두 자료구조에 맞게 비교하면 됩니다.',
      sourceExcerpt: '스택은 후입선출, 큐는 선입선출 원칙으로 원소를 제거한다.',
      editable: false,
    },
    {
      questionId: 'q5',
      number: 5,
      type: 'MULTIPLE_CHOICE',
      topic: '큐 연산',
      prompt: '큐에 데이터를 추가하는 연산은 무엇인가요?',
      answer: 'Enqueue',
      correctAnswer: 'Enqueue',
      outcome: 'CORRECT',
      explanation: '큐의 뒤에 데이터를 추가하는 연산을 enqueue라고 합니다.',
      sourceExcerpt: 'enqueue는 큐의 rear에 새 원소를 삽입한다.',
      editable: false,
    },
    {
      questionId: 'q6',
      number: 6,
      type: 'FILL_BLANK',
      topic: '스택의 순서',
      prompt: '스택은 가장 마지막에 들어온 데이터가 가장 [1] 나옵니다.',
      answer: '먼저',
      correctAnswer: '먼저',
      outcome: 'CORRECT',
      explanation: '후입선출은 마지막에 들어온 데이터가 먼저 나오는 원칙입니다.',
      sourceExcerpt: '스택의 top에 마지막으로 삽입된 원소가 가장 먼저 제거된다.',
      editable: false,
    },
    {
      questionId: 'q7',
      number: 7,
      type: 'SHORT_ANSWER',
      topic: '큐 연산',
      prompt: '큐에서 데이터를 꺼내는 연산의 이름은 무엇인가요?',
      answer: '',
      correctAnswer: 'dequeue',
      outcome: 'INCORRECT',
      explanation: '큐의 앞에서 데이터를 꺼내는 연산을 dequeue라고 합니다.',
      sourceExcerpt: 'dequeue는 큐의 front 원소를 반환하고 제거한다.',
      editable: false,
    },
    {
      questionId: 'q8',
      number: 8,
      type: 'ESSAY',
      topic: '스택의 활용',
      prompt: '스택이 적합한 사용 사례를 하나 들고 그 이유를 설명해 보세요.',
      answer: '웹 브라우저의 뒤로 가기 기록은 최근 페이지부터 꺼내므로 스택이 적합합니다.',
      correctAnswer:
        '함수 호출 기록이나 브라우저 방문 기록처럼 가장 최근 항목부터 되돌려야 하는 작업에 적합합니다.',
      keyPoints: ['구체적인 사용 사례', '가장 최근 항목을 먼저 처리', 'LIFO 원칙과 사례의 연결'],
      explanation: '사례가 최근 항목부터 처리해야 하는 이유를 LIFO 원칙과 연결하면 됩니다.',
      sourceExcerpt: '스택은 실행 취소, 함수 호출, 방문 기록처럼 역순 처리가 필요한 곳에 사용된다.',
      editable: false,
    },
  ],
}

export const quizFixtureReady: QuizGenerationReady = {
  actualCount: 8,
  requestedCount: 10,
  conditions: quizFixtureConditions,
}

export type QuizFixtureGeneration = {
  ready: QuizGenerationReady
  questions: QuizQuestion[]
}

export function createQuizFixtureResult(
  questions: QuizQuestion[],
  submittedAnswers?: QuizAnswers,
  essayOutcomes: Record<string, QuizResultOutcome> = {},
): QuizResult {
  const questionById = new Map(questions.map((question) => [question.id, question]))
  const items = quizFixtureResult.items
    .filter((item) => questionById.has(item.questionId))
    .map((item) => {
      const question = questionById.get(item.questionId)
      const answer = question ? submittedAnswers?.[question.id] : undefined
      const submittedAnswer = question && answer ? formatFixtureAnswer(question, answer) : item.answer
      if (item.type !== 'ESSAY') {
        return {
          ...item,
          number: question?.number ?? item.number,
          answer: submittedAnswers ? submittedAnswer : item.answer,
        }
      }
      const submittedEssay = answer?.type === 'ESSAY' && answer.value.trim().length > 0
      return {
        ...item,
        number: question?.number ?? item.number,
        answer: submittedAnswers ? submittedAnswer : item.answer,
        outcome:
          essayOutcomes[item.questionId] ??
          (submittedAnswers && !submittedEssay ? 'INCORRECT' : undefined),
      }
    })
    .sort((left, right) => left.number - right.number)
  const automaticItems = items.filter((item) => item.type !== 'ESSAY')
  const essayItems = items.filter((item) => item.type === 'ESSAY')
  return {
    summary: {
      correctCount: automaticItems.filter((item) => item.outcome === 'CORRECT').length,
      gradedCount: automaticItems.length,
      essayCorrectCount: essayItems.filter((item) => item.outcome === 'CORRECT').length,
      essayPartialCount: essayItems.filter((item) => item.outcome === 'PARTIAL').length,
      essayIncorrectCount: essayItems.filter((item) => item.outcome === 'INCORRECT').length,
      reviewCount:
        automaticItems.filter((item) => item.outcome !== 'CORRECT').length +
        essayItems.filter(
          (item) => item.outcome === 'PARTIAL' || item.outcome === 'INCORRECT',
        ).length,
    },
    items,
  }
}

function formatFixtureAnswer(question: QuizQuestion, answer: QuizAnswer) {
  switch (answer.type) {
    case 'MULTIPLE_CHOICE':
      return question.type === 'MULTIPLE_CHOICE'
        ? (question.choices.find((choice) => choice.id === answer.choiceId)?.label ?? '')
        : ''
    case 'FILL_BLANK':
      return question.type === 'FILL_BLANK'
        ? question.blanks
            .map((blank) => `${blank.label} ${answer.values[blank.id]?.trim() || '답하지 않음'}`)
            .join(' · ')
        : ''
    case 'SHORT_ANSWER':
    case 'ESSAY':
      return answer.type === question.type ? answer.value.trim() : ''
  }
}

function selectFixtureQuestions(conditions: QuizConditions) {
  const candidates = quizFixtureQuestions.filter((question) =>
    conditions.questionTypes.includes(question.type),
  )
  const required = conditions.questionTypes
    .map((type) => candidates.find((question) => question.type === type))
    .filter((question): question is QuizQuestion => Boolean(question))
  const requiredIds = new Set(required.map((question) => question.id))
  const selected = [...required, ...candidates.filter((question) => !requiredIds.has(question.id))]
    .slice(0, conditions.maxCount)
    .map((question, index) => ({ ...question, number: index + 1 }))
  return selected
}

/** Dev preview only. The delay is a fixture boundary, not an API or progress estimate. */
export async function resolveQuizFixtureGeneration(
  conditions: QuizConditions,
): Promise<QuizFixtureGeneration> {
  await new Promise((resolve) => window.setTimeout(resolve, 700))
  const questions = selectFixtureQuestions(conditions)
  return {
    ready: {
      actualCount: Math.min(questions.length, conditions.maxCount),
      requestedCount: conditions.maxCount,
      conditions,
    },
    questions,
  }
}

export async function resolveQuizFixtureCorrection(
  { questionId, outcome }: { questionId: string; outcome: QuizBinaryOutcome },
  questions: QuizQuestion[] = quizFixtureQuestions,
  essayOutcomes: Record<string, QuizResultOutcome> = {},
  submittedAnswers?: QuizAnswers,
): Promise<QuizResultSummary> {
  await new Promise((resolve) => window.setTimeout(resolve, 450))
  const items = createQuizFixtureResult(questions, submittedAnswers, essayOutcomes).items.map((item) =>
    item.questionId === questionId ? { ...item, outcome } : item,
  )
  const automaticItems = items.filter((item) => item.type !== 'ESSAY')
  const assessments = Object.values(essayOutcomes)
  return {
    correctCount: automaticItems.filter((item) => item.outcome === 'CORRECT').length,
    gradedCount: automaticItems.length,
    essayCorrectCount: assessments.filter((assessment) => assessment === 'CORRECT').length,
    essayPartialCount: assessments.filter((assessment) => assessment === 'PARTIAL').length,
    essayIncorrectCount: assessments.filter((assessment) => assessment === 'INCORRECT').length,
    reviewCount:
      automaticItems.filter((item) => item.outcome !== 'CORRECT').length +
      assessments.filter((assessment) => assessment !== 'CORRECT').length,
  }
}

export async function resolveQuizFixtureSubmission(
  payload: QuizSubmitPayload,
  questions: QuizQuestion[],
): Promise<QuizSubmissionResult> {
  await new Promise((resolve) => window.setTimeout(resolve, 450))
  const pendingEssayQuestionIds = questions
    .filter((question) => {
      const answer = payload.answers[question.id]
      return question.type === 'ESSAY' && answer?.type === 'ESSAY' && answer.value.trim().length > 0
    })
    .map((question) => question.id)
  const summary = createQuizFixtureResult(questions).summary
  return {
    attemptId: 'attempt_fixture_1',
    status: pendingEssayQuestionIds.length > 0 ? 'SELF_ASSESSMENT_REQUIRED' : 'COMPLETED',
    automaticGrading: {
      correctQuestionCount: summary.correctCount,
      gradedQuestionCount: summary.gradedCount,
    },
    pendingEssayQuestionIds,
    createdAt: new Date().toISOString(),
  }
}

export async function resolveQuizFixtureEssayAssessment(
  input: { attemptId: string; questionId: string; assessment: QuizResultOutcome },
  remainingSelfAssessmentCount: number,
): Promise<QuizEssayAssessmentResult> {
  await new Promise((resolve) => window.setTimeout(resolve, 450))
  return {
    ...input,
    status: remainingSelfAssessmentCount === 0 ? 'COMPLETED' : 'SELF_ASSESSMENT_REQUIRED',
    remainingSelfAssessmentCount,
  }
}
