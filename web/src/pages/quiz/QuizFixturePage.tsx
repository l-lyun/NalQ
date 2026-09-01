import { useMemo, useState } from 'react'

import { QuizFlowPage } from './QuizFlowPage'
import {
  createQuizFixtureResult,
  quizFixtureAnswers,
  quizFixtureConditions,
  quizFixtureQuestions,
  quizFixtureReady,
  resolveQuizFixtureGradingOverride,
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
  materialTitle = '자료구조 핵심 개념',
  onExit,
  onHome,
  onStartReview,
}: {
  initialScene?: QuizFlowScene
  flowKind?: QuizFlowKind
  materialTitle?: string
  onExit?: () => void
  onHome?: () => void
  onStartReview?: () => void
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
  const [gradingOverrides, setGradingOverrides] = useState<Record<string, QuizBinaryOutcome>>({})
  const result = useMemo(
    () => createQuizFixtureResult(
      questions,
      undefined,
      { ...essayOutcomes, ...gradingOverrides },
    ),
    [essayOutcomes, gradingOverrides, questions],
  )

  const generate = async (nextConditions: QuizConditions) => {
    const fixture = await resolveQuizFixtureGeneration(nextConditions)
    setQuestions(fixture.questions)
    setPendingEssayQuestionIds([])
    setEssayOutcomes({})
    setGradingOverrides({})
    return fixture.ready
  }

  return (
    <QuizFlowPage
      materialTitle={materialTitle}
      questions={questions}
      result={result}
      flowKind={flowKind}
      initialScene={isReview ? 'SOLVING' : initialScene}
      initialResourceId={isReview ? 'fixture-review-session' : 'fixture-attempt'}
      initialConditions={quizFixtureConditions}
      initialAnswers={isReview ? {} : quizFixtureAnswers}
      generationState={initialScene === 'READY' ? { status: 'READY', ready: quizFixtureReady } : undefined}
      callbacks={{
        onConditionsChange: setConditions,
        onGenerate: generate,
        onRetryGeneration: (_failure: QuizGenerationFailure) => generate(conditions),
        onRefreshGenerationStatus: () => generate(conditions),
        onExitGeneration: onExit,
        onExitQuiz: onExit,
        onDeferQuiz: onExit,
        onResultExit: onExit,
        onGoHome: onHome,
        onStartReview,
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
        onUpdateGradingOutcome: isReview
          ? undefined
          : async (input: { questionId: string; outcome: QuizBinaryOutcome }) => {
              const updated = await resolveQuizFixtureGradingOverride(
                input,
                questions,
                { ...essayOutcomes, ...gradingOverrides },
              )
              setGradingOverrides((current) => ({
                ...current,
                [input.questionId]: input.outcome,
              }))
              return updated
            },
      }}
    />
  )
}
