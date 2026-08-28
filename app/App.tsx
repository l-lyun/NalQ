import { StatusBar } from 'expo-status-bar';
import { StyleSheet, View } from 'react-native';

import { OpenMdWebView } from './src/shell/OpenMdWebView';
import { ShellStateView } from './src/shell/ShellStateView';
import { resolveWebUrl } from './src/shell/webUrl';

export default function App() {
  const webUrl = resolveWebUrl(process.env.EXPO_PUBLIC_WEB_URL, __DEV__);

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
