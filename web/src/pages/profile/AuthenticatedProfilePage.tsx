import packageMetadata from '../../../package.json'

import { useLocation, useNavigate } from 'react-router-dom'
import { ACCOUNT_WITHDRAWAL_COMPLETED_NOTICE } from '@/features/auth/model/loginRouteState'

import { useLogoutMutation } from '@/features/auth/model/auth.mutations'
import { useCurrentUser } from '@/features/auth/model/auth.queries'
import { OnboardingPage } from '@/pages/onboarding/OnboardingPage'

import { ProfilePage } from './ProfilePage'
import { AccountSettingsPage, AccountWithdrawalPage } from './ProfileSubPages'
import {
  cameFromProfileMain,
  normalizeProfilePath,
  profileSubPageNavigationState,
} from './profileRoutes'

export function AuthenticatedProfilePage() {
  const location = useLocation()
  const navigate = useNavigate()
  const currentUser = useCurrentUser()
  const logout = useLogoutMutation()
  const pathname = normalizeProfilePath(location.pathname)
  const status = currentUser.isPending && !currentUser.data
    ? 'loading'
    : currentUser.isError && !currentUser.data
      ? 'error'
      : 'ready'
  const back = () => {
    if (cameFromProfileMain(location.state)) navigate(-1)
    else navigate('/profile', { replace: true })
  }

  if (pathname === '/profile/account') {
    return (
      <AccountSettingsPage
        status={status}
        nickname={currentUser.data?.nickname}
        email={currentUser.data?.email}
        onBack={back}
        onRetry={() => void currentUser.refetch()}
        onOpenWithdrawal={() => navigate('/profile/withdrawal', { state: profileSubPageNavigationState })}
      />
    )
  }
  if (pathname === '/profile/guide') {
    return <OnboardingPage mode="guide" onExit={back} />
  }
  if (pathname === '/profile/withdrawal') {
    return (
      <AccountWithdrawalPage
        onBack={back}
        onCompleted={() => navigate('/login', {
          replace: true,
          state: { notice: ACCOUNT_WITHDRAWAL_COMPLETED_NOTICE },
        })}
      />
    )
  }
  return (
    <ProfilePage
      status={status}
      nickname={currentUser.data?.nickname}
      email={currentUser.data?.email}
      appVersion={import.meta.env.VITE_APP_VERSION?.trim() || packageMetadata.version}
      logoutPending={logout.isPending}
      logoutError={logout.isError ? '로그아웃하지 못했어요. 다시 시도해주세요.' : undefined}
      onOpenAccount={() => navigate('/profile/account', { state: profileSubPageNavigationState })}
      onOpenGuide={() => navigate('/profile/guide', { state: profileSubPageNavigationState })}
      legalDocumentsAvailable={import.meta.env.DEV}
      onOpenTerms={() => navigate('/terms', { state: { returnTo: '/profile' } })}
      onOpenPrivacy={() => navigate('/privacy', { state: { returnTo: '/profile' } })}
      onOpenInquiry={() => navigate('/support', { state: { returnTo: '/profile' } })}
      onOpenOpenSourceLicenses={() => navigate('/open-source-licenses', { state: { returnTo: '/profile' } })}
      onLogout={() => {
        if (!window.confirm('이 기기에서 로그아웃할까요?')) return
        logout.reset()
        logout.mutate()
      }}
      onRetry={() => void currentUser.refetch()}
    />
  )
}
