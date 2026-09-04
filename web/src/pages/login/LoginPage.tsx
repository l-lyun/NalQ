import { ActionButton } from '@seed-design/react'
import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { safeReturnPath } from '@/app/router/AuthGate'
import {
  useCompleteSessionMutation,
  useLoginMutation,
} from '@/features/auth/model/auth.mutations'
import {
  ACCOUNT_WITHDRAWAL_COMPLETED_NOTICE,
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
        state.notice === ACCOUNT_WITHDRAWAL_COMPLETED_NOTICE
          ? '회원탈퇴가 완료됐어요. 이전 학습 기록은 복구되지 않으며 같은 이메일로 새로 가입할 수 있어요.'
          : state.notice === SIGN_UP_SESSION_RECOVERY_NOTICE
          ? '가입은 완료됐지만 로그인 처리에 실패했어요. 잠시 후 다시 로그인해 주세요.'
          : '로그인하고 학습자료와 문제 풀이를 이어가세요.'
      }
      footer={
        import.meta.env.DEV ? (
          <AuthTextLink to="/sign-up" state={{ from: state?.from }}>
            계정이 없나요? 회원가입
          </AuthTextLink>
        ) : undefined
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
