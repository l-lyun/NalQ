import type { ApiResponse } from '@/features/auth/api/auth.types'
import { assertAuthContext, type AuthContext } from '@/features/auth/model/authContext'
import { protectedApi } from '@/shared/api/protectedApi'
import { unwrapApiResponse } from '@/shared/api/apiError'
import type { CharacterShop, Equipment } from './characterShop.types'

const endpoint = '/api/v1/character-shop'

export async function getCharacterShop(authContext: AuthContext, signal?: AbortSignal) {
  assertAuthContext(authContext)
  const response = await protectedApi.get<ApiResponse<CharacterShop>>(endpoint, { authContext, signal })
  assertAuthContext(authContext)
  return unwrapApiResponse(response.data)
}

export async function purchaseItem(itemId: string, authContext: AuthContext) {
  assertAuthContext(authContext)
  const response = await protectedApi.post<ApiResponse<CharacterShop>>(`${endpoint}/purchases`, { itemId }, { authContext })
  assertAuthContext(authContext)
  return unwrapApiResponse(response.data)
}

export async function saveEquipment(equipment: Equipment, authContext: AuthContext) {
  assertAuthContext(authContext)
  const response = await protectedApi.put<ApiResponse<CharacterShop>>(`${endpoint}/equipment`, equipment, { authContext })
  assertAuthContext(authContext)
  return unwrapApiResponse(response.data)
}
