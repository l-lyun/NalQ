import type {
  QuizAttemptStatus,
  QuizBinaryOutcome,
  QuizDifficulty,
  QuizQuestion,
  QuizQuestionType,
  QuizRequestedConfig,
  QuizResultOutcome,
  QuizSubmissionPayload,
  ReviewSessionStatus,
} from '@/pages/quiz/quiz.types'

export type CreateQuizSetRequest = QuizRequestedConfig
export type CreateQuizSetResponse = {
  quizSetId: string
  materialId: string
  status: 'GENERATING'
  pollAfterSeconds: number
  requestedConfig: QuizRequestedConfig
  createdAt: string
}
export type ActiveQuizSet = {
  quizSetId: string
  materialId: string
  status: 'GENERATING'
  pollAfterSeconds: number
}
export type QuizSetFailure = {
  code: 'SOURCE_INSUFFICIENT' | 'GENERATION_FAILED'
  message: string
  retryable: boolean
}
export type QuizSetState =
  | { quizSetId: string; materialId: string; status: 'GENERATING'; pollAfterSeconds: number }
  | { quizSetId: string; materialId: string; status: 'READY'; questions: QuizQuestion[] }
  | { quizSetId: string; materialId: string; status: 'FAILED'; failure: QuizSetFailure }

export type QuizSubmissionResponse = {
  attemptId: string
  status: QuizAttemptStatus
  automaticGrading: { correctQuestionCount: number; gradedQuestionCount: number }
  pendingEssayQuestionIds: string[]
  createdAt: string
}
export type PendingSelfAssessment = {
  attemptId: string
  quizSetId: string
  status: 'SELF_ASSESSMENT_REQUIRED'
  pendingEssayQuestionIds: string[]
}
export type EssayAssessmentResponse = {
  attemptId: string
  questionId: string
  assessment: QuizResultOutcome
  status: QuizAttemptStatus
  remainingSelfAssessmentCount: number
}

type QuestionResultBase = {
  questionId: string
  number: number
  type: QuizQuestionType
  topic: string
  prompt: string
  outcome?: QuizResultOutcome
  explanation: string
  sourceExcerpt: string
}
export type QuestionResult = QuestionResultBase & {
  choices?: { choiceId: string; text: string }[]
  blanks?: { blankId: string; number: number }[]
  response:
    | { selectedChoiceId: string }
    | { blankAnswers: { blankId: string; answer: string }[] }
    | { answer: string }
    | null
  representativeAnswer?:
    | { selectedChoiceId: string }
    | { blankAnswers: { blankId: string; answer: string }[] }
    | { answer: string }
    | { modelAnswer: string; keyPoints: string[] }
}
export type QuizResultResponse = {
  attemptId: string
  quizSetId: string
  status: QuizAttemptStatus
  summary: {
    scoredGrading: { correctQuestionCount: number; gradedQuestionCount: number }
    essaySelfAssessment: { correctCount: number; partialCount: number; incorrectCount: number }
    reviewQuestionCount: number
  }
  questionResults: QuestionResult[]
}

export type LatestReview = {
  sourceAttemptId: string | null
  quizSetId: string | null
  attemptNumber: number | null
  materialTitle: string | null
  completedAt: string | null
  totalQuestionCount: number
  reviewQuestionCount: number
  activeReviewSessionId: string | null
}
export type ReviewSession = {
  reviewSessionId: string
  sourceAttemptId: string
  status: ReviewSessionStatus
  reviewQuestionCount?: number
  pendingEssayQuestionIds: string[]
  questions?: QuizQuestion[]
}
export type ReviewSessionEnvelope = { reviewSession: ReviewSession }
export type ReviewSubmissionResponse = {
  reviewSessionId: string
  status: QuizAttemptStatus
  automaticGrading: { correctQuestionCount: number; gradedQuestionCount: number }
  pendingEssayQuestionIds: string[]
  submittedAt: string
}
export type ReviewEssayAssessmentResponse = {
  questionId: string
  assessment: QuizResultOutcome
  reviewStatus: 'RESOLVED' | 'UNRESOLVED'
  status: QuizAttemptStatus
  remainingSelfAssessmentCount: number
}
export type ReviewResultResponse = {
  reviewSessionId: string
  sourceAttemptId: string
  status: QuizAttemptStatus
  // The contract describes this summary semantically but does not define its JSON field names.
  summary: unknown
  questionResults: QuestionResult[]
}

export type { QuizBinaryOutcome, QuizDifficulty, QuizSubmissionPayload }
