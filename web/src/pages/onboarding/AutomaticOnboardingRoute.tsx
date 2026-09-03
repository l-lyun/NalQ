import { ActionButton, Text, VStack } from '@seed-design/react'
import { useEffect, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'

import { useCurrentUser } from '@/features/auth/model/auth.queries'
import {
  createAutomaticOnboardingLifecycle,
  finishAutomaticOnboarding,
  hasAutomaticOnboardingAdmission,
} from '@/features/onboarding/model/automaticOnboarding'

import { OnboardingPage } from './OnboardingPage'

export function AutomaticOnboardingRoute() {
  const location = useLocation()
  const navigate = useNavigate()
  const currentUser = useCurrentUser()
  const [lifecycle] = useState(createAutomaticOnboardingLifecycle)

  useEffect(() => {
    lifecycle.mount()
    return lifecycle.unmount
  }, [lifecycle])

  if (currentUser.isPending && !currentUser.data) {
    return (
      <VStack minHeight="100dvh" align="center" justify="center" bg="bg.layerDefault">
        <Text role="status" textStyle="t5Regular" color="fg.neutralMuted">
          시작 가이드를 준비하고 있어요.
        </Text>
      </VStack>
    )
  }

  if (currentUser.isError || !currentUser.data) {
    return (
      <VStack minHeight="100dvh" align="center" justify="center" bg="bg.layerDefault" gap="x3">
        <Text role="alert" textStyle="t5Regular" color="fg.neutralMuted">
          시작 가이드를 준비하지 못했어요.
        </Text>
        <ActionButton type="button" size="medium" variant="neutralWeak" onClick={() => navigate('/', { replace: true })}>
          홈으로 이동
        </ActionButton>
      </VStack>
    )
  }

  if (!hasAutomaticOnboardingAdmission(currentUser.data.id, location.state)) {
    return <Navigate to="/" replace />
  }

  return (
    <OnboardingPage
      mode="automatic"
      onExit={() => {
        finishAutomaticOnboarding()
        navigate('/', { replace: true })
      }}
    />
  )
}
