const { test } = require('node:test')
const assert = require('node:assert/strict')
const fixture = require('./helpers/loadTs.cjs')

test('changing species preserves room and outfit; only owned matching slots may be saved', () => {
  const { equipItem, canEquip, sameEquipment } = fixture().load('features/character-shop/characterShop.types.ts')
  const equipment = { characterId: 'dragon', roomId: 'night', hatId: 'beret', topId: 'sweater' }
  const cat = { id: 'cat', category: 'CHARACTER' }
  const changed = equipItem(equipment, cat)
  assert.deepEqual(changed, { ...equipment, characterId: 'cat' })
  assert.equal(sameEquipment(changed, equipment), false)
  const catalog = [cat, { id: 'night', category: 'ROOM' }, { id: 'beret', category: 'HAT' }, { id: 'sweater', category: 'TOP' }]
  const shop = { catalog, ownedItemIds: ['cat', 'night', 'beret', 'sweater'] }
  assert.equal(canEquip(changed, shop), true)
  assert.equal(canEquip(changed, { ...shop, ownedItemIds: ['night', 'beret', 'sweater'] }), false)
  assert.equal(canEquip({ ...changed, hatId: 'cat' }, shop), false)
})

function apiFixture() {
  const calls = []
  let resolveResponse
  const transport = (method) => (...args) => { calls.push({ method, args }); return new Promise((resolve) => { resolveResponse = resolve }) }
  const f = fixture({
    '@/shared/api/protectedApi': { protectedApi: { get: transport('GET'), post: transport('POST'), put: transport('PUT') } },
    '@/shared/api/apiError': { unwrapApiResponse: (body) => { if (!body.success) throw new Error(body.error.code); return body.data } },
  })
  return { calls, api: f.load('features/character-shop/characterShop.api.ts'), auth: f.load('features/auth/model/authContext.ts'), respond: (data) => resolveResponse({ data: { success: true, data } }) }
}

test('purchase sends only item ID and the initiating account context, never price or balance', async () => {
  const f = apiFixture()
  const context = f.auth.advanceAuthContext(42)
  const pending = f.api.purchaseItem('hat-beret', context)
  assert.deepEqual(f.calls, [{ method: 'POST', args: ['/api/v1/character-shop/purchases', { itemId: 'hat-beret' }, { authContext: context }] }])
  f.respond({ balance: 10 })
  assert.deepEqual(await pending, { balance: 10 })
})

for (const operation of ['getCharacterShop', 'purchaseItem', 'saveEquipment']) {
  test(`${operation}: late previous-account response is rejected`, async () => {
    const f = apiFixture()
    const context = f.auth.advanceAuthContext(42)
    const pending = operation === 'getCharacterShop' ? f.api[operation](context)
      : f.api[operation](operation === 'purchaseItem' ? 'hat-beret' : {}, context)
    f.auth.advanceAuthContext(99)
    f.respond({ balance: 100 })
    await assert.rejects(pending, { code: 'AUTH_CONTEXT_CHANGED' })
  })
}

test('an old rendered action cannot issue a request under a new login', async () => {
  const f = apiFixture()
  const old = f.auth.advanceAuthContext(42)
  f.auth.advanceAuthContext(99)
  await assert.rejects(f.api.purchaseItem('cat', old), { code: 'AUTH_CONTEXT_CHANGED' })
  assert.equal(f.calls.length, 0)
})
