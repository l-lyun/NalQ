import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import {
  createLearningMaterial,
  LEARNING_MATERIAL_PAGE_SIZE,
  learningMaterialKeys,
  learningMaterialQueryOptions,
} from '@/features/learning-material/api/learningMaterial.api'
import type { LearningMaterialPage } from '@/features/learning-material/api/learningMaterial.types'
import { createUuidV4 } from '@/features/quiz/model/randomUuid'

import { LearningPage } from './LearningPage'
import type {
  LearningMaterialDraft,
  LearningSectionState,
} from './learning.types'

type CreationAttempt = { fingerprint: string; idempotencyKey: string }

export function LearningCreatePage() {
  const navigate = useNavigate()
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
        onExit: () => navigate('/learning'),
        onOpenQuizConditions: (material) =>
          navigate(`/learning/${material.materialId}/quiz`, { state: { materialTitle: material.title } }),
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
