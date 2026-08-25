import { ActionButton, Box, Divider, Skeleton, Text, VStack } from '@seed-design/react'
import { type FormEvent, useEffect, useMemo, useRef, useState } from 'react'

import { LearningBottomNavigation } from './components/LearningBottomNavigation'
import {
  LearningActionList,
  LearningField,
  LearningNotice,
  LearningScreenHeader,
  LearningSectionTitle,
  LearningTextInput,
  LearningTextarea,
} from './components/LearningPrimitives'
import { countUnicodeCodePoints } from './learning.text'
import type {
  LearningMaterial,
  LearningMaterialDraft,
  LearningPageProps,
  LearningReviewSummary,
  LearningSectionState,
} from './learning.types'
import './learning.css'

type Screen =
  | { id: 'main' }
  | { id: 'new-quiz' }
  | { id: 'select-material' }
  | { id: 'new-material' }
  | { id: 'direct-input' }
  | { id: 'edit-material'; materialId: string }
  | { id: 'handoff'; title: string; description: string }

type LearningHistoryEntry = {
  screen: Screen
  depth: number
}

const LEARNING_HISTORY_KEY = 'openmdLearning'

function getLearningHistoryEntry(state: unknown): LearningHistoryEntry | null {
  if (!state || typeof state !== 'object') return null
  const entry = (state as Record<string, unknown>)[LEARNING_HISTORY_KEY]
  if (!entry || typeof entry !== 'object') return null
  const candidate = entry as Partial<LearningHistoryEntry>
  if (!candidate.screen || typeof candidate.depth !== 'number') return null
  return candidate as LearningHistoryEntry
}

function canEditMaterialBody(material: LearningMaterial) {
  return !material.generating && material.contentEditStatus !== 'LOCKED_PERMANENT'
}

const sourceLabel = { PASTE: '직접 입력', NOTION: 'Notion에서 가져옴' } as const

export function LearningPage({
  initialMaterials = [],
  review = null,
  reviewState,
  materialsState,
  callbacks,
}: LearningPageProps) {
  const initialHistoryEntry = getLearningHistoryEntry(window.history.state)
  const [screen, setScreen] = useState<Screen>(initialHistoryEntry?.screen ?? { id: 'main' })
  const [materials, setMaterials] = useState(
    materialsState?.status === 'ready' ? materialsState.data : initialMaterials,
  )
  const [draft, setDraft] = useState<LearningMaterialDraft>({ title: '', body: '' })
  const [editDirty, setEditDirty] = useState(false)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const screenRef = useRef(screen)
  const draftRef = useRef(draft)
  const editDirtyRef = useRef(editDirty)
  const historyDepthRef = useRef(initialHistoryEntry?.depth ?? 0)
  const restoringHistoryRef = useRef(false)
  const hasReadyMaterialsRef = useRef(materialsState?.status === 'ready')
  const resolvedReviewState: LearningSectionState<LearningReviewSummary | null> = reviewState ?? {
    status: 'ready',
    data: review,
  }
  const resolvedMaterialsState: LearningSectionState<LearningMaterial[]> =
    materialsState?.status === 'error' &&
    materialsState.data === undefined &&
    hasReadyMaterialsRef.current
      ? { ...materialsState, data: materials }
      : (materialsState ?? { status: 'ready', data: materials })
  const availableMaterials =
    resolvedMaterialsState.status === 'ready'
      ? resolvedMaterialsState.data
      : resolvedMaterialsState.status === 'error'
        ? (resolvedMaterialsState.data ?? materials)
        : materials

  screenRef.current = screen
  draftRef.current = draft
  editDirtyRef.current = editDirty

  useEffect(() => {
    if (materialsState?.status !== 'ready') return
    hasReadyMaterialsRef.current = true
    setMaterials(materialsState.data)
  }, [materialsState])

  useEffect(() => {
    const existingEntry = getLearningHistoryEntry(window.history.state)
    if (!existingEntry) {
      window.history.replaceState(
        {
          ...window.history.state,
          [LEARNING_HISTORY_KEY]: { screen: { id: 'main' }, depth: 0 },
        },
        '',
      )
    }

    const handlePopState = (event: PopStateEvent) => {
      const nextEntry = getLearningHistoryEntry(event.state)

      if (restoringHistoryRef.current) {
        restoringHistoryRef.current = false
        if (nextEntry) {
          historyDepthRef.current = nextEntry.depth
          setScreen(nextEntry.screen)
        }
        return
      }

      const leavingDirtyEdit = screenRef.current.id === 'edit-material' && editDirtyRef.current
      const leavingCreationFlow =
        Boolean(draftRef.current.title || draftRef.current.body) &&
        (!nextEntry || nextEntry.depth === 0)
      const shouldLeave =
        !leavingDirtyEdit && !leavingCreationFlow
          ? true
          : window.confirm(
              leavingDirtyEdit
                ? '수정한 내용이 저장되지 않아요. 변경사항을 버릴까요?'
                : '작성한 내용이 저장되지 않아요. 변경사항을 버릴까요?',
            )

      if (!shouldLeave) {
        restoringHistoryRef.current = true
        window.history.forward()
        return
      }

      if (leavingCreationFlow) {
        const emptyDraft = { title: '', body: '' }
        draftRef.current = emptyDraft
        setDraft(emptyDraft)
      }

      if (nextEntry) {
        historyDepthRef.current = nextEntry.depth
        setScreen(nextEntry.screen)
      }
    }

    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  useEffect(() => {
    if (screen.id !== 'main') headingRef.current?.focus()
  }, [screen])

  const push = (next: Screen) => {
    const nextDepth = historyDepthRef.current + 1
    window.history.pushState(
      { ...window.history.state, [LEARNING_HISTORY_KEY]: { screen: next, depth: nextDepth } },
      '',
    )
    historyDepthRef.current = nextDepth
    screenRef.current = next
    setScreen(next)
  }
  const goBack = () => window.history.back()
  const goMain = () => {
    if (historyDepthRef.current > 0) window.history.go(-historyDepthRef.current)
  }

  const openQuizConditions = (material: LearningMaterial) => {
    callbacks?.onOpenQuizConditions?.(material)
    push({
      id: 'handoff',
      title: '문제 생성 조건',
      description: `“${material.title}” 자료를 선택했어요. 실제 문제 유형과 개수 설정 화면은 기능 연결 단계에서 이어집니다.`,
    })
  }

  const startReview = () => {
    const currentReview =
      resolvedReviewState.status === 'ready' ? resolvedReviewState.data : null
    if (!currentReview) return
    callbacks?.onStartReview?.(currentReview)
    push({
      id: 'handoff',
      title: currentReview.activeReviewSessionId ? '복습 계속하기' : '복습 시작',
      description: `“${currentReview.materialTitle}”의 미해결 문항 ${currentReview.reviewQuestionCount}개를 복습하는 풀이 화면은 기능 연결 단계에서 이어집니다.`,
    })
  }

  const createMaterial = async (nextDraft: LearningMaterialDraft) => {
    const normalizedDraft = { ...nextDraft, title: nextDraft.title.trim() }
    if (!callbacks?.onCreateMaterial) {
      throw new Error('학습자료 저장 기능을 연결하고 있어요. 잠시 후 다시 시도해주세요.')
    }

    const created = await callbacks.onCreateMaterial(normalizedDraft)
    setMaterials((current) => [created, ...current])
    const emptyDraft = { title: '', body: '' }
    draftRef.current = emptyDraft
    setDraft(emptyDraft)
    callbacks?.onOpenQuizConditions?.(created)
    push({
      id: 'handoff',
      title: '학습자료를 저장했어요',
      description: '저장한 자료를 그대로 유지한 채 문제 생성 조건 화면으로 이어지는 연결 지점입니다.',
    })
  }

  const updateMaterial = (updated: LearningMaterial) => {
    setMaterials((current) => current.map((item) => (item.id === updated.id ? updated : item)))
    callbacks?.onUpdateMaterial?.(
      updated.id,
      canEditMaterialBody(updated)
        ? { title: updated.title, body: updated.body }
        : { title: updated.title },
    )
    editDirtyRef.current = false
    setEditDirty(false)
    goMain()
  }

  return (
    <VStack className="learning-shell" minHeight="100dvh" bg="bg.layerBasement">
      <Box as="main" className="learning-main" bg="bg.layerDefault" width="full" pt="safeArea">
        <Box className="learning-route-panel" key={`${screen.id}-${'materialId' in screen ? screen.materialId : ''}`}>
          {screen.id === 'main' ? (
            <LearningMain
              materialsState={resolvedMaterialsState}
              reviewState={resolvedReviewState}
              onNewQuiz={() => push({ id: 'new-quiz' })}
              onStartReview={startReview}
              onEditMaterial={(materialId) => push({ id: 'edit-material', materialId })}
              onRetryReview={callbacks?.onRetryReview}
              onRetryMaterials={callbacks?.onRetryMaterials}
            />
          ) : screen.id === 'new-quiz' ? (
            <ChoiceScreen
              title="새 문제 만들기"
              headingRef={headingRef}
              intro="어떤 학습자료를 사용할까요?"
              onBack={goBack}
              rows={[
                {
                  id: 'existing',
                  title: '기존 학습자료로 만들기',
                  detail: '저장해둔 자료에서 선택해요',
                  onClick: () => push({ id: 'select-material' }),
                },
                {
                  id: 'new',
                  title: '새 학습자료로 만들기',
                  detail: '새 원문을 먼저 저장해요',
                  onClick: () => push({ id: 'new-material' }),
                },
              ]}
            />
          ) : screen.id === 'select-material' ? (
            <MaterialSelectionScreen
              materials={availableMaterials}
              headingRef={headingRef}
              onBack={goBack}
              onSelect={openQuizConditions}
              onCreateNew={() => push({ id: 'new-material' })}
            />
          ) : screen.id === 'new-material' ? (
            <ChoiceScreen
              title="새 학습자료로 만들기"
              headingRef={headingRef}
              intro="자료를 가져올 방법을 선택하세요"
              onBack={goBack}
              rows={[
                {
                  id: 'notion',
                  title: 'Notion에서 가져오기',
                  detail: '페이지 하나를 복사해 확인·수정해요',
                  onClick: () => {
                    callbacks?.onStartNotionImport?.()
                    push({
                      id: 'handoff',
                      title: 'Notion에서 가져오기',
                      description: '연결과 권한 확인, 단일 페이지 선택 화면은 Notion 연동 단계에서 이어집니다. 가져온 페이지는 한 번 복사되며 이후 자동 동기화되지 않습니다.',
                    })
                  },
                },
                {
                  id: 'paste',
                  title: '직접 입력하기',
                  detail: '제목과 본문을 직접 작성해요',
                  onClick: () => push({ id: 'direct-input' }),
                },
              ]}
            />
          ) : screen.id === 'direct-input' ? (
            <DirectInputScreen
              headingRef={headingRef}
              draft={draft}
              onDraftChange={setDraft}
              onBack={goBack}
              onSubmit={createMaterial}
            />
          ) : screen.id === 'edit-material' ? (
            <EditMaterialScreen
              headingRef={headingRef}
              material={availableMaterials.find((item) => item.id === screen.materialId)}
              onBack={goBack}
              onSave={updateMaterial}
              onDirtyChange={setEditDirty}
            />
          ) : (
            <HandoffScreen
              headingRef={headingRef}
              title={screen.title}
              description={screen.description}
              onBack={goMain}
            />
          )}
        </Box>
      </Box>
      {screen.id === 'main' ? (
        <LearningBottomNavigation onNavigate={(destination) => callbacks?.onNavigate?.(destination)} />
      ) : null}
    </VStack>
  )
}

function LearningMain({
  materialsState,
  reviewState,
  onNewQuiz,
  onStartReview,
  onEditMaterial,
  onRetryReview,
  onRetryMaterials,
}: {
  materialsState: LearningSectionState<LearningMaterial[]>
  reviewState: LearningSectionState<LearningReviewSummary | null>
  onNewQuiz: () => void
  onStartReview: () => void
  onEditMaterial: (materialId: string) => void
  onRetryReview?: () => void
  onRetryMaterials?: () => void
}) {
  const visibleMaterials =
    materialsState.status === 'ready'
      ? materialsState.data
      : materialsState.status === 'error'
        ? (materialsState.data ?? [])
        : []

  return (
    <VStack className="learning-content" px="spacingX.globalGutter" pt="x6" pb="spacingY.screenBottom" gap="x8">
      <VStack as="header" gap="x5">
        <Text as="h1" textStyle="t12Bold" color="fg.neutral">
          학습
        </Text>
        <ActionButton type="button" size="large" variant="brandSolid" onClick={onNewQuiz}>
          새 문제 만들기
        </ActionButton>
      </VStack>

      <VStack as="section" gap="x3" aria-labelledby="learning-review-title">
        <LearningSectionTitle id="learning-review-title">복습하기</LearningSectionTitle>
        {reviewState.status === 'loading' ? (
          <LearningSectionSkeleton label="복습 정보를 불러오는 중" rows={1} />
        ) : reviewState.status === 'error' ? (
          <LearningInlineError message={reviewState.message} onRetry={onRetryReview} />
        ) : reviewState.data && reviewState.data.reviewQuestionCount > 0 ? (
          <LearningActionList
            label="최신 복습"
            rows={[
              {
                id: reviewState.data.sourceAttemptId,
                title: reviewState.data.materialTitle,
                actionLabel: reviewState.data.activeReviewSessionId ? '복습 계속하기' : '복습 시작',
                detail: (
                  <>
                    미해결 문항 {reviewState.data.reviewQuestionCount}개 · {reviewState.data.completedAtLabel}
                    <br />
                    {reviewState.data.activeReviewSessionId
                      ? '같은 문제 목록을 첫 문제부터 다시 풀어요'
                      : '가장 최근 완료한 퀴즈를 복습해요'}
                  </>
                ),
                onClick: onStartReview,
              },
            ]}
          />
        ) : reviewState.data === null ? (
          <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
            문제를 완료하면 복습할 문항이 여기에 보여요.
          </Text>
        ) : (
          <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
            지금 복습할 문항이 없어요. 가장 최근에 완료한 퀴즈는 모두 해결했어요.
          </Text>
        )}
      </VStack>

      <Divider as="div" color="stroke.neutralSubtle" />

      <VStack as="section" gap="x3" aria-labelledby="learning-materials-title">
        <LearningSectionTitle id="learning-materials-title">학습자료 관리</LearningSectionTitle>
        {materialsState.status === 'loading' ? (
          <LearningSectionSkeleton label="학습자료를 불러오는 중" rows={3} />
        ) : (
          <VStack gap="x3">
            {materialsState.status === 'error' ? (
              <LearningInlineError message={materialsState.message} onRetry={onRetryMaterials} />
            ) : null}
            {visibleMaterials.length > 0 ? (
              <LearningActionList
                label="학습자료 목록"
                rows={visibleMaterials.map((material) => ({
                  id: material.id,
                  title: material.title,
                  detail: material.generating ? (
                    <>
                      {sourceLabel[material.source]} · {material.updatedAtLabel}
                      <br />
                      문제 생성 중 · 본문 임시 잠금 · 제목 수정 가능
                    </>
                  ) : material.contentEditStatus === 'LOCKED_PERMANENT' ? (
                    <>
                      {sourceLabel[material.source]} · {material.updatedAtLabel}
                      <br />
                      본문 잠금 · 제목 수정 가능
                    </>
                  ) : (
                    `${sourceLabel[material.source]} · ${material.updatedAtLabel}`
                  ),
                  onClick: () => onEditMaterial(material.id),
                }))}
              />
            ) : materialsState.status === 'ready' ? (
              <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
                아직 저장한 학습자료가 없어요. 위의 새 문제 만들기에서 새 자료를 만들 수 있어요.
              </Text>
            ) : null}
          </VStack>
        )}
      </VStack>
    </VStack>
  )
}

function LearningSectionSkeleton({ label, rows }: { label: string; rows: number }) {
  return (
    <VStack gap="x3" aria-busy="true" aria-label={label}>
      {Array.from({ length: rows }, (_, index) => (
        <VStack key={index} gap="x2" py="x2">
          <Skeleton tone="neutral" radius="8" width={index % 2 === 0 ? '70%' : '85%'} height="x5" />
          <Skeleton tone="neutral" radius="8" width={index % 2 === 0 ? '55%' : '65%'} height="x4" />
        </VStack>
      ))}
    </VStack>
  )
}

function LearningInlineError({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <VStack gap="x2" align="flex-start" role="status">
      <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
        {message}
      </Text>
      {onRetry ? (
        <ActionButton type="button" size="small" variant="ghost" onClick={onRetry}>
          다시 시도
        </ActionButton>
      ) : null}
    </VStack>
  )
}

function ChoiceScreen({ title, intro, rows, onBack, headingRef }: {
  title: string
  intro: string
  rows: Parameters<typeof LearningActionList>[0]['rows']
  onBack: () => void
  headingRef: React.RefObject<HTMLHeadingElement | null>
}) {
  return (
    <VStack className="learning-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x8">
      <LearningScreenHeader title={title} onBack={onBack} headingRef={headingRef} />
      <VStack gap="x4">
        <Text as="p" textStyle="t7Bold" color="fg.neutral">
          {intro}
        </Text>
        <LearningActionList label={intro} rows={rows} outlined />
      </VStack>
    </VStack>
  )
}

function MaterialSelectionScreen({ materials, onBack, onSelect, onCreateNew, headingRef }: {
  materials: LearningMaterial[]
  onBack: () => void
  onSelect: (material: LearningMaterial) => void
  onCreateNew: () => void
  headingRef: React.RefObject<HTMLHeadingElement | null>
}) {
  const [query, setQuery] = useState('')
  const filtered = useMemo(
    () => materials.filter((material) => material.title.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())),
    [materials, query],
  )

  return (
    <VStack className="learning-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x6">
      <LearningScreenHeader title="학습자료 선택" onBack={onBack} headingRef={headingRef} />
      <LearningField label="제목으로 검색">
        <LearningTextInput
          value={query}
          placeholder="학습자료 제목"
          onChange={(event) => setQuery(event.currentTarget.value)}
        />
      </LearningField>
      {filtered.length > 0 ? (
        <LearningActionList
          label="선택할 학습자료"
          rows={filtered.map((material) => ({
            id: material.id,
            title: material.title,
            detail: material.generating
              ? `문제 생성 중 · 완료 후 선택 가능`
              : `${sourceLabel[material.source]} · ${material.updatedAtLabel}`,
            disabled: material.generating,
            onClick: () => onSelect(material),
          }))}
          outlined
        />
      ) : (
        <VStack gap="x3" align="flex-start">
          <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
            {materials.length === 0
              ? '저장한 학습자료가 없어요.'
              : `“${query}”와 일치하는 학습자료가 없어요.`}
          </Text>
          {materials.length === 0 ? (
            <ActionButton type="button" size="medium" variant="neutralWeak" onClick={onCreateNew}>
              새 학습자료로 만들기
            </ActionButton>
          ) : (
            <ActionButton type="button" size="small" variant="ghost" onClick={() => setQuery('')}>
              검색어 지우기
            </ActionButton>
          )}
        </VStack>
      )}
    </VStack>
  )
}

function DirectInputScreen({ draft, onDraftChange, onBack, onSubmit, headingRef }: {
  draft: LearningMaterialDraft
  onDraftChange: (draft: LearningMaterialDraft) => void
  onBack: () => void
  onSubmit: (draft: LearningMaterialDraft) => Promise<void>
  headingRef: React.RefObject<HTMLHeadingElement | null>
}) {
  const [errors, setErrors] = useState<Partial<Record<keyof LearningMaterialDraft, string>>>({})
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string>()

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const titleLength = countUnicodeCodePoints(draft.title.trim())
    const bodyLength = countUnicodeCodePoints(draft.body)
    const nextErrors = {
      title: !draft.title.trim()
        ? '제목을 입력해주세요.'
        : titleLength > 255
          ? '제목은 255자 이하로 입력해주세요.'
          : undefined,
      body: !draft.body.trim()
        ? '문제를 만들 원문을 입력해주세요.'
        : bodyLength > 20_000
          ? '본문은 20,000자 이하로 입력해주세요.'
          : undefined,
    }
    setErrors(nextErrors)
    if (nextErrors.title || nextErrors.body) return
    setSubmitting(true)
    setSubmitError(undefined)
    try {
      await onSubmit(draft)
    } catch (error) {
      setSubmitError(
        error instanceof Error
          ? error.message
          : '학습자료를 저장하지 못했어요. 입력한 내용을 유지한 채 다시 시도해주세요.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <VStack className="learning-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x6">
      <LearningScreenHeader title="직접 입력하기" onBack={onBack} headingRef={headingRef} />
      <form className="learning-form" onSubmit={handleSubmit}>
        <VStack gap="x5">
          <LearningField
            label="제목"
            error={errors.title}
            characterCount={{ current: countUnicodeCodePoints(draft.title), max: 255 }}
          >
            <LearningTextInput
              invalid={Boolean(errors.title)}
              value={draft.title}
              placeholder="학습자료 제목"
              onChange={(event) => onDraftChange({ ...draft, title: event.currentTarget.value })}
            />
          </LearningField>
          <LearningField
            label="본문"
            error={errors.body}
            characterCount={{ current: countUnicodeCodePoints(draft.body), max: 20_000 }}
          >
            <LearningTextarea
              invalid={Boolean(errors.body)}
              value={draft.body}
              placeholder="문제를 만들 원문을 붙여넣거나 입력하세요"
              onChange={(event) => onDraftChange({ ...draft, body: event.currentTarget.value })}
            />
          </LearningField>
          {submitError ? (
            <Text as="p" textStyle="t5Regular" color="fg.critical" role="alert">
              {submitError}
            </Text>
          ) : null}
          <ActionButton type="submit" size="large" variant="brandSolid" disabled={submitting}>
            {submitting ? '저장하는 중...' : '저장하고 문제 만들기'}
          </ActionButton>
        </VStack>
      </form>
    </VStack>
  )
}

function EditMaterialScreen({ material, onBack, onSave, onDirtyChange, headingRef }: {
  material?: LearningMaterial
  onBack: () => void
  onSave: (material: LearningMaterial) => void
  onDirtyChange: (dirty: boolean) => void
  headingRef: React.RefObject<HTMLHeadingElement | null>
}) {
  const [title, setTitle] = useState(material?.title ?? '')
  const [body, setBody] = useState(material?.body ?? '')
  const [errors, setErrors] = useState<{ title?: string; body?: string }>({})
  const bodyEditable = material ? canEditMaterialBody(material) : false
  const dirty = material
    ? title !== material.title || (bodyEditable && body !== material.body)
    : false

  useEffect(() => {
    onDirtyChange(dirty)
    return () => onDirtyChange(false)
  }, [dirty, onDirtyChange])

  if (!material) {
    return (
      <HandoffScreen
        headingRef={headingRef}
        title="학습자료를 확인하지 못했어요"
        description="자료가 삭제됐다고 단정할 수 없어요. 목록으로 돌아가 최신 상태를 다시 확인해주세요."
        onBack={onBack}
      />
    )
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const titleLength = countUnicodeCodePoints(title.trim())
    const bodyLength = countUnicodeCodePoints(body)
    const nextErrors = {
      title: !title.trim()
        ? '제목을 입력해주세요.'
        : titleLength > 255
          ? '제목은 255자 이하로 입력해주세요.'
          : undefined,
      body: !bodyEditable
        ? undefined
        : !body.trim()
          ? '본문을 입력해주세요.'
          : bodyLength > 20_000
            ? '본문은 20,000자 이하로 입력해주세요.'
            : undefined,
    }
    setErrors(nextErrors)
    if (nextErrors.title || nextErrors.body) return
    onSave({ ...material, title: title.trim(), body, updatedAtLabel: '방금 수정' })
  }

  return (
    <VStack className="learning-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x5">
      <LearningScreenHeader title="학습자료 편집" onBack={onBack} headingRef={headingRef} />
      <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
        출처: {sourceLabel[material.source]} · 수정: {material.updatedAtLabel}
      </Text>
      {material.generating ? (
        <LearningNotice>
          문제 생성 중에는 본문을 수정할 수 없어요. 제목은 지금도 수정할 수 있고, 생성이 끝나면 본문을 다시 수정할 수 있어요.
        </LearningNotice>
      ) : material.contentEditStatus === 'LOCKED_PERMANENT' ? (
        <LearningNotice>
          이미 문제 생성에 사용된 본문은 수정할 수 없어요. 제목은 지금도 수정할 수 있어요.
        </LearningNotice>
      ) : null}
      <form className="learning-form" onSubmit={handleSubmit}>
        <VStack gap="x5">
          <LearningField
            label="제목"
            error={errors.title}
            characterCount={{ current: countUnicodeCodePoints(title), max: 255 }}
          >
            <LearningTextInput
              invalid={Boolean(errors.title)}
              value={title}
              onChange={(event) => setTitle(event.currentTarget.value)}
            />
          </LearningField>
          <LearningField
            label="본문"
            error={errors.body}
            description={
              material.generating
                ? '문제 생성이 끝난 뒤 다시 수정할 수 있어요.'
                : material.contentEditStatus === 'LOCKED_PERMANENT'
                  ? '기존 문제의 근거를 유지하기 위해 본문은 읽기 전용이에요.'
                  : undefined
            }
            characterCount={{ current: countUnicodeCodePoints(body), max: 20_000 }}
          >
            <LearningTextarea
              invalid={Boolean(errors.body)}
              value={body}
              readOnly={!bodyEditable}
              aria-readonly={!bodyEditable}
              onChange={(event) => setBody(event.currentTarget.value)}
            />
          </LearningField>
          <LearningNotice>
            수정해도 이미 만든 문제와 풀이 결과는 바뀌지 않아요. 다음에 만드는 문제부터 수정한 내용을 사용해요.
          </LearningNotice>
          <ActionButton type="submit" size="large" variant="brandSolid" disabled={!dirty}>
            변경사항 저장
          </ActionButton>
        </VStack>
      </form>
    </VStack>
  )
}

function HandoffScreen({ title, description, onBack, headingRef }: {
  title: string
  description: string
  onBack: () => void
  headingRef: React.RefObject<HTMLHeadingElement | null>
}) {
  return (
    <VStack className="learning-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x8">
      <LearningScreenHeader title={title} onBack={onBack} headingRef={headingRef} />
      <VStack gap="x4" align="flex-start">
        <Text as="p" textStyle="t6Regular" color="fg.neutralMuted">
          {description}
        </Text>
        <ActionButton type="button" size="medium" variant="neutralWeak" onClick={onBack}>
          학습으로 돌아가기
        </ActionButton>
      </VStack>
    </VStack>
  )
}
