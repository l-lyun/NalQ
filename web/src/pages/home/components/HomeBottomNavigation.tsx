import {
  IconBookFill,
  IconBookLine,
  IconHouseSquareFill,
  IconHouseSquareLine,
  IconPersonCircleFill,
  IconPersonCircleLine,
} from '@karrotmarket/react-monochrome-icon'
import { Box, HStack, Icon, Text, VStack } from '@seed-design/react'

import type { HomeNavigationItem } from '../home.types'

const navigationIcons = {
  home: { current: IconHouseSquareFill, default: IconHouseSquareLine },
  learning: { current: IconBookFill, default: IconBookLine },
  profile: { current: IconPersonCircleFill, default: IconPersonCircleLine },
} satisfies Record<
  HomeNavigationItem['id'],
  { current: typeof IconHouseSquareFill; default: typeof IconHouseSquareLine }
>

export function HomeBottomNavigation({ items }: { items: HomeNavigationItem[] }) {
  return (
    <Box
      as="nav"
      className="home-bottom-navigation"
      aria-label="주요 메뉴"
      bg="bg.layerDefault"
      borderTopWidth={1}
      borderColor="stroke.neutralSubtle"
      px="spacingX.globalGutter"
      pt="x2"
      pb="safeArea"
    >
      <HStack as="ul" className="home-navigation-list" width="full" gap="x2">
        {items.map((item) => {
          const NavigationIcon = item.current
            ? navigationIcons[item.id].current
            : navigationIcons[item.id].default
          const color = item.current ? 'fg.brand' : 'fg.neutralMuted'

          return (
            <Box
              as="li"
              key={item.id}
              flexGrow
              borderRadius="r2"
              bg={item.current ? 'bg.neutralWeak' : undefined}
            >
              <button
                className="home-navigation-button"
                type="button"
                aria-current={item.current ? 'page' : undefined}
                onClick={item.onClick}
              >
                <VStack align="center" gap="x1">
                  <Icon svg={<NavigationIcon />} size="x6" color={color} />
                  <Text textStyle={item.current ? 't4Bold' : 't4Medium'} color={color}>
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
