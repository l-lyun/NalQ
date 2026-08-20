import { ActionButton } from '@seed-design/react'
import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import type { AuthReturnState } from '@/app/router/AuthGate'
import {
  useConfirmEmailMutation,
  useResendVerificationMutation,
} from '@/features/auth/model/auth.mutations'
import {
  AuthField,
  AuthForm,
  AuthPage,
  AuthTextLink,
  getFieldError,
} from '@/features/auth/ui/AuthForm'
type VerificationRouteState = AuthReturnState & { email?: string }

export function VerifyEmailPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const state = location.state as VerificationRouteState | null
  const confirm = useConfirmEmailMutation()
  const resend = useResendVerificationMutation()
  const [email, setEmail] = useState(state?.email ?? '')
  const [code, setCode] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (confirm.isPending) return

    confirm.mutate(
      { email, code: code.trim().toUpperCase() },
      {
        onSuccess: () =>
          navigate('/login', {
            replace: true,
            state: { email, from: state?.from },
          }),
      },
    )
  }

  function handleResend() {
    if (!email || resend.isPending) return
    resend.mutate(email)
  }

  return (
    <AuthPage
      title="이메일 인증"
      description="메일로 받은 6자리 대문자 영문·숫자 코드를 입력해 주세요."
      footer={
        <AuthTextLink to="/login" state={{ email, from: state?.from }}>
          로그인으로 돌아가기
        </AuthTextLink>
      }
    >
      <AuthForm
        onSubmit={handleSubmit}
        submitLabel="인증하고 로그인으로"
        submitting={confirm.isPending}
        error={confirm.error ?? resend.error}
        successMessage={resend.isSuccess ? '인증 메일을 다시 요청했어요.' : undefined}
        secondaryAction={
          <ActionButton
            type="button"
            size="medium"
            variant="neutralWeak"
            loading={resend.isPending}
            disabled={!email || resend.isPending || confirm.isPending}
            onClick={handleResend}
          >
            인증 메일 다시 받기
          </ActionButton>
        }
      >
        <AuthField
          label="이메일"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={getFieldError(confirm.error ?? resend.error, 'email')}
        />
        <AuthField
          label="인증 코드"
          type="text"
          autoComplete="one-time-code"
          required
          minLength={6}
          maxLength={6}
          pattern="[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}"
          value={code}
          onChange={(event) => setCode(event.target.value.toUpperCase())}
          error={getFieldError(confirm.error, 'code')}
        />
      </AuthForm>
    </AuthPage>
  )
}
