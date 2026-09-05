export type NavigationDecision =
  | {
      action: 'internal';
      url: string;
    }
  | {
      action: 'external';
      url: string;
    }
  | {
      action: 'blocked';
    };

const EXTERNAL_PROTOCOLS = new Set(['mailto:', 'tel:']);

export function classifyNavigation(
  requestedUrl: string,
  webOrigin: string,
): NavigationDecision {
  let parsedUrl: URL;
  try {
    parsedUrl = new URL(requestedUrl);
  } catch {
    return { action: 'blocked' };
  }

  if (parsedUrl.protocol === 'http:' || parsedUrl.protocol === 'https:') {
    if (parsedUrl.origin === webOrigin) {
      return { action: 'internal', url: parsedUrl.href };
    }

    return { action: 'external', url: parsedUrl.href };
  }

  if (EXTERNAL_PROTOCOLS.has(parsedUrl.protocol)) {
    return { action: 'external', url: parsedUrl.href };
  }

  return { action: 'blocked' };
}

export function selectInternalRetryUrl(
  failedUrl: string | null,
  initialUrl: string,
  webOrigin: string,
): string | null {
  if (failedUrl) {
    const failedNavigation = classifyNavigation(failedUrl, webOrigin);
    if (failedNavigation.action === 'internal') {
      return failedNavigation.url;
    }
  }

  const initialNavigation = classifyNavigation(initialUrl, webOrigin);
  return initialNavigation.action === 'internal' ? initialNavigation.url : null;
}

export function shouldHandleWebViewBack(
  isDocumentReady: boolean,
  canGoBack: boolean,
): boolean {
  return isDocumentReady && canGoBack;
}

export function selectWebViewBackFallbackPath(
  isDocumentReady: boolean,
  currentUrl: string,
  webOrigin: string,
): '/' | '/profile' | null {
  if (!isDocumentReady) {
    return null;
  }

  const navigation = classifyNavigation(currentUrl, webOrigin);
  if (navigation.action !== 'internal') {
    return null;
  }

  const pathname = new URL(navigation.url).pathname.replace(/\/+$/, '') || '/';
  if (pathname === '/onboarding') return '/';
  if (pathname === '/profile/guide') return '/profile';
  return null;
}

export function isPendingMainDocumentHttpError(
  failedUrl: string,
  statusCode: number,
  pendingMainDocumentUrl: string,
  webOrigin: string,
): boolean {
  if (statusCode < 400) {
    return false;
  }

  const failedNavigation = classifyNavigation(failedUrl, webOrigin);
  return failedNavigation.action === 'internal'
    && failedNavigation.url === pendingMainDocumentUrl;
}
