// Keep the isolated server independent of npm's interactive Windows console.
// The harness owns this process tree and terminates it after the suite.
import { preview } from '../frontend/node_modules/vite/dist/node/index.js'
import { fileURLToPath } from 'node:url'
const root = fileURLToPath(new URL('../frontend', import.meta.url))
const server = await preview({ root, preview: { host: '127.0.0.1', port: 5180, strictPort: true, proxy: { '/api': { target: process.env.API_PROXY_TARGET || 'http://127.0.0.1:8081', changeOrigin: true } } } })
console.log(`Isolated production preview ready, PID ${process.pid}`)
server.printUrls()
process.on('exit', code => console.log(`Isolated Vite server exit ${code}`))
