import { QueryClientProvider } from '@tanstack/react-query'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'

import { queryClient } from '@/app/providers/queryClient'
import { AuthBootstrap } from '@/app/router/AuthBootstrap'
import { AuthGate, PublicOnlyGate } from '@/app/router/AuthGate'
import { AuthenticatedAppShell } from '@/app/shell/AuthenticatedAppShell'
import {
  quizMockEnabled,
  quizRoutesEnabled,
} from '@/features/quiz/model/quizFeature'
import { LoginPage } from '@/pages/login/LoginPage'
import {
  OpenSourceLicensesPage,
  PrivacyPage,
  SupportPage,
  TermsPage,
} from '@/pages/public-service/PublicServicePages'
import {
  QuizAttemptResultRoutePage,
  QuizFixturePage,
  QuizMaterialRoutePage,
  QuizMockAttemptResultRoutePage,
  QuizMockMaterialRoutePage,
  QuizMockReviewRoutePage,
  QuizMockSetRoutePage,
  QuizSetRoutePage,
  ReviewEntryRoutePage,
  ReviewSessionRoutePage,
} from '@/pages/quiz'
import { SignUpPage } from '@/pages/sign-up/SignUpPage'
import { VerifyEmailPage } from '@/pages/verify-email/VerifyEmailPage'

const router = createBrowserRouter([
  { path: '/terms', element: <TermsPage /> },
  { path: '/privacy', element: <PrivacyPage /> },
  { path: '/support', element: <SupportPage /> },
  { path: '/open-source-licenses', element: <OpenSourceLicensesPage /> },
  { path: '/profile/terms', element: <Navigate to="/terms" replace /> },
  { path: '/profile/privacy', element: <Navigate to="/privacy" replace /> },
  { path: '/profile/inquiry', element: <Navigate to="/support" replace /> },
  { path: '/profile/marketing', element: <Navigate to="/" replace /> },
  {
    element: <PublicOnlyGate />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/sign-up', element: <SignUpPage /> },
      { path: '/verify-email', element: <VerifyEmailPage /> },
    ],
  },
  {
    element: <AuthGate />,
    children: [
      {
        element: <AuthenticatedAppShell />,
        children: [
          { index: true, element: null },
          { path: '/learning', element: null },
          { path: '/learning/materials', element: null },
          { path: '/learning/materials/new', element: null },
          { path: '/learning/materials/:materialId', element: null },
          { path: '/learning/import/notion', element: null },
          { path: '/learning/quizzes', element: null },
          { path: '/learning/new', element: null },
          { path: '/profile', element: null },
          { path: '/profile/account', element: null },
          { path: '/profile/withdrawal', element: null },
        ],
      },
      ...(quizRoutesEnabled ? [
        { path: '/learning/:materialId/quiz', element: quizMockEnabled ? <QuizMockMaterialRoutePage /> : <QuizMaterialRoutePage /> },
        { path: '/quiz-sets/:quizSetId', element: quizMockEnabled ? <QuizMockSetRoutePage /> : <QuizSetRoutePage /> },
        { path: '/quiz-attempts/:attemptId/result', element: quizMockEnabled ? <QuizMockAttemptResultRoutePage /> : <QuizAttemptResultRoutePage /> },
        { path: '/review', element: quizMockEnabled ? <QuizMockReviewRoutePage /> : <ReviewEntryRoutePage /> },
        { path: '/review-sessions/:reviewSessionId', element: quizMockEnabled ? <QuizMockReviewRoutePage /> : <ReviewSessionRoutePage /> },
      ] : []),
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])

export function App() {
  if (import.meta.env.DEV && window.location.pathname === '/quiz-preview') {
    return <QuizFixturePage />
  }

  if (import.meta.env.DEV && window.location.pathname === '/quiz-result-preview') {
    return <QuizFixturePage initialScene="RESULT" />
  }

  if (import.meta.env.DEV && window.location.pathname === '/quiz-review-preview') {
    return <QuizFixturePage flowKind="REVIEW" />
  }

  return (
    <QueryClientProvider client={queryClient}>
      <AuthBootstrap>
        <RouterProvider router={router} />
      </AuthBootstrap>
    </QueryClientProvider>
  )
}
