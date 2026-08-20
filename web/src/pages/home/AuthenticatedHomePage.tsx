import { ActionButton, Text, VStack } from '@seed-design/react'
import { useNavigate } from 'react-router-dom'

import { useLogoutMutation } from '@/features/auth/model/auth.mutations'
import { useCurrentUser } from '@/features/auth/model/auth.queries'

import { homeReadyFixture } from './home.fixtures'
import { HomePage } from './HomePage'

export function AuthenticatedHomePage() {
  const navigate = useNavigate()
  const currentUser = useCurrentUser()
  const logout = useLogoutMutation()

  if (currentUser.isError && !currentUser.data) {
    return (
      <VStack minHeight="100dvh" align="center" justify="center" bg="bg.layerDefault" gap="x3">
        <Text role="alert" textStyle="t5Regular" color="fg.critical">
          사용자 정보를 불러오지 못했어요.
        </Text>
        <ActionButton
          type="button"
          size="medium"
          variant="neutralWeak"
          loading={currentUser.isFetching}
          disabled={currentUser.isFetching}
          onClick={() => void currentUser.refetch()}
        >
          다시 시도
        </ActionButton>
      </VStack>
    )
  }

  if (!currentUser.data) {
    return (
      <VStack minHeight="100dvh" align="center" justify="center" bg="bg.layerDefault" gap="x3">
        <Text role="status" textStyle="t5Regular" color="fg.neutralMuted">
          사용자 정보를 불러오고 있어요.
        </Text>
      </VStack>
    )
  }

  return (
    <HomePage
      {...homeReadyFixture}
      navigation={homeReadyFixture.navigation.map((item) =>
        item.id === 'learning' ? { ...item, onClick: () => navigate('/learning') } : item,
      )}
      session={{
        email: currentUser.data.email,
        logoutPending: logout.isPending,
        onLogout: () => logout.mutate(),
      }}
    />
  )
}
