import { ActionButton, Box, HStack, Text, VStack } from '@seed-design/react'
import { useEffect, useReducer, useState, type CSSProperties } from 'react'

import roomDayImage from './assets/room-day.png'
import roomNightImage from './assets/room-night.png'
import {
  PixelSprite,
  type PixelCharacter,
  type PixelHat,
  type PixelTop,
} from './PixelSprite'
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

const characters: ReadonlyArray<{ id: PixelCharacter; label: string }> = [
  { id: 'dragon', label: '꼬마 용' },
  { id: 'cat', label: '고양이' },
  { id: 'bear', label: '곰' },
]

const rooms = [
  { id: 'day', label: '낮 공부방', image: roomDayImage },
  { id: 'night', label: '밤 공부방', image: roomNightImage },
] as const

const tops: ReadonlyArray<{ id: PixelTop; label: string }> = [
  { id: 'none', label: '기본 옷' },
  { id: 'sweater', label: '크림 니트' },
]

type RoomId = typeof rooms[number]['id']

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
  const [characterId, setCharacterId] = useState<PixelCharacter>('dragon')
  const [roomId, setRoomId] = useState<RoomId>('day')
  const [hatId, setHatId] = useState<PixelHat>('beret')
  const [topId, setTopId] = useState<PixelTop>('sweater')
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

  const character = characters.find((item) => item.id === characterId) ?? characters[0]
  const room = rooms.find((item) => item.id === roomId) ?? rooms[0]
  const hat = hats.find((item) => item.id === hatId) ?? hats[0]
  const top = tops.find((item) => item.id === topId) ?? tops[0]
  const frame = moving ? getWalkFrame(motion.walkedMs) : 0
  const sceneStyle: RoomSceneStyle = {
    '--actor-left': `${motion.position * 66}%`,
  }
  const directionLabel = motion.direction === 1 ? '오른쪽' : '왼쪽'
  const motionLabel = reducedMotion
    ? '모션 줄이기 설정으로 멈춰 있음'
    : motion.phase === 'running' ? '걷는 중' : '잠시 멈춤'
  const sceneLabel = `${character.label} · ${room.label} · 모자: ${hat.label} · 옷: ${top.label} · 방향: ${directionLabel} · ${motionLabel}`

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
              캐릭터와 방을 바꾸며 걷기·모자·옷 레이어가 자연스럽게 맞는지 확인해요.
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
              <img className="pixel-room-background" src={room.image} alt="" />
              <div className="pixel-room-walk-track" aria-hidden>
                <div className="pixel-room-actor">
                  <div className="pixel-room-actor-facing" data-direction={motion.direction}>
                    <PixelSprite
                      className="pixel-room-sprite"
                      character={characterId}
                      frame={frame}
                      hat={hatId}
                      top={topId}
                    />
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

          <VStack as="section" gap="x3" aria-labelledby="pixel-room-character-title">
            <VStack gap="x1">
              <Text as="h2" id="pixel-room-character-title" textStyle="t7Bold" color="fg.neutral">
                캐릭터
              </Text>
              <Text textStyle="t3Regular" color="fg.neutralMuted">
                같은 모자와 옷을 유지한 채 캐릭터 정렬을 비교해요.
              </Text>
            </VStack>
            <HStack className="pixel-room-option-controls" gap="x2">
              {characters.map((item) => (
                <ActionButton
                  key={item.id}
                  type="button"
                  size="large"
                  variant={item.id === characterId ? 'brandOutline' : 'neutralWeak'}
                  aria-pressed={item.id === characterId}
                  onClick={() => setCharacterId(item.id)}
                >
                  {item.label}
                </ActionButton>
              ))}
            </HStack>
          </VStack>

          <VStack as="section" gap="x3" aria-labelledby="pixel-room-background-title">
            <VStack gap="x1">
              <Text as="h2" id="pixel-room-background-title" textStyle="t7Bold" color="fg.neutral">
                공부방
              </Text>
              <Text textStyle="t3Regular" color="fg.neutralMuted">
                캐릭터와 꾸미기 조합은 그대로 두고 방만 바꿔요.
              </Text>
            </VStack>
            <HStack className="pixel-room-option-controls" gap="x2">
              {rooms.map((item) => (
                <ActionButton
                  key={item.id}
                  type="button"
                  size="large"
                  variant={item.id === roomId ? 'brandOutline' : 'neutralWeak'}
                  aria-pressed={item.id === roomId}
                  onClick={() => setRoomId(item.id)}
                >
                  {item.label}
                </ActionButton>
              ))}
            </HStack>
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
            <HStack className="pixel-room-option-controls" gap="x2">
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

          <VStack as="section" gap="x3" aria-labelledby="pixel-room-top-title">
            <VStack gap="x1">
              <Text as="h2" id="pixel-room-top-title" textStyle="t7Bold" color="fg.neutral">
                옷
              </Text>
              <Text textStyle="t3Regular" color="fg.neutralMuted">
                세 캐릭터 모두 원래 몸 크기를 유지한 착용 이미지로 비교해요.
              </Text>
            </VStack>
            <HStack className="pixel-room-option-controls" gap="x2">
              {tops.map((item) => (
                <ActionButton
                  key={item.id}
                  type="button"
                  size="large"
                  variant={item.id === topId ? 'brandOutline' : 'neutralWeak'}
                  aria-pressed={item.id === topId}
                  onClick={() => setTopId(item.id)}
                >
                  {item.label}
                </ActionButton>
              ))}
            </HStack>
          </VStack>

          <VStack as="section" gap="x3" aria-labelledby="pixel-room-hat-title">
            <VStack gap="x1">
              <Text as="h2" id="pixel-room-hat-title" textStyle="t7Bold" color="fg.neutral">
                모자
              </Text>
              <Text textStyle="t3Regular" color="fg.neutralMuted">
                구매·코인·실제 보유 상태 없이 착용 정렬만 확인해요.
              </Text>
            </VStack>
            <HStack className="pixel-room-option-controls" gap="x2">
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
