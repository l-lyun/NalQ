import { IconChevronRightLine } from '@karrotmarket/react-monochrome-icon'
import { ActionButton, Divider, Flex, Icon, List, Text, VStack } from '@seed-design/react'
import { Fragment } from 'react'

import type { HomeListItem } from '../home.types'

type SectionHeaderProps = {
  id: string
  title: string
  actionLabel?: string
  onAction?: () => void
}

export function HomeSectionDivider() {
  return <Divider as="div" color="stroke.neutralSubtle" />
}

export function SectionHeader({ id, title, actionLabel, onAction }: SectionHeaderProps) {
  return (
    <Flex align="center" justify="space-between" gap="x3" wrap>
      <Text as="h2" id={id} textStyle="t7Bold" color="fg.neutral">
        {title}
      </Text>
      {actionLabel && onAction ? (
        <ActionButton
          size="small"
          variant="ghost"
          color="fg.neutralMuted"
          fontWeight="medium"
          bleed="asPadding"
          onClick={onAction}
        >
          {actionLabel}
        </ActionButton>
      ) : null}
    </Flex>
  )
}

export function InteractiveList({ items, label }: { items: HomeListItem[]; label: string }) {
  return (
    <List.Root
      className="home-list"
      aria-label={label}
      width="full"
      borderWidth={1}
      borderColor="stroke.neutralSubtle"
      borderRadius="r3"
      itemBorderRadius="r2_5"
    >
      {items.map((item, index) => {
        const disabledState = item.disabled ? '' : undefined

        return (
          <Fragment key={item.id}>
            <List.Item alignItems="flex-start">
              <List.Content asChild gap="x1_5">
                <button
                  className="home-list-button"
                  type="button"
                  disabled={item.disabled}
                  onClick={item.onClick}
                >
                  <VStack minWidth="0px" flexGrow gap="x1_5" align="flex-start">
                    <List.Title data-disabled={disabledState}>{item.title}</List.Title>
                    <List.Detail data-disabled={disabledState}>{item.detail}</List.Detail>
                  </VStack>
                  <List.Suffix data-disabled={disabledState}>
                    <Icon svg={<IconChevronRightLine />} size="x4_5" />
                  </List.Suffix>
                </button>
              </List.Content>
            </List.Item>
            {index < items.length - 1 ? (
              <Divider as="li" aria-hidden color="stroke.neutralSubtle" inset />
            ) : null}
          </Fragment>
        )
      })}
    </List.Root>
  )
}

export function InlineError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <VStack gap="x2" align="flex-start" py="x2">
      <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
        {message}
      </Text>
      <ActionButton
        size="small"
        variant="ghost"
        color="fg.neutralMuted"
        fontWeight="medium"
        bleed="asPadding"
        onClick={onRetry}
      >
        다시 시도
      </ActionButton>
    </VStack>
  )
}
