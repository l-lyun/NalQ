import type { LearningMaterial, LearningReviewSummary } from './learning.types'

// Presentation-only fixtures. API integration should replace these through LearningPage props.
export const learningMaterialFixtures: LearningMaterial[] = [
  {
    id: 'material-data-structure',
    title: '자료구조 핵심 개념',
    body: '스택은 마지막에 들어온 데이터가 먼저 나오는 LIFO 구조입니다. 큐는 먼저 들어온 데이터가 먼저 나오는 FIFO 구조이며, 그래프는 정점과 간선의 관계로 데이터를 표현합니다.',
    source: 'PASTE',
    updatedAtLabel: '오늘 오전 10:30 수정',
    generating: false,
  },
  {
    id: 'material-operating-system',
    title: '운영체제 정리: 프로세스와 스레드, 스케줄링의 차이를 긴 제목에서도 확인하기',
    body: '프로세스는 실행 중인 프로그램이며 독립된 메모리 공간을 가집니다. 스레드는 프로세스 안에서 실행되는 흐름으로 같은 프로세스의 자원을 공유합니다. 스케줄러는 실행할 프로세스를 선택합니다.',
    source: 'NOTION',
    updatedAtLabel: '어제 오후 4:12 수정',
    generating: false,
  },
  {
    id: 'material-network',
    title: '네트워크 기초',
    body: 'TCP는 연결 지향형 전송 프로토콜로 신뢰성 있는 데이터 전달을 제공합니다. HTTP는 요청과 응답을 중심으로 동작하는 애플리케이션 계층 프로토콜입니다.',
    source: 'PASTE',
    updatedAtLabel: '8월 18일 수정',
    generating: true,
  },
]

export const learningReviewFixture: LearningReviewSummary = {
  sourceAttemptId: 'attempt-network-2',
  materialTitle: '네트워크 기초',
  completedAtLabel: '오늘 오전 9:20 완료',
  reviewQuestionCount: 3,
}
