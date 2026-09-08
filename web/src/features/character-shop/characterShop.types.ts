export type ItemCategory = 'CHARACTER' | 'ROOM' | 'HAT' | 'TOP'
export type ShopItem = { id: string; category: ItemCategory; name: string; price: number; assetKey: string }
export type Equipment = { characterId: string; roomId: string; hatId: string; topId: string }
export type CharacterShop = { balance: number; catalog: ShopItem[]; ownedItemIds: string[]; equipment: Equipment }

export const categories: ReadonlyArray<{ id: ItemCategory; label: string; slot: keyof Equipment }> = [
  { id: 'CHARACTER', label: '캐릭터', slot: 'characterId' },
  { id: 'ROOM', label: '방', slot: 'roomId' },
  { id: 'HAT', label: '모자', slot: 'hatId' },
  { id: 'TOP', label: '옷', slot: 'topId' },
]

export function equipItem(equipment: Equipment, item: ShopItem): Equipment {
  const category = categories.find((entry) => entry.id === item.category)
  return category ? { ...equipment, [category.slot]: item.id } : equipment
}

export function sameEquipment(a: Equipment, b: Equipment) {
  return categories.every(({ slot }) => a[slot] === b[slot])
}

export function canEquip(equipment: Equipment, shop: CharacterShop) {
  return categories.every(({ id, slot }) => shop.ownedItemIds.includes(equipment[slot])
    && shop.catalog.some((item) => item.id === equipment[slot] && item.category === id))
}
