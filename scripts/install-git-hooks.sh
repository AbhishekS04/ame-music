#!/usr/bin/env bash
# Points this checkout at the repository's tracked hooks. Run once after
# `git init` / clone. It only changes local git config, nothing is pushed.
set -euo pipefail
cd "$(dirname "$0")/.."

git config core.hooksPath .githooks
chmod +x .githooks/pre-commit scripts/check-secrets.sh

echo "Installed git hooks from .githooks/"
echo "Pre-commit now blocks keystores, local.properties, .env files and high-signal tokens."
