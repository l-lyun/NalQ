import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router-dom'

import { homeReadyFixture } from '@/pages/home/home.fixtures'
import { HomePage } from '@/pages/home/HomePage'

const queryClient = new QueryClient()

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="*" element={<HomePage {...homeReadyFixture} />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
