import { useEffect, type PropsWithChildren } from 'react'

import { bootstrapAuthSession } from '@/features/auth/model/authSession'
import { listenForSessionEnded } from '@/features/auth/model/authSessionChannel'
import { endLocalSession } from '@/features/auth/model/sessionCleanup'
import { startNativeBridge } from '@/shared/native/nativeBridge'
import { createPushOpenSession } from '@/shared/native/pushOpenSession'
import { createPushSession } from '@/shared/native/pushSession'

export function AuthBootstrap({ children, navigate }: PropsWithChildren<{ navigate: (path: string) => Promise<void> }>) {
  useEffect(() => startNativeBridge(window, (connection) => createPushSession(connection, createPushOpenSession(connection, navigate))), [navigate])

  useEffect(() => {
    void bootstrapAuthSession()

    return listenForSessionEnded(() => {
      void endLocalSession()
    })
  }, [])

  return children
}
