import { useLocation, useNavigate } from 'react-router-dom'

import { QuizFixturePage } from './QuizFixturePage'

function useMockRouteContext(defaultTitle: string) {
  const navigate = useNavigate()
  const location = useLocation()
  const routeTitle = (location.state as { materialTitle?: string } | null)?.materialTitle

  return {
    materialTitle: `${routeTitle ?? defaultTitle} · 개발용 샘플`,
    exit: () => navigate('/learning'),
    home: () => navigate('/'),
    startReview: () => navigate('/review'),
  }
}

export function QuizMockMaterialRoutePage() {
  const { materialTitle, exit, home, startReview } = useMockRouteContext('학습자료')
  return <QuizFixturePage materialTitle={materialTitle} onExit={exit} onHome={home} onStartReview={startReview} />
}

export function QuizMockSetRoutePage() {
  const { materialTitle, exit, home, startReview } = useMockRouteContext('학습자료')
  return <QuizFixturePage materialTitle={materialTitle} initialScene="READY" onExit={exit} onHome={home} onStartReview={startReview} />
}

export function QuizMockAttemptResultRoutePage() {
  const { materialTitle, exit, home, startReview } = useMockRouteContext('학습자료')
  return <QuizFixturePage materialTitle={materialTitle} initialScene="RESULT" onExit={exit} onHome={home} onStartReview={startReview} />
}

export function QuizMockReviewRoutePage() {
  const { materialTitle, exit, home, startReview } = useMockRouteContext('최근 퀴즈')
  return <QuizFixturePage materialTitle={materialTitle} flowKind="REVIEW" onExit={exit} onHome={home} onStartReview={startReview} />
}
