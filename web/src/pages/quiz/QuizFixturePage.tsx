import { useMemo, useState } from 'react'

import { QuizFlowPage } from './QuizFlowPage'
import {
  createQuizFixtureResult,
  quizFixtureAnswers,
  quizFixtureConditions,
  quizFixtureQuestions,
  resolveQuizFixtureCorrection,
  resolveQuizFixtureEssayAssessment,
  resolveQuizFixtureGeneration,
  resolveQuizFixtureSubmission,
} from './quiz.fixtures'
import type {
  QuizBinaryOutcome,
  QuizConditions,
  QuizEssayAssessmentResult,
  QuizFlowKind,
  QuizFlowScene,
  QuizGenerationFailure,
  QuizSubmissionResult,
  QuizSubmitPayload,
  QuizResultOutcome,
} from './quiz.types'

/**
 * Presentation-only mount for a dev preview route.
 * The delay and result changes live entirely in fixtures and must not be treated as an API contract.
 */
export function QuizFixturePage({
  initialScene,
  flowKind = 'QUIZ',
}: {
  initialScene?: QuizFlowScene
  flowKind?: QuizFlowKind
}) {
  const isReview = flowKind === 'REVIEW'
  const initialQuestions = isReview
    ? quizFixtureQuestions.filter((question) => ['q2', 'q3', 'q4'].includes(question.id))
    : quizFixtureQuestions
  const [conditions, setConditions] = useState(quizFixtureConditions)
  const [questions, setQuestions] = useState(initialQuestions)
  const [pendingEssayQuestionIds, setPendingEssayQuestionIds] = useState<string[]>([])
  const [essayOutcomes, setEssayOutcomes] = useState<Record<string, QuizResultOutcome>>({})
  const [submittedAnswers, setSubmittedAnswers] = useState<QuizSubmitPayload['answers']>()
  const result = useMemo(
    () => createQuizFixtureResult(questions, submittedAnswers, essayOutcomes),
    [essayOutcomes, questions, submittedAnswers],
  )

  const generate = async (nextConditions: QuizConditions) => {
    const fixture = await resolveQuizFixtureGeneration(nextConditions)
    setQuestions(fixture.questions)
    setPendingEssayQuestionIds([])
    setEssayOutcomes({})
    setSubmittedAnswers(undefined)
    return fixture.ready
  }

  const submit = async (payload: QuizSubmitPayload): Promise<QuizSubmissionResult> => {
    const submission = await resolveQuizFixtureSubmission(payload, questions)
    setPendingEssayQuestionIds(submission.pendingEssayQuestionIds)
    setEssayOutcomes({})
    setSubmittedAnswers(payload.answers)
    return submission
  }

  const saveEssayAssessment = async (input: {
    attemptId: string
    questionId: string
    assessment: 'CORRECT' | 'PARTIAL' | 'INCORRECT'
  }): Promise<QuizEssayAssessmentResult> => {
    const remainingIds = pendingEssayQuestionIds.filter(
      (questionId) => questionId !== input.questionId,
    )
    const saved = await resolveQuizFixtureEssayAssessment(input, remainingIds.length)
    setPendingEssayQuestionIds(remainingIds)
    setEssayOutcomes((current) => ({ ...current, [input.questionId]: input.assessment }))
    return saved
  }

  const updateShortAnswerOutcome = (input: {
    questionId: string
    outcome: QuizBinaryOutcome
  }) => resolveQuizFixtureCorrection(input, questions, essayOutcomes, submittedAnswers)

  return (
    <QuizFlowPage
      materialTitle="자료구조 핵심 개념"
      questions={questions}
      result={result}
      flowKind={flowKind}
      initialScene={isReview ? 'SOLVING' : initialScene}
      initialConditions={quizFixtureConditions}
      initialAnswers={isReview ? {} : quizFixtureAnswers}
      generationState={
        initialScene === 'READY'
          ? {
              status: 'READY',
              ready: {
                actualCount: Math.min(questions.length, quizFixtureConditions.maxCount),
                requestedCount: quizFixtureConditions.maxCount,
                conditions: quizFixtureConditions,
              },
            }
          : undefined
      }
      callbacks={{
        onConditionsChange: setConditions,
        onGenerate: generate,
        onRetryGeneration: (_failure: QuizGenerationFailure) => generate(conditions),
        onRefreshGenerationStatus: () => generate(conditions),
        onSubmit: submit,
        onSaveEssayAssessment: saveEssayAssessment,
        onUpdateShortAnswerOutcome: updateShortAnswerOutcome,
      }}
    />
  )
}
