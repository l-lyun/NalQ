import { IconArrowLeftLine, IconChevronRightLine } from '@karrotmarket/react-monochrome-icon'
import { ActionButton, Divider, Field, Flex, Icon, List, Text, TextField, VStack } from '@seed-design/react'
import type { ComponentProps, ReactNode } from 'react'
import { Fragment } from 'react'

export type LearningListRow = {
  id: string
  title: string
  detail: ReactNode
  actionLabel?: string
  disabled?: boolean
  onClick: () => void
}

export function LearningScreenHeader({
  title,
  onBack,
  headingRef,
}: {
  title: string
  onBack: () => void
  headingRef?: ComponentProps<typeof Text>['ref']
}) {
  return (
    <Flex as="header" align="center" gap="x2">
      <ActionButton
        className="learning-back-button"
        type="button"
        size="small"
        variant="ghost"
        layout="iconOnly"
        aria-label="이전 화면으로 돌아가기"
        onClick={onBack}
      >
        <Icon svg={<IconArrowLeftLine />} size="x5" />
      </ActionButton>
      <Text
        as="h1"
        className="learning-focus-heading"
        ref={headingRef}
        tabIndex={-1}
        textStyle="t10Bold"
        color="fg.neutral"
      >
        {title}
      </Text>
    </Flex>
  )
}

export function LearningSectionTitle({ id, children }: { id: string; children: ReactNode }) {
  return (
    <Text as="h2" id={id} textStyle="t10Bold" color="fg.neutral">
      {children}
    </Text>
  )
}

export function LearningActionList({
  label,
  rows,
  outlined = false,
}: {
  label: string
  rows: LearningListRow[]
  outlined?: boolean
}) {
  return (
    <List.Root
      className="learning-list"
      aria-label={label}
      width="full"
      borderWidth={outlined ? 1 : 0}
      borderColor="stroke.neutralSubtle"
      borderRadius={outlined ? 'r3' : undefined}
      itemBorderRadius="r2_5"
    >
      {rows.map((row, index) => (
        <Fragment key={row.id}>
          <List.Item alignItems="flex-start">
            <List.Content asChild gap="x3">
              <button
                className="learning-list-button"
                type="button"
                disabled={row.disabled}
                onClick={row.onClick}
              >
                <VStack minWidth="0px" flexGrow gap="x1_5" align="flex-start">
                  <List.Title data-disabled={row.disabled ? '' : undefined}>{row.title}</List.Title>
                  <List.Detail data-disabled={row.disabled ? '' : undefined}>
                    {row.detail}
                  </List.Detail>
                </VStack>
                <List.Suffix data-disabled={row.disabled ? '' : undefined}>
                  {row.actionLabel ? (
                    <Text textStyle="t4Medium" color="fg.neutralMuted">
                      {row.actionLabel}
                    </Text>
                  ) : null}
                  <Icon svg={<IconChevronRightLine />} size="x4_5" />
                </List.Suffix>
              </button>
            </List.Content>
          </List.Item>
          {index < rows.length - 1 ? (
            <Divider as="li" aria-hidden color="stroke.neutralSubtle" inset />
          ) : null}
        </Fragment>
      ))}
    </List.Root>
  )
}

type LearningFieldProps = {
  label: string
  error?: string
  description?: string
  characterCount?: { current: number; max: number }
  children: ReactNode
}

export function LearningField({
  label,
  error,
  description,
  characterCount,
  children,
}: LearningFieldProps) {
  return (
    <Field.Root invalid={Boolean(error)}>
      <Field.Label>{label}</Field.Label>
      {children}
      {description || error || characterCount ? (
        <Field.Footer>
          {error ? (
            <Field.ErrorMessage>{error}</Field.ErrorMessage>
          ) : description ? (
            <Field.Description>{description}</Field.Description>
          ) : (
            <span />
          )}
          {characterCount ? (
            <Field.CharacterCount current={characterCount.current} max={characterCount.max} />
          ) : null}
        </Field.Footer>
      ) : null}
    </Field.Root>
  )
}

export function LearningTextInput(
  props: ComponentProps<typeof TextField.Input> & { invalid?: boolean },
) {
  const { invalid, ...inputProps } = props
  return (
    <TextField.Root invalid={invalid}>
      <TextField.Input {...inputProps} />
    </TextField.Root>
  )
}

export function LearningTextarea(
  props: ComponentProps<typeof TextField.Textarea> & { invalid?: boolean },
) {
  const { invalid, ...textareaProps } = props
  return (
    <TextField.Root invalid={invalid}>
      <TextField.Textarea className="learning-textarea" {...textareaProps} />
    </TextField.Root>
  )
}

export function LearningNotice({ children }: { children: ReactNode }) {
  return (
    <VStack className="learning-notice" bg="bg.neutralWeak" borderRadius="r3" p="x4" gap="x1">
      <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
        {children}
      </Text>
    </VStack>
  )
}
