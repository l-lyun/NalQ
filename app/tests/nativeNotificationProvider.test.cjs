const assert = require('node:assert/strict');
const fs = require('node:fs');
const Module = require('node:module');
const test = require('node:test');
const ts = require('typescript');

require.extensions['.ts'] = (module, filename) => {
  const source = fs.readFileSync(filename, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: {
      esModuleInterop: true,
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
    fileName: filename,
  }).outputText;
  module._compile(output, filename);
};

function loadProvider({ platform, permission = { granted: true } }) {
  const calls = [];
  let fcmRefreshListener = null;
  let nativeRefreshListener = null;
  const notifications = {
    AndroidImportance: { DEFAULT: 3 },
    DEFAULT_ACTION_IDENTIFIER: 'default',
    IosAuthorizationStatus: {
      AUTHORIZED: 2,
      PROVISIONAL: 3,
      EPHEMERAL: 4,
      NOT_DETERMINED: 0,
    },
    addPushTokenListener: (listener) => {
      nativeRefreshListener = listener;
      return { remove() {} };
    },
    getDevicePushTokenAsync: async () => {
      calls.push('apns-token');
      return { type: 'ios', data: 'apns-token' };
    },
    getExpoPushTokenAsync: async ({ projectId }) => {
      calls.push(`expo-token:${projectId}`);
      return { data: 'ExponentPushToken[android-token]' };
    },
    getPermissionsAsync: async () => permission,
    requestPermissionsAsync: async () => permission,
    setNotificationChannelAsync: async () => calls.push('android-channel'),
    setNotificationHandler() {},
  };
  const firebaseMessaging = {
    getMessaging: () => ({ app: 'default' }),
    getToken: async () => {
      calls.push('fcm-token');
      return 'firebase-registration-token';
    },
    onTokenRefresh: (_messaging, listener) => {
      fcmRefreshListener = listener;
      return () => {};
    },
  };

  const originalLoad = Module._load;
  Module._load = function load(request, parent, isMain) {
    if (request === '@react-native-firebase/messaging') return firebaseMessaging;
    if (request === 'expo-constants') {
      return {
        __esModule: true,
        default: { expoConfig: { extra: { eas: { projectId: 'eas-project-id' } } } },
      };
    }
    if (request === 'expo-notifications') return notifications;
    if (request === 'react-native') return { Platform: { OS: platform } };
    return originalLoad.call(this, request, parent, isMain);
  };

  const modulePath = require.resolve('../src/push/nativeNotificationProvider.ts');
  delete require.cache[modulePath];
  let exported;
  try {
    exported = require(modulePath);
  } finally {
    Module._load = originalLoad;
  }

  return {
    Provider: exported.NativePushRegistrationProvider,
    calls,
    emitFcmToken: (token) => fcmRefreshListener?.(token),
    emitNativeToken: (token) => nativeRefreshListener?.(token),
  };
}

test('iOS registers with APNs before resolving an FCM token', async () => {
  const harness = loadProvider({ platform: 'ios' });

  const target = await new harness.Provider().resolve();

  assert.deepEqual(target, {
    platform: 'IOS',
    provider: 'FCM',
    permission: 'GRANTED',
    pushToken: 'firebase-registration-token',
  });
  assert.deepEqual(harness.calls, ['apns-token', 'fcm-token']);
});

test('iOS FCM refresh events trigger one registration refresh per distinct token', () => {
  const harness = loadProvider({ platform: 'ios' });
  const provider = new harness.Provider();
  let refreshCount = 0;

  provider.subscribeToTokenChanges(() => { refreshCount += 1; });
  harness.emitFcmToken('first');
  harness.emitFcmToken('first');
  harness.emitFcmToken('second');

  assert.equal(refreshCount, 2);
});

test('Android keeps using its Expo token and native token listener', async () => {
  const harness = loadProvider({ platform: 'android' });
  const provider = new harness.Provider();
  let refreshCount = 0;

  const target = await provider.resolve();
  provider.subscribeToTokenChanges(() => { refreshCount += 1; });
  harness.emitNativeToken({ type: 'fcm', data: 'native-token' });

  assert.deepEqual(target, {
    platform: 'ANDROID',
    provider: 'EXPO',
    permission: 'GRANTED',
    pushToken: 'ExponentPushToken[android-token]',
  });
  assert.deepEqual(harness.calls, ['android-channel', 'expo-token:eas-project-id']);
  assert.equal(refreshCount, 1);
});
