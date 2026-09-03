import {
  IconChevronRightLine,
  IconPersonCircleFill,
} from '@karrotmarket/react-monochrome-icon'
import {
  ActionButton,
  Avatar,
  Box,
  Divider,
  Flex,
  HStack,
  Icon,
  List,
  Skeleton,
  Text,
  VStack,
} from '@seed-design/react'

import './profile.css'

export type ProfilePageProps = {
  status: 'loading' | 'ready' | 'error'
  nickname?: string | null
  email?: string
  appVersion: string
  logoutPending: boolean
  logoutError?: string
  legalDocumentsAvailable: boolean
  accountWithdrawalAvailable: boolean
  onOpenAccount: () => void
  onOpenGuide: () => void
  onOpenTerms: () => void
  onOpenPrivacy: () => void
  onOpenInquiry: () => void
  onOpenOpenSourceLicenses: () => void
  onOpenWithdrawal: () => void
  onLogout: () => void
  onRetry: () => void
}

export function ProfilePage({
  status,
  nickname,
  email,
  appVersion,
  logoutPending,
  logoutError,
  legalDocumentsAvailable,
  accountWithdrawalAvailable,
  onOpenAccount,
  onOpenGuide,
  onOpenTerms,
  onOpenPrivacy,
  onOpenInquiry,
  onOpenOpenSourceLicenses,
  onOpenWithdrawal,
  onLogout,
  onRetry,
}: ProfilePageProps) {
  const displayName = nickname || '닉네임 미설정'

  return (
    <VStack className="profile-shell" minHeight="100dvh" bg="bg.layerBasement">
      <Box as="main" className="profile-main" bg="bg.layerDefault" width="full" pt="safeArea">
        <VStack className="profile-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x3">
          <Text as="h1" textStyle="t12Bold" color="fg.neutral">마이페이지</Text>
          {status === 'loading' ? <ProfileLoading /> : status === 'error' ? (
            <VStack minHeight="320px" align="center" justify="center" gap="x4">
              <Text role="alert" textStyle="t5Regular" color="fg.neutralMuted" align="center">계정 정보를 불러오지 못했어요.</Text>
              <ActionButton type="button" size="medium" variant="neutralWeak" onClick={onRetry}>다시 시도</ActionButton>
            </VStack>
          ) : (
            <>
              <HStack as="section" gap="x3" align="center" aria-label="계정 요약">
                <Avatar.Root size="64" aria-label={`${displayName}님의 기본 프로필 이미지`}>
                  <Avatar.Fallback><Icon svg={<IconPersonCircleFill />} size="x8" color="fg.neutralMuted" /></Avatar.Fallback>
                </Avatar.Root>
                <VStack minWidth="0px" align="flex-start" gap="x1">
                  <Text textStyle="t8Bold" color="fg.neutral">{nickname ? `${nickname}님` : displayName}</Text>
                  <Text className="profile-account-email" textStyle="t4Regular" color="fg.neutralMuted">{email}</Text>
                </VStack>
              </HStack>

              <SettingsSection title="계정" titleId="mypage-account-title">
                <SettingsRow label="계정설정" onClick={onOpenAccount} />
                <SettingsRow label="로그아웃" onClick={onLogout} pending={logoutPending} trailing={false} />
              </SettingsSection>
              {logoutError ? (
                <VStack align="flex-start" gap="x2" role="alert">
                  <Text textStyle="t4Regular" color="fg.critical">{logoutError}</Text>
                  <ActionButton type="button" size="small" variant="ghost" disabled={logoutPending} onClick={onLogout}>다시 시도</ActionButton>
                </VStack>
              ) : null}

              <Divider as="div" color="stroke.neutralSubtle" />
              <SettingsSection title="서비스 정보" titleId="mypage-service-title">
                <SettingsRow label="NalQ 가이드" onClick={onOpenGuide} />
                {legalDocumentsAvailable ? <SettingsRow label="서비스 이용약관" onClick={onOpenTerms} /> : null}
                {legalDocumentsAvailable ? <SettingsRow label="개인정보처리방침" onClick={onOpenPrivacy} /> : null}
                <SettingsRow label="문의하기" onClick={onOpenInquiry} />
                <SettingsRow label="오픈소스 라이선스" onClick={onOpenOpenSourceLicenses} />
                <Flex className="profile-version-row" align="center" justify="space-between" gap="x4">
                  <Text textStyle="t5Medium" color="fg.neutral">앱 버전</Text>
                  <Text textStyle="t4Regular" color="fg.neutralMuted">{appVersion}</Text>
                </Flex>
              </SettingsSection>

              {accountWithdrawalAvailable ? (
                <>
                  <Divider as="div" color="stroke.neutralSubtle" />
                  <SettingsSection title="계정 종료" titleId="mypage-withdrawal-title">
                    <SettingsRow label="회원 탈퇴" onClick={onOpenWithdrawal} />
                  </SettingsSection>
                  <Text textStyle="t4Regular" color="fg.neutralMuted">탈퇴하면 이전 학습 기록을 다시 이용할 수 없어요.</Text>
                </>
              ) : null}
            </>
          )}
        </VStack>
      </Box>
    </VStack>
  )
}

function SettingsSection({ title, titleId, children }: { title: string; titleId: string; children: React.ReactNode }) {
  return (
    <VStack as="section" gap="x1" aria-labelledby={titleId}>
      <Text as="h2" id={titleId} textStyle="t10Bold" color="fg.neutral">{title}</Text>
      <List.Root className="profile-list" width="full">{children}</List.Root>
    </VStack>
  )
}

function SettingsRow({
  label,
  onClick,
  pending = false,
  trailing = true,
}: {
  label: string
  onClick: () => void
  pending?: boolean
  trailing?: boolean
}) {
  return (
    <List.Item>
      <List.Content asChild>
        <button className="profile-list-button" type="button" disabled={pending} onClick={onClick}>
          <VStack minWidth="0px" flexGrow align="flex-start">
            <List.Title>{label}</List.Title>
          </VStack>
          {pending ? (
            <Text textStyle="t3Regular" color="fg.neutralMuted">처리 중</Text>
          ) : trailing ? (
            <Icon svg={<IconChevronRightLine />} size="x4_5" color="fg.neutralSubtle" aria-hidden />
          ) : null}
        </button>
      </List.Content>
    </List.Item>
  )
}

function ProfileLoading() {
  return (
    <VStack gap="x3" aria-busy="true" aria-label="마이페이지 정보를 불러오는 중">
      <HStack align="center" gap="x3">
        <Skeleton tone="neutral" radius="full" width="64px" height="64px" />
        <VStack gap="x2">
          <Skeleton tone="neutral" radius="8" width="120px" height="x6" />
          <Skeleton tone="neutral" radius="8" width="200px" height="x4" />
        </VStack>
      </HStack>
      <VStack gap="x2">
        <Skeleton tone="neutral" radius="8" width="96px" height="x6" />
        <Skeleton tone="neutral" radius="8" width="full" height="180px" />
      </VStack>
    </VStack>
  )
}
