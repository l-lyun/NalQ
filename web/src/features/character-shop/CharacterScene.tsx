import { PixelSprite, type PixelCharacter, type PixelHat, type PixelTop } from '@/experiments/pixel-room/PixelSprite'
import roomDay from '@/experiments/pixel-room/assets/room-day.png'
import roomNight from '@/experiments/pixel-room/assets/room-night.png'
import { categories, type Equipment, type ShopItem } from './characterShop.types'
import './character-shop.css'

export function CharacterScene({ equipment, catalog, compact = false }: { equipment: Equipment; catalog: ShopItem[]; compact?: boolean }) {
  const selected = Object.fromEntries(categories.map(({ slot }) => [slot, catalog.find((item) => item.id === equipment[slot])]))
  const character = selected.characterId?.assetKey
  const hat = selected.hatId?.assetKey
  const top = selected.topId?.assetKey
  // Unknown future catalog assets stay safe until this client gains their renderer.
  const safeCharacter: PixelCharacter = character === 'cat' || character === 'bear' ? character : 'dragon'
  const safeHat: PixelHat = hat === 'beret' || hat === 'beanie' ? hat : 'none'
  const safeTop: PixelTop = top === 'sweater' ? top : 'none'
  return (
    <div className={`character-scene${compact ? ' character-scene--compact' : ''}`} role="img"
      aria-label={categories.map(({ slot }) => selected[slot]?.name ?? '').filter(Boolean).join(' · ')}>
      <img className="character-scene-room" src={selected.roomId?.assetKey === 'night' ? roomNight : roomDay} alt="" />
      <PixelSprite className="character-scene-actor" character={safeCharacter} hat={safeHat} top={safeTop} frame={0} />
    </div>
  )
}
