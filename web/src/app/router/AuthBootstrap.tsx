import { useEffect, type PropsWithChildren } from 'react'

import { bootstrapAuthSession } from '@/features/auth/model/authSession'
import { listenForSessionEnded } from '@/features/auth/model/authSessionChannel'
import { endLocalSession } from '@/features/auth/model/sessionCleanup'
import { startNativeBridge } from '@/shared/native/nativeBridge'
import { createPushSession } from '@/shared/native/pushSession'

export function AuthBootstrap({ children }: PropsWithChildren) {
  useEffect(() => startNativeBridge(window, createPushSession), [])

  useEffect(() => {
    void bootstrapAuthSession()

    return listenForSessionEnded(() => {
      void endLocalSession()
    })
  }, [])

  return children
}
