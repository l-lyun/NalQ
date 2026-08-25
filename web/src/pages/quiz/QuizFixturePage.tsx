import { useMemo, useState } from 'react'

import { QuizFlowPage } from './QuizFlowPage'
import {
  createQuizFixtureResult,
  quizFixtureAnswers,
  quizFixtureConditions,
  quizFixtureQuestions,
  quizFixtureReady,
  resolveQuizFixtureCorrection,
  resolveQuizFixtureEssayAssessment,
  resolveQuizFixtureGeneration,
  resolveQuizFixtureSubmission,
} from './quiz.fixtures'
import type {
  QuizBinaryOutcome,
  QuizConditions,
  QuizFlowKind,
  QuizFlowScene,
  QuizGenerationFailure,
  QuizResultOutcome,
} from './quiz.types'

/** Contract-shaped data mount for development preview routes only. */
export function QuizFixturePage({
  initialScene,
  flowKind = 'QUIZ',
}: {
  initialScene?: QuizFlowScene
  flowKind?: QuizFlowKind
}) {
  const isReview = flowKind === 'REVIEW'
  const initialQuestions = isReview
    ? quizFixtureQuestions.filter((question) =>
        ['question_2', 'question_3', 'question_4'].includes(question.questionId),
      )
    : quizFixtureQuestions
  const [conditions, setConditions] = useState(quizFixtureConditions)
  const [questions, setQuestions] = useState(initialQuestions)
  const [pendingEssayQuestionIds, setPendingEssayQuestionIds] = useState<string[]>([])
  const [essayOutcomes, setEssayOutcomes] = useState<Record<string, QuizResultOutcome>>({})
  const result = useMemo(
    () => createQuizFixtureResult(questions, undefined, essayOutcomes),
    [essayOutcomes, questions],
  )

  const generate = async (nextConditions: QuizConditions) => {
    const fixture = await resolveQuizFixtureGeneration(nextConditions)
    setQuestions(fixture.questions)
    setPendingEssayQuestionIds([])
    setEssayOutcomes({})
    return fixture.ready
  }

  return (
    <QuizFlowPage
      materialTitle="자료구조 핵심 개념"
      questions={questions}
      result={result}
      flowKind={flowKind}
      initialScene={isReview ? 'SOLVING' : initialScene}
      initialConditions={quizFixtureConditions}
      initialAnswers={isReview ? {} : quizFixtureAnswers}
      generationState={initialScene === 'READY' ? { status: 'READY', ready: quizFixtureReady } : undefined}
      callbacks={{
        onConditionsChange: setConditions,
        onGenerate: generate,
        onRetryGeneration: (_failure: QuizGenerationFailure) => generate(conditions),
        onRefreshGenerationStatus: () => generate(conditions),
        onSubmit: async (input) => {
          const submission = await resolveQuizFixtureSubmission(input, questions)
          setPendingEssayQuestionIds(submission.pendingEssayQuestionIds)
          setEssayOutcomes({})
          return submission
        },
        onLoadResult: () => result,
        onSaveEssayAssessment: async (input) => {
          const remainingIds = pendingEssayQuestionIds.filter((id) => id !== input.questionId)
          const saved = await resolveQuizFixtureEssayAssessment(input, remainingIds.length)
          setPendingEssayQuestionIds(remainingIds)
          setEssayOutcomes((current) => ({ ...current, [input.questionId]: input.assessment }))
          return saved
        },
        onUpdateShortAnswerOutcome: (input: {
          questionId: string
          outcome: QuizBinaryOutcome
        }) => resolveQuizFixtureCorrection(input, questions),
      }}
    />
  )
}
