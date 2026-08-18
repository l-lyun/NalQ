import { Box } from '@seed-design/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router-dom'

const queryClient = new QueryClient()

function SeedRuntimeProbe() {
  return <Box aria-hidden="true" />
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="*" element={<SeedRuntimeProbe />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
