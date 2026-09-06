const assert = require('node:assert/strict')
const path = require('node:path')
const test = require('node:test')
const fixture = require('./helpers/loadTs.cjs')
function setup(os, permission) {
  const calls = []
  let handler
  const sdk = {
    IosAuthorizationStatus: { NOT_DETERMINED: 0, AUTHORIZED: 2, PROVISIONAL: 3, EPHEMERAL: 4 },
    AndroidImportance: { DEFAULT: 3 },
    getPermissionsAsync: async () => { calls.push('read'); return permission },
    requestPermissionsAsync: async () => { calls.push('request'); return { granted: true } },
    getExpoPushTokenAsync: async (options) => { calls.push(['token', options]); return { data: 'ExpoPushToken[test]' } },
    setNotificationChannelAsync: async (id) => calls.push(['channel', id]),
    setNotificationHandler: (next) => { handler = next },
  }
  const { load } = fixture({ 'expo-notifications': sdk,
    'expo-constants': { expoConfig: { extra: { eas: { projectId: 'fixture-project' } } } },
    'react-native': { Platform: { OS: os } },
  })
  const provider = load(path.resolve(__dirname, '../../app/src/push/nativeNotificationProvider.ts'))
  return { provider, calls, getHandler: () => handler }
}

test('foreground handler disables banner, list, sound and badge', async () => {
  const h = setup('ios', { granted: true })
  h.provider.installForegroundNotificationSuppression()
  assert.deepEqual(await h.getHandler().handleNotification(), {
    shouldPlaySound: false, shouldSetBadge: false, shouldShowBanner: false, shouldShowList: false,
  })
})

test('iOS asks only for undetermined permission and uses configured project for the token', async () => {
  const h = setup('ios', { granted: false, ios: { status: 0 } })
  const result = await new h.provider.ExpoPushRegistrationProvider().resolve()
  assert.equal(result.permission, 'GRANTED')
  assert.deepEqual(h.calls, ['read', 'request', ['token', { projectId: 'fixture-project' }]])
  const denied = setup('ios', { granted: false, ios: { status: 1 } })
  assert.deepEqual(await new denied.provider.ExpoPushRegistrationProvider().resolve(), {
    platform: 'IOS', permission: 'DENIED', pushToken: null,
  })
  assert.deepEqual(denied.calls, ['read'])
})

test('Android creates its channel first and does not prompt again for granted permission', async () => {
  const h = setup('android', { granted: true, status: 'granted' })
  const result = await new h.provider.ExpoPushRegistrationProvider().resolve()
  assert.equal(result.platform, 'ANDROID')
  assert.deepEqual(h.calls, [['channel', 'quiz-results'], 'read', ['token', { projectId: 'fixture-project' }]])
})
