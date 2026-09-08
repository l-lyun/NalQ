import { IconArrowLeftLine } from '@karrotmarket/react-monochrome-icon'
import {
  ActionButton,
  Box,
  Checkbox,
  ContentDialog,
  Field,
  Flex,
  Icon,
  ProgressCircle,
  Text,
  TextField,
  VStack,
} from '@seed-design/react'
import type { FormEvent, ReactNode } from 'react'

import { signUpTerms, type SignUpTermId } from './termsContent'
import { PublicServiceFooter } from '@/pages/public-service/PublicServiceFooter'

import './sign-up-page.css'

export type EmailVerificationStatus =
  | 'idle'
  | 'sending'
  | 'sent'
  | 'verifying'
  | 'verified'
  | 'error'

export type NicknameCheckStatus =
  | 'unchecked'
  | 'checking'
  | 'available'
  | 'unavailable'
  | 'error'

type InputMessage = {
  description?: string
  error?: string
}

export type SignUpAccountStepProps = {
  email: string
  verificationCode: string
  password: string
  passwordConfirmation: string
  emailVerificationStatus: EmailVerificationStatus
  verificationCodeRequested: boolean
  verificationMessage?: string
  remainingTimeLabel?: string
  resendLabel?: string
  emailMessage?: InputMessage
  verificationCodeMessage?: InputMessage
  passwordMessage?: InputMessage
  passwordConfirmationMessage?: InputMessage
  passwordVisible: boolean
  passwordConfirmationVisible: boolean
  canSendVerificationCode: boolean
  canVerifyCode: boolean
  canResendVerificationCode: boolean
  canContinue: boolean
  isSubmitting?: boolean
  submitError?: string
  onEmailChange: (value: string) => void
  onVerificationCodeChange: (value: string) => void
  onPasswordChange: (value: string) => void
  onPasswordConfirmationChange: (value: string) => void
  onSendVerificationCode: () => void
  onVerifyCode: () => void
  onResendVerificationCode: () => void
  onTogglePasswordVisibility: () => void
  onTogglePasswordConfirmationVisibility: () => void
  onContinue: () => void
}

export type SignUpProfileStepProps = {
  nickname: string
  nicknameCheckStatus: NicknameCheckStatus
  nicknameMessage?: InputMessage
  serviceTermsAccepted: boolean
  privacyTermsAccepted: boolean
  activeTermId: SignUpTermId | null
  canCheckNickname: boolean
  canComplete: boolean
  isSubmitting?: boolean
  submitError?: string
  onNicknameChange: (value: string) => void
  onCheckNickname: () => void
  onServiceTermsAcceptedChange: (checked: boolean) => void
  onPrivacyTermsAcceptedChange: (checked: boolean) => void
  onAllTermsAcceptedChange: (checked: boolean) => void
  onOpenTerm: (termId: SignUpTermId) => void
  onCloseTerm: () => void
  onComplete: () => void
}

export type SignUpViewProps = {
  step: 1 | 2
  account: SignUpAccountStepProps
  profile: SignUpProfileStepProps
  onBack: () => void
  onLogin: () => void
}

type SignUpShellProps = {
  step: 1 | 2
  title: string
  description: string
  onBack: () => void
  children: ReactNode
  footer?: ReactNode
}

function SignUpShell({
  step,
  title,
  description,
  onBack,
  children,
  footer,
}: SignUpShellProps) {
  return (
    <VStack className="sign-up-public-layout" bg="bg.layerDefault">
      <Box as="main" pt="safeArea">
        <VStack
          className="sign-up-page"
          px="spacingX.globalGutter"
          pt="x4"
          pb="spacingY.screenBottom"
          gap="x4"
        >
        <ActionButton
          className="sign-up-back-button"
          type="button"
          size="small"
          variant="ghost"
          layout="iconOnly"
          aria-label="이전"
          onClick={onBack}
        >
          <Icon svg={<IconArrowLeftLine />} size="x5" />
        </ActionButton>

        <VStack as="header" gap="x3">
          <VStack gap="x1_5">
            <Text textStyle="t4Medium" color="fg.brand" aria-label={`${step}/2 단계`}>
              {step}/2 · {step === 1 ? '계정 확인' : '가입정보 설정'}
            </Text>
            <div className="sign-up-progress" aria-hidden="true">
              <span className="sign-up-progress__value" data-step={step} />
            </div>
          </VStack>
          <VStack gap="x2">
            <Text as="h1" textStyle="t9Bold" color="fg.neutral">
              {title}
            </Text>
            <Text textStyle="t5Regular" color="fg.neutralMuted">
              {description}
            </Text>
          </VStack>
        </VStack>

          {children}
          {footer}
        </VStack>
      </Box>
      <PublicServiceFooter preserveContext />
    </VStack>
  )
}

type SignUpFieldProps = InputMessage & {
  label: string
  children: ReactNode
  required?: boolean
}

function SignUpField({ label, description, error, children, required }: SignUpFieldProps) {
  return (
    <Field.Root invalid={Boolean(error)} required={required}>
      <Field.Label>
        {label}
        {required ? <Field.RequiredIndicator aria-label="필수" /> : null}
      </Field.Label>
      {children}
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

function StatusMessage({
  tone,
  children,
}: {
  tone: 'positive' | 'critical' | 'neutral'
  children?: ReactNode
}) {
  if (!children) return null

  const color =
    tone === 'positive' ? 'fg.positive' : tone === 'critical' ? 'fg.critical' : 'fg.neutralMuted'

  return (
    <Text
      role={tone === 'critical' ? 'alert' : 'status'}
      aria-live={tone === 'critical' ? 'assertive' : 'polite'}
      textStyle="t4Regular"
      color={color}
    >
      {children}
    </Text>
  )
}

function PasswordField({
  label,
  value,
  visible,
  autoComplete,
  message,
  onChange,
  onToggleVisibility,
}: {
  label: string
  value: string
  visible: boolean
  autoComplete: 'new-password'
  message?: InputMessage
  onChange: (value: string) => void
  onToggleVisibility: () => void
}) {
  return (
    <SignUpField label={label} required {...message}>
      <Flex gap="x2" align="center">
        <TextField.Root className="sign-up-text-field" invalid={Boolean(message?.error)}>
          <TextField.Input
            type={visible ? 'text' : 'password'}
            autoComplete={autoComplete}
            required
            minLength={8}
            maxLength={64}
            value={value}
            onChange={(event) => onChange(event.currentTarget.value)}
          />
        </TextField.Root>
        <ActionButton
          type="button"
          size="small"
          variant="neutralWeak"
          aria-pressed={visible}
          aria-label={`${label} ${visible ? '숨기기' : '보기'}`}
          onClick={onToggleVisibility}
        >
          {visible ? '숨기기' : '보기'}
        </ActionButton>
      </Flex>
    </SignUpField>
  )
}

export function SignUpAccountStep(props: SignUpAccountStepProps) {
  const codeWasSent = props.verificationCodeRequested
  const emailVerified = props.emailVerificationStatus === 'verified'

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    props.onContinue()
  }

  return (
    <>
      <form className="sign-up-form" onSubmit={handleSubmit} noValidate>
        <VStack gap="x5">
        <SignUpField label="이메일" required {...props.emailMessage}>
          <Flex className="sign-up-field-action" gap="x2" align="center">
            <TextField.Root className="sign-up-text-field" invalid={Boolean(props.emailMessage?.error)}>
              <TextField.Input
                type="email"
                inputMode="email"
                autoComplete="email"
                required
                readOnly={codeWasSent}
                value={props.email}
                onChange={(event) => props.onEmailChange(event.currentTarget.value)}
              />
            </TextField.Root>
            {!codeWasSent ? (
              <ActionButton
                type="button"
                size="small"
                variant="neutralWeak"
                loading={props.emailVerificationStatus === 'sending'}
                disabled={!props.canSendVerificationCode}
                onClick={props.onSendVerificationCode}
              >
                인증 코드 받기
              </ActionButton>
            ) : null}
          </Flex>
        </SignUpField>

        {codeWasSent ? (
          <SignUpField label="인증 코드" required {...props.verificationCodeMessage}>
            <VStack gap="x2">
              <Flex className="sign-up-field-action" gap="x2" align="center">
                <TextField.Root
                  className="sign-up-text-field"
                  invalid={Boolean(props.verificationCodeMessage?.error)}
                >
                  <TextField.Input
                    type="text"
                    inputMode="text"
                    autoComplete="one-time-code"
                    autoCapitalize="characters"
                    spellCheck={false}
                    required
                    readOnly={emailVerified}
                    maxLength={6}
                    pattern="[A-HJ-NP-Z2-9]{6}"
                    value={props.verificationCode}
                    onChange={(event) => props.onVerificationCodeChange(event.currentTarget.value)}
                  />
                </TextField.Root>
                <ActionButton
                  type="button"
                  size="small"
                  variant="neutralWeak"
                  loading={props.emailVerificationStatus === 'verifying'}
                  disabled={!props.canVerifyCode || emailVerified}
                  onClick={props.onVerifyCode}
                >
                  {emailVerified ? '인증 완료' : '확인'}
                </ActionButton>
              </Flex>
              <Flex justify="space-between" align="center" gap="x2" wrap>
                <StatusMessage
                  tone={
                    emailVerified
                      ? 'positive'
                      : props.emailVerificationStatus === 'error'
                        ? 'critical'
                        : 'neutral'
                  }
                >
                  {props.verificationMessage}
                </StatusMessage>
                {!emailVerified ? (
                  <ActionButton
                    type="button"
                    size="small"
                    variant="ghost"
                    color="fg.neutralMuted"
                    fontWeight="medium"
                    bleed="asPadding"
                    disabled={!props.canResendVerificationCode || props.emailVerificationStatus === 'sending'}
                    onClick={props.onResendVerificationCode}
                  >
                    {props.resendLabel ?? '재발송'}
                  </ActionButton>
                ) : null}
              </Flex>
              {props.remainingTimeLabel && !emailVerified ? (
                <Text textStyle="t3Regular" color="fg.neutralSubtle">
                  유효시간 {props.remainingTimeLabel}
                </Text>
              ) : null}
            </VStack>
          </SignUpField>
        ) : null}

        <PasswordField
          label="비밀번호"
          value={props.password}
          visible={props.passwordVisible}
          autoComplete="new-password"
          message={{
            description: '8~64자, 영문자와 숫자를 포함하고 공백 없이 입력해 주세요.',
            ...props.passwordMessage,
          }}
          onChange={props.onPasswordChange}
          onToggleVisibility={props.onTogglePasswordVisibility}
        />

        <PasswordField
          label="비밀번호 확인"
          value={props.passwordConfirmation}
          visible={props.passwordConfirmationVisible}
          autoComplete="new-password"
          message={props.passwordConfirmationMessage}
          onChange={props.onPasswordConfirmationChange}
          onToggleVisibility={props.onTogglePasswordConfirmationVisibility}
        />

        <StatusMessage tone="critical">{props.submitError}</StatusMessage>

        <ActionButton
          type="submit"
          size="large"
          variant="brandSolid"
          loading={props.isSubmitting}
          disabled={!props.canContinue || props.isSubmitting}
        >
          다음
        </ActionButton>
        </VStack>
      </form>

      <EmailSendingOverlay open={props.emailVerificationStatus === 'sending'} />
    </>
  )
}

function EmailSendingOverlay({ open }: { open: boolean }) {
  return (
    <ContentDialog.Root
      open={open}
      closeOnEscape={false}
      closeOnInteractOutside={false}
      onOpenChange={() => undefined}
    >
      <ContentDialog.Backdrop />
      <ContentDialog.Positioner className="sign-up-email-sending-positioner">
        <ContentDialog.Content className="sign-up-email-sending-dialog">
          <ContentDialog.Header>
            <ContentDialog.Title>인증 메일을 보내고 있어요</ContentDialog.Title>
            <ContentDialog.Description>
              안전하게 인증 코드를 보내는 중이에요. 잠시만 기다려 주세요.
            </ContentDialog.Description>
          </ContentDialog.Header>
          <ContentDialog.Body>
            <Flex className="sign-up-email-sending-body" justify="center" align="center">
              <ProgressCircle.Root aria-label="인증 메일 발송 중" tone="brand" size="40">
                <ProgressCircle.Track />
                <ProgressCircle.Range />
              </ProgressCircle.Root>
            </Flex>
          </ContentDialog.Body>
        </ContentDialog.Content>
      </ContentDialog.Positioner>
    </ContentDialog.Root>
  )
}

function CheckmarkIcon() {
  return (
    <svg className="sign-up-checkmark-icon" viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path
        d="M3 8.25 6.35 11.5 13 4.75"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function IndeterminateIcon() {
  return (
    <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path d="M3.5 8h9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

function AgreementCheckbox({
  checked,
  indeterminate,
  label,
  required,
  onCheckedChange,
}: {
  checked: boolean
  indeterminate?: boolean
  label: string
  required?: boolean
  onCheckedChange: (checked: boolean) => void
}) {
  return (
    <Checkbox.Root
      checked={checked}
      indeterminate={indeterminate}
      required={required}
      onCheckedChange={onCheckedChange}
    >
      <Checkbox.HiddenInput />
      <Checkbox.Control>
        <Checkbox.Indicator checked={<CheckmarkIcon />} indeterminate={<IndeterminateIcon />} />
      </Checkbox.Control>
      <Checkbox.Label>{label}</Checkbox.Label>
    </Checkbox.Root>
  )
}

function AgreementRow({
  termId,
  checked,
  onCheckedChange,
  onOpenTerm,
}: {
  termId: SignUpTermId
  checked: boolean
  onCheckedChange: (checked: boolean) => void
  onOpenTerm: (termId: SignUpTermId) => void
}) {
  const term = signUpTerms[termId]

  return (
    <Flex className="sign-up-agreement-row" justify="space-between" align="center" gap="x2">
      <AgreementCheckbox
        checked={checked}
        required
        label={`[필수] ${term.shortLabel}`}
        onCheckedChange={onCheckedChange}
      />
      <ActionButton
        type="button"
        size="small"
        variant="ghost"
        color="fg.neutralMuted"
        fontWeight="medium"
        bleed="asPadding"
        aria-label={`${term.title} 보기`}
        onClick={() => onOpenTerm(termId)}
      >
        보기
      </ActionButton>
    </Flex>
  )
}

export function SignUpProfileStep(props: SignUpProfileStepProps) {
  const allTermsAccepted = props.serviceTermsAccepted && props.privacyTermsAccepted
  const someTermsAccepted = props.serviceTermsAccepted || props.privacyTermsAccepted

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    props.onComplete()
  }

  const nicknameStatusTone =
    props.nicknameCheckStatus === 'available'
      ? 'positive'
      : props.nicknameCheckStatus === 'unavailable' || props.nicknameCheckStatus === 'error'
        ? 'critical'
        : 'neutral'

  const nicknameStatusMessage = props.nicknameMessage?.error
    ? undefined
    : (props.nicknameMessage?.description ??
      (props.nicknameCheckStatus === 'checking'
        ? '닉네임을 확인하고 있어요.'
        : props.nicknameCheckStatus === 'available'
          ? '사용할 수 있는 닉네임이에요.'
          : props.nicknameCheckStatus === 'unavailable'
            ? '이미 사용 중인 닉네임이에요.'
            : props.nicknameCheckStatus === 'error'
              ? '확인하지 못했어요. 다시 시도해 주세요.'
              : '한글, 영문, 숫자만 사용해 공백 없이 2~10자로 입력해 주세요.'))

  return (
    <>
      <form className="sign-up-form" onSubmit={handleSubmit} noValidate>
        <VStack gap="x6">
          <SignUpField label="닉네임" required error={props.nicknameMessage?.error}>
            <VStack gap="x2">
              <Flex className="sign-up-field-action" gap="x2" align="center">
                <TextField.Root
                  className="sign-up-text-field"
                  invalid={Boolean(props.nicknameMessage?.error)}
                >
                  <TextField.Input
                    type="text"
                    autoComplete="nickname"
                    required
                    minLength={2}
                    maxLength={10}
                    pattern="[가-힣A-Za-z0-9]{2,10}"
                    disabled={props.nicknameCheckStatus === 'checking'}
                    value={props.nickname}
                    onChange={(event) => props.onNicknameChange(event.currentTarget.value)}
                  />
                </TextField.Root>
                <ActionButton
                  type="button"
                  size="small"
                  variant="neutralWeak"
                  loading={props.nicknameCheckStatus === 'checking'}
                  disabled={!props.canCheckNickname}
                  onClick={props.onCheckNickname}
                >
                  {props.nicknameCheckStatus === 'error' ? '다시 확인' : '중복 확인'}
                </ActionButton>
              </Flex>
              <StatusMessage tone={nicknameStatusTone}>{nicknameStatusMessage}</StatusMessage>
            </VStack>
          </SignUpField>

          <Box as="section" aria-labelledby="sign-up-terms-title">
            <VStack gap="x4">
              <VStack gap="x1">
                <Text as="h2" id="sign-up-terms-title" textStyle="t6Bold" color="fg.neutral">
                  약관 동의
                </Text>
                <Text textStyle="t4Regular" color="fg.neutralMuted">
                  가입에 필요한 두 약관을 확인하고 동의해 주세요.
                </Text>
              </VStack>

              <Checkbox.Group className="sign-up-agreement-group" aria-label="회원가입 필수 약관">
                <div className="sign-up-agreement-all">
                  <AgreementCheckbox
                    checked={allTermsAccepted}
                    indeterminate={!allTermsAccepted && someTermsAccepted}
                    label="필수 약관 전체 동의"
                    onCheckedChange={props.onAllTermsAcceptedChange}
                  />
                </div>
                <VStack gap="x3">
                  <AgreementRow
                    termId="service"
                    checked={props.serviceTermsAccepted}
                    onCheckedChange={props.onServiceTermsAcceptedChange}
                    onOpenTerm={props.onOpenTerm}
                  />
                  <AgreementRow
                    termId="privacy"
                    checked={props.privacyTermsAccepted}
                    onCheckedChange={props.onPrivacyTermsAcceptedChange}
                    onOpenTerm={props.onOpenTerm}
                  />
                </VStack>
              </Checkbox.Group>

              <Text textStyle="t3Regular" color="fg.neutralSubtle">
                동의한 약관 버전과 동의 시각은 계정에 기록됩니다.
              </Text>
            </VStack>
          </Box>

          <StatusMessage tone="critical">{props.submitError}</StatusMessage>

          <ActionButton
            type="submit"
            size="large"
            variant="brandSolid"
            loading={props.isSubmitting}
            disabled={!props.canComplete || props.isSubmitting}
          >
            가입하고 시작하기
          </ActionButton>
        </VStack>
      </form>

      <TermsOverlay activeTermId={props.activeTermId} onClose={props.onCloseTerm} />
    </>
  )
}

function TermsOverlay({
  activeTermId,
  onClose,
}: {
  activeTermId: SignUpTermId | null
  onClose: () => void
}) {
  const term = activeTermId ? signUpTerms[activeTermId] : null

  return (
    <ContentDialog.Root
      open={Boolean(term)}
      closeOnInteractOutside
      closeOnEscape
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
    >
      <ContentDialog.Backdrop />
      <ContentDialog.Positioner className="sign-up-terms-positioner">
        <ContentDialog.Content className="sign-up-terms-dialog">
          <ContentDialog.Header>
            <ContentDialog.Title>{term?.title ?? '약관 전문'}</ContentDialog.Title>
            <ContentDialog.Description>
              시행일 {term?.effectiveAt ?? '-'} · 버전 {term?.version ?? '-'}
            </ContentDialog.Description>
            <ContentDialog.CloseButton aria-label="약관 전문 닫기">
              <svg className="sign-up-close-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path
                  d="m6 6 12 12M18 6 6 18"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                />
              </svg>
            </ContentDialog.CloseButton>
          </ContentDialog.Header>
          <ContentDialog.Body className="sign-up-terms-body">
            <VStack as="article" gap="x5">
              {term?.paragraphs.map((paragraph, index) => (
                <Text
                  as="p"
                  key={`${term.id}-${index}`}
                  textStyle="t5Regular"
                  color="fg.neutral"
                >
                  {paragraph}
                </Text>
              ))}
            </VStack>
          </ContentDialog.Body>
          <ContentDialog.Footer>
            <ContentDialog.Action asChild>
              <ActionButton size="large" variant="neutralSolid">
                확인
              </ActionButton>
            </ContentDialog.Action>
          </ContentDialog.Footer>
        </ContentDialog.Content>
      </ContentDialog.Positioner>
    </ContentDialog.Root>
  )
}

export function SignUpView({ step, account, profile, onBack, onLogin }: SignUpViewProps) {
  return (
    <SignUpShell
      step={step}
      title={step === 1 ? '계정을 확인해 주세요.' : '가입 정보를 설정해주세요.'}
      description={
        step === 1
          ? '이메일을 인증하고 로그인에 사용할 비밀번호를 만들어 주세요.'
          : '앞으로 사용할 닉네임과 필수 약관 동의를 확인해 주세요.'
      }
      onBack={onBack}
      footer={
        step === 1 ? (
          <Flex justify="center" align="center" gap="x1" wrap>
            <Text textStyle="t4Regular" color="fg.neutralMuted">
              이미 계정이 있나요?
            </Text>
            <ActionButton
              type="button"
              size="small"
              variant="ghost"
              color="fg.brand"
              bleed="asPadding"
              onClick={onLogin}
            >
              로그인
            </ActionButton>
          </Flex>
        ) : null
      }
    >
      {step === 1 ? <SignUpAccountStep {...account} /> : <SignUpProfileStep {...profile} />}
    </SignUpShell>
  )
}
