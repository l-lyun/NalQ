import type { LearningMaterialSummary, LearningReviewSummary } from './learning.types'

// Presentation-only fixtures. API integration should replace these through LearningPage props.
export const learningMaterialFixtures: LearningMaterialSummary[] = [
  {
    materialId: 'material-data-structure',
    title: '자료구조 핵심 개념',
    sourceType: 'PASTE',
    updatedAt: '2026-08-26T01:30:00Z',
    contentEditStatus: 'EDITABLE',
  },
  {
    materialId: 'material-operating-system',
    title: '운영체제 정리: 프로세스와 스레드, 스케줄링의 차이를 긴 제목에서도 확인하기',
    sourceType: 'NOTION',
    updatedAt: '2026-08-25T07:12:00Z',
    contentEditStatus: 'EDITABLE',
  },
  {
    materialId: 'material-network',
    title: '네트워크 기초',
    sourceType: 'PASTE',
    updatedAt: '2026-08-18T02:00:00Z',
    contentEditStatus: 'LOCKED_GENERATING',
  },
]

export const learningReviewFixture: LearningReviewSummary = {
  sourceAttemptId: 'attempt-network-2',
  quizSetId: 'quiz-set-network',
  materialTitle: '운영체제 핵심 정리',
  completedAt: '2026-08-26T00:20:00Z',
  totalQuestionCount: 10,
  attemptNumber: 2,
  reviewQuestionCount: 3,
}
