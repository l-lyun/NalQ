import { ActionButton, Skeleton, Text, VStack } from '@seed-design/react'

import type { HomePageProps } from '../home.types'
import { HomeSectionDivider } from './HomePrimitives'
import { StudyMethodsSection } from './HomeSections'

export function HomeLoading({ studyMethods }: Pick<HomePageProps, 'studyMethods'>) {
  return (
    <VStack gap="x5" aria-busy="true" aria-label="홈 정보를 불러오는 중">
      <VStack bg="bg.neutralWeak" borderRadius="r3" p="x4" gap="x3">
        <Skeleton tone="neutral" radius="8" width="80%" height="x6" />
        <Skeleton tone="neutral" radius="8" width="full" height="x4" />
        <Skeleton tone="neutral" radius="16" width="full" height="x12" />
      </VStack>
      <HomeSectionDivider />
      <VStack gap="x3">
        <Skeleton tone="neutral" radius="8" width="x12" height="x6" />
        <Skeleton tone="neutral" radius="8" width="full" height="x12" />
      </VStack>
      <HomeSectionDivider />
      <VStack gap="x3">
        <Skeleton tone="neutral" radius="8" width="x16" height="x6" />
        <Skeleton tone="neutral" radius="8" width="full" height="x16" />
      </VStack>
      <HomeSectionDivider />
      <StudyMethodsSection items={studyMethods} />
    </VStack>
  )
}

export function FullError({ onRetry }: { onRetry: () => void }) {
  return (
    <VStack
      as="section"
      minHeight="320px"
      align="center"
      justify="center"
      gap="x4"
      aria-labelledby="home-error-title"
    >
      <VStack gap="spacingY.betweenText" align="center">
        <Text as="h2" id="home-error-title" textStyle="t7Bold" color="fg.neutral" align="center">
          홈 정보를 불러오지 못했어요
        </Text>
        <Text as="p" textStyle="t5Regular" color="fg.neutralMuted" align="center">
          잠시 후 다시 시도해주세요. 새 학습은 바로 시작할 수 있어요.
        </Text>
      </VStack>
      <ActionButton size="large" variant="neutralSolid" onClick={onRetry}>
        다시 시도
      </ActionButton>
    </VStack>
  )
}
