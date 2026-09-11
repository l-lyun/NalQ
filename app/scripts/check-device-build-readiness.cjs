const fs = require('node:fs');
const path = require('node:path');

const EXPECTED_APP_ID = 'com.nalq.app';
const EXPECTED_CHANNEL = 'quiz-results';
const SUPPORTED_PLATFORMS = new Set(['android', 'ios', 'all']);

function parsePlatform(argv, environment) {
  const explicitIndex = argv.indexOf('--platform');
  if (explicitIndex >= 0) {
    return argv[explicitIndex + 1];
  }
  if (argv.includes('--eas-build')) {
    return environment.EAS_BUILD_PLATFORM;
  }
  return environment.EAS_BUILD_PLATFORM || 'all';
}

function validateHttpsWebUrl(value) {
  if (!value || !value.trim()) {
    return 'EXPO_PUBLIC_WEB_URL이 없습니다. 실기기 빌드는 접근 가능한 HTTPS 웹 주소가 필요합니다.';
  }

  let parsed;
  try {
    parsed = new URL(value.trim());
  } catch {
    return 'EXPO_PUBLIC_WEB_URL이 올바른 URL이 아닙니다.';
  }

  if (parsed.protocol !== 'https:') {
    return 'EXPO_PUBLIC_WEB_URL은 HTTPS여야 합니다.';
  }
  if (parsed.username || parsed.password) {
    return 'EXPO_PUBLIC_WEB_URL에 사용자 이름이나 비밀번호를 포함할 수 없습니다.';
  }
  return null;
}

function validateGoogleServicesConfig(value, expectedPackage = EXPECTED_APP_ID) {
  if (!value || typeof value !== 'object' || !Array.isArray(value.client)) {
    return ['google-services.json에 client 목록이 없습니다.'];
  }

  const packageNames = value.client
    .map((client) => client?.client_info?.android_client_info?.package_name)
    .filter((packageName) => typeof packageName === 'string');

  if (!packageNames.includes(expectedPackage)) {
    return [`google-services.json에 Android package ${expectedPackage} 설정이 없습니다.`];
  }
  return [];
}

function validateGoogleServicesPlist(value, expectedBundleId = EXPECTED_APP_ID) {
  if (typeof value !== 'string') {
    return ['GoogleService-Info.plist를 읽을 수 없습니다.'];
  }
  const bundleId = value.match(
    /<key>\s*BUNDLE_ID\s*<\/key>\s*<string>\s*([^<]+?)\s*<\/string>/,
  )?.[1];
  if (bundleId !== expectedBundleId) {
    return [`GoogleService-Info.plist에 iOS bundle identifier ${expectedBundleId} 설정이 없습니다.`];
  }
  return [];
}

function hasNotificationPlugin(plugins) {
  if (!Array.isArray(plugins)) return false;
  return plugins.some((plugin) => {
    if (!Array.isArray(plugin)) return false;
    return plugin[0] === 'expo-notifications' && plugin[1]?.defaultChannel === EXPECTED_CHANNEL;
  });
}

function checkReadiness({ rootDir, platform, environment }) {
  const errors = [];
  if (!SUPPORTED_PLATFORMS.has(platform)) {
    return [`플랫폼을 android, ios, all 중 하나로 지정해야 합니다. 현재 값: ${platform || '(없음)'}`];
  }

  const appJson = JSON.parse(fs.readFileSync(path.join(rootDir, 'app.json'), 'utf8'));
  const easJson = JSON.parse(fs.readFileSync(path.join(rootDir, 'eas.json'), 'utf8'));
  const expo = appJson.expo;
  const preview = easJson.build?.['device-preview'];

  if (expo?.ios?.bundleIdentifier !== EXPECTED_APP_ID) {
    errors.push(`iOS bundle identifier는 ${EXPECTED_APP_ID}여야 합니다.`);
  }
  if (expo?.android?.package !== EXPECTED_APP_ID) {
    errors.push(`Android package는 ${EXPECTED_APP_ID}여야 합니다.`);
  }
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(expo?.extra?.eas?.projectId || '')) {
    errors.push('Expo EAS projectId가 없거나 UUID 형식이 아닙니다.');
  }
  if (!hasNotificationPlugin(expo?.plugins)) {
    errors.push(`expo-notifications plugin의 defaultChannel은 ${EXPECTED_CHANNEL}이어야 합니다.`);
  }
  if (preview?.environment !== 'preview' || preview?.distribution !== 'internal') {
    errors.push('EAS device-preview 프로필은 preview 환경의 internal distribution이어야 합니다.');
  }
  if (preview?.android?.buildType !== 'apk') {
    errors.push('EAS device-preview Android 산출물은 직접 설치 가능한 APK여야 합니다.');
  }
  if (preview?.ios?.buildConfiguration !== 'Release') {
    errors.push('EAS device-preview iOS 산출물은 Metro가 필요 없는 Release build여야 합니다.');
  }

  const webUrlError = validateHttpsWebUrl(environment.EXPO_PUBLIC_WEB_URL);
  if (webUrlError) errors.push(webUrlError);

  if (platform === 'android' || platform === 'all') {
    const googleServicesPath = environment.GOOGLE_SERVICES_JSON?.trim();
    if (!googleServicesPath) {
      errors.push('GOOGLE_SERVICES_JSON이 없습니다. Firebase에서 받은 Android 앱 설정 파일을 file 변수로 연결하세요.');
    } else {
      const resolvedPath = path.resolve(rootDir, googleServicesPath);
      let isReadableFile = false;
      try {
        isReadableFile = fs.statSync(resolvedPath).isFile();
      } catch {
        // 아래의 동일한 사용자용 오류로 정규화한다.
      }
      if (!isReadableFile) {
        errors.push('GOOGLE_SERVICES_JSON이 가리키는 파일을 읽을 수 없습니다.');
      } else {
        try {
          const googleServices = JSON.parse(fs.readFileSync(resolvedPath, 'utf8'));
          errors.push(...validateGoogleServicesConfig(googleServices));
        } catch {
          errors.push('GOOGLE_SERVICES_JSON 파일이 올바른 JSON이 아닙니다.');
        }
      }
    }
  }

  if (platform === 'ios' || platform === 'all') {
    const googleServicesPlistPath = environment.GOOGLE_SERVICES_PLIST?.trim();
    if (!googleServicesPlistPath) {
      errors.push('GOOGLE_SERVICES_PLIST가 없습니다. Firebase에서 받은 iOS 앱 설정 파일을 file 변수로 연결하세요.');
    } else {
      const resolvedPath = path.resolve(rootDir, googleServicesPlistPath);
      let isReadableFile = false;
      try {
        isReadableFile = fs.statSync(resolvedPath).isFile();
      } catch {
        // 아래의 동일한 사용자용 오류로 정규화한다.
      }
      if (!isReadableFile) {
        errors.push('GOOGLE_SERVICES_PLIST가 가리키는 파일을 읽을 수 없습니다.');
      } else {
        errors.push(...validateGoogleServicesPlist(fs.readFileSync(resolvedPath, 'utf8')));
      }
    }
  }

  return errors;
}

function main() {
  const platform = parsePlatform(process.argv.slice(2), process.env);
  const errors = checkReadiness({
    rootDir: path.resolve(__dirname, '..'),
    platform,
    environment: process.env,
  });

  if (errors.length > 0) {
    console.error(`실기기 빌드 사전점검 실패 (${platform || 'platform 미확인'}):`);
    for (const error of errors) console.error(`- ${error}`);
    process.exitCode = 1;
    return;
  }

  console.log(`실기기 빌드 사전점검 통과 (${platform}). 비밀이나 credential 내용은 검사 결과에 출력하지 않았습니다.`);
}

if (require.main === module) main();

module.exports = {
  EXPECTED_APP_ID,
  checkReadiness,
  parsePlatform,
  validateGoogleServicesConfig,
  validateGoogleServicesPlist,
  validateHttpsWebUrl,
};
