import { QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'

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
        <BrowserRouter>
          <Routes>
            <Route element={<PublicOnlyGate />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/sign-up" element={<SignUpPage />} />
              <Route path="/verify-email" element={<VerifyEmailPage />} />
            </Route>
            <Route element={<AuthGate />}>
              <Route element={<AuthenticatedAppShell />}>
                <Route index element={null} />
                <Route path="/learning" element={null} />
                <Route path="/learning/materials" element={null} />
                <Route path="/learning/materials/new" element={null} />
                <Route path="/learning/materials/:materialId" element={null} />
                <Route path="/learning/import/notion" element={null} />
                <Route path="/learning/quizzes" element={null} />
                <Route path="/learning/new" element={null} />
                <Route path="/profile" element={null} />
                <Route path="/profile/account" element={null} />
                <Route path="/profile/terms" element={null} />
                <Route path="/profile/privacy" element={null} />
                <Route path="/profile/marketing" element={null} />
                <Route path="/profile/inquiry" element={null} />
                <Route path="/profile/withdrawal" element={null} />
              </Route>
              {quizRoutesEnabled ? (
                <>
                  <Route
                    path="/learning/:materialId/quiz"
                    element={quizMockEnabled ? <QuizMockMaterialRoutePage /> : <QuizMaterialRoutePage />}
                  />
                  <Route
                    path="/quiz-sets/:quizSetId"
                    element={quizMockEnabled ? <QuizMockSetRoutePage /> : <QuizSetRoutePage />}
                  />
                  <Route
                    path="/quiz-attempts/:attemptId/result"
                    element={
                      quizMockEnabled
                        ? <QuizMockAttemptResultRoutePage />
                        : <QuizAttemptResultRoutePage />
                    }
                  />
                  <Route
                    path="/review"
                    element={quizMockEnabled ? <QuizMockReviewRoutePage /> : <ReviewEntryRoutePage />}
                  />
                  <Route
                    path="/review-sessions/:reviewSessionId"
                    element={quizMockEnabled ? <QuizMockReviewRoutePage /> : <ReviewSessionRoutePage />}
                  />
                </>
              ) : null}
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthBootstrap>
    </QueryClientProvider>
  )
}
