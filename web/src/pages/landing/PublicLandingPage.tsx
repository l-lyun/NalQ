import { ActionButton, Box, Flex, Text, VStack } from '@seed-design/react'
import { useNavigate } from 'react-router-dom'

import { PublicServiceFooter } from '@/pages/public-service/PublicServiceFooter'

import './public-landing.css'

export function PublicLandingPage() {
  const navigate = useNavigate()

  return (
    <VStack className="public-landing" minHeight="100dvh" bg="bg.layerDefault">
      <Flex
        as="header"
        className="public-landing-header"
        width="full"
        align="center"
        justify="space-between"
        px="spacingX.globalGutter"
        pt="safeArea"
      >
        <Text textStyle="t8Bold" color="fg.neutral">NalQ</Text>
        <ActionButton type="button" size="small" variant="ghost" onClick={() => navigate('/login')}>
          로그인
        </ActionButton>
      </Flex>

      <Box as="main" className="public-landing-main" width="full" px="spacingX.globalGutter">
        <VStack className="public-landing-hero" align="flex-start" gap="x6">
          <VStack align="flex-start" gap="x3">
            <Text as="p" textStyle="t5Medium" color="fg.brand">
              읽은 내용을 내 것으로 만드는 방법 ✍️
            </Text>
            <Text as="h1" textStyle="t12Bold" color="fg.neutral">
              읽은 것을 문제로 풀어보면 더 오래 남아요.
            </Text>
            <Text as="p" textStyle="t6Regular" color="fg.neutralMuted">
              노션에 정리해둔 글이나 텍스트를 복사해서 가져오세요.
            </Text>
          </VStack>

          <VStack width="full" gap="x2">
            <ActionButton
              className="public-landing-primary"
              type="button"
              size="large"
              variant="brandSolid"
              onClick={() => navigate('/sign-up')}
            >
              가입하고 시작하기
            </ActionButton>
            <ActionButton
              className="public-landing-login"
              type="button"
              size="medium"
              variant="ghost"
              onClick={() => navigate('/login')}
            >
              이미 계정이 있나요? 로그인
            </ActionButton>
          </VStack>
        </VStack>
      </Box>
      <PublicServiceFooter />
    </VStack>
  )
}
