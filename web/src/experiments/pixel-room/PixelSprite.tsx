import { useId } from 'react'

import characterUrl from './assets/character-walk.png'
import hatsUrl from './assets/hats.png'

export type PixelHat = 'none' | 'beret' | 'beanie'

interface PixelSpriteProps {
  frame: number
  hat: PixelHat
  className?: string
}

// Frame registration is measured against the head top and feet baseline.
// These offsets compensate for the generated atlas, not different character poses.
const registration = [
  { x: -14, y: 0 },
  { x: 16, y: 0 },
  { x: -8, y: 56 },
  { x: 13, y: 56 },
] as const

export function PixelSprite({ frame, hat, className }: PixelSpriteProps) {
  const matteId = `pixel-matte-${useId().replace(/:/g, '')}`
  const safeFrame = Number.isFinite(frame) ? ((Math.floor(frame) % 4) + 4) % 4 : 0
  const offset = registration[safeFrame]

  return (
    <svg className={className} viewBox="0 0 627 627" aria-hidden="true" focusable="false" data-frame={safeFrame} data-hat={hat}>
      <defs>
        <clipPath id={`${matteId}-body`}><rect width="627" height="490" /></clipPath>
        <clipPath id={`${matteId}-feet`}><rect y="490" width="627" height="137" /></clipPath>
        {/* The generator returned RGB assets, even for alpha requests. For this
            experiment only, key out near-white in the renderer; originals stay
            untouched. Final artwork should provide a real artist-checked alpha. */}
        <filter id={matteId} colorInterpolationFilters="sRGB" x="0" y="0" width="100%" height="100%">
          <feComponentTransfer in="SourceGraphic" result="whiteChannels">
            <feFuncR type="discrete" tableValues="0 0 0 0 0 0 0 0 0 0 1" />
            <feFuncG type="discrete" tableValues="0 0 0 0 0 0 0 0 0 0 1" />
            <feFuncB type="discrete" tableValues="0 0 0 0 0 0 0 0 0 0 1" />
          </feComponentTransfer>
          <feColorMatrix in="whiteChannels" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  .333333 .333333 .333333 0 0" result="whiteness" />
          <feComponentTransfer in="whiteness" result="foreground">
            <feFuncA type="discrete" tableValues="1 1 1 0" />
          </feComponentTransfer>
          {/* Pull the binary matte two source pixels inward so blended white
              edge pixels do not flash while the sprite frames advance. */}
          <feMorphology in="foreground" operator="erode" radius="2" result="trimmedForeground" />
          <feComposite in="SourceGraphic" in2="trimmedForeground" operator="in" />
        </filter>
      </defs>
      {/* Keep the same upper-body pixels in every frame, so generated variations
          cannot make the head or clothing wobble under a fixed hat. */}
      <g clipPath={`url(#${matteId}-body)`}>
        <svg x={registration[0].x} width="627" height="627" viewBox="0 0 627 627" overflow="hidden">
          <image href={characterUrl} width="1254" height="1254" filter={`url(#${matteId})`} />
        </svg>
      </g>
      <g clipPath={`url(#${matteId}-feet)`}>
        <svg x={offset.x} y={offset.y} width="627" height="627" viewBox={`${(safeFrame % 2) * 627} ${Math.floor(safeFrame / 2) * 627} 627 627`} overflow="hidden">
          <image href={characterUrl} width="1254" height="1254" filter={`url(#${matteId})`} />
        </svg>
      </g>
      {hat !== 'none' && (
        <g transform="translate(38 -23) scale(.86)">
          <svg width="627" height="627" viewBox={`${hat === 'beanie' ? 887 : 0} 0 887 887`} overflow="hidden">
            <image href={hatsUrl} width="1774" height="887" filter={`url(#${matteId})`} />
          </svg>
        </g>
      )}
    </svg>
  )
}
