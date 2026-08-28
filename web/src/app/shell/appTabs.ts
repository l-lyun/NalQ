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
  if (pathname === '/profile') return 'profile'
  if (pathname === '/learning' || pathname.startsWith('/learning/')) return 'learning'
  return 'home'
}

export function isTopLevelTabPath(pathname: string) {
  return Object.values(appTabPaths).some((path) => path === pathname)
}
