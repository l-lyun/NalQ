import { ActionButton, ProgressCircle, Text, VStack } from '@seed-design/react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

import { shouldRetryQuery } from '@/app/providers/queryClient'
import {
  createQuizSet,
  createReviewSession,
  getActiveQuizSet,
  getLatestReview,
  getPendingSelfAssessment,
  getQuizResult,
  getQuizSet,
  getReviewResult,
  getReviewSession,
  saveEssayAssessment,
  saveReviewEssayAssessment,
  submitQuiz,
  submitReview,
  updateShortAnswerGrading,
} from '@/features/quiz/api/quiz.api'
import type { QuizSetState } from '@/features/quiz/api/quiz.types'
import {
  adaptQuizResult,
  adaptReviewResult,
  includedQuestionTypes,
} from '@/features/quiz/model/quizAdapter'
import {
  loadRequestedConfig,
  saveRequestedConfig,
} from '@/features/quiz/model/quizRequestedConfigStorage'
import { createUuidV4 } from '@/features/quiz/model/randomUuid'
import { useCurrentUser } from '@/features/auth/model/auth.queries'

import { QuizFlowPage } from './QuizFlowPage'
import type {
  QuizConditions,
  QuizGenerationFailure,
  QuizGenerationState,
  QuizQuestion,
  QuizResult,
} from './quiz.types'

const emptyResult: QuizResult = {
  status: 'COMPLETED',
  summary: {
    correctCount: 0,
    gradedCount: 0,
    essayCorrectCount: 0,
    essayPartialCount: 0,
    essayIncorrectCount: 0,
    reviewCount: 0,
  },
  items: [],
}

function RouteStatus({ message, retry }: { message: string; retry?: () => void }) {
  return (
    <VStack minHeight="100dvh" align="center" justify="center" gap="x4" px="spacingX.globalGutter">
      <Text role="status" textStyle="t5Regular" color="fg.neutralMuted">{message}</Text>
      {retry ? <ActionButton size="medium" variant="neutralWeak" onClick={retry}>다시 시도</ActionButton> : null}
    </VStack>
  )
}

function RouteLoading() {
  return (
    <VStack minHeight="100dvh" align="center" justify="center" gap="x4">
      <ProgressCircle.Root aria-label="퀴즈 정보를 불러오는 중" tone="brand" size="40">
        <ProgressCircle.Track /><ProgressCircle.Range />
      </ProgressCircle.Root>
    </VStack>
  )
}

function generationStateFrom(
  state: QuizSetState,
  requestedConfig: QuizConditions | undefined,
): QuizGenerationState {
  if (state.status === 'GENERATING') return { status: 'GENERATING', requestedConfig }
  if (state.status === 'FAILED') {
    return {
      status: 'ERROR',
      error: {
        kind: state.failure.code,
        retryable: Boolean(requestedConfig && state.failure.retryable),
        message: state.failure.message,
      },
    }
  }
  return {
    status: 'READY',
    ready: {
      actualCount: state.questions.length,
      includedTypes: includedQuestionTypes(state.questions),
      requestedConfig,
    },
  }
}

function validateQuestions(questions: QuizQuestion[]) {
  for (const question of questions) {
    if (
      question.type === 'MULTIPLE_CHOICE' &&
      (question.choices.length < 3 || question.choices.length > 5)
    ) {
      throw new Error('객관식 보기 개수가 계약 범위를 벗어났습니다.')
    }
  }
  return questions
}

export function QuizMaterialRoutePage() {
  const { materialId } = useParams<{ materialId: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const currentUser = useCurrentUser()
  const materialTitle = (location.state as { materialTitle?: string } | null)?.materialTitle ?? '학습자료'
  const [quizSetId, setQuizSetId] = useState<string>()
  const idempotencyKeyRef = useRef<string | undefined>(undefined)
  const lastConditionsRef = useRef<QuizConditions | undefined>(undefined)

  const activeQuery = useQuery({
    queryKey: ['private', 'quiz', 'active', materialId],
    queryFn: ({ signal }) => getActiveQuizSet(materialId!, signal),
    enabled: Boolean(materialId),
    retry: shouldRetryQuery,
  })

  useEffect(() => {
    if (activeQuery.data?.quizSetId) {
      navigate(`/quiz-sets/${activeQuery.data.quizSetId}`, {
        replace: true,
        state: { materialTitle },
      })
    }
  }, [activeQuery.data, materialTitle, navigate])

  const quizSetQuery = useQuery({
    queryKey: ['private', 'quiz-set', quizSetId],
    queryFn: async ({ signal }) => {
      const state = await getQuizSet(quizSetId!, signal)
      if (state.status === 'READY') validateQuestions(state.questions)
      return state
    },
    enabled: Boolean(quizSetId),
    retry: shouldRetryQuery,
    refetchInterval: (query) => {
      const data = query.state.data
      return data?.status === 'GENERATING' ? data.pollAfterSeconds * 1_000 : false
    },
  })

  const createMutation = useMutation({
    mutationFn: async (conditions: QuizConditions) => {
      lastConditionsRef.current = conditions
      const idempotencyKey = idempotencyKeyRef.current ?? createUuidV4()
      idempotencyKeyRef.current = idempotencyKey
      return createQuizSet(materialId!, conditions, idempotencyKey)
    },
    onSuccess: (created) => {
      idempotencyKeyRef.current = undefined
      if (currentUser.data) {
        saveRequestedConfig(currentUser.data.id, created.quizSetId, created.requestedConfig)
      }
      setQuizSetId(created.quizSetId)
      navigate(`/quiz-sets/${created.quizSetId}`, {
        replace: true,
        state: { materialTitle },
      })
    },
  })

  if (!materialId) return <RouteStatus message="학습자료를 확인하지 못했어요." />
  if (activeQuery.isPending || currentUser.isPending) return <RouteLoading />
  if (activeQuery.isError && !quizSetId) {
    return <RouteStatus message="문제 생성 상태를 불러오지 못했어요." retry={() => void activeQuery.refetch()} />
  }

  const requestedConfig = quizSetId && currentUser.data
    ? loadRequestedConfig(currentUser.data.id, quizSetId)
    : undefined
  const state = quizSetQuery.data
  const questions = state?.status === 'READY' ? state.questions : []
  const generationState: QuizGenerationState | undefined = state
    ? generationStateFrom(state, requestedConfig)
    : quizSetQuery.isError
      ? { status: 'ERROR', error: { kind: 'STATUS_UNAVAILABLE', retryable: true } }
      : quizSetId
        ? { status: 'GENERATING', requestedConfig }
        : undefined

  return (
    <QuizFlowPage
      key={quizSetId ?? 'new'}
      materialTitle={materialTitle}
      questions={questions}
      result={emptyResult}
      initialScene={quizSetId ? 'GENERATION' : 'CONDITIONS'}
      generationState={generationState}
      callbacks={{
        onGenerate: async (conditions) => { await createMutation.mutateAsync(conditions) },
        onRetryGeneration: async (_failure: QuizGenerationFailure) => {
          const conditions = requestedConfig ?? lastConditionsRef.current
          if (conditions) await createMutation.mutateAsync(conditions)
        },
        onRefreshGenerationStatus: async () => { await quizSetQuery.refetch() },
        onExitGeneration: () => navigate('/learning'),
        onExitQuiz: () => navigate('/learning'),
        onDeferQuiz: () => navigate('/learning'),
        onResultExit: () => navigate('/learning'),
        onSubmit: ({ attemptId, payload }) => {
          if (!attemptId) throw new Error('attempt UUID를 만들지 못했어요.')
          return submitQuiz(quizSetId!, attemptId, payload)
        },
        onLoadResult: async (attemptId) => adaptQuizResult(await getQuizResult(attemptId)),
        onSaveEssayAssessment: ({ resourceId, questionId, assessment }) =>
          saveEssayAssessment(resourceId, questionId, assessment),
        onUpdateShortAnswerOutcome: async ({ questionId, outcome }) => {
          throw new Error(`결과 화면 attempt 경로로 진입해야 채점 수정이 가능합니다: ${questionId}:${outcome}`)
        },
      }}
    />
  )
}

export function QuizSetRoutePage() {
  const { quizSetId } = useParams<{ quizSetId: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const currentUser = useCurrentUser()
  const materialTitle = (location.state as { materialTitle?: string } | null)?.materialTitle ?? '학습자료'
  const lastAttemptIdRef = useRef<string | undefined>(undefined)
  const createKeyRef = useRef<string | undefined>(undefined)
  const createConditionsRef = useRef<QuizConditions | undefined>(undefined)
  const stateQuery = useQuery({
    queryKey: ['private', 'quiz-set', quizSetId],
    queryFn: async ({ signal }) => {
      const state = await getQuizSet(quizSetId!, signal)
      if (state.status === 'READY') validateQuestions(state.questions)
      return state
    },
    enabled: Boolean(quizSetId),
    retry: shouldRetryQuery,
    refetchInterval: (query) => {
      const data = query.state.data
      return data?.status === 'GENERATING' ? data.pollAfterSeconds * 1_000 : false
    },
  })
  const pendingQuery = useQuery({
    queryKey: ['private', 'quiz-set', quizSetId, 'pending-self-assessment'],
    queryFn: ({ signal }) => getPendingSelfAssessment(quizSetId!, signal),
    enabled: stateQuery.data?.status === 'READY',
  })
  const pendingResultQuery = useQuery({
    queryKey: ['private', 'quiz-attempt', pendingQuery.data?.attemptId, 'result'],
    queryFn: ({ signal }) => getQuizResult(pendingQuery.data!.attemptId, signal),
    enabled: Boolean(pendingQuery.data?.attemptId),
  })
  const createMutation = useMutation({
    mutationFn: async ({ materialId, conditions }: { materialId: string; conditions: QuizConditions }) => {
      createConditionsRef.current = conditions
      const key = createKeyRef.current ?? createUuidV4()
      createKeyRef.current = key
      return createQuizSet(materialId, conditions, key)
    },
    onSuccess: (created) => {
      createKeyRef.current = undefined
      if (currentUser.data) saveRequestedConfig(currentUser.data.id, created.quizSetId, created.requestedConfig)
      navigate(`/quiz-sets/${created.quizSetId}`, { replace: true, state: { materialTitle } })
    },
  })

  if (
    stateQuery.isPending ||
    currentUser.isPending ||
    (stateQuery.data?.status === 'READY' && pendingQuery.isPending) ||
    (pendingQuery.data && pendingResultQuery.isPending)
  ) return <RouteLoading />
  if (stateQuery.isError || !stateQuery.data || pendingQuery.isError || pendingResultQuery.isError) {
    return <RouteStatus message="퀴즈를 불러오지 못했어요. 다시 시도해 주세요." retry={() => void stateQuery.refetch()} />
  }

  const state = stateQuery.data
  const requestedConfig = currentUser.data
    ? loadRequestedConfig(currentUser.data.id, state.quizSetId)
    : undefined
  const questions = state.status === 'READY' ? state.questions : []
  const pending = pendingQuery.data
  const resumedResult = pendingResultQuery.data ? adaptQuizResult(pendingResultQuery.data) : emptyResult
  if (pending) lastAttemptIdRef.current = pending.attemptId

  return (
    <QuizFlowPage
      key={pending?.attemptId ?? state.quizSetId}
      materialTitle={materialTitle}
      questions={questions}
      result={resumedResult}
      initialScene={pending ? 'SELF_ASSESSMENT' : state.status === 'READY' ? 'READY' : 'GENERATION'}
      generationState={pending ? undefined : generationStateFrom(state, requestedConfig)}
      initialResourceId={pending?.attemptId}
      initialPendingEssayQuestionIds={pending?.pendingEssayQuestionIds}
      callbacks={{
        onGenerate: async (conditions) => {
          await createMutation.mutateAsync({ materialId: state.materialId, conditions })
        },
        onRefreshGenerationStatus: async () => { await stateQuery.refetch() },
        onRetryGeneration: async (failure) => {
          if (failure.kind === 'REQUEST_FAILED' && createConditionsRef.current) {
            await createMutation.mutateAsync({
              materialId: state.materialId,
              conditions: createConditionsRef.current,
            })
            return
          }
          if (failure.kind === 'GENERATION_FAILED' && requestedConfig) {
            createKeyRef.current = undefined
            await createMutation.mutateAsync({ materialId: state.materialId, conditions: requestedConfig })
            return
          }
          if (failure.kind === 'SOURCE_INSUFFICIENT' || !failure.retryable) {
            navigate(`/learning/${state.materialId}/quiz`, { replace: true, state: { materialTitle } })
            return
          }
          navigate(`/learning/${state.materialId}/quiz`, { replace: true, state: { materialTitle } })
        },
        onExitGeneration: () => navigate('/learning'),
        onExitQuiz: () => navigate('/learning'),
        onDeferQuiz: () => navigate('/learning'),
        onResultExit: () => navigate('/learning'),
        onSubmit: async ({ attemptId, payload }) => {
          if (!attemptId) throw new Error('attempt UUID를 만들지 못했어요.')
          const submission = await submitQuiz(state.quizSetId, attemptId, payload)
          lastAttemptIdRef.current = submission.attemptId
          return submission
        },
        onLoadResult: async (attemptId) => adaptQuizResult(await getQuizResult(attemptId)),
        onSaveEssayAssessment: ({ resourceId, questionId, assessment }) =>
          saveEssayAssessment(resourceId, questionId, assessment),
        onUpdateShortAnswerOutcome: async ({ questionId, outcome }) => {
          if (!lastAttemptIdRef.current) throw new Error('attempt ID가 현재 화면에 없습니다.')
          return adaptQuizResult(
            await updateShortAnswerGrading(lastAttemptIdRef.current, questionId, outcome),
          )
        },
      }}
    />
  )
}

export function QuizAttemptResultRoutePage() {
  const { attemptId } = useParams<{ attemptId: string }>()
  const navigate = useNavigate()
  const resultQuery = useQuery({
    queryKey: ['private', 'quiz-attempt', attemptId, 'result'],
    queryFn: ({ signal }) => getQuizResult(attemptId!, signal),
    enabled: Boolean(attemptId),
  })
  if (resultQuery.isPending) return <RouteLoading />
  if (resultQuery.isError || !resultQuery.data) {
    return <RouteStatus message="결과를 불러오지 못했어요. 다시 시도해 주세요." retry={() => void resultQuery.refetch()} />
  }
  const result = adaptQuizResult(resultQuery.data)
  return (
    <QuizFlowPage
      materialTitle="학습자료"
      questions={[]}
      result={result}
      initialScene="RESULT"
      initialResourceId={attemptId}
      callbacks={{
        onResultExit: () => navigate('/learning'),
        onUpdateShortAnswerOutcome: async ({ questionId, outcome }) =>
          adaptQuizResult(await updateShortAnswerGrading(attemptId!, questionId, outcome)),
      }}
    />
  )
}

export function ReviewEntryRoutePage() {
  const navigate = useNavigate()
  const startedRef = useRef(false)
  const latestQuery = useQuery({
    queryKey: ['private', 'quiz-review', 'latest'],
    queryFn: ({ signal }) => getLatestReview(signal),
  })
  const createMutation = useMutation({ mutationFn: createReviewSession })
  useEffect(() => {
    const latest = latestQuery.data
    if (!latest || startedRef.current) return
    if (latest.activeReviewSessionId) {
      startedRef.current = true
      navigate(`/review-sessions/${latest.activeReviewSessionId}`, { replace: true })
      return
    }
    if (latest.sourceAttemptId && latest.reviewQuestionCount > 0) {
      startedRef.current = true
      void createMutation.mutateAsync(latest.sourceAttemptId).then((session) =>
        navigate(`/review-sessions/${session.reviewSessionId}`, { replace: true }),
      )
    }
  }, [createMutation, latestQuery.data, navigate])
  if (latestQuery.isError || createMutation.isError) {
    return <RouteStatus message="복습 정보를 불러오지 못했어요. 다시 시도해 주세요." retry={() => { startedRef.current = false; void latestQuery.refetch() }} />
  }
  if (latestQuery.data?.reviewQuestionCount === 0) {
    return <RouteStatus message="지금 복습할 문제가 없어요." />
  }
  return <RouteLoading />
}

export function ReviewSessionRoutePage() {
  const { reviewSessionId } = useParams<{ reviewSessionId: string }>()
  const navigate = useNavigate()
  const sessionQuery = useQuery({
    queryKey: ['private', 'review-session', reviewSessionId],
    queryFn: ({ signal }) => getReviewSession(reviewSessionId!, signal),
    enabled: Boolean(reviewSessionId),
  })
  const resultQuery = useQuery({
    queryKey: ['private', 'review-session', reviewSessionId, 'result'],
    queryFn: ({ signal }) => getReviewResult(reviewSessionId!, signal),
    enabled: sessionQuery.data?.status !== 'SOLVING',
  })
  if (sessionQuery.isPending || (sessionQuery.data?.status !== 'SOLVING' && resultQuery.isPending)) return <RouteLoading />
  if (sessionQuery.isError || !sessionQuery.data || resultQuery.isError) {
    return <RouteStatus message="복습을 불러오지 못했어요. 다시 시도해 주세요." retry={() => void sessionQuery.refetch()} />
  }
  const session = sessionQuery.data
  const result = resultQuery.data ? adaptReviewResult(resultQuery.data) : emptyResult
  if (session.status === 'SELF_ASSESSMENT_REQUIRED') {
    return (
      <RouteStatus
        message="남은 자기평가 문항을 확인하지 못했어요. 다시 시도해 주세요."
        retry={() => void resultQuery.refetch()}
      />
    )
  }
  const scene = session.status === 'SOLVING'
    ? 'SOLVING'
    : 'RESULT'
  return (
    <QuizFlowPage
      key={`${reviewSessionId}-${scene}`}
      materialTitle="최근 퀴즈"
      questions={validateQuestions(session.questions ?? [])}
      result={result}
      flowKind="REVIEW"
      initialScene={scene}
      initialResourceId={reviewSessionId}
      callbacks={{
        onExitQuiz: () => navigate('/learning'),
        onResultExit: () => navigate('/learning'),
        onSubmit: ({ payload }) => submitReview(reviewSessionId!, payload),
        onLoadResult: async () => adaptReviewResult(await getReviewResult(reviewSessionId!)),
        onSaveEssayAssessment: ({ questionId, assessment }) =>
          saveReviewEssayAssessment(reviewSessionId!, questionId, assessment),
      }}
    />
  )
}
