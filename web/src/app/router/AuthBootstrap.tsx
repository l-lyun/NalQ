import { useEffect, type PropsWithChildren } from 'react'

import { bootstrapAuthSession } from '@/features/auth/model/authSession'
import { listenForSessionEnded } from '@/features/auth/model/authSessionChannel'
import { endLocalSession } from '@/features/auth/model/sessionCleanup'

export function AuthBootstrap({ children }: PropsWithChildren) {
  useEffect(() => {
    void bootstrapAuthSession()

    return listenForSessionEnded(() => {
      void endLocalSession()
    })
  }, [])

  return children
}
