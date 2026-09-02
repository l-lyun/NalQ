import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import {
  createLearningMaterial,
  LEARNING_MATERIAL_PAGE_SIZE,
  learningMaterialKeys,
  learningMaterialQueryOptions,
} from '@/features/learning-material/api/learningMaterial.api'
import type { LearningMaterialPage } from '@/features/learning-material/api/learningMaterial.types'
import { quizRoutesEnabled } from '@/features/quiz/model/quizFeature'
import { createUuidV4 } from '@/features/quiz/model/randomUuid'

import { LearningPage } from './LearningPage'
import { readLearningCreateReturnState } from './learningRoutes'
import type {
  LearningMaterialDraft,
  LearningSectionState,
} from './learning.types'

type CreationAttempt = { fingerprint: string; idempotencyKey: string }

export function LearningCreatePage() {
  const navigate = useNavigate()
  const location = useLocation()
  const returnState = readLearningCreateReturnState(location.state)
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)
  const creationAttempt = useRef<CreationAttempt | null>(null)
  const materials = useQuery({
    ...learningMaterialQueryOptions.list({
      page,
      size: LEARNING_MATERIAL_PAGE_SIZE,
      query: query.trim(),
    }),
    placeholderData: (previous) => previous,
    refetchOnWindowFocus: false,
  })
  const createMaterial = useMutation({
    mutationFn: ({ draft, idempotencyKey }: { draft: LearningMaterialDraft; idempotencyKey: string }) =>
      createLearningMaterial(
        { title: draft.title.trim(), content: draft.body, sourceType: 'PASTE' },
        idempotencyKey,
      ),
  })
  const materialsState: LearningSectionState<LearningMaterialPage> = materials.isPending
    ? { status: 'loading' }
    : materials.isError
      ? { status: 'error', message: '학습자료를 불러오지 못했어요.', data: materials.data }
      : { status: 'ready', data: materials.data }

  return (
    <LearningPage
      initialScreen="new-quiz"
      reviewState={{ status: 'ready', data: null }}
      materialsState={materialsState}
      materialsQuery={query}
      materialsFetching={materials.isFetching && !materials.isPending}
      callbacks={{
        onExit: () => navigate(returnState.returnTo ?? '/learning', {
          replace: true,
          state: returnState.returnTo && returnState.returnScrollTop !== undefined
            ? { restoreScrollTop: returnState.returnScrollTop }
            : undefined,
        }),
        onStartNotionImport: () => navigate('/learning/import/notion'),
        onStartDirectInput: () => navigate('/learning/materials/new', {
          state: { sourceType: 'PASTE', title: '', content: '' },
        }),
        onOpenQuizConditions: quizRoutesEnabled
          ? (material) =>
              navigate(`/learning/${material.materialId}/quiz`, {
                state: { materialTitle: material.title },
              })
          : undefined,
        onLoadMaterialDetail: (materialId) =>
          queryClient.fetchQuery(learningMaterialQueryOptions.detail(materialId)),
        onMaterialsQueryChange: (nextQuery) => {
          setQuery(nextQuery)
          setPage(1)
        },
        onMaterialsPageChange: setPage,
        onRetryMaterials: () => void materials.refetch(),
        onCreateMaterial: async (draft) => {
          const normalized = { ...draft, title: draft.title.trim() }
          const fingerprint = JSON.stringify(normalized)
          if (creationAttempt.current?.fingerprint !== fingerprint) {
            creationAttempt.current = { fingerprint, idempotencyKey: createUuidV4() }
          }
          const created = await createMaterial.mutateAsync({
            draft: normalized,
            idempotencyKey: creationAttempt.current.idempotencyKey,
          })
          creationAttempt.current = null
          await queryClient.invalidateQueries({ queryKey: learningMaterialKeys.all })
          return { materialId: created.materialId, title: created.title }
        },
      }}
    />
  )
}
