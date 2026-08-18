import { seedDesignPlugin } from '@seed-design/vite-plugin'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), seedDesignPlugin()],
  resolve: {
    tsconfigPaths: true,
  },
})
