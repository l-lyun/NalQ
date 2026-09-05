#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

ENV_FILE="$DEFAULT_WEB_ENV_FILE"

while [ "$#" -gt 0 ]; do
	case "$1" in
		--env-file) ENV_FILE="${2:?missing value for --env-file}"; shift 2 ;;
		*) die "unknown argument: $1" ;;
	esac
done

require_file "$ENV_FILE"
allowed_names=(AWS_REGION SERVICE_DOMAIN WEB_DOMAIN API_DOMAIN VITE_API_BASE_URL WEB_S3_BUCKET CLOUDFRONT_DISTRIBUTION_ID)
while IFS= read -r line || [ -n "$line" ]; do
	trimmed="${line#"${line%%[![:space:]]*}"}"
	case "$trimmed" in
		''|'#'*) continue ;;
	esac
	[[ "$trimmed" =~ ^(export[[:space:]]+)?([A-Za-z_][A-Za-z0-9_]*)= ]] \
		|| die "web deploy env contains an unsupported line"
	name="${BASH_REMATCH[2]}"
	case " ${allowed_names[*]} " in
		*" $name "*) ;;
		*) die "web deploy env must not contain server configuration or secrets: $name" ;;
	esac
done <"$ENV_FILE"

load_env_file_unexported "$ENV_FILE"

for name in "${allowed_names[@]}"; do
	value="${!name:-}"
	[ -n "$value" ] || die "$name must not be empty"
	case "$value" in
		*CHANGE_ME*) die "$name still contains CHANGE_ME" ;;
	esac
done

[[ "$SERVICE_DOMAIN" =~ ^[a-z0-9.-]+$ ]] || die "SERVICE_DOMAIN must be a hostname"
[[ "$WEB_DOMAIN" =~ ^[a-z0-9.-]+$ ]] || die "WEB_DOMAIN must be a hostname"
[[ "$API_DOMAIN" =~ ^[a-z0-9.-]+$ ]] || die "API_DOMAIN must be a hostname"
case "$SERVICE_DOMAIN:$WEB_DOMAIN:$API_DOMAIN" in
	*example.com*) die "example.com placeholders are forbidden in an applied web environment" ;;
esac
[ "$AWS_REGION" = "ap-northeast-2" ] || die "AWS_REGION must be ap-northeast-2"
case "$WEB_DOMAIN" in
	"$SERVICE_DOMAIN"|"app.$SERVICE_DOMAIN") ;;
	*) die "WEB_DOMAIN must equal SERVICE_DOMAIN or app.SERVICE_DOMAIN" ;;
esac
[ "$API_DOMAIN" = "api.$SERVICE_DOMAIN" ] || die "API_DOMAIN must equal api.SERVICE_DOMAIN"
[ "$VITE_API_BASE_URL" = "https://$API_DOMAIN" ] || die "VITE_API_BASE_URL must equal https://API_DOMAIN"

log "web deployment environment contract is valid (public build values only)"
