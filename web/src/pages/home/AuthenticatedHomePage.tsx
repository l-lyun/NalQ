import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'

import { useCurrentUser } from '@/features/auth/model/auth.queries'
import { learningMaterialQueryOptions } from '@/features/learning-material/api/learningMaterial.api'
import { quizApiEnabled, quizMockEnabled } from '@/features/quiz/model/quizFeature'
import { latestReviewQueryOptions } from '@/features/quiz/model/quizQueries'

import { HomePage } from './HomePage'
import { homeVisitQueryOptions } from './homeVisit.adapter'
import type { HomeNextAction, HomePageProps } from './home.types'

const mockReview = {
  sourceAttemptId: 'mock-source-attempt',
  activeReviewSessionId: null,
  quizTitle: '운영체제 중간고사 대비',
  materialTitle: '운영체제 핵심 정리',
  reviewQuestionCount: 4,
} as const

export function AuthenticatedHomePage() {
  const navigate = useNavigate()
  const currentUser = useCurrentUser()
  const visitQuery = useQuery({ ...homeVisitQueryOptions, enabled: Boolean(currentUser.data) })
  const materialsQuery = useQuery(learningMaterialQueryOptions.list({ page: 1, size: 3 }))
  const reviewQuery = useQuery({
    ...latestReviewQueryOptions(),
    enabled: quizApiEnabled && Boolean(currentUser.data),
  })

  const openReview = (materialTitle?: string | null) =>
    navigate('/review', { state: materialTitle ? { materialTitle } : undefined })
  const apiReview = reviewQuery.data as
    | (NonNullable<typeof reviewQuery.data> & { quizTitle?: string | null })
    | undefined
  const selectedReview = quizMockEnabled ? mockReview : apiReview
  const hasReview = Boolean(
    selectedReview?.sourceAttemptId &&
      selectedReview.quizTitle &&
      (selectedReview.activeReviewSessionId || selectedReview.reviewQuestionCount > 0),
  )
  const recentMaterial = materialsQuery.data?.items[0]
  const nextAction = createNextAction({
    selectedReview,
    hasReview,
    recentMaterial,
    materialsResolved: materialsQuery.isSuccess,
    navigate,
    openReview,
  })
  const review = createReviewState({
    apiReview,
    selectedReview,
    hasReview,
    apiError: reviewQuery.isError && quizApiEnabled,
    openReview,
    retry: () => void reviewQuery.refetch(),
  })
  const recentMaterials: HomePageProps['recentMaterials'] = materialsQuery.isPending
    ? { status: 'empty', message: '' }
    : materialsQuery.isError
      ? { status: 'error', message: '최근 학습자료를 불러오지 못했어요.', onRetry: () => void materialsQuery.refetch() }
      : materialsQuery.data.items.length === 0
        ? { status: 'empty', message: '최근 학습자료가 없어요.' }
        : {
            status: 'ready',
            data: materialsQuery.data.items.slice(0, 3).map((material) => ({
              id: material.materialId,
              title: material.title,
              detail: formatMaterialDetail(material.sourceType, material.updatedAt),
              onClick: () => navigate(`/learning/materials/${material.materialId}`),
            })),
          }
  const recommendationQueriesFailed = materialsQuery.isError || (reviewQuery.isError && quizApiEnabled)
  const recommendationWarning = recommendationQueriesFailed
    ? {
        title: '추천을 완성하지 못했어요',
        description: '최근 학습 상태를 모두 확인하지 못했어요. 새 학습은 계속 시작할 수 있어요.',
        onRetry: () => {
          if (materialsQuery.isError) void materialsQuery.refetch()
          if (reviewQuery.isError && quizApiEnabled) void reviewQuery.refetch()
        },
        onStartLearning: () => navigate('/learning/new'),
      }
    : undefined

  return (
    <HomePage
      status={(quizApiEnabled && reviewQuery.isPending) || materialsQuery.isPending ? 'loading' : 'ready'}
      greeting={{ nickname: currentUser.data?.nickname, consecutiveVisitDays: visitQuery.data?.summary?.consecutiveVisitDays }}
      nextAction={nextAction}
      review={review}
      recentMaterials={recentMaterials}
      studyMethods={[
        { id: 'new-quiz', title: '새 문제 만들기', detail: '학습자료를 선택하거나 새로 만들어요', onClick: () => navigate('/learning/new') },
        { id: 'materials', title: '내 학습자료 관리', detail: '저장한 학습자료를 찾고 관리해요', onClick: () => navigate('/learning/materials') },
      ]}
      dataBoundaryNotice={quizMockEnabled ? '복습 요약은 실제 API가 아닌 개발용 mock fixture를 사용하고 있어요.' : undefined}
      recommendationWarning={recommendationWarning}
      onViewAllReviews={() => navigate('/learning/quizzes')}
      onViewAllMaterials={() => navigate('/learning/materials')}
      onRetryAll={() => {
        void currentUser.refetch()
        void materialsQuery.refetch()
        void visitQuery.refetch()
        if (quizApiEnabled) void reviewQuery.refetch()
      }}
    />
  )
}

type ReviewSummary = {
  sourceAttemptId: string | null
  activeReviewSessionId: string | null
  quizTitle?: string | null
  materialTitle: string | null
  reviewQuestionCount: number
}

function createNextAction({ selectedReview, hasReview, recentMaterial, materialsResolved, navigate, openReview }: {
  selectedReview?: ReviewSummary
  hasReview: boolean
  recentMaterial?: { materialId: string; title: string; sourceType: 'PASTE' | 'NOTION'; updatedAt: string }
  materialsResolved: boolean
  navigate: ReturnType<typeof useNavigate>
  openReview: (materialTitle?: string | null) => void
}): HomeNextAction | undefined {
  if (hasReview && selectedReview?.quizTitle) {
    return {
      title: selectedReview.activeReviewSessionId ? `${selectedReview.quizTitle} 복습을 이어서 풀어보세요` : `${selectedReview.quizTitle}에서 다시 볼 문제가 ${selectedReview.reviewQuestionCount}개 있어요`,
      description: '최근에 완료한 퀴즈의 미해결 문제를 다시 확인해보세요.',
      context: `${selectedReview.materialTitle ?? '학습자료'} · ${selectedReview.reviewQuestionCount}문제`,
      action: { label: selectedReview.activeReviewSessionId ? '이어서 풀기' : '복습 시작', onClick: () => openReview(selectedReview.materialTitle) },
    }
  }
  if (recentMaterial) {
    return {
      title: `${recentMaterial.title}을 다시 살펴보세요`,
      description: '최근 학습한 자료를 열어 내용을 확인할 수 있어요.',
      context: formatMaterialDetail(recentMaterial.sourceType, recentMaterial.updatedAt),
      action: { label: '학습자료 관리', onClick: () => navigate(`/learning/materials/${recentMaterial.materialId}`) },
    }
  }
  if (!materialsResolved) return undefined
  return {
    title: '첫 학습자료를 만들고 문제를 풀어보세요',
    description: '저장한 글로 나만의 문제를 만들 수 있어요.',
    context: '새 학습 시작',
    action: { label: '학습자료 만들기', onClick: () => navigate('/learning/new') },
  }
}

function createReviewState({ apiReview, selectedReview, hasReview, apiError, openReview, retry }: {
  apiReview?: ReviewSummary
  selectedReview?: ReviewSummary
  hasReview: boolean
  apiError: boolean
  openReview: (materialTitle?: string | null) => void
  retry: () => void
}): HomePageProps['review'] {
  if (apiError) return { status: 'error', message: '복습 정보를 불러오지 못했어요.', onRetry: retry }
  if (hasReview && selectedReview?.quizTitle) {
    return {
      status: 'ready',
      data: {
        id: selectedReview.activeReviewSessionId ?? selectedReview.sourceAttemptId ?? 'review',
        title: selectedReview.quizTitle,
        detail: `${selectedReview.materialTitle ?? '학습자료'} · ${selectedReview.reviewQuestionCount}문제 · ${selectedReview.activeReviewSessionId ? '이어서 풀기' : '복습 시작'}`,
        onClick: () => openReview(selectedReview.materialTitle),
      },
    }
  }
  if (apiReview?.sourceAttemptId && !apiReview.quizTitle) return { status: 'error', message: '퀴즈명을 확인하지 못했어요.', onRetry: retry }
  return { status: 'empty', message: '지금 복습할 문제는 없어요. 새 문제를 풀면 여기에서 다시 확인할 수 있어요.' }
}

function formatMaterialDetail(sourceType: 'PASTE' | 'NOTION', updatedAt: string) {
  const source = sourceType === 'NOTION' ? 'Notion에서 가져옴' : '직접 입력'
  const date = new Intl.DateTimeFormat('ko-KR', { month: 'short', day: 'numeric' }).format(new Date(updatedAt))
  return `${source} · ${date} 수정`
}
