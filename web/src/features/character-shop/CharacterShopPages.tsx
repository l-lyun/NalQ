import { ActionButton, ChipTabs, HStack, Skeleton, Text, VStack } from '@seed-design/react'
import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ProfileSubPage } from '@/pages/profile/ProfileSubPages'
import { profileSubPageNavigationState } from '@/pages/profile/profileRoutes'
import { getApiErrorMessage } from '@/shared/api/apiError'
import { CharacterScene } from './CharacterScene'
import { canEquip, categories, equipItem, sameEquipment, type CharacterShop, type Equipment, type ShopItem } from './characterShop.types'
import { useCharacterShop } from './useCharacterShop'

export function CharacterProfileSection() {
  const location = useLocation()
  const navigate = useNavigate()
  const { query } = useCharacterShop(location.pathname === '/profile')
  return (
    <VStack as="section" gap="x3" aria-labelledby="my-study-room-title">
      <HStack justify="space-between" align="center" gap="x2">
        <Text as="h2" id="my-study-room-title" textStyle="t7Bold">내 공부방</Text>
        {query.data ? <Text textStyle="t5Bold">{query.data.balance.toLocaleString()} 코인</Text> : null}
      </HStack>
      {query.data ? <CharacterScene equipment={query.data.equipment} catalog={query.data.catalog} compact /> : <ShopQueryState query={query} />}
      <HStack gap="x2" className="character-shop-actions">
        <ActionButton variant="neutralWeak" size="medium" onClick={() => navigate('/profile/character', { state: profileSubPageNavigationState })}>캐릭터 꾸미기</ActionButton>
        <ActionButton variant="neutralWeak" size="medium" onClick={() => navigate('/profile/shop', { state: profileSubPageNavigationState })}>상점</ActionButton>
      </HStack>
    </VStack>
  )
}

export function CharacterShopPage({ mode, onBack }: { mode: 'shop' | 'character'; onBack: () => void }) {
  const shop = useCharacterShop()
  return (
    <ProfileSubPage title={mode === 'shop' ? '상점' : '캐릭터 꾸미기'} onBack={onBack}>
      {shop.query.data ? <ShopContent key={`${shop.identity}:${mode}`} mode={mode} shop={shop} data={shop.query.data} /> : <ShopQueryState query={shop.query} />}
    </ProfileSubPage>
  )
}

function ShopQueryState({ query }: { query: ReturnType<typeof useCharacterShop>['query'] }) {
  if (query.isError) return (
    <VStack gap="x3" align="flex-start">
      <Text role="alert" textStyle="t4Regular" color="fg.critical">공부방 정보를 불러오지 못했어요.</Text>
      <ActionButton size="medium" variant="neutralWeak" onClick={() => void query.refetch()}>다시 시도</ActionButton>
    </VStack>
  )
  return <Skeleton aria-label="공부방 정보를 불러오는 중" aria-busy="true" tone="neutral" radius="8" width="full" height="180px" />
}

function ShopContent({ mode, shop, data }: { mode: 'shop' | 'character'; shop: ReturnType<typeof useCharacterShop>; data: CharacterShop }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [draft, setDraft] = useState<Equipment>(data.equipment)
  const [confirmation, setConfirmation] = useState<string | null>(null)
  const [notice, setNotice] = useState('')
  const [category, setCategory] = useState('CHARACTER')
  const busy = shop.purchase.isPending || shop.save.isPending
  const changed = !sameEquipment(draft, data.equipment)
  const confirmationRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (confirmation) { confirmationRef.current?.scrollIntoView({ block: 'nearest' }); confirmationRef.current?.focus() }
  }, [confirmation])
  const pendingItem = data.catalog.find((item) => item.id === confirmation)
  const isShop = mode === 'shop'
  const error = shop.purchase.error ?? shop.save.error

  const choose = (item: ShopItem) => {
    setNotice('')
    shop.save.reset()
    setDraft((current) => equipItem(current, item))
  }
  const buy = () => {
    if (!pendingItem || busy) return
    setNotice('')
    shop.purchase.mutate(pendingItem.id, { onSuccess: () => {
      setConfirmation(null)
      setNotice(`${pendingItem.name} 구매를 완료했어요. 캐릭터 꾸미기에서 장착해 주세요.`)
    } })
  }

  return (
    <VStack gap="x4">
      <HStack align="center" justify="space-between" gap="x2">
        <Text textStyle="t4Regular" color="fg.neutralMuted">보유 코인</Text>
        <Text textStyle="t7Bold">{data.balance.toLocaleString()} 코인</Text>
      </HStack>
      <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
        퀴즈 세트를 처음 끝내면 10코인을 받아요. 서술형은 자기평가까지 완료해 주세요. 재풀이와 복습에는 지급되지 않아요.
      </Text>
      {!isShop ? <>
        <CharacterScene equipment={draft} catalog={data.catalog} />
        <Text textStyle="t3Regular" color="fg.neutralMuted">{changed ? '미리보기예요. 저장하면 마이페이지에 반영돼요.' : '현재 저장된 모습이에요.'}</Text>
      </> : null}
      <div className="character-shop-feedback" aria-live="polite">
        {notice ? <Text as="p" role="status" textStyle="t4Regular" color="fg.positive">{notice}</Text> : null}
        {error ? <Text as="p" role="alert" textStyle="t4Regular" color="fg.critical">{getApiErrorMessage(error, '처리하지 못했어요. 다시 시도해 주세요.')}</Text> : null}
        {shop.query.isError ? <ActionButton size="small" variant="ghost" onClick={() => void shop.query.refetch()}>최신 상태 다시 불러오기</ActionButton> : null}
      </div>
      {pendingItem ? (
        <VStack gap="x3" p="x4" bg="bg.neutralWeak" borderRadius="r3" role="region" aria-label="구매 확인" ref={confirmationRef} tabIndex={-1}>
          <Text as="h2" textStyle="t6Bold">{pendingItem.name} 구매</Text>
          <Text textStyle="t4Regular">{pendingItem.price}코인을 사용할까요? 현재 {data.balance}코인을 보유하고 있어요.</Text>
          <HStack gap="x2" className="character-shop-actions">
            <ActionButton size="medium" variant="neutralWeak" disabled={busy} onClick={() => { setConfirmation(null); shop.purchase.reset() }}>취소</ActionButton>
            <ActionButton size="medium" variant="neutralSolid" disabled={busy || data.balance < pendingItem.price} loading={shop.purchase.isPending} onClick={buy}>구매 확정</ActionButton>
          </HStack>
        </VStack>
      ) : null}
      <ChipTabs.Root value={category} onValueChange={setCategory} size="medium" variant="neutralSolid">
        <ChipTabs.List className="character-shop-tabs" aria-label="아이템 종류">
          {categories.map(({ id, label }) => <ChipTabs.Trigger key={id} value={id}>{label}</ChipTabs.Trigger>)}
        </ChipTabs.List>
      {categories.map(({ id, label, slot }) => {
        const items = data.catalog.filter((item) => item.category === id && (isShop || data.ownedItemIds.includes(item.id)))
        return (
          <ChipTabs.Content key={id} value={id}><VStack as="section" gap="x3" aria-label={label}>
            <Text as="h2" textStyle="t6Bold">{label}</Text>
            {items.length ? <div className="character-shop-grid">{items.map((item) => {
              const owned = data.ownedItemIds.includes(item.id)
              const selected = draft[slot] === item.id
              return (
                <VStack key={item.id} className="character-shop-item" gap="x2">
                  <CharacterScene equipment={equipItem(isShop ? data.equipment : draft, item)} catalog={data.catalog} />
                  <Text textStyle="t5Bold">{item.name}</Text>
                  <Text textStyle="t3Regular" color="fg.neutralMuted">{isShop ? owned ? '보유 중' : `${item.price} 코인` : selected ? '선택됨' : '보유 중'}</Text>
                  {isShop ? <ActionButton size="small" variant="neutralWeak" disabled={busy || owned || Boolean(confirmation) || data.balance < item.price}
                    onClick={() => { shop.purchase.reset(); setNotice(''); setConfirmation(item.id) }} aria-label={`${item.name} ${owned ? '보유 중' : '구매'}`}>
                    {owned ? '보유 중' : data.balance < item.price ? `${item.price - data.balance} 코인 부족` : '구매하기'}
                  </ActionButton> : <ActionButton size="small" variant={selected ? 'neutralSolid' : 'neutralWeak'} aria-pressed={selected} disabled={busy} onClick={() => choose(item)} aria-label={`${item.name} 선택`}>{selected ? '선택됨' : '선택하기'}</ActionButton>}
                </VStack>
              )
            })}</div> : <Text textStyle="t4Regular" color="fg.neutralMuted">보유한 아이템이 없어요. 상점에서 만나보세요.</Text>}
          </VStack></ChipTabs.Content>
        )
      })}
      </ChipTabs.Root>
      {!isShop ? <HStack gap="x2" className="character-shop-actions">
        <ActionButton variant="neutralWeak" size="medium" disabled={busy || !changed} onClick={() => { setDraft(data.equipment); setNotice(''); shop.save.reset() }}>선택 취소</ActionButton>
        <ActionButton variant="neutralSolid" size="medium" disabled={busy || !changed || !canEquip(draft, data)} loading={shop.save.isPending}
          onClick={() => { setNotice(''); shop.save.mutate(draft, { onSuccess: (result) => { setDraft(result.equipment); setNotice('내 공부방을 저장했어요.') } }) }}>저장하기</ActionButton>
      </HStack> : null}
      <ActionButton variant="ghost" size="medium" disabled={busy} onClick={() => navigate(isShop ? '/profile/character' : '/profile/shop', { replace: true, state: location.state })}>{isShop ? '캐릭터 꾸미기' : '상점에서 더 만나보기'}</ActionButton>
    </VStack>
  )
}
