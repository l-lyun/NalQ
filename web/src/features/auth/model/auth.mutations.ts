import { useMutation } from '@tanstack/react-query'

import {
  checkNicknameAvailability,
  confirmEmailVerification,
  requestSignUp,
  resendVerificationEmail,
} from '@/features/auth/api/auth.api'

import {
  completeSignUpAndLoadCurrentUser,
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

export function useNicknameAvailabilityMutation() {
  return useMutation({ mutationFn: checkNicknameAvailability, retry: false })
}

export function useCompleteSignUpMutation() {
  return useMutation({ mutationFn: completeSignUpAndLoadCurrentUser, retry: false })
}

export function useLogoutMutation() {
  return useMutation({ mutationFn: logoutCurrentSession, retry: false })
}
