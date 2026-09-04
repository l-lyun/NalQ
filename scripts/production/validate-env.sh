#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

ENV_FILE="$DEFAULT_ENV_FILE"

while [ "$#" -gt 0 ]; do
	case "$1" in
		--env-file) ENV_FILE="${2:?missing value for --env-file}"; shift 2 ;;
		*) die "unknown argument: $1" ;;
	esac
done

load_env_file "$ENV_FILE"

[[ "${WEB_DOMAIN:-}" =~ ^[a-z0-9.-]+$ ]] || die "WEB_DOMAIN must be a hostname"
[[ "${API_DOMAIN:-}" =~ ^[a-z0-9.-]+$ ]] || die "API_DOMAIN must be a hostname"
case "$WEB_DOMAIN:$API_DOMAIN" in
	*example.com*) die "example.com placeholders are forbidden in an applied production environment" ;;
esac

required_names=(
	AWS_REGION WEB_DOMAIN API_DOMAIN WEB_ORIGIN VITE_API_BASE_URL
	WEB_S3_BUCKET CLOUDFRONT_DISTRIBUTION_ID DB_BACKUP_S3_URI SERVER_IMAGE
	MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD
	OPENMD_CORS_ALLOWED_ORIGINS OPENMD_AUTH_BROWSER_ALLOWED_ORIGINS
	OPENMD_AUTH_ACCESS_TOKEN_SECRET OPENMD_AUTH_EMAIL_CODE_HMAC_SECRET
	OPENMD_MAIL_FROM SPRING_MAIL_HOST SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD
	OPENAI_API_KEY
)

for name in "${required_names[@]}"; do
	value="${!name:-}"
	[ -n "$value" ] || die "$name must not be empty"
	case "$value" in
		*CHANGE_ME*) die "$name still contains CHANGE_ME" ;;
	esac
done

[ "$AWS_REGION" = "ap-northeast-2" ] || die "AWS_REGION must be ap-northeast-2"
[ "$WEB_ORIGIN" = "https://$WEB_DOMAIN" ] || die "WEB_ORIGIN must equal https://WEB_DOMAIN"
[ "$VITE_API_BASE_URL" = "https://$API_DOMAIN" ] || die "VITE_API_BASE_URL must equal https://API_DOMAIN"
[ "$OPENMD_CORS_ALLOWED_ORIGINS" = "$WEB_ORIGIN" ] || die "CORS origin must equal WEB_ORIGIN"
[ "$OPENMD_AUTH_BROWSER_ALLOWED_ORIGINS" = "$WEB_ORIGIN" ] || die "browser origin must equal WEB_ORIGIN"
[[ "$DB_BACKUP_S3_URI" =~ ^s3://[^/]+/.+ ]] || die "DB_BACKUP_S3_URI must include bucket and prefix"
require_immutable_image "$SERVER_IMAGE"

require_command openssl
for name in OPENMD_AUTH_ACCESS_TOKEN_SECRET OPENMD_AUTH_EMAIL_CODE_HMAC_SECRET; do
	value="${!name}"
	decoded_bytes="$(printf '%s' "$value" | openssl base64 -d -A 2>/dev/null | wc -c | tr -d ' ')"
	[ "$decoded_bytes" -ge 32 ] || die "$name must decode to at least 32 bytes"
done

if [ "${OPENMD_NOTION_ENABLED:-false}" = "true" ]; then
	for name in OPENMD_NOTION_CLIENT_ID OPENMD_NOTION_CLIENT_SECRET OPENMD_NOTION_CALLBACK_URI OPENMD_NOTION_TOKEN_KEYS OPENMD_NOTION_WRITE_KEY_VERSION; do
		[ -n "${!name:-}" ] || die "$name is required when Notion is enabled"
	done
fi

if [ "${OPENMD_QUIZ_GENERATION_ENABLED:-false}" = "true" ]; then
	case "$OPENAI_API_KEY" in
		no-key-configured|CHANGE_ME*) die "a real OPENAI_API_KEY is required when quiz generation is enabled" ;;
	esac
fi

log "production environment contract is valid (secret values not printed)"
