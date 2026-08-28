export const appTabPaths = {
  home: '/',
  learning: '/learning',
  profile: '/profile',
} as const

export type AppTabId = keyof typeof appTabPaths

export const appTabOrder: Record<AppTabId, number> = {
  home: 0,
  learning: 1,
  profile: 2,
}

export function getAppTab(pathname: string): AppTabId {
  const normalizedPathname = normalizeAppPath(pathname)
  if (normalizedPathname === '/profile') return 'profile'
  if (normalizedPathname === '/learning' || normalizedPathname.startsWith('/learning/')) return 'learning'
  return 'home'
}

export function isTopLevelTabPath(pathname: string) {
  const normalizedPathname = normalizeAppPath(pathname)
  return Object.values(appTabPaths).some((path) => path === normalizedPathname)
}

function normalizeAppPath(pathname: string) {
  return pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname
}
