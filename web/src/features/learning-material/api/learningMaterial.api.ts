import type { ApiResponse } from '@/features/auth/api/auth.types'
import { protectedApi } from '@/shared/api/protectedApi'
import { unwrapApiResponse } from '@/shared/api/apiError'
import { queryOptions } from '@tanstack/react-query'

import type {
  CreateLearningMaterialRequest,
  CreateLearningMaterialResponse,
  GetLearningMaterialsParams,
  LearningMaterialDetail,
  LearningMaterialPage,
  UpdateLearningMaterialRequest,
} from './learningMaterial.types'

export const LEARNING_MATERIAL_PAGE_SIZE = 6
export const LEARNING_MATERIAL_STALE_TIME = 5 * 60 * 1_000

export const learningMaterialKeys = {
  all: ['private', 'learning-materials'] as const,
  list: (params: GetLearningMaterialsParams) => [...learningMaterialKeys.all, 'list', params] as const,
  detail: (materialId: string) => [...learningMaterialKeys.all, 'detail', materialId] as const,
}

export const learningMaterialQueryOptions = {
  list: (params: GetLearningMaterialsParams) =>
    queryOptions({
      queryKey: learningMaterialKeys.list(params),
      queryFn: ({ signal }) => getLearningMaterials(params, signal),
      staleTime: LEARNING_MATERIAL_STALE_TIME,
    }),
  detail: (materialId: string) =>
    queryOptions({
      queryKey: learningMaterialKeys.detail(materialId),
      queryFn: ({ signal }) => getLearningMaterial(materialId, signal),
      staleTime: LEARNING_MATERIAL_STALE_TIME,
    }),
}

export async function getLearningMaterials(
  params: GetLearningMaterialsParams,
  signal?: AbortSignal,
) {
  const query = params.query?.trim()
  const response = await protectedApi.get<ApiResponse<LearningMaterialPage>>(
    '/api/v1/learning-materials',
    {
      signal,
      params: {
        page: params.page,
        size: params.size ?? LEARNING_MATERIAL_PAGE_SIZE,
        ...(query ? { query } : {}),
      },
    },
  )
  return unwrapApiResponse(response.data)
}

export async function getLearningMaterial(materialId: string, signal?: AbortSignal) {
  const response = await protectedApi.get<ApiResponse<LearningMaterialDetail>>(
    `/api/v1/learning-materials/${materialId}`,
    { signal },
  )
  return unwrapApiResponse(response.data)
}

export async function createLearningMaterial(
  payload: CreateLearningMaterialRequest,
  idempotencyKey: string,
) {
  const response = await protectedApi.post<ApiResponse<CreateLearningMaterialResponse>>(
    '/api/v1/learning-materials',
    payload,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return unwrapApiResponse(response.data)
}

export async function updateLearningMaterial(
  materialId: string,
  payload: UpdateLearningMaterialRequest,
) {
  const response = await protectedApi.patch<ApiResponse<LearningMaterialDetail>>(
    `/api/v1/learning-materials/${materialId}`,
    payload,
  )
  return unwrapApiResponse(response.data)
}
