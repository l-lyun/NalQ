import { Box, PageBanner, Text, VStack } from '@seed-design/react'

import { HomeBottomNavigation } from './components/HomeBottomNavigation'
import { HomeSectionDivider } from './components/HomePrimitives'
import {
  FirstVisitSection,
  NextActionSection,
  RecentMaterialsSection,
  ReviewSection,
  StudyMethodsSection,
  TodaySection,
} from './components/HomeSections'
import { FullError, HomeLoading } from './components/HomeStates'
import type { HomePageProps } from './home.types'
import './home.css'

export function HomePage(props: HomePageProps) {
  const {
    status,
    nextAction,
    review,
    recentMaterials,
    studyMethods,
    today,
    navigation,
    recommendationWarning,
    onViewAllReviews,
    onViewAllMaterials,
    onRetryAll,
  } = props

  return (
    <VStack minHeight="100dvh" bg="bg.layerBasement">
      <Box as="main" className="home-main" bg="bg.layerDefault" width="full" pt="safeArea">
        <VStack
          className="home-content"
          px="spacingX.globalGutter"
          pt="x6"
          pb="spacingY.screenBottom"
          gap="x3"
        >
          <Box as="header">
            <Text as="h1" textStyle="t12Bold" color="fg.neutral">
              홈
            </Text>
          </Box>

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
              <NextActionSection nextAction={nextAction} />
              {nextAction ? <HomeSectionDivider /> : null}
              <ReviewSection state={review} onViewAll={onViewAllReviews} />
              <HomeSectionDivider />
              <RecentMaterialsSection state={recentMaterials} onViewAll={onViewAllMaterials} />
              {recentMaterials.status !== 'empty' ? <HomeSectionDivider /> : null}
              <StudyMethodsSection items={studyMethods} />
              <HomeSectionDivider />
              <TodaySection state={today} />
            </VStack>
          )}
        </VStack>
      </Box>
      <HomeBottomNavigation items={navigation} />
    </VStack>
  )
}
