import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import type { AuthReturnState } from '@/app/router/AuthGate'
import { useSignUpMutation } from '@/features/auth/model/auth.mutations'
import {
  AuthField,
  AuthForm,
  AuthPage,
  AuthTextLink,
  getFieldError,
} from '@/features/auth/ui/AuthForm'

export function SignUpPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const signUp = useSignUpMutation()
  const state = location.state as AuthReturnState | null
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (signUp.isPending) return

    signUp.mutate(
      { email, password },
      {
        onSuccess: () =>
          navigate('/verify-email', {
            state: { email, from: state?.from },
          }),
      },
    )
  }

  return (
    <AuthPage
      title="회원가입"
      description="인증 메일을 받은 뒤 6자리 코드를 확인해 주세요."
      footer={
        <AuthTextLink to="/login" state={{ from: state?.from }}>
          이미 계정이 있나요? 로그인
        </AuthTextLink>
      }
    >
      <AuthForm
        onSubmit={handleSubmit}
        submitLabel="인증 메일 받기"
        submitting={signUp.isPending}
        error={signUp.error}
      >
        <AuthField
          label="이메일"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={getFieldError(signUp.error, 'email')}
        />
        <AuthField
          label="비밀번호"
          description="8~64자, 영문자와 숫자를 포함하고 공백 없이 입력해 주세요."
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={64}
          pattern="(?=.*[A-Za-z])(?=.*\d)(?=\S{8,64}$).+"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={getFieldError(signUp.error, 'password')}
        />
      </AuthForm>
    </AuthPage>
  )
}
