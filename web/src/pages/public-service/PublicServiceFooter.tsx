import { Box, Text, VStack } from '@seed-design/react'
import { Link, useLocation } from 'react-router-dom'

import './public-service.css'

const footerLinks = [
  { to: '/terms', label: '이용약관' },
  { to: '/privacy', label: '개인정보처리방침' },
  { to: '/support', label: '문의하기' },
  { to: '/open-source-licenses', label: '오픈소스 라이선스' },
] as const

export function PublicServiceFooter({ preserveContext = false }: { preserveContext?: boolean }) {
  const { pathname } = useLocation()
  const visibleLinks = import.meta.env.DEV
    ? footerLinks
    : footerLinks.filter((item) => item.to !== '/terms' && item.to !== '/privacy')

  return (
    <Box as="footer" className="public-service-footer" px="spacingX.globalGutter" py="x6">
      <VStack className="public-service-footer__content" gap="x2" align="flex-start">
        <Text textStyle="t5Bold" color="fg.neutral">NalQ</Text>
        <nav className="public-service-footer__links" aria-label="서비스 정보">
          {visibleLinks.map((item) => pathname === item.to ? (
            <span key={item.to} className="public-service-link" aria-current="page">
              {item.label}
            </span>
          ) : (
            <Link
              key={item.to}
              className="public-service-link"
              to={item.to}
              state={{ returnTo: pathname }}
              target={preserveContext ? '_blank' : undefined}
              rel={preserveContext ? 'noreferrer' : undefined}
            >
              {item.label}
            </Link>
          ))}
        </nav>
        <Text textStyle="t3Regular" color="fg.neutralMuted">운영자 김도현</Text>
        <a className="public-service-link public-service-email" href="mailto:kimdohyun032@gmail.com">
          kimdohyun032@gmail.com
        </a>
        <Text textStyle="t3Regular" color="fg.neutralSubtle">© 2026 NalQ</Text>
      </VStack>
    </Box>
  )
}
