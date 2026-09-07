import { ActionButton, Box, HStack, Text, VStack } from '@seed-design/react'
import { useEffect, useReducer, useState, type CSSProperties } from 'react'

import roomImage from './assets/room.png'
import { PixelSprite, type PixelHat } from './PixelSprite'
import {
  getWalkFrame,
  INITIAL_WALK_STATE,
  reduceWalkMotion,
  type WalkDirection,
  type WalkMotionAction,
} from './pixelRoomMotion'

import './pixel-room.css'

const hats: ReadonlyArray<{ id: PixelHat; label: string }> = [
  { id: 'none', label: '모자 없음' },
  { id: 'beret', label: '크림 베레모' },
  { id: 'beanie', label: '파란 비니' },
]

type RoomSceneStyle = CSSProperties & {
  '--actor-left': string
}

function usePrefersReducedMotion() {
  const [reducedMotion, setReducedMotion] = useState(() => (
    typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  ))

  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)')
    const update = () => setReducedMotion(media.matches)

    update()
    media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [])

  return reducedMotion
}

export function PixelRoomExperimentPage() {
  const [motion, dispatch] = useReducer(
    (state: typeof INITIAL_WALK_STATE, action: WalkMotionAction) => reduceWalkMotion(state, action),
    INITIAL_WALK_STATE,
  )
  const [hatId, setHatId] = useState<PixelHat>('beret')
  const reducedMotion = usePrefersReducedMotion()
  const moving = motion.phase === 'running' && !reducedMotion

  useEffect(() => {
    if (!moving) return

    let animationFrame = 0
    let previousTime: number | undefined

    const animate = (time: number) => {
      if (previousTime !== undefined) {
        dispatch({ type: 'tick', elapsedMs: Math.min(time - previousTime, 64) })
      }
      previousTime = time
      animationFrame = window.requestAnimationFrame(animate)
    }

    animationFrame = window.requestAnimationFrame(animate)
    return () => window.cancelAnimationFrame(animationFrame)
  }, [moving])

  const hat = hats.find((item) => item.id === hatId) ?? hats[0]
  const frame = moving ? getWalkFrame(motion.walkedMs) : 0
  const sceneStyle: RoomSceneStyle = {
    '--actor-left': `${motion.position * 66}%`,
  }
  const directionLabel = motion.direction === 1 ? '오른쪽' : '왼쪽'
  const motionLabel = reducedMotion
    ? '모션 줄이기 설정으로 멈춰 있음'
    : motion.phase === 'running' ? '걷는 중' : '잠시 멈춤'
  const sceneLabel = `모자: ${hat.label} · 방향: ${directionLabel} · ${motionLabel}`

  const face = (direction: WalkDirection) => dispatch({ type: 'face', direction })

  return (
    <VStack className="pixel-room-page" minHeight="100dvh" bg="bg.layerBasement">
      <Box as="main" className="pixel-room-main" width="full" pt="safeArea">
        <VStack
          className="pixel-room-content"
          width="full"
          px="spacingX.globalGutter"
          pt="x6"
          pb="spacingY.screenBottom"
          gap="x6"
        >
          <VStack as="header" align="flex-start" gap="x2">
            <Text textStyle="t3Bold" color="fg.brand">개발 전용 미리보기</Text>
            <Text as="h1" textStyle="t9Bold" color="fg.neutral">도트 움직임 실험</Text>
            <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
              작은 공부방에서 걷기와 모자 레이어가 자연스럽게 맞는지 확인해요.
            </Text>
          </VStack>

          <VStack as="section" gap="x3" aria-labelledby="pixel-room-scene-title">
            <Text as="h2" id="pixel-room-scene-title" textStyle="t7Bold" color="fg.neutral">
              내 공부방
            </Text>
            <Box
              className="pixel-room-scene"
              borderRadius="r4"
              role="img"
              aria-label={sceneLabel}
              style={sceneStyle}
            >
              <img className="pixel-room-background" src={roomImage} alt="" />
              <div className="pixel-room-walk-track" aria-hidden>
                <div className="pixel-room-actor">
                  <div className="pixel-room-actor-facing" data-direction={motion.direction}>
                    <PixelSprite className="pixel-room-sprite" frame={frame} hat={hatId} />
                  </div>
                </div>
              </div>
            </Box>
            <Text textStyle="t4Medium" color="fg.neutral" align="center">{sceneLabel}</Text>
            {reducedMotion ? (
              <Text role="status" textStyle="t3Regular" color="fg.neutralMuted" align="center">
                기기의 모션 줄이기 설정을 따라 자동 걷기를 멈췄어요.
              </Text>
            ) : null}
          </VStack>

          <VStack as="section" gap="x3" aria-labelledby="pixel-room-motion-title">
            <VStack gap="x1">
              <Text as="h2" id="pixel-room-motion-title" textStyle="t7Bold" color="fg.neutral">
                움직임
              </Text>
              <Text textStyle="t3Regular" color="fg.neutralMuted">
                버튼은 터치하거나 키보드로 선택할 수 있어요.
              </Text>
            </VStack>
            <HStack className="pixel-room-motion-controls" gap="x2">
              <ActionButton
                type="button"
                size="large"
                variant={motion.direction === -1 ? 'brandOutline' : 'neutralWeak'}
                aria-pressed={motion.direction === -1}
                onClick={() => face(-1)}
              >
                왼쪽 보기
              </ActionButton>
              <ActionButton
                type="button"
                size="large"
                variant="neutralSolid"
                disabled={reducedMotion}
                onClick={() => dispatch({ type: 'toggle' })}
              >
                {reducedMotion ? '자동 걷기 꺼짐' : motion.phase === 'running' ? '잠시 멈추기' : '다시 걷기'}
              </ActionButton>
              <ActionButton
                type="button"
                size="large"
                variant={motion.direction === 1 ? 'brandOutline' : 'neutralWeak'}
                aria-pressed={motion.direction === 1}
                onClick={() => face(1)}
              >
                오른쪽 보기
              </ActionButton>
            </HStack>
          </VStack>

          <VStack as="section" gap="x3" aria-labelledby="pixel-room-hat-title">
            <VStack gap="x1">
              <Text as="h2" id="pixel-room-hat-title" textStyle="t7Bold" color="fg.neutral">
                보유 모자
              </Text>
              <Text textStyle="t3Regular" color="fg.neutralMuted">
                구매·코인 없이 레이어 정렬만 확인하는 실험이에요.
              </Text>
            </VStack>
            <HStack className="pixel-room-hat-controls" gap="x2">
              {hats.map((item) => (
                <ActionButton
                  key={item.id}
                  type="button"
                  size="large"
                  variant={item.id === hatId ? 'brandOutline' : 'neutralWeak'}
                  aria-pressed={item.id === hatId}
                  onClick={() => setHatId(item.id)}
                >
                  {item.label}
                </ActionButton>
              ))}
            </HStack>
          </VStack>
        </VStack>
      </Box>
    </VStack>
  )
}

export { PixelRoomExperimentPage as Component }
