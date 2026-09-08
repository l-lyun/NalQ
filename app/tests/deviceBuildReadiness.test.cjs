const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');

const appJson = require('../app.json').expo;
const easJson = require('../eas.json');
const dynamicConfig = require('../app.config.js');
const {
  EXPECTED_APP_ID,
  checkReadiness,
  parsePlatform,
  validateGoogleServicesConfig,
  validateGoogleServicesPlist,
  validateHttpsWebUrl,
} = require('../scripts/check-device-build-readiness.cjs');

function createReadinessFixture(t) {
  const rootDir = fs.mkdtempSync(path.join(os.tmpdir(), 'nalq-device-readiness-'));
  fs.writeFileSync(path.join(rootDir, 'app.json'), JSON.stringify({ expo: appJson }));
  fs.writeFileSync(path.join(rootDir, 'eas.json'), JSON.stringify(easJson));
  t.after(() => fs.rmSync(rootDir, { recursive: true, force: true }));
  return rootDir;
}

test('native identifiers and the installable internal profile stay aligned', () => {
  assert.equal(appJson.ios.bundleIdentifier, EXPECTED_APP_ID);
  assert.equal(appJson.android.package, EXPECTED_APP_ID);
  assert.equal(easJson.build['device-preview'].environment, 'preview');
  assert.equal(easJson.build['device-preview'].distribution, 'internal');
  assert.equal(easJson.build['device-preview'].android.buildType, 'apk');
  assert.ok(appJson.plugins.includes('@react-native-firebase/app'));
  assert.ok(appJson.plugins.some((plugin) => (
    Array.isArray(plugin)
    && plugin[0] === 'expo-build-properties'
    && plugin[1]?.ios?.useFrameworks === 'dynamic'
  )));
});

test('dynamic Expo config maps supplied Firebase files to their platform', () => {
  const originalAndroid = process.env.GOOGLE_SERVICES_JSON;
  const originalIos = process.env.GOOGLE_SERVICES_PLIST;
  delete process.env.GOOGLE_SERVICES_JSON;
  delete process.env.GOOGLE_SERVICES_PLIST;
  assert.equal(dynamicConfig({ config: appJson }).android.googleServicesFile, undefined);
  assert.equal(dynamicConfig({ config: appJson }).ios.googleServicesFile, undefined);

  process.env.GOOGLE_SERVICES_JSON = './google-services.json';
  process.env.GOOGLE_SERVICES_PLIST = './GoogleService-Info.plist';
  assert.equal(
    dynamicConfig({ config: appJson }).android.googleServicesFile,
    './google-services.json',
  );
  assert.equal(
    dynamicConfig({ config: appJson }).ios.googleServicesFile,
    './GoogleService-Info.plist',
  );

  if (originalAndroid === undefined) delete process.env.GOOGLE_SERVICES_JSON;
  else process.env.GOOGLE_SERVICES_JSON = originalAndroid;
  if (originalIos === undefined) delete process.env.GOOGLE_SERVICES_PLIST;
  else process.env.GOOGLE_SERVICES_PLIST = originalIos;
});

test('readiness parser uses the EAS platform and rejects missing hook context', () => {
  assert.equal(parsePlatform([], {}), 'all');
  assert.equal(parsePlatform(['--platform', 'ios'], {}), 'ios');
  assert.equal(parsePlatform(['--eas-build'], { EAS_BUILD_PLATFORM: 'android' }), 'android');
  assert.equal(parsePlatform(['--eas-build'], {}), undefined);
});

test('release web URL must be HTTPS and must not embed credentials', () => {
  assert.equal(validateHttpsWebUrl('https://preview.nalq.app'), null);
  assert.match(validateHttpsWebUrl('http://preview.nalq.app'), /HTTPS/);
  assert.match(validateHttpsWebUrl('https://user:secret@preview.nalq.app'), /비밀번호/);
  assert.match(validateHttpsWebUrl(undefined), /없습니다/);
});

test('google services config must contain the approved Android package', () => {
  const configured = {
    client: [{ client_info: { android_client_info: { package_name: EXPECTED_APP_ID } } }],
  };
  const mismatched = {
    client: [{ client_info: { android_client_info: { package_name: 'com.example.other' } } }],
  };

  assert.deepEqual(validateGoogleServicesConfig(configured), []);
  assert.match(validateGoogleServicesConfig(mismatched)[0], /com\.nalq\.app/);
});

test('GoogleService-Info.plist must contain the approved iOS bundle identifier', () => {
  const configured = '<?xml version="1.0"?><plist><dict><key>BUNDLE_ID</key><string>com.nalq.app</string></dict></plist>';
  const mismatched = '<?xml version="1.0"?><plist><dict><key>BUNDLE_ID</key><string>com.example.other</string></dict></plist>';

  assert.deepEqual(validateGoogleServicesPlist(configured), []);
  assert.match(validateGoogleServicesPlist(mismatched)[0], /com\.nalq\.app/);
});

test('full readiness check accepts valid platform Firebase config files', (t) => {
  const rootDir = createReadinessFixture(t);
  const googleServicesPath = path.join(rootDir, 'google-services.json');
  const googleServicesPlistPath = path.join(rootDir, 'GoogleService-Info.plist');
  fs.writeFileSync(googleServicesPath, JSON.stringify({
    client: [{ client_info: { android_client_info: { package_name: EXPECTED_APP_ID } } }],
  }));
  fs.writeFileSync(
    googleServicesPlistPath,
    `<?xml version="1.0"?><plist><dict><key>BUNDLE_ID</key><string>${EXPECTED_APP_ID}</string></dict></plist>`,
  );

  const baseEnvironment = {
    EXPO_PUBLIC_WEB_URL: 'https://preview.nalq.app',
    GOOGLE_SERVICES_PLIST: googleServicesPlistPath,
  };
  assert.deepEqual(checkReadiness({ rootDir, platform: 'ios', environment: baseEnvironment }), []);
  assert.deepEqual(checkReadiness({
    rootDir,
    platform: 'android',
    environment: { ...baseEnvironment, GOOGLE_SERVICES_JSON: googleServicesPath },
  }), []);
});

test('full iOS readiness check reports a missing plist and bundle mismatch', (t) => {
  const rootDir = createReadinessFixture(t);
  const googleServicesPlistPath = path.join(rootDir, 'GoogleService-Info.plist');
  const baseEnvironment = { EXPO_PUBLIC_WEB_URL: 'https://preview.nalq.app' };

  const missing = checkReadiness({ rootDir, platform: 'ios', environment: baseEnvironment });
  assert.ok(missing.some((error) => error.includes('GOOGLE_SERVICES_PLIST')));

  fs.writeFileSync(
    googleServicesPlistPath,
    '<?xml version="1.0"?><plist><dict><key>BUNDLE_ID</key><string>com.example.other</string></dict></plist>',
  );
  const mismatched = checkReadiness({
    rootDir,
    platform: 'ios',
    environment: { ...baseEnvironment, GOOGLE_SERVICES_PLIST: googleServicesPlistPath },
  });
  assert.ok(mismatched.some((error) => error.includes(EXPECTED_APP_ID)));
});

test('full Android readiness check reports a missing file and package mismatch', (t) => {
  const rootDir = createReadinessFixture(t);
  const googleServicesPath = path.join(rootDir, 'google-services.json');
  const baseEnvironment = { EXPO_PUBLIC_WEB_URL: 'https://preview.nalq.app' };

  const missing = checkReadiness({
    rootDir,
    platform: 'android',
    environment: { ...baseEnvironment, GOOGLE_SERVICES_JSON: googleServicesPath },
  });
  assert.ok(missing.some((error) => error.includes('파일을 읽을 수 없습니다')));

  fs.writeFileSync(googleServicesPath, JSON.stringify({
    client: [{ client_info: { android_client_info: { package_name: 'com.example.other' } } }],
  }));
  const mismatched = checkReadiness({
    rootDir,
    platform: 'android',
    environment: { ...baseEnvironment, GOOGLE_SERVICES_JSON: googleServicesPath },
  });
  assert.ok(mismatched.some((error) => error.includes(EXPECTED_APP_ID)));
});
