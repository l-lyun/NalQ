export type HomeAction = {
  label: string
  onClick: () => void
  loading?: boolean
}

export type HomeListItem = {
  id: string
  title: string
  detail: string
  disabled?: boolean
  onClick: () => void
}

export type HomeSectionState<T> =
  | { status: 'ready'; data: T }
  | { status: 'empty'; message: string }
  | { status: 'error'; message: string; onRetry: () => void }

export type HomeNextAction = {
  title: string
  description: string
  context: string
  action: HomeAction
}

export type HomeTodaySummary = {
  solvedCount: number
  correctCount: number
  gradedCount: number
}

export type HomeNavigationItem = {
  id: 'home' | 'learning' | 'profile'
  label: string
  current?: boolean
  onClick: () => void
}

export type HomePageProps = {
  status: 'ready' | 'firstVisit' | 'loading' | 'fullError'
  nextAction?: HomeNextAction
  review: HomeSectionState<HomeListItem>
  recentMaterials: HomeSectionState<HomeListItem[]>
  studyMethods: HomeListItem[]
  today: HomeSectionState<HomeTodaySummary>
  navigation: HomeNavigationItem[]
  recommendationWarning?: {
    title: string
    description: string
    onRetry: () => void
    onStartLearning: () => void
  }
  onViewAllReviews: () => void
  onViewAllMaterials: () => void
  onRetryAll: () => void
  session?: {
    email: string
    logoutPending: boolean
    onLogout: () => void
  }
}
