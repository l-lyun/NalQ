import { Box, VStack } from '@seed-design/react'
import { useLayoutEffect, useRef, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'

import { AppBottomNavigation } from '@/app/navigation/AppBottomNavigation'
import { AuthenticatedHomePage } from '@/pages/home/AuthenticatedHomePage'
import { AuthenticatedLearningPage } from '@/pages/learning/AuthenticatedLearningPage'
import { AuthenticatedProfilePage } from '@/pages/profile/AuthenticatedProfilePage'

import {
  appTabOrder,
  appTabPaths,
  getAppTab,
  isTopLevelTabPath,
  type AppTabId,
} from './appTabs'
import './app-shell.css'

type TabTransition = {
  from: AppTabId
  to: AppTabId
  direction: 'left' | 'right'
}

const transitionDurationMs = 210

export function AuthenticatedAppShell() {
  const location = useLocation()
  const navigate = useNavigate()
  const activeTab = getAppTab(location.pathname)
  const previousTabRef = useRef(activeTab)
  const pendingTabRef = useRef<AppTabId | null>(null)
  const transitionTimerRef = useRef<number | null>(null)
  const [transition, setTransition] = useState<TabTransition | null>(null)
  const [visitedTabs, setVisitedTabs] = useState<Record<AppTabId, boolean>>({
    home: activeTab === 'home',
    learning: activeTab === 'learning',
    profile: activeTab === 'profile',
  })

  useLayoutEffect(() => {
    setVisitedTabs((current) => current[activeTab] ? current : { ...current, [activeTab]: true })
    const previousTab = previousTabRef.current
    if (previousTab === activeTab) return

    if (transitionTimerRef.current !== null) window.clearTimeout(transitionTimerRef.current)
    setTransition({
      from: previousTab,
      to: activeTab,
      direction: appTabOrder[activeTab] > appTabOrder[previousTab] ? 'left' : 'right',
    })
    previousTabRef.current = activeTab
    transitionTimerRef.current = window.setTimeout(() => {
      setTransition(null)
      pendingTabRef.current = null
      transitionTimerRef.current = null
    }, transitionDurationMs)
  }, [activeTab])

  const navigateToTab = (tab: AppTabId) => {
    if (tab === activeTab && isTopLevelTabPath(location.pathname)) return
    if (pendingTabRef.current === tab) return
    const replace = pendingTabRef.current !== null
    pendingTabRef.current = tab
    setVisitedTabs((current) => current[tab] ? current : { ...current, [tab]: true })
    navigate(appTabPaths[tab], { replace })
  }

  return (
    <VStack className="app-shell" minHeight="100dvh" bg="bg.layerBasement">
      <Box className="app-tab-viewport">
        {visitedTabs.home ? <TabPanel tab="home" activeTab={activeTab} transition={transition}><AuthenticatedHomePage /></TabPanel> : null}
        {visitedTabs.learning ? <TabPanel tab="learning" activeTab={activeTab} transition={transition}><LearningTabRouteHost pathname={location.pathname} /></TabPanel> : null}
        {visitedTabs.profile ? <TabPanel tab="profile" activeTab={activeTab} transition={transition}><AuthenticatedProfilePage /></TabPanel> : null}
        <Outlet />
      </Box>
      {isTopLevelTabPath(location.pathname) ? <AppBottomNavigation activeTab={activeTab} onNavigate={navigateToTab} /> : null}
    </VStack>
  )
}

function LearningTabRouteHost({ pathname }: { pathname: string }) {
  const [ready, setReady] = useState(false)

  useLayoutEffect(() => {
    const learningEntry = getLearningHistoryEntry(pathname)
    if (!learningEntry) return
    const currentState = window.history.state as Record<string, unknown> | null
    window.history.replaceState({ ...currentState, openmdLearning: learningEntry }, '')
    setReady(true)
  }, [])

  useLayoutEffect(() => {
    if (!ready) return
    const learningEntry = getLearningHistoryEntry(pathname)
    if (!learningEntry) return
    const currentState = window.history.state as Record<string, unknown> | null
    const nextState = { ...currentState, openmdLearning: learningEntry }
    window.history.replaceState(nextState, '')
    window.dispatchEvent(new PopStateEvent('popstate', { state: nextState }))
  }, [pathname, ready])

  return ready ? <AuthenticatedLearningPage /> : null
}

function getLearningHistoryEntry(pathname: string) {
  const materialMatch = pathname.match(/^\/learning\/materials\/([^/]+)$/)
  if (materialMatch?.[1]) {
    return {
      screen: { id: 'material-detail', materialId: decodeURIComponent(materialMatch[1]) },
      depth: 1,
    }
  }
  if (pathname === '/learning/new') return { screen: { id: 'new-quiz' }, depth: 1 }
  if (pathname === '/learning' || pathname === '/learning/materials' || pathname === '/learning/quizzes') {
    return { screen: { id: 'main' }, depth: 0 }
  }
  return null
}

function TabPanel({ tab, activeTab, transition, children }: {
  tab: AppTabId
  activeTab: AppTabId
  transition: TabTransition | null
  children: React.ReactNode
}) {
  const entering = transition?.to === tab
  const exiting = transition?.from === tab
  const classNames = ['app-tab-panel']
  if (!transition && tab === activeTab) classNames.push('app-tab-panel--active')
  if (entering) classNames.push('app-tab-panel--enter', transition.direction === 'left' ? 'app-tab-panel--from-right' : 'app-tab-panel--from-left')
  if (exiting) classNames.push('app-tab-panel--exit', transition.direction === 'left' ? 'app-tab-panel--to-left' : 'app-tab-panel--to-right')
  const interactive = tab === activeTab

  return <section className={classNames.join(' ')} aria-hidden={!interactive} inert={!interactive} data-app-tab={tab}>{children}</section>
}
