import { IconArrowLeftLine } from '@karrotmarket/react-monochrome-icon'
import { ActionButton, Box, Divider, Flex, Icon, Text, VStack } from '@seed-design/react'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import seedLicense from './licenses/seed-license.txt?raw'
import seedNotice from './licenses/seed-notice.txt?raw'
import { privacyPolicyDraft, serviceTermsDraft, type LegalDocument } from './legalContent'
import { PublicServiceFooter } from './PublicServiceFooter'
import './public-service.css'

const SUPPORT_EMAIL = 'kimdohyun032@gmail.com'

function PublicPage({ title, children }: { title: string; children: React.ReactNode }) {
  const navigate = useNavigate()
  const location = useLocation()
  return (
    <VStack className="public-service-shell" minHeight="100dvh" bg="bg.layerBasement">
      <Box as="main" className="public-service-main" bg="bg.layerDefault" width="full" pt="safeArea">
        <VStack className="public-service-content" px="spacingX.globalGutter" pt="x4" pb="x10" gap="x6">
          <Flex as="header" align="center" gap="x2">
            <ActionButton
              type="button"
              size="small"
              variant="ghost"
              layout="iconOnly"
              aria-label="이전 화면으로 돌아가기"
              onClick={() => {
                if (location.key !== 'default') navigate(-1)
                else navigate('/login', { replace: true })
              }}
            >
              <Icon svg={<IconArrowLeftLine />} size="x5" />
            </ActionButton>
            <Link className="public-service-brand-link" to="/login">NalQ</Link>
          </Flex>
          <Text as="h1" textStyle="t12Bold" color="fg.neutral">{title}</Text>
          {children}
        </VStack>
      </Box>
      <PublicServiceFooter />
    </VStack>
  )
}

function LegalDocumentPage({ document }: { document: LegalDocument }) {
  return (
    <PublicPage title={document.title}>
      <Box role="status" bg="bg.warningWeak" borderRadius="r3" p="x4">
        <VStack gap="x2">
          <Text textStyle="t5Bold" color="fg.warning">출시 전 법률 검토가 필요한 문서예요</Text>
          <Text textStyle="t4Regular" color="fg.neutralMuted">
            확정 시행일과 운영 버전이 아직 승인되지 않았습니다. 현재 화면은 구현 검토용이며 운영 중인 약관·방침으로 표시하지 않습니다.
          </Text>
        </VStack>
      </Box>
      <Text textStyle="t4Medium" color="fg.neutralMuted">검토 문서 ID {document.reviewId}</Text>
      <VStack as="article" gap="x8">
        {document.sections.map((section) => (
          <VStack as="section" key={section.heading} gap="x3">
            <Text as="h2" textStyle="t7Bold" color="fg.neutral">{section.heading}</Text>
            {section.paragraphs.map((paragraph) => (
              <Text as="p" key={paragraph} textStyle="t5Regular" color="fg.neutral">{paragraph}</Text>
            ))}
          </VStack>
        ))}
      </VStack>
      <Divider as="div" color="stroke.neutralSubtle" />
      <Text textStyle="t4Regular" color="fg.neutralMuted">
        문서 내용에 관한 문의는 <Link className="public-service-link" to="/support">문의하기</Link>에서 접수해 주세요.
      </Text>
    </PublicPage>
  )
}

export function TermsPage() {
  return <LegalDocumentPage document={serviceTermsDraft} />
}

export function PrivacyPage() {
  return <LegalDocumentPage document={privacyPolicyDraft} />
}

export function SupportPage() {
  const [copyStatus, setCopyStatus] = useState<'idle' | 'success' | 'error'>('idle')

  async function copyEmail() {
    try {
      await navigator.clipboard.writeText(SUPPORT_EMAIL)
      setCopyStatus('success')
    } catch {
      setCopyStatus('error')
    }
  }

  return (
    <PublicPage title="문의하기">
      <VStack gap="x3">
        <Text as="h2" textStyle="t7Bold" color="fg.neutral">NalQ 문의</Text>
        <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">운영자 김도현에게 이메일로 문의할 수 있어요.</Text>
        <a className="public-service-email-value" href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</a>
      </VStack>
      <Flex gap="x3" wrap>
        <ActionButton asChild size="large" variant="brandSolid">
          <a href={`mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent('[NalQ 문의]')}`}>이메일로 문의하기</a>
        </ActionButton>
        <ActionButton type="button" size="large" variant="neutralWeak" onClick={() => void copyEmail()}>
          이메일 주소 복사
        </ActionButton>
      </Flex>
      {copyStatus === 'success' ? (
        <Text role="status" aria-live="polite" textStyle="t4Regular" color="fg.positive">이메일 주소를 복사했어요.</Text>
      ) : copyStatus === 'error' ? (
        <Text role="alert" textStyle="t4Regular" color="fg.critical">주소를 복사하지 못했어요. 위 이메일 주소를 길게 눌러 직접 복사해 주세요.</Text>
      ) : null}
      <Divider as="div" color="stroke.neutralSubtle" />
      <VStack gap="x3">
        <Text as="h2" textStyle="t7Bold" color="fg.neutral">저작권 침해 신고</Text>
        <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
          신고 대상 자료나 문제를 찾을 수 있는 정보와 권리자임을 확인할 수 있는 근거를 적어 주세요. 비밀번호나 인증 코드는 보내지 마세요.
        </Text>
        <Text as="p" textStyle="t5Regular" color="fg.neutralMuted">
          접수된 콘텐츠는 검토 중 임시 제한될 수 있고, 게시자의 소명과 확인 결과에 따라 삭제하거나 복원합니다. 반복적이고 악의적인 위반에는 단계적으로 이용 제한 조치를 할 수 있어요.
        </Text>
        <ActionButton asChild size="large" variant="neutralWeak">
          <a href={`mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent('[NalQ 저작권 침해 신고]')}`}>저작권 침해 신고 작성</a>
        </ActionButton>
      </VStack>
    </PublicPage>
  )
}

export function OpenSourceLicensesPage() {
  return (
    <PublicPage title="오픈소스 라이선스">
      <VStack as="article" gap="x5">
        <VStack gap="x2">
          <Text as="h2" textStyle="t8Bold" color="fg.neutral">SEED Design</Text>
          <Text textStyle="t4Regular" color="fg.neutralMuted">사용 버전: @seed-design/react 2.3.0 · @seed-design/css 2.5.0</Text>
          <Text textStyle="t5Regular" color="fg.neutral">Copyright 2025 주식회사 당근마켓</Text>
          <Text textStyle="t5Regular" color="fg.neutral">Licensed under the Apache License, Version 2.0</Text>
          <a className="public-service-link" href="https://www.apache.org/licenses/LICENSE-2.0" target="_blank" rel="noreferrer">Apache License 2.0 공식 원문</a>
        </VStack>
        <details className="public-service-disclosure">
          <summary>Apache License 2.0 전문 보기</summary>
          <pre>{seedLicense}</pre>
        </details>
        <details className="public-service-disclosure">
          <summary>SEED NOTICE 전문 보기</summary>
          <pre>{seedNotice}</pre>
        </details>
      </VStack>
    </PublicPage>
  )
}
