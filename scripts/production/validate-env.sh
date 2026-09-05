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

[[ "${SERVICE_DOMAIN:-}" =~ ^[a-z0-9.-]+$ ]] || die "SERVICE_DOMAIN must be a hostname"
[[ "${WEB_DOMAIN:-}" =~ ^[a-z0-9.-]+$ ]] || die "WEB_DOMAIN must be a hostname"
[[ "${API_DOMAIN:-}" =~ ^[a-z0-9.-]+$ ]] || die "API_DOMAIN must be a hostname"
case "$SERVICE_DOMAIN:$WEB_DOMAIN:$API_DOMAIN" in
	*example.com*) die "example.com placeholders are forbidden in an applied production environment" ;;
esac

required_names=(
	AWS_REGION SERVICE_DOMAIN WEB_DOMAIN API_DOMAIN WEB_ORIGIN
	DB_BACKUP_S3_URI SERVER_IMAGE SERVER_HOST_PORT
	MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD
	OPENMD_CORS_ALLOWED_ORIGINS OPENMD_AUTH_BROWSER_ALLOWED_ORIGINS
	OPENMD_AUTH_ACCESS_TOKEN_SECRET OPENMD_AUTH_EMAIL_CODE_HMAC_SECRET
	OPENMD_MAIL_FROM SPRING_MAIL_HOST SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD
	SPRING_AI_OPENAI_BASE_URL OPENAI_API_KEY
)

for name in "${required_names[@]}"; do
	value="${!name:-}"
	[ -n "$value" ] || die "$name must not be empty"
	case "$value" in
		*CHANGE_ME*) die "$name still contains CHANGE_ME" ;;
	esac
done

[ "$AWS_REGION" = "ap-northeast-2" ] || die "AWS_REGION must be ap-northeast-2"
case "$WEB_DOMAIN" in
	"$SERVICE_DOMAIN"|"app.$SERVICE_DOMAIN") ;;
	*) die "WEB_DOMAIN must equal SERVICE_DOMAIN or app.SERVICE_DOMAIN" ;;
esac
[ "$API_DOMAIN" = "api.$SERVICE_DOMAIN" ] || die "API_DOMAIN must equal api.SERVICE_DOMAIN"
[ "$SERVER_HOST_PORT" = "8080" ] || die "SERVER_HOST_PORT must be exactly 8080 for the fixed Nginx upstream"
[ "$WEB_ORIGIN" = "https://$WEB_DOMAIN" ] || die "WEB_ORIGIN must equal https://WEB_DOMAIN"
[ "$OPENMD_CORS_ALLOWED_ORIGINS" = "$WEB_ORIGIN" ] || die "CORS origin must equal WEB_ORIGIN"
[ "$OPENMD_AUTH_BROWSER_ALLOWED_ORIGINS" = "$WEB_ORIGIN" ] || die "browser origin must equal WEB_ORIGIN"
[[ "$DB_BACKUP_S3_URI" =~ ^s3://[^/]+/.+ ]] || die "DB_BACKUP_S3_URI must include bucket and prefix"
[ "$SPRING_AI_OPENAI_BASE_URL" = "https://api.openai.com" ] || die "SPRING_AI_OPENAI_BASE_URL must be https://api.openai.com"
require_immutable_image "$SERVER_IMAGE"

require_command openssl
for name in OPENMD_AUTH_ACCESS_TOKEN_SECRET OPENMD_AUTH_EMAIL_CODE_HMAC_SECRET; do
	value="${!name}"
	decoded_bytes="$(printf '%s' "$value" | openssl base64 -d -A 2>/dev/null | wc -c | tr -d ' ')"
	[ "$decoded_bytes" -ge 32 ] || die "$name must decode to at least 32 bytes"
done

case "${OPENMD_NOTION_ENABLED:-false}" in
	true|false) ;;
	*) die "OPENMD_NOTION_ENABLED must be true or false" ;;
esac

require_https_uri() {
	local name="$1"
	local uri="$2"
	[[ "$uri" =~ ^https://[^/?#[:space:]@]+(/[^[:space:]#]*)?$ ]] || die "$name must be an absolute https URI without credentials or fragments"
}

validate_notion_token_keys() {
	local configured_keys="$1"
	local write_version="$2"
	local configured version encoded_key decoded_bytes seen_versions write_version_found
	[[ "$write_version" =~ ^[A-Za-z0-9._-]+$ ]] || die "OPENMD_NOTION_WRITE_KEY_VERSION has an invalid format"
	seen_versions=','
	write_version_found=false
	IFS=',' read -r -a notion_token_keys <<<"$configured_keys"
	for configured in "${notion_token_keys[@]}"; do
		configured="${configured#"${configured%%[![:space:]]*}"}"
		configured="${configured%"${configured##*[![:space:]]}"}"
		[[ "$configured" =~ ^([A-Za-z0-9._-]+):([A-Za-z0-9+/]{43}=?)$ ]] \
			|| die "each OPENMD_NOTION_TOKEN_KEYS entry must be version:base64"
		version="${BASH_REMATCH[1]}"
		encoded_key="${BASH_REMATCH[2]}"
		case "$seen_versions" in
			*",$version,"*) die "OPENMD_NOTION_TOKEN_KEYS contains a duplicate version: $version" ;;
		esac
		seen_versions="${seen_versions}${version},"
		decoded_bytes="$(printf '%s' "$encoded_key" | openssl base64 -d -A 2>/dev/null | wc -c | tr -d ' ')"
		[ "$decoded_bytes" = "32" ] || die "each Notion token key must decode to exactly 32 bytes"
		if [ "$version" = "$write_version" ]; then
			write_version_found=true
		fi
	done
	[ "$write_version_found" = true ] || die "OPENMD_NOTION_WRITE_KEY_VERSION must exist in OPENMD_NOTION_TOKEN_KEYS"
}

if [ "${OPENMD_NOTION_ENABLED:-false}" = "true" ]; then
	for name in OPENMD_NOTION_CLIENT_ID OPENMD_NOTION_CLIENT_SECRET OPENMD_NOTION_CALLBACK_URI OPENMD_NOTION_ALLOWED_RETURN_URIS OPENMD_NOTION_FAILURE_RETURN_URI OPENMD_NOTION_TOKEN_KEYS OPENMD_NOTION_WRITE_KEY_VERSION; do
		[ -n "${!name:-}" ] || die "$name is required when Notion is enabled"
	done
	require_https_uri OPENMD_NOTION_CALLBACK_URI "$OPENMD_NOTION_CALLBACK_URI"
	require_https_uri OPENMD_NOTION_FAILURE_RETURN_URI "$OPENMD_NOTION_FAILURE_RETURN_URI"
	failure_uri_allowed=false
	IFS=',' read -r -a notion_return_uris <<<"$OPENMD_NOTION_ALLOWED_RETURN_URIS"
	for uri in "${notion_return_uris[@]}"; do
		require_https_uri OPENMD_NOTION_ALLOWED_RETURN_URIS "$uri"
		if [ "$uri" = "$OPENMD_NOTION_FAILURE_RETURN_URI" ]; then
			failure_uri_allowed=true
		fi
	done
	[ "$failure_uri_allowed" = true ] || die "OPENMD_NOTION_FAILURE_RETURN_URI must be an exact member of OPENMD_NOTION_ALLOWED_RETURN_URIS"
	validate_notion_token_keys "$OPENMD_NOTION_TOKEN_KEYS" "$OPENMD_NOTION_WRITE_KEY_VERSION"
fi

if [ "${OPENMD_QUIZ_GENERATION_ENABLED:-false}" = "true" ]; then
	case "$OPENAI_API_KEY" in
		no-key-configured|CHANGE_ME*) die "a real OPENAI_API_KEY is required when quiz generation is enabled" ;;
	esac
fi

log "production environment contract is valid (secret values not printed)"
