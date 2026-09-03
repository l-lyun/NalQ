import { IconArrowLeftLine } from '@karrotmarket/react-monochrome-icon'
import { ActionButton, Box, Flex, Icon, Text, VisuallyHidden, VStack } from '@seed-design/react'
import { useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'

import './onboarding.css'

const slides = [
  {
    title: '읽은 글로 바로 문제를 만들어요',
    description: '노션에 정리해 둔 글이나 텍스트를 가져오면, 그 내용에 맞는 문제를 만들 수 있어요.',
    placeholder: '학습자료에서 문제로 이어지는 앱 화면',
  },
  {
    title: '공부할 방식은 직접 골라요',
    description: '문제 유형과 난이도, 문제 수를 원하는 대로 정할 수 있어요.',
    placeholder: '문제 유형·난이도·문제 수 선택 앱 화면',
  },
  {
    title: '헷갈린 부분만 다시 보면 돼요',
    description: '결과와 관련 내용을 함께 확인하고, 틀린 문제만 모아 다시 풀어보세요.',
    placeholder: '결과와 다시 보기 앱 화면',
  },
] as const

type OnboardingPageProps = {
  mode: 'automatic' | 'guide'
  onExit: () => void
}

type SwipeStart = {
  pointerId: number
  x: number
  y: number
}

export function OnboardingPage({ mode, onExit }: OnboardingPageProps) {
  const [index, setIndex] = useState(0)
  const swipeStart = useRef<SwipeStart | null>(null)
  const slide = slides[index]
  const previousAvailable = index > 0
  const nextAvailable = index < slides.length - 1

  const move = (direction: -1 | 1) => {
    setIndex((current) => Math.max(0, Math.min(slides.length - 1, current + direction)))
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key === 'ArrowLeft' && previousAvailable) {
      event.preventDefault()
      move(-1)
    }
    if (event.key === 'ArrowRight' && nextAvailable) {
      event.preventDefault()
      move(1)
    }
  }

  const handlePointerDown = (event: PointerEvent<HTMLElement>) => {
    swipeStart.current = { pointerId: event.pointerId, x: event.clientX, y: event.clientY }
  }

  const handlePointerUp = (event: PointerEvent<HTMLElement>) => {
    const start = swipeStart.current
    swipeStart.current = null
    if (!start || start.pointerId !== event.pointerId) return

    const deltaX = event.clientX - start.x
    const deltaY = event.clientY - start.y
    if (Math.abs(deltaX) < 48 || Math.abs(deltaX) <= Math.abs(deltaY) * 1.2) return
    move(deltaX < 0 ? 1 : -1)
  }

  return (
    <VStack className="onboarding-page" minHeight="100dvh" bg="bg.layerDefault">
      <Flex
        as="header"
        className="onboarding-header"
        align="center"
        gap="x2"
        px="spacingX.globalGutter"
        pt="safeArea"
      >
        {mode === 'guide' ? (
          <ActionButton
            type="button"
            size="small"
            variant="ghost"
            layout="iconOnly"
            aria-label="마이페이지로 돌아가기"
            onClick={onExit}
          >
            <Icon svg={<IconArrowLeftLine />} size="x5" />
          </ActionButton>
        ) : null}
        <Text textStyle={mode === 'guide' ? 't10Bold' : 't8Bold'} color="fg.neutral">
          {mode === 'guide' ? 'NalQ 가이드' : 'NalQ'}
        </Text>
        <VisuallyHidden><h1>NalQ 시작 가이드</h1></VisuallyHidden>
      </Flex>

      <Box
        as="main"
        className="onboarding-content"
        px="spacingX.globalGutter"
      >
        <VStack
          as="section"
          className="onboarding-carousel"
          aria-label="NalQ 시작 가이드"
          aria-roledescription="캐러셀"
          tabIndex={0}
          onKeyDown={handleKeyDown}
          onPointerDown={handlePointerDown}
          onPointerUp={handlePointerUp}
          onPointerCancel={() => { swipeStart.current = null }}
        >
          <VStack key={slide.title} className="onboarding-slide" gap="x6">
            <Box className="onboarding-capture-slot" bg="bg.neutralWeak" borderRadius="r4" aria-hidden>
              <Text textStyle="t4Medium" color="fg.neutralMuted" align="center">
                {slide.placeholder}
                <br />
                <span className="onboarding-capture-caption">앱 화면 캡처 준비 중</span>
              </Text>
            </Box>
            <VStack align="flex-start" gap="x3">
              <Text as="h2" textStyle="t10Bold" color="fg.neutral">{slide.title}</Text>
              <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">{slide.description}</Text>
            </VStack>
          </VStack>
        </VStack>
      </Box>

      <VStack
        as="footer"
        className="onboarding-footer"
        bg="bg.layerDefault"
        px="spacingX.globalGutter"
        gap="x3"
      >
        <Flex width="full" align="center" justify="space-between">
          <Box className="onboarding-footer-side">
            {previousAvailable ? (
              <ActionButton type="button" size="small" variant="ghost" onClick={() => move(-1)}>
                이전
              </ActionButton>
            ) : null}
          </Box>
          <Flex className="onboarding-indicator" align="center" gap="x2" aria-hidden>
            {slides.map((item, itemIndex) => (
              <span
                key={item.title}
                className="onboarding-indicator-dot"
                data-current={itemIndex === index || undefined}
              />
            ))}
          </Flex>
          <Box className="onboarding-footer-side onboarding-footer-side--end">
            {nextAvailable ? (
              <ActionButton type="button" size="small" variant="ghost" onClick={() => move(1)}>
                다음
              </ActionButton>
            ) : null}
          </Box>
        </Flex>
        <VisuallyHidden aria-live="polite">3개 중 {index + 1}번째</VisuallyHidden>
        <ActionButton
          className="onboarding-exit"
          type="button"
          size="large"
          variant="brandSolid"
          onClick={onExit}
        >
          {mode === 'automatic' ? '건너뛰기' : '가이드 닫기'}
        </ActionButton>
      </VStack>
    </VStack>
  )
}
