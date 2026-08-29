import { useLocation } from 'react-router-dom'

import { LearningCreatePage } from './LearningCreatePage'
import {
  LearningManagementPage,
  LearningMaterialEditPage,
  LearningMaterialsPage,
  QuizManagementPage,
} from './LearningManagementPages'
import { resolveLearningRoute } from './learningRoutes'

export function AuthenticatedLearningPage() {
  const route = resolveLearningRoute(useLocation().pathname)
  if (route.id === 'materials') return <LearningMaterialsPage />
  if (route.id === 'material-edit') return <LearningMaterialEditPage />
  if (route.id === 'quizzes') return <QuizManagementPage />
  if (route.id === 'new-quiz') return <LearningCreatePage />
  return <LearningManagementPage />
}
