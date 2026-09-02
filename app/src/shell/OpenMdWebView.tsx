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

import {
  classifyNavigation,
  isPendingMainDocumentHttpError,
  selectInternalRetryUrl,
  shouldHandleWebViewBack,
} from './navigationPolicy';
import { ShellStateView, type ShellState } from './ShellStateView';

interface OpenMdWebViewProps {
  webOrigin: string;
  webUrl: string;
}

type VisibleShellState = Extract<ShellState, 'loading' | 'load-error' | 'renderer-error'> | 'ready';

const EXTERNAL_LINK_ERROR_DURATION_MS = 4_000;

export function OpenMdWebView({ webOrigin, webUrl }: OpenMdWebViewProps) {
  const webViewRef = useRef<WebView>(null);
  const documentVisibleRef = useRef(false);
  const failedMainDocumentUrlRef = useRef<string | null>(null);
  const loadFailedRef = useRef(false);
  const pendingMainDocumentUrlRef = useRef(webUrl);
  const visibleMainDocumentUrlRef = useRef(webUrl);

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
      if (!shouldHandleWebViewBack(shellState === 'ready', canGoBack)) {
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

  const showMainDocumentLoadError = useCallback((failedUrl: string) => {
    const failedNavigation = classifyNavigation(failedUrl, webOrigin);
    if (failedNavigation.action !== 'internal') {
      return;
    }

    failedMainDocumentUrlRef.current = failedNavigation.url;
    loadFailedRef.current = true;
    setShellState('load-error');
  }, [webOrigin]);

  const handleLoadStart = useCallback((event: WebViewNavigationEvent) => {
    const navigation = classifyNavigation(event.nativeEvent.url, webOrigin);
    if (navigation.action !== 'internal') {
      return;
    }

    pendingMainDocumentUrlRef.current = navigation.url;
    loadFailedRef.current = false;

    if (!documentVisibleRef.current) {
      setShellState('loading');
    }
  }, [webOrigin]);

  const handleLoad = useCallback((event: WebViewNavigationEvent) => {
    if (loadFailedRef.current) {
      return;
    }

    const navigation = classifyNavigation(event.nativeEvent.url, webOrigin);
    if (navigation.action === 'internal') {
      visibleMainDocumentUrlRef.current = navigation.url;
    }

    documentVisibleRef.current = true;
    failedMainDocumentUrlRef.current = null;
    setShellState('ready');
  }, [webOrigin]);

  const handleError = useCallback(
    (event: WebViewErrorEvent) => {
      event.preventDefault();
      showMainDocumentLoadError(event.nativeEvent.url);
    },
    [showMainDocumentLoadError],
  );

  const handleHttpError = useCallback(
    (event: WebViewHttpErrorEvent) => {
      if (isPendingMainDocumentHttpError(
        event.nativeEvent.url,
        event.nativeEvent.statusCode,
        pendingMainDocumentUrlRef.current,
        webOrigin,
      )) {
        showMainDocumentLoadError(event.nativeEvent.url);
      }
    },
    [showMainDocumentLoadError, webOrigin],
  );

  const handleNavigationStateChange = useCallback((navigation: WebViewNavigation) => {
    setCanGoBack(navigation.canGoBack);

    const currentNavigation = classifyNavigation(navigation.url, webOrigin);
    if (currentNavigation.action === 'internal') {
      if (navigation.loading) {
        pendingMainDocumentUrlRef.current = currentNavigation.url;
      } else {
        visibleMainDocumentUrlRef.current = currentNavigation.url;
      }
    }
  }, [webOrigin]);

  const handleRendererTerminated = useCallback(() => {
    loadFailedRef.current = true;
    setCanGoBack(false);
    setShellState('renderer-error');
  }, []);

  const retry = useCallback(() => {
    const retryUrl = selectInternalRetryUrl(
      shellState === 'renderer-error'
        ? visibleMainDocumentUrlRef.current
        : failedMainDocumentUrlRef.current,
      webUrl,
      webOrigin,
    );
    if (!retryUrl) {
      return;
    }

    documentVisibleRef.current = false;
    failedMainDocumentUrlRef.current = null;
    loadFailedRef.current = false;
    pendingMainDocumentUrlRef.current = retryUrl;
    setCanGoBack(false);
    setDocumentUrl(retryUrl);
    setShellState('loading');
    setWebViewKey((currentKey) => currentKey + 1);
  }, [shellState, webOrigin, webUrl]);

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
