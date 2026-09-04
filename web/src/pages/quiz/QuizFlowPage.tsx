import {
  IconArrowLeftLine,
  IconCheckmarkLine,
  IconExclamationmarkCircleFill,
} from '@karrotmarket/react-monochrome-icon'
import {
  ActionButton,
  BottomSheet,
  Box,
  Checkbox,
  ContentDialog,
  Field,
  Flex,
  Grid,
  HStack,
  Icon,
  PageBanner,
  Portal,
  ProgressCircle,
  Text,
  TextField,
  VStack,
} from '@seed-design/react'
import { useEffect, useRef, useState, type ReactNode } from 'react'

import { createSubmissionPayload, isQuestionAnswered } from '@/features/quiz/model/quizAdapter'
import {
  countGenerationPromptCodePoints,
  GENERATION_PROMPT_MAX_CODE_POINTS,
  isQuizGenerationActiveConflict,
  sliceGenerationPrompt,
} from '@/features/quiz/model/quizGenerationRequest'
import { createUuidV4 } from '@/features/quiz/model/randomUuid'

import {
  getQuizOutcomeLabel,
  getQuizResultTitle,
  shouldShowQuizReviewAction,
} from './quizFeedback'
import type {
  QuizAnswer,
  QuizAnswers,
  QuizBinaryOutcome,
  QuizConditions,
  QuizDifficulty,
  QuizFlowPageProps,
  QuizGenerationFailure,
  QuizGenerationReady,
  QuizMaxQuestionCount,
  QuizQuestion,
  QuizQuestionType,
  QuizResult,
  QuizResultItem,
  QuizResultOutcome,
} from './quiz.types'
import './quiz.css'

type Sheet =
  | 'GENERATION_EXIT'
  | 'READY_START'
  | 'QUIZ_EXIT'
  | 'ANSWER_MAP'
  | 'SUBMIT'
  | 'RESULT_LIST'
  | 'CORRECTION'
  | null

const typeLabels: Record<QuizQuestionType, string> = {
  MULTIPLE_CHOICE: '객관식',
  FILL_IN_THE_BLANK: '빈칸 채우기',
  SHORT_ANSWER: '단답형',
  ESSAY: '서술형',
}

const difficultyLabels: Record<QuizDifficulty, string> = {
  EASY: '쉬움',
  NORMAL: '보통',
  HARD: '어려움',
}

function firstResultQuestionId(result: QuizResult) {
  return result.items.find((item) => item.type === 'SHORT_ANSWER')?.questionId ?? result.items[0]?.questionId
}

function CheckmarkIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24">
      <path
        d="m5 12.5 4.25 4.25L19 7"
        fill="none"
        stroke="var(--seed-color-palette-static-white)"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2.4"
      />
    </svg>
  )
}

function ScreenHeader({
  title,
  backLabel,
  onBack,
  action,
}: {
  title: string
  backLabel: string
  onBack: () => void
  action?: ReactNode
}) {
  return (
    <Flex as="header" className="quiz-header" align="center" gap="x2">
      <ActionButton
        type="button"
        size="small"
        variant="ghost"
        layout="iconOnly"
        aria-label={backLabel}
        onClick={onBack}
      >
        <Icon svg={<IconArrowLeftLine />} size="x5" />
      </ActionButton>
      <Text as="h1" className="quiz-header-title" textStyle="t8Bold" color="fg.neutral">
        {title}
      </Text>
      <Box className="quiz-header-action">{action}</Box>
    </Flex>
  )
}

function SheetFrame({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
  dismissible = true,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: ReactNode
  children?: ReactNode
  footer: ReactNode
  dismissible?: boolean
}) {
  return (
    <BottomSheet.Root open={open} onOpenChange={onOpenChange} dismissible={dismissible}>
      <Portal>
        <BottomSheet.Backdrop />
        <BottomSheet.Positioner>
          <BottomSheet.Content>
            <BottomSheet.Header>
              <BottomSheet.Title>{title}</BottomSheet.Title>
              {description ? (
                <BottomSheet.Description>{description}</BottomSheet.Description>
              ) : null}
            </BottomSheet.Header>
            {children ? <BottomSheet.Body>{children}</BottomSheet.Body> : null}
            <BottomSheet.Footer>{footer}</BottomSheet.Footer>
          </BottomSheet.Content>
        </BottomSheet.Positioner>
      </Portal>
    </BottomSheet.Root>
  )
}

function generationErrorCopy(error: QuizGenerationFailure) {
  switch (error.kind) {
    case 'REQUEST_FAILED':
      return {
        title: '문제 만들기를 시작하지 못했어요',
        description: error.message ?? '새 문제 세트가 만들어지지 않았어요. 잠시 후 다시 시도해 주세요.',
        action: '다시 시도',
      }
    case 'STATUS_UNAVAILABLE':
      return {
        title: '진행 상태를 확인하지 못했어요',
        description:
          error.message ?? '연결 문제로 상태를 확인하지 못했어요. 문제 만들기는 계속되고 있을 수 있어요.',
        action: '상태 다시 확인',
      }
    case 'SOURCE_INSUFFICIENT':
      return {
        title: '자료와 조건을 확인해 주세요',
        description:
          error.message ?? '선택한 유형을 모두 만들 수 없어 불완전한 문제 세트는 저장하지 않았어요.',
        action: '조건 다시 보기',
      }
    case 'GENERATION_FAILED':
      return {
        title: '문제 만들기를 완료하지 못했어요',
        description: error.message ?? '불완전한 문제 세트는 저장하지 않았어요.',
        action: error.retryable ? '같은 조건으로 다시 시도' : '조건 다시 보기',
      }
  }
}

export function QuizFlowPage({
  materialTitle,
  questions,
  result,
  flowKind = 'QUIZ',
  initialScene = 'CONDITIONS',
  initialConditions = {
    selectedTypes: ['MULTIPLE_CHOICE'],
    difficulty: 'NORMAL',
    maxQuestionCount: 10,
  },
  initialAnswers = {},
  generationState,
  initialResourceId,
  initialPendingEssayQuestionIds = [],
  callbacks,
}: QuizFlowPageProps) {
  const [scene, setScene] = useState(initialScene)
  const [conditions, setConditions] = useState(initialConditions)
  const [conditionsError, setConditionsError] = useState<string>()
  const [generationDisclosureOpen, setGenerationDisclosureOpen] = useState(false)
  const [generationStarting, setGenerationStarting] = useState(false)
  const [generation, setGeneration] = useState(
    generationState ?? ({ status: 'GENERATING' } as const),
  )
  const [sheet, setSheet] = useState<Sheet>(initialScene === 'READY' ? 'READY_START' : null)
  const [answers, setAnswers] = useState<QuizAnswers>(initialAnswers)
  const [questionIndex, setQuestionIndex] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string>()
  const [resourceId, setResourceId] = useState(initialResourceId)
  const [pendingEssayQuestionIds, setPendingEssayQuestionIds] = useState(initialPendingEssayQuestionIds)
  const [essayAssessmentCount, setEssayAssessmentCount] = useState(initialPendingEssayQuestionIds.length)
  const [essayOutcome, setEssayOutcome] = useState<QuizResultOutcome>()
  const [savingEssay, setSavingEssay] = useState(false)
  const [essayError, setEssayError] = useState<string>()
  const [resultState, setResultState] = useState<QuizResult>(result)
  const [resultQuestionId, setResultQuestionId] = useState(firstResultQuestionId(result))
  const [correctionOutcome, setCorrectionOutcome] = useState<QuizBinaryOutcome>('CORRECT')
  const [savingCorrection, setSavingCorrection] = useState(false)
  const [correctionError, setCorrectionError] = useState<string>()
  const [reviewStarting, setReviewStarting] = useState(false)
  const [reviewStartError, setReviewStartError] = useState<string>()
  const questionHeadingRef = useRef<HTMLHeadingElement>(null)
  const submitLockRef = useRef(false)
  const submissionRef = useRef<{
    attemptId?: string
    payload: ReturnType<typeof createSubmissionPayload>
  } | undefined>(undefined)
  const essaySaveLockRef = useRef(false)
  const correctionSaveLockRef = useRef(false)
  const reviewStartLockRef = useRef(false)

  const currentQuestion = questions[questionIndex]
  const answeredCount = questions.filter((question) =>
    isQuestionAnswered(question, answers[question.questionId]),
  ).length
  const unansweredQuestions = questions.filter(
    (question) => !isQuestionAnswered(question, answers[question.questionId]),
  )
  const resultItem = resultState.items.find((item) => item.questionId === resultQuestionId)
  const essayItem = resultState.items.find(
    (item) => item.questionId === pendingEssayQuestionIds[0] && item.type === 'ESSAY',
  )

  useEffect(() => {
    setResultState(result)
    setResultQuestionId((current) =>
      current && result.items.some((item) => item.questionId === current)
        ? current
        : firstResultQuestionId(result),
    )
  }, [result])

  useEffect(() => {
    if (!generationState) return
    if (
      scene === 'CONDITIONS' ||
      scene === 'SOLVING' ||
      scene === 'SUBMIT_ERROR' ||
      scene === 'SELF_ASSESSMENT' ||
      scene === 'RESULT'
    ) return
    setGeneration(generationState)
    if (generationState.status === 'READY') {
      setScene('READY')
      if (scene !== 'READY') setSheet('READY_START')
    } else {
      setScene('GENERATION')
    }
  }, [generationState, scene])

  useEffect(() => {
    if (scene === 'SOLVING') questionHeadingRef.current?.focus()
  }, [questionIndex, scene])

  useEffect(() => {
    if (scene !== 'SOLVING' || answeredCount === 0) return
    const warnBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', warnBeforeUnload)
    return () => window.removeEventListener('beforeunload', warnBeforeUnload)
  }, [answeredCount, scene])

  const updateConditions = (next: QuizConditions) => {
    setConditions(next)
    setConditionsError(undefined)
    callbacks?.onConditionsChange?.(next)
  }

  const startGeneration = async () => {
    if (conditions.selectedTypes.length === 0) {
      setConditionsError('문제 유형을 하나 이상 선택해 주세요.')
      return
    }
    setGenerationDisclosureOpen(true)
  }

  const confirmGeneration = async () => {
    if (generationStarting) return
    setGenerationStarting(true)
    setGeneration({ status: 'GENERATING' })
    setScene('GENERATION')
    try {
      const ready = await callbacks?.onGenerate?.(conditions)
      if (ready) showReady(ready)
    } catch (error) {
      if (isQuizGenerationActiveConflict(error)) {
        setScene('CONDITIONS')
        callbacks?.onGenerationActive?.()
        return
      }
      setGeneration({
        status: 'ERROR',
        error: { kind: 'REQUEST_FAILED', retryable: true },
      })
    } finally {
      setGenerationDisclosureOpen(false)
      setGenerationStarting(false)
    }
  }

  const showReady = (ready: QuizGenerationReady) => {
    setGeneration({ status: 'READY', ready })
    setScene('READY')
    setSheet('READY_START')
  }

  const retryGeneration = async (failure: QuizGenerationFailure) => {
    if (failure.kind === 'SOURCE_INSUFFICIENT' || !failure.retryable) {
      setScene('CONDITIONS')
      return
    }
    setGeneration({ status: 'GENERATING' })
    try {
      const ready =
        failure.kind === 'STATUS_UNAVAILABLE'
          ? await callbacks?.onRefreshGenerationStatus?.()
          : await callbacks?.onRetryGeneration?.(failure)
      if (ready) showReady(ready)
    } catch (error) {
      if (isQuizGenerationActiveConflict(error)) {
        setScene('CONDITIONS')
        callbacks?.onGenerationActive?.()
        return
      }
      setGeneration({
        status: 'ERROR',
        error:
          failure.kind === 'STATUS_UNAVAILABLE'
            ? failure
            : { kind: 'REQUEST_FAILED', retryable: true },
      })
    }
  }

  const exitGenerationScreen = () => {
    if (
      generation.status === 'GENERATING' ||
      (generation.status === 'ERROR' && generation.error.kind === 'STATUS_UNAVAILABLE')
    ) {
      setSheet('GENERATION_EXIT')
      return
    }
    setScene('CONDITIONS')
  }

  const setAnswer = (questionId: string, answer: QuizAnswer) => {
    submissionRef.current = undefined
    setAnswers((current) => {
      const next = { ...current, [questionId]: answer }
      callbacks?.onAnswersChange?.(next)
      return next
    })
  }

  const goToQuestion = (index: number) => {
    const normalized = Math.max(0, Math.min(index, questions.length - 1))
    setQuestionIndex(normalized)
    callbacks?.onNavigateQuestion?.(questions[normalized].questionId)
  }

  const requestSubmit = () => {
    if (submitLockRef.current) return
    setSubmitError(undefined)
    if (unansweredQuestions.length > 0) {
      setSheet('SUBMIT')
      return
    }
    void submitAnswers()
  }

  const submitAnswers = async () => {
    if (submitLockRef.current) return
    const submit = callbacks?.onSubmit
    if (!submit) {
      setSubmitError('제출 기능을 사용할 수 없어요. 잠시 후 다시 시도해 주세요.')
      setSheet(null)
      setScene('SUBMIT_ERROR')
      return
    }
    submitLockRef.current = true
    setSubmitting(true)
    setSubmitError(undefined)
    try {
      const request = submissionRef.current ?? {
        attemptId: flowKind === 'QUIZ' ? createUuidV4() : undefined,
        payload: createSubmissionPayload(questions, answers),
      }
      submissionRef.current = request
      const submission = await submit(request)
      setSheet(null)
      const nextResourceId = 'attemptId' in submission
        ? submission.attemptId
        : submission.reviewSessionId
      setResourceId(nextResourceId)
      const latestResult = callbacks?.onLoadResult
        ? await callbacks.onLoadResult(nextResourceId)
        : resultState
      setResultState(latestResult)
      setResultQuestionId(firstResultQuestionId(latestResult))
      if (submission.status === 'SELF_ASSESSMENT_REQUIRED') {
        const pendingItems = submission.pendingEssayQuestionIds.map((questionId) =>
          latestResult.items.find((item) => item.questionId === questionId && item.type === 'ESSAY'),
        )
        if (
          submission.pendingEssayQuestionIds.length === 0 ||
          pendingItems.some((item) => !item)
        ) {
          throw new Error('Missing essay assessment details')
        }
        setPendingEssayQuestionIds(submission.pendingEssayQuestionIds)
        setEssayAssessmentCount(submission.pendingEssayQuestionIds.length)
        setEssayOutcome(undefined)
        setEssayError(undefined)
        setScene('SELF_ASSESSMENT')
      } else {
        setPendingEssayQuestionIds([])
        setScene('RESULT')
        callbacks?.onCompleted?.(nextResourceId)
      }
    } catch {
      setSubmitError('답안을 제출하지 못했어요. 입력한 답은 유지되며 다시 시도할 수 있어요.')
      setSheet(null)
      setScene('SUBMIT_ERROR')
    } finally {
      submitLockRef.current = false
      setSubmitting(false)
    }
  }

  const saveEssayAssessment = async () => {
    const save = callbacks?.onSaveEssayAssessment
    if (
      essaySaveLockRef.current ||
      !save ||
      !resourceId ||
      !essayItem ||
      !essayOutcome
    ) {
      return
    }
    essaySaveLockRef.current = true
    setSavingEssay(true)
    setEssayError(undefined)
    try {
      const saved = await save({
        resourceId,
        questionId: essayItem.questionId,
        assessment: essayOutcome,
      })
      const remainingIds = pendingEssayQuestionIds.filter(
        (questionId) => questionId !== saved.questionId,
      )
      if (
        saved.questionId !== essayItem.questionId ||
        saved.assessment !== essayOutcome ||
        saved.remainingSelfAssessmentCount !== remainingIds.length ||
        (saved.status === 'COMPLETED' && remainingIds.length > 0) ||
        (saved.status === 'SELF_ASSESSMENT_REQUIRED' && remainingIds.length === 0)
      ) {
        throw new Error('Essay assessment response mismatch')
      }
      if (callbacks?.onLoadResult) {
        const latestResult = await callbacks.onLoadResult(resourceId)
        setResultState(latestResult)
        setResultQuestionId((current) =>
          current && latestResult.items.some((item) => item.questionId === current)
            ? current
            : firstResultQuestionId(latestResult),
        )
      }
      setPendingEssayQuestionIds(remainingIds)
      setEssayOutcome(undefined)
      if (saved.status === 'COMPLETED') {
        setScene('RESULT')
        callbacks?.onCompleted?.(resourceId)
      }
    } catch {
      setEssayError('자기평가를 저장하지 못했어요. 선택한 판정은 저장되지 않았으며 다시 시도할 수 있어요.')
    } finally {
      essaySaveLockRef.current = false
      setSavingEssay(false)
    }
  }

  const openCorrection = (item: QuizResultItem) => {
    if (
      !item.editable ||
      (item.type !== 'SHORT_ANSWER' && item.type !== 'FILL_IN_THE_BLANK') ||
      item.outcome === 'PARTIAL'
    ) return
    setCorrectionOutcome(item.outcome === 'CORRECT' ? 'INCORRECT' : 'CORRECT')
    setCorrectionError(undefined)
    setSheet('CORRECTION')
  }

  const saveCorrection = async () => {
    const updateGradingOutcome = callbacks?.onUpdateGradingOutcome
    if (
      correctionSaveLockRef.current ||
      !resultItem ||
      !resultItem.editable ||
      !updateGradingOutcome
    ) return
    correctionSaveLockRef.current = true
    setSavingCorrection(true)
    setCorrectionError(undefined)
    try {
      const latestResult = await updateGradingOutcome({
        questionId: resultItem.questionId,
        outcome: correctionOutcome,
      })
      setResultState(latestResult)
      setSheet(null)
    } catch {
      setCorrectionError('채점 결과를 저장하지 못했어요. 이전 판정과 점수는 그대로 유지돼요.')
    } finally {
      correctionSaveLockRef.current = false
      setSavingCorrection(false)
    }
  }

  const startReview = async () => {
    const start = callbacks?.onStartReview
    if (reviewStartLockRef.current || !start || !resourceId) return
    reviewStartLockRef.current = true
    setReviewStarting(true)
    setReviewStartError(undefined)
    try {
      await start(resourceId)
    } catch {
      setReviewStartError('복습을 시작하지 못했어요. 현재 결과를 유지했으니 다시 시도해 주세요.')
    } finally {
      reviewStartLockRef.current = false
      setReviewStarting(false)
    }
  }

  return (
    <VStack className="quiz-shell" minHeight="100dvh" bg="bg.layerBasement">
      <Box as="main" className="quiz-main" bg="bg.layerDefault" width="full" pt="safeArea">
        {scene === 'CONDITIONS' ? (
          <ConditionsScreen
            materialTitle={materialTitle}
            conditions={conditions}
            error={conditionsError}
            onChange={updateConditions}
            onGenerate={() => void startGeneration()}
            onBack={() => callbacks?.onExitGeneration?.()}
          />
        ) : null}
        {scene === 'GENERATION' ? (
          <GenerationScreen
            state={generation}
            onExit={exitGenerationScreen}
            onRetry={(error) => void retryGeneration(error)}
          />
        ) : null}
        {scene === 'READY' && generation.status === 'READY' ? (
          <ReadyScreen
            materialTitle={materialTitle}
            ready={generation.ready}
            onBack={() => callbacks?.onExitGeneration?.()}
            onStart={() => setSheet('READY_START')}
          />
        ) : null}
        {scene === 'SOLVING' && currentQuestion ? (
          <SolvingScreen
            materialTitle={flowKind === 'REVIEW' ? `${materialTitle} · 복습` : materialTitle}
            question={currentQuestion}
            questionIndex={questionIndex}
            questionCount={questions.length}
            answeredCount={answeredCount}
            answer={answers[currentQuestion.questionId]}
            headingRef={questionHeadingRef}
            onAnswer={(answer) => setAnswer(currentQuestion.questionId, answer)}
            onExit={() => setSheet('QUIZ_EXIT')}
            onOpenMap={() => setSheet('ANSWER_MAP')}
            onPrevious={() => goToQuestion(questionIndex - 1)}
            submitting={submitting}
            onNext={() => {
              if (questionIndex === questions.length - 1) requestSubmit()
              else goToQuestion(questionIndex + 1)
            }}
          />
        ) : null}
        {scene === 'SUBMIT_ERROR' ? (
          <SubmissionErrorScreen
            submitting={submitting}
            error={submitError}
            onBack={() => setScene('SOLVING')}
            onRetry={() => void submitAnswers()}
          />
        ) : null}
        {scene === 'SELF_ASSESSMENT' && essayItem ? (
          <EssayAssessmentScreen
            item={essayItem}
            currentNumber={essayAssessmentCount - pendingEssayQuestionIds.length + 1}
            totalCount={essayAssessmentCount}
            outcome={essayOutcome}
            saving={savingEssay}
            error={essayError}
            onBack={() => callbacks?.onExitQuiz?.()}
            onOutcomeChange={setEssayOutcome}
            onSave={() => void saveEssayAssessment()}
          />
        ) : null}
        {scene === 'RESULT' && resultItem ? (
          <ResultScreen
            result={resultState}
            item={resultItem}
            title={getQuizResultTitle(resultState.kind)}
            correctionAvailable={Boolean(callbacks?.onUpdateGradingOutcome)}
            reviewStarting={reviewStarting}
            reviewStartError={reviewStartError}
            onBack={() => callbacks?.onResultExit?.()}
            onOpenList={() => setSheet('RESULT_LIST')}
            onCorrect={() => openCorrection(resultItem)}
            onStartReview={callbacks?.onStartReview ? () => void startReview() : undefined}
            onGoHome={callbacks?.onGoHome}
          />
        ) : null}
      </Box>

      <GenerationDisclosureDialog
        open={generationDisclosureOpen}
        loading={generationStarting}
        onOpenChange={(open) => {
          if (!generationStarting) setGenerationDisclosureOpen(open)
        }}
        onConfirm={() => void confirmGeneration()}
      />

      <SheetFrame
        open={sheet === 'GENERATION_EXIT'}
        onOpenChange={(open) => setSheet(open ? 'GENERATION_EXIT' : null)}
        title="지금 나갈까요?"
        description="지금 나가도 문제 만들기는 계속돼요. 같은 학습자료를 다시 열면 진행 상태를 확인할 수 있어요."
        footer={
          <VStack gap="x2" width="full">
            <ActionButton size="large" variant="brandSolid" onClick={() => setSheet(null)}>
              여기서 기다리기
            </ActionButton>
            <ActionButton
              size="large"
              variant="neutralWeak"
              onClick={() => {
                setSheet(null)
                callbacks?.onExitGeneration?.()
              }}
            >
              나가기
            </ActionButton>
          </VStack>
        }
      />

      <SheetFrame
        open={sheet === 'READY_START'}
        onOpenChange={(open) => setSheet(open ? 'READY_START' : null)}
        title="지금 바로 풀어볼까요?"
        description={
          <>
            {questions.length}문제를 한 문제씩 풀고 마지막에 답안을 제출해요.
            <br />
            자동 채점 뒤 서술형은 직접 평가해요.
          </>
        }
        footer={
          <VStack gap="x2" width="full">
            <ActionButton
              size="large"
              variant="brandSolid"
              onClick={() => {
                setSheet(null)
                setScene('SOLVING')
                callbacks?.onStartQuiz?.()
              }}
            >
              지금 풀기
            </ActionButton>
            <ActionButton
              size="large"
              variant="neutralWeak"
              onClick={() => {
                setSheet(null)
                callbacks?.onDeferQuiz?.()
              }}
            >
              나중에 풀기
            </ActionButton>
          </VStack>
        }
      />

      <SheetFrame
        open={sheet === 'QUIZ_EXIT'}
        onOpenChange={(open) => setSheet(open ? 'QUIZ_EXIT' : null)}
        title="지금 나갈까요?"
        description="작성 중인 답이 사라져요. 나갈까요?"
        footer={
          <VStack gap="x2" width="full">
            <ActionButton size="large" variant="brandSolid" onClick={() => setSheet(null)}>
              계속 풀기
            </ActionButton>
            <ActionButton
              size="large"
              variant="neutralWeak"
              onClick={() => {
                setSheet(null)
                callbacks?.onExitQuiz?.()
              }}
            >
              나가기
            </ActionButton>
          </VStack>
        }
      />

      <AnswerMapSheet
        open={sheet === 'ANSWER_MAP'}
        questions={questions}
        answers={answers}
        currentIndex={questionIndex}
        onOpenChange={(open) => setSheet(open ? 'ANSWER_MAP' : null)}
        onSelect={(index) => {
          goToQuestion(index)
          setSheet(null)
        }}
      />

      <SubmitSheet
        open={sheet === 'SUBMIT'}
        unanswered={unansweredQuestions}
        submitting={submitting}
        onOpenChange={(open) => setSheet(open ? 'SUBMIT' : null)}
        onReview={() => {
          goToQuestion(questions.indexOf(unansweredQuestions[0]))
          setSheet(null)
        }}
        onSubmit={() => void submitAnswers()}
      />

      <ResultListSheet
        open={sheet === 'RESULT_LIST'}
        result={resultState}
        selectedId={resultQuestionId}
        onOpenChange={(open) => setSheet(open ? 'RESULT_LIST' : null)}
        onSelect={(questionId) => {
          setResultQuestionId(questionId)
          setSheet(null)
        }}
      />

      {resultItem ? (
        <CorrectionSheet
          open={sheet === 'CORRECTION'}
          item={resultItem}
          outcome={correctionOutcome}
          saving={savingCorrection}
          error={correctionError}
          onOpenChange={(open) => setSheet(open ? 'CORRECTION' : null)}
          onOutcomeChange={setCorrectionOutcome}
          onSave={() => void saveCorrection()}
        />
      ) : null}
    </VStack>
  )
}

function ConditionsScreen({
  materialTitle,
  conditions,
  error,
  onChange,
  onGenerate,
  onBack,
}: {
  materialTitle: string
  conditions: QuizConditions
  error?: string
  onChange: (conditions: QuizConditions) => void
  onGenerate: () => void
  onBack: () => void
}) {
  const toggleType = (type: QuizQuestionType, checked: boolean) => {
    onChange({
      ...conditions,
      selectedTypes: checked
        ? [...new Set([...conditions.selectedTypes, type])]
        : conditions.selectedTypes.filter((candidate) => candidate !== type),
    })
  }

  return (
    <VStack className="quiz-screen">
      <ScreenHeader title="문제 만들기" backLabel="학습 화면으로 돌아가기" onBack={onBack} />
      <VStack className="quiz-content" gap="x8">
        <VStack gap="x2">
          <Text as="h2" textStyle="t10Bold" color="fg.neutral">
            {materialTitle}
          </Text>
          <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
            문제 유형과 난이도, 문제 수를 선택해 주세요.
          </Text>
        </VStack>

        <Field.Root invalid={Boolean(error)}>
          <Field.Label>문제 유형 (1개 이상)</Field.Label>
          <Grid className="quiz-type-grid" columns={2} gap="x3" width="full">
            {(Object.keys(typeLabels) as QuizQuestionType[]).map((type) => (
              <Checkbox.Root
                className="quiz-check-option"
                key={type}
                checked={conditions.selectedTypes.includes(type)}
                onCheckedChange={(checked) => toggleType(type, checked)}
              >
                <Checkbox.HiddenInput />
                <Checkbox.Control>
                  <Checkbox.Indicator checked={<CheckmarkIcon />} />
                </Checkbox.Control>
                <Checkbox.Label>{typeLabels[type]}</Checkbox.Label>
              </Checkbox.Root>
            ))}
          </Grid>
          {error ? (
            <Field.Footer>
              <Field.ErrorMessage>{error}</Field.ErrorMessage>
            </Field.Footer>
          ) : null}
        </Field.Root>

        <ChoiceFieldset
          legend="난이도"
          name="quiz-difficulty"
          value={conditions.difficulty}
          options={(Object.keys(difficultyLabels) as QuizDifficulty[]).map((value) => ({
            value,
            label: difficultyLabels[value],
          }))}
          onChange={(difficulty) =>
            onChange({ ...conditions, difficulty: difficulty as QuizDifficulty })
          }
        />

        <ChoiceFieldset
          legend="최대 문제 수"
          name="quiz-max-count"
          value={String(conditions.maxQuestionCount)}
          options={([5, 10, 15, 20] as QuizMaxQuestionCount[]).map((value) => ({
            value: String(value),
            label: `${value}개`,
          }))}
          description="학습자료 내용에 따라 만들어지는 문제 수가 적을 수 있어요."
          onChange={(maxQuestionCount) =>
            onChange({
              ...conditions,
              maxQuestionCount: Number(maxQuestionCount) as QuizMaxQuestionCount,
            })
          }
        />

        <Field.Root>
          <Field.Label>추가 요청 (선택)</Field.Label>
          <TextField.Root>
            <TextField.Textarea
              className="quiz-generation-prompt"
              value={conditions.generationPrompt ?? ''}
              placeholder="예: 동시성 부분에 집중해서 실무 면접 스타일로 내줘"
              aria-describedby="quiz-generation-prompt-description"
              onChange={(event) => onChange({
                ...conditions,
                generationPrompt: sliceGenerationPrompt(event.currentTarget.value),
              })}
            />
          </TextField.Root>
          <Field.Footer>
            <Field.Description id="quiz-generation-prompt-description">
              출제 초점과 스타일만 반영해요. 문제 유형·수·학습자료 근거는 바꿀 수 없어요.
            </Field.Description>
            <Field.CharacterCount
              current={countGenerationPromptCodePoints(conditions.generationPrompt ?? '')}
              max={GENERATION_PROMPT_MAX_CODE_POINTS}
            />
          </Field.Footer>
        </Field.Root>

        <ActionButton size="large" variant="brandSolid" onClick={onGenerate}>
          문제 만들기
        </ActionButton>
      </VStack>
    </VStack>
  )
}

function GenerationDisclosureDialog({
  open,
  loading,
  onOpenChange,
  onConfirm,
}: {
  open: boolean
  loading: boolean
  onOpenChange: (open: boolean) => void
  onConfirm: () => void
}) {
  return (
    <ContentDialog.Root
      open={open}
      onOpenChange={onOpenChange}
      closeOnEscape={!loading}
      closeOnInteractOutside={!loading}
    >
      <Portal>
        <ContentDialog.Backdrop />
        <ContentDialog.Positioner>
          <ContentDialog.Content width="full" maxWidth="440px">
            <ContentDialog.Header>
              <ContentDialog.Title>학습자료를 OpenAI로 전송할까요?</ContentDialog.Title>
              <ContentDialog.Description>
                문제를 만들기 전에 외부 전송 범위를 확인해 주세요.
              </ContentDialog.Description>
            </ContentDialog.Header>
            <ContentDialog.Body>
              <VStack gap="x4">
                <VStack gap="x1">
                  <Text as="h3" textStyle="t5Bold" color="fg.neutral">전송하는 정보</Text>
                  <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
                    학습자료 본문 전체, 문제 유형·난이도·문제 수, 입력한 추가 요청
                  </Text>
                </VStack>
                <VStack gap="x1">
                  <Text as="h3" textStyle="t5Bold" color="fg.neutral">전송하지 않는 정보</Text>
                  <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
                    이메일·닉네임·비밀번호·인증정보·풀이 답안
                  </Text>
                </VStack>
                <PageBanner.Root tone="informative" variant="weak">
                  <PageBanner.Content>
                    <PageBanner.Body>
                      <PageBanner.Description>
                        기본 악용 방지 모니터링 과정에서 입력과 출력이 최대 30일 보관될 수 있어요.
                      </PageBanner.Description>
                    </PageBanner.Body>
                  </PageBanner.Content>
                </PageBanner.Root>
              </VStack>
            </ContentDialog.Body>
            <ContentDialog.Footer>
              <ContentDialog.Action asChild>
                <ActionButton type="button" size="large" variant="neutralWeak" disabled={loading}>
                  취소
                </ActionButton>
              </ContentDialog.Action>
              <ActionButton
                autoFocus
                type="button"
                size="large"
                variant="brandSolid"
                loading={loading}
                disabled={loading}
                onClick={onConfirm}
              >
                {loading ? '요청 중' : '확인하고 문제 만들기'}
              </ActionButton>
            </ContentDialog.Footer>
          </ContentDialog.Content>
        </ContentDialog.Positioner>
      </Portal>
    </ContentDialog.Root>
  )
}

function ChoiceFieldset({
  legend,
  name,
  value,
  options,
  description,
  onChange,
}: {
  legend: string
  name: string
  value: string
  options: { value: string; label: string }[]
  description?: string
  onChange: (value: string) => void
}) {
  return (
    <fieldset className="quiz-fieldset">
      <Text as="legend" textStyle="t5Bold" color="fg.neutral">
        {legend}
      </Text>
      <Grid className="quiz-segmented" columns={options.length} width="full">
        {options.map((option) => (
          <label key={option.value} data-selected={value === option.value ? '' : undefined}>
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={value === option.value}
              onChange={() => onChange(option.value)}
            />
            <span>{option.label}</span>
          </label>
        ))}
      </Grid>
      {description ? (
        <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
          {description}
        </Text>
      ) : null}
    </fieldset>
  )
}

function GenerationScreen({
  state,
  onExit,
  onRetry,
}: {
  state: QuizFlowPageProps['generationState'] extends infer _T
    ? NonNullable<QuizFlowPageProps['generationState']>
    : never
  onExit: () => void
  onRetry: (error: QuizGenerationFailure) => void
}) {
  return (
    <VStack className="quiz-screen">
      <ScreenHeader title="문제 만드는 중" backLabel="문제 만들기 화면 나가기" onBack={onExit} />
      {state.status === 'GENERATING' ? (
        <VStack className="quiz-status-content" align="center" gap="x6" aria-live="polite">
          <ProgressCircle.Root aria-label="문제 만드는 중" tone="brand" size="40">
            <ProgressCircle.Track />
            <ProgressCircle.Range />
          </ProgressCircle.Root>
          <VStack gap="x2" align="center">
            <Text as="h2" className="quiz-center-copy" textStyle="t10Bold" color="fg.neutral">
              자료를 바탕으로
              <br />
              문제를 만들 수 있어요.
            </Text>
            <Text as="p" className="quiz-center-copy" textStyle="t5Regular" color="fg.neutralMuted">
              완료까지 걸리는 시간은 자료마다 달라요.
            </Text>
          </VStack>
          {state.requestedConfig ? (
            <dl className="quiz-meta-list">
              <div>
                <dt>문제 유형</dt>
                <dd>{state.requestedConfig.selectedTypes.map((type) => typeLabels[type]).join(' · ')}</dd>
              </div>
              <div>
                <dt>난이도</dt>
                <dd>{difficultyLabels[state.requestedConfig.difficulty]}</dd>
              </div>
              <div>
                <dt>최대 문제 수</dt>
                <dd>{state.requestedConfig.maxQuestionCount}개</dd>
              </div>
            </dl>
          ) : null}
          <Box className="quiz-notice" bg="bg.informativeWeak" borderRadius="r3" p="x4">
            <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
              지금 나가도 문제 만들기는 계속돼요. 같은 학습자료를 다시 열면 진행 상태를
              확인할 수 있어요.
            </Text>
          </Box>
          <ActionButton className="quiz-status-action" size="large" variant="neutralWeak" onClick={onExit}>
            나가기
          </ActionButton>
        </VStack>
      ) : state.status === 'ERROR' ? (
        <GenerationError state={state.error} onRetry={() => onRetry(state.error)} />
      ) : null}
    </VStack>
  )
}

function GenerationError({
  state,
  onRetry,
}: {
  state: QuizGenerationFailure
  onRetry: () => void
}) {
  const copy = generationErrorCopy(state)
  return (
    <VStack className="quiz-status-content" align="center" gap="x6" aria-live="polite">
      <Icon svg={<IconExclamationmarkCircleFill />} size="x10" color="fg.critical" />
      <VStack gap="x2" align="center">
        <Text as="h2" className="quiz-center-copy" textStyle="t10Bold" color="fg.neutral">
          {copy.title}
        </Text>
        <Text as="p" className="quiz-center-copy" textStyle="t5Regular" color="fg.neutralMuted">
          {copy.description}
        </Text>
      </VStack>
      <ActionButton className="quiz-status-action" size="large" variant="brandSolid" onClick={onRetry}>
        {copy.action}
      </ActionButton>
    </VStack>
  )
}

function ReadyScreen({
  materialTitle,
  ready,
  onBack,
  onStart,
}: {
  materialTitle: string
  ready: QuizGenerationReady
  onBack: () => void
  onStart: () => void
}) {
  return (
    <VStack className="quiz-screen">
      <ScreenHeader title="문제 준비 완료" backLabel="학습 화면으로 돌아가기" onBack={onBack} />
      <VStack className="quiz-status-content" align="center" gap="x6" aria-live="polite">
        <Flex
          className="quiz-ready-icon"
          align="center"
          justify="center"
          bg="bg.brandSolid"
          borderRadius="full"
          aria-hidden
        >
          <Icon svg={<IconCheckmarkLine />} size="x6" color="palette.staticWhite" />
        </Flex>
        <VStack gap="x2" align="center">
          <Text as="h2" className="quiz-center-copy" textStyle="t10Bold" color="fg.neutral">
            {ready.actualCount}문제를 만들었어요
          </Text>
          <Text as="p" className="quiz-center-copy" textStyle="t5Regular" color="fg.neutralMuted">
            {ready.requestedConfig
              ? `요청한 ${ready.requestedConfig.maxQuestionCount}개 중 학습자료로 만들 수 있는 ${ready.actualCount}문제를 준비했어요.`
              : `학습자료로 만들 수 있는 ${ready.actualCount}문제를 준비했어요.`}
          </Text>
        </VStack>
        <dl className="quiz-meta-list">
          <div>
            <dt>문제 유형</dt>
            <dd>{ready.includedTypes.map((type) => typeLabels[type]).join(' · ')}</dd>
          </div>
          <div>
            <dt>난이도</dt>
            <dd>
              {ready.requestedConfig
                ? difficultyLabels[ready.requestedConfig.difficulty]
                : '요청 조건 정보 없음'}
            </dd>
          </div>
          <div>
            <dt>학습자료</dt>
            <dd>{materialTitle}</dd>
          </div>
        </dl>
        <ActionButton className="quiz-status-action" size="large" variant="brandSolid" onClick={onStart}>
          문제 풀기
        </ActionButton>
      </VStack>
    </VStack>
  )
}

function SolvingScreen({
  materialTitle,
  question,
  questionIndex,
  questionCount,
  answeredCount,
  answer,
  headingRef,
  onAnswer,
  onExit,
  onOpenMap,
  onPrevious,
  submitting,
  onNext,
}: {
  materialTitle: string
  question: QuizQuestion
  questionIndex: number
  questionCount: number
  answeredCount: number
  answer?: QuizAnswer
  headingRef: React.RefObject<HTMLHeadingElement | null>
  onAnswer: (answer: QuizAnswer) => void
  onExit: () => void
  onOpenMap: () => void
  onPrevious: () => void
  submitting: boolean
  onNext: () => void
}) {
  return (
    <VStack className="quiz-screen quiz-solving-screen">
      <ScreenHeader
        title={materialTitle}
        backLabel="퀴즈 나가기"
        onBack={onExit}
        action={
          <ActionButton size="small" variant="ghost" onClick={onOpenMap}>
            풀이 현황
          </ActionButton>
        }
      />
      <VStack className="quiz-content quiz-solving-content" gap="x8">
        <VStack gap="x3">
          <HStack justify="space-between" align="center" gap="x3">
            <Text textStyle="t5Bold" color="fg.neutral">
              문제 {questionIndex + 1} / {questionCount}
            </Text>
            <Text textStyle="t4Regular" color="fg.neutralMuted">
              {answeredCount}개 답함 · {questionCount - answeredCount}개 남음
            </Text>
          </HStack>
          <div
            className="quiz-progress-track"
            role="progressbar"
            aria-label="퀴즈 진행률"
            aria-valuemin={1}
            aria-valuemax={questionCount}
            aria-valuenow={questionIndex + 1}
          >
            {Array.from({ length: questionCount }, (_, index) => (
              <span key={index} data-active={index <= questionIndex ? '' : undefined} />
            ))}
          </div>
        </VStack>

        <VStack key={question.questionId} className="quiz-question-panel" gap="x8">
          <VStack gap="x3" aria-live="polite">
            <Text textStyle="t4Bold" color="fg.brand">
              {typeLabels[question.type]}
            </Text>
            <Text
              as="h2"
              ref={headingRef}
              tabIndex={-1}
              className="quiz-question-heading"
              textStyle="t7Bold"
              color="fg.neutral"
            >
              {question.number}. {question.prompt}
            </Text>
          </VStack>

          <QuestionAnswer question={question} answer={answer} onAnswer={onAnswer} />
        </VStack>

        <HStack className="quiz-solving-actions" gap="x3">
          <ActionButton
            size="large"
            variant="neutralWeak"
            disabled={questionIndex === 0}
            onClick={onPrevious}
          >
            이전 문제
          </ActionButton>
          <ActionButton
            size="large"
            variant="brandSolid"
            loading={questionIndex === questionCount - 1 && submitting}
            disabled={submitting}
            onClick={onNext}
          >
            {questionIndex === questionCount - 1 ? '답안 제출' : '다음 문제'}
          </ActionButton>
        </HStack>
      </VStack>
    </VStack>
  )
}

function QuestionAnswer({
  question,
  answer,
  onAnswer,
}: {
  question: QuizQuestion
  answer?: QuizAnswer
  onAnswer: (answer: QuizAnswer) => void
}) {
  if (question.type === 'MULTIPLE_CHOICE') {
    const selected = answer?.type === 'MULTIPLE_CHOICE' ? answer.selectedChoiceId : ''
    return (
      <fieldset className="quiz-fieldset quiz-objective-options">
        <legend className="quiz-sr-only">답 선택</legend>
        <VStack gap="x3">
          {question.choices.map((choice, index) => (
            <label key={choice.choiceId} data-selected={selected === choice.choiceId ? '' : undefined}>
              <input
                type="radio"
                name={`answer-${question.questionId}`}
                value={choice.choiceId}
                checked={selected === choice.choiceId}
                onChange={() =>
                  onAnswer({ type: 'MULTIPLE_CHOICE', selectedChoiceId: choice.choiceId })
                }
              />
              <span>
                {index + 1}. {choice.text}
              </span>
            </label>
          ))}
        </VStack>
      </fieldset>
    )
  }

  if (question.type === 'FILL_IN_THE_BLANK') {
    const blankAnswers = answer?.type === 'FILL_IN_THE_BLANK' ? answer.blankAnswers : {}
    return (
      <VStack gap="x4">
        {question.blanks.map((blank) => (
          <Field.Root key={blank.blankId}>
            <Field.Label>{blank.number}번</Field.Label>
            <TextField.Root>
              <TextField.Input
                value={blankAnswers[blank.blankId] ?? ''}
                placeholder={`${blank.number}번 답을 입력해 주세요`}
                autoComplete="off"
                onChange={(event) =>
                  onAnswer({
                    type: 'FILL_IN_THE_BLANK',
                    blankAnswers: {
                      ...blankAnswers,
                      [blank.blankId]: event.currentTarget.value,
                    },
                  })
                }
              />
            </TextField.Root>
          </Field.Root>
        ))}
      </VStack>
    )
  }

  if (question.type === 'SHORT_ANSWER') {
    const value = answer?.type === 'SHORT_ANSWER' ? answer.text : ''
    return (
      <Field.Root>
        <Field.Label>답</Field.Label>
        <TextField.Root>
          <TextField.Input
            value={value}
            placeholder="단어나 짧은 문장으로 입력해 주세요"
            autoComplete="off"
            onChange={(event) =>
              onAnswer({ type: 'SHORT_ANSWER', text: event.currentTarget.value })
            }
          />
        </TextField.Root>
      </Field.Root>
    )
  }

  const value = answer?.type === 'ESSAY' ? answer.text : ''
  return (
    <VStack gap="x4">
      <Field.Root>
        <Field.Label>내 답</Field.Label>
        <TextField.Root>
          <TextField.Textarea
            className="quiz-essay-input"
            value={value}
            placeholder="내 답을 작성해 주세요"
            onChange={(event) => onAnswer({ type: 'ESSAY', text: event.currentTarget.value })}
          />
        </TextField.Root>
      </Field.Root>
      <Box className="quiz-notice" bg="bg.informativeWeak" borderRadius="r3" p="x4">
        <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
          서술형은 자동 점수에 포함되지 않아요.
          <br />
          제출한 뒤 모범 답안과 핵심 포인트를 보고 직접 평가해요.
        </Text>
      </Box>
    </VStack>
  )
}

function AnswerMapSheet({
  open,
  questions,
  answers,
  currentIndex,
  onOpenChange,
  onSelect,
}: {
  open: boolean
  questions: QuizQuestion[]
  answers: QuizAnswers
  currentIndex: number
  onOpenChange: (open: boolean) => void
  onSelect: (index: number) => void
}) {
  const answeredCount = questions.filter((question) =>
    isQuestionAnswered(question, answers[question.questionId]),
  ).length
  const firstUnanswered = questions.findIndex(
    (question) => !isQuestionAnswered(question, answers[question.questionId]),
  )
  return (
    <SheetFrame
      open={open}
      onOpenChange={onOpenChange}
      title="풀이 현황"
      description={`${answeredCount}개 답함 · ${questions.length - answeredCount}개 남음`}
      footer={
        <VStack gap="x2" width="full">
          <ActionButton size="large" variant="brandSolid" onClick={() => onOpenChange(false)}>
            계속 풀기
          </ActionButton>
          {firstUnanswered >= 0 ? (
            <ActionButton
              size="large"
              variant="neutralWeak"
              onClick={() => onSelect(firstUnanswered)}
            >
              답하지 않은 첫 문제로 이동
            </ActionButton>
          ) : null}
        </VStack>
      }
    >
      <Grid className="quiz-question-map" columns={5} gap="x2" aria-label="문항별 답안 상태">
        {questions.map((question, index) => {
          const answered = isQuestionAnswered(question, answers[question.questionId])
          return (
            <button
              key={question.questionId}
              type="button"
              data-answered={answered ? '' : undefined}
              data-current={index === currentIndex ? '' : undefined}
              aria-current={index === currentIndex ? 'step' : undefined}
              aria-label={`${question.number}번, ${answered ? '답함' : '미응답'}${index === currentIndex ? ', 현재 문제' : ''}`}
              onClick={() => onSelect(index)}
            >
              {question.number}
            </button>
          )
        })}
      </Grid>
    </SheetFrame>
  )
}

function SubmitSheet({
  open,
  unanswered,
  submitting,
  onOpenChange,
  onReview,
  onSubmit,
}: {
  open: boolean
  unanswered: QuizQuestion[]
  submitting: boolean
  onOpenChange: (open: boolean) => void
  onReview: () => void
  onSubmit: () => void
}) {
  return (
    <SheetFrame
      open={open}
      onOpenChange={onOpenChange}
      dismissible={!submitting}
      title="아직 답하지 않은 문제가 있어요"
      description="지금 제출하면 답하지 않은 문제는 오답으로 처리되고 복습에 포함돼요."
      footer={
        <VStack gap="x2" width="full">
          <ActionButton
            size="large"
            variant="brandSolid"
            disabled={submitting}
            onClick={onReview}
          >
            남은 문제 풀기
          </ActionButton>
          <ActionButton
            size="large"
            variant="neutralWeak"
            loading={submitting}
            disabled={submitting}
            onClick={onSubmit}
          >
            그대로 제출
          </ActionButton>
        </VStack>
      }
    >
      <VStack gap="x4">
        <Box className="quiz-submit-summary" bg="bg.neutralWeak" borderRadius="r3" p="x4">
          <Text as="p" textStyle="t5Bold" color="fg.neutral">
            아직 풀지 않은 문제 {unanswered.length}개
          </Text>
          <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
            {unanswered.map((question) => `${question.number}번`).join(', ')}에 답하지 않았어요.
          </Text>
        </Box>
        <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
          제출 전에는 언제든 답을 바꿀 수 있어요. 제출한 뒤에는 답을 바꿀 수 없어요.
        </Text>
      </VStack>
    </SheetFrame>
  )
}

function SubmissionErrorScreen({
  submitting,
  error,
  onBack,
  onRetry,
}: {
  submitting: boolean
  error?: string
  onBack: () => void
  onRetry: () => void
}) {
  return (
    <VStack className="quiz-screen">
      <ScreenHeader title="답안 제출" backLabel="마지막 문제로 돌아가기" onBack={onBack} />
      <VStack className="quiz-status-content" align="center" gap="x6" aria-live="polite">
        <Icon svg={<IconExclamationmarkCircleFill />} size="x10" color="fg.critical" />
        <VStack gap="x2" align="center">
          <Text as="h2" className="quiz-center-copy" textStyle="t10Bold" color="fg.neutral">
            답안을 제출하지 못했어요
          </Text>
          <Text as="p" className="quiz-center-copy" textStyle="t5Regular" color="fg.neutralMuted">
            {error ?? '입력한 답은 그대로 유지돼요. 잠시 후 다시 시도해 주세요.'}
          </Text>
        </VStack>
        <VStack className="quiz-status-action" gap="x2">
          <ActionButton
            size="large"
            variant="brandSolid"
            loading={submitting}
            disabled={submitting}
            onClick={onRetry}
          >
            다시 제출
          </ActionButton>
          <ActionButton
            size="large"
            variant="neutralWeak"
            disabled={submitting}
            onClick={onBack}
          >
            답안 확인
          </ActionButton>
        </VStack>
      </VStack>
    </VStack>
  )
}

function EssayAssessmentScreen({
  item,
  currentNumber,
  totalCount,
  outcome,
  saving,
  error,
  onBack,
  onOutcomeChange,
  onSave,
}: {
  item: QuizResultItem
  currentNumber: number
  totalCount: number
  outcome?: QuizResultOutcome
  saving: boolean
  error?: string
  onBack: () => void
  onOutcomeChange: (outcome: QuizResultOutcome) => void
  onSave: () => void
}) {
  return (
    <VStack className="quiz-screen">
      <ScreenHeader
        title="서술형 자기평가"
        backLabel="학습 화면으로 돌아가기"
        onBack={onBack}
      />
      <VStack className="quiz-content" gap="x6">
        <VStack gap="x2" aria-live="polite">
          <Text textStyle="t4Bold" color="fg.brand">
            자기평가 {currentNumber} / {totalCount} · {item.number}번
          </Text>
          <Text as="h2" textStyle="t7Bold" color="fg.neutral">
            {item.prompt}
          </Text>
        </VStack>

        {currentNumber === 1 ? (
          <Box className="quiz-notice" bg="bg.informativeWeak" borderRadius="r3" p="x4">
            <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
              서술형은 자동 점수에 포함되지 않아요. 내 답을 모범 답안과 비교해 직접 평가해
              주세요.
            </Text>
          </Box>
        ) : null}

        {error ? (
          <PageBanner.Root tone="critical" variant="weak">
            <PageBanner.Content>
              <PageBanner.Body>
                <PageBanner.Title>자기평가를 저장하지 못했어요</PageBanner.Title>
                <PageBanner.Description>{error}</PageBanner.Description>
              </PageBanner.Body>
            </PageBanner.Content>
          </PageBanner.Root>
        ) : null}

        <ResultReadingSection title="내 답">{item.answer}</ResultReadingSection>
        <ResultReadingSection title="모범 답안">{item.correctAnswer}</ResultReadingSection>
        <VStack className="quiz-reading-section" gap="x2" bg="bg.neutralWeak" borderRadius="r3" p="x4">
          <Text as="h3" textStyle="t5Bold" color="fg.neutral">
            핵심 포인트
          </Text>
          <ul className="quiz-key-points">
            {(item.keyPoints ?? []).map((keyPoint) => (
              <li key={keyPoint}>{keyPoint}</li>
            ))}
          </ul>
        </VStack>
        <ResultReadingSection title="해설">{item.explanation}</ResultReadingSection>
        <ResultReadingSection title="학습자료 본문">{item.sourceExcerpt}</ResultReadingSection>

        <ChoiceFieldset
          legend="내 평가"
          name={`essay-assessment-${item.questionId}`}
          value={outcome ?? ''}
          options={[
            { value: 'CORRECT', label: '정답' },
            { value: 'PARTIAL', label: '보완 필요' },
            { value: 'INCORRECT', label: '오답' },
          ]}
          onChange={(value) => onOutcomeChange(value as QuizResultOutcome)}
        />
        <ActionButton
          size="large"
          variant="brandSolid"
          loading={saving}
          disabled={saving || !outcome}
          onClick={onSave}
        >
          {currentNumber === totalCount ? '평가 저장하고 결과 보기' : '평가 저장하고 다음'}
        </ActionButton>
      </VStack>
    </VStack>
  )
}

function ResultScreen({
  result,
  item,
  title,
  correctionAvailable,
  reviewStarting,
  reviewStartError,
  onBack,
  onOpenList,
  onCorrect,
  onStartReview,
  onGoHome,
}: {
  result: QuizResult
  item: QuizResultItem
  title: string
  correctionAvailable: boolean
  reviewStarting: boolean
  reviewStartError?: string
  onBack: () => void
  onOpenList: () => void
  onCorrect: () => void
  onStartReview?: () => void
  onGoHome?: () => void
}) {
  const reviewActionAvailable = shouldShowQuizReviewAction(result) && Boolean(onStartReview)
  return (
    <VStack className="quiz-screen">
      <ScreenHeader
        title={title}
        backLabel="학습 화면으로 돌아가기"
        onBack={onBack}
        action={
          <ActionButton size="small" variant="ghost" onClick={onOpenList}>
            문제 목록
          </ActionButton>
        }
      />
      <VStack className="quiz-content" gap="x6">
        <Grid className="quiz-result-summary" columns={2} gap="x2" aria-live="polite">
          <VStack gap="x1">
            <Text textStyle="t4Regular" color="fg.neutralMuted">
              채점 점수
            </Text>
            <Text textStyle="t10Bold" color="fg.neutral">
              {result.summary.correctCount} / {result.summary.gradedCount}
            </Text>
          </VStack>
          <VStack gap="x1">
            <Text textStyle="t4Regular" color="fg.neutralMuted">
              복습할 문제
            </Text>
            <Text textStyle="t10Bold" color="fg.neutral">
              {result.summary.reviewCount}개
            </Text>
          </VStack>
          <VStack className="quiz-result-essay-summary" gap="x1">
            <Text textStyle="t4Regular" color="fg.neutralMuted">
              서술형 자기평가
            </Text>
            <Text textStyle="t5Bold" color="fg.neutral">
              정답 {result.summary.essayCorrectCount} · 보완 필요 {result.summary.essayPartialCount} ·
              오답 {result.summary.essayIncorrectCount}
            </Text>
          </VStack>
        </Grid>

        <VStack as="section" gap="x5" aria-labelledby="quiz-result-question">
          <HStack justify="space-between" align="center" gap="x3">
            <Text textStyle="t4Bold" color="fg.brand">
              {typeLabels[item.type]} · {item.number}번
            </Text>
            <OutcomeLabel outcome={item.outcome} />
          </HStack>
          <VStack gap="x2">
            <Text textStyle="t3Medium" color="fg.neutralMuted">
              {item.topic}
            </Text>
            <Text as="h2" id="quiz-result-question" textStyle="t6Bold" color="fg.neutral">
              {item.prompt}
            </Text>
          </VStack>
          <dl className="quiz-result-answer-list">
            <div>
              <dt>내 답</dt>
              <dd>{item.answer || '답하지 않음'}</dd>
            </div>
            <div>
              <dt>{item.type === 'ESSAY' ? '모범 답안' : '정답 예시'}</dt>
              <dd>{item.correctAnswer}</dd>
            </div>
            <div>
              <dt>판정</dt>
              <dd>{outcomeLabel(item.outcome)}</dd>
            </div>
          </dl>

          {item.editable && correctionAvailable ? (
            <HStack className="quiz-correction-row" align="center" gap="x3">
              <ActionButton
                className="quiz-correction-action"
                size="medium"
                variant="brandOutline"
                onClick={onCorrect}
              >
                채점 수정
              </ActionButton>
              <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
                답을 작성한 단답형과 빈칸 채우기는 채점 결과를 직접 수정할 수 있어요.
              </Text>
            </HStack>
          ) : item.type === 'MULTIPLE_CHOICE' ? (
            <Box className="quiz-notice" bg="bg.informativeWeak" borderRadius="r3" p="x4">
              <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
                객관식은 서버가 채점한 현재 결과를 그대로 보여줘요.
              </Text>
            </Box>
          ) : null}

          {item.type === 'ESSAY' && item.keyPoints ? (
            <VStack
              className="quiz-reading-section"
              gap="x2"
              bg="bg.neutralWeak"
              borderRadius="r3"
              p="x4"
            >
              <Text as="h3" textStyle="t5Bold" color="fg.neutral">
                핵심 포인트
              </Text>
              <ul className="quiz-key-points">
                {item.keyPoints.map((keyPoint) => (
                  <li key={keyPoint}>{keyPoint}</li>
                ))}
              </ul>
            </VStack>
          ) : null}

          <ResultReadingSection title="해설">{item.explanation}</ResultReadingSection>
          <ResultReadingSection title="학습자료 본문">{item.sourceExcerpt}</ResultReadingSection>
        </VStack>

        <VStack className="quiz-result-actions" gap="x2" align="stretch">
          {reviewStartError ? (
            <Text as="p" textStyle="t4Regular" color="fg.critical" role="alert">
              {reviewStartError}
            </Text>
          ) : null}
          {reviewActionAvailable && onStartReview ? (
            <ActionButton
              size="large"
              variant="brandSolid"
              loading={reviewStarting}
              disabled={reviewStarting}
              onClick={onStartReview}
            >
              복습하기
            </ActionButton>
          ) : result.summary.reviewCount === 0 ? (
            <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
              복습할 문제가 없어요.
            </Text>
          ) : null}
          {onGoHome ? (
            <ActionButton
              size="large"
              variant={reviewActionAvailable ? 'neutralWeak' : 'brandSolid'}
              onClick={onGoHome}
            >
              홈으로
            </ActionButton>
          ) : null}
        </VStack>
      </VStack>
    </VStack>
  )
}

function outcomeLabel(outcome?: QuizResultOutcome) {
  return getQuizOutcomeLabel(outcome)
}

function OutcomeLabel({ outcome }: { outcome?: QuizResultOutcome }) {
  return (
    <Text
      className="quiz-outcome"
      data-outcome={outcome}
      textStyle="t4Bold"
      color={
        outcome === 'CORRECT'
          ? 'fg.positive'
          : outcome === 'PARTIAL'
            ? 'fg.warning'
            : 'fg.critical'
      }
    >
      {outcomeLabel(outcome)}
    </Text>
  )
}

function ResultReadingSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <VStack className="quiz-reading-section" gap="x2" bg="bg.neutralWeak" borderRadius="r3" p="x4">
      <Text as="h3" textStyle="t5Bold" color="fg.neutral">
        {title}
      </Text>
      <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
        {children}
      </Text>
    </VStack>
  )
}

function ResultListSheet({
  open,
  result,
  selectedId,
  onOpenChange,
  onSelect,
}: {
  open: boolean
  result: QuizResult
  selectedId?: string
  onOpenChange: (open: boolean) => void
  onSelect: (questionId: string) => void
}) {
  return (
    <SheetFrame
      open={open}
      onOpenChange={onOpenChange}
      title="문제 목록"
      description="확인할 문제를 선택해 주세요."
      footer={
        <ActionButton size="large" variant="neutralWeak" onClick={() => onOpenChange(false)}>
          <Text textStyle="t5Regular">닫기</Text>
        </ActionButton>
      }
    >
      <VStack className="quiz-result-list" gap="x2">
        {result.items.map((item) => (
          <button
            type="button"
            key={item.questionId}
            data-current={item.questionId === selectedId ? '' : undefined}
            onClick={() => onSelect(item.questionId)}
          >
            <span>
              {item.number}번 · {typeLabels[item.type]}
            </span>
            <span className="quiz-result-list-outcome">{outcomeLabel(item.outcome)}</span>
          </button>
        ))}
      </VStack>
    </SheetFrame>
  )
}

function CorrectionSheet({
  open,
  item,
  outcome,
  saving,
  error,
  onOpenChange,
  onOutcomeChange,
  onSave,
}: {
  open: boolean
  item: QuizResultItem
  outcome: QuizBinaryOutcome
  saving: boolean
  error?: string
  onOpenChange: (open: boolean) => void
  onOutcomeChange: (outcome: QuizBinaryOutcome) => void
  onSave: () => void
}) {
  return (
    <SheetFrame
      open={open}
      onOpenChange={onOpenChange}
      dismissible={!saving}
      title="채점 결과 수정"
      description="내 답과 정답 예시를 비교하고 이 문제의 현재 판정을 선택해 주세요."
      footer={
        <VStack gap="x2" width="full">
          <ActionButton
            size="large"
            variant="brandSolid"
            loading={saving}
            disabled={saving || outcome === item.outcome}
            onClick={onSave}
          >
            {outcome === 'CORRECT' ? '정답으로 변경' : '오답으로 변경'}
          </ActionButton>
          <ActionButton
            size="large"
            variant="neutralWeak"
            disabled={saving}
            onClick={() => onOpenChange(false)}
          >
            취소
          </ActionButton>
        </VStack>
      }
    >
      <VStack gap="x5" aria-live="polite">
        {error ? (
          <PageBanner.Root tone="critical" variant="weak">
            <PageBanner.Content>
              <PageBanner.Body>
                <PageBanner.Title>저장하지 못했어요</PageBanner.Title>
                <PageBanner.Description>{error}</PageBanner.Description>
              </PageBanner.Body>
            </PageBanner.Content>
          </PageBanner.Root>
        ) : null}
        <dl className="quiz-correction-comparison">
          <div>
            <dt>내 답</dt>
            <dd>{item.answer}</dd>
          </div>
          <div>
            <dt>정답 예시</dt>
            <dd>{item.correctAnswer}</dd>
          </div>
        </dl>
        <ChoiceFieldset
          legend="현재 판정"
          name={`correction-${item.questionId}`}
          value={outcome}
          options={[
            { value: 'CORRECT', label: '정답' },
            { value: 'INCORRECT', label: '오답' },
          ]}
          onChange={(value) => onOutcomeChange(value as QuizBinaryOutcome)}
        />
        <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
          저장되면 선택한 판정만 현재 결과로 표시되고 점수와 복습할 문제 수가 함께 바뀌어요.
        </Text>
      </VStack>
    </SheetFrame>
  )
}
