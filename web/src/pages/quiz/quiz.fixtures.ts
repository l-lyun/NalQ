import { adaptQuizResult, createSubmissionPayload, includedQuestionTypes } from '@/features/quiz/model/quizAdapter'
import type { QuizResultResponse } from '@/features/quiz/api/quiz.types'

import type {
  QuizAnswers,
  QuizBinaryOutcome,
  QuizConditions,
  QuizEssayAssessmentResult,
  QuizGenerationReady,
  QuizQuestion,
  QuizResult,
  QuizResultOutcome,
  QuizSubmissionPayload,
  QuizSubmissionResult,
} from './quiz.types'

export const quizFixtureConditions: QuizConditions = {
  selectedTypes: ['MULTIPLE_CHOICE', 'FILL_IN_THE_BLANK', 'SHORT_ANSWER', 'ESSAY'],
  difficulty: 'NORMAL',
  maxQuestionCount: 10,
}

export const quizFixtureQuestions: QuizQuestion[] = [
  {
    questionId: 'question_1', number: 1, type: 'MULTIPLE_CHOICE', topic: '스택의 처리 순서',
    prompt: '스택의 처리 원칙은 무엇인가요?',
    choices: [
      { choiceId: 'choice_1_1', text: 'FIFO' },
      { choiceId: 'choice_1_2', text: 'LIFO' },
      { choiceId: 'choice_1_3', text: 'Priority' },
    ],
  },
  {
    questionId: 'question_2', number: 2, type: 'FILL_IN_THE_BLANK', topic: '큐와 스택',
    prompt: '큐는 [1], 스택은 [2] 원칙을 따릅니다.',
    blanks: [{ blankId: 'blank_2_1', number: 1 }, { blankId: 'blank_2_2', number: 2 }],
  },
  {
    questionId: 'question_3', number: 3, type: 'SHORT_ANSWER', topic: '스택 연산',
    prompt: '스택에서 데이터를 꺼내는 연산은 무엇인가요?',
  },
  {
    questionId: 'question_4', number: 4, type: 'ESSAY', topic: '스택과 큐 비교',
    prompt: '스택과 큐의 데이터 제거 순서가 어떻게 다른지 설명해 보세요.',
  },
  {
    questionId: 'question_5', number: 5, type: 'MULTIPLE_CHOICE', topic: '큐 연산',
    prompt: '큐에 데이터를 추가하는 연산은 무엇인가요?',
    choices: [
      { choiceId: 'choice_5_1', text: 'Enqueue' }, { choiceId: 'choice_5_2', text: 'Dequeue' },
      { choiceId: 'choice_5_3', text: 'Push' }, { choiceId: 'choice_5_4', text: 'Pop' },
    ],
  },
  {
    questionId: 'question_6', number: 6, type: 'MULTIPLE_CHOICE', topic: '그래프',
    prompt: '그래프를 구성하는 요소를 고르세요.',
    choices: [
      { choiceId: 'choice_6_1', text: '정점과 간선' }, { choiceId: 'choice_6_2', text: 'front와 rear' },
      { choiceId: 'choice_6_3', text: 'top과 bottom' }, { choiceId: 'choice_6_4', text: '키와 값' },
      { choiceId: 'choice_6_5', text: '행과 열' },
    ],
  },
]

export const quizFixtureAnswers: QuizAnswers = {
  question_1: { type: 'MULTIPLE_CHOICE', selectedChoiceId: 'choice_1_2' },
  question_2: { type: 'FILL_IN_THE_BLANK', blankAnswers: { blank_2_1: 'FIFO' } },
  question_3: { type: 'SHORT_ANSWER', text: '꺼내기' },
  question_4: { type: 'ESSAY', text: '스택은 나중 값부터, 큐는 먼저 넣은 값부터 꺼냅니다.' },
}

const fixtureQuestionResults: QuizResultResponse['questionResults'] = [
  {
    ...quizFixtureQuestions[0], response: { selectedChoiceId: 'choice_1_2' },
    representativeAnswer: { selectedChoiceId: 'choice_1_2' }, outcome: 'CORRECT',
    explanation: '스택은 LIFO 구조입니다.', sourceExcerpt: '스택은 후입선출 원칙으로 동작한다.',
  },
  {
    ...quizFixtureQuestions[1], response: { blankAnswers: [{ blankId: 'blank_2_1', answer: 'FIFO' }] },
    representativeAnswer: { blankAnswers: [{ blankId: 'blank_2_1', answer: 'FIFO' }, { blankId: 'blank_2_2', answer: 'LIFO' }] },
    outcome: 'INCORRECT', explanation: '모든 빈칸을 맞혀야 정답입니다.',
    sourceExcerpt: '큐는 선입선출, 스택은 후입선출이다.',
  },
  {
    ...quizFixtureQuestions[2], response: { answer: '꺼내기' }, representativeAnswer: { answer: 'pop' },
    outcome: 'INCORRECT', explanation: '꺼내는 연산은 pop입니다.', sourceExcerpt: 'pop은 top 원소를 제거한다.',
  },
  {
    ...quizFixtureQuestions[3], response: { answer: '스택은 나중 값부터, 큐는 먼저 넣은 값부터 꺼냅니다.' },
    representativeAnswer: {
      modelAnswer: '스택은 후입선출, 큐는 선입선출 방식입니다.',
      keyPoints: ['스택은 후입선출', '큐는 선입선출'],
    },
    outcome: 'PARTIAL', explanation: '제거 순서를 비교합니다.', sourceExcerpt: '두 구조는 제거 순서가 다르다.',
  },
  {
    ...quizFixtureQuestions[4], response: null, representativeAnswer: { selectedChoiceId: 'choice_5_1' },
    outcome: 'INCORRECT', explanation: '큐 추가 연산은 enqueue입니다.', sourceExcerpt: 'enqueue는 rear에 삽입한다.',
  },
  {
    ...quizFixtureQuestions[5], response: { selectedChoiceId: 'choice_6_1' },
    representativeAnswer: { selectedChoiceId: 'choice_6_1' }, outcome: 'CORRECT',
    explanation: '그래프는 정점과 간선으로 구성됩니다.', sourceExcerpt: '그래프 G=(V,E)로 표현한다.',
  },
]

function fixtureContractResult(
  questions = quizFixtureQuestions,
  outcomes: Record<string, QuizResultOutcome> = {},
): QuizResultResponse {
  const ids = new Set(questions.map((question) => question.questionId))
  const questionResults = fixtureQuestionResults
    .filter((item) => ids.has(item.questionId))
    .map((item) => ({ ...item, outcome: outcomes[item.questionId] ?? item.outcome }))
  const automatic = questionResults.filter((item) => item.type !== 'ESSAY')
  const essays = questionResults.filter((item) => item.type === 'ESSAY')
  return {
    attemptId: '550e8400-e29b-41d4-a716-446655440000', quizSetId: 'qset_fixture', status: 'COMPLETED',
    summary: {
      scoredGrading: { correctQuestionCount: automatic.filter((item) => item.outcome === 'CORRECT').length, gradedQuestionCount: automatic.length },
      essaySelfAssessment: {
        correctCount: essays.filter((item) => item.outcome === 'CORRECT').length,
        partialCount: essays.filter((item) => item.outcome === 'PARTIAL').length,
        incorrectCount: essays.filter((item) => item.outcome === 'INCORRECT').length,
      },
      reviewQuestionCount: questionResults.filter((item) => item.outcome !== 'CORRECT').length,
    },
    questionResults,
  }
}

export const quizFixtureResult = adaptQuizResult(fixtureContractResult())
export const quizFixtureReady: QuizGenerationReady = {
  actualCount: quizFixtureQuestions.length,
  includedTypes: includedQuestionTypes(quizFixtureQuestions),
  requestedConfig: quizFixtureConditions,
}

export type QuizFixtureGeneration = { ready: QuizGenerationReady; questions: QuizQuestion[] }

export function createQuizFixtureResult(
  questions: QuizQuestion[],
  _submittedAnswers?: QuizAnswers,
  essayOutcomes: Record<string, QuizResultOutcome> = {},
): QuizResult {
  return adaptQuizResult(fixtureContractResult(questions, essayOutcomes))
}

function selectFixtureQuestions(conditions: QuizConditions) {
  return quizFixtureQuestions
    .filter((question) => conditions.selectedTypes.includes(question.type))
    .slice(0, conditions.maxQuestionCount)
    .map((question, index) => ({ ...question, number: index + 1 } as QuizQuestion))
}

export async function resolveQuizFixtureGeneration(conditions: QuizConditions): Promise<QuizFixtureGeneration> {
  await new Promise((resolve) => window.setTimeout(resolve, 100))
  const questions = selectFixtureQuestions(conditions)
  return { ready: { actualCount: questions.length, includedTypes: includedQuestionTypes(questions), requestedConfig: conditions }, questions }
}

export async function resolveQuizFixtureGradingOverride(
  { questionId, outcome }: { questionId: string; outcome: QuizBinaryOutcome },
  questions: QuizQuestion[] = quizFixtureQuestions,
  currentOutcomes: Record<string, QuizResultOutcome> = {},
): Promise<QuizResult> {
  await new Promise((resolve) => window.setTimeout(resolve, 100))
  return adaptQuizResult(
    fixtureContractResult(questions, { ...currentOutcomes, [questionId]: outcome }),
  )
}

export async function resolveQuizFixtureSubmission(
  input: { attemptId?: string; payload: QuizSubmissionPayload },
  questions: QuizQuestion[],
): Promise<QuizSubmissionResult> {
  await new Promise((resolve) => window.setTimeout(resolve, 100))
  const pendingEssayQuestionIds = questions.filter((question) =>
    question.type === 'ESSAY' && input.payload.responses.some((response) => response.questionId === question.questionId),
  ).map((question) => question.questionId)
  return {
    attemptId: input.attemptId ?? '550e8400-e29b-41d4-a716-446655440000',
    status: pendingEssayQuestionIds.length ? 'SELF_ASSESSMENT_REQUIRED' : 'COMPLETED',
    automaticGrading: { correctQuestionCount: 2, gradedQuestionCount: questions.filter((question) => question.type !== 'ESSAY').length },
    pendingEssayQuestionIds,
    createdAt: new Date().toISOString(),
  }
}

export async function resolveQuizFixtureEssayAssessment(
  input: { resourceId: string; questionId: string; assessment: QuizResultOutcome },
  remainingSelfAssessmentCount: number,
): Promise<QuizEssayAssessmentResult> {
  await new Promise((resolve) => window.setTimeout(resolve, 100))
  return { attemptId: input.resourceId, questionId: input.questionId, assessment: input.assessment,
    status: remainingSelfAssessmentCount ? 'SELF_ASSESSMENT_REQUIRED' : 'COMPLETED', remainingSelfAssessmentCount }
}

export { createSubmissionPayload }
