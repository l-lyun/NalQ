import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSyncExternalStore } from 'react'
import { useCurrentUser } from '@/features/auth/model/auth.queries'
import { getAuthContext, isCurrentAuthContext, subscribeAuthContext } from '@/features/auth/model/authContext'
import { useAuthPhase } from '@/features/auth/model/useAuthPhase'
import { getCharacterShop, purchaseItem, saveEquipment } from './characterShop.api'
import type { CharacterShop, Equipment } from './characterShop.types'

const getEpoch = () => getAuthContext().authEpoch

export function useCharacterShop(active = true) {
  const currentUser = useCurrentUser()
  const phase = useAuthPhase()
  const epoch = useSyncExternalStore(subscribeAuthContext, getEpoch, getEpoch)
  const context = getAuthContext()
  const queryClient = useQueryClient()
  const queryKey = ['private', 'character-shop', currentUser.data?.id, epoch] as const
  const query = useQuery({
    queryKey,
    queryFn: ({ signal }) => getCharacterShop(context, signal),
    enabled: active && phase === 'authenticated' && Boolean(currentUser.data),
    staleTime: 0,
  })
  const sync = async (data: CharacterShop) => {
    if (!isCurrentAuthContext(context)) return
    // Cancel a pre-purchase read so an older balance cannot overwrite this result.
    await queryClient.cancelQueries({ queryKey, exact: true })
    if (isCurrentAuthContext(context)) queryClient.setQueryData(queryKey, data)
  }
  const purchase = useMutation({
    mutationFn: (itemId: string) => purchaseItem(itemId, context),
    onSuccess: sync,
    onError: () => { if (isCurrentAuthContext(context)) void queryClient.invalidateQueries({ queryKey }) },
  })
  const save = useMutation({
    mutationFn: (equipment: Equipment) => saveEquipment(equipment, context),
    onSuccess: sync,
  })
  return { query, purchase, save, identity: `${currentUser.data?.id}:${epoch}` }
}
