import { queryOptions } from '@tanstack/react-query'

import { LEARNING_MATERIAL_STALE_TIME, learningMaterialKeys } from '../api/learningMaterial.api'
import {
  getManagedLearningMaterial,
  listManagedLearningMaterials,
} from '../api/learningMaterialManagementAdapter'
import type { GetLearningMaterialsParams } from '../api/learningMaterial.types'

export const managedLearningMaterialQueryOptions = {
  list: (params: GetLearningMaterialsParams) =>
    queryOptions({
      queryKey: learningMaterialKeys.list(params),
      queryFn: ({ signal }) => listManagedLearningMaterials(params, signal),
      staleTime: LEARNING_MATERIAL_STALE_TIME,
    }),
  detail: (materialId: string) =>
    queryOptions({
      queryKey: learningMaterialKeys.detail(materialId),
      queryFn: ({ signal }) => getManagedLearningMaterial(materialId, signal),
      staleTime: LEARNING_MATERIAL_STALE_TIME,
    }),
}
