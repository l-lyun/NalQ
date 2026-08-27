import { useLocation, useNavigate } from 'react-router-dom'

import { QuizFixturePage } from './QuizFixturePage'

function useMockRouteContext(defaultTitle: string) {
  const navigate = useNavigate()
  const location = useLocation()
  const routeTitle = (location.state as { materialTitle?: string } | null)?.materialTitle

  return {
    materialTitle: `${routeTitle ?? defaultTitle} · 개발용 샘플`,
    exit: () => navigate('/learning'),
  }
}

export function QuizMockMaterialRoutePage() {
  const { materialTitle, exit } = useMockRouteContext('학습자료')
  return <QuizFixturePage materialTitle={materialTitle} onExit={exit} />
}

export function QuizMockSetRoutePage() {
  const { materialTitle, exit } = useMockRouteContext('학습자료')
  return <QuizFixturePage materialTitle={materialTitle} initialScene="READY" onExit={exit} />
}

export function QuizMockAttemptResultRoutePage() {
  const { materialTitle, exit } = useMockRouteContext('학습자료')
  return <QuizFixturePage materialTitle={materialTitle} initialScene="RESULT" onExit={exit} />
}

export function QuizMockReviewRoutePage() {
  const { materialTitle, exit } = useMockRouteContext('최근 퀴즈')
  return <QuizFixturePage materialTitle={materialTitle} flowKind="REVIEW" onExit={exit} />
}
