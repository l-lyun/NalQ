export const SIGN_UP_SESSION_RECOVERY_NOTICE = 'SIGN_UP_SESSION_RECOVERY' as const
export const ACCOUNT_WITHDRAWAL_COMPLETED_NOTICE = 'ACCOUNT_WITHDRAWAL_COMPLETED' as const

export type LoginRouteState = {
  from?: string
  email?: string
  notice?: typeof SIGN_UP_SESSION_RECOVERY_NOTICE | typeof ACCOUNT_WITHDRAWAL_COMPLETED_NOTICE
}

export function readLoginRouteState(value: unknown): LoginRouteState {
  if (!value || typeof value !== 'object') return {}

  const candidate = value as Record<string, unknown>
  const email = typeof candidate.email === 'string' ? candidate.email.trim() : undefined

  return {
    from: typeof candidate.from === 'string' ? candidate.from : undefined,
    email: email && email.length <= 320 ? email : undefined,
    notice:
      candidate.notice === SIGN_UP_SESSION_RECOVERY_NOTICE
        ? SIGN_UP_SESSION_RECOVERY_NOTICE
        : candidate.notice === ACCOUNT_WITHDRAWAL_COMPLETED_NOTICE
          ? ACCOUNT_WITHDRAWAL_COMPLETED_NOTICE
          : undefined,
  }
}
