import { IconChevronRightLine } from '@karrotmarket/react-monochrome-icon'
import {
  ActionButton,
  BottomSheet,
  Box,
  Flex,
  Icon,
  Portal,
  Skeleton,
  Text,
  VStack,
} from '@seed-design/react'
import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  type FormEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import {
  useLocation,
  useNavigate,
  useSearchParams,
} from 'react-router-dom'

import { learningMaterialKeys } from '@/features/learning-material/api/learningMaterial.api'
import {
  updateManagedLearningMaterial,
} from '@/features/learning-material/api/learningMaterialManagementAdapter'
import type { LearningMaterialSummary } from '@/features/learning-material/api/learningMaterial.types'
import { getLearningMaterialManagementActions } from '@/features/learning-material/model/learningMaterialManagementActions'
import { managedLearningMaterialQueryOptions } from '@/features/learning-material/model/learningMaterialManagementQueries'
import {
  quizManagementMode,
  renameManagedQuizSet,
  startManagedReviewSession,
} from '@/features/quiz/api/quizManagementAdapter'
import type {
  LatestReview,
  PendingSelfAssessment,
  ReviewCandidate,
  QuizSetSummary,
} from '@/features/quiz/api/quiz.types'
import {
  invalidateQuizManagementQueries,
  QUIZ_SET_PAGE_SIZE,
  quizManagementQueryOptions,
} from '@/features/quiz/model/quizManagementQueries'
import { quizManagementKeys } from '@/features/quiz/model/quizManagementQueries'
import {
  createNewMainQuizDestination,
  parseExpandedQuizIds,
  toggleExpandedQuizId,
} from '@/features/quiz/model/quizManagementActions'
import {
  getReviewCandidatesEmptyMessage,
  type LearningReviewAction,
  resolveRecentQuizAction,
  resolveReviewCandidateAction,
} from '@/features/quiz/model/learningReviewActions'

import {
  LearningActionList,
  LearningField,
  LearningNotice,
  LearningScreenHeader,
  LearningTextInput,
  LearningTextarea,
} from './components/LearningPrimitives'
import {
  resolveLearningMaterialEditBackNavigation,
  resolveLearningMaterialsReturnTo,
  resolveLearningQuizzesReturnTo,
  shouldCommitLearningSearchInput,
} from './learningRoutes'
import { QuizManagementCard } from './components/QuizManagementCard'
import { countUnicodeCodePoints } from './learning.text'
import './learning.css'

const MATERIAL_PAGE_SIZE = 6
const REVIEW_CANDIDATE_LIMIT = 3

function formatDate(value: string | null) {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
}

function usePageBack(fallback = '/learning') {
  const navigate = useNavigate()
  const location = useLocation()
  return () => {
    if (location.key === 'default') navigate(fallback, { replace: true })
    else navigate(-1)
  }
}

function LoadingRows({ label, count = 3 }: { label: string; count?: number }) {
  return (
    <VStack gap="x3" role="status" aria-label={label}>
      {Array.from({ length: count }, (_, index) => (
        <Skeleton key={index} width="full" height="x16" />
      ))}
    </VStack>
  )
}

function InlineFailure({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <VStack gap="x3" align="flex-start" role="alert">
      <Text as="p" textStyle="t5Regular" color="fg.critical">
        {message}
      </Text>
      <ActionButton type="button" size="medium" variant="neutralWeak" onClick={onRetry}>
        다시 시도
      </ActionButton>
    </VStack>
  )
}

function EmptyState({ children, action }: { children: string; action?: React.ReactNode }) {
  return (
    <VStack gap="x3" align="flex-start">
      <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
        {children}
      </Text>
      {action}
    </VStack>
  )
}

function SectionHeading({ id, title, action }: { id: string; title: string; action?: React.ReactNode }) {
  return (
    <Flex align="center" justify="space-between" gap="x3">
      <Text as="h2" id={id} textStyle="t7Bold" color="fg.neutral">
        {title}
      </Text>
      {action}
    </Flex>
  )
}

function PreviewRow({ title, detail, onClick }: { title: string; detail?: string; onClick: () => void }) {
  return (
    <button className="learning-management-row" type="button" onClick={onClick}>
      <VStack minWidth="0px" gap="x1" align="flex-start">
        <Text className="learning-long-title" textStyle="t5Medium" color="fg.neutral">
          {title}
        </Text>
        {detail ? (
          <Text textStyle="t3Regular" color="fg.neutralMuted">
            {detail}
          </Text>
        ) : null}
      </VStack>
      <Icon svg={<IconChevronRightLine />} size="x4_5" aria-hidden />
    </button>
  )
}

export function LearningManagementPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const quizManagementAvailable = quizManagementMode !== 'disabled'
  const recentQuiz = useQuery({
    ...quizManagementQueryOptions.latestReview(),
    enabled: quizManagementAvailable,
    refetchOnWindowFocus: false,
  })
  const recentPending = useQuery({
    ...quizManagementQueryOptions.pendingSelfAssessment(recentQuiz.data?.quizSetId ?? ''),
    enabled: Boolean(recentQuiz.data?.quizSetId),
    refetchOnWindowFocus: false,
  })
  const reviewCandidates = useQuery({
    ...quizManagementQueryOptions.reviewCandidates(REVIEW_CANDIDATE_LIMIT),
    enabled: quizManagementAvailable,
    refetchOnWindowFocus: false,
  })
  const reviewStartInFlightRef = useRef(false)
  const startReview = useMutation({
    mutationFn: startManagedReviewSession,
    onSuccess: async (session) => {
      await queryClient.invalidateQueries({ queryKey: ['private', 'quiz-review'] })
      navigate(`/review-sessions/${session.reviewSessionId}`)
    },
    onSettled: () => {
      reviewStartInFlightRef.current = false
    },
  })

  const runAction = (action: LearningReviewAction) => {
    if (reviewStartInFlightRef.current) return
    if (action.kind === 'navigate') navigate(action.path)
    else {
      reviewStartInFlightRef.current = true
      startReview.mutate(action.sourceAttemptId)
    }
  }

  return (
    <VStack className="learning-shell" minHeight="100dvh" bg="bg.layerDefault">
      <Box as="main" className="learning-main" bg="bg.layerDefault" width="full" pt="safeArea">
        <VStack
          className="learning-content"
          px="spacingX.globalGutter"
          pt="x6"
          pb="spacingY.screenBottom"
          gap="x6"
        >
          <Flex as="header" align="center" justify="space-between" gap="x3">
            <Text as="h1" textStyle="t9Bold" color="fg.neutral">
              학습
            </Text>
            <div className="app-notification-slot" data-app-notification-slot />
          </Flex>

          <VStack as="section" gap="x4" aria-labelledby="recent-quiz-heading">
            <Text as="h2" id="recent-quiz-heading" textStyle="t7Bold" color="fg.neutral">
              최근 퀴즈
            </Text>
            {!quizManagementAvailable ? (
              <EmptyState>퀴즈 기능을 준비하고 있어요.</EmptyState>
            ) : recentQuiz.isPending ? (
              <LoadingRows label="최근 퀴즈를 불러오는 중" count={1} />
            ) : recentQuiz.isError ? (
              <InlineFailure
                message="최근 퀴즈를 불러오지 못했어요."
                onRetry={() => void recentQuiz.refetch()}
              />
            ) : recentQuiz.data.sourceAttemptId && recentQuiz.data.quizSetId ? (
              <RecentQuizPanel
                review={recentQuiz.data}
                pending={recentPending.data ?? null}
                actionState={recentPending.isError ? 'error' : recentPending.isPending ? 'loading' : 'ready'}
                busy={startReview.isPending}
                onRetryAction={() => void recentPending.refetch()}
                onAction={runAction}
                onRestart={() => navigate(`/quiz-sets/${recentQuiz.data.quizSetId}`, {
                  state: {
                    materialTitle: recentQuiz.data.materialTitle ?? undefined,
                    restartMain: true,
                  },
                })}
              />
            ) : (
              <EmptyState>아직 완료한 퀴즈가 없어요. 퀴즈를 끝까지 풀면 최근 학습을 여기서 이어갈 수 있어요.</EmptyState>
            )}
          </VStack>

          <ActionButton
            type="button"
            size="large"
            variant="brandSolid"
            onClick={() => navigate('/learning/new')}
          >
            새 문제 만들기
          </ActionButton>

          <VStack as="section" gap="x3" aria-labelledby="review-candidates-heading">
            <SectionHeading id="review-candidates-heading" title="복습할 퀴즈" />
            {!quizManagementAvailable ? (
              <EmptyState>복습 기능을 준비하고 있어요.</EmptyState>
            ) : reviewCandidates.isPending ? (
              <LoadingRows label="복습할 퀴즈를 불러오는 중" />
            ) : reviewCandidates.isError ? (
              <InlineFailure
                message="복습할 퀴즈를 불러오지 못했어요."
                onRetry={() => void reviewCandidates.refetch()}
              />
            ) : reviewCandidates.data.items.length === 0 ? (
              <EmptyState>{getReviewCandidatesEmptyMessage(recentQuiz.data)}</EmptyState>
            ) : (
              <VStack as="ul" className="learning-review-list">
                {reviewCandidates.data.items.map((candidate, index) => (
                  <Box
                    as="li"
                    key={candidate.quizSetId}
                    width="full"
                    borderTopWidth={index ? 1 : 0}
                    borderColor="stroke.neutralSubtle"
                  >
                    <ReviewCandidateRow
                      candidate={candidate}
                      busy={startReview.isPending}
                      onAction={runAction}
                    />
                  </Box>
                ))}
              </VStack>
            )}
          </VStack>

          {startReview.isError ? (
            <Text as="p" textStyle="t5Regular" color="fg.critical" role="alert">
              복습을 시작하지 못했어요. 입력한 내용은 없으니 다시 시도해주세요.
            </Text>
          ) : null}

          <VStack aria-label="학습 관리" borderTopWidth={1} borderColor="stroke.neutralSubtle">
            <PreviewRow title="내 퀴즈 전체 보기" onClick={() => navigate('/learning/quizzes')} />
            <Box width="full" borderTopWidth={1} borderColor="stroke.neutralSubtle">
              <PreviewRow title="내 학습자료 전체 보기" onClick={() => navigate('/learning/materials')} />
            </Box>
          </VStack>
        </VStack>
      </Box>
    </VStack>
  )
}

function RecentQuizPanel({
  review,
  pending,
  actionState,
  busy,
  onRetryAction,
  onAction,
  onRestart,
}: {
  review: LatestReview
  pending: PendingSelfAssessment | null
  actionState: 'loading' | 'ready' | 'error'
  busy: boolean
  onRetryAction: () => void
  onAction: (action: LearningReviewAction) => void
  onRestart: () => void
}) {
  if (!review.quizSetId || !review.sourceAttemptId) return null
  const primaryAction = resolveRecentQuizAction(review, pending)
  return (
    <VStack bg="bg.brandWeak" borderRadius="r3" p="x4" gap="x4" align="flex-start">
      <VStack gap="x1" align="flex-start">
        <Text className="learning-long-title" textStyle="t5Bold" color="fg.neutral">
          {review.quizTitle}
        </Text>
        <Text textStyle="t4Regular" color="fg.neutralMuted">
          {review.materialTitle} · {review.totalQuestionCount}문제 · {review.attemptNumber}회차
        </Text>
        {review.completedAt ? (
          <Text textStyle="t3Regular" color="fg.neutralMuted">
            최근 학습 {formatDate(review.completedAt)}
          </Text>
        ) : null}
      </VStack>
      {actionState === 'error' ? (
        <VStack gap="x2" align="flex-start">
          <Text as="p" textStyle="t4Regular" color="fg.critical" role="alert">
            다음 학습 행동을 확인하지 못했어요.
          </Text>
          <ActionButton type="button" size="small" variant="ghost" onClick={onRetryAction}>
            다시 시도
          </ActionButton>
        </VStack>
      ) : null}
      <Flex className="learning-management-actions" gap="x2" width="full">
        <ActionButton
          type="button"
          size="medium"
          variant="neutralWeak"
          disabled={busy}
          onClick={onRestart}
        >
          전체 문제 다시 풀기
        </ActionButton>
        {actionState === 'loading' ? (
          <ActionButton type="button" size="medium" variant="brandSolid" disabled>
            다음 행동 확인 중
          </ActionButton>
        ) : actionState === 'ready' && primaryAction ? (
          <ActionButton
            type="button"
            size="medium"
            variant="brandSolid"
            disabled={busy}
            onClick={() => onAction(primaryAction)}
          >
            {busy ? '복습 준비 중' : primaryAction.label}
          </ActionButton>
        ) : null}
      </Flex>
    </VStack>
  )
}

function ReviewCandidateRow({
  candidate,
  busy,
  onAction,
}: {
  candidate: ReviewCandidate
  busy: boolean
  onAction: (action: LearningReviewAction) => void
}) {
  const action = resolveReviewCandidateAction(candidate)
  return (
    <Flex className="learning-review-row" align="center" justify="space-between" gap="x3">
      <VStack minWidth="0px" gap="x1" align="flex-start">
        <Text className="learning-long-title" textStyle="t5Medium" color="fg.neutral">
          {candidate.quizTitle}
        </Text>
        <Text textStyle="t3Regular" color="fg.neutralMuted">
          최근 학습 {formatDate(candidate.lastLearningActivityAt)}
        </Text>
      </VStack>
      <ActionButton
        type="button"
        size="small"
        variant="neutralWeak"
        disabled={busy}
        onClick={() => onAction(action)}
      >
        {busy ? '준비 중' : action.label}
      </ActionButton>
    </Flex>
  )
}

export function LearningMaterialsPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const pageRef = useRef<HTMLDivElement>(null)
  const back = usePageBack()
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('query') ?? ''
  const [searchDraft, setSearchDraft] = useState(query)
  const searchCompositionRef = useRef(false)
  const requestedPage = Number(searchParams.get('page') ?? '1')
  const page = Number.isInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1
  const expanded = useMemo(
    () => new Set((searchParams.get('expanded') ?? '').split(',').filter(Boolean)),
    [searchParams],
  )
  const materials = useQuery({
    ...managedLearningMaterialQueryOptions.list({ page, size: MATERIAL_PAGE_SIZE, query }),
    placeholderData: keepPreviousData,
    refetchOnWindowFocus: false,
  })

  useEffect(() => {
    if (materials.data && materials.data.totalPages > 0 && page > materials.data.totalPages) {
      const next = new URLSearchParams(searchParams)
      next.set('page', String(materials.data.totalPages))
      setSearchParams(next, { replace: true })
    }
  }, [materials.data, page, searchParams, setSearchParams])

  const updateParams = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(searchParams)
    Object.entries(changes).forEach(([key, value]) => {
      if (value) next.set(key, value)
      else next.delete(key)
    })
    setSearchParams(next, { replace: true })
  }

  const toggleExpanded = (materialId: string) => {
    const next = new Set(expanded)
    if (next.has(materialId)) next.delete(materialId)
    else next.add(materialId)
    updateParams({ expanded: [...next].join(',') || null })
  }

  useEffect(() => {
    if (!searchCompositionRef.current) setSearchDraft(query)
  }, [query])

  const returnTo = resolveLearningMaterialsReturnTo(`${location.pathname}${location.search}`)
    ?? '/learning/materials'
  const restoreScrollTop = (() => {
    const value = (location.state as { restoreScrollTop?: unknown } | null)?.restoreScrollTop
    return typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : undefined
  })()

  useEffect(() => {
    if (restoreScrollTop === undefined || materials.isPending) return
    const frame = requestAnimationFrame(() => pageRef.current?.scrollTo({ top: restoreScrollTop }))
    return () => cancelAnimationFrame(frame)
  }, [materials.isPending, restoreScrollTop])

  const creationState = () => ({
    returnTo,
    returnScrollTop: pageRef.current?.scrollTop ?? 0,
  })

  return (
    <Box ref={pageRef} as="main" className="learning-management-page" bg="bg.layerDefault" minHeight="100dvh" pt="safeArea">
      <VStack className="learning-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x5">
        <LearningScreenHeader title="내 학습자료" onBack={back} />
        <LearningMaterialAddSheet
          onNotion={() => navigate('/learning/import/notion', { state: creationState() })}
          onDirect={() => navigate('/learning/materials/new', {
            state: { sourceType: 'PASTE', title: '', content: '', ...creationState() },
          })}
        />
        <LearningField label="학습자료 제목 검색">
          <LearningTextInput
            type="search"
            value={searchDraft}
            placeholder="제목으로 검색"
            autoFocus
            onCompositionStart={() => {
              searchCompositionRef.current = true
            }}
            onCompositionEnd={(event) => {
              searchCompositionRef.current = false
              setSearchDraft(event.currentTarget.value)
              updateParams({ query: event.currentTarget.value, page: null })
            }}
            onChange={(event) => {
              const value = event.currentTarget.value
              setSearchDraft(value)
              if (shouldCommitLearningSearchInput(
                Boolean((event.nativeEvent as InputEvent).isComposing),
                searchCompositionRef.current,
              )) {
                updateParams({ query: value, page: null })
              }
            }}
          />
        </LearningField>
        {materials.isFetching && materials.data ? (
          <Text as="p" textStyle="t3Regular" color="fg.neutralMuted" role="status" aria-live="polite">
            목록을 업데이트하고 있어요.
          </Text>
        ) : null}
        {materials.isError && materials.data ? (
          <InlineFailure
            message="목록을 새로 불러오지 못했어요. 이전 결과를 유지하고 있어요."
            onRetry={() => void materials.refetch()}
          />
        ) : null}
        {materials.isPending ? (
          <LoadingRows label="학습자료를 불러오는 중" />
        ) : materials.isError && !materials.data ? (
          <InlineFailure
            message="학습자료를 불러오지 못했어요."
            onRetry={() => void materials.refetch()}
          />
        ) : materials.data.items.length === 0 ? (
          <EmptyState
            action={query.trim() ? (
              <ActionButton type="button" size="medium" variant="neutralWeak" onClick={() => updateParams({ query: null, page: null })}>
                검색어 지우기
              </ActionButton>
            ) : undefined}
          >
            {query.trim() ? `“${query.trim()}”와 일치하는 학습자료가 없어요.` : '아직 저장한 학습자료가 없어요.'}
          </EmptyState>
        ) : (
          <VStack as="ul" className="learning-management-list" gap="x3">
            {materials.data.items.map((material) => (
              <MaterialDisclosureCard
                key={material.materialId}
                material={material}
                expanded={expanded.has(material.materialId)}
                disabled={materials.isPlaceholderData}
                onToggle={() => toggleExpanded(material.materialId)}
                onCreateQuiz={() => navigate(`/learning/${material.materialId}/quiz`, { state: { materialTitle: material.title } })}
                onEdit={() => navigate(`/learning/materials/${material.materialId}`, { state: { returnTo: `/learning/materials?${searchParams.toString()}` } })}
              />
            ))}
          </VStack>
        )}
        {materials.data ? (
          <Pagination
            label="학습자료 페이지"
            page={materials.data.page}
            totalPages={materials.data.totalPages}
            disabled={materials.isFetching}
            onChange={(nextPage) => updateParams({ page: String(nextPage) })}
          />
        ) : null}
      </VStack>
    </Box>
  )
}

function LearningMaterialAddSheet({ onNotion, onDirect }: {
  onNotion: () => void
  onDirect: () => void
}) {
  return (
    <BottomSheet.Root>
      <BottomSheet.Trigger asChild>
        <ActionButton
          className="learning-full-width-action"
          type="button"
          size="large"
          variant="brandSolid"
        >
          학습자료 추가하기
        </ActionButton>
      </BottomSheet.Trigger>
      <Portal>
        <BottomSheet.Backdrop />
        <BottomSheet.Positioner>
          <BottomSheet.Content>
            <BottomSheet.Header>
              <BottomSheet.Title>학습자료 추가하기</BottomSheet.Title>
              <BottomSheet.Description>자료를 가져올 방법을 선택해 주세요.</BottomSheet.Description>
            </BottomSheet.Header>
            <BottomSheet.Body>
              <LearningActionList
                label="학습자료 추가 방법"
                rows={[
                  {
                    id: 'notion',
                    title: '노션에서 가져오기',
                    detail: '노션 페이지 하나를 복사해 확인·수정해요',
                    onClick: onNotion,
                  },
                  {
                    id: 'direct',
                    title: '직접 입력하기',
                    detail: '제목과 본문을 직접 작성해요',
                    onClick: onDirect,
                  },
                ]}
              />
            </BottomSheet.Body>
            <BottomSheet.Footer>
              <BottomSheet.CloseButton asChild>
                <ActionButton type="button" size="large" variant="neutralWeak">
                  취소
                </ActionButton>
              </BottomSheet.CloseButton>
            </BottomSheet.Footer>
          </BottomSheet.Content>
        </BottomSheet.Positioner>
      </Portal>
    </BottomSheet.Root>
  )
}

function MaterialDisclosureCard({
  material,
  expanded,
  disabled,
  onToggle,
  onCreateQuiz,
  onEdit,
}: {
  material: LearningMaterialSummary
  expanded: boolean
  disabled: boolean
  onToggle: () => void
  onCreateQuiz: () => void
  onEdit: () => void
}) {
  const locked = material.contentEditStatus === 'LOCKED_GENERATING'
  const detailId = `material-${material.materialId}-detail`
  const lockReasonId = `material-${material.materialId}-lock-reason`
  const actions = getLearningMaterialManagementActions(material.contentEditStatus, disabled)
  return (
    <Box as="li" borderWidth={1} borderColor="stroke.neutralSubtle" borderRadius="r3">
      <button
        className="learning-disclosure-trigger"
        type="button"
        aria-expanded={expanded}
        aria-controls={detailId}
        disabled={disabled}
        onClick={onToggle}
      >
        <VStack minWidth="0px" gap="x1" align="flex-start">
          <Text className="learning-long-title" textStyle="t5Bold" color="fg.neutral">
            {material.title}
          </Text>
          <Text textStyle="t4Regular" color="fg.neutralMuted">
            {material.sourceType === 'NOTION' ? 'Notion에서 가져옴' : '직접 입력'} · {formatDate(material.updatedAt)}
            {locked ? ' · 문제 생성 중' : ''}
          </Text>
        </VStack>
        <Box
          className={expanded ? 'learning-disclosure-icon learning-disclosure-icon-open' : 'learning-disclosure-icon'}
          aria-hidden
        >
          <Icon svg={<IconChevronRightLine />} size="x4_5" />
        </Box>
      </button>
      {expanded ? (
        <VStack id={detailId} className="learning-disclosure-detail" gap="x2">
          <Flex className="learning-management-actions" gap="x2" width="full">
            {actions.map((action) => (
              <ActionButton
                key={action.id}
                type="button"
                size="medium"
                variant="neutralWeak"
                disabled={action.disabled}
                aria-label={`${material.title} ${action.label}`}
                aria-describedby={action.id === 'create-quiz' && locked ? lockReasonId : undefined}
                onClick={action.id === 'create-quiz' ? onCreateQuiz : onEdit}
              >
                {action.label}
              </ActionButton>
            ))}
          </Flex>
          {locked ? (
            <Text id={lockReasonId} as="p" textStyle="t3Regular" color="fg.neutralMuted">
              이 학습자료로 문제를 만들고 있어요. 완료되면 다시 만들 수 있어요.
            </Text>
          ) : null}
        </VStack>
      ) : null}
    </Box>
  )
}

export function LearningMaterialEditPage({ materialId }: { materialId: string }) {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const headingRef = useRef<HTMLHeadingElement>(null)
  const detail = useQuery({
    ...managedLearningMaterialQueryOptions.detail(materialId),
    enabled: Boolean(materialId),
    refetchOnWindowFocus: false,
    refetchInterval: (query) =>
      query.state.data?.contentEditStatus === 'LOCKED_GENERATING' ? 5_000 : false,
  })
  const [draft, setDraft] = useState<{ title: string; content: string } | null>(null)
  const [errors, setErrors] = useState<{ title?: string; content?: string }>({})
  const [saveError, setSaveError] = useState<string>()

  useEffect(() => {
    headingRef.current?.focus()
  }, [])

  useEffect(() => {
    if (detail.data && !draft) {
      setDraft({ title: detail.data.title, content: detail.data.content })
    }
  }, [detail.data, draft])

  const save = useMutation({
    mutationFn: (payload: { title: string; content?: string }) =>
      updateManagedLearningMaterial(materialId, payload),
    onSuccess: async (updated) => {
      queryClient.setQueryData(learningMaterialKeys.detail(materialId), updated)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: learningMaterialKeys.all }),
        queryClient.invalidateQueries({ queryKey: quizManagementKeys.all }),
        queryClient.invalidateQueries({ queryKey: ['private', 'quiz-review'] }),
        queryClient.invalidateQueries({ queryKey: ['private', 'home'] }),
      ])
      const returnTo = resolveLearningMaterialsReturnTo(
        (location.state as { returnTo?: unknown } | null)?.returnTo,
      )
      const returnScrollTop = (location.state as { returnScrollTop?: unknown } | null)?.returnScrollTop
      navigate(returnTo ?? '/learning/materials', {
        replace: true,
        state: {
          saved: true,
          ...(typeof returnScrollTop === 'number' ? { restoreScrollTop: returnScrollTop } : {}),
        },
      })
    },
    onError: (error) => {
      setSaveError(error instanceof Error ? error.message : '학습자료를 저장하지 못했어요. 다시 시도해주세요.')
    },
  })

  const dirty = Boolean(
    draft && detail.data && (draft.title !== detail.data.title || draft.content !== detail.data.content),
  )
  useEffect(() => {
    if (!dirty) return
    const handleBeforeUnload = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [dirty])

  const restoringHistoryRef = useRef(false)
  useEffect(() => {
    if (!dirty) return
    const handlePopState = () => {
      if (restoringHistoryRef.current) {
        restoringHistoryRef.current = false
        return
      }
      if (window.confirm('변경사항을 버리고 나갈까요?')) return
      restoringHistoryRef.current = true
      window.history.forward()
    }
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [dirty])

  const goBack = () => {
    if (dirty && !window.confirm('변경사항을 버리고 나갈까요?')) return
    const navigation = resolveLearningMaterialEditBackNavigation(
      (location.state as { returnTo?: unknown } | null)?.returnTo,
    )
    const returnScrollTop = (location.state as { returnScrollTop?: unknown } | null)?.returnScrollTop
    navigate(navigation.to, {
      replace: navigation.replace,
      state: typeof returnScrollTop === 'number' ? { restoreScrollTop: returnScrollTop } : undefined,
    })
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!draft || !detail.data) return
    const title = draft.title.trim()
    const titleLength = countUnicodeCodePoints(title)
    const contentLength = countUnicodeCodePoints(draft.content)
    const nextErrors = {
      title: !title ? '제목을 입력해주세요.' : titleLength > 255 ? '제목은 255자 이하로 입력해주세요.' : undefined,
      content:
        detail.data.contentEditStatus === 'EDITABLE' && !draft.content.trim()
          ? '본문을 입력해주세요.'
          : contentLength > 20_000
            ? '본문은 20,000자 이하로 입력해주세요.'
            : undefined,
    }
    setErrors(nextErrors)
    if (nextErrors.title || nextErrors.content) return
    setSaveError(undefined)
    save.mutate({
      title,
      ...(detail.data.contentEditStatus === 'EDITABLE' ? { content: draft.content } : {}),
    })
  }

  return (
    <Box as="main" className="learning-management-page" bg="bg.layerDefault" minHeight="100dvh" pt="safeArea">
      <VStack className="learning-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x5">
        <LearningScreenHeader title="학습자료 수정" onBack={goBack} headingRef={headingRef} />
        {detail.isError ? (
          <InlineFailure message="학습자료를 불러오지 못했어요." onRetry={() => void detail.refetch()} />
        ) : detail.isPending || !draft ? (
          <LoadingRows label="학습자료를 불러오는 중" count={4} />
        ) : (
          <form className="learning-form" onSubmit={handleSubmit}>
            <VStack gap="x5">
              <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
                {detail.data.sourceType === 'NOTION' ? 'Notion에서 가져옴' : '직접 입력'} · 생성 {formatDate(detail.data.createdAt)} · 수정 {formatDate(detail.data.updatedAt)}
              </Text>
              <LearningField
                label="제목"
                error={errors.title}
                characterCount={{ current: countUnicodeCodePoints(draft.title), max: 255 }}
              >
                <LearningTextInput
                  invalid={Boolean(errors.title)}
                  value={draft.title}
                  onChange={(event) => setDraft({ ...draft, title: event.currentTarget.value })}
                />
              </LearningField>
              <LearningField
                label="본문"
                error={errors.content}
                description={detail.data.contentEditStatus === 'LOCKED_GENERATING' ? '문제를 만드는 동안에는 본문을 수정할 수 없어요. 제목은 지금도 저장할 수 있어요.' : undefined}
                characterCount={{ current: countUnicodeCodePoints(draft.content), max: 20_000 }}
              >
                <LearningTextarea
                  invalid={Boolean(errors.content)}
                  value={draft.content}
                  readOnly={detail.data.contentEditStatus === 'LOCKED_GENERATING'}
                  aria-readonly={detail.data.contentEditStatus === 'LOCKED_GENERATING'}
                  onChange={(event) => setDraft({ ...draft, content: event.currentTarget.value })}
                />
              </LearningField>
              <LearningNotice>
                이미 만든 퀴즈와 결과는 바뀌지 않아요. 다음에 만드는 퀴즈부터 반영돼요.
              </LearningNotice>
              {saveError ? (
                <Text as="p" textStyle="t5Regular" color="fg.critical" role="alert">
                  {saveError}
                </Text>
              ) : null}
              <ActionButton type="submit" size="large" variant="brandSolid" disabled={save.isPending || !dirty}>
                {save.isPending ? '저장하는 중...' : '변경사항 저장'}
              </ActionButton>
            </VStack>
          </form>
        )}
      </VStack>
    </Box>
  )
}

export function QuizManagementPage() {
  const back = usePageBack()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const location = useLocation()
  const pageRef = useRef<HTMLDivElement>(null)
  const query = searchParams.get('query') ?? ''
  const requestedPage = Number(searchParams.get('page') ?? '1')
  const page = Number.isInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1
  const focusedQuizId = searchParams.get('focus')
  const expanded = useMemo(
    () => parseExpandedQuizIds(searchParams.get('expanded')),
    [searchParams],
  )
  const quizzes = useQuery({
    ...quizManagementQueryOptions.list({
      page,
      size: QUIZ_SET_PAGE_SIZE,
      query,
      focusQuizSetId: focusedQuizId ?? undefined,
    }),
    placeholderData: keepPreviousData,
    refetchOnWindowFocus: false,
  })
  const [editingId, setEditingId] = useState<string>()
  const [draftTitle, setDraftTitle] = useState('')
  const [renameError, setRenameError] = useState<string>()
  const [savedMessage, setSavedMessage] = useState<string>()
  const restoreScrollTop = (() => {
    const value = (location.state as { restoreScrollTop?: unknown } | null)?.restoreScrollTop
    return typeof value === 'number' && Number.isFinite(value) && value >= 0 ? value : undefined
  })()

  useEffect(() => {
    if (restoreScrollTop === undefined || quizzes.isPending) return
    const frame = requestAnimationFrame(() => pageRef.current?.scrollTo({ top: restoreScrollTop }))
    return () => cancelAnimationFrame(frame)
  }, [quizzes.isPending, restoreScrollTop])
  useEffect(() => {
    if (!focusedQuizId) return
    requestAnimationFrame(() => document.getElementById(`quiz-card-${focusedQuizId}`)?.focus())
  }, [focusedQuizId, quizzes.data])

  const rename = useMutation({
    mutationFn: ({ quizSetId, quizTitle }: { quizSetId: string; quizTitle: string }) =>
      renameManagedQuizSet(quizSetId, quizTitle),
    onSuccess: async (updated) => {
      const editedId = updated.quizSetId
      setEditingId(undefined)
      setRenameError(undefined)
      setSavedMessage(`“${updated.quizTitle}”으로 이름을 변경했어요.`)
      await invalidateQuizManagementQueries(queryClient, editedId)
    },
    onError: () => {
      setRenameError('이름을 저장하지 못했어요. 입력한 이름을 유지했으니 다시 시도해주세요.')
    },
  })

  const updateSearch = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(searchParams)
    Object.entries(changes).forEach(([key, value]) => {
      if (value) next.set(key, value)
      else next.delete(key)
    })
    setSearchParams(next, { replace: true })
  }

  const beginRename = (quiz: QuizSetSummary) => {
    setEditingId(quiz.quizSetId)
    setDraftTitle(quiz.quizTitle)
    setRenameError(undefined)
    setSavedMessage(undefined)
  }
  const cancelRename = (quizSetId: string) => {
    if (rename.isPending && editingId === quizSetId) return
    setEditingId(undefined)
    setRenameError(undefined)
  }
  const saveRename = (quizSetId: string) => {
    const title = draftTitle.trim()
    const length = countUnicodeCodePoints(title)
    if (!title) {
      setRenameError('퀴즈 이름을 입력해주세요.')
      return
    }
    if (length > 255) {
      setRenameError('퀴즈 이름은 255자 이하로 입력해주세요.')
      return
    }
    setRenameError(undefined)
    rename.mutate({ quizSetId, quizTitle: title })
  }
  const toggleExpanded = (quizSetId: string) => {
    const next = toggleExpandedQuizId(expanded, quizSetId)
    updateSearch({ expanded: [...next].join(',') || null })
  }

  const openQuizCreation = () => {
    const returnTo = resolveLearningQuizzesReturnTo(`${location.pathname}${location.search}`)
    navigate('/learning/new', {
      state: {
        ...(returnTo ? { returnTo } : {}),
        returnScrollTop: pageRef.current?.scrollTop ?? 0,
      },
    })
  }

  return (
    <Box ref={pageRef} as="main" className="learning-management-page" bg="bg.layerDefault" minHeight="100dvh" pt="safeArea">
      <VStack className="learning-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x5">
        <LearningScreenHeader title="내 퀴즈" onBack={back} />
        <ActionButton
          className="learning-full-width-action"
          type="button"
          size="large"
          variant="brandSolid"
          onClick={openQuizCreation}
        >
          퀴즈 만들기
        </ActionButton>
        <LearningField label="퀴즈 제목 검색">
          <LearningTextInput
            type="search"
            value={query}
            placeholder="퀴즈 제목으로 검색"
            autoFocus
            onChange={(event) => updateSearch({ query: event.currentTarget.value, page: null, focus: null })}
          />
        </LearningField>
        <Box className="learning-visually-hidden" role="status" aria-live="polite">
          {savedMessage}
        </Box>
        {quizzes.isFetching && quizzes.data ? (
          <Text as="p" textStyle="t3Regular" color="fg.neutralMuted" role="status" aria-live="polite">
            목록을 업데이트하고 있어요.
          </Text>
        ) : null}
        {quizzes.isError && quizzes.data ? (
          <InlineFailure
            message="목록을 새로 불러오지 못했어요. 이전 결과를 유지하고 있어요."
            onRetry={() => void quizzes.refetch()}
          />
        ) : null}
        {quizzes.isPending ? (
          <LoadingRows label="퀴즈를 불러오는 중" />
        ) : quizzes.isError && !quizzes.data ? (
          <InlineFailure message="퀴즈를 불러오지 못했어요." onRetry={() => void quizzes.refetch()} />
        ) : quizzes.data.items.length === 0 ? (
          <EmptyState
            action={query.trim() ? (
              <ActionButton type="button" size="medium" variant="neutralWeak" onClick={() => updateSearch({ query: null, page: null })}>
                검색어 지우기
              </ActionButton>
            ) : undefined}
          >
            {query.trim() ? `“${query.trim()}”와 일치하는 퀴즈가 없어요.` : '아직 만든 퀴즈가 없어요.'}
          </EmptyState>
        ) : (
          <VStack as="ul" className="learning-management-list" gap="x3">
            {quizzes.data.items.map((quiz) => (
              <QuizManagementCard
                key={quiz.quizSetId}
                quizId={quiz.quizSetId}
                title={quiz.quizTitle}
                materialTitle={quiz.materialTitle}
                questionCount={quiz.questionCount}
                status={quiz.status}
                expanded={expanded.has(quiz.quizSetId)}
                disclosureDisabled={quizzes.isFetching}
                renameOpen={editingId === quiz.quizSetId}
                renameDraft={draftTitle}
                renameError={editingId === quiz.quizSetId ? renameError : undefined}
                renameSaving={rename.isPending && editingId === quiz.quizSetId}
                onToggle={() => toggleExpanded(quiz.quizSetId)}
                onRenameOpenChange={(open) => {
                  if (open) beginRename(quiz)
                  else cancelRename(quiz.quizSetId)
                }}
                onRenameDraftChange={setDraftTitle}
                onRenameSubmit={() => saveRename(quiz.quizSetId)}
                onStartQuiz={() => {
                  const destination = createNewMainQuizDestination(quiz.quizSetId)
                  navigate(destination.path, { state: destination.state })
                }}
              />
            ))}
          </VStack>
        )}
        {quizzes.data ? (
          <Pagination
            label="퀴즈 페이지"
            page={quizzes.data.page}
            totalPages={quizzes.data.totalPages}
            disabled={quizzes.isFetching}
            onChange={(nextPage) => updateSearch({ page: String(nextPage), focus: null })}
          />
        ) : null}
        {quizManagementMode === 'mock' ? (
          <Text as="p" textStyle="t3Regular" color="fg.neutralSubtle">
            개발 환경에서는 미완성 관리 API와 동일한 타입의 mock adapter를 사용하고 있어요.
          </Text>
        ) : null}
      </VStack>
    </Box>
  )
}

function Pagination({
  label,
  page,
  totalPages,
  disabled,
  onChange,
}: {
  label: string
  page: number
  totalPages: number
  disabled: boolean
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) return null
  return (
    <nav className="learning-pagination" aria-label={label}>
      <ActionButton type="button" size="medium" variant="ghost" disabled={disabled || page <= 1} onClick={() => onChange(page - 1)}>
        이전
      </ActionButton>
      <Text as="span" textStyle="t4Medium" color="fg.neutral" aria-current="page">
        {page} / {totalPages}
      </Text>
      <ActionButton type="button" size="medium" variant="ghost" disabled={disabled || page >= totalPages} onClick={() => onChange(page + 1)}>
        다음
      </ActionButton>
    </nav>
  )
}
