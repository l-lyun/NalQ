import type { SessionTokens } from '@/features/auth/api/auth.types'

type TokenSnapshot = SessionTokens

let tokenSnapshot: TokenSnapshot | null = null

export function setSessionTokens(tokens: SessionTokens) {
  tokenSnapshot = tokens
}

export function clearSessionTokens() {
  tokenSnapshot = null
}

export function getAccessToken() {
  return tokenSnapshot?.accessToken ?? null
}

export function shouldRefreshAccessToken(clockSkewMs = 30_000) {
  if (!tokenSnapshot) return true

  const expiresAt = Date.parse(tokenSnapshot.accessExpiresAt)
  return !Number.isFinite(expiresAt) || expiresAt <= Date.now() + clockSkewMs
}
