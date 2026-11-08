import http from 'node:http'
import https from 'node:https'
import { performance } from 'node:perf_hooks'
import { appendFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

export function validateOrigin(value) {
  const url = new URL(value)
  const local = ['127.0.0.1', '[::1]'].includes(url.hostname)
  if (!(url.protocol === 'https:' || (local && url.protocol === 'http:')) || url.username || url.password || url.pathname !== '/' || url.search || url.hash)
    throw new Error('Use an HTTPS origin without credentials, path, query or fragment (HTTP loopback is allowed for tests).')
  return url.origin
}

// Public GETs only. Do not attach credentials or print response bodies/headers.
export function probe(origin, path, timeoutMs) {
  origin = validateOrigin(origin)
  if (!['/actuator/health', '/api/auth/oauth/config'].includes(path)) throw new Error('Unsupported public probe path.')
  if (!Number.isFinite(timeoutMs) || timeoutMs < 1 || timeoutMs > 240000) throw new Error('Timeout must be within 1..240000 ms.')
  return new Promise(resolve => {
    const startedAt = new Date().toISOString(), start = performance.now()
    let tlsMs = null, firstByteMs = null, status = null, finished = false, bytes = 0
    const chunks = [], transport = origin.startsWith('https:') ? https : http
    const finish = (outcome, extra = {}) => {
      if (finished) return
      finished = true
      clearTimeout(deadline)
      resolve({ startedAt, finishedAt: new Date().toISOString(), origin, path, status, tlsMs, firstByteMs, totalMs: Math.round(performance.now() - start), outcome, ...extra })
    }
    const req = transport.get(origin + path, { agent: false, headers: { Accept: 'application/json', 'User-Agent': 'CaiZhiTong-Availability/1.0' } }, res => {
      status = res.statusCode
      firstByteMs = Math.round(performance.now() - start)
      res.on('data', chunk => {
        bytes += chunk.length
        if (bytes > 65536) { finish('response_too_large'); req.destroy(); return }
        chunks.push(chunk)
      })
      res.on('error', () => finish('network_error'))
      res.on('end', () => {
        if (status !== 200) { finish('http_error'); return }
        try {
          const data = JSON.parse(Buffer.concat(chunks).toString('utf8'))
          const valid = path === '/actuator/health' ? data.status === 'UP' : data.code === 0 && typeof data.data?.githubEnabled === 'boolean'
          finish(valid ? 'ok' : 'invalid_response', valid && path.includes('/oauth/') ? { githubEnabled: data.data.githubEnabled } : {})
        } catch { finish('invalid_response') }
      })
    })
    const deadline = setTimeout(() => { finish('timeout'); req.destroy() }, timeoutMs)
    req.on('socket', socket => socket.once('secureConnect', () => { tlsMs = Math.round(performance.now() - start) }))
    req.on('error', () => finish('network_error'))
  })
}

async function main() {
  const origin = validateOrigin(process.env.AVAILABILITY_BASE_URL || 'https://caizhitong-api.onrender.com')
  const seconds = Number(process.env.PROBE_TIMEOUT_SECONDS || '180')
  if (!Number.isInteger(seconds) || seconds < 1 || seconds > 240) throw new Error('PROBE_TIMEOUT_SECONDS must be 1..240.')
  let failed = false
  for (const path of ['/actuator/health', '/api/auth/oauth/config']) {
    const result = await probe(origin, path, seconds * 1000)
    const line = JSON.stringify(result)
    console.log(line)
    if (process.env.PROBE_OUTPUT) appendFileSync(process.env.PROBE_OUTPUT, line + '\n')
    failed ||= result.outcome !== 'ok'
  }
  if (failed) process.exitCode = 1
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main().catch(() => { console.error('Availability probe configuration failed; no private values printed.'); process.exitCode = 1 })
