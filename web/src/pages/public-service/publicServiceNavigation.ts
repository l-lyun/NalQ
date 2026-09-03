export function readPublicReturnPath(state: unknown) {
  if (!state || typeof state !== 'object') return '/login'
  const candidate = (state as { returnTo?: unknown }).returnTo
  if (typeof candidate !== 'string' || !candidate.startsWith('/') || candidate.startsWith('//')) {
    return '/login'
  }
  return candidate
}
