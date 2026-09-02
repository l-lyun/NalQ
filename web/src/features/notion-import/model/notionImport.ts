import { NOTION_ERROR_CODES, type NotionErrorCode } from '../api/notionImport.types.ts'

export type NotionErrorPresentation = {
  message: string
  recovery: 'connect' | 'reauth' | 'keep-workspace' | 'add-access' | 'retry'
}

const notionErrors: Record<NotionErrorCode, NotionErrorPresentation> = {
  NOTION_CONNECTION_REQUIRED: { message: '노션 연결이 필요해요', recovery: 'connect' },
  NOTION_REAUTH_REQUIRED: { message: '노션을 다시 연결해 주세요', recovery: 'reauth' },
  NOTION_WORKSPACE_MISMATCH: { message: '현재 연결된 워크스페이스와 달라요', recovery: 'keep-workspace' },
  NOTION_PAGE_NOT_ACCESSIBLE: { message: '이 페이지에 접근할 수 없어요', recovery: 'add-access' },
  NOTION_CONTENT_INCOMPLETE: { message: '페이지를 전부 가져오지 못했어요', recovery: 'retry' },
  NOTION_TEMPORARILY_UNAVAILABLE: { message: '지금은 노션에서 가져올 수 없어요', recovery: 'retry' },
}

export function isNotionErrorCode(value: unknown): value is NotionErrorCode {
  return typeof value === 'string' && (NOTION_ERROR_CODES as readonly string[]).includes(value)
}

export function getNotionErrorPresentation(code: unknown): NotionErrorPresentation {
  return isNotionErrorCode(code)
    ? notionErrors[code]
    : { message: '요청을 처리하지 못했어요. 다시 시도해 주세요.', recovery: 'retry' }
}

export function extractNotionPageId(input: string): string | null {
  let url: URL
  try {
    url = new URL(input.trim())
  } catch {
    return null
  }

  const hostname = url.hostname.toLowerCase()
  const isNotionHost = hostname === 'notion.so'
    || hostname.endsWith('.notion.so')
    || hostname === 'notion.site'
    || hostname.endsWith('.notion.site')
  if (!isNotionHost) return null

  const matches = [...decodeURIComponent(url.pathname).matchAll(/([0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/gi)]
  const value = matches.at(-1)?.[1]?.replaceAll('-', '').toLowerCase()
  if (!value) return null
  return `${value.slice(0, 8)}-${value.slice(8, 12)}-${value.slice(12, 16)}-${value.slice(16, 20)}-${value.slice(20)}`
}

export function formatNotionRelativeDate(value: string, now = new Date()) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const target = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
  const days = Math.max(0, Math.round((start - target) / 86_400_000))
  if (days === 0) return '오늘'
  if (days === 1) return '어제'
  return `${days}일 전`
}
