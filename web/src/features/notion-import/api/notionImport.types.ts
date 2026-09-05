export const NOTION_ERROR_CODES = [
  'NOTION_CONNECTION_REQUIRED',
  'NOTION_REAUTH_REQUIRED',
  'NOTION_WORKSPACE_MISMATCH',
  'NOTION_PAGE_NOT_ACCESSIBLE',
  'NOTION_CONTENT_INCOMPLETE',
  'NOTION_TEMPORARILY_UNAVAILABLE',
] as const

export type NotionErrorCode = (typeof NOTION_ERROR_CODES)[number]
export type NotionConnectionStatus = 'DISCONNECTED' | 'CONNECTED' | 'REAUTH_REQUIRED'

export type NotionConnection = {
  status: NotionConnectionStatus
  workspaceName: string | null
}

export type NotionAuthorization = {
  authorizationUrl: string
  expiresAt: string
}

export type NotionPage = {
  pageId: string
  title: string
  lastEditedAt: string
}

export type NotionPageBatch = {
  items: NotionPage[]
  nextCursor: string | null
}

export type NotionImportResult = {
  sourceType: 'NOTION'
  title: string
  content: string
}
