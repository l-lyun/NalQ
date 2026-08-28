import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { BackHandler, Linking, Platform, StyleSheet, Text, View } from 'react-native';
import WebView, {
  type WebViewNavigation,
} from 'react-native-webview';
import type {
  ShouldStartLoadRequest,
  WebViewErrorEvent,
  WebViewHttpErrorEvent,
  WebViewNavigationEvent,
  WebViewOpenWindowEvent,
} from 'react-native-webview/lib/WebViewTypes';

import { classifyNavigation } from './navigationPolicy';
import { ShellStateView, type ShellState } from './ShellStateView';

interface OpenMdWebViewProps {
  webOrigin: string;
  webUrl: string;
}

type VisibleShellState = Extract<ShellState, 'loading' | 'load-error' | 'renderer-error'> | 'ready';

const EXTERNAL_LINK_ERROR_DURATION_MS = 4_000;

export function OpenMdWebView({ webOrigin, webUrl }: OpenMdWebViewProps) {
  const webViewRef = useRef<WebView>(null);
  const firstDocumentVisibleRef = useRef(false);
  const loadFailedRef = useRef(false);
  const pendingMainDocumentUrlRef = useRef(webUrl);

  const [canGoBack, setCanGoBack] = useState(false);
  const [documentUrl, setDocumentUrl] = useState(webUrl);
  const [externalLinkError, setExternalLinkError] = useState(false);
  const [shellState, setShellState] = useState<VisibleShellState>('loading');
  const [webViewKey, setWebViewKey] = useState(0);

  const source = useMemo(() => ({ uri: documentUrl }), [documentUrl]);

  useEffect(() => {
    if (!externalLinkError) {
      return undefined;
    }

    const timeout = setTimeout(() => {
      setExternalLinkError(false);
    }, EXTERNAL_LINK_ERROR_DURATION_MS);

    return () => clearTimeout(timeout);
  }, [externalLinkError]);

  useEffect(() => {
    if (Platform.OS !== 'android') {
      return undefined;
    }

    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (shellState !== 'ready' || !canGoBack) {
        return false;
      }

      webViewRef.current?.goBack();
      return true;
    });

    return () => subscription.remove();
  }, [canGoBack, shellState]);

  const openExternalUrl = useCallback(async (url: string) => {
    try {
      await Linking.openURL(url);
    } catch {
      setExternalLinkError(true);
    }
  }, []);

  const applyNavigationPolicy = useCallback(
    (requestedUrl: string, openInternalInCurrentView: boolean) => {
      const decision = classifyNavigation(requestedUrl, webOrigin);

      if (decision.action === 'internal') {
        if (openInternalInCurrentView) {
          setDocumentUrl(decision.url);
        }
        return true;
      }

      if (decision.action === 'external') {
        void openExternalUrl(decision.url);
      }

      return false;
    },
    [openExternalUrl, webOrigin],
  );

  const handleShouldStartLoad = useCallback(
    (request: ShouldStartLoadRequest) => applyNavigationPolicy(request.url, false),
    [applyNavigationPolicy],
  );

  const handleOpenWindow = useCallback(
    (event: WebViewOpenWindowEvent) => {
      applyNavigationPolicy(event.nativeEvent.targetUrl, true);
    },
    [applyNavigationPolicy],
  );

  const showInitialLoadError = useCallback(() => {
    if (firstDocumentVisibleRef.current) {
      return;
    }

    loadFailedRef.current = true;
    setShellState('load-error');
  }, []);

  const handleLoadStart = useCallback((event: WebViewNavigationEvent) => {
    pendingMainDocumentUrlRef.current = event.nativeEvent.url;
    loadFailedRef.current = false;

    if (!firstDocumentVisibleRef.current) {
      setShellState('loading');
    }
  }, []);

  const handleLoad = useCallback(() => {
    if (loadFailedRef.current) {
      return;
    }

    firstDocumentVisibleRef.current = true;
    setShellState('ready');
  }, []);

  const handleError = useCallback(
    (event: WebViewErrorEvent) => {
      event.preventDefault();
      showInitialLoadError();
    },
    [showInitialLoadError],
  );

  const handleHttpError = useCallback(
    (event: WebViewHttpErrorEvent) => {
      const isPendingMainDocument =
        event.nativeEvent.url === pendingMainDocumentUrlRef.current;

      if (isPendingMainDocument && event.nativeEvent.statusCode >= 400) {
        showInitialLoadError();
      }
    },
    [showInitialLoadError],
  );

  const handleNavigationStateChange = useCallback((navigation: WebViewNavigation) => {
    setCanGoBack(navigation.canGoBack);
  }, []);

  const handleRendererTerminated = useCallback(() => {
    loadFailedRef.current = true;
    setCanGoBack(false);
    setShellState('renderer-error');
  }, []);

  const retry = useCallback(() => {
    firstDocumentVisibleRef.current = false;
    loadFailedRef.current = false;
    pendingMainDocumentUrlRef.current = webUrl;
    setCanGoBack(false);
    setDocumentUrl(webUrl);
    setShellState('loading');
    setWebViewKey((currentKey) => currentKey + 1);
  }, [webUrl]);

  const platformProps =
    Platform.OS === 'ios'
      ? {
          allowsBackForwardNavigationGestures: false,
          sharedCookiesEnabled: false,
        }
      : Platform.OS === 'android'
        ? {
            allowFileAccess: false,
            mixedContentMode: 'never' as const,
            setSupportMultipleWindows: true,
            thirdPartyCookiesEnabled: false,
          }
        : {};

  return (
    <View style={styles.container}>
      <WebView
        {...platformProps}
        allowFileAccessFromFileURLs={false}
        allowUniversalAccessFromFileURLs={false}
        domStorageEnabled
        incognito={false}
        javaScriptCanOpenWindowsAutomatically={false}
        key={webViewKey}
        onContentProcessDidTerminate={handleRendererTerminated}
        onError={handleError}
        onHttpError={handleHttpError}
        onLoad={handleLoad}
        onLoadStart={handleLoadStart}
        onNavigationStateChange={handleNavigationStateChange}
        onOpenWindow={handleOpenWindow}
        onRenderProcessGone={handleRendererTerminated}
        onShouldStartLoadWithRequest={handleShouldStartLoad}
        originWhitelist={['*']}
        ref={webViewRef}
        source={source}
        style={styles.webView}
      />

      {shellState === 'loading' ? <ShellStateView state="loading" /> : null}
      {shellState === 'load-error' ? (
        <ShellStateView onRetry={retry} state="load-error" />
      ) : null}
      {shellState === 'renderer-error' ? (
        <ShellStateView onRetry={retry} state="renderer-error" />
      ) : null}

      {externalLinkError ? (
        <View
          accessibilityLiveRegion="polite"
          accessibilityRole="alert"
          pointerEvents="none"
          style={styles.externalLinkNotice}
        >
          <Text style={styles.externalLinkNoticeText}>링크를 열 수 없어요.</Text>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#FFFFFF',
    flex: 1,
  },
  webView: {
    backgroundColor: '#FFFFFF',
    flex: 1,
  },
  externalLinkNotice: {
    alignSelf: 'center',
    backgroundColor: '#2B3038',
    borderRadius: 8,
    bottom: 24,
    maxWidth: 320,
    paddingHorizontal: 16,
    paddingVertical: 12,
    position: 'absolute',
  },
  externalLinkNoticeText: {
    color: '#FFFFFF',
    fontSize: 14,
    lineHeight: 20,
    textAlign: 'center',
  },
});
