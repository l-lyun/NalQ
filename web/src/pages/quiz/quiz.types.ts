export type QuizQuestionType = 'MULTIPLE_CHOICE' | 'FILL_BLANK' | 'SHORT_ANSWER' | 'ESSAY'

export type QuizDifficulty = 'EASY' | 'NORMAL' | 'HARD'

export type QuizMaxCount = 5 | 10 | 15

export type QuizConditions = {
  questionTypes: QuizQuestionType[]
  difficulty: QuizDifficulty
  maxCount: QuizMaxCount
}

export type ObjectiveQuestion = {
  id: string
  number: number
  type: 'MULTIPLE_CHOICE'
  topic: string
  prompt: string
  choices: { id: string; label: string }[]
}

export type FillBlankQuestion = {
  id: string
  number: number
  type: 'FILL_BLANK'
  topic: string
  prompt: string
  blanks: { id: string; label: string }[]
}

export type ShortAnswerQuestion = {
  id: string
  number: number
  type: 'SHORT_ANSWER'
  topic: string
  prompt: string
}

export type EssayQuestion = {
  id: string
  number: number
  type: 'ESSAY'
  topic: string
  prompt: string
}

export type QuizQuestion =
  | ObjectiveQuestion
  | FillBlankQuestion
  | ShortAnswerQuestion
  | EssayQuestion

export type QuizAnswer =
  | { type: 'MULTIPLE_CHOICE'; choiceId: string }
  | { type: 'FILL_BLANK'; values: Record<string, string> }
  | { type: 'SHORT_ANSWER'; value: string }
  | { type: 'ESSAY'; value: string }

export type QuizAnswers = Record<string, QuizAnswer | undefined>

export type QuizGenerationReady = {
  actualCount: number
  requestedCount: QuizMaxCount
  conditions: QuizConditions
}

export type QuizGenerationFailureKind =
  | 'REQUEST_FAILED'
  | 'STATUS_UNAVAILABLE'
  | 'SOURCE_INSUFFICIENT'
  | 'GENERATION_FAILED'

export type QuizGenerationFailure = {
  kind: QuizGenerationFailureKind
  retryable: boolean
  message?: string
}

export type QuizGenerationState =
  | { status: 'GENERATING' }
  | { status: 'READY'; ready: QuizGenerationReady }
  | { status: 'ERROR'; error: QuizGenerationFailure }

export type QuizBinaryOutcome = 'CORRECT' | 'INCORRECT'

export type QuizResultOutcome = QuizBinaryOutcome | 'PARTIAL'

export type QuizResultItem = {
  questionId: string
  number: number
  type: QuizQuestionType
  topic: string
  prompt: string
  answer: string
  correctAnswer: string
  outcome?: QuizResultOutcome
  keyPoints?: string[]
  explanation: string
  sourceExcerpt: string
  editable: boolean
  edited?: boolean
}

export type QuizResultSummary = {
  correctCount: number
  gradedCount: number
  essayCorrectCount: number
  essayPartialCount: number
  essayIncorrectCount: number
  reviewCount: number
}

export type QuizResult = {
  summary: QuizResultSummary
  items: QuizResultItem[]
}

export type QuizSubmitPayload = {
  answers: QuizAnswers
  unansweredQuestionIds: string[]
}

export type QuizAttemptStatus = 'SELF_ASSESSMENT_REQUIRED' | 'COMPLETED'

export type QuizSubmissionResult = {
  attemptId: string
  status: QuizAttemptStatus
  automaticGrading: {
    correctQuestionCount: number
    gradedQuestionCount: number
  }
  pendingEssayQuestionIds: string[]
  createdAt: string
}

export type QuizEssayAssessmentResult = {
  attemptId: string
  questionId: string
  assessment: QuizResultOutcome
  status: QuizAttemptStatus
  remainingSelfAssessmentCount: number
}

export type QuizPresentationCallbacks = {
  onConditionsChange?: (conditions: QuizConditions) => void
  onGenerate?: (conditions: QuizConditions) => void | Promise<QuizGenerationReady | void>
  onRetryGeneration?: (failure: QuizGenerationFailure) => void | Promise<QuizGenerationReady | void>
  onRefreshGenerationStatus?: () => void | Promise<QuizGenerationReady | void>
  onExitGeneration?: () => void
  onStartQuiz?: () => void
  onDeferQuiz?: () => void
  onAnswersChange?: (answers: QuizAnswers) => void
  onNavigateQuestion?: (questionId: string) => void
  onSubmit?: (payload: QuizSubmitPayload) => QuizSubmissionResult | Promise<QuizSubmissionResult>
  onSaveEssayAssessment?: (input: {
    attemptId: string
    questionId: string
    assessment: QuizResultOutcome
  }) => QuizEssayAssessmentResult | Promise<QuizEssayAssessmentResult>
  onExitQuiz?: () => void
  onUpdateShortAnswerOutcome?: (input: {
    questionId: string
    outcome: QuizBinaryOutcome
  }) => QuizResultSummary | Promise<QuizResultSummary>
  onResultExit?: () => void
}

export type QuizFlowScene =
  | 'CONDITIONS'
  | 'GENERATION'
  | 'READY'
  | 'SOLVING'
  | 'SUBMIT_ERROR'
  | 'SELF_ASSESSMENT'
  | 'RESULT'

export type QuizFlowPageProps = {
  materialTitle: string
  questions: QuizQuestion[]
  result: QuizResult
  initialScene?: QuizFlowScene
  initialConditions?: QuizConditions
  initialAnswers?: QuizAnswers
  generationState?: QuizGenerationState
  callbacks?: QuizPresentationCallbacks
}
