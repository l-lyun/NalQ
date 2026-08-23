import { ActionButton } from '@seed-design/react'
import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { safeReturnPath } from '@/app/router/AuthGate'
import {
  useCompleteSessionMutation,
  useLoginMutation,
} from '@/features/auth/model/auth.mutations'
import {
  readLoginRouteState,
  SIGN_UP_SESSION_RECOVERY_NOTICE,
} from '@/features/auth/model/loginRouteState'
import {
  AuthField,
  AuthForm,
  AuthPage,
  AuthTextLink,
  getFieldError,
} from '@/features/auth/ui/AuthForm'
import { ApiClientError } from '@/shared/api/apiError'

export function LoginPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const login = useLoginMutation()
  const completeSession = useCompleteSessionMutation()
  const state = readLoginRouteState(location.state)
  const [email, setEmail] = useState(state.email ?? '')
  const [password, setPassword] = useState('')
  const canRetryCurrentUser =
    login.error instanceof ApiClientError &&
    (login.error.kind === 'network' || (login.error.status !== undefined && login.error.status >= 500))

  function navigateAfterLogin() {
    navigate(safeReturnPath(location.state), { replace: true })
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (login.isPending) return

    login.mutate(
      { email, password },
      {
        onSuccess: navigateAfterLogin,
      },
    )
  }

  return (
    <AuthPage
      title="로그인"
      description={
        state.notice === SIGN_UP_SESSION_RECOVERY_NOTICE
          ? '가입은 완료됐지만 로그인 처리에 실패했어요. 잠시 후 다시 로그인해 주세요.'
          : '테스트용 인증 화면입니다. 로그인하면 기존 홈 화면으로 이동해요.'
      }
      footer={
        <AuthTextLink to="/sign-up" state={{ from: state?.from }}>
          계정이 없나요? 회원가입
        </AuthTextLink>
      }
    >
      <AuthForm
        onSubmit={handleSubmit}
        submitLabel="로그인"
        submitting={login.isPending || completeSession.isPending}
        error={completeSession.error ?? login.error}
        secondaryAction={
          canRetryCurrentUser ? (
            <ActionButton
              type="button"
              size="medium"
              variant="neutralWeak"
              loading={completeSession.isPending}
              disabled={completeSession.isPending || login.isPending}
              onClick={() => completeSession.mutate(undefined, { onSuccess: navigateAfterLogin })}
            >
              사용자 정보 다시 불러오기
            </ActionButton>
          ) : undefined
        }
      >
        <AuthField
          label="이메일"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={getFieldError(login.error, 'email')}
        />
        <AuthField
          label="비밀번호"
          type="password"
          autoComplete="current-password"
          required
          maxLength={64}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={getFieldError(login.error, 'password')}
        />
      </AuthForm>
    </AuthPage>
  )
}
