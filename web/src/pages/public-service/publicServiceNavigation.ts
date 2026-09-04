export function readPublicReturnPath(state: unknown) {
  if (!state || typeof state !== 'object') return '/login'
  const candidate = (state as { returnTo?: unknown }).returnTo
  if (typeof candidate !== 'string' || !candidate.startsWith('/') || candidate.startsWith('//')) {
    return '/login'
  }
  return candidate
}

export function getPublicBackLabel(returnTo: string) {
  if (returnTo === '/profile') return '마이페이지로 돌아가기'
  if (returnTo === '/login') return '로그인 화면으로 돌아가기'
  return '이전 화면으로 돌아가기'
}
