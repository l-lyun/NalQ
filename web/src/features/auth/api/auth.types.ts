export type ApiFieldError = {
  field: string
  reason: string
}

export type ApiErrorBody = {
  code: string
  message: string
  fields: ApiFieldError[]
}

export type ApiResponse<T> =
  | { success: true; data: T; error: null }
  | { success: false; data: null; error: ApiErrorBody }

export type SignUpRequest = {
  email: string
  password: string
}

export type VerificationRequired = {
  verificationRequired: boolean
  resendAvailableAt?: string
}

export type EmailVerificationRequest = {
  email: string
  code: string
}

export type LegacyEmailVerified = {
  emailVerified: boolean
  nextAction: 'LOGIN'
}

export type ProfileCompletionRequired = {
  emailVerified: boolean
  signUpToken: string
  nextAction: 'COMPLETE_PROFILE'
}

export type EmailVerified = LegacyEmailVerified | ProfileCompletionRequired

export type NicknameAvailabilityRequest = {
  nickname: string
}

export type NicknameAvailability = {
  available: boolean
  checkedNickname: string
}

export type TermsAgreement = {
  termsId: 'SERVICE_TERMS' | 'PRIVACY_COLLECTION'
  version: string
}

export type CompleteSignUpRequest = {
  signUpToken: string
  password: string
  nickname: string
  agreements: TermsAgreement[]
}

export type LoginRequest = {
  email: string
  password: string
}

export type SessionTokens = {
  accessToken: string
  accessExpiresAt: string
  refreshExpiresAt: string
}

export type CurrentUser = {
  id: number
  email: string
  emailVerified: boolean
  status: 'PENDING_ACTIVATION' | 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN'
}
