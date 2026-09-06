import { IconChevronRightLine } from '@karrotmarket/react-monochrome-icon'
import {
  ActionButton,
  Box,
  ContentDialog,
  Field,
  Flex,
  Icon,
  ProgressCircle,
  Text,
  TextField,
  VStack,
} from '@seed-design/react'
import { type FormEvent, useEffect, useId, useRef } from 'react'

export type QuizManagementStatus = 'READY' | 'GENERATING' | 'FAILED'

export type QuizManagementCardProps = {
  quizId: string
  title: string
  materialTitle: string
  questionCount?: number | null
  status: QuizManagementStatus
  expanded: boolean
  disclosureDisabled?: boolean
  renameOpen: boolean
  renameDraft: string
  renameError?: string
  renameSaving: boolean
  renameMaxLength?: number
  onToggle: () => void
  onRenameOpenChange: (open: boolean) => void
  onRenameDraftChange: (value: string) => void
  onRenameSubmit: () => void
  onStartQuiz: () => void
}

export function QuizManagementCard({
  quizId,
  title,
  materialTitle,
  questionCount,
  status,
  expanded,
  disclosureDisabled = false,
  renameOpen,
  renameDraft,
  renameError,
  renameSaving,
  renameMaxLength = 255,
  onToggle,
  onRenameOpenChange,
  onRenameDraftChange,
  onRenameSubmit,
  onStartQuiz,
}: QuizManagementCardProps) {
  const generatedId = useId()
  const detailId = `${generatedId}-detail`
  const renameFormId = `${generatedId}-rename-form`
  const renameInputRef = useRef<HTMLInputElement>(null)
  const actionsDisabled = status !== 'READY' || renameSaving
  const questionSummary = status === 'READY' && questionCount != null
    ? `${questionCount.toLocaleString('ko-KR')}문제`
    : status === 'GENERATING'
      ? '문제 생성 중'
      : status === 'FAILED'
        ? '문제 생성 실패'
        : null

  useEffect(() => {
    if (!renameOpen) return

    const frame = requestAnimationFrame(() => {
      renameInputRef.current?.focus()
      renameInputRef.current?.select()
    })

    return () => cancelAnimationFrame(frame)
  }, [renameOpen])

  const handleRenameSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!renameSaving) onRenameSubmit()
  }

  return (
    <Box
      as="li"
      id={`quiz-card-${quizId}`}
      tabIndex={-1}
      className="learning-quiz-management-card"
      borderWidth={1}
      borderColor="stroke.neutralSubtle"
      borderRadius="r3"
    >
      <button
        className="learning-disclosure-trigger"
        type="button"
        aria-expanded={expanded}
        aria-controls={detailId}
        disabled={disclosureDisabled}
        onClick={onToggle}
      >
        <VStack minWidth="0px" gap="x1" align="flex-start">
          <Text className="learning-long-title" textStyle="t5Bold" color="fg.neutral">
            {title}
          </Text>
          <Text textStyle="t4Regular" color="fg.neutralMuted">
            {materialTitle}{questionSummary ? ` · ${questionSummary}` : ''}
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
        <Box
          id={detailId}
          className="learning-disclosure-detail learning-quiz-management-detail"
          aria-busy={status === 'GENERATING' || undefined}
        >
          <Flex className="learning-management-actions" gap="x2" width="full">
            <ContentDialog.Root
              open={renameOpen}
              closeOnEscape={!renameSaving}
              closeOnInteractOutside={!renameSaving}
              onOpenChange={onRenameOpenChange}
            >
              <ContentDialog.Trigger asChild>
                <ActionButton
                  type="button"
                  size="medium"
                  variant="neutralWeak"
                  disabled={actionsDisabled}
                >
                  퀴즈 이름 변경
                </ActionButton>
              </ContentDialog.Trigger>
              <ContentDialog.Backdrop />
              <ContentDialog.Positioner>
                <ContentDialog.Content className="learning-quiz-rename-dialog" width="full" maxWidth="480px">
                  <ContentDialog.Header>
                    <ContentDialog.Title>퀴즈 이름 변경</ContentDialog.Title>
                    <ContentDialog.Description>
                      새 퀴즈 이름을 입력해주세요.
                    </ContentDialog.Description>
                  </ContentDialog.Header>
                  <ContentDialog.Body>
                    <form id={renameFormId} onSubmit={handleRenameSubmit}>
                      <Field.Root invalid={Boolean(renameError)}>
                        <Field.Label>퀴즈 이름</Field.Label>
                        <TextField.Root invalid={Boolean(renameError)}>
                          <TextField.Input
                            ref={renameInputRef}
                            value={renameDraft}
                            disabled={renameSaving}
                            enterKeyHint="done"
                            aria-describedby={renameError ? `${generatedId}-rename-error` : undefined}
                            onChange={(event) => onRenameDraftChange(event.currentTarget.value)}
                          />
                        </TextField.Root>
                        <Field.Footer>
                          {renameError ? (
                            <Field.ErrorMessage id={`${generatedId}-rename-error`}>
                              {renameError}
                            </Field.ErrorMessage>
                          ) : (
                            <span />
                          )}
                          <Field.CharacterCount
                            current={Array.from(renameDraft).length}
                            max={renameMaxLength}
                          />
                        </Field.Footer>
                      </Field.Root>
                    </form>
                  </ContentDialog.Body>
                  <ContentDialog.Footer>
                    <ContentDialog.Action asChild>
                      <ActionButton type="button" size="large" variant="neutralWeak" disabled={renameSaving}>
                        취소
                      </ActionButton>
                    </ContentDialog.Action>
                    <ActionButton
                      form={renameFormId}
                      type="submit"
                      size="large"
                      variant="brandSolid"
                      disabled={renameSaving}
                    >
                      {renameSaving ? '저장하는 중...' : '저장'}
                    </ActionButton>
                  </ContentDialog.Footer>
                </ContentDialog.Content>
              </ContentDialog.Positioner>
            </ContentDialog.Root>

            <ActionButton
              type="button"
              size="medium"
              variant="brandSolid"
              disabled={actionsDisabled}
              onClick={onStartQuiz}
            >
              퀴즈 풀기
            </ActionButton>
          </Flex>

          {status !== 'READY' ? (
            <VStack
              className="learning-quiz-status-overlay"
              gap="x2"
              align="center"
              justify="center"
              role="status"
            >
              {status === 'GENERATING' ? (
                <ProgressCircle.Root aria-label="문제 생성 중" tone="neutral" size="24">
                  <ProgressCircle.Track />
                  <ProgressCircle.Range />
                </ProgressCircle.Root>
              ) : (
                <Text
                  as="span"
                  className="learning-quiz-failure-indicator"
                  textStyle="t4Bold"
                  color="fg.critical"
                  aria-hidden="true"
                >
                  !
                </Text>
              )}
              <Text as="span" textStyle="t4Medium" color={status === 'FAILED' ? 'fg.critical' : 'fg.neutral'}>
                {status === 'GENERATING' ? '문제 생성 중' : '문제 생성 실패'}
              </Text>
            </VStack>
          ) : null}
        </Box>
      ) : null}
    </Box>
  )
}
