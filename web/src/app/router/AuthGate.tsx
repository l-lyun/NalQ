import { ActionButton, Text, VStack } from '@seed-design/react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { useAuthPhase } from '@/features/auth/model/useAuthPhase'
import { bootstrapAuthSession } from '@/features/auth/model/authSession'
import { getAutomaticOnboardingAdmission } from '@/features/onboarding/model/automaticOnboarding'
import { PublicLandingPage } from '@/pages/landing/PublicLandingPage'

export type AuthReturnState = {
  from?: string
}

export function AuthLoading() {
  return (
    <VStack minHeight="100dvh" align="center" justify="center" bg="bg.layerDefault" gap="x3">
      <Text role="status" textStyle="t5Regular" color="fg.neutralMuted">
        로그인 상태를 확인하고 있어요.
      </Text>
    </VStack>
  )
}

export function AuthBootstrapError() {
  return (
    <VStack minHeight="100dvh" align="center" justify="center" bg="bg.layerDefault" gap="x3">
      <Text role="alert" textStyle="t5Regular" color="fg.critical">
        로그인 상태를 확인하지 못했어요.
      </Text>
      <ActionButton
        type="button"
        size="medium"
        variant="neutralWeak"
        onClick={() => void bootstrapAuthSession()}
      >
        다시 시도
      </ActionButton>
    </VStack>
  )
}

export function AuthGate() {
  const phase = useAuthPhase()
  const location = useLocation()

  if (phase === 'bootstrapping') return <AuthLoading />
  if (phase === 'bootstrap-error') return <AuthBootstrapError />
  if (phase === 'anonymous') {
    if (location.pathname === '/') return <PublicLandingPage />

    const from = `${location.pathname}${location.search}${location.hash}`
    return <Navigate to="/login" replace state={{ from } satisfies AuthReturnState} />
  }

  return <Outlet />
}

export function PublicOnlyGate() {
  const phase = useAuthPhase()

  if (phase === 'bootstrapping') return <AuthLoading />
  if (phase === 'bootstrap-error') return <AuthBootstrapError />
  if (phase === 'authenticated') {
    const onboardingState = getAutomaticOnboardingAdmission()
    return onboardingState
      ? <Navigate to="/onboarding" replace state={onboardingState} />
      : <Navigate to="/" replace />
  }

  return <Outlet />
}

export function safeReturnPath(state: unknown) {
  const candidate = (state as AuthReturnState | null)?.from
  if (!candidate || !candidate.startsWith('/') || candidate.startsWith('//')) return '/'

  const resolved = new URL(candidate, window.location.origin)
  if (resolved.origin !== window.location.origin) return '/'

  return `${resolved.pathname}${resolved.search}${resolved.hash}`
}
