export type QuizQuestionType = 'MULTIPLE_CHOICE' | 'FILL_IN_THE_BLANK' | 'SHORT_ANSWER' | 'ESSAY'
export type QuizDifficulty = 'EASY' | 'NORMAL' | 'HARD'
export type QuizMaxQuestionCount = 5 | 10 | 15 | 20
export type QuizRequestedConfig = {
  selectedTypes: QuizQuestionType[]
  difficulty: QuizDifficulty
  maxQuestionCount: QuizMaxQuestionCount
}
export type QuizConditions = QuizRequestedConfig & { generationPrompt?: string }

type QuizQuestionBase = { questionId: string; number: number; topic: string; prompt: string }
export type ObjectiveQuestion = QuizQuestionBase & {
  type: 'MULTIPLE_CHOICE'
  choices: { choiceId: string; text: string }[]
}
export type FillBlankQuestion = QuizQuestionBase & {
  type: 'FILL_IN_THE_BLANK'
  blanks: { blankId: string; number: number }[]
}
export type ShortAnswerQuestion = QuizQuestionBase & { type: 'SHORT_ANSWER' }
export type EssayQuestion = QuizQuestionBase & { type: 'ESSAY' }
export type QuizQuestion = ObjectiveQuestion | FillBlankQuestion | ShortAnswerQuestion | EssayQuestion

/** Open-screen memory only. Never persisted or sent directly. */
export type QuizAnswer =
  | { type: 'MULTIPLE_CHOICE'; selectedChoiceId: string }
  | { type: 'FILL_IN_THE_BLANK'; blankAnswers: Record<string, string> }
  | { type: 'SHORT_ANSWER'; text: string }
  | { type: 'ESSAY'; text: string }
export type QuizAnswers = Record<string, QuizAnswer | undefined>
export type QuizResponse =
  | { questionId: string; selectedChoiceId: string }
  | { questionId: string; blankAnswers: { blankId: string; answer: string }[] }
  | { questionId: string; text: string }
export type QuizSubmissionPayload = { responses: QuizResponse[] }

export type QuizGenerationReady = {
  actualCount: number
  includedTypes: QuizQuestionType[]
  requestedConfig?: QuizRequestedConfig
}
export type QuizGenerationFailureKind =
  | 'REQUEST_FAILED' | 'STATUS_UNAVAILABLE' | 'SOURCE_INSUFFICIENT' | 'GENERATION_FAILED'
export type QuizGenerationFailure = { kind: QuizGenerationFailureKind; retryable: boolean; message?: string }
export type QuizGenerationState =
  | { status: 'GENERATING'; requestedConfig?: QuizRequestedConfig }
  | { status: 'READY'; ready: QuizGenerationReady }
  | { status: 'ERROR'; error: QuizGenerationFailure }

export type QuizBinaryOutcome = 'CORRECT' | 'INCORRECT'
export type QuizResultOutcome = QuizBinaryOutcome | 'PARTIAL'
export type QuizAttemptStatus = 'SELF_ASSESSMENT_REQUIRED' | 'COMPLETED'
export type ReviewSessionStatus = 'SOLVING' | QuizAttemptStatus
export type QuizResultItem = {
  questionId: string
  number: number
  type: QuizQuestionType
  topic: string
  prompt: string
  answer: string
  correctAnswer: string
  outcome: QuizResultOutcome
  keyPoints?: string[]
  explanation: string
  sourceExcerpt: string
  editable: boolean
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
  kind: 'MAIN' | 'REVIEW'
  status: QuizAttemptStatus | ReviewSessionStatus
  reviewAvailable: boolean
  summary: QuizResultSummary
  items: QuizResultItem[]
}
export type QuizSubmissionResult = {
  attemptId: string
  status: QuizAttemptStatus
  automaticGrading: { correctQuestionCount: number; gradedQuestionCount: number }
  pendingEssayQuestionIds: string[]
  createdAt: string
}
export type ReviewSubmissionResult = {
  reviewSessionId: string
  status: QuizAttemptStatus
  automaticGrading: { correctQuestionCount: number; gradedQuestionCount: number }
  pendingEssayQuestionIds: string[]
  submittedAt: string
}
export type QuizEssayAssessmentResult = {
  attemptId?: string
  questionId: string
  assessment: QuizResultOutcome
  reviewStatus?: 'RESOLVED' | 'UNRESOLVED'
  status: QuizAttemptStatus
  remainingSelfAssessmentCount: number
}

export type QuizPresentationCallbacks = {
  onConditionsChange?: (conditions: QuizConditions) => void
  onGenerate?: (conditions: QuizConditions) => void | Promise<QuizGenerationReady | void>
  onGenerationActive?: () => void
  onRetryGeneration?: (failure: QuizGenerationFailure) => void | Promise<QuizGenerationReady | void>
  onRefreshGenerationStatus?: () => void | Promise<QuizGenerationReady | void>
  onExitGeneration?: () => void
  onStartQuiz?: () => void
  onDeferQuiz?: () => void
  onAnswersChange?: (answers: QuizAnswers) => void
  onNavigateQuestion?: (questionId: string) => void
  onSubmit?: (input: { attemptId?: string; payload: QuizSubmissionPayload }) =>
    | QuizSubmissionResult | ReviewSubmissionResult
    | Promise<QuizSubmissionResult | ReviewSubmissionResult>
  onLoadResult?: (resourceId: string) => QuizResult | Promise<QuizResult>
  onSaveEssayAssessment?: (input: {
    resourceId: string
    questionId: string
    assessment: QuizResultOutcome
  }) => QuizEssayAssessmentResult | Promise<QuizEssayAssessmentResult>
  onExitQuiz?: () => void
  onUpdateGradingOutcome?: (input: {
    questionId: string
    outcome: QuizBinaryOutcome
  }) => QuizResult | Promise<QuizResult>
  onCompleted?: (resourceId: string) => void
  onResultExit?: () => void
  onStartReview?: (resourceId: string) => void | Promise<void>
  onGoHome?: () => void
}

export type QuizFlowScene =
  | 'CONDITIONS' | 'GENERATION' | 'READY' | 'SOLVING' | 'SUBMIT_ERROR'
  | 'SELF_ASSESSMENT' | 'RESULT'
export type QuizFlowKind = 'QUIZ' | 'REVIEW'
export type QuizFlowPageProps = {
  materialTitle: string
  questions: QuizQuestion[]
  result: QuizResult
  flowKind?: QuizFlowKind
  initialScene?: QuizFlowScene
  initialConditions?: QuizConditions
  initialAnswers?: QuizAnswers
  generationState?: QuizGenerationState
  initialResourceId?: string
  initialPendingEssayQuestionIds?: string[]
  callbacks?: QuizPresentationCallbacks
}
