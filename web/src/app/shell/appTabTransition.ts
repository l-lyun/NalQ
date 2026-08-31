import type { AppTabId } from './appTabs'

export type TabTransition = {
  from: AppTabId
  to: AppTabId
}

export function getTabPanelClassNames(
  tab: AppTabId,
  activeTab: AppTabId,
  transition: TabTransition | null,
) {
  const classNames = ['app-tab-panel']
  if (!transition && tab === activeTab) classNames.push('app-tab-panel--active')
  if (transition?.to === tab) {
    classNames.push('app-tab-panel--enter', 'app-tab-panel--from-right')
  }
  if (transition?.from === tab) {
    classNames.push('app-tab-panel--exit', 'app-tab-panel--to-left')
  }
  return classNames.join(' ')
}
