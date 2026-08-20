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

  return (
    <LearningPage
      reviewState={{ status: 'error', message: '복습 데이터 연동을 준비하고 있어요.' }}
      materialsState={{ status: 'error', message: '학습자료 데이터 연동을 준비하고 있어요.' }}
      callbacks={{ onNavigate: handleNavigate }}
    />
  )
}
