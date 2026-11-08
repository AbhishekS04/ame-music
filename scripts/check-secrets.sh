#!/usr/bin/env bash
# Final pre-push gate. Checks the files git is actually going to publish, not
# the whole working tree (keystore.properties and local.properties exist on
# disk on purpose and must remain untracked).
#
# Usage:
#   git add -A
#   bash scripts/check-secrets.sh
#   git commit ...
#   git push
set -euo pipefail
cd "$(dirname "$0")/.."

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Not a git repository. Run 'git init' first." >&2
  exit 1
fi

fail=0

tracked=$(git ls-files)

bad_paths=$(printf '%s\n' "$tracked" | grep -E \
  '(^|/)(keystore\.properties|local\.properties|google-services\.json|GoogleService-Info\.plist)$|\.(jks|keystore|p12|pfx|pem|key|p8|mobileprovision|ovpn)$|(^|/)\.env($|\.)' \
  | grep -v 'keystore\.properties\.example' \
  | grep -v '\.env\.example$' || true)

if [ -n "$bad_paths" ]; then
  echo "FAIL: secret-bearing files are tracked:" >&2
  printf '%s\n' "$bad_paths" | sed 's/^/  /' >&2
  fail=1
fi

vendored=$(printf '%s\n' "$tracked" | grep -E \
  '(^|/)(node_modules|build|\.cxx|\.gradle|\.kotlin|dist|logs|coverage|\.vercel|\.turbo)/' || true)

if [ -n "$vendored" ]; then
  echo "FAIL: generated/vendored directories are tracked (bloats the repo and hides secrets):" >&2
  printf '%s\n' "$vendored" | head -20 | sed 's/^/  /' >&2
  fail=1
fi

content_hits=$(printf '%s\n' "$tracked" | while IFS= read -r f; do
  [ -f "$f" ] || continue
  grep -I -n -E \
    '-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----|ghp_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{20,}|xox[baprs]-[A-Za-z0-9-]{10,}|AKIA[0-9A-Z]{16}|sk-(live|proj|ant)-[A-Za-z0-9_-]{16,}' \
    "$f" 2>/dev/null || true
done)

if [ -n "$content_hits" ]; then
  echo "FAIL: possible credential found in tracked content:" >&2
  printf '%s\n' "$content_hits" >&2
  fail=1
fi

# A keystore on disk is expected; it must just never be tracked.
if [ -f ame-release.jks ] && ! printf '%s\n' "$tracked" | grep -qx 'ame-release.jks'; then
  echo "OK: ame-release.jks exists on disk and is not tracked."
fi

if [ "$fail" -ne 0 ]; then
  echo >&2
  echo "SECRET CHECK FAILED — do not push." >&2
  exit 1
fi

echo "SECRET CHECK PASSED: no secret-bearing files or credential patterns are tracked."
