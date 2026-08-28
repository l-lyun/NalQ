export const DEFAULT_WEB_URL = 'http://localhost:5173';

export type WebUrlError =
  | 'invalid-url'
  | 'unsupported-protocol'
  | 'insecure-production-url'
  | 'embedded-credentials';

export type WebUrlResult =
  | {
      ok: true;
      origin: string;
      url: string;
    }
  | {
      ok: false;
      error: WebUrlError;
    };

export function resolveWebUrl(
  configuredUrl: string | undefined,
  isDevelopment: boolean,
): WebUrlResult {
  const candidate = configuredUrl?.trim() || DEFAULT_WEB_URL;

  let parsedUrl: URL;
  try {
    parsedUrl = new URL(candidate);
  } catch {
    return { ok: false, error: 'invalid-url' };
  }

  if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
    return { ok: false, error: 'unsupported-protocol' };
  }

  if (parsedUrl.username || parsedUrl.password) {
    return { ok: false, error: 'embedded-credentials' };
  }

  if (!isDevelopment && parsedUrl.protocol !== 'https:') {
    return { ok: false, error: 'insecure-production-url' };
  }

  return {
    ok: true,
    origin: parsedUrl.origin,
    url: parsedUrl.href,
  };
}
