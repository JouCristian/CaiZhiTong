import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    server: {
      port: 5173, strictPort: true,
      proxy: Object.fromEntries(['/api', '/oauth2', '/login/oauth2'].map(path => [path, { target: env.API_PROXY_TARGET || 'http://127.0.0.1:8080', changeOrigin: true }])),
    },
    build: {
      rollupOptions: {
        output: { manualChunks: { 'react-vendor': ['react', 'react-dom', 'react-router-dom'], 'antd-vendor': ['antd', '@ant-design/icons'] } },
      },
    },
  }
})

