import { ActionButton, Box, Flex, Skeleton, Text, VStack } from '@seed-design/react'
import { type FormEvent, useEffect, useRef, useState } from 'react'

import { LearningBottomNavigation } from './components/LearningBottomNavigation'
import {
  LearningActionList,
  LearningField,
  LearningScreenHeader,
  LearningSectionTitle,
  LearningTextInput,
  LearningTextarea,
} from './components/LearningPrimitives'
import { countUnicodeCodePoints } from './learning.text'
import type {
  LearningMaterialDetail,
  LearningMaterialDraft,
  LearningMaterialPage,
  LearningMaterialSummary,
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
  | { id: 'material-detail'; materialId: string }
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

const sourceLabel = { PASTE: '직접 입력', NOTION: 'Notion에서 가져옴' } as const

export function LearningPage({
  review = null,
  reviewState,
  materialsState,
  materialsQuery,
  materialsFetching = false,
  callbacks,
}: LearningPageProps) {
  const initialHistoryEntry = getLearningHistoryEntry(window.history.state)
  const [screen, setScreen] = useState<Screen>(initialHistoryEntry?.screen ?? { id: 'main' })
  const [draft, setDraft] = useState<LearningMaterialDraft>({ title: '', body: '' })
  const [mainSearchOpen, setMainSearchOpen] = useState(Boolean(materialsQuery.trim()))
  const [mainSearchShouldFocus, setMainSearchShouldFocus] = useState(false)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const screenRef = useRef(screen)
  const draftRef = useRef(draft)
  const historyDepthRef = useRef(initialHistoryEntry?.depth ?? 0)
  const restoringHistoryRef = useRef(false)
  const resolvedReviewState: LearningSectionState<LearningReviewSummary | null> = reviewState ?? {
    status: 'ready',
    data: review,
  }

  screenRef.current = screen
  draftRef.current = draft

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

      const leavingCreationFlow =
        Boolean(draftRef.current.title || draftRef.current.body) &&
        (!nextEntry || nextEntry.depth === 0)
      const shouldLeave = !leavingCreationFlow
        ? true
        : window.confirm('작성한 내용이 저장되지 않아요. 변경사항을 버릴까요?')

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

  useEffect(() => {
    if (screen.id === 'main' && materialsQuery.trim()) {
      setMainSearchOpen(true)
    }
  }, [materialsQuery, screen.id])

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

  const openQuizConditions = (material: Pick<LearningMaterialSummary, 'materialId' | 'title'>) => {
    if (callbacks?.onOpenQuizConditions) {
      callbacks.onOpenQuizConditions(material)
      return
    }
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
    if (callbacks?.onStartReview) {
      callbacks.onStartReview(currentReview)
      return
    }
    push({
      id: 'handoff',
      title: currentReview.activeReviewSessionId ? '복습 계속하기' : '복습 시작',
      description: `“${currentReview.materialTitle}”의 미해결 문항 ${currentReview.reviewQuestionCount}개를 복습하는 풀이 화면은 기능 연결 단계에서 이어집니다.`,
    })
  }

  const restartQuiz = () => {
    const currentReview =
      resolvedReviewState.status === 'ready' ? resolvedReviewState.data : null
    if (!currentReview) return
    if (callbacks?.onRestartQuiz) {
      callbacks.onRestartQuiz(currentReview)
      return
    }
    push({
      id: 'handoff',
      title: '전체 문제 다시 풀기',
      description: `“${currentReview.materialTitle}”의 전체 ${currentReview.totalQuestionCount}문제를 새 회차로 푸는 화면은 기능 연결 단계에서 이어집니다.`,
    })
  }

  const createMaterial = async (nextDraft: LearningMaterialDraft) => {
    const normalizedDraft = { ...nextDraft, title: nextDraft.title.trim() }
    if (!callbacks?.onCreateMaterial) {
      throw new Error('학습자료 저장 기능을 연결하고 있어요. 잠시 후 다시 시도해주세요.')
    }

    const created = await callbacks.onCreateMaterial(normalizedDraft)
    const emptyDraft = { title: '', body: '' }
    draftRef.current = emptyDraft
    setDraft(emptyDraft)
    if (callbacks.onOpenQuizConditions) {
      callbacks.onOpenQuizConditions(created)
      return
    }
    push({
      id: 'handoff',
      title: '학습자료를 저장했어요',
      description: '저장한 자료를 그대로 유지한 채 문제 생성 조건 화면으로 이어지는 연결 지점입니다.',
    })
  }

  return (
    <VStack className="learning-shell" minHeight="100dvh" bg="bg.layerBasement">
      <Box as="main" className="learning-main" bg="bg.layerDefault" width="full" pt="safeArea">
        <Box className="learning-route-panel" key={`${screen.id}-${'materialId' in screen ? screen.materialId : ''}`}>
          {screen.id === 'main' ? (
            <LearningMain
              materialsState={materialsState}
              materialsQuery={materialsQuery}
              materialsFetching={materialsFetching}
              reviewState={resolvedReviewState}
              searchOpen={mainSearchOpen}
              searchShouldFocus={mainSearchShouldFocus}
              onNewQuiz={() => push({ id: 'new-quiz' })}
              onStartReview={startReview}
              onRestartQuiz={restartQuiz}
              onOpenMaterial={(materialId) => push({ id: 'material-detail', materialId })}
              onSearchOpenChange={(open) => {
                setMainSearchOpen(open)
                setMainSearchShouldFocus(open)
                if (!open) callbacks?.onMaterialsQueryChange?.('')
              }}
              onSearchFocused={() => setMainSearchShouldFocus(false)}
              onQueryChange={(query) => callbacks?.onMaterialsQueryChange?.(query)}
              onPageChange={(page) => callbacks?.onMaterialsPageChange?.(page)}
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
              materialsState={materialsState}
              query={materialsQuery}
              fetching={materialsFetching}
              headingRef={headingRef}
              onBack={goBack}
              onSelect={openQuizConditions}
              onCreateNew={() => push({ id: 'new-material' })}
              onQueryChange={(query) => callbacks?.onMaterialsQueryChange?.(query)}
              onPageChange={(page) => callbacks?.onMaterialsPageChange?.(page)}
              onRetry={callbacks?.onRetryMaterials}
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
          ) : screen.id === 'material-detail' ? (
            <MaterialDetailScreen
              headingRef={headingRef}
              materialId={screen.materialId}
              loadMaterial={callbacks?.onLoadMaterialDetail}
              onBack={goBack}
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
  materialsQuery,
  materialsFetching,
  reviewState,
  searchOpen,
  searchShouldFocus,
  onNewQuiz,
  onStartReview,
  onRestartQuiz,
  onOpenMaterial,
  onSearchOpenChange,
  onSearchFocused,
  onQueryChange,
  onPageChange,
  onRetryReview,
  onRetryMaterials,
}: {
  materialsState: LearningSectionState<LearningMaterialPage>
  materialsQuery: string
  materialsFetching: boolean
  reviewState: LearningSectionState<LearningReviewSummary | null>
  searchOpen: boolean
  searchShouldFocus: boolean
  onNewQuiz: () => void
  onStartReview: () => void
  onRestartQuiz: () => void
  onOpenMaterial: (materialId: string) => void
  onSearchOpenChange: (open: boolean) => void
  onSearchFocused: () => void
  onQueryChange: (query: string) => void
  onPageChange: (page: number) => void
  onRetryReview?: () => void
  onRetryMaterials?: () => void
}) {
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
        <LearningSectionTitle id="learning-review-title">최근 퀴즈</LearningSectionTitle>
        {reviewState.status === 'loading' ? (
          <Box bg="bg.neutralWeak" borderRadius="r3" p="x4">
            <LearningSectionSkeleton label="최근 퀴즈를 불러오는 중" rows={2} />
          </Box>
        ) : reviewState.status === 'error' ? (
          <LearningInlineError
            message="최근 퀴즈를 확인하지 못했어요. 완료한 기록은 그대로 있어요. 잠시 후 다시 확인해 주세요."
            onRetry={onRetryReview}
          />
        ) : reviewState.data ? (
          <RecentQuizContext
            review={reviewState.data}
            onStartReview={onStartReview}
            onRestartQuiz={onRestartQuiz}
          />
        ) : reviewState.data === null ? (
          <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
            아직 완료한 퀴즈가 없어요. 새 문제를 만들고 풀면 여기에서 다시 풀 수 있어요.
          </Text>
        ) : null}
      </VStack>

      <VStack as="section" gap="x3" aria-labelledby="learning-materials-title">
        <Flex align="center" justify="space-between" gap="x3">
          <LearningSectionTitle id="learning-materials-title">내 학습자료</LearningSectionTitle>
          <ActionButton
            type="button"
            size="small"
            variant="ghost"
            color="fg.neutralMuted"
            fontWeight="medium"
            aria-expanded={searchOpen}
            aria-controls="learning-material-search"
            onClick={() => onSearchOpenChange(!searchOpen)}
          >
            {searchOpen ? '닫기' : '검색'}
          </ActionButton>
        </Flex>
        <MaterialCollection
          materialsState={materialsState}
          query={materialsQuery}
          fetching={materialsFetching}
          onQueryChange={onQueryChange}
          onPageChange={onPageChange}
          onRetry={onRetryMaterials}
          onSelect={(material) => onOpenMaterial(material.materialId)}
          searchVisible={searchOpen}
          searchAutoFocus={searchShouldFocus}
          onSearchFocus={onSearchFocused}
          label="학습자료 목록"
        />
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

function MaterialSelectionScreen({
  materialsState,
  query,
  fetching,
  onBack,
  onSelect,
  onCreateNew,
  onQueryChange,
  onPageChange,
  onRetry,
  headingRef,
}: {
  materialsState: LearningSectionState<LearningMaterialPage>
  query: string
  fetching: boolean
  onBack: () => void
  onSelect: (material: LearningMaterialSummary) => void
  onCreateNew: () => void
  onQueryChange: (query: string) => void
  onPageChange: (page: number) => void
  onRetry?: () => void
  headingRef: React.RefObject<HTMLHeadingElement | null>
}) {
  return (
    <VStack className="learning-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x6">
      <LearningScreenHeader title="학습자료 선택" onBack={onBack} headingRef={headingRef} />
      <MaterialCollection
        materialsState={materialsState}
        query={query}
        fetching={fetching}
        onQueryChange={onQueryChange}
        onPageChange={onPageChange}
        onRetry={onRetry}
        onSelect={onSelect}
        onCreateNew={onCreateNew}
        disableLocked
        outlined
        label="선택할 학습자료"
      />
    </VStack>
  )
}

function RecentQuizContext({
  review,
  onStartReview,
  onRestartQuiz,
}: {
  review: LearningReviewSummary
  onStartReview: () => void
  onRestartQuiz: () => void
}) {
  const rows = [] as Parameters<typeof LearningActionList>[0]['rows']

  if (review.activeReviewSessionId) {
    rows.push({
      id: `${review.sourceAttemptId}-review`,
      title: '틀린 문제 이어서 풀기',
      detail: '진행 중인 문제를 첫 문제부터 다시 풀어요',
      onClick: onStartReview,
    })
  } else if (review.reviewQuestionCount > 0) {
    rows.push({
      id: `${review.sourceAttemptId}-review`,
      title: `틀린 문제 ${review.reviewQuestionCount}개만 풀기`,
      detail: '최근 회차의 미해결 문제만 풀어요',
      onClick: onStartReview,
    })
  }

  rows.push({
    id: `${review.quizSetId}-restart`,
    title: `전체 ${review.totalQuestionCount}문제 다시 풀기`,
    detail: '같은 문제 전체를 새 회차로 풀어요',
    onClick: onRestartQuiz,
  })

  return (
    <Box bg="bg.neutralWeak" borderRadius="r3" p="x4">
      <VStack gap="x3">
        <VStack gap="x1">
          <Text as="p" textStyle="t7Bold" color="fg.neutral">
            {review.materialTitle}
          </Text>
          <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
            {formatQuizCompletedAt(review.completedAt)} · {review.totalQuestionCount}문제 ·{' '}
            {review.attemptNumber}회차
          </Text>
          {review.activeReviewSessionId ? (
            <Text as="p" textStyle="t4Medium" color="fg.positive">
              틀린 문제 풀이가 진행 중이에요
            </Text>
          ) : review.reviewQuestionCount === 0 ? (
            <Text as="p" textStyle="t4Medium" color="fg.positive">
              틀린 문제를 모두 해결했어요
            </Text>
          ) : null}
        </VStack>
        <LearningActionList label={`${review.materialTitle} 다시 풀기`} rows={rows} />
      </VStack>
    </Box>
  )
}

function MaterialCollection({
  materialsState,
  query,
  fetching,
  onQueryChange,
  onPageChange,
  onSelect,
  onCreateNew,
  onRetry,
  disableLocked = false,
  outlined = false,
  searchVisible = true,
  searchAutoFocus = false,
  onSearchFocus,
  label,
}: {
  materialsState: LearningSectionState<LearningMaterialPage>
  query: string
  fetching: boolean
  onQueryChange: (query: string) => void
  onPageChange: (page: number) => void
  onSelect: (material: LearningMaterialSummary) => void
  onCreateNew?: () => void
  onRetry?: () => void
  disableLocked?: boolean
  outlined?: boolean
  searchVisible?: boolean
  searchAutoFocus?: boolean
  onSearchFocus?: () => void
  label: string
}) {
  const page =
    materialsState.status === 'ready'
      ? materialsState.data
      : materialsState.status === 'error'
        ? materialsState.data
        : undefined

  return (
    <VStack gap="x3" aria-busy={fetching || materialsState.status === 'loading'}>
      {searchVisible ? (
        <Box id="learning-material-search" className="learning-search-region">
          <LearningField label="학습자료 제목">
            <LearningTextInput
              type="search"
              value={query}
              placeholder="제목으로 검색"
              autoFocus={searchAutoFocus}
              onFocus={onSearchFocus}
              onChange={(event) => onQueryChange(event.currentTarget.value)}
            />
          </LearningField>
        </Box>
      ) : null}
      {fetching && page ? (
        <Text as="p" textStyle="t3Regular" color="fg.neutralMuted" role="status" aria-live="polite">
          목록을 업데이트하고 있어요.
        </Text>
      ) : null}
      {materialsState.status === 'loading' ? (
        <LearningSectionSkeleton label="학습자료를 불러오는 중" rows={3} />
      ) : (
        <>
          {materialsState.status === 'error' ? (
            <LearningInlineError message={materialsState.message} onRetry={onRetry} />
          ) : null}
          {page && page.items.length > 0 ? (
            <>
              <LearningActionList
                label={label}
                outlined={outlined}
                rows={page.items.map((material) => {
                  const locked = material.contentEditStatus === 'LOCKED_GENERATING'
                  return {
                    id: material.materialId,
                    title: material.title,
                    detail: locked ? (
                      <>
                        {sourceLabel[material.sourceType]} · {formatMaterialDate(material.updatedAt)}
                        <br />
                        {disableLocked
                          ? '문제 생성 중 · 완료 후 선택 가능'
                          : '문제 생성 중 · 본문 임시 잠금'}
                      </>
                    ) : (
                      `${sourceLabel[material.sourceType]} · ${formatMaterialDate(material.updatedAt)}`
                    ),
                    disabled: fetching || (disableLocked && locked),
                    onClick: () => onSelect(material),
                  }
                })}
              />
              <MaterialPagination
                page={page.page}
                totalPages={page.totalPages}
                disabled={fetching}
                onPageChange={onPageChange}
              />
            </>
          ) : page && materialsState.status !== 'error' ? (
            <VStack gap="x3" align="flex-start">
              <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
                {page.totalElements > 0
                  ? '이 페이지에 표시할 학습자료가 없어요. 이전 페이지로 이동해주세요.'
                  : query.trim()
                  ? `“${query.trim()}”와 일치하는 학습자료가 없어요.`
                  : '아직 저장한 학습자료가 없어요.'}
              </Text>
              {page.totalElements > 0 ? (
                <MaterialPagination
                  page={page.page}
                  totalPages={page.totalPages}
                  disabled={fetching}
                  onPageChange={onPageChange}
                />
              ) : query.trim() ? (
                <ActionButton type="button" size="small" variant="ghost" onClick={() => onQueryChange('')}>
                  검색어 지우기
                </ActionButton>
              ) : onCreateNew ? (
                <ActionButton type="button" size="medium" variant="neutralWeak" onClick={onCreateNew}>
                  새 학습자료로 만들기
                </ActionButton>
              ) : null}
            </VStack>
          ) : null}
        </>
      )}
    </VStack>
  )
}

function MaterialPagination({
  page,
  totalPages,
  disabled,
  onPageChange,
}: {
  page: number
  totalPages: number
  disabled: boolean
  onPageChange: (page: number) => void
}) {
  if (totalPages <= 1) return null

  return (
    <nav className="learning-pagination" aria-label="학습자료 페이지">
      <ActionButton
        type="button"
        size="medium"
        variant="ghost"
        disabled={disabled || page <= 1}
        onClick={() => onPageChange(page - 1)}
      >
        이전
      </ActionButton>
      <Text as="span" textStyle="t4Medium" color="fg.neutral" aria-current="page">
        {page} / {totalPages}
      </Text>
      <ActionButton
        type="button"
        size="medium"
        variant="ghost"
        disabled={disabled || page >= totalPages}
        onClick={() => onPageChange(page + 1)}
      >
        다음
      </ActionButton>
    </nav>
  )
}

function formatMaterialDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
}

function formatQuizCompletedAt(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
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

function MaterialDetailScreen({ materialId, loadMaterial, onBack, headingRef }: {
  materialId: string
  loadMaterial?: (materialId: string) => Promise<LearningMaterialDetail>
  onBack: () => void
  headingRef: React.RefObject<HTMLHeadingElement | null>
}) {
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState<
    | { status: 'loading' }
    | { status: 'ready'; data: LearningMaterialDetail }
    | { status: 'error'; message: string }
  >({ status: 'loading' })

  useEffect(() => {
    let active = true
    setState({ status: 'loading' })
    if (!loadMaterial) {
      setState({ status: 'error', message: '학습자료 상세 조회 기능을 연결하고 있어요.' })
      return () => {
        active = false
      }
    }

    void loadMaterial(materialId)
      .then((data) => {
        if (active) setState({ status: 'ready', data })
      })
      .catch((error: unknown) => {
        if (!active) return
        setState({
          status: 'error',
          message:
            error instanceof Error
              ? error.message
              : '학습자료를 불러오지 못했어요. 목록에서 다시 확인해주세요.',
        })
      })

    return () => {
      active = false
    }
  }, [attempt, loadMaterial, materialId])

  return (
    <VStack className="learning-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x5">
      <LearningScreenHeader title="학습자료 상세" onBack={onBack} headingRef={headingRef} />
      {state.status === 'loading' ? (
        <LearningSectionSkeleton label="학습자료 상세를 불러오는 중" rows={4} />
      ) : state.status === 'error' ? (
        <LearningInlineError message={state.message} onRetry={() => setAttempt((current) => current + 1)} />
      ) : (
        <VStack gap="x6">
          <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
            출처: {sourceLabel[state.data.sourceType]}
            <br />
            생성: {formatMaterialDate(state.data.createdAt)} · 수정: {formatMaterialDate(state.data.updatedAt)}
          </Text>
          {state.data.contentEditStatus === 'LOCKED_GENERATING' ? (
            <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
              문제 생성 중이라 본문이 임시 잠금 상태예요.
            </Text>
          ) : null}
          <VStack gap="x2">
            <Text as="h2" textStyle="t5Bold" color="fg.neutralMuted">
              제목
            </Text>
            <Text as="p" textStyle="t7Medium" color="fg.neutral">
              {state.data.title}
            </Text>
          </VStack>
          <VStack gap="x2">
            <Text as="h2" textStyle="t5Bold" color="fg.neutralMuted">
              본문
            </Text>
            <Box className="learning-material-body" bg="bg.neutralWeak" borderRadius="r3" p="x4">
              <Text as="p" textStyle="t5Regular" color="fg.neutral">
                {state.data.content}
              </Text>
            </Box>
            <Text as="p" textStyle="t3Regular" color="fg.neutralMuted">
              {state.data.contentLength.toLocaleString('ko-KR')}자
            </Text>
          </VStack>
        </VStack>
      )}
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
