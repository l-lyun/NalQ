import { IconChevronRightLine } from '@karrotmarket/react-monochrome-icon'
import {
  ActionButton,
  Box,
  Flex,
  Icon,
  Skeleton,
  Text,
  VStack,
} from '@seed-design/react'
import {
  keepPreviousData,
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  type FormEvent,
  type KeyboardEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import {
  useLocation,
  useNavigate,
  useParams,
  useSearchParams,
} from 'react-router-dom'

import { learningMaterialKeys } from '@/features/learning-material/api/learningMaterial.api'
import {
  updateManagedLearningMaterial,
} from '@/features/learning-material/api/learningMaterialManagementAdapter'
import type { LearningMaterialSummary } from '@/features/learning-material/api/learningMaterial.types'
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
  resolveQuizManagementActions,
  resolveQuizManagementActionState,
} from '@/features/quiz/model/quizManagementActions'
import {
  type LearningReviewAction,
  resolveRecentQuizAction,
  resolveReviewCandidateAction,
} from '@/features/quiz/model/learningReviewActions'

import {
  LearningField,
  LearningNotice,
  LearningScreenHeader,
  LearningTextInput,
  LearningTextarea,
} from './components/LearningPrimitives'
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
      <Text as="h2" id={id} textStyle="t9Bold" color="fg.neutral">
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
        <Text className="learning-long-title" textStyle="t6Medium" color="fg.neutral">
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
  const recentQuiz = useQuery({
    ...quizManagementQueryOptions.latestReview(),
    refetchOnWindowFocus: false,
  })
  const recentPending = useQuery({
    ...quizManagementQueryOptions.pendingSelfAssessment(recentQuiz.data?.quizSetId ?? ''),
    enabled: Boolean(recentQuiz.data?.quizSetId),
    refetchOnWindowFocus: false,
  })
  const reviewCandidates = useQuery({
    ...quizManagementQueryOptions.reviewCandidates(REVIEW_CANDIDATE_LIMIT),
    refetchOnWindowFocus: false,
  })
  const [startingAttemptId, setStartingAttemptId] = useState<string>()
  const startReview = useMutation({
    mutationFn: startManagedReviewSession,
    onMutate: (sourceAttemptId) => {
      setStartingAttemptId(sourceAttemptId)
    },
    onSuccess: async (session) => {
      await queryClient.invalidateQueries({ queryKey: ['private', 'quiz-review'] })
      navigate(`/review-sessions/${session.reviewSessionId}`)
    },
    onSettled: () => {
      setStartingAttemptId(undefined)
    },
  })

  const runAction = (action: LearningReviewAction) => {
    if (action.kind === 'navigate') navigate(action.path)
    else startReview.mutate(action.sourceAttemptId)
  }

  return (
    <VStack className="learning-shell" minHeight="100dvh" bg="bg.layerBasement">
      <Box as="main" className="learning-main" bg="bg.layerDefault" width="full" pt="safeArea">
        <VStack
          className="learning-content"
          px="spacingX.globalGutter"
          pt="x6"
          pb="spacingY.screenBottom"
          gap="x8"
        >
          <Text as="h1" textStyle="t12Bold" color="fg.neutral">
            학습
          </Text>

          <VStack as="section" gap="x4" aria-labelledby="recent-quiz-heading">
            <Text as="h2" id="recent-quiz-heading" textStyle="t9Bold" color="fg.neutral">
              최근 퀴즈
            </Text>
            {recentQuiz.isPending ? (
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
                starting={startingAttemptId === recentQuiz.data.sourceAttemptId}
                onRetryAction={() => void recentPending.refetch()}
                onAction={runAction}
                onRestart={() => navigate(`/quiz-sets/${recentQuiz.data.quizSetId}`)}
              />
            ) : (
              <EmptyState>아직 만든 퀴즈가 없어요. 새 문제를 만들어 학습을 시작해보세요.</EmptyState>
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
            {reviewCandidates.isPending ? (
              <LoadingRows label="복습할 퀴즈를 불러오는 중" />
            ) : reviewCandidates.isError ? (
              <InlineFailure
                message="복습할 퀴즈를 불러오지 못했어요."
                onRetry={() => void reviewCandidates.refetch()}
              />
            ) : reviewCandidates.data.items.length === 0 ? (
              <EmptyState>복습할 퀴즈가 없어요. 지금까지의 학습이 잘 정리되어 있어요.</EmptyState>
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
                      starting={startingAttemptId === candidate.sourceAttemptId}
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
  starting,
  onRetryAction,
  onAction,
  onRestart,
}: {
  review: LatestReview
  pending: PendingSelfAssessment | null
  actionState: 'loading' | 'ready' | 'error'
  starting: boolean
  onRetryAction: () => void
  onAction: (action: LearningReviewAction) => void
  onRestart: () => void
}) {
  if (!review.quizSetId || !review.sourceAttemptId) return null
  const primaryAction = resolveRecentQuizAction(review, pending)
  return (
    <VStack bg="bg.neutralWeak" borderRadius="r3" p="x4" gap="x4" align="flex-start">
      <VStack gap="x1" align="flex-start">
        <Text className="learning-long-title" textStyle="t7Bold" color="fg.neutral">
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
            disabled={starting}
            onClick={() => onAction(primaryAction)}
          >
            {starting ? '복습 준비 중' : primaryAction.label}
          </ActionButton>
        ) : null}
      </Flex>
    </VStack>
  )
}

function ReviewCandidateRow({
  candidate,
  starting,
  onAction,
}: {
  candidate: ReviewCandidate
  starting: boolean
  onAction: (action: LearningReviewAction) => void
}) {
  const action = resolveReviewCandidateAction(candidate)
  const stateLabel = candidate.pendingSelfAssessmentAttemptId
    ? '자기평가가 남아 있어요'
    : candidate.activeReviewSessionId
      ? '복습 중'
      : `틀린 문제 ${candidate.reviewQuestionCount}개`
  return (
    <Flex className="learning-review-row" align="center" justify="space-between" gap="x3">
      <VStack minWidth="0px" gap="x1" align="flex-start">
        <Text className="learning-long-title" textStyle="t6Medium" color="fg.neutral">
          {candidate.quizTitle}
        </Text>
        <Text textStyle="t3Regular" color="fg.neutralMuted">
          {candidate.materialTitle} · {stateLabel}
        </Text>
        <Text textStyle="t3Regular" color="fg.neutralMuted">
          최근 학습 {formatDate(candidate.lastLearningActivityAt)}
        </Text>
      </VStack>
      <ActionButton
        type="button"
        size="small"
        variant="neutralWeak"
        disabled={starting}
        onClick={() => onAction(action)}
      >
        {starting ? '준비 중' : action.label}
      </ActionButton>
    </Flex>
  )
}

export function LearningMaterialsPage() {
  const navigate = useNavigate()
  const back = usePageBack()
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('query') ?? ''
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

  return (
    <Box as="main" className="learning-management-page" bg="bg.layerDefault" minHeight="100dvh" pt="safeArea">
      <VStack className="learning-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x5">
        <LearningScreenHeader title="내 학습자료" onBack={back} />
        <LearningField label="학습자료 제목 검색">
          <LearningTextInput
            type="search"
            value={query}
            placeholder="제목으로 검색"
            autoFocus
            onChange={(event) => updateParams({ query: event.currentTarget.value, page: null })}
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
  const detailId = `material-${material.materialId}-detail`
  const detail = useQuery({
    ...managedLearningMaterialQueryOptions.detail(material.materialId),
    enabled: expanded,
    refetchOnWindowFocus: false,
  })
  const locked = material.contentEditStatus === 'LOCKED_GENERATING'
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
          <Text className="learning-long-title" textStyle="t7Bold" color="fg.neutral">
            {material.title}
          </Text>
          <Text textStyle="t4Regular" color="fg.neutralMuted">
            {material.sourceType === 'NOTION' ? 'Notion에서 가져옴' : '직접 입력'} · {formatDate(material.updatedAt)}
          </Text>
          {locked ? (
            <Text textStyle="t4Regular" color="fg.warning">
              문제 생성 중 · 본문은 잠시 수정할 수 없어요
            </Text>
          ) : null}
        </VStack>
        <Box
          className={expanded ? 'learning-disclosure-icon learning-disclosure-icon-open' : 'learning-disclosure-icon'}
          aria-hidden
        >
          <Icon svg={<IconChevronRightLine />} size="x4_5" />
        </Box>
      </button>
      {expanded ? (
        <VStack id={detailId} className="learning-disclosure-detail" gap="x4" p="x4" pt="x1">
          {detail.isPending ? (
            <LoadingRows label={`${material.title} 상세를 불러오는 중`} count={1} />
          ) : detail.isError ? (
            <InlineFailure message="학습자료 내용을 불러오지 못했어요." onRetry={() => void detail.refetch()} />
          ) : (
            <>
              <Text className="learning-material-preview" as="p" textStyle="t5Regular" color="fg.neutralMuted">
                {detail.data.content}
              </Text>
              <Text as="p" textStyle="t3Regular" color="fg.neutralMuted">
                {detail.data.contentLength.toLocaleString('ko-KR')}자
              </Text>
            </>
          )}
          <Flex className="learning-management-actions" gap="x2" width="full">
            <ActionButton
              type="button"
              size="medium"
              variant="neutralWeak"
              disabled={locked}
              aria-describedby={locked ? `${detailId}-lock-reason` : undefined}
              onClick={onCreateQuiz}
            >
              퀴즈 만들기
            </ActionButton>
            <ActionButton type="button" size="medium" variant="neutralWeak" onClick={onEdit}>
              학습자료 변경
            </ActionButton>
          </Flex>
          {locked ? (
            <Text id={`${detailId}-lock-reason`} as="p" textStyle="t3Regular" color="fg.neutralMuted">
              진행 중인 문제 생성이 끝나면 이 자료로 새 퀴즈를 만들 수 있어요.
            </Text>
          ) : null}
        </VStack>
      ) : null}
    </Box>
  )
}

export function LearningMaterialEditPage() {
  const { materialId = '' } = useParams()
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
      const returnTo = (location.state as { returnTo?: string } | null)?.returnTo
      navigate(returnTo ?? '/learning/materials', { replace: true, state: { saved: true } })
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
    const returnTo = (location.state as { returnTo?: string } | null)?.returnTo
    navigate(returnTo ?? '/learning/materials')
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
        <LearningScreenHeader title="학습자료 변경" onBack={goBack} headingRef={headingRef} />
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
  const query = searchParams.get('query') ?? ''
  const requestedPage = Number(searchParams.get('page') ?? '1')
  const page = Number.isInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1
  const focusedQuizId = searchParams.get('focus')
  const quizzes = useQuery({
    ...quizManagementQueryOptions.list({ page, size: QUIZ_SET_PAGE_SIZE, query }),
    placeholderData: keepPreviousData,
    refetchOnWindowFocus: false,
  })
  const latestReview = useQuery({
    ...quizManagementQueryOptions.latestReview(),
    refetchOnWindowFocus: false,
  })
  const pendingQueries = useQueries({
    queries: (quizzes.data?.items ?? []).map((quiz) => ({
      ...quizManagementQueryOptions.pendingSelfAssessment(quiz.quizSetId),
      enabled: quiz.status === 'READY',
      refetchOnWindowFocus: false,
    })),
  })
  const pendingByQuizSet = new Map<string, PendingSelfAssessment | null>()
  ;(quizzes.data?.items ?? []).forEach((quiz, index) => {
    pendingByQuizSet.set(quiz.quizSetId, pendingQueries[index]?.data ?? null)
  })
  const [editingId, setEditingId] = useState<string>()
  const [draftTitle, setDraftTitle] = useState('')
  const [renameError, setRenameError] = useState<string>()
  const [savedMessage, setSavedMessage] = useState<string>()
  const inputRef = useRef<HTMLInputElement>(null)
  const renameButtons = useRef(new Map<string, HTMLButtonElement>())

  useEffect(() => {
    if (editingId) {
      inputRef.current?.focus()
      inputRef.current?.select()
    }
  }, [editingId, renameError])

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
      requestAnimationFrame(() => renameButtons.current.get(editedId)?.focus())
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
    setEditingId(undefined)
    setRenameError(undefined)
    requestAnimationFrame(() => renameButtons.current.get(quizSetId)?.focus())
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

  return (
    <Box as="main" className="learning-management-page" bg="bg.layerDefault" minHeight="100dvh" pt="safeArea">
      <VStack className="learning-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x5">
        <LearningScreenHeader title="내 퀴즈" onBack={back} />
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
            ) : (
              <ActionButton type="button" size="medium" variant="neutralWeak" onClick={() => navigate('/learning')}>
                학습으로 돌아가기
              </ActionButton>
            )}
          >
            {query.trim() ? `“${query.trim()}”와 일치하는 퀴즈가 없어요.` : '아직 만든 퀴즈가 없어요.'}
          </EmptyState>
        ) : (
          <VStack as="ul" className="learning-management-list" gap="x3">
            {quizzes.data.items.map((quiz, index) => {
              const pendingQuery = pendingQueries[index]
              const pendingState = quiz.status !== 'READY'
                ? 'ready'
                : pendingQuery?.isError
                  ? 'error'
                  : pendingQuery?.isPending
                    ? 'loading'
                    : 'ready'
              return (
                <QuizManagementCard
                key={quiz.quizSetId}
                quiz={quiz}
                latestReview={latestReview.data}
                pending={pendingByQuizSet.get(quiz.quizSetId) ?? null}
                actionState={resolveQuizManagementActionState(
                  pendingState,
                  latestReview.isError
                    ? 'error'
                    : latestReview.isPending
                      ? 'loading'
                      : 'ready',
                )}
                editing={editingId === quiz.quizSetId}
                draftTitle={draftTitle}
                renameError={editingId === quiz.quizSetId ? renameError : undefined}
                saving={rename.isPending && editingId === quiz.quizSetId}
                inputRef={inputRef}
                registerRenameButton={(element) => {
                  if (element) renameButtons.current.set(quiz.quizSetId, element)
                  else renameButtons.current.delete(quiz.quizSetId)
                }}
                onDraftTitleChange={setDraftTitle}
                onBeginRename={() => beginRename(quiz)}
                onCancelRename={() => cancelRename(quiz.quizSetId)}
                onSaveRename={() => saveRename(quiz.quizSetId)}
                onRetryAction={() => {
                  if (quiz.status === 'READY') void pendingQuery?.refetch()
                  void latestReview.refetch()
                }}
                onNavigate={(path) => navigate(path)}
              />
              )
            })}
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

function QuizManagementCard({
  quiz,
  latestReview,
  pending,
  actionState,
  editing,
  draftTitle,
  renameError,
  saving,
  inputRef,
  registerRenameButton,
  onDraftTitleChange,
  onBeginRename,
  onCancelRename,
  onSaveRename,
  onRetryAction,
  onNavigate,
}: {
  quiz: QuizSetSummary
  latestReview?: LatestReview
  pending: PendingSelfAssessment | null
  actionState: 'loading' | 'ready' | 'error'
  editing: boolean
  draftTitle: string
  renameError?: string
  saving: boolean
  inputRef: React.RefObject<HTMLInputElement | null>
  registerRenameButton: (element: HTMLButtonElement | null) => void
  onDraftTitleChange: (value: string) => void
  onBeginRename: () => void
  onCancelRename: () => void
  onSaveRename: () => void
  onRetryAction: () => void
  onNavigate: (path: string) => void
}) {
  const actions = resolveQuizManagementActions(quiz, pending, latestReview)
  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      onCancelRename()
    }
  }
  return (
    <Box
      as="li"
      id={`quiz-card-${quiz.quizSetId}`}
      tabIndex={-1}
      className="learning-quiz-card"
      borderWidth={1}
      borderColor="stroke.neutralSubtle"
      borderRadius="r3"
      p="x4"
    >
      <VStack gap="x4" align="flex-start">
        {editing ? (
          <form
            className="learning-form"
            onSubmit={(event) => {
              event.preventDefault()
              onSaveRename()
            }}
          >
            <VStack gap="x3">
              <LearningField
                label="퀴즈 이름"
                error={renameError}
                characterCount={{ current: countUnicodeCodePoints(draftTitle), max: 255 }}
              >
                <LearningTextInput
                  ref={inputRef}
                  invalid={Boolean(renameError)}
                  value={draftTitle}
                  disabled={saving}
                  enterKeyHint="done"
                  onKeyDown={handleKeyDown}
                  onChange={(event) => onDraftTitleChange(event.currentTarget.value)}
                />
              </LearningField>
              <Flex className="learning-management-actions" gap="x2">
                <ActionButton type="button" size="medium" variant="ghost" disabled={saving} onClick={onCancelRename}>
                  취소
                </ActionButton>
                <ActionButton type="submit" size="medium" variant="neutralWeak" disabled={saving}>
                  {saving ? '저장하는 중...' : '저장'}
                </ActionButton>
              </Flex>
            </VStack>
          </form>
        ) : (
          <>
            <VStack gap="x1" align="flex-start">
              <Text className="learning-long-title" textStyle="t7Bold" color="fg.neutral">
                {quiz.quizTitle}
              </Text>
              <Text textStyle="t4Regular" color="fg.neutralMuted">
                {quiz.materialTitle}{quiz.questionCount ? ` · ${quiz.questionCount}문제` : ''}
              </Text>
              <Text textStyle="t4Regular" color="fg.neutralMuted">
                {quizStatusLabel(quiz)}{quiz.lastAttemptAt ? ` · 최근 학습 ${formatDate(quiz.lastAttemptAt)}` : ''}
              </Text>
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
            ) : actionState === 'loading' ? (
              <Text as="p" textStyle="t4Regular" color="fg.neutralMuted" role="status">
                다음 학습 행동을 확인하고 있어요.
              </Text>
            ) : null}
            <Flex className="learning-management-actions" gap="x2" width="full">
              <ActionButton
                ref={registerRenameButton}
                type="button"
                size="medium"
                variant="ghost"
                disabled={saving}
                onClick={onBeginRename}
              >
                이름 변경
              </ActionButton>
              {actionState === 'ready' ? actions.map((action) => (
                <ActionButton
                  key={action.label}
                  type="button"
                  size="medium"
                  variant={action.primary ? 'neutralWeak' : 'ghost'}
                  disabled={saving}
                  onClick={() => onNavigate(action.path)}
                >
                  {action.label}
                </ActionButton>
              )) : null}
            </Flex>
          </>
        )}
      </VStack>
    </Box>
  )
}

function quizStatusLabel(quiz: QuizSetSummary) {
  if (quiz.status === 'GENERATING') return '문제를 만들고 있어요'
  if (quiz.status === 'FAILED') return '문제 생성에 실패했어요'
  return quiz.lastAttemptAt ? '최근 풀이 있음' : '풀기 전'
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
