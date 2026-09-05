import type { AppTabId } from './appTabs'

export type TabTransition = {
  from: AppTabId
  to: AppTabId
}

export function getTabPanelClassNames(
  tab: AppTabId,
  activeTab: AppTabId,
) {
  const classNames = ['app-tab-panel']
  if (tab === activeTab) classNames.push('app-tab-panel--active')
  return classNames.join(' ')
}
