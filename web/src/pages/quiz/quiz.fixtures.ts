import type {
  QuizAnswers,
  QuizConditions,
  QuizGenerationReady,
  QuizQuestion,
  QuizResult,
  QuizResultOutcome,
  QuizResultSummary,
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
  q3: { type: 'SHORT_ANSWER', value: 'pop' },
}

export const quizFixtureResult: QuizResult = {
  summary: { correctCount: 4, gradedCount: 6, reviewCount: 2 },
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
  ],
}

export const quizFixtureReady: QuizGenerationReady = {
  actualCount: 8,
  requestedCount: 10,
  conditions: quizFixtureConditions,
}

/** Dev preview only. The delay is a fixture boundary, not an API or progress estimate. */
export async function resolveQuizFixtureGeneration() {
  await new Promise((resolve) => window.setTimeout(resolve, 700))
  return quizFixtureReady
}

export async function resolveQuizFixtureCorrection({
  outcome,
}: {
  questionId: string
  outcome: QuizResultOutcome
}): Promise<QuizResultSummary> {
  await new Promise((resolve) => window.setTimeout(resolve, 450))
  return outcome === 'CORRECT'
    ? { correctCount: 5, gradedCount: 6, reviewCount: 1 }
    : { correctCount: 4, gradedCount: 6, reviewCount: 2 }
}
