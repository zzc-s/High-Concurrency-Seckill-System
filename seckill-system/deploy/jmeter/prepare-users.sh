#!/usr/bin/env bash
# Batch register test users and write JWT tokens to users.csv for JMeter.
# Usage: ./deploy/jmeter/prepare-users.sh [base_url] [count]

set -euo pipefail

BASE_URL="${1:-http://localhost:9000}"
COUNT="${2:-50}"
PASSWORD="${JMETER_USER_PASSWORD:-test123456}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CSV_PATH="$SCRIPT_DIR/users.csv"

echo "=== Prepare JMeter users ==="
echo "API: $BASE_URL/api/auth/register"
echo "Count: $COUNT -> $CSV_PATH"

ok=0
fail=0

echo "token" > "$CSV_PATH"

for ((i = 1; i <= COUNT; i++)); do
  username=$(printf "testuser%03d" "$i")
  register_json=$(printf '{"username":"%s","password":"%s"}' "$username" "$PASSWORD")
  resp=$(curl -sS -X POST "$BASE_URL/api/auth/register" \
    -H "Content-Type: application/json" \
    -d "$register_json" || true)
  token=$(echo "$resp" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
  if [[ -z "$token" ]]; then
    login_json=$(printf '{"username":"%s","password":"%s"}' "$username" "$PASSWORD")
    resp=$(curl -sS -X POST "$BASE_URL/api/auth/login" \
      -H "Content-Type: application/json" \
      -d "$login_json" || true)
    token=$(echo "$resp" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
  fi
  if [[ -n "$token" ]]; then
    echo "$token" >> "$CSV_PATH"
    echo "  [OK] $username"
    ok=$((ok + 1))
  else
    echo "  [FAIL] $username"
    fail=$((fail + 1))
  fi
done

if [[ "$ok" -eq 0 ]]; then
  echo "[ERROR] No tokens collected. Is the backend/gateway running?"
  exit 1
fi

echo ""
echo "[DONE] $ok tokens written (failed: $fail)"
echo "Next: curl -X POST $BASE_URL/api/admin/warmup"
