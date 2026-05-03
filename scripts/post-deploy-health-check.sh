#!/usr/bin/env bash
# Post-deploy gate for Halqa backend.
#
# Run this immediately after promoting a Vercel deploy to production.
# Fails (exit 1) if /api/health reports any subsystem unhealthy, so a
# broken deploy can't sit silently in front of users.
#
# Usage:
#   ./scripts/post-deploy-health-check.sh
#   ./scripts/post-deploy-health-check.sh https://halqa-backend.vercel.app
#   ./scripts/post-deploy-health-check.sh --strict-no-kyc-bypass
#
# When --strict-no-kyc-bypass is passed (recommended for the public
# launch in M3+), the script also fails if BYPASS_KYC_FOR_BETA is still
# enabled. During closed beta we leave that off.
#
# `--strict-no-kyc-bypass` requires the HEALTH_INTERNAL_TOKEN env var
# because the kycBypass flag is no longer exposed in the public health
# payload (any scraper hitting /api/health used to be told whether
# closed-beta KYC bypass was on — a useful hint for abuse). The
# script authenticates with `Authorization: Bearer $HEALTH_INTERNAL_TOKEN`
# to receive the internal payload. Set the same token in Vercel env
# vars for the backend project so server and ops agree on the secret.
#
# Environment overrides:
#   HALQA_BACKEND_URL       (default: https://halqa-backend.vercel.app)
#   HEALTH_TIMEOUT_S        (default: 30)
#   HEALTH_INTERNAL_TOKEN   (required for --strict-no-kyc-bypass)
#
# Exit codes:
#   0 = all good
#   1 = at least one subsystem unhealthy
#   2 = HTTP/network error reaching the backend at all

set -euo pipefail

STRICT_NO_KYC_BYPASS=0
URL_ARG=""
for arg in "$@"; do
  case "$arg" in
    --strict-no-kyc-bypass)
      STRICT_NO_KYC_BYPASS=1
      ;;
    http://*|https://*)
      URL_ARG="$arg"
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 2
      ;;
  esac
done

BASE_URL="${URL_ARG:-${HALQA_BACKEND_URL:-https://halqa-backend.vercel.app}}"
TIMEOUT="${HEALTH_TIMEOUT_S:-30}"
HEALTH_URL="${BASE_URL%/}/api/health"

echo "==> probing $HEALTH_URL (timeout ${TIMEOUT}s)"

# Strict mode requires the internal token because kycBypass.enabled is
# only exposed to authenticated callers — refuse early so a CI run that
# *thinks* it's enforcing the no-bypass invariant doesn't silently
# degrade to "yeah I checked, it's fine" against a sanitised payload
# that simply omits the field.
if [ "$STRICT_NO_KYC_BYPASS" = "1" ] && [ -z "${HEALTH_INTERNAL_TOKEN:-}" ]; then
  echo "FAIL: --strict-no-kyc-bypass requires HEALTH_INTERNAL_TOKEN env var." >&2
  echo "      The kycBypass flag is no longer in the public payload." >&2
  echo "      Set HEALTH_INTERNAL_TOKEN to the same value provisioned in" >&2
  echo "      Vercel env vars for the backend project." >&2
  exit 2
fi

# We want both the body and the HTTP status code, even on 503 (which is
# the *correct* response when a subsystem is down — curl --fail would hide it).
TMP_BODY="$(mktemp)"
trap 'rm -f "$TMP_BODY"' EXIT

CURL_AUTH_ARGS=()
if [ -n "${HEALTH_INTERNAL_TOKEN:-}" ]; then
  CURL_AUTH_ARGS=(-H "Authorization: Bearer ${HEALTH_INTERNAL_TOKEN}")
fi

HTTP_STATUS=$(curl --silent --show-error --max-time "$TIMEOUT" \
  "${CURL_AUTH_ARGS[@]}" \
  --output "$TMP_BODY" --write-out '%{http_code}' "$HEALTH_URL" \
  || echo "000")

if [ "$HTTP_STATUS" = "000" ]; then
  echo "FAIL: could not reach $HEALTH_URL (network error / DNS / TLS)" >&2
  exit 2
fi

BODY="$(cat "$TMP_BODY")"
echo "$BODY" | (jq . 2>/dev/null || cat)

OK=$(echo "$BODY" | jq -r '.ok // false' 2>/dev/null || echo "false")
FAILURES=$(echo "$BODY" | jq -r '.failures // [] | join(", ")' 2>/dev/null || echo "")
KYC_BYPASS=$(echo "$BODY" | jq -r '.checks.kycBypass.enabled // false' 2>/dev/null || echo "false")

if [ "$OK" != "true" ]; then
  echo "FAIL: backend reports unhealthy subsystems: ${FAILURES}" >&2
  echo "      HTTP status: $HTTP_STATUS" >&2
  exit 1
fi

if [ "$STRICT_NO_KYC_BYPASS" = "1" ] && [ "$KYC_BYPASS" = "true" ]; then
  echo "FAIL: BYPASS_KYC_FOR_BETA=true is still set on this deploy." >&2
  echo "      Disable it in Vercel env vars before promoting to public release." >&2
  exit 1
fi

echo "OK: $HEALTH_URL responded healthy (HTTP $HTTP_STATUS)"
