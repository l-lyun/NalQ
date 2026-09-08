import { useId } from 'react'
import dragonUrl from './assets/dragon-alpha.png'
import dragonSweaterUrl from './assets/dragon-sweater.png'
import catUrl from './assets/cat.png'
import catSweaterUrl from './assets/cat-sweater.png'
import bearUrl from './assets/bear.png'
import bearSweaterUrl from './assets/bear-sweater.png'
import beretUrl from './assets/hat-beret.png'
import beanieUrl from './assets/hat-beanie.png'

export type PixelHat = 'none' | 'beret' | 'beanie'
export type PixelCharacter = 'dragon' | 'cat' | 'bear'
export type PixelTop = 'none' | 'sweater'

interface PixelSpriteProps {
  frame: number
  hat: PixelHat
  character?: PixelCharacter
  top?: PixelTop
  className?: string
}

// Per-species registration in the shared 1254 px source canvas.
// Display transforms leave the generated alpha originals intact.
const characters = {
  dragon: {
    url: dragonUrl,
    sweaterUrl: dragonSweaterUrl,
    feet: 1020,
    torso: { x: 310, y: 620, width: 625, height: 400 },
    hat: [115, -65, 1000, 500],
  },
  cat: {
    url: catUrl,
    sweaterUrl: catSweaterUrl,
    feet: 1005,
    torso: {
      points: '440,720 800,720 880,850 889,920 805,920 805,1005 445,1005 445,920 360,920 365,850',
    },
    hat: [115, -25, 1000, 500],
  },
  bear: {
    url: bearUrl,
    sweaterUrl: bearSweaterUrl,
    feet: 1030,
    torso: {
      points: '410,730 830,730 929,850 929,910 832,910 832,1030 400,1030 400,910 310,910 310,850',
    },
    hat: [105, -40, 1020, 500],
  },
} as const

export function PixelSprite({ frame, hat, character = 'dragon', top = 'none', className }: PixelSpriteProps) {
  const id = `pixel-${useId().replace(/:/g, '')}`
  const model = characters[character]
  const fittedOutfit = top === 'sweater'
  const safeFrame = Number.isFinite(frame) ? ((Math.floor(frame) % 4) + 4) % 4 : 0
  const step = [0, 9, 0, -9][safeFrame]
  const [hx, hy, hw, hh] = model.hat

  return (
    <svg className={className} viewBox="0 0 1254 1254" aria-hidden="true" focusable="false"
      data-frame={safeFrame} data-hat={hat} data-character={character} data-top={top}
      data-outfit-mode={fittedOutfit ? 'fitted' : 'base'}>
      <defs>
        <clipPath id={`${id}-body`}><rect width="1254" height={model.feet + 12} /></clipPath>
        <clipPath id={`${id}-left`}><rect y={model.feet} width="627" height="234" /></clipPath>
        <clipPath id={`${id}-right`}><rect x="627" y={model.feet} width="627" height="234" /></clipPath>
        <clipPath id={`${id}-outfit-torso`}>
          {'points' in model.torso ? (
            <polygon points={model.torso.points} />
          ) : (
            <rect {...model.torso} />
          )}
        </clipPath>
        <mask
          id={`${id}-outfit-silhouette`}
          x="0"
          y="0"
          width="1254"
          height="1254"
          maskUnits="userSpaceOnUse"
          style={{ maskType: 'alpha' }}
        >
          <image href={model.url} width="1254" height="1254" />
        </mask>
      </defs>
      <image href={model.url} width="1254" height="1254" clipPath={`url(#${id}-body)`} />
      {fittedOutfit && (
        <image
          href={model.sweaterUrl}
          width="1254"
          height="1254"
          clipPath={`url(#${id}-outfit-torso)`}
          mask={`url(#${id}-outfit-silhouette)`}
        />
      )}
      {/* Prototype cutout shuffle; the upper body stays fixed under clothing.
          Final production animation needs an artist-checked walking atlas. */}
      <g transform={`translate(0 ${step})`}>
        <image href={model.url} width="1254" height="1254" clipPath={`url(#${id}-left)`} />
      </g>
      <g transform={`translate(0 ${-step})`}>
        <image href={model.url} width="1254" height="1254" clipPath={`url(#${id}-right)`} />
      </g>
      {hat !== 'none' && <image href={hat === 'beret' ? beretUrl : beanieUrl}
        x={hx} y={hat === 'beanie' ? hy - 15 : hy} width={hw} height={hh}
        preserveAspectRatio="none" />}
    </svg>
  )
}
