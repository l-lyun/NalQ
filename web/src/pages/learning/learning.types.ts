import type {
  LearningMaterialDetail,
  LearningMaterialPage,
  LearningMaterialSummary,
} from '@/features/learning-material/api/learningMaterial.types'

export type { LearningMaterialDetail, LearningMaterialPage, LearningMaterialSummary }

export type LearningReviewSummary = {
  sourceAttemptId: string
  materialTitle: string
  completedAtLabel: string
  reviewQuestionCount: number
  activeReviewSessionId?: string
  completedQuestionCount?: number
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
  onNavigate?: (destination: LearningNavigationDestination) => void
  onStartReview?: (review: LearningReviewSummary) => void
  onOpenQuizConditions?: (material: Pick<LearningMaterialSummary, 'materialId' | 'title'>) => void
  onStartNotionImport?: () => void
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
  review?: LearningReviewSummary | null
  reviewState?: LearningSectionState<LearningReviewSummary | null>
  materialsState: LearningSectionState<LearningMaterialPage>
  materialsQuery: string
  materialsFetching?: boolean
  callbacks?: LearningPageCallbacks
}
