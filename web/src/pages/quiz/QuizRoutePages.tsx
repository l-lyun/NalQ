import { ActionButton, ProgressCircle, Text, VStack } from '@seed-design/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

import { shouldRetryQuery } from '@/app/providers/queryClient'
import {
  createQuizSet,
  createReviewSession,
  getActiveQuizSet,
  getPendingSelfAssessment,
  getQuizResult,
  getQuizSet,
  getReviewResult,
  getReviewSession,
  saveEssayAssessment,
  saveReviewEssayAssessment,
  submitQuiz,
  submitReview,
  updateGradingOverride,
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
import { useCurrentUser } from '@/features/auth/model/auth.queries'
import { learningMaterialKeys } from '@/features/learning-material/api/learningMaterial.api'
import {
  latestReviewQueryOptions,
  quizQueryKeys,
} from '@/features/quiz/model/quizQueries'

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
  const queryClient = useQueryClient()
  const materialTitle = (location.state as { materialTitle?: string } | null)?.materialTitle ?? '학습자료'
  const [quizSetId, setQuizSetId] = useState<string>()
  const lastConditionsRef = useRef<QuizConditions | undefined>(undefined)

  const activeQuery = useQuery({
    queryKey: quizQueryKeys.active(materialId!),
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
    queryKey: quizQueryKeys.quizSet(quizSetId!),
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
      return createQuizSet(materialId!, conditions)
    },
    onSuccess: (created) => {
      if (currentUser.data) {
        saveRequestedConfig(currentUser.data.id, created.quizSetId, created.requestedConfig)
      }
      setQuizSetId(created.quizSetId)
      void queryClient.invalidateQueries({ queryKey: learningMaterialKeys.all })
      void queryClient.invalidateQueries({ queryKey: quizQueryKeys.active(created.materialId) })
      navigate(`/quiz-sets/${created.quizSetId}`, {
        replace: true,
        state: { materialTitle },
      })
    },
  })

  useEffect(() => {
    if (!materialId || !quizSetQuery.data || quizSetQuery.data.status === 'GENERATING') return
    void queryClient.invalidateQueries({ queryKey: learningMaterialKeys.all })
    void queryClient.invalidateQueries({ queryKey: quizQueryKeys.active(materialId) })
  }, [materialId, queryClient, quizSetQuery.data])

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
          if (!conditions) return
          const activeResult = await activeQuery.refetch()
          if (activeResult.isError) throw activeResult.error
          const active = activeResult.data
          if (active) {
            if (currentUser.data) {
              saveRequestedConfig(currentUser.data.id, active.quizSetId, conditions)
            }
            navigate(`/quiz-sets/${active.quizSetId}`, {
              replace: true,
              state: { materialTitle },
            })
            return
          }
          await createMutation.mutateAsync(conditions)
        },
        onRefreshGenerationStatus: async () => { await quizSetQuery.refetch() },
        onExitGeneration: () => navigate('/learning'),
        onExitQuiz: () => navigate('/learning'),
        onDeferQuiz: () => navigate('/learning'),
        onResultExit: () => navigate('/learning'),
        onSubmit: async ({ attemptId, payload }) => {
          if (!attemptId) throw new Error('attempt UUID를 만들지 못했어요.')
          const submission = await submitQuiz(quizSetId!, attemptId, payload)
          void queryClient.invalidateQueries({
            queryKey: quizQueryKeys.pendingSelfAssessment(quizSetId!),
            refetchType: 'none',
          })
          void queryClient.invalidateQueries({
            queryKey: quizQueryKeys.attemptResult(submission.attemptId),
            refetchType: 'none',
          })
          void queryClient.invalidateQueries({ queryKey: quizQueryKeys.reviews })
          return submission
        },
        onLoadResult: async (attemptId) => adaptQuizResult(await getQuizResult(attemptId)),
        onSaveEssayAssessment: async ({ resourceId, questionId, assessment }) => {
          const saved = await saveEssayAssessment(resourceId, questionId, assessment)
          void queryClient.invalidateQueries({
            queryKey: quizQueryKeys.attemptResult(resourceId),
            refetchType: 'none',
          })
          void queryClient.invalidateQueries({ queryKey: quizQueryKeys.reviews })
          return saved
        },
        onUpdateGradingOutcome: async ({ questionId, outcome }) => {
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
  const queryClient = useQueryClient()
  const materialTitle = (location.state as { materialTitle?: string } | null)?.materialTitle ?? '학습자료'
  const lastAttemptIdRef = useRef<string | undefined>(undefined)
  const createConditionsRef = useRef<QuizConditions | undefined>(undefined)
  const stateQuery = useQuery({
    queryKey: quizQueryKeys.quizSet(quizSetId!),
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
    queryKey: quizQueryKeys.pendingSelfAssessment(quizSetId!),
    queryFn: ({ signal }) => getPendingSelfAssessment(quizSetId!, signal),
    enabled: stateQuery.data?.status === 'READY',
  })
  const pendingResultQuery = useQuery({
    queryKey: quizQueryKeys.attemptResult(pendingQuery.data?.attemptId),
    queryFn: ({ signal }) => getQuizResult(pendingQuery.data!.attemptId, signal),
    enabled: Boolean(pendingQuery.data?.attemptId),
  })
  const createMutation = useMutation({
    mutationFn: async ({ materialId, conditions }: { materialId: string; conditions: QuizConditions }) => {
      createConditionsRef.current = conditions
      return createQuizSet(materialId, conditions)
    },
    onSuccess: (created) => {
      if (currentUser.data) saveRequestedConfig(currentUser.data.id, created.quizSetId, created.requestedConfig)
      void queryClient.invalidateQueries({ queryKey: learningMaterialKeys.all })
      void queryClient.invalidateQueries({ queryKey: quizQueryKeys.active(created.materialId) })
      navigate(`/quiz-sets/${created.quizSetId}`, { replace: true, state: { materialTitle } })
    },
  })

  useEffect(() => {
    if (!stateQuery.data || stateQuery.data.status === 'GENERATING') return
    void queryClient.invalidateQueries({ queryKey: learningMaterialKeys.all })
    void queryClient.invalidateQueries({
      queryKey: quizQueryKeys.active(stateQuery.data.materialId),
    })
  }, [queryClient, stateQuery.data])

  if (
    stateQuery.isPending ||
    currentUser.isPending ||
    (stateQuery.data?.status === 'READY' && pendingQuery.isPending) ||
    (pendingQuery.data && pendingResultQuery.isPending)
  ) return <RouteLoading />
  if (stateQuery.isError || !stateQuery.data || pendingQuery.isError || pendingResultQuery.isError) {
    return (
      <RouteStatus
        message="퀴즈를 불러오지 못했어요. 다시 시도해 주세요."
        retry={() => {
          if (stateQuery.isError) void stateQuery.refetch()
          if (pendingQuery.isError) void pendingQuery.refetch()
          if (pendingResultQuery.isError) void pendingResultQuery.refetch()
        }}
      />
    )
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
            const active = await getActiveQuizSet(state.materialId)
            if (active) {
              if (currentUser.data) {
                saveRequestedConfig(
                  currentUser.data.id,
                  active.quizSetId,
                  createConditionsRef.current,
                )
              }
              navigate(`/quiz-sets/${active.quizSetId}`, {
                replace: true,
                state: { materialTitle },
              })
              return
            }
            await createMutation.mutateAsync({
              materialId: state.materialId,
              conditions: createConditionsRef.current,
            })
            return
          }
          if (failure.kind === 'GENERATION_FAILED' && requestedConfig) {
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
          void queryClient.invalidateQueries({
            queryKey: quizQueryKeys.pendingSelfAssessment(state.quizSetId),
            refetchType: 'none',
          })
          void queryClient.invalidateQueries({
            queryKey: quizQueryKeys.attemptResult(submission.attemptId),
            refetchType: 'none',
          })
          void queryClient.invalidateQueries({ queryKey: quizQueryKeys.reviews })
          return submission
        },
        onLoadResult: async (attemptId) => adaptQuizResult(await getQuizResult(attemptId)),
        onSaveEssayAssessment: async ({ resourceId, questionId, assessment }) => {
          const saved = await saveEssayAssessment(resourceId, questionId, assessment)
          void queryClient.invalidateQueries({
            queryKey: quizQueryKeys.attemptResult(resourceId),
            refetchType: 'none',
          })
          void queryClient.invalidateQueries({
            queryKey: quizQueryKeys.pendingSelfAssessment(state.quizSetId),
            refetchType: 'none',
          })
          void queryClient.invalidateQueries({ queryKey: quizQueryKeys.reviews })
          return saved
        },
        onCompleted: (attemptId) => {
          void queryClient.invalidateQueries({ queryKey: quizQueryKeys.reviews })
          navigate(`/quiz-attempts/${attemptId}/result`, { replace: true })
        },
        onUpdateGradingOutcome: async ({ questionId, outcome }) => {
          if (!lastAttemptIdRef.current) throw new Error('attempt ID가 현재 화면에 없습니다.')
          const updated = await updateGradingOverride(
            lastAttemptIdRef.current,
            questionId,
            outcome,
          )
          void queryClient.invalidateQueries({
            queryKey: quizQueryKeys.attemptResult(lastAttemptIdRef.current),
            refetchType: 'none',
          })
          void queryClient.invalidateQueries({ queryKey: quizQueryKeys.reviews })
          return adaptQuizResult(updated)
        },
      }}
    />
  )
}

export function QuizAttemptResultRoutePage() {
  const { attemptId } = useParams<{ attemptId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const resultQuery = useQuery({
    queryKey: quizQueryKeys.attemptResult(attemptId!),
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
        onUpdateGradingOutcome: async ({ questionId, outcome }) => {
          const updated = await updateGradingOverride(attemptId!, questionId, outcome)
          void queryClient.invalidateQueries({
            queryKey: quizQueryKeys.attemptResult(attemptId!),
            refetchType: 'none',
          })
          void queryClient.invalidateQueries({ queryKey: quizQueryKeys.reviews })
          return adaptQuizResult(updated)
        },
      }}
    />
  )
}

export function ReviewEntryRoutePage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const startedRef = useRef(false)
  const latestQuery = useQuery({
    ...latestReviewQueryOptions(),
  })
  const createMutation = useMutation({
    mutationFn: createReviewSession,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: quizQueryKeys.reviews })
    },
  })
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
  if (
    latestQuery.data?.reviewQuestionCount === 0 &&
    !latestQuery.data.activeReviewSessionId
  ) {
    return <RouteStatus message="지금 복습할 문제가 없어요." />
  }
  return <RouteLoading />
}

export function ReviewSessionRoutePage() {
  const { reviewSessionId } = useParams<{ reviewSessionId: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const sessionQuery = useQuery({
    queryKey: quizQueryKeys.reviewSession(reviewSessionId!),
    queryFn: ({ signal }) => getReviewSession(reviewSessionId!, signal),
    enabled: Boolean(reviewSessionId),
  })
  const shouldLoadResult =
    sessionQuery.data?.status === 'SELF_ASSESSMENT_REQUIRED' ||
    sessionQuery.data?.status === 'COMPLETED'
  const resultQuery = useQuery({
    queryKey: quizQueryKeys.reviewResult(reviewSessionId!),
    queryFn: ({ signal }) => getReviewResult(reviewSessionId!, signal),
    enabled: Boolean(reviewSessionId && shouldLoadResult),
  })
  if (sessionQuery.isPending || (shouldLoadResult && resultQuery.isPending)) {
    return <RouteLoading />
  }
  if (sessionQuery.isError || !sessionQuery.data || (shouldLoadResult && resultQuery.isError)) {
    return (
      <RouteStatus
        message="복습을 불러오지 못했어요. 다시 시도해 주세요."
        retry={() => {
          if (sessionQuery.isError) void sessionQuery.refetch()
          if (resultQuery.isError) void resultQuery.refetch()
        }}
      />
    )
  }
  const session = sessionQuery.data
  const result = resultQuery.data ? adaptReviewResult(resultQuery.data) : emptyResult
  const scene = session.status === 'SOLVING'
    ? 'SOLVING'
    : session.status === 'SELF_ASSESSMENT_REQUIRED'
      ? 'SELF_ASSESSMENT'
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
      initialPendingEssayQuestionIds={session.pendingEssayQuestionIds}
      callbacks={{
        onExitQuiz: () => navigate('/learning'),
        onResultExit: () => navigate('/learning'),
        onSubmit: async ({ payload }) => {
          const submission = await submitReview(reviewSessionId!, payload)
          void queryClient.invalidateQueries({
            queryKey: quizQueryKeys.reviewSession(reviewSessionId!),
            refetchType: 'none',
          })
          void queryClient.invalidateQueries({
            queryKey: quizQueryKeys.reviewResult(reviewSessionId!),
            refetchType: 'none',
          })
          void queryClient.invalidateQueries({ queryKey: quizQueryKeys.reviews })
          return submission
        },
        onLoadResult: async () => adaptReviewResult(await getReviewResult(reviewSessionId!)),
        onSaveEssayAssessment: async ({ questionId, assessment }) => {
          const saved = await saveReviewEssayAssessment(
            reviewSessionId!,
            questionId,
            assessment,
          )
          void queryClient.invalidateQueries({
            queryKey: quizQueryKeys.reviewSession(reviewSessionId!),
            refetchType: 'none',
          })
          void queryClient.invalidateQueries({
            queryKey: quizQueryKeys.reviewResult(reviewSessionId!),
            refetchType: 'none',
          })
          void queryClient.invalidateQueries({ queryKey: quizQueryKeys.reviews })
          return saved
        },
      }}
    />
  )
}
