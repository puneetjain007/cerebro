#!/usr/bin/env bash
#
# E2E Test: Bearer Token Auth (OAuth2 Proxy Scenario)
#
# Verifies JWT validation, claim extraction, role mapping, and RBAC enforcement
# when Cerebro receives requests with Authorization: Bearer <JWT> headers.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CEREBRO_PORT=9000
CEREBRO_URL="http://localhost:${CEREBRO_PORT}"
MOCK_IDP_URL="http://localhost:8888"
ES_URL="http://localhost:9200"
CEREBRO_PID=""
TESTS_PASSED=0
TESTS_FAILED=0

cleanup() {
  echo ""
  echo "=== Cleanup ==="
  if [[ -n "$CEREBRO_PID" ]] && kill -0 "$CEREBRO_PID" 2>/dev/null; then
    echo "Stopping Cerebro (PID $CEREBRO_PID)..."
    kill "$CEREBRO_PID" 2>/dev/null || true
    wait "$CEREBRO_PID" 2>/dev/null || true
  fi
  echo "Stopping Docker services..."
  cd "$SCRIPT_DIR" && docker compose down 2>/dev/null || true
  echo "Cleanup complete."
}
trap cleanup EXIT

pass() {
  echo "  PASS: $1"
  TESTS_PASSED=$((TESTS_PASSED + 1))
}

fail() {
  echo "  FAIL: $1"
  echo "        $2"
  TESTS_FAILED=$((TESTS_FAILED + 1))
}

wait_for_url() {
  local url="$1"
  local name="$2"
  local max_attempts="${3:-60}"
  local attempt=0
  echo "Waiting for $name ($url)..."
  while ! curl -sf "$url" >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [[ $attempt -ge $max_attempts ]]; then
      echo "ERROR: $name did not become ready after $max_attempts attempts"
      exit 1
    fi
    sleep 2
  done
  echo "$name is ready."
}

get_token() {
  local client_id="$1"
  local client_secret="${2:-secret}"
  local response
  response=$(curl -sf -X POST "${MOCK_IDP_URL}/default/token" \
    -d "grant_type=client_credentials" \
    -d "client_id=${client_id}" \
    -d "client_secret=${client_secret}" \
    -d "scope=openid profile email groups")
  echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"
}

# ============================================================
# Step 1: Start infrastructure
# ============================================================
echo "=== Starting E2E Infrastructure ==="
cd "$SCRIPT_DIR"
docker compose up -d
wait_for_url "${ES_URL}/_cluster/health" "Elasticsearch" 60
wait_for_url "${MOCK_IDP_URL}/default/.well-known/openid-configuration" "Mock IdP" 30

# ============================================================
# Step 2: Start Cerebro with access_token OAuth config
# ============================================================
echo ""
echo "=== Starting Cerebro ==="
cd "$PROJECT_DIR"
sbt "run -Dconfig.file=e2e/application-oauth-accesstoken.conf" &
CEREBRO_PID=$!
echo "Cerebro started with PID $CEREBRO_PID"

# Wait for Cerebro to be ready (it serves the frontend on GET /)
attempt=0
max_attempts=120
echo "Waiting for Cerebro (${CEREBRO_URL})..."
while true; do
  http_code=$(curl -s -o /dev/null -w "%{http_code}" "${CEREBRO_URL}/" 2>/dev/null || echo "000")
  if [[ "$http_code" =~ ^(200|303|302|301)$ ]]; then
    break
  fi
  attempt=$((attempt + 1))
  if [[ $attempt -ge $max_attempts ]]; then
    echo "ERROR: Cerebro did not start after $max_attempts attempts (last HTTP code: $http_code)"
    exit 1
  fi
  sleep 2
done
echo "Cerebro is ready."

echo ""
echo "=== Running Bearer Token Auth Tests ==="

# ============================================================
# Test 1: Valid Bearer token with admin role → 200
# ============================================================
echo ""
echo "--- Test 1: Valid Bearer token (admin) → 200 ---"
ADMIN_TOKEN=$(get_token "cerebro-client" "cerebro-secret")
if [[ -z "$ADMIN_TOKEN" ]]; then
  fail "Test 1" "Failed to obtain admin JWT from mock IdP"
else
  RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${CEREBRO_URL}/overview" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -d '{"host":"http://localhost:9200"}')
  HTTP_CODE=$(echo "$RESPONSE" | tail -1)
  BODY=$(echo "$RESPONSE" | sed '$d')

  if [[ "$HTTP_CODE" == "200" ]]; then
    # Check the CerebroResponse status field
    CEREBRO_STATUS=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || echo "")
    if [[ "$CEREBRO_STATUS" == "200" ]]; then
      pass "Test 1 - Valid admin Bearer token returns 200 with cluster data"
    else
      fail "Test 1" "HTTP 200 but CerebroResponse status=${CEREBRO_STATUS}, body=${BODY}"
    fi
  else
    fail "Test 1" "Expected HTTP 200, got ${HTTP_CODE}. Body: ${BODY}"
  fi
fi

# ============================================================
# Test 2: Invalid Bearer token → 401
# ============================================================
echo ""
echo "--- Test 2: Invalid Bearer token → 401 ---"
RESPONSE=$(curl -s -w "\n%{http_code}" -D - -X POST "${CEREBRO_URL}/overview" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer invalid-token-value" \
  -d '{"host":"http://localhost:9200"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
HEADERS=$(echo "$RESPONSE" | sed '$d')

if [[ "$HTTP_CODE" == "401" ]]; then
  if echo "$HEADERS" | grep -qi "WWW-Authenticate.*Bearer"; then
    pass "Test 2 - Invalid Bearer token returns 401 with WWW-Authenticate: Bearer"
  else
    fail "Test 2" "Got 401 but missing WWW-Authenticate: Bearer header"
  fi
else
  fail "Test 2" "Expected HTTP 401, got ${HTTP_CODE}"
fi

# ============================================================
# Test 3: No auth at all → CerebroResponse 303
# ============================================================
echo ""
echo "--- Test 3: No auth → CerebroResponse 303 ---"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${CEREBRO_URL}/overview" \
  -H "Content-Type: application/json" \
  -d '{"host":"http://localhost:9200"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [[ "$HTTP_CODE" == "200" ]]; then
  CEREBRO_STATUS=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || echo "")
  if [[ "$CEREBRO_STATUS" == "303" ]]; then
    pass "Test 3 - No auth returns CerebroResponse with status 303"
  else
    fail "Test 3" "HTTP 200 but CerebroResponse status=${CEREBRO_STATUS}, expected 303. Body: ${BODY}"
  fi
else
  fail "Test 3" "Expected HTTP 200 (CerebroResponse wrapper), got ${HTTP_CODE}. Body: ${BODY}"
fi

# ============================================================
# Test 4: RBAC enforcement — viewer cannot delete → 403
# ============================================================
echo ""
echo "--- Test 4: Viewer Bearer token cannot delete indices → 403 ---"
VIEWER_TOKEN=$(get_token "cerebro-viewer" "secret")
if [[ -z "$VIEWER_TOKEN" ]]; then
  fail "Test 4" "Failed to obtain viewer JWT from mock IdP"
else
  RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${CEREBRO_URL}/overview/delete_indices" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${VIEWER_TOKEN}" \
    -d '{"host":"http://localhost:9200","indices":"nonexistent-index"}')
  HTTP_CODE=$(echo "$RESPONSE" | tail -1)
  BODY=$(echo "$RESPONSE" | sed '$d')

  if [[ "$HTTP_CODE" == "200" ]]; then
    CEREBRO_STATUS=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || echo "")
    if [[ "$CEREBRO_STATUS" == "403" ]]; then
      # Verify error message mentions permission
      ERROR_MSG=$(echo "$BODY" | python3 -c "import sys,json; b=json.load(sys.stdin).get('body',{}); print(b.get('error',''))" 2>/dev/null || echo "")
      if echo "$ERROR_MSG" | grep -qi "not permitted\|permission"; then
        pass "Test 4 - Viewer cannot delete indices (403 with permission denied)"
      else
        pass "Test 4 - Viewer cannot delete indices (403)"
      fi
    else
      fail "Test 4" "Expected CerebroResponse status=403, got ${CEREBRO_STATUS}. Body: ${BODY}"
    fi
  else
    fail "Test 4" "Expected HTTP 200 (CerebroResponse wrapper), got ${HTTP_CODE}. Body: ${BODY}"
  fi
fi

# ============================================================
# Results
# ============================================================
echo ""
echo "==============================="
echo "  Results: ${TESTS_PASSED} passed, ${TESTS_FAILED} failed"
echo "==============================="

if [[ $TESTS_FAILED -gt 0 ]]; then
  exit 1
fi
exit 0
