import {
  IconBookFill,
  IconHouseSquareLine,
  IconPersonCircleLine,
} from '@karrotmarket/react-monochrome-icon'
import { Box, HStack, Icon, Text, VStack } from '@seed-design/react'

import type { LearningNavigationDestination } from '../learning.types'

const items: Array<{
  id: LearningNavigationDestination
  label: string
  icon: typeof IconBookFill
  disabled?: boolean
}> = [
  { id: 'home', label: '홈', icon: IconHouseSquareLine },
  { id: 'learning', label: '학습', icon: IconBookFill },
  // TODO: 프로필 라우트가 추가되면 disabled를 제거하고 라우팅 콜백을 연결한다.
  { id: 'profile', label: '프로필', icon: IconPersonCircleLine, disabled: true },
]

export function LearningBottomNavigation({
  onNavigate,
}: {
  onNavigate: (destination: LearningNavigationDestination) => void
}) {
  return (
    <Box
      as="nav"
      className="learning-bottom-navigation"
      aria-label="주요 메뉴"
      bg="bg.layerDefault"
      borderTopWidth={1}
      borderColor="stroke.neutralSubtle"
      px="spacingX.globalGutter"
      pt="x2"
      pb="safeArea"
    >
      <HStack as="ul" className="learning-navigation-list" width="full" gap="x2">
        {items.map((item) => {
          const current = item.id === 'learning'
          const color = current ? 'fg.brand' : 'fg.neutralMuted'
          const NavigationIcon = item.icon

          return (
            <Box
              as="li"
              key={item.id}
              flexGrow
              borderRadius="r2"
              bg={current ? 'bg.neutralWeak' : undefined}
            >
              <button
                className="learning-navigation-button"
                type="button"
                disabled={item.disabled}
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
