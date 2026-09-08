const assert = require('node:assert/strict')
const path = require('node:path')
const test = require('node:test')
const fixture = require('./helpers/loadTs.cjs')
function setup(os, permission) {
  const calls = []
  let handler
  let tokenListener
  let fcmTokenListener
  const sdk = {
    IosAuthorizationStatus: { NOT_DETERMINED: 0, AUTHORIZED: 2, PROVISIONAL: 3, EPHEMERAL: 4 },
    AndroidImportance: { DEFAULT: 3 },
    getPermissionsAsync: async () => { calls.push('read'); return permission },
    requestPermissionsAsync: async () => { calls.push('request'); return { granted: true } },
    getDevicePushTokenAsync: async () => { calls.push('apns'); return { type: 'ios', data: 'apns-token' } },
    getExpoPushTokenAsync: async (options) => { calls.push(['token', options]); return { data: 'ExpoPushToken[test]' } },
    addPushTokenListener: (listener) => { tokenListener = listener; return { remove() {} } },
    setNotificationChannelAsync: async (id) => calls.push(['channel', id]),
    setNotificationHandler: (next) => { handler = next },
  }
  const firebase = {
    getMessaging: () => ({}),
    getToken: async () => { calls.push('fcm'); return 'firebase-registration-token' },
    onTokenRefresh: (_messaging, listener) => { fcmTokenListener = listener; return () => {} },
  }
  const { load } = fixture({ 'expo-notifications': sdk,
    '@react-native-firebase/messaging': firebase,
    'expo-constants': { expoConfig: { extra: { eas: { projectId: 'fixture-project' } } } },
    'react-native': { Platform: { OS: os } },
  })
  const provider = load(path.resolve(__dirname, '../../app/src/push/nativeNotificationProvider.ts'))
  return { provider, calls,
    emitToken: (token) => tokenListener?.(token), emitFcmToken: (token) => fcmTokenListener?.(token),
    getHandler: () => handler }
}

test('foreground handler disables banner, list, sound and badge', async () => {
  const h = setup('ios', { granted: true })
  h.provider.installForegroundNotificationSuppression()
  assert.deepEqual(await h.getHandler().handleNotification(), {
    shouldPlaySound: false, shouldSetBadge: false, shouldShowBanner: false, shouldShowList: false,
  })
})

test('iOS asks only for undetermined permission and resolves an FCM token after APNs registration', async () => {
  const h = setup('ios', { granted: false, ios: { status: 0 } })
  const result = await new h.provider.NativePushRegistrationProvider().resolve()
  assert.equal(result.permission, 'GRANTED')
  assert.equal(result.provider, 'FCM')
  assert.deepEqual(h.calls, ['read', 'request', 'apns', 'fcm'])
  const denied = setup('ios', { granted: false, ios: { status: 1 } })
  assert.deepEqual(await new denied.provider.NativePushRegistrationProvider().resolve(), {
    platform: 'IOS', provider: 'FCM', permission: 'DENIED', pushToken: null,
  })
  assert.deepEqual(denied.calls, ['read'])
})

test('Android creates its channel first and does not prompt again for granted permission', async () => {
  const h = setup('android', { granted: true, status: 'granted' })
  const result = await new h.provider.NativePushRegistrationProvider().resolve()
  assert.equal(result.platform, 'ANDROID')
  assert.equal(result.provider, 'EXPO')
  assert.deepEqual(h.calls, [['channel', 'quiz-results'], 'read', ['token', { projectId: 'fixture-project' }]])
})

test('native token listener ignores duplicate values but reports an actual rotation', () => {
  const h = setup('ios', { granted: true })
  const provider = new h.provider.NativePushRegistrationProvider()
  let changes = 0
  provider.subscribeToTokenChanges(() => { changes++ })

  h.emitFcmToken('same-token')
  h.emitFcmToken('same-token')
  h.emitFcmToken('rotated-token')

  assert.equal(changes, 2)
})
