export type LearningMaterialSource = 'PASTE' | 'NOTION'

export type LearningMaterial = {
  id: string
  title: string
  body: string
  source: LearningMaterialSource
  updatedAtLabel: string
  generating: boolean
}

export type LearningReviewSummary = {
  sourceAttemptId: string
  materialTitle: string
  completedAtLabel: string
  reviewQuestionCount: number
  activeReviewSessionId?: string
  completedQuestionCount?: number
}

export type LearningNavigationDestination = 'home' | 'learning' | 'profile'

export type LearningMaterialDraft = Pick<LearningMaterial, 'title' | 'body'>

export type LearningMaterialUpdate = {
  title: string
  body?: string
}

export type LearningSectionState<T> =
  | { status: 'loading' }
  | { status: 'ready'; data: T }
  | { status: 'error'; message: string }

export type LearningPageCallbacks = {
  onNavigate?: (destination: LearningNavigationDestination) => void
  onStartReview?: (review: LearningReviewSummary) => void
  onOpenQuizConditions?: (material: LearningMaterial) => void
  onStartNotionImport?: () => void
  onCreateMaterial?: (draft: LearningMaterialDraft) => void
  onUpdateMaterial?: (materialId: string, update: LearningMaterialUpdate) => void
  onRetryReview?: () => void
  onRetryMaterials?: () => void
}

export type LearningPageProps = {
  initialMaterials?: LearningMaterial[]
  review?: LearningReviewSummary | null
  reviewState?: LearningSectionState<LearningReviewSummary | null>
  materialsState?: LearningSectionState<LearningMaterial[]>
  callbacks?: LearningPageCallbacks
}
