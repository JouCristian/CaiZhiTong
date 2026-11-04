// Test-only loopback OAuth provider, with synthetic profiles and single-use PKCE codes.
import http from 'node:http'
import { randomUUID, createHash } from 'node:crypto'
const codes = new Map(), tokens = new Map()
const initial = { id: '990000001', login: 'synthetic-visitor', name: 'GitHub 体验访客', email: 'synthetic@example.test', avatar_url: 'https://avatars.githubusercontent.com/u/990000001?v=4', mode: 'success' }
let profile = { ...initial }
const send = (res, status, body) => { res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(body)) }
http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1:9098')
  if (url.pathname === '/health') return send(res, 200, { status: 'UP' })
  if (url.pathname === '/control' && req.method === 'POST') {
    let body = ''; for await (const chunk of req) body += chunk
    try { profile = { ...initial, ...JSON.parse(body) }; return send(res, 200, { ok: true }) } catch { return send(res, 400, { error: 'invalid' }) }
  }
  if (url.pathname === '/authorize') {
    const q = url.searchParams, redirect = q.get('redirect_uri')
    if (q.get('client_id') !== 'mock-client' || redirect !== 'http://127.0.0.1:8081/login/oauth2/code/github' || q.get('scope') !== 'read:user' || q.get('code_challenge_method') !== 'S256' || !q.get('state')) return send(res, 400, { error: 'invalid_request' })
    const code = randomUUID(); codes.set(code, { profile: { ...profile }, challenge: q.get('code_challenge'), redirect })
    const target = new URL(redirect); target.searchParams.set('state', q.get('state'))
    if (profile.mode === 'cancel') target.searchParams.set('error', 'access_denied')
    else target.searchParams.set('code', code)
    res.writeHead(302, { Location: target.href, 'Cache-Control': 'no-store' }); return res.end()
  }
  if (url.pathname === '/token' && req.method === 'POST') {
    let raw = ''; for await (const chunk of req) raw += chunk
    const q = new URLSearchParams(raw), grant = codes.get(q.get('code')); codes.delete(q.get('code'))
    const proof = createHash('sha256').update(q.get('code_verifier') || '').digest('base64url')
    if (!grant || q.get('client_id') !== 'mock-client' || q.get('client_secret') !== 'mock-test-only-secret' || proof !== grant.challenge || q.get('redirect_uri') !== grant.redirect) return send(res, 400, { error: 'invalid_grant' })
    if (grant.profile.mode === 'provider-error') return send(res, 503, { error: 'temporarily_unavailable' })
    const token = randomUUID(); tokens.set(token, grant.profile)
    return send(res, 200, { access_token: token, token_type: 'Bearer', scope: 'read:user' })
  }
  if (url.pathname === '/user') {
    const p = tokens.get(req.headers.authorization?.replace(/^Bearer /, ''))
    return p ? send(res, 200, p) : send(res, 401, { error: 'unauthorized' })
  }
  send(res, 404, { error: 'not_found' })
}).listen(9098, '127.0.0.1', () => console.log('Synthetic GitHub provider listening on loopback port 9098'))
