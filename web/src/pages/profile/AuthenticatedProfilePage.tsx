import packageMetadata from '../../../package.json'

import { useLocation, useNavigate } from 'react-router-dom'

import { useLogoutMutation } from '@/features/auth/model/auth.mutations'
import { useCurrentUser } from '@/features/auth/model/auth.queries'

import { ProfilePage } from './ProfilePage'
import { AccountSettingsPage, PendingFeaturePage, TermsPage } from './ProfileSubPages'

export function AuthenticatedProfilePage() {
  const location = useLocation()
  const navigate = useNavigate()
  const currentUser = useCurrentUser()
  const logout = useLogoutMutation()
  const status = currentUser.isPending && !currentUser.data
    ? 'loading'
    : currentUser.isError && !currentUser.data
      ? 'error'
      : 'ready'
  const back = () => {
    if (location.key === 'default') navigate('/profile', { replace: true })
    else navigate(-1)
  }

  if (location.pathname === '/profile/account') {
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
  if (location.pathname === '/profile/terms') {
    return <TermsPage termId="service" title="서비스 이용약관" onBack={back} />
  }
  if (location.pathname === '/profile/privacy') {
    return <TermsPage termId="privacy" title="개인정보처리방침" onBack={back} />
  }
  if (location.pathname === '/profile/marketing') {
    return (
      <PendingFeaturePage
        title="마케팅 수신동의"
        description="수신 동의 계약이 확정되지 않아 현재 상태를 조회하거나 변경하지 않아요."
        onBack={back}
      />
    )
  }
  if (location.pathname === '/profile/inquiry') {
    return (
      <PendingFeaturePage
        title="서비스 이용 문의하기"
        description="문의 채널이 확정되지 않아 이 화면에서 문의가 전송되지는 않아요."
        onBack={back}
      />
    )
  }
  if (location.pathname === '/profile/withdrawal') {
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
      onOpenAccount={() => navigate('/profile/account')}
      onOpenTerms={() => navigate('/profile/terms')}
      onOpenPrivacy={() => navigate('/profile/privacy')}
      onOpenMarketing={() => navigate('/profile/marketing')}
      onOpenInquiry={() => navigate('/profile/inquiry')}
      onOpenWithdrawal={() => navigate('/profile/withdrawal')}
      onLogout={() => {
        if (!window.confirm('이 기기에서 로그아웃할까요?')) return
        logout.reset()
        logout.mutate()
      }}
      onRetry={() => void currentUser.refetch()}
    />
  )
}
