import { IconArrowLeftLine } from '@karrotmarket/react-monochrome-icon'
import {
  ActionButton,
  Box,
  Field,
  Flex,
  Icon,
  Skeleton,
  Text,
  TextField,
  VStack,
} from '@seed-design/react'
import { type FormEvent, useEffect, useState } from 'react'

import { useUpdateCurrentUserNicknameMutation } from '@/features/auth/model/auth.mutations'
import { ApiClientError, getApiErrorMessage } from '@/shared/api/apiError'
import { signUpTerms, type SignUpTermId } from '@/pages/sign-up/termsContent'

const NICKNAME_PATTERN = /^[가-힣A-Za-z0-9]{2,10}$/u

export function AccountSettingsPage({
  status,
  nickname,
  email,
  onBack,
  onRetry,
}: {
  status: 'loading' | 'ready' | 'error'
  nickname?: string | null
  email?: string
  onBack: () => void
  onRetry: () => void
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
      )}
    </ProfileSubPage>
  )
}

export function TermsPage({ termId, title, onBack }: { termId: SignUpTermId; title: string; onBack: () => void }) {
  const term = signUpTerms[termId]
  return (
    <ProfileSubPage title={title} onBack={onBack}>
      <VStack as="article" gap="x5">
        <Text textStyle="t4Medium" color="fg.neutralMuted">현재 앱 내 문안 · 버전 {term.version}</Text>
        {term.paragraphs.map((paragraph, index) => (
          <Text as="p" key={`${term.id}-${index}`} textStyle="t5Regular" color={index === 0 ? 'fg.warning' : 'fg.neutral'}>
            {paragraph}
          </Text>
        ))}
      </VStack>
    </ProfileSubPage>
  )
}

export function PendingFeaturePage({
  title,
  description,
  onBack,
}: {
  title: string
  description: string
  onBack: () => void
}) {
  return (
    <ProfileSubPage title={title} onBack={onBack}>
      <Box bg="bg.neutralWeak" borderRadius="r3" p="x5">
        <VStack gap="x3" align="flex-start">
          <Text as="h2" textStyle="t7Bold" color="fg.neutral">아직 준비 중이에요</Text>
          <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">{description}</Text>
        </VStack>
      </Box>
    </ProfileSubPage>
  )
}

function ProfileSubPage({ title, onBack, children }: { title: string; onBack: () => void; children: React.ReactNode }) {
  return (
    <VStack className="profile-shell" minHeight="100dvh" bg="bg.layerBasement">
      <Box as="main" className="profile-main" bg="bg.layerDefault" width="full" pt="safeArea">
        <VStack className="profile-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x6">
          <Flex as="header" align="center" gap="x2">
            <ActionButton type="button" size="small" variant="ghost" layout="iconOnly" aria-label="마이페이지로 돌아가기" onClick={onBack}>
              <Icon svg={<IconArrowLeftLine />} size="x5" />
            </ActionButton>
            <Text as="h1" textStyle="t10Bold" color="fg.neutral">{title}</Text>
          </Flex>
          {children}
        </VStack>
      </Box>
    </VStack>
  )
}
