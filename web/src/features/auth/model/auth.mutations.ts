import { useMutation, useQueryClient } from '@tanstack/react-query'

import {
  checkNicknameAvailability,
  confirmEmailVerification,
  requestVerificationEmail,
  resendVerificationEmail,
  updateCurrentUserNickname,
  withdrawCurrentUser,
} from '@/features/auth/api/auth.api'
import type { AccountWithdrawalRequest } from '@/features/auth/api/auth.types'

import {
  completeSignUpAndLoadCurrentUser,
  completeCurrentUserSession,
  loginAndLoadCurrentUser,
  logoutCurrentSession,
  completeAccountWithdrawal,
  recoverCompletedSignUpSession,
} from './authSession'
import { currentUserQueryKey } from './auth.queries'
import { shouldRetryAccountWithdrawal } from './accountWithdrawal'

export function useLoginMutation() {
  return useMutation({ mutationFn: loginAndLoadCurrentUser, retry: false })
}

export function useCompleteSessionMutation() {
  return useMutation({ mutationFn: completeCurrentUserSession, retry: false })
}

export function useRequestVerificationEmailMutation() {
  return useMutation({ mutationFn: requestVerificationEmail, retry: false })
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

export function useRecoverCompletedSignUpMutation() {
  return useMutation({ mutationFn: recoverCompletedSignUpSession, retry: false })
}

export function useLogoutMutation() {
  return useMutation({ mutationFn: logoutCurrentSession, retry: false })
}

export function useUpdateCurrentUserNicknameMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: updateCurrentUserNickname,
    retry: false,
    onSuccess: async (currentUser) => {
      queryClient.setQueryData(currentUserQueryKey, currentUser)
      await queryClient.invalidateQueries({ queryKey: currentUserQueryKey })
    },
  })
}

export function useAccountWithdrawalMutation() {
  return useMutation({
    mutationFn: async (payload: AccountWithdrawalRequest) => {
      const result = await withdrawCurrentUser(payload)
      await completeAccountWithdrawal()
      return result
    },
    retry: shouldRetryAccountWithdrawal,
  })
}
