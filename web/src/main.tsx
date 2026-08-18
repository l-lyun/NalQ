import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@seed-design/css/base.css'

import { App } from '@/app/App'

const root = document.getElementById('root')

if (!root) {
  throw new Error('Root element was not found')
}

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
