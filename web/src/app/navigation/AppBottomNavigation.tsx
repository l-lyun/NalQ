import {
  IconBookFill,
  IconBookLine,
  IconHouseSquareFill,
  IconHouseSquareLine,
  IconPersonCircleFill,
  IconPersonCircleLine,
} from '@karrotmarket/react-monochrome-icon'
import { Box, HStack, Icon, Text, VStack } from '@seed-design/react'

import type { AppTabId } from '../shell/appTabs'

const items = [
  { id: 'home', label: '홈', currentIcon: IconHouseSquareFill, icon: IconHouseSquareLine },
  { id: 'learning', label: '학습', currentIcon: IconBookFill, icon: IconBookLine },
  { id: 'profile', label: '프로필', currentIcon: IconPersonCircleFill, icon: IconPersonCircleLine },
] satisfies Array<{
  id: AppTabId
  label: string
  currentIcon: typeof IconHouseSquareFill
  icon: typeof IconHouseSquareLine
}>

export function AppBottomNavigation({
  activeTab,
  onNavigate,
}: {
  activeTab: AppTabId
  onNavigate: (tab: AppTabId) => void
}) {
  return (
    <Box
      as="nav"
      className="app-bottom-navigation"
      aria-label="주요 메뉴"
      bg="bg.layerDefault"
      borderTopWidth={1}
      borderColor="stroke.neutralSubtle"
      px="spacingX.globalGutter"
      pt="x2"
      pb="safeArea"
    >
      <HStack as="ul" className="app-navigation-list" width="full" gap="x2">
        {items.map((item) => {
          const current = item.id === activeTab
          const NavigationIcon = current ? item.currentIcon : item.icon
          const color = current ? 'fg.brand' : 'fg.neutralMuted'

          return (
            <Box
              as="li"
              key={item.id}
              flexGrow
              borderRadius="r2"
              bg={current ? 'bg.neutralWeak' : undefined}
            >
              <button
                className="app-navigation-button"
                type="button"
                aria-current={current ? 'page' : undefined}
                onClick={() => onNavigate(item.id)}
              >
                <VStack align="center" gap="x1">
                  <Icon svg={<NavigationIcon />} size="x6" color={color} />
                  <Text textStyle={current ? 't4Bold' : 't4Medium'} color={color}>
                    {item.label}
                  </Text>
                </VStack>
              </button>
            </Box>
          )
        })}
      </HStack>
    </Box>
  )
}
