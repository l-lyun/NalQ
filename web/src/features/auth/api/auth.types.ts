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
}

export type EmailVerificationRequest = {
  email: string
  code: string
}

export type EmailVerified = {
  emailVerified: boolean
  nextAction: 'LOGIN'
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
