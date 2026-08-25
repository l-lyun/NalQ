import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { getLatestReview } from '@/features/quiz/api/quiz.api'
import { quizApiEnabled } from '@/features/quiz/model/quizFeature'

import { LearningPage } from './LearningPage'
import type { LearningMaterial, LearningNavigationDestination } from './learning.types'

export function AuthenticatedLearningPage() {
  const navigate = useNavigate()
  const reviewQuery = useQuery({
    queryKey: ['private', 'quiz-review', 'latest'],
    queryFn: ({ signal }) => getLatestReview(signal),
    enabled: quizApiEnabled,
  })

  const handleNavigate = (destination: LearningNavigationDestination) => {
    if (destination === 'home') {
      navigate('/')
    }

    // 학습은 현재 경로라 유지하고, 프로필은 라우트가 생길 때 연결한다.
  }

  return (
    <LearningPage
      reviewState={
        !quizApiEnabled
          ? { status: 'error', message: '복습 데이터 연동을 준비하고 있어요.' }
          : reviewQuery.isPending
          ? { status: 'loading' }
          : reviewQuery.isError
            ? { status: 'error', message: '복습 정보를 불러오지 못했어요.' }
            : reviewQuery.data && reviewQuery.data.sourceAttemptId
              ? {
                  status: 'ready',
                  data: {
                    sourceAttemptId: reviewQuery.data.sourceAttemptId,
                    materialTitle: '최근 완료한 퀴즈',
                    completedAtLabel: `${reviewQuery.data.attemptNumber ?? 1}회차`,
                    reviewQuestionCount: reviewQuery.data.reviewQuestionCount,
                    activeReviewSessionId: reviewQuery.data.activeReviewSessionId ?? undefined,
                  },
                }
              : { status: 'ready', data: null }
      }
      materialsState={{ status: 'error', message: '학습자료 데이터 연동을 준비하고 있어요.' }}
      callbacks={{
        onNavigate: handleNavigate,
        ...(quizApiEnabled
          ? {
              onRetryReview: () => void reviewQuery.refetch(),
              onStartReview: () => navigate('/review'),
              onOpenQuizConditions: (material: LearningMaterial) =>
                navigate(`/learning/${material.id}/quiz`, {
                  state: { materialTitle: material.title },
                }),
            }
          : {}),
      }}
    />
  )
}
