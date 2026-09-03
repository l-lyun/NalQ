import type { LearningMaterialSourceType } from '../api/learningMaterial.types'

export const MATERIAL_TITLE_MAX_LENGTH = 255
export const MATERIAL_CONTENT_MAX_LENGTH = 20_000

export type LearningMaterialDraftState = {
  sourceType: LearningMaterialSourceType
  title: string
  content: string
}

export function countCodePoints(value: string) {
  return Array.from(value).length
}

export function validateLearningMaterialDraft(draft: Pick<LearningMaterialDraftState, 'title' | 'content'>) {
  const title = draft.title.trim()
  const titleLength = countCodePoints(title)
  const contentLength = countCodePoints(draft.content)

  return {
    title: titleLength === 0
      ? '제목을 입력해 주세요.'
      : titleLength > MATERIAL_TITLE_MAX_LENGTH
        ? '제목은 255자까지 입력할 수 있어요.'
        : undefined,
    content: !/\S/u.test(draft.content)
      ? '내용을 입력해 주세요.'
      : contentLength > MATERIAL_CONTENT_MAX_LENGTH
        ? '내용은 20,000자까지 저장할 수 있어요.'
        : undefined,
  }
}

export function isLearningMaterialDraftValid(draft: Pick<LearningMaterialDraftState, 'title' | 'content'>) {
  const errors = validateLearningMaterialDraft(draft)
  return !errors.title && !errors.content
}
