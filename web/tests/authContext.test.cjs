const assert = require('node:assert/strict')
const test = require('node:test')
const fixture = require('./helpers/loadTs.cjs')

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const tokens = (name) => ({ accessToken: name, accessExpiresAt: '2099-01-01T00:00:00Z', refreshExpiresAt: '2099-02-01T00:00:00Z' })

test('identity epochs reject old account work but ordinary refresh preserves the epoch', async () => {
  const { load } = fixture({ '@/features/auth/api/authTransport': { refreshSessionTransport: async () => tokens('refreshed') },
    './sessionCleanup': { endLocalSession: async () => {} } })
  const context = load('features/auth/model/authContext.ts')
  const vault = load('features/auth/model/tokenVault.ts')
  const old = context.advanceAuthContext(1)
  context.advanceAuthContext(2)
  assert.throws(() => context.assertAuthContext(old), { code: 'AUTH_CONTEXT_CHANGED' })
  const current = context.getAuthContext()
  await load('features/auth/model/authRefresh.ts').refreshAccessToken()
  assert.equal(vault.getAccessToken(), 'refreshed')
  assert.deepEqual(context.getAuthContext(), current)
})

test('late refresh success never stores the previous account token in a newer session', async () => {
  const response = deferred()
  let cleanups = 0
  const { load } = fixture({
    '@/features/auth/api/authTransport': { refreshSessionTransport: () => response.promise },
    './sessionCleanup': { endLocalSession: async () => { cleanups++ } },
  })
  const context = load('features/auth/model/authContext.ts')
  const vault = load('features/auth/model/tokenVault.ts')
  context.advanceAuthContext(1)
  const refresh = load('features/auth/model/authRefresh.ts').refreshAccessToken()
  context.advanceAuthContext(2)
  vault.setSessionTokens(tokens('new-account'))
  response.resolve(tokens('old-account'))
  await assert.rejects(refresh, { code: 'AUTH_CONTEXT_CHANGED' })
  assert.equal(vault.getAccessToken(), 'new-account')
  assert.equal(cleanups, 0)
})

test('late refresh 401 cannot log out the newly signed in account', async () => {
  const response = deferred()
  let cleanups = 0
  const { load } = fixture({
    '@/features/auth/api/authTransport': { refreshSessionTransport: () => response.promise },
    './sessionCleanup': { endLocalSession: async () => { cleanups++ } },
  })
  const context = load('features/auth/model/authContext.ts')
  const { ApiClientError } = load('shared/api/apiError.ts')
  context.advanceAuthContext(1)
  const refresh = load('features/auth/model/authRefresh.ts').refreshAccessToken()
  context.advanceAuthContext(2)
  response.reject(new ApiClientError({ code: 'AUTH_005', message: 'expired', status: 401, kind: 'api' }))
  await assert.rejects(refresh)
  assert.equal(cleanups, 0)
  assert.equal(context.getAuthContext().userId, 2)
})

test('requests started during logout cannot restore tokens by refreshing the old cookie', async () => {
  let calls = 0
  const { load } = fixture({
    '@/features/auth/api/authTransport': { refreshSessionTransport: async () => { calls++; return tokens('old-cookie') } },
    './sessionCleanup': { endLocalSession: async () => {} },
  })
  load('features/auth/model/authContext.ts').advanceAuthContext(null, false)
  await assert.rejects(load('features/auth/model/authRefresh.ts').refreshAccessToken(), { code: 'AUTH_CONTEXT_CHANGED' })
  assert.equal(calls, 0)
})

test('protected request checks the starting context after async refresh before HTTP dispatch', async () => {
  const response = deferred()
  let dispatched = 0
  const { load } = fixture({
    '@/features/auth/model/authRefresh': { refreshAccessToken: () => response.promise },
  })
  const context = load('features/auth/model/authContext.ts')
  const vault = load('features/auth/model/tokenVault.ts')
  const { protectedApi } = load('shared/api/protectedApi.ts')
  const old = context.advanceAuthContext(1)
  protectedApi.defaults.adapter = async () => { dispatched++; return {} }
  const request = protectedApi.get('/api/v1/push-devices/installation', { authContext: old })
  await Promise.resolve()
  context.advanceAuthContext(2)
  vault.setSessionTokens(tokens('new-token'))
  response.resolve()
  await assert.rejects(request, { code: 'AUTH_CONTEXT_CHANGED' })
  assert.equal(dispatched, 0)
})

test('protected 401 retry cannot reissue an old operation with the newer token', async () => {
  const response = deferred()
  const refreshStarted = deferred()
  let dispatched = 0
  const { load } = fixture({
    '@/features/auth/model/authRefresh': { refreshAccessToken: () => { refreshStarted.resolve(); return response.promise } },
  })
  const context = load('features/auth/model/authContext.ts')
  const vault = load('features/auth/model/tokenVault.ts')
  const { protectedApi } = load('shared/api/protectedApi.ts')
  context.advanceAuthContext(1)
  vault.setSessionTokens(tokens('old-token'))
  protectedApi.defaults.adapter = async (config) => {
    dispatched++
    throw { isAxiosError: true, config, response: { status: 401, data: { success: false, error: { code: 'AUTH_005', message: 'expired', fields: [] } } } }
  }
  const request = protectedApi.put('/api/v1/push-devices/installation', {})
  await refreshStarted.promise
  context.advanceAuthContext(2)
  vault.setSessionTokens(tokens('new-token'))
  response.resolve()
  await assert.rejects(request, { code: 'AUTH_CONTEXT_CHANGED' })
  assert.equal(dispatched, 1)
})
