import { IconArrowLeftLine } from '@karrotmarket/react-monochrome-icon'
import { ActionButton, Box, Divider, Flex, Icon, Text, VStack } from '@seed-design/react'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import seedLicense from './licenses/seed-license.txt?raw'
import seedNotice from './licenses/seed-notice.txt?raw'
import thirdPartyNoticesUrl from './licenses/third-party-notices.txt?url'
import { privacyPolicy, serviceTerms, type LegalDocument } from './legalContent'
import { PublicServiceFooter } from './PublicServiceFooter'
import { getPublicBackLabel, readPublicReturnPath } from './publicServiceNavigation'
import './public-service.css'

const SUPPORT_EMAIL = 'nalq.service@gmail.com'

function PublicPage({ title, children }: { title: string; children: React.ReactNode }) {
  const navigate = useNavigate()
  const location = useLocation()
  const returnTo = readPublicReturnPath(location.state)
  return (
    <VStack className="public-service-shell" minHeight="100dvh" bg="bg.layerDefault">
      <Box as="main" className="public-service-main" bg="bg.layerDefault" width="full" pt="safeArea">
        <VStack className="public-service-content" px="spacingX.globalGutter" pt="x4" pb="x10" gap="x6">
          <Flex as="header" align="center" gap="x2">
            <ActionButton
              className="public-service-back-button"
              type="button"
              size="small"
              variant="ghost"
              layout="iconOnly"
              aria-label={getPublicBackLabel(returnTo)}
              onClick={() => {
                navigate(returnTo, { replace: true })
              }}
            >
              <Icon svg={<IconArrowLeftLine />} size="x5" />
            </ActionButton>
            <Text as="h1" textStyle="t10Bold" color="fg.neutral">{title}</Text>
          </Flex>
          {children}
        </VStack>
      </Box>
      <PublicServiceFooter />
    </VStack>
  )
}

function LegalDocumentPage({ document }: { document: LegalDocument }) {
  const location = useLocation()
  return (
    <PublicPage title={document.title}>
      <Text as="p" textStyle="t6Regular" color="fg.neutralMuted">{document.summary}</Text>
      <VStack className="public-service-document-meta" bg="bg.neutralWeak" borderRadius="r2" py="x3" px="x4" gap="x1">
        <Text textStyle="t4Medium" color="fg.neutral">시행일 {document.effectiveAt}</Text>
        <Text textStyle="t3Regular" color="fg.neutralMuted">버전 {document.version}</Text>
      </VStack>
      {document.sections.length >= 6 ? (
        <Box as="nav" className="public-service-toc" aria-label={`${document.title} 목차`}>
          <Text textStyle="t5Bold" color="fg.neutral">목차</Text>
          <ol>
            {document.sections.map((section, index) => (
              <li key={section.heading}><a href={`#legal-section-${index + 1}`}>{section.heading}</a></li>
            ))}
          </ol>
        </Box>
      ) : null}
      <VStack as="article" className="public-service-article" gap="x8">
        {document.sections.map((section, index) => (
          <VStack as="section" id={`legal-section-${index + 1}`} key={section.heading} gap="x3">
            <Text as="h2" textStyle="t7Bold" color="fg.neutral">{section.heading}</Text>
            {section.paragraphs.map((paragraph) => (
              <Text as="p" key={paragraph} textStyle="t5Regular" color="fg.neutral">{paragraph}</Text>
            ))}
          </VStack>
        ))}
      </VStack>
      <Divider as="div" color="stroke.neutralSubtle" />
      <Text textStyle="t4Regular" color="fg.neutralMuted">
        문서 내용에 관한 문의는 <Link className="public-service-link" to="/support" state={{ returnTo: location.pathname }}>문의하기</Link>에서 접수해 주세요.
      </Text>
    </PublicPage>
  )
}

export function TermsPage() {
  return <LegalDocumentPage document={serviceTerms} />
}

export function PrivacyPage() {
  return <LegalDocumentPage document={privacyPolicy} />
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
      <Flex className="public-service-support-actions" gap="x3" wrap>
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
      <Text as="p" textStyle="t6Regular" color="fg.neutralMuted">
        NalQ를 만드는 데 사용한 오픈소스 소프트웨어와 각 라이선스 고지를 확인할 수 있어요.
      </Text>
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
        <VStack gap="x2" align="flex-start">
          <Text textStyle="t5Bold" color="fg.neutral">전체 제3자 소프트웨어 고지</Text>
          <Text textStyle="t4Regular" color="fg.neutralMuted">
            운영 웹 번들에 포함된 패키지 이름·버전·출처와 라이선스 전문을 한 파일에서 확인할 수 있어요.
          </Text>
          <a className="public-service-link" href={thirdPartyNoticesUrl} target="_blank" rel="noreferrer">
            전체 제3자 라이선스 및 고지 열기
          </a>
        </VStack>
      </VStack>
    </PublicPage>
  )
}
