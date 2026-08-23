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

import type {
  QuizAnswer,
  QuizAnswers,
  QuizConditions,
  QuizDifficulty,
  QuizFlowPageProps,
  QuizGenerationFailure,
  QuizGenerationReady,
  QuizMaxCount,
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
  FILL_BLANK: '빈칸 채우기',
  SHORT_ANSWER: '단답형',
  ESSAY: '서술형',
}

const difficultyLabels: Record<QuizDifficulty, string> = {
  EASY: '쉬움',
  NORMAL: '보통',
  HARD: '어려움',
}

function CheckmarkIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24">
      <path
        d="m5 12.5 4.25 4.25L19 7"
        fill="none"
        stroke="currentColor"
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

function isAnswered(question: QuizQuestion, answer: QuizAnswer | undefined) {
  if (!answer || answer.type !== question.type) return false
  if (answer.type === 'MULTIPLE_CHOICE') return answer.choiceId.length > 0
  if (answer.type === 'FILL_BLANK') {
    return question.type === 'FILL_BLANK'
      ? question.blanks.every((blank) => answer.values[blank.id]?.trim())
      : false
  }
  return answer.value.trim().length > 0
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
  initialScene = 'CONDITIONS',
  initialConditions = { questionTypes: ['MULTIPLE_CHOICE'], difficulty: 'NORMAL', maxCount: 10 },
  initialAnswers = {},
  generationState,
  callbacks,
}: QuizFlowPageProps) {
  const [scene, setScene] = useState(initialScene)
  const [conditions, setConditions] = useState(initialConditions)
  const [conditionsError, setConditionsError] = useState<string>()
  const [generation, setGeneration] = useState(
    generationState ?? ({ status: 'GENERATING' } as const),
  )
  const [sheet, setSheet] = useState<Sheet>(initialScene === 'READY' ? 'READY_START' : null)
  const [answers, setAnswers] = useState<QuizAnswers>(initialAnswers)
  const [questionIndex, setQuestionIndex] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string>()
  const [resultState, setResultState] = useState<QuizResult>(result)
  const [resultQuestionId, setResultQuestionId] = useState(
    result.items.find((item) => item.type === 'SHORT_ANSWER')?.questionId ?? result.items[0]?.questionId,
  )
  const [correctionOutcome, setCorrectionOutcome] = useState<QuizResultOutcome>('CORRECT')
  const [savingCorrection, setSavingCorrection] = useState(false)
  const [correctionError, setCorrectionError] = useState<string>()
  const questionHeadingRef = useRef<HTMLHeadingElement>(null)

  const currentQuestion = questions[questionIndex]
  const answeredCount = questions.filter((question) => isAnswered(question, answers[question.id])).length
  const unansweredQuestions = questions.filter(
    (question) => !isAnswered(question, answers[question.id]),
  )
  const resultItem = resultState.items.find((item) => item.questionId === resultQuestionId)

  useEffect(() => {
    if (!generationState) return
    setGeneration(generationState)
    if (generationState.status === 'READY') {
      setScene('READY')
      setSheet('READY_START')
    } else {
      setScene('GENERATION')
    }
  }, [generationState])

  useEffect(() => {
    if (scene === 'SOLVING') questionHeadingRef.current?.focus()
  }, [questionIndex, scene])

  const updateConditions = (next: QuizConditions) => {
    setConditions(next)
    setConditionsError(undefined)
    callbacks?.onConditionsChange?.(next)
  }

  const startGeneration = async () => {
    if (conditions.questionTypes.length === 0) {
      setConditionsError('문제 유형을 하나 이상 선택해 주세요.')
      return
    }
    setGeneration({ status: 'GENERATING' })
    setScene('GENERATION')
    try {
      const ready = await callbacks?.onGenerate?.(conditions)
      if (ready) showReady(ready)
    } catch {
      setGeneration({
        status: 'ERROR',
        error: { kind: 'REQUEST_FAILED', retryable: true },
      })
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
    } catch {
      setGeneration({ status: 'ERROR', error: failure })
    }
  }

  const setAnswer = (questionId: string, answer: QuizAnswer) => {
    setAnswers((current) => {
      const next = { ...current, [questionId]: answer }
      callbacks?.onAnswersChange?.(next)
      return next
    })
  }

  const goToQuestion = (index: number) => {
    const normalized = Math.max(0, Math.min(index, questions.length - 1))
    setQuestionIndex(normalized)
    callbacks?.onNavigateQuestion?.(questions[normalized].id)
  }

  const requestSubmit = () => {
    setSubmitError(undefined)
    if (unansweredQuestions.length > 0) {
      setSheet('SUBMIT')
      return
    }
    void submitAnswers()
  }

  const submitAnswers = async () => {
    setSubmitting(true)
    setSubmitError(undefined)
    try {
      await callbacks?.onSubmit?.({
        answers,
        unansweredQuestionIds: unansweredQuestions.map((question) => question.id),
      })
      setSheet(null)
      setScene('RESULT')
    } catch {
      setSubmitError('답안을 제출하지 못했어요. 입력한 답은 유지되며 다시 시도할 수 있어요.')
      setSheet('SUBMIT')
    } finally {
      setSubmitting(false)
    }
  }

  const openCorrection = (item: QuizResultItem) => {
    setCorrectionOutcome(item.outcome === 'CORRECT' ? 'INCORRECT' : 'CORRECT')
    setCorrectionError(undefined)
    setSheet('CORRECTION')
  }

  const saveCorrection = async () => {
    const updateShortAnswerOutcome = callbacks?.onUpdateShortAnswerOutcome
    if (!resultItem || !resultItem.editable || !updateShortAnswerOutcome) return
    setSavingCorrection(true)
    setCorrectionError(undefined)
    const previousOutcome = resultItem.outcome
    try {
      const serverSummary = await updateShortAnswerOutcome({
        questionId: resultItem.questionId,
        outcome: correctionOutcome,
      })
      setResultState((current) => {
        const delta =
          previousOutcome === correctionOutcome ? 0 : correctionOutcome === 'CORRECT' ? 1 : -1
        return {
          summary:
            serverSummary ?? {
              ...current.summary,
              correctCount: current.summary.correctCount + delta,
              reviewCount: Math.max(0, current.summary.reviewCount - delta),
            },
          items: current.items.map((item) =>
            item.questionId === resultItem.questionId
              ? { ...item, outcome: correctionOutcome, edited: true }
              : item,
          ),
        }
      })
      setSheet(null)
    } catch {
      setCorrectionError('채점 결과를 저장하지 못했어요. 이전 판정과 점수는 그대로 유지돼요.')
    } finally {
      setSavingCorrection(false)
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
            onExit={() => setSheet('GENERATION_EXIT')}
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
            materialTitle={materialTitle}
            question={currentQuestion}
            questionIndex={questionIndex}
            questionCount={questions.length}
            answeredCount={answeredCount}
            answer={answers[currentQuestion.id]}
            headingRef={questionHeadingRef}
            onAnswer={(answer) => setAnswer(currentQuestion.id, answer)}
            onExit={() => setSheet('QUIZ_EXIT')}
            onOpenMap={() => setSheet('ANSWER_MAP')}
            onPrevious={() => goToQuestion(questionIndex - 1)}
            onNext={() => {
              if (questionIndex === questions.length - 1) requestSubmit()
              else goToQuestion(questionIndex + 1)
            }}
          />
        ) : null}
        {scene === 'RESULT' && resultItem ? (
          <ResultScreen
            result={resultState}
            item={resultItem}
            correctionAvailable={Boolean(callbacks?.onUpdateShortAnswerOutcome)}
            onBack={() => callbacks?.onResultExit?.()}
            onOpenList={() => setSheet('RESULT_LIST')}
            onCorrect={() => openCorrection(resultItem)}
          />
        ) : null}
      </Box>

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
        description="입력한 답과 위치는 이 기기에 최대 7일 동안 보관될 수 있어요. 다른 기기에서는 이어서 풀 수 없어요."
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
        error={submitError}
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
      questionTypes: checked
        ? [...new Set([...conditions.questionTypes, type])]
        : conditions.questionTypes.filter((candidate) => candidate !== type),
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
                checked={conditions.questionTypes.includes(type)}
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
          value={String(conditions.maxCount)}
          options={([5, 10, 15] as QuizMaxCount[]).map((value) => ({
            value: String(value),
            label: `${value}개`,
          }))}
          description="학습자료 내용에 따라 만들어지는 문제 수가 적을 수 있어요."
          onChange={(maxCount) =>
            onChange({ ...conditions, maxCount: Number(maxCount) as QuizMaxCount })
          }
        />

        <ActionButton size="large" variant="brandSolid" onClick={onGenerate}>
          문제 만들기
        </ActionButton>
      </VStack>
    </VStack>
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
            요청한 {ready.requestedCount}개 중 학습자료로 만들 수 있는
            <br />
            {ready.actualCount}문제를 준비했어요.
          </Text>
        </VStack>
        <dl className="quiz-meta-list">
          <div>
            <dt>문제 유형</dt>
            <dd>{ready.conditions.questionTypes.map((type) => typeLabels[type]).join(' · ')}</dd>
          </div>
          <div>
            <dt>난이도</dt>
            <dd>{difficultyLabels[ready.conditions.difficulty]}</dd>
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

        <VStack key={question.id} className="quiz-question-panel" gap="x8">
          <VStack gap="x3" aria-live="polite">
            <Text textStyle="t4Bold" color="fg.brand">
              {typeLabels[question.type]}
            </Text>
            <Text
              as="h2"
              ref={headingRef}
              tabIndex={-1}
              className="quiz-question-heading"
              textStyle="t8Bold"
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
          <ActionButton size="large" variant="brandSolid" onClick={onNext}>
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
    const selected = answer?.type === 'MULTIPLE_CHOICE' ? answer.choiceId : ''
    return (
      <fieldset className="quiz-fieldset quiz-objective-options">
        <legend className="quiz-sr-only">답 선택</legend>
        <VStack gap="x3">
          {question.choices.map((choice, index) => (
            <label key={choice.id} data-selected={selected === choice.id ? '' : undefined}>
              <input
                type="radio"
                name={`answer-${question.id}`}
                value={choice.id}
                checked={selected === choice.id}
                onChange={() => onAnswer({ type: 'MULTIPLE_CHOICE', choiceId: choice.id })}
              />
              <span>
                {index + 1}. {choice.label}
              </span>
            </label>
          ))}
        </VStack>
      </fieldset>
    )
  }

  if (question.type === 'FILL_BLANK') {
    const values = answer?.type === 'FILL_BLANK' ? answer.values : {}
    return (
      <VStack gap="x4">
        {question.blanks.map((blank, index) => (
          <Field.Root key={blank.id}>
            <Field.Label>{index + 1}번</Field.Label>
            <TextField.Root>
              <TextField.Input
                value={values[blank.id] ?? ''}
                placeholder={`${index + 1}번 답을 입력해 주세요`}
                autoComplete="off"
                onChange={(event) =>
                  onAnswer({
                    type: 'FILL_BLANK',
                    values: { ...values, [blank.id]: event.currentTarget.value },
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
    const value = answer?.type === 'SHORT_ANSWER' ? answer.value : ''
    return (
      <Field.Root>
        <Field.Label>답</Field.Label>
        <TextField.Root>
          <TextField.Input
            value={value}
            placeholder="단어나 짧은 문장으로 입력해 주세요"
            autoComplete="off"
            onChange={(event) =>
              onAnswer({ type: 'SHORT_ANSWER', value: event.currentTarget.value })
            }
          />
        </TextField.Root>
      </Field.Root>
    )
  }

  const value = answer?.type === 'ESSAY' ? answer.value : ''
  return (
    <VStack gap="x4">
      <Field.Root>
        <Field.Label>내 답</Field.Label>
        <TextField.Root>
          <TextField.Textarea
            className="quiz-essay-input"
            value={value}
            placeholder="내 답을 작성해 주세요"
            onChange={(event) => onAnswer({ type: 'ESSAY', value: event.currentTarget.value })}
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
  const answeredCount = questions.filter((question) => isAnswered(question, answers[question.id])).length
  const firstUnanswered = questions.findIndex(
    (question) => !isAnswered(question, answers[question.id]),
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
          const answered = isAnswered(question, answers[question.id])
          return (
            <button
              key={question.id}
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
  error,
  onOpenChange,
  onReview,
  onSubmit,
}: {
  open: boolean
  unanswered: QuizQuestion[]
  submitting: boolean
  error?: string
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
      <VStack gap="x4" aria-live="polite">
        {error ? (
          <PageBanner.Root tone="critical" variant="weak">
            <PageBanner.Content>
              <PageBanner.Body>
                <PageBanner.Title>제출하지 못했어요</PageBanner.Title>
                <PageBanner.Description>{error}</PageBanner.Description>
              </PageBanner.Body>
            </PageBanner.Content>
          </PageBanner.Root>
        ) : null}
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

function ResultScreen({
  result,
  item,
  correctionAvailable,
  onBack,
  onOpenList,
  onCorrect,
}: {
  result: QuizResult
  item: QuizResultItem
  correctionAvailable: boolean
  onBack: () => void
  onOpenList: () => void
  onCorrect: () => void
}) {
  return (
    <VStack className="quiz-screen">
      <ScreenHeader
        title="채점 결과"
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
            <Text as="h2" id="quiz-result-question" textStyle="t8Bold" color="fg.neutral">
              {item.prompt}
            </Text>
          </VStack>
          <dl className="quiz-result-answer-list">
            <div>
              <dt>내 답</dt>
              <dd>{item.answer || '답하지 않음'}</dd>
            </div>
            <div>
              <dt>정답 예시</dt>
              <dd>{item.correctAnswer}</dd>
            </div>
            <div>
              <dt>판정</dt>
              <dd>{item.outcome === 'CORRECT' ? '정답' : '오답'}</dd>
            </div>
          </dl>

          {item.editable && correctionAvailable ? (
            <VStack gap="x2" align="flex-start">
              <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
                {item.edited
                  ? '현재 채점 결과를 다시 바꿀 수 있어요.'
                  : '현재 판정이 맞지 않다면 직접 바꿀 수 있어요.'}
              </Text>
              <ActionButton
                className="quiz-correction-action"
                size="medium"
                variant="brandOutline"
                onClick={onCorrect}
              >
                {item.edited ? '채점 다시 수정' : '채점 수정'}
              </ActionButton>
            </VStack>
          ) : item.type === 'MULTIPLE_CHOICE' ? (
            <Box className="quiz-notice" bg="bg.informativeWeak" borderRadius="r3" p="x4">
              <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
                객관식은 서버가 채점한 현재 결과를 그대로 보여줘요.
              </Text>
            </Box>
          ) : null}

          <ResultReadingSection title="해설">{item.explanation}</ResultReadingSection>
          <ResultReadingSection title="원문 근거">{item.sourceExcerpt}</ResultReadingSection>
        </VStack>
      </VStack>
    </VStack>
  )
}

function OutcomeLabel({ outcome }: { outcome: QuizResultOutcome }) {
  return (
    <Text
      className="quiz-outcome"
      data-outcome={outcome}
      textStyle="t4Bold"
      color={outcome === 'CORRECT' ? 'fg.positive' : 'fg.critical'}
    >
      {outcome === 'CORRECT' ? '정답' : '오답'}
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
          닫기
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
            <strong>{item.outcome === 'CORRECT' ? '정답' : '오답'}</strong>
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
  outcome: QuizResultOutcome
  saving: boolean
  error?: string
  onOpenChange: (open: boolean) => void
  onOutcomeChange: (outcome: QuizResultOutcome) => void
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
          onChange={(value) => onOutcomeChange(value as QuizResultOutcome)}
        />
        <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
          저장되면 선택한 판정만 현재 결과로 표시되고 점수와 복습할 문제 수가 함께 바뀌어요.
        </Text>
      </VStack>
    </SheetFrame>
  )
}
