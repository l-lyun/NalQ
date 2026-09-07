const assert = require('node:assert/strict')
const test = require('node:test')
const fixture = require('./helpers/loadTs.cjs')
const id = '11111111-1111-4111-8111-111111111111'
const bindingId = '22222222-2222-4222-8222-222222222222'
const request = { installationId: id, installationKey: 'A'.repeat(43),
  operationId: '33333333-3333-4333-8333-333333333333', operationIssuedAt: '2026-09-07T00:00:00Z',
  expectedRevision: 0, platform: 'IOS', permission: 'GRANTED', pushToken: 'ExpoPushToken[test]' }
const success = (data) => ({ data: { success: true, data, error: null } })

test('registration keeps installation key in its header and projects only approved response fields', async () => {
  let actual
  const { load } = fixture({ '@/shared/api/protectedApi': { protectedApi: { put: async (...args) => {
    actual = args
    return success({ installationId: id, revision: 1, bindingId, status: 'ACTIVE', userId: 42, unexpectedSecret: 'must-not-cross-bridge' })
  } } } })
  const context = load('features/auth/model/authContext.ts').advanceAuthContext(42)
  const result = await load('shared/native/pushApi.ts').registerPushDevice(request, context)
  assert.equal(result.outcome, 'SUCCESS')
  assert.equal(result.data.unexpectedSecret, undefined)
  assert.equal(actual[1].installationKey, undefined)
  assert.equal(actual[2].headers['X-Push-Installation-Key'], request.installationKey)
  assert.equal(actual[1].provider, 'EXPO')
  assert.deepEqual(actual[2].authContext, context)
})

test('denied permission omits pushToken and mismatched response identity is rejected', async () => {
  let body
  const { load } = fixture({ '@/shared/api/protectedApi': { protectedApi: { put: async (_, payload) => {
    body = payload
    return success({ installationId: id, revision: 0, bindingId: null, status: 'DISABLED', userId: 84 })
  } } } })
  const context = load('features/auth/model/authContext.ts').advanceAuthContext(42)
  const result = await load('shared/native/pushApi.ts').registerPushDevice({ ...request, permission: 'DENIED' }, context)
  assert.equal(Object.hasOwn(body, 'pushToken'), false)
  assert.deepEqual(result, { outcome: 'FAILED', errorCode: 'PUSH_RESPONSE_INVALID' })
})

test('anonymous revoke uses a separate cookie-free client and 404 is a completed no-op', async () => {
  let options, actual
  const { load } = fixture({
    '@/shared/api/protectedApi': { protectedApi: {} },
    axios: { create(config) { options = config; return { post: async (...args) => {
      actual = args
      throw { isAxiosError: true, response: { status: 404, data: { success: false, error: { code: 'COMMON_003', message: 'missing', fields: [] } } } }
    } } }, isAxiosError: (value) => value?.isAxiosError === true },
  })
  const result = await load('shared/native/pushApi.ts').revokePushDevice({ ...request, bindingId })
  assert.equal(options.withCredentials, false)
  assert.equal(actual[2].headers.Authorization, undefined)
  assert.equal(actual[1].installationKey, undefined)
  assert.equal(actual[1].pushToken, undefined)
  assert.deepEqual(result, { outcome: 'SUCCESS', data: { revoked: false } })
})

test('retry-after is preserved while raw request/provider text is not returned', () => {
  const { load } = fixture()
  const { ApiClientError } = load('shared/api/apiError.ts')
  const result = load('shared/native/pushApi.ts').pushFailure(new ApiClientError({
    code: 'PUSH_RATE_LIMITED', status: 429, retryAfterMs: 60000, message: 'private provider body', kind: 'api',
  }))
  assert.deepEqual(result, { outcome: 'RETRY', errorCode: 'PUSH_RATE_LIMITED', retryAfterMs: 60000 })
})
