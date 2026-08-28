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
