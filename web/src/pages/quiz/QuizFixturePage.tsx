import { QuizFlowPage } from './QuizFlowPage'
import {
  quizFixtureAnswers,
  quizFixtureConditions,
  quizFixtureQuestions,
  quizFixtureResult,
  resolveQuizFixtureCorrection,
  resolveQuizFixtureGeneration,
} from './quiz.fixtures'
import type { QuizFlowScene } from './quiz.types'

/**
 * Presentation-only mount for a dev preview route.
 * The delay and result changes live entirely in fixtures and must not be treated as an API contract.
 */
export function QuizFixturePage({ initialScene }: { initialScene?: QuizFlowScene }) {
  return (
    <QuizFlowPage
      materialTitle="자료구조 핵심 개념"
      questions={quizFixtureQuestions}
      result={quizFixtureResult}
      initialScene={initialScene}
      initialConditions={quizFixtureConditions}
      initialAnswers={quizFixtureAnswers}
      generationState={
        initialScene === 'READY'
          ? {
              status: 'READY',
              ready: {
                actualCount: quizFixtureQuestions.length,
                requestedCount: quizFixtureConditions.maxCount,
                conditions: quizFixtureConditions,
              },
            }
          : undefined
      }
      callbacks={{
        onGenerate: resolveQuizFixtureGeneration,
        onRetryGeneration: resolveQuizFixtureGeneration,
        onRefreshGenerationStatus: resolveQuizFixtureGeneration,
        onUpdateShortAnswerOutcome: resolveQuizFixtureCorrection,
      }}
    />
  )
}
