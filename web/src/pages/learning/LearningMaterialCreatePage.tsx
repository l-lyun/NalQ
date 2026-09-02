import {
  ActionButton,
  ContentDialog,
  Portal,
  Text,
  VStack,
} from '@seed-design/react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useBlocker, useLocation, useNavigate } from 'react-router-dom'

import {
  createLearningMaterial,
  learningMaterialKeys,
} from '@/features/learning-material/api/learningMaterial.api'
import type { LearningMaterialSourceType } from '@/features/learning-material/api/learningMaterial.types'
import {
  countCodePoints,
  isLearningMaterialDraftValid,
  MATERIAL_CONTENT_MAX_LENGTH,
  MATERIAL_TITLE_MAX_LENGTH,
  validateLearningMaterialDraft,
} from '@/features/learning-material/model/learningMaterialDraft'
import { createUuidV4 } from '@/features/quiz/model/randomUuid'
import { quizRoutesEnabled } from '@/features/quiz/model/quizFeature'

import {
  LearningField,
  LearningNotice,
  LearningScreenHeader,
  LearningTextarea,
  LearningTextInput,
} from './components/LearningPrimitives'
import {
  readLearningCreateReturnState,
  resolveLearningMaterialsReturnTo,
} from './learningRoutes'
import './learning.css'

type EditorRouteState = {
  sourceType?: LearningMaterialSourceType
  title?: string
  content?: string
  returnTo?: string
  learningCreateReturnState?: unknown
}

type SaveDestination = 'material' | 'quiz'
type SaveAttempt = { fingerprint: string; idempotencyKey: string }

export function LearningMaterialCreatePage() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const routeState = (location.state ?? {}) as EditorRouteState
  const sourceType = routeState.sourceType === 'NOTION' ? 'NOTION' : 'PASTE'
  const returnTo = resolveLearningMaterialsReturnTo(routeState.returnTo)
  const learningCreateReturnState = readLearningCreateReturnState(routeState.learningCreateReturnState)
  const [title, setTitle] = useState(routeState.title ?? '')
  const [content, setContent] = useState(routeState.content ?? '')
  const [touched, setTouched] = useState({ title: sourceType === 'NOTION', content: sourceType === 'NOTION' })
  const [exitOpen, setExitOpen] = useState(false)
  const [destination, setDestination] = useState<SaveDestination | null>(null)
  const attemptRef = useRef<SaveAttempt | null>(null)
  const allowNavigationRef = useRef(false)
  const draft = useMemo(() => ({ title, content }), [content, title])
  const errors = validateLearningMaterialDraft(draft)
  const valid = isLearningMaterialDraftValid(draft)
  const hasUnsavedDraft = sourceType === 'NOTION' || Boolean(title || content)
  const blocker = useBlocker(() => hasUnsavedDraft && !allowNavigationRef.current)

  useEffect(() => {
    if (blocker.state === 'blocked') setExitOpen(true)
  }, [blocker.state])

  const save = useMutation({
    mutationFn: async (nextDestination: SaveDestination) => {
      const payload = { title: title.trim(), content, sourceType } satisfies {
        title: string
        content: string
        sourceType: LearningMaterialSourceType
      }
      const fingerprint = JSON.stringify(payload)
      if (attemptRef.current?.fingerprint !== fingerprint) {
        attemptRef.current = { fingerprint, idempotencyKey: createUuidV4() }
      }
      setDestination(nextDestination)
      return createLearningMaterial(payload, attemptRef.current.idempotencyKey)
    },
    onSuccess: (created, nextDestination) => {
      attemptRef.current = null
      allowNavigationRef.current = true
      void queryClient.invalidateQueries({ queryKey: learningMaterialKeys.all })
      if (nextDestination === 'quiz' && quizRoutesEnabled) {
        navigate(`/learning/${created.materialId}/quiz`, {
          replace: true,
          state: { materialTitle: created.title },
        })
      } else {
        navigate(`/learning/materials/${created.materialId}`, {
          replace: true,
          state: returnTo ? { returnTo } : undefined,
        })
      }
    },
  })

  const requestBack = () => {
    if (returnTo) {
      navigate(returnTo)
      return
    }
    navigate(sourceType === 'NOTION' ? '/learning/import/notion' : '/learning/new', {
      state: Object.keys(learningCreateReturnState).length > 0
        ? { learningCreateReturnState }
        : undefined,
    })
  }

  return (
    <VStack className="learning-management-page" bg="bg.layerDefault">
      <VStack className="learning-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x6">
        <LearningScreenHeader title={sourceType === 'NOTION' ? '학습자료 확인' : '학습자료 만들기'} onBack={requestBack} />

        {sourceType === 'NOTION' ? (
          <VStack gap="x2" align="flex-start">
            <Text as="p" textStyle="t5Bold" color="fg.neutral">노션에서 가져옴</Text>
            <LearningNotice>원본 페이지와 자동으로 동기화되지 않아요.</LearningNotice>
          </VStack>
        ) : (
          <LearningNotice>제목과 내용을 입력한 뒤 저장해 주세요.</LearningNotice>
        )}

        <VStack as="form" className="learning-form" gap="x5" onSubmit={(event) => event.preventDefault()}>
          <LearningField
            label="제목"
            error={touched.title ? errors.title : undefined}
            description={!touched.title && !title ? '제목을 입력하면 저장할 수 있어요.' : undefined}
            characterCount={{ current: countCodePoints(title), max: MATERIAL_TITLE_MAX_LENGTH }}
          >
            <LearningTextInput
              value={title}
              required
              readOnly={save.isPending}
              invalid={touched.title && Boolean(errors.title)}
              placeholder="학습자료 제목"
              onBlur={() => setTouched((current) => ({ ...current, title: true }))}
              onChange={(event) => {
                setTitle(event.currentTarget.value)
                if (touched.title || event.currentTarget.value) {
                  setTouched((current) => ({ ...current, title: true }))
                }
              }}
            />
          </LearningField>

          <LearningField
            label="내용"
            error={touched.content ? errors.content : undefined}
            description={!touched.content && !content ? '문제를 만들 내용을 붙여넣거나 입력해 주세요.' : undefined}
            characterCount={{ current: countCodePoints(content), max: MATERIAL_CONTENT_MAX_LENGTH }}
          >
            <LearningTextarea
              value={content}
              required
              readOnly={save.isPending}
              invalid={touched.content && Boolean(errors.content)}
              placeholder="Markdown 원문을 붙여넣거나 입력하세요"
              onBlur={() => setTouched((current) => ({ ...current, content: true }))}
              onChange={(event) => {
                setContent(event.currentTarget.value)
                if (touched.content || event.currentTarget.value) {
                  setTouched((current) => ({ ...current, content: true }))
                }
              }}
            />
          </LearningField>

          {save.isError ? (
            <Text as="p" textStyle="t5Regular" color="fg.critical" role="alert">
              자료를 저장하지 못했어요. 다시 시도해 주세요.
            </Text>
          ) : null}

          <VStack className="learning-create-actions" gap="x3" aria-busy={save.isPending}>
            <ActionButton
              className="learning-full-width-action"
              type="button"
              size="large"
              variant="neutralWeak"
              disabled={!valid || save.isPending}
              loading={save.isPending && destination === 'material'}
              onClick={() => save.mutate('material')}
            >
              {save.isPending && destination === 'material' ? '저장 중' : '자료 저장'}
            </ActionButton>
            <ActionButton
              className="learning-full-width-action"
              type="button"
              size="large"
              variant="brandSolid"
              disabled={!valid || save.isPending || !quizRoutesEnabled}
              loading={save.isPending && destination === 'quiz'}
              onClick={() => save.mutate('quiz')}
            >
              {save.isPending && destination === 'quiz' ? '저장 중' : '저장하고 퀴즈 만들기'}
            </ActionButton>
            {!quizRoutesEnabled ? (
              <Text as="p" textStyle="t3Regular" color="fg.neutralMuted">
                현재 환경에서는 퀴즈 만들기를 사용할 수 없어요. 자료 저장을 이용해 주세요.
              </Text>
            ) : null}
            {!valid ? (
              <Text as="p" textStyle="t3Regular" color="fg.neutralMuted">
                제목과 내용을 저장 가능한 상태로 입력해 주세요.
              </Text>
            ) : null}
          </VStack>
        </VStack>
      </VStack>

      <ContentDialog.Root
        open={exitOpen}
        onOpenChange={(open) => {
          setExitOpen(open)
          if (!open && blocker.state === 'blocked') blocker.reset()
        }}
        closeOnEscape
      >
        <Portal>
          <ContentDialog.Backdrop />
          <ContentDialog.Positioner>
            <ContentDialog.Content className="learning-confirm-dialog" width="full" maxWidth="420px">
              <ContentDialog.Header>
                <ContentDialog.Title>{sourceType === 'NOTION' ? '가져온 내용을 나갈까요?' : '작성 중인 내용을 나갈까요?'}</ContentDialog.Title>
                <ContentDialog.Description>저장하지 않은 제목과 내용은 사라져요.</ContentDialog.Description>
              </ContentDialog.Header>
              <ContentDialog.Footer>
                <ContentDialog.Action asChild>
                  <ActionButton autoFocus type="button" size="large" variant="neutralSolid">이어서 편집</ActionButton>
                </ContentDialog.Action>
                <ActionButton
                  type="button"
                  size="large"
                  variant="neutralWeak"
                  onClick={() => blocker.state === 'blocked' && blocker.proceed()}
                >
                  저장하지 않고 나가기
                </ActionButton>
              </ContentDialog.Footer>
            </ContentDialog.Content>
          </ContentDialog.Positioner>
        </Portal>
      </ContentDialog.Root>
    </VStack>
  )
}
