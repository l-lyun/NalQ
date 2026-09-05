import type { HomePageProps } from './home.types'

// These values exercise presentation states only. They do not decide the open product rules
// for recommendation ordering, recent-item limits, or today's aggregation boundary.
const noop = () => undefined

const studyMethods: HomePageProps['studyMethods'] = [
  {
    id: 'notion',
    title: 'Notion에서 가져오기',
    detail: 'Notion에서 페이지를 선택해 가져와요',
    onClick: noop,
  },
  {
    id: 'file',
    title: '파일에서 가져오기',
    detail: '.txt, .md 파일을 가져와요',
    onClick: noop,
  },
  {
    id: 'paste',
    title: '텍스트 붙여넣기',
    detail: '복사한 글을 바로 붙여넣어요',
    onClick: noop,
  },
]

const baseFixture = {
  greeting: { nickname: '공부왕7' },
  studyMethods,
  onViewAllReviews: noop,
  onViewAllMaterials: noop,
  onRetryAll: noop,
} satisfies Pick<
  HomePageProps,
  | 'greeting'
  | 'studyMethods'
  | 'onViewAllReviews'
  | 'onViewAllMaterials'
  | 'onRetryAll'
>

export const homeReadyFixture: HomePageProps = {
  ...baseFixture,
  status: 'ready',
  nextAction: {
    title: '운영체제 핵심 개념 문제를 이어서 풀어보세요',
    description: '마지막으로 풀던 문제부터 바로 이어갈 수 있어요.',
    context: '3/10문제 · 어제 오후 9:20에 마지막으로 학습',
    action: { label: '이어서 풀기', onClick: noop },
  },
  review: {
    status: 'ready',
    data: {
      id: 'review-1',
      title: '복습할 문제 4개',
      detail: '운영체제 핵심 개념 외 1개 자료',
      onClick: noop,
    },
  },
  recentMaterials: {
    status: 'ready',
    data: [
      {
        id: 'material-1',
        title: '운영체제 핵심 개념과 프로세스 관리',
        detail: '어제 학습 · 미완료 풀이 있음',
        onClick: noop,
      },
      {
        id: 'material-2',
        title: '네트워크 계층과 TCP 흐름 제어 정리',
        detail: '3일 전 학습 · 문제 있음',
        onClick: noop,
      },
      {
        id: 'material-3',
        title: '아주 긴 자료명이 작은 WebView에서도 잘리지 않고 자연스럽게 여러 줄로 표시되는지 확인하기 위한 학습자료',
        detail: '1주 전 학습 · 문제 없음',
        onClick: noop,
      },
    ],
  },
}

export const homeFirstVisitFixture: HomePageProps = {
  ...baseFixture,
  status: 'firstVisit',
  review: { status: 'empty', message: '지금 복습할 문제는 없어요.' },
  recentMaterials: { status: 'empty', message: '최근 학습자료가 없어요.' },
}

export const homeLoadingFixture: HomePageProps = {
  ...baseFixture,
  status: 'loading',
  review: { status: 'empty', message: '' },
  recentMaterials: { status: 'empty', message: '' },
}

export const homePartialErrorFixture: HomePageProps = {
  ...homeReadyFixture,
  review: {
    status: 'error',
    message: '복습 정보를 불러오지 못했어요.',
    onRetry: noop,
  },
}

export const homeRecommendationWarningFixture: HomePageProps = {
  ...homeReadyFixture,
  recommendationWarning: {
    title: '추천을 완성하지 못했어요',
    description: '일부 학습 상태를 확인하지 못했어요. 새 학습은 계속 시작할 수 있어요.',
    onRetry: noop,
    onStartLearning: noop,
  },
  nextAction: undefined,
}

export const homeFullErrorFixture: HomePageProps = {
  ...baseFixture,
  status: 'fullError',
  review: { status: 'error', message: '복습 정보를 불러오지 못했어요.', onRetry: noop },
  recentMaterials: {
    status: 'error',
    message: '최근 학습자료를 불러오지 못했어요.',
    onRetry: noop,
  },
}
