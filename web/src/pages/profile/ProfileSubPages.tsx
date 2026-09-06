import { IconArrowLeftLine } from '@karrotmarket/react-monochrome-icon'
import {
  ActionButton,
  Box,
  Divider,
  Field,
  Flex,
  Icon,
  Skeleton,
  Text,
  TextField,
  VStack,
} from '@seed-design/react'
import { type FormEvent, useEffect, useRef, useState } from 'react'
import { useBlocker } from 'react-router-dom'

import {
  useAccountWithdrawalMutation,
  useUpdateCurrentUserNicknameMutation,
} from '@/features/auth/model/auth.mutations'
import { ApiClientError, getApiErrorMessage } from '@/shared/api/apiError'

const NICKNAME_PATTERN = /^[가-힣A-Za-z0-9]{2,10}$/u

export function AccountSettingsPage({
  status,
  nickname,
  email,
  onBack,
  onRetry,
  onOpenWithdrawal,
}: {
  status: 'loading' | 'ready' | 'error'
  nickname?: string | null
  email?: string
  onBack: () => void
  onRetry: () => void
  onOpenWithdrawal: () => void
}) {
  const updateNickname = useUpdateCurrentUserNicknameMutation()
  const [draft, setDraft] = useState(nickname ?? '')

  useEffect(() => {
    setDraft(nickname ?? '')
  }, [nickname])

  const normalizedNickname = draft.normalize('NFC')
  const formatValid = NICKNAME_PATTERN.test(normalizedNickname)
  const changed = normalizedNickname !== (nickname ?? '')
  const fieldError = !formatValid && draft.length > 0
    ? '한글, 영문, 숫자만 사용해 공백 없이 2~10자로 입력해 주세요.'
    : updateNickname.error instanceof ApiClientError
      ? updateNickname.error.fields.find((item) => item.field === 'nickname')?.reason
      : undefined

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!formatValid || !changed || updateNickname.isPending) return
    updateNickname.mutate({ nickname: normalizedNickname })
  }

  return (
    <ProfileSubPage title="계정설정" onBack={onBack}>
      {status === 'loading' ? (
        <VStack gap="x4" aria-busy="true" aria-label="계정설정을 불러오는 중">
          <Skeleton tone="neutral" radius="8" width="full" height="72px" />
          <Skeleton tone="neutral" radius="8" width="full" height="72px" />
        </VStack>
      ) : status === 'error' ? (
        <VStack align="flex-start" gap="x4">
          <Text role="alert" textStyle="t5Regular" color="fg.critical">계정 정보를 불러오지 못했어요.</Text>
          <ActionButton type="button" size="medium" variant="neutralWeak" onClick={onRetry}>다시 시도</ActionButton>
        </VStack>
      ) : (
        <VStack gap="x6">
          <form onSubmit={handleSubmit} noValidate>
            <VStack gap="x6">
            <Field.Root>
              <Field.Label>이메일</Field.Label>
              <TextField.Root>
                <TextField.Input type="email" value={email ?? ''} readOnly aria-readonly />
              </TextField.Root>
              <Field.Footer><Field.Description>로그인에 사용하는 이메일은 현재 변경할 수 없어요.</Field.Description></Field.Footer>
            </Field.Root>
            <Field.Root invalid={Boolean(fieldError)} required>
              <Field.Label>닉네임<Field.RequiredIndicator aria-label="필수" /></Field.Label>
              <TextField.Root invalid={Boolean(fieldError)}>
                <TextField.Input
                  type="text"
                  autoComplete="nickname"
                  minLength={2}
                  maxLength={10}
                  pattern="[가-힣A-Za-z0-9]{2,10}"
                  value={draft}
                  disabled={updateNickname.isPending}
                  onChange={(event) => {
                    setDraft(event.currentTarget.value)
                    updateNickname.reset()
                  }}
                />
              </TextField.Root>
              <Field.Footer>
                {fieldError ? <Field.ErrorMessage>{fieldError}</Field.ErrorMessage> : (
                  <Field.Description>한글, 영문, 숫자만 사용해 공백 없이 2~10자로 입력해 주세요.</Field.Description>
                )}
              </Field.Footer>
            </Field.Root>
            {updateNickname.isError && !fieldError ? (
              <Text role="alert" textStyle="t4Regular" color="fg.critical">
                {getApiErrorMessage(updateNickname.error, '닉네임을 변경하지 못했어요. 다시 시도해 주세요.')}
              </Text>
            ) : null}
            {updateNickname.isSuccess ? (
              <Text role="status" aria-live="polite" textStyle="t4Regular" color="fg.positive">닉네임을 변경했어요.</Text>
            ) : null}
            <ActionButton
              type="submit"
              size="large"
              variant="brandSolid"
              loading={updateNickname.isPending}
              disabled={!formatValid || !changed || updateNickname.isPending}
            >
              변경사항 저장
            </ActionButton>
            </VStack>
          </form>
          <Divider as="div" color="stroke.neutralSubtle" />
          <VStack as="section" gap="x3" align="flex-start" aria-labelledby="account-withdrawal-title">
            <Text as="h2" id="account-withdrawal-title" textStyle="t6Bold" color="fg.neutral">계정 삭제</Text>
            <Text textStyle="t4Regular" color="fg.neutralMuted">
              탈퇴하면 계정 사용이 즉시 중단되고 이전 학습 기록은 복구할 수 없어요.
            </Text>
            <ActionButton type="button" size="medium" variant="neutralWeak" onClick={onOpenWithdrawal}>
              회원 탈퇴
            </ActionButton>
          </VStack>
        </VStack>
      )}
    </ProfileSubPage>
  )
}

export function AccountWithdrawalPage({
  onBack,
  onCompleted,
}: {
  onBack: () => void
  onCompleted: () => void
}) {
  const allowCompletionNavigation = useRef(false)
  const withdrawal = useAccountWithdrawalMutation(() => {
    allowCompletionNavigation.current = true
    onCompleted()
  })
  const blocker = useBlocker(() => withdrawal.isPending && !allowCompletionNavigation.current)
  const requestId = useRef<string | null>(null)
  const [step, setStep] = useState<'impact' | 'confirm'>('impact')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const confirmationMatches = confirmation === '회원탈퇴'
  const passwordError = withdrawal.error instanceof ApiClientError && withdrawal.error.code === 'AUTH_012'
    ? '현재 비밀번호가 일치하지 않아요.'
    : withdrawal.error instanceof ApiClientError
      ? withdrawal.error.fields.find((item) => item.field === 'currentPassword')?.reason
      : undefined
  const confirmationError = confirmation.length > 0 && !confirmationMatches
    ? '띄어쓰기 없이 회원탈퇴를 정확히 입력해 주세요.'
    : withdrawal.error instanceof ApiClientError
      ? withdrawal.error.fields.find((item) => item.field === 'confirmation')?.reason
      : undefined

  useEffect(() => {
    if (blocker.state === 'blocked' && !withdrawal.isPending) blocker.reset()
  }, [blocker, withdrawal.isPending])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!password || !confirmationMatches || withdrawal.isPending) return
    if (!window.confirm('계정을 탈퇴할까요? 이 작업은 되돌릴 수 없고 이전 학습 기록은 복구되지 않아요.')) return

    requestId.current ??= window.crypto.randomUUID()
    withdrawal.mutate({
      withdrawalRequestId: requestId.current,
      currentPassword: password,
      confirmation: '회원탈퇴',
    })
  }

  return (
    <ProfileSubPage title="회원탈퇴" onBack={onBack} backDisabled={withdrawal.isPending}>
      {step === 'impact' ? (
        <VStack gap="x6" align="stretch">
          <Box bg="bg.criticalWeak" borderRadius="r3" p="x4">
            <VStack gap="x3" align="flex-start">
              <Text as="h2" textStyle="t6Bold" color="fg.critical">탈퇴 전에 확인해 주세요</Text>
              <Text as="p" textStyle="t5Regular" color="fg.neutral">
                탈퇴 즉시 NalQ 계정과 모든 로그인 세션을 더 이상 사용할 수 없어요.
              </Text>
            </VStack>
          </Box>
          <VStack as="ul" className="profile-impact-list" gap="x3">
            <li><Text textStyle="t5Regular" color="fg.neutral">이메일, 닉네임과 로그인 정보는 탈퇴 확정 즉시 제거돼요.</Text></li>
            <li><Text textStyle="t5Regular" color="fg.neutral">학습자료, 퀴즈와 풀이 기록은 가능한 한 신속히, 최대 30일 이내 삭제하거나 개인과 연결할 수 없게 처리해요.</Text></li>
            <li><Text textStyle="t5Regular" color="fg.neutral">30일은 보관이나 복구 유예 기간이 아니라 처리를 완료하는 최대 기한이에요.</Text></li>
            <li><Text textStyle="t5Regular" color="fg.neutral">같은 이메일로 다시 가입할 수 있지만 새 계정이 만들어지며 이전 기록은 이어지지 않아요.</Text></li>
          </VStack>
          <ActionButton type="button" size="large" variant="criticalSolid" onClick={() => setStep('confirm')}>
            회원 탈퇴 계속
          </ActionButton>
          <ActionButton type="button" size="large" variant="ghost" onClick={onBack}>취소</ActionButton>
        </VStack>
      ) : (
        <form onSubmit={handleSubmit} noValidate>
          <VStack gap="x6">
            <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
              본인 확인을 위해 현재 비밀번호와 확인 문구를 입력해 주세요. 비밀번호를 잊었다면 이 화면을 나가 비밀번호를 재설정하고 다시 로그인한 뒤 처음부터 진행해야 해요.
            </Text>
            <Field.Root required invalid={Boolean(passwordError)}>
              <Field.Label>현재 비밀번호<Field.RequiredIndicator aria-label="필수" /></Field.Label>
              <TextField.Root invalid={Boolean(passwordError)}>
                <TextField.Input
                  type="password"
                  autoComplete="current-password"
                  maxLength={64}
                  value={password}
                  disabled={withdrawal.isPending}
                  onChange={(event) => {
                    setPassword(event.currentTarget.value)
                    withdrawal.reset()
                  }}
                />
              </TextField.Root>
              <Field.Footer>
                {passwordError ? <Field.ErrorMessage>{passwordError}</Field.ErrorMessage> : (
                  <Field.Description>탈퇴 화면에서는 이메일 코드로 본인 확인을 대신할 수 없어요.</Field.Description>
                )}
              </Field.Footer>
            </Field.Root>
            <Field.Root required invalid={Boolean(confirmationError)}>
              <Field.Label>확인 문구<Field.RequiredIndicator aria-label="필수" /></Field.Label>
              <TextField.Root invalid={Boolean(confirmationError)}>
                <TextField.Input
                  type="text"
                  autoComplete="off"
                  value={confirmation}
                  disabled={withdrawal.isPending}
                  onChange={(event) => {
                    setConfirmation(event.currentTarget.value)
                    withdrawal.reset()
                  }}
                  aria-describedby="withdrawal-confirmation-description"
                />
              </TextField.Root>
              <Field.Footer>
                {confirmationError ? <Field.ErrorMessage>{confirmationError}</Field.ErrorMessage> : (
                  <Field.Description id="withdrawal-confirmation-description">회원탈퇴를 띄어쓰기 없이 정확히 입력해 주세요.</Field.Description>
                )}
              </Field.Footer>
            </Field.Root>
            {withdrawal.isError && !passwordError && !confirmationError ? (
              <Text role="alert" textStyle="t4Regular" color="fg.critical">
                {getApiErrorMessage(withdrawal.error, '계정이 탈퇴되지 않았어요. 잠시 후 다시 시도해 주세요.')}
              </Text>
            ) : null}
            <ActionButton
              type="submit"
              size="large"
              variant="criticalSolid"
              loading={withdrawal.isPending}
              disabled={!password || !confirmationMatches || withdrawal.isPending}
            >
              회원 탈퇴
            </ActionButton>
            <ActionButton
              type="button"
              size="large"
              variant="ghost"
              disabled={withdrawal.isPending}
              onClick={onBack}
            >
              취소
            </ActionButton>
          </VStack>
        </form>
      )}
    </ProfileSubPage>
  )
}

function ProfileSubPage({
  title,
  onBack,
  backDisabled = false,
  children,
}: {
  title: string
  onBack: () => void
  backDisabled?: boolean
  children: React.ReactNode
}) {
  return (
    <VStack className="profile-shell" minHeight="100dvh" bg="bg.layerDefault">
      <Box as="main" className="profile-main" bg="bg.layerDefault" width="full" pt="safeArea">
        <VStack className="profile-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x6">
          <Flex as="header" align="center" gap="x2">
            <ActionButton type="button" size="small" variant="ghost" layout="iconOnly" aria-label="마이페이지로 돌아가기" disabled={backDisabled} onClick={onBack}>
              <Icon svg={<IconArrowLeftLine />} size="x5" />
            </ActionButton>
            <Text as="h1" textStyle="t9Bold" color="fg.neutral">{title}</Text>
            <div className="app-notification-slot" data-app-notification-slot />
          </Flex>
          {children}
        </VStack>
      </Box>
    </VStack>
  )
}
