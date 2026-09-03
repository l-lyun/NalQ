import {
  ActionButton,
  Box,
  Field,
  Text,
  TextField,
  VStack,
} from '@seed-design/react'
import type { ComponentProps, FormEvent, PropsWithChildren, ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { PublicServiceFooter } from '@/pages/public-service/PublicServiceFooter'

import { ApiClientError, getApiErrorMessage } from '@/shared/api/apiError'

import './auth-form.css'

type AuthPageProps = PropsWithChildren<{
  title: string
  description: string
  footer?: ReactNode
}>

export function AuthPage({ title, description, footer, children }: AuthPageProps) {
  return (
    <VStack className="auth-public-layout" bg="bg.layerDefault">
      <Box as="main" pt="safeArea">
        <VStack
          className="auth-page"
          px="spacingX.globalGutter"
          pt="x10"
          pb="spacingY.screenBottom"
          gap="x6"
        >
          <VStack as="header" gap="x2">
            <Text as="h1" textStyle="t12Bold" color="fg.neutral">
              {title}
            </Text>
            <Text textStyle="t5Regular" color="fg.neutralMuted">
              {description}
            </Text>
          </VStack>
          {children}
          {footer}
        </VStack>
      </Box>
      <PublicServiceFooter />
    </VStack>
  )
}

type AuthFieldProps = ComponentProps<typeof TextField.Input> & {
  label: string
  description?: string
  error?: string
}

export function AuthField({ label, description, error, ...inputProps }: AuthFieldProps) {
  return (
    <Field.Root invalid={Boolean(error)}>
      <Field.Label>{label}</Field.Label>
      <TextField.Root invalid={Boolean(error)}>
        <TextField.Input {...inputProps} />
      </TextField.Root>
      {description || error ? (
        <Field.Footer>
          {error ? (
            <Field.ErrorMessage>{error}</Field.ErrorMessage>
          ) : (
            <Field.Description>{description}</Field.Description>
          )}
        </Field.Footer>
      ) : null}
    </Field.Root>
  )
}

type AuthFormProps = PropsWithChildren<{
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  submitLabel: string
  submitting: boolean
  error?: unknown
  successMessage?: string
  secondaryAction?: ReactNode
}>

export function AuthForm({
  onSubmit,
  submitLabel,
  submitting,
  error,
  successMessage,
  secondaryAction,
  children,
}: AuthFormProps) {
  return (
    <form className="auth-form" onSubmit={onSubmit}>
      <VStack gap="x4">
        {children}
        {error ? (
          <Text role="alert" textStyle="t4Regular" color="fg.critical">
            {getApiErrorMessage(error, '요청을 처리하지 못했어요.')}
          </Text>
        ) : null}
        {successMessage ? (
          <Text role="status" textStyle="t4Regular" color="fg.positive">
            {successMessage}
          </Text>
        ) : null}
        <ActionButton type="submit" size="large" variant="brandSolid" loading={submitting} disabled={submitting}>
          {submitLabel}
        </ActionButton>
        {secondaryAction}
      </VStack>
    </form>
  )
}

export function AuthTextLink({ to, state, children }: PropsWithChildren<{ to: string; state?: unknown }>) {
  return (
    <Text textStyle="t4Regular" color="fg.neutralMuted">
      <Link className="auth-link" to={to} state={state}>
        {children}
      </Link>
    </Text>
  )
}

export function getFieldError(error: unknown, field: string) {
  if (!(error instanceof ApiClientError)) return undefined
  return error.fields.find((item) => item.field === field)?.reason
}
