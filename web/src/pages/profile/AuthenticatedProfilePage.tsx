import packageMetadata from '../../../package.json'

import { useLocation, useNavigate } from 'react-router-dom'

import { useLogoutMutation } from '@/features/auth/model/auth.mutations'
import { useCurrentUser } from '@/features/auth/model/auth.queries'
import { OnboardingPage } from '@/pages/onboarding/OnboardingPage'

import { ProfilePage } from './ProfilePage'
import { AccountSettingsPage, PendingFeaturePage, TermsPage } from './ProfileSubPages'
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
      />
    )
  }
  if (pathname === '/profile/guide') {
    return <OnboardingPage mode="guide" onExit={back} />
  }
  if (pathname === '/profile/terms') {
    return <TermsPage termId="service" title="서비스 이용약관" onBack={back} />
  }
  if (pathname === '/profile/privacy') {
    return (
      <PendingFeaturePage
        title="개인정보처리방침"
        description="운영용 개인정보처리방침이 확정되지 않아 임시 수집·이용 동의서를 대신 보여주지 않아요."
        onBack={back}
      />
    )
  }
  if (pathname === '/profile/marketing') {
    return (
      <PendingFeaturePage
        title="마케팅 수신동의"
        description="수신 동의 계약이 확정되지 않아 현재 상태를 조회하거나 변경하지 않아요."
        onBack={back}
      />
    )
  }
  if (pathname === '/profile/inquiry') {
    return (
      <PendingFeaturePage
        title="서비스 이용 문의하기"
        description="문의 채널이 확정되지 않아 이 화면에서 문의가 전송되지는 않아요."
        onBack={back}
      />
    )
  }
  if (pathname === '/profile/withdrawal') {
    return (
      <PendingFeaturePage
        title="회원탈퇴"
        description="탈퇴 시 데이터 처리와 재인증 계약이 확정되지 않아 계정이나 학습 기록을 변경하지 않아요."
        onBack={back}
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
      onOpenTerms={() => navigate('/profile/terms', { state: profileSubPageNavigationState })}
      onOpenPrivacy={() => navigate('/profile/privacy', { state: profileSubPageNavigationState })}
      onOpenMarketing={() => navigate('/profile/marketing', { state: profileSubPageNavigationState })}
      onOpenInquiry={() => navigate('/profile/inquiry', { state: profileSubPageNavigationState })}
      onOpenWithdrawal={() => navigate('/profile/withdrawal', { state: profileSubPageNavigationState })}
      onLogout={() => {
        if (!window.confirm('이 기기에서 로그아웃할까요?')) return
        logout.reset()
        logout.mutate(undefined, {
          onSettled: () => navigate('/', { replace: true }),
        })
      }}
      onRetry={() => void currentUser.refetch()}
    />
  )
}
