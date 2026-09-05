import { useLocation } from 'react-router-dom'

import { LearningCreatePage } from './LearningCreatePage'
import { LearningMaterialCreatePage } from './LearningMaterialCreatePage'
import {
  LearningManagementPage,
  LearningMaterialEditPage,
  LearningMaterialsPage,
  QuizManagementPage,
} from './LearningManagementPages'
import { resolveLearningRoute } from './learningRoutes'
import { NotionImportPage } from './NotionImportPage'

export function AuthenticatedLearningPage() {
  const route = resolveLearningRoute(useLocation().pathname)
  if (route.id === 'materials') return <LearningMaterialsPage />
  if (route.id === 'material-edit') return <LearningMaterialEditPage materialId={route.materialId} />
  if (route.id === 'material-create') return <LearningMaterialCreatePage />
  if (route.id === 'notion-import') return <NotionImportPage />
  if (route.id === 'quizzes') return <QuizManagementPage />
  if (route.id === 'new-quiz') return <LearningCreatePage />
  return <LearningManagementPage />
}
