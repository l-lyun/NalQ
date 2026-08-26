import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import {
  createLearningMaterial,
  getLearningMaterial,
  getLearningMaterials,
  LEARNING_MATERIAL_PAGE_SIZE,
  learningMaterialKeys,
} from '@/features/learning-material/api/learningMaterial.api'
import { getLatestReview } from '@/features/quiz/api/quiz.api'
import { quizApiEnabled } from '@/features/quiz/model/quizFeature'
import { createUuidV4 } from '@/features/quiz/model/randomUuid'

import { LearningPage } from './LearningPage'
import type {
  LearningMaterialDraft,
  LearningNavigationDestination,
  LearningSectionState,
} from './learning.types'
import type { LearningMaterialPage } from '@/features/learning-material/api/learningMaterial.types'

type CreationAttempt = {
  fingerprint: string
  idempotencyKey: string
}

export function AuthenticatedLearningPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [materialsQuery, setMaterialsQuery] = useState('')
  const [materialsPage, setMaterialsPage] = useState(1)
  const creationAttemptRef = useRef<CreationAttempt | null>(null)
  const lastSuccessfulMaterialsRef = useRef<LearningMaterialPage | null>(null)
  const materialsQueryResult = useQuery({
    queryKey: learningMaterialKeys.list({
      page: materialsPage,
      size: LEARNING_MATERIAL_PAGE_SIZE,
      query: materialsQuery.trim(),
    }),
    queryFn: ({ signal }) =>
      getLearningMaterials(
        {
          page: materialsPage,
          size: LEARNING_MATERIAL_PAGE_SIZE,
          query: materialsQuery,
        },
        signal,
      ),
    placeholderData: (previousData) => previousData,
    refetchInterval: (query) =>
      query.state.data?.items.some(
        (material) => material.contentEditStatus === 'LOCKED_GENERATING',
      )
        ? 3_000
        : false,
  })
  const createMaterialMutation = useMutation({
    mutationFn: ({ draft, idempotencyKey }: { draft: LearningMaterialDraft; idempotencyKey: string }) =>
      createLearningMaterial(
        { title: draft.title.trim(), content: draft.body, sourceType: 'PASTE' },
        idempotencyKey,
      ),
  })
  const reviewQuery = useQuery({
    queryKey: ['private', 'quiz-review', 'latest'],
    queryFn: ({ signal }) => getLatestReview(signal),
    enabled: quizApiEnabled,
  })

  useEffect(() => {
    if (materialsQueryResult.isSuccess && !materialsQueryResult.isPlaceholderData) {
      lastSuccessfulMaterialsRef.current = materialsQueryResult.data
    }
  }, [
    materialsQueryResult.data,
    materialsQueryResult.isPlaceholderData,
    materialsQueryResult.isSuccess,
  ])

  const handleNavigate = (destination: LearningNavigationDestination) => {
    if (destination === 'home') {
      navigate('/')
    }

    // 학습은 현재 경로라 유지하고, 프로필은 라우트가 생길 때 연결한다.
  }

  const createMaterial = useCallback(
    async (draft: LearningMaterialDraft) => {
      const normalizedTitle = draft.title.trim()
      const fingerprint = JSON.stringify({
        title: normalizedTitle,
        content: draft.body,
        sourceType: 'PASTE',
      })
      if (creationAttemptRef.current?.fingerprint !== fingerprint) {
        creationAttemptRef.current = { fingerprint, idempotencyKey: createUuidV4() }
      }

      const attempt = creationAttemptRef.current
      const created = await createMaterialMutation.mutateAsync({
        draft: { title: normalizedTitle, body: draft.body },
        idempotencyKey: attempt.idempotencyKey,
      })
      creationAttemptRef.current = null
      void queryClient.invalidateQueries({ queryKey: learningMaterialKeys.all })
      return { materialId: created.materialId, title: created.title }
    },
    [createMaterialMutation, queryClient],
  )

  const loadMaterialDetail = useCallback(
    (materialId: string) =>
      queryClient.fetchQuery({
        queryKey: learningMaterialKeys.detail(materialId),
        queryFn: ({ signal }) => getLearningMaterial(materialId, signal),
      }),
    [queryClient],
  )

  const materialsState: LearningSectionState<LearningMaterialPage> = materialsQueryResult.isPending
    ? { status: 'loading' }
    : materialsQueryResult.isError
      ? {
          status: 'error',
          message: '학습자료를 불러오지 못했어요. 잠시 후 다시 시도해주세요.',
          data: materialsQueryResult.data ?? lastSuccessfulMaterialsRef.current ?? undefined,
        }
      : { status: 'ready', data: materialsQueryResult.data }

  return (
    <LearningPage
      reviewState={
        !quizApiEnabled
          ? { status: 'error', message: '복습 데이터 연동을 준비하고 있어요.' }
          : reviewQuery.isPending
          ? { status: 'loading' }
          : reviewQuery.isError
            ? { status: 'error', message: '복습 정보를 불러오지 못했어요.' }
            : reviewQuery.data &&
                reviewQuery.data.sourceAttemptId &&
                reviewQuery.data.quizSetId &&
                reviewQuery.data.attemptNumber !== null &&
                reviewQuery.data.materialTitle &&
                reviewQuery.data.completedAt &&
                reviewQuery.data.totalQuestionCount > 0
              ? {
                  status: 'ready',
                  data: {
                    sourceAttemptId: reviewQuery.data.sourceAttemptId,
                    quizSetId: reviewQuery.data.quizSetId,
                    attemptNumber: reviewQuery.data.attemptNumber,
                    materialTitle: reviewQuery.data.materialTitle,
                    completedAt: reviewQuery.data.completedAt,
                    totalQuestionCount: reviewQuery.data.totalQuestionCount,
                    reviewQuestionCount: reviewQuery.data.reviewQuestionCount,
                    activeReviewSessionId:
                      reviewQuery.data.activeReviewSessionId ?? undefined,
                  },
                }
              : reviewQuery.data?.sourceAttemptId
                ? {
                    status: 'error',
                    message: '최근 퀴즈 정보를 완전히 불러오지 못했어요.',
                  }
                : { status: 'ready', data: null }
      }
      materialsState={materialsState}
      materialsQuery={materialsQuery}
      materialsFetching={materialsQueryResult.isFetching && !materialsQueryResult.isPending}
      callbacks={{
        onNavigate: handleNavigate,
        onCreateMaterial: createMaterial,
        onLoadMaterialDetail: loadMaterialDetail,
        onMaterialsQueryChange: (query) => {
          setMaterialsQuery(query)
          setMaterialsPage(1)
        },
        onMaterialsPageChange: setMaterialsPage,
        onRetryMaterials: () => void materialsQueryResult.refetch(),
        ...(quizApiEnabled
          ? {
              onRetryReview: () => void reviewQuery.refetch(),
              onStartReview: () => navigate('/review'),
              onOpenQuizConditions: (material: { materialId: string; title: string }) =>
                navigate(`/learning/${material.materialId}/quiz`, {
                  state: { materialTitle: material.title },
                }),
            }
          : {}),
      }}
    />
  )
}
