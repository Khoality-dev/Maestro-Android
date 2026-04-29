#!/usr/bin/env bash
# Upload release-signing secrets from .env to the GitHub repo.
# Usage: scripts/upload-secrets.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$REPO_ROOT/.env"

if [ ! -f "$ENV_FILE" ]; then
    echo "error: $ENV_FILE not found" >&2
    exit 1
fi

# shellcheck disable=SC1090
set -a
source "$ENV_FILE"
set +a

require() {
    local name="$1"
    local value="${!name:-}"
    if [ -z "$value" ]; then
        echo "error: $name is empty in .env" >&2
        exit 1
    fi
}

require KEYSTORE_FILE
require KEYSTORE_PASSWORD
require KEY_ALIAS
require KEY_PASSWORD

if [ ! -f "$KEYSTORE_FILE" ]; then
    echo "error: KEYSTORE_FILE='$KEYSTORE_FILE' does not exist" >&2
    exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
    echo "error: gh CLI not installed" >&2
    exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
    echo "error: gh CLI not authenticated. Run: gh auth login" >&2
    exit 1
fi

echo "Uploading secrets to $(gh repo view --json nameWithOwner -q .nameWithOwner) ..."

base64 -w0 "$KEYSTORE_FILE" | gh secret set KEYSTORE_BASE64 --body -
printf %s "$KEYSTORE_PASSWORD" | gh secret set KEYSTORE_PASSWORD --body -
printf %s "$KEY_ALIAS"          | gh secret set KEY_ALIAS          --body -
printf %s "$KEY_PASSWORD"       | gh secret set KEY_PASSWORD       --body -

echo
echo "Done. Secrets now set:"
gh secret list | grep -E '^(KEYSTORE_BASE64|KEYSTORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD)\b'
