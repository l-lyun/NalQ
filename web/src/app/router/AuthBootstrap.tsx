import { useEffect, type PropsWithChildren } from 'react'

import { bootstrapAuthSession } from '@/features/auth/model/authSession'
import { listenForSessionEnded } from '@/features/auth/model/authSessionChannel'
import { endLocalSession } from '@/features/auth/model/sessionCleanup'
import { startNativeBridge } from '@/shared/native/nativeBridge'

export function AuthBootstrap({ children }: PropsWithChildren) {
  useEffect(() => startNativeBridge(window), [])

  useEffect(() => {
    void bootstrapAuthSession()

    return listenForSessionEnded(() => {
      void endLocalSession()
    })
  }, [])

  return children
}
