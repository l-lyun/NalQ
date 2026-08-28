export type LearningMaterialSourceType = 'PASTE' | 'NOTION'

export type LearningMaterialContentEditStatus = 'EDITABLE' | 'LOCKED_GENERATING'

export type LearningMaterialSummary = {
  materialId: string
  title: string
  sourceType: LearningMaterialSourceType
  contentEditStatus: LearningMaterialContentEditStatus
  updatedAt: string
}

export type LearningMaterialPage = {
  items: LearningMaterialSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type LearningMaterialDetail = LearningMaterialSummary & {
  content: string
  contentLength: number
  createdAt: string
}

export type CreateLearningMaterialRequest = {
  title: string
  content: string
  sourceType: LearningMaterialSourceType
}

export type CreateLearningMaterialResponse = {
  materialId: string
  title: string
  contentLength: number
  contentEditStatus: LearningMaterialContentEditStatus
  createdAt: string
}

export type GetLearningMaterialsParams = {
  page: number
  size?: number
  query?: string
}

export type UpdateLearningMaterialRequest = {
  title?: string
  content?: string
}
