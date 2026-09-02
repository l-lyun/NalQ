import {
  IconArrowClockwiseCircularLine,
  IconDocumentLine,
} from '@karrotmarket/react-monochrome-icon'
import {
  ActionButton,
  BottomSheet,
  Box,
  ContentDialog,
  Flex,
  Icon,
  Portal,
  RadioGroup,
  RadioGroupField,
  Skeleton,
  Text,
  VStack,
} from '@seed-design/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  type FormEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import {
  disconnectNotion,
  getNotionPages,
  importNotionPage,
  notionConnectionQueryOptions,
  notionImportKeys,
  startNotionAuthorization,
} from '@/features/notion-import/api/notionImport.api'
import type { NotionPage } from '@/features/notion-import/api/notionImport.types'
import {
  extractNotionPageId,
  formatNotionRelativeDate,
  getNotionErrorPresentation,
  isNotionErrorCode,
} from '@/features/notion-import/model/notionImport'
import { toApiClientError } from '@/shared/api/apiError'

import {
  LearningField,
  LearningNotice,
  LearningScreenHeader,
  LearningTextInput,
} from './components/LearningPrimitives'
import './learning.css'

const PAGE_REVEAL_SIZE = 5

type CallbackNotice = { outcome?: 'connected' | 'cancelled' | 'failed'; error?: string }
type ConfirmKind = 'disconnect' | 'switch-workspace' | null

export function NotionImportPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const initialCallback = useRef(readCallbackNotice(location.search))
  const [callbackNotice] = useState(initialCallback.current)
  const [rawQuery, setRawQuery] = useState('')
  const [committedQuery, setCommittedQuery] = useState('')
  const [composing, setComposing] = useState(false)
  const [pages, setPages] = useState<NotionPage[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [visibleCount, setVisibleCount] = useState(PAGE_REVEAL_SIZE)
  const [selectedPageId, setSelectedPageId] = useState('')
  const [pageLoading, setPageLoading] = useState(false)
  const [moreLoading, setMoreLoading] = useState(false)
  const [pageError, setPageError] = useState<string>()
  const [importError, setImportError] = useState<string>()
  const [manageOpen, setManageOpen] = useState(false)
  const [linkOpen, setLinkOpen] = useState(false)
  const [link, setLink] = useState('')
  const [linkError, setLinkError] = useState<string>()
  const [confirm, setConfirm] = useState<ConfirmKind>(null)
  const requestSequence = useRef(0)
  const connection = useQuery({
    ...notionConnectionQueryOptions,
    enabled: !location.search,
  })
  const importing = useMutation({ mutationFn: importNotionPage })
  const authorizing = useMutation({
    mutationFn: () => startNotionAuthorization(`${window.location.origin}/learning/import/notion`),
    onSuccess: ({ authorizationUrl }) => window.location.assign(authorizationUrl),
  })
  const disconnecting = useMutation({
    mutationFn: disconnectNotion,
    onSuccess: async () => {
      setConfirm(null)
      setManageOpen(false)
      setPages([])
      setSelectedPageId('')
      await queryClient.invalidateQueries({ queryKey: notionImportKeys.connection() })
    },
  })

  useEffect(() => {
    if (!location.search) return
    navigate(location.pathname, { replace: true, state: location.state })
  }, [location.pathname, location.search, location.state, navigate])

  useEffect(() => {
    if (composing) return
    const timer = window.setTimeout(() => setCommittedQuery(rawQuery.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [composing, rawQuery])

  const loadFirstBatch = useCallback(async (preserveCurrent = false) => {
    const sequence = ++requestSequence.current
    setPageLoading(true)
    setPageError(undefined)
    setSelectedPageId('')
    setNextCursor(null)
    if (!preserveCurrent) {
      setPages([])
      setVisibleCount(PAGE_REVEAL_SIZE)
    }
    try {
      const batch = await getNotionPages({ query: committedQuery })
      if (sequence !== requestSequence.current) return
      setPages(batch.items)
      setNextCursor(batch.nextCursor)
      setVisibleCount(PAGE_REVEAL_SIZE)
    } catch (error) {
      if (sequence !== requestSequence.current) return
      setPageError(presentationForError(error))
    } finally {
      if (sequence === requestSequence.current) setPageLoading(false)
    }
  }, [committedQuery])

  useEffect(() => {
    if (connection.data?.status !== 'CONNECTED') return
    void loadFirstBatch()
  }, [connection.data?.status, loadFirstBatch])

  const visiblePages = useMemo(() => pages.slice(0, visibleCount), [pages, visibleCount])
  const interactionLocked = importing.isPending || authorizing.isPending || disconnecting.isPending

  const showMore = async () => {
    if (visibleCount < pages.length) {
      setVisibleCount((current) => Math.min(current + PAGE_REVEAL_SIZE, pages.length))
      return
    }
    if (!nextCursor) return
    setMoreLoading(true)
    setPageError(undefined)
    try {
      const batch = await getNotionPages({ query: committedQuery, cursor: nextCursor })
      setPages((current) => [...current, ...batch.items])
      setNextCursor(batch.nextCursor)
      setVisibleCount((current) => current + PAGE_REVEAL_SIZE)
    } catch (error) {
      setPageError(presentationForError(error))
    } finally {
      setMoreLoading(false)
    }
  }

  const beginAuthorization = () => {
    setImportError(undefined)
    authorizing.mutate()
  }

  const finishImport = async (pageId: string, fromLink = false) => {
    setImportError(undefined)
    if (fromLink) setLinkError(undefined)
    try {
      const result = await importing.mutateAsync(pageId)
      navigate('/learning/materials/new', {
        state: { sourceType: result.sourceType, title: result.title, content: result.content },
      })
    } catch (error) {
      const code = toApiClientError(error).code
      const message = presentationForError(error)
      if (fromLink) setLinkError(message)
      else setImportError(message)
      if (code === 'NOTION_CONNECTION_REQUIRED' || code === 'NOTION_REAUTH_REQUIRED') {
        void connection.refetch()
      }
    }
  }

  const submitLink = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!link.trim()) {
      setLinkError('노션 페이지 링크를 입력해 주세요.')
      return
    }
    const pageId = extractNotionPageId(link)
    if (!pageId) {
      setLinkError('올바른 노션 페이지 링크를 입력해 주세요.')
      return
    }
    void finishImport(pageId, true)
  }

  const confirmDisconnect = async () => {
    if (confirm === 'switch-workspace') {
      try {
        await disconnecting.mutateAsync()
        authorizing.mutate()
      } catch {
        // The mutation keeps the current connection and exposes the adjacent error.
      }
      return
    }
    disconnecting.mutate()
  }

  return (
    <VStack className="learning-management-page" bg="bg.layerDefault">
      <VStack className="learning-content notion-import-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x6">
        <LearningScreenHeader title="노션에서 가져오기" onBack={() => navigate('/learning/new')} />

        {connection.isPending ? <ConnectionSkeleton /> : connection.isError ? (
          <InlineStatus
            message={presentationForError(connection.error)}
            action="다시 시도"
            onAction={() => void connection.refetch()}
          />
        ) : connection.data?.status !== 'CONNECTED' ? (
          <DisconnectedState
            reauth={connection.data?.status === 'REAUTH_REQUIRED'}
            callbackNotice={callbackNotice}
            pending={authorizing.isPending}
            error={authorizing.isError ? presentationForError(authorizing.error) : undefined}
            onConnect={beginAuthorization}
            onDirect={() => navigate('/learning/materials/new', { state: { sourceType: 'PASTE', title: '', content: '' } })}
          />
        ) : (
          <VStack gap="x6" aria-busy={interactionLocked}>
            {callbackNotice.outcome === 'connected' || callbackNotice.error ? (
              <Box aria-live="polite">
                <LearningNotice>
                  {callbackNotice.error ? getNotionErrorPresentation(callbackNotice.error).message : '노션 연결이 완료됐어요.'}
                </LearningNotice>
              </Box>
            ) : null}
            {authorizing.isError ? (
              <InlineStatus message={presentationForError(authorizing.error)} action="다시 시도" onAction={beginAuthorization} />
            ) : null}
            <Flex className="notion-workspace-row" align="center" justify="space-between" gap="x3">
              <VStack minWidth="0px" gap="x1" align="flex-start">
                <Text textStyle="t3Regular" color="fg.neutralMuted">연결된 워크스페이스</Text>
                <Text className="learning-long-title" textStyle="t5Medium" color="fg.neutral">
                  {connection.data.workspaceName || '연결된 노션 워크스페이스'}
                </Text>
              </VStack>
              <ActionButton type="button" size="small" variant="ghost" disabled={interactionLocked} onClick={() => setManageOpen(true)}>
                관리
              </ActionButton>
            </Flex>

            <VStack gap="x4">
              <Flex align="center" justify="space-between" gap="x3">
                <Text as="h2" textStyle="t9Bold" color="fg.neutral">페이지 선택</Text>
                <ActionButton
                  type="button"
                  size="small"
                  layout="iconOnly"
                  variant="ghost"
                  aria-label="페이지 목록 새로고침"
                  disabled={interactionLocked || pageLoading}
                  loading={pageLoading && pages.length > 0}
                  onClick={() => void loadFirstBatch(true)}
                >
                  <Icon svg={<IconArrowClockwiseCircularLine />} size="x5" />
                </ActionButton>
              </Flex>
              <LearningField label="노션 페이지 제목 검색">
                <LearningTextInput
                  value={rawQuery}
                  disabled={interactionLocked}
                  placeholder="페이지 제목을 입력하세요"
                  enterKeyHint="search"
                  onCompositionStart={() => setComposing(true)}
                  onCompositionEnd={(event) => {
                    setComposing(false)
                    setRawQuery(event.currentTarget.value)
                  }}
                  onChange={(event) => setRawQuery(event.currentTarget.value)}
                  onKeyDown={(event) => {
                    if (event.key !== 'Enter' || event.nativeEvent.isComposing) return
                    event.preventDefault()
                    setCommittedQuery(event.currentTarget.value.trim())
                  }}
                />
              </LearningField>

              {pageLoading && pages.length === 0 ? (
                <VStack gap="x3" role="status" aria-label="노션 페이지를 불러오는 중">
                  {Array.from({ length: 5 }, (_, index) => <Skeleton key={index} width="full" height="x16" />)}
                </VStack>
              ) : pageError && pages.length === 0 ? (
                <InlineStatus message={pageError} action="다시 시도" onAction={() => void loadFirstBatch()} />
              ) : visiblePages.length ? (
                <RadioGroupField.Root
                  aria-label="가져올 노션 페이지"
                  name="notion-page"
                  value={selectedPageId}
                  disabled={interactionLocked || pageLoading}
                  onValueChange={(value: string) => {
                    setSelectedPageId(value)
                    setImportError(undefined)
                  }}
                ><RadioGroup.Root className="notion-page-list">
                    {visiblePages.map((page) => (
                    <RadioGroup.Item className="notion-page-row" key={page.pageId} value={page.pageId}>
                      <Icon svg={<IconDocumentLine />} size="x5" aria-hidden />
                      <VStack className="notion-page-copy" minWidth="0px" gap="x1" align="flex-start">
                        <RadioGroup.ItemLabel className="notion-page-title">
                          {page.title || '제목 없는 페이지'}
                        </RadioGroup.ItemLabel>
                        <Text as="span" textStyle="t3Regular" color="fg.neutralMuted">
                          <time dateTime={page.lastEditedAt} aria-label={formatAbsoluteDate(page.lastEditedAt)}>{formatNotionRelativeDate(page.lastEditedAt)}</time>
                        </Text>
                      </VStack>
                      <RadioGroup.ItemControl>
                        <RadioGroup.ItemIndicator />
                      </RadioGroup.ItemControl>
                      <RadioGroup.ItemHiddenInput />
                    </RadioGroup.Item>
                  ))}
                  </RadioGroup.Root></RadioGroupField.Root>
              ) : (
                <VStack gap="x2" align="flex-start">
                  <Text as="p" textStyle="t6Bold" color="fg.neutral">
                    {committedQuery ? `‘${committedQuery}’와 일치하는 페이지가 없어요` : '가져올 수 있는 페이지가 없어요'}
                  </Text>
                  <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">
                    노션에서 OpenMD가 접근할 페이지를 추가해 주세요.
                  </Text>
                  {committedQuery ? (
                    <ActionButton type="button" size="small" variant="ghost" onClick={() => setRawQuery('')}>검색어 지우기</ActionButton>
                  ) : (
                    <ActionButton type="button" size="small" variant="ghost" onClick={beginAuthorization}>접근 페이지 추가하기</ActionButton>
                  )}
                </VStack>
              )}

              {visibleCount < pages.length || nextCursor ? (
                <ActionButton type="button" size="medium" variant="neutralWeak" disabled={interactionLocked || moreLoading} loading={moreLoading} onClick={() => void showMore()}>
                  {moreLoading ? '더 불러오는 중' : '더 보기'}
                </ActionButton>
              ) : null}
              {pageError && pages.length ? <InlineStatus message={pageError} action="다시 시도" onAction={() => void showMore()} /> : null}
            </VStack>

            <VStack gap="x2" align="flex-start">
              <Text as="p" textStyle="t4Regular" color="fg.neutralMuted">원하는 페이지가 없나요?</Text>
              <ActionButton type="button" size="medium" variant="ghost" disabled={interactionLocked} onClick={() => setLinkOpen(true)}>
                노션 페이지 링크로 가져오기
              </ActionButton>
            </VStack>

            <VStack className="notion-import-action" gap="x2">
              {importError ? (
                <InlineStatus
                  message={importError}
                  action={toRecoveryAction(importing.error)}
                  onAction={() => {
                    const code = toApiClientError(importing.error).code
                    if (code === 'NOTION_PAGE_NOT_ACCESSIBLE' || code === 'NOTION_REAUTH_REQUIRED') beginAuthorization()
                    else if (selectedPageId) void finishImport(selectedPageId)
                  }}
                />
              ) : null}
              <ActionButton
                className="learning-full-width-action"
                type="button"
                size="large"
                variant="brandSolid"
                disabled={!selectedPageId || interactionLocked}
                loading={importing.isPending}
                onClick={() => void finishImport(selectedPageId)}
              >
                {importing.isPending ? '페이지 가져오는 중' : '이 페이지 가져오기'}
              </ActionButton>
              {!selectedPageId ? <Text textStyle="t3Regular" color="fg.neutralMuted">가져올 페이지를 하나 선택해 주세요.</Text> : null}
            </VStack>
          </VStack>
        )}
      </VStack>

      <ManageSheet
        open={manageOpen}
        disabled={interactionLocked}
        onOpenChange={setManageOpen}
        onAddAccess={beginAuthorization}
        onSwitch={() => { setManageOpen(false); setConfirm('switch-workspace') }}
        onDisconnect={() => { setManageOpen(false); setConfirm('disconnect') }}
      />
      <LinkSheet
        open={linkOpen}
        link={link}
        error={linkError}
        loading={importing.isPending}
        onOpenChange={(open) => { setLinkOpen(open); if (!open) setLinkError(undefined) }}
        onLinkChange={(value) => { setLink(value); setLinkError(undefined) }}
        onSubmit={submitLink}
        onAddAccess={beginAuthorization}
      />
      <DisconnectDialog
        kind={confirm}
        loading={disconnecting.isPending || authorizing.isPending}
        error={disconnecting.isError ? presentationForError(disconnecting.error) : undefined}
        onOpenChange={(open) => { if (!open) setConfirm(null) }}
        onConfirm={() => void confirmDisconnect()}
      />
    </VStack>
  )
}

function DisconnectedState({ reauth, callbackNotice, pending, error, onConnect, onDirect }: {
  reauth: boolean
  callbackNotice: CallbackNotice
  pending: boolean
  error?: string
  onConnect: () => void
  onDirect: () => void
}) {
  const callbackMessage = callbackNotice.outcome === 'cancelled'
    ? '노션 연결을 취소했어요.'
    : callbackNotice.error
      ? getNotionErrorPresentation(callbackNotice.error).message
      : undefined
  return (
    <VStack gap="x5" align="stretch">
      <VStack gap="x2" align="flex-start">
        <Text as="h2" textStyle="t9Bold" color="fg.neutral">{reauth ? '노션을 다시 연결해 주세요' : '노션으로 로그인해 주세요'}</Text>
        <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
          노션 페이지를 가져오려면 워크스페이스 연결이 필요해요. 가져온 내용은 원본 페이지와 자동으로 동기화되지 않아요.
        </Text>
      </VStack>
      {callbackMessage ? <LearningNotice>{callbackMessage}</LearningNotice> : null}
      {error ? <Text role="alert" textStyle="t4Regular" color="fg.critical">{error}</Text> : null}
      <ActionButton className="learning-full-width-action" size="large" variant="brandSolid" loading={pending} disabled={pending} onClick={onConnect}>
        {reauth || callbackNotice.outcome === 'cancelled' ? '다시 연결하기' : '노션 연결하기'}
      </ActionButton>
      <ActionButton className="learning-full-width-action" size="large" variant="neutralWeak" disabled={pending} onClick={onDirect}>직접 입력하기</ActionButton>
    </VStack>
  )
}

function ConnectionSkeleton() {
  return <VStack gap="x4" aria-label="노션 연결 상태를 확인하는 중" role="status"><Skeleton width="full" height="x16" /><Skeleton width="full" height="x32" /></VStack>
}

function InlineStatus({ message, action, onAction }: { message: string; action: string; onAction: () => void }) {
  return (
    <VStack gap="x2" align="flex-start" role="alert">
      <Text as="p" textStyle="t4Regular" color="fg.critical">{message}</Text>
      <ActionButton type="button" size="small" variant="ghost" onClick={onAction}>{action}</ActionButton>
    </VStack>
  )
}

function ManageSheet({ open, disabled, onOpenChange, onAddAccess, onSwitch, onDisconnect }: {
  open: boolean
  disabled: boolean
  onOpenChange: (open: boolean) => void
  onAddAccess: () => void
  onSwitch: () => void
  onDisconnect: () => void
}) {
  return (
    <BottomSheet.Root open={open} onOpenChange={onOpenChange}>
      <Portal><BottomSheet.Backdrop /><BottomSheet.Positioner><BottomSheet.Content>
        <BottomSheet.Header><BottomSheet.Title>노션 연결 관리</BottomSheet.Title><BottomSheet.Description>워크스페이스 연결과 접근 페이지를 관리해요.</BottomSheet.Description></BottomSheet.Header>
        <BottomSheet.Body><VStack gap="x2">
          <ActionButton autoFocus type="button" size="large" variant="neutralWeak" disabled={disabled} onClick={onAddAccess}>접근할 페이지 추가</ActionButton>
          <ActionButton type="button" size="large" variant="neutralWeak" disabled={disabled} onClick={onSwitch}>다른 워크스페이스 연결</ActionButton>
          <ActionButton type="button" size="large" variant="neutralWeak" disabled={disabled} onClick={onDisconnect}>연결 해제</ActionButton>
        </VStack></BottomSheet.Body>
        <BottomSheet.Footer><ActionButton type="button" size="large" variant="neutralSolid" onClick={() => onOpenChange(false)}>닫기</ActionButton></BottomSheet.Footer>
      </BottomSheet.Content></BottomSheet.Positioner></Portal>
    </BottomSheet.Root>
  )
}

function LinkSheet({ open, link, error, loading, onOpenChange, onLinkChange, onSubmit, onAddAccess }: {
  open: boolean
  link: string
  error?: string
  loading: boolean
  onOpenChange: (open: boolean) => void
  onLinkChange: (value: string) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  onAddAccess: () => void
}) {
  return (
    <BottomSheet.Root open={open} onOpenChange={onOpenChange} dismissible={!loading}>
      <Portal><BottomSheet.Backdrop /><BottomSheet.Positioner><BottomSheet.Content>
        <BottomSheet.Header><BottomSheet.Title>노션 페이지 링크로 가져오기</BottomSheet.Title><BottomSheet.Description>노션에서 페이지 링크를 복사해 붙여넣어 주세요.</BottomSheet.Description></BottomSheet.Header>
        <BottomSheet.Body maxHeight="60dvh"><form id="notion-link-form" onSubmit={onSubmit}><VStack gap="x4">
            <Box as="ol" className="notion-link-steps">
              <li>노션에서 가져올 페이지를 열어요.</li><li>오른쪽 위 공유에서 링크 복사를 눌러요.</li><li>복사한 링크를 아래에 붙여넣어요.</li>
            </Box>
            <LearningField label="노션 페이지 링크" error={error}>
              <LearningTextInput autoFocus type="url" value={link} readOnly={loading} placeholder="https://www.notion.so/..." invalid={Boolean(error)} onChange={(event) => onLinkChange(event.currentTarget.value)} />
            </LearningField>
            {error === '이 페이지에 접근할 수 없어요' ? <ActionButton type="button" size="small" variant="ghost" onClick={onAddAccess}>접근 페이지 추가하기</ActionButton> : null}
          </VStack></form></BottomSheet.Body>
        <BottomSheet.Footer>
          <ActionButton type="button" size="large" variant="neutralWeak" disabled={loading} onClick={() => onOpenChange(false)}>취소</ActionButton>
          <ActionButton type="submit" form="notion-link-form" size="large" variant="brandSolid" loading={loading} disabled={loading}>{loading ? '페이지 가져오는 중' : '링크로 가져오기'}</ActionButton>
        </BottomSheet.Footer>
      </BottomSheet.Content></BottomSheet.Positioner></Portal>
    </BottomSheet.Root>
  )
}

function DisconnectDialog({ kind, loading, error, onOpenChange, onConfirm }: {
  kind: ConfirmKind
  loading: boolean
  error?: string
  onOpenChange: (open: boolean) => void
  onConfirm: () => void
}) {
  const switching = kind === 'switch-workspace'
  return (
    <ContentDialog.Root open={Boolean(kind)} onOpenChange={onOpenChange}>
      <Portal><ContentDialog.Backdrop /><ContentDialog.Positioner><ContentDialog.Content className="learning-confirm-dialog" width="full" maxWidth="420px">
        <ContentDialog.Header><ContentDialog.Title>{switching ? '다른 워크스페이스를 연결할까요?' : '노션 연결을 해제할까요?'}</ContentDialog.Title><ContentDialog.Description>{switching ? '현재 워크스페이스 연결을 먼저 해제해야 해요. 이미 저장한 학습자료는 그대로 유지돼요.' : '이미 저장한 학습자료는 그대로 유지돼요. 다시 가져오려면 노션을 연결해야 해요.'}</ContentDialog.Description></ContentDialog.Header>
        {error ? <ContentDialog.Body><Text role="alert" textStyle="t4Regular" color="fg.critical">{error}</Text></ContentDialog.Body> : null}
        <ContentDialog.Footer>
          <ContentDialog.Action asChild><ActionButton autoFocus type="button" size="large" variant="neutralSolid" disabled={loading}>{switching ? '취소' : '연결 유지'}</ActionButton></ContentDialog.Action>
          <ActionButton type="button" size="large" variant="neutralWeak" loading={loading} disabled={loading} onClick={onConfirm}>{switching ? '연결 해제하고 변경' : '연결 해제'}</ActionButton>
        </ContentDialog.Footer>
      </ContentDialog.Content></ContentDialog.Positioner></Portal>
    </ContentDialog.Root>
  )
}

function readCallbackNotice(search: string): CallbackNotice {
  const query = new URLSearchParams(search)
  const outcome = query.get('outcome')
  const error = query.get('error')
  return {
    outcome: outcome === 'connected' || outcome === 'cancelled' || outcome === 'failed' ? outcome : undefined,
    error: isNotionErrorCode(error) ? error : undefined,
  }
}

function presentationForError(error: unknown) {
  const apiError = toApiClientError(error)
  return isNotionErrorCode(apiError.code)
    ? getNotionErrorPresentation(apiError.code).message
    : '요청을 처리하지 못했어요. 다시 시도해 주세요.'
}

function toRecoveryAction(error: unknown) {
  const recovery = getNotionErrorPresentation(toApiClientError(error).code).recovery
  return recovery === 'add-access' || recovery === 'reauth' || recovery === 'connect' ? '접근 페이지 추가하기' : '다시 시도'
}

function formatAbsoluteDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('ko-KR', { dateStyle: 'long', timeStyle: 'short' }).format(date)
}
