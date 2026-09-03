import { Box, Snackbar, VStack } from '@seed-design/react'
import { useLayoutEffect, useRef, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'

import { AppBottomNavigation } from '@/app/navigation/AppBottomNavigation'
import { NotificationBell, NotificationCenterProvider } from '@/features/notification/ui/NotificationCenter'
import { AuthenticatedHomePage } from '@/pages/home/AuthenticatedHomePage'
import { AuthenticatedLearningPage } from '@/pages/learning/AuthenticatedLearningPage'
import { NotificationsPage } from '@/pages/notifications/NotificationsPage'
import { AuthenticatedProfilePage } from '@/pages/profile/AuthenticatedProfilePage'

import {
  appTabPaths,
  getAppTab,
  isTopLevelTabPath,
  type AppTabId,
} from './appTabs'
import { getTabPanelClassNames, type TabTransition } from './appTabTransition'
import './app-shell.css'

const transitionDurationMs = 210

export function AuthenticatedAppShell() {
  return <NotificationCenterProvider><AuthenticatedAppShellContent /></NotificationCenterProvider>
}

function AuthenticatedAppShellContent() {
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
  const isNotificationsPage = location.pathname === '/notifications'

  useLayoutEffect(() => {
    setVisitedTabs((current) => current[activeTab] ? current : { ...current, [activeTab]: true })
    const previousTab = previousTabRef.current
    if (previousTab === activeTab) return

    if (transitionTimerRef.current !== null) window.clearTimeout(transitionTimerRef.current)
    setTransition({
      from: previousTab,
      to: activeTab,
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
      {isNotificationsPage ? (
        <Box className="app-tab-viewport"><NotificationsPage /><Outlet /></Box>
      ) : (
        <>
          <Box className="app-tab-viewport">
            {visitedTabs.home ? <TabPanel tab="home" activeTab={activeTab} transition={transition}><AuthenticatedHomePage /></TabPanel> : null}
            {visitedTabs.learning ? <TabPanel tab="learning" activeTab={activeTab} transition={transition}><AuthenticatedLearningPage /></TabPanel> : null}
            {visitedTabs.profile ? <TabPanel tab="profile" activeTab={activeTab} transition={transition}><AuthenticatedProfilePage /></TabPanel> : null}
            <Outlet />
          </Box>
          <div className="app-notification-utility"><NotificationBell /></div>
          {isTopLevelTabPath(location.pathname) ? (
            <Snackbar.AvoidOverlap>
              <div className="app-bottom-navigation-boundary"><AppBottomNavigation activeTab={activeTab} onNavigate={navigateToTab} /></div>
            </Snackbar.AvoidOverlap>
          ) : null}
        </>
      )}
    </VStack>
  )
}

function TabPanel({ tab, activeTab, transition, children }: {
  tab: AppTabId
  activeTab: AppTabId
  transition: TabTransition | null
  children: React.ReactNode
}) {
  const interactive = tab === activeTab

  return <section className={getTabPanelClassNames(tab, activeTab, transition)} aria-hidden={!interactive} inert={!interactive} data-app-tab={tab}>{children}</section>
}
