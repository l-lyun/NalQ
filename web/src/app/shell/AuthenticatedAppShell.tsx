import { Box, Snackbar, VStack } from '@seed-design/react'
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'

import { AppBottomNavigation } from '@/app/navigation/AppBottomNavigation'
import { NotificationBell } from '@/features/notification/ui/NotificationCenter'
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
import { getTabPanelClassNames } from './appTabTransition'
import './app-shell.css'

export function AuthenticatedAppShell() {
  return <AuthenticatedAppShellContent />
}

function AuthenticatedAppShellContent() {
  const location = useLocation()
  const navigate = useNavigate()
  const activeTab = getAppTab(location.pathname)
  const pendingTabRef = useRef<AppTabId | null>(null)
  const activeTabRef = useRef(activeTab)
  const documentScrollEnabledRef = useRef(false)
  const tabScrollPositionsRef = useRef<Record<AppTabId, number>>({
    home: 0,
    learning: 0,
    profile: 0,
  })
  const [visitedTabs, setVisitedTabs] = useState<Record<AppTabId, boolean>>({
    home: activeTab === 'home',
    learning: activeTab === 'learning',
    profile: activeTab === 'profile',
  })
  const [notificationSlot, setNotificationSlot] = useState<HTMLElement | null>(null)
  const isNotificationsPage = location.pathname === '/notifications'
  const documentScrollEnabled = !isNotificationsPage && isTopLevelTabPath(location.pathname)

  useEffect(() => {
    const rememberScrollPosition = () => {
      if (!documentScrollEnabledRef.current) return
      tabScrollPositionsRef.current[activeTabRef.current] = window.scrollY
    }

    window.addEventListener('scroll', rememberScrollPosition, { passive: true })
    return () => window.removeEventListener('scroll', rememberScrollPosition)
  }, [])

  useLayoutEffect(() => {
    if (documentScrollEnabledRef.current) {
      tabScrollPositionsRef.current[activeTabRef.current] = window.scrollY
    }

    activeTabRef.current = activeTab
    documentScrollEnabledRef.current = documentScrollEnabled
    const nextScrollTop = documentScrollEnabled ? tabScrollPositionsRef.current[activeTab] : 0
    window.scrollTo(0, nextScrollTop)
  }, [activeTab, documentScrollEnabled])

  useLayoutEffect(() => {
    setVisitedTabs((current) => current[activeTab] ? current : { ...current, [activeTab]: true })
    pendingTabRef.current = null
  }, [activeTab])

  useLayoutEffect(() => {
    if (isNotificationsPage) {
      setNotificationSlot(null)
      return
    }

    setNotificationSlot(
      document.querySelector<HTMLElement>('.app-tab-panel--active [data-app-notification-slot]'),
    )
  }, [activeTab, isNotificationsPage, location.pathname, visitedTabs])

  const navigateToTab = (tab: AppTabId) => {
    if (tab === activeTab && isTopLevelTabPath(location.pathname)) return
    if (pendingTabRef.current === tab) return
    const replace = pendingTabRef.current !== null
    pendingTabRef.current = tab
    setVisitedTabs((current) => current[tab] ? current : { ...current, [tab]: true })
    navigate(appTabPaths[tab], { replace })
  }

  return (
    <VStack
      className={`app-shell${documentScrollEnabled ? ' app-shell--document-scroll' : ''}`}
      minHeight="100dvh"
      bg="bg.layerBasement"
    >
      {isNotificationsPage ? (
        <Box className="app-tab-viewport"><NotificationsPage /><Outlet /></Box>
      ) : (
        <>
          <Box className="app-tab-viewport">
            {visitedTabs.home ? <TabPanel tab="home" activeTab={activeTab}><AuthenticatedHomePage /></TabPanel> : null}
            {visitedTabs.learning ? <TabPanel tab="learning" activeTab={activeTab}><AuthenticatedLearningPage /></TabPanel> : null}
            {visitedTabs.profile ? <TabPanel tab="profile" activeTab={activeTab}><AuthenticatedProfilePage /></TabPanel> : null}
            <Outlet />
          </Box>
          {notificationSlot ? createPortal(<NotificationBell />, notificationSlot) : null}
          {documentScrollEnabled ? (
            <Snackbar.AvoidOverlap>
              <div className="app-bottom-navigation-boundary"><AppBottomNavigation activeTab={activeTab} onNavigate={navigateToTab} /></div>
            </Snackbar.AvoidOverlap>
          ) : null}
        </>
      )}
    </VStack>
  )
}

function TabPanel({ tab, activeTab, children }: {
  tab: AppTabId
  activeTab: AppTabId
  children: React.ReactNode
}) {
  const interactive = tab === activeTab

  return <section className={getTabPanelClassNames(tab, activeTab)} aria-hidden={!interactive} inert={!interactive} data-app-tab={tab}>{children}</section>
}
