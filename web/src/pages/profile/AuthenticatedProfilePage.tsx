import packageMetadata from '../../../package.json'

import { useLogoutMutation } from '@/features/auth/model/auth.mutations'
import { useCurrentUser } from '@/features/auth/model/auth.queries'

import { ProfilePage } from './ProfilePage'

export function AuthenticatedProfilePage() {
  const currentUser = useCurrentUser()
  const logout = useLogoutMutation()

  return (
    <ProfilePage
      status={currentUser.isPending && !currentUser.data ? 'loading' : currentUser.isError && !currentUser.data ? 'error' : 'ready'}
      nickname={currentUser.data?.nickname}
      email={currentUser.data?.email}
      appVersion={import.meta.env.VITE_APP_VERSION?.trim() || packageMetadata.version}
      logoutPending={logout.isPending}
      logoutError={logout.isError ? '로그아웃하지 못했어요. 다시 시도해주세요.' : undefined}
      onLogout={() => { logout.reset(); logout.mutate() }}
      onRetry={() => void currentUser.refetch()}
    />
  )
}
