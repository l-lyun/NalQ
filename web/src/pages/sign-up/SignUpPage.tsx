import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import type { AuthReturnState } from '@/app/router/AuthGate'
import {
  useCompleteSignUpMutation,
  useConfirmEmailMutation,
  useNicknameAvailabilityMutation,
  useRecoverCompletedSignUpMutation,
  useResendVerificationMutation,
  useRequestVerificationEmailMutation,
} from '@/features/auth/model/auth.mutations'
import { SIGN_UP_SESSION_RECOVERY_NOTICE } from '@/features/auth/model/loginRouteState'
import { ApiClientError, getApiErrorMessage } from '@/shared/api/apiError'

import {
  SignUpView,
  type EmailVerificationStatus,
  type NicknameCheckStatus,
} from './SignUpView'
import { signUpTerms, type SignUpTermId } from './termsContent'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)\S{8,64}$/
const VERIFICATION_CODE_PATTERN = /^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$/
const NICKNAME_PATTERN = /^[가-힣A-Za-z0-9]{2,10}$/u
const VERIFICATION_LIFETIME_MS = 10 * 60 * 1000
const RESEND_COOLDOWN_MS = 60 * 1000

function formatRemainingTime(milliseconds: number) {
  const totalSeconds = Math.max(0, Math.ceil(milliseconds / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

function fieldError(error: unknown, field: string) {
  if (!(error instanceof ApiClientError)) return undefined
  return error.fields.find((item) => item.field === field)?.reason
}

function normalizeNickname(value: string) {
  return value.normalize('NFC')
}

function isSameNickname(left: string, right: string) {
  return (
    normalizeNickname(left).toLocaleLowerCase('en-US') ===
    normalizeNickname(right).toLocaleLowerCase('en-US')
  )
}

type SignUpRouteState = AuthReturnState & { signUpStep?: 1 | 2 }

export function SignUpPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const routeState = location.state as SignUpRouteState | null

  const requestVerification = useRequestVerificationEmailMutation()
  const resendVerification = useResendVerificationMutation()
  const confirmVerification = useConfirmEmailMutation()
  const checkNickname = useNicknameAvailabilityMutation()
  const completeSignUp = useCompleteSignUpMutation()
  const recoverCompletedSignUp = useRecoverCompletedSignUpMutation()

  const [profileStepUnlocked, setProfileStepUnlocked] = useState(false)
  const [email, setEmail] = useState('')
  const [verificationCode, setVerificationCode] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirmation, setPasswordConfirmation] = useState('')
  const [passwordVisible, setPasswordVisible] = useState(false)
  const [passwordConfirmationVisible, setPasswordConfirmationVisible] = useState(false)
  const [verificationRequested, setVerificationRequested] = useState(false)
  const [verificationStatus, setVerificationStatus] = useState<EmailVerificationStatus>('idle')
  const [verificationExpiresAt, setVerificationExpiresAt] = useState<number | null>(null)
  const [resendAvailableAt, setResendAvailableAt] = useState<number | null>(null)
  const [signUpToken, setSignUpToken] = useState<string | null>(null)
  const [now, setNow] = useState(() => Date.now())

  const [nickname, setNickname] = useState('')
  const [nicknameCheckStatus, setNicknameCheckStatus] =
    useState<NicknameCheckStatus>('unchecked')
  const [serviceTermsAccepted, setServiceTermsAccepted] = useState(false)
  const [privacyTermsAccepted, setPrivacyTermsAccepted] = useState(false)
  const [activeTermId, setActiveTermId] = useState<SignUpTermId | null>(null)
  const [profileSubmitError, setProfileSubmitError] = useState<string>()

  useEffect(() => {
    if (!verificationRequested || verificationStatus === 'verified') return

    const timer = window.setInterval(() => setNow(Date.now()), 1_000)
    return () => window.clearInterval(timer)
  }, [verificationRequested, verificationStatus])

  const normalizedEmail = email.trim()
  const emailValid = EMAIL_PATTERN.test(normalizedEmail)
  const passwordValid = PASSWORD_PATTERN.test(password)
  const passwordsMatch = passwordConfirmation.length > 0 && password === passwordConfirmation
  const normalizedCode = verificationCode.trim().toUpperCase()
  const codeValid = VERIFICATION_CODE_PATTERN.test(normalizedCode)
  const verificationExpired = verificationExpiresAt !== null && now >= verificationExpiresAt
  const resendCoolingDown = resendAvailableAt !== null && now < resendAvailableAt
  const normalizedNickname = normalizeNickname(nickname)
  const nicknameValid = NICKNAME_PATTERN.test(normalizedNickname)

  const requestPending = requestVerification.isPending || resendVerification.isPending
  const canSendVerificationCode =
    emailValid && passwordValid && passwordsMatch && !requestPending && !confirmVerification.isPending
  const canVerifyCode =
    verificationRequested &&
    codeValid &&
    !verificationExpired &&
    !confirmVerification.isPending &&
    !requestPending
  const canContinue = verificationStatus === 'verified' && passwordValid && passwordsMatch
  const canCheckNickname = nicknameValid && nicknameCheckStatus !== 'checking'
  const canComplete =
    nicknameValid &&
    nicknameCheckStatus === 'available' &&
    serviceTermsAccepted &&
    privacyTermsAccepted
  const step: 1 | 2 =
    profileStepUnlocked && routeState?.signUpStep === 2 ? 2 : 1

  function openProfileStep() {
    setProfileStepUnlocked(true)
    navigate('/sign-up', {
      state: { from: routeState?.from, signUpStep: 2 } satisfies SignUpRouteState,
    })
  }

  function resetVerificationState() {
    setVerificationCode('')
    setVerificationRequested(false)
    setVerificationStatus('idle')
    setVerificationExpiresAt(null)
    setResendAvailableAt(null)
    setSignUpToken(null)
    requestVerification.reset()
    resendVerification.reset()
    confirmVerification.reset()
  }

  function handleEmailChange(value: string) {
    if (value !== email) resetVerificationState()
    setEmail(value)
  }

  function applyVerificationRequested(resendAt?: string) {
    const requestedAt = Date.now()
    const parsedResendAt = resendAt ? Date.parse(resendAt) : Number.NaN
    setNow(requestedAt)
    setVerificationRequested(true)
    setVerificationStatus('sent')
    setVerificationExpiresAt(requestedAt + VERIFICATION_LIFETIME_MS)
    setResendAvailableAt(
      Number.isFinite(parsedResendAt) ? parsedResendAt : requestedAt + RESEND_COOLDOWN_MS,
    )
  }

  function handleSendVerificationCode() {
    if (!canSendVerificationCode) return

    setVerificationStatus('sending')
    requestVerification.mutate(
      { email: normalizedEmail },
      {
        onSuccess: (response) => applyVerificationRequested(response.resendAvailableAt),
        onError: () => setVerificationStatus('error'),
      },
    )
  }

  function handleResendVerificationCode() {
    if (!verificationRequested || resendCoolingDown || requestPending) return

    setVerificationStatus('sending')
    resendVerification.mutate(normalizedEmail, {
      onSuccess: (response) => {
        setVerificationCode('')
        setSignUpToken(null)
        confirmVerification.reset()
        applyVerificationRequested(response.resendAvailableAt)
      },
      onError: () => setVerificationStatus('error'),
    })
  }

  function handleVerificationCodeChange(value: string) {
    setVerificationCode(value.toUpperCase())
    if (verificationStatus === 'error') {
      setVerificationStatus('sent')
      confirmVerification.reset()
    }
  }

  function handleVerifyCode() {
    if (!canVerifyCode) return

    setVerificationStatus('verifying')
    confirmVerification.mutate(
      { email: normalizedEmail, code: normalizedCode },
      {
        onSuccess: (response) => {
          if (!response.emailVerified) {
            setVerificationStatus('error')
            return
          }

          if (response.nextAction === 'LOGIN') {
            navigate('/login', {
              replace: true,
              state: { email: normalizedEmail, from: routeState?.from },
            })
            return
          }

          setSignUpToken(response.signUpToken)
          setVerificationStatus('verified')
        },
        onError: () => setVerificationStatus('error'),
      },
    )
  }

  function handleContinue() {
    if (canContinue) openProfileStep()
  }

  function handleNicknameChange(value: string) {
    const nextNickname = normalizeNickname(value)
    setNickname(nextNickname)
    setNicknameCheckStatus('unchecked')
    setProfileSubmitError(undefined)
    checkNickname.reset()
    completeSignUp.reset()
    recoverCompletedSignUp.reset()
  }

  function handleCheckNickname() {
    if (!canCheckNickname) return

    const checkedValue = normalizedNickname
    setNicknameCheckStatus('checking')
    setProfileSubmitError(undefined)
    checkNickname.mutate(
      { nickname: checkedValue },
      {
        onSuccess: (response) => {
          if (!isSameNickname(response.checkedNickname, checkedValue)) {
            setNicknameCheckStatus('error')
            return
          }
          setNicknameCheckStatus(response.available ? 'available' : 'unavailable')
        },
        onError: () => setNicknameCheckStatus('error'),
      },
    )
  }

  function navigateToSignUpRecoveryLogin() {
    setPassword('')
    setPasswordConfirmation('')
    setSignUpToken(null)
    completeSignUp.reset()
    navigate('/login', {
      replace: true,
      state: {
        email: normalizedEmail,
        from: routeState?.from,
        notice: SIGN_UP_SESSION_RECOVERY_NOTICE,
      },
    })
  }

  function handleComplete() {
    if (!canComplete || completeSignUp.isPending || recoverCompletedSignUp.isPending) return
    if (!signUpToken) {
      setProfileSubmitError(
        '현재 서버는 가입정보 설정을 아직 지원하지 않아요. 서버 업데이트 후 다시 시도해 주세요.',
      )
      return
    }

    setProfileSubmitError(undefined)
    completeSignUp.mutate(
      {
        signUpToken,
        password,
        nickname: normalizedNickname,
        agreements: [
          { termsId: 'SERVICE_TERMS', version: signUpTerms.service.version },
          { termsId: 'PRIVACY_COLLECTION', version: signUpTerms.privacy.version },
        ],
      },
      {
        onError: (error) => {
          if (error instanceof ApiClientError && error.code === 'AUTH_010') {
            setNicknameCheckStatus('unavailable')
            setProfileSubmitError('그사이 닉네임이 사용되었어요. 다른 닉네임을 확인해 주세요.')
            return
          }

          if (error instanceof ApiClientError && error.code === 'AUTH_011') {
            navigateToSignUpRecoveryLogin()
            return
          }

          const resultIsUnclear =
            error instanceof ApiClientError &&
            (error.kind === 'network' ||
              (error.code === undefined && error.status !== undefined && error.status >= 500))

          if (resultIsUnclear) {
            recoverCompletedSignUp.mutate(undefined, { onError: navigateToSignUpRecoveryLogin })
            return
          }

          setProfileSubmitError(
            getApiErrorMessage(error, '가입을 완료하지 못했어요. 다시 시도해 주세요.'),
          )
        },
      },
    )
  }

  const passwordError =
    password.length > 0 && !passwordValid
      ? '8~64자, 영문자와 숫자를 포함하고 공백 없이 입력해 주세요.'
      : fieldError(requestVerification.error, 'password')
  const passwordConfirmationError =
    passwordConfirmation.length > 0 && !passwordsMatch ? '비밀번호가 일치하지 않아요.' : undefined
  const emailError =
    email.length > 0 && !emailValid
      ? '올바른 이메일 주소를 입력해 주세요.'
      : fieldError(requestVerification.error ?? resendVerification.error, 'email')
  const verificationCodeError = fieldError(confirmVerification.error, 'code')
  const verificationMessage =
    verificationStatus === 'verified'
      ? '이메일 인증을 완료했어요.'
      : verificationExpired
        ? '인증 코드 유효시간이 끝났어요. 새 코드를 받아 주세요.'
        : verificationStatus === 'error'
          ? getApiErrorMessage(
              confirmVerification.error ?? resendVerification.error,
              '인증 요청을 처리하지 못했어요. 다시 시도해 주세요.',
            )
          : verificationRequested
            ? '이메일로 받은 6자리 코드를 입력해 주세요.'
            : undefined
  const nicknameError =
    nickname.length > 0 && !nicknameValid
      ? '한글, 영문, 숫자만 사용해 공백 없이 2~10자로 입력해 주세요.'
      : fieldError(checkNickname.error ?? completeSignUp.error, 'nickname')
  const firstRequestError =
    !requestPending && !verificationRequested && requestVerification.error
      ? getApiErrorMessage(
          requestVerification.error,
          '인증 메일을 보내지 못했어요. 다시 시도해 주세요.',
        )
      : undefined

  return (
    <SignUpView
      step={step}
      account={{
        email,
        verificationCode,
        password,
        passwordConfirmation,
        emailVerificationStatus: requestPending ? 'sending' : verificationStatus,
        verificationCodeRequested: verificationRequested,
        verificationMessage,
        remainingTimeLabel:
          verificationExpiresAt === null
            ? undefined
            : formatRemainingTime(verificationExpiresAt - now),
        resendLabel: resendCoolingDown
          ? `재발송 (${Math.max(1, Math.ceil(((resendAvailableAt ?? now) - now) / 1000))}초)`
          : '재발송',
        emailMessage: {
          description: verificationRequested
            ? '이메일을 변경하려면 로그인 화면으로 돌아가 다시 시작해 주세요.'
            : '비밀번호와 비밀번호 확인까지 입력하면 인증 코드를 받을 수 있어요.',
          error: emailError,
        },
        verificationCodeMessage: { error: verificationCodeError },
        passwordMessage: { error: passwordError },
        passwordConfirmationMessage: { error: passwordConfirmationError },
        passwordVisible,
        passwordConfirmationVisible,
        canSendVerificationCode,
        canVerifyCode,
        canResendVerificationCode: verificationRequested && !resendCoolingDown && !requestPending,
        canContinue,
        isSubmitting: false,
        submitError: firstRequestError,
        onEmailChange: handleEmailChange,
        onVerificationCodeChange: handleVerificationCodeChange,
        onPasswordChange: setPassword,
        onPasswordConfirmationChange: setPasswordConfirmation,
        onSendVerificationCode: handleSendVerificationCode,
        onVerifyCode: handleVerifyCode,
        onResendVerificationCode: handleResendVerificationCode,
        onTogglePasswordVisibility: () => setPasswordVisible((visible) => !visible),
        onTogglePasswordConfirmationVisibility: () =>
          setPasswordConfirmationVisible((visible) => !visible),
        onContinue: handleContinue,
      }}
      profile={{
        nickname,
        nicknameCheckStatus,
        nicknameMessage: { error: nicknameError },
        serviceTermsAccepted,
        privacyTermsAccepted,
        activeTermId,
        canCheckNickname,
        canComplete,
        isSubmitting: completeSignUp.isPending || recoverCompletedSignUp.isPending,
        submitError: profileSubmitError,
        onNicknameChange: handleNicknameChange,
        onCheckNickname: handleCheckNickname,
        onServiceTermsAcceptedChange: setServiceTermsAccepted,
        onPrivacyTermsAcceptedChange: setPrivacyTermsAccepted,
        onAllTermsAcceptedChange: (checked) => {
          setServiceTermsAccepted(checked)
          setPrivacyTermsAccepted(checked)
        },
        onOpenTerm: setActiveTermId,
        onCloseTerm: () => setActiveTermId(null),
        onComplete: handleComplete,
      }}
      onBack={() => {
        if (step === 2) {
          navigate(-1)
          return
        }
        navigate('/login', { state: { from: routeState?.from } })
      }}
      onLogin={() => navigate('/login', { state: { from: routeState?.from } })}
    />
  )
}
