export type AccountWithdrawalError = {
  kind?: string
  code?: string
  status?: number
}

export function shouldRetryAccountWithdrawal(
  failureCount: number,
  error: AccountWithdrawalError,
) {
  if (failureCount >= 1) return false
  return error.kind === 'network' || error.code === 'AUTH_013' || (error.status ?? 0) >= 500
}
