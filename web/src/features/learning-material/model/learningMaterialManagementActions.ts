import type { LearningMaterialContentEditStatus } from '../api/learningMaterial.types'

export type LearningMaterialManagementAction = {
  id: 'create-quiz' | 'edit'
  label: '퀴즈 만들기' | '학습자료 수정'
  disabled: boolean
}

export function getLearningMaterialManagementActions(
  contentEditStatus: LearningMaterialContentEditStatus,
  listTransitioning: boolean,
): LearningMaterialManagementAction[] {
  return [
    {
      id: 'create-quiz',
      label: '퀴즈 만들기',
      disabled: listTransitioning || contentEditStatus === 'LOCKED_GENERATING',
    },
    {
      id: 'edit',
      label: '학습자료 수정',
      disabled: listTransitioning,
    },
  ]
}
