import { useMutation } from '@tanstack/react-query'

import {
  confirmEmailVerification,
  requestSignUp,
  resendVerificationEmail,
} from '@/features/auth/api/auth.api'

import {
  completeCurrentUserSession,
  loginAndLoadCurrentUser,
  logoutCurrentSession,
} from './authSession'

export function useLoginMutation() {
  return useMutation({ mutationFn: loginAndLoadCurrentUser, retry: false })
}

export function useCompleteSessionMutation() {
  return useMutation({ mutationFn: completeCurrentUserSession, retry: false })
}

export function useSignUpMutation() {
  return useMutation({ mutationFn: requestSignUp, retry: false })
}

export function useConfirmEmailMutation() {
  return useMutation({ mutationFn: confirmEmailVerification, retry: false })
}

export function useResendVerificationMutation() {
  return useMutation({ mutationFn: resendVerificationEmail, retry: false })
}

export function useLogoutMutation() {
  return useMutation({ mutationFn: logoutCurrentSession, retry: false })
}
