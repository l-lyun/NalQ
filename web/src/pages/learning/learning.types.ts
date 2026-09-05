import type {
  LearningMaterialDetail,
  LearningMaterialPage,
  LearningMaterialSummary,
} from '@/features/learning-material/api/learningMaterial.types'

export type { LearningMaterialDetail, LearningMaterialPage, LearningMaterialSummary }

export type LearningReviewSummary = {
  sourceAttemptId: string
  quizSetId: string
  materialTitle: string
  completedAt: string
  totalQuestionCount: number
  attemptNumber: number
  reviewQuestionCount: number
  activeReviewSessionId?: string
}

export type LearningNavigationDestination = 'home' | 'learning' | 'profile'

export type LearningMaterialDraft = {
  title: string
  body: string
}

export type LearningSectionState<T> =
  | { status: 'loading' }
  | { status: 'ready'; data: T }
  | { status: 'error'; message: string; data?: T }

export type LearningPageCallbacks = {
  onExit?: () => void
  onNavigate?: (destination: LearningNavigationDestination) => void
  onStartReview?: (review: LearningReviewSummary) => void
  onRestartQuiz?: (review: LearningReviewSummary) => void
  onOpenQuizConditions?: (material: Pick<LearningMaterialSummary, 'materialId' | 'title'>) => void
  onStartNotionImport?: () => void
  onStartDirectInput?: () => void
  onCreateMaterial?: (
    draft: LearningMaterialDraft,
  ) => Promise<Pick<LearningMaterialSummary, 'materialId' | 'title'>>
  onLoadMaterialDetail?: (materialId: string) => Promise<LearningMaterialDetail>
  onMaterialsQueryChange?: (query: string) => void
  onMaterialsPageChange?: (page: number) => void
  onRetryReview?: () => void
  onRetryMaterials?: () => void
}

export type LearningPageProps = {
  initialScreen?: 'main' | 'new-quiz'
  review?: LearningReviewSummary | null
  reviewState?: LearningSectionState<LearningReviewSummary | null>
  materialsState: LearningSectionState<LearningMaterialPage>
  materialsQuery: string
  materialsFetching?: boolean
  callbacks?: LearningPageCallbacks
}
