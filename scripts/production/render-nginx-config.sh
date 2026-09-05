#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$SCRIPT_DIR/common.sh"

ENV_FILE="$DEFAULT_ENV_FILE"
MODE=bootstrap
OUTPUT=""
APPLY=false

while [ "$#" -gt 0 ]; do
	case "$1" in
		--env-file) ENV_FILE="${2:?missing value for --env-file}"; shift 2 ;;
		--mode) MODE="${2:?missing value for --mode}"; shift 2 ;;
		--output) OUTPUT="${2:?missing value for --output}"; shift 2 ;;
		--apply) APPLY=true; shift ;;
		*) die "unknown argument: $1" ;;
	esac
done

load_env_file "$ENV_FILE"
[[ "${API_DOMAIN:-}" =~ ^[a-z0-9.-]+$ ]] || die "API_DOMAIN must be a hostname"

case "$MODE" in
	bootstrap) template="$PRODUCTION_ROOT/nginx/nalq-api-bootstrap.conf.template" ;;
	tls) template="$PRODUCTION_ROOT/nginx/nalq-api.conf.template" ;;
	*) die "--mode must be bootstrap or tls" ;;
esac

if [ "$APPLY" != true ]; then
	log "DRY RUN: would render $template for $API_DOMAIN"
	[ -z "$OUTPUT" ] || log "target: $OUTPUT"
	exit 0
fi

[ -n "$OUTPUT" ] || die "--output is required with --apply"
temporary_file="$(mktemp)"
trap 'rm -f "$temporary_file"' EXIT
sed "s/__API_DOMAIN__/$API_DOMAIN/g" "$template" >"$temporary_file"
grep -q '__API_DOMAIN__' "$temporary_file" && die "unrendered API domain placeholder remains"
install -m 0644 "$temporary_file" "$OUTPUT"
log "rendered Nginx configuration: $OUTPUT"
