import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState, BackHandler, Linking, Platform, StyleSheet, Text, View } from 'react-native';
import WebView, {
  type WebViewNavigation,
} from 'react-native-webview';
import { randomUUID } from 'expo-crypto';
import type {
  ShouldStartLoadRequest,
  WebViewErrorEvent,
  WebViewHttpErrorEvent,
  WebViewMessageEvent,
  WebViewNavigationEvent,
  WebViewOpenWindowEvent,
} from 'react-native-webview/lib/WebViewTypes';

import {
  classifyNavigation,
  isPendingMainDocumentHttpError,
  selectInternalRetryUrl,
  selectWebViewBackFallbackPath,
  shouldHandleWebViewBack,
} from './navigationPolicy';
import { ShellStateView, type ShellState } from './ShellStateView';
import {
  PUSH_BRIDGE_VERSION,
  createHelloMessage,
  createMainDocumentBridgeScript,
  createNativeFeatureMessage,
  createNativeMessageDispatchScript,
  decideAuthState,
  parseTransportMessage,
  parseWebFeatureMessage,
  parseWebReadyMessage,
  serializeNativeMessage,
  type AcceptedAuthState,
  type HelloMessage,
} from '../push/bridgeProtocol';
import {
  createInstallationCredentials,
  nativePushStorage,
} from '../push/nativePushStorage';
import { ExpoPushRegistrationProvider } from '../push/nativeNotificationProvider';
import { PushRegistrationCoordinator } from '../push/pushRegistrationCoordinator';

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
  const bridgeSessionIdRef = useRef<string | null>(randomUUID());
  const documentNonceRef = useRef(`${randomUUID()}.${randomUUID()}`);
  const documentStartedRef = useRef(false);
  const bridgeMessageEnabledRef = useRef(false);
  const coordinatorSessionRef = useRef<string | null>(null);
  const lastHelloRef = useRef<{ replyTo: string; message: HelloMessage } | null>(null);
  const acceptedAuthStateRef = useRef<AcceptedAuthState | null>(null);
  const registrationProviderRef = useRef<ExpoPushRegistrationProvider | null>(null);
  const coordinatorRef = useRef<PushRegistrationCoordinator | null>(null);

  if (!registrationProviderRef.current) {
    registrationProviderRef.current = new ExpoPushRegistrationProvider();
  }
  if (!coordinatorRef.current) {
    coordinatorRef.current = new PushRegistrationCoordinator({
      storage: nativePushStorage,
      createInstallation: createInstallationCredentials,
      registrationProvider: registrationProviderRef.current,
      createMessageId: randomUUID,
      now: () => new Date().toISOString(),
      schedule: (callback, delayMs) => setTimeout(callback, delayMs),
      cancelSchedule: (handle) => clearTimeout(handle as ReturnType<typeof setTimeout>),
    });
  }
  const coordinator = coordinatorRef.current;

  const [canGoBack, setCanGoBack] = useState(false);
  const [documentUrl, setDocumentUrl] = useState(webUrl);
  const [externalLinkError, setExternalLinkError] = useState(false);
  const [shellState, setShellState] = useState<VisibleShellState>('loading');
  const [webViewKey, setWebViewKey] = useState(0);

  const source = useMemo(() => ({ uri: documentUrl }), [documentUrl]);
  const initialDocumentBridgeScript = useMemo(
    () => createMainDocumentBridgeScript(webOrigin, documentNonceRef.current),
    [webOrigin],
  );

  useEffect(() => {
    const tokenSubscription = registrationProviderRef.current?.subscribeToTokenChanges(() => {
      void coordinator.refreshRegistration().catch(() => {
        // 다음 foreground 또는 웹 요청에서 동일 자격으로 다시 확인한다.
      });
    });
    const appStateSubscription = AppState.addEventListener('change', (nextState) => {
      if (nextState === 'active') {
        void coordinator.refreshRegistration().catch(() => {
          // foreground 복귀 실패는 WebView 이용을 막지 않는다.
        });
      }
    });
    return () => {
      tokenSubscription?.remove();
      appStateSubscription.remove();
      coordinator.disconnect();
    };
  }, [coordinator]);

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
      const fallbackPath = selectWebViewBackFallbackPath(
        shellState === 'ready',
        visibleMainDocumentUrlRef.current,
        webOrigin,
      );
      if (fallbackPath) {
        const fallbackUrl = new URL(fallbackPath, webOrigin).href;
        webViewRef.current?.injectJavaScript(
          `window.location.replace(${JSON.stringify(fallbackUrl)}); true;`,
        );
        return true;
      }

      if (!shouldHandleWebViewBack(shellState === 'ready', canGoBack)) {
        return false;
      }

      webViewRef.current?.goBack();
      return true;
    });

    return () => subscription.remove();
  }, [canGoBack, shellState, webOrigin]);

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
    bridgeMessageEnabledRef.current = false;
    coordinator.disconnect();
    coordinatorSessionRef.current = null;
    setShellState('load-error');
  }, [coordinator, webOrigin]);

  const handleLoadStart = useCallback((event: WebViewNavigationEvent) => {
    const navigation = classifyNavigation(event.nativeEvent.url, webOrigin);
    if (navigation.action !== 'internal') {
      return;
    }

    pendingMainDocumentUrlRef.current = navigation.url;
    loadFailedRef.current = false;
    if (documentStartedRef.current) {
      bridgeSessionIdRef.current = randomUUID();
      documentNonceRef.current = `${randomUUID()}.${randomUUID()}`;
    } else {
      documentStartedRef.current = true;
    }
    coordinator.disconnect();
    coordinatorSessionRef.current = null;
    bridgeMessageEnabledRef.current = false;
    lastHelloRef.current = null;
    acceptedAuthStateRef.current = null;

    if (!documentVisibleRef.current) {
      setShellState('loading');
    }
  }, [coordinator, webOrigin]);

  const sendNativeFeatureMessage = useCallback((
    type: string,
    payload: unknown,
    authEpoch: number,
  ) => {
    const bridgeSessionId = bridgeSessionIdRef.current;
    if (!bridgeSessionId || coordinatorSessionRef.current !== bridgeSessionId) {
      return null;
    }
    const messageId = randomUUID();
    const message = createNativeFeatureMessage(
      type,
      bridgeSessionId,
      authEpoch,
      payload,
      messageId,
    );
    const serialized = serializeNativeMessage(message);
    webViewRef.current?.injectJavaScript(createNativeMessageDispatchScript(
      serialized,
      webOrigin,
      documentNonceRef.current,
    ));
    return messageId;
  }, [webOrigin]);

  const handleBridgeMessage = useCallback((event: WebViewMessageEvent) => {
    if (!bridgeMessageEnabledRef.current) {
      return;
    }
    const sourceNavigation = classifyNavigation(event.nativeEvent.url, webOrigin);
    if (sourceNavigation.action !== 'internal') {
      return;
    }

    const rawMessage = parseTransportMessage(
      event.nativeEvent.data,
      documentNonceRef.current,
    );
    if (!rawMessage) {
      return;
    }
    const webReady = parseWebReadyMessage(rawMessage);
    if (webReady) {
      if (!webReady.payload.versions.includes(PUSH_BRIDGE_VERSION)) {
        return;
      }

      const bridgeSessionId = bridgeSessionIdRef.current;
      if (!bridgeSessionId) {
        return;
      }

      let hello = lastHelloRef.current?.replyTo === webReady.messageId
        ? lastHelloRef.current.message
        : null;
      if (hello === null) {
        hello = createHelloMessage(
          bridgeSessionId,
          webReady.messageId,
          randomUUID(),
        );
        lastHelloRef.current = { replyTo: webReady.messageId, message: hello };
      }

      const serializedHello = serializeNativeMessage(hello);
      webViewRef.current?.injectJavaScript(createNativeMessageDispatchScript(
        serializedHello,
        webOrigin,
        documentNonceRef.current,
      ));
      if (coordinatorSessionRef.current !== bridgeSessionId) {
        coordinatorSessionRef.current = bridgeSessionId;
        coordinator.connect(bridgeSessionId, sendNativeFeatureMessage);
        void coordinator.flushPendingRevokes().catch(() => {
          // durable pending은 유지되며 다음 bridge/foreground에서 재시도한다.
        });
      }
      return;
    }

    const bridgeSessionId = bridgeSessionIdRef.current;
    if (!bridgeSessionId) {
      return;
    }

    const featureMessage = parseWebFeatureMessage(rawMessage, bridgeSessionId);
    if (!featureMessage) {
      return;
    }

    if (featureMessage.type === 'AUTH_STATE') {
      const decision = decideAuthState(
        acceptedAuthStateRef.current,
        featureMessage,
        ['push-v1'],
      );
      if (decision.accepted) {
        acceptedAuthStateRef.current = decision.state;
        void coordinator.acceptAuthState(decision.state);
      }
      return;
    }
    if (featureMessage.type === 'PUSH_REGISTER_REQUEST') {
      void coordinator.requestRegistration(featureMessage.authEpoch).catch(() => {
        // 권한/토큰/저장 실패는 다음 명시 요청 또는 foreground에서 재시도한다.
      });
      return;
    }
    if (featureMessage.type === 'PUSH_STATE_RESULT') {
      void coordinator.acceptStateResult(featureMessage).catch(() => {});
      return;
    }
    if (featureMessage.type === 'PUSH_REGISTER_RESULT') {
      void coordinator.acceptRegistrationResult(featureMessage).catch(() => {});
      return;
    }
    if (featureMessage.type === 'SESSION_ENDING') {
      void coordinator.captureSessionEnding(featureMessage).catch(() => {
        // 저장 실패 시 ACK를 보내지 않아 웹의 제한 대기 뒤 기존 logout이 진행된다.
      });
      return;
    }
    if (featureMessage.type === 'PUSH_REVOKE_RESULT') {
      void coordinator.acceptRevokeResult(featureMessage).catch(() => {});
    }
  }, [coordinator, sendNativeFeatureMessage, webOrigin]);

  const handleLoad = useCallback((event: WebViewNavigationEvent) => {
    if (loadFailedRef.current) {
      return;
    }

    const navigation = classifyNavigation(event.nativeEvent.url, webOrigin);
    if (navigation.action !== 'internal') {
      return;
    }
    visibleMainDocumentUrlRef.current = navigation.url;

    documentVisibleRef.current = true;
    failedMainDocumentUrlRef.current = null;
    bridgeMessageEnabledRef.current = true;
    webViewRef.current?.injectJavaScript(
      createMainDocumentBridgeScript(webOrigin, documentNonceRef.current),
    );
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
    bridgeMessageEnabledRef.current = false;
    coordinator.disconnect();
    coordinatorSessionRef.current = null;
    setCanGoBack(false);
    setShellState('renderer-error');
  }, [coordinator]);

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
        injectedJavaScriptBeforeContentLoaded={initialDocumentBridgeScript}
        injectedJavaScriptBeforeContentLoadedForMainFrameOnly
        javaScriptCanOpenWindowsAutomatically={false}
        key={webViewKey}
        onContentProcessDidTerminate={handleRendererTerminated}
        onError={handleError}
        onHttpError={handleHttpError}
        onLoad={handleLoad}
        onLoadStart={handleLoadStart}
        onMessage={handleBridgeMessage}
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
