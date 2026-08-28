import { IconPersonCircleFill } from '@karrotmarket/react-monochrome-icon'
import { ActionButton, Avatar, Box, Divider, Flex, Icon, List, Skeleton, Text, VStack } from '@seed-design/react'

import './profile.css'

export type ProfilePageProps = {
  status: 'loading' | 'ready' | 'error'
  nickname?: string | null
  email?: string
  appVersion: string
  logoutPending: boolean
  logoutError?: string
  onLogout: () => void
  onRetry: () => void
}

export function ProfilePage({ status, nickname, email, appVersion, logoutPending, logoutError, onLogout, onRetry }: ProfilePageProps) {
  const displayName = nickname || '닉네임을 확인할 수 없어요'

  return (
    <VStack className="profile-shell" minHeight="100dvh" bg="bg.layerBasement">
      <Box as="main" className="profile-main" bg="bg.layerDefault" width="full" pt="safeArea">
        <VStack className="profile-content" px="spacingX.globalGutter" pt="x6" pb="spacingY.screenBottom" gap="x8">
          <Text as="h1" textStyle="t12Bold" color="fg.neutral">프로필</Text>
          {status === 'loading' ? <ProfileLoading /> : status === 'error' ? (
            <VStack minHeight="320px" align="center" justify="center" gap="x4">
              <Text role="alert" textStyle="t5Regular" color="fg.neutralMuted" align="center">프로필 정보를 불러오지 못했어요.</Text>
              <ActionButton type="button" size="medium" variant="neutralWeak" onClick={onRetry}>다시 시도</ActionButton>
            </VStack>
          ) : (
            <>
              <VStack as="section" align="center" gap="x3" aria-label="계정 요약">
                <Avatar.Root size="108" aria-label={`${displayName}님의 기본 프로필 이미지`}>
                  <Avatar.Fallback><Icon svg={<IconPersonCircleFill />} size="x10" color="fg.neutralMuted" /></Avatar.Fallback>
                </Avatar.Root>
                <VStack align="center" gap="x1">
                  <Text textStyle="t8Bold" color="fg.neutral" align="center">{nickname ? `${nickname}님` : displayName}</Text>
                  <Text textStyle="t4Regular" color="fg.neutralMuted" align="center">{email}</Text>
                </VStack>
              </VStack>

              <VStack as="section" gap="x3" aria-labelledby="profile-account-title">
                <Text as="h2" id="profile-account-title" textStyle="t10Bold" color="fg.neutral">계정 관리</Text>
                <List.Root className="profile-list" width="full">
                  <List.Item>
                    <List.Content asChild>
                      <button className="profile-list-button" type="button" disabled={logoutPending} onClick={onLogout}>
                        <VStack minWidth="0px" flexGrow gap="x1" align="flex-start">
                          <List.Title>로그아웃</List.Title>
                          <List.Detail>이 기기의 현재 세션을 종료해요</List.Detail>
                        </VStack>
                        {logoutPending ? <Text textStyle="t3Regular" color="fg.neutralMuted">처리 중</Text> : null}
                      </button>
                    </List.Content>
                  </List.Item>
                </List.Root>
                {logoutError ? (
                  <VStack align="flex-start" gap="x2" role="alert">
                    <Text textStyle="t4Regular" color="fg.critical">{logoutError}</Text>
                    <ActionButton type="button" size="small" variant="ghost" disabled={logoutPending} onClick={onLogout}>다시 시도</ActionButton>
                  </VStack>
                ) : null}
              </VStack>

              <Divider as="div" color="stroke.neutralSubtle" />
              <VStack as="section" gap="x3" aria-labelledby="profile-service-title">
                <Text as="h2" id="profile-service-title" textStyle="t10Bold" color="fg.neutral">서비스 정보</Text>
                <Flex className="profile-version-row" align="center" justify="space-between" gap="x4">
                  <Text textStyle="t5Medium" color="fg.neutral">앱 버전</Text>
                  <Text textStyle="t4Regular" color="fg.neutralMuted">{appVersion}</Text>
                </Flex>
              </VStack>
            </>
          )}
        </VStack>
      </Box>
    </VStack>
  )
}

function ProfileLoading() {
  return (
    <VStack gap="x8" aria-busy="true" aria-label="프로필 정보를 불러오는 중">
      <VStack align="center" gap="x3">
        <Skeleton tone="neutral" radius="full" width="108px" height="108px" />
        <Skeleton tone="neutral" radius="8" width="120px" height="x6" />
        <Skeleton tone="neutral" radius="8" width="200px" height="x4" />
      </VStack>
      <VStack gap="x3">
        <Skeleton tone="neutral" radius="8" width="96px" height="x6" />
        <Skeleton tone="neutral" radius="8" width="full" height="64px" />
      </VStack>
    </VStack>
  )
}
