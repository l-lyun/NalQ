export { LearningPage } from './LearningPage'
export { AuthenticatedLearningPage } from './AuthenticatedLearningPage'
export { LearningCreatePage } from './LearningCreatePage'
export {
  LearningManagementPage,
  LearningMaterialEditPage,
  LearningMaterialsPage,
  QuizManagementPage,
} from './LearningManagementPages'

export const learningManagementRoutePaths = [
  '/learning',
  '/learning/materials',
  '/learning/materials/',
  '/learning/materials/:materialId',
  '/learning/quizzes',
  '/learning/new',
] as const
export { learningMaterialFixtures, learningReviewFixture } from './learning.fixtures'
export { countUnicodeCodePoints } from './learning.text'
export type {
  LearningMaterialDetail,
  LearningMaterialDraft,
  LearningMaterialPage,
  LearningMaterialSummary,
  LearningNavigationDestination,
  LearningPageCallbacks,
  LearningPageProps,
  LearningReviewSummary,
  LearningSectionState,
} from './learning.types'
