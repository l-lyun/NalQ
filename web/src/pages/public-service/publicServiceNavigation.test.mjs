import assert from 'node:assert/strict'
import test from 'node:test'

import { readPublicReturnPath } from './publicServiceNavigation.ts'

test('uses an explicit internal return path', () => {
  assert.equal(readPublicReturnPath({ returnTo: '/profile' }), '/profile')
  assert.equal(readPublicReturnPath({ returnTo: '/sign-up?step=2' }), '/sign-up?step=2')
})

test('direct and unsafe entries return to the public login page', () => {
  assert.equal(readPublicReturnPath(undefined), '/login')
  assert.equal(readPublicReturnPath({ returnTo: 'https://example.com' }), '/login')
  assert.equal(readPublicReturnPath({ returnTo: '//example.com' }), '/login')
})
