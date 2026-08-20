import { useEffect, type PropsWithChildren } from 'react'

import { bootstrapAuthSession } from '@/features/auth/model/authSession'

export function AuthBootstrap({ children }: PropsWithChildren) {
  useEffect(() => {
    void bootstrapAuthSession()
  }, [])

  return children
}
