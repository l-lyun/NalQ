import { useNavigate } from 'react-router-dom'

import { LearningPage } from './LearningPage'
import type { LearningNavigationDestination } from './learning.types'

export function AuthenticatedLearningPage() {
  const navigate = useNavigate()

  const handleNavigate = (destination: LearningNavigationDestination) => {
    if (destination === 'home') {
      navigate('/')
    }

    // 학습은 현재 경로라 유지하고, 프로필은 라우트가 생길 때 연결한다.
  }

  return <LearningPage callbacks={{ onNavigate: handleNavigate }} />
}
