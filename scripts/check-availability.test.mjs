import { test } from 'node:test'
import assert from 'node:assert/strict'
import http from 'node:http'
import { probe, validateOrigin } from './check-availability.mjs'

async function withServer(handler, action) {
  const server = http.createServer(handler)
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  try { await action(`http://127.0.0.1:${server.address().port}`) }
  finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)) }
}
test('origins reject credentials, insecure remote URLs and extra URL components', () => {
  for (const value of ['http://example.com', 'https://user:secret@example.com', 'https://example.com/path', 'https://example.com?secret=x', 'https://example.com#x']) assert.throws(() => validateOrigin(value))
  assert.equal(validateOrigin('https://example.com'), 'https://example.com')
})
test('healthy endpoints return bounded timing and public boolean, without body contents', async () => {
  await withServer((req, res) => res.end(JSON.stringify(req.url.includes('oauth') ? { code: 0, data: { githubEnabled: true }, private: 'never-log-this' } : { status: 'UP' })), async origin => {
    for (const path of ['/actuator/health', '/api/auth/oauth/config']) {
      const result = await probe(origin, path, 1000)
      assert.equal(result.outcome, 'ok'); assert.equal(result.status, 200)
      assert.ok(result.firstByteMs >= 0 && result.totalMs >= result.firstByteMs)
      assert.ok(!JSON.stringify(result).includes('never-log-this'))
    }
  })
})
test('a response that never completes has a total deadline', async () => {
  await withServer((_req, res) => { res.writeHead(200); res.write('{') }, async origin => {
    const result = await probe(origin, '/actuator/health', 60)
    assert.equal(result.outcome, 'timeout'); assert.ok(result.totalMs < 1000)
  })
})
test('first-byte timing is captured before delayed response headers finish', async () => {
  await withServer((_req, res) => {
    const socket = res.socket
    const body = '{"status":"UP"}'
    socket.write('HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n')
    setTimeout(() => socket.end(`Content-Length: ${Buffer.byteLength(body)}\r\nConnection: close\r\n\r\n${body}`), 120)
  }, async origin => {
    const result = await probe(origin, '/actuator/health', 2000)
    assert.equal(result.outcome, 'ok')
    assert.ok(result.totalMs - result.firstByteMs >= 80, 'TTFB must not wait for all response headers')
  })
})
test('HTTP failure, invalid JSON and false OAuth availability are distinct', async () => {
  for (const [status, body, expected] of [[503, '{}', 'http_error'], [200, '{', 'invalid_response'], [200, '{"code":0,"data":{"githubEnabled":false}}', 'ok']]) {
    await withServer((_req, res) => { res.writeHead(status); res.end(body) }, async origin => {
      const result = await probe(origin, '/api/auth/oauth/config', 1000)
      assert.equal(result.outcome, expected)
      if (expected === 'ok') assert.equal(result.githubEnabled, false)
    })
  }
})
test('connection failures and oversized responses terminate without printing payload', async () => {
  await withServer((_req, res) => res.end('x'.repeat(70000)), async origin => assert.equal((await probe(origin, '/actuator/health', 1000)).outcome, 'response_too_large'))
  const server = http.createServer()
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const origin = `http://127.0.0.1:${server.address().port}`
  await new Promise(resolve => server.close(resolve))
  assert.equal((await probe(origin, '/actuator/health', 1000)).outcome, 'network_error')
})
