import { Box, PageBanner, Text, VStack } from '@seed-design/react'

import { HomeSectionDivider } from './components/HomePrimitives'
import {
  FirstVisitSection,
  NextActionSection,
  RecentMaterialsSection,
  RecommendationFallbackSection,
  ReviewSection,
  StudyMethodsSection,
} from './components/HomeSections'
import { FullError, HomeLoading } from './components/HomeStates'
import type { HomePageProps } from './home.types'
import './home.css'

export function HomePage(props: HomePageProps) {
  const {
    status,
    greeting,
    nextAction,
    review,
    recentMaterials,
    studyMethods,
    dataBoundaryNotice,
    recommendationWarning,
    onViewAllReviews,
    onViewAllMaterials,
    onRetryAll,
  } = props

  return (
    <VStack className="home-shell" minHeight="100dvh" bg="bg.layerBasement">
      <Box as="main" className="home-main" bg="bg.layerDefault" width="full" pt="safeArea">
        <VStack
          className="home-content"
          px="spacingX.globalGutter"
          pt="x6"
          pb="spacingY.screenBottom"
          gap="x3"
        >
          <VStack as="header" gap="x2">
            <Text as="h1" textStyle="t12Bold" color="fg.neutral">
              홈
            </Text>
            <VStack gap="x1">
              <Text as="p" textStyle="t7Bold" color="fg.neutral">
                <span aria-hidden>👋 </span>
                {greeting.nickname ? `${greeting.nickname}님, ` : ''}오늘도 반가워요
              </Text>
              {greeting.consecutiveVisitDays && greeting.consecutiveVisitDays > 0 ? (
                <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
                  연속 방문 {greeting.consecutiveVisitDays}일째예요
                </Text>
              ) : null}
            </VStack>
          </VStack>

          {dataBoundaryNotice ? (
            <PageBanner.Root tone="warning" variant="weak">
              <PageBanner.Content>
                <PageBanner.Body>
                  <PageBanner.Title>개발용 데이터</PageBanner.Title>
                  <PageBanner.Description>{dataBoundaryNotice}</PageBanner.Description>
                </PageBanner.Body>
              </PageBanner.Content>
            </PageBanner.Root>
          ) : null}

          {recommendationWarning ? (
            <PageBanner.Root tone="warning" variant="weak">
              <PageBanner.Content>
                <PageBanner.Body>
                  <PageBanner.Title>{recommendationWarning.title}</PageBanner.Title>
                  <PageBanner.Description>{recommendationWarning.description}</PageBanner.Description>
                </PageBanner.Body>
                <PageBanner.Button onClick={recommendationWarning.onRetry}>다시 시도</PageBanner.Button>
              </PageBanner.Content>
            </PageBanner.Root>
          ) : null}

          {status === 'loading' ? (
            <HomeLoading studyMethods={studyMethods} />
          ) : status === 'firstVisit' ? (
            <FirstVisitSection studyMethods={studyMethods} />
          ) : status === 'fullError' ? (
            <VStack gap="x5">
              <FullError onRetry={onRetryAll} />
              <HomeSectionDivider />
              <StudyMethodsSection items={studyMethods} />
            </VStack>
          ) : (
            <VStack gap="x5">
              {nextAction ? (
                <NextActionSection nextAction={nextAction} />
              ) : recommendationWarning ? (
                <RecommendationFallbackSection
                  onStartLearning={recommendationWarning.onStartLearning}
                />
              ) : null}
              {nextAction || recommendationWarning ? <HomeSectionDivider /> : null}
              <ReviewSection state={review} onViewAll={onViewAllReviews} />
              <HomeSectionDivider />
              <RecentMaterialsSection state={recentMaterials} onViewAll={onViewAllMaterials} />
              {recentMaterials.status !== 'empty' ? <HomeSectionDivider /> : null}
              <StudyMethodsSection items={studyMethods} />
            </VStack>
          )}
        </VStack>
      </Box>
    </VStack>
  )
}
