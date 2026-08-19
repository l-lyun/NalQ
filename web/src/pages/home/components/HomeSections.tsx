import { ActionButton, Flex, Text, VStack } from '@seed-design/react'

import type {
  HomeListItem,
  HomePageProps,
  HomeSectionState,
  HomeTodaySummary,
} from '../home.types'
import { InlineError, InteractiveList, SectionHeader } from './HomePrimitives'

export function ReviewSection({
  state,
  onViewAll,
}: {
  state: HomeSectionState<HomeListItem>
  onViewAll: () => void
}) {
  return (
    <VStack as="section" gap="x3" aria-labelledby="home-review-title">
      <SectionHeader
        id="home-review-title"
        title="복습"
        actionLabel="전체 보기"
        onAction={onViewAll}
      />
      {state.status === 'ready' ? (
        <InteractiveList label="복습 요약" items={[state.data]} />
      ) : state.status === 'error' ? (
        <InlineError message={state.message} onRetry={state.onRetry} />
      ) : (
        <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
          {state.message}
        </Text>
      )}
    </VStack>
  )
}

export function RecentMaterialsSection({
  state,
  onViewAll,
}: {
  state: HomeSectionState<HomeListItem[]>
  onViewAll: () => void
}) {
  if (state.status === 'empty') return null

  return (
    <VStack as="section" gap="x3" aria-labelledby="home-recent-title">
      <SectionHeader
        id="home-recent-title"
        title="최근 학습자료"
        actionLabel="전체 보기"
        onAction={onViewAll}
      />
      {state.status === 'ready' ? (
        <InteractiveList label="최근 학습자료" items={state.data} />
      ) : (
        <InlineError message={state.message} onRetry={state.onRetry} />
      )}
    </VStack>
  )
}

export function StudyMethodsSection({
  items,
  compact = false,
}: {
  items: HomeListItem[]
  compact?: boolean
}) {
  return (
    <VStack
      as={compact ? 'div' : 'section'}
      gap="x3"
      aria-labelledby={compact ? undefined : 'home-new-study-title'}
    >
      <Text
        as={compact ? 'h3' : 'h2'}
        id={compact ? undefined : 'home-new-study-title'}
        textStyle={compact ? 't8Bold' : 't10Bold'}
        color="fg.neutral"
      >
        {compact ? '가져올 방법을 선택하세요' : '새 학습'}
      </Text>
      <InteractiveList label="새 학습 가져오기 방법" items={items} />
    </VStack>
  )
}

export function TodaySection({ state }: { state: HomeSectionState<HomeTodaySummary> }) {
  return (
    <VStack as="section" gap="x3" aria-labelledby="home-today-title">
      <SectionHeader id="home-today-title" title="오늘의 학습" />
      {state.status === 'ready' ? (
        <Flex as="dl" className="home-metrics" gap="x6" wrap>
          <VStack gap="x1">
            <Text as="dt" textStyle="t4Regular" color="fg.neutralMuted">
              푼 문제
            </Text>
            <Text as="dd" className="home-metric-value" textStyle="t7Bold" color="fg.neutral">
              {state.data.solvedCount}문제
            </Text>
          </VStack>
          <VStack gap="x1">
            <Text as="dt" textStyle="t4Regular" color="fg.neutralMuted">
              정답 현황
            </Text>
            <Text as="dd" className="home-metric-value" textStyle="t7Bold" color="fg.neutral">
              정답 {state.data.correctCount} / 채점 {state.data.gradedCount}
            </Text>
          </VStack>
        </Flex>
      ) : state.status === 'error' ? (
        <InlineError message={state.message} onRetry={state.onRetry} />
      ) : (
        <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
          {state.message}
        </Text>
      )}
    </VStack>
  )
}

export function NextActionSection({ nextAction }: Pick<HomePageProps, 'nextAction'>) {
  if (!nextAction) return null

  return (
    <VStack as="section" gap="x4" aria-labelledby="home-next-action-title">
      <VStack bg="bg.neutralWeak" borderRadius="r3" p="x5" gap="x4">
        <VStack gap="spacingY.betweenText">
          <Text as="h2" id="home-next-action-title" textStyle="t8Bold" color="fg.neutral">
            {nextAction.title}
          </Text>
          <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
            {nextAction.description}
          </Text>
          <Text as="p" textStyle="t3Regular" color="fg.neutralSubtle">
            {nextAction.context}
          </Text>
        </VStack>
        <Flex width="full">
          <ActionButton
            flexGrow
            size="large"
            variant="brandSolid"
            loading={nextAction.action.loading}
            disabled={nextAction.action.loading}
            onClick={nextAction.action.onClick}
          >
            {nextAction.action.label}
          </ActionButton>
        </Flex>
      </VStack>
    </VStack>
  )
}

export function RecommendationFallbackSection({
  onStartLearning,
}: {
  onStartLearning: () => void
}) {
  return (
    <VStack as="section" gap="x4" aria-labelledby="home-recommendation-fallback-title">
      <VStack bg="bg.neutralWeak" borderRadius="r3" p="x5" gap="x4">
        <VStack gap="spacingY.betweenText">
          <Text
            as="h2"
            id="home-recommendation-fallback-title"
            textStyle="t8Bold"
            color="fg.neutral"
          >
            새 학습을 시작해보세요
          </Text>
          <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
            학습자료를 가져와 바로 시작할 수 있어요.
          </Text>
        </VStack>
        <Flex width="full">
          <ActionButton flexGrow size="large" variant="brandSolid" onClick={onStartLearning}>
            학습 시작
          </ActionButton>
        </Flex>
      </VStack>
    </VStack>
  )
}

export function FirstVisitSection({ studyMethods }: Pick<HomePageProps, 'studyMethods'>) {
  return (
    <VStack as="section" gap="x4" aria-labelledby="home-first-title">
      <VStack bg="bg.neutralWeak" borderRadius="r3" p="x5" gap="x5">
        <VStack gap="spacingY.betweenText">
          <Text as="h2" id="home-first-title" textStyle="t8Bold" color="fg.neutral">
            학습할 글을 가져와 시작해보세요
          </Text>
          <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
            Notion, .txt/.md 파일, 복사한 텍스트를 사용할 수 있어요.
          </Text>
        </VStack>
        <StudyMethodsSection items={studyMethods} compact />
      </VStack>
    </VStack>
  )
}
