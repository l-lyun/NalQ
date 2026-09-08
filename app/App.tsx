import { useEffect } from 'react';
import { StatusBar } from 'expo-status-bar';
import { StyleSheet, View } from 'react-native';

import {
  createInstallationCredentials,
  nativePushStorage,
} from './src/push/nativePushStorage';
import { installForegroundNotificationSuppression } from './src/push/nativeNotificationProvider';
import { OpenMdWebView } from './src/shell/OpenMdWebView';
import { ShellStateView } from './src/shell/ShellStateView';
import { resolveWebUrl } from './src/shell/webUrl';

installForegroundNotificationSuppression();

export default function App() {
  const webUrl = resolveWebUrl(process.env.EXPO_PUBLIC_WEB_URL, __DEV__);

  useEffect(() => {
    void nativePushStorage.getOrCreateInstallation(createInstallationCredentials).catch(() => {
      // 푸시 저장소 준비 실패는 WebView 이용을 막지 않는다. 등록 단위에서 다시 복구한다.
    });
  }, []);

  return (
    <View style={styles.container}>
      <StatusBar style="dark" />
      {webUrl.ok ? (
        <OpenMdWebView webOrigin={webUrl.origin} webUrl={webUrl.url} />
      ) : (
        <ShellStateView state="configuration-error" />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
});
