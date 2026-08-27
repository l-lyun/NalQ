import { ActionButton, Text, VStack } from '@seed-design/react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'

import { useLogoutMutation } from '@/features/auth/model/auth.mutations'
import { useCurrentUser } from '@/features/auth/model/auth.queries'
import {
  quizApiEnabled,
  quizMockEnabled,
} from '@/features/quiz/model/quizFeature'
import { learningReviewFixture } from '@/pages/learning/learning.fixtures'
import { latestReviewQueryOptions } from '@/features/quiz/model/quizQueries'

import { homeReadyFixture } from './home.fixtures'
import { HomePage } from './HomePage'
import type { HomeNextAction, HomePageProps } from './home.types'

export function AuthenticatedHomePage() {
  const navigate = useNavigate()
  const currentUser = useCurrentUser()
  const logout = useLogoutMutation()
  const reviewQuery = useQuery({
    ...latestReviewQueryOptions(),
    enabled: quizApiEnabled && Boolean(currentUser.data),
  })

  if (currentUser.isError && !currentUser.data) {
    return (
      <VStack minHeight="100dvh" align="center" justify="center" bg="bg.layerDefault" gap="x3">
        <Text role="alert" textStyle="t5Regular" color="fg.critical">
          사용자 정보를 불러오지 못했어요.
        </Text>
        <ActionButton
          type="button"
          size="medium"
          variant="neutralWeak"
          loading={currentUser.isFetching}
          disabled={currentUser.isFetching}
          onClick={() => void currentUser.refetch()}
        >
          다시 시도
        </ActionButton>
      </VStack>
    )
  }

  if (!currentUser.data) {
    return (
      <VStack minHeight="100dvh" align="center" justify="center" bg="bg.layerDefault" gap="x3">
        <Text role="status" textStyle="t5Regular" color="fg.neutralMuted">
          사용자 정보를 불러오고 있어요.
        </Text>
      </VStack>
    )
  }

  const openLearning = () => navigate('/learning')
  const openReview = (materialTitle?: string | null) =>
    navigate('/review', {
      state: materialTitle ? { materialTitle } : undefined,
    })
  const mockReview = learningReviewFixture
  const apiReview = reviewQuery.data
  const hasApiReview = Boolean(
    apiReview?.sourceAttemptId &&
      apiReview.quizSetId &&
      apiReview.materialTitle &&
      apiReview.reviewQuestionCount > 0,
  )
  const hasReview = quizMockEnabled || hasApiReview
  const reviewCount = quizMockEnabled
    ? mockReview.reviewQuestionCount
    : (apiReview?.reviewQuestionCount ?? 0)
  const reviewTitle = quizMockEnabled ? mockReview.materialTitle : apiReview?.materialTitle
  const activeReviewSessionId = quizMockEnabled
    ? mockReview.activeReviewSessionId
    : apiReview?.activeReviewSessionId
  const nextAction: HomeNextAction | undefined = hasReview
    ? {
        title: activeReviewSessionId
          ? `${reviewTitle ?? '최근 퀴즈'} 복습을 이어서 풀어보세요`
          : `다시 확인할 문제가 ${reviewCount}개 있어요`,
        description: activeReviewSessionId
          ? '진행 중인 복습을 첫 문제부터 다시 이어갈 수 있어요.'
          : '최근에 완료한 퀴즈에서 아직 해결하지 못한 문제를 다시 풀어보세요.',
        context: `${reviewTitle ?? '최근 퀴즈'} · ${reviewCount}문제`,
        action: {
          label: activeReviewSessionId ? '복습 이어서 풀기' : '복습 시작',
          onClick: () => openReview(reviewTitle),
        },
      }
    : undefined
  const review: HomePageProps['review'] = reviewQuery.isError && quizApiEnabled
    ? {
        status: 'error',
        message: '복습 정보를 불러오지 못했어요.',
        onRetry: () => void reviewQuery.refetch(),
      }
    : hasReview
      ? {
          status: 'ready',
          data: {
            id: activeReviewSessionId ?? apiReview?.sourceAttemptId ?? mockReview.sourceAttemptId,
            title: activeReviewSessionId
              ? `복습 이어서 풀기 · ${reviewCount}문제`
              : `복습할 문제 ${reviewCount}개`,
            detail: reviewTitle ?? '최근 완료한 퀴즈',
            onClick: () => openReview(reviewTitle),
          },
        }
      : {
          status: 'empty',
          message: '지금 복습할 문제는 없어요. 새 문제를 풀면 여기에서 다시 확인할 수 있어요.',
        }

  const homeFixture = {
    ...homeReadyFixture,
    status: quizApiEnabled && reviewQuery.isPending ? 'loading' : 'ready',
    nextAction,
    review,
    recentMaterials: homeReadyFixture.recentMaterials.status === 'ready'
      ? {
          ...homeReadyFixture.recentMaterials,
          data: homeReadyFixture.recentMaterials.data.map((item) => ({
            ...item,
            onClick: openLearning,
          })),
        }
      : homeReadyFixture.recentMaterials,
    studyMethods: homeReadyFixture.studyMethods.map((item) => ({
      ...item,
      onClick: openLearning,
    })),
    recommendationWarning: reviewQuery.isError && quizApiEnabled
      ? {
          title: '추천을 완성하지 못했어요',
          description: '복습 상태를 확인하지 못했어요. 새 학습은 계속 시작할 수 있어요.',
          onRetry: () => void reviewQuery.refetch(),
          onStartLearning: openLearning,
        }
      : undefined,
    onViewAllReviews: openLearning,
    onViewAllMaterials: openLearning,
    onRetryAll: () => {
      void currentUser.refetch()
      if (quizApiEnabled) void reviewQuery.refetch()
    },
  } satisfies HomePageProps

  return (
    <HomePage
      {...homeFixture}
      navigation={homeReadyFixture.navigation.map((item) =>
        item.id === 'learning' ? { ...item, onClick: () => navigate('/learning') } : item,
      )}
      session={{
        email: currentUser.data.email,
        logoutPending: logout.isPending,
        onLogout: () => logout.mutate(),
      }}
    />
  )
}
