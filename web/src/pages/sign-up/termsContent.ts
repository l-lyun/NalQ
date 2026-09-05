import {
  LEGAL_EFFECTIVE_AT,
  LEGAL_VERSION,
  privacyPolicy,
  serviceTerms,
  type LegalDocument,
} from '../public-service/legalContent'

export type SignUpTermId = 'service' | 'privacy'

export type SignUpTerm = {
  id: SignUpTermId
  title: string
  shortLabel: string
  version: string
  effectiveAt: string
  paragraphs: readonly string[]
}

function documentParagraphs(document: LegalDocument) {
  return document.sections.map(
    (section) => `${section.heading} ${section.paragraphs.join(' ')}`,
  )
}

export const signUpTerms: Record<SignUpTermId, SignUpTerm> = {
  service: {
    id: 'service',
    title: serviceTerms.title,
    shortLabel: '만 14세 이상이며 서비스 이용약관에 동의해요.',
    version: LEGAL_VERSION,
    effectiveAt: LEGAL_EFFECTIVE_AT,
    paragraphs: documentParagraphs(serviceTerms),
  },
  privacy: {
    id: 'privacy',
    title: '개인정보 수집·이용 동의',
    shortLabel: '개인정보 수집·이용에 동의해요.',
    version: LEGAL_VERSION,
    effectiveAt: LEGAL_EFFECTIVE_AT,
    paragraphs: documentParagraphs(privacyPolicy),
  },
}
