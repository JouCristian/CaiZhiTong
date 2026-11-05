#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ "${CI:-}" != "true" || ! "${DB_URL:-}" =~ /caizhitong_v070_demo_e2e_test$ ]]; then
  echo 'OAuth browser CI requires the isolated caizhitong_v070_demo_e2e_test database.' >&2
  exit 1
fi
mkdir -p .runtime
export SPRING_PROFILES_ACTIVE=dev,test SERVER_PORT=8081 PUBLIC_WEB_URL=http://127.0.0.1:5180
export CORS_ALLOWED_ORIGINS=http://127.0.0.1:5180 DEMO_ENABLED=true
export DEMO_ADMIN_PASSWORD=ci-only-administrator-password DEMO_LAB_PASSWORD=ci-only-laboratory-password DEMO_STUDENT_PASSWORD=ci-only-student-password
export GITHUB_OAUTH_ENABLED=true GITHUB_CLIENT_ID=mock-client GITHUB_CLIENT_SECRET=mock-test-only-secret
export GITHUB_REDIRECT_URI=http://127.0.0.1:8081/login/oauth2/code/github
export APP_OAUTH_GITHUB_AUTHORIZATION_URI=http://127.0.0.1:9098/authorize
export APP_OAUTH_GITHUB_TOKEN_URI=http://127.0.0.1:9098/token APP_OAUTH_GITHUB_USER_URI=http://127.0.0.1:9098/user
export JWT_SECRET="$(openssl rand -base64 48)"
export STORAGE_MODE=local ALERT_SCAN_ENABLED=false MAIL_DISPATCH_ENABLED=false
export E2E_OAUTH=1 E2E_ISOLATED=1 E2E_BASE_URL=http://127.0.0.1:5180 E2E_BROWSER_CHANNEL=chromium
export API_PROXY_TARGET=http://127.0.0.1:8081
node scripts/mock-github.mjs >.runtime/mock-github-ci.log 2>&1 & provider=$!
java -jar backend/target/backend-0.7.0.jar >.runtime/oauth-backend-ci.log 2>&1 & backend=$!
node scripts/e2e-frontend.mjs >.runtime/oauth-frontend-ci.log 2>&1 & frontend=$!
trap 'kill "$provider" "$backend" "$frontend" 2>/dev/null || true' EXIT
for target in http://127.0.0.1:9098/health http://127.0.0.1:8081/actuator/health http://127.0.0.1:5180/login; do
  ready=false
  for attempt in $(seq 1 90); do
    if curl --fail --silent "$target" >/dev/null; then ready=true; break; fi
    sleep 1
  done
  if [[ "$ready" != true ]]; then echo 'Isolated OAuth browser server did not become ready.' >&2; exit 1; fi
done
npm --prefix frontend test
E2E_OAUTH=0 npm --prefix frontend test -- e2e/ui-motion.spec.ts
